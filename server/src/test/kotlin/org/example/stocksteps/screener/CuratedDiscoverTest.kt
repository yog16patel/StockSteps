package org.example.stocksteps.screener

import kotlinx.coroutines.*
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.search.SearchRanking
import org.example.stocksteps.service.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Phase 7E: REAL Discover on the curated list — data quality of the list, background enrichment from the shared profile and close
 * caches (bounded, never blocking a response, never repeated for unchanged data), and filters, sorts, text search and pagination over it.
 */
class CuratedDiscoverTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
        fun minutes(m: Long) { instant = instant.plusSeconds(m * 60) }
    }

    // --- The list itself ---

    @Test fun theCuratedListIsAboutOneHundredFiftyValidUniqueCompaniesAcrossAllSectors() {
        val file = CuratedUniverseFile.load()
        assertTrue(file.companies.size in 140..160, "${file.companies.size}")
        assertTrue(file.version.matches(Regex("""\d{4}-\d{2}-\d{2}\.\d+""")))
        val symbol = Regex("[A-Z][A-Z0-9-]{0,9}")
        file.companies.forEach { c ->
            assertTrue(symbol.matches(c.symbol), "invalid ticker ${c.symbol}")
            assertTrue(c.name.isNotBlank() && c.name.trim() == c.name, "name for ${c.symbol}")
            assertTrue(c.exchange == null || c.exchange in setOf("NASDAQ", "NYSE"), "exchange for ${c.symbol}: ${c.exchange}")
        }
        assertEquals(file.companies.size, file.companies.map { SearchRanking.ticker(it.symbol) }.distinct().size, "no duplicate tickers (BRK.B = BRK-B)")
        val sectors = setOf("Technology", "Financial Services", "Healthcare", "Consumer Cyclical", "Consumer Defensive", "Industrials", "Energy",
            "Utilities", "Basic Materials", "Communication Services", "Real Estate")
        assertEquals(sectors, file.companies.mapNotNull { it.sector }.toSet(), "every sector, provider names only")
        assertTrue(file.description.contains("isn't the whole market"), "never presented as the full market")
        assertTrue(file.companies.none { it.symbol.contains('.') }, "provider symbol form (BRK-B)")
    }

    // --- Enrichment ---

    private val clock = MutableClock(Instant.parse("2026-10-16T22:00:00Z"))   // Fri 18:00 ET: Friday's close expected
    private val profileCalls = AtomicInteger(); private val closeCalls = AtomicInteger()
    private val failing = ConcurrentHashMap<String, Exception>()
    private fun closesFor(symbol: String) = listOf(PricePoint("2026-10-15", 100.0 + (symbol.hashCode() and 0x3f)), PricePoint("2026-10-16", 101.0 + (symbol.hashCode() and 0x3f)))

    private fun curated(scope: CoroutineScope, perHour: Int = 60) = CuratedScreenerUniverse(
        profile = { s -> profileCalls.incrementAndGet(); failing[s]?.let { throw it }; CompanyProfile(s, "$s Provider Name", sector = "Technology", industry = "Software", exchange = "NASDAQ",
            marketCap = (1_000 + (s.hashCode() and 0xff)) * 1e9) },
        closes = { s -> closeCalls.incrementAndGet(); failing[s]?.let { throw it }; closesFor(s) },
        eod = EodSessions(clock), clock = clock, scope = scope, perHour = perHour, perDay = null, meter = ProviderUsageMeter())  // Phase 7E behaviour: no daily cap (9B cap: MarketDataPhase9bBenchmarkTest)

    @Test fun firstOpenNeverWaitsAndEnrichmentIsBudgetedAndNotRepeated() = runBlocking<Unit> {
        val u = curated(this)
        val first = u.universe()
        assertTrue(first.entries.all { it.price == null && it.marketCap == null }, "nothing invented while loading")
        u.pending?.join()
        assertEquals(60, profileCalls.get(), "60 companies an hour"); assertEquals(60, closeCalls.get())
        val second = u.universe(); u.pending?.join()
        assertEquals(60, second.entries.count { it.price != null })
        assertEquals(60, profileCalls.get(), "the budget for this hour is used")
        clock.minutes(61); u.universe(); u.pending?.join(); clock.minutes(61); u.universe(); u.pending?.join()
        val all = u.universe(); u.pending?.join()
        assertEquals(u.size, all.entries.count { it.price != null && it.priceDate == "2026-10-16" })
        val calls = profileCalls.get() to closeCalls.get()
        repeat(10) { clock.minutes(5); u.universe(); u.pending?.join() }
        assertEquals(calls, profileCalls.get() to closeCalls.get(), "unchanged data isn't fetched again")
        val e = all.entries.first { it.symbol == "AAPL" }
        assertEquals("AAPL Provider Name", e.name, "provider profile data shown once loaded"); assertEquals("Software", e.industry)
        assertNotNull(e.changePercent)
    }

    @Test fun failuresStayUnavailableAndAreRetriedAfterTheCooldown() = runBlocking<Unit> {
        failing["AAPL"] = StockProviderException(StockProviderException.Failure.UNAVAILABLE, 402)
        failing["MSFT"] = StockProviderException(StockProviderException.Failure.RATE_LIMITED, 429)
        val u = curated(this, perHour = 500)
        u.universe(); u.pending?.join()
        val d = u.universe(); u.pending?.join()
        listOf("AAPL", "MSFT").forEach { s -> val e = d.entries.first { it.symbol == s }; assertNull(e.price); assertNull(e.marketCap); assertEquals(s.let { CuratedUniverseFile.load().companies.first { c -> c.symbol == it }.name }, e.name) }
        val before = profileCalls.get()
        clock.minutes(10); u.universe(); u.pending?.join()
        assertEquals(before, profileCalls.get(), "not retried within 30 minutes")
        failing.clear(); clock.minutes(25); u.universe(); u.pending?.join()
        assertNotNull(u.universe().entries.first { it.symbol == "AAPL" }.price, "recovers after the cooldown")
    }

    // --- Discover on the curated list (REAL adapters, MockEngine) ---

    private class World(scope: CoroutineScope, val clock: MutableClock) {
        val up = FmpMock(latency = 1)
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { clock.millis() }) { LocalDate.parse("2026-10-16") }
        val stocks = StockService(fmp, fmp, CompanyFinancialCache(capacity = 512, now = { clock.millis() }))
        val charts = PriceChartService(FmpPriceHistoryProvider(up.client, "k"), CompanyFinancialCache(capacity = 512, now = { clock.millis() }), eod = EodSessions(clock))
        val universe = CuratedScreenerUniverse(stocks::getProfile, charts::getDailyCloses, EodSessions(clock), clock, scope, perHour = 500, perDay = null, meter = ProviderUsageMeter())
        val financials = CompanyFinancialService(fmp)
        val service = ScreenerService(universe, stocks, { financials.getFundamentals(it, "annual") }, charts, { 0.73 }, clock, sampleData = false,
            fundamentalsPerHour = 0, fullRecords = false, scope = scope, meter = ProviderUsageMeter(), pageQuotes = false,
            pageCache = CompanyFinancialCache(capacity = 64, now = { clock.millis() }))
        init {
            CuratedUniverseFile.load().companies.forEachIndexed { i, c ->
                up.bodies["historical-price-eod/light|${c.symbol}"] = """[{"date":"2026-10-15","price":${50.0 + i}},{"date":"2026-10-16","price":${51.0 + i}}]"""
                // Market cap reported for all but the last ten companies.
                if (i < 145) up.bodies["profile|${c.symbol}"] = """[{"symbol":"${c.symbol}","companyName":"${c.name}","currency":"USD","exchange":"${c.exchange ?: "NYSE"}","sector":"${c.sector}","industry":"Industry $i","marketCap":${(i + 1) * 1e9}}]"""
            }
        }
        suspend fun ready() { service.catalog(); universe.pending?.join() }
        fun calls(path: String) = up.n(path)
    }

    @Test fun discoverWorksWithoutTheScreenerOrQuotesAndFiltersSortsSearchesAndPagesCorrectly() = runBlocking<Unit> {
        val w = World(this, clock)
        w.ready(); w.ready()
        assertEquals(0, w.calls("company-screener"), "no FMP screener"); assertEquals(0, w.calls("quote"), "no quotes")
        val all = w.service.search(ScreenerQuery())
        assertEquals(155, all.total); assertEquals(155, all.universe.priced)
        assertTrue(all.warnings.any { "official closing prices" in it && "Oct 16, 2026" in it })
        assertEquals(all.rows.first().company.metrics["price"]?.basis?.period, "Latest close")
        // Market cap sort: numeric, descending, the ten without a market cap last (by symbol), stable over three pages.
        val q = ScreenerQuery(sort = ScreenerSort(SortField.MARKET_CAP, descending = true), pageSize = 20)
        val pages = mutableListOf<ScreenerResultRow>(); var page = w.service.search(q); pages += page.rows
        while (page.nextCursor != null) { page = w.service.search(q.copy(cursor = page.nextCursor)); pages += page.rows }
        assertEquals(155, pages.size); assertEquals(155, pages.map { it.company.symbol }.distinct().size, "no duplicates across pages")
        val caps = pages.map { it.company.metrics["marketCap"]?.value }
        assertEquals(caps.filterNotNull().sortedDescending(), caps.filterNotNull()); assertTrue(caps.takeLast(10).all { it == null }, "missing last")
        // A market-cap filter leaves out the ten without one and says so; nothing becomes "small".
        val big = w.service.search(ScreenerQuery(ranges = listOf(RangeFilter("marketCap", min = 100e9))))
        assertEquals(10, big.excludedForMissingData); assertTrue(big.rows.all { it.company.metrics["marketCap"]!!.value!! >= 100e9 })
        // Sector filter; price filter on dated closes.
        val energy = w.service.search(ScreenerQuery(choices = listOf(ChoiceFilter(ChoiceField.SECTOR, listOf("Energy"))), pageSize = 50))
        assertEquals(10, energy.total); assertTrue(energy.rows.all { it.company.sector == "Energy" })
        val cheap = w.service.search(ScreenerQuery(ranges = listOf(RangeFilter("price", max = 60.0)), pageSize = 50))
        assertTrue(cheap.rows.all { it.company.price!! <= 60.0 }); assertEquals(0, cheap.excludedForMissingData)
        // Text search: exact ticker first, then prefixes; case-insensitive; no results.
        val v = w.service.search(ScreenerQuery(text = "v", pageSize = 50))
        assertEquals("V", v.rows.first().company.symbol, "exact ticker first")
        assertEquals("AAPL", w.service.search(ScreenerQuery(text = "apple")).rows.first().company.symbol)
        assertEquals("BRK-B", w.service.search(ScreenerQuery(text = "brk.b")).rows.first().company.symbol, "BRK.B finds BRK-B")
        assertEquals(0, w.service.search(ScreenerQuery(text = "zzzz-no-such")).total)
        // P/E needs fundamentals (not loaded on this plan): the filter reports missing data instead of treating it as zero.
        val pe = w.service.search(ScreenerQuery(ranges = listOf(RangeFilter("pe", max = 20.0))))
        assertEquals(0, pe.total); assertFalse(pe.universe.complete)
        assertTrue(pe.warnings.any { "Financial filters were applied to 0 of 155" in it })
        assertEquals(0, w.calls("company-screener")); assertEquals(0, w.calls("quote"), "still no quotes after every search")
        coroutineContext.cancelChildren()
    }

    @Test fun repeatedAndConcurrentOpensShareTheCachesAndAColdOpenSendsNothingSynchronously() = runBlocking<Unit> {
        val w = World(this, clock)
        val cold = w.service.catalog()
        assertEquals(0, cold.universe.priced, "the first open answers before any provider request")
        w.universe.pending?.join()
        val afterFirstPass = w.up.total
        assertEquals(155 * 2, afterFirstPass, "one profile and one close series per company, once")
        (1..10).map { async(Dispatchers.Default) { w.service.search(ScreenerQuery()) } }.awaitAll()
        repeat(10) { w.service.search(ScreenerQuery(sort = ScreenerSort(SortField.NAME))) }
        assertEquals(afterFirstPass, w.up.total, "ten concurrent users and ten more opens: no new upstream request")
        coroutineContext.cancelChildren()
    }
}
