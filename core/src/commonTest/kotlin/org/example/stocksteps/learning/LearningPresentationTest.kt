package org.example.stocksteps.learning

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.userdata.InMemoryUserDataCache
import org.example.stocksteps.data.userdata.userCacheOwner
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.domain.CompanyDetailsRepository
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApiException
import kotlin.test.*

private class Auth(initial: String? = null) : AuthRepository {
    val mutable = MutableStateFlow(AuthSession(initial?.let { User(it, null) }, initializing = false))
    override val session: StateFlow<AuthSession> = mutable
    override val currentUser = mutable.map { it.user }
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String) = Unit
    override suspend fun signOut() { mutable.value = AuthSession(initializing = false) }
    fun switch(uid: String?) { mutable.value = AuthSession(uid?.let { User(it, null) }, initializing = false) }
}

/** The account copy, per user, with a switch to simulate the server being unreachable. */
private class FakeSync : LearningSync {
    val stored = HashMap<String, LearningProgressDocument>()
    var online = true
    var saves = 0
    override suspend fun load(uid: String): LearningProgressDocument {
        if (!online) throw IllegalStateException("offline")
        return stored[uid] ?: LearningProgressDocument()
    }
    override suspend fun save(uid: String, document: LearningProgressDocument): LearningProgressDocument {
        if (!online) throw IllegalStateException("offline")
        saves++
        return (stored[uid] ?: LearningProgressDocument()).merge(document).also { stored[uid] = it }
    }
}

class LearningProgressRepositoryTest {
    private var clock = 1_000L
    private fun repo(auth: AuthRepository, sync: LearningSync?, cache: InMemoryUserDataCache, env: MutableStateFlow<String>, scope: CoroutineScope) =
        LearningProgressRepository(auth, sync, cache, env, scope, now = { clock++ }).also { it.start() }

    private suspend fun LearningProgressRepository.await(predicate: (LearningProgressRepository.State) -> Boolean) =
        withTimeout(5_000) { state.first(predicate) }

    @Test fun guestProgressIsSavedLocallyAndSurvivesARestart(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = InMemoryUserDataCache()
        val env = MutableStateFlow("mock")
        try {
            val first = repo(Auth(), FakeSync(), cache, env, scope)
            first.await { it.loaded }
            first.record("aapl", "Apple") { it.copy(completedSteps = listOf(1, 2), currentStep = 3) }
            first.record("KO", "Coca-Cola") { it.copy(completedSteps = listOf(1)) }
            assertEquals(listOf("KO", "AAPL"), first.state.value.inProgress.map { it.symbol })
            // A new repository over the same device storage (app restart).
            val second = repo(Auth(), FakeSync(), cache, env, scope)
            val restored = second.await { it.loaded && it.document.journeys.size == 2 }
            assertEquals(listOf(1, 2), restored.journey("AAPL")!!.completedSteps)
            assertEquals(3, restored.journey("aapl")!!.resumeStep)
            assertNull(restored.uid)
        } finally { scope.cancel() }
    }

    @Test fun signedInProgressSyncsAndAnotherDeviceSeesIt(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sync = FakeSync()
        val env = MutableStateFlow("mock")
        try {
            val phone = repo(Auth("alice"), sync, InMemoryUserDataCache(), env, scope)
            phone.await { it.loaded && it.uid == "alice" && !it.syncing }
            phone.record("AAPL", "Apple") { it.copy(completedSteps = listOf(1, 2, 3)) }
            withTimeout(5_000) { while (sync.stored["alice"]?.journeys?.firstOrNull()?.completedCount != 3) delay(10) }
            val tablet = repo(Auth("alice"), sync, InMemoryUserDataCache(), env, scope)
            val state = tablet.await { it.journey("AAPL") != null }
            assertEquals(3, state.journey("AAPL")!!.completedCount)
        } finally { scope.cancel() }
    }

    @Test fun guestProgressIsNeverMergedIntoAnAccountAndAccountsNeverMix(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val auth = Auth()
        val sync = FakeSync()
        val cache = InMemoryUserDataCache()
        try {
            val repository = repo(auth, sync, cache, MutableStateFlow("mock"), scope)
            repository.await { it.loaded }
            repository.record("TSLA", "Tesla") { it.copy(completedSteps = listOf(1)) }
            auth.switch("alice")
            repository.await { it.uid == "alice" && !it.syncing }
            assertNull(repository.state.value.journey("TSLA"), "guest progress stays on the guest space")
            repository.record("AAPL", "Apple") { it.copy(completedSteps = listOf(1, 2)) }
            withTimeout(5_000) { while (sync.stored["alice"] == null) delay(10) }
            auth.switch("bob")
            val bob = repository.await { it.uid == "bob" && !it.syncing }
            assertTrue(bob.document.journeys.isEmpty(), "bob never sees alice's research")
            assertNull(sync.stored["alice"]!!.journeys.firstOrNull { it.symbol == "TSLA" })
            auth.switch(null)
            val guest = repository.await { it.uid == null && it.loaded }
            assertNotNull(guest.journey("TSLA"))
            assertNull(guest.journey("AAPL"), "signing out never shows the account's progress")
            // Sign-out clears account copies on the device; the guest copy stays.
            cache.clearAccount("alice")
            assertNull(cache.read(userCacheOwner("mock", "alice"), LearningProgressRepository.KEY))
            assertNotNull(cache.read("mock|guest", LearningProgressRepository.KEY))
        } finally { scope.cancel() }
    }

    @Test fun remoteFailureKeepsLocalProgressAndRetriesOnTheNextChange(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sync = FakeSync().apply { online = false }
        try {
            val repository = repo(Auth("alice"), sync, InMemoryUserDataCache(), MutableStateFlow("mock"), scope)
            repository.await { it.loaded && it.syncFailed }
            repository.record("AAPL", "Apple") { it.copy(completedSteps = listOf(1)) }
            repository.await { it.syncFailed && !it.syncing && it.journey("AAPL") != null }
            sync.online = true
            repository.record("AAPL", "Apple") { it.copy(completedSteps = listOf(1, 2)) }
            repository.await { !it.syncFailed && !it.syncing }
            assertEquals(listOf(1, 2), sync.stored["alice"]!!.journeys.single().completedSteps)
        } finally { scope.cancel() }
    }

    @Test fun mockAndRealProgressAreSeparate(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val env = MutableStateFlow("mock")
        try {
            val repository = repo(Auth(), null, InMemoryUserDataCache(), env, scope)
            repository.await { it.loaded }
            repository.record("AAPL", "Apple") { it }
            env.value = "real"
            assertTrue(repository.await { it.owner == "real|guest" }.document.journeys.isEmpty())
        } finally { scope.cancel() }
    }
}

class GuidedResearchPresenterTest {
    private class Details(private val details: CompanyDetails) : CompanyDetailsRepository {
        var calls = 0
        var fail = false
        override suspend fun getDetails(symbol: String): CompanyDetails {
            calls++
            if (fail) throw org.example.stocksteps.domain.StockDataException("Could not reach StockSteps.")
            return details.copy(symbol = symbol)
        }
        override suspend fun getChart(symbol: String, range: ChartRange): PriceChart = error("unused")
        override suspend fun getWhyMoving(symbol: String): WhyMoving? = null
    }

    private class Harness(signedIn: String? = "alice", plus: Boolean = false, val asker: ResearchAsk? = ResearchAsk { _, q -> ResearchAnswer("Answer to ${q.question}") }) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val auth = Auth(signedIn)
        val details = Details(company(symbol = "AAPL", name = "Apple Inc."))
        val plusFlow = MutableStateFlow<Boolean?>(plus)
        val progress = LearningProgressRepository(auth, null, InMemoryUserDataCache(), MutableStateFlow("mock"), scope).also { it.start() }
        val presenter = GuidedResearchPresenter(GuidedResearchRepository(details), progress, plusFlow, asker, scope) { "2026-10-08" }
        suspend fun await(predicate: (GuidedResearchState) -> Boolean) = withTimeout(5_000) { presenter.state.first(predicate) }
        suspend fun awaitProgress(predicate: (ResearchProgress?) -> Boolean) = withTimeout(5_000) { progress.state.first { predicate(it.journey("AAPL")) } }
    }

    @Test fun startContinueCompleteAndSummary(): Unit = runBlocking {
        val h = Harness()
        try {
            h.progress.state.first { it.loaded }
            h.presenter.load("aapl", "Apple")
            val loaded = h.await { it.snapshot != null }
            assertEquals("Start learning", loaded.primaryLabel)
            assertEquals(0, loaded.screen)
            h.presenter.startOrContinue()
            assertEquals(1, h.await { it.screen == 1 }.step!!.step.number)
            h.presenter.next()
            h.awaitProgress { it?.completedSteps == listOf(1) }
            assertEquals(2, h.presenter.state.value.screen)
            h.presenter.back()
            assertEquals(1, h.presenter.state.value.screen)
            // No step is locked: jump straight to step 5 and finish it.
            h.presenter.open(5)
            h.presenter.next()
            h.awaitProgress { it?.completedSteps == listOf(1, 5) }
            assertEquals(GuidedResearchState.SUMMARY, h.presenter.state.value.screen)
            h.presenter.open(0)
            val overview = h.await { it.completedCount == 2 && it.screen == 0 }
            assertEquals("Continue learning", overview.primaryLabel)
            assertEquals("2 of 5 steps completed", overview.progressLabel)
            h.presenter.startOrContinue()
            assertEquals(2, h.await { it.screen == 2 }.screen, "resumes at the first unfinished step")
            listOf(2, 3, 4).forEach { _ -> h.presenter.next() }
            h.awaitProgress { it?.finished == true }
            h.presenter.open(0)
            assertEquals("Review research", h.await { it.progress?.finished == true }.primaryLabel)
        } finally { h.scope.cancel() }
    }

    @Test fun companyDataLoadsOnceForAllSteps(): Unit = runBlocking {
        val h = Harness()
        try {
            h.presenter.load("AAPL")
            h.await { it.snapshot != null }
            (1..5).forEach { h.presenter.open(it) }
            h.presenter.load("AAPL")
            h.await { it.snapshot != null }
            assertEquals(1, h.details.calls)
        } finally { h.scope.cancel() }
    }

    @Test fun loadFailureShowsARetryableError(): Unit = runBlocking {
        val h = Harness()
        try {
            h.details.fail = true
            h.presenter.load("AAPL")
            assertEquals("Could not reach StockSteps.", h.await { it.error != null }.error)
            h.details.fail = false
            h.presenter.retry()
            assertNotNull(h.await { it.snapshot != null }.snapshot)
        } finally { h.scope.cancel() }
    }

    @Test fun quizAnswerRetryAndSavedRecord(): Unit = runBlocking {
        val h = Harness()
        try {
            h.progress.state.first { it.loaded }
            h.presenter.load("AAPL", startAt = 2)
            h.await { it.snapshot != null && it.screen == 2 }
            h.presenter.answer("b")
            val wrong = h.await { it.answers.isNotEmpty() }
            assertFalse(wrong.answers.values.single().correct)
            h.awaitProgress { it?.quizzes?.get("q-growth-1")?.correct == false }
            h.presenter.retryQuiz()
            assertTrue(h.await { it.answers.isEmpty() }.answers.isEmpty())
            h.presenter.answer("a")
            h.awaitProgress { it?.quizzes?.get("q-growth-1")?.correct == true }
            assertTrue(h.presenter.state.value.answers.values.single().correct)
            assertEquals(0, h.presenter.state.value.completedCount, "a quiz is optional and doesn't complete the step")
        } finally { h.scope.cancel() }
    }

    @Test fun aiIsGatedForSignedOutAndFreeUsersWithoutSendingAnything(): Unit = runBlocking {
        var sent = 0
        val counting = ResearchAsk { _, _ -> sent++; ResearchAnswer("x") }
        val guest = Harness(signedIn = null, asker = counting)
        val free = Harness(plus = false, asker = counting)
        try {
            guest.presenter.load("AAPL"); guest.await { it.snapshot != null }
            guest.presenter.ask("What does this mean?")
            assertTrue(guest.await { it.message != null }.message!!.contains("Sign in"))
            free.presenter.load("AAPL"); free.await { it.snapshot != null && it.signedIn }
            free.presenter.ask("What does this mean?")
            assertTrue(free.await { it.upgradeRequired }.upgradeRequired)
            assertEquals(0, sent)
        } finally { guest.scope.cancel(); free.scope.cancel() }
    }

    @Test fun plusUsersGetAnAnswerAndServerRefusalShowsTheUpgrade(): Unit = runBlocking {
        val h = Harness(plus = true)
        val refused = Harness(plus = true, asker = { _, _ -> throw StockStepsApiException(403, ApiError("PLUS_REQUIRED", "StockSteps+ only")) })
        try {
            h.presenter.load("AAPL", startAt = 3); h.await { it.snapshot != null && it.plus }
            h.presenter.ask("  Is this profit good?  ")
            assertEquals("Answer to Is this profit good?", h.await { it.answer != null }.answer!!.answer)
            refused.presenter.load("AAPL"); refused.await { it.snapshot != null && it.plus }
            refused.presenter.ask("Why?")
            assertTrue(refused.await { it.upgradeRequired }.upgradeRequired, "an expired plan is enforced by the server")
        } finally { h.scope.cancel(); refused.scope.cancel() }
    }

    @Test fun restartClearsProgressForThatCompanyOnly(): Unit = runBlocking {
        val h = Harness()
        try {
            h.progress.state.first { it.loaded }
            h.progress.record("KO", "Coca-Cola") { it.copy(completedSteps = listOf(1)) }
            h.presenter.load("AAPL", startAt = 1)
            h.await { it.snapshot != null }
            h.presenter.next()
            h.awaitProgress { it?.completedCount == 1 }
            h.presenter.restart()
            h.awaitProgress { it?.completedCount == 0 }
            assertEquals(1, h.progress.state.value.journey("KO")!!.completedCount)
        } finally { h.scope.cancel() }
    }
}
