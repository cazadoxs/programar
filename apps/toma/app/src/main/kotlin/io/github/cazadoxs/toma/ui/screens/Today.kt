package io.github.cazadoxs.toma.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.alarm.Notifications
import io.github.cazadoxs.toma.core.DoseStatus
import io.github.cazadoxs.toma.core.ScheduleType
import io.github.cazadoxs.toma.data.DayDose
import io.github.cazadoxs.toma.data.DoseState
import io.github.cazadoxs.toma.settings.Prefs
import io.github.cazadoxs.toma.ui.AppViewModel
import io.github.cazadoxs.toma.ui.Formats
import io.github.cazadoxs.toma.ui.Permissions
import io.github.cazadoxs.toma.ui.components.Banner
import io.github.cazadoxs.toma.ui.components.BlisterStrip
import io.github.cazadoxs.toma.ui.components.LabelCard
import io.github.cazadoxs.toma.ui.components.PillIcon
import io.github.cazadoxs.toma.ui.components.swatch
import io.github.cazadoxs.toma.ui.theme.LocalTomaColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(vm: AppViewModel, onAddMed: () -> Unit, onOpenGuide: () -> Unit, onOpenMed: (Long) -> Unit) {
    val context = LocalContext.current
    val state by vm.today.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val meds by vm.medications.collectAsState()
    val day by vm.day.collectAsState()
    val isToday = day == LocalDate.now()

    var readiness by remember { mutableStateOf(Permissions.check(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) readiness = Permissions.check(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val visibleMeds = meds.filter { prefs.activeProfileId == Prefs.ALL_PROFILES || it.profileId == prefs.activeProfileId }
    val scheduled = state.doses.filter { it.med.schedule.type != ScheduleType.AS_NEEDED }
    val taken = scheduled.count { it.state == DoseState.TAKEN }
    val asNeededMeds = visibleMeds.filter { it.active && it.schedule.type == ScheduleType.AS_NEEDED }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (visibleMeds.isNotEmpty()) {
                FloatingActionButton(onClick = onAddMed, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.today_add_med))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(day).replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            if (profiles.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = prefs.activeProfileId == Prefs.ALL_PROFILES,
                                onClick = { vm.selectProfile(Prefs.ALL_PROFILES) },
                                label = { Text(stringResource(R.string.today_all_profiles)) },
                            )
                        }
                        items(profiles) { p ->
                            FilterChip(
                                selected = prefs.activeProfileId == p.id,
                                onClick = { vm.selectProfile(p.id) },
                                label = { Text("${p.emoji} ${p.name}") },
                            )
                        }
                    }
                }
            }
            if (!readiness.essentialsOk) {
                item {
                    Banner(
                        text = stringResource(if (!readiness.notifications) R.string.perm_notifications_body else R.string.perm_exact_body),
                        action = stringResource(R.string.perm_grant),
                        color = LocalTomaColors.current.missed,
                        onClick = {
                            context.startActivity(
                                if (!readiness.notifications) Permissions.notificationSettings(context) else Permissions.exactAlarmSettings(context),
                            )
                        },
                    )
                }
            } else if (!readiness.batteryUnrestricted && visibleMeds.isNotEmpty()) {
                item {
                    Banner(
                        text = stringResource(R.string.perm_battery_body),
                        action = stringResource(R.string.perm_guide),
                        color = LocalTomaColors.current.refill,
                        onClick = onOpenGuide,
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { vm.day.value = day.minusDays(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null)
                    }
                    Text(
                        if (isToday) stringResource(R.string.tab_today) else day.toString(),
                        modifier = Modifier.weight(1f).clickable { vm.day.value = LocalDate.now() },
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { vm.day.value = day.plusDays(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    }
                }
            }

            if (visibleMeds.isEmpty()) {
                item { EmptyToday(onAddMed) }
                return@LazyColumn
            }

            if (scheduled.isNotEmpty()) {
                item {
                    LabelCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (taken == scheduled.size) stringResource(R.string.today_all_done)
                                else stringResource(R.string.today_progress, taken, scheduled.size),
                                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                            )
                            if (isToday && state.streak > 1) {
                                Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.secondary)
                                Text(" ${state.streak}", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        BlisterStrip(scheduled, stringResource(R.string.today_blister_desc, taken, scheduled.size))
                        if (isToday && state.next.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(
                                    R.string.today_next,
                                    state.next.joinToString(", ") { it.med.name },
                                    Notifications.timeText(state.next.first().at),
                                ),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (asNeededMeds.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.today_nothing_today), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            items(state.doses, key = { "${it.med.id}-${it.at.toEpochMilli()}" }) { dose ->
                DoseCard(dose, onOpen = { onOpenMed(dose.med.id) }, onTake = { vm.record(dose, DoseStatus.TAKEN) },
                    onSkip = { vm.record(dose, DoseStatus.SKIPPED) }, onUndo = { vm.undo(dose) })
            }

            if (isToday && asNeededMeds.isNotEmpty()) {
                item { Text(stringResource(R.string.today_as_needed).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }
                items(asNeededMeds, key = { "asneeded-${it.id}" }) { med ->
                    LabelCard(accent = swatch(med.colorIndex), onClick = { onOpenMed(med.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillIcon(med.form, med.colorIndex)
                            Column(Modifier.weight(1f)) {
                                Text(med.name, style = MaterialTheme.typography.titleMedium)
                                if (med.doseLabel.isNotBlank()) Text(med.doseLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton(onClick = { vm.takeAsNeeded(med, med.schedule.slots.firstOrNull()?.amount ?: 1.0) }) {
                                Text(stringResource(R.string.dose_log_now))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun DoseCard(dose: DayDose, onOpen: () -> Unit, onTake: () -> Unit, onSkip: () -> Unit, onUndo: () -> Unit) {
    val colors = LocalTomaColors.current
    val med = dose.med
    LabelCard(accent = swatch(med.colorIndex), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillIcon(med.form, med.colorIndex)
            Column(Modifier.weight(1f)) {
                Text(Notifications.timeText(dose.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(med.name, style = MaterialTheme.typography.titleMedium)
                val detail = listOfNotNull(
                    med.doseLabel.takeIf { it.isNotBlank() },
                    Formats.instruction(androidx.compose.ui.platform.LocalContext.current, med.instruction),
                ).joinToString(" · ")
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (dose.state) {
                DoseState.TAKEN -> TextButton(onClick = onUndo) {
                    Text(stringResource(R.string.status_taken), color = colors.taken)
                }
                DoseState.SKIPPED -> TextButton(onClick = onUndo) { Text(stringResource(R.string.status_skipped), color = colors.skipped) }
                DoseState.MISSED -> TextButton(onClick = onUndo) { Text(stringResource(R.string.status_missed), color = colors.missed) }
                else -> Unit
            }
        }
        if (dose.state == DoseState.DUE || dose.state == DoseState.UPCOMING) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTake, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dose_take)) }
                OutlinedButton(onClick = onSkip) { Text(stringResource(R.string.dose_skip)) }
            }
        }
    }
}

@Composable
private fun EmptyToday(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PillIcon(io.github.cazadoxs.toma.data.MedForm.CAPSULE, 0, size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.today_empty_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd, modifier = Modifier.height(52.dp)) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.padding(4.dp))
            Text(stringResource(R.string.today_add_med))
        }
    }
}
