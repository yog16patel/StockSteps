package org.example.stocksteps.security

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import org.example.stocksteps.companyFinancialRoutes
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.service.*
import org.example.stocksteps.userdata.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/**
 * Phase 4A: identity (verified uid, trusted XFF only when configured), cost-weighted admission with Retry-After, request-size guards,
 * App Check monitor/enforce, per-job internal authentication, the symbol existence gate and watch-data limits. Ktor test host + MockEngine.
 */
class PublicApiProtectionTest {
    private val auth = MockUserAuthenticator()

    // ---------- Identity ----------

    @Test fun trustedClientIpComesOnlyFromTheConfiguredEndOfXForwardedFor() {
        assertEquals("203.0.113.7", ClientIdentityResolver.clientIp(listOf("6.6.6.6, 203.0.113.7"), 1), "a forged leading entry is ignored")
        assertEquals("203.0.113.7", ClientIdentityResolver.clientIp(listOf("6.6.6.6", "203.0.113.7, 130.211.0.1"), 2), "load balancer: second from the right")
        assertNull(ClientIdentityResolver.clientIp(listOf("203.0.113.7"), 2), "too few hops → not trusted")
        assertNull(ClientIdentityResolver.clientIp(listOf("evil.example.com"), 1), "host names aren't IPs (no DNS)")
        assertNull(ClientIdentityResolver.clientIp(emptyList(), 1))
        assertEquals("203.0.113.7", ClientIdentityResolver.clientIp(listOf("203.0.113.7:51234"), 1))
        assertEquals(ClientIdentityResolver.clientIp(listOf("2001:db8:1:2:aaaa::1"), 1), ClientIdentityResolver.clientIp(listOf("2001:db8:1:2:bbbb::9"), 1),
            "IPv6 addresses in one /64 share a bucket")
    }

    private fun probe(hops: Int?, controller: AdmissionController = AdmissionController(), appCheck: AppCheckGuard? = null,
                      body: suspend io.ktor.server.routing.RoutingContext.() -> Unit = { call.respondText(call.clientIdentity().toString()) }) = testApplication {
        application {
            configureApiErrors()
            install(ContentNegotiation) { json() }
            installAdmission(ClientIdentityResolver(auth, hops), controller, appCheck)
            routing {
                get("/api/v1/stocks/{symbol}/details") { body() }
                post("/api/v1/screener/search") { call.respondText("ok") }
                get("/internal/ping") { call.respondText("internal") }
            }
        }
        probeChecks?.invoke(this)
    }
    private var probeChecks: (suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit)? = null
    private fun withProbe(hops: Int?, controller: AdmissionController = AdmissionController(), appCheck: AppCheckGuard? = null,
                          body: suspend io.ktor.server.routing.RoutingContext.() -> Unit = { call.respondText(call.clientIdentity().toString()) },
                          checks: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) {
        probeChecks = checks
        probe(hops, controller, appCheck, body)
    }

    @Test fun unverifiedTopologyNeverTrustsForwardedHeaders() = withProbe(hops = null) {
        assertEquals("Unverified", client.get("/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", "1.2.3.4") }.bodyAsText())
        assertEquals("User(uid=alice)", client.get("/api/v1/stocks/AAPL/details") { header("Authorization", "Bearer mock-user:alice") }.bodyAsText())
    }

    @Test fun verifiedTopologyUsesTheTrustedAddressAndSpoofingDoesNotChangeIt() = withProbe(hops = 1) {
        val a = client.get("/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", "203.0.113.7") }.bodyAsText()
        val spoofed = client.get("/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", "9.9.9.9, 203.0.113.7") }.bodyAsText()
        assertEquals("Address(address=203.0.113.7)", a)
        assertEquals(a, spoofed)
        assertEquals("Unverified", client.get("/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", "not-an-ip") }.bodyAsText())
    }

    // ---------- Weighted admission ----------

    @Test fun cachedRequestsCostOneUnitAndColdOnesCostTheirUpstreamCalls() {
        val time = AtomicLong(0)
        val meter = ProviderUsageMeter()
        val controller = AdmissionController(AdmissionPolicy.DEFAULTS + (RouteGroup.MARKET_DATA to AdmissionPolicy(40, 100, 16, 256, 1_000)), { time.get() }, meter = meter)
        var upstream = 0
        withProbe(hops = 1, controller = controller, body = { repeat(upstream) { RequestCost.add("fmp") }; call.respondText("ok") }) {
            suspend fun get(ip: String) = client.get("/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", ip) }
            upstream = 0
            repeat(40) { assertEquals(HttpStatusCode.OK, get("203.0.113.7").status) }          // cache hits: 1 unit each
            val denied = get("203.0.113.7")
            assertEquals(HttpStatusCode.TooManyRequests, denied.status)
            assertTrue(denied.bodyAsText().contains("RATE_LIMITED"))
            assertTrue((denied.headers[HttpHeaders.RetryAfter]?.toLong() ?: 0) in 1..60, "Retry-After tells the client when to retry")
            assertEquals(HttpStatusCode.OK, get("198.51.100.1").status, "another client has its own budget")
            upstream = 16                                                                          // a cold Company Details
            assertEquals(HttpStatusCode.OK, get("198.51.100.2").status); assertEquals(HttpStatusCode.OK, get("198.51.100.2").status)
            assertEquals(HttpStatusCode.OK, get("198.51.100.2").status)
            assertEquals(HttpStatusCode.TooManyRequests, get("198.51.100.2").status, "3 cold loads (51 units) exhaust a 40-unit budget")
            time.addAndGet(60_000)
            assertEquals(HttpStatusCode.OK, get("203.0.113.7").status, "the window rolls over")
            assertTrue(meter.eventCount("admission.market-data.denied") >= 2)
        }
    }

    @Test fun unverifiedCallersShareALargePoolThatCheapFloodsCannotExhaust() {
        val controller = AdmissionController(AdmissionPolicy.DEFAULTS + (RouteGroup.MARKET_DATA to AdmissionPolicy(5, 50, 16, 256, 1_000)), { 0L })
        var upstream = 0
        withProbe(hops = null, controller = controller, body = { repeat(upstream) { RequestCost.add("fmp") }; call.respondText("ok") }) {
            // A script flooding cached requests (no upstream work) never drains the guest pool, so other guests keep working.
            assertTrue((1..500).all { client.get("/api/v1/stocks/AAPL/details").status == HttpStatusCode.OK }, "cache hits are free in the anonymous pool")
            // Requests that cause provider calls are bounded in aggregate (50 upstream units per minute here).
            upstream = 10
            val statuses = (1..10).map { client.get("/api/v1/stocks/AAPL/details").status }
            assertEquals(5, statuses.count { it == HttpStatusCode.OK }, "5 cold requests × 10 upstream calls fill the pool")
            assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
            // Known limitation without a trusted client IP: once the pool is full, other *guests* wait for the minute window too.
            upstream = 0
            assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/stocks/AAPL/details").status)
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/AAPL/details") { header("Authorization", "Bearer mock-user:bob") }.status,
                "signed-in users have their own budgets")
        }
    }

    @Test fun signedInLimitsAreDistinctPerAccount() {
        val controller = AdmissionController(AdmissionPolicy.DEFAULTS + (RouteGroup.MARKET_DATA to AdmissionPolicy(3, 1_000, 16, 256, 1_000)), { 0L })
        withProbe(hops = 1, controller = controller, body = { call.respondText("ok") }) {
            suspend fun get(uid: String) = client.get("/api/v1/stocks/AAPL/details") { header("Authorization", "Bearer mock-user:$uid"); header("X-Forwarded-For", "203.0.113.7") }
            repeat(3) { assertEquals(HttpStatusCode.OK, get("carol").status) }
            assertEquals(HttpStatusCode.TooManyRequests, get("carol").status)
            // Several accounts from one IP each get their own per-instance budget; the provider budgets (4C) bound the total.
            assertEquals(HttpStatusCode.OK, get("dave").status)
        }
    }

    @Test fun inFlightRequestsPerIdentityAreBoundedAndStateIsBounded() = runBlocking {
        val controller = AdmissionController(maxTracked = 100)
        val id = ClientIdentity.Address("203.0.113.9")
        val tickets = (1..16).map { controller.enter(RouteGroup.MARKET_DATA, id) }
        assertTrue(tickets.all { it is AdmissionController.Decision.Admitted })
        assertIs<AdmissionController.Decision.Denied>(controller.enter(RouteGroup.MARKET_DATA, id), "17th concurrent request")
        tickets.forEach { controller.exit(it as AdmissionController.Decision.Admitted, 1) }
        repeat(1_000) { i -> controller.exit(controller.enter(RouteGroup.MARKET_DATA, ClientIdentity.Address("10.0.${i / 250}.${i % 250}")) as AdmissionController.Decision.Admitted, 1) }
        assertTrue(controller.tracked(RouteGroup.MARKET_DATA) <= 100, "limiter memory is bounded")
    }

    // ---------- Request size ----------

    @Test fun oversizedBodiesAndQueriesAreRejectedBeforeHandlers() = withProbe(hops = null) {
        assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/api/v1/screener/search") { contentType(ContentType.Application.Json); setBody("x".repeat(40_000)) }.status)
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/screener/search") { contentType(ContentType.Application.Json); setBody("{}") }.status)
        assertEquals(HttpStatusCode.fromValue(414), client.get("/api/v1/stocks/AAPL/details?q=" + "a".repeat(3_000)).status)
        assertEquals(HttpStatusCode.OK, client.get("/internal/ping").status, "internal routes aren't admission-controlled")
    }

    // ---------- App Check ----------

    @Test fun appCheckMonitorsByDefaultAndEnforcesOnlyWhenAsked() {
        val meter = ProviderUsageMeter()
        val verifier = AppCheckVerifier { it == "good-token" }
        withProbe(hops = null, appCheck = AppCheckGuard(verifier, enforce = false, meter = meter), body = { call.respondText("ok") }) {
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/AAPL/details").status, "missing token admitted in monitor mode")
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/AAPL/details") { header(AppCheckGuard.HEADER, "forged") }.status)
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/AAPL/details") { header(AppCheckGuard.HEADER, "good-token") }.status)
        }
        assertEquals(listOf(1L, 1L, 1L), listOf("missing", "invalid", "valid").map { meter.eventCount("appcheck.$it") })
        withProbe(hops = null, appCheck = AppCheckGuard(verifier, enforce = true, meter = meter), body = { call.respondText("ok") }) {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/stocks/AAPL/details").status)
            assertTrue(client.get("/api/v1/stocks/AAPL/details") { header(AppCheckGuard.HEADER, "forged") }.bodyAsText().contains("APP_CHECK_REQUIRED"))
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/AAPL/details") { header(AppCheckGuard.HEADER, "good-token") }.status)
        }
        assertFailsWith<IllegalArgumentException> { AppCheckGuard(null, enforce = true) }
    }

    // ---------- Internal jobs ----------

    @Test fun internalJobsUseTheirOwnSecretsInConstantTimeOrOidc() = runBlocking {
        val a = "a".repeat(32); val b = "b".repeat(32)
        val secrets = InternalJob.secrets(mapOf("ALERTS_EVALUATOR_TOKEN" to a, "EARNINGS_REMINDERS_TOKEN" to b, "DAILY_BRIEF_DISPATCH_TOKEN" to b, "USAGE_METRICS_TOKEN" to "short")::get)
        assertEquals(mapOf(InternalJob.ALERTS to a), secrets, "a secret shared by two jobs disables both; short secrets are ignored")
        assertTrue(InternalCallers.constantTimeEquals(a, a)); assertFalse(InternalCallers.constantTimeEquals(a, b))
        val store = InMemoryUserDataStore()
        val evaluator = AlertEvaluator(store, WatchMarketData(StockService(org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource(sampleFallback = false)), null,
            NewsService(org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource(sampleFallback = false), simplification = null)), SimulatedPushSender())
        testApplication {
            application { install(ContentNegotiation) { json() } }
            routing { alertEvaluationRoutes(evaluator, a, mock = false) }
            assertEquals(HttpStatusCode.Forbidden, client.post("/internal/alerts/evaluate").status, "no credentials")
            assertEquals(HttpStatusCode.Forbidden, client.post("/internal/alerts/evaluate") { header("X-StockSteps-Scheduler-Token", b) }.status, "another job's secret")
            assertEquals(HttpStatusCode.OK, client.post("/internal/alerts/evaluate") { header("X-StockSteps-Scheduler-Token", a) }.status)
        }
        testApplication {
            routing { alertEvaluationRoutes(evaluator, null, mock = false) }
            assertEquals(HttpStatusCode.NotFound, client.post("/internal/alerts/evaluate") { header("X-StockSteps-Scheduler-Token", a) }.status, "absent without configuration")
        }
        try {
            InternalCallers.configure({ token -> if (token == "scheduler-oidc") "scheduler@project.iam.gserviceaccount.com" else if (token == "other-oidc") "attacker@example.com" else null },
                "scheduler@project.iam.gserviceaccount.com")
            testApplication {
                application { install(ContentNegotiation) { json() } }
                routing { alertEvaluationRoutes(evaluator, null, mock = false) }
                assertEquals(HttpStatusCode.OK, client.post("/internal/alerts/evaluate") { header("Authorization", "Bearer scheduler-oidc") }.status)
                assertEquals(HttpStatusCode.Forbidden, client.post("/internal/alerts/evaluate") { header("Authorization", "Bearer other-oidc") }.status)
            }
        } finally { InternalCallers.configure(null, null) }
    }

    // ---------- Existence gate ----------

    @Test fun randomSymbolsCannotTriggerFinancialBundles() = testApplication {
        val up = FmpMock().apply { knownSymbols = setOf("AAPL") }
        val ms = AtomicLong(0)
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { ms.get() })
        val stocks = StockService(fmp, fmp)
        application { install(ContentNegotiation) { json() } }
        routing { companyFinancialRoutes(CompanyFinancialService(fmp), SymbolExistence(stocks, CompanyFinancialCache(capacity = 20_000, now = { ms.get() }))) }
        val symbols = (0 until 1_000).map { "Z${it.toString(36).uppercase().padStart(3, '0')}" }
        symbols.forEach { assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/stocks/$it/fundamentals").status) }
        val first = up.total
        assertTrue(first <= 2_000, "≤ 2 existence lookups (profile, quote) per unknown symbol, not 13–17: $first")
        assertEquals(0, up.n("income-statement") + up.n("ratios-ttm"), "no fundamentals requested for unknown symbols")
        symbols.take(200).forEach { client.get("/api/v1/stocks/$it/fundamentals") }
        assertEquals(first, up.total, "repeats are answered from the negative cache")
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/AAPL/fundamentals").status, "real symbols still work")
        assertTrue(client.get("/api/v1/stocks/Z00A/fundamentals").bodyAsText().contains("SYMBOL_NOT_FOUND"))
    }

    @Test fun providerOutagesAreNeverRememberedAsUnknownSymbols() = testApplication {
        val up = FmpMock()
        val ms = AtomicLong(0)
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { ms.get() })
        val stocks = StockService(fmp, fmp)
        val gate = SymbolExistence(stocks, CompanyFinancialCache(capacity = 100, now = { ms.get() }))
        up.status["profile"] = 503
        assertEquals(SymbolExistence.Status.UNKNOWN, gate.check("NEWCO"))
        up.status.clear(); ms.addAndGet(11 * 60_000L)
        // StockService keeps its own provider cooldown on profile failures; once it ends the symbol is found.
        assertNotEquals(SymbolExistence.Status.NOT_FOUND, gate.check("NEWCO"))
    }

    // ---------- Watch-data ----------

    private fun watchWorld(up: FmpMock) = org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl(up.client, "k").let { fmp ->
        val stocks = StockService(fmp, fmp)
        WatchDataService(WatchMarketData(stocks, null, NewsService(org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource(sampleFallback = false), simplification = null)),
            AlertRules(), UsMarketCalendar(), Clock.fixed(Instant.parse("2026-10-07T18:00:00Z"), ZoneOffset.UTC), "n")
    }

    @Test fun watchDataIsBoundedForAnonymousCallersAndConcurrencyIsLimited() {
        val up = FmpMock(latency = 20)
        val service = watchWorld(up)
        testApplication {
            application {
                configureApiErrors(); install(ContentNegotiation) { json() }
                installAdmission(ClientIdentityResolver(auth, null), AdmissionController())
                routing { watchDataRoutes(service, anonymousMaxSymbols = 30) }
            }
            fun symbols(n: Int) = (1..n).joinToString(",") { "S$it" }
            assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/watch-data?symbols=").status, "0 symbols")
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/watch-data?symbols=${symbols(1)}").status)
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/watch-data?symbols=${symbols(30)}").status)
            assertTrue(client.get("/api/v1/stocks/watch-data?symbols=${symbols(31)}").bodyAsText().contains("TOO_MANY_SYMBOLS"))
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/watch-data?symbols=${symbols(31)}") { header("Authorization", "Bearer mock-user:erin") }.status,
                "signed-in watchlists keep up to 100")
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/watch-data?symbols=${symbols(100)}") { header("Authorization", "Bearer mock-user:erin") }.status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/watch-data?symbols=${symbols(101)}") { header("Authorization", "Bearer mock-user:erin") }.status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/watch-data?symbols=AAPL,bad%20sym").status, "invalid symbols rejected before upstream")
            val dup = up.total
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/watch-data?symbols=DUP1,DUP1,DUP1").status)
            assertEquals(2, up.total - dup, "duplicates are looked up once (quote + profile)")
            up.failSymbols += "S2"
            val partial = client.get("/api/v1/stocks/watch-data?symbols=FAIL1,S2,FAIL3")
            assertEquals(HttpStatusCode.OK, partial.status, "one failing symbol doesn't fail the response")
        }
        assertTrue(up.peakConcurrency.get() <= 8, "≤ 8 symbols at a time per request (quote and profile are sequential per symbol): ${up.peakConcurrency.get()}")
    }
}
