package org.example.stocksteps.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import org.example.stocksteps.presentation.welcome.*
import org.example.stocksteps.MainDestination
import org.example.stocksteps.presentation.watchlist.*
import org.example.stocksteps.presentation.learn.*
import org.example.stocksteps.presentation.settings.*
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.presentation.discovery.DiscoveryRoute
import org.example.stocksteps.presentation.discovery.DiscoveryScene
import org.example.stocksteps.presentation.stocksearch.StockSearchRoute
import org.example.stocksteps.presentation.stocksearch.StockSearchScene

@Composable
internal fun AppNavigation(
    baseUrl: String?,
    hinge: WindowHinge?,
    navigationIcon: @Composable (MainDestination) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    fun openTab(tab: MainDestination) {
        val route: Any = when (tab) {
            MainDestination.HOME -> DiscoveryRoute
            MainDestination.WATCHLIST -> WatchListRoute
            MainDestination.LEARN -> LearnRoute
            MainDestination.SETTINGS -> SettingsRoute
        }
        navController.navigate(route) {
            popUpTo(DiscoveryRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isWelcome = destination == null || destination?.hasRoute<WelcomeRoute>() == true
    val isSearch = destination?.hasRoute<StockSearchRoute>() == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isSearch) {
            TextButton(
                modifier = Modifier.statusBarsPadding(),
                onClick = { navController.popBackStack() }
            ) { Text(text = "Back") }
        }
        NavHost(
            navController = navController,
            startDestination = WelcomeRoute,
            modifier = Modifier.weight(1f)
        ) {
            composable<WelcomeRoute> {
                WelcomeScene(hinge = hinge, onStartExploring = {
                    navController.navigate(DiscoveryRoute) {
                        popUpTo<WelcomeRoute> { inclusive = true }
                    }
                })
            }
            composable<DiscoveryRoute> {
                DiscoveryScene(
                    baseUrl = baseUrl,
                    hinge = hinge,
                    onSearch = { navController.navigate(StockSearchRoute()) },
                    onExplore = { symbol ->
                        navController.navigate(StockSearchRoute(initialSymbol = symbol)) {
                            popUpTo(DiscoveryRoute)
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable<WatchListRoute> { WatchListScene(hinge) }
            composable<LearnRoute> { LearnScene(hinge) }
            composable<SettingsRoute> { SettingsScene(hinge) }
            composable<StockSearchRoute> { entry ->
                StockSearchScene(
                    route = entry.toRoute<StockSearchRoute>(),
                    baseUrl = baseUrl,
                    hinge = hinge
                )
            }
        }
        if (!isSearch && !isWelcome) {
            NavigationBar {
                MainDestination.entries.forEach { tab ->
                    val selected = when (tab) {
                        MainDestination.HOME -> destination?.hasRoute<DiscoveryRoute>() == true
                        MainDestination.WATCHLIST -> destination?.hasRoute<WatchListRoute>() == true
                        MainDestination.LEARN -> destination?.hasRoute<LearnRoute>() == true
                        MainDestination.SETTINGS -> destination?.hasRoute<SettingsRoute>() == true
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = { openTab(tab) },
                        icon = { navigationIcon(tab) },
                        label = { Text(text = tab.label) }
                    )
                }
            }
        }
    }
}
