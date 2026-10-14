package com.cazadoxs.pasalo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Servicio en primer plano que solo existe mientras hay una transferencia ya
 * empezada. Mantiene el proceso, la CPU y el Wi-Fi despiertos aunque el
 * usuario salga de la app. Nunca inicia transferencias por sí mismo.
 */
class TransferService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "Pásalo"
        val text = intent?.getStringExtra("text") ?: ""
        val percent = intent?.getIntExtra("percent", -1) ?: -1
        val n = build(this, title, text, percent)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(1, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        if (wake == null) {
            wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pasalo:transfer").apply {
                    setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L)
                }
        }
        if (wifi == null) {
            @Suppress("DEPRECATION")
            wifi = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "pasalo:wifi").apply {
                    setReferenceCounted(false); acquire()
                }
        }
        return START_NOT_STICKY
    }

    companion object {
        @Volatile var running = false

        /** Actualiza el progreso sin volver a arrancar el servicio. */
        fun update(ctx: Context, title: String, text: String, percent: Int) {
            if (!running) return
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(1, build(ctx, title, text, percent))
        }

    fun build(ctx: Context, title: String, text: String, percent: Int): Notification {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel("transfer") == null) {
                nm.createNotificationChannel(
                    NotificationChannel("transfer", "Transferencias", NotificationManager.IMPORTANCE_LOW))
            }
            Notification.Builder(ctx, "transfer")
        } else {
            @Suppress("DEPRECATION") Notification.Builder(ctx)
        }
        b.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        if (percent in 0..100) b.setProgress(100, percent, false) else b.setProgress(0, 0, true)
        return b.build()
    }
    }

    override fun onDestroy() {
        running = false
        wake?.let { if (it.isHeld) it.release() }
        wifi?.let { if (it.isHeld) it.release() }
        wake = null
        wifi = null
        super.onDestroy()
    }
}
