package org.example.stocksteps.repositoryImpl

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
    override suspend fun getNews(page: Int, limit: Int): List<NewsArticle> {
        require(page in 0..100 && limit in 1..100)
        val articles = client.apiCall<List<FinnhubNewsArticle>>("https://finnhub.io/api/v1/news") {
            header("X-Finnhub-Token", apiKey)
            parameter("category", "general")
        }
        if (articles.any {
                val uri = runCatching { URI(it.url) }.getOrNull()
                it.headline.isBlank() || uri?.scheme !in listOf("http", "https") ||
                    uri?.host.isNullOrBlank() || it.datetime <= 0 ||
                    runCatching { Instant.ofEpochSecond(it.datetime) }.isFailure
            }) throw StockProviderException(Failure.INVALID_RESPONSE)
        // Finnhub returns a current feed, not page-based historical results.
        return articles.sortedByDescending { it.datetime }
            .drop(page * limit).take(limit).map { it.toNewsArticle() }
    }
}
