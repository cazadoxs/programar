package app.facil.launcher.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import app.facil.launcher.data.Store

/** Primera vez: ¿eres la persona que usará el móvil o un familiar que ayuda? */
class WelcomeActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        setRoot(vbox {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(text("Fácil", 56f, pal.accent, BLACK, Gravity.CENTER), lp(MATCH, WRAP).apply { topMargin = dp(24) })
            addView(text("El móvil, sin complicaciones", 24f, pal.text, MEDIUM, Gravity.CENTER))
            addView(space(24))
            addView(text("Sin anuncios. Botones grandes. Y tu familia puede ayudarte desde su móvil.", 20f, pal.sub, gravity = Gravity.CENTER), lp(MATCH, 0, 1f))
            addView(button("Voy a usar este móvil yo", pal.accent, pal.onAccent, 24f) { finishAs(Store.ROLE_SENIOR) }, lp().apply { bottomMargin = dp(14) })
            addView(ghostButton("Soy un familiar y quiero ayudar a alguien") { finishAs(Store.ROLE_FAMILY) }, lp().apply { bottomMargin = dp(10) })
        })
    }

    private fun finishAs(role: String) {
        store.role = role
        store.onboarded = true
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        if (role == Store.ROLE_FAMILY) startActivity(Intent(this, FamilyActivity::class.java))
        else if (store.config.sosPhone.isEmpty()) startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }
}
