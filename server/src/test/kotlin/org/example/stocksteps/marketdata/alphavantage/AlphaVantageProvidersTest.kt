package org.example.stocksteps.marketdata.alphavantage

import kotlinx.coroutines.runBlocking
import org.example.stocksteps.marketdata.AdjustmentBasis
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.MarketMover
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.repository.Statement
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.EodSessions
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B.7: Alpha Vantage behind the existing interfaces — mappings, dataset-level delegation, no runtime fallback, statement periods. */
class AlphaVantageProvidersTest {
    private val now = Instant.parse("2026-10-16T22:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sessions = EodSessions(clock)

    private class Delegate : StockProviderRepository {
        val calls = mutableListOf<String>()
        override suspend fun getQuote(symbol: String): StockQuote? { calls += "quote"; return null }
        override suspend fun getGainers(): List<MarketMover> { calls += "gainers"; return emptyList() }
        override suspend fun getLosers(): List<MarketMover> { calls += "losers"; return emptyList() }
        override suspend fun getProfile(symbol: String): CompanyProfile? { calls += "profile"; return CompanyProfile(symbol, "From FMP", sector = "Technology") }
        override suspend fun searchStocks(query: String): List<StockSearchResult> { calls += "search"; return emptyList() }
        override suspend fun getFundamentals(symbol: String, period: String): CompanyFundamentals { calls += "fundamentals"; return CompanyFundamentals(symbol) }
    }

    private fun world(premium: Boolean = false): Triple<AlphaVantageClientTest.Av, AlphaVantageStockProvider, Delegate> {
        val av = AlphaVantageClientTest.Av { 0 }
        val cfg = AlphaVantageConfig("k", true, if (premium) 75 else null, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet(),
            if (premium) AlphaVantageConfig.Entitlement.PREMIUM else AlphaVantageConfig.Entitlement.FREE)
        val client = AlphaVantageClient(av.http, cfg, sessions, clock, CompanyFinancialCache(capacity = 64, now = { 0 }))
        val d = Delegate()
        return Triple(av, AlphaVantageStockProvider(client, d, sessions, clock), d)
    }

    @Test fun pricesAreAsTradedWithAStatedBasisAndNoIntraday() = runBlocking<Unit> {
        val av = AlphaVantageClientTest.Av { 0 }
        av.bodies["TIME_SERIES_DAILY"] = AvFixtures.daily("EXMP", listOf("2026-10-15" to "10.50", "2026-10-16" to "11.25"))
        val client = AlphaVantageClient(av.http, AlphaVantageConfig("k", true, null, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet()), sessions, clock)
        val p = AlphaVantagePriceHistoryProvider(client, clock)
        assertEquals(listOf("2026-10-15" to 10.5, "2026-10-16" to 11.25), p.getDailyCloses("EXMP").map { it.time to it.close })
        val s = p.daily("EXMP")!!.value
        assertEquals(AdjustmentBasis.UNADJUSTED, s.basis); assertEquals("USD", s.currency); assertEquals("America/New_York", s.timeZone!!.id)
        assertEquals("alphavantage", s.provenance.provider); assertEquals("2026-10-16", s.provenance.asOf)
        assertTrue(p.getIntradayPoints("EXMP").isEmpty() && p.getIntradaySparkline("EXMP").closes.isEmpty())
        assertEquals(1, av.n("TIME_SERIES_DAILY"), "intraday never requested; daily cached")
    }

    @Test fun quotesAreEndOfDayAtTheSessionClose() = runBlocking<Unit> {
        val (av, p, _) = world()
        av.bodies["GLOBAL_QUOTE"] = AvFixtures.quote("EXMP", "11.0000", "2026-10-16", "10.0000")
        val q = p.getQuote("EXMP")!!
        assertEquals(11.0, q.price); assertEquals(10.0, q.previousClose); assertEquals(1.0, q.changePercent)
        assertEquals(Instant.parse("2026-10-16T20:00:00Z").epochSecond, q.timestamp, "16:00 ET close")
        assertNull(p.getQuote("^GSPC")); av.bodies["GLOBAL_QUOTE|NONE"] = AvFixtures.EMPTY_QUOTE; assertNull(p.getQuote("NONE"))
    }

    @Test fun profilesComeFromOverviewOnlyOnAPremiumPlanWithoutMixingSectorTaxonomies() = runBlocking<Unit> {
        val (_, free, d) = world()
        assertEquals("From FMP", free.getProfile("EXMP")!!.companyName); assertEquals(listOf("profile"), d.calls, "configured dataset-level source")
        val (av, premium, d2) = world(premium = true)
        av.bodies["OVERVIEW"] = AvFixtures.OVERVIEW
        val p = premium.getProfile("EXMP")!!
        assertEquals("Example Corp", p.companyName); assertNull(p.sector); assertEquals("SERVICES-PREPACKAGED SOFTWARE", p.industry)
        assertEquals(12_345_000_000.0, p.marketCap); assertEquals(false, p.isEtf)
        av.bodies["OVERVIEW|LIMIT"] = AvFixtures.RATE_LIMIT
        assertFailsWith<StockProviderException> { premium.getProfile("LIMIT") }
        assertTrue(d2.calls.isEmpty(), "an Alpha Vantage refusal is never answered from FMP")
    }

    @Test fun searchIsUsListingsOnlyAndMoversMap() = runBlocking<Unit> {
        val (av, p, _) = world()
        av.bodies["SYMBOL_SEARCH"] = AvFixtures.SEARCH; av.bodies["TOP_GAINERS_LOSERS"] = AvFixtures.MOVERS
        assertEquals(listOf("EXMP", "EXET"), p.searchStocks("example").map { it.symbol }, "no LSE listing, no mutual fund")
        assertEquals("USD", p.searchStocks("example").first().currency)
        assertEquals(66.6667, p.getGainers().single().changePercent); assertEquals("DOWN", p.getLosers().single().symbol)
        assertEquals(1, av.n("TOP_GAINERS_LOSERS"), "one request serves both lists")
    }

    @Test fun fundamentalsBundleStaysWithTheDelegate() = runBlocking<Unit> {
        val (av, p, d) = world()
        p.getFundamentals("EXMP", "annual"); p.screenerFundamentals("EXMP", "USD")
        assertEquals(listOf("fundamentals", "fundamentals"), d.calls); assertEquals(0, av.total)
    }

    @Test fun statementsMapToTheExistingContract() = runBlocking<Unit> {
        val (av, p, _) = world()
        av.bodies["INCOME_STATEMENT"] = AvFixtures.INCOME; av.bodies["BALANCE_SHEET"] = AvFixtures.BALANCE; av.bodies["CASH_FLOW"] = AvFixtures.CASH_FLOW
        val h = p.statementHistory("EXMP", "annual", setOf(Statement.INCOME, Statement.BALANCE, Statement.CASH_FLOW))
        assertEquals("alphavantage", h.source); assertEquals(Statement.entries.associateWith { FinancialAvailability.AVAILABLE }, h.availability)
        val fy = h.rows.first()
        assertEquals("FY" to 2025, fy.period to fy.fiscalYear); assertEquals("2025-12-31", fy.date); assertEquals("USD", fy.currency)
        assertEquals(1.2e9, fy.revenue); assertEquals(1.4e8, fy.netIncome); assertNull(fy.epsDiluted, "not in Alpha Vantage statements")
        assertEquals(2.6e8, fy.operatingCashFlow); assertEquals(6.0e7, fy.capitalExpenditure); assertEquals(2.0e8, fy.freeCashFlow)
        assertEquals(3.3e8, fy.totalDebt); assertEquals(6.0e8, fy.equity)
        assertNull(h.rows[1].grossProfit, "\"None\" stays null"); assertNull(h.rows[1].equity, "no 2024 balance sheet: null, not 0")
        val q = p.statementHistory("EXMP", "quarter", setOf(Statement.INCOME))
        assertEquals(listOf("Q2" to 2026, "Q1" to 2026, "Q4" to 2025), q.rows.map { it.period to it.fiscalYear })
        assertNull(q.rows.last().grossProfit)
    }

    @Test fun fiftyTwoFiftyThreeWeekFiscalYearsGetTheRightQuarters() = runBlocking<Unit> {
        val (av, p, _) = world()
        av.bodies["INCOME_STATEMENT"] = AvFixtures.INCOME_52_53
        val q = p.statementHistory("FRUT", "quarter", setOf(Statement.INCOME))
        assertEquals(listOf("Q2" to 2026, "Q1" to 2026, "Q4" to 2025, "Q3" to 2025), q.rows.map { it.period to it.fiscalYear })
        assertEquals("Q4", AlphaVantageStatements.quarterLabel(LocalDate.parse("2022-10-01"), 9), "a 53-week year ending Oct 1 is still Q4")
        assertEquals(2022, AlphaVantageStatements.fiscalYearOf(LocalDate.parse("2022-10-01"), 9))
        assertNull(AlphaVantageStatements.quarterLabel(LocalDate.parse("2025-08-15"), 9), "not a quarter boundary")
    }

    @Test fun anIncomeFailureFailsTheHistoryAndOtherStatementsDegrade() = runBlocking<Unit> {
        val (av, p, _) = world()
        av.bodies["INCOME_STATEMENT"] = AvFixtures.INCOME; av.bodies["BALANCE_SHEET"] = AvFixtures.RATE_LIMIT; av.bodies["CASH_FLOW"] = "{}"
        val h = p.statementHistory("EXMP", "annual", setOf(Statement.BALANCE, Statement.CASH_FLOW))
        assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, h.availability[Statement.BALANCE])
        assertEquals(FinancialAvailability.MISSING, h.availability[Statement.CASH_FLOW])
        av.bodies["INCOME_STATEMENT|BAD"] = AvFixtures.PREMIUM
        assertFailsWith<StockProviderException> { p.statementHistory("BAD", "annual", emptySet()) }
    }
}
