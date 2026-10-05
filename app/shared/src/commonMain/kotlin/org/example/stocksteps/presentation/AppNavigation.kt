package org.example.stocksteps.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.presentation.account.*
import org.example.stocksteps.MainDestination
import org.example.stocksteps.presentation.watchlist.*
import org.example.stocksteps.presentation.learn.*
import org.example.stocksteps.presentation.settings.*
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.presentation.discovery.DiscoveryRoute
import org.example.stocksteps.presentation.discovery.DiscoveryScene
import org.example.stocksteps.presentation.home.HomeScene
import org.example.stocksteps.presentation.stocksearch.StockSearchRoute
import org.example.stocksteps.presentation.stocksearch.StockSearchScene
import org.example.stocksteps.model.*
import org.example.stocksteps.presentation.components.StockStepsTopBar

@Composable
internal fun AppNavigation(
    baseUrl: String?,
    hinge: WindowHinge?,
    navigationIcon: @Composable (MainDestination) -> Unit,
    accounts: AccountDependencies?,
    backIcon: @Composable () -> Unit
) {
    val session = accounts?.auth?.session?.collectAsStateWithLifecycle()?.value
    if (session?.initializing == true) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    // Choose once after Firebase restoration; later account changes must not reset navigation.
    val initialRoute: Any = remember {
        if (accounts == null || session?.user != null) DiscoveryRoute else AuthRoute()
    }
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

    val isAuth = destination?.hasRoute<AuthRoute>() == true
    val isSearch = destination?.hasRoute<StockSearchRoute>() == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        StockStepsTopBar(
            configuration = AppBarConfiguration(
                title = when {
                    isSearch -> "Search stocks"
                    destination?.hasRoute<WatchListRoute>() == true -> "WatchList"
                    destination?.hasRoute<LearnRoute>() == true -> "Learn"
                    destination?.hasRoute<SettingsRoute>() == true -> "Settings"
                    else -> "Home"
                },
                visible = !isAuth,
                backButton = if (isSearch) AppBarBackButton.BACK else AppBarBackButton.NONE
            ),
            onBack = { navController.popBackStack() },
            backIcon = backIcon
        )
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier.weight(1f)
        ) {
            composable<DiscoveryRoute> {
                if (accounts != null) HomeScene(
                    baseUrl = baseUrl,
                    accounts = accounts,
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
            composable<WatchListRoute> {
                if (accounts != null) WatchListScene(
                    accounts = accounts,
                    hinge = hinge,
                    onSignIn = { navController.navigate(AuthRoute()) },
                    onSearch = { navController.navigate(StockSearchRoute()) },
                    onExplore = { navController.navigate(StockSearchRoute(initialSymbol = it)) }
                )
            }
            composable<LearnRoute> { LearnScene(hinge) }
            composable<SettingsRoute> {
                if (accounts != null) SettingsScene(accounts, hinge) { navController.navigate(AuthRoute()) }
            }
            composable<AuthRoute> { entry ->
                if (accounts != null) AuthScene(accounts, entry.toRoute<AuthRoute>(), hinge) {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(DiscoveryRoute) {
                            popUpTo<AuthRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
            composable<StockSearchRoute> { entry ->
                StockSearchScene(
                    route = entry.toRoute<StockSearchRoute>(),
                    baseUrl = baseUrl,
                    hinge = hinge,
                    accounts = accounts
                )
            }
        }
        if (destination != null && !isSearch && !isAuth) {
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
