package com.cazadoxs.descifra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cazadoxs.descifra.core.model.Deadline
import com.cazadoxs.descifra.core.model.DeadlineKind
import com.cazadoxs.descifra.core.model.Urgency
import com.cazadoxs.descifra.data.Letter
import java.time.LocalDate

fun nextDeadline(letter: Letter, today: LocalDate = LocalDate.now()): Deadline? =
    letter.analysis.deadlines
        .filter { it.kind != DeadlineKind.INFO && !LocalDate.parse(it.date).isBefore(today) }
        .minByOrNull { it.date }

fun liveUrgency(letter: Letter): Urgency {
    if (letter.done) return Urgency.NONE
    return com.cazadoxs.descifra.core.rules.LocalAnalyzer.urgencyOf(letter.analysis.deadlines, null, LocalDate.now())
        .let { if (it < letter.analysis.urgency && nextDeadline(letter) == null) letter.analysis.urgency else it }
}

@Composable
fun HomeScreen(
    letters: List<Letter>,
    onScan: () -> Unit,
    onPaste: () -> Unit,
    onImport: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val p = MaterialTheme.palette
    val pending = letters.filter { !it.done }.sortedWith(compareBy({ nextDeadline(it)?.date ?: "9999" }, { -it.createdAt }))
    val archived = letters.filter { it.done }.sortedByDescending { it.createdAt }
    val hero = pending.firstOrNull { nextDeadline(it) != null }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Descifra", style = MaterialTheme.typography.headlineLarge)
                        Text("Tus cartas oficiales, en cristiano.", color = p.inkSoft, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = onSettings, Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Ajustes", tint = p.ink)
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }

            if (letters.isEmpty()) {
                item { EmptyState() }
            } else if (hero != null) {
                item { Hero(hero, onOpen) }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Escanear una carta", onScan, Modifier.fillMaxWidth(), icon = { Icon(Icons.Outlined.CameraAlt, null, tint = p.paper) })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("Pegar texto", onPaste, Modifier.weight(1f), filled = false, icon = { Icon(Icons.Outlined.ContentPaste, null, tint = p.ink) })
                        BigButton("Foto o PDF", onImport, Modifier.weight(1f), filled = false, icon = { Icon(Icons.Outlined.FileUpload, null, tint = p.ink) })
                    }
                }
            }

            if (pending.isNotEmpty()) {
                item { Eyebrow("Pendientes (${pending.size})", Modifier.padding(top = 12.dp)) }
                items(pending, key = { it.id }) { LetterRow(it, onOpen) }
            }
            if (archived.isNotEmpty()) {
                item { Eyebrow("Resueltas (${archived.size})", Modifier.padding(top = 12.dp)) }
                items(archived, key = { it.id }) { LetterRow(it, onOpen) }
            }
            item {
                Text(
                    "Descifra explica cartas; no sustituye a un abogado ni a un asesor.",
                    color = p.inkSoft,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).navigationBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun Hero(letter: Letter, onOpen: (String) -> Unit) {
    val p = MaterialTheme.palette
    val d = nextDeadline(letter) ?: return
    val date = LocalDate.parse(d.date)
    val days = daysLeft(date)
    val urgency = liveUrgency(letter)
    val color = urgencyColor(urgency)
    Paper(Modifier.fillMaxWidth(), onClick = { onOpen(letter.id) }, accent = color, padding = PaddingValues(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Lo siguiente que vence", Modifier.weight(1f))
            Stamp(urgencyLabel(urgency), color)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (days <= 0) (if (days == 0L) "Hoy" else "Vencido") else "$days",
                style = MaterialTheme.typography.displayLarge,
                color = color,
                modifier = Modifier.semantics { contentDescription = "Plazo ${daysText(days)}" },
            )
            if (days > 0) Text(if (days == 1L) " día" else " días", style = MaterialTheme.typography.headlineSmall, color = p.inkSoft, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(d.label + " · " + formatDay(date), style = MaterialTheme.typography.titleMedium)
        Text(letter.analysis.title, color = p.inkSoft, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (d.estimated) Text("Fecha estimada: confírmala en la carta", color = p.amber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun LetterRow(letter: Letter, onOpen: (String) -> Unit) {
    val p = MaterialTheme.palette
    val d = nextDeadline(letter)
    val urgency = liveUrgency(letter)
    val color = urgencyColor(urgency)
    Paper(Modifier.fillMaxWidth(), onClick = { onOpen(letter.id) }, accent = if (letter.done) null else color, padding = PaddingValues(start = 18.dp, top = 14.dp, end = 22.dp, bottom = 14.dp)) {
        Text(letter.analysis.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(
            if (letter.done) "Resuelta" else d?.let { "${it.label}: ${daysText(daysLeft(LocalDate.parse(it.date)))} · ${formatShort(LocalDate.parse(it.date))}" } ?: "Sin plazo detectado",
            color = if (letter.done) p.green else if (d != null) color else p.inkSoft,
            style = MaterialTheme.typography.bodyMedium,
        )
        letter.analysis.amounts.firstOrNull()?.let {
            Text("${it.label}: ${it.formatted}", color = p.inkSoft, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyState() {
    val p = MaterialTheme.palette
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(width = 150.dp, height = 104.dp)) {
            val w = size.width; val h = size.height
            drawRect(p.sheet, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w, h))
            drawRect(p.ink, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w, h), style = Stroke(5f))
            val flap = Path().apply { moveTo(0f, 0f); lineTo(w / 2, h * 0.58f); lineTo(w, 0f) }
            drawPath(flap, p.ink, style = Stroke(5f))
            drawCircle(p.stamp, radius = 20f, center = Offset(w * 0.78f, h * 0.72f))
            drawCircle(p.onStamp, radius = 9f, center = Offset(w * 0.78f, h * 0.72f), style = Stroke(3f))
        }
        Spacer(Modifier.height(14.dp))
        Text("Aquí aparecerán tus cartas", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Haz una foto a la carta que no entiendes. Te diremos qué es, qué piden, cuánto y para cuándo.",
            color = p.inkSoft,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
    }
}
