# Authentication review — Phase 9A

## 1. Before 9A (baseline at `1c71207`)
- Each signed-in route called `user(auth)` → `requireUser` → `UserAuthenticator.uid(token)`. Correct, but per route: a new `/api/v1/me` route
  that forgot the helper would have been public.
- REAL: `FirebaseIdTokenAuthenticator` (google-auth `TokenVerifier`, securetoken certificates, `iss`/`aud` = project, `exp`). Every failure,
  including **a certificate download outage**, was `null` → 401 → both apps show "Session expired" and sign the user out.
- google-auth's verifier accepts a token **without `exp`**, and does not check `iat`, `auth_time` or the shape of `sub`.
- Admission (`ClientIdentityResolver`) verified the same token again: two verifications per signed-in request.
- MOCK: `MockUserAuthenticator` reads the uid from an unsigned token (MOCK only; `DataMode` refuses MOCK on Cloud Run).
- No Admin SDK and no service-account key are needed for verification (public certificates). Firestore uses ADC.

## 2. What 9A changed
| Concern | Implementation |
|---|---|
| Central gate | `security/Authentication.kt` `installUserAuthentication`: every `/api/v1/me` and `/api/v1/me/…` request is verified **before routing**, fail closed. Installed in `stockStepsModule` after admission and rate limits. |
| Typed principal | `AuthenticatedUser(uid, signInProvider)` in call attributes; `call.principal()`. `requireUser` / `user(auth)` now read it (no per-route verification copies; route tests without the gate still verify). |
| One verification per request | `ApplicationCall.tokenVerification(auth)` memoizes the result per call; admission identity, rate limits, gate and route share it (test: `eachRequestIsVerifiedOnce…` = 1 call). |
| Header handling | Exactly one `Authorization: Bearer <token>`; another scheme, two headers, empty, whitespace/control characters or > 4,096 characters → MALFORMED without calling the verifier. |
| REAL checks added | RS256 only; `exp` required; `iat` and `auth_time` not in the future (60 s skew); `sub` 1–128 chars without `/`, whitespace or control characters (it becomes a Firestore document id); `firebase.sign_in_provider` read. Existing: signature (securetoken keys), `iss` = `https://securetoken.google.com/<project>`, `aud` = project, `exp`. |
| Failure reasons | `TokenVerification.Failure`: MISSING, MALFORMED, EXPIRED, WRONG_PROJECT, BAD_SIGNATURE, INVALID, UNAVAILABLE (log-safe words; mapped from google-auth messages, pinned by `FirebaseTokenVerifierTest.libraryMessagesMapToStableReasons`). |
| Outage ≠ sign-out | Certificate fetch failure → **503 `AUTH_UNAVAILABLE`** + `Retry-After: 30` (mobile: SERVER kind, not SESSION). |
| Anonymous accounts | Neither app creates anonymous Firebase users. A token with `sign_in_provider = anonymous` → **403 `ACCOUNT_REQUIRED`** on signed-in routes, and is **not** a per-account limiter identity (freshly minted anonymous uids would otherwise each get their own rate budget). |
| Never trusted | uid in body, path, query, `X-User-Id`, `X-Firebase-Uid`, `X-Forwarded-User`, unverified JWTs (tests: `forgedUidsInHeadersQueryAndBodyAreIgnored`, `everySignedInRouteRefusesMissingAndForgedTokens`). |
| Project per environment | Unchanged: `FIREBASE_PROJECT_ID` (`AppConfig.firebaseProjectId`); on Cloud Run it must be explicit (`StartupConfiguration`). Tokens of another project → WRONG_PROJECT → 401. |

## 3. Failure policy (stable, compatible)
| Situation | Status | Code | Notes |
|---|---|---|---|
| No token | 401 | `SIGN_IN_REQUIRED` | `WWW-Authenticate: Bearer realm="stocksteps"` |
| Malformed / expired / bad signature / wrong project / invalid | 401 | `SIGN_IN_REQUIRED` | `WWW-Authenticate: … error="invalid_token"`; message "Your session has ended. Sign in again." |
| Anonymous Firebase account | 403 | `ACCOUNT_REQUIRED` | |
| Plan / admin / internal job denied | 403 | existing (`PLUS_REQUIRED`, `FORBIDDEN`…) | unchanged |
| Rate limit | 429 | `RATE_LIMITED` | `Retry-After` |
| Token verification unavailable | 503 | `AUTH_UNAVAILABLE` | `Retry-After: 30` |
| User-data store down | 503 | `USER_DATA_UNAVAILABLE` | unchanged (Phase 7D) |

The 401 code is unchanged on purpose: both apps key on `SIGN_IN_REQUIRED` (`EarningsPremiumPresentation`, `ComparisonAiPresentation`,
`ScreenerPresentation`) and `UserApi` retries any 401 once with a refreshed token. Bodies never contain the token, project, certificate
URL, exception class or stack (asserted per reason in `AuthenticationGateTest.failuresMapToStableCodesWithoutInternalDetails`).

## 4. Tests (all PASS — see `CHATGPT_TEST_RESULTS.md`)
- `FirebaseTokenVerifierTest` (REAL verifier, test RSA key): valid token → uid + provider; other key, tampered payload, wrong `aud`, wrong `iss`,
  expired, no `exp`, no `iat`, future `iat`, future `auth_time`, `sub` with `/`, empty, 129 chars, `alg: none`, malformed, oversized;
  certificate outage → UNAVAILABLE; library wording pinned.
- `AuthenticationGateTest`: gate protects a route registered without a check; public routes ignore an expired token (guest); every failure
  reason → status/code/header without leaks; anonymous → 403; Basic → 401; two headers → 401; oversized never reaches the verifier;
  one verification per request; forged uids in headers, query and body ignored.
- `RouteAccessTest.everySignedInRouteRefusesMissingAndForgedTokens`: all 88 signed-in routes of the real MOCK server × 5 token variants → 401.
- `FirebaseEmulatorTwoUserIsolationTest.realVerifierRefusesEmulatorTokens`: an Auth-emulator token never passes the REAL verifier.

## 5. Limitations
- **Revocation:** like every stateless JWT check, a disabled or deleted account's ID token stays valid until it expires (≤ 1 h). The Admin SDK's
  `verifyIdToken(checkRevoked = true)` costs an Auth backend call per request; recommended only for sensitive operations if the owner wants it
  (owner decision). The emulator test shows a deleted account's token failing only because its test verifier asks the emulator.
- Account creation is open (Firebase email sign-up): rotating free accounts gets fresh per-account rate budgets; Firebase's own sign-up quota
  per IP and the global provider budgets bound it. App Check (when enforced) is the planned mitigation.
- The MOCK authenticator accepts unsigned tokens by design (local only; `DataMode` refuses MOCK on Cloud Run).
