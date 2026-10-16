# StockSteps — DEBUG-only API request/response logging

Status: **implemented and verified; committed and pushed** as "Add DEBUG-only API request and response logging on Android and iOS with default-deny redaction and record why My Portfolio and Practice fail on the LAN REAL backend" (2026-10-16). Base: `44d0247` (Phase 5D.5.6D). Review package: `docs/reviews/debug_api_logging/`. The My Portfolio / Practice REAL diagnosis is §9 (not fixed here).

## 1. What it is

One shared Ktor client plugin (`core/.../network/ApiLogging.kt`, `StockStepsApiLogging`) logs every request the apps send to the StockSteps
backend, in Android and iOS **debug builds only**, as single redacted lines:

- Android: Logcat tag **`StockSteps-API`** (`adb -s emulator-5554 logcat -s StockSteps-API`); failures at WARN, everything else at INFO.
- iOS: the same lines through `NSLog`, prefixed **`[StockSteps-API]`** (Xcode console, or
  `xcrun simctl spawn <udid> log stream --style compact --predicate 'eventMessage CONTAINS "StockSteps-API"'`).

It replaces the earlier per-request `BackendDiagnostics` OkHttp interceptor (tag `StockStepsBackend`, Android only), so nothing is logged twice.

## 2. Networking paths (inspection, 2026-10-16)

| Path | Client | Covered |
|---|---|---|
| Public market data (`StockStepsApi`: Home, Markets, search, Company Details, news, earnings, screener, Learn, meta) | the graph's `HttpClient` from `createBackendClient()` | yes |
| Signed-in data (`UserApi`, `/api/v1/me/…`: watchlists, alerts, notes, **portfolio**, **practice**, entitlements, brief, learning, research, devices); Firebase ID token added per request as `Authorization: Bearer …`, one retry with a refreshed token on 401 | same `HttpClient` (account graph) | yes |
| iOS `Ios*Client` bridges (`IosStockStepsClient`, `IosMarketsClient`, `IosCompanyDetailsClient`, `IosAccountClient`, `IosEarningsClient`, `IosScreenerClient`, `IosLearningClient`, `IosBackendInfoClient`, `IosMarketClient`) | each creates its graph through `createBackendClient()` (Darwin) | yes |
| Environment selection | Android `BackendRouter` (stored choice + "no mock URL ⇒ REAL"), base URL read per request; iOS `BackendSettings.currentURL` / `.environment` (same key, mock only in DEBUG) | label from these, never from the URL |
| Coil image loading (Compose `AsyncImage`: logos, news images) | Coil's own `coil-network-ktor3` client | **no** (binary images; own client) |
| iOS `AsyncImage` | `URLSession.shared` | **no** |
| Firebase Auth (sign-in, ID-token refresh), FCM registration, Google Sign-In | Firebase/Google SDK networking | **no** (SDK-owned; tokens must not be logged anyway) |
| Server-side provider calls (FMP, Finnhub, BoC, Gemini) | server `apiCall` | not app traffic; never installed on the server |

No app code builds another `HttpClient` (`createBackendClient()` is the only factory in `app/`), and no Swift code calls `URLSession` for API data.

## 3. Architecture

- **Plugin** (`createClientPlugin`), installed only in `createBackendClient()` (Android OkHttp, iOS Darwin), after `configureStockStepsClient()`.
  - `Send` hook: assigns the correlation id, snapshots method, URL, headers and the already-serialized body (`ByteArrayContent`/`TextContent`;
    streamed request bodies are `[NOT AVAILABLE]`), calls `proceed`, then logs the response or the failure and **rethrows it unchanged**.
  - The response body is read from the copy Ktor's built-in `SaveBody` already holds for every non-streaming call (`response.isSaved`;
    `rawContent` is a fresh channel over the saved bytes). Nothing is consumed, nothing is buffered twice, and streaming responses are never
    touched (`[NOT AVAILABLE]`). Binary types are not even read (`[BINARY CONTENT OMITTED]`, size from `Content-Length` or a discard count).
  - A `responsePipeline` interceptor at `Receive` (before ContentNegotiation's `Transform`) reports **deserialization failures** when the caller
    reads `body<T>()` (type, target class, missing field / JSON path only), then rethrows.
- **Writer** (`ApiLogWriter`): the request path only enqueues a line builder; formatting and redaction run on `Dispatchers.Default` in
  one ordered consumer (bounded queue of 512, drops oldest). Never blocks the UI thread; a failing formatter loses only its own line.
- **Correlation ids**: `<4-char process prefix>-<counter>` (atomic), e.g. `k3f9-0012`; distinct for concurrent requests. A 401 retry in
  `UserApi` is a new request with a new id.
- **Environment label**: `ApiLogSettings.environment`, supplied by the host from the explicit choice (Android
  `backendRouter.currentEnvironment().name`, iOS `BackendSettings.environment`), read per request. An `ENV` line marks every change.
- **Platform glue**: `app/shared/.../androidMain/AndroidApiLogging.kt` (Logcat sink, 3,500-char chunks), `iosMain/IosApiLogging.kt` (NSLog
  sink, 900-char chunks; `NSURLError` code → kind). `MainActivity` and `iOSApp.init` enable it and print one `CONFIG start …` line.
- Unchanged: auth (token injection and 401 retry), serialization, timeouts, retry, cancellation and error mapping (`StockStepsApiException`).

## 4. Build gating

| Platform | Condition to enable | Release |
|---|---|---|
| Android | `MainActivity`: inside `if (BuildConfig.DEBUG)`, and `AndroidApiLogging.enable` also requires `ApplicationInfo.FLAG_DEBUGGABLE` on the installed app | release `BuildConfig.DEBUG = false`, not debuggable ⇒ `ApiLogging.enable(debugBuild = false)` leaves it off |
| iOS | `iOSApp.init` call compiled only under `#if DEBUG`; `IosApiLogging.enable` also requires `kotlin.native.Platform.isDebugBinary` (the Debug configuration builds a debug framework) | the call isn't compiled; a release framework refuses |

- Choosing REAL (or MOCK) never enables or disables it; the environment only changes the label.
- With no settings the plugin's hook is a single null check and `proceed`.

## 5. Line format

```
CONFIG start stored=<MOCK|REAL> effective=<…> real=<scheme://host:port> mock=<scheme://host:port|->
ENV    id=<id> from=<previous|-> to=<env> host=<host> port=<port>
REQ    id=<id> env=<env> ts=<ISO-8601 UTC> method=<M> url=<sanitized> host=<h> port=<p> path=<sanitized> query=<sanitized|-> contentType=<t|-> headers={…} body=<safe>
RES    id=<id> env=<env> method=<M> path=<p> status=<n> result=<OK|REDIRECT|HTTP_4XX|HTTP_5XX> durationMs=<ms> size=<bytes|[NOT AVAILABLE]> contentType=<t|-> headers={…} body=<safe>
ERR    id=<id> env=<env> method=<M> path=<p> kind=<kind> type=<ExceptionClass> durationMs=<ms> message=<redacted, ≤200 chars>
ERR    id=<id> env=<env> method=<M> path=<p> kind=SERIALIZATION type=<class> target=<model> detail=<missing=…,unknownKey=…,jsonPath=…>
CANCEL id=<id> env=<env> method=<M> path=<p> type=<class> durationMs=<ms>
```

Error kinds: `CONNECTION_REFUSED`, `CONNECT_FAILED`, `DNS`, `TLS`, `TIMEOUT_REQUEST` / `TIMEOUT_CONNECT` / `TIMEOUT_SOCKET` / `TIMEOUT`,
`NO_ROUTE`, `NETWORK`, `SERIALIZATION`, `UNKNOWN`; iOS adds `CONNECTION_LOST`, `OFFLINE`, `ATS_BLOCKED`, `NETWORK_NSURL_<code>`.
Cancellation (`CancellationException` without a timeout cause) is a `CANCEL` line, never an `ERR`. A timeout arrives wrapped in a
`CancellationException`; the classified cause names it (`type=HttpRequestTimeoutException`).

Placeholders: `[REDACTED]`, `[BINARY CONTENT OMITTED]`, `[TRUNCATED]`, `[NOT AVAILABLE]`; `[METADATA]` prefixes a shape-only body.

## 6. Security (default-deny, `ApiLogRedaction`)

- **Headers:** values only for a fixed safe list (Accept*, Content-Type/Length/Encoding/Language, Cache-Control, Date, ETag, Age, Expires,
  Last-Modified, Retry-After, Vary, Transfer-Encoding, Connection, Keep-Alive, Server). Every other header, including `Authorization`,
  `Cookie`, `Set-Cookie`, App Check and API-key headers, is `Name=[REDACTED]`.
- **URLs:** user info and fragment dropped; query values redacted for sensitive names (token, key, secret, password, signature, email,
  phone, uid/user/owner/account/device/session ids, card, iban…) and by value shape. Under `/api/v1/me/` every path segment that isn't a
  lower-case route word (ids) is `[REDACTED]`.
- **JSON (nested, any depth):** sensitive keys replaced whatever their value; string values redacted by shape: JWTs, `Bearer`/`Basic`
  credentials, `mock-user:` tokens, e-mails, phone numbers, card numbers, opaque 32+ character tokens, `key=value` secrets, and URLs inside
  text (sanitized query). Numbers and booleans of public market data stay.
- **Signed-in user data** (`/api/v1/me/…`, which includes My Portfolio, holdings, transactions, Practice, account, entitlements): request
  bodies and 2xx response bodies are logged as **metadata only** (`[METADATA] {accounts:array(1)[{id:string,…}],…}`: key names, types, array
  sizes, two levels; id-like keys hidden). No amount, quantity, price, cash, name or id is printed. Error payloads (`{code,message}`) stay
  readable (redacted), because they are what diagnosis needs.
- **Exceptions:** type name + redacted, bounded message; serialization failures give only field names and JSON path (their messages quote
  the input). Never a stack trace.
- **Limits:** body text truncated at `maxBodyChars` (default 2,048, `ApiLogSettings`); JSON over 256 KB is not parsed or printed
  (`[TRUNCATED] (size=…, not inspected)`); binary, image, PDF, audio, video, font, multipart and octet-stream bodies are never decoded.

## 7. Using it

```bash
adb -s emulator-5554 logcat -s StockSteps-API
```

```bash
xcrun simctl spawn A04A9C8B-D9D1-4910-B0C0-BEC042629EDA log stream --style compact --predicate 'eventMessage CONTAINS "StockSteps-API"'
```

Follow one request by its id: `adb -s emulator-5554 logcat -d -s StockSteps-API | grep 'id=k3f9-0012 '`.

## 8. Verification (2026-10-16)

Status words follow `docs/AI_REVIEW_WORKFLOW.md` §4. Logs: `docs/reviews/debug_api_logging/captures/` (git-ignored; LAN address replaced
by `<lan>`). Test and build results: `docs/reviews/debug_api_logging/CHATGPT_TEST_RESULTS.md`.

**Android (emulator-5554, Pixel_9_Pro, debug build, cached signed-in test session).** The emulator can't route to the LAN host
(`nc: No route to host`; the Mac reaches it). So for this check the debug build used `-PstockstepsMockBackendUrl=http://127.0.0.1:18081
-PstockstepsDebugRealBackendUrl=http://127.0.0.1:18082`, a throwaway localhost TCP forwarder on the Mac (scratchpad, stopped afterwards) to
the LAN MOCK `:8081` and REAL `:8082`, and `adb -s emulator-5554 reverse` for those two ports (removed afterwards).

| Check | Result | Evidence |
|---|---|---|
| `logcat -s StockSteps-API` shows only this tag's lines; no `StockStepsBackend` lines (no duplicates) | PASS | `android-*.log`; `logcat -s StockStepsBackend` empty |
| CONFIG start line (stored/effective, host:port only) | PASS | `android-1-home-mock.log` |
| Home (MOCK): meta, Daily Brief, signed-in entitlements/watchlists/portfolio/alerts/screens/learning, personalized brief | PASS | `android-1-home-mock.log` |
| Markets (MOCK): overview, earnings calendar, following | PASS | `android-2-markets-mock.log` |
| Company Details (MOCK): details, chart, why-moving, news, earnings next; `earnings/company/SXTC/latest` → 404 `NO_REPORT` as a WARN `RES … result=HTTP_4XX` | PASS | `android-3-company-mock.log` |
| My Portfolio and Practice (MOCK): `GET /me/portfolio` 200, `GET /me/practice` 200, `GET /me/practice/performance?range=1M` 200; bodies `[METADATA]` only (no cash, values or ids) | PASS | `android-4-portfolio-practice-mock.log` |
| Environment switch in Settings (MOCK → REAL, confirmation dialog; later REAL → MOCK): one `ENV from=… to=…` line each; labels follow the choice | PASS | `android-5-switch-real.log`, `android-7-switch-mock.log` |
| My Portfolio and Practice (REAL) incl. Retry / Try again | PASS (logged; see §9) | `android-5…`, `android-6-portfolio-practice-real.log` |
| Failing request: connection refused (reverse mapping removed for one cold start) → `ERR kind=CONNECTION_REFUSED type=ConnectException` | PASS | `android-8-connection-refused.log` |
| Unreachable host (first install pointed at the LAN host) → `ERR … type=ConnectException` (build before the NO_ROUTE refinement printed `CONNECT_FAILED`; the classifier now says `NO_ROUTE`, JVM test) | PASS | session log |
| Distinct ids: 51 REQ lines, 51 ids; every RES/ERR id has its REQ | PASS | `captures/` grep |
| Redaction scan over all captured lines: 0 JWTs, Bearer values, e-mails, API keys, `apikey=`/`token=` values, `mock-user:`, unredacted `Authorization`/`Set-Cookie`, uid, Practice cash/value amounts | PASS | `grep -cE` per pattern |
| Cancellation on a device | NOT RUN | covered by `ApiLoggingTest.cancellationIsReportedApartFromFailures` |
| Release build on a device | NOT RUN | gating covered by `AndroidApiLoggingTest` and `ApiLoggingTest.releaseBuildsCannotEnableLogging` |

**iOS (iPhone 16 simulator, iOS 18.3, Debug build with `STOCKSTEPS_BACKEND_URL=http://<lan>:8082`, guest).** The simulator reaches the LAN
host directly.

| Check | Result | Evidence |
|---|---|---|
| First run crashed (`EXC_BAD_ACCESS` in `NSLog`): the sink passed a Kotlin `String` through NSLog's C varargs. Fixed (the line is the format, `%` escaped); `IosApiLoggingSinkTest` now drives the real sink | FIXED, then PASS | crash report `StockSteps-2026-10-16-113843.ips` (local) |
| Same format as Android, prefixed `[StockSteps-API]`; long lines in `[i/n]` parts | PASS | `ios-mock-real.log` |
| CONFIG start line for MOCK and for REAL; `ENV from=- to=…` | PASS | same |
| Home, Markets (overview, calendar), Company Details (PFAI details/chart/why-moving/news, earnings `latest` 404) on MOCK | PASS | same |
| Portfolio (guest): sign-in card, no `/me/` request | PASS | screenshot (not kept) and log |
| REAL (switched with `defaults write` in the app container, then relaunch): meta 200, Daily Brief 200, `screener/catalog` 503 `SCREENER_UNAVAILABLE` as a WARN line | PASS | same |
| My Portfolio / Practice signed in (MOCK or REAL) on iOS | BLOCKED | the simulator app is in guest mode; no approved test account |
| Connection refused through Darwin (`NSURLError` −1004 → `CONNECTION_REFUSED`) | PASS (test) | `IosApiLoggingTest` (real Darwin client against 127.0.0.1:1) |
| Settings → Backend Data Source tap | NOT RUN | the gear tap didn't open Settings in this run; `defaults write` was used instead |

Device state afterwards: emulator-5554 back on MOCK with a debug build of this working tree (`MOCK = http://127.0.0.1:8081` through the
pre-existing `adb reverse tcp:8081`, REAL = LAN `:8082`), still signed in; iPhone 16 back on MOCK, guest. emulator-5556, the physical phone
and the process on `:8081` (pid 492) were not touched.

## 9. Diagnosis: My Portfolio and Practice on MOCK vs REAL

Traced with the logger on emulator-5554 in the same signed-in session, a few seconds apart. Read-only GETs only: no Practice order, reset,
trial or portfolio write was sent.

| Endpoint (method, path, params) | Auth | MOCK | REAL |
|---|---|---|---|
| `GET /api/v1/me/portfolio` (none) | Firebase ID token, `Authorization` (redacted) | 200, `[METADATA] {}` (empty ledger) | **503** `{"code":"USER_DATA_UNAVAILABLE","message":"Watchlists and alerts are temporarily unavailable."}` |
| `GET /api/v1/me/practice` (none) | same | 200, 7,722 bytes, `[METADATA] {portfolioId,generation,baseCurrency,startingCash,cash,…,holdings:array(0),…}` | **503** `USER_DATA_UNAVAILABLE` |
| `GET /api/v1/me/practice/performance?range=1M` | same | 200, `[METADATA] {range,available:array(2),locked:array(3),points:array(1)…}` | not sent (the overview failed first) |
| `GET /api/v1/me/entitlements`, `watchlists`, `alerts`, `screens`, `learning` | same | 200 | **503** `USER_DATA_UNAVAILABLE` (every one) |
| `GET /api/v1/me/daily-brief/[REDACTED]/personalized` | same | 200 | **500** `INTERNAL_ERROR` |
| public `GET /api/v1/meta`, `/api/v1/daily-brief/latest` | none | 200 (`mock`) | 200 (`real`, live brief) |

- **The token is accepted on REAL.** No 401 and no refresh retry. `FirebaseIdTokenAuthenticator` verified it. Auth isn't the problem.
- **Client parsing and handling are correct.** `UserApi.send` reads the error body as `ApiError` and throws
  `StockStepsApiException(503, USER_DATA_UNAVAILABLE)`. `userDataErrorKind` maps it to `SERVER`, not SESSION, so there is no "Session
  expired". My Portfolio shows "Couldn't refresh your portfolio" plus the server message and Retry, over the empty REAL cache ("Start tracking
  what you own"). Practice shows the server message and Try again. Retry repeats one GET and gets 503 again. No deserialization error occurs.
- **Root cause (server configuration, by design of the LAN REAL deployment).** The REAL container starts without Google credentials. Its log
  says: "User data storage unavailable (Google credentials not found); watchlists and alerts are disabled." `Application.kt` then uses
  `UnavailableUserDataStore`, which answers every user-data operation (portfolio, practice, watchlists, alerts, entitlements, learning,
  screens…) with 503 `USER_DATA_UNAVAILABLE`. This is decision D-R1 in `docs/LOCAL_REAL_BACKEND_PLAN.md` ("signed-in features … answer 503
  without Firestore credentials"). MOCK works because it uses the in-memory user-data store.
- **Secondary findings (not fixed):**
  1. `GET /me/daily-brief/{id}/personalized` returns **500 `INTERNAL_ERROR`**, not 503. Daily Brief's `signedIn`/`guarded` wrapper doesn't map
     `UserDataException`, so the global StatusPages handler answers 500 (the container logs "Unhandled request failure: UserDataException").
  2. The 503 message says "Watchlists and alerts are temporarily unavailable." even on Portfolio and Practice, and the apps show it verbatim.
     That is misleading copy on the server.
  3. iOS REAL `GET /api/v1/screener/catalog` → 503 `SCREENER_UNAVAILABLE` (not investigated; the REAL stack runs with screener warm-up 0).
- **To make My Portfolio and Practice work on REAL (owner decision, not done):** take the D-R1 alternative. Mount a read-only service-account
  JSON for the dev Firebase project (`GOOGLE_APPLICATION_CREDENTIALS`) into the REAL container, redeploy, then repeat the trace below. Expect
  200 on `GET /me/portfolio` and `GET /me/practice`.
- **Steps to verify later:**
  1. Start a forwarder if the emulator can't route to the LAN, or use a device on the LAN.
  2. Install a debug build pointing at REAL.
  3. Run `adb -s emulator-5554 logcat -c`.
  4. Open Portfolio → My Portfolio, then Practice.
  5. Run `adb -s emulator-5554 logcat -d -s StockSteps-API | grep -E "me/(portfolio|practice)"`.
  6. Expect `status=200 result=OK` with `[METADATA]` bodies. Don't create orders without approval.

## 10. Limitations

- Coil (Compose images), iOS `AsyncImage` and the Firebase/Google SDKs aren't logged (§2).
- Request bodies that are streamed, and responses read as streams (none today), show `[NOT AVAILABLE]`.
- Signed-in success bodies are shape only by design. Field-level debugging of a portfolio payload needs a unit test, not the log.
- The queue drops the oldest lines if more than 512 are pending. Lines go only to the device log: no file, no telemetry, no external sink.
- Path ids under `/me/` that are lower-case words (e.g. challenge slugs) stay visible; anything with digits, capitals or `_` is hidden.
- The iOS lines go through NSLog (unified logging at default level). `log stream` on the simulator needs the predicate in §7.
