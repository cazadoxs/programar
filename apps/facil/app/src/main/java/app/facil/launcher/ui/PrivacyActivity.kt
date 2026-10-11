package app.facil.launcher.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import app.facil.launcher.remote.FamilySync
import app.facil.launcher.remote.Firebase
import app.facil.launcher.remote.SeniorSync
import app.facil.launcher.system.Notifs
import app.facil.launcher.system.SyncJob

/** Qué datos hay, dónde están y cómo borrarlos (requisito de Play y del RGPD). */
class PrivacyActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = vbox {
            addView(text(
                "Fácil no tiene anuncios ni rastreadores. Todo lo que configuras se guarda en este móvil.\n\n" +
                "Solo si añades a un familiar, se guardan en un servidor seguro (Google Firebase, en la UE) tus botones y ajustes, " +
                "el nivel de batería, la hora a la que usaste el móvil por última vez y si pulsaste «Ayuda». " +
                "Nunca se envían tus mensajes, llamadas, ubicación ni fotos (las fotos de los botones van reducidas, solo dentro de la configuración).\n\n" +
                "Puedes dejar de compartir cuando quieras, quitar a un familiar o borrar todo.", 19f, pal.text))
            addView(space(16))
            addView(ghostButton("Leer la política de privacidad") {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(POLICY_URL))) }
            })
            addView(space(10))
            addView(button("Borrar todos mis datos", pal.sos, pal.onSos, 22f) {
                confirm("¿Borrar todo?", "Sí, borrar", "No", "Se elimina lo guardado en la nube y se desvincula a tu familia. Tus botones en este móvil se mantienen.") { wipe() }
            })
        }
        setRoot(vbox {
            addView(title("Privacidad y datos"))
            addView(scroll(box), lp(MATCH, 0, 1f))
            addView(button("Volver", pal.surface, pal.text) { finish() }, lp().apply { topMargin = dp(12) })
        })
    }

    private fun wipe() {
        bg({
            if (Firebase.get(this).configured) {
                if (store.isFamily) FamilySync(this).deleteEverything() else SeniorSync(this).deleteEverything()
            }
            store.clearCloud()
        }) { r ->
            Notifs.updateSharing(this)
            SyncJob.schedule(this)
            toast(if (r.isSuccess) "Datos borrados" else "No se pudo borrar ahora. Comprueba la conexión e inténtalo otra vez.")
        }
    }

    companion object { const val POLICY_URL = "https://cazadoxs.github.io/programar/facil/privacidad.html" }
}
