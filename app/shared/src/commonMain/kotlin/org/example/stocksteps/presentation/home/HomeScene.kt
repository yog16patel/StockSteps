package org.example.stocksteps.presentation.home

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter

@Composable
internal fun HomeScene(
    backend: BackendRouter,
    environment: BackendEnvironment,
    accounts: AccountDependencies,
    hinge: WindowHinge?,
    onLearn: () -> Unit,
    onSearch: () -> Unit,
    onWatchlist: () -> Unit,
    onSettings: () -> Unit,
    onPortfolio: () -> Unit,
    onAlerts: (String?) -> Unit,
    onExplore: (String) -> Unit,
    onEarnings: (String) -> Unit = onExplore,
    onResearch: (symbol: String, name: String) -> Unit = { symbol, _ -> onExplore(symbol) },
    onPractice: () -> Unit = {},
    onDailyBrief: () -> Unit = {}
) {
    val model = viewModel(key = "home:$environment") { HomeViewModel(accounts.home) }
    val state by model.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var hour by remember { mutableStateOf(localHomeHour()) }
    LifecycleResumeEffect(model) {
        hour = localHomeHour()
        model.onVisible()
        onPauseOrDispose { }
    }
    val portfolio by accounts.portfolioPresenter.state.collectAsStateWithLifecycle()
    val learning by accounts.learning.state.collectAsStateWithLifecycle()
    val brief by accounts.dailyBrief.state.collectAsStateWithLifecycle()
    HomeScreen(state, hour, hinge, portfolio = portfolio, onPortfolio = onPortfolio, showMockPersonas = environment == BackendEnvironment.MOCK,
        research = learning.inProgress.firstOrNull(), dailyBrief = brief) { action ->
        when (action) {
            HomeAction.Refresh -> model.refresh()
            HomeAction.RetryQuotes -> model.retryQuotes()
            HomeAction.RetryNews -> model.retryNews()
            HomeAction.RetryWatchlists -> model.retryWatchlists()
            HomeAction.RetryAlerts -> model.retryAlerts()
            HomeAction.ClearRecent -> model.clearRecent()
            is HomeAction.Persona -> model.persona(action.id)
            HomeAction.Learn -> onLearn()
            HomeAction.Practice -> onPractice()
            HomeAction.DailyBrief -> onDailyBrief()
            HomeAction.Search -> onSearch()
            HomeAction.Watchlist -> onWatchlist()
            HomeAction.Settings -> onSettings()
            HomeAction.Alerts -> onAlerts(null)
            is HomeAction.OpenStockAlerts -> onAlerts(action.symbol)
            is HomeAction.OpenStock -> onExplore(action.symbol)
            is HomeAction.OpenEarnings -> onEarnings(action.symbol)
            is HomeAction.ContinueResearch -> onResearch(action.symbol, action.name)
            is HomeAction.OpenArticle -> runCatching { uriHandler.openUri(action.url) }
        }
    }
}
