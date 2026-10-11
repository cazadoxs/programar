package vlc.busmetro.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.launch
import vlc.busmetro.R
import vlc.busmetro.ads.Ads
import vlc.busmetro.ads.BannerAd
import vlc.busmetro.app
import vlc.busmetro.data.Settings
import vlc.busmetro.data.ThemeMode
import vlc.busmetro.ui.screens.*
import vlc.busmetro.ui.theme.BusMetroTheme

private object Routes {
    const val HOME = "home"
    const val MAP = "map?stop={stop}&net={net}&line={line}"
    const val LINES = "lines"
    const val ALERTS = "alerts"
    const val SETTINGS = "settings"
    const val PRO = "pro"
    const val LICENSES = "licenses"
    const val STOP = "stop/{key}"
    const val LINE = "line/{net}/{name}"
    fun stop(key: String) = "stop/${Uri.encode(key)}"
    fun line(net: String, name: String) = "line/$net/${Uri.encode(name)}"
    fun map(stop: String? = null, net: String? = null, line: String? = null) =
        "map?stop=${Uri.encode(stop ?: "")}&net=${Uri.encode(net ?: "")}&line=${Uri.encode(line ?: "")}"
}

class MainActivity : AppCompatActivity() {
    private val pending = mutableStateOf<Intent?>(null)
    private val canShowAds = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pending.value = intent
        Ads.gather(this) { canShowAds.value = it }
        val app = application.app

        setContent {
            val settings by app.prefs.settings.collectAsStateWithLifecycle(Settings())
            val dark = when (settings.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
            BusMetroTheme(dark) { Root(settings) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pending.value = intent
    }

    @Composable
    private fun Root(settings: Settings) {
        val nav = rememberNavController()
        val scope = rememberCoroutineScope()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val home: HomeViewModel = viewModel()
        val settingsVm: SettingsViewModel = viewModel()
        val showBanner = !settings.pro && canShowAds.value

        // Deep links from widgets, shortcuts and notifications.
        val p = pending.value
        LaunchedEffect(p) {
            p ?: return@LaunchedEffect
            when (p.action) {
                ACTION_STOP -> p.getStringExtra(EXTRA_KEY)?.let { nav.navigate(Routes.stop(it)) }
                ACTION_ALERTS -> nav.navigate(Routes.ALERTS) { launchSingleTop = true }
                ACTION_MAP -> nav.navigate(Routes.map())
                ACTION_NEARBY -> nav.navigate(Routes.HOME) { launchSingleTop = true }
            }
            pending.value = null
        }

        // Ask for a review once, after a few happy opens.
        LaunchedEffect(settings.opens, settings.reviewAsked) {
            if (settings.opens >= 8 && !settings.reviewAsked) {
                application.app.prefs.setReviewAsked()
                val rm = ReviewManagerFactory.create(this@MainActivity)
                rm.requestReviewFlow().addOnSuccessListener { info -> rm.launchReviewFlow(this@MainActivity, info) }
            }
        }

        val tabs = listOf(
            Triple(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
            Triple("map", R.string.nav_map, Icons.Filled.Map),
            Triple(Routes.LINES, R.string.nav_lines, Icons.Filled.Route),
            Triple(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
        )
        val tabRoutes = setOf(Routes.HOME, Routes.MAP, Routes.LINES, Routes.SETTINGS, Routes.ALERTS)
        val barVisible = route in tabRoutes

        Scaffold(
            bottomBar = {
                Column {
                    if (showBanner) BannerAd(widthDp = LocalConfiguration.current.screenWidthDp)
                    if (barVisible) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        tabs.forEach { (r, label, icon) ->
                            val selected = route?.startsWith(r.substringBefore('?')) == true
                            NavigationBarItem(selected, {
                                nav.navigate(if (r == "map") Routes.map() else r) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true }
                            }, icon = { Icon(icon, null) }, label = { Text(stringResource(label)) })
                        }
                    }
                }
            },
        ) { pad ->
            NavHost(nav, Routes.HOME, Modifier.padding(pad).consumeWindowInsets(pad)) {
                composable(Routes.HOME) {
                    HomeScreen(home, onStop = { nav.navigate(Routes.stop(it)) }, onAlerts = { nav.navigate(Routes.ALERTS) }, onUpgrade = { nav.navigate(Routes.PRO) })
                }
                composable(Routes.MAP, arguments = listOf(
                    navArgument("stop") { type = NavType.StringType; defaultValue = "" },
                    navArgument("net") { type = NavType.StringType; defaultValue = "" },
                    navArgument("line") { type = NavType.StringType; defaultValue = "" })) { e ->
                    val stop = e.arguments?.getString("stop").orEmpty().ifEmpty { null }
                    val net = e.arguments?.getString("net").orEmpty()
                    val line = e.arguments?.getString("line").orEmpty()
                    MapScreen(onStop = { nav.navigate(Routes.stop(it)) }, focusStop = stop, highlight = if (net.isNotEmpty() && line.isNotEmpty()) net to line else null)
                }
                composable(Routes.LINES) { LinesScreen(viewModel()) { net, name -> nav.navigate(Routes.line(net, name)) } }
                composable(Routes.ALERTS) { AlertsScreen(viewModel()) }
                composable(Routes.SETTINGS) { SettingsScreen(settingsVm, onPro = { nav.navigate(Routes.PRO) }, onLicenses = { nav.navigate(Routes.LICENSES) }) }
                composable(Routes.PRO) { ProScreen(settingsVm) { nav.popBackStack() } }
                composable(Routes.LICENSES) { LicensesScreen { nav.popBackStack() } }
                composable(Routes.STOP, arguments = listOf(navArgument("key") { type = NavType.StringType })) { e ->
                    val key = e.arguments?.getString("key").orEmpty()
                    val vm: StopViewModel = viewModel(factory = StopVmFactory(application, key))
                    StopScreen(vm, onBack = { nav.popBackStack() }, onUpgrade = { nav.navigate(Routes.PRO) })
                }
                composable(Routes.LINE, arguments = listOf(navArgument("net") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })) { e ->
                    val net = e.arguments?.getString("net").orEmpty()
                    val name = e.arguments?.getString("name").orEmpty()
                    LineDetailScreen(net, name, onBack = { nav.popBackStack() }, onStop = { nav.navigate(Routes.stop(it)) },
                        onMap = { nav.navigate(Routes.map(net = net, line = name)) })
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP = "vlc.busmetro.STOP"
        const val ACTION_ALERTS = "vlc.busmetro.ALERTS"
        const val ACTION_MAP = "vlc.busmetro.MAP"
        const val ACTION_NEARBY = "vlc.busmetro.NEARBY"
        const val EXTRA_KEY = "stop_key"
    }
}
