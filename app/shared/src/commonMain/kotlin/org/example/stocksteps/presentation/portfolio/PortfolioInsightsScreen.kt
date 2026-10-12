package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.portfolio.analytics.*

/**
 * Portfolio insights (named "Portfolio Intelligence" before Portfolio Phase A). Order follows NUMBER → CONTEXT → EXPLANATION → EDUCATION: each section
 * leads with its figures, then what they're measured against, then a plain-language note, and the
 * Learn section explains every method. Locked and unavailable sections say why instead of showing 0.
 */
@Composable
internal fun PortfolioInsightsScreen(
    state: InsightsUiState,
    modifier: Modifier = Modifier,
    onSelectAccount: (String) -> Unit,
    onSelectPeriod: (AnalyticsPeriod) -> Unit,
    onSelectBenchmark: (BenchmarkId) -> Unit,
    onScenario: (String?) -> Unit,
    onRefresh: () -> Unit,
    onCompany: (String) -> Unit,
    onSignIn: () -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    val colors = StockStepsTheme.colors
    val view = state.view
    var allocationTab by rememberSaveable { mutableStateOf("holding") }
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                // C.1: the top bar already says "Portfolio insights"; a second large title repeated it.
                Text(listOfNotNull(state.accountName.takeIf { it.isNotBlank() }, view?.let { "Amounts in ${it.currency}" }, if (state.plus) "StockSteps+" else null).joinToString(" · "),
                    Modifier.padding(bottom = spacing.xs), style = typography.small, color = colors.textSupporting) // C.1 closure: it ran into the field label
                // Phase C: design-system chips (was Material FilterChip); account labels as on the dashboard.
                if (state.scenario == null && state.accounts.size > 1) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { selectableGroup() }, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        state.accounts.forEach { account ->
                            StockChip(PortfolioPresentation.accountLabel(account), account.id == state.selectedAccountId, onClick = { onSelectAccount(account.id) })
                        }
                    }
                }
                if (state.mockAvailable) {
                    // Readable scenario names (MOCK only), e.g. "Missing sectors" instead of "missing-sectors".
                    StockSelectField("Sample scenario", listOf<String?>(null) + state.scenarios, state.scenario, { it?.let(PortfolioDisplay::scenarioLabel) ?: "My accounts" },
                        onSelect = { onScenario(it) }, placeholder = "My accounts")
                    if (state.scenario != null) StockSampleDataBanner("Read-only sample data. Generated prices and index levels, not real markets.")
                }
            }
        }
        if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading insights" }) }
        state.error?.let { message -> item(key = "error") { StockErrorState(message, onRetry = onRefresh) } }
        if (!state.signedIn && state.scenario == null) {
            item(key = "signin") { StockEmptyState("Sign in to see insights about your portfolio.", actionText = "Sign in", onAction = onSignIn) }
            return@LazyColumn
        }
        if (view == null) {
            if (!state.loading && state.error == null) item(key = "empty") {
                StockEmptyState(if (state.accounts.isEmpty()) "Create a portfolio and record what you own to see insights." else "No insights yet.")
            }
            return@LazyColumn
        }

        // 1 · Health overview: transparent facts, no score.
        item(key = "health") {
            InsightsSection("Portfolio health", "Key facts about your portfolio. There's no single score; each number is explained.") {
                view.health.forEachIndexed { i, row -> if (i > 0) StockDivider(); MetricLine(row) }
            }
        }

        // 2 · Performance and benchmark.
        item(key = "performance") {
            InsightsSection("Performance", null) {
                PeriodSelector(view.periods, view.selectedPeriod, onSelectPeriod)
                SectionBody(view.performance) {
                    view.chart?.let { chart ->
                        StockTrendChart(
                            values = chart.portfolio,
                            details = chart.dates.indices.map { i ->
                                listOfNotNull(PortfolioDates.date(chart.dates[i]), chart.portfolio[i]?.let { "Portfolio ${fmt(it)}" }, chart.benchmark?.getOrNull(i)?.let { "${chart.benchmarkLabel} ${fmt(it)}" }).joinToString(" · ")
                            },
                            xLabels = listOf(PortfolioDates.shortDate(chart.dates.first()), PortfolioDates.shortDate(chart.dates.last())),
                            yLabels = emptyList(),
                            description = chart.description,
                            reference = 100.0,
                            comparison = chart.benchmark,
                            seriesLabel = chart.portfolioLabel,
                            comparisonLabel = chart.benchmarkLabel
                        )
                    }
                    view.performance.rows.forEachIndexed { i, row -> if (i > 0) StockDivider(); MetricLine(row) }
                }
            }
        }
        item(key = "benchmark") {
            InsightsSection("Compared with a market index", null) {
                if (view.benchmark.status != Availability.LOCKED) {
                    StockPillSelector(state.benchmarks.map { it.id }, state.benchmark, { id -> state.benchmarks.first { it.id == id }.name.replace("S&P/TSX Composite", "TSX") }, onSelectBenchmark)
                }
                SectionBody(view.benchmark) { view.benchmark.rows.forEach { MetricLine(it) } }
            }
        }

        // 3 · Allocation.
        item(key = "allocation") {
            InsightsSection("Allocation", null) {
                // C.1 closure: the pill selector used for the index above (shared StockPillLayout: equal width while every label fits, else a
                // fading scroll row) — four equal segments broke "Holding/s" and "Currenc/y" mid-word at 1.3×.
                StockPillSelector(view.allocation.map { it.id }, allocationTab, { id -> view.allocation.first { it.id == id }.title }, { allocationTab = it })
                val group = view.allocation.firstOrNull { it.id == allocationTab } ?: view.allocation.first()
                when (group.status) {
                    Availability.LOCKED -> LockedNotice(group.note)
                    Availability.AVAILABLE -> group.rows.forEach { AllocationLine(it) }
                    else -> Text(group.note ?: "Allocation isn't available.", style = typography.small, color = colors.textSecondary)
                }
                group.note?.takeIf { group.status == Availability.AVAILABLE }?.let { Note(it) }
            }
        }

        // 4 · Concentration.
        item(key = "concentration") {
            InsightsSection("Concentration", null) { SectionBody(view.concentration) { view.concentration.rows.forEach { MetricLine(it) } } }
        }

        // 5 · Contributors.
        item(key = "contributors") {
            InsightsSection("What moved your portfolio", "Over ${view.selectedPeriod.label}, in ${view.currency}") {
                SectionBody(view.contributors) {
                    if (view.positiveContributors.isNotEmpty()) SubHeading("Added the most")
                    view.positiveContributors.forEach { ContributorLine(it, onCompany) }
                    if (view.negativeContributors.isNotEmpty()) SubHeading("Subtracted the most")
                    view.negativeContributors.forEach { ContributorLine(it, onCompany) }
                    view.contributors.rows.forEach { MetricLine(it) }
                }
            }
        }

        // 6 · Dividends and currency.
        item(key = "dividends") {
            InsightsSection("Dividends", null) {
                SectionBody(view.dividends) {
                    view.dividends.rows.forEach { MetricLine(it) }
                    if (view.dividendsByYear.isNotEmpty()) { SubHeading("By year"); view.dividendsByYear.forEach { MetricLine(it) } }
                    if (view.dividendsByCompany.isNotEmpty()) { SubHeading("By company"); view.dividendsByCompany.take(5).forEach { MetricLine(it) } }
                }
            }
        }
        item(key = "currency") {
            InsightsSection("Currency exposure", null) {
                SectionBody(view.currencyExposure) {
                    view.currencySlices.forEach { AllocationLine(it) }
                    view.currencyExposure.rows.forEach { MetricLine(it) }
                }
            }
        }

        // 7 · Insights (deterministic, from the numbers above).
        if (view.insights.isNotEmpty()) item(key = "insights") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                StockSectionHeader(PortfolioDisplay.INSIGHTS_LIST_TITLE)
                view.insights.forEach { insight ->
                    StockInsightCard(insight.title, insight.explanation,
                        eyebrow = listOfNotNull(PortfolioInsightIcons.label(insight.category), insight.period?.label, if (insight.completeness != Availability.AVAILABLE) "Partial data" else null).joinToString(" · "),
                        leading = { StockIconTile(insightIcon(insight.category), colors.primaryContainer, colors.primaryText) })
                }
            }
        }

        // 8 · Learn more.
        item(key = "learn") {
            InsightsSection("How these numbers work", null) {
                AnalyticsEducation.entries.forEach { LearnLine(it) }
            }
        }
        item(key = "notes") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                (view.notes + "Not investment advice. Past performance doesn't indicate future results.").forEach { Note(it) }
                Text(PortfolioDisplay.asOf(view.asOf), style = typography.caption, color = colors.textTertiary)
                TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)) { Text("Refresh insights") }
            }
        }
    }
}

private fun fmt(value: Double) = ((value * 100).let { kotlin.math.round(it) } / 100).toString()

@Composable
private fun InsightsSection(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    // Phase C: borderless sections like the Portfolio dashboard; in-card titles use the card-title role.
    StockCard(bordered = false) {
        Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            Text(title, style = StockStepsTheme.typography.cardTitle, color = StockStepsTheme.colors.textTitle, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSupporting) }
            content()
        }
    }
}

@Composable
private fun SectionBody(section: SectionView, content: @Composable () -> Unit) {
    when (section.status) {
        Availability.LOCKED -> LockedNotice(section.message)
        Availability.UNAVAILABLE -> Text(section.message ?: "Not available.", style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary)
        else -> {
            content()
            section.notes.take(3).forEach { Note(it) }
        }
    }
}

@Composable
private fun LockedNotice(message: String?) {
    val colors = StockStepsTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).background(colors.primaryContainer).padding(StockStepsTheme.spacing.md)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)
    ) {
        Text("StockSteps+", style = StockStepsTheme.typography.label, color = colors.primaryText)
        Text(message ?: "Available with StockSteps+.", style = StockStepsTheme.typography.small, color = colors.textBody)
    }
}

@Composable
private fun MetricLine(row: MetricRow) {
    val colors = StockStepsTheme.colors
    val valueColor = when (row.tone) { Tone.POSITIVE -> colors.positiveText; Tone.NEGATIVE -> colors.negativeText; Tone.NEUTRAL -> colors.textValue }
    // Phase C: label in the supporting colour, value strong (was all near-white body text in dark mode); value under the label when the
    // label's longest word and the value can't share the line (shared StockLayout.metricRowStacked), so nothing breaks mid-word.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = listOfNotNull(row.accessibility, row.detail).joinToString(". ") }) {
        val word = row.label.split(' ').maxOfOrNull { measurer.measure(it, StockStepsTheme.typography.small).size.width } ?: 0
        val value = measurer.measure(row.value, StockStepsTheme.typography.numberLabelStrong).size.width
        // C.1: at large text every row stacks (one decision for the list — rows decided one by one alternated at 2.0×).
        val stacked = density.fontScale >= StockStepsTheme.dimensions.largeFontScale ||
            with(density) { org.example.stocksteps.theme.StockLayout.metricRowStacked(word.toDp().value, value.toDp().value, maxWidth.value) }
        Column(Modifier.fillMaxWidth().padding(vertical = StockStepsTheme.spacing.xxs), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            if (stacked) {
                Text(row.label, style = StockStepsTheme.typography.small, color = colors.textSupporting)
                Text(row.value, style = StockStepsTheme.typography.numberLabelStrong, color = valueColor)
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm), verticalAlignment = Alignment.Top) {
                Text(row.label, Modifier.weight(1f), style = StockStepsTheme.typography.small, color = colors.textSupporting)
                Text(row.value, style = StockStepsTheme.typography.numberLabelStrong, color = valueColor)
            }
            row.detail?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textMeta) }
        }
    }
}

@Composable
private fun AllocationLine(row: AllocationRowView) {
    val colors = StockStepsTheme.colors
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = row.accessibility }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        // C.1: value under the label at large text (Android 2.0×), as on iOS.
        if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale) {
            Text(row.label, style = StockStepsTheme.typography.small, color = colors.textBody)
            Text("${row.percent} · ${row.value}", style = StockStepsTheme.typography.small, color = colors.textSupporting)
        } else Row(Modifier.fillMaxWidth()) {
            Text(row.label, Modifier.weight(1f), style = StockStepsTheme.typography.small, color = colors.textBody)
            Text("${row.percent} · ${row.value}", style = StockStepsTheme.typography.small, color = colors.textSupporting)
        }
        Box(Modifier.fillMaxWidth().height(StockStepsTheme.dimensions.rangeBar).clip(StockStepsTheme.shapes.pill).background(colors.surfaceSecondary)) {
            Box(Modifier.fillMaxWidth(row.fraction).fillMaxHeight().clip(StockStepsTheme.shapes.pill).background(colors.primary))
        }
    }
}

@Composable
private fun ContributorLine(row: ContributorView, onCompany: (String) -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val amountColor = if (row.tone == Tone.NEGATIVE) colors.negativeText else colors.positiveText
    val detail = listOfNotNull(row.percent, row.fx?.let { "FX $it" }).joinToString(" · ")
    // C.1: at large text the amounts go under the name — side by side the name column broke words ("Micros / oft") at 2.0×.
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= StockStepsTheme.dimensions.largeFontScale
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(onClickLabel = "Open ${row.symbol}") { onCompany(row.symbol) }
        .clearAndSetSemantics { contentDescription = row.accessibility; role = Role.Button }
    if (largeText) {
        Column(rowModifier.padding(vertical = StockStepsTheme.spacing.xxs), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            Text(row.symbol, style = typography.label, color = colors.textTitle)
            row.name?.let { Text(it, style = typography.caption, color = colors.textSupporting) }
            Text(row.amount, style = typography.numberLabelStrong, color = amountColor)
            if (detail.isNotEmpty()) Text(detail, style = typography.caption, color = colors.textSupporting)
        }
    } else Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(row.symbol, style = typography.label, color = colors.textTitle)
            row.name?.let { Text(it, style = typography.caption, color = colors.textSupporting) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.amount, style = typography.label, color = amountColor)
            Text(detail, style = typography.caption, color = colors.textSupporting)
        }
    }
}

@Composable
private fun PeriodSelector(options: List<PeriodOption>, selected: AnalyticsPeriod, onSelect: (AnalyticsPeriod) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroupCompat(), horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        options.forEach { option ->
            StockChip(option.label, option.period == selected, onClick = { onSelect(option.period) }, enabled = option.enabled,
                modifier = Modifier.semantics { if (!option.enabled) stateDescription = "Not enough history" })
        }
    }
}

private fun Modifier.selectableGroupCompat() = semantics { selectableGroup() }

@Composable
private fun LearnLine(entry: AnalyticsEducation.Entry) {
    var expanded by rememberSaveable(entry.key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse" else "Expand") { expanded = !expanded }
        .heightIn(min = StockStepsTheme.dimensions.touchTarget).semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
        // C.1 closure: a disclosure chevron like "By currency" (and the iOS DisclosureGroup) — the rows read as plain text before.
        Row(Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget), verticalAlignment = Alignment.CenterVertically) {
            Text(entry.title, Modifier.weight(1f).padding(vertical = StockStepsTheme.spacing.xs), style = StockStepsTheme.typography.label)
            Icon(StockIcons.ChevronDown, contentDescription = null, tint = StockStepsTheme.colors.iconSecondary,
                modifier = Modifier.size(StockStepsTheme.dimensions.icon).graphicsLayer { rotationZ = if (expanded) 180f else 0f })
        }
        if (expanded) Text(entry.body, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)
    }
}

/** C.1: sub-group title inside a section (label role, supporting colour, heading for screen readers, space above). */
@Composable
private fun SubHeading(text: String) = Text(text, Modifier.padding(top = StockStepsTheme.spacing.xs).semantics { heading() },
    style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.textSupporting)

@Composable
private fun Note(text: String) = Text(text, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textMeta)

/** Insight category icon (shared mapping `PortfolioInsightIcons`), decorative — the eyebrow names the category. */
private fun insightIcon(category: InsightCategory) = when (PortfolioInsightIcons.icon(category)) {
    PortfolioInsightIcons.Icon.LAYERS -> StockIcons.Layers
    PortfolioInsightIcons.Icon.PIE -> StockIcons.PieChart
    PortfolioInsightIcons.Icon.TREND -> StockIcons.TrendingUp
    PortfolioInsightIcons.Icon.BAR -> StockIcons.BarChart
    PortfolioInsightIcons.Icon.SPEED -> StockIcons.Speed
    PortfolioInsightIcons.Icon.COIN -> StockIcons.Coin
    PortfolioInsightIcons.Icon.GLOBE -> StockIcons.Globe
    PortfolioInsightIcons.Icon.INFO -> StockIcons.Info
}
