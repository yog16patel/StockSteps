# Phase 8D — test and build results

The suites ran one after another with `--rerun` on the 8D changes just before they were committed (2026-10-17). Logs are in `captures/logs/`.

| Step | Result | Counts (tests / failures / errors / skipped) |
|---|---|---|
| `./gradlew :core:jvmTest --rerun` | PASS | 619 / 0 / 0 / 0 (8C: 618; +1 new) |
| `./gradlew :core:iosSimulatorArm64Test --rerun` | PASS | 617 / 0 / 0 / 0 |
| `./gradlew :server:test --rerun` | PASS | 571 / 0 / 0 / 8 skipped (Firestore-emulator tests: SKIPPED, not passed) |
| `./gradlew :app:shared:testAndroidHostTest --rerun` | PASS | 289 / 0 / 0 / 0 |
| `./gradlew :app:shared:iosSimulatorArm64Test --rerun` | PASS | 286 / 0 / 0 / 0 |
| `./gradlew :app:androidApp:assembleDebug` | PASS | — |
| `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | PASS (`** BUILD SUCCEEDED **`) | — |
| UI and instrumentation tests | NOT RUN | the repo has no Android instrumentation or XCUITest target |
| Firestore emulator suite | NOT RUN in 8D | no server or persistence change; last run in 7D (565/0) |

## Tests

**New:** `ScreenerDisplayTest.scopeLabelIsShortAndHonest`, which checks that the always-visible label says "not the whole market" and
covers `ABOUT_LIST`.

**Not unit-tested (layout only, verified with screenshots):**
- Practice heading role;
- iOS sign-in block;
- Learn intro at very large text;
- research step wrap.
