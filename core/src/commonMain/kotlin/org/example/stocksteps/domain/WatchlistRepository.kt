package org.example.stocksteps.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.example.stocksteps.model.WatchlistSnapshot
import org.example.stocksteps.model.WatchlistItem
import org.example.stocksteps.model.WatchlistSyncState

interface WatchlistRepository {
    val snapshot: Flow<WatchlistSnapshot>
    val items: Flow<List<WatchlistItem>>
    val syncState: StateFlow<WatchlistSyncState>
    suspend fun add(symbol: String)
    suspend fun add(stock: org.example.stocksteps.model.StockSearchResult) = add(stock.symbol)
    suspend fun remove(symbol: String)
    fun retrySync()
}

fun normalizedWatchlistSymbol(symbol: String): String {
    val normalized = symbol.trim().uppercase()
    require(normalized.matches(Regex("[A-Z0-9][A-Z0-9.^-]{0,31}"))) { "Enter a valid stock symbol." }
    return normalized
}
