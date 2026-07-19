package com.souru.colorhunt.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.souru.colorhunt.R
import com.souru.colorhunt.ui.collage.CollageScreen
import com.souru.colorhunt.ui.imports.SortScreen

/**
 * Top-level destinations. Phase 1 ships Sort and Collage; Phase 2 (Grid) and
 * Phase 3 (Map) slot in here as extra tabs.
 */
enum class TopDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Sort("sort", R.string.tab_sort, Icons.Filled.PhotoLibrary),
    Collage("collage", R.string.tab_collage, Icons.Filled.GridView),
}

@Composable
fun ColorHuntApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopDestination.entries.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Sort.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopDestination.Sort.route) {
                SortScreen(onOpenCollage = {
                    navController.navigate(TopDestination.Collage.route) {
                        launchSingleTop = true
                    }
                })
            }
            composable(TopDestination.Collage.route) {
                CollageScreen()
            }
        }
    }
}
