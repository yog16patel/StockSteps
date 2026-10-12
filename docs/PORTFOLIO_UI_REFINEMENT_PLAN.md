# Portfolio UI Refinement — audit and plan (awaiting approval; nothing implemented)

Status: **plan only** (2026-10-11). No Portfolio code was changed. Written after the Phase 5C.1 closure pass, against the code at HEAD
"Add end-of-session handoff after Global UI Refinement Phase 5C.1 and point to the Phase 5D Earnings audit" plus the 5C.1 closure edits
(which don't touch Portfolio); committed in "Close Company Comparison Phase 5C.1 with section-based sticky header and spacing fixes, and add the Portfolio UI audit and Phase P-A display rules".

Scope: the Portfolio tab dashboard on Android (Compose, `app/shared/.../presentation/portfolio/`) and iOS (SwiftUI, `app/iosApp/iosApp/Portfolio*.swift`):
summary/hero, performance chart, holdings, allocation, cash and dividends, transactions, insights entry, empty / loading / error / offline / sample
states. Holding details, Portfolio Insights and the forms are listed only where they share components.

Rules that stay (CLAUDE.md, `docs/GLOBAL_UI_REFINEMENT_PLAN.md`): UI only — **no** presenter, API, calculation, ledger, entitlement, navigation
or fixture change; exact decimal strings from `PortfolioUiState` are formatted, never recomputed; missing data stays missing (never `0.00`);
gain/loss in words + sign + arrow, not colour alone; 48 dp / 44 pt targets; five tabs; `primaryAction` for filled buttons (brand blue `#1683FF`
= `primary` stays for accents, charts, selection and links); layout decisions in shared `StockSemanticStyles` objects with tests; screen copy in a
core/shared `*Display` object.

## 1. Evidence

- Code read in full: `PortfolioScreen.kt` (462 lines), `PortfolioHistoryChart.kt`, `PortfolioPresentation.kt`; iOS `PortfolioScreen.swift`
  (395), `PortfolioHistoryChart.swift`; `PortfolioUiState` / `PortfolioHoldingRow` / allocation models (core).
- Device captures from Phase 3 / 3.1 (`docs/design/phase3/`, `docs/design/phase3_1/`): Android 427 dp and 360 dp × 1.3, iOS light/dark/XXXL.
  **No new Portfolio device captures were taken in this pass** — the audit is code-based plus those captures; the first implementation phase
  starts with fresh "before" captures.
- Reference: the owner's "StockSteps — Portfolio (Reference for UI Refinement)" sheet (Android/iOS light, iOS dark, holdings, allocation donut,
  collapsed sections, empty state, menu and actions) and `docs/design/stocksteps-portfolio-reference.png`.

## 2. Current structure (both platforms, same order)

Header (title · ⓘ About these numbers · ⋯ Refresh / Account settings / Delete account) → My Portfolio | Practice segmented control → (MOCK only)
"Sample scenario" select field + SAMPLE banner → loading bar → one sync banner (offline / error + Retry) → sign-in banner → account selector
("Name · CAD ▾" + "+ Add") → **hero card** (Total portfolio value, `largeNumber`; today's change or "Today's change unavailable · Why?";
divider; Invested cost · Unrealized P/L · Realized P/L grid; all-accounts line; notice; freshness lines; SAMPLE badge) → full-width primary
**"+ Add transaction"** → **Portfolio performance** card (pills 1D…ALL, nothing selected until tapped; chart or "No performance history shown")
→ **Holdings (n)** rows → **Allocation** (bars by holding and by currency) → **Cash and dividends** → **Transactions (n)** (5 + Show all) →
**Portfolio insights** row.

## 3. Prioritised issues

Severity: **P1** = hurts comprehension or accessibility on common devices; **P2** = noticeable polish / consistency; **P3** = nice to have.

| # | Area | Issue (evidence) | Pri | Proposal (UI only) |
|---|---|---|---|---|
| 1 | Summary | The value story is split over three blocks: hero → "+ Add transaction" button → separate performance card. The reference reads total → change → chart → ranges in one card. The primary button between the number and its chart breaks NUMBER → CONTEXT. | P1 | One **overview card**: label + ⓘ, total, today's change, chart, range pills, then the Invested / Unrealized / Realized rows. Move "Add transaction" out of the hero path (see #9). |
| 2 | Performance | No range is selected on open; the card shows "No performance history shown · Choose a period to load dated values" (history loads only on a pill tap — presenter behaviour). First-time users see an empty chart area. | P1 | **Decision needed (D1)**: (a) keep on-demand loading but replace the empty block with one compact line + "Show 1M" action, or (b) have the Scene call the existing `onHistory("1M")` once on first appearance (no presenter change, but it adds a dated-history request on every open). Recommend (a) for MOCK-first/cost safety unless the owner prefers (b). |
| 3 | Summary | "Overall return" is not on screen as a percentage: the hero shows Unrealized P/L as an amount only. `PortfolioUiState` has no total-return % field. | P1 | **Decision needed (D2)**: computing a % in the UI would be a financial calculation (not allowed in a UI pass). Options: show the amount only with the label "Unrealized gain/loss" (now), or add an exact-decimal field in the presenter in a separate, approved logic change. |
| 4 | Accessibility (iOS) | Most Portfolio text uses `StockStepsTheme.font(type.x)` without `relativeTo:` (34 call sites), so each token follows the Dynamic Type curve nearest its size; small styles (`caption`, `tiny`) grow faster than values — the same defect found and fixed in Compare 5C.1 (a period drawn larger than its value at XXXL). The hero total uses `minimumScaleFactor(0.7)` (shrinks instead of wrapping; Android wraps). 9 hard-coded `44` frames instead of `dims.touchTarget`. | P1 | Pair value + caption on one curve (`relativeTo: .footnote` / `.body` per row type, as CLAUDE.md requires); remove the scale factor and let the total wrap at a currency boundary (shared rule, #C3); use `dims.touchTarget`. |
| 5 | Allocation | Bars by holding and by currency are accurate but read as a list of equal-weight rows; the reference uses a donut + legend. Allocation can legitimately exceed 100 % or be negative (borrowed / negative cash — About sheet), which a donut cannot show honestly. | P2 | New shared **`StockAllocationDonut`** + legend rows, drawn only when every slice is present, ≥ 0 and the slices sum to ≤ 100 % (shared rule `StockAllocationLayout.donutAllowed`); otherwise keep today's bars. Legend = colour dot + label + % (strong) + amount (muted); the colour is never the only cue (labels always present). Max 6 slices + "Other" only if the presenter already groups them — no client-side regrouping of amounts. |
| 6 | Holdings | Rows are good (one spoken description, stacking at 320 dp / 1.3×) but dense on the right: value, "↑ +25.00%", "(+200.00)" on three lines. MOCK samples have no logos, so the avatar shows a cramped ticker box. | P2 | Two right-hand lines: value (strong) + signed % (coloured, arrow); the amount stays in the spoken label and holding details. Sort control "Total value ▾" (display order only: value, name, change %) — **D3**: optional. Avatar fallback = monogram on `surfaceSecondary` (shared with Watchlist). |
| 7 | Typography / density | Section titles (`sectionTitle`) sit outside cards with `md` top padding while hero/cards use `md` gaps — every section looks equally important. Freshness lines, notice and SAMPLE badge stack at the bottom of the hero (up to 4 small lines). | P2 | Hierarchy: overview card (largeNumber) › section cards with in-card titles (`cardTitle`) › meta. Freshness collapses to one line "Prices Oct 8, 3:00 PM UTC · FX Oct 8" with the full text in ⓘ; notice stays visible (caution). Use `StockTitleWithBadge` for "Total value [Sample]" instead of a badge at the end. |
| 8 | Sample / dev state | The MOCK "Sample scenario" select field takes the top of the screen above the numbers on every MOCK open. | P2 | Move it into the ⋯ menu ("Sample scenario…", MOCK builds only) and show the active scenario as a SAMPLE badge on the overview title + the existing read-only banner. No change for REAL builds (field never shown). |
| 9 | Actions | Three adds compete: "+ Add" (account), "+ Add transaction" (primary, full width), and Create portfolio (empty). The reference puts "Add a holding" under the holdings list and groups the rest in a menu. | P2 | Primary action only in empty states. With holdings: a tonal "+ Add transaction" row at the end of the Holdings card + "Add transaction" first in the ⋯ menu. Menu groups (existing flows only): Add transaction · Refresh · Account settings · (MOCK) Sample scenario · About these numbers · Delete account… . **No new features** from the reference ("Manage lists", "Set cash balance", "Send feedback", "Common questions") unless the owner asks — each maps to work outside this UI pass. |
| 10 | Empty state | Good copy, single action. The reference adds an illustration, a secondary "Learn how portfolios work" and a "Popular ideas" list with + buttons. | P2 | Keep the `StockEmptyState` v2 (icon tile, title, one line, primary + secondary action → existing Learn content / Practice). **Do not add "Popular ideas"**: a list of named stocks with "+" on a portfolio screen reads as a suggestion to buy, which conflicts with the no-advice rule. If wanted, link to Discover Stocks or the Practice Portfolio instead. |
| 11 | Loading | Android shows a thin `LinearProgressIndicator` above everything; iOS shows `ProgressView("Loading portfolio")` — different, and both shift the layout when data arrives. | P2 | Shared skeleton: overview-card skeleton + 3 `StockRowSkeleton` rows on both platforms (existing components), spoken "Loading portfolio". Refresh while data is shown keeps the content and shows the small indicator only. |
| 12 | Chart consistency | Compose `StockTrendChart` (line, touch details, short first/last dates) vs Swift Charts (line + point marks, a selection line above the chart). Different marks and selection behaviour. | P2 | Same spec on both: line in `primary`, subtle area fill (10 % → 0 %), no point marks, gaps for missing values (unchanged), first/last short dates, selection text above the chart on both ("Oct 8, 2026 · CAD 1,850.00"). Line colour stays brand blue regardless of direction (colour must not imply good/bad); the change text carries direction in words. |
| 13 | Holding details | Three text buttons ("Company details", "Price alerts", "Add to watchlist") stacked under a metric grid. | P3 | `StockNavigationRow` group (as Compare "More tools"); same on iOS. |
| 14 | Transactions | Always expanded below cash; long ledgers push Insights far down. | P3 | Collapsible "Transactions (n)" section (shared collapsible card from Compare 5C.1), open by default only when n ≤ 5. |
| 15 | Account selector | Full-width selector + "+ Add" row above the hero for single-account users. | P3 | Compact account chip in the overview card header ("TFSA · CAD ▾"); "+ Add account" moves into the chip's menu. Hidden chip text never truncates (2 lines). |
| 16 | Dark mode | Phase 3 dark captures look correct; hero/sections use tokens. Not re-verified since 3.1; Android dark only via the in-app Theme setting. | P3 | Re-capture light/dark both platforms in phase P-A. |

## 4. Proposed reusable components (shared rules first, then Compose + SwiftUI)

| Component | Where | Notes |
|---|---|---|
| **C1 `StockOverviewCard`** (title + badge + info action, big value, change line, chart slot, range pills, metric rows, meta line) | `designsystem/components` + iOS `DesignSystem` | Reusable by Home's `PortfolioSummaryCard` and Practice later; all parameters optional with today's defaults. |
| **C2 `StockAllocationDonut` + `StockLegendRow`** | Compose Canvas / SwiftUI `SectorMark` | Spoken: "AAPL, 72.97 percent, CAD 1,350.00". Decorative ring hidden from screen readers; legend rows carry the data. |
| **C3 `StockAllocationLayout`** (`donutAllowed(percents)`, slice colour order) and **`StockValueWrapRule`** (break a long total at the currency boundary instead of scaling) | `theme/StockSemanticStyles.kt` + tests in `ResponsiveLayoutRulesTest` | Platforms only measure and call them. |
| **C4 `PortfolioDisplay`** (copy: titles, empty/loading/error lines, menu labels, spoken labels for overview/legend) | `presentation/portfolio/` next to `PortfolioPresentation` (or core, as `ComparisonDisplay`) | Same text on both platforms; tests. |
| **C5 `StockEmptyState` v2** (icon tile, title, message, primary + secondary action) | both | Plan step 2 gap, reused by Watchlist/Screener later. |
| **C6 Collapsible section card** | extract from Compare 5C.1 (`SectionHeader` / `sectionView`) | Used for Transactions; Compare keeps its behaviour. |
| **C7 Chart spec** for `StockTrendChart` / Swift Charts (area fill, no points, selection line) | both | Optional parameters; existing callers unchanged. |

## 5. Android / iOS differences found

| Topic | Android (Compose) | iOS (SwiftUI) | Target |
|---|---|---|---|
| Loading | top `LinearProgressIndicator` | centred `ProgressView` with text | shared skeleton (#11) |
| Total value overflow | wraps | `minimumScaleFactor(0.7)`, 2 lines | wrap rule (#4, C3) |
| Text scaling | `sp` × font scale (uniform) | per-token Dynamic Type curves (uneven) | pair curves (#4) |
| Touch targets | `dims.touchTarget` / `IconButton` | hard-coded `44` | `dims.touchTarget` |
| Chart | `StockTrendChart`, touch details | Swift Charts, point marks, selection line | one spec (#12) |
| About | `AlertDialog` | medium-detent sheet | keep native (documented) |
| Menus | Material `DropdownMenu` | SwiftUI `Menu` | keep native |
| Refresh | ⋯ menu | ⋯ menu + pull-to-refresh | keep (pull-to-refresh is iOS idiom) |
| Holding details actions | text `StockButton`s | plain `Button`s with `minHeight: 44` | `StockNavigationRow` (#13) |

## 6. Implementation phases (each its own reviewable change; both platforms together; stop for review after each)

- **P-A — Baseline and shared rules** (no visible change): fresh before captures (Android 360 dp 1.0×/1.3× light/dark, iPhone 16 Large/XXXL
  light/dark, iPhone SE) on local MOCK; `PortfolioDisplay`, `StockAllocationLayout`, value-wrap rule + tests.
- **P-B — Overview card** (#1, #3 per D2, #4, #7, #8): C1 on both platforms, chart inside the card, freshness one line, SAMPLE badge on the
  title, sample scenario into ⋯ (MOCK), iOS Dynamic Type curve pairing + touch targets. Empty-chart treatment per D1 (#2).
- **P-C — Holdings and allocation** (#5, #6, #12): two-line values, optional sort (D3), donut + legend with bar fallback, chart spec.
- **P-D — States and actions** (#9, #10, #11, #13, #14, #15): action placement and menu, empty state v2 (no "Popular ideas"), skeletons,
  holding-details rows, collapsible transactions, account chip.
- **P-E — Verification**: full suites (`:core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
  :app:androidApp:assembleDebug`, default `xcodebuild`), device captures after, TalkBack/VoiceOver spot checks **where the environment allows**
  (see §7), `docs/PORTFOLIO_UI_REFINEMENT_P*.md` per phase; commit only on request.

Estimated size: P-A small, P-B medium (largest), P-C medium, P-D medium, P-E small.

## 7. Risks and open points

- **D1** history on open (on-demand vs automatic 1M request) — affects request volume; recommend on-demand with a one-tap "Show 1M".
- **D2** overall return % — needs a presenter field (exact decimal) in a separate approved change, or stays amount-only.
- **D3** holdings sort control — display order only; optional.
- Donut honesty: never draw when slices are missing, negative or exceed 100 %; bars remain the fallback.
- Reference items not adopted without approval: "Popular ideas" (advice risk), "Manage lists" / "Set cash balance" / "Send feedback" / "Common
  questions" (new features or flows), a sixth tab (none shown, none planned).
- Screen readers: the Android emulator asks for a notification permission for Android Accessibility Suite the first time TalkBack starts; the
  project rules forbid answering permission prompts on the owner's behalf, so TalkBack runs need the owner to answer it once (or a physical
  device). VoiceOver isn't available in the iOS Simulator (Accessibility Inspector audits are the substitute; real VoiceOver needs a device).
- Home's `PortfolioSummaryCard` should adopt C1 later (Home phase), not in this pass.
