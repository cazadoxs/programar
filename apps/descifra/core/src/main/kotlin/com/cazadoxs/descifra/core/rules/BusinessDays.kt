package com.cazadoxs.descifra.core.rules

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Spanish administrative deadline arithmetic (Ley 39/2015, art. 30, and LGT art. 62).
 *
 * Only national holidays are known; regional and local ones are not, which is why every computed
 * deadline is marked as estimated in the UI.
 */
object BusinessDays {

    fun nationalHolidays(year: Int): Set<LocalDate> {
        val easter = easterSunday(year)
        return setOf(
            LocalDate.of(year, 1, 1),
            LocalDate.of(year, 1, 6),
            easter.minusDays(2), // Viernes Santo
            LocalDate.of(year, 5, 1),
            LocalDate.of(year, 8, 15),
            LocalDate.of(year, 10, 12),
            LocalDate.of(year, 11, 1),
            LocalDate.of(year, 12, 6),
            LocalDate.of(year, 12, 8),
            LocalDate.of(year, 12, 25),
        )
    }

    /** Anonymous Gregorian algorithm. */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    fun isBusinessDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY &&
            date.dayOfWeek != DayOfWeek.SUNDAY &&
            date !in nationalHolidays(date.year)

    fun nextBusinessDayIfNeeded(date: LocalDate): LocalDate {
        var d = date
        while (!isBusinessDay(d)) d = d.plusDays(1)
        return d
    }

    /** "N días hábiles": counted from the day after notification, skipping weekends and holidays. */
    fun addBusinessDays(notified: LocalDate, days: Int): LocalDate {
        var d = notified
        var left = days
        while (left > 0) {
            d = d.plusDays(1)
            if (isBusinessDay(d)) left--
        }
        return d
    }

    /** "N días naturales": calendar days; if the last one is not a business day it moves forward. */
    fun addCalendarDays(notified: LocalDate, days: Int): LocalDate =
        nextBusinessDayIfNeeded(notified.plusDays(days.toLong()))

    /**
     * "N meses": ends on the same day number of the target month (or its last day if shorter),
     * moved to the next business day when needed.
     */
    fun addMonths(notified: LocalDate, months: Int): LocalDate {
        val target = YearMonth.from(notified).plusMonths(months.toLong())
        val day = minOf(notified.dayOfMonth, target.lengthOfMonth())
        return nextBusinessDayIfNeeded(target.atDay(day))
    }

    /**
     * Voluntary payment period for a tax assessment (LGT art. 62.2): notified between the 1st and
     * 15th, pay by the 20th of the next month; between the 16th and the end, by the 5th of the
     * second month after.
     */
    fun voluntaryPaymentDeadline(notified: LocalDate): LocalDate {
        val target = if (notified.dayOfMonth <= 15) {
            YearMonth.from(notified).plusMonths(1).atDay(20)
        } else {
            YearMonth.from(notified).plusMonths(2).atDay(5)
        }
        return nextBusinessDayIfNeeded(target)
    }

    /**
     * Payment after a "providencia de apremio" (LGT art. 62.5): notified 1st–15th, pay by the 20th
     * of the same month; 16th–end, by the 5th of the next month.
     */
    fun enforcementPaymentDeadline(notified: LocalDate): LocalDate {
        val target = if (notified.dayOfMonth <= 15) {
            YearMonth.from(notified).atDay(20)
        } else {
            YearMonth.from(notified).plusMonths(1).atDay(5)
        }
        return nextBusinessDayIfNeeded(target)
    }
}
