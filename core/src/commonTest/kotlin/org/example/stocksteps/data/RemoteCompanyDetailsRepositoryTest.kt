package org.example.stocksteps.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.domain.GetWhyMoving
import org.example.stocksteps.domain.StockDataException
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.configureStockStepsClient
import kotlin.test.*

class RemoteCompanyDetailsRepositoryTest {
    private fun repository(status: HttpStatusCode, body: String, block: suspend (RemoteCompanyDetailsRepository) -> Unit) = runBlocking {
        HttpClient(MockEngine { request ->
            assertEquals("/api/v1/stocks/MSFT/why-moving", request.url.encodedPath)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }.use { block(RemoteCompanyDetailsRepository(StockStepsApi(it, "https://stocksteps.test"))) }
    }

    @Test fun noExplanationIsAnEmptyResultNotAFailure() = repository(
        HttpStatusCode.NotFound, """{"code":"WHY_MOVING_UNAVAILABLE","message":"An explanation for this move is not available yet."}"""
    ) { assertNull(GetWhyMoving(it)("MSFT")) }

    @Test fun explanationIsReturnedWithSources() = repository(
        HttpStatusCode.OK, """{"symbol":"MSFT","summary":"Sample","sources":[{"title":"T","url":"https://example.com","publisher":"Wire"}]}"""
    ) { assertEquals("Wire", GetWhyMoving(it)("MSFT")!!.sources.single().publisher) }

    @Test fun otherErrorsStayFailuresWithoutProviderDetails() = repository(
        HttpStatusCode.ServiceUnavailable, """{"code":"PROVIDER_RATE_LIMITED","message":"Market data is temporarily unavailable."}"""
    ) { repo ->
        val error = assertFailsWith<StockDataException> { GetWhyMoving(repo)("MSFT") }
        assertFalse(error.message.orEmpty().contains("FMP"))
    }
}
