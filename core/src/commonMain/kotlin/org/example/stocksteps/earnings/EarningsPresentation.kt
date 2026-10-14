package org.example.stocksteps.earnings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.userdata.AlertsRepository
import org.example.stocksteps.data.userdata.UserApi
import org.example.stocksteps.markets.MarketsPresenter
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.StockStepsApiException
import org.example.stocksteps.portfolio.analytics.AnalyticsDates
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Clock

// ---------- Data access ----------

interface EarningsRemote : PriceReactionRemote {
    suspend fun calendar(query: EarningsCalendarQuery): EarningsCalendarPage
    suspend fun following(query: EarningsCalendarQuery): EarningsCalendarPage
    suspend fun details(symbol: String, signedIn: Boolean): EarningsDetails
    suspend fun ask(symbol: String, question: String): EarningsAnswer
    /** One calendar event by its stable id (public). */
    suspend fun event(id: String): EarningsEventInfo
    /** A company's next announcement (public). */
    suspend fun next(symbol: String): NextEarnings
    /** One published report with server-calculated insights (public, free). */
    suspend fun results(reportId: String): EarningsResultsResponse
    /** The latest published report (404 when there's none). */
    suspend fun latestResults(symbol: String): EarningsResultsResponse
}

/** Public calls for everyone; signed-in calls (following, tier-aware details, AI) through [user]. */
class RemoteEarnings(private val api: StockStepsApi, private val user: UserApi?) : EarningsRemote {
    override suspend fun calendar(query: EarningsCalendarQuery) = api.earningsCalendar(query)
    override suspend fun following(query: EarningsCalendarQuery) = requireNotNull(user) { "Sign in to see companies you follow." }.earningsFollowing(query)
    override suspend fun details(symbol: String, signedIn: Boolean) = if (signedIn && user != null) user.earningsDetails(symbol) else api.earningsDetails(symbol)
    override suspend fun ask(symbol: String, question: String) = requireNotNull(user) { "Sign in first." }.askEarnings(symbol, question)
    override suspend fun event(id: String) = api.earningsEvent(id)
    override suspend fun next(symbol: String) = api.nextEarnings(symbol)
    override suspend fun results(reportId: String) = api.earningsResults(reportId)
    override suspend fun latestResults(symbol: String) = api.latestEarningsResults(symbol)
    override suspend fun priceReaction(reportId: String, window: ReactionWindow) = api.earningsPriceReaction(reportId, window)
}

// ---------- Formatting (display only) ----------

object EarningsFormatter {
    private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun weekday(date: String): Int = ((MarketsPresenter.dayNumber(date) ?: 0) + 3).mod(7) // 0 = Monday
    /** "Wed, Oct 14". */
    fun date(date: String): String = runCatching {
        "${DAYS[weekday(date)].take(3)}, ${MONTHS[date.substring(5, 7).toInt() - 1].take(3)} ${date.substring(8, 10).toInt()}"
    }.getOrDefault(date)
    /** "Wednesday, October 14, 2026" for screen readers. */
    fun spokenDate(date: String): String = runCatching {
        "${DAYS[weekday(date)]}, ${MONTHS[date.substring(5, 7).toInt() - 1]} ${date.substring(8, 10).toInt()}, ${date.take(4)}"
    }.getOrDefault(date)

    fun session(time: EarningsTime) = when (time) {
        EarningsTime.BEFORE_OPEN -> "Before market open"
        EarningsTime.AFTER_CLOSE -> "After market close"
        EarningsTime.DURING_MARKET -> "During market hours"
        EarningsTime.UNKNOWN -> "Time not confirmed"
    }
    fun sessionShort(time: EarningsTime) = when (time) {
        EarningsTime.BEFORE_OPEN -> "BMO"; EarningsTime.AFTER_CLOSE -> "AMC"; EarningsTime.DURING_MARKET -> "Intraday"; EarningsTime.UNKNOWN -> "Time TBA"
    }
    fun dateStatus(status: EarningsDateStatus) = when (status) {
        EarningsDateStatus.CONFIRMED -> "Confirmed"
        EarningsDateStatus.ESTIMATED -> "Estimated date"
        EarningsDateStatus.TENTATIVE -> "Tentative"
        EarningsDateStatus.UNKNOWN -> "Date unknown"
    }

    /** "Earnings in 5 days" — only for a known date; non-confirmed dates say so. */
    fun countdown(event: EarningsEvent, today: String): String? {
        if (event.dateStatus == EarningsDateStatus.UNKNOWN) return null
        val days = (MarketsPresenter.dayNumber(event.date) ?: return null) - (MarketsPresenter.dayNumber(today) ?: return null)
        if (days < 0) return null
        val text = when (days) { 0 -> "Earnings today"; 1 -> "Earnings tomorrow"; else -> "Earnings in $days days" }
        return text + if (event.dateStatus != EarningsDateStatus.CONFIRMED) " (${dateStatus(event.dateStatus).lowercase()})" else ""
    }

    private fun two(value: Double): String {
        val cents = round(abs(value) * 100).toLong()
        return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }
    /**
     * Phase 5D.1B (owner decision 5D1A-2): a null currency means the source didn't provide one, so no symbol is shown: never "$" by default.
     * Pair a currency-less amount with [UNKNOWN_CURRENCY] once per card or section ([currencyNote]).
     */
    private fun symbol(currency: String?) = when (currency) { null -> ""; "USD" -> "$"; "CAD" -> "C$"; else -> "$currency " }

    /** Phase 5D.1B: shown once per card / section that has an amount without a currency. */
    const val UNKNOWN_CURRENCY = "Currency not provided by the source."

    /** [UNKNOWN_CURRENCY] when any of [currencies] belonging to a shown amount is null; null otherwise. */
    fun currencyNote(vararg currencies: String?): String? = UNKNOWN_CURRENCY.takeIf { currencies.isNotEmpty() && currencies.any { it == null } }
    fun eps(value: Double?, currency: String?) = value?.let { (if (it < 0) "−" else "") + symbol(currency) + two(it) } ?: "—"
    fun revenue(value: Double?, currency: String?): String = value?.let {
        val a = abs(it)
        val text = when { a >= 1e12 -> "${two(a / 1e12)}T"; a >= 1e9 -> "${two(a / 1e9)}B"; a >= 1e6 -> "${two(a / 1e6)}M"; else -> two(a) }
        (if (it < 0) "−" else "") + symbol(currency) + text
    } ?: "—"
    fun percent(value: Double?) = value?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + (round(abs(it) * 10) / 10).toString() + "%" }

    /**
     * Phase 5D.1 (E-02): the "not reported yet" estimate sentence, the same on both platforms. Only the estimates that exist are named, so a
     * missing one is never shown as "—" inside the sentence (or as zero); a zero or negative estimate is a real value and is shown. Amounts use
     * [eps] / [revenue] with the estimate's own currency.
     */
    fun estimateSentence(estimate: EarningsEstimate?): String {
        val eps = estimate?.eps?.takeIf { it.isFinite() }?.let { "EPS of ${eps(it, estimate.currency)}" }
        val revenue = estimate?.revenue?.takeIf { it.isFinite() }?.let { "revenue of ${revenue(it, estimate.currency)}" }
        val parts = listOfNotNull(eps, revenue)
        return if (parts.isEmpty()) "Analyst estimates aren't available yet."
            else "Analysts expect ${parts.joinToString(" and ")}." + (currencyNote(estimate?.currency)?.let { " $it" } ?: "")
    }

    /**
     * Phase 5D.1 (E-03): one history-chart tooltip, "Q4 25 · reported $1.20 · est. $1.10", in the same [currency] and formatter as that
     * quarter's history row. A missing value says "not reported" / "no estimate" instead of a dash; numbers are only formatted, never changed.
     */
    fun historyPoint(label: String, actual: Double?, estimate: Double?, currency: String?): String =
        "$label · " + (actual?.let { "reported ${eps(it, currency)}" } ?: "not reported") + " · " + (estimate?.let { "est. ${eps(it, currency)}" } ?: "no estimate")

    /** "Beat (+5.9%)" — text, so meaning never depends on color. */
    /**
     * Phase 5D.3: the Details takeaway headline from the server's EPS classification and surprise — never inferred: UNAVAILABLE (e.g. no
     * estimate) gives null, and a beat/miss without a surprise percent omits the amount.
     */
    fun epsHeadline(r: SurpriseResult?): String? {
        val by = r?.percent?.let { p -> percent(abs(p))?.removePrefix("+")?.let { " by $it" } }.orEmpty()
        return when (r?.classification) {
            Classification.BEAT -> "EPS beat estimates$by."
            Classification.MISS -> "EPS missed estimates$by."
            Classification.MET -> "EPS met estimates."
            else -> null
        }
    }

    fun result(r: SurpriseResult?): String? = r?.let { if (it.classification == Classification.UNAVAILABLE) null else it.classification.label + (percent(it.percent)?.let { p -> " ($p)" } ?: "") }
}

// ---------- Earnings Details ----------

data class MetricLine(val label: String, val value: String, val detail: String? = null)

data class ResultCard(
    val title: String,
    /** "Beat (+5.9%)", "Miss", "In line", or "Not comparable". */
    val headline: String,
    val classification: Classification,
    val lines: List<MetricLine>,
    val note: String?,
    /** Phase 5D.3: the three numbers a beginner scans first (EPS: reported, estimate, surprise; revenue: reported, estimate, YoY growth). */
    val keyMetrics: List<MetricLine> = emptyList(),
    /** Phase 5D.3: the remaining [lines] (difference, basis, quarter-over-quarter), shown smaller below [keyMetrics]. */
    val secondary: List<MetricLine> = emptyList()
) {
    val accessibility: String get() = "$title: $headline. " + lines.joinToString(" ") { "${it.label} ${it.value}." } + (note?.let { " $it" } ?: "")
}

data class HistoryRowView(
    val id: String, val period: String, val date: String, val eps: String, val revenue: String, val growth: String?, val lines: List<MetricLine>,
    /** Phase 5D.2: the server's EPS classification for the row's badge (`EarningsDisplay.classification`); never recomputed. */
    val classification: Classification = Classification.UNAVAILABLE,
    /** Phase 5D.2: "C$1.94 vs C$2.00 est." — the same amounts and currency as the chart tooltip for this quarter. */
    val epsVsEstimate: String? = null,
    /** Phase 5D.2: the EPS surprise percent ("-3.0%"), when the server provided one. */
    val surprise: String? = null
) {
    val accessibility get() = "$period, reported ${EarningsFormatter.spokenDate(date)}. EPS $eps. Revenue $revenue." + (growth?.let { " Revenue growth $it year over year." } ?: "")
}

data class ChartSeries(val label: String, val values: List<Double?>)

data class EarningsDetailsState(
    val symbol: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val details: EarningsDetails? = null,
    val headerPeriod: String? = null,
    val headerDate: String? = null,
    val headerSession: String? = null,
    val headerDateStatus: String? = null,
    val next: String? = null,
    val eps: ResultCard? = null,
    val revenue: ResultCard? = null,
    val reaction: List<MetricLine> = emptyList(),
    val reactionSentence: String? = null,
    /** Phase 5D.3: the measured change first ("+0.2%"), its direction for the arrow, Before/After, and the window/source details (collapsed). */
    val reactionHeadline: String? = null,
    val reactionDirection: org.example.stocksteps.companydetail.PriceDirection? = null,
    val reactionKey: List<MetricLine> = emptyList(),
    val reactionDetails: List<MetricLine> = emptyList(),
    /** Phase 5D.3: "EPS missed estimates by 3.0%." from the server's EPS classification; the server summary when there is none. */
    val headline: String? = null,
    val history: List<HistoryRowView> = emptyList(),
    /** Oldest → newest, aligned: EPS actual vs estimate, and revenue growth. */
    val epsChart: List<ChartSeries> = emptyList(),
    val chartLabels: List<String> = emptyList(),
    /** Phase 5D.1 (E-03): one tooltip per [chartLabels] slot, with the quarter's currency (`EarningsFormatter.historyPoint`). */
    val epsChartDetails: List<String> = emptyList(),
    /**
     * Phase 5D.4: the history chart's y-axis labels, top to bottom (highest, middle, lowest of the shown reported and estimated EPS — the same
     * range the chart already scales to), in the quarters' currency. Empty with fewer than two reported values or mixed currencies.
     */
    val epsChartYLabels: List<String> = emptyList(),
    /** Phase 5D.1B: [EarningsFormatter.UNKNOWN_CURRENCY] once for the history chart and rows when a shown quarter has no currency. */
    val historyNote: String? = null,
    /** Phase 5D.1 (E-02): the next report's estimate sentence (`EarningsFormatter.estimateSentence`); null without a next report. */
    val nextEstimate: String? = null,
    val signedIn: Boolean = false,
    val reminder: AlertRule? = null,
    val reminderBusy: Boolean = false,
    val answer: EarningsAnswer? = null,
    val asking: Boolean = false,
    /** Free users tapping AI research: show the upgrade experience; nothing was sent. */
    val upgradeRequired: Boolean = false,
    val message: String? = null
) {
    val plus: Boolean get() = details?.plus == true
}

/** Earnings Details for one company; reminders reuse the existing alerts; AI is StockSteps+ only. */
class EarningsDetailsPresenter(
    val symbol: String,
    private val remote: EarningsRemote,
    private val scope: CoroutineScope,
    private val alerts: AlertsRepository? = null,
    private val today: () -> String = { Clock.System.now().toString().take(10) }
) {
    private val mutable = MutableStateFlow(EarningsDetailsState(symbol.uppercase()))
    val state: StateFlow<EarningsDetailsState> = mutable.asStateFlow()
    private val refreshes = MutableStateFlow(0)

    init {
        scope.launch {
            combine((alerts?.state?.map { it.uid }?.distinctUntilChanged() ?: flowOf(null)), refreshes) { uid, r -> uid to r }.collectLatest { (uid, _) -> load(uid != null) }
        }
        alerts?.let { repository ->
            scope.launch {
                repository.state.collect { s ->
                    val rule = s.value?.alerts.orEmpty().firstOrNull { it.type == AlertType.EARNINGS && it.instrument.symbol.equals(symbol, ignoreCase = true) }
                    mutable.update { it.copy(signedIn = s.uid != null, reminder = rule) }
                }
            }
        }
    }

    private suspend fun load(signedIn: Boolean) {
        mutable.update { it.copy(loading = true, error = null) }
        try {
            val details = remote.details(symbol, signedIn)
            mutable.update { build(it.copy(loading = false), details) }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            mutable.update { it.copy(loading = false, error = (cause as? StockStepsApiException)?.error?.message ?: "Earnings couldn't be loaded. Try again.") }
        }
    }

    private fun card(title: String, r: SurpriseResult, money: (Double?, String?) -> String, extra: List<MetricLine> = emptyList(), third: MetricLine? = null): ResultCard {
        val reported = MetricLine("Reported", money(r.actual, r.currency))
        val estimate = MetricLine("Estimate", money(r.estimate, r.currency))
        val difference = r.absolute?.let { MetricLine("Difference", (if (it > 0) "+" else "") + money(it, r.currency)) }
        val surprise = r.percent?.let { MetricLine("Surprise", EarningsFormatter.percent(it)!!) }
        val basis = r.basis?.let { MetricLine("Basis", it) }
        // 5D.3: the third key number is the surprise ("—" when the server gave none), or [third] (revenue: YoY growth) when given.
        val key = listOf(reported, estimate, third ?: surprise ?: MetricLine("Surprise", "—"))
        val all = listOfNotNull(reported, estimate, difference, surprise, basis) + extra
        return ResultCard(title, EarningsFormatter.result(r) ?: Classification.UNAVAILABLE.label, r.classification, all,
            // 5D.1B: one currency caption per card when its amounts have no currency.
            listOfNotNull(r.reason, EarningsFormatter.currencyNote(r.currency).takeIf { r.actual != null || r.estimate != null }).joinToString(" ").ifEmpty { null },
            key, all.filterNot { line -> key.any { it === line } })
    }

    private fun build(state: EarningsDetailsState, d: EarningsDetails): EarningsDetailsState {
        val e = d.event
        val yoy = d.revenueGrowthYoY?.let { MetricLine("YoY growth", EarningsFormatter.percent(it)!!) }
        val growth = listOfNotNull(yoy, d.revenueGrowthQoQ?.let { MetricLine("Growth vs last quarter", EarningsFormatter.percent(it)!!, "Can reflect seasonality.") })
        val reaction = d.reaction?.takeIf { it.available }?.let { r ->
            listOfNotNull(
                MetricLine("Before", "${EarningsFormatter.eps(r.baselineClose, r.currency)} · ${EarningsFormatter.date(r.baselineDate!!)}"),
                MetricLine("After", "${EarningsFormatter.eps(r.endClose, r.currency)} · ${EarningsFormatter.date(r.endDate!!)}"),
                MetricLine("Change", EarningsFormatter.percent(r.changePercent)!! + if (r.approximate) " (approximate)" else ""),
                r.marketChangePercent?.let { MetricLine(r.marketLabel ?: "Market", EarningsFormatter.percent(it)!!, "Same window") },
                MetricLine("Window", r.window.orEmpty()),
                MetricLine("Source", listOfNotNull(r.source, r.asOf?.take(10)).joinToString(" · "))
            )
        }.orEmpty()
        val sentence = d.reaction?.let { r -> if (r.available) r.changePercent?.let { c ->
            "The stock ${if (c >= 0) "rose" else "fell"} ${EarningsFormatter.percent(abs(c))!!.removePrefix("+")} over the measured earnings window${if (r.approximate) " (approximate)" else ""}. Other news and market moves can also affect the price." +
                (EarningsFormatter.currencyNote(r.currency)?.let { " $it" } ?: "")
        } else r.reason }
        val chronological = d.history.reversed()
        return state.copy(
            details = d,
            headerPeriod = e?.period, headerDate = e?.let { EarningsFormatter.date(it.date) }, headerSession = e?.let { EarningsFormatter.session(it.session) },
            headerDateStatus = e?.let { EarningsFormatter.dateStatus(it.dateStatus) },
            next = d.next?.takeIf { it.id != e?.id }?.let { n -> "Next: ${n.period} · ${EarningsFormatter.date(n.date)} · ${EarningsFormatter.session(n.session)} · ${EarningsFormatter.dateStatus(n.dateStatus)}" },
            eps = e?.let { card("EPS", d.eps, EarningsFormatter::eps) },
            revenue = e?.let { card("Revenue", d.revenue, EarningsFormatter::revenue, growth, yoy) },
            reaction = reaction, reactionSentence = sentence,
            reactionHeadline = reaction.firstOrNull { it.label == "Change" }?.value,
            reactionDirection = d.reaction?.takeIf { it.available }?.changePercent?.let {
                when { it > 0 -> org.example.stocksteps.companydetail.PriceDirection.UP; it < 0 -> org.example.stocksteps.companydetail.PriceDirection.DOWN
                    else -> org.example.stocksteps.companydetail.PriceDirection.UNCHANGED } },
            reactionKey = reaction.filter { it.label == "Before" || it.label == "After" },
            reactionDetails = reaction.filterNot { it.label == "Before" || it.label == "After" || it.label == "Change" },
            headline = EarningsFormatter.epsHeadline(d.eps) ?: d.summary,
            history = d.history.map { h ->
                HistoryRowView(h.event.id, h.event.period, h.event.date,
                    EarningsFormatter.result(h.eps) ?: EarningsFormatter.eps(h.eps.actual, h.eps.currency),
                    EarningsFormatter.result(h.revenue) ?: EarningsFormatter.revenue(h.revenue.actual, h.revenue.currency),
                    h.revenueGrowthYoY?.let { EarningsFormatter.percent(it) },
                    listOf(MetricLine("EPS", "${EarningsFormatter.eps(h.eps.actual, h.eps.currency)} vs ${EarningsFormatter.eps(h.eps.estimate, h.eps.currency)} est."),
                        MetricLine("Revenue", "${EarningsFormatter.revenue(h.revenue.actual, h.revenue.currency)} vs ${EarningsFormatter.revenue(h.revenue.estimate, h.revenue.currency)} est."),
                        MetricLine("Reported", EarningsFormatter.date(h.event.date) + " · " + EarningsFormatter.session(h.event.session))),
                    classification = h.eps.classification,
                    epsVsEstimate = "${EarningsFormatter.eps(h.eps.actual, h.eps.currency)} vs ${EarningsFormatter.eps(h.eps.estimate, h.eps.currency)} est.",
                    surprise = EarningsFormatter.percent(h.eps.percent))
            },
            epsChart = listOf(ChartSeries("Reported EPS", chronological.map { it.eps.actual }), ChartSeries("Estimate", chronological.map { it.eps.estimate })),
            chartLabels = chronological.map { "Q${it.event.fiscalQuarter} ${it.event.fiscalYear % 100}" },
            epsChartDetails = chronological.map { EarningsFormatter.historyPoint("Q${it.event.fiscalQuarter} ${it.event.fiscalYear % 100}", it.eps.actual, it.eps.estimate, it.eps.currency) },
            nextEstimate = d.next?.let { EarningsFormatter.estimateSentence(it.estimate) },
            historyNote = EarningsFormatter.currencyNote(*d.history.map { it.eps.currency }.toTypedArray()),
            epsChartYLabels = epsAxis(chronological.map { it.eps.actual }, chronological.map { it.eps.estimate }, d.history.map { it.eps.currency })
        )
    }

    /** Phase 5D.4: display-only axis labels from values already shown (no new data); see [EarningsDetailsState.epsChartYLabels]. */
    internal fun epsAxis(reported: List<Double?>, estimate: List<Double?>, currencies: List<String?>): List<String> {
        if (reported.count { it != null } < 2 || currencies.distinct().size > 1) return emptyList()
        val all = (reported + estimate).filterNotNull()
        val high = all.max(); val low = all.min()
        val currency = currencies.firstOrNull()
        return if (high == low) listOf(EarningsFormatter.eps(high, currency))
        else listOf(high, (high + low) / 2, low).map { EarningsFormatter.eps(it, currency) }
    }

    fun refresh() { refreshes.value++ }

    /** StockSteps+ only: free users get the upgrade experience and nothing is sent. */
    fun ask(question: String) {
        val current = mutable.value
        if (!current.signedIn) { mutable.update { it.copy(message = "Sign in and subscribe to StockSteps+ to ask about earnings.") }; return }
        if (!current.plus) { mutable.update { it.copy(upgradeRequired = true) }; return }
        scope.launch {
            mutable.update { it.copy(asking = true, answer = null) }
            try {
                val answer = remote.ask(symbol, question)
                mutable.update { it.copy(asking = false, answer = answer) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { it.copy(asking = false, message = (cause as? StockStepsApiException)?.error?.message ?: "AI research isn't available right now.") }
            }
        }
    }

    fun dismissUpgrade() = mutable.update { it.copy(upgradeRequired = false) }
    fun dismissMessage() = mutable.update { it.copy(message = null) }
}
