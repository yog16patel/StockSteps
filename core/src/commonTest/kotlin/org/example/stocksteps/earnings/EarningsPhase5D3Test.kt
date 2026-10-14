package org.example.stocksteps.earnings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.model.EarningsDateStatus
import org.example.stocksteps.model.EarningsTime
import kotlin.test.*

/**
 * Global UI Refinement Phase 5D.3 (visual polish): headline, key metrics and reaction summary are display-only views of values the presenters
 * already format. Never a beat/miss without the server's classification; missing estimates stay "—", never zero.
 */
class EarningsPhase5D3Test {
    @Test fun headlineComesOnlyFromTheServerClassification() {
        assertEquals("EPS missed estimates by 3.0%.", EarningsFormatter.epsHeadline(SurpriseResult(1.94, 2.0, -0.06, -3.0, Classification.MISS)))
        assertEquals("EPS beat estimates by 5.9%.", EarningsFormatter.epsHeadline(SurpriseResult(1.8, 1.7, 0.1, 5.88, Classification.BEAT)))
        assertEquals("EPS met estimates.", EarningsFormatter.epsHeadline(SurpriseResult(1.0, 1.0, 0.0, 0.0, Classification.MET)))
        // A beat with no meaningful percent (near-zero estimate) states no amount.
        assertEquals("EPS beat estimates.", EarningsFormatter.epsHeadline(SurpriseResult(0.05, 0.001, 0.049, null, Classification.BEAT)))
        // No estimate → no headline (the screen falls back to the server summary); never inferred from the numbers.
        assertNull(EarningsFormatter.epsHeadline(SurpriseResult(1.94, null)))
        assertNull(EarningsFormatter.epsHeadline(SurpriseResult(2.5, 2.0, 0.5, 25.0, Classification.UNAVAILABLE)))
        assertNull(EarningsFormatter.epsHeadline(null))
    }

    private fun event() = EarningsEvent("CNR.TO:2026-Q2", "CNR.TO", "Canadian National Railway Co", "TSX", "CA", fiscalYear = 2026, fiscalQuarter = 2,
        date = "2026-07-22", session = EarningsTime.AFTER_CLOSE, dateStatus = EarningsDateStatus.CONFIRMED, source = "test", updatedAt = "now")

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

    private fun details(d: EarningsDetails): EarningsDetailsState = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try { withTimeout(5_000) { EarningsDetailsPresenter("CNR.TO", Remote(d), scope).state.first { !it.loading } } } finally { scope.cancel() }
    }

    private val cnr = EarningsDetails("CNR.TO", "Canadian National Railway Co", event(), EarningsStatus.REPORTED,
        SurpriseResult(1.94, 2.00, -0.06, -3.0, Classification.MISS, currency = "CAD", basis = "GAAP diluted"),
        SurpriseResult(4.45e9, null, currency = "CAD", reason = "No analyst revenue estimate is available."), revenueGrowthYoY = 3.0, revenueGrowthQoQ = 4.2,
        summary = "EPS miss, revenue not comparable", asOf = "now")

    @Test fun detailsKeyMetricsAreTheCardsOwnValues() {
        val s = details(cnr)
        assertEquals("EPS missed estimates by 3.0%.", s.headline)
        assertEquals(listOf("Reported" to "C$1.94", "Estimate" to "C$2.00", "Surprise" to "−3.0%"), s.eps!!.keyMetrics.map { it.label to it.value })
        assertEquals(listOf("Difference", "Basis"), s.eps!!.secondary.map { it.label })
        // Revenue: no estimate stays "—" (never 0.00), the third number is the YoY growth, quarter-over-quarter is secondary.
        assertEquals(listOf("Reported" to "C$4.45B", "Estimate" to "No estimate", "YoY growth" to "+3.0%"), s.revenue!!.keyMetrics.map { it.label to it.value })
        assertEquals(listOf("Growth vs last quarter"), s.revenue!!.secondary.map { it.label })
        // Every original line is still in `lines` (accessibility and fallbacks unchanged).
        assertEquals(s.eps!!.lines.toSet(), (s.eps!!.keyMetrics + s.eps!!.secondary).toSet())
    }

    @Test fun headlineFallsBackToTheServerSummaryWithoutAClassification() {
        assertEquals("EPS miss, revenue not comparable", details(cnr.copy(eps = SurpriseResult(1.94, null, currency = "CAD"))).headline)
    }

    @Test fun reactionShowsTheMeasuredChangeFirstAndFoldsTheDetails() {
        val reaction = PriceReaction(available = true, baselineClose = 132.64, baselineDate = "2026-07-22", endClose = 132.97, endDate = "2026-07-23",
            changePercent = 0.25, window = "Close on Wed, Jul 22 → close on Thu, Jul 23", source = "StockSteps sample price history", asOf = "2026-10-07", currency = "CAD", methodology = "close to close")
        val s = details(cnr.copy(reaction = reaction))
        assertEquals(s.reaction.first { it.label == "Change" }.value, s.reactionHeadline)
        assertEquals(PriceDirection.UP, s.reactionDirection)
        assertEquals(listOf("Before", "After"), s.reactionKey.map { it.label })
        // 5D.4.1: price as the value, the observation date as its detail (same strings as before, split).
        assertEquals(Triple("C$132.64", "Wed, Jul 22", "C$132.97"), Triple(s.reactionKey[0].value, s.reactionKey[0].detail, s.reactionKey[1].value))
        assertEquals("Next · Q3 FY2026 · Thu, Oct 22", details(cnr.copy(next = event().copy(id = "CNR.TO:2026-Q3", fiscalQuarter = 3, date = "2026-10-22"))).nextLine)
        assertEquals(listOf("Window", "Source"), s.reactionDetails.map { it.label })
    }

    @Test fun resultsKeyMetricsKeepMissingEstimatesMissing() {
        val card = EarningsResultsFormat.card("revenue", MetricComparison(actual = "4450000000", explanation = "x", currency = "CAD"), false)
        assertEquals(listOf("Actual" to "C$4.45B", "Estimate" to "No estimate", "Surprise" to "—"), card.keyMetrics.map { it.label to it.value })
        val folded = EarningsResultsFormat.foldGrowth(card, GrowthComparison("4450000000", "4320000000", "Q2 FY2026", "Q2 FY2025", "3.009", "x", currency = "CAD"))!!
        assertEquals(listOf("Actual", "Estimate", "YoY growth"), folded.keyMetrics.map { it.label })
        assertEquals("Year ago (Q2 FY2025)", folded.secondary.last().label)
        // No growth percent → nothing folded; the YoY card keeps its "not available" reason.
        assertNull(EarningsResultsFormat.foldGrowth(card, GrowthComparison(currentLabel = "Q2 FY2026", explanation = "x", reason = "Revenue for the same quarter last year isn't available.")))
        val eps = EarningsResultsFormat.card("eps", MetricComparison("1.94", "2.00", "-0.06", "-3.0", Classification.MISS, "x", basis = "GAAP diluted", currency = "CAD"), true)
        assertEquals(listOf("C$1.94", "C$2.00"), eps.keyMetrics.take(2).map { it.value })
        assertEquals(listOf("Difference"), eps.secondary.map { it.label })
    }
}
