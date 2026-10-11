package app.facil.launcher.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Schedule {
    private fun activeOn(r: Reminder, d: LocalDate) = (r.days shr (d.dayOfWeek.value - 1)) and 1 == 1

    /** Próximo disparo estrictamente posterior a [now]. */
    fun next(r: Reminder, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val t = LocalTime.of(r.hour, r.minute)
        val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
        for (i in 0..7) {
            val d = today.plusDays(i.toLong())
            if (!activeOn(r, d)) continue
            val ms = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
            if (ms > now) return ms
        }
        return null
    }

    /** Último disparo anterior o igual a [now]. */
    fun lastOccurrence(r: Reminder, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val t = LocalTime.of(r.hour, r.minute)
        val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
        for (i in 0..7) {
            val d = today.minusDays(i.toLong())
            if (!activeOn(r, d)) continue
            val ms = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
            if (ms <= now) return ms
        }
        return null
    }

    fun daysText(days: Int): String {
        if (days and 127 == 127) return "Todos los días"
        if (days == 0b0011111) return "De lunes a viernes"
        if (days == 0b1100000) return "Fines de semana"
        val names = listOf("L", "M", "X", "J", "V", "S", "D")
        return names.filterIndexed { i, _ -> (days shr i) and 1 == 1 }.joinToString(" ")
    }
}
