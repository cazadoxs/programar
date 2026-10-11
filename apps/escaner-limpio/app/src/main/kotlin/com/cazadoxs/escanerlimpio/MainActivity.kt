package com.cazadoxs.escanerlimpio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cazadoxs.escanerlimpio.security.LockGuard
import com.cazadoxs.escanerlimpio.ui.EscanerRoot
import com.cazadoxs.escanerlimpio.ui.IncomingShare
import com.cazadoxs.escanerlimpio.util.Share
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val app get() = application as EscanerApp

    var locked by mutableStateOf(false)
        private set
    var incoming by mutableStateOf<IncomingShare?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        locked = app.settings.state.value.appLock && savedInstanceState?.getBoolean(K_UNLOCKED) != true
        if (savedInstanceState == null) handleShare(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.settings.state.collect { s ->
                    if (s.secureScreen || s.appLock && locked) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent { EscanerRoot(activity = this, locked = locked, onUnlocked = ::unlock) }
    }

    private fun unlock() {
        locked = false
        if (!app.settings.state.value.secureScreen) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        if (app.settings.state.value.appLock && LockGuard.shouldLockOnStart()) {
            locked = true
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStop() {
        super.onStop()
        LockGuard.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(K_UNLOCKED, !locked)
    }

    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }.filter { Share.isSafeIncoming(this, it) }.take(50)
        if (uris.isEmpty()) return
        val pdf = intent.type == "application/pdf" || uris.any { contentResolver.getType(it) == "application/pdf" }
        incoming = IncomingShare(uris, pdf)
    }

    private companion object {
        const val K_UNLOCKED = "desbloqueado"
    }
}
