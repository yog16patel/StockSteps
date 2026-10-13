package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.portfolio.PortfolioActionKind
import org.example.stocksteps.portfolio.PortfolioTransaction
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.theme.StockBannerKind

/**
 * Phase D3: Transaction details. Acquires the **same** portfolio presenter as the dashboard (no new loading) and finds the transaction in
 * `state.transactions` by the route's id, so an edit re-renders here and a transaction that disappears is reported, never shown stale. After
 * its own confirmed delete it goes back; the screen underneath announces "Transaction deleted".
 */
@Composable
internal fun PortfolioTransactionScene(
    accounts: AccountDependencies,
    route: PortfolioTransactionRoute,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    hinge: WindowHinge? = null
) {
    val model = viewModel(key = "portfolio") { PortfolioViewModel(accounts.portfolioPresenter) }
    val state by model.state.collectAsStateWithLifecycle()
    val since = rememberSaveable { PortfolioActionFeedback.baseline(state.actionResult) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.actionResult?.id) {
        val result = state.actionResult ?: return@LaunchedEffect
        if (result.id > since && result.kind == PortfolioActionKind.DELETED && result.transactionId == route.transactionId) onBack()
    }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            PortfolioTransactionScreen(
                state = state,
                transactionId = route.transactionId,
                tracker = accounts.portfolioFeedback,
                modifier = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize(),
                onBack = onBack,
                onRefresh = { model.presenter.refresh() },
                onSignIn = onSignIn,
                onDelete = model.presenter::deleteTransaction,
                onSave = { model.presenter.saveTransaction(it, true) },
                onDismissError = model.presenter::clearError,
                onSignInAgain = { scope.signInAgain(accounts) }
            )
        }
    }
}

/**
 * Transaction details (Phase D3): summary (type, security, date, recorded amount with currency) → details (only the fields this type uses
 * and records, `PortfolioTransactionDetailsDisplay`) → notes → Edit / Delete. Edit is the existing sheet; Delete the existing confirmation;
 * samples (D3.1) show "Sample transactions can't be changed." instead of actions. Missing values have no row — never "0.00".
 */
@Composable
internal fun PortfolioTransactionScreen(
    state: PortfolioUiState,
    transactionId: String,
    tracker: PortfolioFeedbackTracker,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSignIn: () -> Unit,
    onDelete: (String) -> Unit,
    onSave: (PortfolioTransaction) -> Unit,
    onDismissError: () -> Unit = {},
    /** E.2: "Sign in again" for an ended session (signs out, so the app's sign-out handling opens Login). */
    onSignInAgain: () -> Unit = {}
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var sampleDetails by rememberSaveable { mutableStateOf(false) }
    val transaction = state.transactions.firstOrNull { it.id == transactionId }
    val readOnly = PortfolioFormRules.readOnly(state)
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale
    // D3: the confirmation sits above the list (an empty list item would still take a gap), like the iOS top inset.
    Column(modifier) {
        // Its own delete goes back instead: the screen underneath announces it.
        PortfolioActionBanner(tracker, state.actionResult, Modifier.padding(start = spacing.screen, end = spacing.screen, top = spacing.sm),
            leaving = { it.kind == PortfolioActionKind.DELETED && it.transactionId == transactionId })
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(spacing.screen), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (state.mockScenario != null) item {
                StockBanner(StockBannerKind.SAMPLE, if (sampleDetails) "${PortfolioDisplay.SAMPLE_SHORT} ${PortfolioDisplay.SAMPLE_BANNER}" else PortfolioDisplay.SAMPLE_SHORT,
                    actionText = if (sampleDetails) PortfolioDisplay.SAMPLE_HIDE else PortfolioDisplay.SAMPLE_DETAILS, onAction = { sampleDetails = !sampleDetails })
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            // A rejected delete (or a sync problem) keeps the server's message, as on the dashboard.
            if (state.error != null || state.offline) item {
                PortfolioErrorBanner(state, onRefresh, onDismissError, onSignInAgain)
            }
            if (!state.signedIn && state.mockScenario == null) item {
                StockBanner(StockBannerKind.INFO, PortfolioDisplay.SIGN_IN_PROMPT, actionText = "Sign in", onAction = onSignIn)
            }
            if (transaction == null) {
                if (!state.loading) item {
                    StockCard(bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(PortfolioTransactionDetailsDisplay.GONE_TITLE, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)
                        Text(PortfolioTransactionDetailsDisplay.GONE_MESSAGE, style = typography.body, color = colors.textBody)
                        StockButton(PortfolioTransactionDetailsDisplay.BACK, onBack, Modifier.fillMaxWidth(), variant = StockButtonVariant.SECONDARY)
                    }
                }
                return@LazyColumn
            }
            val details = PortfolioTransactionDetailsDisplay.of(transaction, state.accounts)
            item {
                StockCard(bordered = false) {
                    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = details.spokenSummary },
                        horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        if (!largeText) PortfolioTransactionAvatar(transaction, state.logoFor(transaction))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                            Text(details.title, style = typography.cardTitle, color = colors.textTitle)
                            details.security?.let { Text(it, style = typography.small, color = colors.textBody) }
                            Text(details.date, style = typography.small, color = colors.textSupporting)
                            details.amount?.let { Text(it, Modifier.padding(top = spacing.xs), style = typography.numberEmphasis, color = colors.textValue) }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(PortfolioTransactionDetailsDisplay.DETAILS, Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textTitle)
                    StockCard(bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        details.rows.forEachIndexed { index, row ->
                            if (index > 0) StockDivider()
                            PortfolioDetailLine(row, largeText)
                        }
                    }
                }
            }
            details.notes?.let { notes -> item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(PortfolioTransactionDetailsDisplay.NOTES, Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textTitle)
                    StockCard(bordered = false) { Text(notes, style = typography.body, color = colors.textBody) }
                }
            } }
            item {
                // Side by side while both labels fit; stacked at large text (the D1.1 rule for form actions).
                val edit: @Composable (Modifier) -> Unit = { m ->
                    StockButton(PortfolioTransactionDetailsDisplay.EDIT, { editing = true }, m, variant = StockButtonVariant.SECONDARY)
                }
                val delete: @Composable (Modifier) -> Unit = { m ->
                    StockButton(PortfolioTransactionDetailsDisplay.DELETE, { deleting = true }, m, variant = StockButtonVariant.DESTRUCTIVE, enabled = !state.busy)
                }
                // D3.1: samples can't be changed, so one plain line instead of actions (the SAMPLE banner above says why).
                if (readOnly) Text(PortfolioDisplay.SAMPLE_CANNOT_CHANGE, style = typography.small, color = colors.textSupporting)
                else if (largeText) Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    edit(Modifier.fillMaxWidth()); delete(Modifier.fillMaxWidth())
                } else Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) { edit(Modifier.weight(1f)); delete(Modifier.weight(1f)) }
            }
        }
    }
    if (deleting && transaction != null) PortfolioDeleteTransactionDialog(transaction, onConfirm = { onDelete(transaction.id); deleting = false }, onDismiss = { deleting = false })
    if (editing && transaction != null) PortfolioEditSheet(state, transaction, onSave) { editing = false }
}

/** "Price per share      USD 100.00": label left, value right; stacked (value under label) at large text so neither squeezes. */
@Composable
private fun PortfolioDetailLine(row: PortfolioDetailRow, stacked: Boolean) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val lines = row.value.split('\n')
    val value: @Composable (TextAlign) -> Unit = { align ->
        Column(horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start) {
            Text(lines.first(), style = typography.numberLabelStrong, color = colors.textValue, textAlign = align)
            lines.drop(1).forEach { Text(it, style = typography.caption, color = colors.textSupporting, textAlign = align) }
        }
    }
    Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            Text(row.label, style = typography.small, color = colors.textSupporting); value(TextAlign.Start)
        } else Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.md)) {
            Text(row.label, Modifier.weight(1f), style = typography.small, color = colors.textSupporting)
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) { value(TextAlign.End) }
        }
    }
}
