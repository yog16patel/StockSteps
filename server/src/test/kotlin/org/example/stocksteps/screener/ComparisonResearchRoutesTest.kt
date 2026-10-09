package org.example.stocksteps.screener

import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.portfolio.analytics.DebugEntitlementRequest
import org.example.stocksteps.portfolio.analytics.SubscriptionTier
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.*
import org.example.stocksteps.userdata.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Company Comparison Phase 4 over MOCK fixtures and the in-memory store: sessions, limits, ownership,
 * StockSteps+ checks, snapshots, summaries, PDF export, provider-call accounting and caching.
 * No FMP, Finnhub, Gemini, Firebase or Firestore.
 */
class ComparisonResearchRoutesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val fixture = FixtureMarketDataSource(sampleFallback = true)
    private val stocks = StockService(fixture, fixture)
    private val financials = CompanyFinancialService(fixture)
    private val meter = ProviderUsageMeter()
    private val statementLoads = AtomicInteger()
    private val comparisonLoads = AtomicInteger()

    private class Setup(val store: UserDataStore, val entitlements: EntitlementService, val service: ComparisonResearchService)

    private fun setup(store: UserDataStore = InMemoryUserDataStore(), plusLimit: Int = 100, budget: ProviderRequestBudget = ProviderRequestBudget.UNLIMITED,
                      render: (ResearchSummary) -> ByteArray = { ResearchPdf.render(it) }): Setup {
        val entitlements = EntitlementService(store, clock::millis, debugAllowed = true)
        val history = ComparisonHistoryService(stocks, { symbol, period -> statementLoads.incrementAndGet(); financials.getFundamentals(symbol, period) },
            entitlements, clock, sampleData = true, source = "Sample fixture financial statements (MOCK)", meter = meter, budget = budget)
        val screener = ScreenerService(FixtureScreenerUniverse(), stocks, { financials.getFundamentals(it, "annual") }, PriceChartService(fixture), { 1 / 1.35 }, clock,
            sampleData = true, fundamentalsPerHour = 0, fullRecords = true)
        val service = ComparisonResearchService(store, entitlements, { symbols -> comparisonLoads.incrementAndGet(); screener.compare(symbols.joinToString(",")) }, history, clock,
            plusLimit, meter, render)
        return Setup(store, entitlements, service)
    }

    private fun ApplicationTestBuilder.install(setup: Setup) = application {
        configureApiErrors()
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        routing { comparisonResearchRoutes(setup.service, MockUserAuthenticator(), RequestRateLimiter(10_000)); usageMetricsRoutes(meter, null, mock = true) }
    }

    private suspend fun Setup.plan(uid: String, tier: SubscriptionTier, expired: Boolean = false, state: String? = null) {
        try { entitlements.simulate(uid, DebugEntitlementRequest(tier, expired, state)) } catch (_: UserDataException) {}
    }
    private suspend fun ApplicationTestBuilder.call(uid: String?, method: HttpMethod, path: String, body: String? = null): HttpResponse =
        client.request("/api/v1/me/comparison-research$path") {
            this.method = method
            uid?.let { header("Authorization", "Bearer mock-user:$it") }
            body?.let { contentType(ContentType.Application.Json); setBody(it) }
        }
    private suspend fun ApplicationTestBuilder.create(uid: String, symbols: String = "AAPL,MSFT", title: String? = null) =
        call(uid, HttpMethod.Post, "", """{"symbols":[${symbols.split(',').joinToString(",") { "\"$it\"" }}]${title?.let { ",\"title\":\"$it\"" } ?: ""}}""")
    private suspend fun HttpResponse.session() = json.decodeFromString(ResearchSessionResponse.serializer(), bodyAsText())
    private suspend fun HttpResponse.sessions() = json.decodeFromString(ResearchSessionsResponse.serializer(), bodyAsText())
    private suspend fun HttpResponse.error() = json.decodeFromString(ApiError.serializer(), bodyAsText())
    private suspend fun ApplicationTestBuilder.patch(uid: String, id: String, body: String) = call(uid, HttpMethod.Patch, "/$id", body)
    private fun note(question: String, status: String? = null, note: String? = null, revision: Int? = null) =
        """{"responses":[{"questionId":"$question"${status?.let { ",\"status\":\"$it\"" } ?: ""}${note?.let { ",\"note\":\"$it\"" } ?: ""}}]${revision?.let { ",\"expectedRevision\":$it" } ?: ""}}"""

    private val ranking = Regex("(?i)\\b(better|best|worst|winner|buy|sell|cheap|expensive|outperform|recommend)\\b")

    // ---------- Free tier ----------

    @Test fun freeUsersKeepThreeSessionsAndCanStillEditAndDelete() = testApplication {
        val s = setup(); install(s); s.plan("free", SubscriptionTier.FREE)
        assertTrue(call("free", HttpMethod.Get, "").sessions().let { it.sessions.isEmpty() && it.canCreate && it.limit == 3 })                // zero sessions
        val first = create("free").session()
        assertEquals("AAPL vs MSFT", first.session.title); assertEquals(13, first.progress.total); assertTrue(first.advancedLocked)
        create("free", "KO,RIVN")
        assertEquals(2, call("free", HttpMethod.Get, "").sessions().sessions.size)                                                             // two sessions
        create("free", "RY.TO,TD")
        val full = call("free", HttpMethod.Get, "").sessions()
        assertFalse(full.canCreate); assertTrue(full.message!!.contains("keeps 3"))                                                           // at the limit
        val fourth = create("free", "AMD,NVDA")
        assertEquals(HttpStatusCode.Forbidden, fourth.status); assertEquals("RESEARCH_SESSION_LIMIT", fourth.error().code)
        // Still editable and deletable at the limit.
        val edited = patch("free", first.session.id, note("p-margins", "REVIEWED", "Margins differ a lot.")).session()
        assertEquals(1, edited.progress.reviewed); assertEquals("Margins differ a lot.", edited.session.responses.getValue("p-margins").note)
        assertEquals(HttpStatusCode.OK, call("free", HttpMethod.Delete, "/${first.session.id}").status)
        assertEquals(HttpStatusCode.OK, create("free", "AMD,NVDA").status)
    }

    @Test fun parallelCreatesCannotExceedTheLimit() = testApplication {
        val s = setup(); install(s); s.plan("free", SubscriptionTier.FREE)
        val statuses = coroutineScope { (1..10).map { async { create("free").status } }.awaitAll() }
        assertEquals(3, statuses.count { it == HttpStatusCode.OK })
        assertEquals(3, call("free", HttpMethod.Get, "").sessions().sessions.size)
    }

    @Test fun notesStatusesAndCrudNeverCallProviders() = testApplication {
        val s = setup(); install(s); s.plan("free", SubscriptionTier.FREE)
        val id = create("free").session().session.id
        call("free", HttpMethod.Get, "/$id"); call("free", HttpMethod.Get, "")
        patch("free", id, note("b-revenue-model", "NEEDS_MORE_RESEARCH", "Read the annual report."))
        patch("free", id, """{"title":"My comparison"}""")
        call("free", HttpMethod.Delete, "/$id")
        assertEquals(0, statementLoads.get()); assertEquals(0, comparisonLoads.get())
        assertEquals(0, meter.count(event = "upstream")); assertEquals(0, meter.count(provider = "statements", event = "cacheMiss"))
    }

    @Test fun sessionsArePrivateToTheirOwner() = testApplication {
        val s = setup(); install(s); s.plan("alice", SubscriptionTier.FREE); s.plan("bob", SubscriptionTier.FREE)
        val id = create("alice").session().session.id
        patch("alice", id, note("t-observations", note = "Alice's private note"))
        assertEquals(HttpStatusCode.NotFound, call("bob", HttpMethod.Get, "/$id").status)
        assertEquals(HttpStatusCode.NotFound, patch("bob", id, note("t-observations", note = "overwrite")).status)
        assertEquals(HttpStatusCode.NotFound, call("bob", HttpMethod.Delete, "/$id").status)
        assertEquals(HttpStatusCode.NotFound, call("bob", HttpMethod.Post, "/$id/export").status.takeIf { it != HttpStatusCode.Forbidden } ?: HttpStatusCode.NotFound)
        assertTrue(call("bob", HttpMethod.Get, "").sessions().sessions.isEmpty())
        assertEquals(HttpStatusCode.Unauthorized, call(null, HttpMethod.Get, "/$id").status)
        assertEquals("Alice's private note", call("alice", HttpMethod.Get, "/$id").session().session.responses.getValue("t-observations").note)
    }

    @Test fun validationConflictsAndSaveFailures() = testApplication {
        val s = setup(); install(s); s.plan("u", SubscriptionTier.FREE)
        val session = create("u").session().session
        assertEquals("UNKNOWN_QUESTION", patch("u", session.id, note("nope", "REVIEWED")).error().code)
        assertEquals("NOTE_TOO_LONG", patch("u", session.id, note("t-next", note = "x".repeat(1_001))).error().code)
        assertEquals("INVALID_COMPARISON", create("u", "AAPL").error().code)
        assertEquals(HttpStatusCode.NotFound, call("u", HttpMethod.Get, "/bad id!").status)
        // Concurrent edit: a stale revision is rejected so the client can reload instead of overwriting.
        patch("u", session.id, note("t-next", note = "first device", revision = session.revision))
        val stale = patch("u", session.id, note("t-next", note = "second device", revision = session.revision))
        assertEquals(HttpStatusCode.Conflict, stale.status); assertEquals("RESEARCH_CONFLICT", stale.error().code)
        // Store unavailable (save failure): 503, nothing pretends to be saved.
        val down = setup(UnavailableUserDataStore)
        testApplication { install(down); assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/api/v1/me/comparison-research") {
            header("Authorization", "Bearer mock-user:u"); contentType(ContentType.Application.Json); setBody("""{"symbols":["AAPL","MSFT"]}""") }.status) }
    }

    // ---------- StockSteps+ ----------

    @Test fun plusGetsMoreSessionsAdvancedQuestionsAndAFairUseLimit() = testApplication {
        val s = setup(plusLimit = 5); install(s); s.plan("plus", SubscriptionTier.PLUS)
        repeat(5) { assertEquals(HttpStatusCode.OK, create("plus").status) }
        val over = create("plus"); assertEquals("RESEARCH_SESSION_LIMIT", over.error().code); assertTrue(over.error().message.contains("fair-use"))
        val id = call("plus", HttpMethod.Get, "").sessions().sessions.first().id
        val advanced = patch("plus", id, note("hd-fcf", "REVIEWED", "FCF positive most years")).session()
        assertEquals(ResearchChecklist.questions.size, advanced.progress.total); assertFalse(advanced.advancedLocked)
    }

    @Test fun freeUsersCannotUsePremiumEndpointsAndNoCostlyWorkHappens() = testApplication {
        val s = setup(); install(s); s.plan("free", SubscriptionTier.FREE)
        val id = create("free").session().session.id
        for ((method, path, body) in listOf(Triple(HttpMethod.Post, "/$id/snapshots", "{}"), Triple(HttpMethod.Post, "/$id/export", null))) {
            val r = call("free", method, path, body)
            assertEquals(HttpStatusCode.Forbidden, r.status); assertEquals("PLUS_REQUIRED", r.error().code)
        }
        val advanced = patch("free", id, note("gq-multi-year", "REVIEWED"))
        assertEquals(HttpStatusCode.Forbidden, advanced.status)
        assertEquals(0, comparisonLoads.get()); assertEquals(0, statementLoads.get())
        // A free account asking for the detailed summary gets the basic one.
        val summary = json.decodeFromString(ResearchSummary.serializer(), call("free", HttpMethod.Get, "/$id/summary").bodyAsText())
        assertFalse(summary.detailed)
        assertTrue(summary.sections.none { it.title == "Financial health in depth" || it.title.startsWith("Financial history (") })
        assertTrue(summary.sections.any { it.title == "Latest four quarters" })
    }

    @Test fun downgradeKeepsEverythingAndOnlyStopsNewPremiumWork() = testApplication {
        val s = setup(); install(s); s.plan("u", SubscriptionTier.PLUS)
        val ids = (1..4).map { create("u").session().session.id }
        patch("u", ids[0], note("pq-losses", "NEEDS_MORE_RESEARCH", "Check the loss years"))
        assertEquals(HttpStatusCode.OK, call("u", HttpMethod.Post, "/${ids[0]}/snapshots", """{"label":"Before earnings"}""").status)
        s.plan("u", SubscriptionTier.PLUS, expired = true)                                                                                      // subscription expiry
        val list = call("u", HttpMethod.Get, "").sessions()
        assertEquals(4, list.sessions.size); assertFalse(list.canCreate)                                                                       // nothing deleted
        assertEquals("RESEARCH_SESSION_LIMIT", create("u").error().code)
        val kept = call("u", HttpMethod.Get, "/${ids[0]}").session()
        assertEquals("Check the loss years", kept.session.responses.getValue("pq-losses").note)                                                // advanced answer still readable
        assertTrue(kept.advancedLocked)
        assertEquals(HttpStatusCode.Forbidden, patch("u", ids[0], note("pq-losses", note = "edit")).status)
        assertEquals(HttpStatusCode.OK, patch("u", ids[0], note("t-next", note = "basic edit")).status)
        val snaps = json.decodeFromString(ListSerializer(ResearchSnapshot.serializer()), call("u", HttpMethod.Get, "/${ids[0]}/snapshots").bodyAsText())
        assertEquals("Before earnings", snaps.single().label)                                                                                  // readable after expiry
        assertEquals(HttpStatusCode.Forbidden, call("u", HttpMethod.Post, "/${ids[0]}/snapshots", "{}").status)
        assertEquals(HttpStatusCode.OK, call("u", HttpMethod.Delete, "/${ids[0]}/snapshots/${snaps.single().id}").status)
        repeat(2) { call("u", HttpMethod.Delete, "/${ids[it + 1]}") }
        assertEquals(HttpStatusCode.OK, create("u").status)                                                                                    // below the free limit again
    }

    @Test fun snapshotsAreUserCreatedSmallAndLimited() = testApplication {
        val s = setup(); install(s); s.plan("plus", SubscriptionTier.PLUS)
        val id = create("plus", "RY.TO,AAPL").session().session.id
        patch("plus", id, note("v-ratios", "REVIEWED", "P/E differs"))
        val snap = call("plus", HttpMethod.Post, "/$id/snapshots", """{"label":"Week 1"}""").session().session.snapshots.single()
        assertEquals("Week 1", snap.label); assertEquals("P/E differs", snap.responses.getValue("v-ratios").note)
        assertNotNull(snap.dataAsOf)
        assertTrue(snap.metrics.isNotEmpty() && snap.metrics.all { it.value.isNotBlank() })                                                    // references, not datasets
        repeat(ComparisonResearchService.MAX_SNAPSHOTS - 1) { call("plus", HttpMethod.Post, "/$id/snapshots", "{}") }
        assertEquals("SNAPSHOT_LIMIT", call("plus", HttpMethod.Post, "/$id/snapshots", "{}").error().code)
        // Editing a note never creates a snapshot.
        patch("plus", id, note("v-ratios", note = "edited"))
        assertEquals(ComparisonResearchService.MAX_SNAPSHOTS, call("plus", HttpMethod.Get, "/$id").session().session.snapshots.size)
    }

    @Test fun detailedSummaryLabelsFactsCalculationsAndNotes() = testApplication {
        val s = setup(); install(s); s.plan("plus", SubscriptionTier.PLUS)
        val id = create("plus", "RY.TO,AAPL").session().session.id
        patch("plus", id, note("t-observations", "REVIEWED", "I think the bank looks steadier"))
        patch("plus", id, note("r-missing", "NEEDS_MORE_RESEARCH"))
        val summary = json.decodeFromString(ResearchSummary.serializer(), call("plus", HttpMethod.Get, "/$id/summary").bodyAsText())
        assertTrue(summary.detailed && summary.sampleData)
        val titles = summary.sections.map { it.title }
        for (t in listOf("Companies compared", "Key differences", "Growth", "Valuation context", "Financial history (5Y)", "Financial health in depth", "Your notes", "Questions needing more research", "Data sources and limitations"))
            assertTrue(t in titles, "$t missing from $titles")
        val note = summary.sections.first { it.title == "Your notes" }.items.single()
        assertEquals(SummaryKind.USER_NOTE, note.kind); assertTrue(note.attribution!!.startsWith("Your note on:"))                             // attributed, never a fact
        assertTrue(summary.sections.first { it.title == "Financial health in depth" }.items.any { it.text.contains("financial company") })     // RY.TO: D/E not calculated
        assertTrue(summary.sections.first { it.title == "Data sources and limitations" }.items.any { it.text.contains("currency") || it.text.contains("currencies") })
        // Data-derived lines never rank (educational lines may say "a larger company isn't automatically a better business").
        assertTrue(summary.sections.flatMap { it.items }.filter { it.kind == SummaryKind.FACT || it.kind == SummaryKind.CALCULATION }.none { ranking.containsMatchIn(it.text) },
            summary.sections.flatMap { it.items }.firstOrNull { (it.kind == SummaryKind.FACT || it.kind == SummaryKind.CALCULATION) && ranking.containsMatchIn(it.text) }?.text)
    }

    @Test fun pdfExportIsPlusOnlyPrivateAndHandlesFailures() = testApplication {
        val s = setup(); install(s); s.plan("plus", SubscriptionTier.PLUS)
        val id = create("plus", "AAPL,MSFT", "Tech giants").session().session.id
        patch("plus", id, note("t-next", "NEEDS_MORE_RESEARCH", "Read both 10-K risk sections"))
        val pdf = call("plus", HttpMethod.Post, "/$id/export")
        assertEquals(HttpStatusCode.OK, pdf.status)
        assertEquals(ContentType.Application.Pdf, pdf.contentType()?.withoutParameters())
        assertTrue(pdf.headers[HttpHeaders.CacheControl]!!.contains("no-store"))
        assertTrue(pdf.headers[HttpHeaders.ContentDisposition]!!.contains("StockSteps-research-AAPL-MSFT.pdf"))
        val bytes = pdf.bodyAsBytes(); val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4") && text.trimEnd().endsWith("%%EOF"))
        assertTrue(text.contains("Tech giants") && text.contains("Read both 10-K risk sections") && text.contains("Page 1 of"))
        assertTrue(Regex("/Count (\\d+)").find(text)!!.groupValues[1].toInt() >= 2)                                                         // multi-page
        assertEquals(HttpStatusCode.Unauthorized, call(null, HttpMethod.Post, "/$id/export").status)
        // Renderer failure → stable error, no partial file.
        val broken = setup(render = { throw IllegalStateException("font") })
        testApplication {
            install(broken); broken.plan("plus", SubscriptionTier.PLUS)
            val sid = create("plus").session().session.id
            val failed = call("plus", HttpMethod.Post, "/$sid/export")
            assertEquals(HttpStatusCode.InternalServerError, failed.status); assertEquals("EXPORT_FAILED", failed.error().code)
        }
    }

    @Test fun entitlementOutageFailsClosedForPremiumOnly() = testApplication {
        val s = setup(); install(s); s.plan("free", SubscriptionTier.FREE)
        val id = create("free").session().session.id
        s.plan("free", SubscriptionTier.PLUS, state = "unavailable")
        assertEquals(HttpStatusCode.ServiceUnavailable, create("free").status)                                                                  // never create on an unknown plan
        assertFalse(call("free", HttpMethod.Get, "").sessions().canCreate)
        assertEquals(HttpStatusCode.OK, patch("free", id, note("t-next", note = "still saves")).status)                                         // basic editing continues
        assertEquals(HttpStatusCode.ServiceUnavailable, call("free", HttpMethod.Post, "/$id/export").status)
        assertFalse(json.decodeFromString(ResearchSummary.serializer(), call("free", HttpMethod.Get, "/$id/summary").bodyAsText()).detailed)
    }

    // ---------- Shared data layer: reuse, caching, single flight, budget ----------

    @Test fun summariesReuseCachedStatementsAndCoalesceIdenticalRequests() = testApplication {
        val s = setup(); install(s); s.plan("plus", SubscriptionTier.PLUS)
        val id = create("plus", "AAPL,MSFT").session().session.id
        statementLoads.set(0)
        call("plus", HttpMethod.Get, "/$id/summary")                                                                                           // cache miss: AAPL+MSFT annual
        assertEquals(2, statementLoads.get())
        assertEquals(2, meter.count(provider = "statements", event = "cacheMiss"))
        call("plus", HttpMethod.Get, "/$id/summary"); call("plus", HttpMethod.Post, "/$id/export")                                            // cache hits
        assertEquals(2, statementLoads.get())
        assertTrue(meter.count(provider = "statements", event = "cacheHit", feature = "comparison-research") >= 4)
        // Many identical requests at once → one load per dataset (single flight per instance).
        val other = create("plus", "KO,JNJ").session().session.id
        statementLoads.set(0)
        coroutineScope { (1..8).map { async { call("plus", HttpMethod.Get, "/$other/summary").status } }.awaitAll() }.forEach { assertEquals(HttpStatusCode.OK, it) }
        assertEquals(2, statementLoads.get())
        val report = client.get("/internal/metrics/usage").bodyAsText()
        assertTrue(report.contains("research.summary.detailed") && report.contains("cacheHit"))
        assertFalse(report.contains("KO,JNJ") || report.contains("plus"))                                                                      // aggregates only, no user data
    }

    @Test fun requestBudgetStopsUpstreamLoadsInsteadOfOverspending() = testApplication {
        val s = setup(budget = ProviderRequestBudget(mapOf("comparison-research" to 1))); install(s); s.plan("plus", SubscriptionTier.PLUS)
        val id = create("plus", "AAPL,MSFT").session().session.id
        val summary = json.decodeFromString(ResearchSummary.serializer(), call("plus", HttpMethod.Get, "/$id/summary").bodyAsText())
        assertEquals(1, statementLoads.get())                                                                                                   // the second load was refused
        assertTrue(meter.count(provider = "statements", event = "budgetExceeded") >= 1)                                                       // each refused attempt is counted
        assertTrue(summary.sections.flatMap { it.items }.any { it.text.contains("request limit reached") })                                  // said plainly, nothing invented
    }
}
