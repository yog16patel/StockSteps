package org.example.stocksteps.service

import kotlinx.coroutines.*
import org.example.stocksteps.earnings.DataFreshness
import org.example.stocksteps.earnings.EarningsService
import org.example.stocksteps.earnings.FinnhubEarningsDataSource
import org.example.stocksteps.portfolio.analytics.DebugEntitlementRequest
import org.example.stocksteps.portfolio.analytics.SubscriptionTier
import org.example.stocksteps.repositoryImpl.FmpMarketDataProvider
import org.example.stocksteps.repositoryImpl.FmpPriceHistoryProvider
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.screener.*
import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.InMemoryUserDataStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Financial API Phase 3 before/after benchmark (scenarios A–H). REAL FMP/Finnhub adapters on a Ktor MockEngine ([FmpMock]),
 * one instance, fake clocks (cache expiry, market sessions, warm-up budget and earnings windows all follow [MutableClock]);
 * no network, no keys. Every number is a **measured** upstream request count by provider and endpoint.
 *
 * - **after**: the Phase 3 configuration wired by `Application.realDataSources` (market-aware lifetimes, earnings signals,
 *   selective statements, screener dataset set, 50 per exchange, 4,096-entry FMP cache).
 * - **legacy config**: the same code with the Phase 3 switches off (fixed lifetimes, no signals, full bundles, 100 per
 *   exchange, 512 entries). It reproduces the pre-Phase 3 request paths except the screener re-warm, which can't be switched
 *   back on; the pre-Phase 3 screener numbers come from running the old code (`e4ba2be`) — see
 *   `docs/FINANCIAL_API_PHASE3_IMPLEMENTATION.md` §5.
 *
 * Writes `server/build/phase3-benchmark.txt`.
 */
class Phase3AuditBenchmarkTest {
    private val wednesday = Instant.parse("2026-10-07T18:00:00Z")   // 14:00 ET: US and TSX regular session

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
        fun advance(seconds: Long) { instant = instant.plusSeconds(seconds) }
    }

    private class World(val up: FmpMock, val clock: MutableClock, val scope: CoroutineScope, perHour: Int = 0, val legacy: Boolean = false,
                        capacity: Int? = null, universeLimit: Int = if (legacy) 100 else 50) {
        val meter = ProviderUsageMeter()
        val signals = if (legacy) null else EarningsStatementSignals({ clock.instant() }, meter = meter)
        val freshness = if (legacy) MarketFreshnessPolicy.FIXED else MarketFreshnessPolicy({ clock.instant() })
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", capacity ?: if (legacy) 512 else FmpStockProviderRepositoryImpl.DEFAULT_DATASET_CACHE_ENTRIES,
            cacheNow = { clock.millis() }, freshness = freshness, statementSignals = signals, wallClock = { clock.instant() }) { LocalDate.parse("2026-10-07") }
        val stocks = StockService(fmp, fmp)
        val charts = PriceChartService(FmpPriceHistoryProvider(up.client, "k", CompanyFinancialCache(capacity = 256, now = { clock.millis() }), freshness))
        val financials = CompanyFinancialService(fmp)
        val valuation = ValuationService(fmp, charts, fmp)
        val details = CompanyDetailsService(stocks, financials, FmpMarketDataProvider(up.client, "k"), valuation = valuation,
            marketStatus = UsMarketCalendar().let { c -> { c.marketStatusAt(clock.instant()) } })
        val store = InMemoryUserDataStore()
        val entitlements = EntitlementService(store, clock::millis, debugAllowed = true)
        val earnings = EarningsService(FinnhubEarningsDataSource(up.client, "k"), stocks, charts, store, entitlements, clock, sampleData = false, research = null,
            statementSignals = signals)
        val screener = ScreenerService(FmpScreenerUniverse(up.client, "k", listOf("NASDAQ", "NYSE", "TSX"), universeLimit, 2_000_000_000L), stocks,
            { financials.getFundamentals(it, "annual") }, charts, { 0.73 }, clock, sampleData = false, fundamentalsPerHour = perHour, fullRecords = false,
            scope = scope, quarterlyRevenueGrowth = { s -> try { quarterRevenueGrowth(earnings.latestResults(s)) } catch (_: Exception) { MetricValue(note = "none") } },
            screenerFundamentalsOf = if (legacy) null else { s, c -> financials.screenerFundamentals(s, c) }, meter = meter)
        val history = if (legacy) ComparisonHistoryService(stocks, { s, p -> financials.getFundamentals(s, p) }, entitlements, clock, sampleData = false, source = "test",
                cache = CompanyFinancialCache(capacity = 256, now = { clock.millis() }))
            else ComparisonHistoryService(stocks, { s, p -> financials.getFundamentals(s, p) }, entitlements, clock, sampleData = false, source = "test",
                cache = CompanyFinancialCache(capacity = 256, now = { clock.millis() }), statementsOf = { s, p, set -> financials.statementHistory(s, p, set) },
                statementSignals = signals)
        val research = ComparisonResearchService(store, entitlements, { s -> screener.compare(s.joinToString(",")) }, history, clock)
        val ai = ComparisonAiService({ s -> screener.compare(s.joinToString(",")) }, history, store, entitlements, TemplateComparisonAi(),
            ComparisonAiQuota(store, clock), clock, sampleData = false, source = "test")
        suspend fun settle() { while (true) { val running = scope.coroutineContext.job.children.toList(); if (running.isEmpty()) return; running.joinAll() } }
    }

    private suspend fun <T> quiet(block: suspend () -> T): T? = try { block() } catch (cause: Exception) { if (cause is CancellationException) throw cause; null }
    private fun provider(path: String) = if (path.startsWith("calendar/")) "finnhub" else "fmp"
    private fun breakdown(up: FmpMock) = up.counts.toSortedMap().filterKeys { '?' !in it }.entries.joinToString("") { "    ${provider(it.key)} ${it.key} = ${it.value.get()}\n" }
    private val four = "AAPL,MSFT,KO,RIVN"
    private val out = StringBuilder()
    private fun section(title: String, legacy: FmpMock?, after: FmpMock, extra: String = "") {
        out.append("## $title — legacy config ${legacy?.total ?: "n/a"} → after ${after.total}$extra\n")
        legacy?.let { out.append("  legacy config:\n").append(breakdown(it)) }
        out.append("  after:\n").append(breakdown(after))
    }

    @Test fun scenarios() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // A — Company Details: Overview, Financials (annual and quarterly), Valuation.
            suspend fun a(legacy: Boolean) = FmpMock().also { up -> val w = World(up, MutableClock(wednesday), scope, legacy = legacy)
                quiet { w.details.getDetails("AAPL") }; quiet { w.financials.getFundamentals("AAPL", "annual") }
                quiet { w.financials.getFundamentals("AAPL", "quarter") }; quiet { w.valuation.history("AAPL") } }
            val aLegacy = a(true); val aAfter = a(false)
            section("A Company Details (overview, financials annual+quarter, valuation)", aLegacy, aAfter)
            assertEquals(aLegacy.total, aAfter.total, "Company Details keeps every dataset it displays")

            // B — Comparison of four, performance 1Y/3Y, 1Y history, 3Y/5Y history (StockSteps+), repeat comparison.
            var oneYear = IntArray(2); var longer = IntArray(2)
            suspend fun b(legacy: Boolean) = FmpMock().also { up -> val w = World(up, MutableClock(wednesday), scope, legacy = legacy)
                w.entitlements.simulate("plus", DebugEntitlementRequest(SubscriptionTier.PLUS))
                quiet { w.screener.compare(four) }
                listOf("1Y", "3Y").forEach { quiet { w.screener.performance(four, it) } }
                val t0 = up.total; quiet { w.history.publicHistory(four, "1Y") }; oneYear[if (legacy) 0 else 1] = up.total - t0
                val t1 = up.total; quiet { w.history.userHistory("plus", four, "3Y") }; quiet { w.history.userHistory("plus", four, "5Y") }; longer[if (legacy) 0 else 1] = up.total - t1
                quiet { w.screener.compare(four) } }
            val bLegacy = b(true); val bAfter = b(false)
            section("B Comparison of 4 (+performance 1Y/3Y, 1Y history, 3Y/5Y history, repeat)", bLegacy, bAfter,
                " (1Y history after the comparison: ${oneYear[0]} → ${oneYear[1]}; 3Y+5Y: ${longer[0]} → ${longer[1]})")
            // 1Y history cold (no comparison first): statements only.
            suspend fun bCold(legacy: Boolean) = FmpMock().also { up -> val w = World(up, MutableClock(wednesday), scope, legacy = legacy); quiet { w.history.publicHistory(four, "1Y") } }
            val coldLegacy = bCold(true); val coldAfter = bCold(false)
            val statements = { up: FmpMock -> listOf("income-statement", "balance-sheet-statement", "cash-flow-statement").sumOf { up.n(it) } }
            out.append("## B′ 1Y history, 4 cold companies — legacy config ${coldLegacy.total} (statements ${statements(coldLegacy)}) → after ${coldAfter.total} (statements ${statements(coldAfter)})\n")
            assertEquals(4, statements(coldAfter)); assertEquals(1, oneYear[1] / 4)

            // C — Premium research after a comparison: 3Y + 5Y, detailed summary, one AI question.
            val premium = IntArray(3)
            FmpMock().let { up -> val w = World(up, MutableClock(wednesday), scope)
                w.entitlements.simulate("plus", DebugEntitlementRequest(SubscriptionTier.PLUS))
                quiet { w.screener.compare(four) }
                val t0 = up.total
                quiet { w.history.userHistory("plus", four, "3Y") }; quiet { w.history.userHistory("plus", four, "5Y") }
                val t1 = up.total
                val session = w.research.create("plus", CreateResearchRequest(four.split(",")))
                quiet { w.research.summary("plus", session.session.id, true) }
                val t2 = up.total
                quiet { w.ai.ask("plus", ComparisonAiRequest(four.split(","), ComparisonAiType.QUESTION, "How has revenue changed over the years?", idempotencyKey = "phase3-benchmark"), null) }
                premium[0] = t1 - t0; premium[1] = t2 - t1; premium[2] = up.total - t2
                section("C Premium research after a 4-company comparison", null, up, " (3Y+5Y: ${premium[0]}, detailed summary: ${premium[1]}, AI question: ${premium[2]})") }
            assertEquals(listOf(0, 0, 0), premium.toList(), "premium research reuses the comparison's data")

            // D — Screener, 150 companies: catalog + first-hour warm-up, two searches, five Company Details.
            FmpMock(universeSize = 80).let { up -> val w = World(up, MutableClock(wednesday), scope, perHour = 25)
                quiet { w.screener.catalog() }; w.settle()
                val afterWarm = up.total
                val q = ScreenerQuery(ranges = listOf(RangeFilter("pe", max = 30.0)), sort = ScreenerSort(SortField.METRIC, "pe", false))
                val page = quiet { w.screener.search(q) }; quiet { w.screener.search(q.copy(sort = ScreenerSort(SortField.MARKET_CAP))) }; w.settle()
                val afterSearch = up.total
                listOf("NASDAQ0", "NASDAQ1", "NYSE5", "T3.TO", "NASDAQ49").forEach { quiet { w.details.getDetails(it) } }
                section("D Screener (universe 3×50 = ${w.screener.catalog().universe.size}, warm-up 25/h, 2 searches, 5 details)", null, up,
                    " (catalog + first-hour warm-up: $afterWarm = 3 universe + 25 × ${(afterWarm - 3) / 25}; searches: ${afterSearch - afterWarm}; 5 details: ${up.total - afterSearch}; " +
                        "coverage ${page?.universe?.evaluated}/${page?.universe?.size}; warning: \"${page?.warnings?.firstOrNull()}\")")
                assertEquals(3 + 25 * 11, afterWarm) }

            // D24 — 150 companies over 24 simulated hours: 10 concurrent users each hour, 20 companies failing in hours 12–13
            // (their second re-warm), the default cache and a 512-entry cache (eviction), and a budget too small for full coverage.
            var failedResponses = 0
        var denied = 0L
        suspend fun day(capacity: Int?, perHour: Int, failures: Boolean, budget: Boolean = false): Triple<List<Int>, List<Int>, Long> {
                val up = FmpMock(universeSize = 50); val clock = MutableClock(wednesday)
                // Phase 4C: the REAL development-default provider budget (FMP 600/min, burst 600 per instance) on the simulated clock.
                val meter = ProviderUsageMeter()
                val guard = if (budget) ProviderGuard(ProviderBudgetConfig(ProviderBudgetConfig.DEVELOPMENT), { clock.millis() }, meter) { clock.instant = clock.instant.plusMillis(it) } else null
                val dayScope = if (guard != null) CoroutineScope(SupervisorJob() + Dispatchers.Default + guard) else scope
                val w = World(up, clock, dayScope, perHour = perHour, capacity = capacity)
                val evictions = ProviderUsageMeter.shared.eventCount("cache.fmp.evict")
                val coverage = mutableListOf<Int>(); val requests = mutableListOf<Int>()
                for (hour in 0 until 24) {
                    if (failures) { if (hour == 12) (0 until 20).forEach { up.failSymbols += "NASDAQ$it" }; if (hour == 14) up.failSymbols.clear() }
                    val before = up.total
                    coroutineScope { repeat(10) { launch(Dispatchers.Default + (guard ?: kotlin.coroutines.EmptyCoroutineContext)) { quiet { w.screener.catalog() } } } }
                    w.settle()
                    coverage += w.screener.catalog().universe.evaluated; w.settle()
                    requests += up.total - before
                    clock.advance(3_600)
                }
                if (failures) failedResponses = up.failed.get()
                if (budget) { denied = meter.snapshot().second.filterKeys { it.startsWith("provider.fmp.budget.denied.") }.values.sum(); dayScope.cancel() }
                return Triple(coverage, requests, ProviderUsageMeter.shared.eventCount("cache.fmp.evict") - evictions)
            }
            val normal = day(null, 25, failures = false)
            val failing = day(null, 25, failures = true)
            val small = day(512, 25, failures = false)
            val tight = day(null, 10, failures = false)
            val budgeted = day(null, 25, failures = false, budget = true)
            fun line(name: String, r: Triple<List<Int>, List<Int>, Long>) =
                "  $name: coverage by hour ${r.first}\n    requests by hour ${r.second}; 24 h total ${r.second.sum()}; max/hour ${r.second.max()}; FMP cache evictions ${r.third}\n"
            out.append("## D24 Screener 150 companies over 24 h (10 concurrent users per hour)\n")
                .append(line("default (4,096 entries, 25/h)", normal)).append(line("20 companies failing in hours 12–13 (during their re-warm; $failedResponses failed responses, statements still cached or served stale, so they stay evaluated with partial data and retry after 1 h)", failing))
                .append(line("512-entry cache (pre-Phase 3 size)", small)).append(line("budget 10/h (incomplete coverage)", tight))
                .append(line("Phase 4C development provider budget (FMP 600/min, burst 600; warm-up is LOW priority; $denied requests denied)", budgeted))
                .append("  pre-Phase 3 (e4ba2be, measured separately): coverage peaks at 75, falls to 0 from hour 16; 1,953 requests in 24 h\n")
            assertTrue(normal.first.drop(6).all { it >= 125 } && normal.second.drop(1).all { it <= 25 * 11 + 3 })
            assertTrue(tight.first.all { it <= 60 }, "a small budget is reported as incomplete coverage, never as full")

            // E — 50 users, 10 overlapping symbols, all at once.
            FmpMock().let { up -> val w = World(up, MutableClock(wednesday), scope)
                val symbols = listOf("AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "KO", "JPM", "TD.TO", "RY.TO")
                coroutineScope { (0 until 50).map { i -> async(Dispatchers.Default) { quiet { w.details.getDetails(symbols[i % symbols.size]) } } }.awaitAll() }
                section("E 50 users, 10 overlapping symbols (Company Details)", null, up, " (${up.total / 10} per symbol)") }

            // F — Market closed: fundamentals at t, +10, +20 min and a quote every minute for 20 minutes.
            for ((label, at) in listOf("Saturday" to "2026-10-10T15:00:00Z", "US holiday (Thanksgiving)" to "2026-11-26T15:00:00Z", "open market (Wednesday)" to "2026-10-07T14:00:00Z")) {
                suspend fun f(legacy: Boolean) = FmpMock().also { up -> val clock = MutableClock(Instant.parse(at)); val w = World(up, clock, scope, legacy = legacy)
                    w.fmp.getFundamentals("AAPL", "annual"); repeat(2) { clock.advance(600); w.fmp.getFundamentals("AAPL", "annual") }
                    clock.instant = Instant.parse(at); repeat(20) { w.fmp.getQuote("MSFT"); clock.advance(60) } }
                val fl = f(true); val fa = f(false)
                val repeats = { up: FmpMock -> up.n("ratios-ttm") + up.n("key-metrics-ttm") - 2 }
                section("F $label: fundamentals t/+10/+20 min, quote each minute for 20 min", fl, fa,
                    " (TTM repeats ${repeats(fl)} → ${repeats(fa)}; quote requests ${fl.n("quote")} → ${fa.n("quote")})")
                if (label != "open market (Wednesday)") assertEquals(0, repeats(fa))
            }

            // G — Earnings: initial statements, report announced (Finnhub), delayed publication, new quarter, outage, correction.
            fun g(legacy: Boolean): Pair<FmpMock, String> = runBlocking {
                val up = FmpMock(); val clock = MutableClock(Instant.parse("2026-10-29T21:00:00Z")); val w = World(up, clock, scope, legacy = legacy)
                suspend fun latest() = w.fmp.getFundamentals("AAPL", "quarter").let { f -> f.history.maxByOrNull { it.date.orEmpty() }?.let { "${it.date}" } to f.freshness }
                latest()                                                                        // statements viewed before the report
                up.finnhubEvents = """{"symbol":"AAPL","date":"2026-10-29","year":2026,"quarter":4,"epsActual":1.6,"epsEstimate":1.5,"hour":"amc"}"""
                clock.advance(3_600); quiet { w.earnings.next("AAPL") }                       // the earnings screen loads the report (1 Finnhub request)
                val published = Instant.parse("2026-10-30T02:00:00Z")
                var visibleAt: Instant? = null; var staleSeen = false
                while (clock.instant < Instant.parse("2026-11-01T00:00:00Z")) {                 // a viewer every 30 minutes
                    clock.advance(1_800)
                    if (clock.instant >= published) up.newQuarter = "2026-09-27" to "2026-10-30 02:00:00"
                    if (clock.instant == Instant.parse("2026-10-31T02:00:00Z")) up.status["income-statement"] = 503      // outage for 1 h
                    if (clock.instant == Instant.parse("2026-10-31T03:00:00Z")) up.status.clear()
                    if (clock.instant == Instant.parse("2026-10-30T12:00:00Z")) up.correction = 5.0
                    val (date, freshness) = latest()
                    if (freshness == DataFreshness.STALE) staleSeen = true
                    if (visibleAt == null && date == "2026-09-27") visibleAt = clock.instant
                }
                val delay = visibleAt?.let { java.time.Duration.between(published, it).toMinutes() }
                up to "new quarter visible ${delay?.let { "$it min" } ?: "not within 46 h"} after publication; stale label during the outage: $staleSeen; " +
                    "loads still awaiting the period (2 h lifetime) ${w.meter.eventCount("fmp.statements.earningsRefresh")}; statement requests ${up.n("income-statement") + up.n("balance-sheet-statement") + up.n("cash-flow-statement") + up.n("income-statement-ttm") + up.n("cash-flow-statement-ttm")}; Finnhub requests ${up.n("calendar/earnings")}"
            }
            val (gl, gls) = g(true); val (ga, gas) = g(false)
            section("G Earnings (report 2026-10-29 after close, filing 4 h later, 1 h outage, correction; viewer every 30 min for ~2 days)", gl, ga,
                "\n  legacy config: $gls\n  after: $gas")

            // H — Screener expiration regression (15 companies, budget 25/h).
            FmpMock(universeSize = 5).let { up -> val clock = MutableClock(wednesday); val w = World(up, clock, scope, perHour = 25)
                quiet { w.screener.catalog() }; w.settle()
                val warmed = w.screener.catalog().universe.evaluated; val first = up.total
                clock.advance(7 * 3_600); quiet { w.screener.catalog() }; w.settle()
                val later = w.screener.catalog().universe.evaluated
                out.append("## H Screener re-warm after 7 h (15 companies, 25/h) — evaluated $warmed → $later of 15 (pre-Phase 3: 15 → 0); " +
                    "requests: first warm-up $first, re-warm ${up.total - first} (${(up.total - first) / 15} per company: statements still cached)\n")
                assertEquals(15, later) }
        } finally { scope.cancel() }
        val text = out.toString()
        println(text)
        File("build/phase3-benchmark.txt").writeText(text)
    }
}
