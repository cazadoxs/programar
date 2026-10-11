package app.facil.launcher.ui

import android.Manifest
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import app.facil.launcher.core.Phone
import app.facil.launcher.system.Actions
import app.facil.launcher.system.SyncJob
import java.util.Locale

/**
 * Pantalla de ayuda. Cuenta atrás de 5 segundos con un botón enorme para
 * cancelar (por si fue sin querer) y después llama al contacto de emergencia.
 * El 112 siempre está a un toque (se abre marcado: Android no deja que una
 * app llame sola a números de emergencia).
 */
class EmergencyActivity : BaseActivity() {
    private var remaining = COUNTDOWN
    private var cancelled = false
    private var tts: TextToSpeech? = null
    private lateinit var counter: android.widget.TextView

    private val tick = object : Runnable {
        override fun run() {
            if (cancelled) return
            remaining--
            if (remaining <= 0) { callPrimary(); return }
            counter.text = remaining.toString()
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val c = store.config
        val hasPrimary = Phone.isValid(c.sosPhone)
        val root = vbox {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(title("Pedir ayuda").apply { gravity = Gravity.CENTER }, lp())
            if (hasPrimary) {
                addView(text("Llamando a ${c.sosName.ifBlank { Phone.pretty(c.sosPhone) }} en…", 24f, pal.text, MEDIUM, Gravity.CENTER), lp())
                counter = text(COUNTDOWN.toString(), 140f, pal.sos, BLACK, Gravity.CENTER)
                addView(counter, lp(MATCH, 0, 1f))
                addView(button("CANCELAR", pal.surface, pal.text, 30f) { cancelled = true; finish() }.apply {
                    background = rounded(pal.surface, dpf(28), pal.text, dp(3))
                    minHeight = dp(110)
                }, lp())
                addView(space(14))
                addView(button("Llamar ya", pal.sos, pal.onSos, 26f) { callPrimary() }, lp())
            } else {
                addView(text("No hay un contacto de emergencia configurado.", 24f, pal.text, MEDIUM, Gravity.CENTER), lp(MATCH, 0, 1f))
                addView(button("Volver", pal.surface, pal.text, 26f) { finish() }, lp())
            }
            if (Phone.isValid(c.sos2Phone)) {
                addView(space(14))
                addView(ghostButton("Llamar a ${c.sos2Name.ifBlank { Phone.pretty(c.sos2Phone) }}") {
                    cancelled = true; Actions.call(this@EmergencyActivity, c.sos2Phone); finish()
                }, lp())
            }
            addView(space(14))
            addView(ghostButton("Emergencias 112") {
                cancelled = true; Actions.dialEmergency(this@EmergencyActivity); finish()
            }, lp())
        }
        setRoot(root)
        store.lastSos = System.currentTimeMillis()
        SyncJob.runSoon(this)
        if (hasPrimary) {
            main.postDelayed(tick, 1000)
            if (c.speak) tts = TextToSpeech(applicationContext) { s ->
                if (s == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("es", "ES")
                    tts?.speak("Voy a llamar a ${c.sosName}. Pulsa cancelar si ha sido sin querer.", TextToSpeech.QUEUE_FLUSH, null, "sos")
                }
            }
        }
    }

    private fun callPrimary() {
        if (cancelled) return
        cancelled = true
        val phone = store.config.sosPhone
        if (!Actions.canCallDirectly(this)) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 9)
            return
        }
        Actions.call(this, phone)
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Actions.call(this, store.config.sosPhone)
        finish()
    }

    override fun onDestroy() {
        cancelled = true
        main.removeCallbacks(tick)
        tts?.shutdown()
        super.onDestroy()
    }

    companion object { private const val COUNTDOWN = 5 }
}
