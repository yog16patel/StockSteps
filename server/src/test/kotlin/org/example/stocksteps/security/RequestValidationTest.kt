package org.example.stocksteps.security

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.example.stocksteps.appconfig.DataMode
import org.example.stocksteps.stockStepsModule
import kotlin.test.*

/**
 * Phase 9A input validation and headers on the real MOCK server: size limits, path and query validation, content types, methods,
 * malformed bodies (4xx with stable codes, never 500), no CORS, `nosniff`, and no internal details in any error body.
 */
class RequestValidationTest {
    private fun server(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { stockStepsModule(DataMode.MOCK) }
        block()
    }
    private val alice = "mock-user:alice-9a"

    private fun assertSafe(body: String) {
        for (leak in listOf("Exception", "at org.", "kotlinx.serialization", "stack", "/Users/", "apikey", "Bearer")) assertFalse(body.contains(leak), "body leaks $leak: $body")
    }

    @Test fun sizeLimitsAreEnforcedBeforeHandlers() = server {
        val big = client.post("/api/v1/me/watchlists") { bearerAuth(alice); contentType(ContentType.Application.Json); setBody("{\"name\":\"" + "x".repeat(300_000) + "\"}") }
        assertEquals(HttpStatusCode.PayloadTooLarge, big.status); assertTrue(big.bodyAsText().contains("PAYLOAD_TOO_LARGE"))
        val aiBody = client.post("/api/v1/me/compare/ai/ask") { bearerAuth(alice); contentType(ContentType.Application.Json); setBody("{\"q\":\"" + "x".repeat(20_000) + "\"}") }
        assertEquals(HttpStatusCode.PayloadTooLarge, aiBody.status, "AI routes accept small bodies only")
        assertEquals(HttpStatusCode.fromValue(414), client.get("/api/v1/stocks/search?query=" + "a".repeat(3_000)).status)
    }

    @Test fun pathQueryAndPaginationInputIsValidated() = server {
        for (path in listOf("/api/v1/stocks/%3Cscript%3E/details", "/api/v1/stocks/AAPL%2F..%2Fx/quote", "/api/v1/stocks/" + "A".repeat(30) + "/profile"))
            assertEquals(HttpStatusCode.BadRequest, client.get(path).status, path)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/search?query=").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/search?query=" + "a".repeat(101)).status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/news?limit=1000").status, "page sizes are capped")
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/AAPL/news?page=99").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/stocks/watch-data?symbols=" + (1..31).joinToString(",") { "S$it" }).status,
            "guests send at most 30 symbols per request")
        val traversal = client.delete("/api/v1/me/watchlists/..%2F..%2Fusers%2Fbob/entries/x") { bearerAuth(alice) }.status
        assertTrue(traversal == HttpStatusCode.BadRequest || traversal == HttpStatusCode.NotFound, "encoded traversal in an id never reaches storage: $traversal")
        assertEquals(HttpStatusCode.BadRequest, client.patch("/api/v1/me/watchlists/bad%20id") { bearerAuth(alice); contentType(ContentType.Application.Json); setBody("""{"name":"x"}""") }.status)
    }

    @Test fun malformedBodiesAndWrongContentTypesAreClientErrorsNotServerErrors() = server {
        val lines = mutableListOf<String>(); SecurityLog.reset(); SecurityLog.sink = { synchronized(lines) { lines += it } }
        for ((path, method) in listOf("/api/v1/me/watchlists" to HttpMethod.Post, "/api/v1/me/practice/orders/execute" to HttpMethod.Post,
            "/api/v1/me/portfolio/transactions" to HttpMethod.Post, "/api/v1/me/learning" to HttpMethod.Put, "/api/v1/screener/search" to HttpMethod.Post)) {
            val broken = client.request(path) { this.method = method; bearerAuth(alice); contentType(ContentType.Application.Json); setBody("{not json") }
            assertEquals(HttpStatusCode.BadRequest, broken.status, "$path malformed JSON"); assertSafe(broken.bodyAsText())
            val wrongType = client.request(path) { this.method = method; bearerAuth(alice); contentType(ContentType.Text.Plain); setBody("hello") }
            assertTrue(wrongType.status == HttpStatusCode.UnsupportedMediaType || wrongType.status == HttpStatusCode.BadRequest, "$path text/plain → ${wrongType.status}")
            assertSafe(wrongType.bodyAsText())
        }
        SecurityLog.sink = null
        assertTrue(lines.any { "event=invalid_input reason=status-415" in it } && lines.any { "event=invalid_input reason=status-400" in it }, lines.toString())
        assertTrue(lines.none { "hello" in it || "not json" in it }, "request content is never logged")
        // Chunked bodies without Content-Length (411) need real HTTP/1.1 framing, which the test engine can't produce: verified with curl
        // against a local MOCK server (RATE_LIMIT_ABUSE_TEST_RESULTS.md, real-socket checks).
    }

    @Test fun unsupportedMethodsAreRefused() = server {
        for ((path, method) in listOf("/api/v1/stocks/AAPL/details" to HttpMethod.Delete, "/api/v1/stocks/search" to HttpMethod.Post,
            "/api/v1/me/portfolio" to HttpMethod.Patch, "/api/v1/me/practice/orders/execute" to HttpMethod.Get, "/internal/alerts/evaluate" to HttpMethod.Get)) {
            val r = client.request(path) { this.method = method; bearerAuth(alice) }
            assertTrue(r.status == HttpStatusCode.MethodNotAllowed || r.status == HttpStatusCode.NotFound, "$method $path → ${r.status}")
        }
        val trace = client.request("/api/v1/stocks/AAPL/details") { method = HttpMethod("TRACE") }
        assertTrue(trace.status.value in 400..499, "TRACE is never echoed: ${trace.status}")
    }

    @Test fun noCorsAndSafeDefaultHeaders() = server {
        val r = client.get("/api/v1/stocks/AAPL/quote") { header(HttpHeaders.Origin, "https://evil.example") }
        assertNull(r.headers[HttpHeaders.AccessControlAllowOrigin], "no CORS: browsers keep same-origin")
        assertNull(r.headers[HttpHeaders.AccessControlAllowCredentials])
        assertEquals("nosniff", r.headers["X-Content-Type-Options"])
        val preflight = client.options("/api/v1/me/portfolio") { header(HttpHeaders.Origin, "https://evil.example"); header(HttpHeaders.AccessControlRequestMethod, "GET") }
        assertNull(preflight.headers[HttpHeaders.AccessControlAllowOrigin]); assertTrue(preflight.status.value >= 400, "preflight not granted: ${preflight.status}")
        val me = client.get("/api/v1/me/portfolio") { bearerAuth(alice) }
        assertEquals(HttpStatusCode.OK, me.status); assertEquals("no-store, private", me.headers[HttpHeaders.CacheControl])
    }
}
