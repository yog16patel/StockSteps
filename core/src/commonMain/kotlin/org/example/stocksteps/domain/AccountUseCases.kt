package org.example.stocksteps.domain

class SignIn(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String) = repository.signIn(email, password)
}
class SignUp(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String) = repository.signUp(email, password)
}
class SignOut(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.signOut()
}
class AddToWatchlist(private val repository: WatchlistRepository) {
    suspend operator fun invoke(symbol: String) = repository.add(normalizedWatchlistSymbol(symbol))
    suspend operator fun invoke(stock: org.example.stocksteps.model.StockSearchResult) = repository.add(stock)
}
class RemoveFromWatchlist(private val repository: WatchlistRepository) {
    suspend operator fun invoke(symbol: String) = repository.remove(normalizedWatchlistSymbol(symbol))
}
