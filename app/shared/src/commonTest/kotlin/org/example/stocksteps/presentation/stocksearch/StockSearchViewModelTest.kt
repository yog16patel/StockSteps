package org.example.stocksteps.presentation.stocksearch

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StockSearchViewModelTest {
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
