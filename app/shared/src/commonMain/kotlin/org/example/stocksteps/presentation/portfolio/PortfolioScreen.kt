package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.graphicsLayer
import org.example.stocksteps.theme.StockAllocationLayout
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
    onPractice: (() -> Unit)? = null,
    /** Phase D2: opens Transaction history (scoped to [holdingSymbol] on Holding details); null keeps the preview without a link. */
    onTransactions: (() -> Unit)? = null,
    /** Phase D3: opens Transaction details for a row; null keeps rows without the tap. */
    onOpenTransaction: ((String) -> Unit)? = null,
    /** Phase D3: shared "already announced" tracker for confirmed adds / edits / deletes; null shows no feedback. */
    feedback: PortfolioFeedbackTracker? = null
) {
    var accountEditor by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<PortfolioAccount?>(null) }
    var deletingAccount by remember { mutableStateOf<PortfolioAccount?>(null) }
    var deletingTransaction by remember { mutableStateOf<PortfolioTransaction?>(null) }
    var editingTransaction by remember { mutableStateOf<PortfolioTransaction?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    // The period the user chose here (null = none yet): history stays on demand, so the card can tell "not requested" from "no values".
    var requestedPeriod by rememberSaveable { mutableStateOf<String?>(null) }
    var sampleDetails by rememberSaveable { mutableStateOf(false) }
    var choosingScenario by remember { mutableStateOf(false) }
    // Phase C: display-only holdings order (null = the presenter's order); not persisted.
    var holdingSort by rememberSaveable { mutableStateOf<String?>(null) }
    val sort = holdingSort?.let { name -> PortfolioHoldingSort.entries.firstOrNull { it.name == name } }
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val activeAccounts = state.accounts.filterNot { it.archived }
    val selectedAccount = activeAccounts.firstOrNull { it.id == state.selectedAccountId }
    val listMode = holdingSymbol == null
    // D3: the confirmation sits above the list (an empty list item would still take a gap), like the iOS top inset.
    Column(modifier) {
        feedback?.let { PortfolioActionBanner(it, state.actionResult, Modifier.padding(start = spacing.screen, end = spacing.screen, top = spacing.sm)) } // nonblocking, auto-hides
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            // C.1: on Holding details the top bar already says "Holding details"; a second large title repeated it.
            if (listMode) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(PortfolioDisplay.TITLE, Modifier.weight(1f).semantics { heading() },
                        style = typography.screenTitle, color = colors.textTitle)
                    run {
                        IconButton(onClick = { showAbout = true }) {
                            Icon(StockIcons.Info, contentDescription = PortfolioDisplay.ABOUT_TITLE, tint = colors.iconSecondary)
                        }
                        PortfolioOverflowMenu(
                            account = selectedAccount, busy = state.busy, onRefresh = onRefresh,
                            onSettings = { editingAccount = it; accountEditor = true }, onDelete = { deletingAccount = it },
                            onChooseScenario = if (state.mockAvailable) ({ choosingScenario = true }) else null)
                    }
                }
            }
            if (listMode && onPractice != null) item {
                StockSegmentedControl(
                    options = listOf(StockSegment(PortfolioMode.MINE, "My Portfolio", StockIcons.Briefcase),
                        StockSegment(PortfolioMode.PRACTICE, "Practice", StockIcons.School)),
                    selected = PortfolioMode.MINE,
                    onSelect = { if (it == PortfolioMode.PRACTICE) onPractice() },
                    compactIcons = true
                )
            }
            // Status: sample, loading, sync errors, offline and sign-in — compact, above the numbers they qualify. The sample scenario is chosen in
            // the ⋯ menu (MOCK builds only); while one is shown, this read-only banner stays visible (never collapsed).
            // B.1: one short line; the full explanation is one tap away (never collapsed away entirely).
            if (state.mockScenario != null) item {
                StockBanner(StockBannerKind.SAMPLE, if (sampleDetails) "${PortfolioDisplay.SAMPLE_SHORT} ${PortfolioDisplay.SAMPLE_BANNER}" else PortfolioDisplay.SAMPLE_SHORT,
                    actionText = if (sampleDetails) PortfolioDisplay.SAMPLE_HIDE else PortfolioDisplay.SAMPLE_DETAILS, onAction = { sampleDetails = !sampleDetails })
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            // One banner for sync problems: offline (cached data) and/or the error, with Retry — never stale data presented as current.
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
            if (activeAccounts.isEmpty()) {
                item {
                    StockEmptyState(
                        message = PortfolioDisplay.EMPTY_MESSAGE,
                        title = PortfolioDisplay.EMPTY_TITLE, icon = StockIcons.PieChart,
                        actionText = if (state.signedIn && !state.busy) PortfolioDisplay.EMPTY_ACTION else null,
                        onAction = { editingAccount = null; accountEditor = true })
                }
            } else {
                if (listMode) {
                    item {
                        PortfolioOverviewCard(state, activeAccounts.size, requestedPeriod,
                            onPeriod = { requestedPeriod = it; onHistory(it) }, onRetry = onRefresh) {
                            PortfolioAccountSelector(activeAccounts, selectedAccount, onSelect,
                                onAddAccount = { editingAccount = null; accountEditor = true }, addEnabled = state.signedIn && !state.busy)
                        }
                    }
                    // Outside the overview so the total stays next to its history.
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            StockButton("+ Add transaction", onAdd, Modifier.fillMaxWidth(), enabled = !state.busy)
                            // Samples are read-only (the presenter rejects changes); say why the action is off instead of a silent grey button.
                            if (state.mockScenario != null) Text(PortfolioDisplay.ADD_DISABLED_SAMPLE, style = typography.caption, color = colors.textSupporting)
                        }
                    }
                }
                val items = PortfolioHoldingsDisplay.items(state, if (listMode) sort else null).filter { holdingSymbol == null || it.symbol == holdingSymbol }
                if (listMode) item {
                    // Phase C: the sort menu sits beside the title (display order only) instead of a toolbar.
                    val title = if (items.isNotEmpty()) "Holdings (${items.size})" else "Holdings"
                    // C.1: at large text the sort menu goes under the title ("Holdings / (2)" wrapped beside it at 2.0×).
                    if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale) {
                        Column { PortfolioSectionTitle(title); if (items.size > 1) PortfolioHoldingSortMenu(sort) { holdingSort = it?.name } }
                    } else Row(verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f)) { PortfolioSectionTitle(title) }
                        if (items.size > 1) PortfolioHoldingSortMenu(sort) { holdingSort = it?.name }
                    }
                }
                if (items.isEmpty()) item {
                    StockCard(bordered = false) { Text(PortfolioDisplay.HOLDINGS_EMPTY, style = typography.small, color = colors.textSupporting) }
                } else item {
                    StockCard(bordered = false, contentPadding = PaddingValues(vertical = spacing.xs)) {
                        items.forEachIndexed { index, item ->
                            if (index > 0) StockDivider(Modifier.padding(horizontal = spacing.cardPadding))
                            PortfolioHoldingRowView(item, onClick = if (listMode) ({ onHolding(item.symbol) }) else null)
                        }
                    }
                }
                // Holding details (Phase C): every holding metric with its currency in a responsive grid, then the actions as navigation rows.
                if (!listMode) state.holdings.firstOrNull { it.symbol == holdingSymbol }?.let { row -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        StockCard(bordered = false) {
                            StockMetricGrid(PortfolioHoldingsDisplay.detailMetrics(row).map { StockMetricItem(it.label, it.value, direction = it.direction) },
                                maxColumns = 2, rowsWhenNarrow = true)
                        }
                        PortfolioSectionTitle(PortfolioHoldingsDisplay.DETAILS_ACTIONS)
                        StockCard(bordered = false, contentPadding = PaddingValues(vertical = spacing.xxs)) {
                            StockNavigationRow(PortfolioHoldingsDisplay.COMPANY_DETAILS, { onCompany(row.symbol) }, icon = StockIcons.Document)
                            StockDivider(Modifier.padding(horizontal = spacing.cardPadding))
                            StockNavigationRow(PortfolioHoldingsDisplay.PRICE_ALERTS, { onAlerts(row.symbol) }, icon = StockIcons.Bell)
                            StockDivider(Modifier.padding(horizontal = spacing.cardPadding))
                            StockNavigationRow(PortfolioHoldingsDisplay.ADD_TO_WATCHLIST, { onWatchlist(row.symbol) }, icon = StockIcons.StarOutline)
                        }
                    }
                } }
                if (listMode) item { PortfolioAllocationCard(state) }
                // Cash and dividends share one card: each is a labelled line, so empty values cost one row, not a whole section. Account-wide, so
                // not on Holding details (Phase C: it isn't this holding's cash).
                if (listMode) item {
                    PortfolioSectionTitle("Cash and dividends")
                    StockCard(bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        PortfolioValueLines("Cash", state.cash, "None recorded")
                        StockDivider()
                        PortfolioValueLines("Dividends received", state.dividends, "None recorded")
                    }
                }
                val transactions = state.transactions.filter { holdingSymbol == null || it.instrument?.symbol == holdingSymbol }
                item { PortfolioSectionTitle(if (transactions.isEmpty()) "Transactions" else "Transactions (${transactions.size})") }
                item {
                    StockCard(bordered = false, contentPadding = PaddingValues(vertical = spacing.xs)) {
                        if (transactions.isEmpty()) {
                            Text(PortfolioDisplay.TRANSACTIONS_EMPTY, Modifier.padding(horizontal = spacing.cardPadding, vertical = spacing.sm), style = typography.small, color = colors.textSupporting)
                        }
                        // D2: a short preview; the full list is Transaction history (the inline "Show all" toggle grew this page without limit).
                        val readOnly = PortfolioFormRules.readOnly(state)
                        transactions.take(RECENT_TRANSACTIONS).forEachIndexed { index, transaction ->
                            if (index > 0) StockDivider(Modifier.padding(horizontal = spacing.cardPadding)) // C.1 closure: inset like the holdings rows
                            PortfolioTransactionRow(transaction, state.logoFor(transaction), onOpen = onOpenTransaction?.let { open -> { open(transaction.id) } },
                                onEdit = { editingTransaction = transaction }, onDelete = if (readOnly) null else ({ deletingTransaction = transaction }))
                        }
                        // D2 owner decision: the link is shown whenever there is at least one transaction (one consistent entry point).
                        if (transactions.isNotEmpty()) onTransactions?.let {
                            StockButton(PortfolioTransactionsDisplay.seeAll(transactions.size), it, Modifier.padding(horizontal = spacing.sm),
                                variant = StockButtonVariant.TEXT)
                        }
                    }
                }
                if (listMode) item {
                    StockCard(onClick = onInsights, onClickLabel = PortfolioDisplay.INSIGHTS_OPEN, bordered = false) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                            StockIconTile(StockIcons.TrendingUp, colors.primaryContainer, colors.primaryText)
                            Column(Modifier.weight(1f)) {
                                Text(PortfolioDisplay.INSIGHTS_TITLE, style = typography.bodySemiBold, color = colors.textTitle)
                                Text(PortfolioDisplay.INSIGHTS_SUBTITLE, style = typography.small, color = colors.textSupporting)
                            }
                            Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.iconSecondary)
                        }
                    }
                }
            }
        }
    }
    if (showAbout) PortfolioAboutDialog(state) { showAbout = false }
    if (choosingScenario) PortfolioScenarioSheet(state.mockScenario ?: SAVED_ACCOUNTS, onDismiss = { choosingScenario = false }) {
        choosingScenario = false
        onScenario(it.takeUnless { it == SAVED_ACCOUNTS })
    }
    if (accountEditor) PortfolioAccountDialog(editingAccount, onDismiss = { accountEditor = false }, onSave = { onSaveAccount(it); accountEditor = false })
    deletingAccount?.let { account ->
        AlertDialog(onDismissRequest = { deletingAccount = null }, title = { Text(PortfolioDisplay.deleteAccountTitle(account.name)) },
            text = { Text(PortfolioDisplay.DELETE_ACCOUNT_MESSAGE) },
            confirmButton = { TextButton(onClick = { onDeleteAccount(account.id); deletingAccount = null }) { Text(PortfolioDisplay.DELETE, color = StockStepsTheme.colors.negativeText) } },
            dismissButton = { TextButton(onClick = { deletingAccount = null }) { Text(PortfolioDisplay.FORM_CANCEL) } })
    }
    deletingTransaction?.let { transaction ->
        PortfolioDeleteTransactionDialog(transaction, onConfirm = { onDeleteTransaction(transaction.id); deletingTransaction = null }, onDismiss = { deletingTransaction = null })
    }
    editingTransaction?.let { transaction -> PortfolioEditSheet(state, transaction, onSaveTransaction) { editingTransaction = null } }
}

private const val RECENT_TRANSACTIONS = 5
private const val SAVED_ACCOUNTS = PortfolioDisplay.SAVED_ACCOUNTS

/** "Cash      CAD 1,200.00" — label left, one value per line right; [empty] when there is none (never an invented 0.00). */
@Composable
private fun PortfolioValueLines(label: String, values: List<String>, empty: String) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // C.1: value under the label when they can't share a line (shared StockLayout.metricRowStacked) — "Dividends / received" at 2.0×.
    BoxWithConstraints(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        val word = label.split(' ').maxOf { measurer.measure(it, typography.small).size.width }
        val value = (values.ifEmpty { listOf(empty) }).maxOf { measurer.measure(it, typography.numberLabelStrong).size.width }
        val stacked = with(density) { org.example.stocksteps.theme.StockLayout.metricRowStacked(word.toDp().value, value.toDp().value, maxWidth.value) }
        val lines: @Composable (Alignment.Horizontal) -> Unit = { align ->
            Column(horizontalAlignment = align) {
                if (values.isEmpty()) Text(empty, style = typography.small, color = colors.textMeta)
                values.forEach { Text(it, style = typography.numberLabelStrong, color = colors.textValue) }
            }
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            Text(label, style = typography.small, color = colors.textSupporting); lines(Alignment.Start)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.md)) {
            Text(label, Modifier.weight(1f), style = typography.small, color = colors.textSupporting); lines(Alignment.End)
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
private fun PortfolioOverflowMenu(account: PortfolioAccount?, busy: Boolean, onRefresh: () -> Unit, onSettings: (PortfolioAccount) -> Unit, onDelete: (PortfolioAccount) -> Unit,
                                  onChooseScenario: (() -> Unit)? = null) {
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
            // MOCK only: one entry opening the grouped scenario sheet (B.1 — the inline list ran past the screen).
            if (onChooseScenario != null) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text(PortfolioDisplay.SCENARIO_MENU) }, onClick = { open = false; onChooseScenario() })
            }
        }
    }
}

/** "Personal · CAD ▾" with a menu of accounts (name, type, currency) and "+ Add". */
@Composable
internal fun PortfolioAccountSelector(accounts: List<PortfolioAccount>, selected: PortfolioAccount?, onSelect: (String) -> Unit,
                                      onAddAccount: (() -> Unit)?, addEnabled: Boolean) {
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
        // D2: Transaction history reuses the selector without "+ Add" (accounts are created from the dashboard).
        onAddAccount?.let { StockButton("+ Add", it, variant = StockButtonVariant.SECONDARY, enabled = addEnabled) }
    }
}

/**
 * Overview card (Portfolio Phase B): account context → total value (largest, wraps instead of shrinking) → today's change or "unavailable ·
 * Why?" → all-accounts line → account value history → Invested / Unrealized / Realized with currencies → one freshness line and the caution
 * notice. All text comes from [PortfolioOverviewDisplay]; nothing is computed here.
 */
@Composable
private fun PortfolioOverviewCard(state: PortfolioUiState, accountCount: Int, requested: String?, onPeriod: (String) -> Unit, onRetry: () -> Unit,
                                  account: @Composable () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val overview = PortfolioOverviewDisplay.of(state, accountCount)
    // Phase C: borderless — the surface colour against the page background and whitespace separate it (no shadow added).
    StockCard(bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        account()
        // One spoken summary (total, today's change in words, all accounts) when there is no "Why?" action to reach; otherwise the total alone.
        val summary = if (overview.today != null) Modifier.clearAndSetSemantics { contentDescription = overview.spoken } else Modifier
        Column(summary, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                StockTitleWithBadge(title = { Text(overview.totalLabel, style = typography.label, color = colors.textSupporting) },
                    badge = { overview.sampleBadge?.let { StockStatusBadge(it, StockBadgeKind.SAMPLE, size = StockBadgeSize.COMPACT) } })
                Text(overview.total, style = typography.largeNumber, color = colors.textValue)
                overview.sampleScenario?.let { Text(it, style = typography.caption, color = colors.textSupporting) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                val today = overview.today
                if (today != null) {
                    Text(PortfolioOverviewDisplay.TODAY_LABEL, style = typography.label, color = colors.textSupporting)
                    StockPriceChange(today, overview.todayDirection, style = typography.numberMedium, maxLines = 2)
                } else {
                    // The reason stays one tap away instead of adding two lines to every view of the card.
                    var why by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(PortfolioPresentation.TODAY_UNAVAILABLE, Modifier.weight(1f, fill = false), style = typography.small, color = colors.textSupporting)
                        if (state.dailyNotice.isNotBlank()) {
                            Text(if (why) "Hide" else "Why?", Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) { why = !why },
                                style = typography.label, color = colors.primaryText)
                        }
                    }
                    if (why) Text(state.dailyNotice, style = typography.caption, color = colors.textMeta)
                }
            }
            overview.allAccounts?.let { Text(it, style = typography.small, color = colors.textSupporting) }
        }
        StockDivider()
        PortfolioHistorySection(state, requested, onPeriod, onRetry)
        StockDivider()
        StockMetricGrid(overview.metrics.map { StockMetricItem(it.label, it.value, direction = it.direction) }, rowsWhenNarrow = true)
        overview.notice?.let { Text(it, style = typography.caption, color = colors.cautionText) }
        overview.freshness?.let { Text(it, style = typography.caption, color = colors.textMeta) }
    }
}

/**
 * Holding row (Phase C): avatar · name / "AAPL · NASDAQ · 10 shares" / optional weight — market value (strong) over the signed change (arrow +
 * sign + colour, percentage when known). One spoken description. Narrow rows and large text put the values under the name (shared
 * `PortfolioLayout.stackedRowWidth`), so names wrap on whole words and values are never squeezed or cut.
 */
@Composable
private fun PortfolioHoldingRowView(item: PortfolioHoldingItem, onClick: (() -> Unit)?) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale
    val values: @Composable (Alignment.Horizontal) -> Unit = { alignment ->
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(item.value, style = typography.numberLabelStrong, color = colors.textValue)
            item.change?.let { StockPriceChange(it, item.direction, maxLines = 2) }
                ?: Text(PortfolioHoldingsDisplay.GAIN_UNAVAILABLE, style = typography.caption, color = colors.textMeta)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val stacked = largeText || maxWidth < PortfolioLayout.stackedRowWidth.dp
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.rowMinHeight)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Open holding", onClick = onClick) else Modifier)
        .semantics { contentDescription = item.spoken }
        .padding(horizontal = spacing.cardPadding, vertical = spacing.sm)
    val details: @Composable () -> Unit = {
        Text(item.subtitle, style = typography.small, color = colors.textSupporting)
        item.weight?.let { Text(it, style = typography.caption, color = colors.textMeta) }
        if (item.stale) Text(PortfolioHoldingsDisplay.STALE, style = typography.caption, color = colors.textMeta)
    }
    if (stacked) {
        // C.1: structured vertical layout — avatar beside the name only; ticker, weight and values use the full row width below.
        Column(rowModifier.clearAndSetSemantics { contentDescription = item.spoken }, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                StockTickerAvatar(symbol = item.symbol, logoUrl = item.logoUrl)
                Text(item.name, Modifier.weight(1f), style = typography.bodySemiBold, color = colors.textTitle)
            }
            details()
            values(Alignment.Start)
        }
    } else Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(Modifier.weight(1f).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            StockTickerAvatar(symbol = item.symbol, logoUrl = item.logoUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(item.name, style = typography.bodySemiBold, color = colors.textTitle)
                details()
            }
        }
        values(Alignment.End)
    }
    }
}

/** "Sort: Value ▾" — Default / Value / Name / Change %; display order only, nothing saved. */
@Composable
private fun PortfolioHoldingSortMenu(sort: PortfolioHoldingSort?, onSort: (PortfolioHoldingSort?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = StockStepsTheme.colors
    Box {
        Row(Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).clip(StockStepsTheme.shapes.chip)
            .clickable(role = Role.DropdownList, onClickLabel = PortfolioHoldingsDisplay.SORT_TITLE) { open = true }
            .padding(horizontal = StockStepsTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            Text(PortfolioHoldingsDisplay.sortLabel(sort), style = StockStepsTheme.typography.label, color = colors.primaryText)
            Icon(StockIcons.ChevronDown, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf<PortfolioHoldingSort?>(null) + PortfolioHoldingSort.entries).forEach { option ->
                DropdownMenuItem(text = { Text(option?.label ?: PortfolioHoldingsDisplay.DEFAULT_ORDER) }, onClick = { open = false; onSort(option) },
                    trailingIcon = if (option == sort) ({ Icon(StockIcons.Check, contentDescription = "Selected", tint = colors.primaryText) }) else null)
            }
        }
    }
}

/**
 * Allocation (Phase C): by holding as a donut + legend when every slice is valid (`PortfolioAllocationDisplay` / `allocationDonutAllowed`), else
 * the labelled bars; by currency as a second legend group, collapsed by default. Percentages and amounts are the server's, never recomputed.
 */
@Composable
private fun PortfolioAllocationCard(state: PortfolioUiState) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val view = PortfolioAllocationDisplay.of(state)
    var byCurrency by rememberSaveable { mutableStateOf(false) }
    PortfolioSectionTitle(PortfolioAllocationDisplay.TITLE)
    StockCard(bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (view.holdings.isEmpty() && view.currencies.isEmpty()) {
            Text(PortfolioAllocationDisplay.EMPTY, style = typography.small, color = colors.textSupporting)
        }
        if (view.holdings.isNotEmpty()) {
            Text(PortfolioAllocationDisplay.BY_HOLDING, style = typography.label, color = colors.textSupporting)
            if (view.donut) {
                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                val density = androidx.compose.ui.platform.LocalDensity.current
                val widest = view.holdings.maxOf { e ->
                    measurer.measure(e.label, typography.small).size.width + measurer.measure(e.percent, typography.numberLabelStrong).size.width
                }.let { with(density) { it.toDp().value } } + 40f
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val diameter = StockAllocationLayout.diameter(maxWidth.value).dp
                    val beside = StockAllocationLayout.legendBeside(maxWidth.value, widest, spacing.md.value)
                    val legendWidth = if (beside) maxWidth.value - diameter.value - spacing.md.value else maxWidth.value
                    val groupStacked = allocationLegendStacked(view.holdings.map { Triple(it.label, it.percent, it.amount) }, legendWidth)
                    val legend: @Composable (Modifier) -> Unit = { m ->
                        Column(m, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            view.holdings.forEach { StockAllocationLegendRow(it.colorIndex, it.label, it.percent, it.amount, it.spoken, stacked = groupStacked) }
                        }
                    }
                    val donut: @Composable () -> Unit = {
                        StockAllocationDonut(view.holdings.map { it.fraction }, view.holdings.map { it.colorIndex }, diameter)
                    }
                    if (beside) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                            donut(); legend(Modifier.weight(1f))
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                            donut(); legend(Modifier.fillMaxWidth())
                        }
                    }
                }
            } else {
                view.holdings.forEach { AllocationBar(it.label, it.percent, it.amount, it.fraction, it.spoken) }
            }
        }
        if (view.currencies.isNotEmpty()) {
            if (view.holdings.isNotEmpty()) StockDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
                .clickable(role = Role.Button, onClickLabel = if (byCurrency) PortfolioAllocationDisplay.HIDE_CURRENCY else PortfolioAllocationDisplay.SHOW_CURRENCY) {
                    byCurrency = !byCurrency
                }.semantics { stateDescription = if (byCurrency) "Expanded" else "Collapsed" },
                verticalAlignment = Alignment.CenterVertically) {
                Text("${PortfolioAllocationDisplay.BY_CURRENCY} (${view.currencies.size})", Modifier.weight(1f).semantics { heading() },
                    style = typography.label, color = colors.textSupporting)
                Icon(StockIcons.ChevronDown, contentDescription = null, tint = colors.iconSecondary,
                    modifier = Modifier.size(StockStepsTheme.dimensions.icon).graphicsLayer { rotationZ = if (byCurrency) 180f else 0f })
            }
            if (byCurrency) BoxWithConstraints(Modifier.fillMaxWidth()) {
                val currencyStacked = allocationLegendStacked(view.currencies.map { Triple(it.label, it.percent, it.amount) }, maxWidth.value)
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    view.currencies.forEach { StockAllocationLegendRow(it.colorIndex, it.label, it.percent, it.amount, it.spoken, stacked = currencyStacked) }
                }
            }
        }
    }
}

/** Fallback bar when the donut isn't allowed (borrowed cash can exceed 100 %): label · reported % · amount, bar clamped to 0…1. */
@Composable
private fun AllocationBar(label: String, percent: String, detail: String?, fraction: Float, spoken: String) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            Text(label, Modifier.weight(1f), style = typography.small, color = colors.textBody)
            Text(percent, style = typography.numberLabelStrong, color = colors.textValue)
            detail?.let { Text(it, style = typography.numberLabel, color = colors.textSupporting) }
        }
        Box(Modifier.fillMaxWidth().height(StockStepsTheme.dimensions.rangeBar).clip(StockStepsTheme.shapes.pill).background(colors.surfaceSecondary)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(StockStepsTheme.shapes.pill).background(colors.primary))
        }
    }
}

/** Explanations moved out of the dashboard: daily return, missing values, history, cash and allocation rules. */
@Composable
private fun PortfolioAboutDialog(state: PortfolioUiState, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text(PortfolioDisplay.ABOUT_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
                listOfNotNull(
                    state.dailyNotice.takeIf { it.isNotBlank() },
                    state.notice,
                    state.historyNotice,
                    PortfolioDisplay.ABOUT_CASH,
                    PortfolioDisplay.ABOUT_ALLOCATION
                ).forEach { Text(it, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody) }
            }
        })
}

/**
 * MOCK sample scenarios grouped by what they show (Portfolio B.1, `PortfolioScenarioGroups`), opened from the ⋯ menu. The active one is
 * checked and announced as selected; choosing one closes the sheet and calls the same presenter action as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortfolioScenarioSheet(selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xxl)) {
            item {
                Column(Modifier.padding(bottom = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Text(PortfolioDisplay.SAMPLE_SCENARIO, Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textTitle)
                    Text(PortfolioDisplay.SCENARIO_NOTE, style = typography.small, color = colors.textSupporting)
                }
            }
            PortfolioScenarioGroups.groups(PortfolioFixtureCatalog.ids).forEach { group ->
                item {
                    Text(group.title, Modifier.padding(top = spacing.md, bottom = spacing.xxs).semantics { heading() }, style = typography.label,
                        color = colors.textSupporting)
                }
                items(group.ids) { id ->
                    val active = id == selected
                    Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
                        .selectable(active, role = Role.RadioButton) { onSelect(id) }.padding(vertical = spacing.xs),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(PortfolioDisplay.scenarioLabel(id), Modifier.weight(1f), style = if (active) typography.bodySemiBold else typography.body,
                            color = if (active) colors.primaryText else colors.textBody)
                        if (active) Icon(StockIcons.Check, contentDescription = null, tint = colors.primaryText)
                    }
                }
            }
        }
    }
}
