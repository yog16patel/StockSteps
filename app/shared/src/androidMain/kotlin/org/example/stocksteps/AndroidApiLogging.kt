package org.example.stocksteps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import org.example.stocksteps.network.ApiLogLevel
import org.example.stocksteps.network.ApiLogSettings
import org.example.stocksteps.network.ApiLogSink
import org.example.stocksteps.network.ApiLogWriter
import org.example.stocksteps.network.ApiLogging
import org.example.stocksteps.network.start

/**
 * Android side of the debug API log (`adb logcat -s StockSteps-API`). On only when the caller's
 * `BuildConfig.DEBUG` is true *and* the installed app is debuggable, so a release build can't
 * enable it even if the call were left in. Replaces the earlier `StockStepsBackend` interceptor.
 */
object AndroidApiLogging {
    private const val CHUNK = 3_500 // below Logcat's per-line limit

    private val sink = ApiLogSink { level, line ->
        val parts = line.chunked(CHUNK)
        parts.forEachIndexed { index, part ->
            val text = if (parts.size == 1) part else "[${index + 1}/${parts.size}] $part"
            if (level == ApiLogLevel.WARN) Log.w(ApiLogging.TAG, text) else Log.i(ApiLogging.TAG, text)
        }
    }

    /** [environment]: the explicit MOCK/REAL choice (`BackendRouter.currentEnvironment`). */
    fun enable(context: Context, debugBuild: Boolean, environment: () -> String): Boolean {
        return ApiLogging.enable(allowed(debugBuild, context.applicationInfo.flags), ApiLogSettings(environment, ApiLogWriter(sink)))
    }

    internal fun allowed(debugBuild: Boolean, applicationFlags: Int) = debugBuild && (applicationFlags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun start(stored: String, effective: String, real: String?, mock: String?) = ApiLogging.start(stored, effective, real, mock)
}
