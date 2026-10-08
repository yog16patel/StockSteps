package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import kotlinx.coroutines.launch
import org.example.stocksteps.WindowHinge
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
    onInsights: () -> Unit = {}
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
    val revision by accounts.portfolio.state.collectAsStateWithLifecycle()
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var submittedRevision by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(revision.value?.revision) {
        submittedRevision?.let { submitted ->
            if ((revision.value?.revision ?: 0) > submitted) onDone()
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        if (state.accounts.none { !it.archived }) {
            Button(onClick = { createAccount = true }, enabled = state.signedIn && !state.busy) {
                Text("Create an account first")
            }
        }
        TextButton(onClick = onSearch) { Text("Search and select a security") }
        PortfolioTransactionForm(
            state = state,
            modifier = Modifier.weight(1f),
            initialSymbol = route.symbol,
            initialName = route.name,
            initialExchange = route.exchange,
            initialCurrency = route.currency,
            onSubmit = {
                submittedRevision = revision.value?.revision ?: 0
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
