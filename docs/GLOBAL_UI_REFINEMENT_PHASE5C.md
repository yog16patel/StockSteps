# Global UI Refinement Phase 5C — Company Comparison

Status: **committed and pushed** as "Refine Company Comparison with word-safe stacked values, honest missing data, compact selection and grouped tools (Global UI Refinement Phase 5C) on Android and iOS" (2026-10-11). Base: `9e764fe` "Close Global UI Refinement Phase 5B with filter-input, range and accessibility
fixes and add the Phase 5C Comparison brief" (pushed). The brief's note that 5B closure fixes were uncommitted was out of date — they are in `9e764fe`;
the working tree held no other changes before this phase. UI/UX only: no endpoint, request, presenter contract, calculation, FX, entitlement, quota,
AI, research or navigation change. Brief: `docs/GLOBAL_UI_REFINEMENT_PHASE5C_BRIEF.md`. Reference image: directional only (not added; the owner's
attachment).

## 1. Before / after (device evidence in `docs/design/phase5c/`)

| Area | Before | After |
|---|---|---|
| Header | Long sentence + in-page sample banner (Android) / caution caption (iOS) | "Compare Companies" + compact **Sample** badge; "See how companies differ across key financial metrics. The numbers don't say which is a better investment." (no-ranking safeguard kept) |
| Selected companies | Name on one line ("Microsoft Cor…" on Android), Replace + Remove text buttons on every row, no logo on iOS | Logo, ticker, two-line name, listing, **Partial data** badge when a company failed; ⋮ = Replace / Remove (Remove in the negative colour); "Add a company" in the card header, hidden at 4 of 4 |
| Fewer than two | Example cards + text "Discover Stocks" | Same examples as grouped `StockNavigationRow`s + primary **Discover Stocks** |
| Learning summary | Plain card | Restrained `surfaceSecondary` card with a lightbulb; text unchanged |
| **Metric values (main defect)** | Always equal columns: 4 companies at 360 dp broke words ("Semiconduct/ors", "Technol/ogy", "Con-/sumer"), truncated industries/periods ("Auto - Manufactur…", "FY2026…"), iOS shrank nothing but cut text at XXXL | Shared **`StockComparisonLayout`**: columns only while every value and every word fits a column; otherwise each metric lists **one line per company** (logo + ticker + value + period), in selection order. Two companies at 360 dp stay side by side; four stack. Never a mid-word break, never a shrunk value |
| Missing values | "N/A ⓘ" (glyph in the text) | "No data" + a real info control ("Market cap unavailable for TSLA. Explain why.") opening the existing metric-specific reason; "None" (no dividend) and genuine zeros unchanged |
| Sticky header | Always pinned, also over chart/history/tools; fixed 72 dp scroll offset | Pinned only around the metric sections and only in column layout (stacked rows name each company); logo + ticker + price (only when available) + Partial badge; Android scroll-to-metric uses the measured header height (0 when stacked) |
| Sections | Flat headings + rows on the page background | Each section is one card (title, note, rows with dividers) |
| Share price chart | Progress bar / section error; iOS segmented picker; change lines as text | Skeleton; full error state + "The rest of the comparison is still available." (chart-only); shared pill selector on iOS too; per-company changes as whole "AAPL +31.27%" pairs that wrap, never split |
| Historical comparison | System chips (iOS shrank text to 0.8×), "N/A ⓘ", tiny StockSteps+ line | "1Y free" badge; shared chips in a fading row (iOS); same `StockComparisonLayout` for the table (stacked lines when needed); "No data"; one PREMIUM "StockSteps+" label, hidden when the server's own access note already says it. Locked items still open the existing preview; nothing is requested to draw them |
| Research / AI entries | Two separate cards with outlined buttons | **More tools**: Research checklist, Ask StockSteps AI (**StockSteps+** PREMIUM badge), Discover Stocks — grouped `StockNavigationRow`s. Same destinations |
| Observations, sources | Unchanged statements | Same statements, theme typography, card spacing |
| iOS add sheet | "No matching companies." flashed while typing | Shown only after the search for the current text finished |
| iOS styling | System fonts, `.bordered` buttons | Theme fonts/colours; `.stockSecondary` in Compare, research and AI screens |

## 2. Shared rules and helpers
- `theme/StockSemanticStyles.kt` — **`StockComparisonLayout.sideBySide(widestToken, companies, availableWidth, gap, accessibilityText)`**: column width
  = (width − gap × (n − 1)) / n; side by side only when the widest unbreakable token fits; always stacked at accessibility sizes or unmeasured width.
  `tokens(text)` splits only at spaces (values never split). Compose measures with `rememberTextMeasurer`, SwiftUI with `uiFont` + `NSString.size`;
  "No data" + info icon is included so a missing value never forces a squeeze. Test: `comparisonColumnsOnlyWhenEveryUnbreakableTokenFits`
  (2/3/4 companies, exact-fit boundary, large text, unmeasured width, token rules).
- core **`screener/ComparisonDisplay.kt`**: header and state copy, `cellText` ("No data" for missing, never "N/A"/0; "None" and zero preserved),
  `cellSpoken` ("Microsoft Corporation, Revenue, FY ended Jun 2025, $281.7B" / "…, unavailable. <reason>"), `rowSpoken` (selection order),
  `missingAction`, `selectionSpoken` (partial data), `layoutTexts` / `historyLayoutTexts`. Tests: `ComparisonDisplayTest` (5: missing vs zero vs None,
  spoken company/metric/period, FX/period details, partial data, Replace keeps position / Remove / 4-company limit).
- `StockNavigationRow` (Compose + SwiftUI): new optional `badge` / `badgeKind` (default none → every existing row unchanged).

## 3. Files changed
- Core: `screener/ComparisonDisplay.kt` (new); test `screener/ComparisonDisplayTest.kt` (new).
- Shared: `theme/StockSemanticStyles.kt`, test `theme/ResponsiveLayoutRulesTest.kt`; `designsystem/components/StockNavigationRow.kt`;
  `presentation/screener/ComparisonScreen.kt` (screen, selection, header, metric rows, chart), `ComparisonHistoryUi.kt` (table layout, badges),
  `GuidedMetricExplanation.kt` (summary card), `ComparisonResearchUi.kt` / `ComparisonAiUi.kt` (entry cards removed — replaced by More tools rows).
- iOS: `ScreenerScenes.swift` (Compare screen, summary card, add sheet), `ComparisonHistoryViews.swift`, `ComparisonAiViews.swift` (entry card removed,
  `.stockSecondary`), `ComparisonResearchViews.swift` (`.stockSecondary`), `DesignSystem/StockComponents.swift` (navigation-row badge).
- Docs: this file, `docs/design/phase5c/` (29 screenshots), handoff/status docs.

## 4. Financial correctness and gating (unchanged)
Values, periods, FX conversions and notes come straight from `ComparisonPresenter` / `MetricFormatter`; only the visible "N/A" string became "No data"
(and spoken "unavailable"). Selection still goes through `SharedComparisonSelection` (Replace keeps the position — tested). History: free 1Y, locked
3Y/5Y and advanced metrics decided by `history.access` from the server; locked chips open the existing upsell. Research checklist and AI assistant open
the same screens; their gating, quotas, grounding and validation weren't touched. No new requests (verified on device: the four-company flow loads as
before; no Gemini/provider calls — local MOCK server only).

## 5. Android / iOS parity
Same hierarchy, copy (shared), metric order, missing-value wording, spoken labels and layout decisions (shared rule). Platform differences: native
menus (DropdownMenu / Menu); iOS add sheet uses `.searchable`, Android a dialog; iOS chart is Swift Charts with a stacked legend fallback, Android
`StockTrendChart`; iOS history chips scroll with the fade, Android wraps them.

## 6. Accessibility
Code level: screen and section headings; selection rows speak name, ticker, listing and partial data, ⋮ labelled "Replace or remove <name>"; every
value names the company, metric and period (stacked) or the row reads all companies in order (columns); missing values have a labelled control;
gains/losses in words; 48 dp / 44 pt targets on info controls, ⋮, Explain, pills; no shrunk text (iOS `minimumScaleFactor` removed).
Verified on Android (uiautomator tree, emulator 360 dp): stacked Market cap row reads "Tesla, Inc., Market cap, Latest quote; in each listing's
currency, unavailable. Market value isn't available right now." and the control "Market cap unavailable for TSLA. Explain why." opens that reason.
**TalkBack: NOT RUN. VoiceOver: NOT RUN** (no speech capture; needs a person listening).

## 7. Device verification (local MOCK server :8081 only; `adb reverse`; iOS built with `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081`, checked in the built Info.plist)
| Check | Android emulator (360 dp override) | iOS iPhone 16 (18.3) |
|---|---|---|
| Fewer than two | PASS | PASS |
| 2 companies, light | PASS (side by side) | PASS (side by side) |
| 4 companies, light | PASS (stacked, no broken words) | PASS (stacked) |
| 4 companies, large text | PASS (1.3×) | PASS (XXXL) |
| Missing metric + explanation | PASS (TSLA market cap, reason sheet) | PASS (history "No data") |
| Sticky header | PASS (2 companies; released after metrics) | PASS (2 companies) |
| Chart / history / More tools | PASS (light) | PASS (dark) |
| Dark mode | NOT RUN (the app follows its own theme setting; changing it would alter user settings) | PASS |
| Compact iOS (SE) | — | NOT RUN |
| Chart error, partial company, premium history unlocked, Research/AI screens | NOT RUN (no fixture/plan state for them) | NOT RUN |
Before captures use the `9e764fe` build on the same devices. Device settings were restored (emulator: no size override, night mode on, font 1.0;
iPhone 16: Light, Large). Incident: on iOS one tap landed on a Markets sample news headline while the list was still scrolling and opened the public
article in Safari; nothing was entered, Safari was closed and the app reopened.

## 8. Tests and builds (this session, actual)
Outputs cleaned, then one sequential command: `./gradlew :core:cleanJvmTest :core:cleanIosSimulatorArm64Test :app:shared:cleanTestAndroidHostTest
:app:shared:cleanIosSimulatorArm64Test :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
:app:androidApp:assembleDebug --continue` → BUILD SUCCESSFUL in 8m 49s; then the default `xcodebuild … CODE_SIGNING_ALLOWED=NO build`.

| Check | Result |
|---|---|
| Core JVM | **461 passed, 0 skipped, 0 failed** (456 + 5 `ComparisonDisplayTest`; `ComparisonInterpretationTest`, `ComparisonResearchTest`, `ComparisonAiTest`, `ComparisonHistoryTest` unchanged and passing) |
| Core iOS simulator | **461 / 0 / 0** |
| Shared Android host | **111 / 0 / 0** (+1 `ResponsiveLayoutRulesTest`) |
| Shared iOS simulator | **107 / 0 / 0** |
| `assembleDebug` | OK |
| iOS `xcodebuild` | **BUILD SUCCEEDED**, no warnings in changed files |
| `:server:test` | NOT RUN (no server change) |
Note: the last iOS polish (value/period type curves, "Add a company" inset, full-width Discover button) is in the built and tested code but was not
re-captured on the device (screenshots predate it).

## 9. Known limitations
- Screen readers not run; iPhone SE, Android dark, chart-error and partial-company states not captured on device.
- Stacked lines make four-company comparisons longer to scroll (by design; the crowded hint still recommends 2–3).
- Android system Back on the Compare screen unchanged; AI/research screens were only restyled (buttons), not redesigned.

## 10. Verdict
**PASS WITH CONDITIONS** — the four-company truncation and mid-word breaks are fixed on both platforms by one shared, tested rule (before/after
captures), missing values are honest and explained, gating and requests unchanged, all suites and both builds pass. Conditions: TalkBack/VoiceOver,
iPhone SE, Android dark and the chart-error / partial-company / unlocked-premium states on device are NOT RUN.

## 11. Next (not started)
Phase 5D candidates (plan step 6): Learn, Daily Market Brief and Earnings — Earnings has the most system-font and `.bordered` debt on iOS
(`EarningsScenes`, `EarningsPremiumViews`) and equal-width tables like Compare had; recommend starting with an Earnings audit. Only after approval.
