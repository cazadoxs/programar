package app.facil.launcher.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.LinearLayout
import android.widget.TextView
import app.facil.launcher.core.Pin
import app.facil.launcher.core.Throttle

/**
 * Teclado de PIN grande. Modos:
 *  - verificar (por defecto): comprueba el PIN y abre [EXTRA_NEXT].
 *  - crear: pide el PIN dos veces y lo devuelve como (sal, hash) en el resultado.
 */
class PinActivity : BaseActivity() {
    private var entered = ""
    private var first: String? = null
    private lateinit var dots: TextView
    private lateinit var message: TextView
    private val create get() = intent.getBooleanExtra(EXTRA_CREATE, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(title(if (create) "Elige un PIN" else "Escribe el PIN").apply { gravity = Gravity.CENTER }, lp())
            message = text(if (create) "4 a 8 números. Sirve para que nadie cambie los ajustes sin querer." else "Para entrar en los ajustes",
                19f, pal.sub, gravity = Gravity.CENTER)
            addView(message, lp())
            dots = text("", 44f, pal.text, BLACK, Gravity.CENTER).apply { letterSpacing = 0.3f }
            addView(dots, lp(MATCH, dp(80)))
            addView(keypad(), lp(MATCH, 0, 1f))
        }
        setRoot(root)
        render()
    }

    private fun keypad(): LinearLayout = vbox {
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("Borrar", "0", "OK"))
        keys.forEachIndexed { ri, row ->
            addView(hbox {
                row.forEachIndexed { ci, k ->
                    val isAction = k.length > 1
                    val b = button(k, if (k == "OK") pal.accent else pal.surface, if (k == "OK") pal.onAccent else pal.text, if (isAction) 22f else 34f) { press(k) }
                    if (k != "OK") b.background = ripple(rounded(pal.surface, dpf(22), pal.line, dp(2)), pal.dark, dpf(22))
                    addView(b, lp(0, MATCH, 1f).apply { if (ci > 0) marginStart = dp(10) })
                }
            }, lp(MATCH, 0, 1f).apply { if (ri > 0) topMargin = dp(10) })
        }
    }

    private fun press(k: String) {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        when (k) {
            "Borrar" -> entered = entered.dropLast(1)
            "OK" -> submit()
            else -> if (entered.length < 8) entered += k
        }
        render()
    }

    private fun render() { dots.text = "●".repeat(entered.length).ifEmpty { "– – – –" } }

    private fun submit() {
        if (create) {
            if (!Pin.isValidFormat(entered)) { message.text = "Tiene que tener entre 4 y 8 números"; return }
            val f = first
            if (f == null) {
                first = entered; entered = ""
                message.text = "Escríbelo otra vez para confirmarlo"
            } else if (f == entered) {
                val salt = Pin.newSalt()
                val hash = Pin.hash(entered, salt)
                setResult(RESULT_OK, Intent().putExtra(RES_SALT, salt).putExtra(RES_HASH, hash))
                finish()
            } else {
                first = null; entered = ""
                message.text = "No coinciden. Empieza de nuevo."
            }
            return
        }
        val now = System.currentTimeMillis()
        val wait = Throttle.remaining(store.pinFailures, store.pinLastFailure, now)
        if (wait > 0) {
            message.text = "Demasiados intentos. Espera ${(wait / 1000) + 1} segundos."
            entered = ""; return
        }
        val c = store.config
        if (Pin.verify(entered, c.pinSalt, c.pinHash)) {
            store.pinFailures = 0
            when (intent.getStringExtra(EXTRA_NEXT)) {
                NEXT_SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
                else -> setResult(RESULT_OK)
            }
            finish()
        } else {
            store.pinFailures = store.pinFailures + 1
            store.pinLastFailure = now
            entered = ""
            val left = Throttle.FREE_ATTEMPTS - store.pinFailures
            message.text = if (left > 0) "PIN incorrecto" else "PIN incorrecto. Espera ${Throttle.lockMillis(store.pinFailures) / 1000} segundos."
        }
    }

    companion object {
        const val EXTRA_CREATE = "create"
        const val EXTRA_NEXT = "next"
        const val NEXT_SETTINGS = "settings"
        const val RES_SALT = "salt"
        const val RES_HASH = "hash"
    }
}
