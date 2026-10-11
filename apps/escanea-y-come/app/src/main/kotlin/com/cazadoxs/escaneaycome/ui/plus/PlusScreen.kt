package com.cazadoxs.escaneaycome.ui.plus

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escaneaycome.billing.PlusState
import com.cazadoxs.escaneaycome.ui.components.Card
import com.cazadoxs.escaneaycome.ui.container

private val FREE = listOf(
    "Escáner de códigos de barras ilimitado",
    "Diario, macros, agua y peso",
    "Favoritos, recientes y comidas guardadas",
    "Nutri-Score, NOVA y alérgenos",
    "Sin anuncios",
)

private val PLUS = listOf(
    "Estadísticas de 30 y 90 días",
    "Qué alimentos te aportan más proteína y energía",
    "Cómo repartes la energía en el día y cuántos días aciertas",
    "Exportar todo tu diario a CSV (Excel, Sheets)",
    "Colores extra: Mar, Ciruela y Naranja",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusScreen(onBack: () -> Unit) {
    val billing = container().billing
    val state by billing.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escanea y come Plus") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Un pago. Para siempre.",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                "Sin suscripciones ni pruebas que se renuevan solas. Lo básico es y será gratis: Plus es para quien quiere mirar más a fondo y, de paso, apoyar la app.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card {
                Text("Gratis para siempre", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FREE.forEach { Bullet(it, MaterialTheme.colorScheme.primary) }
            }
            Card(color = MaterialTheme.colorScheme.primaryContainer) {
                Text("Con Plus, además", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PLUS.forEach { Bullet(it, MaterialTheme.colorScheme.secondary) }
            }
            when (val s = state) {
                PlusState.Owned -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Favorite, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Ya tienes Plus. ¡Gracias por apoyar la app!", style = MaterialTheme.typography.titleMedium)
                }
                PlusState.Pending -> Text("Tu pago está pendiente. Plus se activará solo en cuanto Google lo confirme.")
                is PlusState.Available -> Button(
                    onClick = { activity?.let(billing::buy) },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Conseguir Plus · ${s.price}", style = MaterialTheme.typography.titleMedium) }
                PlusState.Loading -> Text("Conectando con Google Play…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                is PlusState.Unavailable -> Column {
                    Text(s.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.TextButton(onClick = billing::start) { Text("Reintentar") }
                }
            }
            Text(
                "El pago lo gestiona Google Play. Si cambias de móvil, Plus se restaura solo con la misma cuenta de Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Bullet(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CheckCircle, null, tint = color)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
