package org.example.stocksteps.data.account

import kotlinx.coroutines.test.*
import org.example.stocksteps.domain.AccountException
import org.example.stocksteps.model.User
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DefaultAuthRepositoryTest {
    private class Gateway(override val configurationError: String? = null) : PlatformAuthGateway {
        var listener: ((User?) -> Unit)? = null
        var signInCalls = 0
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
}
