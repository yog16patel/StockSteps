package org.example.stocksteps.userdata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.model.*
import org.example.stocksteps.network.configureStockStepsClient
import org.example.stocksteps.portfolio.*
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

class PortfolioMarketServiceTest {
    @Test fun bankOfCanadaMappingKeepsPublicationDatesAndCachesRequests(): Unit = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("2026-10-01", request.url.parameters["start_date"])
            respond("""{"observations":[{"d":"2026-10-01","FXUSDCAD":{"v":"1.30"}},{"d":"2026-10-08","FXUSDCAD":{"v":"1.40"}},{"d":"2026-10-09","FXUSDCAD":{"v":"bad"}}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }
        try {
            val source = BankOfCanadaPortfolioFx(client)
            val result = source.rates("2026-10-01", "2026-10-08")
            assertEquals(mapOf("2026-10-01" to "1.30", "2026-10-08" to "1.40"), result)
            assertEquals(result, source.rates("2026-10-01", "2026-10-08"))
            assertEquals(1, requests)
        } finally { client.close() }
    }
    @Test fun historicalValuationUsesDatedFxAndSharesAndDoesNotLoadHistoryForSummary(): Unit = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZoneOffset.UTC)
        val service = PortfolioService(InMemoryUserDataStore(), clock::millis)
        service.saveAccount("alice", PortfolioAccount("a", "CAD account"))
        service.saveTransaction("alice", PortfolioTransaction("opening", "a", TransactionType.OPENING_POSITION,
            "2026-10-01", PortfolioCurrency.USD, InstrumentRef("AAPL", currency = "USD"), "1", "100"), false)
        var historyCalls = 0
        val prices = object : PriceHistoryProvider {
            override suspend fun getIntradaySparkline(symbol: String) = Sparkline(symbol, emptyList())
            override suspend fun getIntradayPoints(symbol: String) = emptyList<PricePoint>()
            override suspend fun getDailyCloses(symbol: String): List<PricePoint> {
                historyCalls++
                return listOf(PricePoint("2026-09-30", 90.0), PricePoint("2026-10-01", 100.0), PricePoint("2026-10-08", 110.0))
            }
        }
        val fx = object : PortfolioFxSource {
            override suspend fun rates(from: String, through: String) = mapOf("2026-10-01" to "1.3", "2026-10-08" to "1.4")
        }
        val fixture = FixtureMarketDataSource(sampleFallback = true)
        val market = PortfolioMarketService(service, prices, WatchMarketData(StockService(fixture, fixture), fixture, NewsService(fixture)), fx, clock)
        assertEquals("130", market.report("alice", "a", "1M", false).summary.investedCost)
        assertEquals(0, historyCalls)
        val report = market.report("alice", "a", "ALL", true)
        assertEquals(listOf("2026-10-01", "2026-10-08"), report.history.map { it.date })
        assertEquals(listOf("130", "154"), report.history.map { it.totalValue })
        assertEquals(listOf("0", "24"), report.history.map { it.unrealizedGain })
        market.report("alice", "a", "1M", true)
        assertEquals(1, historyCalls)
    }
}
