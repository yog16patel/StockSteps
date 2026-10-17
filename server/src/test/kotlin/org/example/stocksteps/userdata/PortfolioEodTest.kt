package org.example.stocksteps.userdata

import kotlinx.coroutines.*
import org.example.stocksteps.model.*
import org.example.stocksteps.portfolio.*
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Phase 7C (REAL path): My Portfolio values holdings at each one's latest valid official close (dated; FX at the close's date) from the
 * shared session-aware close cache. A holding without a usable close stays missing, so the total stays unavailable — never a partial
 * sum or a zero. User-entered transactions are never rewritten. The "Today" line says it needs live prices.
 */
class PortfolioEodTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }
    private val clock = MutableClock(Instant.parse("2026-10-16T22:00:00Z"))   // Fri 18:00 ET: Friday's close expected
    private val store = InMemoryUserDataStore()
    private val ledger = PortfolioService(store, clock::millis)
    private val series = ConcurrentHashMap<String, List<PricePoint>>()
    private val failures = ConcurrentHashMap<String, Exception>()
    private val upstream = AtomicInteger()
    private val provider = object : PriceHistoryProvider {
        override suspend fun getIntradaySparkline(symbol: String) = Sparkline(symbol, emptyList())
        override suspend fun getIntradayPoints(symbol: String) = emptyList<PricePoint>()
        override suspend fun getDailyCloses(symbol: String): List<PricePoint> { upstream.incrementAndGet(); delay(2); failures[symbol]?.let { throw it }; return series[symbol].orEmpty() }
    }
    private val charts = PriceChartService(provider, CompanyFinancialCache(capacity = 64, now = { clock.millis() }), eod = EodSessions(clock))
    private val fxCalls = AtomicInteger()
    private val fx = object : PortfolioFxSource {
        override suspend fun rates(from: String, through: String): Map<String, String> { fxCalls.incrementAndGet(); return mapOf("2026-10-01" to "1.3", "2026-10-15" to "1.38", "2026-10-16" to "1.4") }
    }
    private val fixture = FixtureMarketDataSource(sampleFallback = true)
    private val market = PortfolioMarketService(ledger, provider, WatchMarketData(StockService(fixture, fixture), fixture, NewsService(fixture)), fx, clock,
        dailyCloses = charts::getDailyCloses, eod = EodSessions(clock))

    private fun closes(symbol: String, vararg rows: Pair<String, Double>) { series[symbol] = rows.map { PricePoint(it.first, it.second) } }
    private suspend fun hold(symbol: String, qty: String, price: String) =
        ledger.saveTransaction("alice", PortfolioTransaction("t-$symbol", "a", TransactionType.OPENING_POSITION, "2026-10-01", PortfolioCurrency.USD,
            InstrumentRef(symbol, currency = "USD"), qty, price), false)

    @BeforeTest fun account() = runBlocking<Unit> { ledger.saveAccount("alice", PortfolioAccount("a", "CAD account")) }

    @Test fun completeValuationUsesDatedClosesAndTheLatestFx() = runBlocking {
        hold("AAPL", "2", "100"); closes("AAPL", "2026-10-15" to 110.0, "2026-10-16" to 120.0)
        val r = market.report("alice", "a", "1M", false)
        assertEquals("336", r.summary.totalValue?.let { java.math.BigDecimal(it).stripTrailingZeros().toPlainString() }, "2 × 120 USD × 1.40")
        assertEquals("260", r.summary.investedCost?.let { java.math.BigDecimal(it).stripTrailingZeros().toPlainString() }, "basis at the dated FX 1.30, unchanged")
        assertEquals("76", r.summary.unrealizedGain?.let { java.math.BigDecimal(it).stripTrailingZeros().toPlainString() })
        assertTrue(r.summary.missing.isEmpty())
        assertEquals("2026-10-16", r.pricesAsOf); assertNull(r.quoteAsOf, "closes are never presented as quote times")
        assertNull(r.daily?.gain); assertEquals(PortfolioMarketService.EOD_DAILY_NOTICE, r.daily?.notice, "no past session's move shown as today's")
        assertEquals("100", r.allocations.single().percent?.let { java.math.BigDecimal(it).stripTrailingZeros().toPlainString() })
        assertEquals("100", ledger.get("alice").transactions.single().unitPrice, "user-entered data untouched")
    }

    @Test fun aHoldingWithoutAUsableCloseMakesTheTotalUnavailableNotPartial() = runBlocking {
        hold("AAPL", "2", "100"); closes("AAPL", "2026-10-15" to 110.0, "2026-10-16" to 120.0)
        hold("MSFT", "1", "200")   // no closes
        val r = market.report("alice", "a", "1M", false)
        assertNull(r.summary.totalValue, "never the AAPL-only sum"); assertTrue("MSFT" in r.summary.missing)
        assertNotNull(r.summary.investedCost, "cost basis kept without market data")
        assertTrue(r.allocations.all { it.percent == null }, "no renormalized allocation")
        for (bad in listOf<() -> Unit>({ closes("MSFT", "2026-10-15" to 0.0, "2026-10-16" to Double.NaN) },
                                        { closes("MSFT", "2026-10-02" to 190.0, "2026-10-05" to 191.0) },   // older than five sessions
                                        { failures["MSFT"] = StockProviderException(StockProviderException.Failure.UNAVAILABLE, 402) })) {
            bad(); clock.instant = clock.instant.plusSeconds(3_700)   // past cooldowns / late retry
            assertNull(market.report("alice", "a", "1M", false).summary.totalValue)
        }
    }

    @Test fun aLateCloseIsUsedWithItsDate() = runBlocking {
        hold("AAPL", "1", "100"); closes("AAPL", "2026-10-14" to 105.0, "2026-10-15" to 110.0)
        val r = market.report("alice", "a", "1M", false)
        assertEquals("2026-10-15", r.pricesAsOf)
        assertEquals("154", r.summary.totalValue?.let { java.math.BigDecimal(it).stripTrailingZeros().toPlainString() },
            "110 USD × the latest FX publication (1.40, shown as fxAsOf) — the existing reporting-conversion rule")
    }

    @Test fun repeatedRefreshesAndManyUsersShareOneCloseRequestPerSymbol() = runBlocking {
        hold("AAPL", "1", "100"); closes("AAPL", "2026-10-15" to 110.0, "2026-10-16" to 120.0)
        repeat(10) { market.report("alice", "a", "1M", false) }
        for (u in 1..10) {
            ledger.saveAccount("u$u", PortfolioAccount("a", "CAD account"))
            ledger.saveTransaction("u$u", PortfolioTransaction("t", "a", TransactionType.OPENING_POSITION, "2026-10-01", PortfolioCurrency.USD,
                InstrumentRef("AAPL", currency = "USD"), "1", "100"), false)
        }
        (1..10).map { u -> async(Dispatchers.Default) { market.report("u$u", "a", "1M", false) } }.awaitAll()
        assertEquals(1, upstream.get(), "one close request for every refresh and user")
        assertNull(ledger.get("u1").transactions.firstOrNull { it.accountId != "a" }, "each user's own ledger only")
    }

    @Test fun aSplitLikeMoveIsCalledOut() = runBlocking {
        hold("AAPL", "4", "100"); closes("AAPL", "2026-10-15" to 200.0, "2026-10-16" to 50.0)
        val r = market.report("alice", "a", "1M", false)
        assertTrue(r.notice.contains("AAPL: the closing price changed by more than 45%"))
        assertEquals("4", ledger.get("alice").transactions.single().quantity, "nothing adjusted")
    }
}
