# ChatGPT review package — UI/UX Refinement Phase 8A (Home and Markets)

## 1. Phase and Git baseline
- Phase 8A of the Phase 8 UI/UX brief: Home and Markets only. 8B not started.
- Base: `ddb6dfb` "Make Discover work on REAL for Phase 7E …". Under review: "Refine Home and Markets for UI/UX Refinement Phase 8A with open borderless Markets sections, a one-line market status, the Daily Brief entry in the indices group, diverging sector bars, readable Top Movers at large iOS text and the Portfolio overview wording on Home on Android and iOS" (committed and pushed).

## 2. Changes implemented (Android + iOS)
- **Markets:**
  - the 7 bordered section cards are now open sections: the title sits on the page and the rows sit in one borderless surface group
    (the Home KAN-14 pattern);
  - sections are 20 apart (new token `pageSectionGap`).
- **Markets status:**
  - an open block: "US Market · After-hours", the Sample badge, the next open and "Sample quotes · Not live";
  - no separate card or heading.
- **Daily Market Brief entry:**
  - closes the Major indices group;
  - it keeps its own group when the overview failed.
- **Sector Performance:** diverging bars around a centre line (gains right, losses left).
- **Top Movers (iOS, accessibility sizes):** the price and change go under the name. The symbol and name were squeezed to one letter
  per line before.
- **Home:**
  - the "What matters to you today" subtitle is removed;
  - the portfolio summary uses the Portfolio overview's wording: currency on Invested and P/L, notice codes in words, one spoken
    summary, "Total value unavailable".

## 3. Affected files
- **shared:**
  - `theme/Spacing.kt`, `designsystem/theme/StockStepsTheme.kt` (token);
  - `presentation/markets/MarketsScene.kt`, `MarketsScreenPresentation.kt`;
  - `presentation/home/HomeScreen.kt`, `HomeComponents.kt`, `HomeDashboardPresentation.kt`;
  - tests: `DesignTokensTest`, `MarketsScreenPresentationTest`, `HomeDashboardPresentationTest`.
- **iOS:** `MarketsScene.swift`, `HomeScreen.swift`, `HomeComponents.swift`, `DesignSystem/StockComponents.swift` (`StockRow.stackTrailing`).
- **core / server / Android-only:** none.
- **docs:**
  - `docs/UI_UX_REFINEMENT_PHASE8.md` and this folder;
  - `docs/design/ui_ux_phase8a/`;
  - `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`.

## 4. Architectural decisions
- Borderless sections are set per call site by reusing `HomeSection` / `HomeRowGroup`. The global `StockCard` default is unchanged.
- New wording and geometry live in shared display objects that both platforms call:
  - `statusHeadline`;
  - `sectorBar`;
  - `portfolioOverview` → `PortfolioOverviewDisplay`.
- The iOS-only large-text fix is an optional `StockRow` parameter (default off), so other screens are unaffected.

## 5. Test and build results
See `CHATGPT_TEST_RESULTS.md`.

## 6. Android verification
- Device: emulator-5554 (Pixel 9 Pro, 412 dp) on local MOCK `:8087`.
- Light, dark and 2.0× BEFORE/AFTER: **PASS**.
- Requests are identical BEFORE/AFTER: 12 (`API_REQUEST_REGRESSION.md`).

## 7. iOS verification
- Device: iPhone 16 (iOS 18.3), guest, local MOCK `:8087`.
- Light, dark and AX5 BEFORE/AFTER: **PASS** (AX5 movers fixed).

## 8. Known issues
- Diverging bars are half as long as before; small moves are short stubs (that is the data's ratio).
- The legacy iOS Discover `MarketMoversView` prints "Unavailable%". It is out of scope and listed as a follow-up.
- The `:8081` / `:8083` MOCK servers answer 500 for `markets/overview` (pre-existing, left untouched).

## 9. Unverified scenarios
- TalkBack / VoiceOver: NOT RUN.
- Home portfolio summary with an owned account on a device: NOT RUN (unit-tested only; the Android account has no portfolio and iOS is
  guest).
- Loading and error states on a device: NOT RUN.
- Small phones and tablets: NOT RUN.
- REAL: NOT RUN (FMP 429; MOCK only by rule).
- iOS runtime request count: NOT RUN (code shows no client change).

## 10. Deviations from the brief
None.
- Shared-component impact: the iOS `StockRow` gained an optional parameter, and `HomeSection` / `HomeRowGroup` are reused on Markets
  unchanged.
- On Android the sector name weight stays 0.45, so long names don't wrap.

## 11. Decisions requiring owner approval
- **D8A-1:** drop the Home subtitle; "US Market" is no longer a separate heading.
- **D8A-2:** diverging sector bars.
- **D8A-3:** the brief entry inside the Major indices group.

## 12. Links
- Phase report: `docs/UI_UX_REFINEMENT_PHASE8.md`.
- This folder:
  - `UI_AUDIT.md`;
  - `DESIGN_TOKENS.md`;
  - `SCREEN_BEFORE_AFTER.md`;
  - `ANDROID_IOS_PARITY.md`;
  - `ACCESSIBILITY_CHECKLIST.md`;
  - `API_REQUEST_REGRESSION.md`;
  - `CHATGPT_FOCUSED_DIFF.md`;
  - `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`.
- Frames: `docs/design/ui_ux_phase8a/`.

**Verdict: READY FOR REVIEW.** All 5 suites and both builds pass (`CHATGPT_TEST_RESULTS.md`).
