package org.example.stocksteps.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.data.RemoteMarketRepository
import kotlin.test.*

class MarketApiTest {
    @Test fun discoveryUsesBackendContractsAndFirstNewsPage() = runBlocking<Unit> {
        HttpClient(MockEngine { request ->
            assertEquals("stocksteps.test", request.url.host)
            assertNull(request.url.parameters["apikey"])
            val body = when (request.url.encodedPath) {
                "/api/v1/market/gainers", "/api/v1/market/losers" ->
                    """[{"symbol":"AAPL","price":150,"change":1,"changePercent":0.5}]"""
                "/api/v1/news" -> {
                    assertEquals("0", request.url.parameters["page"])
                    assertEquals("20", request.url.parameters["limit"])
                    """[{"title":"Market headline","url":"https://example.com/article"}]"""
                }
                else -> error("Unexpected request")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }.use { client ->
            val repository = RemoteMarketRepository(StockStepsApi(client, "https://stocksteps.test"))
            assertEquals("AAPL", repository.getGainers().single().symbol)
            assertNull(repository.getLosers().single().name)
            assertNull(repository.getNews().single().source)
        }
    }
}
