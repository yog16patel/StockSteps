package org.example.stocksteps.screener

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.userdata.UserApi
import org.example.stocksteps.network.StockStepsApiException

// ---------- Phase 4 client: research checklist state shared by Android and iOS ----------

/** Signed-in research API (fakeable in tests). */
interface ComparisonResearchRemote {
    suspend fun sessions(): ResearchSessionsResponse
    suspend fun create(symbols: List<String>, title: String?): ResearchSessionResponse
    suspend fun session(id: String): ResearchSessionResponse
    suspend fun update(id: String, request: UpdateResearchRequest): ResearchSessionResponse
    suspend fun delete(id: String): ResearchSessionsResponse
    suspend fun summary(id: String): ResearchSummary
    suspend fun snapshots(id: String): List<ResearchSnapshot>
    suspend fun createSnapshot(id: String, label: String?): ResearchSessionResponse
    suspend fun deleteSnapshot(id: String, snapshotId: String): ResearchSessionResponse
    suspend fun export(id: String): ByteArray
}

class RemoteComparisonResearch(private val api: UserApi) : ComparisonResearchRemote {
    override suspend fun sessions() = api.researchSessions()
    override suspend fun create(symbols: List<String>, title: String?) = api.createResearch(CreateResearchRequest(symbols, title))
    override suspend fun session(id: String) = api.research(id)
    override suspend fun update(id: String, request: UpdateResearchRequest) = api.updateResearch(id, request)
    override suspend fun delete(id: String) = api.deleteResearch(id)
    override suspend fun summary(id: String) = api.researchSummary(id)
    override suspend fun snapshots(id: String) = api.researchSnapshots(id)
    override suspend fun createSnapshot(id: String, label: String?) = api.createResearchSnapshot(id, label)
    override suspend fun deleteSnapshot(id: String, snapshotId: String) = api.deleteResearchSnapshot(id, snapshotId)
    override suspend fun export(id: String) = api.exportResearch(id)
}

/** A finished PDF for the platform to save or share (consumed once). */
class ResearchExport(val fileName: String, val bytes: ByteArray)

/** The one StockSteps+ entry point in the checklist (honest: no prices, no purchase flow). */
data class ResearchUpsell(val title: String, val body: String) {
    val benefits: List<String> get() = listOf("Advanced checklist: growth quality, profitability quality, financial health in depth, valuation context, research gaps",
        "Up to 100 saved research sessions (fair use)", "Dated research snapshots", "A detailed research summary and a PDF report",
        "3- and 5-year financial history")
    val footnote: String get() = "The basic checklist, notes, progress, three saved sessions and the basic summary stay free. In-app purchase isn't available yet; your StockSteps+ status is in Settings."
    companion object { val DEFAULT = ResearchUpsell("Deeper research with StockSteps+", "Go beyond the basic checklist with multi-year research, snapshots and a downloadable report.") }
}

/** One question ready to render: the user's answer, an unsaved draft, and "what the data shows". */
data class ResearchQuestionView(
    val question: ResearchQuestion,
    val status: ResearchStatus,
    val note: String,
    val draft: String,
    val saving: Boolean,
    val locked: Boolean,
    val context: List<String>
) {
    val dirty: Boolean get() = draft != note
    val remaining: Int get() = ResearchChecklist.MAX_NOTE - draft.length
}
data class ResearchCategoryView(val category: ResearchCategory, val questions: List<ResearchQuestionView>)

data class ResearchUiState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val sessions: ResearchSessionsResponse? = null,
    val open: ResearchSessionResponse? = null,
    val opening: Boolean = false,
    val drafts: Map<String, String> = emptyMap(),
    val saving: Set<String> = emptySet(),
    val summary: ResearchSummary? = null,
    val summaryLoading: Boolean = false,
    val snapshots: List<ResearchSnapshot> = emptyList(),
    val busy: Boolean = false,
    val export: ResearchExport? = null,
    val upsell: ResearchUpsell? = null,
    val message: String? = null
) {
    val plus: Boolean get() = open?.plus ?: sessions?.plus ?: false
    /** Free sessions with advanced answers from an earlier StockSteps+ period still show them, read-only. */
    fun categories(context: (ResearchQuestion) -> List<String> = { emptyList() }): List<ResearchCategoryView> {
        val response = open ?: return emptyList()
        val responses = response.session.responses
        return ResearchChecklist.categories.mapNotNull { category ->
            val questions = ResearchChecklist.questions.filter { it.category == category.id }
            val locked = category.tier == ResearchTier.PLUS && !response.plus
            if (locked && questions.none { responses[it.id]?.let { r -> r.note.isNotBlank() || r.status != ResearchStatus.NOT_REVIEWED } == true }) return@mapNotNull null
            ResearchCategoryView(category, questions.map { q ->
                val r = responses[q.id]
                ResearchQuestionView(q, r?.status ?: ResearchStatus.NOT_REVIEWED, r?.note.orEmpty(), drafts[q.id] ?: r?.note.orEmpty(), q.id in saving, locked, context(q))
            })
        }
    }
    val advancedPreview: List<ResearchCategory> get() = if (open != null && !plus) ResearchChecklist.categories.filter { it.tier == ResearchTier.PLUS } else emptyList()
}

/**
 * Research checklist presenter. Status changes save immediately; notes save on "Save note" (drafts stay
 * local until then, and survive a conflict reload). Nothing here requests market data: context lines come
 * from the comparison already on screen, summaries/exports from the server's shared data layer.
 */
class ComparisonResearchPresenter(
    private val remote: ComparisonResearchRemote,
    private val scope: CoroutineScope,
    /** The signed-in uid (null = guest); a change resets everything so accounts never mix. */
    private val owner: Flow<String?>
) {
    private val mutable = MutableStateFlow(ResearchUiState())
    val state: StateFlow<ResearchUiState> = mutable.asStateFlow()
    private var signedIn = false

    init {
        scope.launch {
            owner.distinctUntilChanged().collectLatest { uid ->
                signedIn = uid != null
                mutable.value = ResearchUiState(signedIn = signedIn)
                if (uid != null) refresh()
            }
        }
    }

    private fun failure(cause: Exception, fallback: String): Pair<String?, String> {
        val api = cause as? StockStepsApiException
        return api?.error?.code to (api?.error?.message ?: fallback)
    }

    private suspend fun <T> run(fallback: String, onError: (String?, String) -> Unit = { code, text -> mutable.update { it.copy(message = text, upsell = if (code == "PLUS_REQUIRED") ResearchUpsell.DEFAULT else it.upsell) } },
                                block: suspend () -> T): T? = try { block() } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        val (code, text) = failure(cause, fallback)
        onError(code, text); null
    }

    suspend fun refresh() {
        if (!signedIn) return
        mutable.update { it.copy(loading = true, error = null) }
        val list = run("Your research couldn't be loaded. Check your connection and try again.", { _, text -> mutable.update { it.copy(error = text) } }) { remote.sessions() }
        mutable.update { it.copy(loading = false, sessions = list ?: it.sessions) }
    }
    fun reload() { scope.launch { refresh() } }

    fun create(symbols: List<String>, title: String? = null) = scope.launch {
        mutable.update { it.copy(busy = true) }
        val created = run("The research session couldn't be saved. Try again.") { remote.create(symbols, title) }
        mutable.update { it.copy(busy = false, open = created ?: it.open, drafts = if (created != null) emptyMap() else it.drafts, summary = null, snapshots = emptyList()) }
        if (created != null) refresh()
    }

    fun open(id: String) = scope.launch {
        mutable.update { it.copy(opening = true, summary = null, snapshots = emptyList(), drafts = emptyMap()) }
        val session = run("This research session couldn't be opened.") { remote.session(id) }
        mutable.update { it.copy(opening = false, open = session ?: it.open) }
        if (session != null && session.session.snapshots.isNotEmpty()) mutable.update { it.copy(snapshots = session.session.snapshots.sortedByDescending { s -> s.createdAt }) }
    }
    fun close() = mutable.update { it.copy(open = null, drafts = emptyMap(), summary = null, snapshots = emptyList()) }

    fun delete(id: String) = scope.launch {
        val list = run("The research session couldn't be deleted.") { remote.delete(id) }
        if (list != null) mutable.update { it.copy(sessions = list, open = it.open?.takeIf { o -> o.session.id != id }) }
    }

    private suspend fun save(questionId: String, update: ResearchResponseUpdate) {
        val current = mutable.value.open ?: return
        mutable.update { it.copy(saving = it.saving + questionId) }
        try {
            val next = remote.update(current.session.id, UpdateResearchRequest(responses = listOf(update), expectedRevision = current.session.revision))
            mutable.update { s -> s.copy(open = next, saving = s.saving - questionId,
                drafts = if (update.note != null && s.drafts[questionId] == update.note) s.drafts - questionId else s.drafts) }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            val (code, text) = failure(cause, "Not saved. Check your connection and try again.")
            if (code == "RESEARCH_CONFLICT") {
                // Edited on another device: load the latest; unsaved drafts stay so nothing typed is lost.
                val latest = run("This research session couldn't be reloaded.") { remote.session(current.session.id) }
                mutable.update { it.copy(open = latest ?: it.open, saving = it.saving - questionId, message = text) }
            } else mutable.update { it.copy(saving = it.saving - questionId, message = text, upsell = if (code == "PLUS_REQUIRED") ResearchUpsell.DEFAULT else it.upsell) }
        }
    }

    /** User-controlled only: nothing is ever marked reviewed automatically. */
    fun setStatus(questionId: String, status: ResearchStatus) = scope.launch { save(questionId, ResearchResponseUpdate(questionId, status = status)) }
    fun editNote(questionId: String, text: String) = mutable.update { it.copy(drafts = it.drafts + (questionId to text.take(ResearchChecklist.MAX_NOTE))) }
    fun saveNote(questionId: String) = scope.launch {
        val draft = mutable.value.drafts[questionId] ?: return@launch
        save(questionId, ResearchResponseUpdate(questionId, note = draft))
    }
    fun discardNote(questionId: String) = mutable.update { it.copy(drafts = it.drafts - questionId) }

    fun loadSummary() = scope.launch {
        val id = mutable.value.open?.session?.id ?: return@launch
        mutable.update { it.copy(summaryLoading = true) }
        val summary = run("The summary couldn't be created. Try again.") { remote.summary(id) }
        mutable.update { it.copy(summaryLoading = false, summary = summary ?: it.summary) }
    }
    fun closeSummary() = mutable.update { it.copy(summary = null) }

    /** StockSteps+ only (the server decides); free accounts see the one upgrade preview instead of a request. */
    fun createSnapshot(label: String? = null) = scope.launch {
        val open = mutable.value.open ?: return@launch
        if (!open.plus) { showUpsell(); return@launch }
        mutable.update { it.copy(busy = true) }
        val next = run("The snapshot couldn't be saved. Try again.") { remote.createSnapshot(open.session.id, label) }
        mutable.update { it.copy(busy = false, open = next ?: it.open, snapshots = next?.session?.snapshots?.sortedByDescending { s -> s.createdAt } ?: it.snapshots,
            message = if (next != null) "Snapshot saved. It keeps today's progress and notes; values in it are as of ${next.session.snapshots.lastOrNull()?.dataAsOf?.take(10) ?: "the time it was saved"}." else it.message) }
    }
    fun deleteSnapshot(snapshotId: String) = scope.launch {
        val id = mutable.value.open?.session?.id ?: return@launch
        val next = run("The snapshot couldn't be deleted.") { remote.deleteSnapshot(id, snapshotId) }
        if (next != null) mutable.update { it.copy(open = next, snapshots = next.session.snapshots.sortedByDescending { s -> s.createdAt }) }
    }

    fun export() = scope.launch {
        val open = mutable.value.open ?: return@launch
        if (!open.plus) { showUpsell(); return@launch }
        mutable.update { it.copy(busy = true) }
        val bytes = run("The report couldn't be created. Try again.") { remote.export(open.session.id) }
        mutable.update { it.copy(busy = false, export = bytes?.let { b -> ResearchExport("StockSteps-research-${open.session.symbols.joinToString("-")}.pdf", b) }) }
    }
    fun exportHandled(message: String? = null) = mutable.update { it.copy(export = null, message = message ?: it.message) }

    fun showUpsell() = mutable.update { it.copy(upsell = ResearchUpsell.DEFAULT) }
    fun dismissUpsell() = mutable.update { it.copy(upsell = null) }
    fun dismissMessage() = mutable.update { it.copy(message = null) }
}

/**
 * "What the data shows" for a question, from the comparison already loaded (Phase 2 explanations and
 * Phase 3 history). No requests; when data isn't on screen yet, it says where to find it.
 */
object ResearchContext {
    fun lines(question: ResearchQuestion, comparison: ComparisonUiState?): List<String> {
        comparison ?: return emptyList()
        val lines = mutableListOf<String>()
        question.metrics.forEach { id -> comparison.guide(id)?.let { g -> lines += "${g.label} (${g.comparability.label.lowercase()}): ${g.observation}" } }
        val history = comparison.history
        question.history.forEach { metric ->
            val data = history?.metric(metric)
            when {
                data != null -> data.insights.take(2).forEach { lines += "${metric.label}, ${history.range.label}: $it" }
                metric.premium && history?.access?.plus != true -> Unit
                metric.premium -> lines += "${metric.label}: choose ${metric.label} in Historical comparison to see it."
            }
        }
        if (question.id == "r-context" || question.id == "rg-gaps") comparison.guides.values.filter { it.comparability == Comparability.NOT_COMPARABLE || it.comparability == Comparability.INSUFFICIENT_DATA }
            .forEach { lines += "${it.label}: ${it.comparability.label.lowercase()}" }
        if (question.id == "b-comparable") comparison.industryNote?.let { lines += it } ?: comparison.insights.firstOrNull { it.category == InsightCategory.INDUSTRY }?.let { lines += it.text }
        if (question.id == "r-missing" || question.id == "rg-gaps") history?.insights?.take(3)?.forEach { lines += it }
        return lines.distinct().take(5)
    }
}
