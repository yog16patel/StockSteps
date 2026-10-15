package org.example.stocksteps.brief

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.format.ReadableText
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Daily Brief reader copy and display rules (Global UI Refinement Phase 5D.5.1), shared by Compose and SwiftUI. Display only: every number and
 * sentence comes from the brief; nothing is reworded, estimated or given a cause. Section names are sentence case.
 */
@OptIn(ExperimentalTime::class)
object DailyBriefDisplay {
    const val TITLE = "Daily brief"
    const val MARKETS = "Markets"
    const val STORIES = "Market stories"
    const val WATCHLIST = "From your watchlist"
    const val EARNINGS = "Upcoming earnings"
    const val CONCEPT = "Concept of the day"
    const val PLUS = "StockSteps+ insights"
    const val INDEX_EXPLAINER_TITLE = "What is a market index?"
    const val ABOUT_STORIES = "About these stories"
    const val ABOUT_BRIEF = "About this brief"
    const val SAMPLE = "Sample"
    const val SAMPLE_DESCRIPTION = "Sample data"
    const val MORE_OPTIONS = "More options"
    const val NOTIFY = "Notify me"
    const val HISTORY = "Previous briefs"
    const val SCENARIOS = "Scenarios"
    const val SCENARIO_NONE = "Current (no scenario)"
    const val READ_ARTICLE = "Read article"
    const val UNDERSTAND_MORE = "Understand more"
    const val UNDERSTAND_MORE_PLUS = "Understand more · StockSteps+"
    const val UNDERSTAND_CONCEPT = "Understand more"
    const val OPEN_LEARN = "Open Learn"
    const val LOADING = "Loading the Daily Market Brief"
    const val UNAVAILABLE = "The brief isn't available right now."
    const val NO_INDICES = "Index values aren't available right now."
    const val NO_STORIES = "No market stories met our source checks for this brief."
    const val STALE = "This is the latest brief available. It isn't from today."
    const val OFFLINE = "You're offline. This is a copy saved on this device."
    const val CHANGE_UNAVAILABLE = "Change unavailable"
    const val SIGN_IN_PROMPT = "Sign in to see news, price moves and earnings for companies you follow."
    const val SIGN_IN = "Sign in"
    const val WATCHLIST_EMPTY = "Add companies to a watchlist to see their updates in your brief."
    const val GO_TO_WATCHLIST = "Go to Watchlist"
    const val WATCHLIST_UNAVAILABLE = "Your watchlist updates aren't available right now."
    const val EARNINGS_DIGEST = "Your earnings digest"
    const val EARNINGS_DATES_NOTE = "Report dates can change until a company confirms them."
    const val PLUS_PREVIEW = "See how today's market relates to the companies you follow, more company stories, and ask follow-up questions with source-backed AI explanations."
    const val PLUS_PREVIEW_ACTION = "Preview StockSteps+"
    const val PLUS_STORIES = "More from companies you follow"
    const val ASK_LABEL = "Ask about this brief"
    const val ASK = "Ask"
    const val ASK_NOTE = "Answers use only this brief's sources. No predictions or buy/sell advice."
    const val PREPARING = "Preparing an explanation"
    const val INSUFFICIENT = "The sources don't contain enough to answer that fully."
    const val UPGRADE_TITLE = "StockSteps+"
    const val UPGRADE = "AI explanations, the full personalized brief and the complete brief history are part of StockSteps+. The market summary, stories and Concept of the day stay free."
    const val HISTORY_UPGRADE = "The full brief history is part of StockSteps+. The latest three briefs stay free."
    const val SEE_PLUS = "See StockSteps+"
    const val NOT_NOW = "Not now"
    const val NO_HISTORY = "No briefs yet. A new brief is prepared for each market session."

    // ---------- Header ----------

    /** "After-close brief · Oct 7 · 2 min read". */
    fun meta(brief: DailyBrief): String = "${brief.edition.label} · ${BriefFormat.date(brief.briefDate)} · ${brief.readingMinutes} min read"

    /** "Updated 30 min ago" (relative, no time zone guessed); "Updated Oct 7" once it's older than 20 hours, as Home's freshness. */
    fun updated(brief: DailyBrief, nowMillis: Long): String = "Updated ${ago(brief.updatedAt, nowMillis) ?: BriefFormat.date(brief.briefDate)}"

    /** "Updated 30 min ago · Market data for the Oct 7 session". Offline is said once, by the [OFFLINE] banner (device check 5D.5.1). */
    fun freshness(brief: DailyBrief, nowMillis: Long): String =
        "${updated(brief, nowMillis)} · Market data for the ${BriefFormat.date(brief.sessionDate)} session"

    /** "US stocks: Closed for the day (after-hours trading)" — one line per market (Home uses the same wording). */
    fun sessionLine(session: BriefMarketSession): String = "${sessionName(session)}: ${session.label}"
    fun sessionLines(brief: DailyBrief): List<String> = brief.sessions.map(::sessionLine)
    private fun sessionName(session: BriefMarketSession): String = when (session.market) {
        "US" -> "US stocks"
        "CA" -> "Canadian stocks"
        else -> session.name
    }

    /** Header banners, in order: stale first, then offline. */
    fun banners(brief: DailyBrief, nowMillis: Long, offline: Boolean): List<String> =
        listOfNotNull(STALE.takeIf { BriefFormat.isStale(brief, nowMillis) }, OFFLINE.takeIf { offline })

    /** D-5: Home shows the summary's first sentence under the brief entry (the reader has the full text). */
    fun homeSummary(brief: DailyBrief): String? = brief.summaryLine.trim().takeIf { it.isNotEmpty() }?.let { ReadableText.firstSentence(it).first }

    // ---------- Index rows ----------

    /** "−18.59 (−0.24%)", or null when the brief has no change (the row then shows the quote state only). */
    fun indexChange(index: BriefIndex): String? = if (index.value == null || index.change == null) null else BriefFormat.change(index)

    fun indexDirection(index: BriefIndex): PriceDirection = when {
        index.value == null || index.changePercent == null -> PriceDirection.UNAVAILABLE
        else -> when (BriefFormat.sign(index)) { 1 -> PriceDirection.UP; -1 -> PriceDirection.DOWN; else -> PriceDirection.UNCHANGED }
    }

    /** Stale or unavailable quotes are labelled in the caution colour, never shown as current. */
    fun indexCaution(index: BriefIndex): Boolean = index.state == QuoteState.STALE || index.state == QuoteState.UNAVAILABLE

    // ---------- Stories ----------

    /** "Earnings · Reuters · 2 hr ago"; a missing publisher is said, never guessed. */
    fun storyMeta(story: BriefStory, nowMillis: Long): String =
        listOfNotNull(story.topic, story.publisher ?: "Publisher not provided", story.publishedAt?.let { published(it, nowMillis) }).joinToString(" · ")

    /**
     * The provider excerpt to show, or null when it only repeats the headline (some providers send the title as the summary, e.g. "… to come
     * Reuters" under "… to come - Reuters"). Compared on letters and digits only; the text shown is never changed.
     */
    fun excerpt(story: BriefStory): String? {
        val summary = story.summary ?: return null
        fun norm(t: String) = t.lowercase().filter { it.isLetterOrDigit() }
        return summary.takeUnless { norm(it).isEmpty() || norm(it) == norm(story.headline) }
    }

    /** "Read the original article from Reuters". */
    fun readArticleLabel(story: BriefStory): String = "Read the original article from ${story.publisher ?: "the publisher"}"

    fun companyAction(symbol: String): String = "View $symbol"

    /** Each story's evidence note once (they are the same sentence), so it isn't repeated under every story. */
    fun storyNotes(brief: DailyBrief): List<String> = brief.stories.map { it.evidenceNote }.distinct()

    /** Server notes (including the sample sentence) and the disclaimer, each once. */
    fun aboutLines(brief: DailyBrief): List<String> = (brief.notes + brief.disclaimer).distinct()

    fun understandLabel(plus: Boolean): String = if (plus) UNDERSTAND_MORE else UNDERSTAND_MORE_PLUS

    fun aiLabel(answer: BriefAiAnswer): String = if (answer.usesAi) "AI explanation (StockSteps+)" else "Sample explanation (mock)"
    fun aiSource(source: BriefSource): String = "Source: ${source.publisher ?: source.title ?: "article"}"
    fun aiRemaining(left: Int): String = "$left explanations left today"

    // ---------- Personal sections ----------

    fun highlightTitle(h: WatchlistHighlight): String = h.symbol + (h.name?.let { " · $it" } ?: "")
    fun highlightAction(h: WatchlistHighlight): String = when {
        h.url != null -> "Read the article"
        h.reportId != null -> "View ${h.symbol} earnings results"
        else -> "Open ${h.symbol}"
    }
    fun nothingNotable(count: Int): String = "Nothing notable from the $count ${if (count == 1) "company" else "companies"} you follow in this session."
    fun moreHighlights(count: Int): String = "$count more ${if (count == 1) "update is" else "updates are"} included with StockSteps+."

    fun earningsTitle(e: BriefEarnings): String = "${e.name ?: e.symbol} (${e.symbol})"
    /** "Expected Oct 30 · Confirmed date · After market close · EPS estimate USD 1.23 · On your watchlist"; no currency symbol is invented. */
    fun earningsDetail(e: BriefEarnings): String = listOfNotNull(
        "Expected ${BriefFormat.date(e.date)}", "${e.dateStatus} date", e.timing,
        e.epsEstimate?.let { listOfNotNull("EPS estimate", e.currency, BriefFormat.groupDigits(it)).joinToString(" ") },
        when (e.reason) { "portfolio" -> "In your portfolio"; "watchlist" -> "On your watchlist"; else -> "General example" }
    ).joinToString(" · ")
    fun earningsAction(e: BriefEarnings): String = if (e.eventId != null) "Open ${e.symbol} earnings event" else "Open ${e.symbol}"

    fun conceptExample(example: String): String = "Example: $example"

    // ---------- History ----------

    /** The summary's first sentence for a history row (shown in full, never cut mid-number). */
    fun historySummary(s: BriefSummary): String = ReadableText.firstSentence(s.summaryLine.trim()).first

    fun historyTitle(s: BriefSummary): String = "${s.edition.label} · ${BriefFormat.date(s.briefDate)}"
    fun historyDetail(s: BriefSummary): String = "${s.readingMinutes} min read"
    fun lockedHistory(count: Int): String = "$count older ${if (count == 1) "brief is" else "briefs are"} included with StockSteps+."

    // ---------- Time ----------

    /** "2 hr ago" within a day; otherwise the date ("Oct 6"). Instants only; unparseable values fall back to the date part. */
    fun published(iso: String, nowMillis: Long): String = ago(iso, nowMillis) ?: BriefFormat.date(iso)

    /** "just now", "12 min ago", "3 hr ago"; null when unparseable, in the future by more than a minute, or older than 20 hours. */
    internal fun ago(iso: String, nowMillis: Long): String? {
        val at = runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull() ?: return null
        val minutes = (nowMillis - at) / 60_000
        return when {
            minutes < -1 || minutes > 20 * 60 -> null
            minutes < 2 -> "just now"
            minutes < 60 -> "$minutes min ago"
            else -> "${minutes / 60} hr ago"
        }
    }
}
