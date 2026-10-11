package com.cazadoxs.escanerlimpio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escanerlimpio.BuildConfig
import com.cazadoxs.escanerlimpio.MainActivity
import com.cazadoxs.escanerlimpio.data.Quality
import com.cazadoxs.escanerlimpio.data.ThemeMode
import com.cazadoxs.escanerlimpio.security.LockGuard
import com.cazadoxs.escanerlimpio.ui.LocalActions
import com.cazadoxs.escanerlimpio.ui.Routes
import com.cazadoxs.escanerlimpio.ui.app
import com.cazadoxs.escanerlimpio.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(activity: MainActivity) {
    val app = app()
    val actions = LocalActions.current
    val s by app.settings.state.collectAsStateWithLifecycle()
    val isPro by app.billing.isPro.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { actions.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text("Ajustes") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = 40.dp)) {
            item { SectionTitle("Privacidad") }
            item {
                ToggleRow(Icons.Outlined.Fingerprint, "Bloqueo con huella o PIN", "Pide desbloquear al abrir la app", s.appLock) { on ->
                    if (!on) {
                        app.settings.update { it.copy(appLock = false) }
                    } else if (LockGuard.canAuthenticate(activity)) {
                        LockGuard.authenticate(activity, onSuccess = { app.settings.update { it.copy(appLock = true) } }, onError = {})
                    } else {
                        actions.snackbar("Configura antes un bloqueo de pantalla o una huella en los ajustes del móvil.")
                    }
                }
            }
            item {
                ToggleRow(Icons.Outlined.VisibilityOff, "Pantalla privada", "Bloquea capturas y oculta la app en recientes", s.secureScreen) { on ->
                    app.settings.update { it.copy(secureScreen = on) }
                }
            }
            item {
                InfoRow(Icons.Outlined.WifiOff, "Sin permiso de Internet", "La app no puede enviar nada fuera del móvil. Ni cuenta, ni nube, ni anuncios, ni analítica.")
            }

            item { SectionTitle("Escaneo") }
            item { ClickRow(Icons.Outlined.HighQuality, "Calidad de imagen", s.quality.label) { dialog = "calidad" } }
            item {
                ToggleRow(Icons.Outlined.AutoAwesome, "Nombre automático", "«Factura Iberdrola · marzo 2026» en vez de «Escaneo 12»", s.autoName) { on ->
                    app.settings.update { it.copy(autoName = on) }
                }
            }

            item { SectionTitle("Apariencia") }
            item {
                ClickRow(Icons.Outlined.DarkMode, "Tema", when (s.theme) {
                    ThemeMode.SISTEMA -> "Como el sistema"
                    ThemeMode.CLARO -> "Claro"
                    ThemeMode.OSCURO -> "Oscuro"
                }) { dialog = "tema" }
            }

            item { SectionTitle("Escáner limpio Pro") }
            item {
                ClickRow(Icons.Outlined.WorkspacePremium, if (isPro) "Pro activado" else "Hazte Pro", if (isPro) "Gracias por apoyar una app sin anuncios" else "Pago único, sin suscripción") {
                    actions.nav.navigate(Routes.PRO)
                }
            }
            item { ClickRow(Icons.Outlined.Restore, "Restaurar compra", "Si ya pagaste en otro móvil") { app.billing.restore() } }

            item { SectionTitle("Acerca de") }
            item {
                InfoRow(
                    Icons.Outlined.Info,
                    "Escáner limpio ${BuildConfig.VERSION_NAME}",
                    "Escaneo y OCR con Google ML Kit en el propio móvil. PDF con PdfBox-Android (Apache 2.0). Tipografías Fraunces y Manrope (SIL OFL).",
                )
            }
            item {
                InfoRow(Icons.Outlined.Lock, "Tus datos", "Los documentos se guardan en el almacenamiento privado de la app, protegido por el cifrado de Android, y no se incluyen en copias de seguridad. Si desinstalas la app, se borran.")
            }
        }
    }

    when (dialog) {
        "calidad" -> ChoiceDialog("Calidad de imagen", Quality.entries.map { Choice(it, "${it.label} · hasta ${it.maxSide} px") }, s.quality, { dialog = null }) { q ->
            dialog = null
            app.settings.update { it.copy(quality = q) }
        }
        "tema" -> ChoiceDialog(
            "Tema",
            listOf(Choice(ThemeMode.SISTEMA, "Como el sistema"), Choice(ThemeMode.CLARO, "Claro"), Choice(ThemeMode.OSCURO, "Oscuro")),
            s.theme,
            { dialog = null },
        ) { t ->
            dialog = null
            app.settings.update { it.copy(theme = t) }
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
