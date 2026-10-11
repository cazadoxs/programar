package app.apunta.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.apunta.R
import app.apunta.record.RecorderService
import app.apunta.ui.AppViewModel
import app.apunta.ui.components.Waveform
import app.apunta.ui.theme.Apunta
import app.apunta.ui.theme.Fraunces
import app.apunta.util.TimeFormat

@Composable
fun RecordScreen(vm: AppViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val ctx = LocalContext.current
    val st by RecorderService.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) RecorderService.send(ctx, RecorderService.ACTION_START)
    }
    fun begin() {
        val perms = buildList { add(Manifest.permission.RECORD_AUDIO); if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }
        permission.launch(perms.toTypedArray())
    }

    LaunchedEffect(st.lastSavedId) {
        st.lastSavedId?.let { RecorderService.clearResult(); onSaved(it) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, enabled = !st.active) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            }
            Spacer(Modifier.weight(1f))

            if (settings.consentReminder && !st.active) {
                Column(
                    Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.tertiaryContainer).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(stringResource(R.string.consent_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(Modifier.height(32.dp))
            }

            Text(
                TimeFormat.clock(st.elapsedMs),
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = Fraunces, fontSize = 72.sp),
                color = if (st.active && !st.paused) Apunta.colors.margin else Apunta.colors.ink,
            )
            Text(
                stringResource(
                    when {
                        st.error != null -> R.string.rec_error
                        !st.active -> R.string.rec_ready
                        st.paused -> R.string.rec_paused
                        else -> R.string.rec_recording
                    }
                ),
                style = MaterialTheme.typography.bodyMedium, color = Apunta.colors.inkSoft, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Waveform(st.levels, color = if (st.paused) Apunta.colors.inkSoft else MaterialTheme.colorScheme.primary)
            if (st.bookmarks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.bookmarks_count, st.bookmarks.size), style = MaterialTheme.typography.labelMedium, color = Apunta.colors.inkSoft)
            }
            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Filled.Bookmark, stringResource(R.string.bookmark), size = 56, enabled = st.active) { RecorderService.send(ctx, RecorderService.ACTION_BOOKMARK) }
                if (!st.active) {
                    RoundButton(Icons.Filled.Mic, stringResource(R.string.record), size = 88, filled = true) { begin() }
                } else {
                    RoundButton(Icons.Filled.Stop, stringResource(R.string.rec_stop), size = 88, filled = true) { RecorderService.send(ctx, RecorderService.ACTION_STOP) }
                }
                RoundButton(if (st.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, stringResource(R.string.rec_pause), size = 56, enabled = st.active) { RecorderService.send(ctx, RecorderService.ACTION_PAUSE) }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.rec_private), style = MaterialTheme.typography.labelMedium, color = Apunta.colors.inkSoft, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, size: Int, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (enabled) bg else bg.copy(alpha = 0.4f)).clickable(enabled = enabled, onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size((size * 0.4f).dp), tint = fg) }
}
