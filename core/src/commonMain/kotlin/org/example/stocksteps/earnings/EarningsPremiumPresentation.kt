package org.example.stocksteps.earnings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.example.stocksteps.network.StockStepsApiException

// Phase 5 client state (shared by Android and iOS). The server decides plan, quotas and content; these
// presenters never treat a client flag as access and never call an AI service themselves.

/** Signed-in premium earnings calls (identity from the verified token on the server). */
interface EarningsPremiumRemote {
    suspend fun overview(reportId: String, scenario: String?): PremiumEarningsOverview
    suspend fun history(reportId: String, limit: Int, cursor: String?, scenario: String?): HistoricalEarningsInsight
    suspend fun explain(reportId: String, refresh: Boolean, scenario: String?): EarningsAiExplanation
    suspend fun ask(reportId: String, question: EarningsAiQuestion, scenario: String?): EarningsAiAnswer
    suspend fun usage(): EarningsAiUsage
    suspend fun digest(scenario: String?): PersonalizedEarningsDigest
    suspend fun explainDigest(scenario: String?): PersonalizedEarningsDigest
    suspend fun digestHistory(): EarningsDigestHistory
    suspend fun digestSettings(): EarningsDigestSettings
    suspend fun saveDigestPreferences(preferences: EarningsDigestPreferences): EarningsDigestSettings
}

class RemoteEarningsPremium(private val user: org.example.stocksteps.data.userdata.UserApi) : EarningsPremiumRemote {
    override suspend fun overview(reportId: String, scenario: String?) = user.premiumEarnings(reportId, scenario)
    override suspend fun history(reportId: String, limit: Int, cursor: String?, scenario: String?) = user.earningsHistory(reportId, limit, cursor, scenario)
    override suspend fun explain(reportId: String, refresh: Boolean, scenario: String?) = user.explainEarnings(reportId, refresh, scenario)
    override suspend fun ask(reportId: String, question: EarningsAiQuestion, scenario: String?) = user.askEarningsReport(reportId, question, scenario)
    override suspend fun usage() = user.earningsAiUsage()
    override suspend fun digest(scenario: String?) = user.earningsDigest(scenario)
    override suspend fun explainDigest(scenario: String?) = user.explainEarningsDigest(scenario)
    override suspend fun digestHistory() = user.earningsDigestHistory()
    override suspend fun digestSettings() = user.earningsDigestSettings()
    override suspend fun saveDigestPreferences(preferences: EarningsDigestPreferences) = user.saveEarningsDigestPreferences(preferences)
}

/** A failed premium request, kept distinct so the UI can show the right state (upgrade, quota, retry). */
data class PremiumError(val code: String?, val message: String, val retryable: Boolean)

/** Which contextual StockSteps+ prompt to show; none of them interrupts the free screen. */
enum class UpgradeReason(val title: String, val body: String) {
    EXPLAIN("Understand Earnings With StockSteps+", "Get an easy-to-follow AI explanation of these results, built only from verified figures."),
    ASK("Understand Earnings With StockSteps+", "Ask questions about these results and get answers grounded in the verified report."),
    HISTORY("Understand Earnings With StockSteps+", "Explore up to eight recent quarters of results with a chart and plain-English observations."),
    DIGEST("Understand Earnings With StockSteps+", "Get a personalized earnings digest for the companies on your watchlists.")
}

object PremiumCopy {
    const val TITLE = "Understand These Earnings"
    const val SUBTITLE = "An easy-to-follow explanation of the company's quarterly results."
    const val UPGRADE_TITLE = "Understand Earnings With StockSteps+"
    val BENEFITS = listOf("AI explanations in simple language", "Ask questions about company results", "Explore historical earnings", "Receive personalized earnings digests")
    const val FAIR_USE = "StockSteps+ includes fair-use daily limits for AI features."
    /** MOCK-only demo scenarios for the premium section (the server ignores them in REAL). */
    val SCENARIOS: List<Pair<String?, String>> = listOf(null to "Normal", "ai-failure" to "AI failure", "ai-timeout" to "AI timeout",
        "ai-rate-limit" to "AI rate limit", "ai-quota" to "Quota exceeded", "ai-missing-citation" to "Missing citation",
        "ai-injection" to "Unsafe AI output", "conflicting-sources" to "Conflicting sources", "revised-source" to "Revised source data",
        "entitlement-unavailable" to "Plan unavailable")

    fun error(cause: Throwable, fallback: String): PremiumError {
        val api = cause as? StockStepsApiException
        val code = api?.error?.code
        val retryable = api == null || api.status >= 500 || code == "RATE_LIMITED" || code == "AI_RATE_LIMITED"
        return PremiumError(code, api?.error?.message ?: fallback, retryable)
    }

    fun quotaText(q: EarningsAiQuota?): String? = q?.let {
        "${it.remaining} of ${it.limit} ${it.category.unit} left" + (it.resetAt?.let { at -> " · resets ${EarningsFormatter.date(at.take(10))}, ${at.drop(11).take(5)} UTC" } ?: "")
    }

    fun status(status: org.example.stocksteps.portfolio.analytics.EntitlementStatus): String? = when (status) {
        org.example.stocksteps.portfolio.analytics.EntitlementStatus.CANCELED -> "Your StockSteps+ renewal is off. Premium features stay available until the paid period ends."
        org.example.stocksteps.portfolio.analytics.EntitlementStatus.GRACE_PERIOD -> "There's a problem with your StockSteps+ payment. Premium features stay available while the store retries."
        org.example.stocksteps.portfolio.analytics.EntitlementStatus.BILLING_ISSUE -> "StockSteps+ is paused because of a payment problem. Your free features and data are unaffected."
        org.example.stocksteps.portfolio.analytics.EntitlementStatus.EXPIRED -> "Your StockSteps+ plan has ended. Basic earnings results, history and reminders remain free."
        else -> null
    }
}

enum class HistoryMetric(val label: String) { REVENUE("Revenue"), EPS("EPS") }

/** Chart input for [HistoricalEarningsInsight]: null slots are gaps (missing, not comparable or a fiscal-calendar break). */
data class HistoryChartView(
    val metric: HistoryMetric,
    val values: List<Double?>,
    /** Tooltip per slot with the full value, or why there is no value. */
    val details: List<String>,
    val xLabels: List<String>,
    val yLabels: List<String>,
    val description: String,
    /** Show a zero line (negative EPS). */
    val zeroLine: Boolean,
    val emptyNote: String?
)

data class PremiumHistoryRow(val reportId: String, val label: String, val missing: Boolean, val lines: List<MetricLine>, val notes: List<String>) {
    val accessibility: String get() = if (missing) "$label: no results from the data source." else "$label. " + lines.joinToString(" ") { "${it.label}: ${it.value}." } + notes.joinToString(" ", prefix = " ")
}

object EarningsPremiumFormat {
    private fun dbl(text: String?) = text?.toDoubleOrNull()

    fun chart(h: HistoricalEarningsInsight, metric: HistoryMetric): HistoryChartView {
        val values = ArrayList<Double?>(); val details = ArrayList<String>(); val labels = ArrayList<String>()
        h.quarters.forEachIndexed { i, q ->
            // A fiscal-calendar change gets its own empty slot, so the line never connects across it.
            if (q.breakBefore && i > 0) { values += null; details += "Fiscal calendar changed"; labels += "" }
            val raw = if (metric == HistoryMetric.REVENUE) h.revenueSeries.getOrNull(i) else h.epsSeries.getOrNull(i)
            values += dbl(raw)
            labels += "Q${q.fiscalQuarter} ’${(q.fiscalYear % 100).toString().padStart(2, '0')}"
            details += when {
                q.missing -> "${q.label}: no results from the data source"
                raw == null -> "${q.label}: ${if (metric == HistoryMetric.REVENUE) "revenue" else "EPS"} " +
                    (if ((if (metric == HistoryMetric.REVENUE) q.revenue else q.eps) == null) "not provided" else "not comparable with the other quarters")
                metric == HistoryMetric.REVENUE -> "${q.label}: revenue ${EarningsResultsFormat.revenue(raw, q.currency)}"
                else -> "${q.label}: EPS ${EarningsResultsFormat.eps(raw, q.currency)} (${q.epsBasis.label})"
            }
        }
        val present = values.filterNotNull()
        val fmt: (Double) -> String = { v -> if (metric == HistoryMetric.REVENUE) EarningsFormatter.revenue(v, h.currency) else EarningsFormatter.eps(v, h.currency) }
        val y = if (present.isEmpty()) emptyList() else listOf(fmt(maxOf(present.max(), if (metric == HistoryMetric.EPS) 0.0 else present.max())), fmt(minOf(present.min(), if (metric == HistoryMetric.EPS && present.min() < 0) 0.0 else present.min())))
        val shown = h.quarters.filter { !it.missing }
        val description = "${metric.label} by fiscal quarter, ${h.quarters.firstOrNull()?.label ?: ""} to ${h.quarters.lastOrNull()?.label ?: ""}. " +
            details.filter { it.isNotEmpty() && it != "Fiscal calendar changed" }.joinToString(". ") + "."
        val note = if (present.isEmpty()) "No comparable ${metric.label.lowercase()} values to chart."
            else if (present.size < shown.size || h.quarters.any { it.missing }) "Gaps are quarters without comparable data; they're never drawn as zero." else null
        return HistoryChartView(metric, values, details, labels.let { l -> if (l.size <= 4) l else l.mapIndexed { i, s -> if (i == 0 || i == l.lastIndex || i == l.size / 2) s else "" } },
            y, description, metric == HistoryMetric.EPS && present.any { it < 0 }, note)
    }

    fun rows(h: HistoricalEarningsInsight): List<PremiumHistoryRow> = h.quarters.reversed().map { q ->
        PremiumHistoryRow(q.reportId, q.label, q.missing, if (q.missing) emptyList() else listOfNotNull(
            MetricLine("Revenue", EarningsResultsFormat.revenue(q.revenue, q.currency)),
            MetricLine("EPS", EarningsResultsFormat.eps(q.eps, q.currency), q.epsBasis.label.takeIf { q.eps != null }),
            EarningsResultsFormat.percent(q.revenueYoYPercent)?.let { MetricLine("Revenue vs a year earlier", it) },
            MetricLine("EPS vs estimate", q.epsComparison.label),
            MetricLine("Revenue vs estimate", q.revenueComparison.label)
        ), q.notes)
    }

    /** Short plain-text preview for the AI card (from the deterministic preview). */
    fun usageLine(usage: EarningsAiUsage?, category: EarningsAiCategory): String? = PremiumCopy.quotaText(usage?.quota(category))
}

data class ConversationTurn(val id: Int, val question: String, val answer: EarningsAiAnswer? = null, val pending: Boolean = false, val error: PremiumError? = null)

data class EarningsPremiumState(
    val reportId: String,
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val overview: PremiumEarningsOverview? = null,
    val overviewError: PremiumError? = null,
    val explanation: EarningsAiExplanation? = null,
    val explaining: Boolean = false,
    val explainError: PremiumError? = null,
    /** Progressive disclosure: the full explanation is shown after "Show more". */
    val expanded: Boolean = false,
    val history: HistoricalEarningsInsight? = null,
    val historyLoading: Boolean = false,
    val historyError: PremiumError? = null,
    val metric: HistoryMetric = HistoryMetric.REVENUE,
    val chart: HistoryChartView? = null,
    val historyRows: List<PremiumHistoryRow> = emptyList(),
    val conversation: List<ConversationTurn> = emptyList(),
    val conversationId: String? = null,
    val contextId: String? = null,
    val contextNote: String? = null,
    val asking: Boolean = false,
    val upgrade: UpgradeReason? = null,
    val signInRequired: Boolean = false,
    val scenario: String? = null
) {
    val plus: Boolean get() = overview?.plus == true
    val chips: List<SuggestedQuestion> get() = overview?.suggestedQuestions.orEmpty()
    val sampleData: Boolean get() = overview?.sampleData == true
    val explainUsage: String? get() = EarningsPremiumFormat.usageLine(explanation?.usage ?: overview?.usage, EarningsAiCategory.EXPLANATION)
    val askUsage: String? get() = EarningsPremiumFormat.usageLine(conversation.lastOrNull { it.answer != null }?.answer?.usage ?: overview?.usage, EarningsAiCategory.QUESTION)
    val planNote: String? get() = overview?.let { PremiumCopy.status(it.entitlementStatus) }
}

/**
 * The Earnings Results premium section for one report. Plan and quotas come from the server's
 * overview; a free user's tap shows a contextual upgrade prompt instead of a request. State is reset on
 * every account change (account switching or sign-out), so one user's AI content never shows for another.
 */
class EarningsPremiumPresenter(
    val reportId: String,
    private val remote: EarningsPremiumRemote?,
    private val scope: CoroutineScope,
    private val session: Flow<String?>
) {
    private val mutable = MutableStateFlow(EarningsPremiumState(reportId))
    val state: StateFlow<EarningsPremiumState> = mutable.asStateFlow()
    private var uid: String? = null
    private var started = false
    private var turns = 0
    private var explainJob: Job? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            session.distinctUntilChanged().collectLatest { id ->
                uid = id
                explainJob?.cancel()
                mutable.value = EarningsPremiumState(reportId, signedIn = id != null && remote != null, scenario = mutable.value.scenario)
                if (id != null && remote != null) loadOverview(id)
            }
        }
    }

    private suspend fun loadOverview(owner: String) {
        val r = remote ?: return
        mutable.update { it.copy(loading = true, overviewError = null) }
        try {
            val o = r.overview(reportId, mutable.value.scenario)
            if (uid == owner) mutable.update { it.copy(loading = false, overview = o) }
        } catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            if (uid == owner) mutable.update { it.copy(loading = false, overviewError = PremiumCopy.error(cause, "StockSteps+ earnings features couldn't be loaded.")) }
        }
    }

    /** Returns the remote and owner if the user may proceed; otherwise shows sign-in or upgrade. */
    private fun gate(reason: UpgradeReason): Pair<EarningsPremiumRemote, String>? {
        val r = remote; val owner = uid
        if (r == null || owner == null) { mutable.update { it.copy(signInRequired = true) }; return null }
        val o = mutable.value.overview
        if (o != null && !o.plus) { mutable.update { it.copy(upgrade = reason) }; return null }
        return r to owner
    }

    private fun handle(cause: Exception, reason: UpgradeReason, fallback: String): PremiumError? {
        val e = PremiumCopy.error(cause, fallback)
        if (e.code == "PLUS_REQUIRED") { mutable.update { it.copy(upgrade = reason, overview = it.overview?.copy(plus = false)) }; return null }
        if (e.code == "SIGN_IN_REQUIRED") { mutable.update { it.copy(signInRequired = true) }; return null }
        return e
    }

    private fun refreshUsage(owner: String) {
        val r = remote ?: return
        scope.launch { runCatching { r.usage() }.getOrNull()?.let { u -> if (uid == owner) mutable.update { it.copy(overview = it.overview?.copy(usage = u)) } } }
    }

    fun explain(refresh: Boolean = false) {
        if (mutable.value.explaining) return
        val (r, owner) = gate(UpgradeReason.EXPLAIN) ?: return
        explainJob = scope.launch {
            mutable.update { it.copy(explaining = true, explainError = null) }
            try {
                val e = r.explain(reportId, refresh, mutable.value.scenario)
                if (uid == owner) mutable.update { s -> s.copy(explaining = false, explanation = e,
                    overview = s.overview?.copy(explanation = ExplanationAvailability.CURRENT, explanationGeneratedAt = e.generatedAt, usage = e.usage ?: s.overview.usage)) }
            } catch (cause: Exception) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                val error = handle(cause, UpgradeReason.EXPLAIN, "The explanation couldn't be created. Basic results are still available.")
                if (uid == owner) mutable.update { it.copy(explaining = false, explainError = error) }
                if (error?.code == "AI_QUOTA_EXCEEDED") refreshUsage(owner)
            }
        }
    }

    fun ask(text: String) {
        val question = text.trim()
        if (question.isEmpty() || mutable.value.asking) return
        val (r, owner) = gate(UpgradeReason.ASK) ?: return
        if (question.length > 300) { mutable.update { it.copy(contextNote = "Ask a shorter question (up to 300 characters).") }; return }
        val id = ++turns
        scope.launch {
            mutable.update { it.copy(asking = true, contextNote = null, conversation = it.conversation + ConversationTurn(id, question, pending = true)) }
            try {
                val s = mutable.value
                val a = r.ask(reportId, EarningsAiQuestion(question, s.conversationId, s.contextId), s.scenario)
                if (uid != owner) return@launch
                mutable.update { st ->
                    val turn = ConversationTurn(id, question, a)
                    val history = if (a.contextReset) listOf(turn) else st.conversation.map { if (it.id == id) turn else it }
                    st.copy(asking = false, conversation = history, conversationId = a.conversationId, contextId = a.contextId,
                        contextNote = if (a.contextReset && st.conversation.size > 1) "These results were updated, so a new conversation started." else a.note,
                        overview = st.overview?.copy(usage = a.usage ?: st.overview.usage))
                }
            } catch (cause: Exception) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                val error = handle(cause, UpgradeReason.ASK, "Your question couldn't be answered right now.")
                if (uid == owner) mutable.update { st -> st.copy(asking = false, conversation = if (error == null) st.conversation.filterNot { it.id == id }
                    else st.conversation.map { if (it.id == id) it.copy(pending = false, error = error) else it }) }
                if (error?.code == "AI_QUOTA_EXCEEDED") refreshUsage(owner)
            }
        }
    }

    fun resetConversation() = mutable.update { it.copy(conversation = emptyList(), conversationId = null, contextId = null, contextNote = null) }

    fun loadHistory(cursor: String? = null) {
        if (mutable.value.historyLoading) return
        val (r, owner) = gate(UpgradeReason.HISTORY) ?: return
        scope.launch {
            mutable.update { it.copy(historyLoading = true, historyError = null) }
            try {
                val h = r.history(reportId, HistoricalEarningsEngine.MAX_QUARTERS, cursor, mutable.value.scenario)
                if (uid == owner) mutable.update { it.copy(historyLoading = false, history = h, chart = EarningsPremiumFormat.chart(h, it.metric), historyRows = EarningsPremiumFormat.rows(h)) }
            } catch (cause: Exception) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                val error = handle(cause, UpgradeReason.HISTORY, "Historical earnings couldn't be loaded.")
                if (uid == owner) mutable.update { it.copy(historyLoading = false, historyError = error) }
            }
        }
    }

    fun selectMetric(metric: HistoryMetric) = mutable.update { s -> s.copy(metric = metric, chart = s.history?.let { EarningsPremiumFormat.chart(it, metric) }) }
    fun toggleExpanded() = mutable.update { it.copy(expanded = !it.expanded) }
    fun dismissUpgrade() = mutable.update { it.copy(upgrade = null) }
    fun dismissSignIn() = mutable.update { it.copy(signInRequired = false) }
    /** Re-checks the plan (e.g. after returning from StockSteps+ settings). */
    fun refresh() { uid?.let { owner -> scope.launch { loadOverview(owner) } } }

    /** MOCK only: the demo scenario sent with premium requests; resets AI content so states don't mix. */
    fun setScenario(id: String?) {
        mutable.update { it.copy(scenario = id, explanation = null, explainError = null, history = null, chart = null, historyRows = emptyList(), historyError = null,
            conversation = emptyList(), conversationId = null, contextId = null, contextNote = null) }
        refresh()
    }
}

// ---------- Personalized digest ----------

data class EarningsDigestState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val digest: PersonalizedEarningsDigest? = null,
    val error: PremiumError? = null,
    val settings: EarningsDigestSettings? = null,
    val settingsError: PremiumError? = null,
    val saving: Boolean = false,
    val history: EarningsDigestHistory? = null,
    val aiLoading: Boolean = false,
    val aiError: PremiumError? = null,
    val upgrade: UpgradeReason? = null,
    val scenario: String? = null,
    val message: String? = null
) {
    val plus: Boolean get() = settings?.plus == true
    val preferences: EarningsDigestPreferences get() = settings?.preferences ?: EarningsDigestPreferences()
}

/**
 * "Your Earnings Digest" and its settings. The digest is built on the server from the user's own
 * watchlists (identity from the token); a free user sees the contextual upgrade, not someone else's data.
 */
class EarningsDigestPresenter(
    private val remote: EarningsPremiumRemote?,
    private val scope: CoroutineScope,
    private val session: Flow<String?>,
    private val loadDigest: Boolean = true
) {
    private val mutable = MutableStateFlow(EarningsDigestState())
    val state: StateFlow<EarningsDigestState> = mutable.asStateFlow()
    private var uid: String? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            session.distinctUntilChanged().collectLatest { id ->
                uid = id
                mutable.value = EarningsDigestState(signedIn = id != null && remote != null, scenario = mutable.value.scenario)
                if (id != null && remote != null) load(id)
            }
        }
    }

    private suspend fun load(owner: String) {
        val r = remote ?: return
        mutable.update { it.copy(loading = true, error = null, settingsError = null) }
        val settings = try { r.digestSettings() } catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            if (uid == owner) mutable.update { it.copy(loading = false, settingsError = PremiumCopy.error(cause, "Digest settings couldn't be loaded.")) }
            return
        }
        if (uid != owner) return
        mutable.update { it.copy(settings = settings) }
        if (!settings.plus || !loadDigest) { mutable.update { it.copy(loading = false) }; return }
        try {
            val d = r.digest(mutable.value.scenario)
            if (uid == owner) mutable.update { it.copy(loading = false, digest = d) }
        } catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            val e = PremiumCopy.error(cause, "Your earnings digest couldn't be loaded.")
            if (uid == owner) mutable.update { it.copy(loading = false, error = e, settings = if (e.code == "PLUS_REQUIRED") it.settings?.copy(plus = false) else it.settings) }
        }
    }

    fun refresh() { uid?.let { owner -> scope.launch { load(owner) } } }

    fun explain() {
        val r = remote ?: return
        val owner = uid ?: return
        if (!mutable.value.plus) { mutable.update { it.copy(upgrade = UpgradeReason.DIGEST) }; return }
        if (mutable.value.aiLoading) return
        scope.launch {
            mutable.update { it.copy(aiLoading = true, aiError = null) }
            try {
                val d = r.explainDigest(mutable.value.scenario)
                if (uid == owner) mutable.update { it.copy(aiLoading = false, digest = d) }
            } catch (cause: Exception) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                val e = PremiumCopy.error(cause, "The AI summary couldn't be created. Your digest is still available.")
                if (uid == owner) mutable.update { it.copy(aiLoading = false, aiError = e, upgrade = if (e.code == "PLUS_REQUIRED") UpgradeReason.DIGEST else it.upgrade) }
            }
        }
    }

    fun loadHistory() {
        val r = remote ?: return
        val owner = uid ?: return
        scope.launch {
            runCatching { r.digestHistory() }.onSuccess { h -> if (uid == owner) mutable.update { it.copy(history = h) } }
        }
    }

    /** Saves after the server confirms; turning the weekly digest on needs StockSteps+ (checked on the server too). */
    fun updatePreferences(transform: (EarningsDigestPreferences) -> EarningsDigestPreferences) {
        val r = remote ?: return
        val owner = uid ?: run { mutable.update { it.copy(message = "Sign in to set up your earnings digest.") }; return }
        val next = transform(mutable.value.preferences)
        if (next.cadence == DigestCadence.WEEKLY && !mutable.value.plus) { mutable.update { it.copy(upgrade = UpgradeReason.DIGEST) }; return }
        scope.launch {
            mutable.update { it.copy(saving = true, settingsError = null) }
            try {
                val s = r.saveDigestPreferences(next)
                if (uid == owner) mutable.update { it.copy(saving = false, settings = s) }
            } catch (cause: Exception) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                val e = PremiumCopy.error(cause, "Digest settings couldn't be saved. Check your connection and try again.")
                if (uid == owner) mutable.update { it.copy(saving = false, settingsError = e, upgrade = if (e.code == "PLUS_REQUIRED") UpgradeReason.DIGEST else it.upgrade) }
            }
        }
    }

    fun dismissUpgrade() = mutable.update { it.copy(upgrade = null) }
    fun dismissMessage() = mutable.update { it.copy(message = null) }
    fun setScenario(id: String?) { mutable.update { it.copy(scenario = id, digest = null) }; refresh() }
}
