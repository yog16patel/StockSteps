package org.example.stocksteps.presentation.screener

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import org.example.stocksteps.designsystem.components.StockCard
import org.example.stocksteps.designsystem.components.StockChip
import org.example.stocksteps.designsystem.components.StockTag
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.screener.ComparisonInsight
import org.example.stocksteps.screener.MetricInterpretation

/**
 * Company Comparison Phase 2 (free): one metric's guided explanation, shown inline under its row.
 * Concise by default (what it measures, your comparison, what to keep in mind, explore next);
 * "Learn more" adds why it matters, the formula, more caveats and research questions. Every sentence
 * comes from the shared `ComparisonInterpretationEngine`; this composable only renders.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuidedMetricExplanation(
    guide: MetricInterpretation,
    deeper: Boolean,
    onToggleDeeper: () -> Unit,
    onRelated: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(modifier.fillMaxWidth(), containerColor = colors.surfaceSecondary, bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(guide.title, Modifier.weight(1f).semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
            StockTag(guide.comparability.label, Modifier.semantics { contentDescription = "Comparability: ${guide.comparability.label}" })
        }
        Part("What it measures") { Body(guide.definition) }
        Part("Your comparison") {
            Body(guide.observation)
            guide.meaning?.let { Body(it) }
        }
        if (guide.keyCaveats.isNotEmpty()) Part("What to keep in mind") { guide.keyCaveats.forEach { Bullet(it) } }
        if (deeper) {
            Part("Why it matters") { Body(guide.whyItMatters) }
            guide.calculation?.let { Part("How it's calculated") { Body(it) } }
            if (guide.moreCaveats.isNotEmpty() || guide.learnMore.isNotEmpty()) Part("More to consider") { (guide.moreCaveats + guide.learnMore).forEach { Bullet(it) } }
            Part("Questions to research") { guide.questions.forEach { Bullet(it) } }
        }
        Part("Explore next") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                guide.related.forEach { related ->
                    StockChip(related.label, selected = false, onClick = { onRelated(related.id) },
                        modifier = Modifier.semantics { onClick(label = "Open ${related.label}") { onRelated(related.id); true } })
                }
            }
            if (!deeper) guide.questions.firstOrNull()?.let { Bullet(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            TextButton(onClick = onToggleDeeper, modifier = Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)
                .semantics { stateDescription = if (deeper) "Expanded" else "Collapsed" }) { Text(if (deeper) "Show less" else "Learn more") }
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)
                .semantics { contentDescription = "Close explanation of ${guide.label}" }) { Text("Close") }
        }
        Text("Education, not investment advice. These explanations describe the numbers; they don't rank the companies.",
            style = typography.tiny, color = colors.textTertiary)
    }
}

@Composable
private fun Part(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Text(title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.textSecondary)
        content()
    }
}

@Composable
private fun Body(text: String) = Text(text, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)

@Composable
private fun Bullet(text: String) = Text("• $text", style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)

/** "What can we learn from this comparison?": two or three grounded lines from the engine, never a ranking. */
@Composable
internal fun ComparisonLearningSummary(insights: List<ComparisonInsight>, onOpen: (String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text("What can we learn from this comparison?", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        insights.forEach { insight ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Text(insight.category.title, style = typography.tiny, color = colors.textTertiary)
                Text(insight.text, style = typography.small, color = colors.textBody)
                insight.metricId?.let { id ->
                    TextButton(onClick = { onOpen(id) }, modifier = Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)) { Text("Explain this") }
                }
            }
        }
        Text("Tap Explain on any metric to learn what it measures, what to keep in mind and what to look at next. Free for everyone.",
            style = typography.caption, color = colors.textSecondary)
    }
}
