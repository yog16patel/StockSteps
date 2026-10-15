package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.search.SearchDisplay
import org.example.stocksteps.theme.StockBadgeKind
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.designsystem.icons.StockIcons

/** Font scale at which a search row stacks ticker and exchange and puts the avatar above the text (as Learn's very-large layout). */
internal const val SEARCH_ROW_STACK_SCALE = 1.75f

/**
 * One company search result (Phase 5D.5.5B): ticker-initials avatar, the name (wraps, never truncated) with an optional "Best match" badge,
 * then ticker · exchange (never truncated — they tell listings apart), and a chevron. Borderless; the caller draws dividers. One spoken
 * element ("Apple Inc., AAPL, NASDAQ, best match") with a button role and [actionLabel]. At very large text the avatar goes above the text and
 * ticker and exchange take their own lines.
 */
@Composable
internal fun StockSearchResultRow(
    result: StockSearchResult,
    best: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val stacked = LocalDensity.current.fontScale >= SEARCH_ROW_STACK_SCALE
    val spoken = SearchDisplay.spoken(result, best)
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            StockTitleWithBadge(
                title = { Text(result.name, style = StockStepsTheme.typography.bodySemiBold, color = colors.textPrimary) },
                badge = { if (best) StockStatusBadge(SearchDisplay.BEST_MATCH, StockBadgeKind.INFO, size = StockBadgeSize.COMPACT) }
            )
            if (stacked) {
                Text(result.symbol, style = StockStepsTheme.typography.small, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                result.exchange?.takeIf { it.isNotBlank() }?.let { Text(it, style = StockStepsTheme.typography.small, color = colors.textSecondary) }
            } else {
                Text(SearchDisplay.identity(result), style = StockStepsTheme.typography.small, color = colors.textSecondary)
            }
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = actionLabel) { onClick(); true }
            }
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        if (stacked) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StockTickerAvatar(result.symbol)
                text()
            }
        } else {
            StockTickerAvatar(result.symbol)
            Column(Modifier.weight(1f)) { text() }
        }
        Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
    }
}
