package org.example.stocksteps.screener

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.service.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Discover Stocks on REAL (LAN, 2026-10-16): FMP answers `company-screener` with 402 "Restricted Endpoint" for the current plan.
 * The universe failure must say why (plan restriction / rate limit / temporary), and a refusal must not be repeated on every
 * open (the existing [providerCooldown]: 1 h for 402/403, 10 min for 429, 30 s otherwise). REAL adapters on a MockEngine.
 */
class ScreenerUniverseFailureTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
        fun advance(seconds: Long) { instant = instant.plusSeconds(seconds) }
    }

    private class World(universeSize: Int = 3, client: HttpClient? = null) {
        val up = FmpMock(universeSize)
        val http = client ?: up.client
        val clock = MutableClock(Instant.parse("2026-10-07T14:00:00Z"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val fmp = FmpStockProviderRepositoryImpl(http, "k", cacheNow = { clock.millis() }) { LocalDate.parse("2026-10-07") }
        val financials = CompanyFinancialService(fmp)
        val universe = FmpScreenerUniverse(http, "k", listOf("NASDAQ", "NYSE", "TSX"), 50, 2_000_000_000L,
            cache = CompanyFinancialCache(capacity = 4, now = { clock.millis() }))
        // The LAN REAL configuration: no background warm-up (SCREENER_FUNDAMENTALS_PER_HOUR=0).
        val service = ScreenerService(universe, StockService(fmp, fmp), { financials.getFundamentals(it, "annual") },
            PriceChartService(FmpPriceHistoryProvider(http, "k")), { 0.73 }, clock, sampleData = false, fundamentalsPerHour = 0,
            fullRecords = false, scope = scope, screenerFundamentalsOf = { s, c -> financials.screenerFundamentals(s, c) }, meter = ProviderUsageMeter())
        fun screener() = up.n("company-screener")
        suspend fun catalogFailure(): ScreenerRequestException = assertFailsWith { service.catalog() }
    }

    @Test fun planRestrictionSaysRetryingWontHelpAndIsNotRepeatedForAnHour() = runBlocking {
        for (status in listOf(402, 403)) {
            val w = World()
            w.up.status["company-screener"] = status
            val failure = w.catalogFailure()
            assertEquals(503, failure.status)
            assertEquals(ScreenerDisplay.CODE_PROVIDER_RESTRICTED, failure.code)
            assertEquals(ScreenerDisplay.PROVIDER_RESTRICTED_MESSAGE, failure.message)
            assertFalse(ScreenerDisplay.catalogRetryable(failure.code))
            assertEquals(1, w.screener(), "the first exchange's refusal stops the load")
            repeat(5) { w.clock.advance(10 * 60); assertEquals(ScreenerDisplay.CODE_PROVIDER_RESTRICTED, w.catalogFailure().code) }
            assertEquals(ScreenerDisplay.CODE_PROVIDER_RESTRICTED, assertFailsWith<ScreenerRequestException> { w.service.search(ScreenerQuery()) }.code)
            assertEquals(1, w.screener(), "no new provider request during the hour-long cooldown ($status)")
            w.clock.advance(11 * 60)
            w.up.status.clear()   // e.g. the plan was upgraded
            assertEquals(3 * 3, w.service.catalog().universe.size, "tried again after the cooldown")
            assertEquals(1 + 3, w.screener())
            w.scope.cancel()
        }
    }

    @Test fun rateLimitIsReportedAndCoolsDownForTenMinutes() = runBlocking {
        val w = World()
        w.up.status["company-screener"] = 429
        assertEquals(ScreenerDisplay.CODE_RATE_LIMITED, w.catalogFailure().code)
        assertTrue(ScreenerDisplay.catalogRetryable(ScreenerDisplay.CODE_RATE_LIMITED))
        w.clock.advance(9 * 60)
        assertEquals(ScreenerDisplay.CODE_RATE_LIMITED, w.catalogFailure().code)
        assertEquals(1, w.screener(), "never retried within the rate-limit cooldown")
        w.clock.advance(2 * 60)
        w.catalogFailure()
        assertEquals(2, w.screener())
        w.scope.cancel()
    }

    @Test fun anOutageIsTemporaryAndRetriedAfterThirtySeconds() = runBlocking {
        val w = World()
        w.up.status["company-screener"] = 500
        val failure = w.catalogFailure()
        assertEquals(ScreenerDisplay.CODE_UNAVAILABLE, failure.code)
        assertEquals(ScreenerDisplay.UNAVAILABLE_MESSAGE, failure.message)
        w.clock.advance(10); w.catalogFailure()
        assertEquals(1, w.screener())
        w.clock.advance(25)
        w.up.status.clear()
        assertEquals(9, w.service.catalog().universe.size)
        assertEquals(1 + 3, w.screener())
        w.scope.cancel()
    }

    @Test fun anUnreadableProviderAnswerIsTemporary() = runBlocking {
        val calls = AtomicInteger()
        val client = HttpClient(MockEngine {
            calls.incrementAndGet()
            respond("<html>not json</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val w = World(client = client)
        assertEquals(ScreenerDisplay.CODE_UNAVAILABLE, w.catalogFailure().code)
        assertEquals(1, calls.get())
        w.scope.cancel()
    }

    @Test fun concurrentIdenticalRequestsShareOneProviderRequest() = runBlocking {
        val refused = World()
        refused.up.status["company-screener"] = 402
        val codes = (1..20).map { async(Dispatchers.Default) { runCatching { refused.service.catalog() }.exceptionOrNull() as? ScreenerRequestException } }.awaitAll()
        assertTrue(codes.all { it?.code == ScreenerDisplay.CODE_PROVIDER_RESTRICTED })
        assertEquals(1, refused.screener())
        refused.scope.cancel()

        val ok = World()
        (1..20).map { async(Dispatchers.Default) { ok.service.catalog() } }.awaitAll()
        assertEquals(3, ok.screener(), "one request per exchange for 20 concurrent opens")
        ok.scope.cancel()
    }

    @Test fun theRouteAnswersWithTheCodeAndMessage() = testApplication {
        val w = World()
        w.up.status["company-screener"] = 402
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { screenerRoutes(w.service, RequestRateLimiter(1_000)) }
        }
        val response = client.get("/api/v1/screener/catalog")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val error = Json.decodeFromString(ApiError.serializer(), response.bodyAsText())
        assertEquals(ScreenerDisplay.CODE_PROVIDER_RESTRICTED, error.code)
        assertFalse("Try again shortly" in error.message)
        assertFalse("financialmodelingprep" in response.bodyAsText(), "no provider URL in the answer")
        w.scope.cancel()
    }

    /** Phase F: provider requests for the Discover journeys on REAL (warm-up off), measured on the MockEngine. */
    @Test fun discoverJourneysReuseTheUniverseAndPageCaches() = runBlocking {
        val w = World(universeSize = 50)
        w.service.catalog()                                                       // open Discover
        val open = w.up.total
        assertEquals(3, open, "the universe: one request per exchange")
        w.service.search(ScreenerQuery(pageSize = 20))                            // default results
        val firstPage = w.up.total - open
        assertEquals(20, firstPage, "one quote per row on the page")
        val filter = ScreenerQuery(ranges = listOf(org.example.stocksteps.screener.RangeFilter("marketCap", min = 900_000_000_000.0)), pageSize = 20)
        val before = w.up.total
        w.service.search(filter)                                                  // apply a market-data filter
        val applied = w.up.total - before
        val again = w.up.total
        w.service.search(filter)                                                  // the identical filter again
        val repeated = w.up.total - again
        val back = w.up.total
        w.service.catalog()                                                       // back from Company Details / tab switch (if the app re-asks)
        assertEquals(0, w.up.total - back, "the universe stays cached")
        assertEquals(3, w.screener())
        println("DISCOVER_REQUESTS open=$open firstPage=$firstPage filter=$applied repeat=$repeated catalogAgain=${w.up.total - back}")
        w.scope.cancel()
    }
}
