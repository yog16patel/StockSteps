package org.example.stocksteps

import kotlinx.coroutines.*
import org.example.stocksteps.data.account.AccountSubscription
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.earnings.*
import org.example.stocksteps.model.EarningsTime
import org.example.stocksteps.model.EarningsTiming

/**
 * Earnings Center and Earnings Details for SwiftUI: the same shared presenters, reminders (existing
 * alerts) and backend contract as Android. `baseUrl` is read per request (Mock/Real switch).
 */
class IosEarningsClient(baseUrl: () -> String, account: IosAccountClient?) {
    private val dependencies = StockStepsDependencies(baseUrl)
    private val accounts = account?.dependenciesForEarnings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val remote = dependencies.earningsRemote(accounts)
    val center = EarningsCenterPresenter(remote, scope, accounts?.alerts)

    fun observeCenter(onChange: (EarningsCenterState) -> Unit): AccountSubscription {
        val job = scope.launch { center.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }

    fun details(symbol: String): EarningsDetailsPresenter = EarningsDetailsPresenter(symbol, remote, scope, accounts?.alerts)
    fun observeDetails(presenter: EarningsDetailsPresenter, onChange: (EarningsDetailsState) -> Unit): AccountSubscription {
        val job = scope.launch { presenter.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }

    // Swift-friendly wrappers.
    fun selectTab(name: String) = EarningsTab.entries.firstOrNull { it.name == name }?.let(center::selectTab)
    fun selectRange(name: String) = EarningsRange.entries.firstOrNull { it.name == name }?.let(center::selectRange)
    fun toggleMarket(code: String) = center.state.value.filters.let { f -> center.setFilters(f.copy(markets = if (code in f.markets) f.markets - code else f.markets + code)) }
    fun toggleExchange(code: String) = center.state.value.filters.let { f -> center.setFilters(f.copy(exchanges = if (code in f.exchanges) f.exchanges - code else f.exchanges + code)) }
    fun toggleSession(name: String) = EarningsTime.entries.firstOrNull { it.name == name }?.let { s ->
        center.state.value.filters.let { f -> center.setFilters(f.copy(sessions = if (s in f.sessions) f.sessions - s else f.sessions + s)) }
    }
    fun setReminder(presenter: EarningsDetailsPresenter, timing: String?, leadDays: Int, results: Boolean) =
        presenter.setReminder(timing?.let { t -> EarningsTiming.entries.firstOrNull { it.name == t } }, leadDays.takeIf { it > 1 }, results, null)
    val topics: List<EarningsEducation.Topic> get() = EarningsEducation.topics
    fun topic(key: String): EarningsEducation.Topic? = EarningsEducation.topic(key)
    fun date(date: String) = EarningsFormatter.date(date)
    fun spokenDate(date: String) = EarningsFormatter.spokenDate(date)
    fun sessionShort(name: String) = EarningsTime.entries.firstOrNull { it.name == name }?.let(EarningsFormatter::sessionShort) ?: name

    fun close() {
        scope.cancel()
        dependencies.close()
    }
}
