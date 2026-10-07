package org.example.stocksteps.repositoryImpl

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.model.Sparkline
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repository.models.FmpIntradayBar
import org.example.stocksteps.repository.models.toSparkline
import org.example.stocksteps.repository.models.toLatestSessionPoints
import org.example.stocksteps.repository.models.toDailyPoints
import org.example.stocksteps.repository.models.FmpDailyPrice
import org.example.stocksteps.model.PricePoint

/** FMP 5-minute bars; the most recent session becomes a ~78-point sparkline. */
class FmpPriceHistoryProvider(private val client: HttpClient, private val apiKey: String) : PriceHistoryProvider {
    private val baseUrl = "https://financialmodelingprep.com/stable"
    override suspend fun getIntradaySparkline(symbol: String): Sparkline = intradayBars(symbol).toSparkline(symbol)

    override suspend fun getIntradayPoints(symbol: String): List<PricePoint> = intradayBars(symbol).toLatestSessionPoints()

    /** One call returns the available daily history; ranges are sliced by PriceChartService. */
    override suspend fun getDailyCloses(symbol: String): List<PricePoint> =
        client.apiCall<List<FmpDailyPrice>>("$baseUrl/historical-price-eod/light", apiKey) {
            parameter("symbol", symbol)
        }.toDailyPoints()

    private suspend fun intradayBars(symbol: String): List<FmpIntradayBar> =
        client.apiCall<List<FmpIntradayBar>>("$baseUrl/historical-chart/5min", apiKey) {
            parameter("symbol", symbol)
        }
}
