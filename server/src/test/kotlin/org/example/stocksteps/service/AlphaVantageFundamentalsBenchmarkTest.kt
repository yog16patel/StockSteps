package org.example.stocksteps.service

import kotlinx.coroutines.*
import org.example.stocksteps.marketdata.alphavantage.*
import org.example.stocksteps.repository.Statement
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 9B.8 fundamentals request benchmark (MockEngine, fixed clock). Upstream requests by provider (Alpha Vantage, SEC, FMP, Finnhub)
 * with `FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage` over an FMP base provider (prices/quotes from FMP). Writes
 * `server/build/phase9b8-fundamentals-benchmark.txt`.
 */
class AlphaVantageFundamentalsBenchmarkTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-16T22:00:00Z"), ZoneOffset.UTC)

    private inner class World {
        val av = AlphaVantageClientTest.Av { 0 }
        val sessions = EodSessions(clock)
        val fmp = AtomicInteger(); val finnhub = AtomicInteger(); val sec = AtomicInteger()
        val base = object : org.example.stocksteps.repository.StockProviderRepository {
            override suspend fun getQuote(symbol: String) = run { fmp.incrementAndGet()
                org.example.stocksteps.model.StockQuote(symbol, null, 50.0, 0.0, 0.0, 50.0, 50.0, timestamp = Instant.parse("2026-10-16T20:00:00Z").epochSecond) }
            override suspend fun getGainers() = emptyList<org.example.stocksteps.model.MarketMover>()
            override suspend fun getLosers() = emptyList<org.example.stocksteps.model.MarketMover>()
            override suspend fun getProfile(symbol: String): org.example.stocksteps.model.CompanyProfile? = null
            override suspend fun searchStocks(query: String) = emptyList<org.example.stocksteps.model.StockSearchResult>()
            override suspend fun getFundamentals(symbol: String, period: String) = run { fmp.addAndGet(14); org.example.stocksteps.model.CompanyFundamentals(symbol) }
        }
        val client = AlphaVantageClient(av.http, AlphaVantageConfig("k", false, 75, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet()), sessions, clock,
            CompanyFinancialCache(capacity = 4_096, now = { 0 }))
        val provider = AlphaVantageFundamentalsProvider(client, base, sessions, clock, CompanyFinancialCache(capacity = 1_024, now = { 0 }))
        // The quote is cached by StockService in the real wiring; here the base counts every call (an upper bound for FMP).
        val financial = CompanyFinancialService(provider)
        init {
            av.bodies["INCOME_STATEMENT"] = AvFixtures.INCOME; av.bodies["BALANCE_SHEET"] = AvFixtures.BALANCE; av.bodies["CASH_FLOW"] = AvFixtures.CASH_FLOW
            av.bodies["DIVIDENDS"] = AvFixtures.DIVIDENDS; av.bodies["SPLITS"] = AvFixtures.SPLITS
        }
        fun counts() = "AV ${av.total} · SEC ${sec.get()} · FMP ${fmp.get()} · Finnhub ${finnhub.get()}"
    }

    @Test fun benchmark() = runBlocking<Unit> {
        val rows = mutableListOf<Pair<String, String>>()
        World().let { w -> w.financial.getFundamentals("AAPL", "annual"); rows += "Company Details fundamentals, cold" to w.counts()
            val cold = w.av.total; w.financial.getFundamentals("AAPL", "annual"); w.financial.getFundamentals("AAPL", "quarter")
            rows += "… then warm (annual + quarter)" to "AV ${w.av.total - cold} more"; assertEquals(5, w.av.total) }
        World().let { w -> (1..100).map { async(Dispatchers.Default) { w.financial.getFundamentals("AAPL", "annual") } }.awaitAll()
            rows += "100 simultaneous opens of one company" to w.counts(); assertEquals(5, w.av.total) }
        World().let { w -> (1..1000).map { i -> async(Dispatchers.Default) { w.financial.getFundamentals("S${i % 20}", "annual") } }.awaitAll()
            rows += "1,000 users across 20 companies" to w.counts(); assertEquals(100, w.av.total) }
        World().let { w -> w.financial.getFundamentals("AAPL", "annual"); val before = w.av.total
            w.financial.statementHistory("AAPL", "annual", Statement.entries.toSet()); w.financial.statementHistory("AAPL", "quarter", setOf(Statement.INCOME))
            w.financial.screenerFundamentals("AAPL", "USD")
            rows += "Comparison history + Discover + AI input after Details" to "AV ${w.av.total - before} more (statements shared)"; assertEquals(before, w.av.total) }
        World().let { w -> CuratedUniverseNames.take(155).forEach { w.financial.screenerFundamentals(it, "USD") }
            rows += "Discover screener fundamentals, 155 companies (warm-up, if enabled)" to w.counts() }
        val text = buildString {
            appendLine("Phase 9B.8 fundamentals benchmark (MockEngine; upstream requests by provider; FMP counts the base quote per computed bundle)")
            appendLine("| Scenario | Requests |"); appendLine("|---|---|"); rows.forEach { (k, v) -> appendLine("| $k | $v |") }
        }
        File("build/phase9b8-fundamentals-benchmark.txt").writeText(text); println(text)
    }

    private val CuratedUniverseNames get() = org.example.stocksteps.screener.CuratedUniverseFile.load().companies.map { it.symbol }
}
