package app.apunta.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class Quota(context: Context) {
    private val prefs = context.getSharedPreferences("quota", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<QuotaState> = _state.asStateFlow()

    private fun read(): QuotaState {
        val month = QuotaRules.monthKey(System.currentTimeMillis())
        return QuotaState(month, prefs.getLong("used_$month", 0))
    }

    /** Re-reads the month (the allowance resets on the 1st). */
    fun refresh(): QuotaState = read().also { _state.value = it }

    @Synchronized
    fun addUsage(ms: Long) {
        if (ms <= 0) return
        val s = refresh()
        prefs.edit().putLong("used_${s.month}", s.usedMs + ms).apply()
        _state.value = s.copy(usedMs = s.usedMs + ms)
    }
}
