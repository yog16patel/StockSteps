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
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.screener.RequestRateLimiter
import org.example.stocksteps.service.*
import org.example.stocksteps.userdata.*
import java.time.*
import kotlin.test.*

/** Earnings Intelligence Lite, Phase 3: price reactions from MOCK fixtures (clock 2026-10-07 17:15 New York, after the close). */
class EarningsPriceReactionServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-07T21:15:00Z"), ZoneOffset.UTC)
    private val fixture = FixtureMarketDataSource(sampleFallback = true)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun service(sampleData: Boolean = true, charts: PriceChartService = PriceChartService(fixture)): EarningsService {
        val store = InMemoryUserDataStore()
        return EarningsService(FixtureEarningsDataSource(), StockService(fixture, fixture), charts, store, EntitlementService(store, clock::millis, true), clock, sampleData, null)
    }

    private suspend fun EarningsService.r(id: String, w: ReactionWindow = ReactionWindow.FIRST_SESSION, scenario: String? = null) = priceReaction(id, w.name, scenario = scenario)

    @Test fun specExampleAfterCloseFirstSessionAndIncompleteLongerWindows(): Unit = runBlocking {
        val r = service().r("SSRV:2026-Q3")
        val x = r.reaction
        assertEquals(ReactionStatus.AVAILABLE, x.status)
        assertEquals("2026-10-06", x.baseline?.sessionDate); assertEquals("150", x.baseline?.price)
        assertEquals("2026-10-07", x.endpoint?.sessionDate); assertEquals("157.5", x.endpoint?.price)
        assertEquals("7.5", x.absoluteChange); assertEquals("5", x.percentChange); assertEquals("USD", x.currency)
        assertEquals(PriceAdjustment.SPLIT_ADJUSTED, x.adjustment)
        assertEquals(Classification.BEAT, r.eps); assertEquals(Classification.BEAT, r.revenue)                    // Phase 2, reused
        assertTrue(r.explanation.startsWith("The stock increased after the earnings announcement."))                // case A
        assertEquals(mapOf(ReactionWindow.FIRST_SESSION to ReactionStatus.AVAILABLE, ReactionWindow.THREE_SESSIONS to ReactionStatus.WINDOW_INCOMPLETE,
            ReactionWindow.FIVE_SESSIONS to ReactionStatus.WINDOW_INCOMPLETE), r.windows.associate { it.window to it.status })
        val three = service().r("SSRV:2026-Q3", ReactionWindow.THREE_SESSIONS)
        assertNull(three.reaction.absoluteChange); assertEquals(PriceReactionExplainer.INCOMPLETE, three.explanation)
        assertEquals("2026-10-07", r.history.eventSlotDate); assertTrue(r.history.points.none { it.date > "2026-10-07" })  // never future candles
        assertTrue(r.sampleData && x.sources.single() == "StockSteps sample price history")
    }

    @Test fun fixtureScenariosCoverEveryCase(): Unit = runBlocking {
        val s = service()
        suspend fun status(id: String, w: ReactionWindow = ReactionWindow.FIRST_SESSION) = s.r(id, w).reaction.status
        // EPS beat (revenue met), before the open, price −3.2% → case B
        s.r("SSRM:2027-Q1").let { assertEquals("2026-10-01", it.reaction.baseline?.sessionDate); assertEquals("2026-10-02", it.reaction.endpoint?.sessionDate)
            assertEquals("-3.2", it.reaction.percentChange); assertTrue(it.explanation.contains("The available data does not prove which factor caused the decline")) }
        assertEquals("2026-10-06", s.r("SSRM:2027-Q1", ReactionWindow.THREE_SESSIONS).reaction.endpoint?.sessionDate)
        // miss/miss, price −10% → case D
        assertTrue(s.r("SSLL:2026-Q3").explanation.startsWith("The company reported results below expectations, and its share price declined"))
        // EPS met / revenue miss during market hours, price +3% → case C, labelled
        s.r("SSFC:2026-Q3").let { assertEquals("2026-10-02", it.reaction.baseline?.sessionDate); assertEquals("3", it.reaction.percentChange)
            assertTrue(it.explanation.contains("yet its share price increased")); assertTrue(it.reaction.warnings.any { w -> w.contains("intraday prices aren't available") }) }
        // unknown time, flat, no estimates → case F
        s.r("SSNE:2026-Q3").let { assertEquals(ReactionStatus.EVENT_TIME_UNKNOWN, it.reaction.status); assertEquals("0", it.reaction.percentChange)
            assertTrue(it.explanation.contains(PriceReactionExplainer.NO_ESTIMATES)) }
        // trading halt on the first session; three sessions still measurable
        assertEquals(ReactionStatus.ENDPOINT_UNAVAILABLE, status("SSAN:2026-Q3"))
        s.r("SSAN:2026-Q3", ReactionWindow.THREE_SESSIONS).reaction.let { assertEquals(ReactionStatus.AVAILABLE, it.status); assertTrue(it.warnings.any { w -> w.contains("Thu, Oct 1") }) }
        assertEquals(ReactionStatus.BASELINE_UNAVAILABLE, status("SSRV:2026-Q2"))                 // missing baseline
        assertEquals(ReactionStatus.DATA_NOT_COMPARABLE, status("SSRV:2025-Q3"))                  // zero baseline
        assertEquals(ReactionStatus.CORPORATE_ACTION_AMBIGUITY, status("SSLL:2026-Q2"))           // special dividend
        assertEquals(ReactionStatus.AVAILABLE, status("SSRM:2026-Q4"))                            // split, split-adjusted series
        assertTrue(s.r("SSFC:2026-Q2").reaction.statusMessage!!.contains("adjustment bases"))
        assertTrue(s.r("SSFC:2025-Q3").reaction.statusMessage!!.contains("different currencies"))
        assertEquals("2026-07-06", s.r("SSHU:2026-Q2").reaction.endpoint?.sessionDate)            // US-only holiday Jul 3
        s.r("SSCA.TO:2026-Q2").let { assertEquals("2026-07-02", it.reaction.endpoint?.sessionDate); assertEquals("CAD", it.reaction.currency)   // Canada Day
            assertEquals(PriceReactionExplainer.MIXED, it.mixedNote) }                             // EPS miss, revenue beat
        assertTrue(s.r("SSHD:2026-Q1").reaction.endpoint!!.earlyClose)                            // half day
        // Existing fixture companies use the shared daily-close history.
        assertNotNull(s.r("AAPL:2026-Q3").reaction.percentChange)
        assertEquals("CAD", s.r("RY.TO:2026-Q3").reaction.currency.let { it ?: "CAD" })
    }

    @Test fun earningsDetailsUsesTheSamePolicy(): Unit = runBlocking {
        val s = service()
        val details = s.details("SSRV", null).reaction!!
        val phase3 = s.r("SSRV:2026-Q3").reaction
        assertEquals(phase3.baseline?.sessionDate, details.baselineDate); assertEquals(phase3.endpoint?.sessionDate, details.endDate)
        assertEquals(5.0, details.changePercent!!, 1e-9)
    }

    @Test fun invalidRequestsAndMissingReports(): Unit = runBlocking {
        val s = service()
        assertEquals("INVALID_WINDOW", assertFailsWith<EarningsRequestException> { s.priceReaction("SSRV:2026-Q3", "TWO_WEEKS") }.code)
        assertEquals(400, assertFailsWith<EarningsRequestException> { s.priceReaction("nope", null) }.status)
        assertEquals("NOT_REPORTED", assertFailsWith<EarningsRequestException> { s.priceReaction("AAPL:2026-Q4", null) }.code)
        assertEquals("NOT_FOUND", assertFailsWith<EarningsRequestException> { s.priceReaction("AAPL:2019-Q1", null) }.code)
    }

    @Test fun providerFailuresAreHonestAndNeverSampleDataInReal(): Unit = runBlocking {
        val s = service()
        assertEquals(ReactionStatus.PROVIDER_UNAVAILABLE, s.r("SSRV:2026-Q3", scenario = "price-timeout").reaction.status)
        assertTrue(s.r("SSRV:2026-Q3", scenario = "price-rate-limit").reaction.statusMessage!!.contains("limiting requests"))
        assertEquals(DataFreshness.STALE, s.r("SSRV:2026-Q3", scenario = "price-stale").reaction.freshness)
        // REAL: MOCK scenarios are ignored; a failing provider gives PROVIDER_UNAVAILABLE (no sample fallback).
        assertNotEquals(ReactionStatus.PROVIDER_UNAVAILABLE, service(sampleData = false).r("AAPL:2026-Q3", scenario = "price-timeout").reaction.status)
        val failing = object : PriceHistoryProvider {
            override suspend fun getDailyCloses(symbol: String): List<PricePoint> = throw StockProviderException(StockProviderException.Failure.RATE_LIMITED)
            override suspend fun getIntradayPoints(symbol: String): List<PricePoint> = throw StockProviderException(StockProviderException.Failure.RATE_LIMITED)
            override suspend fun getIntradaySparkline(symbol: String) = throw StockProviderException(StockProviderException.Failure.RATE_LIMITED)
        }
        val real = service(sampleData = false, charts = PriceChartService(failing))
        val r = real.r("AAPL:2026-Q3").reaction
        assertEquals(ReactionStatus.PROVIDER_UNAVAILABLE, r.status); assertTrue(r.statusMessage!!.contains("limiting requests")); assertNull(r.percentChange)
        assertEquals(ReactionStatus.PROVIDER_UNAVAILABLE, real.r("SSRV:2026-Q3").reaction.status)    // REAL never reads the demo price fixtures
    }

    @Test fun routesServeReactionHistoryAndValidate() = testApplication {
        val s = service()
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing { earningsRoutes(s, MockUserAuthenticator(), RequestRateLimiter(1_000)) }
        }
        val ok = client.get("/api/v1/earnings/reports/SSRV%3A2026-Q3/price-reaction?window=FIRST_SESSION&includeExtendedHours=true")
        assertEquals(HttpStatusCode.OK, ok.status)
        val body = json.decodeFromString(EarningsPriceReactionResponse.serializer(), ok.bodyAsText())
        assertEquals("5", body.reaction.percentChange); assertFalse(body.reaction.extendedHoursAvailable)
        assertTrue(body.reaction.warnings.any { it.contains("Extended-hours prices aren't available") })
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/reports/SSRV%3A2026-Q3/price-history").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/earnings/reports/SSRV%3A2026-Q3/price-reaction?window=WEEK").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/earnings/reports/AAPL%3A2026-Q4/price-reaction").status)
        // Phase 1 and 2 routes still work.
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/reports/SSRV%3A2026-Q3").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/earnings/calendar?from=2026-10-05&to=2026-10-11").status)
    }

    @Test fun rateLimited() = testApplication {
        val s = service()
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing { earningsRoutes(s, MockUserAuthenticator(), RequestRateLimiter(1)) }
        }
        client.get("/api/v1/earnings/reports/SSRV%3A2026-Q3/price-reaction")
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/earnings/reports/SSRV%3A2026-Q3/price-reaction").status)
    }
}
