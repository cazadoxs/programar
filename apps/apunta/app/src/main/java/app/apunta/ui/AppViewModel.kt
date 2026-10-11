package app.apunta.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.apunta.app
import app.apunta.audio.Importer
import app.apunta.billing.QuotaRules
import app.apunta.data.Recording
import app.apunta.data.SearchHit
import app.apunta.data.SearchQuery
import app.apunta.data.Segment
import app.apunta.data.Status
import app.apunta.export.ExportFormat
import app.apunta.export.Line
import app.apunta.export.TranscriptExporter
import app.apunta.settings.AiProvider
import app.apunta.settings.AppSettings
import app.apunta.settings.SecretStore
import app.apunta.summary.AiSummarizer
import app.apunta.summary.LocalSummarizer
import app.apunta.transcribe.TranscriptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SearchUi(val query: String = "", val hits: List<SearchHit> = emptyList(), val titles: List<Recording> = emptyList(), val searching: Boolean = false)

sealed interface UiMessage {
    data class Res(val id: Int) : UiMessage
    data class Shared(val uri: Uri, val mime: String) : UiMessage
}

@OptIn(FlowPreview::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val a = application.app
    val settings: StateFlow<AppSettings> = a.settings.state
    val isPro = a.billing.isPro
    val price = a.billing.price
    val quota = a.quota.state
    val models = a.models.states
    val recordings = a.db.recordings().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val current = TranscriptionService.current
    val progress = TranscriptionService.progress

    val message = MutableStateFlow<UiMessage?>(null)
    val importing = MutableStateFlow(false)
    val summarizing = MutableStateFlow(false)
    val aiKeySet = MutableStateFlow(a.secrets.has(SecretStore.AI_API_KEY))

    val searchQuery = MutableStateFlow("")
    val search: StateFlow<SearchUi> = searchQuery.debounce(200)
        .transformLatest { q ->
            val match = SearchQuery.toFtsMatch(q)
            if (match == null) emit(SearchUi(q))
            else {
                emit(SearchUi(q, searching = true))
                val hits = a.db.segments().search(match)
                val titles = a.db.recordings().searchTitles(SearchQuery.escapeLike(q.trim()))
                emit(SearchUi(q, hits, titles))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUi())

    init {
        a.billing.start()
        // Jobs that were running when the app died continue where they stopped.
        viewModelScope.launch {
            if (a.db.recordings().withStatus(listOf(Status.QUEUED, Status.RUNNING)).isNotEmpty()) TranscriptionService.resumeAll(a)
        }
    }

    fun refreshQuota() { a.quota.refresh() }
    fun update(block: (AppSettings) -> AppSettings) = a.settings.update(block)

    fun recording(id: Long) = a.db.recordings().observe(id)
    fun segments(id: Long) = a.db.segments().observe(id)

    fun transcribe(id: Long) {
        viewModelScope.launch {
            val r = a.db.recordings().get(id) ?: return@launch
            if (a.models.fileFor(r.modelId ?: settings.value.modelId) == null) {
                message.value = UiMessage.Res(app.apunta.R.string.msg_no_model); return@launch
            }
            if (!isPro.value && !QuotaRules.canTranscribe(false, a.quota.refresh().usedMs)) {
                a.db.recordings().setStatus(id, Status.NEEDS_PRO, r.progress); return@launch
            }
            TranscriptionService.enqueue(a, id)
        }
    }

    fun retranscribe(id: Long, language: String, modelId: String?, translate: Boolean) {
        viewModelScope.launch {
            val r = a.db.recordings().get(id) ?: return@launch
            a.db.segments().deleteFor(id)
            a.db.recordings().update(r.copy(language = language, modelId = modelId, translate = translate, processedMs = 0, progress = 0f, summary = null, status = Status.NEW, error = null))
            File(a.cacheDir, "pcm_$id.raw").delete()
            transcribe(id)
        }
    }

    fun cancel(id: Long) = TranscriptionService.cancel(a, id)

    fun importUris(uris: List<Uri>) {
        viewModelScope.launch {
            importing.value = true
            var lastOk: Long? = null
            for (u in uris.take(20)) {
                when (val r = Importer.import(a, u)) {
                    is Importer.Result.Ok -> { lastOk = r.id; if (settings.value.autoTranscribe) transcribe(r.id) }
                    Importer.Result.TooBig -> message.value = UiMessage.Res(app.apunta.R.string.msg_too_big)
                    Importer.Result.Failed -> message.value = UiMessage.Res(app.apunta.R.string.msg_import_failed)
                }
            }
            importing.value = false
        }
    }

    fun rename(id: Long, title: String) = viewModelScope.launch { a.db.recordings().rename(id, title.trim().take(100).ifBlank { "Apunta" }) }
    fun star(id: Long, v: Boolean) = viewModelScope.launch { a.db.recordings().setStarred(id, v) }
    fun setNotes(id: Long, n: String) = viewModelScope.launch { a.db.recordings().setNotes(id, n.take(20_000)) }
    fun editSegment(id: Long, text: String) = viewModelScope.launch { a.db.segments().editText(id, text.trim()) }

    fun delete(id: Long) {
        viewModelScope.launch {
            val r = a.db.recordings().get(id)
            a.db.recordings().delete(id)
            withContext(Dispatchers.IO) {
                r?.let { File(File(a.filesDir, "recordings"), it.fileName).delete() }
                File(a.cacheDir, "pcm_$id.raw").delete()
            }
        }
    }

    fun audioFile(r: Recording) = File(File(a.filesDir, "recordings"), r.fileName)

    fun export(r: Recording, format: ExportFormat) {
        viewModelScope.launch {
            val segs = a.db.segments().list(r.id)
            val text = TranscriptExporter.export(format, r.title, segs.map { Line(it.startMs, it.endMs, it.text) }, r.summary)
            val file = withContext(Dispatchers.IO) {
                val dir = File(a.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                File(dir, TranscriptExporter.fileName(r.title, format)).also { it.writeText(text) }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(a, "${a.packageName}.files", file)
            message.value = UiMessage.Shared(uri, format.mime)
        }
    }

    fun summarizeLocal(r: Recording) {
        viewModelScope.launch {
            val segs = a.db.segments().list(r.id)
            val s = withContext(Dispatchers.Default) {
                LocalSummarizer.summarize(LocalSummarizer.splitSentences(segs.joinToString(" ") { it.text.trim() }), 6)
            }
            a.db.recordings().setSummary(r.id, s.joinToString("\n") { "• $it" }.ifBlank { null }, "local")
        }
    }

    fun summarizeAi(r: Recording) {
        val s = settings.value
        val key = a.secrets.get(SecretStore.AI_API_KEY)
        if (s.aiProvider == AiProvider.NONE || key == null || !s.aiConsentGiven) {
            message.value = UiMessage.Res(app.apunta.R.string.msg_ai_not_configured); return
        }
        viewModelScope.launch {
            summarizing.value = true
            try {
                val segs = a.db.segments().list(r.id)
                val text = segs.joinToString("\n") { it.text.trim() }
                val out = withContext(Dispatchers.IO) { AiSummarizer.summarize(s.aiProvider, s.aiModel, key, text, r.detectedLanguage ?: r.language) }
                a.db.recordings().setSummary(r.id, out, s.aiProvider.key)
            } catch (e: AiSummarizer.AiException) {
                message.value = UiMessage.Res(
                    when (e.kind) {
                        AiSummarizer.AiException.Kind.AUTH -> app.apunta.R.string.msg_ai_auth
                        AiSummarizer.AiException.Kind.RATE -> app.apunta.R.string.msg_ai_rate
                        AiSummarizer.AiException.Kind.NETWORK -> app.apunta.R.string.msg_ai_network
                        else -> app.apunta.R.string.msg_ai_failed
                    }
                )
            } finally { summarizing.value = false }
        }
    }

    fun saveAiKey(key: String) { a.secrets.put(SecretStore.AI_API_KEY, key); aiKeySet.value = a.secrets.has(SecretStore.AI_API_KEY) }

    fun downloadModel(id: String) { app.apunta.models.ModelCatalog.byId(id)?.let { a.models.download(it) } }
    fun cancelModel(id: String) { app.apunta.models.ModelCatalog.byId(id)?.let { a.models.cancel(it) } }
    fun deleteModel(id: String) = a.models.delete(id)
    fun importModel(uri: Uri) {
        viewModelScope.launch {
            val ok = a.models.importCustom { a.contentResolver.openInputStream(uri) }
            if (ok) update { it.copy(modelId = app.apunta.models.ModelCatalog.CUSTOM_ID) }
            else message.value = UiMessage.Res(app.apunta.R.string.msg_bad_model)
        }
    }

    fun totalRamGb(): Double {
        val am = a.getSystemService(android.app.ActivityManager::class.java)
        val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        return mi.totalMem / 1_073_741_824.0
    }

    fun wipeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            a.db.clearAllTables()
            File(a.filesDir, "recordings").deleteRecursively()
            a.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            a.secrets.put(SecretStore.AI_API_KEY, null); aiKeySet.value = false
        }
    }
}
