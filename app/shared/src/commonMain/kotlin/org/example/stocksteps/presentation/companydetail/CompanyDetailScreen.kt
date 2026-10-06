package org.example.stocksteps.presentation.companydetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.stocksteps.companydetail.*
import org.example.stocksteps.presentation.stocksearch.StockSearchState
import org.example.stocksteps.presentation.news.NewsCard
import org.example.stocksteps.theme.ThemeSpacing

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CompanyDetailScreen(
    state: StockSearchState,
    modifier: Modifier = Modifier,
    saved: Boolean,
    watchlistEnabled: Boolean,
    watchlistError: String?,
    onToggleWatchlist: () -> Unit,
    onRetryQuote: () -> Unit,
    onRetryProfile: () -> Unit,
    onAction: (CompanyDetailAction) -> Unit
) {
    val detail = state.detail
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(ThemeSpacing.large.dp),
        verticalArrangement = Arrangement.spacedBy(ThemeSpacing.large.dp)
    ) {
        item(key = "header") {
            CompanyHeader(detail, state.quoteLoading, state.quoteError, saved, watchlistEnabled, onToggleWatchlist, onRetryQuote)
            watchlistError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        stickyHeader(key = "tabs") {
            Surface {
                PrimaryScrollableTabRow(
                    selectedTabIndex = state.detailTab.ordinal,
                    edgePadding = 0.dp,
                    minTabWidth = 0.dp
                ) {
                    CompanyDetailTab.entries.forEach { tab ->
                        Tab(
                            selected = state.detailTab == tab,
                            onClick = { onAction(CompanyDetailAction.SelectTab(tab)) }
                        ) {
                            Text(
                                text = tab.title,
                                modifier = Modifier.padding(horizontal = ThemeSpacing.small.dp, vertical = ThemeSpacing.large.dp)
                            )
                        }
                    }
                }
            }
        }
        when (state.detailTab) {
            CompanyDetailTab.OVERVIEW -> {
                item(key = "about") {
                    DetailCard("What does this company do?") {
                        SectionFeedback(state.profileLoading, state.profileError, "Retry company information", onRetryProfile)
                        Text(detail.description?.takeIf { it.isNotBlank() } ?: "A company description is not available yet.")
                        detail.industry?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                    }
                }
                item(key = "chart") { StockPriceChart(state.chart, onAction) }
                item(key = "snapshot") { CompanySnapshotCard(detail.snapshot) { onAction(CompanyDetailAction.OpenMetricInfo("snapshot")) } }
                item(key = "key-metrics") {
                    DetailCard("Key metrics") {
                        detail.keyMetrics.filter { it.value != null }.forEach { metric ->
                            FinancialMetricRow(metric) { onAction(CompanyDetailAction.OpenMetricInfo(metric.id)) }
                        }
                        if (detail.keyMetrics.none { it.value != null }) Text("Key financial metrics are unavailable from the current integration.")
                        TextButton(onClick = { onAction(CompanyDetailAction.SelectTab(CompanyDetailTab.FINANCIALS)) }) { Text("View all metrics →") }
                    }
                }
                item(key = "risks") {
                    DetailCard("Risks to understand") {
                        if (detail.risks.isEmpty()) Text("No verified risk analysis is available. This does not mean the company has no risks.")
                        detail.risks.forEach { risk -> Text("${risk.title}: ${risk.explanation}\nSource: ${risk.source}") }
                    }
                }
            }
            CompanyDetailTab.FINANCIALS -> {
                item(key = "financial-period") {
                    Row(horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)) {
                        FinancialPeriod.entries.forEach { period ->
                            FilterChip(
                                selected = state.financialPeriod == period,
                                enabled = !state.fundamentalsLoading,
                                onClick = { onAction(CompanyDetailAction.SelectFinancialPeriod(period)) },
                                label = { Text(period.label) }
                            )
                        }
                    }
                }
                state.financials.refreshMessage?.let { message ->
                    item(key = "financial-refresh-feedback") {
                        Text(message, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onAction(CompanyDetailAction.RefreshFundamentals) }) { Text("Try again") }
                    }
                }
                state.financials.sections.forEach { section ->
                    item(key = section.title) {
                        CompanyFinancialsSection(
                            section = section,
                            onRetry = { onAction(CompanyDetailAction.RefreshFundamentals) },
                            onInfo = { onAction(CompanyDetailAction.OpenMetricInfo(it)) }
                        )
                    }
                }
            }
            CompanyDetailTab.VALUATION -> {
                item(key = "valuation-intro") {
                    DetailCard("Price in context") {
                        Text("Compare valuation with the company's history, industry, peers and growth. A single ratio cannot establish whether a stock is cheap or expensive.")
                        Text("Historical comparisons use eligible fiscal-year observations.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                detail.valuation.forEach { metric ->
                    item(key = metric.id) {
                        DetailCard(metric.label) {
                            FinancialMetricRow(metric) { onAction(CompanyDetailAction.OpenMetricInfo(metric.id)) }
                            Text("${metric.historicalLabel}: ${CompanyDetailPresenter.number(metric.historicalAverage)}")
                            metric.historicalRange?.let { Text(it) }
                            Text("Industry: ${CompanyDetailPresenter.number(metric.industryAverage)}")
                            CompanyDetailPresenter.historicalContext(metric)?.let { Text(it) }
                        }
                    }
                }
            }
            CompanyDetailTab.NEWS -> {
                item(key = "news-status") {
                    Text("Company news", style = MaterialTheme.typography.titleLarge)
                    SectionFeedback(state.newsLoading, state.newsError, "Retry news") { onAction(CompanyDetailAction.RefreshNews) }
                    if (!state.newsLoading && state.newsError == null && state.news.isEmpty()) Text("No recent company news is available.")
                    TextButton(onClick = { onAction(CompanyDetailAction.RefreshNews) }) { Text("Refresh news") }
                }
                state.news.forEach { article -> item(key = "news:${article.id ?: article.url}") { NewsCard(article) } }
            }
        }
        item(key = "education-note") { Text("Educational information, not a stock recommendation.", style = MaterialTheme.typography.bodySmall) }
    }
    state.metricInfo?.let { id ->
        MetricInfoBottomSheet(id = id, metrics = detail.keyMetrics + detail.valuation + detail.financials.flatMap { it.metrics }) {
            onAction(CompanyDetailAction.CloseMetricInfo)
        }
    }
}
