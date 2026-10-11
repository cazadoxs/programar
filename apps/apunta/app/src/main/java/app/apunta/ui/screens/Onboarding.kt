package app.apunta.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.apunta.R
import app.apunta.models.ModelCatalog
import app.apunta.models.ModelState
import app.apunta.ui.AppViewModel
import app.apunta.ui.theme.Apunta

@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit, onPaywall: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val models by vm.models.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(24.dp))
        when (step) {
            0 -> {
                Text(stringResource(R.string.ob_title), style = MaterialTheme.typography.displayMedium)
                Text(stringResource(R.string.ob_promise), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.ob_body), style = MaterialTheme.typography.bodyLarge, color = Apunta.colors.inkSoft)
                Text(stringResource(R.string.ob_legal), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button({ step = 1 }, Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.ob_next)) }
            }
            else -> {
                Text(stringResource(R.string.ob_model_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.ob_model_body), style = MaterialTheme.typography.bodyMedium, color = Apunta.colors.inkSoft)
                val rec = ModelCatalog.recommended(vm.totalRamGb())
                if (rec.id != vm.settings.collectAsStateWithLifecycle().value.modelId) vm.update { it.copy(modelId = rec.id) }
                ModelPicker(vm, onPaywall)
                val anyReady = models.values.any { it is ModelState.Ready }
                Button(
                    { vm.update { it.copy(onboarded = true) }; onDone() }, Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(if (anyReady) R.string.ob_start else R.string.ob_skip)) }
            }
        }
    }
}
