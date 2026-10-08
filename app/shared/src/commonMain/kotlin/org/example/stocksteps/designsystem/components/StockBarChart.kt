package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.stocksteps.companydetail.FinancialBar
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import kotlin.math.abs
import kotlin.math.max

/**
 * Compact period bar chart (one or two series, oldest → newest). Tapping a period selects it and
 * shows its exact value(s) above the chart; negative values hang below a zero baseline and
 * unreported periods are left empty, never drawn as zero. [description] is read by screen readers.
 */
@Composable
internal fun StockBarChart(
    bars: List<FinancialBar>,
    description: String,
    modifier: Modifier = Modifier,
    secondary: List<FinancialBar>? = null,
    primaryLabel: String? = null,
    secondaryLabel: String? = null
) {
    if (bars.isEmpty()) return
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    var selected by remember(bars) { mutableStateOf(bars.lastIndex) }
    val values = bars.mapNotNull { it.value } + secondary.orEmpty().mapNotNull { it.value }
    val top = max(values.maxOrNull() ?: 0.0, 0.0)
    val bottom = minOf(values.minOrNull() ?: 0.0, 0.0)
    val span = (top - bottom).takeIf { it > 0 } ?: 1.0
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Text(
            // Grouped charts name each series so the two lines are never ambiguous.
            listOfNotNull(
                bars.getOrNull(selected)?.detail?.let { if (secondary != null && primaryLabel != null) "$primaryLabel · $it" else it },
                secondary?.getOrNull(selected)?.detail?.let { if (secondaryLabel != null) "$secondaryLabel · $it" else it }
            ).joinToString("\n"),
            modifier = Modifier.clearAndSetSemantics {},
            style = typography.label,
            color = colors.textPrimary
        )
        if (secondary != null && primaryLabel != null && secondaryLabel != null) {
            Row(Modifier.padding(top = spacing.xs).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                Legend(primaryLabel, colors.primary)
                Legend(secondaryLabel, colors.learnAccent)
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm)
                .height(CHART_HEIGHT)
                .clearAndSetSemantics {}
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        selected = ((offset.x / size.width) * bars.size).toInt().coerceIn(0, bars.lastIndex)
                    }
                }
        ) {
            val slot = size.width / bars.size
            val groupWidth = slot * 0.62f
            val barWidth = if (secondary != null) groupWidth / 2 - 2.dp.toPx() else groupWidth
            val zeroY = (size.height * (top / span)).toFloat()
            drawLine(colors.border, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
            fun bar(index: Int, value: Double?, x: Float, color: androidx.compose.ui.graphics.Color) {
                value ?: return
                val height = (abs(value) / span * size.height).toFloat().coerceAtLeast(1.dp.toPx())
                val y = if (value >= 0) zeroY - height else zeroY
                val tint = if (value < 0) colors.negative else color
                drawRoundRect(tint.copy(alpha = if (index == selected) 1f else 0.45f), Offset(x, y), Size(barWidth, height), CornerRadius(3.dp.toPx()))
            }
            bars.forEachIndexed { index, item ->
                val start = slot * index + (slot - groupWidth) / 2
                bar(index, item.value, start, colors.primary)
                secondary?.getOrNull(index)?.let { bar(index, it.value, start + barWidth + 4.dp.toPx(), colors.learnAccent) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = spacing.xs).clearAndSetSemantics {}) {
            bars.forEachIndexed { index, item ->
                Text(
                    item.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                    style = typography.tiny, color = if (index == selected) colors.textPrimary else colors.textTertiary
                )
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Box(Modifier.size(StockStepsTheme.dimensions.statusDot).clip(StockStepsTheme.shapes.pill).background(color))
        Text(label, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
    }
}

/** Two amounts as proportional horizontal bars with their values and a factual caption. */
@Composable
internal fun StockComparisonBars(
    title: String, firstLabel: String, first: Double, firstText: String,
    secondLabel: String, second: Double, secondText: String, caption: String, modifier: Modifier = Modifier
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val largest = max(abs(first), abs(second)).takeIf { it > 0 } ?: 1.0
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(title, style = StockStepsTheme.typography.bodySemiBold, color = colors.textPrimary)
        listOf(Triple(firstLabel, first, firstText) to colors.primary, Triple(secondLabel, second, secondText) to colors.learnAccent).forEach { (entry, color) ->
            val (label, value, text) = entry
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(label, modifier = Modifier.width(COMPARISON_LABEL), style = StockStepsTheme.typography.small, color = colors.textSecondary, maxLines = 1)
                Box(Modifier.weight(1f).height(StockStepsTheme.dimensions.rangeBar * 2)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((abs(value) / largest).toFloat().coerceIn(0.02f, 1f)).clip(StockStepsTheme.shapes.pill).background(color))
                }
                Text(text, style = StockStepsTheme.typography.numberLabelStrong, color = colors.textPrimary, maxLines = 1)
            }
        }
        Text(caption, style = StockStepsTheme.typography.caption, color = colors.textSecondary)
    }
}

/**
 * Metric × period table. Labels stay fixed; only the value columns scroll horizontally, inside
 * this section (the page itself keeps its single vertical scroll).
 */
@Composable
internal fun StockDataTable(columns: List<String>, rows: List<Pair<String, List<String>>>, labelHeader: String, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val rowHeight = StockStepsTheme.dimensions.touchTarget - spacing.sm
    Row(modifier.fillMaxWidth()) {
        Column(Modifier.width(TABLE_LABEL)) {
            Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                Text(labelHeader, style = typography.label, color = colors.textSecondary)
            }
            rows.forEach { (label, _) ->
                Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(label, style = typography.small, color = colors.textBody, maxLines = 2)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            columns.forEachIndexed { index, column ->
                Column(Modifier.width(TABLE_COLUMN)) {
                    Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.CenterEnd) {
                        Text(column, style = typography.label, color = colors.textSecondary)
                    }
                    rows.forEach { (label, values) ->
                        Box(
                            Modifier.fillMaxWidth().height(rowHeight).semantics(mergeDescendants = true) { contentDescription = "$label, $column: ${values[index]}" },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(values[index], style = typography.numberLabel, color = colors.textPrimary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private val CHART_HEIGHT = 140.dp
private val COMPARISON_LABEL = 72.dp
private val TABLE_LABEL = 128.dp
private val TABLE_COLUMN = 84.dp
