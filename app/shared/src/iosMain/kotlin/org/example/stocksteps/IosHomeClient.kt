package org.example.stocksteps

import org.example.stocksteps.di.StockStepsDependencies

/** Home supports independent concurrent quote reads without cancelling search/detail requests. */
class IosHomeClient(baseUrl: String) {
    private val dependencies = StockStepsDependencies(baseUrl)
    private val quote = dependencies.getStockQuote()
    @Throws(Exception::class)
    suspend fun getQuote(symbol: String) = quote(symbol)
    @Throws(Exception::class)
    suspend fun getSnapshot() = dependencies.getMarketSnapshot()()
    fun close() = dependencies.close()
}
