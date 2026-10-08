package org.example.stocksteps.domain

class SignIn(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String) = repository.signIn(email, password)
}
class SignUp(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String) = repository.signUp(email, password)
}
/**
 * Signs out of Firebase, then removes that account's local watchlist copy so the next person on
 * the device can't see it. Public market data caches are untouched. Fails without clearing if
 * Firebase sign-out fails, so the app never looks signed out while it isn't.
 */
class SignOut(
    private val repository: AuthRepository,
    private val local: org.example.stocksteps.data.watchlist.WatchlistLocalStore? = null,
    private val cache: org.example.stocksteps.data.userdata.UserDataCache? = null,
    /** Runs while still signed in (e.g. unlinking this device's push token). */
    private val beforeSignOut: suspend () -> Unit = {}
) {
    suspend operator fun invoke() {
        val uid = repository.session.value.user?.id
        if (uid != null) beforeSignOut()
        repository.signOut()
        if (uid != null) {
            local?.clearOwner(org.example.stocksteps.data.watchlist.watchlistOwner(uid))
            // Cached watchlists, notes, alerts and quotes for this account (every environment).
            cache?.clearAccount(uid)
        }
    }
}
class AddToWatchlist(private val repository: WatchlistRepository) {
    suspend operator fun invoke(symbol: String) = repository.add(normalizedWatchlistSymbol(symbol))
    suspend operator fun invoke(stock: org.example.stocksteps.model.StockSearchResult) = repository.add(stock)
}
class RemoveFromWatchlist(private val repository: WatchlistRepository) {
    suspend operator fun invoke(symbol: String) = repository.remove(normalizedWatchlistSymbol(symbol))
}
