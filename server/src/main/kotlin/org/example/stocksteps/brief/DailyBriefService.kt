package org.example.stocksteps.brief

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import org.example.stocksteps.earnings.EarningsCalendarQuery
import org.example.stocksteps.earnings.EarningsService
import org.example.stocksteps.model.*
import org.example.stocksteps.service.MarketsService
import org.example.stocksteps.userdata.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Index quotes and market news for the global brief (shared, cached upstream). */
interface BriefMarketSource {
    suspend fun indices(): List<IndexQuote>
    suspend fun news(): List<NewsArticle>
}

/** Reuses the Markets overview, so a brief adds no provider calls beyond the Markets screen's cache. */
class MarketsBriefSource(private val markets: MarketsService) : BriefMarketSource {
    override suspend fun indices() = markets.overview().indices
    override suspend fun news() = markets.overview().news
}

/** A user's notification settings plus delivery bookkeeping (server only). */
@Serializable
data class BriefPreferenceRecord(val uid: String, val preferences: BriefPreferences, val lastNotifiedBriefId: String? = null, val lastNotifiedAt: Long? = null, val updatedAt: Long = 0)

class BriefRequestException(val status: Int, val code: String, message: String) : Exception(message)

/**
 * MOCK scenarios: a pinned instant plus deterministic edits of the fixture data (time shifts per
 * market, fewer/duplicate stories, missing or stale index, missing publisher, invalid URL, AI states).
 */
data class BriefScenario(
    val name: String,
    val now: Instant,
    /** Moves US / Canadian quote times (and news) to the session the scenario describes. */
    val usQuoteAt: Instant? = null,
    val caQuoteAt: Instant? = null,
    val stories: Int? = null,
    val duplicateStories: Boolean = false,
    val dropIndex: String? = null,
    val staleIndex: String? = null,
    val missingPublisher: Boolean = false,
    val invalidUrl: Boolean = false,
    val aiUnavailable: Boolean = false,
    val aiQuota: Boolean = false
) {
    companion object {
        private val capture = Instant.parse("2026-10-07T21:15:00Z")
        val all: Map<String, BriefScenario> = listOf(
            BriefScenario("normal", capture),
            BriefScenario("after-close", capture),
            BriefScenario("pre-market", Instant.parse("2026-10-08T12:00:00Z")),
            BriefScenario("market-hours", Instant.parse("2026-10-08T15:00:00Z"), usQuoteAt = Instant.parse("2026-10-08T14:40:00Z"), caQuoteAt = Instant.parse("2026-10-08T14:40:00Z")),
            BriefScenario("weekend", Instant.parse("2026-10-10T14:00:00Z"), usQuoteAt = Instant.parse("2026-10-09T20:00:00Z"), caQuoteAt = Instant.parse("2026-10-09T20:00:00Z")),
            BriefScenario("holiday", Instant.parse("2026-12-25T15:00:00Z"), usQuoteAt = Instant.parse("2026-12-24T18:00:00Z"), caQuoteAt = Instant.parse("2026-12-24T21:00:00Z")),
            BriefScenario("us-open-ca-closed", Instant.parse("2026-10-12T15:00:00Z"), usQuoteAt = Instant.parse("2026-10-12T14:40:00Z"), caQuoteAt = Instant.parse("2026-10-09T20:00:00Z")),
            BriefScenario("ca-open-us-closed", Instant.parse("2026-11-26T16:00:00Z"), usQuoteAt = Instant.parse("2026-11-25T21:00:00Z"), caQuoteAt = Instant.parse("2026-11-26T15:40:00Z")),
            BriefScenario("three-stories", capture, stories = 3),
            BriefScenario("one-story", capture, stories = 1),
            BriefScenario("no-stories", capture, stories = 0),
            BriefScenario("duplicate-stories", capture, duplicateStories = true),
            BriefScenario("missing-index", capture, dropIndex = "NASDAQ_COMPOSITE"),
            BriefScenario("stale-index", capture, staleIndex = "TSX"),
            BriefScenario("missing-publisher", capture, missingPublisher = true),
            BriefScenario("invalid-url", capture, invalidUrl = true),
            BriefScenario("ai-unavailable", capture, aiUnavailable = true),
            BriefScenario("ai-quota", capture, aiQuota = true)
        ).associateBy { it.name }
    }
}

class DailyBriefService(
    private val source: BriefMarketSource,
    private val store: UserDataStore,
    private val entitlements: EntitlementService,
    private val watch: WatchMarketData?,
    private val earnings: EarningsService?,
    private val ai: BriefAiProvider?,
    /** Market time (MOCK pins it to the fixture capture). */
    private val marketClock: Clock,
    /** Real backend time for AI quotas and notification bookkeeping. */
    private val clock: Clock,
    val sampleData: Boolean,
    private val aiDailyLimit: Int = 15,
    private val sessions: BriefSessions = BriefSessions(),
    /** Optional combined StockSteps+ daily AI cap (Phase 4B, decision D6); null = feature limits only. */
    combinedAiCap: org.example.stocksteps.service.CombinedAiCap? = null
) {
    private val et = ZoneId.of("America/New_York")
    private val lock = Mutex()
    private val memory = ConcurrentHashMap<String, DailyBrief>()
    private val personal = ConcurrentHashMap<String, Pair<Long, PersonalizedBrief>>()
    /** Phase 4B: durable Brief AI allowance on the user's `aiUsage` document (shared across instances and restarts). */
    private val aiQuota = org.example.stocksteps.service.DurableAiQuota(store, clock, combinedAiCap)
    private val permits = Semaphore(6)
    private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val longDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val hm = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    companion object {
        /** Market-hours briefs refresh their numbers this often; other editions are built once. */
        const val MARKET_HOURS_REFRESH = 15 * 60_000L
        const val PERSONAL_TTL = 5 * 60_000L
        private val ID = Regex("[a-z0-9-]{10,80}")
        /** The Brief AI feature tag on `aiUsage` charges (Phase 4B). */
        const val BRIEF_AI_FEATURE = "brief-ai"
    }

    fun scenario(name: String?): BriefScenario? = name?.let {
        if (!sampleData) throw BriefRequestException(404, "NOT_FOUND", "Not found.")
        BriefScenario.all[it] ?: throw BriefRequestException(400, "UNKNOWN_SCENARIO", "Unknown scenario.")
    }

    private fun now(s: BriefScenario?) = s?.now ?: marketClock.instant()
    private fun parseInstant(text: String): Long? = runCatching { Instant.parse(text).epochSecond }.getOrNull()
        ?: runCatching { LocalDateTime.parse(text.replace(' ', 'T')).atZone(et).toEpochSecond() }.getOrNull()

    // ---------- Global brief ----------

    suspend fun latest(scenarioName: String? = null): DailyBrief {
        val s = scenario(scenarioName)
        val now = now(s)
        val moment = sessions.moment(now)
        val id = "${moment.briefDate}-${moment.edition.name.lowercase().replace('_', '-')}" + (s?.let { "-mock-${it.name}" } ?: "")
        memory[id]?.let { existing ->
            val fresh = existing.edition != BriefEdition.MARKET_HOURS || marketClock.millis() - Instant.parse(existing.updatedAt).toEpochMilli() < MARKET_HOURS_REFRESH || s != null
            if (fresh) return existing
        }
        return lock.withLock {
            memory[id]?.takeIf { it.edition != BriefEdition.MARKET_HOURS || s != null || marketClock.millis() - Instant.parse(it.updatedAt).toEpochMilli() < MARKET_HOURS_REFRESH }
                ?: build(id, moment, now, s, memory[id]).also { brief ->
                    memory[id] = brief
                    if (s == null) safe { store.saveBrief(brief) }
                }
        }
    }

    suspend fun byId(id: String, access: BriefAccess): DailyBrief {
        if (!ID.matches(id)) throw BriefRequestException(400, "INVALID_ID", "Invalid brief id.")
        if (!id.contains("-mock-") && access != BriefAccess.PLUS) {
            val allowed = history(access).items.map { it.id }
            if (id !in allowed) throw BriefRequestException(403, "HISTORY_LOCKED", "Older briefs are included with StockSteps+.")
        }
        return memory[id] ?: safe { store.brief(id) } ?: throw BriefRequestException(404, "BRIEF_NOT_FOUND", "This brief isn't available.")
    }

    suspend fun history(access: BriefAccess): BriefHistory {
        latest()
        val stored = safe { store.recentBriefs(BriefPolicy.PLUS_HISTORY + 1) }.orEmpty()
        val all = (stored + memory.values.filterNot { it.id.contains("-mock-") }.map { it.summary() }).distinctBy { it.id }.sortedByDescending { it.generatedAt }
        val limit = BriefPolicy.historyLimit(access)
        return BriefHistory(all.take(limit), access, lockedCount = (all.size - limit).coerceAtLeast(0))
    }

    private suspend fun <T> safe(block: suspend () -> T): T? = try { block() } catch (cause: Exception) { if (cause is CancellationException) throw cause; null }

    private fun localDate(instant: Instant) = instant.atZone(et).toLocalDate()

    private fun shift(quote: IndexQuote, s: BriefScenario?, market: String): IndexQuote {
        s ?: return quote
        val target = (if (market == "CA") s.caQuoteAt else s.usQuoteAt) ?: return if (s.staleIndex == quote.id) staled(quote) else quote
        val shifted = quote.copy(asOf = target.toString())
        return if (s.staleIndex == quote.id) staled(shifted) else shifted
    }
    private fun staled(q: IndexQuote) = q.copy(asOf = q.asOf?.let { Instant.parse(it).minus(Duration.ofDays(4)).toString() })

    private fun index(q: IndexQuote, session: BriefMarketSession): BriefIndex {
        val market = if (q.region == "Canada") "CA" else "US"
        val at = q.asOf?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val date = at?.let { localDate(it) }
        val value = q.value?.takeIf { it.isFinite() }
        val (state, label) = when {
            value == null || q.error != null -> QuoteState.UNAVAILABLE to "Not available right now"
            date == null -> QuoteState.STALE to "Time of this value isn't known"
            session.phase == BriefPhase.OPEN && date.toString() == session.localDate -> QuoteState.LIVE_DELAYED to "During today's session (may be delayed) · ${hm.format(at.atZone(et))} ET"
            date.toString() == session.lastCompletedSession && session.phase == BriefPhase.OPEN -> QuoteState.PREVIOUS_CLOSE to "Previous close · ${shortDate.format(date)} (today's values aren't available yet)"
            date.toString() == session.lastCompletedSession -> QuoteState.SESSION_CLOSE to "Close · ${shortDate.format(date)}"
            else -> QuoteState.STALE to "From an earlier session (${shortDate.format(date)})"
        }
        return BriefIndex(q.id, q.name, market, value, q.change?.takeIf { it.isFinite() && value != null }, q.changePercent?.takeIf { it.isFinite() && value != null },
            q.currency, q.unit, q.isProxy, q.proxyDescription, q.asOf, date?.toString(), state, label)
    }

    private fun summaryLine(moment: BriefMoment, indices: List<BriefIndex>): String {
        fun moves(states: Set<QuoteState>, list: List<BriefIndex> = indices) = list.filter { it.state in states }.mapNotNull { BriefWording.move(it.displayName, it.changePercent) }
        fun join(parts: List<String>) = when (parts.size) { 0 -> ""; 1 -> parts[0]; else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last() }
        val us = indices.filter { it.market == "US" }
        val ca = indices.filter { it.market == "CA" }
        val closes = setOf(QuoteState.SESSION_CLOSE, QuoteState.PREVIOUS_CLOSE)
        val session = shortDate.format(moment.sessionDate)
        val usLine = when (moment.edition) {
            BriefEdition.MARKET_HOURS -> moves(setOf(QuoteState.LIVE_DELAYED), us).takeIf { it.isNotEmpty() }?.let { "US markets are open. So far today (delayed), ${join(it)}." }
                ?: "US markets are open; today's index values aren't available yet."
            BriefEdition.PRE_MARKET -> "US markets haven't opened yet." + (moves(closes, us).takeIf { it.isNotEmpty() }?.let { " In the last session ($session), ${join(it)}." } ?: "")
            BriefEdition.AFTER_CLOSE -> moves(closes, us).takeIf { it.isNotEmpty() }?.let { "At the close on $session, ${join(it)}." } ?: "US markets have closed for the day."
            BriefEdition.WEEKEND -> "Markets are closed for the weekend." + (moves(closes, us).takeIf { it.isNotEmpty() }?.let { " In the last session (${longDate.format(moment.sessionDate)}), ${join(it)}." } ?: "")
            BriefEdition.HOLIDAY -> "US markets are closed for ${moment.us.holiday ?: "a holiday"}." + (moves(closes, us).takeIf { it.isNotEmpty() }?.let { " In the last session (${longDate.format(moment.sessionDate)}), ${join(it)}." } ?: "")
        }
        val caLine = when (moment.ca.phase) {
            BriefPhase.OPEN -> moves(setOf(QuoteState.LIVE_DELAYED), ca).takeIf { it.isNotEmpty() }?.let { "In Canada (open, delayed), ${join(it)}." } ?: "Canadian markets are open."
            BriefPhase.HOLIDAY -> if (moment.us.phase != BriefPhase.HOLIDAY) "Canadian markets are closed for ${moment.ca.holiday}." else null
            else -> moves(closes, ca).takeIf { it.isNotEmpty() && moment.edition != BriefEdition.WEEKEND && moment.edition != BriefEdition.HOLIDAY }?.let { "In Canada, ${join(it)}." }
                ?: moves(closes, ca).takeIf { it.isNotEmpty() }?.let { "In Canada, ${join(it)}." }
        }
        return listOfNotNull(usLine, caLine).joinToString(" ")
    }

    private fun story(a: NewsArticle): BriefStory {
        val id = a.id?.takeIf { it.isNotBlank() } ?: "u" + a.url.hashCode().toUInt().toString(16)
        return BriefStory(id, a.title.trim(), StoryRanker.excerpt(a.description), a.source?.takeIf { it.isNotBlank() }, a.publishedAt, a.url,
            listOfNotNull(a.symbol?.uppercase()), StoryRanker.topic(a.category), StoryRanker.EVIDENCE_NOTE, listOf(id))
    }

    private fun mockNews(articles: List<NewsArticle>, s: BriefScenario?): List<NewsArticle> {
        s ?: return articles
        var list = articles
        val shiftTo = s.usQuoteAt
        if (shiftTo != null) {
            val newest = list.mapNotNull { a -> a.publishedAt?.let(::parseInstant) }.maxOrNull()
            if (newest != null) {
                val delta = shiftTo.epochSecond - newest
                list = list.map { a -> a.copy(publishedAt = a.publishedAt?.let(::parseInstant)?.let { Instant.ofEpochSecond(it + delta).toString() }) }
            }
        }
        if (s.duplicateStories && list.isNotEmpty()) list = listOf(list[0], list[0].copy(id = "${list[0].id}-dup", title = list[0].title + " - update", url = list[0].url + "?ref=dup"), list[0].copy(id = "${list[0].id}-dup2")) + list.drop(1)
        if (s.missingPublisher && list.isNotEmpty()) list = listOf(list[0].copy(source = null)) + list.drop(1)
        if (s.invalidUrl && list.isNotEmpty()) list = listOf(list[0].copy(url = "not-a-url"), list[1].copy(url = "http://insecure.example/news")) + list.drop(2)
        return list
    }

    private suspend fun build(id: String, moment: BriefMoment, now: Instant, s: BriefScenario?, previous: DailyBrief?): DailyBrief = coroutineScope {
        val indicesCall = async { safe { source.indices() } }
        val newsCall = async { safe { source.news() } }
        val rawIndices = indicesCall.await()
        val rawNews = newsCall.await()
        val wanted = listOf("SP500", "NASDAQ_COMPOSITE", "TSX")
        val indices = wanted.mapNotNull { idx -> rawIndices?.firstOrNull { it.id == idx } }
            .filterNot { it.id == s?.dropIndex }
            .map { q -> val market = if (q.region == "Canada") "CA" else "US"; index(shift(q, s, market), if (market == "CA") moment.ca else moment.us) }
        val articles = mockNews(rawNews.orEmpty(), s)
        val ranked = StoryRanker.rank(articles, now.epochSecond, ::parseInstant, limit = s?.stories?.coerceAtMost(BriefPolicy.MAX_STORIES) ?: BriefPolicy.MAX_STORIES)
        val stories = ranked.map(::story)
        val closed = moment.edition == BriefEdition.WEEKEND || moment.edition == BriefEdition.HOLIDAY
        val concept = BriefContent.concept(moment.briefDate.dayOfYear, stories.any { it.topic == "Earnings" }, closed)
        val summary = summaryLine(moment, indices)
        val sources = stories.map { BriefSource(it.id, it.publisher, it.sourceUrl, it.publishedAt, it.headline) }
        val generated = now.toString()
        val texts = listOf(summary, BriefContent.INDEX_EXPLAINER, concept.explanation, concept.example.orEmpty()) + stories.flatMap { listOf(it.headline, it.summary.orEmpty()) }
        val missing = wanted.size - indices.count { it.state != QuoteState.UNAVAILABLE }
        DailyBrief(
            id = id, briefDate = moment.briefDate.toString(), sessionDate = moment.sessionDate.toString(), edition = moment.edition,
            sessions = listOf(moment.us, moment.ca), summaryLine = summary, marketSnapshot = indices, stories = stories, concept = concept,
            sources = sources, generatedAt = previous?.generatedAt ?: generated, updatedAt = generated,
            status = when { rawIndices == null && rawNews == null -> BriefStatus.UNAVAILABLE; missing > 0 || rawNews == null || stories.isEmpty() -> BriefStatus.PARTIAL; else -> BriefStatus.COMPLETE },
            readingMinutes = BriefContent.readingMinutes(texts),
            notes = listOfNotNull(
                if (missing > 0) "Some index values aren't available right now and are left out rather than estimated." else null,
                if (indices.any { it.state == QuoteState.STALE }) "Some values are from an earlier session and are labelled." else null,
                if (rawNews == null) "Market news couldn't be loaded right now." else if (stories.isEmpty()) "No market stories met our source checks for this brief." else null,
                if (stories.isNotEmpty()) "Stories link to the original publisher. Summaries are short excerpts from the news provider." else null,
                if (sampleData) "Sample data for development: captured prices and news, not live markets." else null
            ),
            sampleData = sampleData
        )
    }

    // ---------- Personalized overlay (private; never cached with public content) ----------

    suspend fun access(uid: String?): BriefAccess = when {
        uid == null -> BriefAccess.ANONYMOUS
        entitlements.get(uid).plus -> BriefAccess.PLUS
        else -> BriefAccess.FREE
    }

    suspend fun personalized(uid: String, briefId: String, scenarioName: String? = null): PersonalizedBrief {
        val access = access(uid)
        val brief = if (scenarioName != null) latest(scenarioName) else byId(briefId, access)
        val watchlists = store.updateWatchlists(uid) { it to it }
        val revision = watchlists.watchlists.joinToString("|") { l -> l.id + ":" + l.entries.joinToString(",") { it.instrument.symbol } }
        val key = "$uid|${brief.id}|$access|${revision.hashCode()}"
        personal[key]?.takeIf { clock.millis() - it.first < PERSONAL_TTL }?.let { return it.second }
        val result = buildPersonal(uid, brief, access, watchlists.watchlists.flatMap { l -> l.entries.map { it.instrument.symbol.uppercase() } }.distinct().take(50), scenario(scenarioName))
        personal.keys.removeIf { it.startsWith("$uid|") }
        personal[key] = clock.millis() to result
        return result
    }

    /** Drops this user's overlays (sign-out, watchlist or plan changes are also covered by the cache key). */
    fun forget(uid: String) { personal.keys.removeIf { it.startsWith("$uid|") } }

    private suspend fun buildPersonal(uid: String, brief: DailyBrief, access: BriefAccess, symbols: List<String>, s: BriefScenario?): PersonalizedBrief = coroutineScope {
        val now = now(s)
        val sp = brief.marketSnapshot.firstOrNull { it.indexId == "SP500" && it.changePercent != null && it.state != QuoteState.STALE }
        data class Row(val symbol: String, val name: String?, val quote: StockQuote?, val news: List<NewsArticle>, val upcoming: UpcomingEarnings?, val result: org.example.stocksteps.earnings.EarningsEvent?)
        val rows = if (watch == null) emptyList() else symbols.map { symbol -> async {
            permits.withPermit {
                Row(symbol, safe { watch.profile(symbol) }?.companyName, safe { watch.quote(symbol) }, safe { watch.companyNews(symbol) }.orEmpty(),
                    safe { watch.upcomingEarnings(symbol) }, safe { watch.recentEarningsResult(symbol) })
            }
        } }.awaitAll()
        val highlights = mutableListOf<Pair<Double, WatchlistHighlight>>()
        rows.forEach { r ->
            val q = r.quote
            val price = q?.price?.takeIf { it.isFinite() && it > 0 }
            val previous = q?.previousClose?.takeIf { it.isFinite() && it > 0 }
            val date = q?.timestamp?.let { localDate(Instant.ofEpochSecond(it)) }?.toString()
            // Only a move from the brief's own session; never an older quote presented as today's.
            if (price != null && previous != null && date == brief.sessionDate) {
                val pct = (price / previous - 1) * 100
                if (kotlin.math.abs(pct) >= 2.0) highlights += kotlin.math.abs(pct) to WatchlistHighlight(r.symbol, r.name, HighlightKind.PRICE_MOVE,
                    "${BriefWording.move(r.symbol, pct)} in the ${shortDate.format(LocalDate.parse(date))} session.", at = Instant.ofEpochSecond(q.timestamp!!).toString())
            }
            r.result?.let { e -> highlights += 50.0 to WatchlistHighlight(r.symbol, r.name, HighlightKind.EARNINGS_RESULT, "Reported quarterly results on ${shortDate.format(LocalDate.parse(e.date))}.", at = e.date,
                reportId = e.id.takeIf { e.actual?.let { a -> a.eps != null || a.revenue != null } == true }) }
            r.upcoming?.takeIf { runCatching { LocalDate.parse(it.date) <= localDate(now).plusDays(7) }.getOrDefault(false) }?.let { u ->
                highlights += 40.0 to WatchlistHighlight(r.symbol, r.name, HighlightKind.EARNINGS_UPCOMING,
                    "Expected to report on ${shortDate.format(LocalDate.parse(u.date))} (${u.status.name.lowercase()} date${timing(u.time)?.let { t -> ", $t" } ?: ""}).", at = u.date)
            }
            r.news.filter(StoryRanker::valid).firstOrNull { a -> StoryRanker.ageHours(a.publishedAt, now.epochSecond, ::parseInstant) <= 48 }?.let { a ->
                highlights += 30.0 to WatchlistHighlight(r.symbol, r.name, HighlightKind.NEWS, a.title.trim(), a.url, a.source?.takeIf { it.isNotBlank() }, a.publishedAt)
            }
        }
        val sorted = highlights.sortedWith(compareByDescending<Pair<Double, WatchlistHighlight>> { it.first }.thenBy { it.second.symbol }).map { it.second }
        val limit = BriefPolicy.highlightLimit(access)
        val globalUrls = brief.stories.map { it.sourceUrl }.toSet()
        val companyStories = if (BriefPolicy.companyStoryLimit(access) == 0) emptyList() else
            StoryRanker.rank(rows.flatMap { it.news }.filterNot { it.url in globalUrls }, now.epochSecond, ::parseInstant, symbols.toSet(), BriefPolicy.companyStoryLimit(access)).map(::story)
        val today = localDate(now)
        val query = EarningsCalendarQuery(today.toString(), today.plusDays(14).toString(), view = "upcoming", pageSize = 20)
        val followed = safe { earnings?.following(uid, query) }?.items.orEmpty()
        val earningsRows = (if (followed.isNotEmpty()) followed.take(if (access == BriefAccess.PLUS) 8 else 3).map { item ->
            earningsRow(item.event, if (org.example.stocksteps.earnings.FollowReason.PORTFOLIO in item.following) "portfolio" else "watchlist")
        } else safe { earnings?.calendar(query.copy(to = today.plusDays(7).toString())) }?.items.orEmpty().take(3).map { earningsRow(it.event, "general") })
        val premium: List<String>? = if (access != BriefAccess.PLUS) null else buildList<String> {
            val moved = sorted.filter { it.kind == HighlightKind.PRICE_MOVE }
            if (symbols.isNotEmpty()) add("${rows.count { r: Row -> r.news.any { StoryRanker.valid(it) && StoryRanker.ageHours(it.publishedAt, now.epochSecond, ::parseInstant) <= 48 } }} of the ${symbols.size} companies you follow have news from the last two days.")
            moved.firstOrNull()?.let { add("The largest move among companies you follow: ${it.text}" + (sp?.changePercent?.let { change -> " The S&P 500's change in that session was ${if (change >= 0) "+" else "−"}${BriefWording.pct(change)}." } ?: "")) }
            val soon = earningsRows.filter { it.reason != "general" }
            if (soon.isNotEmpty()) add("${soon.size} compan${if (soon.size == 1) "y you follow reports" else "ies you follow report"} earnings in the next two weeks: ${soon.joinToString { it.symbol }}. Results are compared with analysts' estimates, which can move prices either way.")
            if (isEmpty()) add("Nothing notable from the companies you follow in this session.")
        }
        PersonalizedBrief(brief.id, access, symbols.size, sorted.take(limit), moreHighlights = (sorted.size - limit).coerceAtLeast(0), companyStories = companyStories,
            earnings = earningsRows, premiumInsights = premium, aiAvailable = BriefPolicy.aiAllowed(access) && ai != null,
            notes = listOfNotNull(
                if (symbols.isEmpty()) "Add companies to a watchlist to see their news, price moves and earnings here." else null,
                "Watching a company doesn't mean you own it. Price moves of 2% or more from the brief's session are shown, without a cause unless a source states one.",
                if (followed.isEmpty() && earningsRows.isNotEmpty()) "No upcoming earnings from companies you follow; these are a few general examples." else null
            ), generatedAt = clock.instant().toString())
    }

    private fun timing(time: EarningsTime): String? = when (time) { EarningsTime.BEFORE_OPEN -> "before market open"; EarningsTime.AFTER_CLOSE -> "after market close"; else -> null }

    private fun earningsRow(e: org.example.stocksteps.earnings.EarningsEvent, reason: String) = BriefEarnings(e.symbol, e.name, e.date,
        timing(e.session)?.replaceFirstChar { it.uppercase() }, e.dateStatus.name.lowercase().replaceFirstChar { it.uppercase() }, e.estimate?.eps, e.estimate?.currency, reason, e.id)

    // ---------- AI (StockSteps+ only) ----------

    suspend fun ai(uid: String, briefId: String, request: BriefAiRequest, scenarioName: String? = null): BriefAiAnswer {
        val s = scenario(scenarioName)
        if (access(uid) != BriefAccess.PLUS) throw BriefRequestException(403, "PLUS_REQUIRED", "AI explanations are part of StockSteps+.")
        val question = request.question?.trim()
        if (question != null && question.length !in 3..300) throw BriefRequestException(400, "INVALID_QUESTION", "Ask a question of 3–300 characters.")
        if (question == null && request.storyId == null) throw BriefRequestException(400, "INVALID_QUESTION", "Choose a story or ask a question.")
        val brief = if (s != null) latest(scenarioName) else byId(briefId, BriefAccess.PLUS)
        val stories = request.storyId?.let { id -> listOf(brief.stories.firstOrNull { it.id == id } ?: throw BriefRequestException(404, "STORY_NOT_FOUND", "This story isn't in the brief.")) } ?: brief.stories
        val provider = ai?.takeUnless { s?.aiUnavailable == true } ?: throw BriefRequestException(503, "AI_UNAVAILABLE", "AI explanations aren't available right now.")
        if (s?.aiQuota == true) throw BriefRequestException(429, "AI_LIMIT", "You've reached today's limit of 0 AI explanations.")
        // Phase 4B: durable per-user allowance (Firestore `aiUsage` transaction in REAL), reserved before the provider call.
        val policy = org.example.stocksteps.service.AiQuotaPolicy(BRIEF_AI_FEATURE, aiDailyLimit)
        val reservation = try {
            aiQuota.reserve(uid, policy, org.example.stocksteps.service.DurableAiQuota.clientKey(request.idempotencyKey) ?: ("b" + java.util.UUID.randomUUID().toString().replace("-", "").take(24)))
        } catch (denied: org.example.stocksteps.service.AiQuotaDenied) {
            throw BriefRequestException(429, "AI_LIMIT", "You've reached today's limit of ${denied.limit} AI explanations.")
        } catch (cause: org.example.stocksteps.service.AiQuotaUnavailable) {
            throw BriefRequestException(503, "AI_UNAVAILABLE", "AI explanations aren't available right now.")
        }
        val context = BriefAiContext(brief.summaryLine, brief.marketSnapshot, stories, question)
        val draft = try { withTimeout(12_000) { provider.answer(context) } } catch (cause: Exception) {
            // A timeout or cancellation keeps the charge (the provider may have produced a result); a provider error is refunded.
            aiQuota.settleFailure(reservation, cause)
            if (cause is CancellationException && cause !is TimeoutCancellationException) throw cause
            throw BriefRequestException(503, "AI_UNAVAILABLE", "AI explanations aren't available right now.")
        }
        val valid = BriefAiValidator.validate(draft, context) ?: run {
            aiQuota.release(reservation)
            throw BriefRequestException(502, "AI_UNRELIABLE", "We couldn't produce an explanation that matched the sources. Nothing was shown instead of a guess.")
        }
        val used = try { aiQuota.usage(uid, listOf(policy))[BRIEF_AI_FEATURE]?.first ?: 0 } catch (cause: org.example.stocksteps.service.AiQuotaUnavailable) { aiDailyLimit }
        return BriefAiAnswer(valid.answer, valid.points, stories.filter { it.id in valid.sourceIds }.map { BriefSource(it.id, it.publisher, it.sourceUrl, it.publishedAt, it.headline) },
            valid.insufficientEvidence, (aiDailyLimit - used).coerceAtLeast(0), provider.usesAi)
    }

    // ---------- Preferences and notifications ----------

    suspend fun preferences(uid: String): BriefPreferences = store.updateBriefPreferences(uid) { it to (it?.preferences ?: BriefPreferences()) }

    suspend fun savePreferences(uid: String, value: BriefPreferences): BriefPreferences {
        if (value.deliveryHour !in 0..23 || listOfNotNull(value.quietStartHour, value.quietEndHour).any { it !in 0..23 }) throw BriefRequestException(400, "INVALID_PREFERENCES", "Choose hours between 0 and 23.")
        if (runCatching { ZoneId.of(value.timeZone) }.isFailure) throw BriefRequestException(400, "INVALID_PREFERENCES", "Unknown time zone.")
        if (value.markets.isEmpty() || value.markets.any { it !in setOf("US", "CA") }) throw BriefRequestException(400, "INVALID_PREFERENCES", "Choose US and/or CA.")
        // Personalized notifications are a StockSteps+ option; the stored value is checked again at send time.
        val clean = value.copy(personalizedNotifications = value.personalizedNotifications && access(uid) == BriefAccess.PLUS)
        return store.updateBriefPreferences(uid) { current ->
            (current ?: BriefPreferenceRecord(uid, clean)).copy(preferences = clean, updatedAt = clock.millis()) to clean
        }
    }

    @Serializable data class DispatchReport(val checked: Int, val sent: Int, val skipped: Int, val failed: Int, val briefId: String?)

    /**
     * Sends the "brief is ready" notification to opted-in users whose local delivery hour it is, once
     * per brief, never in quiet hours, and not on days when both markets are closed. MOCK uses the
     * simulated sender (nothing reaches FCM/APNs).
     */
    suspend fun dispatch(sender: PushSender): DispatchReport {
        val now = clock.instant()
        val moment = sessions.moment(marketClock.instant())
        if (moment.us.phase in setOf(BriefPhase.WEEKEND, BriefPhase.HOLIDAY) && moment.ca.phase in setOf(BriefPhase.WEEKEND, BriefPhase.HOLIDAY)) return DispatchReport(0, 0, 0, 0, null)
        val brief = latest()
        var sent = 0; var skipped = 0; var failed = 0
        val subscribers = safe { store.briefSubscribers(500) }.orEmpty()
        for (record in subscribers) {
            val p = record.preferences
            val local = now.atZone(runCatching { ZoneId.of(p.timeZone) }.getOrDefault(et))
            val hour = local.hour
            val qs = p.quietStartHour
            val qe = p.quietEndHour
            val quiet = qs != null && qe != null && (if (qs <= qe) hour in qs until qe else hour >= qs || hour < qe)
            if (!p.notificationsEnabled || hour != p.deliveryHour || quiet || record.lastNotifiedBriefId == brief.id) { skipped++; continue }
            // Claim first, so a retry or a second worker can't send it twice.
            val claimed = store.updateBriefPreferences(record.uid) { current ->
                if (current == null || current.lastNotifiedBriefId == brief.id || !current.preferences.notificationsEnabled) current to false
                else current.copy(lastNotifiedBriefId = brief.id, lastNotifiedAt = now.toEpochMilli()) to true
            }
            if (!claimed) { skipped++; continue }
            val personalLine = if (p.personalizedNotifications && access(record.uid) == BriefAccess.PLUS &&
                safe { personalized(record.uid, brief.id) }?.watchlistHighlights?.isNotEmpty() == true) "Your market brief includes updates about companies you follow." else null
            val devices = safe { store.devices(record.uid) }.orEmpty()
            for (device in devices) {
                when (sender.send(PushMessage(device.token, "Your StockSteps Market Brief is ready.", personalLine ?: brief.summaryLine.take(160),
                    mapOf("type" to "daily-brief", "briefId" to brief.id)))) {
                    PushResult.InvalidToken -> { safe { store.removeToken(device.token) }; failed++ }
                    is PushResult.Failed, is PushResult.Retryable -> failed++
                    else -> sent++
                }
            }
        }
        return DispatchReport(subscribers.size, sent, skipped, failed, brief.id)
    }
}
