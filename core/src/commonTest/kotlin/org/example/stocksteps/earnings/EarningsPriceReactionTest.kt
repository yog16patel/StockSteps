package org.example.stocksteps.earnings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.model.EarningsTime
import org.example.stocksteps.network.StockStepsApiException
import kotlin.test.*

/** Earnings Intelligence Lite, Phase 3: deterministic explanations and the shared price-reaction presenter. */
@OptIn(ExperimentalCoroutinesApi::class)
class EarningsPriceReactionTest {
    private val b = Classification.BEAT; private val m = Classification.MISS; private val u = Classification.UNAVAILABLE; private val met = Classification.MET

    private fun obs(date: String, price: String) = PriceObservation(date, "${date}T16:00-04:00", price, "USD", source = "test")
    private fun reaction(pct: String? = "5", abs: String? = "7.5", status: ReactionStatus = ReactionStatus.AVAILABLE, window: ReactionWindow = ReactionWindow.FIRST_SESSION) =
        EarningsPriceReaction("X:2026-Q3:${window.name}", "X:2026-Q3", "X:2026-Q3", "X", "NYSE", "2026-10-06", null, null, EarningsTime.AFTER_CLOSE, window, status,
            if (status == ReactionStatus.AVAILABLE) null else "Not available.", obs("2026-10-06", "150"), if (pct != null) obs("2026-10-07", "157.5") else null,
            abs, pct, "USD", PriceAdjustment.SPLIT_ADJUSTED, "Close on Tue, Oct 6 → close on Wed, Oct 7", 1, listOf("warning"), listOf("test"), "now")

    @Test fun explanationsCoverEveryCaseWithoutCausalClaims() {
        assertTrue(PriceReactionExplainer.explanation(reaction(), b, b).startsWith("The stock increased after the earnings announcement. Positive results"))       // A
        assertTrue(PriceReactionExplainer.explanation(reaction("-3.2", "-1.28"), b, met).startsWith("The company exceeded some earnings expectations, but its stock price declined")) // B
        assertTrue(PriceReactionExplainer.explanation(reaction(), m, m).startsWith("The company reported results below some expectations, yet its share price increased"))   // C
        assertTrue(PriceReactionExplainer.explanation(reaction("-10", "-1.2"), m, m).startsWith("The company reported results below expectations, and its share price declined")) // D
        assertTrue(PriceReactionExplainer.explanation(reaction("-3.2", "-1"), b, m).endsWith("These observations do not establish which factors caused the price movement."))  // E
        assertEquals(PriceReactionExplainer.MIXED, PriceReactionExplainer.mixedNote(b, m)); assertNull(PriceReactionExplainer.mixedNote(b, b))
        assertTrue(PriceReactionExplainer.explanation(reaction(), u, u).endsWith(PriceReactionExplainer.NO_ESTIMATES))                                                   // F
        assertEquals(PriceReactionExplainer.NO_PRICE, PriceReactionExplainer.explanation(reaction(null, null, ReactionStatus.ENDPOINT_UNAVAILABLE), b, b))              // G
        assertEquals(PriceReactionExplainer.INCOMPLETE, PriceReactionExplainer.explanation(reaction(null, null, ReactionStatus.WINDOW_INCOMPLETE), b, b))                // H
        assertTrue(PriceReactionExplainer.explanation(reaction("0", "0"), b, b).contains("unchanged"))
        assertTrue(PriceReactionExplainer.explanation(reaction(status = ReactionStatus.EVENT_TIME_UNKNOWN), b, b).endsWith("so this compares a broader window."))
        val all = listOf("5", "-5", "0").flatMap { p -> Classification.entries.flatMap { e -> Classification.entries.map { r -> PriceReactionExplainer.explanation(reaction(p), e, r) } } }
        val forbidden = Regex("(?i)buying opportunity|should (buy|sell)|will (recover|rise|fall)|punished|caused the stock|because of the earnings")
        assertTrue(all.none { forbidden.containsMatchIn(it) })
        assertEquals("The stock price increased by 5.0% between the selected observations around the earnings announcement. This change may reflect earnings news and other market factors.",
            PriceReactionExplainer.summary(reaction()))
    }

    @Test fun formattingBuildsLinesContextChartAndAccessibleSummary() {
        val history = EarningsPriceHistory("X:2026-Q3", listOf(PriceHistoryPoint("2026-10-05", "149"), PriceHistoryPoint("2026-10-06", "150"), PriceHistoryPoint("2026-10-07", "157.5"),
            PriceHistoryPoint("2026-10-08", null)), "2026-10-06", "2026-10-07", "Earnings: Tue, Oct 6, after market close", "USD", PriceAdjustment.SPLIT_ADJUSTED, listOf("2026-10-08"), "test")
        val response = EarningsPriceReactionResponse(reaction(), history, listOf(ReactionWindowOption(ReactionWindow.FIRST_SESSION, ReactionStatus.AVAILABLE)), b, m,
            "explanation", PriceReactionExplainer.MIXED)
        val s = PriceReactionFormat.state(EarningsPriceReactionState("X:2026-Q3"), response)
        assertEquals(listOf("$150.00", "$157.50", "+$7.50", "+5.0%", "First Session"), s.lines.map { it.value })
        assertEquals("Tue, Oct 6, regular-session close", s.lines.first().detail)
        assertEquals("EPS: Beat · Revenue: Miss · Stock reaction: +5.0%", s.contextLine)
        val chart = s.chart!!
        assertEquals(listOf(149.0, 150.0, 157.5, null), chart.values)
        assertEquals(2, chart.eventIndex); assertEquals(1, chart.baselineIndex); assertEquals(2, chart.endpointIndex)
        assertEquals("Wed, Oct 7: $157.50 · endpoint · earnings", chart.details[2])
        assertTrue(chart.description.contains("Baseline Tue, Oct 6 $150.00, endpoint Wed, Oct 7 $157.50, change $7.50, +5.0%. Window: First Session."))
        assertTrue(chart.missingNote!!.contains("Thu, Oct 8"))
        assertTrue(s.accessibility.startsWith("How did the stock react? Before earnings: $150.00."))
        assertTrue(s.learn.isNotEmpty() && s.learn.all { it.body != null })
        val incomplete = PriceReactionFormat.state(EarningsPriceReactionState("X:2026-Q3"), response.copy(reaction = reaction(null, null, ReactionStatus.WINDOW_INCOMPLETE, ReactionWindow.THREE_SESSIONS)))
        assertTrue(incomplete.statusText!!.startsWith("Not available yet.")); assertTrue(incomplete.lines.isEmpty())
    }

    private class Remote : PriceReactionRemote {
        val calls = mutableListOf<ReactionWindow>()
        var fail: Exception? = null
        override suspend fun priceReaction(reportId: String, window: ReactionWindow): EarningsPriceReactionResponse {
            calls += window; fail?.let { throw it }
            val r = EarningsPriceReaction("$reportId:${window.name}", reportId, reportId, "X", "NYSE", "2026-10-06", timing = EarningsTime.AFTER_CLOSE, window = window,
                status = if (window == ReactionWindow.FIRST_SESSION) ReactionStatus.AVAILABLE else ReactionStatus.WINDOW_INCOMPLETE,
                baseline = PriceObservation("2026-10-06", null, "150", "USD", source = "t"),
                endpoint = if (window == ReactionWindow.FIRST_SESSION) PriceObservation("2026-10-07", null, "157.5", "USD", source = "t") else null,
                absoluteChange = "7.5".takeIf { window == ReactionWindow.FIRST_SESSION }, percentChange = "5".takeIf { window == ReactionWindow.FIRST_SESSION }, currency = "USD", calculatedAt = "now")
            return EarningsPriceReactionResponse(r, EarningsPriceHistory(reportId, eventDate = "2026-10-06", eventLabel = "Earnings"), explanation = "e")
        }
    }
    private class MemoryCache : EarningsResultsCache {
        val saved = HashMap<String, Pair<String, Long>>()
        override suspend fun read(reportId: String) = saved[reportId]
        override suspend fun write(reportId: String, json: String, savedAt: Long) { saved[reportId] = json to savedAt }
    }
    private fun TestScope.settle() { advanceTimeBy(1_000); runCurrent() }

    @Test fun presenterSwitchesWindowsWithoutDuplicateRequestsAndWorksOffline() = runTest {
        val remote = Remote(); val cache = MemoryCache()
        val p = EarningsPriceReactionPresenter("X:2026-Q3", remote, backgroundScope, cache); settle()
        assertTrue(p.state.value.available); assertEquals(listOf(ReactionWindow.FIRST_SESSION), remote.calls)
        p.selectWindow(ReactionWindow.FIRST_SESSION); settle(); assertEquals(1, remote.calls.size)     // same window: no request
        p.selectWindow(ReactionWindow.THREE_SESSIONS); settle()
        assertEquals(ReactionWindow.THREE_SESSIONS, p.state.value.window); assertFalse(p.state.value.available)
        assertTrue(p.state.value.statusText!!.startsWith("Not available yet."))
        p.toggleChart(); assertTrue(p.state.value.expandedChart)
        // Offline: the saved copy for the window, clearly labelled.
        remote.fail = IllegalStateException("offline")
        val offline = EarningsPriceReactionPresenter("X:2026-Q3", remote, backgroundScope, cache); settle()
        assertTrue(offline.state.value.offline && offline.state.value.available)
        // Failure without a copy: a retryable error, distinct from "unavailable".
        val none = EarningsPriceReactionPresenter("Y:2026-Q3", remote.also { it.fail = StockStepsApiException(503, ApiError("X", "Server down.")) }, backgroundScope, MemoryCache()); settle()
        assertEquals("Server down.", none.state.value.error); assertNull(none.state.value.response)
        remote.fail = null; none.refresh(); settle()
        assertNull(none.state.value.error); assertTrue(none.state.value.available)
        assertEquals(ReactionWindow.THREE_SESSIONS, EarningsPriceReactionPresenter("X:2026-Q3", remote, backgroundScope, null, ReactionWindow.THREE_SESSIONS).currentWindow) // restored window
    }
}
