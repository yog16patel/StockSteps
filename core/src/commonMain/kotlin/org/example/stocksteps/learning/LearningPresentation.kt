package org.example.stocksteps.learning

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.example.stocksteps.network.StockStepsApiException
import org.example.stocksteps.domain.StockDataException
import org.example.stocksteps.data.userdata.UserApi
import org.example.stocksteps.data.userdata.UserDataCache
import org.example.stocksteps.data.userdata.userCacheOwner
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.domain.CompanyDetailsRepository
import org.example.stocksteps.model.CompanyDetails
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// ---------- Progress persistence ----------

/** Account copy of learning progress (`/api/v1/me/learning`). */
interface LearningSync {
    suspend fun load(uid: String): LearningProgressDocument
    suspend fun save(uid: String, document: LearningProgressDocument): LearningProgressDocument
}

class RemoteLearningSync(private val api: UserApi) : LearningSync {
    override suspend fun load(uid: String) = api.learning(expectedOwner = uid)
    override suspend fun save(uid: String, document: LearningProgressDocument) = api.saveLearning(document, expectedOwner = uid)
}

/**
 * Research progress, saved on the device first so learning works offline and signed out.
 * Signed in, it also syncs with the account (per company, the most recent visit wins). Progress
 * made while signed out stays in the device's guest space and is never merged into an account, and
 * one account's progress is never shown to another (each has its own cache namespace).
 */
@OptIn(ExperimentalTime::class)
class LearningProgressRepository(
    private val auth: AuthRepository,
    private val sync: LearningSync?,
    private val cache: UserDataCache,
    private val environment: StateFlow<String>,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    data class State(
        val owner: String? = null,
        val uid: String? = null,
        val document: LearningProgressDocument = LearningProgressDocument(),
        val loaded: Boolean = false,
        val syncing: Boolean = false,
        /** Saved on this device, but the account copy couldn't be updated yet; retried on the next change. */
        val syncFailed: Boolean = false
    ) {
        fun journey(symbol: String) = document.journeys.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
        val inProgress: List<ResearchProgress> get() = document.journeys.filter { !it.finished }.sortedByDescending { it.lastVisited }
        val completed: List<ResearchProgress> get() = document.journeys.filter { it.finished }.sortedByDescending { it.lastVisited }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()
    private val writes = Mutex()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(auth.session.filter { !it.initializing }.map { it.user?.id }, environment) { uid, env -> uid to env }
                .distinctUntilChanged()
                .collectLatest { (uid, env) ->
                    val owner = owner(env, uid)
                    mutable.value = State(owner = owner, uid = uid, document = local(owner), loaded = true)
                    if (uid != null) pull(owner, uid)
                }
        }
    }

    private fun owner(env: String, uid: String?) = uid?.let { userCacheOwner(env, it) } ?: "$env|guest"

    private suspend fun local(owner: String): LearningProgressDocument = cache.read(owner, KEY)?.let { (text, _) ->
        runCatching { json.decodeFromString(LearningProgressDocument.serializer(), text) }.getOrNull()
    } ?: LearningProgressDocument()

    /** Fetches the account copy and merges it with this device's copy; pushes back if the device had newer visits. */
    suspend fun pull() {
        val owner = mutable.value.owner ?: return
        pull(owner, mutable.value.uid ?: return)
    }

    private suspend fun pull(owner: String, uid: String) {
        val remote = sync ?: return
        mutable.update { if (it.owner == owner) it.copy(syncing = true) else it }
        try {
            val server = remote.load(uid)
            val pushed = writes.withLock {
                if (mutable.value.owner != owner) return
                val merged = mutable.value.document.merge(server)
                store(owner, merged)
                merged != server
            }
            if (pushed) push(owner, uid) else mutable.update { if (it.owner == owner) it.copy(syncing = false, syncFailed = false) else it }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            mutable.update { if (it.owner == owner) it.copy(syncing = false, syncFailed = true) else it }
        }
    }

    private suspend fun push(owner: String, uid: String) {
        val remote = sync ?: return
        try {
            val server = remote.save(uid, mutable.value.takeIf { it.owner == owner }?.document ?: return)
            writes.withLock {
                if (mutable.value.owner != owner) return
                store(owner, mutable.value.document.merge(server))
            }
            mutable.update { if (it.owner == owner) it.copy(syncing = false, syncFailed = false) else it }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            mutable.update { if (it.owner == owner) it.copy(syncing = false, syncFailed = true) else it }
        }
    }

    private suspend fun store(owner: String, document: LearningProgressDocument) {
        mutable.update { if (it.owner == owner) it.copy(document = document) else it }
        cache.write(owner, KEY, json.encodeToString(LearningProgressDocument.serializer(), document), now())
    }

    /** Records a change for one company (creating its journey on first visit), then syncs when signed in. */
    suspend fun record(symbol: String, name: String, change: (ResearchProgress) -> ResearchProgress) {
        val current = mutable.value
        val owner = current.owner ?: return
        val time = now()
        writes.withLock {
            if (mutable.value.owner != owner) return
            val document = mutable.value.document
            val key = symbol.uppercase()
            val existing = document.journeys.firstOrNull { it.symbol.equals(key, ignoreCase = true) }
                ?: ResearchProgress(symbol = key, name = name, startedAt = time, lastVisited = time)
            val updated = change(existing).copy(lastVisited = maxOf(time, existing.lastVisited + 1), name = name.ifBlank { existing.name })
            store(owner, LearningProgressDocument(listOf(updated) + document.journeys.filter { !it.symbol.equals(key, ignoreCase = true) }, time)
                .let { it.copy(journeys = it.journeys.take(LearningProgressDocument.MAX_JOURNEYS)) })
        }
        val uid = current.uid ?: return
        mutable.update { if (it.owner == owner) it.copy(syncing = true) else it }
        scope.launch { push(owner, uid) }
    }

    /** Starts a company's research again: clears completed steps and quiz answers. */
    suspend fun restart(symbol: String, name: String) = record(symbol, name) { it.copy(completedSteps = emptyList(), currentStep = 0, quizzes = emptyMap(), contentVersion = GuidedResearchContent.VERSION) }

    companion object { const val KEY = "learning.progress.v1" }
}

/** Hosts without an account graph (previews, tests): guest progress kept in memory only. */
fun ephemeralLearningProgress(scope: CoroutineScope): LearningProgressRepository =
    LearningProgressRepository(NoAccounts, null, org.example.stocksteps.data.userdata.InMemoryUserDataCache(), MutableStateFlow("local"), scope).also { it.start() }

private object NoAccounts : AuthRepository {
    override val session: StateFlow<org.example.stocksteps.model.AuthSession> = MutableStateFlow(org.example.stocksteps.model.AuthSession(initializing = false))
    override val currentUser = session.map { it.user }
    override suspend fun signUp(email: String, password: String) = throw org.example.stocksteps.domain.AccountException("Accounts aren't available here.")
    override suspend fun signIn(email: String, password: String) = throw org.example.stocksteps.domain.AccountException("Accounts aren't available here.")
    override suspend fun signOut() = Unit
}

// ---------- Company data (loaded once per company, shared by all steps) ----------

@OptIn(ExperimentalTime::class)
class GuidedResearchRepository(
    private val details: CompanyDetailsRepository,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val lock = Mutex()
    private val memory = LinkedHashMap<String, Pair<CompanyDetails, Long>>()

    suspend fun load(symbol: String, force: Boolean = false): CompanyDetails {
        val key = symbol.uppercase()
        lock.withLock { memory[key]?.takeIf { !force && now() - it.second < TTL }?.let { return it.first } }
        val loaded = details.getDetails(key)
        lock.withLock {
            memory[key] = loaded to now()
            while (memory.size > 12) memory.remove(memory.keys.first())
        }
        return loaded
    }

    private companion object { const val TTL = 15 * 60_000L }
}

// ---------- Guided research flow ----------

/** Plus-only AI research, asked through the backend (free users never reach it). */
fun interface ResearchAsk { suspend fun ask(symbol: String, question: ResearchQuestion): ResearchAnswer }

data class GuidedResearchState(
    val symbol: String = "",
    val name: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val snapshot: ResearchSnapshot? = null,
    val progress: ResearchProgress? = null,
    /** 0 = overview, 1–5 = a step, 6 = summary. */
    val screen: Int = 0,
    /** Answers given in this visit (shown as feedback); the saved record keeps whether it was right. */
    val answers: Map<String, QuizResult> = emptyMap(),
    val signedIn: Boolean = false,
    val plus: Boolean = false,
    val asking: Boolean = false,
    val answer: ResearchAnswer? = null,
    /** Free users tapping "Ask StockSteps AI": show the upgrade flow; nothing was sent. */
    val upgradeRequired: Boolean = false,
    val message: String? = null,
    val syncFailed: Boolean = false
) {
    val completedCount: Int get() = progress?.completedCount ?: 0
    val started: Boolean get() = progress != null && (completedCount > 0 || (progress.currentStep) > 0)
    val step: StepView? get() = snapshot?.step(screen)
    fun completed(step: Int) = progress?.completedSteps?.contains(step) == true
    val progressLabel: String get() = "$completedCount of ${ResearchStep.COUNT} steps completed"
    val primaryLabel: String get() = when {
        progress?.finished == true -> "Review research"
        started -> "Continue learning"
        else -> "Start learning"
    }
    companion object { const val OVERVIEW = 0; const val SUMMARY = 6 }
}

class GuidedResearchPresenter(
    private val data: GuidedResearchRepository,
    private val progress: LearningProgressRepository,
    private val plus: kotlinx.coroutines.flow.Flow<Boolean?>,
    private val asker: ResearchAsk?,
    private val scope: CoroutineScope,
    private val today: () -> String
) {
    private val mutable = MutableStateFlow(GuidedResearchState())
    val state: StateFlow<GuidedResearchState> = mutable.asStateFlow()
    private var loadJob: Job? = null
    private var watchJob: Job? = null

    fun load(symbol: String, name: String? = null, startAt: Int? = null) {
        val key = symbol.uppercase()
        mutable.value = GuidedResearchState(symbol = key, name = name ?: key, screen = startAt?.coerceIn(0, GuidedResearchState.SUMMARY) ?: 0)
        watchJob?.cancel()
        watchJob = scope.launch {
            combine(progress.state, plus) { p, isPlus -> p to isPlus }.collectLatest { (p, isPlus) ->
                mutable.update { it.copy(progress = p.journey(key), signedIn = p.uid != null, plus = p.uid != null && isPlus == true, syncFailed = p.syncFailed) }
            }
        }
        fetch(force = false)
    }

    fun retry() = fetch(force = true)

    private fun fetch(force: Boolean) {
        val symbol = mutable.value.symbol
        loadJob?.cancel()
        loadJob = scope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val snapshot = GuidedResearchEngine.build(data.load(symbol, force), today())
                mutable.update { it.copy(loading = false, snapshot = snapshot, name = snapshot.name) }
                if (mutable.value.screen != GuidedResearchState.OVERVIEW) visit(mutable.value.screen)
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { it.copy(loading = false, error = (cause as? StockDataException)?.message ?: (cause as? StockStepsApiException)?.error?.message ?: "This company's information couldn't be loaded. Check your connection and try again.") }
            }
        }
    }

    /** Overview's primary action: the first step, the step to resume, or the summary once finished. */
    fun startOrContinue() {
        val p = mutable.value.progress
        open(when { p == null -> 1; p.finished -> GuidedResearchState.SUMMARY; else -> p.resumeStep })
    }

    /** Any step can be opened at any time; nothing is locked. */
    fun open(screen: Int) {
        val target = screen.coerceIn(0, GuidedResearchState.SUMMARY)
        mutable.update { it.copy(screen = target, answer = null, message = null) }
        visit(target)
    }

    /** "Continue": marks this step complete and moves on (the summary follows step 5). */
    fun next() {
        val current = mutable.value.screen
        if (current in 1..ResearchStep.COUNT) {
            record { p -> p.copy(completedSteps = (p.completedSteps + current).distinct().sorted(), currentStep = current + 1) }
            mutable.update { it.copy(screen = current + 1, answer = null, message = null) }
        } else if (current == GuidedResearchState.OVERVIEW) startOrContinue()
    }

    fun back() {
        val current = mutable.value.screen
        if (current > 0) open(current - 1)
    }

    fun answer(optionId: String) {
        val step = mutable.value.step ?: return
        val result = QuizEngine.answer(step.quiz, optionId)
        mutable.update { it.copy(answers = it.answers + (step.quiz.id to result)) }
        record { p -> p.copy(quizzes = p.quizzes + (step.quiz.id to QuizRecord(result.correct, step.quiz.version))) }
    }

    /** Clears the shown answer so the question can be tried again (no penalty). */
    fun retryQuiz() {
        val step = mutable.value.step ?: return
        mutable.update { it.copy(answers = it.answers - step.quiz.id) }
    }

    fun restart() {
        val s = mutable.value
        scope.launch { progress.restart(s.symbol, s.name) }
        mutable.update { it.copy(answers = emptyMap(), screen = GuidedResearchState.OVERVIEW) }
    }

    /** Before showing the question box: true for StockSteps+; otherwise shows sign-in or the upgrade flow. */
    fun requestAi(): Boolean {
        val current = mutable.value
        return when {
            !current.signedIn -> { mutable.update { it.copy(message = "Sign in and subscribe to StockSteps+ to ask StockSteps AI.") }; false }
            !current.plus || asker == null -> { mutable.update { it.copy(upgradeRequired = true) }; false }
            else -> true
        }
    }

    /** StockSteps+ only: free users get the upgrade experience and nothing is sent. */
    fun ask(question: String) {
        val current = mutable.value
        val text = question.trim()
        if (text.isEmpty() || current.asking) return
        if (!requestAi()) return
        val asker = asker ?: return
        scope.launch {
            mutable.update { it.copy(asking = true, answer = null, message = null) }
            try {
                val answer = asker.ask(current.symbol, ResearchQuestion(text.take(300), current.screen.takeIf { it in 1..ResearchStep.COUNT }))
                mutable.update { if (it.symbol == current.symbol) it.copy(asking = false, answer = answer) else it }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                val error = (cause as? StockStepsApiException)?.error
                mutable.update { it.copy(asking = false, upgradeRequired = error?.code == "PLUS_REQUIRED", message = if (error?.code == "PLUS_REQUIRED") null else error?.message ?: "StockSteps AI isn't available right now.") }
            }
        }
    }

    fun dismissUpgrade() = mutable.update { it.copy(upgradeRequired = false) }
    fun dismissMessage() = mutable.update { it.copy(message = null) }

    /** Stops observing when the screen goes away (hosts that share one scope, e.g. iOS). */
    fun close() {
        watchJob?.cancel()
        loadJob?.cancel()
    }

    private fun visit(screen: Int) {
        if (mutable.value.snapshot == null) return
        record { p -> p.copy(currentStep = screen) }
    }

    private fun record(change: (ResearchProgress) -> ResearchProgress) {
        val s = mutable.value
        if (s.symbol.isEmpty()) return
        scope.launch { progress.record(s.symbol, s.name, change) }
    }
}
