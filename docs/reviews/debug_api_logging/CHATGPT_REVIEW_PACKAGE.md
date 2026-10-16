# Review package — DEBUG-only API request/response logging + My Portfolio / Practice REAL diagnosis

## 1. Phase and Git baseline
- Task: secure DEBUG-only HTTP logging on Android and iOS, then use it to explain why My Portfolio and Practice work on MOCK but fail on REAL
  (diagnosis only, no backend fix).
- Base: `44d0247` "Stabilize navigation for Phase 5D.5.6D with once-per-screen loading on Android, …". Under review: "Add DEBUG-only API request and response logging on Android and iOS with default-deny redaction and record why My Portfolio and Practice fail on the LAN REAL backend" (pushed; nothing deployed).

## 2. Changes implemented
- Android debug builds now log every request the app sends to the StockSteps backend under the Logcat tag `StockSteps-API`. iOS Debug builds
  print the same lines with NSLog, prefixed `[StockSteps-API]`.
- Line types: `CONFIG`, `ENV`, `REQ`, `RES`, `ERR`, `CANCEL`. Each request has a correlation id. Lines carry:
  - the explicit MOCK/REAL label;
  - the sanitized URL, host, port, path and query;
  - default-deny headers;
  - safe bodies;
  - status, duration in ms and size;
  - classified errors.
- Signed-in payloads (portfolio, holdings, practice, account) are logged as shape metadata only.
- The earlier `BackendDiagnostics` OkHttp interceptor (`StockStepsBackend`, Android only) is removed, so nothing is logged twice.
- No UI, navigation, endpoint, auth, Firebase, serialization, timeout, retry, cancellation or error-mapping change. Release builds can't
  enable it.

## 3. Affected files
- **core** (new):
  - `network/ApiLogging.kt`: plugin, writer, settings, ids, error classification;
  - `network/ApiLogRedaction.kt`;
  - tests `commonTest/network/ApiLoggingTest.kt` (13) and `ApiLogRedactionTest.kt` (10), `jvmTest/network/ApiLogErrorsJvmTest.kt` (2).
- **shared Android:**
  - `AndroidApiLogging.kt` (new: Logcat sink, debuggable gate);
  - `BackendClient.kt` (installs the plugin instead of the interceptor);
  - `BackendDiagnostics.kt` (deleted);
  - `androidHostTest/AndroidApiLoggingTest.kt` (3).
- **shared iOS:**
  - `IosApiLogging.kt` (new: NSLog sink, debug-binary gate, `NSURLError` kinds);
  - `BackendClient.kt` (installs the plugin);
  - `iosTest/IosApiLoggingTest.kt` (4).
- **Android app:** `MainActivity.kt` (enable plus the start line, inside `BuildConfig.DEBUG`).
- **iOS app:** `iOSApp.swift` (`init` under `#if DEBUG`).
- **docs:**
  - `docs/DEBUG_API_LOGGING.md` (new);
  - `docs/LOCAL_DEVELOPMENT_SERVER.md` and `docs/LOCAL_REAL_BACKEND_PLAN.md` (tag rename notes);
  - `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`;
  - this package.

## 4. Architectural decisions
- **One shared Ktor plugin** (`createClientPlugin`), installed only in the apps' `createBackendClient()`, so every `StockStepsApi`, `UserApi`
  and `Ios*Client` request is covered by one client. There is no second client and nothing on the server. The Ktor `Logging` plugin was not
  used: it can't redact JSON bodies, apply a per-route metadata policy or emit one correlated line per event.
- **Send hook:**
  - observe, `proceed`, rethrow unchanged;
  - response bodies come from the copy Ktor's `SaveBody` already keeps for non-streaming calls (`isSaved`, `rawContent`), so nothing is
    consumed, buffered twice or read from a stream;
  - deserialization failures come from a `responsePipeline` interceptor at `Receive`, before ContentNegotiation.
- **Formatting off the request path:** a bounded, ordered background writer (`Dispatchers.Default`), so nothing blocks the UI thread.
- **Gating:** two independent conditions per platform:
  - Android: `BuildConfig.DEBUG` and `FLAG_DEBUGGABLE`;
  - iOS: `#if DEBUG` and `Platform.isDebugBinary`;
  - the environment choice only labels.
- **Redaction is default-deny:** a header allow-list; sensitive keys and value shapes; `/me/` path ids hidden; signed-in success bodies
  shape only.

## 5. Test and build results
See `CHATGPT_TEST_RESULTS.md`. All four suites PASS (core JVM 606, core iOS 604, shared Android host 276, shared iOS 273, 0 failures);
`assembleDebug` PASS; `xcodebuild` (Debug, simulator) PASS.

## 6. Android verification (emulator-5554, signed in)
PASS on MOCK and REAL:
- Home, Markets and Company Details (including a 404);
- My Portfolio and Practice;
- both environment switches;
- connection refused;
- distinct ids (51/51);
- `-s StockSteps-API` filtering and no duplicate tag;
- the redaction scan (0 hits).

The emulator can't route to the LAN host, so the check used a localhost forwarder and `adb reverse`, both removed afterwards
(`docs/DEBUG_API_LOGGING.md` §8).

## 7. iOS verification (iPhone 16 simulator, guest)
- The first run **crashed in the NSLog sink**: a Kotlin `String` passed through C varargs. It is fixed and covered by a new test.
- After the fix, PASS:
  - same format, CONFIG/ENV for MOCK and REAL;
  - Home, Markets and Company Details including a 404;
  - guest Portfolio (no `/me/` request);
  - REAL meta, Daily Brief and a 503.
- Signed-in My Portfolio and Practice on iOS: BLOCKED (guest).

## 8. Known issues (found, not fixed — out of scope)
- REAL `/me/*` → 503 `USER_DATA_UNAVAILABLE` (the root cause below).
- REAL personalized Daily Brief → 500 instead of 503.
- The 503 copy says "Watchlists and alerts" on every user-data route.
- REAL `screener/catalog` → 503 `SCREENER_UNAVAILABLE` (not investigated).
- The emulator can't reach the LAN host (no route).

## 9. Unverified scenarios
- Signed-in iOS: BLOCKED (no approved test account).
- Device cancellation: NOT RUN (unit-tested).
- Release device builds: NOT RUN (gating unit-tested).
- iOS Settings toggle: NOT RUN (`defaults write` used).
- TLS and DNS on a device: NOT RUN (JVM tests).

## 10. Deviations
- The task asked for one Android tag. The `StockStepsBackend` interceptor was **removed**, not kept alongside, and its start-up line moved
  into `CONFIG`.
- The iOS sink chunks at 900 characters and Android at 3,500 (platform line limits). The content format is identical.

## 11. Decisions requiring owner approval
1. **REAL user data (D-R1):** mount a read-only dev Firebase service-account JSON in the REAL container so `/me/` routes work (today they 503
   by design). This needs approval and a redeploy.
2. Fix the personalized-brief 500 → 503 mapping and the generic 503 copy (separate backend task).

## 12. Links
`docs/DEBUG_API_LOGGING.md` (architecture §3, format §5, security §6, verification §8, **diagnosis §9**); `CHATGPT_FOCUSED_DIFF.md`;
`CHATGPT_TEST_RESULTS.md`; logs in `captures/` (git-ignored). No contact sheets: no UI change.
