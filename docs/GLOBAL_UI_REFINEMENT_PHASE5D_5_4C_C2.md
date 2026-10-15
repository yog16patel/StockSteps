# Global UI Refinement Phase 5D.5.4C, stage C2: Valuation hierarchy

- **Status:** implemented and verified on Android and iOS against the local MOCK server. Approved by the owner; committed locally as "Refine Company Details Valuation for Global UI Refinement Phase 5D.5.4C C2 with ratio tiles first, a P/E vs History card, one Learn link, a folded P/E explanation and a neutral ratio footnote on Android and iOS" (**not pushed**).
- **Baseline:** `33fa94c` (C1 Financials, committed and pushed).
- **Plan:** `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4C_PLAN.md`, approved with D-F1 to D-V3 as recommended. C2 implements D-V1, D-V2 and D-V3.
- **Review package:** `docs/reviews/phase5d_5_4c2/` (two contact sheets plus `CHATGPT_REVIEW_PACKAGE.md`; `captures/` is git-ignored).

## 1. Changes (display only)
The new page order is:
1. **Key Valuation Metrics (D-V1):** borderless tiles (`StockMetricGrid(tiled)`, two columns, one column at large text).
   - P/E first, then the ratios the model has, in its order: P/S, P/B, EV/EBITDA, PEG, Dividend Yield.
   - Values are the model's own text ("N/A" and "No dividend" stay as written); a ratio the model leaves out has no tile.
   - Each caption is the first sentence of the ratio's existing description (P/E has none, so no caption).
   - Each tile opens the existing explanation sheet (5D.5.3); ⓘ only for terms with a lesson.
2. **P/E vs History:** one borderless surface.
   - Current, average and difference, only when the model has a reliable average (`ValuationDisplay.comparison`).
   - The existing badge (amber only when above history), then the meaning sentence.
   - Range pills, the P/E chart with its average line, and the methodology.
   - Without history, the existing message and Try again.
3. **One "Learn more about P/E ratio" link** (D-V3) below the card, replacing the duplicate inside the explanation card.
4. **Understanding P/E Ratio:** folded by default (a toggle with Expanded/Collapsed state).
   - It holds the existing explanation and "Why can P/E be high/low?" unchanged.
5. **Valuation & Growth, Historical Valuation Range and What Does This Valuation Tell Us?:** unchanged.
6. **Footnote (D-V2):** "Different ratios measure different things; none is a buy or sell signal on its own." It sits above the existing
   disclaimer.

**Removed:**
- the separate "Current P/E Ratio" card (its value is the P/E tile, and its comparison is in P/E vs History);
- the "Other Valuation Metrics" rows (now tiles).

### Large-text fixes found during verification
- **iOS badge.** At AX5 "Near Historical Average" was wider than the screen and pushed the whole card past the right edge. Badges never wrap, so
  the badge now uses the regular badge, then the compact badge, then (only if neither fits) the same text and colours wrapping on a rounded
  rectangle. This is local to this screen.
- **Both platforms.** "5-Year Average P/E" broke as "P/" + "E". `ValuationDisplay.keepTogether` puts invisible word joiners around the slash in
  ratio abbreviations (P/E, P/S, P/B). The visible text is unchanged; sheet titles and spoken hints use `ValuationDisplay.plain`.
- **iOS `StockTrendChart`.** At AX5 the date labels truncated to "Oc… Ju…". They now shrink to fit (`minimumScaleFactor(0.4)`), the same fix
  C1 made for `StockBarChart`. Nothing changes when the labels already fit.

## 2. Files
- **shared:**
  - `presentation/companydetails/ValuationDisplay.kt` (new): `KeyMetric`, `keyMetrics`, `Comparison`, `comparison`, `keepTogether`, `plain`,
    the section titles and `FOOTNOTE`.
  - `presentation/companydetails/CompanyValuationScene.kt`: tiles, `HistorySection`, folded `UnderstandingCard`, footnote.
  - Test: `ValuationDisplayTest` (new, 4 tests).
- **iOS:**
  - `CompanyValuationScene.swift`: tiles, `history(...)`, the badge fallback (`wrappingBadge`), `UnderstandingView` (folded), footnote.
  - `DesignSystem/StockDetailComponents.swift`: trend-chart date labels scale.
- **Not changed:**
  - `ValuationPresenter` / `ValuationModel` and every value, average, difference, badge and methodology;
  - server, historical methodology, Learn catalogue and mappings, global theme defaults;
  - Company Details (5D.5.4A/B) and Financials (C1).

## 3. Verification
### Automated
| Suite / build | Result |
|---|---|
| core JVM / iOS | PASS 541/0 / 541/0 |
| shared Android / iOS (first run, before the large-text fixes) | PASS 261/0 / 257/0 |
| shared Android / iOS (final code, incl. the new `keepTogether` test) | PASS 262/0 / 258/0 |
| server | PASS 501/0 |
| `:app:androidApp:assembleDebug`, `xcodebuild` (iPhone 16, MOCK) | PASS on final code |

### Android: emulator-5554, 360 dp, MOCK, Apple
| Check | Result |
|---|---|
| 1.0× first viewport: six tiles, then P/E vs History (current / average / difference, badge, meaning) | PASS |
| P/E vs History: 5Y pills, chart with average line, methodology; one Learn link below | PASS |
| Other metrics: Valuation & Growth, Range, Insight unchanged; footnote above disclaimer | PASS |
| P/E tile → sheet → "Learn more about P/E ratio" → lesson → Back to Valuation | PASS |
| Understanding P/E Ratio folded; opens with explanation and high/low reasons | PASS |
| Scroll restoration: inline Learn link → Back keeps the scroll position | PASS |
| 1.3× (two columns, wrapped captions) | PASS |
| 2.0× (one column, stacked comparison, whole badge, readable chart) | PASS |
| Dark (in-app theme; restored to Light) | PASS |
| Missing data: 10Y has no reliable average → no comparison, badge or average line; the model's reason is shown | PASS |

### iOS: iPhone 16, MOCK, guest
| Check | Result |
|---|---|
| Default first viewport, P/E vs History, chart, Learn link | PASS |
| Other metrics, Insight, footnote | PASS |
| P/E tile → sheet → Learn → lesson → Back | PASS |
| Understanding fold; inline Learn → Back keeps the scroll position and the open fold | PASS |
| Dark | PASS |
| AX XL (reopened at that size) | PASS |
| AX5 (reopened at that size) | PASS after the fixes in §1. Before them the P/E vs History card was wider than the screen |

AX checks reopen the Valuation page after changing the size, because of the known iOS limit on live Dynamic Type changes.
A live switch left some tiles at the old size.

### 3.1 Final shared test rerun
`:app:shared:testAndroidHostTest` PASS 262/0 and `:app:shared:iosSimulatorArm64Test` PASS 258/0 on the final C2 code.

Devices restored:
- emulator 1.0×, Light theme, `adb reverse` removed;
- simulator Default size, light.

The MOCK server is stopped.

## 4. Known issues and limits
- **Pre-existing, outside C2:** at iOS AX5 the unchanged **Valuation & Growth** rows break a value before its unit ("+75.5" / "%"). This is the
  same `StockInfoLine` stacking issue as the Company Details Financial Highlights card and is planned for 5D.5.4D.
- **iOS AX5:** the P/E chart is narrow, because the y-axis labels are wide. It is readable and was the same before C2.
- **Missing data:** the negative-earnings ("N/A") and no-dividend states are unit-tested only; MOCK Apple has both values. The no-reliable-average
  state was verified on device (10Y).
- **Not run:** TalkBack NOT RUN; VoiceOver BLOCKED; iOS signed-in BLOCKED.
- **iOS captures:** the Default, dark and sheet captures were taken before the word-joiner change, which leaves the visible text the same.

## 5. Next
Push on approval, then C3 polish and C4 verification (plan).
