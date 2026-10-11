package io.github.cazadoxs.toma.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.cazadoxs.toma.BuildConfig
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.ui.AppViewModel
import io.github.cazadoxs.toma.ui.components.LabelCard
import io.github.cazadoxs.toma.ui.components.SectionTitle
import io.github.cazadoxs.toma.ui.components.SettingRow
import io.github.cazadoxs.toma.ui.components.SwitchRow
import io.github.cazadoxs.toma.ui.components.TomaTopBar
import io.github.cazadoxs.toma.ui.theme.Palettes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onProfiles: () -> Unit,
    onCaregiver: () -> Unit,
    onBackup: () -> Unit,
    onPlus: () -> Unit,
    onBattery: () -> Unit,
) {
    val prefs by vm.prefs.collectAsState()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TomaTopBar(stringResource(R.string.settings_title)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            LabelCard(accent = MaterialTheme.colorScheme.secondary, onClick = onPlus) {
                Text(stringResource(R.string.settings_plus), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (prefs.plusOwned) R.string.plus_owned else R.string.plus_tagline),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle(stringResource(R.string.settings_profiles))
            SettingRow(stringResource(R.string.settings_profiles), stringResource(R.string.settings_profiles_sub), Icons.Rounded.Group, onClick = onProfiles)
            SettingRow(stringResource(R.string.settings_caregiver), stringResource(R.string.settings_caregiver_sub), Icons.Rounded.Favorite, onClick = onCaregiver)

            SectionTitle(stringResource(R.string.settings_alarm))
            Choice(stringResource(R.string.settings_repeat), prefs.repeatMinutes, listOf(2, 5, 10, 15)) { v -> vm.update { it.copy(repeatMinutes = v) } }
            Choice(stringResource(R.string.settings_snooze), prefs.snoozeMinutes, listOf(5, 10, 15, 30)) { v -> vm.update { it.copy(snoozeMinutes = v) } }
            Choice(stringResource(R.string.settings_grace), prefs.graceMinutes, listOf(30, 60, 90, 120)) { v -> vm.update { it.copy(graceMinutes = v) } }
            SettingRow(stringResource(R.string.settings_permissions), stringResource(R.string.perm_battery_body), Icons.Rounded.BatteryAlert, onClick = onBattery)

            SectionTitle(stringResource(R.string.settings_appearance))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Palettes.forEach { p ->
                    val locked = p.plus && !prefs.plusOwned
                    FilterChip(
                        selected = prefs.themeId == p.id && !locked,
                        onClick = { if (locked) onPlus() else vm.update { it.copy(themeId = p.id) } },
                        label = { Text(stringResource(p.nameRes) + if (locked) " ★" else "") },
                    )
                }
            }
            SwitchRow(stringResource(R.string.settings_large_text), null, prefs.largeText) { v -> vm.update { it.copy(largeText = v) } }

            SectionTitle(stringResource(R.string.settings_backup))
            SettingRow(stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_sub), Icons.Rounded.Backup, onClick = onBackup)

            SectionTitle(stringResource(R.string.settings_about))
            LabelCard(accent = MaterialTheme.colorScheme.tertiary) {
                Text(stringResource(R.string.settings_disclaimer_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.settings_disclaimer_body), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_free_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(48.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(title: String, current: Int, options: List<Int>, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                FilterChip(selected = current == o, onClick = { onSelect(o) }, label = { Text(stringResource(R.string.settings_minutes, o)) })
            }
        }
    }
}
