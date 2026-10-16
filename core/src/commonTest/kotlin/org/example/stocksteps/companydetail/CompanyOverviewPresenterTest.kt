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
        netMargin: FinancialFact = fact(35.1),
        revenueGrowth: FinancialFact = fact(15.3),
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
                growth = mapOf("revenue" to fact(amount = 245_000_000_000), "revenueGrowth" to revenueGrowth, "netIncome" to fact(amount = 88_000_000_000)),
                profitability = mapOf("netMargin" to netMargin),
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
        // UI Phase 3: one decimal, as the Valuation page (was "+14%" here and "+13.9%" there for the same difference).
        assertEquals("+13.9%", above.difference)
        assertEquals("+13.9%", CompanyOverviewPresenter.build(details()).insightEvidence.first { it.label == "Difference from average" }.value)
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
        val day = ChartPresentation.summary(intraday, ChartRange.ONE_DAY)!!
        // UI Phase 3: the period's baseline and date, never "today" for an earlier session (the header says "Oct 7").
        assertEquals("+2.00% from 9:30 AM on Oct 7", day.change)
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

    @Test fun marginWordingReadsNaturallyForEverySign() {
        assertEquals("Apple kept about $32 of every $100 in revenue as profit.", MarginWording.sentence("Apple", 32.04))
        assertEquals("Rivian lost about $80 for every $100 in revenue.", MarginWording.sentence("Rivian", -80.2))
        assertEquals("Acme kept less than $1 of every $100 in revenue as profit.", MarginWording.sentence("Acme", 0.3))
        assertEquals("Acme lost less than $1 for every $100 in revenue.", MarginWording.sentence("Acme", -0.4))
        assertTrue(MarginWording.sentence("Acme", 0.0)!!.startsWith("Acme broke even"))
        assertNull(MarginWording.sentence("Acme", null))
        assertNull(MarginWording.sentence("Acme", Double.NaN))
    }

    @Test fun lossMakingCompanyNeverShowsANegativeDollarAmount() {
        val overview = CompanyOverviewPresenter.build(details(netMargin = fact(-80.2), revenueGrowth = fact(-12.1)))
        val profitability = overview.assessment.first { it.id == "profitability" }
        assertEquals("Microsoft lost about $80 for every $100 in revenue.", profitability.explanation)
        assertEquals("Losing money", profitability.badge)
        val insight = overview.insight!!
        assertTrue(insight.contains("revenue fell 12.1%.") && insight.contains("It lost about $80 for every $100 in revenue."), insight)
        val everything = (overview.assessment.mapNotNull { it.explanation } + insight + overview.highlights.mapNotNull { it.helper }).joinToString(" ")
        assertFalse(everything.contains("$-"), everything)
        // The margin value itself is unchanged.
        assertEquals("Net Margin -80.2%", profitability.detail)
    }

    @Test fun explanationsInterpretInsteadOfRepeatingTheNumbers() {
        val overview = CompanyOverviewPresenter.build(details())
        val growth = overview.assessment.first { it.id == "growth" }
        assertEquals("Revenue +15.3% YoY", growth.detail)
        assertEquals("Microsoft brought in more money from sales than a year earlier.", growth.explanation)
        // The net-margin sentence lives in Profitability; the Financial Highlights row shows the figure only.
        assertNull(overview.highlights.first { it.label == "Net Margin" }.helper)
    }

    @Test fun chartFailuresMapToNoHistoryConnectionOrTemporary() {
        assertEquals(ChartIssue.NO_HISTORY, ChartPresentation.issue(org.example.stocksteps.domain.StockDataException("x", 404, "CHART_NOT_FOUND")))
        assertEquals(ChartIssue.CONNECTION, ChartPresentation.issue(org.example.stocksteps.domain.StockDataException("x")))
        assertEquals(ChartIssue.TEMPORARY, ChartPresentation.issue(org.example.stocksteps.domain.StockDataException("x", 429, "PROVIDER_RATE_LIMITED")))
        assertEquals(ChartIssue.TEMPORARY, ChartPresentation.issue(IllegalStateException("x")))
        assertEquals(ChartIssue.NO_HISTORY, ChartPresentation.issue(PriceChart("X", ChartRange.ONE_DAY, emptyList(), "5min")))
        assertNull(ChartPresentation.issue(PriceChart("X", ChartRange.ONE_DAY, listOf(PricePoint("2026-10-07 09:30:00", 1.0), PricePoint("2026-10-07 09:35:00", 1.1)), "5min")))
        assertEquals(listOf(false, true, true), ChartIssue.entries.map { ChartPresentation.notice(it).canRetry })
        // Phase 4C.3: each state has its own short title; only "no history" suggests another range, only failures mention trying again.
        assertEquals(listOf("No price history", "Couldn't load the chart", "Chart temporarily unavailable"), ChartIssue.entries.map { ChartPresentation.notice(it).title })
        assertTrue(ChartPresentation.notice(ChartIssue.NO_HISTORY).message.contains("another range"))
        assertTrue(ChartPresentation.notice(ChartIssue.CONNECTION).message.contains("connection"))
    }

    @Test fun highlightAndKeyRatioRowsCarryTheirMetricIdsForExplanations() {
        // UI Phase 3: each row opens its existing explanation (MetricEducation id); labels and values are unchanged.
        val overview = CompanyOverviewPresenter.build(details())
        overview.highlights.forEach { row -> assertNotNull(row.metricId, row.label); assertNotNull(MetricEducation.find(row.metricId!!), row.label) }
        overview.keyRatios.forEach { row -> assertNotNull(row.metricId, row.label); assertNotNull(MetricEducation.find(row.metricId!!), row.label) }
        assertEquals("netMargin", overview.highlights.first { it.label == "Net Margin" }.metricId)
        assertNull(overview.highlights.first { it.label == "Net Margin" }.change)
    }

    @Test fun chartPeriodWordingNamesWhatTheRangeCoversWithoutChangingThePercentage() {
        val daily = listOf(PricePoint("2021-03-01", 50.0), PricePoint("2023-06-01", 40.0), PricePoint("2026-10-07", 100.0))
        assertEquals("+100.00% since 2021", ChartPresentation.summary(daily, ChartRange.ALL)!!.change)
        assertEquals("+100.00% past 5Y", ChartPresentation.summary(daily, ChartRange.FIVE_YEARS)!!.change)
        val undated = listOf(PricePoint("x", 50.0), PricePoint("y", 100.0))
        assertEquals("+100.00% over the day", ChartPresentation.summary(undated, ChartRange.ONE_DAY)!!.change)
        assertEquals("+100.00% over all available history", ChartPresentation.summary(undated, ChartRange.ALL)!!.change)
    }

    @Test fun oneDayLineCaptionAndSummaryFollowThePeriodWhenTheDailyQuoteDisagrees() {
        // UI Phase 3 (U3): the quote is up vs the previous close (header "+0.91%"), but the day opened higher and faded. The chart describes
        // its own period: line direction, caption and spoken summary all say down; the percentage is the same first-to-last calculation.
        val faded = listOf(PricePoint("2026-10-07 09:30:00", 338.0), PricePoint("2026-10-07 12:00:00", 335.5), PricePoint("2026-10-07 15:55:00", 331.68))
        val summary = ChartPresentation.summary(faded, ChartRange.ONE_DAY)!!
        assertEquals(PriceDirection.DOWN, summary.direction)
        assertEquals("-1.87% from 9:30 AM on Oct 7", summary.change)
        assertEquals("Price chart, 1D: down 1.87% from 9:30 AM on Oct 7, from 338.00 to 331.68.", summary.description)
    }

    @Test fun longerRangesAgreeAcrossLineCaptionAndSummaryEvenAgainstTheDay() {
        // The reverse conflict: a falling day inside a rising year — 1Y stays up everywhere.
        val year = ChartPresentation.summary(listOf(PricePoint("2025-10-07", 100.0), PricePoint("2026-06-01", 120.0), PricePoint("2026-10-07", 110.0)), ChartRange.ONE_YEAR)!!
        assertEquals(PriceDirection.UP, year.direction)
        assertEquals("+10.00% past 1Y", year.change)
        assertEquals("Price chart, 1Y: up 10.00% past 1Y, from 100.00 to 110.00.", year.description)
        val flat = ChartPresentation.summary(listOf(PricePoint("2026-10-06", 50.0), PricePoint("2026-10-07", 50.0)), ChartRange.ONE_WEEK)!!
        assertEquals(PriceDirection.UNCHANGED, flat.direction)
        assertEquals("Price chart, 1W: unchanged past 1W, from 50.00 to 50.00.", flat.description)
        val zeroStart = ChartPresentation.summary(listOf(PricePoint("2026-10-06", 0.0), PricePoint("2026-10-07", 5.0)), ChartRange.ONE_WEEK)!!
        assertEquals(PriceDirection.UNAVAILABLE, zeroStart.direction, "no percentage from a zero start, never a guessed direction")
        assertTrue(zeroStart.description.contains("change unavailable"), zeroStart.description)
    }
}
