package io.github.cazadoxs.toma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val start = LocalDate.of(2026, 3, 1)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Instant =
        LocalDateTime.of(y, mo, d, h, mi).atZone(madrid).toInstant()

    private fun local(i: Instant) = LocalDateTime.ofInstant(i, madrid)

    @Test
    fun dailyProducesEveryTimeEveryDay() {
        val s = Schedule(ScheduleType.DAILY, listOf(DoseSlot.of(21, 0), DoseSlot.of(9, 0)), startEpochDay = start.toEpochDay())
        val occ = s.occurrences(at(2026, 3, 1, 0), at(2026, 3, 3, 0), madrid)
        assertEquals(4, occ.size)
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 0), local(occ[0].at))
        assertEquals(LocalDateTime.of(2026, 3, 2, 21, 0), local(occ[3].at))
    }

    @Test
    fun nothingBeforeStartOrAfterEnd() {
        val s = Schedule(
            ScheduleType.DAILY, listOf(DoseSlot.of(9, 0)),
            startEpochDay = start.toEpochDay(), endEpochDay = start.plusDays(2).toEpochDay(),
        )
        val occ = s.occurrences(at(2026, 2, 20, 0), at(2026, 3, 20, 0), madrid)
        assertEquals(3, occ.size)
        assertNull(s.nextOccurrence(at(2026, 3, 3, 10), madrid))
    }

    @Test
    fun alternateDays() {
        val s = Schedule(ScheduleType.EVERY_N_DAYS, listOf(DoseSlot.of(8, 30)), everyNDays = 2, startEpochDay = start.toEpochDay())
        val days = s.occurrences(at(2026, 3, 1, 0), at(2026, 3, 8, 0), madrid).map { local(it.at).dayOfMonth }
        assertEquals(listOf(1, 3, 5, 7), days)
    }

    @Test
    fun weekdaysOnly() {
        val s = Schedule(
            ScheduleType.WEEKDAYS, listOf(DoseSlot.of(10, 0)),
            weekdays = Schedule.weekdaysOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), startEpochDay = start.toEpochDay(),
        )
        val occ = s.occurrences(at(2026, 3, 1, 0), at(2026, 3, 15, 0), madrid)
        assertTrue(occ.all { local(it.at).dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY) })
        assertEquals(4, occ.size)
    }

    @Test
    fun intervalKeepsRhythmAcrossMidnight() {
        val s = Schedule(ScheduleType.INTERVAL_HOURS, listOf(DoseSlot.of(8, 0)), intervalHours = 8, startEpochDay = start.toEpochDay())
        val hours = s.occurrences(at(2026, 3, 1, 0), at(2026, 3, 2, 12), madrid).map { local(it.at).hour }
        assertEquals(listOf(8, 16, 0, 8), hours)
    }

    @Test
    fun intervalIsAbsoluteAcrossDst() {
        // Spain moves clocks forward on 29 March 2026 at 02:00.
        val s = Schedule(ScheduleType.INTERVAL_HOURS, listOf(DoseSlot.of(20, 0)), intervalHours = 12, startEpochDay = LocalDate.of(2026, 3, 28).toEpochDay())
        val occ = s.occurrences(at(2026, 3, 28, 0), at(2026, 3, 30, 0), madrid)
        assertEquals(12 * 3600L, occ[1].at.epochSecond - occ[0].at.epochSecond)
        assertEquals(LocalDateTime.of(2026, 3, 29, 9, 0), local(occ[1].at))
    }

    @Test
    fun dstGapTimeIsNotLost() {
        val s = Schedule(ScheduleType.DAILY, listOf(DoseSlot.of(2, 30)), startEpochDay = LocalDate.of(2026, 3, 28).toEpochDay())
        val occ = s.occurrences(at(2026, 3, 28, 0), at(2026, 3, 31, 0), madrid)
        assertEquals(3, occ.size)
    }

    @Test
    fun nextOccurrenceFindsSparseSchedules() {
        val s = Schedule(ScheduleType.EVERY_N_DAYS, listOf(DoseSlot.of(9, 0)), everyNDays = 60, startEpochDay = start.toEpochDay())
        val next = s.nextOccurrence(at(2026, 3, 1, 10), madrid)!!
        assertEquals(LocalDateTime.of(2026, 4, 30, 9, 0), local(next.at))
    }

    @Test
    fun nextOccurrenceIsStrictlyAfter() {
        val s = Schedule(ScheduleType.DAILY, listOf(DoseSlot.of(9, 0)), startEpochDay = start.toEpochDay())
        val next = s.nextOccurrence(at(2026, 3, 2, 9), madrid)!!
        assertEquals(LocalDateTime.of(2026, 3, 3, 9, 0), local(next.at))
    }

    @Test
    fun asNeededHasNoAlarms() {
        val s = Schedule(ScheduleType.AS_NEEDED, startEpochDay = start.toEpochDay())
        assertTrue(s.isValid)
        assertNull(s.nextOccurrence(at(2026, 3, 2, 9), madrid))
    }

    @Test
    fun validation() {
        assertEquals(
            listOf(ScheduleError.NO_TIMES),
            Schedule(ScheduleType.DAILY, startEpochDay = 0).validate(),
        )
        assertTrue(ScheduleError.NO_WEEKDAYS in Schedule(ScheduleType.WEEKDAYS, listOf(DoseSlot.of(9, 0)), startEpochDay = 0).validate())
        assertTrue(ScheduleError.BAD_INTERVAL in Schedule(ScheduleType.INTERVAL_HOURS, listOf(DoseSlot.of(9, 0)), intervalHours = 0, startEpochDay = 0).validate())
        assertTrue(ScheduleError.END_BEFORE_START in Schedule(ScheduleType.DAILY, listOf(DoseSlot.of(9, 0)), startEpochDay = 10, endEpochDay = 5).validate())
    }

    @Test
    fun unitsPerDay() {
        val twice = listOf(DoseSlot.of(9, 0, 1.0), DoseSlot.of(21, 0, 0.5))
        assertEquals(1.5, Schedule(ScheduleType.DAILY, twice, startEpochDay = 0).averageUnitsPerDay(), 1e-9)
        assertEquals(0.75, Schedule(ScheduleType.EVERY_N_DAYS, twice, everyNDays = 2, startEpochDay = 0).averageUnitsPerDay(), 1e-9)
        assertEquals(3.0, Schedule(ScheduleType.INTERVAL_HOURS, listOf(DoseSlot.of(8, 0)), intervalHours = 8, startEpochDay = 0).averageUnitsPerDay(), 1e-9)
    }
}
