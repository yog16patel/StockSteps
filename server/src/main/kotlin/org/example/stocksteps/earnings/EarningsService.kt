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
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 512)
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

    private suspend fun window(from: LocalDate, to: LocalDate): List<EarningsEvent> {
        // Recent/upcoming windows refresh hourly; windows entirely in the past are stable for 12 h.
        val ttl = if (to < today().minusDays(7)) 43_200_000L else 3_600_000L
        return try {
            cache.getOrLoad("calendar:$from:$to", ttl) { source.calendar(from, to) }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            log.warn("Earnings calendar unavailable: {}", cause::class.simpleName)
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

    private fun item(event: EarningsEvent, following: List<FollowReason> = emptyList(), shares: Double? = null): EarningsCalendarItem {
        val status = EarningsCalculator.status(event, today().toString())
        val reported = status == EarningsStatus.REPORTED || status == EarningsStatus.PARTIALLY_REPORTED
        return EarningsCalendarItem(event, status,
            if (reported) EarningsCalculator.eps(event.estimate, event.actual) else null,
            if (reported) EarningsCalculator.revenue(event.estimate, event.actual) else null, following, shares)
    }

    private fun sorted(items: List<EarningsCalendarItem>) = items.sortedWith(
        compareBy<EarningsCalendarItem> { it.event.date }.thenBy { SESSION_ORDER.indexOf(it.event.session) }.thenBy { it.event.name.lowercase() }.thenBy { it.event.symbol })

    private fun validate(query: EarningsCalendarQuery): Pair<LocalDate, LocalDate> {
        val from = runCatching { LocalDate.parse(query.from) }.getOrNull() ?: throw EarningsRequestException(400, "INVALID_RANGE", "Use dates like 2026-10-08.")
        val to = runCatching { LocalDate.parse(query.to) }.getOrNull() ?: throw EarningsRequestException(400, "INVALID_RANGE", "Use dates like 2026-10-08.")
        if (to < from || to.toEpochDay() - from.toEpochDay() > MAX_RANGE_DAYS) throw EarningsRequestException(400, "INVALID_RANGE", "Choose a range of up to $MAX_RANGE_DAYS days.")
        if (query.pageSize !in 1..MAX_PAGE) throw EarningsRequestException(400, "INVALID_PAGE", "Page size must be 1–$MAX_PAGE.")
        return from to to
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

    private fun matches(item: EarningsCalendarItem, query: EarningsCalendarQuery): Boolean {
        val e = item.event
        if (query.exchanges.isNotEmpty() && query.exchanges.none { it.equals(e.exchange, ignoreCase = true) }) return false
        if (query.countries.isNotEmpty() && query.countries.none { it.equals(e.country, ignoreCase = true) }) return false
        if (query.sessions.isNotEmpty() && e.session !in query.sessions) return false
        if (query.symbol != null && !query.symbol.equals(e.symbol, ignoreCase = true)) return false
        return when (query.view) {
            "upcoming" -> item.status == EarningsStatus.UPCOMING
            "results" -> item.status != EarningsStatus.UPCOMING
            else -> true
        }
    }

    private val baseNotes = listOf("Dates are exchange-local. Only company-confirmed dates are labelled Confirmed; others can change.")

    suspend fun calendar(query: EarningsCalendarQuery): EarningsCalendarPage {
        val (from, to) = validate(query)
        val items = enrich(window(from, to)).map { item(it) }.filter { matches(it, query) }
        return page(sorted(items), query, baseNotes)
    }

    /** Companies the user follows: positive portfolio holdings and watchlist entries (deduplicated by symbol). */
    suspend fun following(uid: String, query: EarningsCalendarQuery): EarningsCalendarPage {
        val (from, to) = validate(query)
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
        val items = enrich(window(from, to).filter { it.symbol.uppercase() in symbols }).map { e ->
            val s = e.symbol.uppercase()
            item(e, listOfNotNull(FollowReason.PORTFOLIO.takeIf { s in shares }, FollowReason.WATCHLIST.takeIf { s in watched }),
                shares[s]?.toString()?.toDouble())
        }.filter { matches(it, query) }
        return page(sorted(items), query, baseNotes + "Includes companies you hold in a portfolio and companies on your watchlists. Watching a company doesn't mean you own it.")
    }

    /** Next upcoming announcement for alerts, Home and Watchlist (one source with the calendar). */
    suspend fun next(symbol: String): UpcomingEarnings? {
        val today = today().toString()
        val event = events(symbol).filter { it.actual == null && it.date >= today && it.dateStatus != EarningsDateStatus.UNKNOWN }.minByOrNull { it.date } ?: return null
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

    /** Cached once the measurement window has completed (it never changes afterwards). */
    private suspend fun reaction(event: EarningsEvent): PriceReaction =
        cache.getOrLoad("reaction:${event.id}:${event.date}", 600_000L, resultTtl = { r: PriceReaction -> if (r.available) 7 * 86_400_000L else 600_000L }) {
            suspend fun closes(symbol: String) = runCatching { charts.getDailyCloses(symbol) }.getOrNull().orEmpty()
                .mapNotNull { p -> runCatching { LocalDate.parse(p.time.take(10)) }.getOrNull()?.let { it to p.close } }.toMap()
            val us = !event.exchange.equals("TSX", ignoreCase = true)
            val market = if (us) closes("SPY").takeIf { it.isNotEmpty() } else null
            val currency = runCatching { stocks.getProfile(event.symbol)?.currency }.getOrNull()
            EarningsReactionCalculator.compute(event, closes(event.symbol), market, if (us) "S&P 500 (SPY)" else null, clock.instant(), currency,
                if (sampleData) "StockSteps sample price history" else "Daily closes from Financial Modeling Prep")
        }

    /** StockSteps+ only, checked before any provider is called; fair-use limit per day. */
    suspend fun ask(uid: String, symbol: String, question: String): EarningsAnswer {
        if (!entitlements.get(uid).plus) throw EarningsRequestException(403, "PLUS_REQUIRED", "AI earnings research is part of StockSteps+.")
        val trimmed = question.trim()
        if (trimmed.length !in 3..500) throw EarningsRequestException(400, "INVALID_QUESTION", "Ask a question of 3–500 characters.")
        val provider = research ?: throw EarningsRequestException(503, "AI_UNAVAILABLE", "AI earnings research isn't available yet.")
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
            symbol = p["symbol"], view = p["view"]?.takeIf { it in setOf("upcoming", "results") },
            pageSize = p["pageSize"]?.toIntOrNull() ?: 30, cursor = p["cursor"]
        )
    }
    get("/api/v1/earnings/calendar") { guarded { service.calendar(query()) } }
    get("/api/v1/earnings/{symbol}") { guarded { service.details(call.parameters["symbol"].orEmpty(), null) } }
    route("/api/v1/me/earnings") {
        get("/following") { user(auth) { uid -> guarded { service.following(uid, query()) } } }
        get("/{symbol}") { user(auth) { uid -> guarded { service.details(call.parameters["symbol"].orEmpty(), uid) } } }
        post("/{symbol}/ask") { user(auth) { uid -> guarded { service.ask(uid, call.parameters["symbol"].orEmpty(), call.receive<EarningsQuestion>().question) } } }
    }
}
