package com.cazadoxs.pasalo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.UUID
import java.util.concurrent.Executors

/** Bluetooth clásico (RFCOMM) entre dos Android ya emparejados. */
@SuppressLint("MissingPermission")
class BluetoothBridge(private val ctx: Context) : EventChannel.StreamHandler {
    private val uuid: UUID = UUID.fromString("6f3b1a52-8c1e-4f7a-9d2c-5a50a1c0de01")
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    private var sink: EventChannel.EventSink? = null
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var socket: BluetoothSocket? = null

    private fun adapter(): BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    fun available(): Boolean = adapter()?.isEnabled == true

    fun bonded(): List<Map<String, String>> =
        adapter()?.bondedDevices?.map { mapOf("name" to (it.name ?: it.address), "address" to it.address) } ?: emptyList()

    fun listen() {
        close()
        val a = adapter() ?: throw IllegalStateException("Este dispositivo no tiene Bluetooth.")
        Thread {
            try {
                val srv = a.listenUsingRfcommWithServiceRecord("Pasalo", uuid)
                server = srv
                val s = srv.accept()
                srv.close()
                server = null
                connected(s)
            } catch (e: Exception) {
                if (server != null) emit(mapOf("type" to "error", "message" to (e.message ?: "Error de Bluetooth")))
            }
        }.start()
    }

    fun connect(address: String) {
        close()
        val a = adapter() ?: throw IllegalStateException("Este dispositivo no tiene Bluetooth.")
        Thread {
            try {
                a.cancelDiscovery()
                val s = a.getRemoteDevice(address).createRfcommSocketToServiceRecord(uuid)
                s.connect()
                connected(s)
            } catch (e: Exception) {
                emit(mapOf("type" to "error", "message" to "No se pudo conectar por Bluetooth: ${e.message}"))
            }
        }.start()
    }

    private fun connected(s: BluetoothSocket) {
        socket = s
        emit(mapOf("type" to "connected", "name" to (s.remoteDevice.name ?: s.remoteDevice.address)))
        val buf = ByteArray(64 * 1024)
        try {
            val input = s.inputStream
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                emit(mapOf("type" to "data", "data" to buf.copyOf(n)))
            }
        } catch (_: Exception) {
        }
        emit(mapOf("type" to "closed"))
    }

    fun write(data: ByteArray, result: MethodChannel.Result) {
        writer.execute {
            try {
                val s = socket ?: throw IllegalStateException("Bluetooth no conectado.")
                s.outputStream.write(data)
                main.post { result.success(true) }
            } catch (e: Exception) {
                main.post { result.error("bt", e.message ?: "Error escribiendo por Bluetooth", null) }
            }
        }
    }

    fun close() {
        try { server?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        server = null
        socket = null
    }

    private fun emit(event: Map<String, Any>) = main.post { sink?.success(event) }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) { sink = events }
    override fun onCancel(arguments: Any?) { sink = null }
}
