package org.example.stocksteps.marketdata.alphavantage

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.example.stocksteps.httpclient.ProviderCalls
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.EodSessions
import org.example.stocksteps.service.ProviderBudgetConfig
import org.example.stocksteps.service.ProviderGuard
import org.example.stocksteps.service.ProviderUsageMeter
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B.7: the Alpha Vantage client on a MockEngine — refusal handling, cooldowns, single flight, plan gating, key secrecy. */
class AlphaVantageClientTest {
    private val now = Instant.parse("2026-10-16T22:00:00Z")   // Friday 18:00 ET: Friday's close is expected
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sessions = EodSessions(clock)
    private val secret = "TESTKEY-not-real-0123456789"

    internal class Av(val clockMs: () -> Long) {
        val calls = ConcurrentHashMap<String, AtomicInteger>()
        val urls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val bodies = ConcurrentHashMap<String, String>()
        fun n(f: String) = calls[f]?.get() ?: 0
        val total get() = calls.values.sumOf { it.get() }
        val http = HttpClient(MockEngine { req ->
            delay(5)
            val f = req.url.parameters["function"] ?: "?"
            calls.getOrPut(f) { AtomicInteger() }.incrementAndGet(); urls += req.url.toString()
            val sym = req.url.parameters["symbol"] ?: req.url.parameters["keywords"] ?: ""
            respond(bodies["$f|$sym"] ?: bodies[f] ?: "{}", HttpStatusCode.OK)
        })
    }
    private fun config(premium: Boolean = false) = AlphaVantageConfig(secret, true, if (premium) 75 else null, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet())
    private fun client(av: Av, premium: Boolean = false, t: () -> Long = { 0L }) =
        AlphaVantageClient(av.http, config(premium), sessions, clock, CompanyFinancialCache(capacity = 64, now = t))

    @Test fun theKeyIsARequestParameterOnlyNeverInLabelsLogsOrConfigText() = runBlocking<Unit> {
        val av = Av { 0 }; av.bodies["TIME_SERIES_DAILY"] = AvFixtures.daily("EXMP", listOf("2026-10-15" to "10", "2026-10-16" to "11"))
        av.bodies["GLOBAL_QUOTE"] = AvFixtures.PREMIUM
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        val logger = LoggerFactory.getLogger("StockSteps.Provider") as Logger
        logger.addAppender(appender)
        try {
            val c = client(av)
            assertNotNull(c.dailyRaw("EXMP"))
            runCatching { c.quote("EXMP") }
        } finally { logger.detachAppender(appender) }
        assertTrue(av.urls.all { "apikey=$secret" in it }, "the key travels as the apikey parameter")
        assertTrue(appender.list.none { secret in it.formattedMessage }, "never in logs")
        assertTrue(appender.list.any { "query/GLOBAL_QUOTE" in it.formattedMessage && "PREMIUM_ONLY" in it.formattedMessage })
        assertEquals("query/TIME_SERIES_DAILY", ProviderCalls.endpoint("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=EXMP"))
        assertEquals("alphavantage", ProviderCalls.provider("https://www.alphavantage.co/query?function=OVERVIEW"))
        assertTrue(secret !in config().toString())
    }

    @Test fun refusalsAreCooledDownAndNeverReturnedAsData() = runBlocking<Unit> {
        var t = 0L
        val av = Av { t }
        av.bodies["GLOBAL_QUOTE|EXMP"] = AvFixtures.RATE_LIMIT
        av.bodies["GLOBAL_QUOTE|PREM"] = AvFixtures.PREMIUM
        av.bodies["GLOBAL_QUOTE|NOPE"] = AvFixtures.INVALID_CALL
        val c = client(av) { t }
        repeat(5) { assertEquals(StockProviderException.Failure.RATE_LIMITED, assertFailsWith<StockProviderException> { c.quote("EXMP") }.failure) }
        repeat(5) { assertTrue(assertFailsWith<StockProviderException> { c.quote("PREM") }.restricted) }
        repeat(5) { assertNull(c.quote("NOPE"), "an Error Message is 'no such symbol', cached") }
        assertEquals(3, av.n("GLOBAL_QUOTE"), "one upstream request each")
        t += 10 * 60_000L + 1; av.bodies["GLOBAL_QUOTE|EXMP"] = AvFixtures.quote("EXMP", "11", "2026-10-16", "10")
        assertNotNull(c.quote("EXMP"), "rate-limit cooldown is 10 min")
        assertTrue(assertFailsWith<StockProviderException> { c.quote("PREM") }.restricted, "premium refusal still cooling down (1 h)")
        assertEquals(4, av.n("GLOBAL_QUOTE"))
        t += 2 * 86_400_000L; assertNotNull(c.quote("EXMP"))
        assertEquals(4, av.n("GLOBAL_QUOTE"), "an end-of-day quote holding Friday's close lives through the weekend")
    }

    @Test fun premiumFunctionsAreNeverCalledWithoutAPlanAndIndicesAreRefusedLocally() = runBlocking<Unit> {
        val av = Av { 0 }; av.bodies["OVERVIEW"] = AvFixtures.OVERVIEW
        val free = client(av)
        assertTrue(assertFailsWith<StockProviderException> { free.overview("EXMP") }.restricted)
        assertTrue(assertFailsWith<StockProviderException> { free.dailyAdjusted("EXMP") }.restricted)
        assertNull(free.quote("^GSPC")); assertNull(free.dailyRaw("^GSPC"))
        assertEquals(0, av.total, "no request, no quota used")
        assertNotNull(client(av, premium = true).overview("EXMP")); assertEquals(1, av.n("OVERVIEW"))
        assertEquals("SHOP.TRT", free.avSymbol("SHOP.TO")); assertNull(free.avSymbol("A B")); assertNull(free.avSymbol("x".repeat(30)))
    }

    @Test fun historyDepthFollowsThePlan() = runBlocking<Unit> {
        val av = Av { 0 }; av.bodies["TIME_SERIES_DAILY"] = AvFixtures.daily("EXMP", listOf("2026-10-16" to "11"))
        client(av).dailyRaw("EXMP"); client(av, premium = true).dailyRaw("EXMP")
        assertEquals(listOf("compact", "full"), av.urls.map { Regex("outputsize=(\\w+)").find(it)!!.groupValues[1] })
    }

    @Test fun concurrentIdenticalRequestsShareOneFetchAndCachesExpire() = runBlocking<Unit> {
        var t = 0L
        val av = Av { t }; av.bodies["TIME_SERIES_DAILY"] = AvFixtures.daily("EXMP", listOf("2026-10-15" to "10", "2026-10-16" to "11"))
        av.bodies["SYMBOL_SEARCH"] = AvFixtures.SEARCH
        val c = client(av) { t }
        (1..50).map { async { c.dailyRaw("EXMP") } }.awaitAll()
        (1..50).map { async { c.search("Example ") } }.awaitAll(); c.search("example")
        assertEquals(1, av.n("TIME_SERIES_DAILY")); assertEquals(1, av.n("SYMBOL_SEARCH"), "normalized query, one request")
        // The daily series holds Friday's close, so it lives until Monday's expected close (no request over the weekend).
        t += 2 * 86_400_000L; c.dailyRaw("EXMP"); assertEquals(1, av.n("TIME_SERIES_DAILY"))
        t += 6 * 3_600_000L + 1; c.search("example"); assertEquals(2, av.n("SYMBOL_SEARCH"), "search 6 h")
    }

    @Test fun malformedAndUnexpectedAnswersAreInvalidNotEmpty() = runBlocking<Unit> {
        val av = Av { 0 }; av.bodies["TIME_SERIES_DAILY"] = """{"Meta Data":{"2. Symbol":"EXMP"}}"""; av.bodies["SYMBOL_SEARCH"] = "not json"
        val c = client(av)
        assertEquals(StockProviderException.Failure.INVALID_RESPONSE, assertFailsWith<StockProviderException> { c.dailyRaw("EXMP") }.failure)
        assertEquals(StockProviderException.Failure.INVALID_RESPONSE, assertFailsWith<StockProviderException> { c.search("x") }.failure)
        av.bodies["EARNINGS"] = AvFixtures.UNKNOWN_NOTICE
        assertEquals(StockProviderException.Failure.UNAVAILABLE, assertFailsWith<StockProviderException> { c.earnings("EXMP") }.failure)
    }

    @Test fun theDailyBudgetAndRateLimitProtectTheQuota() = runBlocking<Unit> {
        val av = Av { 0 }; av.bodies["GLOBAL_QUOTE"] = AvFixtures.quote("EXMP", "11", "2026-10-16", "10")
        // Free tier without a plan: 25 a day × 0.8 safety margin = 20 on this instance.
        val budgets = ProviderBudgetConfig.fromEnvironment { null }
        assertEquals(25L, budgets.limits.getValue("alphavantage").dailyLimit)
        var t = 0L
        val guard = ProviderGuard(budgets, now = { t }, meter = ProviderUsageMeter(), sleep = { t += it })
        val c = client(av)
        // The free rate (5/min, 4 after the margin) refuses a fast burst first: 4 sent, the rest refused locally.
        withContext(guard) { (1..10).map { i -> runCatching { c.quote("B$i") } } }
        assertEquals(4, av.n("GLOBAL_QUOTE"))
        // Spread over the day, the daily allowance is the limit: 20 in total on this instance.
        val results = withContext(guard) { (1..40).map { i -> t += 60_000; runCatching { c.quote("S$i") } } }
        assertEquals(20, av.n("GLOBAL_QUOTE"), "the 21st request of the day is refused locally")
        assertTrue(results.drop(16).all { (it.exceptionOrNull() as? StockProviderException)?.dailyLimitReached == true })
        // A premium plan: per-minute rate, no daily cap unless configured.
        val premium = ProviderBudgetConfig.fromEnvironment { mapOf("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE" to "75", "ALPHA_VANTAGE_MAX_CONCURRENT" to "3")[it] }.limits.getValue("alphavantage")
        assertEquals(75, premium.perMinute); assertNull(premium.dailyLimit); assertEquals(3, premium.maxConcurrent)
        assertEquals(500L, ProviderBudgetConfig.fromEnvironment { mapOf("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE" to "75", "ALPHA_VANTAGE_DAILY_BUDGET" to "500")[it] }.limits.getValue("alphavantage").dailyLimit)
    }

    @Test fun anInBodyRateLimitOpensTheProviderBreaker() = runBlocking<Unit> {
        val av = Av { 0 }; av.bodies["GLOBAL_QUOTE"] = AvFixtures.NOTE_FREQUENCY
        val guard = ProviderGuard(ProviderBudgetConfig.fromEnvironment { mapOf("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE" to "75")[it] }, meter = ProviderUsageMeter())
        val c = client(av, premium = true)
        withContext(guard) { (1..5).forEach { i -> runCatching { c.quote("S$i") } } }
        assertEquals(1, av.n("GLOBAL_QUOTE"), "after the first in-body rate limit the breaker refuses locally")
        assertTrue(guard.open("alphavantage"))
    }
}
