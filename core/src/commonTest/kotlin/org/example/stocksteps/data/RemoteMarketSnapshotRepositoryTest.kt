package org.example.stocksteps.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.MarketStatus
import org.example.stocksteps.network.*
import kotlin.test.*

class RemoteMarketSnapshotRepositoryTest {
    @Test fun requestsOnlyBackendAndDecodesPartialSnapshot() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("stocksteps.test", request.url.host)
            assertEquals("/market/snapshot", request.url.encodedPath)
            assertNull(request.url.parameters["apikey"])
            respond("""{"marketStatus":"CLOSED","indices":[{"symbol":"SPY","name":"S&P 500","price":500,"changePercent":-0.5}],"lastUpdated":"2026-10-04T12:00:00Z","errors":[{"section":"mostActive","error":{"code":"PROVIDER_UNAVAILABLE","message":"Unavailable"}}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }
        try {
            val result = GetMarketSnapshot(RemoteMarketSnapshotRepository(StockStepsApi(client, "https://stocksteps.test")))()
            assertEquals(MarketStatus.CLOSED, result.marketStatus)
            assertEquals(-0.5, result.indices.single().changePercent)
            assertTrue(result.mostActive.isEmpty())
            assertEquals("mostActive", result.errors.single().section)
        } finally { client.close() }
    }
    @Test fun backendFailureIsSafeDomainError() = runBlocking {
        val client = HttpClient(MockEngine { respond("private provider body", HttpStatusCode.BadGateway) }) { configureStockStepsClient() }
        try {
            val error = assertFailsWith<StockDataException> { RemoteMarketSnapshotRepository(StockStepsApi(client, "https://stocksteps.test")).getSnapshot() }
            assertFalse(error.message.orEmpty().contains("private"))
        } finally { client.close() }
    }
}
