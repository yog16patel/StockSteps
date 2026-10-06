package org.example.stocksteps

import io.ktor.http.*
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.service.CompanyFinancialService
import kotlin.test.*

class CompanyFinancialRoutesTest {
    @Test fun validatesPeriodAndSymbolAndReturnsProviderNeutralPartialData() = testApplication {
        val calls = mutableListOf<Pair<String, String>>()
        val provider = object : StockProviderRepository {
            override suspend fun getFundamentals(symbol: String, period: String): CompanyFundamentals {
                calls += symbol to period
                return CompanyFundamentals(symbol, financials = CompanyFinancials(
                    growth = mapOf("revenue" to FinancialFact(amount = 100, source = FinancialSource.PROVIDER_DIRECT, availability = FinancialAvailability.AVAILABLE, note = "FMP HTTP 403 access denied")),
                    cashFlow = mapOf("freeCashFlow" to FinancialFact(availability = FinancialAvailability.TEMPORARILY_UNAVAILABLE))
                ))
            }
            override suspend fun getQuote(symbol: String): StockQuote? = null
            override suspend fun getProfile(symbol: String): CompanyProfile? = null
            override suspend fun searchStocks(query: String) = emptyList<StockSearchResult>()
            override suspend fun getGainers() = emptyList<MarketMover>()
            override suspend fun getLosers() = emptyList<MarketMover>()
        }
        application {
            install(ContentNegotiation) { json() }
            routing { companyFinancialRoutes(CompanyFinancialService(provider)) }
        }
        val response = client.get("/api/v1/stocks/aapl/fundamentals?period=quarter")
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(response.bodyAsText().contains("FMP"))
        assertFalse(response.bodyAsText().contains("403"))
        assertTrue(response.bodyAsText().contains("TEMPORARILY_UNAVAILABLE"))
        assertEquals(listOf("AAPL" to "quarter"), calls)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/AAPL/fundamentals?period=monthly").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/_BAD/fundamentals").status)
        assertEquals(1, calls.size)
    }
}
