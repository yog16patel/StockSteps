package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.portfolio.*
import org.example.stocksteps.theme.StockBadgeKind
import org.example.stocksteps.theme.StockBannerKind

private enum class PortfolioMode { MINE, PRACTICE }

/**
 * Portfolio dashboard (Global UI Refinement Phase 3, the reference screen): mode selector → account selector → value hero → add
 * transaction → performance → holdings → cash / allocation → dividends → transactions → insights. Low-frequency account actions live
 * in the overflow menu, explanations in the (i) sheet. Every number comes from [PortfolioUiState]; missing values stay missing.
 * With [holdingSymbol] the same screen shows one holding's details (unchanged behaviour).
 */
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
    onInsights: () -> Unit = {},
    onDeleteTransaction: (String) -> Unit,
    onSaveTransaction: (PortfolioTransaction) -> Unit,
    onWatchlist: (String) -> Unit,
    /** Opens the separate, simulated Practice Portfolio (never mixed into these totals). */
    onPractice: (() -> Unit)? = null
) {
    var accountEditor by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<PortfolioAccount?>(null) }
    var deletingAccount by remember { mutableStateOf<PortfolioAccount?>(null) }
    var deletingTransaction by remember { mutableStateOf<PortfolioTransaction?>(null) }
    var editingTransaction by remember { mutableStateOf<PortfolioTransaction?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var allTransactions by remember { mutableStateOf(false) }
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val activeAccounts = state.accounts.filterNot { it.archived }
    val selectedAccount = activeAccounts.firstOrNull { it.id == state.selectedAccountId }
    val listMode = holdingSymbol == null
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (listMode) "Portfolio" else "Holding details", Modifier.weight(1f).semantics { heading() },
                    style = typography.screenTitle, color = colors.textTitle)
                if (listMode) {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(StockIcons.Info, contentDescription = "About these numbers", tint = colors.iconSecondary)
                    }
                    PortfolioOverflowMenu(
                        account = selectedAccount, busy = state.busy, onRefresh = onRefresh,
                        onSettings = { editingAccount = it; accountEditor = true }, onDelete = { deletingAccount = it })
                }
            }
        }
        if (listMode && onPractice != null) item {
            StockSegmentedControl(
                options = listOf(StockSegment(PortfolioMode.MINE, "My Portfolio"), StockSegment(PortfolioMode.PRACTICE, "Practice")),
                selected = PortfolioMode.MINE,
                onSelect = { if (it == PortfolioMode.PRACTICE) onPractice() }
            )
        }
        // Status: sample scenario, loading, sync errors, offline and sign-in — compact, above the numbers they qualify.
        if (state.mockAvailable) item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StockSelectField(label = "Sample scenario", options = listOf(SAVED_ACCOUNTS) + PortfolioFixtureCatalog.ids,
                    selected = state.mockScenario ?: SAVED_ACCOUNTS, optionLabel = { it }, onSelect = { onScenario(it.takeUnless { it == SAVED_ACCOUNTS }) })
                if (state.mockScenario != null) StockBanner(StockBannerKind.SAMPLE, "Read-only sample. Switch to My saved accounts to make changes.")
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        // One banner for sync problems: offline (cached data) and/or the error, with Retry — never stale data presented as current.
        if (state.error != null || state.offline) item {
            StockBanner(
                kind = if (state.error != null) StockBannerKind.ERROR else StockBannerKind.WARNING,
                title = if (state.offline) "Offline · last saved portfolio" else "Couldn't refresh your portfolio",
                message = state.error ?: "Values may be out of date.",
                actionText = if (state.error != null) "Retry" else null, onAction = onRefresh)
        }
        if (!state.signedIn && state.mockScenario == null) item {
            StockBanner(StockBannerKind.INFO, "Sign in to save your portfolio securely.", actionText = "Sign in", onAction = onSignIn)
        }
        if (activeAccounts.isEmpty()) {
            item {
                StockEmptyState(
                    message = "Create an account, then add an existing investment or record a trade. Saving a stock to a watchlist does not buy it.",
                    title = "Start tracking what you own", icon = StockIcons.PieChart,
                    actionText = if (state.signedIn && !state.busy) "Create portfolio" else null,
                    onAction = { editingAccount = null; accountEditor = true })
            }
        } else {
            if (listMode) {
                item {
                    PortfolioAccountSelector(activeAccounts, selectedAccount, onSelect,
                        onAddAccount = { editingAccount = null; accountEditor = true }, addEnabled = state.signedIn && !state.busy)
                }
                item { PortfolioHero(state, activeAccounts.size) }
                item {
                    StockButton("+ Add transaction", onAdd, Modifier.fillMaxWidth(), enabled = !state.busy)
                }
                item { PortfolioPerformanceCard(state, onHistory) }
            }
            val rows = state.holdings.filter { holdingSymbol == null || it.symbol == holdingSymbol }
            item { PortfolioSectionTitle(if (listMode && rows.isNotEmpty()) "Holdings (${rows.size})" else "Holdings") }
            if (rows.isEmpty()) item {
                StockCard { Text("No holdings yet. Add an existing investment with its effective date, or record a buy.", style = typography.small, color = colors.textSupporting) }
            } else item {
                StockCard(contentPadding = PaddingValues(vertical = spacing.xs)) {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) StockDivider()
                        PortfolioHoldingRowView(row, onClick = if (listMode) ({ onHolding(row.symbol) }) else null)
                    }
                }
            }
            if (!listMode) rows.firstOrNull()?.let { row -> item {
                StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    StockMetricGrid(listOf(
                        StockMetricItem("Price", "${row.currency} ${PortfolioFormat.amount(row.price)}"),
                        StockMetricItem("Cost", "${row.currency} ${PortfolioFormat.amount(row.basis)}"),
                        StockMetricItem("Realized", row.realized),
                        StockMetricItem("Dividends", row.dividends)
                    ), maxColumns = 2)
                    StockButton("Company details", { onCompany(row.symbol) }, variant = StockButtonVariant.TEXT)
                    StockButton("Price alerts", { onAlerts(row.symbol) }, variant = StockButtonVariant.TEXT)
                    StockButton("Add to watchlist", { onWatchlist(row.symbol) }, variant = StockButtonVariant.TEXT)
                }
            } }
            if (listMode) item { PortfolioAllocationCard(state) }
            // Cash and dividends share one card: each is a labelled line, so empty values cost one row, not a whole section.
            item {
                PortfolioSectionTitle("Cash and dividends")
                StockCard(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    PortfolioValueLines("Cash", state.cash, "None recorded")
                    StockDivider()
                    PortfolioValueLines("Dividends received", state.dividends, "None recorded")
                }
            }
            val transactions = state.transactions.filter { holdingSymbol == null || it.instrument?.symbol == holdingSymbol }
            item { PortfolioSectionTitle(if (transactions.isEmpty()) "Transactions" else "Transactions (${transactions.size})") }
            item {
                StockCard(contentPadding = PaddingValues(vertical = spacing.xs)) {
                    if (transactions.isEmpty()) {
                        Text("No transactions yet", Modifier.padding(horizontal = spacing.cardPadding, vertical = spacing.sm), style = typography.small, color = colors.textSupporting)
                    }
                    val shown = if (allTransactions) transactions else transactions.take(RECENT_TRANSACTIONS)
                    shown.forEachIndexed { index, transaction ->
                        if (index > 0) StockDivider()
                        PortfolioTransactionRow(transaction, onEdit = { editingTransaction = transaction }, onDelete = { deletingTransaction = transaction })
                    }
                    if (transactions.size > RECENT_TRANSACTIONS) {
                        StockButton(if (allTransactions) "Show recent only" else "Show all ${transactions.size}", { allTransactions = !allTransactions },
                            Modifier.padding(horizontal = spacing.sm), variant = StockButtonVariant.TEXT)
                    }
                }
            }
            if (listMode) item {
                StockCard(onClick = onInsights, onClickLabel = "Open portfolio insights") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        StockIconTile(StockIcons.TrendingUp, colors.primaryContainer, colors.primaryText)
                        Column(Modifier.weight(1f)) {
                            Text("Portfolio insights", style = typography.bodySemiBold, color = colors.textTitle)
                            Text("Performance, allocation and concentration", style = typography.small, color = colors.textSupporting)
                        }
                        Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.iconSecondary)
                    }
                }
            }
        }
    }
    if (showAbout) PortfolioAboutDialog(state) { showAbout = false }
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

private const val RECENT_TRANSACTIONS = 5
private const val SAVED_ACCOUNTS = "My saved accounts"

/** "Cash      CAD 1,200.00" — label left, one value per line right; [empty] when there is none (never an invented 0.00). */
@Composable
private fun PortfolioValueLines(label: String, values: List<String>, empty: String) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.md)) {
        Text(label, Modifier.weight(1f), style = typography.small, color = colors.textSupporting)
        Column(horizontalAlignment = Alignment.End) {
            if (values.isEmpty()) Text(empty, style = typography.small, color = colors.textMeta)
            values.forEach { Text(it, style = typography.numberLabelStrong, color = colors.textValue) }
        }
    }
}

@Composable
private fun PortfolioSectionTitle(title: String) {
    Text(title, Modifier.padding(top = StockStepsTheme.spacing.md).semantics { heading() }, style = StockStepsTheme.typography.sectionTitle,
        color = StockStepsTheme.colors.textTitle)
}

/** Account settings, refresh and delete — low-frequency actions kept out of the dashboard. */
@Composable
private fun PortfolioOverflowMenu(account: PortfolioAccount?, busy: Boolean, onRefresh: () -> Unit, onSettings: (PortfolioAccount) -> Unit, onDelete: (PortfolioAccount) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = StockStepsTheme.colors
    Box {
        IconButton(onClick = { open = true }) { Icon(StockIcons.More, contentDescription = "Portfolio actions", tint = colors.iconSecondary) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Refresh portfolio") }, enabled = !busy, onClick = { open = false; onRefresh() })
            if (account != null) {
                DropdownMenuItem(text = { Text("Account settings") }, onClick = { open = false; onSettings(account) })
                DropdownMenuItem(text = { Text("Delete account…", color = colors.negativeText) }, onClick = { open = false; onDelete(account) })
            }
        }
    }
}

/** "Personal · CAD ▾" with a menu of accounts (name, type, currency) and "+ Add". */
@Composable
private fun PortfolioAccountSelector(accounts: List<PortfolioAccount>, selected: PortfolioAccount?, onSelect: (String) -> Unit,
                                     onAddAccount: () -> Unit, addEnabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).clip(StockStepsTheme.shapes.button)
                    .background(colors.surface).clickable(role = Role.DropdownList, onClickLabel = "Change account") { open = true }
                    .semantics { contentDescription = "Account: ${selected?.let(PortfolioPresentation::accountLabel) ?: "none"}" }
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Text(selected?.let(PortfolioPresentation::accountLabel) ?: "Choose an account", Modifier.weight(1f, fill = false),
                    style = StockStepsTheme.typography.bodySemiBold, color = colors.textTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(StockIcons.ChevronDown, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.icon))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(account.name, style = StockStepsTheme.typography.bodySemiBold)
                                Text(PortfolioPresentation.accountDetail(account), style = StockStepsTheme.typography.caption, color = colors.textSupporting)
                            }
                        },
                        trailingIcon = if (account.id == selected?.id) ({ Icon(StockIcons.Check, contentDescription = "Selected", tint = colors.primaryText) }) else null,
                        onClick = { open = false; onSelect(account.id) })
                }
            }
        }
        StockButton("+ Add", onAddAccount, variant = StockButtonVariant.SECONDARY, enabled = addEnabled)
    }
}

/** Value hero: total (dominant), today's change (or that it is unavailable), invested / unrealized / realized, freshness. */
@Composable
private fun PortfolioHero(state: PortfolioUiState, accountCount: Int) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text("Total portfolio value", style = typography.label, color = colors.textSupporting)
        Text("${state.currency} ${PortfolioFormat.amount(state.total)}", style = typography.largeNumber, color = colors.textValue)
        val today = PortfolioPresentation.todayChange(state)
        if (today != null) {
            StockPriceChange("$today today", PortfolioPresentation.direction(state.dailyGain), style = typography.numberMedium, maxLines = 2)
        } else {
            // The reason stays one tap away instead of adding two lines to every view of the hero.
            var why by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(PortfolioPresentation.TODAY_UNAVAILABLE, style = typography.small, color = colors.textSupporting)
                if (state.dailyNotice.isNotBlank()) {
                    Text(if (why) "Hide" else "Why?", Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) { why = !why },
                        style = typography.label, color = colors.primaryText)
                }
            }
            if (why) Text(state.dailyNotice, style = typography.caption, color = colors.textMeta)
        }
        StockDivider(Modifier.padding(vertical = spacing.xs))
        StockMetricGrid(listOf(
            StockMetricItem("Invested cost", PortfolioFormat.amount(state.basis)),
            StockMetricItem("Unrealized P/L", PortfolioPresentation.signedAmount(state.unrealized) ?: "—", direction = PortfolioPresentation.direction(state.unrealized)),
            StockMetricItem("Realized P/L", PortfolioPresentation.signedAmount(state.realized) ?: "—", direction = PortfolioPresentation.direction(state.realized))
        ), rowsWhenNarrow = true)
        if (accountCount > 1) Text("All accounts · ${state.currency} ${PortfolioFormat.amount(state.combinedTotal)}", style = typography.small, color = colors.textSupporting)
        state.notice?.let { Text(it, style = typography.caption, color = colors.cautionText) }
        PortfolioPresentation.freshnessLines(state).forEach { Text(it, style = typography.caption, color = colors.textMeta) }
        if (state.mockScenario != null) StockStatusBadge("Sample · ${state.mockScenario}", StockBadgeKind.SAMPLE, size = StockBadgeSize.COMPACT)
    }
}

/**
 * Compact holding row: logo · name / ticker · shares — value / unrealized change; one spoken description. At large font scale the
 * values move under the name so long company names never break mid-word.
 */
@Composable
private fun PortfolioHoldingRowView(row: PortfolioHoldingRow, onClick: (() -> Unit)?) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val description = PortfolioPresentation.holdingDescription(row)
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale
    val values: @Composable (Alignment.Horizontal) -> Unit = { alignment ->
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(PortfolioPresentation.holdingValue(row), style = typography.numberLabelStrong, color = colors.textValue, maxLines = 1)
            val direction = PortfolioPresentation.direction(row.gain)
            PortfolioPresentation.holdingChangeLines(row)?.let { (first, second) ->
                StockPriceChange(first, direction)
                second?.let { Text(it, style = typography.numberLabel, color = if (direction == PriceDirection.DOWN) colors.negativeText else if (direction == PriceDirection.UP) colors.positiveText else colors.textSupporting) }
            } ?: Text("Gain unavailable", style = typography.caption, color = colors.textMeta)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // Narrow rows (small phones, split screen) and large text put the values under the name, so neither is squeezed.
    val stacked = largeText || maxWidth < PortfolioLayout.stackedRowWidth.dp
    Row(
        Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.rowMinHeight)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Open holding", onClick = onClick) else Modifier)
            .semantics { contentDescription = description }
            .padding(horizontal = spacing.cardPadding, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Row(Modifier.weight(1f).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            StockTickerAvatar(symbol = row.symbol, logoUrl = row.logoUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(row.name, style = typography.bodySemiBold, color = colors.textTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(PortfolioPresentation.holdingSubtitle(row), style = typography.small, color = colors.textSupporting)
                if (row.stale) Text("Last available quote · delayed or stale", style = typography.caption, color = colors.textMeta)
                if (stacked) values(Alignment.Start)
            }
        }
        if (!stacked) values(Alignment.End)
    }
    }
}

/** Human-readable transaction with Edit / Delete in a menu. */
@Composable
private fun PortfolioTransactionRow(transaction: PortfolioTransaction, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    var open by remember { mutableStateOf(false) }
    val title = listOfNotNull(PortfolioPresentation.transactionLabel(transaction.type), PortfolioPresentation.transactionDetail(transaction)).joinToString(" · ")
    Row(Modifier.fillMaxWidth().padding(start = spacing.cardPadding, top = spacing.xs, bottom = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(title, style = typography.bodyMedium, color = colors.textTitle)
            Text("${PortfolioPresentation.dateLabel(transaction.tradeDate)} · ${PortfolioPresentation.transactionAmount(transaction)}", style = typography.small, color = colors.textSupporting)
        }
        Box {
            IconButton(onClick = { open = true }) { Icon(StockIcons.More, contentDescription = "Actions for $title", tint = colors.iconSecondary) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { open = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete…", color = colors.negativeText) }, onClick = { open = false; onDelete() })
            }
        }
    }
}

/** Allocation by holding and by currency as labelled bars; one line when there is nothing to allocate (no empty chart area). */
@Composable
private fun PortfolioAllocationCard(state: PortfolioUiState) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    PortfolioSectionTitle("Allocation")
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (state.allocations.isEmpty() && state.currencyAllocations.isEmpty()) {
            Text("Allocation appears once you have holdings.", style = typography.small, color = colors.textSupporting)
        }
        if (state.allocations.isNotEmpty()) Text("By holding", style = typography.label, color = colors.textSupporting)
        state.allocations.forEach { AllocationBar("${it.symbol} · ${it.currency.name}", it.percent, it.reportingValue?.let { v -> "${state.currency} ${PortfolioFormat.amount(v)}" }) }
        if (state.currencyAllocations.isNotEmpty()) Text("By currency", Modifier.padding(top = spacing.xs), style = typography.label, color = colors.textSupporting)
        state.currencyAllocations.forEach { AllocationBar(it.currency.name, it.percent, "${state.currency} ${PortfolioFormat.amount(it.reportingValue)}") }
    }
}

@Composable
private fun AllocationBar(label: String, percent: String?, detail: String?) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val text = percent?.let { "${PortfolioFormat.amount(it)}%" } ?: "—"
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            Text(label, Modifier.weight(1f), style = typography.small, color = colors.textBody)
            Text(text, style = typography.numberLabelStrong, color = colors.textValue)
            detail?.let { Text(it, style = typography.numberLabel, color = colors.textSupporting) }
        }
        Box(Modifier.fillMaxWidth().height(StockStepsTheme.dimensions.rangeBar).clip(StockStepsTheme.shapes.pill).background(colors.surfaceSecondary)) {
            Box(Modifier.fillMaxWidth(PortfolioPresentation.allocationFraction(percent)).fillMaxHeight().clip(StockStepsTheme.shapes.pill).background(colors.primary))
        }
    }
}

/** Explanations moved out of the dashboard: daily return, missing values, history, cash and allocation rules. */
@Composable
private fun PortfolioAboutDialog(state: PortfolioUiState, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("About these numbers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
                listOfNotNull(
                    state.dailyNotice.takeIf { it.isNotBlank() },
                    state.notice,
                    state.historyNotice,
                    "Cash is included in account value. A negative cash balance represents unfunded or borrowed cash; record deposits separately from existing investments.",
                    "Borrowed cash can make a holding exceed 100%. Sector and ETF look-through allocations are not estimated."
                ).forEach { Text(it, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody) }
            }
        })
}
