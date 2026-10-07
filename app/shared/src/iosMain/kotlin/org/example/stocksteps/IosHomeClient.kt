package org.example.stocksteps

import org.example.stocksteps.di.StockStepsDependencies

/** Home supports independent concurrent quote reads without cancelling search/detail requests. */
/** `baseUrl` is read per request, so a backend switch applies to the next call. */
class IosHomeClient(baseUrl: () -> String) {
    private val dependencies = StockStepsDependencies(baseUrl)
    private val quote = dependencies.getStockQuote()
    @Throws(Exception::class)
    suspend fun getQuote(symbol: String) = quote(symbol)
    @Throws(Exception::class)
    suspend fun getSnapshot() = dependencies.getMarketSnapshot()()
    @Throws(Exception::class)
    suspend fun getProfile(symbol: String) = dependencies.getCompanyProfile()(symbol)
    @Throws(Exception::class)
    suspend fun getSparkline(symbol: String) = dependencies.getSparkline()(symbol)
    @Throws(Exception::class)
    suspend fun getNews() = dependencies.marketNews()()
    fun close() = dependencies.close()
}
