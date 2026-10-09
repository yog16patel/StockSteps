package org.example.stocksteps.screener

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.userdata.UserApi
import org.example.stocksteps.network.StockStepsApiException
import kotlin.random.Random

// ---------- Phase 5 client: AI Comparison Assistant state shared by Android and iOS ----------

/** Signed-in AI comparison API (fakeable in tests). The server checks StockSteps+ and quotas on every call. */
interface ComparisonAiRemote {
    suspend fun summary(request: ComparisonAiRequest): ComparisonAiResponse
    suspend fun ask(request: ComparisonAiRequest): ComparisonAiResponse
    suspend fun usage(): ComparisonAiUsage
}

class RemoteComparisonAi(private val api: UserApi) : ComparisonAiRemote {
    override suspend fun summary(request: ComparisonAiRequest) = api.compareAiSummary(request)
    override suspend fun ask(request: ComparisonAiRequest) = api.compareAiAsk(request)
    override suspend fun usage() = api.compareAiUsage()
}

/** One exchange in the conversation: what was asked and the (validated) answer or a retryable error. */
data class AiTurn(
    val id: String,
    val title: String,
    val request: ComparisonAiRequest,
    val response: ComparisonAiResponse? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val errorCode: String? = null
) {
    val retryable: Boolean get() = error != null && errorCode !in setOf("PLUS_REQUIRED", "AI_QUOTA_EXCEEDED", "AI_DAILY_LIMIT", "SIGN_IN_REQUIRED", "INVALID_QUESTION")
}

/** The StockSteps+ preview (honest: no prices, no purchase flow in the app yet). */
data class AiUpsell(val signIn: Boolean) {
    val title: String get() = if (signIn) "Sign in to use StockSteps AI" else "StockSteps AI is part of StockSteps+"
    val body: String get() = "Ask questions about the companies you're comparing and get explanations built only from the verified figures on the Compare screen, with every number linked to its source."
    val benefits: List<String> get() = listOf("A plain-language summary of the biggest differences", "Follow-up questions about growth, profitability, valuation and debt",
        "Help with your research checklist questions", "Explanations of 3- and 5-year financial history")
    val footnote: String get() = "The comparison, guided explanations, 1-year history and the basic research checklist stay free. In-app purchase isn't available yet; your StockSteps+ status is in Settings."
}

data class ComparisonAiUiState(
    val signedIn: Boolean = false,
    val symbols: List<String> = emptyList(),
    val usage: ComparisonAiUsage? = null,
    val usageLoading: Boolean = false,
    val turns: List<AiTurn> = emptyList(),
    val input: String = "",
    val conversationId: String? = null,
    val contextVersion: String? = null,
    val upsell: AiUpsell? = null,
    /** A research question waiting for the user's choice about sending their note (explicit consent). */
    val pendingResearch: PendingResearch? = null,
    val message: String? = null,
    /** Set when the companies changed: earlier answers were about other companies and were cleared. */
    val resetNote: String? = null
) {
    val busy: Boolean get() = turns.any { it.loading }
    /** From the server's answer only (never a local flag). */
    val plus: Boolean get() = usage?.plus == true
    val canAsk: Boolean get() = signedIn && symbols.size >= 2 && !busy
    val inputValid: Boolean get() = input.trim().length in 3..MAX_QUESTION
    val remaining: Int get() = MAX_QUESTION - input.length
    companion object { const val MAX_QUESTION = 300 }
}

/** The research question being asked about, and whether a non-empty note exists to (optionally) include. */
data class PendingResearch(val questionId: String, val questionText: String, val sessionId: String?, val hasNote: Boolean)

/**
 * AI Comparison Assistant presenter. One conversation per company selection and account: changing the
 * companies or signing in as someone else clears it, so earlier answers are never shown for other
 * companies. Each request carries an idempotency key; "Try again" reuses it, so a retried or doubled
 * request is answered once and charged once. Nothing here calls an AI service: the server does.
 */
class ComparisonAiPresenter(
    private val remote: ComparisonAiRemote,
    private val scope: CoroutineScope,
    /** The signed-in uid (null = guest). */
    private val owner: Flow<String?>,
    /** The companies on the Compare screen. */
    private val selection: Flow<List<String>>,
    private val newKey: () -> String = { (1..3).joinToString("") { Random.nextLong().toULong().toString(36) }.take(32) }
) {
    private val mutable = MutableStateFlow(ComparisonAiUiState())
    val state: StateFlow<ComparisonAiUiState> = mutable.asStateFlow()
    private val jobs = HashMap<String, Job>()

    init {
        scope.launch {
            combine(owner.distinctUntilChanged(), selection.distinctUntilChanged()) { uid, symbols -> uid to symbols }.collectLatest { (uid, symbols) ->
                val previous = mutable.value
                jobs.values.forEach { it.cancel() }; jobs.clear()
                val companiesChanged = previous.symbols.isNotEmpty() && previous.symbols != symbols && previous.turns.isNotEmpty()
                mutable.value = ComparisonAiUiState(signedIn = uid != null, symbols = symbols, usage = previous.usage.takeIf { uid != null && previous.signedIn },
                    resetNote = if (companiesChanged && uid != null && previous.signedIn) "You changed the companies, so a new conversation started. Earlier answers were about ${previous.symbols.joinToString(", ")}." else null)
                if (uid != null) loadUsage()
            }
        }
    }

    private fun failure(cause: Exception): Pair<String?, String> {
        val api = cause as? StockStepsApiException
        return api?.error?.code to (api?.error?.message ?: "StockSteps AI couldn't be reached. Check your connection and try again.")
    }

    suspend fun loadUsage() {
        if (!mutable.value.signedIn) return
        mutable.update { it.copy(usageLoading = true) }
        val usage = try { remote.usage() } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            null
        }
        mutable.update { it.copy(usageLoading = false, usage = usage ?: it.usage) }
    }
    fun refreshUsage() { scope.launch { loadUsage() } }

    /** Guests and free accounts get the preview instead of a request (the server would refuse anyway). */
    private fun gate(): Boolean {
        val s = mutable.value
        if (!s.signedIn) { mutable.update { it.copy(upsell = AiUpsell(signIn = true)) }; return false }
        if (s.usage != null && !s.usage.plus) { mutable.update { it.copy(upsell = AiUpsell(signIn = false)) }; return false }
        if (s.symbols.size < 2) { mutable.update { it.copy(message = "Choose at least two companies to compare first.") }; return false }
        return true
    }

    private fun request(type: ComparisonAiType, question: String? = null, metricId: String? = null, historyRange: HistoryRange? = null,
                        research: PendingResearch? = null, includeNote: Boolean = false): ComparisonAiRequest {
        val s = mutable.value
        return ComparisonAiRequest(s.symbols, type, question, metricId, s.conversationId.takeIf { !type.summary }, s.contextVersion, historyRange?.label,
            research?.sessionId, research?.questionId, includeNote && research?.hasNote == true, newKey())
    }

    private fun send(title: String, request: ComparisonAiRequest) {
        // Double taps and recomposition: an identical question already in flight isn't sent twice.
        if (mutable.value.turns.any { it.loading && it.title == title }) return
        val turn = AiTurn(newKey(), title, request, loading = true)
        mutable.update { it.copy(turns = it.turns + turn, resetNote = null) }
        run(turn)
    }

    private fun run(turn: AiTurn) {
        jobs[turn.id] = scope.launch {
            try {
                val response = if (turn.request.type.summary) remote.summary(turn.request) else remote.ask(turn.request)
                mutable.update { s ->
                    // An answer for companies that are no longer selected is dropped.
                    if (response.symbols.isNotEmpty() && response.symbols != s.symbols) return@update s
                    s.copy(turns = s.turns.map { if (it.id == turn.id) it.copy(response = response, loading = false, error = null, errorCode = null) else it },
                        usage = response.usage ?: s.usage, conversationId = response.conversationId ?: s.conversationId, contextVersion = response.contextVersion)
                }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                val (code, text) = failure(cause)
                mutable.update { s ->
                    s.copy(turns = s.turns.map { if (it.id == turn.id) it.copy(loading = false, error = text, errorCode = code) else it },
                        upsell = when (code) { "PLUS_REQUIRED" -> AiUpsell(false); "SIGN_IN_REQUIRED" -> AiUpsell(true); else -> s.upsell })
                }
                if (code == "PLUS_REQUIRED" || code == "AI_QUOTA_EXCEEDED" || code == "AI_DAILY_LIMIT") loadUsage()
            } finally { jobs.remove(turn.id) }
        }
    }

    fun summarize(type: ComparisonAiType = ComparisonAiType.OVERVIEW, historyRange: HistoryRange? = null) {
        if (!gate()) return
        send(type.label, request(type, historyRange = historyRange))
    }

    fun explainMetric(metricId: String, question: String? = null) {
        if (!gate()) return
        val label = ComparisonInterpretationEngine.label(metricId)
        send(question ?: "Explain the difference in ${label.lowercase()}", request(ComparisonAiType.METRIC, question ?: "Explain the difference in ${label.lowercase()}", metricId))
    }

    fun suggest(suggestion: SuggestedAiQuestion) = when (suggestion.type) {
        ComparisonAiType.OVERVIEW, ComparisonAiType.HISTORY -> summarize(suggestion.type)
        ComparisonAiType.METRIC -> explainMetric(suggestion.metricId ?: "pe", suggestion.text)
        else -> ask(suggestion.text)
    }

    fun editInput(text: String) = mutable.update { it.copy(input = text.take(ComparisonAiUiState.MAX_QUESTION)) }

    fun ask(question: String = mutable.value.input) {
        val q = question.trim()
        if (q.length !in 3..ComparisonAiUiState.MAX_QUESTION) { mutable.update { it.copy(message = "Ask a question of 3–${ComparisonAiUiState.MAX_QUESTION} characters.") }; return }
        if (!gate()) return
        mutable.update { it.copy(input = if (it.input.trim() == q) "" else it.input) }
        send(q, request(ComparisonAiType.QUESTION, q))
    }

    /**
     * Research checklist help. With a non-empty note the user chooses first whether to include it
     * ([confirmResearch]); without one the question is sent straight away (no note is ever read).
     */
    fun research(questionId: String, sessionId: String?, hasNote: Boolean) {
        val question = ResearchChecklist.question(questionId) ?: return
        if (!gate()) return
        val pending = PendingResearch(questionId, question.text, sessionId, hasNote && sessionId != null)
        if (pending.hasNote) mutable.update { it.copy(pendingResearch = pending) } else send("Help with: ${question.text}", request(ComparisonAiType.RESEARCH, question.text, research = pending))
    }
    fun confirmResearch(includeNote: Boolean) {
        val pending = mutable.value.pendingResearch ?: return
        mutable.update { it.copy(pendingResearch = null) }
        send("Help with: ${pending.questionText}" + if (includeNote) " (with my note)" else "", request(ComparisonAiType.RESEARCH, pending.questionText, research = pending, includeNote = includeNote))
    }
    fun cancelResearch() = mutable.update { it.copy(pendingResearch = null) }

    /** Same idempotency key: the server returns the earlier answer if it already produced one. */
    fun retry(turnId: String) {
        val turn = mutable.value.turns.firstOrNull { it.id == turnId && it.retryable && !it.loading } ?: return
        mutable.update { s -> s.copy(turns = s.turns.map { if (it.id == turnId) it.copy(loading = true, error = null, errorCode = null) else it }) }
        run(turn.copy(loading = true, error = null, errorCode = null))
    }
    fun remove(turnId: String) = mutable.update { s -> s.copy(turns = s.turns.filterNot { it.id == turnId && !it.loading }) }
    fun startOver() { jobs.values.forEach { it.cancel() }; jobs.clear(); mutable.update { it.copy(turns = emptyList(), conversationId = null, contextVersion = null, resetNote = null) } }

    fun showUpsell() = mutable.update { it.copy(upsell = AiUpsell(signIn = !it.signedIn)) }
    fun dismissUpsell() = mutable.update { it.copy(upsell = null) }
    fun dismissMessage() = mutable.update { it.copy(message = null) }
    fun dismissResetNote() = mutable.update { it.copy(resetNote = null) }
}
