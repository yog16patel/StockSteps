package org.example.stocksteps.presentation.companydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.companydetail.*
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.model.AppBarBackButton
import org.example.stocksteps.model.AppBarConfiguration
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.model.PriceChart
import org.example.stocksteps.model.WhyMoving
import org.example.stocksteps.news.NewsUiModel
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.components.StockStepsTopBar
import org.example.stocksteps.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Company Details: one vertical page ordered for beginners — identity, price, chart and quote
 * stats, why it moved, at a glance, how it looks, insight, about, financial highlights,
 * valuation, ranges, key ratios, sector, news. Each section loads and fails on its own; the
 * page never becomes a full-screen error because one part failed.
 */
@Composable
internal fun CompanyDetailsScreen(
    state: CompanyDetailsState,
    watched: Boolean,
    watchlistEnabled: Boolean,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    /** Saved Guided Research progress for this company; null when never started. */
    researchCompleted: Int? = null,
    onAction: (CompanyDetailsAction) -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val overview = (state.overview as? Section.Content)?.value
    var education by remember { mutableStateOf<String?>(null) }
    var showEvidence by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    val section = content.padding(top = spacing.xl)
    Column(Modifier.fillMaxSize().background(StockStepsTheme.colors.appBackground)) {
        // Minimal bar: back and watchlist only; the company identity lives in the page header.
        StockStepsTopBar(
            configuration = AppBarConfiguration(title = "", backButton = AppBarBackButton.BACK),
            onBack = { onAction(CompanyDetailsAction.Back) },
            backIcon = backIcon,
            trailing = {
                if (watchlistEnabled) {
                    val label = stringResource(if (watched) Res.string.details_watchlist_remove else Res.string.details_watchlist_add)
                    IconButton(onClick = { onAction(CompanyDetailsAction.ToggleWatchlist) }, modifier = Modifier.semantics { contentDescription = label }) {
                        Icon(
                            if (watched) StockIcons.Star else StockIcons.StarOutline,
                            contentDescription = null,
                            tint = if (watched) StockStepsTheme.colors.positive else StockStepsTheme.colors.iconSecondary
                        )
                    }
                }
            }
        )
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(
                modifier = region,
                contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item(key = "portfolio") {
                    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        TextButton(onClick = { onAction(CompanyDetailsAction.AddPortfolio) }) { Text("Add to portfolio") }
                        TextButton(onClick = { onAction(CompanyDetailsAction.Compare) }) { Text("Compare") }
                        TextButton(onClick = { onAction(CompanyDetailsAction.Earnings) }) { Text("Earnings") }
                        TextButton(onClick = { onAction(CompanyDetailsAction.PracticeBuy) }) { Text("Practice Buy") }
                    }
                }
                item(key = "header") { CompanyHeader(state.overview, onRetry = { onAction(CompanyDetailsAction.RetryCore) }, modifier = content) }
                item(key = "chart") {
                    ChartSection(
                        section = state.chart,
                        range = state.range,
                        dayDirection = overview?.direction,
                        quickStats = overview?.quickStats,
                        onSelect = { onAction(CompanyDetailsAction.SelectRange(it)) },
                        onRetry = { onAction(CompanyDetailsAction.RetryChart) },
                        modifier = content.padding(top = spacing.lg)
                    )
                }
                item(key = "research") {
                    org.example.stocksteps.presentation.research.UnderstandStockCard(researchCompleted, { onAction(CompanyDetailsAction.Research) }, section)
                }
                // Hidden while loading and when the backend has no source-backed explanation.
                when (val why = state.whyMoving) {
                    is Section.Content -> why.value?.let { value ->
                        item(key = "why") { WhyMovingSection(value, onSources = { showSources = true }, onOpen = { onAction(CompanyDetailsAction.OpenArticle(it)) },
                            onMore = { onAction(CompanyDetailsAction.OpenMovement) }, modifier = section) }
                    }
                    Section.Unavailable -> item(key = "why") {
                        SectionTitled(stringResource(Res.string.details_why_title), section) {
                            StockCard(bordered = false) { StockErrorState(stringResource(Res.string.details_why_unavailable), { onAction(CompanyDetailsAction.RetryWhyMoving) }) }
                        }
                    }
                    Section.Loading -> Unit
                }
                overview?.let { data ->
                    item(key = "glance") { GlanceSection(data.glance, onExplain = { education = it }, modifier = section) }
                    if (data.assessment.isNotEmpty()) {
                        item(key = "look") { AssessmentSection(data, onExplain = { education = it }, modifier = section) }
                    }
                    data.insight?.let { insight ->
                        item(key = "insight") {
                            StockInsightCard(
                                title = stringResource(Res.string.details_insight_title),
                                body = insight,
                                actionText = stringResource(Res.string.details_insight_why),
                                onClick = { showEvidence = true },
                                modifier = section
                            )
                        }
                    }
                    if (data.about != null || data.country != null) {
                        item(key = "about") { AboutSection(data, modifier = section) }
                    }
                    item(key = "highlights") { HighlightsSection(data, onSeeAll = { onAction(CompanyDetailsAction.OpenFinancials) }, modifier = section) }
                    item(key = "valuation") {
                        ValuationSection(data, onUnderstand = { education = "pe" }, onSee = { onAction(CompanyDetailsAction.OpenValuation) }, modifier = section)
                    }
                    if (data.dayRange != null || data.yearRange != null) {
                        item(key = "ranges") { RangesSection(data, modifier = section) }
                    }
                    if (data.keyRatios.isNotEmpty()) {
                        item(key = "ratios") {
                            SectionTitled(stringResource(Res.string.details_key_ratios), section) {
                                StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs)) {
                                    data.keyRatios.forEach { StockInfoRow(it.label, it.value, compact = true) }
                                }
                            }
                        }
                    }
                    if (data.sector != null && data.industry != null) {
                        item(key = "sector") { SectorSection(data, modifier = section) }
                    }
                }
                item(key = "news") {
                    NewsSection(state.news, onOpen = { onAction(CompanyDetailsAction.OpenArticle(it)) }, onRetry = { onAction(CompanyDetailsAction.RetryNews) },
                        onViewAll = { onAction(CompanyDetailsAction.OpenNews) }, modifier = section)
                }
                item(key = "disclaimer") {
                    Text(stringResource(Res.string.details_disclaimer), modifier = content.padding(top = spacing.lg),
                        style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textTertiary)
                }
            }
        }
    }
    if (overview != null) {
        education?.let { id -> EducationSheet(id, overview, onDismiss = { education = null }) }
        if (showEvidence) EvidenceSheet(overview.insightEvidence, onDismiss = { showEvidence = false })
    }
    val why = (state.whyMoving as? Section.Content)?.value
    if (showSources && why != null) {
        SourcesSheet(why, onOpen = { onAction(CompanyDetailsAction.OpenArticle(it)) }, onDismiss = { showSources = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanyHeader(section: Section<CompanyOverview>, onRetry: () -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    when (section) {
        Section.Loading -> StockLoadingState(modifier.padding(top = spacing.sm)) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                StockSkeleton(Modifier.size(StockStepsTheme.dimensions.avatar))
                StockSkeleton(Modifier.fillMaxWidth(0.5f))
                StockSkeleton(Modifier.fillMaxWidth(0.3f).height(StockStepsTheme.dimensions.touchTarget / 2))
            }
        }
        Section.Unavailable -> StockCard(modifier.padding(top = spacing.sm), bordered = false) { StockErrorState(stringResource(Res.string.details_unavailable), onRetry) }
        is Section.Content -> {
            val overview = section.value
            Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    StockTickerAvatar(overview.symbol, logoUrl = overview.logoUrl, size = StockStepsTheme.dimensions.avatar)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(overview.name, modifier = Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                        Text(overview.listing, style = StockStepsTheme.typography.small, color = colors.textSecondary)
                        if (overview.tags.isNotEmpty()) {
                            FlowRow(Modifier.padding(top = spacing.xxs), horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                overview.tags.forEach { StockTag(it) }
                            }
                        }
                    }
                }
                overview.price?.let { Text(it, modifier = Modifier.padding(top = spacing.sm), style = StockStepsTheme.typography.largeNumber, color = colors.textPrimary) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    StockPriceChange(
                        percentage = if (overview.changeAmount != null) "(${overview.changePercent})" else overview.changePercent,
                        direction = overview.direction,
                        amount = overview.changeAmount,
                        style = StockStepsTheme.typography.numberMedium
                    )
                    Text(stringResource(Res.string.details_today), style = StockStepsTheme.typography.small, color = colors.textSecondary)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.Center) {
                    MarketStatusIndicator(overview.marketStatus)
                    overview.updatedAt?.let(::formatQuoteTime)?.let { time ->
                        Text(stringResource(Res.string.details_updated, time), modifier = Modifier.align(Alignment.CenterVertically),
                            style = StockStepsTheme.typography.label, color = colors.textTertiary)
                    }
                }
            }
        }
    }
}

/** Range selector, chart (touch to inspect) and the quote stats, grouped on one surface. */
@Composable
private fun ChartSection(
    section: Section<PriceChart>,
    range: ChartRange,
    dayDirection: PriceDirection?,
    quickStats: List<InfoRow>?,
    onSelect: (ChartRange) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    StockCard(modifier, bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm)) {
        StockPillSelector(ChartRange.entries, range, label = { it.label }, onSelect = onSelect)
        val chart = (section as? Section.Content)?.value
        val summary = remember(chart, dayDirection) { chart?.let { ChartPresentation.summary(it.points, range, dayDirection) } }
        var scrub by remember(chart) { mutableStateOf<Int?>(null) }
        Text(
            text = scrub?.let { index -> chart?.points?.getOrNull(index)?.let { ChartPresentation.scrubLabel(it, range) } } ?: summary?.change.orEmpty(),
            modifier = Modifier.padding(top = spacing.xs),
            style = StockStepsTheme.typography.label,
            color = when {
                scrub != null -> colors.textPrimary
                summary?.direction == PriceDirection.UP -> colors.positiveText
                summary?.direction == PriceDirection.DOWN -> colors.negativeText
                else -> colors.textSecondary
            }
        )
        Box(Modifier.fillMaxWidth().padding(top = spacing.xs).height(StockStepsTheme.dimensions.chartHeight), contentAlignment = Alignment.Center) {
            when {
                section == Section.Loading -> StockLoadingState(Modifier.fillMaxSize()) { StockSkeleton(Modifier.fillMaxSize(), shape = StockStepsTheme.shapes.card) }
                summary == null -> StockErrorState(stringResource(Res.string.details_chart_unavailable), onRetry)
                else -> StockLineChart(summary.closes, summary.direction, summary.yLabels, summary.xLabels, summary.description,
                    Modifier.fillMaxSize(), onScrub = { scrub = it })
            }
        }
        if (quickStats != null) {
            StockDivider(Modifier.padding(top = spacing.sm))
            Row(Modifier.padding(top = spacing.xxs), horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                quickStats.chunked((quickStats.size + 1) / 2).forEach { column ->
                    Column(Modifier.weight(1f)) { column.forEach { StockInfoRow(it.label, it.value, compact = true) } }
                }
            }
        }
    }
}

/** Source-backed explanation on an educational (not market-colored) surface. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhyMovingSection(why: WhyMoving, onSources: () -> Unit, onOpen: (String) -> Unit, onMore: () -> Unit, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    Column(
        modifier.clip(StockStepsTheme.shapes.cardLarge).background(colors.educationContainer).padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(StockIcons.Lightbulb, contentDescription = null, tint = colors.educationAccent, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
            Text(stringResource(Res.string.details_why_title), Modifier.weight(1f).semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
            if (why.sources.isNotEmpty()) {
                Text(
                    "${stringResource(Res.string.details_why_sources, why.sources.size)} →",
                    modifier = Modifier
                        .clip(StockStepsTheme.shapes.pill)
                        .background(colors.surface)
                        .clickable(role = Role.Button, onClick = onSources)
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                    style = StockStepsTheme.typography.label,
                    color = colors.primaryText
                )
            }
        }
        Text(why.summary, style = StockStepsTheme.typography.body, color = colors.textBody)
        why.whyItMatters?.let { matters ->
            Row(
                Modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).background(colors.learnContainerStart).padding(spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Icon(StockIcons.Info, contentDescription = null, tint = colors.learnAccent, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Text(stringResource(Res.string.details_why_caution), style = StockStepsTheme.typography.label, color = colors.learnAccent)
                    Text(matters, style = StockStepsTheme.typography.small, color = colors.textBody)
                }
            }
        }
        if (why.sources.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                why.sources.forEach { source ->
                    Text(
                        source.publisher ?: source.title,
                        modifier = Modifier.clickable(role = Role.Button) { onOpen(source.url) }.padding(vertical = spacing.xs),
                        style = StockStepsTheme.typography.label,
                        color = colors.primaryText,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            "${stringResource(Res.string.details_why_more)} →",
            modifier = Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).wrapContentHeight()
                .clickable(role = Role.Button, onClick = onMore).padding(vertical = spacing.xs),
            style = StockStepsTheme.typography.label,
            color = colors.primaryText
        )
    }
}

/** 2×2 metric tiles; one column on narrow windows or large text so values never clip. */
@Composable
private fun GlanceSection(metrics: List<GlanceMetric>, onExplain: (String) -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    SectionTitled(stringResource(Res.string.details_glance_title), modifier) {
        BoxWithConstraints {
            val dims = StockStepsTheme.dimensions
            val columns = if (maxWidth < dims.multiColumnMinWidth || LocalDensity.current.fontScale > dims.largeFontScale) 1 else 2
            val learn = stringResource(Res.string.details_learn_more)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                metrics.chunked(columns).forEach { row ->
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        row.forEach { metric ->
                            val (icon, container, tint) = glanceIcon(metric.id)
                            StockCard(Modifier.weight(1f).fillMaxHeight(), bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    StockMetric(
                                        label = metric.label, value = metric.value, helper = metric.helper, direction = metric.direction,
                                        onClick = metric.educationId?.let { id -> { onExplain(id) } }, onClickLabel = learn,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StockIconTile(icon, container, tint, Modifier.padding(top = spacing.xs))
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun glanceIcon(id: String): Triple<ImageVector, Color, Color> {
    val colors = StockStepsTheme.colors
    return when (id) {
        "marketCap" -> Triple(StockIcons.Bank, colors.primaryContainer, colors.primaryText)
        "pe" -> Triple(StockIcons.Tag, colors.warningContainer, colors.cautionText)
        "revenueGrowth" -> Triple(StockIcons.TrendingUp, colors.positiveContainer, colors.positiveText)
        else -> Triple(StockIcons.Coin, colors.learnContainerStart, colors.learnAccent)
    }
}

@Composable
private fun AssessmentSection(data: CompanyOverview, onExplain: (String) -> Unit, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    SectionTitled(stringResource(Res.string.details_look_title, data.shortName), modifier) {
        StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
            data.assessment.forEachIndexed { index, row ->
                if (index > 0) StockDivider()
                val (icon, container, tint) = when (row.id) {
                    "growth" -> Triple(StockIcons.TrendingUp, colors.positiveContainer, colors.positiveText)
                    "profitability" -> Triple(StockIcons.PieChart, colors.learnContainerStart, colors.learnAccent)
                    "health" -> Triple(StockIcons.Shield, colors.primaryContainer, colors.primaryText)
                    else -> Triple(StockIcons.Tag, colors.warningContainer, colors.cautionText)
                }
                StockAssessmentRow(
                    icon = icon, iconContainer = container, iconContent = tint,
                    title = row.title, detail = row.detail, badge = row.badge, tone = row.tone, note = row.explanation,
                    onClick = if (row.id == "valuation") { { onExplain("pe") } } else null,
                    onClickLabel = stringResource(Res.string.details_learn_more)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutSection(data: CompanyOverview, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    var expanded by rememberSaveable(data.symbol) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(
            title = stringResource(Res.string.details_about_title, data.shortName),
            actionText = data.aboutFull?.let { stringResource(if (expanded) Res.string.details_see_less else Res.string.details_see_more) },
            onActionClick = data.aboutFull?.let { { expanded = !expanded } }
        )
        val text = if (expanded) data.aboutFull ?: data.about else data.about
        text?.let { Text(it, style = StockStepsTheme.typography.body, color = colors.textBody) }
        data.country?.let { country ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Icon(StockIcons.Globe, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
                Text(country, style = StockStepsTheme.typography.small, color = colors.textSecondary)
            }
        }
        val tags = listOfNotNull(data.sector, data.industry)
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) { tags.forEach { StockTag(it) } }
        }
    }
}

@Composable
private fun HighlightsSection(data: CompanyOverview, onSeeAll: () -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(stringResource(Res.string.details_highlights_title), actionText = stringResource(Res.string.details_see_financials), onActionClick = onSeeAll)
        StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
            if (data.highlights.isEmpty()) {
                StockEmptyState(stringResource(Res.string.details_highlights_unavailable), Modifier.padding(vertical = spacing.sm))
            } else {
                data.highlights.forEachIndexed { index, row ->
                    if (index > 0) StockDivider()
                    StockInfoRow(row.label, row.value, helper = row.helper, change = row.change ?: "—", direction = row.changeDirection)
                }
            }
        }
        data.highlightsBasis?.let { Text(it, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textTertiary) }
    }
}

@Composable
private fun ValuationSection(data: CompanyOverview, onUnderstand: () -> Unit, onSee: () -> Unit, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val valuation = data.valuation
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(stringResource(Res.string.details_valuation_title), actionText = stringResource(Res.string.details_see_details), onActionClick = onSee)
        if (valuation == null) {
            StockCard(bordered = false) { StockEmptyState(stringResource(Res.string.details_valuation_unavailable)) }
            return@Column
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ValuationTile(stringResource(Res.string.details_valuation_current), valuation.currentPe ?: "N/A", colors.textPrimary, Modifier.weight(1f))
            ValuationTile(valuation.historicalLabel, valuation.historicalAverage ?: "—", colors.textPrimary, Modifier.weight(1f))
            ValuationTile(
                stringResource(Res.string.details_valuation_difference), valuation.difference ?: "—",
                if (valuation.position == ValuationPosition.ABOVE) colors.cautionText else colors.textPrimary, Modifier.weight(1f)
            )
        }
        val (container, accent) = if (valuation.position == ValuationPosition.ABOVE) colors.warningContainer to colors.cautionText else colors.primaryContainer to colors.primaryText
        Column(
            Modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).background(container).padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            valuation.headline?.let { headline ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Icon(StockIcons.Tag, contentDescription = null, tint = accent, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
                    Text(headline, style = StockStepsTheme.typography.bodyMedium, color = accent)
                }
            }
            Text(valuation.explanation, style = StockStepsTheme.typography.small, color = colors.textBody)
        }
        Column {
            LinkAction(stringResource(Res.string.details_understand_pe), onUnderstand)
            LinkAction(stringResource(Res.string.details_see_valuation), onSee)
        }
    }
}

@Composable
private fun ValuationTile(label: String, value: String, valueColor: Color, modifier: Modifier) {
    StockCard(modifier.fillMaxHeight().semantics(mergeDescendants = true) {}, bordered = false, contentPadding = PaddingValues(StockStepsTheme.spacing.sm)) {
        Text(label, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary, maxLines = 2)
        Text(value, modifier = Modifier.padding(top = StockStepsTheme.spacing.xxs), style = StockStepsTheme.typography.numberEmphasis, color = valueColor, maxLines = 1)
    }
}

@Composable
private fun RangesSection(data: CompanyOverview, modifier: Modifier) {
    StockCard(modifier, bordered = false, contentPadding = PaddingValues(StockStepsTheme.spacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.lg)) {
            data.yearRange?.let { range ->
                val title = stringResource(Res.string.details_year_range)
                StockRangeBar(title, range.low, range.high, range.position, stringResource(Res.string.details_range_a11y, title, range.low, range.high))
            }
            data.dayRange?.let { range ->
                val title = stringResource(Res.string.details_day_range)
                StockRangeBar(title, range.low, range.high, range.position, stringResource(Res.string.details_range_a11y, title, range.low, range.high))
            }
        }
    }
}

@Composable
private fun SectorSection(data: CompanyOverview, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    SectionTitled(stringResource(Res.string.details_sector_industry), modifier) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            listOf(
                Triple(stringResource(Res.string.details_sector), data.sector.orEmpty(), StockIcons.Bank),
                Triple(stringResource(Res.string.details_industry), data.industry.orEmpty(), StockIcons.PieChart)
            ).forEach { (label, value, icon) ->
                StockCard(Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) {}, bordered = false, contentPadding = PaddingValues(spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        StockIconTile(icon, colors.primaryContainer, colors.primaryText)
                        Column {
                            Text(label, style = StockStepsTheme.typography.caption, color = colors.textSecondary)
                            Text(value, style = StockStepsTheme.typography.bodySemiBold, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
        Text(
            stringResource(Res.string.details_sector_body, data.shortName, data.sector.orEmpty(), data.industry.orEmpty()),
            modifier = Modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).background(colors.primaryContainer).padding(spacing.md),
            style = StockStepsTheme.typography.small,
            color = colors.textBody
        )
    }
}

@Composable
private fun NewsSection(news: Section<List<NewsUiModel>>, onOpen: (String) -> Unit, onRetry: () -> Unit, onViewAll: () -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val hasNews = news is Section.Content && news.value.isNotEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(
            stringResource(Res.string.details_news_title),
            actionText = if (hasNews) stringResource(Res.string.details_view_all_news) else null,
            onActionClick = if (hasNews) onViewAll else null
        )
        StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
            when (news) {
                Section.Loading -> StockLoadingState { Column { repeat(2) { StockNewsCardSkeleton() } } }
                Section.Unavailable -> StockErrorState(stringResource(Res.string.home_news_unavailable), onRetry, Modifier.padding(vertical = spacing.sm))
                is Section.Content -> if (news.value.isEmpty()) {
                    StockEmptyState(stringResource(Res.string.details_news_empty), Modifier.padding(vertical = spacing.sm))
                } else news.value.forEachIndexed { index, article ->
                    if (index > 0) StockDivider()
                    StockNewsCard(article, onClick = article.url?.let { url -> { onOpen(url) } })
                }
            }
        }
    }
}

@Composable
private fun SectionTitled(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
        StockSectionHeader(title)
        content()
    }
}

/** Compact text link with a full 48dp touch target, so stacked links stay tight. */
@Composable
private fun LinkAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Text("$text →", style = StockStepsTheme.typography.bodyMedium, color = StockStepsTheme.colors.primaryText)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EducationSheet(id: String, overview: CompanyOverview, onDismiss: () -> Unit) {
    val metric = overview.glance.firstOrNull { it.id == id }
    val name = overview.shortName
    val value = metric?.value?.substringBefore(" (")?.takeIf { it != "—" && it != "N/A" && it != "None" }
    val (title, body) = when (id) {
        "marketCap" -> stringResource(Res.string.edu_marketCap_title) to stringResource(Res.string.edu_marketCap_body, name, metric?.value ?: "—")
        "pe" -> stringResource(Res.string.edu_pe_title) to (value?.toDoubleOrNull()?.let {
            stringResource(Res.string.edu_pe_body, name, value, it.toLong().toString())
        } ?: stringResource(Res.string.edu_pe_missing, name))
        "revenueGrowth" -> stringResource(Res.string.edu_revenueGrowth_title) to
            (value?.let { stringResource(Res.string.edu_revenueGrowth_body, name, it) } ?: stringResource(Res.string.edu_unavailable, name))
        else -> stringResource(Res.string.edu_dividendYield_title) to
            (value?.let { stringResource(Res.string.edu_dividendYield_body, name, it) } ?: stringResource(Res.string.edu_unavailable, name))
    }
    InfoSheet(title, onDismiss) { Text(body, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvidenceSheet(rows: List<EvidenceRow>, onDismiss: () -> Unit) {
    InfoSheet(stringResource(Res.string.details_insight_evidence_title), onDismiss) {
        rows.forEachIndexed { index, row ->
            if (index > 0) StockDivider()
            StockInfoRow(row.label, row.value)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesSheet(why: WhyMoving, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    InfoSheet(stringResource(Res.string.details_why_title), onDismiss) {
        why.sources.forEachIndexed { index, source ->
            if (index > 0) StockDivider()
            StockAssessmentRow(
                icon = StockIcons.News,
                iconContainer = StockStepsTheme.colors.primaryContainer,
                iconContent = StockStepsTheme.colors.primaryText,
                title = source.publisher ?: source.title,
                detail = source.title,
                onClick = { onOpen(source.url) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val spacing = StockStepsTheme.spacing
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(
            Modifier.fillMaxWidth().padding(start = spacing.screen, end = spacing.screen, bottom = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(title, modifier = Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)
            content()
        }
    }
}
