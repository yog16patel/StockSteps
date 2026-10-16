package org.example.stocksteps

import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.network.ApiLogSettings
import org.example.stocksteps.network.ApiLogWriter
import org.example.stocksteps.network.ApiLogging
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.*

class IosApiLoggingTest {
    /** The real Darwin client: a refused connection is named from its NSURLError code. */
    @Test fun appClientLogsAConnectionRefusedFailure() = runBlocking<Unit> {
        val lines = mutableListOf<String>()
        try {
            ApiLogging.enable(true, ApiLogSettings({ "MOCK" }, ApiLogWriter({ _, line -> lines += line }, dispatcher = null), errorKind = ::darwinErrorKind))
            createBackendClient().use { client -> assertFails { client.get("http://127.0.0.1:1/api/v1/me/practice?range=1M") } }
        } finally {
            ApiLogging.disable()
        }
        val error = lines.single { it.startsWith("ERR ") }
        assertTrue("env=MOCK method=GET path=/api/v1/me/practice kind=CONNECTION_REFUSED" in error, error)
        assertTrue(lines.single { it.startsWith("REQ ") }.contains("query=range=1M"))
    }

    @Test fun otherFailuresFallBackToTheSharedClassification() {
        assertNull(darwinErrorKind(IllegalStateException("x")))
    }
}

@OptIn(ExperimentalNativeApi::class)
class IosApiLoggingSinkTest {
    /** The real NSLog sink, with format characters in the line (a varargs NSLog call crashed here). */
    @Test fun nsLogSinkWritesLinesWithPercentSignsAndLongLines() {
        IosApiLogging.sink.write(org.example.stocksteps.network.ApiLogLevel.INFO, "RES id=t-0001 body={\"change\":\"-0.24%\",\"text\":\"%@ %s %d\"}")
        IosApiLogging.sink.write(org.example.stocksteps.network.ApiLogLevel.WARN, "x".repeat(2_000))
    }

    @Test fun enableUsesTheDebugBinaryFlag() {
        try {
            assertEquals(kotlin.native.Platform.isDebugBinary, IosApiLogging.enable { "MOCK" })
        } finally {
            ApiLogging.disable()
        }
    }
}
