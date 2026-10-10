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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.model.MarketStatus
import org.example.stocksteps.presentation.companydetails.CompanyDetailsPresentation
import org.example.stocksteps.presentation.markets.MarketsScreenPresentation

/**
 * US market status chip from the backend's market-status flag (Phase 4C, Phase 4B.1 semantics): a dot — green while open, amber for
 * pre-market/after-hours, neutral when closed (closed is not a loss) — and the state in words, so colour is never the only signal.
 * Read-only. UNKNOWN renders nothing: market hours are never guessed from the device clock.
 */
@Composable
internal fun MarketStatusIndicator(status: MarketStatus, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val label = CompanyDetailsPresentation.statusLabel(status) ?: return
    val description = CompanyDetailsPresentation.statusDescription(status) ?: label
    Row(
        modifier = modifier.clip(StockStepsTheme.shapes.pill).background(colors.surfaceSecondary)
            .padding(horizontal = spacing.sm, vertical = spacing.xxs)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Box(Modifier.size(spacing.sm).clip(CircleShape).background(
            when (CompanyDetailsPresentation.statusDot(status)) {
                MarketsScreenPresentation.StatusDot.OPEN -> colors.positive
                MarketsScreenPresentation.StatusDot.EXTENDED -> colors.caution
                MarketsScreenPresentation.StatusDot.NEUTRAL -> colors.textDisabled
            }
        ))
        Text(label, style = StockStepsTheme.typography.label, color = colors.textSupporting, maxLines = 1)
    }
}
