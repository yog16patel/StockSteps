package org.example.stocksteps.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.earnings.EarningsService
import org.example.stocksteps.earnings.FinnhubEarningsDataSource
import org.example.stocksteps.model.ChartRange
import org.example.stocksteps.repositoryImpl.FinnhubNewsProviderRepositoryImpl
import org.example.stocksteps.repositoryImpl.FmpMarketDataProvider
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.screener.ComparisonHistoryService
import org.example.stocksteps.screener.FixtureScreenerUniverse
import org.example.stocksteps.screener.HistoryRange
import org.example.stocksteps.screener.MetricValue
import org.example.stocksteps.screener.ScreenerService
import org.example.stocksteps.screener.quarterRevenueGrowth
import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.InMemoryUserDataStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test

/**
 * Counts upstream provider requests for representative audit scenarios by running the REAL-mode adapters
 * (FMP, Finnhub) against a Ktor MockEngine: no network, no keys, deterministic. Writes the table to
 * `server/build/provider-benchmark.txt`. Used for the Phase 2 before/after comparison
 * (`docs/FINANCIAL_API_CACHE_IMPLEMENTATION.md`; the baseline ran this file on the pre-Phase-2 commit, where the
 * only difference was the market-status wiring that didn't exist yet). Assertions live in `FinancialCacheTest`.
 */
class ProviderRequestBenchmarkTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-07T18:00:00Z"), ZoneOffset.UTC)

    private class Upstream(failQuotes: Boolean = false) {
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val total get() = counts.values.sumOf { it.get() }
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath.substringAfter("/stable/").substringAfter("/api/v1/")
            val provider = when { "financialmodelingprep" in request.url.host -> "fmp"; "finnhub" in request.url.host -> "finnhub"; else -> request.url.host }
            counts.getOrPut("$provider $path") { AtomicInteger() }.incrementAndGet()
            delay(5) // a little latency so concurrent callers really overlap
            val symbol = request.url.parameters["symbol"] ?: "AAPL"
            val body = when {
                failQuotes && path == "quote" -> return@MockEngine respond("{}", HttpStatusCode.ServiceUnavailable)
                path == "quote" -> """[{"symbol":"$symbol","name":"$symbol Corp","price":100.0,"previousClose":99.0,"change":1.0,"changePercentage":1.01,"timestamp":1791396000}]"""
                path == "profile" -> """[{"symbol":"$symbol","companyName":"$symbol Corp","currency":"USD","exchange":"NASDAQ","sector":"Technology","industry":"Software"}]"""
                path.startsWith("search") -> """[{"symbol":"AAPL","name":"Apple Inc.","currency":"USD","exchange":"NASDAQ"}]"""
                path == "exchange-market-hours" -> """[{"exchange":"NYSE","isMarketOpen":true}]"""
                path == "historical-price-eod/light" -> (0 until 30).joinToString(",", "[", "]") { """{"date":"2026-09-${(it + 1).toString().padStart(2, '0')}","price":${100 + it}.0}""" }
                path == "historical-chart/5min" -> """[{"date":"2026-10-07 09:30:00","close":100.0},{"date":"2026-10-07 09:35:00","close":101.0},{"date":"2026-10-07 09:40:00","close":100.5}]"""
                path == "calendar/earnings" -> """{"earningsCalendar":[]}"""
                path == "stock/profile2" -> """{"name":"$symbol Corp","ticker":"$symbol"}"""
                path == "company-news" || path == "news" -> "[]"
                else -> "[]" // statements, ratios, estimates, dividends: empty (counts are what matter)
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json(json) } }
    }

    private class World(val up: Upstream, clock: Clock) {
        val fmp = FmpStockProviderRepositoryImpl(up.client, "test-key") { java.time.LocalDate.parse("2026-10-07") }
        val stocks = StockService(fmp, fmp)
        private val prices = FmpPriceHistoryProvider(up.client, "test-key")   // one instance, as in Application.realDataSources
        val charts = PriceChartService(prices)
        val sparklines = SparklineService(prices)
        val financials = CompanyFinancialService(fmp)
        val valuation = ValuationService(fmp, charts, fmp)
        // As wired in Application: market status from the exchange calendar (Phase 2).
        val details = CompanyDetailsService(stocks, financials, FmpMarketDataProvider(up.client, "test-key"), valuation = valuation,
            marketStatus = UsMarketCalendar().let { c -> { c.marketStatusAt(clock.instant()) } })
        val news = NewsService(FinnhubNewsProviderRepositoryImpl(up.client, "test-key"))
        val store = InMemoryUserDataStore()
        val entitlements = EntitlementService(store, clock::millis, debugAllowed = true)
        val earnings = EarningsService(FinnhubEarningsDataSource(up.client, "test-key"), stocks, charts, store, entitlements, clock, sampleData = false, research = null)
        val screener = ScreenerService(FixtureScreenerUniverse(), stocks, { financials.getFundamentals(it, "annual") }, charts, { 0.73 }, clock,
            sampleData = false, fundamentalsPerHour = 0, fullRecords = false,
            quarterlyRevenueGrowth = { s -> try { quarterRevenueGrowth(earnings.latestResults(s)) } catch (_: Exception) { MetricValue(note = "none") } })
        val history = ComparisonHistoryService(stocks, { s, p -> financials.getFundamentals(s, p) }, entitlements, clock, sampleData = false, source = "test")
    }

    private suspend fun <T> quiet(block: suspend () -> T): T? = try { block() } catch (cause: Exception) { if (cause is CancellationException) throw cause; null }

    private fun table(name: String, up: Upstream) = buildString {
        append("## $name — total upstream: ${up.total}\n")
        up.counts.toSortedMap().forEach { (k, v) -> append("  $k = ${v.get()}\n") }
    }

    @Test fun providerRequestScenarios() = runBlocking {
        val report = StringBuilder()

        // A/B: one company researched across features (Details → Financials → Valuation → Guided Research → chart/sparkline → earnings → search).
        Upstream().let { up ->
            val w = World(up, clock)
            quiet { w.details.getDetails("AAPL") }
            quiet { w.financials.getFundamentals("AAPL", "quarter") }
            quiet { w.valuation.history("AAPL") }
            quiet { w.details.getDetails("AAPL") }                           // Guided Research reads Company Details
            quiet { w.charts.getChart("AAPL", ChartRange.ONE_DAY) }
            quiet { w.sparklines.getSparkline("AAPL") }
            quiet { w.earnings.details("AAPL", null) }
            quiet { w.earnings.latestResults("AAPL") }
            quiet { w.stocks.searchStocks("apple") }
            quiet { w.stocks.searchStocks("apple") }
            report.append(table("Company research (AAPL across features)", up))
        }

        // D: four-company comparison with 1Y history, then a second user opens the same comparison.
        Upstream().let { up ->
            val w = World(up, clock)
            val symbols = listOf("AAPL", "MSFT", "KO", "RIVN")
            quiet { w.screener.compare(symbols.joinToString(",")) }
            quiet { w.screener.performance(symbols.joinToString(","), "1Y") }
            quiet { w.screener.performance(symbols.joinToString(","), "3Y") }
            quiet { w.history.data(symbols, HistoryRange.ONE_YEAR, plus = false) }
            quiet { w.screener.compare(symbols.joinToString(",")) }
            quiet { w.details.getDetails("AAPL") }
            report.append(table("Four-company comparison (+1Y history, repeat, then AAPL details)", up))
        }

        // Concurrency: 50 users open AAPL Company Details at the same moment.
        Upstream().let { up ->
            val w = World(up, clock)
            coroutineScope { (1..50).map { async(Dispatchers.Default) { quiet { w.details.getDetails("AAPL") } } }.awaitAll() }
            report.append(table("50 concurrent identical Company Details (AAPL)", up))
        }

        // Failure stampede: the quote provider is down while 30 requests arrive together.
        Upstream(failQuotes = true).let { up ->
            val w = World(up, clock)
            coroutineScope { (1..30).map { async(Dispatchers.Default) { quiet { w.stocks.getStock("AAPL") } } }.awaitAll() }
            report.append(table("30 concurrent quotes while the provider fails (503)", up))
        }

        val text = report.toString()
        println(text)
        File("build/provider-benchmark.txt").writeText(text)
    }
}
