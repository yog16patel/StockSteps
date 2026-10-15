package org.example.stocksteps.presentation.earnings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    // 5D.4.2: the reported/actual value is the loud one; estimate and surprise are quieter (same size, medium weight).
    StockMetricGrid(metrics.mapIndexed { i, it -> StockMetricItem(it.label, it.value, helper = it.detail, quiet = i > 0) }, modifier, maxColumns = 3, rowsWhenNarrow = true)
}

/** The measured price change with the app's arrow and gain/loss style; the sign and arrow carry the meaning, colour is secondary. */
@Composable
internal fun EarningsReactionHeadline(headline: String, direction: PriceDirection?, modifier: Modifier = Modifier) {
    if (direction == null || direction == PriceDirection.UNAVAILABLE) {
        Text(headline, modifier, style = StockStepsTheme.typography.numberMedium, color = StockStepsTheme.colors.textPrimary)
    } else StockPriceChange(headline, direction, modifier, style = StockStepsTheme.typography.numberMedium, maxLines = Int.MAX_VALUE)
}

/** A collapsed secondary section; since 5D.5.1 the design-system [StockDisclosure] (same behaviour, kept under its Earnings name). */
@Composable
internal fun EarningsDisclosure(title: String, modifier: Modifier = Modifier, icon: ImageVector = StockIcons.Info, content: @Composable ColumnScope.() -> Unit) =
    StockDisclosure(title, modifier, icon, content)

/** Caption lines inside a disclosure ([StockNotes]). */
@Composable
internal fun EarningsNotes(lines: List<String>) = StockNotes(lines)

/** Neutral insight icon from `EarningsDisplay.insightIcon` (never a direction cue). */
internal fun earningsInsightIcon(category: String): ImageVector = when (EarningsDisplay.insightIcon(category)) {
    "percent" -> StockIcons.Percent
    "chart" -> StockIcons.BarChart
    "layers" -> StockIcons.Layers
    "info" -> StockIcons.Info
    else -> StockIcons.Lightbulb
}

/**
 * 5D.4.1: the one "Show details" of a card; since 5D.5.1 the design-system [StockShowDetails] (same behaviour, kept under its Earnings name).
 * 5D.4.2: [trailing] shares the toggle's row (e.g. "Learn more").
 */
@Composable
internal fun EarningsShowDetails(explanation: String?, hasMore: Boolean, modifier: Modifier = Modifier,
                                 trailing: (@Composable () -> Unit)? = null,
                                 content: @Composable ColumnScope.() -> Unit = {}) = StockShowDetails(explanation, hasMore, modifier, trailing, content)

/** 5D.4.1 (option a): a compact insight row — small neutral icon, period, title; the server explanation behind "Show details", unchanged. */
@Composable
internal fun EarningsInsightRow(category: String, period: String?, title: String, explanation: String, lessonTitle: String?, onLesson: (() -> Unit)?) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    Row(Modifier.fillMaxWidth().padding(vertical = spacing.sm), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        // 5D.4.2: the icon lines up with the title (below the period caption), not with the caption.
        Icon(earningsInsightIcon(category), null, Modifier.padding(top = if (period != null) spacing.lg + spacing.xxs else spacing.none).size(StockStepsTheme.dimensions.icon), tint = colors.primaryText)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            period?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
            Text(title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.bodySemiBold, color = colors.textTitle)
            EarningsShowDetails(null, hasMore = true) {
                Text(explanation, style = StockStepsTheme.typography.small, color = colors.textBody)
                if (lessonTitle != null && onLesson != null) StockButton(lessonTitle, onClick = onLesson, variant = StockButtonVariant.TEXT, icon = StockIcons.Lightbulb)
            }
        }
    }
}
