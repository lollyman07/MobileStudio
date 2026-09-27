package com.mobilestudio.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mobilestudio.app.ui.recordings.RecordingsScreen
import com.mobilestudio.app.ui.scenes.ScenesScreen
import com.mobilestudio.app.ui.settings.SettingsScreen
import com.mobilestudio.app.ui.sources.SourcesScreen
import com.mobilestudio.app.ui.studio.StudioScreen
import com.mobilestudio.app.ui.studio.StudioViewModel

private sealed class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Studio : Tab("studio", "Studio", Icons.Filled.Videocam)
    object Recordings : Tab("recordings", "Recordings", Icons.Filled.Movie)
    object Settings : Tab("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun MobileStudioNavHost() {
    val navController = rememberNavController()
    val viewModel: StudioViewModel = viewModel()
    val tabs = listOf(Tab.Studio, Tab.Recordings, Tab.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = Tab.Studio.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Studio.route) {
                StudioScreen(
                    viewModel = viewModel,
                    onOpenScenes = { navController.navigate("scenes") },
                    onOpenSources = { navController.navigate("sources") },
                    onOpenSettings = { navController.navigate(Tab.Settings.route) }
                )
            }
            composable("scenes") { ScenesScreen(viewModel = viewModel, onBack = { navController.popBackStack() }) }
            composable("sources") { SourcesScreen(viewModel = viewModel, onBack = { navController.popBackStack() }) }
            composable(Tab.Recordings.route) { RecordingsScreen() }
            composable(Tab.Settings.route) { SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() }) }
        }
    }
}
