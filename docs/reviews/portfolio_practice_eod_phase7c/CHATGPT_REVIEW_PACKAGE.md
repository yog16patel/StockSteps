# ChatGPT review package — Phase 7C Portfolio & Practice EOD

## 1. Phase and Git baseline
- **Phase:** 7C, My Portfolio and Practice EOD valuation and execution.
- **Base:** `b9f871a` (Phase 7B, pushed; deployed to LAN REAL 2026-10-17). Phase 7A `c57135a` is deployed too. Reviews of 7A and 7B are
  pending.
- **Under review:** "Value My Portfolio and execute Practice trades at dated official closing prices for Phase 7C with one latest-close rule, no quotes on REAL, unavailable totals instead of partial sums, split-like move warnings and a closing-price freshness line on Android and iOS" (committed and pushed; not deployed).

## 2. Changes
- **Practice:** executes immediately at the last available official close ("Simulated trade using the last available closing price ·
  date") and values positions at it.
- **My Portfolio:** values holdings at the latest valid close. Its "Prices as of" line becomes "Closing prices as of Oct 16, 2026", and
  "Today's change" says it needs live prices.
- **Neither:** reads a quote any more on REAL.
- **Split-like jumps:** called out on both, never adjusted.
- **Markets sectors:** a 429 + local refusals mix reads as "rate limited".

## 3. Files
- **server:** `service/EodCloses.kt` (`EodPrice`, `isSplitLike`), `practice/PracticeService.kt`, `userdata/PortfolioMarketService.kt`,
  `service/MarketsService.kt`, `Application.kt`.
- **server tests:** `practice/PracticeEodTest.kt`, `userdata/PortfolioEodTest.kt`, `userdata/PortfolioPracticeEodBenchmarkTest.kt` (new);
  `practice/PracticeServiceTest.kt` (`FakeMarket` close failures/counter), `service/MarketsEodTest.kt`.
- **core:** `portfolio/PortfolioModels.kt` (`pricesAsOf`), `portfolio/PortfolioPresenter.kt`.
- **shared:** `presentation/portfolio/PortfolioPresentation.kt` + test.
- **Android / iOS app code:** none.
- **docs:** `docs/PORTFOLIO_PRACTICE_EOD_PHASE7C.md`, this folder, handoff and status.

## 4. Decisions
- One close rule (`EodPrice.latest`) with per-use windows: valuation ≤ 5 sessions; execution ≤ 1 session for a late publication.
- Existing blocker codes are reused (`QUOTE_STALE` / `QUOTE_MISSING` / `QUOTE_RESTRICTED`), so the apps need no change.
- Engines, documents, idempotency and authentication are untouched.
- MOCK keeps fixture quotes.
- The "Today" change isn't shown from closes.

## 5. Tests and builds
- core 615/0 and 613/0; server 556/0 (3 skipped); shared 284/0 and 281/0.
- assembleDebug PASS; xcodebuild PASS.

## 6–7. Android / iOS
BLOCKED (redeploy approval; the FMP key is rate-limited; the owner's test account). No app code changed.

## 8. Known issues
- **FMP limit:** the key is still at its limit (live 7B check: 429 everywhere), so REAL EOD values are unverified live.
- **Corporate actions:** not applied (`CORPORATE_ACTIONS_GAPS.md`).
- **Mixed FX dates:** Portfolio reporting FX is the latest publication while prices are the latest close (existing rule).

## 9. Unverified
- FMP EOD adjustment basis and publication delay (Q6).
- The display and caching licence (Q4).
- Firestore emulator and device passes.

## 10. Deviations from the brief
- **Daily change:** "Today's change" is kept unavailable on closes, instead of relabelling the hero line (a UI change outside scope).
- **Error codes:** the brief's `EOD_PRICE_STALE` / `EOD_PRICE_UNAVAILABLE` are covered by the existing equivalent codes, per the brief's
  "reuse if equivalent".

## 11. Owner decisions
- The windows (5 sessions for valuation, 1 for execution).
- The copy.
- A "Last session" line in a later UI phase.
- The redeploy.
- A licensed corporate-actions source (separate phase).

## 12. Links
- `docs/PORTFOLIO_PRACTICE_EOD_PHASE7C.md`.
- This folder: `VALUATION_AND_EXECUTION_RULES.md`, `CORPORATE_ACTIONS_GAPS.md`, `FIRESTORE_SECURITY_AND_PERSISTENCE.md`,
  `API_REQUEST_BENCHMARK.md`, `CHATGPT_FOCUSED_DIFF.md`, `CHATGPT_TEST_RESULTS.md`.
