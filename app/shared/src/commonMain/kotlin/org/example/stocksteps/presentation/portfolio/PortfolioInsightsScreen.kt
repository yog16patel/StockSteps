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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.portfolio.analytics.*

/**
 * Portfolio Intelligence. Order follows NUMBER → CONTEXT → EXPLANATION → EDUCATION: each section
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
                Text("Portfolio Intelligence", style = typography.screenTitle, modifier = Modifier.semantics { heading() })
                Text(listOfNotNull(state.accountName.takeIf { it.isNotBlank() }, view?.let { "Amounts in ${it.currency}" }, if (state.plus) "StockSteps+" else null).joinToString(" · "),
                    style = typography.small, color = colors.textSecondary)
                if (state.scenario == null && state.accounts.size > 1) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        state.accounts.forEach { account ->
                            FilterChip(account.id == state.selectedAccountId, onClick = { onSelectAccount(account.id) },
                                label = { Text("${account.name} · ${account.reportingCurrency.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) })
                        }
                    }
                }
                if (state.mockAvailable) {
                    PortfolioChoice("Sample scenario", state.scenario ?: "My accounts", listOf("My accounts") + state.scenarios) { onScenario(it.takeUnless { it == "My accounts" }) }
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
                view.health.forEach { MetricLine(it) }
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
                                listOfNotNull(chart.dates[i], chart.portfolio[i]?.let { "Portfolio ${fmt(it)}" }, chart.benchmark?.getOrNull(i)?.let { "${chart.benchmarkLabel} ${fmt(it)}" }).joinToString(" · ")
                            },
                            xLabels = listOf(chart.dates.first(), chart.dates.last()),
                            yLabels = emptyList(),
                            description = chart.description,
                            reference = 100.0,
                            comparison = chart.benchmark,
                            seriesLabel = chart.portfolioLabel,
                            comparisonLabel = chart.benchmarkLabel
                        )
                    }
                    view.performance.rows.forEach { MetricLine(it) }
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
                StockSegmentedControl(view.allocation.map { StockSegment(it.id, it.title) }, allocationTab, { allocationTab = it })
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
                    if (view.positiveContributors.isNotEmpty()) Text("Added the most", style = typography.label)
                    view.positiveContributors.forEach { ContributorLine(it, onCompany) }
                    if (view.negativeContributors.isNotEmpty()) Text("Subtracted the most", style = typography.label)
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
                    if (view.dividendsByYear.isNotEmpty()) { Text("By year", style = typography.label); view.dividendsByYear.forEach { MetricLine(it) } }
                    if (view.dividendsByCompany.isNotEmpty()) { Text("By company", style = typography.label); view.dividendsByCompany.take(5).forEach { MetricLine(it) } }
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
                StockSectionHeader("Insights")
                view.insights.forEach { insight ->
                    StockInsightCard(insight.title, insight.explanation,
                        eyebrow = listOfNotNull(insight.category.name.lowercase().replaceFirstChar(Char::uppercase), insight.period?.label, if (insight.completeness != Availability.AVAILABLE) "Partial data" else null).joinToString(" · "))
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
                Text("As of ${view.asOf.take(16).replace('T', ' ')} UTC", style = typography.caption, color = colors.textTertiary)
                TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh insights") }
            }
        }
    }
}

private fun fmt(value: Double) = ((value * 100).let { kotlin.math.round(it) } / 100).toString()

@Composable
private fun InsightsSection(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    StockCard {
        Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            Text(title, style = StockStepsTheme.typography.sectionTitle, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSecondary) }
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
    val valueColor = when (row.tone) { Tone.POSITIVE -> colors.positiveText; Tone.NEGATIVE -> colors.negativeText; Tone.NEUTRAL -> colors.textPrimary }
    Column(Modifier.fillMaxWidth().heightIn(min = 40.dp).clearAndSetSemantics { contentDescription = listOfNotNull(row.accessibility, row.detail).joinToString(". ") }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm), verticalAlignment = Alignment.Top) {
            Text(row.label, Modifier.weight(1f), style = StockStepsTheme.typography.body, color = colors.textBody)
            Text(row.value, style = StockStepsTheme.typography.label, color = valueColor)
        }
        row.detail?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
    }
}

@Composable
private fun AllocationLine(row: AllocationRowView) {
    val colors = StockStepsTheme.colors
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = row.accessibility }, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        Row(Modifier.fillMaxWidth()) {
            Text(row.label, Modifier.weight(1f), style = StockStepsTheme.typography.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${row.percent} · ${row.value}", style = StockStepsTheme.typography.small, color = colors.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(StockStepsTheme.shapes.pill).background(colors.surfaceSecondary)) {
            Box(Modifier.fillMaxWidth(row.fraction).fillMaxHeight().clip(StockStepsTheme.shapes.pill).background(colors.primary))
        }
    }
}

@Composable
private fun ContributorLine(row: ContributorView, onCompany: (String) -> Unit) {
    val colors = StockStepsTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "Open ${row.symbol}") { onCompany(row.symbol) }
            .clearAndSetSemantics { contentDescription = row.accessibility; role = Role.Button },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.symbol, style = StockStepsTheme.typography.label)
            row.name?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.amount, style = StockStepsTheme.typography.label, color = if (row.tone == Tone.NEGATIVE) colors.negativeText else colors.positiveText)
            Text(listOfNotNull(row.percent, row.fx?.let { "FX $it" }).joinToString(" · "), style = StockStepsTheme.typography.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun PeriodSelector(options: List<PeriodOption>, selected: AnalyticsPeriod, onSelect: (AnalyticsPeriod) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroupCompat(), horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        options.forEach { option ->
            FilterChip(
                selected = option.period == selected,
                onClick = { onSelect(option.period) },
                enabled = option.enabled,
                label = { Text(option.label, maxLines = 1) },
                modifier = Modifier.semantics { if (!option.enabled) stateDescription = "Not enough history" }
            )
        }
    }
}

private fun Modifier.selectableGroupCompat() = semantics { selectableGroup() }

@Composable
private fun LearnLine(entry: AnalyticsEducation.Entry) {
    var expanded by rememberSaveable(entry.key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { expanded = !expanded }
        .heightIn(min = 48.dp).semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
        Text(entry.title, style = StockStepsTheme.typography.label, modifier = Modifier.padding(vertical = StockStepsTheme.spacing.xs))
        if (expanded) Text(entry.body, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody)
    }
}

@Composable
private fun Note(text: String) = Text(text, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
