package org.example.stocksteps.presentation.screener

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.screener.*

/** Phase 3 actions; defaults keep previews and older call sites working. */
internal data class HistoryActions(
    val onRange: (HistoryRange) -> Unit = {},
    val onMetric: (HistoryMetric) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissUpsell: () -> Unit = {},
    val onUpgrade: () -> Unit = {},
    val onSignIn: () -> Unit = {}
)

/**
 * Company Comparison Phase 3: historical financial comparison. Free = latest four fiscal quarters of
 * revenue, net income and diluted EPS; 3Y/5Y and growth, margin, EPS growth and revenue index are
 * StockSteps+ (decided by the server; locked items open a preview, never a request). Values, periods,
 * observations and the table come formatted from the shared presenter; this card only renders.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistoricalComparisonCard(state: ComparisonUiState, actions: HistoryActions) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var why by remember { mutableStateOf<Pair<String, String>?>(null) }
    var allInsights by rememberSaveable { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val accessibilityText = density.fontScale >= StockStepsTheme.dimensions.largeFontScale * 1.15f
    StockCard(bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        StockTitleWithBadge(title = { Text("Historical comparison", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle) },
            badge = { if (!state.historyPlus) StockStatusBadge("1Y free", org.example.stocksteps.theme.StockBadgeKind.INFO, size = StockBadgeSize.COMPACT) })
        Text("How each company's reported results changed over time. ${state.historyRange.description}; " +
            (if (state.historyRange.granularity == HistoryGranularity.QUARTERLY) "quarterly" else "annual") + " figures, as reported.",
            style = typography.caption, color = colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            HistoryRange.entries.forEach { range ->
                val locked = state.historyLocked(range)
                StockChip(range.label + if (locked) " · Plus" else "", selected = state.historyRange == range, onClick = { actions.onRange(range) },
                    modifier = Modifier.semantics { contentDescription = range.description + if (locked) ", requires StockSteps+" else ""; selected = state.historyRange == range })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            HistoryMetric.entries.forEach { metric ->
                val locked = state.historyLocked(metric)
                StockChip(metric.label + if (locked) " · Plus" else "", selected = state.historyMetric == metric, onClick = { actions.onMetric(metric) },
                    modifier = Modifier.semantics { contentDescription = metric.label + if (locked) ", requires StockSteps+" else ""; selected = state.historyMetric == metric })
            }
        }
        when {
            state.historyLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading financial history" })
            state.historyError != null -> StockErrorState(state.historyError.orEmpty(), actions.onRetry)
        }
        val history = state.history
        val view = state.historyView()
        if (history != null && view != null) {
            Text(view.title, Modifier.semantics { heading() }, style = typography.bodySemiBold, color = colors.textPrimary)
            Text(view.definition, style = typography.caption, color = colors.textSecondary)
            Text(view.unitNote, style = typography.tiny, color = colors.textTertiary)
            if (view.empty) Text("No values to show for this metric and period.", style = typography.small, color = colors.textBody)
            else view.series.firstOrNull()?.let { first ->
                StockTrendChart(values = first.values, details = view.details, xLabels = view.xLabels, yLabels = emptyList(), description = view.description,
                    reference = view.reference, referenceLabel = view.referenceLabel, seriesLabel = first.label,
                    additional = view.series.drop(1).map { it.label to it.values })
            }
            // Same shared rule as the metric rows: columns only while every value and period word fits.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val width = maxWidth.value
                val sideBySide = remember(view, width, density.fontScale) {
                    val tokens = ComparisonDisplay.historyLayoutTexts(view).flatMap(org.example.stocksteps.theme.StockComparisonLayout::tokens) + view.symbols
                    val widest = with(density) { tokens.distinct().maxOfOrNull { measurer.measure(it, typography.numberLabel).size.width.toDp().value } ?: 0f }
                    org.example.stocksteps.theme.StockComparisonLayout.sideBySide(widest, view.symbols.size, width, spacing.xs.value, accessibilityText)
                }
                HistoryTable(view, sideBySide) { title, text -> why = title to text }
            }
            val insights = history.insights + view.insights
            if (insights.isNotEmpty()) {
                Text("What does this mean?", Modifier.semantics { heading() }, style = typography.label, color = colors.textSecondary)
                (if (allInsights) insights else insights.take(3)).forEach { Text("• $it", style = typography.small, color = colors.textBody) }
                if (insights.size > 3) TextButton(onClick = { allInsights = !allInsights }, modifier = Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)
                    .semantics { stateDescription = if (allInsights) "Expanded" else "Collapsed" }) { Text(if (allInsights) "Show fewer" else "Show all ${insights.size} observations") }
                Text("These describe reported figures. They don't explain why results changed and aren't predictions or advice.", style = typography.tiny, color = colors.textTertiary)
            }
            Text("Source: ${history.source}", style = typography.caption, color = colors.textSecondary)
            (view.notes + history.notes).distinct().forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
            history.access.message?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
        }
        // Locked state: one PREMIUM label and the existing preview flow (tapping a "· Plus" option); nothing is requested to draw it.
        if (!state.historyPlus && state.history?.access?.message == null) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StockStatusBadge("StockSteps+", org.example.stocksteps.theme.StockBadgeKind.PREMIUM, size = StockBadgeSize.COMPACT)
            Text("3Y and 5Y history, growth, margins, EPS growth and the revenue index.", Modifier.weight(1f), style = typography.caption, color = colors.textSupporting)
        }
    }
    why?.let { (title, text) -> AlertDialog(onDismissRequest = { why = null }, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onClick = { why = null }) { Text("OK") } }) }
    state.historyUpsell?.let { HistoryUpsellDialog(it, actions) }
}

/**
 * Newest first; each cell shows the company's own fiscal label and period end. Equal columns when they fit ([sideBySide]); otherwise each
 * period lists one line per company (Phase 5C), so values and periods are never cut. Missing values read "No data" with a reason.
 */
@Composable
private fun HistoryTable(view: HistoryView, sideBySide: Boolean, onWhy: (String, String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    if (!sideBySide) Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        view.rows.forEach { row ->
            HorizontalDivider(color = colors.borderSubtle)
            Text(row.title, Modifier.semantics { heading() }, style = typography.label, color = colors.textSecondary)
            view.symbols.zip(row.cells).forEach { (symbol, cell) ->
                val explanation = cell.explanation
                Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
                    .then(if (explanation != null) Modifier.clickable(onClickLabel = "Why $symbol has no value") { onWhy("$symbol · ${cell.period ?: row.title}", explanation) } else Modifier)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$symbol, ${row.title}, " + if (cell.available) listOfNotNull(cell.text, cell.period).joinToString(", ") else "unavailable, $explanation"
                    }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(symbol, style = typography.label, color = colors.primaryText)
                    Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text(if (cell.available) cell.text else ComparisonDisplay.NO_DATA, style = typography.numberLabel,
                            color = if (cell.available) colors.textValue else colors.textTertiary)
                        cell.period?.let { Text(it, style = typography.tiny, color = colors.textSupporting, textAlign = androidx.compose.ui.text.style.TextAlign.End) }
                    }
                }
            }
            row.note?.let { Text(it, style = typography.tiny, color = colors.cautionText) }
        }
    }
    else Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Row(Modifier.fillMaxWidth().semantics { heading() }, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            view.symbols.forEach { Text(it, Modifier.weight(1f), style = typography.label, color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        view.rows.forEach { row ->
            HorizontalDivider(color = colors.borderSubtle)
            Text(row.title, style = typography.tiny, color = colors.textTertiary)
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = row.title + ": " + view.symbols.zip(row.cells).joinToString("; ") { (symbol, cell) ->
                    "$symbol " + if (cell.available) "${cell.text}, ${cell.period}" else "not available, ${cell.explanation}" } + (row.note?.let { ". $it" } ?: "")
            }, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                view.symbols.zip(row.cells).forEach { (symbol, cell) ->
                    val explanation = cell.explanation
                    Column(Modifier.weight(1f).heightIn(min = StockStepsTheme.dimensions.touchTarget)
                        .then(if (explanation != null) Modifier.clickable(onClickLabel = "Why $symbol has no value") { onWhy("$symbol · ${cell.period ?: row.title}", explanation) } else Modifier)) {
                        Text(if (cell.available) cell.text else ComparisonDisplay.NO_DATA, style = typography.numberLabel,
                            color = if (cell.available) colors.textValue else colors.textTertiary)
                        cell.period?.let { Text(it, style = typography.tiny, color = colors.textSecondary) }
                    }
                }
            }
            row.note?.let { Text(it, style = typography.tiny, color = colors.cautionText) }
        }
    }
}

/** The StockSteps+ preview (honest: no prices or purchase flow; Settings shows plan status). The free 1Y view stays. */
@Composable
private fun HistoryUpsellDialog(upsell: HistoryUpsell, actions: HistoryActions) {
    AlertDialog(onDismissRequest = actions.onDismissUpsell, containerColor = StockStepsTheme.colors.surface,
        title = { Text(upsell.title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
                Text(upsell.body, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody)
                upsell.benefits.forEach { Text("• $it", style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody) }
                Text(upsell.footnote, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
            }
        },
        confirmButton = { StockButton(if (upsell.signIn) "Sign In" else "See StockSteps+", onClick = if (upsell.signIn) actions.onSignIn else actions.onUpgrade, variant = StockButtonVariant.TEXT) },
        dismissButton = { StockButton("Not now", onClick = actions.onDismissUpsell, variant = StockButtonVariant.TEXT) })
}
