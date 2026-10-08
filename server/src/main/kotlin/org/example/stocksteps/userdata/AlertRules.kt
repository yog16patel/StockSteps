package org.example.stocksteps.userdata

import org.example.stocksteps.model.*
import org.example.stocksteps.news.NewsClassifier
import org.example.stocksteps.service.UsMarketCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs

/** What the evaluator should do with one rule after looking at the data. */
sealed interface AlertDecision {
    data object None : AlertDecision
    /** Price seen on the "other side" of the threshold: the rule may now trigger on a cross. */
    data object Arm : AlertDecision
    data class Trigger(
        val eventKey: String,
        val title: String,
        val body: String,
        val observedValue: Double?,
        val sessionDate: String?,
        val sourceUrl: String? = null,
        val update: (AlertRule) -> AlertRule
    ) : AlertDecision
    /** Data too old or from another session: nothing is decided (never triggers on stale prices). */
    data object Stale : AlertDecision
}

/** The market data one evaluation uses for an instrument (fetched once per symbol per run). */
data class InstrumentSnapshot(
    val symbol: String,
    val name: String?,
    val quote: StockQuote? = null,
    val earnings: UpcomingEarnings? = null,
    val news: List<NewsArticle> = emptyList(),
    /** Latest reported earnings within the last few days (for results reminders). */
    val earningsResult: org.example.stocksteps.earnings.EarningsEvent? = null
)

/**
 * Deterministic alert conditions. Prices are FMP's delayed quote (last regular-session price);
 * nothing triggers on a quote from an earlier session or older than [MAX_QUOTE_AGE_SECONDS]
 * while the market is open.
 *
 * - Price above: price ≥ threshold. Price below: price ≤ threshold. A rule fires only while armed;
 *   a rule created while the condition was already true (WAIT_FOR_CROSS) arms once the price is seen
 *   on the other side. ONCE → TRIGGERED; REPEAT → re-arms after the price crosses back.
 *   Event key: rule + trigger cycle, so retries and duplicate workers can't fire twice.
 * - Daily move: |price / previous close − 1| ≥ threshold in the chosen direction, regular session
 *   only (quotes outside 9:30–close+5min are ignored). Event key: rule + session date (once per session).
 * - Earnings: day before (from 9:00 ET) and/or day of (from 7:00 ET). Event key: rule + date + timing,
 *   so a rescheduled date gets its own reminder and the same date never repeats.
 * - News: a concrete company event in a headline naming the company (same rules as "Why did it
 *   move?"), published after the rule was created and within 24 hours; similar headlines (syndicated
 *   copies) are grouped and only the first notifies.
 */
class AlertRules(private val calendar: UsMarketCalendar = UsMarketCalendar()) {
    fun decide(rule: AlertRule, data: InstrumentSnapshot, now: Instant, recentTitles: List<String> = emptyList()): AlertDecision {
        if (rule.status != AlertStatus.ACTIVE) return AlertDecision.None
        return when (rule.type) {
            AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW -> price(rule, data, now)
            AlertType.DAILY_MOVE -> move(rule, data, now)
            AlertType.EARNINGS -> earnings(rule, data, now)
            AlertType.NEWS -> news(rule, data, now, recentTitles)
        }
    }

    /** True when a price alert's condition holds for [price] (used at creation to ask the user). */
    fun priceConditionMet(type: AlertType, threshold: Double, price: Double) =
        if (type == AlertType.PRICE_ABOVE) price >= threshold else price <= threshold

    /** The session a fresh quote must belong to: today once the regular session has opened, otherwise the previous trading day. */
    fun latestSessionDate(now: Instant): LocalDate {
        val local = now.atZone(calendar.zone)
        val today = local.toLocalDate()
        return if (calendar.isTradingDay(today) && !local.toLocalTime().isBefore(UsMarketCalendar.OPEN)) today
        else calendar.lastTradingDay(today.minusDays(1))
    }

    fun freshQuote(quote: StockQuote?, now: Instant): StockQuote? {
        val price = quote?.price?.takeIf { it.isFinite() && it > 0 } ?: return null
        val at = quote.timestamp?.let(Instant::ofEpochSecond) ?: return null
        val session = calendar.session(now)
        if (at.atZone(calendar.zone).toLocalDate() < latestSessionDate(now)) return null
        if (session.status == MarketSessionStatus.OPEN && now.epochSecond - at.epochSecond > MAX_QUOTE_AGE_SECONDS) return null
        return quote.takeIf { price > 0 }
    }

    private fun price(rule: AlertRule, data: InstrumentSnapshot, now: Instant): AlertDecision {
        val threshold = rule.threshold ?: return AlertDecision.None
        val quote = freshQuote(data.quote, now) ?: return AlertDecision.Stale
        val price = quote.price!!
        val met = priceConditionMet(rule.type, threshold, price)
        if (!rule.armed) return if (met) AlertDecision.None else AlertDecision.Arm
        if (!met) return AlertDecision.None
        val above = rule.type == AlertType.PRICE_ABOVE
        val currency = rule.currency ?: "USD"
        val name = display(data)
        return AlertDecision.Trigger(
            eventKey = "${rule.id}:cycle-${rule.triggerCount + 1}",
            title = "${rule.instrument.symbol} price alert",
            body = "$name is at ${money(price, currency)}, ${if (above) "at or above" else "at or below"} your ${money(threshold, currency)} alert. Prices may be delayed.",
            observedValue = price,
            sessionDate = quoteDate(quote)
        ) { current ->
            val time = now.toEpochMilli()
            if (current.repeat == RepeatPolicy.REPEAT) current.copy(armed = false, triggerCount = current.triggerCount + 1, lastTriggeredAt = time, updatedAt = time)
            else current.copy(status = AlertStatus.TRIGGERED, triggerCount = current.triggerCount + 1, lastTriggeredAt = time, updatedAt = time)
        }
    }

    private fun move(rule: AlertRule, data: InstrumentSnapshot, now: Instant): AlertDecision {
        val threshold = rule.threshold ?: return AlertDecision.None
        val quote = freshQuote(data.quote, now) ?: return AlertDecision.Stale
        val previous = quote.previousClose?.takeIf { it.isFinite() && it > 0 } ?: return AlertDecision.Stale
        val at = Instant.ofEpochSecond(quote.timestamp!!).atZone(calendar.zone)
        val close = if (calendar.isEarlyClose(at.toLocalDate())) UsMarketCalendar.EARLY_CLOSE else UsMarketCalendar.CLOSE
        // Regular-session price only: pre-market and after-hours moves aren't mixed in.
        if (at.toLocalTime().isBefore(UsMarketCalendar.OPEN) || at.toLocalTime().isAfter(close.plusMinutes(5))) return AlertDecision.Stale
        val percent = (quote.price!! / previous - 1) * 100
        val hit = when (rule.direction ?: MoveDirection.EITHER) {
            MoveDirection.UP -> percent >= threshold
            MoveDirection.DOWN -> percent <= -threshold
            MoveDirection.EITHER -> abs(percent) >= threshold
        }
        if (!hit) return AlertDecision.None
        val session = at.toLocalDate().toString()
        return AlertDecision.Trigger(
            eventKey = "${rule.id}:session-$session",
            title = "${rule.instrument.symbol} moved ${signed(percent)} today",
            body = "${display(data)} is ${if (percent >= 0) "up" else "down"} ${String.format(Locale.US, "%.2f", abs(percent))}% from the previous close (your alert: ${String.format(Locale.US, "%.1f", threshold)}%). Prices may be delayed.",
            observedValue = percent,
            sessionDate = session
        ) { current ->
            val time = now.toEpochMilli()
            val next = current.copy(triggerCount = current.triggerCount + 1, lastTriggeredAt = time, updatedAt = time)
            if (current.repeat == RepeatPolicy.ONCE) next.copy(status = AlertStatus.TRIGGERED) else next
        }
    }

    /**
     * Earnings reminders. Keys use the event's stable fiscal-period id when known, so a moved date
     * never sends the same reminder again; results notifications fire once per event.
     */
    private fun earnings(rule: AlertRule, data: InstrumentSnapshot, now: Instant): AlertDecision {
        if (rule.earningsResults) results(rule, data)?.let { return it }
        val earnings = data.earnings ?: return AlertDecision.None
        val date = runCatching { LocalDate.parse(earnings.date) }.getOrNull() ?: return AlertDecision.None
        val local = now.atZone(calendar.zone)
        val today = local.toLocalDate()
        val timing = rule.earningsTiming ?: EarningsTiming.BOTH
        val lead = (rule.earningsLeadDays ?: 1).toLong().coerceIn(1, 7)
        val which = when {
            today == date.minusDays(lead) && timing != EarningsTiming.DAY_OF && !local.toLocalTime().isBefore(LocalTime.of(9, 0)) -> EarningsTiming.DAY_BEFORE
            today == date && timing != EarningsTiming.DAY_BEFORE && !local.toLocalTime().isBefore(LocalTime.of(7, 0)) -> EarningsTiming.DAY_OF
            else -> return AlertDecision.None
        }
        val whenText = if (which == EarningsTiming.DAY_OF) "today" else if (lead == 1L) "tomorrow" else "in $lead days"
        val timeText = when (earnings.time) {
            EarningsTime.BEFORE_OPEN -> " before the market opens"
            EarningsTime.AFTER_CLOSE -> " after the market closes"
            EarningsTime.DURING_MARKET -> " during market hours"
            EarningsTime.UNKNOWN -> ""
        }
        val certainty = when (earnings.status) {
            EarningsDateStatus.CONFIRMED -> "The company has confirmed the date."
            EarningsDateStatus.TENTATIVE -> "The date is tentative and may change."
            else -> "The date is estimated and may change."
        }
        val identity = earnings.eventId ?: earnings.date
        return AlertDecision.Trigger(
            eventKey = "${rule.id}:earnings-$identity-${which.name}",
            title = "${rule.instrument.symbol} earnings $whenText",
            body = "${display(data)} is expected to report earnings $whenText$timeText. $certainty",
            observedValue = null,
            sessionDate = earnings.date
        ) { current ->
            val time = now.toEpochMilli()
            current.copy(triggerCount = current.triggerCount + 1, lastTriggeredAt = time, updatedAt = time)
        }
    }

    /** Results available (StockSteps+): once per event; optional verified-surprise threshold. */
    private fun results(rule: AlertRule, data: InstrumentSnapshot): AlertDecision.Trigger? {
        val event = data.earningsResult ?: return null
        val eps = org.example.stocksteps.earnings.EarningsCalculator.eps(event.estimate, event.actual)
        val revenue = org.example.stocksteps.earnings.EarningsCalculator.revenue(event.estimate, event.actual)
        rule.earningsSurprisePercent?.let { threshold ->
            val biggest = listOfNotNull(eps.percent, revenue.percent).maxOfOrNull { kotlin.math.abs(it) } ?: return null
            if (biggest < threshold) return null
        }
        fun part(label: String, r: org.example.stocksteps.earnings.SurpriseResult) = when (r.classification) {
            org.example.stocksteps.earnings.Classification.UNAVAILABLE -> null
            else -> "$label ${r.classification.label.lowercase()}" + (r.percent?.let { " (${if (it >= 0) "+" else ""}${"%.1f".format(java.util.Locale.US, it)}%)" } ?: "")
        }
        val summary = listOfNotNull(part("EPS", eps), part("revenue", revenue)).joinToString(", ").ifBlank { "Results are available" }
        return AlertDecision.Trigger(
            eventKey = "${rule.id}:earnings-results-${event.id}",
            title = "${rule.instrument.symbol} reported ${event.period} results",
            body = "${display(data)}: $summary versus analyst estimates. Results don't determine how the stock moves.",
            observedValue = null,
            sessionDate = event.date
        ) { current -> current.copy(triggerCount = current.triggerCount + 1, lastTriggeredAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()) }
    }

    private fun news(rule: AlertRule, data: InstrumentSnapshot, now: Instant, recentTitles: List<String>): AlertDecision {
        val since = maxOf(rule.createdAt, now.toEpochMilli() - NEWS_WINDOW_MILLIS)
        val symbol = rule.instrument.symbol
        val shortName = shortName(data.name ?: rule.instrument.name)
        val candidates = data.news.filter { article ->
            val published = article.publishedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@filter false
            published > since && published <= now.toEpochMilli() && isCompanyEvent(article.title, symbol, shortName)
        }.sortedBy { it.publishedAt }
        val seen = recentTitles.map(::tokens).toMutableList()
        val first = candidates.firstOrNull { article -> seen.none { similar(it, tokens(article.title)) } } ?: return AlertDecision.None
        return AlertDecision.Trigger(
            eventKey = "${rule.id}:news-${groupKey(first)}",
            title = "$symbol company news",
            body = first.title.take(180) + (first.source?.let { " — $it" } ?: ""),
            observedValue = null,
            sessionDate = null,
            sourceUrl = first.url
        ) { current ->
            val time = now.toEpochMilli()
            current.copy(triggerCount = current.triggerCount + 1, lastTriggeredAt = time, updatedAt = time)
        }
    }

    companion object {
        const val MAX_QUOTE_AGE_SECONDS = 30 * 60L
        const val NEWS_WINDOW_MILLIS = 24 * 3_600_000L
        private val STRONG = setOf(NewsCategory.EARNINGS, NewsCategory.BUSINESS, NewsCategory.PRODUCTS, NewsCategory.REGULATION)

        /** A concrete company event (not commentary) in a headline that names the company near its start. */
        fun isCompanyEvent(title: String, symbol: String, shortName: String?): Boolean {
            val position = listOfNotNull(
                Regex("(?<![A-Za-z0-9])${Regex.escape(symbol)}(?![A-Za-z0-9])").find(title)?.range?.first,
                shortName?.let { Regex("(?i)(?<![a-z0-9])${Regex.escape(it)}").find(title)?.range?.first }
            ).minOrNull() ?: return false
            return position <= 20 && NewsClassifier.eventCategory(title) in STRONG
        }

        fun shortName(name: String?): String? = name
            ?.replace(Regex("(?i)(?:[,. ]+(?:incorporated|inc|corporation|corp|limited|ltd|plc|holdings|company|co)\\.?)+$"), "")
            ?.trim()?.split(" ")?.first()?.takeIf { it.length >= 4 }

        fun tokens(title: String): Set<String> = title.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()

        /** Syndicated copies of one story share most words. */
        fun similar(a: Set<String>, b: Set<String>): Boolean {
            if (a.isEmpty() || b.isEmpty()) return false
            return a.intersect(b).size.toDouble() / a.union(b).size >= 0.5
        }

        private fun groupKey(article: NewsArticle) = (article.id ?: article.url).filter { it.isLetterOrDigit() || it in "-_" }.take(64)
        private fun display(data: InstrumentSnapshot) = data.name?.let { "$it (${data.symbol})" } ?: data.symbol
        private fun signed(value: Double) = String.format(Locale.US, "%+.2f%%", value)
        private fun quoteDate(quote: StockQuote) = quote.timestamp?.let { Instant.ofEpochSecond(it).atZone(java.time.ZoneId.of("America/New_York")).toLocalDate().toString() }
        fun money(value: Double, currency: String) =
            if (currency == "USD") String.format(Locale.US, "$%,.2f", value) else String.format(Locale.US, "%,.2f %s", value, currency)
    }
}
