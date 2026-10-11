package io.github.cazadoxs.toma.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.billing.BillingManager
import io.github.cazadoxs.toma.container
import io.github.cazadoxs.toma.ui.AppViewModel
import io.github.cazadoxs.toma.ui.MainActivity
import io.github.cazadoxs.toma.ui.components.LabelCard
import io.github.cazadoxs.toma.ui.components.TomaTopBar

@Composable
fun PlusScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val billing = context.container.billing
    val prefs by vm.prefs.collectAsState()
    val price by billing.price.collectAsState()
    val status by billing.status.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TomaTopBar(stringResource(R.string.plus_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.plus_tagline), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            LabelCard(accent = MaterialTheme.colorScheme.primary) {
                Text(stringResource(R.string.plus_free_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.plus_free_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            LabelCard(accent = MaterialTheme.colorScheme.secondary) {
                listOf(R.string.plus_feat_themes, R.string.plus_feat_widget, R.string.plus_feat_backup, R.string.plus_feat_support).forEach {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                        Text("  " + stringResource(it), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            when {
                prefs.plusOwned -> Text(stringResource(R.string.plus_owned), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                status == BillingManager.Status.PENDING -> Text(stringResource(R.string.plus_pending), style = MaterialTheme.typography.bodyLarge)
                else -> {
                    Button(
                        onClick = { (context as? MainActivity)?.let { billing.buy(it) } ?: Unit },
                        enabled = status == BillingManager.Status.READY && price != null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(price?.let { stringResource(R.string.plus_buy, it) } ?: stringResource(R.string.plus_buy_generic))
                    }
                    if (status == BillingManager.Status.UNAVAILABLE || status == BillingManager.Status.ERROR) {
                        Text(stringResource(R.string.plus_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Text(stringResource(R.string.plus_restore_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(48.dp))
        }
    }
}
