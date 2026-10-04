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
            override suspend fun getQuote(symbol: String): StockQuote = error("Not expected")
        }
        val store = ViewModelStore()
        try {
            val model = StockSearchViewModel(SearchStocks(repository), GetStockQuote(repository))
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
}
