package org.example.stocksteps.userdata

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.*
import org.example.stocksteps.portfolio.*
import org.example.stocksteps.portfolio.analytics.*
import org.example.stocksteps.repository.PriceHistoryProvider
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.NewsService
import org.example.stocksteps.service.StockService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.*

class PortfolioAnalyticsRoutesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true }
    private val path = "/api/v1/me"

    private class Harness(val store: InMemoryUserDataStore, val portfolios: PortfolioService, val benchmarkCalls: () -> Int, val historyCalls: () -> Int)

    /** Daily AAPL closes rising 0.1/day for a year; USD/CAD fixed at 1.35 (MOCK FX). */
    private val prices = object : PriceHistoryProvider {
        var calls = 0
        override suspend fun getIntradaySparkline(symbol: String) = Sparkline(symbol, emptyList())
        override suspend fun getIntradayPoints(symbol: String) = emptyList<PricePoint>()
        override suspend fun getDailyCloses(symbol: String): List<PricePoint> {
            calls++
            val start = LocalDate.parse("2025-09-01")
            return (0..400).map { start.plusDays(it.toLong()) }.filter { it.dayOfWeek.value <= 5 && it < LocalDate.parse("2026-10-08") }
                .mapIndexed { index, date -> PricePoint(date.toString(), 200.0 + index * 0.1) }
        }
    }

    private fun ApplicationTestBuilder.harness(debugAllowed: Boolean = true): Harness {
        val store = InMemoryUserDataStore()
        val fixture = FixtureMarketDataSource(sampleFallback = true)
        val portfolios = PortfolioService(store, clock::millis)
        val watch = WatchMarketData(StockService(fixture, fixture), fixture, NewsService(fixture))
        val market = PortfolioMarketService(portfolios, prices, watch, MockPortfolioFx, clock)
        var benchmarkCalls = 0
        val benchmarks = BenchmarkHistorySource { info ->
            benchmarkCalls++
            val start = LocalDate.parse("2025-09-01")
            (0..402).map { start.plusDays(it.toLong()) }.filter { it.dayOfWeek.value <= 5 }.mapIndexed { i, date -> date.toString() to (1000 + i).toString() }.toMap()
                .takeIf { info.id != BenchmarkId.NASDAQ }.orEmpty()
        }
        val entitlements = EntitlementService(store, clock::millis, debugAllowed)
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { portfolioAnalyticsRoutes(MockUserAuthenticator(), PortfolioAnalyticsService(market, watch, benchmarks, entitlements, clock, sampleData = true), entitlements) }
        }
        return Harness(store, portfolios, { benchmarkCalls }, { prices.calls })
    }

    private fun seed(portfolios: PortfolioService, uid: String = "alice") = runBlocking {
        portfolios.saveAccount(uid, PortfolioAccount("tfsa", "TFSA", PortfolioCategory.TFSA, PortfolioCurrency.CAD))
        portfolios.saveTransaction(uid, PortfolioTransaction("d1", "tfsa", TransactionType.CASH_DEPOSIT, "2025-09-02", PortfolioCurrency.USD, grossAmount = "5000"), false)
        portfolios.saveTransaction(uid, PortfolioTransaction("b1", "tfsa", TransactionType.BUY, "2025-09-02", PortfolioCurrency.USD,
            InstrumentRef("AAPL", "Apple", "NASDAQ", "USD"), "10", "200.1"), false)
    }

    private suspend fun ApplicationTestBuilder.analytics(query: String = "", user: String = "alice") =
        client.get("$path/portfolio/accounts/tfsa/analytics$query") { bearerAuth("mock-user:$user") }

    private suspend fun ApplicationTestBuilder.simulate(body: String, user: String = "alice") = client.put("$path/entitlements/debug") {
        bearerAuth("mock-user:$user"); contentType(ContentType.Application.Json); setBody(body)
    }

    @Test fun requiresIdentityAndEnforcesOwnership() = testApplication {
        val h = harness()
        seed(h.portfolios)
        assertEquals(HttpStatusCode.Unauthorized, client.get("$path/portfolio/accounts/tfsa/analytics").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("$path/entitlements").status)
        assertEquals(HttpStatusCode.NotFound, analytics(user = "bob").status) // bob can't read alice's account by ID
        assertEquals(HttpStatusCode.OK, analytics().status)
    }

    @Test fun freeTierIsComputedAndShapedOnTheServer() = testApplication {
        val h = harness()
        seed(h.portfolios)
        val entitlements = json.decodeFromString<Entitlements>(client.get("$path/entitlements") { bearerAuth("mock-user:alice") }.bodyAsText())
        assertEquals(SubscriptionTier.FREE, entitlements.tier)
        val response = analytics("?period=1Y&benchmark=SP500")
        val result = json.decodeFromString<PortfolioAnalytics>(response.bodyAsText())
        assertEquals(SubscriptionTier.FREE, result.tier)
        assertNull(result.performance); assertNull(result.benchmark); assertNull(result.contributors)
        assertTrue("performance" in result.locked)
        assertFalse(response.bodyAsText().contains("timeWeightedReturn")) // premium numbers never leave the server
        assertEquals("AAPL", result.concentration.largestHolding?.name) // own holdings stay visible
        assertEquals(0, h.benchmarkCalls())
    }

    @Test fun plusTierGetsPerformanceAndBenchmark() = testApplication {
        val h = harness()
        seed(h.portfolios)
        val granted = json.decodeFromString<Entitlements>(simulate("""{"tier":"PLUS"}""").bodyAsText())
        assertEquals(EntitlementStatus.ACTIVE, granted.status)
        assertEquals("debug", granted.source)
        val result = json.decodeFromString<PortfolioAnalytics>(analytics("?period=1Y&benchmark=SP500").bodyAsText())
        val performance = assertNotNull(result.performance)
        assertEquals(Availability.AVAILABLE, performance.availability)
        assertEquals("0", performance.netExternalFlows) // funded before the 1Y window
        assertEquals(Availability.AVAILABLE, result.benchmark?.availability)
        assertEquals(PortfolioCurrency.CAD, result.benchmark?.currency)
        assertTrue(result.notes.any { it.contains("Sample market data") })
        // A benchmark the source can't supply is unavailable, never zero.
        val missing = json.decodeFromString<PortfolioAnalytics>(analytics("?period=1Y&benchmark=NASDAQ").bodyAsText())
        assertEquals(Availability.UNAVAILABLE, missing.benchmark?.availability)
        assertNull(missing.benchmark?.benchmarkReturn)
    }

    @Test fun expiredPlusFallsBackToFreeWithoutLosingRecords() = testApplication {
        val h = harness()
        seed(h.portfolios)
        val expired = json.decodeFromString<Entitlements>(simulate("""{"tier":"PLUS","expired":true}""").bodyAsText())
        assertEquals(SubscriptionTier.FREE, expired.tier)
        assertEquals(EntitlementStatus.EXPIRED, expired.status)
        val result = json.decodeFromString<PortfolioAnalytics>(analytics().bodyAsText())
        assertNull(result.performance)
        assertTrue(result.allocation.byHolding.isNotEmpty())
        assertEquals(2, h.portfolios.get("alice").transactions.size)
    }

    @Test fun subscriptionExpiryIsEvaluatedAtRequestTime() = runBlocking {
        val store = InMemoryUserDataStore()
        val service = EntitlementService(store, clock::millis, debugAllowed = false)
        store.setEntitlement("u", StoredEntitlement(SubscriptionTier.PLUS, clock.millis() + 1, "subscription"))
        assertEquals(SubscriptionTier.PLUS, service.get("u").tier)
        store.setEntitlement("u", StoredEntitlement(SubscriptionTier.PLUS, clock.millis(), "subscription"))
        assertEquals(EntitlementStatus.EXPIRED, service.get("u").status)
    }

    @Test fun debugOverridesDoNotExistOutsideMock() = testApplication {
        val h = harness(debugAllowed = false)
        seed(h.portfolios)
        assertEquals(HttpStatusCode.NotFound, simulate("""{"tier":"PLUS"}""").status)
        // Even a stored debug record (e.g. copied from a MOCK database) is ignored.
        h.store.setEntitlement("alice", StoredEntitlement(SubscriptionTier.PLUS, null, "debug"))
        assertEquals(SubscriptionTier.FREE, json.decodeFromString<PortfolioAnalytics>(analytics().bodyAsText()).tier)
        assertFailsWith<UserDataException> { EntitlementService(h.store, clock::millis, false).simulate("alice", DebugEntitlementRequest(SubscriptionTier.PLUS)) }
    }

    @Test fun rejectsUnboundedOrUnknownParameters() = testApplication {
        val h = harness()
        seed(h.portfolios)
        assertEquals(HttpStatusCode.BadRequest, analytics("?period=10Y").status)
        assertEquals(HttpStatusCode.BadRequest, analytics("?benchmark=DAX").status)
    }

    @Test fun cachesByRevisionAndRecomputesAfterLedgerEdits() = testApplication {
        val h = harness()
        seed(h.portfolios)
        simulate("""{"tier":"PLUS"}""")
        val first = json.decodeFromString<PortfolioAnalytics>(analytics("?period=1Y&benchmark=SP500").bodyAsText())
        val calls = h.benchmarkCalls()
        assertEquals(first, json.decodeFromString<PortfolioAnalytics>(analytics("?period=1Y&benchmark=SP500").bodyAsText()))
        assertEquals(calls, h.benchmarkCalls()) // served from cache
        runBlocking {
            h.portfolios.saveTransaction("alice", PortfolioTransaction("d2", "tfsa", TransactionType.CASH_DEPOSIT, "2026-10-01", PortfolioCurrency.CAD, grossAmount = "1000"), false)
        }
        val second = json.decodeFromString<PortfolioAnalytics>(analytics("?period=1Y&benchmark=SP500").bodyAsText())
        assertEquals(first.revision + 1, second.revision)
        assertEquals("1000", second.performance?.netExternalFlows)
    }
}
