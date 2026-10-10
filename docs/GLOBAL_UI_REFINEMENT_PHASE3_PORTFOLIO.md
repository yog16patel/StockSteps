# Global UI Refinement — Phase 3: Portfolio reference screen (Android + iOS)

Status: **committed** as "Redesign Portfolio as the Global UI Refinement Phase 3 reference screen on Android and iOS" (2026-10-10), on top of "Add Global UI Refinement Phase 2: badges, pill selectors, banners, search field
and component baseline". Plan step 3 (`docs/GLOBAL_UI_REFINEMENT_PLAN.md`). Visual references: `docs/design/stocksteps-portfolio-reference.png`
(moved by this session from `docs/stocksteps-portfolio-reference.png.png`) and `docs/design/stocksteps-ui-reference.png`. The "attached"
before screenshots in the request were not attached; the before state was captured on the devices (below).

No business logic, calculation, endpoint, persistence, navigation, entitlement or MOCK-fixture change. Every value on screen is a field of
`PortfolioUiState` formatted with the existing exact-decimal `PortfolioFormat`; the reference image's numbers were not used.

## 1. Layout — before → after

| Before (both platforms) | After |
|---|---|
| Title + subtitle | **Header**: "Portfolio" + (i) "About these numbers" + ⋯ overflow (Refresh portfolio, Account settings, Delete account…) |
| Two large cards "My Portfolio" / "Practice Portfolio" | **`StockSegmentedControl` My Portfolio · Practice** (Practice opens the separate simulated portfolio, which keeps its SIMULATED badge) |
| "Sample scenario: …" button + loose text | **`StockSelectField` "Sample scenario"** + SAMPLE `StockBanner` when a read-only sample is active |
| Red error paragraph + "Retry" + "Offline · last saved portfolio" text above the numbers | **One `StockBanner`**: "Offline · last saved portfolio" (or "Couldn't refresh your portfolio") with the sync error and Retry; WARNING when only offline |
| Account chips / menu + "Add account" | **Account selector** "Name · CAD ▾" (menu: name, "TFSA · reports in CAD", ✓ selected) + **"+ Add"** |
| Summary card with stacked text lines | **Hero card**: "Total portfolio value" (muted) → value (`largeNumber`, tabular) → today's change with sign/arrow, or "Today's change unavailable" + the presenter's reason → divider → `StockMetricGrid` Invested cost · Unrealized P/L · Realized P/L (signed, coloured, arrows) → "All accounts …" (multi-account) → missing-data notice → freshness line → SAMPLE badge |
| "Add investment / transaction" + long "Insights: …" button | **"+ Add transaction"** full-width primary under the hero; Insights moved to a navigation row at the end |
| "Portfolio history" + chips + two paragraphs | **"Portfolio performance" card**: `StockPillSelector` 1D…ALL; chart; compact empty state; one-line disclosure |
| Large holding cards (monospace value, price, cost, unrealized) | **Compact rows** in one card: logo · name · "MSFT · NASDAQ · 3 shares" — value · "+13,144.00%" over "(+1,577.28)"; stale-quote line |
| Cash / allocation / dividends paragraphs | **Cash**, **Allocation** (labelled bars by holding and by currency), **Dividends** cards; one line when empty |
| "OPENING POSITION · MSFT" + Edit / Delete text buttons | **Transactions (n)**: "Opening position · MSFT · 3 shares", "2026-10-09 · USD 12.00", ⋯ menu (Edit, Delete…) — 5 recent + "Show all n" |
| "PERSONAL · reporting CAD", Account settings, Delete account, Refresh at the bottom | moved to the ⋯ overflow menu (same confirmations) |
| — | **Portfolio insights** row (icon, title, "Performance, allocation and concentration", chevron) |

Holding details (`holdingSymbol` set) keep their screen: holding row, a 2-column metric grid (price, cost, realized, dividends), Company
details / Price alerts / Add to watchlist, cash, dividends and that holding's transactions.

## 2. Component reuse

`StockSegmentedControl`, `StockSelectField`, `StockBanner`, `StockStatusBadge`, `StockCard` / `.stockCard`, `StockButton` / `.stockPrimary` /
`.stockSecondary`, `StockMetricGrid`, `StockPriceChange`, `StockPillSelector`, `StockTrendChart` (Compose) / Swift Charts, `StockTickerAvatar`,
`StockDivider`, `StockIconTile`, `StockEmptyState` / `StockStateMessage`. New shared, tested helpers in
`presentation/portfolio/PortfolioPresentation.kt` (labels for account types and transaction kinds, signed changes, direction, holding
subtitle/value/spoken description, two-line change, transaction detail/amount, freshness, allocation bar fraction) and `PortfolioChartRules`
(chart only with ≥ 2 dated values). `PortfolioSummaryCard` (also used by Home) is unchanged.

Design-system fixes found while building the screen (all platforms that use them benefit):
- `StockLayout.metricColumns(…, widestContent, gap)` + measured content in `StockMetricGrid` (Compose `TextMeasurer`, SwiftUI `UIFont`): a grid
  drops a column instead of breaking "↑ +2,129.33" inside a narrow column.
- `StockPriceChange`: non-breaking space after the arrow (never an orphaned "↑").
- `StockTrendChart`: a flat series (all values equal) is drawn through the middle instead of along the bottom edge.

## 3. Android / iOS differences

Same section order, hierarchy, tokens, banners, empty states and freshness treatment. Native differences: overflow and transaction actions
are a Material `DropdownMenu` (Android) vs a SwiftUI `Menu` (iOS); the (i) explanations are an `AlertDialog` vs a medium-detent sheet; the
history chart is `StockTrendChart` vs Swift Charts (both draw gaps for missing values); iOS keeps pull-to-refresh and the iOS screen now uses a
plain `VStack` (the old `LazyVStack` reserved a ~200-pt blank block under Allocation — fixed).

## 4. Screenshots (approved devices; `docs/design/phase3/`)

- iOS: spare **iPhone 16 Simulator**, throwaway Phase 5C.1 account (offline cached portfolio + sign-in failure, because the Auth emulator is
  off), local MOCK server. Android: **emulator-5554** (427 dp; also run at **360 dp** via `wm size 1080x2400`) with the signed-in account
  already on that emulator — only viewed; populated data from the read-only MOCK sample "mixed-currencies" (no saved data changed).
- Variants: light/dark, default and large text (iOS XXXL, Android font scale 1.3), small width (Android 360 dp). All device settings were
  restored (iOS light/Large, simulator shut down; Android font scale 1.0, size reset, app theme Light); MOCK server stopped.
- Files: `before-*` (Android own account and sample, iOS own account), `after-android-light-own` (an early build: the empty-history and
  sample-field fixes came later), `after-android-light-sample`, `after-android-dark`, `after-android-small-large-sample` (before the
  large-text holding-row fix) and `after-android-small-large-holdings` (after it), `after-ios-light-own`, `after-ios-variants` (dark /
  large; two frames were captured while the text-size change was still applying).

Visual findings fixed during the pass: "↑" orphaned above the unrealized value (iOS); long one-line holding change squeezing the name;
duplicate latest-value line; near-empty chart box for < 2 values; flat history line on the bottom edge; "Toronto-Dominio / n Bank" mid-word
break at 360 dp × 1.3; error + offline banners stacking; iOS allocation blank block; sample field shown as a placeholder.

## 5. Accessibility

- Holding rows: one spoken description ("Microsoft Corporation, MSFT · NASDAQ · 3 shares, value USD 1,589.28, unrealized gain 1,577.28
  (+13,144.00%)", stale/unavailable stated) and a button role; transaction rows combine their two lines; ⋯ menus are labelled
  ("Actions for Opening position · MSFT · 3 shares"); header buttons labelled; section titles are headings; selector state announced.
- Gains/losses: sign + arrow + colour + words ("unrealized gain/loss"); "Today's change unavailable" instead of a zero.
- 44–48 pt targets for header buttons, menus, "+ Add", pills, segments. Values never truncate: grids reflow (3 → 2 → 1 columns), holding values
  move under the name at large text, names wrap to two lines.
- Not done: full TalkBack/VoiceOver walkthrough (only layout/labels checked), physical devices, high-contrast mode.

## 6. Empty / error / offline / sample handling

No accounts → full empty state "Start tracking what you own" (Create portfolio when signed in); not signed in → INFO banner with Sign in;
sync error / offline → one banner (Retry); read-only sample → SAMPLE banner + SAMPLE badge in the hero, actions disabled as before; no
history → "No performance history shown · Choose a period to load dated values." (history loads only after a period is chosen, so no pill
is shown selected before that); one dated value → one line, no empty chart; no holdings / cash / dividends / transactions / allocation → one
line each. The app-wide "Sample data · mock backend" strip stays visible under the tab bar (verified on both platforms).

## 7. Financial-data integrity

- **§14 finding — MSFT 13,144 % is consistent with the ledger, not a calculation error.** Cached ledger on the spare simulator: one
  `OPENING_POSITION` MSFT, 2026-10-09, quantity 3, **unit price 4**, gross/net 12 USD. Market value 3 × 529.76 = 1,589.28; unrealized
  1,589.28 − 12.00 = 1,577.28; 1,577.28 / 12.00 = 13,144 %; account invested cost 16.20 CAD = 12 USD at the cached FX rate. The input is
  implausible (almost certainly a Phase 5C.1 test-entry artefact — simulator text injection was known to drop digits). No calculation was
  changed. Possible follow-up (needs approval): a non-blocking "price far from market" hint in the transaction form.
- No value is invented: missing totals/gains show "—" / "unavailable"; daily change missing → explicit message; allocation bars clamp only
  the bar (labels keep e.g. 135 %); history gaps are drawn as gaps.

## 8. Tests and builds (2026-10-10)

- New `PortfolioPresentationTest` (5): no raw enums, signed changes / missing values, holding row text and spoken description, transactions
  and freshness, chart drawability; `DesignTokensTest` + content-aware metric columns.
- `./gradlew :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`: **BUILD SUCCESSFUL** —
  shared Android host **80 / 0 failed / 0 skipped**, shared iOS **76 / 0 / 0**, `assembleDebug` OK.
- iOS `xcodebuild … generic/platform=iOS Simulator … build`: **BUILD SUCCEEDED**; no warnings in the changed Swift files.
- No Compose/XCUITest UI-test targets exist; screen behaviour is covered by the device pass above.

## 9. Known limitations

- Freshness timestamps are shown as provided ("2026-10-07T20:00:00Z") — a friendly date format needs a shared date formatter (next phase).
- The reference's 2×2 quick tiles (Transactions / Allocation / Dividends / Insights) were not built: there are no separate destinations for
  them; the sections stay inline and Insights is a row.
- "View all" holdings: no separate holdings screen exists; all holdings are listed.
- Home still uses the old `PortfolioSummaryCard` (Phase 4). Practice screens unchanged.
- Hero value stays `largeNumber` 32 (reference 34) — it already dominates at 360 dp; revisit with Home.

## 10. Recommended before migrating other screens

1. Shared date/time formatter for "as of" lines. 2. A reusable "section" helper (title + card) and "list card" (rows + dividers) extracted
from Portfolio. 3. Move `PortfolioHoldingRowView`'s adaptive stacking into `StockRow` (trailing column under the name at large text). 4. Reuse
the hero pattern for Home's portfolio summary.
