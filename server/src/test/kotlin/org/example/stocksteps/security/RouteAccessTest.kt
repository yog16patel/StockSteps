package org.example.stocksteps.security

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.example.stocksteps.appconfig.DataMode
import org.example.stocksteps.stockStepsModule
import kotlin.test.*

/**
 * Phase 9A: the route table ([RouteAccess]) against the real MOCK server (every route either mode registers; MOCK registers the
 * internal, debug and persona routes too):
 * - every registered route has exactly its own rule, and every rule names a registered route;
 * - `/api/v1/me…` rules are AUTHENTICATED or USER_OWNED, `/internal/…` rules ADMIN_INTERNAL, nothing else is either;
 * - every signed-in route refuses a request without a token (401 `SIGN_IN_REQUIRED`) and with a forged one, through the central gate;
 * - every public GET route answers a guest without 401/403.
 */
class RouteAccessTest {
    private lateinit var app: Application

    private fun withMockServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { stockStepsModule(DataMode.MOCK); app = this }
        startApplication()
        block()
    }

    /** `(GET, /api/v1/stocks/{symbol}/details)` for every registered handler. */
    private fun registered(): Set<Pair<HttpMethod, String>> = app.plugin(RoutingRoot).getAllRoutes().mapNotNull { node ->
        val text = node.toString()
        val method = Regex("""\(method:([A-Z]+)\)""").find(text)?.groupValues?.get(1) ?: return@mapNotNull null
        val path = text.substringBefore("/(method:").replace(Regex("/+"), "/").trimEnd('/').ifEmpty { "/" }
        HttpMethod.parse(method) to path
    }.toSet()

    private fun sample(pattern: String) = pattern
        .replace("{symbol}", "AAPL").replace("{articleId}", "a1").replace("{reportId}", "AAPL:2026-Q3").replace("{eventId}", "AAPL:2026-Q3")
        .replace(Regex("""\{[A-Za-z]+\}"""), "x1")

    @Test fun everyRegisteredRouteHasExactlyItsOwnRule() = withMockServer {
        val routes = registered()
        assertTrue(routes.size > 100, "the MOCK server registers every route (${routes.size})")
        val unclassified = routes.filter { (m, p) -> RouteAccess.RULES.none { it.method == m && it.pattern == p } }
        assertTrue(unclassified.isEmpty(), "routes without an access rule: $unclassified")
        val stale = RouteAccess.RULES.filter { r -> (r.method to r.pattern) !in routes }
        assertTrue(stale.isEmpty(), "rules for routes that don't exist: ${stale.map { it.method.value + " " + it.pattern }}")
        routes.forEach { (m, p) -> assertEquals(p, RouteAccess.of(m, sample(p))?.pattern, "first match for $m $p") }
    }

    @Test fun accessClassesFollowThePathPrefixes() {
        RouteAccess.RULES.forEach { r ->
            when {
                requiresAccount(r.pattern) -> assertTrue(r.access == AccessClass.AUTHENTICATED || r.access == AccessClass.USER_OWNED, r.pattern)
                r.pattern.startsWith("/internal/") -> assertEquals(AccessClass.ADMIN_INTERNAL, r.access, r.pattern)
                r.pattern.startsWith("/health") -> assertEquals(AccessClass.HEALTH_INTERNAL, r.access, r.pattern)
                else -> assertEquals(AccessClass.PUBLIC, r.access, r.pattern)
            }
            if (r.access != AccessClass.HEALTH_INTERNAL) assertNotNull(r.limit, "${r.pattern} is rate limited")
            if (r.access == AccessClass.USER_OWNED && r.method != HttpMethod.Get)
                assertTrue(r.limit == RateLimitClass.USER_WRITE || r.limit == RateLimitClass.EXPENSIVE_OPERATION || r.pattern.endsWith("/preview") || r.pattern == "/api/v1/me/practice",
                    "${r.method.value} ${r.pattern} writes are USER_WRITE")
            if (("/ai/" in r.pattern && !r.pattern.endsWith("/usage")) || r.pattern.endsWith("/ask") || r.pattern.endsWith("/insight") || r.pattern.endsWith("/movement") || r.pattern.endsWith("/export"))
                assertEquals(RateLimitClass.EXPENSIVE_OPERATION, r.limit, r.pattern)
        }
        assertEquals("search", RouteAccess.of(HttpMethod.Get, "/api/v1/stocks/search")?.bucket)
        assertEquals("/api/v1/me/earnings/reminders", RouteAccess.of(HttpMethod.Get, "/api/v1/me/earnings/reminders")?.pattern, "literal before {symbol}")
        assertEquals(RateLimitClass.AUTHENTICATED_STANDARD, RouteAccess.fallback("/api/v1/me/unknown"))
        assertEquals(RateLimitClass.ADMIN_INTERNAL, RouteAccess.fallback("/internal/unknown"))
        assertNull(RouteAccess.fallback("/health/live"))
    }

    @Test fun everySignedInRouteRefusesMissingAndForgedTokens() = withMockServer {
        val signedIn = RouteAccess.RULES.filter { requiresAccount(it.pattern) }
        assertTrue(signedIn.size > 80)
        val unsignedJwt = "eyJhbGciOiJub25lIn0.eyJ1c2VyX2lkIjoiIn0.x"   // MOCK accepts unsigned tokens with a uid; this one has none
        for (rule in signedIn) {
            val path = sample(rule.pattern)
            for (auth in listOf<String?>(null, "Bearer garbage", "Basic YWxpY2U6c2VjcmV0", "Bearer $unsignedJwt", "Bearer ")) {
                val response = client.request(path) {
                    method = rule.method
                    auth?.let { header(HttpHeaders.Authorization, it) }
                    header("X-User-Id", "alice"); header("X-Forwarded-User", "alice")      // never trusted
                    if (rule.method != HttpMethod.Get && rule.method != HttpMethod.Delete) { contentType(ContentType.Application.Json); setBody("""{"uid":"alice"}""") }
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status, "${rule.method.value} $path with ${auth ?: "no token"}")
                assertTrue(response.bodyAsText().contains("SIGN_IN_REQUIRED"))
                assertTrue(response.headers[HttpHeaders.WWWAuthenticate]!!.startsWith("Bearer"))
                assertEquals("no-store, private", response.headers[HttpHeaders.CacheControl])
            }
        }
    }

    @Test fun publicRoutesServeGuestsWithoutSignIn() = withMockServer {
        val guestGets = RouteAccess.RULES.filter { it.access == AccessClass.PUBLIC && it.method == HttpMethod.Get }
        for (rule in guestGets) {
            val path = sample(rule.pattern) + when {
                rule.pattern.endsWith("/search") -> "?query=apple"
                rule.pattern.endsWith("/watch-data") -> "?symbols=AAPL,MSFT"
                rule.pattern.startsWith("/api/v1/compare") -> "?symbols=AAPL,MSFT"
                rule.pattern == "/api/v1/earnings/calendar/search" -> "?q=apple"
                rule.pattern == "/api/v1/home/personas/{id}" -> ""
                else -> ""
            }
            val status = client.get(path.replace("/personas/x1", "/personas/new-user")).status
            assertTrue(status != HttpStatusCode.Unauthorized && status != HttpStatusCode.Forbidden && status != HttpStatusCode.TooManyRequests,
                "guest GET $path → $status")
        }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/search?query=apple").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/stocks/AAPL/details").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/screener/catalog").status)
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/screener/search") { contentType(ContentType.Application.Json); setBody("{}") }.status)
    }
}
