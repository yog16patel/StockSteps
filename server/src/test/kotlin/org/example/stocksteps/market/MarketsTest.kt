package org.example.stocksteps.market

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.marketsRoutes
import org.example.stocksteps.mockDataSources
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.MarketDataProvider
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockQuoteProviderRepository
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class MarketsTest {
    private val calendar = UsMarketCalendar()
    private fun status(iso: String) = calendar.session(Instant.parse(iso))

    // --- Market session ---

    @Test fun regularPremarketAfterHoursAndOvernight() {
        assertEquals(MarketSessionStatus.OPEN, status("2026-10-07T14:00:00Z").status, "10:00 EDT")
        assertEquals(MarketSessionStatus.PRE_MARKET, status("2026-10-07T12:00:00Z").status, "08:00 EDT")
        assertEquals(MarketSessionStatus.AFTER_HOURS, status("2026-10-07T21:15:00Z").status, "17:15 EDT")
        assertEquals(MarketSessionStatus.CLOSED, status("2026-10-08T02:00:00Z").status, "22:00 EDT")
        val open = status("2026-10-07T14:00:00Z")
        assertEquals("09:30", open.opensAt); assertEquals("16:00", open.closesAt); assertNull(open.nextOpen)
    }

    @Test fun weekendsAndHolidaysPointToTheNextOpen() {
        val saturday = status("2026-10-10T15:00:00Z")
        assertEquals(MarketSessionStatus.WEEKEND, saturday.status)
        assertEquals("2026-10-12T13:30:00Z", saturday.nextOpen, "Monday 09:30 EDT")
        val thanksgiving = status("2026-11-26T16:00:00Z")
        assertEquals(MarketSessionStatus.HOLIDAY, thanksgiving.status)
        assertEquals("Thanksgiving Day", thanksgiving.holiday)
        assertEquals(MarketSessionStatus.HOLIDAY, status("2026-04-03T15:00:00Z").status, "Good Friday 2026")
        assertEquals("Independence Day", calendar.holiday(LocalDate.parse("2026-07-03")), "July 4 on Saturday is observed Friday")
        assertEquals("Juneteenth", calendar.holiday(LocalDate.parse("2027-06-18")), "Saturday Juneteenth observed Friday")
        assertEquals("Christmas Day", calendar.holiday(LocalDate.parse("2022-12-26")), "Sunday Christmas observed Monday")
        assertNull(calendar.holiday(LocalDate.parse("2021-12-31")), "Saturday New Year's Day isn't observed")
        assertEquals("New Year's Day", calendar.holiday(LocalDate.parse("2023-01-02")))
    }

    @Test fun earlyClosesAndDaylightSavingTime() {
        val blackFriday = status("2026-11-27T18:30:00Z") // 13:30 EST
        assertTrue(blackFriday.earlyClose)
        assertEquals("13:00", blackFriday.closesAt)
        assertEquals(MarketSessionStatus.AFTER_HOURS, blackFriday.status)
        assertEquals(MarketSessionStatus.CLOSED, status("2026-11-27T22:30:00Z").status, "after-hours end at 17:00 on early-close days")
        assertTrue(calendar.isEarlyClose(LocalDate.parse("2026-12-24")))
        // 14:00 UTC is 09:00 EST (pre-market) in January but 10:00 EDT (open) in July.
        assertEquals(MarketSessionStatus.PRE_MARKET, status("2026-01-14T14:00:00Z").status)
        assertEquals(MarketSessionStatus.OPEN, status("2026-07-14T14:00:00Z").status)
        // Day of the spring-forward switch (Monday after): 13:30 UTC is the open.
        assertEquals(MarketSessionStatus.OPEN, status("2026-03-09T13:30:00Z").status)
    }

    // --- Movers ---

    private fun mover(symbol: String, change: Double?, price: Double? = 10.0, volume: Double? = null, name: String? = "Name $symbol", exchange: String? = "NASDAQ") =
        MarketMover(symbol, name, price, null, change, exchange, volume)

    @Test fun moverListsAreCleanedOrderedAndBounded() {
        val raw = listOf(
            mover("AAA", 5.0), mover("bbb", 25.0), mover("AAA", 9.0), mover("CCC", 3.0, price = null), mover("DDD", 4.0, price = 0.0),
            mover("EEE", 5_000.0), mover("FFF", 2.0, exchange = "TSX"), mover("bad symbol!", 8.0), mover("GGG", -3.0), mover("HHH", 0.0, name = "  ")
        )
        assertEquals(listOf("BBB", "AAA"), MoverRules.gainers(raw).map { it.symbol }, "dupes keep the first, invalid/suspicious/foreign dropped")
        assertEquals(listOf("GGG"), MoverRules.losers(raw).map { it.symbol })
        assertNull(MoverRules.clean(raw).first { it.symbol == "HHH" }.name, "blank names become missing, not empty text")
        assertEquals(10, MoverRules.gainers((1..30).map { mover("S$it", it.toDouble()) }).size)
        assertTrue(MoverRules.gainers(emptyList()).isEmpty())
        val active = MoverRules.mostActive(listOf(mover("A", 1.0, volume = 10.0), mover("B", 1.0, volume = null), mover("C", -1.0, volume = 99.0)))
        assertEquals(listOf("C", "A", "B"), active.map { it.symbol }, "by volume; unknown volume last")
    }

    // --- Service ---

    private class FakeMovers(var gainers: List<MarketMover> = emptyList(), var fail: Exception? = null) : MarketDataProvider {
        val calls = AtomicInteger()
        override suspend fun getMarketIndices() = emptyList<MarketIndex>()
        override suspend fun getGainers(): List<MarketMover> { calls.incrementAndGet(); fail?.let { throw it }; delay(20); return gainers }
        override suspend fun getLosers() = listOf(MarketMover("LOSE", "Loser", 5.0, -1.0, -10.0, "NYSE"))
        override suspend fun getMostActive() = listOf(MarketMover("BUSY", "Busy", 5.0, 0.1, 2.0, "NYSE"), MarketMover("QUIET", "Quiet", 5.0, 0.0, 0.0, "NYSE"))
        override suspend fun getMarketStatus() = MarketStatus.UNKNOWN
    }

    private class FakeQuotes(val quotes: Map<String, StockQuote>, val slow: Set<String> = emptySet()) : StockQuoteProviderRepository {
        override suspend fun getQuote(symbol: String): StockQuote? { if (symbol in slow) delay(5_000); return quotes[symbol] }
    }

    private fun q(symbol: String, price: Double, previous: Double?, ts: Long = 1_791_403_200, volume: Long? = null) =
        StockQuote(symbol, symbol, price, null, null, null, null, previousClose = previous, timestamp = ts, volume = volume)

    private val noIndices = object : IndexDataSource {
        override suspend fun indexQuote(symbol: String): StockQuote? = null
        override suspend fun indexHistory(symbol: String) = emptyList<PricePoint>()
    }
    private val emptyNews = NewsService(object : NewsProviderRepository { override suspend fun getNews(page: Int, limit: Int) = emptyList<NewsArticle>() })
    private val labels = MarketsSourceLabels("Test universe", "Test source", sampleData = false, notice = "Delayed")
    private val at = Clock.fixed(Instant.parse("2026-10-07T21:15:00Z"), ZoneOffset.UTC)

    private fun service(movers: MarketDataProvider = FakeMovers(listOf(mover("WIN", 12.0))), quotes: Map<String, StockQuote> = emptyMap(),
                        indices: IndexDataSource = noIndices, slow: Set<String> = emptySet(), timeout: Long = 8_000) =
        MarketsService(movers, FakeQuotes(quotes, slow), indices, emptyNews, labels, clock = at, callTimeoutMillis = timeout)

    @Test fun indicesUseTheIndexThenALabelledProxyThenUnavailable() = runBlocking {
        val indices = object : IndexDataSource {
            override suspend fun indexQuote(symbol: String) = if (symbol == "^GSPC") q("^GSPC", 7772.36, 7790.95) else if (symbol == "^GSPTSE") q("^GSPTSE", 30544.18, 30449.76) else null
            override suspend fun indexHistory(symbol: String) =
                if (symbol == "^GSPC") listOf(PricePoint("2026-10-06", 7790.95), PricePoint("2026-10-07", 7772.36)) else listOf(PricePoint("2026-10-01", 1.0), PricePoint("2026-10-02", 2.0))
        }
        val overview = service(indices = indices, quotes = mapOf("DIA" to q("DIA", 511.02, 514.56))).overview()
        val byId = overview.indices.associateBy { it.id }
        val sp = byId.getValue("SP500")
        assertFalse(sp.isProxy); assertEquals("points", sp.unit); assertEquals("^GSPC", sp.symbol)
        assertEquals((7772.36 / 7790.95 - 1) * 100, sp.changePercent!!, 1e-9)
        assertEquals(listOf(7790.95, 7772.36), sp.trend)
        assertEquals("2026-10-07T20:00:00Z", sp.asOf)
        val dow = byId.getValue("DOW")
        assertTrue(dow.isProxy); assertEquals("DIA", dow.symbol); assertEquals("USD", dow.unit)
        assertTrue(dow.proxyDescription!!.contains("not the index level"))
        val tsx = byId.getValue("TSX")
        assertEquals("CAD", tsx.currency); assertTrue(tsx.trend.isEmpty(), "history from another session is dropped, not mixed")
        val nasdaq = byId.getValue("NASDAQ_COMPOSITE")
        assertNull(nasdaq.value); assertNotNull(nasdaq.error); assertEquals("Nasdaq Composite", nasdaq.name, "never relabelled as Nasdaq-100")
    }

    @Test fun sectorsAreEtfProxiesSortedAndAlignedToOneSession() = runBlocking {
        val quotes = mapOf(
            "XLK" to q("XLK", 102.0, 100.0), "XLE" to q("XLE", 98.0, 100.0), "XLV" to q("XLV", 101.0, 100.0),
            "XLF" to q("XLF", 50.0, 50.0, ts = 1_791_316_800) // previous session: left out
        )
        val sectors = service(quotes = quotes).overview().sectors
        assertTrue(sectors.isProxy); assertTrue(sectors.methodology.contains("ETF proxies"))
        assertEquals(listOf("Technology", "Healthcare", "Energy"), sectors.items.map { it.sector })
        assertEquals(2.0, sectors.items.first().changePercent!!, 1e-9)
        assertEquals(-2.0, sectors.items.last().changePercent!!, 1e-9)
    }

    @Test fun partialFailuresStayInTheirSectionAndAreCachedWithCooldown() = runBlocking {
        val movers = FakeMovers(fail = StockProviderException(StockProviderException.Failure.RATE_LIMITED, 429))
        val service = service(movers = movers)
        val first = service.overview()
        assertTrue(first.gainers.items.isEmpty())
        assertEquals("PROVIDER_RATE_LIMITED", first.errors.single { it.section == "gainers" }.error.code)
        assertEquals(listOf("LOSE"), first.losers.items.map { it.symbol }, "other sections still load")
        assertTrue(first.errors.any { it.section == "sectors" }, "no sector quotes: section unavailable, not zero")
        assertEquals("Test universe", first.gainers.universe)
        assertEquals("2026-10-07T21:15:00Z", first.generatedAt)
        assertEquals(MarketSessionStatus.AFTER_HOURS, first.session.status)
        service.overview()
        assertEquals(1, movers.calls.get(), "the cached overview and the rate-limit cooldown avoid repeat calls")
    }

    @Test fun concurrentRequestsShareOneLoadAndSlowCallsTimeOut() = runBlocking {
        val movers = FakeMovers(listOf(mover("WIN", 12.0)))
        val service = service(movers = movers, quotes = mapOf("BUSY" to q("BUSY", 5.0, 4.9, volume = 9_000_000)), slow = setOf("XLK"), timeout = 200)
        val results = coroutineScope { (1..8).map { async { service.overview() } }.awaitAll() }
        assertEquals(1, movers.calls.get())
        assertEquals(1L, service.usage.overviewBuilds.get())
        assertTrue(results.all { it.gainers.items.single().symbol == "WIN" })
        assertEquals(listOf("BUSY", "QUIET"), results.first().mostActive.items.map { it.symbol })
        assertEquals(9_000_000.0, results.first().mostActive.items.first().volume, "volume comes from the stock's own quote")
        assertTrue(service.usage.providerFailures.get() >= 1, "the slow sector quote timed out")
    }

    // --- MOCK ---

    @Test fun mockOverviewServesFixturesAtTheCaptureTime() = testApplication {
        val sources = mockDataSources()
        assertIs<FixtureMarketDataSource>(sources.indexData)
        assertTrue(sources.marketsLabels.sampleData)
        val fixtures = FixtureMarketDataSource(sampleFallback = true)
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing {
                marketsRoutes(MarketsService(fixtures, fixtures, fixtures, NewsService(fixtures), sources.marketsLabels, clock = sources.marketClock))
            }
        }
        val response = client.get("/api/v1/markets/overview")
        assertEquals(HttpStatusCode.OK, response.status)
        val overview = Json { ignoreUnknownKeys = true }.decodeFromString(MarketsOverview.serializer(), response.bodyAsText())
        assertTrue(overview.sampleData && overview.dataNotice.startsWith("Sample data captured"))
        assertEquals(MarketSessionStatus.AFTER_HOURS, overview.session.status, "evaluated at the fixture capture time, not now")
        assertEquals(listOf("SP500", "NASDAQ_COMPOSITE", "DOW", "TSX"), overview.indices.map { it.id })
        assertTrue(overview.indices.none { it.isProxy }, "MOCK serves index levels")
        assertEquals("CAD", overview.indices.last().currency)
        assertTrue(overview.gainers.items.zipWithNext().all { (a, b) -> a.changePercent!! >= b.changePercent!! })
        assertTrue(overview.losers.items.all { it.changePercent!! < 0 })
        assertEquals(11, overview.sectors.items.size)
        assertTrue(overview.mostActive.items.all { it.volume != null })
        assertTrue(overview.news.isNotEmpty())
        assertTrue(overview.errors.isEmpty(), overview.errors.toString())
    }
}
