package org.example.stocksteps.market

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.marketSnapshotRoutes
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.*
import org.example.stocksteps.repository.models.*
import org.example.stocksteps.repositoryImpl.FmpMarketDataProvider
import org.example.stocksteps.service.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

class MarketSnapshotTest {
    @Test fun mapperPreservesSignedChangesAndHandlesMissingFields() {
        val positive = FmpSnapshotMover("SPY", price = 500.0, change = 2.0, changesPercentage = 0.4, volume = 10.0).toSnapshotMover()!!
        assertEquals(0.4, positive.changePercent)
        val negative = FmpSnapshotMover("QQQ", change = -5.0, changePercentage = -1.2).toSnapshotMover()!!
        assertEquals(-1.2, negative.changePercent)
        assertNull(negative.price)
        assertNull(negative.volume)
        val malformed = FmpSnapshotMover("DIA", price = -1.0, change = Double.NaN, volume = Double.POSITIVE_INFINITY).toSnapshotMover()!!
        assertNull(malformed.price); assertNull(malformed.change); assertNull(malformed.volume)
        assertNull(FmpSnapshotMover().toSnapshotMover())
        val index = FmpQuote("SPY", price = 500.0, changePercentage = -0.3).toMarketIndex("S&P 500")
        assertEquals("S&P 500", index.name)
        assertEquals(-0.3, index.changePercent)
        assertTrue(index.isProxy)
    }
    @Test fun statusOnlyUsesProviderFlag() {
        assertEquals(MarketStatus.OPEN, FmpMarketHours("NYSE", true).toMarketStatus())
        assertEquals(MarketStatus.CLOSED, FmpMarketHours("NYSE", false).toMarketStatus())
        assertEquals(MarketStatus.UNKNOWN, FmpMarketHours("NYSE", null).toMarketStatus())
    }
    @Test fun fmpQuoteFailureDoesNotRemoveOtherProxyQuotes() = runBlocking {
        val requests: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        val client = HttpClient(MockEngine { request ->
            requests += request.url.encodedPath
            val body = when (request.url.parameters["symbol"]) {
                "SPY" -> """[{"symbol":"SPY","price":500,"changePercentage":1.2}]"""
                "DIA" -> """[{"symbol":"DIA","changePercentage":-0.5}]"""
                else -> return@MockEngine respond("Restricted", HttpStatusCode.PaymentRequired)
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            val result = FmpMarketDataProvider(client, "test-key").getMarketIndices()
            assertEquals(listOf("SPY", "QQQ", "DIA"), result.map { it.symbol })
            assertEquals(500.0, result[0].price)
            assertNotNull(result[1].error)
            assertNull(result[2].price)
            assertEquals(-0.5, result[2].changePercent)
            assertEquals(3, requests.size)
        } finally { client.close() }
    }
    @Test fun fallbackLoadsRestrictedQuotesAndPreservesSuccessfulFmpQuote() = runBlocking {
        val fallbackSymbols: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        val client = HttpClient(MockEngine { request ->
            if (request.url.parameters["symbol"] == "SPY") {
                respond("""[{"symbol":"SPY","price":500}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else respond("Restricted", HttpStatusCode.PaymentRequired)
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val fallback = object : StockQuoteProviderRepository {
            override suspend fun getQuote(symbol: String): StockQuote? {
                fallbackSymbols += symbol
                if (symbol == "DIA") return null
                return StockQuote(symbol, null, 450.0, -2.0, -0.4, null, null)
            }
        }
        try {
            val indices = FmpMarketDataProvider(client, "test-key", fallback).getMarketIndices()
            assertEquals(500.0, indices[0].price)
            assertEquals(450.0, indices[1].price)
            assertEquals(-0.4, indices[1].changePercent)
            assertNull(indices[1].error)
            assertTrue(indices[1].isProxy)
            assertEquals("Dow 30", indices[2].name)
            assertNotNull(indices[2].error)
            assertEquals(setOf("QQQ", "DIA"), fallbackSymbols.toSet())
        } finally { client.close() }
    }

    private open class Provider : MarketDataProvider {
        var calls = 0
        override suspend fun getMarketIndices(): List<MarketIndex> { calls++; return listOf(MarketIndex("SPY", "S&P 500", 500.0, 2.0, 0.4)) }
        override suspend fun getGainers() = (1..8).map { MarketMover("A$it", changePercent = it.toDouble()) }
        override suspend fun getLosers() = emptyList<MarketMover>()
        override suspend fun getMostActive() = emptyList<MarketMover>()
        override suspend fun getMarketStatus() = MarketStatus.CLOSED
    }
    @Test fun servicePreservesSuccessfulSectionsLimitsMoversAndReportsSafeFailures() = runBlocking {
        val provider = object : Provider() {
            override suspend fun getMostActive(): List<MarketMover> = throw StockProviderException(StockProviderException.Failure.TIMEOUT)
        }
        val clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)
        val snapshot = MarketSnapshotService(provider, clock = clock).getSnapshot()
        assertEquals(500.0, snapshot.indices.single().price)
        assertEquals(5, snapshot.gainers.size)
        assertTrue(snapshot.losers.isEmpty())
        assertEquals(MarketStatus.CLOSED, snapshot.marketStatus)
        assertEquals("2026-10-04T12:00:00Z", snapshot.lastUpdated)
        assertEquals("mostActive", snapshot.errors.single().section)
        assertEquals("PROVIDER_TIMEOUT", snapshot.errors.single().error.code)
    }
    @Test fun cacheHitExpiryAndConcurrentMisses() = runBlocking {
        var now = 0L
        val provider = object : Provider() {
            override suspend fun getMarketIndices(): List<MarketIndex> { delay(10); return super.getMarketIndices() }
        }
        val service = MarketSnapshotService(provider, InMemoryMarketSnapshotCache(nowMillis = { now }))
        val first = service.getSnapshot()
        assertEquals(first, service.getSnapshot())
        assertEquals(1, provider.calls)
        now = 45_000
        coroutineScope { (1..5).map { async { service.getSnapshot() } }.awaitAll() }
        assertEquals(2, provider.calls)
    }
    @Test fun bothRoutesShareContractAndCache() = testApplication {
        val provider = Provider()
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { marketSnapshotRoutes(MarketSnapshotService(provider)) }
        }
        val first = client.get("/market/snapshot")
        val alias = client.get("/api/v1/market/snapshot")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(first.bodyAsText(), alias.bodyAsText())
        val snapshot = Json.decodeFromString<MarketSnapshot>(first.bodyAsText())
        assertEquals(MarketStatus.CLOSED, snapshot.marketStatus)
        assertTrue(first.bodyAsText().contains("\"mostActive\":[]"))
        assertEquals(1, provider.calls)
    }
}
