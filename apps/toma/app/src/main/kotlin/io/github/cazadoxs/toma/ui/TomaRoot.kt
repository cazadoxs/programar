package io.github.cazadoxs.toma.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import io.github.cazadoxs.toma.R
import io.github.cazadoxs.toma.ui.screens.BackupScreen
import io.github.cazadoxs.toma.ui.screens.BatteryGuideScreen
import io.github.cazadoxs.toma.ui.screens.CaregiverScreen
import io.github.cazadoxs.toma.ui.screens.HistoryScreen
import io.github.cazadoxs.toma.ui.screens.MedEditorScreen
import io.github.cazadoxs.toma.ui.screens.MedsScreen
import io.github.cazadoxs.toma.ui.screens.OnboardingScreen
import io.github.cazadoxs.toma.ui.screens.PlusScreen
import io.github.cazadoxs.toma.ui.screens.ProfilesScreen
import io.github.cazadoxs.toma.ui.screens.SettingsScreen
import io.github.cazadoxs.toma.ui.screens.TodayScreen

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    Today("today", R.string.tab_today, Icons.Rounded.Today),
    Meds("meds", R.string.tab_meds, Icons.Rounded.Medication),
    History("history", R.string.tab_history, Icons.Rounded.History),
    Settings("settings", R.string.tab_settings, Icons.Rounded.Settings),
}

@Composable
fun TomaRoot(vm: AppViewModel) {
    val prefs by vm.prefs.collectAsState()
    if (!prefs.onboardingDone) {
        OnboardingScreen(vm)
        return
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = Tab.entries.any { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Today.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Today.route) {
                TodayScreen(vm, onAddMed = { nav.navigate("med/0") }, onOpenGuide = { nav.navigate("battery") }, onOpenMed = { nav.navigate("med/$it") })
            }
            composable(Tab.Meds.route) { MedsScreen(vm, onOpen = { nav.navigate("med/$it") }, onAdd = { nav.navigate("med/0") }) }
            composable(Tab.History.route) { HistoryScreen(vm) }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    vm,
                    onProfiles = { nav.navigate("profiles") },
                    onCaregiver = { nav.navigate("caregiver") },
                    onBackup = { nav.navigate("backup") },
                    onPlus = { nav.navigate("plus") },
                    onBattery = { nav.navigate("battery") },
                )
            }
            composable("med/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                MedEditorScreen(vm, e.arguments?.getLong("id") ?: 0L, onClose = { nav.popBackStack() }, onPlus = { nav.navigate("plus") })
            }
            composable("profiles") { ProfilesScreen(vm, onBack = { nav.popBackStack() }) }
            composable("caregiver") { CaregiverScreen(vm, onBack = { nav.popBackStack() }) }
            composable("backup") { BackupScreen(vm, onBack = { nav.popBackStack() }, onPlus = { nav.navigate("plus") }) }
            composable("plus") { PlusScreen(vm, onBack = { nav.popBackStack() }) }
            composable("battery") { BatteryGuideScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
