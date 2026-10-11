package com.cazadoxs.filtro400.data

import android.content.Context
import android.util.AtomicFile
import com.cazadoxs.filtro400.BuildConfig
import com.cazadoxs.filtro400.core.CommunityIndex
import com.cazadoxs.filtro400.core.SpamCategory
import com.cazadoxs.filtro400.core.SpamReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface NetResult {
    data object Ok : NetResult
    data object NotConfigured : NetResult
    data object NotPremium : NetResult
    data class Failed(val message: String) : NetResult
}

/**
 * Lista anónima de la comunidad.
 *
 * - Descargar la lista (Premium): baja un fichero con los números denunciados por
 *   varias personas. Se consulta en local, así que tus llamadas no salen del móvil.
 * - Denunciar (gratis para todos): envía SOLO el número que te llamó y la categoría,
 *   junto a un identificador aleatorio de la instalación. Nunca tu número ni tu agenda.
 */
class CommunityRepository(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val file = AtomicFile(File(context.filesDir, "community.json"))
    private val json = Json { ignoreUnknownKeys = true }

    private val _index = MutableStateFlow(CommunityIndex.EMPTY)
    val index: StateFlow<CommunityIndex> = _index

    val isConfigured: Boolean get() = BuildConfig.COMMUNITY_API.startsWith("https://")

    /** Carga la última lista guardada. Rápido: se llama al arrancar la app o el servicio. */
    suspend fun loadCached() = withContext(Dispatchers.IO) {
        if (_index.value !== CommunityIndex.EMPTY) return@withContext
        val bytes = runCatching { file.readFully() }.getOrNull() ?: return@withContext
        runCatching { CommunityIndex.parse(bytes.decodeToString()) }.onSuccess { _index.value = it }
    }

    fun clearCache() {
        file.delete()
        _index.value = CommunityIndex.EMPTY
    }

    suspend fun sync(): NetResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext NetResult.NotConfigured
        val token = settings.purchaseToken() ?: return@withContext NetResult.NotPremium
        val conn = open("/v1/list", "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("X-Package", context.packageName)
        try {
            when (val code = conn.responseCode) {
                200 -> {
                    val body = conn.inputStream.readLimited(MAX_LIST_BYTES)
                    val parsed = CommunityIndex.parse(body.decodeToString())
                    val out = file.startWrite()
                    try {
                        out.write(body)
                        file.finishWrite(out)
                    } catch (e: Exception) {
                        file.failWrite(out)
                        throw e
                    }
                    _index.value = parsed
                    settings.setLastSync(System.currentTimeMillis())
                    NetResult.Ok
                }
                401, 402, 403 -> NetResult.NotPremium
                else -> NetResult.Failed("El servidor respondió $code")
            }
        } catch (e: Exception) {
            NetResult.Failed(e.message ?: "Sin conexión")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun report(number: String, category: SpamCategory): NetResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext NetResult.NotConfigured
        val report = SpamReport.create(number, category, settings.installId())
            ?: return@withContext NetResult.Failed("Ese número no se puede denunciar")
        val conn = open("/v1/report", "POST")
        try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(json.encodeToString(SpamReport.serializer(), report).encodeToByteArray()) }
            when (val code = conn.responseCode) {
                200, 201, 202, 204 -> NetResult.Ok
                429 -> NetResult.Failed("Has enviado muchas denuncias hoy. Prueba mañana.")
                else -> NetResult.Failed("El servidor respondió $code")
            }
        } catch (e: Exception) {
            NetResult.Failed(e.message ?: "Sin conexión")
        } finally {
            conn.disconnect()
        }
    }

    private fun open(path: String, method: String): HttpURLConnection {
        val url = URL(BuildConfig.COMMUNITY_API.trimEnd('/') + path)
        require(url.protocol == "https") { "Solo HTTPS" }
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Filtro400/${BuildConfig.VERSION_NAME}")
        }
    }

    private fun InputStream.readLimited(max: Int): ByteArray = use { input ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw IllegalStateException("Lista demasiado grande")
        }
        out.toByteArray()
    }

    companion object {
        private const val MAX_LIST_BYTES = 8 * 1024 * 1024
    }
}
