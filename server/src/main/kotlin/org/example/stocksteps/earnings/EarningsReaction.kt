package org.example.stocksteps.earnings

import org.example.stocksteps.model.EarningsTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Post-earnings price reaction from regular-session daily closes. Trading days are the days that
 * have a close (so weekends and exchange holidays are skipped naturally); times are exchange-local
 * (DST handled by the zone rules). Never computed before the end session has closed.
 *
 * - Before the open: last close before the announcement date → close on the first trading day on or
 *   after it (an announcement on a weekend/holiday uses the next session).
 * - After the close: close on the announcement date (or the last session before it) → next trading
 *   day's close.
 * - During market hours: previous close → that day's close (the window includes pre-announcement trading).
 * - Unknown time: previous close → first close after the date, covering both possibilities; labelled approximate.
 *
 * The result describes how the price changed over the window. It never claims why.
 */
object EarningsReactionCalculator {
    /** The regular session is treated as complete this long after the 4:00 pm local close. */
    private val SESSION_COMPLETE = LocalTime.of(16, 15)
    private val FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun zone(exchange: String?): ZoneId = if (exchange.equals("TSX", ignoreCase = true) || exchange.equals("TSXV", ignoreCase = true))
        ZoneId.of("America/Toronto") else ZoneId.of("America/New_York")

    fun compute(
        event: EarningsEvent,
        closes: Map<LocalDate, Double>,
        market: Map<LocalDate, Double>?,
        marketLabel: String?,
        now: Instant,
        currency: String?,
        source: String
    ): PriceReaction {
        val methodology = "Regular-session closing prices around the announcement; ${EarningsEducation.topic("reaction")?.body.orEmpty()}"
        fun unavailable(reason: String) = PriceReaction(false, methodology = methodology, reason = reason, source = source)
        val date = runCatching { LocalDate.parse(event.date) }.getOrNull() ?: return unavailable("The announcement date isn't known.")
        if (event.actual == null) return unavailable("Results haven't been reported yet.")
        val days = closes.filterValues { it.isFinite() && it > 0 }.keys.sorted()
        if (days.isEmpty()) return unavailable("Price history isn't available for this company.")
        val before = { d: LocalDate -> days.lastOrNull { it < d } }
        val onOrBefore = { d: LocalDate -> days.lastOrNull { it <= d } }
        val onOrAfter = { d: LocalDate -> days.firstOrNull { it >= d } }
        val after = { d: LocalDate -> days.firstOrNull { it > d } }
        val (baseline, end) = when (event.session) {
            EarningsTime.BEFORE_OPEN -> before(date) to onOrAfter(date)
            EarningsTime.AFTER_CLOSE -> onOrBefore(date) to onOrBefore(date)?.let(after)
            EarningsTime.DURING_MARKET -> before(date) to onOrAfter(date)
            EarningsTime.UNKNOWN -> before(date) to after(date)
        }
        val local = now.atZone(zone(event.exchange))
        if (baseline == null) return unavailable("No closing price before the announcement is available.")
        if (end == null) {
            val pending = local.toLocalDate() <= date.plusDays(5)
            return unavailable(if (pending) "The measurement window hasn't finished yet." else "No closing price after the announcement is available.")
        }
        if (end > local.toLocalDate() || end == local.toLocalDate() && local.toLocalTime() < SESSION_COMPLETE)
            return unavailable("The measurement window hasn't finished yet.")
        if (end.toEpochDay() - baseline.toEpochDay() > 10) return unavailable("Price history has a gap around the announcement.")
        val start = closes.getValue(baseline); val finish = closes.getValue(end)
        val change = (finish / start - 1) * 100
        val marketChange = market?.let { m -> val a = m[baseline]; val b = m[end]; if (a != null && b != null && a > 0) (b / a - 1) * 100 else null }
        val approximate = event.session == EarningsTime.UNKNOWN
        val sessionText = when (event.session) {
            EarningsTime.BEFORE_OPEN -> "before-market announcement"
            EarningsTime.AFTER_CLOSE -> "after-market announcement"
            EarningsTime.DURING_MARKET -> "announcement during market hours; includes trading before it"
            EarningsTime.UNKNOWN -> "announcement time unknown, so this wider window is approximate"
        }
        return PriceReaction(
            available = true, approximate = approximate,
            baselineDate = baseline.toString(), baselineClose = start, endDate = end.toString(), endClose = finish,
            changePercent = change, marketChangePercent = marketChange, marketLabel = marketLabel.takeIf { marketChange != null },
            currency = currency,
            window = "Close on ${FORMAT.format(baseline)} → close on ${FORMAT.format(end)} ($sessionText)",
            methodology = methodology, source = source, asOf = now.toString()
        )
    }

    /** Neutral wording: describes the change over the window, never a cause. */
    fun sentence(reaction: PriceReaction): String? {
        val change = reaction.changePercent ?: return null
        val direction = if (change >= 0) "rose" else "fell"
        return "The stock $direction ${"%.1f".format(Locale.US, kotlin.math.abs(change))}% over the measured earnings window" +
            (if (reaction.approximate) " (approximate)." else ".") +
            " Other news and overall market moves can also affect the price."
    }
}
