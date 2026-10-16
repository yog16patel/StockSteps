# Test and build results — Typography & Spacing Phase 5

Working tree on `b8321ce` (uncommitted). Run sequentially on 2026-10-16. Logs: `captures/logs/` (git-ignored).

| Suite / build | Result | Count | Notes |
|---|---|---|---|
| `./gradlew :core:jvmTest --rerun` | PASS | 612 / 0 failures / 0 skipped | Core has no change in this phase; the first sequential run was Gradle up-to-date, so it was forced again with `--rerun`. |
| `./gradlew :core:iosSimulatorArm64Test --rerun` | PASS | 610 / 0 / 0 | Same (forced rerun, 8 min). No flake of the known intermittent `UserDataRepositoriesTest`. |
| `./gradlew :app:shared:testAndroidHostTest` | PASS | 284 / 0 / 0 | +2 new: `DesignTokensTest.phase5SemanticRolesKeepTheReadingOrder`, `phase5ListRowSpacingStaysOnTheGridAndDense` (Phase 4 had 282). |
| `./gradlew :app:shared:iosSimulatorArm64Test` | PASS | 281 / 0 / 0 | The same 2 new tests on Kotlin/Native (Phase 4 had 279). |
| `./gradlew :app:androidApp:assembleDebug -PstockstepsMockBackendUrl=http://127.0.0.1:8083` | PASS | — | Installed on emulator-5554. |
| `xcodebuild … CODE_SIGNING_ALLOWED=NO build` (iOS Simulator) | PASS | — | Built with `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8085` for captures, then again with `localhost:8083` to leave the simulator on its usual host. 0 errors. |
| `:server:test` | NOT RUN | — | No server change (out of scope per the brief). |

Updated existing tests: `DesignTokensTest.typographyIsAStrictHierarchy` and `typographyLineHeightsAndWeightsAreConsistent` include the
new roles (`sectionHeading` 18, `listTitle` 16, `numberCompact` 14 tabular).

## Interaction checks (device, MOCK)

| Check | Android (emulator-5554) | iOS (iPhone 16, guest) |
|---|---|---|
| Search: recent → type "apple" → results → keyboard hidden | PASS (+ scroll to the end of the list) | PASS (no scroll) |
| Search: open a result (Company Details), Back | PASS | PASS (recent rows → MSFT / AAPL) |
| Search at 2.0× / AX5 | PASS | PASS |
| Search loading ("Searching…") | NOT RUN after (seen in BEFORE; `SearchingRow` unchanged) | NOT RUN |
| Search error / no results | NOT RUN (no UI change to those states) | NOT RUN |
| Company Details section bar → Financials, scroll Highlights → Key Ratios | PASS (1.0×, 2.0×) | PASS |
| Markets scroll (movers, sectors, Research tools) | PASS | PASS (movers) |
| Watchlist, Learn, Portfolio sample (Practice tab: BEFORE only, not re-captured) | PASS | Learn PASS; Watchlist guest empty only; Portfolio/Practice NOT RUN (guest) |
| TalkBack / VoiceOver | NOT RUN | NOT RUN |
