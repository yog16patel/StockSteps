package org.example.stocksteps.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePreferenceStoreTest {
    @Test fun unknownOrMissingValuesFallBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemePreferenceStore.decode(null))
        assertEquals(ThemeMode.SYSTEM, ThemePreferenceStore.decode("dark"))
        assertEquals(ThemeMode.DARK, ThemePreferenceStore.decode("DARK"))
    }

    @Test fun storeEmitsSelectedMode() {
        val store = InMemoryThemePreferenceStore()
        assertEquals(ThemeMode.SYSTEM, store.themeMode.value)
        store.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, store.themeMode.value)
    }
}
