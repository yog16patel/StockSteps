# ChatGPT review package — App-Wide Typography & Spacing Refinement (Phase 5)

## 1. Phase and Git baseline
- Phase: Typography & Spacing Refinement Phase 5 (app-wide, Android Compose + iOS SwiftUI; targeted design-system refinement, not a redesign).
- Base: `b8321ce` "Normalize inner whitespace in Company Search queries for Phase 4 …" (`main` = `origin/main`).
- Under review: the local commit "Refine app-wide typography and spacing for Phase 5 with section heading, list title and compact value roles, roomier Company Search rows without the Best match badge, readable navigation rows, two-line company names and clearer Key Ratios on Android and iOS" (approved by the owner with D1–D3; not pushed or deployed).

## 2. Changes implemented (both platforms)
- New semantic type roles: section heading 18, primary list line 16, compact value 14 (tabular). Body 15, secondary 14, caption 12 unchanged.
- Company Search rows: names 16 semibold, ticker · exchange 14, 40 dp/pt logos, 12 above/below and 4 between lines, subtle inset dividers;
  "Best match" badge removed (heading kept; still spoken). Android search margins 46 → 16 dp (gesture insets no longer padded). iOS rows own
  their padding and headers share the 16 pt margin. Ranking, sections, requests, caching and navigation unchanged.
- Section headers app-wide 17 → 18 (`StockSectionHeader`).
- Navigation rows app-wide: title 15 → 16, description 12 → 14 (Markets Research tools, Discover More tools, Watchlist Alerts, Learn, Company
  Details "See detailed valuation", Daily Brief entry).
- Company names in shared stock rows wrap to 2 lines by default (Top Movers no longer cut "China SXT Pharmace…").
- Company Details: Key Ratios values 13 → 14 semibold (were smaller than their labels); +8 above Financial Highlights and Key Ratios; "P/E"
  kept whole in the valuation sentence (existing `ValuationDisplay.keepTogether`).

## 3. Affected files
- shared (Compose): `theme/Typography.kt`, `theme/Spacing.kt`, `designsystem/theme/StockStepsTheme.kt`, `designsystem/components/`
  `StockSearchResultRow.kt`, `StockSectionHeader.kt`, `StockNavigationRow.kt`, `StockMetric.kt`, `StockRow.kt`; `presentation/stocksearch/`
  `StockSearchPane.kt`, `StockSearchScreen.kt`; `presentation/companydetails/CompanyDetailsScreen.kt`; test `theme/DesignTokensTest.kt`.
- iOS: `DesignSystem/SearchResultRow.swift`, `DesignSystem/StockComponents.swift`, `DesignSystem/StockDetailComponents.swift`,
  `StockSearchScreen.swift`, `CompanyDetailsScreen.swift`.
- core / server / Android-app module: none.
- docs: `docs/TYPOGRAPHY_SPACING_REFINEMENT_PHASE5.md`, this package, `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`.

## 4. Architectural decisions
- Values live in the shared tokens (`ThemeTypography`, `ThemeSpacing`, `ThemeDimensions`); Compose and SwiftUI read the same numbers. Only new
  roles were added — no existing token value changed — so untouched screens keep their layout.
- Fixes go into the shared component pairs, not per screen. Display only: no copy, data, request or navigation change.
- iOS keeps the plain `List` group gap above a following header instead of Compose's `sectionBreak` (same look, native behaviour).
- `screenTitle` stays 28 (D2). Search row padding 12, not 14–16 (D1). Focused diff: `CHATGPT_FOCUSED_DIFF.md`.

## 5. Test and build results
`:core:jvmTest` PASS 612/0 and `:core:iosSimulatorArm64Test` PASS 610/0 (forced `--rerun`, core unchanged); `:app:shared:testAndroidHostTest`
PASS 284/0 (+2 new); `:app:shared:iosSimulatorArm64Test` PASS 281/0 (+2); `assembleDebug` PASS; `xcodebuild` PASS. Server NOT RUN (no change).
No flakes. See `CHATGPT_TEST_RESULTS.md`.

## 6. Android verification (emulator-5554 Pixel 9 Pro, 427 dp, MOCK, cached signed-in session)
BEFORE/AFTER at 1.0× light: Search (recent, "apple" results, scrolled), Home, Markets (movers, Research tools), Discover (+ results), AAPL
Company Details (Financials, Highlights, Key Ratios), Watchlist, Learn, Portfolio sample — PASS. 2.0×: Search, Learn rows, Watchlist,
Key Ratios — PASS (no clipping/overlap). Dark: NOT RUN (system night mode doesn't switch the app; in-app theme left untouched).
Density: search results visible 12 → 9 (row 53 → 65 dp); nav-row groups +2–9%; Key Ratios unchanged.

## 7. iOS verification (iPhone 16 simulator, guest, MOCK)
BEFORE/AFTER at Default light: Search (recent, "apple"), Home, Markets (movers), MSFT Company Details (Financials, Highlights, Key Ratios),
Learn — PASS. AX5 Search and Dark Search — PASS. "P/E" sentence fixed on the final build — PASS. Density: search results 8 → 9 (row 75 → 64 pt).

## 8. Known issues
- `:8083` MOCK server (another session's) returns 500 for `markets/overview` and `screener/search` (its jars were rebuilt under it). Not
  touched; this phase used a private `:8085` MOCK copy (stopped afterwards) via `adb reverse tcp:8083 tcp:8085` (restored).
- Top Movers names longer than two lines still end in "…" ("Pinnacle Food Group Limited Class A Com…" on iOS) — by design (2-line cap).
- Pre-existing: Understand-row explanations and Discover tile descriptions stay 12 (approved designs; candidates for a later pass).

## 9. Unverified scenarios
- TalkBack / VoiceOver: NOT RUN (owner pass).
- Android dark: NOT RUN. Small phones (≤ 360 dp, iPhone SE): NOT RUN (no small emulator free; the SE simulator can't install the app).
- iOS signed-in (Portfolio, Practice, Watchlist rows): BLOCKED (guest simulator; no credentials entered).
- Search error / no-results / loading AFTER captures: NOT RUN (those states' components weren't changed).
- Practice AFTER capture: NOT RUN.

## 10. Deviations from the brief
- Search row vertical padding 12 instead of ~14–16 (density; 4-pt grid) — D1.
- Screen title kept at 28 instead of 24 — D2.
- Android BEFORE Home shows an empty watchlist (MOCK user data was added afterwards for Watchlist captures).
- BEFORE iOS Company Details opened from Markets, AFTER from Home (Back label differs only).

## 11. Decisions requiring owner approval
- D1 search row padding 12; D2 keep `screenTitle` 28; D3 "Best match" badge removed (requested change to the 5D.5.5D design).
- Approved 2026-10-16 (D1–D3). Push not requested.

## 12. Links
- Report: `docs/TYPOGRAPHY_SPACING_REFINEMENT_PHASE5.md`.
- Contact sheets: `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png` (BEFORE row above AFTER row).
- Originals and logs: `captures/before/`, `captures/after/`, `captures/logs/` (git-ignored).
