# Global UI Refinement Phase 5B — Screener ("Discover Stocks")

Status: **committed and pushed** as "Refine Discover Stocks with word-safe preset cards, a categorised filter editor, grouped result rows and clear empty and error states (Global UI Refinement Phase 5B) on Android and iOS" (2026-10-11). Base: `f08350c` "Close Global UI Refinement Phase 5A.1 with a re-verification pass and Phase 5B handoff" (pushed with it) on top of `bb7105e` (5A.1). UI/UX refinement only: no backend, API contract, filter meaning, entitlement,
navigation-destination or business-logic change; no new network requests. Comparison (Phase 5C) is untouched.

Visual reference: `docs/design/phase5b/REFERENCE-screener-discover-stocks.webp` (the image the owner attached; copied into the repository this session).
It is a direction, not a specification: its preset names ("Value Stocks", "High Growth", "Quality Companies"), sample prices, "+$3.04" dollar changes,
"Less than / Greater than" operators, "Dividends" category and a Screener bottom tab were **not** copied — see §3.

## 1. Before / after audit

| # | Area | Before (both platforms unless noted) | After |
|---|---|---|---|
| 1 | Header | "Discover Stocks" + long sentence; long in-page MOCK banner; iOS nav bar also said "Discover" | "Discover Stocks", "Find companies using filters that matter to you.", ? button → "How screening works". The MOCK disclosure is the app-wide bottom strip plus a compact **Sample** badge on the results count; iOS nav bar keeps only Back |
| 2 | Discover cards (known defect) | 2-column grid, title beside a 48 dp ⓘ button: at 360 dp / 1.3× "Compan/ies" broke mid-word; iOS fixed `minHeight: 96`, system fonts | Icon tile, title, one-line factual summary; **shared `StockCardGridLayout`** keeps two columns only while every title word fits one column, else one column; heights follow content (no fixed height, no smaller text). Exact criteria moved from the ⓘ to "How screening works" |
| 3 | First screen | Presets + hint text; Filters/Sort/Saved/Sign-in in a horizontally scrolling row with no cue | Presets + primary **Create custom screen** (opens the filter editor). No search runs until a preset or filters are chosen (unchanged behaviour, as in the reference's "no filters" state) |
| 4 | Filter controls | One long sheet: ~24 metrics × Min/Max fields, 24 "What's this?" buttons, attribute chips in hidden horizontal scrolls | Categories → filters in a category (current value shown) → one filter: explanation, ⓘ for the full sheet, **Any value / At least / At most / Between**, typed values with the unit in the label, period and missing-data rule. "Show results (N filters)" pinned at the bottom above the keyboard; Reset all at the top; presets also reachable at the top level |
| 5 | Applied filters | Range chips (✕) + attribute chips that only reopened the sheet; Reset all hidden at the end of a scroll | "N filters" pill (shows/hides the full list) + **Edit filters**; chips in the shared `StockFadingScrollRow` (fades while more are hidden); every chip — attributes too — removes its filter; expanded list has one 48 dp/44 pt ✕ per filter |
| 6 | Results header | Count + 3–5 stacked captions (universe, excluded, warnings, notes) | "24 companies match" + Sample badge + **Sort: Market cap ↓** menu (wraps under the count when narrow); one coverage line ("Checked all 150 companies · 4 left out for missing data"); one line on why values are shown + "What these numbers mean"; warnings and preset caveats kept |
| 7 | Result rows | One tall card per company: row + metric columns + notes + Compare chip + ⋮; iOS custom row without logo, one-line name; missing price "—", missing metric "N/A" | One grouped list (Watchlist pattern): `StockRow` with logo and two-line name; price + daily change beside or stacked by the **shared `StockQuoteRowLayout`**; shown metrics as wrapping label/value pairs; missing price/metric = **"No data available"** ("None" = no dividend stays); ⋮ = View details, Add/Remove comparison, Add to watchlist (signed in), Add to portfolio; selected rows show a **Comparing** badge |
| 8 | Why a company appears | Not stated | "Each company shows the values your filters checked: P/E ratio (trailing)…" (or "…for context" without range filters); rows matching filters add "Matches your filters." to the spoken label |
| 9 | Tools | Saved / Sign in buttons in the scrolling control row | "More tools" grouped `StockNavigationRow`s: Saved screens (signed in; "3 of 5 saved") or **Sign in to save screens** (guest), Compare companies (selected count), How screening works |
| 10 | States | Loading = progress bar; no results = one sentence + Reset all; error = inline text; **catalog error had no retry** (Android passed `onRetry = null`, iOS plain text) | Skeleton rows (no "0 companies match" while loading); empty = full state "No companies match your filters" + **Edit filters** + **Clear all filters**; error = full state "Couldn't load screener results" + Try again; catalog error = full state + **Try again** (`ScreenerPresenter.start()`, which reloads only when nothing loaded) |
| 11 | Compare bar | Android: overlay that could cover the last row; iOS `.bar` material | Below the list (Android) / bottom safe-area inset (iOS), surface colour, two-line symbols |
| 12 | iOS styling | System fonts, `.bordered` buttons, hard-coded `.red`, `Form` | Theme fonts/colours/corners, `.stockPrimary` / `.stockSecondary`, `StockTextField` (keyboard Done button), `StockStateMessage`, `StockNavigationRow`, `StockFadingScrollRow` |

## 2. Final hierarchy
1. Title + subtitle + ? (How screening works).
2. Not searched yet: preset cards (1 or 2 columns) → Create custom screen.
   Searched: filter bar (N filters / Edit filters) → applied filters (chips or expanded list) → results header (count, Sample, sort; coverage;
   explanation; caveats) → loading / error / empty state **or** grouped result rows (paged on scroll).
3. More tools (Saved screens or Sign in · Compare companies · How screening works).
4. Disclaimer ("Screens describe financial characteristics. They aren't recommendations to buy or sell.").
5. Compare bar while companies are selected. App-wide MOCK strip below everything (unchanged; never over the tab bar or the compare bar).

## 3. Decisions and conflicts with the reference (contracts preserved)
- **Bottom tab**: the reference shows a Screener tab; the five tabs stay Home | Markets | Portfolio | Watchlist | Learn (owner instruction, CLAUDE.md).
  Discover Stocks is still opened from Markets.
- **Preset names/descriptions** are the existing `ScreenerDefinitions` presets. The cards add a one-line factual summary (`ScreenerDisplay.presetSummary`,
  tested to contain no "good/safe/cheap/undervalued/best/buy"); the reference's "potentially undervalued" wording would break the no-advice rule.
- **Operators**: ranges are inclusive (`value < min || value > max` excludes), so "At least / At most / Between", not "Less than / Greater than".
- **Categories** follow the existing `MetricGroup`s: Company characteristics (market metrics + country/exchange/sector/industry when more than one value
  exists), Valuation, Growth, Profitability, Financial health, Shareholder returns. Dividend yield stays under Valuation where it is defined.
- **Daily change** is a percentage only: screener records carry `changePercent`, not a dollar change, so no "+$3.04" is shown (nothing is derived).
- **Compare** moved from a per-row chip into ⋮ (+ "Comparing" badge, compare bar and tools row); capability unchanged.
- **Metric explanations** moved from tapping a metric cell (which competed with opening the row) to "What these numbers mean" and the ⓘ in the editor.
- **Offline**: the Screener has no cached results (the presenter keeps none), so offline appears as the error state with Try again; nothing is
  labelled live or saved. No per-row offline labels exist.

## 4. Shared rules and helpers
- `theme/StockSemanticStyles.kt` — **new `StockCardGridLayout`** (`columns(widestTitleWord, availableWidth, gap, cardPadding, accessibilityText)`, `words(title)`):
  2 columns only when the widest title word ≤ (width − gap) / 2 − 2 × padding; always 1 at accessibility sizes or unmeasured width. Compose measures with
  `rememberTextMeasurer`, SwiftUI with `uiFont` + `NSString.size`. Tests in `ResponsiveLayoutRulesTest`.
- Reused unchanged: `StockQuoteRowLayout` (result values), `StockFadingScrollRow` (chips, iOS attribute choices).
- Core **new `screener/ScreenerDisplay.kt`**: title/subtitle/state copy, `matchLine`, `filterCount`, `coverageLine`, `resultsExplanation`, `presetSummary`,
  `presetCriteria`, `categories` (+ `FilterCategoryView`), operators (`operatorOf`, `bounds`, `firstValue/secondValue`, `inputUnit/inputText/parseInput`,
  `boundsError`), `activeFilters` (+ spoken text), `sortOptions/sortLabel/sortSpoken`, `metricItems` ("No data available", filtered flag).
- `ScreenerPresentation.kt`: `MISSING_PRICE`, `ScreenerRowView.priceAvailable`, spoken row label now says up/down/unchanged in words, "Price
  unavailable", "no data available" for missing metrics (was "Price —, — today", "N/A").
- `IosScreenerClient.rangeText` delegates to `ScreenerDisplay.rangeValue` (one formatter).

## 5. Components
Reused: `StockCard`, `StockRow` (`nameMaxLines`/`nameLineLimit` 2), `StockPriceChange`, `StockIconTile`, `StockStatusBadge` (SAMPLE, INFO), `StockTitleWithBadge`
(Compose), `StockButton` / `.stockPrimary` / `.stockSecondary`, `StockNavigationRow`, `StockSectionHeader`, `StockDivider`, `StockFadingScrollRow`, `StockChip` (iOS),
`StockTextField`, `StockEmptyState` / `StockErrorState` / `StockStateMessage`, `StockRowSkeleton`, `StockSkeleton`, `StockLoadingState`.
Modified: none of the shared components. `MetricInfoSheet` (Compose, also used by Comparison) now delegates to the new `MetricsInfoSheet` and scrolls; same content.
New screen-local pieces only (preset grid/card, filter bar, applied filters, results header, result row, tools, compare bar, filter editor pages, help sheets).

## 6. Files changed
- Core: `screener/ScreenerDisplay.kt` (new), `screener/ScreenerPresentation.kt`; test `screener/ScreenerDisplayTest.kt` (new, 13 tests).
- Shared: `theme/StockSemanticStyles.kt` (`StockCardGridLayout`); test `theme/ResponsiveLayoutRulesTest.kt` (+1); `presentation/screener/ScreenerScreen.kt`
  (rewritten), `presentation/screener/ScreenerFilters.kt` (new, filter editor), `presentation/screener/ScreenerScene.kt` (`onClearChoice`, `onRetryCatalog`);
  `iosMain/IosScreenerClient.kt`.
- iOS: `DiscoverStocksViews.swift` (new: screen, filter editor, help, saved screens), `ScreenerScenes.swift` (old Discover views removed; nav title).
- Docs: this file, `docs/design/phase5b/REFERENCE-screener-discover-stocks.webp`, handoff/status docs.

## 7. Android / iOS parity
Same order, copy, categories, operators, chips, sort labels, spoken labels and layout rules (shared Kotlin). Platform differences: Android edits filters
in one full-height bottom sheet with in-sheet pages (a back arrow; system Back closes the sheet — the draft is kept in the presenter, nothing is lost);
iOS uses a sheet with a `NavigationStack` (native back). Android sort and row actions are dropdown menus; iOS uses `Menu`. iOS attribute choices scroll
horizontally with the fade; Android wraps them (`FlowRow`; SwiftUI has no flow layout in the design system).

## 8. Tests and builds (this session, actual results)
One sequential command with test outputs cleaned first, then `xcodebuild`:
`./gradlew :core:cleanJvmTest :core:cleanIosSimulatorArm64Test :app:shared:cleanTestAndroidHostTest :app:shared:cleanIosSimulatorArm64Test :core:jvmTest
:core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` → **BUILD SUCCESSFUL in 9m 1s**.

| Suite | Result |
|---|---|
| Core JVM | **454 / 0 failed** (441 + 13 new `ScreenerDisplayTest`; existing `ScreenerPresenterTest` 4/0) |
| Core iOS simulator | **454 / 0** |
| Shared Android host | **110 / 0** (+1 `discoverCardsUseTwoColumnsOnlyWhenEveryTitleWordFits`) |
| Shared iOS simulator | **106 / 0** |
| `:app:androidApp:assembleDebug` | OK |
| iOS `xcodebuild` (generic simulator, unsigned) | **BUILD SUCCEEDED**, no warnings in changed files |

New tests cover: counts/coverage wording, why-values explanation, categories (existing groups, draft counts, comparison-only metrics excluded),
operator ↔ min/max mapping and pre-fill, display-unit parsing (billions, comma decimals, empty = no limit), active-filter text and spoken form, sort
labels, missing metrics never "N/A"/zero, spoken direction in words and missing prices, preset summaries free of advice words, empty vs failed states,
filter persistence/removal/reset, catalog retry, and the card-grid rule boundaries. `:server:test` not run (no server change).

## 9. Visual verification and screenshots
**NOT RUN** — device use needs the owner's approval, which was not given in this task. No screenshots exist in `docs/design/phase5b/` besides the reference
image. Planned (after approval, local MOCK only: `./gradlew :server:runMock`, `adb reverse tcp:8081 tcp:8081`, iOS build with
`STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` verified in the built Info.plist): the ten captures listed in the phase brief, including Android
360 dp 1.0×/1.3× preset cards ("Companies" unbroken), dark filters/results, iPhone 16 default/XXXL, compact iOS width, empty/error/missing-data rows and the
bottom of the page with the compare bar and MOCK strip.

## 10. Accessibility status
Implemented (code): one spoken element per result row (identity, price, change in words, shown metrics, "No data available", "Selected for comparison",
"Matches your filters"); chips speak "P/E ratio (trailing), at most 25.0×. Remove filter"; pill states Expanded/Collapsed; sort control speaks "Sorted by
market cap, high to low"; operators are radio buttons (Android `Role.RadioButton` + `selectableGroup`, iOS `.isSelected`); attribute chips state
Selected/Not selected; headings marked; touch targets 48 dp / 44 pt (pill, chips, ✕, ⋮, sort, radios); gain/loss also in words.
**TalkBack: NOT RUN. VoiceOver: NOT RUN. Large text on device: NOT RUN.**

## 11. Known limitations
- No device/screen-reader verification yet (above).
- Android system Back closes the whole filter sheet rather than stepping back a page (no shared `BackHandler` in the codebase).
- Switching operator keeps the first typed value (e.g. "At least 5" → "At most" becomes "At most 5"); the draft updates immediately, as before.
- Dollar daily change isn't available from screener data (percentage only).
- Phase 5A.1 conditions still open: TalkBack/VoiceOver speech, iOS signed-in Watchlist, iPhone SE at XXXL, mixed saved dates on device.

## 12. Verdict
**PASS WITH CONDITIONS** — every brief item implemented on both platforms with shared rules and tests; all suites and both builds pass. Conditions: device
screenshots, large-text and screen-reader checks are NOT RUN pending approval. Next: Phase 5C — Company Comparison, only after approval.
