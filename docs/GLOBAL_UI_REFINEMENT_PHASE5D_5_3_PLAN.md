# Global UI Refinement Phase 5D.5.3 — Contextual learning in Company Details: audit and plan

Status: **READ-ONLY AUDIT AND PLAN. No application code changed.** Waiting for the owner's approval.
- Date: 2026-10-15. Base: `main` at `600c295`; the working tree was clean.
- Phase 5D.5.2 Learn is **closed and pushed** (`057d6e6`, `0730773`, closure `600c295`).
- Owner reference: `docs/design/phase5d_5_3/REFERENCE-contextual-learning.webp` (illustrative).
- Proposed mockups: `docs/design/phase5d_5_3/references/`; contact sheet: `docs/reviews/phase5d_5_3/CHATGPT_UI_REVIEW_PROPOSED.png`.
- The mockups are annotated drawings rendered locally, **not app screenshots**. Their values are sample numbers.
- Unrelated work is not touched: worktree `charming-hodgkin-98e850` (Portfolio test fix).

---

## A. Current architecture (verified in code)

### A.1 Two metric vocabularies already exist
| Source | What it is | Ids |
|---|---|---|
| `core/.../companydetail/MetricEducation.kt` | Every financial metric the app shows. Each has a label, a one-line explanation, `calculation`, `why`. Used by Financials, Valuation, Screener, Comparison and Comparison AI | ~50 ids: `marketCap`, `pe`, `forwardPe`, `eps`, `revenue`, `revenueGrowth`, `netMargin`, `roe`, `debtEquity`, `freeCashFlow`, `dividendYield`, `yearRangePosition`… |
| `core/.../learning/BeginnerEducation.kt` | The Learn catalogue: 14 `EducationEntry` items with stable ids (`pe`, `eps`, …). It **already has `relatedMetrics`**, a list of `MetricEducation` ids per term, and reuses `MetricEducation`'s "what it is" text | 14 terms |

`relatedMetrics` is an existing canonical link from Learn terms to metric ids. It is the basis of the mapping, so there is no title-string matching.

### A.2 Company Details (the live screens)
| Screen | Android | iOS | Metrics shown | Existing education |
|---|---|---|---|---|
| Overview → **At a Glance** | `presentation/companydetails/CompanyDetailsScreen.kt` `GlanceSection` (`StockMetricGrid`, 2 columns, 1 at large text) | `CompanyDetailsScreen.swift` glance `StockMetricGrid` | Market Cap, P/E Ratio, Revenue Growth, Dividend Yield (`CompanyOverview.glance`, each with an `educationId`) | **Tap the tile** → `EducationSheet` / `InfoSheet` with a **company-specific sentence** (`edu_*` string resources: "A P/E of 29.4 means investors pay about $29 for every $1 Apple earned…"). **No link to Learn** |
| Overview → assessment and valuation cards | same file (`ValuationHealthSection`, `UnderstandSection`) | same | Assessment rows; valuation summary | "Understand" opens the same P/E sheet |
| **Financials** (pushed) | `companydetails/CompanyFinancialsScene.kt` (`StockInfoRow`, tiles) | `CompanyFinancialsView.swift` | revenue, revenueGrowth, netIncome, eps, epsGrowth, grossMargin, operatingMargin, netMargin, roe, roic, cash, debt, debtEquity, currentRatio, interestCoverage, operatingCashFlow, capex, freeCashFlow, fcfMargin, dividendYield, payoutRatio | **None** (rows are not tappable) |
| **Valuation** (pushed) | `companydetails/CompanyValuationScene.kt` | `CompanyValuationScene.swift` | pe, forwardPe, peg, priceSales, priceBook, priceFcf, evEbitda (+ history) | **None** |
| Ranges | `StockRangeBar` (52-week, day) | same | 52-week range | None |

Not displayed anywhere in Company Details: **Beta**.

An older, unused screen (`presentation/companydetail/CompanyDetailScreen.kt` with `MetricInfoBottomSheet`) is only reached from the stock-search quote pane. It is **out of scope**.

### A.3 Learn navigation
- **Android:** type-safe `LearnTermRoute(id)` already exists (5D.5.2). `navController.navigate(LearnTermRoute(id))` from `CompanyDetailsRoute` pushes the
  term page; **Back pops to the same Company Details entry**, and its `LazyListState` (saved in the back-stack entry) keeps the scroll position.
  While the term is on top, the bottom bar highlights the Learn tab (`AppNavigation` treats `LearnTermRoute` as Learn).
- **iOS:**
  - `AppScene` owns one `NavigationStack`. Company Details is `navigationDestination(item: $detailsSymbol)` and Learn pages are
    `navigationDestination(item: $learnTarget)` on the same stack.
  - Setting `learnTarget = .term(id)` from Company Details pushes the term **on top of the stock**. Back returns to it with its scroll state
    (SwiftUI keeps the pushed view alive).
  - The term page's own "Next term" pushes are nested inside `LearnTermView` (no duplicate root).
- **Unknown or unavailable ids:** `BeginnerEducation.entry(id)` returns null. `LearnTermScreen` and `LearnTermView` render nothing for an unknown id
  (it never happens today, because ids come from the catalogue).

## B. Metric → Learn term inventory
| Metric (id) | Where shown | Learn term (id) | Mapping | Content |
|---|---|---|---|---|
| Market cap (`marketCap`) | Glance | What is market capitalization? (`marketCap`) | ✅ exact | complete |
| P/E (`pe`) | Glance, Valuation | What is the P/E ratio? (`pe`) | ✅ exact | complete, with example |
| EPS (`eps`) | Financials | What is EPS? (`eps`) | ✅ exact | complete |
| Revenue (`revenue`) | Financials | What is revenue? (`revenue`) | ✅ exact | complete, with example |
| Revenue growth (`revenueGrowth`) | Glance, Financials | What is revenue growth? (`revenueGrowth`) | ✅ exact | complete, with example |
| Net income (`netIncome`) | Financials | What is net income (profit)? (`netIncome`) | ✅ exact | complete (no example) |
| Net margin (`netMargin`) | Financials | What is profit margin? (`netMargin`) | ✅ exact | complete, with example |
| Free cash flow (`freeCashFlow`) | Financials | What is free cash flow? (`freeCashFlow`) | ✅ exact | complete |
| Total debt (`debt`) | Financials | What is debt? (`debt`) | ✅ exact | complete |
| Cash (`cash`) | Financials | What is cash? (`cash`) | ⚠️ **ambiguous**: `debt.relatedMetrics` also lists `cash` | Resolve to the exact id `cash` (rule below) |
| Debt / equity (`debtEquity`) | Financials | What is debt-to-equity? (`debtEquity`) | ✅ exact | complete |
| Dividend yield (`dividendYield`) | Glance, Financials | What is dividend yield? (`dividendYield`) | ✅ exact | complete |
| Forward P/E, PEG, P/S, P/B, P/FCF, EV/EBITDA | Valuation | — | ❌ no term | Keep the existing `MetricEducation` text only |
| EPS growth, gross/operating margin, ROE, ROIC, current ratio, interest coverage, operating cash flow, capex, FCF margin, payout ratio | Financials | — | ❌ no term | Same |
| 52-week range (`yearRangePosition`) | Range bar | — | ❌ no Learn term (only `MetricEducation`) | Same |
| Beta | not displayed | — | n/a | n/a |
| (`netDebt`) | listed in `debt.relatedMetrics` but not a displayed id | — | ignored | — |

**Coverage:**
- **12 displayed metrics have an existing Learn term** (12 terms; `earningsBeat` and `diversification` are not Company Details metrics).
- **All 4 At-a-Glance metrics are covered.**
- 9 of the 12 illustrative metrics in the brief are covered. Not covered: 52-week range and ROE (no term), Beta (not shown).

## C. UX: inline disclosure vs Learn page
| | Option A — inline expansion in the metric grid | Option B — straight to the Learn page | **Recommended hybrid: existing sheet + "Learn more"** |
|---|---|---|---|
| Taps to understand | 1 | 1 (but leaves the stock) | 1 for the short answer; 2 for the lesson |
| Beginner comprehension | short definition only | full lesson, but loses the company's number | the company's **own value in a sentence** (existing `edu_*` text) + the full lesson one tap away |
| Complexity | high: a 2-column grid must reflow around an expanded tile (reference #2) on both platforms | low | low: the sheet already exists on both platforms; add one button and navigation |
| Accessibility | expanding tiles in a grid moves focus unpredictably | good | sheet is modal and focus-trapped; the button is a 48 dp/pt primary action |
| Parity | layout differences between Compose FlowRow and SwiftUI grids | good | both platforms already use a sheet (reference #7 shows exactly this on iOS) |
| Navigation reliability | n/a | needs the routing in §E | same as B, only after an explicit "Learn more" |
| Clutter | high (big expanded card in the grid) | none | none |

**Recommendation:** the hybrid. Keep the existing tap-to-explain sheet, add a primary **"Learn more about {term}"** button when the metric maps to
a term, and extend tap-to-explain to the mapped metrics on Financials and Valuation (sheet with the `MetricEducation` explanation + Learn more).
Metrics without a term keep a sheet with their `MetricEducation` text and no button: nothing is invented.

## D. Screen references (proposed; `docs/design/phase5d_5_3/references/`)
1. `ref1-company-details-light.png`: Key metrics with an ⓘ on each tile (owner reference #1). See decision D-C2 on the ⓘ glyph.
2. `ref2-explanation-sheet-light.png`: tap → sheet with the company sentence, the term's first sentence and **Learn more about P/E ratio**
   (reference #2 / #7).
3. `ref3-learn-term-light.png`: the existing Learn term page pushed on top. The back label names the stock (`‹ AAPL` on iOS; ← on Android).
4. `ref4-large-text-light.png`: large text, one metric per row, ⓘ after the label, chevron, whole row is the target (reference #9).
5. `ref5-company-details-dark.png`: dark theme (reference #5 / #10).

The mockups follow the owner's layout ("Financials" tab, 8 key-metric tiles). **Today's app differs:**
- Overview shows 4 At-a-Glance tiles.
- Financials and Valuation are separate pushed screens, not tabs.

Re-laying out Company Details to the reference's tab and 8-tile layout would be a Company Details redesign (decision D-C1). The plan applies the
interaction to the current layout.

## E. Shared architecture
### E.1 Mapping (core, new, display-only): `learning/MetricLearnTerms.kt`
```kotlin
object MetricLearnTerms {
    /** The Learn term for a displayed metric id, or null. Exact catalogue id first, then a unique relatedMetrics owner; never title matching. */
    fun termFor(metricId: String): EducationEntry?
    fun hasTerm(metricId: String): Boolean
    fun learnMoreLabel(entry: EducationEntry): String   // "Learn more about P/E ratio" (LearnDisplay.termName)
}
```
Rules:
1. If `BeginnerEducation.entry(metricId)` exists, use it (this resolves `cash` → `cash`, not `debt`).
2. Otherwise, use the single entry whose `relatedMetrics` contains the id.
3. Otherwise null. Two or more owners → null (ambiguity is never guessed).

Tests: every Company Details metric id resolves as in §B; `cash` → `cash`; unknown and no-term ids → null; every result exists in the
catalogue; a stability snapshot of the full mapping; no mapping depends on titles.

### E.2 Presentation
- `CompanyOverview.GlanceMetric.educationId` stays. The sheet adds `MetricLearnTerms.termFor(id)`.
- Financials and Valuation rows get an optional tap (`StockInfoRow(onClick =)`). Whether that parameter already exists needs checking in
  implementation; if not, add an optional parameter with an unchanged default on both platforms.
- Sheet copy: the existing `edu_*` resources for the 4 glance metrics; `MetricEducation.explanation` for the others. **No new educational text.**

### E.3 Navigation
- **Android:** `CompanyDetailsScene` / `CompanyFinancialsScene` / `CompanyValuationScene` get `onLearnTerm: (String) -> Unit`. `AppNavigation` wires
  it to `navController.navigate(LearnTermRoute(id))`. Back pops to the stock with its scroll position.
- **iOS:** pass `onLearnTerm` down from `AppScene`, setting `learnTarget = .term(id)` on the same stack, so Back returns to the stock. Pushed Financials
  and Valuation pages also need the closure.
- **Not needed:** new external deep links, new routes, a second Learn instance.
- **Out of scope:** the reference #4 "Returned to Apple" toast (decision D-C4).

## F. Exact affected files
- **core:** new `learning/MetricLearnTerms.kt`, new test `learning/MetricLearnTermsTest.kt`.
- **shared Compose:**
  - `presentation/companydetails/CompanyDetailsScreen.kt` (EducationSheet + Learn more);
  - `CompanyDetailsScene.kt` and `CompanyDetailsAction.kt` (a `LearnTerm(id)` action);
  - `CompanyFinancialsScene.kt` and `CompanyValuationScene.kt` (tappable mapped rows + sheet);
  - `presentation/AppNavigation.kt` (wire `LearnTermRoute`);
  - possibly `designsystem/components/StockInfoRow` (optional `onClick`, default unchanged).
- **iOS:**
  - `CompanyDetailsScreen.swift` (`InfoSheet` + Learn more);
  - `CompanyDetailsScene.swift`, `CompanyFinancialsView.swift`, `CompanyValuationScene.swift`;
  - `AppScene.swift` (`learnTarget` from the stock);
  - possibly `IosCompanyDetailsClient` / `IosLearningClient.kt` (exposing the mapping).
- **Untouched:** `MetricEducation` and `BeginnerEducation` text, the server, calculations, formatting, Learn screens, search, Daily Brief, Portfolio,
  Screener.

## G. Implementation phases
| Phase | Scope |
|---|---|
| **5D.5.3A** | `MetricLearnTerms` + tests (core only) |
| **5D.5.3B + C** (together) | Glance sheet "Learn more" + navigation on both platforms, then tap-to-explain sheets on the mapped Financials and Valuation rows. B and C merge because the navigation is one small wiring per platform |
| **5D.5.3D** | Suites, builds, device QA (local MOCK): Android 1.0×/1.3×/2.0× light and dark; iOS Default/AX XL/AX5 light and dark; Back returns to the same stock and scroll position; review package |

## H. Risks and mitigation
| Risk | Mitigation |
|---|---|
| Financial accuracy | Values and formatting untouched. Sheet sentences are the existing ones. Mapping is by id with tests; no-term metrics show no Learn button |
| Wrong term | Exact-id-first rule; the ambiguity test (`cash`); snapshot test of the whole map |
| Routing / back stack | Use the existing Learn routes on the same stack (Android `LearnTermRoute`, iOS `learnTarget`); device-test Back from term → stock and from Next-term chains |
| Scroll restoration | Android: back-stack-saved `LazyListState`. iOS: the pushed view stays alive. Verify after Learn → Next → Back ×2 |
| Learn tab highlight while reading a term from a stock (Android) | Accepted: the reference shows Learn highlighted. Alternative (keep the current tab) is decision D-C3 |
| Accessibility | Sheet button 48 dp/pt with the label "Learn more about P/E ratio". Tappable rows announce "Explain {metric}". No colour-only meaning; large-text rows stack (existing `StockMetricGrid` / `StockInfoRow` rules) |
| Parity | Same mapping object; same sheet structure; device checks on both |

## I. Testing strategy
- **Core:** the mapping tests in §E.1.
- **Shared / iOS suites and builds:** run one after another.
- **Android and iOS device checks:**
  - glance tile → sheet → Learn more → correct term → Back → same stock and scroll;
  - Financials and Valuation mapped rows → sheet → Learn more;
  - no-term rows → sheet without a button;
  - Next term → Back → Back;
  - guest and signed in (iOS signed-in stays **BLOCKED** until the owner signs in).
- **Regression:** Company Details values and format unchanged; Learn search and topics unchanged; no progress changes (term pages don't write
  progress); offline (glance sheet text is local; Learn content is local).

## J. Future integration opportunities (not in 5D.5.3)
- **Screener:** the metrics help sheet (`MetricsInfoSheet`) could add Learn more for mapped ids.
- **Comparison:** row info already opens `MetricFormatter.education`; add Learn more.
- **Markets:** an index explainer → a future "What is a market index?" term (would need new content).
- **Portfolio:** P/L and allocation terms (new content).
- **Watchlist:** quote rows have no metrics today.
- **Daily Brief:** already links Concept of the day to Learn.
- **AI Comparison Assistant:** answers already cite `MetricEducation`; it could link mapped terms.

## K. Owner decisions
| ID | Decision | Recommendation |
|---|---|---|
| D-C1 | Keep the current Company Details layout (4 glance tiles; Financials and Valuation as pushed screens) vs re-layout to the reference's tabs and 8 tiles | **Keep the layout**; apply the interaction. A re-layout is a separate Company Details phase |
| D-C2 | Visible ⓘ on each tile vs the whole tile tappable with no glyph (today) | **Add a small ⓘ beside mapped metric labels only**, with the whole tile still the 48 dp target. This matches the reference and signals that help exists |
| D-C3 | Bottom tab while a term opened from a stock is shown (Android) | **Accept Learn highlighted** (reference #3); the back stack still returns to the stock |
| D-C4 | "Returned to Apple" toast on return (reference #4) | **Skip**: Back already returns to the same place; a toast adds noise |
| D-C5 | Interaction pattern | **Hybrid:** existing sheet + Learn more (§C) |
| D-C6 | Metrics without a Learn term (ROE, forward P/E, …) | **Sheet with the existing `MetricEducation` text, no Learn button**; new lessons are a separate content phase |
