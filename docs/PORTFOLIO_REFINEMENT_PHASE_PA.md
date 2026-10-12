# Portfolio UI Refinement — Phase P-A: baseline and shared display rules

Status: **committed and pushed** as "Close Company Comparison Phase 5C.1 with section-based sticky header and spacing fixes, and add the Portfolio UI audit and Phase P-A display rules" (2026-10-12). Plan: `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md` (prioritised issues #1–#16, components C1–C7, phases P-A…P-E).
P-A changes **no visible UI**: one shared rules object + tests, and this baseline. No presenter, API, calculation, ledger, navigation, entitlement or
fixture change. P-B is **not started**.

## 1. Baseline screenshots — located, not newly captured

New device captures were **not** taken in P-A: the P-A brief requires explicit approval before simulators/emulators are used, and none was
given for this step. The dashboard code is unchanged since Phase 3.1 (`git log`: the only later commit touching these files, Phase 4A `e1a19bd`,
removed the unused SwiftUI/Compose `PortfolioSummaryCard` and moved readable dates to core; no dashboard layout change), so the Phase 3.1 captures
are a valid baseline:

| Variant | File | Captured |
|---|---|---|
| Android light, 427 dp, 1.0× — empty account, mixed-currencies hero / chart / holdings / allocation / cash and dividends / transactions | `docs/design/phase3_1/android.jpg` | 2026-10-10 (Phase 3.1) |
| Android light, 360 dp, 1.3× | `docs/design/phase3_1/android-small.jpg` | Phase 3.1 |
| iOS iPhone 16 light (offline own account, chart, holdings, allocation) and **dark at XXXL** (hero, chart, holdings) | `docs/design/phase3_1/ios-final.jpg` | Phase 3.1 |
| Android dark | `docs/design/phase3/after-android-dark.jpg` | Phase 3 — **before** the 3.1 polish (freshness/hero rows differ) |
| iOS light/dark/large variants | `docs/design/phase3/after-ios-variants.jpg` | Phase 3 (pre-3.1) |

Gaps to fill at the start of P-B (with approval): Android dark after 3.1, Android 1.3× dark, iOS XXXL light, iPhone SE, loading state (both),
history-loaded chart with ≥ 3 distinct values (the MOCK "mixed-currencies" ledger draws a flat two-point line).

## 2. Baseline findings by platform (from the captures + code)

### Shared (both)
- Order: header (ⓘ, ⋯) → My Portfolio | Practice → (MOCK) Sample scenario field + banner → sync / sign-in banners → account selector + "+ Add"
  → hero → full-width **"+ Add transaction"** → Portfolio performance card → Holdings → Allocation → Cash and dividends → Transactions →
  Portfolio insights.
- **Overview split in three** (hero, add button, performance card): the value and its chart are separated by a primary button (plan #1).
- **Performance**: no range selected on open — "No performance history shown · Choose a period…" until a pill is tapped (on-demand loading
  is the presenter's behaviour and is kept; plan #2).
- **Overall return %**: not on screen and not in `PortfolioUiState` (hero shows Unrealized P/L as an amount only; plan #3).
- **Holdings**: correct and accessible (one spoken description; stacking at 320 dp / 1.3×); three right-hand lines (value, %, amount) are
  dense; ticker-text avatars when there's no logo (MOCK samples).
- **Allocation**: labelled bars by holding and by currency (% strong + reporting amount); accurate but list-like.
- **Empty**: icon, "Start tracking what you own", one line, "Create portfolio" (signed in). Holdings / Transactions empty lines are plain text.
- **Sample data**: "Sample scenario" field at the top (MOCK builds only), read-only banner, SAMPLE badge at the end of the hero, global "Sample
  data · mock backend" strip — disclosure is complete; the field takes prime space.
- Missing data is honest throughout ("Today's change unavailable · Why?", "None recorded", "USD —", "Gain unavailable").

### Android (Compose)
- Loading = thin `LinearProgressIndicator` above the content (layout shifts when data arrives).
- 1.3× at 360 dp: hero metric rows, holdings and transactions wrap without clipping (`android-small.jpg`); section titles + card gaps make a long page.
- Dark after 3.1: **not captured** (only pre-3.1 `phase3/after-android-dark.jpg`).

### iOS (SwiftUI)
- Loading = centred `ProgressView("Loading portfolio")`.
- 34 `StockStepsTheme.font(type.x)` calls without `relativeTo:` → each token follows the Dynamic Type curve nearest its size; at XXXL dark
  (`ios-final.jpg`) the freshness captions are nearly as large as the metric values (the Compare 5C.1 defect pattern).
- Hero total uses `minimumScaleFactor(0.7)` (shrinks; Android wraps). 9 hard-coded `44` frames instead of `dims.touchTarget`.
- Swift Charts line with point marks and a selection line above the chart; Compose draws a plain line with touch details (different look).

## 3. Shared display rules agreed for P-B onward

| # | Rule | Where enforced |
|---|---|---|
| R1 | **One overview card**: total value, today's change, performance chart + range pills and the Invested / Unrealized / Realized summary live in one card (P-B). "Add transaction" leaves the hero path. | P-B (C1 `StockOverviewCard`) |
| R2 | **History stays on demand**: dated history loads only when the user picks a period (today's presenter behaviour). P-B shows a compact "Show 1M" action instead of the empty block — **no automatic request on open** (avoids extra calls). | P-B UI; presenter unchanged |
| R3 | **No new overall-return percentage**: the UI shows only verified values from `PortfolioUiState` (`unrealized`, `realized`, `dailyGain`/`dailyPercent`). A total-return % would need an exact-decimal presenter field in a separately approved logic change. | review rule |
| R4 | **Donut only for valid allocations**: `PortfolioDisplayRules.allocationDonutAllowed` — every slice present and parseable, none negative, total ≤ 100 %; otherwise keep the labelled bars (borrowed/negative cash can exceed 100 %). | new code + tests (this phase) |
| R5 | **Optional holdings sort** (Value, Name, Change %): `PortfolioDisplayRules.sortHoldings` — display order only; Value uses the reporting-currency amount from the allocation list (USD and CAD never ranked by native amounts); rows with a missing sort value keep their order after the others. | new code + tests; UI in P-C (owner decision D3) |
| R6 | **Missing stays missing**: never `0.00`, never a zero-length "value"; existing helpers (`signedAmount`, `change`, `holdingChangeLines`, `direction` → UNAVAILABLE, `allocationFraction(null)` = no bar) are the only formatters. | existing tests (`PortfolioPresentationTest`) |
| R7 | **Keep disclosures and formatting**: SAMPLE badge / banner / strip, read-only sample banner, freshness lines (may move into ⓘ but stay reachable), About-these-numbers text, `PortfolioFormat` exact-decimal amounts with currency codes. | review rule |
| R8 | **Tokens and native accessibility**: `StockStepsTheme` tokens only; 48 dp / 44 pt (`dims.touchTarget`); iOS value + caption on the same Dynamic Type curve; gain/loss in words + sign + arrow; brand `#1683FF` (`primary`) for chart line, selection and links, `primaryAction` for filled buttons; chart line colour never encodes gain/loss. | P-B…P-D |

## 4. Code and tests (this phase)

- `app/shared/.../presentation/portfolio/PortfolioPresentation.kt`: `enum PortfolioHoldingSort` and `object PortfolioDisplayRules`
  (`allocationDonutAllowed`, `sortHoldings`). Exact `Decimal` comparison only; nothing displayed is computed. Not yet used by any screen.
- `PortfolioPresentationTest.kt`: +2 tests — `donutOnlyForCompleteNonNegativeAllocations`, `holdingsSortIsDisplayOnlyAndKeepsMissingLast`.
- Results: see `docs/GLOBAL_UI_REFINEMENT_PHASE5C_1_CLOSURE.md` §4 (same run).

## 5. Proposed P-B scope — Portfolio Overview Card (awaiting approval)

1. Fresh **before** captures (approval needed): Android 360 dp 1.0×/1.3× light/dark (app Theme), iPhone 16 Large/XXXL light/dark, iPhone SE.
2. Shared `PortfolioDisplay` copy object (overview labels, empty-chart line, spoken summary) + tests.
3. **C1 `StockOverviewCard`** (Compose + SwiftUI, optional params): title "Total value" + SAMPLE badge (`StockTitleWithBadge`) + ⓘ (About these
   numbers), total (`largeNumber`, wraps at the currency boundary — no scale factor), today's change line or "Today's change unavailable · Why?",
   chart slot + range pills (R2: nothing auto-loads; "Show 1M" text action when no history), Invested / Unrealized / Realized rows
   (`StockMetricGrid(rowsWhenNarrow)`), one freshness line (full text in ⓘ), notice (caution) kept visible.
4. Portfolio dashboard uses it; the separate performance card is removed; "+ Add transaction" moves below the overview as a secondary/tonal
   action (final placement in P-D).
5. iOS: overview text on paired Dynamic Type curves, `dims.touchTarget` for the header buttons.
6. Out of P-B: holdings, allocation donut, sort, skeletons, empty-state v2, menus, account chip, Home's summary card.
7. Verification: full suites, both builds, after captures, TalkBack/VoiceOver only if the environment allows (see blockers).

## 6. Blockers / limits

- Device captures need the owner's approval (P-A brief).
- TalkBack: the emulator's first TalkBack start shows "Allow Android Accessibility Suite to send you notifications?"; that dialog was left open
  for the owner on emulator-5554 during the 5C.1 closure pass (rules forbid answering it on the owner's behalf).
- VoiceOver: not available in the Simulator; needs a physical device.
- D2 (overall return %) and D3 (sort control) are owner decisions.
