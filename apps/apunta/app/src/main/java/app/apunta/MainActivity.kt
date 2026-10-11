package app.apunta

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.apunta.transcribe.TranscriptionService
import app.apunta.ui.AppViewModel
import app.apunta.ui.UiMessage
import app.apunta.ui.screens.DetailScreen
import app.apunta.ui.screens.HomeScreen
import app.apunta.ui.screens.OnboardingScreen
import app.apunta.ui.screens.PaywallScreen
import app.apunta.ui.screens.RecordScreen
import app.apunta.ui.screens.SearchScreen
import app.apunta.ui.screens.SettingsScreen
import app.apunta.ui.theme.ApuntaTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val route = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            ApuntaTheme(settings.theme) {
                Surface(Modifier.fillMaxSize().systemBarsPadding(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    AppRoot(vm, route)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Share-to-Apunta, "open with", shortcuts, the quick-settings tile and notification taps all land here. */
    private fun handle(i: Intent?) {
        i ?: return
        when (i.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri?.let { vm.importUris(listOf(it)) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val list = if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                list?.let { vm.importUris(it) }
            }
            Intent.ACTION_VIEW -> i.data?.let { vm.importUris(listOf(it)) }
            "app.apunta.action.RECORD" -> route.value = "record"
            "app.apunta.action.SEARCH" -> route.value = "search"
            TranscriptionService.ACTION_OPEN -> route.value = "detail/${i.getLongExtra(TranscriptionService.EXTRA_ID, -1)}?seek=0&q="
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel, pending: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    val route by pending.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshQuota() }

    LaunchedEffect(route, settings.onboarded) {
        val r = route
        if (r != null && settings.onboarded) { pending.value = null; nav.navigate(r) { launchSingleTop = true } }
    }
    LaunchedEffect(message) {
        when (val m = message) {
            is UiMessage.Res -> { snackbar.showSnackbar(ctx.getString(m.id)); vm.message.value = null }
            is UiMessage.Shared -> {
                val send = Intent(Intent.ACTION_SEND).setType(m.mime).putExtra(Intent.EXTRA_STREAM, m.uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(send, null))
                vm.message.value = null
            }
            null -> {}
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importUris(it) }

    NavHost(nav, startDestination = if (settings.onboarded) "home" else "onboarding") {
        composable("onboarding") {
            OnboardingScreen(vm, onDone = { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } }, onPaywall = { nav.navigate("paywall") })
        }
        composable("home") {
            HomeScreen(
                vm, snackbar,
                onOpen = { nav.navigate("detail/$it?seek=0&q=") },
                onRecord = { nav.navigate("record") },
                onImport = { picker.launch(arrayOf("audio/*", "video/*", "application/ogg")) },
                onSearch = { nav.navigate("search") },
                onSettings = { nav.navigate("settings") },
                onPaywall = { nav.navigate("paywall") },
            )
        }
        composable("record") {
            RecordScreen(vm, onBack = { nav.popBackStack() }, onSaved = { id -> nav.navigate("detail/$id?seek=0&q=") { popUpTo("home") } })
        }
        composable(
            "detail/{id}?seek={seek}&q={q}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("seek") { type = NavType.LongType; defaultValue = 0L },
                navArgument("q") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { e ->
            DetailScreen(vm, e.arguments!!.getLong("id"), e.arguments!!.getLong("seek"), e.arguments!!.getString("q").orEmpty(), onBack = { nav.popBackStack() }, onPaywall = { nav.navigate("paywall") })
        }
        composable("search") {
            SearchScreen(vm, onBack = { nav.popBackStack() }, onOpen = { id, ms, q -> nav.navigate("detail/$id?seek=$ms&q=${Uri.encode(q)}") })
        }
        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }, onPaywall = { nav.navigate("paywall") }) }
        composable("paywall") { PaywallScreen(vm, onBack = { nav.popBackStack() }) }
    }
}
