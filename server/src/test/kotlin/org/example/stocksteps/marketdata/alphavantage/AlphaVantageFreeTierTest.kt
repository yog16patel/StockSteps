package org.example.stocksteps.marketdata.alphavantage

import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.example.stocksteps.appconfig.DataMode
import org.example.stocksteps.appconfig.StartupConfiguration
import org.example.stocksteps.marketdata.MarketDataAccessGate
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.EodSessions
import org.example.stocksteps.service.ProviderBudgetConfig
import org.example.stocksteps.service.ProviderGuard
import org.example.stocksteps.service.ProviderUsageMeter
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B.9A: Alpha Vantage FREE entitlement — never premium by accident, exact daily budget, zero-cost refusals, bounded smoke test. */
class AlphaVantageFreeTierTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-16T22:00:00Z"), ZoneOffset.UTC)
    private val sessions = EodSessions(clock)
    private val secret = "FREEKEY-not-real-123"

    @Test fun aRateSettingNeverImpliesPremium() {
        val c = AlphaVantageConfig.fromEnvironment { mapOf("ALPHA_VANTAGE_ENABLED" to "true", "ALPHA_VANTAGE_API_KEY" to secret, "ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE" to "1200")[it] }!!
        assertEquals(AlphaVantageConfig.Entitlement.FREE, c.entitlement); assertFalse(c.premium)
        assertTrue(AlphaVantageConfig.fromEnvironment { mapOf("ALPHA_VANTAGE_ENABLED" to "true", "ALPHA_VANTAGE_API_KEY" to secret, "ALPHA_VANTAGE_ENTITLEMENT" to "Premium")[it] }!!.premium)
        assertTrue("FREE" in c.toString() && secret !in c.toString())
        // The lane stays at the free rate whatever the plan setting says.
        val lane = ProviderBudgetConfig.fromEnvironment { mapOf("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE" to "1200")[it] }.limits.getValue("alphavantage")
        assertEquals(5, lane.perMinute); assertEquals(20L, lane.dailyLimit)
    }

    @Test fun premiumFunctionsCostZeroUpstreamCallsOnFree() = runBlocking<Unit> {
        val av = AlphaVantageClientTest.Av { 0 }; av.bodies["OVERVIEW"] = AvFixtures.OVERVIEW
        av.bodies["TIME_SERIES_DAILY"] = AvFixtures.daily("AAPL", listOf("2026-10-16" to "1"))
        val c = AlphaVantageClient(av.http, AlphaVantageConfig(secret, true, 1200, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet()), sessions, clock)
        assertTrue(runCatching { c.overview("AAPL") }.exceptionOrNull().let { it is StockProviderException && it.restricted })
        assertTrue(runCatching { c.dailyAdjusted("AAPL") }.exceptionOrNull().let { it is StockProviderException && it.restricted })
        assertEquals(0, av.total, "OVERVIEW and adjusted history never reach Alpha Vantage on FREE")
        c.dailyRaw("AAPL")
        assertTrue(av.urls.single().contains("outputsize=compact"), "free history is compact (100 sessions)")
        for (f in listOf("OVERVIEW", "TIME_SERIES_DAILY_ADJUSTED", "NEWS_SENTIMENT", "INDEX_DATA", "REALTIME_BULK_QUOTES"))
            assertFalse(AlphaVantageFunctions.usable(f, premium = false), f)
        for (f in AlphaVantageSmokeTest.DEFAULT_FUNCTIONS + AlphaVantageSmokeTest.OPTIONAL_FUNCTIONS) assertTrue(AlphaVantageFunctions.usable(f, premium = false), f)
    }

    @Test fun theFreeDailyBudgetIsExactCappedAndEnforced() = runBlocking<Unit> {
        // Exact: not halved by a 0.5 safety margin; capped at the published 25.
        val margin = ProviderBudgetConfig.fromEnvironment { mapOf("PROVIDER_SAFETY_MARGIN" to "0.5")[it] }
        assertEquals(20L, margin.local(margin.limits.getValue("alphavantage")).dailyLimit)
        assertEquals(25L, ProviderBudgetConfig.fromEnvironment { mapOf("ALPHA_VANTAGE_DAILY_BUDGET" to "100")[it] }.limits.getValue("alphavantage").dailyLimit)
        assertEquals(10L, ProviderBudgetConfig.fromEnvironment { mapOf("ALPHA_VANTAGE_DAILY_BUDGET" to "10")[it] }.limits.getValue("alphavantage").dailyLimit)
        val av = AlphaVantageClientTest.Av { 0 }; av.bodies["GLOBAL_QUOTE"] = AvFixtures.quote("X", "1", "2026-10-16", "1")
        var t = 0L
        val guard = ProviderGuard(margin, now = { t }, meter = ProviderUsageMeter(), sleep = { t += it })
        val c = AlphaVantageClient(av.http, AlphaVantageConfig(secret, true, null, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet()), sessions, clock,
            CompanyFinancialCache(capacity = 64))
        val results = withContext(guard) { (1..30).map { i -> t += 60_000; runCatching { c.quote("S$i") } } }
        assertEquals(20, av.n("GLOBAL_QUOTE"))
        assertTrue(results.drop(20).all { (it.exceptionOrNull() as? StockProviderException)?.dailyLimitReached == true }, "refused locally, never a fallback")
    }

    @Test fun startupRulesForFreeAndPremium() {
        val base = mapOf("FMP_API_KEY" to "t", "FINNHUB_API_KEY" to "t", "ALPHA_VANTAGE_ENABLED" to "true", "ALPHA_VANTAGE_API_KEY" to secret)
        fun r(vararg kv: Pair<String, String>) = StartupConfiguration.check(DataMode.REAL) { (base + kv.toMap())[it] }
        assertTrue(r("ALPHA_VANTAGE_ENTITLEMENT" to "gold").errors.any { "ALPHA_VANTAGE_ENTITLEMENT must be free or premium" in it })
        assertTrue(r("ALPHA_VANTAGE_DAILY_BUDGET" to "26").errors.any { "can't exceed 25" in it })
        assertTrue(r("ALPHA_VANTAGE_DAILY_BUDGET" to "20").errors.isEmpty())
        assertTrue(r("ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE" to "75").warnings.any { "ignored on the free entitlement" in it })
        assertTrue(r("ALPHA_VANTAGE_ENTITLEMENT" to "premium").errors.any { "requires ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE" in it })
        assertTrue(r("ALPHA_VANTAGE_PERSONAL_CLIENTS" to "loopback,192.168.1.20").errors.any { "accepts only loopback" in it })
        assertTrue(r("ALPHA_VANTAGE_PERSONAL_CLIENTS" to "loopback").errors.isEmpty())
        assertTrue((r().errors + r().warnings).none { secret in it })
        assertFalse(MarketDataAccessGate.matches("192.168.1.20", "192.168.1.20"), "only loopback ever matches")
        assertTrue(MarketDataAccessGate.matches("loopback", "127.0.0.1"))
    }

    @Test fun theSmokeTestIsOptInBoundedAndStopsAtTheFirstRefusal() = runBlocking<Unit> {
        assertEquals("ALPHA_VANTAGE_API_KEY is not set", AlphaVantageSmokeTest.refusal { null })
        val ok = mapOf("ALPHA_VANTAGE_API_KEY" to secret, "ALPHA_VANTAGE_SMOKE_CONFIRM" to AlphaVantageSmokeTest.CONFIRM, "ALPHA_VANTAGE_TERMS_CONFIRMED" to AlphaVantageSmokeTest.TERMS)
        assertNull(AlphaVantageSmokeTest.refusal { ok[it] })
        assertTrue(AlphaVantageSmokeTest.refusal { (ok - "ALPHA_VANTAGE_TERMS_CONFIRMED")[it] }!!.contains("personal, non-commercial"))
        assertTrue(AlphaVantageSmokeTest.refusal { (ok + ("ALPHA_VANTAGE_SMOKE_MAX_REQUESTS" to "11"))[it] }!!.contains("1–10"))

        val av = AlphaVantageClientTest.Av { 0 }
        av.bodies["TIME_SERIES_DAILY"] = AvFixtures.daily("AAPL", listOf("2026-10-15" to "10", "2026-10-16" to "11"))
        av.bodies["GLOBAL_QUOTE"] = AvFixtures.quote("AAPL", "11", "2026-10-16", "10"); av.bodies["SYMBOL_SEARCH"] = AvFixtures.SEARCH.replace("EXMP\"", "AAPL\"")
        av.bodies["INCOME_STATEMENT"] = AvFixtures.INCOME; av.bodies["BALANCE_SHEET"] = AvFixtures.BALANCE; av.bodies["CASH_FLOW"] = AvFixtures.CASH_FLOW
        av.bodies["EARNINGS"] = AvFixtures.EARNINGS; av.bodies["SPLITS"] = AvFixtures.SPLITS
        fun client() = AlphaVantageClient(av.http, AlphaVantageConfig(secret, false, null, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet()), sessions, clock,
            CompanyFinancialCache(capacity = 32))
        val all = AlphaVantageSmokeTest.DEFAULT_FUNCTIONS + AlphaVantageSmokeTest.OPTIONAL_FUNCTIONS
        val full = AlphaVantageSmokeTest.run(client(), counting(av), "AAPL", all, 10) {}
        assertEquals(8, av.total); assertTrue(full.steps.all { it.outcome == "OK" }, full.markdown())
        val md = full.markdown()
        assertTrue(secret !in md && "apikey" !in md && "https://" !in md, "no key or URL in the report")
        assertTrue("quarters STANDALONE" in md || "quarters UNVERIFIED" in md)
        // Budget: 3 requests allowed → the rest are skipped, never sent.
        val small = AlphaVantageClientTest.Av { 0 }.also { it.bodies.putAll(av.bodies) }
        val r = AlphaVantageSmokeTest.run(AlphaVantageClient(small.http, AlphaVantageConfig(secret, false, null, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet()), sessions, clock,
            CompanyFinancialCache(capacity = 32)), counting(small), "AAPL", all, 3) {}
        assertEquals(3, small.total); assertEquals(3, r.requestsSent); assertTrue(r.stoppedEarly)
        // First refusal stops the run: no retry, nothing after it is sent.
        val limited = AlphaVantageClientTest.Av { 0 }.also { it.bodies.putAll(av.bodies); it.bodies["GLOBAL_QUOTE"] = AvFixtures.RATE_LIMIT }
        val l = AlphaVantageSmokeTest.run(AlphaVantageClient(limited.http, AlphaVantageConfig(secret, false, null, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet()), sessions, clock,
            CompanyFinancialCache(capacity = 32)), counting(limited), "AAPL", all, 10) {}
        assertEquals(2, limited.total); assertEquals("RATE_LIMITED", l.steps[1].outcome); assertTrue(l.steps.drop(2).all { it.outcome.startsWith("SKIPPED") })
    }

    /** Counts every request the client sends, as the real command does (HttpSend interceptor). */
    private fun counting(av: AlphaVantageClientTest.Av): AtomicInteger {
        val n = AtomicInteger()
        av.http.plugin(HttpSend).intercept { request -> n.incrementAndGet(); execute(request) }
        return n
    }
}
