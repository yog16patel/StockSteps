package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.theme.StockBannerKind

/**
 * Phase D2: acquires the **same** portfolio presenter as the dashboard (no second load) and wires its actions; rendering is
 * [PortfolioTransactionsScreen]. The holding scope from [route] applies only while its account is selected; switching account drops it for good.
 */
@Composable
internal fun PortfolioTransactionsScene(
    accounts: AccountDependencies,
    route: PortfolioTransactionsRoute,
    onAdd: () -> Unit,
    onSignIn: () -> Unit,
    hinge: WindowHinge? = null,
    /** Phase D3: Transaction details for a transaction id. */
    onOpenTransaction: (String) -> Unit = {}
) {
    val model = viewModel(key = "portfolio") { PortfolioViewModel(accounts.portfolioPresenter) }
    val state by model.state.collectAsStateWithLifecycle()
    var scopeCleared by rememberSaveable { mutableStateOf(false) }
    // Account switched away from the holding's account: the symbol no longer belongs here, so the scope is dropped (not restored on switching back).
    LaunchedEffect(state.selectedAccountId) {
        if (route.accountId != null && state.selectedAccountId != null && state.selectedAccountId != route.accountId) scopeCleared = true
    }
    val symbol = if (scopeCleared) null else PortfolioTransactionsDisplay.activeSymbol(route.symbol, route.accountId, state.selectedAccountId)
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            PortfolioTransactionsScreen(
                state = state,
                symbol = symbol,
                modifier = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize(),
                onClearSymbol = { scopeCleared = true },
                onSelectAccount = model.presenter::selectAccount,
                onRefresh = { model.presenter.refresh() },
                onAdd = onAdd,
                onSignIn = onSignIn,
                onDeleteTransaction = model.presenter::deleteTransaction,
                onSaveTransaction = { model.presenter.saveTransaction(it, true) },
                onOpenTransaction = onOpenTransaction,
                feedback = accounts.portfolioFeedback
            )
        }
    }
}

/**
 * Transaction history (Portfolio Phase D2): account → holding scope → type filter → count → month groups of the dashboard's transaction rows.
 * Display only: rows are `state.transactions` filtered and grouped by [PortfolioTransactionsDisplay], in the presenter's order; nothing is
 * summed or computed. Edit / delete reuse the dashboard's sheet, dialog and presenter calls; samples hide Delete.
 */
@Composable
internal fun PortfolioTransactionsScreen(
    state: PortfolioUiState,
    symbol: String?,
    modifier: Modifier = Modifier,
    onClearSymbol: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onSignIn: () -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onSaveTransaction: (PortfolioTransaction) -> Unit,
    onOpenTransaction: ((String) -> Unit)? = null,
    feedback: PortfolioFeedbackTracker? = null
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var filterName by rememberSaveable { mutableStateOf(PortfolioTransactionFilter.ALL.name) }
    val filter = PortfolioTransactionFilter.valueOf(filterName)
    var sampleDetails by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PortfolioTransaction?>(null) }
    var deleting by remember { mutableStateOf<PortfolioTransaction?>(null) }
    val readOnly = PortfolioFormRules.readOnly(state)
    val activeAccounts = state.accounts.filterNot { it.archived }
    val selectedAccount = activeAccounts.firstOrNull { it.id == state.selectedAccountId }
    val scoped = PortfolioTransactionsDisplay.filter(state.transactions, PortfolioTransactionFilter.ALL, symbol)
    val shown = PortfolioTransactionsDisplay.filter(scoped, filter)
    // D3: the confirmation sits above the list (an empty list item would still take a gap), like the iOS top inset.
    Column(modifier) {
        feedback?.let { PortfolioActionBanner(it, state.actionResult, Modifier.padding(start = spacing.screen, end = spacing.screen, top = spacing.sm)) } // e.g. "Transaction deleted" after details
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(spacing.screen), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            // Status: the dashboard's banners, same copy and order (sample, loading, sync problems, sign-in).
            if (state.mockScenario != null) item {
                StockBanner(StockBannerKind.SAMPLE, if (sampleDetails) "${PortfolioDisplay.SAMPLE_SHORT} ${PortfolioDisplay.SAMPLE_BANNER}" else PortfolioDisplay.SAMPLE_SHORT,
                    actionText = if (sampleDetails) PortfolioDisplay.SAMPLE_HIDE else PortfolioDisplay.SAMPLE_DETAILS, onAction = { sampleDetails = !sampleDetails })
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error != null || state.offline) item {
                StockBanner(
                    kind = if (state.error != null) StockBannerKind.ERROR else StockBannerKind.WARNING,
                    title = if (state.offline) PortfolioDisplay.OFFLINE_TITLE else PortfolioDisplay.REFRESH_FAILED_TITLE,
                    message = state.error ?: PortfolioDisplay.OUT_OF_DATE,
                    actionText = if (state.error != null) "Retry" else null, onAction = onRefresh)
            }
            if (!state.signedIn && state.mockScenario == null) item {
                StockBanner(StockBannerKind.INFO, PortfolioDisplay.SIGN_IN_PROMPT, actionText = "Sign in", onAction = onSignIn)
            }
            if (activeAccounts.isNotEmpty()) item {
                PortfolioAccountSelector(activeAccounts, selectedAccount, onSelectAccount, onAddAccount = null, addEnabled = false)
            }
            symbol?.let { held -> item {
                // Stacked (line, then its action) so neither squeezes the other at large text.
                Column {
                    Text(PortfolioTransactionsDisplay.scopeLine(held), style = typography.bodySemiBold, color = colors.textTitle)
                    StockButton(PortfolioTransactionsDisplay.SHOW_ALL_HOLDINGS, onClearSymbol, variant = StockButtonVariant.TEXT)
                }
            } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    StockPillSelector(PortfolioTransactionsDisplay.filters, filter, PortfolioTransactionsDisplay::label, { filterName = it.name },
                        Modifier.semantics { contentDescription = PortfolioTransactionsDisplay.FILTERS_LABEL })
                    PortfolioTransactionsDisplay.note(filter)?.let { Text(it, style = typography.caption, color = colors.textSupporting) }
                    // The count is a row count (never an amount); announced politely when the filter changes.
                    Text(PortfolioTransactionsDisplay.summary(shown.size, filter),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = PortfolioTransactionsDisplay.spokenFilter(filter, shown.size) },
                        style = typography.small, color = colors.textSupporting)
                }
            }
            if (shown.isEmpty()) item {
                StockCard(bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    if (scoped.isEmpty() && symbol == null) {
                        Text(PortfolioDisplay.TRANSACTIONS_EMPTY, style = typography.small, color = colors.textSupporting)
                        if (activeAccounts.isNotEmpty() && !readOnly) StockButton("+ Add transaction", onAdd, enabled = !state.busy)
                    } else {
                        Text(PortfolioTransactionsDisplay.emptyMessage(filter, symbol), style = typography.small, color = colors.textSupporting)
                        if (filter != PortfolioTransactionFilter.ALL) StockButton(PortfolioTransactionsDisplay.SHOW_ALL_FILTER,
                            { filterName = PortfolioTransactionFilter.ALL.name }, variant = StockButtonVariant.TEXT)
                    }
                }
            }
            PortfolioTransactionsDisplay.groups(shown).forEach { month ->
                item(key = "month-${month.key}") {
                    Text(month.title, Modifier.padding(top = spacing.xs).semantics { heading() }, style = typography.label, color = colors.textSecondary)
                }
                item(key = "rows-${month.key}") {
                    StockCard(bordered = false, contentPadding = PaddingValues(vertical = spacing.xs)) {
                        month.transactions.forEachIndexed { index, transaction ->
                            if (index > 0) StockDivider(Modifier.padding(horizontal = spacing.cardPadding))
                            PortfolioTransactionRow(transaction, state.logoFor(transaction), onOpen = onOpenTransaction?.let { open -> { open(transaction.id) } },
                                onEdit = { editing = transaction }, onDelete = if (readOnly) null else ({ deleting = transaction }))
                        }
                    }
                }
            }
        }
    }
    deleting?.let { transaction ->
        PortfolioDeleteTransactionDialog(transaction, onConfirm = { onDeleteTransaction(transaction.id); deleting = null }, onDismiss = { deleting = null })
    }
    editing?.let { transaction -> PortfolioEditSheet(state, transaction, onSaveTransaction) { editing = null } }
}
