package org.example.stocksteps.marketdata.alphavantage

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.marketdata.AdjustmentBasis
import org.example.stocksteps.marketdata.Dataset
import org.example.stocksteps.marketdata.PriceDelay
import org.example.stocksteps.marketdata.PriceObservation
import org.example.stocksteps.marketdata.Provenance
import org.example.stocksteps.model.CompanyFundamentals
import org.example.stocksteps.model.CompanyProfile
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialSource
import org.example.stocksteps.model.MarketMover
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.repository.Statement
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.StockProviderRepository
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.EodSessions
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B.8: the fundamentals bundle from Alpha Vantage, computed by the metrics engine (invented values, Alpha Vantage shapes). */
class AlphaVantageFundamentalsTest {
    private val now = Instant.parse("2026-10-16T22:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val today = LocalDate.parse("2026-10-16")
    private val sessions = EodSessions(clock)

    private fun annual(end: String, vararg kv: Pair<String, String>) = """{"fiscalDateEnding":"$end","reportedCurrency":"USD",${kv.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }}}"""
    private val income = """{"symbol":"EXMP","annualReports":[
        ${annual("2025-12-31", "totalRevenue" to "1200000000", "grossProfit" to "500000000", "operatingIncome" to "200000000", "netIncome" to "140000000", "ebit" to "210000000", "interestExpense" to "10000000")},
        ${annual("2024-12-31", "totalRevenue" to "1000000000", "grossProfit" to "None", "operatingIncome" to "150000000", "netIncome" to "100000000")},
        ${annual("2022-12-31", "totalRevenue" to "800000000", "netIncome" to "60000000")}],
        "quarterlyReports":[
        ${annual("2026-06-30", "totalRevenue" to "330000000", "operatingIncome" to "60000000", "netIncome" to "44000000")},
        ${annual("2026-03-31", "totalRevenue" to "320000000", "operatingIncome" to "55000000", "netIncome" to "42000000")},
        ${annual("2025-12-31", "totalRevenue" to "350000000", "netIncome" to "35000000")},
        ${annual("2025-09-30", "totalRevenue" to "300000000", "netIncome" to "40000000")},
        ${annual("2025-06-30", "totalRevenue" to "300000000", "operatingIncome" to "50000000", "netIncome" to "35000000")},
        ${annual("2025-03-31", "totalRevenue" to "250000000", "netIncome" to "30000000")}]}"""
    private val balance = """{"symbol":"EXMP","annualReports":[
        ${annual("2025-12-31", "totalAssets" to "1400000000", "totalCurrentAssets" to "450000000", "cashAndCashEquivalentsAtCarryingValue" to "120000000",
            "totalLiabilities" to "800000000", "totalCurrentLiabilities" to "300000000", "shortLongTermDebtTotal" to "330000000", "totalShareholderEquity" to "600000000")},
        ${annual("2024-12-31", "totalAssets" to "1200000000", "totalShareholderEquity" to "500000000")}],"quarterlyReports":[]}"""
    private fun cash(quarters: List<String>) = """{"symbol":"EXMP","annualReports":[
        ${annual("2025-12-31", "operatingCashflow" to "260000000", "capitalExpenditures" to "60000000", "dividendPayout" to "50000000", "paymentsForRepurchaseOfCommonStock" to "20000000")},
        ${annual("2024-12-31", "operatingCashflow" to "150000000", "capitalExpenditures" to "None")}],
        "quarterlyReports":[${listOf("2025-12-31", "2025-09-30", "2025-06-30", "2025-03-31").zip(quarters).joinToString(",") { (d, v) -> annual(d, "operatingCashflow" to v, "capitalExpenditures" to "10000000") }}]}"""
    private val standaloneCash = cash(listOf("80000000", "70000000", "60000000", "50000000"))
    private val ytdCash = cash(listOf("260000000", "180000000", "110000000", "50000000"))
    private val dividends = """{"symbol":"EXMP","data":[{"ex_dividend_date":"2026-08-10","amount":"0.25"},{"ex_dividend_date":"2026-05-10","amount":"0.25"},
        {"ex_dividend_date":"2026-02-10","amount":"0.25"},{"ex_dividend_date":"2025-11-10","amount":"0.25"},{"ex_dividend_date":"2025-08-10","amount":"0.20"},
        {"ex_dividend_date":"2024-08-10","amount":"0.80"},{"ex_dividend_date":"2023-08-10","amount":"0.70"}]}"""
    private fun price(v: String = "50", delay: PriceDelay = PriceDelay.END_OF_DAY) =
        PriceObservation("EXMP", BigDecimal(v), "USD", today, AdjustmentBasis.UNADJUSTED, delay, Provenance("alphavantage", Dataset.QUOTES, now))
    private fun map(period: String = "annual", cashText: String = standaloneCash, splits: String = """{"symbol":"EXMP","data":[]}""", p: PriceObservation? = price()) =
        AlphaVantageFundamentalsMapper.map("EXMP", period, AlphaVantageParsers.statement(income), AlphaVantageParsers.statement(balance), AlphaVantageParsers.statement(cashText),
            AlphaVantageParsers.dividends(dividends), AlphaVantageParsers.splits(splits), p, today, now, mapOf("income" to FinancialAvailability.AVAILABLE))
    private fun v(f: CompanyFundamentals, id: String) = f.financials.metrics()[id] ?: f.valuation.metrics[id]

    @Test fun annualBundleMatchesHandCalculationsThroughTheEngine() {
        val f = map()
        assertEquals(1_200_000_000L, v(f, "revenue")!!.amount); assertEquals(FinancialSource.PROVIDER_DIRECT, v(f, "revenue")!!.source)
        assertEquals(20.0, v(f, "revenueGrowth")!!.value!!, 1e-6)
        assertEquals(40.0, v(f, "netIncomeGrowth")!!.value!!, 1e-6)
        assertEquals(41.666667, v(f, "grossMargin")!!.value!!, 1e-6); assertEquals(16.666667, v(f, "operatingMargin")!!.value!!, 1e-6)
        assertEquals(25.454545, v(f, "roe")!!.value!!, 1e-6); assertEquals(10.769231, v(f, "roa")!!.value!!, 1e-6)
        assertEquals(0.55, v(f, "debtEquity")!!.value!!, 1e-9); assertEquals(1.5, v(f, "currentRatio")!!.value!!, 1e-9)
        assertEquals(210_000_000L, v(f, "netDebt")!!.amount); assertEquals(21.0, v(f, "interestCoverage")!!.value!!, 1e-9)
        assertEquals(200_000_000L, v(f, "freeCashFlow")!!.amount); assertEquals(16.666667, v(f, "fcfMargin")!!.value!!, 1e-5)
        assertEquals(35.714286, v(f, "payoutRatio")!!.value!!, 1e-5); assertEquals(20_000_000L, v(f, "buybacks")!!.amount)
        assertEquals(14.471424, v(f, "revenueCagr3")!!.value!!, 1e-5, "(1.2 / 0.8)^(1/3) − 1")
        assertEquals(FinancialAvailability.INSUFFICIENT_HISTORY, v(f, "revenueCagr5")!!.availability)
        assertEquals("2025-12-31", v(f, "revenueGrowth")!!.basis!!.date); assertEquals("USD", v(f, "revenue")!!.basis!!.currency)
        assertTrue(f.warnings.first().startsWith("Source: Alpha Vantage"))
    }

    @Test fun unverifiableInputsStayUnavailableWithAReason() {
        val f = map()
        for (id in listOf("eps", "epsGrowth", "pe", "peg", "priceSales", "priceBook", "priceFcf", "enterpriseValue", "evEbitda", "forwardPe", "shares", "roic")) {
            val x = v(f, id)!!
            assertEquals(FinancialAvailability.MISSING, x.availability, id); assertNull(x.value, id); assertNull(x.amount, id); assertTrue(!x.note.isNullOrBlank(), id)
        }
        assertTrue(f.valuation.historical.values.all { it.validCount == 0 && !it.reliable })
    }

    @Test fun missingIsNeverZero() {
        val f = map()
        // FY2024 gross profit "None" → the 2025 vs 2024 gross margin compare is skipped, not 0.
        assertEquals(null, f.history.first { it.date == "2024-12-31" }.grossProfit)
        val noBalance = AlphaVantageFundamentalsMapper.map("EXMP", "annual", AlphaVantageParsers.statement(income), null, null, null, null, null, today, now, emptyMap())
        for (id in listOf("cash", "debt", "equity", "debtEquity", "currentRatio", "operatingCashFlow", "freeCashFlow", "dividendYield", "dividendPerShare"))
            assertTrue(v(noBalance, id)!!.let { it.value == null && it.amount == null && it.availability != FinancialAvailability.AVAILABLE }, id)
    }

    @Test fun quarterlyCashFlowIsUsedOnlyWhenVerifiedStandalone() {
        assertEquals(AlphaVantageFundamentalsMapper.QuarterBasis.STANDALONE, AlphaVantageFundamentalsMapper.quarterBasis(AlphaVantageParsers.statement(standaloneCash), "operatingCashflow"))
        assertEquals(AlphaVantageFundamentalsMapper.QuarterBasis.YEAR_TO_DATE, AlphaVantageFundamentalsMapper.quarterBasis(AlphaVantageParsers.statement(ytdCash), "operatingCashflow"))
        assertEquals(AlphaVantageFundamentalsMapper.QuarterBasis.STANDALONE, AlphaVantageFundamentalsMapper.quarterBasis(AlphaVantageParsers.statement(income), "totalRevenue"))
        assertEquals(AlphaVantageFundamentalsMapper.QuarterBasis.UNVERIFIED, AlphaVantageFundamentalsMapper.quarterBasis(AlphaVantageParsers.statement(balance), "totalAssets"))
        val ytd = AlphaVantageFundamentalsMapper.history("EXMP", AlphaVantageParsers.statement(income), null, AlphaVantageParsers.statement(ytdCash), now)
        assertTrue(ytd.quarterly.none { it[org.example.stocksteps.marketdata.FinancialConcept.OPERATING_CASH_FLOW] != null }, "year-to-date values never become quarters")
        assertTrue(ytd.annual.first()[org.example.stocksteps.marketdata.FinancialConcept.OPERATING_CASH_FLOW] != null, "annual values unaffected")
        val q = map("quarter", ytdCash)
        assertEquals(FinancialAvailability.MISSING, v(q, "operatingCashFlow")!!.availability)
        assertTrue(v(q, "operatingCashFlow")!!.note!!.contains("isn't verified as standalone"))
    }

    @Test fun quarterRequestsUseTheSameQuarterAYearEarlier() {
        val q = map("quarter")
        // Latest quarter Q2 2026 (330) vs Q2 2025 (300) → 10 %; ROE/ROA come from the fiscal year and say so.
        assertEquals(10.0, v(q, "revenueGrowth")!!.value!!, 1e-6); assertEquals("Q2", v(q, "revenueGrowth")!!.basis!!.period)
        assertEquals("annual", v(q, "roe")!!.basis!!.period)
        assertEquals(listOf("Q2", "Q1", "Q4", "Q3", "Q2", "Q1"), q.history.map { it.period })
    }

    @Test fun dividendsNeedCoverageASplitFreeWindowAndAUsablePrice() {
        val f = map()
        assertEquals(1.0, v(f, "dividendPerShare")!!.value!!, 1e-9, "four 0.25 ex-dates in the trailing year")
        assertEquals(2.0, v(f, "dividendYield")!!.value!!, 1e-9, "1.00 / 50")
        assertEquals(FinancialAvailability.UNRELIABLE_COMPARISON, v(map(splits = AvFixtures.SPLITS), "dividendPerShare")!!.availability, "2026-02-15 split in the window")
        assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, v(map(p = price(delay = PriceDelay.STALE)), "dividendYield")!!.availability)
        assertEquals(FinancialAvailability.MISSING, v(map(p = null), "dividendYield")!!.availability)
        val growth = v(f, "dividendGrowth")!!
        assertEquals(-43.75, growth.value!!, 1e-6, "2025: 0.45 vs 2024: 0.80")
    }

    @Test fun negativeEquityAndLossesAreNotMeaningful() {
        val neg = balance.replace("\"600000000\"", "\"-20000000\"")
        val f = AlphaVantageFundamentalsMapper.map("EXMP", "annual", AlphaVantageParsers.statement(income), AlphaVantageParsers.statement(neg), null, null, null, null, today, now, emptyMap())
        assertEquals(FinancialAvailability.NON_POSITIVE_DENOMINATOR, v(f, "debtEquity")!!.availability)
        val loss = income.replace("\"netIncome\":\"100000000\"", "\"netIncome\":\"-5000000\"")
        val g = AlphaVantageFundamentalsMapper.map("EXMP", "annual", AlphaVantageParsers.statement(loss), null, null, null, null, null, today, now, emptyMap())
        assertEquals(FinancialAvailability.NON_POSITIVE_DENOMINATOR, v(g, "netIncomeGrowth")!!.availability)
    }

    @Test fun theContractKeysMatchTheFmpBundle() {
        val fmp = org.example.stocksteps.repositoryImpl.FmpFundamentalsMapper.map("EXMP", "annual", emptyList(), emptyList(), emptyList(), emptyList(), null, null,
            emptyList(), null, null, emptyList(), emptyList(), null, null, null, today)
        val f = map()
        assertEquals(fmp.financials.growth.keys, f.financials.growth.keys); assertEquals(fmp.financials.profitability.keys, f.financials.profitability.keys)
        assertEquals(fmp.financials.financialHealth.keys, f.financials.financialHealth.keys); assertEquals(fmp.financials.cashFlow.keys, f.financials.cashFlow.keys)
        assertEquals(fmp.financials.shareholderReturns.keys, f.financials.shareholderReturns.keys)
        assertEquals(fmp.valuation.metrics.keys, f.valuation.metrics.keys); assertEquals(fmp.valuation.historical.keys, f.valuation.historical.keys)
        // Serializes with the existing model (older apps read it unchanged).
        assertTrue(kotlinx.serialization.json.Json.encodeToString(CompanyFundamentals.serializer(), f).contains("\"revenueGrowth\""))
    }

    private class Base : StockProviderRepository {
        val calls = mutableListOf<String>()
        override suspend fun getQuote(symbol: String) = StockQuote(symbol, null, 50.0, 0.0, 0.0, 50.0, 50.0, timestamp = Instant.parse("2026-10-16T20:00:00Z").epochSecond)
        override suspend fun getGainers() = emptyList<MarketMover>()
        override suspend fun getLosers() = emptyList<MarketMover>()
        override suspend fun getProfile(symbol: String): CompanyProfile? = null
        override suspend fun searchStocks(query: String) = emptyList<StockSearchResult>()
        override suspend fun getFundamentals(symbol: String, period: String): CompanyFundamentals { calls += "fundamentals"; return CompanyFundamentals(symbol) }
    }

    @Test fun theProviderSharesStatementsAcrossFeaturesAndNeverFallsBack() = runBlocking<Unit> {
        val av = AlphaVantageClientTest.Av { 0 }
        av.bodies["INCOME_STATEMENT"] = income; av.bodies["BALANCE_SHEET"] = balance; av.bodies["CASH_FLOW"] = standaloneCash
        av.bodies["DIVIDENDS"] = dividends; av.bodies["SPLITS"] = """{"symbol":"EXMP","data":[]}"""
        val client = AlphaVantageClient(av.http, AlphaVantageConfig("k", false, 75, AlphaVantageConfig.LicenseMode.PERSONAL, emptySet(), emptySet()), sessions, clock,
            CompanyFinancialCache(capacity = 64, now = { 0 }))
        val base = Base()
        val p = AlphaVantageFundamentalsProvider(client, base, sessions, clock, CompanyFinancialCache(capacity = 64, now = { 0 }))
        (1..100).map { async { p.getFundamentals("EXMP", "annual") } }.awaitAll()
        p.getFundamentals("EXMP", "quarter"); p.screenerFundamentals("EXMP", "USD")
        p.statementHistory("EXMP", "annual", Statement.entries.toSet())
        assertEquals(5, av.total, "income, balance, cash flow, dividends, splits — once each, shared by every feature")
        val f = p.getFundamentals("EXMP", "annual")
        assertEquals(2.0, v(f, "dividendYield")!!.value!!, 1e-9, "quote from the base provider, dated at the close")
        assertTrue(base.calls.isEmpty(), "the FMP bundle isn't used")
        assertEquals(FinancialAvailability.AVAILABLE, f.datasets["dividends"])
        // Optional datasets degrade; the required one fails with its typed error (no fallback).
        av.bodies["DIVIDENDS|LIMITED"] = AvFixtures.RATE_LIMIT; av.bodies["INCOME_STATEMENT|LIMITED"] = income
        assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, p.getFundamentals("LIMITED", "annual").datasets["dividends"])
        av.bodies["INCOME_STATEMENT|PREM"] = AvFixtures.PREMIUM
        assertTrue(assertFailsWith<StockProviderException> { p.getFundamentals("PREM", "annual") }.restricted)
        assertTrue(base.calls.isEmpty())
    }
}
