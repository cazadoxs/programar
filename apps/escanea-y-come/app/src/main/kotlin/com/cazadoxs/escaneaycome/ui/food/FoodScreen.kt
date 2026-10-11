package com.cazadoxs.escaneaycome.ui.food

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cazadoxs.escaneaycome.core.KJ_PER_KCAL
import com.cazadoxs.escaneaycome.core.Labels
import com.cazadoxs.escaneaycome.core.Meal
import com.cazadoxs.escaneaycome.core.gramsText
import com.cazadoxs.escaneaycome.core.kcalText
import com.cazadoxs.escaneaycome.core.parseAmount
import com.cazadoxs.escaneaycome.data.FoodEntity
import com.cazadoxs.escaneaycome.ui.MainViewModel
import com.cazadoxs.escaneaycome.ui.appViewModel
import com.cazadoxs.escaneaycome.ui.components.Card
import com.cazadoxs.escaneaycome.ui.components.NovaBadge
import com.cazadoxs.escaneaycome.ui.components.NumberField
import com.cazadoxs.escaneaycome.ui.components.NutriScore
import com.cazadoxs.escaneaycome.ui.components.Pill
import com.cazadoxs.escaneaycome.ui.container
import com.cazadoxs.escaneaycome.ui.theme.LocalFoodColors
import com.cazadoxs.escaneaycome.ui.today.dayTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodScreen(
    foodId: Long,
    entryId: Long,
    initialMeal: Meal,
    main: MainViewModel,
    onDone: (String) -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val vm = appViewModel(key = "food-$foodId-$entryId") { FoodViewModel(it, foodId, entryId) }
    val food by vm.food.collectAsStateWithLifecycle()
    val day by main.day.collectAsStateWithLifecycle()
    val calm = container().settings.settings.collectAsStateWithLifecycle().value.calmMode
    var gramsText by rememberSaveable { mutableStateOf("") }
    var meal by rememberSaveable { mutableStateOf(initialMeal) }

    LaunchedEffect(food?.id) {
        val f = food ?: return@LaunchedEffect
        if (gramsText.isEmpty()) gramsText = f.defaultGrams.gramsText()
        vm.loadEntry { e ->
            gramsText = e.grams.gramsText()
            meal = Meal.fromName(e.meal)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (vm.isEditing) "Editar" else dayTitle(day), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                actions = {
                    food?.let { f ->
                        IconButton(onClick = vm::toggleFavourite) {
                            Icon(
                                if (f.favourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                if (f.favourite) "Quitar de favoritos" else "Añadir a favoritos",
                                tint = if (f.favourite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onEdit(f.id) }) { Icon(Icons.Outlined.Edit, "Corregir datos") }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            val f = food
            val grams = parseAmount(gramsText)
            if (f != null) {
                Column(
                    Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp),
                ) {
                    Button(
                        onClick = { if (grams != null) vm.save(grams, meal, day, onDone) },
                        enabled = grams != null && grams > 0,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        val kcal = grams?.let { f.per100.forGrams(it).kcal }
                        val suffix = if (!calm && kcal != null) " · ${kcal.kcalText()} kcal" else ""
                        Text(
                            (if (vm.isEditing) "Guardar cambios" else "Añadir a ${meal.label.lowercase()}") + suffix,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (vm.isEditing) {
                        TextButton(onClick = { vm.deleteEntry(onDone) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Delete, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Borrar del diario")
                        }
                    }
                }
            }
        },
    ) { padding ->
        val f = food
        if (f == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProductHeader(f)
            PortionCard(f, gramsText, { gramsText = it }, calm)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Meal.entries.forEach { m ->
                    FilterChip(selected = m == meal, onClick = { meal = m }, label = { Text(m.label) })
                }
            }
            if (f.nutriScore != null || f.nova != null) {
                Card {
                    Text("Calidad", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    f.nutriScore?.let { NutriScore(it) }
                    if (f.nutriScore != null && f.nova != null) Spacer(Modifier.height(12.dp))
                    f.nova?.let { NovaBadge(it) }
                }
            }
            NutritionTable(f)
            val allergens = f.allergenList
            val claims = Labels.claims(f.labelList)
            if (allergens.isNotEmpty() || claims.isNotEmpty()) {
                Card {
                    if (allergens.isNotEmpty()) {
                        Text("Alérgenos", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            allergens.forEach { Pill(Labels.allergen(it), MaterialTheme.colorScheme.secondary) }
                        }
                    }
                    if (claims.isNotEmpty()) {
                        if (allergens.isNotEmpty()) Spacer(Modifier.height(12.dp))
                        Text("Etiquetas", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            claims.forEach { Pill(it, MaterialTheme.colorScheme.primary, onColor = MaterialTheme.colorScheme.onPrimary) }
                        }
                    }
                }
            }
            Attribution(f)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProductHeader(f: FoodEntity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (f.imageUrl != null) {
                AsyncImage(model = f.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Outlined.Fastfood, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(f.brand, f.quantity).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (f.isCustom) Text("Creado por ti", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortionCard(f: FoodEntity, gramsText: String, onGrams: (String) -> Unit, calm: Boolean) {
    val colors = LocalFoodColors.current
    val grams = parseAmount(gramsText) ?: 0.0
    val t = f.per100.forGrams(grams)
    Card(color = MaterialTheme.colorScheme.primaryContainer) {
        Text("¿Cuánto?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(onClick = { onGrams((grams - step(grams)).coerceAtLeast(0.0).gramsText()) }) { Icon(Icons.Outlined.Remove, "Menos") }
            NumberField(gramsText, onGrams, "Cantidad", Modifier.weight(1f).padding(horizontal = 8.dp), suffix = "g")
            OutlinedIconButton(onClick = { onGrams((grams + step(grams)).gramsText()) }) { Icon(Icons.Outlined.Add, "Más") }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            f.servingGrams?.let { s ->
                val label = f.servingLabel?.let { "1 ración ($it)" } ?: "1 ración (${s.gramsText()} g)"
                FilterChip(selected = grams == s, onClick = { onGrams(s.gramsText()) }, label = { Text(label) })
                FilterChip(selected = grams == s * 2, onClick = { onGrams((s * 2).gramsText()) }, label = { Text("2 raciones") })
            }
            FilterChip(selected = grams == 100.0, onClick = { onGrams("100") }, label = { Text("100 g") })
        }
        if (!calm) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("kcal", t.kcal.kcalText(), MaterialTheme.colorScheme.onPrimaryContainer)
                Stat("Proteína", "${t.protein.gramsText()} g", colors.protein)
                Stat("Hidratos", "${t.carbs.gramsText()} g", colors.carbs)
                Stat("Grasas", "${t.fat.gramsText()} g", colors.fat)
            }
        }
    }
}

private fun step(g: Double) = if (g >= 100) 25.0 else 10.0

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NutritionTable(f: FoodEntity) {
    val p = f.per100
    Card {
        Text("Información nutricional por 100 g", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Line("Valor energético", "${(p.kcal * KJ_PER_KCAL).kcalText()} kJ / ${p.kcal.kcalText()} kcal", bold = true)
        Line("Grasas", "${p.fat.gramsText()} g", bold = true)
        p.saturatedFat?.let { Line("  de las cuales saturadas", "${it.gramsText()} g") }
        Line("Hidratos de carbono", "${p.carbs.gramsText()} g", bold = true)
        p.sugars?.let { Line("  de los cuales azúcares", "${it.gramsText()} g") }
        p.fiber?.let { Line("Fibra alimentaria", "${it.gramsText()} g", bold = true) }
        Line("Proteínas", "${p.protein.gramsText()} g", bold = true)
        p.salt?.let { Line("Sal", "${it.gramsText()} g", bold = true) }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    val style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = style, modifier = Modifier.weight(1f), color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = style)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Attribution(f: FoodEntity) {
    val context = LocalContext.current
    Column {
        if (!f.isCustom && f.barcode != null) {
            Text(
                "Datos de Open Food Facts, la base de datos libre y colaborativa (licencia ODbL). " +
                    "Si algo no cuadra con la etiqueta, puedes corregirlo para todos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                // barcode is a validated digit string, so the URL cannot be redirected elsewhere.
                val uri = Uri.parse("https://world.openfoodfacts.org/product/${f.barcode}")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }) { Text("Ver en Open Food Facts") }
        }
        if (f.barcode != null) {
            Text("Código ${f.barcode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
