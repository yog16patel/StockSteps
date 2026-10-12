# Portfolio UI Refinement — Phase C.1: visual and accessibility closure

Status: **committed and pushed** as "Close Portfolio visual and accessibility pass with group stacking, grouped transaction rows, single headings and large-text insights fixes (Portfolio Phase C.1) on Android and iOS" (2026-10-12), on top of `8a86e4e` (Phase C). **Phase D is not started.**

No presenter, API, server, ledger, calculation, entitlement, fixture, route or authentication change. Text scaling isn't limited anywhere; every
fix changes the layout (stacking, grouping, wrapping) or removes a duplicate.

## 1. Evidence

All screenshots below were captured in this phase on the local MOCK backend, in `docs/design/portfolio_pc1/`:
- `d-*` = Android 360 dp;
- `di-*` = iPhone 16;
- files named `*-before*` show the defect **before** its fix in this phase; every other file shows the **current** state.

Phase C captures (`docs/design/portfolio_pc/`) are the earlier baseline.

## 2. Issues

| # | Platform | Issue (evidence) | Status | Fix |
|---|---|---|---|---|
| 1 | both | Metric rows decided one by one at large text: "Invested / cost  CAD 1,455.00" beside a stacked "Unrealized P/L" (Phase C `c-dark2x-*`) | **FIXED** (`d-dark2x-y3`, `di-ax5-overview`) | `StockMetricGrid(rowsWhenNarrow)` makes one decision per group: if any row must stack, all do (both platforms) |
| 2 | Android | Allocation legend "AAPL · / USD" broke and rows alternated layouts at 2.0× (Phase C `c-dark2x-r7`) | **FIXED** (`d-dark2x-y6`) | `StockAllocationLegendRow(stacked)` + `allocationLegendStacked` (whole label measured, one decision per group); donut above the legend when it can't sit beside it (unchanged rule) |
| 3 | iOS | Legend stacked only at AX sizes | **FIXED** (`di-ax5-legend`) | Same measured group rule (`StockAllocationLegendRow.groupStacked`) |
| 4 | Android | "Holdings / (2)" wrapped beside the sort menu at 2.0× | **FIXED** (`d-dark2x-y4`) | Sort menu below the title at large text (iOS: at AX sizes, `di-ax5-holdings`) |
| 5 | both | Stacked holding rows kept the avatar column, so text started indented and wrapped early | **FIXED** (`d-dark2x-y5`, `di-ax5-holdings`) | Structured vertical layout: avatar beside the name only; ticker, weight, value and change use the full row width |
| 6 | both | Transaction rows "Opening position · TD.TO · 5 shares" wrapped inside groups at large text | **FIXED** (`d-transactions-2x`, `di-ax5-transactions`, `d-light1x-allocation-transactions`) | Shared `PortfolioPresentation.transactionText`: title / security / date / amount as separate lines (date · amount share a line at normal sizes); one spoken label |
| 7 | Android | "Dividends / received" broke at 2.0× | **FIXED for words** (`d-dark2x-y6`) | Cash/dividend lines use `metricRowStacked`. "Dividends received" now wraps between whole words (ACCEPTED) |
| 8 | both | Holding details showed the title twice (top bar + large in-screen "Holding details") and a "Holdings" section heading above one row | **FIXED** (`d-details-2x-top`, `d-light1x-details-top`, `di-large-details`) | In-screen title and section heading removed on details; the holding row heads the screen |
| 9 | both | Portfolio insights showed the title twice (bar + screen); on iOS a large nav title truncated "Portfolio ins…" at AX5 | **FIXED** (`d-insights-header-2x`, `di-ax5-insights-heading`; before: `di-ax5-insights-duplicate-title-before`, `di-ax5-insights-largetitle-truncated-before`) | Android: in-screen title removed (bar shows it). iOS: empty nav title, in-screen header kept (it wraps) |
| 10 | Android | Insights sample-scenario field blank when no scenario chosen (`d-insights-header-2x` before) | **FIXED** | Placeholder "My accounts" |
| 11 | both | Insights metric rows alternated stacked/side-by-side at 2.0× (`d-insights-health-2x-before`) | **FIXED** (`d-insights-health-2x`, `di-ax5-insights-health`) | All rows stack at large text; thin dividers group label/value/detail |
| 12 | both | Insights contributors broke names mid-word ("Micros / oft") at 2.0× (`d-insights-contributors-2x-before`) | **FIXED** (`d-insights-contributors-2x`, `di-ax5-insights-contributors`) | Amounts under the name at large text |
| 13 | both | Insights allocation rows broke tickers ("SHOP.T / O") at AX5 (`di-ax5-insights-allocation-before`) | **FIXED** (`di-ax5-insights-allocation`) | Value under the label at large text |
| 14 | both | Insight cards at AX5: icon overlapped the text, a non-functional arrow squeezed it (`di-ax5-insight-card-before`) | **FIXED** (`di-ax5-insight-cards`) | `StockInsightCard`: fixed-size symbol, icon above the text at large text, arrow only on tappable cards (both platforms) |
| 15 | iOS | "How these numbers work" rows centred and hyphenated ("complete-ness") at AX5 (`di-ax5-learn-hyphen-before`) | **FIXED (code; not re-captured)** | Leading-aligned label, title colour, muted disclosure tint |
| 16 | iOS | Content scrolled under the status bar (time over numbers) on the navigation-bar-less Portfolio tab | **FIXED** (`di-large-overview-statusbar`, `di-ax5-overview`) | Page-background strip behind the top safe area (decorative, no hit testing) |
| 17 | both | Scenario caption between "Total value" and the total | **FIXED** (`d-light1x-overview`, `di-large-overview-statusbar`) | Caption moved under the total |
| 18 | both | Insights sub-groups ("Added the most", "By year") looked like body text | **FIXED** | Label role, supporting colour, heading semantics, space above |
| 19 | both | MOCK strip / bottom navigation over the last item | **ACCEPTED (no issue)** | Last item scrolls fully clear on both platforms (`d-dark2x-*`, `di-ax5-transactions`) |
| 20 | iOS | Cached test account: "Insights couldn't be loaded" (offline session token) | **DEFERRED** (not UI; reported in Phase C) | Session/auth state, outside UI scope |
| 21 | both | Overview hierarchy: total, today's change and account value already lead; disclosures stay visible | **ACCEPTED** | No change beyond #17 |
| 22 | — | iPhone SE accessibility layout | **DEFERRED (NOT RUN, not approved)** | — |

## 3. Shared / design-system changes (backward compatible)

- `PortfolioPresentation.transactionText` / `TransactionText` (new).
- `StockMetricGrid(rowsWhenNarrow)`: one stacking decision per group, on both platforms. This affects every `rowsWhenNarrow` caller, but only when a row
  would otherwise need to stack.
- Android `StockAllocationLegendRow(stacked)` + `allocationLegendStacked`; iOS `StockAllocationLegendRow(groupStacked)` + `groupStacked(...)`.
- `StockInsightCard` (both platforms): icon above the text at large text; on iOS a fixed-size symbol and an arrow only when `action` is set.

## 4. Tests and verification

- New `PortfolioPhaseC1Test` (3):
  - transaction text groups;
  - cash transaction without a security;
  - the row-stacking rule.
- All earlier Portfolio tests are unchanged and passing.
- Final run: §6.

## 5. Device coverage

| Device | Run |
|---|---|
| Android 360 dp light 1.0× | RUN (`d-light1x-*`) |
| Android 360 dp dark 1.0× | RUN (`d-dark1x-*`) |
| Android 360 dp dark 2.0× | RUN (`d-dark2x-*`, `d-details-2x-*`, `d-transactions-2x`, `d-insights-*`, `d-empty-dark2x`) |
| iPhone 16 light Large | RUN (`di-large-*`) |
| iPhone 16 dark AX5 (live switch) | RUN (`di-ax5-*`) |
| iPhone SE accessibility | NOT RUN (not approved) |
| TalkBack / VoiceOver | NOT RUN |

Restored afterwards:
- Android: emulator size reset, font scale 1.0, app Theme Light, `adb reverse` removed;
- iPhone 16: Large/light;
- MOCK stopped.

No Firebase emulator was started.

## 6. Verification results (final sequential run after all changes, clean test outputs)

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` | **BUILD SUCCESSFUL** (8 m 43 s) |
| core JVM / core iOS | **471 / 0** each |
| shared Android host | **169 / 0** (Phase C: 166; +3) |
| shared iOS simulator | **165 / 0** (Phase C: 162; +3) |
| `:app:androidApp:assembleDebug` | OK |
| default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED** |
| `:server:test` | NOT RUN (no server change) |

## 7. Phase D readiness

Ready after review. No form, transaction-filtering or navigation change was made. Phase D (date pickers, search-first form, iOS validation, Android
edit with Cancel, Transactions destination) still needs approval for its new routes.
