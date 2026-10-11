package io.github.cazadoxs.toma.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.backup.BackupManager
import io.github.cazadoxs.toma.core.AdherenceStats
import io.github.cazadoxs.toma.report.PdfReport
import io.github.cazadoxs.toma.settings.Prefs
import io.github.cazadoxs.toma.ui.AppViewModel
import io.github.cazadoxs.toma.ui.components.LabelCard
import io.github.cazadoxs.toma.ui.components.SectionTitle
import io.github.cazadoxs.toma.ui.components.SettingRow
import io.github.cazadoxs.toma.ui.components.TomaTopBar
import io.github.cazadoxs.toma.ui.theme.LocalTomaColors
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by vm.prefs.collectAsState()
    var days by remember { mutableStateOf(30) }
    var stats by remember { mutableStateOf<AdherenceStats?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val doses by vm.today.collectAsState() // refresh trigger: the stats follow data changes

    LaunchedEffect(days, prefs.activeProfileId, doses.doses) { stats = vm.adherence(days) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) scope.launch { runCatching { BackupManager.exportCsv(context, uri) }.onFailure { error = true } }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TomaTopBar(stringResource(R.string.history_title)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { d ->
                    FilterChip(selected = days == d, onClick = { days = d }, label = { Text(stringResource(R.string.history_days, d)) })
                }
            }
            Spacer(Modifier.height(16.dp))
            val s = stats
            LabelCard {
                Text(stringResource(R.string.history_adherence), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (s?.percent == null) {
                    Text(stringResource(R.string.history_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Ring(s.percent!! / 100f, "${s.percent} %")
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Legend(LocalTomaColors.current.taken, stringResource(R.string.history_taken), s.taken)
                            Legend(LocalTomaColors.current.skipped, stringResource(R.string.history_skipped), s.skipped)
                            Legend(LocalTomaColors.current.missed, stringResource(R.string.history_missed), s.missed)
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.history_export_pdf))
            LabelCard {
                SettingRow(
                    title = stringResource(R.string.history_share_pdf), subtitle = stringResource(R.string.history_period_days, days),
                    icon = Icons.Rounded.Description,
                    onClick = {
                        if (busy) return@SettingRow
                        busy = true; error = false
                        scope.launch {
                            runCatching {
                                val file = PdfReport.create(context, prefs.activeProfileId.takeIf { it != Prefs.ALL_PROFILES }, days)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure { error = true }
                            busy = false
                        }
                    },
                )
                SettingRow(
                    title = stringResource(R.string.history_export_csv), icon = Icons.Rounded.TableChart,
                    onClick = { csvLauncher.launch("toma-historial.csv") },
                )
            }
            if (busy) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("  " + stringResource(R.string.history_generating))
            }
            if (error) Text(stringResource(R.string.history_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).androidxBackground(color))
        Text("  $label: $value", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Modifier.androidxBackground(color: androidx.compose.ui.graphics.Color) =
    this.then(Modifier.background(color, androidx.compose.foundation.shape.CircleShape))

@Composable
private fun Ring(fraction: Float, label: String) {
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val color = LocalTomaColors.current.taken
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}
