package org.example.stocksteps.security

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.practice.*
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.service.EodSessions
import org.example.stocksteps.service.StockService
import org.example.stocksteps.stockRoutes
import org.example.stocksteps.userdata.*
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/**
 * Phase 9A addendum: back-to-back, concurrent and automated request abuse against the production security stack ([securedForTest]) with
 * the **default** limits ([RateLimitPolicy.DEFAULTS]) unless a test says otherwise. Each scenario is listed in
 * `docs/reviews/security_phase9a/RATE_LIMIT_ABUSE_TEST_RESULTS.md`.
 */
class RateLimitAbuseTest {
    private val auth = MockUserAuthenticator()      // "mock-user:<uid>" → uid
    private val clock = AtomicLong(1_760_000_000_000)
    private fun limiter(policies: Map<String, RateLimitPolicy> = RateLimitPolicy.DEFAULTS, maxTracked: Int = 20_000) =
        RateLimiter(policies, now = clock::get, maxTracked = maxTracked)

    private val ok: Routing.() -> Unit = {
        get("/api/v1/me/watchlists") { call.respondText("ok") }
        get("/api/v1/me/portfolio") { call.respondText("ok") }
        get("/api/v1/me/portfolio/accounts/{id}/summary") { call.respondText("ok") }
        get("/api/v1/me/practice") { call.respondText("ok") }
        get("/api/v1/stocks/{symbol}/details") { call.respondText("ok") }
        get("/api/v1/stocks/watch-data") { call.respondText("ok") }
        get("/api/v1/markets/overview") { call.respondText("ok") }
        get("/api/v1/daily-brief/latest") { call.respondText("ok") }
    }

    private suspend fun HttpClient.burst(n: Int, path: String, block: HttpRequestBuilder.() -> Unit = {}): List<HttpResponse> = (1..n).map { get(path, block) }
    private fun List<HttpResponse>.count(status: HttpStatusCode) = count { it.status == status }

    private suspend fun assertLimited(response: HttpResponse) {
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        val retry = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
        assertNotNull(retry, "429 carries Retry-After"); assertTrue(retry in 1..60, "Retry-After within the window: $retry")
        val body = response.bodyAsText()
        assertTrue(body.contains("RATE_LIMITED"))
        for (leak in listOf("120", "90", "per minute", "pool", "fmp", "finnhub", "apikey", "quota")) assertFalse(body.lowercase().contains(leak), "429 body reveals $leak")
    }

    @Test fun oneUserFiringRapidRequestsIsServedUpToTheLimitThenGetsRetryAfter() = testApplication {
        application { securedForTest(auth, limiter = limiter(), routes = ok) }
        val first = client.burst(100, "/api/v1/me/watchlists") { bearerAuth("mock-user:alice") }
        assertEquals(100, first.count(HttpStatusCode.OK), "100 rapid signed-in requests are under AUTHENTICATED_STANDARD (120/min)")
        val more = client.burst(30, "/api/v1/me/watchlists") { bearerAuth("mock-user:alice") }
        assertEquals(20, more.count(HttpStatusCode.OK)); assertEquals(10, more.count(HttpStatusCode.TooManyRequests))
        assertLimited(more.last())
    }

    @Test fun hundredRapidGuestRequestsFromOneAddressAreCappedAndSpoofingDoesNotReset() = testApplication {
        application { securedForTest(auth, hops = 1, limiter = limiter(), routes = ok) }
        val responses = client.burst(100, "/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", "203.0.113.7") }
        assertEquals(90, responses.count(HttpStatusCode.OK), "PUBLIC_MARKET_DATA guest limit 90/min")
        assertEquals(10, responses.count(HttpStatusCode.TooManyRequests)); assertLimited(responses.last())
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", "9.9.9.9, 203.0.113.7") }.status,
            "a forged leading X-Forwarded-For entry is not a new identity")
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", "203.0.113.7"); header("X-Device-Id", "fresh-device") }.status,
            "client device ids are never identities")
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", "198.51.100.4") }.status, "another address is unaffected")
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/markets/overview") { header("X-Forwarded-For", "203.0.113.7") }.status, "other classes keep their own budget")
    }

    @Test fun twoSignedInUsersHaveIndependentLimits() = testApplication {
        application { securedForTest(auth, limiter = limiter(), routes = ok) }
        client.burst(125, "/api/v1/me/portfolio") { bearerAuth("mock-user:alice") }
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/me/portfolio") { bearerAuth("mock-user:alice") }.status)
        assertEquals(120, client.burst(120, "/api/v1/me/portfolio") { bearerAuth("mock-user:bob") }.count(HttpStatusCode.OK), "Bob's budget is his own")
    }

    @Test fun usersSharingOneAddressAreLimitedPerAccountAndGuestsThereShareTheAddressBudget() = testApplication {
        application { securedForTest(auth, hops = 1, limiter = limiter(), routes = ok) }
        val nat = "198.51.100.77"
        assertEquals(90, client.burst(95, "/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", nat) }.count(HttpStatusCode.OK))
        for (user in listOf("alice", "bob", "carol"))
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", nat); bearerAuth("mock-user:$user") }.status,
                "signed-in $user behind the same NAT is limited by account, not by the exhausted guest address")
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/stocks/AAPL/details") { header("X-Forwarded-For", nat); bearerAuth("mock-user:not a valid uid") }.status,
            "an invalid token falls back to the address identity")
    }

    @Test fun concurrentExpensiveRequestsAreCappedPerUserAndRejectedNotQueued() = testApplication {
        val entered = AtomicInteger(); val release = CompletableDeferred<Unit>()
        application { securedForTest(auth, limiter = limiter()) {
            post("/api/v1/me/compare/ai/ask") { entered.incrementAndGet(); release.await(); call.respondText("answer") }
        } }
        coroutineScope {
            val running = (1..2).map { async { client.post("/api/v1/me/compare/ai/ask") { bearerAuth("mock-user:alice") } } }
            withTimeout(5_000) { while (entered.get() < 2) delay(5) }
            val third = client.post("/api/v1/me/compare/ai/ask") { bearerAuth("mock-user:alice") }
            assertLimited(third)
            assertEquals(1L, third.headers[HttpHeaders.RetryAfter]!!.toLong(), "an in-flight rejection asks for a short wait")
            assertEquals(2, entered.get(), "the third request never reached the handler (no queue)")
            val otherUser = async { client.post("/api/v1/me/compare/ai/ask") { bearerAuth("mock-user:bob") } }
            withTimeout(5_000) { while (entered.get() < 3) delay(5) }
            release.complete(Unit)
            running.awaitAll().forEach { assertEquals(HttpStatusCode.OK, it.status) }
            assertEquals(HttpStatusCode.OK, otherUser.await().status)
        }
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/me/compare/ai/ask") { bearerAuth("mock-user:alice") }.status, "slots free up after completion")
    }

    @Test fun expensiveOperationsHaveALowPerMinuteLimit() = testApplication {
        application { securedForTest(auth, limiter = limiter()) { post("/api/v1/me/compare/ai/ask") { call.respondText("answer") } } }
        val responses = (1..15).map { client.post("/api/v1/me/compare/ai/ask") { bearerAuth("mock-user:alice") } }
        assertEquals(12, responses.count(HttpStatusCode.OK)); assertLimited(responses.last())
    }

    @Test fun repeatedIdenticalSearchesShareOneProviderCallAndTheSearchBucketStopsFloods() = testApplication {
        val providerCalls = AtomicInteger()
        val provider = object : StockProviderRepository {
            override suspend fun getGainers() = emptyList<org.example.stocksteps.model.MarketMover>()
            override suspend fun getLosers() = emptyList<org.example.stocksteps.model.MarketMover>()
            override suspend fun getProfile(symbol: String): org.example.stocksteps.model.CompanyProfile? = null
            override suspend fun getQuote(symbol: String): org.example.stocksteps.model.StockQuote? = null
            override suspend fun searchStocks(query: String): List<StockSearchResult> {
                providerCalls.incrementAndGet(); delay(100)
                return listOf(StockSearchResult("AAPL", "Apple Inc.", "USD", "NASDAQ", "NASDAQ"))
            }
        }
        application { securedForTest(auth, hops = 1, limiter = limiter()) { stockRoutes(StockService(provider)) } }
        coroutineScope {
            (1..40).map { i -> async { client.get("/api/v1/stocks/search?query=apple") { bearerAuth("mock-user:user$i") } } }.awaitAll()
                .forEach { assertEquals(HttpStatusCode.OK, it.status) }
        }
        assertEquals(1, providerCalls.get(), "40 concurrent identical searches from 40 users: one provider call (single flight + cache)")
        val guest = client.burst(50, "/api/v1/stocks/search?query=apple") { header("X-Forwarded-For", "203.0.113.9") }
        assertEquals(45, guest.count(HttpStatusCode.OK), "search bucket: 45/min per guest address (5 slowly typed words)")
        assertLimited(guest.last())
        assertEquals(1, providerCalls.get(), "repeats are cache hits; rejected requests cost nothing upstream")
    }

    @Test fun rapidTabSwitchingBySignedInUserStaysWithinLimits() = testApplication {
        application { securedForTest(auth, limiter = limiter(), routes = ok) }
        // Worst case: every switch refetches every landing (the apps load once per screen and don't refetch on tab switches).
        var limited = 0
        repeat(25) {
            for (path in listOf("/api/v1/markets/overview", "/api/v1/daily-brief/latest", "/api/v1/stocks/watch-data?symbols=AAPL,MSFT",
                "/api/v1/me/watchlists", "/api/v1/me/portfolio", "/api/v1/me/portfolio/accounts/a1/summary", "/api/v1/me/practice")) {
                if (client.get(path) { bearerAuth("mock-user:alice") }.status == HttpStatusCode.TooManyRequests) limited++
            }
        }
        assertEquals(0, limited, "25 full tab cycles (175 requests) in one minute are never limited")
    }

    @Test fun providerQuotaExhaustionIsA503WithoutQuotaDetailsAndIsLogged() = testApplication {
        val lines = mutableListOf<String>(); SecurityLog.reset(); SecurityLog.sink = { synchronized(lines) { lines += it } }
        try {
            val provider = object : StockProviderRepository {
                override suspend fun getGainers() = emptyList<org.example.stocksteps.model.MarketMover>()
                override suspend fun getLosers() = emptyList<org.example.stocksteps.model.MarketMover>()
                override suspend fun getProfile(symbol: String): org.example.stocksteps.model.CompanyProfile? = null
                override suspend fun searchStocks(query: String) = emptyList<StockSearchResult>()
                override suspend fun getQuote(symbol: String): org.example.stocksteps.model.StockQuote? = when (symbol) {
                    "DAILY" -> throw StockProviderException(StockProviderException.Failure.RATE_LIMITED, dailyLimitReached = true)
                    "MINUTE" -> throw StockProviderException(StockProviderException.Failure.RATE_LIMITED, upstreamStatus = 429)
                    else -> throw StockProviderException(StockProviderException.Failure.UNAVAILABLE, upstreamStatus = 403)
                }
            }
            application { securedForTest(auth, limiter = limiter()) { stockRoutes(StockService(provider)) } }
            val daily = client.get("/api/v1/stocks/DAILY/quote")
            assertEquals(HttpStatusCode.ServiceUnavailable, daily.status); assertTrue(daily.bodyAsText().contains("PROVIDER_DAILY_LIMIT_REACHED"))
            val minute = client.get("/api/v1/stocks/MINUTE/quote")
            assertEquals(HttpStatusCode.ServiceUnavailable, minute.status); assertTrue(minute.bodyAsText().contains("PROVIDER_RATE_LIMITED"))
            assertEquals(HttpStatusCode.BadGateway, client.get("/api/v1/stocks/PLAN/quote").status)
            for (body in listOf(daily.bodyAsText(), minute.bodyAsText())) for (leak in listOf("apikey", "429", "limit=", "financialmodelingprep", "finnhub"))
                assertFalse(body.lowercase().contains(leak), "provider failure body leaks $leak")
            assertTrue(lines.any { "event=upstream_restricted reason=provider_daily_limit_reached" in it && "route=/api/v1/stocks/{symbol}/quote" in it }, lines.toString())
            assertTrue(lines.none { "DAILY" in it || "MINUTE" in it }, "raw symbols/paths are never logged")
        } finally { SecurityLog.sink = null }
    }

    @Test fun aLimitedCallerIsServedAgainAfterRetryAfter() = testApplication {
        application { securedForTest(auth, limiter = limiter(), routes = ok) }
        client.burst(120, "/api/v1/me/watchlists") { bearerAuth("mock-user:alice") }
        val limited = client.get("/api/v1/me/watchlists") { bearerAuth("mock-user:alice") }
        assertLimited(limited)
        val wait = limited.headers[HttpHeaders.RetryAfter]!!.toLong()
        clock.addAndGet((wait - 2) * 1_000)
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/me/watchlists") { bearerAuth("mock-user:alice") }.status, "still limited before Retry-After")
        clock.addAndGet(2_000)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/me/watchlists") { bearerAuth("mock-user:alice") }.status, "served once Retry-After has passed")
    }

    @Test fun practiceTradeRetriesWithOneIdempotencyKeyFillExactlyOnce() = testApplication {
        val store = InMemoryUserDataStore()
        val marketClock = MutableClock(Instant.parse("2026-10-16T22:00:00Z")); val now = MutableClock(Instant.parse("2026-10-17T15:00:00Z"))
        val market = FakeMarket().apply { history = mapOf("AAPL" to listOf(PricePoint("2026-10-15", 100.0), PricePoint("2026-10-16", 110.0))) }
        val practice = PracticeService(store, EntitlementService(store, now::millis, debugAllowed = true), market, FakeFx(), now, marketClock, null,
            sampleData = false, eod = EodSessions(marketClock))
        application { securedForTest(auth, limiter = limiter()) { practiceRoutes(auth, practice, mock = false) } }
        val order = """{"symbol":"AAPL","side":"BUY","quantity":"2","idempotencyKey":"buy-1-abcdef","reviewedPrice":"110"}"""
        suspend fun execute() = client.post("/api/v1/me/practice/orders/execute") { bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json); setBody(order) }
        val responses = coroutineScope { (1..8).map { async { execute() } }.awaitAll() }
        assertTrue(responses.all { it.status == HttpStatusCode.OK || it.status == HttpStatusCode.TooManyRequests }, responses.map { it.status }.toString())
        val filled = responses.filter { it.status == HttpStatusCode.OK }.map { Json { ignoreUnknownKeys = true }.decodeFromString(PracticeOrderResult.serializer(), it.bodyAsText()) }
        assertTrue(filled.isNotEmpty())
        assertEquals(1, filled.map { it.transaction.id }.distinct().size, "every successful retry returns the same fill")
        val replay = execute()
        assertEquals(HttpStatusCode.OK, replay.status)
        assertTrue(Json { ignoreUnknownKeys = true }.decodeFromString(PracticeOrderResult.serializer(), replay.bodyAsText()).replayed)
        assertEquals(1, practice.transactions("alice", null).items.size, "one trade in the ledger after 9 attempts")
        val conflict = client.post("/api/v1/me/practice/orders/execute") { bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json); setBody(order.replace("\"2\"", "\"3\"")) }
        assertEquals(HttpStatusCode.Conflict, conflict.status, "the same key with a different order is refused, never deduplicated into a fill")
    }

    @Test fun limiterStateIsBoundedAndCleanedAfterTheWindow() {
        val limiter = limiter(maxTracked = 1_000)
        repeat(5_000) { i -> limiter.enter(RateLimitClass.PUBLIC_MARKET_DATA, null, ClientIdentity.Address("10.0.${i / 256}.${i % 256}")).also { if (it is RateLimiter.Decision.Admitted) limiter.exit(it) } }
        assertTrue(limiter.tracked() <= 1_000, "LRU bound: ${limiter.tracked()}")
        clock.addAndGet(61_000)
        limiter.sweep()
        assertEquals(0, limiter.tracked(), "nothing is kept after the window passes")
        val held = limiter.enter(RateLimitClass.EXPENSIVE_OPERATION, null, ClientIdentity.User("busy")) as RateLimiter.Decision.Admitted
        clock.addAndGet(61_000); limiter.sweep()
        assertEquals(1, limiter.tracked(), "an in-flight request keeps its entry"); limiter.exit(held); limiter.sweep()
        assertEquals(0, limiter.tracked())
        SecurityLog.reset()
        repeat(20_000) { i -> SecurityLog.record(SecurityEvent.RATE_LIMITED, "x", "GET", "/r", null, "ip:$i") }
        assertTrue(SecurityLog.trackedKeys() <= 10_000, "log throttling state is bounded too")
    }

    @Test fun unverifiedGuestsShareALargePoolAndAnonymousAccountsGetNoBucketOfTheirOwn() = testApplication {
        val small = RateLimitPolicy.DEFAULTS + (RateLimitClass.PUBLIC_MARKET_DATA.name to RateLimitPolicy(90, 180, 50, 12))
        val anonymousAuth = object : UserAuthenticator {
            override suspend fun uid(bearerToken: String) = null
            override suspend fun verify(bearerToken: String) = TokenVerification.Valid(bearerToken, "anonymous")
        }
        application { securedForTest(anonymousAuth, hops = null, limiter = limiter(small), routes = ok) }
        val mixed = (1..60).map { i -> client.get("/api/v1/stocks/AAPL/details") { if (i % 2 == 0) bearerAuth("anon-$i") } }
        assertEquals(50, mixed.count(HttpStatusCode.OK), "no trusted proxy: guests and fresh anonymous accounts share one pool")
        assertEquals(10, mixed.count(HttpStatusCode.TooManyRequests))
    }

    @Test fun rateLimitDenialsAreLoggedWithoutIdentitiesOrPaths() = testApplication {
        val lines = mutableListOf<String>(); SecurityLog.reset(); SecurityLog.sink = { synchronized(lines) { lines += it } }
        val meter = org.example.stocksteps.service.ProviderUsageMeter(); SecurityLog.meter = meter
        try {
            application { securedForTest(auth, hops = 1, limiter = limiter(), routes = ok) }
            client.burst(100, "/api/v1/stocks/SECRETSYM/details") { header("X-Forwarded-For", "203.0.113.7"); bearerAuth("not-a-token") }
            val rateLines = lines.filter { "event=rate_limited" in it }
            assertEquals(1, rateLines.size, "10 denials → one line per minute per identity: $rateLines")
            val line = rateLines.single()
            assertTrue("class=PUBLIC_MARKET_DATA" in line && "route=/api/v1/stocks/{symbol}/details" in line && "identity=ip:" in line, line)
            for (leak in listOf("203.0.113.7", "SECRETSYM", "not-a-token", "Bearer")) assertFalse(lines.any { leak in it }, "log leaks $leak")
            assertEquals(10L, meter.eventCount("security.rate_limited.public_market_data-identity"), "every denial is counted")
        } finally { SecurityLog.sink = null; SecurityLog.meter = org.example.stocksteps.service.ProviderUsageMeter.shared }
    }
}
