package org.example.stocksteps.earnings

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.example.stocksteps.model.*
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.PortfolioEngine
import org.example.stocksteps.screener.RequestRateLimiter
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.PriceChartService
import org.example.stocksteps.service.StockService
import org.example.stocksteps.userdata.*
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

class EarningsRequestException(val status: Int, val code: String, message: String) : Exception(message)

/** Grounded answers to a StockSteps+ user's earnings question (no provider is called automatically). */
fun interface EarningsResearchProvider {
    suspend fun answer(question: String, details: EarningsDetails): EarningsAnswer
}

/**
 * MOCK: a deterministic answer built only from the verified metrics, saying plainly when the data
 * can't explain a cause. Never calls an AI service.
 */
object TemplateEarningsResearch : EarningsResearchProvider {
    override suspend fun answer(question: String, details: EarningsDetails): EarningsAnswer {
        val facts = listOfNotNull(
            details.summaryExplanation,
            details.eps.percent?.let { "EPS surprise: ${"%.1f".format(it)}% versus the consensus estimate." },
            details.revenue.percent?.let { "Revenue surprise: ${"%.1f".format(it)}% versus the consensus estimate." },
            details.revenueGrowthYoY?.let { "Revenue changed ${"%.1f".format(it)}% year over year." },
            details.reaction?.let(EarningsReactionCalculator::sentence)
        )
        val why = Regex("(?i)\\bwhy\\b").containsMatchIn(question)
        return EarningsAnswer(
            "Sample answer (no AI service in mock mode). Based on the verified figures: " + facts.joinToString(" ").ifBlank { "no reported figures are available yet." } +
                if (why) " The available data doesn't include management commentary or sourced news that explains the cause, so StockSteps can't say why." else "",
            sources = listOf("StockSteps sample earnings fixtures", "StockSteps sample price history"),
            insufficientEvidence = why
        )
    }
}

/**
 * Earnings Intelligence: calendar, following, details (history, surprises, price reaction,
 * insights) and the next/latest events used by alerts, Home and Watchlist. One data source per
 * environment; caches by window and symbol; StockSteps+ shaping and AI access are enforced here.
 */
class EarningsService(
    private val source: EarningsDataSource,
    private val stocks: StockService,
    private val charts: PriceChartService,
    private val store: UserDataStore,
    private val entitlements: EntitlementService,
    private val clock: Clock,
    private val sampleData: Boolean,
    private val research: EarningsResearchProvider?,
    private val aiDailyLimit: Int = 20,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 512),
    /** Phase 5: the shared earnings AI quota (questions here and on Earnings Results count together). */
    var aiQuota: EarningsAiQuotaLedger? = null
) {
    private val log = LoggerFactory.getLogger("StockSteps.Earnings")
    private val permits = Semaphore(4)
    private val aiUsage = ConcurrentHashMap<String, Int>()
    private val zone = ZoneId.of("America/New_York")

    companion object {
        const val FREE_HISTORY = 4
        const val MAX_RANGE_DAYS = 62L
        const val MAX_PAGE = 50
        /** An upcoming date not refreshed for this long is labelled stale. */
        const val STALE_DAYS = 14L
        private val SESSION_ORDER = listOf(EarningsTime.BEFORE_OPEN, EarningsTime.DURING_MARKET, EarningsTime.AFTER_CLOSE, EarningsTime.UNKNOWN)
    }

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    /** Events for one date window plus when they were fetched from the source. */
    private class Window(val events: List<EarningsEvent>, val fetchedAt: java.time.Instant)
    private class Loaded(val events: List<EarningsEvent>, val freshness: DataFreshness, val fetchedAt: java.time.Instant)
    /** The last successful copy of each window, served (labelled STALE) only when the source fails. */
    private val lastGood = ConcurrentHashMap<String, Window>()

    private suspend fun window(from: LocalDate, to: LocalDate, scenario: String? = null): Loaded {
        if (sampleData) when (scenario) {
            "provider-unavailable" -> throw EarningsRequestException(503, "EARNINGS_UNAVAILABLE", "Earnings data isn't available right now. Try again shortly. (Sample scenario)")
            "stale-cache" -> return Loaded(source.calendar(from, to), DataFreshness.STALE, clock.instant().minusSeconds(3 * 3600))
        }
        // Recent/upcoming windows refresh hourly; windows entirely in the past are stable for 12 h.
        val ttl = if (to < today().minusDays(7)) 43_200_000L else 3_600_000L
        val key = "calendar:$from:$to"
        return try {
            val w = cache.getOrLoad(key, ttl) { Window(source.calendar(from, to), clock.instant()) }
            lastGood[key] = w
            if (lastGood.size > 256) lastGood.keys.take(64).forEach(lastGood::remove)
            Loaded(w.events, if (w.fetchedAt.isAfter(clock.instant().minusSeconds(5))) DataFreshness.FRESH else DataFreshness.CACHED, w.fetchedAt)
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            log.warn("Earnings calendar unavailable: {}", cause::class.simpleName)
            // Never replaced with sample data: an old real copy (labelled) or an honest error.
            lastGood[key]?.let { return Loaded(it.events, DataFreshness.STALE, it.fetchedAt) }
            throw EarningsRequestException(503, "EARNINGS_UNAVAILABLE", "Earnings data isn't available right now. Try again shortly.")
        }
    }

    private suspend fun events(symbol: String): List<EarningsEvent> = try {
        cache.getOrLoad("history:${symbol.uppercase()}", 21_600_000L) { source.history(symbol) }
    } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        throw EarningsRequestException(503, "EARNINGS_UNAVAILABLE", "Earnings data isn't available right now. Try again shortly.")
    }

    /** Provider names/logos for display (from the shared, cached profiles), bounded concurrency. */
    private suspend fun enrich(events: List<EarningsEvent>): List<EarningsEvent> = coroutineScope {
        events.map { e -> async {
            if (e.name != e.symbol && e.exchange != null) e else permits.withPermit {
                val profile = runCatching { stocks.getProfile(e.symbol) }.getOrNull()
                e.copy(name = profile?.companyName ?: e.name, exchange = e.exchange ?: profile?.exchange, country = e.country ?: profile?.country,
                    logoUrl = e.logoUrl ?: profile?.logoUrl)
            }
        } }.awaitAll()
    }

    /** Today in the event's own exchange zone, so a date-only event is never shifted by a conversion. */
    private fun todayFor(event: EarningsEvent): String =
        clock.instant().atZone(runCatching { ZoneId.of(event.timeZone ?: exchangeZone(event.exchange)) }.getOrDefault(zone)).toLocalDate().toString()

    private fun exchangeZone(exchange: String?) = if (exchange.equals("TSX", ignoreCase = true) || exchange.equals("TSXV", ignoreCase = true)) "America/Toronto" else "America/New_York"

    private fun item(event: EarningsEvent, following: List<FollowReason> = emptyList(), shares: Double? = null): EarningsCalendarItem {
        val status = EarningsCalculator.status(event, today().toString())
        val reported = status == EarningsStatus.REPORTED || status == EarningsStatus.PARTIALLY_REPORTED
        return EarningsCalendarItem(event, status,
            if (reported) EarningsCalculator.eps(event.estimate, event.actual) else null,
            if (reported) EarningsCalculator.revenue(event.estimate, event.actual) else null, following, shares,
            EarningsCalendarRules.status(event, todayFor(event)), event.id.takeIf { reported })
    }

    private fun sorted(items: List<EarningsCalendarItem>) = items.sortedWith(
        compareBy<EarningsCalendarItem> { it.event.date }.thenBy { SESSION_ORDER.indexOf(it.event.session) }.thenBy { it.event.name.lowercase() }.thenBy { it.event.symbol })

    private fun validate(query: EarningsCalendarQuery): Pair<LocalDate, LocalDate> {
        val from = runCatching { LocalDate.parse(query.from) }.getOrNull() ?: throw EarningsRequestException(400, "INVALID_RANGE", "Use dates like 2026-10-08.")
        val to = runCatching { LocalDate.parse(query.to) }.getOrNull() ?: throw EarningsRequestException(400, "INVALID_RANGE", "Use dates like 2026-10-08.")
        if (to < from || to.toEpochDay() - from.toEpochDay() > MAX_RANGE_DAYS) throw EarningsRequestException(400, "INVALID_RANGE", "Choose a range of up to $MAX_RANGE_DAYS days.")
        if (query.pageSize !in 1..MAX_PAGE) throw EarningsRequestException(400, "INVALID_PAGE", "Page size must be 1–$MAX_PAGE.")
        if ((query.query?.length ?: 0) > EarningsCalendarRules.MAX_QUERY) throw EarningsRequestException(400, "INVALID_QUERY", "Search for up to ${EarningsCalendarRules.MAX_QUERY} characters.")
        query.day?.let { d ->
            val day = runCatching { LocalDate.parse(d) }.getOrNull() ?: throw EarningsRequestException(400, "INVALID_RANGE", "Use dates like 2026-10-08.")
            if (day < from || day > to) throw EarningsRequestException(400, "INVALID_RANGE", "The day must be inside the date range.")
        }
        if (query.scope != null && query.scope != "watchlist") throw EarningsRequestException(400, "INVALID_SCOPE", "Unknown scope.")
        if (query.view != null && query.view !in setOf("upcoming", "reported", "results", "scheduled")) throw EarningsRequestException(400, "INVALID_VIEW", "Use view=upcoming, scheduled or reported.")
        return from to to
    }

    /**
     * Filters → per-day counts over the whole range → the day filter → sorting → one page. Only the
     * page's events are enriched with profiles (names/logos), so a busy week isn't N profile calls.
     */
    private suspend fun respond(all: List<EarningsCalendarItem>, query: EarningsCalendarQuery, notes: List<String>, loaded: Loaded, partial: Boolean = false, followed: Int? = null): EarningsCalendarPage {
        val counts = all.groupingBy { it.event.date }.eachCount().toSortedMap()
        val shown = sorted(query.day?.let { d -> all.filter { it.event.date == d } } ?: all)
        val result = page(shown, query, notes)
        val enriched = enrich(result.items.map { it.event }).associateBy { it.id }
        return result.copy(items = result.items.map { it.copy(event = enriched[it.event.id] ?: it.event) }, dayCounts = counts, freshness = loaded.freshness,
            fetchedAt = loaded.fetchedAt.toString(), partial = partial, followedCount = followed,
            notes = result.notes + listOfNotNull(if (partial) "Company-name search was limited; ticker search still works." else null))
    }

    /** Symbols whose company name matches (REAL providers don't name calendar events); cached, never fatal. */
    private suspend fun nameMatches(query: String?): Pair<Set<String>, Boolean> {
        val q = query?.trim()?.takeIf { it.length >= 2 } ?: return emptySet<String>() to false
        return try {
            cache.getOrLoad("names:${q.lowercase()}", 3_600_000L) { stocks.searchStocks(q).take(20).map { it.symbol.uppercase() }.toSet() } to false
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            emptySet<String>() to true
        }
    }

    private fun page(items: List<EarningsCalendarItem>, query: EarningsCalendarQuery, notes: List<String>): EarningsCalendarPage {
        val fingerprint = query.copy(cursor = null).hashCode().toUInt().toString(36)
        val offset = query.cursor?.let { c ->
            val parts = c.split('.')
            if (parts.size != 2 || parts[1] != fingerprint) throw EarningsRequestException(400, "INVALID_CURSOR", "The calendar changed; reload it.")
            parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: throw EarningsRequestException(400, "INVALID_CURSOR", "Invalid cursor.")
        } ?: 0
        val slice = items.drop(offset).take(query.pageSize)
        val next = (offset + slice.size).takeIf { it < items.size && slice.isNotEmpty() }?.let { "$it.$fingerprint" }
        val stale = slice.any { it.status == EarningsStatus.UPCOMING && runCatching { java.time.Instant.parse(it.event.updatedAt) }.getOrNull()
            ?.let { u -> u.isBefore(clock.instant().minusSeconds(STALE_DAYS * 86_400)) } == true }
        return EarningsCalendarPage(slice, query.from, query.to, items.size, next,
            notes + listOfNotNull(if (stale) "Some dates haven't been updated by the data provider recently and may have changed." else null,
                if (sampleData) "Sample earnings data for development, not real announcements." else null),
            clock.instant().toString(), stale, sampleData)
    }

    private fun matches(item: EarningsCalendarItem, query: EarningsCalendarQuery, named: Set<String> = emptySet()): Boolean {
        val e = item.event
        if (query.exchanges.isNotEmpty() && query.exchanges.none { it.equals(e.exchange, ignoreCase = true) }) return false
        if (query.countries.isNotEmpty() && query.countries.none { it.equals(e.country, ignoreCase = true) }) return false
        if (query.sessions.isNotEmpty() && e.session !in query.sessions) return false
        if (query.symbol != null && !query.symbol.equals(e.symbol, ignoreCase = true)) return false
        if (!query.query.isNullOrBlank() && !EarningsCalendarRules.matches(e, query.query) && e.symbol.uppercase() !in named) return false
        // Reported means verified (figures or an explicit source flag), never "the date has passed".
        return EarningsCalendarRules.inView(item.eventStatus, query.view)
    }

    private val baseNotes = listOf("Dates are exchange-local. Only company-confirmed dates are labelled Confirmed; others can change.")

    suspend fun calendar(query: EarningsCalendarQuery): EarningsCalendarPage {
        val (from, to) = validate(query)
        // Watchlist filtering needs the signed-in identity (/api/v1/me/earnings/following); it is never public.
        if (query.scope != null) throw EarningsRequestException(400, "INVALID_SCOPE", "Sign in to filter by your watchlist.")
        val loaded = window(from, to, query.scenario)
        val (named, partial) = nameMatches(query.query)
        val items = loaded.events.map { item(it) }.filter { matches(it, query, named) }
        return respond(items, query, baseNotes, loaded, partial)
    }

    /** Companies the user follows: positive portfolio holdings and watchlist entries (deduplicated by symbol). */
    suspend fun following(uid: String, query: EarningsCalendarQuery): EarningsCalendarPage {
        val (from, to) = validate(query)
        if (query.scope == "watchlist") return watchlist(uid, query, from, to)
        val ledger = store.updatePortfolio(uid) { it to it }
        val shares = HashMap<String, Decimal>()
        for (account in ledger.accounts) {
            PortfolioEngine.replay(ledger, account.id).holdings.forEach { h ->
                val q = Decimal.parse(h.quantity)
                if (q > Decimal.ZERO) shares[h.instrument.symbol.uppercase()] = (shares[h.instrument.symbol.uppercase()] ?: Decimal.ZERO) + q
            }
        }
        val watched = store.updateWatchlists(uid) { it to it }.watchlists.flatMap { list -> list.entries.map { it.instrument.symbol.uppercase() } }.toSet()
        val symbols = shares.keys + watched
        val loaded = window(from, to, query.scenario)
        val (named, partial) = nameMatches(query.query)
        val items = loaded.events.filter { it.symbol.uppercase() in symbols }.map { e ->
            val s = e.symbol.uppercase()
            item(e, listOfNotNull(FollowReason.PORTFOLIO.takeIf { s in shares }, FollowReason.WATCHLIST.takeIf { s in watched }),
                shares[s]?.toString()?.toDouble())
        }.filter { matches(it, query, named) }
        return respond(items, query, baseNotes + "Includes companies you hold in a portfolio and companies on your watchlists. Watching a company doesn't mean you own it.", loaded, partial)
    }

    /**
     * Earnings for the signed-in user's own watchlists (identity from the verified token, never a
     * client-supplied id or list). Instruments are deduplicated across lists by their canonical,
     * exchange-qualified symbol ("SHOP" and "SHOP.TO" are different listings).
     */
    private suspend fun watchlist(uid: String, query: EarningsCalendarQuery, from: LocalDate, to: LocalDate): EarningsCalendarPage {
        val watched = store.updateWatchlists(uid) { it to it }.watchlists.flatMap { list -> list.entries.map { it.instrument.symbol.trim().uppercase() } }.toSet()
        val notes = baseNotes + "Companies from all of your watchlists, each shown once."
        if (watched.isEmpty()) return EarningsCalendarPage(emptyList(), query.from, query.to, 0, null, notes, clock.instant().toString(), sampleData = sampleData, followedCount = 0)
        val loaded = window(from, to, query.scenario)
        val (named, partial) = nameMatches(query.query)
        val items = loaded.events.filter { it.symbol.uppercase() in watched }.map { item(it, listOf(FollowReason.WATCHLIST)) }.filter { matches(it, query, named) }
        return respond(items, query, notes, loaded, partial, watched.size)
    }

    /** One event by its stable id ("SYMBOL:YYYY-Qn"); 404 when the source doesn't have it. */
    suspend fun event(id: String): EarningsEventInfo {
        val symbol = id.substringBefore(':', "")
        if (!Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}:\\d{4}-Q[1-4]").matches(id)) throw EarningsRequestException(400, "INVALID_EVENT", "Invalid earnings event id.")
        val loaded = history(symbol)
        val event = loaded.events.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: throw EarningsRequestException(404, "NOT_FOUND", "This earnings event isn't available.")
        return EarningsEventInfo(item(enrich(listOf(event)).single()), clock.instant().toString(), loaded.freshness, loaded.fetchedAt.toString(),
            listOfNotNull("Dates are in the exchange's local time. Only company-confirmed dates are labelled Confirmed.",
                if (sampleData) "Sample earnings data for development, not real announcements." else null), sampleData)
    }

    /** The next not-yet-reported, not-canceled announcement for Company Details (null when none is known). */
    suspend fun nextEvent(symbol: String): NextEarnings {
        if (!Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol)) throw EarningsRequestException(400, "INVALID_SYMBOL", "Invalid symbol.")
        val loaded = history(symbol)
        val next = loaded.events.filter { e ->
            val status = EarningsCalendarRules.status(e, todayFor(e))
            e.date >= todayFor(e) && status != EarningsEventStatus.REPORTED && status != EarningsEventStatus.CANCELED && e.dateStatus != EarningsDateStatus.UNKNOWN
        }.minByOrNull { it.date }?.let { enrich(listOf(it)).single() }
        return NextEarnings(symbol.uppercase(), next, next?.let { EarningsCalendarRules.status(it, todayFor(it)) }, clock.instant().toString(), loaded.freshness, sampleData)
    }

    // ---------- Phase 2: Earnings Results (free for every reported period) ----------
    // Reports and insights are calculated here; the apps only format them. Revisions appear when the
    // cached history refreshes (6 h); a report keeps the source's publication time and our fetch time.

    private suspend fun resultsHistory(symbol: String, scenario: String?): Loaded {
        if (sampleData) when (scenario) {
            "provider-timeout" -> throw EarningsRequestException(503, "EARNINGS_UNAVAILABLE", "The earnings data provider didn't respond in time. Try again shortly. (Sample scenario)")
            "stale-cache" -> return Loaded(source.history(symbol), DataFreshness.STALE, clock.instant().minusSeconds(3 * 3600))
        }
        return history(symbol)
    }

    private suspend fun response(event: EarningsEvent, loaded: Loaded): EarningsResultsResponse {
        val report = EarningsReportMapper.report(enrich(listOf(event)).single(), loaded.events, loaded.fetchedAt.toString())
            ?: throw EarningsRequestException(404, "NOT_REPORTED", "Results for this period haven't been published yet.")
        return EarningsResultsResponse(report, EarningsResultsCalculator.insights(report), clock.instant().toString(), loaded.freshness, loaded.fetchedAt.toString(),
            listOfNotNull("Estimates are analysts' consensus. EPS and revenue are judged separately, using the exact reported numbers.",
                if (sampleData) "Sample earnings data for development, not real results." else null), sampleData)
    }

    /** One fiscal period's report and insights ("SYMBOL:YYYY-Qn"). 404 when there's no such period or it isn't reported yet. */
    suspend fun resultsFor(reportId: String, scenario: String? = null): EarningsResultsResponse {
        val (symbol, year, quarter) = EarningsReportMapper.parse(reportId) ?: throw EarningsRequestException(400, "INVALID_REPORT", "Invalid earnings report id.")
        val loaded = resultsHistory(symbol, scenario)
        val event = loaded.events.firstOrNull { it.fiscalYear == year && it.fiscalQuarter == quarter && it.symbol.equals(symbol, ignoreCase = true) }
            ?: throw EarningsRequestException(404, "NOT_FOUND", "There's no earnings report for this period.")
        return response(event, loaded)
    }

    /** The most recent reported fiscal period. 404 NO_REPORT when the company has none. */
    suspend fun latestResults(symbol: String, scenario: String? = null): EarningsResultsResponse {
        if (!Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol)) throw EarningsRequestException(400, "INVALID_SYMBOL", "Invalid symbol.")
        val loaded = resultsHistory(symbol, scenario)
        val latest = loaded.events.filter { it.actual?.let { a -> a.eps != null || a.revenue != null } == true }
            .maxWithOrNull(compareBy<EarningsEvent> { it.fiscalYear }.thenBy { it.fiscalQuarter })
            ?: throw EarningsRequestException(404, "NO_REPORT", "No published earnings results are available for this company.")
        return response(latest, loaded)
    }

    /** Every reported period, newest first, with the two independent classifications. */
    suspend fun reports(symbol: String): EarningsReportsPage {
        if (!Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol)) throw EarningsRequestException(400, "INVALID_SYMBOL", "Invalid symbol.")
        val loaded = history(symbol)
        val rows = loaded.events.mapNotNull { e -> EarningsReportMapper.report(e, loaded.events, null)?.let { r ->
            val i = EarningsResultsCalculator.insights(r)
            EarningsReportSummary(r.reportId, r.fiscalYear, r.fiscalQuarter, r.reportDate, i.eps.classification, i.revenue.classification)
        } }.sortedWith(compareByDescending<EarningsReportSummary> { it.fiscalYear }.thenByDescending { it.fiscalQuarter })
        return EarningsReportsPage(symbol.uppercase(), rows, clock.instant().toString(), loaded.freshness, sampleData)
    }

    /** Up to [attempts] tries for transient provider failures (no retry on cancellation). */
    private suspend fun <T> retrying(attempts: Int = 2, block: suspend () -> T): T {
        var last: Exception? = null
        repeat(attempts) { i ->
            try { return block() } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                last = cause
                if (i < attempts - 1) kotlinx.coroutines.delay(250L * (i + 1))
            }
        }
        throw last!!
    }

    /** A symbol's events with freshness; an old copy (STALE) if the source fails. */
    private suspend fun history(symbol: String): Loaded {
        val key = "history:${symbol.uppercase()}"
        return try {
            val w = cache.getOrLoad("$key:w", 21_600_000L) { Window(retrying { source.history(symbol) }, clock.instant()) }
            lastGood[key] = w
            Loaded(w.events, if (w.fetchedAt.isAfter(clock.instant().minusSeconds(5))) DataFreshness.FRESH else DataFreshness.CACHED, w.fetchedAt)
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            lastGood[key]?.let { return Loaded(it.events, DataFreshness.STALE, it.fetchedAt) }
            throw EarningsRequestException(503, "EARNINGS_UNAVAILABLE", "Earnings data isn't available right now. Try again shortly.")
        }
    }

    /** Next upcoming announcement for alerts, Home and Watchlist (one source with the calendar). */
    suspend fun next(symbol: String): UpcomingEarnings? {
        val today = today().toString()
        // A postponed or canceled report has no date to remind about.
        val event = events(symbol).filter { it.actual == null && it.date >= today && it.dateStatus != EarningsDateStatus.UNKNOWN &&
            it.sourceStatus != EarningsEventStatus.POSTPONED && it.sourceStatus != EarningsEventStatus.CANCELED }.minByOrNull { it.date } ?: return null
        return UpcomingEarnings(event.symbol, event.date, event.session, event.dateStatus, event.source, event.id)
    }

    /** The latest reported event if it was reported in the last 3 days (results reminders). */
    suspend fun recentResult(symbol: String): EarningsEvent? {
        val today = today()
        return events(symbol).filter { it.actual != null }.maxByOrNull { it.date }
            ?.takeIf { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> d >= today.minusDays(3) && d <= today } == true }
    }

    suspend fun details(symbol: String, uid: String?): EarningsDetails {
        if (!Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol)) throw EarningsRequestException(400, "INVALID_SYMBOL", "Invalid symbol.")
        val plus = uid?.let { entitlements.get(it).plus } ?: false
        val today = today().toString()
        val all = enrich(events(symbol)).sortedWith(compareByDescending<EarningsEvent> { it.fiscalYear }.thenByDescending { it.fiscalQuarter })
        val reported = all.filter { it.actual != null }
        val latest = reported.firstOrNull()
        val next = all.filter { it.actual == null && it.date >= today }.minByOrNull { it.date }
        val pending = all.filter { it.actual == null && it.date < today }.maxByOrNull { it.date }
        val focus = when {
            pending != null && (latest == null || pending.date > latest.date) -> pending
            else -> latest ?: next
        }
        val name = focus?.name ?: runCatching { stocks.getProfile(symbol)?.companyName }.getOrNull() ?: symbol
        val eps = EarningsCalculator.eps(focus?.estimate, focus?.actual)
        val revenue = EarningsCalculator.revenue(focus?.estimate, focus?.actual)
        val history = reported.map { e ->
            EarningsHistoryRow(e, EarningsCalculator.eps(e.estimate, e.actual), EarningsCalculator.revenue(e.estimate, e.actual),
                EarningsCalculator.growth(e, EarningsCalculator.yearAgo(e, reported)))
        }
        val shown = if (plus) history else history.take(FREE_HISTORY)
        val insights = if (focus?.actual != null) EarningsInsightEngine.generate(focus, reported.filter { it.id != focus.id }, clock.instant().toString()) else emptyList()
        val reaction = focus?.takeIf { it.actual != null }?.let { reaction(it) }
        val summary = EarningsCalculator.summary(eps, revenue)
        val notes = buildList {
            add("Estimates are analysts' consensus from ${focus?.estimate?.source ?: source.label}; results and estimates for an event always come from the same source.")
            if (!plus && history.size > FREE_HISTORY) add("Showing the latest $FREE_HISTORY quarters. StockSteps+ shows up to ${history.size} quarters of available history.")
            if (reported.isEmpty()) add("No reported earnings history is available for this company.")
            if (sampleData) add("Sample earnings data for development, not real results.")
        }
        val stale = next?.let { runCatching { java.time.Instant.parse(it.updatedAt) }.getOrNull()?.isBefore(clock.instant().minusSeconds(STALE_DAYS * 86_400)) } == true
        return EarningsDetails(
            symbol.uppercase(), name, focus, EarningsCalculator.status(focus, today), eps, revenue,
            focus?.let { EarningsCalculator.growth(it, EarningsCalculator.yearAgo(it, reported)) },
            focus?.let { EarningsCalculator.growth(it, EarningsCalculator.previousQuarter(it, reported)) },
            summary?.first, summary?.second, next, shown, !plus && history.size > FREE_HISTORY, reaction,
            insights.filter { plus || !it.advanced }, if (plus) 0 else insights.count { it.advanced },
            notes, plus, clock.instant().toString(), stale, sampleData
        )
    }

    // ---------- Phase 3: post-earnings price reaction (free) ----------

    private val reactionPrices: ReactionPriceSource = ChartReactionPriceSource(charts, stocks,
        if (sampleData) "StockSteps sample price history" else "Daily closes from Financial Modeling Prep (split-adjusted)", clock)
        .let { if (sampleData) FixtureReactionPriceSource(it, clock) else it }
    /** The last real series per symbol, served (STALE) only when the price provider fails. */
    private val lastSeries = ConcurrentHashMap<String, PriceSeries>()

    /** A symbol's price series, or a plain reason when the provider can't supply one. Never sample data in REAL. */
    private suspend fun reactionSeries(symbol: String, scenario: String?): Pair<PriceSeries?, String?> {
        val key = symbol.uppercase()
        if (sampleData) when (scenario) {
            "price-timeout" -> return null to "The price data provider didn't respond in time. Try again shortly. (Sample scenario)"
            "price-rate-limit" -> return null to "The price data provider is limiting requests right now. Try again in a minute. (Sample scenario)"
            "price-stale" -> return reactionPrices.series(symbol).let { it.copy(freshness = DataFreshness.STALE, fetchedAt = clock.instant().minusSeconds(3 * 3600)) } to null
        }
        return try {
            reactionPrices.series(symbol).also { lastSeries[key] = it } to null
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            lastSeries[key]?.let { return it.copy(freshness = DataFreshness.STALE) to null }
            null to when ((cause as? org.example.stocksteps.repository.StockProviderException)?.failure) {
                org.example.stocksteps.repository.StockProviderException.Failure.RATE_LIMITED -> "The price data provider is limiting requests right now. Try again in a minute."
                org.example.stocksteps.repository.StockProviderException.Failure.TIMEOUT -> "The price data provider didn't respond in time. Try again shortly."
                else -> "Price history isn't available right now. Try again shortly."
            }
        }
    }

    /** Every known event for a company (shared, cached history), for the reminder scheduler. */
    suspend fun reminderEvents(symbol: String): List<EarningsEvent> = history(symbol).events

    /** The calendar event behind a published report (404/400 exactly as Earnings Results). */
    private suspend fun reportEvent(reportId: String): Pair<EarningsEvent, EarningsResultsResponse> {
        val results = resultsFor(reportId)
        val event = history(results.report.symbol).events.first { it.id == results.report.reportId }
        return enrich(listOf(event)).single() to results
    }

    /** Reaction for one window plus every window's status, the chart history and Phase 2 context. */
    suspend fun priceReaction(reportId: String, windowName: String?, includeExtendedHours: Boolean = false, scenario: String? = null): EarningsPriceReactionResponse {
        val window = windowName?.let { w -> ReactionWindow.entries.firstOrNull { it.name.equals(w, ignoreCase = true) }
            ?: throw EarningsRequestException(400, "INVALID_WINDOW", "Use window=FIRST_SESSION, THREE_SESSIONS or FIVE_SESSIONS.") } ?: ReactionWindow.FIRST_SESSION
        val (event, results) = reportEvent(reportId)
        val calendar = ExchangeCalendars.of(event.exchange, event.symbol)
        val (series, problem) = if (calendar == null) null to null else reactionSeries(event.symbol, scenario)
        val now = clock.instant()
        val all = ReactionWindow.entries.associateWith { PriceReactionEngine.compute(event, reportId, it, series, calendar, now, problem) }
        var reaction = all.getValue(window)
        if (includeExtendedHours) reaction = reaction.copy(warnings = reaction.warnings + "Extended-hours prices aren't available from the data source, so only regular-session closes are used.")
        // Phase 2 classifications are reused, never recalculated here.
        val eps = results.insights.eps.classification
        val revenue = results.insights.revenue.classification
        return EarningsPriceReactionResponse(reaction, PriceReactionEngine.history(event, reportId, series, calendar, now),
            all.map { (w, r) -> ReactionWindowOption(w, r.status) }, eps, revenue, PriceReactionExplainer.explanation(reaction, eps, revenue),
            PriceReactionExplainer.mixedNote(eps, revenue), PriceReactionExplainer.learnTopics, sampleData)
    }

    suspend fun priceHistory(reportId: String, scenario: String? = null): EarningsPriceHistory {
        val (event, _) = reportEvent(reportId)
        val calendar = ExchangeCalendars.of(event.exchange, event.symbol)
        val (series, _) = if (calendar == null) null to null else reactionSeries(event.symbol, scenario)
        return PriceReactionEngine.history(event, reportId, series, calendar, clock.instant())
    }

    /** Earnings Details' reaction uses the same engine and policy (first session), plus the SPY comparison it already showed. */
    private suspend fun reaction(event: EarningsEvent): PriceReaction {
        val methodology = "Regular-session closing prices around the announcement; ${EarningsEducation.topic("reaction")?.body.orEmpty()}"
        val calendar = ExchangeCalendars.of(event.exchange, event.symbol)
        val (series, problem) = if (calendar == null) null to null else reactionSeries(event.symbol, null)
        val r = PriceReactionEngine.compute(event, event.id, ReactionWindow.FIRST_SESSION, series, calendar, clock.instant(), problem)
        if (!r.hasChange) return PriceReaction(false, methodology = methodology, reason = r.statusMessage, source = series?.source)
        val us = calendar !is TsxExchangeCalendar
        val market = if (us) runCatching { reactionPrices.series("SPY") }.getOrNull()?.let { spy ->
            val a = spy.bars[LocalDate.parse(r.baseline!!.sessionDate)]?.close; val b = spy.bars[LocalDate.parse(r.endpoint!!.sessionDate)]?.close
            if (a != null && b != null && a > org.example.stocksteps.portfolio.Decimal.ZERO) (b - a).multiplyDivide(org.example.stocksteps.portfolio.Decimal.parse("100"), a).toString().toDouble() else null
        } else null
        val start = r.baseline!!; val end = r.endpoint!!
        return PriceReaction(true, r.status == ReactionStatus.EVENT_TIME_UNKNOWN, start.sessionDate, start.price.toDouble(), end.sessionDate,
            end.price.toDouble(), r.percentChange!!.toDouble(), market, if (market != null) "S&P 500 (SPY)" else null, r.currency, r.measurement,
            methodology, r.sources.firstOrNull(), r.calculatedAt)
    }

    /** StockSteps+ only, checked before any provider is called; fair-use limit per day. */
    suspend fun ask(uid: String, symbol: String, question: String): EarningsAnswer {
        if (!entitlements.get(uid).plus) throw EarningsRequestException(403, "PLUS_REQUIRED", "AI earnings research is part of StockSteps+.")
        val trimmed = question.trim()
        if (trimmed.length !in 3..500) throw EarningsRequestException(400, "INVALID_QUESTION", "Ask a question of 3–500 characters.")
        val provider = research ?: throw EarningsRequestException(503, "AI_UNAVAILABLE", "AI earnings research isn't available yet.")
        aiQuota?.let { quota ->
            val reservation = quota.reserve(uid, EarningsAiCategory.QUESTION)
            try {
                val answer = provider.answer(trimmed, details(symbol, uid))
                return answer.copy(remainingToday = quota.usage(uid, true, entitlements.get(uid).status).quota(EarningsAiCategory.QUESTION)?.remaining)
            } catch (cause: Throwable) { quota.release(reservation); throw cause }
        }
        val key = "$uid:${today()}"
        val used = aiUsage.merge(key, 1, Int::plus)!!
        if (used > aiDailyLimit) { aiUsage.merge(key, -1, Int::plus); throw EarningsRequestException(429, "AI_LIMIT", "You've reached today's limit of $aiDailyLimit questions.") }
        // Only public earnings data is sent; portfolio holdings are never included.
        val details = details(symbol, uid)
        return provider.answer(trimmed, details).copy(remainingToday = aiDailyLimit - used)
    }
}

/** Public calendar and details (free tier) plus signed-in following, tier-aware details and AI. */
fun Route.earningsRoutes(service: EarningsService, auth: UserAuthenticator, limiter: RequestRateLimiter) {
    suspend fun RoutingContext.guarded(block: suspend () -> Any) {
        if (!limiter.allow(call.request.origin.remoteHost)) {
            call.respond(HttpStatusCode.TooManyRequests, ApiError("RATE_LIMITED", "Too many requests. Wait a moment and try again.")); return
        }
        try { call.respond(block()) } catch (cause: EarningsRequestException) {
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message ?: "Request failed."))
        }
    }
    fun RoutingContext.query(): EarningsCalendarQuery {
        val p = call.request.queryParameters
        fun list(name: String) = p[name]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val today = LocalDate.now(ZoneId.of("America/New_York"))
        return EarningsCalendarQuery(
            from = p["from"] ?: today.toString(), to = p["to"] ?: today.plusDays(13).toString(),
            exchanges = list("exchange"), countries = list("country"),
            sessions = list("session").mapNotNull { s -> EarningsTime.entries.firstOrNull { it.name.equals(s, ignoreCase = true) } },
            symbol = p["symbol"], view = p["view"],
            pageSize = p["pageSize"]?.toIntOrNull() ?: 30, cursor = p["cursor"],
            query = p["q"]?.trim()?.takeIf { it.isNotEmpty() }, day = p["day"], scope = p["scope"], scenario = p["scenario"]
        )
    }
    get("/api/v1/earnings/calendar") { guarded { service.calendar(query()) } }
    get("/api/v1/earnings/calendar/search") {
        guarded { query().also { if (it.query == null) throw EarningsRequestException(400, "INVALID_QUERY", "Add a company name or ticker to search.") }.let { service.calendar(it) } }
    }
    get("/api/v1/earnings/events/{eventId}") { guarded { service.event(call.parameters["eventId"].orEmpty()) } }
    get("/api/v1/earnings/company/{symbol}/next") { guarded { service.nextEvent(call.parameters["symbol"].orEmpty()) } }
    get("/api/v1/earnings/company/{symbol}/latest") { guarded { service.latestResults(call.parameters["symbol"].orEmpty(), call.request.queryParameters["scenario"]) } }
    get("/api/v1/earnings/company/{symbol}/reports") { guarded { service.reports(call.parameters["symbol"].orEmpty()) } }
    get("/api/v1/earnings/reports/{reportId}") { guarded { service.resultsFor(call.parameters["reportId"].orEmpty(), call.request.queryParameters["scenario"]) } }
    get("/api/v1/earnings/reports/{reportId}/price-reaction") {
        guarded { service.priceReaction(call.parameters["reportId"].orEmpty(), call.request.queryParameters["window"],
            call.request.queryParameters["includeExtendedHours"].toBoolean(), call.request.queryParameters["scenario"]) }
    }
    get("/api/v1/earnings/reports/{reportId}/price-history") { guarded { service.priceHistory(call.parameters["reportId"].orEmpty(), call.request.queryParameters["scenario"]) } }
    get("/api/v1/earnings/reports/{reportId}/insights") { guarded { service.resultsFor(call.parameters["reportId"].orEmpty(), call.request.queryParameters["scenario"]).insights } }
    get("/api/v1/earnings/{symbol}") { guarded { service.details(call.parameters["symbol"].orEmpty(), null) } }
    route("/api/v1/me/earnings") {
        get("/following") { user(auth) { uid -> guarded { service.following(uid, query()) } } }
        get("/{symbol}") { user(auth) { uid -> guarded { service.details(call.parameters["symbol"].orEmpty(), uid) } } }
        post("/{symbol}/ask") { user(auth) { uid -> guarded { service.ask(uid, call.parameters["symbol"].orEmpty(), call.receive<EarningsQuestion>().question) } } }
    }
}
