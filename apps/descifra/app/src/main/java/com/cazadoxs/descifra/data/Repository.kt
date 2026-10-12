package com.cazadoxs.descifra.data

import android.content.Context
import com.cazadoxs.descifra.core.ai.CloudProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class Repository(context: Context, private val vault: Vault) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    private val _letters = MutableStateFlow(load())
    val letters: StateFlow<List<Letter>> = _letters.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun load(): List<Letter> {
        val bytes = vault.readFile(FILE) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(Letter.serializer()), String(bytes)) }.getOrDefault(emptyList())
    }

    private fun persist(list: List<Letter>) {
        vault.writeFile(FILE, json.encodeToString(ListSerializer(Letter.serializer()), list).toByteArray())
    }

    fun get(id: String): Letter? = _letters.value.firstOrNull { it.id == id }

    fun upsert(letter: Letter) = synchronized(lock) {
        val list = _letters.value.toMutableList()
        val i = list.indexOfFirst { it.id == letter.id }
        if (i >= 0) list[i] = letter else list.add(0, letter)
        persist(list)
        _letters.value = list
    }

    fun update(id: String, change: (Letter) -> Letter) = synchronized(lock) {
        val current = get(id) ?: return
        upsert(change(current))
    }

    fun delete(id: String) = synchronized(lock) {
        val list = _letters.value.filterNot { it.id == id }
        persist(list)
        _letters.value = list
    }

    fun deleteAll() = synchronized(lock) {
        vault.deleteFile(FILE)
        _letters.value = emptyList()
    }

    /** Wipes letters, API keys and settings, and destroys the encryption key. */
    fun eraseEverything() = synchronized(lock) {
        vault.deleteFile(FILE)
        prefs.edit().clear().apply()
        app.getSharedPreferences(SECRETS, Context.MODE_PRIVATE).edit().clear().apply()
        vault.wipeKey()
        _letters.value = emptyList()
        _settings.value = Settings()
    }

    fun apiKey(provider: CloudProvider): String? = vault.getString(SECRETS, "key_${provider.name}")

    fun setApiKey(provider: CloudProvider, key: String?) {
        vault.putString(SECRETS, "key_${provider.name}", key?.trim()?.ifBlank { null })
        refreshSettings()
    }

    fun updateSettings(change: (Settings) -> Settings) {
        val s = change(_settings.value)
        prefs.edit()
            .putString("mode", s.mode.name)
            .putString("provider", s.provider.name)
            .putString("model", s.model)
            .putBoolean("redact", s.redact)
            .putBoolean("lock", s.lockWithBiometrics)
            .putStringSet("remind", s.reminderDaysBefore.map { it.toString() }.toSet())
            .putInt("remindHour", s.reminderHour)
            .putBoolean("onboarded", s.onboarded)
            .putBoolean("cloudConsent", s.cloudConsent)
            .putBoolean("shots", s.allowScreenshots)
            .apply()
        _settings.update { s.copy(hasKey = apiKey(s.provider) != null) }
    }

    private fun refreshSettings() = _settings.update { it.copy(hasKey = apiKey(it.provider) != null) }

    private fun readSettings(): Settings {
        val provider = runCatching { CloudProvider.valueOf(prefs.getString("provider", "")!!) }.getOrDefault(CloudProvider.ANTHROPIC)
        return Settings(
            mode = runCatching { AnalysisMode.valueOf(prefs.getString("mode", "")!!) }.getOrDefault(AnalysisMode.AUTO),
            provider = provider,
            model = prefs.getString("model", "") ?: "",
            redact = prefs.getBoolean("redact", true),
            lockWithBiometrics = prefs.getBoolean("lock", false),
            reminderDaysBefore = prefs.getStringSet("remind", null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(7, 1),
            reminderHour = prefs.getInt("remindHour", 9),
            onboarded = prefs.getBoolean("onboarded", false),
            cloudConsent = prefs.getBoolean("cloudConsent", false),
            allowScreenshots = prefs.getBoolean("shots", false),
            hasKey = apiKey(provider) != null,
        )
    }

    private companion object {
        const val FILE = "letters.bin"
        const val SECRETS = "secrets"
    }
}
