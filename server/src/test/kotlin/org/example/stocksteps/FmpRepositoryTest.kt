package org.example.stocksteps

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderException.Failure
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import kotlin.test.*

class FmpRepositoryTest {
    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        HttpClient(MockEngine { request ->
            assertEquals("AAPL", request.url.parameters["symbol"])
            assertEquals("test-secret", request.url.parameters["apikey"])
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json() }
        }

    @Test
    fun mapsQuoteAndEmptyArray() = runBlocking<Unit> {
        client("""[{"symbol":"AAPL","price":333.42,"volume":20073601.00459}]""").use {
            val quote = FmpStockProviderRepositoryImpl(it, "test-secret").getQuote("AAPL")
            assertEquals(333.42, quote?.price)
            assertEquals(20073601L, quote?.volume)
        }
        client("[]").use {
            assertNull(FmpStockProviderRepositoryImpl(it, "test-secret").getQuote("AAPL"))
        }
    }

    @Test
    fun classifiesStatusAndMalformedResponses() = runBlocking<Unit> {
        val cases = listOf(
            Triple("secret upstream body", HttpStatusCode.TooManyRequests, Failure.RATE_LIMITED),
            Triple("secret upstream body", HttpStatusCode.Unauthorized, Failure.UNAVAILABLE),
            Triple("secret upstream body", HttpStatusCode.ServiceUnavailable, Failure.UNAVAILABLE),
            Triple("{broken", HttpStatusCode.OK, Failure.INVALID_RESPONSE),
            Triple("{}", HttpStatusCode.OK, Failure.INVALID_RESPONSE),
            Triple("""[{"symbol":"MSFT"}]""", HttpStatusCode.OK, Failure.INVALID_RESPONSE)
        )
        for ((body, status, failure) in cases) {
            client(body, status).use {
                val error = assertFailsWith<StockProviderException> {
                    FmpStockProviderRepositoryImpl(it, "test-secret").getQuote("AAPL")
                }
                assertEquals(failure, error.failure)
                assertNull(error.cause)
                assertFalse(error.toString().contains("secret"))
            }
        }
    }

    @Test
    fun timeoutIsMappedAndCancellationIsPreserved() = runBlocking<Unit> {
        HttpClient(MockEngine { throw HttpRequestTimeoutException("https://example.com?apikey=test-secret", 10) }).use {
            assertEquals(Failure.TIMEOUT, assertFailsWith<StockProviderException> {
                FmpStockProviderRepositoryImpl(it, "test-secret").getQuote("AAPL")
            }.failure)
        }
        HttpClient(MockEngine { throw CancellationException("cancelled") }).use {
            assertFailsWith<CancellationException> {
                FmpStockProviderRepositoryImpl(it, "test-secret").getQuote("AAPL")
            }
        }
    }
}
