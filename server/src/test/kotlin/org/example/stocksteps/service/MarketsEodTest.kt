package org.example.stocksteps.service

import kotlinx.coroutines.*
import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repositoryImpl.FmpMarketDataProvider
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Phase 7B: EOD-first Markets — latest completed-session closes and the change from the previous trading day's close, exchange
 * calendars, session-aware close lifetimes, sectors and index fall-backs without quotes. REAL adapters on a MockEngine, fake clocks.
 */
class MarketsEodTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
        fun at(iso: String) { instant = Instant.parse(iso) }
        fun advanceMinutes(m: Long) { instant = instant.plusSeconds(m * 60) }
    }

    private fun pts(vararg rows: Pair<String, Double>) = rows.map { PricePoint(it.first, it.second) }
    private fun change(symbol: String, at: String, vararg rows: Pair<String, Double>) =
        EodChange.of(symbol, pts(*rows), EodSessions(), Instant.parse(at))

    // --- Close pairs and calendars ---

    @Test fun twoConsecutiveTradingDayCloses() {
        // Fri 2026-10-16 18:00 ET: the close (16:00) is expected after the 60-min publication delay.
        val c = assertNotNull(change("XLK", "2026-10-16T22:00:00Z", "2026-10-15" to 100.0, "2026-10-16" to 102.0))
        assertEquals(102.0, c.close); assertEquals(LocalDate.parse("2026-10-16"), c.closeDate)
        assertEquals(LocalDate.parse("2026-10-15"), c.previousCloseDate)
        assertEquals(2.0, c.change, 1e-9); assertEquals(2.0, c.changePercent, 1e-9)
        assertEquals(Instant.parse("2026-10-16T20:00:00Z"), c.closedAt); assertFalse(c.stale)
    }

    @Test fun weekendMondayAndSameDayPartialRows() {
        // Saturday: Friday's close is the latest session.
        assertEquals(LocalDate.parse("2026-10-16"), change("XLK", "2026-10-17T15:00:00Z", "2026-10-15" to 100.0, "2026-10-16" to 101.0)?.closeDate)
        // Monday after Friday: Friday is the previous trading day.
        val monday = assertNotNull(change("XLK", "2026-10-19T21:30:00Z", "2026-10-16" to 100.0, "2026-10-19" to 99.0))
        assertEquals(LocalDate.parse("2026-10-16"), monday.previousCloseDate); assertEquals(-1.0, monday.changePercent, 1e-9)
        // During Monday's session a row dated today isn't a close yet: Friday vs Thursday is shown, labelled by its date.
        val during = assertNotNull(change("XLK", "2026-10-19T15:00:00Z", "2026-10-15" to 98.0, "2026-10-16" to 100.0, "2026-10-19" to 120.0))
        assertEquals(LocalDate.parse("2026-10-16"), during.closeDate); assertFalse(during.stale)
    }

    @Test fun holidaysAndEarlyCloses() {
        // Thanksgiving 2026-11-26: Wednesday is the latest session; Friday is an early close (13:00 ET) compared with Wednesday.
        assertEquals(LocalDate.parse("2026-11-25"), change("XLK", "2026-11-26T20:00:00Z", "2026-11-24" to 100.0, "2026-11-25" to 101.0)?.closeDate)
        val friday = assertNotNull(change("XLK", "2026-11-27T19:30:00Z", "2026-11-25" to 101.0, "2026-11-27" to 102.0))
        assertEquals(LocalDate.parse("2026-11-25"), friday.previousCloseDate)
        assertEquals(Instant.parse("2026-11-27T18:00:00Z"), friday.closedAt, "early close at 13:00 ET")
        // TSX: Thanksgiving (Canada) Monday 2026-10-12 is a TSX holiday but a NYSE trading day.
        assertEquals(LocalDate.parse("2026-10-09"), change("XIC.TO", "2026-10-13T21:30:00Z", "2026-10-09" to 40.0, "2026-10-13" to 41.0)?.previousCloseDate)
        assertNull(change("SPY", "2026-10-13T21:30:00Z", "2026-10-09" to 40.0, "2026-10-13" to 41.0), "NYSE traded on Oct 12: not consecutive")
    }

    @Test fun missingZeroInvalidAndDuplicateClosesAreNeverUsed() {
        val at = "2026-10-16T22:00:00Z"
        assertNull(change("XLK", at, "2026-10-14" to 100.0, "2026-10-16" to 102.0), "previous trading day missing")
        val late = assertNotNull(change("XLK", at, "2026-10-14" to 100.0, "2026-10-15" to 101.0), "latest close missing: the older pair, stale")
        assertTrue(late.stale); assertEquals(LocalDate.parse("2026-10-15"), late.closeDate)
        assertNull(change("XLK", at, "2026-10-15" to 0.0, "2026-10-16" to 102.0), "zero previous close")
        assertNull(change("XLK", at, "2026-10-15" to Double.POSITIVE_INFINITY, "2026-10-16" to 102.0))
        assertEquals(LocalDate.parse("2026-10-15"), change("XLK", at, "2026-10-14" to 99.0, "2026-10-15" to 100.0, "2026-10-16" to Double.NaN)?.closeDate)
        assertNull(change("XLK", at, "2026-10-16" to 102.0, "2026-10-16" to 102.0), "one day twice is not a change")
        assertEquals(0.0, change("XLK", at, "2026-10-15" to 100.0, "2026-10-16" to 100.0)?.changePercent, "equal closes on two days are a real 0 %")
        assertNull(change("BMW.DE", at, "2026-10-15" to 100.0, "2026-10-16" to 101.0), "no calendar for other exchanges")
    }

    // --- Session-aware close lifetimes (PriceChartService) ---

    private class Series(var rows: List<PricePoint>) : PriceHistoryProvider {
        val calls = AtomicInteger()
        override suspend fun getDailyCloses(symbol: String): List<PricePoint> { calls.incrementAndGet(); delay(5); return rows }
        override suspend fun getIntradaySparkline(symbol: String) = throw StockProviderException(StockProviderException.Failure.UNAVAILABLE)
        override suspend fun getIntradayPoints(symbol: String) = emptyList<PricePoint>()
    }

    @Test fun closesLiveUntilTheNextExpectedCloseAndALateCloseIsRetried() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-10-16T21:30:00Z"))   // Fri 17:30 ET: Friday's close is expected
        val provider = Series(pts("2026-10-14" to 99.0, "2026-10-15" to 100.0))
        val charts = PriceChartService(provider, CompanyFinancialCache(capacity = 16, now = { clock.millis() }), eod = EodSessions(clock))
        charts.getDailyCloses("XLK"); assertEquals(1, provider.calls.get())
        clock.advanceMinutes(20); charts.getDailyCloses("XLK"); assertEquals(1, provider.calls.get(), "late close: retried after 30 min, not sooner")
        provider.rows = pts("2026-10-15" to 100.0, "2026-10-16" to 101.0)
        clock.advanceMinutes(11); charts.getDailyCloses("XLK"); assertEquals(2, provider.calls.get(), "the new close is picked up")
        // Now complete: kept over the weekend until Monday's expected close (17:00 ET = 21:00Z).
        repeat(6) { clock.advanceMinutes(600); charts.getDailyCloses("XLK") }   // to Sat/Sun
        assertEquals(2, provider.calls.get(), "no refetch over the weekend")
        clock.at("2026-10-19T21:01:00Z"); charts.getDailyCloses("XLK"); assertEquals(3, provider.calls.get(), "refreshed after Monday's close is expected")
        assertEquals(EodSessions.MAX_TTL, EodSessions(clock).ttl("XLK", pts("2026-10-19" to 1.0), 1L, Instant.parse("2026-12-24T20:00:00Z")).coerceAtLeast(EodSessions.MAX_TTL),
            "bounded over long holiday weekends")
    }

    @Test fun concurrentReadsShareOneLoad() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-10-16T22:00:00Z"))
        val provider = Series(pts("2026-10-15" to 100.0, "2026-10-16" to 101.0))
        val charts = PriceChartService(provider, CompanyFinancialCache(capacity = 16, now = { clock.millis() }), eod = EodSessions(clock))
        (1..20).map { async(Dispatchers.Default) { charts.getDailyCloses("XLK") } }.awaitAll()
        assertEquals(1, provider.calls.get())
    }

    // --- Markets on EOD (REAL adapters, MockEngine) ---

    private val emptyNews = NewsService(object : NewsProviderRepository { override suspend fun getNews(page: Int, limit: Int) = emptyList<NewsArticle>() })
    private val up = FmpMock(latency = 1)
    private val clock = MutableClock(Instant.parse("2026-10-16T22:00:00Z"))   // Fri 18:00 ET, closed
    private val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { clock.millis() }) { LocalDate.parse("2026-10-16") }
    private val charts = PriceChartService(FmpPriceHistoryProvider(up.client, "k"), CompanyFinancialCache(capacity = 64, now = { clock.millis() }), eod = EodSessions(clock))
    private fun eodBody(vararg rows: Pair<String, Double>) = rows.joinToString(",", "[", "]") { """{"date":"${it.first}","price":${it.second}}""" }
    private fun serveCloses(symbol: String, previous: Double, latest: Double) {
        up.bodies["historical-price-eod/light|$symbol"] = eodBody("2026-10-14" to previous - 1, "2026-10-15" to previous, "2026-10-16" to latest)
    }
    private fun markets(eodFirst: Boolean = true) = MarketsService(FmpMarketDataProvider(up.client, "k"), fmp,
        object : IndexDataSource {
            override suspend fun indexQuote(symbol: String) = fmp.getQuote(symbol)
            override suspend fun indexHistory(symbol: String) = charts.getDailyCloses(symbol)
        }, emptyNews, MarketsSourceLabels("u", "q", sampleData = false, notice = "Delayed"), clock = clock,
        cache = CompanyFinancialCache(capacity = 128, now = { clock.millis() }),
        dailyCloses = if (eodFirst) charts::getDailyCloses else null, eod = EodSessions(clock))
    private fun eodCalls() = up.n("historical-price-eod/light")
    private fun sectorQuoteCalls() = up.log.count { it.startsWith("quote|XL") }

    @BeforeTest fun sectorsAndIndices() {
        MarketsService.SECTORS.forEachIndexed { i, (_, symbol, _) -> serveCloses(symbol, 100.0, 100.0 + i - 5) }
        // Index quotes refused (as on the current plan); index EOD available for three, proxies for the TSX.
        MarketsService.INDICES.forEach { up.symbolStatus["quote|${it.symbol}"] = 402 }
        listOf("^GSPC", "^IXIC", "^DJI").forEach { serveCloses(it, 6000.0, 6060.0) }
        up.symbolStatus["historical-price-eod/light|^GSPTSE"] = 402
        serveCloses("XIC.TO", 40.0, 39.6)
    }

    @Test fun sectorsComeFromClosesWithTheirDatesAndNoQuotes() = runBlocking {
        val o = markets().overview()
        assertEquals(0, sectorQuoteCalls(), "no sector quote")
        assertEquals(11, o.sectors.items.size)
        assertEquals(MarketsService.SECTORS.size, o.sectors.items.map { it.symbol }.toSet().size)
        val xlk = o.sectors.items.single { it.symbol == "XLK" }
        assertEquals(-5.0, xlk.changePercent!!, 1e-9); assertEquals("2026-10-16", xlk.closeDate); assertEquals("2026-10-15", xlk.previousCloseDate)
        assertEquals(o.sectors.items.sortedByDescending { it.changePercent }, o.sectors.items, "ordering kept (by change)")
        assertEquals("2026-10-16", o.sectors.closeDate); assertTrue(o.sectors.unavailable.isEmpty())
        assertEquals("Change from the previous close", o.sectors.period)
        assertFalse(o.sampleData, "REAL is never sample data")
    }

    @Test fun indicesUseTheirOwnCloseThenALabelledProxyClose() = runBlocking {
        val o = markets().overview()
        val sp = o.indices.single { it.id == "SP500" }
        assertFalse(sp.isProxy); assertEquals("^GSPC", sp.symbol); assertEquals("points", sp.unit)
        assertEquals(6060.0, sp.value); assertEquals(1.0, sp.changePercent!!, 1e-9)
        assertEquals("2026-10-16", sp.closeDate); assertEquals("2026-10-16T20:00:00Z", sp.asOf); assertTrue(sp.trend.size >= 2)
        val tsx = o.indices.single { it.id == "TSX" }
        assertTrue(tsx.isProxy); assertEquals("XIC.TO", tsx.symbol); assertEquals("CAD", tsx.unit, "fund price in its currency, not points")
        assertTrue(tsx.proxyDescription!!.contains("not the index level")); assertTrue(tsx.trend.isEmpty(), "no fund trend drawn as the index")
        assertEquals(-1.0, tsx.changePercent!!, 1e-9)
        assertEquals(0, up.log.count { it.startsWith("quote|") && it.split("|")[1] in setOf("SPY", "ONEQ", "DIA", "XIC.TO") }, "no proxy quotes")
    }

    @Test fun anIndexWithoutAnyCloseIsUnavailableAndOtherSectionsStay() = runBlocking {
        up.symbolStatus["historical-price-eod/light|XIC.TO"] = 402
        val o = markets().overview()
        val tsx = o.indices.single { it.id == "TSX" }
        assertNull(tsx.value); assertEquals("INDEX_UNAVAILABLE", tsx.error?.code)
        assertEquals(11, o.sectors.items.size); assertEquals(3, o.indices.count { it.value != null })
    }

    @Test fun partialSectorFailureIsReportedPerSector() = runBlocking {
        up.symbolStatus["historical-price-eod/light|XLRE"] = 402
        up.bodies["historical-price-eod/light|XLU"] = eodBody("2026-10-14" to 50.0, "2026-10-15" to 51.0)   // stale: no Friday close
        val o = markets().overview()
        assertEquals(9, o.sectors.items.size, "only funds on the newest close date are compared")
        assertEquals(setOf("Real Estate", "Utilities"), o.sectors.unavailable.toSet())
        assertTrue(o.errors.none { it.section == "sectors" })
    }

    @Test fun allSectorsUnavailableKeepTheProviderReason() = runBlocking {
        for ((status, code) in listOf(402 to ProviderErrors.RESTRICTED, 429 to "PROVIDER_RATE_LIMITED")) {
            val w = MarketsEodTest().also { it.sectorsAndIndices() }
            MarketsService.SECTORS.forEach { (_, s, _) -> w.up.symbolStatus["historical-price-eod/light|$s"] = status }
            val o = w.markets().overview()
            assertTrue(o.sectors.items.isEmpty())
            assertEquals(code, o.errors.single { it.section == "sectors" }.error.code)
            assertEquals(4, o.indices.count { it.value != null }, "indices unaffected (three own closes, the TSX by its labelled proxy)")
        }
        val w = MarketsEodTest().also { it.sectorsAndIndices() }
        MarketsService.SECTORS.forEach { (_, s, _) -> w.up.bodies["historical-price-eod/light|$s"] = "[]" }
        assertEquals("PROVIDER_UNAVAILABLE", w.markets().overview().errors.single { it.section == "sectors" }.error.code, "no data isn't a restriction")
    }

    @Test fun aConfiguredDailyAllowanceIsReportedForTheSection() = runBlocking {
        val guard = ProviderGuard(ProviderBudgetConfig(mapOf("fmp" to ProviderLimits(600, 600, 50, dailyLimit = 3)), safetyMargin = 1.0),
            { clock.millis() }, ProviderUsageMeter(), sleep = { })
        val o = withContext(guard) { markets().overview() }
        assertTrue(up.total <= 3, "beyond the allowance nothing is sent")
        assertTrue(o.errors.any { it.section == "sectors" && it.error.code == ProviderErrors.DAILY_LIMIT } || o.sectors.unavailable.size >= 8,
            "what loaded is shown; the rest is unavailable, never estimated")
    }

    @Test fun mostActiveRowsKeepTheirOwnFieldsWithoutQuoteEnrichment() = runBlocking {
        up.bodies["most-actives"] = """[{"symbol":"BUSY","name":"Busy Co","price":5.0,"change":0.1,"changesPercentage":2.0,"exchange":"NYSE"},
            {"symbol":"LOUD","name":"Loud Co","price":7.0,"change":-0.1,"changesPercentage":-1.0,"exchange":"NASDAQ","volume":900000}]"""
        val o = markets().overview()
        assertEquals(listOf("LOUD", "BUSY"), o.mostActive.items.map { it.symbol }, "known volume first, provider order after")
        assertNull(o.mostActive.items.single { it.symbol == "BUSY" }.volume, "missing volume stays missing")
        assertEquals(0, up.log.count { it.startsWith("quote|BUSY") }, "no quote per row")
    }

    @Test fun repeatedOpensAndAWeekendReuseTheCloses() = runBlocking {
        val m = markets()
        m.overview(); val cold = eodCalls()
        assertEquals(11 + 4 + 1, cold, "11 sector funds + 3 indices + the TSX index (refused) + its proxy")
        repeat(10) { clock.advanceMinutes(5); m.overview() }     // past the 5-min overview cache each time, within the hour
        assertEquals(cold, eodCalls(), "10 more opens: no new close requests")
        clock.at("2026-10-18T15:00:00Z"); repeat(5) { m.overview(); clock.advanceMinutes(10) }   // Sunday, five opens within an hour
        assertEquals(cold + 1, eodCalls(), "only the refused TSX index is asked again (its 1 h cooldown had passed); closes are kept")
    }
}
