package vlc.busmetro.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import vlc.busmetro.app
import vlc.busmetro.data.*
import vlc.busmetro.work.Scheduler
import vlc.transit.core.Network
import java.time.LocalDate

const val FREE_FAVORITES = 5

object Locator {
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? = try {
        val c = LocationServices.getFusedLocationProviderClient(context)
        withTimeoutOrNull(8000) { c.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await() }
            ?: c.lastLocation.await()
    } catch (_: Exception) { null }
}

data class FavoriteCard(val fav: FavoriteEntity, val name: String, val network: String, val board: StopBoard?)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    private val repo = app.repo

    val settings = app.prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val favorites = repo.favorites().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hasData = MutableStateFlow<Boolean?>(null)
    val cards = MutableStateFlow<List<FavoriteCard>>(emptyList())
    val nearby = MutableStateFlow<List<Pair<StopEntity, Double>>>(emptyList())
    val results = MutableStateFlow<List<StopEntity>>(emptyList())
    val query = MutableStateFlow("")
    val refreshing = MutableStateFlow(false)
    val syncing = MutableStateFlow(false)
    val syncError = MutableStateFlow<String?>(null)
    val syncProgress = MutableStateFlow(0f)
    val unseen = repo.unseenAlerts().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            query.debounce(150).collectLatest { q -> results.value = if (q.trim().length >= 2) repo.search(q) else emptyList() }
        }
        viewModelScope.launch { hasData.value = repo.hasData(); if (hasData.value == false) firstSync() }
        viewModelScope.launch { app.prefs.countOpen() }
        viewModelScope.launch { favorites.collect { refresh(live = false) } }
    }

    fun firstSync() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true; syncError.value = null; syncProgress.value = 0f
            val res = app.feeds.syncAll { net, f ->
                val idx = Network.entries.indexOf(net).coerceAtMost(2)
                syncProgress.value = (idx + f) / 3f
            }
            syncing.value = false
            hasData.value = repo.hasData()
            if (res.any { it.ok }) { app.prefs.setLastSync(System.currentTimeMillis()); refresh(true) }
            if (res.none { it.ok }) syncError.value = res.firstNotNullOfOrNull { it.error }
            else if (res.any { !it.ok }) Scheduler.syncNow(app)
        }
    }

    fun refresh(live: Boolean = true) {
        viewModelScope.launch {
            refreshing.value = live
            val favs = favorites.value
            val out = coroutineScope {
                favs.map { f ->
                    async {
                        val board = runCatching { repo.board(f.key, live = live) }.getOrNull()
                        FavoriteCard(f, f.nickname ?: board?.stop?.name ?: board?.bike?.name ?: repo.nameOf(f.key) ?: f.key,
                            vlc.transit.core.splitKey(f.key)?.first?.prefix ?: "bus", board)
                    }
                }.awaitAll()
            }
            cards.value = out
            refreshing.value = false
        }
    }

    fun loadNearby(context: Context) {
        viewModelScope.launch {
            val loc = Locator.current(context) ?: return@launch
            nearby.value = repo.nearby(loc.latitude, loc.longitude, 800.0, 12)
        }
    }

    fun canAddFavorite() = settings.value.pro || favorites.value.size < FREE_FAVORITES
    fun remove(key: String) = viewModelScope.launch { repo.removeFavorite(key) }
    fun rename(f: FavoriteEntity, name: String?) = viewModelScope.launch { repo.renameFavorite(f, name) }
    fun move(list: List<FavoriteEntity>) = viewModelScope.launch { repo.reorder(list) }
}

class StopViewModel(application: Application, val key: String) : AndroidViewModel(application) {
    private val app = application.app
    private val repo = app.repo
    val board = MutableStateFlow<StopBoard?>(null)
    val loading = MutableStateFlow(true)
    val isFavorite = repo.isFavorite(key).map { it > 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val settings = app.prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val favoriteCount = repo.favorites().map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val day = MutableStateFlow<Pair<List<DayRow>, Boolean>?>(null)

    fun refresh(live: Boolean = true) {
        viewModelScope.launch {
            board.value = runCatching { repo.board(key, live = live) }.getOrNull() ?: board.value
            loading.value = false
        }
    }

    fun loadDay(date: LocalDate) { viewModelScope.launch { day.value = repo.day(key, date) } }

    fun toggleFavorite(): Boolean {
        if (isFavorite.value) { viewModelScope.launch { repo.removeFavorite(key) }; return true }
        if (!settings.value.pro && favoriteCount.value >= FREE_FAVORITES) return false
        viewModelScope.launch { repo.addFavorite(key) }
        return true
    }
}

class AlertsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.app.repo
    val alerts = repo.alerts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val loading = MutableStateFlow(false)
    fun refresh() = viewModelScope.launch {
        loading.value = true
        val lang = java.util.Locale.getDefault().language.let { if (it == "ca" || it == "en") it else "es" }
        runCatching { repo.refreshAlerts(lang) }
        loading.value = false
    }
    fun markSeen() = viewModelScope.launch { repo.markAlertsSeen() }
}

data class LineItem(val network: String, val name: String, val longName: String, val style: LineStyle)

class LinesViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.app.repo
    val lines = MutableStateFlow<List<LineItem>>(emptyList())
    init {
        viewModelScope.launch {
            lines.value = repo.lines().map { LineItem(it.network, it.shortName, it.longName, LineStyle(it.color, it.textColor)) }
        }
    }
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    val settings = app.prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val feeds = app.repo.feeds().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val syncing = MutableStateFlow(false)
    val offer = app.billing.offer
    val busy = app.billing.busy

    fun setTheme(t: ThemeMode) = viewModelScope.launch { app.prefs.setTheme(t) }
    fun setNotify(v: Boolean) = viewModelScope.launch { app.prefs.setNotifyAlerts(v) }
    fun setBikes(v: Boolean) = viewModelScope.launch { app.prefs.setShowBikes(v) }
    fun syncNow() = viewModelScope.launch {
        syncing.value = true
        val r = app.feeds.syncAll()
        if (r.any { it.ok }) app.prefs.setLastSync(System.currentTimeMillis())
        syncing.value = false
    }
    fun restore() = viewModelScope.launch { app.billing.restore() }
}
