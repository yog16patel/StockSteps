package org.example.stocksteps.data.account

import org.example.stocksteps.model.User
import org.example.stocksteps.model.WatchlistItem

// Callback contracts keep coroutine and Firebase SDK types out of native adapters.
interface AccountSubscription { fun cancel() }

interface PlatformAuthGateway {
    val configurationError: String?
    fun observeUser(onChange: (User?) -> Unit): AccountSubscription
    fun signUp(email: String, password: String, completion: (String?) -> Unit)
    fun signIn(email: String, password: String, completion: (String?) -> Unit)
    fun signInWithGoogle(completion: (String?) -> Unit) { completion("Google sign-in is not configured.") }
    fun signOut(completion: (String?) -> Unit)
    /** The signed-in user's Firebase ID token (refreshed by the SDK); `completion(token, error)`. */
    fun idToken(forceRefresh: Boolean, completion: (String?, String?) -> Unit) { completion(null, "Not signed in.") }
}

interface PlatformWatchlistGateway {
    fun observe(
        uid: String,
        onSnapshot: (List<WatchlistItem>?, Boolean, String?) -> Unit
    ): AccountSubscription
    fun put(uid: String, item: WatchlistItem, completion: (String?) -> Unit)
    fun delete(uid: String, symbol: String, completion: (String?) -> Unit)
}

/** Keeps the old single-list watchlist on the device only (guest list); the backend owns signed-in data. */
object LocalOnlyWatchlistGateway : PlatformWatchlistGateway {
    override fun observe(uid: String, onSnapshot: (List<WatchlistItem>?, Boolean, String?) -> Unit) =
        object : AccountSubscription { override fun cancel() = Unit }
    override fun put(uid: String, item: WatchlistItem, completion: (String?) -> Unit) = completion(null)
    override fun delete(uid: String, symbol: String, completion: (String?) -> Unit) = completion(null)
}
