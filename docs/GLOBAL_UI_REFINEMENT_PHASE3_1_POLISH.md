# Global UI Refinement — Phase 3.1: Portfolio polish and regression review

Status: **committed** as "Polish the Portfolio reference screen (Global UI Refinement Phase 3.1) on Android and iOS" (2026-10-10). Working tree at start: clean, HEAD "Redesign Portfolio as the Global UI Refinement Phase 3
reference screen on Android and iOS" (`5d3b3cd`, **already committed and pushed** — the request assumed Phase 3 was uncommitted). The
"attached" screenshots were not attached; the review used the committed Phase 3 sheets (`docs/design/phase3/`), the final code and a new
device pass. No financial formula, conversion, ledger, endpoint, auth, persistence, navigation, Practice or fixture change.

## 1–2. Issue inventory (screenshots vs final Phase 3 code)

| # | Problem | Platform | Evidence | Root cause | Fix | Severity | Status before 3.1 |
|---|---|---|---|---|---|---|---|
| 1 | Freshness shown as raw ISO ("2026-10-08T15:00:00Z") | both | phase3 sheets | presenter strings printed as-is | shared `PortfolioDates` → "Prices as of Oct 8, 2026, 3:00 PM UTC" / "Exchange rate as of Oct 8, 2026" on separate lines | medium | open → **fixed** |
| 2 | Hero metrics fall back to 2 + 1 (Realized alone) | iOS default, Android 360 dp / 1.3× | phase3 iOS sheet | grid drops columns when "↑ +2,129.33" does not fit | `StockMetricGrid(rowsWhenNarrow)` → aligned label / value rows when the three do not fit on one line | medium | open → **fixed** |
| 3 | Daily-change reason adds two lines to the hero | both | phase3 sheets | notice always shown | "Today's change unavailable · Why?" toggle (reason one tap away; also in (i)) | low | open → **fixed** |
| 4 | Four near-empty cards (Cash, Allocation, Dividends, Transactions) | both | phase3 empty-account sheet | one section per list | Cash + Dividends in one "Cash and dividends" card with labelled lines ("None recorded") | low | open → **fixed** |
| 5 | Allocation: holdings show % only, currencies % · amount; uneven alignment | both | phase3 sample sheets | different label builders | holdings and currencies both show **% (strong)** + reporting amount (muted), same bar height (`rangeBar`), label wraps | low | open → **fixed** |
| 6 | Trades read "CAD 0.00" (net cash of opening positions) | both | phase3 sample transactions | net amount used for every type | trades show the entered price ("CAD 75.00 per share"); cash movements keep their net amount; dates readable ("Sep 1, 2026") | medium | open → **fixed** |
| 7 | Holding rows stack only at large text | both | code review | font-scale-only rule | also stack below a 320 dp/pt row width (`PortfolioLayout.stackedRowWidth`) | low | open → **fixed** |
| 8 | iOS chart x-labels truncated ("Sep 1…"), y-axis from 0, odd labels for a flat series | iOS | 3.1 device pass | Swift Charts defaults | first/last short dates ("Sep 1", "Oct 8"), y-domain from data (+5 %), ±1 for a flat series | medium | **new → fixed** |
| 9 | Compose x-labels used full dates (crowded) | Android | 3.1 device pass | — | short dates as iOS | low | **new → fixed** |
| — | "↑" orphaned, holding change squeezing names, duplicate latest value, empty chart box (< 2 values), flat line on bottom edge, mid-word name break at 360 dp × 1.3, stacked error + offline banners, iOS allocation blank block | both | Phase 3 intermediate screenshots | — | — | — | **already fixed in Phase 3** (not reworked) |
| — | MSFT 13,144 % | data | — | ledger opening position at USD 4 | none (calculation correct) | — | finding only |

## 3. Code changes

- `presentation/portfolio/PortfolioPresentation.kt`: `freshnessLines`, `dateLabel`, trade-aware `transactionAmount` (ledger fields only),
  `PortfolioDates` (`date`, `shortDate`, `dateTime` — UTC instants stay UTC and are labelled; unknown formats unchanged), `PortfolioLayout`.
- `designsystem/components/StockMetric.kt` + iOS `DesignSystem/StockControls.swift`: `StockMetricGrid(rowsWhenNarrow:)`.
- `presentation/portfolio/PortfolioScreen.kt` / iOS `PortfolioScreen.swift`: hero rows + "Why?" + freshness lines; "Cash and dividends" card;
  allocation % + amount; readable trade dates and prices; width-aware holding-row stacking.
- `PortfolioHistoryChart.kt` / `PortfolioHistoryChart.swift`: readable dates in the selection line and one-value summary; short axis dates;
  iOS y-domain from data.
- Tests: `PortfolioPresentationTest` (+1 test, updated transaction/freshness assertions).

## 4. Before / after

Phase 3 sheets: `docs/design/phase3/`. Phase 3.1 sheets: `docs/design/phase3_1/ios-final.jpg` (offline own account light; mixed-currencies chart
/ holdings / allocation light; hero and chart dark at XXXL), `android.jpg` (empty account, mixed-currencies hero / chart / holdings /
allocation / cash and dividends / transactions), `android-small.jpg` (360 dp × 1.3). Key differences: hero metrics as aligned rows when
needed, readable "as of" lines, one-tap reason, short axis dates, centred flat history with a data-based y-axis on iOS, compact cash and
dividends, consistent allocation rows, per-share prices on trades.

## 5. Android / iOS consistency

Same section order, hierarchy, tokens, shared formatters (`PortfolioPresentation`, `PortfolioDates`), layout rules (`StockLayout.metricColumns`,
`PortfolioChartRules`, `PortfolioLayout`), banners and empty states. Native differences kept: Material menus/dialog vs SwiftUI menus/sheet,
`StockTrendChart` vs Swift Charts, pull-to-refresh on iOS.

## 6. Accessibility

Manual (device) checks: iOS default and XXXL in light/dark; Android 427 dp and 360 dp at 1.0× and 1.3×. No clipped or missing values;
values wrap or stack; "Why?" / "Hide" is a labelled button (44/48 pt); metric rows merge label + value for screen readers; holding rows keep
their single spoken description; MOCK strip visible under the tab bar on every capture. Not done: full TalkBack/VoiceOver walkthrough, physical
devices, high-contrast mode.

## 7. Tests and builds (2026-10-10)

Automated: `./gradlew :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` **BUILD
SUCCESSFUL** — shared Android host **81 / 0 / 0 skipped**, shared iOS **77 / 0 / 0**, `assembleDebug` OK; iOS `xcodebuild … generic/platform=iOS
Simulator` **BUILD SUCCEEDED** (no warnings in changed files). Manual: device pass above (approved: spare iPhone 16 Simulator, emulator-5554;
settings restored — iOS light/Large and shut down, Android font 1.0 / size reset / app theme unchanged (Light); MOCK server stopped). The
emulator's signed-in account now shows the empty-account state because the local MOCK server was restarted (in-memory data) — no data was
created or changed.

## 8. Remaining limitations

UTC is shown instead of local time (no time-zone database; local conversion would need `kotlinx-datetime` or a platform formatter); no
separate holdings / transactions screens ("View all"); `PortfolioSummaryCard` on Home unchanged; no UI-test targets; sample ledgers make
opening-position net cash 0 by design.

## 9. Phase 4 readiness

Ready to start Phase 4 (Home, Markets, Company Details) after review: Portfolio's patterns (hero, metric rows, list cards, banners, readable
dates) are shared and tested. Suggested first steps: reuse the hero for Home's portfolio card, move `PortfolioDates` to a general date
helper, and promote the holding-row stacking into `StockRow`.
