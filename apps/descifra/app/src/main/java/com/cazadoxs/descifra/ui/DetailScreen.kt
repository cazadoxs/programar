package com.cazadoxs.descifra.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cazadoxs.descifra.core.model.AnalysisSource
import com.cazadoxs.descifra.core.model.Deadline
import com.cazadoxs.descifra.core.model.Reference
import com.cazadoxs.descifra.core.model.ReferenceKind
import com.cazadoxs.descifra.data.Letter
import java.time.LocalDate

class DetailActions(
    val onBack: () -> Unit,
    val onToggleDone: () -> Unit,
    val onDelete: () -> Unit,
    val onNote: (String) -> Unit,
    val onCalendar: (Deadline) -> Unit,
    val onCopy: (String) -> Unit,
    val onShare: () -> Unit,
    val onExportPdf: () -> Unit,
    val onAsk: (String) -> Unit,
    val onDraft: (String) -> Unit,
    val onPaywall: () -> Unit,
    val onToggleReminders: () -> Unit,
    val onChangeReceived: (LocalDate) -> Unit,
    val onCopyDraft: () -> Unit,
    val onReport: () -> Unit,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(letter: Letter, isPro: Boolean, busy: Boolean, a: DetailActions) {
    val p = MaterialTheme.palette
    var focus by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    val quotes = remember(letter) { letter.analysis.deadlines.map { it.quote } + letter.analysis.amounts.map { it.quote } }
    val show: (String?) -> Unit = { q -> focus = q; showOriginal = true }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { Summary(letter, isPro, busy, a, show, showHeaderOriginal = false) }
                Column(Modifier.weight(1f).fillMaxHeight().statusBarsPadding().background(p.sheet).border(1.dp, p.line)) {
                    OriginalHeader()
                    OriginalText(letter.text, quotes, focus, Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                Summary(letter, isPro, busy, a, show, showHeaderOriginal = true, bottomSpace = 76.dp)
                // Peek panel: the original is always one tap away, never hidden behind a menu.
                val height = if (showOriginal) maxHeight * 0.62f else 64.dp
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(height)
                        .animateContentSize()
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .background(p.sheet)
                        .border(1.dp, p.line, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                ) {
                    OriginalHeader(
                        Modifier.clickable { showOriginal = !showOriginal; if (!showOriginal) focus = null }
                            .semantics { contentDescription = if (showOriginal) "Ocultar carta original" else "Ver carta original" },
                    ) { Icon(if (showOriginal) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null, tint = p.ink) }
                    if (showOriginal) OriginalText(letter.text, quotes, focus, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Summary(
    letter: Letter,
    isPro: Boolean,
    busy: Boolean,
    a: DetailActions,
    showQuote: (String?) -> Unit,
    showHeaderOriginal: Boolean,
    bottomSpace: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val p = MaterialTheme.palette
    val an = letter.analysis
    val urgency = liveUrgency(letter)
    val color = urgencyColor(urgency)
    var confirmDelete by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val checked = remember(letter.id) { mutableStateListOf<Int>() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = a.onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = p.ink) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = a.onToggleReminders) {
                Icon(
                    if (letter.remindersOn) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                    if (letter.remindersOn) "Desactivar avisos" else "Activar avisos",
                    tint = p.ink,
                )
            }
            IconButton(onClick = a.onShare) { Icon(Icons.Outlined.Share, "Compartir resumen", tint = p.ink) }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Borrar carta", tint = p.ink) }
        }

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Headline
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Text(an.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
                    if (!letter.done) Stamp(urgencyLabel(urgency), color, Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(sourceLabel(an.source, an.provider), p.blue)
                    if (letter.done) Chip("Resuelta", p.green, filled = true)
                }
            }

            Paper(Modifier.fillMaxWidth()) {
                Eyebrow("En pocas palabras")
                Spacer(Modifier.height(6.dp))
                Text(an.summary, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Resumen automático: puede equivocarse. Compruébalo con el texto de la carta original, sobre todo fechas e importes.",
                    color = p.inkSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (an.warnings.isNotEmpty()) {
                Paper(Modifier.fillMaxWidth(), accent = p.stamp) {
                    Eyebrow("Ojo con esto", color = p.stamp)
                    an.warnings.forEach { Text("• $it", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium) }
                }
            }

            if (an.whatTheyAsk.isNotEmpty()) {
                Section("Qué te piden") { an.whatTheyAsk.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) } }
            }

            Section("Plazos") {
                if (an.deadlines.isEmpty()) {
                    Text("No hemos encontrado ninguna fecha límite. Si la carta menciona un plazo, búscalo en el original.", color = p.inkSoft, style = MaterialTheme.typography.bodyMedium)
                }
                an.deadlines.forEachIndexed { i, d -> DeadlineCard(letter, d, a.onCalendar, showQuote); if (i < an.deadlines.lastIndex) Spacer(Modifier.height(8.dp)) }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recibida el ${formatShort(LocalDate.parse(letter.receivedOn))}", color = p.inkSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { pickingDate = true }) { Text("Cambiar", color = p.blue) }
                }
            }

            if (an.amounts.isNotEmpty()) {
                Section("Importes") {
                    an.amounts.forEach { am ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(am.label, style = MaterialTheme.typography.bodyMedium, color = p.inkSoft)
                                if (am.quote != null) Text("Ver en la carta", color = p.blue, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { showQuote(am.quote) })
                            }
                            Text(am.formatted, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }

            if (an.steps.isNotEmpty()) {
                Section("Qué hacer, paso a paso") {
                    an.steps.forEachIndexed { i, s ->
                        val done = i in checked
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { if (done) checked.remove(i) else checked.add(i) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(if (done) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, tint = if (done) p.green else p.ink, modifier = Modifier.padding(top = 2.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(s, style = MaterialTheme.typography.bodyLarge, color = if (done) p.inkSoft else p.ink)
                        }
                    }
                }
            }

            if (an.references.isNotEmpty()) {
                Section("Referencias (toca para copiar)") {
                    an.references.forEach { r -> ReferenceRow(r, a.onCopy) }
                }
            }

            ProTools(letter, isPro, busy, a)

            Section("Tus notas") {
                var note by remember(letter.id) { mutableStateOf(letter.note) }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; a.onNote(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Con quién hablaste, qué número de cita te dieron…") },
                    minLines = 2,
                )
            }

            BigButton(
                if (letter.done) "Volver a marcar como pendiente" else "Marcar como resuelta",
                a.onToggleDone,
                Modifier.fillMaxWidth(),
                filled = !letter.done,
                color = if (letter.done) p.ink else p.green,
                onColor = p.onStamp,
            )
            TextButton(onClick = a.onReport) { Text("¿Algo no cuadra en este resumen? Avísanos", color = p.blue) }
            Text(
                "Descifra no es un servicio legal ni fiscal. Ante un embargo, un juicio o una sanción importante, consulta con un profesional.",
                color = p.inkSoft,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(bottomSpace).navigationBarsPadding())
        }
    }

    if (pickingDate) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(letter.receivedOn).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        a.onChangeReceived(if (d.isAfter(LocalDate.now())) LocalDate.now() else d)
                    }
                    pickingDate = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancelar") } },
        ) { androidx.compose.material3.DatePicker(state = state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar esta carta?") },
            text = { Text("Se borra de este móvil, con su texto y tus notas. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; a.onDelete() }) { Text("Borrar", color = p.stamp) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

private fun sourceLabel(source: AnalysisSource, provider: String?): String = when (source) {
    AnalysisSource.RULES -> "Leída por reglas, sin IA"
    AnalysisSource.ON_DEVICE_AI -> "IA en tu móvil"
    AnalysisSource.CLOUD_AI -> "IA: ${provider ?: "tu proveedor"}"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow(title)
        content()
    }
}

@Composable
private fun DeadlineCard(letter: Letter, d: Deadline, onCalendar: (Deadline) -> Unit, showQuote: (String?) -> Unit) {
    val p = MaterialTheme.palette
    val date = LocalDate.parse(d.date)
    val days = daysLeft(date)
    val past = days < 0
    val color = when {
        letter.done -> p.inkSoft
        past -> p.inkSoft
        days <= 3 -> p.stamp
        days <= 10 -> p.amber
        else -> p.green
    }
    Paper(Modifier.fillMaxWidth(), accent = color, padding = PaddingValues(start = 18.dp, top = 14.dp, end = 22.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.label, style = MaterialTheme.typography.titleMedium)
                Text(formatDay(date), style = MaterialTheme.typography.bodyLarge)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    daysText(days).replaceFirstChar { it.uppercase() },
                    color = color,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { contentDescription = "Plazo ${daysText(days)}" },
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.estimated) Chip("Fecha estimada", p.amber)
            if (!d.verified) Chip("No está en la carta", p.stamp)
            if (past) Chip("Ya pasó", p.inkSoft)
        }
        d.basis?.let { Text(it, color = p.inkSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        if (d.estimated) Text("Calculada sin festivos locales ni autonómicos. Confírmala en la carta.", color = p.inkSoft, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (d.quote != null) Text("Ver en la carta", color = p.blue, style = MaterialTheme.typography.labelLarge, modifier = Modifier.heightIn(min = 40.dp).clickable { showQuote(d.quote) }.padding(vertical = 8.dp))
            if (!past) Row(Modifier.heightIn(min = 40.dp).clickable { onCalendar(d) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EventAvailable, null, tint = p.blue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Añadir al calendario", color = p.blue, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ReferenceRow(r: Reference, onCopy: (String) -> Unit) {
    val p = MaterialTheme.palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onCopy(r.value) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(refName(r.kind), color = p.inkSoft, style = MaterialTheme.typography.bodySmall)
            Text(r.value, style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.5.sp))
        }
        Icon(Icons.Outlined.ContentCopy, "Copiar ${refName(r.kind)}", tint = p.inkSoft, modifier = Modifier.size(20.dp))
    }
}

private fun refName(k: ReferenceKind): String = when (k) {
    ReferenceKind.NIF -> "NIF"
    ReferenceKind.NIE -> "NIE"
    ReferenceKind.CIF -> "CIF"
    ReferenceKind.IBAN -> "IBAN"
    ReferenceKind.EXPEDIENTE -> "Nº de expediente"
    ReferenceKind.CSV -> "Código seguro de verificación"
    ReferenceKind.JUSTIFICANTE -> "Justificante"
    ReferenceKind.MATRICULA -> "Matrícula"
    ReferenceKind.REFERENCIA -> "Referencia"
}

@Composable
private fun ProTools(letter: Letter, isPro: Boolean, busy: Boolean, a: DetailActions) {
    val p = MaterialTheme.palette
    Section(if (isPro) "Herramientas Pro" else "Descifra Pro") {
        if (!isPro) {
            Paper(Modifier.fillMaxWidth(), onClick = a.onPaywall) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, tint = p.stamp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Pregunta lo que no entiendas, redacta la respuesta y exporta en PDF", style = MaterialTheme.typography.bodyMedium)
                        Text("Un solo pago, sin suscripción", color = p.stamp, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            return@Section
        }
        var question by remember(letter.id) { mutableStateOf("") }
        letter.chat.forEach { t ->
            Paper(Modifier.fillMaxWidth()) {
                Text(t.question, style = MaterialTheme.typography.titleSmall, color = p.blue)
                Spacer(Modifier.height(4.dp))
                Text(t.answer, style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pregunta sobre esta carta") },
            placeholder = { Text("¿Puedo pagarlo en plazos?") },
            minLines = 2,
        )
        BigButton("Preguntar", { a.onAsk(question.trim()); question = "" }, Modifier.fillMaxWidth(), filled = false, enabled = question.isNotBlank() && !busy)

        var request by remember(letter.id) { mutableStateOf("") }
        OutlinedTextField(
            value = request,
            onValueChange = { request = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Quiero redactar…") },
            placeholder = { Text("unas alegaciones / pedir aplazamiento") },
            minLines = 2,
        )
        BigButton("Redactar borrador", { a.onDraft(request.trim()) }, Modifier.fillMaxWidth(), filled = false, enabled = request.isNotBlank() && !busy)
        if (letter.draft.isNotBlank()) {
            Paper(Modifier.fillMaxWidth()) {
                Eyebrow("Borrador (revísalo antes de usarlo)")
                Spacer(Modifier.height(6.dp))
                Text(letter.draft, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = a.onCopyDraft) { Text("Copiar", color = p.blue) }
                }
            }
        }
        BigButton("Exportar resumen en PDF", a.onExportPdf, Modifier.fillMaxWidth(), filled = false, icon = { Icon(Icons.Outlined.PictureAsPdf, null, tint = p.ink) })
    }
}
