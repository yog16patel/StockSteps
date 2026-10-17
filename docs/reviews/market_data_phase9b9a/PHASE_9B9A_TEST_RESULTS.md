# Phase 9B.9A final — test results (2026-10-17, sequential, `--rerun`, working tree on `e163b38`)

| Suite / build | Status | Result |
|---|---|---|
| `:core:jvmTest` | PASS | 625 / 0 |
| `:core:iosSimulatorArm64Test` | PASS | 623 / 0 (the known `ApiLoggingTest` race did not occur) |
| `:server:test` | PASS | 713 / 0, 10 skipped (Firebase-emulator suites; no user/auth change) — was 707; **+6** `FinancialScenariosTest` |
| `:app:shared:testAndroidHostTest` | PASS | 289 / 0 |
| `:app:shared:iosSimulatorArm64Test` | PASS | 286 / 0 |
| `:app:androidApp:assembleDebug` | PASS | UP-TO-DATE (no app change) |
| `xcodebuild` (iOS Simulator) | PASS | BUILD SUCCEEDED |
| Firebase emulators | NOT RUN | no user-owned flow changed |
| Live Alpha Vantage / SEC | NOT RUN | no approval for further requests; SEC needs a User-Agent |

During development: three new scenario assertions had wrong expected growth (my arithmetic: 5,000 vs 4,000 = 25 %, not 20 %); corrected,
no product defect. Logs: `captures/summary-final.txt` (git-ignored).
