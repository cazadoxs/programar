package vlc.transit.core.gtfs

import java.time.LocalDate
import java.time.ZoneId

val VALENCIA_ZONE: ZoneId = ZoneId.of("Europe/Madrid")

data class ScheduledDeparture(
    val routeIdx: Int,
    val headsignIdx: Int,
    val tripIdx: Int,
    val epochSec: Long,
    /** True when the feed had lapsed and we inferred today's times from a matching past day. */
    val approximate: Boolean,
)

object Schedule {
    /**
     * Departures from one stop's [blob] between [nowEpochSec] - [graceSec] and
     * [nowEpochSec] + [horizonSec], sorted by time. Late-night trips listed as
     * 24:xx-27:xx on the previous service day are included.
     */
    fun upcoming(
        blob: ByteArray,
        calendar: ServiceCalendar,
        nowEpochSec: Long,
        horizonSec: Int = 2 * 3600,
        graceSec: Int = 60,
        limit: Int = 60,
        zone: ZoneId = VALENCIA_ZONE,
    ): List<ScheduledDeparture> {
        val today = java.time.Instant.ofEpochSecond(nowEpochSec).atZone(zone).toLocalDate()
        val entries = Timetable.decodeAll(blob)
        val out = ArrayList<ScheduledDeparture>()
        for (day in listOf(today.minusDays(1), today, today.plusDays(1))) {
            val resolved = calendar.resolve(day)
            val active = calendar.active(resolved.date)
            val base = day.atStartOfDay(zone).toEpochSecond()
            for (e in entries) {
                if (e.serviceIdx !in active.indices || !active[e.serviceIdx]) continue
                val t = base + e.secs
                if (t < nowEpochSec - graceSec || t > nowEpochSec + horizonSec) continue
                out += ScheduledDeparture(e.routeIdx, e.headsignIdx, e.tripIdx, t, resolved.approximate)
            }
        }
        out.sortBy { it.epochSec }
        return if (out.size > limit) out.subList(0, limit) else out
    }

    /** Every departure on [date] (for the full-day timetable view), as seconds of that day. */
    fun day(blob: ByteArray, calendar: ServiceCalendar, date: LocalDate): Pair<List<Timetable.Entry>, Boolean> {
        val resolved = calendar.resolve(date)
        val active = calendar.active(resolved.date)
        val list = Timetable.decodeAll(blob).filter { it.serviceIdx in active.indices && active[it.serviceIdx] }.sortedBy { it.secs }
        return list to resolved.approximate
    }
}
