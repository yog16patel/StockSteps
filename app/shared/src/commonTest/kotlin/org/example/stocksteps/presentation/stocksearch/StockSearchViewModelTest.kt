package org.example.stocksteps.presentation.stocksearch

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StockSearchViewModelTest {
    @Test fun fundamentalsLoadIndependentlyCancelOldSelectionAndSwitchPeriod() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var cancelled = false
        var failRefresh = false
        val periods = mutableListOf<String>()
        val repository = object : StockRepository {
            override suspend fun searchStocks(query: String) = emptyList<StockSearchResult>()
            override suspend fun getQuote(symbol: String) = StockQuote(symbol, symbol, 100.0, 1.0, 1.0, null, null)
            override suspend fun getProfile(symbol: String) = CompanyProfile(symbol)
            override suspend fun getFundamentals(symbol: String, period: String): CompanyFundamentals {
                if (symbol == "OLD") try { awaitCancellation() } finally { cancelled = true }
                periods += period
                if (failRefresh || period == "quarter") throw StockDataException("Fixture failure")
                return CompanyFundamentals(symbol, financials = CompanyFinancials(growth = mapOf("revenue" to FinancialFact(amount = 123))))
            }
        }
        val store = ViewModelStore()
        try {
            val model = StockSearchViewModel(SearchStocks(repository), GetStockQuote(repository), GetCompanyProfile(repository), getFundamentals = GetCompanyFundamentals(repository))
            store.put("search", model)
            model.selectStock(StockSearchResult("OLD", "Old")); runCurrent()
            model.selectStock(StockSearchResult("AAPL", "Apple")); runCurrent()
            assertTrue(cancelled)
            assertEquals("AAPL", model.state.value.fundamentals?.symbol)
            assertEquals(123.0, model.state.value.detail.financials.flatMap { it.metrics }.first { it.id == "revenue" }.value)
            failRefresh = true
            model.detailAction(org.example.stocksteps.presentation.companydetail.CompanyDetailAction.RefreshFundamentals)
            runCurrent()
            assertEquals(123L, model.state.value.fundamentals?.financials?.growth?.get("revenue")?.amount)
            assertNotNull(model.state.value.financials.refreshMessage)
            assertEquals(org.example.stocksteps.companydetail.SectionStatus.SUCCESS, model.state.value.financials.sections.first().status)
            failRefresh = false
            model.detailAction(org.example.stocksteps.presentation.companydetail.CompanyDetailAction.SelectFinancialPeriod(org.example.stocksteps.companydetail.FinancialPeriod.QUARTERLY))
            runCurrent()
            assertEquals(listOf("annual", "annual", "quarter"), periods)
            assertNotNull(model.state.value.fundamentalsError)
            assertEquals("AAPL", model.state.value.quote?.symbol)
            assertNotNull(model.state.value.profile)
            model.changeQuery("new"); runCurrent()
            assertNull(model.state.value.fundamentals)
            assertNull(model.state.value.fundamentalsError)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun newsFailureDoesNotDiscardCompanyOrQuoteAndSelectionCancelsOldNews() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = object : StockRepository {
            override suspend fun searchStocks(query: String) = emptyList<StockSearchResult>()
            override suspend fun getQuote(symbol: String) = StockQuote(symbol, symbol, 100.0, 1.0, 1.0, null, null)
            override suspend fun getProfile(symbol: String) = CompanyProfile(symbol, description = "Company information")
        }
        var cancelled = false
        val news = object : MarketRepository {
            override suspend fun getGainers() = emptyList<MarketMover>()
            override suspend fun getLosers() = emptyList<MarketMover>()
            override suspend fun getNews() = emptyList<NewsArticle>()
            override suspend fun getCompanyNews(symbol: String): List<NewsArticle> {
                if (symbol == "OLD") {
                    try { awaitCancellation() } finally { cancelled = true }
                }
                throw StockDataException("News unavailable")
            }
        }
        val store = ViewModelStore()
        try {
            val model = StockSearchViewModel(SearchStocks(repository), GetStockQuote(repository), GetCompanyProfile(repository), companyNews = GetCompanyNews(news))
            store.put("search", model)
            model.selectStock(StockSearchResult("OLD", "Old")); runCurrent()
            model.selectStock(StockSearchResult("AAPL", "Apple")); runCurrent()
            assertTrue(cancelled)
            assertEquals("AAPL", model.state.value.quote?.symbol)
            assertEquals("Company information", model.state.value.profile?.description)
            assertNotNull(model.state.value.newsError)
            assertFalse(model.state.value.newsLoading)
            assertTrue(model.state.value.news.isEmpty())
            model.detailAction(org.example.stocksteps.presentation.companydetail.CompanyDetailAction.OpenMetricInfo("pe")); runCurrent()
            assertEquals("pe", model.state.value.metricInfo)
            model.detailAction(org.example.stocksteps.presentation.companydetail.CompanyDetailAction.CloseMetricInfo); runCurrent()
            assertNull(model.state.value.metricInfo)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun typingDebouncesAndClearingCancelsActiveSearch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val calls = mutableListOf<String>()
        var cancelled = false
        val repository = object : StockRepository {
            override suspend fun searchStocks(query: String): List<StockSearchResult> {
                calls += query
                try { delay(1000) } catch (cause: CancellationException) { cancelled = true; throw cause }
                return listOf(StockSearchResult("AAPL", "Apple"))
            }
            override suspend fun getProfile(symbol: String) = CompanyProfile(symbol)
            override suspend fun getQuote(symbol: String): StockQuote = error("Not expected")
        }
        val store = ViewModelStore()
        try {
            val model = StockSearchViewModel(SearchStocks(repository), GetStockQuote(repository), GetCompanyProfile(repository))
            store.put("search", model)
            runCurrent()
            model.changeQuery("A")
            runCurrent()
            advanceTimeBy(200)
            model.changeQuery("Apple")
            runCurrent()
            advanceTimeBy(299)
            runCurrent()
            assertTrue(calls.isEmpty())
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("Apple"), calls)
            model.changeQuery("")
            runCurrent()
            assertTrue(cancelled)
            assertFalse(model.state.value.searching)
            advanceTimeBy(2000)
            runCurrent()
            assertTrue(model.state.value.results.isEmpty())
            assertEquals(1, calls.size)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun profileFailureAndRetryDoNotDiscardQuote() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var profileCalls = 0
        var quoteCalls = 0
        val repository = object : StockRepository {
            override suspend fun searchStocks(query: String) = emptyList<StockSearchResult>()
            override suspend fun getQuote(symbol: String): StockQuote {
                quoteCalls++
                return StockQuote(symbol, null, 150.0, null, null, null, null)
            }
            override suspend fun getProfile(symbol: String): CompanyProfile {
                profileCalls++
                if (profileCalls == 1) throw StockDataException("Profile unavailable")
                return CompanyProfile(symbol, companyName = "Apple Inc.")
            }
        }
        val store = ViewModelStore()
        try {
            val model = StockSearchViewModel(SearchStocks(repository), GetStockQuote(repository), GetCompanyProfile(repository))
            store.put("search", model)
            model.selectStock(StockSearchResult("AAPL", "Apple"))
            runCurrent()
            assertEquals(150.0, model.state.value.quote?.price)
            assertEquals("Profile unavailable", model.state.value.profileError)
            model.retryProfile()
            runCurrent()
            assertEquals("Apple Inc.", model.state.value.profile?.companyName)
            assertNull(model.state.value.profileError)
            assertEquals(1, quoteCalls)
            assertEquals(2, profileCalls)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

}
