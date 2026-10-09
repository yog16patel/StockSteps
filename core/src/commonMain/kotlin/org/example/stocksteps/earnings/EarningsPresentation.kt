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

interface EarningsRemote {
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
    private fun symbol(currency: String?) = when (currency) { null, "USD" -> "$"; "CAD" -> "C$"; else -> "$currency " }
    fun eps(value: Double?, currency: String?) = value?.let { (if (it < 0) "−" else "") + symbol(currency) + two(it) } ?: "—"
    fun revenue(value: Double?, currency: String?): String = value?.let {
        val a = abs(it)
        val text = when { a >= 1e12 -> "${two(a / 1e12)}T"; a >= 1e9 -> "${two(a / 1e9)}B"; a >= 1e6 -> "${two(a / 1e6)}M"; else -> two(a) }
        (if (it < 0) "−" else "") + symbol(currency) + text
    } ?: "—"
    fun percent(value: Double?) = value?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + (round(abs(it) * 10) / 10).toString() + "%" }

    /** "Beat (+5.9%)" — text, so meaning never depends on color. */
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
    val note: String?
) {
    val accessibility: String get() = "$title: $headline. " + lines.joinToString(" ") { "${it.label} ${it.value}." } + (note?.let { " $it" } ?: "")
}

data class HistoryRowView(val id: String, val period: String, val date: String, val eps: String, val revenue: String, val growth: String?, val lines: List<MetricLine>) {
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
    val history: List<HistoryRowView> = emptyList(),
    /** Oldest → newest, aligned: EPS actual vs estimate, and revenue growth. */
    val epsChart: List<ChartSeries> = emptyList(),
    val chartLabels: List<String> = emptyList(),
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

    private fun card(title: String, r: SurpriseResult, money: (Double?, String?) -> String, extra: List<MetricLine> = emptyList()) = ResultCard(
        title, EarningsFormatter.result(r) ?: Classification.UNAVAILABLE.label, r.classification,
        listOfNotNull(MetricLine("Reported", money(r.actual, r.currency)), MetricLine("Estimate", money(r.estimate, r.currency)),
            r.absolute?.let { MetricLine("Difference", (if (it > 0) "+" else "") + money(it, r.currency)) },
            r.percent?.let { MetricLine("Surprise", EarningsFormatter.percent(it)!!) }, r.basis?.let { MetricLine("Basis", it) }) + extra,
        r.reason
    )

    private fun build(state: EarningsDetailsState, d: EarningsDetails): EarningsDetailsState {
        val e = d.event
        val growth = listOfNotNull(d.revenueGrowthYoY?.let { MetricLine("Growth vs a year ago", EarningsFormatter.percent(it)!!) },
            d.revenueGrowthQoQ?.let { MetricLine("Growth vs last quarter", EarningsFormatter.percent(it)!!, "Can reflect seasonality.") })
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
            "The stock ${if (c >= 0) "rose" else "fell"} ${EarningsFormatter.percent(abs(c))!!.removePrefix("+")} over the measured earnings window${if (r.approximate) " (approximate)" else ""}. Other news and market moves can also affect the price."
        } else r.reason }
        val chronological = d.history.reversed()
        return state.copy(
            details = d,
            headerPeriod = e?.period, headerDate = e?.let { EarningsFormatter.date(it.date) }, headerSession = e?.let { EarningsFormatter.session(it.session) },
            headerDateStatus = e?.let { EarningsFormatter.dateStatus(it.dateStatus) },
            next = d.next?.takeIf { it.id != e?.id }?.let { n -> "Next: ${n.period} · ${EarningsFormatter.date(n.date)} · ${EarningsFormatter.session(n.session)} · ${EarningsFormatter.dateStatus(n.dateStatus)}" },
            eps = e?.let { card("EPS", d.eps, EarningsFormatter::eps) },
            revenue = e?.let { card("Revenue", d.revenue, EarningsFormatter::revenue, growth) },
            reaction = reaction, reactionSentence = sentence,
            history = d.history.map { h ->
                HistoryRowView(h.event.id, h.event.period, h.event.date,
                    EarningsFormatter.result(h.eps) ?: EarningsFormatter.eps(h.eps.actual, h.eps.currency),
                    EarningsFormatter.result(h.revenue) ?: EarningsFormatter.revenue(h.revenue.actual, h.revenue.currency),
                    h.revenueGrowthYoY?.let { EarningsFormatter.percent(it) },
                    listOf(MetricLine("EPS", "${EarningsFormatter.eps(h.eps.actual, h.eps.currency)} vs ${EarningsFormatter.eps(h.eps.estimate, h.eps.currency)} est."),
                        MetricLine("Revenue", "${EarningsFormatter.revenue(h.revenue.actual, h.revenue.currency)} vs ${EarningsFormatter.revenue(h.revenue.estimate, h.revenue.currency)} est."),
                        MetricLine("Reported", EarningsFormatter.date(h.event.date) + " · " + EarningsFormatter.session(h.event.session))))
            },
            epsChart = listOf(ChartSeries("Reported EPS", chronological.map { it.eps.actual }), ChartSeries("Estimate", chronological.map { it.eps.estimate })),
            chartLabels = chronological.map { "Q${it.event.fiscalQuarter} ${it.event.fiscalYear % 100}" }
        )
    }

    fun refresh() { refreshes.value++ }

    /** Basic reminders are free (day before / day of); lead days and results are StockSteps+ (server-enforced). */
    fun setReminder(timing: EarningsTiming?, leadDays: Int? = null, results: Boolean = false, surprisePercent: Double? = null) {
        val repository = alerts ?: return
        val current = mutable.value
        scope.launch {
            mutable.update { it.copy(reminderBusy = true) }
            try {
                val existing = current.reminder
                when {
                    timing == null && existing != null -> repository.delete(existing.id)
                    timing == null -> Unit
                    existing != null -> repository.update(existing.id, UpdateAlertRequest(status = AlertStatus.ACTIVE, earningsTiming = timing,
                        earningsLeadDays = leadDays, earningsResults = results, earningsSurprisePercent = surprisePercent))
                    else -> repository.create(CreateAlertRequest(InstrumentRef(symbol.uppercase(), current.details?.name, current.details?.event?.exchange),
                        AlertType.EARNINGS, earningsTiming = timing, earningsLeadDays = leadDays, earningsResults = results.takeIf { it }, earningsSurprisePercent = surprisePercent))
                }
                mutable.update { it.copy(reminderBusy = false, message = if (timing == null) "Earnings reminder turned off." else "Earnings reminder saved.") }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { it.copy(reminderBusy = false, message = (cause as? StockStepsApiException)?.error?.message ?: "The reminder couldn't be saved.") }
            }
        }
    }

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
