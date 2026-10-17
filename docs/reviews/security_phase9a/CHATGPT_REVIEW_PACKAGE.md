# ChatGPT review package — Phase 9A: backend security and access control (+ back-to-back request addendum)

## 1. Phase and Git baseline
- Phase 9A, backend security (`docs/reviews/security_phase9a/`). Under review: the commit "Secure the backend for Phase 9A with a central sign-in check on every signed-in route, a classified route table, per-class rate limits with Retry-After and concurrency caps, safe security logging, 415 for non-JSON bodies and two-user isolation tests on the Firebase emulators", on top of `1a39ce3`
  (test-only `PortfolioSessionStateTest` fix). Tests ran on the identical 9A code over `1c71207` (Phase 8D); `1a39ce3` changes one core test only.
- Phase 8E (Android motion) remains uncommitted in the main checkout and is not part of this commit. Not deployed; no production or Firebase console change; no provider change.

## 2. Changes implemented
- **Central authentication** for every `/api/v1/me…` request before routing (fail closed), a typed principal, one token verification per
  request, stricter REAL checks (RS256, `exp` required, `iat`/`auth_time`, uid shape), anonymous Firebase accounts refused (403) and not
  given their own rate budget, token-verification outages → 503 instead of a sign-out.
- **Route table** (`RouteAccess`): all 136 routes classified PUBLIC 41 / AUTHENTICATED 14 / USER_OWNED 74 / ADMIN_INTERNAL 4 /
  HEALTH_INTERNAL 3, each with a rate-limit class; a test proves the table equals the real server's routes.
- **Per-class rate limits + concurrency** (`RateLimits`): PUBLIC_LOW_COST, PUBLIC_MARKET_DATA (+ `search` bucket), AUTHENTICATED_STANDARD,
  USER_WRITE, EXPENSIVE_OPERATION (2 in flight), ADMIN_INTERNAL; per uid, per trusted address, or a shared pool; 429 `RATE_LIMITED` +
  `Retry-After`; bounded state. Limits set from emulator measurements (search 10/min would block one slowly typed word).
- **Validation/headers:** 415 instead of 500 for non-JSON bodies (bug found), 400/413 mapping, `nosniff`, `Cache-Control: no-store, private`
  on signed-in/internal responses, no CORS, Netty read/write timeouts, `RATE_LIMIT_*` startup validation.
- **Security log:** structured, throttled, no tokens/uids/IPs/raw paths; events counted in the usage summary.
- **Clients:** `StockStepsApiException.retryAfterSeconds`; no retry behaviour change.

## 3. Affected files
- server main: `security/Authentication.kt`, `RouteAccess.kt`, `RateLimits.kt`, `SecurityLog.kt`, `SecurityObservability.kt` (new);
  `security/ClientIdentity.kt`, `userdata/UserAuthentication.kt`, `Application.kt`, `ApiErrorHandling.kt`, `appconfig/StartupConfiguration.kt`,
  `screener/ComparisonResearchService.kt` (export header).
- server tests: `security/AuthenticationGateTest.kt`, `RateLimitAbuseTest.kt`, `RequestValidationTest.kt`, `RouteAccessTest.kt`,
  `userdata/FirebaseEmulatorTwoUserIsolationTest.kt` (new); `userdata/FirebaseTokenVerifierTest.kt`, `screener/ComparisonResearchRoutesTest.kt`.
- core: `network/StockStepsApi.kt`, `data/userdata/UserApi.kt`, `network/StockStepsApiTest.kt`.
- docs: this folder (14 files), `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`.
- No Android/iOS app source, Compose/SwiftUI, provider, cache, calculation or fixture change.

## 4. Architectural decisions
- Gate by path prefix (fail closed) instead of wrapping ~90 route registrations in `authenticate {}` blocks; route helpers became principal
  readers. Rate limiting runs before the gate so floods of bad tokens are limited too.
- Keep 401 `SIGN_IN_REQUIRED` for all token failures (mobile contract); reasons go to logs and `WWW-Authenticate`.
- One table (`RouteAccess`) drives classification, limits, logs and the matrix; tests keep it equal to the router.
- Duplicate-request handling stays in the existing single-flight public-data caches (never shared across users); no HTTP-response cache.
- No Redis/gateway added: per-instance limits documented with a migration plan.
- App Check: assessment only; monitor mode unchanged.

## 5. Test and build results
All PASS (`CHATGPT_TEST_RESULTS.md`): core JVM 625/0; core iOS 623/0; server 604/0 (10 emulator-only skipped, all 10 PASS under the Auth +
Firestore emulators); shared Android 289/0; shared iOS 286/0; `assembleDebug` and `xcodebuild` PASS. New security tests: gate 4, abuse 14,
validation 5, route table 4, verifier 7 (4 new), two-user emulator 2, client 2. Real-socket checks and latency (±1 ms) PASS.

## 6. Android verification
emulator-5554, debug build pointed at a local 9A MOCK server, cached signed-in session (no credentials typed): cold start, all tabs, 25 rapid
tab switches (0 requests), search typing (9 requests per slowly typed word), Company Details (7 requests) — all 200, 0 × 429; `/me`
responses carry `no-store, private`. PASS. TalkBack: NOT RUN.

## 7. iOS verification
Shared client code covered by `:core:iosSimulatorArm64Test` / `:app:shared:iosSimulatorArm64Test`; `xcodebuild` build. Simulator UI against
the 9A server: NOT RUN (guest simulator on another server). Signed-in iOS: BLOCKED (owner sign-in). VoiceOver: NOT RUN.

## 8. Known issues
`KNOWN_LIMITATIONS.md` (14 items): per-instance limits, guest pool without `TRUSTED_PROXY_HOPS`, NAT sharing, no revocation check, open
sign-ups, App Check not integrated, development provider budgets, tracked iOS Firebase client config.

## 9. Unverified scenarios
iOS simulator against 9A (NOT RUN); signed-in iOS (BLOCKED); physical devices (NOT RUN); production/staging attack tests (NOT RUN, by rule);
real Firebase tokens against the REAL verifier on a real project (NOT RUN: emulator + test RSA keys only); multi-instance limits (NOT RUN).

## 10. Deviations from the brief
- Addendum suggested search 10/min, guest 30/min, expensive 5/min: measured app patterns set 45 / 90 / 6 (guest) instead
  (`RATE_LIMIT_REVIEW.md` §2). Authenticated 120/min and 2 concurrent expensive kept.
- No HTTP-level GET dedup (the caches already single-flight public data; documented).
- Ktor's `Authentication` plugin isn't used; an equivalent central interceptor keeps the existing route functions intact.

## 11. Decisions requiring owner approval
1. Production limiter architecture for multiple instances (Cloud Armor/API Gateway or a shared store) — `RATE_LIMIT_REVIEW.md` §6.
2. Revocation checks for sensitive operations (Admin SDK, extra latency/cost).
3. App Check rollout (console registration, SDKs, debug providers).
4. Verify and set `TRUSTED_PROXY_HOPS`, provider plan limits and `CLOUD_RUN_MAX_INSTANCES` for the deployed topology.
5. Restrict `/internal/*` at the platform (ingress/IAM) and decide on the tracked iOS Firebase plist.
6. Final rate-limit numbers (env-configurable; defaults proposed here).

## 12. Links
`ENDPOINT_ACCESS_MATRIX.md`, `AUTHENTICATION_REVIEW.md`, `AUTHORIZATION_REVIEW.md`, `GUEST_MODE_REVIEW.md`, `RATE_LIMIT_REVIEW.md`,
`RATE_LIMIT_ABUSE_TEST_RESULTS.md`, `APP_CHECK_ASSESSMENT.md`, `PROVIDER_COST_PROTECTION.md`, `SECURITY_TEST_MATRIX.md`,
`ANDROID_IOS_INTEGRATION.md`, `KNOWN_LIMITATIONS.md`, `CHATGPT_FOCUSED_DIFF.md`, `CHATGPT_TEST_RESULTS.md`; logs in `captures/` (git-ignored).
No contact sheets: no UI change.
