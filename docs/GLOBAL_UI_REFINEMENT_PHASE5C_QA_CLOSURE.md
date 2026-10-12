# Company Comparison — final polish & QA closure

Status: **committed and pushed** as "Finish Company Comparison QA with a two-column chart selection summary, scannable chart limitations, heading fit at large text and live Dynamic Type for buttons on Android and iOS" (2026-10-12), on top of `85e24a0` "Close Company Comparison accessibility pass with an icon-led insights card, collapsible
chart help and sources, readable tab labels at large text and live Dynamic Type fixes on Android and iOS" (pushed; working tree was clean at start).
UI only: no business logic, formula, API contract, selection behaviour or data-source detection change. Device checks on the local MOCK server only.

## 1. Already implemented before this pass (verified, not redone)

Four-group "How to read this chart" and collapsed "Sources & methodology" (`ComparisonNotesDisplay`), swatch legend (`StockChartSeriesStyle`), iOS
AX y-axis cap and stacked chart dates, iOS history header badge stacking, Android tab labels at 2.0× (`StockNavigationLayout`), live Dynamic Type for
Compare views and the shared header/navigation row, icon-led insights card.

## 2. Changes in this pass

| Area | Before | After | Where |
|---|---|---|---|
| Selected-date summary | Android: one line "2026-10-07 · AAPL 131.3 · MSFT 101.1 · NVDA 128.3 · TSLA 87.2"; iOS: none | Date on its own line ("Oct 7, 2026"), then **swatch · ticker · indexed value** per company in **two columns** (one column at accessibility text); missing = "No data". Same values and formatter as before. Android keeps touch/drag selection; iOS gains `chartXSelection` (press-and-drag), keeps the last selected day after release and marks it with a thin rule line, as Compose does | core `ComparisonChartDisplay` (`selection`, `defaultIndex`, `selectionColumns`); Compose `StockTrendChart(selectionContent:)` + `ChartSelectionView`; SwiftUI `chartSelectionView` |
| Chart explanations | "Important limitations" was one long sentence | Split into two scannable points ("Share price moves aren't the same as business performance." / "Past price moves don't predict future returns.") — only when the server sentence matches exactly; otherwise kept verbatim. Index-100 start, dividends/total return, split adjustment, holiday carry and sample-data notes unchanged | `ComparisonNotesDisplay.chartGroups` |
| Screen-reader labels | Insight labels read "(NVDA).. Past growth …" (doubled full stop) | "Title. Text. Caveat." without doubled stops | `ObservationItem.spoken` |
| iOS live Dynamic Type | `.stockPrimary` / `.stockSecondary` button labels ("More metrics (26)") kept the old size after a live change | Shared `StockButtonBody` depends on `dynamicTypeSize` → re-renders | `DesignSystem/StockControls.swift` |
| iOS AX5 headings on 375-pt phones | "Shareholde / r Returns", "comparison / ?" broke mid-word | New shared rule `StockHeadingFit.scale`: a heading shrinks **only** when one word can't fit its line (8 % measuring margin, floor 0.6); every other heading keeps the user's size (hierarchy intact). Widths come from the header row and chevron, which don't depend on the title font (an earlier version measured the title itself and fed back on itself — `BEFORE-…-feedback-loop`) | `theme/StockSemanticStyles.kt` (+ test); `StockStepsTheme.fittedHeadingFont`; section headers and "What can we learn…" |

## 3. Files changed

- Core: `screener/ComparisonDisplay.kt` (`ComparisonChartDisplay`, `ChartSelection`, limitations split, `spoken` fix); test `ComparisonDisplayTest.kt`
  (+1 `chartSelectionListsEveryDrawnCompanyWithItsLineStyle`, updated chart-help and spoken expectations).
- Shared app: `designsystem/components/StockTrendChart.kt` (optional `selectionContent`), `presentation/screener/ComparisonScreen.kt`
  (`ChartSelectionView`), `theme/StockSemanticStyles.kt` (`StockHeadingFit`), test `theme/ResponsiveLayoutRulesTest.kt` (+1).
- iOS: `ScreenerScenes.swift` (selection view, sticky selection + marker, heading fit), `DesignSystem/StockComponents.swift`
  (`fittedHeadingFont`), `DesignSystem/StockControls.swift` (live type in buttons).
- Docs: this file; screenshots `docs/design/phase5c_qa/` (18).

## 4. Tests and builds (actual)

Outputs cleaned → one sequential command `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest
:app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` → exit 0; then `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp
-sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`.

| Check | Result |
|---|---|
| Core JVM | **471 passed / 0 failed / 0 skipped** |
| Core iOS simulator | **471 / 0 / 0** |
| Shared Android host | **117 / 0 / 0** |
| Shared iOS simulator | **113 / 0 / 0** |
| `:app:androidApp:assembleDebug` | OK |
| iOS `xcodebuild` (default) | **BUILD SUCCEEDED**, no warnings in changed files |
| `:server:test` | not run (no server change) |

Expanding/collapsing sections and selecting chart days only change local UI state (`rememberSaveable` / `@State`); no handler calls a presenter or
client — **verified by code review**. A network-level count was not possible: the MOCK server logs only a periodic usage summary, not each request.

## 5. Device and accessibility verification

| Platform / case | Result | Evidence |
|---|---|---|
| Android 360 dp **light 1.0×**, 4 companies: selection default, after tap, chart help | PASS | 01–03 |
| Android light **1.3×**, 4 companies: two-column selection, legend, history header wraps between words | PASS | 04 |
| Android **dark 2.0×**, 2 companies: one-column selection, whole-word tab labels, insights expanded | PASS | 05–06 |
| Android **TalkBack**: labels and reading order of the insights card (accessibility tree via `uiautomator`: title, subtitle, one label per insight, toggle, footer, sources, disclaimer) | PASS (tree) | dump in this session; doubled full stop found and fixed |
| Android **TalkBack** live navigation (swipe / keyboard next, double-tap activation, spoken output) | **NOT VERIFIED** — TalkBack ran (focus box rendered, 07) but injected gestures and Alt+→ didn't move its focus on the emulator; spoken text isn't logged once TTS is ready | 07 |
| iPhone 16 **light Large**, 4 companies: selection grid | PASS | 08 |
| iPhone SE (iOS 27) **light Large**, 3 companies: 2 + 1 grid; selection kept after release | PASS | 09–10 |
| iPhone SE **dark**: selection marker | PASS | 11 |
| iPhone SE dark **AX5**, switched live: one-column selection keeps the selected day; "More metrics" button, "More tools" rows, history header refresh; headings fit without mid-word breaks | PASS (after the fixes in §2) | 12–16, `BEFORE-*` |
| iOS **VoiceOver** | **NOT VERIFIED** — not available in the Simulator; needs a physical device | — |
| Physical devices | **NOT VERIFIED** | — |
| Android 4-company 2.0× light, iPhone 16 dark AX5 in this build | Not re-captured (covered by the previous pass's captures `phase5c_a11y/` for unchanged areas) | — |

Device state restored: emulator size reset, font scale 1.0, night mode as before, TalkBack off, app Theme Light, `adb reverse` removed; iPhone 16
Light/Large; iPhone SE Light/Large and shut down; MOCK server stopped. (The TalkBack notification permission had already been granted by the owner.)

## 6. Visual consistency review

Typography and colours come from shared tokens; the selection grid reuses the legend swatches; card padding and section spacing unchanged; insights
card, icons, panel and footer unchanged; the education disclaimer stays visible under Sources; chart explanations no longer repeat the subtitle's
dividend line except under "What is excluded" (kept as the material disclosure).

## 7. Remaining issues

- Blockers before full closure: TalkBack live navigation and VoiceOver passes on physical devices (environment limitation, NOT VERIFIED).
- Backlog: Android has no equivalent of the iOS heading-fit rule (no mid-word break observed at 360 dp × 2.0×; would matter only on narrower
  windows); iOS chart selection is press-and-drag (system behaviour) while Android also selects on a tap; the "What can we learn…" heading measures
  its own width (no feedback observed, but the section-header approach is the safer pattern if it ever shows one).

## 8. Recommendation

**PASS WITH CONDITIONS.** All requested refinements are implemented on both platforms with shared, tested rules; tests and builds pass; the
representative light/dark, standard/large-text, 2/3/4-company, small-phone and live Dynamic Type states were checked on devices and the defects they
revealed were fixed. Conditions: TalkBack navigation and VoiceOver are NOT VERIFIED (need physical devices).
