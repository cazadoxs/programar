package com.cazadoxs.descifra.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cazadoxs.descifra.core.ai.CloudProvider
import com.cazadoxs.descifra.data.AnalysisMode
import com.cazadoxs.descifra.data.Settings
import com.cazadoxs.descifra.ml.NanoState

class SettingsActions(
    val onBack: () -> Unit,
    val update: ((Settings) -> Settings) -> Unit,
    val saveKey: (CloudProvider, String?) -> Unit,
    val openUrl: (String) -> Unit,
    val downloadNano: () -> Unit,
    val onPaywall: () -> Unit,
    val onPrivacy: () -> Unit,
    val onErase: () -> Unit,
    val onNotificationsPermission: () -> Unit,
    val onLockToggled: (Boolean) -> Unit,
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(s: Settings, isPro: Boolean, nano: NanoState?, nanoDownloading: Boolean, canNotify: Boolean, version: String, a: SettingsActions) {
    val p = MaterialTheme.palette
    var confirmErase by remember { mutableStateOf(false) }
    var keyInput by remember(s.provider) { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = a.onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = p.ink) }
            Text("Ajustes", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Block("Cómo se analizan las cartas") {
                AnalysisMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { a.update { it.copy(mode = m) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.mode == m, onClick = { a.update { it.copy(mode = m) } })
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(m.label, style = MaterialTheme.typography.titleSmall)
                            Text(m.help, color = p.inkSoft, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                when (nano) {
                    NanoState.AVAILABLE -> Chip("IA integrada lista en este móvil", p.green)
                    NanoState.DOWNLOADABLE -> {
                        Chip("Tu móvil admite IA integrada", p.blue)
                        BigButton(if (nanoDownloading) "Descargando…" else "Descargar el modelo (una vez)", a.downloadNano, Modifier.fillMaxWidth().padding(top = 8.dp), filled = false, enabled = !nanoDownloading)
                    }
                    NanoState.DOWNLOADING -> Chip("Descargando el modelo de IA…", p.amber)
                    NanoState.UNAVAILABLE -> Text(
                        "Este móvil no admite la IA integrada (hace falta, por ejemplo, un Pixel 9 o 10, Galaxy S25 u otro compatible). Descifra funciona igual con las reglas y, con Pro, con tu clave.",
                        color = p.inkSoft,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    null -> Unit
                }
            }

            Block("IA con tu propia clave" + if (isPro) "" else " (Pro)") {
                if (!isPro) {
                    Text("Usa Claude, OpenAI o Gemini con tu cuenta para resúmenes más finos y para preguntar sobre la carta.", color = p.inkSoft, style = MaterialTheme.typography.bodyMedium)
                    BigButton("Ver Descifra Pro", a.onPaywall, Modifier.fillMaxWidth().padding(top = 8.dp), color = p.stamp, onColor = p.onStamp)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CloudProvider.entries.forEach { pr ->
                            val selected = s.provider == pr
                            Box2(selected, pr.label) { a.update { it.copy(provider = pr, model = "") } }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.provider.models.forEach { m ->
                            val selected = (s.model.ifBlank { s.provider.defaultModel }) == m
                            Box2(selected, m) { a.update { it.copy(model = m) } }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (s.hasKey) {
                        Chip("Clave guardada, cifrada en este móvil", p.green)
                        TextButton(onClick = { a.saveKey(s.provider, null) }) { Text("Quitar la clave", color = p.stamp) }
                    } else {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Clave de ${s.provider.label}") },
                            placeholder = { Text(s.provider.keyHint) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            BigButton("Guardar clave", { a.saveKey(s.provider, keyInput); keyInput = "" }, Modifier.weight(1f), enabled = keyInput.length >= 20)
                            BigButton("Conseguir una", { a.openUrl(s.provider.consoleUrl) }, Modifier.weight(1f), filled = false)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        "Permitir enviar el texto a ${s.provider.label}",
                        "Solo se envía el texto de la carta cuando tú pides el análisis. Nunca las fotos.",
                        s.cloudConsent,
                    ) { v -> a.update { it.copy(cloudConsent = v) } }
                    SwitchRow(
                        "Ocultar datos personales antes de enviar",
                        "Sustituye NIF, IBAN, teléfonos, emails, nombres y direcciones por marcas; vuelven a ponerse en la respuesta.",
                        s.redact,
                    ) { v -> a.update { it.copy(redact = v) } }
                }
            }

            Block("Avisos de plazos") {
                Text("Avisarme antes de que venza:", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(14, 7, 3, 1).forEach { d ->
                        Box2(d in s.reminderDaysBefore, if (d == 1) "1 día antes" else "$d días antes") {
                            a.update { st -> st.copy(reminderDaysBefore = if (d in st.reminderDaysBefore) st.reminderDaysBefore - d else st.reminderDaysBefore + d) }
                        }
                    }
                }
                Text("Y el mismo día del plazo. A las:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(8, 9, 12, 18).forEach { h -> Box2(s.reminderHour == h, "$h:00") { a.update { it.copy(reminderHour = h) } } }
                }
                if (!canNotify) {
                    BigButton("Permitir notificaciones", a.onNotificationsPermission, Modifier.fillMaxWidth().padding(top = 10.dp), filled = false)
                }
                Text("Los avisos no muestran el contenido de la carta en la pantalla de bloqueo.", color = p.inkSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }

            Block("Privacidad") {
                SwitchRow("Pedir huella o PIN al abrir", "Protege tus cartas si alguien coge tu móvil desbloqueado.", s.lockWithBiometrics) { a.onLockToggled(it) }
                SwitchRow("Permitir capturas de pantalla", "Por defecto están bloqueadas y la vista previa de apps recientes se oculta.", s.allowScreenshots) { v -> a.update { it.copy(allowScreenshots = v) } }
                TextButton(onClick = a.onPrivacy) { Text("Qué datos se guardan y adónde van", color = p.blue) }
                TextButton(onClick = { confirmErase = true }) { Text("Borrar todo lo de este móvil", color = p.stamp) }
            }

            Block("Acerca de") {
                Text("Descifra $version", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Descifra explica cartas con ayuda de reglas e inteligencia artificial. Puede equivocarse y no es asesoramiento legal, fiscal ni financiero. Comprueba siempre fechas e importes en la carta original.",
                    color = p.inkSoft,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text("Tipografías Fraunces y Atkinson Hyperlegible, con licencia SIL Open Font.", color = p.inkSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("¿Borrar todo?") },
            text = { Text("Se eliminan las cartas, las notas, las claves de IA y los ajustes, y se destruye la clave de cifrado. No se puede deshacer. La compra de Pro no se pierde: está en tu cuenta de Google Play.") },
            confirmButton = { TextButton(onClick = { confirmErase = false; a.onErase() }) { Text("Borrar todo", color = p.stamp) } },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Column {
        Eyebrow(title, Modifier.padding(bottom = 8.dp))
        Paper(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SwitchRow(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = MaterialTheme.palette
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(help, color = p.inkSoft, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.green, checkedThumbColor = p.sheet, uncheckedTrackColor = p.line, uncheckedThumbColor = p.sheet, uncheckedBorderColor = p.line),
        )
    }
}

@Composable
private fun Box2(selected: Boolean, text: String, onClick: () -> Unit) {
    val p = MaterialTheme.palette
    androidx.compose.foundation.layout.Box(
        Modifier
            .heightIn(min = 44.dp)
            .androidxClip()
            .let { if (selected) it.androidxBg(p.ink) else it.androidxBorder(p.line) }
            .androidxClickable(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) p.paper else p.ink, style = MaterialTheme.typography.labelMedium) }
}
