# Top Bars Phase 2 (KAN-16) — test results

Sequential runs on the working tree (base `77a2656` + this phase's changes). Logs: `captures/` (git-ignored).

| Suite / build | Run 1 | Rerun | Notes |
|---|---|---|---|
| `:core:jvmTest` | FAIL — 606 tests, 1 failed | PASS — 606/0 (`--rerun`) | `PortfolioSessionStateTest.anEndedSessionIsSessionExpiredUntilTheNextSuccessfulSyncAndKeepsTheCachedLedger` — known flake (presenter coroutine race under load); no core change in this phase; fix pending in worktree `charming-hodgkin-98e850` |
| `:core:iosSimulatorArm64Test` | FAIL — 604 tests, 1 failed | PASS — 604/0 (`--rerun`) | `PortfolioSessionStateTest.aChangeThatFailsWithAnEndedSessionMarksItExpired` — same flaky class |
| `:app:shared:testAndroidHostTest` | PASS — 278/0 | — | +2: `FinancialsDisplayTest.titleUsesTheTicker…`, `ValuationDisplayTest.titleUsesTheTicker` |
| `:app:shared:iosSimulatorArm64Test` | PASS — 275/0 | — | same 2 new tests (common) |
| `:app:androidApp:assembleDebug` | PASS | — | `-PstockstepsMockBackendUrl=http://127.0.0.1:8083` |
| `xcodebuild` (iphonesimulator, `CODE_SIGNING_ALLOWED=NO`) | PASS | — | final build includes every iOS change |

Navigation regression: no navigation code changed (`TabNavigation`, `TabRouter`, `TabNavigator`, routes untouched); the core navigation tests
(`TabNavigationModelTest`, `NavigationPolicyTest`, `PortfolioSectionTest`) and the shared `AppScreensTest` are in the suites above (all PASS on the final runs).

Request check (DEBUG API logging, MOCK): Android 0 `REQ` over 6 tab switches (6 on opening a company — channel live); iOS 0 `REQ` over 5 tab
switches after first visits.
