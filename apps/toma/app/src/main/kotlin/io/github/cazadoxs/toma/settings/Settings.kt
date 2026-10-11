package io.github.cazadoxs.toma.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User preferences. Small values only: medical data lives in the Room database. */
data class Prefs(
    val onboardingDone: Boolean = false,
    val activeProfileId: Long = ALL_PROFILES,
    val repeatMinutes: Int = 5,
    val snoozeMinutes: Int = 10,
    val graceMinutes: Int = 60,
    val caregiverEnabled: Boolean = false,
    val caregiverServer: String = DEFAULT_NTFY_SERVER,
    val caregiverTopic: String = "",
    val caregiverIncludeMeds: Boolean = false,
    val plusOwned: Boolean = false,
    val themeId: String = "botica",
    val largeText: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val autoBackupFolder: String = "",
    val lastAutoBackupAt: Long = 0,
    val lastSweepAt: Long = 0,
) {
    companion object {
        const val ALL_PROFILES = -1L
        const val DEFAULT_NTFY_SERVER = "https://ntfy.sh"
    }
}

class Settings(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("toma_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Prefs> = _state.asStateFlow()
    val current: Prefs get() = _state.value

    fun update(block: (Prefs) -> Prefs) {
        synchronized(this) {
            val next = block(read())
            write(next)
            _state.value = next
        }
    }

    private fun read() = Prefs(
        onboardingDone = sp.getBoolean("onboardingDone", false),
        activeProfileId = sp.getLong("activeProfileId", Prefs.ALL_PROFILES),
        repeatMinutes = sp.getInt("repeatMinutes", 5),
        snoozeMinutes = sp.getInt("snoozeMinutes", 10),
        graceMinutes = sp.getInt("graceMinutes", 60),
        caregiverEnabled = sp.getBoolean("caregiverEnabled", false),
        caregiverServer = sp.getString("caregiverServer", null) ?: Prefs.DEFAULT_NTFY_SERVER,
        caregiverTopic = sp.getString("caregiverTopic", null) ?: "",
        caregiverIncludeMeds = sp.getBoolean("caregiverIncludeMeds", false),
        plusOwned = sp.getBoolean("plusOwned", false),
        themeId = sp.getString("themeId", null) ?: "botica",
        largeText = sp.getBoolean("largeText", false),
        autoBackupEnabled = sp.getBoolean("autoBackupEnabled", false),
        autoBackupFolder = sp.getString("autoBackupFolder", null) ?: "",
        lastAutoBackupAt = sp.getLong("lastAutoBackupAt", 0),
        lastSweepAt = sp.getLong("lastSweepAt", 0),
    )

    private fun write(p: Prefs) {
        sp.edit()
            .putBoolean("onboardingDone", p.onboardingDone)
            .putLong("activeProfileId", p.activeProfileId)
            .putInt("repeatMinutes", p.repeatMinutes)
            .putInt("snoozeMinutes", p.snoozeMinutes)
            .putInt("graceMinutes", p.graceMinutes)
            .putBoolean("caregiverEnabled", p.caregiverEnabled)
            .putString("caregiverServer", p.caregiverServer)
            .putString("caregiverTopic", p.caregiverTopic)
            .putBoolean("caregiverIncludeMeds", p.caregiverIncludeMeds)
            .putBoolean("plusOwned", p.plusOwned)
            .putString("themeId", p.themeId)
            .putBoolean("largeText", p.largeText)
            .putBoolean("autoBackupEnabled", p.autoBackupEnabled)
            .putString("autoBackupFolder", p.autoBackupFolder)
            .putLong("lastAutoBackupAt", p.lastAutoBackupAt)
            .putLong("lastSweepAt", p.lastSweepAt)
            .apply()
    }
}
