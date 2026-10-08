package org.example.stocksteps.companydetail

import org.example.stocksteps.model.*
import kotlin.test.*

class ValuationPresenterTest {
    /** Monthly observations Jan 2021 … Dec 2025 (60 months), P/E 20 → 39.5 rising 0.33 per month. */
    private fun monthly(skip: Set<String> = emptySet(), current: Double? = 40.0) = ValuationHistory(
        symbol = "SMPL", method = ValuationMethod.MONTHLY_TTM,
        observations = (0 until 60).map { i ->
            val year = 2021 + i / 12
            val month = (i % 12 + 1).toString().padStart(2, '0')
            ValuationObservation("$year-$month-28", 20.0 + i * 0.33)
        }.filter { it.date.take(7) !in skip },
        currentPe = current, currentPeAvailability = if (current != null) FinancialAvailability.AVAILABLE else FinancialAvailability.NON_POSITIVE_DENOMINATOR
    )

    private fun fact(value: Double) = FinancialFact(value = value, availability = FinancialAvailability.AVAILABLE)
    private val fundamentals = CompanyFundamentals("SMPL",
        financials = CompanyFinancials(growth = mapOf("revenueGrowth" to fact(15.3), "epsGrowth" to fact(-4.0)),
            shareholderReturns = mapOf("dividendYield" to FinancialFact(availability = FinancialAvailability.NO_DIVIDEND))),
        valuation = CompanyValuation(metrics = mapOf("priceSales" to fact(11.9), "priceBook" to fact(-3.0), "peg" to fact(0.93))))

    private fun build(history: ValuationHistory?, range: ValuationRange = ValuationRange.FIVE_YEARS) =
        ValuationPresenter.build(history, fundamentals, range, "Sample", "USD")

    @Test fun currentPeAverageAndDifferenceUseActualObservations() {
        val model = build(monthly())
        val expected = (0 until 60).map { 20.0 + it * 0.33 }.average()
        assertEquals("40.0x", model.currentPe)
        assertEquals(ValuationPresenter.multiple(expected), model.average)
        assertEquals("+${CompanyOverviewPresenter.decimal((40.0 / expected - 1) * 100, 1)}%", model.difference)
        assertEquals("Above Historical Average", model.badge)
        assertEquals("Investors are paying about $40.00 for every $1 of annual earnings per share.", model.meaning)
        assertTrue(model.methodology.startsWith("Average of 60 month-end P/E values (Jan 2021 – Dec 2025)"))
        assertEquals("20.0x", model.rangeSummary!!.lowest)
        assertEquals("39.5x", model.rangeSummary!!.highest)
        assertEquals("Above the highest P/E observed in this period.", model.rangeSummary!!.percentile, "current outside the observed range")
    }

    @Test fun percentileComesFromTheObservationsNotMinAndMax() {
        val model = build(monthly(current = 30.0))
        assertEquals("Higher than 51% of the 60 observations in this period.", model.rangeSummary!!.percentile)
        val below = build(monthly(current = 10.0))
        assertEquals("Below the lowest P/E observed in this period.", below.rangeSummary!!.percentile)
        assertEquals(0f, below.rangeSummary!!.position)
        assertEquals("Below Historical Average", below.badge)
    }

    @Test fun rangesSliceTheSameSeriesAndKeepGapsAsGaps() {
        val history = monthly(skip = setOf("2025-03", "2025-04"))
        val oneYear = build(history, ValuationRange.ONE_YEAR)
        assertEquals(12, oneYear.chart!!.values.size, "one slot per month, Jan–Dec 2025")
        assertEquals(2, oneYear.chart!!.values.count { it == null }, "missing months are gaps, never interpolated")
        assertTrue(oneYear.chart!!.details[2].contains("no valid P/E"))
        assertTrue(oneYear.methodology.contains("Average of 10 month-end"), "the average uses only the 10 valid months")
        assertEquals("1-Year Average P/E", oneYear.averageLabel)
        assertNotEquals(oneYear.average, build(history, ValuationRange.FIVE_YEARS).average)
    }

    @Test fun insufficientHistoryShowsNoAverageInsteadOfAnUnreliableOne() {
        val tenYears = build(monthly(), ValuationRange.TEN_YEARS)
        assertNull(tenYears.average, "60 of 120 months is below the coverage threshold")
        assertNull(tenYears.badge)
        assertTrue(tenYears.methodology.startsWith("Not enough valid observations in the last 10 years"))
        assertEquals("40.0x", tenYears.currentPe, "the current P/E is still shown")
        assertEquals(ValuationRange.FIVE_YEARS, ValuationPresenter.defaultRange(monthly()))
    }

    @Test fun zeroOrNegativeEarningsShowNotApplicable() {
        val model = build(monthly(current = null))
        assertEquals("N/A", model.currentPe)
        assertFalse(model.currentPeAvailable)
        assertEquals("P/E is not meaningful because earnings are currently zero or negative.", model.meaning)
        assertNull(model.difference)
        assertTrue(model.insight.contains("zero or negative"))
    }

    @Test fun annualReportedRatiosAreLabelledAndNeedThreeYears() {
        val annual = ValuationHistory("TD", ValuationMethod.ANNUAL_REPORTED,
            (2021..2025).map { ValuationObservation("$it-10-31", 10.0 + it - 2021) }, currentPe = 10.6, currentPeAvailability = FinancialAvailability.AVAILABLE)
        val five = build(annual)
        assertEquals("12.0x", five.average)
        assertTrue(five.methodology.contains("fiscal-year P/E ratios reported by the data provider"))
        assertEquals(listOf("FY2021", "FY2022", "FY2023", "FY2025"), five.chart!!.xLabels)
        assertNull(build(annual, ValuationRange.ONE_YEAR).average, "a 1Y average from one annual ratio is not shown")
    }

    @Test fun missingHistoryAndRatiosAreHandledWithoutZeros() {
        val failed = build(null)
        assertEquals("We couldn't load the valuation history right now.", failed.historyMessage)
        assertNull(failed.chart)
        val model = build(monthly())
        assertEquals(listOf("Price to Sales (P/S)", "PEG Ratio", "Dividend Yield"), model.otherMetrics.map { it.label }, "negative P/B is not shown as a multiple")
        assertEquals("No dividend", model.otherMetrics.last().value)
        assertTrue(model.growthNote.startsWith("Earnings per share declined"))
        listOf("buy", "sell", "undervalued", "overvalued", "fair value").forEach { word ->
            assertFalse((model.insight + model.growthNote + model.meaning).lowercase().contains(word), word)
        }
    }
}
