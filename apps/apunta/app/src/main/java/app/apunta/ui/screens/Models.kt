package app.apunta.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.apunta.R
import app.apunta.models.ModelCatalog
import app.apunta.models.ModelState
import app.apunta.ui.AppViewModel
import app.apunta.ui.components.Bars
import app.apunta.ui.components.Chip
import app.apunta.ui.theme.Apunta

/** Model list used by onboarding and settings. */
@Composable
fun ModelPicker(vm: AppViewModel, onPaywall: () -> Unit) {
    val states by vm.models.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val ram = vm.totalRamGb()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importModel(it) } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModelCatalog.all.forEach { m ->
            val st = states[m.id] ?: ModelState.Missing
            val locked = m.pro && !isPro
            Card(
                colors = CardDefaults.cardColors(containerColor = Apunta.colors.card),
                border = BorderStroke(1.dp, if (settings.modelId == m.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(settings.modelId == m.id, enabled = st is ModelState.Ready, onClick = { vm.update { it.copy(modelId = m.id) } })
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(modelName(m.id)), style = MaterialTheme.typography.titleMedium)
                            Text("${m.sizeBytes / 1_000_000} MB", style = MaterialTheme.typography.bodySmall, color = Apunta.colors.inkSoft)
                        }
                        if (m.pro) Chip("PRO", container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column { Text(stringResource(R.string.speed), style = MaterialTheme.typography.labelSmall); Bars(m.speed) }
                        Column { Text(stringResource(R.string.accuracy), style = MaterialTheme.typography.labelSmall); Bars(m.accuracy) }
                    }
                    if (ram < m.minRamGb) Text(stringResource(R.string.model_ram_warn, m.minRamGb), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    when (st) {
                        is ModelState.Downloading -> {
                            LinearProgressIndicator(progress = { st.progress }, Modifier.fillMaxWidth())
                            TextButton({ vm.cancelModel(m.id) }) { Text(stringResource(R.string.cancel)) }
                        }
                        ModelState.Verifying -> Text(stringResource(R.string.model_verifying), style = MaterialTheme.typography.bodySmall)
                        is ModelState.Ready -> TextButton({ vm.deleteModel(m.id) }) { Text(stringResource(R.string.model_delete)) }
                        is ModelState.Failed -> Column {
                            Text(stringResource(when (st.reason) {
                                ModelState.Reason.CHECKSUM -> R.string.model_err_checksum
                                ModelState.Reason.STORAGE -> R.string.model_err_storage
                                else -> R.string.model_err_network
                            }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Button({ if (locked) onPaywall() else vm.downloadModel(m.id) }) { Text(stringResource(R.string.model_retry)) }
                        }
                        ModelState.Missing -> Button({ if (locked) onPaywall() else vm.downloadModel(m.id) }) {
                            Text(stringResource(if (locked) R.string.see_pro else R.string.model_download))
                        }
                    }
                }
            }
        }
        OutlinedButton({ picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.model_import)) }
        Text(stringResource(R.string.model_note), style = MaterialTheme.typography.bodySmall, color = Apunta.colors.inkSoft)
    }
}

fun modelName(id: String) = when (id) {
    "tiny" -> R.string.model_tiny
    "base" -> R.string.model_base
    "small" -> R.string.model_small
    "turbo" -> R.string.model_turbo
    else -> R.string.model_custom
}
