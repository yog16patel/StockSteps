package org.example.stocksteps.presentation.companydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import org.example.stocksteps.presentation.companydetails.CompanyDetailsPresentation.Section as PageSection
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
 * Company Details (Global UI Refinement Phase 4C; hierarchy 5D.5.4A), one vertical page that answers a beginner's questions in order:
 * what company is this and what is the price doing (identity, price, chart with the day range) → actions → what the company does → the
 * important numbers (At a Glance) → what they mean (Understand, then why it moved) → valuation & financial health → earnings → news → About →
 * research further. A sticky section bar scrolls to Overview · Financials · News · About within the page (never a new screen). Each section
 * loads and fails on its own; a value appears once. Every section is borderless (owner direction, as Daily Brief and Earnings).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompanyDetailsScreen(
    state: CompanyDetailsState,
    watched: Boolean,
    watchlistEnabled: Boolean,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    /** Saved Guided Research progress for this company; null when never started. */
    researchCompleted: Int? = null,
    /** Next earnings date (null hides the section, e.g. previews). */
    earnings: org.example.stocksteps.earnings.CompanyEarningsState? = null,
    /** Reminder state for the next earnings event (null hides the control). */
    earningsReminder: org.example.stocksteps.earnings.ReminderControl? = null,
    onAction: (CompanyDetailsAction) -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val overview = (state.overview as? Section.Content)?.value
    var education by remember { mutableStateOf<String?>(null) }
    var showEvidence by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    val section = content.padding(top = spacing.sectionGap)
    val why = state.whyMoving
    val whatItDoes = overview?.let(CompanyDetailsPresentation::whatItDoes)
    val pageSections = CompanyDetailsPresentation.sections(overview)
    // One ordered list of page items, so the section bar knows where each section starts (plan §C).
    val keys = buildList {
        add("header"); add("chart")
        if (overview != null) add("actions")
        add("sections")
        if (whatItDoes != null) add("what")
        if (overview != null) { add("glance"); add("understand") }
        if ((why is Section.Content && why.value != null) || why == Section.Unavailable) add("why")
        if (overview != null) add("valuation")
        if (earnings != null) add("earnings")
        add("news")
        if (PageSection.ABOUT in pageSections) add("about")
        add("research"); add("disclaimer")
    }
    fun anchor(target: PageSection): Int = when (target) {
        PageSection.OVERVIEW -> keys.indexOf("sections") + 1
        PageSection.FINANCIALS -> keys.indexOf("valuation")
        PageSection.NEWS -> keys.indexOf("news")
        PageSection.ABOUT -> keys.indexOf("about")
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var barHeight by remember { mutableIntStateOf(0) }
    // The selected entry follows the scroll position: the last section whose top reached the bottom of the pinned bar (shared rule).
    val selected by remember(keys, pageSections) {
        derivedStateOf {
            val info = listState.layoutInfo
            val sticky = keys.indexOf("sections")
            val tops = pageSections.associateWith { target ->
                val index = anchor(target)
                val item = info.visibleItemsInfo.firstOrNull { it.index == index && it.index != sticky }
                when {
                    item != null -> (item.offset - barHeight).toFloat()
                    index < listState.firstVisibleItemIndex -> -1f
                    else -> Float.MAX_VALUE
                }
            }
            CompanyDetailsPresentation.selectedSection(tops, tolerance = 1f, atEnd = !listState.canScrollForward, viewport = info.viewportEndOffset.toFloat())
        }
    }
    val scrollTo: (PageSection) -> Unit = { target ->
        anchor(target).takeIf { it >= 0 }?.let { index -> scope.launch { listState.animateScrollToItem(index, -barHeight) } }
    }
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
                            tint = if (watched) StockStepsTheme.colors.primaryText else StockStepsTheme.colors.iconSecondary
                        )
                    }
                }
            }
        )
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(
                modifier = region,
                state = listState,
                contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                keys.forEach { key ->
                    if (key == "sections") stickyHeader(key = key) {
                        StockSectionBar(pageSections, selected, label = { it.label }, onSelect = scrollTo,
                            modifier = content.background(StockStepsTheme.colors.appBackground).padding(top = spacing.sm).onSizeChanged { barHeight = it.height })
                    } else item(key = key) {
                        when (key) {
                            "header" -> CompanyHeader(state.overview, onRetry = { onAction(CompanyDetailsAction.RetryCore) }, modifier = content)
                            "chart" -> ChartSection(
                                section = state.chart,
                                issue = state.chartIssue,
                                range = state.range,
                                dayDirection = overview?.direction,
                                quickStats = overview?.let(CompanyDetailsPresentation::chartStats),
                                dayRange = overview?.dayRange,
                                onSelect = { onAction(CompanyDetailsAction.SelectRange(it)) },
                                onRetry = { onAction(CompanyDetailsAction.RetryChart) },
                                modifier = content.padding(top = spacing.md)
                            )
                            "actions" -> CompanyActions(onAction, content.padding(top = spacing.md))
                            "what" -> whatItDoes?.let { what ->
                                WhatItDoesRow(what, onOpen = if (PageSection.ABOUT in pageSections) { { scrollTo(PageSection.ABOUT) } } else null,
                                    modifier = content.padding(top = spacing.md))
                            }
                            "glance" -> overview?.let { GlanceSection(it, onExplain = { id -> education = id }, modifier = section) }
                            "understand" -> overview?.let { UnderstandSection(it, onEvidence = { showEvidence = true }, onExplain = { id -> education = id }, modifier = section) }
                            // Why it moved explains the price: after the numbers and what they mean (D-E5). Hidden while loading and when no
                            // source-backed explanation exists.
                            "why" -> when (why) {
                                is Section.Content -> why.value?.let { value ->
                                    WhyMovingSection(value, onSources = { showSources = true }, onOpen = { onAction(CompanyDetailsAction.OpenArticle(it)) },
                                        onMore = { onAction(CompanyDetailsAction.OpenMovement) }, modifier = section)
                                }
                                Section.Unavailable -> SectionTitled(stringResource(Res.string.details_why_title), section) {
                                    StockCard(bordered = false) { StockErrorState(stringResource(Res.string.details_why_unavailable), { onAction(CompanyDetailsAction.RetryWhyMoving) }) }
                                }
                                Section.Loading -> Unit
                            }
                            "valuation" -> overview?.let {
                                ValuationHealthSection(it, onValuation = { onAction(CompanyDetailsAction.OpenValuation) },
                                    onFinancials = { onAction(CompanyDetailsAction.OpenFinancials) }, modifier = section)
                            }
                            "earnings" -> earnings?.let { e -> EarningsSection(e, earningsReminder, onAction, section) }
                            "news" -> NewsSection(state.news, onOpen = { onAction(CompanyDetailsAction.OpenArticle(it)) }, onRetry = { onAction(CompanyDetailsAction.RetryNews) },
                                onViewAll = { onAction(CompanyDetailsAction.OpenNews) }, modifier = section)
                            "about" -> overview?.let { AboutCard(it, section) }
                            "research" -> ResearchSection(researchCompleted, practice = overview != null, showEarningsAction = earnings == null, onAction, section)
                            "disclaimer" -> Text(stringResource(Res.string.details_disclaimer), modifier = content.padding(top = spacing.lg),
                                style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textTertiary)
                        }
                    }
                }
            }
        }
    }
    if (overview != null) {
        education?.let { id ->
            val onLearn: (String) -> Unit = { onAction(CompanyDetailsAction.LearnTerm(it)) }
            // Glance metrics keep their company sentence; Understand rows for net margin and debt use the 5D.5.3 metric sheet.
            if (id in CompanyDetailsPresentation.GLANCE_SHEET_IDS) EducationSheet(id, overview, onDismiss = { education = null }, onLearn = onLearn)
            else org.example.stocksteps.learning.MetricLearnTerms.explain(id)?.let { explanation ->
                org.example.stocksteps.presentation.learn.MetricExplanationSheet(explanation, onLearn, onDismiss = { education = null })
            }
        }
        if (showEvidence) EvidenceSheet(overview.insightEvidence, onDismiss = { showEvidence = false })
    }
    val whyValue = (state.whyMoving as? Section.Content)?.value
    if (showSources && whyValue != null) {
        SourcesSheet(whyValue, onOpen = { onAction(CompanyDetailsAction.OpenArticle(it)) }, onDismiss = { showSources = false })
    }
}

/** "What Apple does": the first sentence of the description and "Sector · Industry"; tapping scrolls to About (D-E3: tags left the header). */
@Composable
private fun WhatItDoesRow(what: CompanyDetailsPresentation.WhatItDoes, onOpen: (() -> Unit)?, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    StockCard(modifier, onClick = onOpen, onClickLabel = onOpen?.let { "Read more in About" }, bordered = false, contentPadding = PaddingValues(spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(what.title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
                what.summary?.let { Text(it, style = StockStepsTheme.typography.small, color = colors.textBody) }
                what.classification?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textMeta) }
            }
            if (onOpen != null) Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.iconSecondary,
                modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
        }
    }
}

/**
 * Research further (D-E2): Guided Research and Practice Buy together as the learning actions, plus Earnings when the page has no Earnings
 * section.
 */
@Composable
private fun ResearchSection(researchCompleted: Int?, practice: Boolean, showEarningsAction: Boolean, onAction: (CompanyDetailsAction) -> Unit, modifier: Modifier) {
    SectionTitled(CompanyDetailsPresentation.RESEARCH_TITLE, modifier) {
        org.example.stocksteps.presentation.research.UnderstandStockCard(researchCompleted, { onAction(CompanyDetailsAction.Research) }, Modifier)
        if (practice) StockButton("Practice Buy", { onAction(CompanyDetailsAction.PracticeBuy) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.SECONDARY)
        if (showEarningsAction) StockButton("Earnings", { onAction(CompanyDetailsAction.Earnings) }, Modifier.fillMaxWidth(), variant = StockButtonVariant.SECONDARY)
    }
}

/**
 * Compact identity (logo, name that wraps, ticker · exchange) and the price as the focal point: price, signed change with its session
 * ("Today" or the quote's date), the US market status chip and the quote time. Sector and industry live in "What … does" and About (5D.5.4A,
 * D-E3); the actions follow the chart.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanyHeader(section: Section<CompanyOverview>, onRetry: () -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
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
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    StockTickerAvatar(overview.symbol, logoUrl = overview.logoUrl, size = StockStepsTheme.dimensions.avatar)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(overview.name, modifier = Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textTitle)
                        Text(overview.listing, style = typography.small, color = colors.textSupporting)
                    }
                }
                // Price block: the price is the focal point; the session chip sits beside it (never inside the green/red change line);
                // one spoken sentence for price, change and quote time.
                val updated = overview.updatedAt?.let(::formatQuoteTime)
                val changeLabel = remember(overview) { CompanyDetailsPresentation.changeLabel(overview) }
                Column(Modifier.padding(top = spacing.sm).clearAndSetSemantics {
                    contentDescription = listOfNotNull(CompanyDetailsPresentation.priceDescription(overview, updated, changeLabel),
                        CompanyDetailsPresentation.statusDescription(overview.marketStatus)?.let { "$it." }).joinToString(" ")
                },
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        itemVerticalAlignment = Alignment.CenterVertically) {
                        Text(overview.price ?: "—", Modifier.padding(end = spacing.sm), style = typography.largeNumber, color = colors.textValue)
                        MarketStatusIndicator(overview.marketStatus)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
                        StockPriceChange(
                            percentage = if (overview.changeAmount != null) "(${overview.changePercent})" else overview.changePercent,
                            direction = overview.direction,
                            amount = overview.changeAmount,
                            style = typography.numberMedium,
                            maxLines = 2
                        )
                        Text(changeLabel.text, style = typography.small, color = colors.textSupporting)
                    }
                    updated?.let { Text(stringResource(Res.string.details_updated, it), style = typography.caption, color = colors.textMeta) }
                }
            }
        }
    }
}

/**
 * Actions after the chart (5D.5.4A, D-E2): Add to portfolio (primary) and Compare share a row only while both measured labels fit their equal
 * share (shared `StockActionLayout`, same rule as iOS, Phase 4C.3); at large font scale or on narrow widths each takes its own full-width row,
 * so labels never clip or wrap. Practice Buy and Earnings moved to Research further.
 */
@Composable
private fun CompanyActions(onAction: (CompanyDetailsAction) -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = StockStepsTheme.typography.bodySemiBold
    val add: @Composable (Modifier) -> Unit = { m -> StockButton("Add to portfolio", { onAction(CompanyDetailsAction.AddPortfolio) }, m) }
    val compare: @Composable (Modifier) -> Unit = { m -> StockButton("Compare", { onAction(CompanyDetailsAction.Compare) }, m, variant = StockButtonVariant.SECONDARY) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widths = listOf("Add to portfolio", "Compare").map { with(density) { measurer.measure(it, labelStyle).size.width.toDp().value } }
        val sideBySide = org.example.stocksteps.theme.StockActionLayout.sideBySide(widths, maxWidth.value, spacing.lg.value, spacing.sm.value,
            accessibilityText = density.fontScale >= StockStepsTheme.dimensions.largeFontScale)
        if (sideBySide) Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            add(Modifier.weight(1f)); compare(Modifier.weight(1f))
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            add(Modifier.fillMaxWidth()); compare(Modifier.fillMaxWidth())
        }
    }
}

/**
 * Range selector, chart (touch to inspect), the quote stats and the day range, grouped on one borderless surface (5D.5.4A). The stats are a
 * `StockMetricGrid` (label above value; one column at large text) so a value never splits mid-word; High/Low are the Day Range bar (D-E4).
 */
@Composable
private fun ChartSection(
    section: Section<PriceChart>,
    issue: ChartIssue?,
    range: ChartRange,
    dayDirection: PriceDirection?,
    quickStats: List<InfoRow>?,
    dayRange: RangeSummary?,
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
        if (section == Section.Loading) {
            // Loading keeps the chart's full height (a skeleton of the chart), so it reads differently from the compact notice below.
            StockLoadingState(Modifier.fillMaxWidth().padding(top = spacing.xs).height(StockStepsTheme.dimensions.chartHeight)) {
                StockSkeleton(Modifier.fillMaxSize(), shape = StockStepsTheme.shapes.card)
            }
        } else if (summary == null) {
            // Phase 4C.3: a compact centered state instead of a chart-sized blank area; no flat or placeholder line is drawn. Connection and
            // temporary failures offer Try again; "no history" is an answer, so it suggests another range instead (Phase 4C.2 classification).
            val notice = ChartPresentation.notice(issue ?: ChartIssue.NO_HISTORY)
            if (notice.canRetry) StockErrorState(notice.message, onRetry, title = notice.title)
            else StockEmptyState(notice.message, title = notice.title)
        } else {
            Text(
                text = scrub?.let { index -> chart?.points?.getOrNull(index)?.let { ChartPresentation.scrubLabel(it, range) } } ?: summary.change,
                modifier = Modifier.padding(top = spacing.xs),
                style = StockStepsTheme.typography.label,
                color = when {
                    scrub != null -> colors.textPrimary
                    summary.direction == PriceDirection.UP -> colors.positiveText
                    summary.direction == PriceDirection.DOWN -> colors.negativeText
                    else -> colors.textSecondary
                }
            )
            StockLineChart(summary.closes, summary.direction, summary.yLabels, summary.xLabels, summary.description,
                Modifier.fillMaxWidth().padding(top = spacing.xs).height(StockStepsTheme.dimensions.chartHeight), onScrub = { scrub = it })
        }
        if (!quickStats.isNullOrEmpty() || dayRange != null) StockDivider(Modifier.padding(top = spacing.sm))
        if (!quickStats.isNullOrEmpty()) {
            StockMetricGrid(quickStats.map { StockMetricItem(it.label, it.value, quiet = true) }, Modifier.padding(top = spacing.sm), maxColumns = 2)
        }
        dayRange?.let { range ->
            val title = stringResource(Res.string.details_day_range)
            StockRangeBar(title, range.low, range.high, range.position, stringResource(Res.string.details_range_a11y, title, range.low, range.high),
                Modifier.padding(top = spacing.sm, bottom = spacing.xs))
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
                val sources = stringResource(Res.string.details_why_sources, why.sources.size)
                // The pill stays compact; the clickable box around it is a full 48dp touch target, read as "3 sources, button". The label
                // replaces the pill's own text (Phase 4C.3: a separate description node made TalkBack hear "3 sources" twice plus the arrow).
                Box(
                    Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(role = Role.Button, onClick = onSources),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$sources →",
                        modifier = Modifier.clearAndSetSemantics { contentDescription = sources }
                            .clip(StockStepsTheme.shapes.pill).background(colors.surface).padding(horizontal = spacing.sm, vertical = spacing.xs),
                        style = StockStepsTheme.typography.label,
                        color = colors.primaryText
                    )
                }
            }
        }
        // 5D.5.4B: the first sentence (what moved) as the lead, the rest of the same summary below in a quieter style.
        val shown = remember(why.summary) { CompanyDetailsPresentation.whyMoving(why.summary) }
        Text(shown.lead, style = StockStepsTheme.typography.bodySemiBold, color = colors.textPrimary)
        shown.rest?.let { Text(it, style = StockStepsTheme.typography.small, color = colors.textBody) }
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
        Text(
            "${stringResource(Res.string.details_why_more)} →",
            modifier = Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).wrapContentHeight()
                .clickable(role = Role.Button, onClick = onMore).padding(vertical = spacing.xs),
            style = StockStepsTheme.typography.label,
            color = colors.primaryText
        )
    }
}

/**
 * At a Glance: the key metrics in one `StockMetricGrid` (two columns, one at large text; values never truncate — qualifiers such as "(YoY)"
 * move to the supporting line) and the 52-week range under a divider. Tapping a metric opens its plain-English explanation.
 */
@Composable
private fun GlanceSection(data: CompanyOverview, onExplain: (String) -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val learn = stringResource(Res.string.details_learn_more)
    SectionTitled(stringResource(Res.string.details_glance_title), modifier) {
        StockCard(bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StockMetricGrid(data.glance.map { metric ->
                val shown = CompanyDetailsPresentation.glance(metric)
                // 5D.5.3 (D-C2): a small ⓘ marks metrics with a Learn term; the whole tile stays the 48 dp target.
                StockMetricItem(metric.label, shown.value, helper = shown.helper.ifBlank { null }, direction = metric.direction,
                    onClick = metric.educationId?.let { id -> { onExplain(id) } }, onClickLabel = learn,
                    info = metric.educationId?.let(org.example.stocksteps.learning.MetricLearnTerms::hasTerm) == true)
            }, maxColumns = 2)
            // The day range sits with the chart (5D.5.4A); the 52-week range stays here.
            data.yearRange?.let { range ->
                StockDivider()
                val title = stringResource(Res.string.details_year_range)
                StockRangeBar(title, range.low, range.high, range.position, stringResource(Res.string.details_range_a11y, title, range.low, range.high))
            }
        }
    }
}

/**
 * "Understand <company>": the factual assessment rows and the beginner insight. Facts first, interpretation second; About and Guided Research
 * have their own sections further down (5D.5.4A).
 */
@Composable
private fun UnderstandSection(data: CompanyOverview, onExplain: (String) -> Unit, onEvidence: () -> Unit, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    SectionTitled(CompanyDetailsPresentation.understandTitle(data), modifier) {
        if (data.assessment.isNotEmpty()) {
            StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
                // 5D.5.4B: Category → Interpretation → Supporting metric → Short explanation; every row opens its metric's explanation.
                CompanyDetailsPresentation.assessment(data).forEachIndexed { index, row ->
                    if (index > 0) StockDivider()
                    val (icon, container, tint) = when (row.id) {
                        "growth" -> Triple(StockIcons.TrendingUp, colors.positiveContainer, colors.positiveText)
                        "profitability" -> Triple(StockIcons.PieChart, colors.learnContainerStart, colors.learnAccent)
                        "health" -> Triple(StockIcons.Shield, colors.primaryContainer, colors.primaryText)
                        else -> Triple(StockIcons.Tag, colors.warningContainer, colors.cautionText)
                    }
                    StockAssessmentRow(
                        icon = icon, iconContainer = container, iconContent = tint,
                        title = row.title, detail = row.metric, badge = row.status, tone = row.tone, note = row.explanation,
                        onClick = row.metricId?.let { id -> { onExplain(id) } },
                        onClickLabel = stringResource(Res.string.details_learn_more),
                        statusBelowTitle = true
                    )
                }
            }
        }
        data.insight?.let { insight ->
            StockInsightCard(
                title = stringResource(Res.string.details_insight_title),
                body = insight,
                actionText = stringResource(Res.string.details_insight_why),
                onClick = onEvidence
            )
        }
    }
}

/** About (description with See more, country, sector and industry once as rows plus the context sentence); its own section since 5D.5.4A. */
@Composable
private fun AboutCard(data: CompanyOverview, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    var expanded by rememberSaveable(data.symbol) { mutableStateOf(false) }
    StockCard(modifier, bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        StockSectionHeader(
            title = stringResource(Res.string.details_about_title, data.shortName),
            actionText = data.aboutFull?.let { stringResource(if (expanded) Res.string.details_see_less else Res.string.details_see_more) },
            onActionClick = data.aboutFull?.let { { expanded = !expanded } }
        )
        val text = if (expanded) data.aboutFull ?: data.about else data.about
        text?.let { Text(it, style = StockStepsTheme.typography.small, color = colors.textBody) }
        data.country?.let { country ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Icon(StockIcons.Globe, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
                Text(country, style = StockStepsTheme.typography.small, color = colors.textSupporting)
            }
        }
        val classification = CompanyDetailsPresentation.classification(data)
        if (classification.isNotEmpty()) {
            StockDivider(Modifier.padding(vertical = spacing.xxs))
            classification.forEach { StockInfoRow(it.label, it.value, compact = true) }
            if (data.sector != null && data.industry != null) {
                Text(stringResource(Res.string.details_sector_body, data.shortName, data.sector.orEmpty(), data.industry.orEmpty()),
                    style = StockStepsTheme.typography.caption, color = colors.textMeta)
            }
        }
    }
}

/**
 * Valuation & financial health: current vs historical P/E (one metric row), the factual headline (unless Understand already says it) and explanation, links to the P/E lesson
 * and the detailed valuation; the financial highlights with their basis; key ratios not already shown in At a Glance.
 */
@Composable
private fun ValuationHealthSection(data: CompanyOverview, onValuation: () -> Unit, onFinancials: () -> Unit, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val valuation = data.valuation
    SectionTitled(CompanyDetailsPresentation.VALUATION_HEALTH_TITLE, modifier) {
        StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StockSectionHeader(stringResource(Res.string.details_valuation_title))
            if (valuation == null) {
                StockEmptyState(stringResource(Res.string.details_valuation_unavailable))
            } else {
                StockMetricGrid(listOf(
                    StockMetricItem(stringResource(Res.string.details_valuation_current), valuation.currentPe ?: "N/A"),
                    StockMetricItem(valuation.historicalLabel, valuation.historicalAverage ?: "—"),
                    StockMetricItem(stringResource(Res.string.details_valuation_difference), valuation.difference ?: "—")
                ), maxColumns = 3, rowsWhenNarrow = true)
                val accent = if (valuation.position == ValuationPosition.ABOVE) colors.cautionText else colors.primaryText
                CompanyDetailsPresentation.valuationHeadline(data)?.let { headline ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Icon(StockIcons.Tag, contentDescription = null, tint = accent, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
                        Text(headline, style = StockStepsTheme.typography.bodyMedium, color = accent)
                    }
                }
                Text(valuation.explanation, style = StockStepsTheme.typography.small, color = colors.textBody)
            }
            // 5D.5.4B: "Understand P/E Ratio" removed here; the same P/E sheet opens from the P/E tile and the Understand Valuation row.
            StockDivider()
            StockNavigationRow(stringResource(Res.string.details_see_valuation), onValuation, icon = StockIcons.TrendingUp,
                detail = "Historical P/E and how it has changed")
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StockSectionHeader(stringResource(Res.string.details_highlights_title), actionText = stringResource(Res.string.details_see_financials), onActionClick = onFinancials)
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
            data.highlightsBasis?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textMeta) }
        }
        val ratios = CompanyDetailsPresentation.keyRatios(data)
        if (ratios.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                StockSectionHeader(stringResource(Res.string.details_key_ratios))
                StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs)) {
                    ratios.forEach { StockInfoRow(it.label, it.value, compact = true) }
                }
            }
        }
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

/** Next earnings date, timing and status; never an invented date. */
@Composable
private fun EarningsSection(state: org.example.stocksteps.earnings.CompanyEarningsState, reminder: org.example.stocksteps.earnings.ReminderControl?, onAction: (CompanyDetailsAction) -> Unit, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val error = state.error
    val dateText = state.dateText
    SectionTitled("Earnings", modifier) {
        StockCard(Modifier.fillMaxWidth(), bordered = false, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            when {
                state.loading -> Text("Loading the next earnings date…", style = typography.small, color = colors.textSecondary)
                error != null -> StockErrorState(error, { onAction(CompanyDetailsAction.RetryEarnings) })
                dateText != null -> Column(Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Next earnings: ${state.spokenDate}. ${state.timingText}. ${state.statusText}."
                }) {
                    Text("Next earnings", style = typography.label, color = colors.textSecondary)
                    Text(dateText, style = typography.bodySemiBold, color = colors.textTitle)
                    state.timingText?.let { Text(it, style = typography.small, color = colors.textBody) }
                    state.statusText?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
                }
                else -> Text(state.message.orEmpty(), style = typography.small, color = colors.textSecondary)
            }
            reminder?.let { org.example.stocksteps.presentation.earnings.ReminderButton(it, compact = true) { onAction(CompanyDetailsAction.RemindEarnings) } }
            // Latest published results (comparable measures only), linking to the full Earnings Results.
            state.latestTitle?.let { title ->
                StockDivider()
                Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "$title. ${state.latestSummary.orEmpty()}." }) {
                    Text(title, style = typography.label, color = colors.textSecondary)
                    state.latestSummary?.let { Text(it, style = typography.bodySemiBold, color = colors.textPrimary) }
                }
                StockButton("View Results", onClick = { onAction(CompanyDetailsAction.LatestResults) }, variant = StockButtonVariant.OUTLINED)
                // Phase 5 preview: the AI explanation lives on Earnings Results (StockSteps+, checked by the server there).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
                    Text("AI Earnings Insight: a plain-English explanation of these results, built only from verified figures.", Modifier.weight(1f),
                        style = typography.caption, color = colors.textSecondary)
                    org.example.stocksteps.presentation.earnings.PlusBadge()
                }
                StockButton("Explain With AI", onClick = { onAction(CompanyDetailsAction.LatestResults) }, variant = StockButtonVariant.TEXT, icon = StockIcons.Lightbulb)
            }
            if (state.sampleData) Text("Sample earnings data.", style = typography.caption, color = colors.cautionText)
            StockDivider()
            StockNavigationRow("Earnings calendar", { onAction(CompanyDetailsAction.EarningsCalendar) }, icon = StockIcons.TrendingUp,
                detail = "Upcoming report dates across companies")
            StockDivider()
            StockNavigationRow("Earnings history", { onAction(CompanyDetailsAction.Earnings) }, icon = StockIcons.Document,
                detail = "Past results for this company")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EducationSheet(id: String, overview: CompanyOverview, onDismiss: () -> Unit, onLearn: (String) -> Unit = {}) {
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
    InfoSheet(title, onDismiss) {
        Text(body, style = StockStepsTheme.typography.body, color = StockStepsTheme.colors.textBody)
        // 5D.5.3 (D-C5): the existing company sentence, then the full Learn lesson one tap away.
        org.example.stocksteps.presentation.learn.MetricLearnMore(org.example.stocksteps.learning.MetricLearnTerms.termFor(id), onLearn, onDismiss)
    }
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
