package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
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
import org.example.stocksteps.designsystem.icons.StockIcons
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
    onClickLabel: String? = null,
    /** 5D.4.2: a secondary metric in a group (e.g. an estimate beside the reported value): same size, medium weight, body colour. Default unchanged. */
    quiet: Boolean = false,
    /** 5D.5.3: a small ⓘ after the label signals that tapping explains the metric (decorative; [onClickLabel] says it). Default off. */
    info: Boolean = false,
    /**
     * 5D.5.4B: the metric on its own surface tile (no border), ⓘ at the tile's top-right, a larger value — for a few headline metrics
     * (Company Details At a Glance). Default off (unchanged).
     */
    tile: Boolean = false
) {
    val colors = StockStepsTheme.colors
    val baseValue = if (tile) StockStepsTheme.typography.sectionTitle else StockStepsTheme.typography.numberEmphasis
    val valueStyle = if (quiet) baseValue.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) else baseValue
    Column(
        modifier = modifier
            .then(if (tile) Modifier.clip(StockStepsTheme.shapes.card).background(colors.surface) else Modifier)
            .heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier.semantics(mergeDescendants = true) {})
            .then(if (tile) Modifier.padding(StockStepsTheme.spacing.md) else Modifier.padding(vertical = StockStepsTheme.spacing.xs)),
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.labelValueGap)
    ) {
        if (tile) Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            Text(label, Modifier.weight(1f), style = StockStepsTheme.typography.label, color = colors.textSecondary)
            if (info) Icon(StockIcons.Info, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
        } else StockMetricLabel(label, info)
        if (direction != null && direction != PriceDirection.UNAVAILABLE) {
            StockPriceChange(value, direction, style = valueStyle, maxLines = Int.MAX_VALUE)
        } else {
            Text(value, style = valueStyle, color = if (quiet) colors.textBody else colors.textPrimary)
        }
        if (helper != null) Text(helper, style = StockStepsTheme.typography.caption, color = colors.textTertiary)
    }
}

/** A metric label, optionally followed by a decorative ⓘ (5D.5.3) that wraps with the label instead of squeezing it. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun StockMetricLabel(label: String, info: Boolean, style: androidx.compose.ui.text.TextStyle = StockStepsTheme.typography.label,
                              color: androidx.compose.ui.graphics.Color = StockStepsTheme.colors.textSecondary, modifier: Modifier = Modifier) {
    if (!info) { Text(label, modifier, style = style, color = color); return }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style, color = color)
        Icon(StockIcons.Info, contentDescription = null, tint = StockStepsTheme.colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
    }
}

/** One cell of a [StockMetricGrid]. */
internal data class StockMetricItem(
    val label: String,
    val value: String,
    val helper: String? = null,
    val direction: PriceDirection? = null,
    val onClick: (() -> Unit)? = null,
    val onClickLabel: String? = null,
    /** 5D.4.2: see [StockMetric] `quiet`; default unchanged. */
    val quiet: Boolean = false,
    /** 5D.5.3: see [StockMetric] `info`; default off. */
    val info: Boolean = false
)

/**
 * Metric row/grid (At a Glance, valuation, index summaries): up to [maxColumns] equal columns that drop to fewer columns
 * — one at large font scale — instead of truncating values ([StockLayout.metricColumns], shared with SwiftUI).
 */
@Composable
internal fun StockMetricGrid(items: List<StockMetricItem>, modifier: Modifier = Modifier, maxColumns: Int = 3,
                              /** When not every item fits on one line, show aligned label/value rows instead of an uneven grid (e.g. 2 + 1). */
                              rowsWhenNarrow: Boolean = false,
                              /** 5D.5.4B: each metric on its own surface tile ([StockMetric] `tile`), equal heights per row. Default off. */
                              tiled: Boolean = false) {
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
            val rowValueStyle = StockStepsTheme.typography.numberMedium
            val available = maxWidth.value
            val gap = StockStepsTheme.spacing.md
            // Portfolio C.1: one decision for the whole group — if any row must stack, all do, so neighbouring rows read the same way
            // ("Invested / cost  CAD 1,455.00" beside a stacked "Unrealized P/L" looked broken at 2.0×).
            val anyStacked = items.any { item ->
                val dir = item.direction != null && item.direction != PriceDirection.UNAVAILABLE
                val w = item.label.split(' ').maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0
                val v = measurer.measure((if (dir) "↑\u00A0" else "") + item.value, rowValueStyle).size.width
                with(density) { StockLayout.metricRowStacked(w.toDp().value, v.toDp().value, available, gap.value) }
            }
            // C.1 cleanup: stacked label/value groups get a wider gap than the label→value gap (xxs), so each pair reads as one group at 2.0×.
            Column(verticalArrangement = Arrangement.spacedBy(if (anyStacked) StockStepsTheme.spacing.md else StockStepsTheme.spacing.sm)) {
                items.forEach { item ->
                    val hasDirection = item.direction != null && item.direction != PriceDirection.UNAVAILABLE
                    val value: @Composable () -> Unit = {
                        if (hasDirection) {
                            StockPriceChange(item.value, item.direction!!, style = rowValueStyle, maxLines = Int.MAX_VALUE)
                        } else {
                            Text(item.value, style = rowValueStyle, color = StockStepsTheme.colors.textPrimary, textAlign = TextAlign.End)
                        }
                    }
                    // Stack only when the label's longest word and the value can't share the line (StockLayout.metricRowStacked).
                    val stacked = anyStacked
                    if (stacked) {
                        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
                            StockMetricLabel(item.label, item.info)
                            value()
                        }
                    } else Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(gap)) {
                        StockMetricLabel(item.label, item.info, modifier = Modifier.weight(1f))
                        value()
                    }
                }
            }
            return@BoxWithConstraints
        }
        val gap = if (tiled) StockStepsTheme.spacing.sm else StockStepsTheme.spacing.md
        Column(verticalArrangement = Arrangement.spacedBy(if (tiled) gap else StockStepsTheme.spacing.itemGap)) {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().then(if (tiled) Modifier.height(IntrinsicSize.Min) else Modifier), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { item ->
                        StockMetric(item.label, item.value, Modifier.weight(1f).then(if (tiled) Modifier.fillMaxHeight() else Modifier), item.helper,
                            item.direction, item.onClick, item.onClickLabel, item.quiet, item.info, tile = tiled)
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
    compact: Boolean = false,
    /** 5D.5.3: tapping the row explains the metric (the row is then a 48 dp button); [info] adds a decorative ⓘ after the label. Defaults unchanged. */
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    info: Boolean = false
) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val tap = if (onClick != null) Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        else Modifier.semantics(mergeDescendants = true) {}
    Column(modifier.fillMaxWidth().then(tap).padding(vertical = if (compact) StockStepsTheme.spacing.xs else StockStepsTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            StockMetricLabel(label, info, style = if (compact) typography.small else typography.body, color = if (compact) colors.textSecondary else colors.textBody,
                modifier = Modifier.weight(1f))
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
