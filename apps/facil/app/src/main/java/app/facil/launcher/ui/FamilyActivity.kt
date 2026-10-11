package app.facil.launcher.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import app.facil.launcher.R
import app.facil.launcher.billing.Billing
import app.facil.launcher.core.Alerts
import app.facil.launcher.core.Codes
import app.facil.launcher.remote.FamilySync
import app.facil.launcher.remote.Firebase
import app.facil.launcher.remote.RemoteDevice
import app.facil.launcher.system.Notifs
import app.facil.launcher.system.SyncJob
import java.text.DateFormat
import java.util.Date

/**
 * Panel del familiar: lista de móviles que gestiona, su estado y acceso
 * a los ajustes remotos. Añadir móviles y editar es gratis solo la primera
 * vez; las alertas y la gestión continua requieren "Fácil Familia".
 */
class FamilyActivity : BaseActivity() {
    private lateinit var content: LinearLayout
    private val billing by lazy { Billing.get(this) }
    private val remotes = mutableMapOf<String, RemoteDevice?>()
    private val billingListener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = vbox()
        setRoot(vbox {
            addView(title("Mi familia"))
            addView(scroll(content), lp(MATCH, 0, 1f))
        })
        billing.addListener(billingListener)
        billing.connect()
        SyncJob.schedule(this)
        render()
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() { billing.removeListener(billingListener); super.onDestroy() }

    private fun refresh() {
        if (!Firebase.get(this).configured || store.devices.isEmpty()) return
        bg({
            val f = FamilySync(this)
            store.devices.associate { d ->
                // Si el familiar aún está pendiente de aprobación, fetch devuelve null (sin acceso).
                d.id to runCatching { f.fetch(d.id) }.getOrNull()
            }
        }) { r ->
            remotes.clear(); remotes.putAll(r.getOrDefault(emptyMap()))
            // Nombre real del móvil una vez aceptado.
            store.devices = store.devices.map { d -> remotes[d.id]?.let { d.copy(name = it.name) } ?: d }
            render()
            if (store.premium) bg({ SyncJob.checkAlerts(this) }) { }
        }
    }

    private fun render() {
        content.removeAllViews()
        if (!Firebase.get(this).configured) {
            content.addView(text("La gestión remota no está activada en esta versión.", 20f, pal.text)); return
        }
        val devices = store.devices
        if (devices.isEmpty()) {
            content.addView(text("Todavía no ayudas a nadie. Pide a la persona que abra Fácil › Ajustes › Ayuda de la familia y escanea su código.", 20f, pal.text))
        }
        devices.forEach { d ->
            val r = remotes[d.id]
            content.addView(card {
                addView(text(d.name.ifBlank { r?.name ?: "Móvil" }, 26f, pal.text, BOLD))
                if (r == null) {
                    addView(text("Esperando a que acepte tu petición en su móvil…", 17f, pal.sub))
                } else {
                    val st = r.status
                    val alerts = r.config?.let { Alerts.evaluate(d.name, st, it, System.currentTimeMillis()) }.orEmpty()
                    if (st.lastSeen > 0) {
                        addView(text("Batería: ${if (st.battery >= 0) "${st.battery} %" else "—"}${if (st.charging) " (cargando)" else ""}", 19f, pal.text))
                        addView(text("Última conexión: " + fmt(st.lastSeen), 17f, pal.sub))
                        if (st.lastActive > 0) addView(text("Último uso: " + fmt(st.lastActive), 17f, pal.sub))
                    } else addView(text("No comparte su estado.", 17f, pal.sub))
                    alerts.forEach { addView(text("⚠ " + it.text, 18f, pal.sos, BOLD).apply { setPadding(0, dp(6), 0, 0) }) }
                }
                addView(space(10))
                addView(button("Cambiar sus ajustes") {
                    if (store.premium) startActivity(Intent(this@FamilyActivity, SettingsActivity::class.java)
                        .putExtra(SettingsActivity.EXTRA_DEVICE, d.id).putExtra(SettingsActivity.EXTRA_NAME, d.name))
                    else paywall()
                })
                addView(space(8))
                addView(ghostButton("Dejar de ayudar") {
                    confirm("¿Dejar de ayudar a ${d.name}?", "Sí", "No") {
                        bg({ FamilySync(this@FamilyActivity).leave(d.id) }) { render() }
                    }
                })
            }, lp().apply { bottomMargin = dp(14) })
        }
        content.addView(button("Añadir un móvil") { addDevice() }, lp().apply { bottomMargin = dp(14) })

        if (!store.premium) content.addView(card {
            addView(text("Fácil Familia", 22f, pal.text, BOLD))
            addView(text("Cambiar los ajustes a distancia y recibir alertas (batería baja, móvil sin usar, pastilla sin marcar).", 17f, pal.sub))
            addView(space(8))
            addView(button("Ver planes") { paywall() })
        }) else content.addView(text("✓ Fácil Familia activa", 18f, pal.accent, BOLD))

        content.addView(space(14))
        content.addView(card {
            addView(row("Mi nombre", store.myName.ifBlank { "Se verá en el móvil de la persona" }, R.drawable.ic_person) {
                val inp = input("Tu nombre", store.myName, max = 30)
                dialog().setTitle("Tu nombre").setView(vbox { setPadding(dp(20), dp(8), dp(20), 0); addView(inp) })
                    .setPositiveButton("Guardar") { _, _ -> store.myName = inp.text.toString().trim(); render() }.setNegativeButton("Cancelar", null).show()
            })
            addView(divider())
            addView(row("Privacidad y datos", null, R.drawable.ic_lock) { startActivity(Intent(this@FamilyActivity, PrivacyActivity::class.java)) })
        })
    }

    private fun fmt(ms: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))

    private fun addDevice() {
        if (store.myName.isBlank()) {
            val inp = input("Tu nombre (p. ej. María)", "", max = 30)
            dialog().setTitle("¿Cómo te llamas?").setMessage("Así te verá la persona a la que ayudas.")
                .setView(vbox { setPadding(dp(20), dp(8), dp(20), 0); addView(inp) })
                .setPositiveButton("Seguir") { _, _ -> store.myName = inp.text.toString().trim().ifEmpty { "Familiar" }; addDevice() }
                .setNegativeButton("Cancelar", null).show()
            return
        }
        dialog().setTitle("Añadir un móvil")
            .setItems(arrayOf("Escanear código QR", "Escribir el código")) { _, w ->
                if (w == 0) startActivityForResult(Intent(this, QrScanActivity::class.java), REQ_QR) else typeCode()
            }.show()
    }

    private fun typeCode() {
        val inp = input("Código de 10 letras", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, 16)
        dialog().setTitle("Código").setView(vbox { setPadding(dp(20), dp(8), dp(20), 0); addView(inp) })
            .setPositiveButton("Unirme") { _, _ -> join(inp.text.toString()) }.setNegativeButton("Cancelar", null).show()
    }

    private fun join(raw: String) {
        val code = Codes.normalize(raw) ?: run { toast("Ese código no es válido"); return }
        bg({ FamilySync(this).join(code, store.myName) }) { r ->
            val (res, dev) = r.getOrNull() ?: run { toast("No hay conexión"); return@bg }
            when (res) {
                FamilySync.JoinResult.OK -> {
                    FamilySync(this).addLocal(dev!!, "Su móvil")
                    store.cloudEnabled = true
                    toast("Petición enviada. La persona tiene que aceptarla en su móvil.")
                    SyncJob.schedule(this); refresh(); render()
                }
                FamilySync.JoinResult.BAD_CODE -> toast("No encuentro ese código")
                FamilySync.JoinResult.EXPIRED -> toast("El código ha caducado. Pide uno nuevo.")
            }
        }
    }

    private fun paywall() {
        val plans = billing.plans
        if (plans.isEmpty()) { toast(billing.lastError.ifEmpty { "Los planes no están disponibles ahora mismo" }); return }
        val labels = plans.map { p -> "${p.price} ${Billing.periodText(p.period)}" + if (p.hasTrial) " · prueba gratis" else "" }
        dialog().setTitle("Fácil Familia")
            .setMessage("Gestiona a distancia los móviles de tu familia y recibe alertas. Se cancela cuando quieras desde Google Play. El lanzador de la persona mayor es siempre gratis y sin anuncios.")
            .setItems(labels.toTypedArray()) { _, w -> billing.buy(this, plans[w]) }
            .setNegativeButton("Ahora no", null).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_QR && resultCode == RESULT_OK) data?.getStringExtra(QrScanActivity.RES_CODE)?.let(::join)
    }

    companion object { private const val REQ_QR = 1 }
}
