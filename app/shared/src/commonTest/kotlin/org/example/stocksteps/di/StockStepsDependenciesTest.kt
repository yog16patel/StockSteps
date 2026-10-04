package org.example.stocksteps.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.network.configureStockStepsClient
import kotlin.test.*

class StockStepsDependenciesTest {
    @Test fun resolvesSharedGraphAndReusesClient() = runBlocking<Unit> {
        var creations = 0
        val dependencies = StockStepsDependencies("https://stocksteps.test") {
            creations++
            HttpClient(MockEngine { request ->
                assertEquals("stocksteps.test", request.url.host)
                val body = if (request.url.encodedPath.endsWith("search")) "[]"
                    else """{"symbol":"AAPL","companyName":null,"price":150,"change":null,"changePercent":null,"dayHigh":null,"dayLow":null}"""
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureStockStepsClient() }
        }
        try {
            assertTrue(dependencies.searchStocks()("Apple").isEmpty())
            assertEquals(150.0, dependencies.getStockQuote()("AAPL").price)
            assertEquals(1, creations)
        } finally { dependencies.close() }
    }
}
