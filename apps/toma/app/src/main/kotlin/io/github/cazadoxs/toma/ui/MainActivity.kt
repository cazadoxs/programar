package io.github.cazadoxs.toma.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import io.github.cazadoxs.toma.container
import io.github.cazadoxs.toma.ui.theme.TomaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container.billing.connect()
        setContent {
            val prefs by viewModel.prefs.collectAsState()
            TomaTheme(prefs.themeId, prefs.plusOwned, prefs.largeText) {
                TomaRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        container.billing.connect()
        container.scope.launch { container.scheduler.rescheduleAll() }
    }
}
