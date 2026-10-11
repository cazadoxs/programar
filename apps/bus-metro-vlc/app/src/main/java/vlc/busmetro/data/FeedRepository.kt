package vlc.busmetro.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vlc.transit.core.Network
import vlc.transit.core.feeds.FeedSource
import vlc.transit.core.feeds.Feeds
import vlc.transit.core.gtfs.CompiledFeed
import vlc.transit.core.gtfs.GtfsCompiler
import vlc.transit.core.key
import vlc.transit.core.util.normalizeForSearch
import java.io.File
import java.util.zip.ZipFile

/**
 * Downloads each network's GTFS feed, compiles it on the phone and swaps it
 * into transit.db in one transaction, so the app never shows half a network.
 */
class FeedRepository(
    private val context: Context,
    private val http: Http,
    private val db: TransitDb,
    private val onChanged: () -> Unit,
) {
    data class Result(val network: Network, val ok: Boolean, val error: String? = null)

    suspend fun syncAll(progress: (network: Network, fraction: Float) -> Unit = { _, _ -> }): List<Result> =
        Feeds.ALL.map { src -> sync(src) { progress(src.network, it) } }

    suspend fun sync(src: FeedSource, progress: (Float) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "feed-${src.network.prefix}.zip")
        var lastError: String? = null
        for (url in src.urls) {
            try {
                http.download(url, file) { progress(it * 0.6f) }
                progress(0.65f)
                val feed = ZipFile(file).use { zip ->
                    // Refuse zip entries with absurd sizes before reading them (zip bombs).
                    val total = zip.entries().asSequence().sumOf { e -> e.size.coerceAtLeast(0) }
                    if (total > 900L shl 20) throw IllegalStateException("feed expands too much")
                    GtfsCompiler({ name -> zip.getEntry(name)?.let(zip::getInputStream) }, src.options).compile()
                }
                progress(0.85f)
                store(feed, url)
                progress(1f)
                onChanged()
                return@withContext Result(src.network, true)
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message}"
            } finally {
                file.delete()
            }
        }
        Result(src.network, false, lastError)
    }

    private suspend fun store(feed: CompiledFeed, url: String) {
        val n = feed.network.prefix
        val routes = feed.routes
        val stopRows = feed.stops.map { s ->
            val lines = s.routeIdxs.map { routes[it].shortName }.distinct().sortedWith(LineOrder)
            StopEntity(
                key = key(feed.network, s.id), network = n, code = s.code, name = s.name,
                searchName = normalizeForSearch(s.name + " " + s.code), lat = s.lat, lon = s.lon,
                lines = lines.joinToString(","), wheelchair = s.wheelchair,
            )
        }
        val dao = db.dao()
        db.withTransaction {
            dao.clearStops(n); dao.clearRoutes(n); dao.clearServices(n); dao.clearHeadsigns(n)
            dao.clearTrips(n); dao.clearTimetables(n); dao.clearPatterns(n)
            dao.insertStops(stopRows)
            dao.insertRoutes(routes.mapIndexed { i, r -> RouteEntity(n, i, r.id, r.shortName, r.longName, r.color, r.textColor, r.type) })
            dao.insertServices(feed.services.mapIndexed { i, s ->
                ServiceEntity(n, i, s.mask, s.start, s.end, s.added.joinToString(","), s.removed.joinToString(","), s.trips)
            })
            dao.insertHeadsigns(feed.headsigns.mapIndexed { i, h -> HeadsignEntity(n, i, h) })
            feed.tripIds?.let { ids -> ids.chunked(5000).forEachIndexed { c, chunk -> dao.insertTrips(chunk.mapIndexed { i, t -> TripEntity(n, c * 5000 + i, t) }) } }
            feed.timetables.entries.chunked(500).forEach { chunk ->
                dao.insertTimetables(chunk.map { (id, blob) -> TimetableEntity(key(feed.network, id), n, blob) })
            }
            dao.insertPatterns(feed.patterns.map { p ->
                PatternEntity(network = n, routeIdx = p.routeIdx, lineName = routes[p.routeIdx].shortName, headsign = feed.headsigns[p.headsignIdx],
                    stopKeys = p.stopIds.joinToString(",") { key(feed.network, it) }, shape = p.shape)
            })
            dao.insertFeed(FeedEntity(n, feed.version, feed.validFrom, feed.validTo, System.currentTimeMillis(), url, stopRows.size))
        }
    }
}

/** Natural order for line names: 1, 2, 10, C1, C2, N1… */
object LineOrder : Comparator<String> {
    private val num = Regex("^(\\D*)(\\d+)(.*)$")
    override fun compare(a: String, b: String): Int {
        val ma = num.find(a); val mb = num.find(b)
        if (ma != null && mb != null) {
            val p = ma.groupValues[1].compareTo(mb.groupValues[1])
            if (p != 0) return p
            val n = ma.groupValues[2].toInt().compareTo(mb.groupValues[2].toInt())
            if (n != 0) return n
            return ma.groupValues[3].compareTo(mb.groupValues[3])
        }
        return a.compareTo(b)
    }
}
