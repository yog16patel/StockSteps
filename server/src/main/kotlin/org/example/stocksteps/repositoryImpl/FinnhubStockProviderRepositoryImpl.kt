package org.example.stocksteps.repositoryImpl

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.repository.StockQuoteProviderRepository
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderException.Failure
import org.example.stocksteps.repository.models.FinnhubQuote
import org.example.stocksteps.repository.models.toStockQuote

class FinnhubStockProviderRepositoryImpl(
    private val client: HttpClient,
    private val apiKey: String
) : StockQuoteProviderRepository {
    override suspend fun getQuote(symbol: String): StockQuote? {
        val quote = client.apiCall<FinnhubQuote>("https://finnhub.io/api/v1/quote") {
            parameter("symbol", symbol)
            header("X-Finnhub-Token", apiKey)
        }
        // Finnhub's no-data quote uses zero prices and a zero timestamp.
        if (quote.timestamp == 0L && listOf(quote.currentPrice, quote.dayHigh,
                quote.dayLow, quote.open, quote.previousClose).all { it == 0.0 } &&
            listOf(quote.change, quote.changePercent).all { it == null || it == 0.0 }) return null
        if (quote.timestamp <= 0L ||
            listOf(quote.currentPrice, quote.dayHigh, quote.dayLow, quote.open, quote.previousClose)
                .any { !it.isFinite() || it < 0.0 } ||
            listOfNotNull(quote.change, quote.changePercent).any { !it.isFinite() }) {
            throw StockProviderException(Failure.INVALID_RESPONSE)
        }
        return quote.toStockQuote(symbol)
    }
}
