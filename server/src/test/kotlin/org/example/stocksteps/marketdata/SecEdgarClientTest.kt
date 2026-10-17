package org.example.stocksteps.marketdata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.example.stocksteps.httpclient.ProviderCalls
import org.example.stocksteps.httpclient.apiCallText
import org.example.stocksteps.marketdata.sec.SecEdgarClient
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.ProviderBudgetConfig
import org.example.stocksteps.service.ProviderGuard
import org.example.stocksteps.service.ProviderUsageMeter
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B: SEC EDGAR client fair access, bounds, caching, cooldowns and single flight on a MockEngine (no network). */
class SecEdgarClientTest {
    private class Sec(var now: Long = 0) {
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val agents: MutableList<String?> = Collections.synchronizedList(mutableListOf())
        val status = ConcurrentHashMap<String, Int>()
        @Volatile var factsBody = SecFixtures.json(1001, "Example Growth Co", SecFixtures.growthCo)
        @Volatile var declaredLength: Long? = null
        fun n(k: String) = counts[k]?.get() ?: 0
        val client = HttpClient(MockEngine { req ->
            delay(5)
            val key = ProviderCalls.endpoint(req.url.toString())
            counts.getOrPut(key) { AtomicInteger() }.incrementAndGet()
            agents += req.headers[HttpHeaders.UserAgent]
            status[key]?.let { return@MockEngine respond("{}", HttpStatusCode.fromValue(it)) }
            when (key) {
                "company_tickers_exchange.json" -> respond(SecFixtures.tickersJson(Triple(1001L, "Example Growth Co", "EXGC")), HttpStatusCode.OK)
                "xbrl/companyfacts" -> if ("CIK0000001001" in req.url.encodedPath)
                    respond(factsBody, HttpStatusCode.OK, declaredLength?.let { headersOf(HttpHeaders.ContentLength, it.toString()) } ?: headersOf())
                    else respond("{}", HttpStatusCode.NotFound)
                else -> respond("{}", HttpStatusCode.NotFound)
            }
        })
        val sec = SecEdgarClient(client, "StockSteps test ops@example.com", CompanyFinancialCache(capacity = 64, now = { now }))
    }

    @Test fun refusesToStartWithoutAnIdentifyingUserAgent() {
        assertFailsWith<IllegalArgumentException> { SecEdgarClient(HttpClient(MockEngine { respond("") }), "StockSteps") }
        assertFailsWith<IllegalArgumentException> { SecEdgarClient(HttpClient(MockEngine { respond("") }), "a@b.co\nX-Injected: 1") }
        assertNull(SecEdgarClient.fromEnvironment(HttpClient(MockEngine { respond("") })) { null })
        assertNotNull(SecEdgarClient.fromEnvironment(HttpClient(MockEngine { respond("") })) { if (it == "SEC_USER_AGENT") "StockSteps admin@example.org" else null })
    }

    @Test fun sendsTheUserAgentAndBuildsHistoryFromTickerAndFacts() = runBlocking {
        val s = Sec()
        val h = assertNotNull(s.sec.history("EXGC"))
        assertEquals("sec", h.provenance.provider)
        assertEquals(3, h.value.annual.size)
        assertTrue(s.agents.isNotEmpty() && s.agents.all { it == "StockSteps test ops@example.com" })
        assertEquals(1, s.n("company_tickers_exchange.json")); assertEquals(1, s.n("xbrl/companyfacts"))
        assertNull(s.sec.history("UNKNOWN"), "unknown ticker: no request for facts")
        assertEquals(1, s.n("xbrl/companyfacts"))
    }

    @Test fun endpointLabelsAreBoundedAndNeverCarryTheCik() {
        assertEquals("sec", ProviderCalls.provider("https://data.sec.gov/api/xbrl/companyfacts/CIK0000320193.json"))
        assertEquals("xbrl/companyfacts", ProviderCalls.endpoint("https://data.sec.gov/api/xbrl/companyfacts/CIK0000320193.json"))
        assertEquals("submissions", ProviderCalls.endpoint("https://data.sec.gov/submissions/CIK0000320193.json"))
        assertEquals("company_tickers_exchange.json", ProviderCalls.endpoint(SecEdgarClient.TICKERS_URL))
    }

    @Test fun concurrentSameCompanyRequestsShareOneFetchAndRepeatsAreCached() = runBlocking {
        val s = Sec()
        (1..25).map { async { s.sec.companyFacts(1001) } }.awaitAll().forEach { assertNotNull(it) }
        repeat(10) { s.sec.companyFacts(1001) }
        assertEquals(1, s.n("xbrl/companyfacts"))
        s.now += SecEdgarClient.DAY + 1
        s.sec.companyFacts(1001)
        assertEquals(2, s.n("xbrl/companyfacts"), "reloaded after 24 h")
    }

    @Test fun notFoundIsCachedAsNoneAndRefusalsCoolDown() = runBlocking {
        val s = Sec()
        repeat(5) { assertNull(s.sec.companyFacts(999)) }
        assertEquals(1, s.n("xbrl/companyfacts"), "404 answered once, then cached as 'no facts'")

        val r = Sec(); r.status["xbrl/companyfacts"] = 403
        repeat(5) { assertTrue(assertFailsWith<StockProviderException> { r.sec.companyFacts(1001) }.restricted) }
        assertEquals(1, r.n("xbrl/companyfacts"), "403 never retried inside the 1 h cooldown")
        r.now += 3_600_001; r.status.remove("xbrl/companyfacts")
        assertNotNull(r.sec.companyFacts(1001)); assertEquals(2, r.n("xbrl/companyfacts"))

        val l = Sec(); l.status["xbrl/companyfacts"] = 429
        repeat(5) { assertEquals(StockProviderException.Failure.RATE_LIMITED, assertFailsWith<StockProviderException> { l.sec.companyFacts(1001) }.failure) }
        assertEquals(1, l.n("xbrl/companyfacts"), "429 cooled down for 10 min, never retried in a loop")
    }

    @Test fun oversizedDocumentsAreRefused() = runBlocking {
        // Declared too large: refused before reading.
        val s = Sec(); s.declaredLength = s.factsBody.encodeToByteArray().size.toLong()
        val small = SecEdgarClient(s.client, "StockSteps test ops@example.com", CompanyFinancialCache(capacity = 8), maxFactsBytes = 1_000)
        assertEquals(StockProviderException.Failure.INVALID_RESPONSE, assertFailsWith<StockProviderException> { small.companyFacts(1001) }.failure)
        val undeclared = Sec()
        val bounded = SecEdgarClient(undeclared.client, "StockSteps test ops@example.com", CompanyFinancialCache(capacity = 8), maxFactsBytes = 1_000)
        assertEquals(StockProviderException.Failure.INVALID_RESPONSE, assertFailsWith<StockProviderException> { bounded.companyFacts(1001) }.failure)
        assertNotNull(SecEdgarClient(undeclared.client, "StockSteps test ops@example.com", CompanyFinancialCache(capacity = 8)).companyFacts(1001))
        // A body read stops at the bound even without a Content-Length header.
        val tiny = HttpClient(MockEngine { respond("x".repeat(2_000), HttpStatusCode.OK) })
        assertEquals(StockProviderException.Failure.INVALID_RESPONSE, assertFailsWith<StockProviderException> {
            tiny.apiCallText("https://data.sec.gov/submissions/CIK0000000001.json", 1_000)
        }.failure)
        assertEquals(2_000, tiny.apiCallText("https://data.sec.gov/submissions/CIK0000000001.json", 2_000).length, "exactly at the bound is accepted")
    }

    @Test fun malformedFactsAreAnInvalidResponseNotData() = runBlocking {
        val s = Sec(); s.factsBody = """{"cik":1001}"""
        assertEquals(StockProviderException.Failure.INVALID_RESPONSE, assertFailsWith<StockProviderException> { s.sec.companyFacts(1001) }.failure)
    }

    @Test fun theSecLaneIsPacedByProviderGuard() = runBlocking {
        val s = Sec()
        val config = ProviderBudgetConfig.fromEnvironment { null }
        val guard = ProviderGuard(config, meter = ProviderUsageMeter())
        val override = ProviderBudgetConfig.fromEnvironment { if (it == "PROVIDER_SEC_PER_MINUTE") "100000" else null }.limits.getValue("sec")
        assertEquals(600, override.perMinute, "never above the SEC's published 10 requests/second"); assertEquals(10, override.burst)
        assertEquals(setOf("fmp", "finnhub", "gemini", "boc", "sec"), ProviderBudgetConfig.DEVELOPMENT.keys)
        val local = config.local(config.limits.getValue("sec"))
        withContext(guard) { (1..40).map { async { runCatching { s.sec.companyFacts(it.toLong() + 5000) } } }.awaitAll() }
        val sent = s.n("xbrl/companyfacts")
        // Burst, then at most the refill during the 2 s an interactive caller may wait; the rest are refused locally (no upstream call).
        assertTrue(sent <= local.burst + local.perMinute * 2 / 60 + 2 && sent < 40, "sent $sent")
        assertTrue(s.agents.size == sent)
    }
}
