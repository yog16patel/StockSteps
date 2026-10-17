package org.example.stocksteps.practice

import kotlinx.coroutines.runBlocking
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.service.EodSessions
import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.InMemoryUserDataStore
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.*

/**
 * Phase 7D: Practice accounting against independent hand calculations (CAD base, USD stock, USD/CAD 1.35 from [FakeFx], the EOD path).
 * Cash amounts are rounded to cents per trade (`PracticeEngine.money`); a sell removes cost pro rata (weighted average).
 *
 *   buy 3 @ 100  → 3 × 100 × 1.35 = 405.00          cash 9,595.00   cost 405.00
 *   buy 1 @ 120  → 1 × 120 × 1.35 = 162.00          cash 9,433.00   cost 567.00 (avg 141.75 CAD/share)
 *   sell 2 @ 130 → 2 × 130 × 1.35 = 351.00          cash 9,784.00   cost removed 283.50 → realized 67.50; cost left 283.50
 *   value @ 125  → 2 × 125 × 1.35 = 337.50          unrealized 337.50 − 283.50 = 54.00
 *   buy 0.5 @ 101 → 0.5 × 101 × 1.35 = 68.175 → 68.18 (cents)
 *   sell all 2.5 @ 125 → 2.5 × 125 × 1.35 = 421.875 → 421.88; realized total 67.50 + (421.88 − 351.68) = 137.70; cash 10,137.70
 */
class PracticeAccountingReferenceTest {
    private val store = InMemoryUserDataStore()
    private val clock = MutableClock(Instant.parse("2026-10-17T15:00:00Z"))
    private val marketClock = MutableClock(Instant.parse("2026-10-16T22:00:00Z"))
    private val market = FakeMarket()
    private val svc = PracticeService(store, EntitlementService(store, clock::millis, debugAllowed = true), market, FakeFx(), clock, marketClock, null,
        sampleData = false, eod = EodSessions(marketClock))
    private var k = 0
    private fun close(price: Double) { market.history = mapOf("AAPL" to listOf(PricePoint("2026-10-15", 1.0), PricePoint("2026-10-16", price))) }
    private fun trade(side: OrderSide, qty: String, price: Double) = runBlocking {
        close(price)
        svc.execute("u", PracticeOrderRequest("AAPL", side, quantity = qty, idempotencyKey = "ref-${++k}-abcdef", reviewedPrice = price.toBigDecimal().toPlainString()))
    }
    private fun eq(expected: String, actual: String?, what: String) = assertEquals(0, BigDecimal(expected).compareTo(BigDecimal(actual!!)), "$what: expected $expected, was $actual")

    @Test fun weightedAverageRealizedUnrealizedFractionalAndFullExit() = runBlocking {
        eq("405", trade(OrderSide.BUY, "3", 100.0).transaction.amount, "first buy")
        eq("9433", trade(OrderSide.BUY, "1", 120.0).cashAfter, "cash after two buys")
        var o = svc.overview("u")
        eq("567", o.holdings.single().costBasis, "cost basis")
        eq("141.75", o.holdings.single().averageCost, "weighted average cost per share")
        eq("9784", trade(OrderSide.SELL, "2", 130.0).cashAfter, "cash after the partial sell")
        close(125.0); o = svc.overview("u")
        eq("67.5", o.realizedGain, "realized gain")
        eq("283.5", o.holdings.single().costBasis, "cost left")
        eq("337.5", o.holdings.single().marketValue, "market value at the latest close")
        eq("54", o.holdings.single().unrealizedGain, "unrealized gain")
        eq("68.18", trade(OrderSide.BUY, "0.5", 101.0).transaction.amount, "fractional buy rounded to cents")
        val exit = trade(OrderSide.SELL, "2.5", 125.0)
        eq("421.88", exit.transaction.amount, "full exit proceeds")
        o = svc.overview("u")
        assertTrue(o.holdings.isEmpty())
        eq("137.7", o.realizedGain, "total realized")
        eq("10137.7", o.cash, "final cash = starting cash + realized")
        // Restart: a new service on the same store replays to the same numbers.
        val again = PracticeService(store, EntitlementService(store, clock::millis, debugAllowed = true), market, FakeFx(), clock, marketClock, null,
            sampleData = false, eod = EodSessions(marketClock)).overview("u")
        assertEquals(o.cash, again.cash); assertEquals(o.realizedGain, again.realizedGain)
    }
}
