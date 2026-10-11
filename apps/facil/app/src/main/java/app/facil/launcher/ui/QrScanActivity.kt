package app.facil.launcher.ui

import app.facil.launcher.system.svc
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import app.facil.launcher.core.Codes
import com.google.zxing.PlanarYUVLuminanceSource
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lector de QR con Camera2 + ZXing, sin librerías de Google Play Services.
 * Solo procesa la imagen en memoria para buscar el código: no guarda fotos.
 */
class QrScanActivity : BaseActivity() {
    private lateinit var texture: TextureView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val busy = AtomicBoolean(false)
    private var done = false
    private var previewSize = Size(1280, 720)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        texture = TextureView(this)
        val frame = FrameLayout(this).apply {
            addView(texture, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(android.view.View(this@QrScanActivity).apply {
                background = rounded(android.graphics.Color.TRANSPARENT, dpf(28), android.graphics.Color.WHITE, dp(4))
            }, FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER))
        }
        setRoot(vbox {
            addView(title("Apunta al código QR"))
            addView(text("Está en el móvil de tu familiar, en Ajustes › Familia.", 18f, pal.sub))
            addView(frame, lp(MATCH, 0, 1f).apply { topMargin = dp(12); bottomMargin = dp(12) })
            addView(ghostButton("Cancelar") { finish() })
        })
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1); return
        }
        start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            toast("Sin permiso de cámara puedes escribir el código a mano"); finish()
        }
    }

    override fun onPause() { stop(); super.onPause() }

    private fun start() {
        thread = HandlerThread("qr").also { it.start() }
        handler = Handler(thread!!.looper)
        if (texture.isAvailable) openCamera()
        else texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) = openCamera()
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) = fitPreview()
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
        }
    }

    private fun stop() {
        try { session?.close() } catch (e: Exception) { }
        try { camera?.close() } catch (e: Exception) { }
        try { reader?.close() } catch (e: Exception) { }
        session = null; camera = null; reader = null
        thread?.quitSafely(); thread = null; handler = null
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cm = svc<CameraManager>()
        val id = try {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                ?: cm.cameraIdList.firstOrNull()
        } catch (e: Exception) { null } ?: run { toast("No se encuentra la cámara"); finish(); return }
        val map = cm.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
        previewSize = sizes.filter { it.width <= 1600 && it.height <= 1200 }.maxByOrNull { it.width * it.height } ?: Size(640, 480)
        fitPreview()
        try {
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) { camera = c; createSession(c) }
                override fun onDisconnected(c: CameraDevice) { c.close() }
                override fun onError(c: CameraDevice, error: Int) { c.close(); main.post { toast("No se puede usar la cámara"); finish() } }
            }, handler)
        } catch (e: Exception) { toast("No se puede usar la cámara"); finish() }
    }

    @Suppress("DEPRECATION")
    private fun createSession(c: CameraDevice) {
        val st = texture.surfaceTexture ?: return
        st.setDefaultBufferSize(previewSize.width, previewSize.height)
        val preview = Surface(st)
        val r = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 2)
        reader = r
        r.setOnImageAvailableListener({ rd ->
            val img = rd.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (done || !busy.compareAndSet(false, true)) { img.close(); return@setOnImageAvailableListener }
            val w = img.width; val h = img.height
            val plane = img.planes[0]
            val buf = plane.buffer
            val rowStride = plane.rowStride
            val data = ByteArray(w * h)
            for (row in 0 until h) {
                buf.position(row * rowStride)
                buf.get(data, row * w, w)
            }
            img.close()
            BG.execute {
                val text = Qr.decode(PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false))
                val code = text?.let(Codes::normalize)
                if (code != null && !done) {
                    done = true
                    main.post { setResult(RESULT_OK, Intent().putExtra(RES_CODE, code)); finish() }
                }
                busy.set(false)
            }
        }, handler)
        try {
            c.createCaptureSession(listOf(preview, r.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    val req = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(preview); addTarget(r.surface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }
                    try { s.setRepeatingRequest(req.build(), null, handler) } catch (e: Exception) { }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { main.post { toast("No se puede usar la cámara"); finish() } }
            }, handler)
        } catch (e: Exception) { main.post { toast("No se puede usar la cámara"); finish() } }
    }

    /** Recorta la vista previa para que no salga estirada. */
    private fun fitPreview() {
        val vw = texture.width.toFloat(); val vh = texture.height.toFloat()
        if (vw == 0f || vh == 0f) return
        val content = previewSize.height.toFloat() / previewSize.width // vertical
        val (dw, dh) = if (vw / vh > content) vw to vw / content else vh * content to vh
        texture.setTransform(Matrix().apply { setScale(dw / vw, dh / vh, vw / 2, vh / 2) })
    }

    companion object { const val RES_CODE = "code" }
}
