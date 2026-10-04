package org.example.stocksteps

import kotlinx.coroutines.*
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult

// SwiftUI owns this client. Provider credentials never cross into the app.
class IosStockStepsClient(baseUrl: String) {
    private val dependencies = org.example.stocksteps.di.StockStepsDependencies(baseUrl)
    private val searchStocks = dependencies.searchStocks()
    private val getStockQuote = dependencies.getStockQuote()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Deferred<List<StockSearchResult>>? = null
    private var quoteJob: Deferred<StockQuote>? = null

    @Throws(Exception::class)
    suspend fun searchStocks(query: String): List<StockSearchResult> {
        searchJob?.cancel()
        val job = scope.async { searchStocks(query) }
        searchJob = job
        return job.await()
    }

    @Throws(Exception::class)
    suspend fun getQuote(symbol: String): StockQuote {
        quoteJob?.cancel()
        val job = scope.async { getStockQuote(symbol) }
        quoteJob = job
        return job.await()
    }

    fun cancelSearch() { searchJob?.cancel() }
    fun cancelQuote() { quoteJob?.cancel() }
    fun close() {
        scope.cancel()
        dependencies.close()
    }
}
