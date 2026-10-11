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

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val p = MaterialTheme.palette
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = p.ink) }
            Text("Tus datos", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Item("Qué se guarda", "El texto de cada carta, el resumen, tus notas y tus ajustes. Todo cifrado con una clave que vive en el chip de seguridad de tu móvil. Las fotos se borran en cuanto se lee el texto.")
            Item("Dónde", "Solo en este teléfono. Descifra no tiene cuentas ni servidores, y la copia de seguridad de Android no incluye estos datos.")
            Item("Lectura de la foto (OCR)", "Se hace en el móvil con ML Kit. La imagen no sale del teléfono.")
            Item("IA integrada", "Si tu móvil la tiene, el resumen lo hace Gemini Nano en el propio teléfono, a través del servicio AICore de Android, que no tiene acceso directo a internet y no guarda lo que le envías.")
            Item("IA con tu clave (Pro)", "Solo si activas esta opción y das permiso: el texto de la carta, con tus datos personales ocultos si lo dejas activado, se envía a Anthropic, OpenAI o Google según elijas, con tu clave y bajo sus condiciones. Descifra no ve ni guarda ese tráfico.")
            Item("Métricas y publicidad", "Descifra no incluye anuncios, rastreadores ni analítica. Google Play puede recoger datos técnicos de la instalación y de la compra según su propia política.")
            Item("Permisos", "Internet (solo para la IA con tu clave y Google Play), notificaciones (avisos de plazos) y huella o PIN (si activas el bloqueo). No pedimos acceso a tus fotos, contactos ni ubicación; el calendario se abre con tu app de calendario y tú confirmas el evento.")
            Item("Tus derechos", "Puedes borrar una carta, o todo, en cualquier momento desde Ajustes. Al borrar todo se destruye la clave de cifrado.")
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun Item(title: String, body: String) {
    Paper(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
