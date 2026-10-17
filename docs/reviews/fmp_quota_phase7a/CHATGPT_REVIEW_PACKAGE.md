# ChatGPT review package — Phase 7A FMP quota protection

## 1. Phase and Git baseline
- **Phase:** 7A, FMP quota protection and restricted-quote error handling.
- **Base commit:** `29c6cae` "Add the Phase 6 REAL market-data provider audit …" (pushed).
- **Under review:** "Protect the FMP quota for Phase 7A with cached restricted and rate-limited quote outcomes, an optional configured daily allowance, distinct restricted and daily-limit errors, a Practice QUOTE_RESTRICTED blocker and no $0.00 price on blocked Practice previews".
- **Not deployed:** the LAN REAL container runs `ff5662e…-dirty`, without `be3c15e` or this phase.

## 2. Changes
- Quote outcomes are cached with the shared `providerCooldown`: 402/403 1 h, 429 10 min, otherwise 30 s.
- `ProviderGuard` takes an optional hard `PROVIDER_<NAME>_DAILY_LIMIT`, separate from the per-minute rate, with UTC budget days.
- Typed errors: `PROVIDER_RESTRICTED` and `PROVIDER_DAILY_LIMIT_REACHED`; HTTP statuses are unchanged.
- The Markets sectors section reports a plan restriction when every fund is refused.
- Practice uses `QUOTE_RESTRICTED`.
- Blocked Practice previews show "Unavailable", not $0.00, on Android and iOS.
- Report: `docs/FMP_QUOTA_PROTECTION_PHASE7A.md`.

## 3. Files
- **server:** `StockProviderException.kt`, `FmpStockProviderRepositoryImpl.kt`, `ProviderBudget.kt`, `SnapshotErrors.kt` (`ProviderErrors`),
  `SparklineService.kt` (`providerCooldown`), `ApiErrorHandling.kt`, `MarketsService.kt`, `AlertEvaluator.kt` (`WatchMarketData`),
  `PracticeService.kt`, `StartupConfiguration.kt`.
- **server tests:** `QuoteQuotaProtectionTest.kt`, `QuoteQuotaBenchmarkTest.kt` (new); `PracticeServiceTest.kt`, `FmpMock.kt`.
- **core:** `PracticePresentation.kt` (`PracticeFormat.previewPrice`), `PracticeFormatTest.kt`.
- **shared:** `PracticeOrderScreen.kt`, `IosPracticeClient.kt`. **iOS:** `PracticeScenes.swift`.
- **docs:** this folder, the report, the handoff and the status.

## 4. Architectural decisions
- No new cache layer: the same `quote:SYM` key holds the outcome.
- The daily cap is counter-based only. A provider 429, or its "Limit Reach" body, is never taken as proof of a daily cap.
- The production limit isn't set: it stays unset until the owner confirms it (see `CONFIGURATION_DECISIONS.md`).
- One error mapping serves routes and section errors.
- Statuses and success schemas are unchanged.

## 5. Tests and builds
- core JVM 614/0; core iOS PASS on rerun (1 flaky logging test, unrelated); server 529/0 (3 skipped); shared Android 284/0; shared iOS
  281/0; assembleDebug PASS; xcodebuild PASS.
- Details: `CHATGPT_TEST_RESULTS.md`.

## 6–7. Android / iOS verification
NOT RUN. The UI change is one formatter call on each Practice order screen; both builds pass.

## 8. Known issues
- The FMP plan still refuses most quotes, the screener and some statements.
- The `ApiLoggingTest` concurrency flake on core iOS.
- `PracticeOrderPreview.price` keeps `"0"` as its no-price placeholder (contract unchanged; display fixed).

## 9. Unverified
- REAL behaviour after deploy: BLOCKED (redeploy approval; the key's daily cap).
- FMP's reset time and whether 402/429 count against its limit: BLOCKED (Q1, Q3).

## 10. Deviations from the brief
- **Added:** the Practice $0.00 display fix, a pre-existing bug found by the new test (core + two call sites per platform).
- **Added:** the Markets sector-section restricted code.
- **Not done:** inferring a daily cap from provider 429 bodies (unreliable evidence, brief item 4.8/4.9).

## 11. Owner decisions
- Copy approval for the three new messages.
- The FMP daily limit and tier (Q1).
- Whether to set `PROVIDER_FMP_DAILY_LIMIT` on LAN REAL.
- The LAN REAL redeploy.

## 12. Links
`docs/FMP_QUOTA_PROTECTION_PHASE7A.md`, `CHATGPT_FOCUSED_DIFF.md`, `CHATGPT_TEST_RESULTS.md`, `API_REQUEST_BENCHMARK.md`,
`CONFIGURATION_DECISIONS.md`; Phase 6: `docs/REAL_MARKET_DATA_PROVIDER_AUDIT_PHASE6.md`.
