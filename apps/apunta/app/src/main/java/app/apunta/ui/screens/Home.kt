package app.apunta.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.apunta.R
import app.apunta.billing.QuotaRules
import app.apunta.data.Recording
import app.apunta.data.Source
import app.apunta.data.Status
import app.apunta.ui.AppViewModel
import app.apunta.ui.components.Chip
import app.apunta.ui.theme.Apunta
import app.apunta.ui.theme.TimestampStyle
import app.apunta.util.TimeFormat
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    snackbar: SnackbarHostState,
    onOpen: (Long) -> Unit,
    onRecord: () -> Unit,
    onImport: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onPaywall: () -> Unit,
) {
    val list by vm.recordings.collectAsStateWithLifecycle()
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onRecord,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Mic, null) },
                text = { Text(stringResource(R.string.record), style = MaterialTheme.typography.labelLarge) },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
                        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium, color = Apunta.colors.inkSoft)
                    }
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                }
            }
            item {
                if (!isPro) QuotaCard(quota.usedFraction, quota.remainingMs, onPaywall)
            }
            item {
                TextButton(onClick = onImport, enabled = !importing) {
                    Icon(Icons.Filled.AudioFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (importing) R.string.importing else R.string.import_audio))
                }
            }
            val items = list
            if (items != null && items.isEmpty()) item { EmptyState() }
            items?.let { rows ->
                items(rows, key = { it.id }) { r ->
                    RecordingCard(r, active = r.id == current, progress = if (r.id == current) progress else r.progress, onClick = { onOpen(r.id) }, onStar = { vm.star(r.id, !r.starred) })
                }
            }
        }
    }
}

@Composable
private fun QuotaCard(used: Float, remainingMs: Long, onPaywall: () -> Unit) {
    Card(
        onClick = onPaywall,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.quota_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.quota_left, TimeFormat.human(remainingMs)), style = TimestampStyle, color = Apunta.colors.inkSoft)
            }
            LinearProgressIndicator(
                progress = { used }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = if (used > 0.9f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(stringResource(R.string.quota_hint), style = MaterialTheme.typography.bodySmall, color = Apunta.colors.inkSoft)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Mic, null, Modifier.size(40.dp), tint = Apunta.colors.margin)
        }
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyMedium, color = Apunta.colors.inkSoft, modifier = Modifier.padding(horizontal = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun RecordingCard(r: Recording, active: Boolean, progress: Float, onClick: () -> Unit, onStar: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Apunta.colors.card),
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.createdAt)),
                        style = MaterialTheme.typography.bodySmall, color = Apunta.colors.inkSoft,
                    )
                }
                IconButton(onClick = onStar, Modifier.size(36.dp)) {
                    Icon(if (r.starred) Icons.Filled.Star else Icons.Outlined.StarBorder, stringResource(R.string.star), tint = if (r.starred) Apunta.colors.margin else Apunta.colors.inkSoft)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r.durationMs > 0) Text(TimeFormat.clock(r.durationMs), style = TimestampStyle, color = Apunta.colors.inkSoft)
                Chip(stringResource(if (r.source == Source.IMPORTED) R.string.src_import else R.string.src_rec))
                StatusChip(r.status)
            }
            if (Status.isBusy(r.status)) {
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun StatusChip(status: Int) {
    val (label, container) = when (status) {
        Status.DONE -> R.string.st_done to MaterialTheme.colorScheme.secondaryContainer
        Status.QUEUED -> R.string.st_queued to MaterialTheme.colorScheme.surfaceContainerHigh
        Status.RUNNING -> R.string.st_running to MaterialTheme.colorScheme.primaryContainer
        Status.FAILED -> R.string.st_failed to MaterialTheme.colorScheme.errorContainer
        Status.NEEDS_PRO -> R.string.st_limit to MaterialTheme.colorScheme.tertiaryContainer
        Status.CANCELLED -> R.string.st_cancelled to MaterialTheme.colorScheme.surfaceContainerHigh
        Status.IMPORTING -> R.string.st_running to MaterialTheme.colorScheme.primaryContainer
        else -> R.string.st_new to MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Chip(stringResource(label), container = container, content = MaterialTheme.colorScheme.onSurface)
}
