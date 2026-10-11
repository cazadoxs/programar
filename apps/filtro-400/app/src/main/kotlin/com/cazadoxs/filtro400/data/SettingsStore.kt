package com.cazadoxs.filtro400.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cazadoxs.filtro400.core.FilterConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.security.SecureRandom

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "filtro400")

/** Estado de la app que no es configuración de filtrado. */
data class AppFlags(
    val onboarded: Boolean = false,
    val premium: Boolean = false,
    val robinsonDone: Boolean = false,
    val lastCommunitySync: Long = 0,
)

class SettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object K {
        val CONFIG = stringPreferencesKey("config")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val INSTALL_ID = stringPreferencesKey("install_id")
        val PREMIUM = booleanPreferencesKey("premium")
        val PURCHASE_TOKEN = stringPreferencesKey("purchase_token")
        val ROBINSON_DONE = booleanPreferencesKey("robinson_done")
        val LAST_SYNC = longPreferencesKey("last_community_sync")
    }

    val config: Flow<FilterConfig> = context.dataStore.data.map { decode(it[K.CONFIG]) }

    val flags: Flow<AppFlags> = context.dataStore.data.map {
        AppFlags(
            onboarded = it[K.ONBOARDED] ?: false,
            premium = it[K.PREMIUM] ?: false,
            robinsonDone = it[K.ROBINSON_DONE] ?: false,
            lastCommunitySync = it[K.LAST_SYNC] ?: 0,
        )
    }

    suspend fun currentConfig(): FilterConfig = config.first()

    suspend fun updateConfig(transform: (FilterConfig) -> FilterConfig) {
        context.dataStore.edit { prefs ->
            val updated = transform(decode(prefs[K.CONFIG]))
            prefs[K.CONFIG] = json.encodeToString(FilterConfig.serializer(), updated)
        }
    }

    suspend fun setOnboarded() = context.dataStore.edit { it[K.ONBOARDED] = true }
    suspend fun setRobinsonDone(done: Boolean) = context.dataStore.edit { it[K.ROBINSON_DONE] = done }
    suspend fun setLastSync(ms: Long) = context.dataStore.edit { it[K.LAST_SYNC] = ms }

    suspend fun setPremium(active: Boolean, purchaseToken: String?) = context.dataStore.edit {
        it[K.PREMIUM] = active
        if (purchaseToken != null) it[K.PURCHASE_TOKEN] = purchaseToken else it.remove(K.PURCHASE_TOKEN)
    }

    suspend fun purchaseToken(): String? = context.dataStore.data.first()[K.PURCHASE_TOKEN]

    /**
     * Identificador aleatorio de esta instalación, solo para que el servidor no cuente
     * dos veces la misma denuncia. No se deriva de nada del teléfono ni de la cuenta.
     */
    suspend fun installId(): String {
        context.dataStore.data.first()[K.INSTALL_ID]?.let { return it }
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val id = android.util.Base64.encodeToString(
            bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        var result = id
        context.dataStore.edit { prefs ->
            val existing = prefs[K.INSTALL_ID]
            if (existing != null) result = existing else prefs[K.INSTALL_ID] = id
        }
        return result
    }

    private fun decode(raw: String?): FilterConfig =
        if (raw == null) FilterConfig()
        else runCatching { json.decodeFromString(FilterConfig.serializer(), raw) }.getOrDefault(FilterConfig())
}
