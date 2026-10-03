package org.example.stocksteps

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.repository.models.FmpQuote
import org.example.stocksteps.repository.models.toStockQuote
import org.example.stocksteps.service.StockService
import kotlin.test.*

class ApplicationTest {
    @Test
    fun quoteRouteReturnsPublicModelAndNotFound() = testApplication {
        val quote = FmpQuote(symbol = "AAPL", name = "Apple Inc.", price = 333.42).toStockQuote()
        val repository = object : StockProviderRepository {
            override suspend fun getGainers(): List<org.example.stocksteps.model.MarketMover> = error("Movers not expected")
            override suspend fun getLosers(): List<org.example.stocksteps.model.MarketMover> = error("Movers not expected")
            override suspend fun getProfile(symbol: String): org.example.stocksteps.model.CompanyProfile? = error("Profile not expected")
            override suspend fun searchStocks(query: String): List<org.example.stocksteps.model.StockSearchResult> =
                error("Search not expected")
            override suspend fun getQuote(symbol: String): StockQuote? =
                if (symbol == "AAPL") quote else null
        }
        application {
            install(ContentNegotiation) { json() }
            configureApiErrors()
            routing { stockRoutes(StockService(repository)) }
        }
        val response = client.get("/api/v1/stocks/aapl/quote")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(quote, Json.decodeFromString<StockQuote>(response.bodyAsText()))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/stocks/UNKNOWN/quote").status)
    }

    @Test
    fun errorsHaveStableJsonAndInvalidSymbolsSkipProvider() = testApplication {
        val repository = object : StockProviderRepository {
            override suspend fun getGainers(): List<org.example.stocksteps.model.MarketMover> = error("Movers not expected")
            override suspend fun getLosers(): List<org.example.stocksteps.model.MarketMover> = error("Movers not expected")
            override suspend fun getProfile(symbol: String): org.example.stocksteps.model.CompanyProfile? = error("Profile not expected")
            override suspend fun searchStocks(query: String): List<org.example.stocksteps.model.StockSearchResult> =
                error("Search not expected")
            override suspend fun getQuote(symbol: String): StockQuote? = when (symbol) {
                "TIMEOUT" -> throw org.example.stocksteps.repository.StockProviderException(
                    org.example.stocksteps.repository.StockProviderException.Failure.TIMEOUT)
                "LIMIT" -> throw org.example.stocksteps.repository.StockProviderException(
                    org.example.stocksteps.repository.StockProviderException.Failure.RATE_LIMITED)
                "DOWN" -> throw org.example.stocksteps.repository.StockProviderException(
                    org.example.stocksteps.repository.StockProviderException.Failure.UNAVAILABLE)
                "BAD" -> throw org.example.stocksteps.repository.StockProviderException(
                    org.example.stocksteps.repository.StockProviderException.Failure.INVALID_RESPONSE)
                "BUG" -> error("secret-api-key")
                "UNKNOWN" -> null
                else -> fail("Invalid symbol reached provider")
            }
        }
        application {
            install(ContentNegotiation) { json() }
            configureApiErrors()
            routing { stockRoutes(StockService(repository)) }
        }
        val cases = listOf(
            Triple("%24BAD", 400, "INVALID_SYMBOL"),
            Triple("UNKNOWN", 404, "STOCK_NOT_FOUND"),
            Triple("TIMEOUT", 504, "PROVIDER_TIMEOUT"),
            Triple("LIMIT", 503, "PROVIDER_RATE_LIMITED"),
            Triple("DOWN", 502, "PROVIDER_UNAVAILABLE"),
            Triple("BAD", 502, "INVALID_PROVIDER_RESPONSE"),
            Triple("BUG", 500, "INTERNAL_ERROR")
        )
        for ((symbol, status, code) in cases) {
            val response = client.get("/api/v1/stocks/$symbol/quote")
            assertEquals(status, response.status.value)
            val body = response.bodyAsText()
            assertEquals(code, Json.decodeFromString<org.example.stocksteps.model.ApiError>(body).code)
            assertFalse(body.contains("secret-api-key"))
        }
    }

    @Test
    fun mapsProviderFieldsAndFractionalVolume() {
        val actual = FmpQuote(
            symbol = "AAPL", name = "Apple Inc.", price = 333.42,
            change = 3.1, changePercentage = 0.93848, previousClose = 330.32,
            dayHigh = 334.0, dayLow = 330.61, volume = 20073601.00459,
            timestamp = 1700000000L
        ).toStockQuote()
        assertEquals(StockQuote(
            symbol = "AAPL", companyName = "Apple Inc.", price = 333.42,
            change = 3.1, changePercent = 0.93848, previousClose = 330.32,
            dayHigh = 334.0, dayLow = 330.61, volume = 20073601L,
            timestamp = 1700000000L
        ), actual)
    }

    @Test
    fun preservesMissingPriceAndRejectsInvalidVolume() {
        assertNull(FmpQuote(symbol = "AAPL").toStockQuote().price)
        assertNull(FmpQuote(symbol = "AAPL").toStockQuote().volume)
        for (volume in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Long.MAX_VALUE.toDouble())) {
            assertNull(FmpQuote(symbol = "AAPL", volume = volume).toStockQuote().volume)
        }
        assertEquals(0L, FmpQuote(symbol = "AAPL", volume = 0.0).toStockQuote().volume)
    }
}
