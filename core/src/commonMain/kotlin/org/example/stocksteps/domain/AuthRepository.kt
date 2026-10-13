package org.example.stocksteps.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.example.stocksteps.model.AuthSession
import org.example.stocksteps.model.User

interface AuthRepository {
    val session: StateFlow<AuthSession>
    val currentUser: Flow<User?>
    suspend fun signUp(email: String, password: String)
    suspend fun signIn(email: String, password: String)
    suspend fun signInWithGoogle() { throw AccountException("Google sign-in is not configured.") }
    suspend fun signOut()
    /** A Firebase ID token for StockSteps backend requests; null when signed out. */
    suspend fun idToken(forceRefresh: Boolean = false): String? = null
}

open class AccountException(message: String) : Exception(message)

/**
 * Portfolio Phase E.2: the ID token couldn't be refreshed only because the auth service couldn't be reached — the session may still be valid, so
 * this is a connection problem ("Could not connect…"), not an ended session. Thrown for [AccountMessages.TOKEN_CONNECTION].
 */
class AccountConnectionException(message: String) : AccountException(message)

/** Messages shared by the platform auth gateways and the shared code that tells them apart. */
object AccountMessages {
    /** E.1 token-refresh message for a connection failure; the platform mappers return exactly this text so the cause can be recognized. */
    const val TOKEN_CONNECTION = "Could not connect. Check your connection and try again."
}
