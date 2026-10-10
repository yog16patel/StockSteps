# Global UI Refinement Phase 4 — Home, Markets, Company Details

Status: **Phase 4A (Home) committed and pushed** as "Redesign Home with a market overview and portfolio summary (Global UI Refinement
Phase 4A) on Android and iOS". **Phase 4B (Markets) implemented, verified, committed and pushed** as "Redesign Markets data-first with status, index rows and research tools (Global UI Refinement Phase 4B) on Android and iOS" (2026-10-10). Phase 4C (Company Details) has
not started and waits for review of 4B. **Phase 4B.1 (Markets refinement to the final reference) committed and pushed** ("Refine Markets to the final reference: compact US market status, US-only indices and sector methodology sheet (Global UI Refinement Phase 4B.1)")
(2026-10-10). Reference: the Phase 3.1 Portfolio screen
(`docs/GLOBAL_UI_REFINEMENT_PHASE3_1_POLISH.md`, `docs/design/phase3_1/`). UI-only: no formulas, API contracts, endpoints, entitlements,
navigation destinations, Firebase or MOCK fixtures changed.

---

## Phase 4A — Home

### 1. Before/after issue inventory

| # | Before (both platforms unless noted) | After |
|---|---|---|
| H1 | Portfolio summary was the pre-Phase-3 card: raw Material `Card`, Monospace/`.largeTitle` figures, "Today — (—%)", "Invested cost 16.20" without context, raw ISO "Prices as of 2026-10-07T20:00:00Z", text-only "View portfolio" | `HomePortfolioSummary` in the Phase 3 language: "Total value · account" label, `largeNumber` total, `StockPriceChange` today (or "Today's change unavailable"), one `StockMetricGrid(rowsWhenNarrow)` row (Invested cost / Unrealized P/L / Realized P/L with arrows), one readable freshness line, SAMPLE badge; whole card opens Portfolio (chevron); no account → explanation + secondary "Create portfolio" |
| H2 | No market overview on Home | "Markets today" card: market sessions ("US stocks: Closed for the day (after-hours trading)"), the brief's first three indices (value, signed change with ↑/↓ and spoken Up/Down, quote state such as "Close · Oct 7" in caution colour when stale/unavailable), Daily Market Brief entry row. **Data: the Daily Brief already loaded on Home — zero new requests**; empty snapshot → "Index values aren't available right now." (never placeholders) |
| H3 | Order: brand bar → MOCK scenario picker → brief card → greeting → portfolio; greeting hidden while restoring | Brand bar + greeting together → Markets today → portfolio → watchlist → personal facts → Learn and practice → news → recently viewed → MOCK scenario picker (compact, SAMPLE badge, at the end) |
| H4 | Brief preview duplicated the index moves in a three-line summary; action wrapped to two lines at large text (iOS) | Brief entry is one row inside the market card: title + "Latest available · Oct 7 · 1 min read" (caution colour when stale/offline) + chevron; the action name is the accessibility hint/click label |
| H5 | Watchlist: rows padded inside a padded card; stale caption plain; iOS bare spinner; plain "Find stocks" text button | Rows edge-to-edge with inset dividers (Portfolio style); stale caption in caution colour; labelled progress ("Updating prices"); empty state with secondary "Find stocks" button (search icon) |
| H6 | Android facts wrapped in `TextButton` (centred, tinted); iOS "Alerts unavailable" floated outside any card | `HomeLinkRow`: left-aligned title, muted detail, chevron, 52 dp/pt min height, dividers; alerts error inside the "Upcoming & alerts" card on both platforms |
| H7 | News symbol label in link blue above every story | Muted `label` text (context, not a link) |
| H8 | Learn banner, Practice card and all sections equally prominent | "Learn and practice" section header groups them as a quieter second tier |
| H9 | iOS section titles hand-rolled; "View all"/"Clear" default-tinted; MOCK menu label inherited the primary text colour | `StockSectionHeader` everywhere (action colour, 48 pt targets); menu label in `primaryText` |
| H10 | Core-built Home strings used raw ISO: "Quote: 2026-10-08T15:00:00Z", "Sample scenario · 2026-10-08", "Updated 2026-…Z" (or "Updated null") | "Quote: Oct 8, 2026, 3:00 PM UTC", "Sample scenario · Oct 8, 2026", "Prices updated Oct 8, 2026, …" (omitted when unknown) via `ReadableDates` |
| H11 | `StockRow` company names cut to one line at every size ("Microsoft Corp…" at 1.3×) | Names may use two lines at large text (Compose font scale ≥ 1.3, iOS ≥ xxxLarge); prices still never truncate |

### 2. Design decisions
- **Market overview from the brief, merged with the brief entry** (approved by the user): the brief is already fetched on Home, so indices
  cost no requests (Phase 5C.1 launch budget kept); one card instead of two stacked cards, and the summary sentence was dropped because it
  repeated the index rows. `DailyBriefPreviewCard` is unchanged and still used on Markets (Phase 4B decides its future there).
- **Index rows show the source quote state** ("Close · Oct 7", "From an earlier session (Oct 7)") rather than a computed freshness; stale and
  unavailable states use `cautionText`. Values via existing `BriefFormat` (no new number formatting).
- **Portfolio summary is a compact hero, not a copy of Portfolio**: no chart, holdings or "Why?" toggle; only the prices-as-of line (the
  exchange-rate date stays on Portfolio). The whole card is the tap target (Portfolio's own hero is not tappable).
- **`ReadableDates` moved to core** (`core/.../format/ReadableDates.kt`) with `PortfolioDates` delegating unchanged, so core-built Home strings
  and Portfolio share one wording. UTC stays UTC (Home has no exchange offset; no guessed local time).
- Cards on Home now use the bordered `StockCard` like the Portfolio reference (previously `bordered = false`).
- The Android "Refresh dashboard" text button stays (no pull-to-refresh on Android Home); iOS keeps pull-to-refresh.

### 3. Screens and components modified
- New: `app/shared/.../presentation/home/HomeDashboardPresentation.kt` (wording/grouping shared by both platforms), test
  `commonTest/.../home/HomeDashboardPresentationTest.kt` (6), core `format/ReadableDates.kt`.
- Android: `presentation/home/HomeScreen.kt` (order, sections, persona picker), `HomeComponents.kt` (`HomeMarketOverview`, `HomeIndexRow`,
  `HomeLinkRow`, `HomePortfolioSummary`), `presentation/portfolio/PortfolioScreen.kt` (old `PortfolioSummaryCard` removed — Home was its only
  user), `PortfolioPresentation.kt` (`PortfolioDates` delegates), `designsystem/components/StockRow.kt` (two-line names at large text).
- iOS: `HomeScreen.swift`, `HomeComponents.swift` (same four views), `PortfolioScreen.swift` (old `PortfolioSummaryCard` removed),
  `DesignSystem/StockComponents.swift` (`StockRow` two-line names at xxxLarge+).
- Core: `home/PersonalDashboard.kt` (fact quote time, sample-scenario date), `home/PersonalDashboardStore.kt` ("Prices updated …").

### 4. Android/iOS consistency review
Same order, titles, wording (from `HomeDashboardPresentation`), badges and states on both. Native differences kept: iOS pull-to-refresh vs
Android "Refresh dashboard"; iOS stacks index values at Dynamic Type ≥ xxxLarge, Android at font scale ≥ 1.3 (the existing large-text rule);
iOS uses `Menu`, Android `DropdownMenu` for the MOCK scenario picker. Index quote-state text differs between the two devices in the
screenshots only because each showed a different cached brief copy (iOS "From an earlier session (Oct 7)", Android "Close · Oct 7") — data,
not UI.

### 5. Responsive and accessibility results (actually performed)
Device pass approved by the user: local MOCK server (`:server:runMock`, stopped afterwards), iPhone 16 Simulator (iOS 18.3) and
emulator-5554 (signed-in account only viewed; read-only MOCK Home scenarios).

| Check | iOS | Android |
|---|---|---|
| Light, default text | ✅ | ✅ |
| Dark, default text | ✅ | ✅ (in-app theme Dark, restored to Light) |
| Large text | ✅ XXXL — index values stack, hero/metrics rows readable | ✅ 1.3× at 360 dp — index values stack, nothing clipped |
| Narrow width | NOT RUN (393 pt device only) | ✅ 360 dp (`wm size 1080x2400`, reset) |
| Populated watchlist rows + facts | Facts ✅ (own account: earnings rows with chevrons, alerts error in card); populated watchlist rows **not seen** (MOCK scenario menu could not be driven reliably; own watchlist shows a sign-in error) | ✅ `watchlist-and-portfolio` scenario, light/dark/1.3× |
| Two-line company names at large text (H11) | Build only — **not visually checked** | Build only — **not visually checked** (change made after the device pass; the 1.3× screenshot shows the old one-line truncation) |
| Populated portfolio summary | ✅ own cached portfolio (CAD 2,145.53) | Empty-account state only (the MOCK restart cleared that account's in-memory portfolio) |
| VoiceOver / TalkBack | NOT RUN. Semantics by code: one merged label per index row (`BriefFormat.accessibility`: name, value, Up/Down change, state), chevrons hidden, headings marked, click labels/hints on card and rows | NOT RUN (same) |

Navigation spot checks: Recently viewed row → Company Details (iOS) ✅; back to Home ✅.

### 6. Test and build results (2026-10-10)
- `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`
  → **BUILD SUCCESSFUL**: core JVM **429/0**, core iOS **429/0**, shared Android **87/0** (was 81), shared iOS **83/0** (was 77), `assembleDebug` OK.
- `./gradlew :server:test` → **482/0, 3 skipped** (core is shared with the server; no server code changed).
- iOS `xcodebuild … -destination 'generic/platform=iOS Simulator' … build` → **BUILD SUCCEEDED** (re-run after the last Swift edit).
- After the final edits (`StockRow` wrap, iOS menu colour, unused import) only `compileAndroidMain` and `xcodebuild` were re-run.

### 7. Screenshots and evidence (`docs/design/phase4a/`)
Before: `before-ios-light(-2).jpg`, `before-android-light(-3).jpg`. After: `after-ios-light.jpg`, `after-ios-dark.jpg`,
`after-ios-dark-xxxl(-2).jpg`, `after-ios-dark-facts.jpg`, `after-android-light(-2).jpg`, `after-android-dark.jpg`,
`after-android-dark-persona-2/-3.jpg` (populated watchlist, facts, news, picker), `after-android-dark-large-360.jpg`,
`after-android-dark-large-persona-2.jpg` (1.3× at 360 dp; taken before the two-line-name change).

### 8. Known limitations
- Home indices come from the latest Daily Brief, so they are as fresh as the brief (session close or earlier), labelled by its quote state
  — never live intraday values. If the brief fails, the market card shows the brief's error message.
- Timestamps in Home facts are UTC (no exchange offset available there).
- MOCK-only `newsNotice` developer text ("Portfolio is not implemented…") still appears in the News card for some scenarios (pre-existing).
- iOS watchlist error "Could not sign in. Check your email and password." on the spare simulator is the Phase 5C.1 throwaway account without
  the Auth emulator — environment, not UI.

### 9. Regression risks
- `StockRow` change affects every screen using it (Markets, Watchlist, search, Portfolio-adjacent rows) — only at large text, and only by
  allowing a second line for the name.
- `ReadableDates` extraction: `PortfolioDates` keeps the same API and output (Portfolio tests unchanged and green).
- Core wording change: "Updated <ISO>" → "Prices updated <readable>" in Home's quote notice; no tests asserted the old text.
- Removing `PortfolioSummaryCard` (Android + iOS): it had no other callers (verified by search).

### 10. Readiness for the next stage
Phase 4A is ready for review. **Phase 4B (Markets) starts only after approval.** Carry-overs for 4B: decide the Markets brief preview
(Home no longer uses `DailyBriefPreviewCard`), reuse `HomeLinkRow`-style rows for research tools, check `StockRow` two-line names visually,
and run the iOS narrow-width (iPhone SE) and screen-reader checks that were not run here.

---

## Phase 4B — Markets

Visual reference: `docs/design/phase4b/stocksteps-markets-ui-reference.png` (layout guidance only — none of its prices, times, open states,
stock lists, "View all" links or "Market insights" tool were copied). Decisions approved by the user: device pass allowed; Market status
shows the **US session only** (the Markets data has no Canadian session; Canada stays in the Daily Brief and on Home).

### 1. Before/after issue inventory

| # | Before (both platforms unless noted) | After |
|---|---|---|
| M1 | Market data started as the 4th block: title → brief preview → three large tool cards → session line → indices | Data first: title → Market status → Major indices → brief entry → Top Movers → Sector Performance → Research tools → Market News → lesson → disclaimer |
| M2 | Indices in a horizontal carousel of fixed-width cards: names cut to one line, the 3rd/4th index hidden off-screen (Dow), values not comparable | "Major indices": one grouped card of rows — name + source quote time ("As of 4:00 PM ET · Oct 7", proxy note in caution colour) left, value + signed change "↓ -18.59 (-0.24%)" right, inset dividers; names wrap; stacked at large text; tap still opens the index lesson |
| M3 | Session status a loose line; **closed shown with a red dot** (reads as a loss); sample notice glued onto "Updated …" | "Market status" card: dot (green open / amber pre-market & after-hours / **neutral grey** closed & unknown) + status in words, next open/close, muted "US stocks (NYSE, Nasdaq) · Updated …", sample notice in caution colour, SAMPLE badge |
| M4 | Research tools: two half-width promotional cards + a 5-line Earnings Center card | "Research tools": one grouped card of `StockNavigationRow`s (icon, title, one-line detail, chevron): Discover Stocks, Compare Companies, Earnings Calendar (calendar counts only; plain description while loading/unknown; watchlist count as a second line) |
| M5 | Brief preview repeated the index moves in a summary sentence next to the indices; actions wrapped to 2–4 lines at large text | Brief entry row ("Your Daily Market Brief", freshness · read time · Sample data, caution colour when stale/offline) + "Previous briefs" link; no summary sentence. The now-unused `DailyBriefPreviewCard` was removed on both platforms |
| M6 | Mover prices in `textPrimary`; captions in `textTertiary` | Prices `textValue`, volume `textSupporting`, methodology captions `textMeta`; company names wrap to two lines at large text (Phase 4A `StockRow` rule) |
| M7 | Sector names cut to one line ("Communication Servi…"); iOS fixed 96 pt bars / 72 pt percent column | Names wrap; flexible bar width; percent sized to content; at large text the bar and change move under the name |
| M8 | Unbordered cards; title "Explore Markets" + subtitle | Bordered `StockCard`s like Portfolio/Home; plain "Markets" heading + search |
| M9 | Index sparklines squeezed names on phones once rows replaced the carousel (found during this phase) | Trend sparkline shown only when the indices card is ≥ 380 dp/pt wide (`MarketsScreenPresentation.TREND_MIN_ROW_WIDTH`): shown on the 427 dp emulator and tablets, left out on 360–393 pt phones |

### 2. Design decisions
- **Data first, tools later**: the screen answers "what is the market doing?" before offering tools. The brief entry sits right after the
  indices because it explains them; tools follow the data sections.
- **Closed is neutral**: a red dot implied a loss. Green only while the regular session is open; amber for extended hours (the same tones as
  `SessionTone`); the status is always written out.
- **No computed freshness**: index rows show the presenter's source quote time; the status card shows the presenter's "Updated …" time and
  the backend's data notice. Sample data keeps the caution-coloured "Not live prices" notice and a SAMPLE badge.
- **Brief entry without its summary**: the summary sentence restated the index moves shown just above (the reference's description line
  "Key market moves, top stories…" is not data the app has, so it was not invented).
- **`StockNavigationRow`** (new design-system component, both platforms) generalises Home's link row; `HomeLinkRow` now delegates to it
  (same look; the title–chevron gap is `md` instead of `sm`).
- Movers tabs keep their existing labels (Gainers / Losers / Most Active) and the "Why did it move?" button; "Show all" unchanged.
- Kept, not added: no "View all" on indices (no destination), no "Market insights" tool (no feature), no empty-state illustration.

### 3. Components and screens modified
- New: `designsystem/components/StockNavigationRow.kt`; iOS `StockNavigationRow` in `DesignSystem/StockComponents.swift`;
  `presentation/markets/MarketsScreenPresentation.kt` (status dot family, status meta line, index change/meta text, trend width rule, research
  tool wording, earnings detail) + test `MarketsScreenPresentationTest` (5).
- Android: `presentation/markets/MarketsScene.kt` (screen order, status card, index rows, brief entry, research tools, movers/sectors/news
  styling); `composeResources/values/strings.xml` (`markets_title` → "Markets"); `presentation/home/HomeComponents.kt` (`HomeLinkRow`
  delegates); `presentation/brief/DailyBriefScreens.kt` (unused `DailyBriefPreviewCard` removed).
- iOS: `MarketsScene.swift` (same), `HomeComponents.swift` (`HomeLinkRow` delegates), `DailyBriefScenes.swift` (unused preview removed).
- Unchanged: `MarketsPresenter`/core, `MarketsViewModel`/`MarketsModel`, requests and refresh behaviour, navigation destinations, fixtures.

### 4. Android/iOS consistency
Same section order, titles, wording (from `MarketsScreenPresentation`), badges, dot colours and states. Native differences kept: Android
pull-to-refresh box vs iOS `.refreshable`; Android stacks at font scale ≥ 1.3, iOS at Dynamic Type ≥ xxxLarge; tool icons are Material
(search, pie chart, trending-up) vs SF Symbols (magnifyingglass, square.split.2x1, calendar). The emulator and simulator showed different
MOCK clock times ("Updated 5:30 PM ET · Oct 7" vs "7:46 PM ET · Oct 8") because the MOCK market clock advances — data, not UI.

### 5. Responsive and accessibility results (actually performed)
Device pass approved by the user: local MOCK server (stopped afterwards), iPhone 16 Simulator (iOS 18.3), a **temporary iPhone SE (3rd gen,
iOS 18.3) simulator** created for the narrow check and deleted afterwards (guest mode; backend preference set to MOCK before first launch),
emulator-5554 (signed-in account only viewed).

| Check | iOS | Android |
|---|---|---|
| Light, default text | ✅ iPhone 16 | ✅ |
| Dark | ✅ | ✅ (in-app theme Dark, restored to Light) |
| Large text | ✅ XXXL — indices, sectors stack; mover names on two lines; nothing clipped | ✅ 1.3× at 360 dp — same |
| Narrow width | ✅ iPhone SE 375 pt | ✅ 360 dp |
| Two-line `StockRow` names (4A carry-over) | ✅ XXXL movers | ✅ 1.3× movers |
| Error state (MOCK server stopped) | NOT RUN | ✅ market-data error card with Try again; brief "Offline copy"; research tools still usable; earnings row falls back to its plain description |
| Navigation spot checks | — | ✅ Compare Companies and Previous briefs open; Back returns |
| VoiceOver / TalkBack | NOT RUN. Code review: one spoken label per index row (`IndexCardModel.accessibilityLabel` + "Explains this index"), per mover row, per sector row; status dot hidden, status read in words; headings marked; chevrons/icons hidden; click labels on tool rows | NOT RUN (same) |

Observation: changing Dynamic Type while the app is open left already-rendered iOS lazy rows at the old size until relaunch (SwiftUI
behaviour, not specific to this screen); after relaunch every header and row scaled consistently.

### 6. Tests and builds (2026-10-10)
- `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`
  → core JVM **429/0**, core iOS **429/0**, shared Android **92/0** (was 87), shared iOS **88/0** (was 83), `assembleDebug` OK.
- After removing `DailyBriefPreviewCard`: shared Android 92/0, shared iOS 88/0 and `assembleDebug` re-run OK; iOS `xcodebuild … build`
  **BUILD SUCCEEDED** (final run). Core and server code were not changed in 4B (server tests not re-run).

### 7. Screenshot evidence (`docs/design/phase4b/`)
`before-android-light.jpg`, `before-ios-light.jpg` (full scroll); `after-top-ios-light-ios-dark-android-light.jpg`;
`after-android-light-full.jpg`; `after-ios-light-lower-sections.jpg`; `after-android-dark.jpg`; `after-android-dark-1.3x-360dp.jpg`;
`after-ios-dark-xxxl.jpg`; `after-ios-se-375pt-light.jpg`; `after-android-dark-error-state.jpg`; reference `stocksteps-markets-ui-reference.png`.

### 8. Known limitations
- Market status covers the US session only (the only session in the Markets data); Canadian status is on Home and in the brief.
- Index trend sparklines are hidden on phones narrower than 380 dp/pt (data unchanged; shown on wider screens).
- No VoiceOver/TalkBack walkthrough; iOS error state not captured; movers "Losers"/"Most Active" tabs and "Show all" not re-tapped on device
  (unchanged code paths).
- Movers' company names still truncate after two lines at large text (prices never truncate).

### 9. Regression risks
- `HomeLinkRow` now renders through `StockNavigationRow` (Home facts and brief row): slightly wider gap before the chevron.
- Removal of `DailyBriefPreviewCard` (no remaining callers on either platform; the brief reader and history are untouched).
- iOS `onGeometryChange` on the indices card (same pattern as Portfolio rows).
- "Markets" string change affects only the Markets title (`markets_title` has no other users; the tab label is separate).

### 10. Readiness for Phase 4C
Phase 4B is ready for review. **Phase 4C (Company Details) starts only after approval.** Carry-overs: Company Details still uses the
read-only `MarketStatusIndicator` (red track when closed) — align it with the neutral closed tone; reuse `StockNavigationRow` for its
research entries and `MarketsScreenPresentation`-style source-time lines; fix "At a Glance" truncation (`+14.9 % (Y…`).

---

## Phase 4B.1 — Markets refinement to the final reference

Reference: `docs/design/phase4b/stocksteps-markets-ui-reference-final.webp` ("Phase 4B Final Reference", visual guidance only; none of its
values, times or open states were copied). Device use: the Phase 4B-approved iPhone 16 Simulator and emulator-5554 with the local MOCK server
(stopped afterwards; all settings restored).

### 1. Changes (Android and iOS)
| Area | Phase 4B | Phase 4B.1 |
|---|---|---|
| Status card | "Market status", long "Market closed/After-hours" label, detail, market name + update line, full sample sentence (5 lines) | **"US Market"** + Sample badge; dot + short status ("Open", "Pre-market", "After-hours", "Closed", "Closed for the weekend", "Closed · <holiday>", "Status unavailable"); **"Next open · Fri, Oct 9 · 9:30 AM ET"** / "Closes · 4:00 PM ET" (early close named) from the backend calendar; one footer — "Sample quotes · Not live" for sample data, otherwise the source "Updated …" time; the full data notice is the footer's spoken label. ~100 pt tall at default size |
| Indices | US and Canadian indices | **US only** (by the API's `region`): S&P 500, Nasdaq Composite, Dow Jones Industrial Average. TSX stays in the data, the Daily Brief and Home |
| Brief card | Detail with "· Sample data" appended | Freshness · read time on one line; "Sample data" / "Offline copy" on their own line |
| Sectors | Full ETF methodology paragraph under the card | Short caption "Change since the previous close. Measured with sector ETFs."; the backend's full methodology in an **(i) sheet** ("How sector performance is measured"); rows at the 48 dp/pt touch-target minimum (extra padding only at large text) |
| Research tools | Earnings fallback "See when companies are reporting results" | "Upcoming earnings dates and reports" (real counts replace it when known) |
| Wrapping | — | "9:30 AM ET" kept together with non-breaking spaces (it wrapped as "9:30 AM / ET" at 1.3×) |

Unchanged (already matched the reference or the requirements): screen order, Top Movers tabs and rows (company names two lines at large
text), research-tool rows, Market News rows, Today's lesson card with Learn more (opens the lesson sheet), disclaimer, five-tab navigation.

### 2. Files
- Core: `markets/MarketsPresentation.kt` — new `MarketsPresenter.sessionLine(session)` (display formatting of the backend's `closesAt` /
  `earlyClose` / `nextOpenLocal`; no calendar logic), test `MarketsPresenterTest.sessionLineUsesTheBackendCalendarOnly`.
- Shared: `presentation/markets/MarketsScreenPresentation.kt` (status label/line/footer, US-only filter, brief detail/labels, sector
  caption + methodology lesson), test `MarketsScreenPresentationTest` (+5 rules; `statusMeta` removed).
- Android: `presentation/markets/MarketsScene.kt`. iOS: `MarketsScene.swift`.

### 3. Differences from the reference and why
- **Index sparklines** appear only when the indices card is ≥ 380 dp/pt wide (emulator 427 dp: shown; iPhone 16 / SE / 360 dp: hidden).
  At the app's accessible type sizes a sparkline on a 393 pt phone squeezed the index name and quote time to ~105 pt (Phase 4B finding);
  the reference uses smaller text than the design system allows.
- **"Dow Jones Industrial Average"** keeps its full API name (wraps) instead of the reference's "Dow Jones".
- **No "View all"** on indices or news and no extra news screen (no destinations exist).
- **Sector rows** are taller than the reference (48 dp/pt touch targets; each row opens its lesson).
- **Top Movers tabs** stay the Phase 2 pill selector (blue filled selection) rather than a segmented track.
- **Lesson topic** rotates daily from reviewed content (the reference's "When is the US stock market open?" appears on its day).
- **Status footer for REAL data** shows the update time; delayed/cached notices are spoken in full and visible in the brief/offline labels.
- Design tokens were not changed to the reference hex values; the shared semantic tokens remain authoritative (as the task allows).

### 4. Verification (actually performed)
- Tests: core JVM **430/0**, core iOS **430/0** (+1), shared Android **96/0**, shared iOS **92/0** (+4 net), `assembleDebug` OK, iOS
  `xcodebuild` BUILD SUCCEEDED (after the final edit).
- Devices: iOS light + dark, Android light; iOS XXXL; Android 1.3× at 360 dp (status line wrap fixed and re-checked); Android sector (i)
  sheet opens with the backend methodology. The iOS card showed "Closed · Next open · Fri, Oct 9" and Android "After-hours · Next open ·
  Thu, Oct 8" at the same moment because each device's MOCK clock had advanced differently — both come from the calendar data.
- NOT RUN in 4B.1: VoiceOver/TalkBack walkthrough, iPhone SE (the temporary simulator from 4B was deleted), error/offline states (unchanged
  code paths, verified in 4B), movers tab switching on device.
- Evidence (`docs/design/phase4b/`): `final-top-ios-light-ios-dark-android-light.jpg`, `final-android-light-full.jpg`,
  `final-large-text-ios-xxxl-android-1.3x-360dp.jpg`, `final-android-sector-methodology-sheet.jpg`.

### 5. Remaining issues / next
- Screen-reader walkthroughs still outstanding for Home and Markets.
- Phase 4C (Company Details) only after approval; carry-over: Company Details' `MarketStatusIndicator` still uses a red track when closed.
