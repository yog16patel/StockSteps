package org.example.stocksteps.market

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
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.repositoryImpl.fixture.SampleCompanyCatalog
import org.example.stocksteps.screener.*
import org.example.stocksteps.service.*
import org.example.stocksteps.stockRoutes
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.test.*

/** Ubuntu MOCK backend: the sample company catalog widens the screener to ~150 US stocks without any provider call. */
class MockCatalogTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val fixture = FixtureMarketDataSource(now = clock::instant, sampleFallback = true)
    private val stocks = StockService(fixture, fixture)
    private val financials = CompanyFinancialService(fixture)
    private val catalog = SampleCompanyCatalog.companies

    /** The detailed list from the Ubuntu migration brief; each has captured fixtures or a catalog entry. */
    private val detailed = listOf("AAPL", "MSFT", "NVDA", "GOOGL", "AMZN", "META", "TSLA", "AMD", "NFLX", "JPM", "V", "MA", "COST", "WMT", "KO",
        "PEP", "JNJ", "UNH", "XOM", "CVX", "DIS", "BA", "INTC", "ORCL", "CRM", "ADBE", "PLTR", "UBER", "SHOP", "PYPL")

    @Test fun catalogIsUsOnlyUniqueAndLabelledSimulated() {
        assertTrue(catalog.size >= 130)
        assertTrue(catalog.values.all { it.exchange in setOf("NYSE", "NASDAQ") && it.price > 0 && it.sharesBillions > 0 })
        assertTrue(catalog.values.all { (it.pe != null) == (it.netMargin > 0) }, "a P/E exactly when profitable")
        val text = requireNotNull(javaClass.classLoader.getResource("fixtures/stocks/catalog.json")).readText()
        assertTrue(text.contains("Not real or current financial data"))
    }

    @Test fun everyBriefCompanyHasQuoteProfileChartsAndFundamentals() = runBlocking {
        for (symbol in detailed) {
            val quote = assertNotNull(fixture.getQuote(symbol), symbol)
            assertNotNull(quote.price, symbol)
            assertNotNull(fixture.getProfile(symbol)?.sector, symbol)
            assertTrue(fixture.getDailyCloses(symbol).size >= 1_000, "$symbol has ~5 years of daily closes")
            assertTrue(fixture.getIntradayPoints(symbol).isNotEmpty(), symbol)
        }
    }

    @Test fun catalogCompaniesAreInternallyConsistent() = runBlocking {
        for (symbol in listOf("V", "COST", "WMT", "PEP", "CVX", "ORCL", "PLTR")) {
            val company = catalog.getValue(symbol)
            val quote = assertNotNull(fixture.getQuote(symbol))
            val price = quote.price!!
            assertTrue(abs(price / company.price - 1) <= 0.04, "$symbol price stays near its anchor")
            assertEquals(company.name, quote.companyName)
            assertEquals(company.name, fixture.getProfile(symbol)?.companyName)
            assertEquals(company.sector, fixture.getProfile(symbol)?.sector)
            assertTrue(fixture.getProfile(symbol)!!.description!!.contains("simulated"))
            // Chart, quote and 52-week range agree.
            val daily = fixture.getDailyCloses(symbol)
            assertEquals(price, daily.last().close)
            val year = daily.takeLast(252).map { it.close }
            assertTrue(quote.yearHigh!! >= year.max() && quote.yearLow!! <= year.min(), symbol)
            // P/E = price ÷ latest EPS, and it is the catalog's P/E.
            val fundamentals = fixture.getFundamentals(symbol, "annual")
            val pe = fundamentals.valuation.metrics.getValue("pe").value!!
            val eps = fundamentals.history.first().epsDiluted!!
            assertTrue(abs(pe / company.pe!! - 1) < 0.01, "$symbol P/E $pe vs ${company.pe}")
            assertTrue(abs(price / eps / pe - 1) < 0.02, "$symbol price/EPS ${price / eps} vs P/E $pe")
            val dividend = fundamentals.financials.shareholderReturns.getValue("dividendYield")
            if (company.dividendYield > 0) assertEquals(company.dividendYield, dividend.value)
            else assertEquals(FinancialAvailability.NO_DIVIDEND, dividend.availability)
        }
    }

    @Test fun lossMakingSampleHasNoPe() = runBlocking {
        val pe = fixture.getFundamentals("BA", "annual").valuation.metrics.getValue("pe")
        assertNull(pe.value)
        assertEquals(FinancialAvailability.NON_POSITIVE_DENOMINATOR, pe.availability)
        assertTrue(fixture.getFundamentals("BA", "annual").history.first().netIncome!! < 0)
    }

    @Test fun catalogIsOnlyUsedWhenGapsAreFilled() = runBlocking {
        val plain = FixtureMarketDataSource(now = clock::instant)
        assertNull(plain.getQuote("COST"))
        assertTrue(plain.searchStocks("costco").isEmpty())
        assertEquals(listOf("COST"), fixture.searchStocks("costco").map { it.symbol })
        // Captured fixtures always win over the catalog.
        assertEquals(plain.getQuote("AAPL")?.price, fixture.getQuote("AAPL")?.price)
    }

    private fun service() = ScreenerService(FixtureScreenerUniverse(includeCatalog = true), stocks, { financials.getFundamentals(it, "annual") },
        PriceChartService(fixture), { 1 / 1.35 }, clock, sampleData = true, fundamentalsPerHour = 0, fullRecords = true,
        provenance = listOf("Sample fixture data for development; not live market data."))

    private fun ApplicationTestBuilder.install() = application {
        configureApiErrors()
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        routing { screenerRoutes(service(), RequestRateLimiter(1_000)); stockRoutes(stocks) }
    }

    private suspend fun ApplicationTestBuilder.search(query: ScreenerQuery) = client.post("/api/v1/screener/search") {
        contentType(ContentType.Application.Json); setBody(json.encodeToString(ScreenerQuery.serializer(), query))
    }
    private suspend fun ApplicationTestBuilder.page(query: ScreenerQuery) = json.decodeFromString(ScreenerPage.serializer(), search(query).bodyAsText())

    @Test fun screenerCoversAbout150UsStocksWithFiltersAndPaging() = testApplication {
        install()
        val usage = ProviderUsageMeter.shared.count(event = "upstream")
        val us = page(ScreenerQuery(choices = listOf(ChoiceFilter(ChoiceField.COUNTRY, listOf("US"))), pageSize = 50))
        assertTrue(us.total >= 150, "US companies: ${us.total}")
        assertTrue(us.sampleData)
        assertTrue(us.rows.map { it.company.symbol }.containsAll(listOf("AAPL", "COST")) || us.nextCursor != null)
        // Paging walks the whole universe once, in order.
        val query = ScreenerQuery(sort = ScreenerSort(SortField.MARKET_CAP, descending = true), pageSize = 40)
        val symbols = mutableListOf<String>()
        var next: ScreenerQuery? = query
        while (next != null) { val p = page(next); symbols += p.rows.map { it.company.symbol }; next = p.nextCursor?.let { query.copy(cursor = it) } }
        assertEquals(symbols.distinct(), symbols)
        assertTrue(symbols.size >= 158 && symbols.containsAll(detailed))
        // Sector, P/E, dividend-yield and market-cap filters apply server-side.
        val tech = page(ScreenerQuery(choices = listOf(ChoiceFilter(ChoiceField.SECTOR, listOf("Technology"))), pageSize = 50))
        assertTrue(tech.total >= 30 && tech.rows.all { it.company.sector == "Technology" })
        val cheap = page(ScreenerQuery(ranges = listOf(RangeFilter("pe", max = 15.0)), pageSize = 50))
        assertTrue(cheap.total > 0 && cheap.rows.all { it.company.metrics.getValue("pe").value!! <= 15.0 })
        assertTrue(cheap.rows.none { it.company.symbol == "BA" }, "no P/E, never in a P/E filter")
        val income = page(ScreenerQuery(ranges = listOf(RangeFilter("dividendYield", min = 3.0)), pageSize = 50))
        assertTrue(income.rows.map { it.company.symbol }.containsAll(listOf("VZ", "PFE")))
        assertEquals(HttpStatusCode.BadRequest, search(ScreenerQuery(ranges = listOf(RangeFilter("pe", 30.0, 10.0)))).status)
        assertEquals(usage, ProviderUsageMeter.shared.count(event = "upstream"), "MOCK makes no provider requests")
    }

    @Test fun unknownAndInvalidSymbols() = testApplication {
        install()
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/COST/quote").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/not%20a%20symbol/quote").status)
        assertTrue(client.get("/api/v1/stocks/search?query=visa").bodyAsText().contains("\"V\""))
    }
}
