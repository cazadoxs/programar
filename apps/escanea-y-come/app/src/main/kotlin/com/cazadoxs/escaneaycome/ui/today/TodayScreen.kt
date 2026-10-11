package com.cazadoxs.escaneaycome.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escaneaycome.core.Meal
import com.cazadoxs.escaneaycome.core.gramsText
import com.cazadoxs.escaneaycome.core.kcalText
import com.cazadoxs.escaneaycome.core.parseAmount
import com.cazadoxs.escaneaycome.data.EntryEntity
import com.cazadoxs.escaneaycome.ui.MainViewModel
import com.cazadoxs.escaneaycome.ui.appViewModel
import com.cazadoxs.escaneaycome.ui.components.Card
import com.cazadoxs.escaneaycome.ui.components.EnergyRing
import com.cazadoxs.escaneaycome.ui.components.MacroBar
import com.cazadoxs.escaneaycome.ui.components.NumberField
import com.cazadoxs.escaneaycome.ui.components.Pill
import com.cazadoxs.escaneaycome.ui.theme.LocalFoodColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPANISH = Locale.forLanguageTag("es-ES")

fun dayTitle(day: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (day) {
        today -> "Hoy"
        today - 1 -> "Ayer"
        today + 1 -> "Mañana"
        else -> LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH))
            .replaceFirstChar { it.uppercase() }
    }
}

@Composable
fun TodayScreen(
    main: MainViewModel,
    onAdd: (Meal) -> Unit,
    onScan: () -> Unit,
    onOpenEntry: (EntryEntity) -> Unit,
) {
    val vm = appViewModel { TodayViewModel(it, main.day) }
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var quickAddFor by rememberSaveable { mutableStateOf<Meal?>(null) }
    var saveMealFor by rememberSaveable { mutableStateOf<Meal?>(null) }
    val calm = s.settings.calmMode

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DayHeader(
                    day = s.day,
                    streak = if (calm) 0 else s.streak,
                    onPrev = { main.shiftDay(-1) },
                    onNext = { main.shiftDay(1) },
                    onToday = { main.setDay(LocalDate.now().toEpochDay()) },
                )
            }
            item { EnergyCard(s, onScan) }
            item { WaterCard(s.water, s.settings.waterGoal, vm::setWater) }
            items(Meal.entries.toList(), key = { it.name }) { meal ->
                MealCard(
                    meal = meal,
                    entries = s.entries[meal].orEmpty(),
                    kcal = s.mealTotals(meal).kcal,
                    calm = calm,
                    onAdd = { onAdd(meal) },
                    onOpen = onOpenEntry,
                    onDelete = { e ->
                        vm.delete(e)
                        scope.launch {
                            val r = snackbar.showSnackbar("Borrado: ${e.name}", actionLabel = "Deshacer", duration = SnackbarDuration.Short)
                            if (r == SnackbarResult.ActionPerformed) vm.restore(e)
                        }
                    },
                    onCopyYesterday = {
                        vm.copyFromYesterday(meal) { n ->
                            toast(if (n == 0) "Ayer no anotaste nada en ${meal.label.lowercase()}" else "Copiados $n alimentos de ayer")
                        }
                    },
                    onSave = { saveMealFor = meal },
                    onQuickAdd = { quickAddFor = meal },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    quickAddFor?.let { meal ->
        QuickAddDialog(meal, onDismiss = { quickAddFor = null }) { name, kcal, p, c, f ->
            vm.quickAdd(name, kcal, p, c, f, meal)
            quickAddFor = null
        }
    }
    saveMealFor?.let { meal ->
        SaveMealDialog(meal, onDismiss = { saveMealFor = null }) { name ->
            vm.saveMeal(name, meal) { ok -> toast(if (ok) "Guardada «$name». La tienes en Buscar › Comidas" else "No hay nada que guardar en ${meal.label.lowercase()}") }
            saveMealFor = null
        }
    }
}

@Composable
private fun DayHeader(day: Long, streak: Int, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val today = LocalDate.now().toEpochDay()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(enabled = day != today, onClick = onToday)) {
            Text(
                LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMM", SPANISH)).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(dayTitle(day), style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (streak >= 2) {
            Pill("🔥 $streak días", MaterialTheme.colorScheme.secondaryContainer, onColor = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Día anterior") }
        IconButton(onClick = onNext, enabled = day < today + 1) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Día siguiente") }
    }
}

@Composable
private fun EnergyCard(s: TodayState, onScan: () -> Unit) {
    val colors = LocalFoodColors.current
    val t = s.settings.targets
    val calm = s.settings.calmMode
    Card(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EnergyRing(
                progress = if (t.kcal > 0) (s.totals.kcal / t.kcal).toFloat() else 0f,
                size = 150.dp,
                color = if (calm) colors.calm else MaterialTheme.colorScheme.primary,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (calm) {
                        Text("${s.count}", style = MaterialTheme.typography.displaySmall)
                        Text(if (s.count == 1) "alimento" else "alimentos", style = MaterialTheme.typography.labelMedium)
                    } else {
                        val left = t.kcal - s.totals.kcal
                        Text(abs(left).roundToInt().toString(), style = MaterialTheme.typography.displaySmall)
                        Text(if (left >= 0) "kcal por delante" else "kcal sobre tu objetivo", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!calm) {
                    Text(
                        "${s.totals.kcal.kcalText()} de ${t.kcal} kcal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                MacroBar("Proteína", s.totals.protein, t.proteinG, colors.protein, !calm)
                MacroBar("Hidratos", s.totals.carbs, t.carbsG, colors.carbs, !calm)
                MacroBar("Grasas", s.totals.fat, t.fatG, colors.fat, !calm)
            }
        }
        if (s.count == 0) {
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("Escanea lo primero que comas")
            }
        }
    }
}

@Composable
private fun WaterCard(glasses: Int, goal: Int, onSet: (Int) -> Unit) {
    val water = LocalFoodColors.current.water
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Agua", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$glasses/$goal vasos", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val shown = maxOf(goal, glasses + 1).coerceAtMost(12)
            for (i in 1..shown) {
                val filled = i <= glasses
                IconButton(onClick = { onSet(if (filled && i == glasses) i - 1 else i) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (filled) Icons.Filled.WaterDrop else Icons.Outlined.WaterDrop,
                        contentDescription = "$i vasos",
                        tint = if (filled) water else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealCard(
    meal: Meal,
    entries: List<EntryEntity>,
    kcal: Double,
    calm: Boolean,
    onAdd: () -> Unit,
    onOpen: (EntryEntity) -> Unit,
    onDelete: (EntryEntity) -> Unit,
    onCopyYesterday: () -> Unit,
    onSave: () -> Unit,
    onQuickAdd: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(meal.label, style = MaterialTheme.typography.titleLarge)
                if (!calm && entries.isNotEmpty()) {
                    Text("${kcal.kcalText()} kcal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Más opciones de ${meal.label}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Copiar de ayer") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        onClick = { menu = false; onCopyYesterday() },
                    )
                    DropdownMenuItem(
                        text = { Text("Añadido rápido") },
                        leadingIcon = { Icon(Icons.Outlined.Bolt, null) },
                        onClick = { menu = false; onQuickAdd() },
                    )
                    DropdownMenuItem(
                        text = { Text("Guardar como comida") },
                        leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, null) },
                        enabled = entries.isNotEmpty(),
                        onClick = { menu = false; onSave() },
                    )
                }
            }
            IconButton(
                onClick = onAdd,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).size(40.dp),
            ) {
                Icon(Icons.Outlined.Add, "Añadir a ${meal.label}", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        if (entries.isNotEmpty()) Spacer(Modifier.height(6.dp))
        entries.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            key(e.id) {
            val state = rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    if (it != SwipeToDismissBoxValue.Settled) onDelete(e)
                    it != SwipeToDismissBoxValue.Settled
                },
            )
            SwipeToDismissBox(
                state = state,
                backgroundContent = {
                    Row(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) { Icon(Icons.Outlined.Delete, "Borrar", tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                },
                enableDismissFromStartToEnd = false,
            ) {
                EntryRow(e, calm, onClick = { onOpen(e) })
            }
            }
        }
    }
}

@Composable
private fun EntryRow(e: EntryEntity, calm: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = e.foodId != null, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(e.brand, if (e.grams > 0) "${e.grams.gramsText()} g" else null).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (!calm) {
            Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(e.kcal.kcalText(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun QuickAddDialog(meal: Meal, onDismiss: () -> Unit, onAdd: (String, Double, Double, Double, Double) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var p by rememberSaveable { mutableStateOf("") }
    var c by rememberSaveable { mutableStateOf("") }
    var f by rememberSaveable { mutableStateOf("") }
    val k = parseAmount(kcal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadido rápido · ${meal.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Qué era (opcional)") }, singleLine = true)
                NumberField(kcal, { kcal = it }, "Kilocalorías", suffix = "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(p, { p = it }, "Prot.", Modifier.weight(1f), "g")
                    NumberField(c, { c = it }, "Hidr.", Modifier.weight(1f), "g")
                    NumberField(f, { f = it }, "Grasa", Modifier.weight(1f), "g")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = k != null, onClick = {
                onAdd(name, k ?: 0.0, parseAmount(p) ?: 0.0, parseAmount(c) ?: 0.0, parseAmount(f) ?: 0.0)
            }) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SaveMealDialog(meal: Meal, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar ${meal.label.lowercase()}") },
        text = {
            Column {
                Text("Ponle nombre y podrás repetirla con un toque.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Ej.: Tostada con aceite y café") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
