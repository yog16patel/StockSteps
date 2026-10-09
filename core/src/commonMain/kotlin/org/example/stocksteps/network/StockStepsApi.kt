package org.example.stocksteps.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
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

    suspend fun getCompanyDetails(symbol: String): CompanyDetails {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request { url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/details") }
    }

    suspend fun getPriceChart(symbol: String, range: ChartRange): PriceChart {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request {
            url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/chart")
            parameter("range", range.label)
        }
    }

    /** Full historical P/E series; the apps slice 1Y/3Y/5Y/10Y locally. */
    suspend fun getValuationHistory(symbol: String): ValuationHistory {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request { url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/valuation") }
    }

    suspend fun getWhyMoving(symbol: String): WhyMoving {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request { url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/why-moving") }
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

    suspend fun getHomePersona(id: String): org.example.stocksteps.home.HomePersonaFixture {
        require(Regex("[a-z-]{1,40}").matches(id))
        return request { url("$baseUrl/api/v1/home/personas/$id") }
    }

    /** One company's normalized feed; filters and pages are sliced from the backend's cached copy. */
    suspend fun getCompanyNews(symbol: String, category: NewsCategory?, page: Int = 0, limit: Int = 50, enrich: Boolean = true): List<NewsArticle> {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request {
            url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/news")
            category?.let { parameter("category", it.name) }
            parameter("page", page)
            parameter("limit", limit)
            if (!enrich) parameter("enrich", "false")
        }
    }

    /** On-demand beginner explanation of one article (generated and cached by the backend). */
    suspend fun getArticleInsight(symbol: String, articleId: String): ArticleInsight {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        require(Regex("[A-Za-z0-9:_-]{1,128}").matches(articleId))
        return request { url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/news/$articleId/insight") }
    }

    suspend fun getMovement(symbol: String, period: MovementPeriod): MovementExplanation {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request {
            url("$baseUrl/api/v1/stocks/${symbol.uppercase()}/movement")
            parameter("period", period.label)
        }
    }

    /** Markets dashboard: session, indices, movers, sectors and market news in one request. */
    suspend fun getMarketsOverview(): MarketsOverview = request { url("$baseUrl/api/v1/markets/overview") }

    // Daily Market Brief: public, shared content (no user data). [scenario] is MOCK-only.
    suspend fun getLatestBrief(scenario: String? = null): org.example.stocksteps.brief.DailyBrief =
        request { url("$baseUrl/api/v1/daily-brief/latest" + (scenario?.let { "?scenario=$it" } ?: "")) }
    suspend fun getBrief(id: String): org.example.stocksteps.brief.DailyBrief {
        require(Regex("[a-z0-9-]{10,80}").matches(id))
        return request { url("$baseUrl/api/v1/daily-brief/$id") }
    }
    suspend fun getBriefHistory(): org.example.stocksteps.brief.BriefHistory = request { url("$baseUrl/api/v1/daily-brief/history") }

    /** Quotes, names/logos and next earnings for watched symbols (public market data). */
    suspend fun getWatchData(symbols: List<String>): WatchDataResponse {
        require(symbols.isNotEmpty() && symbols.size <= 100 && symbols.all { Regex("[A-Z0-9][A-Z0-9.^-]{0,31}").matches(it) })
        return request {
            url("$baseUrl/api/v1/stocks/watch-data")
            parameter("symbols", symbols.joinToString(","))
        }
    }

    suspend fun getBackendInfo(): BackendInfo = request { url("$baseUrl/api/v1/meta") }

    // Earnings (public; free tier).
    suspend fun earningsCalendar(query: org.example.stocksteps.earnings.EarningsCalendarQuery): org.example.stocksteps.earnings.EarningsCalendarPage = request {
        url("$baseUrl/api/v1/earnings/calendar"); earningsParameters(query)
    }
    suspend fun earningsDetails(symbol: String): org.example.stocksteps.earnings.EarningsDetails {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request { url("$baseUrl/api/v1/earnings/${symbol.uppercase()}") }
    }
    /** One calendar event by its stable id ("AAPL:2026-Q4"). */
    suspend fun earningsEvent(id: String): org.example.stocksteps.earnings.EarningsEventInfo {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}:\\d{4}-Q[1-4]").matches(id))
        return request { url("$baseUrl/api/v1/earnings/events/${id.encodeURLPathPart()}") }
    }
    /** One published report ("AAPL:2026-Q3") with server-calculated insights. */
    suspend fun earningsResults(reportId: String): org.example.stocksteps.earnings.EarningsResultsResponse {
        require(org.example.stocksteps.earnings.EarningsReportMapper.parse(reportId) != null)
        return request { url("$baseUrl/api/v1/earnings/reports/${reportId.encodeURLPathPart()}") }
    }
    suspend fun latestEarningsResults(symbol: String): org.example.stocksteps.earnings.EarningsResultsResponse {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request { url("$baseUrl/api/v1/earnings/company/${symbol.uppercase()}/latest") }
    }
    suspend fun nextEarnings(symbol: String): org.example.stocksteps.earnings.NextEarnings {
        require(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(symbol))
        return request { url("$baseUrl/api/v1/earnings/company/${symbol.uppercase()}/next") }
    }

    // Screener and comparison (public; all provider access stays on the server).
    suspend fun getScreenerCatalog(): org.example.stocksteps.screener.ScreenerCatalog = request { url("$baseUrl/api/v1/screener/catalog") }
    suspend fun searchScreener(query: org.example.stocksteps.screener.ScreenerQuery): org.example.stocksteps.screener.ScreenerPage = send(io.ktor.http.HttpMethod.Post) {
        url("$baseUrl/api/v1/screener/search")
        contentType(io.ktor.http.ContentType.Application.Json)
        setBody(query)
    }
    suspend fun compare(symbols: List<String>): org.example.stocksteps.screener.ComparisonResponse {
        require(symbols.all { Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(it) })
        return request { url("$baseUrl/api/v1/compare"); parameter("symbols", symbols.joinToString(",") { it.uppercase() }) }
    }
    suspend fun comparePerformance(symbols: List<String>, period: org.example.stocksteps.screener.PerformancePeriod): org.example.stocksteps.screener.PerformanceComparison {
        require(symbols.all { Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,19}").matches(it) })
        return request {
            url("$baseUrl/api/v1/compare/performance")
            parameter("symbols", symbols.joinToString(",") { it.uppercase() })
            parameter("period", period.label)
        }
    }

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
    ): T = send(io.ktor.http.HttpMethod.Get, configure)

    private suspend inline fun <reified T> send(
        method: io.ktor.http.HttpMethod,
        crossinline configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit
    ): T {
        val response = client.request {
            this.method = method
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

/** Calendar query → URL parameters (shared by the public and signed-in earnings calls). */
fun io.ktor.client.request.HttpRequestBuilder.earningsParameters(query: org.example.stocksteps.earnings.EarningsCalendarQuery) {
    parameter("from", query.from); parameter("to", query.to)
    if (query.exchanges.isNotEmpty()) parameter("exchange", query.exchanges.joinToString(","))
    if (query.countries.isNotEmpty()) parameter("country", query.countries.joinToString(","))
    if (query.sessions.isNotEmpty()) parameter("session", query.sessions.joinToString(",") { it.name })
    query.symbol?.let { parameter("symbol", it) }
    query.view?.let { parameter("view", it) }
    parameter("pageSize", query.pageSize)
    query.cursor?.let { parameter("cursor", it) }
    query.query?.let { parameter("q", it) }
    query.day?.let { parameter("day", it) }
    query.scope?.let { parameter("scope", it) }
    query.scenario?.let { parameter("scenario", it) }
}

fun io.ktor.client.HttpClientConfig<*>.configureStockStepsClient() {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 5_000
        socketTimeoutMillis = 20_000
    }
}
