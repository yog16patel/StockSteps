# Portfolio UI Refinement — Phase C.1: visual and accessibility closure

Status: **closure verification done 2026-10-12, verdict READY TO CLOSE** (§8; closure fixes committed and pushed as "Close Portfolio Phase C.1 verification with pill selectors, inset transaction dividers, vertically scrollable trend charts and large-text chart and selector fixes on Android and iOS"). Implementation committed and pushed as "Close Portfolio visual and accessibility pass with group stacking, grouped transaction rows, single headings and large-text insights fixes (Portfolio Phase C.1) on Android and iOS" (2026-10-12), on top of `8a86e4e` (Phase C). **Phase D is not started.**

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

## 8. Final verification and closure (2026-10-12, committed)

Device run on the local MOCK backend only (`:server:runMock`, `adb reverse`, ad-hoc signed iOS MOCK build launched with `--firebase-emulators`; no Auth
emulator started, no sign-in, no credentials). Data: Android ⋯ → "Mixed currencies"; Insights → "Diversified"; iOS the same through the cached
iPhone 16 session. Evidence: 30 screenshots in `docs/design/portfolio_pc1v/` (`a-*` Android 360 dp, `i16-*` iPhone 16; `*-before` = defect found in this
pass, `*-after` = after its fix; all other files show the current state).

### 8.1 Verification matrix

| Setup | Overview | Holdings + allocation | Holding details | Transactions | Insights / health | Cards + learn links | Result |
|---|---|---|---|---|---|---|---|
| Android 360 dp light 1.0× | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | PASS after fixes F1, F2, F7 |
| Android 360 dp light 1.3× | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | PASS after fixes F3, F4 |
| Android 360 dp dark 2.0× | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | PASS after fix F5 |
| iPhone 16 light Large | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | PASS after fixes F4, F6 |
| iPhone 16 dark AX5 (live switch from Large) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | PASS after fixes F4, F6, F8, F9, F10 |
| iPhone SE light AX / dark AX5 | — | — | — | — | — | — | **EXCLUDED by owner** (2026-10-12). Partial run before exclusion: guest state only (a fresh SE install has no session and guests get no sample scenarios); found and fixed F11 |

"Transactions" = the dashboard and holding-details transaction groups (there is no Transactions destination yet; that is Phase D).

### 8.2 Reported C.1 issues re-checked

| # | Issue | Result | Evidence |
|---|---|---|---|
| 1 | One metric-row stacking decision per group | PASS (both) | `a-dark2x-holding-details-metrics`, `a-light13x-holding-details`, `i16-ax5-overview-statusbar` |
| 2–3 | Allocation legend/donut responsiveness | PASS (both) | `a-dark2x-allocation-legend`, `i16-ax5-allocation-legend` |
| 4–5 | Holdings title/sort, structured holding rows, no mid-word ticker breaks | PASS (both) | `a-dark2x-holdings`, `i16-ax5-holdings` |
| 6 | Grouped transaction rows | PASS after F2/F6/F8 | `*-transactions-*` |
| 7 | "Dividends received" wraps between words | PASS (ACCEPTED as before) | `a-dark2x-transactions-divider-after` (top) |
| 8–9 | One heading on Holding details / Portfolio insights | PASS (both) | `a-light13x-holding-details`, `i16-ax5-insights-menu-leading-after` |
| 10 | Android Insights scenario placeholder "My accounts" | PASS | `a-light1x-insights-header-after` |
| 11–13 | Insights health/contributors/allocation rows stack, tickers whole | PASS (both); allocation **selector** failed → F3/F4 | `a-dark2x-insights-contributors`, `a-light13x-insights-allocation-midword-before` |
| 14 | Insight cards: no icon/text overlap, icon above text at large text | PASS (both) | `a-dark2x-insight-cards`, `i16-ax5-insight-cards` |
| 15 | iOS learn rows leading-aligned, no hyphenation | **PASS (now re-captured)** at Large and AX5 | `a-light13x-insight-cards-learn-chevrons-after` (Android parity, F1) |
| 16 | iOS status-bar backdrop | PASS | `i16-ax5-overview-statusbar` |
| 17 | Scenario caption under the total | PASS (both) | `a-light1x-overview` |
| 18 | Insights sub-group headings | PASS (both) | `a-dark2x-insights-contributors` |
| 19 | MOCK strip / bottom navigation clearance | PASS (both, last item scrolls clear on every screen) | `a-dark2x-transactions-divider-after`, `i16-ax5-transactions-after` |
| 20 | iOS cached account "Insights couldn't be loaded" | Still DEFERRED (session state, not UI); text readable at AX5 | `i16-ax5-insights-menu-leading-after` |
| — | Scenario selector wrapping (sheet + Insights menu) | PASS (whole words); iOS **system** menus hyphenate at AX5 ("ac-count…", "curren-cy"), not app-controlled | — |
| — | Error-state readability | PASS: Portfolio offline banner (Large + AX5), Insights error | `i16-large-error-offline` |

### 8.3 Defects found in this pass and fixed (UI only, smallest change)

| ID | Platform | Defect (evidence) | Fix |
|---|---|---|---|
| F1 | Android | "How these numbers work" rows expanded on tap but looked like plain text (no affordance; iOS has a disclosure chevron) | `LearnLine`: rotating `StockIcons.ChevronDown` like "By currency", `Role.Button` |
| F2 | Android | Transaction dividers ran edge to edge while holding dividers are inset (`a-light13x-transactions-divider-before`) | Divider inset by `cardPadding` (as holdings) |
| F3 | Android | Insights allocation tabs broke words mid-word at 1.3× ("Holding/s", "Currenc/y") | `StockPillSelector` (shared `StockPillLayout`: equal width while labels fit, else fading scroll row) — same component as the index selector |
| F4 | iOS | System segmented pickers truncated "NASDAQ Com…" even at Large (`i16-large-insights-segmented-truncated-before`) | Index and allocation selectors → existing iOS `StockPillSelector` (Android parity) |
| F5 | Android (shared) | A vertical swipe starting on `StockTrendChart` did not scroll the page (`detectDragGestures` consumed it); at 2.0× the Insights chart filled most of the viewport | Scrubbing via `detectHorizontalDragGestures` (as `StockLineChart`). Affects every `StockTrendChart` caller, only by letting vertical swipes scroll |
| F6 | iOS | Same full-width transaction dividers (`i16-large-transactions-divider-before`) | Inset like holdings |
| F7 | Android | Insights meta line ("Sample · … · StockSteps+") ran into the "Sample scenario" field label | `padding(bottom = spacing.xs)` |
| F8 | iOS | AX5: the body-sized "⋯" glyph grew past its 44 pt frame to the card edge (`i16-ax5-transactions-ellipsis-before`) | Fixed-size symbol (`dims.icon`) inside the unchanged 44 pt target (same approach as C.1 #14) |
| F9 | iOS | AX5: Insights scenario menu label centred its wrapped lines (`i16-ax5-insights-menu-centred-before`) | Custom `Menu` label, leading-aligned |
| F10 | iOS | AX5 Insights chart: legend broke "Composi/te" mid-word and the y-axis labels ran into the legend (`i16-ax5-insights-chart-legend-before`) | Legend one series per line at accessibility sizes; 3 y marks and vertical padding at accessibility sizes. Axis labels keep their full size |
| F11 | iOS (shared component) | iPhone SE AX5 truncated the selector to "My Portfol…" | `StockSegmentedControl`: equal-width only while every label's longest word (+ compact icon) fits (shared `StockPillLayout.fitsEqualWidth`), else one full-width row per segment. Unchanged when labels fit (iPhone 16 Large re-checked) |

No text scaling was limited anywhere. No presenter, API, server, calculation, decimal, ledger, entitlement, fixture, route or navigation change.

### 8.4 Accessibility results

| Check | Result |
|---|---|
| No clipping / horizontal overflow | PASS after F3, F4, F8, F10 (Android 1.0/1.3/2.0×, iPhone 16 Large/AX5) |
| No mid-word ticker breaks | PASS (holdings, legends, insights rows, contributors) |
| No icon/label overlap | PASS after F8 |
| Safe areas / fixed banners | PASS (status-bar backdrop, MOCK strip, bottom navigation) |
| Touch targets ≥ 48 dp | PASS: uiautomator audit of every captured Android screen found 0 clickable nodes under 48 dp. iOS: 44 pt frames in code (transaction actions, learn rows, Refresh); no on-device iOS measurement tool |
| Headings / reading order | Android: `heading()` semantics in code; uiautomator node order follows the visual order. TalkBack navigation NOT VERIFIED |
| Chart / transaction descriptions | PASS: transaction rows read "Opening position, TD.TO · 5 shares, Sep 1, 2026, CAD 75.00 per share" + "Actions for …"; Insights chart description "Growth of 100 from … Your portfolio rose 8.3%; …". Finding (not fixed, pre-C.1 text): holding rows say "unrealized gain 200.00 (+25.00%)" without the currency → Phase E |
| Contrast / semantic colours | Visual check light + dark: gain/loss in words + sign + arrow; theme tokens only; no automated contrast measurement this pass |
| Live iOS Dynamic Type | PASS: Large → AX5 and dark switched live on a running app; screens re-laid out without relaunch |
| TalkBack / VoiceOver | NOT VERIFIED (TalkBack first start needs the owner to answer a permission prompt; VoiceOver needs a device) |

### 8.5 Remaining limitations (not fixed here)

- iPhone SE excluded by the owner; data-backed SE screens would also need a signed-in session (Auth emulator only with owner approval).
- Out of Portfolio scope, seen on the SE sign-in screen at AX5: "Stoc/kSteps?" and "Cre-ate ac-count" break mid-word (sign-in layout) → backlog.
- Bottom tab is not highlighted on Portfolio sub-destinations (Holding details, Insights). App-wide `AppNavigation` rule since the tracker was added (`187d130`), not C.1.
- Insight-card engine text shows raw amounts ("earned 4128.79", "subtracted 283.94") without currency or grouping (core engine copy; out of UI scope).
- iOS insight cards show no category eyebrow ("Benchmark · 1Y") while Android does (parity, pre-existing).
- Holding-row spoken gain amount lacks a currency (Phase E).
- System `Menu` hyphenation at AX5 (iOS-controlled).
- Insights segmented selector on Android now scrolls with a fade even at 1.0× on 360 dp (four labels don't fit equally), matching the index selector.

### 8.6 Tests and builds (this pass, sequential, clean test outputs)

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` | **BUILD SUCCESSFUL** (8 m 29 s) |
| core JVM / core iOS | **471 / 0** each |
| shared Android host | **169 / 0** |
| shared iOS simulator | **165 / 0** |
| `:app:androidApp:assembleDebug` | OK |
| default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED** |
| ad-hoc signed MOCK `xcodebuild` (device checks) | BUILD SUCCEEDED |
| `:server:test` | NOT RUN (no server change) |

No new unit tests: the fixes reuse already-tested shared rules (`StockPillLayout.fitsEqualWidth`) or are pure layout.

### 8.7 Exact changed files (closure pass)

- `app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/portfolio/PortfolioInsightsScreen.kt` (F1, F3, F7)
- `app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/portfolio/PortfolioScreen.kt` (F2)
- `app/shared/src/commonMain/kotlin/org/example/stocksteps/designsystem/components/StockTrendChart.kt` (F5)
- `app/iosApp/iosApp/PortfolioScreen.swift` (F6, F8)
- `app/iosApp/iosApp/PortfolioInsightsScreen.swift` (F4, F9, F10)
- `app/iosApp/iosApp/DesignSystem/StockSettingsComponents.swift` (F11)
- docs: this file, `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`, `docs/PROJECT_HANDOFF.md`; captures `docs/design/portfolio_pc1v/` (30 files)

Devices restored: Android size reset, font 1.0, night mode off, app Theme Light, `adb reverse` removed; iPhone 16 Large/light; iPhone SE shut down
(the MOCK build stays installed on it); MOCK server stopped.

### 8.8 Verdict

**READY TO CLOSE.** Every reported C.1 issue passes on Android (1.0/1.3/2.0×) and iPhone 16 (Large/AX5), and the 11 defects found in this pass are
fixed. All suites and both builds pass. Screen readers are NOT VERIFIED (deferred to Phase E), and the SE was excluded by the owner.
**Phase D can begin** once the owner approves it (new routes: `PortfolioEntryRoute(transactionId)`, the Transactions destination / Q4), now that
these closure fixes are committed.
