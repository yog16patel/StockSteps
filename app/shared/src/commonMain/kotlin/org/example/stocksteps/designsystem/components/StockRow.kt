package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.home.StockRowUiModel

/**
 * Shared stock/company row for movers, watchlist, search results and holdings:
 * logo · ticker/company · optional sparkline · price over change.
 * `compact` is the dense list treatment; `trailing` replaces the change/price column.
 */
@Composable
internal fun StockRow(
    symbol: String,
    name: String?,
    modifier: Modifier = Modifier,
    price: String? = null,
    change: String? = null,
    direction: PriceDirection = PriceDirection.UNAVAILABLE,
    logoUrl: String? = null,
    compact: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = StockStepsTheme.spacing.cardPadding,
        vertical = StockStepsTheme.spacing.sm
    ),
    onClick: (() -> Unit)? = null,
    sparkline: (@Composable () -> Unit)? = null,
    trailing: (@Composable ColumnScope.() -> Unit)? = null
) {
    val spacing = StockStepsTheme.spacing
    val dimensions = StockStepsTheme.dimensions
    val typography = StockStepsTheme.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) dimensions.rowCompactMinHeight else dimensions.rowMinHeight)
            // Clickable rows merge automatically; plain rows are merged too so TalkBack reads one row, not each text (Phase 2).
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier.semantics(mergeDescendants = true) {})
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        StockTickerAvatar(
            symbol = symbol,
            logoUrl = logoUrl,
            size = if (compact) dimensions.logoCompact else dimensions.logo
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = symbol,
                style = typography.bodySemiBold,
                color = StockStepsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (name != null) {
                Text(
                    text = name,
                    style = if (compact) typography.caption else typography.small,
                    color = StockStepsTheme.colors.textSupporting,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (sparkline != null) {
            Box(Modifier.size(dimensions.sparklineWidth, dimensions.sparklineHeight)) { sparkline() }
        }
        Column(
            modifier = Modifier.padding(start = spacing.xs),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(spacing.xxs)
        ) {
            if (trailing != null) {
                trailing()
            } else {
                if (price != null) {
                    Text(
                        text = price,
                        style = typography.numberLabelStrong,
                        color = StockStepsTheme.colors.textValue,
                        maxLines = 1
                    )
                }
                if (change != null) {
                    StockPriceChange(percentage = change, direction = direction, style = typography.numberLabel)
                }
            }
        }
    }
}

@Composable
internal fun StockRow(
    model: StockRowUiModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = StockStepsTheme.spacing.cardPadding,
        vertical = StockStepsTheme.spacing.sm
    ),
    onClick: (() -> Unit)? = null
) {
    StockRow(
        symbol = model.symbol,
        name = model.name,
        modifier = modifier,
        price = model.price,
        change = model.change,
        direction = model.direction,
        compact = compact,
        contentPadding = contentPadding,
        logoUrl = model.logoUrl,
        onClick = onClick,
        sparkline = model.sparkline?.let { closes -> { StockSparkline(closes, model.direction) } }
    )
}

/** Logo on a light tile once it loads; until then, or if it fails, the ticker stands in. */
@Composable
internal fun StockTickerAvatar(
    symbol: String,
    modifier: Modifier = Modifier,
    logoUrl: String? = null,
    size: Dp = StockStepsTheme.dimensions.logo
) {
    // Decorative: the row already names the ticker, and the fixed-size tile keeps its text unscaled.
    val tiny = StockStepsTheme.typography.tiny
    val fontScale = LocalDensity.current.fontScale
    var loaded by remember(logoUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(StockStepsTheme.shapes.chip)
            .background(if (loaded) StockStepsTheme.colors.logoContainer else StockStepsTheme.colors.surfaceSecondary)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Text(
                text = symbol.substringBefore('.').take(AVATAR_LETTERS),
                style = tiny.copy(fontSize = tiny.fontSize / fontScale),
                color = StockStepsTheme.colors.textSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.matchParentSize().padding(StockStepsTheme.spacing.xs)
            )
        }
    }
}

/** Placeholder shaped like [StockRow] so loading lists keep their final height. */
@Composable
internal fun StockRowSkeleton(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = StockStepsTheme.spacing.cardPadding,
        vertical = StockStepsTheme.spacing.sm
    )
) {
    val spacing = StockStepsTheme.spacing
    val dimensions = StockStepsTheme.dimensions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) dimensions.rowCompactMinHeight else dimensions.rowMinHeight)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        StockSkeleton(Modifier.size(if (compact) dimensions.logoCompact else dimensions.logo), shape = StockStepsTheme.shapes.chip)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StockSkeleton(Modifier.width(SKELETON_TICKER))
            StockSkeleton(Modifier.width(SKELETON_NAME))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StockSkeleton(Modifier.width(SKELETON_PRICE))
            StockSkeleton(Modifier.width(SKELETON_CHANGE))
        }
    }
}

private const val AVATAR_LETTERS = 4
private val SKELETON_TICKER = 44.dp
private val SKELETON_NAME = 110.dp
private val SKELETON_PRICE = 56.dp
private val SKELETON_CHANGE = 44.dp
