package app.facil.launcher.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.facil.launcher.R
import app.facil.launcher.core.Tile
import java.io.ByteArrayOutputStream

object Images {
    /** Lee una imagen elegida por el usuario, la recorta en cuadrado y la deja en JPEG pequeño en base64. */
    fun encodePicked(ctx: Context, uri: Uri, size: Int = 256): String? = try {
        val src: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                val s = info.size
                val f = maxOf(1, minOf(s.width, s.height) / (size * 2))
                d.setTargetSize(s.width / f, s.height / f)
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            opts.inSampleSize = maxOf(1, minOf(opts.outWidth, opts.outHeight) / (size * 2))
            opts.inJustDecodeBounds = false
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: error("no image")
        }
        encodeSquare(src, size)
    } catch (e: Exception) { null }

    fun encodeSquare(src: Bitmap, size: Int = 256): String {
        val side = minOf(src.width, src.height)
        val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(sq, size, size, true)
        val out = ByteArrayOutputStream()
        var q = 82
        do {
            out.reset(); scaled.compress(Bitmap.CompressFormat.JPEG, q, out); q -= 10
        } while (out.size() * 4 / 3 > Tile.MAX_PHOTO_CHARS - 100 && q > 30)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private val cache = android.util.LruCache<String, Bitmap>(16)

    fun decode(b64: String): Bitmap? {
        if (b64.isEmpty()) return null
        val key = b64.length.toString() + b64.takeLast(32)
        cache.get(key)?.let { return it }
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            // Una foto llega de fuera (la familia): rechazamos dimensiones absurdas
            // antes de decodificar para que un JPEG manipulado no agote la memoria.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { cache.put(key, it) }
        } catch (e: Exception) { null }
    }
}

/** Foto o iniciales dentro de un círculo con borde. */
class Avatar(ctx: Context) : View(ctx) {
    var bitmap: Bitmap? = null; set(v) { field = v; invalidate() }
    var initials: String = ""; set(v) { field = v; invalidate() }
    var ring = Color.WHITE
    var fill = 0x33FFFFFF
    var fg = Color.WHITE
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val r = minOf(width, height) / 2f
        val cx = width / 2f; val cy = height / 2f
        val ringW = r * 0.07f
        p.shader = null; p.style = Paint.Style.FILL; p.color = ring
        c.drawCircle(cx, cy, r, p)
        val inner = r - ringW
        val bmp = bitmap
        if (bmp != null) {
            val sh = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val s = inner * 2 / minOf(bmp.width, bmp.height)
            sh.setLocalMatrix(Matrix().apply { setScale(s, s); postTranslate(cx - bmp.width * s / 2, cy - bmp.height * s / 2) })
            p.shader = sh
            c.drawCircle(cx, cy, inner, p)
            p.shader = null
        } else {
            p.color = fill; c.drawCircle(cx, cy, inner, p)
            p.color = fg; p.textAlign = Paint.Align.CENTER; p.typeface = BLACK
            p.textSize = inner * 0.9f
            c.drawText(initials, cx, cy - (p.descent() + p.ascent()) / 2, p)
        }
    }

    companion object {
        fun initialsOf(name: String) = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    }
}

/** Botón de la pantalla de inicio. */
@SuppressLint("ViewConstructor")
class TileView(private val act: BaseActivity, val tile: Tile, index: Int) : FrameLayout(act) {
    private val label: TextView
    private val icon: ImageView?
    var active = false
        set(v) { field = v; refreshActive() }
    private val baseColor = act.pal.tile(if (tile.color >= 0) tile.color else index)

    init {
        val pal = act.pal
        val r = act.dpf(30)
        background = ripple(rounded(baseColor, r, pal.tileStroke, act.dp(3)), true, r)
        isClickable = true; isFocusable = true
        contentDescription = describe()
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(act.dp(10), act.dp(10), act.dp(10), act.dp(12))
        }
        if (tile.type == Tile.CALL || tile.type == Tile.WHATSAPP && tile.phone.isNotEmpty() || tile.type == Tile.SMS_TO) {
            val av = Avatar(act).apply {
                bitmap = Images.decode(tile.photo)
                initials = Avatar.initialsOf(tile.label)
                ring = if (pal === Palette.CONTRAST) pal.onTile else Color.WHITE
                fg = pal.onTile
            }
            col.addView(av, LinearLayout.LayoutParams(0, 0, 1f).apply { width = LinearLayout.LayoutParams.MATCH_PARENT; bottomMargin = act.dp(6) })
            icon = null
            val badge = when (tile.type) { Tile.WHATSAPP -> R.drawable.ic_chat; Tile.SMS_TO -> R.drawable.ic_sms; else -> R.drawable.ic_phone }
            addView(ImageView(act).apply {
                setImageResource(badge); imageTintList = ColorStateList.valueOf(baseColor)
                background = rounded(if (pal === Palette.CONTRAST) pal.onTile else Color.WHITE, act.dpf(20))
                setPadding(act.dp(7), act.dp(7), act.dp(7), act.dp(7))
            }, LayoutParams(act.dp(40), act.dp(40), Gravity.TOP or Gravity.END).apply { setMargins(0, act.dp(10), act.dp(10), 0) })
        } else {
            icon = ImageView(act).apply {
                setImageResource(iconFor(tile.type)); imageTintList = ColorStateList.valueOf(pal.onTile)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            col.addView(icon, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = act.dp(6) })
        }
        label = act.text(tile.label.ifBlank { defaultLabel(tile.type) }, 24f, pal.onTile, BOLD, Gravity.CENTER).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            setAutoSizeTextTypeUniformWithConfiguration(14, (26 * act.scale).toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
        }
        col.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, act.dp(36 * act.scale + 30)))
        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun refreshActive() {
        if (tile.type != Tile.FLASHLIGHT) return
        label.text = if (active) "Apagar linterna" else tile.label.ifBlank { defaultLabel(tile.type) }
        val pal = act.pal
        val r = act.dpf(30)
        val color = if (active) Color.parseColor("#FFD43B") else baseColor
        val fg = if (active) Color.BLACK else pal.onTile
        background = ripple(rounded(color, r, pal.tileStroke, act.dp(3)), true, r)
        label.setTextColor(fg); icon?.imageTintList = ColorStateList.valueOf(fg)
    }

    private fun describe() = when (tile.type) {
        Tile.CALL -> "Llamar a ${tile.label}"
        Tile.WHATSAPP -> "WhatsApp ${tile.label}"
        Tile.SMS_TO -> "Mensaje a ${tile.label}"
        else -> tile.label.ifBlank { defaultLabel(tile.type) }
    }

    companion object {
        fun iconFor(type: String) = when (type) {
            Tile.CALL, Tile.DIALER -> R.drawable.ic_phone
            Tile.WHATSAPP -> R.drawable.ic_chat
            Tile.SMS_TO, Tile.MESSAGES -> R.drawable.ic_sms
            Tile.CAMERA -> R.drawable.ic_camera
            Tile.GALLERY -> R.drawable.ic_photos
            Tile.FLASHLIGHT -> R.drawable.ic_flashlight
            Tile.ALL_APPS -> R.drawable.ic_apps
            Tile.TIME -> R.drawable.ic_clock
            else -> R.drawable.ic_app
        }

        fun defaultLabel(type: String) = when (type) {
            Tile.CALL -> "Llamar"
            Tile.WHATSAPP -> "WhatsApp"
            Tile.SMS_TO -> "Mensaje"
            Tile.CAMERA -> "Cámara"
            Tile.GALLERY -> "Fotos"
            Tile.DIALER -> "Teléfono"
            Tile.MESSAGES -> "Mensajes"
            Tile.FLASHLIGHT -> "Linterna"
            Tile.ALL_APPS -> "Aplicaciones"
            Tile.TIME -> "¿Qué hora es?"
            else -> "Aplicación"
        }

        fun typeName(type: String) = when (type) {
            Tile.CALL -> "Llamar a una persona"
            Tile.WHATSAPP -> "WhatsApp (con una persona o la app)"
            Tile.SMS_TO -> "Mensaje SMS a una persona"
            Tile.CAMERA -> "Cámara"
            Tile.GALLERY -> "Fotos"
            Tile.DIALER -> "Teclado del teléfono"
            Tile.MESSAGES -> "Mensajes"
            Tile.FLASHLIGHT -> "Linterna"
            Tile.ALL_APPS -> "Lista de aplicaciones"
            Tile.TIME -> "Decir la hora en voz alta"
            else -> "Otra aplicación"
        }
    }
}

/**
 * Botón que hay que mantener pulsado (para emergencias y ajustes): se va
 * llenando y solo se activa al completarse. Evita toques sin querer.
 */
@SuppressLint("ViewConstructor")
class HoldButton(
    private val act: BaseActivity,
    private val labelText: String,
    private val fillColor: Int,
    private val fg: Int,
    private val holdMs: Long = 1500,
    private val onDone: () -> Unit,
) : View(act) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f
    private var anim: ValueAnimator? = null
    var hint: (() -> Unit)? = null

    init {
        isClickable = true; isFocusable = true
        contentDescription = "$labelText. Mantén pulsado."
        minimumHeight = act.dp(76)
    }

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        p.style = Paint.Style.FILL
        p.color = fillColor; p.alpha = 255
        c.drawRoundRect(rect, r, r, p)
        if (progress > 0f) {
            p.color = Color.WHITE; p.alpha = 70
            c.save(); c.clipRect(0f, 0f, width * progress, height.toFloat())
            c.drawRoundRect(rect, r, r, p); c.restore()
        }
        p.alpha = 255; p.color = fg; p.typeface = BLACK; p.textAlign = Paint.Align.CENTER
        p.textSize = act.dpf(22 * act.scale.coerceAtMost(1.2f))
        val label = if (progress > 0f) "Sigue pulsando…" else labelText
        c.drawText(label, width / 2f, height / 2f - (p.descent() + p.ascent()) / 2, p)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { start(); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val completed = progress >= 1f
                cancel()
                if (!completed && e.actionMasked == MotionEvent.ACTION_UP) hint?.invoke()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    /** Con TalkBack un doble toque equivale a mantener pulsado. */
    override fun performClick(): Boolean { super.performClick(); onDone(); return true }

    private fun start() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = holdMs; interpolator = LinearInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (!cancelled) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onDone()
                    }
                }
            })
            start()
        }
    }

    private fun cancel() {
        anim?.cancel(); anim = null
        progress = 0f; invalidate()
    }
}
