package org.example.stocksteps.earnings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.example.stocksteps.model.EarningsDateStatus
import org.example.stocksteps.model.EarningsTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Global UI Refinement Phase 5D.2: Earnings Details history rows carry the server's EPS classification (for the shared badge) and the same
 * "actual vs estimate" amounts and currency as the chart tooltip for that quarter. Display only: nothing is recomputed, missing stays missing.
 */
class EarningsPhase5D2Test {
    private fun event(year: Int, quarter: Int) = EarningsEvent("CNR.TO:$year-Q$quarter", "CNR.TO", "Canadian National Railway Co", "TSX", "CA",
        fiscalYear = year, fiscalQuarter = quarter, date = "$year-0${quarter + 3}-22", session = EarningsTime.AFTER_CLOSE,
        dateStatus = EarningsDateStatus.CONFIRMED, source = "test", updatedAt = "now")

    private val history = listOf(
        EarningsHistoryRow(event(2026, 2), SurpriseResult(1.94, 2.00, -0.06, -3.0, Classification.MISS, currency = "CAD"), SurpriseResult(4.45e9, 4.32e9, currency = "CAD")),
        EarningsHistoryRow(event(2026, 1), SurpriseResult(1.86, 1.77, 0.09, 5.1, Classification.BEAT, currency = "CAD"), SurpriseResult(4.3e9, null, currency = "CAD")),
        // No estimate: no classification, no surprise — never a fabricated percent.
        EarningsHistoryRow(event(2025, 4), SurpriseResult(1.72, null, currency = "CAD"), SurpriseResult())
    )

    private class Remote(private val details: EarningsDetails) : EarningsRemote {
        override suspend fun calendar(query: EarningsCalendarQuery): EarningsCalendarPage = throw UnsupportedOperationException()
        override suspend fun following(query: EarningsCalendarQuery): EarningsCalendarPage = throw UnsupportedOperationException()
        override suspend fun details(symbol: String, signedIn: Boolean) = details
        override suspend fun ask(symbol: String, question: String): EarningsAnswer = throw UnsupportedOperationException()
        override suspend fun event(id: String): EarningsEventInfo = throw UnsupportedOperationException()
        override suspend fun next(symbol: String): NextEarnings = throw UnsupportedOperationException()
        override suspend fun results(reportId: String): EarningsResultsResponse = throw UnsupportedOperationException()
        override suspend fun latestResults(symbol: String): EarningsResultsResponse = throw UnsupportedOperationException()
        override suspend fun priceReaction(reportId: String, window: ReactionWindow): EarningsPriceReactionResponse = throw UnsupportedOperationException()
    }

    private fun state(): EarningsDetailsState = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val details = EarningsDetails("CNR.TO", "Canadian National Railway Co", event(2026, 2), EarningsStatus.REPORTED, history[0].eps, history[0].revenue,
                history = history, asOf = "now")
            withTimeout(5_000) { EarningsDetailsPresenter("CNR.TO", Remote(details), scope).state.first { !it.loading } }
        } finally { scope.cancel() }
    }

    @Test fun historyRowsCarryTheServerClassification() {
        assertEquals(listOf(Classification.MISS, Classification.BEAT, Classification.UNAVAILABLE), state().history.map { it.classification })
    }

    @Test fun rowComparisonMatchesTheChartTooltipForTheSameQuarter() {
        val s = state()
        val row = s.history.first() // newest first in rows; the chart is oldest → newest
        assertEquals("C$1.94 vs C$2.00 est.", row.epsVsEstimate)
        assertEquals("−3.0%", row.surprise)
        assertEquals("Q2 26 · reported C$1.94 · est. C$2.00", s.epsChartDetails.last())
        // The expanded EPS line (unchanged) is the same text as the new collapsed comparison.
        assertEquals(row.epsVsEstimate, row.lines.first { it.label == "EPS" }.value)
    }

    @Test fun missingEstimateStaysMissing() {
        val row = state().history.last()
        assertNull(row.surprise)
        assertEquals("C$1.72 vs — est.", row.epsVsEstimate) // the existing "—" for a missing estimate, never 0.00
    }
}
