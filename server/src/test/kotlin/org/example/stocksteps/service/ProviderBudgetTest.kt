package org.example.stocksteps.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.earnings.DataFreshness
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repositoryImpl.FmpFundamentalsLoader
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/**
 * Phase 4C: provider-wide budgets — token bucket, concurrency, priority reserve, circuit breaker, multi-instance sizing — on a fake clock;
 * MockEngine only. The guard is put in the coroutine context, so no global state is touched.
 */
class ProviderBudgetTest {
    private val time = AtomicLong(1_000_000)
    private fun guard(perMinute: Int = 60, burst: Int = 10, concurrent: Int = 100, instances: Int = 1, reserve: Double = 0.3,
                      wait: Map<ProviderPriority, Long> = mapOf(ProviderPriority.HIGH to 2_000, ProviderPriority.NORMAL to 1_000, ProviderPriority.LOW to 0),
                      meter: ProviderUsageMeter = ProviderUsageMeter()) =
        ProviderGuard(ProviderBudgetConfig(mapOf("fmp" to ProviderLimits(perMinute, burst, concurrent), "finnhub" to ProviderLimits(perMinute, burst, concurrent)),
            safetyMargin = 1.0, maxInstances = instances, interactiveReserve = reserve, maxWaitMillis = wait, slotWaitMillis = wait), { time.get() }, meter, sleep = { time.addAndGet(it) })

    private suspend fun ProviderGuard.tryCall(provider: String = "fmp", endpoint: String = "quote", priority: ProviderPriority = ProviderPriority.HIGH, status: Int = 200): Boolean =
        withContext(ProviderPriorityElement(priority)) {
            try { complete(acquire(provider, endpoint), status); true } catch (_: StockProviderException) { false }
        }

    @Test fun localLimitsAreDerivedConservativelyFromPlanValues() {
        val config = ProviderBudgetConfig(mapOf("fmp" to ProviderLimits(1_000, 500, 40)), safetyMargin = 0.8, maxInstances = 5)
        assertEquals(ProviderLimits(160, 80, 8, null), config.local(config.limits.getValue("fmp")))
        val env = ProviderBudgetConfig.fromEnvironment(emptyMap<String, String>()::get)
        assertFalse(env.verified, "development defaults are never reported as verified production limits")
        assertTrue(env.limits.values.all { it.perMinute > 0 }, "finite even when unconfigured")
        val configured = ProviderBudgetConfig.fromEnvironment(mapOf("PROVIDER_FMP_PER_MINUTE" to "3000", "PROVIDER_FINNHUB_PER_MINUTE" to "60",
            "PROVIDER_GEMINI_PER_MINUTE" to "100", "PROVIDER_BOC_PER_MINUTE" to "30", "CLOUD_RUN_MAX_INSTANCES" to "3")::get)
        assertTrue(configured.verified); assertEquals(3, configured.maxInstances)
    }

    @Test fun burstThenRateLimitedWithBoundedWaiting(): Unit = runBlocking {
        val g = guard(perMinute = 60, burst = 10)
        repeat(10) { assertTrue(g.tryCall()) }
        val before = time.get()
        assertTrue(g.tryCall(), "HIGH waits for the next token (≈ 1 s at 60/min)")
        assertTrue(time.get() - before in 1_000..1_100)
        val strict = guard(perMinute = 60, burst = 10, wait = mapOf(ProviderPriority.HIGH to 0L))
        repeat(10) { strict.tryCall() }
        assertFalse(strict.tryCall(), "no waiting allowed → denied, not queued forever")
    }

    @Test fun backgroundWorkCannotStarveInteractiveRequests(): Unit = runBlocking {
        val g = guard(perMinute = 60, burst = 10, reserve = 0.3)
        val low = (1..20).count { g.tryCall(priority = ProviderPriority.LOW) }
        assertEquals(7, low, "LOW stops at the 30 % interactive reserve")
        assertTrue(g.tryCall(priority = ProviderPriority.HIGH)); assertTrue(g.tryCall(priority = ProviderPriority.HIGH))
        assertFalse(g.tryCall(priority = ProviderPriority.LOW), "warm-up is deferred, not queued")
    }

    @Test fun concurrencyIsBoundedAndCancellationReleasesSlots(): Unit = runBlocking {
        val g = guard(concurrent = 2, wait = mapOf(ProviderPriority.HIGH to 0L))
        val a = g.acquire("fmp", "quote"); val b = g.acquire("fmp", "quote")
        assertFailsWith<StockProviderException> { g.acquire("fmp", "quote") }
        g.complete(a, null, cancelled = true)
        val c = g.acquire("fmp", "quote")
        g.complete(b, 200); g.complete(c, 200)
    }

    @Test fun rateLimitedProviderOpensTheCircuitHonouringRetryAfterThenHalfOpens(): Unit = runBlocking {
        val g = guard(burst = 100, perMinute = 6_000)
        g.complete(g.acquire("fmp", "quote"), 429, retryAfterSeconds = 10)
        assertTrue(g.open("fmp"))
        assertFalse(g.tryCall(endpoint = "profile"), "the whole provider pauses on 429")
        assertTrue(g.tryCall(provider = "finnhub"), "other providers are unaffected")
        time.addAndGet(10_001)
        val trial = g.acquire("fmp", "quote")
        assertFailsWith<StockProviderException>("half-open: one trial at a time") { g.acquire("fmp", "quote") }
        g.complete(trial, 200)
        assertTrue(g.tryCall(), "a successful trial closes the circuit")
        g.complete(g.acquire("fmp", "quote"), 429)
        time.addAndGet(30_001)
        g.complete(g.acquire("fmp", "quote"), 429)
        assertTrue(g.open("fmp")); time.addAndGet(59_000); assertTrue(g.open("fmp"), "repeated 429s double the pause (30 s → 60 s)")
    }

    @Test fun serverErrorsOpenOnlyTheFailingEndpointAndAccessDenialNeverTrips(): Unit = runBlocking {
        val g = guard(burst = 100, perMinute = 6_000)
        repeat(5) { g.tryCall(endpoint = "ratios-ttm", status = 503) }
        assertTrue(g.open("fmp", "ratios-ttm")); assertFalse(g.open("fmp", "quote"))
        assertTrue(g.tryCall(endpoint = "quote"), "unrelated endpoints keep working")
        repeat(20) { g.tryCall(endpoint = "dividends", status = 403) }
        assertFalse(g.open("fmp", "dividends"), "402/403 aren't transient")
    }

    @Test fun fiveInstancesNeverExceedThePlanTogether(): Unit = runBlocking {
        val instances = (1..5).map { guard(perMinute = 100, burst = 100, instances = 5, reserve = 0.0, wait = mapOf(ProviderPriority.HIGH to 0L)) }
        val admitted = instances.sumOf { g -> (1..100).count { g.tryCall() } }
        assertEquals(100, admitted, "5 × (100 ÷ 5) = the plan's burst, however traffic is spread")
        val single = guard(perMinute = 100, burst = 100, instances = 1, reserve = 0.0, wait = mapOf(ProviderPriority.HIGH to 0L))
        assertEquals(100, (1..300).count { single.tryCall() })
    }

    private fun mock(status: () -> HttpStatusCode, calls: AtomicInteger, headers: Headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) =
        HttpClient(MockEngine { calls.incrementAndGet(); respond("[]", status(), headers) }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    @Test fun everyUpstreamAttemptIsAdmittedAndA429StormStopsQuickly(): Unit = runBlocking {
        val meter = ProviderUsageMeter()
        val g = guard(burst = 1_000, perMinute = 60_000, meter = meter)
        val calls = AtomicInteger()
        val client = mock({ HttpStatusCode.TooManyRequests }, calls, headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("20")))
        withContext(g) {
            repeat(100) { runCatching { client.apiCall<List<String>>("https://financialmodelingprep.com/stable/quote", "k") { } } }
        }
        assertEquals(1, calls.get(), "after the first 429 the provider pauses for Retry-After: 99 calls never leave the server")
        assertTrue(meter.eventCount("provider.fmp.budget.denied.circuitOpen") >= 99)
        time.addAndGet(20_001)
        val ok = mock({ HttpStatusCode.OK }, calls)
        withContext(g) { ok.apiCall<List<String>>("https://financialmodelingprep.com/stable/quote", "k") { } }
        assertEquals(2, calls.get(), "recovers after the pause")
    }

    @Test fun deniedCallsNeverCountAsUpstreamAndCachedDataNeedsNoBudget(): Unit = runBlocking {
        val up = FmpMock()
        val ms = AtomicLong(0)
        val quote: suspend () -> Pair<StockQuote?, CompanyProfile?> = { null to null }
        val loader = FmpFundamentalsLoader(up.client, "k", CompanyFinancialCache(now = { ms.get() }), { LocalDate.parse("2026-10-07") }, meter = ProviderUsageMeter())
        val roomy = guard(burst = 1_000, perMinute = 60_000)
        val first = withContext(roomy) { loader.load("AAPL", "annual", quote = quote) }
        val upstream = up.total
        val exhausted = guard(burst = 1, perMinute = 1, wait = mapOf(ProviderPriority.HIGH to 0L))
        withContext(exhausted) { exhausted.tryCall() }                                                 // use the only token
        ms.addAndGet(60_000)
        val cached = withContext(exhausted) { loader.load("AAPL", "annual", quote = quote) }
        assertEquals(first.history, cached.history, "statements still cached: no provider budget needed")
        ms.addAndGet(25 * 3_600_000L)
        val degraded = withContext(exhausted) { loader.load("AAPL", "annual", quote = quote) }
        assertEquals(upstream, up.total, "a denied call never reaches the provider")
        assertEquals(DataFreshness.STALE, degraded.freshness, "budget exhaustion falls back to labelled stale statements")
        assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, degraded.datasets["ratiosTtm"], "price-sensitive data is unavailable, never zero")
        assertNull(degraded.valuation.metrics["pe"]?.value)
    }

    @Test fun geminiNewsSimplificationAndBankOfCanadaPathsAreGuardedToo(): Unit = runBlocking {
        val calls = AtomicInteger()
        val client = mock({ HttpStatusCode.OK }, calls)
        val closed = ProviderGuard(ProviderBudgetConfig(mapOf("gemini" to ProviderLimits(1, 1, 1), "boc" to ProviderLimits(1, 1, 1)), safetyMargin = 1.0,
            interactiveReserve = 0.0, maxWaitMillis = mapOf(ProviderPriority.HIGH to 0L, ProviderPriority.LOW to 0L), slotWaitMillis = mapOf(ProviderPriority.HIGH to 0L, ProviderPriority.LOW to 0L)),
            { time.get() }, ProviderUsageMeter())
        withContext(closed) {
            // Use each provider's only token, then every path must be refused before a request leaves the server.
            closed.complete(closed.acquire("gemini", "x"), 200); closed.complete(closed.acquire("boc", "x"), 200)
            assertFailsWith<StockProviderException> {
                org.example.stocksteps.news.GeminiJsonCall(client, "dummy", "model").generate("p", kotlinx.serialization.json.buildJsonObject { }, kotlinx.serialization.json.buildJsonObject { }, 100, 1_000)
            }
            assertFailsWith<StockProviderException> {
                org.example.stocksteps.news.GeminiNewsSimplifier(client, "dummy", "model").simplify(org.example.stocksteps.model.NewsArticle("t", "https://example.com/a", description = "d"))
            }
            assertFailsWith<StockProviderException> { org.example.stocksteps.userdata.BankOfCanadaPortfolioFx(client).rates("2026-10-01", "2026-10-07") }
        }
        assertEquals(0, calls.get(), "no Gemini or Bank of Canada request was sent")
    }
}
