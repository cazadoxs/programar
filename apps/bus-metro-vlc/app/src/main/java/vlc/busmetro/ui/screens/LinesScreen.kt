package vlc.busmetro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vlc.busmetro.R
import vlc.busmetro.app
import vlc.busmetro.data.PatternEntity
import vlc.busmetro.data.StopEntity
import vlc.busmetro.ui.LinesViewModel
import vlc.busmetro.ui.components.*
import vlc.busmetro.ui.theme.networkColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinesScreen(vm: LinesViewModel, onLine: (String, String) -> Unit) {
    val lines by vm.lines.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }
    val shown = lines.filter { filter.isBlank() || it.name.contains(filter.trim(), true) || it.longName.contains(filter.trim(), true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.nav_lines), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp), placeholder = { Text(stringResource(R.string.lines_filter)) })
        }
        if (lines.isEmpty()) item { EmptyState(Icons.Filled.SearchOff, stringResource(R.string.lines_empty), stringResource(R.string.lines_empty_hint)) }
        listOf("metro" to R.string.net_metro, "bus" to R.string.net_bus, "rail" to R.string.net_rail).forEach { (net, label) ->
            val group = shown.filter { it.network == net }
            if (group.isNotEmpty()) {
                item(key = "h$net") { SectionTitle(stringResource(label)) }
                items(group, key = { "$net${it.name}" }) { l ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onLine(l.network, l.name) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        LineChip(l.name, l.network, l.style, big = true)
                        Spacer(Modifier.width(14.dp))
                        Text(l.longName.removePrefix("${l.name} - ").ifEmpty { l.name }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetailScreen(network: String, line: String, onBack: () -> Unit, onStop: (String) -> Unit, onMap: () -> Unit) {
    val repo = androidx.compose.ui.platform.LocalContext.current.app.repo
    var patterns by remember { mutableStateOf<List<PatternEntity>>(emptyList()) }
    var style by remember { mutableStateOf<vlc.busmetro.data.LineStyle?>(null) }
    var sel by remember { mutableIntStateOf(0) }
    var stops by remember { mutableStateOf<List<StopEntity>>(emptyList()) }
    LaunchedEffect(network, line) {
        patterns = repo.patterns(network, line).sortedByDescending { it.stopKeys.count { c -> c == ',' } }
        style = repo.styles(network)[line]
    }
    LaunchedEffect(patterns, sel) {
        val p = patterns.getOrNull(sel) ?: return@LaunchedEffect
        val keys = p.stopKeys.split(',').filter { it.isNotEmpty() }
        val byKey = repo.stops(keys).associateBy { it.key }
        stops = keys.mapNotNull { byKey[it] }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Row(verticalAlignment = Alignment.CenterVertically) { LineChip(line, network, style, big = true); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.line_title)) } },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { IconButton(onMap) { Icon(Icons.Filled.Map, stringResource(R.string.see_on_map)) } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            if (patterns.size > 1) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    patterns.forEachIndexed { i, p -> FilterChip(sel == i, { sel = i }, label = { Text("→ " + p.headsign.ifEmpty { "?" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(stops) { i, s ->
                Row(Modifier.fillMaxWidth().clickable { onStop(s.key) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(if (i == 0 || i == stops.lastIndex) 14.dp else 10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(lineColor(style, network)))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(s.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}
