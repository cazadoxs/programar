package app.apunta.models

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val progress: Float, val downloadedBytes: Long) : ModelState
    data object Verifying : ModelState
    data class Ready(val file: File) : ModelState
    data class Failed(val reason: Reason) : ModelState

    enum class Reason { NETWORK, CHECKSUM, STORAGE, INVALID_FILE }
}

/**
 * Downloads, verifies and stores whisper models.
 *
 * Downloads go through Android's DownloadManager, so a 500 MB model survives the
 * app being closed, resumes after losing connection and shows its own
 * notification. The file lands in app-specific external storage and is only
 * moved into private internal storage after its SHA-1 matches the catalogue.
 */
class ModelManager(private val context: Context, private val scope: CoroutineScope) {

    private val dm = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("models", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "models").apply { mkdirs() }
    private val _states = MutableStateFlow(initialStates())
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()
    private var poller: Job? = null

    init {
        if (pendingDownloads().isNotEmpty()) startPolling()
    }

    private fun initialStates(): Map<String, ModelState> {
        val map = mutableMapOf<String, ModelState>()
        for (m in ModelCatalog.all) {
            val f = File(dir, m.fileName)
            map[m.id] = if (f.exists() && f.length() > 0) ModelState.Ready(f) else ModelState.Missing
        }
        val custom = File(dir, CUSTOM_FILE)
        map[ModelCatalog.CUSTOM_ID] = if (custom.exists()) ModelState.Ready(custom) else ModelState.Missing
        return map
    }

    fun fileFor(modelId: String): File? = (states.value[modelId] as? ModelState.Ready)?.file

    fun readyModels(): List<String> = states.value.filterValues { it is ModelState.Ready }.keys.toList()

    fun freeBytes(): Long = context.getExternalFilesDir(null)?.usableSpace ?: dir.usableSpace

    fun download(model: WhisperModel) {
        val current = states.value[model.id]
        if (current is ModelState.Downloading || current is ModelState.Ready) return
        if (freeBytes() < model.sizeBytes * 2 + 50_000_000) {
            set(model.id, ModelState.Failed(ModelState.Reason.STORAGE)); return
        }
        tmpFile(model).delete()
        val request = DownloadManager.Request(Uri.parse(model.url))
            .setTitle("Apunta · modelo ${model.id}")
            .setDescription("Modelo de transcripción (${model.sizeBytes / 1_000_000} MB)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, model.fileName + ".part")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = dm.enqueue(request)
        prefs.edit().putLong("dl_${model.id}", id).apply()
        set(model.id, ModelState.Downloading(0f, 0))
        startPolling()
    }

    fun cancel(model: WhisperModel) {
        val id = prefs.getLong("dl_${model.id}", -1)
        if (id >= 0) dm.remove(id)
        prefs.edit().remove("dl_${model.id}").apply()
        tmpFile(model).delete()
        set(model.id, ModelState.Missing)
    }

    fun delete(modelId: String) {
        val file = if (modelId == ModelCatalog.CUSTOM_ID) File(dir, CUSTOM_FILE)
        else ModelCatalog.byId(modelId)?.let { File(dir, it.fileName) }
        file?.delete()
        set(modelId, ModelState.Missing)
    }

    /** Imports a ggml model the user already has (e.g. copied from a computer). */
    suspend fun importCustom(open: () -> InputStream?): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(dir, "$CUSTOM_FILE.tmp")
        try {
            val input = open() ?: return@withContext false
            input.use { inp -> tmp.outputStream().use { inp.copyTo(it, 1 shl 16) } }
            if (!hasGgmlMagic(tmp)) {
                tmp.delete(); set(ModelCatalog.CUSTOM_ID, ModelState.Failed(ModelState.Reason.INVALID_FILE))
                return@withContext false
            }
            val target = File(dir, CUSTOM_FILE)
            target.delete()
            if (!tmp.renameTo(target)) { tmp.delete(); return@withContext false }
            set(ModelCatalog.CUSTOM_ID, ModelState.Ready(target))
            true
        } catch (e: Exception) {
            tmp.delete()
            set(ModelCatalog.CUSTOM_ID, ModelState.Failed(ModelState.Reason.STORAGE))
            false
        }
    }

    private fun pendingDownloads(): Map<WhisperModel, Long> =
        ModelCatalog.all.mapNotNull { m -> prefs.getLong("dl_${m.id}", -1).takeIf { it >= 0 }?.let { m to it } }.toMap()

    private fun startPolling() {
        if (poller?.isActive == true) return
        poller = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val pending = pendingDownloads()
                if (pending.isEmpty()) break
                for ((model, id) in pending) poll(model, id)
                delay(700)
            }
        }
    }

    private suspend fun poll(model: WhisperModel, id: Long) {
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return
        cursor.use { c ->
            if (!c.moveToFirst()) {
                // Removed from the system download list (e.g. user cancelled it there).
                prefs.edit().remove("dl_${model.id}").apply()
                set(model.id, ModelState.Missing)
                return
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                .takeIf { it > 0 } ?: model.sizeBytes
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    prefs.edit().remove("dl_${model.id}").apply()
                    finish(model)
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.edit().remove("dl_${model.id}").apply()
                    dm.remove(id)
                    set(model.id, ModelState.Failed(ModelState.Reason.NETWORK))
                }
                else -> set(model.id, ModelState.Downloading((done.toFloat() / total).coerceIn(0f, 1f), done))
            }
        }
    }

    private suspend fun finish(model: WhisperModel) = withContext(Dispatchers.IO) {
        set(model.id, ModelState.Verifying)
        val tmp = tmpFile(model)
        val ok = tmp.exists() && sha1(tmp).equals(model.sha1, ignoreCase = true)
        if (!ok) {
            tmp.delete()
            set(model.id, ModelState.Failed(ModelState.Reason.CHECKSUM))
            return@withContext
        }
        val target = File(dir, model.fileName)
        val partial = File(dir, model.fileName + ".tmp")
        try {
            tmp.inputStream().use { inp -> partial.outputStream().use { inp.copyTo(it, 1 shl 16) } }
            if (!partial.renameTo(target)) throw java.io.IOException("rename failed")
            set(model.id, ModelState.Ready(target))
        } catch (e: Exception) {
            partial.delete()
            set(model.id, ModelState.Failed(ModelState.Reason.STORAGE))
        } finally {
            tmp.delete()
        }
    }

    private fun tmpFile(model: WhisperModel) =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), model.fileName + ".part")

    private fun set(id: String, state: ModelState) = _states.update { it + (id to state) }

    companion object {
        private const val CUSTOM_FILE = "ggml-custom.bin"

        fun sha1(file: File): String {
            val md = MessageDigest.getInstance("SHA-1")
            file.inputStream().use { inp ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val r = inp.read(buf)
                    if (r < 0) break
                    md.update(buf, 0, r)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** whisper.cpp model files start with the uint32 0x67676d6c ("ggml") in little-endian. */
        fun hasGgmlMagic(file: File): Boolean {
            if (file.length() < 1_000_000) return false
            val head = ByteArray(4)
            file.inputStream().use { if (it.read(head) != 4) return false }
            return head.contentEquals(byteArrayOf(0x6c, 0x6d, 0x67, 0x67))
        }
    }
}
