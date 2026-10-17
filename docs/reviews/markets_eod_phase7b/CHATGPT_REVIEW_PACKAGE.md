# ChatGPT review package — Phase 7B EOD-first Markets

## 1. Phase and Git baseline
- **Phase:** 7B, EOD-first Markets optimization.
- **Base:** `1404b4e` (Phase 7A deployment note).
- **Dependency:** Phase 7A `c57135a` is committed, pushed and deployed on the owner's request; its ChatGPT review is pending.
- **Under review:** "Make Markets EOD-first for Phase 7B with sector and index fall-back changes from consecutive trading-day closes, quotes only while an index market is active, no proxy or most-active enrichment quotes, closes cached until the next expected close and Last close date labels on Android and iOS" (committed and pushed; not deployed).

## 2. Changes
- **Sectors:** computed from each fund's latest completed-session close vs the previous trading day's close, with dates. No more sector
  quotes.
- **Indices:** a quote only while the index's market is active; otherwise, or when the quote fails, the index's own close; then its
  fund's close (labelled proxy); then unavailable.
- **Most active:** no quote enrichment.
- **Daily closes:** live until the next expected close (exchange calendars, a 60-min publication delay), with a 30-min retry while late.
- **Labels:** "Last close · Oct 16" and "Change from the previous close · Last close Oct 16 · Not available: …".

## 3. Files
- **server:**
  - `service/EodCloses.kt` (new);
  - `service/MarketsService.kt`, `service/PriceChartService.kt`, `Application.kt`, `appconfig/StartupConfiguration.kt`.
- **server tests:** `MarketsEodTest.kt`, `MarketsEodBenchmarkTest.kt` (new); `FmpMock.kt` (`bodies`).
- **core:** `model/Markets.kt` (optional fields), `markets/MarketsPresentation.kt`, `MarketsPresenterTest.kt`.
- **Android / iOS:** none.
- **docs:** `docs/MARKETS_EOD_OPTIMIZATION_PHASE7B.md`, this folder, handoff and status.

## 4. Decisions
- **Previous close** = the previous trading day on the listing's calendar (never across a provider gap).
- **No new cache:** the session-aware lifetime sits on the existing `price-chart` cache.
- **Additive JSON:** existing statuses and codes kept; both apps render the shared strings, so no UI code changes.
- **Movers and news** endpoints unchanged.

## 5. Tests and builds
- core JVM 615/0; core iOS 613/0; server 544/0 (3 skipped); shared Android 284/0; shared iOS 281/0.
- assembleDebug PASS; xcodebuild PASS.
- MOCK end-to-end overview PASS.
- Details: `CHATGPT_TEST_RESULTS.md`.

## 6–7. Android / iOS
BLOCKED for REAL (needs a redeploy approval). No app code changed; both builds pass.

## 8. Known issues
- **MOCK most-active rows** lose their volume: it came from enrichment, and the fixture lists have none.
- **Late closes:** after 17:00 ET until the close appears, a late series is retried every 30 min.

## 9. Unverified
- FMP's EOD publication delay and adjustment basis (Q6).
- Whether FMP serves index EOD (`^GSPC`, …) for all four indices on this plan: three worked in the Phase 6 window.
- Display and caching licence (Q4).

## 10. Deviations from the brief
- **Index quotes during active hours:** kept, when the plan serves them, so the Brief's "so far today" stays possible. Removed outside
  active hours.
- **Equal closes on two trading days:** treated as a real 0 %. Only a duplicate date is rejected, the reading of "two identical closes"
  used here.

## 11. Owner decisions
- Copy (`DATA_FRESHNESS_AND_LABELS.md`).
- The publication delay.
- Most active without volume.
- The LAN REAL redeploy for the device pass.

## 12. Links
`docs/MARKETS_EOD_OPTIMIZATION_PHASE7B.md`, `CHATGPT_FOCUSED_DIFF.md`, `CHATGPT_TEST_RESULTS.md`, `API_REQUEST_BENCHMARK.md`,
`DATA_FRESHNESS_AND_LABELS.md`; Phase 7A `docs/FMP_QUOTA_PROTECTION_PHASE7A.md`; Phase 6 audit.
