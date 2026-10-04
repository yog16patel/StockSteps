package org.example.stocksteps.account

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import org.example.stocksteps.data.account.*
import org.example.stocksteps.domain.normalizedWatchlistSymbol
import org.example.stocksteps.model.WatchlistItem

internal class AndroidWatchlistGateway(private val firestore: FirebaseFirestore?) : PlatformWatchlistGateway {
    override fun observe(uid: String, onSnapshot: (List<WatchlistItem>?, Boolean, String?) -> Unit): AccountSubscription {
        val database = firestore ?: run {
            onSnapshot(null, false, "Cloud sync is not configured yet.")
            return object : AccountSubscription { override fun cancel() {} }
        }
        val listener = database.collection("users").document(uid).collection("watchlist")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) onSnapshot(null, false, error.safeCloudMessage())
                else if (snapshot != null) {
                    try {
                        val items = snapshot.documents.map { document ->
                            val symbol = document.getString("symbol") ?: error("Invalid watchlist data")
                            require(symbol == document.id && normalizedWatchlistSymbol(symbol) == symbol)
                            val added = requireNotNull(document.getLong("addedAt"))
                            val updated = requireNotNull(document.getLong("updatedAt"))
                            require(added >= 0 && updated >= added)
                            WatchlistItem(symbol, added, updated)
                        }
                        onSnapshot(items, !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites(), null)
                    } catch (_: Exception) {
                        onSnapshot(null, false, "Cloud watchlist data could not be read. Local data is safe.")
                    }
                }
            }
        return object : AccountSubscription { override fun cancel() { listener.remove() } }
    }

    // Transactions require a server connection. Firestore cannot independently replay stale
    // offline writes after our durable SQL outbox has moved on to a newer operation.
    override fun put(uid: String, item: WatchlistItem, completion: (String?) -> Unit) {
        val database = firestore ?: return completion("Cloud sync is not configured yet.")
        val document = database.collection("users").document(uid).collection("watchlist").document(item.symbol)
        database.runTransaction { transaction ->
            val existing = transaction.get(document).getLong("addedAt")
            val added = minOf(item.addedAt, existing ?: item.addedAt)
            transaction.set(document, mapOf("symbol" to item.symbol, "addedAt" to added, "updatedAt" to maxOf(item.updatedAt, added)))
            Unit
        }.addOnCompleteListener { completion(it.exception?.safeCloudMessage()) }
    }
    override fun delete(uid: String, symbol: String, completion: (String?) -> Unit) {
        val database = firestore ?: return completion("Cloud sync is not configured yet.")
        val document = database.collection("users").document(uid).collection("watchlist").document(symbol)
        database.runTransaction { transaction ->
            transaction.get(document)
            transaction.delete(document)
            Unit
        }.addOnCompleteListener { completion(it.exception?.safeCloudMessage()) }
    }
}

private fun Exception.safeCloudMessage() = if (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    "Cloud sync was denied. Check your account or cloud permissions. Local data is safe."
else "Could not sync. Your changes are saved on this device."
