package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * A market index as one compact row: name and quote state on the left, value and signed change (arrow + words via [StockPriceChange]) on the
 * right, one spoken summary. At large font scale the values move under the name instead of squeezing it (Home 4A rule; shared with the Daily
 * Brief reader since 5D.5.1). [change] null shows [changeUnavailable]; values are already formatted and never estimated.
 */
@Composable
internal fun StockIndexRow(
    name: String,
    stateLabel: String,
    value: String,
    change: String?,
    direction: PriceDirection,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    caution: Boolean = false,
    note: String? = null,
    changeUnavailable: String = "Change unavailable"
) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val largeText = LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale
    val nameBlock: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(name, style = typography.bodySemiBold, color = colors.textTitle)
            Text(stateLabel, style = typography.caption, color = if (caution) colors.cautionText else colors.textMeta)
            note?.let { Text(it, style = typography.caption, color = colors.textMeta) }
        }
    }
    val values: @Composable (Alignment.Horizontal) -> Unit = { alignment ->
        Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(value, style = typography.numberLabelStrong, color = colors.textValue)
            if (change != null) StockPriceChange(change, direction, maxLines = 2)
            else Text(changeUnavailable, style = typography.caption, color = colors.textMeta)
        }
    }
    val rowModifier = modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.rowCompactMinHeight)
        .clearAndSetSemantics { contentDescription = accessibilityLabel }.padding(vertical = spacing.sm)
    if (largeText) {
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) { nameBlock(Modifier); values(Alignment.Start) }
    } else Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        nameBlock(Modifier.weight(1f)); values(Alignment.End)
    }
}
