# Test results — Phase 9B.8 (2026-10-17, sequential, `--rerun`, working tree on `a3aa026`)

| Suite / build | Status | Result | Notes |
|---|---|---|---|
| `:core:jvmTest` | PASS | 625 / 0 | no core change |
| `:core:iosSimulatorArm64Test` | PASS | 623 / 0 | |
| `:server:test` | PASS | 697 / 0, 10 skipped | 9B.7 was 686; **+11 new** (+ new assertions in 5 existing tests); skips = Firebase-emulator suites |
| `:app:shared:testAndroidHostTest` | PASS | 289 / 0 | |
| `:app:shared:iosSimulatorArm64Test` | PASS | 286 / 0 | |
| `:app:androidApp:assembleDebug` | PASS | UP-TO-DATE | no app change |
| `xcodebuild` | PASS | BUILD SUCCEEDED | |
| Firebase emulator suites | NOT RUN | — | no user/auth boundary changed |
| REAL Alpha Vantage / SEC | BLOCKED | — | no key, no approved User-Agent |

New: `AlphaVantageFundamentalsTest` (9: engine values, unavailable-with-reason, missing ≠ zero, standalone/YTD quarters, quarter requests,
dividends, negative equity and losses, contract key parity and serialization, shared statements + no fallback + degraded datasets),
`AlphaVantageFundamentalsBenchmarkTest` (1), `RawBasisValuationTest.aSeptemberFiscalYear…` (1). Extended: `PriceBoundaryTest` (close-stamped
quotes), `MarketDataAccessGateTest` (fundamentals-only personal mode), `AlphaVantageConfigurationTest` (`FUNDAMENTALS_PRIMARY_PROVIDER`),
`FinancialMetricsEngineTest` (excludes the two new dividend metrics from `all()`). One product bug fixed during the phase: `quoteDelay`
labelled a close-stamped end-of-day quote STALE during after-hours.

Provider requests made in this phase: **none** (no network calls).
