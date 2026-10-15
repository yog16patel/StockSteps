# Global UI Refinement Phase 5D.5.4: Company Details UI/UX refinement (audit and plan)

- **Status:** approved by the owner (5D.5.4A first, D-E1 to D-B1 as recommended, borderless at every Company Details call site). 5D.5.4A is
  implemented: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4A.md`. This plan itself changed no application code.
- **Baseline:** `73a6c23` "Record the closure of Global UI Refinement Phase 5D.5.3 …" (pushed). Phase 5D.5.3 is fully committed, so nothing mixes
  with this phase. The working tree was clean; the Portfolio worktree `charming-hodgkin-98e850` was not touched.
- **Evidence:**
  - Current screens were captured against the local MOCK server (Apple, fixture Oct 7): Android emulator-5554 at 360 dp, 1.0× and 2.0×; iPhone 16
    simulator at Default and AX5, guest mode.
  - Originals are in the session scratchpad; the representative ones are in the before/after reference (ref6).
  - The emulator and simulator were restored afterwards and the MOCK server was stopped.
- **Owner input during the audit:** "remove borders as we did in previous screens". Every Company Details section becomes borderless at its call
  site, as in Daily Brief 5D.5.1c, Earnings 5D.4.2 and Portfolio (§E). The global card default is unchanged.
- **References:** owner's reference `docs/design/phase5d_5_4/REFERENCE-company-details.webp`; proposed mockups
  `docs/design/phase5d_5_4/references/`; contact sheet `docs/reviews/phase5d_5_4/CHATGPT_COMPANY_DETAILS_REFERENCES.png`.

## A. Current-state inventory (from the code, both platforms)

Company Details is **one vertical page**, with no tabs. Android: `CompanyDetailsScreen.kt` (LazyColumn); iOS: `CompanyDetailsScreen.swift`
(ScrollView + LazyVStack). The iOS screen mirrors Android section by section. Data comes from core `CompanyOverview` and the
`CompanyDetailsPresentation` helpers; each section loads and fails on its own (`Section.Loading/Unavailable/Content`).

| # | Section (current order) | Shows | Components | Interactions |
|---|---|---|---|---|
| 0 | Top bar | Back; watchlist star (when enabled) | `StockStepsTopBar` / toolbar | Toggle watchlist ("Add to/Remove from watchlist") |
| 1 | Header | Logo, name (heading), "AAPL • NASDAQ", **tags** (sector, industry, size), price, market-status chip, signed change + session label ("Today" / date), "Updated …" | `StockTickerAvatar`, `StockTag`, `MarketStatusIndicator`, `StockPriceChange` | One spoken sentence for price block |
| 1b | Header actions | **Add to portfolio** (primary) + **Compare**, **Practice Buy** full width, **Earnings** (only when no Earnings section) | `StockButton`, `StockActionLayout.sideBySide` | Navigate |
| 2 | Chart (bordered card) | Range pills 1D 1W 1M 3M 1Y 5Y ALL, change over range, line chart with scrub, y/x labels; quick stats **Open, High, Low, Volume** (Market Cap and P/E filtered out) | `StockPillSelector`, `StockLineChart`, `StockInfoRow(compact)` | Range select, scrub; loading skeleton; notice with Try again / "no history" |
| 3 | Why Did It Move? | Source-backed summary, "Keep in mind" caution, "3 sources" pill, "See full breakdown" | education surface (borderless) | Sources sheet, open article, Stock Movement screen |
| 4 | At a Glance (bordered card) | 4 tiles: Market Cap, P/E Ratio, Revenue Growth, Dividend Yield (value + helper, ⓘ from 5D.5.3), **52-Week Range** and **Day Range** bars | `StockMetricGrid`, `StockMetricItem(info)`, `StockRangeBar` | Tile → explanation sheet + "Learn more about {term}" |
| 5 | Understand {Company} | Assessment rows Growth / Profitability / Financial Health / Valuation with badge + sentence (bordered card); Beginner Insight + "Why does this matter?" (evidence sheet); **About** card (description + See more, country, Sector, Industry, context sentence; bordered); **Understand This Stock** (Guided Research) card | `StockAssessmentRow`, `StockInsightCard`, `StockSectionHeader`, `StockInfoRow`, `UnderstandStockCard` | Valuation row → P/E sheet; evidence sheet; See more; Start learning |
| 6 | Valuation & financial health | Valuation card: Current P/E / 5Y Average / Difference, headline (skipped if Understand says it), explanation, rows "Understand P/E Ratio", "See detailed valuation" (bordered); **Financial Highlights** Revenue, Net Income, Net Margin, Free Cash Flow with change + basis caption, "See Financials" (bordered); **Key Ratios** P/S, EPS, Gross/Operating Margin, Debt to Equity, Current Ratio (bordered; not tappable) | `StockMetricGrid`, `StockNavigationRow`, `StockInfoRow` | P/E sheet; Valuation screen; Financials screen |
| 7 | Earnings (bordered) | Next earnings date/timing/status, Remind, latest results title + summary, **View Results**, AI Earnings Insight text + StockSteps+ badge, **Explain With AI**, "Sample earnings data.", Earnings calendar row, Earnings history row | `ReminderButton`, `StockButton`, `PlusBadge`, `StockNavigationRow` | Reminder; Results; calendar; history |
| 8 | Recent News (bordered) | Up to N articles (thumbnail, title, publisher, time), "View All" | `StockNewsCard`, skeleton, error, empty | Open article; Company News screen |
| 9 | Disclaimer | "Educational information, not a recommendation…" | caption | — |

**Destinations:**
- Company Financials (`CompanyFinancialsScene`): Annual/Quarterly + 3Y/5Y/10Y; sections Revenue, Profitability, Cash Flow, Financial Health, EPS,
  Dividends, each with a chart, rows and a "What is this?" panel; summary; table; advanced; valuation rows. All borderless since 5D.5.3.
- Company Valuation (`CompanyValuationScene`): current P/E card, historical P/E trend 1Y/3Y/5Y/10Y, Understanding P/E (+ Learn link), Other
  Valuation Metrics, Valuation & Growth, P/E range (lowest/average/highest/current), insight. Borderless.
- Company News (`CompanyNewsScene`, filters, movement preview), Stock Movement (`StockMovementScene`), News insight (`NewsInsightScene`).
- Earnings history and results; Guided Research; Practice Buy; Compare; Add to portfolio; Learn term pages (5D.5.3).

**Not in the data (so not proposed):**
- industry or sector P/E averages (the reference's "Industry 24");
- employee count;
- an EPS estimate on Company Details;
- intraday extended-hours prices beyond the existing status chip;
- new chart intervals or technical indicators.

**Data note (out of scope):** the MOCK fixture shows Market Cap $127.2B next to a $336.67 price. This is fixture data, not presentation; fixture
refresh is out of scope (Global UI plan §4).

## B. Ranked UX findings

| ID | Pri | Finding | Evidence |
|---|---|---|---|
| F1 | P0 | **iOS AX5: chart stats split words and financial values.** "Op/en", "Hig/h", "$33/3.74"; the x-axis dates truncate ("Se… Oc…"). The two side-by-side `StockInfoLine` columns never stack. | iOS AX5 capture; `CompanyDetailsScreen.swift` `chartSection` |
| F2 | P0 | **First viewport is mostly actions.** Three full-width buttons (Add to portfolio, Compare, Practice Buy, plus Earnings when shown) sit between price and chart. At a Glance starts on the 2nd screen at 1.0× and the 3rd at 2.0×. Q1 "what does it do" is answered only in About, about 5 screens down. | Android 1.0×/2.0× captures; `CompanyActions` |
| F3 | P0 | **iOS AX5: the header tag "Consumer Electroni…" is truncated** (data text clipped). | iOS AX5 capture |
| F4 | P1 | **Borders everywhere on the main page** (the owner's request): chart, At a Glance, Understand rows, About, Valuation, Highlights, Key Ratios, Earnings, News and error cards are all outlined, unlike the borderless Financials, Valuation, Daily Brief and Earnings screens. | §E call-site list |
| F5 | P1 | **Repeated values.** P/E appears in At a Glance, the Understand Valuation row and the Valuation card. Revenue growth appears in Glance, the Understand Growth row, Highlights and the insight. Net margin appears in Understand and Highlights. Sector and industry appear in the header tags and in About. Day High/Low appear in the chart stats and the Day Range bar. | `CompanyOverview` builders; captures |
| F6 | P1 | **Earnings card overload:** nine elements (date, timing, status, Remind, results, View Results, AI text + badge, Explain With AI, two navigation rows). | Android capture screen 5 |
| F7 | P1 | **Learning entry points are scattered.** "Understand This Stock" (Guided Research) sits between About and Valuation; Practice Buy sits in the header. | captures |
| F8 | P1 | **Key Ratios rows have no contextual Learn** (5D.5.3 follow-up): EPS and Debt to Equity have Learn terms, and P/S, Gross/Operating Margin and Current Ratio have explanations. | `ValuationHealthSection` |
| F9 | P1 | **No way to jump between sections** on a long page (≈9 screens at 1.0×). The reference shows an Overview · Financials · News · About bar. | reference image; captures |
| F10 | P2 | Why Did It Move? is long (summary + caution + pill + link) directly under the chart, which pushes At a Glance down. | captures |
| F11 | P2 | Chart range row at 2.0×/AX5 scrolls; "ALL" is off-screen without a fade cue on Android. | 2.0× capture |
| F12 | P2 | News is last and has no publication-time grouping. The card is bordered, and thumbnails vary in size. | captures |
| F13 | P2 | The Financials "What is this?" link sits below each chart; the reference uses an ⓘ by the title. Keep the link (Learn integration lives there since 5D.5.3). | Financials capture |

## C. Proposed information hierarchy

```
CURRENT (1.0×, screens)                         PROPOSED
1  identity + tags + price + 3 actions          1  identity (no tags) + price + session + Updated
   chart card (+Open/High/Low/Volume)              chart (borderless) + ranges + Open · Volume
2  Why Did It Move? (long)                         Add to portfolio · Compare (one row; stack only when it can't fit)
   At a Glance (4 tiles + 2 range bars)            [Overview · Financials · News · About] in-page section bar (D-E1)
3  Understand: assessment, insight              2  What Apple does (first sentence + Sector · Industry, › opens About)
4  About, Understand This Stock                    At a Glance (4 tiles + 52-week range; day range → chart stats)
5  Valuation & health, Highlights, Key Ratios   3  Understand Apple (assessment + insight; Valuation row keeps P/E sheet)
6  Earnings (9 elements)                           Why Did It Move? (compact: summary, sources, breakdown)
7  News, disclaimer                             4  Valuation & financial health (P/E vs average once; Highlights; Key Ratios with ⓘ)
                                                5  Earnings (compact: next date + Remind, latest results + View Results; calendar/history rows)
                                                6  News (3 items, View All) → About (full) → Research further (Guided Research, Practice Buy) → disclaimer
```

## D. Visual references (PROPOSED mockups, sample data)
All are drawn locally with the StockSteps tokens (#1683FF primary, #07111C / #0D1B2A dark, #F7FAFD light). They are marked
"PROPOSED · SAMPLE DATA" and use the MOCK fixture values and real section names.

1. `ref1-overview-light-android-360.png`:
   - identity without tags;
   - price with "After-hours" and "Updated …";
   - a borderless chart with the existing seven ranges;
   - Open · Volume;
   - one action row;
   - the section bar;
   - "What Apple does";
   - At a Glance tiles (ⓘ after the label, as 5D.5.3 requires) and the 52-week range;
   - Understand Apple with badges.
2. `ref2-overview-dark-iphone16.png`: the same hierarchy on dark surfaces (#0D1B2A on #07111C).
3. `ref3-financials-light-android-360.png`:
   - period and range pills on one line;
   - a metric switcher (Revenue · Net Income · Free Cash Flow) over the existing bar chart (D-F1);
   - plain-English sentence;
   - Profitability rows with ⓘ only on mapped metrics.
4. `ref4-valuation-light-iphone16.png`:
   - current P/E with the **historical** lowest / 5Y average / highest range bar moved into the top card (not "Industry", which has no data);
   - "Learn more about P/E ratio";
   - Other Valuation Metrics with ⓘ and formula captions.
   - The rows open sheets, so no chevrons (a chevron implies navigation).
5. `ref5-large-text-android-2x.png`: single-column tiles, label above value for price details, and no clipped or split values (fixes F1/F3).
6. `ref6-before-after-android-360.png`: current first two viewports next to the proposed overview.

Deviations from the owner's reference, each kept for accuracy or consistency:
- no "Industry" P/E;
- no Employees row;
- ⓘ after the label, not at the tile corner (5D.5.3 D-C2);
- the price, values and change are the MOCK fixture's, not the reference's $173.22;
- the reference's share icon is omitted because no share action exists;
- the Apple logo comes from the existing `logoUrl` avatar.

## E. Border and spacing audit (call sites; global `StockCard` default unchanged)

| Section | Android (`CompanyDetailsScreen.kt`) | iOS (`CompanyDetailsScreen.swift`) | Change |
|---|---|---|---|
| Header error | `StockCard` l.176 | `.stockCard()` l.180 | `bordered = false` |
| Chart | `StockCard` l.267 | `.stockCard(padding:)` l.326 | borderless surface; stats outside the card (F5) |
| Why unavailable | `StockCard` l.115 | `.stockCard()` l.116 | borderless |
| At a Glance | `StockCard` l.372 | l.407 | tiles on surface fill, no outer outline; range bar on its own surface |
| Understand rows | `StockCard` l.408 | l.423 | borderless; keep the inner dividers (grouping) |
| About | `StockCard` l.444 | l.453 | borderless; moves down (§C) |
| Valuation card | `StockCard` l.480 | l.484 | borderless |
| Financial Highlights | `StockCard` l.508 | l.497 | borderless |
| Key Ratios | `StockCard` l.524 | l.507 | borderless; rows get explain/ⓘ (F8) |
| News | `StockCard` l.542 | l.528 | borderless |
| Earnings | `StockCard` l.565 | l.100 | borderless; compact (F6) |
| `UnderstandStockCard` (Guided Research) | `GuidedResearchScreens.kt` l.350 | `ResearchViews` | caller passes borderless; keep its education tint |

**Spacing:**
- Keep `spacing.sectionGap` between sections and `spacing.sm` inside groups.
- Remove the extra `spacing.md` above the chart, since the action row sits below it now.
- Section headers once; no "Valuation" header repeated inside the "Valuation & financial health" section (use the metric row only).

**Borders kept:**
- the selected range pill (selection);
- focus and pressed states;
- the reminder button outline (`OUTLINED` variant, meaningful as a toggle).

## F. Component reuse
| Need | Existing component (reuse) | Addition |
|---|---|---|
| Borderless sections | `StockCard(bordered = false)` / `.stockCard(bordered: false)` | none |
| Tiles with ⓘ | `StockMetricGrid` / `StockMetricItem(info)` / `StockMetricLabel` (5D.5.3) | none |
| Explainable rows | `StockInfoRow(onClick, onClickLabel, info)` / `ExplainableInfoLine` + `MetricExplanationSheet` (5D.5.3) | none |
| Section bar | `StockPillSelector` (selection semantics) or `StockFadingScrollRow` | **`StockSectionBar`** (only if D-E1 = B): text tabs + indicator that scroll to anchors; one shared rule object in `StockSemanticStyles` |
| What it does | `StockNavigationRow(summary, detail)` | none |
| Ranges | `StockRangeBar` | none |
| Actions | `StockActionLayout.sideBySide` + `StockButton` | none |
| Chart stats stacking | `StockLayout.metricRowStacked` (one decision per group) | none |
| Disclosure (Why it moved / Earnings AI) | `StockShowDetails` / `StockDisclosure` | none |
| Status | `StockStatusBadge` / `MarketStatusIndicator` / `StockBanner` / `StockSkeleton` | none |

## G. Cross-platform implementation (files likely to change)

**Shared and core:**
- `app/shared/.../presentation/companydetails/CompanyDetailsScreen.kt`: order, borders, actions, stats, section bar, compact earnings, key-ratio
  explain.
- `CompanyDetailsPresentation.kt`: display-only helpers: `whatItDoes` (first sentence of `about` + "Sector · Industry"), `chartStats` (Open, Volume),
  section ids. Unit tests in `app/shared/src/commonTest/.../CompanyDetailsPresentationTest.kt`.
- `core/.../companydetail/CompanyOverview.kt`: only `InfoRow.metricId` tags on `keyRatios` (as 5D.5.3 did for Financials). No value or calculation
  changes.
- `app/shared/.../theme/StockSemanticStyles.kt` + `ResponsiveLayoutRulesTest`: the section-bar rule (if D-E1 = B) and a chart-axis label count at
  large text.
- `app/shared/.../presentation/research/GuidedResearchScreens.kt`: `UnderstandStockCard(bordered)` optional parameter.
- `CompanyFinancialsScene.kt` / `CompanyValuationScene.kt`: metric switcher (D-F1) and range in the current card (D-V1). Display only.

**iOS:**
- `app/iosApp/iosApp/CompanyDetailsScreen.swift`;
- `CompanyFinancialsScene.swift`;
- `CompanyValuationScene.swift`;
- `ResearchViews.swift` (Understand card border);
- `DesignSystem/StockDetailComponents.swift` (stacked info lines, if needed).

**Not changed:**
- backend, fixtures, calculations, providers, auth, entitlements;
- Portfolio, Screener, Daily Brief, the Learn catalogue, `MetricLearnTerms` mapping;
- the global theme and navigation contracts (routes stay the same; the section bar scrolls within the page).

## H. Accessibility strategy
- **Large text:**
  - Android ≥ 1.75× and iOS AX4+: single-column tiles.
  - Chart stats and key–value groups stack label above value through `StockLayout.metricRowStacked` (fixes F1).
  - Never truncate data; tags leave the header (F3).
  - The chart shows at most 3 x-axis labels at large text, and the y-labels move inside the plot so the chart keeps its width.
- **Typography:** values and their captions on the same Dynamic Type curve (`relativeTo: .footnote`); no new font sizes.
- **Spoken labels:**
  - keep the single price sentence;
  - the section bar items are tabs ("Overview, tab, 1 of 4", selected state);
  - explainable rows keep "Explain {label}", role button;
  - ⓘ stays decorative.
- **Focus order:** follows the visual order in §C; the section bar is reachable after the actions; sheets return focus to the tapped element.
- **Targets:** 48 dp/pt for tiles, rows, pills, section tabs and chevrons.
- **Contrast:** borderless surfaces need surface/background contrast in dark (#0D1B2A on #07111C); check `textMeta` on surface ≥ 4.5:1.
- **Back and sheets:** the sheet closes before navigating (5D.5.3); Back from Learn, Financials and Valuation restores the scroll position; the
  section bar never pushes a route.
- **Known limits (tracked, not claimed solved):** iOS live Dynamic Type refresh; TalkBack not run; VoiceOver needs a device.

## I. Risks and regression coverage
| Risk | Mitigation / test |
|---|---|
| Chart state (loading / notice / scrub) breaks when the card loses its border or stats move | Keep `ChartSection` logic; device check of each state (MOCK `no-history` range, airplane-mode retry) |
| Metric accuracy | Display only; no change in `CompanyOverview` values; a presentation test that every value shown once still equals its source row |
| Repetition removal hides a value | Each removed duplicate is checked against its remaining place (§C); a test for `chartStats`/`keyRatios` filters |
| Navigation and Back | Routes unchanged; the section bar scrolls only; checks for Learn → Back, Financials → Back, Valuation → Back, article |
| Contextual Learn integration | `MetricLearnTermsTest` unchanged; key-ratio ids tagged with a test (same pattern as 5D.5.3) |
| Section-bar complexity (sticky header, anchor scroll) | Phase 5D.5.4A delivers the hierarchy first; the bar is optional (D-E1) |
| Earnings compaction drops information | Move AI preview text into `StockShowDetails`; keep View Results and Remind visible |

## J. Proposed implementation phases (each reviewable on its own)
1. **5D.5.4A: header, chart and overview hierarchy.**
   - Tags out of the header; one action row.
   - Practice Buy moves to Research further.
   - Borderless chart with Open · Volume; stacked stats at large text (F1–F3).
   - "What Apple does" row.
   - New order (§C).
   - Section bar if approved (D-E1).
2. **5D.5.4B: At a Glance and Understand.**
   - Borderless tiles, 52-week range only.
   - Understand rows borderless.
   - Insight kept.
   - Why Did It Move? compact and moved below Understand.
   - Repetition removed (F5).
3. **5D.5.4C: Financials and Valuation.**
   - Metric switcher (D-F1).
   - P/E range in the current card (D-V1).
   - Key Ratios explainable (F8).
   - Period selectors on one line.
4. **5D.5.4D: News, Earnings, spacing and consistency.**
   - Compact Earnings (F6).
   - Borderless News and About.
   - Research further group.
   - Spacing tokens.
   - Disclaimer.
5. **5D.5.4E: Android/iOS verification and regression QA** (matrix below).

**Verification matrix (5D.5.4E, local MOCK only):**

| Area | Android emulator 360 dp | iPhone 16 simulator |
|---|---|---|
| Appearance | light, dark (in-app theme) | light, dark |
| Text size | 1.0×, 1.3×, 2.0× | Default, AX XL, AX5 |
| Price and chart | ranges 1D–ALL, scrub, loading, no-history notice, retry | same |
| Navigation | Financials, Valuation, News, Movement, Learn term, Research, Practice Buy, Compare, Add to portfolio, Back and scroll restore | same |
| Contextual Learn | glance tiles, key ratios, Financials/Valuation rows, mapped vs unmapped sheets | same |
| Watchlist | star toggle (guest and signed-in where available) | guest (signed-in BLOCKED without the owner) |
| States | loading, empty news, offline, stale quote ("Updated …" date), section errors | same |
| Tests | `:core:jvmTest`, `:core:iosSimulatorArm64Test`, `:app:shared:testAndroidHostTest`, `:app:shared:iosSimulatorArm64Test`, `:server:test`, `assembleDebug`, `xcodebuild` | — |

## K. Owner decisions (recommended option first)
- **D-E1 Section navigation:**
  - **B: an in-page section bar (Overview · Financials · News · About) that scrolls to sections.** Recommended: it matches the reference and keeps
    the navigation contracts.
  - A: no bar, reorder only (least work).
  - C: real tabs with separate content panes (changes navigation; out of scope).
- **D-E2 Header actions:**
  - **Add to portfolio + Compare on one row; Practice Buy moves to "Research further" with Guided Research.** Recommended.
  - Keep all three in the header.
  - Move all actions below At a Glance.
- **D-E3 Header tags:** **remove them (sector and industry show once in "What Apple does" and About; the size tag is already the Market Cap helper).**
  Recommended. Alternative: keep the tags, wrapping without truncation.
- **D-E4 Duplicates (F5):**
  - **Remove the chart's High/Low (the Day Range carries them) and move the Day Range bar next to the chart.** Recommended.
  - **Show P/E vs average once (the Valuation card) and keep the Understand Valuation row as the beginner sentence.** Recommended.
  - Keep the others: each answers a different question.
- **D-E5 Why Did It Move? position:** **below Understand, compact** (recommended); alternatively, keep it under the chart.
- **D-F1 Financials:**
  - **A metric switcher (Revenue · Net Income · Free Cash Flow) on one chart at the top; sections keep their rows, ⓘ and "What is this?".**
    Recommended; matches the reference.
  - Alternatively, keep a chart per section.
- **D-V1 Valuation:** **move the historical P/E range bar into the current P/E card** (recommended). The reference's "Industry" marker is not
  possible because no data exists.
- **D-N1 Earnings card:** **compact (date + Remind, latest result + View Results; the AI preview folds into Show details)** (recommended);
  alternatively, keep it as is.
- **D-B1 Borders:** **borderless at every Company Details call site** (owner direction, recommended); keep outlines only for selection and toggles.

## Blocked or unverified during the audit
- iOS signed-in behaviour (watchlist, Add to portfolio): BLOCKED (guest mode; Claude never signs in).
- TalkBack and VoiceOver: NOT RUN.
- iOS AX XL and Android 1.3× were not captured in the audit (planned in 5D.5.4E).
- Company News, Stock Movement and Earnings destinations were reviewed in code only.
