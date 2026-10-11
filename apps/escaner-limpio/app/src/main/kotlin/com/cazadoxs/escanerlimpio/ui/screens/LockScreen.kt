package com.cazadoxs.escanerlimpio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cazadoxs.escanerlimpio.MainActivity
import com.cazadoxs.escanerlimpio.security.LockGuard
import com.cazadoxs.escanerlimpio.ui.components.paper
import com.cazadoxs.escanerlimpio.ui.theme.LocalBrand

@Composable
fun LockScreen(activity: MainActivity, onUnlocked: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val prompt = { LockGuard.authenticate(activity, onSuccess = onUnlocked, onError = { error = it }) }
    LaunchedEffect(Unit) { prompt() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(width = 96.dp, height = 124.dp).paper(fold = 22.dp, radius = 12.dp, elevation = 8.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Lock, null, tint = LocalBrand.current.ink, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("Documentos protegidos", style = MaterialTheme.typography.headlineSmall)
            Text(
                error ?: "Desbloquea con tu huella, cara o PIN del móvil.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { prompt() }) { Text("Desbloquear") }
        }
    }
}
