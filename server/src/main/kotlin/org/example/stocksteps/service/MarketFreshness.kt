package org.example.stocksteps.service

import org.example.stocksteps.brief.TsxMarketCalendar
import org.example.stocksteps.model.MarketSessionStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** Price-sensitive data whose cache lifetime depends on the listing's market session (Phase 3C). */
enum class SessionData { QUOTE, TTM_RATIOS, INTRADAY_BARS }

/**
 * Market-session-aware cache lifetimes (Phase 3C), chosen per exchange-qualified symbol:
 * - US listings use [UsMarketCalendar] (NYSE holidays, early closes, America/New_York incl. daylight saving);
 *   `.TO` listings use [TsxMarketCalendar] (TSX holidays, America/Toronto). NYSE hours are never applied to TSX.
 *   Other suffixes (`.L`, `.V`, …) have no supported calendar and keep the fixed lifetimes.
 * - **Active** = prices can still move: US pre-market, regular session and after-hours (04:00–20:00 ET; 17:00 on early
 *   closes); TSX 07:00–17:00 ET on trading days (pre-open through the post-market session). Quotes and intraday bars keep
 *   their short lifetimes while active; TTM ratios refresh every 15 minutes.
 * - **Inactive** (overnight, weekends, holidays): entries live until the market next becomes active, bounded
 *   ([QUOTE_CLOSED_MAX], [CLOSED_MAX]) because providers may still revise data and calendars don't know unscheduled events.
 *   Entries never outlive the next session start, so the first request of a session is fresh.
 * - Statements (24 h), estimates (6 h) and daily closes (6 h) keep [FinancialCachePolicy]; earnings-aware statement
 *   lifetimes are Phase 3D ([EarningsStatementSignals]).
 *
 * Only cache lifetimes change: provider timestamps (quote `timestamp`, statement `acceptedDate`) are kept as received.
 * [FIXED] restores the pre-Phase 3 constants (rollback: env `MARKET_AWARE_TTL=false`).
 */
class MarketFreshnessPolicy(
    private val now: () -> Instant = Instant::now,
    private val enabled: Boolean = true,
    private val us: UsMarketCalendar = UsMarketCalendar(),
    private val tsx: TsxMarketCalendar = TsxMarketCalendar()
) {
    enum class Market { US, TSX }

    fun market(symbol: String): Market? = when {
        symbol.endsWith(".TO", ignoreCase = true) -> Market.TSX
        '.' in symbol -> null
        else -> Market.US
    }

    fun ttl(data: SessionData, symbol: String): Long {
        val fixed = when (data) {
            SessionData.QUOTE -> FinancialCachePolicy.QUOTE
            SessionData.TTM_RATIOS -> FinancialCachePolicy.RATIOS
            SessionData.INTRADAY_BARS -> FinancialCachePolicy.INTRADAY
        }
        val market = market(symbol)
        if (!enabled || market == null) return fixed
        val at = now()
        if (active(market, at)) return when (data) {
            SessionData.QUOTE -> FinancialCachePolicy.QUOTE
            SessionData.TTM_RATIOS -> RATIOS_ACTIVE
            SessionData.INTRADAY_BARS -> FinancialCachePolicy.INTRADAY
        }
        val untilActive = Duration.between(at, nextActive(market, at)).toMillis()
        return minOf(untilActive, if (data == SessionData.QUOTE) QUOTE_CLOSED_MAX else CLOSED_MAX).coerceAtLeast(1_000L)
    }

    fun active(market: Market, at: Instant): Boolean = when (market) {
        Market.US -> us.session(at).status in setOf(MarketSessionStatus.PRE_MARKET, MarketSessionStatus.OPEN, MarketSessionStatus.AFTER_HOURS)
        Market.TSX -> at.atZone(tsx.zone).let { local -> tsx.isTradingDay(local.toLocalDate()) && local.toLocalTime() >= TSX_START && local.toLocalTime() < TSX_END }
    }

    /** The next instant the market becomes active (start of the next trading day's extended session), strictly after an inactive [at]. */
    fun nextActive(market: Market, at: Instant): Instant {
        val zone = if (market == Market.US) us.zone else tsx.zone
        val start = if (market == Market.US) UsMarketCalendar.PRE_OPEN else TSX_START
        val trading: (java.time.LocalDate) -> Boolean = if (market == Market.US) us::isTradingDay else tsx::isTradingDay
        val local = at.atZone(zone)
        var date = local.toLocalDate()
        if (!(trading(date) && local.toLocalTime() < start)) date = date.plusDays(1)
        while (!trading(date)) date = date.plusDays(1)
        return date.atTime(start).atZone(zone).toInstant()
    }

    companion object {
        /** TTM ratios / key metrics while the listing's market is active. */
        const val RATIOS_ACTIVE = 15 * 60_000L
        /** Upper bound for a quote while the market is inactive (Markets already uses 15 minutes when closed). */
        const val QUOTE_CLOSED_MAX = 15 * 60_000L
        /** Upper bound for TTM ratios and intraday bars while the market is inactive. */
        const val CLOSED_MAX = 6 * 3_600_000L
        val TSX_START: LocalTime = LocalTime.of(7, 0)
        val TSX_END: LocalTime = LocalTime.of(17, 0)
        /** The pre-Phase 3 fixed lifetimes (tests and rollback). */
        val FIXED = MarketFreshnessPolicy(enabled = false)
    }
}
