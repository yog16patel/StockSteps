package org.example.stocksteps.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class StockStepsApiTest {
    @Test
    fun decodesSearchAndQuoteAndSendsOnlyBackendRequests() = runBlocking<Unit> {
        HttpClient(MockEngine { request ->
            assertEquals("stocksteps.test", request.url.host)
            assertNull(request.url.parameters["apikey"])
            val body = when (request.url.encodedPath) {
                "/api/v1/stocks/search" -> {
                    assertEquals("Apple & Co", request.url.parameters["query"])
                    """[{"symbol":"AAPL","name":"Apple Inc.","currency":"USD","exchange":"NASDAQ","extra":"ignored"}]"""
                }
                "/api/v1/stocks/AAPL/quote" -> """{"symbol":"AAPL","companyName":null,"price":150.0,"change":-2.0,"changePercent":-1.3,"dayHigh":153.0,"dayLow":149.0}"""
                else -> error("Unexpected endpoint")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }.use {
            val api = StockStepsApi(it, "https://stocksteps.test/")
            assertEquals("AAPL", api.searchStocks(" Apple & Co ").single().symbol)
            assertEquals(150.0, api.getQuote("aapl").price)
        }
    }

    @Test
    fun exposesApiErrorsAndHidesNonJsonBodies() = runBlocking<Unit> {
        for ((body, expected) in listOf(
            """{"code":"STOCK_NOT_FOUND","message":"No quote was found for this symbol."}""" to "STOCK_NOT_FOUND",
            "secret raw upstream body" to "HTTP_ERROR"
        )) {
            HttpClient(MockEngine {
                respond(body, HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureStockStepsClient() }.use {
                val error = assertFailsWith<StockStepsApiException> {
                    StockStepsApi(it, "https://stocksteps.test").getQuote("AAPL")
                }
                assertEquals(404, error.status)
                assertEquals(expected, error.error.code)
                assertFalse(error.message.orEmpty().contains("secret"))
            }
        }
    }
}
