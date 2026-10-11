package com.cazadoxs.escaneaycome.ui.search

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cazadoxs.escaneaycome.core.Barcode
import com.cazadoxs.escaneaycome.core.Meal
import com.cazadoxs.escaneaycome.core.OffProduct
import com.cazadoxs.escaneaycome.core.kcalText
import com.cazadoxs.escaneaycome.data.FoodEntity
import com.cazadoxs.escaneaycome.data.SavedMealWithItems
import com.cazadoxs.escaneaycome.ui.MainViewModel
import com.cazadoxs.escaneaycome.ui.appViewModel
import com.cazadoxs.escaneaycome.ui.components.EmptyHint
import com.cazadoxs.escaneaycome.ui.components.SectionTitle
import com.cazadoxs.escaneaycome.ui.theme.NutriScoreColors
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    main: MainViewModel,
    meal: Meal?,
    onOpenFood: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onScan: () -> Unit,
    onManualCode: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val vm = appViewModel { SearchViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val custom by vm.custom.collectAsStateWithLifecycle()
    val meals by vm.meals.collectAsStateWithLifecycle()
    val day by main.day.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val targetMeal = meal ?: LocalTime.now().let { Meal.forTime(it.hour, it.minute) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
                    Text(
                        if (meal != null) "Añadir a ${meal.label.lowercase()}" else "Buscar",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Yogur, pan, Hacendado…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (ui.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Clear, "Borrar búsqueda") }
                        } else {
                            IconButton(onClick = onScan) { Icon(Icons.Outlined.QrCodeScanner, "Escanear") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }

            if (ui.query.isBlank()) {
                item {
                    ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.background) {
                        listOf("Recientes", "Favoritos", "Comidas", "Mis alimentos").forEachIndexed { i, t ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                when (tab) {
                    0 -> foodList(recent, onOpenFood, "Aún no hay recientes", "Lo que escanees o añadas aparecerá aquí para repetirlo en un toque.")
                    1 -> foodList(favourites, onOpenFood, "Sin favoritos", "Pulsa el corazón en un alimento para tenerlo siempre a mano.")
                    2 -> {
                        if (meals.isEmpty()) {
                            item { EmptyHint("Sin comidas guardadas", "En Hoy, abre el menú ⋮ de una comida y elige «Guardar como comida». Así repites tu desayuno de siempre con un toque.") }
                        }
                        items(meals, key = { "m" + it.meal.id }) { m ->
                            SavedMealRow(
                                m,
                                targetMeal,
                                onLog = {
                                    vm.logMeal(m.meal.id, targetMeal, day) {
                                        scope.launch { snackbar.showSnackbar("«${m.meal.name}» añadida a ${targetMeal.label.lowercase()}") }
                                    }
                                },
                                onDelete = { vm.deleteMeal(m.meal.id) },
                            )
                        }
                    }
                    else -> {
                        item {
                            OutlinedButton(onClick = { onCreate("") }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Crear alimento")
                            }
                        }
                        foodList(custom, onOpenFood, "Nada por aquí", "Crea tus recetas o productos sin código (el cocido de tu madre también cuenta).")
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onManualCode) {
                        Icon(Icons.Outlined.Dialpad, null); Spacer(Modifier.width(6.dp)); Text("Escribir el código de barras")
                    }
                }
            } else {
                if (ui.local.isNotEmpty()) {
                    item { SectionTitle("En tu móvil") }
                    items(ui.local, key = { "l" + it.id }) { FoodRow(it.name, it.brand, it.kcal, it.nutriScore, it.imageUrl) { onOpenFood(it.id) } }
                }
                item { SectionTitle("En Open Food Facts") }
                when {
                    ui.loadingOnline -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    ui.onlineError != null -> item {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(ui.onlineError!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = vm::retryOnline) { Text("Reintentar") }
                        }
                    }
                    ui.query.trim().length < 3 -> item {
                        Text("Escribe al menos 3 letras.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ui.searchedOnline && ui.online.isEmpty() -> item {
                        Text("Sin resultados. Prueba con la marca o escanéalo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(ui.online, key = { "o" + it.barcode }) { p ->
                    FoodRow(p.name, p.brand, p.per100.kcal, p.nutriScore, p.imageUrl) { vm.open(p, onOpenFood) }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { onCreate(ui.query.trim()) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("¿No aparece? Créalo tú")
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.foodList(
    list: List<FoodEntity>,
    onOpen: (Long) -> Unit,
    emptyTitle: String,
    emptyBody: String,
) {
    if (list.isEmpty()) item { EmptyHint(emptyTitle, emptyBody) }
    items(list, key = { "f" + it.id }) { FoodRow(it.name, it.brand, it.kcal, it.nutriScore, it.imageUrl) { onOpen(it.id) } }
}

@Composable
private fun FoodRow(name: String, brand: String?, kcal100: Double, nutri: String?, image: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Outlined.Fastfood, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(brand, "${kcal100.kcalText()} kcal/100 g").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (nutri != null) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(NutriScoreColors.of(nutri)),
                contentAlignment = Alignment.Center,
            ) { Text(nutri.uppercase(), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun SavedMealRow(m: SavedMealWithItems, target: Meal, onLog: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onLog).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.meal.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${m.items.size} alimentos · ${m.totals.kcal.kcalText()} kcal · toca para añadir a ${target.label.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, "Borrar comida guardada") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Borrar «${m.meal.name}»?") },
            text = { Text("Solo se borra la plantilla. Tu diario no cambia.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun ManualCodeDialog(onDismiss: () -> Unit, onCode: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val valid = Barcode.normalize(code) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Código de barras") },
        text = {
            Column {
                Text("Escribe los números que hay debajo de las barras.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(14) },
                    singleLine = true,
                    isError = code.length >= 8 && !valid,
                    supportingText = { if (code.length >= 8 && !valid) Text("El último dígito no cuadra. Revisa el código.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onCode(code) }) { Text("Buscar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
