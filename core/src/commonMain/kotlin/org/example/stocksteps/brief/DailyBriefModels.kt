package org.example.stocksteps.brief

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.learning.BeginnerEducation
import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.model.NewsCategory
import kotlin.math.ceil
import kotlin.math.max

/*
 * Daily Market Brief: a short, source-backed, educational summary. Two layers: one global brief per
 * market edition (shared by everyone, cached) and a per-user overlay (watchlist, earnings, plan).
 * Values come only from provider data; nothing is estimated or invented, and missing data stays null.
 */

/** Which moment of the market day an edition describes (US Eastern time). */
@Serializable enum class BriefEdition(val label: String) {
    PRE_MARKET("Pre-market brief"), MARKET_HOURS("Market-hours brief"), AFTER_CLOSE("After-close brief"),
    WEEKEND("Weekend brief"), HOLIDAY("Holiday brief")
}

@Serializable enum class BriefPhase { PRE_MARKET, OPEN, AFTER_HOURS, CLOSED, WEEKEND, HOLIDAY }

@Serializable
data class BriefMarketSession(
    /** "US" or "CA". */
    val market: String,
    val name: String,
    val phase: BriefPhase,
    /** Exchange-local date the phase applies to. */
    val localDate: String,
    /** The latest completed regular session (exchange-local date). */
    val lastCompletedSession: String,
    val holiday: String? = null,
    val timezone: String
) {
    val label: String get() = when (phase) {
        BriefPhase.PRE_MARKET -> "Not open yet"
        BriefPhase.OPEN -> "Open"
        BriefPhase.AFTER_HOURS -> "Closed for the day (after-hours trading)"
        BriefPhase.CLOSED -> "Closed"
        BriefPhase.WEEKEND -> "Closed for the weekend"
        BriefPhase.HOLIDAY -> "Closed for ${holiday ?: "a holiday"}"
    }
}

/** How a quote relates to the session it's shown in. */
@Serializable enum class QuoteState { LIVE_DELAYED, SESSION_CLOSE, PREVIOUS_CLOSE, STALE, UNAVAILABLE }

@Serializable
data class BriefIndex(
    val indexId: String,
    val displayName: String,
    val market: String,
    val value: Double? = null,
    val change: Double? = null,
    val changePercent: Double? = null,
    val currency: String,
    /** "points", or the fund currency when a fund proxy is shown. */
    val unit: String,
    val isProxy: Boolean = false,
    val proxyNote: String? = null,
    val quoteAsOf: String? = null,
    /** Exchange-local date of the quote. */
    val quoteDate: String? = null,
    val state: QuoteState,
    /** "Close · Oct 7", "During the session (delayed) · 11:02 ET", "From an earlier session (Oct 3)". */
    val stateLabel: String
)

@Serializable
data class BriefSource(val id: String, val publisher: String? = null, val url: String, val publishedAt: String? = null, val title: String? = null)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BriefStory(
    val id: String,
    val headline: String,
    /** A short excerpt of the provider's own summary (not the article); null when none was provided. */
    val summary: String? = null,
    /** Null when the provider didn't name one (never guessed). */
    val publisher: String? = null,
    val publishedAt: String? = null,
    val sourceUrl: String,
    @EncodeDefault val relatedSymbols: List<String> = emptyList(),
    val topic: String,
    /** States what the article is and isn't evidence of; never asserts a market cause. */
    val evidenceNote: String,
    @EncodeDefault val sourceIds: List<String> = emptyList()
)

@Serializable
data class ConceptOfTheDay(val id: String, val title: String, val explanation: String, val example: String?, val whyToday: String, val learnTermId: String)

@Serializable enum class BriefStatus { COMPLETE, PARTIAL, UNAVAILABLE }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DailyBrief(
    val id: String,
    /** Publication date (US Eastern). */
    val briefDate: String,
    /** The trading session the market numbers describe. */
    val sessionDate: String,
    val edition: BriefEdition,
    @EncodeDefault val sessions: List<BriefMarketSession> = emptyList(),
    val summaryLine: String,
    @EncodeDefault val marketSnapshot: List<BriefIndex> = emptyList(),
    val indexExplainer: String = BriefContent.INDEX_EXPLAINER,
    @EncodeDefault val stories: List<BriefStory> = emptyList(),
    val concept: ConceptOfTheDay,
    @EncodeDefault val sources: List<BriefSource> = emptyList(),
    val generatedAt: String,
    /** Changes only when the data is refreshed (never re-stamped without new data). */
    val updatedAt: String,
    val status: BriefStatus,
    val contentVersion: Int = BriefContent.VERSION,
    val readingMinutes: Int,
    @EncodeDefault val notes: List<String> = emptyList(),
    val sampleData: Boolean = false,
    val disclaimer: String = BriefContent.DISCLAIMER
) {
    fun summary() = BriefSummary(id, briefDate, sessionDate, edition, summaryLine, generatedAt, readingMinutes, sampleData)
}

@Serializable
data class BriefSummary(val id: String, val briefDate: String, val sessionDate: String, val edition: BriefEdition, val summaryLine: String, val generatedAt: String, val readingMinutes: Int, val sampleData: Boolean = false)

@Serializable enum class BriefAccess { ANONYMOUS, FREE, PLUS }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BriefHistory(
    @EncodeDefault val items: List<BriefSummary> = emptyList(),
    val access: BriefAccess,
    /** Older briefs that exist but aren't included for this plan. */
    val lockedCount: Int = 0,
    val nextCursor: String? = null
)

@Serializable enum class HighlightKind { PRICE_MOVE, NEWS, EARNINGS_UPCOMING, EARNINGS_RESULT }

@Serializable
data class WatchlistHighlight(
    val symbol: String,
    val name: String? = null,
    val kind: HighlightKind,
    val text: String,
    val url: String? = null,
    val publisher: String? = null,
    val at: String? = null
)

@Serializable
data class BriefEarnings(
    val symbol: String,
    val name: String? = null,
    val date: String,
    /** "Before market open", "After market close"; null when the time isn't reliably known. */
    val timing: String? = null,
    /** "Confirmed", "Estimated", "Tentative". */
    val dateStatus: String,
    val epsEstimate: Double? = null,
    val currency: String? = null,
    /** "watchlist", "portfolio" or "general". */
    val reason: String
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PersonalizedBrief(
    val briefId: String,
    val access: BriefAccess,
    val watchlistCount: Int,
    @EncodeDefault val watchlistHighlights: List<WatchlistHighlight> = emptyList(),
    /** Highlights that exist but are included with StockSteps+. */
    val moreHighlights: Int = 0,
    @EncodeDefault val companyStories: List<BriefStory> = emptyList(),
    @EncodeDefault val earnings: List<BriefEarnings> = emptyList(),
    /** Null for Free: the app shows a preview instead. */
    val premiumInsights: List<String>? = null,
    val aiAvailable: Boolean = false,
    @EncodeDefault val notes: List<String> = emptyList(),
    val generatedAt: String
)

@Serializable
data class BriefPreferences(
    val notificationsEnabled: Boolean = false,
    /** Local hour (0–23) in [timeZone] to deliver the brief. */
    val deliveryHour: Int = 8,
    val timeZone: String = "America/Toronto",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val markets: List<String> = listOf("US", "CA"),
    /** StockSteps+: mention followed companies in the notification. */
    val personalizedNotifications: Boolean = false,
    /** Optional quiet hours (local hours); no notification is sent inside them. */
    val quietStartHour: Int? = null,
    val quietEndHour: Int? = null
)

@Serializable data class BriefAiRequest(val storyId: String? = null, val question: String? = null)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BriefAiAnswer(
    val answer: String,
    @EncodeDefault val points: List<String> = emptyList(),
    @EncodeDefault val sources: List<BriefSource> = emptyList(),
    val insufficientEvidence: Boolean = false,
    val remainingToday: Int? = null,
    /** False for MOCK templates, so they're never labelled as AI. */
    val usesAi: Boolean = true
)

// ---------- Policy, content and deterministic rules ----------

/** One place for Free vs StockSteps+ limits (the backend enforces them). */
object BriefPolicy {
    const val FREE_HISTORY = 3
    const val PLUS_HISTORY = 60
    const val FREE_HIGHLIGHTS = 3
    const val PLUS_HIGHLIGHTS = 12
    const val PLUS_COMPANY_STORIES = 4
    const val MAX_STORIES = 3
    fun historyLimit(access: BriefAccess) = if (access == BriefAccess.PLUS) PLUS_HISTORY else FREE_HISTORY
    fun highlightLimit(access: BriefAccess) = if (access == BriefAccess.PLUS) PLUS_HIGHLIGHTS else FREE_HIGHLIGHTS
    fun companyStoryLimit(access: BriefAccess) = if (access == BriefAccess.PLUS) PLUS_COMPANY_STORIES else 0
    fun aiAllowed(access: BriefAccess) = access == BriefAccess.PLUS
}

object BriefContent {
    const val VERSION = 1
    const val INDEX_EXPLAINER = "A market index tracks a group of stocks to show how that part of the market is doing. The S&P 500 follows 500 large US companies, the Nasdaq Composite follows companies listed on the Nasdaq exchange, and the S&P/TSX Composite follows large Canadian companies."
    const val DISCLAIMER = "Educational information, not investment advice. Numbers come from market data providers and may be delayed."
    const val WORDS_PER_MINUTE = 200

    /** Concepts reuse the Learn catalogue (no second education system). */
    private val rotation = listOf("pe", "diversification", "marketCap", "dividendYield", "earningsBeat", "revenueGrowth", "eps", "netMargin")

    fun concept(dayOfYear: Int, hasEarningsStory: Boolean, marketsClosed: Boolean): ConceptOfTheDay {
        val (id, why) = when {
            hasEarningsStory -> "earningsBeat" to "Several companies are in the news for their results, so it helps to know how results are compared with expectations."
            marketsClosed -> "diversification" to "With markets closed, it's a good moment to look at how a portfolio is spread out."
            else -> rotation[dayOfYear % rotation.size] to "One concept a day builds a solid foundation."
        }
        val entry = BeginnerEducation.entry(id) ?: BeginnerEducation.entries.first()
        return ConceptOfTheDay(entry.id, entry.title, "${entry.short} ${entry.why}", entry.example ?: entry.interpret, why, entry.id)
    }

    fun readingMinutes(texts: List<String>): Int = max(1, ceil(texts.sumOf { t -> t.split(Regex("\\s+")).count { it.isNotBlank() } }.toDouble() / WORDS_PER_MINUTE).toInt())
}

/**
 * Transparent story selection: drop invalid records (no https link, no headline), remove near
 * duplicates (same URL or ≥ 60% shared headline words), then rank by market relevance (company link or
 * market vocabulary), recency, watchlist relevance and topic; at most one story per company and per
 * topic while others exist. Off-topic general news is never selected.
 */
object StoryRanker {
    private val stop = setOf("the", "a", "an", "of", "to", "in", "on", "for", "and", "is", "as", "at", "with", "its", "by", "from", "after", "says", "stock", "stocks", "shares")

    fun valid(article: NewsArticle): Boolean = article.title.isNotBlank() && article.url.startsWith("https://") && article.url.length < 2_000 &&
        !article.url.contains(' ') && article.url.substringAfter("https://").substringBefore('/').contains('.')

    fun words(title: String): Set<String> = title.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in stop }.toSet()

    fun similar(a: NewsArticle, b: NewsArticle): Boolean {
        if (a.url.trimEnd('/') == b.url.trimEnd('/')) return true
        val wa = words(a.title); val wb = words(b.title)
        if (wa.isEmpty() || wb.isEmpty()) return false
        return (wa intersect wb).size.toDouble() / minOf(wa.size, wb.size) >= 0.6
    }

    fun dedupe(articles: List<NewsArticle>): List<NewsArticle> = articles.fold(mutableListOf<NewsArticle>()) { kept, a ->
        if (kept.none { similar(it, a) }) kept += a
        kept
    }

    /** Hours since publication (large when unknown). */
    fun ageHours(publishedAt: String?, nowEpochSeconds: Long, parse: (String) -> Long?): Double =
        publishedAt?.let(parse)?.let { (nowEpochSeconds - it).coerceAtLeast(0) / 3600.0 } ?: 96.0

    private val marketTerms = Regex("(?i)\\b(stocks?|shares?|markets?|investors?|earnings|revenue|profit|sales|nasdaq|s&p|tsx|dow|index|indexes|fed|interest rates?|inflation|bonds?|yields?|treasur(y|ies)|oil|dollar|economy|economic|gdp|jobs report|ipo|merger|acquisition|tariffs?|chips?|bank|dividend|guidance|forecast|analysts?)\\b")

    /** True when the story is about companies or markets (a company link or market vocabulary). */
    fun marketRelevant(a: NewsArticle): Boolean = a.symbol != null || marketTerms.containsMatchIn(a.title) || marketTerms.containsMatchIn(a.description.orEmpty())

    fun score(a: NewsArticle, ageHours: Double, watch: Set<String>): Double {
        var s = 100.0 - ageHours.coerceAtMost(96.0)
        if (!marketRelevant(a)) s -= 60
        if (a.symbol != null) s += 10
        if (a.symbol != null && a.symbol.uppercase() in watch) s += 25
        if (a.category == NewsCategory.EARNINGS) s += 8
        if (a.source.isNullOrBlank()) s -= 15
        if (a.description.isNullOrBlank()) s -= 5
        return s
    }

    fun rank(articles: List<NewsArticle>, nowEpochSeconds: Long, parse: (String) -> Long?, watch: Set<String> = emptySet(), limit: Int = BriefPolicy.MAX_STORIES): List<NewsArticle> {
        // Off-topic general news isn't a market story: fewer stories are shown rather than filler.
        val candidates = dedupe(articles.filter { valid(it) && marketRelevant(it) })
            .map { it to score(it, ageHours(it.publishedAt, nowEpochSeconds, parse), watch) }
            .sortedWith(compareByDescending<Pair<NewsArticle, Double>> { it.second }.thenBy { it.first.id ?: it.first.url })
        val picked = mutableListOf<NewsArticle>()
        // First pass keeps companies and topics diverse; the second fills remaining slots.
        for ((a, _) in candidates) {
            if (picked.size >= limit) break
            val sameCompany = a.symbol != null && picked.any { it.symbol.equals(a.symbol, true) }
            val sameTopic = a.category != null && a.category != NewsCategory.OTHER && picked.any { it.category == a.category }
            if (!sameCompany && !sameTopic) picked += a
        }
        for ((a, _) in candidates) {
            if (picked.size >= limit) break
            if (a !in picked && (a.symbol == null || picked.none { it.symbol.equals(a.symbol, true) })) picked += a
        }
        return picked
    }

    fun topic(category: NewsCategory?): String = when (category) {
        NewsCategory.EARNINGS -> "Earnings"; NewsCategory.PRODUCTS -> "Products"; NewsCategory.BUSINESS -> "Business"
        NewsCategory.REGULATION -> "Regulation"; NewsCategory.ANALYST -> "Analysts"; else -> "Markets"
    }

    /** A short excerpt of the provider's summary: up to two sentences and 260 characters. */
    fun excerpt(description: String?): String? {
        val text = description?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.length >= 20 } ?: return null
        val sentences = Regex("(?<=[.!?])\\s+").split(text).take(2).joinToString(" ")
        return if (sentences.length <= 260) sentences else sentences.take(257).substringBeforeLast(' ') + "…"
    }

    const val EVIDENCE_NOTE = "This is what the article reports. On its own it doesn't show why the market or any stock moved."
}

/** Plain, neutral wording for index moves (no causes, no adjectives like "soared"). */
object BriefWording {
    fun pct(v: Double): String { val r = kotlin.math.round(kotlin.math.abs(v) * 100) / 100; val s = r.toString().let { if (it.substringAfter('.', "").length == 1) it + "0" else if (!it.contains('.')) "$it.00" else it }; return s + "%" }
    fun move(name: String, percent: Double?): String? = percent?.let {
        when {
            it > 0.005 -> "$name rose ${pct(it)}"
            it < -0.005 -> "$name fell ${pct(it)}"
            else -> "$name was about unchanged"
        }
    }
}
