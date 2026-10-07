package org.example.stocksteps.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.example.stocksteps.model.*

class StockStepsApiException(val status: Int, val error: ApiError) : Exception(error.message)

// The caller owns the injected client and closes it when no longer needed.
// The base URL is resolved per request so a backend switch applies to the next request.
class StockStepsApi(private val client: HttpClient, private val baseUrlProvider: () -> String) {
    constructor(client: HttpClient, baseUrl: String) : this(client, { baseUrl })

    init { validated(baseUrlProvider()) }

    private val baseUrl: String get() = validated(baseUrlProvider())

    private fun validated(url: String): String {
        require(Url(url).protocol in listOf(URLProtocol.HTTP, URLProtocol.HTTPS))
        return url.trimEnd('/')
    }

    suspend fun searchStocks(query: String): List<StockSearchResult> = request {
        url("$baseUrl/api/v1/stocks/search")
        parameter("query", query.trim())
    }

    suspend fun getQuote(symbol: String): StockQuote {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request { url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/quote") }
    }

    suspend fun getSparkline(symbol: String): Sparkline {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request { url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/sparkline") }
    }

    suspend fun getProfile(symbol: String): CompanyProfile {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request { url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/profile") }
    }

    suspend fun getFundamentals(symbol: String, period: String = "annual"): CompanyFundamentals {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        require(period in listOf("annual", "quarter"))
        return request {
            url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/fundamentals")
            parameter("period", period)
        }
    }

    suspend fun getCompanyNews(symbol: String): List<NewsArticle> {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request { url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/news") }
    }

    suspend fun getBackendInfo(): BackendInfo = request { url("$baseUrl/api/v1/meta") }

    suspend fun getMarketSnapshot(): MarketSnapshot = request { url("$baseUrl/market/snapshot") }

    suspend fun getGainers(): List<MarketMover> = request { url("$baseUrl/api/v1/market/gainers") }
    suspend fun getLosers(): List<MarketMover> = request { url("$baseUrl/api/v1/market/losers") }
    suspend fun getNews(): List<NewsArticle> = request {
        url("$baseUrl/api/v1/news")
        parameter("page", 0)
        parameter("limit", 20)
    }

    private suspend inline fun <reified T> request(
        crossinline configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit
    ): T {
        val response = client.get {
            expectSuccess = false
            configure()
        }
        if (response.status.value !in 200..299) {
            val error = try {
                response.body<ApiError>()
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                ApiError("HTTP_ERROR", "The StockSteps server could not complete the request.")
            }
            throw StockStepsApiException(response.status.value, error)
        }
        return response.body()
    }
}

fun io.ktor.client.HttpClientConfig<*>.configureStockStepsClient() {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 5_000
        socketTimeoutMillis = 20_000
    }
}
