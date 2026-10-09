package org.example.stocksteps.earnings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.model.EarningsDateStatus
import org.example.stocksteps.model.EarningsTime
import org.example.stocksteps.network.StockStepsApiException
import org.example.stocksteps.portfolio.analytics.EntitlementStatus
import kotlin.test.*

/** Earnings Intelligence Lite, Phase 5: deterministic history rules (shared by server and apps). */
class HistoricalEarningsEngineTest {
    private fun q(y: Int, q: Int, rev: Double?, eps: Double?, revEst: Double? = null, epsEst: Double? = null, currency: String = "USD", basis: EpsBasis = EpsBasis.GAAP_DILUTED,
                  end: String? = null, restated: Boolean = false) =
        EarningsEvent("ACME:$y-Q$q", "ACME", "Acme Corp", "NYSE", fiscalYear = y, fiscalQuarter = q, periodEnd = end ?: defaultEnd(y, q), date = "$y-0${minOf(q * 3, 9)}-20",
            estimate = if (revEst != null || epsEst != null) EarningsEstimate(epsEst, basis, revEst, currency, source = "t") else null,
            actual = EarningsActual(eps, basis, rev, currency, "t", "$y-01-01T00:00:00Z", restated), source = "t", updatedAt = "t")
    private fun defaultEnd(y: Int, q: Int) = "$y-${(q * 3).toString().padStart(2, '0')}-${if (q == 1 || q == 4) 31 else 30}"
    private fun build(vararg e: EarningsEvent, limit: Int = 8, cursor: String? = null) = HistoricalEarningsEngine.build(e.toList(), limit, cursor, "now")

    @Test fun ordersByFiscalPeriodAndNeverPlotsMissingQuartersAsZero() {
        val h = build(q(2026, 2, 110.0, 0.5), q(2025, 4, 100.0, 0.4), q(2026, 1, 105.0, null))
        assertEquals(listOf("Q4 FY2025", "Q1 FY2026", "Q2 FY2026"), h.fiscalPeriods)               // oldest → newest whatever the input order
        assertEquals(listOf("100", "105", "110"), h.revenueSeries)
        assertEquals(listOf("0.4", null, "0.5"), h.epsSeries)                                      // missing EPS is a gap, not 0
        val gap = build(q(2026, 2, 110.0, 0.5), q(2025, 4, 100.0, 0.4))
        assertEquals(3, gap.quarters.size); assertTrue(gap.quarters[1].missing); assertNull(gap.revenueSeries[1])
        assertTrue(gap.dataWarnings.any { it.contains("missing") })
        assertEquals(2, gap.availableQuarters)                                                      // only real quarters count
    }

    @Test fun negativeEpsCurrencyAndBasisChangesAreHandledHonestly() {
        val h = build(q(2025, 3, 90.0, -0.35), q(2025, 4, 95.0, -0.2, currency = "CAD"), q(2026, 1, 99.0, -0.1, basis = EpsBasis.ADJUSTED), q(2026, 2, 101.0, -0.05, basis = EpsBasis.ADJUSTED))
        assertEquals("-0.05", h.epsSeries.last())
        assertNull(h.revenueSeries[1]); assertFalse(h.quarters[1].revenueComparable)                 // CAD quarter isn't plotted with USD
        assertNull(h.epsSeries[0]); assertFalse(h.quarters[0].epsComparable)                         // different EPS measure isn't plotted
        assertTrue(h.dataWarnings.any { it.contains("currency") }); assertTrue(h.dataWarnings.any { it.contains("EPS measure") })
        assertTrue(h.dataWarnings.contains(HistoricalEarningsEngine.SPLIT_NOTE))
        val chart = EarningsPremiumFormat.chart(h, HistoryMetric.EPS)
        assertTrue(chart.zeroLine); assertEquals(listOf(null, null, -0.1, -0.05), chart.values)          // GAAP quarters aren't mixed with adjusted ones
    }

    @Test fun fiscalCalendarChangeBreaksTheSeries() {
        val h = build(q(2026, 2, 100.0, 0.5, end = "2026-06-30"), q(2026, 3, 101.0, 0.5, end = "2026-11-30"))
        assertTrue(h.quarters[1].breakBefore); assertTrue(h.dataWarnings.any { it.contains("fiscal calendar") })
        val chart = EarningsPremiumFormat.chart(h, HistoryMetric.REVENUE)
        assertEquals(listOf(100.0, null, 101.0), chart.values)                                       // never connected across the change
    }

    @Test fun observationsOnlyStateWhatTheDataShows() {
        // Eight quarters: revenue up year over year in the latest four, EPS beat in 3 of 4 comparisons.
        val e = (0 until 8).map { i -> val y = 2025 + i / 4; val qq = i % 4 + 1
            q(y, qq, 100.0 + i * 5, 1.0, revEst = 99.0 + i * 5, epsEst = if (i == 6) 1.1 else 0.9) }
        val h = build(*e.toTypedArray())
        assertTrue(h.deterministicObservations.any { it.text == "Revenue increased year over year in each of the last 4 comparable quarters." })
        assertTrue(h.deterministicObservations.any { it.text == "EPS exceeded comparable consensus estimates in 3 of the last 4 quarters." })
        assertTrue(h.deterministicObservations.none { it.text.contains("buy", ignoreCase = true) || it.text.contains("will") })
        // Two consecutive year-over-year declines.
        val down = build(q(2025, 1, 100.0, 1.0), q(2025, 2, 100.0, 1.0), q(2026, 1, 90.0, 1.0), q(2026, 2, 95.0, 1.0))
        assertTrue(down.deterministicObservations.any { it.kind == ObservationKind.REVENUE_DECLINE && it.text.contains("Q1 FY2026 and Q2 FY2026") })
        // Too few points → says so instead of describing a trend.
        val few = build(q(2026, 1, 100.0, 1.0), q(2026, 2, 101.0, 1.0))
        assertEquals("The available results are insufficient to establish a trend.", few.deterministicObservations.single().text)
        assertTrue(few.dataWarnings.any { it.startsWith("Only 2 reported quarters") })
    }

    @Test fun paginationAndEmptyHistory() {
        val e = (0 until 8).map { i -> q(2025 + i / 4, i % 4 + 1, 100.0 + i, 1.0) }
        val first = build(*e.toTypedArray(), limit = 5)
        assertEquals(5, first.availableQuarters); assertEquals("ACME:2025-Q4", first.nextCursor)
        val second = build(*e.toTypedArray(), limit = 5, cursor = first.nextCursor)
        assertEquals(3, second.availableQuarters); assertNull(second.nextCursor)
        assertFailsWith<IllegalArgumentException> { build(*e.toTypedArray(), cursor = "OTHER:2020-Q1") }
        assertFailsWith<IllegalArgumentException> { build(*e.toTypedArray(), limit = 9) }
        assertEquals(ObservationKind.INSUFFICIENT, HistoricalEarningsEngine.build(emptyList(), 8, null, "now").deterministicObservations.single().kind)
    }
}

class EarningsQuestionChipsTest {
    private fun insights(eps: Classification, rev: Classification, yoy: String? = null, estimates: Boolean = true) = EarningsResultInsights("X:2026-Q3",
        MetricComparison(estimate = if (estimates) "1" else null, classification = eps, explanation = ""), MetricComparison(estimate = if (estimates) "1" else null, classification = rev, explanation = ""),
        GrowthComparison(percent = yoy, currentLabel = "Q3", explanation = ""), GrowthComparison(currentLabel = "Q3", explanation = ""), "t")

    @Test fun chipsFollowTheFactsAndNeverAssumeMissingData() {
        val beatFell = EarningsQuestionChips.forReport(insights(Classification.BEAT, Classification.MISS, "-2.5"), "-3.1").map { it.id }
        assertEquals(listOf("fall-after-beat", "revenue-miss", "mixed", "yoy", "growth-slow", "reaction"), beatFell)
        val none = EarningsQuestionChips.forReport(insights(Classification.UNAVAILABLE, Classification.UNAVAILABLE, estimates = false), null).map { it.id }
        assertTrue("no-estimates" in none); assertFalse("expectations" in none); assertFalse("reaction" in none); assertFalse("yoy" in none)
        assertTrue(EarningsQuestionChips.forReport(insights(Classification.BEAT, Classification.BEAT), "2.0").size <= EarningsQuestionChips.MAX)
    }
}

class EarningsDigestRulesTest {
    private fun item(eps: Classification, rev: Classification) = DigestReportedItem("A:2026-Q3", "A", "A Co", "2026-10-06", EarningsDigestRules.headline(eps, rev), eps, rev)

    @Test fun headlinesLessonsAndPushTextAreDeterministic() {
        assertEquals("EPS beat, revenue miss", EarningsDigestRules.headline(Classification.BEAT, Classification.MISS))
        assertEquals("EPS and revenue above expectations", EarningsDigestRules.headline(Classification.BEAT, Classification.BEAT))
        assertEquals("Results published; no comparable estimates", EarningsDigestRules.headline(Classification.UNAVAILABLE, Classification.UNAVAILABLE))
        val lessons = EarningsDigestRules.lessons(listOf(item(Classification.BEAT, Classification.MISS)), emptyList(), listOf("reaction"), fellAfterBeat = true)
        assertEquals(listOf("mixed", "fall-after-beat", "reaction"), lessons.map { it.key })
        val upcomingOnly = EarningsDigestRules.lessons(emptyList(), listOf(DigestUpcomingItem("B:2026-Q3", "B", "B Co", "2026-10-14", EarningsTime.BEFORE_OPEN, EarningsDateStatus.CONFIRMED, "Oct 14")), emptyList(), false)
        assertEquals(listOf("quarterly"), upcomingOnly.map { it.key })
        assertEquals("Two companies on your watchlist reported earnings this week.", EarningsDigestRules.notificationBody(2, 5))
        assertEquals("One company on your watchlist is expected to report soon.", EarningsDigestRules.notificationBody(0, 1))
        assertNull(EarningsDigestRules.notificationBody(0, 0))                                        // never an empty digest
    }
}

/** The shared presenters: server-decided plan, account isolation, honest error states. */
@OptIn(ExperimentalCoroutinesApi::class)
class EarningsPremiumPresenterTest {
    private class Remote : EarningsPremiumRemote {
        var plus = mutableMapOf("alice" to true, "bob" to false)
        var owner = "alice"
        var fail: Exception? = null
        val calls = mutableListOf<String>()
        private fun currentUsage() = EarningsAiUsage(plus[owner] == true, if (plus[owner] == true) EntitlementStatus.ACTIVE else EntitlementStatus.NONE,
            listOf(EarningsAiQuota(EarningsAiCategory.EXPLANATION, 10, 1, 9, "2026-10-08T00:00:00Z")), "now")
        override suspend fun overview(reportId: String, scenario: String?): PremiumEarningsOverview { calls += "overview:$owner"
            return PremiumEarningsOverview(reportId, plus[owner] == true, preview = "Preview for $owner", suggestedQuestions = listOf(SuggestedQuestion("yoy", "Was revenue higher than last year?")), usage = currentUsage()) }
        override suspend fun history(reportId: String, limit: Int, cursor: String?, scenario: String?): HistoricalEarningsInsight { calls += "history"; fail?.let { throw it }
            return HistoricalEarningsEngine.build(listOf(
                EarningsEvent("X:2026-Q2", "X", "X", fiscalYear = 2026, fiscalQuarter = 2, date = "2026-07-20", actual = EarningsActual(1.0, EpsBasis.GAAP_DILUTED, 100.0, "USD", "t"), source = "t", updatedAt = "t")), limit, cursor, "now") }
        override suspend fun explain(reportId: String, refresh: Boolean, scenario: String?): EarningsAiExplanation { calls += "explain:$owner"; fail?.let { throw it }
            return EarningsAiExplanation("e1", reportId, "X", "X Co", "Q3 FY2026", "now", "v1", "p", "m", ExplainedClaim("Summary for $owner", listOf("S1-results")), disclaimer = "d", usage = currentUsage()) }
        override suspend fun ask(reportId: String, question: EarningsAiQuestion, scenario: String?): EarningsAiAnswer { calls += "ask:${question.conversationId}"; fail?.let { throw it }
            val reset = question.conversationId == "c1" && question.question.contains("revised")
            return EarningsAiAnswer("a${calls.size}", if (reset) "c2" else "c1", reportId, question.question, "Answer", generatedAt = "now", contextId = if (reset) "v2" else "v1", contextReset = reset) }
        override suspend fun usage() = currentUsage()
        override suspend fun digest(scenario: String?) = PersonalizedEarningsDigest("d-$owner", "2026-10-01", "2026-10-07", generatedAt = "now", sourceVersion = "v").also { calls += "digest:$owner" }
        override suspend fun explainDigest(scenario: String?) = digest(scenario).copy(aiSummary = DigestAiSummary("Summary", generatedAt = "now"))
        override suspend fun digestHistory() = EarningsDigestHistory(asOf = "now")
        override suspend fun digestSettings() = EarningsDigestSettings(plus = plus[owner] == true, statusMessage = "s")
        override suspend fun saveDigestPreferences(preferences: EarningsDigestPreferences): EarningsDigestSettings { calls += "save:${preferences.cadence}"; fail?.let { throw it }
            return EarningsDigestSettings(preferences, plus[owner] == true, statusMessage = "saved") }
    }
    private fun TestScope.settle() { advanceTimeBy(1_000); runCurrent() }

    @Test fun plusUserExplainsAsksAndLoadsHistory() = runTest {
        val remote = Remote(); val session = MutableStateFlow<String?>("alice")
        val p = EarningsPremiumPresenter("X:2026-Q3", remote, backgroundScope, session).also { it.start() }
        settle()
        assertTrue(p.state.value.plus); assertEquals("9 of 10 explanations left · resets Thu, Oct 8, 00:00 UTC", p.state.value.explainUsage)
        p.explain(); settle()
        assertEquals("Summary for alice", p.state.value.explanation!!.summary.text)
        p.ask("Was revenue higher than last year?"); settle()
        assertEquals("c1", p.state.value.conversationId); assertEquals("Answer", p.state.value.conversation.single().answer!!.answer)
        p.ask("And if revised?"); settle()
        assertEquals("ask:c1", remote.calls.last())
        assertEquals(1, p.state.value.conversation.size); assertEquals("c2", p.state.value.conversationId)          // server reset → old turns dropped
        assertTrue(p.state.value.contextNote!!.contains("updated"))
        p.loadHistory(); settle()
        assertNotNull(p.state.value.chart); assertEquals(1, p.state.value.historyRows.size)
        p.selectMetric(HistoryMetric.EPS); assertEquals(HistoryMetric.EPS, p.state.value.chart!!.metric)
    }

    @Test fun freeUserGetsContextualUpgradeWithoutAnyRequest() = runTest {
        val remote = Remote().also { it.owner = "bob" }
        val p = EarningsPremiumPresenter("X:2026-Q3", remote, backgroundScope, MutableStateFlow("bob")).also { it.start() }
        settle()
        assertFalse(p.state.value.plus)
        p.explain(); p.ask("What is EPS?"); p.loadHistory(); settle()
        assertEquals(UpgradeReason.HISTORY, p.state.value.upgrade)
        assertTrue(remote.calls.none { it.startsWith("explain") || it.startsWith("ask") || it == "history" })
        p.dismissUpgrade(); assertNull(p.state.value.upgrade)
    }

    @Test fun serverDecidesPlanAndErrorsStayVisible() = runTest {
        val remote = Remote()
        val p = EarningsPremiumPresenter("X:2026-Q3", remote, backgroundScope, MutableStateFlow("alice")).also { it.start() }
        settle()
        // The plan lapsed on the server: the request's 403 turns into the upgrade prompt.
        remote.fail = StockStepsApiException(403, ApiError("PLUS_REQUIRED", "AI earnings explanations are part of StockSteps+."))
        p.explain(); settle()
        assertEquals(UpgradeReason.EXPLAIN, p.state.value.upgrade); assertFalse(p.state.value.plus); assertNull(p.state.value.explainError)
        val q = Remote(); val p2 = EarningsPremiumPresenter("X:2026-Q3", q, backgroundScope, MutableStateFlow("alice")).also { it.start() }
        settle()
        q.fail = StockStepsApiException(429, ApiError("AI_QUOTA_EXCEEDED", "You've used all 10 explanations for today."))
        p2.explain(); settle()
        assertEquals("AI_QUOTA_EXCEEDED", p2.state.value.explainError!!.code); assertFalse(p2.state.value.explainError!!.retryable)
        q.fail = StockStepsApiException(503, ApiError("AI_UNAVAILABLE", "The AI service isn't available right now."))
        p2.explain(); settle()
        assertTrue(p2.state.value.explainError!!.retryable); assertNull(p2.state.value.explanation)    // never a fabricated explanation
        q.fail = StockStepsApiException(503, ApiError("AI_UNAVAILABLE", "x"))
        p2.ask("What is EPS?"); settle()
        assertNotNull(p2.state.value.conversation.single().error)
    }

    @Test fun accountSwitchAndSignOutClearEverything() = runTest {
        val remote = Remote(); val session = MutableStateFlow<String?>("alice")
        val p = EarningsPremiumPresenter("X:2026-Q3", remote, backgroundScope, session).also { it.start() }
        settle(); p.explain(); settle()
        assertNotNull(p.state.value.explanation)
        remote.owner = "bob"; session.value = "bob"; settle()
        assertNull(p.state.value.explanation); assertFalse(p.state.value.plus); assertTrue(p.state.value.conversation.isEmpty())
        session.value = null; settle()
        assertFalse(p.state.value.signedIn); assertNull(p.state.value.overview)
        p.explain(); assertTrue(p.state.value.signInRequired)
    }

    @Test fun digestPresenterGatesOnTheServerPlan() = runTest {
        val remote = Remote(); val session = MutableStateFlow<String?>("alice")
        val d = EarningsDigestPresenter(remote, backgroundScope, session).also { it.start() }
        settle()
        assertEquals("d-alice", d.state.value.digest!!.digestId)
        d.explain(); settle(); assertEquals("Summary", d.state.value.digest!!.aiSummary!!.text)
        d.updatePreferences { it.copy(cadence = DigestCadence.WEEKLY) }; settle()
        assertEquals(DigestCadence.WEEKLY, d.state.value.preferences.cadence)
        remote.owner = "bob"; session.value = "bob"; settle()
        assertNull(d.state.value.digest); assertFalse(remote.calls.contains("digest:bob"))            // free: no digest request
        d.updatePreferences { it.copy(cadence = DigestCadence.WEEKLY) }; settle()
        assertEquals(UpgradeReason.DIGEST, d.state.value.upgrade); assertEquals(1, remote.calls.count { it == "save:WEEKLY" })
        d.dismissUpgrade(); d.updatePreferences { it.copy(cadence = DigestCadence.NONE) }; settle()
        assertTrue(remote.calls.contains("save:NONE"))                                                 // turning it off never needs StockSteps+
    }
}
