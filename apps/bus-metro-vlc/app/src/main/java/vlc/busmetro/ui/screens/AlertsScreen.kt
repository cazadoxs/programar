package vlc.busmetro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vlc.busmetro.R
import vlc.busmetro.ui.AlertsViewModel
import vlc.busmetro.ui.components.*
import vlc.busmetro.ui.theme.Palette
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(vm: AlertsViewModel) {
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refresh(); vm.markSeen() }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    PullToRefreshBox(loading, { vm.refresh() }, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.nav_alerts), style = MaterialTheme.typography.headlineMedium) }
            if (alerts.isEmpty()) item { EmptyState(Icons.Filled.CheckCircle, stringResource(R.string.alerts_empty), stringResource(R.string.alerts_empty_body)) }
            items(alerts, key = { it.id }) { a ->
                Card2 {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Palette.Warn, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(a.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                    if (a.body.isNotBlank() && a.body != a.title) Text(a.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Text(fmt.format(Date(a.start ?: a.fetchedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    a.url?.takeIf { it.startsWith("https://") }?.let { u ->
                        TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }) { Text(stringResource(R.string.more_info)) }
                    }
                }
            }
            item { Text(stringResource(R.string.alerts_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
