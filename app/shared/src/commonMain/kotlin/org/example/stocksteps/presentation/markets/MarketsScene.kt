package org.example.stocksteps.presentation.markets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.markets.*
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.resources.*
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter
import org.jetbrains.compose.resources.stringResource

internal sealed interface MarketsAction {
    data object Refresh : MarketsAction
    data object DailyBrief : MarketsAction
    data object BriefHistory : MarketsAction
    data object Retry : MarketsAction
    data object Search : MarketsAction
    data class SelectTab(val tab: MoversTab) : MarketsAction
    data object ToggleExpanded : MarketsAction
    data class OpenStock(val symbol: String) : MarketsAction
    data class WhyMoved(val symbol: String) : MarketsAction
    data class OpenUrl(val url: String) : MarketsAction
    data object Discover : MarketsAction
    data object Compare : MarketsAction
    data object Earnings : MarketsAction
}

/** Owns the Markets ViewModel (keyed by environment so Mock and Real never share data) and wires navigation. */
@Composable
internal fun MarketsScene(
    backend: BackendRouter,
    environment: BackendEnvironment,
    hinge: WindowHinge?,
    onOpenStock: (String) -> Unit,
    onWhyMoved: (String) -> Unit,
    onSearch: () -> Unit,
    onDiscover: () -> Unit = {},
    onCompare: () -> Unit = {},
    onEarnings: () -> Unit = {},
    /** Shared Daily Market Brief presenter (null without an account graph, e.g. previews). */
    brief: org.example.stocksteps.brief.DailyBriefPresenter? = null,
    onDailyBrief: () -> Unit = {},
    onBriefHistory: () -> Unit = {},
    /** Account graph for the watchlist count on the Earnings Center entry (null when unavailable). */
    accounts: org.example.stocksteps.di.AccountDependencies? = null
) {
    val model = viewModel(key = "markets:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        MarketsViewModel(data.getMarketsOverview(), data::close)
    }
    val earnings = viewModel(key = "earnings-summary:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        org.example.stocksteps.presentation.earnings.EarningsSummaryViewModel(data.earningsRemote(accounts), accounts, data::close)
    }
    val state by model.state.collectAsStateWithLifecycle()
    val earningsState by earnings.presenter.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val briefState = brief?.state?.collectAsStateWithLifecycle()?.value
    MarketsScreen(state, hinge, briefState, earningsState) { action ->
        when (action) {
            MarketsAction.Refresh -> { model.refresh(); earnings.presenter.refresh() }
            MarketsAction.Retry -> model.retry()
            MarketsAction.Search -> onSearch()
            is MarketsAction.SelectTab -> model.selectTab(action.tab)
            MarketsAction.ToggleExpanded -> model.toggleExpanded()
            is MarketsAction.OpenStock -> onOpenStock(action.symbol)
            is MarketsAction.WhyMoved -> onWhyMoved(action.symbol)
            is MarketsAction.OpenUrl -> runCatching { uriHandler.openUri(action.url) }
            MarketsAction.Discover -> onDiscover()
            MarketsAction.Compare -> onCompare()
            MarketsAction.Earnings -> onEarnings()
            MarketsAction.DailyBrief -> onDailyBrief()
            MarketsAction.BriefHistory -> onBriefHistory()
        }
    }
}

/**
 * Markets dashboard: header and session, index cards, top movers (gainers / losers / most active),
 * sector performance (ETF proxies), market news and a daily lesson. Sections fail independently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketsScreen(
    state: MarketsState,
    hinge: WindowHinge?,
    brief: org.example.stocksteps.brief.DailyBriefUiState? = null,
    earnings: org.example.stocksteps.earnings.EarningsSummaryState? = null,
    onAction: (MarketsAction) -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val model = remember(state.overview, state.tab, state.expanded, state.loadedAt) { state.model }
    val listState = rememberLazyListState()
    var lesson by remember { mutableStateOf<MarketLesson?>(null) }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    val section = content.padding(top = spacing.xl)
    Box(Modifier.fillMaxSize().background(colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onAction(MarketsAction.Refresh) }, modifier = region) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "title") { Title(content.padding(top = spacing.lg), onSearch = { onAction(MarketsAction.Search) }) }
                    if (brief != null) item(key = "daily-brief") {
                        org.example.stocksteps.presentation.brief.DailyBriefPreviewCard(brief, org.example.stocksteps.presentation.brief.briefNow(),
                            { onAction(MarketsAction.DailyBrief) }, content.padding(top = spacing.xl), onHistory = { onAction(MarketsAction.BriefHistory) })
                    }
                    item(key = "tools") { ResearchTools(content.padding(top = spacing.xl), earnings, onAction) }
                    if (model == null) {
                        item(key = "state") {
                            if (state.loading) LoadingSkeleton(content.padding(top = spacing.lg))
                            else StockCard(content.padding(top = spacing.lg), bordered = false) {
                                StockErrorState(state.error ?: stringResource(Res.string.markets_unavailable), { onAction(MarketsAction.Retry) })
                            }
                        }
                        return@LazyColumn
                    }
                    item(key = "header") { SessionHeader(model.header, content.padding(top = spacing.xl)) }
                    item(key = "indices") {
                        Indices(model, onOpen = { id -> lesson = MarketEducation.index(id) }, onRetry = { onAction(MarketsAction.Retry) }, modifier = content.padding(top = spacing.lg))
                    }
                    item(key = "movers") { Movers(model.movers, onAction, section) }
                    item(key = "sectors") {
                        Sectors(model.sectors, onOpen = { row -> lesson = MarketEducation.sector(row.sector, row.symbol, model.sectors.methodology) },
                            onRetry = { onAction(MarketsAction.Retry) }, modifier = section)
                    }
                    item(key = "news") { News(model, onAction, section) }
                    item(key = "lesson") {
                        StockInsightCard(
                            title = model.lesson.title,
                            body = model.lesson.body,
                            eyebrow = stringResource(Res.string.markets_lesson_eyebrow),
                            tone = InsightTone.EDUCATION,
                            actionText = stringResource(Res.string.details_learn_more),
                            onClick = { lesson = model.lesson },
                            modifier = section
                        )
                    }
                    item(key = "disclaimer") {
                        Text(stringResource(Res.string.details_disclaimer), content.padding(top = spacing.lg),
                            style = StockStepsTheme.typography.caption, color = colors.textTertiary)
                    }
                }
            }
        }
    }
    lesson?.let { open -> LessonSheet(open) { lesson = null } }
}

@Composable
private fun Title(modifier: Modifier, onSearch: () -> Unit) {
    val colors = StockStepsTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            Text(stringResource(Res.string.markets_title), Modifier.semantics { heading() }, style = StockStepsTheme.typography.screenTitle, color = colors.textPrimary)
            Text(stringResource(Res.string.markets_subtitle), style = StockStepsTheme.typography.small, color = colors.textSecondary)
        }
        IconButton(onClick = onSearch) {
            Icon(StockIcons.Search, contentDescription = stringResource(Res.string.markets_search), tint = colors.iconPrimary)
        }
    }
}

/** Market Overview (this screen) plus entry points to Discover Stocks and Compare Stocks. */
@Composable
private fun ResearchTools(modifier: Modifier, earnings: org.example.stocksteps.earnings.EarningsSummaryState?, onAction: (MarketsAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        listOf(
            Triple("Discover Stocks", "Find companies by growth, dividends, strength or valuation", MarketsAction.Discover),
            Triple("Compare Companies", "See two or three companies side by side", MarketsAction.Compare)
        ).forEach { (title, subtitle, action) ->
            StockCard(Modifier.weight(1f).fillMaxHeight(), onClick = { onAction(action) }, onClickLabel = "Open $title") {
                Icon(if (action == MarketsAction.Discover) StockIcons.Search else StockIcons.PieChart, contentDescription = null, tint = colors.primary)
                Text(title, Modifier.padding(top = spacing.sm), style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
                Text(subtitle, Modifier.padding(top = spacing.xxs), style = StockStepsTheme.typography.caption, color = colors.textSecondary)
            }
        }
    }
    // Earnings Center: counts only from the calendar (never estimated); a plain fallback otherwise.
    StockCard(Modifier.fillMaxWidth(), onClick = { onAction(MarketsAction.Earnings) }, onClickLabel = "View Earnings Calendar") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Icon(StockIcons.TrendingUp, contentDescription = null, tint = colors.primary)
            Column(Modifier.weight(1f)) {
                Text("Earnings Center", Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
                Text("See when companies are reporting results.", Modifier.padding(top = spacing.xxs), style = StockStepsTheme.typography.caption, color = colors.textSecondary)
                (earnings?.headline ?: if (earnings?.loading == false) "Open the calendar to browse upcoming and reported earnings." else null)?.let {
                    Text(it, Modifier.padding(top = spacing.xxs), style = StockStepsTheme.typography.small, color = colors.textPrimary)
                }
                earnings?.watchlistText?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.primaryText) }
                Text("View Earnings Calendar", Modifier.padding(top = spacing.xs), style = StockStepsTheme.typography.label, color = colors.primaryText)
            }
        }
    }
    }
}

@Composable
private fun SessionHeader(header: MarketHeaderModel, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Box(Modifier.size(StockStepsTheme.spacing.sm).clip(androidx.compose.foundation.shape.CircleShape).background(
                when (header.tone) { SessionTone.OPEN -> colors.positive; SessionTone.EXTENDED -> colors.caution; SessionTone.CLOSED -> colors.negative; SessionTone.UNKNOWN -> colors.textDisabled }
            ))
            Text(header.statusLabel, style = StockStepsTheme.typography.bodySemiBold, color = colors.textPrimary)
            header.detail?.let { Text("· $it", style = StockStepsTheme.typography.small, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text(listOfNotNull(header.updated, header.notice).joinToString(" · "),
            style = StockStepsTheme.typography.caption, color = if (header.sampleData) colors.cautionText else colors.textSecondary)
    }
}

@Composable
private fun Indices(model: MarketsUiModel, onOpen: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    if (model.indicesFailed || model.indices.isEmpty()) {
        StockCard(modifier, bordered = false) { StockErrorState(stringResource(Res.string.markets_indices_failed), onRetry) }
        return
    }
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        items(model.indices, key = { it.id }) { card -> IndexCard(card, onClick = { onOpen(card.id) }) }
    }
}

@Composable
private fun IndexCard(card: IndexCardModel, onClick: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    StockCard(
        modifier = Modifier.width(StockStepsTheme.dimensions.indexCardWidth).clearAndSetSemantics { contentDescription = card.accessibilityLabel },
        onClick = onClick,
        bordered = false,
        contentPadding = PaddingValues(spacing.md)
    ) {
        Text(card.name, style = StockStepsTheme.typography.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(card.value, Modifier.padding(top = spacing.xxs), style = StockStepsTheme.typography.numberEmphasis, color = colors.textPrimary, maxLines = 1)
        if (card.available) {
            StockPriceChange(card.percent, card.direction, amount = card.change)
            if (card.trend.size >= 2) StockSparkline(card.trend, card.direction, Modifier.padding(top = spacing.xs).fillMaxWidth().height(StockStepsTheme.dimensions.sparklineHeight))
        } else {
            Text(stringResource(Res.string.markets_index_unavailable), style = StockStepsTheme.typography.caption, color = colors.textTertiary)
        }
        card.proxyLabel?.let { Text(it, Modifier.padding(top = spacing.xs), style = StockStepsTheme.typography.tiny, color = colors.cautionText, maxLines = 1) }
        card.updated?.let { Text(it, Modifier.padding(top = spacing.xs), style = StockStepsTheme.typography.tiny, color = colors.textTertiary, maxLines = 1) }
    }
}

@Composable
private fun Movers(movers: MoversModel, onAction: (MarketsAction) -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(stringResource(Res.string.markets_movers_title))
        StockPillSelector(MoversTab.entries, movers.tab, label = { it.label }, onSelect = { onAction(MarketsAction.SelectTab(it)) })
        StockCard(bordered = false, contentPadding = PaddingValues(vertical = spacing.xxs)) {
            when {
                movers.failed -> StockErrorState(stringResource(Res.string.markets_section_failed), { onAction(MarketsAction.Retry) }, Modifier.padding(spacing.md))
                movers.emptyMessage != null -> StockEmptyState(movers.emptyMessage!!, Modifier.padding(spacing.md))
                else -> movers.rows.forEachIndexed { index, mover ->
                    if (index > 0) StockDivider(startIndent = spacing.cardPadding)
                    MoverRow(mover, movers.tab, onAction)
                }
            }
        }
        Text("${movers.rankedBy}. ${movers.universe}.", style = StockStepsTheme.typography.caption, color = colors.textTertiary)
        if (!movers.failed && movers.total > MarketsPresenter.PREVIEW_ROWS) {
            Text(
                if (movers.expanded) stringResource(Res.string.markets_show_fewer) else stringResource(Res.string.markets_show_all, movers.total),
                Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).wrapContentHeight()
                    .clickable(role = Role.Button) { onAction(MarketsAction.ToggleExpanded) },
                style = StockStepsTheme.typography.label, color = colors.primaryText
            )
        }
    }
}

@Composable
private fun MoverRow(mover: MoverRowModel, tab: MoversTab, onAction: (MarketsAction) -> Unit) {
    val row = mover.row
    Row(verticalAlignment = Alignment.CenterVertically) {
        StockRow(
            symbol = row.symbol,
            name = row.name,
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = mover.accessibilityLabel },
            logoUrl = row.logoUrl,
            onClick = { onAction(MarketsAction.OpenStock(row.symbol)) },
            trailing = {
                row.price?.let { Text(it, style = StockStepsTheme.typography.numberLabelStrong, color = StockStepsTheme.colors.textPrimary, maxLines = 1) }
                StockPriceChange(row.change, row.direction)
                if (tab == MoversTab.MOST_ACTIVE) mover.volume?.let { Text(it, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary) }
            }
        )
        if (tab != MoversTab.MOST_ACTIVE && row.direction != PriceDirection.UNAVAILABLE) {
            val label = stringResource(Res.string.markets_why_moved, row.symbol)
            IconButton(onClick = { onAction(MarketsAction.WhyMoved(row.symbol)) }) {
                Icon(StockIcons.Help, contentDescription = label, tint = StockStepsTheme.colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
            }
        }
    }
}

@Composable
private fun Sectors(sectors: SectorsModel, onOpen: (SectorRowModel) -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(stringResource(Res.string.markets_sectors_title))
        StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs)) {
            when {
                sectors.failed -> StockErrorState(stringResource(Res.string.markets_section_failed), onRetry, Modifier.padding(vertical = spacing.sm))
                sectors.rows.isEmpty() -> StockEmptyState(stringResource(Res.string.markets_sectors_empty), Modifier.padding(vertical = spacing.sm))
                else -> Column(Modifier.semantics { contentDescription = sectors.chartDescription }) {
                    sectors.rows.forEach { row -> SectorRow(row, onClick = { onOpen(row) }) }
                }
            }
        }
        Text("${sectors.period}. ${sectors.methodology}", style = StockStepsTheme.typography.caption, color = colors.textTertiary)
    }
}

@Composable
private fun SectorRow(row: SectorRowModel, onClick: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val positive = row.direction == PriceDirection.UP
    Row(
        Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = row.accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Text(row.sector, Modifier.weight(0.42f), style = StockStepsTheme.typography.small, color = colors.textBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(0.38f).height(spacing.sm).clip(StockStepsTheme.shapes.pill).background(colors.surfaceSecondary)) {
            Box(Modifier.fillMaxWidth(row.fraction).fillMaxHeight().clip(StockStepsTheme.shapes.pill)
                .background(if (positive) colors.positive else if (row.direction == PriceDirection.DOWN) colors.negative else colors.textDisabled))
        }
        StockPriceChange(row.change, row.direction, Modifier.weight(0.2f))
    }
}

@Composable
private fun News(model: MarketsUiModel, onAction: (MarketsAction) -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(stringResource(Res.string.markets_news_title))
        StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
            when {
                model.newsFailed -> StockErrorState(stringResource(Res.string.home_news_unavailable), { onAction(MarketsAction.Retry) }, Modifier.padding(vertical = spacing.sm))
                model.news.isEmpty() -> StockEmptyState(stringResource(Res.string.markets_news_empty), Modifier.padding(vertical = spacing.sm))
                else -> model.news.forEachIndexed { index, article ->
                    if (index > 0) StockDivider()
                    StockNewsCard(article, onClick = article.url?.let { url -> { onAction(MarketsAction.OpenUrl(url)) } })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonSheet(lesson: MarketLesson, onDismiss: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(start = spacing.screen, end = spacing.screen, bottom = spacing.xxl), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(lesson.title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = colors.textPrimary)
            Text(lesson.body, style = StockStepsTheme.typography.body, color = colors.textBody)
            lesson.more.forEach { Text("• $it", style = StockStepsTheme.typography.small, color = colors.textBody) }
        }
    }
}

@Composable
private fun LoadingSkeleton(modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    StockLoadingState(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            StockSkeleton(Modifier.fillMaxWidth(0.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                repeat(2) { StockSkeleton(Modifier.weight(1f).height(StockStepsTheme.dimensions.touchTarget * 2), shape = StockStepsTheme.shapes.card) }
            }
            repeat(5) { StockRowSkeleton() }
        }
    }
}
