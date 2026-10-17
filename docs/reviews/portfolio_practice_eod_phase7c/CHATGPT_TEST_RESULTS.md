# Phase 7C — test and build results (sequential, this working tree)

| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS 615/0 |
| `:core:iosSimulatorArm64Test` | PASS 613/0 |
| `:server:test` | PASS 556/0, 3 skipped (pre-existing Firestore-emulator tests); new `PracticeEodTest` 6/0, `PortfolioEodTest` 5/0, `PortfolioPracticeEodBenchmarkTest` 1/0; `MarketsEodTest` 14/0 (+1 rate-limit case); existing `PracticeServiceTest` 24/0, `PortfolioMarketServiceTest`, `PortfolioRoutesTest`, `PracticeRoutesTest` pass |
| `:app:shared:testAndroidHostTest` | PASS 284/0 (`PortfolioPresentationTest` gains the closing-price assertion) |
| `:app:shared:iosSimulatorArm64Test` | PASS 281/0 |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` (iOS simulator, generic) | PASS (BUILD SUCCEEDED) |
| Firestore emulator (atomicity, persistence across restart) | BLOCKED (emulator runs only with owner approval; 3 skipped tests) |
| Android physical / iOS simulator REAL passes | BLOCKED (redeploy approval, FMP key rate-limited, owner test account) |

## Brief items → tests

| Area | Items | Tests |
|---|---|---|
| EOD | valid close, weekend/holiday, delayed publication, missing/stale, invalid, concurrent, 402/429 | `MarketsEodTest` (7B calendar cases), `PracticeEodTest.lateStaleMissingAndRestrictedClosesHaveTheirOwnReasons`, `PortfolioEodTest.aLateCloseIsUsedWithItsDate`, `…TotalUnavailableNotPartial`, `…ShareOneCloseRequestPerSymbol` |
| EOD | currency mismatch | Practice: only USD/CAD (`UNSUPPORTED_INSTRUMENT`, existing tests); Portfolio: listing→trade currency at the close date, reporting at the latest FX (`completeValuationUsesDatedClosesAndTheLatestFx`) |
| My Portfolio | complete, partial, cost basis and P/L, allocation, FX, no zero substitution, persisted holdings untouched | `PortfolioEodTest` (5) |
| Practice | buy, partial/full sell, insufficient cash/shares, invalid quantity, missing/stale, duplicate and concurrent requests | `PracticeEodTest` (6) |
| Practice | atomic persistence, session restoration | in-memory store only; Firestore BLOCKED |
| Security | user isolation, unauthorized, guest, Firestore unavailable | existing route tests (401), `FirestoreFailureMappingTest`; `PortfolioEodTest` (10 users, public closes only) |
| Client | shared presentation, date formatting, unavailable states | `PortfolioPresentationTest` (closing-price line), `PracticeFormatTest` (7A "Unavailable") |
| No REAL → MOCK | | `eod` wired only in REAL; tests use REAL adapters with fakes, `sampleData = false` |
