package org.example.stocksteps

import kotlinx.coroutines.*
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult

// SwiftUI owns this client. Provider credentials never cross into the app.
/** `baseUrl` is read per request, so a backend switch applies to the next call. */
class IosStockStepsClient internal constructor(private val dependencies: org.example.stocksteps.di.StockStepsDependencies) {
    constructor(baseUrl: () -> String) : this(org.example.stocksteps.di.StockStepsDependencies(baseUrl))
    // Use-case properties must not share a name with the suspend functions below: inside `scope.async { searchStocks(query) }` Kotlin
    // resolves the call to the member function, not the property's invoke, which recursed forever and failed every iOS search (Phase 5C).
    private val searchStocksUseCase = dependencies.searchStocks()
    private val getCompanyProfile = dependencies.getCompanyProfile()
    private val getCompanyFundamentals = dependencies.getCompanyFundamentals()
    private var fundamentalsJob: Deferred<org.example.stocksteps.model.CompanyFundamentals>? = null
    private val companyNewsUseCase = dependencies.companyNews()
    private var newsJob: Deferred<List<org.example.stocksteps.model.NewsArticle>>? = null
    private val getStockQuote = dependencies.getStockQuote()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Deferred<List<StockSearchResult>>? = null
    private var profileJob: Deferred<org.example.stocksteps.model.CompanyProfile>? = null
    private var quoteJob: Deferred<StockQuote>? = null

    @Throws(Exception::class)
    suspend fun searchStocks(query: String): List<StockSearchResult> {
        searchJob?.cancel()
        val job = scope.async { searchStocksUseCase(query) }
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

    @Throws(Exception::class)
    suspend fun getProfile(symbol: String): org.example.stocksteps.model.CompanyProfile {
        profileJob?.cancel()
        val job = scope.async { getCompanyProfile(symbol) }
        profileJob = job
        return job.await()
    }

    @Throws(Exception::class)
    suspend fun getCompanyNews(symbol: String): List<org.example.stocksteps.model.NewsArticle> {
        newsJob?.cancel()
        val job = scope.async { companyNewsUseCase(symbol) }
        newsJob = job
        return job.await()
    }
    @Throws(Exception::class)
    suspend fun getFundamentals(symbol: String, period: String): org.example.stocksteps.model.CompanyFundamentals {
        fundamentalsJob?.cancel()
        val job = scope.async { getCompanyFundamentals(symbol, period) }
        fundamentalsJob = job
        return job.await()
    }
    fun cancelFundamentals() { fundamentalsJob?.cancel() }
    fun cancelNews() { newsJob?.cancel() }
    fun cancelProfile() { profileJob?.cancel() }
    fun cancelSearch() { searchJob?.cancel() }
    fun cancelQuote() { quoteJob?.cancel() }
    fun close() {
        scope.cancel()
        dependencies.close()
    }
}
