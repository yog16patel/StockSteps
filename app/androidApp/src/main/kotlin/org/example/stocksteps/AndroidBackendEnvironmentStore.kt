package org.example.stocksteps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendEnvironmentStore

/** Development backend choice in SharedPreferences; survives process restarts. */
internal class AndroidBackendEnvironmentStore(context: Context) : BackendEnvironmentStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(BackendEnvironmentStore.decode(preferences.getString(BackendEnvironmentStore.KEY, null)))
    override val environment: StateFlow<BackendEnvironment> = state.asStateFlow()
    override fun setEnvironment(environment: BackendEnvironment) {
        preferences.edit().putString(BackendEnvironmentStore.KEY, environment.name).apply()
        state.value = environment
    }

    private companion object { const val FILE = "stocksteps-preferences" }
}
