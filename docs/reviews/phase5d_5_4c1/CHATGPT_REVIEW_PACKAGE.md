# Review package: Phase 5D.5.4C C1 (Financials hierarchy)

## 1. Phase and Git baseline
- **Phase:** 5D.5.4C, stage C1.
- **Base:** `038aa33`.
- **Under review:** the local commit "Refine Company Details Financials for Global UI Refinement Phase 5D.5.4C C1 with an Income, Balance Sheet and Cash Flow section bar, one selector row, the revenue and net income chart and key-metric tiles first on Android and iOS" (not pushed).

## 2. Changes
- **Section bar:** a pinned Income · Balance Sheet · Cash Flow bar (scrolls within the page; the selected tab is the nearest section's group).
- **Selectors:** on one row.
- **Overview:** the revenue + net income chart moved up, plus "Key Metrics (FY2025)" tiles (Revenue, Net Income with their change vs FY2024;
  Gross and Operating Margin).
- **Sections:** keep everything except the moved chart.
- **Caption direction:** the change caption carries the direction (the value stays neutral).
- **iOS bar chart:** labels scale instead of truncating.

## 3. Files
- **shared:** new `FinancialsDisplay.kt` + `FinancialsDisplayTest`; `CompanyFinancialsScene.kt`; `StockMetric.kt` (`helperDirection`).
- **iOS:** `CompanyFinancialsScene.swift`, `StockControls.swift`, `StockDetailComponents.swift`.

## 4. Decisions
- Display only; values and texts come from `FinancialStatementsModel`.
- No margin change (D-F3).
- New component parameters are optional with unchanged defaults.

## 5. Tests
PASS: core 541/0 ×2; shared 258/0 and 254/0; server 501/0; both builds. The final shared rerun is in report §3.1.

## 6. Android
360 dp at 1.0× and 2.0×, light and dark: PASS (bar, scroll selection, tabs, tile → sheet). Sheet: `CHATGPT_UI_REVIEW_ANDROID.png`.

## 7. iOS
iPhone 16 at Default and AX5, light and dark: PASS. Sheet: `CHATGPT_UI_REVIEW_IOS.png`.

## 8. Known issues
- The Company Details Financial Highlights card breaks at iOS AX5 (pre-existing; planned for 5D.5.4D).
- iOS live Dynamic Type limit.

## 9. Unverified
- **NOT RUN:** iOS AX XL; Quarterly view on device.
- **Screen readers:** TalkBack NOT RUN; VoiceOver BLOCKED.

## 10. Deviations
None from the plan. Two scroll-selection bugs were found and fixed during verification (report §4).

## 11. Owner decisions
Approve C1; proceed to C2 Valuation.
