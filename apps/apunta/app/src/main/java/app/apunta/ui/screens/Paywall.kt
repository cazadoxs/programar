package app.apunta.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.apunta.R
import app.apunta.ui.AppViewModel
import app.apunta.ui.theme.Apunta

@Composable
fun PaywallScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val price by vm.price.collectAsStateWithLifecycle()
    val billing = (ctx.applicationContext as app.apunta.ApuntaApp).billing
    val event by billing.events.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.pro_subtitle), style = MaterialTheme.typography.bodyLarge, color = Apunta.colors.inkSoft)
        Spacer(Modifier.height(8.dp))
        listOf(R.string.pro_f1, R.string.pro_f2, R.string.pro_f3, R.string.pro_f4, R.string.pro_f5).forEach {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.secondary)
                Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.pro_free_note), style = MaterialTheme.typography.bodySmall, color = Apunta.colors.inkSoft)
        if (isPro) {
            Text(stringResource(R.string.pro_thanks), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        } else {
            Button(
                onClick = { (ctx as? Activity)?.let { billing.launchPurchase(it) } },
                enabled = price != null, modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(if (price != null) stringResource(R.string.pro_buy, price!!) else stringResource(R.string.pro_loading), style = MaterialTheme.typography.labelLarge) }
            TextButton({ billing.refresh() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.pro_restore)) }
            when (event) {
                is app.apunta.billing.BillingManager.PurchaseEvent.Pending -> Text(stringResource(R.string.pro_pending), color = Apunta.colors.inkSoft)
                is app.apunta.billing.BillingManager.PurchaseEvent.Error -> Text(stringResource(R.string.pro_error), color = MaterialTheme.colorScheme.error)
                else -> {}
            }
        }
        Text(stringResource(R.string.pro_onetime), style = MaterialTheme.typography.bodySmall, color = Apunta.colors.inkSoft)
    }
}
