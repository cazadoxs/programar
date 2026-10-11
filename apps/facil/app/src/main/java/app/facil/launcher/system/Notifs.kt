package app.facil.launcher.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.facil.launcher.R
import app.facil.launcher.core.Reminder
import app.facil.launcher.data.Store
import app.facil.launcher.ui.FamilyActivity
import app.facil.launcher.ui.HomeActivity

object Notifs {
    const val CH_REMINDERS = "reminders"
    const val CH_ALERTS = "alerts"
    const val CH_SHARING = "sharing"
    private const val ID_SHARING = 1
    private const val ID_ALERT_BASE = 1000
    private const val ID_REMINDER_BASE = 5000

    fun createChannels(ctx: Context) {
        val nm = ctx.svc<NotificationManager>()
        nm.createNotificationChannel(NotificationChannel(CH_REMINDERS, "Recordatorios", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos de pastillas, citas y tareas"
            enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Alertas de la familia", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Batería baja, móvil sin usar o recordatorio sin marcar"
        })
        nm.createNotificationChannel(NotificationChannel(CH_SHARING, "Aviso de privacidad", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Recuerda qué información ve tu familia"
            setShowBadge(false)
        })
    }

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(ctx: Context, cls: Class<*>, req: Int) = PendingIntent.getActivity(
        ctx, req, Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun reminder(ctx: Context, r: Reminder) {
        if (!canPost(ctx)) return
        val id = ID_REMINDER_BASE + (r.id.hashCode() and 0xFFF)
        val done = PendingIntent.getBroadcast(
            ctx, id, Intent(ctx, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DONE)
                .putExtra(ReminderReceiver.EXTRA_ID, r.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(r.label)
            .setContentText("Son las ${r.timeText()}. Pulsa «Hecho» cuando lo hayas hecho.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open(ctx, HomeActivity::class.java, id))
            .addAction(Notification.Action.Builder(null, "Hecho", done).build())
            .setAutoCancel(true)
            .build()
        ctx.svc<NotificationManager>().notify(id, n)
    }

    fun cancelReminder(ctx: Context, reminderId: String) {
        ctx.svc<NotificationManager>().cancel(ID_REMINDER_BASE + (reminderId.hashCode() and 0xFFF))
    }

    fun alert(ctx: Context, key: String, title: String, text: String) {
        if (!canPost(ctx)) return
        val id = ID_ALERT_BASE + (key.hashCode() and 0xFFF)
        val n = Notification.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open(ctx, FamilyActivity::class.java, id))
            .setAutoCancel(true)
            .build()
        ctx.svc<NotificationManager>().notify(id, n)
    }

    /**
     * Aviso permanente y discreto en el móvil de la persona mayor mientras
     * comparte su estado: así siempre sabe que su familia ve la batería y la
     * actividad (requisito de transparencia de Google Play).
     */
    fun updateSharing(ctx: Context) {
        val store = Store.get(ctx)
        val nm = ctx.svc<NotificationManager>()
        val names = store.memberNames
        if (!store.isSenior || !store.cloudEnabled || names.isEmpty() || !store.shareStatus || !canPost(ctx)) {
            nm.cancel(ID_SHARING); return
        }
        val who = names.joinToString(", ")
        val n = Notification.Builder(ctx, CH_SHARING)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Tu familia te acompaña")
            .setContentText("$who puede ver tu batería y cuándo usaste el móvil por última vez.")
            .setStyle(Notification.BigTextStyle().bigText(
                "$who puede ver tu batería y cuándo usaste el móvil por última vez, y cambiar los botones de la pantalla. No ve tus mensajes, llamadas ni dónde estás."))
            .setOngoing(true)
            .setContentIntent(open(ctx, HomeActivity::class.java, ID_SHARING))
            .build()
        nm.notify(ID_SHARING, n)
    }
}
