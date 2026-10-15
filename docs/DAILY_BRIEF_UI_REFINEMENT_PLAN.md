# Daily Brief UI/UX refinement — plan (Phase 5D.5.1b)

Status: **READ-ONLY PLAN. No source code changed.** Waiting for the owner's approval.
- Date: 2026-10-14. Base: `main` at `38002c0` (Phase 5D.5.1, pushed). The working tree was clean before this file.
- Builds on: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_PLAN.md` (audit and decisions D-1…D-8) and `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_1.md` (what
  shipped and the device results).
- Captures of the current state: `docs/design/phase5d_5/`.

Theme stays as is: primary `#1683FF`, dark background `#07111C`, dark surface `#0D1B2A`, light background `#F7FAFD` (`theme/Colors.kt`), and the
existing typography and spacing tokens. No new palette.

---

## 1. Current implementation audit (after 5D.5.1)

| Area | Files | Today |
|---|---|---|
| Copy and rules | core `brief/DailyBriefDisplay.kt`, `brief/DailyBriefPresentation.kt` (`BriefFormat`, presenter), `format/ReadableText.kt` | All reader copy; meta and freshness lines; relative times; banners; excerpt rule; earnings and history lines. Tests: `DailyBriefDisplayTest` (12) |
| Reader (Android) | `app/shared/.../presentation/brief/DailyBriefScreens.kt`, `DailyBriefComponents.kt` (`BriefIndexRow`), `DailyBriefScenes.kt` | Meta line + Sample badge + ⋯; **server summary as a `sectionTitle` headline (3–4 lines at 1.0×, about 8 lines at 2.0×)**; freshness and two session lines; stale/offline banners; index rows; stories as borderless rows; watchlist; earnings rows; concept card; StockSteps+; About this brief; Previous briefs |
| Reader (iOS) | `app/iosApp/iosApp/DailyBriefScenes.swift` | Same structure (`BriefIndexRow`, `.toolbar` ⋯ menu) |
| Home entry | `presentation/home/HomeComponents.kt` / `HomeComponents.swift`, `HomeDashboardPresentation.kt` | "Markets today": session lines, 3 index rows (`BriefIndexRow`), "Your Daily Market Brief" row with the summary's **first sentence** (it repeats numbers shown in the rows just above) |
| Design system | `designsystem/components/StockDisclosure.kt` (`StockDisclosure`, `StockNotes`, `StockShowDetails`, `StockLinkAction`), `StockIndexRow.kt`, `StockNavigationRow` (`summary`), `StockBanner`, `StockStatusBadge` (`StockBadgeKind`), `StockCard`, `StockSectionHeader`, `StockEmptyState` / iOS `StockSectionMessage`, `StockPriceChange`, `StockSkeleton`; iOS mirrors in `DesignSystem/StockDisclosure.swift`, `StockComponents.swift`, `StockStatusViews.swift` | Present on both platforms |
| Layout rules | `app/shared/.../theme/StockSemanticStyles.kt`: `StockComparisonLayout.sideBySide(widestToken, n, width, gap, accessibilityText)`, `StockCardGridLayout`, `StockQuoteRowLayout`; tests in `ResponsiveLayoutRulesTest` | `sideBySide` is exactly the rule the tiles need: n columns only while the widest unbreakable token fits one column; always stacked at accessibility sizes or an unknown width |
| Server text used by the UI | `server/.../brief/DailyBriefService.kt`: `summaryLine`, `notes`, the personal note "Watching a company doesn't mean you own it…", earnings `dateStatus` (Confirmed / Estimated / Tentative), `timing` | **Not changed by this plan** |

There are no other unfinished Daily Brief changes. Proposed but not started: a follow-up session for the iOS Home AX5 heading break and the
history → brief navigation.

## 2. UX problems

Evidence comes from the 5D.5.1 captures (`android-360dp-*`, `ios-iphone16-*`).

| ID | P | Problem | Where |
|---|---|---|---|
| U-01 | **P0** | The answer isn't scannable. The summary paragraph is the largest text on screen (20 sp/pt semibold, 3–4 lines at 1.0×, about 8 at 2.0× or AX5) and states every percentage in a sentence. You have to read it to learn "up or down". | both |
| U-02 | **P0** | Large text: the index section is 3 rows × 4 lines (about 600 dp at 2.0×). The headline plus the indices fill more than two screens before any story. | both |
| U-03 | P1 | Indices take full-width rows even at 1.0×, where three compact tiles fit (estimated in §3.3). | both |
| U-04 | P1 | The same session label ("Close · Oct 7") repeats under each index, and the session lines in the header say it again. | both |
| U-05 | P1 | Stale and offline are two stacked banners (about 120 dp). They can coexist and must both stay true, but one banner can say both. | both |
| U-06 | P1 | Stories are flat rows; the headline weight equals the section weight, so there is no card grouping. | both |
| U-07 | P1 | The empty watchlist is verbose: a sentence, a big text button, and the three-line "Watching a company…" methodology note always shown. | both |
| U-08 | P1 | Earnings rows are one long " · " sentence ("Expected Oct 8 · Estimated date · Before market open · EPS estimate USD −0.42 · General example"). Confirmed and Estimated have no visual distinction. | both |
| U-09 | P1 | History rows put type and date in one bold title, so at AX sizes "After-close brief · Oct 7" wraps to two big lines plus the badge. | both |
| U-10 | P1 | Home's brief preview repeats the index numbers shown in the rows right above it. | both |
| U-11 | P2 | Header: only the nav title "Daily brief". The owner's direction asks for a "Your Daily Brief" heading (see D-B4). | both |
| U-12 | P2 | Learn content is spread out (index explainer under Markets, concept card mid-page). | both |
| U-13 | P2 | The concept card has a large bottom gap (48 dp action row + card padding). | both |

## 3. Proposed screen structure

### 3.1 Headline rule (needs owner approval; it is new copy derived from market data)
`DailyBriefDisplay.takeaway(brief): String?`, deterministic, computed only from `marketSnapshot` and `sessions`. One sentence per market:
- **Group** by market: US = S&P 500 + Nasdaq Composite; Canada = S&P/TSX Composite.
- **Values used:** only indices with a value and a change whose state is `SESSION_CLOSE`, `PREVIOUS_CLOSE` or `LIVE_DELAYED`. Never `STALE` or
  `UNAVAILABLE`.
- **Direction words** (the `BriefWording` thresholds, ±0.005%):
  - every index up → "higher";
  - every index down → "lower";
  - every index within the threshold → "about unchanged";
  - otherwise → "mixed".
- **Tense** from the quote state, never "today" for an old brief:
  - close or previous close → "US stocks closed lower.";
  - live → "US stocks are lower so far today (delayed).";
  - a market closed for a holiday with no values → "US markets are closed for Thanksgiving." (the session's holiday name).
- **Pre-market, weekend and holiday editions** describe the last session, so the sentence names it: "In the last session (Oct 9), US stocks closed
  lower."
- **No magnitude words** ("slightly", "sharply") and **no causes**. The sample example therefore reads **"US stocks closed lower. Canadian stocks
  closed higher."** rather than "slipped slightly".
- If no market qualifies, the result is null and the screen falls back to the server summary as today.

### 3.2 Reader order (normal text: Android 1.0×, iOS Default/Large)
```
┌ Daily brief                                   ⋯ ┐  nav title (D-B4)
│ After-close brief · Oct 7 · 1 min read [Sample] │  meta (caption) + badge
│                                                 │
│ US stocks closed lower.                         │  takeaway (sectionTitle, 2 short lines)
│ Canadian stocks closed higher.                  │
│ At the close on Oct 7, S&P 500 fell 0.24% and … │  server summary, body-small, textBody (D-B2)
│ Updated Oct 7 · Close · Oct 7 session           │  one freshness line (session label once, U-04)
│ ┌ ⚠ Latest available brief, from Oct 7. ───────┐│  one status banner; offline sentence added
│ └ You're offline: copy saved on this device. ──┘│  only when offline (U-05)
│                                                 │
│ Markets                                         │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐             │  MarketPerformanceTile ×3 (tinted surface,
│ │S&P 500  │ │Nasdaq   │ │S&P/TSX  │             │  no border, card radius); % change is the
│ │↓ −0.24% │ │Composite│ │Composite│             │  loud value with arrow + sign; level and
│ │7,772.36 │ │↓ −0.26% │ │↑ +0.31% │             │  points below; equal heights
│ │−18.59   │ │26,418.52│ │30,544.18│             │
│ └─────────┘ │−68.59   │ │+94.42   │             │
│             └─────────┘ └─────────┘             │
│ All values: Close · Oct 7                       │  shared state label once; a tile whose state differs
│ ▸ What is a market index?                       │  shows its own label (stale = caution colour)
│                                                 │
│ Market stories                                  │
│ ┌───────────────────────────────────────────┐   │  one grouped surface (StockCard, borderless,
│ │ Reuters · 2 hr ago            [Markets]   │   │  surfaceSecondary), rows divided inside —
│ │ IEA to accelerate oil reserve release, …  │   │  no card-in-card
│ │ Show details ▾          ↗ Read article    │   │
│ │ ─────────────────────────────────────────  │   │
│ │ CNBC · 1 hr ago               [Markets]   │   │
│ │ SpaceX's plans to buy more Nvidia GPUs …  │   │
│ │ SpaceX shows that companies are making a  │   │  preview = first sentence (D-B3)
│ │ return on their AI compute investments…   │   │
│ │ Show details ▾          ↗ Read article    │   │
│ └───────────────────────────────────────────┘   │
│ ▸ About these stories                           │
│                                                 │
│ From your watchlist                             │
│  (empty) Follow companies to see important news │  compact StockEmptyState: one line + one
│          and price moves here.  [Add companies] │  secondary button (U-07)
│  (populated) AAPL · Apple            ↑ +2.4%  › │  WatchlistUpdateRow: symbol/name, kind icon,
│              Apple rose 2.40% in the Oct 7 …    │  the server text, direction in words
│ ▸ About watchlist updates                       │  the "Watching a company…" note, folded
│                                                 │
│ Upcoming earnings                               │
│ Intel Corporation (INTC)                      › │  UpcomingEarningsRow
│ Oct 7 · After market close  [Confirmed]         │  date + timing; status badge (words)
│ EPS estimate USD 0.18 · General example         │  only when an estimate exists
│ ─────────────────────────────────────────────── │
│ Caribou Biosciences, Inc. (CRBU)              › │
│ Oct 8 · Before market open  [Estimated]         │
│ EPS estimate USD −0.42 · General example        │
│ Report dates can change until a company confirms│
│                                                 │
│ Learn                                           │  section 7 (U-12)
│ ┌ Concept of the day ────────────────────────┐  │  existing tinted card, tighter bottom (U-13)
│ └────────────────────────────────────────────┘  │
│ StockSteps+ insights (unchanged behaviour)      │
│ ▸ About this brief                              │
│ 📅 Previous briefs                            › │
└─────────────────────────────────────────────────┘
```

### 3.3 Large text (Android 1.3× and 2.0×, iOS AX XL and AX5)
```
│ After-close brief · Oct 7 ·        ⋯ │  meta wraps; badge below it (StockTitleWithBadge)
│ 1 min read                           │
│ [Sample]                             │
│ US stocks closed lower.              │  takeaway stays 2–4 short lines
│ Canadian stocks closed higher.       │
│ ▸ Full summary                       │  at accessibility sizes the server summary folds
│ Updated Oct 7 · Close · Oct 7 session│  (StockShowDetails; never removed) (D-B2)
│ ┌ ⚠ Latest available brief, from …  ┐│
│                                      │
│ Markets                              │  tiles → full-width rows, the existing StockIndexRow
│ S&P 500                              │  (values under the name), one per index
│ Close · Oct 7                        │
│ 7,772.36                             │
│ ↓ −18.59 (−0.24%)                    │
│ ──────────────────────────────────── │
│ …                                    │
│ Market stories                       │  same grouped card; Show details and Read article
│ Reuters · 2 hr ago                   │  stack (already does)
│ IEA to accelerate oil reserve …      │
│ Show details ▾                       │
│ ↗ Read article                       │
│ Upcoming earnings                    │  rows: badge drops below the date line; no single-line
│ Intel Corporation (INTC)           › │  forcing for values
│ Oct 7 · After market close           │
│ [Confirmed]                          │
```
Tiles stack whenever `StockComparisonLayout.sideBySide(widestToken, 3, width, gap, accessibilityText)` is false. The widest token is measured
from the tile's unbreakable pieces ("Composite", "26,418.52", "−0.24%", "−68.59") in the fonts actually used.

**Estimated fit** (from font sizes; the rule test and device checks confirm it):
- Android 360 dp: 104 dp per tile minus 24 dp of padding = 80 dp of text. "Composite" at 15 sp semibold ≈ 76 dp, so tiles fit at **1.0× only**.
  At 1.3× the tokens are about 100 dp, so the tiles stack.
- iPhone 16: 115 − 24 = 91 pt at Default/Large, so they fit. At XL and above they stack.

## 4. Component reuse
| Proposed (illustrative) | Decision | How |
|---|---|---|
| `MarketIndexSummary` / `MarketPerformanceTile` | **New `StockIndexTile`** (Compose + SwiftUI) plus a small layout helper `StockIndexTilesLayout` in `StockSemanticStyles` that reuses `StockComparisonLayout.sideBySide` and adds the tile padding. Tested in `ResponsiveLayoutRulesTest`. | No existing compact metric tile shows a value, a change and a state with arrow and words. `StockMetricGrid` cells don't carry `StockPriceChange` or a state label. The stacked form reuses `StockIndexRow` unchanged |
| `DailyBriefHeadline` | Core `DailyBriefDisplay.takeaway` + plain `Text` (`sectionTitle`) + `StockShowDetails` for the folded summary | No new UI component |
| `MarketStoryCard` | Existing `StockCard(bordered = false, containerColor = surfaceSecondary)` wrapping the existing story row + `StockDivider` | Grouped container, no nesting |
| `WatchlistUpdateCard` | `StockNavigationRow` (existing `icon`, `detail`) + `StockPriceChange` text for price moves | Direction in words and arrow; colour secondary |
| `UpcomingEarningsRow` | **Small new private composable/view per platform** in the brief files (title, date+timing line, `StockStatusBadge`, estimate line) with copy from `DailyBriefDisplay.earningsParts(e)` | `StockNavigationRow` can't hold a badge after the date |
| `BriefHistoryRow` | `StockNavigationRow` with title = edition label, `badge` = Sample, `detail` = "Oct 7 · 1 min read", `summary` = first sentence | Only the copy changes (`historyTitle` → `historyEdition` + `historyDetail`) |
| `BriefStatusIndicator` | One `StockBanner` with `DailyBriefDisplay.statusLines(brief, now, offline)`: kind WARNING when stale, INFO when only offline; each state keeps its own sentence | Replaces two banners; the Sample badge stays in the header and "Sample data" stays in About this brief |
| Empty watchlist | Existing `StockEmptyState` (Compose) / `StockSectionMessage` (iOS) with a secondary-style action | Copy in `DailyBriefDisplay` |

## 5. Android implementation (Compose)
- `presentation/brief/DailyBriefScreens.kt`:
  - Header: takeaway + server summary (or folded at large text) + one freshness line + one status banner.
  - Markets: tiles or rows through the layout helper (`BoxWithConstraints` + `rememberTextMeasurer` for the widest token, as `ComparisonScreen.kt`
    does).
  - Stories: wrapped in one grouped card.
  - Watchlist: compact empty state; populated rows with direction; note folded.
  - Earnings: structured rows.
  - Learn: grouped at the end.
  - History: new row copy.
- `presentation/brief/DailyBriefComponents.kt`: `BriefIndexTile`, `BriefEarningsRow`, `BriefIndexRow` (kept).
- `designsystem/components/StockIndexTile.kt` (new).
- `theme/StockSemanticStyles.kt` (+ `StockIndexTilesLayout`).
- `presentation/home/HomeComponents.kt`: the preview uses `takeaway` (D-B5); nothing else on Home.

## 6. iOS implementation (SwiftUI)
- `DailyBriefScenes.swift`: the same sections. The tiles use `ViewThatFits` guarded by the shared rule (a measured widest token via
  `UIFont`-based width as in `ComparisonResearchViews`, `relativeTo: .footnote` for value and caption). `dynamicTypeSize` is read in the body for
  live changes.
- `DesignSystem/StockDisclosure.swift` (or `StockComponents.swift`): `StockIndexTile`.
- `HomeComponents.swift`: the preview uses `takeaway` (D-B5).
- `app/shared/src/iosMain/.../IosBriefClient.kt`: only if a helper is needed (`DailyBriefDisplay.shared` is already reachable).

## 7. Risk assessment
| Area | Risk | Mitigation |
|---|---|---|
| **Financial accuracy: takeaway** | New derived sentence could misstate direction, imply today, or hide a missing or stale index | Rules in §3.1 with tests for every edition and state (`SESSION_CLOSE`, `PREVIOUS_CLOSE`, `LIVE_DELAYED`, `STALE`, `UNAVAILABLE`), mixed and unchanged, US/CA holidays, missing index, all MOCK scenarios; server summary kept verbatim (visible or folded); null → fallback. **Owner approval required (D-B1)** |
| Tiles | Truncation or overflow at large text or narrow width; colour-only direction | Shared measured rule, stack when unsure; arrow + sign + "Down/Up" in the spoken label (existing `BriefFormat.accessibility`); no `maxLines` on values; device matrix |
| Session label once | A tile with a different state (stale TSX) loses its label | Shared label only when all states are equal; otherwise per tile (test with `stale-index`) |
| Status banner | Stale and offline merged into one wrong statement | Separate sentences per state; tests for each combination (sample × stale × offline) |
| Watchlist note | Moving the server note into a disclosure could look like hiding a disclosure | It stays one tap away under a clearly named disclosure; owner decision D-B6 |
| Earnings | Estimated shown as confirmed; invented currency | Badge text comes from server `dateStatus` verbatim (Confirmed / Estimated / Tentative); estimate line only when `epsEstimate` exists; currency only if given (existing test) |
| Stories | Grouped card changes the 5D.4.2 "borderless sections" convention | One surface for the list, not per story; per call site; global default unchanged |
| Navigation and history | Row copy change breaks open-by-id | Actions unchanged (`OpenBrief(id)`); only labels change |
| Home | Preview changes meaning | Takeaway is a summary of the same rows; falls back to the first sentence when null |
| Offline, Sample, stale coexistence | Lost label | Sample badge (header) + banner (stale/offline) + About this brief sentence; tested per combination |

## 8. Testing strategy
- **Core unit tests** (`DailyBriefDisplayTest`):
  - `takeaway` across editions, states, mixed/unchanged, holidays and missing/stale indices;
  - never "today" outside live states; no magnitude or cause words (word-list assertion);
  - `statusLines` combinations; earnings parts (badge text verbatim, no estimate → no line); history edition/detail split;
  - shared session label.
- **Layout rule test** (`ResponsiveLayoutRulesTest`): tiles at 360 dp × 1.0 / 1.3 and 393 pt; accessibility always stacked; unknown width stacked.
- **Suites, run one after another:** `:core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test`,
  plus `:server:test` (unchanged, run because core changes).
- **Builds:** `:app:androidApp:assembleDebug`, then `xcodebuild` (generic, plus iPhone 16 ad-hoc with the MOCK URL).
- **Device matrix, local MOCK only, after asking:**
  - Android `emulator-5554` at 360 dp: 1.0×, 1.3× and 2.0×, each in light and dark.
  - iPhone 16: Default, AX XL and AX5, each in light and dark, plus a live switch.
  - Scenarios: normal, `market-hours`, `pre-market`, `weekend`, `holiday`, `us-open-ca-closed`, `missing-index`, `stale-index`, `no-stories`,
    `missing-publisher`.
  - States: offline (Android: remove the port forward; iOS: stop the server) and stale + offline together.
  - Screens: history; Home preview.
- **Checks:** no clipping, no overlap, nothing under bars, tiles never overflow, 48 dp/pt targets, reading order (header → takeaway → markets →
  stories …).
- **TalkBack/VoiceOver:** remain a tracked owner item.

## 9. Scope boundaries
**Included:**
- Daily Brief reader layout and copy presentation: takeaway, tiles, status banner, story grouping, watchlist empty state and folded note,
  earnings rows, Learn grouping, history rows.
- Home brief preview text (D-B5).
- New `StockIndexTile` + layout helper.
- Core display functions and tests.

**Excluded:**
- Backend, API, fixtures, news ranking, URLs, brief generation, calculations, notifications, entitlements, caching, watchlist behaviour, history
  semantics.
- B-1 sparklines.
- The rest of Home, Portfolio and Learn (5D.5.2).
- The pre-existing iOS Home AX5 heading break and history navigation (separate follow-up).
- Edition label wording ("After-close brief" stays; "summary" would change a core enum label used elsewhere).

## 10. Questions requiring owner approval
| ID | Decision | Options | Recommendation |
|---|---|---|---|
| **D-B1** | Derived takeaway headline (§3.1). Changes D-2 A from 5D.5 and **affects financial wording** | A: approve the rule as written (no magnitude words). B: allow "slightly" under a fixed threshold (e.g. < 0.5%). C: keep the server summary as the headline | **A** |
| D-B2 | Server summary under the takeaway | Visible as body-small at normal sizes, folded under "Full summary" at accessibility sizes / always folded / always visible | **Visible, folded at accessibility sizes** |
| D-B3 | Collapsed story preview | First sentence (current; never cut mid-number) / 3-line clamp with ellipsis (full text in Show details) | **First sentence**, no clamp: a clamp can cut "S&P 50…" as in 5D.5.1 F-5 |
| D-B4 | Header title | Nav title "Daily brief" only (current) / add in-page "Your Daily Brief" heading | **Nav title only** (an in-page title repeats it and costs a line) |
| D-B5 | Home preview text | Takeaway (no repeated numbers) / keep the summary's first sentence | **Takeaway**, falling back to the first sentence |
| D-B6 | "Watching a company doesn't mean you own it…" note | Fold under "About watchlist updates" / keep visible | **Fold** (one tap, named clearly) |
| D-B7 | Story list surface | One grouped card / keep borderless rows | **One grouped card** |
| D-B8 | Empty watchlist action label | "Add companies" (opens the Watchlist tab, same action as today) / keep "Go to Watchlist" | **"Add companies"** |

## 11. Suggested phases
| Phase | Scope | Gate |
|---|---|---|
| **A** | Core: `takeaway`, `statusLines`, shared session label, earnings parts, history split + tests; `StockIndexTilesLayout` + tests; header (takeaway, summary, one status banner) and tiles on both platforms; Home preview (D-B5) | D-B1, D-B2, D-B4, D-B5 |
| **B** | Story grouped card, watchlist empty state + folded note + populated rows, structured earnings rows | D-B3, D-B6, D-B7, D-B8 |
| **C** | Learn grouping, concept card spacing, history rows, visual consistency pass (radii, padding, spacing) | — |
| **D** | Full suites, builds, device matrix (§8), report `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_1B.md`, captures; commit only on request | Device checks after asking |

Each phase is reviewable on its own. A and B can ship together if preferred. Nothing changes on the server.
