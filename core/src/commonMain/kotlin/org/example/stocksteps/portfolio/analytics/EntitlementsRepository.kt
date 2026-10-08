package org.example.stocksteps.portfolio.analytics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.example.stocksteps.data.userdata.UserApi
import org.example.stocksteps.domain.AuthRepository

/**
 * The signed-in user's StockSteps+ state as reported by the backend. Display only: the server
 * decides what each request returns. Cleared on sign-out and on any account or Mock/Real change.
 */
class EntitlementsRepository(
    private val auth: AuthRepository,
    private val api: UserApi,
    private val environment: StateFlow<String>,
    private val scope: CoroutineScope
) {
    private val mutable = MutableStateFlow<Entitlements?>(null)
    val state: StateFlow<Entitlements?> = mutable.asStateFlow()
    private val owner = MutableStateFlow<Pair<String?, String>?>(null)

    fun start() {
        scope.launch {
            combine(auth.session.filter { !it.initializing }.map { it.user?.id }, environment) { uid, env -> uid to env }
                .distinctUntilChanged()
                .collectLatest { key ->
                    mutable.value = null
                    owner.value = key
                    if (key.first != null) load(key)
                }
        }
    }

    suspend fun refresh() { owner.value?.takeIf { it.first != null }?.let { load(it) } }

    /** MOCK only: asks the mock backend to simulate a tier. Never available against REAL. */
    suspend fun simulate(tier: SubscriptionTier, expired: Boolean = false) {
        check(environment.value == "mock") { "Plan simulation is only available with the mock backend." }
        val key = owner.value
        val result = api.simulateEntitlements(DebugEntitlementRequest(tier, expired))
        if (owner.value == key) mutable.value = result
    }

    private suspend fun load(key: Pair<String?, String>) {
        try {
            val result = api.entitlements()
            if (owner.value == key) mutable.value = result
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            // Unknown plan: show the free experience; the server still decides each response.
            if (owner.value == key) mutable.value = null
        }
    }
}
