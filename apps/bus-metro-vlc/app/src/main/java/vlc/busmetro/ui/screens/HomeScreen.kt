package vlc.busmetro.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import vlc.busmetro.R
import vlc.busmetro.ui.*
import vlc.busmetro.ui.components.*
import vlc.busmetro.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel, onStop: (String) -> Unit, onAlerts: () -> Unit, onUpgrade: () -> Unit) {
    val context = LocalContext.current
    val hasData by vm.hasData.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val nearby by vm.nearby.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val unseen by vm.unseen.collectAsStateWithLifecycle()
    val error by vm.syncError.collectAsStateWithLifecycle()
    val progress by vm.syncProgress.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { vm.refresh(live = true); delay(20_000) }
        }
    }

    fun hasLoc() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var locGranted by remember { mutableStateOf(hasLoc()) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { locGranted = hasLoc() }
    LaunchedEffect(locGranted, hasData) { if (locGranted && hasData == true) vm.loadNearby(context) }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAlerts) {
                BadgedBox(badge = { if (unseen > 0) Badge(containerColor = Palette.Orange) { Text("$unseen") } }) {
                    Icon(Icons.Filled.NotificationsNone, stringResource(R.string.nav_alerts))
                }
            }
        }

        OutlinedTextField(
            value = query, onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            singleLine = true, shape = RoundedCornerShape(18.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ vm.query.value = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(),
        )

        when {
            hasData == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            hasData == false -> FirstRun(syncing, progress, error, vm::firstSync)
            query.trim().length >= 2 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                if (results.isEmpty()) item { EmptyState(Icons.Filled.SearchOff, stringResource(R.string.no_results), stringResource(R.string.no_results_hint)) }
                items(results, key = { it.key }) { s -> StopRow(s, null, onClick = { onStop(s.key) }) }
            }
            else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh(true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { SectionTitle(stringResource(R.string.favorites)) }
                    if (cards.isEmpty()) {
                        item { EmptyState(Icons.Filled.StarBorder, stringResource(R.string.fav_empty_title), stringResource(R.string.fav_empty_body)) }
                    }
                    items(cards, key = { it.fav.key }) { c -> FavoriteCardView(c, onClick = { onStop(c.fav.key) }) }
                    if (!settings.pro && favs.size >= FREE_FAVORITES) item {
                        Card2(onClick = onUpgrade) {
                            Text(stringResource(R.string.fav_limit_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.fav_limit_body, FREE_FAVORITES), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    item { SectionTitle(stringResource(R.string.nearby)) }
                    if (!locGranted) item {
                        Card2 {
                            Text(stringResource(R.string.nearby_perm_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.nearby_perm_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            Button({ permLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }) { Text(stringResource(R.string.allow_location)) }
                        }
                    } else if (nearby.isEmpty()) item { Text(stringResource(R.string.nearby_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp)) }
                    items(nearby, key = { "n" + it.first.key }) { (s, d) -> StopRow(s, d, onClick = { onStop(s.key) }) }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCardView(c: FavoriteCard, onClick: () -> Unit) {
    Card2(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkDot(c.network, 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            c.board?.let { SourceBadge(it.source, it.liveFailed) }
        }
        val b = c.board
        when {
            b == null -> Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            b.bike != null -> BikeSummary(b.bike)
            b.arrivals.isEmpty() -> Text(stringResource(R.string.no_service_soon), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            else -> Column {
                // The first arrival of each line, then fill with the next ones.
                val shown = (b.arrivals.distinctBy { it.line } + b.arrivals).distinct().take(3)
                shown.forEach { ArrivalRow(it, c.network, b.styles) }
            }
        }
    }
}

@Composable
fun BikeSummary(b: vlc.busmetro.data.BikeEntity) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column { Text("${b.bikes}", style = MaterialTheme.typography.displaySmall, color = if (b.bikes == 0) Palette.Bad else Palette.Live); Text(stringResource(R.string.bikes), style = MaterialTheme.typography.labelMedium) }
        Column { Text("${b.docks}", style = MaterialTheme.typography.displaySmall, color = if (b.docks == 0) Palette.Bad else MaterialTheme.colorScheme.onSurface); Text(stringResource(R.string.docks), style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun FirstRun(syncing: Boolean, progress: Float, error: String?, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.first_run_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.first_run_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (syncing) {
            LinearProgressIndicator(progress = { progress.coerceIn(0.02f, 1f) }, modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            AnimatedVisibility(error != null) { Text(stringResource(R.string.first_run_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp)) }
            Button(retry) { Text(stringResource(if (error != null) R.string.retry else R.string.first_run_start)) }
        }
    }
}
