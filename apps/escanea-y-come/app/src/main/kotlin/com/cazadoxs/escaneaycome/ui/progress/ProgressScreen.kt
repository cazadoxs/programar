package com.cazadoxs.escaneaycome.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escaneaycome.core.FoodRank
import com.cazadoxs.escaneaycome.core.Meal
import com.cazadoxs.escaneaycome.core.Stats
import com.cazadoxs.escaneaycome.core.gramsText
import com.cazadoxs.escaneaycome.core.kcalText
import com.cazadoxs.escaneaycome.core.parseAmount
import com.cazadoxs.escaneaycome.ui.appViewModel
import com.cazadoxs.escaneaycome.ui.components.BarChart
import com.cazadoxs.escaneaycome.ui.components.Card
import com.cazadoxs.escaneaycome.ui.components.LineChart
import com.cazadoxs.escaneaycome.ui.components.NumberField
import com.cazadoxs.escaneaycome.ui.theme.LocalFoodColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(onPlus: () -> Unit) {
    val vm = appViewModel { ProgressViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val plus = s.settings.plus
    val calm = s.settings.calmMode
    val colors = LocalFoodColors.current
    var weightDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Progreso", style = MaterialTheme.typography.headlineLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.entries.forEach { r ->
                FilterChip(
                    selected = s.range == r,
                    onClick = { if (r.plus && !plus) onPlus() else vm.setRange(r) },
                    label = { Text(r.label) },
                    leadingIcon = if (r.plus && !plus) ({ Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp)) }) else null,
                )
            }
        }

        if (calm) {
            Card {
                Text("Modo tranquilo", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Aquí no verás calorías ni peso. Solo cómo se reparten tus comidas, que es lo que de verdad cuenta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Card {
                Text("Energía por día", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Línea: tu objetivo de ${s.settings.kcalGoal} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                val shown = if (s.days.size > 31) s.days.chunked(7).map { w -> w.last() to w.map { it.totals.kcal }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0 } else null
                if (shown != null) {
                    BarChart(shown.map { it.second }, shown.map { LocalDate.ofEpochDay(it.first.day).dayOfMonth.toString() }, s.settings.kcalGoal.toDouble())
                    Text("Media semanal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    BarChart(s.days.map { it.totals.kcal }, s.days.map { label(it.day, s.days.size) }, s.settings.kcalGoal.toDouble())
                }
            }
        }

        Card {
            Text("Medias", style = MaterialTheme.typography.titleMedium)
            Text(
                "${s.averages.loggedDays} de ${s.averages.days} días con algo anotado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (!calm) Avg("kcal", s.averages.average.kcal.kcalText(), MaterialTheme.colorScheme.primary)
                Avg("Proteína", "${s.averages.average.protein.gramsText()} g", colors.protein)
                Avg("Hidratos", "${s.averages.average.carbs.gramsText()} g", colors.carbs)
                Avg("Grasas", "${s.averages.average.fat.gramsText()} g", colors.fat)
            }
            Spacer(Modifier.height(14.dp))
            SplitBar(s.averages.average.energySplit().let { listOf(it.protein to colors.protein, it.carbs to colors.carbs, it.fat to colors.fat) })
        }

        if (!calm) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Peso", style = MaterialTheme.typography.titleMedium)
                        val last = s.weights.lastOrNull()
                        Text(
                            last?.let { "Último: ${it.kg.gramsText()} kg" } ?: "Pésate cuando quieras; la tendencia importa más que un día.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { weightDialog = true }) { Text("Anotar") }
                }
                if (s.weights.size >= 2) {
                    Spacer(Modifier.height(12.dp))
                    val values = s.weights.map { it.kg }
                    LineChart(values, Stats.movingAverage(values, 5))
                }
            }
        }

        PlusCard("Lo que más te aporta", plus, onPlus) {
            if (s.topProtein.isEmpty()) {
                Text("Anota unos días y verás qué alimentos te dan más proteína y energía.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Proteína", style = MaterialTheme.typography.labelLarge, color = colors.protein)
                s.topProtein.forEach { RankRow(it, "g") }
                if (!calm) {
                    Spacer(Modifier.height(10.dp))
                    Text("Energía", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    s.topKcal.forEach { RankRow(it, "kcal") }
                }
            }
        }

        PlusCard("Cómo repartes el día", plus, onPlus) {
            Meal.entries.forEach { m ->
                val share = s.perMeal[m] ?: 0.0
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.label, Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(share.toFloat()).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    Text("${(share * 100).roundToInt()} %", Modifier.width(48.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (!calm) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Días cerca de tu objetivo (±10 %): ${s.adherence} %",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (weightDialog) {
        var text by rememberSaveable { mutableStateOf(s.settings.weightKg.gramsText()) }
        val kg = parseAmount(text)?.takeIf { it in 25.0..350.0 }
        AlertDialog(
            onDismissRequest = { weightDialog = false },
            title = { Text("Peso de hoy") },
            text = { NumberField(text, { text = it }, "Peso", suffix = "kg") },
            confirmButton = { TextButton(enabled = kg != null, onClick = { kg?.let(vm::logWeight); weightDialog = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { weightDialog = false }) { Text("Cancelar") } },
        )
    }
}

private fun label(day: Long, total: Int): String {
    val d = LocalDate.ofEpochDay(day)
    return if (total <= 7) {
        d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.forLanguageTag("es-ES")).uppercase()
    } else if (d.dayOfMonth % 5 == 0 || d.dayOfMonth == 1) {
        d.dayOfMonth.toString()
    } else {
        ""
    }
}

@Composable
private fun Avg(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SplitBar(parts: List<Pair<Double, Color>>) {
    Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        parts.filter { it.first > 0 }.forEach { (share, color) ->
            Box(Modifier.weight(share.toFloat()).height(14.dp).background(color))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("Proteína", "Hidratos", "Grasas").zip(parts).forEach { (l, p) ->
            Text("$l ${(p.first * 100).roundToInt()} %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RankRow(r: FoodRank, unit: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text("${if (unit == "kcal") r.value.kcalText() else r.value.gramsText()} $unit", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PlusCard(title: String, unlocked: Boolean, onPlus: () -> Unit, content: @Composable () -> Unit) {
    Card(modifier = if (unlocked) Modifier else Modifier.clickable(onClick = onPlus)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!unlocked) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(4.dp))
                Text("Plus", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (unlocked) {
            content()
        } else {
            Text(
                "Desbloquéalo con Plus, un pago único. Escanear y anotar siguen siendo gratis siempre.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
