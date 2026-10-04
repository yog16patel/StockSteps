package org.example.stocksteps.account

import android.app.Activity
import android.app.Application
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

/** Owns provider SDK details; the retained account graph never retains an Activity. */
internal class AndroidGoogleSignIn(private val application: Application) {
    private val manager = CredentialManager.create(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activity = WeakReference<Activity>(null)
    private var request: Job? = null

    fun attach(value: Activity) { activity = WeakReference(value) }
    fun detach(value: Activity) {
        if (activity.get() === value) { activity.clear(); request?.cancel() }
    }
    fun signIn(completion: (String?, String?) -> Unit) {
        val presenter = activity.get()?.takeUnless { it.isFinishing || it.isDestroyed }
            ?: return completion(null, "Reopen the login screen and try again.")
        val id = application.resources.getIdentifier("default_web_client_id", "string", application.packageName)
        val clientId = if (id != 0) application.getString(id) else ""
        if (clientId.isBlank()) return completion(null, "Google sign-in needs an updated Firebase app configuration.")
        if (request?.isActive == true) return completion(null, "Google sign-in is already in progress.")
        request = scope.launch {
            try {
                val option = GetSignInWithGoogleOption.Builder(clientId).build()
                val response = manager.getCredential(presenter, GetCredentialRequest.Builder().addCredentialOption(option).build())
                val credential = response.credential
                if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    completion(null, "Google did not return a valid sign-in credential.")
                } else {
                    completion(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
                }
            } catch (_: GetCredentialCancellationException) {
                completion(null, "Google sign-in was cancelled.")
            } catch (_: NoCredentialException) {
                completion(null, "Add a Google account to this device and try again.")
            } catch (cause: CancellationException) {
                completion(null, "Google sign-in was cancelled.")
                throw cause
            } catch (_: Exception) {
                completion(null, "Could not sign in with Google. Check your connection and Firebase Google configuration.")
            }
        }
    }
    fun clearSession() {
        scope.launch { runCatching { manager.clearCredentialState(ClearCredentialStateRequest()) } }
    }
    fun close() { scope.cancel(); activity.clear() }
}
