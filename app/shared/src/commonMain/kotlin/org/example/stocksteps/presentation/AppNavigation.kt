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
import org.example.stocksteps.designsystem.components.StockBottomNavigation
import org.example.stocksteps.designsystem.components.StockBottomNavigationItem
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppNavigation(
    baseUrl: String?,
    hinge: WindowHinge?,
    navigationIcon: @Composable (MainDestination) -> Unit,
    accounts: AccountDependencies?,
    backIcon: @Composable () -> Unit,
    themePreferences: org.example.stocksteps.settings.ThemePreferenceStore,
    appVersion: String?
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

    val isHome = destination?.hasRoute<DiscoveryRoute>() == true
    val isSettings = destination?.hasRoute<SettingsRoute>() == true
    // Scaffold applies status/navigation-bar insets once and consumes them for the content,
    // so screens' own safe-content padding does not double them.
    Scaffold(
        containerColor = StockStepsTheme.colors.appBackground,
        topBar = {
            StockStepsTopBar(
                configuration = AppBarConfiguration(
                    title = when {
                        isSearch -> "Search stocks"
                        destination?.hasRoute<WatchListRoute>() == true -> "Watchlist"
                        destination?.hasRoute<LearnRoute>() == true -> "Learn"
                        destination?.hasRoute<SettingsRoute>() == true -> "Settings"
                        else -> "Home"
                    },
                    // Home and Settings render their own compact headers instead of a title bar.
                    visible = !isAuth && !isHome && !isSettings,
                    backButton = if (isSearch) AppBarBackButton.BACK else AppBarBackButton.NONE
                ),
                onBack = { navController.popBackStack() },
                backIcon = backIcon
            )
        },
        bottomBar = {
            if (destination != null && !isSearch && !isAuth) {
                StockBottomNavigation {
                    MainDestination.entries.forEach { tab ->
                        val selected = when (tab) {
                            MainDestination.HOME -> isHome
                            MainDestination.WATCHLIST -> destination.hasRoute<WatchListRoute>()
                            MainDestination.LEARN -> destination.hasRoute<LearnRoute>()
                            MainDestination.SETTINGS -> destination.hasRoute<SettingsRoute>()
                        }
                        StockBottomNavigationItem(
                            selected = selected,
                            label = stringResource(tab.labelResource()),
                            onClick = { openTab(tab) },
                            icon = { navigationIcon(tab) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable<DiscoveryRoute> {
                if (accounts != null) HomeScene(
                    baseUrl = baseUrl,
                    accounts = accounts,
                    hinge = hinge,
                    onLearn = { openTab(MainDestination.LEARN) },
                    onExplore = { stock ->
                        navController.navigate(StockSearchRoute(stock.symbol, stock.name, stock.exchange, stock.currency, stock.exchangeFullName)) {
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
                    onExplore = { stock -> navController.navigate(StockSearchRoute(stock.symbol, stock.name ?: stock.symbol, stock.exchange, stock.currency, stock.exchangeFullName)) }
                )
            }
            composable<LearnRoute> { LearnScene(hinge) }
            composable<SettingsRoute> {
                if (accounts != null) SettingsScene(
                    accounts = accounts,
                    themePreferences = themePreferences,
                    appVersion = appVersion,
                    hinge = hinge,
                    onSignIn = { navController.navigate(AuthRoute()) }
                )
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
    }
}

private fun MainDestination.labelResource(): StringResource = when (this) {
    MainDestination.HOME -> Res.string.nav_home
    MainDestination.WATCHLIST -> Res.string.nav_watchlist
    MainDestination.LEARN -> Res.string.nav_learn
    MainDestination.SETTINGS -> Res.string.nav_settings
}
