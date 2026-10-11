package com.cazadoxs.escanerlimpio.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escanerlimpio.MainActivity
import com.cazadoxs.escanerlimpio.ui.LocalActions
import com.cazadoxs.escanerlimpio.ui.app
import com.cazadoxs.escanerlimpio.ui.components.highlight
import com.cazadoxs.escanerlimpio.ui.components.paper
import com.cazadoxs.escanerlimpio.ui.theme.LocalBrand

@Composable
fun ProScreen(activity: MainActivity) {
    val app = app()
    val actions = LocalActions.current
    val isPro by app.billing.isPro.collectAsStateWithLifecycle()
    val price by app.billing.price.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        IconButton(onClick = { actions.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
        Text("Escáner limpio", style = MaterialTheme.typography.headlineMedium)
        Text("Pro", style = MaterialTheme.typography.displaySmall, modifier = Modifier.highlight(LocalBrand.current.highlighter))
        Spacer(Modifier.height(12.dp))
        Text(
            "Escanear, leer el texto y guardar PDFs sin marca de agua es y será gratis. Pro añade herramientas para quien maneja muchos papeles, con un único pago.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().paper(fold = 24.dp, radius = 20.dp, elevation = 3.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Feature(Icons.Outlined.Savings, "Paquete Renta", "Todo un ejercicio en un ZIP por carpetas, con resumen de importes.")
                Feature(Icons.Outlined.CallMerge, "Unir PDFs", "Junta el DNI por las dos caras, o todas las nóminas del año.")
                Feature(Icons.Outlined.ContentCut, "Organizar páginas", "Reordena, gira, quita o separa páginas en otro documento.")
                Feature(Icons.Outlined.PictureAsPdf, "Importar PDFs con OCR", "Haz buscables los PDF que ya tienes, también desde otras apps.")
            }
        }
        Spacer(Modifier.height(24.dp))
        if (isPro) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Pro está activado en este móvil. ¡Gracias!", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(onClick = { app.billing.buy(activity) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(price?.let { "Desbloquear Pro · $it" } ?: "Desbloquear Pro")
            }
            Text(
                "Pago único con Google Play. Sin suscripción, sin cuenta y válido en tus otros móviles con la misma cuenta de Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = { app.billing.restore() }) { Text("Ya lo compré: restaurar") }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row {
        Icon(icon, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
