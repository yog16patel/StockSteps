# Android and iOS integration — Phase 9A

No networking rewrite. Both apps send every StockSteps request through the shared KMP Ktor clients in `core`: public routes through
`network/StockStepsApi`, signed-in routes through `data/userdata/UserApi` (iOS SwiftUI uses them via the `Ios*Client` bridges; no Swift
`URLSession` calls to the backend exist). The token comes from each platform's Firebase SDK adapter.

## 1. Interceptor behaviour (verified per platform path)
| Requirement | Android (`AndroidAuthGateway.idToken` → `FirebaseUser.getIdToken`) | iOS (`FirebaseAccountAdapters.idToken` → `getIDTokenForcingRefresh`) | Evidence |
|---|---|---|---|
| Fresh ID token | SDK returns a cached token and refreshes it before expiry; `forceRefresh` after a 401 | same | code review; `UserApi.request` asks per request |
| Exactly one `Authorization` header | `UserApi.request` sets one header; public `StockStepsApi` sends none | same client | `StockStepsApiTest.signedInRequestsRetryA401Once…` (one header per attempt); emulator log shows `Authorization=[REDACTED]` once on `/me` only |
| 401 handling | one retry with `forceRefresh = true`, then `StockStepsApiException(401, SIGN_IN_REQUIRED)` → SESSION banner "Session expired" + Sign in again | same | same test: exactly 2 attempts, never a loop |
| 403 | no refresh, no retry; code shown by the feature (e.g. `PLUS_REQUIRED`, new `ACCOUNT_REQUIRED`) → SERVER kind | same | same test (`tokenCalls = 1`) |
| 429 / 503 | **no automatic retry**; `retryAfterSeconds` now exposed (0–3600; HTTP-date or malformed → null) | same | `StockStepsApiTest.rateLimitsAreNotRetriedAutomaticallyAndExposeRetryAfter` |
| 503 `AUTH_UNAVAILABLE` | SERVER kind (not SESSION): the user is not signed out by a verification outage | same | `userDataErrorKind` maps only 401 to SESSION |
| Guest public access | public client sends no token | same | emulator: guest-reachable screens loaded, 0 errors |
| No token logging | debug-only `StockSteps-API` log redacts `Authorization`, cookies, App Check | iOS NSLog `[StockSteps-API]` same redaction (shared code) | `ApiLoggingTest`; emulator Logcat shows `Authorization=[REDACTED]` |
| MOCK preserved | MOCK base URL and unsigned MOCK tokens unchanged | unchanged | emulator run against local MOCK :8091 |

## 2. Change in core (both platforms)
- `StockStepsApiException` gains an optional `retryAfterSeconds` (default null; Kotlin callers unchanged; no Swift code constructs it).
- `HttpResponse.retryAfterSeconds()` parses delta-seconds only.
- Nothing else in the clients changed; no screen uses the value yet (the existing error banners already say "try again later").
  Showing a countdown is a UI follow-up, not required for correctness.

## 3. Device verification
- **Android (emulator-5554, MOCK on a local 9A server, debug build with `-PstockstepsMockBackendUrl=http://127.0.0.1:8091`, cached signed-in
  session, no credentials typed):** cold start, every tab, 25 rapid tab switches, search, Company Details — all 200, **0** 429s, `/me` responses
  carry `Cache-Control: no-store, private`; request counts in `RATE_LIMIT_REVIEW.md` §2. PASS.
- **iOS:** shared client tests run in `:core:iosSimulatorArm64Test` and `:app:shared:iosSimulatorArm64Test`; the app builds (`xcodebuild`).
  Simulator UI against the 9A server: **NOT RUN** (the simulator app is in guest mode and points at another server; the client logic is the
  same KMP code exercised on Android). Signed-in iOS: BLOCKED (needs the owner's sign-in).
- Physical devices: NOT RUN.
- After the measurement the emulator app was reinstalled from the standard `assembleDebug` build (LAN MOCK host) and the `adb reverse` for
  :8091 removed.
