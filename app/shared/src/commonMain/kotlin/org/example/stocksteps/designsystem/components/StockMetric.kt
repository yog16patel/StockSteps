package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.StockLayout

/**
 * One metric: muted label, prominent value, short context. Direction (if any) adds an arrow and semantic color;
 * metrics without direction stay neutral. Tappable metrics open an explanation. The value wraps (never truncates),
 * so long values like "+14.9 % (YoY)" stay readable at large font sizes.
 */
@Composable
internal fun StockMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    direction: PriceDirection? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null
) {
    val colors = StockStepsTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier.semantics(mergeDescendants = true) {})
            .padding(vertical = StockStepsTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.labelValueGap)
    ) {
        Text(label, style = StockStepsTheme.typography.label, color = colors.textSecondary)
        if (direction != null && direction != PriceDirection.UNAVAILABLE) {
            StockPriceChange(value, direction, style = StockStepsTheme.typography.numberEmphasis, maxLines = Int.MAX_VALUE)
        } else {
            Text(value, style = StockStepsTheme.typography.numberEmphasis, color = colors.textPrimary)
        }
        if (helper != null) Text(helper, style = StockStepsTheme.typography.caption, color = colors.textTertiary)
    }
}

/** One cell of a [StockMetricGrid]. */
internal data class StockMetricItem(
    val label: String,
    val value: String,
    val helper: String? = null,
    val direction: PriceDirection? = null,
    val onClick: (() -> Unit)? = null,
    val onClickLabel: String? = null
)

/**
 * Metric row/grid (At a Glance, valuation, index summaries): up to [maxColumns] equal columns that drop to fewer columns
 * — one at large font scale — instead of truncating values ([StockLayout.metricColumns], shared with SwiftUI).
 */
@Composable
internal fun StockMetricGrid(items: List<StockMetricItem>, modifier: Modifier = Modifier, maxColumns: Int = 3,
                              /** When not every item fits on one line, show aligned label/value rows instead of an uneven grid (e.g. 2 + 1). */
                              rowsWhenNarrow: Boolean = false) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val valueStyle = StockStepsTheme.typography.numberEmphasis
    val labelStyle = StockStepsTheme.typography.label
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widest = items.maxOfOrNull { item ->
            val value = (if (item.direction != null && item.direction != PriceDirection.UNAVAILABLE) "↑\u00A0" else "") + item.value
            maxOf(measurer.measure(value, valueStyle).size.width, measurer.measure(item.label, labelStyle).size.width)
        }?.let { with(density) { it.toDp().value } } ?: 0f
        val columns = StockLayout.metricColumns(items.size, maxWidth.value, density.fontScale, maxColumns, widest, StockStepsTheme.spacing.md.value)
        if (rowsWhenNarrow && columns < minOf(items.size, maxColumns)) {
            Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
                items.forEach { item ->
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.md)) {
                        Text(item.label, Modifier.weight(1f), style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.textSecondary)
                        if (item.direction != null && item.direction != PriceDirection.UNAVAILABLE) {
                            StockPriceChange(item.value, item.direction, style = StockStepsTheme.typography.numberMedium, maxLines = Int.MAX_VALUE)
                        } else {
                            Text(item.value, style = StockStepsTheme.typography.numberMedium, color = StockStepsTheme.colors.textPrimary, textAlign = TextAlign.End)
                        }
                    }
                }
            }
            return@BoxWithConstraints
        }
        Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.itemGap)) {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.md)) {
                    row.forEach { item ->
                        StockMetric(item.label, item.value, Modifier.weight(1f), item.helper, item.direction, item.onClick, item.onClickLabel)
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Compact label/value line for grouped lists (financial highlights, ratios, valuation).
 * [change] adds a signed change column; [direction] colors it and adds an arrow.
 */
@Composable
internal fun StockInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    change: String? = null,
    direction: PriceDirection? = null,
    compact: Boolean = false
) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = if (compact) StockStepsTheme.spacing.xs else StockStepsTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            Text(label, modifier = Modifier.weight(1f), style = if (compact) typography.small else typography.body, color = if (compact) colors.textSecondary else colors.textBody)
            Text(value, style = if (compact) typography.numberLabelStrong else typography.numberMedium, color = colors.textPrimary, textAlign = TextAlign.End)
            if (change != null) {
                Box(Modifier.widthIn(min = StockStepsTheme.dimensions.changeColumn), contentAlignment = Alignment.CenterEnd) {
                    if (direction != null && direction != PriceDirection.UNAVAILABLE) StockPriceChange(change, direction, style = typography.numberLabel)
                    else Text(change, style = typography.numberLabel, color = colors.textSecondary)
                }
            }
        }
        if (helper != null) {
            Text(helper, modifier = Modifier.padding(top = StockStepsTheme.spacing.xxs), style = StockStepsTheme.typography.caption, color = colors.textSecondary)
        }
    }
}
