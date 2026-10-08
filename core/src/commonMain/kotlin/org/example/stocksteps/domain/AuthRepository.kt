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

class AccountException(message: String) : Exception(message)
