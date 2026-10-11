package com.cazadoxs.filtro400.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.filtro400.Labels
import com.cazadoxs.filtro400.core.CallAction
import com.cazadoxs.filtro400.core.PhoneNumbers
import com.cazadoxs.filtro400.core.SpamCategory
import com.cazadoxs.filtro400.data.CallRecord
import com.cazadoxs.filtro400.ui.AppViewModel
import com.cazadoxs.filtro400.ui.components.Gap
import com.cazadoxs.filtro400.ui.components.HGap
import com.cazadoxs.filtro400.ui.components.Pill
import com.cazadoxs.filtro400.ui.components.ScreenHeader
import com.cazadoxs.filtro400.ui.components.SectionLabel
import com.cazadoxs.filtro400.ui.components.StatusDot
import com.cazadoxs.filtro400.ui.components.actionColor
import com.cazadoxs.filtro400.ui.theme.NumberStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class HistoryFilter(val label: String) { ALL("Todas"), BLOCKED("Cortadas"), SILENCED("Silenciadas"), RANG("Han sonado") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(vm: AppViewModel) {
    val history by vm.history.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var selected by remember { mutableStateOf<CallRecord?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val shown = history.filter {
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.BLOCKED -> it.action == CallAction.BLOCK
            HistoryFilter.SILENCED -> it.action == CallAction.SILENCE
            HistoryFilter.RANG -> it.action == CallAction.ALLOW
        }
    }
    val zone = ZoneId.systemDefault()
    val grouped = shown.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            ScreenHeader(
                "Historial",
                "Llamadas de números que no tienes guardados. Solo se guarda en este teléfono.",
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Borrar historial")
                        }
                    }
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HistoryFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Gap(48.dp)
                Text(
                    if (history.isEmpty()) "Aún no ha pasado ninguna llamada por el filtro. Cuando un número desconocido te llame, aparecerá aquí."
                    else "No hay llamadas con este filtro.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        grouped.forEach { (day, records) ->
            item(key = "h$day") { SectionLabel(dayTitle(day)) }
            items(records, key = { it.id }) { r -> HistoryRow(r, now) { selected = r } }
        }
        item { Gap(32.dp) }
    }

    selected?.let { r -> RecordSheet(r, vm) { selected = null } }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todo el historial?") },
            text = { Text("No afecta a tus reglas ni a tus listas.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun HistoryRow(r: CallRecord, now: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(actionColor(r.action), 12.dp)
        HGap(14.dp)
        Column(Modifier.weight(1f)) {
            Text(PhoneNumbers.format(r.number), style = NumberStyle)
            Text(
                Labels.reason(r.reason, r.detail) + if (r.reported) " · Denunciado" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Pill(Labels.actionPast(r.action), actionColor(r.action))
            Text(
                timeOf(r.timestamp).takeIf { now - r.timestamp > 3_600_000 } ?: relativeTime(r.timestamp, now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecordSheet(r: CallRecord, vm: AppViewModel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var reporting by remember { mutableStateOf(false) }
    val canAct = r.number.startsWith("+")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(PhoneNumbers.format(r.number), style = MaterialTheme.typography.headlineMedium.merge(NumberStyle))
            Text(
                "${Labels.actionPast(r.action)} · ${Labels.reason(r.reason, r.detail)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap()
            if (reporting) {
                Text("¿Qué tipo de llamada era?", style = MaterialTheme.typography.titleMedium)
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpamCategory.entries.forEach { cat ->
                        FilterChip(selected = false, onClick = { vm.report(r, cat); onDismiss() }, label = { Text(cat.label) })
                    }
                }
                Gap(8.dp)
                Text(
                    "Solo se envía este número y la categoría. Nunca tu número ni tus contactos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                if (canAct && !r.reported && vm.communityConfigured) {
                    SheetAction(Icons.Rounded.Flag, "Denunciar como spam", "Anónimo. Ayuda a proteger a los demás.", itemColors) { reporting = true }
                }
                if (canAct) {
                    SheetAction(Icons.Rounded.Block, "Bloquear siempre", null, itemColors) { vm.block(r.number); onDismiss() }
                    SheetAction(Icons.Rounded.CheckCircle, "Dejar sonar siempre", null, itemColors) { vm.allow(r.number); onDismiss() }
                }
                SheetAction(Icons.Rounded.ContentCopy, "Copiar número", null, itemColors) {
                    clipboard.setText(AnnotatedString(r.number)); onDismiss()
                }
                SheetAction(Icons.Rounded.DeleteOutline, "Borrar del historial", null, itemColors) { vm.deleteRecord(r.id); onDismiss() }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    colors: androidx.compose.material3.ListItemColors,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null) },
        colors = colors,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES"))

private fun timeOf(ts: Long) = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(timeFmt)

private fun dayTitle(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> day.format(dayFmt)
    }
}
