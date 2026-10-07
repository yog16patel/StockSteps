package org.example.stocksteps.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's appearance choice. SYSTEM keeps following the OS; it is never resolved and saved. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * App-level appearance preference. The root composition observes [themeMode];
 * Settings only writes it. Platforms back it with their native key-value store.
 */
interface ThemePreferenceStore {
    val themeMode: StateFlow<ThemeMode>
    fun setThemeMode(mode: ThemeMode)

    companion object {
        /** Key shared by every platform store (iOS SwiftUI reads the same UserDefaults key). */
        const val KEY = "stocksteps.themeMode"

        fun decode(stored: String?): ThemeMode = ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }
}

/** Non-persistent store for previews, tests and hosts without platform storage. */
class InMemoryThemePreferenceStore(initial: ThemeMode = ThemeMode.SYSTEM) : ThemePreferenceStore {
    private val state = MutableStateFlow(initial)
    override val themeMode: StateFlow<ThemeMode> = state.asStateFlow()
    override fun setThemeMode(mode: ThemeMode) { state.value = mode }
}
