# ChatGPT review package — UI/UX Refinement Phase 8C (My Portfolio, Practice, Watchlist)

## 1. Phase and Git baseline

- **Phase:** 8C of the Phase 8 brief. 8D has not started.
- **Base:** `67d40d7`, "Refine Discover for UI/UX Refinement Phase 8B …". It is committed and pushed; the request said 8B was
  uncommitted, but it wasn't.
- **Under review:** "Refine My Portfolio, Practice and Watchlist for UI/UX Refinement Phase 8C with the summary figures before the account-value history, borderless Practice sections without the repeated cash amount, house iOS buttons and a dated Watchlist header on Android and iOS" (committed and pushed).

## 2. Changes implemented (display only)

- **My Portfolio overview:** total → today's change → Invested cost / Unrealized / Realized P/L → notice and valuation date →
  account-value history. Before, the history sat between today's change and the figures. Android and iOS.
- **Practice:** sections and rows are borderless per call site (summary, Available cash / Open holdings, insights, holdings, allocation,
  activity, challenges). The empty state no longer repeats the cash amount, and the iOS buttons use `.stockSecondary`. The order flow
  (preview, review, result) is unchanged.
- **Watchlist:** the header is dated: "Updated Oct 7, 5:15 PM ET".

## 3. Affected files

| Area | Files |
|---|---|
| core | `watchlist/WatchlistPresentation.kt`, `WatchlistPresenterTest.kt` |
| shared | `presentation/portfolio/PortfolioScreen.kt`, `presentation/practice/PracticeScreens.kt` |
| iOS | `PortfolioScreen.swift`, `PracticeScenes.swift` |
| server | none |
| docs | `docs/UI_UX_REFINEMENT_PHASE8.md` (8C section), this folder, `docs/design/ui_ux_phase8c/`, `docs/PROJECT_HANDOFF.md`, `docs/project-status.md` |

## 4. Architectural decisions

- Borderless surfaces are set at each call site; the global default is unchanged.
- The reorder keeps all text in `PortfolioOverviewDisplay`.
- The dated freshness line lives in the core presenter, which both platforms use.
- A single-holding donut change was **reverted**, because it contradicts the Phase C test. It is now owner decision D8C-3.

## 5. Tests and builds

All five suites and both builds pass (`CHATGPT_TEST_RESULTS.md`):

| Suite | Result |
|---|---|
| core | 618/0 and 616/0 |
| server | 571/0, 8 skipped |
| shared | 289/0 and 286/0 |

The failing run before the revert is recorded there too. Firestore emulator tests: NOT RUN (no server change).

## 6. Android verification

Light, dark and 2.0× BEFORE/AFTER on the MOCK sample scenarios: PASS. Requests: 12 = 12.

## 7. iOS verification

- **Guest views** (Portfolio, Practice, Watchlist) in light, dark and AX5: PASS, unchanged.
- **Signed-in content:** BLOCKED (no credentials on devices); the iOS changes are verified by build only.

## 8. Financial integrity

PASS (`FINANCIAL_INTEGRITY_CHECKLIST.md`). No calculation defect found, and nothing in accounting, execution, idempotency or persistence
changed.

## 9. Known issues / unverified

See `KNOWN_LIMITATIONS.md`. Not verified:

| Item | Status |
|---|---|
| iOS signed-in | BLOCKED |
| Trade preview / result frames | NOT RUN |
| Practice empty state frame | NOT RUN (code-verified) |
| Transaction forms | NOT RUN |
| TalkBack / VoiceOver | NOT RUN |
| Firestore emulator | NOT RUN |

## 10. Deviations from the brief

- The brief lists transaction-entry, trade-preview and trade-result improvements. No P0/P1 was found there, and they are
  financial-flow code, so they were left unchanged.
- The allocation fix was reverted (D8C-3).

## 11. Owner decisions

- **D8C-1:** the overview order.
- **D8C-2:** borderless Practice.
- **D8C-3:** a bar instead of a full ring for a lone 100 % holding.
- **Still open:** the Phase 7E enrichment budget.

## 12. Links

- `UI_AUDIT.md`
- `PORTFOLIO_REVIEW.md`
- `PRACTICE_REVIEW.md`
- `WATCHLIST_REVIEW.md`
- `FINANCIAL_INTEGRITY_CHECKLIST.md`
- `SCREEN_BEFORE_AFTER.md`
- `ANDROID_IOS_PARITY.md`
- `ACCESSIBILITY_CHECKLIST.md`
- `API_REQUEST_REGRESSION.md`
- `KNOWN_LIMITATIONS.md`
- `CHATGPT_FOCUSED_DIFF.md`
- `CHATGPT_TEST_RESULTS.md`
- Contact sheets: `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`
- Downscaled frames: `docs/design/ui_ux_phase8c/`

**Verdict: READY FOR REVIEW**, with signed-in iOS BLOCKED. Owner decisions D8C-1 to D8C-3 are pending.
