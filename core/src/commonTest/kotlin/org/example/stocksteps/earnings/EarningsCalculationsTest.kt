package org.example.stocksteps.earnings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.example.stocksteps.model.EarningsDateStatus
import org.example.stocksteps.model.EarningsTime
import kotlin.math.abs
import kotlin.test.*

class EarningsCalculationsTest {
    private fun est(eps: Double? = null, revenue: Double? = null, basis: EpsBasis = EpsBasis.ADJUSTED, currency: String? = "USD") = EarningsEstimate(eps, basis, revenue, currency, source = "test")
    private fun act(eps: Double? = null, revenue: Double? = null, basis: EpsBasis = EpsBasis.ADJUSTED, currency: String? = "USD") = EarningsActual(eps, basis, revenue, currency, source = "test")
    private fun close(expected: Double, actual: Double?) = assertTrue(actual != null && abs(expected - actual) < 1e-9, "expected $expected, was $actual")

    // ---------- EPS ----------

    @Test fun positiveBeatAndMiss() {
        val beat = EarningsCalculator.eps(est(1.00), act(1.10))
        assertEquals(Classification.BEAT, beat.classification); close(0.10, beat.absolute); close(10.0, beat.percent)
        val miss = EarningsCalculator.eps(est(2.00), act(1.80))
        assertEquals(Classification.MISS, miss.classification); close(-10.0, miss.percent)
    }

    @Test fun negativeEstimatesUseAbsoluteDenominator() {
        // A smaller loss than expected is a beat, and the percentage is positive.
        val r = EarningsCalculator.eps(est(-0.50), act(-0.40))
        assertEquals(Classification.BEAT, r.classification); close(20.0, r.percent)
        val worse = EarningsCalculator.eps(est(-0.50), act(-0.60))
        assertEquals(Classification.MISS, worse.classification); close(-20.0, worse.percent)
    }

    @Test fun negativeActualAgainstPositiveEstimate() {
        val r = EarningsCalculator.eps(est(0.20), act(-0.10))
        assertEquals(Classification.MISS, r.classification); close(-150.0, r.percent)
    }

    @Test fun zeroEstimateHasNoPercentageButStillClassifies() {
        val r = EarningsCalculator.eps(est(0.0), act(0.03))
        assertNull(r.percent)
        assertEquals(Classification.BEAT, r.classification)
        assertNotNull(r.reason)
        assertEquals(Classification.IN_LINE, EarningsCalculator.eps(est(0.0), act(0.004)).classification)
    }

    @Test fun missingValuesAreNotComparable() {
        assertEquals(Classification.UNAVAILABLE, EarningsCalculator.eps(null, act(1.0)).classification)
        assertEquals(Classification.UNAVAILABLE, EarningsCalculator.eps(est(1.0), null).classification)
        assertEquals(Classification.UNAVAILABLE, EarningsCalculator.eps(est(eps = null), act(1.0)).classification)
    }

    @Test fun roundingToleranceProducesInLine() {
        assertEquals(Classification.IN_LINE, EarningsCalculator.eps(est(1.50), act(1.504)).classification) // under half a cent
        assertEquals(Classification.IN_LINE, EarningsCalculator.eps(est(10.00), act(10.04)).classification) // 0.4% of estimate
        assertEquals(Classification.BEAT, EarningsCalculator.eps(est(1.00), act(1.01)).classification)
    }

    @Test fun basisAndCurrencyMismatchesAreNeverCompared() {
        val basis = EarningsCalculator.eps(est(1.0, basis = EpsBasis.ADJUSTED), act(0.9, basis = EpsBasis.GAAP_DILUTED))
        assertEquals(Classification.UNAVAILABLE, basis.classification); assertTrue(basis.reason!!.contains("measured the same way"))
        assertEquals(Classification.UNAVAILABLE, EarningsCalculator.eps(est(1.0, basis = EpsBasis.UNKNOWN), act(1.0, basis = EpsBasis.UNKNOWN)).classification)
        val currency = EarningsCalculator.eps(est(1.0, currency = "CAD"), act(1.1, currency = "USD"))
        assertEquals(Classification.UNAVAILABLE, currency.classification); assertTrue(currency.reason!!.contains("currencies"))
    }

    // ---------- Revenue ----------

    @Test fun revenueSurpriseAndTolerance() {
        val r = EarningsCalculator.revenue(est(revenue = 10e9), act(revenue = 10.4e9))
        assertEquals(Classification.BEAT, r.classification); close(4.0, r.percent); close(0.4e9, r.absolute)
        assertEquals(Classification.IN_LINE, EarningsCalculator.revenue(est(revenue = 10e9), act(revenue = 10.04e9)).classification)
        assertEquals(Classification.MISS, EarningsCalculator.revenue(est(revenue = 10e9), act(revenue = 9.8e9)).classification)
        assertEquals(Classification.UNAVAILABLE, EarningsCalculator.revenue(est(revenue = 0.0), act(revenue = 1e9)).classification)
        assertEquals(Classification.UNAVAILABLE, EarningsCalculator.revenue(est(revenue = 1e9, currency = "CAD"), act(revenue = 1e9, currency = "USD")).classification)
        assertEquals(Classification.UNAVAILABLE, EarningsCalculator.revenue(est(revenue = null), act(revenue = 1e9)).classification)
    }

    private fun event(year: Int, quarter: Int, revenue: Double?, eps: Double? = 1.0, estimate: Double? = 0.9, currency: String = "USD", date: String = "$year-0${quarter * 2}-15") =
        EarningsEvent("T:$year-Q$quarter", "T", "Test", fiscalYear = year, fiscalQuarter = quarter, date = date, dateStatus = EarningsDateStatus.CONFIRMED,
            estimate = est(estimate, revenue?.let { it * 0.98 }, currency = currency), actual = act(eps, revenue, currency = currency), source = "test", updatedAt = "2026-10-07T00:00:00Z")

    @Test fun revenueGrowthUsesComparablePeriodsOnly() {
        val now = event(2026, 2, 110.0); val yearAgo = event(2025, 2, 100.0); val previous = event(2026, 1, 105.0)
        close(10.0, EarningsCalculator.growth(now, EarningsCalculator.yearAgo(now, listOf(yearAgo, previous))))
        assertEquals(previous, EarningsCalculator.previousQuarter(now, listOf(yearAgo, previous)))
        assertEquals(event(2025, 4, 1.0).id, EarningsCalculator.previousQuarter(event(2026, 1, 1.0), listOf(event(2025, 4, 1.0)))?.id) // across fiscal years
        assertNull(EarningsCalculator.growth(now, event(2025, 2, 100.0, currency = "CAD")), "different currencies")
        assertNull(EarningsCalculator.yearAgo(now, listOf(previous)), "no same quarter last year → no YoY")
    }

    // ---------- Status and summary ----------

    @Test fun statusComesFromDataNotTheClock() {
        val upcoming = event(2026, 3, null, eps = null).copy(date = "2026-10-20", actual = null)
        assertEquals(EarningsStatus.UPCOMING, EarningsCalculator.status(upcoming, "2026-10-08"))
        assertEquals(EarningsStatus.DATA_PENDING, EarningsCalculator.status(upcoming, "2026-10-21"), "a past date without results isn't 'reported'")
        assertEquals(EarningsStatus.PARTIALLY_REPORTED, EarningsCalculator.status(upcoming.copy(actual = act(eps = 1.0)), "2026-10-21"))
        assertEquals(EarningsStatus.REPORTED, EarningsCalculator.status(event(2026, 2, 1e9), "2026-10-21"))
        assertEquals(EarningsStatus.UNAVAILABLE, EarningsCalculator.status(null, "2026-10-21"))
    }

    @Test fun summaryNamesBothResultsAndNeverImpliesThePriceMove() {
        val (headline, explanation) = EarningsCalculator.summary(EarningsCalculator.eps(est(1.0), act(1.1)), EarningsCalculator.revenue(est(revenue = 10e9), act(revenue = 9e9)))!!
        assertEquals("EPS beat, revenue miss", headline)
        assertTrue(explanation.startsWith("The company reported earnings per share above analyst expectations, while revenue below analyst expectations"))
        assertTrue(explanation.contains("different stories"))
        assertTrue(explanation.contains("don't determine how the stock moves"))
        assertNull(EarningsCalculator.summary(SurpriseResult(), SurpriseResult()))
    }

    // ---------- Insights ----------

    @Test fun insightsOnlyFromSupportedData() {
        val latest = event(2026, 2, 110.0, eps = 1.10, estimate = 1.00)
        val history = listOf(event(2026, 1, 105.0, eps = 1.05, estimate = 1.0), event(2025, 4, 104.0, eps = 1.02, estimate = 1.0),
            event(2025, 3, 102.0), event(2025, 2, 100.0), event(2025, 1, 98.0))
        val insights = EarningsInsightEngine.generate(latest, history, "now")
        assertTrue(insights.any { it.title == "Reported EPS exceeded the consensus estimate by 10.0%." })
        assertTrue(insights.any { it.title.startsWith("Revenue increased 10.0%") })
        val streak = insights.single { it.id.startsWith("eps-streak") }
        assertTrue(streak.advanced)
        assertTrue(streak.title.contains("consecutive"))
        insights.forEach { assertFalse(Regex("(?i)\\b(buy|sell|will rise|will fall|guidance|management said)\\b").containsMatchIn(it.title + it.explanation), it.title) }
    }

    @Test fun noStreakAcrossAGapOrANonComparableQuarter() {
        val latest = event(2026, 2, 110.0, eps = 1.10, estimate = 1.0)
        val gap = listOf(event(2026, 1, 105.0, eps = 1.05, estimate = 1.0), event(2025, 3, 104.0, eps = 1.05, estimate = 1.0)) // 2025-Q4 missing
        assertTrue(EarningsInsightEngine.generate(latest, gap, "now").none { it.id.startsWith("eps-streak") })
        val mismatch = listOf(event(2026, 1, 105.0).copy(estimate = est(1.0, basis = EpsBasis.GAAP_DILUTED)), event(2025, 4, 104.0))
        assertTrue(EarningsInsightEngine.generate(latest, mismatch, "now").none { it.id.startsWith("eps-streak") })
        // No acceleration claim without two year-over-year growth rates.
        assertTrue(EarningsInsightEngine.generate(latest, listOf(event(2025, 2, 100.0)), "now").none { it.id.startsWith("revenue-trend") })
    }

    @Test fun educationCoversEveryTopic() {
        listOf("quarterly", "eps", "revenue", "estimates", "beat", "miss", "inline", "surprise", "fall-after-beat", "guidance", "yoy", "qoq", "reaction")
            .forEach { assertTrue(EarningsEducation.topic(it)!!.body.isNotBlank(), it) }
    }

    // ---------- Formatting and calendar ranges ----------

    @Test fun datesCountdownsAndSessions() {
        assertEquals("Wed, Oct 14", EarningsFormatter.date("2026-10-14"))
        assertEquals("Wednesday, October 14, 2026", EarningsFormatter.spokenDate("2026-10-14"))
        val confirmed = event(2026, 3, null).copy(date = "2026-10-13", dateStatus = EarningsDateStatus.CONFIRMED, actual = null)
        assertEquals("Earnings in 5 days", EarningsFormatter.countdown(confirmed, "2026-10-08"))
        assertEquals("Earnings tomorrow (estimated date)", EarningsFormatter.countdown(confirmed.copy(date = "2026-10-09", dateStatus = EarningsDateStatus.ESTIMATED), "2026-10-08"))
        assertNull(EarningsFormatter.countdown(confirmed.copy(dateStatus = EarningsDateStatus.UNKNOWN), "2026-10-08"))
        assertNull(EarningsFormatter.countdown(confirmed.copy(date = "2026-10-01"), "2026-10-08"))
        assertEquals("Time not announced", EarningsFormatter.session(EarningsTime.UNKNOWN))
        assertEquals("Beat (+5.9%)", EarningsFormatter.result(SurpriseResult(classification = Classification.BEAT, percent = 5.94)))
        assertEquals("−$0.05", EarningsFormatter.eps(-0.05, "USD"))
        assertEquals("C$1.50B", EarningsFormatter.revenue(1.5e9, "CAD"))
    }

    @Test fun calendarRangesStartOnMonday(): Unit = runBlocking {
        val presenter = EarningsCenterPresenter(FakeRemote(), CoroutineScope(SupervisorJob()), today = { "2026-10-08" }) // a Thursday
        assertEquals("2026-10-05" to "2026-10-11", presenter.range(EarningsRange.THIS_WEEK))
        assertEquals("2026-10-12" to "2026-10-18", presenter.range(EarningsRange.NEXT_WEEK))
        assertEquals("2026-09-28" to "2026-10-04", presenter.range(EarningsRange.LAST_WEEK))
        assertEquals("2026-10-08" to "2026-11-07", presenter.range(EarningsRange.MONTH))
    }

    // ---------- Presenters ----------

    private class FakeRemote : EarningsRemote {
        val queries = mutableListOf<EarningsCalendarQuery>()
        var asked = 0
        var fail = false
        override suspend fun calendar(query: EarningsCalendarQuery): EarningsCalendarPage {
            queries += query
            if (fail) throw IllegalStateException("down")
            val all = (1..5).map { i ->
                EarningsCalendarItem(EarningsEvent("S$i:2026-Q3", "S$i", "Company $i", "NYSE", "US", fiscalYear = 2026, fiscalQuarter = 3, date = "2026-10-1$i",
                    session = EarningsTime.BEFORE_OPEN, dateStatus = EarningsDateStatus.CONFIRMED, source = "test", updatedAt = "now"), EarningsStatus.UPCOMING)
            }
            val offset = query.cursor?.toInt() ?: 0
            val slice = all.drop(offset).take(query.pageSize)
            return EarningsCalendarPage(slice, query.from, query.to, all.size, (offset + slice.size).takeIf { it < all.size }?.toString(), asOf = "now")
        }
        override suspend fun following(query: EarningsCalendarQuery) = calendar(query)
        override suspend fun details(symbol: String, signedIn: Boolean) = EarningsDetails(symbol, "Company", null, EarningsStatus.UNAVAILABLE, SurpriseResult(), SurpriseResult(), asOf = "now")
        override suspend fun ask(symbol: String, question: String): EarningsAnswer { asked++; return EarningsAnswer("answer") }
    }

    @Test fun centerLoadsPagesFiltersAndReportsFailures(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val remote = FakeRemote()
            val presenter = EarningsCenterPresenter(remote, scope, today = { "2026-10-08" })
            presenter.start()
            val first = withTimeout(5_000) { presenter.state.first { it.rows.size == 5 && !it.loading } }
            assertEquals("upcoming", remote.queries.last().view)
            assertEquals(listOf("2026-10-11", "2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15"), first.days.map { it.first })
            assertEquals("Company 1, S1. Upcoming. Sunday, October 11, 2026, Before market open. Confirmed. No estimates available. ", first.rows.first().accessibility)
            presenter.setFilters(EarningsFilters(markets = setOf("CA")))
            withTimeout(5_000) { while (remote.queries.last().countries != listOf("CA")) delay(10) }
            presenter.selectTab(EarningsTab.RESULTS)
            assertEquals(EarningsRange.LAST_MONTH, withTimeout(5_000) { presenter.state.first { it.tab == EarningsTab.RESULTS && !it.loading } }.range)
            assertEquals("results", remote.queries.last().view)
            // Following needs sign-in: no request, explanatory note.
            val before = remote.queries.size
            presenter.selectTab(EarningsTab.FOLLOWING)
            val following = withTimeout(5_000) { presenter.state.first { it.tab == EarningsTab.FOLLOWING && !it.loading } }
            assertEquals(before, remote.queries.size)
            assertTrue(following.notes.single().contains("Sign in"))
            remote.fail = true
            presenter.selectTab(EarningsTab.UPCOMING)
            assertNotNull(withTimeout(5_000) { presenter.state.first { it.error != null } }.error)
        } finally { scope.cancel() }
    }

    @Test fun aiRequiresSignInAndPlusBeforeAnyRequest(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val remote = FakeRemote()
            val presenter = EarningsDetailsPresenter("AAPL", remote, scope)
            withTimeout(5_000) { presenter.state.first { !it.loading } }
            presenter.ask("Explain this report")
            assertNotNull(withTimeout(5_000) { presenter.state.first { it.message != null } }.message)
            assertEquals(0, remote.asked) // signed out: nothing sent
        } finally { scope.cancel() }
    }
}
