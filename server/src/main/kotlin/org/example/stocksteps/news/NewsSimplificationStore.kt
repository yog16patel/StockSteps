package org.example.stocksteps.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.example.stocksteps.model.SimplifiedNews
import java.nio.file.Path
import java.sql.DriverManager

interface NewsSimplificationStore {
    suspend fun read(key: String): SimplifiedNews?
    suspend fun claim(key: String, now: Long): Boolean
    suspend fun save(key: String, value: SimplifiedNews)
    suspend fun fail(key: String, retryAt: Long)
}

/** Shared across users; leases prevent duplicate work and expire after interrupted jobs. */
class SqliteNewsSimplificationStore(path: Path) : NewsSimplificationStore {
    companion object { private const val LEASE_MILLIS = 60_000L }
    private val url: String
    init {
        path.toAbsolutePath().parent.toFile().mkdirs()
        url = "jdbc:sqlite:${path.toAbsolutePath()}"
        connection().use { db -> db.createStatement().use {
            it.executeUpdate("CREATE TABLE IF NOT EXISTS news_summaries (cache_key TEXT PRIMARY KEY, result TEXT, retry_at INTEGER NOT NULL)")
        } }
    }
    private fun connection() = DriverManager.getConnection(url).also { db ->
        db.createStatement().use { it.execute("PRAGMA busy_timeout=5000") }
    }
    override suspend fun read(key: String): SimplifiedNews? = withContext(Dispatchers.IO) {
        connection().use { db -> db.prepareStatement("SELECT result FROM news_summaries WHERE cache_key=?").use { q ->
            q.setString(1, key)
            q.executeQuery().use { r -> if (r.next()) r.getString(1)?.let { Json.decodeFromString<SimplifiedNews>(it) } else null }
        } }
    }
    override suspend fun claim(key: String, now: Long): Boolean = withContext(Dispatchers.IO) {
        connection().use { db -> db.prepareStatement("""
            INSERT INTO news_summaries(cache_key,result,retry_at) VALUES(?,NULL,?)
            ON CONFLICT(cache_key) DO UPDATE SET retry_at=excluded.retry_at
            WHERE news_summaries.result IS NULL AND news_summaries.retry_at<=?
        """).use { q ->
            q.setString(1, key); q.setLong(2, now + LEASE_MILLIS); q.setLong(3, now)
            q.executeUpdate() == 1
        } }
    }
    override suspend fun save(key: String, value: SimplifiedNews) = withContext(Dispatchers.IO) {
        connection().use { db -> db.prepareStatement("UPDATE news_summaries SET result=?,retry_at=0 WHERE cache_key=?").use {
            it.setString(1, Json.encodeToString(value)); it.setString(2, key); it.executeUpdate()
        } }; Unit
    }
    override suspend fun fail(key: String, retryAt: Long) = withContext(Dispatchers.IO) {
        connection().use { db -> db.prepareStatement("UPDATE news_summaries SET retry_at=? WHERE cache_key=? AND result IS NULL").use {
            it.setLong(1, retryAt); it.setString(2, key); it.executeUpdate()
        } }; Unit
    }
}
