package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.portfolio.*

@Composable
internal fun PortfolioScreen(
    state: PortfolioUiState,
    modifier: Modifier = Modifier,
    holdingSymbol: String?,
    onRefresh: () -> Unit,
    onHistory: (String) -> Unit,
    onScenario: (String?) -> Unit,
    onSelect: (String) -> Unit,
    onSaveAccount: (PortfolioAccount) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onAdd: () -> Unit,
    onHolding: (String) -> Unit,
    onCompany: (String) -> Unit,
    onSignIn: () -> Unit,
    onAlerts: (String) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onSaveTransaction: (PortfolioTransaction) -> Unit,
    onWatchlist: (String) -> Unit
) {
    var accountEditor by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<PortfolioAccount?>(null) }
    var deletingAccount by remember { mutableStateOf<PortfolioAccount?>(null) }
    var deletingTransaction by remember { mutableStateOf<PortfolioTransaction?>(null) }
    var editingTransaction by remember { mutableStateOf<PortfolioTransaction?>(null) }
    val spacing = StockStepsTheme.spacing
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        item {
            Text(if (holdingSymbol == null) "Portfolio" else "Holding details", style = StockStepsTheme.typography.screenTitle)
            Text("Your investments, separate from your watchlists", style = StockStepsTheme.typography.small)
        }
        if (state.mockAvailable) item {
            PortfolioChoice("Sample scenario", state.mockScenario ?: "My saved accounts", listOf("My saved accounts") + PortfolioFixtureCatalog.ids) {
                onScenario(it.takeUnless { it == "My saved accounts" })
            }
            if (state.mockScenario != null) Text("Read-only sample. Switch to My saved accounts to make changes.")
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRefresh) { Text("Retry") } } }
        if (state.offline) item { Text("Offline · showing your last saved portfolio") }
        if (!state.signedIn && state.mockScenario == null) item { Text("Sign in to save your portfolio securely."); Button(onClick = onSignIn) { Text("Sign in") } }
        if (state.accounts.none { !it.archived }) {
            item {
                Text("Start tracking what you own", style = StockStepsTheme.typography.sectionTitle)
                Text("Create an account, then add an existing investment or record a trade. Saving a stock to a watchlist does not buy it.")
                Button(onClick = { editingAccount = null; accountEditor = true }, enabled = state.signedIn && !state.busy) { Text("Create portfolio") }
            }
        } else {
            if (holdingSymbol == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        state.accounts.filterNot { it.archived }.forEach { account ->
                            FilterChip(selected = account.id == state.selectedAccountId, onClick = { onSelect(account.id) },
                                label = { Text("${account.name} · ${account.reportingCurrency.name}") })
                        }
                        TextButton(onClick = { editingAccount = null; accountEditor = true }) { Text("Add account") }
                    }
                }
                item {
                    PortfolioSummaryCard(state)
                    Text(state.dailyNotice, style = StockStepsTheme.typography.caption)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Button(onClick = onAdd, enabled = !state.busy) { Text("Add investment / transaction") }
                    }
                }
                item {
                    Text("Portfolio history", style = StockStepsTheme.typography.sectionTitle)
                    PortfolioHistoryChart(state, onHistory)
                }
            }
            item { Text("Holdings", style = StockStepsTheme.typography.sectionTitle) }
            val rows = state.holdings.filter { holdingSymbol == null || it.symbol == holdingSymbol }
            if (rows.isEmpty()) item { Text("No holdings yet. Add an existing investment with its effective date, or record a buy.") }
            items(rows, key = { it.symbol }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { if (holdingSymbol == null) onHolding(row.symbol) },
                    colors = CardDefaults.cardColors(containerColor = StockStepsTheme.colors.surface)
                ) {
                    Column(Modifier.padding(spacing.cardPadding), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        org.example.stocksteps.designsystem.components.StockRow(row.symbol, row.name, logoUrl = row.logoUrl)
                        Text("${row.symbol} · ${row.exchange ?: "Exchange not provided"} · ${row.quantity} shares")
                        Text("${row.currency} ${PortfolioFormat.amount(row.value)}", style = StockStepsTheme.typography.largeNumber, fontFamily = FontFamily.Monospace)
                        Text("Price ${PortfolioFormat.amount(row.price)} · cost ${PortfolioFormat.amount(row.basis)}")
                        Text("Unrealized ${PortfolioFormat.amount(row.gain)} (${PortfolioFormat.amount(row.gainPercent)}%)", color = if (row.gain?.startsWith('-') == true) StockStepsTheme.colors.negative else StockStepsTheme.colors.positive)
                        if (row.stale) Text("Last available quote · delayed or stale")
                        if (holdingSymbol != null) {
                            Text("Realized ${row.realized} · Dividends ${row.dividends}")
                            TextButton(onClick = { onCompany(row.symbol) }) { Text("Company details") }
                            TextButton(onClick = { onAlerts(row.symbol) }) { Text("Price alerts") }
                            TextButton(onClick = { onWatchlist(row.symbol) }) { Text("Add to watchlist") }
                        }
                    }
                }
            }
            item {
                Text("Cash by currency", style = StockStepsTheme.typography.sectionTitle)
                Text(state.cash.joinToString("\n").ifEmpty { "No cash recorded" })
                Text("A negative balance represents unfunded or borrowed cash. Record deposits separately from existing investments.", style = StockStepsTheme.typography.caption)
            }
            if (holdingSymbol == null) item {
                Text("Allocation by holding and currency", style = StockStepsTheme.typography.sectionTitle)
                state.allocations.forEach { allocation ->
                    Text("${allocation.symbol} · ${allocation.currency.name} · ${PortfolioFormat.amount(allocation.percent)}% of account value")
                }
                state.currencyAllocations.forEach { allocation ->
                    Text("${allocation.currency.name} · ${PortfolioFormat.amount(allocation.percent)}% · ${state.currency} ${PortfolioFormat.amount(allocation.reportingValue)}")
                }
                Text("Cash is included in account value. Borrowed cash can make a holding exceed 100%. Sector and ETF look-through allocations are not estimated.", style = StockStepsTheme.typography.caption)
            }
            item { Text("Dividends received", style = StockStepsTheme.typography.sectionTitle); Text(state.dividends.joinToString("\n").ifEmpty { "No dividends recorded" }) }
            item { Text("Transactions", style = StockStepsTheme.typography.sectionTitle) }
            items(state.transactions.filter { holdingSymbol == null || it.instrument?.symbol == holdingSymbol }, key = { it.id }) { transaction ->
                Column {
                    Text("${transaction.type.name.replace('_', ' ')} · ${transaction.instrument?.symbol.orEmpty()}")
                    Text("${transaction.tradeDate} · ${transaction.currency.name} ${transaction.netAmount}")
                    Row {
                        TextButton(onClick = { editingTransaction = transaction }) { Text("Edit") }
                        TextButton(onClick = { deletingTransaction = transaction }) { Text("Delete") }
                    }
                }
            }
            if (holdingSymbol == null) item {
                state.accounts.firstOrNull { it.id == state.selectedAccountId }?.let { account ->
                    Text("${account.category.name.replace('_', ' ')} · reporting ${account.reportingCurrency.name}")
                    TextButton(onClick = { editingAccount = account; accountEditor = true }) { Text("Account settings") }
                    TextButton(onClick = { deletingAccount = account }) { Text("Delete account") }
                }
            }
        }
        item { TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Refresh portfolio") } }
    }
    if (accountEditor) PortfolioAccountDialog(editingAccount, onDismiss = { accountEditor = false }, onSave = { onSaveAccount(it); accountEditor = false })
    deletingAccount?.let { account ->
        AlertDialog(onDismissRequest = { deletingAccount = null }, title = { Text("Delete ${account.name}?") },
            text = { Text("This deletes this account and its transactions. Watchlists and alerts stay saved.") },
            confirmButton = { TextButton(onClick = { onDeleteAccount(account.id); deletingAccount = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingAccount = null }) { Text("Cancel") } })
    }
    deletingTransaction?.let { transaction ->
        AlertDialog(onDismissRequest = { deletingTransaction = null }, title = { Text("Delete transaction?") },
            text = { Text("Balances will be recalculated. Deletion is rejected if a later sale would exceed your shares.") },
            confirmButton = { TextButton(onClick = { onDeleteTransaction(transaction.id); deletingTransaction = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingTransaction = null }) { Text("Cancel") } })
    }
    editingTransaction?.let { transaction ->
        AlertDialog(onDismissRequest = { editingTransaction = null }, confirmButton = {}, text = {
            PortfolioTransactionForm(state = state, initialSymbol = transaction.instrument?.symbol.orEmpty(), initialName = transaction.instrument?.name, initialExchange = transaction.instrument?.exchange,
                initialCurrency = transaction.currency.name, existing = transaction, onSubmit = { onSaveTransaction(it); editingTransaction = null })
        })
    }
}

@Composable
internal fun PortfolioSummaryCard(state: PortfolioUiState, onOpen: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StockStepsTheme.colors.surface)
    ) {
        Column(Modifier.padding(StockStepsTheme.spacing.cardPadding), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            Text(state.accountName, style = StockStepsTheme.typography.cardTitle)
            if (state.accounts.none { !it.archived }) {
                Text("Track the investments you own, alongside your separate watchlists.")
            } else {
            Text("${state.currency} ${PortfolioFormat.amount(state.total)}", style = StockStepsTheme.typography.largeNumber, fontFamily = FontFamily.Monospace)
            Text("Today ${PortfolioFormat.amount(state.dailyGain)} (${PortfolioFormat.amount(state.dailyPercent)}%)")
            if (state.accounts.count { !it.archived } > 1) Text("All accounts · ${state.currency} ${PortfolioFormat.amount(state.combinedTotal)}")
            Text("Invested cost ${PortfolioFormat.amount(state.basis)}")
            Text("Unrealized ${PortfolioFormat.amount(state.unrealized)} · Realized ${PortfolioFormat.amount(state.realized)}")
            state.notice?.let { Text(it, style = StockStepsTheme.typography.caption) }
            state.quoteAsOf?.let { Text("Prices as of $it", style = StockStepsTheme.typography.caption) }
            state.fxAsOf?.let { Text("Exchange rate as of $it", style = StockStepsTheme.typography.caption) }
            }
            if (state.mockScenario != null) Text("Read-only sample · ${state.mockScenario}", style = StockStepsTheme.typography.caption)
            if (onOpen != null) TextButton(onClick = onOpen) { Text(if (state.accounts.isEmpty()) "Create portfolio" else "View portfolio") }
        }
    }
}
