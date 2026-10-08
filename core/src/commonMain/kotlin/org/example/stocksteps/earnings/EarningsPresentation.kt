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
}

/** Public calls for everyone; signed-in calls (following, tier-aware details, AI) through [user]. */
class RemoteEarnings(private val api: StockStepsApi, private val user: UserApi?) : EarningsRemote {
    override suspend fun calendar(query: EarningsCalendarQuery) = api.earningsCalendar(query)
    override suspend fun following(query: EarningsCalendarQuery) = requireNotNull(user) { "Sign in to see companies you follow." }.earningsFollowing(query)
    override suspend fun details(symbol: String, signedIn: Boolean) = if (signedIn && user != null) user.earningsDetails(symbol) else api.earningsDetails(symbol)
    override suspend fun ask(symbol: String, question: String) = requireNotNull(user) { "Sign in first." }.askEarnings(symbol, question)
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
        EarningsTime.UNKNOWN -> "Time not announced"
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

/** A calendar row, ready to render on both platforms. */
data class EarningsRowView(
    val id: String,
    val symbol: String,
    val name: String,
    val exchange: String?,
    val logoUrl: String?,
    val date: String,
    val dateText: String,
    val sessionText: String,
    val dateStatusText: String,
    val confirmed: Boolean,
    val status: EarningsStatus,
    val countdown: String?,
    val expectation: String,
    val epsResult: String?,
    val revenueResult: String?,
    val following: String?,
    val reminder: Boolean,
    val previousDate: String?
) {
    val accessibility: String get() = buildString {
        append("$name, $symbol. ${status.label}. ")
        append(EarningsFormatter.spokenDate(date)).append(", ").append(sessionText).append(". ").append(dateStatusText).append(". ")
        previousDate?.let { append("Moved from ${EarningsFormatter.spokenDate(it)}. ") }
        epsResult?.let { append("EPS $it. ") }; revenueResult?.let { append("Revenue $it. ") }
        if (epsResult == null && revenueResult == null) append("$expectation. ")
        following?.let { append("$it. ") }
        if (reminder) append("Reminder on.")
    }
}

fun EarningsCalendarItem.row(today: String, reminders: Set<String>): EarningsRowView {
    val e = event
    val est = e.estimate
    return EarningsRowView(
        e.id, e.symbol, e.name, e.exchange, e.logoUrl, e.date, EarningsFormatter.date(e.date), EarningsFormatter.session(e.session),
        EarningsFormatter.dateStatus(e.dateStatus), e.dateStatus == EarningsDateStatus.CONFIRMED, status,
        if (status == EarningsStatus.UPCOMING) EarningsFormatter.countdown(e, today) else null,
        listOfNotNull(est?.eps?.let { "EPS est. ${EarningsFormatter.eps(it, est.currency)}" }, est?.revenue?.let { "Rev. est. ${EarningsFormatter.revenue(it, est.currency)}" })
            .joinToString(" · ").ifBlank { "No estimates available" },
        EarningsFormatter.result(eps), EarningsFormatter.result(revenue),
        following.takeIf { it.isNotEmpty() }?.let { reasons ->
            listOfNotNull(if (FollowReason.PORTFOLIO in reasons) "In your portfolio" + (sharesHeld?.let { " · ${round(it * 1000) / 1000} shares" } ?: "") else null,
                if (FollowReason.WATCHLIST in reasons) "On your watchlist" else null).joinToString(" · ")
        },
        e.symbol.uppercase() in reminders, e.previousDate
    )
}

// ---------- Earnings Center ----------

enum class EarningsTab(val label: String) { UPCOMING("Upcoming"), RESULTS("Results"), FOLLOWING("Following") }
enum class EarningsRange(val label: String) { THIS_WEEK("This week"), NEXT_WEEK("Next week"), MONTH("Next 30 days"), LAST_WEEK("Last week"), LAST_MONTH("Last 30 days") }

data class EarningsFilters(
    /** "US", "CA". */
    val markets: Set<String> = emptySet(),
    val exchanges: Set<String> = emptySet(),
    val sessions: Set<EarningsTime> = emptySet()
) { val count: Int get() = markets.size + exchanges.size + sessions.size }

data class EarningsCenterState(
    val tab: EarningsTab = EarningsTab.UPCOMING,
    val range: EarningsRange = EarningsRange.THIS_WEEK,
    val from: String = "",
    val to: String = "",
    val filters: EarningsFilters = EarningsFilters(),
    val rows: List<EarningsRowView> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val notes: List<String> = emptyList(),
    val stale: Boolean = false,
    val sampleData: Boolean = false,
    val signedIn: Boolean = false
) {
    val ranges: List<EarningsRange> get() = if (tab == EarningsTab.RESULTS) listOf(EarningsRange.LAST_WEEK, EarningsRange.THIS_WEEK, EarningsRange.LAST_MONTH)
        else listOf(EarningsRange.THIS_WEEK, EarningsRange.NEXT_WEEK, EarningsRange.MONTH)
    /** Rows grouped by date, in order. */
    val days: List<Pair<String, List<EarningsRowView>>> get() = rows.groupBy { it.date }.toList()
}

/**
 * Earnings Center (Upcoming / Results / Following). Date-bounded server queries (never a whole
 * year), paged by cursor; obsolete loads are cancelled. Reminder state comes from the existing alerts.
 */
class EarningsCenterPresenter(
    private val remote: EarningsRemote,
    private val scope: CoroutineScope,
    private val alerts: AlertsRepository? = null,
    private val today: () -> String = { Clock.System.now().toString().take(10) }
) {
    private val mutable = MutableStateFlow(EarningsCenterState())
    val state: StateFlow<EarningsCenterState> = mutable.asStateFlow()
    private val key = MutableStateFlow<Triple<EarningsTab, EarningsRange, EarningsFilters>?>(null)
    private val refreshes = MutableStateFlow(0)
    private var page: EarningsCalendarPage? = null
    private var query: EarningsCalendarQuery? = null
    private var moreJob: Job? = null
    private var items: List<EarningsCalendarItem> = emptyList()

    private fun reminders(): Set<String> = alerts?.state?.value?.value?.alerts.orEmpty()
        .filter { it.type == AlertType.EARNINGS && it.status == AlertStatus.ACTIVE }.map { it.instrument.symbol.uppercase() }.toSet()

    init {
        alerts?.let { repository ->
            scope.launch {
                repository.state.collect { s ->
                    val set = reminders()
                    mutable.update { it.copy(signedIn = s.uid != null, rows = items.map { item -> item.row(today(), set) }) }
                }
            }
        }
        scope.launch {
            combine(key.filterNotNull(), refreshes) { k, r -> k to r }.collectLatest { (k, _) -> load(k.first, k.second, k.third) }
        }
    }

    /** Dates for a range relative to today (weeks start on Monday). */
    fun range(range: EarningsRange, today: String = today()): Pair<String, String> {
        val monday = AnalyticsDates.plusDays(today, -EarningsFormatter.weekday(today))
        return when (range) {
            EarningsRange.THIS_WEEK -> monday to AnalyticsDates.plusDays(monday, 6)
            EarningsRange.NEXT_WEEK -> AnalyticsDates.plusDays(monday, 7) to AnalyticsDates.plusDays(monday, 13)
            EarningsRange.MONTH -> today to AnalyticsDates.plusDays(today, 30)
            EarningsRange.LAST_WEEK -> AnalyticsDates.plusDays(monday, -7) to AnalyticsDates.plusDays(monday, -1)
            EarningsRange.LAST_MONTH -> AnalyticsDates.plusDays(today, -30) to today
        }
    }

    private suspend fun load(tab: EarningsTab, range: EarningsRange, filters: EarningsFilters) {
        moreJob?.cancel()
        val (from, to) = range(range)
        val q = EarningsCalendarQuery(from, to, exchanges = filters.exchanges.toList(), countries = filters.markets.toList(), sessions = filters.sessions.toList(),
            view = when (tab) { EarningsTab.UPCOMING -> "upcoming"; EarningsTab.RESULTS -> "results"; EarningsTab.FOLLOWING -> null })
        query = q
        mutable.update { it.copy(tab = tab, range = range, filters = filters, from = from, to = to, loading = true, error = null) }
        if (tab == EarningsTab.FOLLOWING && alerts?.state?.value?.uid == null) {
            items = emptyList()
            mutable.update { it.copy(loading = false, rows = emptyList(), total = 0, hasMore = false, error = null, notes = listOf("Sign in to see earnings for companies in your watchlists and portfolios.")) }
            return
        }
        try {
            val result = if (tab == EarningsTab.FOLLOWING) remote.following(q) else remote.calendar(q)
            page = result
            items = result.items
            mutable.update { it.copy(loading = false, rows = items.map { item -> item.row(today(), reminders()) }, total = result.total, hasMore = result.nextCursor != null,
                notes = result.notes, stale = result.stale, sampleData = result.sampleData) }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            mutable.update { it.copy(loading = false, error = (cause as? StockStepsApiException)?.error?.message ?: "Earnings couldn't be loaded. Try again.") }
        }
    }

    fun start() { if (key.value == null) key.value = Triple(EarningsTab.UPCOMING, EarningsRange.THIS_WEEK, EarningsFilters()) }
    fun selectTab(tab: EarningsTab) {
        val current = key.value ?: Triple(tab, EarningsRange.THIS_WEEK, EarningsFilters())
        val range = if (tab == EarningsTab.RESULTS) EarningsRange.LAST_MONTH else if (current.second in listOf(EarningsRange.LAST_WEEK, EarningsRange.LAST_MONTH)) EarningsRange.THIS_WEEK else current.second
        key.value = Triple(tab, range, current.third)
    }
    fun selectRange(range: EarningsRange) { key.value = key.value?.copy(second = range) }
    fun setFilters(filters: EarningsFilters) { key.value = key.value?.copy(third = filters) }
    fun refresh() { refreshes.value++ }
    fun loadMore() {
        val q = query ?: return
        val cursor = page?.nextCursor ?: return
        if (moreJob?.isActive == true) return
        val tab = mutable.value.tab
        moreJob = scope.launch {
            mutable.update { it.copy(loadingMore = true) }
            try {
                val next = if (tab == EarningsTab.FOLLOWING) remote.following(q.copy(cursor = cursor)) else remote.calendar(q.copy(cursor = cursor))
                page = next
                items = (items + next.items).distinctBy { it.event.id }
                mutable.update { it.copy(loadingMore = false, rows = items.map { item -> item.row(today(), reminders()) }, hasMore = next.nextCursor != null) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { it.copy(loadingMore = false) }
            }
        }
    }
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
