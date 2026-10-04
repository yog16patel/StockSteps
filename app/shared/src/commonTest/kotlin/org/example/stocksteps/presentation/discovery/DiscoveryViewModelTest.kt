package org.example.stocksteps.presentation.discovery

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiscoveryViewModelTest {
    @Test fun sectionFailureDoesNotHideOtherFeedsAndRetryIsIndependent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var gainersCalls = 0
        var newsCalls = 0
        val repository = object : MarketRepository {
            override suspend fun getGainers(): List<MarketMover> {
                gainersCalls++
                if (gainersCalls == 1) throw StockDataException("Provider unavailable")
                return listOf(MarketMover("AAPL", "Apple", 150.0, 1.0, 0.5))
            }
            override suspend fun getLosers() = emptyList<MarketMover>()
            override suspend fun getNews(): List<NewsArticle> {
                newsCalls++
                return listOf(NewsArticle("Market headline", "https://example.com/article"))
            }
        }
        val store = ViewModelStore()
        try {
            val model = DiscoveryViewModel(GetMarketGainers(repository), GetMarketLosers(repository), GetMarketNews(repository))
            store.put("discovery", model)
            runCurrent()
            assertEquals("Provider unavailable", model.state.value.gainers.error)
            assertEquals(1, model.state.value.news.items.size)
            assertTrue(model.state.value.losers.items.isEmpty())
            model.retry(DiscoverySection.GAINERS)
            runCurrent()
            assertNull(model.state.value.gainers.error)
            assertEquals("AAPL", model.state.value.gainers.items.single().symbol)
            assertEquals(1, newsCalls)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
