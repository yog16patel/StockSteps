# Test results and sample logs — DEBUG-only API logging

Commit "Add DEBUG-only API request and response logging on Android and iOS with default-deny redaction and record why My Portfolio and Practice fail on the LAN REAL backend" on `44d0247`. Raw logs: `captures/test-suites*.log`, `captures/builds.log`, `captures/android-*.log`,
`captures/ios-mock-real.log` (git-ignored; the LAN address is replaced by `<lan>`).

## 1. Suites and builds (run sequentially; final run with `--rerun-tasks` after the last code change)

| Suite / build | Result | Count | Baseline (5D.5.6D) |
|---|---|---|---|
| `:core:jvmTest` | PASS | 606 / 0 failed | 581 + 25 new (13 `ApiLoggingTest`, 10 `ApiLogRedactionTest`, 2 `ApiLogErrorsJvmTest`) |
| `:core:iosSimulatorArm64Test` | PASS | 604 / 0 | 581 + 23 (the common tests; the JVM test is JVM only) |
| `:app:shared:testAndroidHostTest` | PASS | 276 / 0 | 273 + 3 `AndroidApiLoggingTest` (real OkHttp client) |
| `:app:shared:iosSimulatorArm64Test` | PASS | 273 / 0 | 269 + 4 `IosApiLoggingTest` / `IosApiLoggingSinkTest` (real Darwin client, real NSLog sink) |
| `:app:androidApp:assembleDebug` | PASS | — | default properties and the two device builds |
| `xcodebuild` Debug, iphonesimulator | PASS | — | scratchpad DerivedData, `STOCKSTEPS_BACKEND_URL` set to the LAN REAL host |
| Release builds | NOT RUN | — | gating is unit-tested; release signing isn't configured locally |

No flaky tests seen. The known intermittent `UserDataRepositoriesTest` (core iOS) passed on both runs.

## 2. Requirement → test

| Requirement | Test(s) |
|---|---|
| Correlation ids; REQ/RES share the id | `requestAndResponseShareOneIdWithMethodUrlAndRedactedQueryHeadersAndJson` |
| Method, URL, host, port, path, query, timestamp | same |
| Query, header, JSON and nested redaction | same, plus `urlsKeepRouteAndSafeQuery…`, `headersAreDefaultDeny`, `jsonIsRedactedAtEveryDepth`, `textRedactsTokenShapes…` |
| Signed-in metadata only; auth unchanged | `signedInPayloadsAreMetadataOnlyAndAuthIsUnchanged`, `signedInPathIdsAndRequestBodiesAreHidden` |
| Error responses (4xx/5xx), mapping unchanged | `errorResponsesKeepTheirCodeAndAreWarnings` |
| Connection failure, DNS, TLS, socket timeout, no route | `connectionFailuresAreClassifiedAndRethrownUnchanged`, `ApiLogErrorsJvmTest`, `AndroidApiLoggingTest.appClientLogsAConnectionRefusedFailure`, `IosApiLoggingTest.appClientLogsAConnectionRefusedFailure` |
| Request timeout | `timeoutsAreFailuresWithASanitizedMessage` |
| Cancellation apart from failure | `cancellationIsReportedApartFromFailures` |
| Serialization failures (type + safe context) | `deserializationFailuresGiveTypeAndFieldsOnly`, `serializationDetailNamesFieldsAndPathOnly` |
| Truncation; huge bodies not inspected | `largeBodiesAreTruncatedAndHugeOnesNotInspected`, `bodiesFollowTypeSizeAndUserDataRules` |
| Binary exclusion; body still readable | `binaryBodiesAreOmittedAndStillReadable` (+ the full-body assertions above) |
| Concurrency, distinct ids, off-thread writer | `concurrentRequestsGetDistinctIdsThroughTheBackgroundWriter` |
| Environment labels from the explicit choice; switch lines | `environmentLabelsComeFromTheExplicitChoiceAndSwitchesAreLogged` |
| Release disabled | `releaseBuildsCannotEnableLogging`, `AndroidApiLoggingTest.onlyADebugBuildOfADebuggableAppMayLog`, `IosApiLoggingSinkTest.enableUsesTheDebugBinaryFlag` |

## 3. Device verification

- Android emulator-5554: PASS (details in `docs/DEBUG_API_LOGGING.md` §8).
- iOS iPhone 16 guest: PASS after the NSLog fix.
- Signed-in iOS: BLOCKED.
- Device cancellation and release device builds: NOT RUN.
- Redaction grep over all captured lines found 0 occurrences of each of:
  - JWT (`eyJ…`), `Bearer` values, e-mail addresses, `AIza…` keys;
  - `apikey=` or `token=` values, `mock-user:`;
  - unredacted `Authorization` or `Set-Cookie`;
  - `uid`, Practice `cash` or `totalValue` values.

## 4. Sanitized sample logs

**Android (Logcat `-s StockSteps-API`, emulator-5554, signed in; ports are the temporary localhost forwarder):**

```
I StockSteps-API: CONFIG start stored=MOCK effective=MOCK real=http://127.0.0.1:18082/ mock=http://127.0.0.1:18081/
I StockSteps-API: REQ id=fzk9-0020 env=MOCK ts=2026-10-16T15:29:59.901133Z method=GET url=http://127.0.0.1:18081/api/v1/me/practice host=127.0.0.1 port=18081 path=/api/v1/me/practice query=- contentType=- headers={Authorization=[REDACTED], Accept=application/json} body=<empty>
I StockSteps-API: RES id=fzk9-0020 env=MOCK method=GET path=/api/v1/me/practice status=200 result=OK durationMs=164 size=7722 contentType=application/json headers={connection=keep-alive, content-length=7722, content-type=application/json} body=[METADATA] {portfolioId:string,generation:number,baseCurrency:string,startingCash:string,cash:string,holdingsValue:string,totalValue:string,…,holdings:array(0),…}
I StockSteps-API: RES id=fzk9-0021 env=MOCK method=GET path=/api/v1/me/practice/performance status=200 result=OK durationMs=65 size=635 contentType=application/json headers={…} body=[METADATA] {range:string,available:array(2),locked:array(3),points:array(1)[{date:string,value:string}],baseCurrency:string,…}
W StockSteps-API: RES id=fzk9-0018 env=MOCK method=GET path=/api/v1/earnings/company/SXTC/latest status=404 result=HTTP_4XX durationMs=237 size=107 contentType=application/json headers={…} body={"code":"NO_REPORT","message":"No published earnings results are available for this company."}
I StockSteps-API: ENV id=fzk9-0022 from=MOCK to=REAL host=127.0.0.1 port=18082
W StockSteps-API: RES id=fzk9-0026 env=REAL method=GET path=/api/v1/me/portfolio status=503 result=HTTP_5XX durationMs=306 size=108 contentType=application/json headers={…} body={"code":"USER_DATA_UNAVAILABLE","message":"Watchlists and alerts are temporarily unavailable."}
W StockSteps-API: RES id=fzk9-0030 env=REAL method=GET path=/api/v1/me/practice status=503 result=HTTP_5XX durationMs=387 size=108 contentType=application/json headers={…} body={"code":"USER_DATA_UNAVAILABLE","message":"Watchlists and alerts are temporarily unavailable."}
W StockSteps-API: RES id=fzk9-0031 env=REAL method=GET path=/api/v1/me/daily-brief/[REDACTED]/personalized status=500 result=HTTP_5XX durationMs=20 size=80 contentType=application/json headers={…} body={"code":"INTERNAL_ERROR","message":"An unexpected error occurred."}
W StockSteps-API: ERR id=zy6q-0001 env=MOCK method=GET path=/api/v1/meta kind=CONNECTION_REFUSED type=ConnectException durationMs=2666 message=Failed to connect to /127.0.0.1:18081
```

**iOS (`log stream … 'eventMessage CONTAINS "StockSteps-API"'`, iPhone 16, guest):**

```
[StockSteps-API] CONFIG start stored=REAL effective=REAL real=http://<lan>:8082/ mock=http://<lan>:8081/
[StockSteps-API] ENV id=8dfc-0001 from=- to=REAL host=<lan> port=8082
[StockSteps-API] REQ id=8dfc-0001 env=REAL ts=2026-10-16T15:44:50.276932Z method=GET url=http://<lan>:8082/api/v1/meta host=<lan> port=8082 path=/api/v1/meta query=- contentType=- headers={Accept=application/json} body=<empty>
[StockSteps-API] RES id=8dfc-0001 env=REAL method=GET path=/api/v1/meta status=200 result=OK durationMs=33 size=26 contentType=application/json headers={Content-Length=26, Connection=keep-alive, Content-Type=application/json} body={"dataMode":"real"}
[StockSteps-API] RES id=8dfc-0003 env=REAL method=GET path=/api/v1/screener/catalog status=503 result=HTTP_5XX durationMs=183 size=118 contentType=application/json headers={…} body={"code":"SCREENER_UNAVAILABLE","message":"The screening universe couldn't be loaded. Try again shortly."}
[StockSteps-API] RES id=kwx7-0007 env=MOCK method=GET path=/api/v1/earnings/company/PFAI/latest status=404 result=HTTP_4XX durationMs=39 size=107 contentType=application/json headers={…} body={"code":"NO_REPORT","message":"No published earnings results are available for this company."}
[StockSteps-API] [1/3] RES id=kwx7-0008 env=MOCK method=GET path=/api/v1/stocks/PFAI/details status=200 result=OK durationMs=118 size=16846 …
```

**From the tests (MockEngine; the same formatter):**

```
REQ … url=https://stocksteps.test:8443/api/v1/stocks/AAPL/quote?symbol=AAPL&apikey=[REDACTED]&email=[REDACTED] … headers={Authorization=[REDACTED], Cookie=[REDACTED], X-Firebase-AppCheck=[REDACTED], Accept=application/json} …
RES … body={"symbol":"AAPL","price":189.5,"owner":"[REDACTED]","items":[{"apiKey":"[REDACTED]","name":"Apple Inc."}]}
ERR … kind=TIMEOUT_REQUEST type=HttpRequestTimeoutException … message=Request timeout has expired [url=https://stocksteps.test/api/v1/meta?apikey=[REDACTED] request_timeout=50 ms]
ERR … kind=SERIALIZATION type=JsonConvertException target=ApiError detail=missing=…
CANCEL id=… method=GET path=/api/v1/meta type=… durationMs=…
```
