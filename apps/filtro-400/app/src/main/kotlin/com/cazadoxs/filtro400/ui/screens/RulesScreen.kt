package com.cazadoxs.filtro400.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.filtro400.Labels
import com.cazadoxs.filtro400.core.CallAction
import com.cazadoxs.filtro400.core.NumberEntry
import com.cazadoxs.filtro400.core.PhoneNumbers
import com.cazadoxs.filtro400.core.QuietHours
import com.cazadoxs.filtro400.ui.AppViewModel
import com.cazadoxs.filtro400.ui.components.ActionSelector
import com.cazadoxs.filtro400.ui.components.Gap
import com.cazadoxs.filtro400.ui.components.HGap
import com.cazadoxs.filtro400.ui.components.Hairline
import com.cazadoxs.filtro400.ui.components.Panel
import com.cazadoxs.filtro400.ui.components.Pill
import com.cazadoxs.filtro400.ui.components.ScreenHeader
import com.cazadoxs.filtro400.ui.components.ScrollScreen
import com.cazadoxs.filtro400.ui.components.SectionLabel
import com.cazadoxs.filtro400.ui.components.StatusDot
import com.cazadoxs.filtro400.ui.components.actionColor
import com.cazadoxs.filtro400.ui.theme.NumberStyle

@Composable
fun RulesScreen(vm: AppViewModel, onOpenPremium: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val flags by vm.flags.collectAsStateWithLifecycle()
    val c = config ?: return
    val premium = flags?.premium == true

    var addPrefix by rememberSaveable { mutableStateOf(false) }
    var addNumber by rememberSaveable { mutableStateOf<String?>(null) } // "block" | "allow"

    ScrollScreen {
        ScreenHeader("Reglas", "Tú decides qué suena, qué se silencia y qué se corta. Tus contactos siempre suenan.")

        TesterPanel(vm)

        SectionLabel("Por tipo de número")
        Panel {
            KindRule("Comerciales · 400", "El rango reservado por ley a la venta telefónica.", c.commercial400, vm::set400)
            Hairline(Modifier.padding(vertical = 14.dp))
            KindRule("Tarificación adicional", "803, 806, 807 y 905. Suelen ser concursos, tarot o estafas.", c.premiumRate, vm::setPremiumRate)
            Hairline(Modifier.padding(vertical = 14.dp))
            KindRule("Coste compartido", "901 y 902. A veces es tu banco o tu seguro.", c.sharedCost, vm::setSharedCost)
            Hairline(Modifier.padding(vertical = 14.dp))
            KindRule("Números extranjeros", "Cualquier número que no empiece por +34.", c.international, vm::setInternational)
            Hairline(Modifier.padding(vertical = 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Denunciados por la comunidad", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!premium) Pill("PREMIUM", MaterialTheme.colorScheme.primary, Modifier.clickable(onClick = onOpenPremium))
            }
            Text(
                "Números con varias denuncias anónimas de otros usuarios.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(10.dp)
            ActionSelector(c.community, vm::setCommunity, enabled = premium)
        }

        SectionLabel("Tus prefijos")
        Panel {
            if (c.prefixRules.isEmpty()) {
                Text(
                    "Corta o silencia todo lo que empiece por un prefijo. Por ejemplo, 91 para Madrid o +44 para Reino Unido.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            c.prefixRules.sortedBy { it.prefix }.forEach { r ->
                EntryRow(
                    title = r.prefix + "…",
                    subtitle = r.label.ifBlank { Labels.action(r.action) },
                    action = r.action,
                    onRemove = { vm.removePrefixRule(r.id) },
                )
            }
            Gap(8.dp)
            OutlinedButton(onClick = { addPrefix = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null); HGap(6.dp); Text("Añadir prefijo")
            }
        }

        SectionLabel("Lista negra")
        NumberList(c.blocked, CallAction.BLOCK, "Números que se cortan siempre.", vm::unblock) { addNumber = "block" }

        SectionLabel("Siempre permitidos")
        NumberList(c.allowed, CallAction.ALLOW, "Números que siempre suenan, aunque una regla diga lo contrario.", vm::unallow) { addNumber = "allow" }

        SectionLabel("Horario de silencio")
        QuietHoursPanel(c.quietHours, vm::setQuietHours)

        Gap()
        Text(
            "Los números ocultos no se pueden filtrar: Android no se los pasa a ninguna app. Si quieres rechazarlos, busca \"Bloquear números desconocidos\" en los ajustes de la app Teléfono.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (addPrefix) PrefixDialog(onDismiss = { addPrefix = false }) { p, a, l -> if (vm.addPrefixRule(p, a, l)) addPrefix = false }
    addNumber?.let { kind ->
        NumberDialog(
            title = if (kind == "block") "Bloquear número" else "Permitir número",
            onDismiss = { addNumber = null },
        ) { n, l -> if (if (kind == "block") vm.block(n, l) else vm.allow(n, l)) addNumber = null }
    }
}

@Composable
private fun TesterPanel(vm: AppViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val decision = vm.test(input)
    Panel {
        Text("Probar un número", style = MaterialTheme.typography.titleMedium)
        Gap(8.dp)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(20) },
            placeholder = { Text("400 123 456") },
            singleLine = true,
            textStyle = NumberStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        if (decision != null) {
            Gap(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(Labels.action(decision.action).uppercase(), actionColor(decision.action))
                HGap(10.dp)
                Text(Labels.reason(decision.reason, decision.detail), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun KindRule(title: String, subtitle: String, value: CallAction, onChange: (CallAction) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Gap(10.dp)
    ActionSelector(value, onChange)
}

@Composable
private fun EntryRow(title: String, subtitle: String, action: CallAction, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(actionColor(action))
        HGap()
        Column(Modifier.weight(1f)) {
            Text(title, style = NumberStyle)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Quitar $title") }
    }
}

@Composable
private fun NumberList(
    entries: List<NumberEntry>,
    action: CallAction,
    empty: String,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Panel {
        if (entries.isEmpty()) {
            Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        entries.sortedByDescending { it.addedAt }.forEach { e ->
            EntryRow(PhoneNumbers.format(e.number), e.label, action) { onRemove(e.number) }
        }
        Gap(8.dp)
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null); HGap(6.dp); Text("Añadir número")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuietHoursPanel(q: QuietHours, onChange: (QuietHours) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // "start" | "end"
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Silenciar desconocidos", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Durante esta franja, las llamadas de números que no tienes guardados no suenan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = q.enabled, onCheckedChange = { onChange(q.copy(enabled = it)) })
        }
        Gap(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { editing = "start" }, modifier = Modifier.weight(1f)) {
                Text("Desde ${hhmm(q.startMinute)}", style = NumberStyle)
            }
            OutlinedButton(onClick = { editing = "end" }, modifier = Modifier.weight(1f)) {
                Text("Hasta ${hhmm(q.endMinute)}", style = NumberStyle)
            }
        }
        Gap(10.dp)
        val names = listOf("L", "M", "X", "J", "V", "S", "D")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEachIndexed { i, n ->
                val day = i + 1
                val selected = q.days.isEmpty() || day in q.days
                FilterChip(
                    selected = selected,
                    onClick = {
                        val current = if (q.days.isEmpty()) (1..7).toSet() else q.days
                        val next = if (day in current) current - day else current + day
                        onChange(q.copy(days = if (next.size == 7) emptySet() else next))
                    },
                    label = { Text(n) },
                )
            }
        }
        Gap(10.dp)
        ActionSelector(q.action, { onChange(q.copy(action = it)) }, enabled = q.enabled)
    }

    editing?.let { which ->
        TimeDialog(
            initialMinute = if (which == "start") q.startMinute else q.endMinute,
            onDismiss = { editing = null },
        ) { m ->
            onChange(if (which == "start") q.copy(startMinute = m) else q.copy(endMinute = m))
            editing = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = { Button(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrefixDialog(onDismiss: () -> Unit, onConfirm: (String, CallAction, String) -> Unit) {
    var prefix by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var action by rememberSaveable { mutableStateOf(CallAction.BLOCK) }
    val presets = listOf("902" to "Coste compartido", "+44" to "Reino Unido", "+1" to "EE. UU. y Canadá", "70" to "Numeración personal")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo prefijo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { (p, l) ->
                        FilterChip(selected = prefix == p, onClick = { prefix = p; label = l }, label = { Text(p) })
                    }
                }
                OutlinedTextField(
                    value = prefix, onValueChange = { prefix = it.take(14) },
                    label = { Text("Prefijo") }, placeholder = { Text("91, 902, +44…") },
                    singleLine = true, textStyle = NumberStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(40) },
                    label = { Text("Nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                ActionSelector(action, { action = it })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(prefix, action, label) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun NumberDialog(title: String, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = number, onValueChange = { number = it.take(20) },
                    label = { Text("Número") }, singleLine = true, textStyle = NumberStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(40) },
                    label = { Text("Nota (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(number, label) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun hhmm(m: Int) = String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60)
