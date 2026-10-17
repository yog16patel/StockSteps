package org.example.stocksteps.security

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.userdata.UserAuthenticator

/**
 * Outcome of checking a request's bearer token (Phase 9A). Only [Valid] carries a uid, and only from a verified token: never from a body,
 * path, query or other header.
 */
sealed interface TokenVerification {
    /** A verified Firebase ID token. [signInProvider] is the token's `firebase.sign_in_provider` claim when the authenticator reads it. */
    data class Valid(val uid: String, val signInProvider: String? = null) : TokenVerification {
        /** Anonymous Firebase accounts aren't StockSteps accounts: the apps never create them, and they never unlock signed-in features. */
        val anonymous: Boolean get() = signInProvider == "anonymous"
    }

    /** Why a token was refused. [reason] is a fixed, log-safe word. [UNAVAILABLE] means verification couldn't run (certificate download). */
    enum class Failure(val reason: String) : TokenVerification {
        MISSING("missing"), MALFORMED("malformed"), EXPIRED("expired"), WRONG_PROJECT("wrong-project"), BAD_SIGNATURE("bad-signature"),
        INVALID("invalid"), UNAVAILABLE("unavailable")
    }
}

/** The signed-in caller of a request, set once by [installUserAuthentication] (or [ApplicationCall.authenticatedUser]'s fallback). */
data class AuthenticatedUser(val uid: String, val signInProvider: String? = null) {
    companion object { val KEY = AttributeKey<AuthenticatedUser>("StockSteps.AuthenticatedUser") }
}

/** The principal of this call, or null for guests and for calls not yet authenticated. */
fun ApplicationCall.principal(): AuthenticatedUser? = attributes.getOrNull(AuthenticatedUser.KEY)

private class Verified(val authenticator: UserAuthenticator, val result: TokenVerification)
private val VERIFIED = AttributeKey<Verified>("StockSteps.TokenVerification")

/** Largest bearer token considered at all (Firebase ID tokens are ≈ 1–2 KB). Longer values are malformed, never sent to the verifier. */
const val MAX_BEARER_TOKEN = 4_096

/**
 * The bearer token check for this call, run at most once per call and [authenticator] (identity resolution, the central gate and route
 * helpers share it). A missing `Authorization` header, another scheme, an empty or oversized value are refused before verification.
 */
suspend fun ApplicationCall.tokenVerification(authenticator: UserAuthenticator): TokenVerification {
    attributes.getOrNull(VERIFIED)?.takeIf { it.authenticator === authenticator }?.let { return it.result }
    val headers = request.headers.getAll(HttpHeaders.Authorization).orEmpty()
    val result = when {
        headers.isEmpty() -> TokenVerification.Failure.MISSING
        headers.size > 1 -> TokenVerification.Failure.MALFORMED
        else -> {
            val header = headers.single()
            val token = header.takeIf { it.length > 7 && it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()
            if (token.isNullOrEmpty() || token.length > MAX_BEARER_TOKEN || token.any { it.isWhitespace() || it.isISOControl() }) TokenVerification.Failure.MALFORMED
            else try { authenticator.verify(token) } catch (cause: Exception) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                TokenVerification.Failure.INVALID
            }
        }
    }
    attributes.put(VERIFIED, Verified(authenticator, result))
    return result
}

/**
 * The verified, non-anonymous caller, or null after responding with the failure (401 / 403 / 503). Route helpers call this; when the
 * central gate already ran it only reads the principal.
 */
suspend fun ApplicationCall.authenticatedUser(authenticator: UserAuthenticator): AuthenticatedUser? {
    principal()?.let { return it }
    return when (val verification = tokenVerification(authenticator)) {
        is TokenVerification.Valid -> if (verification.anonymous) { respondAuthFailure(null, anonymous = true); null }
            else AuthenticatedUser(verification.uid, verification.signInProvider).also { attributes.put(AuthenticatedUser.KEY, it) }
        is TokenVerification.Failure -> { respondAuthFailure(verification); null }
    }
}

/**
 * Failure policy (Phase 9A). Every 401 keeps the code `SIGN_IN_REQUIRED` that both apps already handle (one refresh-and-retry, then
 * "sign in again"); the reason only goes to the logs and to `WWW-Authenticate` (RFC 6750). An anonymous Firebase token is 403
 * `ACCOUNT_REQUIRED`; a verification outage is 503 `AUTH_UNAVAILABLE` (never 401, which would sign people out during an outage).
 * Bodies never name the token, the project, the certificates or the exception.
 */
suspend fun ApplicationCall.respondAuthFailure(failure: TokenVerification.Failure?, anonymous: Boolean = false) {
    markSecurityLogged()
    when {
        anonymous -> {
            SecurityLog.event(this, SecurityEvent.AUTH_REJECTED, "anonymous")
            respond(HttpStatusCode.Forbidden, ApiError("ACCOUNT_REQUIRED", "Sign in with a StockSteps account to use this feature."))
        }
        failure == TokenVerification.Failure.UNAVAILABLE -> {
            SecurityLog.event(this, SecurityEvent.AUTH_UNAVAILABLE, failure.reason)
            response.header(HttpHeaders.RetryAfter, "30")
            respond(HttpStatusCode.ServiceUnavailable, ApiError("AUTH_UNAVAILABLE", "We couldn't check your sign-in right now. Try again shortly."))
        }
        else -> {
            val reason = failure ?: TokenVerification.Failure.INVALID
            SecurityLog.event(this, SecurityEvent.AUTH_REJECTED, reason.reason)
            response.header(HttpHeaders.WWWAuthenticate,
                if (reason == TokenVerification.Failure.MISSING) "Bearer realm=\"stocksteps\"" else "Bearer realm=\"stocksteps\", error=\"invalid_token\"")
            respond(HttpStatusCode.Unauthorized, ApiError("SIGN_IN_REQUIRED",
                if (reason == TokenVerification.Failure.MISSING) "Sign in to use this feature." else "Your session has ended. Sign in again."))
        }
    }
}

/** Paths that always need a signed-in StockSteps account: everything under `/api/v1/me` (classified as AUTHENTICATED or USER_OWNED). */
fun requiresAccount(path: String): Boolean = path == "/api/v1/me" || path.startsWith("/api/v1/me/")

/**
 * Central authentication (Phase 9A): every `/api/v1/me…` request is verified here before routing, fail closed — a route registered under
 * that prefix without its own check is still protected. The principal is stored on the call; route helpers (`user(auth)`, `requireUser`)
 * read it instead of verifying again. Install after admission (which rate-limits first and shares the same per-call verification).
 */
fun Application.installUserAuthentication(authenticator: UserAuthenticator) {
    intercept(ApplicationCallPipeline.Plugins) {
        if (!requiresAccount(call.request.path())) return@intercept
        if (call.authenticatedUser(authenticator) == null) finish()
    }
}
