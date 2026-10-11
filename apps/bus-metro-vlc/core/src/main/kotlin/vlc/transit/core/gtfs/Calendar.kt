package vlc.transit.core.gtfs

import java.time.DayOfWeek
import java.time.LocalDate

/** yyyymmdd int <-> LocalDate. */
fun ymd(d: LocalDate) = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
fun ymdToDate(v: Int): LocalDate = LocalDate.of(v / 10000, (v / 100) % 100, v % 100)

fun parseYmd(s: String): Int {
    val t = s.trim()
    if (t.length != 8) return 0
    return t.toIntOrNull() ?: 0
}

/**
 * One GTFS service: a weekday mask (bit 0 = Monday) valid between [start] and
 * [end] (yyyymmdd, inclusive), plus explicit added/removed dates.
 */
data class Service(
    val mask: Int,
    val start: Int,
    val end: Int,
    val added: Set<Int> = emptySet(),
    val removed: Set<Int> = emptySet(),
    /** How many trips use this service; used to pick a representative day when the feed has lapsed. */
    val trips: Int = 0,
) {
    fun runsOn(date: LocalDate): Boolean {
        val d = ymd(date)
        if (d in removed) return false
        if (d in added) return true
        if (d < start || d > end) return false
        return mask and (1 shl (date.dayOfWeek.value - 1)) != 0
    }
}

/**
 * The calendar of one feed. Feeds go stale (Metrovalencia's has lapsed for
 * months at a time), so when asked about a date outside the feed's range we
 * answer for the nearest date inside it that falls on the same weekday and say
 * so via [Resolved.approximate], instead of showing an empty timetable.
 */
class ServiceCalendar(val services: List<Service>, val validFrom: Int, val validTo: Int) {

    data class Resolved(val date: LocalDate, val approximate: Boolean)

    fun resolve(date: LocalDate): Resolved {
        if (validFrom == 0 || validTo == 0) return Resolved(date, false)
        val d = ymd(date)
        if (d in validFrom..validTo) return Resolved(date, false)
        // Among the feed's days with the same weekday, take the busiest one
        // (a regular day, not a holiday stub), preferring the most recent.
        val from = ymdToDate(validFrom)
        val to = ymdToDate(validTo)
        var probe = to
        while (probe.dayOfWeek != date.dayOfWeek) probe = probe.minusDays(1)
        var best: LocalDate? = null
        var bestTrips = -1
        while (!probe.isBefore(from)) {
            val n = services.sumOf { if (it.runsOn(probe)) it.trips.coerceAtLeast(1) else 0 }
            if (n > bestTrips) { best = probe; bestTrips = n }
            probe = probe.minusWeeks(1)
        }
        return Resolved(best ?: date, true)
    }

    /** Indices of services running on [date] (already resolved). */
    fun active(date: LocalDate): BooleanArray = BooleanArray(services.size) { services[it].runsOn(date) }

    companion object {
        fun maskOf(days: List<Boolean>): Int = days.foldIndexed(0) { i, acc, on -> if (on) acc or (1 shl i) else acc }
        fun runsOnWeekday(mask: Int, day: DayOfWeek) = mask and (1 shl (day.value - 1)) != 0
    }
}
