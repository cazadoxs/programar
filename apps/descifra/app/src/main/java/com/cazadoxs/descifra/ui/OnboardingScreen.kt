package com.cazadoxs.descifra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private data class Page(val eyebrow: String, val title: String, val body: String, val bullets: List<String>)

private val pages = listOf(
    Page(
        "1 de 3",
        "Una carta que no entiendes ya no da miedo",
        "Haz una foto a la carta de Hacienda, la Seguridad Social, tu banco o tu casero.",
        listOf("Te explicamos qué es en lenguaje normal.", "Te decimos qué piden, cuánto y para cuándo.", "Puedes añadir el plazo a tu calendario y recibir un aviso antes de que venza."),
    ),
    Page(
        "2 de 3",
        "Tus cartas se quedan en tu móvil",
        "Leemos la foto en el propio teléfono y guardamos el texto cifrado, solo aquí.",
        listOf("No hay cuenta, ni servidores nuestros.", "Si tu móvil tiene IA integrada (Gemini Nano), el resumen también se hace sin internet.", "Puedes borrar todo cuando quieras."),
    ),
    Page(
        "3 de 3",
        "Siempre verás el original",
        "Un resumen automático puede fallar, y en asuntos de dinero o plazos no queremos que te fíes a ciegas.",
        listOf("El texto original va siempre al lado del resumen.", "Cada fecha enseña la frase de la que sale.", "Los plazos calculados se marcan como estimados."),
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val p = MaterialTheme.palette
    var i by remember { mutableIntStateOf(0) }
    val page = pages[i]
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(32.dp))
            Stamp("DESCIFRA", p.stamp, angle = -4f)
            Spacer(Modifier.height(28.dp))
            Eyebrow(page.eyebrow)
            Spacer(Modifier.height(8.dp))
            Text(page.title, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(14.dp))
            Text(page.body, style = MaterialTheme.typography.bodyLarge, color = p.inkSoft)
            Spacer(Modifier.height(20.dp))
            page.bullets.forEach {
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text("✓", color = p.stamp, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.size(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            pages.indices.forEach { n ->
                Spacer(Modifier.padding(4.dp).size(if (n == i) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (n == i) p.stamp else p.line))
            }
        }
        BigButton(if (i < pages.lastIndex) "Siguiente" else "Empezar", { if (i < pages.lastIndex) i++ else onDone() }, Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp))
    }
}
