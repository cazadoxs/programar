package app.foco.blocker

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * The timeline JS sends covers 7 days; rules cannot be evaluated natively, so
 * if Foco is not opened for that long blocking would silently stop. An inexact
 * alarm 24 h before the timeline ends posts "Abre Foco para renovar tus
 * bloqueos"; every setState() pushes the alarm further out, so the user only
 * sees it when the app really has not refreshed.
 */
object RenewalReminder {
  private const val TAG = "FocoBlocker"
  private const val CHANNEL_ID = "foco_reminders"
  private const val NOTIFICATION_ID = 4108
  private const val REQUEST_CODE = 4108
  internal const val LEAD_MS = 24 * 3600_000L

  /** (Re)schedules the reminder for the stored state. Called on setState and at boot. */
  fun schedule(context: Context) {
    val app = context.applicationContext
    val alarms = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pending = pendingIntent(app)
    alarms.cancel(pending)
    val state = BlockState.load(app)
    val end = state.timelineEnd ?: return
    if (!worthReminding(state)) return
    // A fresh state means the app refreshed: drop a reminder that is still showing.
    if (end - System.currentTimeMillis() > LEAD_MS) cancelNotification(app)
    val at = maxOf(end - LEAD_MS, System.currentTimeMillis() + 60_000L)
    try {
      alarms.set(AlarmManager.RTC, at, pending) // inexact, no special permission, delivered on next wake
    } catch (e: RuntimeException) {
      Log.w(TAG, "Could not schedule the renewal reminder", e)
    }
  }

  /** Something would actually stop being blocked. */
  private fun worthReminding(state: BlockState): Boolean = state.timeline.any { it.blocks.isNotEmpty() }

  internal fun onAlarm(context: Context) {
    val state = BlockState.load(context)
    val end = state.timelineEnd ?: return
    if (!worthReminding(state) || end - System.currentTimeMillis() > LEAD_MS) return
    notify(context)
  }

  private fun notify(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    if (Build.VERSION.SDK_INT >= 26) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT).apply {
          description = "Avisos para que Foco siga bloqueando lo que has programado."
        },
      )
    }
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val content = launch?.let {
      PendingIntent.getActivity(context, REQUEST_CODE, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL_ID)
    else @Suppress("DEPRECATION") Notification.Builder(context)
    val notification = builder
      .setSmallIcon(R.drawable.foco_ic_notification)
      .setContentTitle("Abre Foco para renovar tus bloqueos")
      .setContentText("Tus horarios de bloqueo se acaban pronto si no abres la app.")
      .setAutoCancel(true)
      .apply { if (content != null) setContentIntent(content) }
      .build()
    // Without the notification permission (Android 13+) this is silently dropped by the system.
    runCatching { manager.notify(NOTIFICATION_ID, notification) }
  }

  private fun cancelNotification(context: Context) {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIFICATION_ID)
  }

  private fun pendingIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      REQUEST_CODE,
      Intent(context, RenewalReceiver::class.java).setAction(RenewalReceiver.ACTION),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Explicit, non-exported target of the renewal alarm. */
class RenewalReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    if (intent?.action == ACTION) RenewalReminder.onAlarm(context)
  }

  companion object {
    const val ACTION = "app.foco.blocker.RENEWAL_REMINDER"
  }
}
