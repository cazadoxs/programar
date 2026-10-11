package app.apunta.billing

import java.time.YearMonth
import java.time.ZoneId

/**
 * Free plan: recording, import, search and export are unlimited; transcription
 * has a generous monthly allowance. Pro removes the allowance.
 */
object QuotaRules {
    const val FREE_MINUTES_PER_MONTH = 300   // 5 hours

    fun monthKey(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        YearMonth.from(java.time.Instant.ofEpochMilli(nowMs).atZone(zone)).toString()

    /** Milliseconds still available this month for a free user. */
    fun remainingMs(usedMs: Long): Long =
        (FREE_MINUTES_PER_MONTH * 60_000L - usedMs).coerceAtLeast(0)

    fun canTranscribe(isPro: Boolean, usedMs: Long): Boolean = isPro || remainingMs(usedMs) > 0
}

data class QuotaState(val month: String, val usedMs: Long) {
    val remainingMs: Long get() = QuotaRules.remainingMs(usedMs)
    val usedFraction: Float get() = (usedMs / (QuotaRules.FREE_MINUTES_PER_MONTH * 60_000f)).coerceIn(0f, 1f)
}
