package org.example.stocksteps.earnings

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.EarningsDateStatus
import org.example.stocksteps.model.EarningsTime
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.analytics.EntitlementStatus

// Earnings Intelligence Lite, Phase 5: StockSteps+ Premium Earnings Intelligence. AI explanations and
// questions grounded in the verified Phase 1–3 data, expanded history with deterministic observations,
// and a personalized watchlist digest. The server enforces StockSteps+, quotas and validation; these
// models and deterministic rules are shared so Android, iOS and the server agree. Never advice.

// ---------- AI usage (server-enforced fair use) ----------

@Serializable enum class EarningsAiCategory(val label: String, val unit: String) {
    EXPLANATION("AI earnings explanations", "explanations"),
    QUESTION("AI follow-up questions", "questions"),
    DIGEST("AI digest summaries", "digest summaries")
}

/** One category's allowance. [resetAt] is the real reset instant (UTC), never a guess. */
@Serializable
data class EarningsAiQuota(val category: EarningsAiCategory, val limit: Int, val used: Int, val remaining: Int, val resetAt: String? = null)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsAiUsage(
    val plus: Boolean,
    val entitlementStatus: EntitlementStatus = EntitlementStatus.NONE,
    @EncodeDefault val quotas: List<EarningsAiQuota> = emptyList(),
    val asOf: String,
    /** "AI is temporarily unavailable", etc. Null when nothing needs saying. */
    val note: String? = null
) {
    fun quota(category: EarningsAiCategory): EarningsAiQuota? = quotas.firstOrNull { it.category == category }
}

// ---------- Citations and structured AI output ----------

@Serializable enum class CitationKind { RESULTS, ESTIMATES, PRICES, HISTORY, LESSON }

/**
 * A structured source reference. [url] is set only when a real, licensable link exists (never invented);
 * an AI explanation is never itself a source.
 */
@Serializable
data class EarningsCitation(
    val sourceId: String,
    val kind: CitationKind,
    val label: String,
    val provider: String,
    val fiscalPeriod: String? = null,
    val publishedAt: String? = null,
    val retrievedAt: String? = null,
    val url: String? = null
)

/** One explanation paragraph and the source ids its facts come from. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ExplainedClaim(val text: String, @EncodeDefault val sourceIds: List<String> = emptyList())

@Serializable data class KeyTerm(val term: String, val meaning: String)

/**
 * "Understand These Earnings": a validated, source-grounded explanation of one report. Plain text only
 * (never HTML). [sourceDataVersion] identifies the exact verified data it was built from; a revision of
 * that data makes it stale.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsAiExplanation(
    val explanationId: String,
    val reportId: String,
    val instrumentId: String,
    val companyName: String,
    val period: String,
    val generatedAt: String,
    val sourceDataVersion: String,
    val promptVersion: String,
    val model: String,
    /** 1. What the company reported. */
    val summary: ExplainedClaim,
    /** 2. What exceeded or missed expectations (EPS). */
    val epsExplanation: ExplainedClaim? = null,
    /** 2. What exceeded or missed expectations (revenue). */
    val revenueExplanation: ExplainedClaim? = null,
    /** 3. Revenue compared with previous periods. */
    val growthExplanation: ExplainedClaim? = null,
    /** 4. What the stock did after earnings (never why). */
    val priceReactionExplanation: ExplainedClaim? = null,
    /** 5. What remains uncertain. */
    @EncodeDefault val uncertaintyNotes: List<String> = emptyList(),
    /** 6. Key terms explained. */
    @EncodeDefault val keyTerms: List<KeyTerm> = emptyList(),
    /** 7. What a beginner can learn. */
    @EncodeDefault val beginnerTakeaways: List<String> = emptyList(),
    @EncodeDefault val citations: List<EarningsCitation> = emptyList(),
    val disclaimer: String,
    @EncodeDefault val dataWarnings: List<String> = emptyList(),
    /** Served from the shared cache (no AI call, no quota used). */
    @EncodeDefault val cached: Boolean = false,
    /** MOCK: a deterministic template, labelled "Sample". */
    @EncodeDefault val sample: Boolean = false,
    val usage: EarningsAiUsage? = null
) {
    val wordCount: Int get() = (listOfNotNull(summary, epsExplanation, revenueExplanation, growthExplanation, priceReactionExplanation).map { it.text } +
        uncertaintyNotes + beginnerTakeaways + keyTerms.map { "${it.term} ${it.meaning}" }).sumOf { t -> t.split(' ').count { it.isNotBlank() } }
}

@Serializable data class EarningsAiExplainRequest(val refresh: Boolean = false)

/** A follow-up question about one report. [previousContextId] is the sourceDataVersion the client last saw. */
@Serializable data class EarningsAiQuestion(val question: String, val conversationId: String? = null, val previousContextId: String? = null,
    /** Optional client retry key (8–64 of A–Z, a–z, 0–9, '-', '_'): a retry with the same key isn't charged twice (Phase 4B). */
    val idempotencyKey: String? = null)

@Serializable enum class AnswerScope {
    /** Answered from the report's verified data. */ REPORT,
    /** A general concept answered from approved Learn material. */ EDUCATION,
    /** Advice, predictions or off-topic: declined without calling the AI service. */ UNSUPPORTED,
    /** On topic, but the verified data can't answer it (e.g. "why"). */ INSUFFICIENT_DATA
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsAiAnswer(
    val answerId: String,
    val conversationId: String,
    val reportId: String,
    val question: String,
    val answer: String,
    @EncodeDefault val points: List<String> = emptyList(),
    @EncodeDefault val citations: List<EarningsCitation> = emptyList(),
    val generatedAt: String,
    @EncodeDefault val scope: AnswerScope = AnswerScope.REPORT,
    /** The sourceDataVersion this answer used. */
    val contextId: String,
    /** True when the server started a fresh conversation (report revised, or a different report). */
    @EncodeDefault val contextReset: Boolean = false,
    val note: String? = null,
    val usage: EarningsAiUsage? = null,
    val sample: Boolean = false
)

@Serializable data class SuggestedQuestion(val id: String, val text: String)

@Serializable enum class ExplanationAvailability { NONE, CURRENT, STALE }

/**
 * Everything the Earnings Results premium section needs without calling an AI service: the verified
 * plan, a deterministic preview, quotas, question chips and whether a current explanation is cached.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PremiumEarningsOverview(
    val reportId: String,
    val plus: Boolean,
    val entitlementStatus: EntitlementStatus = EntitlementStatus.NONE,
    /** Deterministic preview of what the explanation covers (built from free data). */
    val preview: String,
    val explanation: ExplanationAvailability = ExplanationAvailability.NONE,
    val explanationGeneratedAt: String? = null,
    @EncodeDefault val suggestedQuestions: List<SuggestedQuestion> = emptyList(),
    val usage: EarningsAiUsage,
    /** Reported quarters available for the expanded history (never padded). */
    val historyQuarters: Int = 0,
    val aiAvailable: Boolean = true,
    @EncodeDefault val notes: List<String> = emptyList(),
    val sampleData: Boolean = false
)

// ---------- Expanded history (StockSteps+) ----------

/** One fiscal quarter. [missing] rows are gaps the source has no results for (never filled in). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HistoricalQuarter(
    val reportId: String,
    val fiscalYear: Int,
    val fiscalQuarter: Int,
    val periodEnd: String? = null,
    val reportDate: String? = null,
    val missing: Boolean = false,
    /** Exact decimal strings; null when the source doesn't provide them. */
    val revenue: String? = null,
    val eps: String? = null,
    val epsBasis: EpsBasis = EpsBasis.UNKNOWN,
    val currency: String? = null,
    val revenueYoYPercent: String? = null,
    val epsComparison: Classification = Classification.UNAVAILABLE,
    val revenueComparison: Classification = Classification.UNAVAILABLE,
    /** False when this quarter can't sit on the same chart (other currency or EPS measure). */
    val revenueComparable: Boolean = true,
    val epsComparable: Boolean = true,
    /** The fiscal calendar changed before this quarter: the chart doesn't connect across it. */
    val breakBefore: Boolean = false,
    val revised: Boolean = false,
    @EncodeDefault val notes: List<String> = emptyList()
) { val label: String get() = "Q$fiscalQuarter FY$fiscalYear" }

@Serializable enum class ObservationKind { REVENUE_GROWTH, REVENUE_DECLINE, EPS_ESTIMATES, INSUFFICIENT }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HistoricalObservation(val kind: ObservationKind, val text: String, @EncodeDefault val supportingReportIds: List<String> = emptyList())

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HistoricalEarningsInsight(
    val instrumentId: String,
    val symbol: String,
    val companyName: String,
    /** The series currency (the latest quarter's); quarters in another currency aren't plotted. */
    val currency: String? = null,
    val epsBasis: EpsBasis = EpsBasis.UNKNOWN,
    /** Oldest → newest, including gap rows. */
    @EncodeDefault val quarters: List<HistoricalQuarter> = emptyList(),
    @EncodeDefault val fiscalPeriods: List<String> = emptyList(),
    /** Aligned with [fiscalPeriods]; null = gap (missing or not comparable), never zero. */
    @EncodeDefault val revenueSeries: List<String?> = emptyList(),
    @EncodeDefault val epsSeries: List<String?> = emptyList(),
    @EncodeDefault val deterministicObservations: List<HistoricalObservation> = emptyList(),
    @EncodeDefault val dataWarnings: List<String> = emptyList(),
    /** Reported quarters in this page. */
    val availableQuarters: Int = 0,
    val requestedQuarters: Int = HistoricalEarningsEngine.MAX_QUARTERS,
    /** Older quarters: pass as `cursor` to continue. */
    val nextCursor: String? = null,
    @EncodeDefault val sources: List<String> = emptyList(),
    val disclaimer: String = HistoricalEarningsEngine.DISCLAIMER,
    val asOf: String,
    val sampleData: Boolean = false
)

/**
 * Deterministic expanded history. Reuses the Phase 2 rules (exact decimal classifications, the same
 * year-over-year comparability checks) for every quarter, then validates the series as a whole:
 * currency, EPS measure, fiscal-calendar changes, gaps and revisions. Observations only state what
 * the data shows and never suggest a decision.
 */
object HistoricalEarningsEngine {
    const val MAX_QUARTERS = 8
    /** Comparable points needed before describing a pattern. */
    const val TREND_MIN = 3
    const val TREND_WINDOW = 4
    const val DISCLAIMER = "Past results don't predict future results or share prices. A pattern over a few quarters isn't a reason to buy or sell."
    const val SPLIT_NOTE = "EPS is shown as reported. StockSteps doesn't have stock-split data, so EPS from before a split may not be directly comparable."

    fun index(fiscalYear: Int, fiscalQuarter: Int) = fiscalYear * 4 + fiscalQuarter - 1

    private fun reported(e: EarningsEvent) = e.actual?.let { it.eps != null || it.revenue != null } == true

    private fun days(a: String?, b: String?): Int? {
        val x = org.example.stocksteps.markets.MarketsPresenter.dayNumber(a ?: return null) ?: return null
        val y = org.example.stocksteps.markets.MarketsPresenter.dayNumber(b ?: return null) ?: return null
        return y - x
    }

    /** Reported quarters available for [events] (what the overview advertises; never padded). */
    fun available(events: List<EarningsEvent>): Int = events.filter(::reported).distinctBy { it.id }.size

    /**
     * Builds one page of up to [limit] reported quarters, newest first, starting after [cursor] (a
     * report id from a previous page). Throws [IllegalArgumentException] for an unknown cursor.
     */
    fun build(events: List<EarningsEvent>, limit: Int = MAX_QUARTERS, cursor: String? = null, asOf: String, sampleData: Boolean = false): HistoricalEarningsInsight {
        require(limit in 1..MAX_QUARTERS) { "limit" }
        val all = events.filter(::reported).distinctBy { it.id }.sortedByDescending { index(it.fiscalYear, it.fiscalQuarter) }
        val start = cursor?.let { c -> all.indexOfFirst { it.id.equals(c, ignoreCase = true) }.takeIf { it >= 0 }?.plus(1) ?: throw IllegalArgumentException("cursor") } ?: 0
        val page = all.drop(start).take(limit)
        val more = all.size > start + page.size
        val first = page.firstOrNull() ?: events.firstOrNull()
        if (page.isEmpty()) return HistoricalEarningsInsight(first?.symbol ?: "", first?.symbol ?: "", first?.name ?: "",
            deterministicObservations = listOf(HistoricalObservation(ObservationKind.INSUFFICIENT, "No reported results are available from the data source.")),
            requestedQuarters = limit, asOf = asOf, sampleData = sampleData)
        // Reports are mapped against the full history, so year-over-year can use quarters outside the page.
        val reports = page.map { EarningsReportMapper.report(it, events, null)!! }
        val latest = reports.first()
        val currency = latest.reportingCurrency
        val basis = latest.epsActualBasis
        val warnings = LinkedHashSet<String>()
        val rows = ArrayList<HistoricalQuarter>()
        var previous: EarningsReport? = null
        for (r in reports.reversed()) {
            previous?.let { p ->
                val gap = index(r.fiscalYear, r.fiscalQuarter) - index(p.fiscalYear, p.fiscalQuarter) - 1
                if (gap in 1..limit) {
                    warnings += "Some quarters are missing from the data source. Gaps are left empty, never filled in."
                    var i = index(p.fiscalYear, p.fiscalQuarter) + 1
                    repeat(gap) {
                        val y = i / 4; val q = i % 4 + 1
                        rows += HistoricalQuarter(EarningsReportMapper.reportId(r.symbol, y, q), y, q, missing = true, notes = listOf("No results for this quarter from the data source."))
                        i++
                    }
                }
            }
            val yoy = EarningsResultsCalculator.yearOverYear(r)
            val sameCurrency = r.reportingCurrency == currency
            val sameBasis = r.epsActualBasis == basis
            val consecutive = previous?.let { p -> index(r.fiscalYear, r.fiscalQuarter) - index(p.fiscalYear, p.fiscalQuarter) == 1 } == true
            val spacing = if (consecutive) days(previous?.fiscalPeriodEnd, r.fiscalPeriodEnd) else null
            val calendarChange = spacing != null && spacing !in 80..100
            val notes = buildList {
                if (!sameCurrency) add("Reported in ${r.reportingCurrency ?: "an unstated currency"}, unlike the latest quarter (${currency ?: "unstated"}), so it isn't plotted with the others.")
                if (!sameBasis && r.epsActual != null) add("EPS measured as ${r.epsActualBasis.label}, unlike the latest quarter (${basis.label}), so it isn't plotted with the others.")
                if (r.revised) add("Revised by the source after first publication.")
                if (calendarChange) add("The company's fiscal calendar changed before this quarter.")
                yoy.reason?.let { add("Year over year: $it") }
            }
            if (!sameCurrency) warnings += "Not every quarter is reported in the same currency. Quarters in another currency aren't plotted or compared."
            if (!sameBasis && r.epsActual != null) warnings += "EPS isn't measured the same way in every quarter. Quarters with a different EPS measure aren't plotted."
            if (calendarChange) warnings += "The fiscal calendar changed during this period, so the chart doesn't connect across the change."
            if (r.revised) warnings += "The data source revised some figures after first publication."
            rows += HistoricalQuarter(r.reportId, r.fiscalYear, r.fiscalQuarter, r.fiscalPeriodEnd, r.reportDate, false, r.revenueActual, r.epsActual, r.epsActualBasis,
                r.reportingCurrency, yoy.percent.takeIf { sameCurrency }, EarningsResultsCalculator.eps(r).classification, EarningsResultsCalculator.revenue(r).classification,
                sameCurrency, sameBasis, calendarChange, r.revised, notes)
            previous = r
        }
        if (rows.any { it.eps != null }) warnings += SPLIT_NOTE
        if (!more && page.size < limit) warnings += "Only ${page.size} reported quarter${if (page.size == 1) " is" else "s are"} available from the data source."
        val real = rows.filter { !it.missing }
        return HistoricalEarningsInsight(
            latest.instrumentId, latest.symbol, latest.companyName, currency, basis, rows, rows.map { it.label },
            rows.map { q -> q.revenue.takeIf { !q.missing && q.revenueComparable } }, rows.map { q -> q.eps.takeIf { !q.missing && q.epsComparable } },
            observations(real.reversed()), warnings.toList(), page.size, limit, page.last().id.takeIf { more },
            latest.sources.map { it.provider }.distinct().map { "Results and estimates: $it" }, DISCLAIMER, asOf, sampleData
        )
    }

    private fun Decimal.isPositive() = this > Decimal.ZERO
    private fun Decimal.isNegative() = this < Decimal.ZERO

    /** [newest] is newest first and contains only real (non-gap) quarters. */
    fun observations(newest: List<HistoricalQuarter>): List<HistoricalObservation> {
        val out = ArrayList<HistoricalObservation>()
        val yoy = newest.filter { it.revenueComparable && it.revenueYoYPercent != null }.take(TREND_WINDOW)
        val eps = newest.filter { it.epsComparison != Classification.UNAVAILABLE }.take(TREND_WINDOW)
        if (yoy.size >= TREND_MIN) {
            val ups = yoy.count { EarningsMath.decimal(it.revenueYoYPercent)!!.isPositive() }
            val text = when (ups) {
                yoy.size -> "Revenue increased year over year in each of the last ${yoy.size} comparable quarters."
                0 -> "Revenue didn't increase year over year in any of the last ${yoy.size} comparable quarters."
                else -> "Revenue increased year over year in $ups of the last ${yoy.size} comparable quarters."
            }
            out += HistoricalObservation(ObservationKind.REVENUE_GROWTH, text, yoy.map { it.reportId })
        }
        // Two consecutive fiscal quarters, both with a year-over-year decline (the latest two comparable ones).
        if (yoy.size >= 2) {
            val (a, b) = yoy[0] to yoy[1]
            if (index(a.fiscalYear, a.fiscalQuarter) - index(b.fiscalYear, b.fiscalQuarter) == 1 &&
                EarningsMath.decimal(a.revenueYoYPercent)!!.isNegative() && EarningsMath.decimal(b.revenueYoYPercent)!!.isNegative())
                out += HistoricalObservation(ObservationKind.REVENUE_DECLINE, "Revenue declined year over year in two consecutive comparable quarters (${b.label} and ${a.label}).", listOf(b.reportId, a.reportId))
        }
        if (eps.size >= TREND_MIN) {
            val beats = eps.count { it.epsComparison == Classification.BEAT }
            val text = when (beats) {
                eps.size -> "EPS exceeded comparable consensus estimates in each of the last ${eps.size} quarters."
                0 -> "EPS didn't exceed comparable consensus estimates in any of the last ${eps.size} quarters."
                else -> "EPS exceeded comparable consensus estimates in $beats of the last ${eps.size} quarters."
            }
            out += HistoricalObservation(ObservationKind.EPS_ESTIMATES, text, eps.map { it.reportId })
        }
        when {
            yoy.size < TREND_MIN && eps.size < TREND_MIN -> out += HistoricalObservation(ObservationKind.INSUFFICIENT, "The available results are insufficient to establish a trend.")
            yoy.size < TREND_MIN -> out += HistoricalObservation(ObservationKind.INSUFFICIENT, "There aren't enough comparable quarters to describe a revenue growth pattern.")
            eps.size < TREND_MIN -> out += HistoricalObservation(ObservationKind.INSUFFICIENT, "There aren't enough comparable estimates to describe how EPS compared with expectations.")
        }
        return out
    }
}

// ---------- Suggested questions (deterministic) ----------

/** Question chips chosen only from facts that exist, so no chip assumes missing data. */
object EarningsQuestionChips {
    const val MAX = 6

    fun forReport(insights: EarningsResultInsights, reactionPercent: String?): List<SuggestedQuestion> {
        val eps = insights.eps.classification
        val revenue = insights.revenue.classification
        val move = EarningsMath.decimal(reactionPercent)
        val yoy = EarningsMath.decimal(insights.yearOverYear.percent)
        val noEstimates = insights.eps.estimate == null && insights.revenue.estimate == null
        val out = buildList {
            if (eps == Classification.BEAT && move != null && move < Decimal.ZERO) add(SuggestedQuestion("fall-after-beat", "Why can a stock fall after beating EPS estimates?"))
            if (eps == Classification.MISS && move != null && move > Decimal.ZERO) add(SuggestedQuestion("rise-after-miss", "Why can a stock rise after missing EPS estimates?"))
            if (revenue == Classification.MISS) add(SuggestedQuestion("revenue-miss", "What does a revenue miss mean?"))
            if (eps == Classification.MISS) add(SuggestedQuestion("eps-miss", "What does an EPS miss mean?"))
            if (eps == Classification.BEAT && revenue == Classification.MISS || eps == Classification.MISS && revenue == Classification.BEAT)
                add(SuggestedQuestion("mixed", "Why can EPS and revenue tell different stories?"))
            if (noEstimates) add(SuggestedQuestion("no-estimates", "How can I understand earnings without analyst estimates?"))
            if (yoy != null) add(SuggestedQuestion("yoy", "Was revenue higher than last year?"))
            if (yoy != null && yoy < Decimal.ZERO) add(SuggestedQuestion("growth-slow", "Why might revenue fall compared with a year ago?"))
            if (move != null) add(SuggestedQuestion("reaction", "What does the price reaction measure?"))
            add(SuggestedQuestion("revenue-profit", "What is the difference between revenue and profit?"))
            if (!noEstimates) add(SuggestedQuestion("expectations", "Why are analyst expectations important?"))
            add(SuggestedQuestion("learn", "What should I learn from this report?"))
        }
        return out.distinctBy { it.id }.take(MAX)
    }
}

// ---------- Personalized earnings digest (StockSteps+) ----------

@Serializable enum class DigestCadence(val label: String) { NONE("No digest"), WEEKLY("Weekly digest") }

/** Opt-in (default off). Delivery time, zone and quiet hours come from the Earnings Reminders preferences. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsDigestPreferences(
    val cadence: DigestCadence = DigestCadence.NONE,
    /** "MONDAY" … "SUNDAY", in the user's time zone. */
    val dayOfWeek: String = DigestPolicy.DEFAULT_DAY,
    val includeUpcoming: Boolean = true,
    /** Learning interests (keys of [DigestPolicy.INTERESTS]) used to pick lessons. */
    @EncodeDefault val interests: List<String> = emptyList()
)

object DigestPolicy {
    val DAYS = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
    const val DEFAULT_DAY = "SATURDAY"
    /** Learning interests a user can pick: key → label. */
    val INTERESTS = listOf("eps" to "Earnings per share", "revenue" to "Revenue and sales", "growth" to "Growth over time",
        "reaction" to "Price reactions", "estimates" to "Analyst expectations")
    const val WINDOW_DAYS = 7
    const val MAX_HISTORY = 12
    const val MAX_ITEMS = 25
    fun dayLabel(day: String) = day.lowercase().replaceFirstChar { it.uppercase() }
}

@Serializable
data class DigestReportedItem(
    val reportId: String,
    val symbol: String,
    val companyName: String,
    val reportDate: String,
    /** "EPS beat, revenue miss". */
    val headline: String,
    val epsClassification: Classification = Classification.UNAVAILABLE,
    val revenueClassification: Classification = Classification.UNAVAILABLE,
    /** "+2.4% over the first session after the report", or null when unavailable. */
    val reactionText: String? = null,
    val dataNote: String? = null
)

@Serializable
data class DigestUpcomingItem(
    val eventId: String,
    val symbol: String,
    val companyName: String,
    val date: String,
    val session: EarningsTime = EarningsTime.UNKNOWN,
    val dateStatus: EarningsDateStatus = EarningsDateStatus.ESTIMATED,
    val whenText: String
)

@Serializable data class DigestLesson(val key: String, val title: String, val body: String, val reason: String)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DigestAiSummary(val text: String, @EncodeDefault val points: List<String> = emptyList(), @EncodeDefault val sourceIds: List<String> = emptyList(),
    val generatedAt: String, val sample: Boolean = false)

/** "What happened with the companies I'm interested in?" Watching a company doesn't mean owning it. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PersonalizedEarningsDigest(
    val digestId: String,
    val periodStart: String,
    val periodEnd: String,
    val watchlistCompanies: Int = 0,
    @EncodeDefault val recentlyReported: List<DigestReportedItem> = emptyList(),
    @EncodeDefault val upcomingEvents: List<DigestUpcomingItem> = emptyList(),
    @EncodeDefault val educationalHighlights: List<DigestLesson> = emptyList(),
    @EncodeDefault val dataNotes: List<String> = emptyList(),
    val empty: Boolean = false,
    val emptyReason: String? = null,
    val generatedAt: String,
    val sourceVersion: String,
    val aiSummary: DigestAiSummary? = null,
    val usage: EarningsAiUsage? = null,
    val sampleData: Boolean = false
)

@Serializable
data class DigestHistoryEntry(val digestId: String, val periodStart: String, val periodEnd: String, val generatedAt: String,
    val reportedCount: Int, val upcomingCount: Int, val notified: Boolean = false)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsDigestHistory(@EncodeDefault val entries: List<DigestHistoryEntry> = emptyList(), val asOf: String)

/** Digest settings as the server sees them (plan verified there; delivery pauses without StockSteps+). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsDigestSettings(
    val preferences: EarningsDigestPreferences = EarningsDigestPreferences(),
    @EncodeDefault val plus: Boolean = false,
    @EncodeDefault val entitlementStatus: EntitlementStatus = EntitlementStatus.NONE,
    /** True only when every gate allows delivery (plan, cadence, app-wide earnings notifications). */
    @EncodeDefault val deliveryActive: Boolean = false,
    val statusMessage: String,
    val deliveryTime: String = ReminderPolicy.DEFAULT_DELIVERY_TIME,
    val timeZone: String = "America/Toronto",
    val quietHours: String? = null,
    val notificationsEnabled: Boolean = true,
    val nextDeliveryText: String? = null,
    val usage: EarningsAiUsage? = null,
    val sampleData: Boolean = false
)

/** Deterministic digest wording and lesson choice (shared so the server and tests agree). */
object EarningsDigestRules {
    fun headline(eps: Classification, revenue: Classification): String {
        val b = Classification.BEAT; val m = Classification.MISS; val u = Classification.UNAVAILABLE
        fun word(c: Classification) = when (c) { Classification.BEAT -> "beat"; Classification.MISS -> "miss"; Classification.MET -> "met estimates"; Classification.UNAVAILABLE -> "not comparable" }
        return when {
            eps == b && revenue == b -> "EPS and revenue above expectations"
            eps == m && revenue == m -> "EPS and revenue below expectations"
            eps == u && revenue == u -> "Results published; no comparable estimates"
            eps == u -> "Revenue ${word(revenue)}; EPS not comparable"
            revenue == u -> "EPS ${word(eps)}; revenue not comparable"
            else -> "EPS ${word(eps)}, revenue ${word(revenue)}"
        }
    }

    private fun lesson(key: String, reason: String): DigestLesson? {
        val (title, body) = when (key) {
            "mixed" -> "Why EPS and revenue can tell different stories" to "Revenue is money from sales; EPS is profit per share after expenses. A company can sell more than expected while costs rise faster, or cut costs while sales fall short."
            "fall-after-beat" -> EarningsEducation.topic("fall-after-beat")?.let { it.title to it.body } ?: return null
            "estimates" -> EarningsEducation.topic("estimates")?.let { it.title to it.body } ?: return null
            "reaction" -> EarningsEducation.topic("reaction")?.let { it.title to it.body } ?: return null
            "yoy" -> EarningsEducation.topic("yoy")?.let { it.title to it.body } ?: return null
            "eps" -> EarningsEducation.topic("eps")?.let { it.title to it.body } ?: return null
            "revenue" -> EarningsEducation.topic("revenue")?.let { it.title to it.body } ?: return null
            "quarterly" -> EarningsEducation.topic("quarterly")?.let { it.title to it.body } ?: return null
            else -> return null
        }
        return DigestLesson(key, title, body, reason)
    }

    /** Up to three lessons: what this week's results illustrate first, then the user's interests. */
    fun lessons(reported: List<DigestReportedItem>, upcoming: List<DigestUpcomingItem>, interests: List<String>, fellAfterBeat: Boolean): List<DigestLesson> {
        val picks = ArrayList<DigestLesson?>()
        if (reported.any { (it.epsClassification == Classification.BEAT && it.revenueClassification == Classification.MISS) ||
                (it.epsClassification == Classification.MISS && it.revenueClassification == Classification.BEAT) })
            picks += lesson("mixed", "A company you follow had EPS and revenue results that pointed in different directions.")
        if (fellAfterBeat) picks += lesson("fall-after-beat", "A company you follow beat EPS estimates and its price fell over the first session.")
        if (reported.any { it.epsClassification == Classification.UNAVAILABLE && it.revenueClassification == Classification.UNAVAILABLE })
            picks += lesson("estimates", "Some results this week had no comparable analyst estimates.")
        if (upcoming.isNotEmpty() && reported.isEmpty()) picks += lesson("quarterly", "Companies you follow are expected to report soon.")
        val interestLessons = mapOf("eps" to "eps", "revenue" to "revenue", "growth" to "yoy", "reaction" to "reaction", "estimates" to "estimates")
        interests.mapNotNull { interestLessons[it] }.forEach { picks += lesson(it, "You chose this learning interest.") }
        if (picks.filterNotNull().isEmpty()) picks += lesson("quarterly", "A starting point for reading earnings reports.")
        return picks.filterNotNull().distinctBy { it.key }.take(3)
    }

    /** Push text with verified counts only; no company names (lock screens are visible to others). */
    fun notificationBody(reported: Int, upcoming: Int): String? = when {
        reported > 0 -> "${count(reported)} on your watchlist reported earnings this week."
        upcoming > 0 -> "${count(upcoming)} on your watchlist ${if (upcoming == 1) "is" else "are"} expected to report soon."
        else -> null
    }

    private fun count(n: Int) = when (n) { 1 -> "One company"; 2 -> "Two companies"; 3 -> "Three companies"; else -> "$n companies" }
}
