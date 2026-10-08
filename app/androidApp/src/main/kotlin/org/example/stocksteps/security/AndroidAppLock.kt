package org.example.stocksteps.security

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Lock preferences in SharedPreferences (ordinary settings; no secrets are stored). */
internal class AndroidAppLockPreferences(context: Context) : AppLockPreferences {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    override fun enabled(uid: String) = preferences.getBoolean(AppLockPreferences.enabledKey(uid), false)
    override fun timeout(uid: String) = AppLockTimeout.decode(preferences.getString(AppLockPreferences.timeoutKey(uid), null))
    override fun setEnabled(uid: String, enabled: Boolean) { preferences.edit().putBoolean(AppLockPreferences.enabledKey(uid), enabled).apply() }
    override fun setTimeout(uid: String, timeout: AppLockTimeout) { preferences.edit().putString(AppLockPreferences.timeoutKey(uid), timeout.name).apply() }
    override fun clear(uid: String) { preferences.edit().remove(AppLockPreferences.enabledKey(uid)).remove(AppLockPreferences.timeoutKey(uid)).apply() }
    private companion object { const val FILE = "stocksteps-app-lock" }
}

/** elapsedRealtime counts deep sleep and can't be changed by the user; it resets only on reboot. */
internal object AndroidAppLockClock : AppLockClock {
    override fun monotonicMillis() = SystemClock.elapsedRealtime()
    override fun wallMillis() = System.currentTimeMillis()
}

/**
 * AndroidX BiometricPrompt with device-credential fallback. The prompt needs a live
 * FragmentActivity; the activity attaches itself in onCreate and detaches in onDestroy, so a
 * configuration change never shows a prompt on a destroyed activity.
 */
internal class AndroidBiometricAuthenticator(private val context: Context) : BiometricAuthenticator {
    private var activity: FragmentActivity? = null
    private var prompt: BiometricPrompt? = null

    fun attach(activity: FragmentActivity) { this.activity = activity }
    fun detach(activity: FragmentActivity) {
        if (this.activity === activity) { prompt?.cancelAuthentication(); prompt = null; this.activity = null }
    }

    /** Strong biometrics or device credential where combinable; API 28–29 only allows weak + credential. */
    private val authenticators = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        else -> BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }

    override fun availability(): BiometricAvailability = when (BiometricManager.from(context).canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
        else -> BiometricAvailability.UNAVAILABLE
    }

    override fun kind(): BiometricKind {
        val packages = context.packageManager
        val strong = BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        return when {
            strong && packages.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
                !packages.hasSystemFeature(PackageManager.FEATURE_FACE) -> BiometricKind.FINGERPRINT
            strong -> BiometricKind.BIOMETRIC
            else -> BiometricKind.DEVICE_CREDENTIAL
        }
    }

    override suspend fun authenticate(reason: String): BiometricResult {
        val host = activity ?: return BiometricResult.Unavailable
        if (availability() != BiometricAvailability.AVAILABLE) return BiometricResult.Unavailable
        return suspendCancellableCoroutine { continuation ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(BiometricResult.Success)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!continuation.isActive) return
                    continuation.resume(when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED -> BiometricResult.Cancelled
                        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricResult.Lockout
                        BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> BiometricResult.Unavailable
                        else -> BiometricResult.Failure(null)
                    })
                }
                // A single non-matching attempt keeps the system prompt open; nothing to do.
                override fun onAuthenticationFailed() = Unit
            }
            val biometricPrompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host), callback)
            prompt = biometricPrompt
            biometricPrompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock StockSteps")
                    .setSubtitle(reason)
                    .setAllowedAuthenticators(authenticators)
                    .build()
            )
            continuation.invokeOnCancellation { biometricPrompt.cancelAuthentication() }
        }
    }
}

/**
 * Process-lifetime owner (survives rotation, not process death — which correctly re-locks).
 * The activity forwards its start/stop (the app's foreground/background) and attaches itself
 * as the prompt host.
 */
internal class AndroidAppLockOwner(application: Application) : AndroidViewModel(application) {
    val authenticator = AndroidBiometricAuthenticator(application)
    val manager = AppLockManager(AndroidAppLockPreferences(application), authenticator, AndroidAppLockClock)
}
