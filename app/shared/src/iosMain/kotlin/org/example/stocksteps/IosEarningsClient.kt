package org.example.stocksteps

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.example.stocksteps.data.account.AccountSubscription
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.earnings.*

/**
 * Earnings Calendar, Earnings Event Details and Earnings Details for SwiftUI: the same shared
 * presenters, watchlist identity (existing repository), reminders (existing alerts) and backend
 * contract as Android. `baseUrl` is read per request (Mock/Real switch). Each screen's presenter
 * runs in its own child scope, cancelled by [release] when the screen goes away.
 */
class IosEarningsClient(baseUrl: () -> String, account: IosAccountClient?) {
    private val dependencies = StockStepsDependencies(baseUrl)
    private val accounts = account?.dependenciesForEarnings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val remote = dependencies.earningsRemote(accounts)
    private val session = accounts?.watchlists?.earningsSession() ?: flowOf(null)
    private val watched = accounts?.watchlists?.watchedSymbols() ?: flowOf(emptySet())
    private val jobs = HashMap<Any, Job>()

    private fun child(): CoroutineScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private fun <T : Any> owned(make: (CoroutineScope) -> T): T { val s = child(); return make(s).also { jobs[it] = s.coroutineContext[Job]!! } }
    private fun <T> observe(flow: kotlinx.coroutines.flow.StateFlow<T>, onChange: (T) -> Unit): AccountSubscription {
        val job = scope.launch { flow.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }
    /** Stops a presenter created by this client. */
    fun release(presenter: Any) { jobs.remove(presenter)?.cancel() }

    // Earnings Calendar (date "yyyy-MM-dd" or empty for today; filter "ALL" | "WATCHLIST").
    fun calendar(date: String?, filter: String?): EarningsCalendarPresenter = owned { s ->
        EarningsCalendarPresenter(remote, s, session, watched,
            initial = CalendarSelection(date.orEmpty(), filter = CalendarFilter.entries.firstOrNull { it.name == filter } ?: CalendarFilter.ALL)).also { it.start() }
    }
    fun observeCalendar(presenter: EarningsCalendarPresenter, onChange: (EarningsCalendarState) -> Unit) = observe(presenter.state, onChange)
    fun selectTab(presenter: EarningsCalendarPresenter, name: String) = CalendarTab.entries.firstOrNull { it.name == name }?.let(presenter::selectTab)
    fun selectFilter(presenter: EarningsCalendarPresenter, name: String) = CalendarFilter.entries.firstOrNull { it.name == name }?.let(presenter::selectFilter)
    fun selectMode(presenter: EarningsCalendarPresenter, name: String) = CalendarMode.entries.firstOrNull { it.name == name }?.let(presenter::selectMode)
    /** MOCK-only demo scenarios: (id or "" for normal, label). */
    val scenarios: List<List<String>> = listOf(listOf("", "Normal"), listOf("stale-cache", "Stale cached data"), listOf("provider-unavailable", "Provider unavailable"))
    fun setScenario(presenter: EarningsCalendarPresenter, id: String) = presenter.setScenario(id.ifEmpty { null })

    // Earnings Event Details.
    fun event(id: String): EarningsEventPresenter = owned { s -> EarningsEventPresenter(id, remote, s) }
    fun observeEvent(presenter: EarningsEventPresenter, onChange: (EarningsEventState) -> Unit) = observe(presenter.state, onChange)

    // Company Details: next earnings date.
    fun company(symbol: String): CompanyEarningsPresenter = owned { s -> CompanyEarningsPresenter(symbol, remote, s) }
    fun observeCompany(presenter: CompanyEarningsPresenter, onChange: (CompanyEarningsState) -> Unit) = observe(presenter.state, onChange)

    // Markets: Earnings Center entry counts (created on first use).
    val summary: EarningsSummaryPresenter by lazy { EarningsSummaryPresenter(remote, scope, session) }
    fun observeSummary(onChange: (EarningsSummaryState) -> Unit) = observe(summary.state, onChange)

    // Earnings Results (Phase 2): server-calculated, saved on the device for offline viewing.
    fun results(reportId: String): EarningsResultsPresenter = owned { s -> EarningsResultsPresenter(reportId, remote, s, accounts?.earningsResultsCache) }
    fun observeResults(presenter: EarningsResultsPresenter, onChange: (EarningsResultsState) -> Unit) = observe(presenter.state, onChange)
    // Price reaction (Phase 3): server-calculated per window.
    fun reaction(reportId: String): EarningsPriceReactionPresenter = owned { s -> EarningsPriceReactionPresenter(reportId, remote, s, accounts?.earningsResultsCache) }
    fun observeReaction(presenter: EarningsPriceReactionPresenter, onChange: (EarningsPriceReactionState) -> Unit) = observe(presenter.state, onChange)
    fun selectWindow(presenter: EarningsPriceReactionPresenter, name: String) = ReactionWindow.entries.firstOrNull { it.name == name }?.let(presenter::selectWindow)
    val windows: List<ReactionWindow> get() = ReactionWindow.entries
    fun symbolOf(reportId: String): String = EarningsReportMapper.parse(reportId)?.first ?: reportId.substringBefore(':')

    // Earnings Details (results and history for one company).
    fun details(symbol: String): EarningsDetailsPresenter = owned { s -> EarningsDetailsPresenter(symbol, remote, s, accounts?.alerts) }
    fun observeDetails(presenter: EarningsDetailsPresenter, onChange: (EarningsDetailsState) -> Unit) = observe(presenter.state, onChange)

    // Earnings reminders (Phase 4): the account-wide presenter (null without accounts); the server schedules and sends.
    private val reminderPresenter: EarningsRemindersPresenter? get() = accounts?.earningsReminders
    fun observeReminders(onChange: (EarningsRemindersState) -> Unit): AccountSubscription? = reminderPresenter?.let { observe(it.state, onChange) }
    fun reminderControl(state: EarningsRemindersState, eventId: String): String = if (state.signedIn) state.control(eventId).name else ReminderControl.OFF.name
    fun reminderNote(state: EarningsRemindersState, eventId: String): String? = (state.reminderFor(eventId)?.let { r -> r.statusMessage ?: r.nextDeliveryText?.let { "Notification: $it" } }
        ?: state.autoFor(eventId)?.let { "On automatically from your watchlist." })
    fun reminderOffset(state: EarningsRemindersState, eventId: String): Int = state.reminderFor(eventId)?.offsetDays ?: state.preferences.defaultOffsetDays
    val reminderOffsets: List<Int> get() = ReminderPolicy.OFFSETS
    fun remind(eventId: String, offsetDays: Int, results: Boolean) { reminderPresenter?.remind(eventId, offsetDays, results) }
    fun turnOffReminder(eventId: String) { reminderPresenter?.turnOff(eventId) }
    fun deleteReminder(reminderId: String) { reminderPresenter?.delete(reminderId) }
    fun refreshReminders() { reminderPresenter?.refresh() }
    fun setNotificationPermission(granted: Boolean) { reminderPresenter?.setPermission(granted) }
    fun updateReminderPreferences(enabled: Boolean, watchlistAuto: Boolean, defaultOffsetDays: Int, resultsAvailable: Boolean, dateChanges: Boolean, cancellations: Boolean,
                                  deliveryTime: String, quietHours: Boolean) {
        reminderPresenter?.updatePreferences { it.copy(enabled = enabled, watchlistAuto = watchlistAuto, defaultOffsetDays = defaultOffsetDays, resultsAvailable = resultsAvailable,
            dateChanges = dateChanges, cancellations = cancellations, deliveryTime = deliveryTime,
            quietStart = if (quietHours) "22:00" else null, quietEnd = if (quietHours) "07:00" else null) }
    }

    // Phase 5 (StockSteps+): premium Earnings Results sections and the personalized digest. The server
    // decides plan and quotas; these presenters reset on every account change.
    private val premiumRemote: EarningsPremiumRemote? = accounts?.let { RemoteEarningsPremium(it.userApi) }
    fun premium(reportId: String): EarningsPremiumPresenter = owned { s -> EarningsPremiumPresenter(reportId, premiumRemote, s, session).also { it.start() } }
    fun observePremium(presenter: EarningsPremiumPresenter, onChange: (EarningsPremiumState) -> Unit) = observe(presenter.state, onChange)
    fun selectMetric(presenter: EarningsPremiumPresenter, name: String) = HistoryMetric.entries.firstOrNull { it.name == name }?.let(presenter::selectMetric)
    fun loadHistory(presenter: EarningsPremiumPresenter) = presenter.loadHistory(presenter.state.value.history?.nextCursor)
    val historyMetrics: List<HistoryMetric> get() = HistoryMetric.entries
    /** MOCK-only AI scenarios: (id or "" for normal, label). */
    val premiumScenarios: List<List<String>> get() = PremiumCopy.SCENARIOS.map { listOf(it.first.orEmpty(), it.second) }
    fun setPremiumScenario(presenter: EarningsPremiumPresenter, id: String) = presenter.setScenario(id.ifEmpty { null })
    val premiumTitle: String get() = PremiumCopy.TITLE
    val premiumSubtitle: String get() = PremiumCopy.SUBTITLE
    val premiumBenefits: List<String> get() = PremiumCopy.BENEFITS
    val fairUse: String get() = PremiumCopy.FAIR_USE
    fun quotaText(quota: EarningsAiQuota?): String? = PremiumCopy.quotaText(quota)
    fun digestQuota(digest: PersonalizedEarningsDigest): String? = PremiumCopy.quotaText(digest.usage?.quota(EarningsAiCategory.DIGEST))
    fun planStatus(status: org.example.stocksteps.portfolio.analytics.EntitlementStatus): String? = PremiumCopy.status(status)

    fun digest(loadDigest: Boolean): EarningsDigestPresenter = owned { s -> EarningsDigestPresenter(premiumRemote, s, session, loadDigest).also { it.start() } }
    fun observeDigest(presenter: EarningsDigestPresenter, onChange: (EarningsDigestState) -> Unit) = observe(presenter.state, onChange)
    fun setDigestWeekly(presenter: EarningsDigestPresenter, weekly: Boolean) = presenter.updatePreferences { it.copy(cadence = if (weekly) DigestCadence.WEEKLY else DigestCadence.NONE) }
    fun setDigestDay(presenter: EarningsDigestPresenter, day: String) = presenter.updatePreferences { it.copy(dayOfWeek = day) }
    fun setDigestUpcoming(presenter: EarningsDigestPresenter, include: Boolean) = presenter.updatePreferences { it.copy(includeUpcoming = include) }
    fun toggleDigestInterest(presenter: EarningsDigestPresenter, key: String) =
        presenter.updatePreferences { it.copy(interests = if (key in it.interests) it.interests - key else it.interests + key) }
    val digestDays: List<String> get() = DigestPolicy.DAYS
    fun dayLabel(day: String): String = DigestPolicy.dayLabel(day)
    /** (key, label) pairs. */
    val digestInterests: List<List<String>> get() = DigestPolicy.INTERESTS.map { listOf(it.first, it.second) }
    val digestScenarios: List<List<String>> get() = listOf(listOf("", "Normal"), listOf("no-events", "No digest events"), listOf("empty-watchlist", "Empty watchlist"), listOf("ai-failure", "AI failure"))
    fun setDigestScenario(presenter: EarningsDigestPresenter, id: String) = presenter.setScenario(id.ifEmpty { null })

    val topics: List<EarningsEducation.Topic> get() = EarningsEducation.topics
    fun topic(key: String): EarningsEducation.Topic? = EarningsEducation.topic(key)
    fun date(date: String) = EarningsFormatter.date(date)
    fun spokenDate(date: String) = EarningsFormatter.spokenDate(date)
    val whatAreEarnings: String get() = EarningsCalendarRules.WHAT_ARE_EARNINGS
    val eventExplanation: String get() = EarningsCalendarRules.EVENT_EXPLANATION

    fun close() {
        scope.cancel()
        dependencies.close()
    }
}
