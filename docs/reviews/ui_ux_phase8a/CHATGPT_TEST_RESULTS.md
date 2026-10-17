# Phase 8A — test and build results

The suites ran one after another with `--rerun`, on the 8A changes just before they were committed (2026-10-17). Logs are in `captures/logs/`.

| Step | Result | Counts (tests / failures / errors / skipped) |
|---|---|---|
| `./gradlew :core:jvmTest --rerun` | PASS | 615 / 0 / 0 / 0 |
| `./gradlew :core:iosSimulatorArm64Test --rerun` | PASS (first run, no flake) | 613 / 0 / 0 / 0 |
| `./gradlew :server:test --rerun` | PASS | 571 / 0 / 0 / 8 skipped (the Firestore-emulator tests, as in 7E) |
| `./gradlew :app:shared:testAndroidHostTest --rerun` | PASS | 287 / 0 / 0 / 0 (was 284: +3 new) |
| `./gradlew :app:shared:iosSimulatorArm64Test --rerun` | PASS | 284 / 0 / 0 / 0 (was 281: +3 new) |
| `./gradlew :app:androidApp:assembleDebug` | PASS | — |
| `xcodebuild … -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` | PASS (`** BUILD SUCCEEDED **`) | — |

New or extended tests:
- `MarketsScreenPresentationTest.statusHeadlineJoinsTheMarketAndItsStatus`;
- `MarketsScreenPresentationTest.sectorBarsDivergeAroundTheCentre`;
- `HomeDashboardPresentationTest.portfolioSummaryUsesThePortfolioOverviewWording`;
- `DesignTokensTest` (asserts `pageSectionGap`).

Device builds for the screenshots used the same sources with the MOCK URL overrides:
- Android: `-PstockstepsMockBackendUrl=http://127.0.0.1:8087`;
- iOS: `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8087`.

Both built successfully.
