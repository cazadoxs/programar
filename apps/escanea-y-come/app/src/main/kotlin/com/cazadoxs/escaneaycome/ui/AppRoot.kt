package com.cazadoxs.escaneaycome.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cazadoxs.escaneaycome.AppContainer
import com.cazadoxs.escaneaycome.core.Meal
import com.cazadoxs.escaneaycome.scan.ScanOutcome
import com.cazadoxs.escaneaycome.scan.Scanner
import com.cazadoxs.escaneaycome.ui.custom.CustomFoodScreen
import com.cazadoxs.escaneaycome.ui.food.FoodScreen
import com.cazadoxs.escaneaycome.ui.onboarding.OnboardingScreen
import com.cazadoxs.escaneaycome.ui.plus.PlusScreen
import com.cazadoxs.escaneaycome.ui.progress.ProgressScreen
import com.cazadoxs.escaneaycome.ui.search.ManualCodeDialog
import com.cazadoxs.escaneaycome.ui.search.SearchScreen
import com.cazadoxs.escaneaycome.ui.settings.SettingsScreen
import com.cazadoxs.escaneaycome.ui.today.TodayScreen
import kotlinx.coroutines.launch
import java.time.LocalTime

object Routes {
    const val TODAY = "hoy"
    const val SEARCH = "buscar?meal={meal}"
    const val PROGRESS = "progreso"
    const val SETTINGS = "ajustes"
    const val FOOD = "alimento/{id}?meal={meal}&entry={entry}"
    const val CUSTOM = "crear?barcode={barcode}&id={id}&name={name}"
    const val PLUS = "plus"

    fun search(meal: Meal? = null) = "buscar?meal=${meal?.name.orEmpty()}"
    fun food(id: Long, meal: Meal? = null, entry: Long = 0) = "alimento/$id?meal=${meal?.name.orEmpty()}&entry=$entry"
    fun custom(barcode: String? = null, id: Long = 0, name: String? = null) =
        "crear?barcode=${barcode.orEmpty()}&id=$id&name=${android.net.Uri.encode(name.orEmpty())}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Hoy", Icons.Outlined.WbSunny),
    Tab(Routes.SEARCH, "Buscar", Icons.Outlined.Search),
    Tab(Routes.PROGRESS, "Progreso", Icons.Outlined.Insights),
    Tab(Routes.SETTINGS, "Ajustes", Icons.Outlined.Settings),
)

@Composable
fun AppRoot(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    if (!settings.onboarded) {
        OnboardingScreen(onDone = { profile -> container.settings.update { profile.copy(onboarded = true) } })
        return
    }

    val nav = rememberNavController()
    val main = appViewModel { MainViewModel(it) }
    val lookup by main.lookup.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manualEntry by rememberSaveable { mutableStateOf(false) }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    fun scan() {
        scope.launch {
            when (val r = Scanner.scan(context)) {
                is ScanOutcome.Code -> main.lookup(r.value)
                ScanOutcome.Cancelled -> Unit
                is ScanOutcome.Failed -> {
                    snackbar.showSnackbar(r.message)
                    manualEntry = true
                }
            }
        }
    }

    LaunchedEffect(lookup) {
        when (val l = lookup) {
            is LookupState.Open -> {
                main.consumeLookup()
                nav.navigate(Routes.food(l.foodId))
            }
            is LookupState.Error -> {
                main.consumeLookup()
                snackbar.showSnackbar(l.message)
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) BottomBar(nav, route, onScan = ::scan)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.padding(padding)) {
                composable(Routes.TODAY) {
                    TodayScreen(
                        main = main,
                        onAdd = { meal -> nav.navigate(Routes.search(meal)) },
                        onScan = ::scan,
                        onOpenEntry = { e -> e.foodId?.let { nav.navigate(Routes.food(it, Meal.fromName(e.meal), e.id)) } },
                    )
                }
                composable(
                    Routes.SEARCH,
                    arguments = listOf(navArgument("meal") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    val meal = entry.arguments?.getString("meal")?.takeIf { it.isNotEmpty() }?.let { Meal.fromName(it) }
                    SearchScreen(
                        main = main,
                        meal = meal,
                        onOpenFood = { id -> nav.navigate(Routes.food(id, meal)) },
                        onCreate = { name -> nav.navigate(Routes.custom(name = name)) },
                        onScan = ::scan,
                        onManualCode = { manualEntry = true },
                        onBack = if (meal != null) ({ nav.popBackStack() }) else null,
                    )
                }
                composable(Routes.PROGRESS) {
                    ProgressScreen(onPlus = { nav.navigate(Routes.PLUS) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onPlus = { nav.navigate(Routes.PLUS) },
                        onEditFood = { id -> nav.navigate(Routes.custom(id = id)) },
                        onCreateFood = { nav.navigate(Routes.custom()) },
                    )
                }
                composable(
                    Routes.FOOD,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("meal") { type = NavType.StringType; defaultValue = "" },
                        navArgument("entry") { type = NavType.LongType; defaultValue = 0L },
                    ),
                ) { entry ->
                    val args = entry.arguments
                    val mealArg = args?.getString("meal")?.takeIf { it.isNotEmpty() }
                    val now = LocalTime.now()
                    FoodScreen(
                        foodId = args?.getLong("id") ?: 0,
                        entryId = args?.getLong("entry") ?: 0,
                        initialMeal = mealArg?.let { Meal.fromName(it) } ?: Meal.forTime(now.hour, now.minute),
                        main = main,
                        onDone = { message ->
                            nav.popBackStack(Routes.TODAY, inclusive = false)
                            scope.launch { snackbar.showSnackbar(message) }
                        },
                        onEdit = { id -> nav.navigate(Routes.custom(id = id)) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    Routes.CUSTOM,
                    arguments = listOf(
                        navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val args = entry.arguments
                    CustomFoodScreen(
                        foodId = args?.getLong("id") ?: 0,
                        barcode = args?.getString("barcode").orEmpty(),
                        initialName = args?.getString("name").orEmpty(),
                        onSaved = { id ->
                            nav.popBackStack()
                            nav.navigate(Routes.food(id))
                        },
                        onDeleted = { nav.popBackStack(Routes.TODAY, inclusive = false) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.PLUS) {
                    PlusScreen(onBack = { nav.popBackStack() })
                }
            }

            if (lookup == LookupState.Loading) LoadingOverlay()
        }
    }

    (lookup as? LookupState.Missing)?.let { missing ->
        AlertDialog(
            onDismissRequest = main::consumeLookup,
            title = { Text("No lo encontramos") },
            text = {
                Text(
                    (missing.name?.let { "«$it» está en Open Food Facts pero sin tabla nutricional. " }
                        ?: "Este código aún no está en Open Food Facts. ") +
                        "Puedes crearlo tú en 20 segundos copiando la etiqueta y quedará guardado para la próxima vez.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    main.consumeLookup()
                    nav.navigate(Routes.custom(barcode = missing.barcode, name = missing.name))
                }) { Text("Crear alimento") }
            },
            dismissButton = { TextButton(onClick = main::consumeLookup) { Text("Ahora no") } },
        )
    }

    if (manualEntry) {
        ManualCodeDialog(
            onDismiss = { manualEntry = false },
            onCode = { code ->
                manualEntry = false
                main.lookup(code)
            },
        )
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?, onScan: () -> Unit) {
    Box(contentAlignment = Alignment.TopCenter) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            tabs.forEachIndexed { i, tab ->
                if (i == 2) Spacer(Modifier.weight(1f))
                NavigationBarItem(
                    selected = route == tab.route,
                    onClick = {
                        nav.navigate(if (tab.route == Routes.SEARCH) Routes.search() else tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label) },
                )
            }
        }
        LargeFloatingActionButton(
            onClick = onScan,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.offset(y = (-22).dp).size(76.dp),
        ) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Escanear código de barras", modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, shadowElevation = 12.dp) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(14.dp))
                Text("Buscando en Open Food Facts…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

