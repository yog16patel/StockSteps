package org.example.stocksteps

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.stocksteps.data.account.*
import org.example.stocksteps.data.userdata.*
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.local.WatchlistDatabase
import org.example.stocksteps.model.*
import org.example.stocksteps.network.StockStepsApiException
import org.example.stocksteps.watchlist.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// App-owned native bridge. Native Firebase adapters implement only callback contracts.
data class AccountSnapshot(
    val session: AuthSession,
    val items: List<WatchlistItem>,
    val sync: WatchlistSyncState
)

/** Signed-in watchlists and alerts for SwiftUI (same repositories and rules as Android). */
data class UserDataSnapshot(
    val uid: String?,
    val watchlists: List<Watchlist>,
    val listsLoading: Boolean,
    val listsLoaded: Boolean,
    /** Showing the saved copy because the server couldn't be reached. */
    val offline: Boolean,
    val savedAt: Long?,
    val listsError: String?,
    val alerts: List<AlertRule>,
    val history: List<AlertEvent>,
    val alertsLoaded: Boolean,
    val alertsError: String?,
    val deliveryNote: String?
)

/** Outcome of creating an alert: CREATED, ALREADY_MET (ask the user) or FAILED, with a message. */
data class AlertCreateOutcome(val status: String, val message: String?)

/** This install's FCM token, set by Swift once notifications are allowed. */
private class IosPushTokens : PushTokenSource {
    val mutable = MutableStateFlow<String?>(null)
    override val token: StateFlow<String?> = mutable
    override val platform: String = "ios"
}

class IosAccountClient(auth: PlatformAuthGateway, baseUrl: () -> String, environment: String) {
    private val environmentFlow = MutableStateFlow(environment)
    private val pushTokens = IosPushTokens()
    private val dependencies = AccountDependencies(
        auth,
        NativeSqliteDriver(WatchlistDatabase.Schema, "stocksteps-watchlist.db"),
        baseUrl = baseUrl,
        environment = environmentFlow,
        pushTokens = pushTokens
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun observe(onChange: (AccountSnapshot) -> Unit): AccountSubscription {
        val job = scope.launch {
            combine(dependencies.auth.session, dependencies.watchlist.snapshot, dependencies.watchlist.syncState) { session, snapshot, sync ->
                AccountSnapshot(session, if (session.user?.id == snapshot.userId) snapshot.items else emptyList(), sync)
            }.collect(onChange)
        }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }

    fun observeUserData(onChange: (UserDataSnapshot) -> Unit): AccountSubscription {
        val job = scope.launch {
            combine(dependencies.auth.session, dependencies.watchlists.state, dependencies.alerts.state) { session, lists, alerts ->
                val uid = session.user?.id
                val mine = lists.uid == uid
                val myAlerts = alerts.uid == uid
                UserDataSnapshot(
                    uid = uid,
                    watchlists = if (mine) lists.value?.watchlists.orEmpty() else emptyList(),
                    listsLoading = mine && lists.loading,
                    listsLoaded = mine && lists.value != null,
                    offline = mine && lists.offline,
                    savedAt = if (mine) lists.savedAt else null,
                    listsError = if (mine) lists.error else null,
                    alerts = if (myAlerts) alerts.value?.alerts.orEmpty() else emptyList(),
                    history = if (myAlerts) alerts.value?.history.orEmpty() else emptyList(),
                    alertsLoaded = myAlerts && alerts.value != null,
                    alertsError = if (myAlerts) alerts.error else null,
                    deliveryNote = if (myAlerts) alerts.value?.deliveryNote else null
                )
            }.collect(onChange)
        }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }

    /** Called when the Development backend switch changes ("mock"/"real"). */
    fun setEnvironment(environment: String) { environmentFlow.value = environment }
    /** Called with the FCM token once notifications are allowed (null when they aren't). */
    fun setPushToken(token: String?) { pushTokens.mutable.value = token }
    val environment: String get() = environmentFlow.value

    @Throws(Exception::class)
    suspend fun signIn(email: String, password: String) = dependencies.signIn()(email, password)
    @Throws(Exception::class)
    suspend fun signUp(email: String, password: String) = dependencies.signUp()(email, password)
    @Throws(Exception::class)
    suspend fun signInWithGoogle() = dependencies.auth.signInWithGoogle()
    @Throws(Exception::class)
    suspend fun signOut() = dependencies.signOut()()
    @Throws(Exception::class)
    suspend fun addListing(stock: StockSearchResult) = dependencies.addToWatchlist()(stock)
    @Throws(Exception::class)
    suspend fun add(symbol: String) = dependencies.addToWatchlist()(symbol)
    @Throws(Exception::class)
    suspend fun remove(symbol: String) = dependencies.removeFromWatchlist()(symbol)
    fun retrySync() = dependencies.watchlist.retrySync()

    fun observePortfolio(onChange: (org.example.stocksteps.portfolio.PortfolioUiState) -> Unit): AccountSubscription {
        val job = scope.launch { dependencies.portfolioPresenter.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }
    fun showPortfolioScenario(id: String?) { dependencies.portfolioPresenter.showScenario(id) }
    /** Signed-in saved screens for the Screener (same repository as Android). */
    val savedScreens: org.example.stocksteps.screener.SavedScreensRepository get() = dependencies.savedScreens

    // Portfolio Intelligence: the same shared presenter as Android.
    fun observeInsights(onChange: (org.example.stocksteps.portfolio.analytics.InsightsUiState) -> Unit): AccountSubscription {
        val job = scope.launch { dependencies.insightsPresenter.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }
    fun selectInsightsAccount(id: String) { dependencies.insightsPresenter.selectAccount(id) }
    fun selectInsightsPeriod(label: String) { org.example.stocksteps.portfolio.analytics.AnalyticsPeriod.parse(label)?.let(dependencies.insightsPresenter::selectPeriod) }
    fun selectInsightsBenchmark(name: String) { org.example.stocksteps.portfolio.analytics.BenchmarkCatalog.parse(name)?.let(dependencies.insightsPresenter::selectBenchmark) }
    fun showInsightsScenario(id: String?) { dependencies.insightsPresenter.showScenario(id) }
    fun refreshInsights() { dependencies.insightsPresenter.refresh() }
    fun observeEntitlements(onChange: (org.example.stocksteps.portfolio.analytics.Entitlements?) -> Unit): AccountSubscription {
        val job = scope.launch { dependencies.entitlements.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }
    /** MOCK only (ignored otherwise): "FREE", "PLUS" or "EXPIRED". */
    fun simulatePlan(plan: String) {
        val tier = if (plan == "FREE") org.example.stocksteps.portfolio.analytics.SubscriptionTier.FREE else org.example.stocksteps.portfolio.analytics.SubscriptionTier.PLUS
        dependencies.insightsPresenter.simulatePlan(tier, expired = plan == "EXPIRED")
    }
    fun loadPortfolioHistory(range: String) { dependencies.portfolioPresenter.loadHistory(range) }
    fun refreshPortfolio() { dependencies.portfolioPresenter.refresh() }
    fun selectPortfolioAccount(id: String) { dependencies.portfolioPresenter.selectAccount(id) }
    fun savePortfolioAccount(id: String, name: String, category: String, currency: String, archived: Boolean) {
        dependencies.portfolioPresenter.saveAccount(org.example.stocksteps.portfolio.PortfolioAccount(
            id.ifBlank { org.example.stocksteps.portfolio.PortfolioPresenter.newId() }, name,
            org.example.stocksteps.portfolio.PortfolioCategory.valueOf(category),
            org.example.stocksteps.portfolio.PortfolioCurrency.valueOf(currency), archived))
    }
    fun deletePortfolioAccount(id: String) { dependencies.portfolioPresenter.deleteAccount(id) }
    fun deletePortfolioTransaction(id: String) { dependencies.portfolioPresenter.deleteTransaction(id) }
    fun savePortfolioTransaction(id: String, accountId: String, type: String, date: String, currency: String,
        symbol: String, name: String, exchange: String, quantity: String, price: String, amount: String, fees: String, notes: String, cashCurrency: String, fxRate: String, settlementDate: String, securityCurrency: String, edit: Boolean) {
        dependencies.portfolioPresenter.saveTransaction(org.example.stocksteps.portfolio.PortfolioTransaction(
            id, accountId, org.example.stocksteps.portfolio.TransactionType.valueOf(type), date,
            org.example.stocksteps.portfolio.PortfolioCurrency.valueOf(currency),
            symbol.takeIf { it.isNotBlank() }?.let { InstrumentRef(it, name.ifBlank { null }, exchange.ifBlank { null }, securityCurrency.ifBlank { currency }) },
            quantity.ifBlank { "0" }, price.ifBlank { "0" }, amount.ifBlank { "0" }, fees = fees.ifBlank { "0" }, notes = notes.ifBlank { null },
            cashCurrency = org.example.stocksteps.portfolio.PortfolioCurrency.valueOf(cashCurrency), fxRate = fxRate.ifBlank { null }, settlementDate = settlementDate.ifBlank { null }), edit)
    }

    fun observeHome(onChange: (org.example.stocksteps.home.PersonalDashboard) -> Unit): AccountSubscription {
        val job = scope.launch { dependencies.home.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }
    fun homeVisible() = dependencies.home.onVisible()
    fun refreshHome() = dependencies.home.refresh()
    fun retryHomeQuotes() = dependencies.home.retryQuotes()
    fun retryHomeNews() = dependencies.home.retryNews()
    fun retryHomeWatchlists() = dependencies.home.retryWatchlists()
    fun retryHomeAlerts() = dependencies.home.retryAlerts()
    fun clearRecentCompanies() = dependencies.home.clearRecent()
    fun showHomePersona(id: String) = dependencies.home.showMockPersona(id)
    fun useSavedHomeCompanies() = dependencies.home.useSavedCompanies()
    fun recordViewedCompany(symbol: String) = dependencies.home.recordViewed(InstrumentRef(symbol))

    // --- Watchlists (errors carry a user-facing message) ---
    suspend fun refreshUserData() { dependencies.watchlists.refresh(); dependencies.alerts.refresh() }
    @Throws(Exception::class) suspend fun createWatchlist(name: String) { dependencies.watchlists.create(name) }
    @Throws(Exception::class) suspend fun renameWatchlist(id: String, name: String) { dependencies.watchlists.rename(id, name) }
    @Throws(Exception::class) suspend fun deleteWatchlist(id: String) { dependencies.watchlists.delete(id) }
    @Throws(Exception::class) suspend fun addEntry(listId: String, instrument: InstrumentRef) { dependencies.watchlists.add(listId, instrument) }
    @Throws(Exception::class) suspend fun removeEntry(listId: String, entryId: String) { dependencies.watchlists.remove(listId, entryId) }
    @Throws(Exception::class) suspend fun saveNote(listId: String, entryId: String, note: String?) { dependencies.watchlists.note(listId, entryId, note) }
    @Throws(Exception::class) suspend fun moveEntry(listId: String, entryId: String, targetId: String, copy: Boolean) { dependencies.watchlists.move(listId, entryId, targetId, copy) }
    @Throws(Exception::class) suspend fun reorderEntries(listId: String, ids: List<String>) { dependencies.watchlists.reorder(listId, ids) }

    /** Quotes and earnings for the visible symbols; falls back to the saved copy offline. */
    @Throws(Exception::class)
    suspend fun watchData(symbols: List<String>): WatchDataRepository.Result {
        val uid = dependencies.auth.session.value.user?.id
        val owner = uid?.let { userCacheOwner(environmentFlow.value, it) } ?: "${environmentFlow.value}|guest"
        return dependencies.watchData.load(owner, symbols)
    }

    // --- Alerts ---
    suspend fun createAlert(request: CreateAlertRequest): AlertCreateOutcome = try {
        dependencies.alerts.create(request)
        AlertCreateOutcome("CREATED", null)
    } catch (cause: UserDataRequestException) {
        val code = (cause.cause as? StockStepsApiException)?.error?.code
        AlertCreateOutcome(if (code == "CONDITION_ALREADY_MET") "ALREADY_MET" else "FAILED", cause.message)
    } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        AlertCreateOutcome("FAILED", cause.message ?: "Couldn't create the alert.")
    }
    /** Builds a create request from form values (Swift can't use Kotlin default arguments). */
    fun alertRequest(instrument: InstrumentRef, type: AlertType, threshold: Double?, direction: MoveDirection?, timing: EarningsTiming?, repeatEveryCross: Boolean, choice: AlreadyMetChoice?): CreateAlertRequest {
        val price = type == AlertType.PRICE_ABOVE || type == AlertType.PRICE_BELOW
        return CreateAlertRequest(
            instrument = instrument, type = type,
            threshold = threshold.takeIf { price || type == AlertType.DAILY_MOVE },
            currency = if (price) instrument.currency ?: "USD" else null,
            direction = direction.takeIf { type == AlertType.DAILY_MOVE },
            earningsTiming = timing.takeIf { type == AlertType.EARNINGS },
            repeat = if (price) (if (repeatEveryCross) RepeatPolicy.REPEAT else RepeatPolicy.ONCE) else null,
            whenAlreadyMet = choice
        )
    }
    @Throws(Exception::class) suspend fun setAlertStatus(id: String, status: AlertStatus) { dependencies.alerts.update(id, UpdateAlertRequest(status = status)) }
    @Throws(Exception::class) suspend fun deleteAlert(id: String) { dependencies.alerts.delete(id) }

    // --- Shared presenters ---
    fun watchlistModel(lists: List<Watchlist>, selectedId: String?, data: WatchDataResponse?, alerts: List<AlertRule>, offlineSince: Long?): WatchlistModel =
        WatchlistPresenter.build(lists, selectedId, data, alerts, offlineSince)
    fun alertCards(alerts: List<AlertRule>, filter: AlertFilter): List<AlertCardModel> = AlertPresenter.cards(alerts, filter, EASTERN_OFFSET)
    fun alertHistory(events: List<AlertEvent>): List<AlertHistoryRow> = AlertPresenter.history(events, EASTERN_OFFSET)
    @OptIn(ExperimentalTime::class)
    fun now(): Long = Clock.System.now().toEpochMilliseconds()

    fun close() { scope.cancel(); dependencies.close() }

    private companion object { const val EASTERN_OFFSET = -240 }
}
