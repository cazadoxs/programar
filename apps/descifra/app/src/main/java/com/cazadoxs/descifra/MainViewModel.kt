package com.cazadoxs.descifra

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cazadoxs.descifra.billing.ProState
import com.cazadoxs.descifra.core.ai.CloudException
import com.cazadoxs.descifra.core.rules.Reanalyze
import com.cazadoxs.descifra.data.ChatTurn
import com.cazadoxs.descifra.data.Letter
import com.cazadoxs.descifra.data.Settings
import com.cazadoxs.descifra.ml.NanoState
import com.cazadoxs.descifra.ml.Ocr
import com.cazadoxs.descifra.ml.PdfPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data object Onboarding : Screen
    data object Review : Screen
    data class Detail(val id: String) : Screen
    data object Settings : Screen
    data object Paywall : Screen
    data object Privacy : Screen
}

data class Draft(val text: String = "", val receivedOn: LocalDate = LocalDate.now(), val pages: Int = 1)

data class UiState(
    val stack: List<Screen> = listOf(Screen.Home),
    val draft: Draft = Draft(),
    val busy: String? = null,
    val message: String? = null,
    val nano: NanoState? = null,
    val nanoDownloading: Boolean = false,
    val locked: Boolean = false,
) {
    val screen: Screen get() = stack.last()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val di = app as DescifraApp
    val repo = di.repository
    val engine = di.engine
    val billing = di.billing

    val letters = repo.letters
    val settings: StateFlow<Settings> = repo.settings
    val pro: StateFlow<ProState> = billing.state

    private val _ui = MutableStateFlow(
        UiState(
            stack = listOf(if (repo.settings.value.onboarded) Screen.Home else Screen.Onboarding),
            locked = repo.settings.value.lockWithBiometrics,
        ),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { billing.refresh() }
        refreshNano()
    }

    fun refreshNano() {
        viewModelScope.launch { _ui.update { it.copy(nano = di.nano.state()) } }
    }

    fun downloadNano() = viewModelScope.launch {
        _ui.update { it.copy(nanoDownloading = true) }
        try {
            di.nano.download().collect { }
        } catch (e: Exception) {
            _ui.update { it.copy(message = "No se pudo descargar el modelo de IA. Prueba con wifi y batería.") }
        }
        _ui.update { it.copy(nanoDownloading = false, nano = null) }
        refreshNano()
    }

    // --- navigation ---------------------------------------------------------------------
    fun go(screen: Screen) = _ui.update { it.copy(stack = it.stack + screen) }

    fun back(): Boolean {
        val s = _ui.value
        if (s.busy != null) return true
        if (s.stack.size <= 1) return false
        _ui.update { it.copy(stack = it.stack.dropLast(1)) }
        return true
    }

    fun resetTo(screen: Screen) = _ui.update { it.copy(stack = listOf(Screen.Home) + if (screen == Screen.Home) emptyList() else listOf(screen)) }

    fun toast(text: String?) = _ui.update { it.copy(message = text) }

    fun unlock() = _ui.update { it.copy(locked = false) }
    fun lockNow() = _ui.update { it.copy(locked = repo.settings.value.lockWithBiometrics) }

    // --- getting text in ----------------------------------------------------------------
    fun onScanned(uris: List<Uri>) = viewModelScope.launch {
        _ui.update { it.copy(busy = "Leyendo la carta en tu móvil…") }
        try {
            val text = Ocr.read(getApplication(), uris)
            deleteTemp(uris)
            beginReview(text, uris.size)
        } catch (e: Exception) {
            _ui.update { it.copy(busy = null, message = "No se pudo leer la imagen. Prueba con más luz o pega el texto.") }
        }
    }

    fun onImage(uri: Uri) = onScanned(listOf(uri))

    fun onPdf(uri: Uri) = viewModelScope.launch {
        _ui.update { it.copy(busy = "Leyendo el PDF en tu móvil…") }
        try {
            beginReview(withContext(Dispatchers.Default) { PdfPages.read(getApplication(), uri) }, 1)
        } catch (e: Exception) {
            _ui.update { it.copy(busy = null, message = "No se pudo abrir el PDF.") }
        }
    }

    fun onText(text: String) = beginReview(text.take(MAX_TEXT), 1)

    private fun beginReview(text: String, pages: Int) {
        _ui.update { s ->
            val stack = if (s.screen == Screen.Review) s.stack else s.stack + Screen.Review
            val msg = if (text.isBlank()) "No hemos podido leer texto. Puedes escribirlo o pegarlo a mano." else null
            s.copy(draft = Draft(text.take(MAX_TEXT), LocalDate.now(), pages), stack = stack, busy = null, message = msg)
        }
    }

    private fun deleteTemp(uris: List<Uri>) {
        uris.forEach { u ->
            if (u.scheme == "file") runCatching { File(u.path!!).delete() }
            else runCatching { getApplication<Application>().contentResolver.delete(u, null, null) }
        }
    }

    fun editDraft(text: String? = null, received: LocalDate? = null) =
        _ui.update { it.copy(draft = it.draft.copy(text = text?.take(MAX_TEXT) ?: it.draft.text, receivedOn = received ?: it.draft.receivedOn)) }

    // --- analysing ----------------------------------------------------------------------
    fun analyzeDraft() = viewModelScope.launch {
        val d = _ui.value.draft
        if (d.text.isBlank()) {
            toast("Escribe o pega el texto de la carta.")
            return@launch
        }
        _ui.update { it.copy(busy = "Descifrando la carta…") }
        val result = engine.analyze(d.text, d.receivedOn, pro.value.isPro)
        val letter = Letter(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            receivedOn = d.receivedOn.toString(),
            text = d.text,
            analysis = result.analysis,
            pages = d.pages,
        )
        withContext(Dispatchers.IO) { repo.upsert(letter) }
        _ui.update {
            it.copy(
                busy = null,
                draft = Draft(),
                stack = listOf(Screen.Home, Screen.Detail(letter.id)),
                message = result.notes.firstOrNull(),
            )
        }
    }

    /** The user fixed the received date or a wrong OCR line: recompute the date-based deadlines. */
    fun changeReceived(id: String, received: LocalDate) = viewModelScope.launch(Dispatchers.Default) {
        repo.update(id) { l ->
            l.copy(receivedOn = received.toString(), analysis = Reanalyze.withReceived(l.analysis, l.text, received))
        }
    }

    fun toggleDone(id: String) = viewModelScope.launch(Dispatchers.IO) { repo.update(id) { it.copy(done = !it.done) } }
    fun setNote(id: String, note: String) = viewModelScope.launch(Dispatchers.IO) { repo.update(id) { it.copy(note = note.take(2000)) } }
    fun toggleReminders(id: String) = viewModelScope.launch(Dispatchers.IO) { repo.update(id) { it.copy(remindersOn = !it.remindersOn) } }

    fun delete(id: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.delete(id)
        _ui.update { it.copy(stack = listOf(Screen.Home)) }
    }

    fun ask(id: String, question: String) = viewModelScope.launch {
        val letter = repo.get(id) ?: return@launch
        _ui.update { it.copy(busy = "Pensando la respuesta…") }
        try {
            val answer = engine.ask(letter, question)
            withContext(Dispatchers.IO) { repo.update(id) { it.copy(chat = it.chat + ChatTurn(question, answer)) } }
            _ui.update { it.copy(busy = null) }
        } catch (e: CloudException) {
            _ui.update { it.copy(busy = null, message = e.message) }
        } catch (e: Exception) {
            _ui.update { it.copy(busy = null, message = "No se pudo obtener respuesta ahora mismo.") }
        }
    }

    fun makeDraft(id: String, request: String) = viewModelScope.launch {
        val letter = repo.get(id) ?: return@launch
        _ui.update { it.copy(busy = "Redactando el borrador…") }
        try {
            val text = engine.draft(letter, request)
            withContext(Dispatchers.IO) { repo.update(id) { it.copy(draft = text) } }
            _ui.update { it.copy(busy = null) }
        } catch (e: CloudException) {
            _ui.update { it.copy(busy = null, message = e.message) }
        } catch (e: Exception) {
            _ui.update { it.copy(busy = null, message = "No se pudo redactar el borrador ahora mismo.") }
        }
    }

    // --- settings -----------------------------------------------------------------------
    fun update(change: (Settings) -> Settings) = repo.updateSettings(change)

    fun finishOnboarding() {
        repo.updateSettings { it.copy(onboarded = true) }
        _ui.update { it.copy(stack = listOf(Screen.Home)) }
    }

    fun eraseEverything() = viewModelScope.launch(Dispatchers.IO) {
        repo.eraseEverything()
        _ui.update { UiState(stack = listOf(Screen.Onboarding), message = "Todo borrado de este móvil.") }
    }

    companion object {
        const val MAX_TEXT = 30_000
    }
}
