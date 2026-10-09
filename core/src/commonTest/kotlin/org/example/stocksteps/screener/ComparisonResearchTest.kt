package org.example.stocksteps.screener

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.example.stocksteps.model.FinancialAvailability
import org.example.stocksteps.model.FinancialBasis
import org.example.stocksteps.model.FinancialPeriodStatement
import kotlin.test.*

/** Company Comparison Phase 4: checklist definitions, progress, summaries and financial-health formulas. */
class ComparisonResearchTest {
    @Test fun checklistHasStableUniqueIdsAndAUsefulFreeLayer() {
        val ids = ResearchChecklist.questions.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val free = ResearchChecklist.questions(plus = false)
        assertTrue(free.size in 10..15, "${free.size} free questions")
        assertEquals(listOf("business", "growth", "profitability", "health", "valuation", "risks", "takeaways"),
            free.map { it.category }.distinct())
        assertTrue(ResearchChecklist.questions.all { q -> ResearchChecklist.categories.any { it.id == q.category && it.tier == q.tier } })
        assertTrue(ResearchChecklist.questions(plus = true).size > free.size)
        assertTrue(ResearchChecklist.questions.flatMap { it.metrics }.all { ScreenerDefinitions.metric(it) != null })     // context ids exist
        assertTrue(ResearchChecklist.questions.map { it.text + it.help }.none { Regex("(?i)\\b(buy|sell|winner|best stock)\\b").containsMatchIn(it) })
    }

    @Test fun progressCountsOnlyQuestionsTheTierSeesAndKeepsUnknownResponses() {
        val responses = mapOf(
            "p-margins" to ResearchResponse("p-margins", ResearchStatus.REVIEWED),
            "h-debt" to ResearchResponse("h-debt", ResearchStatus.NEEDS_MORE_RESEARCH, "Check leases"),
            "gq-multi-year" to ResearchResponse("gq-multi-year", ResearchStatus.REVIEWED),                       // advanced (StockSteps+)
            "retired-question" to ResearchResponse("retired-question", ResearchStatus.REVIEWED, "old note"))      // from an older checklist version
        val free = ResearchProgress.of(responses, plus = false)
        assertEquals(13, free.total); assertEquals(1, free.reviewed); assertEquals(1, free.needsMore); assertEquals(11, free.notReviewed); assertEquals(7, free.percent)
        val plus = ResearchProgress.of(responses, plus = true)
        assertEquals(ResearchChecklist.questions.size, plus.total); assertEquals(2, plus.reviewed)
        assertEquals(ResearchStatus.NOT_REVIEWED, ResearchResponse("x").status)                                   // default: never auto-reviewed
    }

    private fun fy(year: Int, debt: Double?, equity: Double?, ocf: Double?, net: Double?, fcf: Double?, currency: String = "USD") =
        FinancialPeriodStatement("FY", year, "$year-12-31", currency, netIncome = net, operatingCashFlow = ocf, freeCashFlow = fcf, totalDebt = debt, equity = equity)

    @Test fun financialHealthFormulasUseReportedFieldsAndRespectLimits() {
        val items = ResearchHealthEngine.observations("Alpha", listOf(fy(2023, 50.0, 100.0, 30.0, 20.0, 10.0), fy(2024, 60.0, 100.0, 15.0, 25.0, -5.0), fy(2025, 90.0, 100.0, 40.0, 30.0, 12.0)), financial = false)
        val text = items.joinToString("\n") { it.text }
        assertTrue(text.contains("debt to equity went from 0.50 (FY2023) to 0.90 (FY2025)"), text)
        assertTrue(text.contains("operating cash flow was below net income in 1 of 3 fiscal years"))
        assertTrue(text.contains("free cash flow was positive in 2 of 3 fiscal years"))
        val negative = ResearchHealthEngine.observations("Beta", listOf(fy(2024, 50.0, -10.0, null, 5.0, null), fy(2025, 60.0, -20.0, null, 5.0, null)), financial = false)
        assertTrue(negative.any { it.kind == SummaryKind.LIMITATION && it.text.contains("zero or negative equity in 2 fiscal years") })
        assertTrue(negative.any { it.text.contains("free cash flow isn't available") })
        val bank = ResearchHealthEngine.observations("Bank", listOf(fy(2025, 500.0, 50.0, 10.0, 9.0, 8.0)), financial = true)
        assertTrue(bank.first().text.contains("financial company") && bank.none { it.text.contains("debt to equity went") })
        assertEquals(SummaryKind.LIMITATION, ResearchHealthEngine.observations("Empty", emptyList(), false).single().kind)
    }

    private val record = CompanyRecord("AAA", "Alpha", "NYSE", "US", "USD", "Technology", "Software", marketCap = 1e11, fundamentalsAsOf = "2026-10-01T00:00:00Z",
        metrics = mapOf("pe" to MetricValue(30.0, FinancialAvailability.AVAILABLE, FinancialBasis("TTM")), "netMargin" to MetricValue(20.0, FinancialAvailability.AVAILABLE, FinancialBasis("TTM"))))
    private val other = record.copy(symbol = "BBB", name = "Beta", metrics = mapOf("pe" to MetricValue(12.0, FinancialAvailability.AVAILABLE, FinancialBasis("TTM")),
        "netMargin" to MetricValue(null, FinancialAvailability.MISSING)))
    private val comparison = ComparisonResponse(listOf(ComparedCompany(record, "AAA"), ComparedCompany(other, "BBB")), asOf = "2026-10-07T21:15:00Z", sampleData = true)
    private val session = ResearchSession("rs-1", "Alpha vs Beta", listOf("AAA", "BBB"), responses = mapOf(
        "t-observations" to ResearchResponse("t-observations", ResearchStatus.REVIEWED, "Alpha seems pricier to me"),
        "r-missing" to ResearchResponse("r-missing", ResearchStatus.NEEDS_MORE_RESEARCH),
        "pq-losses" to ResearchResponse("pq-losses", ResearchStatus.REVIEWED, "advanced note")))

    @Test fun basicAndDetailedSummariesSeparateNotesFromData() {
        val basic = ResearchSummaryEngine.build(ResearchSummaryEngine.Inputs(session, comparison, null, emptyMap(), detailed = false, generatedAt = "2026-10-08T00:00:00Z"))
        assertFalse(basic.detailed)
        val notes = basic.sections.first { it.title == "Your notes" }.items
        assertEquals(listOf("Alpha seems pricier to me"), notes.map { it.text })                                 // the advanced note isn't in the free summary
        assertTrue(notes.all { it.kind == SummaryKind.USER_NOTE && it.attribution!!.startsWith("Your note on:") })
        assertEquals("2026-10-01T00:00:00Z", basic.dataAsOf)
        assertTrue(basic.sections.first { it.title == "Questions needing more research" }.items.any { it.text == ResearchChecklist.question("r-missing")!!.text })
        assertTrue(basic.sections.first { it.title == "Data sources and limitations" }.items.any { it.text.startsWith("Net profit margin: not enough data") })
        assertTrue(basic.sections.none { it.title == "Valuation context" })
        val detailed = ResearchSummaryEngine.build(ResearchSummaryEngine.Inputs(session, comparison, null, mapOf("AAA" to listOf(fy(2025, 1.0, 2.0, 3.0, 2.0, 1.0))), detailed = true, generatedAt = "x"))
        assertTrue(detailed.sections.any { it.title == "Valuation context" && it.items.any { i -> i.text.startsWith("P/E ratio (trailing): Alpha has a higher trailing P/E than Beta") } })
        assertTrue(detailed.sections.first { it.title == "Your notes" }.items.any { it.text == "advanced note" })
        assertTrue(detailed.sections.any { it.title == "Financial health in depth" })
        // Without comparison data the summary says so instead of inventing values.
        val empty = ResearchSummaryEngine.build(ResearchSummaryEngine.Inputs(session, null, null, emptyMap(), false, "x"))
        assertTrue(empty.sections.first { it.title == "Key differences" }.items.single().kind == SummaryKind.LIMITATION)
        assertEquals(basic, ResearchSummaryEngine.build(ResearchSummaryEngine.Inputs(session, comparison, null, emptyMap(), false, "2026-10-08T00:00:00Z")))   // deterministic
    }

    @Test fun snapshotMetricsAreDisplayValuesOnly() {
        val metrics = ResearchSummaryEngine.snapshotMetrics(comparison)
        assertEquals(listOf(SnapshotMetric("AAA", "netMargin", "20.0%", "Trailing twelve months"), SnapshotMetric("AAA", "pe", "30.0×", "Trailing twelve months"),
            SnapshotMetric("BBB", "pe", "12.0×", "Trailing twelve months")), metrics.sortedWith(compareBy({ it.symbol }, { it.metric })))
    }
}

/** Phase 4 presenter: sessions, statuses, drafts, conflicts, StockSteps+ entry point, account changes. */
class ComparisonResearchPresenterTest {
    private class Fake(var plus: Boolean) : ComparisonResearchRemote {
        val sessions = linkedMapOf<String, ResearchSession>()
        val calls = mutableListOf<String>()
        var conflictNext = false
        private fun response(s: ResearchSession) = ResearchSessionResponse(s, plus, ResearchProgress.of(s.responses, plus), !plus, if (plus) 10 else 0)
        override suspend fun sessions(): ResearchSessionsResponse { calls += "list"; return ResearchSessionsResponse(sessions.values.map { ResearchSessionInfo(it.id, it.title, it.symbols, 0, 0) }, if (plus) 100 else 3, plus, sessions.size < 3 || plus) }
        override suspend fun create(symbols: List<String>, title: String?): ResearchSessionResponse { calls += "create"; val s = ResearchSession("rs-${sessions.size + 1}", title ?: symbols.joinToString(" vs "), symbols, revision = 1); sessions[s.id] = s; return response(s) }
        override suspend fun session(id: String): ResearchSessionResponse { calls += "get"; return response(sessions.getValue(id)) }
        override suspend fun update(id: String, request: UpdateResearchRequest): ResearchSessionResponse {
            calls += "update"
            val s = sessions.getValue(id)
            if (conflictNext) { conflictNext = false; sessions[id] = s.copy(revision = s.revision + 1, title = "Edited elsewhere")
                throw org.example.stocksteps.network.StockStepsApiException(409, org.example.stocksteps.model.ApiError("RESEARCH_CONFLICT", "Changed on another device.")) }
            if (request.responses.any { ResearchChecklist.question(it.questionId)?.tier == ResearchTier.PLUS } && !plus)
                throw org.example.stocksteps.network.StockStepsApiException(403, org.example.stocksteps.model.ApiError("PLUS_REQUIRED", "Part of StockSteps+."))
            val responses = s.responses.toMutableMap()
            request.responses.forEach { u -> val r = responses[u.questionId] ?: ResearchResponse(u.questionId); responses[u.questionId] = r.copy(status = u.status ?: r.status, note = u.note ?: r.note) }
            val next = s.copy(responses = responses, revision = s.revision + 1); sessions[id] = next; return response(next)
        }
        override suspend fun delete(id: String): ResearchSessionsResponse { sessions.remove(id); return sessions() }
        override suspend fun summary(id: String) = ResearchSummary(id, "t", plus, "x", progress = ResearchProgress(13, 0, 0))
        override suspend fun snapshots(id: String) = sessions.getValue(id).snapshots
        override suspend fun createSnapshot(id: String, label: String?): ResearchSessionResponse { calls += "snapshot"; val s = sessions.getValue(id)
            val next = s.copy(snapshots = s.snapshots + ResearchSnapshot("snap-1", label ?: "Snapshot 1", 1, "2026-10-01", s.symbols, s.responses, ResearchProgress(13, 0, 0))); sessions[id] = next; return response(next) }
        override suspend fun deleteSnapshot(id: String, snapshotId: String) = response(sessions.getValue(id))
        override suspend fun export(id: String): ByteArray { calls += "export"; return "%PDF-1.4".encodeToByteArray() }
    }

    private fun test(plus: Boolean, block: suspend CoroutineScope.(Fake, kotlinx.coroutines.flow.MutableStateFlow<String?>, ComparisonResearchPresenter) -> Unit) = kotlinx.coroutines.runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fake = Fake(plus); val owner = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
            block(fake, owner, ComparisonResearchPresenter(fake, scope, owner))
        } finally { scope.cancel() }
    }
    private suspend fun ComparisonResearchPresenter.until(predicate: (ResearchUiState) -> Boolean) = kotlinx.coroutines.withTimeout(5_000) { state.first(predicate) }

    @Test fun guestsLoadNothingAndSignedInUsersSaveStatusesAndNotes(): Unit = test(false) { fake, owner, p ->
        assertFalse(p.state.value.signedIn); assertTrue(fake.calls.isEmpty())
        owner.value = "u1"
        p.until { it.sessions != null }
        p.create(listOf("AAPL", "MSFT"))
        val opened = p.until { it.open != null }
        assertEquals(13, opened.categories().sumOf { it.questions.size })                                         // free layer only
        assertEquals(5, opened.advancedPreview.size)
        p.setStatus("p-margins", ResearchStatus.REVIEWED)
        p.until { it.open!!.session.responses["p-margins"]?.status == ResearchStatus.REVIEWED }
        p.editNote("p-margins", "Margins differ")
        assertTrue(p.state.value.categories().flatMap { it.questions }.first { it.question.id == "p-margins" }.dirty)
        p.saveNote("p-margins")
        val saved = p.until { it.open!!.session.responses["p-margins"]?.note == "Margins differ" && it.drafts.isEmpty() }
        assertFalse(saved.categories().flatMap { it.questions }.first { it.question.id == "p-margins" }.dirty)
        owner.value = "u2"                                                                                       // another account: nothing carried over
        p.until { it.open == null && it.drafts.isEmpty() }
    }

    @Test fun conflictsReloadButKeepTheDraft(): Unit = test(false) { fake, owner, p ->
        owner.value = "u1"; p.until { it.sessions != null }
        p.create(listOf("AAPL", "MSFT")); p.until { it.open != null }
        fake.conflictNext = true
        p.editNote("t-next", "my unsaved thought"); p.saveNote("t-next")
        val after = p.until { it.message != null }
        assertEquals("Edited elsewhere", after.open!!.session.title)
        assertEquals("my unsaved thought", after.drafts["t-next"])
    }

    @Test fun freeAccountsSeeOneUpgradePreviewInsteadOfPremiumRequests(): Unit = test(false) { fake, owner, p ->
        owner.value = "u1"; p.until { it.sessions != null }
        p.create(listOf("AAPL", "MSFT")); p.until { it.open != null }
        p.export(); p.until { it.upsell != null }; p.dismissUpsell()
        p.createSnapshot(); p.until { it.upsell != null }; p.dismissUpsell()
        assertTrue("export" !in fake.calls && "snapshot" !in fake.calls)
        p.setStatus("gq-multi-year", ResearchStatus.REVIEWED)                                                    // the server refuses an advanced edit
        assertEquals(ResearchUpsell.DEFAULT, p.until { it.upsell != null }.upsell)
    }

    @Test fun plusSnapshotsAndExportAndDowngradedAnswersStayReadable(): Unit = test(true) { fake, owner, p ->
        owner.value = "u1"; p.until { it.sessions != null }
        p.create(listOf("AAPL", "MSFT")); p.until { it.open != null }
        p.setStatus("pq-losses", ResearchStatus.NEEDS_MORE_RESEARCH); p.until { it.open!!.session.responses.containsKey("pq-losses") }
        p.createSnapshot("Week 1"); assertEquals("Week 1", p.until { it.snapshots.isNotEmpty() }.snapshots.single().label)
        p.export(); val export = p.until { it.export != null }.export!!
        assertTrue(export.fileName.endsWith(".pdf") && export.bytes.decodeToString().startsWith("%PDF"))
        p.exportHandled("Saved.")
        fake.plus = false                                                                                        // plan ended
        p.open(p.state.value.open!!.session.id)
        val downgraded = p.until { it.open?.plus == false }
        val advanced = downgraded.categories().first { it.category.id == "profitQuality" }
        assertTrue(advanced.questions.all { it.locked })                                                         // shown read-only, not hidden
        assertEquals(ResearchStatus.NEEDS_MORE_RESEARCH, advanced.questions.first { it.question.id == "pq-losses" }.status)
    }

    @Test fun contextComesFromTheComparisonAlreadyLoaded() {
        val guide = ComparisonInterpretationEngine.interpret(listOf(
            ComparedCompany(CompanyRecord("A", "Alpha", sector = "Technology", metrics = mapOf("netMargin" to MetricValue(20.0, org.example.stocksteps.model.FinancialAvailability.AVAILABLE, org.example.stocksteps.model.FinancialBasis("TTM")))), "A"),
            ComparedCompany(CompanyRecord("B", "Beta", sector = "Energy", metrics = mapOf("netMargin" to MetricValue(10.0, org.example.stocksteps.model.FinancialAvailability.AVAILABLE, org.example.stocksteps.model.FinancialBasis("TTM")))), "B")))
        val state = ComparisonUiState(guides = guide.metrics, insights = guide.summary, industryNote = guide.industryNote)
        val margins = ResearchContext.lines(ResearchChecklist.question("p-margins")!!, state)
        assertTrue(margins.single().startsWith("Net profit margin (compare with care): Alpha has a higher net profit margin than Beta"))
        assertTrue(ResearchContext.lines(ResearchChecklist.question("b-comparable")!!, state).any { it.contains("different sectors") })
        assertTrue(ResearchContext.lines(ResearchChecklist.question("p-margins")!!, null).isEmpty())
    }
}
