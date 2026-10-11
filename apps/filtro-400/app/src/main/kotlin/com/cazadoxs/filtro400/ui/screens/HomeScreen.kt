package com.cazadoxs.filtro400.ui.screens

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
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.filtro400.Labels
import com.cazadoxs.filtro400.core.CallAction
import com.cazadoxs.filtro400.core.ExpectedCall
import com.cazadoxs.filtro400.core.PhoneNumbers
import com.cazadoxs.filtro400.ui.AppViewModel
import com.cazadoxs.filtro400.ui.MainActivity
import com.cazadoxs.filtro400.ui.components.ActionSelector
import com.cazadoxs.filtro400.ui.components.BigCount
import com.cazadoxs.filtro400.ui.components.Gap
import com.cazadoxs.filtro400.ui.components.HGap
import com.cazadoxs.filtro400.ui.components.Hairline
import com.cazadoxs.filtro400.ui.components.IconBadge
import com.cazadoxs.filtro400.ui.components.Panel
import com.cazadoxs.filtro400.ui.components.ScrollScreen
import com.cazadoxs.filtro400.ui.components.SectionLabel
import com.cazadoxs.filtro400.ui.components.ShieldHero
import com.cazadoxs.filtro400.ui.components.StatusDot
import com.cazadoxs.filtro400.ui.components.WeekBars
import com.cazadoxs.filtro400.ui.components.actionColor
import com.cazadoxs.filtro400.ui.theme.LocalStatus
import com.cazadoxs.filtro400.ui.theme.NumberStyle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun HomeScreen(
    vm: AppViewModel,
    host: MainActivity,
    onOpenGuide: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPremium: () -> Unit,
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val flags by vm.flags.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val daily by vm.daily.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val roleHeld by host.roleHeld
    val c = config ?: return
    val status = LocalStatus.current

    // Reloj para que "quedan 2 h" se actualice solo.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    var showExpected by rememberSaveable { mutableStateOf(false) }

    val protected = c.enabled && roleHeld
    ScrollScreen {
        Gap(12.dp)
        Text("FILTRO 400", style = MaterialTheme.typography.labelSmall, color = status.blocked)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        !roleHeld -> "Falta un paso"
                        c.enabled -> "Protegido"
                        else -> "En pausa"
                    },
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    when {
                        !roleHeld -> "Filtro 400 aún no puede filtrar tus llamadas."
                        c.enabled -> "Las llamadas comerciales no te molestarán."
                        else -> "Todas las llamadas sonarán con normalidad."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ShieldHero(active = protected, size = 112.dp)
        }
        Gap()

        if (!roleHeld) {
            Panel(accent = status.blocked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(status.blocked) { Icon(Icons.Rounded.WarningAmber, null, tint = status.blocked) }
                    HGap()
                    Text(
                        "Elige Filtro 400 como app de identificación de llamadas y spam para empezar a filtrar.",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Gap()
                Button(onClick = { host.requestScreeningRole() }, modifier = Modifier.fillMaxWidth()) { Text("Activar ahora") }
            }
            Gap()
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Filtro", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (c.enabled) "Encendido" else "Apagado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = c.enabled,
                    onCheckedChange = vm::setEnabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = status.allowed, checkedThumbColor = Color.White),
                )
            }
            Gap(14.dp)
            Hairline()
            Gap(14.dp)
            Text("Llamadas desde el 400", style = MaterialTheme.typography.titleMedium)
            Text(
                "El rango que la ley reserva a llamadas comerciales.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(10.dp)
            ActionSelector(selected = c.commercial400, onSelect = vm::set400, enabled = c.enabled)
        }

        SectionLabel("Esta semana")
        Panel {
            Row {
                BigCount(today.filtered, "hoy", status.blocked, Modifier.weight(1f))
                BigCount(week.filtered, "7 días", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                BigCount(week.silenced, "silenciadas", status.silenced, Modifier.weight(1f))
            }
            Gap(16.dp)
            WeekBars(daily, dayLabels())
            if (week.filtered > 0) {
                Gap(10.dp)
                Text(
                    "Te has ahorrado unos ${week.filtered * 2} minutos de interrupciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionLabel("Llamadas esperadas")
        Panel {
            val active = c.expected.filter { it.untilEpochMs > now }
            if (active.isEmpty()) {
                Text(
                    "¿Esperas al repartidor, al médico o al taller? Deja pasar números desconocidos durante un rato, aunque tus reglas los fueran a cortar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                active.forEach { e -> ExpectedRow(e, now) { vm.removeExpected(e.id) } }
            }
            Gap(10.dp)
            OutlinedButton(onClick = { showExpected = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null)
                HGap(6.dp)
                Text("Espero una llamada")
            }
        }

        if (flags?.premium != true && vm.communityConfigured) {
            Gap()
            Panel(onClick = onOpenPremium) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(status.allowed) { Icon(Icons.Rounded.Groups, null, tint = status.allowed) }
                    HGap()
                    Column(Modifier.weight(1f)) {
                        Text("Lista de la comunidad", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Silencia también los números que otros usuarios han denunciado, aunque no usen el 400.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (flags?.robinsonDone != true) {
            Gap()
            Panel(onClick = onOpenGuide) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(status.silenced) { Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = status.silenced) }
                    HGap()
                    Column(Modifier.weight(1f)) {
                        Text("Apúntate a la Lista Robinson", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Es gratis y corta la publicidad desde el origen. Te lo explicamos paso a paso.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val recent = history.filter { it.action != CallAction.ALLOW }.take(3)
        if (recent.isNotEmpty()) {
            SectionLabel("Últimas filtradas")
            Panel(onClick = onOpenHistory) {
                recent.forEachIndexed { i, r ->
                    if (i > 0) Hairline(Modifier.padding(vertical = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(actionColor(r.action))
                        HGap()
                        Column(Modifier.weight(1f)) {
                            Text(PhoneNumbers.format(r.number), style = NumberStyle)
                            Text(
                                Labels.reason(r.reason, r.detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(relativeTime(r.timestamp, now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Gap(10.dp)
                Text("Ver todo el historial", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showExpected) {
        ExpectedDialog(
            onDismiss = { showExpected = false },
            onConfirm = { label, number, duration ->
                if (vm.addExpected(label, number, duration)) showExpected = false
            },
        )
    }
}

@Composable
private fun ExpectedRow(e: ExpectedCall, now: Long, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(LocalStatus.current.allowed)
        HGap()
        Column(Modifier.weight(1f)) {
            Text(e.label, style = MaterialTheme.typography.titleSmall)
            Text(
                (e.number?.let { PhoneNumbers.format(it) } ?: "Cualquier número desconocido") + " · " + remaining(e.untilEpochMs - now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Quitar ${e.label}") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpectedDialog(onDismiss: () -> Unit, onConfirm: (String, String?, Long) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf(3) }
    val presets = listOf("Repartidor", "Centro de salud", "Taller", "Inmobiliaria", "Entrevista")
    val durations = listOf(1 to "1 h", 3 to "3 h", 0 to "Hoy", 72 to "3 días")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Espero una llamada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { p -> FilterChip(selected = label == p, onClick = { label = p }, label = { Text(p) }) }
                }
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(40) },
                    label = { Text("¿De quién?") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = number, onValueChange = { number = it.take(20) },
                    label = { Text("Número (opcional)") },
                    supportingText = { Text("Vacío = deja pasar cualquier número desconocido, salvo el 400") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Durante", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    durations.forEach { (h, l) -> FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text(l) }) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val ms = if (hours == 0) untilEndOfDay() else hours * 3_600_000L
                onConfirm(label, number.ifBlank { null }, ms)
            }) { Text("Dejar pasar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun untilEndOfDay(): Long {
    val zone = ZoneId.systemDefault()
    val end = LocalDate.now(zone).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
    return (end - System.currentTimeMillis()).coerceAtLeast(60_000)
}

private fun remaining(ms: Long): String {
    val min = (ms / 60_000).coerceAtLeast(1)
    return when {
        min < 60 -> "quedan $min min"
        min < 48 * 60 -> "quedan ${(min + 30) / 60} h"
        else -> "quedan ${(min + 720) / (24 * 60)} días"
    }
}

internal fun relativeTime(ts: Long, now: Long): String {
    val min = (now - ts) / 60_000
    return when {
        min < 1 -> "ahora"
        min < 60 -> "hace $min min"
        min < 24 * 60 -> "hace ${min / 60} h"
        else -> "hace ${min / (24 * 60)} d"
    }
}

/** Iniciales de los últimos 7 días, terminando en hoy. */
private fun dayLabels(): List<String> {
    val names = listOf("L", "M", "X", "J", "V", "S", "D")
    val today = LocalDate.now()
    return (6 downTo 0).map { names[today.minusDays(it.toLong()).dayOfWeek.value - 1] }
}
