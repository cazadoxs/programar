package com.cazadoxs.pasalo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * El motor de Flutter se guarda en caché y no se destruye con la actividad:
 * si el usuario cierra la pantalla mientras hay una transferencia en curso,
 * el servicio en primer plano mantiene vivo el proceso y Dart sigue enviando.
 */
class MainActivity : FlutterActivity() {
    companion object {
        const val ENGINE_ID = "pasalo_engine"
    }

    private var channel: MethodChannel? = null

    override fun provideFlutterEngine(context: Context): FlutterEngine {
        FlutterEngineCache.getInstance().get(ENGINE_ID)?.let { return it }
        val engine = FlutterEngine(context.applicationContext)
        engine.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
        FlutterEngineCache.getInstance().put(ENGINE_ID, engine)
        return engine
    }

    override fun shouldDestroyEngineWithHost(): Boolean = false

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val bridge = NativeBridge.get(applicationContext)
        bridge.initialLink = intent?.data?.toString() ?: bridge.initialLink
        val ch = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "pasalo/native")
        ch.setMethodCallHandler(bridge)
        channel = ch
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, "pasalo/bt")
            .setStreamHandler(bridge.bt)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) intent?.data?.let { channel?.invokeMethod("onLink", it.toString()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { channel?.invokeMethod("onLink", it.toString()) }
    }
}
