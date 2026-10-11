package com.cazadoxs.filtro400.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cazadoxs.filtro400.ui.MainActivity
import com.cazadoxs.filtro400.ui.components.Gap
import com.cazadoxs.filtro400.ui.components.HGap
import com.cazadoxs.filtro400.ui.components.IconBadge
import com.cazadoxs.filtro400.ui.components.ScrollScreen
import com.cazadoxs.filtro400.ui.components.ShieldHero
import com.cazadoxs.filtro400.ui.theme.LocalStatus

@Composable
fun OnboardingScreen(host: MainActivity, onDone: () -> Unit, onPrivacy: () -> Unit) {
    val roleHeld by host.roleHeld
    val roleAvailable by host.roleAvailable
    val status = LocalStatus.current

    ScrollScreen {
        Gap(24.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ShieldHero(active = true, size = 180.dp)
            Gap(8.dp)
            Text(
                "Que llame quien tú quieras",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Gap(8.dp)
            Text(
                "Desde el 17 de octubre de 2026 las empresas solo pueden llamarte para venderte algo desde números que empiezan por 400. Filtro 400 los corta antes de que suene el teléfono.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Gap(24.dp)
        Promise(Icons.Rounded.Block, status.blocked, "El 400, fuera de un toque", "Y también 806, 807, 905 y los prefijos que tú elijas.")
        Promise(Icons.Rounded.ContactPhone, status.allowed, "Nunca subimos tu agenda", "Ni siquiera pedimos permiso de contactos: tus contactos siempre suenan.")
        Promise(Icons.Rounded.Schedule, status.silenced, "Llamadas esperadas", "¿Esperas al repartidor? Deja pasar números desconocidos durante unas horas.")
        Promise(Icons.Rounded.MoneyOff, MaterialTheme.colorScheme.onSurfaceVariant, "Gratis y sin anuncios", "Todo el filtrado local es gratis, para siempre y sin límites.")
        Gap(24.dp)

        if (!roleAvailable) {
            Text(
                "Este teléfono no permite apps de filtrado de llamadas. Puedes seguir usando la app para consultar números y aprender a pedir la desconexión del 400 a tu operador.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Gap()
        }

        if (roleHeld) {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Todo listo · Empezar", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(
                onClick = { host.requestScreeningRole() },
                enabled = roleAvailable,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = status.blocked, contentColor = Color(0xFF0E1015)),
            ) {
                Text("Activar Filtro 400", style = MaterialTheme.typography.titleMedium)
            }
            Gap(6.dp)
            Text(
                "Android te preguntará si Filtro 400 puede ser tu app de identificación y spam. Es el único permiso necesario.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Ahora no") }
        }
        TextButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text("Cómo tratamos tus datos") }
    }
}

@Composable
private fun Promise(icon: ImageVector, color: Color, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.Start) {
        IconBadge(color) { Icon(icon, contentDescription = null, tint = color) }
        HGap(14.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
