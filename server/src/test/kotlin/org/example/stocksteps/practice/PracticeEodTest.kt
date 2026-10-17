package org.example.stocksteps.practice

import kotlinx.coroutines.*
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.service.EodSessions
import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.InMemoryUserDataStore
import org.example.stocksteps.userdata.UserDataException
import java.time.Instant
import kotlin.test.*

/**
 * Phase 7C (REAL path): Practice executes immediately at the last available official close and values positions at it. No quote is
 * read; the client never sets the price; stale, missing and plan-refused closes block with their own reasons; accounting is unchanged.
 */
class PracticeEodTest {
    private val store = InMemoryUserDataStore()
    private val clock = MutableClock(Instant.parse("2026-10-17T15:00:00Z"))
    // Fri 2026-10-16 18:00 ET: Friday's close (16:00) is expected after the 60-min publication delay.
    private val marketClock = MutableClock(Instant.parse("2026-10-16T22:00:00Z"))
    private val market = FakeMarket().apply {
        history = mapOf(
            "AAPL" to listOf(PricePoint("2026-10-14", 98.0), PricePoint("2026-10-15", 100.0), PricePoint("2026-10-16", 110.0)),
            "MSFT" to listOf(PricePoint("2026-10-15", 200.0), PricePoint("2026-10-16", 202.0)),
            "RY.TO" to listOf(PricePoint("2026-10-15", 150.0), PricePoint("2026-10-16", 151.0)))
    }
    private val fx = FakeFx()
    private val svc = PracticeService(store, EntitlementService(store, clock::millis, debugAllowed = true), market, fx, clock, marketClock, null,
        sampleData = false, eod = EodSessions(marketClock))
    private var keys = 0
    private fun key() = "key-${++keys}-abcdef"
    private fun preview(symbol: String, qty: String = "1", side: OrderSide = OrderSide.BUY) = runBlocking { svc.preview("alice", PracticeOrderRequest(symbol, side, quantity = qty)) }
    private fun execute(symbol: String, qty: String, side: OrderSide = OrderSide.BUY, reviewed: String? = null, key: String = key()) = runBlocking {
        svc.execute("alice", PracticeOrderRequest(symbol, side, quantity = qty, idempotencyKey = key, reviewedPrice = reviewed ?: preview(symbol, qty, side).price))
    }
    private fun code(block: () -> Unit): String = assertFailsWith<UserDataException> { block() }.code

    @Test fun buysAtTheLastCloseWithItsDateAndNeverReadsAQuote() {
        val p = preview("AAPL", "2")
        assertNull(p.blocker)
        assertEquals("110", p.price); assertEquals("USD", p.priceCurrency); assertEquals("1.35", p.fxRate)
        assertEquals(PracticeService.EOD_BASIS, p.priceBasis); assertEquals("2026-10-16T20:00:00Z", p.quoteAsOf)
        assertEquals(0, java.math.BigDecimal(p.estimatedTotal).compareTo(java.math.BigDecimal("297")), "2 × 110 USD × 1.35 = 297 CAD")
        val r = execute("AAPL", "2")
        assertEquals("110", r.transaction.price)
        assertEquals(0, java.math.BigDecimal(r.cashAfter).compareTo(java.math.BigDecimal("9703")), "10,000 − 297")
        assertEquals(0, market.quoteCalls.get(), "no quote on the EOD path")
    }

    @Test fun sellsPartiallyAndFullyAndRejectsInvalidOrders() {
        execute("MSFT", "3")
        assertNull(preview("MSFT", "1", OrderSide.SELL).blocker)
        execute("MSFT", "1", OrderSide.SELL)
        assertEquals("INSUFFICIENT_SHARES", preview("MSFT", "5", OrderSide.SELL).blocker?.code)
        execute("MSFT", "2", OrderSide.SELL)
        assertEquals("NO_HOLDING", preview("MSFT", "1", OrderSide.SELL).blocker?.code)
        assertEquals("INSUFFICIENT_CASH", preview("AAPL", "1000").blocker?.code)
        assertEquals("INVALID_QUANTITY", preview("AAPL", "0").blocker?.code)
        val o = runBlocking { svc.overview("alice") }
        assertTrue(o.holdings.isEmpty()); assertEquals(0, java.math.BigDecimal(o.cash).compareTo(java.math.BigDecimal("10000")), "bought and sold at the same close: cash restored")
    }

    @Test fun theClientCannotSetThePriceAndRetriesNeverFillTwice() {
        assertEquals("PRICE_CHANGED", code { execute("AAPL", "1", reviewed = "50") })
        val k = key()
        val first = execute("AAPL", "1", key = k)
        val again = execute("AAPL", "1", key = k)
        assertTrue(again.replayed); assertEquals(first.transaction.id, again.transaction.id)
        val concurrent = runBlocking { (1..10).map { async(Dispatchers.Default) { runCatching { svc.execute("alice",
            PracticeOrderRequest("MSFT", OrderSide.BUY, quantity = "1", idempotencyKey = "same-key-abcdef", reviewedPrice = "202")) } } }.awaitAll() }
        assertTrue(concurrent.all { it.isSuccess })
        val o = runBlocking { svc.overview("alice") }
        assertEquals("1", o.holdings.single { it.instrument.symbol == "MSFT" }.quantity, "ten concurrent retries of one order fill once")
    }

    @Test fun lateStaleMissingAndRestrictedClosesHaveTheirOwnReasons() {
        // Friday's close not published yet: Thursday's close (one session behind) is still used, with its date.
        market.history = market.history + ("MSFT" to listOf(PricePoint("2026-10-14", 199.0), PricePoint("2026-10-15", 200.0)))
        assertEquals("2026-10-15T20:00:00Z", preview("MSFT").quoteAsOf)
        market.history = market.history + ("MSFT" to listOf(PricePoint("2026-10-12", 190.0), PricePoint("2026-10-13", 195.0)))
        assertEquals("QUOTE_STALE", preview("MSFT").blocker?.code)
        assertTrue(preview("MSFT").blocker!!.message.contains("Oct 13, 2026"))
        assertEquals("QUOTE_STALE", code { execute("MSFT", "1", reviewed = "195") })
        market.history = market.history + ("MSFT" to emptyList())
        assertEquals("QUOTE_MISSING", preview("MSFT").blocker?.code)
        market.history = market.history + ("MSFT" to listOf(PricePoint("2026-10-15", 0.0), PricePoint("2026-10-16", Double.NaN)))
        assertEquals("QUOTE_MISSING", preview("MSFT").blocker?.code, "invalid closes are never prices")
        market.closesFail["MSFT"] = StockProviderException(StockProviderException.Failure.UNAVAILABLE, 402)
        assertEquals("QUOTE_RESTRICTED", preview("MSFT").blocker?.code)
        market.closesFail["MSFT"] = StockProviderException(StockProviderException.Failure.RATE_LIMITED, 429)
        assertEquals("QUOTE_MISSING", preview("MSFT").blocker?.code, "a rate limit isn't a plan restriction")
        assertEquals("0", preview("MSFT").price, "the contract placeholder, shown as Unavailable by the apps")
    }

    @Test fun positionsAreValuedAtDatedClosesAndNeverAtZero() {
        execute("AAPL", "1"); execute("RY.TO", "1")
        val o = runBlocking { svc.overview("alice") }
        assertEquals(ValuationStatus.COMPLETE, o.valuationStatus)
        val aapl = o.holdings.single { it.instrument.symbol == "AAPL" }
        assertEquals("110", aapl.currentPrice); assertEquals("2026-10-16T20:00:00Z", aapl.quoteAsOf); assertFalse(aapl.stale)
        // A week later without new closes: still dated (stale), then unavailable after five sessions — never 0.
        marketClock.instant = Instant.parse("2026-10-20T22:00:00Z")
        val stale = runBlocking { svc.overview("alice") }
        assertTrue(stale.holdings.single { it.instrument.symbol == "AAPL" }.stale)
        assertTrue(stale.notices.any { "earlier trading session" in it })
        marketClock.instant = Instant.parse("2026-10-26T22:00:00Z")
        val old = runBlocking { svc.overview("alice") }
        assertNull(old.holdings.single { it.instrument.symbol == "AAPL" }.marketValue)
        assertNull(old.totalValue, "a partial total is never shown as the portfolio's value")
        assertEquals(ValuationStatus.UNAVAILABLE, old.valuationStatus)
    }

    @Test fun aSplitLikeMoveOfAHeldStockIsCalledOutNotAdjusted() {
        execute("AAPL", "1")
        market.history = market.history + ("AAPL" to listOf(PricePoint("2026-10-15", 200.0), PricePoint("2026-10-16", 50.0)))
        val o = runBlocking { svc.overview("alice") }
        assertTrue(o.notices.any { it.startsWith("AAPL:") && "split" in it })
        assertEquals("1", o.holdings.single().quantity, "nothing is adjusted")
        assertTrue(o.notices.any { "stock splits aren't applied" in it })
    }
}
