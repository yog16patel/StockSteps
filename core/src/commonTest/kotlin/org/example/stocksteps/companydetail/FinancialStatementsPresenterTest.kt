package org.example.stocksteps.companydetail

import org.example.stocksteps.model.*
import kotlin.test.*

class FinancialStatementsPresenterTest {
    private fun fy(year: Int, revenue: Double?, net: Double? = revenue?.times(0.3), fcf: Double? = revenue?.times(0.25), currency: String = "USD",
                   cash: Double? = 80.0, debt: Double? = 76.0, eps: Double? = net?.div(10)) =
        FinancialPeriodStatement("FY", year, "$year-06-30", currency, revenue = revenue, grossProfit = revenue?.times(0.7),
            operatingIncome = revenue?.times(0.45), netIncome = net, epsDiluted = eps, operatingCashFlow = fcf?.plus(40.0),
            capitalExpenditure = 40.0, freeCashFlow = fcf, cash = cash, totalDebt = debt, totalAssets = 500.0, totalLiabilities = 240.0,
            equity = 260.0, currentAssets = 150.0, currentLiabilities = 100.0)

    private fun fundamentals(vararg rows: FinancialPeriodStatement, dividend: FinancialFact? = FinancialFact(value = 0.8, availability = FinancialAvailability.AVAILABLE),
                             datasets: Map<String, FinancialAvailability> = emptyMap()) =
        CompanyFundamentals("SMPL", financials = CompanyFinancials(shareholderReturns = listOfNotNull(dividend?.let { "dividendYield" to it }).toMap()),
            datasets = datasets, history = rows.toList())

    private fun build(f: CompanyFundamentals, frequency: FinancialPeriod = FinancialPeriod.ANNUAL, range: FinancialRange = FinancialRange.FIVE_YEARS) =
        FinancialStatementsPresenter.build(f, frequency, range, "Sample")

    @Test fun mathHandlesSignsZeroDenominatorsAndCagr() {
        assertEquals(15.3, FinancialMath.growth(115.3, 100.0)!!, 0.001)
        assertEquals(-10.0, FinancialMath.growth(90.0, 100.0)!!, 0.001)
        assertNull(FinancialMath.growth(10.0, 0.0), "zero denominator")
        assertNull(FinancialMath.growth(-5.0, 100.0), "negative revenue")
        assertNull(FinancialMath.signedGrowth(10.0, -5.0), "growth from a loss is not a percentage")
        assertEquals(-200.0, FinancialMath.signedGrowth(-10.0, 10.0)!!, 0.001)
        assertEquals(35.1, FinancialMath.margin(35.1, 100.0)!!, 0.001)
        assertEquals(-5.0, FinancialMath.margin(-5.0, 100.0)!!, 0.001)
        assertNull(FinancialMath.margin(5.0, 0.0))
        assertEquals(10.0, FinancialMath.cagr(121.0, 100.0, 2)!!, 0.001)
        assertNull(FinancialMath.cagr(121.0, -100.0, 2))
        assertNull(FinancialMath.cagr(121.0, 100.0, 0))
        assertEquals(0.5, FinancialMath.ratio(1.0, 2.0)!!, 0.001)
        assertNull(FinancialMath.ratio(1.0, -2.0), "negative equity has no meaningful debt-to-equity")
    }

    @Test fun annualSectionsUseLatestPeriodAgainstPreviousFiscalYear() {
        val model = build(fundamentals(fy(2025, 245.0), fy(2024, 212.5), fy(2023, 198.0)))
        val revenue = model.sections.first { it.id == "revenue" }
        assertEquals("$245", revenue.headline)
        assertEquals("+15.3% vs FY2024", revenue.change)
        assertEquals("Revenue increased 15.3% compared with FY2024.", revenue.meaning)
        assertEquals(listOf("FY23", "FY24", "FY25"), revenue.chart!!.bars.map { it.label }, "oldest to newest")
        val profit = model.sections.first { it.id == "profitability" }
        assertEquals(listOf("Net Income", "Net Margin", "Operating Margin", "Gross Margin"), profit.rows.map { it.label })
        assertEquals(listOf("30.0%", "45.0%", "70.0%"), profit.rows.drop(1).map { it.value })
        val cash = model.sections.first { it.id == "cashFlow" }
        assertEquals("$61.25", cash.rows.last().value)
        assertTrue(cash.meaning!!.startsWith("Positive free cash flow"))
        val health = model.sections.first { it.id == "health" }
        assertEquals(listOf("1.50", "0.29"), health.moreRows.map { it.value })
        assertEquals("Cash is larger than total debt.", health.comparisons.first().caption)
        assertEquals(listOf("FY2025", "FY2024", "FY2023"), model.table!!.columns, "no invented years")
        assertEquals("Only 3 fiscal years of history are available.", model.rangeNote)
        assertEquals(listOf("Revenue", "Profitability", "Cash Flow", "Financial Position"), model.summary.map { it.title })
    }

    @Test fun quarterlyGrowthComparesTheSameQuarterAYearEarlier() {
        fun q(quarter: String, year: Int, revenue: Double) = FinancialPeriodStatement(quarter, year, "$year-0${quarter.last()}-28", "USD", revenue = revenue)
        val model = build(fundamentals(q("Q3", 2025, 120.0), q("Q2", 2025, 150.0), q("Q3", 2024, 100.0)), FinancialPeriod.QUARTERLY, FinancialRange.THREE_YEARS)
        assertEquals("+20.0% vs Q3 FY2024", model.sections.first().change, "not quarter-over-quarter")
    }

    @Test fun lossesNegativeCashFlowAndMissingValuesAreExplained() {
        val model = build(fundamentals(fy(2025, 100.0, net = -12.0, fcf = -8.0), fy(2024, 0.0, net = 5.0)))
        val revenue = model.sections.first { it.id == "revenue" }
        assertNull(revenue.change)
        assertEquals("Growth can't be calculated because revenue in FY2024 was zero.", revenue.meaning)
        val profit = model.sections.first { it.id == "profitability" }
        assertTrue(profit.meaning!!.contains("net loss of $12"))
        assertEquals("-12.0%", profit.rows.first { it.label == "Net Margin" }.value)
        assertTrue(model.sections.first { it.id == "cashFlow" }.meaning!!.startsWith("Negative free cash flow"))
        assertEquals("Net loss in FY2025", model.summary.first { it.title == "Profitability" }.text)
        assertTrue(model.summaryLimitations!!.contains("revenue growth"))
        val gap = build(fundamentals(fy(2025, 100.0, net = null)))
        assertEquals("—", gap.table!!.rows.first { it.first == "Revenue" }.second.size.let { "—" })
        assertNull(gap.table!!.rows.firstOrNull { it.first == "Net Income" }, "a never-reported metric is omitted, not shown as zero")
    }

    @Test fun currenciesAreNeverMixed() {
        val model = build(fundamentals(fy(2025, 60.0, currency = "CAD"), fy(2024, 50.0, currency = "USD")))
        assertNull(model.sections.first().change, "no growth across currencies")
        assertTrue(model.sections.first().headline!!.startsWith("C$"))
        assertEquals(listOf("FY2025"), model.table!!.columns)
        assertTrue(model.rangeNote!!.contains("another currency"))
    }

    @Test fun dividendsDistinguishNoDividendFromMissing() {
        val none = build(fundamentals(fy(2025, 100.0), dividend = FinancialFact(availability = FinancialAvailability.NO_DIVIDEND)))
        assertEquals("Sample does not currently pay a regular dividend.", none.sections.first { it.id == "dividends" }.meaning)
        val missing = build(fundamentals(fy(2025, 100.0), dividend = null))
        assertEquals("Dividend information isn't available for Sample.", missing.sections.first { it.id == "dividends" }.meaning)
    }

    @Test fun emptyHistoryExplainsWhetherLoadingFailed() {
        assertEquals("Historical data isn't available for this period.", build(fundamentals()).emptyMessage)
        assertEquals("We couldn't load the financial statements. Try again.",
            build(fundamentals(datasets = mapOf("income" to FinancialAvailability.TEMPORARILY_UNAVAILABLE))).emptyMessage)
        val partial = build(fundamentals(fy(2025, 100.0, cash = null, debt = null).copy(totalAssets = null, totalLiabilities = null, equity = null),
            datasets = mapOf("balance" to FinancialAvailability.TEMPORARILY_UNAVAILABLE)))
        assertEquals(SectionAvailability.TEMPORARILY_UNAVAILABLE, partial.sections.first { it.id == "health" }.availability)
        assertEquals(SectionAvailability.CONTENT, partial.sections.first { it.id == "revenue" }.availability, "one failed statement doesn't hide the rest")
    }

    @Test fun defaultRangePrefersFiveYearsWhenAvailable() {
        assertEquals(FinancialRange.FIVE_YEARS, FinancialStatementsPresenter.defaultRange(FinancialPeriod.ANNUAL, 6))
        assertEquals(FinancialRange.THREE_YEARS, FinancialStatementsPresenter.defaultRange(FinancialPeriod.ANNUAL, 4))
        assertEquals(FinancialRange.THREE_YEARS, FinancialStatementsPresenter.defaultRange(FinancialPeriod.QUARTERLY, 8))
    }
}
