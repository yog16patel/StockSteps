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

    /** Financial API Phase 4A: the backend accepts ≤ 30 symbols per anonymous watch-data request, so larger watchlists are split and merged. */
    @Test
    fun watchDataIsRequestedInChunksOfThirtyAndMerged() = runBlocking<Unit> {
        val session = """{"market":"US","status":"OPEN","timezone":"America/New_York","asOf":"2026-10-07T14:00:00Z","sessionDate":"2026-10-07","utcOffsetMinutes":-240,"source":"c"}"""
        val sizes = mutableListOf<Int>()
        HttpClient(MockEngine { request ->
            val symbols = request.url.parameters["symbols"]!!.split(',')
            sizes += symbols.size
            val quotes = symbols.joinToString(",") { """{"symbol":"$it","price":1.0}""" }
            respond("""{"quotes":[$quotes],"session":$session,"generatedAt":"2026-10-07T14:00:00Z","notice":"n"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }.use {
            val api = StockStepsApi(it, "https://stocksteps.test")
            val symbols = (1..75).map { i -> "S$i" } + listOf("S1", "S2")
            val response = api.getWatchData(symbols.take(100))
            assertEquals(listOf(30, 30, 15), sizes, "duplicates removed, ≤ $WATCH_DATA_CHUNK per request")
            assertEquals(75, response.quotes.size)
            assertEquals((1..75).map { i -> "S$i" }, response.quotes.map { q -> q.symbol }, "order preserved")
            sizes.clear()
            api.getWatchData(listOf("AAPL"))
            assertEquals(listOf(1), sizes, "small watchlists are still one request")
        }
    }

    /** Phase 4A error codes reach older and newer clients through the existing ApiError shape. */
    @Test
    fun phase4ErrorCodesUseTheExistingErrorShape() = runBlocking<Unit> {
        for ((status, code) in listOf(HttpStatusCode.NotFound to "SYMBOL_NOT_FOUND", HttpStatusCode.TooManyRequests to "RATE_LIMITED",
            HttpStatusCode.BadRequest to "TOO_MANY_SYMBOLS", HttpStatusCode.PayloadTooLarge to "PAYLOAD_TOO_LARGE")) {
            HttpClient(MockEngine {
                respond("""{"code":"$code","message":"m"}""", status, headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("12")))
            }) { configureStockStepsClient() }.use {
                val error = assertFailsWith<StockStepsApiException> { StockStepsApi(it, "https://stocksteps.test").getQuote("ZZZZ") }
                assertEquals(code, error.error.code); assertEquals(status.value, error.status)
            }
        }
    }
}
