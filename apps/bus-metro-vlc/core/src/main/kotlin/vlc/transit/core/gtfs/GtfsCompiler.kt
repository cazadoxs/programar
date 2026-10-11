package vlc.transit.core.gtfs

import vlc.transit.core.Network
import vlc.transit.core.util.BBox
import vlc.transit.core.util.Polyline
import vlc.transit.core.util.distanceMeters
import vlc.transit.core.util.tidyName
import java.io.InputStream

/** Where the compiler reads GTFS files from (a zip on the phone, strings in tests). */
fun interface GtfsSource {
    /** Opens `name` (e.g. "stops.txt"), or returns null when the feed lacks it. */
    fun open(name: String): InputStream?
}

data class CompileOptions(
    val network: Network,
    /** Stops outside this box are dropped (Renfe's feed covers all of Spain). */
    val bbox: BBox? = BBox.VALENCIA_REGION,
    /** Keep GTFS trip ids so live delays (matched by trip id) can be applied. */
    val keepTripIds: Boolean = false,
    val routeFilter: ((routeId: String) -> Boolean)? = null,
    /** Hard cap protecting the phone from a malformed or hostile feed. */
    val maxStopTimes: Int = 6_000_000,
)

data class CStop(
    val id: String,
    val code: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val routeIdxs: List<Int>,
    val wheelchair: Boolean,
)

data class CRoute(
    val id: String,
    val shortName: String,
    val longName: String,
    val color: Int?,
    val textColor: Int?,
    val type: Int,
)

data class CPattern(val routeIdx: Int, val headsignIdx: Int, val stopIds: List<String>, val shape: String?)

class CompiledFeed(
    val network: Network,
    val routes: List<CRoute>,
    val stops: List<CStop>,
    val services: List<Service>,
    val headsigns: List<String>,
    val tripIds: List<String>?,
    val timetables: Map<String, ByteArray>,
    val patterns: List<CPattern>,
    val validFrom: Int,
    val validTo: Int,
    val version: String,
) {
    val stopTimeCount: Int get() = timetables.values.sumOf { b -> var n = 0; Timetable.decode(b) { n++ }; n }
}

class GtfsFormatException(msg: String) : Exception(msg)

/**
 * Turns a GTFS feed into the compact form the app stores: stops (platforms
 * folded into their station), routes, calendars, one timetable blob per stop,
 * and one representative stop sequence + shape per line and direction.
 */
class GtfsCompiler(private val source: GtfsSource, private val opt: CompileOptions) {

    private class StopRow(val id: String, val code: String, val name: String, val lat: Double, val lon: Double, val parent: String, val wheelchair: Boolean)

    private class IntList {
        var a = IntArray(16); var n = 0
        fun add(v: Int) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
    }

    fun compile(): CompiledFeed {
        // --- stops -------------------------------------------------------
        val stopRows = HashMap<String, StopRow>()
        read("stops.txt", required = true) { r ->
            val cId = r.col("stop_id"); val cCode = r.col("stop_code"); val cName = r.col("stop_name")
            val cLat = r.col("stop_lat"); val cLon = r.col("stop_lon"); val cParent = r.col("parent_station")
            val cLoc = r.col("location_type"); val cWc = r.col("wheelchair_boarding")
            r.forEachRow { row ->
                val loc = row[cLoc]
                if (loc.isNotEmpty() && loc != "0" && loc != "1") return@forEachRow // entrances, nodes, areas
                val id = row[cId]
                if (id.isEmpty()) return@forEachRow
                val lat = row[cLat].toDoubleOrNull() ?: return@forEachRow
                val lon = row[cLon].toDoubleOrNull() ?: return@forEachRow
                stopRows[id] = StopRow(id, row[cCode].ifEmpty { id }, tidyName(row[cName]), lat, lon, row[cParent], row[cWc] == "1")
            }
        }
        fun canonical(id: String): StopRow? {
            val s = stopRows[id] ?: return null
            val p = if (s.parent.isNotEmpty()) stopRows[s.parent] ?: s else s
            return if (opt.bbox == null || opt.bbox.contains(p.lat, p.lon)) p else null
        }

        // --- routes ------------------------------------------------------
        data class RouteRow(val id: String, val short: String, val long: String, val color: Int?, val text: Int?, val type: Int)
        val routeRows = ArrayList<RouteRow>()
        val routeIndex = HashMap<String, Int>()
        read("routes.txt", required = true) { r ->
            val cId = r.col("route_id"); val cS = r.col("route_short_name"); val cL = r.col("route_long_name")
            val cC = r.col("route_color"); val cT = r.col("route_text_color"); val cType = r.col("route_type")
            r.forEachRow { row ->
                val id = row[cId]
                if (id.isEmpty() || opt.routeFilter?.invoke(id) == false) return@forEachRow
                routeIndex[id] = routeRows.size
                routeRows += RouteRow(id, tidyName(row[cS]), tidyName(row[cL]), parseColor(row[cC]), parseColor(row[cT]), row[cType].toIntOrNull() ?: 3)
            }
        }

        // --- trips -------------------------------------------------------
        val tripIndex = HashMap<String, Int>()
        val tripIds = ArrayList<String>()
        val tripRoute = IntList(); val tripService = IntList(); val tripDir = IntList()
        val tripHeadsign = ArrayList<String>(); val tripShape = ArrayList<String>()
        val serviceIndex = HashMap<String, Int>(); val serviceIds = ArrayList<String>()
        read("trips.txt", required = true) { r ->
            val cR = r.col("route_id"); val cS = r.col("service_id"); val cT = r.col("trip_id")
            val cH = r.col("trip_headsign"); val cD = r.col("direction_id"); val cSh = r.col("shape_id")
            r.forEachRow { row ->
                val route = routeIndex[row[cR]] ?: return@forEachRow
                val tid = row[cT]
                if (tid.isEmpty() || tripIndex.containsKey(tid)) return@forEachRow
                val sid = row[cS]
                val sIdx = serviceIndex.getOrPut(sid) { serviceIds += sid; serviceIds.size - 1 }
                tripIndex[tid] = tripIds.size
                tripIds += tid
                tripRoute.add(route); tripService.add(sIdx); tripDir.add(row[cD].toIntOrNull() ?: 0)
                tripHeadsign += tidyName(row[cH]); tripShape += row[cSh]
            }
        }
        val nTrips = tripIds.size

        // --- stop_times, pass 1: timetable per stop ----------------------
        val perStop = HashMap<String, IntList>()      // canonical stop id -> (trip, secs)*
        val tripKept = IntArray(nTrips)               // kept stop count per trip
        val tripLastSeq = IntArray(nTrips) { -1 }
        val tripLastStop = arrayOfNulls<String>(nTrips)
        var rows = 0
        read("stop_times.txt", required = true) { r ->
            val cT = r.col("trip_id"); val cA = r.col("arrival_time"); val cD = r.col("departure_time")
            val cS = r.col("stop_id"); val cQ = r.col("stop_sequence")
            r.forEachRow { row ->
                if (++rows > opt.maxStopTimes) throw GtfsFormatException("feed too large")
                val t = tripIndex[row[cT]] ?: return@forEachRow
                val rawStop = row[cS]
                val seq = row[cQ].toIntOrNull() ?: 0
                if (seq >= tripLastSeq[t]) { tripLastSeq[t] = seq; tripLastStop[t] = rawStop }
                val stop = canonical(rawStop) ?: return@forEachRow
                var secs = parseGtfsTime(row[cD])
                if (secs < 0) secs = parseGtfsTime(row[cA])
                if (secs < 0) return@forEachRow // untimed stop: GTFS allows interpolation; skip it
                perStop.getOrPut(stop.id) { IntList() }.apply { add(t); add(secs) }
                tripKept[t]++
            }
        }
        if (perStop.isEmpty()) throw GtfsFormatException("no timed stops inside the area")

        // --- headsigns: trip_headsign, or the name of the trip's last stop -
        val headIndex = HashMap<String, Int>(); val headsigns = ArrayList<String>()
        val tripHead = IntArray(nTrips) { t ->
            val h = tripHeadsign[t].ifEmpty { tripLastStop[t]?.let { stopRows[it]?.name } ?: "" }
            headIndex.getOrPut(h) { headsigns += h; headsigns.size - 1 }
        }

        // --- compact the routes and services actually used ----------------
        val routeMap = IntArray(routeRows.size) { -1 }; val routes = ArrayList<CRoute>()
        val svcMap = IntArray(serviceIds.size) { -1 }; val usedServiceIds = ArrayList<String>()
        for (t in 0 until nTrips) {
            if (tripKept[t] == 0) continue
            val ro = tripRoute.a[t]
            if (routeMap[ro] < 0) { routeMap[ro] = routes.size; routeRows[ro].let { routes += CRoute(it.id, it.short.ifEmpty { it.long }, it.long, it.color, it.text, it.type) } }
            val sv = tripService.a[t]
            if (svcMap[sv] < 0) { svcMap[sv] = usedServiceIds.size; usedServiceIds += serviceIds[sv] }
        }
        // Trip indices stored in blobs must be dense too when trip ids are kept.
        val tripMap = IntArray(nTrips) { -1 }; val keptTripIds = ArrayList<String>()
        if (opt.keepTripIds) for (t in 0 until nTrips) if (tripKept[t] > 0) { tripMap[t] = keptTripIds.size; keptTripIds += tripIds[t] }

        // --- encode timetables -------------------------------------------
        val timetables = HashMap<String, ByteArray>(perStop.size)
        val stopRoutes = HashMap<String, MutableSet<Int>>()
        for ((stopId, list) in perStop) {
            val groups = LinkedHashMap<Long, Timetable.Group>()
            var i = 0
            while (i < list.n) {
                val t = list.a[i]; val secs = list.a[i + 1]; i += 2
                val ro = routeMap[tripRoute.a[t]]; val hd = tripHead[t]; val sv = svcMap[tripService.a[t]]
                val k = (ro.toLong() shl 42) or (hd.toLong() shl 21) or sv.toLong()
                val g = groups.getOrPut(k) { Timetable.Group(ro, hd, sv) }
                g.secs += secs
                if (opt.keepTripIds) g.trips += tripMap[t]
                stopRoutes.getOrPut(stopId) { sortedSetOf() } += ro
            }
            timetables[stopId] = Timetable.encode(groups.values, opt.keepTripIds)
        }

        // --- stops -------------------------------------------------------
        val stops = perStop.keys.map { id ->
            val s = stopRows.getValue(id)
            CStop(s.id, s.code, s.name, s.lat, s.lon, stopRoutes[id]?.toList() ?: emptyList(), s.wheelchair)
        }

        // --- patterns: longest trip per (route, headsign) ----------------
        val bestTrip = HashMap<Long, Int>()
        for (t in 0 until nTrips) {
            if (tripKept[t] == 0) continue
            val k = (routeMap[tripRoute.a[t]].toLong() shl 32) or tripHead[t].toLong()
            val cur = bestTrip[k]
            if (cur == null || tripKept[t] > tripKept[cur]) bestTrip[k] = t
        }
        val wanted = bestTrip.values.toHashSet()
        val seqs = HashMap<Int, MutableList<Pair<Int, String>>>()
        read("stop_times.txt", required = true) { r ->
            val cT = r.col("trip_id"); val cS = r.col("stop_id"); val cQ = r.col("stop_sequence")
            r.forEachRow { row ->
                val t = tripIndex[row[cT]] ?: return@forEachRow
                if (t !in wanted) return@forEachRow
                val stop = canonical(row[cS]) ?: return@forEachRow
                seqs.getOrPut(t) { ArrayList() } += (row[cQ].toIntOrNull() ?: 0) to stop.id
            }
        }
        val wantedShapes = wanted.mapNotNull { t -> tripShape[t].takeIf { it.isNotEmpty() } }.toHashSet()
        val shapes = readShapes(wantedShapes)
        val patterns = bestTrip.map { (k, t) ->
            val stopsInOrder = seqs[t].orEmpty().sortedBy { it.first }.map { it.second }
                .fold(ArrayList<String>()) { acc, s -> if (acc.lastOrNull() != s) acc += s; acc }
            CPattern((k shr 32).toInt(), (k and 0xffffffffL).toInt(), stopsInOrder, shapes[tripShape[t]])
        }

        // --- calendar ----------------------------------------------------
        val svcBuild = HashMap<Int, Service>()
        read("calendar.txt", required = false) { r ->
            val cId = r.col("service_id")
            val days = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday").map { r.col(it) }
            val cS = r.col("start_date"); val cE = r.col("end_date")
            r.forEachRow { row ->
                val raw = serviceIndex[row[cId]] ?: return@forEachRow
                val idx = svcMap[raw].takeIf { it >= 0 } ?: return@forEachRow
                svcBuild[idx] = Service(ServiceCalendar.maskOf(days.map { row[it] == "1" }), parseYmd(row[cS]), parseYmd(row[cE]))
            }
        }
        val added = HashMap<Int, MutableSet<Int>>(); val removed = HashMap<Int, MutableSet<Int>>()
        read("calendar_dates.txt", required = false) { r ->
            val cId = r.col("service_id"); val cD = r.col("date"); val cX = r.col("exception_type")
            r.forEachRow { row ->
                val raw = serviceIndex[row[cId]] ?: return@forEachRow
                val idx = svcMap[raw].takeIf { it >= 0 } ?: return@forEachRow
                val d = parseYmd(row[cD]); if (d == 0) return@forEachRow
                when (row[cX]) { "1" -> added.getOrPut(idx) { HashSet() } += d; "2" -> removed.getOrPut(idx) { HashSet() } += d }
            }
        }
        val svcTrips = IntArray(usedServiceIds.size)
        for (t in 0 until nTrips) if (tripKept[t] > 0) svcTrips[svcMap[tripService.a[t]]]++
        val services = usedServiceIds.indices.map { i ->
            (svcBuild[i] ?: Service(0, 0, 0)).copy(added = added[i] ?: emptySet(), removed = removed[i] ?: emptySet(), trips = svcTrips[i])
        }

        // --- validity ----------------------------------------------------
        var from = 0; var to = 0; var version = ""
        read("feed_info.txt", required = false) { r ->
            val cS = r.col("feed_start_date"); val cE = r.col("feed_end_date"); val cV = r.col("feed_version")
            r.forEachRow { row -> from = parseYmd(row[cS]); to = parseYmd(row[cE]); version = row[cV] }
        }
        if (from == 0 || to == 0) {
            val dates = services.flatMap { s -> listOfNotNull(s.start.takeIf { it > 0 }, s.end.takeIf { it > 0 }) + s.added }
            from = dates.minOrNull() ?: 0; to = dates.maxOrNull() ?: 0
        }

        return CompiledFeed(opt.network, routes, stops, services, headsigns, if (opt.keepTripIds) keptTripIds else null,
            timetables, patterns, from, to, version)
    }

    private fun readShapes(wanted: Set<String>): Map<String, String> {
        if (wanted.isEmpty()) return emptyMap()
        val pts = HashMap<String, MutableList<Triple<Int, Double, Double>>>()
        read("shapes.txt", required = false) { r ->
            val cId = r.col("shape_id"); val cLa = r.col("shape_pt_lat"); val cLo = r.col("shape_pt_lon"); val cS = r.col("shape_pt_sequence")
            r.forEachRow { row ->
                val id = row[cId]
                if (id !in wanted) return@forEachRow
                val la = row[cLa].toDoubleOrNull() ?: return@forEachRow
                val lo = row[cLo].toDoubleOrNull() ?: return@forEachRow
                pts.getOrPut(id) { ArrayList() } += Triple(row[cS].toIntOrNull() ?: 0, la, lo)
            }
        }
        return pts.mapValues { (_, list) ->
            val sorted = list.sortedBy { it.first }
            val thin = ArrayList<Pair<Double, Double>>()
            for (p in sorted) {
                val last = thin.lastOrNull()
                if (last == null || distanceMeters(last.first, last.second, p.second, p.third) > 8.0) thin += p.second to p.third
            }
            sorted.lastOrNull()?.let { if (thin.last() != (it.second to it.third)) thin += it.second to it.third }
            Polyline.encode(thin)
        }
    }

    private inline fun read(name: String, required: Boolean, block: (CsvReader) -> Unit) {
        val stream = source.open(name)
        if (stream == null) {
            if (required) throw GtfsFormatException("missing $name")
            return
        }
        CsvReader(stream).use(block)
    }
}
