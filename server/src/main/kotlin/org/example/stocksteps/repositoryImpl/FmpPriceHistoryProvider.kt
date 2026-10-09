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

/**
 * FMP 5-minute bars; the most recent session becomes a ~78-point sparkline. The raw bars are cached once
 * per symbol here, so the 1D chart and Home sparklines share one request (their own caches hold the shaped
 * results). Failures aren't cached at this level; callers apply their cooldowns.
 */
class FmpPriceHistoryProvider(
    private val client: HttpClient,
    private val apiKey: String,
    private val bars: org.example.stocksteps.service.CompanyFinancialCache = org.example.stocksteps.service.CompanyFinancialCache(capacity = 256, name = "fmp-intraday")
) : PriceHistoryProvider {
    private val baseUrl = "https://financialmodelingprep.com/stable"
    override suspend fun getIntradaySparkline(symbol: String): Sparkline = intradayBars(symbol).toSparkline(symbol)

    override suspend fun getIntradayPoints(symbol: String): List<PricePoint> = intradayBars(symbol).toLatestSessionPoints()

    /** One call returns the available daily history; ranges are sliced by PriceChartService. */
    override suspend fun getDailyCloses(symbol: String): List<PricePoint> =
        client.apiCall<List<FmpDailyPrice>>("$baseUrl/historical-price-eod/light", apiKey) {
            parameter("symbol", symbol)
        }.toDailyPoints()

    private suspend fun intradayBars(symbol: String): List<FmpIntradayBar> =
        bars.getOrLoad("5min:$symbol", org.example.stocksteps.service.FinancialCachePolicy.INTRADAY) {
            client.apiCall<List<FmpIntradayBar>>("$baseUrl/historical-chart/5min", apiKey) {
                parameter("symbol", symbol)
            }
        }
}
