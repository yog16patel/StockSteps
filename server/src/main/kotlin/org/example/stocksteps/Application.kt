package org.example.stocksteps

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.util.Locale
import org.example.stocksteps.appconfig.AppConfig
import org.example.stocksteps.appconfig.DataMode
import org.example.stocksteps.model.BackendInfo
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.repository.MarketDataProvider
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.repository.StockQuoteProviderRepository
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.httpclient.HttpClientProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.repositoryImpl.FinnhubStockProviderRepositoryImpl
import org.example.stocksteps.service.NewsService
import org.example.stocksteps.news.createNewsService
import org.example.stocksteps.repositoryImpl.FinnhubNewsProviderRepositoryImpl
import org.example.stocksteps.service.StockService

fun main() {
    // PORT lets a mock server run beside the real one locally; Cloud Run also sets it.
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureApiErrors()
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }
        )
    }

    val dataMode = DataMode.fromEnvironment()
    log.info("StockSteps data mode: {}", dataMode.name.lowercase(Locale.ROOT))
    val sources = when (dataMode) {
        DataMode.REAL -> realDataSources()
        DataMode.MOCK -> mockDataSources()
    }

    routing {
        get("/health") {
            call.respondText("StockSteps API is running")
        }
        get("/api/v1/meta") { call.respond(BackendInfo(dataMode.name.lowercase(Locale.ROOT))) }
    }

    // Search stays with the stock provider; quotes can be selected independently.
    val stockService = StockService(
        stockProvider = sources.stockProvider,
        quoteProvider = sources.quoteProvider
    )

    // One chart service so the price chart and the valuation history share cached daily closes.
    val charts = org.example.stocksteps.service.PriceChartService(sources.priceHistory)
    val valuation = org.example.stocksteps.service.ValuationService(sources.stockProvider, charts, sources.earnings)
    routing {
        stockRoutes(stockService)
        valuationRoutes(valuation)
        companyFinancialRoutes(org.example.stocksteps.service.CompanyFinancialService(sources.stockProvider))
        marketRoutes(stockService)
        marketSnapshotRoutes(org.example.stocksteps.service.MarketSnapshotService(sources.marketData))
        newsRoutes(sources.news)
        sparklineRoutes(org.example.stocksteps.service.SparklineService(sources.priceHistory))
        companyDetailsRoutes(
            details = org.example.stocksteps.service.CompanyDetailsService(
                stocks = stockService,
                financials = org.example.stocksteps.service.CompanyFinancialService(sources.stockProvider),
                marketData = sources.marketData,
                valuation = valuation
            ),
            charts = charts,
            whyMoving = org.example.stocksteps.service.WhyMovingService(sources.whyMoving)
        )
    }

}

fun Route.stockRoutes(stockService: StockService) {
    route("/api/v1/stocks") {
        get("/search") {
            val query = call.request.queryParameters["query"]?.trim()
            if (query.isNullOrEmpty() || query.length > 100 || query.any { it.isISOControl() }) {
                call.respond(HttpStatusCode.BadRequest,
                    ApiError("INVALID_QUERY", "Provide a company name of 1–100 characters."))
                return@get
            }
            call.respond(stockService.searchStocks(query))
        }
        get("/{symbol}/profile") {
            val symbol = call.parameters["symbol"]?.uppercase(Locale.ROOT)
            if (symbol == null || !Regex("[A-Z0-9][A-Z0-9.-]{0,19}").matches(symbol)) {
                call.respond(HttpStatusCode.BadRequest,
                    ApiError("INVALID_SYMBOL", "Use a stock symbol of 1–20 letters, digits, dots, or hyphens."))
                return@get
            }
            val profile = stockService.getProfile(symbol)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound,
                    ApiError("PROFILE_NOT_FOUND", "No company profile was found for this symbol."))
                return@get
            }
            call.respond(profile)
        }
        get("/{symbol}/quote") {
            val symbol = call.parameters["symbol"]?.uppercase(Locale.ROOT)
            // Supports common US/Canadian symbols, including BRK.B and SHOP.TO.
            if (symbol == null || !Regex("[A-Z0-9][A-Z0-9.-]{0,19}").matches(symbol)) {
                call.respond(HttpStatusCode.BadRequest,
                    ApiError("INVALID_SYMBOL", "Use a stock symbol of 1–20 letters, digits, dots, or hyphens."))
                return@get
            }

            val quote = stockService.getStock(symbol)

            if(quote == null) {
                call.respond(HttpStatusCode.NotFound,
                    ApiError("STOCK_NOT_FOUND", "No quote was found for this symbol."))
                return@get
            }
            call.respond(quote)
        }
    }
}

fun Route.marketRoutes(stockService: StockService) {
    route("/api/v1/market") {
        get("/gainers") { call.respond(stockService.getGainers()) }
        get("/losers") { call.respond(stockService.getLosers()) }
    }
}


fun Route.newsRoutes(newsService: NewsService) {
    get("/api/v1/stocks/{symbol}/news") {
        val symbol = call.parameters["symbol"]?.uppercase(Locale.ROOT)
        if (symbol == null || !Regex("[A-Z0-9][A-Z0-9.-]{0,19}").matches(symbol)) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_SYMBOL", "Provide a valid stock symbol."))
            return@get
        }
        call.respond(newsService.getCompanyNews(symbol))
    }
    get("/api/v1/news") {
        val pageValue = call.request.queryParameters["page"]
        val limitValue = call.request.queryParameters["limit"]
        val page = if (pageValue == null) 0 else pageValue.toIntOrNull()
        val limit = if (limitValue == null) 20 else limitValue.toIntOrNull()
        if (page == null || page !in 0..100 || limit == null || limit !in 1..100) {
            call.respond(HttpStatusCode.BadRequest,
                ApiError("INVALID_NEWS_QUERY", "Page must be 0–100 and limit must be 1–100."))
            return@get
        }
        call.respond(newsService.getNews(page, limit))
    }
}

fun Route.marketSnapshotRoutes(service: org.example.stocksteps.service.MarketSnapshotService) {
    get("/market/snapshot") { call.respond(service.getSnapshot()) }
    get("/api/v1/market/snapshot") { call.respond(service.getSnapshot()) }
}

fun Route.sparklineRoutes(service: org.example.stocksteps.service.SparklineService) {
    get("/api/v1/stocks/{symbol}/sparkline") {
        val symbol = call.parameters["symbol"]?.uppercase(Locale.ROOT)
        if (symbol == null || !Regex("[A-Z0-9][A-Z0-9.-]{0,19}").matches(symbol)) {
            call.respond(HttpStatusCode.BadRequest,
                ApiError("INVALID_SYMBOL", "Use a stock symbol of 1–20 letters, digits, dots, or hyphens."))
            return@get
        }
        val sparkline = service.getSparkline(symbol)
        if (sparkline == null) {
            call.respond(HttpStatusCode.NotFound, ApiError("SPARKLINE_NOT_FOUND", "No recent price history is available for this symbol."))
            return@get
        }
        call.respond(sparkline)
    }
}

/** Every external data dependency, chosen once per process by [DataMode]. */
internal class DataSources(
    val stockProvider: StockProviderRepository,
    val quoteProvider: StockQuoteProviderRepository,
    val marketData: MarketDataProvider,
    val priceHistory: PriceHistoryProvider,
    val news: NewsService,
    /** Null until the real explanation pipeline exists; the endpoint then reports "unavailable". */
    val whyMoving: org.example.stocksteps.service.WhyMovingSource? = null,
    /** Reported quarterly EPS for the historical P/E series. */
    val earnings: org.example.stocksteps.service.QuarterlyEarningsSource = org.example.stocksteps.service.QuarterlyEarningsSource { emptyList() }
)

private fun Application.realDataSources(): DataSources {
    val fmpRepository = FmpStockProviderRepositoryImpl(
        client = HttpClientProvider.client,
        apiKey = AppConfig.fmpApiKey
    )
    val quoteProvider = when (System.getenv("QUOTE_PROVIDER")?.lowercase(Locale.ROOT) ?: "fmp") {
        "fmp" -> fmpRepository
        "finnhub" -> FinnhubStockProviderRepositoryImpl(
            client = HttpClientProvider.client,
            apiKey = AppConfig.finnhubApiKey
        )
        else -> error("QUOTE_PROVIDER must be fmp or finnhub")
    }
    return DataSources(
        stockProvider = fmpRepository,
        quoteProvider = quoteProvider,
        marketData = org.example.stocksteps.repositoryImpl.FmpMarketDataProvider(
            HttpClientProvider.client,
            AppConfig.fmpApiKey,
            FinnhubStockProviderRepositoryImpl(HttpClientProvider.client, AppConfig.finnhubApiKey)
        ),
        priceHistory = org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider(HttpClientProvider.client, AppConfig.fmpApiKey),
        news = createNewsService(HttpClientProvider.client),
        earnings = fmpRepository
    )
}

/** Captured fixtures plus sample values for gaps: no provider keys, network calls, Gemini or Firestore. */
internal fun mockDataSources(): DataSources {
    val fixtures = FixtureMarketDataSource(sampleFallback = true)
    return DataSources(fixtures, fixtures, fixtures, fixtures, NewsService(fixtures, simplification = null), whyMoving = fixtures, earnings = fixtures)
}

/** `GET /api/v1/stocks/{symbol}/valuation`: the full P/E history; ranges are sliced by the apps. */
fun Route.valuationRoutes(valuation: org.example.stocksteps.service.ValuationService) {
    get("/api/v1/stocks/{symbol}/valuation") {
        val symbol = call.validSymbol() ?: return@get
        call.respond(valuation.history(symbol))
    }
}

fun Route.companyDetailsRoutes(
    details: org.example.stocksteps.service.CompanyDetailsService,
    charts: org.example.stocksteps.service.PriceChartService,
    whyMoving: org.example.stocksteps.service.WhyMovingService
) {
    route("/api/v1/stocks/{symbol}") {
        get("/details") {
            val symbol = call.validSymbol() ?: return@get
            call.respond(details.getDetails(symbol))
        }
        get("/chart") {
            val symbol = call.validSymbol() ?: return@get
            val range = ChartRange.parse(call.request.queryParameters["range"] ?: "1M")
            if (range == null) {
                call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_RANGE", "Range must be 1D, 1W, 1M, 3M, 1Y, 5Y or ALL."))
                return@get
            }
            val chart = charts.getChart(symbol, range)
            if (chart == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("CHART_NOT_FOUND", "Price history is not available for this range."))
                return@get
            }
            call.respond(chart)
        }
        get("/why-moving") {
            val symbol = call.validSymbol() ?: return@get
            val explanation = whyMoving.getWhyMoving(symbol)
            if (explanation == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("WHY_MOVING_UNAVAILABLE", "An explanation for this move is not available yet."))
                return@get
            }
            call.respond(explanation)
        }
    }
}

/** Same symbol rule as the other stock routes; responds 400 and returns null when invalid. */
private suspend fun io.ktor.server.application.ApplicationCall.validSymbol(): String? {
    val symbol = parameters["symbol"]?.uppercase(Locale.ROOT)
    if (symbol == null || !Regex("[A-Z0-9][A-Z0-9.-]{0,19}").matches(symbol)) {
        respond(HttpStatusCode.BadRequest, ApiError("INVALID_SYMBOL", "Use a stock symbol of 1–20 letters, digits, dots, or hyphens."))
        return null
    }
    return symbol
}
