package com.cazadoxs.escaneaycome.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cazadoxs.escaneaycome.core.Activity
import com.cazadoxs.escaneaycome.core.GoalType
import com.cazadoxs.escaneaycome.core.Goals
import com.cazadoxs.escaneaycome.core.Sex
import com.cazadoxs.escaneaycome.data.AppSettings
import com.cazadoxs.escaneaycome.core.parseAmount
import com.cazadoxs.escaneaycome.ui.components.Card
import com.cazadoxs.escaneaycome.ui.components.NumberField
import java.time.LocalDate

/** Three short steps. Everything can be changed later in Ajustes; nothing leaves the phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: (AppSettings) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var sex by rememberSaveable { mutableStateOf(Sex.OTRO) }
    var year by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var activity by rememberSaveable { mutableStateOf(Activity.LIGERA) }
    var goal by rememberSaveable { mutableStateOf(GoalType.MANTENER) }
    var calm by rememberSaveable { mutableStateOf(false) }

    val yearV = parseAmount(year)?.toInt()
    val heightV = parseAmount(height)
    val weightV = parseAmount(weight)
    val thisYear = LocalDate.now().year
    val bodyOk = yearV != null && yearV in (thisYear - 110)..(thisYear - 13) && heightV != null && heightV in 100.0..250.0 && weightV != null && weightV in 30.0..350.0

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Escanea y come", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            when (step) {
                0 -> {
                    Text("Escanear siempre será gratis.", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Apunta con la cámara al código de barras y listo: calorías, macros, Nutri-Score y alérgenos. " +
                            "Sin cuenta, sin anuncios y sin enviar tu diario a ningún sitio.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card {
                        Text("Importante", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Esta app es orientativa y no sustituye a un médico o dietista. Si tienes o has tenido un trastorno de la conducta alimentaria, " +
                                "consulta antes de contar calorías: puedes activar el modo tranquilo, que oculta los números.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(onClick = { step = 1 }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("Empezar") }
                }
                1 -> {
                    Text("Cuéntanos un poco", style = MaterialTheme.typography.displaySmall)
                    Text("Solo para calcular un objetivo orientativo. Se queda en tu móvil.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Sex.MUJER to "Mujer", Sex.HOMBRE to "Hombre", Sex.OTRO to "Prefiero no decirlo").forEach { (v, l) ->
                            FilterChip(selected = sex == v, onClick = { sex = v }, label = { Text(l) })
                        }
                    }
                    NumberField(year, { year = it }, "Año de nacimiento", Modifier.fillMaxWidth(), decimals = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(height, { height = it }, "Altura", Modifier.weight(1f), "cm")
                        NumberField(weight, { weight = it }, "Peso", Modifier.weight(1f), "kg")
                    }
                    Text("¿Cuánto te mueves?", style = MaterialTheme.typography.titleMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Activity.entries.forEach { a ->
                            FilterChip(selected = activity == a, onClick = { activity = a }, label = { Text(a.label) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Button(onClick = { step = 2 }, enabled = bodyOk, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("Siguiente") }
                    OutlinedButton(onClick = { step = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Atrás") }
                }
                else -> {
                    val minor = yearV != null && thisYear - yearV < Goals.MIN_ADULT_AGE
                    Text("¿Qué buscas?", style = MaterialTheme.typography.displaySmall)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GoalType.entries.forEach { g ->
                            FilterChip(
                                selected = goal == g, onClick = { goal = g },
                                enabled = !(minor && g == GoalType.PERDER),
                                label = { Text(g.label) }, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (minor) Text("Con menos de 18 años calculamos solo para mantener. Habla con tu pediatra para cualquier cambio.", style = MaterialTheme.typography.bodySmall)
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Modo tranquilo", style = MaterialTheme.typography.titleMedium)
                                Text("Oculta calorías y peso. Solo ves qué has comido.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = calm, onCheckedChange = { calm = it })
                        }
                    }
                    Button(
                        onClick = {
                            onDone(
                                AppSettings(
                                    sex = sex, birthYear = yearV ?: 1990, heightCm = heightV ?: 170.0, weightKg = weightV ?: 70.0,
                                    activity = activity, goal = if (minor && goal == GoalType.PERDER) GoalType.MANTENER else goal, calmMode = calm,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
                    ) { Text("Listo") }
                    OutlinedButton(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Atrás") }
                }
            }
        }
    }
}
