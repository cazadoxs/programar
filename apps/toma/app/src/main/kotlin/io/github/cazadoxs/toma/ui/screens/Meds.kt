package io.github.cazadoxs.toma.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExtendedFloatingActionButton
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
import io.github.cazadoxs.toma.core.Refill
import io.github.cazadoxs.toma.settings.Prefs
import io.github.cazadoxs.toma.ui.AppViewModel
import io.github.cazadoxs.toma.ui.Formats
import io.github.cazadoxs.toma.ui.components.LabelCard
import io.github.cazadoxs.toma.ui.components.PillIcon
import io.github.cazadoxs.toma.ui.components.TomaTopBar
import io.github.cazadoxs.toma.ui.components.swatch
import io.github.cazadoxs.toma.ui.theme.LocalTomaColors

@Composable
fun MedsScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    val meds by vm.medications.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val colors = LocalTomaColors.current
    val list = meds.filter { prefs.activeProfileId == Prefs.ALL_PROFILES || it.profileId == prefs.activeProfileId }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TomaTopBar(stringResource(R.string.meds_title)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.med_new)) })
        },
    ) { padding ->
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
                Text(stringResource(R.string.med_none), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.id }) { med ->
                val low = Refill.isLow(med.stock, med.schedule, med.refillWarnDays)
                LabelCard(accent = swatch(med.colorIndex), onClick = { onOpen(med.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillIcon(med.form, med.colorIndex)
                        Column(Modifier.weight(1f)) {
                            Text(med.name, style = MaterialTheme.typography.titleMedium)
                            Text(Formats.schedule(context, med.schedule), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (profiles.size > 1) {
                                profiles.firstOrNull { it.id == med.profileId }?.let {
                                    Text("${it.emoji} ${it.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (!med.active) Text(stringResource(R.string.meds_paused), style = MaterialTheme.typography.labelMedium, color = colors.skipped)
                            if (med.stock != null) {
                                val days = Refill.daysLeft(med.stock, med.schedule)
                                Text(
                                    if (days != null) stringResource(R.string.med_stock_left, Formats.amount(med.stock), days)
                                    else stringResource(R.string.med_stock_left_nodays, Formats.amount(med.stock)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (low) colors.missed else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
