package org.example.stocksteps.earnings

import org.example.stocksteps.brief.TsxMarketCalendar
import org.example.stocksteps.model.EarningsTime
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.service.UsMarketCalendar
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- Exchange calendars (reusing the existing US and TSX session rules) ----------

/**
 * Regular-session calendar for one market. Holidays and early closes come from the existing
 * rule-based calendars ([UsMarketCalendar] — NYSE rules incl. half days; [TsxMarketCalendar] — TSX
 * holidays, no early closes modelled). Unscheduled closures and halts aren't in any rule set: a
 * scheduled session without a price is reported as missing, never filled.
 */
interface ExchangeCalendar {
    val zone: ZoneId
    val market: String
    fun isTradingDay(date: LocalDate): Boolean
    fun isEarlyClose(date: LocalDate): Boolean
    fun closeTime(date: LocalDate): LocalTime = if (isEarlyClose(date)) LocalTime.of(13, 0) else LocalTime.of(16, 0)

    fun previous(date: LocalDate): LocalDate { var d = date.minusDays(1); while (!isTradingDay(d)) d = d.minusDays(1); return d }
    fun next(date: LocalDate): LocalDate { var d = date.plusDays(1); while (!isTradingDay(d)) d = d.plusDays(1); return d }
    fun onOrBefore(date: LocalDate): LocalDate = if (isTradingDay(date)) date else previous(date)
    fun onOrAfter(date: LocalDate): LocalDate = if (isTradingDay(date)) date else next(date)
    /** The [n]th session counting [first] as session 1. */
    fun nth(first: LocalDate, n: Int): LocalDate { var d = first; repeat(n - 1) { d = next(d) }; return d }
    fun sessions(from: LocalDate, to: LocalDate): List<LocalDate> = generateSequence(onOrAfter(from)) { next(it) }.takeWhile { it <= to }.toList()
}

class UsExchangeCalendar(private val rules: UsMarketCalendar = UsMarketCalendar()) : ExchangeCalendar {
    override val zone: ZoneId = rules.zone
    override val market = "US (NYSE/Nasdaq rules)"
    override fun isTradingDay(date: LocalDate) = rules.isTradingDay(date)
    override fun isEarlyClose(date: LocalDate) = rules.isEarlyClose(date)
}

class TsxExchangeCalendar(private val rules: TsxMarketCalendar = TsxMarketCalendar()) : ExchangeCalendar {
    override val zone: ZoneId = rules.zone
    override val market = "Toronto Stock Exchange"
    override fun isTradingDay(date: LocalDate) = rules.isTradingDay(date)
    override fun isEarlyClose(date: LocalDate) = false
}

object ExchangeCalendars {
    private val us = UsExchangeCalendar()
    private val tsx = TsxExchangeCalendar()
    private val US = setOf("NASDAQ", "NYSE", "AMEX", "NYSEARCA", "NYSEAMERICAN", "BATS", "CBOE", "OTC", "OTCQX", "OTCQB", "PNK")
    /** Null for markets without a supported calendar (no reaction is calculated there). */
    fun of(exchange: String?, symbol: String): ExchangeCalendar? = when {
        exchange.equals("TSX", true) || exchange.equals("TSXV", true) || symbol.endsWith(".TO", true) -> tsx
        exchange == null || exchange.uppercase() in US -> us
        else -> null
    }
}

// ---------- Price series ----------

data class DailyBar(val date: LocalDate, val close: Decimal, val currency: String?, val adjustment: PriceAdjustment)
data class CorporateAction(val date: LocalDate, val type: String, val description: String)

/** One instrument's regular-session closes in its trading currency, with provenance. */
data class PriceSeries(
    val bars: Map<LocalDate, DailyBar>,
    val actions: List<CorporateAction> = emptyList(),
    /** False when the source doesn't report splits/dividends, so they can't be checked. */
    val actionsKnown: Boolean = false,
    val source: String,
    val fetchedAt: Instant,
    val freshness: DataFreshness = DataFreshness.FRESH
)

/** Range-based daily history for one instrument (one request, never one per chart point). */
fun interface ReactionPriceSource {
    suspend fun series(symbol: String): PriceSeries
}

// ---------- Engine ----------

/**
 * Backend-authoritative post-earnings reaction from regular-session closes.
 *
 * Policy (session 1 = the first regular session whose close reflects the announcement):
 * - After the close: baseline = close of the announcement date's session (or the last session before
 *   a non-trading announcement date); session 1 = the next session.
 * - Before the open: baseline = close of the last session before the announcement date; session 1 =
 *   the first session on or after it.
 * - During market hours: no intraday prices are available, so previous close → that session's close,
 *   labelled as including trading before the announcement.
 * - Unknown time: not guessed. A broader comparison (last close before the date → first close after it)
 *   is shown with status EVENT_TIME_UNKNOWN.
 * - Windows end at the close of session 1, 3 or 5, counted on the exchange calendar (weekends,
 *   holidays and half days included). A session is complete 15 minutes after its scheduled close.
 * Change = endpoint − baseline and (endpoint − baseline) ÷ baseline × 100, in exact decimals, only when
 * both prices are positive and share currency and adjustment basis.
 */
object PriceReactionEngine {
    private val DATA_DELAY: Duration = Duration.ofMinutes(15)
    private val DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val HUNDRED = Decimal.parse("100")

    data class Plan(val baseline: LocalDate, val first: LocalDate)

    fun plan(timing: EarningsTime, date: LocalDate, calendar: ExchangeCalendar): Plan = when (timing) {
        EarningsTime.AFTER_CLOSE -> Plan(calendar.onOrBefore(date), calendar.next(date))
        EarningsTime.BEFORE_OPEN, EarningsTime.DURING_MARKET -> Plan(calendar.previous(date), calendar.onOrAfter(date))
        EarningsTime.UNKNOWN -> Plan(calendar.previous(date), calendar.next(date))
    }

    private fun closed(date: LocalDate, calendar: ExchangeCalendar, now: Instant) =
        !date.atTime(calendar.closeTime(date)).atZone(calendar.zone).toInstant().plus(DATA_DELAY).isAfter(now)

    private fun observation(bar: DailyBar, calendar: ExchangeCalendar, source: String) = PriceObservation(
        bar.date.toString(), bar.date.atTime(calendar.closeTime(bar.date)).atZone(calendar.zone).toOffsetDateTime().toString(), bar.close.toString(),
        bar.currency, PriceSessionType.REGULAR_CLOSE, bar.adjustment, source, calendar.isEarlyClose(bar.date))

    private fun timingText(t: EarningsTime) = when (t) {
        EarningsTime.AFTER_CLOSE -> "after-market announcement"; EarningsTime.BEFORE_OPEN -> "before-market announcement"
        EarningsTime.DURING_MARKET -> "announcement during market hours"; EarningsTime.UNKNOWN -> "announcement time unknown"
    }

    fun compute(
        event: EarningsEvent, reportId: String, window: ReactionWindow, series: PriceSeries?, calendar: ExchangeCalendar?,
        now: Instant, providerProblem: String? = null
    ): EarningsPriceReaction {
        val date = LocalDate.parse(event.date)
        val base = EarningsPriceReaction("$reportId:${window.name}", reportId, event.id, event.symbol, event.exchange, event.date, event.eventTime,
            event.timeZone ?: calendar?.zone?.id, event.session, window, ReactionStatus.AVAILABLE, calculatedAt = now.toString(),
            freshness = series?.freshness ?: DataFreshness.UNAVAILABLE, sources = listOfNotNull(series?.source))
        fun status(s: ReactionStatus, message: String, extra: (EarningsPriceReaction) -> EarningsPriceReaction = { it }) = extra(base.copy(status = s, statusMessage = message))
        calendar ?: return status(ReactionStatus.DATA_NOT_COMPARABLE, "Price reactions are calculated only for US and Toronto Stock Exchange listings, where StockSteps has a trading calendar.")
        providerProblem?.let { return status(ReactionStatus.PROVIDER_UNAVAILABLE, it) }
        series ?: return status(ReactionStatus.PROVIDER_UNAVAILABLE, "Price history isn't available right now.")
        if (series.bars.isEmpty()) return status(ReactionStatus.BASELINE_UNAVAILABLE, "Price history isn't available for this company.")
        val plan = plan(event.session, date, calendar)
        val end = calendar.nth(plan.first, window.sessions)
        val observed = calendar.sessions(plan.first, end).count { closed(it, calendar, now) }
        val measurement = "Close on ${DAY.format(plan.baseline)} → close on ${DAY.format(end)} (${timingText(event.session)}; " +
            (if (window.sessions == 1) "first session after" else "${window.sessions} sessions after") + ")"
        val warnings = buildList {
            if (!series.actionsKnown) add("The price source doesn't report stock splits or special dividends, so they aren't checked for this window.")
            if (event.session == EarningsTime.DURING_MARKET) add("The announcement came during market hours and intraday prices aren't available, so this regular-session comparison includes trading before the announcement.")
            event.previousDate?.let { add("The announcement date moved from ${DAY.format(LocalDate.parse(it))}; the window uses the current date.") }
            calendar.sessions(plan.baseline.plusDays(1), end).filter { it < end && closed(it, calendar, now) && series.bars[it] == null }
                .takeIf { it.isNotEmpty() }?.let { add("No price for ${it.joinToString { d -> DAY.format(d) }} (data missing or trading halted).") }
            if (calendar.isEarlyClose(end)) add("${DAY.format(end)} was a shortened trading session (early close).")
            if (!calendar.isTradingDay(date)) add("The announcement date (${DAY.format(date)}) wasn't a trading day, so the next session is used.")
        }
        val b = series.bars[plan.baseline]
        val withPlan = { r: EarningsPriceReaction -> r.copy(baseline = b?.let { observation(it, calendar, series.source) }, measurement = measurement, sessionsObserved = observed, warnings = warnings) }
        if (b == null) return status(ReactionStatus.BASELINE_UNAVAILABLE, "No closing price for ${DAY.format(plan.baseline)}, the session before the announcement.", withPlan)
        if (!closed(end, calendar, now)) return status(ReactionStatus.WINDOW_INCOMPLETE,
            "The ${if (window.sessions == 1) "first session" else "${ordinal(window.sessions)} session"} after the announcement (${DAY.format(end)}) hasn't closed yet.", withPlan)
        val e = series.bars[end] ?: return status(ReactionStatus.ENDPOINT_UNAVAILABLE, "No closing price for ${DAY.format(end)} (data missing or trading halted).", withPlan)
        val endObs = observation(e, calendar, series.source)
        val withBoth = { r: EarningsPriceReaction -> withPlan(r).copy(endpoint = endObs) }
        if (b.close <= Decimal.ZERO || e.close <= Decimal.ZERO) return status(ReactionStatus.DATA_NOT_COMPARABLE, "One of the prices isn't a valid positive amount, so no change is calculated.", withBoth)
        if (b.currency != null && e.currency != null && b.currency != e.currency) return status(ReactionStatus.DATA_NOT_COMPARABLE, "The two prices are in different currencies (${b.currency} and ${e.currency}).", withBoth)
        if (b.adjustment != e.adjustment) return status(ReactionStatus.DATA_NOT_COMPARABLE, "The two prices use different adjustment bases (${b.adjustment.label} and ${e.adjustment.label}).", withBoth)
        val actions = series.actions.filter { it.date > plan.baseline && it.date <= end }
        val splitsAdjusted = actions.all { it.type == "SPLIT" } && b.adjustment != PriceAdjustment.UNADJUSTED
        if (actions.isNotEmpty() && !splitsAdjusted) return status(ReactionStatus.CORPORATE_ACTION_AMBIGUITY,
            "${actions.joinToString { it.description }} during this window can change the price for reasons unrelated to earnings, so no reaction is shown.", withBoth)
        val change = e.close - b.close
        val pct = change.multiplyDivide(HUNDRED, b.close)
        val note = actions.takeIf { it.isNotEmpty() }?.let { listOf("${it.joinToString { a -> a.description }}; both prices are split-adjusted, so the comparison accounts for it.") }.orEmpty()
        return withBoth(base).copy(status = if (event.session == EarningsTime.UNKNOWN) ReactionStatus.EVENT_TIME_UNKNOWN else ReactionStatus.AVAILABLE,
            statusMessage = if (event.session == EarningsTime.UNKNOWN) "The announcement time isn't known, so this is a broader comparison: the last close before ${DAY.format(date)} to the close of the ${ordinal(window.sessions)} session after it." else null,
            absoluteChange = change.toString(), percentChange = pct.toString(), currency = b.currency, adjustment = b.adjustment, warnings = warnings + note)
    }

    /** Five sessions before session 1 through the 5th session after (only sessions that have closed). */
    fun history(event: EarningsEvent, reportId: String, series: PriceSeries?, calendar: ExchangeCalendar?, now: Instant): EarningsPriceHistory {
        val date = LocalDate.parse(event.date)
        val label = "Earnings: ${DAY.format(date)}, " + when (event.session) {
            EarningsTime.AFTER_CLOSE -> "after market close"; EarningsTime.BEFORE_OPEN -> "before market open"
            EarningsTime.DURING_MARKET -> "during market hours"; EarningsTime.UNKNOWN -> "time not confirmed"
        }
        if (calendar == null || series == null) return EarningsPriceHistory(reportId, eventDate = event.date, eventLabel = label, freshness = DataFreshness.UNAVAILABLE)
        val plan = plan(event.session, date, calendar)
        var start = plan.first; repeat(5) { start = calendar.previous(start) }
        val sessions = calendar.sessions(start, calendar.nth(plan.first, 5)).filter { closed(it, calendar, now) }
        val points = sessions.map { d -> PriceHistoryPoint(d.toString(), series.bars[d]?.close?.toString(), calendar.isEarlyClose(d)) }
        val bar = series.bars.values.firstOrNull()
        return EarningsPriceHistory(reportId, points, event.date, plan.first.toString(), label, bar?.currency, bar?.adjustment,
            points.filter { it.close == null }.map { it.date }, series.source, series.freshness)
    }

    private fun ordinal(n: Int) = when (n) { 1 -> "first"; 3 -> "third"; 5 -> "fifth"; else -> "${n}th" }
}

// ---------- Sources ----------

/**
 * Daily closes from the existing [org.example.stocksteps.service.PriceChartService] (one cached
 * range request per symbol; FMP `historical-price-eod/light` in REAL, split-adjusted; fixtures in
 * MOCK). Currency comes from the cached company profile. The source has no corporate-action data.
 */
class ChartReactionPriceSource(
    private val charts: org.example.stocksteps.service.PriceChartService,
    private val stocks: org.example.stocksteps.service.StockService,
    private val label: String,
    private val clock: Clock
) : ReactionPriceSource {
    override suspend fun series(symbol: String): PriceSeries {
        val currency = runCatching { stocks.getProfile(symbol)?.currency }.getOrNull()
        val bars = charts.getDailyCloses(symbol).mapNotNull { p ->
            val date = runCatching { LocalDate.parse(p.time.take(10)) }.getOrNull() ?: return@mapNotNull null
            val close = EarningsMath.decimal(p.close) ?: return@mapNotNull null
            date to DailyBar(date, close, currency, PriceAdjustment.SPLIT_ADJUSTED)
        }.toMap()
        return PriceSeries(bars, emptyList(), actionsKnown = false, source = label, fetchedAt = clock.instant())
    }
}

/**
 * MOCK only: deterministic closes for the fictional "StockSteps Demo" companies
 * (`fixtures/earnings/price-scenarios.json`, generated with the earnings fixtures), including
 * corporate actions and per-day currency/basis overrides; other symbols use [fallback].
 */
class FixtureReactionPriceSource(private val fallback: ReactionPriceSource, private val clock: Clock) : ReactionPriceSource {
    @kotlinx.serialization.Serializable private data class Override(val currency: String? = null, val adjustment: PriceAdjustment? = null)
    @kotlinx.serialization.Serializable private data class Action(val date: String, val type: String, val description: String)
    @kotlinx.serialization.Serializable private data class Scenario(
        val currency: String, val adjustment: PriceAdjustment = PriceAdjustment.SPLIT_ADJUSTED, val closes: Map<String, String>,
        val overrides: Map<String, Override> = emptyMap(), val actions: List<Action> = emptyList()
    )
    private val scenarios: Map<String, Scenario> by lazy {
        val text = javaClass.classLoader.getResource("fixtures/earnings/price-scenarios.json")?.readText() ?: return@lazy emptyMap()
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(text)
    }
    override suspend fun series(symbol: String): PriceSeries {
        val s = scenarios[symbol.uppercase()] ?: return fallback.series(symbol)
        val bars = s.closes.map { (d, c) ->
            val date = LocalDate.parse(d); val o = s.overrides[d]
            date to DailyBar(date, Decimal.parse(c), o?.currency ?: s.currency, o?.adjustment ?: s.adjustment)
        }.toMap()
        return PriceSeries(bars, s.actions.map { CorporateAction(LocalDate.parse(it.date), it.type, it.description) }, actionsKnown = true,
            source = "StockSteps sample price history", fetchedAt = clock.instant())
    }
}
