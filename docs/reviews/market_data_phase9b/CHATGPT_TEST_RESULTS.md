# Test results — Phase 9B (2026-10-17, sequential, `--rerun`, working tree on `e69626b`)

Logs: `captures/` (git-ignored): `_core_jvmTest.log`, `_core_iosSimulatorArm64Test.log`, `_server_test.log`,
`_app_shared_testAndroidHostTest.log`, `_app_shared_iosSimulatorArm64Test.log`, `_app_androidApp_assembleDebug.log`, `xcodebuild.log`,
`summary.txt`, `phase9b-market-data-benchmark.txt`. Counts read from the JUnit XML written by this run.

| Suite / build | Status | Result | Notes |
|---|---|---|---|
| `:core:jvmTest` | PASS | 625 tests, 0 failures | unchanged by 9B (no core change) |
| `:core:iosSimulatorArm64Test` | PASS | 623 tests, 0 failures | the known `ApiLoggingTest` race did not occur |
| `:server:test` | PASS | 652 tests, 0 failures, 10 skipped | 9A was 604 (10 skipped); **+48 new 9B tests**; the 10 skips are the Firebase-emulator suites (no emulator in this run) |
| `:app:shared:testAndroidHostTest` | PASS | 289 tests, 0 failures | no shared change |
| `:app:shared:iosSimulatorArm64Test` | PASS | 286 tests, 0 failures | no shared change |
| `:app:androidApp:assembleDebug` | PASS | UP-TO-DATE | no Android input changed since 8E's build |
| `xcodebuild` (generic iOS Simulator, unsigned) | PASS | BUILD SUCCEEDED | no iOS change |
| Firebase Auth/Firestore emulator suites | NOT RUN | — | no user-owned flow changed (9A/7D results stand) |
| Devices (emulator-5554, iPhone 16) | NOT RUN | — | no UI or contract change |
| Live SEC / FMP / Finnhub | NOT RUN (by rule) | — | automated tests use fixtures/MockEngine only; SEC needs the owner's `SEC_USER_AGENT` |

## New 9B tests (48)
| Class | Tests | Covers |
|---|---:|---|
| `marketdata.SecEdgarParsingTest` | 10 | parsing, malformed rows, forms, fiscal labels from dates, YTD never quarters, Q4 derivation (flows only), restatements/amendments, currency choice, share classes, provenance, mapping to `FinancialPeriodStatement`, ticker/submissions files |
| `marketdata.SecEdgarClientTest` | 8 | User-Agent required and sent, bounded endpoint labels, single flight (25 concurrent → 1), 24 h cache, 404 cached, 403/429 cooldowns (5 reads → 1 upstream), size bounds (declared and undeclared), malformed facts, `sec` lane pacing and ceiling |
| `marketdata.FinancialMetricsEngineTest` | 11 | hand-calculated FY metrics, TTM (quarters, FY, gaps, short history), P/E, P/S, yield, unusable prices, losses and bad denominators, gross-profit fallback, period/currency rules, splits (unverified vs verified), ambiguity, determinism |
| `marketdata.SourceSelectionAndLicensingTest` | 8 | fail-closed registry, derived inheritance, signed agreement per channel, licence mode, deterministic order, no REAL→MOCK / unlicensed / basis mix, one attempt on 402/429, stale last resort, provenance can't be relabelled |
| `marketdata.PriceBoundaryTest` | 4 | series metadata and cleaning, EOD vs stale (weekend), quote delay classes, basis compatibility |
| `marketdata.MarketDataConfigurationTest` | 2 | startup validation without echoing values; SEC limits don't block `verified` |
| `screener.DiscoverDailyBudgetTest` | 4 | 0 = no requests, cap under 50 concurrent opens + UTC reset, refusals count, cache hits free |
| `service.MarketDataPhase9bBenchmarkTest` | 1 | two-week Discover before/after, 300-open storm, SEC 1,000 readers × 20 companies |

Existing benchmarks re-run unchanged inside `:server:test` (Phase 3, 7A quote quota, 7B Markets EOD, 7C Portfolio/Practice EOD, 7E curated
Discover with `perDay = null`, 9A rate-limit abuse). Earlier during development (not counted above): two test-logic corrections in the new
tests (a UTC-midnight crossing and a missing second pass), no product defect.

## Provider requests before → after (MockEngine)
Discover highest day 465 → 100; two weeks 3,875 → 1,400; 300 cold concurrent opens 310 → 100; SEC 1,000 readers × 20 companies cold 21,
warm 0; SEC 403/429 × 5 → 1 each. Latency: no hot path changed (`apiCall` untouched); not separately measured.
