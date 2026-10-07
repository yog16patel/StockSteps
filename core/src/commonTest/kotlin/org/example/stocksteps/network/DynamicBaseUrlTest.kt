package org.example.stocksteps.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicBaseUrlTest {
    /** Switching the backend must affect the very next request of an existing client. */
    @Test fun eachRequestUsesTheCurrentBaseUrl() = runTest {
        val hosts = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            hosts += "${request.url.host}:${request.url.port}"
            respond("""{"dataMode":"mock"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }
        var url = "http://127.0.0.1:8081"
        val api = StockStepsApi(client) { url }
        api.getBackendInfo()
        url = "https://api.example.com"
        api.getBackendInfo()
        assertEquals(listOf("127.0.0.1:8081", "api.example.com:443"), hosts)
        client.close()
    }
}
