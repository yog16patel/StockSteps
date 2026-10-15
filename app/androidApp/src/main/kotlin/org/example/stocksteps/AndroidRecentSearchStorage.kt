package org.example.stocksteps

import android.content.Context
import org.example.stocksteps.search.RecentSearches
import org.example.stocksteps.search.StringStorage

/** Recent company searches (5D.5.5C) in the app's private SharedPreferences: on this device only, never synced, no account needed. */
internal class AndroidRecentSearchStorage(context: Context) : StringStorage {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    override fun read(): String? = preferences.getString(RecentSearches.KEY, null)
    override fun write(value: String?) {
        preferences.edit().apply { if (value == null) remove(RecentSearches.KEY) else putString(RecentSearches.KEY, value) }.apply()
    }

    private companion object { const val FILE = "stocksteps-preferences" }
}
