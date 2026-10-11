package com.cazadoxs.escanerlimpio.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SISTEMA, CLARO, OSCURO }
enum class Quality(val label: String, val maxSide: Int, val jpegQuality: Int) {
    COMPACTA("Compacta", 1800, 72),
    EQUILIBRADA("Equilibrada", 2400, 82),
    MAXIMA("Máxima", 3300, 92),
}

data class SettingsState(
    val appLock: Boolean = false,
    val secureScreen: Boolean = false,
    val theme: ThemeMode = ThemeMode.SISTEMA,
    val quality: Quality = Quality.EQUILIBRADA,
    val autoName: Boolean = true,
    val onboardingDone: Boolean = false,
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun read() = SettingsState(
        appLock = prefs.getBoolean(K_LOCK, false),
        secureScreen = prefs.getBoolean(K_SECURE, false),
        theme = runCatching { ThemeMode.valueOf(prefs.getString(K_THEME, null)!!) }.getOrDefault(ThemeMode.SISTEMA),
        quality = runCatching { Quality.valueOf(prefs.getString(K_QUALITY, null)!!) }.getOrDefault(Quality.EQUILIBRADA),
        autoName = prefs.getBoolean(K_AUTONAME, true),
        onboardingDone = prefs.getBoolean(K_ONBOARDING, false),
    )

    fun update(transform: (SettingsState) -> SettingsState) {
        val next = transform(_state.value)
        prefs.edit()
            .putBoolean(K_LOCK, next.appLock)
            .putBoolean(K_SECURE, next.secureScreen)
            .putString(K_THEME, next.theme.name)
            .putString(K_QUALITY, next.quality.name)
            .putBoolean(K_AUTONAME, next.autoName)
            .putBoolean(K_ONBOARDING, next.onboardingDone)
            .apply()
        _state.value = next
    }

    private companion object {
        const val K_LOCK = "bloqueo"
        const val K_SECURE = "pantalla_segura"
        const val K_THEME = "tema"
        const val K_QUALITY = "calidad"
        const val K_AUTONAME = "nombre_auto"
        const val K_ONBOARDING = "bienvenida_vista"
    }
}
