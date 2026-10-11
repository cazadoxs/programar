package io.github.cazadoxs.toma.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.alarm.Notifications
import io.github.cazadoxs.toma.container
import io.github.cazadoxs.toma.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/** Home-screen widget with the next dose. It is a Toma Plus extra; without Plus it explains that. */
class NextDoseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ids.forEach { manager.updateAppWidget(it, build(context)) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextDoseWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, NextDoseWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        private suspend fun build(context: Context): RemoteViews {
            val c = context.container
            val views = RemoteViews(context.packageName, R.layout.widget_next_dose)
            if (!c.settings.current.plusOwned) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_plus_needed))
                views.setTextViewText(R.id.widget_time, context.getString(R.string.plus_locked))
            } else {
                val group = c.repository.nextDoseGroup(Instant.now())
                if (group.isEmpty()) {
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_nothing))
                    views.setTextViewText(R.id.widget_time, "")
                } else {
                    views.setTextViewText(R.id.widget_title, group.joinToString(", ") { it.med.name })
                    views.setTextViewText(
                        R.id.widget_time,
                        context.getString(R.string.widget_next_at, Notifications.timeText(group.first().at)),
                    )
                }
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 3, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return views
        }
    }
}
