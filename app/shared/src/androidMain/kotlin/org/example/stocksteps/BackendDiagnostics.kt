package org.example.stocksteps

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Debug-only request log for backend selection (`adb logcat -s StockStepsBackend`): mode, resolved URL, status or exception.
 * Off unless the debug app enables it. Never logs headers or bodies; user info and secret-looking query values are redacted.
 */
object BackendDiagnostics {
    @Volatile var enabled: Boolean = false
    /** The current mode and base URL, supplied by the host (e.g. "REAL http://127.0.0.1:8080"). */
    @Volatile var describe: () -> String = { "unknown" }

    internal const val TAG = "StockStepsBackend"
    private val SECRET = Regex("key|token|secret|password|auth|signature", RegexOption.IGNORE_CASE)

    fun log(message: String) { if (enabled) Log.d(TAG, message) }

    internal fun redact(url: HttpUrl): String = url.newBuilder().username("").password("").apply {
        url.queryParameterNames.filter { SECRET.containsMatchIn(it) }.forEach { setQueryParameter(it, "REDACTED") }
    }.build().toString()

    internal val interceptor = Interceptor { chain ->
        if (!enabled) return@Interceptor chain.proceed(chain.request())
        val request = chain.request()
        val line = "mode=${describe()} ${request.method} ${redact(request.url)}"
        val response: Response = try {
            chain.proceed(request)
        } catch (cause: Exception) {
            Log.w(TAG, "$line failed: ${cause::class.java.simpleName}: ${cause.message}")
            throw cause
        }
        Log.d(TAG, "$line -> ${response.code}")
        response
    }
}
