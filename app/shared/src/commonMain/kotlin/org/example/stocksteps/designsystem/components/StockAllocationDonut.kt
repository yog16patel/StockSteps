package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.StockAllocationLayout
import org.example.stocksteps.theme.StockChartSeriesStyle

/**
 * Allocation ring (Portfolio Phase C). Slices follow [fractions] in order with colours from `StockChartSeriesStyle` ([colorIndexes]) — never
 * gain/loss colours. Decorative for screen readers: the legend rows carry every value. Callers draw it only for valid allocations
 * (`PortfolioDisplayRules.allocationDonutAllowed`); geometry comes from the shared `StockAllocationLayout`, as on iOS.
 */
@Composable
internal fun StockAllocationDonut(fractions: List<Float>, colorIndexes: List<Int>, size: Dp, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val palette = colors.palette
    val track = colors.surfaceSecondary
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val ring = this.size.minDimension * StockAllocationLayout.RING_FRACTION
        val inset = ring / 2
        val arcSize = Size(this.size.width - ring, this.size.height - ring)
        drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(ring))
        StockAllocationLayout.slices(fractions).forEachIndexed { i, (start, sweep) ->
            if (sweep > 0f) drawArc(colors.rgb(StockChartSeriesStyle.series(colorIndexes.getOrElse(i) { i }, palette).color), start, sweep,
                useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(ring, cap = StrokeCap.Butt))
        }
    }
}

/**
 * Allocation legend line: colour dot · label · percentage (strong) · amount (muted), one spoken [description]. The dot repeats the slice colour;
 * the label always names the category, so colour is never the only cue. Wraps on whole words at large text.
 */
@Composable
internal fun StockAllocationLegendRow(colorIndex: Int, label: String, percent: String, amount: String?, description: String, modifier: Modifier = Modifier,
                                      /** C.1: the group's decision (`allocationLegendStacked`) so all rows read the same way; null = decide alone. */
                                      stacked: Boolean? = null) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dot: @Composable () -> Unit = { Box(Modifier.size(10.dp).background(colors.rgb(StockChartSeriesStyle.series(colorIndex, colors.palette).color), CircleShape)) }
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        // C.1: the percentage and amount go under the label when the whole (short) label and the values can't share the line (shared
        // StockLayout.metricRowStacked) — "AAPL · / USD" broke at 2.0×.
        val word = measurer.measure(label, typography.small).size.width
        val values = maxOf(measurer.measure(percent, typography.numberLabelStrong).size.width, amount?.let { measurer.measure(it, typography.caption).size.width } ?: 0)
        val own = with(density) { org.example.stocksteps.theme.StockLayout.metricRowStacked(word.toDp().value + 18f, values.toDp().value, maxWidth.value, spacing.sm.value) }
        if (stacked ?: own) Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Box(Modifier.padding(top = 6.dp)) { dot() }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(label, style = typography.small, color = colors.textBody)
                Text(percent, style = typography.numberLabelStrong, color = colors.textValue)
                amount?.let { Text(it, style = typography.caption, color = colors.textSupporting) }
            }
        } else Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            dot()
            Text(label, Modifier.weight(1f), style = typography.small, color = colors.textBody)
            Column(horizontalAlignment = Alignment.End) {
                Text(percent, style = typography.numberLabelStrong, color = colors.textValue, textAlign = TextAlign.End)
                amount?.let { Text(it, style = typography.caption, color = colors.textSupporting, textAlign = TextAlign.End) }
            }
        }
    }
}

/** C.1: true when any legend line (label, percentage, amount) can't share one line at [width] dp — then every row of the group stacks. */
@Composable
internal fun allocationLegendStacked(lines: List<Triple<String, String, String?>>, width: Float): Boolean {
    val typography = StockStepsTheme.typography
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    return lines.any { (label, percent, amount) ->
        val l = measurer.measure(label, typography.small).size.width
        val v = maxOf(measurer.measure(percent, typography.numberLabelStrong).size.width, amount?.let { measurer.measure(it, typography.caption).size.width } ?: 0)
        with(density) { org.example.stocksteps.theme.StockLayout.metricRowStacked(l.toDp().value + 18f, v.toDp().value, width, StockStepsTheme.spacing.sm.value) }
    }
}
