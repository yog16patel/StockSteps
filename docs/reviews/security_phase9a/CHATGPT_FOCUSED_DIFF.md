# Focused diff — Phase 9A (security; committed on top of `1a39ce3`)

Short excerpts of the decisions that matter, with their risk and tests. Full files are in the working tree.

## 1. Central gate and typed principal — `server/…/security/Authentication.kt` (new)
```kotlin
fun Application.installUserAuthentication(authenticator: UserAuthenticator) {
    intercept(ApplicationCallPipeline.Plugins) {
        if (!requiresAccount(call.request.path())) return@intercept          // "/api/v1/me" or "/api/v1/me/…"
        if (call.authenticatedUser(authenticator) == null) finish()         // responded 401 / 403 / 503
    }
}
suspend fun ApplicationCall.tokenVerification(authenticator: UserAuthenticator): TokenVerification {
    attributes.getOrNull(VERIFIED)?.takeIf { it.authenticator === authenticator }?.let { return it.result }   // once per call
    val headers = request.headers.getAll(HttpHeaders.Authorization).orEmpty()
    // missing → MISSING; 2 headers, non-Bearer, empty, whitespace/control chars, > 4,096 → MALFORMED (verifier never called)
    …
}
```
Decision: prefix-based, fail closed — a future `/me` route without `user(auth)` is still protected; the route helper reads the principal.
Risk: a signed-in route outside `/api/v1/me` wouldn't be gated — `RouteAccessTest.accessClassesFollowThePathPrefixes` fails if any rule
outside the prefix is AUTHENTICATED/USER_OWNED. Tests: `AuthenticationGateTest`, `RouteAccessTest` (88 routes × 5 token variants).

## 2. Failure policy — same file
```kotlin
anonymous -> 403 ApiError("ACCOUNT_REQUIRED", …)
failure == UNAVAILABLE -> Retry-After: 30; 503 ApiError("AUTH_UNAVAILABLE", …)
else -> WWW-Authenticate: Bearer realm="stocksteps"[, error="invalid_token"]; 401 ApiError("SIGN_IN_REQUIRED", …)
```
Decision: keep `SIGN_IN_REQUIRED` for every 401 (both apps key on it); certificate outages are 503 so users aren't signed out.

## 3. REAL verifier — `server/…/userdata/UserAuthentication.kt`
```kotlin
when {
    token.header.algorithm != "RS256" -> BAD_SIGNATURE
    payload.expirationTimeSeconds == null -> INVALID                       // google-auth alone accepts a token without exp
    payload.issuedAtTimeSeconds.let { it == null || it > now + 60 } -> INVALID
    authTime != null && authTime > now + 60 -> INVALID
    uid.isNullOrEmpty() || uid.length > 128 || uid.any { it == '/' || it.isWhitespace() || it.isISOControl() } -> INVALID
    else -> Valid(uid, firebase.sign_in_provider)
}
} catch (cause: TokenVerifier.VerificationException) { classify(cause.message) }   // "Error fetching PublicKey…" → UNAVAILABLE
```
Risk: classification depends on google-auth 1.50.0 wording — pinned by `libraryMessagesMapToStableReasons`; unknown wording → INVALID (401,
safe default). Tests: `FirebaseTokenVerifierTest` with a real RSA key (`publicKey`/`clock`/`transport` constructor is `internal`).

## 4. Identity — `server/…/security/ClientIdentity.kt`
```kotlin
val uid = auth?.let { (call.tokenVerification(it) as? TokenVerification.Valid)?.takeUnless { v -> v.anonymous }?.uid }
```
Decision: anonymous Firebase uids are free to mint, so they never get a per-account budget. Test: `unverifiedGuests…AnonymousAccounts…`.

## 5. Class limits — `server/…/security/RateLimits.kt`, `RouteAccess.kt` (new)
```kotlin
fun enter(rateClass, bucket, identity): Decision = synchronized(lock) {
    // per name in [class, bucket?]: User → userPerMinute, Address → guestPerMinute, Unverified → shared pool
    // deny on in-flight cap (identities only) or on window size; charge all buckets only when all admit
}
// plugin: 429 RATE_LIMITED + Retry-After = ceil(oldest + 60 s − now); in-flight → 1; never queued; exit() in finally
```
Decisions: every route has an explicit rule (table = enforcement = matrix); unknown paths fall back by prefix; bounded state (window
expiry, sweep every 512 decisions, LRU at 20,000). Risk: per-instance only (`RATE_LIMIT_REVIEW.md` §6). Tests: `RateLimitAbuseTest` (14),
real sockets.

## 6. Install order — `server/…/Application.kt`
```kotlin
installSecurityObservability()        // nosniff, Cache-Control: no-store, private on /me and /internal; 403/4xx logs
installAdmission(identities, …)       // Phase 4A unchanged
installRateLimits(identities, RateLimiter(RateLimitPolicy.fromEnvironment()))
installUserAuthentication(sources.userAuth)
```
`module()` now delegates to `internal fun Application.stockStepsModule(dataMode)` so tests boot the real MOCK server. Netty:
`requestReadTimeoutSeconds = 30`, `responseWriteTimeoutSeconds = 60`.

## 7. Error mapping — `server/…/ApiErrorHandling.kt`
`UnsupportedMediaTypeException` / `CannotTransformContentToTypeException` → 415 `UNSUPPORTED_MEDIA_TYPE` (was 500 for `text/plain` on JSON
routes — found by `RequestValidationTest`); `PayloadTooLargeException` → 413; `BadRequestException` → 400 `INVALID_REQUEST`; provider
restrictions log `upstream_restricted` (code only).

Also: exception-mapped input errors (400/413/415 thrown by `receive`) are logged as `invalid_input` in the hook's `catch` before rethrowing.

## 8. Security log — `server/…/security/SecurityLog.kt` (new)
`security event=… reason=… class=… method=… route=<template> identity=<kind>:<12-hex salted SHA-256> [trace=…]`; ≤ 1 line per minute per
(event, reason, identity), every event counted. Never: tokens, headers, query, body, raw path, uid, IP. Tests: log assertions in
`RateLimitAbuseTest`; real server logs grep = 0.

## 9. Clients — `core/…/network/StockStepsApi.kt`, `data/userdata/UserApi.kt`
```kotlin
class StockStepsApiException(val status: Int, val error: ApiError, val retryAfterSeconds: Long? = null)
fun HttpResponse.retryAfterSeconds(): Long? = headers[RetryAfter]?.trim()?.toLongOrNull()?.takeIf { it in 0..3_600 }
```
No retry behaviour changed (401 once with refresh; 429/503 never). Tests: `StockStepsApiTest` (2 new).

## 10. Small route change — `server/…/screener/ComparisonResearchService.kt`
The PDF export no longer sets its own `Cache-Control`; the central hook sets `no-store, private` on every signed-in response, errors included.
`ComparisonResearchRoutesTest` installs the hook and still asserts `no-store` on the PDF.
