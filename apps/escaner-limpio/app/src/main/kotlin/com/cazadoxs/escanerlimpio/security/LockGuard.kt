package com.cazadoxs.escanerlimpio.security

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** App lock with fingerprint, face or the phone's screen lock. */
object LockGuard {
    private const val GRACE_MS = 30_000L
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private var stoppedAt = 0L
    private var externalFlow = false

    /** Call right before opening the scanner, a picker or the share sheet. */
    fun expectExternalFlow() {
        externalFlow = true
    }

    fun onStop() {
        stoppedAt = SystemClock.elapsedRealtime()
    }

    /** Whether returning to the app should show the lock screen. */
    fun shouldLockOnStart(): Boolean {
        val wasExternal = externalFlow
        externalFlow = false
        if (stoppedAt == 0L) return false
        val away = SystemClock.elapsedRealtime() - stoppedAt
        return if (wasExternal) away > 10 * 60_000L else away > GRACE_MS
    }

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear Escáner limpio")
                .setSubtitle("Tus documentos están protegidos")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
