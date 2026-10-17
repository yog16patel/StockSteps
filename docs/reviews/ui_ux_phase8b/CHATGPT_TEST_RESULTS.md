# Phase 8B — test and build results

The suites ran one after another with `--rerun`, on the 8B changes just before they were committed (2026-10-17). Logs are in `captures/logs/`.

| Step | Result | Counts (tests / failures / errors / skipped) |
|---|---|---|
| `./gradlew :core:jvmTest --rerun` | PASS | 618 / 0 / 0 / 0 (8A: 615; +3 new) |
| `./gradlew :core:iosSimulatorArm64Test --rerun` | PASS (first run, no flake) | 616 / 0 / 0 / 0 (8A: 613) |
| `./gradlew :server:test --rerun` | PASS | 571 / 0 / 0 / 8 skipped (Firestore-emulator tests; skipped, not passed) |
| `./gradlew :app:shared:testAndroidHostTest --rerun` | PASS | 289 / 0 / 0 / 0 (8A: 287; +2 new) |
| `./gradlew :app:shared:iosSimulatorArm64Test --rerun` | PASS | 286 / 0 / 0 / 0 (8A: 284) |
| `./gradlew :app:androidApp:assembleDebug` | PASS | — |
| `xcodebuild … -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` | PASS (`** BUILD SUCCEEDED **`) | — |

## New and changed tests

**New:**
- core `ScreenerDisplayTest.scopeLineNamesTheListAndItsSize`;
- core `ScreenerDisplayTest.sectorShortcutMirrorsTheSheetFilter`;
- core `ScreenerDisplayTest.rowSectorIsSpokenOnlyWhenKnown`;
- shared `MarketMoverDisplayTest`, two tests: missing, NaN, zero, positive and negative values.

**Changed:** `ScreenerDisplayTest.sortLabelsAreShortAndSpokenInWords` now expects words instead of arrows (2 expected strings).

**Not covered by a unit test:** `ScreenerPresenter.selectSector`. It was verified on the device instead: one request per tap, and the
applied query plus the sector (`captures/after/*/light-sector-selected`).

## Device builds

The screenshots used the same sources with the MOCK URL overrides:
- Android: `-PstockstepsMockBackendUrl=http://127.0.0.1:8087`;
- iOS: `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8087`.

Both built.
