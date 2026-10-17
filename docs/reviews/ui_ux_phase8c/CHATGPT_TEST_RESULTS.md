# Phase 8C — test and build results

The final sequential run used `--rerun` on the 8C changes just before they were committed (2026-10-17), after the G1 revert. Logs are in
`captures/logs/`.

| Step | Result | Counts (tests / failures / errors / skipped) |
|---|---|---|
| `./gradlew :core:jvmTest --rerun` | PASS | 618 / 0 / 0 / 0 |
| `./gradlew :core:iosSimulatorArm64Test --rerun` | PASS | 616 / 0 / 0 / 0 |
| `./gradlew :server:test --rerun` | PASS | 571 / 0 / 0 / 8 skipped (Firestore-emulator tests: SKIPPED, not passed) |
| `./gradlew :app:shared:testAndroidHostTest --rerun` | PASS | 289 / 0 / 0 / 0 |
| `./gradlew :app:shared:iosSimulatorArm64Test --rerun` | PASS | 286 / 0 / 0 / 0 |
| `./gradlew :app:androidApp:assembleDebug` | PASS | — |
| `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | PASS (`** BUILD SUCCEEDED **`) | — |
| Firestore emulator suite | NOT RUN in 8C | no server or persistence change; last run in 7D (565/0) |

## Earlier failing run

The first full run had `:app:shared:testAndroidHostTest` FAIL with 1 of 289:
`PortfolioPhaseCTest.donutOnlyForValidAllocations`. It failed on the G1 donut-rule change, which contradicts that Phase C decision.

`:app:shared:iosSimulatorArm64Test` also failed in that run; I attribute it to the same change. G1 was reverted (owner decision
D8C-3), and both suites pass above.

## Tests changed

`WatchlistPresenterTest`: 2 expected strings, now "Updated Oct 7, 5:15 PM ET". The test counts are unchanged from 8B; the changes to
existing assertions are covered by these updated strings.

**Not unit-tested:** the overview reorder and the borderless Practice changes are layout changes. They are verified with Android frames.
