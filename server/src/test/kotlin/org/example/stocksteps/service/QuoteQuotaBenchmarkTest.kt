package org.example.stocksteps.service

import io.ktor.client.request.parameter
import kotlinx.coroutines.*
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.model.PricePoint
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.repository.NewsProviderRepository
import org.example.stocksteps.repository.StockQuoteProviderRepository
import org.example.stocksteps.repository.models.FmpQuote
import org.example.stocksteps.repository.models.toStockQuote
import org.example.stocksteps.repositoryImpl.FmpMarketDataProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 7A request benchmark (MockEngine, fake clocks, no network): upstream FMP `quote` requests before and after the quote outcome
 * cache and the daily allowance. "Before" replays the pre-7A repository rule exactly (`quote:SYM` cached only on success, 30 s; no daily
 * allowance) against the same mock. Writes `server/build/phase7a-quote-benchmark.txt`.
 */
class QuoteQuotaBenchmarkTest {
    private class World {
        val ms = AtomicLong(1_000_000)
        val up = FmpMock(latency = 2)
        val after = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { ms.get() }) { LocalDate.parse("2026-10-07") }
        /** The pre-Phase 7A `getQuote`: successes cached 30 s, failures not stored. */
        val before = object : StockQuoteProviderRepository {
            val cache = CompanyFinancialCache(capacity = 4_096, now = { ms.get() })
            override suspend fun getQuote(symbol: String): StockQuote? = cache.getOrLoad("quote:$symbol", FinancialCachePolicy.QUOTE) {
                listOfNotNull(up.client.apiCall<List<FmpQuote>>("https://financialmodelingprep.com/stable/quote", "k") { parameter("symbol", symbol) }
                    .singleOrNull()?.toStockQuote())
            }.firstOrNull()
        }
        fun quotes() = up.n("quote")
    }

    private suspend fun measure(count: World.() -> Int = { quotes() }, block: suspend World.(StockQuoteProviderRepository) -> Unit): Pair<Int, Int> {
        val b = World().let { w -> w.block(w.before); w.count() }
        val a = World().let { w -> w.block(w.after); w.count() }
        return b to a
    }
    private val sectorQuotes: World.() -> Int = { up.log.count { it.startsWith("quote|XL") } }

    private suspend fun StockQuoteProviderRepository.read(symbol: String) = runCatching { getQuote(symbol) }

    private val emptyNews = NewsService(object : NewsProviderRepository { override suspend fun getNews(page: Int, limit: Int) = emptyList<NewsArticle>() })

    private fun World.markets(quotes: StockQuoteProviderRepository, at: String) = MarketsService(FmpMarketDataProvider(up.client, "k"), quotes,
        object : IndexDataSource {
            override suspend fun indexQuote(symbol: String) = runCatching { quotes.getQuote(symbol) }.getOrNull()
            override suspend fun indexHistory(symbol: String) = emptyList<PricePoint>()
        }, emptyNews, MarketsSourceLabels("u", "q", false, "Delayed"), clock = Clock.fixed(Instant.parse(at), ZoneOffset.UTC),
        cache = CompanyFinancialCache(capacity = 128, now = { ms.get() }))

    private fun World.refuseSectors() = MarketsService.SECTORS.forEach { (_, s, _) -> up.symbolStatus["quote|$s"] = 402 }

    @Test fun benchmark() = runBlocking {
        val rows = mutableListOf<Triple<String, Int, Int>>()
        fun row(name: String, r: Pair<Int, Int>) { rows += Triple(name, r.first, r.second) }

        row("10 repeated reads, one restricted symbol (402), within 1 min", measure { q ->
            up.symbolStatus["quote|XLK"] = 402; repeat(10) { q.read("XLK"); ms.addAndGet(5_000) } })
        row("20 concurrent reads, one restricted symbol (402)", measure { q ->
            up.symbolStatus["quote|XLK"] = 402; (1..20).map { async(Dispatchers.Default) { q.read("XLK") } }.awaitAll() })
        row("10 different restricted symbols, 3 reads each", measure { q ->
            repeat(10) { up.symbolStatus["quote|R$it"] = 402 }; repeat(3) { repeat(10) { i -> q.read("R$i") } } })
        row("Markets closed: 3 overview builds in 58 min (29-min steps), all 11 sector funds refused", measure(sectorQuotes) { q ->
            refuseSectors(); val m = markets(q, "2026-10-08T01:30:00Z"); repeat(3) { m.overview(); ms.addAndGet(29 * 60_000) } })
        row("Markets live: 29 overview builds in 58 min (every 2 min), all 11 sector funds refused", measure(sectorQuotes) { q ->
            refuseSectors(); val m = markets(q, "2026-10-07T15:00:00Z"); repeat(29) { m.overview(); ms.addAndGet(121_000) } })
        row("429: 5 reads within 10 min, then 1 after", measure { q ->
            up.symbolStatus["quote|MSFT"] = 429; repeat(5) { q.read("MSFT"); ms.addAndGet(110_000) }; ms.addAndGet(60_000); q.read("MSFT") })
        row("Daily allowance 10, 50 concurrent callers on 50 symbols", measure { q ->
            val guard = ProviderGuard(ProviderBudgetConfig(mapOf("fmp" to ProviderLimits(600, 600, 100, dailyLimit = if (q === before) null else 10)), safetyMargin = 1.0),
                { 1_800_000_000_000 }, ProviderUsageMeter(), sleep = { })
            withContext(guard) { (1..50).map { async(Dispatchers.Default) { q.read("C$it") } }.awaitAll() } })

        // Markets rows count only the 11 sector funds' quote requests.
        val text = buildString {
            appendLine("Phase 7A quote benchmark (MockEngine; upstream FMP `quote` requests; before = pre-7A rule replayed)")
            appendLine("| Scenario | Before | After |"); appendLine("|---|---:|---:|")
            rows.forEach { (n, b, a) -> appendLine("| $n | $b | $a |") }
        }
        File("build/phase7a-quote-benchmark.txt").writeText(text)
        println(text)
        val after = rows.associate { it.first to it.third }
        assertEquals(1, after.getValue("10 repeated reads, one restricted symbol (402), within 1 min"))
        assertEquals(1, after.getValue("20 concurrent reads, one restricted symbol (402)"))
        assertEquals(10, after.getValue("10 different restricted symbols, 3 reads each"))
        assertEquals(2, after.getValue("429: 5 reads within 10 min, then 1 after"))
        assertEquals(10, after.getValue("Daily allowance 10, 50 concurrent callers on 50 symbols"))
    }
}
