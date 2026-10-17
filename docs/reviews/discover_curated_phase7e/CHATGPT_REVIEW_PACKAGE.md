# ChatGPT review package — Phase 7E Discover with a curated universe

## 1. Phase and Git baseline
- **Phase:** 7E, Discover Stocks without the paid screener.
- **Base:** `9b067e2` (Phase 7D, pushed).
- **Under review:** "Make Discover work on REAL for Phase 7E with a versioned curated list of 155 US-listed companies enriched in the background from shared profile and closing-price caches, no FMP screener or per-row quote requests, dated close prices and a find-by-name-or-ticker search on Android and iOS" (committed and pushed; not deployed).

## 2. Changes
- **Universe:** REAL Discover uses a versioned list of 155 US-listed companies (11 sectors). Each is enriched in the background from the
  shared profile and close caches.
- **Not called:** no FMP screener, batch quote or per-row quote.
- **Search:** a "Find a company by name or ticker" field on both apps, ranked by the shared search tiers.
- **Labels:** prices are dated closes, and coverage is reported.

## 3. Files
- **server:**
  - `resources/screener/curated-us-universe.json` (new);
  - `screener/CuratedScreenerUniverse.kt` (new);
  - `screener/ScreenerService.kt`, `repository/models/FmpCompanyProfile.kt`, `Application.kt`, `appconfig/StartupConfiguration.kt`.
- **server tests:** `screener/CuratedDiscoverTest.kt`, `screener/CuratedDiscoverBenchmarkTest.kt` (new).
- **core:** `screener/ScreenerModels.kt`, `screener/ScreenerEngine.kt`, `screener/ScreenerPresentation.kt`, `screener/ScreenerDisplay.kt`,
  `model/CompanyProfile.kt`.
- **Android:** `presentation/screener/ScreenerScreen.kt`, `ScreenerScene.kt`.
- **iOS:** `DiscoverStocksViews.swift`.
- **docs:** `docs/DISCOVER_CURATED_UNIVERSE_PHASE7E.md`, this folder, handoff and status.

## 4. Decisions
- **No blocking:** a response never waits for the provider (background, budgeted, LOW priority).
- **Whole-list evaluation:** filters and sorts run over the whole list (the existing engine); missing values are excluded and counted,
  and sort last.
- **Provider values win:** profile values replace curated metadata once loaded.
- **Additive contracts only.**
- **Legacy screener:** the FMP screener stays behind `SCREENER_UNIVERSE=fmp`.

## 5. Tests and builds
- core 615/0 and 613/0; server 571/0 (8 emulator tests skipped); shared 284/0 and 281/0.
- assembleDebug PASS; xcodebuild PASS.

## 6–7. Android / iOS
- Builds PASS.
- Device and simulator runs NOT RUN; REAL BLOCKED.

## 8. Known issues
- `KNOWN_LIMITATIONS.md`: fundamentals coverage, the market-cap field unverified, no volume, coverage takes hours, quota.

## 9. Unverified
- FMP `profile.marketCap` on this plan.
- The live enrichment rate under the 429 limit.
- The UI on devices.

## 10. Deviations from the brief
- **Industry:** comes from the provider profile, not the curated file (not guessed).
- **Stable identifiers** (CIK/FIGI): omitted.
- **WMT exchange:** left null.

## 11. Owner decisions
- The list.
- The search copy.
- `SCREENER_ENRICHMENT_PER_HOUR`.
- The fundamentals warm-up on REAL.
- The redeploy.
- The licence.

## 12. Links
`CURATED_UNIVERSE.md`, `FILTER_SORT_CONTRACT.md`, `DATA_COVERAGE_AND_FRESHNESS.md`, `API_REQUEST_BENCHMARK.md`, `ANDROID_IOS_VERIFICATION.md`,
`KNOWN_LIMITATIONS.md`, `CHATGPT_FOCUSED_DIFF.md`, `CHATGPT_TEST_RESULTS.md`.
