package org.example.stocksteps.presentation.earnings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.earnings.*
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.stocksearch.StockWatchlistState
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter

internal class EarningsCalendarViewModel(remote: EarningsRemote, accounts: AccountDependencies?, initial: CalendarSelection?, private val close: () -> Unit) : ViewModel() {
    // Signed-in identity and watched symbols come from the existing watchlists repository.
    val presenter = EarningsCalendarPresenter(remote, viewModelScope, accounts?.watchlists?.earningsSession() ?: flowOf(null),
        accounts?.watchlists?.watchedSymbols() ?: flowOf(emptySet()), initial = initial).also { it.start() }
    override fun onCleared() = close()
}

internal class EarningsEventViewModel(eventId: String, remote: EarningsRemote, private val close: () -> Unit) : ViewModel() {
    val presenter = EarningsEventPresenter(eventId, remote, viewModelScope)
    override fun onCleared() = close()
}

internal class EarningsDetailsViewModel(symbol: String, remote: EarningsRemote, accounts: AccountDependencies?, private val close: () -> Unit) : ViewModel() {
    val presenter = EarningsDetailsPresenter(symbol, remote, viewModelScope, accounts?.alerts)
    override fun onCleared() = close()
}

internal class EarningsResultsViewModel(reportId: String, remote: EarningsRemote, cache: EarningsResultsCache?, window: ReactionWindow, private val close: () -> Unit) : ViewModel() {
    val presenter = EarningsResultsPresenter(reportId, remote, viewModelScope, cache)
    /** Phase 3: the price reaction section (server-calculated; the window is restored after process death). */
    val reaction = EarningsPriceReactionPresenter(reportId, remote, viewModelScope, cache, window)
    override fun onCleared() = close()
}

/** Owns one report's presenter (keyed by report and Mock/Real); route arguments restore it after process death. */
@Composable
internal fun EarningsResultsScene(
    route: EarningsResultsRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    accounts: AccountDependencies?,
    hinge: WindowHinge?,
    onOpenCompany: (String) -> Unit,
    onLearn: () -> Unit
) {
    var savedWindow by rememberSaveable { mutableStateOf(ReactionWindow.FIRST_SESSION.name) }
    val model = viewModel(key = "earnings-results:${route.reportId}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        EarningsResultsViewModel(route.reportId, data.earningsRemote(accounts), accounts?.earningsResultsCache, ReactionWindow.valueOf(savedWindow), data::close)
    }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    val reaction by model.reaction.state.collectAsStateWithLifecycle()
    LaunchedEffect(reaction.window) { savedWindow = reaction.window.name }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            EarningsResultsScreen(state, Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize(), reaction) { action ->
                when (action) {
                    ResultsAction.Retry -> model.presenter.refresh()
                    is ResultsAction.Window -> model.reaction.selectWindow(action.window)
                    ResultsAction.RetryReaction -> model.reaction.refresh()
                    ResultsAction.ToggleChart -> model.reaction.toggleChart()
                    is ResultsAction.Toggle -> model.presenter.toggle(action.key)
                    ResultsAction.Company -> onOpenCompany(route.symbol)
                    ResultsAction.Learn -> onLearn()
                }
            }
        }
    }
}

/** Company Details' next earnings date (one cached request per company). */
internal class CompanyEarningsViewModel(symbol: String, remote: EarningsRemote, private val close: () -> Unit) : ViewModel() {
    val presenter = CompanyEarningsPresenter(symbol, remote, viewModelScope)
    override fun onCleared() = close()
}

/** Markets' Earnings Center entry counts. */
internal class EarningsSummaryViewModel(remote: EarningsRemote, accounts: AccountDependencies?, private val close: () -> Unit) : ViewModel() {
    val presenter = EarningsSummaryPresenter(remote, viewModelScope, accounts?.watchlists?.earningsSession() ?: flowOf(null))
    override fun onCleared() = close()
}

private fun filterOf(name: String?) = CalendarFilter.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CalendarFilter.ALL

/**
 * Owns the Earnings Calendar presenter (keyed by route and Mock/Real) and wires navigation. The
 * selection is saved with the back stack entry, so date, view, tab, filter and search survive
 * rotation and process recreation; route arguments only seed a fresh screen.
 */
@Composable
internal fun EarningsCalendarScene(
    route: EarningsCalendarRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    accounts: AccountDependencies?,
    hinge: WindowHinge?,
    onOpenEvent: (String) -> Unit,
    onSignIn: () -> Unit,
    onOpenResults: (String) -> Unit = {}
) {
    var saved by rememberSaveable { mutableStateOf<String?>(null) }
    val model = viewModel(key = "earnings-calendar:$route:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        val initial = CalendarSelection.decode(saved) ?: CalendarSelection(route.selectedDate.orEmpty(), filter = filterOf(route.filter))
        EarningsCalendarViewModel(data.earningsRemote(accounts), accounts, initial, data::close)
    }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.selection) { saved = model.presenter.currentSelection.encode() }
    val reminders by (accounts?.earningsReminders?.state ?: remember { MutableStateFlow(EarningsRemindersState()) }).collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ReminderTarget?>(null) }
    ReminderSheetHost(accounts, sheet, onDismiss = { sheet = null }, onSignIn = onSignIn)
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            EarningsCalendarScreen(state, Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize(),
                reminder = { id -> if (reminders.signedIn) reminders.control(id) else ReminderControl.OFF }) { action ->
                val p = model.presenter
                when (action) {
                    CalendarAction.PreviousWeek -> p.previousWeek()
                    CalendarAction.NextWeek -> p.nextWeek()
                    CalendarAction.Today -> p.goToToday()
                    is CalendarAction.SelectDate -> p.selectDate(action.date)
                    is CalendarAction.Mode -> p.selectMode(action.mode)
                    is CalendarAction.Tab -> p.selectTab(action.tab)
                    is CalendarAction.Filter -> p.selectFilter(action.filter)
                    is CalendarAction.Query -> p.setQuery(action.text)
                    CalendarAction.ClearQuery -> p.clearQuery()
                    is CalendarAction.Open -> onOpenEvent(action.eventId)
                    is CalendarAction.OpenResults -> onOpenResults(action.reportId)
                    is CalendarAction.Remind -> sheet = action.row.let { r -> ReminderTarget(r.id, r.symbol, r.name, r.dateText, r.timingText, r.statusText.substringAfter(" · ", "").ifBlank { null }) }
                    CalendarAction.LoadMore -> p.loadMore()
                    CalendarAction.Retry -> p.refresh()
                    CalendarAction.SignIn -> onSignIn()
                    is CalendarAction.Scenario -> p.setScenario(action.name)
                }
            }
        }
    }
}

/** Owns one event's presenter; watchlist changes reuse the shared "Add to Watchlist" model. */
@Composable
internal fun EarningsEventScene(
    route: EarningsEventRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    accounts: AccountDependencies?,
    hinge: WindowHinge?,
    onOpenCompany: (String) -> Unit,
    onOpenCalendar: (String?) -> Unit,
    onOpenResults: (String) -> Unit,
    onSignIn: () -> Unit
) {
    val model = viewModel(key = "earnings-event:${route.eventId}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        EarningsEventViewModel(route.eventId, data.earningsRemote(accounts), data::close)
    }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    val watchlistModel = accounts?.let { owner -> viewModel(key = "stock-watchlist") { owner.stockWatchlistViewModel() } }
    val watchlist by (watchlistModel?.state ?: MutableStateFlow(StockWatchlistState())).collectAsStateWithLifecycle()
    val reminders by (accounts?.earningsReminders?.state ?: remember { MutableStateFlow(EarningsRemindersState()) }).collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ReminderTarget?>(null) }
    ReminderSheetHost(accounts, sheet, onDismiss = { sheet = null }, onSignIn = onSignIn)
    val control = if (reminders.signedIn) reminders.control(route.eventId) else ReminderControl.OFF
    val note = reminders.reminderFor(route.eventId)?.let { r -> r.statusMessage ?: r.nextDeliveryText?.let { "Notification: $it" } }
        ?: reminders.autoFor(route.eventId)?.let { "On automatically from your watchlist." }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            EarningsEventScreen(state, watched = state.symbol.uppercase() in watchlist.symbols, watchlistEnabled = watchlist.enabled,
                modifier = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize(),
                reminder = control, reminderNote = listOfNotNull(note, reminders.deliveryWarning.takeIf { control == ReminderControl.ON }).joinToString(" ").ifBlank { null }) { action ->
                when (action) {
                    EventAction.Company -> onOpenCompany(state.symbol)
                    EventAction.Calendar -> onOpenCalendar(state.date)
                    EventAction.Results -> state.reportId?.let(onOpenResults)
                    EventAction.Retry -> model.presenter.refresh()
                    EventAction.SignIn -> onSignIn()
                    EventAction.ToggleWatchlist -> watchlistModel?.toggle(StockSearchResult(state.symbol, state.name, exchange = state.exchange))
                    EventAction.Remind -> sheet = ReminderTarget(route.eventId, state.symbol, state.name, state.dateText.orEmpty(), state.timingText,
                        state.statusText?.substringAfter(" · ", "")?.ifBlank { null })
                }
            }
        }
    }
    watchlist.choosing?.let { stock ->
        org.example.stocksteps.presentation.stocksearch.WatchlistChooserSheet(stock, watchlist.lists,
            onChoose = { watchlistModel?.choose(it) }, onDismiss = { watchlistModel?.cancelChoice() })
    }
}

/** Owns one company's Earnings Details presenter; Company Details stays the company destination. */
@Composable
internal fun EarningsDetailsScene(
    route: EarningsDetailsRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    accounts: AccountDependencies?,
    hinge: WindowHinge?,
    onOpenCompany: (String) -> Unit,
    onSignIn: () -> Unit,
    onUpgrade: () -> Unit
) {
    val model = viewModel(key = "earnings-details:${route.symbol}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        EarningsDetailsViewModel(route.symbol, data.earningsRemote(accounts), accounts, data::close)
    }
    val state by model.presenter.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ReminderTarget?>(null) }
    ReminderSheetHost(accounts, sheet, onDismiss = { sheet = null }, onSignIn = onSignIn)
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            EarningsDetailsScreen(state, Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize(),
                onRemind = { state.details?.next?.let { n -> sheet = ReminderTarget(n.id, n.symbol, state.details?.name ?: n.name, EarningsFormatter.date(n.date),
                    EarningsCalendarRules.timing(n), EarningsFormatter.dateStatus(n.dateStatus)) } },
                onAsk = model.presenter::ask, onRetry = model.presenter::refresh,
                onCompany = { onOpenCompany(route.symbol) }, onSignIn = onSignIn,
                onUpgrade = { model.presenter.dismissUpgrade(); onUpgrade() }, onDismissUpgrade = model.presenter::dismissUpgrade,
                onDismissMessage = model.presenter::dismissMessage)
        }
    }
}
