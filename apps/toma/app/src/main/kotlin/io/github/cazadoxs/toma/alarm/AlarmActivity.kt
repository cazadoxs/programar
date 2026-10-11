package io.github.cazadoxs.toma.alarm

import android.app.KeyguardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.container
import io.github.cazadoxs.toma.core.DoseStatus
import io.github.cazadoxs.toma.data.DueDose
import io.github.cazadoxs.toma.ui.Formats
import io.github.cazadoxs.toma.ui.components.LabelCard
import io.github.cazadoxs.toma.ui.components.PillIcon
import io.github.cazadoxs.toma.ui.components.swatch
import io.github.cazadoxs.toma.ui.theme.TomaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** Full-screen alarm over the lock screen. Big buttons: it must be usable half asleep. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = container.repository
        setContent {
            val prefs by container.settings.state.collectAsState()
            var due by remember { mutableStateOf<List<DueDose>?>(null) }
            LaunchedEffect(Unit) {
                while (true) {
                    due = repo.dueDoses(Instant.now())
                    delay(5_000)
                }
            }
            TomaTheme(prefs.themeId, prefs.plusOwned, prefs.largeText) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AlarmContent(
                        due = due,
                        snoozeMinutes = prefs.snoozeMinutes,
                        onTake = { list -> act { DoseActions.record(this@AlarmActivity, list, DoseStatus.TAKEN) } },
                        onSkip = { list -> act { DoseActions.record(this@AlarmActivity, list, DoseStatus.SKIPPED) } },
                        onSnooze = { DoseActionReceiver.snooze(this@AlarmActivity); finish() },
                        onClose = ::finish,
                    )
                }
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        Notifications.dismissAlarm(this)
        lifecycleScope.launch {
            block()
            if (container.repository.dueDoses(Instant.now()).isEmpty()) {
                getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this@AlarmActivity, null)
                finish()
            }
        }
    }
}

@Composable
private fun AlarmContent(
    due: List<DueDose>?,
    snoozeMinutes: Int,
    onTake: (List<DueDose>) -> Unit,
    onSkip: (List<DueDose>) -> Unit,
    onSnooze: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        if (due == null) return@Column
        if (due.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.alarm_nothing), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text(stringResource(R.string.alarm_close)) }
            return@Column
        }
        Text(stringResource(R.string.alarm_screen_title), style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(R.string.alarm_time_now, Notifications.timeText(due.first().at)),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            due.forEach { d ->
                LabelCard(accent = swatch(d.med.colorIndex)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PillIcon(d.med.form, d.med.colorIndex, size = 56.dp)
                        Column {
                            Text(d.med.name, style = MaterialTheme.typography.titleLarge)
                            val detail = listOfNotNull(
                                d.med.doseLabel.takeIf { it.isNotBlank() },
                                Formats.instruction(androidx.compose.ui.platform.LocalContext.current, d.med.instruction),
                            ).joinToString(" · ")
                            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onTake(due) }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text(stringResource(R.string.alarm_take_all), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f).height(56.dp)) {
                Text(stringResource(R.string.alarm_snooze_for, snoozeMinutes))
            }
            TextButton(onClick = { onSkip(due) }, modifier = Modifier.height(56.dp)) { Text(stringResource(R.string.alarm_skip_all)) }
        }
    }
}
