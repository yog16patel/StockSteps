# Top Bars, Safe Areas & Accessibility — UI/UX Refinement Phase 2 (KAN-16) — review package

## 1. Phase and Git baseline
- Base: `77a2656` "Refine Home for UI/UX Refinement Phase 1 (KAN-14) …" on `c6c3407` (DEBUG API logging); both pushed, tree clean at start.
- Under review: "Fix iOS landing content under the status bar and shorten company page titles for UI/UX Refinement Phase 2 (KAN-16) with a page-background status strip, ticker Financials and Valuation titles, a symbol Back label from Company Details and trailing Done on iOS sheets". KAN-16 not Done.
- Report: `docs/TOP_BAR_UI_REFINEMENT_PHASE2.md` (inventory, decisions T1–T6, safe-area explanation, verification).

## 2. Changes implemented
- **iOS:** landing pages no longer let content scroll under the status bar. The status-bar inset is filled with the page background on all
  five tabs (one modifier at the tab root). No added padding, no layout shift, native status bar unchanged.
- **Both:** Financials and Valuation titles are "PFAI Financials" / "PFAI Valuation" (ticker) instead of the full company name, which
  truncated to "Pinnacle Food Group Limited Class A C…" and lost the page name.
- **iOS:** pages opened from Company Details show "‹ PFAI" instead of a bare "‹ Back"; Company Details' bar stays title-free.
- **iOS:** Settings and Notifications sheets put Done on the trailing side like every other sheet.
- Unchanged: landing bar components, Android chrome, routing, stacks, cross-tab trail, reselection, Portfolio nested tabs, Home Phase 1.

## 3. Affected files
- iOS: `DesignSystem/StockLandingTopBar.swift`, `AppScene.swift`, `CompanyDetailsScreen.swift`, `CompanyFinancialsScene.swift`,
  `CompanyValuationScene.swift`
- shared: `presentation/companydetails/FinancialsDisplay.kt`, `ValuationDisplay.kt`, `CompanyFinancialsScene.kt`, `CompanyValuationScene.kt`,
  `composeResources/values/strings.xml` (2 unused strings removed); tests `FinancialsDisplayTest.kt`, `ValuationDisplayTest.kt`
- core / server / Android app module: none
- docs: report, this package, focused diff, test results, two contact sheets, `docs/design/top_bar_ui_phase2/`, handoff, status

## 4. Architectural decisions
- Container-level safe-area fix (T1/T2), not per-screen spacers: the root cause is the hidden navigation bar on tab roots.
- Title rule in the shared `*Display` objects (T3), so Compose and SwiftUI cannot drift.
- iOS Back label via the existing Learn pattern (T4); router and `AppDestination.backTitle` untouched.
- No new design-system component; no token changes; component defaults unchanged.

## 5. Test and build results — see `CHATGPT_TEST_RESULTS.md`
core JVM 606/0 and core iOS 604/0 on rerun (first runs each hit the known `PortfolioSessionStateTest` flake, 1 failure);
shared Android host 278/0; shared iOS 275/0; `assembleDebug` PASS; `xcodebuild` PASS.

## 6. Android verification (emulator-5554, Pixel 9 Pro, signed-in test session, MOCK :8083)
- Five landing tabs light and dark, top and scrolled: PASS (consistent, content clipped at the status bar). 2.0×: PASS (title and icons on
  one line, no overlap). PFAI Company Details / Financials / Valuation after: PASS. 0 requests over 6 tab switches.
- Restored: Light theme, font scale 1.0.

## 7. iOS verification (iPhone 16 simulator, guest, MOCK :8083)
- Landing tabs light: before FAIL (Home, Markets, Learn content under the clock) → after PASS; dark PASS. AX5 live change PASS (Learn, Home).
- PFAI Financials / Valuation titles and "‹ PFAI": PASS. Learn Term "‹ Learn": PASS. Settings Done trailing: PASS. Search sheet: PASS.
- Back keeps scroll; swipe-back keeps Company Details' reading position; 5 tab switches keep Home's scroll and make 0 requests; reselect to top.
- Restored: Light, Large text.

## 8. Known issues
- Pre-existing flake `PortfolioSessionStateTest` (core, both targets); fix pending in another worktree.
- Other iOS parents with an empty `navigationTitle` (Portfolio Insights, Discover root) may still give children "‹ Back" (not changed).
- The status strip is opaque without a scroll-edge divider.

## 9. Unverified scenarios
- Android Financials/Valuation BEFORE frames: NOT RUN (install restarted the app mid-capture).
- iOS signed-in and Android guest: BLOCKED (no approved iOS account; no sign-out on the emulator).
- TalkBack / VoiceOver: NOT RUN. Tablet/foldable widths: NOT RUN. iOS dark for every tab: only Learn captured (same modifier on all roots).

## 10. Deviations
- The brief asked for an audit and plan before editing; the owner prefers no approval stop, so the audit (report §2–§3) was written and
  implementation followed immediately.

## 11. Decisions requiring owner approval
- T2: opaque page-background strip (vs. a blurred material). T3: ticker titles ("MSFT Financials" instead of "Microsoft Financials").

## 12. Links
`docs/TOP_BAR_UI_REFINEMENT_PHASE2.md` · `CHATGPT_FOCUSED_DIFF.md` · `CHATGPT_TEST_RESULTS.md` · `CHATGPT_UI_REVIEW_ANDROID.png` ·
`CHATGPT_UI_REVIEW_IOS.png` · `docs/design/top_bar_ui_phase2/` · originals and logs in `captures/` (git-ignored).

**Recommendation: APPROVE WITH FIXES** (owner passes and T2/T3 decisions; no code fixes outstanding).
