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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.search.SearchDisplay
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.designsystem.icons.StockIcons

/** Font scale at which a search row puts ticker and exchange on their own lines. */
internal const val SEARCH_ROW_STACK_SCALE = 1.75f

/**
 * One company search result (Phase 5D.5.5B): the company logo when it loads (5D.5.5C), else ticker initials, the name (wraps, never truncated),
 * then ticker · exchange (never truncated — they tell listings apart), and a chevron. Borderless; the caller draws dividers. One spoken
 * element ("Apple Inc., AAPL, NASDAQ, best match") with a button role and [actionLabel]. At very large text ticker and exchange take their
 * own lines; the logo stays on the left.
 *
 * Typography & Spacing Phase 5: name [listTitle] (16), ticker · exchange [small] (14), a 40 dp logo, 12 dp above and below and 4 dp between
 * the lines. The "Best match" badge is gone: the section heading already says it ([best] is still spoken, for a reader moving row by row).
 */
@Composable
internal fun StockSearchResultRow(
    result: StockSearchResult,
    best: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    /** 5D.5.5C (Comparison): false dims the row and ignores taps; [note] ("Already added") is shown and spoken after the identity line. */
    enabled: Boolean = true,
    note: String? = null
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val stacked = LocalDensity.current.fontScale >= SEARCH_ROW_STACK_SCALE
    val spoken = listOfNotNull(SearchDisplay.spoken(result, best), note).joinToString(", ")
    val nameColor = if (enabled) colors.textPrimary else colors.textTertiary
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.listTitleSubtitle)) {
            Text(result.name, style = StockStepsTheme.typography.listTitle, color = nameColor)
            if (stacked) {
                Text(result.symbol, style = StockStepsTheme.typography.small, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                result.exchange?.takeIf { it.isNotBlank() }?.let { Text(it, style = StockStepsTheme.typography.small, color = colors.textSecondary) }
            } else {
                Text(SearchDisplay.identity(result), style = StockStepsTheme.typography.small, color = colors.textSecondary)
            }
            note?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textTertiary) }
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .clickable(enabled = enabled, onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                if (enabled) onClick(label = actionLabel) { onClick(); true } else disabled()
            }
            .padding(vertical = spacing.listRowVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        // 5D.5.5D (owner reference): the logo stays beside the text at every size; at very large text only ticker and exchange stack.
        StockTickerAvatar(result.symbol, logoUrl = result.logoUrl, size = StockStepsTheme.dimensions.logoList)
        Column(Modifier.weight(1f)) { text() }
        if (enabled) Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
    }
}
