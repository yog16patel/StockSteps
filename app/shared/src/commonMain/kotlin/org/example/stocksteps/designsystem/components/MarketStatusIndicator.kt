package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
 * "Market Open [switch]" status from the backend's market-status flag. The switch is a
 * read-only display (on only during the regular session) and is never focusable or tappable;
 * the label always names the state, so neither color nor switch position is the only signal.
 * UNKNOWN renders nothing: market hours are never guessed from the device clock.
 */
@Composable
internal fun MarketStatusIndicator(status: MarketStatus, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val (label, spoken) = when (status) {
        MarketStatus.OPEN -> Res.string.home_market_status_open to Res.string.market_status_open_a11y
        MarketStatus.CLOSED -> Res.string.home_market_status_closed to Res.string.market_status_closed_a11y
        MarketStatus.PRE_MARKET -> Res.string.home_market_status_pre to Res.string.market_status_pre_a11y
        MarketStatus.AFTER_HOURS -> Res.string.home_market_status_after to Res.string.market_status_after_a11y
        MarketStatus.UNKNOWN -> return
    }
    val description = stringResource(spoken)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)
    ) {
        Text(stringResource(label), style = StockStepsTheme.typography.label, color = colors.textSecondary, maxLines = 1)
        MarketStatusSwitch(status)
    }
}

/**
 * Read-only mini switch: green with the thumb right when open, red with it left when closed,
 * grey with it left for pre-market/after hours.
 */
@Composable
private fun MarketStatusSwitch(status: MarketStatus) {
    val colors = StockStepsTheme.colors
    val dimensions = StockStepsTheme.dimensions
    val on = status == MarketStatus.OPEN
    val track = when (status) {
        MarketStatus.OPEN -> colors.positive
        MarketStatus.CLOSED -> colors.negative
        else -> colors.textDisabled
    }
    Box(
        modifier = Modifier
            .size(dimensions.statusSwitchWidth, dimensions.statusSwitchHeight)
            .background(track, CircleShape)
            .padding(horizontal = (dimensions.statusSwitchHeight - dimensions.statusSwitchThumb) / 2),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(dimensions.statusSwitchThumb).background(colors.onPrimary, CircleShape))
    }
}
