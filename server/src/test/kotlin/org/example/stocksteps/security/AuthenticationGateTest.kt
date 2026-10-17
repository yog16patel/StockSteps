package org.example.stocksteps.security

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.example.stocksteps.configureApiErrors
import org.example.stocksteps.portfolio.PortfolioLedger
import org.example.stocksteps.userdata.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** The full Phase 9A security stack in the production order (`Application.stockStepsModule`), for route-level tests. */
internal fun Application.securedForTest(auth: UserAuthenticator, hops: Int? = null, limiter: RateLimiter = RateLimiter(),
                                       admission: AdmissionController = AdmissionController(), routes: Routing.() -> Unit) {
    configureApiErrors()
    install(ContentNegotiation) { json() }
    installSecurityObservability()
    val identities = ClientIdentityResolver(auth, hops)
    installAdmission(identities, admission)
    installRateLimits(identities, limiter)
    installUserAuthentication(auth)
    routing(routes)
}

/**
 * Phase 9A central authentication and failure policy: the gate protects every `/api/v1/me` route (even one registered without its own
 * check), the uid comes only from the verified token, one verification per request, stable codes, and nothing internal in the bodies.
 */
class AuthenticationGateTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** "ok-<uid>" → valid; "anon-<uid>" → anonymous account; "fail-<reason>" → that failure; anything else → INVALID. Counts calls. */
    private class ScriptedAuth : UserAuthenticator {
        val calls = AtomicInteger()
        override suspend fun uid(bearerToken: String) = (verify(bearerToken) as? TokenVerification.Valid)?.uid
        override suspend fun verify(bearerToken: String): TokenVerification {
            calls.incrementAndGet()
            return when {
                bearerToken.startsWith("ok-") -> TokenVerification.Valid(bearerToken.removePrefix("ok-"), "password")
                bearerToken.startsWith("anon-") -> TokenVerification.Valid(bearerToken.removePrefix("anon-"), "anonymous")
                bearerToken.startsWith("fail-") -> TokenVerification.Failure.entries.first { it.reason == bearerToken.removePrefix("fail-") }
                else -> TokenVerification.Failure.INVALID
            }
        }
    }

    @Test fun theGateProtectsRoutesThatForgotTheirOwnCheckAndExposesATypedPrincipal() = testApplication {
        val auth = ScriptedAuth()
        application { securedForTest(auth) {
            get("/api/v1/me/unchecked") { call.respondText("uid=" + call.principal()?.uid) }     // no user(auth) here on purpose
            get("/api/v1/stocks/{symbol}/quote") { call.respondText("guest-ok principal=" + call.principal()) }
        } }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/unchecked").status)
        assertEquals("uid=alice", client.get("/api/v1/me/unchecked") { bearerAuth("ok-alice") }.bodyAsText())
        assertEquals("guest-ok principal=null", client.get("/api/v1/stocks/AAPL/quote").bodyAsText(), "public routes need no token")
        assertEquals("guest-ok principal=null", client.get("/api/v1/stocks/AAPL/quote") { bearerAuth("fail-expired") }.bodyAsText(),
            "an expired token on a public route is treated as a guest, not refused")
    }

    @Test fun failuresMapToStableCodesWithoutInternalDetails() = testApplication {
        val auth = ScriptedAuth()
        application { securedForTest(auth) { get("/api/v1/me/thing") { call.respondText(call.requireUser(auth)!!) } } }
        for (reason in listOf("missing", "malformed", "expired", "wrong-project", "bad-signature", "invalid")) {
            val r = client.get("/api/v1/me/thing") { if (reason != "missing") bearerAuth("fail-$reason") }
            assertEquals(HttpStatusCode.Unauthorized, r.status, reason)
            val body = r.bodyAsText()
            assertTrue(body.contains("\"code\":\"SIGN_IN_REQUIRED\"") || body.contains("\"code\": \"SIGN_IN_REQUIRED\""), "$reason keeps the code both apps handle")
            for (leak in listOf("fail-", "Exception", "securetoken", "projects/", "certificate", "stack")) assertFalse(body.contains(leak), "$reason body leaks $leak")
            assertEquals(if (reason == "missing") "Bearer realm=\"stocksteps\"" else "Bearer realm=\"stocksteps\", error=\"invalid_token\"", r.headers[HttpHeaders.WWWAuthenticate])
        }
        val outage = client.get("/api/v1/me/thing") { bearerAuth("fail-unavailable") }
        assertEquals(HttpStatusCode.ServiceUnavailable, outage.status, "a verification outage never signs people out")
        assertTrue(outage.bodyAsText().contains("AUTH_UNAVAILABLE")); assertEquals("30", outage.headers[HttpHeaders.RetryAfter])
        val anonymous = client.get("/api/v1/me/thing") { bearerAuth("anon-x") }
        assertEquals(HttpStatusCode.Forbidden, anonymous.status, "anonymous Firebase accounts never unlock signed-in features")
        assertTrue(anonymous.bodyAsText().contains("ACCOUNT_REQUIRED"))
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/thing") { header(HttpHeaders.Authorization, "Basic YWxpY2U6eA==") }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/thing") { headers.append(HttpHeaders.Authorization, "Bearer ok-alice"); headers.append(HttpHeaders.Authorization, "Bearer ok-bob") }.status,
            "two Authorization headers are ambiguous → refused")
        val before = auth.calls.get()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/thing") { bearerAuth("ok-" + "x".repeat(5_000)) }.status)
        assertEquals(before, auth.calls.get(), "oversized tokens never reach the verifier")
    }

    @Test fun eachRequestIsVerifiedOnceAcrossAdmissionRateLimitsGateAndRoute() = testApplication {
        val auth = ScriptedAuth()
        application { securedForTest(auth) { get("/api/v1/me/thing") { call.respondText(call.requireUser(auth)!!) } } }
        assertEquals("alice", client.get("/api/v1/me/thing") { bearerAuth("ok-alice") }.bodyAsText())
        assertEquals(1, auth.calls.get())
    }

    @Test fun forgedUidsInHeadersQueryAndBodyAreIgnored() = testApplication {
        val auth = ScriptedAuth()
        val store = InMemoryUserDataStore()
        val portfolios = PortfolioService(store) { 1_760_000_000_000 }
        application { securedForTest(auth) {
            get("/api/v1/me/portfolio") { user(auth) { uid -> call.respond(portfolios.get(uid)) } }
            post("/api/v1/me/echo") { user(auth) { uid -> call.receive<String>(); call.respondText(uid) } }
        } }
        kotlinx.coroutines.runBlocking { portfolios.saveAccount("alice", org.example.stocksteps.portfolio.PortfolioAccount(id = "acc-a", name = "Alice TFSA")) }
        val asBob = client.get("/api/v1/me/portfolio?uid=alice&userId=alice") {
            bearerAuth("ok-bob"); header("X-User-Id", "alice"); header("X-Firebase-Uid", "alice"); header("X-Forwarded-User", "alice")
        }
        assertEquals(HttpStatusCode.OK, asBob.status)
        assertTrue(json.decodeFromString(PortfolioLedger.serializer(), asBob.bodyAsText()).accounts.isEmpty(), "Bob never sees Alice's ledger")
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/portfolio") { header("X-User-Id", "alice") }.status, "a uid header is not a sign-in")
        assertEquals("bob", client.post("/api/v1/me/echo") { bearerAuth("ok-bob"); contentType(ContentType.Text.Plain); setBody("""{"uid":"alice"}""") }.bodyAsText())
    }
}
