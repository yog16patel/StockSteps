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
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.httpclient.HttpClientProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.repositoryImpl.FinnhubStockProviderRepositoryImpl
import org.example.stocksteps.service.StockService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
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

    routing {
        get("/health") {
            call.respondText("StockSteps API is running")
        }
    }
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

    // Search stays with FMP; quotes can be selected independently.
    val stockService = StockService(
        stockProvider = fmpRepository,
        quoteProvider = quoteProvider
    )

    routing {
        stockRoutes(stockService)
        marketRoutes(stockService)
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
