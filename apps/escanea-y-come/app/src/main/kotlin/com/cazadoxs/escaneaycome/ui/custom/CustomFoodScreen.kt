package com.cazadoxs.escaneaycome.ui.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cazadoxs.escaneaycome.core.Barcode
import com.cazadoxs.escaneaycome.core.Per100
import com.cazadoxs.escaneaycome.core.gramsText
import com.cazadoxs.escaneaycome.core.kcalText
import com.cazadoxs.escaneaycome.core.parseAmount
import com.cazadoxs.escaneaycome.ui.components.Card
import com.cazadoxs.escaneaycome.ui.components.NumberField
import com.cazadoxs.escaneaycome.ui.container
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFoodScreen(
    foodId: Long,
    barcode: String,
    initialName: String,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val repo = container().repo
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable { mutableStateOf(false) }
    var isCustom by rememberSaveable { mutableStateOf(foodId == 0L) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var brand by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf(barcode) }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var sugars by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var satFat by rememberSaveable { mutableStateOf("") }
    var fiber by rememberSaveable { mutableStateOf("") }
    var salt by rememberSaveable { mutableStateOf("") }
    var serving by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(foodId) {
        if (foodId == 0L || loaded) return@LaunchedEffect
        repo.foodNow(foodId)?.let { f ->
            isCustom = f.isCustom
            name = f.name; brand = f.brand.orEmpty(); code = f.barcode.orEmpty()
            kcal = f.kcal.kcalText(); protein = f.protein.gramsText(); carbs = f.carbs.gramsText(); fat = f.fat.gramsText()
            sugars = f.sugars?.gramsText().orEmpty(); satFat = f.saturatedFat?.gramsText().orEmpty()
            fiber = f.fiber?.gramsText().orEmpty(); salt = f.salt?.gramsText().orEmpty()
            serving = f.servingGrams?.gramsText().orEmpty()
        }
        loaded = true
    }

    val codeOk = code.isEmpty() || Barcode.normalize(code) != null
    val per100 = Per100.sanitized(
        kcal = parseAmount(kcal), kj = null,
        protein = parseAmount(protein), carbs = parseAmount(carbs), fat = parseAmount(fat),
        sugars = parseAmount(sugars), fiber = parseAmount(fiber), salt = parseAmount(salt), saturatedFat = parseAmount(satFat),
    )
    val canSave = name.isNotBlank() && per100 != null && codeOk

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (foodId == 0L) "Nuevo alimento" else "Corregir alimento") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                actions = {
                    if (foodId != 0L && isCustom) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Borrar alimento") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (foodId != 0L && !isCustom) {
                Text(
                    "Tus cambios se guardan solo en tu móvil y tienen prioridad sobre Open Food Facts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(brand, { brand = it.take(60) }, label = { Text("Marca (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                code, { v -> code = v.filter { it.isDigit() }.take(14) },
                label = { Text("Código de barras (opcional)") }, singleLine = true, isError = !codeOk,
                supportingText = { if (!codeOk) Text("Código no válido") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Card {
                Text("Por 100 g, como en la etiqueta", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                NumberField(kcal, { kcal = it }, "Valor energético", Modifier.fillMaxWidth(), "kcal")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(fat, { fat = it }, "Grasas", Modifier.weight(1f), "g")
                    NumberField(satFat, { satFat = it }, "Saturadas", Modifier.weight(1f), "g")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(carbs, { carbs = it }, "Hidratos", Modifier.weight(1f), "g")
                    NumberField(sugars, { sugars = it }, "Azúcares", Modifier.weight(1f), "g")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(protein, { protein = it }, "Proteínas", Modifier.weight(1f), "g")
                    NumberField(fiber, { fiber = it }, "Fibra", Modifier.weight(1f), "g")
                }
                Spacer(Modifier.height(8.dp))
                NumberField(salt, { salt = it }, "Sal", Modifier.fillMaxWidth(), "g")
                if (kcal.isEmpty() && per100 != null) {
                    Text(
                        "Sin kcal, las calculamos con los macros: ${per100.kcal.kcalText()} kcal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            NumberField(serving, { serving = it }, "Una ración pesa (opcional)", Modifier.fillMaxWidth(), "g")
            Button(
                onClick = {
                    val p = per100 ?: return@Button
                    scope.launch {
                        val id = repo.saveCustomFood(
                            id = foodId.takeIf { it != 0L },
                            name = name, brand = brand, barcode = code.ifEmpty { null },
                            per100 = p, servingGrams = parseAmount(serving),
                        )
                        onSaved(id)
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Guardar", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar «$name»?") },
            text = { Text("Lo que ya anotaste en el diario se queda como está.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { repo.deleteFood(foodId); onDeleted() }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
