package org.example.stocksteps.market

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.Sparkline
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.models.FmpIntradayBar
import org.example.stocksteps.repository.models.FmpSnapshotMover
import org.example.stocksteps.repository.models.toSnapshotMover
import org.example.stocksteps.repository.models.toSparkline
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.SparklineService
import org.example.stocksteps.sparklineRoutes
import kotlin.test.*

class SparklineTest {
    @Test fun keepsOnlyLatestSessionOldestFirstAndDropsInvalidCloses() {
        val sparkline = listOf(
            FmpIntradayBar("2026-10-06 15:55:00", 101.0),
            FmpIntradayBar("2026-10-06 09:30:00", 100.0),
            FmpIntradayBar("2026-10-06 12:00:00", Double.NaN),
            FmpIntradayBar("2026-10-05 15:55:00", 90.0),
            FmpIntradayBar(null, 5.0)
        ).toSparkline("AAPL")
        assertEquals(listOf(100.0, 101.0), sparkline.closes)
        assertEquals("2026-10-06", sparkline.sessionDate)
        assertTrue(emptyList<FmpIntradayBar>().toSparkline("AAPL").closes.isEmpty())
    }

    @Test fun moversCarryProviderLogo() {
        val mover = FmpSnapshotMover("brk.b", price = 1.0).toSnapshotMover()!!
        assertEquals("https://images.financialmodelingprep.com/symbol/BRK.B.png", mover.logoUrl)
    }

    @Test fun providerRequestsFiveMinuteBarsForExactSymbol() = runBlocking {
        var path = ""; var symbol: String? = null
        val client = HttpClient(MockEngine { request ->
            path = request.url.encodedPath; symbol = request.url.parameters["symbol"]
            respond("""[{"date":"2026-10-06 09:35:00","close":2},{"date":"2026-10-06 09:30:00","close":1}]""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val result = FmpPriceHistoryProvider(client, "test-key").getIntradaySparkline("SHOP.TO")
        assertEquals("/stable/historical-chart/5min", path)
        assertEquals("SHOP.TO", symbol)
        assertEquals(listOf(1.0, 2.0), result.closes)
        client.close()
    }

    @Test fun serviceCachesSuccessAndAccessDenials() = runBlocking {
        var calls = 0
        val service = SparklineService(object : PriceHistoryProvider {
            override suspend fun getIntradaySparkline(symbol: String): Sparkline {
                calls++
                if (symbol == "DENY") throw StockProviderException(StockProviderException.Failure.UNAVAILABLE, 402)
                return Sparkline(symbol, listOf(1.0, 2.0))
            }
        }, CompanyFinancialCache(now = { 0L }))
        service.getSparkline("AAPL"); service.getSparkline("AAPL")
        repeat(2) { assertFailsWith<StockProviderException> { service.getSparkline("DENY") } }
        assertEquals(2, calls)
    }

    @Test fun rateLimitsCoolDownLongerThanTransientFailures() {
        val limited = StockProviderException(StockProviderException.Failure.RATE_LIMITED, 429)
        val transient = StockProviderException(StockProviderException.Failure.UNAVAILABLE, 500)
        assertEquals(600_000L, org.example.stocksteps.service.providerCooldown(limited, 1L))
        assertEquals(30_000L, org.example.stocksteps.service.providerCooldown(transient, 1L))
        assertEquals(1L, org.example.stocksteps.service.providerCooldown(null, 1L))
    }

    @Test fun routeValidatesAndReportsMissingHistory() = testApplication {
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing {
                sparklineRoutes(SparklineService(object : PriceHistoryProvider {
                    override suspend fun getIntradaySparkline(symbol: String) =
                        if (symbol == "AAPL") Sparkline(symbol, listOf(1.0, 2.0), "2026-10-06") else Sparkline(symbol, listOf(1.0))
                }))
            }
        }
        val ok = client.get("/api/v1/stocks/aapl/sparkline")
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue(ok.bodyAsText().contains("\"closes\":[1.0,2.0]"))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/stocks/THIN/sparkline").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/!!/sparkline").status)
    }

    @Test fun profilesAreCachedSoHomeLogosDoNotSpendQuota() = runBlocking {
        var calls = 0
        val provider = object : org.example.stocksteps.repository.StockProviderRepository {
            override suspend fun getGainers() = emptyList<org.example.stocksteps.model.MarketMover>()
            override suspend fun getLosers() = emptyList<org.example.stocksteps.model.MarketMover>()
            override suspend fun searchStocks(query: String) = emptyList<org.example.stocksteps.model.StockSearchResult>()
            override suspend fun getQuote(symbol: String): org.example.stocksteps.model.StockQuote? = null
            override suspend fun getProfile(symbol: String): org.example.stocksteps.model.CompanyProfile? {
                calls++
                if (symbol == "LIMIT") throw StockProviderException(StockProviderException.Failure.RATE_LIMITED, 429)
                return null
            }
        }
        val service = org.example.stocksteps.service.StockService(provider, profileCache = CompanyFinancialCache(now = { 0L }))
        repeat(3) { assertNull(service.getProfile("NONE")) }
        repeat(2) { assertFailsWith<StockProviderException> { service.getProfile("LIMIT") } }
        assertEquals(2, calls)
    }
}
