package io.github.cazadoxs.toma.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.ui.Permissions
import io.github.cazadoxs.toma.ui.components.LabelCard
import io.github.cazadoxs.toma.ui.components.TomaTopBar
import java.util.Locale

@Composable
fun BatteryGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val maker = Build.MANUFACTURER.lowercase(Locale.ROOT)
    val specific = when {
        maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") -> R.string.bat_xiaomi
        maker.contains("samsung") -> R.string.bat_samsung
        maker.contains("huawei") || maker.contains("honor") -> R.string.bat_huawei
        maker.contains("oppo") || maker.contains("realme") || maker.contains("oneplus") || maker.contains("vivo") -> R.string.bat_oppo
        else -> null
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TomaTopBar(stringResource(R.string.bat_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.bat_intro), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { runCatching { context.startActivity(Permissions.batterySettings()) } }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.bat_open_battery))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { context.startActivity(Permissions.appSettings(context)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.bat_open_app))
            }
            Spacer(Modifier.height(16.dp))
            LabelCard {
                Text(stringResource(R.string.bat_generic), style = MaterialTheme.typography.bodyMedium)
            }
            specific?.let {
                Spacer(Modifier.height(12.dp))
                LabelCard(accent = MaterialTheme.colorScheme.secondary) { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }
            Spacer(Modifier.height(12.dp))
            listOf(R.string.bat_xiaomi, R.string.bat_samsung, R.string.bat_huawei, R.string.bat_oppo).filter { it != specific }.forEach {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/"))) }) {
                Text(stringResource(R.string.bat_dontkillmyapp))
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
