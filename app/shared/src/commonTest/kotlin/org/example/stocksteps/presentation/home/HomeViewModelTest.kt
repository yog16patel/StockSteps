package org.example.stocksteps.presentation.home

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test fun accountSwitchClearsRowsAndCancelsLateQuotes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = MutableStateFlow(AuthSession(user = User("one", null), initializing = false))
        val snapshots = MutableStateFlow(WatchlistSnapshot("one", listOf(WatchlistItem("AAPL", 1, 1))))
        val auth = object : AuthRepository {
            override val session = session
            override val currentUser = session.map { it.user }
            override suspend fun signIn(email: String, password: String) = Unit
            override suspend fun signUp(email: String, password: String) = Unit
            override suspend fun signOut() = Unit
        }
        val watchlist = object : WatchlistRepository {
            override val snapshot = snapshots
            override val items = snapshots.map { it.items }
            override val syncState = MutableStateFlow(WatchlistSyncState())
            override suspend fun add(symbol: String) = Unit
            override suspend fun remove(symbol: String) = Unit
            override fun retrySync() = Unit
        }
        val pending = CompletableDeferred<StockQuote>()
        val stocks = object : StockRepository {
            override suspend fun searchStocks(query: String) = emptyList<StockSearchResult>()
            override suspend fun getProfile(symbol: String): CompanyProfile = error("Unused")
            override suspend fun getQuote(symbol: String): StockQuote {
                if (symbol == "AAPL") return pending.await()
                throw StockDataException("Unavailable")
            }
        }
        val store = ViewModelStore()
        try {
            val model = HomeViewModel(GetStockQuote(stocks), watchlist, auth, GetMarketSnapshot(object : MarketSnapshotRepository {
                override suspend fun getSnapshot(): MarketSnapshot = throw StockDataException("Unavailable")
            })) {}
            store.put("home", model)
            runCurrent()
            assertTrue(model.state.value.stocks.single().loading)
            assertTrue(model.state.value.indices.all { it.error })
            session.value = AuthSession(user = User("two", null), initializing = false)
            runCurrent()
            assertTrue(model.state.value.stocks.isEmpty())
            pending.complete(StockQuote("AAPL", "Apple", 100.0, 1.0, 1.0, null, null))
            runCurrent()
            assertTrue(model.state.value.stocks.isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun moverChipsSwitchLocallyAndNewsFailureStaysSectionLevel() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = MutableStateFlow(AuthSession(user = null, initializing = false))
        val auth = object : AuthRepository {
            override val session = session
            override val currentUser = session.map { it.user }
            override suspend fun signIn(email: String, password: String) = Unit
            override suspend fun signUp(email: String, password: String) = Unit
            override suspend fun signOut() = Unit
        }
        val snapshots = MutableStateFlow(WatchlistSnapshot(null, emptyList()))
        val watchlist = object : WatchlistRepository {
            override val snapshot = snapshots
            override val items = snapshots.map { it.items }
            override val syncState = MutableStateFlow(WatchlistSyncState())
            override suspend fun add(symbol: String) = Unit
            override suspend fun remove(symbol: String) = Unit
            override fun retrySync() = Unit
        }
        val stocks = object : StockRepository {
            override suspend fun searchStocks(query: String) = emptyList<StockSearchResult>()
            override suspend fun getProfile(symbol: String): CompanyProfile = error("Unused")
            override suspend fun getQuote(symbol: String): StockQuote = error("Unused")
        }
        var snapshotCalls = 0
        val sparklineRequests = mutableListOf<String>()
        val snapshot = MarketSnapshot(
            marketStatus = MarketStatus.CLOSED,
            indices = listOf(MarketIndex("SPY", "S&P 500", price = 500.0, changePercent = 0.5)),
            gainers = listOf(MarketMover("UP", changePercent = 4.0)),
            losers = listOf(MarketMover("DOWN", changePercent = -4.0)),
            lastUpdated = "2026-10-06T14:00:00Z"
        )
        val news = object : MarketRepository {
            override suspend fun getGainers() = emptyList<MarketMover>()
            override suspend fun getLosers() = emptyList<MarketMover>()
            override suspend fun getNews(): List<NewsArticle> = throw StockDataException("HTTP 403 provider detail")
        }
        val store = ViewModelStore()
        try {
            val model = HomeViewModel(GetStockQuote(stocks), watchlist, auth, GetMarketSnapshot(object : MarketSnapshotRepository {
                override suspend fun getSnapshot(): MarketSnapshot { snapshotCalls++; return snapshot }
            }), GetMarketNews(news), GetSparkline(object : SparklineRepository {
                override suspend fun getSparkline(symbol: String): Sparkline {
                    sparklineRequests += symbol
                    if (symbol == "DOWN") throw StockDataException("Unavailable")
                    return Sparkline(symbol, listOf(1.0, 2.0))
                }
            })) {}
            store.put("home", model)
            runCurrent()
            assertEquals(org.example.stocksteps.companydetail.SectionStatus.SUCCESS, model.state.value.market.status)
            assertEquals(MarketStatus.CLOSED, model.state.value.market.marketStatus)
            assertEquals("UP", model.state.value.movers.rows.single().symbol)
            model.selectMovers(org.example.stocksteps.home.MoverCategory.LOSERS)
            assertEquals("DOWN", model.state.value.movers.rows.single().symbol)
            assertEquals(1, snapshotCalls)
            runCurrent()
            assertNull(model.state.value.movers.rows.single().sparkline)
            model.selectMovers(org.example.stocksteps.home.MoverCategory.GAINERS)
            runCurrent()
            assertEquals(listOf(1.0, 2.0), model.state.value.movers.rows.single().sparkline)
            // Index cards request their trend line once, alongside the visible movers.
            assertEquals(listOf("SPY", "UP", "DOWN"), sparklineRequests)
            assertEquals(listOf(1.0, 2.0), model.state.value.market.indices.first().sparkline)
            assertEquals(org.example.stocksteps.companydetail.SectionStatus.ERROR, model.state.value.newsStatus)
            assertEquals(org.example.stocksteps.companydetail.SectionStatus.SUCCESS, model.state.value.market.status)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
