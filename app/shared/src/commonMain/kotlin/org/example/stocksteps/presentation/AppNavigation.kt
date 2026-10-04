package org.example.stocksteps.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.presentation.discovery.DiscoveryRoute
import org.example.stocksteps.presentation.discovery.DiscoveryScene
import org.example.stocksteps.presentation.stocksearch.StockSearchRoute
import org.example.stocksteps.presentation.stocksearch.StockSearchScene

@Composable
internal fun AppNavigation(baseUrl: String?, hinge: WindowHinge?) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    fun openHome() {
        navController.navigate(DiscoveryRoute) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openSearch() {
        navController.navigate(StockSearchRoute()) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = DiscoveryRoute,
            modifier = Modifier.weight(1f)
        ) {
            composable<DiscoveryRoute> {
                DiscoveryScene(
                    baseUrl = baseUrl,
                    hinge = hinge,
                    onExplore = { symbol ->
                        navController.navigate(StockSearchRoute(initialSymbol = symbol)) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable<StockSearchRoute> { entry ->
                StockSearchScene(
                    route = entry.toRoute<StockSearchRoute>(),
                    baseUrl = baseUrl,
                    hinge = hinge
                )
            }
        }
        NavigationBar {
            NavigationBarItem(
                selected = destination?.hasRoute<DiscoveryRoute>() == true,
                onClick = ::openHome,
                icon = { Text("⌂") },
                label = { Text("Home") }
            )
            NavigationBarItem(
                selected = destination?.hasRoute<StockSearchRoute>() == true,
                onClick = ::openSearch,
                icon = { Text("⌕") },
                label = { Text("Search") }
            )
        }
    }
}
