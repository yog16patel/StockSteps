# Phase 7A — test and build results (sequential, this working tree)

| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS 614/0 (+1 `PracticeFormatTest.aPreviewWithoutAUsablePriceSaysUnavailable`) |
| `:core:iosSimulatorArm64Test` | PASS on rerun. Full run: 612 tests, 1 failure in `ApiLoggingTest.concurrentRequestsGetDistinctIdsThroughTheBackgroundWriter` (`ConcurrentModificationException`; network logging, untouched by this phase). Rerun of `ApiLoggingTest` alone: 13/0. Recorded as a flaky test |
| `:server:test` | PASS 529/0, 3 skipped (pre-existing Firestore-emulator tests); new `QuoteQuotaProtectionTest` 12/0, `QuoteQuotaBenchmarkTest` 1/0, `PracticeServiceTest` 24/0 (+1) |
| `:app:shared:testAndroidHostTest` | PASS 284/0 |
| `:app:shared:iosSimulatorArm64Test` | PASS 281/0 |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` (iOS simulator, generic, `CODE_SIGNING_ALLOWED=NO`) | PASS (BUILD SUCCEEDED) |
| Android device / emulator | NOT RUN |
| iOS simulator / device | NOT RUN |
| REAL backend (LAN) | NOT RUN (not deployed; deploy needs approval) |

## Brief items → tests

| # | Item | Test |
|---|---|---|
| 1 | Quote 200 keeps its TTL | `successfulQuotesKeepTheirLifetime` |
| 2–3 | 402 / 403 restricted cooldown | `planRestrictedQuotesCoolDownForAnHourKeepTheirTypeAndRecover` |
| 4 | 429 cooldown | `providerRateLimitCoolsDownForTenMinutesAndIsNotADailyLimit` |
| 5 | Confirmed daily cap | `aConfiguredDailyAllowanceStopsUpstreamCallsUntilTheNextBudgetDay` |
| 6 | Unknown 429 isn't a daily cap | `providerRateLimitsNeverSwitchOnTheDailyState` and item 4's test |
| 7 | Recovery after cooldown | items 2–4 |
| 8 | Recovery after daily reset | item 5 |
| 9 | Concurrent identical → 1 call | `concurrentReadsShareOneRequestAndSymbolsStayIsolated`, `concurrentCallersNeverExceedTheDailyAllowance` |
| 10 | Symbols isolated | item 9 |
| 11 | Never a zero price | `aRestrictedQuoteIsNeverAPriceOrNotFound`, `PracticeFormatTest.aPreviewWithoutAUsablePriceSaysUnavailable` |
| 12 | Never SYMBOL_NOT_FOUND | `quoteRouteSaysRestrictedNotNotFoundAndSuccessKeepsItsSchema` |
| 13 | Success schema unchanged | item 12 |
| 14 | Markets reuse | `marketsSectorRefreshesDoNotRepeatRefusedQuotes` |
| 15 | Portfolio / Practice no invented values | `aPlanRestrictedQuoteBlocksTradingWithItsOwnReasonAndIsNeverValued`, `missingPricesAreNeverEstimated` (existing) |
| 16 | No REAL → MOCK fallback | REAL adapters only: failures are thrown typed, never fixture data (items 2, 11); existing `Phase3MockSeparationTest` passes |
