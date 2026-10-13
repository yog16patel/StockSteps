package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.StockButton
import org.example.stocksteps.designsystem.components.StockButtonVariant
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.portfolio.PortfolioPresenter
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.stocksearch.WatchlistChooserSheet

internal class PortfolioViewModel(val presenter: PortfolioPresenter) : ViewModel() {
    val state = presenter.state
}

/**
 * Portfolio Phase E.2: "Sign in again" for an ended session. Signs out — Firebase sign-out plus this device's cached copy of the account; nothing
 * on the server is deleted — and the app's sign-out handling then opens Login with an empty back stack (`AppNavigation`). Login is never opened
 * while the expired user is still signed in, so there is no loop. A failed sign-out leaves everything as it was (the banner stays).
 */
internal fun CoroutineScope.signInAgain(accounts: AccountDependencies) {
    launch { try { accounts.signOut()() } catch (cause: Exception) { if (cause is CancellationException) throw cause } }
}

@Composable
internal fun PortfolioScene(
    accounts: AccountDependencies,
    onAdd: () -> Unit,
    onHolding: (String, String) -> Unit,
    onOpenCompany: (String) -> Unit,
    holding: HoldingDetailsRoute? = null,
    hinge: WindowHinge? = null,
    onSignIn: () -> Unit = {},
    onAlerts: (String) -> Unit = {},
    onInsights: () -> Unit = {},
    onPractice: (() -> Unit)? = null,
    /** Phase D2: Transaction history for (account, holding symbol); the symbol is null on the dashboard. */
    onTransactions: (String?, String?) -> Unit = { _, _ -> },
    /** Phase D3: Transaction details for a transaction id. */
    onOpenTransaction: (String) -> Unit = {}
) {
    val model = viewModel(key = "portfolio") { PortfolioViewModel(accounts.portfolioPresenter) }
    val state by model.state.collectAsStateWithLifecycle()
    val lists by accounts.watchlists.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf<StockSearchResult?>(null) }
    var chooserError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(holding?.accountId) {
        holding?.let { model.presenter.selectAccount(it.accountId) }
    }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            PortfolioScreen(
                state = state,
                modifier = Modifier
                    .widthIn(max = StockStepsTheme.dimensions.contentMaxWidth)
                    .fillMaxSize(),
                holdingSymbol = holding?.symbol,
                onRefresh = { model.presenter.refresh() },
                onHistory = model.presenter::loadHistory,
                onScenario = model.presenter::showScenario,
                onSelect = model.presenter::selectAccount,
                onSaveAccount = model.presenter::saveAccount,
                onDeleteAccount = model.presenter::deleteAccount,
                onAdd = onAdd,
                onHolding = { symbol -> state.selectedAccountId?.let { onHolding(it, symbol) } },
                onCompany = onOpenCompany,
                onSignIn = onSignIn,
                onAlerts = onAlerts,
                onInsights = onInsights,
                onPractice = onPractice,
                onTransactions = { onTransactions(holding?.accountId ?: state.selectedAccountId, holding?.symbol) },
                onOpenTransaction = onOpenTransaction,
                feedback = accounts.portfolioFeedback,
                onDismissError = model.presenter::clearError,
                onSignInAgain = { scope.signInAgain(accounts) },
                onDeleteTransaction = model.presenter::deleteTransaction,
                onSaveTransaction = { model.presenter.saveTransaction(it, true) },
                onWatchlist = { symbol ->
                    if (state.mockScenario == null) {
                        state.holdings.firstOrNull { it.symbol == symbol }?.let { row ->
                            choosing = StockSearchResult(row.symbol, row.name, exchange = row.exchange, currency = row.currency)
                        }
                    }
                }
            )
        }
    }
    choosing?.let { stock ->
        WatchlistChooserSheet(
            stock = stock,
            lists = lists.value?.watchlists.orEmpty(),
            onChoose = { list ->
                scope.launch {
                    try {
                        accounts.watchlists.add(list, InstrumentRef(stock.symbol, stock.name, stock.exchange, stock.currency))
                        choosing = null
                    } catch (cause: Exception) {
                        if (cause is CancellationException) throw cause
                        chooserError = cause.message ?: "Try again when the server is available."
                    }
                }
            },
            onDismiss = { choosing = null }
        )
    }
    chooserError?.let { message ->
        AlertDialog(
            onDismissRequest = { chooserError = null },
            title = { Text("Could not save to watchlist") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { chooserError = null }) { Text("OK") } }
        )
    }
}

@Composable
internal fun PortfolioEntryScene(
    accounts: AccountDependencies,
    route: PortfolioEntryRoute,
    onSearch: () -> Unit,
    onDone: () -> Unit
) {
    val state by accounts.portfolioPresenter.state.collectAsStateWithLifecycle()
    var createAccount by rememberSaveable { mutableStateOf(false) }
    // D3: the result id current when Save was tapped, and the id of the transaction sent (null = nothing submitted yet).
    var submittedAfter by rememberSaveable { mutableStateOf<Long?>(null) }
    var submittedId by rememberSaveable { mutableStateOf<String?>(null) }
    // Phase D1: a fresh form never shows an earlier, unrelated failure (e.g. a rejected account name) as a save error.
    LaunchedEffect(Unit) { if (submittedAfter == null) accounts.portfolioPresenter.clearError() }

    // D3: close only when the presenter confirms this transaction was added (was: any ledger revision bump, which a change made elsewhere could
    // also cause). A rejection keeps the form open with the server message.
    LaunchedEffect(state.actionResult?.id) {
        val after = submittedAfter ?: return@LaunchedEffect
        val result = state.actionResult ?: return@LaunchedEffect
        if (result.id > after && result.kind == org.example.stocksteps.portfolio.PortfolioActionKind.ADDED && result.transactionId == submittedId) onDone()
    }
    KeyboardResizesContent() // D1.1: imePadding below needs the window to resize, not pan
    Column(Modifier.fillMaxSize().imePadding()) {
        if (state.accounts.none { !it.archived } && !PortfolioFormRules.readOnly(state)) {
            StockButton(PortfolioDisplay.CREATE_ACCOUNT_FIRST, { createAccount = true },
                Modifier.padding(horizontal = StockStepsTheme.spacing.screen).fillMaxWidth(), enabled = state.signedIn && !state.busy)
        }
        // Phase D1: the security search sits next to the symbol field inside the form; samples get the read-only explanation instead.
        PortfolioTransactionForm(
            state = state,
            modifier = Modifier.weight(1f),
            initialSymbol = route.symbol,
            initialName = route.name,
            initialExchange = route.exchange,
            initialCurrency = route.currency,
            onSearch = onSearch,
            onUseSavedAccounts = { accounts.portfolioPresenter.showScenario(null) },
            onSubmit = {
                submittedAfter = PortfolioActionFeedback.baseline(state.actionResult)
                submittedId = it.id
                accounts.portfolioPresenter.saveTransaction(it)
            }
        )
    }
    if (createAccount) {
        PortfolioAccountDialog(
            existing = null,
            onDismiss = { createAccount = false },
            onSave = {
                accounts.portfolioPresenter.saveAccount(it)
                createAccount = false
            }
        )
    }
}
