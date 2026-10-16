package org.example.stocksteps

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import org.example.stocksteps.network.ApiLogSettings
import org.example.stocksteps.network.ApiLogSink
import org.example.stocksteps.network.ApiLogWriter
import org.example.stocksteps.network.ApiLogging
import org.example.stocksteps.network.start
import platform.Foundation.NSLog
import kotlin.experimental.ExperimentalNativeApi

/**
 * iOS side of the debug API log: the same lines as Android's `StockSteps-API` Logcat tag, printed
 * with NSLog and prefixed `[StockSteps-API]` (Xcode console; `log stream` with that text). Swift calls
 * [enable] only under `#if DEBUG`, and it also requires a debug Kotlin framework, so a Release build
 * can't turn it on.
 */
object IosApiLogging {
    private const val CHUNK = 900 // unified logging truncates long NSLog messages

    internal val sink = ApiLogSink { _, line ->
        val parts = line.chunked(CHUNK)
        parts.forEachIndexed { index, part ->
            val text = if (parts.size == 1) part else "[${index + 1}/${parts.size}] $part"
            // The line is the format itself (percent signs escaped): a Kotlin String passed through NSLog's C varargs
            // isn't an NSString, and "%@" with it crashes (EXC_BAD_ACCESS, seen on the simulator).
            NSLog("[${ApiLogging.TAG}] $text".replace("%", "%%"))
        }
    }

    /** [environment]: the explicit MOCK/REAL choice (`BackendSettings.environment`), not the URL. */
    @OptIn(ExperimentalNativeApi::class)
    fun enable(environment: () -> String): Boolean =
        ApiLogging.enable(kotlin.native.Platform.isDebugBinary, ApiLogSettings(environment, ApiLogWriter(sink), errorKind = ::darwinErrorKind))

    fun start(stored: String, effective: String, real: String?, mock: String?) = ApiLogging.start(stored, effective, real, mock)
}

/** `NSURLError` codes behind Darwin engine failures (the exception type alone says nothing). */
internal fun darwinErrorKind(cause: Throwable): String? {
    val code = (cause as? DarwinHttpRequestException)?.origin?.code ?: return null
    return when (code) {
        -1001L -> "TIMEOUT"
        -1003L, -1006L -> "DNS"
        -1004L -> "CONNECTION_REFUSED"
        -1005L -> "CONNECTION_LOST"
        -1009L -> "OFFLINE"
        -1022L -> "ATS_BLOCKED"
        -999L -> "CANCELLED"
        in -1206L..-1200L, -2000L -> "TLS"
        else -> "NETWORK_NSURL_$code"
    }
}
