# Phase 7B — test and build results (sequential, this working tree)

| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS 615/0 (+1 `MarketsPresenterTest.endOfDayValuesAreLabelledWithTheirCloseDate`) |
| `:core:iosSimulatorArm64Test` | PASS 613/0 |
| `:server:test` | PASS 544/0, 3 skipped (pre-existing Firestore-emulator tests); new `MarketsEodTest` 14/0, `MarketsEodBenchmarkTest` 1/0; existing `MarketsTest` 9/0, `QuoteQuota*` 13/0 |
| `:app:shared:testAndroidHostTest` | PASS 284/0 |
| `:app:shared:iosSimulatorArm64Test` | PASS 281/0 |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` (iOS simulator, generic) | PASS (BUILD SUCCEEDED) |
| MOCK end to end (local throwaway server :8090, `/api/v1/markets/overview`) | PASS: 11 sectors from fixture closes (`closeDate` 2026-10-07), the TSX index by its labelled proxy, `sampleData=true`, no section errors. Most-active volumes now absent (no quote enrichment) |
| Physical Android / iOS simulator, REAL | BLOCKED: needs an approved LAN REAL redeploy |
| Light / dark theme on devices | NOT RUN (no UI code changed; text only through shared strings) |

## Brief items → tests

| # | Item | Test |
|---|---|---|
| 1 | Two consecutive closes | `twoConsecutiveTradingDayCloses` |
| 2, 4 | Weekend; Monday after Friday | `weekendMondayAndSameDayPartialRows` |
| 3 | Holiday | `holidaysAndEarlyCloses` (Thanksgiving, early close, TSX-only holiday) |
| 5–8 | Missing previous/latest, zero, invalid | `missingZeroInvalidAndDuplicateClosesAreNeverUsed` |
| 9–10 | 402/403, 429 | `allSectorsUnavailableKeepTheProviderReason`, `anIndexWithoutAnyCloseIsUnavailableAndOtherSectionsStay` |
| 11 | Daily-cap denial | `aConfiguredDailyAllowanceIsReportedForTheSection` |
| 12–13 | Stale cache; new close appears | `closesLiveUntilTheNextExpectedCloseAndALateCloseIsRetried`, `partialSectorFailureIsReportedPerSector` (stale fund listed) |
| 14 | Concurrent loads | `concurrentReadsShareOneLoad`; benchmark "20 concurrent" |
| 15–16 | Partial / all sectors unavailable | `partialSectorFailureIsReportedPerSector`, `allSectorsUnavailableKeepTheProviderReason` |
| 17 | Proxy labelled | `indicesUseTheirOwnCloseThenALabelledProxyClose` |
| 18 | Movers without enrichment | `mostActiveRowsKeepTheirOwnFieldsWithoutQuoteEnrichment` |
| 19 | No REAL → MOCK | `sectorsComeFromClosesWithTheirDatesAndNoQuotes` (`sampleData=false`, values only from the served closes) |
| 20 | Display formatting | `MarketsPresenterTest.endOfDayValuesAreLabelledWithTheirCloseDate` (shared by Android and iOS) |
