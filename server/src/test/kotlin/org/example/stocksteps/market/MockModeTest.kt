package org.example.stocksteps.market

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.stocksteps.*
import org.example.stocksteps.appconfig.DataMode
import org.example.stocksteps.model.*
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.*
import java.io.File
import java.time.Instant
import kotlin.test.*

class MockModeTest {
    private val fixtures = FixtureMarketDataSource(root = "test-fixtures", now = { Instant.parse("2026-10-01T14:00:00Z") })

    @Test fun dataModeDefaultsToRealAndRefusesMockOnCloudRun() {
        assertEquals(DataMode.REAL, DataMode.fromEnvironment { null })
        assertEquals(DataMode.MOCK, DataMode.fromEnvironment { if (it == DataMode.ENV) "Mock" else null })
        assertFailsWith<IllegalStateException> {
            DataMode.fromEnvironment { mapOf(DataMode.ENV to "mock", "K_SERVICE" to "stocksteps")[it] }
        }
        assertFailsWith<IllegalStateException> { DataMode.fromEnvironment { if (it == DataMode.ENV) "fake" else null } }
    }

    @Test fun routesServeFixturesWithoutProviderKeys() = testApplication {
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing {
                val stocks = StockService(fixtures, fixtures)
                stockRoutes(stocks)
                marketSnapshotRoutes(MarketSnapshotService(fixtures))
                newsRoutes(NewsService(fixtures, simplification = null))
                sparklineRoutes(SparklineService(fixtures))
            }
        }
        val snapshot = client.get("/api/v1/market/snapshot").bodyAsText()
        assertTrue(snapshot.contains("\"SMPL\"") && snapshot.contains("smpl.png"))
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/SMPL/quote").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/stocks/NONE/quote").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/smpl/sparkline").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/stocks/NONE/sparkline").status)
        assertTrue(client.get("/api/v1/stocks/search?query=sample").bodyAsText().contains("SMPL"))
        assertFalse(client.get("/api/v1/stocks/search?query=sample").bodyAsText().contains("OTHR"))
        assertTrue(client.get("/api/v1/stocks/NONE/news").bodyAsText().trim() == "[]")
    }

    @Test fun sampleFallbackFillsMissingTickersConsistently() = runBlocking {
        val sampled = FixtureMarketDataSource(root = "test-fixtures", now = { Instant.parse("2026-10-01T14:00:00Z") }, sampleFallback = true)
        val quote = assertNotNull(sampled.getQuote("NONE"))
        assertEquals(quote, sampled.getQuote("none"), "sample values are stable per symbol")
        assertNotNull(sampled.getProfile("NONE")?.companyName)
        val intraday = sampled.getIntradayPoints("NONE")
        assertEquals(quote.previousClose, intraday.first().close)
        assertEquals(quote.price, intraday.last().close)
        assertEquals(quote.price, sampled.getDailyCloses("NONE").last().close)
        assertEquals(intraday.map { it.close }, sampled.getIntradaySparkline("NONE").closes)
        assertTrue(sampled.getFundamentals("NONE", "annual").valuation.metrics.isNotEmpty())
        // Captured data still wins, and fixture-only content stays absent.
        val smpl = assertNotNull(sampled.getQuote("SMPL"))
        // Captured values win; only missing quote fields are filled.
        assertEquals(fixtures.getQuote("SMPL"), smpl.copy(yearHigh = null, yearLow = null, open = null, volume = null, marketCap = null))
        assertNotNull(smpl.marketCap)
        // 52-week range is derived from the stored daily closes when the captured quote lacks it.
        assertEquals(10.0, smpl.yearHigh)
        assertEquals(8.0, smpl.yearLow)
        val noneQuote = assertNotNull(sampled.getQuote("NONE"))
        val daily = sampled.getDailyCloses("NONE")
        assertEquals(noneQuote.previousClose, daily[daily.lastIndex - 1].close, "the day before ends at the previous close")
        assertNull(sampled.getWhyMoving("NONE"))
        assertTrue(sampled.getCompanyNews("NONE").isEmpty())
    }

    @Test fun gapFillingKeepsCapturedFactsAndNoDividend() {
        val sample = org.example.stocksteps.repositoryImpl.fixture.SampleMarketData { Instant.parse("2026-10-01T14:00:00Z") }
        fun fact(value: Double? = null, availability: FinancialAvailability = FinancialAvailability.AVAILABLE) =
            FinancialFact(value = value, availability = availability, source = FinancialSource.PROVIDER_DIRECT)
        val stored = CompanyFundamentals("REAL",
            financials = CompanyFinancials(
                growth = mapOf("revenueGrowth" to fact(availability = FinancialAvailability.TEMPORARILY_UNAVAILABLE)),
                profitability = mapOf("netMargin" to fact(12.5)),
                shareholderReturns = mapOf("dividendYield" to fact(availability = FinancialAvailability.NO_DIVIDEND))
            ),
            valuation = CompanyValuation(metrics = mapOf("pe" to fact(20.0))))
        val generated = sample.fundamentals("REAL", "annual", StockQuote("REAL", "Real", 50.0, 1.0, 2.0, null, null, marketCap = 5_000_000_000))
        val filled = sample.fillFundamentals(stored, generated, 2025)
        assertEquals(12.5, filled.financials.profitability.getValue("netMargin").value, "captured fact kept")
        assertEquals(FinancialAvailability.NO_DIVIDEND, filled.financials.shareholderReturns.getValue("dividendYield").availability)
        assertEquals(FinancialAvailability.AVAILABLE, filled.financials.growth.getValue("revenueGrowth").availability, "gap filled")
        assertEquals(20.0, filled.valuation.metrics.getValue("pe").value)
        val history = filled.valuation.historical.getValue("pe")
        assertEquals(history.differencePercent!!, (20.0 / history.average!! - 1) * 100, 0.1)
    }

    @Test fun newsTimesShiftWithFixtureAge() = runBlocking {
        // Captured 2026-10-01T12:00Z, "now" two hours later: an article from 10:00 becomes 12:00.
        val news = fixtures.getNews(page = 0, limit = 1)
        assertEquals("2026-10-01T12:00:00Z", news.single().publishedAt)
    }

    /** Every captured fixture must decode into the public models, catching contract drift. */
    @Test fun capturedFixturesMatchPublicContracts() {
        val root = File("src/main/resources/fixtures")
        if (!root.isDirectory) return
        val json = Json { ignoreUnknownKeys = true }
        root.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            val text = file.readText()
            runCatching {
                when {
                    path == "manifest.json" -> Unit
                    path == "market/snapshot.json" -> json.decodeFromString(MarketSnapshot.serializer(), text)
                    path == "market/indices.json" -> json.decodeFromString(ListSerializer(IndexFixture.serializer()), text)
                    path == "stocks/search.json" -> json.decodeFromString(ListSerializer(StockSearchResult.serializer()), text)
                    path.startsWith("news/") -> json.decodeFromString(ListSerializer(NewsArticle.serializer()), text)
                    path.endsWith("/quote.json") -> json.decodeFromString(StockQuote.serializer(), text)
                    path.endsWith("/profile.json") -> json.decodeFromString(CompanyProfile.serializer(), text)
                    path.endsWith("/sparkline.json") -> json.decodeFromString(Sparkline.serializer(), text)
                    path.contains("/fundamentals-") -> json.decodeFromString(CompanyFundamentals.serializer(), text)
                    path.endsWith("/chart-daily.json") || path.endsWith("/chart-intraday.json") ->
                        json.decodeFromString(ListSerializer(PricePoint.serializer()), text)
                    path.endsWith("/why-moving.json") -> json.decodeFromString(WhyMoving.serializer(), text)
                    path.endsWith("/earnings-upcoming.json") -> json.decodeFromString(UpcomingEarnings.serializer(), text)
                    path.endsWith("/earnings-quarterly.json") -> json.decodeFromString(ListSerializer(org.example.stocksteps.service.QuarterlyEarnings.serializer()), text)
                    else -> fail("Unexpected fixture file: $path")
                }
            }.onFailure { fail("Fixture $path does not match the public contract: ${it.message}") }
        }
    }
}
