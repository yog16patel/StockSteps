package org.example.stocksteps.data.watchlist

import kotlinx.coroutines.flow.Flow
import org.example.stocksteps.model.WatchlistItem

// UID namespaces are disjoint from the guest namespace, including on logout.
fun watchlistOwner(uid: String?) = uid?.let { "user:$it" } ?: GUEST_OWNER
const val GUEST_OWNER = "guest"

data class PendingWatchlistOperation(
    val revision: Long,
    val symbol: String,
    val item: WatchlistItem? // null is a durable removal, never a visible row
)

interface WatchlistLocalStore {
    fun observe(owner: String): Flow<List<WatchlistItem>>
    fun observePending(owner: String): Flow<List<PendingWatchlistOperation>>
    suspend fun add(owner: String, symbol: String, now: Long, queue: Boolean)
    suspend fun addListing(owner: String, stock: org.example.stocksteps.model.StockSearchResult, now: Long, queue: Boolean) = add(owner, stock.symbol, now, queue)
    suspend fun remove(owner: String, symbol: String, queue: Boolean)
    suspend fun mergeGuest(owner: String)
    suspend fun applySnapshot(owner: String, items: List<WatchlistItem>)
    suspend fun acknowledge(owner: String, operation: PendingWatchlistOperation)
}
