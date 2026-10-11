package vlc.busmetro.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vlc.busmetro.R
import vlc.busmetro.app
import vlc.busmetro.data.StopBoard
import vlc.busmetro.ui.MainActivity
import vlc.busmetro.ui.theme.Palette
import vlc.transit.core.Arrival

object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun requestAll(context: Context) {
        val c = context.applicationContext
        scope.launch { runCatching { updateAllNow(c) } }
    }

    suspend fun updateAllNow(context: Context) {
        StopWidget().updateAll(context)
        FavoritesWidget().updateAll(context)
    }
}

private val Ink = Color(0xFF0B1F2A)
private val InkSoft = Color(0xFF16323F)
private val Cream = Color(0xFFFFF7EC)
private val Muted = Color(0xFF9DB4BF)
private val Orange = Color(0xFFFF6B1A)

private fun fmtMinutes(a: Arrival): String = when {
    a.canceled -> "×"
    a.minutes <= 0 -> "ya"
    a.minutes >= 60 -> "${a.minutes / 60}h${(a.minutes % 60).toString().padStart(2, '0')}"
    else -> "${a.minutes}'"
}

@Composable
private fun LineChip(line: String, color: Int?, text: Int?) {
    val bg = color?.let { Color(it) } ?: Orange
    val fg = text?.let { Color(it) } ?: Color.White
    Box(
        modifier = GlanceModifier.background(bg).cornerRadius(8.dp).padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) { Text(line.take(4), style = TextStyle(color = ColorProvider(fg), fontWeight = FontWeight.Bold, fontSize = 12.sp)) }
}

@Composable
private fun ArrivalRow(a: Arrival, board: StopBoard) {
    val st = board.styles[a.line]
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        LineChip(a.line, st?.color, st?.textColor)
        Spacer(GlanceModifier.width(8.dp))
        Text(a.headsign, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = ColorProvider(Cream), fontSize = 13.sp))
        Text(fmtMinutes(a), style = TextStyle(color = ColorProvider(if (a.realtime) Orange else Cream), fontWeight = FontWeight.Bold, fontSize = 16.sp))
    }
}

class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        StopWidget().update(context, glanceId)
        FavoritesWidget().updateAll(context)
    }
}

@Composable
private fun Shell(openKey: String?, title: String, subtitle: String?, content: @Composable () -> Unit) {
    val open = actionStartActivity(Intent().setComponent(ComponentName("com.cazadoxs.busmetrovlc", MainActivity::class.java.name))
        .setAction(MainActivity.ACTION_STOP).putExtra(MainActivity.EXTRA_KEY, openKey))
    Column(GlanceModifier.fillMaxSize().background(Ink).cornerRadius(20.dp).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth().clickable(open), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(title, maxLines = 1, style = TextStyle(color = ColorProvider(Cream), fontWeight = FontWeight.Bold, fontSize = 15.sp))
                if (subtitle != null) Text(subtitle, maxLines = 1, style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp))
            }
            Text("↻", modifier = GlanceModifier.clickable(actionRunCallback<RefreshCallback>()).padding(6.dp),
                style = TextStyle(color = ColorProvider(Orange), fontSize = 20.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(6.dp))
        content()
    }
}

/** One stop, next arrivals. Free. */
class StopWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.app
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val key = app.prefs.widgetStop(widgetId)
        val board = key?.let { runCatching { app.repo.board(it) }.getOrNull() }
        provideContent {
            val size = LocalSize.current
            val rows = if (size.height < 100.dp) 1 else if (size.height < 150.dp) 2 else 4
            Shell(key, board?.stop?.name ?: board?.bike?.name ?: context.getString(R.string.widget_pick_stop),
                board?.stop?.lines?.replace(",", " · ")) {
                if (board == null || (board.arrivals.isEmpty() && board.bike == null)) {
                    Text(context.getString(if (key == null) R.string.widget_pick_stop_hint else R.string.widget_no_arrivals),
                        style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp))
                } else if (board.bike != null) {
                    Text("🚲 ${board.bike.bikes}   ·   ${context.getString(R.string.docks)} ${board.bike.docks}",
                        style = TextStyle(color = ColorProvider(Cream), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                } else board.arrivals.filter { !it.canceled }.take(rows).forEach { ArrivalRow(it, board) }
            }
        }
    }
}

/** All favourites at once. Pro. */
class FavoritesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    private data class Block(val name: String, val key: String, val board: StopBoard?)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.app
        val pro = app.prefs.current().pro
        val blocks = if (!pro) emptyList() else app.repo.favoriteList().take(6).map { f ->
            Block(f.nickname ?: app.repo.nameOf(f.key) ?: f.key, f.key, runCatching { app.repo.board(f.key) }.getOrNull())
        }
        provideContent {
            val size = LocalSize.current
            val perStop = if (size.height < 170.dp) 1 else 2
            Shell(null, context.getString(R.string.widget_favorites_name), if (pro) null else "Pro") {
                when {
                    !pro -> Text(context.getString(R.string.widget_pro_only), style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp))
                    blocks.isEmpty() -> Text(context.getString(R.string.widget_no_favorites), style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp))
                    else -> LazyColumn {
                        items(blocks) { b ->
                            Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                Text(b.name, maxLines = 1, style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp, fontWeight = FontWeight.Medium))
                                val bike = b.board?.bike
                                if (bike != null) Text("🚲 ${bike.bikes}  ·  ${context.getString(R.string.docks)} ${bike.docks}",
                                    style = TextStyle(color = ColorProvider(Cream), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                else b.board?.arrivals?.filter { !it.canceled }?.take(perStop)?.forEach { ArrivalRow(it, b.board) }
                            }
                        }
                    }
                }
            }
        }
    }
}

class StopWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StopWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val c = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch { appWidgetIds.forEach { c.app.prefs.clearWidget(it) } }
    }
}

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}
