package org.example.stocksteps.data

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.example.stocksteps.data.userdata.UserDataCache
import org.example.stocksteps.local.WatchlistDatabase

/** Offline copies of signed-in data in the app's SQLite database (same file as the guest list). */
class SqlUserDataCache(driver: SqlDriver) : UserDataCache {
    private val queries = WatchlistDatabase(driver).watchlistQueries

    override suspend fun read(owner: String, key: String): Pair<String, Long>? = withContext(Dispatchers.IO) {
        queries.cachedValue(owner, key).executeAsOneOrNull()?.let { it.json to it.savedAt }
    }

    override suspend fun write(owner: String, key: String, json: String, savedAt: Long) {
        withContext(Dispatchers.IO) {
            queries.putCachedValue(owner, key, json, savedAt)
        }
    }

    override suspend fun clearAccount(uid: String) {
        withContext(Dispatchers.IO) {
            queries.clearCacheOwner("%|user:$uid")
        }
    }
}
