package org.example.stocksteps.news

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.*
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.NewsService
import java.security.MessageDigest
import java.time.Instant

/** What a generator returns before validation. Only [sourceIds] from the input may be cited. */
@Serializable
data class InsightDraft(
    val simpleSummary: String,
    val whyItMatters: List<String>,
    val watchNext: List<String> = emptyList(),
    val sourceIds: List<String>
)

/** Writes a beginner explanation from one article's headline and summary only. */
fun interface ArticleInsightGenerator {
    suspend fun generate(article: NewsArticle): InsightDraft
    /** False for deterministic templates (MOCK), so the apps never label them as AI. */
    val usesAi: Boolean get() = true
}

/**
 * Rejects drafts that could mislead: wrong shape or length, links, numbers that aren't in the
 * article, sources that weren't supplied, advice or price predictions, or causal certainty.
 */
object InsightValidator {
    private val url = Regex("(?i)(https?://|www\\.)")
    private val advice = Regex("(?i)\\b(you should|we recommend|i recommend|consider (buying|selling)|buy (the|this) stock|sell (the|this) stock|good time to (buy|sell)|strong buy|must[- ]own|will (rise|fall|soar|plunge|go up|go down|double)|guaranteed|price target of)\\b")
    private val certainty = Regex("(?i)\\b(caused the stock|is why the stock|sent shares|drove shares|because of this news)\\b")
    private val number = Regex("\\d+(?:[.,]\\d+)*")

    fun validate(draft: InsightDraft?, article: NewsArticle): InsightDraft? {
        draft ?: return null
        val summary = draft.simpleSummary.trim()
        val why = draft.whyItMatters.map(String::trim).filter(String::isNotEmpty)
        val next = draft.watchNext.map(String::trim).filter(String::isNotEmpty)
        if (summary.length !in 20..400 || why.size !in 1..3 || next.size > 3) return null
        if (why.any { it.length > 240 } || next.any { it.length > 160 }) return null
        val id = article.id ?: return null
        if (draft.sourceIds.isEmpty() || draft.sourceIds.any { it != id }) return null
        val all = listOf(summary) + why + next
        val input = "${article.title} ${article.description.orEmpty()}"
        val allowedNumbers = numbers(input)
        // Wording quoted from the article itself is allowed; new advice or certainty is not.
        fun introduces(pattern: Regex, text: String) = pattern.findAll(text).any { !input.contains(it.value, ignoreCase = true) }
        if (all.any { text -> url.containsMatchIn(text) || introduces(advice, text) || introduces(certainty, text) }) return null
        if (all.any { text -> numbers(text).any { it !in allowedNumbers } }) return null
        return InsightDraft(summary, why, next, listOf(id))
    }

    internal fun numbers(text: String): Set<String> = number.findAll(text).map { it.value.replace(",", "") }.toSet()
}

/**
 * On-demand article explanations. One generation per article + content + version, cached (success
 * 7 days, failure 15 minutes); concurrent requests for the same article share one call; at most
 * [maxConcurrent] generations run at once and at most [hourlyBudget] start per hour.
 */
class ArticleInsightService(
    private val news: NewsService,
    private val generator: ArticleInsightGenerator?,
    private val version: String,
    private val timeoutMillis: Long = 10_000,
    maxConcurrent: Int = 2,
    private val hourlyBudget: Int = 200,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 1024),
    private val now: () -> Instant = Instant::now
) {
    private val permits = Semaphore(maxConcurrent)
    private var budgetWindow = 0L
    private var budgetUsed = 0

    /** Null when the article isn't in this company's current feed (the route answers 404). */
    suspend fun insight(symbol: String, articleId: String): ArticleInsight? {
        val article = news.findCompanyArticle(symbol, articleId) ?: return null
        val key = "insight|$version|$articleId|" + digest("${article.title}|${article.description.orEmpty()}")
        return cache.getOrLoad(key, SUCCESS_TTL, resultTtl = { if (it.availability == InsightAvailability.AVAILABLE) SUCCESS_TTL else FAILURE_TTL }) {
            generate(article)
        }
    }

    private suspend fun generate(article: NewsArticle): ArticleInsight {
        val description = article.description
        if (description == null || description.length < MIN_DESCRIPTION) {
            return unavailable(article, "Only a headline was available, which isn't enough to explain this story reliably.")
        }
        val ai = generator ?: return unavailable(article, "Explanations aren't available right now.")
        if (!takeBudget()) return unavailable(article, "Explanations are busy right now. Please try again later.")
        val draft = permits.withPermit {
            try {
                withTimeoutOrNull(timeoutMillis) { ai.generate(article) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                null
            }
        }
        val valid = InsightValidator.validate(draft, article)
            ?: return unavailable(article, "We couldn't produce a reliable explanation for this article.")
        return ArticleInsight(
            articleId = article.id!!,
            availability = InsightAvailability.AVAILABLE,
            simpleSummary = valid.simpleSummary,
            whyItMatters = valid.whyItMatters,
            watchNext = valid.watchNext,
            terms = FinancialGlossary.termsIn("${article.title} $description ${valid.simpleSummary}"),
            sources = listOf(reference(article)),
            limitations = listOf("Based only on the headline and summary from ${article.source ?: "the publisher"}, not the full article."),
            aiGenerated = ai.usesAi,
            generatedAt = now().toString(),
            explanationVersion = version
        )
    }

    private fun unavailable(article: NewsArticle, reason: String) = ArticleInsight(
        articleId = article.id.orEmpty(),
        availability = InsightAvailability.UNAVAILABLE,
        terms = FinancialGlossary.termsIn("${article.title} ${article.description.orEmpty()}"),
        sources = listOf(reference(article)),
        limitations = listOf(reason),
        explanationVersion = version
    )

    private fun takeBudget(): Boolean = synchronized(this) {
        val hour = now().epochSecond / 3600
        if (hour != budgetWindow) { budgetWindow = hour; budgetUsed = 0 }
        if (budgetUsed >= hourlyBudget) return false
        budgetUsed++
        true
    }

    companion object {
        private const val SUCCESS_TTL = 7 * 24 * 3_600_000L
        private const val FAILURE_TTL = 15 * 60_000L
        private const val MIN_DESCRIPTION = 40

        fun reference(article: NewsArticle) = SourceReference(article.id.orEmpty(), article.title, article.source, article.publishedAt, article.url)
        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) }
    }
}

/**
 * MOCK mode: deterministic explanations built from the article itself (no AI, no network). Articles
 * whose id contains "timeout" or "malformed" exercise the slow-generator and rejected-output paths.
 */
class TemplateArticleInsightGenerator(private val slowDelayMillis: Long = 5_000) : ArticleInsightGenerator {
    override val usesAi: Boolean get() = false
    override suspend fun generate(article: NewsArticle): InsightDraft {
        val id = article.id.orEmpty()
        if ("timeout" in id) delay(slowDelayMillis)
        if ("malformed" in id) {
            return InsightDraft("Shares will rise 45% — see https://example.com for details.", listOf("You should buy now."), sourceIds = listOf("other"))
        }
        val description = article.description.orEmpty()
        val firstSentence = Regex("(?<=[.!?])\\s+").split(description).first().take(300).trim()
        val summary = if (firstSentence.length >= 20) firstSentence else description.take(300).trim()
        val category = article.category ?: NewsCategory.OTHER
        return InsightDraft(
            simpleSummary = summary,
            whyItMatters = listOf(WHY.getValue(category)),
            watchNext = listOf(NEXT.getValue(category)),
            sourceIds = listOf(id)
        )
    }

    private companion object {
        val WHY = mapOf(
            NewsCategory.EARNINGS to "Earnings updates show how the business is actually doing, and they can change what investors expect from the company next.",
            NewsCategory.PRODUCTS to "New products can open new sources of revenue, but it usually takes time to know whether customers will buy them.",
            NewsCategory.BUSINESS to "Deals, investments and other business decisions can shape a company's costs and growth for years.",
            NewsCategory.REGULATION to "Rules, investigations and court cases can limit what a company may do or add costs, so investors watch how they turn out.",
            NewsCategory.ANALYST to "Analyst views are opinions, not facts. They can influence attention on a stock but don't change the business itself.",
            NewsCategory.OTHER to "This story adds context about the company. On its own it may not say much about the business's results."
        )
        val NEXT = mapOf(
            NewsCategory.EARNINGS to "The company's next quarterly report and any update to its forecast.",
            NewsCategory.PRODUCTS to "Reviews, customer demand and whether the company mentions the product in its next results.",
            NewsCategory.BUSINESS to "Whether the company shares more detail on costs, timing or expected benefits.",
            NewsCategory.REGULATION to "Official decisions, deadlines or responses from the company and regulators.",
            NewsCategory.ANALYST to "Whether other analysts change their views and what the company reports next.",
            NewsCategory.OTHER to "Follow-up reporting or company statements that add more detail."
        )
    }
}
