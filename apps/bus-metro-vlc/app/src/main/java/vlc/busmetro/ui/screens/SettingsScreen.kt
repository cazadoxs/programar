package vlc.busmetro.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vlc.busmetro.BuildConfig
import vlc.busmetro.R
import vlc.busmetro.ads.Ads
import vlc.busmetro.app
import vlc.busmetro.data.ThemeMode
import vlc.busmetro.ui.SettingsViewModel
import vlc.busmetro.ui.components.*
import vlc.busmetro.ui.theme.Palette
import java.text.DateFormat
import java.util.Date

const val PRIVACY_URL = "https://github.com/cazadoxs/programar/blob/HEAD/apps/bus-metro-vlc/publicar/politica-privacidad.md"
const val SOURCE_URL = "https://github.com/cazadoxs/programar/tree/HEAD/apps/bus-metro-vlc"

@Composable
fun SettingsScreen(vm: SettingsViewModel, onPro: () -> Unit, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val feeds by vm.feeds.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    fun ymd(v: Int) = if (v == 0) "—" else "%02d/%02d/%04d".format(v % 100, (v / 100) % 100, v / 10000)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium) }

        item {
            Card2(onClick = if (s.pro) null else onPro) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WorkspacePremium, null, tint = Palette.Orange, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (s.pro) R.string.pro_active else R.string.pro_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (s.pro) R.string.pro_thanks else R.string.pro_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.appearance)) }
        item {
            Card2 {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
                    modes.forEachIndexed { i, (m, label) ->
                        SegmentedButton(s.theme == m, { vm.setTheme(m) }, SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(stringResource(label)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                var langMenu by remember { mutableStateOf(false) }
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
                Box {
                    OutlinedButton({ langMenu = true }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Language, null); Spacer(Modifier.width(8.dp))
                        Text(when (current) { "es" -> "Español"; "ca" -> "Valencià"; "en" -> "English"; else -> stringResource(R.string.theme_system) })
                    }
                    DropdownMenu(langMenu, { langMenu = false }) {
                        listOf("" to stringResource(R.string.theme_system), "es" to "Español", "ca" to "Valencià", "en" to "English").forEach { (tag, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { langMenu = false; AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) })
                        }
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.notifications)) }
        item {
            Card2 {
                SwitchRow(stringResource(R.string.set_alert_notifs), stringResource(R.string.set_alert_notifs_body), s.notifyAlerts, vm::setNotify)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(stringResource(R.string.set_bikes), stringResource(R.string.set_bikes_body), s.showBikes, vm::setBikes)
            }
        }

        item { SectionTitle(stringResource(R.string.offline_data)) }
        item {
            Card2 {
                if (feeds.isEmpty()) Text(stringResource(R.string.no_data_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                feeds.sortedBy { it.network }.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetworkDot(f.network, 30.dp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(when (f.network) { "metro" -> R.string.net_metro; "rail" -> R.string.net_rail; else -> R.string.net_bus }), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.feed_valid, ymd(f.validFrom), ymd(f.validTo), f.stops), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (s.lastSync > 0) Text(stringResource(R.string.last_sync, dateFmt.format(Date(s.lastSync))), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                Button(vm::syncNow, enabled = !syncing) {
                    if (syncing) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)) }
                    Text(stringResource(if (syncing) R.string.syncing else R.string.update_now))
                }
            }
        }

        item { SectionTitle(stringResource(R.string.about)) }
        item {
            Card2 {
                Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.data_sources_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LinkRow(Icons.Filled.PrivacyTip, stringResource(R.string.privacy_policy)) { open(PRIVACY_URL) }
                (context as? Activity)?.let { act -> if (Ads.privacyOptionsRequired(act)) LinkRow(Icons.Filled.Tune, stringResource(R.string.ad_choices)) { Ads.showPrivacyOptions(act) } }
                LinkRow(Icons.Filled.Code, stringResource(R.string.source_code)) { open(SOURCE_URL) }
                LinkRow(Icons.Filled.Description, stringResource(R.string.licenses)) { onLicenses() }
                if (s.pro.not()) LinkRow(Icons.Filled.Restore, stringResource(R.string.restore_purchase)) { vm.restore() }
                Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onChange)
    }
}

@Composable
private fun LinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    TextButton(onClick, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(text, Modifier.weight(1f)); Icon(Icons.Filled.ChevronRight, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val offer by vm.offer.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { pad ->
        Column(Modifier.padding(pad).padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Filled.WorkspacePremium, null, tint = Palette.Orange, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.pro_pitch), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(R.string.pro_f1, R.string.pro_f2, R.string.pro_f3, R.string.pro_f4).forEach { f ->
                Row(verticalAlignment = Alignment.Top) { Icon(Icons.Filled.Check, null, tint = Palette.Live); Spacer(Modifier.width(12.dp)); Text(stringResource(f), style = MaterialTheme.typography.bodyLarge) }
            }
            Text(stringResource(R.string.pro_free_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (s.pro) {
                Text(stringResource(R.string.pro_thanks), style = MaterialTheme.typography.titleMedium, color = Palette.Live)
            } else {
                Button({ (context as? Activity)?.let { context.app.billing.launch(it) } }, Modifier.fillMaxWidth().height(56.dp), enabled = offer != null && !busy) {
                    Text(offer?.let { stringResource(R.string.pro_buy, it.price) } ?: stringResource(R.string.pro_unavailable))
                }
                TextButton(vm::restore, Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_purchase)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.licenses)) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(stringResource(R.string.licenses_text), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
