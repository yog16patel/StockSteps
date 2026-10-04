package org.example.stocksteps.service

import java.time.Clock
import kotlinx.coroutines.*
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.MarketDataProvider

class MarketSnapshotService(
    private val provider: MarketDataProvider,
    private val cache: MarketSnapshotCache = InMemoryMarketSnapshotCache(),
    private val clock: Clock = Clock.systemUTC()
) {
    private data class Section<T>(val value: T, val error: SnapshotSectionError? = null)
    private val moverLimit = 5
    suspend fun getSnapshot(): MarketSnapshot = cache.getOrLoad {
        coroutineScope {
            val indices = async { section("indices", emptyList<MarketIndex>()) { provider.getMarketIndices() } }
            val gainers = async { section("gainers", emptyList<MarketMover>()) { provider.getGainers().take(moverLimit) } }
            val losers = async { section("losers", emptyList<MarketMover>()) { provider.getLosers().take(moverLimit) } }
            val active = async { section("mostActive", emptyList<MarketMover>()) { provider.getMostActive().take(moverLimit) } }
            val status = async { section("marketStatus", MarketStatus.UNKNOWN) { provider.getMarketStatus() } }
            MarketSnapshot(
                indices = indices.await().value,
                gainers = gainers.await().value,
                losers = losers.await().value,
                mostActive = active.await().value,
                marketStatus = status.await().value,
                lastUpdated = clock.instant().toString(),
                errors = listOfNotNull(indices.await().error, gainers.await().error, losers.await().error, active.await().error, status.await().error)
            )
        }
    }
    private suspend fun <T> section(name: String, empty: T, fetch: suspend () -> T): Section<T> = try {
        Section(fetch())
    } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        Section(empty, SnapshotSectionError(name, snapshotError(cause)))
    }
}
