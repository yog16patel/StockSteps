package org.example.stocksteps.presentation.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.practice.*

internal sealed interface OrderAction {
    data class Mode(val mode: OrderInput) : OrderAction
    data class Input(val text: String) : OrderAction
    data object Review : OrderAction
    data object Back : OrderAction
    data object Confirm : OrderAction
    data object RefreshPrice : OrderAction
    data object UpgradeOptions : OrderAction
    data object DismissLimit : OrderAction
    data object ViewHoldings : OrderAction
    data object ExploreStocks : OrderAction
    data object ContinueLearning : OrderAction
}

/** Order Entry → Order Review → Confirmation for one simulated trade. */
@Composable
internal fun PracticeOrderScreen(state: PracticeOrderState, modifier: Modifier, onAction: (OrderAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    Column(modifier.background(StockStepsTheme.colors.appBackground).verticalScroll(rememberScrollState()).padding(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        when (state.step) {
            OrderStep.ENTRY -> Entry(state, onAction)
            OrderStep.REVIEW -> Review(state, onAction)
            OrderStep.DONE -> Done(state, onAction)
        }
    }
    if (state.holdingLimit) AlertDialog(onDismissRequest = { onAction(OrderAction.DismissLimit) },
        title = { Text("Ready to practice with more stocks?") },
        text = { Text(state.blocker?.message ?: "Your free Practice Portfolio includes three holdings.") },
        confirmButton = { TextButton(onClick = { onAction(OrderAction.UpgradeOptions) }) { Text("See options") } },
        dismissButton = { TextButton(onClick = { onAction(OrderAction.DismissLimit) }) { Text("Maybe Later") } })
}

@Composable
private fun InstrumentHeader(state: PracticeOrderState) {
    val p = state.preview
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
        StockTickerAvatar(state.symbol, logoUrl = p?.instrument?.logoUrl)
        Column(Modifier.weight(1f)) {
            Text(p?.instrument?.name ?: state.symbol, Modifier.semantics { heading() }, style = StockStepsTheme.typography.screenTitle, color = StockStepsTheme.colors.textPrimary)
            Text(listOfNotNull(state.symbol, p?.instrument?.exchange).joinToString(" · "), style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
        }
        SimulatedBadge()
    }
}

private fun verb(side: OrderSide) = if (side == OrderSide.BUY) "Practice Buy" else "Practice Sell"

@Composable
private fun Entry(state: PracticeOrderState, onAction: (OrderAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val p = state.preview
    InstrumentHeader(state)
    Text(verb(state.side), style = typography.sectionTitle, color = colors.textPrimary)
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        if (p == null) {
            if (state.previewing) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading the latest price" })
            else Text(state.error ?: "Loading the latest price…", style = typography.small)
        } else {
            Detail("Latest usable price", PracticeFormat.price(p.price, p.priceCurrency))
            Text(p.priceBasis + (p.quoteAsOf?.let { " · ${PracticeFormat.isoDate(it)}" } ?: ""), style = typography.caption, color = colors.textSecondary)
            if (p.priceCurrency != p.baseCurrency && p.fxRate != "0") Text("1 ${p.priceCurrency} = ${p.fxRate} ${p.baseCurrency}" + (p.fxAsOf?.let { " (Bank of Canada, $it)" } ?: ""),
                style = typography.caption, color = colors.textSecondary)
            if (state.side == OrderSide.SELL) Detail("You own", PracticeFormat.shareCount(p.ownedQuantity))
        }
    }
    if (state.side == OrderSide.BUY) StockSegmentedControl(listOf(StockSegment(OrderInput.SHARES, "Shares"), StockSegment(OrderInput.AMOUNT, "Amount (${p?.baseCurrency ?: "CAD"})")),
        state.mode, { onAction(OrderAction.Mode(it)) })
    OutlinedTextField(state.input, { onAction(OrderAction.Input(it)) }, Modifier.fillMaxWidth(),
        label = { Text(if (state.mode == OrderInput.SHARES) "Number of shares" else "Virtual cash to invest") },
        supportingText = { Text(if (state.mode == OrderInput.SHARES) "Up to 4 decimal places (fractional shares are simulated)." else "Converted to whole 0.0001 shares at the server's price.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
    if (p != null && p.quantity != "0") StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Detail("Quantity", PracticeFormat.shareCount(p.quantity))
        Detail(if (state.side == OrderSide.BUY) "Estimated cost" else "Estimated proceeds", PracticeFormat.money(p.estimatedTotal, p.baseCurrency))
        Detail("Available virtual cash", PracticeFormat.money(p.cashAvailable, p.baseCurrency))
        Detail("Cash after this trade", PracticeFormat.money(p.cashAfter, p.baseCurrency))
        if (state.side == OrderSide.BUY && p.maxOpenHoldings != null) Detail("Free holding slots",
            if (p.opensNewHolding) "${(p.maxOpenHoldings!! - p.openHoldings).coerceAtLeast(0)} of ${p.maxOpenHoldings} left" else "Adds to a holding you own")
    }
    if (state.previewing && p != null) LinearProgressIndicator(Modifier.fillMaxWidth())
    p?.blocker?.let { b ->
        Text(b.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = typography.body, color = colors.negativeText)
        if (b.code in setOf("QUOTE_STALE", "QUOTE_MISSING", "FX_UNAVAILABLE")) TextButton(onClick = { onAction(OrderAction.RefreshPrice) }) { Text("Check the price again") }
    }
    state.notice?.let { Text(it, style = typography.body, color = colors.cautionText) }
    state.error?.takeIf { p != null }?.let { Text(it, style = typography.body, color = colors.negativeText) }
    Text(PracticePolicy.DISCLOSURE, style = typography.caption, color = colors.textSecondary)
    StockButton("Review order", { onAction(OrderAction.Review) }, Modifier.fillMaxWidth(), enabled = state.canReview || state.holdingLimit || p?.blocker?.code == "HOLDING_LIMIT")
}

@Composable
private fun Review(state: PracticeOrderState, onAction: (OrderAction) -> Unit) {
    val p = state.preview ?: return
    val spacing = StockStepsTheme.spacing
    InstrumentHeader(state)
    Text("Review your ${verb(state.side).lowercase()}", Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle)
    StockCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = "${verb(state.side)} of ${PracticeFormat.shareCount(p.quantity)} of ${state.symbol} at about ${PracticeFormat.price(p.price, p.priceCurrency)}. " +
            "Estimated total ${PracticeFormat.money(p.estimatedTotal, p.baseCurrency)}. Cash remaining ${PracticeFormat.money(p.cashAfter, p.baseCurrency)}. Simulated trade."
    }, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Detail("Instrument", "${p.instrument.name ?: state.symbol} (${state.symbol})")
        Detail("Action", verb(state.side))
        Detail("Quantity", PracticeFormat.shareCount(p.quantity))
        Detail("Indicative price", PracticeFormat.price(p.price, p.priceCurrency))
        Detail("Estimated total", PracticeFormat.money(p.estimatedTotal, p.baseCurrency))
        Detail("Cash remaining", PracticeFormat.money(p.cashAfter, p.baseCurrency))
    }
    Text(PracticePolicy.DISCLOSURE, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)
    Text("The final simulated price is set when you confirm. If it has moved by more than 1%, you'll be asked to review again.",
        style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
    state.error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.negativeText) }
    StockButton(if (state.executing) "Confirming…" else "Confirm ${verb(state.side)}", { onAction(OrderAction.Confirm) }, Modifier.fillMaxWidth(), enabled = !state.executing)
    StockButton("Back", { onAction(OrderAction.Back) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.TEXT, enabled = !state.executing)
}

@Composable
private fun Done(state: PracticeOrderState, onAction: (OrderAction) -> Unit) {
    val r = state.result ?: return
    val t = r.transaction
    val spacing = StockStepsTheme.spacing
    InstrumentHeader(state)
    Text("${verb(state.side)} complete", Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.positiveText)
    StockCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Detail("Instrument", "${t.instrument.name ?: t.instrument.symbol} (${t.instrument.symbol})")
        Detail("Quantity", PracticeFormat.shareCount(t.quantity))
        Detail("Simulated price", PracticeFormat.price(t.price, t.priceCurrency))
        Detail("Total", PracticeFormat.money(t.amount, r.baseCurrency))
        Detail("Virtual cash now", PracticeFormat.money(r.cashAfter, r.baseCurrency))
        Detail("Reference", t.id)
    }
    Text("Simulated with virtual money. No real order was placed.", style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
    StockButton("View Holdings", { onAction(OrderAction.ViewHoldings) }, Modifier.fillMaxWidth())
    StockButton("Explore Stocks", { onAction(OrderAction.ExploreStocks) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.OUTLINED)
    StockButton("Continue Learning", { onAction(OrderAction.ContinueLearning) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.TEXT)
}
