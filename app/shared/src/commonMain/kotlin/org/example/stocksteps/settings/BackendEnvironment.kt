package org.example.stocksteps.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which StockSteps backend the app talks to during development. */
enum class BackendEnvironment { MOCK, REAL }

interface BackendEnvironmentStore {
    val environment: StateFlow<BackendEnvironment>
    fun setEnvironment(environment: BackendEnvironment)

    companion object {
        /** Key shared by every platform store (iOS SwiftUI reads the same UserDefaults key). */
        const val KEY = "stocksteps.backendEnvironment"

        /** Nothing saved keeps today's behaviour: the real backend. */
        fun decode(stored: String?): BackendEnvironment =
            BackendEnvironment.entries.firstOrNull { it.name == stored } ?: BackendEnvironment.REAL
    }
}

class InMemoryBackendEnvironmentStore(initial: BackendEnvironment = BackendEnvironment.REAL) : BackendEnvironmentStore {
    private val state = MutableStateFlow(initial)
    override val environment: StateFlow<BackendEnvironment> = state.asStateFlow()
    override fun setEnvironment(environment: BackendEnvironment) { state.value = environment }
}

/** Build-time backend URLs. [mock] is only configured in development builds. */
data class BackendEndpoints(val real: String, val mock: String? = null)

/**
 * Resolves the backend URL for every request from the current environment, so switching
 * takes effect on the very next request of every client; nothing caches the old URL.
 * Without a mock URL (release builds) the environment is always REAL.
 */
class BackendRouter(private val store: BackendEnvironmentStore, val endpoints: BackendEndpoints) {
    val mockAvailable: Boolean get() = endpoints.mock != null

    fun currentEnvironment(): BackendEnvironment = effective(store.environment.value)

    /** The stored choice; use [effective] to apply the "no mock URL means REAL" rule. */
    val selection: StateFlow<BackendEnvironment> get() = store.environment

    fun effective(stored: BackendEnvironment): BackendEnvironment =
        if (endpoints.mock == null) BackendEnvironment.REAL else stored

    fun currentUrl(): String = when (currentEnvironment()) {
        BackendEnvironment.MOCK -> endpoints.mock ?: endpoints.real
        BackendEnvironment.REAL -> endpoints.real
    }

    fun select(environment: BackendEnvironment) {
        if (environment == BackendEnvironment.REAL || mockAvailable) store.setEnvironment(environment)
    }
}
