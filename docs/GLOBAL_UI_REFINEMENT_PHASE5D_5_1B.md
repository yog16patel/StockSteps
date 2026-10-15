# Global UI Refinement Phase 5D.5.1b — Daily Brief refinement, Phases A and B

Status: **committed and pushed as "Refine the Daily Brief for Global UI Refinement Phase 5D.5.1b with a data-derived market takeaway, compact index tiles, one status banner, a grouped story card, a compact watchlist empty state and structured earnings rows on Android and iOS"** (on top of `38002c0`); device checks run on the local MOCK server (2026-10-14) with two fixes. Plan: `docs/DAILY_BRIEF_UI_REFINEMENT_PLAN.md`.

Owner decisions applied (2026-10-14):
- D-B1 A: the takeaway rule as written, no magnitude words.
- D-B2: server summary visible, folded at large text.
- D-B3: first-sentence previews.
- D-B4: nav title only.
- D-B5: takeaway on Home.
- D-B6: method note folded.
- D-B7: one grouped story card.
- D-B8: "Add companies".

## 1. What changed (display only; no server, API, fixture, presenter, calculation or navigation change)
| Area | Before (5D.5.1) | After |
|---|---|---|
| Headline | Server summary as a 20 sp/pt headline (3–4 lines, ~8 at large text) | **Takeaway** from `DailyBriefDisplay.takeaway` ("US stocks closed lower. Canadian stocks closed higher.") as the headline; the server summary word for word below it in `small`; at large text (Android ≥ 1.3×, iOS accessibility sizes) folded under "Full summary". No takeaway → the summary is the headline as before |
| Takeaway rules | — | Only close/live values (never stale or unavailable); all up "higher", all down "lower", within ±0.005% "about unchanged", else "mixed"; live "so far today (delayed)"; pre-market/weekend/holiday editions start "In the last session (Oct 9), …"; a holiday closure from the session; live and closed values never blended; no magnitude or cause words (tested) |
| Status | Stale and offline as two banners | One `StockBanner`, one sentence per state (WARNING when stale, INFO when only offline) |
| Indices | Three full-width rows | **Three `StockIndexTile`s** (name, % change with arrow and sign, level, points) while the measured widest unbreakable piece fits (`StockIndexTilesLayout`, built on `StockComparisonLayout`); otherwise the existing `BriefIndexRow`s. The shared state label shows once ("All values: Close · Oct 7"); a tile whose state differs keeps its own label (caution colour when stale) |
| Stories | Borderless rows | One grouped `StockCard` with the rows divided inside (no card per story); "About these stories" below |
| Watchlist | Long empty-state sentence + "Go to Watchlist"; method note always shown | "Follow companies to see important news and price moves here." + **Add companies** (same action); the note folded under "About watchlist updates"; highlight rows get a neutral kind icon (chart/news/calendar/document; the sentence says the direction) |
| Upcoming earnings | One " · " sentence | `BriefEarningsRow`: company (ticker); "Expected Oct 8 · Before market open" with the server's status as a badge (Confirmed = INFO, Estimated/Tentative = NEUTRAL; the word is shown, never upgraded); "EPS estimate USD −0.42 · General example" only with an estimate |
| Learn | Concept card mid-page without a section | A "Learn" section with the concept card |
| History | "After-close brief · Oct 7" bold title | Title "After-close brief" (+ Sample badge), detail "Oct 7 · 1 min read", first-sentence preview |
| Home | Summary's first sentence (repeated the numbers above it) | The takeaway, falling back to the first sentence (`homeSummary`) |

**Deviation from the plan:** watchlist price moves don't get a separate `StockPriceChange`. `WatchlistHighlight` has no numeric field, only the
server sentence ("AAPL rose 2.40%…"), and parsing text would be fragile. The direction stays in words in the sentence.

Phase C (concept-card bottom spacing, visual pass) was not part of this approval.

## 2. Files
- **core:**
  - `brief/DailyBriefDisplay.kt`: `takeaway`, `status`, `sharedStateLine`, `indexPercent`, `indexPoints`, `tileTokens`, `earningsWhen`,
    `earningsStatus`, `earningsConfirmed`, `earningsNote`, `earningsSpoken`, `highlightIcon`, history title/detail split, `homeSummary` via the
    takeaway, and new copy constants.
  - Test: `brief/DailyBriefDisplayTest.kt`, 20 tests (+8).
- **shared:**
  - New `designsystem/components/StockIndexTile.kt`.
  - `theme/StockSemanticStyles.kt` (`StockIndexTilesLayout`), with `ResponsiveLayoutRulesTest` (+1).
  - `presentation/brief/DailyBriefComponents.kt` (`BriefIndices`, `BriefEarningsRow`, `briefHighlightIcon`).
  - `presentation/brief/DailyBriefScreens.kt`.
- **iOS:** new `DesignSystem/StockIndexTile.swift`; `DailyBriefScenes.swift` (`BriefIndices`, `BriefEarningsRow`, `briefHighlightSymbol`, sections).
- **Home:** no file change. `HomeDashboardPresentation.briefSummary` already calls `homeSummary`.

## 3. Fixes made during the device checks
| # | Found on | Problem | Fix |
|---|---|---|---|
| F-1 | Android 1.0× | Tiles never showed: "−0.24%" at 17 sp plus its arrow (~81 dp) was just over the 80 dp a tile allowed with 12 dp side padding, so the rule fell back to rows | Tile side padding 8 dp (vertical stays 12) on both platforms, passed to the rule as `tilePadding`. Tiles show at Android 1.0× and iOS Default; rows from 1.3× / AX XL as planned |
| F-2 | Android, MOCK `us-open-ca-closed` | Takeaway said "Canadian stocks closed higher." while Canada was closed today for Thanksgiving; the value was the Oct 9 close and read as today's. Same cause in `ca-open-us-closed` and `holiday` | A market whose session is on holiday says "…markets are closed for {holiday}." whatever older closes exist; both on the same holiday → "US and Canadian markets are closed for Christmas Day." The server summary below still gives the last session's moves. Tests added |

Takeaway wording on device after F-2 (Android, dark, each MOCK scenario; first line = takeaway, the server summary below it unchanged):

| Scenario | Takeaway |
|---|---|
| normal (after close) | US stocks closed lower. Canadian stocks closed higher. |
| pre-market | In the last session (Oct 7), US stocks closed lower. Canadian stocks closed higher. |
| market-hours | US stocks are lower so far today (delayed). Canadian stocks are higher so far today (delayed). |
| weekend | In the last session (Oct 9), US stocks closed lower. Canadian stocks closed higher. |
| holiday (Christmas) | US and Canadian markets are closed for Christmas Day. |
| us-open-ca-closed | US stocks are lower so far today (delayed). Canadian markets are closed for Thanksgiving Day. |
| ca-open-us-closed | US markets are closed for Thanksgiving Day. Canadian stocks are higher so far today (delayed). |
| stale-index (TSX from Oct 3) | US stocks closed lower. (the stale TSX is left out) |
| missing-index (no Nasdaq) | US stocks closed lower. Canadian stocks closed higher. |

## 4. Verification
**Automated (final code, run one after another):** see §4.1.

**Device checks (local MOCK `:server:runMock` only; captures in `docs/design/phase5d_5b/`):**

| Check | Android `emulator-5554`, 360 dp | iOS iPhone 16 |
|---|---|---|
| Takeaway + server summary + one meta line + Sample badge | PASS 1.0× light / dark | PASS Default light / dark |
| Summary folded under "Full summary" at large text | PASS 1.3×, 2.0× | PASS AX XL (live change) |
| Tiles at normal text; "All values: …" once | PASS 1.0× light / dark (after F-1) | PASS Default light / dark (after F-1) |
| Rows at large text, nothing clipped | PASS 1.3×, 2.0× | PASS AX XL, AX5 |
| Per-tile state labels when states differ | PASS (`us-open-ca-closed`: live US, TSX "Close · Oct 9") | not repeated |
| Takeaway in 9 scenarios | PASS (table above, after F-2) | not repeated: the scenario submenu didn't open from a simulated tap; same shared code |
| One banner for stale + offline (both sentences) | PASS (port forward removed) | PASS (MOCK server stopped) |
| Stories in one grouped card, About these stories | PASS | PASS |
| Watchlist empty state + Add companies + folded note | PASS (signed in, empty watchlist) | guest: sign-in prompt PASS |
| Structured earnings rows, Confirmed/Estimated badges, badge wraps at 2.0× | PASS | not seen (earnings rows need a signed-in user) |
| Learn section | PASS | PASS |
| History rows (type, Sample, date · read time, preview) | PASS | not repeated |
| Home preview = takeaway | PASS light | PASS light |

**Not verified:**
- iOS signed-in sections: the simulator's saved session had ended, and the app was continued as a guest. No credentials were typed and no account
  was created.
- Loading skeleton capture; error without a saved copy; StockSteps+ / AI; TalkBack and VoiceOver; iPhone SE width.

**Environment afterwards:**
- MOCK server stopped.
- emulator-5554: font 1.0, `wm size reset`, no `adb reverse`, app theme Light.
- iPhone 16: light, content size large, app in guest mode.

### 4.1 Automated results (final code)
- core `:core:jvmTest` **522/0**, `:core:iosSimulatorArm64Test` **522/0** (`DailyBriefDisplayTest` 20 tests, with the holiday cases from F-2).
- shared `:app:shared:testAndroidHostTest` **246/0**, `:app:shared:iosSimulatorArm64Test` **242/0**.
- `:server:test` **501/0**.
- `:app:androidApp:assembleDebug` OK; `xcodebuild` (generic iOS Simulator, unsigned) **BUILD SUCCEEDED**; device build (iPhone 16, ad-hoc
  signed, MOCK URL) BUILD SUCCEEDED.
- Nothing under `docs/design/` is deleted.
- Captures: 33 files, 9.2 MB.

## 5. Verdict
**PASS WITH CONDITIONS.** The device matrix passes on Android and iOS after two fixes; one of them corrects headline wording that could have
presented an older close as today's. The conditions are the unverified items above.
