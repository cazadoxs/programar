package com.cazadoxs.filtro400.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cazadoxs.filtro400.ui.screens.GuideScreen
import com.cazadoxs.filtro400.ui.screens.HistoryScreen
import com.cazadoxs.filtro400.ui.screens.HomeScreen
import com.cazadoxs.filtro400.ui.screens.OnboardingScreen
import com.cazadoxs.filtro400.ui.screens.PremiumScreen
import com.cazadoxs.filtro400.ui.screens.PrivacyScreen
import com.cazadoxs.filtro400.ui.screens.RulesScreen
import com.cazadoxs.filtro400.ui.screens.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "escudo"
    const val RULES = "reglas"
    const val HISTORY = "historial"
    const val SETTINGS = "ajustes"
    const val PREMIUM = "premium"
    const val GUIDE = "guia"
    const val PRIVACY = "privacidad"

    val deepLinkable = setOf(HOME, RULES, HISTORY, SETTINGS, PREMIUM, GUIDE)
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Escudo", Icons.Rounded.Shield),
    Tab(Routes.RULES, "Reglas", Icons.Rounded.Tune),
    Tab(Routes.HISTORY, "Historial", Icons.Rounded.History),
    Tab(Routes.SETTINGS, "Ajustes", Icons.Rounded.Settings),
)

@Composable
fun AppRoot(vm: AppViewModel, host: MainActivity) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val flags by vm.flags.collectAsStateWithLifecycle()
    if (!ready) {
        Box(Modifier.fillMaxSize())
        return
    }
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val pending by host.pendingDestination
    LaunchedEffect(pending, flags?.onboarded) {
        val dest = pending ?: return@LaunchedEffect
        if (flags?.onboarded == true && dest in Routes.deepLinkable) {
            nav.navigate(dest) { launchSingleTop = true }
        }
        host.pendingDestination.value = null
    }

    fun goTab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (current in tabs.map { it.route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { goTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (flags?.onboarded == true) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    host = host,
                    onDone = {
                        vm.finishOnboarding()
                        nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    },
                    onPrivacy = { nav.navigate(Routes.PRIVACY) },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm, host = host,
                    onOpenGuide = { nav.navigate(Routes.GUIDE) },
                    onOpenHistory = { goTab(Routes.HISTORY) },
                    onOpenPremium = { nav.navigate(Routes.PREMIUM) },
                )
            }
            composable(Routes.RULES) { RulesScreen(vm = vm, onOpenPremium = { nav.navigate(Routes.PREMIUM) }) }
            composable(Routes.HISTORY) { HistoryScreen(vm = vm) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    vm = vm, host = host,
                    onOpenPremium = { nav.navigate(Routes.PREMIUM) },
                    onOpenGuide = { nav.navigate(Routes.GUIDE) },
                    onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                )
            }
            composable(Routes.PREMIUM) { PremiumScreen(vm = vm, host = host, onBack = { nav.popBackStack() }) }
            composable(Routes.GUIDE) { GuideScreen(vm = vm, onBack = { nav.popBackStack() }) }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
