package org.example.stocksteps.presentation.companydetails

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.companydetail.CompanyOverview
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.presentation.stocksearch.StockWatchlistState
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter

@Composable
internal fun CompanyDetailsScene(
    route: CompanyDetailsRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    accounts: AccountDependencies?,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    onBack: () -> Unit,
    onOpenFinancials: (String) -> Unit,
    onOpenValuation: (String) -> Unit,
    onOpenNews: (String) -> Unit,
    onAddPortfolio: (org.example.stocksteps.model.InstrumentRef) -> Unit,
    onOpenMovement: (String) -> Unit,
    onCompare: (symbol: String, name: String) -> Unit = { _, _ -> },
    onEarnings: (String) -> Unit = {},
    onEarningsCalendar: (String?) -> Unit = {},
    onEarningsResults: (String) -> Unit = {},
    onSignIn: () -> Unit = {},
    onResearch: (symbol: String, name: String) -> Unit = { _, _ -> },
    onPracticeBuy: (String) -> Unit = {}
) {
    val model = viewModel(key = "company-details:${route.symbol}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        CompanyDetailsViewModel(route.symbol, data.getCompanyDetails(), data.getPriceChart(), data.getWhyMoving(), data.companyNews(), data::close)
    }
    val state by model.state.collectAsStateWithLifecycle()
    val earningsModel = viewModel(key = "company-earnings:${route.symbol}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        org.example.stocksteps.presentation.earnings.CompanyEarningsViewModel(route.symbol, data.earningsRemote(accounts), data::close)
    }
    val earnings by earningsModel.presenter.state.collectAsStateWithLifecycle()
    val reminders by (accounts?.earningsReminders?.state ?: remember { MutableStateFlow(org.example.stocksteps.earnings.EarningsRemindersState()) }).collectAsStateWithLifecycle()
    var reminderSheet by remember { mutableStateOf<org.example.stocksteps.presentation.earnings.ReminderTarget?>(null) }
    org.example.stocksteps.presentation.earnings.ReminderSheetHost(accounts, reminderSheet, onDismiss = { reminderSheet = null }, onSignIn = onSignIn)
    LaunchedEffect(route.symbol, environment, accounts) {
        accounts?.home?.recordViewed(org.example.stocksteps.model.InstrumentRef(route.symbol))
    }
    // The same watchlist used by the Watchlist tab; no Company Details copy of it.
    val watchlistModel = accounts?.let { owner -> viewModel(key = "stock-watchlist") { owner.stockWatchlistViewModel() } }
    val watchlist by (watchlistModel?.state ?: MutableStateFlow(StockWatchlistState())).collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val learning by (accounts?.learning?.state ?: MutableStateFlow(org.example.stocksteps.learning.LearningProgressRepository.State())).collectAsStateWithLifecycle()
    CompanyDetailsScreen(
        state = state,
        watched = route.symbol in watchlist.symbols,
        watchlistEnabled = watchlist.enabled,
        hinge = hinge,
        backIcon = backIcon,
        researchCompleted = learning.journey(route.symbol)?.completedCount,
        earnings = earnings,
        earningsReminder = earnings.eventId?.let { id -> if (reminders.signedIn) reminders.control(id) else org.example.stocksteps.earnings.ReminderControl.OFF },
        onAction = { action ->
            when (action) {
                CompanyDetailsAction.AddPortfolio -> {
                    val listing = state.overview.listing(route.symbol)
                    onAddPortfolio(org.example.stocksteps.model.InstrumentRef(listing.symbol, listing.name, listing.exchange, listing.currency))
                }
                CompanyDetailsAction.Compare -> state.overview.listing(route.symbol).let { onCompare(it.symbol, it.name) }
                CompanyDetailsAction.Earnings -> onEarnings(route.symbol)
                CompanyDetailsAction.EarningsCalendar -> onEarningsCalendar(earnings.date)
                CompanyDetailsAction.RetryEarnings -> earningsModel.presenter.refresh()
                CompanyDetailsAction.LatestResults -> earnings.latestReportId?.let(onEarningsResults)
                CompanyDetailsAction.RemindEarnings -> earnings.eventId?.let { id -> reminderSheet = org.example.stocksteps.presentation.earnings.ReminderTarget(
                    id, route.symbol, (state.overview as? Section.Content)?.value?.name ?: route.symbol, earnings.dateText.orEmpty(), earnings.timingText,
                    earnings.statusText?.substringAfter(" · ", "")?.ifBlank { null }) }
                CompanyDetailsAction.PracticeBuy -> onPracticeBuy(route.symbol)
                CompanyDetailsAction.Research -> state.overview.listing(route.symbol).let { onResearch(it.symbol, it.name) }
                CompanyDetailsAction.Back -> onBack()
                CompanyDetailsAction.ToggleWatchlist -> watchlistModel?.toggle(state.overview.listing(route.symbol))
                CompanyDetailsAction.RetryCore -> model.loadCore()
                CompanyDetailsAction.RetryChart -> model.retryChart()
                CompanyDetailsAction.RetryWhyMoving -> model.loadWhyMoving()
                CompanyDetailsAction.RetryNews -> model.loadNews()
                is CompanyDetailsAction.SelectRange -> model.selectRange(action.range)
                CompanyDetailsAction.OpenFinancials -> onOpenFinancials(route.symbol)
                CompanyDetailsAction.OpenValuation -> onOpenValuation(route.symbol)
                CompanyDetailsAction.OpenNews -> onOpenNews(route.symbol)
                CompanyDetailsAction.OpenMovement -> onOpenMovement(route.symbol)
                is CompanyDetailsAction.OpenArticle -> runCatching { uriHandler.openUri(action.url) }
            }
        }
    )
    watchlist.choosing?.let { stock ->
        org.example.stocksteps.presentation.stocksearch.WatchlistChooserSheet(stock, watchlist.lists,
            onChoose = { watchlistModel?.choose(it) }, onDismiss = { watchlistModel?.cancelChoice() })
    }
}

/** Watchlist entries keep the listing metadata (name, exchange) when it is known. */
private fun Section<CompanyOverview>.listing(symbol: String): StockSearchResult {
    val overview = (this as? Section.Content)?.value
    return StockSearchResult(symbol, overview?.name ?: symbol, exchange = overview?.listing?.substringAfter(" • ", "")?.ifBlank { null })
}
