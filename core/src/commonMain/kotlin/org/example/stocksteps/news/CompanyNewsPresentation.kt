package org.example.stocksteps.news

import org.example.stocksteps.companydetail.FactTone
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.home.HomePresentation
import org.example.stocksteps.model.*
import kotlin.math.abs
import kotlin.math.roundToLong

/** Company News filters; only filters with matching articles are offered (plus All). */
enum class NewsFilter(val label: String, val category: NewsCategory?) {
    ALL("All", null),
    EARNINGS("Earnings", NewsCategory.EARNINGS),
    PRODUCTS("Products", NewsCategory.PRODUCTS),
    BUSINESS("Business", NewsCategory.BUSINESS),
    REGULATION("Regulation", NewsCategory.REGULATION),
    ANALYST("Analysts", NewsCategory.ANALYST),
    OTHER("Other", NewsCategory.OTHER)
}

data class NewsFilterOption(val filter: NewsFilter, val label: String, val count: Int)

data class CompanyNewsItem(
    val news: NewsUiModel,
    /** Backend article id for the explanation screen; null when the article can't be explained. */
    val articleId: String?,
    val categoryLabel: String?,
    val categoryTone: FactTone
)

data class CompanyNewsFeedModel(
    val filters: List<NewsFilterOption>,
    val selected: NewsFilter,
    val items: List<CompanyNewsItem>,
    /** "Learn as you read": glossary terms that appear in the visible articles. */
    val learnTerms: List<GlossaryTerm>,
    val emptyMessage: String?
)

object CompanyNewsPresenter {
    private val articleId = Regex("[A-Za-z0-9:_-]{1,128}")

    fun feed(articles: List<NewsArticle>, selected: NewsFilter, nowEpochMillis: Long): CompanyNewsFeedModel {
        val counts = articles.groupingBy { it.category ?: NewsCategory.OTHER }.eachCount()
        val filters = listOf(NewsFilterOption(NewsFilter.ALL, NewsFilter.ALL.label, articles.size)) +
            NewsFilter.entries.filter { it.category != null && (counts[it.category] ?: 0) > 0 }
                .map { NewsFilterOption(it, it.label, counts.getValue(it.category!!)) }
        // A filter that no longer has articles (e.g. after a refresh) falls back to All.
        val active = selected.takeIf { option -> filters.any { it.filter == option } } ?: NewsFilter.ALL
        val visible = articles.filter { active.category == null || (it.category ?: NewsCategory.OTHER) == active.category }
        val items = visible.map { article ->
            val category = article.category ?: NewsCategory.OTHER
            CompanyNewsItem(
                news = NewsPresentation.model(article, nowEpochMillis),
                articleId = article.id?.takeIf { articleId.matches(it) },
                categoryLabel = NewsFilter.entries.first { it.category == category }.label.takeIf { category != NewsCategory.OTHER }
                    ?.let { if (category == NewsCategory.ANALYST) "Analyst view" else it },
                categoryTone = if (category == NewsCategory.ANALYST) FactTone.CAUTION else FactTone.NEUTRAL
            )
        }
        val text = visible.take(10).joinToString(" ") { "${it.title} ${it.description.orEmpty()}" }
        return CompanyNewsFeedModel(
            filters = filters,
            selected = active,
            items = items,
            learnTerms = FinancialGlossary.termsIn(text, limit = 3),
            emptyMessage = when {
                articles.isEmpty() -> "No recent news for this company yet."
                items.isEmpty() -> "No ${active.label.lowercase()} news right now."
                else -> null
            }
        )
    }
}

data class InsightSourceRow(val title: String, val meta: String?, val url: String?)

data class ArticleInsightModel(
    val available: Boolean,
    val headline: String?,
    val summary: String?,
    val whyItMatters: List<String>,
    val watchNext: List<String>,
    val terms: List<GlossaryTerm>,
    val sources: List<InsightSourceRow>,
    val limitations: List<String>,
    /** Where the text came from: AI (validated) or a deterministic template. */
    val provenance: String,
    val unavailableMessage: String?
)

object ArticleInsightPresenter {
    fun model(insight: ArticleInsight, nowEpochMillis: Long): ArticleInsightModel {
        val available = insight.availability == InsightAvailability.AVAILABLE && !insight.simpleSummary.isNullOrBlank()
        return ArticleInsightModel(
            available = available,
            headline = insight.sources.firstOrNull()?.title,
            summary = insight.simpleSummary?.takeIf { available },
            whyItMatters = if (available) insight.whyItMatters else emptyList(),
            watchNext = if (available) insight.watchNext else emptyList(),
            terms = insight.terms,
            sources = insight.sources.map { source ->
                InsightSourceRow(
                    title = source.title,
                    meta = listOfNotNull(source.publisher, source.publishedAt?.let { NewsPresentation.relativeTime(it, nowEpochMillis) })
                        .joinToString(" · ").takeIf { it.isNotEmpty() },
                    url = source.url.takeIf { it.startsWith("https://") }
                )
            },
            limitations = insight.limitations,
            provenance = when {
                !available -> "No explanation was generated."
                insight.aiGenerated -> "Written by AI from the headline and summary only, then checked for unsupported numbers, links and advice."
                else -> "Sample explanation from a template (mock data, no AI), based on the headline and summary."
            },
            unavailableMessage = if (available) null else insight.limitations.firstOrNull() ?: "An explanation isn't available for this article."
        )
    }
}

data class MovementBenchmarkRow(val name: String, val change: String, val direction: PriceDirection)

data class MovementEventRow(
    val articleId: String,
    val title: String,
    val meta: String?,
    val label: String,
    val tone: FactTone,
    val url: String?
)

data class MovementModel(
    val title: String,
    val period: MovementPeriod,
    val sessionLabel: String?,
    val change: String,
    val amount: String?,
    val direction: PriceDirection,
    /** "$529.30 → $529.76" */
    val priceLine: String?,
    val summary: String,
    val provenance: String,
    val benchmarks: List<MovementBenchmarkRow>,
    /** "1.15 pts better than the S&P 500" or null without a market benchmark. */
    val relativeToMarket: String?,
    val events: List<MovementEventRow>,
    val noConfirmedCatalyst: Boolean,
    val whatWeKnow: List<String>,
    val whatWeCannotConfirm: List<String>,
    val limitations: List<String>,
    /** What the evidence labels mean, shown once under the events. */
    val labelGuide: List<Pair<String, String>>
)

/** Compact "today's movement" card on Company News and Company Details. */
data class MovementPreview(
    val change: String,
    val direction: PriceDirection,
    val sessionLabel: String?,
    val summary: String,
    val noConfirmedCatalyst: Boolean
)

object MovementPresenter {
    const val NO_CATALYST_TITLE = "No confirmed catalyst"
    const val NO_CATALYST_BODY = "We didn't find a confirmed company-specific event for this move. Prices also move with the overall market, " +
        "investor sentiment and large trades, which news doesn't always capture."

    val labelGuide = listOf(
        label(EvidenceLabel.CONFIRMED_EVENT) to "A company event reported in this window. It happened, but that alone doesn't prove it moved the price.",
        label(EvidenceLabel.POSSIBLE_CONTRIBUTOR) to "News or commentary about the company from the same time. It may or may not have mattered.",
        label(EvidenceLabel.MARKET_CONTEXT) to "Broader market or economic news that can move many stocks at once."
    )

    fun label(evidence: EvidenceLabel) = when (evidence) {
        EvidenceLabel.CONFIRMED_EVENT -> "Confirmed event"
        EvidenceLabel.POSSIBLE_CONTRIBUTOR -> "Possible contributor"
        EvidenceLabel.MARKET_CONTEXT -> "Market context"
    }

    fun model(movement: MovementExplanation, nowEpochMillis: Long): MovementModel {
        val market = movement.benchmarks.firstOrNull { it.symbol == "SPY" }
        val relative = market?.let { spy -> movement.changePercent?.let { relativeLabel(it - spy.changePercent) } }
        return MovementModel(
            title = "Why did ${movement.symbol} move?",
            period = movement.period,
            sessionLabel = movement.sessionLabel,
            change = HomePresentation.percent(movement.changePercent),
            amount = movement.change?.let { amount(it, movement.currency) },
            direction = HomePresentation.direction(movement.changePercent),
            priceLine = movement.startPrice?.let { start -> movement.endPrice?.let { end ->
                listOfNotNull(HomePresentation.price(start, movement.currency), HomePresentation.price(end, movement.currency))
                    .takeIf { it.size == 2 }?.joinToString(" → ")
            } },
            summary = movement.summary,
            provenance = if (movement.aiGenerated) "Summary written by AI from the computed prices and listed news only, then checked for causal claims and unsupported numbers."
                else "Summary generated from computed prices and news timing (no AI).",
            benchmarks = movement.benchmarks.map { MovementBenchmarkRow(it.name, HomePresentation.percent(it.changePercent), HomePresentation.direction(it.changePercent)) },
            relativeToMarket = relative,
            events = movement.events.map { event ->
                MovementEventRow(
                    articleId = event.articleId,
                    title = event.title,
                    meta = listOfNotNull(event.publisher, event.publishedAt?.let { NewsPresentation.relativeTime(it, nowEpochMillis) })
                        .joinToString(" · ").takeIf { it.isNotEmpty() },
                    label = label(event.label),
                    tone = when (event.label) {
                        EvidenceLabel.CONFIRMED_EVENT -> FactTone.POSITIVE
                        EvidenceLabel.POSSIBLE_CONTRIBUTOR -> FactTone.CAUTION
                        EvidenceLabel.MARKET_CONTEXT -> FactTone.NEUTRAL
                    },
                    url = event.url.takeIf { it.startsWith("https://") }
                )
            },
            noConfirmedCatalyst = movement.noConfirmedCatalyst,
            whatWeKnow = movement.whatWeKnow,
            whatWeCannotConfirm = movement.whatWeCannotConfirm,
            limitations = movement.limitations,
            labelGuide = labelGuide
        )
    }

    fun preview(movement: MovementExplanation) = MovementPreview(
        change = HomePresentation.percent(movement.changePercent),
        direction = HomePresentation.direction(movement.changePercent),
        sessionLabel = movement.sessionLabel,
        summary = movement.summary,
        noConfirmedCatalyst = movement.noConfirmedCatalyst
    )

    /** "In line with the S&P 500" within ±0.5 points, otherwise "1.15 pts better/worse than the S&P 500". */
    fun relativeLabel(gap: Double): String {
        if (abs(gap) < 0.5) return "In line with the S&P 500"
        val hundredths = (abs(gap) * 100).roundToLong()
        val value = "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
        return "$value pts ${if (gap > 0) "better" else "worse"} than the S&P 500"
    }

    private fun amount(value: Double, currency: String?): String? {
        val price = HomePresentation.price(abs(value), currency) ?: return null
        return when (HomePresentation.direction(value)) {
            PriceDirection.UP -> "+$price"
            PriceDirection.DOWN -> "-$price"
            else -> price
        }
    }
}
