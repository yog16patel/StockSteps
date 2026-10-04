package org.example.stocksteps.data.account

import kotlinx.coroutines.test.*
import kotlinx.coroutines.async
import org.example.stocksteps.domain.AccountException
import org.example.stocksteps.model.User
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DefaultAuthRepositoryTest {
    private class Gateway(override val configurationError: String? = null) : PlatformAuthGateway {
        var listener: ((User?) -> Unit)? = null
        var signInCalls = 0
        var googleCompletion: ((String?) -> Unit)? = null
        override fun signInWithGoogle(completion: (String?) -> Unit) { googleCompletion = completion }
        override fun observeUser(onChange: (User?) -> Unit): AccountSubscription {
            listener = onChange
            onChange(User("restored", "restored@example.com"))
            return object : AccountSubscription { override fun cancel() { listener = null } }
        }
        override fun signIn(email: String, password: String, completion: (String?) -> Unit) { signInCalls++; completion("Check your email and password.") }
        override fun signUp(email: String, password: String, completion: (String?) -> Unit) { completion(null) }
        override fun signOut(completion: (String?) -> Unit) { listener?.invoke(null); completion(null) }
    }
    @Test fun restoresSessionAndLogoutPublishesGuest() = runTest {
        val gateway = Gateway()
        val auth = DefaultAuthRepository(gateway, backgroundScope)
        assertTrue(auth.session.value.initializing)
        runCurrent()
        assertEquals("restored", auth.session.value.user?.id)
        assertFalse(auth.session.value.initializing)
        auth.signOut()
        runCurrent()
        assertNull(auth.session.value.user)
    }
    @Test fun rejectsInvalidEmailAndSurfacesSafeGatewayErrors() = runTest {
        val gateway = Gateway()
        val auth = DefaultAuthRepository(gateway, backgroundScope)
        runCurrent()
        assertFailsWith<AccountException> { auth.signIn("wrong", "password") }
        assertEquals(0, gateway.signInCalls)
        assertEquals("Check your email and password.", assertFailsWith<AccountException> { auth.signIn("a@example.com", "password") }.message)
        assertEquals(1, gateway.signInCalls)
    }
    @Test fun missingConfigurationFinishesInitializationAndDoesNotFakeAuthentication() = runTest {
        val auth = DefaultAuthRepository(Gateway("Setup required"), backgroundScope)
        runCurrent()
        assertFalse(auth.session.value.initializing)
        assertNull(auth.session.value.user)
        assertFailsWith<AccountException> { auth.signUp("a@example.com", "password") }
    }
    @Test fun googleWaitsForProviderCompletionAndPublishesFirebaseIdentity() = runTest {
        val gateway = Gateway()
        val auth = DefaultAuthRepository(gateway, backgroundScope)
        runCurrent()
        val login = async { auth.signInWithGoogle() }
        runCurrent()
        assertFalse(login.isCompleted)
        gateway.listener?.invoke(User("google-uid", "google@example.com"))
        gateway.googleCompletion?.invoke(null)
        runCurrent()
        login.await()
        assertEquals("google-uid", auth.session.value.user?.id)
    }
    @Test fun googleCancellationDoesNotReportSuccessOrChangeUser() = runTest {
        val gateway = Gateway()
        val auth = DefaultAuthRepository(gateway, backgroundScope)
        runCurrent()
        val login = async { runCatching { auth.signInWithGoogle() } }
        runCurrent()
        gateway.googleCompletion?.invoke("Google sign-in was cancelled.")
        val failure = login.await().exceptionOrNull()
        assertIs<AccountException>(failure)
        assertEquals("Google sign-in was cancelled.", failure.message)
        assertEquals("restored", auth.session.value.user?.id)
    }

}
