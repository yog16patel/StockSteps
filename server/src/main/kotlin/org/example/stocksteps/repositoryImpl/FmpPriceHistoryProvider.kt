package org.example.stocksteps.repositoryImpl

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.model.Sparkline
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repository.models.FmpIntradayBar
import org.example.stocksteps.repository.models.toSparkline

/** FMP 5-minute bars; the most recent session becomes a ~78-point sparkline. */
class FmpPriceHistoryProvider(private val client: HttpClient, private val apiKey: String) : PriceHistoryProvider {
    private val baseUrl = "https://financialmodelingprep.com/stable"
    override suspend fun getIntradaySparkline(symbol: String): Sparkline =
        client.apiCall<List<FmpIntradayBar>>("$baseUrl/historical-chart/5min", apiKey) {
            parameter("symbol", symbol)
        }.toSparkline(symbol)
}
