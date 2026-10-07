package org.example.stocksteps.presentation.companydetails

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyDetailsViewModelTest {
    @Test fun sectionsFailIndependentlyAndChartsAreCachedPerRange() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val chartRequests = mutableListOf<ChartRange>()
        val details = object : CompanyDetailsRepository {
            override suspend fun getDetails(symbol: String) = CompanyDetails(symbol, quote = StockQuote(symbol, "Sample", 10.0, 1.0, 11.1, null, null))
            override suspend fun getChart(symbol: String, range: ChartRange): PriceChart {
                chartRequests += range
                return PriceChart(symbol, range, listOf(PricePoint("2026-10-01", 9.0), PricePoint("2026-10-02", 10.0)), "1day")
            }
            override suspend fun getWhyMoving(symbol: String): WhyMoving = throw StockDataException("not available")
        }
        val news = object : MarketRepository {
            override suspend fun getGainers() = emptyList<MarketMover>()
            override suspend fun getLosers() = emptyList<MarketMover>()
            override suspend fun getNews() = emptyList<NewsArticle>()
            override suspend fun getCompanyNews(symbol: String): List<NewsArticle> = throw StockDataException("HTTP 429")
        }
        val store = ViewModelStore()
        try {
            val model = CompanyDetailsViewModel("SMPL", GetCompanyDetails(details), GetPriceChart(details), GetWhyMoving(details), GetCompanyNews(news)) {}
            store.put("details", model)
            advanceUntilIdle()
            val state = model.state.value
            assertIs<Section.Content<*>>(state.overview)
            assertIs<Section.Content<*>>(state.chart)
            assertEquals(Section.Unavailable, state.whyMoving)
            assertEquals(Section.Unavailable, state.news)
            model.selectRange(ChartRange.ONE_YEAR); advanceUntilIdle()
            model.selectRange(ChartRange.ONE_MONTH); advanceUntilIdle()
            assertEquals(listOf(ChartRange.ONE_MONTH, ChartRange.ONE_YEAR), chartRequests, "returning to a range reuses its chart")
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
