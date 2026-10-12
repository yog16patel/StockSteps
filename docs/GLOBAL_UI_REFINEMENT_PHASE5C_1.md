# Global UI Refinement Phase 5C.1 — Company Comparison polish (+ final polish pass)

Status: **committed and pushed** as "Polish Company Comparison with collapsible sections, short periods, comparison bars, concise notes and one info action (Global UI Refinement Phase 5C.1) on Android and iOS" (2026-10-11), on top of `ab004df` (Phase 5C, pushed). Two owner briefs in one pass: 5C.1 (collapsible sections,
compact metrics, comparison bars, compact selection) and the "final UI polish" follow-up (concise notes and insights, one info action, scannable bar
rows). UI only: no API, request, calculation, entitlement or navigation change; no new dependency.

## 1. Changes (Android + iOS, shared rules in core `ComparisonDisplay`)
| Area | Before (5C) | After |
|---|---|---|
| Sections | Always open | Collapsible cards (48 dp / 44 pt header with title, "N metrics", chevron; spoken "Expanded/Collapsed"). Growth and Profitability open by default; Overview, Financial Health, Valuation, Shareholder Returns, the "More metrics" sections and Historical comparison start collapsed. State kept across recompositions/company changes (Android `rememberSaveable`, iOS `@State`). "Explain this" on a metric in a closed section opens the section first |
| Periods | Long ("Trailing twelve months", "FY ended Sep 2025", "Q3 FY2026 vs Q3 FY2025") on every row | Short visible forms ("TTM", "FY Sep 2025", "Q3 FY26 vs Q3 FY25", "Periods differ"); a period shared by every row is shown once under the section title. Spoken labels and info sheets keep the full text. Unknown texts and "≈ US$… converted" are never altered |
| Notes | Section note + bar note always visible | One "What to keep in mind" control per section (lightbulb, chevron, 48 dp / 44 pt) reveals the same notes |
| Comparison bars | — | Percentage metrics only (shared `ComparisonDisplay.bars`): bar = share of the longest absolute value in that row; negative values keep "−" and the negative colour; a genuine 0 has a zero-length bar; missing = "No data" + reason control, no bar; money amounts never get bars (no cross-currency drawing). Note: "Bar length shows the size of each value, not which company is better." |
| Bar rows | Ticker · squeezed bar · value/period in one row | Ticker and short period left, value right (strong), full-width bar underneath — nothing competes for width at 4 companies, 1.3× or XXXL |
| Info actions | "ⓘ Explain" text buttons on guided metrics, ⓘ icons elsewhere | One ⓘ icon action per metric (48 dp / 44 pt): guided metrics toggle the inline explanation (filled icon + "Expanded" when open), others open the info sheet; labels "Explain …" / "Hide explanation of …" / "About …" |
| Insights | All insights + footer always shown | First insight, then "Show all N insights" (footer appears with the rest); "Explain this" links kept |
| Selection | Ticker, name, listing per row | Ticker + two-line name (listing stays in the spoken label and Overview → Listing); 48 dp rows |
| Collapsed cards (iOS) | `.stockCard` 16 pt padding around a 44 pt header | 8 pt vertical padding; the header keeps its 44 pt target |
| iOS XXXL | Period drawn larger than its value (`.caption2` grows 11→18.7 pt, `.footnote` 13→17.3 pt — measured on the simulator) | Value and period both follow the `.footnote` curve; the value is always the larger |

## 2. Quarterly vs annual revenue growth (owner item 7)
Verified independent fields: `quarterRevenueGrowth` comes from Earnings Results year-over-year (`ScreenerService.quarterRevenueGrowth`, basis
"quarter", e.g. AAPL 6.44501279, 2026-06-30, "Q3 FY2026 vs Q3 FY2025"); `revenueGrowth` from the fundamentals mapper (basis "annual", AAPL 6.445,
FY ended 2025-09-30). The identical displayed values are a **MOCK fixture artifact**: in `fixtures/earnings/events.json` AAPL Q3 FY2026 revenue
110,756,035,806 / Q3 FY2025 104,050,000,000 = +6.445%, exactly the annual rate (the generator scaled quarters by the annual growth). Values unchanged.
Follow-up (not done, fixtures out of scope): regenerate earnings fixtures with independent quarterly growth.

## 3. Files changed
Core: `screener/ComparisonDisplay.kt` (sections, short periods, bars, notes, insights, info labels, `ComparisonBar`), `screener/ScreenerPresentation.kt`
(`MetricCell.value`, set by the formatter for percentages only); test `screener/ComparisonDisplayTest.kt` (+4: defaults/summary, short periods,
bars, notes/insights/info labels; 1 expectation updated to the short period).
Android: `presentation/screener/ComparisonScreen.kt`, `ComparisonHistoryUi.kt` (collapsible header), `GuidedMetricExplanation.kt` (insights preview).
iOS: `ScreenerScenes.swift`, `ComparisonHistoryViews.swift`.
Docs: this file; screenshots `docs/design/phase5c_1/` (5C.1 pass) and `docs/design/phase5c_1/final/` (final polish).

## 4. Tests and builds (actual)
Outputs cleaned, one sequential Gradle command (`:core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
:app:androidApp:assembleDebug --continue`) → BUILD SUCCESSFUL in 8m 58s; then the default `xcodebuild … CODE_SIGNING_ALLOWED=NO build`.

| Check | Result |
|---|---|
| Core JVM | **465 passed, 0 skipped, 0 failed** (461 + 4 new `ComparisonDisplayTest`) |
| Core iOS simulator | **465 / 0 / 0** |
| Shared Android host | **111 / 0 / 0** |
| Shared iOS simulator | **107 / 0 / 0** |
| `assembleDebug` | OK |
| iOS `xcodebuild` | **BUILD SUCCEEDED**, no warnings in changed files |

## 5. Screenshots (local MOCK :8081 only; iOS built with the localhost URL and checked in Info.plist)
`docs/design/phase5c_1/`: Android 360 dp 1.0× two companies growth bars, Overview collapsed / expanded, four-company compact selection; Android
1.3× Overview expanded and growth bars; iOS iPhone 16 light (Overview collapsed, Growth bars), iOS XXXL dark (bars + collapsed sections), and the
iOS XXXL defect before its fix (`BEFORE-FIX-ios-iphone16-xxxl-dark-period-larger-than-value.jpg`).
`docs/design/phase5c_1/final/`: Android 1.0× selection, insights preview + notes collapsed, four-company bar rows; Android 1.3× bar rows + notes
expanded; iOS light bar rows + compact collapsed cards; iOS XXXL bar rows.
Devices restored: emulator size/font/theme unchanged from before (no override, 1.0, night mode on); iPhone 16 Light / Large; port reverse removed;
MOCK server stopped.

## 6. Accessibility
Code: section headers are buttons with heading + Expanded/Collapsed state; notes control and insights toggle state their state; one labelled info
action per metric; bar rows speak "Company, metric, full period, value" (bars are decorative); targets 48 dp / 44 pt. **TalkBack and VoiceOver: NOT RUN.**

## 7. Outstanding
- Screen readers, iPhone SE, Android dark (app-level theme) NOT RUN; four-company iOS and iOS dark not re-captured after the final pass.
- On Android the open section header + "What to keep in mind" row still leaves ~60 dp before the first metric (48 dp targets); acceptable, could tighten.
- Sticky company header stays in column mode even though bar rows name each company (kept for non-bar rows such as P/E).
- MOCK earnings fixtures make quarterly and annual revenue growth identical (§2).

## 8. Verdict
**PASS WITH CONDITIONS** — all 5C.1 and final-polish items implemented on both platforms with shared, tested rules; device captures at 360 dp 1.0×/1.3×
and iPhone 16 Large/XXXL show no overlap, truncation or overflow; all suites and both builds pass. Conditions: screen readers, iPhone SE and Android
dark NOT RUN; quarterly/annual identical values are a MOCK fixture artifact (documented, not changed).
