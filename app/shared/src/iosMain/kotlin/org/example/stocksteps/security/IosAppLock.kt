package org.example.stocksteps.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.example.stocksteps.data.account.AccountSubscription
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import platform.LocalAuthentication.LABiometryTypeFaceID
import platform.LocalAuthentication.LABiometryTypeTouchID
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorBiometryLockout
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

/** Lock preferences in UserDefaults (ordinary settings; Firebase keeps the session in the Keychain). */
internal class IosAppLockPreferences : AppLockPreferences {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun enabled(uid: String) = defaults.boolForKey(AppLockPreferences.enabledKey(uid))
    override fun timeout(uid: String) = AppLockTimeout.decode(defaults.stringForKey(AppLockPreferences.timeoutKey(uid)))
    override fun setEnabled(uid: String, enabled: Boolean) = defaults.setBool(enabled, AppLockPreferences.enabledKey(uid))
    override fun setTimeout(uid: String, timeout: AppLockTimeout) = defaults.setObject(timeout.name, AppLockPreferences.timeoutKey(uid))
    override fun clear(uid: String) {
        defaults.removeObjectForKey(AppLockPreferences.enabledKey(uid))
        defaults.removeObjectForKey(AppLockPreferences.timeoutKey(uid))
    }
}

/**
 * systemUptime can't be changed by the user but pauses while the device sleeps; the wall clock
 * covers sleep. AppLockManager takes the larger elapsed value and locks if the wall clock went back.
 */
internal object IosAppLockClock : AppLockClock {
    override fun monotonicMillis() = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
    override fun wallMillis() = (NSDate().timeIntervalSince1970 * 1000).toLong()
}

/** LocalAuthentication: Face ID / Touch ID with the device passcode as fallback (system UI only). */
@OptIn(ExperimentalForeignApi::class)
internal class IosBiometricAuthenticator : BiometricAuthenticator {
    override fun availability(): BiometricAvailability {
        val context = LAContext()
        if (context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, null)) return BiometricAvailability.AVAILABLE
        return BiometricAvailability.NOT_ENROLLED.takeIf { context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null) }
            ?: BiometricAvailability.UNAVAILABLE
    }

    override fun kind(): BiometricKind {
        val context = LAContext()
        context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)
        return when (context.biometryType) {
            LABiometryTypeFaceID -> BiometricKind.FACE_ID
            LABiometryTypeTouchID -> BiometricKind.TOUCH_ID
            else -> BiometricKind.DEVICE_CREDENTIAL
        }
    }

    override suspend fun authenticate(reason: String): BiometricResult = suspendCancellableCoroutine { continuation ->
        val context = LAContext()
        context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, reason) { success, error ->
            if (!continuation.isActive) return@evaluatePolicy
            continuation.resume(when {
                success -> BiometricResult.Success
                else -> when (error?.code) {
                    LAErrorUserCancel, LAErrorSystemCancel, LAErrorAppCancel -> BiometricResult.Cancelled
                    LAErrorBiometryLockout -> BiometricResult.Lockout
                    LAErrorBiometryNotAvailable, LAErrorBiometryNotEnrolled, LAErrorPasscodeNotSet -> BiometricResult.Unavailable
                    else -> BiometricResult.Failure(null)
                }
            })
        }
        continuation.invokeOnCancellation { context.invalidate() }
    }
}

/**
 * SwiftUI facade over [AppLockManager]. Swift forwards scene-phase changes and the signed-in uid;
 * results come back through callbacks on the main queue so no Kotlin coroutine types cross over.
 */
class IosAppLock {
    private val manager = AppLockManager(IosAppLockPreferences(), IosBiometricAuthenticator(), IosAppLockClock)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun observe(onChange: (AppLockState) -> Unit): AccountSubscription {
        val job = scope.launch { manager.state.collect { onChange(it) } }
        return object : AccountSubscription { override fun cancel() = job.cancel() }
    }

    fun onAccountChanged(uid: String?) = manager.onAccountChanged(uid)
    fun onBackground() = manager.onBackground()
    fun onForeground() = manager.onForeground()
    fun settings(): AppLockSettings? = manager.settings()
    fun setTimeout(timeout: AppLockTimeout) = manager.setTimeout(timeout)

    fun unlock(reason: String, completion: (BiometricResult) -> Unit) { scope.launch { completion(manager.unlock(reason)) } }
    fun enable(reason: String, completion: (BiometricResult) -> Unit) { scope.launch { completion(manager.enable(reason)) } }
    fun disable(reason: String, completion: (BiometricResult) -> Unit) { scope.launch { completion(manager.disable(reason)) } }

    fun close() = scope.cancel()
}
