package org.example.stocksteps.repositoryImpl

import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderException.Failure
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val apiKey: String
) : StockProviderRepository {
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

    override suspend fun getQuote(symbol: String): StockQuote? {
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
