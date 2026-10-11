package com.cazadoxs.escanerlimpio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cazadoxs.escanerlimpio.ui.components.highlight
import com.cazadoxs.escanerlimpio.ui.components.paper
import com.cazadoxs.escanerlimpio.ui.theme.LocalBrand
import kotlinx.coroutines.launch

private class Slide(val icon: ImageVector, val title: String, val mark: String, val body: String)

private val slides = listOf(
    Slide(Icons.Outlined.DocumentScanner, "Escanea sin", "marcas de agua.", "Bordes automáticos, filtros y quita dedos y manchas. Gratis y sin límite."),
    Slide(Icons.Outlined.WifiOff, "Todo se queda", "en tu móvil.", "Sin cuenta, sin nube y sin permiso de Internet. Tu DNI no sale de aquí."),
    Slide(Icons.Outlined.AutoAwesome, "Se ordena", "solo.", "Lee el texto en español, pone nombre al archivo y lo guarda en DNI, Nóminas, Facturas…"),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val brand = LocalBrand.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Saltar") }
        }
        HorizontalPager(pager, modifier = Modifier.weight(1f)) { i ->
            val s = slides[i]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(width = 120.dp, height = 156.dp).paper(fold = 28.dp, radius = 14.dp, elevation = 8.dp), contentAlignment = Alignment.Center) {
                    Icon(s.icon, null, tint = brand.ink, modifier = Modifier.size(52.dp))
                }
                Spacer(Modifier.height(40.dp))
                Text(s.title, style = MaterialTheme.typography.displaySmall)
                Text(s.mark, style = MaterialTheme.typography.displaySmall, modifier = Modifier.highlight(brand.highlighter))
                Spacer(Modifier.height(16.dp))
                Text(s.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(slides.size) { i ->
                    Box(
                        Modifier.size(width = if (i == pager.currentPage) 22.dp else 8.dp, height = 8.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Button(onClick = {
                if (pager.currentPage == slides.lastIndex) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }) {
                Text(if (pager.currentPage == slides.lastIndex) "Empezar" else "Siguiente")
            }
        }
    }
}
