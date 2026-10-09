package org.example.stocksteps.practice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.example.stocksteps.data.userdata.UserApi
import org.example.stocksteps.data.userdata.UserDataCache
import org.example.stocksteps.data.userdata.userCacheOwner
import org.example.stocksteps.data.userdata.userDataError
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.learning.QuizEngine
import org.example.stocksteps.learning.QuizResult
import org.example.stocksteps.network.StockStepsApiException
import org.example.stocksteps.portfolio.Decimal
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Practice Portfolio requests for one signed-in user (every call names the expected owner). */
interface PracticeRemote {
    suspend fun overview(uid: String): PracticeOverview
    suspend fun transactions(uid: String, type: PracticeTransactionType?): PracticeTransactions
    suspend fun performance(uid: String, range: PracticeRange): PracticePerformance
    suspend fun preview(uid: String, request: PracticeOrderRequest): PracticeOrderPreview
    suspend fun execute(uid: String, request: PracticeOrderRequest): PracticeOrderResult
    suspend fun reset(uid: String, request: ResetRequest): PracticeOverview
    suspend fun activateTrial(uid: String): PracticeEntitlement
    suspend fun acknowledgeTrialNotice(uid: String): PracticeEntitlement
    suspend fun completeChallenge(uid: String, id: String, optionId: String): List<PracticeChallengeView>
    suspend fun scenario(uid: String, name: String): PracticeOverview
}

class RemotePractice(private val api: UserApi) : PracticeRemote {
    override suspend fun overview(uid: String) = api.practice(uid)
    override suspend fun transactions(uid: String, type: PracticeTransactionType?) = api.practiceTransactions(type?.name, uid)
    override suspend fun performance(uid: String, range: PracticeRange) = api.practicePerformance(range.label, uid)
    override suspend fun preview(uid: String, request: PracticeOrderRequest) = api.practicePreview(request, uid)
    override suspend fun execute(uid: String, request: PracticeOrderRequest) = api.practiceExecute(request, uid)
    override suspend fun reset(uid: String, request: ResetRequest) = api.practiceReset(request, uid)
    override suspend fun activateTrial(uid: String) = api.practiceActivateTrial(uid)
    override suspend fun acknowledgeTrialNotice(uid: String) = api.practiceAcknowledgeTrialNotice(uid)
    override suspend fun completeChallenge(uid: String, id: String, optionId: String) = api.practiceCompleteChallenge(id, optionId, uid)
    override suspend fun scenario(uid: String, name: String) = api.practiceScenario(name, uid)
}

/** A random request id for idempotent mutations (kept across retries of the same action). */
fun practiceRequestKey(random: Random = Random.Default): String = (1..24).map { "abcdefghijklmnopqrstuvwxyz0123456789"[random.nextInt(36)] }.joinToString("")

private fun errorCode(cause: Throwable): String? = (cause as? StockStepsApiException)?.error?.code
private fun message(cause: Throwable): String = userDataError(cause)

// ---------- Overview / holdings / activity / challenges ----------

enum class PracticeTab(val label: String) { OVERVIEW("Overview"), HOLDINGS("Holdings"), ACTIVITY("Activity"), CHALLENGES("Challenges") }

sealed interface PracticeSheet {
    /** Opening a new holding at the free limit. [trialOffered] is false once the trial was used. */
    data class HoldingLimit(val trialOffered: Boolean) : PracticeSheet
    /** A locked premium feature ([feature] names it). */
    data class Locked(val feature: String, val trialOffered: Boolean) : PracticeSheet
    data object TrialConfirm : PracticeSheet
    data object TrialExpired : PracticeSheet
    data object PlusInfo : PracticeSheet
    data object ResetConfirm : PracticeSheet
}

data class PracticeUiState(
    val uid: String? = null,
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val overview: PracticeOverview? = null,
    /** Showing the last saved copy because the server can't be reached; trading and plan changes are disabled. */
    val offline: Boolean = false,
    val error: String? = null,
    val tab: PracticeTab = PracticeTab.OVERVIEW,
    val range: PracticeRange = PracticeRange.MONTH,
    val performance: PracticePerformance? = null,
    val performanceError: String? = null,
    val filter: PracticeTransactionType? = null,
    val transactions: List<PracticeTransaction>? = null,
    val sheet: PracticeSheet? = null,
    val busy: Boolean = false,
    val message: String? = null,
    /** Local feedback for challenge questions (the server records only completed ones). */
    val challengeAnswers: Map<String, QuizResult> = emptyMap()
) {
    val entitlement: PracticeEntitlement? get() = overview?.entitlement
    val canTrade: Boolean get() = signedIn && overview != null && !offline
    /** "2 of 3 holdings used", "Trial ends in 10 days", "StockSteps+". */
    val accessLabel: String? get() = entitlement?.let { e ->
        when (e.access) {
            PracticeAccess.FREE -> "${overview!!.openHoldings} of ${e.maxOpenHoldings} free holdings used"
            PracticeAccess.TRIAL -> e.trialDaysLeft?.let { if (it <= 1) "Practice trial ends today" else "$it days left in your Practice trial" }
            PracticeAccess.PLUS -> "StockSteps+"
        }
    }
    val atFreeLimit: Boolean get() = entitlement?.maxOpenHoldings?.let { (overview?.openHoldings ?: 0) >= it } == true
}

@OptIn(ExperimentalTime::class)
class PracticePresenter(
    private val remote: PracticeRemote,
    private val auth: AuthRepository,
    private val cache: UserDataCache,
    private val environment: StateFlow<String>,
    private val scope: CoroutineScope,
    private val newKey: () -> String = { practiceRequestKey() },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutable = MutableStateFlow(PracticeUiState())
    val state: StateFlow<PracticeUiState> = mutable.asStateFlow()
    private var started = false
    private var performanceJob: Job? = null
    private var transactionsJob: Job? = null
    private var resetKey: String? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(auth.session.filter { !it.initializing }.map { it.user?.id }, environment) { uid, env -> uid to env }
                .distinctUntilChanged()
                .collectLatest { (uid, env) ->
                    // A new account or environment never shows the previous one's portfolio.
                    mutable.value = PracticeUiState(uid = uid, signedIn = uid != null)
                    if (uid == null) return@collectLatest
                    cache.read(userCacheOwner(env, uid), KEY)?.let { (text, _) ->
                        runCatching { json.decodeFromString(PracticeOverview.serializer(), text) }.getOrNull()?.let { cached ->
                            mutable.update { if (it.uid == uid) it.copy(overview = cached, offline = true) else it }
                        }
                    }
                    refresh()
                }
        }
    }

    private fun owner(): String? = mutable.value.uid?.takeIf { auth.session.value.user?.id == it }

    private fun apply(uid: String, overview: PracticeOverview) {
        mutable.update { if (it.uid != uid) it else it.copy(overview = overview, offline = false, loading = false, error = null,
            sheet = it.sheet ?: if (overview.showTrialExpiredNotice) PracticeSheet.TrialExpired else null) }
        scope.launch { cache.write(userCacheOwner(environment.value, uid), KEY, json.encodeToString(PracticeOverview.serializer(), overview), now()) }
    }

    fun refresh() {
        val uid = owner() ?: return
        scope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                apply(uid, remote.overview(uid))
                when (mutable.value.tab) {
                    PracticeTab.ACTIVITY -> loadTransactions()
                    PracticeTab.OVERVIEW -> loadPerformance()
                    else -> Unit
                }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { if (it.uid == uid) it.copy(loading = false, offline = it.overview != null, error = message(cause)) else it }
            }
        }
    }

    fun selectTab(tab: PracticeTab) {
        mutable.update { it.copy(tab = tab) }
        when (tab) {
            PracticeTab.ACTIVITY -> loadTransactions()
            PracticeTab.OVERVIEW -> if (mutable.value.performance == null) loadPerformance()
            else -> Unit
        }
    }

    /** Locked ranges open the explanation of what the trial and StockSteps+ include. */
    fun selectRange(range: PracticeRange) {
        val e = mutable.value.entitlement ?: return
        if (range !in PracticePolicy.ranges(e.access)) { mutable.update { it.copy(sheet = PracticeSheet.Locked("Longer performance history", e.trialEligible)) }; return }
        mutable.update { it.copy(range = range) }
        loadPerformance()
    }

    fun loadPerformance() {
        val uid = owner() ?: return
        val range = mutable.value.range
        performanceJob?.cancel()
        performanceJob = scope.launch {
            try {
                val result = remote.performance(uid, range)
                mutable.update { if (it.uid == uid && it.range == range) it.copy(performance = result, performanceError = null) else it }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { if (it.uid == uid) it.copy(performanceError = message(cause)) else it }
            }
        }
    }

    fun filter(type: PracticeTransactionType?) {
        mutable.update { it.copy(filter = type) }
        loadTransactions()
    }

    private fun loadTransactions() {
        val uid = owner() ?: return
        val type = mutable.value.filter
        transactionsJob?.cancel()
        transactionsJob = scope.launch {
            try {
                val items = remote.transactions(uid, type).items
                mutable.update { if (it.uid == uid && it.filter == type) it.copy(transactions = items) else it }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { if (it.uid == uid) it.copy(message = message(cause)) else it }
            }
        }
    }

    fun showHoldingLimit() = mutable.update { it.copy(sheet = PracticeSheet.HoldingLimit(it.entitlement?.trialEligible == true)) }
    fun showLocked(feature: String) = mutable.update { it.copy(sheet = PracticeSheet.Locked(feature, it.entitlement?.trialEligible == true)) }
    fun showTrialConfirm() = mutable.update { it.copy(sheet = if (it.entitlement?.trialEligible == true) PracticeSheet.TrialConfirm else PracticeSheet.PlusInfo) }
    fun showPlus() = mutable.update { it.copy(sheet = PracticeSheet.PlusInfo) }
    fun showReset() = mutable.update { it.copy(sheet = PracticeSheet.ResetConfirm) }
    fun dismissMessage() = mutable.update { it.copy(message = null) }

    fun dismissSheet() {
        val sheet = mutable.value.sheet
        mutable.update { it.copy(sheet = null) }
        if (sheet == PracticeSheet.TrialExpired) acknowledgeTrialNotice()
    }

    private fun acknowledgeTrialNotice() {
        val uid = owner() ?: return
        mutable.update { s -> s.copy(overview = s.overview?.copy(showTrialExpiredNotice = false)) }
        scope.launch { runCatching { remote.acknowledgeTrialNotice(uid) } }
    }

    /** Explicit user confirmation only; the server records the dates and never restarts a trial. */
    fun startTrial() {
        val uid = owner() ?: return
        if (mutable.value.offline || mutable.value.busy) return
        scope.launch {
            mutable.update { it.copy(busy = true) }
            try {
                val entitlement = remote.activateTrial(uid)
                mutable.update { s -> if (s.uid != uid) s else s.copy(busy = false, sheet = null, overview = s.overview?.copy(entitlement = entitlement),
                    message = entitlement.trialEndsAt?.let { "Your Practice trial is active until ${PracticeFormat.date(it)}." }) }
                refresh()
                if (mutable.value.range != PracticeRange.MONTH) loadPerformance()
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { if (it.uid == uid) it.copy(busy = false, message = message(cause)) else it }
            }
        }
    }

    fun reset() {
        val uid = owner() ?: return
        if (mutable.value.offline || mutable.value.busy) return
        val key = resetKey ?: newKey().also { resetKey = it }
        scope.launch {
            mutable.update { it.copy(busy = true) }
            try {
                val overview = remote.reset(uid, ResetRequest(true, key))
                resetKey = null
                apply(uid, overview)
                mutable.update { it.copy(busy = false, sheet = null, performance = null, transactions = null, message = "Your Practice Portfolio was reset to ${PracticeEngine.format(Decimal.parse(overview.startingCash), overview.baseCurrency)}.") }
                loadPerformance()
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                // The key is kept, so retrying after a lost response can't reset twice.
                mutable.update { if (it.uid == uid) it.copy(busy = false, message = message(cause)) else it }
            }
        }
    }

    fun answerChallenge(challengeId: String, optionId: String) {
        val uid = owner() ?: return
        val challenge = mutable.value.overview?.challenges?.firstOrNull { it.id == challengeId } ?: return
        val quiz = challenge.quiz ?: return
        if (!challenge.available) { showLocked("Guided practice challenges"); return }
        val result = QuizEngine.answer(quiz, optionId)
        mutable.update { it.copy(challengeAnswers = it.challengeAnswers + (challengeId to result)) }
        if (challenge.kind == ChallengeKind.QUIZ && !result.correct) return
        scope.launch {
            try {
                val views = remote.completeChallenge(uid, challengeId, optionId)
                mutable.update { s -> if (s.uid == uid) s.copy(overview = s.overview?.copy(challenges = views)) else s }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update { if (it.uid == uid) it.copy(message = message(cause)) else it }
            }
        }
    }

    fun retryChallenge(challengeId: String) = mutable.update { it.copy(challengeAnswers = it.challengeAnswers - challengeId) }

    /** MOCK builds only (the route doesn't exist on the REAL server). */
    fun loadScenario(name: String) {
        val uid = owner() ?: return
        scope.launch {
            try { apply(uid, remote.scenario(uid, name)); mutable.update { it.copy(performance = null, transactions = null) }; loadPerformance() }
            catch (cause: Exception) { if (cause is CancellationException) throw cause; mutable.update { it.copy(message = message(cause)) } }
        }
    }

    companion object { const val KEY = "practice.overview.v1" }
}

// ---------- Order flow ----------

enum class OrderStep { ENTRY, REVIEW, DONE }
enum class OrderInput { SHARES, AMOUNT }

data class PracticeOrderState(
    val symbol: String,
    val side: OrderSide,
    val mode: OrderInput = OrderInput.SHARES,
    val input: String = "",
    val preview: PracticeOrderPreview? = null,
    val previewing: Boolean = false,
    val step: OrderStep = OrderStep.ENTRY,
    val executing: Boolean = false,
    val result: PracticeOrderResult? = null,
    val error: String? = null,
    /** The order would open a holding beyond the free limit: show the upgrade options. */
    val holdingLimit: Boolean = false,
    val notice: String? = null
) {
    val blocker: PracticeBlocker? get() = preview?.blocker
    val canReview: Boolean get() = preview != null && preview.canExecute && !previewing && preview.quantity != "0"
}

/**
 * Entry → Review → Confirmation for one simulated order. Previews are indicative; the server prices
 * the fill. One idempotency key per reviewed order is reused on retry, so a lost response or a double
 * tap never fills twice.
 */
class PracticeOrderPresenter(
    symbol: String,
    side: OrderSide,
    private val remote: PracticeRemote,
    private val uid: () -> String?,
    private val scope: CoroutineScope,
    private val newKey: () -> String = { practiceRequestKey() },
    private val debounceMillis: Long = 350
) {
    private val mutable = MutableStateFlow(PracticeOrderState(symbol.uppercase(), side))
    val state: StateFlow<PracticeOrderState> = mutable.asStateFlow()
    private var previewJob: Job? = null
    private var key: String? = null

    private fun request(s: PracticeOrderState, withKey: Boolean = false) = PracticeOrderRequest(s.symbol, s.side,
        quantity = s.input.trim().takeIf { s.mode == OrderInput.SHARES },
        amount = s.input.trim().takeIf { s.mode == OrderInput.AMOUNT },
        idempotencyKey = if (withKey) key else null,
        reviewedPrice = if (withKey) s.preview?.price else null)

    fun setMode(mode: OrderInput) {
        if (mode == OrderInput.AMOUNT && mutable.value.side == OrderSide.SELL) return
        mutable.update { it.copy(mode = mode, input = "", preview = null, error = null) }
    }

    fun setInput(text: String) {
        val clean = text.filter { it.isDigit() || it == '.' }.take(14)
        mutable.update { it.copy(input = clean, error = null, holdingLimit = false) }
        schedulePreview()
    }

    /** Loads a price as soon as the screen opens (one share) so the user sees it before typing. */
    fun start() { if (mutable.value.preview == null) refreshPreview(initial = true) }

    private fun schedulePreview() {
        previewJob?.cancel()
        val s = mutable.value
        if (s.input.isBlank() || runCatching { Decimal.parse(s.input) <= Decimal.ZERO }.getOrDefault(true)) {
            mutable.update { it.copy(preview = it.preview?.takeIf { p -> p.quantity == "0" }, previewing = false) }
            return
        }
        previewJob = scope.launch { delay(debounceMillis); load(request(mutable.value)) }
    }

    fun refreshPreview(initial: Boolean = false) {
        previewJob?.cancel()
        val s = mutable.value
        val req = if (initial || s.input.isBlank()) PracticeOrderRequest(s.symbol, s.side, quantity = "1") else request(s)
        previewJob = scope.launch { load(req, priceOnly = initial || s.input.isBlank()) }
    }

    private suspend fun load(req: PracticeOrderRequest, priceOnly: Boolean = false) {
        val owner = uid() ?: run { mutable.update { it.copy(error = "Sign in to use the Practice Portfolio.") }; return }
        mutable.update { it.copy(previewing = true) }
        try {
            val p = remote.preview(owner, req)
            // A price-only preview shows the quote but isn't an order yet.
            mutable.update { it.copy(previewing = false, preview = if (priceOnly) p.copy(quantity = "0", estimatedTotal = "0", cashAfter = p.cashAvailable,
                blocker = p.blocker?.takeUnless { b -> b.code in setOf("INSUFFICIENT_CASH", "INVALID_QUANTITY", "INSUFFICIENT_SHARES") }) else p,
                holdingLimit = p.blocker?.code == "HOLDING_LIMIT") }
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            mutable.update { it.copy(previewing = false, error = message(cause)) }
        }
    }

    fun review() {
        val s = mutable.value
        if (!s.canReview) { if (s.blocker?.code == "HOLDING_LIMIT") mutable.update { it.copy(holdingLimit = true) }; return }
        key = newKey()
        mutable.update { it.copy(step = OrderStep.REVIEW, notice = null) }
    }

    fun back() = mutable.update { if (it.step == OrderStep.REVIEW) it.copy(step = OrderStep.ENTRY) else it }

    fun confirm() {
        val s = mutable.value
        if (s.step != OrderStep.REVIEW || s.executing) return
        val owner = uid() ?: return
        val req = request(s, withKey = true)
        scope.launch {
            mutable.update { it.copy(executing = true, error = null) }
            try {
                val result = remote.execute(owner, req)
                mutable.update { it.copy(executing = false, step = OrderStep.DONE, result = result) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                when (errorCode(cause)) {
                    "PRICE_CHANGED" -> {
                        key = null
                        mutable.update { it.copy(executing = false, step = OrderStep.ENTRY, notice = message(cause)) }
                        refreshPreview()
                    }
                    "HOLDING_LIMIT" -> mutable.update { it.copy(executing = false, step = OrderStep.ENTRY, holdingLimit = true, error = message(cause)) }
                    null -> mutable.update { it.copy(executing = false, error = "${message(cause)} Your order wasn't confirmed; trying again won't place it twice.") }
                    else -> { key = null; mutable.update { it.copy(executing = false, step = OrderStep.ENTRY, error = message(cause)) }; refreshPreview() }
                }
            }
        }
    }

    fun dismissHoldingLimit() = mutable.update { it.copy(holdingLimit = false) }
}

/** Presentation formatting (never used for calculations). */
object PracticeFormat {
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    fun money(value: String?, currency: String): String = value?.let { runCatching { PracticeEngine.format(Decimal.parse(it), currency) }.getOrNull() } ?: "—"
    fun signedMoney(value: String?, currency: String): String = value?.let { v -> runCatching { Decimal.parse(v) }.getOrNull()?.let {
        (if (it > Decimal.ZERO) "+" else "") + PracticeEngine.format(it, currency) } } ?: "—"
    fun price(value: String?, currency: String): String = value?.let { v -> runCatching { PracticeEngine.symbol(currency) + Decimal.parse(v).display(2) }.getOrNull() } ?: "—"
    fun percent(value: String?): String = value?.let { v -> runCatching { Decimal.parse(v) }.getOrNull()?.let { (if (it > Decimal.ZERO) "+" else if (it < Decimal.ZERO) "−" else "") + (if (it < Decimal.ZERO) -it else it).display(2) + "%" } } ?: "—"
    fun shares(value: String): String = runCatching { Decimal.parse(value).toString() }.getOrDefault(value)
    /** "1 share", "2 shares", "0.5 shares": the quantity with its noun, for every Practice label on both platforms. */
    fun shareCount(value: String): String =
        shares(value) + if (runCatching { Decimal.parse(value).compareTo(Decimal.parse("1")) == 0 }.getOrDefault(false)) " share" else " shares"
    /** Gain/loss in words, so it never relies on colour alone. */
    fun direction(value: String?): String = value?.let { runCatching { Decimal.parse(it) }.getOrNull() }?.let { if (it > Decimal.ZERO) "Gain" else if (it < Decimal.ZERO) "Loss" else "No change" } ?: "Unavailable"
    /**
     * The screen-reader sentence for a holding row, shared by Android and iOS so both read the same facts (including the stale-price note).
     * Names that already end with a period ("Apple Inc.") don't get a second one; a missing name is left out instead of reading ", .".
     */
    fun holdingDescription(h: PracticeHoldingView, currency: String): String {
        val name = h.instrument.name?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
        return listOfNotNull(h.instrument.symbol, name).joinToString(", ") + ". ${shareCount(h.quantity)}. Value ${money(h.marketValue, currency)}. " +
            "${direction(h.unrealizedGain)} ${signedMoney(h.unrealizedGain, currency)}." + if (h.stale) " Price from an earlier session." else ""
    }
    fun positive(value: String?): Boolean? = value?.let { runCatching { Decimal.parse(it) }.getOrNull() }?.let { if (it == Decimal.ZERO) null else it > Decimal.ZERO }
    @OptIn(ExperimentalTime::class)
    fun date(millis: Long): String {
        val text = kotlin.time.Instant.fromEpochMilliseconds(millis).toString()
        return runCatching { "${MONTHS[text.substring(5, 7).toInt() - 1]} ${text.substring(8, 10).toInt()}, ${text.take(4)}" }.getOrDefault(text.take(10))
    }
    @OptIn(ExperimentalTime::class)
    fun dateTime(millis: Long): String {
        val text = kotlin.time.Instant.fromEpochMilliseconds(millis).toString()
        return "${date(millis)} · ${text.substring(11, 16)} UTC"
    }
    fun isoDate(iso: String?): String? = iso?.let { runCatching { "${MONTHS[it.substring(5, 7).toInt() - 1]} ${it.substring(8, 10).toInt()}, ${it.take(4)}" + if (it.length > 15) " · ${it.substring(11, 16)} UTC" else "" }.getOrNull() }
    fun type(type: PracticeTransactionType): String = when (type) {
        PracticeTransactionType.BUY -> "Practice buy"; PracticeTransactionType.SELL -> "Practice sell"
        PracticeTransactionType.DIVIDEND -> "Simulated dividend"; PracticeTransactionType.SPLIT_ADJUSTMENT -> "Stock split adjustment"
    }
}
