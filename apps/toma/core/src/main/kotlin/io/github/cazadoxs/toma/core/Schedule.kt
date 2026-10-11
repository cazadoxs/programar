package io.github.cazadoxs.toma.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** How a medication repeats. */
enum class ScheduleType {
    /** Every day at the given times. */
    DAILY,

    /** Only on the chosen days of the week, at the given times. */
    WEEKDAYS,

    /** Every N days (2 = alternate days), at the given times. */
    EVERY_N_DAYS,

    /** Every N hours, counted from the first slot. Keeps the rhythm across midnight and DST. */
    INTERVAL_HOURS,

    /** No alarm: the user logs it when they take it. */
    AS_NEEDED,
}

/** A time of day with the amount the user wrote for that dose (in their own units). */
@Serializable
data class DoseSlot(val minuteOfDay: Int, val amount: Double = 1.0) {
    init {
        require(minuteOfDay in 0 until MINUTES_PER_DAY) { "minuteOfDay out of range: $minuteOfDay" }
        require(amount > 0 && amount <= MAX_AMOUNT) { "amount out of range: $amount" }
    }

    val time: LocalTime get() = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_AMOUNT = 1000.0
        fun of(hour: Int, minute: Int, amount: Double = 1.0) = DoseSlot(hour * 60 + minute, amount)
    }
}

/** One expected dose. */
data class Occurrence(val at: Instant, val amount: Double)

@Serializable
data class Schedule(
    val type: ScheduleType,
    /** Times of day. For [ScheduleType.INTERVAL_HOURS] the first slot is the starting time. */
    val slots: List<DoseSlot> = emptyList(),
    /** ISO days of week (1 = Monday … 7 = Sunday), for [ScheduleType.WEEKDAYS]. */
    val weekdays: Set<Int> = emptySet(),
    val everyNDays: Int = 1,
    val intervalHours: Int = 8,
    val startEpochDay: Long,
    /** Last day (inclusive) of the treatment, or null when it has no end. */
    val endEpochDay: Long? = null,
) {
    val startDate: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
    val endDate: LocalDate? get() = endEpochDay?.let(LocalDate::ofEpochDay)

    /** Problems that make the schedule unusable, empty when valid. */
    fun validate(): List<ScheduleError> = buildList {
        if (type != ScheduleType.AS_NEEDED && slots.isEmpty()) add(ScheduleError.NO_TIMES)
        if (slots.size > MAX_SLOTS) add(ScheduleError.TOO_MANY_TIMES)
        if (type == ScheduleType.WEEKDAYS && (weekdays.isEmpty() || weekdays.any { it !in 1..7 })) {
            add(ScheduleError.NO_WEEKDAYS)
        }
        if (type == ScheduleType.EVERY_N_DAYS && everyNDays !in 2..MAX_EVERY_N_DAYS) add(ScheduleError.BAD_EVERY_N_DAYS)
        if (type == ScheduleType.INTERVAL_HOURS && intervalHours !in 1..MAX_INTERVAL_HOURS) {
            add(ScheduleError.BAD_INTERVAL)
        }
        if (endEpochDay != null && endEpochDay < startEpochDay) add(ScheduleError.END_BEFORE_START)
    }

    val isValid: Boolean get() = validate().isEmpty()

    /** Expected doses in `[from, to)`, ordered by time. */
    fun occurrences(from: Instant, to: Instant, zone: ZoneId): List<Occurrence> {
        if (!to.isAfter(from) || !isValid) return emptyList()
        return when (type) {
            ScheduleType.AS_NEEDED -> emptyList()
            ScheduleType.INTERVAL_HOURS -> intervalOccurrences(from, to, zone)
            else -> wallClockOccurrences(from, to, zone)
        }
    }

    /** First expected dose strictly after [after], or null if the treatment has ended. */
    fun nextOccurrence(after: Instant, zone: ZoneId): Occurrence? {
        if (type == ScheduleType.AS_NEEDED || !isValid) return null
        var windowStart = after.plusMillis(1)
        // Look ahead in chunks so sparse schedules (every 60 days) are still found.
        repeat(LOOKAHEAD_CHUNKS) {
            val windowEnd = windowStart.plus(Duration.ofDays(LOOKAHEAD_CHUNK_DAYS))
            occurrences(windowStart, windowEnd, zone).firstOrNull()?.let { return it }
            val end = endDate
            if (end != null && windowEnd.atZone(zone).toLocalDate().isAfter(end)) return null
            windowStart = windowEnd
        }
        return null
    }

    /** Average units per day, used to estimate how long the stock lasts. */
    fun averageUnitsPerDay(): Double {
        if (!isValid) return 0.0
        val perDay = slots.sumOf { it.amount }
        return when (type) {
            ScheduleType.DAILY -> perDay
            ScheduleType.WEEKDAYS -> perDay * weekdays.size / 7.0
            ScheduleType.EVERY_N_DAYS -> perDay / everyNDays
            ScheduleType.INTERVAL_HOURS -> slots.first().amount * 24.0 / intervalHours
            ScheduleType.AS_NEEDED -> 0.0
        }
    }

    private fun matchesDay(date: LocalDate): Boolean {
        if (date.isBefore(startDate)) return false
        endDate?.let { if (date.isAfter(it)) return false }
        return when (type) {
            ScheduleType.DAILY -> true
            ScheduleType.WEEKDAYS -> date.dayOfWeek.value in weekdays
            ScheduleType.EVERY_N_DAYS -> Math.floorMod(date.toEpochDay() - startEpochDay, everyNDays.toLong()) == 0L
            else -> false
        }
    }

    private fun wallClockOccurrences(from: Instant, to: Instant, zone: ZoneId): List<Occurrence> {
        val result = ArrayList<Occurrence>()
        var date = from.atZone(zone).toLocalDate().minusDays(1)
        val last = to.atZone(zone).toLocalDate().plusDays(1)
        val sorted = slots.sortedBy { it.minuteOfDay }
        while (!date.isAfter(last)) {
            if (matchesDay(date)) {
                for (slot in sorted) {
                    // ZonedDateTime.of moves times that fall in a DST gap forward, so no dose is lost.
                    val at = ZonedDateTime.of(date, slot.time, zone).toInstant()
                    if (!at.isBefore(from) && at.isBefore(to)) result += Occurrence(at, slot.amount)
                }
            }
            date = date.plusDays(1)
        }
        return result.distinctBy { it.at }.sortedBy { it.at }
    }

    private fun intervalOccurrences(from: Instant, to: Instant, zone: ZoneId): List<Occurrence> {
        val first = slots.first()
        val anchor = ZonedDateTime.of(startDate, first.time, zone).toInstant()
        val stepMillis = Duration.ofHours(intervalHours.toLong()).toMillis()
        val endExclusive = endDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()
        val limit = if (endExclusive != null && endExclusive.isBefore(to)) endExclusive else to
        val sinceAnchor = from.toEpochMilli() - anchor.toEpochMilli()
        var k = if (sinceAnchor <= 0) 0L else (sinceAnchor + stepMillis - 1) / stepMillis
        val result = ArrayList<Occurrence>()
        while (true) {
            val at = anchor.plusMillis(k * stepMillis)
            if (!at.isBefore(limit)) break
            if (!at.isBefore(from)) result += Occurrence(at, first.amount)
            k++
            if (result.size > MAX_OCCURRENCES_PER_QUERY) break
        }
        return result
    }

    companion object {
        const val MAX_SLOTS = 24
        const val MAX_EVERY_N_DAYS = 60
        const val MAX_INTERVAL_HOURS = 72
        private const val LOOKAHEAD_CHUNK_DAYS = 8L
        private const val LOOKAHEAD_CHUNKS = 50
        private const val MAX_OCCURRENCES_PER_QUERY = 100_000

        fun weekdaysOf(vararg days: DayOfWeek): Set<Int> = days.map { it.value }.toSet()
    }
}

enum class ScheduleError { NO_TIMES, TOO_MANY_TIMES, NO_WEEKDAYS, BAD_EVERY_N_DAYS, BAD_INTERVAL, END_BEFORE_START }
