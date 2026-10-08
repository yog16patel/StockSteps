package org.example.stocksteps.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Company-news categories; assigned only from clear keywords, otherwise OTHER. */
@Serializable
enum class NewsCategory { EARNINGS, PRODUCTS, BUSINESS, REGULATION, ANALYST, OTHER }

@Serializable
data class GlossaryTerm(val term: String, val definition: String)

/** A citable source behind an explanation (always one of the supplied articles). */
@Serializable
data class SourceReference(val articleId: String, val title: String, val publisher: String? = null, val publishedAt: String? = null, val url: String)

@Serializable
enum class InsightAvailability { AVAILABLE, UNAVAILABLE }

/**
 * Beginner explanation of one article, generated on request on the backend, grounded only in the
 * article's headline and summary (full text is never assumed), validated and cached.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ArticleInsight(
    val articleId: String,
    val availability: InsightAvailability,
    val simpleSummary: String? = null,
    val whyItMatters: List<String> = emptyList(),
    val watchNext: List<String> = emptyList(),
    val terms: List<GlossaryTerm> = emptyList(),
    val sources: List<SourceReference> = emptyList(),
    /** What the explanation could not rely on (e.g. only a headline was available). */
    val limitations: List<String> = emptyList(),
    /** True when an AI model wrote the text (validated); false for deterministic templates. */
    @EncodeDefault val aiGenerated: Boolean = false,
    val generatedAt: String? = null,
    val explanationVersion: String
)

@Serializable
enum class MovementPeriod {
    @SerialName("1D") ONE_DAY, @SerialName("1W") ONE_WEEK, @SerialName("1M") ONE_MONTH;

    val label: String get() = when (this) { ONE_DAY -> "1D"; ONE_WEEK -> "1W"; ONE_MONTH -> "1M" }

    companion object {
        fun parse(value: String?): MovementPeriod? = entries.firstOrNull { it.label.equals(value, ignoreCase = true) }
    }
}

/** How an event relates to a move. Never "cause": timing alone can't establish causation. */
@Serializable
enum class EvidenceLabel { CONFIRMED_EVENT, POSSIBLE_CONTRIBUTOR, MARKET_CONTEXT }

@Serializable
data class MovementEvent(
    val articleId: String,
    val title: String,
    val publisher: String? = null,
    val publishedAt: String? = null,
    val url: String,
    val category: NewsCategory = NewsCategory.OTHER,
    val label: EvidenceLabel
)

/** A benchmark's change over exactly the same dates (computed, never generated). */
@Serializable
data class BenchmarkMove(val symbol: String, val name: String, val changePercent: Double)

/**
 * Why did it move? All prices, returns, dates and benchmark comparisons are computed by the backend;
 * the optional AI step only words the summary from these facts and the listed events.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MovementExplanation(
    val symbol: String,
    val period: MovementPeriod,
    /** e.g. "Oct 7, 2026 · regular session" or "Sep 30 – Oct 7, 2026". */
    val sessionLabel: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val startPrice: Double? = null,
    val endPrice: Double? = null,
    val change: Double? = null,
    val changePercent: Double? = null,
    val currency: String? = null,
    val benchmarks: List<BenchmarkMove> = emptyList(),
    val events: List<MovementEvent> = emptyList(),
    val summary: String,
    val whatWeKnow: List<String> = emptyList(),
    val whatWeCannotConfirm: List<String> = emptyList(),
    val noConfirmedCatalyst: Boolean,
    val limitations: List<String> = emptyList(),
    @EncodeDefault val aiGenerated: Boolean = false,
    val generatedAt: String? = null,
    val explanationVersion: String
)
