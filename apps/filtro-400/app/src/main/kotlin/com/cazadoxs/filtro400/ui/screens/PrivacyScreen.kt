package com.cazadoxs.filtro400.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.cazadoxs.filtro400.ui.components.Gap
import com.cazadoxs.filtro400.ui.components.Panel
import com.cazadoxs.filtro400.ui.components.ScreenHeader
import com.cazadoxs.filtro400.ui.components.ScrollScreen
import com.cazadoxs.filtro400.ui.components.SectionLabel

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ScrollScreen {
        ScreenHeader("Privacidad", "Lo corto: tus llamadas y tu agenda se quedan en tu teléfono.", onBack = onBack)
        Item(
            "Lo que NO hacemos",
            "No pedimos permiso de contactos, ni de registro de llamadas, ni de estado del teléfono. No subimos tu agenda. No sabemos tu número. No hay anuncios ni rastreadores de terceros.",
        )
        Item(
            "Cómo filtra",
            "Android avisa a Filtro 400 antes de que suene una llamada de un número que no tienes guardado. La decisión se toma en tu teléfono con tus reglas. Tus contactos nunca pasan por el filtro.",
        )
        Item(
            "Tu historial",
            "Las llamadas filtradas se guardan solo en tu teléfono y se borran solas pasado el plazo que elijas. No se incluyen en copias de seguridad en la nube.",
        )
        Item(
            "Si denuncias un número",
            "Se envía el número que te llamó, la categoría que elijas y un identificador aleatorio de la app (para no contar dos veces la misma denuncia). Nunca tu número, tu nombre ni tus contactos. Un número solo entra en la lista si lo denuncian varias personas distintas.",
        )
        Item(
            "Si eres Premium",
            "La app descarga la lista completa y la consulta en local: el servidor nunca sabe quién te llama. Para comprobar tu suscripción se envía el token de compra de Google Play, que no contiene tu nombre ni tu correo.",
        )
        Gap(8.dp)
    }
}

@Composable
private fun Item(title: String, body: String) {
    SectionLabel(title)
    Panel { Text(body, style = MaterialTheme.typography.bodyMedium) }
}
