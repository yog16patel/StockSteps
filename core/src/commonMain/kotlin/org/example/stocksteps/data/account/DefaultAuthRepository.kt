package org.example.stocksteps.data.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import org.example.stocksteps.domain.AccountException
import org.example.stocksteps.domain.AuthRepository
import org.example.stocksteps.model.AuthSession
import org.example.stocksteps.model.User
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DefaultAuthRepository(private val gateway: PlatformAuthGateway, scope: CoroutineScope) : AuthRepository {
    override val session: StateFlow<AuthSession> = callbackFlow {
        val error = gateway.configurationError
        if (error != null) {
            trySend(AuthSession(initializing = false, configurationError = error))
            close()
        } else {
            val subscription = gateway.observeUser { user -> trySend(AuthSession(user = user, initializing = false)) }
            awaitClose { subscription.cancel() }
        }
    }.stateIn(scope, SharingStarted.Eagerly, AuthSession())

    override val currentUser: Flow<User?> = session.filter { !it.initializing }.map { it.user }.distinctUntilChanged()

    override suspend fun signUp(email: String, password: String) {
        validate(email, password)
        awaitAccountOperation { gateway.signUp(email.trim(), password, it) }
    }
    override suspend fun signIn(email: String, password: String) {
        validate(email, password)
        awaitAccountOperation { gateway.signIn(email.trim(), password, it) }
    }
    override suspend fun signInWithGoogle() {
        gateway.configurationError?.let { throw AccountException(it) }
        awaitAccountOperation(gateway::signInWithGoogle)
    }

    override suspend fun signOut() = awaitAccountOperation(gateway::signOut)

    override suspend fun idToken(forceRefresh: Boolean): String? {
        if (session.value.user == null) return null
        return suspendCancellableCoroutine { continuation ->
            gateway.idToken(forceRefresh) { token, error ->
                if (!continuation.isActive) return@idToken
                if (token != null) continuation.resume(token)
                else continuation.resumeWithException(AccountException(error ?: "Sign in again to continue."))
            }
        }
    }

    private fun validate(email: String, password: String) {
        gateway.configurationError?.let { throw AccountException(it) }
        if (!email.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) throw AccountException("Enter a valid email address.")
        if (password.isBlank()) throw AccountException("Enter your password.")
    }
}

internal suspend fun awaitAccountOperation(start: ((String?) -> Unit) -> Unit) = suspendCancellableCoroutine<Unit> { continuation ->
    try {
        start { error ->
            if (continuation.isActive) {
                if (error == null) continuation.resume(Unit)
                else continuation.resumeWithException(AccountException(error))
            }
        }
    } catch (cause: Exception) {
        if (continuation.isActive) continuation.resumeWithException(cause)
    }
}
