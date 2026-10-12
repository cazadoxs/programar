package com.cazadoxs.descifra.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cazadoxs.descifra.billing.ProState

@Composable
fun PaywallScreen(state: ProState, onBuy: () -> Unit, onBack: () -> Unit) {
    val p = MaterialTheme.palette
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = onBack, Modifier.padding(8.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = p.ink) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Stamp("PRO", p.stamp, angle = -6f)
            Spacer(Modifier.height(20.dp))
            Text("Descifra Pro", style = MaterialTheme.typography.headlineLarge)
            Text("Un solo pago. Sin suscripción. Para siempre.", color = p.inkSoft, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Paper(Modifier.fillMaxWidth()) {
                Eyebrow("Gratis, siempre")
                Spacer(Modifier.height(6.dp))
                listOf("Escanear y leer cartas en el móvil", "Resumen, plazos, importes y pasos a seguir", "IA en el móvil si tu teléfono la tiene", "Avisos y calendario", "Bloqueo con huella y borrado total").forEach { Line(it) }
            }
            Spacer(Modifier.height(14.dp))
            Paper(Modifier.fillMaxWidth(), accent = p.stamp) {
                Eyebrow("Con Pro", color = p.stamp)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "IA más potente con tu propia clave (Claude, OpenAI o Gemini)",
                    "Pregunta lo que no entiendas de cada carta",
                    "Borradores de alegaciones, aplazamientos y respuestas",
                    "Exportar el resumen en PDF",
                ).forEach { Line(it) }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "La clave de IA es tuya: la pagas directamente al proveedor según lo que uses. Descifra no cobra por cada consulta.",
                color = p.inkSoft,
                style = MaterialTheme.typography.bodySmall,
            )
            state.message?.let { Text(it, color = p.stamp, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(24.dp).navigationBarsPadding()) {
            if (state.isPro) {
                Text("Ya tienes Descifra Pro. Gracias.", style = MaterialTheme.typography.titleMedium, color = p.green)
            } else {
                BigButton(if (state.price != null) "Desbloquear Pro · ${state.price}" else "Desbloquear Pro", onBuy, Modifier.fillMaxWidth(), color = p.stamp, onColor = p.onStamp, enabled = !state.pending)
            }
        }
    }
}

@Composable
private fun Line(text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text("✓", color = MaterialTheme.palette.stamp, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
