package org.example.stocksteps.home

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.companydetail.SectionStatus
import org.example.stocksteps.model.*
import org.example.stocksteps.news.NewsPresentation
import kotlin.test.*

class HomePresentationTest {
    private fun snapshot(
        indices: List<MarketIndex> = listOf(
            MarketIndex("SPY", "S&P 500", price = 512.4, changePercent = 0.72),
            MarketIndex("QQQ", "Nasdaq-100", price = 440.1, changePercent = -0.45),
            MarketIndex("DIA", "Dow 30", price = 390.0, changePercent = 0.0)
        ),
        gainers: List<MarketMover> = listOf(MarketMover("NVDA", "NVIDIA", 188.234, changePercent = 5.24)),
        losers: List<MarketMover> = listOf(MarketMover("TSLA", "Tesla", 201.0, changePercent = -3.12)),
        status: MarketStatus = MarketStatus.OPEN,
        errors: List<SnapshotSectionError> = emptyList()
    ) = MarketSnapshot(marketStatus = status, indices = indices, gainers = gainers, losers = losers, lastUpdated = "2026-10-06T14:00:00Z", errors = errors)

    @Test fun percentAndDirectionDoNotRelyOnColor() {
        assertEquals("+1.25%", HomePresentation.percent(1.25))
        assertEquals("-1.25%", HomePresentation.percent(-1.249))
        assertEquals("0.00%", HomePresentation.percent(0.001))
        assertEquals("—", HomePresentation.percent(null))
        assertEquals(PriceDirection.UNCHANGED, HomePresentation.direction(-0.004))
        assertEquals(PriceDirection.UNAVAILABLE, HomePresentation.direction(Double.NaN))
    }

    @Test fun pricesClaimCurrencyOnlyWhenKnown() {
        assertEquals("$1,234.57", HomePresentation.price(1234.567, "USD"))
        assertEquals("188.23", HomePresentation.price(188.234, null))
        assertEquals("54.10 CAD", HomePresentation.price(54.1, "cad"))
    }

    @Test fun marketMapsAllIndexDirections() {
        val market = HomePresentation.market(snapshot(), loading = false, failed = false, maxIndices = 3)
        assertEquals(SectionStatus.SUCCESS, market.status)
        assertEquals(listOf(PriceDirection.UP, PriceDirection.DOWN, PriceDirection.UNCHANGED), market.indices.map { it.direction })
        assertEquals("$512.40", market.indices.first().price)
        assertFalse(market.partiallyUnavailable)
    }

    @Test fun homeShowsTwoMarketCardsWithoutJudgingHiddenOnes() {
        val market = HomePresentation.market(snapshot(indices = snapshot().indices.dropLast(1) + MarketIndex("DIA", "Dow 30", error = ApiError("X", "Y"))), loading = false, failed = false, maxIndices = HomePresentation.HOME_MARKET_CARDS)
        assertEquals(listOf("S&P 500", "Nasdaq-100"), market.indices.map { it.name })
        assertFalse(market.partiallyUnavailable)
    }

    @Test fun closedMarketIsContentNotError() {
        val market = HomePresentation.market(snapshot(status = MarketStatus.CLOSED), loading = false, failed = false, maxIndices = 3)
        assertEquals(SectionStatus.SUCCESS, market.status)
        assertEquals(MarketStatus.CLOSED, market.marketStatus)
    }

    @Test fun partialIndexFailureKeepsAvailableValues() {
        val market = HomePresentation.market(snapshot(indices = listOf(
            MarketIndex("SPY", "S&P 500", price = 512.4, changePercent = 0.72),
            MarketIndex("QQQ", "Nasdaq-100", error = ApiError("PROVIDER_ERROR", "HTTP 402"))
        )), loading = false, failed = false, maxIndices = 3)
        assertEquals(SectionStatus.SUCCESS, market.status)
        assertTrue(market.partiallyUnavailable)
        assertNull(market.indices[1].price)
    }

    @Test fun marketLoadingAndFailureUsePlaceholders() {
        assertEquals(SectionStatus.LOADING, HomePresentation.market(null, loading = false, failed = false, maxIndices = 3).status)
        val failed = HomePresentation.market(null, loading = false, failed = true, maxIndices = 3)
        assertEquals(SectionStatus.ERROR, failed.status)
        assertEquals(listOf("S&P 500", "Nasdaq-100", "Dow 30"), failed.indices.map { it.name })
    }

    @Test fun moversFollowSelectedCategory() {
        val data = snapshot()
        val gainers = HomePresentation.movers(data, false, false, MoverCategory.GAINERS, emptyMap(), emptyMap())
        val losers = HomePresentation.movers(data, false, false, MoverCategory.LOSERS, emptyMap(), emptyMap())
        assertEquals("NVDA", gainers.rows.single().symbol)
        assertEquals("188.23", gainers.rows.single().price)
        assertEquals(PriceDirection.DOWN, losers.rows.single().direction)
    }

    @Test fun emptyMoversAreNotErrorsUnlessTheSectionFailed() {
        val data = snapshot(errors = listOf(SnapshotSectionError("losers", ApiError("PROVIDER_ERROR", "Unavailable"))), losers = emptyList())
        assertEquals(SectionStatus.EMPTY, HomePresentation.movers(data, false, false, MoverCategory.MOST_ACTIVE, emptyMap(), emptyMap()).status)
        assertEquals(SectionStatus.ERROR, HomePresentation.movers(data, false, false, MoverCategory.LOSERS, emptyMap(), emptyMap()).status)
        assertEquals(SectionStatus.ERROR, HomePresentation.movers(null, false, true, MoverCategory.GAINERS, emptyMap(), emptyMap()).status)
    }

    @Test fun moversCarryLogosAndOnlyRealSparklines() {
        val data = snapshot(gainers = listOf(MarketMover("UP", logoUrl = "https://example.com/up.png", changePercent = 1.0), MarketMover("FLAT", changePercent = 0.0)))
        val rows = HomePresentation.movers(data, false, false, MoverCategory.GAINERS, mapOf("UP" to listOf(1.0, 2.0), "FLAT" to listOf(1.0)), mapOf("FLAT" to "https://example.com/flat.png")).rows
        assertEquals("https://example.com/up.png", rows[0].logoUrl)
        assertEquals(listOf(1.0, 2.0), rows[0].sparkline)
        assertNull(rows[1].sparkline)
        assertEquals("https://example.com/flat.png", rows[1].logoUrl)
        assertEquals(listOf("FLAT"), HomePresentation.moversMissingLogos(data, MoverCategory.GAINERS))
        assertEquals(listOf("UP", "FLAT"), HomePresentation.visibleMoverSymbols(data, MoverCategory.GAINERS))
    }

    @Test fun moversAreLimitedForHome() {
        val many = (1..8).map { MarketMover("T$it", changePercent = it.toDouble()) }
        assertEquals(HomePresentation.MAX_MOVERS, HomePresentation.movers(snapshot(gainers = many), false, false, MoverCategory.GAINERS, emptyMap(), emptyMap()).rows.size)
    }

    @Test fun newsStatusSeparatesEmptyFromFailure() {
        assertEquals(SectionStatus.EMPTY, HomePresentation.newsStatus(0, loading = false, failed = false))
        assertEquals(SectionStatus.ERROR, HomePresentation.newsStatus(0, loading = false, failed = true))
        assertEquals(SectionStatus.SUCCESS, HomePresentation.newsStatus(2, loading = true, failed = true))
    }

    @Test fun newsModelPrefersSimplifiedTextAndFormatsTime() {
        val now = 1_760_000_000_000L
        val article = NewsArticle(
            title = "Original", url = "https://example.com/a", source = "Reuters",
            publishedAt = "2025-10-09T05:53:20Z", description = "Provider description",
            explanation = SimplifiedNews("Simple", "Summary", "Matters", NewsSentiment.NEUTRAL)
        )
        val model = NewsPresentation.model(article, now)
        assertEquals("Simple", model.headline)
        assertEquals("Summary", model.summary)
        assertTrue(model.aiSimplified)
        assertEquals("3h ago", model.publishedLabel)
        val plain = NewsPresentation.model(article.copy(explanation = null, url = "javascript:alert(1)"), now)
        assertEquals("Provider description", plain.summary)
        assertNull(plain.url)
        assertNull(NewsPresentation.relativeTime("not a date", now))
        val echoed = NewsPresentation.model(NewsArticle(title = "Markets rise - Reuters", url = "https://example.com/b", description = "Markets rise  Reuters"), now)
        assertNull(echoed.summary)
    }
}
