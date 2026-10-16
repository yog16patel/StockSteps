package org.example.stocksteps

import android.content.pm.ApplicationInfo
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.network.ApiLogSettings
import org.example.stocksteps.network.ApiLogWriter
import org.example.stocksteps.network.ApiLogging
import kotlin.test.*

class AndroidApiLoggingTest {
    @Test fun onlyADebugBuildOfADebuggableAppMayLog() {
        assertTrue(AndroidApiLogging.allowed(debugBuild = true, applicationFlags = ApplicationInfo.FLAG_DEBUGGABLE))
        assertFalse(AndroidApiLogging.allowed(debugBuild = false, applicationFlags = ApplicationInfo.FLAG_DEBUGGABLE), "release BuildConfig")
        assertFalse(AndroidApiLogging.allowed(debugBuild = true, applicationFlags = 0), "non-debuggable install")
    }

    /** The real OkHttp client: the plugin is installed, sees the refused connection and rethrows it. */
    @Test fun appClientLogsAConnectionRefusedFailure() = runBlocking<Unit> {
        val lines = mutableListOf<String>()
        try {
            ApiLogging.enable(true, ApiLogSettings({ "REAL" }, ApiLogWriter({ _, line -> lines += line }, dispatcher = null)))
            createBackendClient().use { client -> assertFails { client.get("http://127.0.0.1:1/api/v1/me/portfolio") } }
        } finally {
            ApiLogging.disable()
        }
        val error = lines.single { it.startsWith("ERR ") }
        assertTrue("env=REAL method=GET path=/api/v1/me/portfolio kind=CONNECTION_REFUSED type=ConnectException" in error, error)
        assertEquals(1, lines.count { it.startsWith("REQ ") })
    }

    @Test fun appClientIsSilentWhileLoggingIsOff() = runBlocking<Unit> {
        ApiLogging.disable()
        createBackendClient().use { client -> assertFails { client.get("http://127.0.0.1:1/api/v1/meta") } }
        assertNull(ApiLogging.settings)
    }
}
