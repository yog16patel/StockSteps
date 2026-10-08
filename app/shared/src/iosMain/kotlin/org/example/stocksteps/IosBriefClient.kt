package org.example.stocksteps

import kotlinx.coroutines.*
import org.example.stocksteps.brief.*
import org.example.stocksteps.data.account.AccountSubscription
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Daily Market Brief for SwiftUI: the same shared presenter, caching and backend as Android. */
class IosBriefClient(account: IosAccountClient) {
    private val accounts = account.dependenciesForEarnings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val presenter: DailyBriefPresenter = accounts.dailyBrief

    fun observe(onChange: (DailyBriefUiState) -> Unit): AccountSubscription {
        val job = scope.launch { presenter.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }

    @OptIn(ExperimentalTime::class)
    fun now(): Long = Clock.System.now().toEpochMilliseconds()
    fun freshness(brief: DailyBrief) = BriefFormat.freshness(brief, now())
    fun isStale(brief: DailyBrief) = BriefFormat.isStale(brief, now())
    fun date(iso: String) = BriefFormat.date(iso)
    fun published(iso: String?) = BriefFormat.published(iso)
    fun value(i: BriefIndex) = BriefFormat.value(i)
    fun change(i: BriefIndex) = BriefFormat.change(i)
    fun direction(i: BriefIndex) = BriefFormat.direction(i)
    fun sign(i: BriefIndex) = BriefFormat.sign(i)
    fun accessibility(i: BriefIndex) = BriefFormat.accessibility(i)
    fun money(v: Double) = BriefFormat.groupDigits(v)
    fun sessionLabel(s: BriefMarketSession) = s.label
    fun isStaleQuote(i: BriefIndex) = i.state == QuoteState.STALE || i.state == QuoteState.UNAVAILABLE
    fun ai(state: DailyBriefUiState, key: String): BriefAiState? = state.ai[key]
    fun isPlus(state: DailyBriefUiState) = state.plus
    val scenarios: List<String> get() = DailyBriefPresenter.SCENARIOS
    fun term(id: String) = org.example.stocksteps.learning.BeginnerEducation.entry(id)
    fun setNotifications(p: BriefPreferences, on: Boolean) = presenter.savePreferences(p.copy(notificationsEnabled = on))
    fun setHour(p: BriefPreferences, hour: Int) = presenter.savePreferences(p.copy(deliveryHour = hour))
    fun setPersonalized(p: BriefPreferences, on: Boolean) = presenter.savePreferences(p.copy(personalizedNotifications = on))

    fun close() { scope.cancel() }
}
