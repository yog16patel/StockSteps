package org.example.stocksteps.earnings

import kotlin.test.*

/** Global UI Refinement Phase 5D.4: display-only axis labels and folded previous-quarter comparison; never new data, missing stays missing. */
class EarningsPhase5D4Test {
    private val presenter get() = EarningsDetailsPresenter("CNR.TO", object : EarningsRemote {
        override suspend fun calendar(query: EarningsCalendarQuery): EarningsCalendarPage = throw UnsupportedOperationException()
        override suspend fun following(query: EarningsCalendarQuery): EarningsCalendarPage = throw UnsupportedOperationException()
        override suspend fun details(symbol: String, signedIn: Boolean): EarningsDetails = throw UnsupportedOperationException()
        override suspend fun ask(symbol: String, question: String): EarningsAnswer = throw UnsupportedOperationException()
        override suspend fun event(id: String): EarningsEventInfo = throw UnsupportedOperationException()
        override suspend fun next(symbol: String): NextEarnings = throw UnsupportedOperationException()
        override suspend fun results(reportId: String): EarningsResultsResponse = throw UnsupportedOperationException()
        override suspend fun latestResults(symbol: String): EarningsResultsResponse = throw UnsupportedOperationException()
        override suspend fun priceReaction(reportId: String, window: ReactionWindow): EarningsPriceReactionResponse = throw UnsupportedOperationException()
    }, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined + kotlinx.coroutines.Job().also { it.cancel() }))

    @Test fun axisLabelsComeOnlyFromShownValues() {
        val p = presenter
        // Highest, middle, lowest of the reported + estimated values, in the quarters' currency.
        assertEquals(listOf("C$2.00", "C$1.88", "C$1.75"), p.epsAxis(listOf(1.79, 1.86, 1.86, 1.94), listOf(1.75, 1.77, 1.79, 2.00), List(4) { "CAD" }))
        // Fewer than two reported values: no axis (the chart isn't drawn either). Missing values are skipped, never treated as zero.
        assertTrue(p.epsAxis(listOf(null, 1.2, null), listOf(1.0, 1.1, 1.2), List(3) { "USD" }).isEmpty())
        assertEquals("$1.00", p.epsAxis(listOf(1.0, null, 2.0), listOf(null, null, null), List(3) { "USD" }).last())
        // Mixed currencies: no axis rather than a misleading single symbol.
        assertTrue(p.epsAxis(listOf(1.0, 2.0), listOf(1.0, 2.0), listOf("USD", "CAD")).isEmpty())
        // Unknown currency: no "$" (5D.1B).
        assertFalse(p.epsAxis(listOf(1.0, 2.0), listOf(1.0, 2.0), listOf(null, null)).any { "$" in it })
    }

    @Test fun previousQuarterFoldsIntoRevenueOnlyWithAPercent() {
        val revenue = EarningsResultsFormat.card("revenue", MetricComparison(actual = "4450000000", explanation = "x", currency = "CAD"), false)
        val folded = EarningsResultsFormat.foldQuarter(revenue, GrowthComparison("4450000000", "4280000000", "Q2 FY2026", "Q1 FY2026", "4.2", "x", currency = "CAD"))!!
        assertEquals("Previous quarter (Q1 FY2026)" to "C$4.28B · +4.2%", folded.secondary.last().let { it.label to it.value })
        assertEquals(revenue.keyMetrics, folded.keyMetrics)
        assertNull(EarningsResultsFormat.foldQuarter(revenue, GrowthComparison(currentLabel = "Q2 FY2026", explanation = "x", reason = "Revenue for the previous quarter isn't available.")))
    }
}
