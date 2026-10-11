package app.facil.launcher.system

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import app.facil.launcher.core.Phone
import app.facil.launcher.core.Tile

/** Abre lo que corresponde a cada botón. Solo intents explícitos o del sistema. */
object Actions {

    fun start(ctx: Context, intent: Intent?): Boolean {
        if (intent == null) return notFound(ctx)
        return try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            notFound(ctx)
        } catch (e: SecurityException) {
            notFound(ctx)
        }
    }

    private fun notFound(ctx: Context): Boolean {
        Toast.makeText(ctx, "No hay ninguna aplicación para esto", Toast.LENGTH_LONG).show()
        return false
    }

    fun canCallDirectly(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun call(ctx: Context, phone: String): Boolean {
        val p = Phone.clean(phone)
        if (!Phone.isValid(p)) return false
        val uri = Uri.fromParts("tel", p, null)
        val action = if (canCallDirectly(ctx)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return start(ctx, Intent(action, uri))
    }

    fun dialEmergency(ctx: Context, number: String = "112") =
        start(ctx, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    fun whatsapp(ctx: Context, phone: String): Boolean {
        val uri = if (Phone.isValid(phone)) Uri.parse("https://wa.me/" + Phone.forWhatsApp(phone)) else null
        val pm = ctx.packageManager
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            if (pm.getLaunchIntentForPackage(pkg) == null) continue
            val i = if (uri != null) Intent(Intent.ACTION_VIEW, uri).setPackage(pkg) else pm.getLaunchIntentForPackage(pkg)
            return start(ctx, i)
        }
        Toast.makeText(ctx, "WhatsApp no está instalado", Toast.LENGTH_LONG).show()
        return false
    }

    fun smsTo(ctx: Context, phone: String) =
        start(ctx, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", Phone.clean(phone), null)))

    fun camera(ctx: Context) = start(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    fun gallery(ctx: Context): Boolean {
        val pm = ctx.packageManager
        val selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        if (selector.resolveActivity(pm) != null) return start(ctx, selector)
        val photos = pm.getLaunchIntentForPackage("com.google.android.apps.photos")
        return start(ctx, photos ?: Intent(Intent.ACTION_VIEW).setType("image/*"))
    }

    fun dialer(ctx: Context) = start(ctx, Intent(Intent.ACTION_DIAL))

    fun messages(ctx: Context): Boolean {
        val selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
        return start(ctx, selector)
    }

    fun app(ctx: Context, pkg: String) = start(ctx, ctx.packageManager.getLaunchIntentForPackage(pkg))

    // --- Linterna ---
    @Volatile var torchOn = false
        private set
    private var torchListening = false
    val torchListeners = mutableSetOf<() -> Unit>()

    private fun torchCameraId(cm: CameraManager): String? = try {
        cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) { null }

    fun listenTorch(ctx: Context) {
        if (torchListening) return
        val cm = ctx.svc<CameraManager>()
        val id = torchCameraId(cm) ?: return
        torchListening = true
        cm.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) { torchOn = enabled; torchListeners.toList().forEach { it() } }
            }
        }, Handler(Looper.getMainLooper()))
    }

    fun toggleTorch(ctx: Context): Boolean {
        val cm = ctx.svc<CameraManager>()
        val id = torchCameraId(cm) ?: run {
            Toast.makeText(ctx, "Este móvil no tiene linterna", Toast.LENGTH_LONG).show(); return false
        }
        return try { cm.setTorchMode(id, !torchOn); true } catch (e: Exception) { false }
    }

    fun isAvailable(ctx: Context, t: Tile): Boolean = when (t.type) {
        Tile.APP -> ctx.packageManager.getLaunchIntentForPackage(t.pkg) != null
        else -> true
    }
}
