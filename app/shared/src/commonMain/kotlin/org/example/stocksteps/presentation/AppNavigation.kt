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
import org.example.stocksteps.presentation.portfolio.*
import org.example.stocksteps.MainDestination
import org.example.stocksteps.presentation.watchlist.*
import org.example.stocksteps.presentation.learn.*
import org.example.stocksteps.presentation.settings.*
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.presentation.discovery.DiscoveryRoute
import org.example.stocksteps.presentation.discovery.DiscoveryScene
import org.example.stocksteps.presentation.home.HomeScene
import org.example.stocksteps.presentation.stocksearch.StockSearchRoute
import org.example.stocksteps.presentation.companydetails.*
import org.example.stocksteps.presentation.markets.MarketsRoute
import org.example.stocksteps.presentation.markets.MarketsScene
import org.example.stocksteps.presentation.stocksearch.StockSearchScene
import org.example.stocksteps.model.*
import org.example.stocksteps.presentation.components.StockStepsTopBar
import org.example.stocksteps.designsystem.components.StockBottomNavigation
import org.example.stocksteps.designsystem.components.StockSampleDataBanner
import org.example.stocksteps.presentation.backend.BackendInfoViewModel
import org.example.stocksteps.di.StockStepsDependencies
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.designsystem.components.StockBottomNavigationItem
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppNavigation(
    backend: org.example.stocksteps.settings.BackendRouter,
    hinge: WindowHinge?,
    navigationIcon: @Composable (MainDestination) -> Unit,
    accounts: AccountDependencies?,
    backIcon: @Composable () -> Unit,
    themePreferences: org.example.stocksteps.settings.ThemePreferenceStore,
    appVersion: String?,
    appLock: org.example.stocksteps.security.AppLockManager? = null,
    notifications: org.example.stocksteps.presentation.watchlist.NotificationAccess? = null,
    /** Symbols from tapped alert notifications; each opens that stock's alerts. */
    notificationLinks: kotlinx.coroutines.flow.Flow<String>? = null
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
    // Signing out (or Firebase ending the session) always lands on Login with an empty back stack,
    // so Back can't reopen screens from the previous account.
    var previousUser by remember { mutableStateOf(session?.user?.id) }
    LaunchedEffect(session?.user?.id) {
        val current = session?.user?.id
        if (previousUser != null && current == null) {
            navController.navigate(AuthRoute()) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        previousUser = current
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    fun openTab(tab: MainDestination) {
        val route: Any = when (tab) {
            MainDestination.HOME -> DiscoveryRoute
            MainDestination.PORTFOLIO -> PortfolioRoute
            MainDestination.MARKETS -> MarketsRoute
            MainDestination.WATCHLIST -> WatchListRoute
            MainDestination.LEARN -> LearnRoute
        }
        navController.navigate(route) {
            popUpTo(DiscoveryRoute) { saveState = true }
            launchSingleTop = true
            restoreState = tab != MainDestination.HOME
        }
    }

    val isAuth = destination?.hasRoute<AuthRoute>() == true
    val isSearch = destination?.hasRoute<StockSearchRoute>() == true || destination?.hasRoute<PortfolioSearchRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.practice.PracticeSearchRoute>() == true

    val isHome = destination?.hasRoute<DiscoveryRoute>() == true
    // Keyed by environment: switching creates fresh scene models (fresh data), while every
    // client resolves the backend URL per request, so no request uses the previous backend.
    val storedEnvironment by backend.selection.collectAsStateWithLifecycle()
    val environment = backend.effective(storedEnvironment)
    val backendInfo = viewModel(key = "backend-info:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        BackendInfoViewModel(data.getBackendInfo(), data::close)
    }
    val sampleData by backendInfo.isMock.collectAsStateWithLifecycle()
    val isSettings = destination?.hasRoute<SettingsRoute>() == true
    val isMarkets = destination?.hasRoute<MarketsRoute>() == true
    val isWatchlist = destination?.hasRoute<WatchListRoute>() == true
    val isAlerts = destination?.hasRoute<org.example.stocksteps.presentation.watchlist.AlertsRoute>() == true
    androidx.compose.runtime.LaunchedEffect(notificationLinks) {
        // "brief:<id>" opens a Daily Market Brief, "earnings:<eventId>" an earnings event, "earnings-calendar" the calendar;
        // anything else is an alert's stock symbol.
        notificationLinks?.collect { link ->
            when {
                link.startsWith("brief:") -> navController.navigate(org.example.stocksteps.presentation.brief.DailyBriefRoute(link.removePrefix("brief:").takeIf { it.isNotBlank() }))
                link.startsWith("earnings-results:") -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(link.removePrefix("earnings-results:"))?.let { navController.navigate(it) }
                link.startsWith("earnings:") -> navController.navigate(org.example.stocksteps.presentation.earnings.EarningsEventRoute(link.removePrefix("earnings:")))
                link == "earnings-calendar" -> navController.navigate(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute())
                // Weekly digest (StockSteps+): the app fetches the digest with the signed-in token.
                link == "earnings-digest" -> navController.navigate(org.example.stocksteps.presentation.earnings.EarningsDigestRoute)
                else -> navController.navigate(org.example.stocksteps.presentation.watchlist.AlertsRoute(link))
            }
        }
    }
    val isCompanyDetails = destination?.hasRoute<CompanyDetailsRoute>() == true
    val isCompanyFinancials = destination?.hasRoute<CompanyFinancialsRoute>() == true
    val isCompanyValuation = destination?.hasRoute<CompanyValuationRoute>() == true
    val isCompanyNews = destination?.hasRoute<CompanyNewsRoute>() == true
    val isNewsInsight = destination?.hasRoute<NewsInsightRoute>() == true
    val isMovement = destination?.hasRoute<StockMovementRoute>() == true
    val hasBack = isSearch || isCompanyFinancials || isCompanyNews || isSettings || destination?.hasRoute<PortfolioEntryRoute>() == true || destination?.hasRoute<HoldingDetailsRoute>() == true || destination?.hasRoute<PortfolioInsightsRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.screener.ScreenerRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.screener.ComparisonRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsCalendarRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDetailsRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsEventRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsResultsRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsRemindersRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDigestRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDigestSettingsRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.research.GuidedResearchRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.practice.PracticeRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.brief.DailyBriefRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.practice.PracticeOrderRoute>() == true
    // Practice needs an account: the server keeps the simulated ledger.
    val openPractice: () -> Unit = { if (accounts?.auth?.session?.value?.user == null) navController.navigate(AuthRoute()) else navController.navigate(org.example.stocksteps.presentation.practice.PracticeRoute) { launchSingleTop = true } }
    val practiceTrade: (String, String) -> Unit = { symbol, side -> if (accounts?.auth?.session?.value?.user == null) navController.navigate(AuthRoute()) else navController.navigate(org.example.stocksteps.presentation.practice.PracticeOrderRoute(symbol, side)) }
    val backToPractice: () -> Unit = { navController.navigate(org.example.stocksteps.presentation.practice.PracticeRoute) { popUpTo<org.example.stocksteps.presentation.practice.PracticeRoute> { inclusive = true }; launchSingleTop = true } }
    val openResearch: (String, String?) -> Unit = { symbol, name -> navController.navigate(org.example.stocksteps.presentation.research.GuidedResearchRoute(symbol, name)) }
    // Every stock tap (Home movers, Watchlist, Search) opens the same Company Details page.
    val addPortfolio: (InstrumentRef) -> Unit = { instrument ->
        navController.navigate(PortfolioEntryRoute(instrument.symbol, instrument.name, instrument.exchange, instrument.currency))
    }
    val openStock: (String) -> Unit = { symbol -> navController.navigate(CompanyDetailsRoute(symbol)) }
    // Scaffold applies status/navigation-bar insets once and consumes them for the content,
    // so screens' own safe-content padding does not double them.
    Scaffold(
        containerColor = StockStepsTheme.colors.appBackground,
        topBar = {
            StockStepsTopBar(
                configuration = AppBarConfiguration(
                    title = when {
                        destination?.hasRoute<PortfolioEntryRoute>() == true -> "Add transaction"
                        destination?.hasRoute<HoldingDetailsRoute>() == true -> "Holding details"
                        destination?.hasRoute<PortfolioInsightsRoute>() == true -> "Insights"
                        destination?.hasRoute<org.example.stocksteps.presentation.screener.ScreenerRoute>() == true -> "Discover"
                        destination?.hasRoute<org.example.stocksteps.presentation.screener.ComparisonRoute>() == true -> "Compare"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsCalendarRoute>() == true -> "Earnings"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsEventRoute>() == true -> "Earnings event"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsResultsRoute>() == true -> "Earnings results"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsRemindersRoute>() == true -> "Earnings reminders"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDigestRoute>() == true -> "Earnings digest"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDigestSettingsRoute>() == true -> "Earnings digest & AI"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDetailsRoute>() == true -> "Earnings details"
                        destination?.hasRoute<org.example.stocksteps.presentation.research.GuidedResearchRoute>() == true -> "Research"
                        destination?.hasRoute<org.example.stocksteps.presentation.practice.PracticeRoute>() == true -> "Practice"
                        destination?.hasRoute<org.example.stocksteps.presentation.brief.DailyBriefRoute>() == true -> "Daily Brief"
                        destination?.hasRoute<org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute>() == true -> "Previous briefs"
                        destination?.hasRoute<org.example.stocksteps.presentation.practice.PracticeOrderRoute>() == true -> "Practice order"
                        destination?.hasRoute<PortfolioRoute>() == true -> "Portfolio"
                        isSearch -> "Search stocks"
                        isCompanyFinancials -> "Financials"
                        isCompanyNews -> "Company news"
                        destination?.hasRoute<WatchListRoute>() == true -> "Watchlist"
                        destination?.hasRoute<LearnRoute>() == true -> "Learn"
                        destination?.hasRoute<SettingsRoute>() == true -> "Settings"
                        else -> "Home"
                    },
                    // Home, Settings, Company Details and Financials render their own headers.
                    visible = !isAuth && !isHome && destination?.hasRoute<LearnRoute>() != true && destination?.hasRoute<PortfolioRoute>() != true && !isMarkets && !isWatchlist && !isAlerts && !isCompanyDetails && !isCompanyFinancials && !isCompanyValuation && !isNewsInsight && !isMovement,
                    backButton = if (hasBack) AppBarBackButton.BACK else AppBarBackButton.NONE
                ),
                onBack = { navController.popBackStack() },
                backIcon = backIcon
            )
        },
        bottomBar = {
            val showTabs = destination != null && !isSearch && !isAuth
            Column {
                if (sampleData) {
                    StockSampleDataBanner(
                        text = stringResource(Res.string.sample_data_banner),
                        modifier = if (showTabs) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    )
                }
                if (showTabs) {
                    StockBottomNavigation {
                        MainDestination.entries.forEach { tab ->
                            val selected = when (tab) {
                                MainDestination.HOME -> isHome
                                MainDestination.PORTFOLIO -> destination.hasRoute<PortfolioRoute>()
                                MainDestination.MARKETS -> isMarkets
                                MainDestination.WATCHLIST -> destination.hasRoute<WatchListRoute>()
                                MainDestination.LEARN -> destination.hasRoute<LearnRoute>()
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
        }
    ) { innerPadding ->
      androidx.compose.runtime.CompositionLocalProvider(org.example.stocksteps.presentation.earnings.LocalNotificationAccess provides notifications) {
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable<DiscoveryRoute> {
                val homeLocked = appLock?.state?.collectAsStateWithLifecycle()?.value == org.example.stocksteps.security.AppLockState.LOCKED
                if (accounts != null && !homeLocked) HomeScene(
                    backend = backend,
                    environment = environment,
                    accounts = accounts,
                    hinge = hinge,
                    onLearn = { openTab(MainDestination.LEARN) },
                    onSearch = { navController.navigate(StockSearchRoute()) },
                    onWatchlist = { openTab(MainDestination.WATCHLIST) },
                    onPortfolio = { openTab(MainDestination.PORTFOLIO) },
                    onSettings = { navController.navigate(SettingsRoute) },
                    onAlerts = { symbol -> navController.navigate(AlertsRoute(symbol)) },
                    onExplore = openStock,
                    onEarnings = { symbol -> navController.navigate(org.example.stocksteps.presentation.earnings.EarningsDetailsRoute(symbol)) },
                    onResearch = openResearch,
                    onPractice = openPractice,
                    onDailyBrief = { navController.navigate(org.example.stocksteps.presentation.brief.DailyBriefRoute()) }
                )
            }
            composable<PortfolioRoute> {
                if (accounts != null) PortfolioScene(accounts,
                    onAdd = { navController.navigate(PortfolioEntryRoute()) },
                    onHolding = { account, symbol -> navController.navigate(HoldingDetailsRoute(account, symbol)) },
                    onOpenCompany = openStock, hinge = hinge,
                    onSignIn = { navController.navigate(AuthRoute()) }, onAlerts = { navController.navigate(AlertsRoute(it)) },
                    onInsights = { navController.navigate(PortfolioInsightsRoute) },
                    onPractice = openPractice)
            }
            composable<org.example.stocksteps.presentation.screener.ScreenerRoute> { entry ->
                org.example.stocksteps.presentation.screener.ScreenerScene(
                    route = entry.toRoute(), backend = backend, environment = environment, accounts = accounts, hinge = hinge,
                    onOpenStock = openStock,
                    onCompare = { navController.navigate(org.example.stocksteps.presentation.screener.ComparisonRoute) },
                    onAddPortfolio = addPortfolio,
                    onSignIn = { navController.navigate(AuthRoute()) }
                )
            }
            composable<org.example.stocksteps.presentation.screener.ComparisonRoute> {
                org.example.stocksteps.presentation.screener.ComparisonScene(backend, environment, hinge, onOpenStock = openStock,
                    onDiscover = { navController.navigate(org.example.stocksteps.presentation.screener.ScreenerRoute()) })
            }
            composable<PortfolioInsightsRoute> {
                if (accounts != null) PortfolioInsightsScene(accounts, onOpenCompany = openStock, onSignIn = { navController.navigate(AuthRoute()) }, hinge = hinge)
            }
            composable<HoldingDetailsRoute> { entry ->
                if (accounts != null) PortfolioScene(accounts,
                    onAdd = { navController.navigate(PortfolioEntryRoute()) },
                    onHolding = { _, _ -> }, onOpenCompany = openStock, holding = entry.toRoute(), hinge = hinge,
                    onSignIn = { navController.navigate(AuthRoute()) }, onAlerts = { navController.navigate(AlertsRoute(it)) })
            }
            composable<org.example.stocksteps.presentation.practice.PracticeSearchRoute> {
                StockSearchScene(
                    route = StockSearchRoute(),
                    backend = backend,
                    environment = environment,
                    hinge = hinge,
                    accounts = accounts,
                    onOpenStock = { stock ->
                        navController.navigate(org.example.stocksteps.presentation.practice.PracticeOrderRoute(stock.symbol, "BUY")) { popUpTo<org.example.stocksteps.presentation.practice.PracticeSearchRoute> { inclusive = true } }
                    }
                )
            }
            composable<PortfolioSearchRoute> {
                StockSearchScene(
                    route = StockSearchRoute(),
                    backend = backend,
                    environment = environment,
                    hinge = hinge,
                    accounts = accounts,
                    onOpenStock = { stock ->
                        val selected = PortfolioEntryRoute(stock.symbol, stock.name, stock.exchange, stock.currency)
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            "portfolio.selection", arrayOf(selected.symbol, selected.name.orEmpty(), selected.exchange.orEmpty(), selected.currency.orEmpty())
                        )
                        navController.popBackStack()
                    }
                )
            }
            composable<PortfolioEntryRoute> { entry ->
                val selection by entry.savedStateHandle.getStateFlow("portfolio.selection", emptyArray<String>()).collectAsStateWithLifecycle()
                val selected = remember(selection) {
                    selection.takeIf { it.size == 4 }?.let { PortfolioEntryRoute(it[0], it[1], it[2], it[3]) }
                }
                if (accounts != null) PortfolioEntryScene(
                    accounts,
                    selected ?: entry.toRoute(),
                    onSearch = { navController.navigate(PortfolioSearchRoute) },
                    onDone = { navController.popBackStack() }
                )
            }
            composable<MarketsRoute> {
                MarketsScene(
                    backend = backend,
                    environment = environment,
                    hinge = hinge,
                    onOpenStock = openStock,
                    onWhyMoved = { symbol -> navController.navigate(StockMovementRoute(symbol)) },
                    onSearch = { navController.navigate(StockSearchRoute()) },
                    onDiscover = { navController.navigate(org.example.stocksteps.presentation.screener.ScreenerRoute()) },
                    onCompare = { navController.navigate(org.example.stocksteps.presentation.screener.ComparisonRoute) },
                    onEarnings = { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute()) },
                    accounts = accounts,
                    brief = accounts?.dailyBrief,
                    onDailyBrief = { navController.navigate(org.example.stocksteps.presentation.brief.DailyBriefRoute()) },
                    onBriefHistory = { navController.navigate(org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute) }
                )
            }
            composable<org.example.stocksteps.presentation.brief.DailyBriefRoute> { entry ->
                if (accounts != null) org.example.stocksteps.presentation.brief.DailyBriefScene(entry.toRoute(), accounts.dailyBrief, hinge,
                    onOpenStock = openStock,
                    onHistory = { navController.navigate(org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute) },
                    onOpenBrief = { id -> navController.navigate(org.example.stocksteps.presentation.brief.DailyBriefRoute(id)) },
                    onWatchlist = { openTab(MainDestination.WATCHLIST) },
                    onLearn = { openTab(MainDestination.LEARN) },
                    onSignIn = { navController.navigate(AuthRoute()) },
                    onUpgrade = { navController.navigate(SettingsRoute) },
                    onOpenEarnings = { id -> navController.navigate(org.example.stocksteps.presentation.earnings.EarningsEventRoute(id)) },
                    onOpenResults = { id -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(id)?.let { navController.navigate(it) } },
                    onOpenDigest = { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsDigestRoute) })
            }
            composable<org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute> {
                if (accounts != null) org.example.stocksteps.presentation.brief.DailyBriefHistoryScene(accounts.dailyBrief, hinge,
                    onOpenBrief = { id -> navController.navigate(org.example.stocksteps.presentation.brief.DailyBriefRoute(id)) },
                    onUpgrade = { navController.navigate(SettingsRoute) })
            }
            composable<org.example.stocksteps.presentation.earnings.EarningsCalendarRoute> { entry ->
                org.example.stocksteps.presentation.earnings.EarningsCalendarScene(entry.toRoute(), backend, environment, accounts, hinge,
                    onOpenEvent = { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsEventRoute(it)) },
                    onSignIn = { navController.navigate(AuthRoute()) },
                    onDigest = { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsDigestRoute) },
                    onOpenResults = { id -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(id)?.let { navController.navigate(it) } })
            }
            composable<org.example.stocksteps.presentation.earnings.EarningsEventRoute> { entry ->
                org.example.stocksteps.presentation.earnings.EarningsEventScene(entry.toRoute(), backend, environment, accounts, hinge,
                    onOpenCompany = openStock,
                    onOpenCalendar = { date -> navController.navigate(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute(date)) },
                    onOpenResults = { id -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(id)?.let { navController.navigate(it) } },
                    onSignIn = { navController.navigate(AuthRoute()) })
            }
            composable<org.example.stocksteps.presentation.earnings.EarningsResultsRoute> { entry ->
                org.example.stocksteps.presentation.earnings.EarningsResultsScene(entry.toRoute(), backend, environment, accounts, hinge,
                    onOpenCompany = openStock, onLearn = { openTab(MainDestination.LEARN) },
                    // No purchase flow exists yet: Settings shows the plan (and, in MOCK, the simulated plan switch).
                    onUpgrade = { navController.navigate(SettingsRoute) }, onSignIn = { navController.navigate(AuthRoute()) })
            }
            composable<org.example.stocksteps.presentation.earnings.EarningsDetailsRoute> { entry ->
                org.example.stocksteps.presentation.earnings.EarningsDetailsScene(entry.toRoute(), backend, environment, accounts, hinge,
                    onOpenCompany = openStock, onSignIn = { navController.navigate(AuthRoute()) },
                    // No purchase flow exists yet: Settings shows the plan (and, in MOCK, the simulated plan switch).
                    onUpgrade = { navController.navigate(SettingsRoute) })
            }
            composable<WatchListRoute> {
                if (accounts != null) WatchListScene(
                    accounts = accounts,
                    hinge = hinge,
                    notifications = notifications,
                    onSignIn = { navController.navigate(AuthRoute()) },
                    onSearch = { navController.navigate(StockSearchRoute()) },
                    onOpenStock = openStock,
                    onAddPortfolio = addPortfolio,
                    onOpenAlerts = { symbol -> navController.navigate(org.example.stocksteps.presentation.watchlist.AlertsRoute(symbol)) },
                    onEarnings = { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute(filter = "WATCHLIST")) },
                    onEarningsReminders = { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsRemindersRoute) },
                    onCompare = { navController.navigate(org.example.stocksteps.presentation.screener.ComparisonRoute) }
                )
            }
            composable<org.example.stocksteps.presentation.watchlist.AlertsRoute> { entry ->
                if (accounts != null) org.example.stocksteps.presentation.watchlist.AlertsScene(
                    route = entry.toRoute<org.example.stocksteps.presentation.watchlist.AlertsRoute>(),
                    accounts = accounts,
                    hinge = hinge,
                    notifications = notifications,
                    backIcon = backIcon,
                    onBack = { navController.popBackStack() },
                    onOpenStock = openStock,
                    onSignIn = { navController.navigate(AuthRoute()) }
                )
            }
            composable<LearnRoute> {
                LearnScene(hinge, accounts, onResearch = openResearch, onSearch = { navController.navigate(StockSearchRoute()) }, onPractice = openPractice)
            }
            composable<org.example.stocksteps.presentation.practice.PracticeRoute> {
                if (accounts != null) org.example.stocksteps.presentation.practice.PracticeScene(accounts, hinge,
                    onBuy = { navController.navigate(org.example.stocksteps.presentation.practice.PracticeSearchRoute) },
                    onExplore = { navController.navigate(org.example.stocksteps.presentation.screener.ScreenerRoute()) },
                    onLearn = { openTab(MainDestination.LEARN) },
                    onSignIn = { navController.navigate(AuthRoute()) },
                    onCompany = openStock,
                    onTrade = { symbol, side -> practiceTrade(symbol, side.name) },
                    onManagePlan = { navController.navigate(SettingsRoute) })
            }
            composable<org.example.stocksteps.presentation.practice.PracticeOrderRoute> { entry ->
                if (accounts != null) org.example.stocksteps.presentation.practice.PracticeOrderScene(entry.toRoute(), environment, accounts, hinge,
                    onViewHoldings = backToPractice,
                    onExplore = { navController.navigate(org.example.stocksteps.presentation.screener.ScreenerRoute()) },
                    onLearn = { openTab(MainDestination.LEARN) },
                    onUpgrade = backToPractice,
                    onBack = { navController.popBackStack() })
            }
            composable<org.example.stocksteps.presentation.research.GuidedResearchRoute> { entry ->
                org.example.stocksteps.presentation.research.GuidedResearchScene(entry.toRoute(), backend, environment, accounts, hinge,
                    onCompany = openStock,
                    onSearch = { navController.navigate(StockSearchRoute()) },
                    onLearn = { openTab(MainDestination.LEARN) },
                    onUpgrade = { navController.navigate(SettingsRoute) })
            }
            composable<SettingsRoute> {
                if (accounts != null) SettingsScene(
                    accounts = accounts,
                    themePreferences = themePreferences,
                    backend = backend,
                    appVersion = appVersion,
                    hinge = hinge,
                    onSignIn = { navController.navigate(AuthRoute()) },
                    links = mapOf(SettingsLink.EARNINGS_REMINDERS to { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsRemindersRoute) },
                        SettingsLink.EARNINGS_DIGEST to { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsDigestSettingsRoute) }),
                    appLock = appLock
                )
            }
            composable<org.example.stocksteps.presentation.earnings.EarningsDigestRoute> {
                org.example.stocksteps.presentation.earnings.EarningsDigestScene(accounts, environment, hinge,
                    onOpenResults = { id -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(id)?.let { navController.navigate(it) } },
                    onOpenEvent = { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsEventRoute(it)) },
                    onSettings = { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsDigestSettingsRoute) },
                    onUpgrade = { navController.navigate(SettingsRoute) },
                    onSignIn = { navController.navigate(AuthRoute()) },
                    onRecentWatchlist = { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute(filter = "WATCHLIST", tab = "REPORTED")) })
            }
            composable<org.example.stocksteps.presentation.earnings.EarningsDigestSettingsRoute> {
                org.example.stocksteps.presentation.earnings.EarningsDigestSettingsScene(accounts, environment, hinge,
                    onUpgrade = { navController.navigate(SettingsRoute) },
                    onSignIn = { navController.navigate(AuthRoute()) },
                    onReminders = { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsRemindersRoute) })
            }
            composable<org.example.stocksteps.presentation.earnings.EarningsRemindersRoute> {
                org.example.stocksteps.presentation.earnings.EarningsRemindersScene(accounts, onSignIn = { navController.navigate(AuthRoute()) },
                    onOpenEvent = { navController.navigate(org.example.stocksteps.presentation.earnings.EarningsEventRoute(it)) })
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
                    backend = backend,
                    environment = environment,
                    hinge = hinge,
                    accounts = accounts,
                    onOpenStock = { stock -> openStock(stock.symbol) }
                )
            }
            composable<CompanyDetailsRoute> { entry ->
                CompanyDetailsScene(
                    route = entry.toRoute<CompanyDetailsRoute>(),
                    backend = backend,
                    environment = environment,
                    accounts = accounts,
                    hinge = hinge,
                    backIcon = backIcon,
                    onBack = { navController.popBackStack() },
                    onOpenFinancials = { symbol -> navController.navigate(CompanyFinancialsRoute(symbol)) },
                    onOpenValuation = { symbol -> navController.navigate(CompanyValuationRoute(symbol)) },
                    onOpenNews = { symbol -> navController.navigate(CompanyNewsRoute(symbol)) },
                    onAddPortfolio = addPortfolio,
                    onOpenMovement = { symbol -> navController.navigate(StockMovementRoute(symbol)) },
                    onCompare = { symbol, name ->
                        org.example.stocksteps.screener.SharedComparisonSelection.instance.add(symbol, name)
                        navController.navigate(org.example.stocksteps.presentation.screener.ComparisonRoute)
                    },
                    onEarnings = { symbol -> navController.navigate(org.example.stocksteps.presentation.earnings.EarningsDetailsRoute(symbol)) },
                    onEarningsCalendar = { date -> navController.navigate(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute(date)) },
                    onEarningsResults = { id -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(id)?.let { navController.navigate(it) } },
                    onSignIn = { navController.navigate(AuthRoute()) },
                    onResearch = openResearch,
                    onPracticeBuy = { symbol -> practiceTrade(symbol, "BUY") }
                )
            }
            composable<CompanyFinancialsRoute> { entry ->
                CompanyFinancialsScene(entry.toRoute<CompanyFinancialsRoute>(), backend, environment, hinge, backIcon, onBack = { navController.popBackStack() })
            }
            composable<CompanyValuationRoute> { entry ->
                CompanyValuationScene(entry.toRoute<CompanyValuationRoute>(), backend, environment, hinge, backIcon, onBack = { navController.popBackStack() })
            }
            composable<CompanyNewsRoute> { entry ->
                CompanyNewsScene(entry.toRoute<CompanyNewsRoute>(), backend, environment, hinge,
                    onOpenInsight = { symbol, articleId -> navController.navigate(NewsInsightRoute(symbol, articleId)) },
                    onOpenMovement = { symbol -> navController.navigate(StockMovementRoute(symbol)) })
            }
            composable<NewsInsightRoute> { entry ->
                NewsInsightScene(entry.toRoute<NewsInsightRoute>(), backend, environment, hinge, backIcon, onBack = { navController.popBackStack() })
            }
            composable<StockMovementRoute> { entry ->
                StockMovementScene(entry.toRoute<StockMovementRoute>(), backend, environment, hinge, backIcon, onBack = { navController.popBackStack() })
            }
        }
      }
    }
}

private fun MainDestination.labelResource(): StringResource = when (this) {
    MainDestination.HOME -> Res.string.nav_home
    MainDestination.PORTFOLIO -> Res.string.nav_portfolio
    MainDestination.MARKETS -> Res.string.nav_markets
    MainDestination.WATCHLIST -> Res.string.nav_watchlist
    MainDestination.LEARN -> Res.string.nav_learn
}
