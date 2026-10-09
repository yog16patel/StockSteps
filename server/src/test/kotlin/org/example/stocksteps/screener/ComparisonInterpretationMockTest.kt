package org.example.stocksteps.screener

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.*
import org.example.stocksteps.userdata.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Company Comparison Phase 2 over the MOCK fixtures: the shared interpretation engine applied to real
 * `/api/v1/compare` responses (what both apps receive). Fixture-only: no provider, AI or Firebase calls.
 */
class ComparisonInterpretationMockTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val fixture = FixtureMarketDataSource(sampleFallback = true)
    private val stocks = StockService(fixture, fixture)
    private val financials = CompanyFinancialService(fixture)
    private val earnings = org.example.stocksteps.earnings.EarningsService(org.example.stocksteps.earnings.FixtureEarningsDataSource(), stocks, PriceChartService(fixture),
        InMemoryUserDataStore(), EntitlementService(InMemoryUserDataStore(), clock::millis, true), clock, true, null)
    private val fxCalls = AtomicInteger()

    private fun service(fail: Set<String> = emptySet()) = ScreenerService(FixtureScreenerUniverse(), stocks, { symbol ->
        if (symbol in fail) throw IllegalStateException("provider down")
        financials.getFundamentals(symbol, "annual")
    }, PriceChartService(fixture), { 1 / 1.35 }, clock, sampleData = true, fundamentalsPerHour = 0, fullRecords = true,
        quarterlyRevenueGrowth = { symbol ->
            try { quarterRevenueGrowth(earnings.latestResults(symbol)) } catch (cause: org.example.stocksteps.earnings.EarningsRequestException) {
                if (cause.status == 404) MetricValue(note = "No published quarterly results are available for this company.") else throw cause
            }
        },
        provenance = listOf("Sample fixture data for development; not live market data."),
        usdPerCadQuote = { fxCalls.incrementAndGet(); FxConversion("CAD", "USD", 1 / 1.35, "2026-10-07", "fixed sample rate") })

    private fun ApplicationTestBuilder.install(service: ScreenerService) = application {
        configureApiErrors()
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        routing { screenerRoutes(service, RequestRateLimiter(1_000)) }
    }

    private suspend fun ApplicationTestBuilder.guide(symbols: String): Pair<ComparisonResponse, ComparisonInterpretation> {
        val response = json.decodeFromString(ComparisonResponse.serializer(), client.get("/api/v1/compare?symbols=$symbols").bodyAsText())
        return response to ComparisonInterpretationEngine.interpret(response.companies, response.fx)
    }

    private val forbidden = Regex("(?i)\\b(better|best|worst|winner|buy|sell|cheap|expensive|bargain|overvalued|undervalued|outperform|recommend|will rise|will fall)\\b")

    private fun assertGrounded(response: ComparisonResponse, i: ComparisonInterpretation) {
        assertTrue(i.summary.size in 1..ComparisonInterpretationEngine.SUMMARY_LIMIT)
        val statements = i.summary.map { it.text } + i.metrics.values.flatMap { listOfNotNull(it.observation, it.headline) }
        statements.forEach { assertFalse(forbidden.containsMatchIn(it), it) }
        // Every percentage, multiple or ratio quoted is a value the table displays for one of these companies.
        val displayed = response.companies.mapNotNull { it.record }.flatMap { r ->
            r.metrics.map { (id, mv) -> MetricFormatter.cell(ScreenerDefinitions.metric(id), mv, r.currency).text }
        }.toSet()
        statements.forEach { s -> Regex("[−+]?[0-9]+\\.[0-9]+[×%]|\\b[0-9]+\\.[0-9]{2}\\b").findAll(s).map { it.value }.filterNot { it.startsWith("0.74") }.forEach { n ->
            assertTrue(n in displayed || "+$n" in displayed, "\"$n\" in \"$s\" isn't a displayed value") } }
    }

    @Test fun sameIndustryAndCrossBorderBanks() = testApplication {
        install(service())
        val (response, i) = guide("RY.TO,TD")
        assertGrounded(response, i)
        assertEquals(listOf(FxConversion("CAD", "USD", 1 / 1.35, "2026-10-07", "fixed sample rate")), response.fx)   // additive field, decoded by the apps
        assertTrue(i.summary.first().text.contains("same industry (Banks - Diversified)"))
        val cap = i.metrics.getValue("marketCap")
        assertEquals(Comparability.COMPARABLE_WITH_CAVEATS, cap.comparability)
        assertTrue(cap.observation.contains("C$") && cap.observation.contains("≈ US$") && cap.caveats.first().contains("1 CAD = 0.7407 USD (fixed sample rate, 2026-10-07)"))
        assertEquals(Comparability.NOT_COMPARABLE, i.metrics.getValue("debtEquity").comparability)          // banks: not applicable, said plainly
        assertTrue(i.metrics.getValue("revenueGrowth").caveats.none { it.contains("different currencies") })      // both report in CAD
        // USD-only comparisons don't fetch or show a rate.
        val calls = fxCalls.get()
        assertTrue(guide("AAPL,MSFT").first.fx.isEmpty()); assertEquals(calls, fxCalls.get())
    }

    @Test fun differentIndustriesFiscalCalendarsAndThreeOrFourCompanies() = testApplication {
        install(service())
        val (cross, ci) = guide("AAPL,JPM")
        assertGrounded(cross, ci)
        assertNotNull(ci.industryNote)
        assertTrue(ci.metrics.getValue("pe").caveats.any { it.contains("different sectors") })
        assertTrue(ci.metrics.getValue("netMargin").caveats.any { it.contains("bank or insurer") })
        assertTrue(ci.metrics.getValue("revenueGrowth").caveats.any { it.contains("different months") })     // Sep vs Dec fiscal years
        val (three, ti) = guide("AMD,NVDA,INTC")
        assertGrounded(three, ti)
        assertTrue(ti.metrics.getValue("pe").observation.startsWith("Among these 3 companies"))
        assertTrue(ti.metrics.getValue("dividendYield").observation.contains("Intel Corporation paid none"))
        val (four, fi) = guide("AAPL,MSFT,RY.TO,GIPR")
        assertGrounded(four, fi)
        assertTrue(fi.metrics.getValue("pe").caveats.any { it.contains("funds from operations") })             // REIT context
        assertTrue(fi.metrics.getValue("debtEquity").caveats.first().startsWith("Royal Bank of Canada: Not compared"))
        assertTrue(fi.metrics.getValue("revenueGrowth").caveats.any { it.contains("clearly different dates") })
        assertTrue(fi.metrics.getValue("revenueGrowth").caveats.any { it.contains("different currencies (USD and CAD)") })
    }

    @Test fun lossesMissingValuesStaleDataAndMissingSector() = testApplication {
        install(service())
        val (loss, li) = guide("KO,RIVN")
        assertGrounded(loss, li)
        assertEquals(Comparability.INSUFFICIENT_DATA, li.metrics.getValue("pe").comparability)                // negative EPS: no ordinary P/E reading
        assertTrue(li.metrics.getValue("netMargin").meaning!!.contains("doesn't mean a company will stay unprofitable"))
        assertTrue(li.metrics.getValue("dividendYield").observation.endsWith("Rivian Automotive Inc paid none."))
        val (missing, mi) = guide("CSU.TO,LONGN")
        assertGrounded(missing, mi)
        assertTrue(mi.metrics.getValue("dividendYield").caveats.any { it.contains("isn't the same as no dividend") })
        assertEquals(Comparability.INSUFFICIENT_DATA, mi.metrics.getValue("priceSales").comparability)
        val (stale, si) = guide("BB.TO,MSFT")                                                                  // BB.TO: stale statements, loss, Feb fiscal year
        assertGrounded(stale, si)
        assertTrue(si.metrics.getValue("netMargin").caveats.any { it.contains("more than 18 months old") })
        val (unknown, ui) = guide("LUCY,AAPL")
        assertGrounded(unknown, ui)
        assertTrue(ui.summary.first().text.startsWith("Sector isn't reported"))
        assertTrue(ui.metrics.getValue("priceSales").observation.contains("the same price-to-sales ratio as displayed (10.6×)"))  // equal values
    }

    @Test fun partiallyAvailableDataNeverInventsValues() = testApplication {
        install(service(fail = setOf("MSFT")))                                                                 // fundamentals failure: price only
        val (response, i) = guide("AAPL,MSFT")
        assertGrounded(response, i)
        val msft = response.companies.first { it.symbol == "MSFT" }.record!!
        assertNotEquals(FinancialAvailability.AVAILABLE, msft.metrics["pe"]?.availability)                    // missing P/E
        for (id in listOf("pe", "netMargin", "revenueGrowth", "debtEquity", "priceSales")) {
            val m = i.metrics.getValue(id)
            assertEquals(Comparability.INSUFFICIENT_DATA, m.comparability, id)
            assertTrue(m.observation.startsWith("Only Apple Inc. has a value"), m.observation)
            assertNull(m.headline)
        }
        assertEquals(Comparability.COMPARABLE, i.metrics.getValue("marketCap").comparability)                // price data still compares
    }

    @Test fun interpretationIsDeterministicAcrossRequests() = testApplication {
        install(service())
        val first = guide("AAPL,MSFT,KO").second
        assertEquals(first, guide("AAPL,MSFT,KO").second)
    }
}
