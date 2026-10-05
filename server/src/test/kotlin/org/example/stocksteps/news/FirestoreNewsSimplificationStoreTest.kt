package org.example.stocksteps.news

import com.google.cloud.firestore.FirestoreOptions
import kotlinx.coroutines.*
import org.example.stocksteps.model.*
import org.junit.Assume.assumeTrue
import java.util.UUID
import kotlin.test.*

/** Runs against a demo-project emulator only; never uses production credentials. */
class FirestoreNewsSimplificationStoreTest {
    private val explanation = SimplifiedNews("A simple headline for the news", "A short factual summary.", "Information is insufficient to determine significance.", NewsSentiment.NEUTRAL)
    private fun database(): com.google.cloud.firestore.Firestore {
        val host = System.getenv("FIRESTORE_EMULATOR_HOST")
        assumeTrue("Run with Firestore emulator", !host.isNullOrBlank())
        return FirestoreOptions.getDefaultInstance().toBuilder()
            .setProjectId("demo-stocksteps")
            .setEmulatorHost(host)
            .build().service.also {
                // Warm the emulator channel separately; production cold starts may safely return original news.
                it.collection("newsSimplifications").document("warmup").get()
                    .get(10, java.util.concurrent.TimeUnit.SECONDS)
            }
    }
    @Test fun concurrentInstancesGenerateOneClaimAndPersistResult() = runBlocking<Unit> {
        val db = database()
        try {
            val a = FirestoreNewsSimplificationStore(db)
            val b = FirestoreNewsSimplificationStore(db)
            val key = UUID.randomUUID().toString()
            val results = coroutineScope {
                listOf(async { a.claim(key, 1_000) }, async { b.claim(key, 1_000) }).awaitAll()
            }
            assertEquals(1, results.count { it })
            val owner = if (results[0]) a else b
            owner.save(key, explanation)
            assertEquals(explanation, FirestoreNewsSimplificationStore(db).read(key))
            assertFalse(a.claim(key, 100_000))
            db.collection("newsSimplifications").document(key).delete().get()
        } finally { db.close() }
    }
    @Test fun leaseExpiryPreventsOldWorkerOverwritingNewWorker() = runBlocking<Unit> {
        val db = database()
        try {
            val a = FirestoreNewsSimplificationStore(db)
            val b = FirestoreNewsSimplificationStore(db)
            val key = UUID.randomUUID().toString()
            assertTrue(a.claim(key, 1_000))
            assertFalse(b.claim(key, 60_999))
            assertTrue(b.claim(key, 61_000))
            assertFails { a.save(key, explanation.copy(summary = "Stale worker output")) }
            b.save(key, explanation)
            a.fail(key, 999_999)
            assertEquals(explanation, a.read(key))
            db.collection("newsSimplifications").document(key).delete().get()
        } finally { db.close() }
    }
    @Test fun failureCooldownIsSharedAndExpires() = runBlocking<Unit> {
        val db = database()
        try {
            val a = FirestoreNewsSimplificationStore(db)
            val b = FirestoreNewsSimplificationStore(db)
            val key = UUID.randomUUID().toString()
            assertTrue(a.claim(key, 1_000))
            a.fail(key, 900_000)
            assertNull(b.read(key))
            assertFalse(b.claim(key, 899_999))
            assertTrue(b.claim(key, 900_000))
            b.save(key, explanation)
            assertEquals(explanation, a.read(key))
            db.collection("newsSimplifications").document(key).delete().get()
        } finally { db.close() }
    }
}
