package org.example.stocksteps.earnings

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.screener.RequestRateLimiter
import org.example.stocksteps.service.*
import org.example.stocksteps.userdata.*
import java.time.*
import kotlin.test.*

/** Earnings Intelligence Lite, Phase 2: server-calculated results from the MOCK fixtures (clock 2026-10-07 17:15 New York). */
class EarningsResultsServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-07T21:15:00Z"), ZoneOffset.UTC)
    private val fixture = FixtureMarketDataSource(sampleFallback = true)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun service(source: EarningsDataSource = FixtureEarningsDataSource(), sampleData: Boolean = true, cache: CompanyFinancialCache = CompanyFinancialCache(512)): EarningsService {
        val store = InMemoryUserDataStore()
        return EarningsService(source, StockService(fixture, fixture), PriceChartService(fixture), store, EntitlementService(store, clock::millis, true), clock, sampleData, null, cache = cache)
    }

    @Test fun specExampleReportWithGrowthRevisionAndMissingPublication(): Unit = runBlocking {
        val r = service().resultsFor("SSRV:2026-Q3")
        val i = r.insights
        assertEquals("SSRV:2026-Q3", r.report.reportId); assertEquals("Q3 FY2026", r.report.period); assertEquals("2026-09-30", r.report.fiscalPeriodEnd)
        assertEquals("1.45", i.eps.actual); assertEquals("1.2", i.eps.estimate); assertEquals("0.25", i.eps.surpriseAmount); assertEquals(Classification.BEAT, i.eps.classification)
        assertEquals("8500000000", i.revenue.actual); assertEquals("300000000", i.revenue.surpriseAmount); assertEquals(Classification.BEAT, i.revenue.classification)
        assertEquals("7.59493671", i.yearOverYear.percent); assertEquals("4.9382716", i.quarterOverQuarter.percent)
        assertTrue(r.report.revised); assertNull(r.report.publishedAt)
        assertTrue(i.comparisonWarnings.any { it.contains("revised") } && i.comparisonWarnings.any { it.contains("publication time") })
        assertTrue(r.sampleData && r.report.sources.map { it.role } == listOf("actual", "estimate"))
        assertEquals(r.insights, service().resultsFor("SSRV:2026-Q3").insights)                  // deterministic
    }

    @Test fun fixtureScenariosClassifyEachMeasureIndependently(): Unit = runBlocking {
        val s = service()
        suspend fun c(id: String) = s.resultsFor(id).insights.let { it.eps.classification to it.revenue.classification }
        val b = Classification.BEAT; val m = Classification.MISS; val met = Classification.MET; val u = Classification.UNAVAILABLE
        assertEquals(b to b, c("NVDA:2027-Q2"))                                                   // beat / beat
        assertEquals(b to m, c("AAPL:2026-Q3"))                                                   // EPS beat, revenue miss (US)
        assertEquals(b to m, c("RY.TO:2026-Q3"))                                                  // same, Canadian listing
        assertEquals(m to b, c("TD:2026-Q3"))                                                     // EPS miss, revenue beat
        assertEquals(m to m, c("KO:2026-Q2"))                                                     // miss / miss
        assertEquals(met to m, c("SSFC:2026-Q3"))                                                 // EPS met exactly
        assertEquals(b to met, c("SSRM:2027-Q1"))                                                 // revenue met exactly
        assertEquals(u to u, c("SSNE:2026-Q3"))                                                   // both estimates missing
        assertEquals(u, c("CSU.TO:2026-Q2").first)                                                // EPS estimate missing (and CAD vs USD)
        assertEquals(u, c("CNR.TO:2026-Q2").second)                                               // revenue estimate missing
        assertEquals(u, c("JNJ:2026-Q2").first)                                                   // adjusted vs GAAP
        assertEquals(u to u, c("SSAN:2026-Q3"))                                                   // annual estimate vs quarter
        assertEquals(b, c("RIVN:2026-Q2").first)                                                  // zero EPS estimate
        assertNull(s.resultsFor("RIVN:2026-Q2").insights.eps.surprisePercent)
        assertEquals(b, c("SSLL:2026-Q2").first)                                                  // loss smaller than expected
        assertEquals(m, c("SSLL:2026-Q3").first)                                                  // loss larger than expected
        val partial = s.resultsFor("BB.TO:2027-Q2")                                               // partial provider data
        assertTrue("revenueActual" in partial.report.unavailableFields); assertEquals(u, partial.insights.revenue.classification)
    }

    @Test fun growthUsesMatchingFiscalPeriodsOnly(): Unit = runBlocking {
        val s = service()
        assertTrue(s.resultsFor("SSLL:2026-Q3").insights.yearOverYear.percent!!.startsWith("-11.1"))      // negative growth
        assertEquals("0", s.resultsFor("SSRM:2027-Q1").insights.yearOverYear.percent)                    // flat, non-calendar fiscal year
        assertEquals("SSRM:2026-Q4", s.resultsFor("SSRM:2027-Q1").report.previousQuarter?.reportId)      // fiscal-year rollover
        val ssne = s.resultsFor("SSNE:2026-Q3").insights
        assertNull(ssne.yearOverYear.percent); assertTrue(ssne.yearOverYear.reason!!.contains("zero"))   // zero prior-year revenue
        assertNull(ssne.quarterOverQuarter.percent)                                                      // missing previous quarter
        assertTrue(s.resultsFor("SSAN:2026-Q3").insights.yearOverYear.reason!!.contains("isn't available")) // missing prior year
        assertTrue(s.resultsFor("SSFC:2026-Q3").insights.yearOverYear.reason!!.contains("fiscal calendar changed"))
        assertNotNull(s.resultsFor("SSFC:2026-Q3").insights.quarterOverQuarter.percent)
    }

    @Test fun latestListMissingAndInvalidReports(): Unit = runBlocking {
        val s = service()
        assertEquals("SSRV:2026-Q3", s.latestResults("SSRV").report.reportId)
        assertEquals("AAPL:2026-Q3", s.latestResults("AAPL").report.reportId)                     // AAPL Q4 is only scheduled
        assertEquals(404, assertFailsWith<EarningsRequestException> { s.latestResults("GOOGL") }.status)   // no report available
        assertEquals("NOT_REPORTED", assertFailsWith<EarningsRequestException> { s.resultsFor("AAPL:2026-Q4") }.code) // scheduled: no fake result
        assertEquals("NOT_REPORTED", assertFailsWith<EarningsRequestException> { s.resultsFor("BCE.TO:2026-Q3") }.code) // date passed, no results
        assertEquals("NOT_FOUND", assertFailsWith<EarningsRequestException> { s.resultsFor("AAPL:2019-Q1") }.code)
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.resultsFor("not-a-report") }.status)
        val list = s.reports("SSRV")
        assertEquals(listOf("SSRV:2026-Q3", "SSRV:2026-Q2", "SSRV:2025-Q3"), list.reports.map { it.reportId })
        assertEquals(Classification.BEAT, list.reports.first().eps)
        // Calendar items carry the report id only once results exist.
        val week = s.calendar(EarningsCalendarQuery("2026-10-05", "2026-10-11"))
        assertEquals("SSRV:2026-Q3", week.items.single { it.event.symbol == "SSRV" }.reportId)
        assertNull(week.items.single { it.event.symbol == "BCE.TO" }.reportId)
        assertNull(week.items.single { it.event.symbol == "INTC" }.reportId)
    }

    @Test fun providerFailuresRetryOnceThenServeTheLastRealCopyOrFail(): Unit = runBlocking {
        var failures = 0
        var calls = 0
        val source = object : EarningsDataSource {
            val inner = FixtureEarningsDataSource()
            override val label = "test"
            override suspend fun calendar(from: LocalDate, to: LocalDate) = inner.calendar(from, to)
            override suspend fun history(symbol: String): List<EarningsEvent> { calls++; if (failures > 0) { failures--; error("timeout") }; return inner.history(symbol) }
        }
        var millis = 0L
        val s = service(source, sampleData = false, cache = CompanyFinancialCache(512, now = { millis }))
        failures = 1
        assertEquals(DataFreshness.FRESH, s.resultsFor("SSRV:2026-Q3").freshness); assertEquals(2, calls)   // one transient failure, retried
        millis += 30_000_000; failures = 5
        val stale = s.resultsFor("SSRV:2026-Q3")
        assertEquals(DataFreshness.STALE, stale.freshness); assertEquals("1.45", stale.insights.eps.actual)  // last real copy, labelled
        assertEquals(503, assertFailsWith<EarningsRequestException> { s.resultsFor("KO:2026-Q2") }.status)  // never sample data in REAL
        failures = 0
        assertNotEquals(DataFreshness.STALE, s.latestResults("KO", scenario = "stale-cache").freshness)    // MOCK scenarios ignored in REAL
    }

    @Test fun mockScenariosForTimeoutAndStaleData(): Unit = runBlocking {
        val s = service()
        assertEquals(503, assertFailsWith<EarningsRequestException> { s.resultsFor("SSRV:2026-Q3", "provider-timeout") }.status)
        assertEquals(DataFreshness.STALE, s.resultsFor("SSRV:2026-Q3", "stale-cache").freshness)
    }

    @Test fun revisedFiguresReplaceTheCachedVersionOnRefresh(): Unit = runBlocking {
        var revised = false
        val source = object : EarningsDataSource {
            val inner = FixtureEarningsDataSource()
            override val label = "test"
            override suspend fun calendar(from: LocalDate, to: LocalDate) = inner.calendar(from, to)
            override suspend fun history(symbol: String) = inner.history(symbol).map { e ->
                if (revised && e.id == "KO:2026-Q2") e.copy(actual = e.actual!!.copy(eps = 0.99, restated = true)) else e
            }
        }
        var millis = 0L
        val s = service(source, cache = CompanyFinancialCache(512, now = { millis }))
        val first = s.resultsFor("KO:2026-Q2")
        assertFalse(first.report.revised)
        revised = true; millis += 21_600_001                                                   // history cache expires
        val second = s.resultsFor("KO:2026-Q2")
        assertTrue(second.report.revised); assertEquals("0.99", second.insights.eps.actual)
        assertNotEquals(first.insights.eps, second.insights.eps)
    }

    @Test fun routesServeReportsInsightsLatestAndList() = testApplication {
        val s = service()
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing { earningsRoutes(s, MockUserAuthenticator(), RequestRateLimiter(1_000)) }
        }
        val report = client.get("/api/v1/earnings/reports/SSRV%3A2026-Q3")
        assertEquals(HttpStatusCode.OK, report.status)
        assertEquals("0.25", json.decodeFromString(EarningsResultsResponse.serializer(), report.bodyAsText()).insights.eps.surpriseAmount)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/reports/SSRV%3A2026-Q3/insights").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/company/SSRV/latest").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/company/SSRV/reports").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/earnings/company/GOOGL/latest").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/earnings/reports/AAPL%3A2026-Q4").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/earnings/reports/garbage").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/earnings/reports/SSRV%3A2026-Q3?scenario=provider-timeout").status)
        // Phase 1 routes still work.
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/calendar?from=2026-10-05&to=2026-10-11").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/company/AAPL/next").status)
    }

    @Test fun rateLimitApplies() = testApplication {
        val s = service()
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing { earningsRoutes(s, MockUserAuthenticator(), RequestRateLimiter(1)) }
        }
        client.get("/api/v1/earnings/reports/SSRV%3A2026-Q3")
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/earnings/reports/SSRV%3A2026-Q3").status)
    }
}
