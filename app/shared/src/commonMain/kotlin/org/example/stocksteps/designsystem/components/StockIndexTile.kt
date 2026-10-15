package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * A compact market index tile (5D.5.1b): name, the percentage change as the loud value (arrow + sign via [StockPriceChange], never colour
 * alone), the level and the point change below, an optional state line. One spoken summary. Use only where `StockIndexTilesLayout.tiles` says
 * every piece fits (8 dp side padding, passed to the rule as `tilePadding`); otherwise show [StockIndexRow]. Nothing is truncated: names wrap between words; values are single tokens that fit by rule.
 */
@Composable
internal fun StockIndexTile(
    name: String,
    percent: String?,
    value: String,
    points: String?,
    direction: PriceDirection,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    stateLabel: String? = null,
    caution: Boolean = false,
    changeUnavailable: String = "Change unavailable"
) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    Column(
        modifier.clip(StockStepsTheme.shapes.card).background(colors.surface)
            .clearAndSetSemantics { contentDescription = accessibilityLabel }
            .padding(horizontal = spacing.sm, vertical = spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs)
    ) {
        Text(name, style = typography.label, color = colors.textSupporting)
        if (percent != null) StockPriceChange(percent, direction, style = typography.numberMedium, maxLines = Int.MAX_VALUE)
        else Text(changeUnavailable, style = typography.caption, color = colors.textMeta)
        Text(value, style = typography.numberLabelStrong, color = colors.textValue)
        points?.let { Text(it, style = typography.numberLabel, color = colors.textSupporting) }
        stateLabel?.let { Text(it, style = typography.caption, color = if (caution) colors.cautionText else colors.textMeta) }
    }
}
