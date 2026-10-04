package org.example.stocksteps.repositoryImpl

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import kotlinx.coroutines.*
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.*
import org.example.stocksteps.repository.models.*
import org.example.stocksteps.service.snapshotError

class FmpMarketDataProvider(
    private val client: HttpClient,
    private val apiKey: String,
    private val quoteFallback: StockQuoteProviderRepository? = null
) : MarketDataProvider {
    private val baseUrl = "https://financialmodelingprep.com/stable"
    override suspend fun getMarketIndices(): List<MarketIndex> = coroutineScope {
        listOf("SPY" to "S&P 500", "QQQ" to "Nasdaq-100", "DIA" to "Dow 30").map { (symbol, name) ->
            async {
                val primary = try {
                    val quotes = client.apiCall<List<FmpQuote>>("$baseUrl/quote", apiKey) { parameter("symbol", symbol) }
                    val quote = quotes.singleOrNull()?.takeIf { it.symbol.equals(symbol, true) }
                        ?: throw StockProviderException(StockProviderException.Failure.INVALID_RESPONSE)
                    quote.toMarketIndex(name).copy(symbol = symbol)
                } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    MarketIndex(symbol, name, error = snapshotError(cause))
                }
                if (primary.price != null || quoteFallback == null) primary
                else try {
                    fallbackIndex(symbol, name)
                } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    MarketIndex(symbol, name, error = snapshotError(cause))
                }
            }
        }.awaitAll()
    }
    private suspend fun fallbackIndex(symbol: String, name: String): MarketIndex {
        val quote = quoteFallback?.getQuote(symbol)
            ?: throw StockProviderException(StockProviderException.Failure.INVALID_RESPONSE)
        val price = quote.price?.takeIf { it.isFinite() && it > 0.0 }
            ?: throw StockProviderException(StockProviderException.Failure.INVALID_RESPONSE)
        return MarketIndex(
            symbol = symbol,
            name = name,
            price = price,
            change = quote.change?.takeIf { it.isFinite() },
            changePercent = quote.changePercent?.takeIf { it.isFinite() }
        )
    }

    override suspend fun getGainers() = movers("biggest-gainers").sortedByDescending { it.changePercent }
    override suspend fun getLosers() = movers("biggest-losers").sortedBy { it.changePercent }
    override suspend fun getMostActive() = movers("most-actives").sortedByDescending { it.volume }
    private suspend fun movers(endpoint: String): List<MarketMover> =
        client.apiCall<List<FmpSnapshotMover>>("$baseUrl/$endpoint", apiKey) {}
            .mapNotNull { it.toSnapshotMover() }.distinctBy { it.symbol }

    override suspend fun getMarketStatus(): MarketStatus {
        val hours = client.apiCall<List<FmpMarketHours>>("$baseUrl/exchange-market-hours", apiKey) { parameter("exchange", "NYSE") }
        return hours.singleOrNull { it.exchange.equals("NYSE", true) }?.toMarketStatus() ?: MarketStatus.UNKNOWN
    }
}
