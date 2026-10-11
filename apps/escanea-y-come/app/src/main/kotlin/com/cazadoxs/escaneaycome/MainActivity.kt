package com.cazadoxs.escaneaycome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cazadoxs.escaneaycome.ui.AppRoot
import com.cazadoxs.escaneaycome.ui.theme.EscaneaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EscaneaApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            EscaneaTheme(accent = if (settings.plus) settings.accent else com.cazadoxs.escaneaycome.data.Accent.HUERTA) {
                AppRoot(container)
            }
        }
    }
}
