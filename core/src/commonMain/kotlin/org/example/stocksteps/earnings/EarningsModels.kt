package org.example.stocksteps.earnings

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.EarningsDateStatus
import org.example.stocksteps.model.EarningsTime

/**
 * How an EPS figure was measured. Values are only compared when both sides use the same known
 * basis: adjusted (non-GAAP) consensus is never compared with GAAP diluted EPS.
 */
@Serializable enum class EpsBasis(val label: String) { GAAP_DILUTED("GAAP diluted"), GAAP_BASIC("GAAP basic"), ADJUSTED("Adjusted (non-GAAP)"), UNKNOWN("Basis not reported") }

/** Analysts' consensus for one fiscal period, from one provider. */
@Serializable
data class EarningsEstimate(
    val eps: Double? = null,
    val epsBasis: EpsBasis = EpsBasis.UNKNOWN,
    /** Raw currency units (never millions/billions). */
    val revenue: Double? = null,
    val currency: String? = null,
    val analysts: Int? = null,
    val source: String,
    val asOf: String? = null
)

/** What the company reported for one fiscal period, from one provider. */
@Serializable
data class EarningsActual(
    val eps: Double? = null,
    val epsBasis: EpsBasis = EpsBasis.UNKNOWN,
    /** Raw currency units. */
    val revenue: Double? = null,
    val currency: String? = null,
    val source: String,
    val reportedAt: String? = null,
    /** True when the provider marked the figures as restated or revised after first publication. */
    val restated: Boolean = false
)

/**
 * One company's earnings announcement for one fiscal period. [id] is stable across date changes
 * ("SYMBOL:2026-Q3"), so reminders and caches follow the event, not a particular date.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsEvent(
    val id: String,
    val symbol: String,
    val name: String,
    val exchange: String? = null,
    val country: String? = null,
    val logoUrl: String? = null,
    val fiscalYear: Int,
    /** 1–4. */
    val fiscalQuarter: Int,
    val periodEnd: String? = null,
    /** Exchange-local announcement date (yyyy-MM-dd). */
    val date: String,
    val session: EarningsTime = EarningsTime.UNKNOWN,
    val dateStatus: EarningsDateStatus = EarningsDateStatus.ESTIMATED,
    /** The previously announced date when the date moved (shown so changes are visible). */
    val previousDate: String? = null,
    val estimate: EarningsEstimate? = null,
    val actual: EarningsActual? = null,
    /** Where the date and session came from. */
    val source: String,
    /** When StockSteps last recorded the event from [source]. */
    val updatedAt: String,
    /** Exchange-local report time ("16:05"), only when the source states it; never inferred. */
    val eventTime: String? = null,
    /** IANA zone of the listing exchange that [date] and [eventTime] are expressed in, when known. */
    val timeZone: String? = null,
    /** A status the source states explicitly (postponed, canceled, reported); null when it doesn't say. */
    val sourceStatus: EarningsEventStatus? = null,
    /** When the source last changed this event; null when the provider doesn't say. */
    val sourceUpdatedAt: String? = null
) {
    val period: String get() = "Q$fiscalQuarter FY$fiscalYear"
}

@Serializable enum class EarningsStatus(val label: String) {
    UPCOMING("Upcoming"), REPORTED("Reported"), PARTIALLY_REPORTED("Partially reported"), DATA_PENDING("Results pending"), UNAVAILABLE("Unavailable")
}

@Serializable enum class Classification(val label: String) { BEAT("Beat"), MISS("Miss"), IN_LINE("In line"), UNAVAILABLE("Not comparable") }

@Serializable
data class SurpriseResult(
    val actual: Double? = null,
    val estimate: Double? = null,
    /** actual − estimate. */
    val absolute: Double? = null,
    /** Percent surprise; null when not meaningful (e.g. a zero or near-zero estimate). */
    val percent: Double? = null,
    val classification: Classification = Classification.UNAVAILABLE,
    /** Why the comparison isn't available, in plain language. */
    val reason: String? = null,
    val currency: String? = null,
    val basis: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PriceReaction(
    val available: Boolean,
    /** True when the session is unknown: the window covers the announcement date but isn't exact. */
    val approximate: Boolean = false,
    val baselineDate: String? = null,
    val baselineClose: Double? = null,
    val endDate: String? = null,
    val endClose: Double? = null,
    val changePercent: Double? = null,
    /** Same-window change of a market proxy (e.g. SPY for US listings), when available. */
    val marketChangePercent: Double? = null,
    val marketLabel: String? = null,
    val currency: String? = null,
    /** "Close on Oct 29 → close on Oct 30 (after-market announcement)". */
    val window: String? = null,
    val methodology: String,
    val source: String? = null,
    val asOf: String? = null,
    val reason: String? = null
)

@Serializable enum class InsightAvailability { AVAILABLE, PARTIAL }

/** A deterministic statement computed from normalized metrics; never AI-generated. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsInsight(
    val id: String,
    val category: String,
    val title: String,
    val explanation: String,
    @EncodeDefault val metrics: List<String> = emptyList(),
    val period: String? = null,
    val asOf: String,
    /** Key into [EarningsEducation]. */
    val methodology: String,
    val availability: InsightAvailability = InsightAvailability.AVAILABLE,
    /** Advanced insights are StockSteps+ only. */
    val advanced: Boolean = false
)

/** One reported period in a company's earnings history. */
@Serializable
data class EarningsHistoryRow(
    val event: EarningsEvent,
    val eps: SurpriseResult,
    val revenue: SurpriseResult,
    val revenueGrowthYoY: Double? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsDetails(
    val symbol: String,
    val name: String,
    /** The latest reported event, or the next upcoming one when nothing has been reported. */
    val event: EarningsEvent?,
    val status: EarningsStatus,
    val eps: SurpriseResult,
    val revenue: SurpriseResult,
    val revenueGrowthYoY: Double? = null,
    val revenueGrowthQoQ: Double? = null,
    val summary: String? = null,
    val summaryExplanation: String? = null,
    val next: EarningsEvent? = null,
    @EncodeDefault val history: List<EarningsHistoryRow> = emptyList(),
    /** True when older history exists but is StockSteps+ only. */
    val historyLocked: Boolean = false,
    val reaction: PriceReaction? = null,
    @EncodeDefault val insights: List<EarningsInsight> = emptyList(),
    /** Count of advanced insights withheld for the free tier. */
    val lockedInsights: Int = 0,
    @EncodeDefault val notes: List<String> = emptyList(),
    val plus: Boolean = false,
    val asOf: String,
    val stale: Boolean = false,
    val sampleData: Boolean = false
)

@Serializable enum class FollowReason { PORTFOLIO, WATCHLIST }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsCalendarItem(
    val event: EarningsEvent,
    val status: EarningsStatus,
    val eps: SurpriseResult? = null,
    val revenue: SurpriseResult? = null,
    /** Why the user follows it (signed-in Following only). Watchlist membership isn't ownership. */
    @EncodeDefault val following: List<FollowReason> = emptyList(),
    /** Shares currently held across the user's portfolio accounts (Following only). */
    val sharesHeld: Double? = null,
    /** Calendar status from source data (never "reported" just because the date passed). */
    val eventStatus: EarningsEventStatus = EarningsEventStatus.UNKNOWN
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsCalendarPage(
    @EncodeDefault val items: List<EarningsCalendarItem> = emptyList(),
    val from: String,
    val to: String,
    val total: Int = 0,
    val nextCursor: String? = null,
    @EncodeDefault val notes: List<String> = emptyList(),
    val asOf: String,
    /** True when some upcoming dates haven't been refreshed by the source recently. */
    val stale: Boolean = false,
    val sampleData: Boolean = false,
    /** Matching events per exchange-local date over the whole [from]..[to] range (before paging and the day filter). */
    @EncodeDefault val dayCounts: Map<String, Int> = emptyMap(),
    /** Whether the events came straight from the source, from the server cache, or from an old copy after a failure. */
    val freshness: DataFreshness = DataFreshness.FRESH,
    /** When the server last fetched these events from the source. */
    val fetchedAt: String? = null,
    /** True when part of the answer couldn't be loaded (e.g. company-name search); what's shown is still correct. */
    val partial: Boolean = false,
    /** Signed-in watchlist scope: distinct companies across all of the user's watchlists. */
    val followedCount: Int? = null
)

/** Calendar filters (all optional). Dates are exchange-local yyyy-MM-dd. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsCalendarQuery(
    val from: String,
    val to: String,
    @EncodeDefault val exchanges: List<String> = emptyList(),
    @EncodeDefault val countries: List<String> = emptyList(),
    @EncodeDefault val sessions: List<EarningsTime> = emptyList(),
    val symbol: String? = null,
    /** "upcoming" (not verified as reported) or "reported" (verified; "results" is accepted too); null for both. */
    val view: String? = null,
    val pageSize: Int = 30,
    val cursor: String? = null,
    /** Case-insensitive company name or ticker search within the range. */
    val query: String? = null,
    /** Only events on this date are returned; [EarningsCalendarPage.dayCounts] still covers the whole range. */
    val day: String? = null,
    /** Signed-in Following only: "watchlist" limits it to watchlist companies (no portfolio holdings). */
    val scope: String? = null,
    /** MOCK-only demo scenario ("provider-unavailable", "stale-cache"); ignored in REAL. */
    val scenario: String? = null
)

@Serializable data class EarningsQuestion(val question: String)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EarningsAnswer(
    val answer: String,
    @EncodeDefault val sources: List<String> = emptyList(),
    /** True when the evidence wasn't enough to explain a cause. */
    val insufficientEvidence: Boolean = false,
    val remainingToday: Int? = null
)
