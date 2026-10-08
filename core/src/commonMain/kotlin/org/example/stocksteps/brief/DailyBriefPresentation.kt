package org.example.stocksteps.brief

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.stocksteps.data.userdata.UserApi
import org.example.stocksteps.data.userdata.UserDataCache
import org.example.stocksteps.data.userdata.userCacheOwner
import org.example.stocksteps.data.userdata.userDataError
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.StockStepsApiException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Brief requests. Public content needs no account; [uid] calls name the expected owner. */
interface BriefRemote {
    suspend fun latest(scenario: String?): DailyBrief
    suspend fun brief(id: String, uid: String?): DailyBrief
    suspend fun history(uid: String?): BriefHistory
    suspend fun personalized(uid: String, id: String, scenario: String?): PersonalizedBrief
    suspend fun explain(uid: String, id: String, storyId: String, scenario: String?): BriefAiAnswer
    suspend fun ask(uid: String, id: String, question: String, scenario: String?): BriefAiAnswer
    suspend fun preferences(uid: String): BriefPreferences
    suspend fun savePreferences(uid: String, value: BriefPreferences): BriefPreferences
}

class RemoteBrief(private val api: StockStepsApi, private val user: UserApi) : BriefRemote {
    override suspend fun latest(scenario: String?) = api.getLatestBrief(scenario)
    override suspend fun brief(id: String, uid: String?) = if (uid == null) api.getBrief(id) else user.brief(id, uid)
    override suspend fun history(uid: String?) = if (uid == null) api.getBriefHistory() else user.briefHistory(uid)
    override suspend fun personalized(uid: String, id: String, scenario: String?) = user.personalizedBrief(id, scenario, uid)
    override suspend fun explain(uid: String, id: String, storyId: String, scenario: String?) = user.briefExplain(id, storyId, scenario, uid)
    override suspend fun ask(uid: String, id: String, question: String, scenario: String?) = user.briefAsk(id, question, scenario, uid)
    override suspend fun preferences(uid: String) = user.briefPreferences(uid)
    override suspend fun savePreferences(uid: String, value: BriefPreferences) = user.saveBriefPreferences(value, uid)
}

data class BriefAiState(val loading: Boolean = false, val answer: BriefAiAnswer? = null, val error: String? = null)

data class DailyBriefUiState(
    val uid: String? = null,
    /** The newest brief (Home and Markets preview). */
    val latest: DailyBrief? = null,
    /** The brief open in the reader (latest or an older one). */
    val current: DailyBrief? = null,
    val loading: Boolean = false,
    /** Showing a previously downloaded copy because the server can't be reached. */
    val offline: Boolean = false,
    val error: String? = null,
    val personal: PersonalizedBrief? = null,
    val personalLoading: Boolean = false,
    val personalError: String? = null,
    val history: BriefHistory? = null,
    /** Keyed by story id, or "ask" for a typed question. */
    val ai: Map<String, BriefAiState> = emptyMap(),
    /** A Free user asked for a StockSteps+ feature: show the upgrade preview (nothing was sent). */
    val upgrade: Boolean = false,
    val preferences: BriefPreferences? = null,
    val preferencesBusy: Boolean = false,
    val message: String? = null,
    /** MOCK scenario in use (developer/demo only). */
    val scenario: String? = null
) {
    val signedIn: Boolean get() = uid != null
    val plus: Boolean get() = personal?.access == BriefAccess.PLUS
}

/**
 * Daily Market Brief for Home, Markets and the reader. Public content is cached for offline reading
 * (only briefs this device has downloaded); personal overlays are cached per account and dropped on
 * account changes, so one user's watchlist never appears for another.
 */
@OptIn(ExperimentalTime::class)
class DailyBriefPresenter(
    private val remote: BriefRemote,
    private val auth: AuthRepository,
    private val cache: UserDataCache,
    private val environment: StateFlow<String>,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutable = MutableStateFlow(DailyBriefUiState())
    val state: StateFlow<DailyBriefUiState> = mutable.asStateFlow()
    private var started = false
    private var personalJob: Job? = null

    private fun publicOwner() = "${environment.value}|public"

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(auth.session.filter { !it.initializing }.map { it.user?.id }, environment) { uid, env -> uid to env }
                .distinctUntilChanged()
                .collectLatest { (uid, _) ->
                    // Public content stays; everything personal is cleared on an account change.
                    mutable.update { DailyBriefUiState(uid = uid, latest = it.latest, current = it.current, scenario = it.scenario) }
                    loadLatest()
                }
        }
    }

    private fun owner() = mutable.value.uid?.takeIf { auth.session.value.user?.id == it }
    private fun message(cause: Throwable) = userDataError(cause)
    private fun code(cause: Throwable) = (cause as? StockStepsApiException)?.error?.code

    private suspend fun cachedBriefs(): List<DailyBrief> = cache.read(publicOwner(), KEY)?.let { (text, _) ->
        runCatching { json.decodeFromString(ListSerializer(DailyBrief.serializer()), text) }.getOrNull()
    }.orEmpty()

    private suspend fun remember(brief: DailyBrief) {
        if (brief.id.contains("-mock-")) return
        val list = (listOf(brief) + cachedBriefs().filter { it.id != brief.id }).take(MAX_CACHED)
        cache.write(publicOwner(), KEY, json.encodeToString(ListSerializer(DailyBrief.serializer()), list), now())
    }

    /** Loads the newest brief (Home/Markets). Falls back to the last downloaded copy when offline. */
    fun loadLatest() {
        scope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val brief = remote.latest(mutable.value.scenario)
                mutable.update { s -> s.copy(latest = brief, current = if (s.current == null || s.current.id == s.latest?.id) brief else s.current, loading = false, offline = false) }
                remember(brief)
                loadPersonal()
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                val cached = cachedBriefs().firstOrNull()
                mutable.update { s -> s.copy(loading = false, offline = cached != null, latest = s.latest ?: cached, current = s.current ?: cached,
                    error = if (cached == null && s.latest == null) "The Daily Market Brief couldn't be loaded. Check your connection and try again." else message(cause)) }
            }
        }
    }

    /** Opens a brief in the reader: null for the latest, or an id (notifications, history). */
    fun open(id: String?) {
        mutable.update { it.copy(ai = emptyMap(), upgrade = false) }
        if (id == null || id == mutable.value.latest?.id) {
            mutable.update { it.copy(current = it.latest) }
            if (mutable.value.latest == null) loadLatest() else loadPersonal()
            return
        }
        val uid = owner()
        scope.launch {
            mutable.update { it.copy(loading = true, error = null, personal = null) }
            try {
                val brief = remote.brief(id, uid)
                mutable.update { it.copy(current = brief, loading = false, offline = false) }
                remember(brief)
                loadPersonal()
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                val cached = cachedBriefs().firstOrNull { it.id == id }
                when {
                    cached != null -> mutable.update { it.copy(current = cached, loading = false, offline = true) }
                    code(cause) == "HISTORY_LOCKED" -> mutable.update { it.copy(loading = false, upgrade = true, error = message(cause)) }
                    else -> mutable.update { it.copy(loading = false, error = "This brief isn't available right now. Previously opened briefs can be read offline.") }
                }
            }
        }
    }

    fun loadPersonal() {
        val uid = owner() ?: return
        val brief = mutable.value.current ?: return
        personalJob?.cancel()
        personalJob = scope.launch {
            mutable.update { it.copy(personalLoading = true, personalError = null) }
            try {
                val personal = remote.personalized(uid, brief.id, mutable.value.scenario)
                mutable.update { if (it.uid == uid && it.current?.id == brief.id) it.copy(personal = personal, personalLoading = false) else it }
                cache.write(userCacheOwner(environment.value, uid), PERSONAL_KEY, json.encodeToString(PersonalizedBrief.serializer(), personal), now())
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                val cached = cache.read(userCacheOwner(environment.value, uid), PERSONAL_KEY)?.let { (t, _) -> runCatching { json.decodeFromString(PersonalizedBrief.serializer(), t) }.getOrNull() }
                    ?.takeIf { it.briefId == brief.id }
                mutable.update { if (it.uid == uid) it.copy(personalLoading = false, personal = it.personal ?: cached, personalError = message(cause)) else it }
            }
        }
    }

    fun loadHistory() {
        val uid = owner()
        scope.launch {
            try {
                val history = remote.history(uid)
                mutable.update { if (it.uid == uid) it.copy(history = history) else it }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                val cached = cachedBriefs().map { it.summary() }
                mutable.update { it.copy(history = it.history ?: cached.takeIf { c -> c.isNotEmpty() }?.let { c -> BriefHistory(c, BriefAccess.ANONYMOUS) }, message = message(cause)) }
            }
        }
    }

    /** StockSteps+ only: Free users see the upgrade preview and nothing is sent. */
    fun explain(storyId: String) = runAi(storyId) { uid, id, scenario -> remote.explain(uid, id, storyId, scenario) }

    fun ask(question: String) {
        val text = question.trim()
        if (text.length < 3) return
        runAi("ask") { uid, id, scenario -> remote.ask(uid, id, text.take(300), scenario) }
    }

    private fun runAi(key: String, call: suspend (String, String, String?) -> BriefAiAnswer) {
        val s = mutable.value
        val uid = owner() ?: run { mutable.update { it.copy(message = "Sign in and subscribe to StockSteps+ for AI explanations.") }; return }
        val brief = s.current ?: return
        if (s.personal?.access != BriefAccess.PLUS) { mutable.update { it.copy(upgrade = true) }; return }
        if (s.ai[key]?.loading == true) return
        scope.launch {
            mutable.update { it.copy(ai = it.ai + (key to BriefAiState(loading = true))) }
            try {
                val answer = call(uid, brief.id, mutable.value.scenario)
                mutable.update { if (it.uid == uid) it.copy(ai = it.ai + (key to BriefAiState(answer = answer))) else it }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                mutable.update {
                    if (code(cause) == "PLUS_REQUIRED") it.copy(ai = it.ai - key, upgrade = true)
                    else it.copy(ai = it.ai + (key to BriefAiState(error = message(cause))))
                }
            }
        }
    }

    fun loadPreferences() {
        val uid = owner() ?: return
        scope.launch {
            try { val p = remote.preferences(uid); mutable.update { if (it.uid == uid) it.copy(preferences = p) else it } }
            catch (cause: Exception) { if (cause is CancellationException) throw cause; mutable.update { it.copy(message = message(cause)) } }
        }
    }

    fun savePreferences(value: BriefPreferences) {
        val uid = owner() ?: run { mutable.update { it.copy(message = "Sign in to get the Daily Market Brief notification.") }; return }
        scope.launch {
            mutable.update { it.copy(preferencesBusy = true) }
            try { val p = remote.savePreferences(uid, value); mutable.update { if (it.uid == uid) it.copy(preferences = p, preferencesBusy = false) else it } }
            catch (cause: Exception) { if (cause is CancellationException) throw cause; mutable.update { it.copy(preferencesBusy = false, message = message(cause)) } }
        }
    }

    fun dismissUpgrade() = mutable.update { it.copy(upgrade = false) }
    fun dismissMessage() = mutable.update { it.copy(message = null) }

    /** MOCK builds only: switch the sample scenario (the server refuses it in REAL). */
    fun scenario(name: String?) {
        mutable.update { it.copy(scenario = name, current = null, personal = null, ai = emptyMap()) }
        loadLatest()
    }

    companion object {
        const val KEY = "brief.public.v1"
        const val PERSONAL_KEY = "brief.personal.v1"
        const val MAX_CACHED = 5
        val SCENARIOS = listOf("normal", "pre-market", "market-hours", "weekend", "holiday", "us-open-ca-closed", "ca-open-us-closed", "one-story", "no-stories",
            "duplicate-stories", "missing-index", "stale-index", "missing-publisher", "invalid-url", "ai-unavailable", "ai-quota")
    }
}

/** Presentation formatting (labels never claim "today" for an older brief). */
@OptIn(ExperimentalTime::class)
object BriefFormat {
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    fun date(iso: String): String = runCatching { "${MONTHS[iso.substring(5, 7).toInt() - 1]} ${iso.substring(8, 10).toInt()}" }.getOrDefault(iso)

    /** "Updated 2 hr ago · After-close brief", or "Latest available · Oct 7" when older than 20 hours. */
    fun freshness(brief: DailyBrief, nowMillis: Long): String {
        val updated = runCatching { Instant.parse(brief.updatedAt).toEpochMilliseconds() }.getOrNull() ?: return "${brief.edition.label} · ${date(brief.briefDate)}"
        val minutes = ((nowMillis - updated) / 60_000).coerceAtLeast(0)
        return when {
            minutes > 20 * 60 -> "Latest available · ${date(brief.briefDate)}"
            minutes < 60 -> "${brief.edition.label} · Updated ${if (minutes < 2) "just now" else "$minutes min ago"}"
            else -> "${brief.edition.label} · Updated ${minutes / 60} hr ago"
        }
    }
    fun isStale(brief: DailyBrief, nowMillis: Long): Boolean = runCatching { nowMillis - Instant.parse(brief.updatedAt).toEpochMilliseconds() > 20 * 3_600_000L }.getOrDefault(true)

    fun value(i: BriefIndex): String = i.value?.let { v -> (if (i.isProxy) "${i.unit} " else "") + groupDigits(v) } ?: "Unavailable"
    fun change(i: BriefIndex): String {
        val c = i.change ?: return "Change unavailable"
        val p = i.changePercent
        val sign = if (c > 0) "+" else if (c < 0) "−" else ""
        return "$sign${groupDigits(kotlin.math.abs(c))}" + (p?.let { " (${if (it > 0) "+" else if (it < 0) "−" else ""}${BriefWording.pct(it)})" } ?: "")
    }
    /** Up, down or unchanged in words (never colour alone). */
    fun direction(i: BriefIndex): String = when { i.changePercent == null -> "Unavailable"; i.changePercent > 0.005 -> "Up"; i.changePercent < -0.005 -> "Down"; else -> "Unchanged" }
    fun sign(i: BriefIndex): Int = when { i.changePercent == null -> 0; i.changePercent > 0.005 -> 1; i.changePercent < -0.005 -> -1; else -> 0 }
    fun accessibility(i: BriefIndex): String = "${i.displayName}: ${value(i)}, ${direction(i)} ${change(i)}. ${i.stateLabel}."
    fun published(iso: String?): String? = iso?.takeIf { it.length >= 16 }?.let { "${date(it)} · ${it.substring(11, 16)} UTC" }
    fun groupDigits(v: Double): String {
        val cents = kotlin.math.round(v * 100).toLong()
        val whole = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$whole.${(cents % 100).toString().padStart(2, '0')}"
    }
}
