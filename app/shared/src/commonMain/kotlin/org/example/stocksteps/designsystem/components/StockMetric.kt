package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * One metric: label, value, short context. Direction (if any) adds an arrow and semantic color;
 * metrics without direction stay neutral. Tappable metrics open an explanation.
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
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)
    ) {
        Text(label, style = StockStepsTheme.typography.label, color = colors.textSecondary)
        if (direction != null && direction != PriceDirection.UNAVAILABLE) {
            StockPriceChange(value, direction, style = StockStepsTheme.typography.numberEmphasis)
        } else {
            Text(value, style = StockStepsTheme.typography.numberEmphasis, color = colors.textPrimary)
        }
        if (helper != null) Text(helper, style = StockStepsTheme.typography.caption, color = colors.textTertiary)
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
