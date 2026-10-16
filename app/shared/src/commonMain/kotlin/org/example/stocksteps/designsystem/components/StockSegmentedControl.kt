package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import org.example.stocksteps.designsystem.theme.StockStepsTheme

internal data class StockSegment<T>(val value: T, val label: String, val icon: ImageVector? = null)

/**
 * Equal-width single-choice options (Light | Dark | System, Mock Data | Real Data). Selection is
 * informational blue, never positive/negative. Large text stacks the icon above the label.
 */
@Composable
internal fun <T> StockSegmentedControl(
    options: List<StockSegment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Portfolio Phase C: keep a small icon beside the label at every text size (the label wraps) instead of stacking it above. */
    compactIcons: Boolean = false,
    /** 5D.5.6C4: options are nested tabs (announced as tabs with their selected state) instead of a single choice. */
    asTabs: Boolean = false,
    /** 5D.5.6D: when a label can't fit its equal share (shared `StockPillLayout.fitsEqualWidth`, e.g. four tabs at 2.0×), the options keep
     *  their natural width on one line and scroll horizontally with the shared fade instead of breaking inside words. Text-only options. */
    scrollWhenTight: Boolean = false
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val shape = StockStepsTheme.shapes.button
    val density = LocalDensity.current
    val stacked = !compactIcons && density.fontScale > StockStepsTheme.dimensions.largeFontScale
    val selectedStyle = StockStepsTheme.typography.label.copy(fontWeight = FontWeight.SemiBold)
    val segment: @Composable (StockSegment<T>, Modifier, Boolean) -> Unit = { option, base, natural ->
        val isSelected = option.value == selected
        val content = if (isSelected) colors.primaryText else colors.textSecondary
        val body: @Composable () -> Unit = {
            option.icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(if (compactIcons) StockStepsTheme.dimensions.icon else StockStepsTheme.dimensions.iconSmall)) }
            Text(option.label, style = if (isSelected) selectedStyle else StockStepsTheme.typography.label,
                color = content, textAlign = TextAlign.Center, softWrap = !natural)
        }
        val itemModifier = base
            .heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .clip(shape)
            .background(if (isSelected) colors.primaryContainer else colors.surfaceSecondary)
            .border(StockStepsTheme.dimensions.border, if (isSelected) colors.primary else colors.borderSubtle, shape)
            .selectable(selected = isSelected, role = if (asTabs) Role.Tab else Role.RadioButton, onClick = { onSelect(option.value) })
            .padding(horizontal = if (natural) spacing.md else spacing.xs, vertical = spacing.sm)
        if (stacked && !natural) {
            Column(itemModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterVertically)) { body() }
        } else {
            Row(itemModifier, horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) { body() }
        }
    }
    if (scrollWhenTight) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
            val widths = options.map { with(density) { measurer.measure(it.label, selectedStyle, softWrap = false).size.width.toDp().value } }
            val share = maxWidth.value - spacing.sm.value * (options.size - 1)
            if (!org.example.stocksteps.theme.StockPillLayout.fitsEqualWidth(widths, share, spacing.xs.value)) {
                StockFadingScrollRow(options, options.firstOrNull { it.value == selected }, Modifier.selectableGroup(), spacing.sm) { option, itemModifier ->
                    segment(option, itemModifier, true)
                }
                return@BoxWithConstraints
            }
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                options.forEach { segment(it, Modifier.weight(1f), false) }
            }
        }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        options.forEach { option -> segment(option, Modifier.weight(1f), false) }
    }
}
