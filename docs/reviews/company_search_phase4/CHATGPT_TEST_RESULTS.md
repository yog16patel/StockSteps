# Phase 4 Company Search — test and build results

Final working tree (baseline `6d6b68e` + this phase's uncommitted changes). Commands were run one after another from the repo root.
Counts come from the JUnit XML in `*/build/test-results/` (all fresh from this run).

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest` | PASS 612/0 (`PortfolioSessionStateTest` passed in this run) |
| `./gradlew :core:iosSimulatorArm64Test` | PASS 610/0 |
| `./gradlew :server:test` | PASS 508/0 (3 skipped, as before: opt-in emulator/REAL tests) |
| `./gradlew :app:shared:testAndroidHostTest` | PASS 282/0 |
| `./gradlew :app:shared:iosSimulatorArm64Test` | PASS 279/0 |
| `./gradlew :app:androidApp:assembleDebug -PstockstepsMockBackendUrl=http://127.0.0.1:8083` | PASS |
| `xcodebuild … -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8083 build` | PASS (`** BUILD SUCCEEDED **`) |
| `./gradlew :server:installDist` (for the :8084 before/after check) | PASS |

## New tests (3)
| Test | Covers | On the old code (not re-run; by construction) |
|---|---|---|
| `server StockSearchTest.innerWhitespaceAndCaseShareOneNormalizedProviderQueryAndCacheEntry` | normalization, cache key, provider call count | would fail: the provider receives `apple  inc`, and the three spellings take 6 calls (matches the :8083 reproduction) |
| `core SearchRankingTest.exactTickerBeatsPartialNamesAndShareClassesStayApart` | precedence: APP vs AAPL, AAPL vs ETF, Microsoft/micro, GOOG/GOOGL, SPY, unknown, BRK.B, whitespace | would pass: ranking unchanged (regression guard) |
| `shared StockSearchViewModelTest.olderSlowerQueryNeverReplacesNewerResultsEvenWithoutCancellation` | `app` → `apple` race, B first, A late and non-cancellable; loading state; 2 requests; re-typing same query = 0 | would pass: VM unchanged (regression guard) |

The 3 new tests are in the counts above (core +1 on each target, server +1, shared +1 on each target).

## Live checks (MOCK)
| Check | Result |
|---|---|
| :8083 (baseline server) `apple  inc` | `[]` (defect reproduced) |
| :8084 (fixed build, started and stopped by this session) `apple  inc` / `apple inc` | `AAPL` / `AAPL`, cache 1 hit / 3 misses |
| Android emulator-5554 request log (`StockSteps-API`) | 1 request per settled query; 0 on Back and tab switches |
| iOS iPhone 16 request log (`[StockSteps-API]`) | 1 request per settled query; 0 on Back |
