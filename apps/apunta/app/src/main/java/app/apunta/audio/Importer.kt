package app.apunta.audio

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.apunta.app
import app.apunta.data.Recording
import app.apunta.data.Source
import app.apunta.data.Status
import java.io.File
import java.io.IOException

/**
 * Copies a shared file (WhatsApp voice note, video, m4a...) into private storage.
 * The copy is size-limited and the stored name is generated here, never taken from
 * the sender, so a malicious "../" name or an oversized stream cannot do harm.
 */
object Importer {
    const val MAX_BYTES = 2_000_000_000L

    sealed interface Result {
        data class Ok(val id: Long) : Result
        data object TooBig : Result
        data object Failed : Result
    }

    suspend fun import(context: Context, uri: Uri): Result {
        val resolver = context.contentResolver
        if (uri.scheme != ContentResolver.SCHEME_CONTENT && uri.scheme != ContentResolver.SCHEME_FILE) return Result.Failed
        // file:// is only accepted if it points inside our own storage; otherwise a sender could ask us to read private files.
        if (uri.scheme == ContentResolver.SCHEME_FILE) return Result.Failed
        val (display, size) = query(resolver, uri)
        if (size != null && size > MAX_BYTES) return Result.TooBig
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val ext = extensionFor(resolver.getType(uri), display)
        val name = "imp_${System.currentTimeMillis()}.$ext"
        val out = File(dir, name)
        try {
            val input = resolver.openInputStream(uri) ?: return Result.Failed
            var total = 0L
            input.use { inp ->
                out.outputStream().use { o ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val r = inp.read(buf)
                        if (r < 0) break
                        total += r
                        if (total > MAX_BYTES) { out.delete(); return Result.TooBig }
                        o.write(buf, 0, r)
                    }
                }
            }
        } catch (e: IOException) {
            out.delete(); return Result.Failed
        } catch (e: SecurityException) {
            out.delete(); return Result.Failed
        }
        val s = context.app.settings.current
        val title = (display ?: "Audio").substringBeforeLast('.').take(80).ifBlank { "Audio" }
        val id = context.app.db.recordings().insert(
            Recording(
                title = title, createdAt = System.currentTimeMillis(), fileName = name, source = Source.IMPORTED,
                language = s.language, status = Status.NEW,
            )
        )
        return Result.Ok(id)
    }

    private fun query(resolver: ContentResolver, uri: Uri): Pair<String?, Long?> =
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }
                    val sz = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
                    n to sz
                } else null
            } ?: (null to null)
        } catch (e: Exception) { null to null }

    private val SAFE_EXT = Regex("^[a-z0-9]{2,5}$")

    fun extensionFor(mime: String?, display: String?): String {
        val fromName = display?.substringAfterLast('.', "")?.lowercase()?.takeIf { SAFE_EXT.matches(it) }
        if (fromName != null) return fromName
        return when {
            mime == null -> "bin"
            mime.contains("ogg") || mime.contains("opus") -> "ogg"
            mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> "m4a"
            mime.contains("mpeg") -> "mp3"
            mime.contains("wav") -> "wav"
            mime.startsWith("video/") -> "mp4"
            else -> "bin"
        }
    }
}
