package com.cazadoxs.escaneaycome.data

import com.cazadoxs.escaneaycome.BuildConfig
import com.cazadoxs.escaneaycome.core.Barcode
import com.cazadoxs.escaneaycome.core.OffLookup
import com.cazadoxs.escaneaycome.core.OffParser
import com.cazadoxs.escaneaycome.core.OffProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Network errors surfaced to the UI as a short Spanish message. */
class OffException(message: String) : IOException(message)

/**
 * Minimal Open Food Facts client (read-only, no account). Data is ODbL licensed;
 * the app credits it on every product screen and in Ajustes.
 */
class OffClient(private val base: String = "https://world.openfoodfacts.org") {

    private val userAgent = "EscaneaYCome/${BuildConfig.VERSION_NAME} (Android; https://github.com/cazadoxs/programar)"

    suspend fun product(rawCode: String): OffLookup {
        // Only a validated digit string can reach the URL path.
        val code = Barcode.normalize(rawCode) ?: return OffLookup.NotFound
        val url = "$base/api/v2/product/$code.json?lc=es&fields=${OffParser.FIELDS}"
        val body = get(url, allowNotFound = true) ?: return OffLookup.NotFound
        return OffParser.parseProductResponse(body, code)
    }

    suspend fun search(query: String): List<OffProduct> {
        val q = query.trim().take(80)
        if (q.length < 2) return emptyList()
        val url = "$base/cgi/search.pl?search_simple=1&action=process&json=1&page_size=30" +
            "&lc=es&cc=es&sort_by=unique_scans_n" +
            "&fields=${OffParser.FIELDS}" +
            "&search_terms=" + URLEncoder.encode(q, "UTF-8")
        val body = get(url, allowNotFound = false) ?: return emptyList()
        return OffParser.parseSearchResponse(body)
    }

    private suspend fun get(url: String, allowNotFound: Boolean): String? = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            when {
                code == 404 && allowNotFound -> null
                code == 429 -> throw OffException("Open Food Facts va saturado. Prueba en un minuto.")
                code !in 200..299 -> throw OffException("Open Food Facts no responde ($code).")
                else -> conn.inputStream.use { readCapped(it) }
            }
        } catch (e: OffException) {
            throw e
        } catch (e: IOException) {
            throw OffException("Sin conexión. Revisa tus datos o el wifi.")
        } finally {
            conn.disconnect()
        }
    }

    private fun readCapped(input: java.io.InputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) throw OffException("Respuesta demasiado grande.")
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    companion object {
        private const val MAX_BYTES = 3 * 1024 * 1024
    }
}
