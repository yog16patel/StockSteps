# Review package: Phase 5D.5.6C3 (iOS cross-tab navigation, Back & lifecycle, KAN-10)

## 1. Phase and Git baseline
- **Phase:** 5D.5.6C3 (iOS only) of the approved 5D.5.6 plan. Android C1 is the reference.
- **Base:** `8fce9d1` "Add iOS independent tab stacks for Phase 5D.5.6C2 …" (pushed).
- **Under review:** "Finish iOS cross-tab navigation for Phase 5D.5.6C3 with every tab screen on the router, truthful back labels, reused-top Back, reselection from any depth and no repeated requests on tab switches" (committed and pushed on the owner's approval).

## 2. Changes implemented
1. **All 26 nested `navigationDestination`s** in tab stacks moved to `TabRouter` (28 typed `AppDestination` cases). Modal-local stacks
   (Settings, digest settings, Discover filters) are kept.
2. **Canonical ownership for every migrated route.** Company Details → term/research and Financials/Valuation → term select Learn; News
   insight stays on Markets; Holding/Transactions/Insights stay on Portfolio.
3. **Back and swipe-back** follow the shared trail across tabs. A reused top returns to its origin and keeps the page.
4. **Native back labels** name the real target ("‹ Watchlist", "‹ Home", "‹ AAPL"); deeper hops show the chevron only.
5. **Reselect** pops to the root from any depth (formerly nested screens included).
6. **Lifecycle audit:** `taskOnce` for fetching work; viewed-company recorded once per visit; Practice refreshes when it is the top again.

## 3. Affected files
- **iOS:**
  - `TabRouter.swift`, `AppScene.swift`;
  - Company Details, Financials, Valuation, News, Markets, Learn, Portfolio, Transactions, Screener, Comparison research, Ask AI, Daily Brief,
    Earnings reminders, Practice scenes.
- **Core:** `TabNavigationModelTest.kt` (+1).
- **Android, server, Kotlin production:** none.
- **Docs:** the report, this package, the focused diff, the contact sheet, the plan, the handoff, the status.

## 4. Architectural decisions
- **One navigation system per tab stack:** scenes push only via the router (environment).
- **Shared state through app-level owners:** `CompanyModelStore` per symbol (released when off every stack) and the app's Portfolio, Earnings
  and Screener models.
- **Reused-top Back:** core `back` + undoing the native pop after the tab switch. There is no shared-contract change; `Hop.reused` already
  existed.
- **Native back button kept;** only its title source changes.

## 5. Test and build results
| Suite | Result |
|---|---|
| core JVM | PASS 577/0 |
| core iOS | PASS 577/0 |
| shared iOS | PASS 266/0 |
| Android compile smoke | PASS |
| xcodebuild | PASS |
| Swift router tests | NOT RUN (no iOS test target) |

## 6. Android verification
Not applicable (Android unchanged; compile smoke PASS).

## 7. iOS verification
iPhone 16, guest, MOCK, requests counted.
- **PASS:**
  - A: Watchlist → Apple → P/E term → Back → swipe-back;
  - B: term on Learn;
  - C: research on Learn;
  - D: reselect from Financials and from Learn topic → term;
  - E: reused top;
  - F: modals;
  - G: 0 requests on tab switches;
  - H: light/dark/AX XL.
- **E:** first FAIL (Markets lost the reused page), fixed, then PASS.
- **BLOCKED** (no approved signed-in session): Holding reselect, Watchlist children, Practice, sign-out.
- **NOT RUN:** VoiceOver.

## 8. Known issues
- **Reused-top Back:** the restored page rebuilds its view state (scroll); its data is reused from the shared model.
- **Multi-level pops** stay in the tab.
- **Deeper-hop back button:** chevron only.

## 9. Unverified scenarios
- Signed-in flows (owner).
- VoiceOver.

## 10. Deviations from the approved plan
- QA used a counting proxy on :8082 with a MOCK server on :8083. Port 8081 was held by another process from this checkout (pid 492), which was
  left untouched.

## 11. Decisions requiring owner approval
1. Approve C3.
2. Approve the deeper-hop back button showing only the chevron (vs a custom button that would disable swipe-back).

## 12. Links
- Report: `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6C3.md`.
- Focused diff: `CHATGPT_FOCUSED_DIFF.md`.
- Contact sheet: `CHATGPT_UI_REVIEW_IOS.png`.
- Raw captures: `captures/` (git-ignored).

**Recommendation: APPROVE.**
