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
import org.example.stocksteps.screener.comparisonHistoryRoutes
import org.example.stocksteps.screener.comparisonResearchRoutes
import org.example.stocksteps.screener.comparisonAiRoutes
import org.example.stocksteps.service.marketStatusAt
import org.example.stocksteps.service.usageMetricsRoutes
import org.example.stocksteps.service.ProviderUsageMeter
import org.example.stocksteps.screener.RequestRateLimiter
import org.example.stocksteps.screener.SavedScreensService
import org.example.stocksteps.screener.savedScreenRoutes
import org.example.stocksteps.screener.screenerRoutes
import org.example.stocksteps.earnings.earningsRoutes
import org.example.stocksteps.earnings.earningsReminderRoutes
import org.example.stocksteps.earnings.earningsPremiumRoutes
import org.example.stocksteps.learning.learningRoutes
import org.example.stocksteps.practice.practiceRoutes
import org.example.stocksteps.brief.dailyBriefRoutes
import org.example.stocksteps.userdata.ChartBenchmarkHistory
import org.example.stocksteps.userdata.MockBenchmarkHistory
import org.example.stocksteps.userdata.PortfolioAnalyticsService
import org.example.stocksteps.userdata.portfolioAnalyticsRoutes
import org.example.stocksteps.userdata.WatchlistsService
import org.example.stocksteps.userdata.AlertsService
import org.example.stocksteps.userdata.watchDataRoutes
import org.example.stocksteps.userdata.portfolioRoutes
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
        val portfolioClock = if (dataMode == DataMode.MOCK) java.time.Clock.systemUTC() else sources.marketClock
        val entitlements = org.example.stocksteps.userdata.EntitlementService(userData, portfolioClock::millis, debugAllowed = dataMode == DataMode.MOCK)
        // One earnings source per environment feeds the calendar, details, watch-data, Home and reminders.
        val earnings = org.example.stocksteps.earnings.EarningsService(
            source = sources.earningsData ?: org.example.stocksteps.earnings.FinnhubEarningsDataSource(HttpClientProvider.client, AppConfig.finnhubApiKey),
            stocks = stockService, charts = charts, store = userData, entitlements = entitlements, clock = sources.marketClock,
            sampleData = dataMode == DataMode.MOCK,
            research = if (dataMode == DataMode.MOCK) org.example.stocksteps.earnings.TemplateEarningsResearch else null,
            aiDailyLimit = System.getenv("EARNINGS_AI_DAILY_LIMIT")?.toIntOrNull() ?: 20
        )
        val earningsCalendar = object : org.example.stocksteps.userdata.EarningsCalendarSource {
            override suspend fun upcoming(symbol: String) = earnings.next(symbol)
            override suspend fun recentResult(symbol: String) = earnings.recentResult(symbol)
        }
        earningsRoutes(earnings, sources.userAuth, RequestRateLimiter(System.getenv("EARNINGS_REQUESTS_PER_MINUTE")?.toIntOrNull() ?: 120))
        // Phase 5: StockSteps+ premium earnings. One fair-use quota for every earnings AI feature; MOCK never calls an AI service.
        val earningsAiQuota = org.example.stocksteps.earnings.EarningsAiQuotaLedger(mapOf(
            org.example.stocksteps.earnings.EarningsAiCategory.EXPLANATION to (System.getenv("EARNINGS_AI_EXPLANATIONS_PER_DAY")?.toIntOrNull() ?: 10),
            org.example.stocksteps.earnings.EarningsAiCategory.QUESTION to (System.getenv("EARNINGS_AI_QUESTIONS_PER_DAY")?.toIntOrNull() ?: System.getenv("EARNINGS_AI_DAILY_LIMIT")?.toIntOrNull() ?: 20),
            org.example.stocksteps.earnings.EarningsAiCategory.DIGEST to (System.getenv("EARNINGS_AI_DIGESTS_PER_DAY")?.toIntOrNull() ?: 3)),
            globalDailyBudget = System.getenv("EARNINGS_AI_GLOBAL_DAILY_BUDGET")?.toIntOrNull() ?: 5_000, clock = java.time.Clock.systemUTC())
        earnings.aiQuota = earningsAiQuota
        val earningsPremium = org.example.stocksteps.earnings.EarningsPremiumService(earnings, entitlements,
            provider = if (dataMode == DataMode.MOCK) org.example.stocksteps.earnings.TemplateEarningsAi()
                else AppConfig.geminiApiKey?.let { org.example.stocksteps.earnings.GeminiEarningsAi(HttpClientProvider.client, it, System.getenv("GEMINI_EARNINGS_MODEL") ?: AppConfig.geminiNewsModel) },
            quota = earningsAiQuota, clock = sources.marketClock, sampleData = dataMode == DataMode.MOCK,
            timeoutMillis = if (dataMode == DataMode.MOCK) 3_000 else 15_000)
        val earningsDigests = org.example.stocksteps.earnings.EarningsDigestService(userData, earnings, earningsPremium, sources.marketClock, sampleData = dataMode == DataMode.MOCK)
        earningsPremiumRoutes(earningsPremium, earningsDigests, sources.userAuth, RequestRateLimiter(System.getenv("EARNINGS_PREMIUM_REQUESTS_PER_MINUTE")?.toIntOrNull() ?: 30))
        val watchMarket = org.example.stocksteps.userdata.WatchMarketData(stockService, earningsCalendar, sources.news)
        val alertRules = org.example.stocksteps.userdata.AlertRules()
        val evaluator = org.example.stocksteps.userdata.AlertEvaluator(userData, watchMarket, sources.pushSender(), alertRules, sources.marketClock)
        // Earnings reminders (Phase 4): backend-scheduled; MOCK uses the simulated sender with debug token scenarios.
        val earningsReminders = org.example.stocksteps.earnings.EarningsReminderService(userData, earnings,
            if (dataMode == DataMode.MOCK) org.example.stocksteps.earnings.MockScenarioPushSender(sources.pushSender()) else sources.pushSender(),
            sources.marketClock, sampleData = dataMode == DataMode.MOCK, digests = earningsDigests)
        run {
            // One USD/CAD source (and one 6 h cache) for Portfolio, Practice and Comparison instead of three.
            val fx = if (dataMode == DataMode.MOCK) org.example.stocksteps.userdata.MockPortfolioFx else org.example.stocksteps.userdata.BankOfCanadaPortfolioFx(HttpClientProvider.client)
            watchDataRoutes(WatchDataService(watchMarket, alertRules, org.example.stocksteps.service.UsMarketCalendar(), sources.marketClock,
                if (dataMode == DataMode.MOCK) "Sample data, not live prices." else "Quotes may be delayed. Times show when each price was last updated."))
            val portfolios = org.example.stocksteps.userdata.PortfolioService(userData, portfolioClock::millis)
            val portfolioMarket = org.example.stocksteps.userdata.PortfolioMarketService(
                portfolios, sources.priceHistory, watchMarket,
                fx,
                portfolioClock, dailyCloses = charts::getDailyCloses)
            portfolioRoutes(sources.userAuth, portfolios, portfolioMarket)
            portfolioAnalyticsRoutes(sources.userAuth, PortfolioAnalyticsService(
                portfolioMarket, watchMarket,
                sources.indexData?.takeIf { dataMode == DataMode.MOCK }?.let { MockBenchmarkHistory(it) } ?: ChartBenchmarkHistory(charts::getDailyCloses),
                entitlements, portfolioClock, sampleData = dataMode == DataMode.MOCK), entitlements)
            // One FX source for comparisons (its 6 h cache is reused). The rate's date and source are disclosed to users.
            val comparisonFx = fx
            suspend fun latestUsdPerCad(): org.example.stocksteps.screener.FxConversion? {
                val today = java.time.LocalDate.now(sources.marketClock.withZone(java.time.ZoneOffset.UTC))
                val (date, usdCad) = runCatching { comparisonFx.rates(today.minusDays(10).toString(), today.toString()) }.getOrNull()
                    ?.maxByOrNull { it.key }?.let { it.key to it.value.toDoubleOrNull() } ?: return null
                return usdCad?.takeIf { it > 0 }?.let { org.example.stocksteps.screener.FxConversion("CAD", "USD", 1 / it, date,
                    if (dataMode == DataMode.MOCK) "fixed sample rate" else "Bank of Canada") }
            }
            val screener = org.example.stocksteps.screener.ScreenerService(
                universe = sources.screenerUniverse ?: org.example.stocksteps.screener.FmpScreenerUniverse(HttpClientProvider.client, AppConfig.fmpApiKey,
                    exchanges = (System.getenv("SCREENER_EXCHANGES") ?: "NASDAQ,NYSE,TSX").split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    limit = System.getenv("SCREENER_UNIVERSE_LIMIT")?.toIntOrNull() ?: 100,
                    minMarketCap = System.getenv("SCREENER_MIN_MARKET_CAP")?.toLongOrNull() ?: 2_000_000_000L),
                stocks = stockService,
                fundamentalsOf = org.example.stocksteps.service.CompanyFinancialService(sources.stockProvider).let { service -> { symbol: String -> service.getFundamentals(symbol, "annual") } },
                charts = charts,
                usdPerCad = { latestUsdPerCad()?.rate },
                // Phase 2 explanations disclose the rate behind converted market caps (same source, cached 6 h in REAL).
                usdPerCadQuote = { latestUsdPerCad() },
                clock = sources.marketClock,
                sampleData = dataMode == DataMode.MOCK,
                fundamentalsPerHour = System.getenv("SCREENER_FUNDAMENTALS_PER_HOUR")?.toIntOrNull() ?: 25,
                fullRecords = dataMode == DataMode.MOCK,
                // Company Comparison: latest-quarter revenue growth reuses Earnings Results (no extra statement requests).
                quarterlyRevenueGrowth = { symbol ->
                    try { org.example.stocksteps.screener.quarterRevenueGrowth(earnings.latestResults(symbol)) }
                    catch (cause: org.example.stocksteps.earnings.EarningsRequestException) {
                        if (cause.status == 404 || cause.status == 400) org.example.stocksteps.screener.MetricValue(note = "No published quarterly results are available for this company.")
                        else throw cause
                    }
                },
                provenance = if (dataMode == DataMode.MOCK) listOf("Sample fixture data for development (quotes, profiles, financial statements, quarterly results and prices); not live market data.")
                    else listOf("Quotes, company profiles, financial statements and price history: Financial Modeling Prep. Quarterly results: the earnings data provider (Finnhub by default).")
            )
            screenerRoutes(screener, RequestRateLimiter(System.getenv("SCREENER_REQUESTS_PER_MINUTE")?.toIntOrNull() ?: 60))
            savedScreenRoutes(sources.userAuth, SavedScreensService(userData, entitlements, sources.marketClock::millis))
            // Company Comparison Phase 3: free 1Y quarterly history; 3Y/5Y and advanced metrics verified as StockSteps+ on the server.
            val comparisonHistory = org.example.stocksteps.screener.ComparisonHistoryService(
                stocks = stockService,
                fundamentalsOf = org.example.stocksteps.service.CompanyFinancialService(sources.stockProvider).let { service -> { symbol: String, period: String -> service.getFundamentals(symbol, period) } },
                entitlements = entitlements,
                clock = sources.marketClock,
                sampleData = dataMode == DataMode.MOCK,
                source = if (dataMode == DataMode.MOCK) "Sample fixture financial statements (MOCK)" else "Financial Modeling Prep income statements (reported, as filed with regulators)",
                budget = org.example.stocksteps.service.ProviderRequestBudget.fromEnvironment(listOf("comparison-history", "comparison-research"))
            )
            comparisonHistoryRoutes(comparisonHistory, sources.userAuth, RequestRateLimiter(System.getenv("SCREENER_REQUESTS_PER_MINUTE")?.toIntOrNull() ?: 60))
            // Company Comparison Phase 4: research checklist sessions (Firestore in REAL); summaries/exports reuse the comparison and history caches.
            comparisonResearchRoutes(org.example.stocksteps.screener.ComparisonResearchService(
                store = userData, entitlements = entitlements, comparison = { symbols -> screener.compare(symbols.joinToString(",")) },
                history = comparisonHistory, clock = sources.marketClock,
                plusLimit = System.getenv("RESEARCH_PLUS_SESSION_LIMIT")?.toIntOrNull()?.takeIf { it > 0 } ?: 100
            ), sources.userAuth, RequestRateLimiter(System.getenv("RESEARCH_REQUESTS_PER_MINUTE")?.toIntOrNull() ?: 120))
            // Company Comparison Phase 5: StockSteps+ AI assistant. MOCK = deterministic templates (no Gemini); REAL = Gemini only when a key is set.
            // Context reuses the comparison/history caches; quotas are durable (users/{uid}/meta/aiUsage in Firestore).
            comparisonAiRoutes(org.example.stocksteps.screener.ComparisonAiService(
                comparison = { symbols -> screener.compare(symbols.joinToString(",")) },
                history = comparisonHistory, store = userData, entitlements = entitlements,
                provider = if (dataMode == DataMode.MOCK) org.example.stocksteps.screener.TemplateComparisonAi()
                    else AppConfig.geminiApiKey?.let { org.example.stocksteps.screener.GeminiComparisonAi(HttpClientProvider.client, it, System.getenv("GEMINI_COMPARISON_MODEL") ?: AppConfig.geminiNewsModel) },
                quota = org.example.stocksteps.screener.ComparisonAiQuota(userData, sources.marketClock,
                    dailyLimit = System.getenv("COMPARISON_AI_DAILY_LIMIT")?.toIntOrNull()?.takeIf { it > 0 } ?: 10,
                    windowLimit = System.getenv("COMPARISON_AI_30_DAY_LIMIT")?.toIntOrNull()?.takeIf { it > 0 } ?: 50,
                    globalDailyBudget = System.getenv("COMPARISON_AI_GLOBAL_DAILY_BUDGET")?.toIntOrNull()?.takeIf { it > 0 } ?: 2_000),
                clock = sources.marketClock, sampleData = dataMode == DataMode.MOCK,
                source = if (dataMode == DataMode.MOCK) "Sample fixture data (MOCK)" else "Financial Modeling Prep; quarterly results from the earnings data provider",
                timeoutMillis = if (dataMode == DataMode.MOCK) 3_000 else 20_000,
                pricing = org.example.stocksteps.screener.AiPricing.fromEnvironment()
            ), sources.userAuth, RequestRateLimiter(System.getenv("COMPARISON_AI_REQUESTS_PER_MINUTE")?.toIntOrNull() ?: 20))
            usageMetricsRoutes(ProviderUsageMeter.shared, System.getenv("ALERTS_EVALUATOR_TOKEN")?.takeIf { it.length >= 32 }, mock = dataMode == DataMode.MOCK)
            userRoutes(sources.userAuth, WatchlistsService(userData, now = sources.marketClock::millis),
                AlertsService(userData, watchMarket, alertRules, sources.alertsDeliveryNote, now = sources.marketClock::millis, isPlus = { entitlements.get(it).plus }), userData, now = sources.marketClock::millis)
            // Practice Portfolio: a separate simulated ledger (never mixed with the real portfolio).
            practiceRoutes(sources.userAuth, org.example.stocksteps.practice.PracticeService(
                store = userData, entitlements = entitlements,
                market = org.example.stocksteps.practice.WatchPracticeMarket(watchMarket, charts::getDailyCloses),
                fx = fx,
                clock = java.time.Clock.systemUTC(), marketClock = sources.marketClock,
                corporateActions = if (dataMode == DataMode.MOCK) org.example.stocksteps.practice.MockCorporateActions else null,
                sampleData = dataMode == DataMode.MOCK
            ), mock = dataMode == DataMode.MOCK)
            alertEvaluationRoutes(evaluator, System.getenv("ALERTS_EVALUATOR_TOKEN")?.takeIf { it.length >= 32 }, mock = dataMode == DataMode.MOCK)
            earningsReminderRoutes(earningsReminders, sources.userAuth,
                RequestRateLimiter(System.getenv("REMINDER_REQUESTS_PER_MINUTE")?.toIntOrNull() ?: 60),
                System.getenv("ALERTS_EVALUATOR_TOKEN")?.takeIf { it.length >= 32 }, mock = dataMode == DataMode.MOCK)
        }
        if (dataMode == DataMode.MOCK) startMockAlertLoop(evaluator, earningsReminders)
        val marketsService = org.example.stocksteps.service.MarketsService(
            movers = sources.marketData,
            quotes = sources.stockProvider,
            indexData = sources.indexData ?: object : org.example.stocksteps.service.IndexDataSource {
                override suspend fun indexQuote(symbol: String) = sources.stockProvider.getQuote(symbol)
                override suspend fun indexHistory(symbol: String) = charts.getChart(symbol, ChartRange.ONE_MONTH)?.points.orEmpty()
            },
            news = sources.news,
            labels = sources.marketsLabels,
            clock = sources.marketClock
        )
        marketsRoutes(marketsService)
        // Daily Market Brief: one global brief per edition (from the Markets cache) plus per-user overlays.
        dailyBriefRoutes(org.example.stocksteps.brief.DailyBriefService(
            source = org.example.stocksteps.brief.MarketsBriefSource(marketsService),
            store = userData, entitlements = entitlements, watch = watchMarket, earnings = earnings,
            ai = if (dataMode == DataMode.MOCK) org.example.stocksteps.brief.TemplateBriefAi
                else AppConfig.geminiApiKey?.let { org.example.stocksteps.brief.GeminiBriefAi(HttpClientProvider.client, it, AppConfig.geminiNewsModel) },
            marketClock = sources.marketClock, clock = java.time.Clock.systemUTC(), sampleData = dataMode == DataMode.MOCK,
            aiDailyLimit = System.getenv("BRIEF_AI_DAILY_LIMIT")?.toIntOrNull() ?: 15
        ), sources.userAuth, RequestRateLimiter(System.getenv("BRIEF_REQUESTS_PER_MINUTE")?.toIntOrNull() ?: 120), sources.pushSender(),
            System.getenv("ALERTS_EVALUATOR_TOKEN")?.takeIf { it.length >= 32 }, mock = dataMode == DataMode.MOCK)
        sparklineRoutes(org.example.stocksteps.service.SparklineService(sources.priceHistory))
        val companyDetails = org.example.stocksteps.service.CompanyDetailsService(
            stocks = stockService,
            financials = org.example.stocksteps.service.CompanyFinancialService(sources.stockProvider),
            marketData = sources.marketData,
            valuation = valuation,
            // Computed from the exchange calendar: no FMP `exchange-market-hours` request per Company Details open.
            marketStatus = org.example.stocksteps.service.UsMarketCalendar().let { calendar -> { calendar.marketStatusAt(sources.marketClock.instant()) } }
        )
        // Guided Research: progress sync for signed-in users and the StockSteps+ research assistant.
        learningRoutes(sources.userAuth, org.example.stocksteps.learning.LearningService(
            store = userData, entitlements = entitlements, details = companyDetails::getDetails,
            research = if (dataMode == DataMode.MOCK) org.example.stocksteps.learning.TemplateResearchAi else null,
            aiDailyLimit = System.getenv("RESEARCH_AI_DAILY_LIMIT")?.toIntOrNull() ?: 20,
            clock = sources.marketClock
        ))
        companyDetailsRoutes(
            details = companyDetails,
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
    /** Earnings events: fixtures in MOCK; null in REAL, where Finnhub's earnings calendar is used. */
    val earningsData: org.example.stocksteps.earnings.EarningsDataSource? = null,
    /** The screener universe: fixture symbols in MOCK; null in REAL, where FMP's company screener defines it. */
    val screenerUniverse: org.example.stocksteps.screener.ScreenerUniverseSource? = null,
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
        screenerUniverse = org.example.stocksteps.screener.FixtureScreenerUniverse(),
        earningsData = org.example.stocksteps.earnings.FixtureEarningsDataSource(),
        marketsLabels = org.example.stocksteps.service.MarketsSourceLabels(
            movers = "Sample: movers captured from FMP's daily lists",
            quotes = "StockSteps sample fixtures",
            sampleData = true,
            notice = "Sample data captured ${java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(java.time.ZoneId.of("America/New_York")).format(pinned)}. Not live prices."
        ),
        // Mock time starts at the capture instant and advances, so sessions and alerts behave consistently.
        marketClock = java.time.Clock.offset(java.time.Clock.systemUTC(), java.time.Duration.between(java.time.Instant.now(), pinned)),
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
private fun Application.startMockAlertLoop(evaluator: org.example.stocksteps.userdata.AlertEvaluator, reminders: org.example.stocksteps.earnings.EarningsReminderService) {
    val seconds = System.getenv("STOCKSTEPS_MOCK_ALERT_SECONDS")?.toLongOrNull() ?: 60
    if (seconds <= 0) return
    val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch {
        while (true) {
            kotlinx.coroutines.delay(seconds * 1_000)
            runCatching { evaluator.run() }.onFailure { log.warn("Mock alert evaluation failed") }
            runCatching { reminders.runPass() }.onFailure { log.warn("Mock earnings reminder pass failed") }
        }
    }
    monitor.subscribe(ApplicationStopped) { job.cancel() }
}
