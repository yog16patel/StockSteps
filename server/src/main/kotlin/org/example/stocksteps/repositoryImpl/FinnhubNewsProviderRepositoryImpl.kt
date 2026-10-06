package org.example.stocksteps.repositoryImpl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderException.Failure
import org.example.stocksteps.repository.models.FinnhubNewsArticle
import org.example.stocksteps.repository.models.toNewsArticle
import java.net.URI
import java.time.Instant

class FinnhubNewsProviderRepositoryImpl(
    private val client: HttpClient,
    private val apiKey: String
) : NewsProviderRepository {
    private data class CachedName(val name: String?, val expiresAt: Instant)
    private val names = linkedMapOf<String, CachedName>()

    private suspend fun companyName(symbol: String): String? {
        synchronized(names) {
            names[symbol]?.takeIf { Instant.now().isBefore(it.expiresAt) }?.let { return it.name }
        }
        val name = withTimeoutOrNull(NAME_LOOKUP_TIMEOUT_MILLIS) {
            try {
                client.apiCall<FinnhubNewsCompany>("https://finnhub.io/api/v1/stock/profile2") {
                    header("X-Finnhub-Token", apiKey)
                    parameter("symbol", symbol)
                }.takeIf { it.ticker.equals(symbol, ignoreCase = true) }
                    ?.name?.takeIf { it.isNotBlank() }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                null // Missing profile makes relevance conservative; it does not fail news.
            }
        }

        synchronized(names) {
            if (names.size >= MAX_CACHED_NAMES) names.remove(names.keys.first())
            names[symbol] = CachedName(name, Instant.now().plusSeconds(if (name == null) FAILED_NAME_TTL_SECONDS else NAME_TTL_SECONDS))
        }
        return name
    }

    override suspend fun getCompanyNews(symbol: String): List<NewsArticle> = coroutineScope {
        val name = async { companyName(symbol) }
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val articles = client.apiCall<List<FinnhubNewsArticle>>("https://finnhub.io/api/v1/company-news") {
            header("X-Finnhub-Token", apiKey)
            parameter("symbol", symbol)
            parameter("from", today.minusDays(30).toString())
            parameter("to", today.toString())
        }
        validate(articles)
        val resolvedName = name.await()
        articles.filter { CompanyNewsRelevance.matches(it, symbol, resolvedName) }
            .sortedByDescending { it.datetime }.distinctBy { it.id?.takeIf { id -> id > 0 }?.toString() ?: it.url }
            .take(20).map { it.toNewsArticle().copy(symbol = symbol) }
    }
    override suspend fun getNews(page: Int, limit: Int): List<NewsArticle> {
        require(page in 0..100 && limit in 1..100)
        val articles = client.apiCall<List<FinnhubNewsArticle>>("https://finnhub.io/api/v1/news") {
            header("X-Finnhub-Token", apiKey)
            parameter("category", "general")
        }
        validate(articles)
        // Finnhub returns a current feed, not page-based historical results.
        return articles.sortedByDescending { it.datetime }
            .drop(page * limit).take(limit).map { it.toNewsArticle() }
    }
    private fun validate(articles: List<FinnhubNewsArticle>) {
        if (articles.any {
                val uri = runCatching { URI(it.url) }.getOrNull()
                it.headline.isBlank() || uri?.scheme !in listOf("http", "https") ||
                    uri?.host.isNullOrBlank() || it.datetime <= 0 ||
                    runCatching { Instant.ofEpochSecond(it.datetime) }.isFailure
            }) throw StockProviderException(Failure.INVALID_RESPONSE)
    }

    private companion object {
        const val NAME_LOOKUP_TIMEOUT_MILLIS = 2_000L
        const val MAX_CACHED_NAMES = 256
        const val NAME_TTL_SECONDS = 21_600L
        const val FAILED_NAME_TTL_SECONDS = 300L
    }

}


@Serializable
private data class FinnhubNewsCompany(val name: String? = null, val ticker: String? = null)
