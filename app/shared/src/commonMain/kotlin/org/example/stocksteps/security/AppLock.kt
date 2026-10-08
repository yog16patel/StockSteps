package org.example.stocksteps.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the device can offer for local unlock (independent of Firebase sign-in). */
enum class BiometricAvailability {
    /** Biometrics and/or device passcode can authenticate the user. */
    AVAILABLE,
    /** Hardware exists but nothing is enrolled and no device passcode is set. */
    NOT_ENROLLED,
    /** No supported hardware or the platform can't authenticate locally. */
    UNAVAILABLE
}

/** Which system prompt the user will see, for labels ("Unlock with Face ID"). */
enum class BiometricKind { FACE_ID, TOUCH_ID, FINGERPRINT, BIOMETRIC, DEVICE_CREDENTIAL }

sealed interface BiometricResult {
    data object Success : BiometricResult
    /** The user (or the system, e.g. app backgrounded) dismissed the prompt; not an error to show. */
    data object Cancelled : BiometricResult
    data object Unavailable : BiometricResult
    /** Too many failed attempts; the user must use the device passcode or sign in again. */
    data object Lockout : BiometricResult
    data class Failure(val message: String?) : BiometricResult
}

/**
 * Local device authentication (Face ID / Touch ID / Android BiometricPrompt with device
 * credential fallback). It only unlocks the app for an already signed-in Firebase user; it never
 * creates a session or authorizes backend requests.
 */
interface BiometricAuthenticator {
    fun availability(): BiometricAvailability
    fun kind(): BiometricKind
    suspend fun authenticate(reason: String): BiometricResult
}

/** How long the app may stay in the background before it must be unlocked again. */
enum class AppLockTimeout(val millis: Long?) {
    IMMEDIATELY(0), FIVE_MINUTES(5 * 60_000L), FIFTEEN_MINUTES(15 * 60_000L),
    /** Never re-lock after backgrounding (biometric unlock still protects nothing beyond sign-in). */
    NEVER(null);

    companion object {
        val DEFAULT = FIVE_MINUTES
        fun decode(stored: String?) = entries.firstOrNull { it.name == stored } ?: DEFAULT
    }
}

/**
 * Per-account lock preferences (ordinary preferences, never secrets). Keys include the Firebase
 * uid so a second account on the device never inherits another account's choice.
 */
interface AppLockPreferences {
    fun enabled(uid: String): Boolean
    fun timeout(uid: String): AppLockTimeout
    fun setEnabled(uid: String, enabled: Boolean)
    fun setTimeout(uid: String, timeout: AppLockTimeout)
    fun clear(uid: String)

    companion object {
        fun enabledKey(uid: String) = "stocksteps.appLock.$uid.enabled"
        fun timeoutKey(uid: String) = "stocksteps.appLock.$uid.timeout"
    }
}

class InMemoryAppLockPreferences : AppLockPreferences {
    private val values = mutableMapOf<String, String>()
    override fun enabled(uid: String) = values[AppLockPreferences.enabledKey(uid)] == "true"
    override fun timeout(uid: String) = AppLockTimeout.decode(values[AppLockPreferences.timeoutKey(uid)])
    override fun setEnabled(uid: String, enabled: Boolean) { values[AppLockPreferences.enabledKey(uid)] = enabled.toString() }
    override fun setTimeout(uid: String, timeout: AppLockTimeout) { values[AppLockPreferences.timeoutKey(uid)] = timeout.name }
    override fun clear(uid: String) { values.remove(AppLockPreferences.enabledKey(uid)); values.remove(AppLockPreferences.timeoutKey(uid)) }
}

/**
 * Time sources for the background timer: [monotonicMillis] can't be changed by the user (it may
 * reset on reboot, which also ends the process); [wallMillis] covers time the monotonic source
 * might not count. The lock uses the larger elapsed value and locks if the wall clock went back.
 */
interface AppLockClock {
    fun monotonicMillis(): Long
    fun wallMillis(): Long
}

enum class AppLockState {
    /** No signed-in user, or app lock is off: nothing to unlock. */
    NOT_REQUIRED,
    UNLOCKED,
    LOCKED
}

/** Settings view of the lock for the signed-in user. */
data class AppLockSettings(val available: BiometricAvailability, val kind: BiometricKind, val enabled: Boolean, val timeout: AppLockTimeout)

/**
 * Central app-lock state. Inputs are explicit events — the signed-in account changing, the app
 * moving to the background/foreground (the platform's process lifecycle, never navigation) and
 * unlock attempts — so the decision lives in one place and is unit-testable.
 *
 * - A cold start (process created, including after process death) of a signed-in account with
 *   lock enabled starts LOCKED unless the timeout is NEVER.
 * - Returning from the background locks when the elapsed time reaches the timeout; if elapsed
 *   time can't be trusted (unknown background time, wall clock moved backwards) it locks.
 * - Signing out or switching accounts drops any unlock; preferences are per account.
 */
class AppLockManager(
    private val preferences: AppLockPreferences,
    private val authenticator: BiometricAuthenticator?,
    private val clock: AppLockClock
) {
    private val mutableState = MutableStateFlow(AppLockState.NOT_REQUIRED)
    val state: StateFlow<AppLockState> = mutableState.asStateFlow()
    private var uid: String? = null
    private var backgroundedAt: Pair<Long, Long>? = null
    private var inBackground = false

    private var initialized = false

    /**
     * Call with the account once Firebase finished restoring (null when signed out) and on every
     * change. The first report is the process start: a restored session is locked; a later
     * sign-in has just proved the user's identity, so it starts unlocked.
     */
    fun onAccountChanged(newUid: String?) {
        val firstReport = !initialized
        initialized = true
        if (!firstReport && newUid == uid) return
        // Signed out (explicitly or because Firebase invalidated the session): forget that account's lock.
        if (newUid == null) uid?.let(preferences::clear)
        uid = newUid
        backgroundedAt = null
        mutableState.value = when {
            newUid == null || !isEnabled(newUid) -> AppLockState.NOT_REQUIRED
            firstReport && preferences.timeout(newUid) != AppLockTimeout.NEVER -> AppLockState.LOCKED
            else -> AppLockState.UNLOCKED
        }
    }

    fun onBackground() {
        if (inBackground) return
        inBackground = true
        if (mutableState.value == AppLockState.UNLOCKED) backgroundedAt = clock.monotonicMillis() to clock.wallMillis()
    }

    fun onForeground() {
        if (!inBackground) return
        inBackground = false
        val user = uid ?: return
        if (mutableState.value != AppLockState.UNLOCKED) return
        val limit = preferences.timeout(user).millis ?: return
        val since = backgroundedAt
        backgroundedAt = null
        if (since == null) { mutableState.value = AppLockState.LOCKED; return }
        val monotonic = clock.monotonicMillis() - since.first
        val wall = clock.wallMillis() - since.second
        val untrusted = monotonic < 0 || wall < 0
        if (untrusted || maxOf(monotonic, wall) >= limit) mutableState.value = AppLockState.LOCKED
    }

    /** Shows the system prompt; only success unlocks. */
    suspend fun unlock(reason: String): BiometricResult {
        val result = authenticator?.authenticate(reason) ?: BiometricResult.Unavailable
        if (result == BiometricResult.Success && uid != null && mutableState.value == AppLockState.LOCKED) {
            mutableState.value = AppLockState.UNLOCKED
        }
        return result
    }

    fun settings(): AppLockSettings? {
        val user = uid ?: return null
        return AppLockSettings(
            available = authenticator?.availability() ?: BiometricAvailability.UNAVAILABLE,
            kind = authenticator?.kind() ?: BiometricKind.BIOMETRIC,
            enabled = isEnabled(user),
            timeout = preferences.timeout(user)
        )
    }

    /** Enabling requires a successful prompt first; anything else leaves the setting off. */
    suspend fun enable(reason: String): BiometricResult {
        val user = uid ?: return BiometricResult.Unavailable
        if (authenticator?.availability() != BiometricAvailability.AVAILABLE) return BiometricResult.Unavailable
        val result = authenticator.authenticate(reason)
        if (result == BiometricResult.Success && uid == user) {
            preferences.setEnabled(user, true)
            mutableState.value = AppLockState.UNLOCKED
        }
        return result
    }

    /** Disabling also asks for device authentication so someone holding an unlocked phone can't turn protection off silently. */
    suspend fun disable(reason: String): BiometricResult {
        val user = uid ?: return BiometricResult.Unavailable
        val result = authenticator?.takeIf { it.availability() == BiometricAvailability.AVAILABLE }?.authenticate(reason) ?: BiometricResult.Success
        if (result == BiometricResult.Success && uid == user) {
            preferences.setEnabled(user, false)
            mutableState.value = AppLockState.NOT_REQUIRED
        }
        return result
    }

    fun setTimeout(timeout: AppLockTimeout) {
        uid?.let { preferences.setTimeout(it, timeout) }
    }

    /** Explicit sign-out: same as the account becoming null (unlock state and lock preferences dropped). */
    fun onSignedOut() = onAccountChanged(null)

    /**
     * The saved choice alone: if biometrics are later removed the lock stays on (the unlock screen
     * then offers signing in again) rather than silently switching protection off.
     */
    private fun isEnabled(user: String) = preferences.enabled(user)
}
