package org.example.stocksteps.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.data.RemoteStockRepository
import org.example.stocksteps.domain.GetCompanyProfile
import kotlin.test.*

class CompanyProfileApiTest {
    @Test fun profileUsesBackendAndSupportsMissingFields() = runBlocking<Unit> {
        HttpClient(MockEngine { request ->
            assertEquals("stocksteps.test", request.url.host)
            assertEquals("/api/v1/stocks/AAPL/profile", request.url.encodedPath)
            assertNull(request.url.parameters["apikey"])
            respond("""{"symbol":"AAPL","companyName":"Apple Inc.","sector":"Technology","extra":"ignored"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }.use { client ->
            val useCase = GetCompanyProfile(RemoteStockRepository(StockStepsApi(client, "https://stocksteps.test")))
            val profile = useCase(" aapl ")
            assertEquals("Apple Inc.", profile.companyName)
            assertEquals("Technology", profile.sector)
            assertNull(profile.description)
            assertNull(profile.website)
        }
    }
}
