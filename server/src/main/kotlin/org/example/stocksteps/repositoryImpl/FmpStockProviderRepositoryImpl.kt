package org.example.stocksteps.repositoryImpl

import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderException.Failure
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.repository.models.FmpCompanyProfile
import org.example.stocksteps.repository.models.toCompanyProfile
import org.example.stocksteps.model.MarketMover
import org.example.stocksteps.repository.models.FmpMarketMover
import org.example.stocksteps.repository.models.toMarketMover
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.repository.models.FmpQuote
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.repository.models.FmpSearchResult
import org.example.stocksteps.repository.models.toStockSearchResult
import org.example.stocksteps.repository.models.toStockQuote

class FmpStockProviderRepositoryImpl(
    private val client: HttpClient,
    private val apiKey: String,
    /**
     * Capacity of the shared FMP dataset cache (Phase 3B-1: sized for the 150-company screener plus other screens;
     * env `FMP_DATASET_CACHE_ENTRIES`). See `docs/FINANCIAL_API_PHASE3_IMPLEMENTATION.md` §8 for the estimate.
     */
    datasetCacheEntries: Int = DEFAULT_DATASET_CACHE_ENTRIES,
    /** Monotonic milliseconds for cache expiry (tests use a fake clock). */
    cacheNow: () -> Long = { System.nanoTime() / 1_000_000 },
    /** Phase 3C: quote and TTM-ratio lifetimes by the listing's market session (production passes a real-clock policy). */
    private val freshness: org.example.stocksteps.service.MarketFreshnessPolicy = org.example.stocksteps.service.MarketFreshnessPolicy.FIXED,
    /** Phase 3D: earnings evidence that shortens statement lifetimes after a report (null: fixed lifetimes). */
    statementSignals: org.example.stocksteps.service.EarningsStatementSignals? = null,
    /** Wall clock for provider retrieval times shown to users (tests use a fake one). */
    wallClock: () -> java.time.Instant = java.time.Instant::now,
    private val today: () -> java.time.LocalDate = { java.time.LocalDate.now(java.time.ZoneOffset.UTC) }
) : StockProviderRepository, org.example.stocksteps.service.QuarterlyEarningsSource {
    companion object {
        const val DEFAULT_DATASET_CACHE_ENTRIES = 4_096
    }
    private val financialCache = org.example.stocksteps.service.CompanyFinancialCache(capacity = datasetCacheEntries, now = cacheNow, name = "fmp")
    /** Entries currently held by the dataset cache (diagnostics and tests). */
    val datasetCacheSize: Int get() = financialCache.size
    private val fundamentalsLoader = FmpFundamentalsLoader(client, apiKey, financialCache, today, freshness = freshness, signals = statementSignals, clock = wallClock)

    override suspend fun getFundamentals(symbol: String, period: String) =
        fundamentalsLoader.load(symbol, period) {
            coroutineScope {
                val quote = async { getQuote(symbol) }
                val profile = async { getProfile(symbol) }
                quote.await() to profile.await()
            }
        }

    /**
     * Phase 3B-1 screener set: 10 datasets (no historical annual ratios) + the quote. The listing currency from the
     * screener universe (TSX → CAD, US exchanges → USD) replaces the profile request; it is only used to check that
     * forward P/E compares a price and an estimate in the same currency (a mismatch leaves it unavailable, never wrong).
     * The quote is kept: the universe price is up to 24 h old and has no timestamp.
     */
    override suspend fun screenerFundamentals(symbol: String, listingCurrency: String?) =
        fundamentalsLoader.load(symbol, "annual", historicalRatios = false) {
            coroutineScope {
                val quote = async { getQuote(symbol) }
                val profile = listingCurrency?.let { CompanyProfile(symbol, currency = it) } ?: getProfile(symbol)
                quote.await() to profile
            }
        }

    override suspend fun statementHistory(symbol: String, period: String, statements: Set<org.example.stocksteps.repository.Statement>) =
        fundamentalsLoader.statementHistory(symbol, period, statements)

    override suspend fun quarterlyEarnings(symbol: String) = fundamentalsLoader.quarterlyEarnings(symbol)

    override suspend fun searchStocks(query: String): List<StockSearchResult> = coroutineScope {
        val bySymbol = async { search("search-symbol", query) }
        val byName = async { search("search-name", query) }
        (bySymbol.await() + byName.await()).map { it.toStockSearchResult() }
    }

    private suspend fun search(endpoint: String, query: String): List<FmpSearchResult> {
        val results = client.apiCall<List<FmpSearchResult>>(
            url = "https://financialmodelingprep.com/stable/$endpoint",
            apiKey = apiKey
        ) {
            parameter("query", query)
        }
        if (results.any { it.symbol.isBlank() || it.name.isBlank() }) {
            throw StockProviderException(Failure.INVALID_RESPONSE)
        }
        return results
    }

    override suspend fun getGainers(): List<MarketMover> = getMovers("biggest-gainers", true)

    override suspend fun getLosers(): List<MarketMover> = getMovers("biggest-losers", false)

    private suspend fun getMovers(endpoint: String, gainers: Boolean): List<MarketMover> {
        val movers = client.apiCall<List<FmpMarketMover>>(
            url = "https://financialmodelingprep.com/stable/$endpoint", apiKey = apiKey
        ) {}
        if (movers.any {
                it.symbol.isBlank() || !it.price.isFinite() || it.price < 0.0 ||
                !it.change.isFinite() || !it.changesPercentage.isFinite() ||
                (if (gainers) it.change < 0.0 || it.changesPercentage < 0.0
                 else it.change > 0.0 || it.changesPercentage > 0.0)
            }) throw StockProviderException(Failure.INVALID_RESPONSE)
        return movers.map { it.toMarketMover() }
    }

    override suspend fun getProfile(symbol: String): CompanyProfile? = financialCache.getOrLoad(
        "profile:$symbol",
        org.example.stocksteps.service.FinancialCachePolicy.STATEMENTS
    ) { listOfNotNull(loadProfile(symbol)) }.firstOrNull()

    private suspend fun loadProfile(symbol: String): CompanyProfile? {
        val profiles = client.apiCall<List<FmpCompanyProfile>>(
            url = "https://financialmodelingprep.com/stable/profile",
            apiKey = apiKey
        ) { parameter("symbol", symbol) }
        if (profiles.isEmpty()) return null
        val profile = profiles.singleOrNull()
            ?: throw StockProviderException(Failure.INVALID_RESPONSE)
        if (!profile.symbol.equals(symbol, ignoreCase = true)) {
            throw StockProviderException(Failure.INVALID_RESPONSE)
        }
        return profile.toCompanyProfile()
    }

    override suspend fun getQuote(symbol: String): StockQuote? = financialCache.getOrLoad(
        "quote:$symbol",
        freshness.ttl(org.example.stocksteps.service.SessionData.QUOTE, symbol)
    ) { loadQuote(symbol).let { listOfNotNull(it) } }.firstOrNull()

    private suspend fun loadQuote(symbol: String): StockQuote? {
        val quotes = client
            .apiCall<List<FmpQuote>>(
                url = "https://financialmodelingprep.com/stable/quote",
                apiKey = apiKey,
            ) {
                parameter("symbol", symbol)
            }
        if (quotes.isEmpty()) return null
        val quote = quotes.singleOrNull()
            ?: throw StockProviderException(Failure.INVALID_RESPONSE)
        if (!quote.symbol.equals(symbol, ignoreCase = true) ||
            quote.price?.let { !it.isFinite() || it < 0.0 } == true
        ) {
            throw StockProviderException(Failure.INVALID_RESPONSE)
        }
        return quote.toStockQuote()

    }
}
