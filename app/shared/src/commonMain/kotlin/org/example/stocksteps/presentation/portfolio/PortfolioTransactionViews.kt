package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import org.example.stocksteps.designsystem.components.StockBanner
import org.example.stocksteps.designsystem.components.StockIconTile
import org.example.stocksteps.designsystem.components.StockTickerAvatar
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.portfolio.PortfolioActionKind
import org.example.stocksteps.portfolio.PortfolioActionResult
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.theme.StockBannerKind

/** Android icon for a transaction type icon key (shared mapping `PortfolioTransactionDetailsDisplay.icon`); decorative. */
internal fun transactionIcon(icon: PortfolioTransactionIcon): ImageVector = when (icon) {
    PortfolioTransactionIcon.DIVIDEND -> StockIcons.Coin
    PortfolioTransactionIcon.FEE -> StockIcons.Tag
    PortfolioTransactionIcon.TRANSFER -> StockIcons.ArrowForward
    PortfolioTransactionIcon.CASH, PortfolioTransactionIcon.SECURITY -> StockIcons.Bank
}

/** Logo / ticker avatar for a security, type icon tile otherwise. Decorative (the row names the security). */
@Composable
internal fun PortfolioTransactionAvatar(transaction: PortfolioTransaction, logoUrl: String?) {
    val colors = StockStepsTheme.colors
    val icon = PortfolioTransactionDetailsDisplay.icon(transaction)
    val symbol = transaction.instrument?.symbol
    if (icon == PortfolioTransactionIcon.SECURITY && symbol != null) StockTickerAvatar(symbol, logoUrl = logoUrl, size = StockStepsTheme.dimensions.iconTile)
    else StockIconTile(transactionIcon(icon), colors.primaryContainer, colors.primaryText)
}

/**
 * Transaction row shared by the dashboard preview and Transaction history (Phase D3 refinement of the C.1 row). Normal text: avatar · type with
 * the recorded amount right-aligned on its line, security, date, price per share · ⋯. Large text: no avatar and every group on its own line, so labels and
 * amounts wrap instead of squeezing. The row opens details ([onOpen]); ⋯ stays a separate 48dp target with Edit and Delete ([onDelete] null
 * hides Delete — samples are read-only). Text comes from `PortfolioTransactionDetailsDisplay.row`: recorded values only, no totals.
 */
@Composable
internal fun PortfolioTransactionRow(transaction: PortfolioTransaction, logoUrl: String?, onOpen: (() -> Unit)?, onEdit: () -> Unit, onDelete: (() -> Unit)?) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    var open by remember { mutableStateOf(false) }
    val text = PortfolioTransactionDetailsDisplay.row(transaction)
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale
    Row(Modifier.fillMaxWidth().padding(top = spacing.xxs, bottom = spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f)
                .then(if (onOpen != null) Modifier.clickable(onClickLabel = PortfolioTransactionDetailsDisplay.OPEN, onClick = onOpen) else Modifier)
                .padding(start = spacing.cardPadding, top = spacing.xs, bottom = spacing.xs)
                .clearAndSetSemantics {
                    contentDescription = text.spoken
                    if (onOpen != null) onClick(PortfolioTransactionDetailsDisplay.OPEN) { onOpen(); true }
                },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            if (!largeText) PortfolioTransactionAvatar(transaction, logoUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                // The amount shares the title's line, so the security and date lines below get the full width (360dp: "Oct 13, 2026 · USD 100.00
                // per share" broke into three short lines beside a centred amount).
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(text.title, Modifier.weight(1f), style = typography.bodySemiBold, color = colors.textTitle)
                    if (!largeText) text.amount?.let { Text(it, style = typography.numberLabelStrong, color = colors.textValue, textAlign = TextAlign.End) }
                }
                text.security?.let { Text(it, style = typography.small, color = colors.textBody) }
                // Date and price per share on their own lines: joined, "USD 100.00 per share" broke mid-phrase at 360dp.
                Text(text.date, style = typography.small, color = colors.textSupporting)
                text.unitPrice?.let { Text(it, style = typography.small, color = colors.textSupporting) }
                if (largeText) text.amount?.let { Text(it, style = typography.numberLabelStrong, color = colors.textValue) }
            }
        }
        Box {
            IconButton(onClick = { open = true }) { Icon(StockIcons.More, contentDescription = "Actions for ${text.title}${text.security?.let { ", " + it.replace(' ', ' ') } ?: ""}", tint = colors.iconSecondary) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { open = false; onEdit() })
                onDelete?.let { delete -> DropdownMenuItem(text = { Text("Delete…", color = colors.negativeText) }, onClick = { open = false; delete() }) }
            }
        }
    }
}

/**
 * The delete confirmation used wherever a transaction can be deleted (same presenter call on every screen). D3: names the transaction being
 * deleted (type · security · date) under the explanation.
 */
@Composable
internal fun PortfolioDeleteTransactionDialog(transaction: PortfolioTransaction, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val row = PortfolioTransactionDetailsDisplay.row(transaction)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(PortfolioDisplay.DELETE_TRANSACTION_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
                Text(PortfolioTransactionDetailsDisplay.DELETE_MESSAGE)
                Text(listOfNotNull(row.title, row.security, row.date, row.amount).joinToString(" · "), style = StockStepsTheme.typography.bodySemiBold)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(PortfolioDisplay.DELETE, color = StockStepsTheme.colors.negativeText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(PortfolioDisplay.FORM_CANCEL) } })
}

/**
 * Phase D3 success banner: shows the confirmed result once (shared [tracker]) when it arrives on this screen or when this screen comes back
 * into view; hides after `PortfolioActionFeedback.VISIBLE_MILLIS` or on Dismiss; announced politely. [leaving] lets a screen that is about to
 * close for this result (details after its own delete) leave it for the screen underneath.
 */
@Composable
internal fun PortfolioActionBanner(tracker: PortfolioFeedbackTracker, result: PortfolioActionResult?, modifier: Modifier = Modifier,
                                   leaving: (PortfolioActionResult) -> Boolean = { false }) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(result?.id) {
        if (result == null || leaving(result)) return@LaunchedEffect
        tracker.take(result)?.let { text ->
            message = text
            delay(PortfolioActionFeedback.VISIBLE_MILLIS)
            message = null
        }
    }
    message?.let { text ->
        StockBanner(StockBannerKind.SUCCESS, text, modifier.semantics { liveRegion = LiveRegionMode.Polite }, onDismiss = { message = null })
    }
}

/**
 * Phase D1: editing opens the full form in a full-height sheet with Cancel; no new route. D3: the sheet closes only after the presenter confirms
 * this transaction's update ([PortfolioActionResult]) — a rejection keeps it open with the server message, and a cancelled request (account
 * changed) is never mistaken for success. A read-only (sample) sheet always closes: sample states report busy = true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PortfolioEditSheet(state: PortfolioUiState, transaction: PortfolioTransaction, onSave: (PortfolioTransaction) -> Unit, onClose: () -> Unit) {
    var submittedAfter by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.actionResult?.id) {
        val after = submittedAfter ?: return@LaunchedEffect
        val result = state.actionResult ?: return@LaunchedEffect
        if (result.id > after && result.kind == PortfolioActionKind.UPDATED && result.transactionId == transaction.id) { submittedAfter = null; onClose() }
    }
    val readOnly = PortfolioFormRules.readOnly(state)
    ModalBottomSheet(onDismissRequest = { if (readOnly || !state.busy) onClose() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = StockStepsTheme.colors.appBackground) {
        PortfolioTransactionForm(state = state, modifier = Modifier.imePadding(), initialSymbol = transaction.instrument?.symbol.orEmpty(),
            initialName = transaction.instrument?.name, initialExchange = transaction.instrument?.exchange, initialCurrency = transaction.currency.name,
            existing = transaction, showTitle = true, onCancel = onClose,
            onSubmit = { submittedAfter = PortfolioActionFeedback.baseline(state.actionResult); onSave(it) })
    }
}

/** Holding logos by symbol for transaction rows (the holdings' existing `logoUrl`; no lookup, no new data). */
internal fun PortfolioUiState.logoFor(transaction: PortfolioTransaction): String? =
    transaction.instrument?.symbol?.let { symbol -> holdings.firstOrNull { it.symbol == symbol }?.logoUrl }

