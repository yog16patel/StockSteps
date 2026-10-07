package org.example.stocksteps

import kotlinx.coroutines.*
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.model.*

/** `baseUrl` is read per request, so a backend switch applies to the next call. */
class IosMarketClient(baseUrl: () -> String) {
    private val dependencies = StockStepsDependencies(baseUrl)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var gainersJob: Deferred<List<MarketMover>>? = null
    private var losersJob: Deferred<List<MarketMover>>? = null
    private var newsJob: Deferred<List<NewsArticle>>? = null

    @Throws(Exception::class)
    suspend fun getGainers(): List<MarketMover> {
        gainersJob?.cancel()
        val job = scope.async { dependencies.marketGainers()() }
        gainersJob = job
        return job.await()
    }

    @Throws(Exception::class)
    suspend fun getLosers(): List<MarketMover> {
        losersJob?.cancel()
        val job = scope.async { dependencies.marketLosers()() }
        losersJob = job
        return job.await()
    }

    @Throws(Exception::class)
    suspend fun getNews(): List<NewsArticle> {
        newsJob?.cancel()
        val job = scope.async { dependencies.marketNews()() }
        newsJob = job
        return job.await()
    }

    fun close() { scope.cancel(); dependencies.close() }
}
