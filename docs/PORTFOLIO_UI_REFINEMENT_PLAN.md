# Portfolio UI Refinement — implementation roadmap (P-A and Phase A implemented and committed; Phase B awaiting approval)

Status: **plan only** (revised 2026-10-12). Findings: `docs/PORTFOLIO_UI_AUDIT.md` (IDs **A-F1…A-F20**, risks **D-R1…D-R10**, questions **Q1–Q7**).
This revision replaces the 2026-10-11 plan, which was committed in `92a6d6a` and is still in git history. Its issues **#1–#16**, components
**C1–C7** and decisions **D1–D3** keep their numbers here. Phase P-A of that plan (shared display rules + baseline,
`docs/PORTFOLIO_REFINEMENT_PHASE_PA.md`) is **done**. Its rules R1–R8 stay in force and are folded into Phase A below.

## Rules for every phase

UI only: **no** presenter-contract, API, calculation, ledger, entitlement or fixture change. Exceptions are listed per phase, need the owner's
approval, and are limited to navigation identity (a Route argument) and calling an existing validator. The rules:
- Exact decimal strings from `PortfolioUiState` are formatted, never recomputed. Missing stays missing, never `0.00`.
- Gain/loss is shown in words + sign + arrow, never colour alone. The chart line stays brand blue and never encodes direction.
- Touch targets are 48 dp / 44 pt (`dims.touchTarget`). Five bottom tabs.
- `primaryAction` fills primary buttons. Brand `#1683FF` (`primary`) is for accents, charts, selection and links.
- Layout decisions live in shared `StockSemanticStyles` objects with tests. Screen copy lives in a shared `*Display` object.
- On iOS, a value and its caption use the same Dynamic Type curve, and views read `dynamicTypeSize` so they refresh on live changes.
- No advice-like copy: no "consider adding", no invented thresholds, no scores.
- Each phase is one reviewable change covering both platforms. Stop for review after each. Commit only on request.

Verification for every phase:
- Clean outputs, then sequentially: `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest
  :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`, then the default `xcodebuild … CODE_SIGNING_ALLOWED=NO build`.
- Device captures on the **local MOCK** backend only, after approval.
- A phase doc `docs/PORTFOLIO_UI_REFINEMENT_P<x>.md`, plus `docs/PROJECT_HANDOFF.md` in the same commit.

---

## Phase A — Foundation, correctness and reusable components

**Implemented 2026-10-12, committed as "Fix Portfolio account selection by id and add Phase A shared form rules, readable labels, formatted holding amounts and consistent copy on Android and iOS"**: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_A.md` (results, parity, deviations).

Goal: fix the P0/P1 defects that need no layout redesign, and put the shared copy and form rules in place that later phases use.

| Item | Findings | Change |
|---|---|---|
| A1 | A-F1 (**P0**) | Android form: account chosen **by id**: `StockSelectField<PortfolioAccount>` showing `accountLabel` + `accountDetail` |
| A2 | A-F2 | Readable option labels in both forms: `PortfolioPresentation.transactionLabel` / `categoryLabel` (Swift: `Text(present.transactionLabel(type:))` with the enum tag unchanged) |
| A3 | A-F3 | Holding details: Realized / Dividends through `signedAmount` / `PortfolioFormat.amount` with `row.currency` |
| A4 | A-F7 | Form buttons → `StockButton` / `.stockPrimary`; error colour → `negativeText` (both) |
| A5 | A-F4 | Android number fields → `StockTextField(keyboardType = KeyboardType.Decimal)` (label, supporting, `error`) |
| A6 | A-F18, C4 | New `PortfolioDisplay` (shared copy: sample banner, empty states, holdings/transactions empty lines, form titles, delete confirmations, chart title + note, spoken summaries) + tests; both platforms read it |
| A7 | A-F10 | New `PortfolioFormRules` (shared): visible fields per `TransactionType`, field order, labels ("Average cost per share", "Split ratio: new / old"), primary types (Buy, Sell, Opening position) vs "More types" + tests. **Same fields as today**: the rule only unifies the two platforms |
| A8 | A-F14 | Insights "As of" via `PortfolioDates.dateTime` |
| A9 | A-F12 | One name, "Portfolio insights": entry card, Android top-bar title, screen titles |

- **Files likely to change:**
  - Android: `presentation/portfolio/PortfolioForms.kt`, `PortfolioScene.kt`, `PortfolioScreen.kt` (details grid), `PortfolioInsightsScreen.kt`
    (date, title), `PortfolioPresentation.kt` (+ `PortfolioDisplay`, `PortfolioFormRules`, or a new `PortfolioDisplay.kt`), `AppNavigation.kt`
    (title string only);
  - iOS: `PortfolioForms.swift`, `PortfolioScene.swift`, `PortfolioScreen.swift`, `PortfolioInsightsScreen.swift`;
  - tests: `PortfolioPresentationTest.kt` (+ display/form-rule tests).
- **Reuse:** `StockSelectField`, `StockTextField`, `StockButton`, `.stockPrimary`, `PortfolioPresentation`, `PortfolioDates`.
- **New components:** none. New shared objects: `PortfolioDisplay` and `PortfolioFormRules`, both text and visibility only.
- **Dependencies:** none. The P0 can ship alone first (Q2).
- **Risks:** the form state uses `rememberSaveable` with enums, so switching to id-based selection must keep restoring the selection. iOS
  pickers bind to strings, so keep the tags as enum names.
- **Tests:**
  - duplicate-name accounts select the right id (a pure helper test);
  - every `TransactionType` has a label and a field set;
  - copy parity (one source);
  - holding-detail formatting never returns a raw decimal.
- **Acceptance:**
  - no raw enum text anywhere in Portfolio;
  - Android shows a decimal keyboard on number fields;
  - Save buttons pass AA;
  - two same-named accounts work;
  - identical copy on both platforms;
  - all suites green.
- **Excluded:** layout changes, the date picker (Phase D), validation placement (D), overview card (B).

## Phase B — Portfolio overview

Goal: rules R1/R2 — one overview card with NUMBER → CONTEXT, honest chart naming, and fixed iOS scaling.

| Item | Findings | Change |
|---|---|---|
| B1 | #1, #7 | **C1 `StockOverviewCard`** (Compose + SwiftUI, optional params): title "Total value" + SAMPLE badge (`StockTitleWithBadge`) + ⓘ; total (`largeNumber`, wraps at the currency boundary via a shared `StockValueWrapRule`, no scale factor); today's change or "unavailable · Why?"; chart slot + range pills; metric tiles (Invested / Unrealized / Realized, `StockMetricGrid(rowsWhenNarrow)`); one freshness line (full text in ⓘ); caution notice kept visible |
| B2 | A-F6 / D-R2 | Chart title "Account value"; note "Includes cash, deposits and withdrawals. Returns that leave out deposits are in Portfolio insights." (copy in `PortfolioDisplay`) |
| B3 | #2 (R2) | No auto-load; when nothing is loaded a compact "Show 1M" text action replaces the empty block (calls the existing `onHistory("1M")` on tap) |
| B4 | A-F17 / D-R5 | Shared `PortfolioChartRules.flat(values)`: a flat series shows the value once (no ±1 ticks on iOS, no full-width flat line) |
| B5 | #4 / A-F16 | iOS: paired `relativeTo:` curves for overview text, `dims.touchTarget` for header buttons, reads `dynamicTypeSize` for live updates |
| B6 | A-F11 (Q5) | Currency rule for the metric tiles (per value or one "Amounts in CAD" line) |
| B7 | #9 (part) | "+ Add transaction" leaves the hero path: a secondary/tonal action below the overview (final placement in Phase C/D) |
| B8 | #8 | MOCK "Sample scenario…" moves into ⋯; the active scenario is the SAMPLE badge + existing banner (REAL builds unchanged) |

- **Files:**
  - Android: `designsystem/components/StockOverviewCard.kt` (new), `presentation/portfolio/PortfolioScreen.kt`, `PortfolioHistoryChart.kt`,
    `theme/StockSemanticStyles.kt` (`StockValueWrapRule`), `ResponsiveLayoutRulesTest.kt`;
  - iOS: `DesignSystem/StockComponents.swift` (overview card), `PortfolioScreen.swift`, `PortfolioHistoryChart.swift`.
- **Reuse:** `StockCard`, `StockTitleWithBadge`, `StockStatusBadge`, `StockMetricGrid`, `StockPillSelector`, `StockTrendChart` / Swift Charts,
  `StockPriceChange`, `PortfolioPresentation`.
- **New component:** C1, justified because it's reusable by Home's `PortfolioSummaryCard` and Practice later. All parameters are optional and
  the defaults keep existing callers unchanged.
- **Dependencies:** Phase A (`PortfolioDisplay`). Decisions Q5, D1 (on-demand recommended), D2 (no total-return % unless a presenter field is
  approved separately).
- **Risks:**
  - the wrap rule at AX5 with long CAD amounts;
  - chart height inside the card at 2.0×;
  - iOS live Dynamic Type refresh;
  - avoid a feedback loop if any width is measured (see the `StockHeadingFit` lesson).
- **Tests:**
  - `StockValueWrapRule` and `PortfolioChartRules.flat` in `ResponsiveLayoutRulesTest` / `PortfolioPresentationTest`;
  - `PortfolioDisplay` chart copy.
- **Acceptance:**
  - value, change and chart are in one card;
  - no empty chart area on open;
  - the title matches the data;
  - no clipped or shrunk totals on Android 2.0× / iOS AX5;
  - live type change refreshes on iOS;
  - before/after captures.
- **Excluded:** holdings, allocation, sort, skeletons, empty-state v2, account chip, Home.

## Phase C — Holdings, allocation, holding details (and the Insights screen pass)

| Item | Findings | Change |
|---|---|---|
| C1 | #6 | Holding rows: two right-hand lines (value strong, signed % with arrow); the amount stays in the spoken label and details; monogram fallback on `surfaceSecondary` |
| C2 | ref. Holdings | Optional weight % per row from `allocations[].percent` (server value, joined by symbol, display only, hidden when missing) |
| C3 | D3 | Optional sort control (Value / Name / Change %) using the existing `PortfolioDisplayRules.sortHoldings` |
| C4 | #5, R4 | **C2 `StockAllocationDonut` + legend** only when `allocationDonutAllowed`; bars otherwise; slice colours from `StockChartSeriesStyle` (never gain/loss colours); decorative ring hidden from screen readers; "By currency" per Q6 |
| C5 | #12 | One chart spec on both platforms (line in `primary`, subtle area fill, no point marks, gaps kept, first/last dates, selection text above) |
| C6 | #13, A-F15 | Holding details: formatted grid (Phase A) + actions as a `StockNavigationRow` group + this holding's transactions; no account-wide cash |
| C7 | A-F13, A-F19 | Insights pass: `StockChip`/`StockPillSelector`, theme fonts on paired curves, `rowMinHeight`, legend swatches via `StockChartSeriesStyle`, insight cards with category icons (Comparison `ObservationIcon` pattern) — **copy from the engine unchanged** |

- **Files:**
  - Android: `PortfolioScreen.kt`, `PortfolioHistoryChart.kt`, `PortfolioInsightsScreen.kt`, `designsystem/components/StockTrendChart.kt` (optional
    params), new `StockAllocationDonut.kt`;
  - iOS: `PortfolioScreen.swift`, `PortfolioHistoryChart.swift`, `PortfolioInsightsScreen.swift`, `DesignSystem/StockComponents.swift`;
  - shared: `StockSemanticStyles.kt` (`StockAllocationLayout`).
- **Reuse:** `StockTickerAvatar`, `StockNavigationRow`, `StockChartSeriesStyle`, `StockFadingScrollRow`, `PortfolioDisplayRules`.
- **New component:** C2 donut + legend, justified because it's reused by Practice and the Insights allocation.
- **Dependencies:** Phases A and B; decisions D3, Q6.
- **Risks:**
  - donut honesty (negative cash, > 100 %);
  - weight % when the holdings use live quotes and the allocation uses the report (D-R6) — label "of account value";
  - Insights has Plus-locked sections that must keep their lock copy.
- **Tests:**
  - donut gate, legend spoken labels, sort stability (existing);
  - weight join leaves missing values missing;
  - chart-spec parameters keep the defaults for other callers (Comparison, Company).
- **Acceptance:**
  - rows readable at 360 dp × 2.0× and iPhone SE AX5 with no mid-word breaks;
  - a donut appears only for valid data;
  - both platforms' charts look alike;
  - Insights uses theme tokens only.
- **Excluded:** asset-class / sector donut on the dashboard (Plus analytics), cash % row, logos in MOCK.

## Phase D — Add/Edit and transactions

| Item | Findings | Change |
|---|---|---|
| D1 | A-F5 | Date pickers (Material `DatePickerDialog`, SwiftUI `DatePicker(.date)`) writing the same `YYYY-MM-DD`; settlement optional; text entry kept reachable for accessibility |
| D2 | A-F10 | Form layout from `PortfolioFormRules`: security (search first, shows the chosen security) → type (Buy / Sell / Opening position + "More types") → date → shares & price or amount → fees → **Advanced** (collapsed: trade/cash currency, FX, settlement, notes, name/exchange) |
| D3 | A-F9 | Validation: same `PortfolioEngine.validate` before sending on **both** platforms (iOS bridge calls it; no new rules); known messages mapped to their field (`StockTextField(error =)`), others at the top; a local error, not the global banner |
| D4 | A-F8 | Android edit opens the full-screen form (needs `PortfolioEntryRoute(transactionId)`, **navigation identity only, approval needed**) or a full-height `ModalBottomSheet`, with Cancel; iOS unchanged (sheet) |
| D5 | Q4 | **Transactions destination** inside Portfolio (not a tab): "See all transactions" from the dashboard preview; filter chips All · Buys · Sells · Dividends · Cash · Other (display filter of `state.transactions`, grouping in `PortfolioDisplay`); rows type · symbol · shares / date · amount; empty-filter state |
| D6 | #9, #14 | Dashboard: transactions preview (3–5) + link; action placement final (Add transaction first in ⋯ + tonal row under holdings) |
| D7 | 3.3 l | Success confirmation after save (snackbar / banner text from `PortfolioDisplay`) |

- **Files:**
  - Android: `PortfolioForms.kt`, `PortfolioScene.kt`, `PortfolioScreen.kt`, `PortfolioRoute.kt` (if D4/D5 approved), `AppNavigation.kt` (routes,
    titles), new `PortfolioTransactionsScreen.kt` (Screen) + Scene wiring;
  - iOS: `PortfolioForms.swift`, `PortfolioScene.swift`, new `PortfolioTransactionsScreen.swift`;
  - `IosAccountClient.kt` (call the existing validator before saving).
- **Reuse:** `StockTextField`, `StockSelectField`, `StockSegmentedControl`, `StockChip`, `StockFadingScrollRow`, the Comparison collapsible
  section, `StockBanner`.
- **New components:** none. The Transactions screen is composed from existing rows.
- **Dependencies:** Phase A (form rules); approvals for the routes (D4/D5); the navigation boundary (Route = identity, Scene acquires the
  presenter, Screen renders).
- **Risks:**
  - keyboard overlap (Android `imePadding`, iOS `scrollDismissesKeyboard`);
  - date time zones (dates are plain `YYYY-MM-DD`, so never convert through local time);
  - editing a transaction that the server rejects ("would sell more shares than owned") must keep the form open with the message.
- **Tests:**
  - form rules per type;
  - validation message → field mapping;
  - transaction filter grouping;
  - date string round trip (UTC, no shift).
- **Acceptance:**
  - forms usable at 2.0× / AX5 with the keyboard open;
  - same fields per type on both platforms;
  - errors next to fields;
  - Cancel everywhere;
  - the Transactions screen filters without any new data.
- **Excluded:**
  - recent-securities chips (backlog);
  - a computed "Total value" preview (backlog, D-R7);
  - new transaction types;
  - bulk import.

## Phase E — Accessibility and QA

- Full suites and both builds.
- Captures, all on local MOCK and only after approval:
  - Android 360 dp: light 1.0×, dark 1.0×, light 1.3×, dark 2.0×;
  - iPhone 16: light and dark at Large;
  - iPhone SE: light at accessibility sizes, dark at AX5;
  - screens: dashboard, holding details, form (with keyboard), Transactions, Insights;
  - plus a history chart with ≥ 3 distinct values.
- Checks:
  - no horizontal overflow, overlap or clipped values;
  - no mid-word breaks;
  - whole-word tab labels;
  - 48 dp / 44 pt targets;
  - heading order and reading order;
  - contrast (`DesignTokensTest`).
- Android accessibility tree for every screen. TalkBack navigation needs the owner to answer the first-start notification prompt, and VoiceOver
  needs a physical device. Both stay **NOT VERIFIED** until then.
- Closure doc `docs/PORTFOLIO_UI_REFINEMENT_PE_QA_CLOSURE.md` with a verdict.
- **Excluded:** Home, Practice, server.

---

## Sequence and size

| Phase | Size | Can start | Blocks |
|---|---|---|---|
| A (P0 fix first, can ship alone) | small–medium | now, after approval | B, C, D |
| B | medium (largest visible change) | after A | C (chart spec) |
| C | medium | after B | — |
| D | medium–large (routes need approval) | after A; independent of B/C except the dashboard preview | — |
| E | small | after each phase (captures) and at the end | — |

## Decisions needed (owner)

| ID | Question | Recommendation |
|---|---|---|
| Q1 | Save the new reference sheet in the repo (`docs/design/stocksteps-portfolio-reference-v2.png`)? | Yes |
| Q2 | Ship A1 (P0 account-by-name) on its own now? | Yes |
| Q3 | Sub-tabs Overview / Holdings / Insights inside Portfolio? | No — single scroll + existing Insights screen |
| Q4 | Transactions destination with display filters? | Yes (Phase D) |
| Q5 | Currency on overview metrics: per value or one card line? | Per value ("CAD 1,455.00") — clearer for mixed-currency accounts |
| Q6 | Hide "By currency" when identical to "By holding"? | Show it as a second legend group, collapsed |
| Q7 | Drop duplicated currency in account labels? | Yes (display only) |
| D1 | History on open | On demand + "Show 1M" |
| D2 | Total return % | Not in this plan; separate approved presenter change if wanted |
| D3 | Holdings sort | Optional, Phase C |

## Not adopted from the reference (and why)

- "Consider adding more ETFs", "under the 30% guideline", "Good growth exposure": advice, invented thresholds or judgement.
- Total gain % and cash %: no supported field. The UI won't compute them.
- Stocks/ETFs/Cash and Top sectors on the free dashboard: StockSteps+ analytics.
- Green chart line: colour would encode direction. Ours stays brand blue.
- Reference colour values: our contrast-tested tokens already match its backgrounds and brand.
- A sixth tab or new bottom navigation: none.
