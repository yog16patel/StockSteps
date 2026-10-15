package org.example.stocksteps.presentation.companydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.companydetail.*
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.model.AppBarBackButton
import org.example.stocksteps.model.AppBarConfiguration
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.components.StockStepsTopBar
import org.example.stocksteps.resources.*
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter
import org.jetbrains.compose.resources.stringResource

/** Owns the Financials ViewModel; keyed by symbol and environment so Mock and Real never share state. */
@Composable
internal fun CompanyFinancialsScene(
    route: CompanyFinancialsRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    onBack: () -> Unit,
    /** 5D.5.3: opens a Learn term on top of this screen (Back returns here). */
    onLearnTerm: (String) -> Unit = {}
) {
    val model = viewModel(key = "company-financials:${route.symbol}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        CompanyFinancialsViewModel(route.symbol, data.getCompanyFundamentals(), data.getCompanyProfile(), data::close)
    }
    val state by model.state.collectAsStateWithLifecycle()
    CompanyFinancialsScreen(
        state = state,
        hinge = hinge,
        backIcon = backIcon,
        onBack = onBack,
        onFrequency = model::selectFrequency,
        onRange = model::selectRange,
        onRetry = model::load,
        onLearnTerm = onLearnTerm
    )
}

/**
 * Financials: one vertical page — header, Annual/Quarterly and 3Y/5Y/10Y selectors, then revenue,
 * profitability, cash flow, financial health, EPS, dividends, a factual summary, detailed table,
 * advanced metrics and valuation. Every number and sentence comes from the shared presenter.
 */
@Composable
internal fun CompanyFinancialsScreen(
    state: CompanyFinancialsState,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    onBack: () -> Unit,
    onFrequency: (FinancialPeriod) -> Unit,
    onRange: (FinancialRange) -> Unit,
    onRetry: () -> Unit,
    onLearnTerm: (String) -> Unit = {}
) {
    // 5D.5.3: one explanation sheet for every tappable row; "Learn more" only for metrics with a Learn term.
    var explaining by remember { mutableStateOf<org.example.stocksteps.learning.MetricExplanation?>(null) }
    val explain: (InfoRow) -> (() -> Unit)? = { row -> row.metricId?.let { id -> org.example.stocksteps.learning.MetricLearnTerms.explain(id, row.label)?.let { e -> { explaining = e } } } }
    explaining?.let { e -> org.example.stocksteps.presentation.learn.MetricExplanationSheet(e, onLearnTerm) { explaining = null } }

    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val model = remember(state.fundamentals, state.frequency, state.range, state.shortName) { state.model }
    val valuation = remember(state.loaded[FinancialPeriod.ANNUAL]) {
        state.loaded[FinancialPeriod.ANNUAL]?.let { CompanyDetailPresenter.build(StockSearchResult(state.symbol, state.symbol), null, null, it).valuation }.orEmpty()
    }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    val section = content.padding(top = spacing.xl)
    Column(Modifier.fillMaxSize().background(colors.appBackground)) {
        StockStepsTopBar(
            configuration = AppBarConfiguration(title = stringResource(Res.string.financials_title, state.shortName), backButton = AppBarBackButton.BACK),
            onBack = onBack,
            backIcon = backIcon
        )
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(
                modifier = region,
                contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item(key = "header") {
                    Column(content, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(state.listing ?: state.symbol, style = StockStepsTheme.typography.small, color = colors.textSecondary)
                        Text(stringResource(Res.string.financials_tagline), style = StockStepsTheme.typography.body, color = colors.textBody)
                        model?.periodLabel?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textTertiary) }
                    }
                }
                item(key = "selectors") {
                    StockCard(content.padding(top = spacing.md), bordered = false, contentPadding = PaddingValues(horizontal = spacing.sm, vertical = spacing.xxs)) {
                        StockPillSelector(FinancialPeriod.entries, state.frequency, label = { it.label }, onSelect = onFrequency)
                        StockDivider()
                        StockPillSelector(FinancialRange.entries, state.range, label = { it.label }, onSelect = onRange)
                    }
                    Column(content) {
                        if (state.loading && model != null) {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = spacing.xs), color = colors.primary, trackColor = colors.borderSubtle)
                        }
                        model?.rangeNote?.let { Text(it, Modifier.padding(top = spacing.xs), style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
                        model?.staleNotice?.let { Text(it, Modifier.padding(top = spacing.xs), style = StockStepsTheme.typography.caption, color = colors.cautionText) }
                    }
                }
                when {
                    model == null && state.loading -> item(key = "loading") {
                        StockLoadingState(section) {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                repeat(3) { StockSkeleton(Modifier.fillMaxWidth().height(StockStepsTheme.dimensions.chartHeight), shape = StockStepsTheme.shapes.card) }
                            }
                        }
                    }
                    model == null -> item(key = "error") {
                        StockCard(section, bordered = false) { StockErrorState(stringResource(Res.string.financials_load_failed), onRetry) }
                    }
                    model.emptyMessage != null -> item(key = "empty") {
                        StockCard(section, bordered = false) { StockErrorState(model.emptyMessage!!, onRetry) }
                    }
                    else -> {
                        model.sections.forEach { financial ->
                            item(key = financial.id) { FinancialSectionCard(financial, section, explain, onLearnTerm) }
                        }
                        if (model.summary.isNotEmpty()) {
                            item(key = "summary") { SummaryCard(model, section) }
                        }
                        model.table?.let { table ->
                            item(key = "table") {
                                Expandable(stringResource(Res.string.financials_table_show), stringResource(Res.string.financials_table_hide), section) {
                                    StockDataTable(table.columns, table.rows, stringResource(Res.string.financials_table_metric))
                                }
                            }
                        }
                        if (model.advanced.isNotEmpty()) {
                            item(key = "advanced") {
                                Expandable(stringResource(Res.string.financials_advanced), stringResource(Res.string.financials_advanced), section) {
                                    model.advanced.forEach { StockInfoRow(it.label, it.value, compact = true, onClick = explain(it), onClickLabel = explainLabel(it),
                                        info = hasTerm(it)) }
                                }
                            }
                        }
                        if (valuation.isNotEmpty()) {
                            item(key = "valuation") {
                                Column(section, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                    StockSectionHeader(stringResource(Res.string.financials_valuation))
                                    StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
                                        valuation.forEachIndexed { index, metric ->
                                            if (index > 0) StockDivider()
                                            val row = InfoRow(metric.label, CompanyDetailPresenter.metricValue(metric), metricId = metric.id)
                                            StockInfoRow(metric.label, CompanyDetailPresenter.metricValue(metric), helper = CompanyDetailPresenter.historicalContext(metric),
                                                onClick = explain(row), onClickLabel = explainLabel(row), info = hasTerm(row))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One financial area: headline + change, plain-English meaning, chart, rows, comparisons, explanation. */
@Composable
private fun FinancialSectionCard(section: FinancialStatementSection, modifier: Modifier, explain: (InfoRow) -> (() -> Unit)? = { null },
                                 onLearnTerm: (String) -> Unit = {}) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    var learn by rememberSaveable(section.id) { mutableStateOf(false) }
    var more by rememberSaveable(section.id) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(section.title)
        StockCard(bordered = false, contentPadding = PaddingValues(spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (section.availability != SectionAvailability.CONTENT) {
                    Text(
                        section.meaning ?: stringResource(if (section.availability == SectionAvailability.TEMPORARILY_UNAVAILABLE) Res.string.financials_unavailable else Res.string.financials_not_reported),
                        style = StockStepsTheme.typography.body, color = colors.textSecondary
                    )
                } else {
                    section.headlineLabel?.let { Text(it, style = StockStepsTheme.typography.label, color = colors.textSecondary) }
                    section.headline?.let { headline ->
                        Text(headline, style = StockStepsTheme.typography.largeNumber, color = colors.textPrimary)
                    }
                    if (section.change != null && section.changeDirection != null) {
                        StockPriceChange(section.change!!, section.changeDirection!!, style = StockStepsTheme.typography.numberMedium)
                    }
                    section.meaning?.let { Text(it, style = StockStepsTheme.typography.small, color = colors.textBody) }
                    section.chart?.let { chart ->
                        StockBarChart(chart.bars, chart.description, secondary = chart.secondary, primaryLabel = chart.primaryLabel, secondaryLabel = chart.secondaryLabel)
                    }
                    if (section.rows.isNotEmpty()) {
                        Column {
                            section.rows.forEachIndexed { index, row ->
                                if (index > 0) StockDivider()
                                StockInfoRow(row.label, row.value, helper = row.helper, change = row.change, direction = row.changeDirection,
                                    onClick = explain(row), onClickLabel = explainLabel(row), info = hasTerm(row))
                            }
                        }
                    }
                    section.comparisons.forEach {
                        StockComparisonBars(it.title, it.firstLabel, it.first, it.firstText, it.secondLabel, it.second, it.secondText, it.caption)
                    }
                    if (section.moreRows.isNotEmpty()) {
                        Toggle(stringResource(if (more) Res.string.financials_hide_ratios else Res.string.financials_show_ratios)) { more = !more }
                        if (more) section.moreRows.forEach { StockInfoRow(it.label, it.value, helper = it.helper, compact = true, onClick = explain(it),
                            onClickLabel = explainLabel(it), info = hasTerm(it)) }
                    }
                    section.note?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textTertiary) }
                }
                Toggle(stringResource(if (learn) Res.string.financials_learn_hide else Res.string.financials_learn)) { learn = !learn }
                if (learn) {
                    Column(
                        Modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).background(colors.primaryContainer).padding(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        Text(section.whatIsIt, style = StockStepsTheme.typography.small, color = colors.textBody)
                        Text(stringResource(Res.string.financials_why_matters), style = StockStepsTheme.typography.label, color = colors.primaryText)
                        Text(section.whyItMatters, style = StockStepsTheme.typography.small, color = colors.textBody)
                        // 5D.5.3: a section headline with a Learn term (Revenue, EPS) links to the full lesson.
                        org.example.stocksteps.learning.MetricLearnTerms.termFor(section.id)?.let { term ->
                            StockLinkAction(org.example.stocksteps.learning.MetricLearnTerms.learnMoreLabel(term), { onLearnTerm(term.id) }, icon = StockIcons.School)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(model: FinancialStatementsModel, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(
        modifier.clip(StockStepsTheme.shapes.cardLarge).background(colors.primaryContainer).padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Text(stringResource(Res.string.financials_summary_title), Modifier.semantics { heading() },
            style = StockStepsTheme.typography.cardTitle.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
        model.summary.forEach { line ->
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text(line.title, style = StockStepsTheme.typography.bodySemiBold, color = colors.textPrimary)
                    Text(line.text, style = StockStepsTheme.typography.small, color = colors.textBody)
                }
            }
        }
        model.summaryLimitations?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
        Text(stringResource(Res.string.details_disclaimer), style = StockStepsTheme.typography.caption, color = colors.textTertiary)
    }
}

@Composable
private fun Expandable(showLabel: String, hideLabel: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(showLabel) { mutableStateOf(false) }
    StockCard(modifier, bordered = false, contentPadding = PaddingValues(horizontal = StockStepsTheme.spacing.md, vertical = StockStepsTheme.spacing.xxs)) {
        Toggle(if (open) hideLabel else showLabel) { open = !open }
        if (open) Column(Modifier.padding(bottom = StockStepsTheme.spacing.sm), content = content)
    }
}

/** Text link with a full touch target; announced as a button. */
@Composable
private fun Toggle(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text("$text →", style = StockStepsTheme.typography.bodyMedium, color = StockStepsTheme.colors.primaryText)
    }
}

/** 5D.5.3: spoken label for a tappable row ("Explain Net Margin"); null when the row has no explanation. */
internal fun explainLabel(row: InfoRow): String? = row.metricId?.let { org.example.stocksteps.learning.MetricLearnTerms.explainLabel(row.label) }

/** 5D.5.3 (D-C2): the ⓘ marks only rows whose metric has a Learn term. */
internal fun hasTerm(row: InfoRow): Boolean = row.metricId?.let(org.example.stocksteps.learning.MetricLearnTerms::hasTerm) == true
