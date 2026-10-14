# Global UI Refinement Phase 5D.4.2 — Borderless visual polish (PLAN, awaiting owner approval)

Date: 2026-10-14. Status: **approved and implemented** (see `GLOBAL_UI_REFINEMENT_PHASE5D_4_2.md`).

Repository state:
- `main` = `origin/main` at `a4f35c4` (5D.4.1 committed and pushed; earlier reports calling it uncommitted are out of date).
- Working tree: only the backend session's root `PROJECT_HANDOFF.md` and `docs/project-status.md` (left untouched).

Evidence: the 5D.4.1 captures in `docs/design/earnings_phase5d_4/` (Android 360 dp light 1.0×/1.3×/2.0×, dark; iOS Results light) and the current
Compose/SwiftUI code.

## 1. Current visual issues

| ID | Sev | Issue |
|---|---|---|
| B-01 | P1 | **Every content block is an outlined card** (takeaway, Revenue, reaction, Insights, AI card, Learn, Results EPS/Revenue/reaction). On a 360 dp screen, about eight 1 dp outlines stack up: busy, not "calm dashboard" |
| B-02 | P1 | **Dead space after toggles.** "Show details" is a 48 dp row followed by `spacedBy(sm)` and the card's 16 dp bottom padding. In the takeaway, "Learn more" adds another 48 dp row, leaving about 60 dp of empty space (`after-5d41-a360-details-top.png`). Results EPS has the same gap before "What is EPS?" |
| B-03 | P1 | **Header is tall:** a 28 sp name next to a 48 dp logo, a badges row, two date lines and full-height 56 dp buttons. At 1.0× the first EPS number starts at about 60 % of the screen height |
| B-04 | P1 | **Revenue badge competes with the title:** "Revenue [Comparison unavailable]" shares the line at 1.0× and is visually as loud as the title |
| B-05 | P2 | **Uneven section rhythm:** `LazyColumn spacedBy(md=12)` between items, while section titles ("Insights", "Earnings history", "Learn") have no extra top space, so headings sit as close to the card above as to their own content |
| B-06 | P2 | **Equal-weight metrics:** reported, estimate and surprise all use the same bold `numberEmphasis`; the outcome (surprise) isn't the quietest or the loudest by design |
| B-07 | P2 | **History chart block:** tooltip line, legend and plot are stacked with uneven gaps, and the plot is 150 dp at every width |
| B-08 | P2 | **Insight rows:** 24 dp icon top-aligned against a caption, so the icon and the period sit off by about 4 dp |
| — | P0 | **None found** (no clipping, overlap or broken words in the 5D.4.1 captures). iOS Details remains BLOCKED (owner sign-in) |

## 2. Earnings Details — proposed

1. **Sections, not cards (B-01).**
   - Each block is a borderless surface: `StockCard(bordered = false)` on Compose, `.stockCard(bordered: false)` on iOS. These are existing per-call-site options, so there is no global default change.
   - The light surface (#FFFFFF) on the #F7FAFD background gives the grouping; the dark surface sits on the dark background.
   - Corner radius stays at the existing `shapes.card`.
2. **Section rhythm (B-05).**
   - 20 dp (`xl`) between sections instead of 12; section headings get 8 dp (`sm`) to their content.
   - The two cards of one topic stay 12 dp apart (takeaway → Revenue → reaction are one "results" group).
3. **Header (B-03).**
   - Logo `logoCompact` (40 dp) next to the name at `sectionTitle` (20 sp) instead of `screenTitle` (28 sp). The toolbar already says "Earnings details", and the name still wraps without truncation.
   - The badges row stays. The Reported and Next lines stay as they are (no reminder-sheet change).
   - Actions: the same `StockActionLayout` pair at the existing `buttonHeight`; only the gap above them changes, from `sm` + `xs` to `md`. They still stack at large text.
4. **Takeaway (B-02, B-06).** Order: quarter → headline → badge → Reported · Estimate · Surprise → one sentence → [Show details] [Learn more] on **one row** (`FlowRow`/`ViewThatFits`, wrapping at large text). This removes about 48 dp.
5. **Revenue (B-04).**
   - The title alone on line 1. The status badge on line 2, `COMPACT` size, only when its kind is NEUTRAL ("Comparison unavailable"); a Beat/Missed badge stays beside the title as today.
   - Metrics and "No estimate" unchanged; "Show details" and the lesson link share one row.
6. **Reaction.** The headline % moves under the title as its own line (large `numberMedium`), then Before/After (dates as detail, unchanged), the sentence and the disclosure row. No change to text or methodology.
7. **Insights (B-08).** One borderless surface, rows with the icon centred on the title's first line, period caption above the title, 12 dp row padding instead of 8 + 16, and lightweight dividers kept between rows. All insights and server text kept.
8. **History (B-07).**
   - Title → legend → tooltip line → plot, 8 dp apart.
   - Plot height from the existing shared `StockChartLayout.height(width)` (≈ 45 % of width, clamped), about 130 dp at 360 dp instead of a fixed 150.
   - History rows as one borderless surface with dividers. Values, labels and data unchanged.
9. **AI card and Learn.** The same borderless surface; content unchanged.

## 3. Earnings Results — proposed
1. Header: the same compact treatment as Details (company row 40 dp logo; the title "Q3 FY2026 results" stays `sectionTitle`; period lines stay captions).
2. Beginner takeaway: keep the education tint (`educationContainer`, already borderless) and tighten its padding to 16 dp; "Show details" is inline.
3. EPS and Revenue: borderless surfaces. The Revenue badge rule follows Details item 5. "Show details" and "What is EPS?" are adjacent rows with no extra gap, and the B-02 fix removes the trailing 48 dp gap.
4. Reaction: borderless surface; window chips, chart, marker and rings unchanged. The headline % moves under the title as on Details.
5. Learn more (navigation rows) and "Sources and disclosures": borderless, as on Details.
6. The first screenful (iPhone 16, standard text) shows the header, takeaway and the EPS metrics. Measured before/after, not assumed.

## 4. Borders: removed vs kept

| Removed (content surfaces, Earnings screens only) | Kept (functional) |
|---|---|
| Details: takeaway, Revenue, separate EPS card (when shown), "Not reported yet", reaction, Insights surface, AI card, Learn surface, history rows group | All buttons (`StockButton` OUTLINED "Remind me", "View company details") |
| Results: EPS, Revenue, growth cards (when shown), reaction, StockSteps+ section surfaces in `EarningsPremiumUi` / `EarningsPremiumViews` (content only), Learn surface | Text fields (AI question), chips (window selector), status badges, banners (`StockBanner` warning/sample), the app-wide Sample strip, bottom navigation, dividers between rows |
| Event and Calendar screens: **not changed** (outside this phase) | Global `StockCard` / `stockCard` defaults: **unchanged** (`bordered = false` per call site only) |

## 5. Spacing and typography (tokens only)

| Element | Now | Proposed |
|---|---|---|
| Screen horizontal padding | `screen` 16 | unchanged |
| Between sections | `md` 12 | `xl` 20 |
| Within a topic group | 12 | `md` 12 |
| Section internal padding | `cardPadding` 16 | 16 (Results takeaway also 16) |
| Title → content | `xs`/`sm` mixed | `sm` 8 |
| Metric rows (stacked) | `md` 12 | `sm` 8 inside a card (`StockMetricGrid` rows mode keeps its own rule) |
| Show details ↔ Learn more | two 48 dp rows | one row, 8 dp gap, both 48 dp touch targets |
| Company name | `screenTitle` 28 | `sectionTitle` 20 |
| Metric values | all `numberEmphasis` bold | Reported value bold; Estimate and Surprise `numberMedium` (600). Surprise keeps its sign and word, never colour alone. This needs an optional emphasis flag on `EarningsKeyMetrics`, an Earnings-only wrapper |

## 6. Responsive behaviour
- **1.0×:** the three metrics in one row (shared `StockLayout.metricColumns`); header actions side by side; "Show details" and "Learn more" on one row; badge beside "Revenue" only for Beat/Missed.
- **1.3×:** the same rules decide. Metrics may drop to aligned rows when the measured values don't fit; actions stay side by side only while `StockActionLayout` says both labels fit.
- **2.0× / AX:** aligned label/value rows; actions stacked; the toggle row wraps ("Learn more" under "Show details"); badges below titles (`StockTitleWithBadge`). No text shrinking, no truncation of dates or values.

## 7. Android and iOS approach
- **Compose:** `StockCard(bordered = false)` at each Earnings call site; `LazyColumn` spacing via per-item top padding (sections 20, groups 12); a `FlowRow` for the toggle + lesson row; `StockChartLayout.height` for the history plot. `EarningsShowDetails` gains an optional `trailing` slot so "Learn more" can share its row.
- **SwiftUI:** `.stockCard(bordered: false)`; `ViewThatFits` (`HStack` → `VStack`) for the toggle row; the same token values; `EarningsHistoryChart` height from `StockChartLayout.height`.
- **Shared Kotlin (display only):** `EarningsDisplay.badgeBelowTitle(kind)` (NEUTRAL → below) and section spacing constants, so both platforms decide the same way.

## 8. Files expected to change
- shared: `presentation/earnings/EarningsScreens.kt`, `EarningsComponents.kt`, `EarningsDisplay.kt`, `EarningsPremiumUi.kt` (borders only); tests in `EarningsDisplayTest.kt`.
- iOS: `EarningsScenes.swift`, `EarningsComponents.swift`, `EarningsPremiumViews.swift` (borders only).
- docs: `GLOBAL_UI_REFINEMENT_PHASE5D_4_2.md` (report), captures `docs/design/earnings_phase5d_4_2/`, handoff entry.
- **Not touched:** core, server, API/DTOs, calculations, classifications, currency rules, fixtures, reminders, navigation, auth, entitlements, AI logic, other screens, global component defaults.

## 9. Regression risks
- Borderless white surfaces on light backgrounds can look washed out. If the contrast between `surface` and `appBackground` is too weak in light mode, keep a hairline `borderSubtle` instead (decision 5D42-1).
- `FlowRow` / `ViewThatFits` for the toggle row must keep both 48 dp targets and the Expanded/Collapsed announcement.
- Moving the Revenue badge must not affect Beat/Missed badges (unit-tested rule).
- Chart height change: the tooltip, the x/y labels and the drag area must still fit at 2.0× / AX.
- StockSteps+ card border removal touches premium screens' Earnings sections only. Digest screens are excluded unless approved.

## 10. Verification matrix and screenshots

| Check | Android 360 dp | iPhone 16 |
|---|---|---|
| Details light 1.0× / 1.3× / 2.0× | yes (same scroll positions as the 5D.4.1 captures) | Details BLOCKED until owner sign-in |
| Details dark 1.0× | yes | BLOCKED (sign-in) |
| Results light standard / large / AX | 1.0× + 2.0× | Large + AX XL (fresh launch and live change) |
| Results dark | yes | yes |
| Expand/collapse (takeaway, Revenue, EPS, reaction, insights, sources) | yes | Results only |
| Navigation (Remind me sheet opens, Company details, Results ↔ Details) | yes | Results → Company |

- Suites, run sequentially: core JVM and iOS (only if core is touched; none planned), shared Android and iOS, `assembleDebug`, `xcodebuild`.
- Before/after pairs at the same data (CNR.TO Q2 FY2026, AAPL Q3 FY2026 on iOS), theme, scale and scroll position.
- Also recorded: first-screen content (what is visible without scrolling) and the total scroll height before and after.

## 11. Owner decisions

| ID | Decision | Recommendation |
|---|---|---|
| 5D42-1 | Fully borderless surfaces (white on #F7FAFD in light), or a hairline `borderSubtle` in light mode only if the contrast looks too weak on device | Borderless, and re-check on device; fall back to the hairline only with your approval |
| 5D42-2 | Company name at `sectionTitle` (20) instead of `screenTitle` (28) on Details | Yes: the bar already titles the screen; still wraps and scales |
| 5D42-3 | Metric emphasis: reported value bold, estimate and surprise semibold | Yes |
| 5D42-4 | Include the StockSteps+ Earnings sections (premium cards on Results) in the border removal | Yes, Earnings screens only; the Digest screens stay as they are |
| 5D42-5 | iOS Earnings Details verification | Still needs your sign-in on the local Auth emulator |
