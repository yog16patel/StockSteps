package org.example.stocksteps.presentation.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.StockBadgeKind
import org.example.stocksteps.practice.*

/** Navigation and actions from Practice; the Screen never resolves dependencies. */
internal sealed interface PracticeAction {
    data class Tab(val tab: PracticeTab) : PracticeAction
    data class Range(val range: PracticeRange) : PracticeAction
    data class Filter(val type: PracticeTransactionType?) : PracticeAction
    data object Refresh : PracticeAction
    data object BuyStock : PracticeAction
    data object ExploreStocks : PracticeAction
    data object Learn : PracticeAction
    data object SignIn : PracticeAction
    data class Trade(val symbol: String, val side: OrderSide) : PracticeAction
    data class OpenCompany(val symbol: String) : PracticeAction
    data class Locked(val feature: String) : PracticeAction
    data object ShowTrial : PracticeAction
    data object StartTrial : PracticeAction
    data object ShowPlus : PracticeAction
    data object ManagePlan : PracticeAction
    data object ShowReset : PracticeAction
    data object Reset : PracticeAction
    data object DismissSheet : PracticeAction
    data object DismissMessage : PracticeAction
    data class Answer(val challengeId: String, val optionId: String) : PracticeAction
    data class RetryChallenge(val challengeId: String) : PracticeAction
    data class Scenario(val name: String) : PracticeAction
}

internal val MOCK_SCENARIOS = listOf("empty", "one-holding", "free-limit", "eight-holdings-trial-expired", "trial-active", "trial-expiring", "trial-expired",
    "plus-active", "plus-expired", "low-cash", "missing-quote", "stale-quote", "missing-fx", "dividend", "split")

@Composable
internal fun PracticeScreen(state: PracticeUiState, modifier: Modifier, onAction: (PracticeAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var holding by remember { mutableStateOf<PracticeHoldingView?>(null) }
    val overview = state.overview
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)) {
        item("header") { Header(state, onAction) }
        if (!state.signedIn) {
            item("signin") {
                StockEmptyState("Sign in to use your Practice Portfolio. Your simulated investments are saved to your account, so they're there on every device.",
                    actionText = "Sign in", onAction = { onAction(PracticeAction.SignIn) })
            }
            return@LazyColumn
        }
        if (overview == null) {
            item("loading") {
                val error = state.error
                if (error != null) StockErrorState(error, { onAction(PracticeAction.Refresh) })
                else LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading your Practice Portfolio" })
            }
            return@LazyColumn
        }
        if (state.offline) item("offline") {
            Banner("You're offline. This is your last saved Practice Portfolio; trading and plan changes need a connection.", warning = true)
        }
        item("tabs") { StockSegmentedControl(PracticeTab.entries.map { StockSegment(it, it.label) }, state.tab, { onAction(PracticeAction.Tab(it)) }) }
        when (state.tab) {
            PracticeTab.OVERVIEW -> overviewItems(state, overview, onAction) { holding = it }
            PracticeTab.HOLDINGS -> holdingsItems(state, overview, onAction) { holding = it }
            PracticeTab.ACTIVITY -> activityItems(state, overview, onAction)
            PracticeTab.CHALLENGES -> challengeItems(state, overview, onAction)
        }
        item("notices") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                overview.notices.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
            }
        }
    }
    holding?.let { h -> HoldingSheet(h, overview?.baseCurrency ?: "CAD", state.canTrade, onAction) { holding = null } }
    state.sheet?.let { PracticeSheetContent(it, state, onAction) }
    state.message?.let { message ->
        AlertDialog(onDismissRequest = { onAction(PracticeAction.DismissMessage) }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { onAction(PracticeAction.DismissMessage) }) { Text("OK") } })
    }
}

@Composable
private fun Header(state: PracticeUiState, onAction: (PracticeAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    var menu by remember { mutableStateOf(false) }
    var scenarios by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StockTitleWithBadge(Modifier.weight(1f), title = {
                Text("Practice Portfolio", Modifier.semantics { heading() }, style = StockStepsTheme.typography.screenTitle, color = colors.textPrimary)
            }) { SimulatedBadge() }
            if (state.overview != null) Box {
                TextButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)) { Text("More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Reset practice portfolio") }, { menu = false; onAction(PracticeAction.ShowReset) }, enabled = state.canTrade)
                    DropdownMenuItem({ Text("About StockSteps+") }, { menu = false; onAction(PracticeAction.ShowPlus) })
                    if (state.overview?.sampleData == true) DropdownMenuItem({ Text("Sample scenarios (mock)") }, { menu = false; scenarios = true })
                }
                DropdownMenu(scenarios, { scenarios = false }) {
                    MOCK_SCENARIOS.forEach { name -> DropdownMenuItem({ Text(name) }, { scenarios = false; onAction(PracticeAction.Scenario(name)) }) }
                }
            }
        }
        Text("Learn investing without real money.", style = StockStepsTheme.typography.body, color = colors.textBody)
        state.accessLabel?.let { label ->
            val trial = state.entitlement?.access == PracticeAccess.TRIAL
            Text(label, Modifier.clip(StockStepsTheme.shapes.pill).background(colors.surfaceSecondary).padding(horizontal = spacing.sm, vertical = spacing.xxs),
                style = StockStepsTheme.typography.caption, color = if (trial) colors.primaryText else colors.textSecondary)
        }
    }
}

@Composable
internal fun SimulatedBadge() {
    // Sample/MOCK disclosure badge (Phase 2): one line, never split mid-word ("SIMU-LATED" before).
    StockStatusBadge("SIMULATED", StockBadgeKind.SAMPLE, size = StockBadgeSize.COMPACT, contentDescription = "Simulated with virtual money")
}

@Composable
private fun Banner(text: String, warning: Boolean = false) {
    Text(text, Modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).background(if (warning) StockStepsTheme.colors.warningContainer else StockStepsTheme.colors.educationContainer)
        .padding(StockStepsTheme.spacing.sm), style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textPrimary)
}

/** Gain/loss in words and sign, with colour only as a supplement. */
@Composable
internal fun GainText(value: String?, percent: String?, currency: String, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val positive = PracticeFormat.positive(value)
    val text = if (value == null) "Unavailable" else "${PracticeFormat.direction(value)} ${PracticeFormat.signedMoney(value, currency)}" + (percent?.let { " (${PracticeFormat.percent(it)})" } ?: "")
    Text(text, modifier, style = StockStepsTheme.typography.bodySemiBold, color = when (positive) { true -> colors.positiveText; false -> colors.negativeText; null -> colors.textSecondary })
}

// ---------- Overview ----------

private fun androidx.compose.foundation.lazy.LazyListScope.overviewItems(state: PracticeUiState, o: PracticeOverview, onAction: (PracticeAction) -> Unit, onHolding: (PracticeHoldingView) -> Unit) {
    item("summary") {
        val colors = StockStepsTheme.colors
        val typography = StockStepsTheme.typography
        StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            Text("Portfolio value", style = typography.label, color = colors.textSecondary)
            Text(PracticeFormat.money(o.totalValue, o.baseCurrency), Modifier.semantics { contentDescription = "Portfolio value ${PracticeFormat.money(o.totalValue, o.baseCurrency)}" },
                style = typography.largeNumber, color = colors.textPrimary)
            GainText(o.totalGain, o.totalGainPercent, o.baseCurrency)
            Text("Since you started with ${PracticeFormat.money(o.startingCash, o.baseCurrency)} of virtual cash.", style = typography.caption, color = colors.textSecondary)
            PerformanceSection(state, o, onAction)
        }
    }
    item("secondary") {
        Row(horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            Metric("Available cash", PracticeFormat.money(o.cash, o.baseCurrency), Modifier.weight(1f))
            Metric("Open holdings", o.entitlement.maxOpenHoldings?.let { "${o.openHoldings} of $it" } ?: "${o.openHoldings}", Modifier.weight(1f))
        }
    }
    item("actions") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            StockButton("Buy Stock", { onAction(PracticeAction.BuyStock) }, enabled = state.canTrade)
            StockButton("Explore Stocks", { onAction(PracticeAction.ExploreStocks) }, variant = StockButtonVariant.OUTLINED)
            StockButton("Learn", { onAction(PracticeAction.Learn) }, variant = StockButtonVariant.OUTLINED)
        }
    }
    if (o.holdings.isEmpty()) item("empty") {
        StockCard(Modifier.fillMaxWidth(), containerColor = StockStepsTheme.colors.educationContainer, bordered = false,
            verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            Text("Your practice portfolio is ready.", Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle)
            Text("You have ${PracticeFormat.money(o.cash, o.baseCurrency)} in virtual cash. Explore stocks or ETFs, research a company, then try a simulated purchase. No real money is used.",
                style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody)
        }
    } else {
        item("holdings-title") { SectionTitle("Holdings", "See all") { onAction(PracticeAction.Tab(PracticeTab.HOLDINGS)) } }
        items(o.holdings.take(3), key = { "preview-${it.instrument.id}" }) { HoldingRow(it, o.baseCurrency) { onHolding(it) } }
    }
    item("insights") { InsightsCard(o, onAction) }
    item("explain") {
        Text("Portfolio value includes both your virtual cash and the current value of your simulated investments. Gain or loss compares it with the virtual cash you started with.",
            style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    StockCard(modifier, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Text(label, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
        Text(value, style = StockStepsTheme.typography.bodySemiBold, color = StockStepsTheme.colors.textPrimary)
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)
        action?.let { TextButton(onClick = onClick) { Text(it) } }
    }
}

@Composable
private fun PerformanceSection(state: PracticeUiState, o: PracticeOverview, onAction: (PracticeAction) -> Unit) {
    val allowed = PracticePolicy.ranges(o.entitlement.access)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        PracticeRange.entries.forEach { range ->
            val locked = range !in allowed
            FilterChip(state.range == range, { onAction(PracticeAction.Range(range)) },
                label = { Text(if (locked) "${range.label} · Locked" else range.label) },
                modifier = Modifier.semantics { if (locked) stateDescription = "Locked. Part of the Practice trial and StockSteps+" })
        }
    }
    val perf = state.performance?.takeIf { it.range == state.range }
    val performanceError = state.performanceError
    when {
        performanceError != null -> Text(performanceError, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
        perf == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
        perf.points.count { it.value != null } < 2 -> Text(perf.notes.lastOrNull() ?: "Not enough history yet.", style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
        else -> {
            val values = perf.points.map { it.value?.toDoubleOrNull() }
            val details = perf.points.map { "${it.date} · ${PracticeFormat.money(it.value, perf.baseCurrency)}" }
            StockTrendChart(values, details, listOf(perf.points.first().date, perf.points.last().date), emptyList(),
                description = "Practice portfolio value, ${perf.range.label}. From ${PracticeFormat.money(perf.startValue, perf.baseCurrency)} to ${PracticeFormat.money(perf.endValue, perf.baseCurrency)}: " +
                    "${PracticeFormat.direction(perf.change)} ${PracticeFormat.signedMoney(perf.change, perf.baseCurrency)}. Missing days are gaps.")
            Text("${perf.range.label}: ${PracticeFormat.direction(perf.change)} ${PracticeFormat.signedMoney(perf.change, perf.baseCurrency)} (${PracticeFormat.percent(perf.changePercent)})",
                style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textPrimary)
            perf.notes.forEach { Text(it, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary) }
        }
    }
}

@Composable
private fun InsightsCard(o: PracticeOverview, onAction: (PracticeAction) -> Unit) {
    val premium = o.entitlement.has(PracticeCapability.PREMIUM_INSIGHTS)
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text("What your portfolio shows", Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle)
        o.insights.forEach { Text("• ${it.text}", style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody) }
        if (!premium) LockedPreview("Detailed insights on concentration, ETFs and sectors are part of the Practice trial and StockSteps+.") { onAction(PracticeAction.Locked("Premium educational insights")) }
        Text("These are observations about your simulated portfolio, not advice.", style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
    }
}

@Composable
private fun LockedPreview(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).border(StockStepsTheme.dimensions.border, StockStepsTheme.colors.border, StockStepsTheme.shapes.card)
        .clickable(onClickLabel = "See what's included", onClick = onClick).padding(StockStepsTheme.spacing.sm).heightIn(min = StockStepsTheme.dimensions.touchTarget),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
        Text("🔒", Modifier.clearAndSetSemantics { })
        Text(text, Modifier.weight(1f), style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)
    }
}

// ---------- Holdings ----------

@Composable
private fun HoldingRow(h: PracticeHoldingView, base: String, onClick: () -> Unit) {
    val typography = StockStepsTheme.typography
    StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = PracticeFormat.holdingDescription(h, base)
    }, onClick = onClick, onClickLabel = "Open ${h.instrument.symbol}") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            StockTickerAvatar(h.instrument.symbol, logoUrl = h.instrument.logoUrl, size = StockStepsTheme.dimensions.logoCompact)
            Column(Modifier.weight(1f)) {
                Text(h.instrument.symbol, style = typography.bodySemiBold, color = StockStepsTheme.colors.textPrimary)
                Text(h.instrument.name ?: "", style = typography.caption, color = StockStepsTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(PracticeFormat.money(h.marketValue, base), style = typography.bodySemiBold, color = StockStepsTheme.colors.textPrimary)
                GainText(h.unrealizedGain, h.unrealizedPercent, base)
            }
        }
        if (h.stale) Text("Price from an earlier session", style = typography.caption, color = StockStepsTheme.colors.cautionText)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.holdingsItems(state: PracticeUiState, o: PracticeOverview, onAction: (PracticeAction) -> Unit, onHolding: (PracticeHoldingView) -> Unit) {
    item("invested") {
        Row(horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            Metric("Invested value", PracticeFormat.money(o.holdingsValue, o.baseCurrency), Modifier.weight(1f))
            Metric("Unrealized", "${PracticeFormat.direction(o.unrealizedGain)} ${PracticeFormat.signedMoney(o.unrealizedGain, o.baseCurrency)}", Modifier.weight(1f))
        }
    }
    if (o.holdings.isEmpty()) item("none") { StockEmptyState("You don't have any practice holdings yet.", actionText = "Buy Stock", onAction = { onAction(PracticeAction.BuyStock) }) }
    items(o.holdings, key = { it.instrument.id }) { HoldingRow(it, o.baseCurrency) { onHolding(it) } }
    item("realized") {
        Text("Realized gain from sales: ${PracticeFormat.signedMoney(o.realizedGain, o.baseCurrency)}" +
            (if (o.dividends != "0") " · Simulated dividends: ${PracticeFormat.money(o.dividends, o.baseCurrency)}" else ""),
            style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
    }
    item("allocation") {
        StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            Text("Allocation", Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle)
            val slices = o.allocation
            if (slices == null) LockedPreview("See how your money is spread across holdings, cash and sectors with the Practice trial or StockSteps+.") { onAction(PracticeAction.Locked("Allocation charts")) }
            else {
                slices.forEach { AllocationBar(it, o.baseCurrency) }
                o.sectorAllocation?.let { sectors ->
                    Text("By sector", style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.textSecondary)
                    sectors.forEach { AllocationBar(it, o.baseCurrency) }
                }
                Text("Spreading money across different companies and industries is called diversification. It can lower the impact of one company, but can't remove risk.",
                    style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun AllocationBar(slice: PracticeAllocationSlice, base: String) {
    val fraction = (slice.percent.toDoubleOrNull() ?: 0.0) / 100
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${slice.label}: ${PracticeFormat.percent(slice.percent).removePrefix("+")}, ${PracticeFormat.money(slice.value, base)}" }) {
        Row {
            Text(slice.label, Modifier.weight(1f), style = StockStepsTheme.typography.small)
            Text("${PracticeFormat.percent(slice.percent).removePrefix("+")} · ${PracticeFormat.money(slice.value, base)}", style = StockStepsTheme.typography.small)
        }
        Box(Modifier.fillMaxWidth().height(StockStepsTheme.spacing.xs).clip(StockStepsTheme.shapes.pill).background(StockStepsTheme.colors.surfaceSecondary)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f)).clip(StockStepsTheme.shapes.pill).background(StockStepsTheme.colors.primary))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoldingSheet(h: PracticeHoldingView, base: String, canTrade: Boolean, onAction: (PracticeAction) -> Unit, onDismiss: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.screen).padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                StockTitleWithBadge(Modifier.weight(1f), title = {
                    Text("${h.instrument.symbol} · ${h.instrument.name ?: ""}", Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle)
                }) { SimulatedBadge() }
            }
            Detail("Shares", PracticeFormat.shares(h.quantity))
            Detail("Average cost", PracticeFormat.price(h.averageCost, base) + " per share")
            Detail("Current price", PracticeFormat.price(h.currentPrice, h.priceCurrency) + (h.quoteAsOf?.let { " · ${PracticeFormat.isoDate(it)}" } ?: ""))
            Detail("Market value", PracticeFormat.money(h.marketValue, base))
            Detail("Unrealized gain/loss", "${PracticeFormat.direction(h.unrealizedGain)} ${PracticeFormat.signedMoney(h.unrealizedGain, base)} (${PracticeFormat.percent(h.unrealizedPercent)})")
            if (h.instrument.currency != base) Detail("Exchange rate", h.fxRate?.let { "1 ${h.instrument.currency} = $it $base" } ?: "Unavailable")
            if (h.stale) Text("This price is from an earlier trading session.", style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.cautionText)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                StockButton("Buy more", { onDismiss(); onAction(PracticeAction.Trade(h.instrument.id, OrderSide.BUY)) }, Modifier.weight(1f), enabled = canTrade)
                StockButton("Sell", { onDismiss(); onAction(PracticeAction.Trade(h.instrument.id, OrderSide.SELL)) }, Modifier.weight(1f), variant = StockButtonVariant.OUTLINED, enabled = canTrade)
            }
            StockButton("Company details", { onDismiss(); onAction(PracticeAction.OpenCompany(h.instrument.id)) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.TEXT)
        }
    }
}

@Composable
internal fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(1f), style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
        Text(value, style = StockStepsTheme.typography.bodySemiBold, color = StockStepsTheme.colors.textPrimary)
    }
}

// ---------- Activity ----------

private fun androidx.compose.foundation.lazy.LazyListScope.activityItems(state: PracticeUiState, o: PracticeOverview, onAction: (PracticeAction) -> Unit) {
    item("filters") {
        val options = listOf<PracticeTransactionType?>(null, PracticeTransactionType.BUY, PracticeTransactionType.SELL, PracticeTransactionType.DIVIDEND)
        StockPillSelector(options, state.filter, { when (it) { null -> "All"; PracticeTransactionType.BUY -> "Buys"; PracticeTransactionType.SELL -> "Sells"; else -> "Dividends" } },
            { onAction(PracticeAction.Filter(it)) })
    }
    val items = state.transactions
    when {
        items == null -> item("loading-tx") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        items.isEmpty() -> item("no-tx") { StockEmptyState("No simulated transactions here yet.") }
        else -> items(items, key = { it.id }) { TransactionRow(it, o.baseCurrency) }
    }
}

@Composable
private fun TransactionRow(t: PracticeTransaction, base: String) {
    val typography = StockStepsTheme.typography
    val label = PracticeFormat.type(t.type)
    val detail = when (t.type) {
        PracticeTransactionType.SPLIT_ADJUSTMENT -> "${t.quantity}-for-1 split"
        PracticeTransactionType.DIVIDEND -> "${PracticeFormat.shareCount(t.quantity)} × ${PracticeFormat.price(t.price, t.priceCurrency)}"
        else -> "${PracticeFormat.shareCount(t.quantity)} at ${PracticeFormat.price(t.price, t.priceCurrency)}"
    }
    StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label, ${t.instrument.symbol}, $detail, total ${PracticeFormat.money(t.amount, base)}, ${PracticeFormat.dateTime(t.executedAt)}. Simulated." }) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("$label · ${t.instrument.symbol}", style = typography.bodySemiBold, color = StockStepsTheme.colors.textPrimary)
                Text(detail, style = typography.caption, color = StockStepsTheme.colors.textSecondary)
                Text("${PracticeFormat.dateTime(t.executedAt)} · Simulated", style = typography.caption, color = StockStepsTheme.colors.textTertiary)
            }
            if (t.type != PracticeTransactionType.SPLIT_ADJUSTMENT) Text((if (t.type == PracticeTransactionType.BUY) "−" else "+") + PracticeFormat.money(t.amount, base),
                style = typography.bodySemiBold, color = StockStepsTheme.colors.textPrimary)
        }
    }
}

// ---------- Challenges ----------

private fun androidx.compose.foundation.lazy.LazyListScope.challengeItems(state: PracticeUiState, o: PracticeOverview, onAction: (PracticeAction) -> Unit) {
    item("challenges-intro") {
        Text("Short guided activities. They're about understanding, never about making a profit.", style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody)
    }
    items(o.challenges, key = { it.id }) { ChallengeCard(it, state.challengeAnswers[it.id], onAction) }
}

@Composable
private fun ChallengeCard(c: PracticeChallengeView, answer: org.example.stocksteps.learning.QuizResult?, onAction: (PracticeAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    var open by remember { mutableStateOf(false) }
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(onClickLabel = if (open) "Collapse" else "Open challenge") { open = !open },
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${c.order}. ${c.title}", Modifier.semantics { heading() }, style = StockStepsTheme.typography.bodySemiBold, color = colors.textPrimary)
                Text(c.summary, style = StockStepsTheme.typography.caption, color = colors.textSecondary)
            }
            Text(when { c.completedAt != null -> "Completed"; !c.available -> "🔒 Preview"; else -> "Start" }, style = StockStepsTheme.typography.label,
                color = if (c.completedAt != null) colors.positiveText else colors.primaryText)
        }
        if (open) {
            c.lesson.forEach { Text(it, style = StockStepsTheme.typography.body, color = colors.textBody) }
            when {
                c.kind == ChallengeKind.FIRST_BUY -> Text(if (c.completedAt != null) "Done: you made your first simulated purchase." else "Completes automatically when you confirm your first practice purchase.",
                    style = StockStepsTheme.typography.small, color = colors.textSecondary)
                !c.available -> LockedPreview("This challenge is part of the Practice trial and StockSteps+. Challenges you complete stay completed.") { onAction(PracticeAction.Locked("Guided practice challenges")) }
                c.quiz != null -> {
                    val quiz = c.quiz!!
                    Text(quiz.question, style = StockStepsTheme.typography.bodySemiBold)
                    quiz.options.forEach { option ->
                        val chosen = answer?.selectedOptionId == option.id
                        OutlinedButton(onClick = { onAction(PracticeAction.Answer(c.id, option.id)) }, enabled = answer == null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).semantics { if (chosen) stateDescription = "Your answer" }) {
                            Text(option.text, Modifier.fillMaxWidth())
                        }
                    }
                    answer?.let { a ->
                        val reflection = c.kind == ChallengeKind.REFLECTION
                        Text(if (reflection) "Thanks for reflecting" else a.feedback, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = StockStepsTheme.typography.bodySemiBold,
                            color = if (reflection || a.correct) colors.positiveText else colors.negativeText)
                        Text(a.explanation, style = StockStepsTheme.typography.small, color = colors.textBody)
                        if (!reflection && !a.correct) TextButton(onClick = { onAction(PracticeAction.RetryChallenge(c.id)) }) { Text("Try again") }
                    }
                }
            }
        }
    }
}

// ---------- Sheets ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticeSheetContent(sheet: PracticeSheet, state: PracticeUiState, onAction: (PracticeAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val dismiss = { onAction(PracticeAction.DismissSheet) }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.screen).padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            when (sheet) {
                is PracticeSheet.HoldingLimit -> Paywall(
                    if (sheet.trialOffered) "Ready to practice with more stocks?" else "Unlock Unlimited Practice",
                    if (sheet.trialOffered) "Your free Practice Portfolio includes three holdings. Start a 14-day trial to explore unlimited virtual investments and additional learning tools."
                    else "Your free Practice Portfolio includes three holdings. StockSteps+ unlocks unlimited virtual investments and premium learning tools. You can always sell or add to the holdings you have.",
                    sheet.trialOffered, onAction)
                is PracticeSheet.Locked -> Paywall("${sheet.feature} is a premium learning tool",
                    if (sheet.trialOffered) "Start a 14-day Practice trial to try it, along with unlimited holdings and guided challenges." else "It's included with StockSteps+.",
                    sheet.trialOffered, onAction)
                PracticeSheet.TrialConfirm -> {
                    Title("Start your 14-day Practice trial")
                    Bullets(listOf("Unlimited practice holdings", "Full portfolio history (3M, 1Y, ALL)", "Allocation charts and detailed insights", "All guided practice challenges"))
                    Text("The trial lasts 14 days from when you start it, timed by StockSteps' servers. You'll see the exact end date as soon as it starts.", style = StockStepsTheme.typography.body)
                    Text("No payment method required. Your trial does not automatically become a paid subscription.", style = StockStepsTheme.typography.bodySemiBold)
                    Text("When it ends you keep every holding and transaction. You can keep selling or adding to them; opening new ones follows the free three-holding limit.",
                        style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
                    Text("The trial is available once per account.", style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
                    StockButton(if (state.busy) "Starting…" else "Start Free Trial", { onAction(PracticeAction.StartTrial) }, Modifier.fillMaxWidth(), enabled = !state.busy && state.canTrade)
                    StockButton("Not now", dismiss, Modifier.fillMaxWidth(), variant = StockButtonVariant.TEXT)
                }
                PracticeSheet.TrialExpired -> {
                    Title("Your Practice trial has ended")
                    Text("Your virtual investments are still here. You can continue tracking and selling holdings. Upgrade to StockSteps+ to unlock unlimited new holdings and premium learning tools.",
                        style = StockStepsTheme.typography.body)
                    StockButton("Explore StockSteps+", { onAction(PracticeAction.ShowPlus) }, Modifier.fillMaxWidth())
                    StockButton("Continue Free", dismiss, Modifier.fillMaxWidth(), variant = StockButtonVariant.TEXT)
                }
                PracticeSheet.PlusInfo -> {
                    Title("StockSteps+")
                    Bullets(listOf("Unlimited Practice Portfolio holdings", "Expanded portfolio analytics and full history", "Guided practice challenges",
                        "Premium educational insights", "AI Stock Research Assistant, subject to usage limits"))
                    val plus = state.entitlement?.access == PracticeAccess.PLUS
                    Text(if (plus) "You have StockSteps+." else "In-app purchase isn't available in this version yet, so prices aren't shown and nothing can be charged. Your plan is read from your account.",
                        style = StockStepsTheme.typography.body)
                    if (state.overview?.sampleData == true) StockButton("Simulate a plan (mock)", { onAction(PracticeAction.ManagePlan) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.OUTLINED)
                    StockButton("Close", dismiss, Modifier.fillMaxWidth(), variant = StockButtonVariant.TEXT)
                }
                PracticeSheet.ResetConfirm -> {
                    Title("Reset your Practice Portfolio?")
                    Text("This archives your simulated holdings and transactions and starts again with ${PracticeFormat.money(state.overview?.startingCash, state.overview?.baseCurrency ?: "CAD")} of virtual cash.",
                        style = StockStepsTheme.typography.body)
                    Text("It doesn't change your plan, trial, account, Learn progress or completed challenges. No real money is involved.", style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
                    StockButton(if (state.busy) "Resetting…" else "Reset practice portfolio", { onAction(PracticeAction.Reset) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.DESTRUCTIVE, enabled = !state.busy && state.canTrade)
                    StockButton("Cancel", dismiss, Modifier.fillMaxWidth(), variant = StockButtonVariant.TEXT)
                }
            }
        }
    }
}

@Composable private fun Title(text: String) = Text(text, Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)
@Composable private fun Bullets(items: List<String>) = Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
    items.forEach { Text("• $it", style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody) }
}

@Composable
private fun Paywall(title: String, body: String, trialOffered: Boolean, onAction: (PracticeAction) -> Unit) {
    Title(title)
    Text(body, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody)
    if (trialOffered) StockButton("Start 14-Day Free Trial", { onAction(PracticeAction.ShowTrial) }, Modifier.fillMaxWidth())
    StockButton("Explore StockSteps+", { onAction(PracticeAction.ShowPlus) }, Modifier.fillMaxWidth(), variant = if (trialOffered) StockButtonVariant.OUTLINED else StockButtonVariant.PRIMARY)
    StockButton(if (trialOffered) "Maybe Later" else "Continue with Free", { onAction(PracticeAction.DismissSheet) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.TEXT)
}

// ---------- Entry points ----------

/** A clearly separate entry to the simulated portfolio (Portfolio, Home, Learn). */
@Composable
internal fun PracticeEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier, title: String = "Practice Portfolio", body: String = "Learn with simulated investments.") {
    StockCard(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$title. $body Simulated with virtual money." },
        onClick = onClick, onClickLabel = "Open Practice Portfolio", containerColor = StockStepsTheme.colors.educationContainer, bordered = false,
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            StockTitleWithBadge(Modifier.weight(1f), title = {
                Text(title, style = StockStepsTheme.typography.cardTitle, color = StockStepsTheme.colors.textPrimary)
            }) { SimulatedBadge() }
        }
        Text(body, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)
    }
}
