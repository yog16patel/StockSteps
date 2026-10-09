package org.example.stocksteps

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.network.configureStockStepsClient
import kotlin.test.*

/**
 * Phase 5C regression: `searchStocks` and `getCompanyNews` used to call themselves (a use-case property had the same name as the member
 * function), so every iOS search was cancelled by its own recursion and showed "Could not load stocks". One call = one request now.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosStockStepsClientTest {
    private var requests = 0
    private fun client() = IosStockStepsClient(StockStepsDependencies("https://stocksteps.test") {
        HttpClient(MockEngine { request ->
            requests++
            val body = when {
                request.url.encodedPath.endsWith("/stocks/search") ->
                    """[{"symbol":"AAPL","name":"Apple Inc.","currency":"USD","exchange":"NASDAQ","exchangeFullName":"NASDAQ Global Select"}]"""
                request.url.encodedPath.endsWith("/news") -> "[]"
                else -> error("unexpected ${request.url}")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureStockStepsClient() }
    })

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test fun searchMakesOneRequestAndReturnsResults() = runTest {
        val client = client()
        try {
            val results = client.searchStocks("apple")
            assertEquals(listOf("AAPL"), results.map { it.symbol })
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test fun companyNewsMakesOneRequest() = runTest {
        val client = client()
        try {
            assertTrue(client.getCompanyNews("AAPL").isEmpty())
            assertEquals(1, requests)
        } finally { client.close() }
    }
}
