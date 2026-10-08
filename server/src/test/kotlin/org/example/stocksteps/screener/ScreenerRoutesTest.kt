package org.example.stocksteps.screener

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.*
import org.example.stocksteps.portfolio.analytics.SubscriptionTier
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.*
import org.example.stocksteps.userdata.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ScreenerRoutesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val fixture = FixtureMarketDataSource(sampleFallback = true)
    private val stocks = StockService(fixture, fixture)
    private val financials = CompanyFinancialService(fixture)

    private fun mockService(
        loads: AtomicInteger = AtomicInteger(),
        universe: ScreenerUniverseSource = FixtureScreenerUniverse(),
        fail: Set<String> = emptySet()
    ) = ScreenerService(universe, stocks, { symbol ->
        loads.incrementAndGet()
        if (symbol in fail) throw IllegalStateException("provider down")
        financials.getFundamentals(symbol, "annual")
    }, PriceChartService(fixture), { 1 / 1.35 }, clock, sampleData = true, fundamentalsPerHour = 0, fullRecords = true)

    private fun ApplicationTestBuilder.install(service: ScreenerService, limiter: RequestRateLimiter = RequestRateLimiter(1_000), store: UserDataStore = InMemoryUserDataStore()) {
        val entitlements = EntitlementService(store, clock::millis, debugAllowed = true)
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing {
                screenerRoutes(service, limiter)
                savedScreenRoutes(MockUserAuthenticator(), SavedScreensService(store, entitlements, clock::millis))
            }
        }
    }

    private suspend fun ApplicationTestBuilder.search(query: ScreenerQuery) = client.post("/api/v1/screener/search") {
        contentType(ContentType.Application.Json); setBody(json.encodeToString(ScreenerQuery.serializer(), query))
    }
    private suspend fun ApplicationTestBuilder.page(query: ScreenerQuery) = json.decodeFromString(ScreenerPage.serializer(), search(query).bodyAsText())

    @Test fun catalogDescribesTheMockUniverseHonestly() = testApplication {
        install(mockService())
        val catalog = json.decodeFromString(ScreenerCatalog.serializer(), client.get("/api/v1/screener/catalog").bodyAsText())
        assertEquals(4, catalog.presets.size)
        assertTrue(catalog.universe.complete)
        assertEquals(23, catalog.universe.size)
        assertTrue("TSX" in catalog.choices.getValue(ChoiceField.EXCHANGE))
        assertTrue("CA" in catalog.choices.getValue(ChoiceField.COUNTRY))
    }

    @Test fun everyPresetRunsOverTheFixturesWithConsistentValues() = testApplication {
        install(mockService())
        for (preset in ScreenerDefinitions.presets) {
            val page = page(preset.query(50))
            assertTrue(page.total > 0, preset.id)
            assertTrue(page.sampleData)
            for (row in page.rows) for (range in preset.ranges) {
                val definition = ScreenerDefinitions.metric(range.metric)!!
                if (row.company.sector in definition.notApplicableSectors) { assertTrue(row.notApplied.isNotEmpty()); continue }
                val value = row.company.metrics.getValue(range.metric).value!!
                range.min?.let { assertTrue(value >= it, "${preset.id} ${row.company.symbol} ${range.metric}") }
                range.max?.let { assertTrue(value <= it, "${preset.id} ${row.company.symbol} ${range.metric}") }
            }
        }
        // Losses: no P/E, so never in the valuation preset; unknown dividend history: not a dividend stock.
        assertTrue(page(ScreenerDefinitions.preset("valuation")!!.query(50)).rows.none { it.company.symbol in setOf("RIVN", "BB.TO") })
        val dividends = page(ScreenerDefinitions.preset("dividend")!!.query(50))
        assertTrue(dividends.rows.none { it.company.symbol == "CSU.TO" })
        assertTrue(dividends.excludedForMissingData > 0)
    }

    @Test fun screenerValuesMatchCompanyDetailsFundamentals() = testApplication {
        install(mockService())
        val row = page(ScreenerQuery(ranges = listOf(RangeFilter("pe", max = 1000.0)), choices = listOf(ChoiceFilter(ChoiceField.COUNTRY, listOf("CA"))), pageSize = 50)).rows
            .first { it.company.symbol == "RY.TO" }
        val details = runBlocking { financials.getFundamentals("RY.TO", "annual") }
        assertEquals(details.valuation.metrics["pe"]?.value, row.company.metrics["pe"]?.value)
        assertEquals(details.financials.profitability["netMargin"]?.value, row.company.metrics["netMargin"]?.value)
    }

    @Test fun customCanadianMixedCurrencyAndStaleScenarios() = testApplication {
        install(mockService())
        val canada = page(ScreenerQuery(choices = listOf(ChoiceFilter(ChoiceField.COUNTRY, listOf("CA"))), pageSize = 50))
        // Country is the company's domicile; TD is Canadian even though this listing trades on the NYSE.
        assertEquals(setOf("RY.TO", "ENB.TO", "CNR.TO", "SHOP.TO", "BCE.TO", "CSU.TO", "BB.TO", "TD"), canada.rows.map { it.company.symbol }.toSet())
        val tsx = page(ScreenerQuery(choices = listOf(ChoiceFilter(ChoiceField.EXCHANGE, listOf("TSX"))), pageSize = 50))
        assertFalse(tsx.rows.any { it.company.symbol == "TD" })
        assertTrue(canada.rows.first { it.company.symbol == "BB.TO" }.company.stale)
        assertTrue(canada.warnings.any { it.contains("18 months") })
        val all = page(ScreenerQuery(pageSize = 50))
        assertTrue(all.warnings.any { it.contains("converted to USD") }) // mixed currencies
        val none = page(ScreenerQuery(ranges = listOf(RangeFilter("pe", 1.0, 2.0))))
        assertEquals(0, none.total)
    }

    @Test fun serverPagingAndSortingCoverTheWholeUniverse() = testApplication {
        install(mockService())
        val query = ScreenerQuery(sort = ScreenerSort(SortField.METRIC, "revenueGrowth", true), pageSize = 7)
        val symbols = mutableListOf<String>()
        var next: ScreenerQuery? = query
        while (next != null) {
            val page = page(next)
            symbols += page.rows.map { it.company.symbol }
            next = page.nextCursor?.let { query.copy(cursor = it) }
        }
        assertEquals(23, symbols.size)
        assertEquals(symbols.distinct(), symbols)
        val growth = page(query.copy(pageSize = 50)).rows.mapNotNull { it.company.metrics["revenueGrowth"]?.takeIf { v -> v.availability == FinancialAvailability.AVAILABLE }?.value }
        assertEquals(growth.sortedDescending(), growth)
        assertEquals(HttpStatusCode.BadRequest, search(query.copy(cursor = "3.bogus")).status)
    }

    @Test fun invalidFiltersAndRateLimitsAreRejected() = testApplication {
        install(mockService(), limiter = RequestRateLimiter(3))
        assertEquals(HttpStatusCode.BadRequest, search(ScreenerQuery(ranges = listOf(RangeFilter("pe", 30.0, 10.0)))).status)
        assertEquals(HttpStatusCode.BadRequest, search(ScreenerQuery(ranges = listOf(RangeFilter("secretRatio", 1.0)))).status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/screener/catalog").status)
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/screener/catalog").status)
    }

    @Test fun cachedRecordsAreReusedAcrossSearches() = testApplication {
        val loads = AtomicInteger()
        install(mockService(loads))
        page(ScreenerQuery(pageSize = 50))
        val first = loads.get()
        page(ScreenerDefinitions.preset("growing")!!.query())
        page(ScreenerDefinitions.preset("dividend")!!.query())
        assertEquals(first, loads.get())
        assertEquals(23, first)
    }

    @Test fun providerFailureIsA503AndPartialFailureKeepsOtherCompanies() = testApplication {
        install(mockService(universe = { throw IllegalStateException("FMP down") }))
        assertEquals(HttpStatusCode.ServiceUnavailable, search(ScreenerQuery()).status)
    }

    @Test fun comparisonValidatesSelectionAndHandlesPartialFailure() = testApplication {
        install(mockService(fail = setOf("RY.TO")))
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/compare?symbols=AAPL").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/compare?symbols=AAPL,MSFT,KO,JNJ,XOM").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/compare?symbols=AAPL,aapl").status)
        val two = json.decodeFromString(ComparisonResponse.serializer(), client.get("/api/v1/compare?symbols=AAPL,MSFT").bodyAsText())
        assertEquals(listOf("AAPL", "MSFT"), two.companies.map { it.symbol })
        assertTrue(two.observations.isNotEmpty())
        val four = json.decodeFromString(ComparisonResponse.serializer(), client.get("/api/v1/compare?symbols=AAPL,MSFT,RY.TO,SHOP.TO").bodyAsText())
        assertEquals(4, four.companies.size)
        val bank = four.companies.first { it.symbol == "RY.TO" }
        assertNotNull(bank.error) // fundamentals failed: price info only, explained
        assertNotNull(bank.record)
        assertTrue(four.notes.any { it.contains("reporting currency") })
        assertTrue(four.observations.all { o -> !Regex("(?i)\\b(better|best|buy|sell)\\b").containsMatchIn(o.text) })
    }

    @Test fun performanceIsNormalizedAndShortHistoryIsUnavailable() = testApplication {
        install(mockService())
        val perf = json.decodeFromString(PerformanceComparison.serializer(), client.get("/api/v1/compare/performance?symbols=AAPL,BB.TO&period=1Y").bodyAsText())
        assertEquals(ReturnKind.PRICE_RETURN, perf.kind)
        assertEquals(100.0, perf.series.first { it.symbol == "AAPL" }.values.first())
        assertNotNull(perf.series.first { it.symbol == "BB.TO" }.error)
        assertTrue(perf.notes.any { it.contains("dividends aren't included") })
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/compare/performance?symbols=AAPL,MSFT&period=7Y").status)
    }

    // ---------- Saved screens ----------

    private suspend fun ApplicationTestBuilder.save(uid: String, name: String) = client.post("/api/v1/me/screens") {
        bearerAuth("mock-user:$uid"); contentType(ContentType.Application.Json)
        setBody(json.encodeToString(SaveScreenRequest.serializer(), SaveScreenRequest(name, ScreenerDefinitions.preset("growing")!!.query())))
    }

    @Test fun savedScreensAreOwnedLimitedAndStoreFiltersOnly() = testApplication {
        val store = InMemoryUserDataStore()
        install(mockService(), store = store)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/screens").status)
        repeat(3) { assertEquals(HttpStatusCode.OK, save("alice", "Screen $it").status) }
        val limited = save("alice", "Fourth")
        assertEquals(HttpStatusCode.Forbidden, limited.status)
        assertTrue(limited.bodyAsText().contains("SAVED_SCREEN_LIMIT"))
        val list = json.decodeFromString(SavedScreensResponse.serializer(), client.get("/api/v1/me/screens") { bearerAuth("mock-user:alice") }.bodyAsText())
        assertEquals(3, list.limit); assertFalse(list.plus)
        assertEquals(ScreenerDefinitions.preset("growing")!!.query().definition(), list.screens.first().query)
        val id = list.screens.first().id
        // Names are unique per user.
        assertEquals(HttpStatusCode.Conflict, client.put("/api/v1/me/screens/$id") { bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json); setBody("""{"name":"screen 1"}""") }.status)
        // Another user can't see, rename or delete alice's screens.
        assertTrue(json.decodeFromString(SavedScreensResponse.serializer(), client.get("/api/v1/me/screens") { bearerAuth("mock-user:bob") }.bodyAsText()).screens.isEmpty())
        assertEquals(HttpStatusCode.NotFound, client.put("/api/v1/me/screens/$id") { bearerAuth("mock-user:bob"); contentType(ContentType.Application.Json); setBody("""{"name":"Mine"}""") }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/v1/me/screens/$id") { bearerAuth("mock-user:bob") }.status)
        // Rename, then delete.
        assertEquals(HttpStatusCode.OK, client.put("/api/v1/me/screens/$id") { bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json); setBody("""{"name":"Renamed"}""") }.status)
        assertEquals(HttpStatusCode.OK, client.delete("/api/v1/me/screens/$id") { bearerAuth("mock-user:alice") }.status)
        // Invalid definitions never get stored.
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/me/screens") { bearerAuth("mock-user:alice"); contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SaveScreenRequest.serializer(), SaveScreenRequest("Bad", ScreenerQuery(ranges = listOf(RangeFilter("pe", 9.0, 1.0)))))) }.status)
    }

    @Test fun plusRaisesTheLimitAndExpiryKeepsExistingScreens() = testApplication {
        val store = InMemoryUserDataStore()
        install(mockService(), store = store)
        runBlocking { store.setEntitlement("carol", StoredEntitlement(SubscriptionTier.PLUS, null, "subscription")) }
        repeat(5) { assertEquals(HttpStatusCode.OK, save("carol", "Screen $it").status) }
        runBlocking { store.setEntitlement("carol", StoredEntitlement(SubscriptionTier.PLUS, clock.millis() - 1, "subscription")) }
        val after = json.decodeFromString(SavedScreensResponse.serializer(), client.get("/api/v1/me/screens") { bearerAuth("mock-user:carol") }.bodyAsText())
        assertEquals(5, after.screens.size) // nothing is taken away
        assertEquals(SavedScreensService.FREE_LIMIT, after.limit)
        assertEquals(HttpStatusCode.Forbidden, save("carol", "Sixth").status)
    }

    // ---------- REAL quota budget ----------

    @Test fun realModeLoadsFundamentalsWithinBudgetAndLabelsCoverage() = runBlocking {
        val loads = AtomicInteger()
        val entries = listOf("AAPL", "MSFT", "KO", "JNJ", "XOM").map { UniverseEntry(it, it, "NYSE", "US", "Technology", null, 100.0, 1e11, 1e6) }
        val service = ScreenerService({ UniverseDefinition("test universe", entries) }, stocks, { loads.incrementAndGet(); financials.getFundamentals(it, "annual") },
            PriceChartService(fixture), { 0.74 }, clock, sampleData = false, fundamentalsPerHour = 2, fullRecords = false)
        val first = service.search(ScreenerQuery(ranges = listOf(RangeFilter("pe", max = 1000.0))))
        assertFalse(first.universe.complete)
        assertEquals(0, first.universe.evaluated)
        assertTrue(first.warnings.first().contains("0 of 5"))
        withTimeout(5_000) { while (loads.get() < 2) delay(20) }
        delay(200)
        val second = service.search(ScreenerQuery(ranges = listOf(RangeFilter("pe", max = 999.0))))
        assertEquals(2, second.universe.evaluated)
        assertEquals(2, loads.get()) // hourly budget respected
        // Market-only filters evaluate the whole universe without fundamentals.
        assertTrue(service.search(ScreenerQuery(ranges = listOf(RangeFilter("marketCap", min = 1.0)))).universe.complete)
    }
}
