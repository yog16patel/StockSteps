package org.example.stocksteps.market

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.companyDetailsRoutes
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.models.FmpDailyPrice
import org.example.stocksteps.repository.models.toDailyPoints
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.*
import kotlin.test.*

class CompanyDetailsTest {
    private val fixtures = FixtureMarketDataSource(root = "test-fixtures")

    @Test fun detailsAggregatesSectionsAndIsolatesFailures() = runBlocking {
        var statusCalls = 0
        val marketData = object : org.example.stocksteps.repository.MarketDataProvider by fixtures {
            override suspend fun getMarketStatus(): MarketStatus { statusCalls++; return MarketStatus.OPEN }
        }
        val failingProfiles = object : org.example.stocksteps.repository.StockProviderRepository by fixtures {
            override suspend fun getProfile(symbol: String): CompanyProfile? =
                throw StockProviderException(StockProviderException.Failure.RATE_LIMITED, 429)
        }
        val service = CompanyDetailsService(StockService(failingProfiles, fixtures), CompanyFinancialService(fixtures), marketData)
        val details = service.getDetails("SMPL")
        assertNull(details.profile)
        assertEquals(listOf("profile"), details.errors.map { it.section })
        assertFalse(details.errors.single().error.message.contains("429"))
        assertEquals(10.0, details.quote?.price)
        assertEquals(MarketStatus.OPEN, details.marketStatus)
        service.getDetails("SMPL")
        assertEquals(1, statusCalls, "market status is cached between stocks")
    }

    @Test fun dailyHistoryIsSlicedPerRangeFromOneDataset() {
        val points = listOf("2021-10-01", "2025-10-01", "2026-07-01", "2026-09-10", "2026-09-30", "2026-10-01").map { PricePoint(it, 1.0) }
        val service = PriceChartService(fixtures)
        assertEquals(2, service.slice(points, ChartRange.ONE_WEEK).size)
        assertEquals(3, service.slice(points, ChartRange.ONE_MONTH).size)
        assertEquals(5, service.slice(points, ChartRange.ONE_YEAR).size)
        assertEquals(6, service.slice(points, ChartRange.ALL).size)
    }

    @Test fun fmpDailyRowsBecomeOrderedPoints() {
        val points = listOf(FmpDailyPrice("2026-10-02", price = 2.0), FmpDailyPrice("2026-10-01", close = 1.0), FmpDailyPrice("bad"), FmpDailyPrice("2026-10-03", price = -1.0)).toDailyPoints()
        assertEquals(listOf(PricePoint("2026-10-01", 1.0), PricePoint("2026-10-02", 2.0)), points)
    }

    @Test fun routesServeDetailsChartAndWhyMoving() = testApplication {
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing {
                companyDetailsRoutes(
                    CompanyDetailsService(StockService(fixtures, fixtures), CompanyFinancialService(fixtures), fixtures),
                    PriceChartService(fixtures),
                    WhyMovingService(fixtures)
                )
            }
        }
        val details = client.get("/api/v1/stocks/smpl/details").bodyAsText()
        assertTrue(details.contains("\"marketStatus\": \"CLOSED\"") || details.contains("\"marketStatus\":\"CLOSED\""))
        assertTrue(client.get("/api/v1/stocks/SMPL/chart?range=1M").bodyAsText().contains("2026-09-24"))
        assertFalse(client.get("/api/v1/stocks/SMPL/chart?range=1M").bodyAsText().contains("2025-09-30"))
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/SMPL/chart?range=2Y").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/stocks/SMPL/chart?range=1D").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/SMPL/why-moving").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/stocks/NONE/why-moving").status)
    }

    @Test fun realModeWithoutAnExplanationSourceReportsUnavailable() = runBlocking {
        assertNull(WhyMovingService(null).getWhyMoving("MSFT"))
    }
}
