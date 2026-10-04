package org.example.stocksteps.model

data class WatchlistItem(val symbol: String, val addedAt: Long, val updatedAt: Long)

data class WatchlistSyncState(
    val syncing: Boolean = false,
    val pendingCount: Int = 0,
    val error: String? = null
)

// Tags emissions so an account switch cannot display the previous account's rows.
data class WatchlistSnapshot(val userId: String?, val items: List<WatchlistItem>)
