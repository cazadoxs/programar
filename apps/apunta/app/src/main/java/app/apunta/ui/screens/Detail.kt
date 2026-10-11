package app.apunta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.apunta.R
import app.apunta.data.Recording
import app.apunta.data.Segment
import app.apunta.data.Status
import app.apunta.export.ExportFormat
import app.apunta.settings.Settings
import app.apunta.ui.AppViewModel
import app.apunta.ui.components.AudioPlayer
import app.apunta.ui.components.highlighted
import app.apunta.ui.components.rememberAudioPlayer
import app.apunta.ui.theme.Apunta
import app.apunta.ui.theme.TimestampStyle
import app.apunta.util.TimeFormat

@Composable
fun DetailScreen(vm: AppViewModel, id: Long, initialSeekMs: Long, initialQuery: String, onBack: () -> Unit, onPaywall: () -> Unit) {
    val rec by remember(id) { vm.recording(id) }.collectAsStateWithLifecycle(null)
    val segs by remember(id) { vm.segments(id) }.collectAsStateWithLifecycle(emptyList())
    val r = rec ?: run { Box(Modifier.fillMaxSize()); return }
    val player = rememberAudioPlayer(vm.audioFile(r))
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val summarizing by vm.summarizing.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf(initialQuery) }
    var searching by remember { mutableStateOf(initialQuery.isNotEmpty()) }
    var menu by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var redo by remember { mutableStateOf(false) }

    LaunchedEffect(initialSeekMs, player.durationMs) { if (initialSeekMs > 0 && player.durationMs > 0) player.seekTo(initialSeekMs) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Text(r.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).clickable { renaming = true }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
                Box {
                    IconButton(onClick = { exportMenu = true }) { Icon(Icons.Filled.Share, stringResource(R.string.export)) }
                    DropdownMenu(exportMenu, { exportMenu = false }) {
                        listOf(R.string.fmt_txt to ExportFormat.TXT, R.string.fmt_txt_ts to ExportFormat.TXT_TIMESTAMPS, R.string.fmt_srt to ExportFormat.SRT, R.string.fmt_vtt to ExportFormat.VTT, R.string.fmt_md to ExportFormat.MARKDOWN)
                            .forEach { (label, f) -> DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { exportMenu = false; vm.export(r, f) }) }
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Replay, stringResource(R.string.retranscribe)) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.retranscribe)) }, onClick = { menu = false; redo = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
        },
        bottomBar = { PlayerBar(player, r.bookmarkList()) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (searching) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.search_in_transcript)) },
                    trailingIcon = { IconButton({ query = ""; searching = false }) { Icon(Icons.Filled.Close, null) } },
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                )
            }
            if (Status.isBusy(r.status)) {
                Column(Modifier.padding(16.dp)) {
                    LinearProgressIndicator(progress = { if (r.id == current) progress else r.progress }, Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.transcribing), style = MaterialTheme.typography.bodySmall, color = Apunta.colors.inkSoft, modifier = Modifier.weight(1f))
                        TextButton({ vm.cancel(r.id) }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
            when (r.status) {
                Status.NEW, Status.CANCELLED, Status.FAILED -> if (segs.isEmpty()) {
                    CallToAction(
                        stringResource(if (r.status == Status.FAILED) R.string.failed_title else R.string.not_transcribed),
                        if (r.status == Status.FAILED) stringResource(R.string.failed_body) else null,
                        stringResource(R.string.transcribe_now),
                    ) { vm.transcribe(r.id) }
                }
                Status.NEEDS_PRO -> CallToAction(stringResource(R.string.limit_title), stringResource(R.string.limit_body), stringResource(R.string.see_pro), onPaywall)
            }
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf(R.string.tab_transcript, R.string.tab_summary, R.string.tab_notes).forEachIndexed { i, t ->
                    Tab(tab == i, { tab = i }, text = { Text(stringResource(t), style = MaterialTheme.typography.labelLarge) })
                }
            }
            when (tab) {
                0 -> TranscriptList(segs, player, query, editable = !Status.isBusy(r.status), onEdit = { s, t -> vm.editSegment(s.id, t) })
                1 -> SummaryTab(r, segs.isNotEmpty(), summarizing, isPro, vm)
                else -> NotesTab(r) { vm.setNotes(r.id, it) }
            }
        }
    }

    if (renaming) RenameDialog(r.title, { renaming = false }) { vm.rename(r.id, it); renaming = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_title)) }, text = { Text(stringResource(R.string.delete_body)) },
        confirmButton = { TextButton({ confirmDelete = false; vm.delete(r.id); onBack() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (redo) RetranscribeDialog(vm, r, isPro, { redo = false }, onPaywall)
}

@Composable
private fun CallToAction(title: String, body: String?, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = Apunta.colors.inkSoft)
        Button(onClick) { Text(button) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TranscriptList(segs: List<Segment>, player: AudioPlayer, query: String, editable: Boolean, onEdit: (Segment, String) -> Unit) {
    val state = rememberLazyListState()
    var editing by remember { mutableStateOf<Segment?>(null) }
    val active = segs.indexOfLast { it.startMs <= player.positionMs }.takeIf { player.playing }
    LaunchedEffect(active) { if (active != null && active >= 0) state.animateScrollToItem((active - 1).coerceAtLeast(0)) }
    LazyColumn(state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(segs, key = { _, s -> s.id }) { i, s ->
            val isActive = i == active
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .background(if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent)
                    .combinedClickable(onClick = { player.playFrom(s.startMs) }, onLongClick = { if (editable) editing = s })
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(TimeFormat.clock(s.startMs), style = TimestampStyle, color = Apunta.colors.margin, modifier = Modifier.width(48.dp).padding(top = 5.dp))
                Text(highlighted(s.text.trim(), query), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
        if (segs.isEmpty()) item { Text(stringResource(R.string.no_text_yet), style = MaterialTheme.typography.bodyMedium, color = Apunta.colors.inkSoft) }
    }
    editing?.let { s ->
        var text by remember(s.id) { mutableStateOf(s.text.trim()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(TimeFormat.clock(s.startMs), style = TimestampStyle) },
            text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton({ onEdit(s, text); editing = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton({ editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SummaryTab(r: Recording, hasText: Boolean, summarizing: Boolean, isPro: Boolean, vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var consent by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!r.summary.isNullOrBlank()) {
            Text(r.summary, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(if (r.summarySource == "local") R.string.sum_local else R.string.sum_ai), style = MaterialTheme.typography.labelMedium, color = Apunta.colors.inkSoft)
        } else Text(stringResource(R.string.sum_empty), style = MaterialTheme.typography.bodyMedium, color = Apunta.colors.inkSoft)
        if (hasText) {
            OutlinedButton({ vm.summarizeLocal(r) }, enabled = !summarizing) { Text(stringResource(R.string.sum_make_local)) }
            OutlinedButton({
                if (!settings.aiConsentGiven) consent = true else vm.summarizeAi(r)
            }, enabled = !summarizing) { Text(stringResource(if (summarizing) R.string.sum_working else R.string.sum_make_ai)) }
        }
    }
    if (consent) AlertDialog(
        onDismissRequest = { consent = false },
        title = { Text(stringResource(R.string.ai_consent_title)) },
        text = { Text(stringResource(R.string.ai_consent_body)) },
        confirmButton = { TextButton({ consent = false; vm.update { it.copy(aiConsentGiven = true) }; vm.summarizeAi(r) }) { Text(stringResource(R.string.ai_consent_ok)) } },
        dismissButton = { TextButton({ consent = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NotesTab(r: Recording, onChange: (String) -> Unit) {
    var text by remember(r.id) { mutableStateOf(r.notes) }
    OutlinedTextField(text, { text = it; onChange(it) }, modifier = Modifier.fillMaxSize().padding(20.dp), placeholder = { Text(stringResource(R.string.notes_hint)) })
}

@Composable
private fun PlayerBar(player: AudioPlayer, bookmarks: List<Long>) {
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Slider(
            value = if (player.durationMs > 0) player.positionMs.toFloat() / player.durationMs else 0f,
            onValueChange = { player.seekTo((it * player.durationMs).toLong()) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${TimeFormat.clock(player.positionMs)} / ${TimeFormat.clock(player.durationMs)}", style = TimestampStyle, color = Apunta.colors.inkSoft, modifier = Modifier.weight(1f))
            TextButton({ player.cycleSpeed() }) { Text("${player.speed}×", style = TimestampStyle) }
            IconButton({ player.toggle() }, Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) {
                Icon(if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(R.string.play), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        if (bookmarks.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                bookmarks.take(8).forEach { b -> app.apunta.ui.components.Chip("🔖 ${TimeFormat.clock(b)}", Modifier.clickable { player.playFrom(b) }) }
            }
        }
    }
}

@Composable
fun RenameDialog(title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var t by remember { mutableStateOf(title) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(t, { t = it.take(100) }, singleLine = true) },
        confirmButton = { TextButton({ onSave(t) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RetranscribeDialog(vm: AppViewModel, r: Recording, isPro: Boolean, onDismiss: () -> Unit, onPaywall: () -> Unit) {
    val models by vm.models.collectAsStateWithLifecycle()
    var lang by remember { mutableStateOf(r.language) }
    var translate by remember { mutableStateOf(r.translate) }
    var modelId by remember { mutableStateOf(r.modelId ?: vm.settings.value.modelId) }
    var langMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.retranscribe)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton({ langMenu = true }) { Text(Settings.languageName(lang)) }
                    DropdownMenu(langMenu, { langMenu = false }) {
                        Settings.LANGUAGES.forEach { (c, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { lang = c; langMenu = false }) }
                    }
                }
                app.apunta.models.ModelCatalog.all.filter { models[it.id] is app.apunta.models.ModelState.Ready }.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { modelId = m.id }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(modelId == m.id, { modelId = m.id })
                        Text(m.id)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(translate, { translate = it })
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.translate_to_english), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton({
                val needPro = app.apunta.models.ModelCatalog.byId(modelId)?.pro == true && !isPro
                if (needPro) { onDismiss(); onPaywall() } else { vm.retranscribe(r.id, lang, modelId, translate); onDismiss() }
            }) { Text(stringResource(R.string.transcribe_now)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
