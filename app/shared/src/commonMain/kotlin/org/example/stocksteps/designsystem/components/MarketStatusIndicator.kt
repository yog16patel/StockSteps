package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.model.MarketStatus
import org.example.stocksteps.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * "● Market Open" style status from the backend's market-status flag. The dot carries the
 * semantic color; the label always names the state, so color is never the only signal.
 * UNKNOWN renders nothing: market hours are never guessed from the device clock.
 */
@Composable
internal fun MarketStatusIndicator(status: MarketStatus, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val (dot, label, spoken) = when (status) {
        MarketStatus.OPEN -> Triple(colors.positive, Res.string.home_market_status_open, Res.string.market_status_open_a11y)
        MarketStatus.CLOSED -> Triple(colors.negative, Res.string.home_market_status_closed, Res.string.market_status_closed_a11y)
        MarketStatus.PRE_MARKET -> Triple(colors.primary, Res.string.home_market_status_pre, Res.string.market_status_pre_a11y)
        MarketStatus.AFTER_HOURS -> Triple(colors.primary, Res.string.home_market_status_after, Res.string.market_status_after_a11y)
        MarketStatus.UNKNOWN -> return
    }
    val description = stringResource(spoken)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)
    ) {
        Box(Modifier.size(StockStepsTheme.dimensions.statusDot).background(dot, CircleShape))
        Text(stringResource(label), style = StockStepsTheme.typography.label, color = colors.textSecondary, maxLines = 1)
    }
}
