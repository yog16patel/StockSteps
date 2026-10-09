package org.example.stocksteps.service

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.earnings.DataFreshness
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.repositoryImpl.FmpFundamentalsLoader
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/**
 * Phase 3E: while the provider fails temporarily, the last good statements (≤ 7 days) and estimates (≤ 2 days) are shown
 * labelled STALE with their original retrieval time; access denial, price-sensitive ratios, hard-expired copies and
 * missing copies stay honestly unavailable. Retrieval times never move on cache hits. MockEngine, fake clocks.
 */
class StaleFallbackTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val quote: suspend () -> Pair<StockQuote?, CompanyProfile?> = { null to null }

    private class World {
        val up = FmpMock()
        val ms = AtomicLong(0)
        var wall: Instant = Instant.parse("2026-10-07T18:00:00Z")
        val meter = ProviderUsageMeter()
        val loader = FmpFundamentalsLoader(up.client, "k", CompanyFinancialCache(now = { ms.get() }), { LocalDate.parse("2026-10-07") }, meter = meter, clock = { wall })
        fun advance(millis: Long) { ms.addAndGet(millis); wall = wall.plusMillis(millis) }
        fun fail(status: Int, vararg endpoints: String) = endpoints.forEach { up.status[it] = status }
    }
    private val statements = arrayOf("income-statement", "balance-sheet-statement", "cash-flow-statement", "income-statement-ttm", "cash-flow-statement-ttm",
        "dividends", "shares-float", "ratios", "analyst-estimates")

    @Test fun freshThenCachedWithStableRetrievalTimes() = runBlocking {
        val w = World()
        val first = w.loader.load("AAPL", "annual", quote = quote)
        assertEquals(DataFreshness.FRESH, first.freshness)
        assertEquals("2026-10-07T18:00:00Z", first.retrievedAt)
        w.advance(10 * 60_000L)
        val second = w.loader.load("AAPL", "annual", quote = quote)
        assertEquals(DataFreshness.CACHED, second.freshness)
        assertEquals(first.retrievedAt, second.retrievedAt, "a cache hit doesn't make the data look newer")
    }

    @Test fun temporaryFailuresShowTheLastGoodStatementsLabelledStale() = runBlocking {
        for (status in listOf(429, 500, 503)) {
            val w = World()
            val good = w.loader.load("AAPL", "annual", quote = quote)
            w.advance(25 * hour)                                               // statements (24 h) and TTM statements (6 h) expired
            w.fail(status, *statements)
            val result = w.loader.load("AAPL", "annual", quote = quote)
            assertEquals(DataFreshness.STALE, result.freshness, "$status")
            assertEquals(good.history, result.history, "$status: the same reported rows, nothing invented")
            assertEquals("2026-10-07T18:00:00Z", result.retrievedAt, "$status: dated by the original retrieval")
            assertTrue(result.staleDatasets.containsAll(listOf("annualIncome", "balance", "cashFlow", "incomeTtm", "estimates")), "$status: ${result.staleDatasets}")
            assertEquals(FinancialAvailability.AVAILABLE, result.datasets["annualIncome"])
            assertTrue(w.meter.eventCount("fmp.dataset.staleServed") > 0)
            // The stale answer is cooled down for 30 s: no request storm while the provider is down.
            val failures = w.up.total
            w.advance(10_000); w.loader.load("AAPL", "annual", quote = quote)
            assertEquals(failures, w.up.total)
            // Recovery: fresh data replaces the stale copy.
            w.up.status.clear(); w.advance(31_000)
            assertNotEquals(DataFreshness.STALE, w.loader.load("AAPL", "annual", quote = quote).freshness)
        }
    }

    @Test fun priceSensitiveRatiosAreNeverServedStale() = runBlocking {
        val w = World()
        w.loader.load("AAPL", "annual", quote = quote)
        w.advance(20 * 60_000L)
        w.fail(503, "ratios-ttm", "key-metrics-ttm")
        val result = w.loader.load("AAPL", "annual", quote = quote)
        assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, result.datasets["ratiosTtm"])
        assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, result.datasets["keyMetricsTtm"])
        assertNotEquals(DataFreshness.STALE, result.freshness)
        assertNull(result.valuation.metrics["pe"]?.value, "no stale P/E")
    }

    @Test fun accessDenialNeverServesRestrictedDataOn() = runBlocking {
        val w = World()
        w.loader.load("AAPL", "annual", quote = quote)
        w.advance(25 * hour); w.fail(403, *statements)
        val result = w.loader.load("AAPL", "annual", quote = quote)
        assertNotEquals(DataFreshness.STALE, result.freshness)
        assertTrue(result.history.isEmpty())
        assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, result.datasets["annualIncome"])
    }

    @Test fun hardExpiredAndMissingCopiesStayUnavailable() = runBlocking {
        val old = World()
        old.loader.load("AAPL", "annual", quote = quote)
        old.advance(8 * day); old.fail(503, *statements, "ratios-ttm", "key-metrics-ttm")
        val expired = old.loader.load("AAPL", "annual", quote = quote)
        assertTrue(expired.history.isEmpty(), "older than 7 days isn't shown")
        assertEquals(DataFreshness.UNAVAILABLE, expired.freshness)
        // Estimates have a shorter limit (2 days).
        val est = World()
        est.loader.load("AAPL", "annual", quote = quote)
        est.advance(3 * day); est.fail(503, "analyst-estimates")
        assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, est.loader.load("AAPL", "annual", quote = quote).datasets["estimates"])
        // Never loaded: honest unavailable, no sample values.
        val none = World()
        none.fail(503, *statements, "ratios-ttm", "key-metrics-ttm")
        val empty = none.loader.load("MSFT", "annual", quote = quote)
        assertEquals(DataFreshness.UNAVAILABLE, empty.freshness)
        assertTrue(empty.history.isEmpty())
    }

    @Test fun freshnessFieldsAreAdditiveAndBackwardCompatible() {
        val client = Json { ignoreUnknownKeys = true }
        val legacy = client.decodeFromString(CompanyFundamentals.serializer(), """{"symbol":"AAPL","retrievedAt":"2026-10-07T18:00:00Z"}""")
        assertNull(legacy.freshness); assertTrue(legacy.staleDatasets.isEmpty())
        val encoded = Json.encodeToString(CompanyFundamentals.serializer(), legacy.copy(freshness = DataFreshness.STALE, staleDatasets = listOf("income")))
        assertEquals(DataFreshness.STALE, client.decodeFromString(CompanyFundamentals.serializer(), encoded).freshness)
        // An older client model (no such fields) ignores them: unknown keys are skipped.
        assertEquals("AAPL", client.decodeFromString(LegacyFundamentals.serializer(), encoded).symbol)
    }

    @Test fun comparisonLabelsStaleCompaniesAndKeepsTheirRetrievalDate() = runBlocking {
        val up = FmpMock(); val ms = AtomicLong(0)
        val clock = object : java.time.Clock() {
            override fun getZone() = java.time.ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant(): Instant = Instant.parse("2026-10-07T18:00:00Z").plusMillis(ms.get())
        }
        val fmp = org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { ms.get() }, wallClock = { clock.instant() }) { LocalDate.parse("2026-10-07") }
        val stocks = StockService(fmp, fmp)
        val screener = org.example.stocksteps.screener.ScreenerService({ org.example.stocksteps.screener.UniverseDefinition("t", emptyList()) }, stocks,
            { CompanyFinancialService(fmp).getFundamentals(it, "annual") }, PriceChartService(org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider(up.client, "k")),
            { 0.73 }, clock, sampleData = false, fundamentalsPerHour = 0, fullRecords = false)
        val fresh = screener.compare("AAPL,MSFT")
        assertTrue(fresh.notes.none { "couldn't be reached" in it })
        ms.addAndGet(25 * hour)
        up.status["income-statement"] = 503; up.status["balance-sheet-statement"] = 503; up.status["cash-flow-statement"] = 503
        val stale = screener.compare("AAPL,MSFT")
        assertTrue(stale.notes.any { "AAPL, MSFT: the data provider couldn't be reached" in it }, stale.notes.toString())
        assertEquals(listOf("2026-10-07T18:00:00Z", "2026-10-07T18:00:00Z"), stale.companies.map { it.record?.fundamentalsAsOf }, "dated by the original retrieval")
        assertEquals(fresh.companies.map { it.record?.fundamentalsAsOf }, stale.companies.map { it.record?.fundamentalsAsOf })
        assertEquals(fresh.companies.map { it.annual }, stale.companies.map { it.annual }, "same reported figures, nothing estimated")
    }

    @kotlinx.serialization.Serializable private data class LegacyFundamentals(val symbol: String, val retrievedAt: String? = null)
}
