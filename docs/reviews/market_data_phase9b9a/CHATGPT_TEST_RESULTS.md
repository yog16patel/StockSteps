# Test results — Phase 9B.9A (2026-10-17, sequential, `--rerun`, working tree on `30bf1df`)

**Authenticated Alpha Vantage requests: 0** (terms unclear for a company-associated workflow; no key). Everything below is fixtures/MockEngine.

| Suite / build | Status | Result | Notes |
|---|---|---|---|
| `:core:jvmTest` | PASS | 625 / 0 | no core change |
| `:core:iosSimulatorArm64Test` | PASS (flake on first run) | 623 tests, 1 failure → class re-run 13 / 0 | the known `ApiLoggingTest.concurrentRequestsGetDistinctIdsThroughTheBackgroundWriter` `ConcurrentModificationException` race (8E/7A notes); no core code changed; first-run XML in `captures/` |
| `:server:test` | PASS | 702 / 0, 10 skipped | 9B.8 was 697; **+5 new** (`AlphaVantageFreeTierTest`); 6 existing tests updated for explicit entitlement and loopback-only clients |
| `:app:shared:testAndroidHostTest` | PASS | 289 / 0 | |
| `:app:shared:iosSimulatorArm64Test` | PASS | 286 / 0 | |
| `:app:androidApp:assembleDebug` | PASS | UP-TO-DATE | |
| `xcodebuild` | PASS | BUILD SUCCEEDED | |
| `:server:alphaVantageSmokeTest` | NOT RUN (refused by design) | "ALPHA_VANTAGE_API_KEY is not set", exit 2, 0 requests | the opt-in guard works |
| Firebase emulator suites | NOT RUN | — | no user/auth boundary changed |

New tests (`AlphaVantageFreeTierTest`): a rate setting never implies premium (and the free lane ignores it); premium functions
(OVERVIEW, adjusted history) cost 0 upstream calls on FREE and history is `compact`; the free daily budget is exact (not halved by a 0.5
margin), capped at 25, enforced at 20 with local refusals; startup rules (entitlement values, budget > 25, plan ignored on free, premium
needs a plan, loopback-only clients, no secrets in messages); the smoke runner is opt-in, bounded (budget 3 → 3 sent), stops at the
first refusal (rate limit on request 2 → 2 sent, rest skipped) and its report has no key or URL.
