# Company Details UI Phase 3 — test and build results

Run sequentially on the working tree before the commit (base `ff5662e`, then `0696f7e`). Counts from `*/build/test-results/*/*.xml`. Logs: `captures/logs/`
(run 1), `captures/logs/final/` (run 2), `captures/logs/final2/` (run 3), `captures/logs/final3/` (final: U3, Android ⓘ, iOS chip). Logs are git-ignored.

| Suite / build | Run 1 | Run 2 (chart wording) | Run 3 (caption colour) | Final run (U3, ⓘ, chip) |
|---|---|---|---|---|
| `:core:jvmTest` | PASS 608 / 0 failed | PASS 609 / 0 | PASS 610 / 0 | **PASS 611 / 0** |
| `:core:iosSimulatorArm64Test` | PASS 606 / 0 | PASS 607 / 0 | PASS 608 / 0 | **PASS 609 / 0** |
| `:app:shared:testAndroidHostTest` | PASS 281 / 0 | PASS 281 / 0 | PASS 281 / 0 | **PASS 281 / 0** |
| `:app:shared:iosSimulatorArm64Test` | PASS 278 / 0 | PASS 278 / 0 | PASS 278 / 0 | **PASS 278 / 0** |
| `:app:androidApp:assembleDebug` (`-PstockstepsMockBackendUrl=http://127.0.0.1:8083`) | PASS | PASS | PASS | **PASS** |
| `xcodebuild` simulator (`STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8083`) | PASS (two builds) | PASS | PASS | **PASS** |

Baseline (Phase 2 record): core 606 / 604, shared 278 / 275. New tests in this phase: core +5 (difference precision and
metric ids, readable dates, chart period wording, and two U3 tests for conflicting directions: a 1D day that is up vs the previous close but
down over the chart period → line, caption and spoken summary all "down"; a falling day inside a rising year → 1Y stays "up"; flat and
zero-start cases), existing assertions updated "+14%" → "+13.9%" and "+2.00% today" → "+2.00% from 9:30 AM on Oct 7", shared +3 (freshness, explains, revenue headline once; four `priceDescription` expectations gained
"Prices may be delayed.").

Flakes: none observed in four full runs. The known `PortfolioSessionStateTest` flake did not occur (its fix is still separate).
Not run: `:server:test` (no server change in this phase; a parallel session's server edits are in the tree and were not tested here).
