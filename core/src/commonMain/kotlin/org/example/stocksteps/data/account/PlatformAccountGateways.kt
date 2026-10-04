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
    fun signOut(completion: (String?) -> Unit)
}

interface PlatformWatchlistGateway {
    fun observe(
        uid: String,
        onSnapshot: (List<WatchlistItem>?, Boolean, String?) -> Unit
    ): AccountSubscription
    fun put(uid: String, item: WatchlistItem, completion: (String?) -> Unit)
    fun delete(uid: String, symbol: String, completion: (String?) -> Unit)
}
