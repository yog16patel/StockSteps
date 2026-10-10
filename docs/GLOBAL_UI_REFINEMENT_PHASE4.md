# Global UI Refinement Phase 4 — Home, Markets, Company Details

Status: **Phase 4A (Home) implemented, verified, committed and pushed** as "Redesign Home with a market overview and portfolio summary (Global UI Refinement Phase 4A) on Android and iOS" (2026-10-10). Phase 4B (Markets) and 4C (Company Details) have not
started; each waits for the user's review of the previous stage. Reference: the Phase 3.1 Portfolio screen
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
