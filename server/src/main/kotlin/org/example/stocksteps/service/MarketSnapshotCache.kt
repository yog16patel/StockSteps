package org.example.stocksteps.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.stocksteps.model.MarketSnapshot

interface MarketSnapshotCache {
    suspend fun getOrLoad(loader: suspend () -> MarketSnapshot): MarketSnapshot
}

/** One backend instance, coalesced misses, no stale data returned after expiry. */
class InMemoryMarketSnapshotCache(
    private val ttlMillis: Long = 45_000,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) : MarketSnapshotCache {
    init { require(ttlMillis > 0) }
    private val mutex = Mutex()
    private var value: MarketSnapshot? = null
    private var loadedAt = 0L
    override suspend fun getOrLoad(loader: suspend () -> MarketSnapshot): MarketSnapshot = mutex.withLock {
        val cached = value
        if (cached != null && nowMillis() - loadedAt < ttlMillis) return@withLock cached
        val fresh = loader()
        value = fresh
        loadedAt = nowMillis()
        fresh
    }
}
