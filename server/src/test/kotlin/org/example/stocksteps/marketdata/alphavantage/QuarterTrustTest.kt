package org.example.stocksteps.marketdata.alphavantage

import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.marketdata.FinancialConcept
import org.example.stocksteps.marketdata.alphavantage.AlphaVantageFundamentalsMapper.QuarterBasis
import org.example.stocksteps.repository.Statement
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.EodSessions
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B.9A (owner decision D-9B9A-6): quarterly figures are trusted only when recent fiscal years verify as standalone. */
class QuarterTrustTest {
    private object NoDelegate : org.example.stocksteps.repository.StockProviderRepository {
        override suspend fun getQuote(symbol: String) = null
        override suspend fun getGainers() = emptyList<org.example.stocksteps.model.MarketMover>()
        override suspend fun getLosers() = emptyList<org.example.stocksteps.model.MarketMover>()
        override suspend fun getProfile(symbol: String): org.example.stocksteps.model.CompanyProfile? = null
        override suspend fun searchStocks(query: String) = emptyList<org.example.stocksteps.model.StockSearchResult>()
    }
    private val clock = Clock.fixed(Instant.parse("2026-10-16T22:00:00Z"), ZoneOffset.UTC)
    private fun d(s: String) = LocalDate.parse(s)

    /** Calendar fiscal years 2022–2025 with quarters, plus 2026 Q1–Q2 in progress. [bad] years have a quarter 20 % off; [ytd] years are cumulative. */
    private fun income(bad: Set<Int> = emptySet(), ytd: Set<Int> = emptySet()): String {
        val q = listOf(100, 110, 120, 130)
        fun row(end: String, v: Long) = """{"fiscalDateEnding":"$end","reportedCurrency":"USD","totalRevenue":"$v","netIncome":"${v / 10}"}"""
        val annual = (2025 downTo 2022).map { y -> row("$y-12-31", q.sum().toLong() * (y - 2020)) }
        val quarters = mutableListOf(row("2026-06-30", 999), row("2026-03-31", 888))
        for (y in 2025 downTo 2022) {
            val m = (y - 2020).toLong()
            val values = q.map { it * m }.toMutableList()
            if (y in bad) values[1] = values[1] * 12 / 10
            val shown = if (y in ytd) values.runningReduce { a, b -> a + b } else values
            listOf("$y-12-31", "$y-09-30", "$y-06-30", "$y-03-31").zip(shown.reversed()).forEach { (e, v) -> quarters += row(e, v) }
        }
        return """{"symbol":"T","annualReports":[${annual.joinToString(",")}],"quarterlyReports":[${quarters.joinToString(",")}]}"""
    }
    private fun trust(text: String) = AlphaVantageFundamentalsMapper.quarterTrust(AlphaVantageParsers.statement(text), "totalRevenue")

    @Test fun anOldInconsistentYearNoLongerRefusesRecentQuarters() {
        val t = trust(income(bad = setOf(2022)))
        assertEquals(QuarterBasis.STANDALONE, t.basis)
        assertEquals(listOf(d("2025-12-31"), d("2024-12-31"), d("2023-12-31")), t.recentChecked, "the three most recent complete years")
        assertTrue(d("2022-06-30") !in t.trusted && d("2022-12-31") !in t.trusted, "the failing year's own quarters stay untrusted")
        assertTrue(listOf("2025-03-31", "2024-06-30", "2023-12-31").all { d(it) in t.trusted })
        assertTrue(d("2026-03-31") in t.trusted && d("2026-06-30") in t.trusted, "the fiscal year in progress continues the verified series")
        assertEquals(QuarterBasis.UNVERIFIED, t.years.first { it.fiscalYearEnd == d("2022-12-31") }.verdict)
        assertTrue(t.years.first { it.fiscalYearEnd == d("2025-12-31") }.ratio!!.compareTo(java.math.BigDecimal.ONE) == 0)
    }

    @Test fun aBadRecentYearOrYearToDateRefusesAllQuarters() {
        val recentBad = trust(income(bad = setOf(2024)))
        assertEquals(QuarterBasis.UNVERIFIED, recentBad.basis); assertTrue(recentBad.trusted.isEmpty())
        val ytd = trust(income(ytd = setOf(2025)))
        assertEquals(QuarterBasis.YEAR_TO_DATE, ytd.basis); assertTrue(ytd.trusted.isEmpty())
        assertEquals(QuarterBasis.UNVERIFIED, trust("""{"symbol":"T","annualReports":[],"quarterlyReports":[]}""").basis)
    }

    @Test fun theFundamentalsBundleUsesOnlyTrustedQuarters() {
        val h = AlphaVantageFundamentalsMapper.history("T", AlphaVantageParsers.statement(income(bad = setOf(2022))), null, null, clock.instant())
        assertNotNull(h.quarterly.first { it.end == d("2025-06-30") }[FinancialConcept.REVENUE])
        assertNull(h.quarterly.first { it.end == d("2022-06-30") }[FinancialConcept.REVENUE])
        assertNotNull(h.quarterly.first { it.end == d("2026-06-30") }[FinancialConcept.REVENUE])
    }

    @Test fun comparisonStatementsApplyTheSameRule() = runBlocking<Unit> {
        val av = AlphaVantageClientTest.Av { 0 }
        av.bodies["INCOME_STATEMENT"] = income(bad = setOf(2024)); av.bodies["CASH_FLOW"] = "{}"; av.bodies["BALANCE_SHEET"] = "{}"
        val sessions = EodSessions(clock)
        val client = AlphaVantageClient(av.http, AlphaVantageConfig("k", true, null, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet()), sessions, clock,
            CompanyFinancialCache(capacity = 16, now = { 0 }))
        val stocks = AlphaVantageStockProvider(client, NoDelegate, sessions, clock)
        val q = stocks.statementHistory("T", "quarter", setOf(Statement.INCOME))
        assertTrue(q.rows.isNotEmpty() && q.rows.all { it.revenue == null && it.netIncome == null }, "unverified quarters never shown as quarter figures")
        val a = stocks.statementHistory("T", "annual", setOf(Statement.INCOME))
        assertEquals(2300.0, a.rows.first().revenue, "annual rows unaffected (FY2025 = 460 × 5)")
        av.bodies["INCOME_STATEMENT|U"] = income(bad = setOf(2022))
        val ok = stocks.statementHistory("U", "quarter", setOf(Statement.INCOME))
        assertEquals(650.0, ok.rows.first { it.date == "2025-12-31" }.revenue, "trusted quarters shown (Q4 2025 = 130 × 5)")
        assertNull(ok.rows.first { it.date == "2022-06-30" }.revenue)
    }

    @Test fun theSmokeDiagnosticRunsIncomeOnlyInOneRequest() = runBlocking<Unit> {
        assertEquals(listOf("INCOME_STATEMENT"), AlphaVantageSmokeTest.functions { if (it == "ALPHA_VANTAGE_SMOKE_FUNCTIONS") "income_statement" else null })
        assertEquals(AlphaVantageSmokeTest.DEFAULT_FUNCTIONS, AlphaVantageSmokeTest.functions { null })
        assertTrue(AlphaVantageSmokeTest.functions { if (it == "ALPHA_VANTAGE_SMOKE_FUNCTIONS") "OVERVIEW" else null }.isEmpty(), "premium/unsupported names select nothing")
        val av = AlphaVantageClientTest.Av { 0 }; av.bodies["INCOME_STATEMENT"] = income(bad = setOf(2022))
        val sent = AtomicInteger(); av.http.plugin(HttpSend).intercept { r -> sent.incrementAndGet(); execute(r) }
        val client = AlphaVantageClient(av.http, AlphaVantageConfig("k", false, null, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet()), EodSessions(clock), clock,
            CompanyFinancialCache(capacity = 8))
        val r = AlphaVantageSmokeTest.run(client, sent, "T", listOf("INCOME_STATEMENT"), 1) {}
        assertEquals(1, r.requestsSent)
        val md = r.markdown()
        assertTrue("## INCOME_STATEMENT quarterly basis by fiscal year" in md)
        assertTrue("| 2022-12-31 | 4 |" in md && "UNVERIFIED" in md && "| 2025-12-31 | 4 | 1 | STANDALONE |" in md, md)
        assertTrue("grossProfit missing" in md && "recent years checked" in md && "trusted quarters" in md)
    }
}
