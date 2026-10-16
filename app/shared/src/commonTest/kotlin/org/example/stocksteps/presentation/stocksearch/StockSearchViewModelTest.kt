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
    @Test fun outOfOrderResponsesErrorsAndRetryKeepOnlyTheCurrentQuery() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val calls = mutableListOf<String>()
        var fail = true
        val repository = object : StockRepository {
            override suspend fun searchStocks(query: String): List<StockSearchResult> {
                calls += query
                // The older query answers last: it must never replace the newer query's results.
                delay(if (query == "app") 5000 else 100)
                if (query == "xyz") { if (fail) throw StockDataException("down") else return emptyList() }
                return listOf(StockSearchResult(query.uppercase(), "$query result"))
            }
            override suspend fun getProfile(symbol: String) = CompanyProfile(symbol)
            override suspend fun getQuote(symbol: String): StockQuote = error("Not expected")
        }
        val store = ViewModelStore()
        try {
            val model = StockSearchViewModel(SearchStocks(repository), GetStockQuote(repository), GetCompanyProfile(repository))
            store.put("search", model)
            runCurrent()
            model.changeQuery("app"); advanceTimeBy(301); runCurrent()
            model.changeQuery("msft"); advanceTimeBy(301); runCurrent()
            advanceTimeBy(6000); runCurrent()
            assertEquals(listOf("MSFT"), model.state.value.results.map { it.symbol })
            // An error is kept apart from "no results"; Retry runs the same query once more and replaces the error.
            model.changeQuery("xyz"); advanceTimeBy(301); runCurrent(); advanceTimeBy(200); runCurrent()
            assertTrue(model.state.value.searchError != null)
            assertFalse(model.state.value.searching)
            fail = false
            val before = calls.size
            model.retrySearch(); model.retrySearch(); runCurrent()
            assertTrue(model.state.value.searching)
            advanceTimeBy(200); runCurrent()
            assertEquals(null, model.state.value.searchError)
            assertTrue(model.state.value.results.isEmpty())
            assertEquals(1, calls.size - before) // a double tap on Retry sends one request: the first is cancelled before it starts
            // Clearing while a request is in flight leaves nothing behind.
            model.changeQuery("msft2"); advanceTimeBy(301); runCurrent()
            model.changeQuery(""); advanceTimeBy(500); runCurrent()
            assertTrue(model.state.value.results.isEmpty())
            assertFalse(model.state.value.searching)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    /**
     * Phase 4 audit race: "app" (A) is in flight when the query becomes "apple" (B); B answers first and A answers afterwards — even when A's
     * transport ignores cancellation, A never replaces B's results, and loading ends only with B. Two requests, no duplicates.
     */
    @Test fun olderSlowerQueryNeverReplacesNewerResultsEvenWithoutCancellation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val calls = mutableListOf<String>()
        val repository = object : StockRepository {
            override suspend fun searchStocks(query: String): List<StockSearchResult> {
                calls += query
                if (query == "app") withContext(NonCancellable) { delay(3000) } else delay(500)
                return listOf(StockSearchResult(if (query == "app") "APP" else "AAPL", "$query result"))
            }
            override suspend fun getProfile(symbol: String) = CompanyProfile(symbol)
            override suspend fun getQuote(symbol: String): StockQuote = error("Not expected")
        }
        val store = ViewModelStore()
        try {
            val model = StockSearchViewModel(SearchStocks(repository), GetStockQuote(repository), GetCompanyProfile(repository))
            store.put("search", model)
            runCurrent()
            model.changeQuery("app"); advanceTimeBy(301); runCurrent()
            assertEquals(listOf("app"), calls)
            model.changeQuery("apple"); runCurrent()
            assertTrue(model.state.value.searching)
            assertTrue(model.state.value.results.isEmpty())
            advanceTimeBy(301); runCurrent()
            advanceTimeBy(500); runCurrent()
            assertEquals(listOf("AAPL"), model.state.value.results.map { it.symbol })
            assertFalse(model.state.value.searching)
            advanceTimeBy(3000); runCurrent()
            assertEquals(listOf("AAPL"), model.state.value.results.map { it.symbol })
            assertFalse(model.state.value.searching)
            assertEquals(listOf("app", "apple"), calls)
            // Retyping the current query (or only its surrounding spaces) sends nothing new.
            model.changeQuery(" apple "); advanceTimeBy(1000); runCurrent()
            assertEquals(2, calls.size)
            assertEquals(listOf("AAPL"), model.state.value.results.map { it.symbol })
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
