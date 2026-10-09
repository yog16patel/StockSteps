package org.example.stocksteps.di

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.example.stocksteps.createBackendClient
import org.example.stocksteps.data.SqlUserDataCache
import org.example.stocksteps.data.SqlWatchlistStore
import org.example.stocksteps.data.account.*
import org.example.stocksteps.data.userdata.*
import org.example.stocksteps.data.watchlist.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.presentation.account.AccountViewModel
import org.example.stocksteps.presentation.stocksearch.StockWatchlistViewModel
import kotlin.random.Random

/**
 * App-owned account graph. Signed-in watchlists, notes, alerts and push devices live on the
 * StockSteps backend (Mock or Real, chosen per request by [baseUrl]); the device keeps the guest
 * list and offline copies. Feature ViewModels never close it.
 */
class AccountDependencies(
    authGateway: PlatformAuthGateway,
    private val driver: SqlDriver,
    baseUrl: () -> String,
    /** "mock" or "real"; separates caches and re-registers the device when it changes. */
    environment: StateFlow<String>,
    pushTokens: PushTokenSource = NoPushTokens,
    clientFactory: () -> HttpClient = ::createBackendClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph = koinApplication {
        modules(module {
            single<HttpClient> { clientFactory() } onClose { it?.close() }
            single<AuthRepository> { DefaultAuthRepository(authGateway, scope) }
            single<WatchlistLocalStore> { SqlWatchlistStore(driver) }
            single<UserDataCache> { SqlUserDataCache(driver) }
            single { UserApi(get(), baseUrl, token = { refresh -> get<AuthRepository>().idToken(refresh) }, identity = { get<AuthRepository>().session.value.user?.id }) }
            single { StockStepsApi(get(), baseUrl) }
            single { UserWatchlistsRepository(get(), get(), get(), environment, scope).also { it.start() } }
            single { org.example.stocksteps.portfolio.PortfolioRepository(get(), get(), get(), environment, scope).also { it.start() } }
            single { AlertsRepository(get(), get(), get(), environment, scope).also { it.start() } }
            single { WatchDataRepository(get(), get()) }
            single {
                org.example.stocksteps.home.PersonalDashboardStore(
                    get(), get(), get(), get<WatchlistRepository>(), environment, get(), get(),
                    companyNews = { symbol -> get<StockStepsApi>().getCompanyNews(symbol, category = null, page = 0, limit = 10, enrich = false) },
                    scope = scope,
                    mockPersona = { id -> get<StockStepsApi>().getHomePersona(id) },
                    ownedSymbols = {
                        val ledger = get<org.example.stocksteps.portfolio.PortfolioRepository>().state.value.value
                        ledger?.accounts.orEmpty().flatMap { account ->
                            org.example.stocksteps.portfolio.PortfolioEngine.replay(ledger!!, account.id).holdings
                                .filter { org.example.stocksteps.portfolio.Decimal.parse(it.quantity) > org.example.stocksteps.portfolio.Decimal.ZERO }
                                .map { it.instrument.symbol.uppercase() }
                        }.toSet()
                    }
                )
            }
            single { org.example.stocksteps.portfolio.PortfolioPresenter(get(), get(), scope) }
            single { org.example.stocksteps.portfolio.analytics.EntitlementsRepository(get(), get(), environment, scope).also { it.start() } }
            single { org.example.stocksteps.portfolio.analytics.PortfolioAnalyticsPresenter(get(), get(), scope) }
            single { org.example.stocksteps.screener.SavedScreensRepository(get(), get(), get(), environment, scope).also { it.start() } }
            single { org.example.stocksteps.learning.LearningProgressRepository(get(), org.example.stocksteps.learning.RemoteLearningSync(get()), get(), environment, scope).also { it.start() } }
            single { org.example.stocksteps.brief.DailyBriefPresenter(org.example.stocksteps.brief.RemoteBrief(get(), get()), get(), get(), environment, scope) }
            single { org.example.stocksteps.practice.PracticePresenter(org.example.stocksteps.practice.RemotePractice(get()), get(), get(), environment, scope) }
            single { DeviceRegistrar(get(), get(), pushTokens, environment, { installId() }, scope).also { it.start() } }
            // The previous single list now only holds the signed-out guest list on this device.
            single<WatchlistRepository>(org.koin.core.qualifier.named("guest")) { OfflineWatchlistRepository(get(), get(), LocalOnlyWatchlistGateway, scope) }
            single<WatchlistRepository> { AccountWatchlistRepository(get(), get(org.koin.core.qualifier.named("guest")), get(), get(), scope) }
            factory { SignIn(get()) }
            factory { SignUp(get()) }
            factory { SignOut(get(), get(), get(), beforeSignOut = { get<DeviceRegistrar>().unregister() }) }
            factory { AddToWatchlist(get()) }
            factory { RemoveFromWatchlist(get()) }
            factory { AccountViewModel(get(), get(), get(), get()) }
            factory { StockWatchlistViewModel(get(), get(), get(), get(), get()) }
        })
    }
    val auth: AuthRepository = graph.koin.get()
    val watchlist: WatchlistRepository = graph.koin.get()
    val watchlists: UserWatchlistsRepository = graph.koin.get()
    val portfolioPresenter: org.example.stocksteps.portfolio.PortfolioPresenter = graph.koin.get()
    val portfolio: org.example.stocksteps.portfolio.PortfolioRepository = graph.koin.get()
    val entitlements: org.example.stocksteps.portfolio.analytics.EntitlementsRepository = graph.koin.get()
    val savedScreens: org.example.stocksteps.screener.SavedScreensRepository = graph.koin.get()
    /** Signed-in API (Firebase token per request), e.g. for tier-aware earnings. */
    val userApi: UserApi = graph.koin.get()
    /** Company Comparison Phase 4 research checklist (account-owned; resets when the signed-in user changes). */
    val comparisonResearch: org.example.stocksteps.screener.ComparisonResearchPresenter by lazy {
        org.example.stocksteps.screener.ComparisonResearchPresenter(org.example.stocksteps.screener.RemoteComparisonResearch(userApi), scope,
            auth.session.filter { !it.initializing }.map { it.user?.id })
    }
    /** Company Comparison Phase 5 AI assistant: one conversation per account and company selection (the server checks StockSteps+ and quotas). */
    val comparisonAi: org.example.stocksteps.screener.ComparisonAiPresenter by lazy {
        org.example.stocksteps.screener.ComparisonAiPresenter(org.example.stocksteps.screener.RemoteComparisonAi(userApi), scope,
            auth.session.filter { !it.initializing }.map { it.user?.id },
            org.example.stocksteps.screener.SharedComparisonSelection.instance.selected.map { list -> list.map { it.symbol } })
    }
    /** "uid|tier|status" for Company Comparison history: a sign-in, sign-out or plan change refetches (null = guest). */
    val comparisonAccount: kotlinx.coroutines.flow.Flow<String?> get() = kotlinx.coroutines.flow.combine(
        auth.session.map { it.user?.id }, entitlements.state) { uid, plan -> uid?.let { "$it|${plan?.tier}|${plan?.status}" } }
    /** Created on first use (Insights), not at app start. */
    val insightsPresenter: org.example.stocksteps.portfolio.analytics.PortfolioAnalyticsPresenter by lazy { graph.koin.get() }
    val alerts: AlertsRepository = graph.koin.get()
    /** Daily Market Brief: one presenter shared by Home, Markets, notifications and the reader. */
    val dailyBrief: org.example.stocksteps.brief.DailyBriefPresenter by lazy { graph.koin.get<org.example.stocksteps.brief.DailyBriefPresenter>().also { it.start() } }
    /** Practice Portfolio (simulated): one presenter shared by Home, Portfolio, Learn and Company Details; created on first use. */
    val practice: org.example.stocksteps.practice.PracticePresenter by lazy { graph.koin.get<org.example.stocksteps.practice.PracticePresenter>().also { it.start() } }
    fun practiceOrder(symbol: String, side: org.example.stocksteps.practice.OrderSide, scope: CoroutineScope): org.example.stocksteps.practice.PracticeOrderPresenter =
        org.example.stocksteps.practice.PracticeOrderPresenter(symbol, side, org.example.stocksteps.practice.RemotePractice(userApi), { auth.session.value.user?.id }, scope)
    /** Guided Research progress: on this device for guests, synced with the account when signed in. */
    val learning: org.example.stocksteps.learning.LearningProgressRepository = graph.koin.get()
    /** StockSteps+ research questions (the backend enforces the plan). */
    val researchAsk: org.example.stocksteps.learning.ResearchAsk = org.example.stocksteps.learning.ResearchAsk { symbol, question -> userApi.askResearch(symbol, question) }
    val watchData: WatchDataRepository = graph.koin.get()
    /** Earnings reminders (Phase 4): one account-wide presenter; the backend schedules and sends. */
    val earningsReminders: org.example.stocksteps.earnings.EarningsRemindersPresenter by lazy {
        org.example.stocksteps.earnings.EarningsRemindersPresenter(org.example.stocksteps.earnings.RemoteEarningsReminders(userApi), scope,
            auth.session.filter { !it.initializing }.map { it.user?.id }, { org.example.stocksteps.presentation.earnings.deviceTimeZoneId() }).also { it.start() }
    }
    /** Saved copies of opened earnings reports (public data), so Earnings Results can show them offline. */
    val earningsResultsCache: org.example.stocksteps.earnings.EarningsResultsCache by lazy {
        org.example.stocksteps.earnings.UserDataResultsCache(graph.koin.get()) { environment.value }
    }
    val devices: DeviceRegistrar = graph.koin.get()
    val environment: StateFlow<String> = environment
    val home: org.example.stocksteps.home.PersonalDashboardStore = graph.koin.get()
    internal fun accountViewModel(): AccountViewModel = graph.koin.get()
    internal fun stockWatchlistViewModel(): StockWatchlistViewModel = graph.koin.get()
    fun signIn(): SignIn = graph.koin.get()
    fun signUp(): SignUp = graph.koin.get()
    fun signOut(): SignOut = graph.koin.get()
    fun addToWatchlist(): AddToWatchlist = graph.koin.get()
    fun removeFromWatchlist(): RemoveFromWatchlist = graph.koin.get()

    /** A random id for this installation (not tied to the hardware), kept until the app is removed. */
    private suspend fun installId(): String {
        val cache = graph.koin.get<UserDataCache>()
        cache.read(INSTALL_OWNER, INSTALL_KEY)?.let { return it.first }
        val id = (1..24).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")
        cache.write(INSTALL_OWNER, INSTALL_KEY, id, 0)
        return id
    }

    fun close() {
        scope.cancel()
        graph.close()
        driver.close()
    }

    private companion object {
        const val INSTALL_OWNER = "device"
        const val INSTALL_KEY = "installId"
        const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
    }
}

/** Hosts without push (previews, tests): never registers a device. */
object NoPushTokens : PushTokenSource {
    override val token: StateFlow<String?> = MutableStateFlow(null)
    override val platform: String = "none"
}
