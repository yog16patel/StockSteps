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
    private val today: () -> java.time.LocalDate = { java.time.LocalDate.now(java.time.ZoneOffset.UTC) }
) : StockProviderRepository {
    private val financialCache = org.example.stocksteps.service.CompanyFinancialCache()
    private val fundamentalsLoader = FmpFundamentalsLoader(client, apiKey, financialCache, today)

    override suspend fun getFundamentals(symbol: String, period: String) =
        fundamentalsLoader.load(symbol, period) {
            coroutineScope {
                val quote = async { getQuote(symbol) }
                val profile = async { getProfile(symbol) }
                quote.await() to profile.await()
            }
        }

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
        org.example.stocksteps.service.FinancialCachePolicy.QUOTE
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
