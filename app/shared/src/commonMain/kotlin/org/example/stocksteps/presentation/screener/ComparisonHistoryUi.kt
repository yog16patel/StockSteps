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
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text("Historical comparison", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
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
            HistoryTable(view) { title, text -> why = title to text }
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
        if (!state.historyPlus) Text("3Y and 5Y history, growth, margins, EPS growth and the revenue index are part of StockSteps+.", style = typography.tiny, color = colors.textTertiary)
    }
    why?.let { (title, text) -> AlertDialog(onDismissRequest = { why = null }, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onClick = { why = null }) { Text("OK") } }) }
    state.historyUpsell?.let { HistoryUpsellDialog(it, actions) }
}

/** Newest first; each cell shows the company's own fiscal label and period end. Columns stay equal width (no horizontal scroll). */
@Composable
private fun HistoryTable(view: HistoryView, onWhy: (String, String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
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
                        Text(cell.text + if (cell.explanation != null) " ⓘ" else "", style = typography.numberLabel, maxLines = 1,
                            color = if (cell.explanation != null) colors.textTertiary else colors.textPrimary)
                        cell.period?.let { Text(it, style = typography.tiny, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
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
