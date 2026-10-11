package io.github.cazadoxs.toma.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class DoseStatus { TAKEN, SKIPPED, MISSED }

/** What happened with one expected dose (or an as-needed dose, which has no expected time). */
data class DoseRecord(val medicationId: Long, val scheduledAt: Instant, val status: DoseStatus)

data class AdherenceStats(
    val expected: Int,
    val taken: Int,
    val skipped: Int,
    val missed: Int,
) {
    /** Share of expected doses that were taken, 0..100, or null when nothing was expected. */
    val percent: Int? get() = if (expected == 0) null else Math.round(taken * 100.0 / expected).toInt()
}

data class ExpectedDose(val medicationId: Long, val at: Instant)

object Adherence {
    /**
     * Compares expected doses with what was recorded. A past dose without a record counts as
     * missed once [graceMillis] has passed; a future or still-ringing one is not counted.
     */
    fun stats(
        expected: List<ExpectedDose>,
        records: List<DoseRecord>,
        now: Instant,
        graceMillis: Long = DEFAULT_GRACE_MILLIS,
    ): AdherenceStats {
        val byKey = records.associateBy { it.medicationId to it.scheduledAt }
        var taken = 0
        var skipped = 0
        var missed = 0
        var counted = 0
        for (dose in expected) {
            val record = byKey[dose.medicationId to dose.at]
            when (record?.status) {
                DoseStatus.TAKEN -> taken++
                DoseStatus.SKIPPED -> skipped++
                DoseStatus.MISSED -> missed++
                null -> if (dose.at.plusMillis(graceMillis).isBefore(now)) missed++ else continue
            }
            counted++
        }
        return AdherenceStats(counted, taken, skipped, missed)
    }

    /**
     * Consecutive days, ending today, on which every expected dose was taken. Days without
     * expected doses don't break the streak. Today counts only once all its due doses are taken.
     */
    fun streakDays(
        expected: List<ExpectedDose>,
        records: List<DoseRecord>,
        today: LocalDate,
        zone: ZoneId,
        now: Instant,
    ): Int {
        val taken = records.filter { it.status == DoseStatus.TAKEN }
            .map { it.medicationId to it.scheduledAt }.toSet()
        val byDay = expected.groupBy { LocalDate.ofInstant(it.at, zone) }
        var streak = 0
        var day = today
        val earliest = byDay.keys.minOrNull() ?: return 0
        while (!day.isBefore(earliest)) {
            val doses = byDay[day].orEmpty().filter { !it.at.isAfter(now) }
            if (doses.isNotEmpty()) {
                if (doses.all { (it.medicationId to it.at) in taken }) {
                    streak++
                } else if (day != today) {
                    break
                }
            }
            day = day.minusDays(1)
        }
        return streak
    }

    const val DEFAULT_GRACE_MILLIS = 60 * 60 * 1000L
}

object Refill {
    /** Whole days the stock lasts at the schedule's pace, or null when it can't be estimated. */
    fun daysLeft(stock: Double?, schedule: Schedule): Int? {
        if (stock == null) return null
        val perDay = schedule.averageUnitsPerDay()
        if (perDay <= 0.0) return null
        return kotlin.math.floor(stock.coerceAtLeast(0.0) / perDay).toInt()
    }

    fun isLow(stock: Double?, schedule: Schedule, warnDays: Int): Boolean {
        if (stock == null) return false
        if (stock <= 0.0) return true
        val days = daysLeft(stock, schedule) ?: return false
        return days <= warnDays
    }
}
