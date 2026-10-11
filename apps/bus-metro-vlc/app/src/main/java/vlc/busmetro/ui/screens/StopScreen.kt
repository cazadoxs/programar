package vlc.busmetro.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import vlc.busmetro.R
import vlc.busmetro.ui.StopViewModel
import vlc.busmetro.ui.components.*
import vlc.busmetro.ui.theme.Palette
import vlc.transit.core.Network
import vlc.transit.core.splitKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopScreen(vm: StopViewModel, onBack: () -> Unit, onUpgrade: () -> Unit) {
    val context = LocalContext.current
    val board by vm.board.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val fav by vm.isFavorite.collectAsStateWithLifecycle()
    val dayData by vm.day.collectAsStateWithLifecycle()
    val network = splitKey(vm.key)?.first?.prefix ?: "bus"
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var limitHit by remember { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { vm.refresh(true); delay(15_000) } }
    }
    LaunchedEffect(tab, date) { if (tab == 1) vm.loadDay(date) }

    val title = board?.stop?.name ?: board?.bike?.name ?: ""
    val limitMsg = stringResource(R.string.fav_limit_snack, vlc.busmetro.ui.FREE_FAVORITES)
    val upgradeLabel = stringResource(R.string.go_pro)
    LaunchedEffect(limitHit) {
        if (limitHit) {
            limitHit = false
            if (snackbar.showSnackbar(limitMsg, upgradeLabel) == SnackbarResult.ActionPerformed) onUpgrade()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton({
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, title, board?.stop?.code ?: "")), null))
                    }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
                    IconButton({ if (!vm.toggleFavorite()) limitHit = true }) {
                        Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(if (fav) R.string.fav_remove else R.string.fav_add),
                            tint = if (fav) Palette.Orange else MaterialTheme.colorScheme.onSurface)
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val b = board
            if (b?.bike == null) {
                SecondaryTabRow(tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.tab_next)) })
                    Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.tab_timetable)) })
                }
            }
            when {
                loading && b == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                b?.bike != null -> BikeDetail(b.bike, Modifier.padding(20.dp))
                tab == 0 -> NextTab(b, network)
                else -> TimetableTab(dayData, date, network, b?.styles.orEmpty(), onDate = { date = it })
            }
        }
    }
}

@Composable
private fun BikeDetail(b: vlc.busmetro.data.BikeEntity, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card2 {
            Text(stringResource(R.string.valenbisi), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            BikeSummary(b)
            if (!b.open) Text(stringResource(R.string.station_closed), color = Palette.Bad, modifier = Modifier.padding(top = 8.dp))
        }
        Text(stringResource(R.string.bike_total, b.total), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NextTab(b: vlc.busmetro.data.StopBoard?, network: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        if (b != null) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(b.source, b.liveFailed, Modifier.weight(1f))
                    b.stop?.takeIf { it.wheelchair }?.let { Icon(Icons.Filled.Accessible, stringResource(R.string.accessible), tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp)) }
                }
            }
            if (b.source == vlc.busmetro.data.BoardSource.APPROXIMATE) item {
                Text(stringResource(R.string.approx_warning), style = MaterialTheme.typography.bodySmall, color = Palette.Warn, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (b.arrivals.isEmpty()) item { EmptyState(Icons.Filled.NightsStay, stringResource(R.string.no_service_soon), stringResource(R.string.no_service_hint)) }
            items(b.arrivals.take(40), key = { it.routeKey + it.headsign + it.epochSec }) { a ->
                ArrivalRow(a, network, b.styles)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun TimetableTab(data: Pair<List<vlc.busmetro.data.DayRow>, Boolean>?, date: LocalDate, network: String, styles: Map<String, vlc.busmetro.data.LineStyle>, onDate: (LocalDate) -> Unit) {
    val fmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    var filter by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onDate(date.minusDays(1)) }) { Icon(Icons.Filled.ChevronLeft, stringResource(R.string.prev_day)) }
            Text(date.format(fmt), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton({ onDate(date.plusDays(1)) }) { Icon(Icons.Filled.ChevronRight, stringResource(R.string.next_day)) }
        }
        val rows = data?.first.orEmpty()
        val lines = rows.map { it.line }.distinct().sortedWith(vlc.busmetro.data.LineOrder)
        if (lines.size > 1) {
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == null, { filter = null }, label = { Text(stringResource(R.string.all)) })
                lines.forEach { l -> FilterChip(filter == l, { filter = if (filter == l) null else l }, label = { Text(l) }) }
            }
        }
        if (data?.second == true) Text(stringResource(R.string.approx_warning), style = MaterialTheme.typography.bodySmall, color = Palette.Warn, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        val shown = rows.filter { filter == null || it.line == filter }
        if (data != null && shown.isEmpty()) EmptyState(Icons.Filled.EventBusy, stringResource(R.string.no_service_day), stringResource(R.string.no_service_hint))
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            items(shown.size) { i ->
                val r = shown[i]
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d:%02d".format((r.secs / 3600) % 24, (r.secs / 60) % 60), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp))
                    LineChip(r.line, network, styles[r.line])
                    Spacer(Modifier.width(10.dp))
                    Text(r.headsign, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}
