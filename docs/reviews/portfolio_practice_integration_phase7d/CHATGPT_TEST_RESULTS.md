# Phase 7D — test and build results (sequential; the server suite with the local Firestore emulator)

| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS 615/0 (`PracticePresenterTest` gains the D2 storage-failure retry assertions) |
| `:core:iosSimulatorArm64Test` | PASS on rerun. Full run: 613 tests, 1 failure in `ApiLoggingTest.concurrentRequestsGetDistinctIdsThroughTheBackgroundWriter` (the known logging-writer concurrency flake, also in 7A; untouched). Rerun of `ApiLoggingTest` alone: 13/0 |
| `:server:test` with `FIRESTORE_EMULATOR_HOST=127.0.0.1:8189` | **PASS 565/0, 0 skipped**: the 3 news-store emulator tests now run; new `FirestorePortfolioPracticeIntegrationTest` 5/0, `FirebaseTokenVerifierTest` 3/0, `PracticeAccountingReferenceTest` 1/0 |
| `:server:test` first full run (before D2) | FAIL 1: `concurrentOrdersOnFirestoreNeverOverspendOversellOrFillTwice` (an unexpected 503 under contention), which revealed D2. Integration test reruns after the fix: 5/0 twice, then in the full run |
| `:app:shared:testAndroidHostTest` | PASS 284/0 |
| `:app:shared:iosSimulatorArm64Test` | PASS 281/0 |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` | PASS (BUILD SUCCEEDED) |
| LAN REAL `:8082` (b2b23fe): guest and forged token on `/me/practice`, `/me/portfolio` | PASS: 401 `SIGN_IN_REQUIRED` (copy defect D1 visible) |
| Devices (Android physical, iOS simulator, REAL) | BLOCKED (owner test account; FMP 429) |
| Firebase Auth emulator end to end | NOT RUN (owner approval) |

Without the emulator, the 8 emulator tests skip (`assumeTrue`), as before.
