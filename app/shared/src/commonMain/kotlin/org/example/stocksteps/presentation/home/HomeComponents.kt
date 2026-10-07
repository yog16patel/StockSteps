package org.example.stocksteps.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.stocksteps.companydetail.SectionStatus
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.home.MarketIndexUiModel
import org.example.stocksteps.home.MarketSnapshotUiModel
import org.example.stocksteps.home.MoverCategory
import org.example.stocksteps.home.MoversUiModel
import org.example.stocksteps.news.NewsUiModel
import org.example.stocksteps.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Brand row, then greeting and tagline. No bell: notifications do not exist yet. */
@Composable
internal fun HomeHeader(signedIn: Boolean, modifier: Modifier = Modifier) {
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    Column(modifier = modifier) {
        StockBrandMark(stringResource(Res.string.home_brand))
        Text(
            text = stringResource(if (signedIn) Res.string.home_greeting_signed_in else Res.string.home_greeting_guest),
            modifier = Modifier.padding(top = spacing.lg),
            style = typography.sectionTitle.copy(fontWeight = FontWeight.Bold),
            color = StockStepsTheme.colors.textPrimary
        )
        Text(
            text = stringResource(Res.string.home_tagline),
            modifier = Modifier.padding(top = spacing.xs),
            style = typography.label.copy(fontWeight = FontWeight.Normal),
            color = StockStepsTheme.colors.textSecondary
        )
    }
}

/** "Market" header with the backend status, two compact index cards, and the ETF-proxy disclosure. */
@Composable
internal fun MarketSummarySection(market: MarketSnapshotUiModel, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = StockStepsTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        StockSectionHeader(
            title = stringResource(Res.string.home_market_title),
            trailing = { MarketStatusIndicator(market.marketStatus) }
        )
        if (market.status == SectionStatus.ERROR) {
            StockCard { StockErrorState(stringResource(Res.string.home_market_unavailable), onRetry) }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            market.indices.forEach { index ->
                MarketSummaryCard(index, loading = market.status == SectionStatus.LOADING, modifier = Modifier.weight(1f))
            }
        }
        Text(
            text = stringResource(Res.string.home_market_footnote, market.indices.joinToString(", ") { it.symbol }),
            style = StockStepsTheme.typography.tiny,
            color = StockStepsTheme.colors.textTertiary
        )
        if (market.partiallyUnavailable) {
            StockErrorState(stringResource(Res.string.home_market_partial), onRetry)
        }
    }
}

/** Borderless white card: name, price, change, and the session trend line when available. */
@Composable
private fun MarketSummaryCard(index: MarketIndexUiModel, loading: Boolean, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    StockCard(
        modifier = modifier.semantics(mergeDescendants = true) {},
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
        bordered = false
    ) {
        Text(
            text = index.name,
            style = typography.bodySemiBold,
            color = StockStepsTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (loading) {
            StockLoadingState(Modifier.padding(top = spacing.xs)) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    StockSkeleton(Modifier.width(SKELETON_WIDTH * 2))
                    StockSkeleton(Modifier.width(SKELETON_WIDTH))
                }
            }
            return@StockCard
        }
        Row(Modifier.padding(top = spacing.xxs), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(
                    text = index.price ?: "—",
                    style = typography.numberEmphasis,
                    color = if (index.price != null) StockStepsTheme.colors.textPrimary else StockStepsTheme.colors.textTertiary,
                    maxLines = 1
                )
                StockPriceChange(index.change, index.direction, style = typography.numberLabelStrong)
            }
            index.sparkline?.let { closes ->
                Box(Modifier.size(StockStepsTheme.dimensions.sparklineWidth, StockStepsTheme.dimensions.sparklineHeight)) {
                    StockSparkline(closes, index.direction)
                }
            }
        }
    }
}

/**
 * One borderless white section: header, pill filters and up to three rows split by dividers.
 * "View All" appears only when a destination exists ([onViewAll] non-null).
 */
@Composable
internal fun MoversSection(
    movers: MoversUiModel,
    onSelect: (MoverCategory) -> Unit,
    onOpenStock: (String) -> Unit,
    onRetry: () -> Unit,
    onViewAll: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val rowPadding = PaddingValues(vertical = StockStepsTheme.spacing.xs)
    // No extra gaps around the chips: their 48dp touch targets already space the 34dp visuals.
    StockCard(modifier = modifier, bordered = false) {
        StockSectionHeader(
            title = stringResource(Res.string.home_movers_title),
            actionText = onViewAll?.let { stringResource(Res.string.action_view_all) },
            onActionClick = onViewAll
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)
        ) {
            MoverCategory.entries.forEach { category ->
                StockChip(
                    text = stringResource(category.label()),
                    selected = movers.category == category,
                    onClick = { onSelect(category) }
                )
            }
        }
        when (movers.status) {
            SectionStatus.LOADING -> StockLoadingState {
                Column { repeat(LOADING_ROWS) { StockRowSkeleton(compact = true, contentPadding = rowPadding) } }
            }
            SectionStatus.ERROR -> StockErrorState(stringResource(Res.string.home_movers_unavailable), onRetry)
            SectionStatus.EMPTY -> StockEmptyState(stringResource(Res.string.home_movers_empty))
            SectionStatus.SUCCESS -> movers.rows.forEachIndexed { index, row ->
                if (index > 0) StockDivider(startIndent = StockStepsTheme.dimensions.logoCompact + StockStepsTheme.spacing.sm)
                StockRow(model = row, compact = true, contentPadding = rowPadding, onClick = { onOpenStock(row.symbol) })
            }
        }
    }
}

private fun MoverCategory.label(): StringResource = when (this) {
    MoverCategory.GAINERS -> Res.string.home_movers_gainers
    MoverCategory.LOSERS -> Res.string.home_movers_losers
    MoverCategory.MOST_ACTIVE -> Res.string.home_movers_active
}

/**
 * Inviting learning banner: soft gradient, decorative illustration, indigo title and a round
 * arrow cue. The whole banner is one button; Learn owns the content.
 */
@Composable
internal fun HomeLearnCard(onLearn: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val dimensions = StockStepsTheme.dimensions
    val shape = StockStepsTheme.shapes.cardLarge
    val action = stringResource(Res.string.home_learn_action)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(colors.learnContainerStart, colors.learnContainerEnd)))
            .clickable(onClickLabel = action, role = Role.Button, onClick = onLearn)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Image(
            painter = painterResource(Res.drawable.ill_learn_basics),
            contentDescription = null,
            modifier = Modifier.size(dimensions.learnIllustration)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = stringResource(Res.string.home_learn_title),
                style = StockStepsTheme.typography.cardTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.learnAccent
            )
            Text(
                text = stringResource(Res.string.home_learn_body),
                style = StockStepsTheme.typography.caption,
                color = colors.textBody
            )
        }
        Box(
            modifier = Modifier
                .size(dimensions.learnAction)
                .clip(CircleShape)
                .background(colors.learnAccent)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Icon(StockIcons.ArrowForward, contentDescription = null, tint = colors.onLearnAccent, modifier = Modifier.size(dimensions.iconSmall))
        }
    }
}

/** One borderless white section with up to three compact news rows separated by dividers. */
@Composable
internal fun HomeNewsSection(
    status: SectionStatus,
    news: List<NewsUiModel>,
    onOpenArticle: (String) -> Unit,
    onRetry: () -> Unit,
    onViewAll: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    StockCard(modifier = modifier, bordered = false) {
        StockSectionHeader(
            title = stringResource(Res.string.home_news_title),
            actionText = onViewAll?.let { stringResource(Res.string.action_view_all) },
            onActionClick = onViewAll
        )
        when (status) {
            SectionStatus.LOADING -> StockLoadingState { Column { repeat(NEWS_SKELETONS) { StockNewsCardSkeleton() } } }
            SectionStatus.ERROR -> StockErrorState(stringResource(Res.string.home_news_unavailable), onRetry)
            SectionStatus.EMPTY -> StockEmptyState(stringResource(Res.string.home_news_empty))
            SectionStatus.SUCCESS -> news.forEachIndexed { index, article ->
                if (index > 0) StockDivider()
                StockNewsCard(model = article, onClick = article.url?.let { url -> { onOpenArticle(url) } })
            }
        }
    }
}

private const val LOADING_ROWS = 3
private const val NEWS_SKELETONS = 2
private val SKELETON_WIDTH = 60.dp
