package org.example.stocksteps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.stocksteps.settings.ThemeMode
import org.example.stocksteps.settings.ThemePreferenceStore

/** Theme choice in the platform's SharedPreferences; survives process restarts. */
internal class AndroidThemePreferenceStore(context: Context) : ThemePreferenceStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(ThemePreferenceStore.decode(preferences.getString(ThemePreferenceStore.KEY, null)))
    override val themeMode: StateFlow<ThemeMode> = state.asStateFlow()
    override fun setThemeMode(mode: ThemeMode) {
        preferences.edit().putString(ThemePreferenceStore.KEY, mode.name).apply()
        state.value = mode
    }

    private companion object { const val FILE = "stocksteps-preferences" }
}
