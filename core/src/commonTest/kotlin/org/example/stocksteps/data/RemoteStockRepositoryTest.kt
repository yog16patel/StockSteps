package org.example.stocksteps.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.domain.*
import org.example.stocksteps.network.*
import kotlin.test.*

class RemoteStockRepositoryTest {
    @Test fun useCasesNormalizeInputAndSkipEmptySearch() = runBlocking<Unit> {
        var requests = 0
        HttpClient(MockEngine { request ->
            requests++
            val body = if (request.url.encodedPath.endsWith("search")) {
                assertEquals("Apple", request.url.parameters["query"])
                "[]"
            } else {
                assertEquals("/api/v1/stocks/AAPL/quote", request.url.encodedPath)
                """{"symbol":"AAPL","companyName":null,"price":150,"change":null,"changePercent":null,"dayHigh":null,"dayLow":null}"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }.use { client ->
            val repository = RemoteStockRepository(StockStepsApi(client, "https://stocksteps.test"))
            assertTrue(SearchStocks(repository)("  ").isEmpty())
            assertEquals(0, requests)
            SearchStocks(repository)(" Apple ")
            assertEquals(150.0, GetStockQuote(repository)(" aapl ").price)
        }
    }

    @Test fun errorsCrossDomainBoundaryWithoutRawBodies() = runBlocking<Unit> {
        HttpClient(MockEngine {
            respond("private provider response", HttpStatusCode.ServiceUnavailable)
        }) { configureStockStepsClient() }.use { client ->
            val repository = RemoteStockRepository(StockStepsApi(client, "https://stocksteps.test"))
            val error = assertFailsWith<StockDataException> { SearchStocks(repository)("Apple") }
            assertFalse(error.message.orEmpty().contains("private provider"))
        }
    }

    @Test fun cancellationIsNotConvertedToUserError() = runBlocking<Unit> {
        HttpClient(MockEngine { throw CancellationException("cancelled") }) {
            configureStockStepsClient()
        }.use { client ->
            val repository = RemoteStockRepository(StockStepsApi(client, "https://stocksteps.test"))
            assertFailsWith<CancellationException> { SearchStocks(repository)("Apple") }
        }
    }
}
