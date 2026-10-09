package org.example.stocksteps.service

import kotlinx.coroutines.*
import org.example.stocksteps.portfolio.analytics.DebugEntitlementRequest
import org.example.stocksteps.portfolio.analytics.SubscriptionTier
import org.example.stocksteps.repository.Statement
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import org.example.stocksteps.screener.ComparisonHistoryService
import org.example.stocksteps.screener.ScreenerRequestException
import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.InMemoryUserDataStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/**
 * Phase 3A: Comparison history loads only the statements it reads (quarterly or annual income), sharing the FMP
 * dataset cache with Company Details; detailed research adds balance sheet and cash flow. REAL adapters on a MockEngine.
 */
class SelectiveStatementsTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-07T18:00:00Z"), ZoneOffset.UTC)
    private val four = "AAPL,MSFT,KO,RIVN"

    private class World(selective: Boolean = true) {
        val up = FmpMock()
        val ms = AtomicLong(0)
        val fmp = FmpStockProviderRepositoryImpl(up.client, "k", cacheNow = { ms.get() }) { LocalDate.parse("2026-10-07") }
        val stocks = StockService(fmp, fmp)
        val financials = CompanyFinancialService(fmp)
        val store = InMemoryUserDataStore()
        val entitlements = EntitlementService(store, { 0L }, debugAllowed = true)
        val history = if (selective) ComparisonHistoryService(stocks, { s, p -> financials.getFundamentals(s, p) }, entitlements,
                Clock.fixed(Instant.parse("2026-10-07T18:00:00Z"), ZoneOffset.UTC), sampleData = false, source = "test",
                cache = CompanyFinancialCache(capacity = 256, now = { ms.get() }),
                statementsOf = { s, p, set -> financials.statementHistory(s, p, set) })
            else ComparisonHistoryService(stocks, { s, p -> financials.getFundamentals(s, p) }, entitlements,
                Clock.fixed(Instant.parse("2026-10-07T18:00:00Z"), ZoneOffset.UTC), sampleData = false, source = "test")
        val statements get() = up.n("income-statement") + up.n("balance-sheet-statement") + up.n("cash-flow-statement")
    }

    @Test fun oneYearHistoryNeedsOneQuarterlyIncomeRequestPerCompany() = runBlocking {
        val w = World()
        w.history.publicHistory(four, "1Y")
        assertEquals(4, w.up.n("income-statement?period=quarter"))
        assertEquals(4, w.statements, "four statement requests, not twelve")
        assertEquals(0, w.up.n("balance-sheet-statement") + w.up.n("cash-flow-statement") + w.up.n("ratios-ttm"))
        // Before Phase 3A the same view loaded the whole quarterly bundle: quarterly income, balance sheet and cash flow,
        // annual income and every TTM/ratio dataset (the audit's 12 assumed annual income was already cached).
        val legacy = World(selective = false)
        legacy.history.publicHistory(four, "1Y")
        assertEquals(16, legacy.statements)
        assertTrue(legacy.up.total > 40)
    }

    @Test fun selectiveHistoryMatchesTheFullBundle() = runBlocking {
        val selective = World(); val full = World(selective = false)
        val plus = "plus"
        listOf(selective, full).forEach { it.entitlements.simulate(plus, DebugEntitlementRequest(SubscriptionTier.PLUS)) }
        for (range in listOf("1Y", "3Y", "5Y")) {
            val a = selective.history.userHistory(plus, four, range); val b = full.history.userHistory(plus, four, range)
            assertEquals(b.metrics, a.metrics, "$range metrics")
            assertEquals(b.periods, a.periods, "$range periods")
            assertEquals(b.insights, a.insights, "$range insights")
            assertEquals(b.companies.map { it.symbol to it.currency }, a.companies.map { it.symbol to it.currency })
        }
    }

    @Test fun threeAndFiveYearHistoryLoadAnnualIncomeOnlyAndReuseIt() = runBlocking {
        val w = World()
        w.entitlements.simulate("plus", DebugEntitlementRequest(SubscriptionTier.PLUS))
        w.history.userHistory("plus", four, "3Y")
        assertEquals(4, w.up.n("income-statement?period=annual"))
        assertEquals(4, w.statements)
        w.history.userHistory("plus", four, "5Y")
        assertEquals(4, w.statements, "5Y reuses the same annual income dataset")
        // Annual already loaded by Company Details (the full annual bundle) is reused too.
        val details = World()
        details.entitlements.simulate("plus", DebugEntitlementRequest(SubscriptionTier.PLUS))
        details.financials.getFundamentals("AAPL", "annual")
        val before = details.statements
        details.history.userHistory("plus", "AAPL,MSFT", "3Y")
        assertEquals(before + 1, details.statements, "only MSFT's annual income is new")
    }

    @Test fun quarterlyAndAnnualEntriesStaySeparate() = runBlocking {
        val w = World()
        w.entitlements.simulate("plus", DebugEntitlementRequest(SubscriptionTier.PLUS))
        val q = w.history.userHistory("plus", four, "1Y"); val a = w.history.userHistory("plus", four, "3Y")
        assertEquals(4, w.up.n("income-statement?period=quarter")); assertEquals(4, w.up.n("income-statement?period=annual"))
        assertNotEquals(q.granularity, a.granularity)
    }

    @Test fun freeUsersCannotReachPremiumHistoryAndNothingIsLoaded() = runBlocking {
        val w = World()
        val error = assertFailsWith<ScreenerRequestException> { w.history.userHistory("free-user", four, "5Y") }
        assertEquals(403, error.status)
        assertEquals(0, w.up.total, "the plan is checked before any provider request")
        assertEquals(401, assertFailsWith<ScreenerRequestException> { w.history.publicHistory(four, "3Y") }.status)
        assertEquals(0, w.up.total)
    }

    @Test fun providerFailuresAreNotCachedAsEmptyHistory() = runBlocking {
        val w = World()
        w.up.status["income-statement"] = 500
        assertEquals(503, assertFailsWith<ScreenerRequestException> { w.history.publicHistory(four, "1Y") }.status)
        val failed = w.up.n("income-statement")
        assertEquals(503, assertFailsWith<ScreenerRequestException> { w.history.publicHistory(four, "1Y") }.status)
        assertEquals(failed, w.up.n("income-statement"), "the 30 s provider cooldown is shared: no retry storm")
        w.up.status.clear(); w.ms.addAndGet(31_000)
        val ok = w.history.publicHistory(four, "1Y")
        assertTrue(ok.companies.all { it.error == null }, "recovers once the cooldown ends (the failure wasn't stored as data)")
    }

    @Test fun deniedStatementsKeepTheirLongCooldown() = runBlocking {
        val w = World()
        w.up.status["income-statement"] = 403
        assertFailsWith<ScreenerRequestException> { w.history.publicHistory(four, "1Y") }
        w.up.status.clear(); w.ms.addAndGet(31_000)
        assertFailsWith<ScreenerRequestException> { w.history.publicHistory(four, "1Y") }
        assertEquals(4, w.up.n("income-statement"), "402/403 cool down for an hour and are never retried sooner")
    }

    @Test fun concurrentIdenticalHistoryRequestsShareUpstreamWork() = runBlocking {
        val w = World()
        coroutineScope { repeat(20) { launch(Dispatchers.Default) { w.history.publicHistory(four, "1Y") } } }
        assertEquals(4, w.statements)
    }

    @Test fun detailedResearchStatementsIncludeBalanceSheetAndCashFlow() = runBlocking {
        val w = World()
        val rows = w.history.statements("AAPL", "annual")!!
        assertTrue(rows.first().totalDebt != null && rows.first().equity != null && rows.first().operatingCashFlow != null)
        assertEquals(3, w.statements, "annual income, balance sheet and cash flow")
        // The same datasets as Company Details' annual bundle: nothing new after it.
        w.financials.getFundamentals("AAPL", "annual")
        assertEquals(3, w.statements)
        val incomeOnly = w.fmp.statementHistory("MSFT", "annual", setOf(Statement.INCOME))
        assertTrue(incomeOnly.rows.all { it.totalDebt == null && it.operatingCashFlow == null }, "fields of statements not requested stay null, never zero")
        assertEquals(setOf(Statement.INCOME), incomeOnly.availability.keys)
    }

    @Test fun aStaleCopyDuringAnOutageIsLabelledInTheHistoryNotes() = runBlocking {
        val w = World()
        w.history.publicHistory(four, "1Y")
        w.ms.addAndGet(25 * 3_600_000L)
        w.up.status["income-statement"] = 503
        val stale = w.history.publicHistory(four, "1Y")
        assertTrue(stale.companies.all { it.error == null }, "the last good statements are shown")
        assertTrue(stale.notes.any { "couldn't be reached" in it && "AAPL" in it }, stale.notes.toString())
        w.up.status.clear(); w.ms.addAndGet(6 * 60_000L)
        assertTrue(w.history.publicHistory(four, "1Y").notes.none { "couldn't be reached" in it }, "a stale history entry is kept only 5 minutes")
    }
}
