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
import org.example.stocksteps.httpclient.HttpClientProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.service.StockService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
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
        apiKey = System.getenv("FMP_API_KEY")
            ?: error("FMP_API_KEY is missing")
    )

    // 3. Create StockService
    val stockService = StockService(
        stockProvider = fmpRepository
    )

    routing {
        stockRoutes(stockService)
    }

}

fun Route.stockRoutes(stockService: StockService) {
    route("/api/v1/stocks") {
        get("/{symbol}/quote") {
            val symbol = call.parameters["symbol"]?.uppercase() ?: return@get call.respond(HttpStatusCode.BadRequest)

            val quote = stockService.getStock(symbol)

            if(quote == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(quote)
        }
    }
}