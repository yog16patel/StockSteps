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
            override suspend fun getQuote(symbol: String): StockQuote? =
                if (symbol == "AAPL") quote else null
        }
        application {
            install(ContentNegotiation) { json() }
            routing { stockRoutes(StockService(repository)) }
        }
        val response = client.get("/api/v1/stocks/aapl/quote")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(quote, Json.decodeFromString<StockQuote>(response.bodyAsText()))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/stocks/UNKNOWN/quote").status)
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
