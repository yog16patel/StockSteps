# Test results — Discover REAL backend fix

Commit "Fix Discover Stocks on the LAN REAL backend with distinct plan-restricted, rate-limited and temporary screener errors, a provider cooldown on the universe request and no Retry when the FMP plan excludes the screener on Android and iOS" on `c6baf6a` (pushed, not deployed). Run sequentially on 2026-10-16 (`--rerun` for test tasks). Logs are in the session scratchpad,
not in the repository.

## 1. Suites and builds

| Suite / build | Result | Notes |
|---|---|---|
| `:core:jvmTest` | PASS 613 tests, 0 failures, 0 skipped | +1: `ScreenerPresenterTest.catalogErrorsSayWhetherRetryingHelps` |
| `:core:iosSimulatorArm64Test` | PASS 611 / 0 / 0 | the same new test on Kotlin/Native |
| `:server:test` | PASS 515 / 0, 3 skipped | the skips are pre-existing; +7 `ScreenerUniverseFailureTest` |
| `:app:shared:testAndroidHostTest` | PASS 284 / 0 / 0 | unchanged count |
| `:app:shared:iosSimulatorArm64Test` | PASS 281 / 0 / 0 | unchanged count |
| `:app:androidApp:assembleDebug` | PASS | |
| `xcodebuild` iOS simulator (generic, unsigned) | PASS | BUILD SUCCEEDED (shared code and Swift changed) |

No flaky reruns were needed.

## 2. New regression tests

`server/src/test/kotlin/org/example/stocksteps/screener/ScreenerUniverseFailureTest.kt` (MockEngine, REAL adapters, warm-up 0 as on the LAN):

| Test | Covers |
|---|---|
| `planRestrictionSaysRetryingWontHelpAndIsNotRepeatedForAnHour` | 402 and 403 → `SCREENER_PROVIDER_RESTRICTED`; 1 upstream over 5 opens + a search in 50 min; retried after 1 h; recovers |
| `rateLimitIsReportedAndCoolsDownForTenMinutes` | 429 → `SCREENER_RATE_LIMITED`; no retry within 10 min |
| `anOutageIsTemporaryAndRetriedAfterThirtySeconds` | 500 → `SCREENER_UNAVAILABLE` (unchanged copy); 30 s cooldown; recovery |
| `anUnreadableProviderAnswerIsTemporary` | invalid JSON → `SCREENER_UNAVAILABLE` |
| `concurrentIdenticalRequestsShareOneProviderRequest` | 20 concurrent opens: 1 refused request; healthy: 3 (one per exchange) |
| `theRouteAnswersWithTheCodeAndMessage` | HTTP 503, JSON, code, no "Try again shortly", no provider host in the body |
| `discoverJourneysReuseTheUniverseAndPageCaches` | request counts: open 3, first page 20, filter 0, identical filter 0, catalog again 0 |

`core/src/commonTest/.../screener/ScreenerEngineTest.kt` → `ScreenerPresenterTest.catalogErrorsSayWhetherRetryingHelps` runs this
sequence and checks:
- restricted: no Retry, the "isn't available" title, no presets;
- rate limited: Retry kept;
- offline: the connection copy, no code;
- unreadable answer: the unexpected-response copy;
- recovery: a loaded catalog isn't requested again.

## 3. Endpoint and device checks

| Check | Result | Evidence |
|---|---|---|
| REAL `:8082` catalog / search ×3 | 503 `SCREENER_UNAVAILABLE` (deployed code is from before the fix); invalid query 400 | curl, §1 of the report |
| LAN MOCK `:8081` catalog / search ×3 | 200, `sampleData=true`, 160 / 89 / 39 matches, cursor present | curl |
| FMP `company-screener` (one probe, `limit=1`, inside the REAL container) | 402 "Restricted Endpoint" | the key was never printed |
| FMP `sp500-constituent`, `batch-quote` | 402 | |
| FMP `profile?symbol=AAPL` | 200 | |
| Pixel 7 REAL before (guest) | PASS reproduction: "Couldn't load the screener … Try again shortly", log 503 | `captures/before_real_discover.png` (git-ignored) |
| Pixel 7 after (stub with the new 503 body) | BLOCKED: the phone locked with a secure lock screen; no PIN entered | stub log has no app requests |
| Pixel 7 REAL after redeploy | BLOCKED: needs owner approval to redeploy | — |
| Genuine REAL results, filters, sort, pagination, Company Details | BLOCKED: FMP plan | — |
| MOCK → REAL switch | BLOCKED: phone locked | — |
| iOS device / simulator | NOT RUN (build PASS) | — |

Phone state at the end: the normal debug build of this tree (`CONFIG stored=REAL effective=REAL real=http://<lan>:8082/`), guest. The
stub reverse mapping was removed and the stub stopped. The emulators, `:8081`/`:8083` on the Mac and the Ubuntu containers weren't
changed.
