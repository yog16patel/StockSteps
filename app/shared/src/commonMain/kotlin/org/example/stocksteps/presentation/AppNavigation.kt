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
    notificationLinks: kotlinx.coroutines.flow.Flow<String>? = null,
    recentSearches: org.example.stocksteps.search.RecentSearchStore = org.example.stocksteps.search.RecentSearchStore(org.example.stocksteps.search.InMemoryStringStorage())
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
        if (accounts == null || session?.user != null) HomeGraph else AuthRoute()
    }
    val navController = rememberNavController()
    // 5D.5.6C1: five independent tab stacks with canonical tab selection and the cross-tab Back trail (core TabNavigation, plan §4–5).
    val trail = androidx.compose.runtime.saveable.rememberSaveable(saver = TabNavigator.SnapshotSaver) { mutableStateOf(TrailSnapshot()) }
    val portfolioSection = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(org.example.stocksteps.navigation.PortfolioSections.DEFAULT) }
    val nav = remember(navController) { TabNavigator(navController, trail, portfolioSection) }
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
            nav.reset() // no saved tab stack or trail of the previous account survives (I11)
        }
        previousUser = current
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    /** A tab tap or a programmatic jump to a landing page (restores that tab's stack; the visible tab is reselected). */
    fun openTab(tab: MainDestination) = nav.selectTab(org.example.stocksteps.navigation.AppTab.valueOf(tab.name))
    LaunchedEffect(backStackEntry) { nav.sync() } // pops performed anywhere (in-screen Back, pop-to flows) reach the trail model

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
    val isAlerts = destination?.hasRoute<org.example.stocksteps.presentation.watchlist.AlertsRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.watchlist.NotificationsRoute>() == true
    androidx.compose.runtime.LaunchedEffect(notificationLinks) {
        // "brief:<id>" opens a Daily Market Brief, "earnings:<eventId>" an earnings event, "earnings-calendar" the calendar;
        // anything else is an alert's stock symbol.
        notificationLinks?.collect { link ->
            when {
                link.startsWith("brief:") -> nav.open(org.example.stocksteps.presentation.brief.DailyBriefRoute(link.removePrefix("brief:").takeIf { it.isNotBlank() }))
                link.startsWith("earnings-results:") -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(link.removePrefix("earnings-results:"))?.let { nav.open(it) }
                link.startsWith("earnings:") -> nav.open(org.example.stocksteps.presentation.earnings.EarningsEventRoute(link.removePrefix("earnings:")))
                link == "earnings-calendar" -> nav.open(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute())
                // Weekly digest (StockSteps+): the app fetches the digest with the signed-in token.
                link == "earnings-digest" -> nav.open(org.example.stocksteps.presentation.earnings.EarningsDigestRoute)
                else -> nav.open(org.example.stocksteps.presentation.watchlist.AlertsRoute(link))
            }
        }
    }
    val isCompanyDetails = destination?.hasRoute<CompanyDetailsRoute>() == true
    val isCompanyFinancials = destination?.hasRoute<CompanyFinancialsRoute>() == true
    val isCompanyValuation = destination?.hasRoute<CompanyValuationRoute>() == true
    val isCompanyNews = destination?.hasRoute<CompanyNewsRoute>() == true
    val isNewsInsight = destination?.hasRoute<NewsInsightRoute>() == true
    val isMovement = destination?.hasRoute<StockMovementRoute>() == true
    val hasBack = isSearch || isCompanyFinancials || isCompanyNews || isSettings || destination?.hasRoute<PortfolioEntryRoute>() == true || destination?.hasRoute<HoldingDetailsRoute>() == true || destination?.hasRoute<PortfolioInsightsRoute>() == true || destination?.hasRoute<PortfolioTransactionsRoute>() == true || destination?.hasRoute<PortfolioTransactionRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.screener.ScreenerRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.screener.ComparisonRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.screener.ComparisonResearchRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.screener.ComparisonAiRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsCalendarRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDetailsRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsEventRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsResultsRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsRemindersRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.earnings.SettingsRemindersRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDigestRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDigestSettingsRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.research.GuidedResearchRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.learn.LearnTopicsRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.learn.LearnTermRoute>() == true ||
        destination?.hasRoute<org.example.stocksteps.presentation.brief.DailyBriefRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.practice.PracticeOrderRoute>() == true
    // Practice needs an account: the server keeps the simulated ledger.
    // 5D.5.6C4: Practice is the Portfolio landing's second nested tab (guests see its existing sign-in prompt there).
    val openPractice: () -> Unit = { nav.open(org.example.stocksteps.presentation.practice.PracticeRoute) }
    val practiceTrade: (String, String) -> Unit = { symbol, side -> if (accounts?.auth?.session?.value?.user == null) nav.open(AuthRoute()) else nav.open(org.example.stocksteps.presentation.practice.PracticeOrderRoute(symbol, side)) }
    // Back to the Practice entry already on the stack (keeps its entry, so a cross-tab hop to it stays valid), else open Practice.
    // After an order: back to the Practice section, refreshed (the order changed the simulated holdings).
    val backToPractice: () -> Unit = { nav.open(org.example.stocksteps.presentation.practice.PracticeRoute); accounts?.practice?.refresh() }
    val openResearch: (String, String?) -> Unit = { symbol, name -> nav.open(org.example.stocksteps.presentation.research.GuidedResearchRoute(symbol, name)) }
    // Every stock tap (Home movers, Watchlist, Search) opens the same Company Details page.
    val addPortfolio: (InstrumentRef) -> Unit = { instrument ->
        nav.open(PortfolioEntryRoute(instrument.symbol, instrument.name, instrument.exchange, instrument.currency))
    }
    val openStock: (String) -> Unit = { symbol -> nav.open(CompanyDetailsRoute(symbol)) }
    // 5D.5.6B landing bars: Notifications is the all-alerts history (never a stock filter) and Settings the existing screen, both pushed on the
    // current stack as before, so Back returns to the exact landing page; the global-modal presentation (plan §6) arrives with Phase C.
    val openNotifications: () -> Unit = { nav.open(org.example.stocksteps.presentation.watchlist.NotificationsRoute) }
    val openSettings: () -> Unit = { nav.open(SettingsRoute) }
    // A stock's alerts are Watchlist's; all alerts are Notifications (global, 5D.5.6C1).
    val openAlerts: (String?) -> Unit = { symbol -> if (symbol == null) openNotifications() else nav.open(AlertsRoute(symbol)) }
    // Scaffold applies status/navigation-bar insets once and consumes them for the content,
    // so screens' own safe-content padding does not double them.
    Scaffold(
        containerColor = StockStepsTheme.colors.appBackground,
        topBar = {
            StockStepsTopBar(
                configuration = AppBarConfiguration(
                    title = when {
                        destination?.hasRoute<PortfolioEntryRoute>() == true -> PortfolioDisplay.FORM_ADD_TITLE
                        destination?.hasRoute<HoldingDetailsRoute>() == true -> PortfolioDisplay.HOLDING_DETAILS_TITLE
                        destination?.hasRoute<PortfolioInsightsRoute>() == true -> PortfolioDisplay.INSIGHTS_TITLE
                        destination?.hasRoute<PortfolioTransactionsRoute>() == true -> PortfolioTransactionsDisplay.TITLE
                        destination?.hasRoute<PortfolioTransactionRoute>() == true -> PortfolioTransactionDetailsDisplay.TITLE
                        destination?.hasRoute<org.example.stocksteps.presentation.screener.ScreenerRoute>() == true -> "Discover"
                        destination?.hasRoute<org.example.stocksteps.presentation.screener.ComparisonRoute>() == true -> "Compare"
                        destination?.hasRoute<org.example.stocksteps.presentation.screener.ComparisonResearchRoute>() == true -> "Research"
                        destination?.hasRoute<org.example.stocksteps.presentation.screener.ComparisonAiRoute>() == true -> "Ask AI"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsCalendarRoute>() == true -> "Earnings"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsEventRoute>() == true -> "Earnings event"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsResultsRoute>() == true -> "Earnings results"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsRemindersRoute>() == true ||
                            destination?.hasRoute<org.example.stocksteps.presentation.earnings.SettingsRemindersRoute>() == true -> "Earnings reminders"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDigestRoute>() == true -> "Earnings digest"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDigestSettingsRoute>() == true -> "Earnings digest & AI"
                        destination?.hasRoute<org.example.stocksteps.presentation.earnings.EarningsDetailsRoute>() == true -> "Earnings details"
                        destination?.hasRoute<org.example.stocksteps.presentation.research.GuidedResearchRoute>() == true -> "Research"
                        destination?.hasRoute<org.example.stocksteps.presentation.brief.DailyBriefRoute>() == true -> org.example.stocksteps.brief.DailyBriefDisplay.TITLE
                        destination?.hasRoute<org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute>() == true -> "Previous briefs"
                        destination?.hasRoute<org.example.stocksteps.presentation.practice.PracticeOrderRoute>() == true -> "Practice order"
                        destination?.hasRoute<PortfolioRoute>() == true -> "Portfolio"
                        isSearch -> "Search stocks"
                        isCompanyFinancials -> "Financials"
                        isCompanyNews -> "Company news"
                        destination?.hasRoute<WatchListRoute>() == true -> "Watchlist"
                        destination?.hasRoute<LearnRoute>() == true || destination?.hasRoute<org.example.stocksteps.presentation.learn.LearnTopicsRoute>() == true ||
                            destination?.hasRoute<org.example.stocksteps.presentation.learn.LearnTermRoute>() == true -> "Learn"
                        destination?.hasRoute<SettingsRoute>() == true -> "Settings"
                        else -> "Home"
                    },
                    // Home, Settings, Company Details and Financials render their own headers.
                    visible = !isAuth && !isHome && destination?.hasRoute<LearnRoute>() != true && destination?.hasRoute<PortfolioRoute>() != true && !isMarkets && !isWatchlist && !isAlerts && !isCompanyDetails && !isCompanyFinancials && !isCompanyValuation && !isNewsInsight && !isMovement,
                    backButton = if (hasBack) AppBarBackButton.BACK else AppBarBackButton.NONE
                ),
                onBack = { nav.back() },
                backIcon = backIcon
            )
        },
        bottomBar = {
            // 5D.5.6C1: tabs show on every tab screen and never on modal or global ones (NavigationPolicy); the selected tab is the visible
            // screen's canonical tab — the graph it lives in — whatever it was opened from.
            val showTabs = destination?.let(::screenIdOf)?.let(org.example.stocksteps.navigation.NavigationPolicy::showsBottomNavigation) == true
            val currentTab = destination?.tab()
            Column {
                if (sampleData) {
                    StockSampleDataBanner(
                        text = stringResource(Res.string.sample_data_banner),
                        modifier = if (showTabs) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    )
                }
                if (showTabs) {
                    StockBottomNavigation(labels = MainDestination.entries.map { stringResource(it.labelResource()) }) {
                        MainDestination.entries.forEach { tab ->
                            val selected = currentTab?.name == tab.name
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
      androidx.compose.runtime.CompositionLocalProvider(org.example.stocksteps.presentation.earnings.LocalNotificationAccess provides notifications,
          LocalTabReselect provides nav.reselects) {
        // Phase 8E: one motion policy for every destination (see NavMotionTransitions) instead of the library's 700 ms cross-fade.
        val slidePx = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.unit.Dp(org.example.stocksteps.navigation.NavigationMotion.SLIDE_DP.toFloat()).roundToPx() }
        val motion = remember(nav, slidePx) { NavMotionTransitions(nav, slidePx) }
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = motion.enter,
            exitTransition = motion.exit,
            popEnterTransition = motion.enter,
            popExitTransition = motion.exit
        ) {
            // 5D.5.6C1: every route is registered once, in the graph of its canonical tab (core NavigationPolicy via AppRouteScreens), or at the
            // top level when it is modal or global — graph membership is the policy, never a second table.
            val routes = RouteTable().apply {
                route<DiscoveryRoute> {
                    val homeLocked = appLock?.state?.collectAsStateWithLifecycle()?.value == org.example.stocksteps.security.AppLockState.LOCKED
                    if (accounts != null && !homeLocked) HomeScene(
                        backend = backend,
                        environment = environment,
                        accounts = accounts,
                        hinge = hinge,
                        onLearn = { openTab(MainDestination.LEARN) },
                        onSearch = { nav.open(StockSearchRoute()) },
                        onWatchlist = { openTab(MainDestination.WATCHLIST) },
                        onPortfolio = { openTab(MainDestination.PORTFOLIO) },
                        onSettings = openSettings,
                        onAlerts = openAlerts,
                        onExplore = openStock,
                        onEarnings = { symbol -> nav.open(org.example.stocksteps.presentation.earnings.EarningsDetailsRoute(symbol)) },
                        onResearch = openResearch,
                        onPractice = openPractice,
                        onDailyBrief = { nav.open(org.example.stocksteps.presentation.brief.DailyBriefRoute()) }
                    )
                }
                // 5D.5.6C4: the Portfolio landing hosts two nested tabs (My Portfolio | Practice). The selected one comes from the navigator;
                // each keeps its own saved state (scroll, filters) while the other is shown.
                route<PortfolioRoute> {
                    val section = nav.portfolioSection
                    val sections = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                    if (accounts != null) sections.SaveableStateProvider(section.name) {
                        when (section) {
                            org.example.stocksteps.navigation.PortfolioSection.MY_PORTFOLIO -> PortfolioScene(accounts,
                                onAdd = { nav.open(PortfolioEntryRoute()) },
                                onHolding = { account, symbol -> nav.open(HoldingDetailsRoute(account, symbol)) },
                                onOpenCompany = openStock, hinge = hinge,
                                onSignIn = { nav.open(AuthRoute()) }, onAlerts = { nav.open(AlertsRoute(it)) },
                                onInsights = { nav.open(PortfolioInsightsRoute) },
                                section = section, onSection = nav::selectPortfolioSection,
                                onTransactions = { account, symbol -> nav.open(PortfolioTransactionsRoute(account, symbol)) },
                                onOpenTransaction = { nav.open(PortfolioTransactionRoute(it)) },
                                onNotifications = openNotifications, onSettings = openSettings)
                            org.example.stocksteps.navigation.PortfolioSection.PRACTICE -> org.example.stocksteps.presentation.practice.PracticeScene(accounts, hinge,
                                onBuy = { nav.open(org.example.stocksteps.presentation.practice.PracticeSearchRoute) },
                                onExplore = { nav.open(org.example.stocksteps.presentation.screener.ScreenerRoute()) },
                                onLearn = { openTab(MainDestination.LEARN) },
                                onSignIn = { nav.open(AuthRoute()) },
                                onCompany = openStock,
                                onTrade = { symbol, side -> practiceTrade(symbol, side.name) },
                                onManagePlan = { nav.open(SettingsRoute) },
                                header = { portfolioLandingHeader(section, nav::selectPortfolioSection, openNotifications, openSettings) })
                        }
                    }
                }
                route<org.example.stocksteps.presentation.screener.ScreenerRoute> { entry ->
                    org.example.stocksteps.presentation.screener.ScreenerScene(
                        route = entry.toRoute(), backend = backend, environment = environment, accounts = accounts, hinge = hinge,
                        onOpenStock = openStock,
                        onCompare = { nav.open(org.example.stocksteps.presentation.screener.ComparisonRoute) },
                        onAddPortfolio = addPortfolio,
                        onSignIn = { nav.open(AuthRoute()) }
                    )
                }
                route<org.example.stocksteps.presentation.screener.ComparisonRoute> {
                    org.example.stocksteps.presentation.screener.ComparisonScene(backend, environment, hinge, onOpenStock = openStock,
                        onDiscover = { nav.open(org.example.stocksteps.presentation.screener.ScreenerRoute()) },
                        accounts = accounts, onUpgrade = { nav.open(SettingsRoute) }, onSignIn = { nav.open(AuthRoute()) },
                        onResearch = { nav.open(org.example.stocksteps.presentation.screener.ComparisonResearchRoute) },
                        onAskAi = { nav.open(org.example.stocksteps.presentation.screener.ComparisonAiRoute()) })
                }
                route<org.example.stocksteps.presentation.screener.ComparisonResearchRoute> { entry ->
                    // Reuses the Compare destination's ViewModel (and its already-loaded data) when it's on the back stack.
                    val compareEntry = remember(entry) { runCatching { navController.getBackStackEntry<org.example.stocksteps.presentation.screener.ComparisonRoute>() }.getOrNull() }
                    org.example.stocksteps.presentation.screener.ComparisonResearchScene(backend, environment, accounts, compareEntry, hinge,
                        onSignIn = { nav.open(AuthRoute()) }, onUpgrade = { nav.open(SettingsRoute) },
                        onAskAi = { q, session, hasNote -> nav.open(org.example.stocksteps.presentation.screener.ComparisonAiRoute(q, session, hasNote)) })
                }
                route<org.example.stocksteps.presentation.screener.ComparisonAiRoute> { entry ->
                    // Same Compare ViewModel as the research checklist: no second comparison request, selection kept.
                    val compareEntry = remember(entry) { runCatching { navController.getBackStackEntry<org.example.stocksteps.presentation.screener.ComparisonRoute>() }.getOrNull() }
                    org.example.stocksteps.presentation.screener.ComparisonAiScene(entry.toRoute(), backend, environment, accounts, compareEntry, hinge,
                        onBack = { if (compareEntry != null) navController.popBackStack(org.example.stocksteps.presentation.screener.ComparisonRoute, inclusive = false) else nav.open(org.example.stocksteps.presentation.screener.ComparisonRoute) },
                        onSignIn = { nav.open(AuthRoute()) }, onUpgrade = { nav.open(SettingsRoute) })
                }
                route<PortfolioInsightsRoute> {
                    if (accounts != null) PortfolioInsightsScene(accounts, onOpenCompany = openStock, onSignIn = { nav.open(AuthRoute()) }, hinge = hinge)
                }
                route<HoldingDetailsRoute> { entry ->
                    if (accounts != null) PortfolioScene(accounts,
                        onAdd = { nav.open(PortfolioEntryRoute()) },
                        onHolding = { _, _ -> }, onOpenCompany = openStock, holding = entry.toRoute(), hinge = hinge,
                        onSignIn = { nav.open(AuthRoute()) }, onAlerts = { nav.open(AlertsRoute(it)) },
                        onTransactions = { account, symbol -> nav.open(PortfolioTransactionsRoute(account, symbol)) },
                        onOpenTransaction = { nav.open(PortfolioTransactionRoute(it)) })
                }
                // Phase D2: Transaction history inside Portfolio (not a tab); shares the dashboard's presenter.
                route<PortfolioTransactionsRoute> { entry ->
                    if (accounts != null) PortfolioTransactionsScene(accounts, entry.toRoute(), onAdd = { nav.open(PortfolioEntryRoute()) },
                        onSignIn = { nav.open(AuthRoute()) }, hinge = hinge,
                        onOpenTransaction = { nav.open(PortfolioTransactionRoute(it)) })
                }
                // Phase D3: Transaction details (identity only; same presenter as the dashboard).
                route<PortfolioTransactionRoute> { entry ->
                    if (accounts != null) PortfolioTransactionScene(accounts, entry.toRoute(), onBack = { nav.back() },
                        onSignIn = { nav.open(AuthRoute()) }, hinge = hinge)
                }
                route<org.example.stocksteps.presentation.practice.PracticeSearchRoute> {
                    StockSearchScene(
                        route = StockSearchRoute(),
                        backend = backend,
                        environment = environment,
                        hinge = hinge,
                        accounts = accounts,
                        recentSearches = recentSearches,
                        onOpenStock = { stock ->
                            nav.open(org.example.stocksteps.presentation.practice.PracticeOrderRoute(stock.symbol, "BUY")) // closes the picker first (modal rule)
                        }
                    )
                }
                route<PortfolioSearchRoute> {
                    StockSearchScene(
                        route = StockSearchRoute(),
                        backend = backend,
                        environment = environment,
                        hinge = hinge,
                        accounts = accounts,
                        recentSearches = recentSearches,
                        onOpenStock = { stock ->
                            val selected = PortfolioEntryRoute(stock.symbol, stock.name, stock.exchange, stock.currency)
                            navController.previousBackStackEntry?.savedStateHandle?.set(
                                "portfolio.selection", arrayOf(selected.symbol, selected.name.orEmpty(), selected.exchange.orEmpty(), selected.currency.orEmpty())
                            )
                            nav.back()
                        }
                    )
                }
                route<PortfolioEntryRoute> { entry ->
                    val selection by entry.savedStateHandle.getStateFlow("portfolio.selection", emptyArray<String>()).collectAsStateWithLifecycle()
                    val selected = remember(selection) {
                        selection.takeIf { it.size == 4 }?.let { PortfolioEntryRoute(it[0], it[1], it[2], it[3]) }
                    }
                    if (accounts != null) PortfolioEntryScene(
                        accounts,
                        selected ?: entry.toRoute(),
                        onSearch = { nav.open(PortfolioSearchRoute) },
                        onDone = {
                            // D3: back to a Portfolio screen → it announces "Transaction added"; elsewhere (e.g. Company details) nothing does.
                            val back = navController.previousBackStackEntry?.destination
                            val portfolio = back?.hasRoute<PortfolioRoute>() == true || back?.hasRoute<PortfolioTransactionsRoute>() == true ||
                                back?.hasRoute<HoldingDetailsRoute>() == true || back?.hasRoute<PortfolioTransactionRoute>() == true
                            if (!portfolio) accounts.portfolioFeedback.skip(accounts.portfolioPresenter.state.value.actionResult)
                            nav.back()
                        }
                    )
                }
                route<MarketsRoute> {
                    MarketsScene(
                        backend = backend,
                        environment = environment,
                        hinge = hinge,
                        onOpenStock = openStock,
                        onWhyMoved = { symbol -> nav.open(StockMovementRoute(symbol)) },
                        onSearch = { nav.open(StockSearchRoute()) },
                        onDiscover = { nav.open(org.example.stocksteps.presentation.screener.ScreenerRoute()) },
                        onCompare = { nav.open(org.example.stocksteps.presentation.screener.ComparisonRoute) },
                        onEarnings = { nav.open(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute()) },
                        accounts = accounts,
                        brief = accounts?.dailyBrief,
                        onDailyBrief = { nav.open(org.example.stocksteps.presentation.brief.DailyBriefRoute()) },
                        onBriefHistory = { nav.open(org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute) },
                        onNotifications = openNotifications, onSettings = openSettings
                    )
                }
                route<org.example.stocksteps.presentation.brief.DailyBriefRoute> { entry ->
                    if (accounts != null) org.example.stocksteps.presentation.brief.DailyBriefScene(entry.toRoute(), accounts.dailyBrief, hinge,
                        onOpenStock = openStock,
                        onHistory = { nav.open(org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute) },
                        onOpenBrief = { id -> nav.open(org.example.stocksteps.presentation.brief.DailyBriefRoute(id)) },
                        onWatchlist = { openTab(MainDestination.WATCHLIST) },
                        onLearn = { openTab(MainDestination.LEARN) },
                        onSignIn = { nav.open(AuthRoute()) },
                        onUpgrade = { nav.open(SettingsRoute) },
                        onOpenEarnings = { id -> nav.open(org.example.stocksteps.presentation.earnings.EarningsEventRoute(id)) },
                        onOpenResults = { id -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(id)?.let { nav.open(it) } },
                        onOpenDigest = { nav.open(org.example.stocksteps.presentation.earnings.EarningsDigestRoute) })
                }
                route<org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute> {
                    if (accounts != null) org.example.stocksteps.presentation.brief.DailyBriefHistoryScene(accounts.dailyBrief, hinge,
                        onOpenBrief = { id -> nav.open(org.example.stocksteps.presentation.brief.DailyBriefRoute(id)) },
                        onUpgrade = { nav.open(SettingsRoute) })
                }
                route<org.example.stocksteps.presentation.earnings.EarningsCalendarRoute> { entry ->
                    org.example.stocksteps.presentation.earnings.EarningsCalendarScene(entry.toRoute(), backend, environment, accounts, hinge,
                        onOpenEvent = { nav.open(org.example.stocksteps.presentation.earnings.EarningsEventRoute(it)) },
                        onSignIn = { nav.open(AuthRoute()) },
                        onDigest = { nav.open(org.example.stocksteps.presentation.earnings.EarningsDigestRoute) },
                        onOpenResults = { id -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(id)?.let { nav.open(it) } })
                }
                route<org.example.stocksteps.presentation.earnings.EarningsEventRoute> { entry ->
                    org.example.stocksteps.presentation.earnings.EarningsEventScene(entry.toRoute(), backend, environment, accounts, hinge,
                        onOpenCompany = openStock,
                        onOpenCalendar = { date -> nav.open(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute(date)) },
                        onOpenResults = { id -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(id)?.let { nav.open(it) } },
                        onSignIn = { nav.open(AuthRoute()) })
                }
                route<org.example.stocksteps.presentation.earnings.EarningsResultsRoute> { entry ->
                    org.example.stocksteps.presentation.earnings.EarningsResultsScene(entry.toRoute(), backend, environment, accounts, hinge,
                        onOpenCompany = openStock, onLearn = { openTab(MainDestination.LEARN) },
                        // No purchase flow exists yet: Settings shows the plan (and, in MOCK, the simulated plan switch).
                        onUpgrade = { nav.open(SettingsRoute) }, onSignIn = { nav.open(AuthRoute()) })
                }
                route<org.example.stocksteps.presentation.earnings.EarningsDetailsRoute> { entry ->
                    org.example.stocksteps.presentation.earnings.EarningsDetailsScene(entry.toRoute(), backend, environment, accounts, hinge,
                        onOpenCompany = openStock, onSignIn = { nav.open(AuthRoute()) },
                        // No purchase flow exists yet: Settings shows the plan (and, in MOCK, the simulated plan switch).
                        onUpgrade = { nav.open(SettingsRoute) })
                }
                route<WatchListRoute> {
                    if (accounts != null) WatchListScene(
                        accounts = accounts,
                        hinge = hinge,
                        notifications = notifications,
                        onSignIn = { nav.open(AuthRoute()) },
                        onSearch = { nav.open(StockSearchRoute()) },
                        onOpenStock = openStock,
                        onAddPortfolio = addPortfolio,
                        onOpenAlerts = openAlerts,
                        onEarnings = { nav.open(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute(filter = "WATCHLIST")) },
                        onEarningsReminders = { nav.open(org.example.stocksteps.presentation.earnings.EarningsRemindersRoute) },
                        onCompare = { nav.open(org.example.stocksteps.presentation.screener.ComparisonRoute) },
                        onNotifications = openNotifications, onSettings = openSettings
                    )
                }
                // 5D.5.6C1: all alerts and their history (the landing bars' bell), global and outside the tabs.
                route<org.example.stocksteps.presentation.watchlist.NotificationsRoute> {
                    if (accounts != null) org.example.stocksteps.presentation.watchlist.AlertsScene(
                        route = org.example.stocksteps.presentation.watchlist.AlertsRoute(null),
                        accounts = accounts,
                        hinge = hinge,
                        notifications = notifications,
                        backIcon = backIcon,
                        onBack = { nav.back() },
                        onOpenStock = openStock,
                        onSignIn = { nav.open(AuthRoute()) }
                    )
                }
                route<org.example.stocksteps.presentation.watchlist.AlertsRoute> { entry ->
                    if (accounts != null) org.example.stocksteps.presentation.watchlist.AlertsScene(
                        route = entry.toRoute<org.example.stocksteps.presentation.watchlist.AlertsRoute>(),
                        accounts = accounts,
                        hinge = hinge,
                        notifications = notifications,
                        backIcon = backIcon,
                        onBack = { nav.back() },
                        onOpenStock = openStock,
                        onSignIn = { nav.open(AuthRoute()) }
                    )
                }
                route<LearnRoute> {
                    LearnScene(hinge, accounts, onResearch = openResearch, onSearch = { nav.open(StockSearchRoute()) }, onPractice = openPractice,
                        onTopics = { topic, search -> nav.open(org.example.stocksteps.presentation.learn.LearnTopicsRoute(topic, search)) },
                        onTerm = { nav.open(org.example.stocksteps.presentation.learn.LearnTermRoute(it)) },
                        onNotifications = openNotifications, onSettings = openSettings)
                }
                route<org.example.stocksteps.presentation.learn.LearnTopicsRoute> { entry ->
                    org.example.stocksteps.presentation.learn.LearnTopicsScene(entry.toRoute(), hinge,
                        onTopic = { nav.open(org.example.stocksteps.presentation.learn.LearnTopicsRoute(it)) },
                        onTerm = { nav.open(org.example.stocksteps.presentation.learn.LearnTermRoute(it)) })
                }
                route<org.example.stocksteps.presentation.learn.LearnTermRoute> { entry ->
                    org.example.stocksteps.presentation.learn.LearnTermScene(entry.toRoute(), hinge,
                        onTerm = { nav.open(org.example.stocksteps.presentation.learn.LearnTermRoute(it)) },
                        onTopics = { nav.open(org.example.stocksteps.presentation.learn.LearnTopicsRoute()) },
                        onSearch = { nav.open(StockSearchRoute()) })
                }
                route<org.example.stocksteps.presentation.practice.PracticeOrderRoute> { entry ->
                    if (accounts != null) org.example.stocksteps.presentation.practice.PracticeOrderScene(entry.toRoute(), environment, accounts, hinge,
                        onViewHoldings = backToPractice,
                        onExplore = { nav.open(org.example.stocksteps.presentation.screener.ScreenerRoute()) },
                        onLearn = { openTab(MainDestination.LEARN) },
                        onUpgrade = backToPractice,
                        onBack = { nav.back() })
                }
                route<org.example.stocksteps.presentation.research.GuidedResearchRoute> { entry ->
                    org.example.stocksteps.presentation.research.GuidedResearchScene(entry.toRoute(), backend, environment, accounts, hinge,
                        onCompany = openStock,
                        onSearch = { nav.open(StockSearchRoute()) },
                        onLearn = { openTab(MainDestination.LEARN) },
                        onUpgrade = { nav.open(SettingsRoute) })
                }
                route<SettingsRoute> {
                    if (accounts != null) SettingsScene(
                        accounts = accounts,
                        themePreferences = themePreferences,
                        backend = backend,
                        appVersion = appVersion,
                        hinge = hinge,
                        onSignIn = { nav.open(AuthRoute()) },
                        links = mapOf(SettingsLink.EARNINGS_REMINDERS to { nav.open(org.example.stocksteps.presentation.earnings.SettingsRemindersRoute) },
                            SettingsLink.EARNINGS_DIGEST to { nav.open(org.example.stocksteps.presentation.earnings.EarningsDigestSettingsRoute) }),
                        appLock = appLock
                    )
                }
                route<org.example.stocksteps.presentation.earnings.EarningsDigestRoute> {
                    org.example.stocksteps.presentation.earnings.EarningsDigestScene(accounts, environment, hinge,
                        onOpenResults = { id -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(id)?.let { nav.open(it) } },
                        onOpenEvent = { nav.open(org.example.stocksteps.presentation.earnings.EarningsEventRoute(it)) },
                        onSettings = { nav.open(org.example.stocksteps.presentation.earnings.EarningsDigestSettingsRoute) },
                        onUpgrade = { nav.open(SettingsRoute) },
                        onSignIn = { nav.open(AuthRoute()) },
                        onRecentWatchlist = { nav.open(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute(filter = "WATCHLIST", tab = "REPORTED")) })
                }
                route<org.example.stocksteps.presentation.earnings.EarningsDigestSettingsRoute> {
                    org.example.stocksteps.presentation.earnings.EarningsDigestSettingsScene(accounts, environment, hinge,
                        onUpgrade = { nav.open(SettingsRoute) },
                        onSignIn = { nav.open(AuthRoute()) },
                        onReminders = { nav.open(org.example.stocksteps.presentation.earnings.SettingsRemindersRoute) })
                }
                route<org.example.stocksteps.presentation.earnings.EarningsRemindersRoute> {
                    org.example.stocksteps.presentation.earnings.EarningsRemindersScene(accounts, onSignIn = { nav.open(AuthRoute()) },
                        onOpenEvent = { nav.open(org.example.stocksteps.presentation.earnings.EarningsEventRoute(it)) })
                }
                // 5D.5.6C1: the same reminders inside the Settings flow (global): opening an event closes Settings and shows it in Markets.
                route<org.example.stocksteps.presentation.earnings.SettingsRemindersRoute> {
                    org.example.stocksteps.presentation.earnings.EarningsRemindersScene(accounts, onSignIn = { nav.open(AuthRoute()) },
                        onOpenEvent = { nav.open(org.example.stocksteps.presentation.earnings.EarningsEventRoute(it)) })
                }
                route<AuthRoute> { entry ->
                    if (accounts != null) AuthScene(accounts, entry.toRoute<AuthRoute>(), hinge) {
                        if (navController.previousBackStackEntry != null) {
                            nav.back()
                        } else {
                            navController.navigate(HomeGraph) {
                                popUpTo<AuthRoute> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                route<StockSearchRoute> { entry ->
                    StockSearchScene(
                        route = entry.toRoute<StockSearchRoute>(),
                        backend = backend,
                        environment = environment,
                        hinge = hinge,
                        accounts = accounts,
                        recentSearches = recentSearches,
                        onOpenStock = { stock -> openStock(stock.symbol) }
                    )
                }
                route<CompanyDetailsRoute> { entry ->
                    CompanyDetailsScene(
                        route = entry.toRoute<CompanyDetailsRoute>(),
                        backend = backend,
                        environment = environment,
                        accounts = accounts,
                        hinge = hinge,
                        backIcon = backIcon,
                        onBack = { nav.back() },
                        onLearnTerm = { nav.open(org.example.stocksteps.presentation.learn.LearnTermRoute(it)) },
                        onOpenFinancials = { symbol -> nav.open(CompanyFinancialsRoute(symbol)) },
                        onOpenValuation = { symbol -> nav.open(CompanyValuationRoute(symbol)) },
                        onOpenNews = { symbol -> nav.open(CompanyNewsRoute(symbol)) },
                        onAddPortfolio = addPortfolio,
                        onOpenMovement = { symbol -> nav.open(StockMovementRoute(symbol)) },
                        onCompare = { symbol, name ->
                            org.example.stocksteps.screener.SharedComparisonSelection.instance.add(symbol, name)
                            nav.open(org.example.stocksteps.presentation.screener.ComparisonRoute)
                        },
                        onEarnings = { symbol -> nav.open(org.example.stocksteps.presentation.earnings.EarningsDetailsRoute(symbol)) },
                        onEarningsCalendar = { date -> nav.open(org.example.stocksteps.presentation.earnings.EarningsCalendarRoute(date)) },
                        onEarningsResults = { id -> org.example.stocksteps.presentation.earnings.EarningsResultsRoute.of(id)?.let { nav.open(it) } },
                        onSignIn = { nav.open(AuthRoute()) },
                        onResearch = openResearch,
                        onPracticeBuy = { symbol -> practiceTrade(symbol, "BUY") }
                    )
                }
                route<CompanyFinancialsRoute> { entry ->
                    CompanyFinancialsScene(entry.toRoute<CompanyFinancialsRoute>(), backend, environment, hinge, backIcon, onBack = { nav.back() },
                        onLearnTerm = { nav.open(org.example.stocksteps.presentation.learn.LearnTermRoute(it)) })
                }
                route<CompanyValuationRoute> { entry ->
                    CompanyValuationScene(entry.toRoute<CompanyValuationRoute>(), backend, environment, hinge, backIcon, onBack = { nav.back() },
                        onLearnTerm = { nav.open(org.example.stocksteps.presentation.learn.LearnTermRoute(it)) })
                }
                route<CompanyNewsRoute> { entry ->
                    CompanyNewsScene(entry.toRoute<CompanyNewsRoute>(), backend, environment, hinge,
                        onOpenInsight = { symbol, articleId -> nav.open(NewsInsightRoute(symbol, articleId)) },
                        onOpenMovement = { symbol -> nav.open(StockMovementRoute(symbol)) })
                }
                route<NewsInsightRoute> { entry ->
                    NewsInsightScene(entry.toRoute<NewsInsightRoute>(), backend, environment, hinge, backIcon, onBack = { nav.back() })
                }
                route<StockMovementRoute> { entry ->
                    StockMovementScene(entry.toRoute<StockMovementRoute>(), backend, environment, hinge, backIcon, onBack = { nav.back() })
                }
            }
            org.example.stocksteps.navigation.AppTab.entries.forEach { tab ->
                navigation(startDestination = tab.rootRoute(), route = tab.graphClass) {
                    routes.items.filter { (type, _) -> routeTab(type) == tab }.forEach { (_, register) -> register() }
                }
            }
            routes.items.filter { (type, _) -> routeTab(type) == null }.forEach { (_, register) -> register() }
        }
        // One Back for the whole app, registered after the NavHost so it decides first: modal close, in-stack pop, cross-tab return (hop end) and
        // non-Home root → Home. Disabled only at the Home root without a trail, where the system closes the app.
        org.example.stocksteps.presentation.components.AppBackHandler(enabled = backStackEntry != null && nav.canGoBack()) { nav.back() }
      }
    }
}

/** Collects each route's registration so `AppNavigation` can place it in its canonical tab's graph. */
internal class RouteTable {
    val items = mutableListOf<Pair<kotlin.reflect.KClass<*>, androidx.navigation.NavGraphBuilder.() -> Unit>>()
    inline fun <reified T : Any> route(noinline content: @Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit) {
        items += T::class to { composable<T>(content = content) }
    }
}

/** The canonical tab of a registered route type (null for modal and global routes). */
private fun routeTab(type: kotlin.reflect.KClass<*>): org.example.stocksteps.navigation.AppTab? =
    (AppRouteScreens.firstOrNull { it.first == type } ?: error("Route $type is not in AppRouteScreens")).second.tab

private fun MainDestination.labelResource(): StringResource = when (this) {
    MainDestination.HOME -> Res.string.nav_home
    MainDestination.PORTFOLIO -> Res.string.nav_portfolio
    MainDestination.MARKETS -> Res.string.nav_markets
    MainDestination.WATCHLIST -> Res.string.nav_watchlist
    MainDestination.LEARN -> Res.string.nav_learn
}
