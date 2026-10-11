package io.github.cazadoxs.toma.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.core.DoseSlot
import io.github.cazadoxs.toma.core.Refill
import io.github.cazadoxs.toma.core.Schedule
import io.github.cazadoxs.toma.core.ScheduleError
import io.github.cazadoxs.toma.core.ScheduleType
import io.github.cazadoxs.toma.data.Instruction
import io.github.cazadoxs.toma.data.MedForm
import io.github.cazadoxs.toma.data.MedicationEntity
import io.github.cazadoxs.toma.ui.AppViewModel
import io.github.cazadoxs.toma.ui.Formats
import io.github.cazadoxs.toma.ui.components.PillIcon
import io.github.cazadoxs.toma.ui.components.SectionTitle
import io.github.cazadoxs.toma.ui.components.TomaTopBar
import io.github.cazadoxs.toma.ui.components.swatch
import io.github.cazadoxs.toma.settings.Prefs
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedEditorScreen(vm: AppViewModel, medId: Long, onClose: () -> Unit, onPlus: () -> Unit) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsState()
    val prefs by vm.prefs.collectAsState()
    var loaded by remember { mutableStateOf(medId == 0L) }
    var original by remember { mutableStateOf<MedicationEntity?>(null) }

    var name by remember { mutableStateOf("") }
    var doseLabel by remember { mutableStateOf("") }
    var form by remember { mutableStateOf(MedForm.PILL) }
    var colorIndex by remember { mutableStateOf(0) }
    var type by remember { mutableStateOf(ScheduleType.DAILY) }
    var slots by remember { mutableStateOf(listOf(DoseSlot.of(9, 0))) }
    var weekdays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var everyN by remember { mutableStateOf(2) }
    var intervalHours by remember { mutableStateOf(8) }
    var amount by remember { mutableStateOf(1.0) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var instruction by remember { mutableStateOf(Instruction.NONE) }
    var notes by remember { mutableStateOf("") }
    var trackStock by remember { mutableStateOf(false) }
    var stockText by remember { mutableStateOf("") }
    var warnDays by remember { mutableStateOf(7) }
    var profileId by remember { mutableStateOf(0L) }
    var showErrors by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var timeEditor by remember { mutableStateOf<Int?>(null) } // index into slots, -1 = new
    var datePicker by remember { mutableStateOf<String?>(null) } // "start" | "end"

    LaunchedEffect(medId, profiles) {
        if (medId == 0L) {
            if (profileId == 0L) {
                profileId = prefs.activeProfileId.takeIf { it != Prefs.ALL_PROFILES } ?: profiles.firstOrNull()?.id ?: 0L
            }
            return@LaunchedEffect
        }
        if (loaded) return@LaunchedEffect
        val m = vm.repository.medication(medId) ?: return@LaunchedEffect onClose()
        original = m
        name = m.name; doseLabel = m.doseLabel; form = m.form; colorIndex = m.colorIndex
        type = m.schedule.type; slots = m.schedule.slots.ifEmpty { listOf(DoseSlot.of(9, 0)) }
        weekdays = m.schedule.weekdays.ifEmpty { setOf(1, 2, 3, 4, 5) }
        everyN = m.schedule.everyNDays.coerceAtLeast(2); intervalHours = m.schedule.intervalHours
        amount = m.schedule.slots.firstOrNull()?.amount ?: 1.0
        startDate = m.schedule.startDate; endDate = m.schedule.endDate
        instruction = m.instruction; notes = m.notes
        trackStock = m.stock != null; stockText = m.stock?.let(Formats::amount) ?: ""; warnDays = m.refillWarnDays
        profileId = m.profileId
        loaded = true
    }

    fun buildSchedule(): Schedule {
        val usedSlots = when (type) {
            ScheduleType.AS_NEEDED -> listOf(DoseSlot(9 * 60, amount))
            ScheduleType.INTERVAL_HOURS -> slots.take(1).map { it.copy(amount = amount) }
            else -> slots.map { it.copy(amount = amount) }
        }
        return Schedule(
            type = type, slots = usedSlots, weekdays = if (type == ScheduleType.WEEKDAYS) weekdays else emptySet(),
            everyNDays = if (type == ScheduleType.EVERY_N_DAYS) everyN else 1, intervalHours = intervalHours,
            startEpochDay = startDate.toEpochDay(), endEpochDay = endDate?.toEpochDay(),
        )
    }

    val schedule = buildSchedule()
    val errors = schedule.validate()
    val nameError = name.isBlank()
    val valid = !nameError && errors.isEmpty() && profileId != 0L

    fun save() {
        if (!valid) {
            showErrors = true
            return
        }
        val stock = if (trackStock) stockText.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 100_000.0) else null
        val base = original
        vm.saveMedication(
            (base ?: MedicationEntity(
                profileId = profileId, name = "", doseLabel = "", form = form, colorIndex = colorIndex, notes = "",
                instruction = instruction, schedule = schedule, stock = null, refillWarnDays = warnDays, active = true,
                createdAt = System.currentTimeMillis(),
            )).copy(
                profileId = profileId, name = name, doseLabel = doseLabel, form = form, colorIndex = colorIndex,
                notes = notes, instruction = instruction, schedule = schedule, stock = stock, refillWarnDays = warnDays,
            ),
            onDone = onClose,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TomaTopBar(
                stringResource(if (medId == 0L) R.string.med_new else R.string.med_edit),
                onBack = onClose,
                actions = {
                    if (medId != 0L) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                    }
                    TextButton(onClick = ::save) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillIcon(form, colorIndex, size = 64.dp)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80) }, singleLine = true, modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.med_name)) }, placeholder = { Text(stringResource(R.string.med_name_hint)) },
                    isError = showErrors && nameError, supportingText = { if (showErrors && nameError) Text(stringResource(R.string.med_error_name)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = doseLabel, onValueChange = { doseLabel = it.take(80) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.med_dose_label)) }, placeholder = { Text(stringResource(R.string.med_dose_hint)) },
                supportingText = { Text(stringResource(R.string.med_no_advice)) },
            )

            if (profiles.size > 1) {
                SectionTitle(stringResource(R.string.med_for_whom))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { p ->
                        FilterChip(selected = profileId == p.id, onClick = { profileId = p.id }, label = { Text("${p.emoji} ${p.name}") })
                    }
                }
            }

            SectionTitle(stringResource(R.string.med_form))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MedForm.entries.forEach { f ->
                    FilterChip(selected = form == f, onClick = { form = f }, label = { Text(Formats.form(context, f)) })
                }
            }
            SectionTitle(stringResource(R.string.med_color))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(8) { i ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(swatch(i))
                            .then(if (colorIndex == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { colorIndex = i },
                    )
                }
            }

            SectionTitle(stringResource(R.string.med_schedule))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ScheduleType.DAILY to R.string.sched_daily, ScheduleType.WEEKDAYS to R.string.sched_weekdays,
                    ScheduleType.EVERY_N_DAYS to R.string.sched_every_n, ScheduleType.INTERVAL_HOURS to R.string.sched_interval,
                    ScheduleType.AS_NEEDED to R.string.sched_as_needed,
                ).forEach { (t, label) ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(stringResource(label)) })
                }
            }
            Spacer(Modifier.height(12.dp))

            if (type == ScheduleType.WEEKDAYS) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = d in weekdays,
                            onClick = { weekdays = if (d in weekdays) weekdays - d else weekdays + d },
                            label = { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                if (showErrors && ScheduleError.NO_WEEKDAYS in errors) ErrorText(R.string.med_error_weekdays)
            }
            if (type == ScheduleType.EVERY_N_DAYS) {
                Stepper(stringResource(R.string.med_every_days), everyN, 2..Schedule.MAX_EVERY_N_DAYS) { everyN = it }
            }
            if (type == ScheduleType.INTERVAL_HOURS) {
                Stepper(stringResource(R.string.med_every_hours), intervalHours, 1..Schedule.MAX_INTERVAL_HOURS) { intervalHours = it }
            }

            when (type) {
                ScheduleType.AS_NEEDED -> Unit
                ScheduleType.INTERVAL_HOURS -> {
                    Spacer(Modifier.height(8.dp))
                    TimeChip(Formats.time(slots.first()), stringResource(R.string.med_start_time)) { timeEditor = 0 }
                }
                else -> {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.med_times), style = MaterialTheme.typography.titleSmall)
                    slots.sortedBy { it.minuteOfDay }.forEachIndexed { _, slot ->
                        val index = slots.indexOf(slot)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { timeEditor = index }, modifier = Modifier.weight(1f)) {
                                Text(Formats.time(slot), style = MaterialTheme.typography.titleMedium)
                            }
                            if (slots.size > 1) {
                                IconButton(onClick = { slots = slots - slot }) { Icon(Icons.Rounded.Close, null) }
                            }
                        }
                    }
                    if (slots.size < Schedule.MAX_SLOTS) {
                        TextButton(onClick = { timeEditor = -1 }) {
                            Icon(Icons.Rounded.Add, null); Spacer(Modifier.padding(3.dp)); Text(stringResource(R.string.med_add_time))
                        }
                    }
                    if (showErrors && ScheduleError.NO_TIMES in errors) ErrorText(R.string.med_error_times)
                }
            }

            Spacer(Modifier.height(12.dp))
            DecimalField(stringResource(R.string.med_amount), amount, stringResource(R.string.med_units_per_dose)) { amount = it }

            SectionTitle(stringResource(R.string.med_start_date))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { datePicker = "start" }) { Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(startDate)) }
                if (endDate == null) {
                    OutlinedButton(onClick = { datePicker = "end" }) { Text(stringResource(R.string.med_no_end)) }
                } else {
                    OutlinedButton(onClick = { datePicker = "end" }) {
                        Text(stringResource(R.string.med_end_date) + ": " + DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(endDate))
                    }
                    IconButton(onClick = { endDate = null }) { Icon(Icons.Rounded.Close, null) }
                }
            }
            if (showErrors && ScheduleError.END_BEFORE_START in errors) ErrorText(R.string.med_error_dates)

            SectionTitle(stringResource(R.string.med_instruction))
            var open by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(
                    value = Formats.instruction(context, instruction) ?: stringResource(R.string.instr_none), onValueChange = {},
                    readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    Instruction.entries.forEach { i ->
                        DropdownMenuItem(
                            text = { Text(Formats.instruction(context, i) ?: stringResource(R.string.instr_none)) },
                            onClick = { instruction = i; open = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it.take(500) }, label = { Text(stringResource(R.string.med_notes)) },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )

            SectionTitle(stringResource(R.string.med_track_stock))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.med_track_stock), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = trackStock, onCheckedChange = { trackStock = it })
            }
            if (trackStock) {
                OutlinedTextField(
                    value = stockText, onValueChange = { stockText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) },
                    label = { Text(stringResource(R.string.med_stock)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                val stockNow = stockText.replace(',', '.').toDoubleOrNull()
                val left = Refill.daysLeft(stockNow, schedule)
                if (left != null) Text(stringResource(R.string.med_stock_days, left), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Stepper(stringResource(R.string.med_stock_warn), warnDays, 0..60) { warnDays = it }
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.save)) }
            if (original != null) {
                val m = original!!
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.setActive(m, !m.active); onClose() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (m.active) R.string.med_pause else R.string.med_resume))
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    timeEditor?.let { index ->
        val initial = if (index >= 0) slots.getOrNull(index) ?: DoseSlot.of(9, 0) else DoseSlot.of(9, 0)
        val state = rememberTimePickerState(initial.minuteOfDay / 60, initial.minuteOfDay % 60, is24Hour = android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { timeEditor = null },
            confirmButton = {
                TextButton(onClick = {
                    val slot = DoseSlot(state.hour * 60 + state.minute, amount)
                    slots = when {
                        index == -1 -> (slots + slot).distinctBy { it.minuteOfDay }
                        else -> slots.mapIndexed { i, s -> if (i == index) slot else s }.distinctBy { it.minuteOfDay }
                    }
                    timeEditor = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { timeEditor = null }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state) },
        )
    }

    datePicker?.let { which ->
        val initial = if (which == "start") startDate else endDate ?: startDate.plusDays(7)
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "start") startDate = d else endDate = d
                    }
                    datePicker = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { datePicker = null }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.med_delete_title)) },
            text = { Text(stringResource(R.string.med_delete_body)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteMedication(medId); confirmDelete = false; onClose() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ErrorText(res: Int) =
    Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun TimeChip(time: String, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onClick) { Text(time, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        Text("$value", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
    }
}

@Composable
private fun DecimalField(label: String, value: Double, suffix: String, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(Formats.amount(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
            text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= DoseSlot.MAX_AMOUNT }?.let(onChange)
        },
        label = { Text(label) }, suffix = { Text(suffix) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
    )
}
