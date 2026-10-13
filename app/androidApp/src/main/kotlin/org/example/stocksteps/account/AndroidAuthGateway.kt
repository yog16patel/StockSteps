package org.example.stocksteps.account

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.FirebaseNetworkException
import org.example.stocksteps.data.account.*
import org.example.stocksteps.model.User

internal class AndroidAuthGateway(private val auth: FirebaseAuth?, private val google: AndroidGoogleSignIn? = null) : PlatformAuthGateway {
    override val configurationError: String? = if (auth == null) "Account sign-in is not configured yet. Your watchlist is saved on this device." else null

    override fun observeUser(onChange: (User?) -> Unit): AccountSubscription {
        val listener = FirebaseAuth.AuthStateListener { firebase ->
            onChange(firebase.currentUser?.let { User(it.uid, it.email) })
        }
        auth?.addAuthStateListener(listener)
        return object : AccountSubscription {
            override fun cancel() { auth?.removeAuthStateListener(listener) }
        }
    }
    override fun signUp(email: String, password: String, completion: (String?) -> Unit) {
        val auth = auth ?: return completion(configurationError)
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { completion(it.exception?.safeAuthMessage()) }
    }
    override fun signIn(email: String, password: String, completion: (String?) -> Unit) {
        val auth = auth ?: return completion(configurationError)
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { completion(it.exception?.safeAuthMessage()) }
    }
    override fun signInWithGoogle(completion: (String?) -> Unit) {
        val auth = auth ?: return completion(configurationError)
        val google = google ?: return completion("Google sign-in is not configured.")
        google.signIn { token, error ->
            if (token == null) completion(error ?: "Google sign-in was cancelled.")
            else auth.signInWithCredential(com.google.firebase.auth.GoogleAuthProvider.getCredential(token, null))
                .addOnCompleteListener { completion(it.exception?.safeAuthMessage()) }
        }
    }
    override fun idToken(forceRefresh: Boolean, completion: (String?, String?) -> Unit) {
        val user = auth?.currentUser ?: return completion(null, "Sign in to continue.")
        user.getIdToken(forceRefresh).addOnCompleteListener { task ->
            if (task.isSuccessful) completion(task.result?.token, null)
            else completion(null, task.exception?.tokenMessage() ?: "Sign in again to continue.")
        }
    }
    override fun signOut(completion: (String?) -> Unit) {
        try { auth?.signOut(); google?.clearSession(); completion(null) }
        catch (cause: Exception) { completion(cause.safeAuthMessage()) }
    }
}

/**
 * Phase E.1: messages for a failed ID-token refresh (every backend request), kept apart from sign-in. A session the auth service no longer accepts
 * (e.g. the local emulator answering INVALID_REFRESH_TOKEN for an old session) is not a wrong password — it read "Check your email and password".
 */
private fun Exception.tokenMessage(): String = when {
    this is FirebaseNetworkException -> org.example.stocksteps.domain.AccountMessages.TOKEN_CONNECTION // E.2: recognized as a connection problem
    this is FirebaseAuthException && errorCode in SESSION_ENDED -> "Your session has ended. Sign in again to continue."
    else -> "Sign in again to continue."
}

private val SESSION_ENDED = setOf("ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN", "ERROR_USER_NOT_FOUND", "ERROR_USER_DISABLED",
    "ERROR_REQUIRES_RECENT_LOGIN")

private fun Exception.safeAuthMessage(): String = when {
    this is FirebaseNetworkException -> "Could not connect. Check your connection and try again."
    this is FirebaseAuthException -> when (errorCode) {
        "ERROR_WEAK_PASSWORD" -> "Use a stronger password with at least 6 characters."
        "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Could not create this account. Try signing in instead."
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later."
        "ERROR_OPERATION_NOT_ALLOWED" -> "Email sign-in is unavailable. Please contact support."
        else -> "Could not sign in. Check your email and password."
    }
    else -> "Could not complete the account request. Try again."
}
