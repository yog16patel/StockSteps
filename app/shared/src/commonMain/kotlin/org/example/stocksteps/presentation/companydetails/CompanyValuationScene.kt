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
    onBack: () -> Unit
) {
    val model = viewModel(key = "company-valuation:${route.symbol}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        CompanyValuationViewModel(route.symbol, data.getValuationHistory(), data.getCompanyFundamentals(), data.getCompanyProfile(), data::close)
    }
    val state by model.state.collectAsStateWithLifecycle()
    CompanyValuationScreen(state, hinge, backIcon, onBack, onRange = model::selectRange, onRetry = model::load)
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
    onRetry: () -> Unit
) {
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
                item(key = "current") { CurrentCard(model, content.padding(top = spacing.md)) }
                item(key = "trend") { TrendSection(model, state.history is org.example.stocksteps.presentation.companydetails.Section.Content, onRange, onRetry, section) }
                item(key = "understanding") { UnderstandingCard(model, section) }
                item(key = "other") {
                    Column(section, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        StockSectionHeader(stringResource(Res.string.valuation_other_title))
                        StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
                            when {
                                state.fundamentals == Section.Unavailable -> StockErrorState(stringResource(Res.string.valuation_other_failed), onRetry, Modifier.padding(vertical = spacing.sm))
                                model.otherMetrics.isEmpty() -> StockEmptyState(stringResource(Res.string.valuation_other_empty), Modifier.padding(vertical = spacing.sm))
                                else -> model.otherMetrics.forEachIndexed { index, row ->
                                    if (index > 0) StockDivider()
                                    StockInfoRow(row.label, row.value, helper = row.helper)
                                }
                            }
                        }
                    }
                }
                if (model.growth.size > 1) {
                    item(key = "growth") {
                        Column(section, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            StockSectionHeader(stringResource(Res.string.valuation_growth_title))
                            StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs)) {
                                model.growth.forEachIndexed { index, row ->
                                    if (index > 0) StockDivider()
                                    StockInfoRow(row.label, row.value, change = null)
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
                    Text(stringResource(Res.string.details_disclaimer), content.padding(top = spacing.lg), style = StockStepsTheme.typography.caption, color = colors.textTertiary)
                }
            }
        }
    }
}

/** Primary card: large P/E, neutral badge, what it means, and the historical comparison. */
@Composable
private fun CurrentCard(model: ValuationModel, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    StockCard(modifier, bordered = false, contentPadding = PaddingValues(spacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(stringResource(Res.string.valuation_current_title), style = StockStepsTheme.typography.label, color = colors.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(model.currentPe, Modifier.semantics { heading() }, style = StockStepsTheme.typography.largeNumber, color = colors.textPrimary)
                // A higher or lower P/E is not good or bad: informational blue, caution amber only when above.
                model.badge?.let { StockBadge(it, if (model.position == ValuationPosition.ABOVE) FactTone.CAUTION else FactTone.NEUTRAL) }
            }
            Text(model.meaning, style = StockStepsTheme.typography.body, color = colors.textBody)
            if (model.average != null) {
                StockDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    StockMetric(stringResource(Res.string.valuation_current), model.currentPe, Modifier.weight(1f))
                    StockMetric(model.averageLabel, model.average!!, Modifier.weight(1f))
                    StockMetric(stringResource(Res.string.valuation_difference), model.difference ?: "—", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TrendSection(model: ValuationModel, loaded: Boolean, onRange: (ValuationRange) -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(stringResource(Res.string.valuation_trend_title))
        StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm)) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
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
    }
}

@Composable
private fun UnderstandingCard(model: ValuationModel, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(
        modifier.clip(StockStepsTheme.shapes.cardLarge).background(colors.primaryContainer).padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Text(stringResource(Res.string.valuation_understanding_title), Modifier.semantics { heading() },
            style = StockStepsTheme.typography.cardTitle.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
        Text(model.understanding, style = StockStepsTheme.typography.body, color = colors.textBody)
        listOf(model.highReasons, model.lowReasons).forEach { explainer -> Explainer(explainer) }
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
