package io.github.cazadoxs.toma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AdherenceTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(d: Int, h: Int) = LocalDateTime.of(2026, 5, d, h, 0).atZone(zone).toInstant()

    @Test
    fun countsUnrecordedPastDosesAsMissed() {
        val expected = listOf(ExpectedDose(1, at(1, 9)), ExpectedDose(1, at(1, 21)), ExpectedDose(1, at(2, 9)))
        val records = listOf(DoseRecord(1, at(1, 9), DoseStatus.TAKEN))
        val stats = Adherence.stats(expected, records, now = at(2, 9))
        // The 2nd-day dose is still inside its grace period.
        assertEquals(AdherenceStats(expected = 2, taken = 1, skipped = 0, missed = 1), stats)
        assertEquals(50, stats.percent)
    }

    @Test
    fun noExpectedDosesMeansNoPercent() {
        assertNull(Adherence.stats(emptyList(), emptyList(), at(1, 9)).percent)
    }

    @Test
    fun streakStopsAtFirstIncompleteDay() {
        val expected = (1..4).map { ExpectedDose(1, at(it, 9)) }
        val records = listOf(2, 3, 4).map { DoseRecord(1, at(it, 9), DoseStatus.TAKEN) }
        assertEquals(3, Adherence.streakDays(expected, records, LocalDate.of(2026, 5, 4), zone, at(4, 12)))
    }

    @Test
    fun pendingTodayDoesNotBreakStreak() {
        val expected = (1..3).map { ExpectedDose(1, at(it, 9)) } + ExpectedDose(1, at(3, 21))
        val records = (1..3).map { DoseRecord(1, at(it, 9), DoseStatus.TAKEN) }
        assertEquals(3, Adherence.streakDays(expected, records, LocalDate.of(2026, 5, 3), zone, at(3, 22)) + 1)
    }

    @Test
    fun refillEstimate() {
        val s = Schedule(ScheduleType.DAILY, listOf(DoseSlot.of(9, 0), DoseSlot.of(21, 0)), startEpochDay = 0)
        assertEquals(10, Refill.daysLeft(20.0, s))
        assertTrue(Refill.isLow(10.0, s, warnDays = 7))
        assertFalse(Refill.isLow(30.0, s, warnDays = 7))
        assertTrue(Refill.isLow(0.0, s, warnDays = 0))
        assertNull(Refill.daysLeft(null, s))
    }
}
