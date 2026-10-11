package com.cazadoxs.filtro400.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cazadoxs.filtro400.Labels
import com.cazadoxs.filtro400.R
import com.cazadoxs.filtro400.core.Decision
import com.cazadoxs.filtro400.core.PhoneNumbers
import com.cazadoxs.filtro400.graph
import com.cazadoxs.filtro400.ui.MainActivity
import java.time.LocalDate
import java.time.ZoneId

object Notifications {
    private const val CH_FILTERED = "filtered"
    private const val CH_TIPS = "tips"
    private const val ID_FILTERED = 400
    private const val ID_ROBINSON = 401

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_FILTERED, context.getString(R.string.channel_blocked), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_blocked_desc); setShowBadge(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_TIPS, context.getString(R.string.channel_tips), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_tips_desc) },
        )
    }

    private fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Una sola notificación silenciosa que se actualiza: "3 llamadas filtradas hoy". */
    fun showFiltered(context: Context, number: String, decision: Decision) {
        if (!canNotify(context)) return
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val today = context.graph.history.countFilteredSince(startOfDay).coerceAtLeast(1)
        val shown = PhoneNumbers.format(number)
        val title = if (today == 1) "1 llamada filtrada hoy" else "$today llamadas filtradas hoy"
        val text = "${Labels.actionPast(decision.action)}: $shown · ${Labels.reason(decision.reason, decision.detail)}"

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, "historial")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CH_FILTERED)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF5A36.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if (number.startsWith("+")) {
            builder.addAction(0, "Permitir este número", NotificationActionReceiver.allowIntent(context, number))
        }
        try {
            NotificationManagerCompat.from(context).notify(ID_FILTERED, builder.build())
        } catch (_: SecurityException) {
            // El permiso se retiró justo ahora: no pasa nada.
        }
    }

    fun showRobinsonReminder(context: Context) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, "guia")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH_TIPS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF5A36.toInt())
            .setContentTitle("¿Ya estás en la Lista Robinson?")
            .setContentText("Es gratis y obliga a las empresas a no llamarte con publicidad. Te explicamos cómo en 2 minutos.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Es gratis y obliga a las empresas a no llamarte con publicidad. Te explicamos cómo en 2 minutos."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_ROBINSON, n)
        } catch (_: SecurityException) {
        }
    }

    fun cancelFiltered(context: Context) = NotificationManagerCompat.from(context).cancel(ID_FILTERED)
}
