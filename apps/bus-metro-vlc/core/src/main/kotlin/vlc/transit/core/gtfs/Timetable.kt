package vlc.transit.core.gtfs

import vlc.transit.core.util.VarintReader
import vlc.transit.core.util.VarintWriter

/**
 * A stop's whole timetable as one compact blob (a few KB per stop instead of
 * one SQLite row per departure), so the full EMT + Metro + Cercanías
 * timetables fit in a few MB and work offline.
 *
 * Layout: version byte, flags byte, varint group count, then per group
 * (routeIdx, headsignIdx, serviceIdx, count) followed by `count` departures,
 * each a delta-encoded second-of-service-day and, when FLAG_TRIPS is set, a
 * trip index (needed to apply live delays by trip id).
 */
object Timetable {
    private const val VERSION = 1
    private const val FLAG_TRIPS = 1

    data class Entry(val routeIdx: Int, val headsignIdx: Int, val serviceIdx: Int, val secs: Int, val tripIdx: Int)

    class Group(val routeIdx: Int, val headsignIdx: Int, val serviceIdx: Int) {
        val secs = ArrayList<Int>()
        val trips = ArrayList<Int>()
    }

    fun encode(groups: Collection<Group>, withTrips: Boolean): ByteArray {
        val w = VarintWriter()
        w.byte(VERSION)
        w.byte(if (withTrips) FLAG_TRIPS else 0)
        w.write(groups.size)
        for (g in groups) {
            val order = g.secs.indices.sortedBy { g.secs[it] }
            w.write(g.routeIdx); w.write(g.headsignIdx); w.write(g.serviceIdx); w.write(order.size)
            var prev = 0
            for (i in order) {
                val s = g.secs[i]
                w.write(s - prev); prev = s
                if (withTrips) w.write(g.trips[i])
            }
        }
        return w.toByteArray()
    }

    fun decode(blob: ByteArray, block: (Entry) -> Unit) {
        val r = VarintReader(blob)
        val version = r.byte()
        require(version == VERSION) { "unknown timetable version $version" }
        val withTrips = r.byte() and FLAG_TRIPS != 0
        val n = r.read()
        repeat(n) {
            val route = r.read(); val head = r.read(); val svc = r.read(); val count = r.read()
            var t = 0
            repeat(count) {
                t += r.read()
                val trip = if (withTrips) r.read() else -1
                block(Entry(route, head, svc, t, trip))
            }
        }
    }

    fun decodeAll(blob: ByteArray): List<Entry> = ArrayList<Entry>().also { l -> decode(blob) { l += it } }
}
