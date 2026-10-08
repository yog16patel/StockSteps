package org.example.stocksteps

import kotlinx.coroutines.*
import org.example.stocksteps.data.account.AccountSubscription
import org.example.stocksteps.practice.*

/**
 * Practice Portfolio for SwiftUI: the same shared presenters, backend contract and server-side
 * enforcement as Android. Requires the account graph (the server stores the simulated ledger).
 */
class IosPracticeClient(account: IosAccountClient) {
    private val accounts = account.dependenciesForEarnings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val presenter: PracticePresenter = accounts.practice

    fun observe(onChange: (PracticeUiState) -> Unit): AccountSubscription {
        val job = scope.launch { presenter.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }

    fun order(symbol: String, sell: Boolean): PracticeOrderPresenter =
        accounts.practiceOrder(symbol, if (sell) OrderSide.SELL else OrderSide.BUY, scope).also { it.start() }

    fun observeOrder(order: PracticeOrderPresenter, onChange: (PracticeOrderState) -> Unit): AccountSubscription {
        val job = scope.launch { order.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }

    // Swift-friendly helpers.
    val tabs: List<PracticeTab> get() = PracticeTab.entries
    val ranges: List<PracticeRange> get() = PracticeRange.entries
    val scenarios: List<String> get() = listOf("empty", "one-holding", "free-limit", "eight-holdings-trial-expired", "trial-active", "trial-expiring", "trial-expired",
        "plus-active", "plus-expired", "low-cash", "missing-quote", "stale-quote", "missing-fx", "dividend", "split")
    val disclosure: String get() = PracticePolicy.DISCLOSURE
    fun selectTab(name: String) = PracticeTab.entries.firstOrNull { it.name == name }?.let(presenter::selectTab)
    fun selectRange(name: String) = PracticeRange.entries.firstOrNull { it.name == name }?.let(presenter::selectRange)
    fun filter(name: String?) = presenter.filter(name?.let { n -> PracticeTransactionType.entries.firstOrNull { it.name == n } })
    fun rangeAllowed(state: PracticeUiState, name: String): Boolean = state.entitlement?.let { e -> PracticePolicy.ranges(e.access).any { it.name == name } } ?: false
    fun has(state: PracticeUiState, capability: String): Boolean = state.entitlement?.capabilities?.any { it.name == capability } == true
    fun answer(state: PracticeUiState, challengeId: String) = state.challengeAnswers[challengeId]
    fun setAmountMode(order: PracticeOrderPresenter, amount: Boolean) = order.setMode(if (amount) OrderInput.AMOUNT else OrderInput.SHARES)

    fun money(value: String?, currency: String) = PracticeFormat.money(value, currency)
    fun signedMoney(value: String?, currency: String) = PracticeFormat.signedMoney(value, currency)
    fun price(value: String?, currency: String) = PracticeFormat.price(value, currency)
    fun percent(value: String?) = PracticeFormat.percent(value)
    fun shares(value: String) = PracticeFormat.shares(value)
    fun direction(value: String?) = PracticeFormat.direction(value)
    /** 1 gain, -1 loss, 0 none/unavailable. */
    fun sign(value: String?): Int = when (PracticeFormat.positive(value)) { true -> 1; false -> -1; null -> 0 }
    fun date(millis: Long) = PracticeFormat.date(millis)
    fun dateTime(millis: Long) = PracticeFormat.dateTime(millis)
    fun isoDate(iso: String?) = PracticeFormat.isoDate(iso)
    fun type(t: PracticeTransaction) = PracticeFormat.type(t.type)
    fun values(points: List<PracticeValuePoint>): List<Double?> = points.map { it.value?.toDoubleOrNull() }

    /** Sheet identity for SwiftUI (kind + feature/flag). */
    fun sheetKind(sheet: PracticeSheet?): String? = when (sheet) {
        null -> null
        is PracticeSheet.HoldingLimit -> if (sheet.trialOffered) "limit-trial" else "limit"
        is PracticeSheet.Locked -> if (sheet.trialOffered) "locked-trial" else "locked"
        PracticeSheet.TrialConfirm -> "trial"
        PracticeSheet.TrialExpired -> "expired"
        PracticeSheet.PlusInfo -> "plus"
        PracticeSheet.ResetConfirm -> "reset"
    }
    fun lockedFeature(sheet: PracticeSheet?): String? = (sheet as? PracticeSheet.Locked)?.feature

    fun close() { scope.cancel() }
}
