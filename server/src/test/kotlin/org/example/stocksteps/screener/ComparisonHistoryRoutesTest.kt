package org.example.stocksteps.screener

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.portfolio.analytics.DebugEntitlementRequest
import org.example.stocksteps.portfolio.analytics.SubscriptionTier
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.CompanyFinancialService
import org.example.stocksteps.service.StockService
import org.example.stocksteps.userdata.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Company Comparison Phase 3 over the MOCK fixtures: free 1Y quarterly history, StockSteps+ 3Y/5Y and
 * advanced metrics, and server-side entitlement enforcement exercised by calling the routes directly.
 * Fixtures only — no FMP, Finnhub, Gemini or Firebase.
 */
class ComparisonHistoryRoutesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val fixture = FixtureMarketDataSource(sampleFallback = true)
    private val financials = CompanyFinancialService(fixture)
    private val store = InMemoryUserDataStore()
    private val entitlements = EntitlementService(store, clock::millis, debugAllowed = true)
    private val loads = AtomicInteger()

    private fun service(fail: Set<String> = emptySet(), slow: Set<String> = emptySet()) = ComparisonHistoryService(StockService(fixture, fixture), { symbol, period ->
        loads.incrementAndGet()
        if (symbol in fail) throw IllegalStateException("provider down")
        if (symbol in slow) delay(5_000)
        financials.getFundamentals(symbol, period)
    }, entitlements, clock, sampleData = true, source = "Sample fixture financial statements (MOCK)", timeoutMillis = 500)

    private fun ApplicationTestBuilder.install(service: ComparisonHistoryService = service()) = application {
        configureApiErrors()
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        routing { comparisonHistoryRoutes(service, MockUserAuthenticator(), RequestRateLimiter(1_000)) }
    }

    private suspend fun plan(uid: String, tier: SubscriptionTier, expired: Boolean = false, state: String? = null) {
        // The "unavailable" simulation stores the record and then (correctly) can't be read back.
        try { entitlements.simulate(uid, DebugEntitlementRequest(tier, expired, state)) } catch (_: UserDataException) {}
    }

    private suspend fun ApplicationTestBuilder.me(uid: String?, query: String, extra: Map<String, String> = emptyMap()): HttpResponse =
        client.get("/api/v1/me/compare/history?$query") { uid?.let { header("Authorization", "Bearer mock-user:$it") }; extra.forEach { (k, v) -> header(k, v) } }
    private suspend fun HttpResponse.history() = json.decodeFromString(HistoricalComparison.serializer(), bodyAsText())
    private suspend fun HttpResponse.error() = json.decodeFromString(ApiError.serializer(), bodyAsText())

    // ---------- Free 1Y ----------

    @Test fun freeOneYearForGuestsAndFreeAccounts() = testApplication {
        install()
        val guest = client.get("/api/v1/compare/history?symbols=AAPL,MSFT&range=1Y")
        assertEquals(HttpStatusCode.OK, guest.status)
        val g = guest.history()
        assertEquals(HistoryRange.ONE_YEAR, g.range); assertEquals(HistoryGranularity.QUARTERLY, g.granularity)
        assertEquals(listOf(HistoryMetric.REVENUE, HistoryMetric.NET_INCOME, HistoryMetric.EPS_DILUTED), g.metrics.map { it.metric })
        assertFalse(g.access.plus); assertFalse(g.access.signedIn)
        assertTrue(g.metrics.all { m -> m.series.all { it.points.size == 4 && it.points.all { p -> p.fiscalPeriod in setOf("Q1", "Q2", "Q3", "Q4") } } })
        assertTrue(g.sampleData && g.notes.any { it.startsWith("Sample fixture data") })
        assertTrue(g.insights.any { it.contains("different months (Apple Inc.: Sep; Microsoft Corporation: Jun)") })   // different fiscal calendars
        // Default range is 1Y; a free account gets the same free content, signed in.
        plan("free", SubscriptionTier.FREE)
        val f = me("free", "symbols=AAPL,MSFT").history()
        assertTrue(f.access.signedIn && !f.access.plus)
        assertEquals(g.metrics, f.metrics)
    }

    // ---------- Entitlement enforcement (direct API calls) ----------

    @Test fun premiumRangesAreRejectedBeforeAnyProviderCall() = testApplication {
        install()
        plan("free", SubscriptionTier.FREE)
        loads.set(0)
        for (range in listOf("3Y", "5Y")) {
            val response = me("free", "symbols=AAPL,MSFT&range=$range")
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals("PLUS_REQUIRED", response.error().code)
            val publicCall = client.get("/api/v1/compare/history?symbols=AAPL,MSFT&range=$range")
            assertEquals(HttpStatusCode.Unauthorized, publicCall.status)
            assertEquals("SIGN_IN_REQUIRED", publicCall.error().code)
            assertEquals(HttpStatusCode.Unauthorized, me(null, "symbols=AAPL,MSFT&range=$range").status)            // no token
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/compare/history?symbols=AAPL,MSFT&range=$range") { header("Authorization", "Bearer not-a-token") }.status)
        }
        assertEquals(0, loads.get())                                                                                  // no statements loaded for rejected requests
    }

    @Test fun clientSuppliedPlanFlagsAreIgnored() = testApplication {
        install()
        plan("free", SubscriptionTier.FREE)
        val spoofed = me("free", "symbols=AAPL,MSFT&range=5Y&plus=true&tier=PLUS&premium=1", mapOf("X-StockSteps-Plan" to "PLUS", "X-Entitlement" to "plus"))
        assertEquals(HttpStatusCode.Forbidden, spoofed.status)
        val publicSpoof = client.get("/api/v1/compare/history?symbols=AAPL,MSFT&range=1Y&plus=true") { header("Authorization", "Bearer mock-user:plus-user") }
        assertEquals(listOf(HistoryMetric.REVENUE, HistoryMetric.NET_INCOME, HistoryMetric.EPS_DILUTED), publicSpoof.history().metrics.map { it.metric })  // public route never reads a token
    }

    @Test fun stockStepsPlusGetsThreeAndFiveYearsAndAdvancedMetrics() = testApplication {
        install()
        plan("plus", SubscriptionTier.PLUS)
        val five = me("plus", "symbols=AAPL,MSFT,KO&range=5Y").history()
        assertEquals(HistoryGranularity.ANNUAL, five.granularity)
        assertEquals(HistoryMetric.entries, five.metrics.map { it.metric })
        assertTrue(five.access.plus && five.access.ranges == HistoryRange.entries)
        assertTrue(five.metrics.all { m -> m.series.all { s -> s.points.size == 5 && s.points.all { it.fiscalPeriod == "FY" } } })
        val margin = five.metric(HistoryMetric.NET_MARGIN)!!.series.first { it.symbol == "KO" }.points.last()
        val revenue = five.metric(HistoryMetric.REVENUE)!!.series.first { it.symbol == "KO" }.points.last().value!!
        val income = five.metric(HistoryMetric.NET_INCOME)!!.series.first { it.symbol == "KO" }.points.last().value!!
        assertEquals(income / revenue * 100, margin.value!!, 1e-9)                                                 // formula over the same period
        assertTrue(five.metric(HistoryMetric.REVENUE_INDEX)!!.series.all { it.points.first().value == 100.0 })
        assertEquals(3, me("plus", "symbols=RY.TO,CNR.TO&range=3Y").history().periods.size)                           // two Canadian companies, 3Y
        assertTrue(me("plus", "symbols=AAPL,MSFT&range=1Y").history().metrics.any { it.metric == HistoryMetric.REVENUE_GROWTH })  // advanced metrics on 1Y too
    }

    @Test fun entitlementStatesFollowTheExistingPolicy() = testApplication {
        install()
        suspend fun status(uid: String) = me(uid, "symbols=AAPL,MSFT&range=3Y").status
        plan("expired", SubscriptionTier.PLUS, expired = true); assertEquals(HttpStatusCode.Forbidden, status("expired"))
        plan("failed", SubscriptionTier.PLUS, state = "payment-failed"); assertEquals(HttpStatusCode.Forbidden, status("failed"))
        plan("canceled", SubscriptionTier.PLUS, state = "canceled"); assertEquals(HttpStatusCode.OK, status("canceled"))       // paid until the period ends
        plan("grace", SubscriptionTier.PLUS, state = "grace"); assertEquals(HttpStatusCode.OK, status("grace"))
        assertEquals(HttpStatusCode.Forbidden, status("never-subscribed"))
        // Entitlement store unavailable: premium fails closed (503) without provider calls; 1Y still works as the free view.
        plan("down", SubscriptionTier.PLUS, state = "unavailable")
        loads.set(0)
        val premium = me("down", "symbols=AAPL,MSFT&range=5Y")
        assertEquals(HttpStatusCode.ServiceUnavailable, premium.status); assertEquals("ENTITLEMENT_UNAVAILABLE", premium.error().code)
        assertEquals(0, loads.get())
        val free = me("down", "symbols=AAPL,MSFT&range=1Y").history()
        assertFalse(free.access.plus); assertTrue(free.access.message!!.contains("can't be checked"))
        assertEquals(3, free.metrics.size)
        // Expiry downgrades immediately: the same user loses 3Y once the plan lapses (nothing cached per user).
        plan("lapsing", SubscriptionTier.PLUS); assertEquals(HttpStatusCode.OK, status("lapsing"))
        plan("lapsing", SubscriptionTier.PLUS, expired = true); assertEquals(HttpStatusCode.Forbidden, status("lapsing"))
    }

    @Test fun premiumContentIsNeverReusedForFreeCallers() = testApplication {
        install()
        plan("plus", SubscriptionTier.PLUS); plan("free", SubscriptionTier.FREE)
        val premium = me("plus", "symbols=AAPL,MSFT&range=1Y").history()
        assertEquals(7, premium.metrics.size)
        val free = me("free", "symbols=AAPL,MSFT&range=1Y")
        val text = free.bodyAsText()
        assertEquals(3, json.decodeFromString(HistoricalComparison.serializer(), text).metrics.size)
        for (premiumOnly in listOf("REVENUE_GROWTH", "NET_MARGIN", "EPS_GROWTH", "REVENUE_INDEX", "changed by")) assertFalse(text.contains(premiumOnly), premiumOnly)
        assertFalse(client.get("/api/v1/compare/history?symbols=AAPL,MSFT").bodyAsText().contains("REVENUE_INDEX"))
    }

    // ---------- Validation and provider failures ----------

    @Test fun invalidRequestsUseStableErrors() = testApplication {
        install()
        plan("plus", SubscriptionTier.PLUS)
        suspend fun code(query: String) = client.get("/api/v1/compare/history?$query").let { it.status to it.error().code }
        assertEquals(HttpStatusCode.BadRequest to "INVALID_HISTORY_RANGE", code("symbols=AAPL,MSFT&range=10Y"))
        assertEquals(HttpStatusCode.BadRequest to "INVALID_COMPARISON", code("symbols=AAPL"))
        assertEquals(HttpStatusCode.BadRequest to "INVALID_COMPARISON", code("symbols=AAPL,aapl"))
        assertEquals(HttpStatusCode.BadRequest to "INVALID_SYMBOL", code("symbols=AAPL,%24%24"))
        assertEquals(HttpStatusCode.BadRequest, me("plus", "symbols=AAPL,MSFT&range=7Y").status)
    }

    @Test fun partialFailuresTimeoutsAndCompanyCoverage() = testApplication {
        install(service(fail = setOf("MSFT"), slow = setOf("TSLA")))
        plan("plus", SubscriptionTier.PLUS)
        val partial = me("plus", "symbols=AAPL,MSFT,TSLA&range=1Y").history()
        assertEquals(listOf(null, "financial history isn't available right now", "the financial data provider didn't respond in time"), partial.companies.map { it.error })
        assertTrue(partial.metric(HistoryMetric.REVENUE)!!.series.first { it.symbol == "AAPL" }.points.all { it.value != null })
        assertTrue(partial.metric(HistoryMetric.REVENUE)!!.series.first { it.symbol == "MSFT" }.points.isEmpty())
        val all = me("plus", "symbols=MSFT,TSLA&range=1Y")
        assertEquals(HttpStatusCode.ServiceUnavailable, all.status); assertEquals("HISTORICAL_DATA_UNAVAILABLE", all.error().code)
    }

    // ---------- MOCK financial scenarios ----------

    @Test fun mockScenariosCoverEdgeCasesWithoutFabrication() = testApplication {
        install()
        plan("plus", SubscriptionTier.PLUS)
        // Fictional SSHC.TO: missing quarter, missing diluted EPS, zero-revenue baseline, currency change, losses turning to a profit.
        val q = me("plus", "symbols=TD,SSHC.TO&range=1Y").history()
        fun points(metric: HistoryMetric, symbol: String) = q.metric(metric)!!.series.first { it.symbol == symbol }.points
        assertEquals(listOf("Q2 FY2026", "Q3 FY2026", "Q4 FY2026", "Q1 FY2027"), points(HistoryMetric.REVENUE, "SSHC.TO").map { it.label })
        assertNull(points(HistoryMetric.REVENUE, "SSHC.TO")[1].value)                                          // missing quarter stays empty
        assertEquals(FinancialAvailability.MISSING, points(HistoryMetric.EPS_DILUTED, "SSHC.TO")[0].availability)  // missing diluted EPS
        assertEquals(FinancialAvailability.NON_POSITIVE_DENOMINATOR, points(HistoryMetric.REVENUE_GROWTH, "SSHC.TO")[3].availability)  // zero revenue a year earlier
        assertEquals(FinancialAvailability.PERIOD_MISMATCH, points(HistoryMetric.REVENUE_GROWTH, "SSHC.TO")[0].availability)  // USD → CAD
        assertEquals(FinancialAvailability.UNRELIABLE_COMPARISON, points(HistoryMetric.EPS_GROWTH, "SSHC.TO")[3].availability)  // negative EPS baseline
        assertTrue(q.metric(HistoryMetric.NET_INCOME)!!.insights.any { it.contains("changed from negative in Q4 FY2026 to positive in Q1 FY2027") })
        assertTrue(q.periods.any { it.alignment == PeriodAlignment.DIFFERENT })
        // Annual: improving margin, negative EPS baseline, revenue decline, currency change within one company.
        val y = me("plus", "symbols=RIVN,SSHC.TO,BB.TO&range=5Y").history()
        assertTrue(y.metric(HistoryMetric.NET_MARGIN)!!.insights.any { it.startsWith("StockSteps Sample History Co (fictional)'s net profit margin was higher in FY2026 than in FY2022") })
        assertTrue(y.metric(HistoryMetric.EPS_GROWTH)!!.series.first { it.symbol == "RIVN" }.points.all { it.value == null })
        assertTrue(y.metric(HistoryMetric.REVENUE)!!.insights.any { it.contains("more than one currency") })
        assertTrue(y.metric(HistoryMetric.REVENUE_GROWTH)!!.series.first { it.symbol == "BB.TO" }.points.any { (it.value ?: 0.0) < 0 })  // revenue decline
        // Three companies (KO's quarters are MOCK sample statements, labelled as sample data in the notes).
        val three = me("plus", "symbols=AAPL,MSFT,KO&range=1Y").history()
        assertEquals(listOf("AAPL", "MSFT", "KO"), three.metric(HistoryMetric.REVENUE)!!.series.map { it.symbol })
        assertTrue(three.metric(HistoryMetric.REVENUE)!!.series.all { it.points.size == 4 })
        // Mixed US/Canadian: currencies preserved and never converted.
        val mixed = me("plus", "symbols=AAPL,RY.TO&range=3Y").history()
        assertEquals(listOf("USD", "CAD"), mixed.companies.map { it.currency })
        assertTrue(mixed.insights.any { it.contains("aren't converted") })
        assertEquals(mixed, me("plus", "symbols=AAPL,RY.TO&range=3Y").history().copy(asOf = mixed.asOf))      // deterministic
    }

    @Test fun statementsAreCachedPerSymbolAndFrequency() = testApplication {
        install()
        plan("plus", SubscriptionTier.PLUS)
        loads.set(0)
        me("plus", "symbols=AAPL,MSFT&range=3Y"); me("plus", "symbols=AAPL,MSFT&range=5Y"); client.get("/api/v1/compare/history?symbols=AAPL,MSFT")
        assertEquals(4, loads.get())                                                                                  // AAPL+MSFT annual once, quarterly once
    }
}
