package org.example.stocksteps.service

import kotlinx.coroutines.runBlocking
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.repositoryImpl.FmpFundamentalsLoader
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/**
 * Phase 3C: cache lifetimes follow the listing's own market session (US calendar for US listings, TSX calendar for
 * `.TO`), expire at the next session start, and cut closed-market refreshes. Deterministic clocks; MockEngine only.
 */
class MarketFreshnessTest {
    private fun policy(at: String) = MarketFreshnessPolicy({ Instant.parse(at) })
    private val min = 60_000L
    private val hour = 3_600_000L

    @Test fun usRegularPreMarketAndAfterHoursKeepShortLifetimes() {
        for (at in listOf("2026-10-07T14:00:00Z", "2026-10-07T09:00:00Z", "2026-10-07T22:00:00Z")) {   // 10:00, 05:00, 18:00 ET (EDT)
            val p = policy(at)
            assertEquals(30_000L, p.ttl(SessionData.QUOTE, "AAPL"), at)
            assertEquals(15 * min, p.ttl(SessionData.TTM_RATIOS, "AAPL"), at)
            assertEquals(5 * min, p.ttl(SessionData.INTRADAY_BARS, "AAPL"), at)
        }
    }

    @Test fun tsxRegularSessionAndTsxSymbolsUseTheTsxCalendar() {
        assertEquals(30_000L, policy("2026-10-07T14:00:00Z").ttl(SessionData.QUOTE, "TD.TO"))
        // 18:00 ET: US after-hours (prices still move) but the TSX is closed — NYSE hours are never applied to .TO.
        val evening = policy("2026-10-07T22:00:00Z")
        assertEquals(30_000L, evening.ttl(SessionData.QUOTE, "TD"))
        assertEquals(15 * min, evening.ttl(SessionData.QUOTE, "TD.TO"))
        assertEquals(6 * hour, evening.ttl(SessionData.TTM_RATIOS, "TD.TO"))
    }

    @Test fun overnightEntriesExpireAtTheNextSessionStart() {
        // 01:00 ET Thursday: the US becomes active at 04:00 ET (08:00Z), so ratios live exactly 3 hours.
        val p = policy("2026-10-08T05:00:00Z")
        assertEquals(3 * hour, p.ttl(SessionData.TTM_RATIOS, "AAPL"))
        assertEquals(15 * min, p.ttl(SessionData.QUOTE, "AAPL"), "quotes are bounded to 15 minutes even when closed")
        assertEquals(10 * min, policy("2026-10-08T07:50:00Z").ttl(SessionData.QUOTE, "AAPL"), "never outlives the session start")
    }

    @Test fun weekendsAndHolidaysAreInactive() {
        val saturday = policy("2026-10-10T15:00:00Z")
        assertEquals(6 * hour, saturday.ttl(SessionData.TTM_RATIOS, "AAPL"), "bounded maximum age")
        assertEquals(15 * min, saturday.ttl(SessionData.QUOTE, "AAPL"))
        // Sunday 23:00 ET: the US opens its pre-market at 04:00 ET Monday (5 h); Monday Oct 12 is Canadian Thanksgiving.
        val sunday = policy("2026-10-12T03:00:00Z")
        assertEquals(5 * hour, sunday.ttl(SessionData.TTM_RATIOS, "AAPL"))
        assertEquals(6 * hour, sunday.ttl(SessionData.TTM_RATIOS, "RY.TO"))
        assertEquals(Instant.parse("2026-10-13T11:00:00Z"), sunday.nextActive(MarketFreshnessPolicy.Market.TSX, Instant.parse("2026-10-12T03:00:00Z")))
        // Canadian holiday, US open: different answers for the two listings.
        val caHoliday = policy("2026-10-12T14:00:00Z")
        assertEquals(30_000L, caHoliday.ttl(SessionData.QUOTE, "TD"))
        assertEquals(15 * min, caHoliday.ttl(SessionData.QUOTE, "TD.TO"))
        // US holiday (Thanksgiving), TSX open.
        val usHoliday = policy("2026-11-26T15:00:00Z")
        assertEquals(15 * min, usHoliday.ttl(SessionData.QUOTE, "AAPL"))
        assertEquals(30_000L, usHoliday.ttl(SessionData.QUOTE, "SHOP.TO"))
    }

    @Test fun earlyCloseEndsAfterHoursEarlier() {
        // Day after Thanksgiving: close 13:00, after-hours until 17:00 ET; 17:30 ET is inactive (a normal day would be after-hours).
        assertEquals(15 * min, policy("2026-11-27T22:30:00Z").ttl(SessionData.QUOTE, "AAPL"))
        assertEquals(30_000L, policy("2026-11-27T21:30:00Z").ttl(SessionData.QUOTE, "AAPL"))
        assertEquals(30_000L, policy("2026-11-25T22:30:00Z").ttl(SessionData.QUOTE, "AAPL"))
    }

    @Test fun daylightSavingTransitionsUseExchangeLocalTime() {
        // Fall back (Nov 1): Monday 03:00 EST = 08:00Z; pre-market starts 04:00 EST = 09:00Z → 1 h.
        assertEquals(hour, policy("2026-11-02T08:00:00Z").ttl(SessionData.TTM_RATIOS, "AAPL"))
        // Spring forward (Mar 8): Monday 03:00 EDT = 07:00Z; 04:00 EDT = 08:00Z → 1 h.
        assertEquals(hour, policy("2026-03-09T07:00:00Z").ttl(SessionData.TTM_RATIOS, "AAPL"))
        // TSX (America/Toronto) after the change: Monday 06:00 EST = 11:00Z, active at 07:00 EST = 12:00Z.
        assertEquals(hour, policy("2026-11-02T11:00:00Z").ttl(SessionData.TTM_RATIOS, "RY.TO"))
    }

    @Test fun unsupportedMarketsAndTheRollbackSwitchKeepFixedLifetimes() {
        val saturday = policy("2026-10-10T15:00:00Z")
        assertEquals(FinancialCachePolicy.RATIOS, saturday.ttl(SessionData.TTM_RATIOS, "VOD.L"))
        assertEquals(FinancialCachePolicy.QUOTE, MarketFreshnessPolicy({ Instant.parse("2026-10-10T15:00:00Z") }, enabled = false).ttl(SessionData.QUOTE, "AAPL"))
        assertEquals(FinancialCachePolicy.RATIOS, MarketFreshnessPolicy.FIXED.ttl(SessionData.TTM_RATIOS, "AAPL"))
    }

    @Test fun closedMarketRepeatsCostNothingWhileOpenMarketsStillRefresh() = runBlocking {
        val quote: suspend () -> Pair<StockQuote?, CompanyProfile?> = { null to null }
        fun run(at: String): Pair<Int, Int> {
            val up = FmpMock(); val ms = AtomicLong(0)
            val loader = FmpFundamentalsLoader(up.client, "k", CompanyFinancialCache(now = { ms.get() }), { LocalDate.parse("2026-10-10") }, freshness = policy(at))
            return runBlocking {
                loader.load("AAPL", "annual", quote = quote); val first = up.total
                ms.addAndGet(10 * min); loader.load("AAPL", "annual", quote = quote)
                ms.addAndGet(10 * min); loader.load("AAPL", "annual", quote = quote)
                first to up.total - first
            }
        }
        assertEquals(0, run("2026-10-10T15:00:00Z").second, "Saturday: no TTM refreshes within 20 minutes (audit baseline: 4)")
        assertEquals(0, run("2026-11-26T15:00:00Z").second, "US holiday")
        assertEquals(2, run("2026-10-07T14:00:00Z").second, "open market: ratios + key metrics refresh after 15 minutes (baseline 4 at 5 minutes)")
        val fixed = FmpMock(); val ms = AtomicLong(0)
        val legacy = FmpFundamentalsLoader(fixed.client, "k", CompanyFinancialCache(now = { ms.get() }), { LocalDate.parse("2026-10-10") })
        legacy.load("AAPL", "annual", quote = quote); val first = fixed.total
        ms.addAndGet(10 * min); legacy.load("AAPL", "annual", quote = quote); ms.addAndGet(10 * min); legacy.load("AAPL", "annual", quote = quote)
        assertEquals(4, fixed.total - first, "the fixed policy reproduces the audit baseline")
    }

    @Test fun quotesAndBarsFollowTheSessionAndKeepProviderTimestamps() = runBlocking {
        val up = FmpMock(); val ms = AtomicLong(0)
        var at = "2026-10-10T15:00:00Z"
        val freshness = MarketFreshnessPolicy({ Instant.parse(at) })
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { ms.get() }, freshness = freshness)
        val q1 = fmp.getQuote("AAPL"); ms.addAndGet(5 * min); val q2 = fmp.getQuote("AAPL")
        assertEquals(1, up.n("quote"), "Saturday: one quote request in 5 minutes (was 10 at 30 s)")
        assertEquals(q1?.timestamp, q2?.timestamp, "the provider's timestamp is served as received, not the cache time")
        // Monday 03:55 ET: the cached quote expires at 04:00 ET, when pre-market prices can move again.
        at = "2026-10-12T07:55:00Z"; ms.addAndGet(60 * min)
        fmp.getQuote("AAPL"); ms.addAndGet(4 * min); fmp.getQuote("AAPL")
        assertEquals(2, up.n("quote"))
        ms.addAndGet(2 * min); at = "2026-10-12T08:01:00Z"; fmp.getQuote("AAPL")
        assertEquals(3, up.n("quote"), "refreshed at the session boundary")
        val bars = FmpPriceHistoryProvider(up.client, "k", CompanyFinancialCache(now = { ms.get() }), freshness)
        at = "2026-10-10T15:00:00Z"
        bars.getIntradayPoints("AAPL"); ms.addAndGet(30 * min); bars.getIntradayPoints("AAPL")
        assertEquals(1, up.n("historical-chart/5min"), "Saturday bars don't change")
    }
}
