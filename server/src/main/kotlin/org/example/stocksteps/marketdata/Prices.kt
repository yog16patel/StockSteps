package org.example.stocksteps.marketdata

import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.service.EodSessions
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 9B price boundary: daily prices with everything needed to use them honestly — exchange and time zone, currency, adjustment
 * basis, source and delay. Existing services keep calling [PriceHistoryProvider]; new code (metrics, a second vendor) goes through
 * [DailyPriceSource], so the vendor can be replaced without touching callers. No streaming.
 */
data class DailyClose(val date: LocalDate, val close: BigDecimal)

data class PriceSeries(
    val symbol: String,
    val exchange: String?,
    val timeZone: ZoneId?,
    val currency: String?,
    val basis: AdjustmentBasis,
    /** Oldest first, one row per trading date, only valid closes (> 0). */
    val closes: List<DailyClose>,
    val provenance: Provenance
)

/** One price used in a calculation, with its date, basis and delay. */
data class PriceObservation(
    val symbol: String,
    val value: BigDecimal,
    val currency: String?,
    val date: LocalDate,
    val basis: AdjustmentBasis,
    val delay: PriceDelay,
    val provenance: Provenance
) {
    /** Calculations accept end-of-day and delayed prices; stale and unknown ones never count as current. */
    val usableForRatios: Boolean get() = value.signum() > 0 && delay in setOf(PriceDelay.END_OF_DAY, PriceDelay.DELAYED, PriceDelay.REAL_TIME)
}

fun interface DailyPriceSource {
    /** The daily series for [symbol], or null when the source has none. Provider failures are thrown ([org.example.stocksteps.repository.StockProviderException]). */
    suspend fun daily(symbol: String): Sourced<PriceSeries>?
}

/**
 * FMP's `historical-price-eod/light` behind the boundary. The adjustment basis of that series is not documented for this plan
 * (Phase 6 Q6), so it is [AdjustmentBasis.UNVERIFIED]: it may be combined only with other UNVERIFIED values from the same series,
 * never with a series of known basis. Currency is the listing currency the caller already knows (profile), never guessed here.
 */
class FmpDailyPriceSource(
    private val history: PriceHistoryProvider,
    private val currencyOf: suspend (String) -> String?,
    private val clock: Clock = Clock.systemUTC(),
    private val sessions: EodSessions = EodSessions(clock)
) : DailyPriceSource {
    override suspend fun daily(symbol: String): Sourced<PriceSeries>? {
        val points = history.getDailyCloses(symbol)
        val closes = PriceSeriesRules.clean(points)
        if (closes.isEmpty()) return null
        val market = sessions.market(symbol)
        val zone = when (market) { EodSessions.Market.US -> ZoneId.of("America/New_York"); EodSessions.Market.TSX -> ZoneId.of("America/Toronto"); null -> null }
        val provenance = Provenance("fmp", Dataset.DAILY_PRICES, clock.instant(), closes.last().date.toString())
        return Sourced(PriceSeries(symbol, market?.name, zone, currencyOf(symbol), AdjustmentBasis.UNVERIFIED, closes, provenance), provenance)
    }
}

object PriceSeriesRules {
    /** Valid closes only (finite, > 0, parseable date); a duplicated date keeps its last row; oldest first. Nothing is filled in. */
    fun clean(points: List<PricePoint>): List<DailyClose> {
        val byDate = sortedMapOf<LocalDate, BigDecimal>()
        for (p in points) {
            val d = runCatching { LocalDate.parse(p.time.take(10)) }.getOrNull() ?: continue
            if (!p.close.isFinite() || p.close <= 0) { byDate.remove(d); continue }
            byDate[d] = BigDecimal(p.close.toString())
        }
        return byDate.map { (d, c) -> DailyClose(d, c) }
    }

    /**
     * The delay label for a close: END_OF_DAY when it is the session whose close is expected by now, STALE when older, UNKNOWN without
     * an exchange calendar. Never REAL_TIME or DELAYED: a close is not a live quote.
     */
    fun closeDelay(symbol: String, date: LocalDate, sessions: EodSessions, now: Instant): PriceDelay {
        val m = sessions.market(symbol) ?: return PriceDelay.UNKNOWN
        return if (date >= sessions.expectedSession(m, now)) PriceDelay.END_OF_DAY else PriceDelay.STALE
    }

    /** The latest close of [series] as an observation labelled by [closeDelay] (null when the series is empty). */
    fun latest(series: Sourced<PriceSeries>, sessions: EodSessions, now: Instant): PriceObservation? {
        val s = series.value
        val last = s.closes.lastOrNull() ?: return null
        return PriceObservation(s.symbol, last.close, s.currency, last.date, s.basis, closeDelay(s.symbol, last.date, sessions, now), series.provenance)
    }

    /**
     * Whether a current price may be combined with a history (returns, charts, P/E history): same symbol, same currency, and the same
     * **known** basis — or both UNVERIFIED from the same provider (one consistent series). Different or unknown bases are never mixed.
     */
    fun compatible(history: PriceSeries, current: PriceObservation): Boolean =
        history.symbol.equals(current.symbol, ignoreCase = true) && history.currency != null && history.currency == current.currency &&
            history.basis == current.basis &&
            (history.basis != AdjustmentBasis.UNVERIFIED || history.provenance.provider == current.provenance.provider)

    /**
     * Quote freshness for display: a quote timestamp during an active session within [maxDelayedSeconds] is DELAYED (vendors' 15-min
     * delayed feeds), older during the same session STALE; outside the session a quote equal to the session's close is END_OF_DAY.
     * Missing timestamps are UNKNOWN. Nothing is ever labelled REAL_TIME (no licensed real-time feed exists).
     */
    fun quoteDelay(symbol: String, quoteAt: Instant?, sessions: EodSessions, now: Instant, maxDelayedSeconds: Long = 20 * 60): PriceDelay {
        if (quoteAt == null || quoteAt > now.plusSeconds(60)) return PriceDelay.UNKNOWN
        val m = sessions.market(symbol) ?: return PriceDelay.UNKNOWN
        // Phase 9B.8: a quote stamped at the expected session's close is that session's official close (end-of-day feeds), at any hour.
        val expectedClose = sessions.closeInstant(m, sessions.expectedSession(m, now))
        if (!quoteAt.isBefore(expectedClose) && !quoteAt.isAfter(expectedClose.plusSeconds(60))) return PriceDelay.END_OF_DAY
        return if (sessions.active(symbol, now)) {
            if (now.epochSecond - quoteAt.epochSecond <= maxDelayedSeconds) PriceDelay.DELAYED else PriceDelay.STALE
        } else {
            val expected = sessions.expectedSession(m, now)
            if (!quoteAt.isBefore(sessions.closeInstant(m, expected))) PriceDelay.END_OF_DAY else PriceDelay.STALE
        }
    }
}
