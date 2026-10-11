package app.apunta.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.apunta.R
import app.apunta.ui.AppViewModel
import app.apunta.ui.components.highlighted
import app.apunta.ui.theme.Apunta
import app.apunta.ui.theme.TimestampStyle
import app.apunta.util.TimeFormat
import java.text.DateFormat
import java.util.Date

@Composable
fun SearchScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (Long, Long, String) -> Unit) {
    val q by vm.searchQuery.collectAsStateWithLifecycle()
    val ui by vm.search.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            OutlinedTextField(
                q, { vm.searchQuery.value = it.take(100) }, singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text(stringResource(R.string.search_all)) },
            )
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (ui.query.isNotBlank() && !ui.searching && ui.hits.isEmpty() && ui.titles.isEmpty()) {
                item { Text(stringResource(R.string.search_none), color = Apunta.colors.inkSoft) }
            }
            items(ui.titles, key = { "t${it.id}" }) { r ->
                Text(highlighted(r.title, ui.query), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().clickable { onOpen(r.id, 0, ui.query) })
            }
            items(ui.hits, key = { it.segmentId }) { h ->
                Column(Modifier.fillMaxWidth().clickable { onOpen(h.recordingId, h.startMs, ui.query) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(h.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, false), maxLines = 1)
                        Text(TimeFormat.clock(h.startMs), style = TimestampStyle, color = Apunta.colors.margin)
                    }
                    Text(highlighted(h.text.trim(), ui.query), style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                    Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(h.createdAt)), style = MaterialTheme.typography.bodySmall, color = Apunta.colors.inkSoft)
                }
            }
        }
    }
}
