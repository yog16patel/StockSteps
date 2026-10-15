# Global UI Refinement Phase 5D.5.4C, stage C1: Financials hierarchy

- **Status:** implemented and verified on Android and iOS against the local MOCK server. Approved; committed locally as "Refine Company Details Financials for Global UI Refinement Phase 5D.5.4C C1 with an Income, Balance Sheet and Cash Flow section bar, one selector row, the revenue and net income chart and key-metric tiles first on Android and iOS" (`33fa94c`, pushed).
- **Baseline:** `038aa33` (5D.5.4B r2, pushed).
- **Plan:** `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4C_PLAN.md`, approved with D-F1 to D-V3 as recommended. C1 implements D-F1, D-F2 and D-F3;
  Valuation (C2) has not started.
- **Review package:** `docs/reviews/phase5d_5_4c1/` (Android and iOS contact sheets; `captures/` is git-ignored).

## 1. Changes (display only)
- **Section bar (D-F1):** a pinned **Income · Balance Sheet · Cash Flow** bar that scrolls within the page.
  - Income = Revenue, Profitability, EPS; Balance Sheet = Financial Health; Cash Flow = Cash Flow, Dividends.
  - The model's section order interleaves the groups (revenue, profitability, cash flow, health, EPS, dividends), so the selected tab is
    the group of the **section nearest the bar**, not a fixed group order.
  - A tab scrolls to its group's first section on the page.
  - Shared rule: `FinancialsDisplay.selectedGroup`.
- **Selectors:** Annual/Quarterly and 3Y/5Y/10Y on one bare row (no card); each on its own row at large text.
- **Overview (D-F2):**
  - "Revenue & Net Income": the Profitability section's existing two-series chart, moved to the top. Revenue's own chart is used when the
    two-series chart is missing.
  - **Key Metrics (FY2025):** borderless tiles (`StockMetricGrid(tiled)`):
    - Revenue "+6.4% vs FY2024" and Net Income "+41.9% vs FY2024", using the model's own change texts; net income reuses the revenue
      comparison period;
    - Gross and Operating Margin with the model's existing descriptions;
    - **no margin change** (D-F3: not in the data).
  - Each tile opens the existing metric sheet (5D.5.3), with Learn only for mapped terms.
- **Sections:** every section keeps its headline, sentence, rows, comparisons, "What is this?" and sheets. Revenue and Profitability no longer
  draw the chart that moved to the top; Cash Flow and EPS keep theirs.
- **Tile caption direction:** new optional `helperDirection` on `StockMetric` / `StockMetricItem` (both platforms, default none). The change
  caption carries the arrow and colour; the value stays neutral, never a green dollar amount.
- **iOS bar chart:** at large sizes the period labels shrink to fit instead of truncating to "F…" (`minimumScaleFactor`). This applies to every
  `StockBarChart` and fixes an existing AX5 defect.

## 2. Files
- **shared:**
  - `presentation/companydetails/FinancialsDisplay.kt` (new): `KeyMetric`, `Group`, `groups`, `anchorSection`, `groupOf`, `overviewChart`,
    `chartInSection`, `keyMetrics`, `keyMetricsTitle`, `selectedGroup`.
  - `presentation/companydetails/CompanyFinancialsScene.kt`: sticky bar, selector row, `FinancialsOverview`, `showChart`.
  - `designsystem/components/StockMetric.kt`: `helperDirection`.
  - Test: `FinancialsDisplayTest` (new, 5 tests).
- **iOS:**
  - `CompanyFinancialsScene.swift`: VStack pinned to the column width; inline and overlay bar; per-section string anchors; selectors; overview.
  - `DesignSystem/StockControls.swift`: `helperDirection`.
  - `DesignSystem/StockDetailComponents.swift`: bar-chart label scaling.
- **Not changed:** `FinancialStatementsPresentation` and every value or calculation; server; Learn mappings; routes; Valuation.

## 3. Verification
### Automated
| Suite / build | Result |
|---|---|
| core JVM / iOS | PASS 541/0 / 541/0 |
| shared Android / iOS | PASS 258/0 / 254/0 (+5 new) before the last fixes; final rerun in §3.1 |
| server | PASS 501/0 |
| `assembleDebug`, `xcodebuild` (iPhone 16 MOCK) | PASS on final code |

### Android: emulator-5554, 360 dp, MOCK, Apple
| Check | Result |
|---|---|
| 1.0×: bar, selector row, chart, four tiles; Revenue/Net Income captions green with arrows, values neutral | PASS |
| Scroll: Income → Cash Flow selected under Cash Flow (after fixing the off-screen ranking, see §4) | PASS |
| Balance Sheet tab → Financial Health | PASS |
| Net Income tile → sheet "Learn more about net income (profit)" | PASS |
| Dark (in-app theme, restored to Light) | PASS |
| 2.0×: single-column tiles, whole labels, bar scrolls | PASS |

### iOS: iPhone 16, MOCK, guest
| Check | Result |
|---|---|
| Default: bar, selectors, chart, tiles; Income → Revenue section; Balance Sheet and Cash Flow select correctly | PASS |
| Net Income tile → sheet + Learn link | PASS |
| Dark | PASS |
| AX5: tiles one column, selectors stacked, chart period labels whole (after the fix) | PASS |
| AX XL | NOT RUN |

### 3.1 Final shared test rerun
`:app:shared:testAndroidHostTest` PASS 258/0 and `:app:shared:iosSimulatorArm64Test` PASS 254/0 on the final C1 code. Devices restored (emulator 1.0×, Light theme,
`adb reverse` removed; simulator Default, light); MOCK server stopped.

## 4. Issues found and fixed during C1
- **iOS:** the Balance Sheet tap highlighted Cash Flow, because the groups interleave on the page. Fixed with the nearest-section rule above.
- **Android:** sections scrolled off the top ranked at -1 and outranked a visible section; they now rank in page order far above it.
- **Tiles:** the change direction coloured the value; it now colours the caption (`helperDirection`).
- **iOS bar chart:** period labels truncated at AX5; they now scale.

## 5. Known issues and limits
- **Pre-existing, outside C1:** at iOS AX5 the Company Details **Financial Highlights** card splits words and values ("Financia l Highligh ts",
  "$416. 2B"). The `StockInfoLine` rows with a change column don't stack. This is planned for 5D.5.4D.
- The iOS live Dynamic Type limit remains.
- TalkBack NOT RUN; VoiceOver BLOCKED; iOS signed-in BLOCKED.
- Quarterly view and the stale/empty states were not exercised on device. Their logic is unchanged; the overview shows nothing when the model
  has no chart or metrics (`FinancialsDisplayTest`).

## 6. Next
C2 Valuation (ratio tiles, P/E vs History, one Learn link, folded explanation, footnote), after owner review of C1.
