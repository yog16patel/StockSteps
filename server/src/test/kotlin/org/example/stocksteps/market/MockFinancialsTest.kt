package org.example.stocksteps.market

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.*
import org.example.stocksteps.model.*
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.CompanyFinancialService
import org.example.stocksteps.service.NewsService
import java.time.Instant
import kotlin.test.*

/** The shipped MOCK fixtures (src/main/resources/fixtures) for the Financials screen. */
class MockFinancialsTest {
    private val fixtures = FixtureMarketDataSource(now = { Instant.parse("2026-10-07T20:00:00Z") }, sampleFallback = true)
    private val json = Json { ignoreUnknownKeys = true }

    private fun history(symbol: String, period: String) = runBlocking { fixtures.getFundamentals(symbol, period).history }

    @Test fun curatedCompaniesHaveConsistentAnnualAndQuarterlyHistory() {
        for (symbol in listOf("MSFT", "AAPL", "NVDA", "TSLA", "TD")) {
            val annual = history(symbol, "annual")
            val quarterly = history(symbol, "quarter")
            assertTrue(annual.size >= 6 && annual.all { it.period == "FY" }, "$symbol annual")
            assertTrue(quarterly.size >= 7 && quarterly.all { it.period.startsWith("Q") }, "$symbol quarterly")
            assertEquals(annual.sortedByDescending { it.date }, annual, "$symbol newest first")
            for (row in annual + quarterly) {
                if (row.operatingCashFlow != null && row.capitalExpenditure != null) {
                    assertEquals(row.operatingCashFlow!! - row.capitalExpenditure!!, row.freeCashFlow!!, 1.0, "$symbol ${row.date} FCF = OCF − capex")
                }
                if (row.totalAssets != null && row.totalLiabilities != null && row.equity != null) {
                    assertEquals(row.totalAssets!! - row.totalLiabilities!!, row.equity!!, 1.0, "$symbol ${row.date} equity")
                }
            }
            // Latest facts are read back from the same history, so both screens agree.
            val facts = runBlocking { fixtures.getFundamentals(symbol, "annual") }.financials
            assertEquals(annual.first().revenue, facts.growth.getValue("revenue").amount?.toDouble())
        }
    }

    @Test fun scenariosCoverCalendarsCurrenciesAndMissingData() {
        assertEquals("2025-06-30", history("MSFT", "annual").first().date, "June fiscal year")
        assertEquals("2026-01-31", history("NVDA", "annual").first().date, "January fiscal year")
        val td = history("TD", "annual")
        assertTrue(td.all { it.currency == "CAD" } && td.first().date == "2025-10-31", "CAD bank, October fiscal year")
        assertTrue(td.all { it.grossProfit == null && it.capitalExpenditure == null }, "banks report no gross profit or capex")
        val tsla = history("TSLA", "annual")
        assertTrue(tsla.any { (it.netIncome ?: 0.0) < 0 }, "a net-loss year")
        assertTrue(tsla.any { (it.freeCashFlow ?: 0.0) < 0 }, "a negative free-cash-flow year")
        assertEquals(7, history("TSLA", "quarter").size, "one missing quarter is not invented")
        assertEquals(FinancialAvailability.NO_DIVIDEND, runBlocking { fixtures.getFundamentals("TSLA", "annual") }.financials.shareholderReturns.getValue("dividendYield").availability)
        val longn = history("LONGN", "annual")
        assertEquals(listOf(2025, 2024, 2022, 2021), longn.map { it.fiscalYear }, "FY2023 is missing")
        assertEquals(0.0, longn.last().revenue, "pre-revenue first year (zero denominator)")
        assertNull(longn[1].netIncome, "net income not reported")
        val nvdaQuarter = runBlocking { fixtures.getFundamentals("NVDA", "quarter") }
        assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, nvdaQuarter.datasets["balance"], "partial failure")
        assertTrue(nvdaQuarter.history.none { it.cash != null })
    }

    @Test fun anyOtherTickerGetsGeneratedHistoryThatMatchesItsFacts() = runBlocking {
        val sample = fixtures.getFundamentals("ZZZQ", "annual")
        assertEquals(6, sample.history.size)
        assertEquals(sample.history.first().revenue, sample.financials.growth.getValue("revenue").amount?.toDouble())
        val quarter = fixtures.getFundamentals("ZZZQ", "quarter")
        assertEquals(8, quarter.history.size)
        // Balance sheets are snapshots: a quarter's cash is never a quarter of the year's cash.
        assertEquals(sample.history.first().cash, quarter.history.first().cash)
    }

    @Test fun mockFundamentalsRouteServesTheSameContractWithoutProviders() = testApplication {
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { companyFinancialRoutes(CompanyFinancialService(fixtures)) }
        }
        for (period in listOf("annual", "quarter")) {
            val response = client.get("/api/v1/stocks/msft/fundamentals?period=$period")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString(CompanyFundamentals.serializer(), response.bodyAsText())
            assertTrue(body.history.isNotEmpty(), period)
        }
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/MSFT/fundamentals?period=monthly").status)
    }

    @Test fun mockModeIsWiredOnlyToFixtures() {
        // Every MOCK data source is the fixture source: no provider repository or HTTP client exists.
        val sources = mockDataSources()
        listOf(sources.stockProvider, sources.quoteProvider, sources.marketData, sources.priceHistory, sources.earnings)
            .forEach { assertIs<FixtureMarketDataSource>(it) }
        assertIs<NewsService>(sources.news)
        // Explanations use deterministic templates in MOCK: no AI generator or narrator exists.
        assertIs<org.example.stocksteps.news.TemplateArticleInsightGenerator>(sources.insights)
        assertNull(sources.narrator)
    }
}
