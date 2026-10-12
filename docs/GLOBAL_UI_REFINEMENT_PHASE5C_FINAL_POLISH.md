# Company Comparison — final UI polish (committed and pushed)

Status: **committed and pushed** as "Polish Company Comparison with shared chart legend styles, concise What-the-numbers-show insights, a quieter sample strip and large-text fixes on Android and iOS" (2026-10-12), on top of `92a6d6a` "Close Company Comparison Phase 5C.1 with section-based sticky header and spacing fixes,
and add the Portfolio UI audit and Phase P-A display rules" (pushed). UI only: no API, calculation, data model, navigation or comparison-behaviour
change; MOCK fixtures untouched. Device checks **run** on the owner's approval (2026-10-12) — see §6.

## 1. Changes (before → after)

| # | Issue | Before | After | Platforms |
|---|---|---|---|---|
| 1 | AX5 / large-text headers | Collapsed section header = title + "N metrics" + chevron in one row; at AX5 the title broke one word per line ("Financial / Health") beside the count | At accessibility sizes (iOS `isAccessibilitySize`; Android the existing `accessibilityText` threshold, 1.3 × 1.15 font scale) the count moves **under** the title; scaling stays on, nothing fixed-size | Android, iOS |
| 1/3 | Title → "TTM" → "What to keep in mind" | Shared period ("TTM", "Periods differ") on its own line between the 48 dp / 44 pt header and the notes control | Period sits under the title **inside** the header button (one 2-line target instead of a target + a line); the spoken label includes the full period (`ComparisonDisplay.sectionLabel`) | Android, iOS |
| 3 | Consecutive metrics | `xs` above and below each metric (+ divider) | `xxs` above, `xs` below (the 48 dp / 44 pt title row already gives room) | Android, iOS |
| 2 | Column alignment | Verified in code and the closure captures: header and value columns use the same horizontal padding (`cardPadding`), gap (`xs`) and equal weights, so AAPL/MSFT/NVDA/TSLA start exactly over their values; stacked fallback and section-based header placement unchanged | no change needed | — |
| 4 | Sample data strip | Full-strength strip: `label` (13, medium) in `textBody` on the SAMPLE fill | Quieter: `caption` (12) in `textSecondary` (≥ 7:1 on the fill, light and dark) + hairline `warningBorder` top edge; shared `StockSemanticStyles.sampleStrip`. Still shown whenever the backend reports MOCK (existing single check), never for REAL; placement above the tab bar / filling the home-indicator strip unchanged | Android, iOS |
| 5 | Share price legend | Android: text glyphs ("— AAPL", "- - MSFT", "· · NVDA", "-·- TSLA") in the series colours above the chart; **MSFT and "Start (100)" both "- -" in the same slate**; NVDA and TSLA in near-identical oranges (`caution` E87924 vs `educationAccent` E07B1F); orange caption text ≈ 3:1. iOS: Swift Charts' automatic colours (different from Android) and a separate change row | Shared `StockChartSeriesStyle` (blue solid · slate dashed · orange dotted · indigo `learnAccent` dash-dot; reference = thin muted long dash) drives the lines **and** a drawn swatch per item. One legend under the chart: swatch + ticker + change ("No data" when missing), then "▬ Start (100)"; labels in readable text colours. Tooltips, period pills, normalisation and gaps unchanged | Android, iOS (Compose `StockTrendChart` legend also used by the history chart and Earnings EPS chart) |
| 6 | "More metrics (26)" | Button only | Same button, live count and toggle (`ComparisonDisplay.moreMetricsLabel`) + one caption while collapsed: "Optional, more advanced measures. The sections above cover the basics." | Android, iOS |

## 2. Files changed

- Core: `screener/ComparisonDisplay.kt` (`moreMetricsLabel`, `MORE_METRICS_NOTE`, `sectionLabel`); test `ComparisonDisplayTest.kt` (+1).
- Shared app: `theme/StockSemanticStyles.kt` (`sampleStrip`, `StockChartSeriesStyle`); `designsystem/components/StockTrendChart.kt` (shared styles,
  swatch legend, `StockChartSwatch`, `StockChartLegendItem`); `designsystem/components/StockSampleDataBanner.kt`;
  `presentation/screener/ComparisonScreen.kt`; test `theme/ResponsiveLayoutRulesTest.kt` (+1).
- iOS: `ScreenerScenes.swift`, `ContentView.swift` (`SampleDataBanner`), `DesignSystem/StockComponents.swift` (`StockChartSwatch`, chart style helpers).

## 3. Tests and builds (actual, 2026-10-12; outputs cleaned, one sequential Gradle command, then the default `xcodebuild`)

| Check | Result |
|---|---|
| Core JVM | **467 / 0 failed / 0 skipped** |
| Core iOS simulator | **467 / 0 / 0** |
| Shared Android host | **114 / 0 / 0** |
| Shared iOS simulator | **110 / 0 / 0** |
| `:app:androidApp:assembleDebug` | OK |
| iOS `xcodebuild` (generic simulator, unsigned) | **BUILD SUCCEEDED**, no warnings in changed files |

## 4. Not done / known issues

- **No screenshots and no device validation in this pass** (Android/iOS, light/dark, 2 vs 4 companies, standard/AX text, narrow screens,
  expanded/collapsed, compact/stacked, banner/safe areas, legend, More metrics) — awaiting approval to use the emulator and simulators.
- TalkBack, VoiceOver, physical devices: NOT TESTED (emulator still shows the Accessibility Suite notification-permission dialog for the owner).
- iOS **history** chart (`ComparisonHistoryViews.swift`) still uses Swift Charts' automatic colours/legend; Android's history chart got the new
  swatch legend through `StockTrendChart`. Follow-up: apply `StockChartSeriesStyle` there too.
- Ticker avatars in stacked lines stay a fixed size at AX5 (decorative; the ticker text scales).
- The new legend palette changes the 4th Compose series colour (`educationAccent` → `learnAccent`) wherever `StockTrendChart` draws ≥ 4 series
  (only Compare in practice).

## 5. "What the numbers show" refinement (2026-10-12, same commit)

| Before | After |
|---|---|
| Every server observation as a bullet ("• Among these 4 companies, net margin ranges from 7.3% (Tesla, Inc.) to 53.0% (NVIDIA Corporation).") with its caveat underneath — the same "Reporting periods end on different dates (AAPL 2025-09-30, …)." repeated under most items and "Past growth doesn't indicate future growth." twice; disclaimer at the end | Header "What the numbers show" + "Based on the N companies selected" (live count). **Three items by default**, one per distinct topic in order growth → profitability → valuation → financial health → dividends (first available metric per topic). Each = short heading ("Revenue growth", "Net margin", "Trailing P/E") + concise text with tickers ("Ranges from 0.9% (TSLA) to 43.4% (NVDA).", "… · 3 of 4 companies report it." when some lack a value) + a metric-specific caveat only if not already shown. **"View all N insights" / "Show fewer insights"** (48 dp / 44 pt, Expanded/Collapsed state) reveals the rest. One shared note: "Reporting periods can differ by company. These describe reported figures. They aren't rankings or recommendations." |

- Shared, presentation-only transform `ComparisonObservationsDisplay.view(observations, columns)` (core `ComparisonDisplay.kt`) over the server's
  `ComparisonObservation`s: no value computed, re-ranked or invented; no API or engine change; unexpected sentence shapes are shown unchanged
  (names → tickers only). Missing data stays as the server states it; company counts come from the observation text and `columns.size`.
- Android `ObservationsCard` (`ComparisonScreen.kt`), iOS `observationsCard` (`ScreenerScenes.swift`): theme tokens, `StockCard` / `.stockCard`,
  dividers between items, one spoken label per item (heading, text, caveat), text wraps at every size (iOS on paired Dynamic Type curves).
- Not adopted from the reference: per-item icons (not needed; would add new icon mapping) and "highest/lowest" winner wording (the engine
  deliberately states ranges, not winners).
- Tests: `ComparisonDisplayTest.observationsShowThreeDistinctTopicsWithOneSharedNote` (+1). Results (outputs cleaned): core JVM **468/0**,
  core iOS **468/0**, shared Android **114/0**, shared iOS **110/0**, `assembleDebug` OK, iOS `xcodebuild` **BUILD SUCCEEDED** (no warnings in
  changed files). **Device / screenshot validation NOT RUN** (needs approval); TalkBack / VoiceOver NOT TESTED.

## 6. Device validation (2026-10-12, local MOCK `:8081` only; never the LAN host)

| Device / setting | Checked | Result |
|---|---|---|
| Android emulator-5554, 360 dp (`wm size 1080x2400`), app Theme **Dark**, 1.0× | four companies: section headers (period inside), bars, collapsed/expanded sections, Valuation columns + pinned header, chart legend, More metrics note, insights collapsed/expanded, sample strip | OK. Header columns align with values (column edges within ~1 px). Legend: four distinct colour + dash swatches, "Start (100)" separate |
| same, **2.0×** (max) and 1.3× | headers, stacked lines, insights, selection header | Collapsed header stacks "1 metric" under the title; stacked lines, no pinned header; insights wrap; "Add a company" under "Companies (2 of 4)" (after fix) |
| same, app Theme **Light**, 1.0×, **two companies** | Growth/Profitability, insights (two-company wording, no period note because AAPL/MSFT period ends are < 120 days apart) | OK |
| iPhone 16 Simulator (iOS 18.3), Light, Large, two companies | top, section header, chart legend (shared styles), insights | OK |
| same, Dark, Large, four companies | bars, chart legend (vertical when the row doesn't fit) | OK |
| same, Dark, **AX5** | collapsed headers, notes control, insights, chart, selection header, sample strip | Fixed during the pass: chart start/end dates broke mid-date ("2025-10-0 / 7") → stacked "From … / To …"; selection title broke mid-word → "Add a company" stacks under it; sample strip didn't follow live size changes and grew to 2 lines → resolved from `dynamicTypeSize`, capped at the first accessibility size (persistent chrome, like the tab bar) |
| iPhone SE (3rd gen) Simulator (iOS 27), Light, Large, four companies | chart legend, insights collapsed/expanded | OK |

Captures: `docs/design/phase5c_final/` (25, incl. 4 `BEFORE-*` frames of the defects fixed in this pass). Devices restored: emulator size reset,
font scale 1.0, night mode yes, app Theme Light (as originally), `adb reverse` removed; iPhone 16 Light/Large; iPhone SE shut down; MOCK stopped.

Additional files changed by the device pass: iOS `ContentView.swift` (strip font from `dynamicTypeSize`, capped), `ScreenerScenes.swift` (chart dates,
selection header), Android `ComparisonScreen.kt` (selection header at accessibility text).

Known, not fixed (outside this pass): Swift Charts y-axis labels grow very large at AX5; app text built with `StockStepsTheme.font` doesn't
re-measure on a **live** Dynamic Type change until the screen is rebuilt (leaving and reopening Compare fixes it); Android bottom-tab labels
truncate at 2.0× ("Mark…"); TalkBack / VoiceOver / physical devices NOT TESTED; iOS history chart still uses automatic Swift Charts colours.
