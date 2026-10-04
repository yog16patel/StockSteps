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
}
