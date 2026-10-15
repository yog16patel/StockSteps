# Global UI Refinement Phase 5D.5.4C: Financials and Valuation (audit and plan)

- **Status:** approved by the owner (D-F1 to D-V3 as recommended). C1 Financials is implemented (`docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4C1.md`);
  C2 to C4 are pending. This plan itself changed no application code.
- **Baseline:** `038aa33`, "Refine Company Details for Global UI Refinement Phase 5D.5.4B r2 …" (pushed). 5D.5.4B r2 is committed, so nothing
  mixes with this phase. The working tree was clean; parallel worktrees were not touched.
- **Owner reference:** `docs/design/phase5d_5_4c/REFERENCE-financials-valuation.webp`.
- **Mockups:**
  - `docs/design/phase5d_5_4c/references/` (8 screens, each labelled "MOCKUP — NOT IMPLEMENTED · SAMPLE DATA");
  - contact sheets `CHATGPT_FINANCIALS_REFERENCE.png` and `CHATGPT_VALUATION_REFERENCE.png` in the same folder.
- **Before evidence:** the Financials and Valuation screens have not changed since 5D.5.3 (`git log 3b7a7f5..HEAD` shows no commits to their four
  scene files). The "before" tiles therefore reuse the 5D.5.3 captures (`docs/reviews/phase5d_5_3/captures/`); no new device run was needed.

## A. Current-state inventory (code, both platforms)
Shared model: `core/.../companydetail/FinancialStatementsPresentation.kt` (Financials) and `ValuationPresentation.kt` (Valuation). Screens:
`CompanyFinancialsScene.kt` / `CompanyFinancialsScene.swift` and `CompanyValuationScene.kt` / `CompanyValuationScene.swift`. Both platforms
already render the same sections in the same order; everything is borderless since 5D.5.3.

### Financials
Header: "AAPL · NASDAQ", tagline, "Fiscal year 2025, ended 2025-09-30 · USD". Selectors: Annual/Quarterly and 3Y/5Y/10Y on one surface.

| Section (order) | Data | Current UI | Interaction |
|---|---|---|---|
| Revenue | headline FY value; change vs prior; 5 bars | big number, change, sentence, bar chart, "What is this?" | Learn link (revenue) in the panel |
| Profitability | revenue + net income bars (two series exist in `FinancialChart`); net income + growth; net, operating and gross margin | sentence, two-series chart, 4 `StockInfoRow`s, "What is this?" | rows → explanation sheets (5D.5.3), ⓘ on mapped rows |
| Cash Flow | FCF bars; operating cash flow, capex, FCF | sentence, chart, rows | rows → sheets |
| Financial Health | cash, debt, assets, liabilities, equity; cash-vs-debt and assets-vs-liabilities comparison bars; more rows (current ratio, debt/equity, interest coverage) | rows, proportional bars, Show more | rows → sheets |
| EPS | diluted EPS bars and growth | headline, chart | — |
| Dividends | dividend per share, yield, payout, dividends paid | rows | rows → sheets |
| Summary, table, advanced, valuation rows | summary lines with tone; period table; advanced rows | cards and rows | — |
| States | loading skeleton; error with retry; empty message; stale notice with date | | |

**Not in the data:** year-over-year **margin** changes ("+0.8 pp YoY" in the reference). There is no margin history field, and computing it would
be a new financial calculation.

### Valuation
| Section | Data | Current UI |
|---|---|---|
| Current P/E card | current P/E, badge (Above/Near/Below Historical Average), meaning sentence, average + difference, methodology | big number, badge, sentence |
| P/E trend | `ValuationChart` over the 1Y/3Y/5Y/10Y window, with lowest/average/highest/current and a percentile | pills, line chart, range bar |
| Understanding P/E | explanation; Why high / Why low disclosures; "Learn more about P/E ratio" | card, two disclosures, link |
| Other Valuation Metrics | `otherMetrics` rows: P/S, P/B, EV/EBITDA, PEG, dividend yield | rows with formula captions → sheets (P/S, P/B, EV/EBITDA, PEG: no Learn term; P/E, dividend yield: Learn) |
| Valuation & Growth | `growth` rows + note | rows |
| Insight | `insight` | insight card |

The reference's "10x sales" and "46x book value" captions just restate the value, so the plan keeps the existing formula captions instead.

## B. Ranked UX problems
| ID | Pri | Problem | Evidence |
|---|---|---|---|
| F1 | P0 | Financials opens with a single-series Revenue chart and a long sentence. The key numbers (net income, margins) are a full screen down, and the most useful chart (revenue + net income) is buried in Profitability. | before tiles; `FinancialStatementsPresentation` order |
| F2 | P1 | No overview of the key numbers: each section restates its own headline, so a beginner scrolls ~6 sections to compare revenue, profit and margins. | before tiles |
| F3 | P1 | Six sections with no way to jump between them on a long page. | code |
| V1 | P0 | Valuation leads with P/E only; the other five ratios sit in a plain list at the bottom, so "what is investors' price relative to sales/book/EBITDA" needs a long scroll. | before tiles |
| V2 | P1 | P/E explanation and Why high/low disclosures come before the numbers; the explanation is long for an overview. | before tiles |
| V3 | P2 | Ratios are rows with long captions; there's no grid, unlike At a Glance since 5D.5.4B r2. | before tiles |
| A1 | P1 | At large text the bar-chart axis labels and the selector pills crowd; the selectors take a whole card. | 5D.5.3 large-text captures |

## C. Proposed information hierarchy
**Financials:**
```
Header (symbol · fiscal year · currency)
[Income · Balance Sheet · Cash Flow]   in-page section bar (reuse StockSectionBar, 5D.5.4A) — scrolls, never a new screen
Annual | Quarterly      3Y 5Y 10Y        selectors on one line (stack at large text)
Revenue & Net Income    existing two-series chart moved to the top (Profitability's chart)
Key Metrics (FY2025)    tiles: Revenue (+change vs FY2024), Net Income (+change), Gross Margin, Operating Margin — StockMetricGrid(tiled)
Income:   Revenue / Profitability / EPS sections (existing rows, sentences, "What is this?", ⓘ, sheets)
Balance Sheet: Financial Health (existing rows + comparison bars)
Cash Flow: Cash Flow + Dividends (existing)
Summary · table · advanced (unchanged, below)
```
- The bar chart moves up and the Revenue section's single-series chart is dropped, since the two-series chart already holds the revenue bars.
  Every section keeps its rows and explanations.

**Valuation:**
```
Key Valuation Metrics   tiles (2 columns, 1 at large text): P/E, P/S, P/B, EV/EBITDA, PEG, Dividend yield — value + existing formula caption, ⓘ → existing sheets
P/E vs History          5Y average | current, existing P/E line chart with the average as a dashed line; range pills; badge + meaning sentence
Learn more about P/E ratio  (existing link, once)
Understanding P/E       explanation + Why high / Why low disclosures (folded, unchanged text)
Valuation & Growth · Insight   unchanged
Footnote: "Different ratios measure different things; none is a buy or sell signal on its own."  (D-V2)
```

## D. Visual references
Eight mockups, drawn with StockSteps tokens and the MOCK fixture values the app shows (Apple FY2025):
- Financials: Android light, iPhone light, dark, large text.
- Valuation: Android light, iPhone light, dark, AX5.

Bar heights and the P/E line are illustrative; every tile is marked "MOCKUP — NOT IMPLEMENTED · SAMPLE DATA". Each contact sheet also holds two
"BEFORE" captures of the implemented screens.

**Deviations from the reference image (accuracy):**
- Revenue is shown as $416.2B, the app's MOCK value, not the reference's $383.3B.
- No "pp YoY" margin change (not in the data).
- Formula captions instead of "10x sales".
- The PEG caption is "P/E ÷ expected earnings growth" (existing text).

## E. Border and clutter audit
Already borderless. The proposal reduces:
- repeated per-section headlines (the key metrics move into the tile grid; sections keep rows only);
- the selector card (selectors become a bare row);
- duplicated P/E explanations (the meaning sentence stays in P/E vs History; the long explanation stays folded in Understanding P/E).

## F. Component reuse
| Need | Reuse |
|---|---|
| Section bar | `StockSectionBar` + `CompanyDetailsPresentation.selectedSection` pattern (5D.5.4A) |
| Metric tiles | `StockMetricGrid(tiled = true)` / `StockMetricItem(info)` (5D.5.4B r2) |
| Two-series bars | existing financial bar chart (`FinancialChart` primary + secondary) |
| P/E line | existing `ValuationChart` view |
| Rows and sheets | `StockInfoRow` / `ExplainableInfoLine`, `MetricExplanationSheet` (5D.5.3) |
| Disclosures | `StockShowDetails` / existing Why high / Why low |

**New:** none. A shared helper `FinancialsDisplay.keyMetrics(model)` (display-only selection of existing rows) is the only addition, with tests.

## G. Files likely to change
- **shared:**
  - `presentation/companydetails/CompanyFinancialsScene.kt` and `CompanyValuationScene.kt`;
  - a new display helper `presentation/companydetails/FinancialsDisplay.kt` (key metric selection and section-bar groups), with
    `FinancialsDisplayTest`.
- **iOS:** `CompanyFinancialsScene.swift`, `CompanyValuationScene.swift`.
- **Not changed:** `FinancialStatementsPresentation.kt` and `ValuationPresentation.kt` values and calculations, server, `MetricLearnTerms`,
  the Learn catalogue, routes, Company Details overview.

## H. Accessibility
- **Large text:** tiles go to one column (existing grid rule); selectors stack on two lines; the chart y-axis shows at most three labels in a
  fixed-width gutter; values never split (existing word-joiner and grid rules).
- **Charts:** keep the existing spoken chart descriptions; two-series legends use the existing swatches.
- **Interactions:** tiles and rows are buttons; tabs are announced as tabs; sheets scroll; Back returns to the originating screen.
- **Screen readers:** TalkBack and VoiceOver are tracked separately (NOT RUN / BLOCKED).

## I. Test matrix (5D.5.4C4)
| Area | Android 360 dp | iPhone 16 |
|---|---|---|
| Appearance | light, dark (in-app) | light, dark |
| Text | 1.0×, 1.3×, 2.0× | Default, AX XL, AX5 |
| Financials | Annual/Quarterly, 3Y/5Y/10Y, section bar, tiles → sheets → Learn → Back, stale notice, empty and error | same |
| Valuation | ratio tiles → sheets (mapped vs unmapped), P/E range pills, Learn link → Back, history unavailable | same |
| Data | values identical to before (presentation tests compare the display helper with model rows) | same |
| Tests | core JVM/iOS, shared Android/iOS, server, `assembleDebug`, `xcodebuild` (each run in the background, checked, restarted if stuck > 5 min) | — |

## J. Implementation stages
- **C1 Financials hierarchy:** section bar; selector row; chart moved up; key-metric tiles; sections keep their rows.
- **C2 Valuation hierarchy:** ratio tiles; P/E vs History; one Learn link; folded explanation; footnote.
- **C3 Responsive polish:** large text, dark, parity, chart axis gutter.
- **C4 Verification:** tests, builds, device matrix, review package.

## K. Risks
| Risk | Mitigation |
|---|---|
| Moving the chart hides the single-series revenue chart | the two-series chart includes revenue; the Revenue section keeps its headline and rows |
| Tiles duplicate rows | tiles show the four headline numbers once; their sections keep the detail and the explanations |
| Section-bar anchors on a page with optional sections | reuse the 5D.5.4A rule (only present sections) |
| Ratio tiles imply a judgement | no badges on ratios; only the existing P/E history badge stays |

## L. Owner decisions (recommended option first; proceeding with these unless changed)
- **D-F1:** section bar **Income · Balance Sheet · Cash Flow** scrolling within the page (recommended), or none.
- **D-F2:** **move the revenue + net income chart to the top** and drop the single-series revenue chart (recommended), or keep both.
- **D-F3:** **no margin "pp YoY" change** (not in the data; would be a new calculation). Alternative: a core change computing it from period
  statements (separate approval).
- **D-V1:** **ratio tiles with the existing formula captions** (recommended), or the reference's "10x sales" restatements.
- **D-V2:** **add the neutral footnote** "Different ratios measure different things; none is a buy or sell signal on its own." (new copy;
  recommended), or no footnote.
- **D-V3:** **keep the P/E history badge** (Above / Near / Below Historical Average, existing methodology) and no badges on other ratios
  (recommended).
