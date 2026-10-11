package io.github.cazadoxs.toma.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.caregiver.CaregiverAlerts
import io.github.cazadoxs.toma.ui.AppViewModel
import io.github.cazadoxs.toma.ui.components.Banner
import io.github.cazadoxs.toma.ui.components.LabelCard
import io.github.cazadoxs.toma.ui.components.SectionTitle
import io.github.cazadoxs.toma.ui.components.SwitchRow
import io.github.cazadoxs.toma.ui.components.TomaTopBar

@Composable
fun CaregiverScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    var serverText by remember { mutableStateOf(prefs.caregiverServer) }
    val serverOk = CaregiverAlerts.normalizeServer(serverText) != null

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TomaTopBar(stringResource(R.string.cg_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.cg_intro), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            SwitchRow(stringResource(R.string.cg_enable), null, prefs.caregiverEnabled) { on ->
                vm.update {
                    val topic = if (on && !CaregiverAlerts.isValidTopic(it.caregiverTopic)) CaregiverAlerts.newTopic() else it.caregiverTopic
                    it.copy(caregiverEnabled = on, caregiverTopic = topic)
                }
            }
            if (prefs.caregiverEnabled && CaregiverAlerts.isValidTopic(prefs.caregiverTopic)) {
                SectionTitle(stringResource(R.string.cg_steps))
                listOf(R.string.cg_step_1, R.string.cg_step_2, R.string.cg_step_3).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
                LabelCard {
                    Text(stringResource(R.string.cg_topic), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(prefs.caregiverTopic, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val cm = context.getSystemService(ClipboardManager::class.java)
                            cm.setPrimaryClip(ClipData.newPlainText("ntfy", prefs.caregiverTopic))
                            Toast.makeText(context, R.string.cg_copied, Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.cg_copy)) }
                    }
                    Text(stringResource(R.string.cg_topic_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.cg_qr), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Qr(CaregiverAlerts.subscribeUrl(prefs), Modifier.size(200.dp).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { vm.update { it.copy(caregiverTopic = CaregiverAlerts.newTopic()) } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cg_new_topic))
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    CaregiverAlerts.sendTest(context, prefs)
                    Toast.makeText(context, R.string.cg_test_sent, Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.cg_send_test)) }

                SectionTitle(stringResource(R.string.cg_server))
                OutlinedTextField(
                    value = serverText,
                    onValueChange = {
                        serverText = it.take(120)
                        CaregiverAlerts.normalizeServer(it)?.let { ok -> vm.update { p -> p.copy(caregiverServer = ok) } }
                    },
                    singleLine = true, isError = !serverOk, modifier = Modifier.fillMaxWidth(),
                    supportingText = { if (!serverOk) Text(stringResource(R.string.cg_server_error)) },
                )
                SwitchRow(stringResource(R.string.cg_include_meds), stringResource(R.string.cg_include_meds_sub), prefs.caregiverIncludeMeds) { on ->
                    vm.update { it.copy(caregiverIncludeMeds = on) }
                }
                Spacer(Modifier.height(8.dp))
                Banner(stringResource(R.string.cg_privacy), null, MaterialTheme.colorScheme.tertiary, null)
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun Qr(content: String, modifier: Modifier) {
    val matrix = remember(content) {
        runCatching {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1))
        }.getOrNull()
    }
    Canvas(modifier.background(Color.White)) {
        val m = matrix ?: return@Canvas
        val cell = size.width / m.width
        for (x in 0 until m.width) for (y in 0 until m.height) {
            if (m[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
