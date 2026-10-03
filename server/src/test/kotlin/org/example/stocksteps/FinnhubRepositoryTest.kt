package org.example.stocksteps

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderException.Failure
import org.example.stocksteps.repositoryImpl.FinnhubStockProviderRepositoryImpl
import kotlin.test.*

class FinnhubRepositoryTest {
    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        HttpClient(MockEngine { request ->
            assertEquals("/api/v1/quote", request.url.encodedPath)
            assertEquals("AAPL", request.url.parameters["symbol"])
            assertEquals("test-key", request.headers["X-Finnhub-Token"])
            assertNull(request.url.parameters["apikey"])
            assertNull(request.url.parameters["token"])
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }

    @Test
    fun serviceUsesSeparateQuoteProviderAndKeepsSearch() = runBlocking<Unit> {
        val listing = org.example.stocksteps.model.StockSearchResult("AAPL", "Apple Inc.", "USD")
        val searchProvider = object : org.example.stocksteps.repository.StockProviderRepository {
            override suspend fun searchStocks(query: String) = listOf(listing)
            override suspend fun getQuote(symbol: String): StockQuote? = error("FMP quote should not be used")
        }
        val expected = StockQuote("AAPL", null, 150.0, null, null, null, null)
        val quoteProvider = object : org.example.stocksteps.repository.StockQuoteProviderRepository {
            override suspend fun getQuote(symbol: String): StockQuote = expected
        }
        val service = org.example.stocksteps.service.StockService(searchProvider, quoteProvider)
        assertEquals(listOf(listing), service.searchStocks("apple"))
        assertEquals(expected, service.getStock("AAPL"))
    }

    @Test
    fun mapsObjectQuoteToPublicContract() = runBlocking<Unit> {
        client("""{"c":150.0,"d":-2.0,"dp":-1.3,"h":153.0,"l":149.0,"o":152.0,"pc":152.0,"t":1700000000}""").use {
            assertEquals(StockQuote("AAPL", null, 150.0, -2.0, -1.3, 153.0, 149.0,
                previousClose = 152.0, volume = null, timestamp = 1700000000),
                FinnhubStockProviderRepositoryImpl(it, "test-key").getQuote("AAPL"))
        }
    }

    @Test
    fun zeroNoDataReturnsNullButMalformedDataFails() = runBlocking<Unit> {
        client("""{"c":0,"d":null,"dp":null,"h":0,"l":0,"o":0,"pc":0,"t":0}""").use {
            assertNull(FinnhubStockProviderRepositoryImpl(it, "test-key").getQuote("AAPL"))
        }
        for (body in listOf("{}", "[]", "{broken",
            """{"c":-1,"h":1,"l":1,"o":1,"pc":1,"t":1700000000}""")) {
            client(body).use {
                assertEquals(Failure.INVALID_RESPONSE, assertFailsWith<StockProviderException> {
                    FinnhubStockProviderRepositoryImpl(it, "test-key").getQuote("AAPL")
                }.failure)
            }
        }
    }

    @Test
    fun handlesHttpErrorsWithoutLeakingBody() = runBlocking<Unit> {
        for ((status, failure) in listOf(
            HttpStatusCode.TooManyRequests to Failure.RATE_LIMITED,
            HttpStatusCode.Forbidden to Failure.UNAVAILABLE,
            HttpStatusCode.ServiceUnavailable to Failure.UNAVAILABLE)) {
            client("secret-provider-body", status).use {
                val error = assertFailsWith<StockProviderException> {
                    FinnhubStockProviderRepositoryImpl(it, "test-key").getQuote("AAPL")
                }
                assertEquals(failure, error.failure)
                assertNull(error.cause)
                assertFalse(error.toString().contains("secret"))
            }
        }
    }

    @Test
    fun timeoutAndCancellationAreHandled() = runBlocking<Unit> {
        HttpClient(MockEngine { throw HttpRequestTimeoutException("https://finnhub.io", 10) }).use {
            assertEquals(Failure.TIMEOUT, assertFailsWith<StockProviderException> {
                FinnhubStockProviderRepositoryImpl(it, "test-key").getQuote("AAPL")
            }.failure)
        }
        HttpClient(MockEngine { throw CancellationException("cancelled") }).use {
            assertFailsWith<CancellationException> {
                FinnhubStockProviderRepositoryImpl(it, "test-key").getQuote("AAPL")
            }
        }
    }
}
