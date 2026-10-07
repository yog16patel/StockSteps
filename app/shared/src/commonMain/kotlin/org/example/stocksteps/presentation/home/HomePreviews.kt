package org.example.stocksteps.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.settings.ThemeMode
import org.example.stocksteps.home.MoverCategory
import org.example.stocksteps.model.*

// Preview-only sample values; production Home always renders backend state.
private val previewSnapshot = MarketSnapshot(
    marketStatus = MarketStatus.OPEN,
    indices = listOf(
        MarketIndex("SPY", "S&P 500", price = 512.43, changePercent = 0.72),
        MarketIndex("QQQ", "Nasdaq-100", price = 440.12, changePercent = 1.14),
        MarketIndex("DIA", "Dow 30", price = 390.55, changePercent = -0.31)
    ),
    gainers = listOf(
        MarketMover("SAMPLEA", "Sample Technology Holdings With A Very Long Company Name", 188.23, changePercent = 5.24),
        MarketMover("SMPB", "Sample Motors", 201.40, changePercent = 3.81),
        MarketMover("SMPC", "Sample Semiconductors", 142.07, changePercent = 0.0)
    ),
    losers = listOf(MarketMover("SMPD", "Sample Retail", 54.10, changePercent = -3.12)),
    lastUpdated = "2026-10-06T14:00:00Z"
)

private val previewNews = listOf(
    NewsArticle(
        title = "Sample headline: technology shares lead a broad market gain as investors weigh new data",
        url = "https://example.com/sample-1",
        source = "Sample Wire",
        id = "1",
        explanation = SimplifiedNews("Tech stocks helped the market rise", "A sample summary explaining the story in plain words.", "Sample reason", NewsSentiment.NEUTRAL)
    ),
    NewsArticle(title = "Sample headline without an AI explanation", url = "https://example.com/sample-2", source = "Sample Daily", id = "2", description = "A sample provider description.")
)

private val previewState = HomeState(snapshot = previewSnapshot, news = previewNews)

@Composable
private fun HomePreview(state: HomeState, mode: ThemeMode) {
    StockStepsTheme(mode) { HomeScreen(state, signedIn = true, hinge = null, onAction = {}) }
}

@Preview(name = "Home — Light", heightDp = 1400)
@Composable
private fun HomeLightPreview() = HomePreview(previewState, ThemeMode.LIGHT)

@Preview(name = "Home — Dark", heightDp = 1400)
@Composable
private fun HomeDarkPreview() = HomePreview(previewState.copy(selectedMovers = MoverCategory.LOSERS), ThemeMode.DARK)

@Preview(name = "Home — Market Closed", heightDp = 900)
@Composable
private fun HomeClosedPreview() = HomePreview(previewState.copy(snapshot = previewSnapshot.copy(marketStatus = MarketStatus.CLOSED)), ThemeMode.LIGHT)

@Preview(name = "Home — Partial Failure", heightDp = 1400)
@Composable
private fun HomePartialPreview() = HomePreview(
    previewState.copy(
        snapshot = previewSnapshot.copy(
            indices = previewSnapshot.indices.take(1) + MarketIndex("QQQ", "Nasdaq-100", error = ApiError("PROVIDER_ERROR", "Unavailable")),
            errors = listOf(SnapshotSectionError("gainers", ApiError("PROVIDER_ERROR", "Unavailable"))),
            gainers = emptyList()
        ),
        news = emptyList(),
        newsError = "Unavailable"
    ),
    ThemeMode.LIGHT
)

@Preview(name = "Home — Loading", heightDp = 1200)
@Composable
private fun HomeLoadingPreview() = HomePreview(HomeState(snapshotLoading = true, newsLoading = true), ThemeMode.LIGHT)

@Preview(name = "Home — Narrow, large text", widthDp = 320, heightDp = 1400, fontScale = 1.5f)
@Composable
private fun HomeLargeTextPreview() = HomePreview(previewState, ThemeMode.LIGHT)

@Preview(name = "Home — View All destinations available", heightDp = 1200)
@Composable
private fun HomeViewAllPreview() = StockStepsTheme(ThemeMode.LIGHT) {
    HomeScreen(previewState, signedIn = true, hinge = null, onAction = {}, viewAllMovers = true, viewAllNews = true)
}
