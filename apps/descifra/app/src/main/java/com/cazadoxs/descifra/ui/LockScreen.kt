package com.cazadoxs.descifra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val p = MaterialTheme.palette
    Column(
        Modifier.fillMaxSize().background(p.paper).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Stamp("PRIVADO", p.stamp)
        Spacer(Modifier.height(24.dp))
        Text("Descifra está bloqueada", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Tus cartas son personales. Desbloquea con tu huella, tu cara o el PIN del móvil.", color = p.inkSoft, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        BigButton("Desbloquear", onUnlock)
    }
}
