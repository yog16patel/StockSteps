# Global UI Refinement Phase 5D.5.4C, stage C3: Financials & Valuation polish

- **Status:** implemented and verified on Android and iOS against the local MOCK server. Approved by the owner (Profitability rows kept); committed locally as "Polish Company Details Financials and Valuation for Global UI Refinement Phase 5D.5.4C C3 without the duplicate P/E row, with a measured range grid, row groups that keep values whole and P/E kept whole at large text on Android and iOS" (**not pushed**).
- **Git baseline:** `e8265bd` (C2 Valuation, committed and pushed; `main` = `origin/main`). The tree was clean before C3.
- **Working tree:** only C3 changes (7 code files, this report, `docs/reviews/phase5d_5_4c3/`, and the handoff and status updates).
  Nothing is staged.
- **Review package:** `docs/reviews/phase5d_5_4c3/` (two contact sheets plus `CHATGPT_REVIEW_PACKAGE.md`; `captures/` is git-ignored).
  Unchanged screens are in the C1 (`phase5d_5_4c1`) and C2 (`phase5d_5_4c2`) packages.

## 1. Before → after
| Area | Before (C2) | After (C3) |
|---|---|---|
| Valuation & Growth | P/E Ratio row (third copy of P/E), Revenue Growth, EPS Growth | Revenue Growth, EPS Growth. The section shows exactly when it did before |
| Historical Valuation Range stats | Four equal columns; at Android 2.0× "Averag e", "Highes t" | Measured `StockMetricGrid`: 2 × 2 at normal sizes, aligned label/value rows at large text |
| iOS Valuation & Growth at AX5 | "+75.5" / "%" | Value whole; the label wraps between words, or the row stacks when even its longest word can't fit |
| iOS Financials section rows at AX5 | "Operatin" / "g Cash" beside "$121B" | One decision per section: every value under its label |
| iOS bar-chart labels (Financials) at AX5 | "FY21FY22FY23…" ran together | Small gap per label |
| iOS P/E chart dates at AX XL | "Oct 2021Jun 2023…" ran together | Minimum gap between dates |
| "P/E" in prose at large text (Insight, meaning, methodology, Understanding, reasons, Learn link, chart reference, growth note) | "current P/" + "E" (iOS AX5/AX XL) | Kept whole with invisible word joiners; the visible text is unchanged |

### Duplicate P/E removal
- P/E remains:
  - as the first Key Valuation Metrics tile (with its sheet and Learn link);
  - in P/E vs History (current, average and difference);
  - in the chart and methodology;
  - in the range card.
- The removed row added no information: the same value and the same sheet.
- The Valuation & Growth note ("A higher multiple may reflect…") is kept.
- Rule: `ValuationDisplay.growthRows` drops only the model's `metricId == "pe"` row. `growthVisible` keeps the old rule (more than one model
  row), so availability is unchanged:
  - hidden when there is only P/E, or only one growth figure;
  - two growth rows still show without a current P/E.

### Borders and spacing
- **Already borderless:** both screens already used `StockCard(bordered = false)` / `.stockCard(bordered: false)` at every call site. The Insight
  and Understanding cards draw their border in their fill colour.
- **No outline changes were needed.** Functional lines (chart axes, average line, range bar, selected tab and pill, row dividers) are kept.
- **The range card is shorter at large text:** aligned rows instead of four wrapped columns.

### Financials review
- Order is unchanged and as required:
  1. company and fiscal-period context;
  2. section bar;
  3. period selectors;
  4. chart;
  5. key-metric tiles;
  6. sections with explanations.
- **Duplication found, not changed:** the Profitability rows repeat Net Income, Operating Margin and Gross Margin from the tiles. They add
  descriptions, Net Margin and the explanation sheets, and C1 approved keeping every section's rows. This is an owner decision (§7).

## 2. Files
- **shared:**
  - `presentation/companydetails/ValuationDisplay.kt`: `growthVisible`, `growthRows`; `keepTogether` now takes any text.
  - `presentation/companydetails/CompanyValuationScene.kt`: growth rows without P/E; range stats through `StockMetricGrid(maxColumns = 2,
    rowsWhenNarrow = true)`; `keepTogether` on the model sentences, the Learn link and the chart reference.
  - Tests: `ValuationDisplayTest` (+2 tests: growth rows and visibility; sentences keep "P/E" whole).
- **iOS:**
  - `DesignSystem/StockDetailComponents.swift`:
    - new `StockInfoLineGroup` (one stacking decision per group, by the shared `StockLayout.metricRowStacked`);
    - optional `StockInfoLine.stacked` (default nil = unchanged row);
    - bar-chart label gap;
    - trend-chart date gap.
  - `LearnViews.swift`: `ExplainableInfoLine.stacked` passes through.
  - `CompanyValuationScene.swift`: growth rows through `StockInfoLineGroup`, range through `StockMetricGrid`, `keepTogether` on prose.
  - `CompanyFinancialsScene.swift`: section rows through `StockInfoLineGroup` (left-aligned stack).
- **Reused components:** `StockMetricGrid(rowsWhenNarrow)`, `StockLayout.metricRowStacked`, `StockInfoLine` / `ExplainableInfoLine`,
  `StockCard(bordered = false)`, `ValuationDisplay.keepTogether`.
- **Not changed:**
  - calculations, models, presenters and server;
  - FMP/Finnhub behaviour, methodology, educational text and navigation;
  - global defaults (every new parameter is optional and leaves the default unchanged);
  - Android Financials.

## 3. Automated tests and builds
| Suite / build | Result |
|---|---|
| core JVM / iOS | PASS 541/0 / 541/0 |
| server | PASS 501/0 |
| shared Android host / iOS simulator | PASS 263/0 / 259/0 |
| `:app:androidApp:assembleDebug` | PASS (final code) |
| `xcodebuild` iPhone 16, MOCK | PASS (final code) |

## 4. Android QA: emulator-5554, 360 dp, MOCK, Apple
| Check | Result |
|---|---|
| Financials 1.0× light (context, bar, selectors, chart, tiles, sections; no outlines) | PASS |
| Valuation 1.0× light: tiles, P/E vs History, no P/E in Valuation & Growth, 2 × 2 range, footnote | PASS |
| Valuation 1.3× | PASS |
| Valuation 2.0×: range rows whole, "+75.5%" whole, Insight and footer visible above the Sample banner | PASS (after the range fix) |
| Dark (in-app theme; restored to Light) | PASS |
| Revenue Growth row → sheet → "Learn more about revenue growth" → lesson → Back; scroll position restored | PASS |
| Period switch 5Y → 3Y (chart and range card follow) | PASS |
| Missing data (no reliable average, 10Y) | NOT RUN in C3; C2 PASS, and the logic is unchanged |
| Financials 2.0× / dark | NOT RUN in C3 (no Android Financials code change; C1 PASS) |
| TalkBack | NOT RUN |

## 5. iOS QA: iPhone 16, MOCK, guest
| Check | Result |
|---|---|
| Financials Default light (rows side by side, unchanged) | PASS |
| Valuation Default light: no P/E in Valuation & Growth, 2 × 2 range | PASS |
| Valuation dark | PASS |
| Revenue Growth row → sheet → Learn → lesson → Back; scroll position kept | PASS |
| AX XL: growth rows, chart dates, reference line, Learn link | PASS (after the date-gap and `keepTogether` fixes) |
| AX5 Valuation: growth value whole, range rows, Insight "P/E" whole, footer visible | PASS (after the fixes) |
| AX5 Financials: section rows stack per section, change keeps its arrow and colour, bar labels separated | PASS (after the fixes) |
| Financials dark | NOT RUN in C3 (C1 PASS; only the large-text paths changed) |
| VoiceOver | BLOCKED (needs a device) |

AX sizes were checked by reopening the page after changing the size (known limit on live Dynamic Type changes). Devices were restored:
- emulator 1.0×, Light, `adb reverse` removed;
- simulator Default, light.

The MOCK server is stopped.

## 6. Remaining issues (for 5D.5.4D)
- **iOS AX5 Financials overview legend:** "• Revenue • Net Income" wraps unevenly. This is pre-existing C1 drawing.
- **Company Details Financial Highlights card at iOS AX5:** still splits words. It doesn't use `StockInfoLineGroup` yet.
- **Other screens:** `StockInfoLineGroup` is available for other screens that use `StockInfoLine` with a change column. Adopting it elsewhere is
  out of C3 scope.
- **Narrow P/E chart at iOS AX5:** the y-axis labels are wide. It is readable.
- **Screen readers:** TalkBack NOT RUN; VoiceOver BLOCKED.

## 7. Scope deviations and owner decisions
- **Deviations:** the large-text fixes above go beyond "remove the duplicate P/E". Each fixes a split or overlap the C3 brief forbids. All are
  local to these two screens, or optional and default-off.
- **Owner decision:** the Profitability rows that repeat the Financials key-metric tiles are kept (they add descriptions, Net Margin and the
  explanation sheets).

## 8. Recommended next step
C4 verification:
- a full pass on both screens at 1.0×/1.3×/2.0× and Default/AX XL/AX5, light and dark, including Financials dark and Android Financials 2.0×;
- Quarterly view, 1Y/3Y/10Y and missing-data states;
- TalkBack, when the owner can answer the first-start permission.

Then 5D.5.4D for the shared large-text issues in §6.
