package org.example.stocksteps.presentation.markets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
 * Markets dashboard (Global UI Refinement Phase 4B, data first): title → market status → major indices (grouped rows) → Daily Market
 * Brief entry → top movers → sector performance → research tools → market news → daily lesson. Sections fail independently; the brief
 * and research tools stay available when the market overview itself fails.
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
    val section = content.padding(top = spacing.sectionGap)
    Box(Modifier.fillMaxSize().background(colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onAction(MarketsAction.Refresh) }, modifier = region) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "title") { Title(content.padding(top = spacing.md), onSearch = { onAction(MarketsAction.Search) }) }
                    if (model == null) {
                        item(key = "state") {
                            if (state.loading) LoadingSkeleton(content.padding(top = spacing.md))
                            else StockCard(content.padding(top = spacing.md)) {
                                StockErrorState(state.error ?: stringResource(Res.string.markets_unavailable), { onAction(MarketsAction.Retry) })
                            }
                        }
                    } else {
                        item(key = "header") { SessionHeader(model.header, state.overview?.session?.market, content.padding(top = spacing.md)) }
                        item(key = "indices") {
                            Indices(model, onOpen = { id -> lesson = MarketEducation.index(id) }, onRetry = { onAction(MarketsAction.Retry) }, modifier = section)
                        }
                    }
                    if (brief != null) item(key = "daily-brief") { BriefEntry(brief, onAction, section) }
                    if (model != null) {
                        item(key = "movers") { Movers(model.movers, onAction, section) }
                        item(key = "sectors") {
                            Sectors(model.sectors, onOpen = { row -> lesson = MarketEducation.sector(row.sector, row.symbol, model.sectors.methodology) },
                                onRetry = { onAction(MarketsAction.Retry) }, modifier = section)
                        }
                    }
                    item(key = "tools") { ResearchTools(section, earnings, onAction) }
                    if (model != null) {
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
    }
    lesson?.let { open -> LessonSheet(open) { lesson = null } }
}

@Composable
private fun Title(modifier: Modifier, onSearch: () -> Unit) {
    val colors = StockStepsTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.markets_title), Modifier.weight(1f).semantics { heading() }, style = StockStepsTheme.typography.screenTitle,
            color = colors.textTitle)
        IconButton(onClick = onSearch) {
            Icon(StockIcons.Search, contentDescription = stringResource(Res.string.markets_search), tint = colors.iconPrimary)
        }
    }
}

/** Market status card: session dot + label in words (closed is neutral, not red), next open/close, market and source update time, sample notice. */
@Composable
private fun SessionHeader(header: MarketHeaderModel, market: String?, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        StockSectionHeader("Market status", trailing = if (header.sampleData) {
            { StockStatusBadge("Sample", org.example.stocksteps.theme.StockBadgeKind.SAMPLE, size = StockBadgeSize.COMPACT) }
        } else null)
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Box(Modifier.padding(top = spacing.xs + spacing.xxs).size(spacing.sm).clip(androidx.compose.foundation.shape.CircleShape).background(
                when (MarketsScreenPresentation.statusDot(header.tone)) {
                    MarketsScreenPresentation.StatusDot.OPEN -> colors.positive
                    MarketsScreenPresentation.StatusDot.EXTENDED -> colors.caution
                    MarketsScreenPresentation.StatusDot.NEUTRAL -> colors.textDisabled
                }
            ))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(header.statusLabel, style = typography.bodySemiBold, color = colors.textTitle)
                header.detail?.let { Text(it, style = typography.small, color = colors.textSupporting) }
            }
        }
        MarketsScreenPresentation.statusMeta(header, market)?.let { Text(it, style = typography.caption, color = colors.textMeta) }
        if (header.notice.isNotBlank()) Text(header.notice, style = typography.caption, color = if (header.sampleData) colors.cautionText else colors.textMeta)
    }
}

/** Major indices as one grouped card of rows: name and source quote time left, value and signed change right; stacked at large text. */
@Composable
private fun Indices(model: MarketsUiModel, onOpen: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader("Major indices")
        if (model.indicesFailed || model.indices.isEmpty()) {
            StockCard { StockErrorState(stringResource(Res.string.markets_indices_failed), onRetry) }
            return@Column
        }
        BoxWithConstraints {
            val width = maxWidth.value
            StockCard(contentPadding = PaddingValues(vertical = spacing.xxs)) {
                model.indices.forEachIndexed { n, card ->
                    if (n > 0) StockDivider(startIndent = spacing.cardPadding)
                    IndexRow(card, showTrend = MarketsScreenPresentation.showTrend(width, card), onClick = { onOpen(card.id) })
                }
            }
        }
    }
}

@Composable
private fun IndexRow(card: IndexCardModel, showTrend: Boolean, onClick: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale
    val name: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(card.name, style = typography.bodySemiBold, color = colors.textTitle)
            MarketsScreenPresentation.indexMeta(card)?.let {
                Text(it, style = typography.caption, color = if (card.proxyLabel != null) colors.cautionText else colors.textMeta)
            }
        }
    }
    val values: @Composable (Alignment.Horizontal) -> Unit = { alignment ->
        Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(card.value, style = typography.numberLabelStrong, color = colors.textValue)
            if (card.available) StockPriceChange(MarketsScreenPresentation.indexChange(card), card.direction, maxLines = 2)
            else Text(MarketsScreenPresentation.INDEX_UNAVAILABLE, style = typography.caption, color = colors.textMeta)
        }
    }
    val trend: @Composable () -> Unit = {
        if (showTrend) {
            StockSparkline(card.trend, card.direction, Modifier.size(StockStepsTheme.dimensions.sparklineWidth, StockStepsTheme.dimensions.sparklineHeight))
        }
    }
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.rowCompactMinHeight)
        .clickable(onClickLabel = MarketsScreenPresentation.INDEX_HINT, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = card.accessibilityLabel }
        .padding(horizontal = spacing.cardPadding, vertical = spacing.sm)
    if (largeText) {
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) { name(Modifier); values(Alignment.Start); trend() }
    } else Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        name(Modifier.weight(1f)); trend(); values(Alignment.End)
    }
}

/** The Daily Market Brief as one entry row (no summary sentence: the index rows above already show those moves) + Previous briefs. */
@Composable
private fun BriefEntry(brief: org.example.stocksteps.brief.DailyBriefUiState, onAction: (MarketsAction) -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val latest = brief.latest
    StockCard(modifier, contentPadding = PaddingValues(horizontal = spacing.cardPadding, vertical = spacing.xxs)) {
        when {
            latest != null -> {
                val now = org.example.stocksteps.presentation.brief.briefNow()
                val caution = org.example.stocksteps.brief.BriefFormat.isStale(latest, now) || brief.offline
                StockNavigationRow("Your Daily Market Brief", { onAction(MarketsAction.DailyBrief) },
                    detail = org.example.stocksteps.presentation.home.HomeDashboardPresentation.briefMeta(latest, now, brief.offline) +
                        if (latest.sampleData) " · Sample data" else "",
                    icon = StockIcons.News, detailColor = if (caution) colors.cautionText else colors.textMeta,
                    actionLabel = org.example.stocksteps.presentation.home.HomeDashboardPresentation.briefAction(latest, now))
                StockDivider()
                Text("Previous briefs", Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).wrapContentHeight()
                    .clickable(role = Role.Button) { onAction(MarketsAction.BriefHistory) },
                    style = StockStepsTheme.typography.label, color = colors.primaryText)
            }
            brief.loading -> androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = spacing.md)
                .semantics { contentDescription = "Loading the Daily Market Brief" })
            else -> Text(brief.error ?: "The brief isn't available right now.", Modifier.padding(vertical = spacing.md),
                style = StockStepsTheme.typography.small, color = colors.textSupporting)
        }
    }
}

/** Research tools as one grouped card of navigation rows; the calendar row shows real counts only. */
@Composable
private fun ResearchTools(modifier: Modifier, earnings: org.example.stocksteps.earnings.EarningsSummaryState?, onAction: (MarketsAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val present = MarketsScreenPresentation
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader("Research tools")
        StockCard(contentPadding = PaddingValues(horizontal = spacing.cardPadding, vertical = spacing.xxs)) {
            StockNavigationRow(present.DISCOVER.title, { onAction(MarketsAction.Discover) }, detail = present.DISCOVER.detail,
                icon = StockIcons.Search, actionLabel = "Open ${present.DISCOVER.title}")
            StockDivider()
            StockNavigationRow(present.COMPARE.title, { onAction(MarketsAction.Compare) }, detail = present.COMPARE.detail,
                icon = StockIcons.PieChart, actionLabel = "Open ${present.COMPARE.title}")
            StockDivider()
            StockNavigationRow(present.EARNINGS_TITLE, { onAction(MarketsAction.Earnings) }, detail = present.earningsDetail(earnings),
                secondaryDetail = present.earningsSecondary(earnings), icon = StockIcons.TrendingUp, actionLabel = "View Earnings Calendar")
        }
    }
}

@Composable
private fun Movers(movers: MoversModel, onAction: (MarketsAction) -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(stringResource(Res.string.markets_movers_title))
        StockPillSelector(MoversTab.entries, movers.tab, label = { it.label }, onSelect = { onAction(MarketsAction.SelectTab(it)) })
        StockCard(contentPadding = PaddingValues(vertical = spacing.xxs)) {
            when {
                movers.failed -> StockErrorState(stringResource(Res.string.markets_section_failed), { onAction(MarketsAction.Retry) }, Modifier.padding(spacing.md))
                movers.emptyMessage != null -> StockEmptyState(movers.emptyMessage!!, Modifier.padding(spacing.md))
                else -> movers.rows.forEachIndexed { index, mover ->
                    if (index > 0) StockDivider(startIndent = spacing.cardPadding)
                    MoverRow(mover, movers.tab, onAction)
                }
            }
        }
        Text("${movers.rankedBy}. ${movers.universe}.", style = StockStepsTheme.typography.caption, color = colors.textMeta)
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
            contentPadding = PaddingValues(start = StockStepsTheme.spacing.cardPadding, end = StockStepsTheme.spacing.xs,
                top = StockStepsTheme.spacing.sm, bottom = StockStepsTheme.spacing.sm),
            onClick = { onAction(MarketsAction.OpenStock(row.symbol)) },
            trailing = {
                row.price?.let { Text(it, style = StockStepsTheme.typography.numberLabelStrong, color = StockStepsTheme.colors.textValue, maxLines = 1) }
                StockPriceChange(row.change, row.direction)
                if (tab == MoversTab.MOST_ACTIVE) mover.volume?.let { Text(it, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSupporting) }
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
        StockCard(contentPadding = PaddingValues(horizontal = spacing.cardPadding, vertical = spacing.xs)) {
            when {
                sectors.failed -> StockErrorState(stringResource(Res.string.markets_section_failed), onRetry, Modifier.padding(vertical = spacing.sm))
                sectors.rows.isEmpty() -> StockEmptyState(stringResource(Res.string.markets_sectors_empty), Modifier.padding(vertical = spacing.sm))
                else -> Column(Modifier.semantics { contentDescription = sectors.chartDescription }) {
                    sectors.rows.forEach { row -> SectorRow(row, onClick = { onOpen(row) }) }
                }
            }
        }
        Text("${sectors.period}. ${sectors.methodology}", style = StockStepsTheme.typography.caption, color = colors.textMeta)
    }
}

/** Sector name (wraps) — bar — signed change. At large text the bar and change move under the name so neither is squeezed. */
@Composable
private fun SectorRow(row: SectorRowModel, onClick: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale
    val bar: @Composable (Modifier) -> Unit = { m ->
        Box(m.height(spacing.sm).clip(StockStepsTheme.shapes.pill).background(colors.surfaceSecondary)) {
            Box(Modifier.fillMaxWidth(row.fraction).fillMaxHeight().clip(StockStepsTheme.shapes.pill)
                .background(if (row.direction == PriceDirection.UP) colors.positive else if (row.direction == PriceDirection.DOWN) colors.negative else colors.textDisabled))
        }
    }
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
        .clickable(role = Role.Button, onClick = onClick)
        .clearAndSetSemantics { contentDescription = row.accessibilityLabel }
        .padding(vertical = spacing.xs)
    if (largeText) {
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(row.sector, style = StockStepsTheme.typography.small, color = colors.textBody)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                bar(Modifier.weight(1f)); StockPriceChange(row.change, row.direction)
            }
        }
    } else Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(row.sector, Modifier.weight(0.45f), style = StockStepsTheme.typography.small, color = colors.textBody)
        bar(Modifier.weight(0.35f))
        StockPriceChange(row.change, row.direction, Modifier.weight(0.2f).wrapContentWidth(Alignment.End))
    }
}

@Composable
private fun News(model: MarketsUiModel, onAction: (MarketsAction) -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(stringResource(Res.string.markets_news_title))
        StockCard(contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
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
