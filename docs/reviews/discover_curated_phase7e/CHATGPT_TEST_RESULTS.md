# Phase 7E — test and build results (sequential, this working tree)

| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS 615/0 |
| `:core:iosSimulatorArm64Test` | PASS 613/0 |
| `:server:test` | PASS 571/0, 8 skipped (the Firestore emulator tests, which skip without `FIRESTORE_EMULATOR_HOST`; they passed against the emulator in 7D); new `CuratedDiscoverTest` 5/0, `CuratedDiscoverBenchmarkTest` 1/0; existing `ScreenerRoutesTest`, `ScreenerUniverseFailureTest`, `ScreenerWarmupTest`, `ScreenerUniverseOptimizationTest` pass |
| `:app:shared:testAndroidHostTest` | PASS 284/0 |
| `:app:shared:iosSimulatorArm64Test` | PASS 281/0 |
| `:app:androidApp:assembleDebug` | PASS (with the new search field) |
| `xcodebuild` | PASS (BUILD SUCCEEDED, with the new search field) |
| Devices | NOT RUN / BLOCKED (redeploy approval; FMP 429) |

## Brief items → tests

| Area | Covered by |
|---|---|
| Universe: ~150 unique, valid metadata, sectors, ordering, normalization | `theCuratedListIsAboutOneHundredFiftyValidUniqueCompaniesAcrossAllSectors` |
| Search: exact ticker, prefix, name, case, no results | `discoverWorksWithoutTheScreenerOrQuotes…` ("v", "apple", "brk.b", no match) |
| Filters: sector, market cap, P/E, price, missing metric | same test (P/E reports 0 of 155 evaluated; market cap excludes and counts 10) |
| Sorting: numeric, missing last, stable ties, pagination | same test (155 rows over 8 pages, no duplicates) |
| Providers: 402/429, partial enrichment, missing EOD, no REAL → MOCK | `failuresStayUnavailableAndAreRetriedAfterTheCooldown`, benchmark 402/429 rows; `sampleData = false`, values only from served data |
| Performance: cold, warm, repeated, concurrent, filters, pages | `repeatedAndConcurrentOpensShareTheCaches…`, benchmark |
| Client: shared presentation, labels | builds; the search label is in core `ScreenerDisplay`; no new presenter test (the debounce path is covered indirectly) |
