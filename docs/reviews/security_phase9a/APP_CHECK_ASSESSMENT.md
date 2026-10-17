# App Check assessment — Phase 9A

**Decision: no enforcement and no app integration in 9A.** App Check is defense in depth (it attests the app, not the user); it is not
authentication. Enforcing it now would break every current build. Owner approval is needed for the console and SDK steps below.

## 1. Current state
- **Server:** `security/AppCheck.kt` (Phase 4A). `FirebaseAppCheckVerifier` checks RS256 tokens from `X-Firebase-AppCheck` against the App
  Check JWKS, issuer `https://firebaseappcheck.googleapis.com/<project number>`, audience `projects/<number>`, optional allowed app ids
  (`APP_CHECK_APP_IDS`). `AppCheckGuard` runs **monitor mode by default** (counts `appcheck.valid|missing|invalid|unconfigured`, never logs the
  token); `APP_CHECK_ENFORCE=true` requires `FIREBASE_PROJECT_NUMBER` (startup fails otherwise). Applied to admission route groups only.
  Never verified against real tokens (no project number / registration yet).
- **Android:** no App Check SDK (`firebase-appcheck-playintegrity` not in the build); no header sent.
- **iOS:** no App Check SDK (App Attest / DeviceCheck not configured); no header sent.
- Debug builds, emulators and simulators: no debug provider configured.

## 2. What enforcement would need (owner steps, not done)
1. Firebase console: register the Android app with **Play Integrity** (needs the Play Console app and SHA-256 signing cert) and the iOS app
   with **App Attest** (DeviceCheck as fallback for older devices); set token TTL (default 1 h).
2. Apps: add the App Check SDK, install the provider at startup, attach `X-Firebase-AppCheck` to every StockSteps request (one interceptor
   in the shared Ktor client, next to the existing headers), refresh on expiry, never log it (the debug API log already redacts it:
   `ApiLoggingTest`).
3. Debug/dev: debug provider on debug builds only, with debug secrets registered in a **separate dev project or kept out of
   `APP_CHECK_APP_IDS` in production**; emulators/simulators always use the debug provider. MOCK servers keep App Check off.
4. Server: set `FIREBASE_PROJECT_NUMBER` + `APP_CHECK_APP_IDS`, watch `appcheck.*` counters in monitor mode until valid tokens dominate,
   then `APP_CHECK_ENFORCE=true`.
5. Guest mode: App Check works without sign-in, so guests keep public features; enforcement would cover guests too (that's its point).
6. Failure behaviour when enforced: 401 `APP_CHECK_REQUIRED` ("Please update the app"); a provider outage on the device would block the
   app — keep monitor mode until the apps handle it (retry with backoff, no infinite loop).

## 3. Risk without it
Scripts can call public routes like any app; the per-address/per-uid limits, the shared pool, admission units and provider budgets are the
controls today (`RATE_LIMIT_REVIEW.md`). Account creation for rotating uids is bounded by Firebase's sign-up quotas.
