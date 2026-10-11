package app.facil.launcher.ui

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import app.facil.launcher.system.Actions

/**
 * Lista de aplicaciones, grande y en orden alfabético. Si la familia ha
 * elegido qué apps se ven, solo salen esas. En modo selección devuelve el
 * paquete elegido (para crear un botón).
 */
class AppsActivity : BaseActivity() {
    private val picking get() = intent.getBooleanExtra(EXTRA_PICK, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = vbox()
        val root = vbox {
            addView(hbox {
                addView(title(if (picking) "Elige una aplicación" else "Aplicaciones"), lp(0, WRAP, 1f))
            })
            addView(scroll(list), lp(MATCH, 0, 1f))
            addView(button("Volver", pal.surface, pal.text) { finish() }.apply {
                background = ripple(rounded(pal.surface, dpf(22), pal.line, dp(2)), pal.dark, dpf(22))
            }, lp().apply { topMargin = dp(12) })
        }
        setRoot(root)
        bg({ loadApps() }) { r ->
            val apps = r.getOrDefault(emptyList())
            if (apps.isEmpty()) list.addView(text("No hay aplicaciones para mostrar", 20f, pal.sub))
            apps.forEach { (label, info) -> list.addView(appRow(label, info), lp().apply { bottomMargin = dp(10) }) }
        }
    }

    private fun loadApps(): List<Pair<String, ResolveInfo>> {
        val pm = packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val allowed = store.config.allowedApps.toSet()
        return pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != packageName }
            .filter { picking || allowed.isEmpty() || it.activityInfo.packageName in allowed }
            .distinctBy { it.activityInfo.packageName }
            .map { it.loadLabel(pm).toString() to it }
            .sortedBy { it.first.lowercase() }
    }

    private fun appRow(label: String, info: ResolveInfo): LinearLayout = hbox {
        background = ripple(rounded(pal.surface, dpf(22), pal.line, dp(1)), pal.dark, dpf(22))
        setPadding(dp(16), dp(12), dp(16), dp(12))
        minimumHeight = dp(80)
        addView(ImageView(this@AppsActivity).apply { setImageDrawable(info.loadIcon(packageManager)) },
            lp(dp(52), dp(52)).apply { marginEnd = dp(16) })
        addView(text(label, 24f, pal.text, BOLD), lp(0, WRAP, 1f))
        isClickable = true
        setOnClickListener {
            val pkg = info.activityInfo.packageName
            if (picking) {
                setResult(RESULT_OK, Intent().putExtra(RES_PKG, pkg).putExtra(RES_LABEL, label))
                finish()
            } else Actions.app(this@AppsActivity, pkg)
        }
    }

    companion object {
        const val EXTRA_PICK = "pick"
        const val RES_PKG = "pkg"
        const val RES_LABEL = "label"
    }
}
