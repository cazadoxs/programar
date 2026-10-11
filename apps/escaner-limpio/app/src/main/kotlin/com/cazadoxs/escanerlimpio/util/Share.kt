package com.cazadoxs.escanerlimpio.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.FileProvider
import com.cazadoxs.escanerlimpio.classify.TextTools
import com.cazadoxs.escanerlimpio.data.DocEntry
import com.cazadoxs.escanerlimpio.data.DocumentStore
import com.cazadoxs.escanerlimpio.security.LockGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Share {
    private fun authority(context: Context) = "${context.packageName}.archivos"

    /** Shares PDFs under their readable titles through a temporary, read-only copy. */
    suspend fun shareDocs(context: Context, store: DocumentStore, docs: List<DocEntry>) {
        if (docs.isEmpty()) return
        val uris = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "compartir").apply { deleteRecursively(); mkdirs() }
            val used = HashSet<String>()
            docs.map { d ->
                var name = TextTools.safeFileName(d.title)
                var n = 2
                while (!used.add(name)) name = "${TextTools.safeFileName(d.title)} (${n++})"
                val copy = File(dir, "$name.pdf")
                store.pdfFile(d.id).copyTo(copy, overwrite = true)
                FileProvider.getUriForFile(context, authority(context), copy)
            }
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.apply {
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(null, uris[0]).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        }
        LockGuard.expectExternalFlow()
        context.startActivity(Intent.createChooser(intent, "Compartir PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        LockGuard.expectExternalFlow()
        context.startActivity(Intent.createChooser(intent, "Compartir texto").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Copies text and marks it sensitive so Android hides it from the clipboard preview. */
    fun copyText(context: Context, text: String) {
        val clip = ClipData.newPlainText("Texto del documento", text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    /** Copies a library PDF to a place the user picked with the system file dialog. */
    suspend fun exportTo(context: Context, store: DocumentStore, doc: DocEntry, target: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target)?.use { out ->
            store.pdfFile(doc.id).inputStream().use { it.copyTo(out) }
        } ?: error("No output stream")
    }

    /**
     * Only content:// URIs from other apps are accepted. file:// URIs or our own
     * provider could be abused to make the app read its private files.
     */
    fun isSafeIncoming(context: Context, uri: Uri): Boolean =
        uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority != authority(context)
}
