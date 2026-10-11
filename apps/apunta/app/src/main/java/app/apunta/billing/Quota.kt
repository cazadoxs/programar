package app.apunta.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
