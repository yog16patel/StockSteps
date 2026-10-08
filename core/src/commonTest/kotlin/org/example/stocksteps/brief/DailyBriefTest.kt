package org.example.stocksteps.brief

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.userdata.InMemoryUserDataCache
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApiException
import kotlin.test.*

private fun article(id: String, title: String, symbol: String? = null, at: String = "2026-10-07T18:00:00Z", url: String = "https://news.example.com/$id",
                    source: String? = "Reuters", category: NewsCategory? = null, description: String? = "A summary sentence long enough to keep.") =
    NewsArticle(title, url, symbol, source, at, id = id, description = description, category = category)

private val parse: (String) -> Long? = { runCatching { kotlin.time.Instant.parse(it).epochSeconds }.getOrNull() }
private val NOW = kotlin.time.Instant.parse("2026-10-07T21:00:00Z").epochSeconds

class StoryRankerTest {
    @Test fun invalidRecordsAreDropped() {
        assertTrue(StoryRanker.valid(article("a", "Headline")))
        assertFalse(StoryRanker.valid(article("b", "Headline", url = "http://insecure.example.com/x")))
        assertFalse(StoryRanker.valid(article("c", "Headline", url = "not-a-url")))
        assertFalse(StoryRanker.valid(article("d", "  ")))
        assertFalse(StoryRanker.valid(article("e", "Headline", url = "https://localhost")))
    }

    @Test fun nearDuplicatesAreRemoved() {
        val list = StoryRanker.dedupe(listOf(article("a", "Apple unveils new chips for laptops"), article("b", "Apple unveils new laptop chips"),
            article("c", "Regulators review export rules"), article("d", "Other", url = "https://news.example.com/a")))
        assertEquals(listOf("a", "c"), list.map { it.id })
    }

    @Test fun rankingPrefersRecentDiverseAndWatchedStories() {
        val stories = listOf(
            article("old", "Old news about interest rates", at = "2026-10-04T10:00:00Z"),
            article("a1", "Apple product launch", "AAPL", category = NewsCategory.PRODUCTS),
            article("a2", "Apple supplier deal", "AAPL", "2026-10-07T19:00:00Z", category = NewsCategory.BUSINESS),
            article("k", "Bank earnings beat", "JPM", category = NewsCategory.EARNINGS),
            article("m", "Microsoft product event", "MSFT", category = NewsCategory.PRODUCTS),
            article("x", "No publisher stock story", source = null)
        )
        val picked = StoryRanker.rank(stories, NOW, parse)
        assertEquals(3, picked.size)
        assertEquals(1, picked.count { it.symbol == "AAPL" }, "one per company")
        assertTrue(picked.none { it.id == "old" })
        assertEquals(picked.map { it.category }.distinct().size, picked.size, "topics differ when possible")
        assertEquals("m", StoryRanker.rank(stories, NOW, parse, watch = setOf("MSFT"), limit = 1).single().id)
        assertEquals(picked, StoryRanker.rank(stories.reversed(), NOW, parse), "deterministic")
        assertEquals(1, StoryRanker.rank(listOf(article("only", "One market story")), NOW, parse).size)
        assertTrue(StoryRanker.rank(emptyList(), NOW, parse).isEmpty())
    }

    @Test fun marketStoriesOutrankGeneralNews() {
        val stories = listOf(article("pol", "Candidates debate ahead of the election", at = "2026-10-07T20:50:00Z"),
            article("mkt", "Stocks slip as bond yields rise", at = "2026-10-07T15:00:00Z"))
        assertEquals("mkt", StoryRanker.rank(stories, NOW, parse, limit = 1).single().id)
        assertEquals(listOf("mkt"), StoryRanker.rank(stories, NOW, parse).map { it.id }, "fewer stories rather than off-topic filler")
        assertFalse(StoryRanker.marketRelevant(article("x", "Candidates debate", description = "Voters weigh in.")))
    }

    @Test fun excerptsAreShortAndNeverInvented() {
        assertNull(StoryRanker.excerpt(null))
        assertNull(StoryRanker.excerpt("Too short"))
        assertEquals("First sentence here. Second one too.", StoryRanker.excerpt("First sentence here. Second one too. Third is dropped."))
        assertTrue(StoryRanker.excerpt("word ".repeat(100))!!.length <= 260)
    }
}

class BriefContentTest {
    @Test fun readingTimeFromActualWords() {
        assertEquals(1, BriefContent.readingMinutes(listOf("a few words")))
        assertEquals(2, BriefContent.readingMinutes(listOf("word ".repeat(250))))
        assertEquals(1, BriefContent.readingMinutes(emptyList()))
    }

    @Test fun conceptComesFromTheLearnCatalogue() {
        assertEquals("earningsBeat", BriefContent.concept(10, hasEarningsStory = true, marketsClosed = false).learnTermId)
        assertEquals("diversification", BriefContent.concept(10, hasEarningsStory = false, marketsClosed = true).id)
        val a = BriefContent.concept(100, false, false)
        assertEquals(a, BriefContent.concept(100, false, false), "deterministic per day")
        assertNotNull(org.example.stocksteps.learning.BeginnerEducation.entry(a.learnTermId))
        assertTrue(a.explanation.isNotBlank())
    }

    @Test fun wordingIsNeutral() {
        assertEquals("S&P 500 rose 0.60%", BriefWording.move("S&P 500", 0.6))
        assertEquals("S&P 500 fell 1.25%", BriefWording.move("S&P 500", -1.249))
        assertEquals("S&P 500 was about unchanged", BriefWording.move("S&P 500", 0.001))
        assertNull(BriefWording.move("S&P 500", null))
    }

    @Test fun policyIsCentral() {
        assertEquals(3, BriefPolicy.historyLimit(BriefAccess.FREE))
        assertEquals(3, BriefPolicy.historyLimit(BriefAccess.ANONYMOUS))
        assertTrue(BriefPolicy.historyLimit(BriefAccess.PLUS) > 3)
        assertFalse(BriefPolicy.aiAllowed(BriefAccess.FREE))
        assertTrue(BriefPolicy.aiAllowed(BriefAccess.PLUS))
        assertEquals(0, BriefPolicy.companyStoryLimit(BriefAccess.FREE))
    }

    @Test fun formattingAndFreshness() {
        val idx = BriefIndex("SP500", "S&P 500", "US", 6800.5, -40.25, -0.59, "USD", "points", quoteDate = "2026-10-07", state = QuoteState.SESSION_CLOSE, stateLabel = "Close · Oct 7")
        assertEquals("6,800.50", BriefFormat.value(idx))
        assertEquals("−40.25 (−0.59%)", BriefFormat.change(idx))
        assertEquals("Down", BriefFormat.direction(idx))
        assertEquals("S&P 500: 6,800.50, Down −40.25 (−0.59%). Close · Oct 7.", BriefFormat.accessibility(idx))
        assertEquals("Unavailable", BriefFormat.value(idx.copy(value = null)))
        val brief = sampleBrief("2026-10-07T21:15:00Z")
        val at = kotlin.time.Instant.parse("2026-10-07T21:45:00Z").toEpochMilliseconds()
        assertEquals("After-close brief · Updated 30 min ago", BriefFormat.freshness(brief, at))
        val nextDay = kotlin.time.Instant.parse("2026-10-08T20:00:00Z").toEpochMilliseconds()
        assertEquals("Latest available · Oct 7", BriefFormat.freshness(brief, nextDay), "an old brief is never labelled as today's")
        assertTrue(BriefFormat.isStale(brief, nextDay))
    }
}

internal fun sampleBrief(updated: String, id: String = "2026-10-07-after-close") = DailyBrief(id, "2026-10-07", "2026-10-07", BriefEdition.AFTER_CLOSE, summaryLine = "At the close on Oct 7, S&P 500 rose 0.60%.",
    concept = BriefContent.concept(1, false, false), generatedAt = updated, updatedAt = updated, status = BriefStatus.COMPLETE, readingMinutes = 2,
    stories = listOf(BriefStory("s1", "Headline", null, "Reuters", null, "https://news.example.com/1", topic = "Markets", evidenceNote = StoryRanker.EVIDENCE_NOTE)))

private class Auth(uid: String?) : AuthRepository {
    val mutable = MutableStateFlow(AuthSession(uid?.let { User(it, null) }, initializing = false))
    override val session: StateFlow<AuthSession> = mutable
    override val currentUser = mutable.map { it.user }
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String) = Unit
    override suspend fun signOut() { mutable.value = AuthSession(initializing = false) }
}

private class FakeRemote : BriefRemote {
    var online = true
    val plus = mutableSetOf<String>()
    val aiCalls = mutableListOf<String>()
    private fun check() { if (!online) throw IllegalStateException("offline") }
    override suspend fun latest(scenario: String?): DailyBrief { check(); return sampleBrief("2026-10-07T21:15:00Z") }
    override suspend fun brief(id: String, uid: String?): DailyBrief {
        check()
        if (id == "2026-09-01-after-close" && uid !in plus) throw StockStepsApiException(403, ApiError("HISTORY_LOCKED", "Older briefs are included with StockSteps+."))
        return sampleBrief("2026-10-06T21:15:00Z", id)
    }
    override suspend fun history(uid: String?) = BriefHistory(listOf(sampleBrief("2026-10-07T21:15:00Z").summary()), if (uid in plus) BriefAccess.PLUS else BriefAccess.FREE)
    override suspend fun personalized(uid: String, id: String, scenario: String?): PersonalizedBrief {
        check()
        return PersonalizedBrief(id, if (uid in plus) BriefAccess.PLUS else BriefAccess.FREE, 1,
            listOf(WatchlistHighlight("${uid.uppercase()}CO", null, HighlightKind.NEWS, "News for $uid")), generatedAt = "x")
    }
    override suspend fun explain(uid: String, id: String, storyId: String, scenario: String?): BriefAiAnswer { aiCalls += storyId; return BriefAiAnswer("Explained", usesAi = false) }
    override suspend fun ask(uid: String, id: String, question: String, scenario: String?): BriefAiAnswer { aiCalls += question; return BriefAiAnswer("Answered") }
    override suspend fun preferences(uid: String) = BriefPreferences()
    override suspend fun savePreferences(uid: String, value: BriefPreferences) = value
}

class DailyBriefPresenterTest {
    private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean) = withTimeout(5_000) { first(predicate) }

    @Test fun anonymousUsersReadTheBriefWithoutPersonalData(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val p = DailyBriefPresenter(FakeRemote(), Auth(null), InMemoryUserDataCache(), MutableStateFlow("mock"), scope).also { it.start() }
            val s = p.state.await { it.latest != null }
            assertNull(s.personal)
            assertFalse(s.signedIn)
            p.explain("s1")
            assertTrue(p.state.value.message!!.contains("Sign in"))
        } finally { scope.cancel() }
    }

    @Test fun accountSwitchClearsPersonalDataAndFreeUsersGetTheUpgradeNotAi(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val remote = FakeRemote()
        val auth = Auth("alice")
        try {
            val p = DailyBriefPresenter(remote, auth, InMemoryUserDataCache(), MutableStateFlow("mock"), scope).also { it.start() }
            assertEquals("ALICECO", p.state.await { it.personal != null }.personal!!.watchlistHighlights.single().symbol)
            p.explain("s1")
            assertTrue(p.state.value.upgrade)
            assertTrue(remote.aiCalls.isEmpty(), "nothing is sent for a Free user")
            remote.plus += "bob"
            auth.mutable.value = AuthSession(User("bob", null), initializing = false)
            val bob = p.state.await { it.uid == "bob" && it.personal != null }
            assertEquals("BOBCO", bob.personal!!.watchlistHighlights.single().symbol, "alice's overlay never shows for bob")
            assertNotNull(bob.latest, "public content stays")
            p.explain("s1")
            assertEquals("Explained", p.state.await { it.ai["s1"]?.answer != null }.ai["s1"]!!.answer!!.answer)
            p.ask("What is an index?")
            p.state.await { it.ai["ask"]?.answer != null }
            assertEquals(listOf("s1", "What is an index?"), remote.aiCalls)
        } finally { scope.cancel() }
    }

    @Test fun offlineShowsOnlyBriefsThisDeviceDownloaded(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = InMemoryUserDataCache()
        val remote = FakeRemote()
        try {
            val first = DailyBriefPresenter(remote, Auth(null), cache, MutableStateFlow("mock"), scope).also { it.start() }
            first.state.await { it.latest != null }
            withTimeout(5_000) { while (cache.read("mock|public", DailyBriefPresenter.KEY) == null) delay(10) }
            remote.online = false
            val offline = DailyBriefPresenter(remote, Auth(null), cache, MutableStateFlow("mock"), scope).also { it.start() }
            val s = offline.state.await { it.offline }
            assertEquals("2026-10-07-after-close", s.latest!!.id)
            offline.open("2026-09-15-after-close")
            assertTrue(offline.state.await { it.error != null && !it.loading }.error!!.contains("Previously opened"))
            val empty = DailyBriefPresenter(remote, Auth(null), InMemoryUserDataCache(), MutableStateFlow("mock"), scope).also { it.start() }
            val none = empty.state.await { it.error != null }
            assertNull(none.latest, "no offline copy is promised when none was downloaded")
        } finally { scope.cancel() }
    }

    @Test fun lockedHistoryOffersTheUpgrade(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val p = DailyBriefPresenter(FakeRemote(), Auth("alice"), InMemoryUserDataCache(), MutableStateFlow("mock"), scope).also { it.start() }
            p.state.await { it.latest != null }
            p.open("2026-09-01-after-close")
            assertTrue(p.state.await { it.upgrade }.upgrade)
            p.open("2026-10-06-after-close")
            assertEquals("2026-10-06-after-close", p.state.await { it.current?.id == "2026-10-06-after-close" }.current!!.id)
        } finally { scope.cancel() }
    }
}
