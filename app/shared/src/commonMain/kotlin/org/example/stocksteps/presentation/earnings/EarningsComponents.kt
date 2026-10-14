package org.example.stocksteps.presentation.earnings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.earnings.MetricLine

// Phase 5D.3 Earnings building blocks (Compose). SwiftUI mirrors them in EarningsComponents.swift. Display only: every value is already formatted.

/** The three key numbers (EPS: reported, estimate, surprise; revenue: reported, estimate, YoY) as the shared metric grid (drops columns at large text). */
@Composable
internal fun EarningsKeyMetrics(metrics: List<MetricLine>, modifier: Modifier = Modifier) {
    if (metrics.isEmpty()) return
    // 5D.4: when the three can't share a row (narrow, large text), aligned label/value rows instead of three tall stacked blocks.
    StockMetricGrid(metrics.map { StockMetricItem(it.label, it.value, helper = it.detail) }, modifier, maxColumns = 3, rowsWhenNarrow = true)
}

/** 5D4-2 (option A): the first sentence of a server explanation, the rest behind "Show details" (nothing removed, wording unchanged). */
@Composable
internal fun EarningsExpandableText(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = StockStepsTheme.typography.small) {
    val (first, rest) = remember(text) { EarningsDisplay.firstSentence(text) }
    var open by rememberSaveable(text) { mutableStateOf(false) }
    val colors = StockStepsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Text(if (open && rest != null) "$first $rest" else first, style = style, color = colors.textBody)
        if (rest != null) Row(Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide details" else "Show details") { open = !open }
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            Text(if (open) "Hide details" else "Show details", style = StockStepsTheme.typography.label, color = colors.primaryText)
            Icon(StockIcons.ChevronDown, null, Modifier.size(StockStepsTheme.dimensions.iconSmall).rotate(if (open) 180f else 0f), tint = colors.primaryText)
        }
    }
}

/** The measured price change with the app's arrow and gain/loss style; the sign and arrow carry the meaning, colour is secondary. */
@Composable
internal fun EarningsReactionHeadline(headline: String, direction: PriceDirection?, modifier: Modifier = Modifier) {
    if (direction == null || direction == PriceDirection.UNAVAILABLE) {
        Text(headline, modifier, style = StockStepsTheme.typography.numberMedium, color = StockStepsTheme.colors.textPrimary)
    } else StockPriceChange(headline, direction, modifier, style = StockStepsTheme.typography.numberMedium, maxLines = Int.MAX_VALUE)
}

/**
 * A collapsed secondary section (methodology, sources, disclosures): a 48 dp row that announces its state, the content below when open. Nothing
 * inside is removed, only folded away from the main reading flow (the Comparison 5C.1 "Sources & methodology" pattern).
 */
@Composable
internal fun EarningsDisclosure(title: String, modifier: Modifier = Modifier, icon: ImageVector = StockIcons.Info, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val colors = StockStepsTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide $title" else "Show $title") { open = !open }
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            Icon(icon, null, Modifier.size(StockStepsTheme.dimensions.iconSmall), tint = colors.primaryText)
            Text(title, Modifier.weight(1f), style = StockStepsTheme.typography.label, color = colors.primaryText)
            Icon(StockIcons.ChevronRight, null, Modifier.rotate(if (open) 90f else 0f), tint = colors.iconSecondary)
        }
        if (open) content()
    }
}

/** Caption lines inside a disclosure. */
@Composable
internal fun EarningsNotes(lines: List<String>) {
    lines.forEach { Text(it, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary) }
}

/** Neutral insight icon from `EarningsDisplay.insightIcon` (never a direction cue). */
internal fun earningsInsightIcon(category: String): ImageVector = when (EarningsDisplay.insightIcon(category)) {
    "percent" -> StockIcons.Percent
    "chart" -> StockIcons.BarChart
    "layers" -> StockIcons.Layers
    "info" -> StockIcons.Info
    else -> StockIcons.Lightbulb
}
