package vlc.busmetro.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val onboarded: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val pro: Boolean = false,
    val notifyAlerts: Boolean = true,
    val showBikes: Boolean = true,
    val reminderLeadMin: Int = 3,
    val opens: Int = 0,
    val reviewAsked: Boolean = false,
    val lastSync: Long = 0,
)

class Prefs(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val theme = stringPreferencesKey("theme")
        val pro = booleanPreferencesKey("pro")
        val notifyAlerts = booleanPreferencesKey("notify_alerts")
        val showBikes = booleanPreferencesKey("show_bikes")
        val lead = intPreferencesKey("reminder_lead")
        val opens = intPreferencesKey("opens")
        val reviewAsked = booleanPreferencesKey("review_asked")
        val lastSync = longPreferencesKey("last_sync")
        fun widget(id: Int) = stringPreferencesKey("widget_$id")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            onboarded = p[K.onboarded] ?: false,
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            pro = p[K.pro] ?: false,
            notifyAlerts = p[K.notifyAlerts] ?: true,
            showBikes = p[K.showBikes] ?: true,
            reminderLeadMin = p[K.lead] ?: 3,
            opens = p[K.opens] ?: 0,
            reviewAsked = p[K.reviewAsked] ?: false,
            lastSync = p[K.lastSync] ?: 0,
        )
    }

    suspend fun current() = settings.first()

    suspend fun setOnboarded() = context.store.edit { it[K.onboarded] = true }
    suspend fun setTheme(t: ThemeMode) = context.store.edit { it[K.theme] = t.name }
    suspend fun setPro(v: Boolean) = context.store.edit { it[K.pro] = v }
    suspend fun setNotifyAlerts(v: Boolean) = context.store.edit { it[K.notifyAlerts] = v }
    suspend fun setShowBikes(v: Boolean) = context.store.edit { it[K.showBikes] = v }
    suspend fun setReminderLead(v: Int) = context.store.edit { it[K.lead] = v.coerceIn(1, 15) }
    suspend fun countOpen() = context.store.edit { it[K.opens] = (it[K.opens] ?: 0) + 1 }
    suspend fun setReviewAsked() = context.store.edit { it[K.reviewAsked] = true }
    suspend fun setLastSync(t: Long) = context.store.edit { it[K.lastSync] = t }

    suspend fun widgetStop(id: Int): String? = context.store.data.first()[K.widget(id)]
    suspend fun setWidgetStop(id: Int, key: String) = context.store.edit { it[K.widget(id)] = key }
    suspend fun clearWidget(id: Int) = context.store.edit { it.remove(K.widget(id)) }
}
