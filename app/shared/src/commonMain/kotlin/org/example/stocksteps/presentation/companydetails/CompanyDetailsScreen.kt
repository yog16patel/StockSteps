package org.example.stocksteps.presentation.companydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.companydetail.*
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.home.HomePresentation
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
 * Company Details: one vertical page ordered for beginners — identity, price, chart, why it moved,
 * at a glance, how it looks, insight, about, financial highlights, valuation, news. Each section
 * loads and fails on its own; the page never becomes a full-screen error because one part failed.
 */
@Composable
internal fun CompanyDetailsScreen(
    state: CompanyDetailsState,
    watched: Boolean,
    watchlistEnabled: Boolean,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    onAction: (CompanyDetailsAction) -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val overview = (state.overview as? Section.Content)?.value
    var education by remember { mutableStateOf<String?>(null) }
    var showEvidence by remember { mutableStateOf(false) }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    val section = content.padding(top = spacing.xl)
    Column(Modifier.fillMaxSize().background(StockStepsTheme.colors.appBackground)) {
        StockStepsTopBar(
            configuration = AppBarConfiguration(title = overview?.name ?: state.symbol, backButton = AppBarBackButton.BACK),
            onBack = { onAction(CompanyDetailsAction.Back) },
            backIcon = backIcon,
            trailing = {
                if (watchlistEnabled) {
                    val label = stringResource(if (watched) Res.string.details_watchlist_remove else Res.string.details_watchlist_add)
                    IconButton(onClick = { onAction(CompanyDetailsAction.ToggleWatchlist) }, modifier = Modifier.semantics { contentDescription = label }) {
                        Icon(
                            if (watched) StockIcons.Star else StockIcons.StarOutline,
                            contentDescription = null,
                            tint = if (watched) StockStepsTheme.colors.primary else StockStepsTheme.colors.iconSecondary
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
                item(key = "header") { CompanyHeader(state.overview, onRetry = { onAction(CompanyDetailsAction.RetryCore) }, modifier = content) }
                item(key = "chart") {
                    ChartSection(state.chart, state.range, overview?.direction, onSelect = { onAction(CompanyDetailsAction.SelectRange(it)) },
                        onRetry = { onAction(CompanyDetailsAction.RetryChart) }, modifier = content.padding(top = spacing.lg))
                }
                // Why did it move? disappears when no explanation exists, so the page stays calm.
                (state.whyMoving as? Section.Content)?.value?.let { why ->
                    item(key = "why") { WhyMovingSection(why, onOpen = { onAction(CompanyDetailsAction.OpenArticle(it)) }, modifier = section) }
                }
                overview?.let { data ->
                    val short = data.name.substringBefore(",").removeSuffix(" Corporation").removeSuffix(" Inc.")
                    item(key = "glance") { GlanceSection(data.glance, onExplain = { education = it }, modifier = section) }
                    if (data.assessment.isNotEmpty()) {
                        item(key = "look") {
                            SectionTitled(stringResource(Res.string.details_look_title, short), section) {
                                StockCard(contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
                                    data.assessment.forEachIndexed { index, row ->
                                        if (index > 0) StockDivider()
                                        StockSettingsRow(
                                            title = row.title,
                                            subtitle = row.detail,
                                            trailing = row.comparison?.let { text -> { Text(text, style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.textSecondary) } }
                                        )
                                    }
                                }
                            }
                        }
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
                    data.about?.let { about ->
                        item(key = "about") {
                            SectionTitled(stringResource(Res.string.details_about_title, short), section) {
                                Text(about, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody)
                                data.classification?.let { Text(it, style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.textSecondary) }
                            }
                        }
                    }
                    item(key = "highlights") {
                        SectionTitled(stringResource(Res.string.details_highlights_title), section) {
                            if (data.highlights.isEmpty()) {
                                StockCard { StockEmptyState(stringResource(Res.string.details_section_unavailable)) }
                            } else {
                                StockCard(contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
                                    data.highlights.forEachIndexed { index, row ->
                                        if (index > 0) StockDivider()
                                        StockInfoRow(row.label, row.value, helper = row.helper)
                                    }
                                }
                            }
                            LinkAction(stringResource(Res.string.details_see_financials)) { onAction(CompanyDetailsAction.OpenFinancials) }
                        }
                    }
                    data.valuation?.let { valuation ->
                        item(key = "valuation") { ValuationSection(valuation, onUnderstand = { education = "pe" }, onSee = { onAction(CompanyDetailsAction.OpenFinancials) }, modifier = section) }
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
}

@Composable
private fun CompanyHeader(section: Section<CompanyOverview>, onRetry: () -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    when (section) {
        Section.Loading -> StockLoadingState(modifier.padding(top = spacing.sm)) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                StockSkeleton(Modifier.size(StockStepsTheme.dimensions.avatar))
                StockSkeleton(Modifier.fillMaxWidth(0.5f))
                StockSkeleton(Modifier.fillMaxWidth(0.3f).height(StockStepsTheme.dimensions.touchTarget / 2))
            }
        }
        Section.Unavailable -> StockCard(modifier.padding(top = spacing.sm)) { StockErrorState(stringResource(Res.string.details_unavailable), onRetry) }
        is Section.Content -> {
            val overview = section.value
            Column(modifier.padding(top = spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    StockTickerAvatar(overview.symbol, logoUrl = overview.logoUrl, size = StockStepsTheme.dimensions.avatar)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(overview.name, modifier = Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)
                        Text(overview.listing, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
                    }
                }
                overview.price?.let { Text(it, modifier = Modifier.padding(top = spacing.sm), style = StockStepsTheme.typography.largeNumber, color = StockStepsTheme.colors.textPrimary) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    StockPriceChange(
                        percentage = if (overview.changeAmount != null) "(${overview.changePercent})" else overview.changePercent,
                        direction = overview.direction,
                        amount = overview.changeAmount,
                        style = StockStepsTheme.typography.numberMedium
                    )
                    Text(stringResource(Res.string.details_today), style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
                }
                MarketStatusIndicator(overview.marketStatus)
            }
        }
    }
}

@Composable
private fun ChartSection(
    section: Section<PriceChart>,
    range: ChartRange,
    dayDirection: PriceDirection?,
    onSelect: (ChartRange) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    val spacing = StockStepsTheme.spacing
    StockCard(modifier) {
        Box(Modifier.fillMaxWidth().height(StockStepsTheme.dimensions.chartHeight), contentAlignment = Alignment.Center) {
            when (section) {
                Section.Loading -> StockLoadingState(Modifier.fillMaxSize()) { StockSkeleton(Modifier.fillMaxSize(), shape = StockStepsTheme.shapes.card) }
                Section.Unavailable -> StockErrorState(stringResource(Res.string.details_chart_unavailable), onRetry)
                is Section.Content -> {
                    val closes = section.value.points.map { it.close }
                    // Line colour follows the move over the selected range (1D uses today's change).
                    val direction = if (range == ChartRange.ONE_DAY && dayDirection != null) dayDirection
                        else HomePresentation.direction(closes.last() - closes.first())
                    val summary = stringResource(Res.string.details_chart_summary, range.label,
                        HomePresentation.price(closes.first(), null).orEmpty(), HomePresentation.price(closes.last(), null).orEmpty())
                    StockSparkline(closes, direction, Modifier.fillMaxSize().semantics { contentDescription = summary })
                }
            }
        }
        Row(
            Modifier.padding(top = spacing.xs).horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            ChartRange.entries.forEach { option -> StockChip(option.label, selected = option == range, onClick = { onSelect(option) }) }
        }
    }
}

@Composable
private fun WhyMovingSection(why: WhyMoving, onOpen: (String) -> Unit, modifier: Modifier) {
    val source = why.sources.firstOrNull()
    StockInsightCard(
        eyebrow = stringResource(Res.string.details_why_title),
        title = why.summary,
        body = why.whyItMatters?.let { "${stringResource(Res.string.details_why_matters)}: $it" }.orEmpty(),
        actionText = source?.let { stringResource(Res.string.details_why_sources, why.sources.size) },
        onClick = source?.let { { onOpen(it.url) } },
        modifier = modifier
    )
}

/** Two columns on phones; one column on narrow windows or large text so values never clip. */
@Composable
private fun GlanceSection(metrics: List<GlanceMetric>, onExplain: (String) -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    SectionTitled(stringResource(Res.string.details_glance_title), modifier) {
        StockCard(contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs)) {
            BoxWithConstraints {
                val dims = StockStepsTheme.dimensions
                val columns = if (maxWidth < dims.multiColumnMinWidth || LocalDensity.current.fontScale > dims.largeFontScale) 1 else 2
                val learn = stringResource(Res.string.details_learn_more)
                Column {
                    metrics.chunked(columns).forEachIndexed { index, row ->
                        if (index > 0) StockDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                            row.forEach { metric ->
                                StockMetric(
                                    label = metric.label, value = metric.value, helper = metric.helper, direction = metric.direction,
                                    onClick = metric.educationId?.let { id -> { onExplain(id) } }, onClickLabel = learn,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValuationSection(valuation: ValuationSummary, onUnderstand: () -> Unit, onSee: () -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    SectionTitled(stringResource(Res.string.details_valuation_title), modifier) {
        StockCard(contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
            val rows = listOfNotNull(
                valuation.currentPe?.let { stringResource(Res.string.details_valuation_current) to it },
                valuation.historicalAverage?.let { stringResource(Res.string.details_valuation_average) to it },
                valuation.difference?.let { stringResource(Res.string.details_valuation_difference) to it }
            )
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) StockDivider()
                StockInfoRow(label, value)
            }
            Text(valuation.explanation, modifier = Modifier.padding(vertical = spacing.sm), style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            LinkAction(stringResource(Res.string.details_understand_pe), onUnderstand)
            LinkAction(stringResource(Res.string.details_see_valuation), onSee)
        }
    }
}

@Composable
private fun NewsSection(news: Section<List<NewsUiModel>>, onOpen: (String) -> Unit, onRetry: () -> Unit, onViewAll: () -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    SectionTitled(stringResource(Res.string.details_news_title), modifier) {
        StockCard(contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
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
        if (news is Section.Content && news.value.isNotEmpty()) LinkAction(stringResource(Res.string.details_view_all_news), onViewAll)
    }
}

@Composable
private fun SectionTitled(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
        StockSectionHeader(title)
        content()
    }
}

@Composable
private fun LinkAction(text: String, onClick: () -> Unit) = StockButton("$text →", onClick = onClick, variant = StockButtonVariant.TEXT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EducationSheet(id: String, overview: CompanyOverview, onDismiss: () -> Unit) {
    val metric = overview.glance.firstOrNull { it.id == id }
    val name = overview.name.substringBefore(",")
    val (title, body) = when (id) {
        "marketCap" -> stringResource(Res.string.edu_marketCap_title) to stringResource(Res.string.edu_marketCap_body, name, metric?.value ?: "—")
        "pe" -> stringResource(Res.string.edu_pe_title) to (metric?.value?.toDoubleOrNull()?.let {
            stringResource(Res.string.edu_pe_body, name, metric.value, it.toLong().toString())
        } ?: stringResource(Res.string.edu_pe_missing, name))
        "revenueGrowth" -> stringResource(Res.string.edu_revenueGrowth_title) to
            (metric?.value?.takeIf { it != "—" }?.let { stringResource(Res.string.edu_revenueGrowth_body, name, it) } ?: stringResource(Res.string.edu_unavailable, name))
        else -> stringResource(Res.string.edu_dividendYield_title) to
            (metric?.value?.takeIf { it != "—" }?.let { stringResource(Res.string.edu_dividendYield_body, name, it) } ?: stringResource(Res.string.edu_unavailable, name))
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

