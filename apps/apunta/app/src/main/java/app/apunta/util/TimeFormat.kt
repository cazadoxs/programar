package app.apunta.util

import java.util.Locale

object TimeFormat {
    /** "4:05", "1:02:03". Negative values are clamped to zero. */
    fun clock(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0)) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** "00:01:02,345" (SubRip). */
    fun srt(ms: Long): String = full(ms, ',')

    /** "00:01:02.345" (WebVTT). */
    fun vtt(ms: Long): String = full(ms, '.')

    private fun full(ms: Long, sep: Char): String {
        val v = ms.coerceAtLeast(0)
        val h = v / 3_600_000
        val m = (v % 3_600_000) / 60_000
        val s = (v % 60_000) / 1000
        val milli = v % 1000
        return String.format(Locale.ROOT, "%02d:%02d:%02d%c%03d", h, m, s, sep, milli)
    }

    /** "2 h 5 min", "12 min", "45 s" for durations in lists. */
    fun human(ms: Long): String {
        val totalSec = ms.coerceAtLeast(0) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            m > 0 -> "$m min"
            else -> "${totalSec} s"
        }
    }
}
