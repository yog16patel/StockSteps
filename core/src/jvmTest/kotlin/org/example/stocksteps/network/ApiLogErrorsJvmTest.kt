package org.example.stocksteps.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** The JVM (Android OkHttp) network exceptions, classified by the shared plugin. */
class ApiLogErrorsJvmTest {
    private fun logFor(failure: Throwable): String {
        val lines = mutableListOf<String>()
        val settings = ApiLogSettings({ "REAL" }, ApiLogWriter({ _, line -> lines += line }, dispatcher = null))
        runBlocking {
            HttpClient(MockEngine { throw failure }) {
                configureStockStepsClient()
                install(StockStepsApiLogging) { this.settings = { settings } }
            }.use { http -> assertFails { http.get("http://192.0.2.10:8082/api/v1/me/practice") } }
        }
        return lines.single { it.startsWith("ERR ") }
    }

    @Test fun connectionRefusedDnsTlsAndTimeoutsAreDistinct() {
        val refused = logFor(java.net.ConnectException("Failed to connect to /192.0.2.10:8082").apply { initCause(java.net.ConnectException("Connection refused")) })
        assertTrue("kind=CONNECTION_REFUSED type=ConnectException" in refused, refused)
        assertTrue("env=REAL method=GET path=/api/v1/me/practice" in refused, refused)
        assertTrue("kind=CONNECT_FAILED" in logFor(java.net.ConnectException("Failed to connect")))
        val unreachable = java.net.ConnectException("Failed to connect to /192.0.2.10:8082").apply { initCause(java.net.ConnectException("failed to connect to /192.0.2.10 (port 8082): connect failed: EHOSTUNREACH (No route to host)")) }
        assertTrue("kind=NO_ROUTE type=ConnectException" in logFor(unreachable))
        assertTrue("kind=DNS type=UnknownHostException" in logFor(java.net.UnknownHostException("Unable to resolve host \"stocksteps.invalid\"")))
        assertTrue("kind=TLS type=SSLHandshakeException" in logFor(javax.net.ssl.SSLHandshakeException("Chain validation failed")))
        assertTrue("kind=TIMEOUT_SOCKET type=SocketTimeoutException" in logFor(java.net.SocketTimeoutException("timeout")))
        assertTrue("kind=NETWORK type=IOException" in logFor(java.io.IOException("unexpected end of stream")))
    }

    @Test fun stackTracesNeverReachTheLine() {
        val line = logFor(java.net.ConnectException("Connection refused"))
        assertFalse("\tat " in line || "Exception:" in line.substringAfter("message="), line)
    }
}
