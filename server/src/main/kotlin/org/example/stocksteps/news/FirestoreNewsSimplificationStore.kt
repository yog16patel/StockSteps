package org.example.stocksteps.news

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.common.util.concurrent.MoreExecutors
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.json.Json
import org.example.stocksteps.model.SimplifiedNews
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Server-only collection. Transactions coordinate work across backend instances. */
class FirestoreNewsSimplificationStore(
    private val firestore: Firestore,
    private val collection: String = "newsSimplifications"
) : NewsSimplificationStore {
    companion object {
        private const val LEASE_MILLIS = 60_000L
        private const val OPERATION_TIMEOUT_MILLIS = 5_000L
    }
    private val owners = ConcurrentHashMap<String, String>()
    private fun document(key: String) = firestore.collection(collection).document(key)
    override suspend fun read(key: String): SimplifiedNews? = withContext(Dispatchers.IO) {
        document(key).get().boundedAwait().getString("result")?.let {
            Json.decodeFromString<SimplifiedNews>(it).validated()
        }
    }
    override suspend fun claim(key: String, now: Long): Boolean = withContext(Dispatchers.IO) {
        val owner = UUID.randomUUID().toString()
        val ref = document(key)
        val claimed = firestore.runTransaction { transaction ->
            val current = transaction.get(ref).get()
            if (current.getString("result") != null || (current.getLong("retryAt") ?: 0L) > now) false
            else {
                transaction.set(ref, mapOf(
                    "result" to null,
                    "retryAt" to now + LEASE_MILLIS,
                    "owner" to owner,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                true
            }
        }.boundedAwait()
        if (claimed) owners[key] = owner
        claimed
    }
    override suspend fun save(key: String, value: SimplifiedNews) = withContext(Dispatchers.IO) {
        val owner = owners[key] ?: error("Missing summary lease")
        try {
            val ref = document(key)
            val saved = firestore.runTransaction { transaction ->
                val current = transaction.get(ref).get()
                if (current.getString("owner") != owner || current.getString("result") != null) false
                else {
                    transaction.update(ref, mapOf(
                        "result" to Json.encodeToString(value.validated()),
                        "retryAt" to 0L,
                        "owner" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                    true
                }
            }.boundedAwait()
            check(saved) { "Summary lease no longer owned" }
        } finally { owners.remove(key, owner) }
    }
    override suspend fun fail(key: String, retryAt: Long) = withContext(Dispatchers.IO) {
        val owner = owners[key] ?: return@withContext
        try {
            val ref = document(key)
            firestore.runTransaction { transaction ->
                val current = transaction.get(ref).get()
                if (current.getString("owner") == owner && current.getString("result") == null) {
                    transaction.update(ref, mapOf(
                        "retryAt" to retryAt,
                        "owner" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
                Unit
            }.boundedAwait()
        } finally { owners.remove(key, owner) }
    }
    private suspend fun <T> ApiFuture<T>.boundedAwait(): T = withTimeout(OPERATION_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel(true) }
            ApiFutures.addCallback(this@boundedAwait, object : ApiFutureCallback<T> {
                override fun onSuccess(result: T) {
                    if (continuation.isActive) continuation.resume(result)
                }
                override fun onFailure(cause: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(cause)
                }
            }, MoreExecutors.directExecutor())
        }
    }
}
