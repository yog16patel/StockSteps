# Company Comparison — accessibility & content-density closure (+ insights card visual refinement)

Status: **committed and pushed** as "Close Company Comparison accessibility pass with an icon-led insights card, collapsible chart help and sources, readable tab labels at large text and live Dynamic Type fixes on Android and iOS" (2026-10-12), on top of `53c0044` "Polish Company Comparison with shared chart legend styles, concise What-the-numbers-show
insights, a quieter sample strip and large-text fixes on Android and iOS" (pushed). Two owner briefs in one pass: *Final Accessibility &
Content-Density Refinement* and *Insights Card Visual Refinement*. UI only: no API, engine, formula, data model, navigation, selection, monetisation
or MOCK/REAL detection change. Device captures were taken after the owner asked for screenshots (§6).

## 1. Initial findings (repository state verified)

- `git status` clean at `53c0044`; the brief's note "uncommitted on top of `92a6d6a`" was outdated (that polish pass is committed and pushed).
- Android tab bar (`designsystem/components/StockBottomNavigation.kt`, shared by every tab screen): five equal-weight items, label = `caption`
  (12 sp) with `maxLines = 1` + `TextOverflow.Ellipsis`, no item padding, fixed min height (`bottomBarHeight`), insets applied once. At 360 dp × 2.0×
  "Watchlist"/"Portfolio" exceed the ~64 dp per tab → "Mark…", "Portf…", "Watc…" (captured in `docs/design/phase5c_final/`).
- Share price card: a 3-sentence intro plus 4–5 server notes (`ScreenerService` performance notes) always visible.
- Sources: 3–6 server notes + "Comparison prepared YYYY-MM-DD HH:MM UTC." + disclaimer as a dense always-visible block; retrieval dates as
  "AAPL 2026-10-07, MSFT 2026-10-07, …".
- iOS: Swift Charts y-axis labels follow Dynamic Type without limit (AX5 crowding); `StockStepsTheme.font` resolved sizes via `UIFontMetrics` without
  explicit traits (can use a stale category during a live change); history chart used automatic Swift Charts colours.
- Insights card: text-only rows of equal weight; expanded state = eight similar paragraphs.

## 2. Changes

### Shared (core / KMP)
- `screener/ComparisonDisplay.kt`
  - `ObservationIcon` (GROWTH, PROFITABILITY, VALUATION, OPERATING, DEBT, DIVIDEND, CASH, GENERAL) per observation **metric id** (never from display
    text); `ObservationItem.icon`.
  - Primary insights titled by category ("Revenue growth", "Profitability", "Valuation", "Financial health", "Dividends & cash flow"); with a category
    title the text names the metric ("Net margin ranges from 7.3% (TSLA) to 53.0% (NVDA)."). Additional observations titled by metric, in topic order;
    free cash flow moved to the "Dividends & cash flow" topic. Footer "Reporting periods may differ. These observations aren't investment
    recommendations." (first sentence only when the engine flagged differing periods). `MORE_TITLE` = "Additional observations".
  - `ComparisonNotesDisplay`: `CHART_SUBTITLE`, `CHART_HELP`, `SOURCES_TITLE`, `DISCLAIMER`; `chartGroups(notes)` → How the comparison works / What is
    excluded / Important limitations / Data methodology; `sourceGroups(notes, asOf)` → Reporting periods / Metric definitions / Data sources / Sample
    data, with readable dates ("Financial statements retrieved Oct 7, 2026 for all 4 companies.", or one line per company when dates differ). Notes are
    sorted by their known openings (fixed sentences from this repo); unknown notes stay verbatim in the last group; empty groups are omitted.
- `theme/StockSemanticStyles.kt`: `insightAccent(icon, palette)` (growth/cash `positive`, profitability `primary`, valuation `learnAccent`, operating
  `caution`, debt `negative`, dividends `warning` — category identity only); `StockNavigationLayout.labelScale(widestAtStandard, labelWidth,
  fontScale)` (tab-label rule, §3).

### Android (Compose)
- `StockBottomNavigation.kt` + `AppNavigation.kt` (passes the five labels): measured tab-label scale via `StockNavigationLayout`; labels never
  ellipsized (`Clip` is unreachable by construction); icons-only mode keeps the tab name as the content description; `xs` item padding; five tabs,
  selection state, `Role.Tab`, 56 dp height and insets unchanged.
- `ComparisonScreen.kt`: `ObservationsCard` (header + lightbulb, `InsightRow` primary ×3 with 24 dp icons and indented dividers, toggle, compact
  `surfaceSecondary` "Additional observations" panel with 20 dp icons and no dividers, footer); `CollapsibleNoteGroups` (48 dp control,
  Expanded/Collapsed state, headings per group) used for "How to read this chart" and the "Sources & methodology" card; chart card shows the
  one-line subtitle, range pills, chart, swatch legend, company notes/errors, then the help control. Education line stays visible under Sources.
- `StockIcons.kt`: Material `BarChart`, `Speed`, `Layers`, `Percent` path icons (valuation uses the existing `Tag`; no balance-scale icon in the set).

### iOS (SwiftUI)
- `Theme.swift`: `StockStepsTheme.font` scales against `UIApplication.shared.preferredContentSizeCategory` explicitly.
- `ScreenerScenes.swift`: same insights card (SF Symbols `chart.line.uptrend.xyaxis`, `chart.bar.fill`, `tag.fill`,
  `gauge.with.dots.needle.33percent`, `square.3.layers.3d`, `percent`, `dollarsign.circle.fill`, `lightbulb.fill`; expand animation off with Reduce
  Motion); `noteGroups` for chart help and Sources; y-axis ticks capped at xxxLarge with 3 ticks at accessibility sizes; the screen and its sub-views
  (`GuidedMetricExplanationView`, `ComparisonLearningSummaryView`) depend on `dynamicTypeSize` so they re-render on a live change.
- `ComparisonHistoryViews.swift`: `chartForegroundStyleScale` / `chartLineStyleScale` from `StockChartSeriesStyle` (+ reference style) — the legend
  follows.

## 3. Bottom navigation decision and trade-off (P0)

Options evaluated: (a) two-line labels — no effect, the labels are single words; (b) horizontal scrolling — hides destinations, rejected; (c) icons
only at every accessibility size — loses visible names for everyone who can still fit them; (d) **chosen**: labels follow the user's font scale while
the widest fits its tab, then stop growing at the largest scale that fits (never below the standard size, so not "shrinking until it fits"); icons
only, with names for screen readers, only when even the standard size can't fit. Trade-off: at 2.0× on 360 dp the labels are larger than standard
but smaller than 2.0× — the same behaviour as the iOS system tab bar (whose labels don't scale). Touch targets stay the full tab (≥ 48 dp × 56 dp).

## 4. Before → after (behaviour)

| Area | Before | After |
|---|---|---|
| Tab labels at 2.0× | "Mark…", "Portf…", "Watc…" | Whole words at the largest scale that fits; never an ellipsis |
| Share price card | 3-sentence intro + 4–5 notes always visible | One line ("Compare price movements from the same starting point (100). Dividends are not included.") + range, chart, legend; notes grouped behind "How to read this chart" (collapsed) |
| Sources | Dense always-visible block, raw ISO dates | "Sources & methodology" card (collapsed) with 4 titled groups and readable dates; disclaimer still visible |
| Insights | Eight equal text rows | Header with lightbulb; three primary rows with coloured category icons; "View all N insights" reveals a compact "Additional observations" panel; one footer |
| iOS AX5 axis | Very large tick labels squeezing the plot | Capped at xxxLarge, 3 ticks; data still in the accessibility label and legend |
| iOS live text size | Some text kept the old size until the screen was rebuilt | Theme resolves the current category; Compare views re-render on the change — **not yet verified on a device** |
| iOS history chart | Automatic Swift Charts colours | Shared series colours and dash patterns |

## 5. Tests and builds (actual)

Commands (core has no `testAndroidHostTest`; its JVM suite is `:core:jvmTest`):
1. Outputs cleaned → `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
   :app:androidApp:assembleDebug --continue` → exit 0: core JVM **470 / 0 failed / 0 skipped**, core iOS **470 / 0 / 0**, shared Android **116 / 0 / 0**,
   shared iOS **112 / 0 / 0**, `assembleDebug` OK.
2. After the fixes found on the devices (history header, help refresh, shared-component refresh): shared outputs cleaned →
   `./gradlew :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug` → exit 0: **116 / 0 / 0** and **112 / 0 / 0**,
   `assembleDebug` OK (core unchanged since run 1).
3. `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator'
   CODE_SIGNING_ALLOWED=NO build` → **BUILD SUCCEEDED**, no warnings in changed files (plus the localhost-URL variant for device checks).

New/updated tests: `ComparisonDisplayTest` — `observationsShowThreePrimaryInsightsAndCompactAdditionalOnes` (3 primary, categories/icons, 5 additional
in topic order, no duplicates, total 8, missing dividend values not counted as zero, footer), `twoCompanyObservationsKeepBothValuesAndTheCount`,
`chartHelpAndSourcesKeepEveryNoteInTheRightGroup`; `ResponsiveLayoutRulesTest.tabLabelsGrowWithTextUntilTheyStopFittingButNeverEllipsizeOrShrinkBelowStandard`;
`StockIconsTest.insightCategoryIconsBuild`. Expansion state is local UI state (`rememberSaveable` / `@State`) — no request is made on toggle (no
presenter call in the handlers; covered by code review, not by a test).

## 6. Visual verification (performed 2026-10-12 on the owner's request; local MOCK `:8081` only)

Captures in `docs/design/phase5c_a11y/` (14, all *after* the changes):

| # | Device / setting | Shows |
|---|---|---|
| 01–02 | Android emulator, 360 dp, Light, 1.0×, 4 companies | Insights collapsed (lightbulb, three primary rows with icons, "View all 8 insights", footer, collapsed Sources); expanded with the "Additional observations" panel (gauge / layers / percent / dollar icons) |
| 03 | same | Sources & methodology expanded (Reporting periods, Metric definitions, Data sources with "Financial statements retrieved Oct 7, 2026 for all 4 companies.", Sample data); education line still visible |
| 04–05 | same | Share price card: one-line intro, pills, chart, swatch legend, "How to read this chart" collapsed; expanded into four groups |
| 06 | same, **2.0×** | Tab labels as whole words ("Markets", "Portfolio", "Watchlist") — no ellipsis; insights wrap |
| 07–11 | iPhone 16 (iOS 18.3), Dark, Large, 4 companies | Insights collapsed / expanded; Sources expanded; chart compact; chart help expanded |
| 12 | same, **AX5** (switched live) | Y-axis: three small ticks, plot keeps its width; dates stacked "From … / To …"; legend readable |
| 13 | same, AX5 switched **live** with chart help open | Help groups re-render at the new size without leaving the screen (after the `.id(typeSize)` fix found during this check) |
| 14 | same, AX5 | "Historical comparison" title no longer breaks mid-word; "1Y free" badge stacked under it |

Found and fixed during the device pass: open help groups kept their old text size after a live Dynamic Type change (fixed); "Historical comparis / on"
mid-word break at AX5 (fixed on iOS; same rule applied on Android at accessibility text); shared `StockNavigationRow` / `StockSectionHeader`
("More tools") didn't refresh live — now depend on `dynamicTypeSize` (**build-verified only**, not re-captured).

Not covered by the captures: Android dark, Android 1.3×, two-company layouts on this build, iPhone SE, iOS light, the sample strip compared across
themes on this build (unchanged code from `53c0044`, captured there), TalkBack, VoiceOver, physical devices.

## 7. Remaining issues

- Before closure: the uncaptured cells of the brief's matrix (Android dark / 1.3× / two companies, iPhone SE, iOS light); TalkBack and VoiceOver
  (not tested); physical devices (not tested).
- Optional: valuation icon is a price tag (no balance-scale vector in `StockIcons`); the live Dynamic Type fix covers Compare's views and the shared
  section header / navigation row — other screens' custom views built with `StockStepsTheme.font` may still need a re-render on a live change;
  at Android 2.0× the insight icons stay 24/20 dp while text grows (aligned to the title, not scaled); insights card has no expand animation on Android (state
  change only, no flicker); the 2.0× tab labels are capped (trade-off in §3).

## 8. Recommendation

**PASS WITH CONDITIONS (provisional).** Every item in both briefs is implemented on Android and iOS with shared, tested rules; all suites and both
builds pass; 14 device captures show the intended layout with no clipping, overlap, ellipsized tabs or mid-word breaks in the states checked, and the
two defects the captures revealed were fixed. Conditions before closing Company Comparison: capture the remaining matrix cells (§6), TalkBack and
VoiceOver passes, and the owner's visual sign-off against the insights reference.
