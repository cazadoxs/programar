package vlc.busmetro.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import vlc.busmetro.R
import vlc.busmetro.app
import vlc.busmetro.data.StopEntity
import vlc.busmetro.ui.components.StopRow
import vlc.busmetro.ui.theme.BusMetroTheme

/** Lets the person pick which stop (or bike station) a widget shows. */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val app = application.app
        setContent {
            BusMetroTheme {
                val scope = rememberCoroutineScope()
                var query by remember { mutableStateOf("") }
                var results by remember { mutableStateOf<List<StopEntity>>(emptyList()) }
                var favorites by remember { mutableStateOf<List<StopEntity>>(emptyList()) }
                LaunchedEffect(Unit) { favorites = app.repo.stops(app.repo.favoriteList().map { it.key }) }
                LaunchedEffect(query) { results = if (query.length >= 2) app.repo.search(query) else emptyList() }

                fun choose(key: String) = scope.launch {
                    app.prefs.setWidgetStop(widgetId, key)
                    StopWidget().apply { GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(widgetId).let { update(this@WidgetConfigActivity, it) } }
                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    finish()
                }

                Scaffold { pad ->
                    Column(Modifier.padding(pad).padding(16.dp)) {
                        Text(stringResource(R.string.widget_pick_title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.search_hint)) })
                        Spacer(Modifier.height(8.dp))
                        val list = if (query.length >= 2) results else favorites
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(list, key = { it.key }) { s -> StopRow(s, null, onClick = { choose(s.key) }) }
                        }
                    }
                }
            }
        }
    }
}
