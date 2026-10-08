package org.example.stocksteps.security

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AppLockManagerTest {
    private class FakeClock(var monotonic: Long = 1_000, var wall: Long = 1_700_000_000_000) : AppLockClock {
        override fun monotonicMillis() = monotonic
        override fun wallMillis() = wall
        fun advance(millis: Long) { monotonic += millis; wall += millis }
    }

    private class FakeAuthenticator(
        var availability: BiometricAvailability = BiometricAvailability.AVAILABLE,
        var next: BiometricResult = BiometricResult.Success
    ) : BiometricAuthenticator {
        var prompts = 0
        override fun availability() = availability
        override fun kind() = BiometricKind.FINGERPRINT
        override suspend fun authenticate(reason: String): BiometricResult { prompts++; return next }
    }

    private val preferences = InMemoryAppLockPreferences()
    private val clock = FakeClock()
    private val authenticator = FakeAuthenticator()
    private fun manager() = AppLockManager(preferences, authenticator, clock)

    @Test fun lockIsOffByDefaultAndSignedOutNeedsNothing() {
        val lock = manager()
        lock.onAccountChanged(null)
        assertEquals(AppLockState.NOT_REQUIRED, lock.state.value)
        lock.onAccountChanged("alice")
        assertEquals(AppLockState.NOT_REQUIRED, lock.state.value, "disabled by default")
        assertEquals(AppLockTimeout.FIVE_MINUTES, lock.settings()!!.timeout, "default timeout is 5 minutes")
        assertFalse(lock.settings()!!.enabled)
    }

    @Test fun enablingRequiresSuccessfulAuthentication() = runTest {
        val lock = manager()
        lock.onAccountChanged("alice")
        authenticator.next = BiometricResult.Cancelled
        assertEquals(BiometricResult.Cancelled, lock.enable("Turn on"))
        assertFalse(preferences.enabled("alice"), "cancelled leaves it off")
        authenticator.next = BiometricResult.Success
        lock.enable("Turn on")
        assertTrue(preferences.enabled("alice"))
        assertEquals(AppLockState.UNLOCKED, lock.state.value)
        authenticator.availability = BiometricAvailability.NOT_ENROLLED
        val other = manager().apply { onAccountChanged("bob") }
        assertEquals(BiometricResult.Unavailable, other.enable("Turn on"), "unsupported devices can't enable it")
        assertFalse(preferences.enabled("bob"))
    }

    @Test fun restoredSessionStartsLockedAndOnlySuccessUnlocks() = runTest {
        preferences.setEnabled("alice", true)
        val lock = manager()
        lock.onAccountChanged("alice")
        assertEquals(AppLockState.LOCKED, lock.state.value, "process start (incl. after process death) with a saved session")
        authenticator.next = BiometricResult.Failure("no match")
        lock.unlock("Unlock")
        assertEquals(AppLockState.LOCKED, lock.state.value)
        authenticator.next = BiometricResult.Lockout
        assertEquals(BiometricResult.Lockout, lock.unlock("Unlock"))
        assertEquals(AppLockState.LOCKED, lock.state.value)
        authenticator.next = BiometricResult.Success
        lock.unlock("Unlock")
        assertEquals(AppLockState.UNLOCKED, lock.state.value)
    }

    @Test fun freshSignInIsNotLockedButRestoreIs() {
        preferences.setEnabled("alice", true)
        val lock = manager()
        lock.onAccountChanged(null) // app started signed out
        lock.onAccountChanged("alice") // user signs in now
        assertEquals(AppLockState.UNLOCKED, lock.state.value)
    }

    @Test fun backgroundTimeoutRules() {
        preferences.setEnabled("alice", true)
        val lock = manager().apply { onAccountChanged(null); onAccountChanged("alice") }
        lock.onBackground(); clock.advance(2 * 60_000); lock.onForeground()
        assertEquals(AppLockState.UNLOCKED, lock.state.value, "back before 5 minutes")
        lock.onBackground(); clock.advance(5 * 60_000); lock.onForeground()
        assertEquals(AppLockState.LOCKED, lock.state.value, "back after 5 minutes")
    }

    @Test fun everyTimeAndNeverModes() {
        preferences.setEnabled("alice", true)
        preferences.setTimeout("alice", AppLockTimeout.IMMEDIATELY)
        val every = manager().apply { onAccountChanged(null); onAccountChanged("alice") }
        every.onBackground(); clock.advance(1); every.onForeground()
        assertEquals(AppLockState.LOCKED, every.state.value)
        preferences.setTimeout("alice", AppLockTimeout.NEVER)
        val never = manager().apply { onAccountChanged("alice") }
        assertEquals(AppLockState.UNLOCKED, never.state.value, "never mode doesn't lock even on start")
        never.onBackground(); clock.advance(24 * 3_600_000L); never.onForeground()
        assertEquals(AppLockState.UNLOCKED, never.state.value)
    }

    @Test fun untrustedTimeLocksAndRepeatedEventsAreIgnored() {
        preferences.setEnabled("alice", true)
        val lock = manager().apply { onAccountChanged(null); onAccountChanged("alice") }
        lock.onBackground(); clock.wall -= 3_600_000; clock.monotonic += 1_000; lock.onForeground()
        assertEquals(AppLockState.LOCKED, lock.state.value, "wall clock moved backwards: lock rather than bypass")
        val other = manager().apply { onAccountChanged(null); onAccountChanged("alice") }
        other.onForeground() // foreground without a background (e.g. first resume) doesn't lock
        assertEquals(AppLockState.UNLOCKED, other.state.value)
        other.onBackground(); clock.monotonic += 10 * 60_000 /* device time not counted by wall */; other.onForeground()
        assertEquals(AppLockState.LOCKED, other.state.value, "the larger elapsed value wins")
    }

    @Test fun signOutAndAccountSwitchDropUnlockAndPreferences() = runTest {
        preferences.setEnabled("alice", true)
        val lock = manager().apply { onAccountChanged(null); onAccountChanged("alice") }
        lock.onAccountChanged(null) // explicit sign-out or a revoked session
        assertEquals(AppLockState.NOT_REQUIRED, lock.state.value)
        assertFalse(preferences.enabled("alice"), "lock preferences are cleared with the account")
        lock.onAccountChanged("bob")
        assertEquals(AppLockState.NOT_REQUIRED, lock.state.value, "bob doesn't inherit alice's settings")
        assertNull(manager().settings(), "no settings without an account")
    }

    @Test fun disablingAsksForAuthenticationAndRemovedBiometricsKeepTheLock() = runTest {
        preferences.setEnabled("alice", true)
        val lock = manager().apply { onAccountChanged("alice") }
        authenticator.next = BiometricResult.Cancelled
        lock.disable("Turn off")
        assertTrue(preferences.enabled("alice"), "cancel keeps protection")
        authenticator.availability = BiometricAvailability.NOT_ENROLLED
        val restart = manager().apply { onAccountChanged("alice") }
        assertEquals(AppLockState.LOCKED, restart.state.value, "removing biometrics doesn't silently switch the lock off")
    }
}
