package org.example.stocksteps.companydetail

import org.example.stocksteps.model.*
import kotlin.test.*

class CompanyOverviewPresenterTest {
    private fun fact(value: Double? = null, amount: Long? = null, availability: FinancialAvailability = FinancialAvailability.AVAILABLE) =
        FinancialFact(value = value, amount = amount, availability = availability, source = FinancialSource.PROVIDER_DIRECT)

    private fun details(
        pe: FinancialFact = fact(31.2),
        dividend: FinancialFact = fact(0.8),
        history: HistoricalComparison? = HistoricalComparison(average = 27.4, validCount = 5, differencePercent = 13.9, reliable = true),
        errors: List<SnapshotSectionError> = emptyList(),
        quote: StockQuote = StockQuote("MSFT", "Microsoft Corporation", 425.18, 5.26, 1.25, 426.18, 419.85, volume = 18_400_000,
            timestamp = 1_791_403_200, marketCap = 3_160_000_000_000, open = 420.32, yearHigh = 467.56, yearLow = 309.45)
    ) = CompanyDetails(
        symbol = "MSFT",
        profile = CompanyProfile("MSFT", "Microsoft Corporation", description = "Microsoft develops software. ".repeat(20), sector = "Technology", industry = "Software", exchange = "NASDAQ", currency = "USD"),
        quote = quote,
        marketStatus = MarketStatus.OPEN,
        fundamentals = CompanyFundamentals(
            "MSFT",
            financials = CompanyFinancials(
                growth = mapOf("revenue" to fact(amount = 245_000_000_000), "revenueGrowth" to fact(15.3), "netIncome" to fact(amount = 88_000_000_000)),
                profitability = mapOf("netMargin" to fact(35.1)),
                financialHealth = mapOf("cash" to fact(amount = 80_000_000_000), "debt" to fact(amount = 76_000_000_000)),
                cashFlow = mapOf("freeCashFlow" to fact(amount = 74_000_000_000)),
                shareholderReturns = mapOf("dividendYield" to dividend)
            ),
            valuation = CompanyValuation(metrics = mapOf("pe" to pe), historical = history?.let { mapOf("pe" to it) }.orEmpty())
        ),
        errors = errors
    )

    @Test fun headerAndGlanceUseNumbersWithContext() {
        val overview = CompanyOverviewPresenter.build(details())
        assertEquals("Microsoft Corporation", overview.name)
        assertEquals("MSFT • NASDAQ", overview.listing)
        assertEquals("$425.18", overview.price)
        assertEquals("+$5.26", overview.changeAmount)
        assertEquals("+1.25%", overview.changePercent)
        assertEquals(listOf("$3.16T", "31.2", "+15.3% (YoY)", "0.8%"), overview.glance.map { it.value })
        assertEquals(listOf("Mega company", "31x earnings", "Growing revenue", "Pays dividend"), overview.glance.map { it.helper })
        assertEquals(listOf("Technology", "Software", "Mega Cap"), overview.tags)
        assertEquals(1_791_403_200, overview.updatedAt)
    }

    @Test fun assessmentIsFactualWithoutRatings() {
        val rows = CompanyOverviewPresenter.build(details()).assessment
        assertEquals(listOf("Growth", "Profitability", "Financial Health", "Valuation"), rows.map { it.title })
        assertEquals("Cash $80B • Debt $76B", rows[2].detail)
        assertEquals(listOf("Growing fast", "High margin", "More cash than debt", "Above History"), rows.map { it.badge })
        assertEquals(listOf(FactTone.POSITIVE, FactTone.POSITIVE, FactTone.POSITIVE, FactTone.CAUTION), rows.map { it.tone })
        val text = rows.joinToString { it.detail + it.badge.orEmpty() + it.explanation.orEmpty() }.lowercase()
        listOf("strong", "excellent", "/10", "buy", "sell").forEach { assertFalse(text.contains(it), it) }
    }

    @Test fun insightStatesFactsAndNeverRecommends() {
        val insight = CompanyOverviewPresenter.build(details()).insight!!
        assertTrue(insight.contains("grew 15.3%") && insight.contains("$35 of every $100") && insight.contains("14% above"))
        assertTrue(insight.endsWith("This is context, not a recommendation."))
        listOf("buy", "sell", "will rise", "good investment").forEach { assertFalse(insight.lowercase().contains(it), it) }
    }

    @Test fun negativeEarningsShowNotApplicablePe() {
        val overview = CompanyOverviewPresenter.build(details(pe = fact(availability = FinancialAvailability.NON_POSITIVE_DENOMINATOR)))
        assertEquals("N/A", overview.glance.first { it.id == "pe" }.value)
        assertNull(overview.valuation!!.currentPe)
        assertTrue(overview.valuation!!.explanation.contains("negative"))
    }

    @Test fun noDividendDiffersFromMissingDividend() {
        val none = CompanyOverviewPresenter.build(details(dividend = fact(availability = FinancialAvailability.NO_DIVIDEND)))
        assertEquals("None", none.glance.last().value)
        assertEquals("No dividend", none.glance.last().helper)
        assertEquals("No dividend", none.keyRatios.first { it.label == "Dividend Yield" }.value)
        assertEquals("—", CompanyOverviewPresenter.build(details(dividend = fact(availability = FinancialAvailability.MISSING))).glance.last().value)
    }

    @Test fun missingHistoryStillShowsCurrentValuation() {
        val valuation = CompanyOverviewPresenter.build(details(history = null)).valuation!!
        assertEquals("31.2", valuation.currentPe)
        assertNull(valuation.difference)
        assertTrue(valuation.explanation.contains("isn't available"))
    }

    @Test fun aboutIsTrimmedAtASentenceAndFailuresAreReported() {
        val overview = CompanyOverviewPresenter.build(details(errors = listOf(SnapshotSectionError("fundamentals", ApiError("X", "Y")))))
        assertTrue(overview.about!!.length <= 280 && overview.about!!.endsWith("."))
        assertEquals(setOf("fundamentals"), overview.unavailable)
        assertEquals("$245B", CompanyOverviewPresenter.money(245e9))
        assertEquals("18.4M", CompanyOverviewPresenter.compact(18_400_000.0))
        assertNotNull(overview.aboutFull)
    }

    @Test fun quickStatsAndRangesUseQuoteValuesAndNeverZero() {
        val overview = CompanyOverviewPresenter.build(details())
        assertEquals(listOf("$420.32", "$426.18", "$419.85", "18.4M", "$3.16T", "31.2"), overview.quickStats.map { it.value })
        assertEquals("$309.45", overview.yearRange!!.low)
        assertEquals("$467.56", overview.yearRange!!.high)
        assertTrue(overview.dayRange!!.position in 0.8f..0.9f)
        val sparse = CompanyOverviewPresenter.build(details(quote = StockQuote("MSFT", null, 425.18, null, null, null, null)))
        assertEquals(listOf("—", "—", "—", "—", "—", "31.2"), sparse.quickStats.map { it.value })
        assertNull(sparse.dayRange)
        assertNull(sparse.yearRange)
    }

    @Test fun valuationDifferenceHasPositionAndHeadline() {
        val above = CompanyOverviewPresenter.build(details()).valuation!!
        assertEquals("+14%", above.difference)
        assertEquals(ValuationPosition.ABOVE, above.position)
        assertEquals("Trading above its historical P/E valuation.", above.headline)
        assertEquals("5Y Average", above.historicalLabel)
        val near = CompanyOverviewPresenter.build(details(history = HistoricalComparison(average = 30.0, validCount = 5, differencePercent = 4.0, reliable = true))).valuation!!
        assertEquals(ValuationPosition.NEAR, near.position)
        val unknown = CompanyOverviewPresenter.build(details(pe = fact(availability = FinancialAvailability.NON_POSITIVE_DENOMINATOR))).valuation!!
        assertEquals(ValuationPosition.UNKNOWN, unknown.position)
        assertNull(unknown.headline)
    }

    @Test fun assessmentRulesUsePublishedThresholds() {
        assertEquals("Shrinking" to FactTone.NEGATIVE, AssessmentRules.growth(-0.1))
        assertEquals("Growing" to FactTone.POSITIVE, AssessmentRules.growth(9.9))
        assertEquals("Thin margin" to FactTone.CAUTION, AssessmentRules.profitability(4.9))
        assertEquals("Losing money" to FactTone.NEGATIVE, AssessmentRules.profitability(-2.0))
        assertEquals("More debt than cash" to FactTone.CAUTION, AssessmentRules.health(1.0, 2.0))
    }

    @Test fun chartSummaryLabelsAxesAndChange() {
        val intraday = listOf(PricePoint("2026-10-07 09:30:00", 100.0), PricePoint("2026-10-07 12:00:00", 104.0), PricePoint("2026-10-07 13:00:00", 99.0), PricePoint("2026-10-07 15:55:00", 102.0))
        val day = ChartPresentation.summary(intraday, ChartRange.ONE_DAY, PriceDirection.UP)!!
        assertEquals("+2.00% today", day.change)
        assertEquals(listOf("9:30 AM", "12:00 PM", "1:00 PM", "3:55 PM"), day.xLabels)
        assertEquals(listOf("104.00", "101.50", "99.00"), day.yLabels)
        assertEquals("3:55 PM • $102.00", ChartPresentation.scrubLabel(intraday.last(), ChartRange.ONE_DAY))
        val daily = listOf(PricePoint("2022-01-03", 50.0), PricePoint("2023-06-01", 40.0), PricePoint("2024-06-01", 45.0), PricePoint("2026-10-07", 40.0))
        val years = ChartPresentation.summary(daily, ChartRange.FIVE_YEARS)!!
        assertEquals("-20.00% past 5Y", years.change)
        assertEquals(PriceDirection.DOWN, years.direction)
        assertEquals(listOf("2022", "2023", "2024", "2026"), years.xLabels)
        assertEquals("Oct 7, 2026 • $40.00", ChartPresentation.scrubLabel(daily.last(), ChartRange.ONE_MONTH))
        assertNull(ChartPresentation.summary(daily.take(1), ChartRange.ONE_MONTH))
    }
}
