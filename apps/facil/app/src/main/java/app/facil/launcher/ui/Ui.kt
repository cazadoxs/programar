package app.facil.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.facil.launcher.core.LauncherConfig
import app.facil.launcher.data.Store
import java.util.concurrent.Executors

/** Paleta de cada tema. Contrastes comprobados para texto grande (WCAG AA o más). */
data class Palette(
    val bg: Int,
    val surface: Int,
    val text: Int,
    val sub: Int,
    val line: Int,
    val accent: Int,
    val onAccent: Int,
    val sos: Int,
    val onSos: Int,
    val warn: Int,
    val onWarn: Int,
    val tiles: IntArray,
    val onTile: Int,
    val tileStroke: Int,
    val dark: Boolean,
) {
    fun tile(index: Int) = tiles[((index % tiles.size) + tiles.size) % tiles.size]

    companion object {
        private fun c(s: String) = Color.parseColor(s)

        val CREAM = Palette(
            bg = c("#FBF4E9"), surface = c("#FFFFFF"), text = c("#221B16"), sub = c("#6A5D53"), line = c("#E8DCCB"),
            accent = c("#C2410C"), onAccent = Color.WHITE, sos = c("#B91C1C"), onSos = Color.WHITE,
            warn = c("#FFE7B3"), onWarn = c("#3D2A00"),
            tiles = intArrayOf(c("#1F6F54"), c("#177245"), c("#2F5597"), c("#A8482A"), c("#5B3E8C"), c("#8A6100"), c("#1C6B78"), c("#8A2F55")),
            onTile = Color.WHITE, tileStroke = Color.TRANSPARENT, dark = false,
        )
        val NIGHT = Palette(
            bg = c("#121418"), surface = c("#1D2027"), text = c("#F6F1E9"), sub = c("#B4ADA4"), line = c("#30343D"),
            accent = c("#FB923C"), onAccent = c("#1A0E00"), sos = c("#DC2626"), onSos = Color.WHITE,
            warn = c("#5A4210"), onWarn = c("#FFF1C7"),
            tiles = intArrayOf(c("#1F7A5C"), c("#1A7F4B"), c("#3863AD"), c("#B5532F"), c("#6B4BA3"), c("#946A00"), c("#1F7787"), c("#9A3762")),
            onTile = Color.WHITE, tileStroke = Color.TRANSPARENT, dark = true,
        )
        val CONTRAST = Palette(
            bg = Color.BLACK, surface = Color.BLACK, text = c("#FFE600"), sub = Color.WHITE, line = c("#FFE600"),
            accent = c("#FFE600"), onAccent = Color.BLACK, sos = c("#FF1A1A"), onSos = Color.WHITE,
            warn = c("#FFE600"), onWarn = Color.BLACK,
            tiles = intArrayOf(Color.BLACK), onTile = c("#FFE600"), tileStroke = c("#FFE600"), dark = true,
        )

        fun of(theme: String) = when (theme) {
            LauncherConfig.THEME_NIGHT -> NIGHT
            LauncherConfig.THEME_CONTRAST -> CONTRAST
            else -> CREAM
        }
    }
}

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Number): Float = v.toFloat() * resources.displayMetrics.density

fun rounded(color: Int, radius: Float, stroke: Int = Color.TRANSPARENT, strokeW: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (strokeW > 0 && stroke != Color.TRANSPARENT) setStroke(strokeW, stroke)
    }

fun ripple(content: Drawable, dark: Boolean, radius: Float): Drawable =
    RippleDrawable(
        ColorStateList.valueOf(if (dark) 0x33FFFFFF else 0x33000000),
        content, rounded(Color.BLACK, radius),
    )

val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
val BLACK: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)

fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
    LinearLayout.LayoutParams(w, h, weight)

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Base de todas las pantallas: aplica el tema y el tamaño de letra elegidos,
 * respeta las barras del sistema (Android 15 dibuja de borde a borde) y da
 * un ejecutor de fondo para red/disco.
 */
abstract class BaseActivity : Activity() {
    lateinit var pal: Palette
    var scale = 1f
    val store by lazy { Store.get(this) }
    protected val main = Handler(Looper.getMainLooper())
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadTheme()
    }

    open fun themeConfig(): LauncherConfig = store.config

    fun loadTheme() {
        val c = themeConfig()
        pal = Palette.of(c.theme)
        scale = c.textScale
        window.decorView.setBackgroundColor(pal.bg)
        styleBars()
    }

    @Suppress("DEPRECATION")
    private fun styleBars() {
        val w = window
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
            val light = !pal.dark
            val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(if (light) flags else 0, flags)
            if (Build.VERSION.SDK_INT < 35) { w.statusBarColor = Color.TRANSPARENT; w.navigationBarColor = Color.TRANSPARENT }
        } else {
            w.statusBarColor = pal.bg
            w.navigationBarColor = pal.bg
            var f = w.decorView.systemUiVisibility
            f = if (!pal.dark) f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            else f and (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
            w.decorView.systemUiVisibility = f
        }
    }

    /** Pone [root] como contenido con márgenes para las barras del sistema. */
    @Suppress("DEPRECATION")
    fun setRoot(root: View) {
        root.setBackgroundColor(pal.bg)
        val pad = dp(16)
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                v.setPadding(i.left + pad, i.top + pad / 2, i.right + pad, i.bottom + pad)
            } else {
                v.setPadding(insets.systemWindowInsetLeft + pad, insets.systemWindowInsetTop + pad / 2,
                    insets.systemWindowInsetRight + pad, insets.systemWindowInsetBottom + pad)
            }
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
    }

    override fun onDestroy() { destroyed = true; super.onDestroy() }

    /** Ejecuta [work] en segundo plano y [done] en el hilo principal (si la pantalla sigue viva). */
    fun <T> bg(work: () -> T, done: (Result<T>) -> Unit) {
        BG.execute {
            val r = runCatching(work)
            main.post { if (!destroyed && !isFinishing) done(r) }
        }
    }

    // ----- Constructores de vistas -----

    fun sp(v: Float) = v * scale

    fun text(s: CharSequence, size: Float = 20f, color: Int = pal.text, face: Typeface = Typeface.DEFAULT, gravity: Int = Gravity.START): TextView =
        TextView(this).apply {
            text = s; setTextColor(color); typeface = face; this.gravity = gravity
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(size))
            setLineSpacing(0f, 1.1f)
        }

    fun title(s: String) = text(s, 30f, pal.text, BLACK).apply { setPadding(0, dp(8), 0, dp(4)) }

    fun vbox(block: LinearLayout.() -> Unit = {}) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; block() }
    fun hbox(block: LinearLayout.() -> Unit = {}) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; block()
    }

    fun scroll(child: View) = ScrollView(this).apply {
        isFillViewport = true; clipToPadding = false
        addView(child, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    /** Botón grande con forma de píldora. */
    fun button(label: String, bgColor: Int = pal.accent, fg: Int = pal.onAccent, size: Float = 22f, onClick: () -> Unit): TextView =
        text(label, size, fg, BOLD, Gravity.CENTER).apply {
            val r = dpf(22)
            val stroke = if (bgColor == pal.bg || bgColor == Color.TRANSPARENT || pal == Palette.CONTRAST) pal.line else Color.TRANSPARENT
            background = ripple(rounded(bgColor, r, stroke, dp(2)), pal.dark || bgColor != pal.surface, r)
            minHeight = dp(64)
            setPadding(dp(20), dp(14), dp(20), dp(14))
            isClickable = true; isFocusable = true
            setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick() }
        }

    fun ghostButton(label: String, onClick: () -> Unit) = button(label, pal.surface, pal.text, 20f, onClick).apply {
        background = ripple(rounded(pal.surface, dpf(22), pal.line, dp(2)), pal.dark, dpf(22))
    }

    /** Tarjeta blanca redondeada. */
    fun card(block: LinearLayout.() -> Unit) = vbox {
        background = rounded(pal.surface, dpf(24), pal.line, dp(if (pal == Palette.CONTRAST) 2 else 1))
        setPadding(dp(18), dp(16), dp(18), dp(16))
        block()
    }

    /** Fila de ajustes: título + subtítulo, pulsable. */
    fun row(titleText: String, subtitle: String? = null, icon: Int = 0, onClick: (() -> Unit)? = null): LinearLayout = hbox {
        setPadding(dp(4), dp(14), dp(4), dp(14))
        minimumHeight = dp(64)
        if (icon != 0) addView(ImageView(this@BaseActivity).apply {
            setImageResource(icon); imageTintList = ColorStateList.valueOf(pal.accent)
        }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(14) })
        addView(vbox {
            addView(text(titleText, 20f, pal.text, MEDIUM))
            if (!subtitle.isNullOrEmpty()) addView(text(subtitle, 16f, pal.sub))
        }, lp(0, WRAP, 1f))
        if (onClick != null) {
            addView(text("›", 30f, pal.sub))
            background = ripple(rounded(Color.TRANSPARENT, dpf(12)), pal.dark, dpf(12))
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    fun divider() = View(this).apply { setBackgroundColor(pal.line) }.also { it.layoutParams = lp(MATCH, dp(1)) }

    fun space(h: Int) = View(this).apply { layoutParams = lp(MATCH, dp(h)) }

    fun input(hint: String, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, max: Int = 40) =
        EditText(this).apply {
            setText(value); this.hint = hint; inputType = type
            setTextColor(pal.text); setHintTextColor(pal.sub)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(22f))
            filters = arrayOf(android.text.InputFilter.LengthFilter(max))
            background = rounded(pal.surface, dpf(16), pal.line, dp(2))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            minHeight = dp(60)
        }

    fun dialog(): AlertDialog.Builder =
        AlertDialog.Builder(this, if (pal.dark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)

    /** Diálogo de confirmación grande, con dos botones claros. */
    fun confirm(question: String, yes: String = "Sí", no: String = "No", detail: String? = null, onYes: () -> Unit) {
        val box = vbox {
            setPadding(dp(24), dp(24), dp(24), dp(16))
            addView(text(question, 28f, pal.text, BOLD, Gravity.CENTER))
            if (detail != null) addView(text(detail, 18f, pal.sub, gravity = Gravity.CENTER).apply { setPadding(0, dp(8), 0, 0) })
        }
        lateinit var d: AlertDialog
        val buttons = vbox {
            setPadding(dp(20), 0, dp(20), dp(20))
            addView(button(yes, pal.tile(0), pal.onTile, 26f) { d.dismiss(); onYes() }, lp().apply { bottomMargin = dp(12) })
            addView(ghostButton(no) { d.dismiss() })
        }
        box.addView(space(16)); box.addView(buttons)
        box.background = rounded(pal.bg, dpf(28))
        d = dialog().setView(box).create()
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        d.show()
    }

    fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show()

    companion object {
        val BG = Executors.newFixedThreadPool(2)
    }
}
