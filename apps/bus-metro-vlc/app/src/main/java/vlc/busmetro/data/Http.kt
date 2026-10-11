package vlc.busmetro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import vlc.busmetro.BuildConfig
import vlc.transit.core.feeds.Feeds
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class BlockedHostException(host: String) : IOException("host not allowed: $host")
class TooLargeException : IOException("response too large")

/**
 * The only way the app reaches the network. Refuses anything that is not
 * HTTPS to a known open-data host (redirects included), and caps body sizes so
 * a misbehaving server cannot fill the phone.
 */
class Http {
    private val guard = Interceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps || url.host !in Feeds.ALLOWED_HOSTS) throw BlockedHostException(url.host)
        chain.proceed(chain.request())
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .addNetworkInterceptor(guard) // runs for every redirect hop too
        .addInterceptor(guard)
        .build()

    private val userAgent = "BusMetroVLC/${BuildConfig.VERSION_NAME} (Android)"

    private fun request(url: String, referer: String? = null) = Request.Builder().url(url)
        .header("User-Agent", userAgent)
        .apply { if (referer != null) header("Referer", referer) }
        .build()

    suspend fun bytes(url: String, maxBytes: Long = 4L shl 20, referer: String? = null): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(request(url, referer)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            if (body.contentLength() > maxBytes) throw TooLargeException()
            val src = body.byteStream()
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = src.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw TooLargeException()
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    suspend fun text(url: String, maxBytes: Long = 4L shl 20): String = String(bytes(url, maxBytes), Charsets.UTF_8)

    /** Streams [url] into [dest], reporting progress 0..1 when the size is known. */
    suspend fun download(url: String, dest: File, maxBytes: Long = 200L shl 20, progress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        client.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            val len = body.contentLength()
            if (len > maxBytes) throw TooLargeException()
            dest.outputStream().use { out ->
                val src = body.byteStream()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw TooLargeException()
                    out.write(buf, 0, n)
                    if (len > 0) progress(total.toFloat() / len)
                }
            }
        }
    }
}
