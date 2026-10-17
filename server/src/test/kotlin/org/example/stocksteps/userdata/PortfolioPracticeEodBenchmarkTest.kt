package org.example.stocksteps.userdata

import kotlinx.coroutines.*
import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.portfolio.*
import org.example.stocksteps.practice.OrderSide
import org.example.stocksteps.practice.PracticeOrderRequest
import org.example.stocksteps.practice.PracticeService
import org.example.stocksteps.practice.WatchPracticeMarket
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.service.*
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 7C benchmark (MockEngine, fake clocks, no network): upstream FMP `quote` / `profile` / `historical-price-eod/light` and Bank of
 * Canada requests for My Portfolio summaries and Practice overviews. "Before" = the quote path (Phase 7A); "after" = the EOD path.
 * Writes `server/build/phase7c-portfolio-benchmark.txt`.
 */
class PortfolioPracticeEodBenchmarkTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
        fun minutes(m: Long) { instant = instant.plusSeconds(m * 60) }
    }

    private class World(val eodPath: Boolean, start: String) {
        val clock = MutableClock(Instant.parse(start))
        val up = FmpMock(latency = 1)
        val boc = AtomicInteger()
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { clock.millis() }) { LocalDate.parse("2026-10-16") }
        val charts = PriceChartService(FmpPriceHistoryProvider(up.client, "k"), CompanyFinancialCache(capacity = 256, now = { clock.millis() }), eod = EodSessions(clock))
        val news = NewsService(object : NewsProviderRepository { override suspend fun getNews(page: Int, limit: Int) = emptyList<NewsArticle>() })
        val watch = WatchMarketData(StockService(fmp, fmp), null, news, CompanyFinancialCache(capacity = 2_048, now = { clock.millis() }))
        val fx = object : PortfolioFxSource {
            val cache = CompanyFinancialCache(capacity = 64, now = { clock.millis() })
            override suspend fun rates(from: String, through: String) = cache.getOrLoad("$from:$through", 21_600_000) {
                boc.incrementAndGet(); mapOf("2026-10-01" to "1.3", "2026-10-16" to "1.4") }
        }
        val store = InMemoryUserDataStore()
        val ledger = PortfolioService(store, clock::millis)
        val portfolio = PortfolioMarketService(ledger, FmpPriceHistoryProvider(up.client, "k"), watch, fx, clock, dailyCloses = charts::getDailyCloses,
            eod = EodSessions(clock).takeIf { eodPath })
        val practice = PracticeService(store, EntitlementService(store, clock::millis, debugAllowed = true), WatchPracticeMarket(watch, charts::getDailyCloses),
            fx, clock, clock, null, sampleData = false, eod = EodSessions(clock).takeIf { eodPath })

        fun serve(symbol: String, status: Int? = null, empty: Boolean = false) {
            up.bodies["historical-price-eod/light|$symbol"] = if (empty) "[]" else """[{"date":"2026-10-15","price":100.0},{"date":"2026-10-16","price":101.0}]"""
            // Quote timestamps at Friday's close, so the quote path treats them as current.
            up.bodies["quote|$symbol"] = """[{"symbol":"$symbol","price":101.0,"previousClose":100.0,"timestamp":1792180800}]"""
            status?.let { up.symbolStatus[symbol] = it }
        }
        suspend fun holdings(uid: String, symbols: List<String>) {
            ledger.saveAccount(uid, PortfolioAccount("a", "CAD account"))
            symbols.forEach { s -> serve(s); ledger.saveTransaction(uid, PortfolioTransaction("t-$s", "a", TransactionType.OPENING_POSITION, "2026-10-01",
                PortfolioCurrency.USD, InstrumentRef(s, currency = "USD"), "1", "90"), false) }
        }
        suspend fun summary(uid: String = "alice") = runCatching { portfolio.report(uid, "a", "1M", false) }
        fun counts() = listOf(up.n("quote"), up.n("profile"), up.n("historical-price-eod/light"), boc.get())
    }

    private data class Row(val name: String, val before: List<Int>, val after: List<Int>)

    private suspend fun scenario(name: String, start: String = "2026-10-16T22:00:00Z", run: suspend World.() -> Unit): Row =
        Row(name, World(false, start).apply { run() }.counts(), World(true, start).apply { run() }.counts())

    @Test fun benchmark() = runBlocking {
        val five = (1..5).map { "S$it" }; val twenty = (1..20).map { "S$it" }
        val rows = listOf(
            scenario("Portfolio, 5 holdings, one summary") { holdings("alice", five); summary() },
            scenario("Portfolio, 20 holdings, one summary") { holdings("alice", twenty); summary() },
            scenario("Portfolio, 5 holdings, 10 refreshes 1 min apart") { holdings("alice", five); repeat(10) { summary(); clock.minutes(1) } },
            scenario("10 users holding the same stock, concurrent") { (1..10).forEach { holdings("u$it", listOf("SAME")) }
                (1..10).map { async(Dispatchers.Default) { summary("u$it") } }.awaitAll() },
            scenario("Weekend: 5 holdings, a refresh every hour for 24 h", "2026-10-17T12:00:00Z") { holdings("alice", five); repeat(24) { summary(); clock.minutes(60) } },
            scenario("5 holdings, one without a close, 10 refreshes over 1 h") { holdings("alice", five); serve("S5", empty = true); repeat(10) { summary(); clock.minutes(6) } },
            scenario("5 holdings, one refused (402), 10 refreshes over 1 h") { holdings("alice", five); serve("S5", status = 402); repeat(10) { summary(); clock.minutes(6) } },
            scenario("Practice, 4 positions, overview 10 times 1 min apart") {
                listOf("P1", "P2", "P3", "P4").forEach { serve(it) }
                listOf("P1", "P2", "P3", "P4").forEachIndexed { i, s -> runCatching { practice.execute("alice", PracticeOrderRequest(s, OrderSide.BUY, quantity = "1",
                    idempotencyKey = "bench-key-$i", reviewedPrice = practice.preview("alice", PracticeOrderRequest(s, OrderSide.BUY, quantity = "1")).price)) } }
                repeat(10) { runCatching { practice.overview("alice") }; clock.minutes(1) } },
        )
        val text = buildString {
            appendLine("Phase 7C Portfolio/Practice benchmark (MockEngine; upstream requests; before = quote path, after = EOD path)")
            appendLine("| Scenario | Before quote | Before profile | Before EOD | Before BoC | After quote | After profile | After EOD | After BoC |")
            appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|")
            rows.forEach { r -> appendLine("| ${r.name} | ${r.before.joinToString(" | ")} | ${r.after.joinToString(" | ")} |") }
        }
        File("build/phase7c-portfolio-benchmark.txt").writeText(text)
        println(text)
        assertTrue(rows.all { it.after[0] == 0 }, "no quote on the EOD path")
        assertEquals(1, rows.single { it.name.startsWith("10 users") }.after[2], "one close request shared by ten users")
    }
}
