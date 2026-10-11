package vlc.busmetro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vlc.busmetro.BuildConfig
import vlc.transit.core.Arrival
import vlc.transit.core.Network
import vlc.transit.core.feeds.Feeds
import vlc.transit.core.gtfs.Schedule
import vlc.transit.core.gtfs.Service
import vlc.transit.core.gtfs.ServiceCalendar
import vlc.transit.core.gtfs.VALENCIA_ZONE
import vlc.transit.core.realtime.BikeRealtime
import vlc.transit.core.realtime.BusRealtime
import vlc.transit.core.realtime.Notices
import vlc.transit.core.realtime.RailRealtime
import vlc.transit.core.splitKey
import vlc.transit.core.util.distanceMeters
import vlc.transit.core.util.matchesQuery
import vlc.transit.core.util.normalizeForSearch
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.max

enum class BoardSource { LIVE, SCHEDULE, APPROXIMATE, NO_DATA }

data class LineStyle(val color: Int?, val textColor: Int?)

data class StopBoard(
    val stop: StopEntity?,
    val bike: BikeEntity?,
    val arrivals: List<Arrival>,
    val source: BoardSource,
    val updatedAt: Long,
    val styles: Map<String, LineStyle>,
    val liveFailed: Boolean = false,
)

data class DayRow(val secs: Int, val line: String, val headsign: String)

/** Line identity across direction-split routes: "bus:line:4". */
fun lineKey(network: String, shortName: String) = "$network:line:$shortName"

class TransitRepository(
    private val http: Http,
    private val transit: TransitDb,
    private val user: UserDb,
) {
    private val dao get() = transit.dao()
    private val udao get() = user.dao()

    private class NetCache(
        val calendar: ServiceCalendar,
        val routes: List<RouteEntity>,
        val headsigns: List<String>,
        val tripKeys: List<String>,
    )

    private val cache = ConcurrentHashMap<String, NetCache>()
    private val cacheLock = Mutex()

    fun invalidate() = cache.clear()

    private suspend fun net(network: String): NetCache? {
        cache[network]?.let { return it }
        return cacheLock.withLock {
            cache[network] ?: run {
                val feed = dao.feeds().firstOrNull { it.network == network } ?: return null
                val services = dao.services(network).map { s ->
                    Service(s.mask, s.start, s.end, csvInts(s.added), csvInts(s.removed), s.trips)
                }
                val trips = if (network == Network.RAIL.prefix) dao.trips(network).map { RailRealtime.matchKey(it.tripId) } else emptyList()
                NetCache(ServiceCalendar(services, feed.validFrom, feed.validTo), dao.routes(network), dao.headsigns(network).map { it.text }, trips)
                    .also { cache[network] = it }
            }
        }
    }

    private fun csvInts(s: String) = if (s.isEmpty()) emptySet() else s.split(',').mapNotNull { it.toIntOrNull() }.toSet()

    fun feeds(): Flow<List<FeedEntity>> = dao.feedsFlow()
    suspend fun hasData() = dao.feeds().isNotEmpty()

    suspend fun stop(key: String) = dao.stop(key)
    suspend fun stops(keys: List<String>) = dao.stops(keys)
    suspend fun allStops() = dao.allStops()

    suspend fun search(query: String, limit: Int = 40): List<StopEntity> {
        val q = normalizeForSearch(query)
        if (q.isEmpty()) return emptyList()
        val first = q.substringBefore(' ')
        return dao.searchStops(query.trim(), "%$first%", 400).filter { matchesQuery(it.searchName, q) || it.code == query.trim() }.take(limit)
    }

    suspend fun nearby(lat: Double, lon: Double, radiusM: Double = 700.0, limit: Int = 40): List<Pair<StopEntity, Double>> {
        val dLat = radiusM / 111_000.0
        val dLon = radiusM / (111_000.0 * max(0.2, cos(Math.toRadians(lat))))
        return dao.stopsIn(lat - dLat, lat + dLat, lon - dLon, lon + dLon)
            .map { it to distanceMeters(lat, lon, it.lat, it.lon) }
            .filter { it.second <= radiusM }
            .sortedBy { it.second }
            .take(limit)
    }

    suspend fun lines(): List<LineRow> = dao.lines().sortedWith(compareBy<LineRow> { netOrder(it.network) }.thenComparator { a, b -> LineOrder.compare(a.shortName, b.shortName) })

    suspend fun patterns(network: String, line: String) = dao.patterns(network, line)

    suspend fun styles(network: String): Map<String, LineStyle> =
        net(network)?.routes?.groupBy { it.shortName }?.mapValues { (_, r) -> LineStyle(r.firstNotNullOfOrNull { it.color }, r.firstNotNullOfOrNull { it.textColor }) }
            ?: emptyMap()

    // -------------------------------------------------------------- arrivals

    private val railDelays = Mutex()
    private var railDelayCache: Pair<Long, Map<String, RailRealtime.TripDelay>>? = null

    private suspend fun railDelays(): Map<String, RailRealtime.TripDelay>? = railDelays.withLock {
        val now = System.currentTimeMillis()
        railDelayCache?.let { (t, m) -> if (now - t < 30_000) return m }
        runCatching { RailRealtime.parseTripUpdates(http.text(Feeds.RAIL_TRIP_UPDATES)) }.getOrNull()
            ?.also { railDelayCache = now to it }
            ?: railDelayCache?.second
    }

    suspend fun board(key: String, nowMs: Long = System.currentTimeMillis(), live: Boolean = true): StopBoard = withContext(Dispatchers.Default) {
        val (network, rawId) = splitKey(key) ?: return@withContext StopBoard(null, null, emptyList(), BoardSource.NO_DATA, nowMs, emptyMap())
        if (network == Network.BIKE) {
            if (live) runCatching { refreshBikes() }
            return@withContext StopBoard(null, udao.bike(rawId), emptyList(), if (live) BoardSource.LIVE else BoardSource.SCHEDULE, nowMs, emptyMap())
        }
        val stop = dao.stop(key) ?: return@withContext StopBoard(null, null, emptyList(), BoardSource.NO_DATA, nowMs, emptyMap())
        val nc = net(network.prefix) ?: return@withContext StopBoard(stop, null, emptyList(), BoardSource.NO_DATA, nowMs, emptyMap())
        val styles = styles(network.prefix)
        val nowSec = nowMs / 1000
        val blob = dao.timetable(key)
        val scheduled = blob?.let { Schedule.upcoming(it, nc.calendar, nowSec, horizonSec = 3 * 3600, limit = 80) }.orEmpty()
        val approximate = scheduled.any { it.approximate }

        fun sched(delays: Map<String, RailRealtime.TripDelay>? = null): List<Arrival> = scheduled.map { d ->
            val route = nc.routes.getOrNull(d.routeIdx)
            val line = route?.shortName ?: "?"
            val delay = delays?.let { m -> nc.tripKeys.getOrNull(d.tripIdx)?.let { m[it] } }
            val t = d.epochSec + (delay?.delaySec ?: 0)
            Arrival(lineKey(network.prefix, line), line, nc.headsigns.getOrNull(d.headsignIdx).orEmpty(), t,
                minutesUntil(t, nowSec), realtime = delay != null, delaySec = delay?.delaySec, canceled = delay?.canceled == true)
        }.filter { it.epochSec!! >= nowSec - 30 || it.realtime }.sortedBy { it.epochSec }

        when (network) {
            Network.BUS -> {
                val estimates = if (live && stop.code.matches(Regex("\\d{1,6}"))) runCatching {
                    BusRealtime.parse(http.bytes(Feeds.BUS_ESTIMATES + stop.code, maxBytes = 256 * 1024, referer = "https://www.emtvalencia.es/"))
                }.getOrNull() else null
                val liveArrivals = estimates.orEmpty().map { e ->
                    val minutes = e.minutes ?: clockMinutes(e.clock!!, nowSec)
                    Arrival(lineKey(network.prefix, e.line), e.line, titleCase(e.destination), nowSec + minutes * 60L, minutes, realtime = true)
                }.sortedBy { it.minutes }
                if (liveArrivals.isNotEmpty()) {
                    val liveLines = liveArrivals.map { it.line }.toSet()
                    val rest = sched().filter { it.line !in liveLines }
                    StopBoard(stop, null, liveArrivals + rest, BoardSource.LIVE, nowMs, styles)
                } else {
                    StopBoard(stop, null, sched(), sourceOf(scheduled.isEmpty(), approximate), nowMs, styles, liveFailed = live)
                }
            }
            Network.RAIL -> {
                val delays = if (live) railDelays() else null
                val list = sched(delays)
                val src = if (list.any { it.realtime }) BoardSource.LIVE else sourceOf(scheduled.isEmpty(), approximate)
                StopBoard(stop, null, list, src, nowMs, styles, liveFailed = live && delays == null)
            }
            else -> StopBoard(stop, null, sched(), sourceOf(scheduled.isEmpty(), approximate), nowMs, styles)
        }
    }

    private fun sourceOf(empty: Boolean, approximate: Boolean) = when {
        empty -> BoardSource.NO_DATA
        approximate -> BoardSource.APPROXIMATE
        else -> BoardSource.SCHEDULE
    }

    private fun minutesUntil(t: Long, now: Long) = ((t - now) / 60).toInt().coerceAtLeast(0)

    private fun clockMinutes(clock: String, nowSec: Long): Int {
        val (h, m) = clock.split(':').map { it.toInt() }
        val now = java.time.Instant.ofEpochSecond(nowSec).atZone(VALENCIA_ZONE)
        var target = now.withHour(h % 24).withMinute(m).withSecond(0)
        if (target.isBefore(now.minusMinutes(5))) target = target.plusDays(1)
        return minutesUntil(target.toEpochSecond(), nowSec)
    }

    private fun titleCase(s: String): String =
        if (s.isNotEmpty() && s == s.uppercase()) s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } } else s

    /** The whole day's departures (offline timetable view). */
    suspend fun day(key: String, date: LocalDate): Pair<List<DayRow>, Boolean> = withContext(Dispatchers.Default) {
        val (network, _) = splitKey(key) ?: return@withContext emptyList<DayRow>() to false
        val nc = net(network.prefix) ?: return@withContext emptyList<DayRow>() to false
        val blob = dao.timetable(key) ?: return@withContext emptyList<DayRow>() to false
        val (entries, approx) = Schedule.day(blob, nc.calendar, date)
        entries.map { DayRow(it.secs, nc.routes.getOrNull(it.routeIdx)?.shortName ?: "?", nc.headsigns.getOrNull(it.headsignIdx).orEmpty()) } to approx
    }

    // ----------------------------------------------------------------- bikes

    private var bikesFetched = 0L

    suspend fun bikes(): List<BikeEntity> = udao.bikes()

    suspend fun refreshBikes(force: Boolean = false): List<BikeEntity> {
        val now = System.currentTimeMillis()
        if (!force && now - bikesFetched < 60_000) return udao.bikes()
        val key = BuildConfig.JCDECAUX_KEY
        val body = if (key.isNotBlank()) http.text(Feeds.BIKE_JCDECAUX + key) else http.text(Feeds.BIKE_CKAN)
        val stations = BikeRealtime.parse(body)
        if (stations.isNotEmpty()) {
            udao.upsertBikes(stations.map { BikeEntity(it.id, it.name, it.lat, it.lon, it.bikes, it.docks, it.total, it.open, it.updatedEpochSec, now) })
            bikesFetched = now
        }
        return udao.bikes()
    }

    // ---------------------------------------------------------------- alerts

    /** Refreshes alerts; returns the ones not notified before. */
    suspend fun refreshAlerts(lang: String): List<AlertEntity> {
        val now = System.currentTimeMillis()
        val fresh = ArrayList<vlc.transit.core.ServiceAlert>()
        val railRoutes = net(Network.RAIL.prefix)?.routes?.map { it.routeId }?.toSet().orEmpty()
        if (railRoutes.isNotEmpty()) runCatching { fresh += RailRealtime.parseAlerts(http.text(Feeds.RAIL_ALERTS), railRoutes, lang) }
        runCatching { fresh += Notices.parse(http.text(Feeds.NOTICES, 512 * 1024), lang, now / 1000) }
        if (fresh.isEmpty()) return emptyList()
        val railRouteLine = net(Network.RAIL.prefix)?.routes?.associate { "rail:" + it.routeId to it.shortName }.orEmpty()
        val known = udao.alerts().associateBy { it.id }
        val rows = fresh.map { a ->
            // Rail alerts name raw route ids; store line keys like every other alert.
            val lines = a.routeKeys.map { rk -> railRouteLine[rk]?.let { lineKey("rail", it) } ?: rk }.distinct()
            val old = known[a.id]
            AlertEntity(a.id, a.network.prefix, a.title, a.body, lines.joinToString(","), a.stopKeys.joinToString(","),
                a.startEpochSec?.times(1000), a.endEpochSec?.times(1000), a.url, now, notified = old?.notified ?: false, seen = old?.seen ?: false)
        }
        udao.upsertAlerts(rows)
        udao.pruneAlerts(rows.map { it.id })
        return rows.filter { !it.notified }
    }

    fun alerts(): Flow<List<AlertEntity>> = udao.alertsFlow(System.currentTimeMillis())
    fun unseenAlerts(): Flow<Int> = udao.unseenCount(System.currentTimeMillis())
    suspend fun markAlertsSeen() = udao.markAlertsSeen()
    suspend fun markNotified(ids: List<String>) = udao.markNotified(ids)

    // ------------------------------------------------------------- favourites

    fun favorites() = udao.favoritesFlow()
    suspend fun favoriteList() = udao.favorites()
    fun isFavorite(key: String) = udao.isFavoriteFlow(key)
    suspend fun addFavorite(key: String, nickname: String? = null) =
        udao.upsertFavorite(FavoriteEntity(key, nickname?.take(40), udao.maxPosition() + 1))
    suspend fun removeFavorite(key: String) = udao.deleteFavorite(key)
    suspend fun renameFavorite(f: FavoriteEntity, nickname: String?) = udao.upsertFavorite(f.copy(nickname = nickname?.trim()?.take(40)?.ifEmpty { null }))
    suspend fun reorder(list: List<FavoriteEntity>) = udao.updateFavorites(list.mapIndexed { i, f -> f.copy(position = i) })

    /** Display name for any favourite key (transit stop or bike station). */
    suspend fun nameOf(key: String): String? {
        val (n, raw) = splitKey(key) ?: return null
        return if (n == Network.BIKE) udao.bike(raw)?.name else dao.stop(key)?.name
    }

    companion object {
        fun netOrder(n: String) = when (n) { "metro" -> 0; "bus" -> 1; "rail" -> 2; else -> 3 }
    }
}
