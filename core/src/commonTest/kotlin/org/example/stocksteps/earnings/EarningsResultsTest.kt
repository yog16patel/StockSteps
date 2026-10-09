package org.example.stocksteps.earnings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.network.StockStepsApiException
import org.example.stocksteps.portfolio.Decimal
import kotlin.test.*

/** Earnings Intelligence Lite, Phase 2: exact calculations, comparability rules, takeaways and the results presenter. */
@OptIn(ExperimentalCoroutinesApi::class)
class EarningsResultsTest {
    private fun report(
        epsActual: String? = "1.45", epsEstimate: String? = "1.20", revenueActual: String? = "8500000000", revenueEstimate: String? = "8200000000",
        actualBasis: EpsBasis = EpsBasis.GAAP_DILUTED, estimateBasis: EpsBasis = EpsBasis.GAAP_DILUTED, currency: String? = "USD", estimateCurrency: String? = "USD",
        estimatePeriod: PeriodType = PeriodType.QUARTER, periodEnd: String? = "2026-09-30",
        yearAgo: ComparisonPeriod? = ComparisonPeriod("X:2025-Q3", 2025, 3, "2025-09-30", "7900000000", "USD"),
        previous: ComparisonPeriod? = ComparisonPeriod("X:2026-Q2", 2026, 2, "2026-06-30", "8100000000", "USD")
    ) = EarningsReport("X:2026-Q3", "X", "X", "NYSE", "US", "Example Company", null, 2026, 3, periodEnd, PeriodType.QUARTER, "2026-10-06", "2026-10-06T21:00:00Z",
        currency, epsActual, actualBasis, epsEstimate, estimateBasis, revenueActual, revenueEstimate, estimateCurrency, estimatePeriod, 6, yearAgo, previous)

    // ---------- Exact math ----------

    @Test fun providerDoublesBecomeExactDecimals() {
        assertEquals("1.45", EarningsMath.decimal(1.45).toString())
        assertEquals("177500000000", EarningsMath.decimal(1.775e11).toString())
        assertEquals("0.0001", EarningsMath.decimal(1.0E-4).toString())
        assertEquals("-0.3", EarningsMath.decimal(-0.3).toString())
        assertEquals("0.3", EarningsMath.decimal(0.1 + 0.2).toString())                 // 0.30000000000000004 → 8 places
        assertNull(EarningsMath.decimal(Double.NaN)); assertNull(EarningsMath.decimal(null as Double?))
        assertEquals("20.83333333", EarningsMath.percent(Decimal.parse("1.45"), Decimal.parse("1.2")).toString())
        assertNull(EarningsMath.percent(Decimal.parse("0.03"), Decimal.ZERO))
    }

    @Test fun classificationUsesExactValuesWithoutTolerance() {
        assertEquals(Classification.BEAT, EarningsMath.classify(Decimal.parse("1.2001"), Decimal.parse("1.2")))   // displays as $1.20 but is above
        assertEquals(Classification.MISS, EarningsMath.classify(Decimal.parse("1.1999"), Decimal.parse("1.2")))
        assertEquals(Classification.MET, EarningsMath.classify(Decimal.parse("1.20"), Decimal.parse("1.2")))
    }

    // ---------- EPS ----------

    @Test fun epsBeatWithAmountPercentAndExplanation() {
        val eps = EarningsResultsCalculator.eps(report())
        assertEquals(Classification.BEAT, eps.classification)
        assertEquals("0.25", eps.surpriseAmount); assertEquals("20.83333333", eps.surprisePercent)
        assertTrue(eps.explanation.startsWith("The company earned more per share than analysts expected. This is called an EPS beat."))
        assertTrue(eps.explanation.contains("does not guarantee"))
        val card = EarningsResultsFormat.card("eps", eps, true)
        assertEquals("EPS Beat", card.classificationText)
        assertEquals(listOf("$1.45", "$1.20", "+$0.25", "+20.8%"), card.lines.map { it.value })
    }

    @Test fun zeroAndNegativeEstimatesShowOnlyTheDollarDifference() {
        val zero = EarningsResultsCalculator.eps(report(epsActual = "0.03", epsEstimate = "0"))
        assertEquals(Classification.BEAT, zero.classification); assertEquals("0.03", zero.surpriseAmount); assertNull(zero.surprisePercent)
        assertTrue(zero.reason!!.contains("zero"))
        val smallerLoss = EarningsResultsCalculator.eps(report(epsActual = "-0.20", epsEstimate = "-0.28"))
        assertEquals(Classification.BEAT, smallerLoss.classification); assertEquals("0.08", smallerLoss.surpriseAmount); assertNull(smallerLoss.surprisePercent)
        assertTrue(smallerLoss.explanation.startsWith("The company lost less per share"))
        val biggerLoss = EarningsResultsCalculator.eps(report(epsActual = "-0.45", epsEstimate = "-0.30"))
        assertEquals(Classification.MISS, biggerLoss.classification); assertEquals("-0.15", biggerLoss.surpriseAmount)
        assertTrue(biggerLoss.explanation.startsWith("The company lost more per share"))
        val negativeActual = EarningsResultsCalculator.eps(report(epsActual = "-0.05", epsEstimate = "0.10"))
        assertEquals(Classification.MISS, negativeActual.classification); assertEquals("-150", negativeActual.surprisePercent)
        assertEquals(Classification.MET, EarningsResultsCalculator.eps(report(epsActual = "0.21", epsEstimate = "0.21")).classification)
    }

    @Test fun incomparableEpsIsUnavailableNeverGuessed() {
        fun reason(r: EarningsReport) = EarningsResultsCalculator.eps(r).also { assertEquals(Classification.UNAVAILABLE, it.classification); assertNull(it.surpriseAmount) }.reason!!
        assertTrue(reason(report(epsEstimate = null)).contains("No analyst EPS estimate"))
        assertTrue(reason(report(epsActual = null)).contains("Reported EPS isn't available"))
        assertTrue(reason(report(actualBasis = EpsBasis.ADJUSTED)).contains("aren't measured the same way"))   // adjusted vs GAAP
        assertTrue(reason(report(estimateBasis = EpsBasis.UNKNOWN)).contains("aren't measured the same way"))
        assertTrue(reason(report(estimateCurrency = "CAD")).contains("different currencies"))
        assertTrue(reason(report(estimatePeriod = PeriodType.ANNUAL)).contains("full year"))                // quarterly vs annual
        assertEquals("1.45", EarningsResultsCalculator.eps(report(epsEstimate = null)).actual)              // the value is still shown
    }

    // ---------- Revenue ----------

    @Test fun revenueComparisonIsIndependentOfEps() {
        val r = report(epsActual = "1.10", revenueActual = "8500000000")
        assertEquals(Classification.MISS, EarningsResultsCalculator.eps(r).classification)
        val revenue = EarningsResultsCalculator.revenue(r)
        assertEquals(Classification.BEAT, revenue.classification); assertEquals("300000000", revenue.surpriseAmount)
        assertEquals("3.65853659", revenue.surprisePercent)
        assertEquals(listOf("$8.50B", "$8.20B", "+$300.00M", "+3.7%"), EarningsResultsFormat.card("revenue", revenue, false).lines.map { it.value })
        assertEquals(Classification.MET, EarningsResultsCalculator.revenue(report(revenueActual = "500000000", revenueEstimate = "500000000")).classification)
        assertEquals(Classification.UNAVAILABLE, EarningsResultsCalculator.revenue(report(revenueEstimate = null)).classification)
        assertEquals(Classification.UNAVAILABLE, EarningsResultsCalculator.revenue(report(revenueEstimate = "2000000000", estimatePeriod = PeriodType.ANNUAL)).classification)
        assertEquals(Classification.UNAVAILABLE, EarningsResultsCalculator.revenue(report(estimateCurrency = "CAD")).classification)
    }

    // ---------- Growth ----------

    @Test fun yearOverYearAndQuarterOverQuarterUseComparablePeriods() {
        val yoy = EarningsResultsCalculator.yearOverYear(report())
        assertEquals("7.59493671", yoy.percent); assertEquals("Q3 FY2025", yoy.priorLabel)
        assertTrue(yoy.explanation.startsWith("Revenue increased compared with the same quarter last year."))
        val qoq = EarningsResultsCalculator.quarterOverQuarter(report())
        assertEquals("4.9382716", qoq.percent); assertTrue(qoq.explanation.contains("seasonality"))
        val down = EarningsResultsCalculator.yearOverYear(report(revenueActual = "80000000", yearAgo = ComparisonPeriod("X:2025-Q3", 2025, 3, "2025-09-30", "90000000", "USD")))
        assertTrue(down.percent!!.startsWith("-11.1")); assertTrue(down.explanation.startsWith("Revenue decreased"))
        val flat = EarningsResultsCalculator.yearOverYear(report(revenueActual = "500000000", yearAgo = ComparisonPeriod("X:2025-Q3", 2025, 3, "2025-09-30", "500000000", "USD")))
        assertEquals("0", flat.percent); assertTrue(flat.explanation.startsWith("Revenue was the same"))
    }

    @Test fun growthIsSuppressedWhenThereIsNoMeaningfulBase() {
        fun none(g: GrowthComparison) = g.also { assertNull(it.percent); assertEquals("Not enough comparable data.", it.explanation) }.reason!!
        assertTrue(none(EarningsResultsCalculator.yearOverYear(report(yearAgo = null))).contains("isn't available"))
        assertTrue(none(EarningsResultsCalculator.quarterOverQuarter(report(previous = null))).contains("previous quarter"))
        assertTrue(none(EarningsResultsCalculator.yearOverYear(report(yearAgo = ComparisonPeriod("X:2025-Q3", 2025, 3, "2025-09-30", "0", "USD")))).contains("zero"))
        assertTrue(none(EarningsResultsCalculator.yearOverYear(report(yearAgo = ComparisonPeriod("X:2025-Q3", 2025, 3, "2025-09-30", null, "USD")))).contains("isn't available"))
        assertTrue(none(EarningsResultsCalculator.yearOverYear(report(yearAgo = ComparisonPeriod("X:2025-Q3", 2025, 3, "2025-06-30", "7900000000", "USD")))).contains("fiscal calendar changed"))
        assertTrue(none(EarningsResultsCalculator.yearOverYear(report(yearAgo = ComparisonPeriod("X:2025-Q3", 2025, 3, "2025-09-30", "7900000000", "CAD")))).contains("CAD"))
        assertTrue(none(EarningsResultsCalculator.quarterOverQuarter(report(previous = ComparisonPeriod("X:2026-Q2", 2026, 2, "2026-06-30", "1", "USD", PeriodType.ANNUAL)))).contains("different length"))
    }

    @Test fun mapperUsesFiscalPeriodsAcrossTheFiscalYearRollover() {
        fun e(fy: Int, q: Int, end: String, revenue: Double?, reported: Boolean = true) = EarningsEvent("SSRM:$fy-Q$q", "SSRM", "Demo", "NASDAQ", "US", null, fy, q, end, end,
            estimate = EarningsEstimate(0.40, EpsBasis.GAAP_DILUTED, 500e6, "USD", 6, "t"),
            actual = if (reported) EarningsActual(0.42, EpsBasis.GAAP_DILUTED, revenue, "USD", "t", reportedAt = null) else null, source = "t", updatedAt = "now")
        val q1 = e(2027, 1, "2026-09-30", 500e6)
        val history = listOf(q1, e(2026, 4, "2026-06-30", 480e6), e(2026, 1, "2025-09-30", 500e6), e(2027, 2, "2026-12-31", null, reported = false))
        val r = EarningsReportMapper.report(q1, history, "2026-10-07T21:15:00Z")!!
        assertEquals("SSRM:2027-Q1", r.reportId)
        assertEquals("SSRM:2026-Q4", r.previousQuarter?.reportId)                          // Q1 follows Q4 of the previous fiscal year
        assertEquals("SSRM:2026-Q1", r.previousYear?.reportId)
        assertEquals(listOf("publishedAt"), r.unavailableFields)
        assertEquals("Q1 FY2027", r.period)
        assertNull(EarningsReportMapper.report(e(2027, 2, "2026-12-31", null, reported = false), history, null)) // scheduled isn't a report
        assertEquals(Triple("TD.TO", 2026, 4), EarningsReportMapper.parse("td.to:2026-Q4")); assertNull(EarningsReportMapper.parse("AAPL:26-Q5"))
        val i = EarningsResultsCalculator.insights(r)
        assertEquals(Classification.MET, i.revenue.classification); assertEquals("0", i.yearOverYear.percent); assertEquals("4.16666667", i.quarterOverQuarter.percent)
        assertTrue(i.comparisonWarnings.any { it.contains("publication time") })
    }

    // ---------- Takeaways ----------

    @Test fun takeawaysAreDeterministicAndNeverAdvice() {
        val b = Classification.BEAT; val m = Classification.MISS; val met = Classification.MET; val u = Classification.UNAVAILABLE
        assertTrue(EarningsResultsCalculator.takeaway(b, b).startsWith("The company reported EPS and revenue above expectations."))
        assertTrue(EarningsResultsCalculator.takeaway(b, m).startsWith("The company earned more per share than expected but generated less revenue than expected."))
        assertTrue(EarningsResultsCalculator.takeaway(m, b).startsWith("Revenue exceeded expectations, but earnings per share were below estimates."))
        assertTrue(EarningsResultsCalculator.takeaway(u, u).startsWith("The company reported results, but comparable analyst estimates are unavailable."))
        assertEquals("EPS came in above expectations, while revenue matched expectations. Each measure tells part of the story; neither one alone describes the quarter.",
            EarningsResultsCalculator.takeaway(b, met))
        assertTrue(EarningsResultsCalculator.takeaway(m, u).contains("A comparable estimate for revenue isn't available."))
        val all = Classification.entries.flatMap { x -> Classification.entries.map { y -> EarningsResultsCalculator.takeaway(x, y) } }
        assertEquals(all, Classification.entries.flatMap { x -> Classification.entries.map { y -> EarningsResultsCalculator.takeaway(x, y) } })
        val advice = Regex("(?i)\\b(buy|sell|hold|should invest|will rise|will fall|undervalued|overvalued)\\b")
        assertTrue(all.none { advice.containsMatchIn(it) })
    }

    // ---------- Presenter ----------

    private class Remote(var response: EarningsResultsResponse?, var failure: Exception? = null) : EarningsRemote {
        var calls = 0
        override suspend fun results(reportId: String): EarningsResultsResponse { calls++; failure?.let { throw it }; return response ?: throw StockStepsApiException(404, ApiError("NOT_REPORTED", "Results for this period haven't been published yet.")) }
        override suspend fun latestResults(symbol: String) = results("$symbol:2026-Q3")
        override suspend fun priceReaction(reportId: String, window: ReactionWindow): EarningsPriceReactionResponse = throw UnsupportedOperationException()
        override suspend fun calendar(query: EarningsCalendarQuery) = throw UnsupportedOperationException()
        override suspend fun following(query: EarningsCalendarQuery) = throw UnsupportedOperationException()
        override suspend fun details(symbol: String, signedIn: Boolean) = throw UnsupportedOperationException()
        override suspend fun ask(symbol: String, question: String) = throw UnsupportedOperationException()
        override suspend fun event(id: String) = throw UnsupportedOperationException()
        override suspend fun next(symbol: String) = throw UnsupportedOperationException()
    }

    private class MemoryCache : EarningsResultsCache {
        val saved = HashMap<String, Pair<String, Long>>()
        override suspend fun read(reportId: String) = saved[reportId]
        override suspend fun write(reportId: String, json: String, savedAt: Long) { saved[reportId] = json to savedAt }
    }

    private fun TestScope.settle() { advanceTimeBy(1_000); runCurrent() }
    private fun response(freshness: DataFreshness = DataFreshness.FRESH) = report().let { r ->
        EarningsResultsResponse(r, EarningsResultsCalculator.insights(r), "now", freshness, "2026-10-07T18:00:00Z", listOf("note"), sampleData = true)
    }

    @Test fun presenterFormatsSectionsAndSavesACopy() = runTest {
        val cache = MemoryCache()
        val p = EarningsResultsPresenter("X:2026-Q3", Remote(response()), backgroundScope, cache, now = { 1L }); settle()
        val s = p.state.value
        assertEquals("Q3 FY2026 Earnings Results", s.title)
        assertEquals("Example Company", s.companyName); assertEquals("X · NYSE", s.symbolLine)
        assertEquals("Fiscal Q3 of fiscal year 2026 · period ended Wed, Sep 30, 2026", s.periodLines.first())
        assertEquals("EPS Beat", s.eps!!.classificationText); assertEquals("Revenue Beat", s.revenue!!.classificationText)
        assertEquals(listOf("$8.50B", "$7.90B", "+7.6%"), s.yearOverYear!!.lines.map { it.value })
        assertEquals(listOf("$8.50B", "$8.10B", "+4.9%"), s.quarterOverQuarter!!.lines.map { it.value })
        assertTrue(s.takeaway!!.startsWith("The company reported EPS and revenue above expectations."))
        assertEquals(5, s.learn.size); assertTrue(s.learn.all { it.body != null })              // existing lessons exist
        assertTrue(s.sources.any { it.startsWith("Fetched Wed, Oct 7") } && s.sampleData)
        assertTrue(s.eps!!.accessibility.startsWith("Earnings Per Share (EPS). EPS Beat. Actual: $1.45."))
        assertNotNull(cache.saved["X:2026-Q3"])
        p.toggle("eps"); assertEquals(setOf("eps"), p.state.value.expanded); p.toggle("eps"); assertTrue(p.state.value.expanded.isEmpty())
    }

    @Test fun offlineShowsTheSavedCopyAndRetryRecovers() = runTest {
        val cache = MemoryCache()
        EarningsResultsPresenter("X:2026-Q3", Remote(response()), backgroundScope, cache).also { settle() }
        val remote = Remote(null, IllegalStateException("offline"))
        val p = EarningsResultsPresenter("X:2026-Q3", remote, backgroundScope, cache); settle()
        assertTrue(p.state.value.offline); assertTrue(p.state.value.error!!.startsWith("You're seeing a copy saved on this device."))
        assertEquals("EPS Beat", p.state.value.eps?.classificationText)
        remote.failure = null; remote.response = response(DataFreshness.STALE)
        p.refresh(); settle()
        assertFalse(p.state.value.offline); assertNull(p.state.value.error)
        assertTrue(p.state.value.stale); assertTrue(p.state.value.freshnessText!!.startsWith("Showing saved earnings data"))
        assertEquals(2, remote.calls)
    }

    @Test fun missingReportAndFailuresWithoutACopyAreHonest() = runTest {
        val p = EarningsResultsPresenter("X:2026-Q4", Remote(null), backgroundScope, MemoryCache()); settle()
        assertTrue(p.state.value.notPublished); assertNull(p.state.value.eps)                    // never a fake result
        val failing = EarningsResultsPresenter("X:2026-Q3", Remote(null, StockStepsApiException(503, ApiError("EARNINGS_UNAVAILABLE", "Provider timeout."))), backgroundScope, MemoryCache()); settle()
        assertEquals("Provider timeout.", failing.state.value.error); assertFalse(failing.state.value.offline); assertNull(failing.state.value.response)
    }
}
