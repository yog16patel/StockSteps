package org.example.stocksteps.brief

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.model.*
import org.example.stocksteps.portfolio.analytics.SubscriptionTier
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.screener.RequestRateLimiter
import org.example.stocksteps.service.MarketsService
import org.example.stocksteps.service.MarketsSourceLabels
import org.example.stocksteps.service.NewsService
import org.example.stocksteps.service.StockService
import org.example.stocksteps.userdata.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class MovableClock(var instant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = instant
}

class BriefSessionsTest {
    private val sessions = BriefSessions()
    private fun at(iso: String) = sessions.moment(Instant.parse(iso))

    @Test fun editionsFollowTheUsSessionInEasternTime() {
        assertEquals(BriefEdition.PRE_MARKET, at("2026-10-08T12:00:00Z").edition)          // 08:00 EDT
        assertEquals("2026-10-07", at("2026-10-08T12:00:00Z").sessionDate.toString(), "a pre-market brief describes the last completed session")
        assertEquals(BriefEdition.MARKET_HOURS, at("2026-10-08T15:00:00Z").edition)
        assertEquals("2026-10-08", at("2026-10-08T15:00:00Z").sessionDate.toString())
        assertEquals(BriefEdition.AFTER_CLOSE, at("2026-10-07T21:15:00Z").edition)
        assertEquals("2026-10-07", at("2026-10-07T21:15:00Z").sessionDate.toString())
        assertEquals(BriefEdition.AFTER_CLOSE, at("2026-10-08T02:00:00Z").edition, "late evening (22:00 EDT) is still after the close")
        assertEquals(BriefEdition.PRE_MARKET, at("2026-10-08T05:00:00Z").edition, "01:00 EDT is before the next session")
    }

    @Test fun weekendsAndHolidaysDescribeTheLatestCompletedSession() {
        val weekend = at("2026-10-10T14:00:00Z")
        assertEquals(BriefEdition.WEEKEND, weekend.edition)
        assertEquals("2026-10-09", weekend.sessionDate.toString())
        val christmas = at("2026-12-25T15:00:00Z")
        assertEquals(BriefEdition.HOLIDAY, christmas.edition)
        assertEquals("Christmas Day", christmas.us.holiday)
        assertEquals(BriefPhase.HOLIDAY, christmas.ca.phase)
        assertEquals("2026-12-24", christmas.sessionDate.toString())
    }

    @Test fun usAndCanadianHolidaysDiffer() {
        val cdnThanksgiving = at("2026-10-12T15:00:00Z")
        assertEquals(BriefPhase.OPEN, cdnThanksgiving.us.phase)
        assertEquals(BriefPhase.HOLIDAY, cdnThanksgiving.ca.phase)
        assertEquals("Thanksgiving Day", cdnThanksgiving.ca.holiday)
        assertEquals("2026-10-09", cdnThanksgiving.ca.lastCompletedSession)
        val usThanksgiving = at("2026-11-26T16:00:00Z")
        assertEquals(BriefPhase.HOLIDAY, usThanksgiving.us.phase)
        assertEquals(BriefPhase.OPEN, usThanksgiving.ca.phase)
        assertEquals(BriefEdition.HOLIDAY, usThanksgiving.edition)
    }

    @Test fun tsxHolidayRules() {
        val tsx = TsxMarketCalendar()
        listOf("2026-01-01" to "New Year's Day", "2026-02-16" to "Family Day", "2026-04-03" to "Good Friday", "2026-05-18" to "Victoria Day",
            "2026-07-01" to "Canada Day", "2026-08-03" to "Civic Holiday", "2026-09-07" to "Labour Day", "2026-10-12" to "Thanksgiving Day",
            "2026-12-25" to "Christmas Day", "2026-12-28" to "Boxing Day").forEach { (d, name) -> assertEquals(name, tsx.holiday(LocalDate.parse(d)), d) }
        assertNull(tsx.holiday(LocalDate.parse("2026-11-26")))
    }

    @Test fun daylightSavingIsHandledByTheZone() {
        // 13:00Z is 09:00 EDT in October but 08:00 EST in November (both pre-market), 14:45Z is open in both.
        assertEquals(BriefPhase.OPEN, sessions.usSession(Instant.parse("2026-11-04T14:45:00Z")).phase)
        assertEquals(BriefPhase.PRE_MARKET, sessions.usSession(Instant.parse("2026-11-04T14:15:00Z")).phase)
        assertEquals(BriefPhase.OPEN, sessions.usSession(Instant.parse("2026-10-07T14:15:00Z")).phase)
    }
}

/** Deterministic market inputs; counts calls to prove the global brief is shared. */
class FakeBriefSource : BriefMarketSource {
    val calls = AtomicInteger()
    var indices: List<IndexQuote>? = listOf(
        IndexQuote("SP500", "S&P 500", "^GSPC", 6800.5, 40.2, 0.6, "points", "USD", "US", asOf = "2026-10-07T20:00:00Z"),
        IndexQuote("NASDAQ_COMPOSITE", "Nasdaq Composite", "^IXIC", 23000.0, -46.0, -0.2, "points", "USD", "US", asOf = "2026-10-07T20:00:00Z"),
        IndexQuote("TSX", "S&P/TSX Composite", "^GSPTSE", 30000.0, 0.0, 0.001, "points", "CAD", "Canada", asOf = "2026-10-07T20:00:00Z"),
        IndexQuote("DOW", "Dow Jones Industrial Average", "^DJI", 47000.0, 10.0, 0.02, "points", "USD", "US", asOf = "2026-10-07T20:00:00Z"))
    var news: List<NewsArticle>? = listOf(
        NewsArticle("Apple unveils new chips for its laptops", "https://news.example.com/apple-chips", "AAPL", "Reuters", "2026-10-07T18:00:00Z", id = "n1", description = "Apple introduced new processors. The company said shipments start next month.", category = NewsCategory.PRODUCTS),
        NewsArticle("Apple unveils new chips for laptops", "https://other.example.com/apple", "AAPL", "Other", "2026-10-07T18:30:00Z", id = "n2", category = NewsCategory.PRODUCTS),
        NewsArticle("Bank reports quarterly earnings above estimates", "https://news.example.com/bank", "JPM", "Bloomberg", "2026-10-07T15:00:00Z", id = "n3", description = "The bank's results beat analyst expectations.", category = NewsCategory.EARNINGS),
        NewsArticle("Regulators review chip export rules", "https://news.example.com/rules", null, "AP", "2026-10-07T12:00:00Z", id = "n4", category = NewsCategory.REGULATION),
        NewsArticle("Broken link story", "ftp://bad", null, "X", "2026-10-07T19:00:00Z", id = "n5"),
        NewsArticle("Apple supplier news", "https://news.example.com/apple-2", "AAPL", "Reuters", "2026-10-07T19:30:00Z", id = "n6", category = NewsCategory.BUSINESS)
    )
    override suspend fun indices(): List<IndexQuote> { calls.incrementAndGet(); return indices ?: throw IllegalStateException("down") }
    override suspend fun news(): List<NewsArticle> { calls.incrementAndGet(); return news ?: throw IllegalStateException("down") }
}

class DailyBriefServiceTest {
    private val store = InMemoryUserDataStore()
    private val market = MovableClock(Instant.parse("2026-10-07T21:15:00Z"))
    private val clock = MovableClock(Instant.parse("2026-10-07T21:15:00Z"))
    private val source = FakeBriefSource()
    private val entitlements = EntitlementService(store, clock::millis, debugAllowed = true)
    private val service = DailyBriefService(source, store, entitlements, null, null, TemplateBriefAi, market, clock, sampleData = true, aiDailyLimit = 2)
    private fun code(block: suspend () -> Unit): String = runBlocking { assertFailsWith<BriefRequestException> { block() }.code }
    private fun plus(uid: String) = runBlocking { store.setEntitlement(uid, StoredEntitlement(SubscriptionTier.PLUS, null, "subscription")) }

    @Test fun afterCloseBriefStatesTheCloseWithoutCauses(): Unit = runBlocking {
        val brief = service.latest()
        assertEquals("2026-10-07-after-close", brief.id)
        assertEquals(BriefEdition.AFTER_CLOSE, brief.edition)
        assertEquals(listOf("SP500", "NASDAQ_COMPOSITE", "TSX"), brief.marketSnapshot.map { it.indexId })
        assertTrue(brief.marketSnapshot.all { it.state == QuoteState.SESSION_CLOSE && it.stateLabel == "Close · Oct 7" })
        assertEquals("At the close on Oct 7, S&P 500 rose 0.60% and Nasdaq Composite fell 0.20%. In Canada, S&P/TSX Composite was about unchanged.", brief.summaryLine)
        listOf("because", "panic", "due to", "soared", "plunged").forEach { assertFalse(it in brief.summaryLine.lowercase(), it) }
        assertTrue(brief.readingMinutes >= 1)
        assertEquals(brief.generatedAt, brief.updatedAt)
    }

    @Test fun storiesAreValidDedupedAndDiverse(): Unit = runBlocking {
        val stories = service.latest().stories
        assertEquals(3, stories.size)
        assertTrue(stories.none { it.id == "n5" }, "invalid link removed")
        assertEquals(1, stories.count { "AAPL" in it.relatedSymbols }, "one story per company")
        assertFalse(stories.any { it.id == "n2" } && stories.any { it.id == "n1" }, "near-duplicate headline removed")
        assertTrue(stories.all { it.sourceUrl.startsWith("https://") && it.evidenceNote.isNotBlank() && it.sourceIds == listOf(it.id) })
        assertEquals(stories.map { it.id }, service.latest().sources.map { it.id })
    }

    @Test fun preMarketShowsThePreviousCloseAndMarketHoursNeverCallsItLive(): Unit = runBlocking {
        market.instant = Instant.parse("2026-10-08T12:00:00Z")
        val pre = service.latest()
        assertEquals(BriefEdition.PRE_MARKET, pre.edition)
        assertTrue(pre.summaryLine.startsWith("US markets haven't opened yet. In the last session (Oct 7)"))
        market.instant = Instant.parse("2026-10-08T15:00:00Z")
        val open = service.latest()
        assertTrue(open.marketSnapshot.all { it.state == QuoteState.PREVIOUS_CLOSE })
        assertEquals("US markets are open; today's index values aren't available yet. Canadian markets are open.", open.summaryLine)
        source.indices = source.indices!!.map { it.copy(asOf = "2026-10-08T14:50:00Z") }
        market.instant = Instant.parse("2026-10-08T15:20:00Z")
        val refreshed = service.latest()
        assertEquals(open.id, refreshed.id)
        assertEquals(open.generatedAt, refreshed.generatedAt, "the original generation time is kept")
        assertNotEquals(open.updatedAt, refreshed.updatedAt, "updatedAt moves only because the data was refreshed")
        assertEquals(QuoteState.LIVE_DELAYED, refreshed.marketSnapshot.first().state)
        assertTrue(refreshed.summaryLine.startsWith("US markets are open. So far today (delayed)"))
    }

    @Test fun staleAndMissingIndicesAreLabelledAndOthersKept(): Unit = runBlocking {
        source.indices = source.indices!!.filterNot { it.id == "NASDAQ_COMPOSITE" }.map { if (it.id == "TSX") it.copy(asOf = "2026-10-02T20:00:00Z") else it }
        val brief = service.latest()
        assertEquals(listOf("SP500", "TSX"), brief.marketSnapshot.map { it.indexId })
        assertEquals(QuoteState.STALE, brief.marketSnapshot[1].state)
        assertEquals("From an earlier session (Oct 2)", brief.marketSnapshot[1].stateLabel)
        assertFalse("TSX" in brief.summaryLine, "a stale value isn't described as the session's move")
        assertEquals(BriefStatus.PARTIAL, brief.status)
    }

    @Test fun providerFailuresGivePartialBriefs(): Unit = runBlocking {
        source.news = null
        val brief = service.latest()
        assertTrue(brief.stories.isEmpty())
        assertTrue(brief.notes.any { "couldn't be loaded" in it })
        assertEquals(BriefStatus.PARTIAL, brief.status)
    }

    @Test fun theGlobalBriefIsBuiltOnceAndShared(): Unit = runBlocking {
        repeat(5) { service.latest() }
        assertEquals(2, source.calls.get())
        assertNotNull(store.brief(service.latest().id), "persisted for history")
    }

    @Test fun historyIsThreeForFreeAndMoreForPlus(): Unit = runBlocking {
        listOf("2026-10-05T21:00:00Z", "2026-10-06T21:00:00Z", "2026-10-07T12:00:00Z", "2026-10-07T21:15:00Z", "2026-10-08T12:00:00Z").forEach { market.instant = Instant.parse(it); service.latest() }
        val free = service.history(BriefAccess.FREE)
        assertEquals(3, free.items.size)
        assertEquals(2, free.lockedCount)
        assertEquals(5, service.history(BriefAccess.PLUS).items.size)
        assertEquals("HISTORY_LOCKED", code { service.byId("2026-10-05-after-close", BriefAccess.FREE) })
        assertEquals("2026-10-05-after-close", service.byId("2026-10-05-after-close", BriefAccess.PLUS).id)
        assertEquals("INVALID_ID", code { service.byId("../etc", BriefAccess.PLUS) })
    }

    @Test fun briefAiAllowanceIsSharedAcrossInstancesAndDeniedRequestsNeverReachTheProvider(): Unit = runBlocking {
        // Two Cloud Run instances = two services over one store (Firestore in REAL).
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val counting = BriefAiProvider { c -> calls.incrementAndGet(); TemplateBriefAi.answer(c) }
        val a = DailyBriefService(source, store, entitlements, null, null, counting, market, clock, sampleData = true, aiDailyLimit = 2)
        val b = DailyBriefService(source, store, entitlements, null, null, counting, market, clock, sampleData = true, aiDailyLimit = 2)
        val id = a.latest().id
        val story = a.latest().stories.first().id
        plus("multi")
        a.ai("multi", id, BriefAiRequest(storyId = story))
        b.ai("multi", id, BriefAiRequest(storyId = story))
        assertEquals("AI_LIMIT", code { a.ai("multi", id, BriefAiRequest(storyId = story)) })
        assertEquals("AI_LIMIT", code { b.ai("multi", id, BriefAiRequest(storyId = story)) }, "another instance doesn't grant more")
        assertEquals(2, calls.get(), "no provider call after a denial")
        // A retry with the same idempotency key isn't charged twice.
        plus("retry")
        a.ai("retry", id, BriefAiRequest(storyId = story, idempotencyKey = "retry-key-0001"))
        b.ai("retry", id, BriefAiRequest(storyId = story, idempotencyKey = "retry-key-0001"))
        assertEquals(0, a.ai("retry", id, BriefAiRequest(storyId = story)).remainingToday, "the retried request counted once; a new one used the last unit")
    }

    @Test fun aiIsPlusOnlyGroundedAndQuotaLimited(): Unit = runBlocking {
        val id = service.latest().id
        val story = service.latest().stories.first().id
        assertEquals("PLUS_REQUIRED", code { service.ai("alice", id, BriefAiRequest(storyId = story)) })
        plus("alice")
        val answer = service.ai("alice", id, BriefAiRequest(storyId = story))
        assertFalse(answer.usesAi, "MOCK answers aren't labelled as AI")
        assertEquals(listOf(story), answer.sources.map { it.id })
        assertTrue(service.ai("alice", id, BriefAiRequest(question = "Why did stocks fall?")).insufficientEvidence)
        assertEquals("AI_LIMIT", code { service.ai("alice", id, BriefAiRequest(question = "What is an index?")) })
        assertEquals("INVALID_QUESTION", code { service.ai("alice", id, BriefAiRequest(question = "?")) })
        assertEquals("STORY_NOT_FOUND", code { service.ai("alice", id, BriefAiRequest(storyId = "nope")) })
        plus("bob")
        assertEquals("AI_UNAVAILABLE", code { service.ai("bob", id, BriefAiRequest(storyId = story), "ai-unavailable") })
        assertEquals("AI_LIMIT", code { service.ai("bob", id, BriefAiRequest(storyId = story), "ai-quota") })
        store.setEntitlement("bob", StoredEntitlement(SubscriptionTier.PLUS, clock.millis() - 1, "subscription"))
        assertEquals("PLUS_REQUIRED", code { service.ai("bob", id, BriefAiRequest(storyId = story)) }, "an expired plan loses AI")
    }

    @Test fun validatorRejectsInventedNumbersLinksSourcesAndAdvice() {
        val brief = runBlocking { service.latest() }
        val ctx = BriefAiContext(brief.summaryLine, brief.marketSnapshot, brief.stories, null)
        val ok = BriefAiDraft("The S&P 500 rose 0.6 in this session, according to the supplied data.", listOf("Apple introduced new processors."), listOf(brief.stories[0].id))
        assertNotNull(BriefAiValidator.validate(ok, ctx))
        assertNull(BriefAiValidator.validate(ok.copy(answer = "The S&P 500 rose 4.7% this session, a big day."), ctx), "invented number")
        assertNull(BriefAiValidator.validate(ok.copy(points = listOf("Read more at https://x.example")), ctx), "link")
        assertNull(BriefAiValidator.validate(ok.copy(sourceIds = listOf("made-up")), ctx), "unknown source")
        assertNull(BriefAiValidator.validate(ok.copy(answer = "You should buy this stock before it goes higher."), ctx), "advice")
        assertNull(BriefAiValidator.validate(ok.copy(answer = "Technology stocks fell because investors panicked today."), ctx), "invented cause")
        assertNull(BriefAiValidator.validate(ok.copy(answer = "Shares will rise next week as demand grows."), ctx), "prediction")
    }

    @Test fun preferencesAreValidatedAndPersonalNotificationsNeedPlus(): Unit = runBlocking {
        assertFalse(service.preferences("alice").notificationsEnabled, "opt-in only")
        assertEquals("INVALID_PREFERENCES", code { service.savePreferences("alice", BriefPreferences(true, deliveryHour = 25)) })
        assertEquals("INVALID_PREFERENCES", code { service.savePreferences("alice", BriefPreferences(true, timeZone = "Mars/Base")) })
        assertFalse(service.savePreferences("alice", BriefPreferences(true, personalizedNotifications = true)).personalizedNotifications)
        plus("alice")
        assertTrue(service.savePreferences("alice", BriefPreferences(true, personalizedNotifications = true)).personalizedNotifications)
    }

    @Test fun notificationsGoOutOncePerBriefAtTheLocalHourAndRespectQuietHours(): Unit = runBlocking {
        val sender = SimulatedPushSender()
        store.registerDevice(DeviceRecord("alice", "d1", "token-a", "android", 0))
        store.registerDevice(DeviceRecord("bob", "d2", "token-b", "ios", 0))
        store.registerDevice(DeviceRecord("carol", "d3", "token-c", "ios", 0))
        service.savePreferences("alice", BriefPreferences(true, deliveryHour = 17, timeZone = "America/Toronto"))        // 21:15Z = 17:15 EDT
        service.savePreferences("bob", BriefPreferences(true, deliveryHour = 17, timeZone = "America/Toronto", quietStartHour = 16, quietEndHour = 18))
        service.savePreferences("carol", BriefPreferences(false, deliveryHour = 17, timeZone = "America/Toronto"))
        val first = service.dispatch(sender)
        assertEquals(1, first.sent)
        assertEquals("token-a", sender.sent.single().token)
        assertEquals(mapOf("type" to "daily-brief", "briefId" to "2026-10-07-after-close"), sender.sent.single().data)
        assertEquals("Your StockSteps Market Brief is ready.", sender.sent.single().title)
        assertEquals(0, service.dispatch(sender).sent, "never twice for the same brief")
        market.instant = Instant.parse("2026-10-10T21:15:00Z"); clock.instant = market.instant
        assertEquals(0, service.dispatch(sender).sent, "no notifications when both markets are closed")
    }

    @Test fun personalizedOverlaysAreIsolatedPerUser(): Unit = runBlocking {
        val fixtures = FixtureMarketDataSource(sampleFallback = true)
        val watch = WatchMarketData(StockService(fixtures, fixtures), fixtures, NewsService(fixtures))
        val live = DailyBriefService(source, store, entitlements, watch, null, TemplateBriefAi, market, clock, sampleData = true)
        val lists = WatchlistsService(store)
        lists.get("alice")
        val list = lists.get("alice").watchlists.first().id
        listOf("AAPL", "MSFT", "NVDA", "TSLA", "AMZN").forEach { lists.add("alice", list, InstrumentRef(it)) }
        val id = live.latest().id
        val alice = live.personalized("alice", id)
        assertEquals(5, alice.watchlistCount)
        assertEquals(BriefAccess.FREE, alice.access)
        assertTrue(alice.watchlistHighlights.size <= BriefPolicy.FREE_HIGHLIGHTS)
        assertTrue(alice.watchlistHighlights.all { it.symbol in setOf("AAPL", "MSFT", "NVDA", "TSLA", "AMZN") })
        assertNull(alice.premiumInsights)
        assertTrue(alice.companyStories.isEmpty())
        val bob = live.personalized("bob", id)
        assertEquals(0, bob.watchlistCount)
        assertTrue(bob.watchlistHighlights.isEmpty(), "bob never sees alice's companies")
        assertTrue(bob.notes.any { "Add companies to a watchlist" in it })
        plus("alice")
        val plusView = live.personalized("alice", id)
        assertEquals(BriefAccess.PLUS, plusView.access, "a plan change is a new cache key")
        assertNotNull(plusView.premiumInsights)
        assertTrue(plusView.aiAvailable)
    }
}

class DailyBriefRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun publicContentIsAnonymousAndPersonalDataNeedsSignIn() = testApplication {
        val store = InMemoryUserDataStore()
        val clock = MovableClock(Instant.parse("2026-10-07T21:15:00Z"))
        val service = DailyBriefService(FakeBriefSource(), store, EntitlementService(store, clock::millis, true), null, null, TemplateBriefAi, clock, clock, sampleData = false)
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing { dailyBriefRoutes(service, MockUserAuthenticator(), RequestRateLimiter(1_000), SimulatedPushSender(), null, mock = false) }
        }
        val latest = client.get("/api/v1/daily-brief/latest")
        assertEquals(HttpStatusCode.OK, latest.status)
        val brief = json.decodeFromString(DailyBrief.serializer(), latest.bodyAsText())
        assertFalse(latest.bodyAsText().contains("watchlist", ignoreCase = true) && latest.bodyAsText().contains("alice"))
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/daily-brief/${brief.id}/sources").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/daily-brief/${brief.id}/personalized").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/me/daily-brief/${brief.id}/ai/ask").status)
        val forbidden = client.post("/api/v1/me/daily-brief/${brief.id}/ai/ask") {
            header(HttpHeaders.Authorization, "Bearer mock-user:alice"); contentType(ContentType.Application.Json); setBody("""{"question":"What happened today?"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/daily-brief/latest?scenario=weekend").status, "MOCK scenarios don't exist in REAL")
        assertEquals(HttpStatusCode.NotFound, client.post("/internal/daily-brief/dispatch").status, "no scheduler route without a token in REAL")
    }

    @Test fun rateLimited() = testApplication {
        val store = InMemoryUserDataStore()
        val clock = MovableClock(Instant.parse("2026-10-07T21:15:00Z"))
        val service = DailyBriefService(FakeBriefSource(), store, EntitlementService(store, clock::millis, true), null, null, null, clock, clock, sampleData = false)
        application {
            configureApiErrors()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            routing { dailyBriefRoutes(service, MockUserAuthenticator(), RequestRateLimiter(2), SimulatedPushSender(), null, mock = false) }
        }
        repeat(2) { client.get("/api/v1/daily-brief/latest") }
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/v1/daily-brief/latest").status)
    }
}

/** Every MOCK scenario over the real fixtures (no providers, Firebase, AI or push services). */
class DailyBriefFixtureScenariosTest {
    private val fixtures = FixtureMarketDataSource(sampleFallback = true)
    private val capture = Clock.fixed(fixtures.capturedAt!!, ZoneOffset.UTC)
    private val markets = MarketsService(fixtures, fixtures, fixtures, NewsService(fixtures), MarketsSourceLabels("Sample", "Sample", true, "Sample"), clock = capture)
    private val store = InMemoryUserDataStore()
    private val service = DailyBriefService(MarketsBriefSource(markets), store, EntitlementService(store, capture::millis, true), null, null, TemplateBriefAi, capture, capture, sampleData = true)

    @Test fun everyScenarioBuildsAndIsLabelled(): Unit = runBlocking {
        BriefScenario.all.keys.forEach { name ->
            val brief = service.latest(name)
            assertTrue(brief.sampleData, name)
            assertTrue(brief.id.endsWith("-mock-$name"), name)
            assertTrue(brief.summaryLine.isNotBlank(), name)
            assertTrue(brief.stories.size <= 3, name)
            assertTrue(brief.stories.all { it.sourceUrl.startsWith("https://") }, name)
        }
    }

    @Test fun scenarioSpecificStates(): Unit = runBlocking {
        assertEquals(BriefEdition.AFTER_CLOSE, service.latest("normal").edition)
        assertTrue(service.latest("normal").marketSnapshot.isNotEmpty())
        assertEquals(BriefEdition.WEEKEND, service.latest("weekend").edition)
        assertTrue(service.latest("weekend").summaryLine.startsWith("Markets are closed for the weekend."))
        assertEquals(BriefEdition.HOLIDAY, service.latest("holiday").edition)
        assertTrue(service.latest("ca-open-us-closed").summaryLine.contains("closed for Thanksgiving Day"))
        assertTrue(service.latest("us-open-ca-closed").summaryLine.contains("Canadian markets are closed for Thanksgiving Day"))
        assertEquals(1, service.latest("one-story").stories.size)
        assertTrue(service.latest("no-stories").stories.isEmpty())
        assertTrue(service.latest("missing-index").marketSnapshot.none { it.indexId == "NASDAQ_COMPOSITE" })
        assertEquals(QuoteState.STALE, service.latest("stale-index").marketSnapshot.first { it.indexId == "TSX" }.state)
        assertNull(service.latest("missing-publisher").stories.firstOrNull { it.publisher == null }?.publisher)
        val dupes = service.latest("duplicate-stories").stories
        assertEquals(dupes.size, dupes.map { StoryRanker.words(it.headline) }.distinct().size)
        assertTrue(service.latest("invalid-url").stories.none { !it.sourceUrl.startsWith("https://") })
    }
}
