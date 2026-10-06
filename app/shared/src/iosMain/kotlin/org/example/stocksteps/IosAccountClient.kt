package org.example.stocksteps

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.account.*
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.local.WatchlistDatabase
import org.example.stocksteps.model.*

// App-owned native bridge. Native Firebase adapters implement only callback contracts.
data class AccountSnapshot(
    val session: AuthSession,
    val items: List<WatchlistItem>,
    val sync: WatchlistSyncState
)

class IosAccountClient(auth: PlatformAuthGateway, cloud: PlatformWatchlistGateway) {
    private val dependencies = AccountDependencies(
        auth, cloud,
        NativeSqliteDriver(WatchlistDatabase.Schema, "stocksteps-watchlist.db")
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun observe(onChange: (AccountSnapshot) -> Unit): AccountSubscription {
        val job = scope.launch {
            combine(dependencies.auth.session, dependencies.watchlist.snapshot, dependencies.watchlist.syncState) { session, snapshot, sync ->
                AccountSnapshot(session, if (session.user?.id == snapshot.userId) snapshot.items else emptyList(), sync)
            }.collect(onChange)
        }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }

    @Throws(Exception::class)
    suspend fun signIn(email: String, password: String) = dependencies.signIn()(email, password)
    @Throws(Exception::class)
    suspend fun signUp(email: String, password: String) = dependencies.signUp()(email, password)
    @Throws(Exception::class)
    suspend fun signInWithGoogle() = dependencies.auth.signInWithGoogle()
    @Throws(Exception::class)
    suspend fun signOut() = dependencies.signOut()()
    @Throws(Exception::class)
    suspend fun addListing(stock: org.example.stocksteps.model.StockSearchResult) = dependencies.addToWatchlist()(stock)
    suspend fun add(symbol: String) = dependencies.addToWatchlist()(symbol)
    @Throws(Exception::class)
    suspend fun remove(symbol: String) = dependencies.removeFromWatchlist()(symbol)
    fun retrySync() = dependencies.watchlist.retrySync()
    fun close() { scope.cancel(); dependencies.close() }
}
