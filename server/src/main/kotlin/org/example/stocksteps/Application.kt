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
import kotlinx.coroutines.launch
import org.example.stocksteps.userdata.WatchDataService
import org.example.stocksteps.userdata.WatchlistsService
import org.example.stocksteps.userdata.AlertsService
import org.example.stocksteps.userdata.watchDataRoutes
import org.example.stocksteps.userdata.userRoutes
import org.example.stocksteps.userdata.alertEvaluationRoutes
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
        if (dataMode == DataMode.MOCK) homePersonaRoutes()
    }

    // Search stays with the stock provider; quotes can be selected independently.
    val stockService = StockService(
        stockProvider = sources.stockProvider,
        quoteProvider = sources.quoteProvider
    )

    // One chart service so the price chart and the valuation history share cached daily closes.
    val charts = org.example.stocksteps.service.PriceChartService(sources.priceHistory)
    val valuation = org.example.stocksteps.service.ValuationService(sources.stockProvider, charts, sources.earnings)
    val movement = org.example.stocksteps.service.MovementService(
        stocks = stockService, charts = charts, news = sources.news,
        narrator = sources.narrator, version = sources.movementVersion
    )
    routing {
        stockRoutes(stockService)
        valuationRoutes(valuation)
        companyFinancialRoutes(org.example.stocksteps.service.CompanyFinancialService(sources.stockProvider))
        marketRoutes(stockService)
        marketSnapshotRoutes(org.example.stocksteps.service.MarketSnapshotService(sources.marketData))
        newsRoutes(sources.news)
        newsInsightRoutes(org.example.stocksteps.news.ArticleInsightService(
            sources.news, sources.insights, sources.insightVersion,
            timeoutMillis = if (dataMode == DataMode.MOCK) 2_000 else 10_000
        ))
        movementRoutes(movement)
        val userData = sources.userData()
        val watchMarket = org.example.stocksteps.userdata.WatchMarketData(stockService, sources.earningsCalendar, sources.news)
        val alertRules = org.example.stocksteps.userdata.AlertRules()
        val evaluator = org.example.stocksteps.userdata.AlertEvaluator(userData, watchMarket, sources.pushSender(), alertRules, sources.marketClock)
        run {
            watchDataRoutes(WatchDataService(watchMarket, alertRules, org.example.stocksteps.service.UsMarketCalendar(), sources.marketClock,
                if (dataMode == DataMode.MOCK) "Sample data, not live prices." else "Quotes may be delayed. Times show when each price was last updated."))
            userRoutes(sources.userAuth, WatchlistsService(userData, now = sources.marketClock::millis),
                AlertsService(userData, watchMarket, alertRules, sources.alertsDeliveryNote, now = sources.marketClock::millis), userData, now = sources.marketClock::millis)
            alertEvaluationRoutes(evaluator, System.getenv("ALERTS_EVALUATOR_TOKEN")?.takeIf { it.length >= 32 }, mock = dataMode == DataMode.MOCK)
        }
        if (dataMode == DataMode.MOCK) startMockAlertLoop(evaluator)
        marketsRoutes(org.example.stocksteps.service.MarketsService(
            movers = sources.marketData,
            quotes = sources.stockProvider,
            indexData = sources.indexData ?: object : org.example.stocksteps.service.IndexDataSource {
                override suspend fun indexQuote(symbol: String) = sources.stockProvider.getQuote(symbol)
                override suspend fun indexHistory(symbol: String) = charts.getChart(symbol, ChartRange.ONE_MONTH)?.points.orEmpty()
            },
            news = sources.news,
            labels = sources.marketsLabels,
            clock = sources.marketClock
        ))
        sparklineRoutes(org.example.stocksteps.service.SparklineService(sources.priceHistory))
        companyDetailsRoutes(
            details = org.example.stocksteps.service.CompanyDetailsService(
                stocks = stockService,
                financials = org.example.stocksteps.service.CompanyFinancialService(sources.stockProvider),
                marketData = sources.marketData,
                valuation = valuation
            ),
            charts = charts,
            // The Company Details card previews today's computed movement (same pipeline in both modes).
            whyMoving = org.example.stocksteps.service.WhyMovingService { movement.preview(it) }
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
        val query = call.request.queryParameters
        val categoryValue = query["category"]?.uppercase(Locale.ROOT)
        val category = categoryValue?.let { value -> org.example.stocksteps.model.NewsCategory.entries.firstOrNull { it.name == value } }
        val page = query["page"]?.let { it.toIntOrNull() ?: -1 } ?: 0
        val limit = query["limit"]?.let { it.toIntOrNull() ?: -1 } ?: NewsService.MAX_LIMIT
        if ((categoryValue != null && categoryValue != "ALL" && category == null) || page !in 0..20 || limit !in 1..NewsService.MAX_LIMIT) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_NEWS_QUERY",
                "Category must be ALL, EARNINGS, PRODUCTS, BUSINESS, REGULATION, ANALYST or OTHER; page 0–20; limit 1–${NewsService.MAX_LIMIT}."))
            return@get
        }
        // Home reads cached provider facts without invoking AI simplification.
        call.respond(newsService.getCompanyNews(symbol, category, page, limit, enrich = query["enrich"] != "false"))
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
    /** Article explanations: Gemini in REAL (when configured), deterministic templates in MOCK. */
    val insights: org.example.stocksteps.news.ArticleInsightGenerator? = null,
    val insightVersion: String = "none",
    /** Optional AI wording of computed movement facts; null keeps the deterministic template. */
    val narrator: org.example.stocksteps.service.MovementNarrator? = null,
    val movementVersion: String = "movement-template-v1",
    /** Index levels; null in REAL, where the module builds one from the stock provider and charts. */
    val indexData: org.example.stocksteps.service.IndexDataSource? = null,
    val marketsLabels: org.example.stocksteps.service.MarketsSourceLabels = org.example.stocksteps.service.MarketsSourceLabels(
        movers = "US-listed stocks in FMP's daily market movers lists",
        quotes = "Financial Modeling Prep",
        sampleData = false,
        notice = "Quotes come from Financial Modeling Prep and may be delayed. Times show when each value was last updated."
    ),
    /** The instant used for the market session and alerts (MOCK starts it at the fixture capture time). */
    val marketClock: java.time.Clock = java.time.Clock.systemUTC(),
    /** Watchlists, notes, alerts, outbox and devices: Firestore in REAL, process memory in MOCK. */
    val userData: () -> org.example.stocksteps.userdata.UserDataStore = { org.example.stocksteps.userdata.InMemoryUserDataStore() },
    val userAuth: org.example.stocksteps.userdata.UserAuthenticator = org.example.stocksteps.userdata.MockUserAuthenticator(),
    val pushSender: () -> org.example.stocksteps.userdata.PushSender = { org.example.stocksteps.userdata.SimulatedPushSender() },
    val earningsCalendar: org.example.stocksteps.userdata.EarningsCalendarSource? = null,
    val alertsDeliveryNote: String = "Alerts are checked about every 15 minutes during US market hours using quotes that may be delayed. They aren't real-time.",
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
        earnings = fmpRepository,
        insights = AppConfig.geminiApiKey?.let { org.example.stocksteps.news.GeminiArticleInsightGenerator(HttpClientProvider.client, it, AppConfig.geminiNewsModel) },
        insightVersion = "${AppConfig.geminiNewsModel}:${org.example.stocksteps.news.GeminiArticleInsightGenerator.PROMPT_VERSION}",
        narrator = AppConfig.geminiApiKey?.let { org.example.stocksteps.news.GeminiMovementNarrator(HttpClientProvider.client, it, AppConfig.geminiNewsModel) },
        userData = {
            // Without Google credentials (e.g. a local REAL run without ADC) market data still works;
            // watchlist/alert requests answer 503 instead of the server failing to start.
            try {
                org.example.stocksteps.userdata.FirestoreUserDataStore(
                    com.google.cloud.firestore.FirestoreOptions.getDefaultInstance().toBuilder()
                        .setProjectId(AppConfig.newsFirestoreProject)
                        .setDatabaseId(AppConfig.newsFirestoreDatabase)
                        .build().service
                )
            } catch (cause: Exception) {
                log.warn("User data storage unavailable (Google credentials not found); watchlists and alerts are disabled.")
                org.example.stocksteps.userdata.UnavailableUserDataStore
            }
        },
        userAuth = org.example.stocksteps.userdata.FirebaseIdTokenAuthenticator(AppConfig.firebaseProjectId),
        pushSender = {
            // Without Application Default Credentials, events are recorded and delivery is marked failed.
            runCatching { org.example.stocksteps.userdata.FcmPushSender(HttpClientProvider.client, AppConfig.firebaseProjectId) }
                .getOrElse { org.example.stocksteps.userdata.PushSender { org.example.stocksteps.userdata.PushResult.Failed("Push credentials not configured") } }
        },
        earningsCalendar = org.example.stocksteps.userdata.FinnhubEarningsCalendar(HttpClientProvider.client, AppConfig.finnhubApiKey) {
            java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
        },
        movementVersion = AppConfig.geminiApiKey?.let { "${AppConfig.geminiNewsModel}:${org.example.stocksteps.news.GeminiMovementNarrator.PROMPT_VERSION}" } ?: "movement-template-v1"
    )
}

/** Captured fixtures plus sample values for gaps: no provider keys, network calls, Gemini or Firestore. */
internal fun mockDataSources(): DataSources {
    val fixtures = FixtureMarketDataSource(sampleFallback = true)
    // STOCKSTEPS_MOCK_CLOCK (ISO instant) lets developers view other sessions (premarket, holiday…).
    val pinned = System.getenv("STOCKSTEPS_MOCK_CLOCK")?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
        ?: fixtures.capturedAt ?: java.time.Instant.EPOCH
    return DataSources(fixtures, fixtures, fixtures, fixtures, NewsService(fixtures, simplification = null), earnings = fixtures,
        insights = org.example.stocksteps.news.TemplateArticleInsightGenerator(), insightVersion = "mock-template-v1",
        indexData = fixtures,
        marketsLabels = org.example.stocksteps.service.MarketsSourceLabels(
            movers = "Sample: movers captured from FMP's daily lists",
            quotes = "StockSteps sample fixtures",
            sampleData = true,
            notice = "Sample data captured ${java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(java.time.ZoneId.of("America/New_York")).format(pinned)}. Not live prices."
        ),
        // Mock time starts at the capture instant and advances, so sessions and alerts behave consistently.
        marketClock = java.time.Clock.offset(java.time.Clock.systemUTC(), java.time.Duration.between(java.time.Instant.now(), pinned)),
        earningsCalendar = fixtures,
        alertsDeliveryNote = "Sample mode: alerts are checked every minute against sample prices. Notifications are simulated on the server, not sent to your device.")
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

private val ARTICLE_ID = Regex("[A-Za-z0-9:_-]{1,128}")

/** `GET /api/v1/stocks/{symbol}/news/{articleId}/insight`: on-demand, cached beginner explanation. */
fun Route.newsInsightRoutes(insights: org.example.stocksteps.news.ArticleInsightService) {
    get("/api/v1/stocks/{symbol}/news/{articleId}/insight") {
        val symbol = call.validSymbol() ?: return@get
        val articleId = call.parameters["articleId"]
        if (articleId == null || !ARTICLE_ID.matches(articleId)) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_ARTICLE", "Provide a valid article id."))
            return@get
        }
        val insight = insights.insight(symbol, articleId)
        if (insight == null) {
            call.respond(HttpStatusCode.NotFound, ApiError("ARTICLE_NOT_FOUND", "This article is no longer in the company's recent news."))
            return@get
        }
        call.respond(insight)
    }
}

/** `GET /api/v1/stocks/{symbol}/movement?period=1D|1W|1M`: computed move, benchmarks and time-aligned news. */
fun Route.movementRoutes(movement: org.example.stocksteps.service.MovementService) {
    get("/api/v1/stocks/{symbol}/movement") {
        val symbol = call.validSymbol() ?: return@get
        val period = org.example.stocksteps.model.MovementPeriod.parse(call.request.queryParameters["period"] ?: "1D")
        if (period == null) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_PERIOD", "Period must be 1D, 1W or 1M."))
            return@get
        }
        val explanation = movement.explain(symbol, period)
        if (explanation == null) {
            call.respond(HttpStatusCode.NotFound, ApiError("MOVEMENT_UNAVAILABLE", "Price data for this period isn't available yet."))
            return@get
        }
        call.respond(explanation)
    }
}

/** `GET /api/v1/markets/overview`: the Markets dashboard (session, indices, movers, sectors, news). */
fun Route.marketsRoutes(markets: org.example.stocksteps.service.MarketsService) {
    get("/api/v1/markets/overview") { call.respond(markets.overview()) }
}

/**
 * MOCK only: evaluates alerts every STOCKSTEPS_MOCK_ALERT_SECONDS (default 60; 0 disables) so sample
 * alerts can trigger locally. REAL never runs a background loop: Cloud Run can stop idle instances,
 * so Cloud Scheduler calls `POST /internal/alerts/evaluate` instead.
 */
private fun Application.startMockAlertLoop(evaluator: org.example.stocksteps.userdata.AlertEvaluator) {
    val seconds = System.getenv("STOCKSTEPS_MOCK_ALERT_SECONDS")?.toLongOrNull() ?: 60
    if (seconds <= 0) return
    val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch {
        while (true) {
            kotlinx.coroutines.delay(seconds * 1_000)
            runCatching { evaluator.run() }.onFailure { log.warn("Mock alert evaluation failed") }
        }
    }
    monitor.subscribe(ApplicationStopped) { job.cancel() }
}
