package org.example.stocksteps.service

import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.model.NewsCategory
import org.example.stocksteps.news.NewsClassifier
import org.example.stocksteps.news.NewsSimplificationService
import org.example.stocksteps.repository.NewsProviderRepository
import java.net.URI
import java.time.Instant
import java.util.Locale

/**
 * Company and market news. A company's feed is fetched once per [COMPANY_TTL] (all filters and
 * pages are sliced from that cached copy), normalized, de-duplicated, classified and sorted newest
 * first. Simplified explanations are only attached to the page actually returned.
 */
class NewsService(
    private val provider: NewsProviderRepository,
    private val simplification: NewsSimplificationService? = null,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 256),
    private val now: () -> Instant = Instant::now
) {
    suspend fun getCompanyNews(symbol: String): List<NewsArticle> = getCompanyNews(symbol, category = null, page = 0, limit = MAX_LIMIT)

    suspend fun getCompanyNews(symbol: String, category: NewsCategory?, page: Int, limit: Int, enrich: Boolean = true): List<NewsArticle> {
        val page = companyFeed(symbol)
            .filter { category == null || it.category == category }
            .drop(page * limit).take(limit)
        return if (enrich) simplification?.enrich(page) ?: page else page
    }

    /** The normalized, cached feed (no AI enrichment), used to look up articles by id. */
    suspend fun companyFeed(symbol: String): List<NewsArticle> =
        cache.getOrLoad("company:$symbol", COMPANY_TTL, resultTtl = { if (it.isEmpty()) EMPTY_TTL else COMPANY_TTL }) {
            normalize(provider.getCompanyNews(symbol))
        }

    suspend fun findCompanyArticle(symbol: String, articleId: String): NewsArticle? =
        companyFeed(symbol).firstOrNull { it.id == articleId }

    suspend fun getNews(page: Int, limit: Int): List<NewsArticle> {
        val articles = provider.getNews(page, limit)
        return simplification?.enrich(articles) ?: articles
    }

    /**
     * Drops items without a usable title or an https link, removes non-https images, blanks and
     * future timestamps, de-duplicates by identity and by headline, classifies and sorts.
     */
    internal fun normalize(articles: List<NewsArticle>): List<NewsArticle> {
        val latestAllowed = now().plusSeconds(FUTURE_TOLERANCE_SECONDS)
        val seenHeadlines = HashSet<String>()
        return articles.asSequence()
            .mapNotNull { article ->
                val title = article.title.trim().takeIf { it.length >= 3 } ?: return@mapNotNull null
                val url = article.url.trim().takeIf(::isHttps) ?: return@mapNotNull null
                val published = article.publishedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                if (published != null && published.isAfter(latestAllowed)) return@mapNotNull null
                val description = article.description?.trim()?.takeIf { it.isNotEmpty() && !it.equals(title, ignoreCase = true) }
                val cleaned = article.copy(
                    title = title,
                    url = url,
                    source = article.source?.trim()?.takeIf { it.isNotEmpty() },
                    publishedAt = published?.toString(),
                    imageUrl = article.imageUrl?.trim()?.takeIf(::isHttps),
                    description = description,
                    explanation = null
                )
                cleaned.copy(id = NewsSimplificationService.identity(cleaned), category = NewsClassifier.classify(title, description))
            }
            .distinctBy { it.id }
            .filter { seenHeadlines.add(it.title.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()) }
            .sortedWith(compareByDescending<NewsArticle> { it.publishedAt?.let(Instant::parse) ?: Instant.EPOCH })
            .toList()
    }

    companion object {
        const val MAX_LIMIT = 50
        private const val COMPANY_TTL = 10 * 60_000L
        private const val EMPTY_TTL = 60_000L
        private const val FUTURE_TOLERANCE_SECONDS = 300L

        fun isHttps(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}
