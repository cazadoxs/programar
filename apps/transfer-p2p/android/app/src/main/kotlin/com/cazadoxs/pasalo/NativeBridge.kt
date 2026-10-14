package com.cazadoxs.pasalo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/** Todo lo nativo que la parte Dart necesita, independiente de la actividad. */
class NativeBridge private constructor(private val ctx: Context) : MethodChannel.MethodCallHandler {
    companion object {
        @Volatile private var instance: NativeBridge? = null
        fun get(ctx: Context): NativeBridge =
            instance ?: synchronized(this) { instance ?: NativeBridge(ctx.applicationContext).also { instance = it } }
    }

    var initialLink: String? = null
    val bt = BluetoothBridge(ctx)
    private val main = Handler(Looper.getMainLooper())
    private var multicastLock: WifiManager.MulticastLock? = null
    private var p2pChannel: WifiP2pManager.Channel? = null

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "getInitialLink" -> { result.success(initialLink); initialLink = null }
                "downloadsDir" -> result.success(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
                "deviceName" -> result.success(deviceName())
                "sdkInt" -> result.success(Build.VERSION.SDK_INT)
                "keepAliveUpdate" -> {
                    TransferService.update(ctx, call.argument<String>("title") ?: "Pásalo",
                        call.argument<String>("text") ?: "", call.argument<Int>("percent") ?: -1)
                    result.success(true)
                }
                "keepAliveStart" -> {
                    val i = Intent(ctx, TransferService::class.java)
                        .putExtra("title", call.argument<String>("title") ?: "Pásalo")
                        .putExtra("text", call.argument<String>("text") ?: "")
                        .putExtra("percent", call.argument<Int>("percent") ?: -1)
                    if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
                    result.success(true)
                }
                "keepAliveStop" -> { ctx.stopService(Intent(ctx, TransferService::class.java)); result.success(true) }
                "multicastLock" -> { setMulticast(call.argument<Boolean>("on") == true); result.success(true) }
                "scanFile" -> {
                    MediaScannerConnection.scanFile(ctx, arrayOf(call.argument<String>("path")), null, null)
                    result.success(true)
                }
                "wifiDirectCreate" -> wifiDirectCreate(result)
                "wifiDirectRemove" -> wifiDirectRemove(result)
                "btAvailable" -> result.success(bt.available())
                "btBonded" -> result.success(bt.bonded())
                "btListen" -> { bt.listen(); result.success(true) }
                "btConnect" -> { bt.connect(call.argument<String>("address")!!); result.success(true) }
                "btWrite" -> bt.write(call.argument<ByteArray>("data")!!, result)
                "btClose" -> { bt.close(); result.success(true) }
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error("native", e.message ?: e.toString(), null)
        }
    }

    private fun deviceName(): String =
        Settings.Global.getString(ctx.contentResolver, "device_name") ?: "${Build.MANUFACTURER} ${Build.MODEL}"

    private fun setMulticast(on: Boolean) {
        val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (on) {
            if (multicastLock == null) multicastLock = wifi.createMulticastLock("pasalo").apply {
                setReferenceCounted(false); acquire()
            }
        } else {
            multicastLock?.release(); multicastLock = null
        }
    }

    private fun p2p(): Pair<WifiP2pManager, WifiP2pManager.Channel> {
        val mgr = ctx.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
            ?: throw IllegalStateException("Este móvil no tiene Wi-Fi Direct.")
        val ch = p2pChannel ?: mgr.initialize(ctx, Looper.getMainLooper(), null).also { p2pChannel = it }
        return mgr to ch
    }

    /** Crea un grupo Wi-Fi Direct: este móvil hace de punto de acceso (192.168.49.1). */
    @SuppressLint("MissingPermission")
    private fun wifiDirectCreate(result: MethodChannel.Result) {
        val (mgr, ch) = p2p()
        val create = {
            mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = pollGroup(mgr, ch, result, 0)
                override fun onFailure(reason: Int) = result.error(
                    "p2p", "No se pudo crear el grupo Wi-Fi Direct (código $reason). ¿Está el Wi-Fi activado?", null)
            })
        }
        mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { main.postDelayed({ create() }, 300) }
            override fun onFailure(reason: Int) { create() }
        })
    }

    @SuppressLint("MissingPermission")
    private fun pollGroup(mgr: WifiP2pManager, ch: WifiP2pManager.Channel, result: MethodChannel.Result, n: Int) {
        mgr.requestGroupInfo(ch) { g ->
            if (g != null && !g.networkName.isNullOrEmpty() && !g.passphrase.isNullOrEmpty()) {
                result.success(mapOf("ssid" to g.networkName, "pass" to g.passphrase, "ip" to "192.168.49.1"))
            } else if (n < 20) {
                main.postDelayed({ pollGroup(mgr, ch, result, n + 1) }, 500)
            } else {
                result.error("p2p", "El grupo Wi-Fi Direct no respondió.", null)
            }
        }
    }

    private fun wifiDirectRemove(result: MethodChannel.Result) {
        val (mgr, ch) = p2p()
        mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = result.success(true)
            override fun onFailure(reason: Int) = result.success(false)
        })
    }
}
