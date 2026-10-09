package org.example.stocksteps.earnings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.EarningsDateStatus
import org.example.stocksteps.model.EarningsTime
import org.example.stocksteps.network.StockStepsApiException
import org.example.stocksteps.portfolio.analytics.AnalyticsDates
import kotlin.time.Clock

// Earnings Intelligence Lite, Phase 1: the Earnings Calendar (dates, timing and status only). Results,
// surprises, reactions and AI stay on Earnings Details; nothing here computes or shows them.

/**
 * Calendar status, only as far as the source supports it. REPORTED needs reported figures or an
 * explicit source flag; POSTPONED and CANCELED need an explicit source flag. A past date with no
 * results is UNKNOWN, never REPORTED.
 */
@Serializable enum class EarningsEventStatus(val label: String) {
    SCHEDULED("Scheduled"), REPORTED("Reported"), POSTPONED("Postponed"), CANCELED("Canceled"), UNKNOWN("Status not confirmed")
}

/** Where the data shown came from: just fetched, the server cache, an old copy after a source failure, or nothing. */
@Serializable enum class DataFreshness { FRESH, CACHED, STALE, UNAVAILABLE }

/** One event with its metadata (`GET /api/v1/earnings/events/{eventId}`). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsEventInfo(
    val item: EarningsCalendarItem,
    val asOf: String,
    val freshness: DataFreshness = DataFreshness.FRESH,
    val fetchedAt: String? = null,
    @EncodeDefault val notes: List<String> = emptyList(),
    val sampleData: Boolean = false
)

/** A company's next announcement (`GET /api/v1/earnings/company/{symbol}/next`); [event] is null when none is known. */
@Serializable
data class NextEarnings(
    val symbol: String,
    val event: EarningsEvent? = null,
    val eventStatus: EarningsEventStatus? = null,
    val asOf: String,
    val freshness: DataFreshness = DataFreshness.FRESH,
    val sampleData: Boolean = false
)

/** Deterministic calendar rules shared by the server, Android and iOS. Dates are exchange-local yyyy-MM-dd. */
object EarningsCalendarRules {
    const val MAX_SEARCH_DAYS = 62
    const val MAX_QUERY = 40

    /** [today] is the exchange-local date; a date-only event is never shifted by time zones. */
    fun status(event: EarningsEvent, today: String): EarningsEventStatus = when {
        event.sourceStatus == EarningsEventStatus.CANCELED -> EarningsEventStatus.CANCELED
        event.sourceStatus == EarningsEventStatus.POSTPONED -> EarningsEventStatus.POSTPONED
        event.actual?.let { it.eps != null || it.revenue != null } == true || event.sourceStatus == EarningsEventStatus.REPORTED -> EarningsEventStatus.REPORTED
        event.dateStatus == EarningsDateStatus.UNKNOWN -> EarningsEventStatus.UNKNOWN
        event.date >= today -> EarningsEventStatus.SCHEDULED
        else -> EarningsEventStatus.UNKNOWN
    }

    /** "upcoming" = everything not verified as reported; "reported" = verified only; "scheduled" = expected to report. */
    fun inView(status: EarningsEventStatus, view: String?): Boolean = when (view) {
        "upcoming" -> status != EarningsEventStatus.REPORTED
        "scheduled" -> status == EarningsEventStatus.SCHEDULED
        "reported", "results" -> status == EarningsEventStatus.REPORTED
        else -> true
    }

    /**
     * Case-insensitive ticker prefix, or a company name whose words start with the query ("intel",
     * "royal bank"); "td" doesn't match inside "Ltd.". Blank matches everything.
     */
    fun matches(event: EarningsEvent, query: String?): Boolean {
        val q = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return true
        if (event.symbol.lowercase().startsWith(q)) return true
        val name = event.name.lowercase()
        return name.startsWith(q) || Regex("[^a-z0-9]" + Regex.escape(q)).containsMatchIn(name)
    }

    fun isDate(text: String?): Boolean = text != null && Regex("\\d{4}-\\d{2}-\\d{2}").matches(text) && runCatching {
        val month = text.substring(5, 7).toInt(); val day = text.substring(8, 10).toInt()
        month in 1..12 && day in 1..31 && AnalyticsDates.plusDays(text, 0) == text
    }.getOrDefault(false)

    fun weekStart(date: String): String = AnalyticsDates.plusDays(date, -EarningsFormatter.weekday(date))
    fun week(date: String): List<String> = weekStart(date).let { monday -> (0..6).map { AnalyticsDates.plusDays(monday, it) } }

    /** Server range for a selection: the visible week, or a bounded window from it while searching. */
    fun range(selected: String, tab: CalendarTab, searching: Boolean): Pair<String, String> {
        val monday = weekStart(selected)
        val sunday = AnalyticsDates.plusDays(monday, 6)
        return when {
            !searching -> monday to sunday
            tab == CalendarTab.UPCOMING -> monday to AnalyticsDates.plusDays(monday, MAX_SEARCH_DAYS)
            else -> AnalyticsDates.plusDays(sunday, -MAX_SEARCH_DAYS) to sunday
        }
    }

    /** "Before market open", or with a source-stated time: "After market close · 4:05 PM ET". */
    fun timing(event: EarningsEvent): String {
        val base = EarningsFormatter.session(event.session)
        val time = event.eventTime?.let(::clock) ?: return base
        val zone = when (event.timeZone) { "America/New_York", "America/Toronto" -> "ET"; null -> null; else -> event.timeZone }
        return "$base · $time" + (zone?.let { " $it" } ?: "")
    }

    private fun clock(hhmm: String): String? {
        val parts = hhmm.split(':')
        val h = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return "${if (h % 12 == 0) 12 else h % 12}:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
    }

    /** "Oct 12 – 18, 2026" or "Sep 28 – Oct 4, 2026". */
    fun weekLabel(monday: String): String {
        val sunday = AnalyticsDates.plusDays(monday, 6)
        val a = EarningsFormatter.date(monday).substringAfter(", ")
        val b = EarningsFormatter.date(sunday).substringAfter(", ")
        val end = if (a.substringBefore(' ') == b.substringBefore(' ')) b.substringAfter(' ') else b
        return "$a – $end, ${sunday.take(4)}"
    }

    fun statusText(status: EarningsEventStatus, dateStatus: EarningsDateStatus): String = when (status) {
        EarningsEventStatus.SCHEDULED -> if (dateStatus == EarningsDateStatus.CONFIRMED) "Scheduled · Confirmed date" else "Scheduled · ${EarningsFormatter.dateStatus(dateStatus)}"
        else -> status.label
    }

    fun statusExplanation(status: EarningsEventStatus): String = when (status) {
        EarningsEventStatus.SCHEDULED -> "The company is expected to report on this date. Dates that aren't confirmed by the company can change."
        EarningsEventStatus.REPORTED -> "Results have been reported."
        EarningsEventStatus.POSTPONED -> "The data source says this report was postponed. A new date hasn't been provided yet."
        EarningsEventStatus.CANCELED -> "The data source says this scheduled report was canceled."
        EarningsEventStatus.UNKNOWN -> "The expected date has passed, but the data source hasn't confirmed that results were reported."
    }

    fun freshnessText(freshness: DataFreshness, fetchedAt: String?): String? = when (freshness) {
        DataFreshness.FRESH, DataFreshness.CACHED -> null
        DataFreshness.STALE -> "Showing saved earnings data" + (fetchedAt?.let { " from ${EarningsFormatter.date(it.take(10))}, ${it.drop(11).take(5)} UTC" } ?: "") +
            ". The data provider isn't responding, so dates may have changed."
        DataFreshness.UNAVAILABLE -> "Earnings data isn't available right now."
    }

    const val WHAT_ARE_EARNINGS = "Companies usually report their financial results every quarter. These reports help investors understand how the business is performing."
    const val EVENT_EXPLANATION = "An earnings report shows how a company performed during a financial period. Investors often compare the results with expectations, but stock prices can react in unexpected ways."
}

// ---------- Calendar presenter ----------

enum class CalendarTab(val label: String) { UPCOMING("Upcoming"), REPORTED("Reported") }
enum class CalendarFilter(val label: String) { ALL("All Companies"), WATCHLIST("My Watchlist") }
enum class CalendarMode(val label: String) { DAY("Day"), WEEK("Week") }

/** One day in the seven-day strip. [count] is null when no reliable count is available. */
data class WeekDayView(val date: String, val weekday: String, val day: String, val count: Int?, val selected: Boolean, val today: Boolean) {
    val accessibility: String get() = buildString {
        append(EarningsFormatter.spokenDate(date)).append(". ")
        count?.let { append(if (it == 0) "No earnings reports" else "$it earnings report${if (it == 1) "" else "s"}").append(". ") }
        if (today) append("Today. ")
        if (selected) append("Selected.")
    }.trim()
}

/** A compact calendar card: identity, date, timing and status — no figures. */
data class EarningsEventRow(
    val id: String,
    val symbol: String,
    val name: String,
    val exchange: String?,
    val logoUrl: String?,
    val date: String,
    val dateText: String,
    val timingText: String,
    val status: EarningsEventStatus,
    val statusText: String,
    val watchlisted: Boolean,
    val previousDate: String?,
    /** Published report for "View Results"; null until the source has reported figures. */
    val reportId: String? = null
) {
    val accessibility: String get() = buildString {
        append("$name, $symbol").append(exchange?.let { ", $it" } ?: "").append(". ")
        append(EarningsFormatter.spokenDate(date)).append(". ").append(timingText).append(". ").append(statusText).append(".")
        previousDate?.let { append(" Moved from ${EarningsFormatter.spokenDate(it)}.") }
        if (watchlisted) append(" On your watchlist.")
    }
}

fun EarningsCalendarItem.eventRow(watched: Set<String>): EarningsEventRow {
    val e = event
    return EarningsEventRow(e.id, e.symbol, e.name, e.exchange, e.logoUrl, e.date, EarningsFormatter.date(e.date), EarningsCalendarRules.timing(e),
        eventStatus, EarningsCalendarRules.statusText(eventStatus, e.dateStatus),
        FollowReason.WATCHLIST in following || e.symbol.uppercase() in watched, e.previousDate, reportId.takeIf { eventStatus == EarningsEventStatus.REPORTED })
}

/** Restorable selection (route arguments and saved state). */
data class CalendarSelection(
    val date: String,
    val mode: CalendarMode = CalendarMode.WEEK,
    val tab: CalendarTab = CalendarTab.UPCOMING,
    val filter: CalendarFilter = CalendarFilter.ALL,
    val query: String = ""
) {
    fun encode(): String = listOf(date, mode.name, tab.name, filter.name, query).joinToString("|")
    companion object {
        fun decode(text: String?): CalendarSelection? {
            val p = text?.split('|', limit = 5)?.takeIf { it.size == 5 } ?: return null
            if (!EarningsCalendarRules.isDate(p[0])) return null
            return CalendarSelection(p[0], CalendarMode.entries.firstOrNull { it.name == p[1] } ?: CalendarMode.WEEK,
                CalendarTab.entries.firstOrNull { it.name == p[2] } ?: CalendarTab.UPCOMING,
                CalendarFilter.entries.firstOrNull { it.name == p[3] } ?: CalendarFilter.ALL, p[4].take(EarningsCalendarRules.MAX_QUERY))
        }
    }
}

data class EarningsCalendarState(
    val today: String = "",
    val selection: CalendarSelection = CalendarSelection(""),
    val week: List<WeekDayView> = emptyList(),
    val weekLabel: String = "",
    /** "Results for “app” from Oct 12 – Dec 13" while searching. */
    val rangeText: String? = null,
    val rows: List<EarningsEventRow> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val notes: List<String> = emptyList(),
    val freshness: DataFreshness = DataFreshness.FRESH,
    val freshnessText: String? = null,
    /** Some upcoming dates weren't refreshed by the source recently. */
    val sourceStale: Boolean = false,
    val partial: Boolean = false,
    val sampleData: Boolean = false,
    val signedIn: Boolean = false,
    val followedCount: Int? = null,
    /** MOCK-only demo scenario, or null. */
    val scenario: String? = null
) {
    val searching: Boolean get() = selection.query.isNotBlank()
    val days: List<Pair<String, List<EarningsEventRow>>> get() = rows.groupBy { it.date }.toList()
    val needsSignIn: Boolean get() = selection.filter == CalendarFilter.WATCHLIST && !signedIn
    /** Null when there's content (or loading/error); otherwise the empty-state message. */
    val emptyMessage: String? get() = when {
        loading || error != null || rows.isNotEmpty() -> null
        needsSignIn -> "Sign in to see earnings for companies on your watchlists."
        selection.filter == CalendarFilter.WATCHLIST && followedCount == 0 -> "Your watchlists are empty. Add companies to a watchlist to see their earnings dates here."
        searching -> "No earnings events match “${selection.query.trim()}” in this period."
        selection.filter == CalendarFilter.WATCHLIST -> if (selection.tab == CalendarTab.REPORTED) "No companies on your watchlists have reported results in this period."
            else "No companies on your watchlists are expected to report in this period."
        selection.tab == CalendarTab.REPORTED -> "No verified reported results ${if (selection.mode == CalendarMode.DAY) "on this day" else "this week"}."
        else -> "No earnings events ${if (selection.mode == CalendarMode.DAY) "on this day" else "this week"}."
    }
}

/**
 * Earnings Calendar: a selected date (day or week view), Upcoming/Reported, All/My Watchlist and a
 * debounced server-side search. Every request is date-bounded and paged; obsolete loads are
 * cancelled. First pages are cached briefly in memory (per signed-in user for the watchlist scope,
 * cleared on account change or a watchlist change), so moving back and forth between weeks doesn't
 * refetch. Watchlist filtering happens on the server from the signed-in identity, never from a
 * client-supplied list.
 */
@OptIn(FlowPreview::class)
class EarningsCalendarPresenter(
    private val remote: EarningsRemote,
    private val scope: CoroutineScope,
    /** Signed-in user id (null when signed out). */
    private val session: Flow<String?> = flowOf(null),
    /** Canonical symbols on any of the user's watchlists (for the watchlist indicator and refresh). */
    private val watched: Flow<Set<String>> = flowOf(emptySet()),
    private val today: () -> String = { Clock.System.now().toString().take(10) },
    initial: CalendarSelection? = null,
    private val pageSize: Int = 20,
    private val cacheTtlMillis: Long = 300_000,
    private val debounceMillis: Long = 300,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private data class CacheKey(val owner: String?, val query: EarningsCalendarQuery)
    private class Cached(val page: EarningsCalendarPage, val at: Long)

    /** An initial selection without a valid date opens on today (e.g. "My Watchlist" from the Watchlist tab). */
    private val selection = MutableStateFlow(initial?.let { if (EarningsCalendarRules.isDate(it.date)) it else it.copy(date = today()) } ?: CalendarSelection(today()))
    private val mutable = MutableStateFlow(EarningsCalendarState(today(), selection.value))
    val state: StateFlow<EarningsCalendarState> = mutable.asStateFlow()
    private val refreshes = MutableStateFlow(0)
    private val scenario = MutableStateFlow<String?>(null)
    private val cache = LinkedHashMap<CacheKey, Cached>()
    private var items: List<EarningsCalendarItem> = emptyList()
    private var page: EarningsCalendarPage? = null
    private var query: EarningsCalendarQuery? = null
    private var owner: String? = null
    private var watchedNow: Set<String> = emptySet()
    private var moreJob: Job? = null
    private var started = false
    private var counts: Pair<List<Any?>, Map<String, Int>>? = null

    /** Starts loading (idempotent). */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            var first = true
            watched.distinctUntilChanged().collect { symbols ->
                watchedNow = symbols
                mutable.update { it.copy(rows = items.map { item -> item.eventRow(symbols) }) }
                // A watchlist change makes cached watchlist-scope pages wrong; reload when that filter is on.
                if (!first) { dropScoped(); if (selection.value.filter == CalendarFilter.WATCHLIST) refreshes.value++ }
                first = false
            }
        }
        // Text changes are debounced; everything else applies at once.
        val debounced = selection.map { it.query.trim() }.distinctUntilChanged().debounce { if (it.isEmpty()) 0 else debounceMillis }
        scope.launch {
            combine(selection, debounced, session.distinctUntilChanged(), refreshes, scenario) { s, q, uid, r, sc -> Load(s.copy(query = q), uid, r, sc) }
                .distinctUntilChanged()
                .collectLatest { load(it) }
        }
    }

    private data class Load(val selection: CalendarSelection, val uid: String?, val refresh: Int, val scenario: String?)
    private var lastRefresh = 0

    private fun dropScoped() { cache.keys.removeAll { it.owner != null } }

    private suspend fun load(request: Load) {
        moreJob?.cancel()
        val s = request.selection
        if (request.uid != owner) { dropScoped(); owner = request.uid }
        val force = request.refresh != lastRefresh
        lastRefresh = request.refresh
        val searching = s.query.isNotEmpty()
        val (from, to) = EarningsCalendarRules.range(s.date, s.tab, searching)
        val q = EarningsCalendarQuery(from, to, view = if (s.tab == CalendarTab.UPCOMING) "upcoming" else "reported", pageSize = pageSize,
            query = s.query.takeIf { searching }, day = s.date.takeIf { s.mode == CalendarMode.DAY && !searching },
            scope = "watchlist".takeIf { s.filter == CalendarFilter.WATCHLIST }, scenario = request.scenario)
        query = q
        val today = today()
        val monday = EarningsCalendarRules.weekStart(s.date)
        fun week(counts: Map<String, Int>?) = EarningsCalendarRules.week(s.date).map { d ->
            WeekDayView(d, EarningsFormatter.date(d).take(3), d.takeLast(2).trimStart('0'), counts?.let { it[d] ?: 0 }, d == s.date, d == today)
        }
        // Keep the strip's counts while reloading the same week/tab/filter/search; otherwise they're unknown.
        val countsKey = listOf(monday, s.tab, s.filter, s.query, request.uid)
        val keep = counts?.takeIf { it.first == countsKey }?.second
        mutable.update {
            it.copy(today = today, selection = selection.value, week = week(keep), weekLabel = EarningsCalendarRules.weekLabel(monday),
                signedIn = request.uid != null, scenario = request.scenario,
                rangeText = if (searching) "Results for “${s.query}” from ${EarningsFormatter.date(from).substringAfter(", ")} – ${EarningsFormatter.date(to).substringAfter(", ")}" else null,
                loading = true, error = null)
        }
        if (s.filter == CalendarFilter.WATCHLIST && request.uid == null) {
            items = emptyList(); page = null
            mutable.update { it.copy(loading = false, rows = emptyList(), total = 0, hasMore = false, notes = emptyList(), followedCount = null, week = week(null)) }
            return
        }
        val key = CacheKey(request.uid.takeIf { s.filter == CalendarFilter.WATCHLIST }, q)
        val cached = cache[key]?.takeIf { !force && now() - it.at < cacheTtlMillis && request.scenario == null }
        try {
            val result = cached?.page ?: fetch(q).also { fresh ->
                cache[key] = Cached(fresh, now())
                if (cache.size > 24) cache.remove(cache.keys.first())
            }
            page = result
            items = result.items
            counts = countsKey to result.dayCounts
            mutable.update { it.copy(loading = false, rows = items.map { item -> item.eventRow(watchedNow) }, total = result.total, hasMore = result.nextCursor != null,
                notes = result.notes, freshness = result.freshness, freshnessText = EarningsCalendarRules.freshnessText(result.freshness, result.fetchedAt),
                sourceStale = result.stale, partial = result.partial, sampleData = result.sampleData, followedCount = result.followedCount,
                week = week(result.dayCounts)) }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            items = emptyList(); page = null
            mutable.update { it.copy(loading = false, rows = emptyList(), total = 0, hasMore = false, week = week(null), freshness = DataFreshness.UNAVAILABLE, freshnessText = null,
                error = (cause as? StockStepsApiException)?.error?.message ?: "Earnings couldn't be loaded. Check your connection and try again.") }
        }
    }

    private suspend fun fetch(q: EarningsCalendarQuery) = if (q.scope != null) remote.following(q) else remote.calendar(q)

    private fun select(transform: (CalendarSelection) -> CalendarSelection) { selection.update(transform) }
    fun selectDate(date: String) { if (EarningsCalendarRules.isDate(date)) select { it.copy(date = date) } }
    fun previousWeek() = select { it.copy(date = AnalyticsDates.plusDays(it.date, -7)) }
    fun nextWeek() = select { it.copy(date = AnalyticsDates.plusDays(it.date, 7)) }
    fun goToToday() = select { it.copy(date = today()) }
    fun selectMode(mode: CalendarMode) = select { it.copy(mode = mode) }
    fun selectTab(tab: CalendarTab) = select { it.copy(tab = tab) }
    fun selectFilter(filter: CalendarFilter) = select { it.copy(filter = filter) }
    fun setQuery(text: String) {
        val value = text.take(EarningsCalendarRules.MAX_QUERY)
        select { it.copy(query = value) }
        mutable.update { it.copy(selection = selection.value) }
    }
    fun clearQuery() = setQuery("")
    fun refresh() { refreshes.value++ }
    /** MOCK-only demo scenarios; the server ignores them in REAL. */
    fun setScenario(name: String?) { scenario.value = name }
    val currentSelection: CalendarSelection get() = selection.value

    fun loadMore() {
        val q = query ?: return
        val cursor = page?.nextCursor ?: return
        if (moreJob?.isActive == true || mutable.value.loading) return
        moreJob = scope.launch {
            mutable.update { it.copy(loadingMore = true) }
            try {
                val next = fetch(q.copy(cursor = cursor))
                page = next
                items = (items + next.items).distinctBy { it.event.id }
                mutable.update { it.copy(loadingMore = false, rows = items.map { item -> item.eventRow(watchedNow) }, hasMore = next.nextCursor != null) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { it.copy(loadingMore = false) }
            }
        }
    }
}

// ---------- Event details ----------

data class EarningsEventState(
    val eventId: String,
    val loading: Boolean = true,
    val error: String? = null,
    val symbol: String = "",
    val name: String = "",
    val exchange: String? = null,
    val logoUrl: String? = null,
    val period: String? = null,
    val dateText: String? = null,
    val spokenDate: String? = null,
    val timingText: String? = null,
    val status: EarningsEventStatus? = null,
    val statusText: String? = null,
    val statusExplanation: String? = null,
    val previousDate: String? = null,
    val updatedText: String? = null,
    val sourceText: String? = null,
    val freshnessText: String? = null,
    val sampleData: Boolean = false,
    val notes: List<String> = emptyList(),
    /** Exchange-local date, for opening the calendar on it. */
    val date: String? = null,
    /** Published report for "View Results" (verified reported events only). */
    val reportId: String? = null
) {
    val reported: Boolean get() = status == EarningsEventStatus.REPORTED
}

/** Earnings Event Details: identity, date, timing, status and provenance — no figures (Phase 2 lives on Earnings Details). */
class EarningsEventPresenter(val eventId: String, private val remote: EarningsRemote, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(EarningsEventState(eventId))
    val state: StateFlow<EarningsEventState> = mutable.asStateFlow()
    private val refreshes = MutableStateFlow(0)

    init { scope.launch { refreshes.collectLatest { load() } } }

    private suspend fun load() {
        mutable.update { it.copy(loading = true, error = null) }
        try {
            val info = remote.event(eventId)
            val e = info.item.event
            mutable.value = EarningsEventState(eventId, false, null, e.symbol, e.name, e.exchange, e.logoUrl, e.period, EarningsFormatter.date(e.date),
                EarningsFormatter.spokenDate(e.date), EarningsCalendarRules.timing(e), info.item.eventStatus,
                EarningsCalendarRules.statusText(info.item.eventStatus, e.dateStatus), EarningsCalendarRules.statusExplanation(info.item.eventStatus), e.previousDate,
                e.sourceUpdatedAt?.let { "Source updated ${EarningsFormatter.date(it.take(10))}" } ?: "The source didn't say when this was last updated.",
                "Source: ${e.source}", EarningsCalendarRules.freshnessText(info.freshness, info.fetchedAt), info.sampleData, info.notes, e.date,
                info.item.reportId.takeIf { info.item.eventStatus == EarningsEventStatus.REPORTED })
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            mutable.update { it.copy(loading = false, error = (cause as? StockStepsApiException)?.let { e -> if (e.status == 404) "This earnings event isn't available. It may have been removed by the data source." else e.error.message }
                ?: "Earnings couldn't be loaded. Check your connection and try again.") }
        }
    }

    fun refresh() { refreshes.value++ }
}

// ---------- Company Details: next earnings ----------

data class CompanyEarningsState(
    val loading: Boolean = true,
    val error: String? = null,
    /** Exchange-local date of the next event, for opening the calendar on it. */
    val date: String? = null,
    val eventId: String? = null,
    val dateText: String? = null,
    val spokenDate: String? = null,
    val timingText: String? = null,
    val statusText: String? = null,
    val sampleData: Boolean = false,
    /** Latest published results (Phase 2 preview); null when none or it couldn't be loaded. */
    val latestReportId: String? = null,
    /** "Latest results: Q3 FY2026". */
    val latestTitle: String? = null,
    /** "EPS Beat · Revenue Miss" (only comparable measures). */
    val latestSummary: String? = null
) {
    val message: String? get() = if (!loading && error == null && date == null) "Next earnings date not available." else null
}

/** The next earnings date on Company Details (one cached request per company). */
class CompanyEarningsPresenter(val symbol: String, private val remote: EarningsRemote, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(CompanyEarningsState())
    val state: StateFlow<CompanyEarningsState> = mutable.asStateFlow()
    private val refreshes = MutableStateFlow(0)

    init { scope.launch { refreshes.collectLatest { load() } } }

    private suspend fun load() = coroutineScope {
        mutable.update { it.copy(loading = true, error = null) }
        // The latest results preview is optional: a missing report (404) or failure just hides it.
        val latest = async { runCatching { remote.latestResults(symbol) }.getOrNull() }
        try {
            val next = remote.next(symbol)
            val e = next.event
            val base = if (e == null) CompanyEarningsState(loading = false, sampleData = next.sampleData) else CompanyEarningsState(false, null, e.date, e.id,
                EarningsFormatter.date(e.date) + ", " + e.date.take(4), EarningsFormatter.spokenDate(e.date), EarningsCalendarRules.timing(e),
                EarningsCalendarRules.statusText(next.eventStatus ?: EarningsEventStatus.SCHEDULED, e.dateStatus), next.sampleData)
            val r = latest.await()
            mutable.value = if (r == null) base else base.copy(latestReportId = r.report.reportId, latestTitle = "Latest results: ${r.report.period}",
                latestSummary = listOfNotNull(
                    r.insights.eps.classification.takeIf { it != Classification.UNAVAILABLE }?.let { "EPS ${it.label}" },
                    r.insights.revenue.classification.takeIf { it != Classification.UNAVAILABLE }?.let { "Revenue ${it.label}" }
                ).joinToString(" · ").ifEmpty { "Estimates not comparable" })
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            mutable.update { it.copy(loading = false, error = "Earnings dates couldn't be loaded.") }
        }
    }

    fun refresh() { refreshes.value++ }
}

// ---------- Markets: Earnings Center entry ----------

data class EarningsSummaryState(
    val loading: Boolean = true,
    /** Upcoming events in the next 7 days; null when unknown (never estimated). */
    val upcomingCount: Int? = null,
    val nextDateText: String? = null,
    val watchlistCount: Int? = null
) {
    val headline: String? get() = when {
        loading -> null
        upcomingCount == null -> null
        upcomingCount == 0 -> "No earnings dates in the next 7 days."
        else -> "$upcomingCount compan${if (upcomingCount == 1) "y reports" else "ies report"} in the next 7 days" + (nextDateText?.let { " · next on $it" } ?: "")
    }
    val watchlistText: String? get() = watchlistCount?.takeIf { it > 0 }?.let { "$it on your watchlist${if (it == 1) "" else "s"}" }
}

/** Counts for the Markets entry from two bounded, single-row calendar requests (no fabricated numbers). */
class EarningsSummaryPresenter(
    private val remote: EarningsRemote,
    private val scope: CoroutineScope,
    private val session: Flow<String?> = flowOf(null),
    private val today: () -> String = { Clock.System.now().toString().take(10) }
) {
    private val mutable = MutableStateFlow(EarningsSummaryState())
    val state: StateFlow<EarningsSummaryState> = mutable.asStateFlow()
    private val refreshes = MutableStateFlow(0)

    init {
        scope.launch {
            combine(session.distinctUntilChanged(), refreshes) { uid, _ -> uid }.collectLatest { uid ->
                val t = today()
                // "Scheduled" only: postponed, canceled or unconfirmed events aren't counted as reporting.
                val q = EarningsCalendarQuery(t, AnalyticsDates.plusDays(t, 6), view = "scheduled", pageSize = 1)
                val all = runCatching { remote.calendar(q) }.getOrNull()
                val mine = if (uid != null) runCatching { remote.following(q.copy(scope = "watchlist")) }.getOrNull() else null
                mutable.value = EarningsSummaryState(false, all?.total, all?.items?.firstOrNull()?.event?.date?.let(EarningsFormatter::date), mine?.total)
            }
        }
    }

    fun refresh() { refreshes.value++ }
}

// ---------- Account inputs (the existing watchlists repository; no second copy) ----------

/** Signed-in user id, from the existing watchlists repository (null when signed out). */
fun org.example.stocksteps.data.userdata.UserWatchlistsRepository.earningsSession(): Flow<String?> = state.map { it.uid }

/** Canonical (exchange-qualified) symbols across all of the user's watchlists, deduplicated. */
fun org.example.stocksteps.data.userdata.UserWatchlistsRepository.watchedSymbols(): Flow<Set<String>> =
    state.map { s -> s.value?.watchlists.orEmpty().flatMap { list -> list.entries.map { it.instrument.symbol.trim().uppercase() } }.toSet() }
