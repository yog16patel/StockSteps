package org.example.stocksteps.marketdata.alphavantage

import org.example.stocksteps.marketdata.FinancialConcept
import org.example.stocksteps.marketdata.FinancialConcept.*
import org.example.stocksteps.marketdata.metrics.FinancialMetricsEngine
import org.example.stocksteps.marketdata.metrics.MetricStatus
import org.example.stocksteps.model.FinancialAvailability
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 9B.9A finalization: deterministic scenarios (invented values, Alpha Vantage shapes) for the quarter-trust rule, the engine and the
 * fundamentals bundle — non-December fiscal year, loss-maker, incomplete quarters, inconsistent recent year, missing optional fields,
 * and a statement whose fields have different quarterly bases.
 */
class FinancialScenariosTest {
    private val now = Instant.parse("2026-10-16T22:00:00Z")
    private val today = LocalDate.parse("2026-10-16")

    /** One report; [fields] values as given (null → "None"). */
    private fun row(end: String, fields: Map<String, Long?>) = """{"fiscalDateEnding":"$end","reportedCurrency":"USD",""" +
        fields.entries.joinToString(",") { (k, v) -> "\"$k\":\"${v ?: "None"}\"" } + "}"
    private fun statement(annual: List<String>, quarterly: List<String>) =
        AlphaVantageParsers.statement("""{"symbol":"T","annualReports":[${annual.joinToString(",")}],"quarterlyReports":[${quarterly.joinToString(",")}]}""")

    /** Fiscal years ending June 30 (FY2022–FY2025) + Q1 FY2026 (Sep 2025). Quarter revenue = base × (1..4); net income = revenue / 5. */
    private fun juneIncome(netIncomeSign: Long = 1, interest: Boolean = true, ytdNetIncome: Boolean = false, dropQuarters: Set<String> = emptySet(),
                           skewYear: Int? = null): AlphaVantageParsers.Statement {
        val annual = mutableListOf<String>(); val quarters = mutableListOf<String>()
        for (fy in 2025 downTo 2022) {
            val base = 100L * (fy - 2020)
            val qEnds = listOf("${fy - 1}-09-30", "${fy - 1}-12-31", "$fy-03-31", "$fy-06-30")
            val qRev = (1..4).map { base * it }.toMutableList()
            if (fy == skewYear) qRev[1] = qRev[1] * 8 / 10
            val qNi = qRev.map { netIncomeSign * it / 5 }
            val niShown = if (ytdNetIncome) qNi.runningReduce { a, b -> a + b } else qNi
            annual += row("$fy-06-30", mapOf("totalRevenue" to (1..4).sumOf { base * it }, "grossProfit" to (1..4).sumOf { base * it } / 2,
                "operatingIncome" to netIncomeSign * (1..4).sumOf { base * it } / 4, "netIncome" to netIncomeSign * (1..4).sumOf { base * it } / 5,
                "ebit" to (1..4).sumOf { base * it } / 4, "interestExpense" to if (interest) 10L else null))
            qEnds.forEachIndexed { i, e -> if (e !in dropQuarters) quarters += row(e, mapOf("totalRevenue" to qRev[i], "grossProfit" to qRev[i] / 2,
                "operatingIncome" to netIncomeSign * qRev[i] / 4, "netIncome" to niShown[i], "ebit" to qRev[i] / 4, "interestExpense" to if (interest) 3L else null)) }
        }
        if ("2025-09-30" !in dropQuarters) quarters += row("2025-09-30", mapOf("totalRevenue" to 600L, "grossProfit" to 300L, "operatingIncome" to netIncomeSign * 150L,
            "netIncome" to netIncomeSign * 120L, "ebit" to 150L, "interestExpense" to if (interest) 3L else null))
        return statement(annual, quarters.sortedByDescending { it.substringAfter("\"fiscalDateEnding\":\"").take(10) })
    }
    private fun juneCash(): AlphaVantageParsers.Statement {
        val annual = (2025 downTo 2022).map { fy -> row("$fy-06-30", mapOf("operatingCashflow" to 1000L * (fy - 2020), "capitalExpenditures" to 400L * (fy - 2020))) }
        val quarters = (2025 downTo 2022).flatMap { fy -> listOf("${fy - 1}-09-30", "${fy - 1}-12-31", "$fy-03-31", "$fy-06-30").map { e ->
            row(e, mapOf("operatingCashflow" to 250L * (fy - 2020), "capitalExpenditures" to 100L * (fy - 2020))) } } +
            row("2025-09-30", mapOf("operatingCashflow" to 1300L, "capitalExpenditures" to 500L))
        return statement(annual, quarters)
    }
    private fun bundle(income: AlphaVantageParsers.Statement, period: String = "annual", cash: AlphaVantageParsers.Statement? = juneCash()) =
        AlphaVantageFundamentalsMapper.map("T", period, income, null, cash, null, null, null, today, now, emptyMap())
    private fun ttm(income: AlphaVantageParsers.Statement, c: FinancialConcept, cash: AlphaVantageParsers.Statement? = juneCash()) =
        FinancialMetricsEngine.ttm(AlphaVantageFundamentalsMapper.history("T", income, null, cash, now), c)

    @Test fun aJuneFiscalYearLabelsQuartersAndReconcilesWithTheYear() {
        val inc = juneIncome()
        val t = AlphaVantageFundamentalsMapper.quarterTrust(inc, "totalRevenue")
        assertEquals(AlphaVantageFundamentalsMapper.QuarterBasis.STANDALONE, t.basis)
        assertEquals(listOf("2025-06-30", "2024-06-30", "2023-06-30"), t.recentChecked.map { it.toString() })
        val q = AlphaVantageFundamentalsMapper.history("T", inc, null, null, now).quarterly
        assertEquals(listOf("Q1" to 2026, "Q4" to 2025, "Q3" to 2025, "Q2" to 2025, "Q1" to 2025), q.take(5).map { it.fiscalPeriod to it.fiscalYear })
        val f = bundle(inc)
        assertEquals(25.0, f.financials.metrics()["revenueGrowth"]!!.value!!, 1e-9, "FY2025 5,000 vs FY2024 4,000")
        assertEquals(50.0, f.financials.metrics()["grossMargin"]!!.value!!, 1e-9); assertEquals(20.0, f.financials.metrics()["netMargin"]!!.value!!, 1e-9)
        // TTM to Sep 2025 = Q1 FY2026 + Q4, Q3, Q2 FY2025 = 600 + 2,000 + 1,500 + 1,000.
        assertEquals(0, BigDecimal("5100").compareTo(ttm(inc, REVENUE).value!!))
        assertEquals(0, BigDecimal("1020").compareTo(ttm(inc, NET_INCOME).value!!), "120 + 400 + 300 + 200")
        assertEquals(0, BigDecimal("5050").compareTo(ttm(inc, OPERATING_CASH_FLOW).value!!), "1,300 + 3 × 1,250")
    }

    @Test fun aLossMakerKeepsNegativeValuesAndNeverShowsAMisleadingRatio() {
        val inc = juneIncome(netIncomeSign = -1)
        val f = bundle(inc)
        assertEquals(-20.0, f.financials.metrics()["netMargin"]!!.value!!, 1e-9, "a negative margin is a valid value")
        assertEquals(FinancialAvailability.NON_POSITIVE_DENOMINATOR, f.financials.metrics()["netIncomeGrowth"]!!.availability, "growth from a loss isn't a percentage")
        assertEquals(FinancialAvailability.MISSING, f.valuation.metrics["pe"]!!.availability); assertNull(f.valuation.metrics["pe"]!!.value)
        assertTrue(f.valuation.metrics["pe"]!!.note!!.contains("EPS"))
        assertEquals(0, BigDecimal("-1020").compareTo(ttm(inc, NET_INCOME).value!!), "TTM earnings stay negative, never clamped")
    }

    @Test fun incompleteQuarterlyHistoryIsNeverSummedIntoATtm() {
        // Only the latest quarter of FY2025 and Q1 FY2026 survive: no complete recent year → nothing trusted.
        val drop = (2022..2025).flatMap { fy -> listOf("${fy - 1}-09-30", "${fy - 1}-12-31", "$fy-03-31") }.toSet()
        val inc = juneIncome(dropQuarters = drop)
        assertEquals(AlphaVantageFundamentalsMapper.QuarterBasis.UNVERIFIED, AlphaVantageFundamentalsMapper.quarterBasis(inc, "totalRevenue"))
        val r = ttm(inc, REVENUE)
        assertNull(r.value); assertTrue(r.status in setOf(MetricStatus.INSUFFICIENT_HISTORY, MetricStatus.MISSING_INPUT, MetricStatus.PERIOD_MISMATCH), "${r.status}")
        assertEquals(25.0, bundle(inc).financials.metrics()["revenueGrowth"]!!.value!!, 1e-9, "annual metrics still work")
        // A gap inside the last four quarters (a missing Q3) is a period mismatch, not a three-quarter "TTM".
        val gap = juneIncome(dropQuarters = setOf("2025-03-31"))
        assertNull(ttm(gap, REVENUE).value)
    }

    @Test fun anInconsistentRecentYearRefusesQuartersButNotAnnualFigures() {
        val inc = juneIncome(skewYear = 2024)
        assertEquals(AlphaVantageFundamentalsMapper.QuarterBasis.UNVERIFIED, AlphaVantageFundamentalsMapper.quarterBasis(inc, "totalRevenue"))
        assertNull(ttm(inc, REVENUE).value, "quarters refused → no TTM from quarters")
        val q = bundle(inc, "quarter")
        assertEquals(FinancialAvailability.MISSING, q.financials.metrics()["revenue"]!!.availability)
        assertTrue(q.history.all { it.revenue == null })
        assertEquals(25.0, bundle(inc).financials.metrics()["revenueGrowth"]!!.value!!, 1e-9)
        // An old inconsistent year (outside the recent three) leaves recent quarters usable.
        assertEquals(0, BigDecimal("5100").compareTo(ttm(juneIncome(skewYear = 2022), REVENUE).value!!))
    }

    @Test fun missingOptionalFieldsStayUnavailableNeverZero() {
        val f = bundle(juneIncome(interest = false))
        val cover = f.financials.metrics()["interestCoverage"]!!
        assertEquals(FinancialAvailability.MISSING, cover.availability); assertNull(cover.value)
        assertEquals(125.0, bundle(juneIncome()).financials.metrics()["interestCoverage"]!!.value!!, 1e-9, "EBIT 1,250 / interest 10")
        val noCash = bundle(juneIncome(), cash = null)
        for (id in listOf("operatingCashFlow", "capex", "freeCashFlow", "fcfMargin")) assertTrue(noCash.financials.metrics()[id]!!.let { it.value == null && it.amount == null }, id)
    }

    @Test fun eachQuarterlyFieldIsTrustedOnItsOwn() {
        // Revenue standalone, net income year-to-date: revenue quarters are used, net-income quarters are not.
        val inc = juneIncome(ytdNetIncome = true)
        val trust = AlphaVantageFundamentalsMapper.trustedByField(inc, listOf("totalRevenue", "netIncome"))
        assertTrue(trust.getValue("totalRevenue").isNotEmpty()); assertTrue(trust.getValue("netIncome").isEmpty())
        val q = AlphaVantageFundamentalsMapper.history("T", inc, null, null, now).quarterly.first()
        assertTrue(q[REVENUE] != null && q[NET_INCOME] == null)
        assertNull(ttm(inc, NET_INCOME).value, "no TTM earnings from year-to-date quarters")
        val comparison = AlphaVantageStatements.merge(mapOf(org.example.stocksteps.repository.Statement.INCOME to inc), quarterly = true)
        assertTrue(comparison.all { it.netIncome == null } && comparison.any { it.revenue != null }, "Comparison applies the same per-field rule")
    }
}
