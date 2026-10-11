package app.apunta.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Performance(val key: String) { SAVER("saver"), BALANCED("balanced"), FAST("fast") }

enum class AiProvider(val key: String, val label: String, val defaultModel: String) {
    NONE("none", "—", ""),
    ANTHROPIC("anthropic", "Claude (Anthropic)", "claude-haiku-5-5"),
    OPENAI("openai", "OpenAI", "gpt-4.1-mini"),
    GEMINI("gemini", "Google Gemini", "gemini-2.5-flash");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: NONE
    }
}

data class AppSettings(
    val onboarded: Boolean = false,
    val language: String = "auto",
    val modelId: String = "base",
    val autoTranscribe: Boolean = true,
    val performance: Performance = Performance.BALANCED,
    val theme: Int = 0,              // 0 system, 1 light, 2 dark
    val consentReminder: Boolean = true,
    val aiProvider: AiProvider = AiProvider.NONE,
    val aiModel: String = "",
    val aiConsentGiven: Boolean = false,
    val highQualityAudio: Boolean = false,
    val keepScreenOn: Boolean = false,
)

/** Small preferences store exposed as a StateFlow; no secrets here (see SecretStore). */
class Settings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    private fun read() = AppSettings(
        onboarded = prefs.getBoolean("onboarded", false),
        language = prefs.getString("language", "auto") ?: "auto",
        modelId = prefs.getString("modelId", "base") ?: "base",
        autoTranscribe = prefs.getBoolean("autoTranscribe", true),
        performance = Performance.entries.firstOrNull { it.key == prefs.getString("performance", null) } ?: Performance.BALANCED,
        theme = prefs.getInt("theme", 0),
        consentReminder = prefs.getBoolean("consentReminder", true),
        aiProvider = AiProvider.from(prefs.getString("aiProvider", null)),
        aiModel = prefs.getString("aiModel", "") ?: "",
        aiConsentGiven = prefs.getBoolean("aiConsentGiven", false),
        highQualityAudio = prefs.getBoolean("highQualityAudio", false),
        keepScreenOn = prefs.getBoolean("keepScreenOn", false),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_state.value)
        prefs.edit()
            .putBoolean("onboarded", next.onboarded)
            .putString("language", next.language)
            .putString("modelId", next.modelId)
            .putBoolean("autoTranscribe", next.autoTranscribe)
            .putString("performance", next.performance.key)
            .putInt("theme", next.theme)
            .putBoolean("consentReminder", next.consentReminder)
            .putString("aiProvider", next.aiProvider.key)
            .putString("aiModel", next.aiModel)
            .putBoolean("aiConsentGiven", next.aiConsentGiven)
            .putBoolean("highQualityAudio", next.highQualityAudio)
            .putBoolean("keepScreenOn", next.keepScreenOn)
            .apply()
        _state.value = next
    }

    companion object {
        /** Languages offered first; whisper supports ~100 more through "auto". */
        val LANGUAGES = listOf(
            "auto" to "Detectar automáticamente",
            "es" to "Español",
            "ca" to "Català / Valencià",
            "en" to "English",
            "gl" to "Galego",
            "eu" to "Euskara",
            "pt" to "Português",
            "fr" to "Français",
            "it" to "Italiano",
            "de" to "Deutsch",
            "nl" to "Nederlands",
            "ro" to "Română",
            "pl" to "Polski",
            "uk" to "Українська",
            "ru" to "Русский",
            "ar" to "العربية",
            "zh" to "中文",
            "ja" to "日本語",
            "ko" to "한국어",
        )

        fun languageName(code: String?): String =
            LANGUAGES.firstOrNull { it.first == code }?.second ?: (code ?: "")
    }
}
