package org.example.stocksteps.market

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.*
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.PriceChartService
import org.example.stocksteps.service.ValuationService
import org.example.stocksteps.valuationRoutes
import java.time.Instant
import java.time.LocalDate
import kotlin.test.*

/** MOCK valuation history built through the same service as REAL, from shipped fixtures. */
class MockValuationTest {
    private val fixtures = FixtureMarketDataSource(now = { Instant.parse("2026-10-07T20:00:00Z") }, sampleFallback = true)
    private val service = ValuationService(fixtures, PriceChartService(fixtures), fixtures, today = { LocalDate.parse("2026-10-07") })
    private fun history(symbol: String) = runBlocking { service.history(symbol) }

    @Test fun curatedScenariosUseMonthlyTtmWithoutInventedPoints() {
        for (symbol in listOf("MSFT", "AAPL", "NVDA", "TSLA")) {
            val h = history(symbol)
            assertEquals(ValuationMethod.MONTHLY_TTM, h.method, symbol)
            assertTrue(h.observations.all { it.pe > 0 }, "$symbol: only valid multiples")
            assertEquals(h.observations.sortedBy { it.date }, h.observations, "$symbol oldest first")
            assertEquals(h.observations.map { it.date.take(7) }.distinct().size, h.observations.size, "$symbol: one per month")
            assertEquals(FinancialAvailability.AVAILABLE, h.currentPeAvailability)
        }
        assertTrue(history("NVDA").splitAdjusted, "NVDA's 10:1 split is restated")
        assertTrue(history("TSLA").observations.size < history("MSFT").observations.size, "TSLA's missing filing leaves months out")
    }

    @Test fun differentReportingCurrencyFallsBackToReportedAnnualRatios() {
        val td = history("TD")
        assertEquals(ValuationMethod.ANNUAL_REPORTED, td.method, "USD price and CAD EPS are never divided")
        assertTrue(td.observations.size >= 3)
        assertEquals("CAD", td.epsCurrency)
    }

    @Test fun routeServesTheSharedContract() = testApplication {
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { valuationRoutes(service) }
        }
        val response = client.get("/api/v1/stocks/msft/valuation")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json { ignoreUnknownKeys = true }.decodeFromString(ValuationHistory.serializer(), response.bodyAsText())
        assertEquals("MSFT", body.symbol)
        assertTrue(body.observations.size >= 24)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/%20/valuation").status)
    }

    @Test fun companyDetailsUsesTheSameCurrentPeAndFiveYearAverage() = runBlocking<Unit> {
        val details = org.example.stocksteps.service.CompanyDetailsService(
            org.example.stocksteps.service.StockService(fixtures, fixtures), org.example.stocksteps.service.CompanyFinancialService(fixtures), fixtures, valuation = service
        ).getDetails("MSFT")
        val history = service.history("MSFT")
        val pe = details.fundamentals!!.valuation
        assertEquals(history.currentPe, pe.metrics.getValue("pe").value)
        val latest = history.observations.last().date
        val expected = history.observations.filter { it.date > "${latest.take(4).toInt() - 5}${latest.drop(4)}" }.map { it.pe }.average()
        assertEquals(expected, pe.historical.getValue("pe").average!!, 1e-9, "same 5-year monthly average as the Valuation screen")
    }

    @Test fun failedInputsDegradeToCurrentPeInsteadOfFailing() = runBlocking<Unit> {
        val noEarnings = ValuationService(fixtures, PriceChartService(fixtures), { error("provider down") }, today = { LocalDate.parse("2026-10-07") })
        val result = noEarnings.history("MSFT")
        assertEquals(ValuationMethod.ANNUAL_REPORTED, result.method, "falls back to reported annual ratios")
        assertNotNull(result.currentPe)
    }
}
