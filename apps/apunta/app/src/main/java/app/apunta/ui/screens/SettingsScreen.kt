package app.apunta.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.apunta.BuildConfig
import app.apunta.R
import app.apunta.settings.AiProvider
import app.apunta.settings.Performance
import app.apunta.settings.Settings
import app.apunta.summary.AiSummarizer
import app.apunta.ui.AppViewModel
import app.apunta.ui.theme.Apunta

const val PRIVACY_URL = "https://cazadoxs.github.io/programar/apunta/privacidad.html"

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onPaywall: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val keySet by vm.aiKeySet.collectAsStateWithLifecycle()
    var langMenu by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }
    var keyDialog by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
        }
        Section(R.string.set_transcription)
        Box2 {
            OutlinedButton({ langMenu = true }) { Text(Settings.languageName(s.language)) }
            DropdownMenu(langMenu, { langMenu = false }) {
                Settings.LANGUAGES.forEach { (c, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { vm.update { it.copy(language = c) }; langMenu = false }) }
            }
        }
        SwitchRow(R.string.set_auto, s.autoTranscribe) { v -> vm.update { it.copy(autoTranscribe = v) } }
        Text(stringResource(R.string.set_performance), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Performance.entries.forEach { p ->
                FilterChip(s.performance == p, { vm.update { it.copy(performance = p) } }, label = {
                    Text(stringResource(when (p) { Performance.SAVER -> R.string.perf_saver; Performance.BALANCED -> R.string.perf_balanced; Performance.FAST -> R.string.perf_fast }))
                })
            }
        }
        Text(stringResource(R.string.set_models), style = MaterialTheme.typography.titleSmall)
        ModelPicker(vm, onPaywall)
        HorizontalDivider()

        Section(R.string.set_recording)
        SwitchRow(R.string.set_consent, s.consentReminder) { v -> vm.update { it.copy(consentReminder = v) } }
        SwitchRow(R.string.set_hq, s.highQualityAudio) { v -> vm.update { it.copy(highQualityAudio = v) } }
        HorizontalDivider()

        Section(R.string.set_ai)
        Text(stringResource(R.string.set_ai_desc), style = MaterialTheme.typography.bodySmall, color = Apunta.colors.inkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach { p ->
                FilterChip(s.aiProvider == p, { vm.update { it.copy(aiProvider = p, aiModel = "") } }, label = { Text(if (p == AiProvider.NONE) stringResource(R.string.ai_off) else p.label.substringBefore(' ')) })
            }
        }
        if (s.aiProvider != AiProvider.NONE) {
            OutlinedButton({ keyDialog = true }) { Text(stringResource(if (keySet) R.string.ai_key_change else R.string.ai_key_add)) }
            var model by remember(s.aiProvider) { mutableStateOf(s.aiModel.ifBlank { s.aiProvider.defaultModel }) }
            OutlinedTextField(model, {
                model = it.take(64)
                if (AiSummarizer.isSafeModelId(model)) vm.update { st -> st.copy(aiModel = model) }
            }, label = { Text(stringResource(R.string.ai_model)) }, singleLine = true, isError = !AiSummarizer.isSafeModelId(model), modifier = Modifier.fillMaxWidth())
        }
        HorizontalDivider()

        Section(R.string.set_look)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark).forEachIndexed { i, r ->
                FilterChip(s.theme == i, { vm.update { it.copy(theme = i) } }, label = { Text(stringResource(r)) })
            }
        }
        HorizontalDivider()

        Section(R.string.set_pro)
        OutlinedButton(onPaywall) { Text(stringResource(if (isPro) R.string.pro_thanks else R.string.see_pro)) }
        HorizontalDivider()

        Section(R.string.set_privacy)
        Text(stringResource(R.string.privacy_summary), style = MaterialTheme.typography.bodyMedium)
        TextButton({ uri.openUri(PRIVACY_URL) }) { Text(stringResource(R.string.privacy_policy)) }
        OutlinedButton({ wipe = true }) { Text(stringResource(R.string.wipe)) }
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = Apunta.colors.inkSoft)
        Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.bodySmall, color = Apunta.colors.inkSoft)
    }

    if (keyDialog) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { keyDialog = false },
            title = { Text(stringResource(R.string.ai_key_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ai_key_body), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(key, { key = it.take(300) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
                }
            },
            confirmButton = { TextButton({ vm.saveAiKey(key); keyDialog = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { Row { if (keySet) TextButton({ vm.saveAiKey(""); keyDialog = false }) { Text(stringResource(R.string.delete)) }; TextButton({ keyDialog = false }) { Text(stringResource(R.string.cancel)) } } },
        )
    }
    if (wipe) AlertDialog(
        onDismissRequest = { wipe = false },
        title = { Text(stringResource(R.string.wipe)) }, text = { Text(stringResource(R.string.wipe_body)) },
        confirmButton = { TextButton({ wipe = false; vm.wipeAll() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton({ wipe = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable private fun Section(title: Int) = Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

@Composable private fun Box2(content: @Composable () -> Unit) = androidx.compose.foundation.layout.Box { content() }

@Composable
private fun SwitchRow(label: Int, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}
