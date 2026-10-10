package org.example.stocksteps.home

import org.example.stocksteps.format.ReadableDates
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.stocksteps.data.userdata.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Account-owned aggregation of existing repositories, shared by Compose and native SwiftUI. */
@OptIn(ExperimentalTime::class)
class PersonalDashboardStore(
    private val auth: AuthRepository,
    private val watchlists: UserWatchlistsRepository,
    private val alerts: AlertsRepository,
    private val guest: WatchlistRepository,
    private val environment: StateFlow<String>,
    private val watchData: WatchDataRepository,
    private val cache: UserDataCache,
    private val companyNews: suspend (String) -> List<NewsArticle>,
    private val scope: CoroutineScope,
    private val mockPersona: (suspend (String) -> HomePersonaFixture)? = null,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val briefPolicy: DailyBriefPolicy = VerifiedDailyBrief(),
    /** Symbols with positive portfolio holdings (earnings for them come first; never inferred from watchlists). */
    private val ownedSymbols: () -> Set<String> = { emptySet() }
) {
    private val mutable = MutableStateFlow(PersonalDashboard())
    val state = mutable.asStateFlow()
    private var owner: String? = null
    private var instruments = emptyList<InstrumentRef>()
    private var savedInstruments = emptyList<InstrumentRef>()
    private var data: WatchDataResponse? = null
    private var offline = false
    private var quoteAt = 0L
    private var newsAt = 0L
    private var quotesJob: Job? = null
    private var newsJob: Job? = null
    private var personaJob: Job? = null
    private var previewId: String? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val recentSerializer = ListSerializer(RecentCompany.serializer())
    private val storiesSerializer = ListSerializer(HomeStory.serializer())

    init {
        scope.launch {
            combine(auth.session, environment) { session, env ->
                if (session.initializing) null else session.user?.id?.let { userCacheOwner(env, it) } ?: "$env|guest"
            }.distinctUntilChanged().collectLatest { next ->
                quotesJob?.cancel(); newsJob?.cancel(); personaJob?.cancel(); previewId = null
                owner = next
                instruments = emptyList(); savedInstruments = emptyList(); data = null; quoteAt = 0; newsAt = 0
                mutable.value = PersonalDashboard(initializing = next == null, watchlistLoading = auth.session.value.user != null)
                if (next == null) return@collectLatest
                val recent = cache.read(next, RECENT_KEY)?.first?.let {
                    runCatching { json.decodeFromString(recentSerializer, it) }.getOrNull()
                }.orEmpty()
                if (owner == next) mutable.update { it.copy(recent = recent.take(PersonalDashboardRules.MAX_RECENT)) }
                combine(watchlists.state, alerts.state, guest.snapshot) { lists, alertState, guestState ->
                    val session = auth.session.value
                    val env = environment.value
                    val uid = session.user?.id
                    val mine = lists.uid == uid && lists.environment == env
                    val items = if (uid == null) {
                        if (guestState.userId == null) guestState.items.map { InstrumentRef(it.symbol, it.name, it.exchange, it.currency) } else emptyList()
                    } else if (mine) PersonalDashboardRules.instruments(lists.value?.watchlists.orEmpty()) else emptyList()
                    Triple(items, lists.takeIf { mine && uid != null }, alertState)
                }.collect { (items, lists, _) ->
                    if (owner != next) return@collect
                    savedInstruments = items
                    if (previewId != null) return@collect
                    val restoringLists = auth.session.value.user != null &&
                        (lists == null || lists.loading || (lists.value == null && lists.error == null))
                    mutable.update { it.copy(watchlistCount = items.size, watchlistLoading = restoringLists, watchlistError = lists?.error) }
                    // Notes, alerts and cached sync flags do not re-fetch quotes or company feeds.
                    if (items != instruments) {
                        val symbolsChanged = items.map { it.symbol } != instruments.map { it.symbol }
                        instruments = items
                        if (symbolsChanged) {
                            quotesJob?.cancel(); newsJob?.cancel(); data = null; quoteAt = 0; newsAt = 0
                            mutable.update { it.copy(stories = emptyList(), newsError = null, quotesError = null, quoteNotice = null) }
                            loadQuotes(); loadNews()
                        }
                    }
                    render()
                }
            }
        }
    }

    /** Called on Home re-entry, never polls. Retry bypasses the section's TTL. */
    fun onVisible() {
        if (previewId != null) return
        if (owner == null) return
        if (now() - quoteAt >= PersonalDashboardRules.QUOTE_TTL_MS) loadQuotes()
        if (now() - newsAt >= PersonalDashboardRules.NEWS_TTL_MS) loadNews()
        if (auth.session.value.user != null) {
            val lists = watchlists.state.value
            val history = alerts.state.value
            scope.launch {
                if (!lists.loading && now() - (lists.savedAt ?: 0) >= PersonalDashboardRules.QUOTE_TTL_MS) watchlists.refresh()
            }
            scope.launch {
                if (!history.loading && now() - (history.savedAt ?: 0) >= PersonalDashboardRules.QUOTE_TTL_MS) alerts.refresh()
            }
        }
    }
    fun refresh() {
        previewId?.let { showMockPersona(it); return }
        loadQuotes(); loadNews(force = true)
        scope.launch { watchlists.refresh(); alerts.refresh() }
    }
    fun retryQuotes() {
        previewId?.let { showMockPersona(it); return }
        loadQuotes()
    }
    fun retryNews() {
        previewId?.let { showMockPersona(it); return }
        loadNews(force = true)
    }
    fun retryWatchlists() {
        previewId?.let { showMockPersona(it); return }
        scope.launch { watchlists.refresh() }
    }
    fun retryAlerts() { scope.launch { alerts.refresh() } }

    fun useSavedCompanies() {
        personaJob?.cancel(); previewId = null
        instruments = savedInstruments
        data = null; quoteAt = 0; newsAt = 0
        mutable.value = PersonalDashboard(initializing = false, watchlistCount = instruments.size)
        render(); loadQuotes(); loadNews()
        val key = owner ?: return
        scope.launch {
            val recent = cache.read(key, RECENT_KEY)?.first?.let {
                runCatching { json.decodeFromString(recentSerializer, it) }.getOrNull()
            }.orEmpty()
            if (owner == key && previewId == null) mutable.update { it.copy(recent = recent) }
        }
    }

    /** Development-only read-only previews. Backend route doesn't exist in REAL. */
    fun showMockPersona(id: String) {
        if (!environment.value.equals("mock", ignoreCase = true)) return
        val fetch = mockPersona ?: return
        val key = owner ?: return
        quotesJob?.cancel(); newsJob?.cancel(); personaJob?.cancel()
        previewId = id
        mutable.value = PersonalDashboard(initializing = true, mockPersona = id)
        personaJob = scope.launch {
            try {
                val fixture = fetch(id)
                ensureActive()
                if (owner == key && environment.value.equals("mock", true)) mutable.value = fixture.dashboard()
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                if (owner == key) mutable.value = PersonalDashboard(initializing = false, mockPersona = id,
                    watchlistError = "Could not load the sample scenario. Check the mock server.")
            }
        }
    }

    private fun loadQuotes() {
        val key = owner ?: return
        val symbols = (instruments.map { it.symbol } + ownedSymbols()).distinct()
        if (symbols.isEmpty()) {
            mutable.update { it.copy(quotesLoading = false, highlights = emptyList(), brief = emptyList(), events = emptyList()) }
            return
        }
        if (quotesJob?.isActive == true) return
        quotesJob = scope.launch {
            mutable.update { it.copy(quotesLoading = true, quotesError = null) }
            try {
                // Watch-data already batches quotes and earnings and supports offline snapshots.
                val responses = symbols.chunked(100).map { watchData.load(key, it) }
                ensureActive()
                if (owner != key) return@launch
                data = responses.first().data.copy(quotes = responses.flatMap { it.data.quotes }, earnings = responses.flatMap { it.data.earnings })
                offline = responses.any { it.fromCache }
                quoteAt = now()
                val missing = data?.quotes.orEmpty().count { it.price == null }
                mutable.update { it.copy(quotesLoading = false,
                    quotesError = if (missing > 0) "Some prices are unavailable. Try again." else null,
                    quoteNotice = if (offline) "Saved prices · offline" else data?.generatedAt?.let { "Prices updated ${ReadableDates.dateTime(it)}" }) }
                render()
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                if (owner == key) {
                    offline = data != null
                    mutable.update { it.copy(quotesLoading = false, quotesError = "Prices unavailable. Try again.",
                        quoteNotice = if (offline) "Saved prices · refresh failed" else null) }
                    render()
                }
            }
        }
    }

    private fun loadNews(force: Boolean = false) {
        val key = owner ?: return
        val symbols = instruments.map { it.symbol }
        if (symbols.isEmpty()) { mutable.update { it.copy(newsLoading = false) }; return }
        if (newsJob?.isActive == true) return
        newsJob = scope.launch {
            mutable.update { it.copy(newsLoading = true, newsError = null) }
            val cacheKey = "home.news:${symbols.sorted().joinToString(",")}"
            cache.read(key, cacheKey)?.let { (text, savedAt) ->
                val saved = runCatching { json.decodeFromString(storiesSerializer, text) }.getOrNull()
                if (saved != null && owner == key) {
                    mutable.update { it.copy(stories = saved, newsNotice = "Saved company news") }
                    if (!force && now() - savedAt in 0 until PersonalDashboardRules.NEWS_TTL_MS) {
                        newsAt = savedAt
                        mutable.update { it.copy(newsLoading = false) }
                        return@launch
                    }
                }
            }
            val feeds = mutableMapOf<String, List<NewsArticle>>()
            var failures = 0
            // Bounded concurrency, one company feed per watched symbol; no general news or AI call.
            symbols.chunked(NEWS_CONCURRENCY).forEach { batch ->
                coroutineScope {
                    batch.map { symbol -> async {
                        try { symbol to companyNews(symbol) } catch (cause: Exception) {
                            if (cause is CancellationException) throw cause
                            null
                        }
                    } }.awaitAll().forEach { result -> if (result == null) failures++ else feeds[result.first] = result.second }
                }
            }
            ensureActive()
            if (owner != key) return@launch
            newsAt = if (failures == 0) now() else 0L
            val stories = PersonalDashboardRules.stories(feeds, symbols.toSet())
            mutable.update { it.copy(stories = if (stories.isNotEmpty() || failures == 0) stories else it.stories, newsLoading = false,
                newsNotice = if (failures == 0) null else it.newsNotice,
                newsError = if (failures > 0) "Some company news could not be loaded. Try again." else null) }
            if (failures == 0) cache.write(key, cacheKey, json.encodeToString(storiesSerializer, stories), now())
        }
    }

    private fun render() {
        val key = owner ?: return
        val alertState = alerts.state.value
        val currentAlerts = alertState.takeIf { it.uid == auth.session.value.user?.id && it.environment == environment.value }
        val quotes = data?.quotes.orEmpty().map { if (offline) it.copy(stale = true) else it }
        val myHistory = currentAlerts?.value?.history.orEmpty()
        val personalSymbols = (instruments.map { it.symbol } + myHistory.map { it.symbol }).toSet()
        val events = PersonalDashboardRules.events(data?.earnings.orEmpty(), myHistory,
            personalSymbols, data?.session?.sessionDate ?: Clock.System.now().toString().take(10), now(), ownedSymbols())
        if (key == owner) mutable.update { it.copy(
            highlights = PersonalDashboardRules.highlights(instruments, quotes, offline),
            events = events,
            brief = briefPolicy.build(instruments, quotes, events),
            alertsError = currentAlerts?.error
        ) }
    }

    /** Record only when the Company Details destination actually opens, never a list impression. */
    fun recordViewed(instrument: InstrumentRef) {
        if (previewId != null) return
        val key = owner ?: return
        scope.launch {
            if (owner != key) return@launch
            val known = instruments.firstOrNull { it.symbol == instrument.symbol }
                ?: state.value.recent.firstOrNull { it.instrument.symbol == instrument.symbol }?.instrument
            val listing = instrument.copy(name = instrument.name ?: known?.name,
                exchange = instrument.exchange ?: known?.exchange, currency = instrument.currency ?: known?.currency)
            val recent = (listOf(RecentCompany(listing, now())) + state.value.recent)
                .distinctBy { it.instrument.symbol }.take(PersonalDashboardRules.MAX_RECENT)
            mutable.update { it.copy(recent = recent) }
            cache.write(key, RECENT_KEY, json.encodeToString(recentSerializer, recent), now())
        }
    }
    fun clearRecent() {
        if (previewId != null) {
            mutable.update { it.copy(recent = emptyList()) }
            return
        }
        val key = owner ?: return
        mutable.update { it.copy(recent = emptyList()) }
        scope.launch { cache.write(key, RECENT_KEY, "[]", now()) }
    }

    private companion object {
        const val RECENT_KEY = "home.recent"
        const val NEWS_CONCURRENCY = 3
    }
}
