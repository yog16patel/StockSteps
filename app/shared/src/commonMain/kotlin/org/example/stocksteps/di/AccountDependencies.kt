package org.example.stocksteps.di

import app.cash.sqldelight.db.SqlDriver
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlinx.coroutines.*
import org.example.stocksteps.data.SqlWatchlistStore
import org.example.stocksteps.data.account.*
import org.example.stocksteps.data.watchlist.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.presentation.account.AccountViewModel
import org.example.stocksteps.presentation.watchlist.WatchListViewModel
import org.example.stocksteps.presentation.stocksearch.StockWatchlistViewModel

/** App-owned account graph. Feature ViewModels never close it. */
class AccountDependencies(
    authGateway: PlatformAuthGateway,
    cloudGateway: PlatformWatchlistGateway,
    private val driver: SqlDriver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph = koinApplication {
        modules(module {
            single<AuthRepository> { DefaultAuthRepository(authGateway, scope) }
            single<WatchlistLocalStore> { SqlWatchlistStore(driver) }
            single<WatchlistRepository> { OfflineWatchlistRepository(get(), get(), cloudGateway, scope) }
            factory { SignIn(get()) }
            factory { SignUp(get()) }
            factory { SignOut(get()) }
            factory { AddToWatchlist(get()) }
            factory { RemoveFromWatchlist(get()) }
            factory { AccountViewModel(get(), get(), get(), get()) }
            factory { WatchListViewModel(get(), get(), get()) }
            factory { StockWatchlistViewModel(get(), get(), get(), get()) }
        })
    }
    val auth: AuthRepository = graph.koin.get()
    val watchlist: WatchlistRepository = graph.koin.get()
    internal fun accountViewModel(): AccountViewModel = graph.koin.get()
    internal fun watchlistViewModel(): WatchListViewModel = graph.koin.get()
    internal fun stockWatchlistViewModel(): StockWatchlistViewModel = graph.koin.get()
    fun signIn(): SignIn = graph.koin.get()
    fun signUp(): SignUp = graph.koin.get()
    fun signOut(): SignOut = graph.koin.get()
    fun addToWatchlist(): AddToWatchlist = graph.koin.get()
    fun removeFromWatchlist(): RemoveFromWatchlist = graph.koin.get()

    fun close() {
        scope.cancel()
        graph.close()
        driver.close()
    }
}
