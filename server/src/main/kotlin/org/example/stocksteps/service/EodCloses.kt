package org.example.stocksteps.service

import org.example.stocksteps.brief.TsxMarketCalendar
import org.example.stocksteps.model.PricePoint
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Phase 7B: end-of-day sessions for an exchange-qualified symbol — which session's close should exist by now, when the next one is
 * expected, and the instant a session closed. US listings and US indices (`^GSPC`, `^IXIC`, `^DJI`) use [UsMarketCalendar]; `.TO`
 * listings and TSX indices (`^GSPTSE`) use [TsxMarketCalendar]. Other suffixes have no calendar (null), so callers keep fixed rules.
 *
 * A close is **expected** only [publicationDelay] after the session's close (early closes included). The provider's real
 * publication delay isn't documented for this plan, so the delay is configurable (env `EOD_PUBLICATION_DELAY_MINUTES`, default 60)
 * and a late close is retried every [LATE_RETRY] instead of being assumed.
 */
class EodSessions(
    private val clock: Clock = Clock.systemUTC(),
    val publicationDelay: Duration = Duration.ofMinutes(DEFAULT_DELAY_MINUTES),
    private val us: UsMarketCalendar = UsMarketCalendar(),
    private val tsx: TsxMarketCalendar = TsxMarketCalendar()
) {
    enum class Market { US, TSX }

    fun market(symbol: String): Market? = when {
        symbol.endsWith(".TO", ignoreCase = true) || symbol.equals("^GSPTSE", ignoreCase = true) -> Market.TSX
        symbol.startsWith("^") -> Market.US
        '.' in symbol -> null
        else -> Market.US
    }

    private fun zone(m: Market): ZoneId = if (m == Market.US) us.zone else tsx.zone
    fun isTradingDay(m: Market, date: LocalDate) = if (m == Market.US) us.isTradingDay(date) else tsx.isTradingDay(date)
    fun previousTradingDay(m: Market, date: LocalDate): LocalDate { var d = date.minusDays(1); while (!isTradingDay(m, d)) d = d.minusDays(1); return d }

    /**
     * Whether a quote can say more than the latest close right now: the listing's market is active (US 04:00–20:00 ET on trading days;
     * TSX 07:00–17:00 ET). Otherwise the latest close is the value to show.
     */
    fun active(symbol: String, now: Instant = clock.instant()): Boolean = when (market(symbol)) {
        Market.US -> us.session(now).status in setOf(org.example.stocksteps.model.MarketSessionStatus.PRE_MARKET,
            org.example.stocksteps.model.MarketSessionStatus.OPEN, org.example.stocksteps.model.MarketSessionStatus.AFTER_HOURS)
        Market.TSX -> now.atZone(tsx.zone).let { local -> tsx.isTradingDay(local.toLocalDate()) && local.toLocalTime() >= LocalTime.of(7, 0) && local.toLocalTime() < LocalTime.of(17, 0) }
        null -> true
    }

    /** When [date]'s session closed (16:00 local; 13:00 on US early closes). */
    fun closeInstant(m: Market, date: LocalDate): Instant {
        val time = if (m == Market.US && us.isEarlyClose(date)) UsMarketCalendar.EARLY_CLOSE else LocalTime.of(16, 0)
        return date.atTime(time).atZone(zone(m)).toInstant()
    }

    private fun publication(m: Market, date: LocalDate): Instant = closeInstant(m, date).plus(publicationDelay)

    /** The latest session whose close should be published by now. */
    fun expectedSession(m: Market, now: Instant = clock.instant()): LocalDate {
        var d = now.atZone(zone(m)).toLocalDate()
        while (!isTradingDay(m, d) || now < publication(m, d)) d = d.minusDays(1)
        return d
    }

    /** The next instant a new close is expected (strictly after [now]). */
    fun nextPublication(m: Market, now: Instant = clock.instant()): Instant {
        var d = now.atZone(zone(m)).toLocalDate()
        while (!isTradingDay(m, d) || publication(m, d) <= now) d = d.plusDays(1)
        return publication(m, d)
    }

    /**
     * Cache lifetime for a successfully loaded daily series (oldest first): until the next expected close when it already contains
     * the expected session (at most [MAX_TTL]); [LATE_RETRY] while the expected close hasn't appeared yet; [fallback] without a calendar.
     */
    fun ttl(symbol: String, points: List<PricePoint>, fallback: Long, now: Instant = clock.instant()): Long {
        val m = market(symbol) ?: return fallback
        val latest = points.mapNotNull { dateOf(it) }.maxOrNull()
        val expected = expectedSession(m, now)
        val untilNext = Duration.between(now, nextPublication(m, now)).toMillis()
        val ttl = if (latest != null && latest >= expected) minOf(untilNext, MAX_TTL) else minOf(LATE_RETRY, untilNext)
        return ttl.coerceAtLeast(MIN_TTL)
    }

    companion object {
        const val DEFAULT_DELAY_MINUTES = 60L
        /** While an expected close is missing (provider late, or a gap). */
        const val LATE_RETRY = 30 * 60_000L
        /** Covers a long weekend; corrections are picked up at the next expected close at the latest. */
        const val MAX_TTL = 4 * 86_400_000L
        const val MIN_TTL = 60_000L
        internal fun dateOf(p: PricePoint): LocalDate? = runCatching { LocalDate.parse(p.time.take(10)) }.getOrNull()
    }
}

/**
 * The latest completed session's close and the change from the preceding trading day's close (Phase 7B), from a daily series. Never
 * invented: null unless both closes are valid (finite, > 0), on two **consecutive trading days** of the listing's calendar, and the
 * latest is not after the session whose close is expected by now (a same-day partial row isn't a close). Duplicate rows for one date
 * count once (the last wins); two distinct days with equal closes are a genuine 0.00 % change.
 * [stale] = the latest close is older than the expected session (shown with its date, never as current).
 */
data class EodChange(
    val close: Double, val closeDate: LocalDate, val previousClose: Double, val previousCloseDate: LocalDate,
    val change: Double, val changePercent: Double, val closedAt: Instant, val stale: Boolean
) {
    companion object {
        fun of(symbol: String, points: List<PricePoint>, sessions: EodSessions, now: Instant): EodChange? {
            val m = sessions.market(symbol) ?: return null
            val expected = sessions.expectedSession(m, now)
            val byDate = sortedMapOf<LocalDate, Double>()
            for (p in points) {
                val date = EodSessions.dateOf(p) ?: continue
                if (date > expected || !sessions.isTradingDay(m, date)) continue
                // Missing, zero, negative or non-finite closes aren't observations.
                if (!p.close.isFinite() || p.close <= 0) { byDate.remove(date); continue }
                byDate[date] = p.close
            }
            val (latestDate, latest) = byDate.entries.lastOrNull()?.toPair() ?: return null
            val previousDate = sessions.previousTradingDay(m, latestDate)
            val previous = byDate[previousDate] ?: return null
            val change = latest - previous
            val percent = change / previous * 100
            if (!change.isFinite() || !percent.isFinite()) return null
            return EodChange(latest, latestDate, previous, previousDate, change, percent, sessions.closeInstant(m, latestDate), latestDate < expected)
        }
    }
}

/**
 * Phase 7C: the latest valid official close for valuation and simulated execution — one rule for My Portfolio and Practice. A close is
 * a valid observation (finite, > 0) on a trading day of the listing's calendar, not after the session whose close is expected by now.
 * [sessionsBehind] counts trading sessions between it and the expected session (0 = the latest completed session's close).
 * Callers decide how far behind they accept ([VALUATION_MAX_SESSIONS], [EXECUTION_MAX_SESSIONS]); nothing is estimated.
 */
data class EodPrice(val close: Double, val date: LocalDate, val closedAt: Instant, val expected: LocalDate, val sessionsBehind: Int) {
    companion object {
        /** Valuation: a close up to one trading week old is shown with its date; older is unavailable. */
        const val VALUATION_MAX_SESSIONS = 5
        /** Simulated execution: the expected session's close, or the one before while the provider publishes late. */
        const val EXECUTION_MAX_SESSIONS = 1

        fun latest(symbol: String, points: List<PricePoint>, sessions: EodSessions, now: Instant): EodPrice? {
            val m = sessions.market(symbol) ?: return null
            val expected = sessions.expectedSession(m, now)
            val latest = points.mapNotNull { p -> EodSessions.dateOf(p)?.takeIf { it <= expected && sessions.isTradingDay(m, it) && p.close.isFinite() && p.close > 0 }?.let { it to p.close } }
                .maxByOrNull { it.first } ?: return null
            var behind = 0; var d = expected
            while (d > latest.first && behind <= 30) { d = sessions.previousTradingDay(m, d); behind++ }
            return EodPrice(latest.second, latest.first, sessions.closeInstant(m, latest.first), expected, behind)
        }
    }
}

/**
 * Phase 7C corporate-action guard: the latest two consecutive closes differ by a split-like factor (≥ 1.8× or ≤ 0.55×). It does not
 * prove a split (the adjustment basis of the provider's series is unverified) and nothing is adjusted; callers only warn.
 */
fun isSplitLike(change: EodChange): Boolean = (change.close / change.previousClose).let { it >= 1.8 || it <= 0.55 }

