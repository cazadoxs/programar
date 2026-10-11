package com.cazadoxs.escaneaycome.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escaneaycome.core.Activity
import com.cazadoxs.escaneaycome.core.GoalType
import com.cazadoxs.escaneaycome.core.gramsText
import com.cazadoxs.escaneaycome.core.parseAmount
import com.cazadoxs.escaneaycome.data.Accent
import com.cazadoxs.escaneaycome.ui.components.Card
import com.cazadoxs.escaneaycome.ui.components.NumberField
import com.cazadoxs.escaneaycome.ui.container
import kotlinx.coroutines.launch

private const val PRIVACY_URL = "https://github.com/cazadoxs/programar/blob/main/apps/escanea-y-come/publicar/politica-de-privacidad.md"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onPlus: () -> Unit, onEditFood: (Long) -> Unit, onCreateFood: () -> Unit) {
    val c = container()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kcalText by rememberSaveable(s.customKcal) { mutableStateOf(if (s.customKcal > 0) s.customKcal.toString() else "") }
    var wipe by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val t = s.targets

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineLarge)

        Card {
            Text("Tu objetivo", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (s.calmMode) "Oculto por el modo tranquilo." else "${t.kcal} kcal · ${t.proteinG} g proteína · ${t.carbsG} g hidratos · ${t.fatG} g grasas",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text("Meta", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalType.entries.forEach { g ->
                    FilterChip(
                        selected = s.goal == g, enabled = !(s.isMinor && g == GoalType.PERDER),
                        onClick = { c.settings.update { it.copy(goal = g, customKcal = 0) } }, label = { Text(g.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Actividad", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Activity.entries.forEach { a ->
                    FilterChip(selected = s.activity == a, onClick = { c.settings.update { it.copy(activity = a) } }, label = { Text(a.label) })
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(kcalText, { kcalText = it.filter(Char::isDigit) }, "Objetivo propio", Modifier.weight(1f), "kcal", decimals = false)
                FilledTonalButton(onClick = {
                    val v = parseAmount(kcalText)?.toInt()
                    // Hard bounds so a typo can't create a dangerous or meaningless target.
                    c.settings.update { it.copy(customKcal = if (v != null && v in 1000..6000) v else 0) }
                    if (v != null && v !in 1000..6000) message = "El objetivo debe estar entre 1000 y 6000 kcal."
                }) { Text("Fijar") }
            }
            Text("Vacío = lo calculamos con tu altura, peso y actividad (Mifflin-St Jeor). Es una estimación, no un consejo médico.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card {
            Text("Reparto de macros", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Pct("Proteína", s.proteinPct) { p -> c.settings.update { it.copy(proteinPct = p) } }
            Pct("Hidratos", s.carbsPct) { p -> c.settings.update { it.copy(carbsPct = p) } }
            Pct("Grasas", s.fatPct) { p -> c.settings.update { it.copy(fatPct = p) } }
            Text("Se ajusta solo para sumar 100 %.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modo tranquilo", style = MaterialTheme.typography.titleMedium)
                    Text("Oculta calorías, objetivos, peso y rachas. Solo registras qué comes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = s.calmMode, onCheckedChange = { v -> c.settings.update { it.copy(calmMode = v) } })
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Si contar te genera ansiedad o culpa, hazte una pausa. En España, la línea ANAD atiende dudas sobre conductas alimentarias: consulta a tu médico de cabecera.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card {
            Text("Aspecto", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Accent.entries.forEach { a ->
                    FilterChip(
                        selected = s.accent == a,
                        onClick = { if (a == Accent.HUERTA || s.plus) c.settings.update { it.copy(accent = a) } else onPlus() },
                        label = { Text(if (a == Accent.HUERTA || s.plus) a.label else "${a.label} · Plus") },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Vasos de agua al día: ${s.waterGoal}", style = MaterialTheme.typography.labelLarge)
            Slider(value = s.waterGoal.toFloat(), onValueChange = { v -> c.settings.update { it.copy(waterGoal = v.toInt()) } }, valueRange = 4f..12f, steps = 7)
        }

        Card {
            Text("Tus datos", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Todo vive en tu móvil. No hay cuenta ni servidor. Lo único que sale es el código de barras que escaneas, hacia Open Food Facts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCreateFood, Modifier.fillMaxWidth()) { Text("Crear alimento propio") }
            OutlinedButton(
                onClick = {
                    if (!s.plus) { onPlus(); return@OutlinedButton }
                    scope.launch {
                        runCatching {
                            val file = c.repo.exportCsv(context)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Exportar diario"))
                        }.onFailure { message = "No se pudo exportar." }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (s.plus) "Exportar diario (CSV)" else "Exportar diario (CSV) · Plus") }
            TextButton(onClick = { wipe = true }, Modifier.fillMaxWidth()) { Text("Borrar todos mis datos", color = MaterialTheme.colorScheme.error) }
        }

        Card(color = MaterialTheme.colorScheme.primaryContainer) {
            Text(if (s.plus) "Plus activo ♥" else "Escanea y come Plus", style = MaterialTheme.typography.titleMedium)
            Text("Pago único, sin suscripción.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onPlus) { Text(if (s.plus) "Ver" else "Ver qué incluye") }
        }

        Card {
            Text("Acerca de", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Datos de producto: Open Food Facts (openfoodfacts.org), base de datos abierta bajo licencia ODbL; imágenes bajo CC BY-SA. " +
                    "Nutri-Score y NOVA son clasificaciones de terceros. Escáner: Google ML Kit. " +
                    "Fuente tipográfica: Bricolage Grotesque (SIL OFL). Información orientativa, no médica.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) } }) { Text("Política de privacidad") }
            Text("Versión ${com.cazadoxs.escaneaycome.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (wipe) {
        AlertDialog(
            onDismissRequest = { wipe = false },
            title = { Text("¿Borrar todo?") },
            text = { Text("Se eliminan diario, alimentos, comidas guardadas, peso y ajustes. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { wipe = false; scope.launch { c.repo.wipeAll(); c.settings.clear() } }) { Text("Borrar todo", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { wipe = false }) { Text("Cancelar") } },
        )
    }
    message?.let {
        AlertDialog(onDismissRequest = { message = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("Vale") } })
    }
}

@Composable
private fun Pct(label: String, value: Int, onChange: (Int) -> Unit) {
    Text("$label · $value %", style = MaterialTheme.typography.labelLarge)
    Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 10f..70f)
}
