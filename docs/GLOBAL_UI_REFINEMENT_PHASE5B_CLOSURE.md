# Global UI Refinement Phase 5B — Screener ("Discover Stocks"): closure verification

Date 2026-10-11. Implementation doc: `docs/GLOBAL_UI_REFINEMENT_PHASE5B.md`. Reference (directional): `docs/design/phase5b/REFERENCE-screener-discover-stocks.webp`.
Phase 5C brief: `docs/GLOBAL_UI_REFINEMENT_PHASE5C_BRIEF.md`.

## 1. Repository state
- Reviewed commit: `b1aa29c` "Refine Discover Stocks with word-safe preset cards, a categorised filter editor, grouped result rows and clear empty and
  error states (Global UI Refinement Phase 5B) on Android and iOS" on `main`, **in sync with `origin/main`**, working tree clean before this pass,
  `git diff --check` clean.
- The closure brief expected HEAD `f08350c` with 5B uncommitted; that was out of date — 5B was committed and pushed (with `f08350c`) at the owner's request
  in the previous turn. This pass's fixes and docs are committed and pushed as "Close Global UI Refinement Phase 5B with filter-input, range and accessibility fixes and add the Phase 5C Comparison brief" on top of `b1aa29c`.

## 2. Scope and acceptance criteria
Close 5B by code review, regression review, accessibility review (implementation level), automated tests and builds; fix only genuine closure defects;
device checks only with approval (not given in this pass). Criteria = `docs/GLOBAL_UI_REFINEMENT_PHASE5B.md` §11 of the brief (hierarchy, word-safe cards,
existing filters/sort/navigation intact, clear filter states, no overflow, adaptive values, missing ≠ zero, distinct states, semantic tokens, shared
responsive rules, large text, disclosures, unchanged contracts, tests/builds, honest PASS/FAIL/NOT RUN).

## 3. Implementation audit (code, both platforms)
| Item | Android | iOS | Notes |
|---|---|---|---|
| Header: title, subtitle, help | PASS | PASS | iOS bar keeps only Back |
| Preset cards 1/2 columns, no mid-word break (rule) | PASS | PASS after fix D4 | Shared `StockCardGridLayout` + test; device NOT RUN |
| Create custom screen | PASS | PASS | Primary, disabled until the catalog loads |
| Filter editor: categories, operators, units, explanations, missing-data rule | PASS after fix D1/D2 | PASS after fixes D1/D2/D3 | |
| Applied filters: chips, expanded list, edit, reset, single removal | PASS after fix D5 (a11y) | PASS | Attribute chips removable too |
| Results: count, coverage, sort, grouped rows | PASS | PASS | Count hidden while loading |
| Row actions: details, comparison, watchlist (signed in), portfolio | PASS | PASS | Row tap and ⋮ are separate targets |
| More tools: saved screens / sign in, compare, help | PASS | PASS | |
| Loading / empty / error / missing data / catalog retry | PASS | PASS | Empty and failed are different states (tested) |
| Compare bar | PASS (below the list) | PASS (bottom inset) | |
| MOCK disclosure + disclaimer | PASS | PASS | App-wide strip + Sample badge; disclaimer after results |
| Five tabs unchanged; Screener opened from Markets | PASS | PASS | No navigation code changed in 5B |
| No advice / no "live" or offline-cache claims | PASS | PASS | Preset summaries tested for advice words; no cached results exist |

## 4. Parity
Shared: all copy, categories, operators, chip/sort/spoken text (`ScreenerDisplay`), card-grid and quote-row rules. Platform-specific (by design): Android
filter editor = one sheet with in-sheet pages, iOS = sheet + `NavigationStack`; Android wraps attribute chips, iOS scrolls them with the fade; menus are
platform-native. iOS shows "Reset all" / "Close" only on the editor's first page (toolbar of the root page); Android shows Reset all on every page.

## 5. Responsive layout
- Preset cards: two columns only when the widest measured title word ≤ (width − gap) / 2 − 2 × 12; one column at accessibility sizes or unknown width.
  Titles are the existing presets ("Growing Companies", "Dividend Stocks", "Financially Strong", "Explore Valuations"); "Company characteristics" is a
  filter-category row (full width, wraps), not a card. Fix D4 made iOS draw titles with the same Dynamic Type curve it measures with.
- Result values: shared `StockQuoteRowLayout` (same thresholds as Watchlist); metric pairs wrap (Android `FlowRow`, iOS `ViewThatFits` → stacked).
- Filter bar and results header wrap onto two lines when needed (FlowRow / ViewThatFits); no truncated essential labels except chip text inside the
  fading row (the full text is in the expanded list and spoken label).
- Device confirmation at 360 dp 1.0× / 1.3×, iOS XXXL and compact width: **NOT RUN**.

## 6. Filter behaviour and data correctness
- Operators map exactly to the existing inclusive min/max (`ScreenerEngine`: `value < min || value > max` excludes); At most uses the single value as
  max; Between pre-fills min then max (tests).
- Units: percent as typed, multiples ×, money in billions (×1e9), counts plain; empty = no limit (never 0).
- Draft kept in the presenter across categories and sheet closes; removal of one chip clears that filter and searches once; Reset all clears and searches.
- Sorting/pagination unchanged (server cursor; Android near-end trigger, iOS last-row `onAppear`; rows de-duplicated by symbol).
- No dollar change is derived; missing price / metric = "No data available"; "None" = no dividend (from data); 0.00% only when the source says 0.

## 7. Accessibility (implementation level)
Headings marked (title, results count, help/sheet titles); one spoken element per result row with direction in words, missing values, comparison state
and "Matches your filters"; operators are radio buttons (Android `Role.RadioButton` in a selectable group, iOS `.isSelected`); attribute chips state
Selected / Not selected; filter pill Expanded / Collapsed; sort speaks "Sorted by market cap, high to low"; icon-only buttons are labelled (help, ⓘ, ✕,
⋮); targets 48 dp / 44 pt; colours only from theme tokens (light/dark contrast covered by existing `SemanticStylesTest` for badges/banners).
Gaps: loading/error/empty changes are announced only through the Android results-count live region; iOS posts no announcement (existing pattern).
**TalkBack: NOT RUN. VoiceOver: NOT RUN.**

## 8. Device screenshots
**NOT RUN** — the owner was asked for permission; none was given in this pass. No screenshots were created. The ten planned captures and the
procedure are in §11.

## 9. Automated tests and builds (this pass, actual)
Test outputs cleaned first, one sequential command, then `xcodebuild`:
`./gradlew :core:cleanJvmTest :core:cleanIosSimulatorArm64Test :app:shared:cleanTestAndroidHostTest :app:shared:cleanIosSimulatorArm64Test :core:jvmTest
:core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` → BUILD SUCCESSFUL in 9m 5s.

| Check | Result |
|---|---|
| Core JVM | **456 passed, 0 skipped, 0 failed** (454 + 2 new regression tests) |
| Core iOS simulator | **456 / 0 / 0** |
| Shared Android host | **110 / 0 / 0** |
| Shared iOS simulator | **106 / 0 / 0** |
| `assembleDebug` | OK |
| iOS `xcodebuild` (generic simulator, `CODE_SIGNING_ALLOWED=NO`) | **BUILD SUCCEEDED**, no warnings in changed files |
| `:server:test` | NOT RUN (no server change) |

## 10. Defects found and fixed
| # | Sev | Platform | Defect / reproduction | Root cause | Fix | Test |
|---|---|---|---|---|---|---|
| D1 | P1 | Both | Typing "100,000" for Trading volume (or "1,000" for Share price) filtered on 100 / 1 | `parseInput` replaced every comma with a decimal point | Thousands groups ("100,000", "1,250.5") drop the commas; a single comma without a dot stays a decimal comma ("2,5"); other shapes aren't guessed | `thousandsSeparatorsAreNotDecimalCommas` |
| D2 | P2 | Both | "Between 30 and 10" could be submitted; the server rejected it and the screen showed "Couldn't load screener results" with a Try again that repeated it | No client check before search | Shared `ScreenerDisplay.invalidRange(draft)`; "Show results" disabled with the reason shown above it | `invertedRangesAreReportedBeforeSearching` |
| D3 | P1 | iOS | On a single filter's page there was no "Show results" (Android has it on every page) | The pinned action was attached only to the first two pages | One `ShowResultsBar` pinned on all three pages (above the keyboard) | Build; device NOT RUN |
| D4 | P2 | iOS | Preset title measured with the `.body` Dynamic Type curve but drawn relative to `.headline` — measurement could drift from the drawn width at some sizes | Mismatched `relativeTo` | Title drawn with the token's own style (the one `uiFont` uses) | Build; rule tests |
| D5 | P2 | Android | Removable chips cleared their semantics after `clickable`, dropping the click action TalkBack uses | `clearAndSetSemantics` without re-adding `onClick` | `onClick(label = "Remove filter")` added in the semantics block (Watchlist pattern) | Compile; TalkBack NOT RUN |

Files: `core/.../screener/ScreenerDisplay.kt`, `core/src/commonTest/.../screener/ScreenerDisplayTest.kt`, `app/shared/.../presentation/screener/ScreenerScreen.kt`,
`.../ScreenerFilters.kt`, `app/iosApp/iosApp/DiscoverStocksViews.swift`. No API, filter meaning, entitlement or navigation change.

## 11. Known limitations and deferred checks
- **Android system Back closes the whole filter sheet** instead of stepping back a page. Not release-blocking: the draft lives in the presenter, so
  nothing is lost; reopening starts at the categories; the in-sheet back arrow steps back. A fix needs back interception inside the sheet's own dialog
  window (no shared `BackHandler` in the codebase) — deferred rather than risk the sheet's dismiss behaviour.
- iOS editor shows Reset all / Close on the first page only.
- Switching operator keeps the first typed value ("At least 5" → "At most 5").
- "2,500" is read as 2500 (thousands group), not 2.5 on a comma-decimal keyboard.
- No offline cache for Screener results (offline = error state); no iOS accessibility announcements for state changes.
- **Deferred, needs approval** — device matrix (local MOCK only: `./gradlew :server:runMock`, `adb reverse tcp:8081 tcp:8081`, iOS built with
  `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` and checked in the built Info.plist): 1 Android light presets 360 dp 1.0×; 2 same at 1.3×; 3 Android
  dark results; 4 Android filter categories + selected filter; 5 Android empty/error/missing data; 6 iOS light presets; 7 iOS dark results; 8 iOS filter
  editor; 9 iOS XXXL / compact width; 10 iOS results bottom with compare bar and MOCK strip. TalkBack/VoiceOver need a person listening.
- Phase 5A.1 conditions still open (screen readers, iOS signed-in Watchlist, iPhone SE XXXL, mixed saved dates on device).

## 12. Verdict
**PASS WITH CONDITIONS** — implementation audit passes on both platforms after five small fixes with regression tests; all automated suites and both
builds pass. Conditions (non-blocking, not passes): device screenshots, large-text device checks, TalkBack and VoiceOver — all **NOT RUN**.

## 13. Phase 5C readiness
**READY WITH CONDITIONS** — the brief (`docs/GLOBAL_UI_REFINEMENT_PHASE5C_BRIEF.md`) lists the hierarchy, reusable components, iOS/Android gaps,
responsive/accessibility risks, missing-value wording, selection, history gating and research/AI areas to keep unchanged, plus a verification matrix.
Conditions: owner approval to start; the 5B fixes above committed first (on request); 5B device checks preferably run before or alongside 5C.
