package app.facil.launcher.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import app.facil.launcher.R
import app.facil.launcher.core.Codes
import app.facil.launcher.remote.Firebase
import app.facil.launcher.remote.Member
import app.facil.launcher.remote.SeniorSync
import app.facil.launcher.system.Notifs
import app.facil.launcher.system.SyncJob

/**
 * Móvil de la persona mayor: dejar que un familiar lo gestione desde el suyo.
 * Muestra un QR (y el mismo código para escribirlo a mano). Cada familiar
 * que lo usa aparece aquí y hay que aceptarlo expresamente, viendo antes
 * qué podrá hacer y qué no.
 */
class PairActivity : BaseActivity() {
    private lateinit var content: LinearLayout
    private var code: String? = null
    private var codeExpires = 0L
    private var members: List<Member> = emptyList()
    private val asked = mutableSetOf<String>()
    private var polling = false

    private val poll = object : Runnable {
        override fun run() {
            if (!polling) return
            refreshMembers()
            main.postDelayed(this, 4000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = vbox()
        setRoot(vbox {
            addView(title("Ayuda de la familia"))
            addView(scroll(content), lp(MATCH, 0, 1f))
            addView(button("Volver") { finish() }, lp().apply { topMargin = dp(12) })
        })
        if (!Firebase.get(this).configured) {
            content.addView(text("La gestión desde otro móvil no está activada en esta versión de Fácil.", 20f, pal.text))
            return
        }
        render()
        refreshMembers()
    }

    override fun onResume() { super.onResume(); polling = true; main.post(poll) }
    override fun onPause() { super.onPause(); polling = false; main.removeCallbacks(poll) }

    override fun onDestroy() {
        val c = code
        if (c != null && Firebase.get(this).configured) BG.execute { SeniorSync(applicationContext).endPairing(c) }
        super.onDestroy()
    }

    private fun render() {
        content.removeAllViews()
        content.addView(text(
            "Un familiar de confianza puede cambiar los botones y los ajustes de este móvil desde el suyo, y saber si la batería está baja o si llevas tiempo sin usarlo.",
            19f, pal.text))
        content.addView(space(14))

        content.addView(card {
            addView(text("Qué podrá ver y hacer", 20f, pal.text, BOLD))
            listOf(
                "✓ Cambiar los botones, recordatorios y ajustes",
                "✓ Ver el nivel de batería",
                "✓ Saber cuándo usaste el móvil por última vez",
                "✓ Saber si pulsaste el botón de ayuda",
                "✗ No ve tus mensajes, llamadas, fotos ni dónde estás",
            ).forEach { addView(text(it, 18f, pal.text).apply { setPadding(0, dp(6), 0, 0) }) }
        })
        content.addView(space(16))

        val c = code
        if (c == null || System.currentTimeMillis() > codeExpires) {
            content.addView(button("Añadir a un familiar") { newCode() })
        } else {
            content.addView(card {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(text("Tu familiar abre Fácil en su móvil, pulsa «Añadir un móvil» y escanea este código:", 18f, pal.text, gravity = Gravity.CENTER))
                addView(ImageView(this@PairActivity).apply {
                    setImageBitmap(Qr.encode(Codes.qrPayload(c), 600))
                    contentDescription = "Código QR para emparejar"
                }, lp(dp(260), dp(260)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12); bottomMargin = dp(8) })
                addView(text("O que escriba este código:", 17f, pal.sub, gravity = Gravity.CENTER))
                addView(text(Codes.pretty(c), 34f, pal.text, BLACK, Gravity.CENTER).apply { letterSpacing = 0.08f; setTextIsSelectable(true) })
                addView(text("Caduca en 15 minutos", 15f, pal.sub, gravity = Gravity.CENTER))
                addView(space(10))
                addView(ghostButton("Enviar el código por WhatsApp o SMS") { shareCode(c) })
            })
        }

        // Peticiones pendientes y familiares aceptados
        val pending = members.filter { !it.active }
        val active = members.filter { it.active }
        if (pending.isNotEmpty()) {
            content.addView(space(16))
            pending.forEach { m ->
                content.addView(card {
                    background = rounded(pal.warn, dpf(24))
                    addView(text("${m.name} quiere ayudarte con este móvil", 22f, pal.onWarn, BOLD))
                    addView(text("Acepta solo si es alguien de tu confianza.", 17f, pal.onWarn))
                    addView(space(10))
                    addView(button("Aceptar", pal.tile(0), pal.onTile) { approve(m) })
                    addView(space(8))
                    addView(ghostButton("No, rechazar") { reject(m) })
                }, lp().apply { bottomMargin = dp(10) })
            }
        }
        content.addView(space(16))
        content.addView(text("TE AYUDAN", 15f, pal.accent, BOLD).apply { letterSpacing = 0.08f })
        content.addView(card {
            if (active.isEmpty()) addView(text("Todavía nadie", 18f, pal.sub))
            active.forEachIndexed { i, m ->
                if (i > 0) addView(divider())
                addView(row(m.name, "Toca para dejar de compartir con esta persona", R.drawable.ic_person) {
                    confirm("¿Quitar a ${m.name}?", "Sí, quitar", "No", "Ya no podrá ver ni cambiar nada de este móvil") { reject(m) }
                })
            }
        })
        content.addView(space(16))
        content.addView(card {
            val on = store.shareStatus
            addView(row("Compartir batería y actividad", if (on) "Sí. Tu familia puede avisarte si algo va mal." else "No. Tu familia no verá ni batería ni actividad.",
                R.drawable.ic_battery) {
                store.shareStatus = !on
                BG.execute { runCatching { SeniorSync(applicationContext).pushStatus() } }
                Notifs.updateSharing(this@PairActivity)
                render()
            })
        })
    }

    private fun newCode() {
        if (store.deviceName.isBlank()) {
            val owner = store.config.ownerName
            store.deviceName = if (owner.isNotBlank()) "Móvil de $owner" else "Móvil"
        }
        content.addView(text("Preparando…", 18f, pal.sub))
        bg({ SeniorSync(this).startPairing() }) { r ->
            r.onSuccess { code = it; codeExpires = System.currentTimeMillis() + Codes.TTL_MS; SyncJob.schedule(this); render() }
                .onFailure { toast("No hay conexión a internet. Inténtalo de nuevo."); render() }
        }
    }

    private fun refreshMembers() {
        if (!store.cloudEnabled) return
        bg({ SeniorSync(this).members() }) { r ->
            val list = r.getOrNull() ?: return@bg
            val changed = list != members
            members = list
            if (changed) { render(); Notifs.updateSharing(this) }
            list.filter { !it.active && it.uid !in asked }.forEach { asked += it.uid; window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) }
        }
    }

    private fun approve(m: Member) {
        bg({
            val s = SeniorSync(this)
            s.approve(m.uid)
            s.endPairing(code)
            s.pushStatus()
            s.members()
        }) { r ->
            r.onSuccess {
                code = null; members = it
                toast("${m.name} ya puede ayudarte")
                Notifs.updateSharing(this); render()
            }.onFailure { toast("No hay conexión. Inténtalo de nuevo.") }
        }
    }

    private fun reject(m: Member) {
        bg({ val s = SeniorSync(this); s.remove(m.uid); s.members() }) { r ->
            r.onSuccess { members = it; Notifs.updateSharing(this); render() }.onFailure { toast("No hay conexión. Inténtalo de nuevo.") }
        }
    }

    private fun shareCode(c: String) {
        val msg = "Para ayudarme con el móvil, instala Fácil (https://play.google.com/store/apps/details?id=$packageName), pulsa «Añadir un móvil» y escribe este código: ${Codes.pretty(c)}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Enviar código"))
    }
}
