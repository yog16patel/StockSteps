# Portfolio UI Refinement — Phase C: holdings, allocation, details, insights and visual polish

Status: **IMPLEMENTED — VERIFICATION PARTIAL**, committed and pushed as "Refine Portfolio holdings, allocation donut, holding details and insights with borderless sections and matching selector icons (Portfolio Phase C) on Android and iOS" (2026-10-12), on top of `6c1d76b` (Phase B.1). The brief assumed B.1 was uncommitted, but
it was already committed and pushed, so the tree started clean. Remaining: iPhone SE, TalkBack and VoiceOver (§9). **Phase D is not started.**

No presenter-contract, API, server, ledger, calculation, FX, entitlement, fixture, route or authentication change. Every amount and percentage
shown is an existing exact-decimal presenter value; nothing is recomputed or normalised.

## 1. Summary

| Area | Change |
|---|---|
| Borderless content | Portfolio overview, holdings, holding details, allocation, cash and dividends, transactions, insights entry and the Insights sections use the existing `bordered = false` / `.stockCard(bordered: false)` option. Surface colour against the page background plus whitespace separate sections; no shadow added. Other screens keep their borders (option is per call site) |
| Selector icons | Android "My Portfolio" / "Practice" now show a briefcase and a graduation cap (new `StockIcons.Briefcase`, `School`). A new optional `compactIcons` on `StockSegmentedControl` (Compose and SwiftUI, default false → other callers unchanged) keeps a ~20 dp/pt icon beside the label at every size. Previously iOS stacked a large icon above the label at accessibility sizes |
| Holdings | Shared `PortfolioHoldingsDisplay` / `PortfolioHoldingItem`: name, "AAPL · NASDAQ · 10 shares", optional **weight** "72.97% of account value" (only from a single matching `allocations[]` entry, else hidden), value (strong), signed change (percentage, else amount, else "Gain unavailable"); one spoken label (+ weight). Optional **sort** "Sort: Default ▾" → Default / Value / Name / Change % via `PortfolioDisplayRules.sortHoldings` (display only, stable, missing last, not persisted). Stacking rule unchanged (`PortfolioLayout.stackedRowWidth` / large text) |
| Allocation | New **`StockAllocationDonut`** + **`StockAllocationLegendRow`** (Compose Canvas, SwiftUI Canvas) with geometry from the new shared **`StockAllocationLayout`** (start angle, gaps, minimum visible sweep, ring thickness, diameter, legend beside vs below). Slice colours from `StockChartSeriesStyle` (never gain/loss colours). Shown only when `allocationDonutAllowed`; otherwise the labelled **bars** (reported %, clamped bar). **By currency** is a second legend group, **collapsed by default** (expand/collapse row with state for screen readers). Ring decorative; legend rows spoken in full |
| Holding details | Metric grid from `PortfolioHoldingsDisplay.detailMetrics`: Shares, Price, Market value, Cost, Unrealized P/L (amount + %), Realized P/L, Dividends, each with currency and sign, "USD —" when missing, `rowsWhenNarrow`. Actions grouped as `StockNavigationRow`s under "More for this holding". Transactions still filtered to the holding (unchanged filter); the account-wide "Cash and dividends" section is no longer shown here |
| Chart | Optional `areaFill` on `StockTrendChart` (default false — Company details and Comparison unchanged) and an `AreaMark` on iOS: a subtle brand-blue fill per run of values, gaps preserved. No point markers except the selected point. `StockChartLayout.height(width)` kept |
| Portfolio insights | `StockChip` for accounts and periods (Android `StockChip` gained an optional `enabled`, default true), card titles in the card-title role, borderless sections, supporting/strong colours for metric rows (less white text in dark mode), metric rows stack via `StockLayout.metricRowStacked` (Android) / at accessibility sizes (iOS), readable dates, iOS legend swatches via `StockChartSeriesStyle`, **category icons** on insight cards (shared `PortfolioInsightIcons`), readable sample-scenario names. Engine text, locks and entitlement behaviour untouched |
| Wrapping | Non-breaking spaces keep "10 shares" and "CAD 75.00" together (`holdingSubtitle`, `transactionDetail`, `transactionAmount`); spoken text keeps plain spaces |

## 2. Files changed

- Shared (new):
  - `presentation/portfolio/PortfolioHoldingsDisplay.kt`, `PortfolioAllocationDisplay.kt`;
  - `designsystem/components/StockAllocationDonut.kt`;
  - test `PortfolioPhaseCTest.kt`.
- Shared (modified):
  - `PortfolioScreen.kt` (borderless, icons, holdings + sort, details, allocation, scenario sheet unchanged);
  - `PortfolioHistoryChart.kt` (area fill);
  - `PortfolioInsightsScreen.kt`;
  - `PortfolioPresentation.kt` (NBSP, spoken normalisation);
  - `PortfolioDisplay.kt` (`PortfolioInsightIcons`);
  - `theme/StockSemanticStyles.kt` (`StockAllocationLayout`);
  - `designsystem/components/StockSegmentedControl.kt` (`compactIcons`), `StockChip.kt` (`enabled`), `StockTrendChart.kt` (`areaFill`);
  - `designsystem/icons/StockIcons.kt` (Briefcase, School);
  - tests `StockIconsTest.kt`, `PortfolioPresentationTest.kt` (NBSP expectations).
- iOS:
  - new `DesignSystem/StockAllocationDonut.swift`;
  - modified `PortfolioScreen.swift`, `PortfolioHistoryChart.swift`, `PortfolioInsightsScreen.swift`, `DesignSystem/StockSettingsComponents.swift`
    (`compactIcons`).
- Docs: this file, `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`, `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`; captures `docs/design/portfolio_pc/`.

## 3. Android / iOS parity

Same shared display objects drive both platforms: `PortfolioHoldingsDisplay`, `PortfolioAllocationDisplay`, `StockAllocationLayout` and
`PortfolioInsightIcons`. Matching on both:
- borderless sections;
- the briefcase and graduation-cap selector;
- holding rows, weight, sort options;
- donut + legend, bars fallback, collapsed By currency;
- details grid and actions;
- area fill;
- Insights chips and icons.

Differences (native idioms):

| Topic | Android | iOS |
|---|---|---|
| Sort control | dropdown with a check | `Menu` with a `Picker` |
| Legend placement | measured (`legendBeside`) | beside the donut at Large, below at xxLarge and larger |
| Insights metric rows | stack by measured fit (`metricRowStacked`) | stack at accessibility sizes |
| Insights entry icon at AX sizes | kept (Android wraps cleanly) | hidden at AX sizes |

## 4. Reusable components

- New: `StockAllocationDonut`, `StockAllocationLegendRow` (both platforms), `StockAllocationLayout` (shared, tested).
- Extended, backward compatible:
  - `StockSegmentedControl(compactIcons)`;
  - `StockChip(enabled)`;
  - `StockTrendChart(areaFill)`.

`StockOverviewCard` doesn't exist in the code; the overview remains Portfolio's `PortfolioOverviewCard` (borderless).

## 5. Design decisions and deviations

- **Borderless per call site**, not a global `StockCard` change, to avoid regressions elsewhere.
- **Default sort = presenter order**: no approved default existed.
- **Weight only for an unambiguous match** (one allocation per symbol); the same ticker in two currencies hides it.
- **Holding details no longer show account-wide cash and dividends**: they aren't the holding's. Holding dividends stay in the grid.
- **iOS holding row on details** is no longer dimmed (was `.disabled`); it isn't tappable there.
- **Insights sample-scenario picker readable labels** (MOCK only), as on the dashboard sheet.
- **Not done:** a separate tablet layout, sector/asset-class charts on the dashboard (StockSteps+ data), persisted sort.

## 6. Tests and results

New `PortfolioPhaseCTest` (10):
1. donut eligibility;
2. invalid allocation keeps reported values for bars;
3. spoken legend labels;
4. stable, display-only sort (VALUE by reporting currency);
5. change % missing → amount → "unavailable";
6. weight only from an unambiguous allocation;
7. missing values in holding details;
8. donut geometry and legend placement;
9. every insight category has an icon and a readable label;
10. currency rules.

Also: `StockIconsTest` builds the two new icons; existing chart tests and callers are unchanged by the optional parameters.

Final sequential run, clean test outputs: §11.

## 7. Screenshots (`docs/design/portfolio_pc/`, local MOCK only)

Android 360 dp (`c-*`):
- light 1.0×:
  - Mixed currencies: overview with icons and borderless card (`c-p2`), holdings with weight and sort (`c-p3`), donut + legend + collapsed By
    currency (`c-p4`), By currency expanded (`c-currency-expanded`);
  - sort menu (`c-sort-menu`), sorted by Change % (`c-sorted-change`);
  - Holding details (`c-details-top`, `c-details-actions`);
  - Missing FX fallback bars (`c-fallback-bars`);
  - Portfolio insights, Diversified sample (`c-insights-q5/q6/q7`);
  - empty portfolio (`c-empty`); Multiple accounts (`c-multi-light`);
- light 1.3×: `c-light13-s4/s5`;
- dark 1.0×: `c-dark-r1/r2/r3`;
- dark 2.0×: `c-dark2x-r5…r8`.

iPhone 16 (`ci-*`):
- light Large: allocation (`ci-allocation`), By currency expanded (`ci-currency-expanded`), holdings (`ci-holdings`), Holding details
  (`ci-details-before-dim-fix`, `ci-details-actions`);
- dark AX5: overview, holdings, donut + cash (`ci-ax5-overview`, `ci-ax5-holdings`, `ci-ax5-donut-cash`), insights entry (before/after fix),
  legend before the cash fix, Insights scenarios and metrics.

## 8. Accessibility verification

Fixed during the device pass:
- iOS AX5 "Dividends received" broke as "Dividend / s" → label/value stack at AX sizes;
- iOS AX5 insights entry subtitle broke "concentratio / n" → the decorative icon is hidden at AX sizes;
- Android 2.0× "10 / shares" and "CAD / 75.00" → non-breaking spaces;
- iOS details row was dimmed → no longer.

Checked on screen:
- no horizontal overflow or clipped values at Android 2.0× or iOS AX5;
- the last item clears the MOCK strip;
- legend rows are single spoken elements;
- the donut is hidden from screen readers;
- By currency announces expanded/collapsed;
- the sort control is labelled.

**NOT VERIFIED:** TalkBack/VoiceOver navigation (labels unit-tested and read in code only).

## 9. Known issues

- **iPhone SE: NOT RUN** (not approved for this phase).
- **Insights for the cached iOS test account** shows "Insights couldn't be loaded": the offline cached session has no valid token for this MOCK run.
  This is pre-existing session state, not caused by Phase C; the sample scenarios render. Reported separately, not hidden.
- iOS legend placement uses a type-size threshold, not measured text (Android measures); fine at Large and AX5 on iPhone 16, unverified on SE.
- The Android Insights sample picker is a `StockSelectField` (readable names); the iOS one stays a system `Menu`.

## 10. Deferred to Phase D / E

- **D:**
  - date pickers;
  - search-first form layout with Advanced;
  - iOS client validation and field errors;
  - Android edit with Cancel;
  - the Transactions destination with filters.
- **E:**
  - iPhone SE and screen-reader passes;
  - the Android chart ⓘ row height;
  - a measured iOS legend placement;
  - re-capture of other screens using the shared live-type and chart changes.

## 11. Verification (final sequential run after all changes, clean test outputs)

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` | **BUILD SUCCESSFUL** (8 m 31 s) |
| core JVM / core iOS | **471 / 0** each |
| shared Android host | **166 / 0** (B.1: 156; +10 `PortfolioPhaseCTest`) |
| shared iOS simulator | **162 / 0** (B.1: 152; +10) |
| `:app:androidApp:assembleDebug` | OK |
| default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED**, no warnings in the changed files |
| ad-hoc signed simulator build with `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` | BUILD SUCCEEDED (device checks) |
| Android 360 dp: light 1.0×, dark 1.0×, light 1.3×, dark 2.0× | **RUN** (§7) |
| iPhone 16: light Large, dark AX5 | **RUN** (§7). Dark Large was not captured separately (the dark styling is shared and verified at AX5) |
| iPhone SE accessibility size | **NOT RUN** (not approved) |
| TalkBack, VoiceOver, physical devices, `:server:test` (no server change) | **NOT RUN** |

Devices restored:
- Android: emulator size reset, font scale 1.0, app Theme Light, `adb reverse` removed;
- iPhone 16: Large/light;
- MOCK stopped.

No Firebase emulator was started.
