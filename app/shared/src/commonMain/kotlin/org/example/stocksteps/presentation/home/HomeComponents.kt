package org.example.stocksteps.presentation.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.stocksteps.companydetail.SectionStatus
import org.example.stocksteps.designsystem.components.*
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
            modifier = Modifier.padding(top = spacing.sm),
            style = typography.sectionTitle.copy(fontWeight = FontWeight.Bold),
            color = StockStepsTheme.colors.textPrimary
        )
        Text(
            text = stringResource(Res.string.home_tagline),
            modifier = Modifier.padding(top = spacing.xxs),
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

@Composable
private fun MarketSummaryCard(index: MarketIndexUiModel, loading: Boolean, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    StockCard(
        modifier = modifier.semantics(mergeDescendants = true) {},
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = index.name,
                modifier = Modifier.weight(1f),
                style = StockStepsTheme.typography.label,
                color = StockStepsTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (loading) {
                StockSkeleton(Modifier.width(SKELETON_WIDTH))
            } else {
                StockPriceChange(index.change, index.direction, style = StockStepsTheme.typography.numberLabelStrong)
            }
        }
        if (loading) {
            StockLoadingState(Modifier.padding(top = spacing.xs)) { StockSkeleton(Modifier.width(SKELETON_WIDTH * 2)) }
        } else {
            Text(
                text = index.price ?: "—",
                modifier = Modifier.padding(top = spacing.xxs),
                style = StockStepsTheme.typography.numberEmphasis,
                color = if (index.price != null) StockStepsTheme.colors.textPrimary else StockStepsTheme.colors.textTertiary,
                maxLines = 1
            )
        }
    }
}

/**
 * One section surface: header, filter chips and up to three flat rows (no per-row cards).
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
    // No extra gaps around the chips: their 48dp touch targets already space the 28dp visuals.
    StockCard(modifier = modifier) {
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

/** Compact, warm learning entry point; Learn owns the content. */
@Composable
internal fun HomeLearnCard(onLearn: () -> Unit, modifier: Modifier = Modifier) {
    StockInsightCard(
        title = stringResource(Res.string.home_learn_title),
        body = stringResource(Res.string.home_learn_body),
        tone = InsightTone.EDUCATION,
        actionText = stringResource(Res.string.home_learn_action),
        onClick = onLearn,
        modifier = modifier,
        leading = {
            Icon(
                painter = painterResource(Res.drawable.ic_learn),
                contentDescription = null,
                modifier = Modifier.padding(StockStepsTheme.spacing.sm)
            )
        }
    )
}

/** One section surface with up to three compact news rows separated by dividers. */
@Composable
internal fun HomeNewsSection(
    status: SectionStatus,
    news: List<NewsUiModel>,
    onOpenArticle: (String) -> Unit,
    onRetry: () -> Unit,
    onViewAll: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    StockCard(modifier = modifier) {
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
private val SKELETON_WIDTH = 48.dp
