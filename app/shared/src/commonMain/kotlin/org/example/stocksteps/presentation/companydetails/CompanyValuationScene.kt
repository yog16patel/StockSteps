package org.example.stocksteps.presentation.companydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.companydetail.*
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.model.AppBarBackButton
import org.example.stocksteps.model.AppBarConfiguration
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.components.StockStepsTopBar
import org.example.stocksteps.resources.*
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter
import org.jetbrains.compose.resources.stringResource

/** Owns the Valuation ViewModel; keyed by symbol and environment so Mock and Real never share state. */
@Composable
internal fun CompanyValuationScene(
    route: CompanyValuationRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    onBack: () -> Unit,
    /** 5D.5.3: opens a Learn term on top of this screen (Back returns here). */
    onLearnTerm: (String) -> Unit = {}
) {
    val model = viewModel(key = "company-valuation:${route.symbol}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        CompanyValuationViewModel(route.symbol, data.getValuationHistory(), data.getCompanyFundamentals(), data.getCompanyProfile(), data::close)
    }
    val state by model.state.collectAsStateWithLifecycle()
    CompanyValuationScreen(state, hinge, backIcon, onBack, onRange = model::selectRange, onRetry = model::load, onLearnTerm = onLearnTerm)
}

/**
 * Valuation: current P/E with its historical context, the P/E trend (1Y/3Y/5Y/10Y), an
 * explanation of P/E, other ratios, valuation vs growth, the historical range and a neutral
 * summary. Every value and sentence comes from [ValuationPresenter].
 */
@Composable
internal fun CompanyValuationScreen(
    state: CompanyValuationState,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    onBack: () -> Unit,
    onRange: (ValuationRange) -> Unit,
    onRetry: () -> Unit,
    onLearnTerm: (String) -> Unit = {}
) {
    // 5D.5.3: one explanation sheet for every tappable row; "Learn more" only for metrics with a Learn term.
    var explaining by remember { mutableStateOf<org.example.stocksteps.learning.MetricExplanation?>(null) }
    val explain: (InfoRow) -> (() -> Unit)? = { row -> row.metricId?.let { id -> org.example.stocksteps.learning.MetricLearnTerms.explain(id, row.label)?.let { e -> { explaining = e } } } }
    explaining?.let { e -> org.example.stocksteps.presentation.learn.MetricExplanationSheet(e, onLearnTerm) { explaining = null } }

    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val model = remember(state.history, state.fundamentals, state.range, state.shortName, state.profile?.currency) { state.model }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    val section = content.padding(top = spacing.xl)
    Column(Modifier.fillMaxSize().background(colors.appBackground)) {
        StockStepsTopBar(
            configuration = AppBarConfiguration(title = stringResource(Res.string.valuation_title, state.shortName), backButton = AppBarBackButton.BACK),
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
                        Text(state.listing, style = StockStepsTheme.typography.small, color = colors.textSecondary)
                        Text(stringResource(Res.string.valuation_tagline), style = StockStepsTheme.typography.body, color = colors.textBody)
                    }
                }
                if (model == null) {
                    item(key = "loading") {
                        StockLoadingState(section) {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                repeat(3) { StockSkeleton(Modifier.fillMaxWidth().height(StockStepsTheme.dimensions.chartHeight), shape = StockStepsTheme.shapes.card) }
                            }
                        }
                    }
                    return@LazyColumn
                }
                // 5D.5.4C C2: ratio tiles first (D-V1), then P/E vs History (current, average, difference, badge, chart — D-V3), one Learn link, the
                // P/E explanation folded; growth, range, insight and the neutral footnote (D-V2) follow.
                item(key = "metrics") {
                    Column(content.padding(top = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        StockSectionHeader(ValuationDisplay.KEY_METRICS_TITLE)
                        StockMetricGrid(ValuationDisplay.keyMetrics(model).map { m ->
                            val row = InfoRow(ValuationDisplay.plain(m.label), m.value, metricId = m.metricId)
                            StockMetricItem(m.label, m.value, helper = m.caption, onClick = explain(row), onClickLabel = explainLabel(row), info = hasTerm(row))
                        }, maxColumns = 2, tiled = true)
                        when {
                            state.fundamentals == Section.Unavailable -> StockCard(bordered = false) { StockErrorState(stringResource(Res.string.valuation_other_failed), onRetry) }
                            model.otherMetrics.isEmpty() -> Text(stringResource(Res.string.valuation_other_empty), style = StockStepsTheme.typography.small, color = colors.textSecondary)
                        }
                    }
                }
                item(key = "history") { HistorySection(model, state.history is org.example.stocksteps.presentation.companydetails.Section.Content, onRange, onRetry, section, onLearnTerm) }
                item(key = "understanding") { UnderstandingCard(model, content.padding(top = spacing.md)) }
                if (model.growth.size > 1) {
                    item(key = "growth") {
                        Column(section, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            StockSectionHeader(stringResource(Res.string.valuation_growth_title))
                            StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs)) {
                                model.growth.forEachIndexed { index, row ->
                                    if (index > 0) StockDivider()
                                    StockInfoRow(row.label, row.value, change = null, onClick = explain(row), onClickLabel = explainLabel(row), info = hasTerm(row))
                                }
                                Text(model.growthNote, Modifier.padding(vertical = spacing.sm), style = StockStepsTheme.typography.small, color = colors.textBody)
                            }
                        }
                    }
                }
                model.rangeSummary?.let { summary ->
                    item(key = "range") {
                        Column(section, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            StockSectionHeader(stringResource(Res.string.valuation_range_title))
                            StockCard(bordered = false, contentPadding = PaddingValues(spacing.md)) {
                                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                    summary.position?.let { position ->
                                        StockRangeBar("${model.range.label} P/E", summary.lowest, summary.highest, position,
                                            "${model.range.label} P/E range: lowest ${summary.lowest}, highest ${summary.highest}, current ${summary.current}")
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                        listOf(
                                            stringResource(Res.string.valuation_lowest) to summary.lowest,
                                            stringResource(Res.string.valuation_average) to summary.average,
                                            stringResource(Res.string.valuation_highest) to summary.highest,
                                            stringResource(Res.string.valuation_current) to (summary.current ?: "N/A")
                                        ).forEach { (label, value) ->
                                            StockMetric(label, value, Modifier.weight(1f))
                                        }
                                    }
                                    summary.percentile?.let { Text(it, style = StockStepsTheme.typography.small, color = colors.textBody) }
                                }
                            }
                        }
                    }
                }
                item(key = "insight") {
                    StockInsightCard(title = stringResource(Res.string.valuation_insight_title), body = model.insight, modifier = section)
                }
                item(key = "disclaimer") {
                    Column(content.padding(top = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(ValuationDisplay.FOOTNOTE, style = StockStepsTheme.typography.small, color = colors.textSecondary)
                        Text(stringResource(Res.string.details_disclaimer), style = StockStepsTheme.typography.caption, color = colors.textTertiary)
                    }
                }
            }
        }
    }
}

/**
 * P/E vs History (5D.5.4C C2): the current P/E beside its historical average and the difference (only when the model has a reliable average),
 * the existing badge (D-V3) and meaning sentence, then the range pills, the existing chart and its methodology — one borderless surface — and
 * the single "Learn more about P/E ratio" link.
 */
@Composable
private fun HistorySection(model: ValuationModel, loaded: Boolean, onRange: (ValuationRange) -> Unit, onRetry: () -> Unit, modifier: Modifier,
                           onLearnTerm: (String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(ValuationDisplay.HISTORY_TITLE)
        StockCard(bordered = false, contentPadding = PaddingValues(spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ValuationDisplay.comparison(model)?.let { c ->
                    StockMetricGrid(listOf(StockMetricItem(c.currentLabel, c.current), StockMetricItem(c.averageLabel, c.average),
                        StockMetricItem(stringResource(Res.string.valuation_difference), c.difference ?: "—")), maxColumns = 3, rowsWhenNarrow = true)
                }
                // A higher or lower P/E is not good or bad: neutral, caution amber only when above (existing rule).
                model.badge?.let { StockBadge(it, if (model.position == ValuationPosition.ABOVE) FactTone.CAUTION else FactTone.NEUTRAL) }
                Text(model.meaning, style = StockStepsTheme.typography.small, color = colors.textBody)
                StockDivider()
                if (!loaded) {
                    StockErrorState(model.historyMessage ?: stringResource(Res.string.financials_unavailable), onRetry)
                } else {
                    StockPillSelector(ValuationRange.entries, model.range, label = { it.label }, onSelect = onRange)
                    model.chart?.let { chart ->
                        StockTrendChart(chart.values, chart.details, chart.xLabels, chart.yLabels, chart.description,
                            reference = chart.average, referenceLabel = model.average?.let { "${model.averageLabel}: $it" })
                    }
                    Text(model.methodology, style = StockStepsTheme.typography.caption, color = colors.textSecondary)
                }
            }
        }
        // 5D.5.3: the full P/E lesson one tap away — once on this screen.
        org.example.stocksteps.learning.MetricLearnTerms.termFor("pe")?.let { term ->
            StockLinkAction(org.example.stocksteps.learning.MetricLearnTerms.learnMoreLabel(term), { onLearnTerm(term.id) },
                icon = org.example.stocksteps.designsystem.icons.StockIcons.School)
        }
    }
}

/** Understanding P/E Ratio, folded (5D.5.4C C2): the existing explanation and the Why high / Why low points behind one toggle. */
@Composable
private fun UnderstandingCard(model: ValuationModel, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.clip(StockStepsTheme.shapes.card).background(colors.primaryContainer).padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .clickable(onClickLabel = if (open) "Hide" else "Show", role = Role.Button) { open = !open }
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }, contentAlignment = Alignment.CenterStart) {
            Text("${ValuationDisplay.UNDERSTANDING_TITLE} ${if (open) "↑" else "↓"}", Modifier.semantics { heading() },
                style = StockStepsTheme.typography.bodySemiBold, color = colors.textPrimary)
        }
        if (open) {
            Text(model.understanding, style = StockStepsTheme.typography.small, color = colors.textBody)
            listOf(model.highReasons, model.lowReasons).forEach { explainer -> Explainer(explainer) }
        }
    }
}

@Composable
private fun Explainer(explainer: ValuationExplainer) {
    var open by rememberSaveable(explainer.title) { mutableStateOf(false) }
    val colors = StockStepsTheme.colors
    Box(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(role = Role.Button) { open = !open }, contentAlignment = Alignment.CenterStart) {
        Text("${explainer.title} ${if (open) "↑" else "↓"}", style = StockStepsTheme.typography.bodyMedium, color = colors.primaryText)
    }
    if (open) explainer.points.forEach { Text("• $it", style = StockStepsTheme.typography.small, color = colors.textBody) }
}
