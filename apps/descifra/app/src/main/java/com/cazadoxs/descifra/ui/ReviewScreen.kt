package com.cazadoxs.descifra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cazadoxs.descifra.Draft
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    draft: Draft,
    onEdit: (text: String?, received: LocalDate?) -> Unit,
    onAnalyze: () -> Unit,
    onBack: () -> Unit,
    cloudHint: String?,
) {
    val p = MaterialTheme.palette
    var picking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = p.ink) }
            Text("Revisa el texto", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Esto es lo que hemos leído de la carta. Si ves algún error (una fecha o un importe mal leído), corrígelo ahora: así el resultado será más fiable.",
                color = p.inkSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
            Paper(Modifier.fillMaxWidth()) {
                Eyebrow("¿Cuándo la recibiste?")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val today = LocalDate.now()
                    DayChip("Hoy", draft.receivedOn == today) { onEdit(null, today) }
                    DayChip("Ayer", draft.receivedOn == today.minusDays(1)) { onEdit(null, today.minusDays(1)) }
                    DayChip(
                        if (draft.receivedOn == today || draft.receivedOn == today.minusDays(1)) "Otro día" else formatShort(draft.receivedOn),
                        draft.receivedOn != today && draft.receivedOn != today.minusDays(1),
                    ) { picking = true }
                }
                Text(
                    "Los plazos en «días hábiles» se cuentan desde aquí.",
                    color = p.inkSoft,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            OutlinedTextField(
                value = draft.text,
                onValueChange = { onEdit(it, null) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
                label = { Text("Texto de la carta") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            if (cloudHint != null) Text(cloudHint, color = p.inkSoft, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
        }
        BigButton(
            "Descifrar",
            onAnalyze,
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(),
            enabled = draft.text.isNotBlank(),
        )
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = draft.receivedOn.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onEdit(null, if (d.isAfter(LocalDate.now())) LocalDate.now() else d)
                    }
                    picking = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun DayChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = MaterialTheme.palette
    androidx.compose.foundation.layout.Box(
        Modifier
            .heightIn(min = 44.dp)
            .androidxClip()
            .let { if (selected) it.androidxBg(p.ink) else it.androidxBorder(p.line) }
            .androidxClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) p.paper else p.ink, style = MaterialTheme.typography.labelLarge)
    }
}
