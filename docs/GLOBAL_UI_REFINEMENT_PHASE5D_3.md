# Global UI Refinement Phase 5D.3 — Earnings visual polish

Date: 2026-10-14. Status: **committed and pushed** as "Add Global UI Refinement Phase 5D.3 Earnings visual polish with an EPS headline, key metric groups, a reaction headline with folded methodology, a Sample badge and collapsed sources on Android and iOS" (on top of `3d7b000`, Phase 5D.2). Owner reference: the two-screen Earnings
mock-up (Details + Results), a direction, not a pixel specification. Owner decisions (approved in chat, "go ahead with your recommendation"):
- built EPS headline with the server summary as fallback;
- reaction headline tinted with an arrow and a sign;
- no quarterly history on Results;
- no ⋮ menu;
- D-1 compact "Sample" badge;
- 5D.2 committed first (`3d7b000`).

Presentation only. Nothing changed in:
- EPS/revenue calculations, classifications or currency rules (5D.1B);
- providers, dates or windows;
- price-reaction calculations;
- the API, Firebase, reminders, StockSteps+ entitlements, AI or navigation destinations.

No server code changed.

## 1. Changes

### Earnings Details ("what happened?")

| Area | After |
|---|---|
| Header | Unchanged 5D.2 header. The status badge now has a compact **Sample** badge (D-1) instead of the in-page sample strip; the badge speaks the full sentence, and the page notes and the app-wide strip still carry it |
| Takeaway | The quarter, then **"EPS missed estimates by 3.0%."** (`EarningsFormatter.epsHeadline`: only from the server's BEAT/MET/MISS; UNAVAILABLE → the server summary), then the badge, **Reported EPS / Estimated EPS**, one explanation and "Learn more" (EPS lesson) |
| EPS / revenue | Three key numbers in the shared `StockMetricGrid` (drops columns at large text). EPS: Reported · Estimate · Surprise. Revenue: Reported · Estimate ("—", plus the existing no-estimate reason) · YoY growth. Difference, basis and quarter-over-quarter go smaller below (`EarningsLines`) |
| Reaction | Title + **"↑ +0.2%"** (`StockPriceChange`: arrow, sign, gain/loss colour), Before / After, the non-causal sentence. Window, source, market and the lesson sit under a collapsed **"How it's measured and source"** |
| Insights | Neutral icon per category (surprise %, growth bar chart, trend layers, mixed info; never a direction cue) |
| History | Unchanged from 5D.2 (badges, tooltip-matching rows, legend) |

### Earnings Results ("the detailed numbers")

| Area | After |
|---|---|
| Header | Company, quarter title, fiscal period, report date and a **Sample** badge. "Published by the source…" moved to the disclosures |
| EPS | **Actual · Estimate · Surprise %**, then the badge, explanation, basis ("Measured as GAAP diluted EPS."), Difference, and "What is EPS?" |
| Revenue | **Actual · Estimate · YoY growth** (YoY folded in from the growth card). "—" plus "No analyst revenue estimate is available…" when there's no estimate; never zero, never a miss. Prior-year revenue becomes a "Year ago (Q2 FY2025)" line. The YoY card only stays when its percent is unavailable, so its reason still shows |
| Reaction | Window chips, headline % in the title row, Before / After / Price change, the chart (marker and rings unchanged), the explanation, and warnings as a banner. The measured window, the chart key, the context line, lessons and price sources fold under **"How to read this"** |
| Learn and sources | "Learn more" as grouped navigation rows, the company button, then a collapsed **"Sources and disclosures"**. It holds the publication time, sources and notes, the sample sentence, "This is education, not investment advice." and the price-cause note, each **once** (`distinct()`; the server notes already held the sample sentence) |
| Copy | Sentence case: "Earnings per share (EPS)", "Is the business growing?", "Compared with the previous quarter", lesson titles ("Revenue vs profit"…) |

### Components and display fields
- **New Compose `EarningsComponents.kt` and SwiftUI `EarningsComponents.swift`:** `EarningsKeyMetrics` (over `StockMetricGrid`),
  `EarningsReactionHeadline` (over `StockPriceChange`), `EarningsDisclosure` (48 dp/pt toggle announcing Expanded/Collapsed), `EarningsNotes`, and
  the insight icon mapping.
- **Core, formatting only:**
  - `ResultCard.keyMetrics` / `.secondary`; `EarningsDetailsState.headline`, `reactionHeadline`, `reactionDirection`, `reactionKey` and `reactionDetails`.
  - `ComparisonCardView.keyMetrics` / `.secondary`; `EarningsResultsState.publication` and `yearOverYearFolded`.
  - `EarningsPriceReactionState.headline`, `direction`, `keyLines` and `detailLines`.
  - `EarningsFormatter.epsHeadline`.
- **Shared `EarningsDisplay`:** Sample/disclosure copy, `insightIcon`, `takeawayMetrics`, `resultsDisclosures`.
- **Reused:** `StockStatusBadge`, `StockTitleWithBadge`, `StockBanner`, `StockNavigationRow`, `StockMetricGrid`, `StockActionLayout`, `StockInsightCard`,
  `StockTrendChart`, `EarningsLines` (5D.2). The unused Compose `EarningsSourceFooter` was removed.

### Files
- core: `earnings/EarningsPresentation.kt`, `EarningsResults.kt`, `EarningsPriceReaction.kt`; tests `EarningsPhase5D3Test.kt` (new),
  `EarningsResultsTest.kt` (title now sentence case).
- shared: `presentation/earnings/EarningsComponents.kt` (new), `EarningsDisplay.kt`, `EarningsScreens.kt`; test `EarningsDisplayTest.kt`.
- iOS: `EarningsComponents.swift` (new), `EarningsScenes.swift`.
- docs: this report, `docs/design/earnings_phase5d_3/`, handoff/status entries.

## 2. Screenshots (`docs/design/earnings_phase5d_3/`, Android emulator-5554, 360 dp, local MOCK, CNR.TO)

| Capture | Shows |
|---|---|
| `after-a360-details-top.png` | Header with Reported + Sample badges, the action pair, the takeaway headline, badge, Reported/Estimated EPS |
| `after-a360-details-revenue-reaction.png` | Revenue key metrics ("—" estimate) and the reaction card "↑ +0.2%" with Before/After |
| `after-a360-details-insights-history.png` | Icon insight cards and the history chart (tooltip C$1.94 vs C$2.00) |
| `after-a360-details-revenue-2.0x.png` | 2.0×: metrics stack into one column, nothing clipped (taken before the "YoY growth" label change) |
| `after-a360-results-top.png` / `-revenue.png` | Results Actual · Estimate · Surprise and Actual · Estimate · YoY growth; Sample badge |
| `after-a360-results-reaction.png` / `-reaction-folded.png` | Reaction headline, chart, warning banner, "How to read this" collapsed |
| `after-a360-results-disclosures-open-before-dedupe.png` | The open disclosures **before** the duplicate fix (sample sentence twice; fixed and unit-tested, not re-captured) |
| `after-a360-results-dark.png` | Results in the app's Dark theme (switched in Settings, then restored to Light) |

"Before" is the 5D.2 set in `docs/design/earnings_phase5d_2/`.

## 3. Verification

| Check | Result |
|---|---|
| `:core:jvmTest` | **PASS** 500/0. An earlier run had a timeout in `PortfolioSessionStateTest` (untouched, known flaky under load); it passed alone and in the final full run |
| `:core:iosSimulatorArm64Test` | **PASS** 500/0 |
| `:app:shared:testAndroidHostTest` | **PASS** 237/0 |
| `:app:shared:iosSimulatorArm64Test` | **PASS** 233/0 |
| `:app:androidApp:assembleDebug` | **PASS** |
| unsigned `xcodebuild` | **PASS** (BUILD SUCCEEDED, re-run on the final code) |
| `:server:test` | NOT RUN (no server change) |
| Android device, light 1.0×, 2.0× (Details revenue), dark (Results) | **PASS**: no overlap, no letter-by-letter labels, no clipped amounts, no horizontal scroll, C$ kept, "—" for a missing estimate |
| Android 1.3× for 5D.3 | NOT RUN (1.3× was verified for the shared rows in 5D.2) |
| iOS runtime | **NOT RUN**. Details is BLOCKED: the simulator's signed-in session needs the Firebase Auth emulator. Results is reachable but wasn't captured |
| TalkBack / VoiceOver | NOT VERIFIED (deferred by the owner) |

New tests:
- `EarningsPhase5D3Test` (5):
  - the headline only comes from the classification (beat/met/miss, no percent, unavailable, null);
  - the Details key metrics are the card's own values ("—" estimate, YoY third, QoQ secondary, nothing lost);
  - the server-summary fallback;
  - the reaction headline equals the Change line, Before/After up front, Window/Source folded;
  - the Results key metrics, YoY folding and the not-available case.
- `EarningsDisplayTest` (+3):
  - neutral insight icons;
  - the takeaway pair;
  - disclosures appear once, including when the server notes already contain the sample sentence.

## 4. Parity
Both platforms use the same shared fields and copy. The Compose and SwiftUI components mirror each other: same metric grid rule, same disclosure
pattern, SF Symbols mapped from the same icon keys. Native differences that remain:
- iOS Details shows the sample note as a badge only (Android the same since D-1).
- Gestures differ (iOS chart drag vs Android tap).

## 5. Known limitations
- The Details EPS and revenue cards stack at 360 dp (the shared metric grid decides inside each card). A side-by-side pair of cards, as in the
  reference's wide layout, was not added.
- The history chart has no y-axis labels: the core supplies none, and adding formatted ticks was left out to avoid new chart data.
- The disclosure de-duplication was fixed after capture (unit-tested, not re-captured on device).
- iOS runtime is unverified for 5D.2 and 5D.3.

## 6. Owner decisions still open
- D-3: approve the Firebase Auth emulator (or signing the simulator out) so iOS Earnings Details can be verified.

## 7. Recommendation
**PASS WITH CONDITIONS.** The hierarchy matches the reference's intent and is verified on Android in light, dark and at 2.0×. All suites pass and both
apps build. Conditions: iOS runtime check (D-3) and an on-device re-capture of the de-duplicated disclosures.
