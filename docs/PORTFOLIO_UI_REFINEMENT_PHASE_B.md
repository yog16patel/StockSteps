# Portfolio UI Refinement — Phase B: Overview card

Status: **committed and pushed** as "Add the Portfolio overview card with on-demand account value history, currency on every metric and the sample selector in the menu (Portfolio Phase B) on Android and iOS" (2026-10-12), on top of `37d74cd` "Fix Portfolio account selection by id and add Phase A shared form
rules, readable labels, formatted holding amounts and consistent copy on Android and iOS". The brief assumed Phase A was uncommitted, but it is
committed and pushed in `37d74cd`; nothing from it was touched except where Phase B builds on it. Plan: `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`
§Phase B. Owner decision used: **Q5 = the currency code is shown with every metric**. **Phase C is not started.**

No presenter, API, server, ledger, calculation, persistence, navigation-route, entitlement or fixture change.

## 1. Before / after architecture

| | Before (Phase 3.1 / A) | After (Phase B) |
|---|---|---|
| Android | `PortfolioScreen` items: account selector → `PortfolioHero` (StockCard) → full-width "+ Add transaction" → `PortfolioPerformanceCard` (StockCard) | one item `PortfolioOverviewCard` (StockCard; the account selector passed in as a slot) → "+ Add transaction" below it; `PortfolioHistorySection` (pills + states) lives inside the card |
| iOS | `accountSelector` → `hero` → button → `PortfolioHistoryChart` (own card) | `overviewCard` (`.stockCard()`; `summaryContent` + `PortfolioHistorySection`) → button |
| Shared | `PortfolioPresentation` helpers called directly from each screen | new `PortfolioOverviewDisplay` / `PortfolioOverview` / `PortfolioOverviewMetric` / `PortfolioHistoryState` (presentation/portfolio/`PortfolioOverview.kt`): **one display model** both platforms render |
| Sample selector | `StockSelectField` at the top of the content (MOCK) | in the ⋯ menu (Android `DropdownMenu` group with a check; iOS `Picker(.menu)` inside the `Menu`); the read-only SAMPLE banner stays visible in the content, the card shows a "Sample · <Scenario>" badge |
| History "requested" | inferred from `history.isNotEmpty() || historyLoading` (a requested-but-empty period looked like "not requested") | the Screen remembers the period chosen on it (`rememberSaveable` / `@State`), so not-requested, empty and error are distinct |

Route → Scene → Screen boundaries unchanged: the Scene still passes `onHistory = presenter::loadHistory`, `onRefresh`, `onScenario`.

## 2. Layout (top to bottom inside the card)

1. Account context: the existing account selector ("Personal · CAD ▾" + "+ Add").
2. "Total value" + SAMPLE badge (`StockTitleWithBadge`), total in `largeNumber`. iOS no longer shrinks it (`minimumScaleFactor(0.7)` removed); it wraps.
3. "Today's change" + `StockPriceChange` ("+CAD 12.50 (+0.68%)", arrow + colour + words), or "Today's change unavailable · Why?" (the label is
   dropped then, because the line names itself).
4. "All accounts · CAD 5,000.00" (only with ≥ 2 active accounts; non-breaking space keeps the code with its amount).
5. Divider → **Account value**: heading, one-line note ("Includes cash, deposits and withdrawals, so a rise isn't always a gain. Returns that leave
   out deposits are in Portfolio insights (StockSteps+)."), period pills, then one history state (§4).
6. Divider → Invested cost / Unrealized P/L / Realized P/L (`StockMetricGrid(rowsWhenNarrow)`), each with its currency.
7. The caution notice ("Unavailable: …") and **one** freshness line ("Prices as of … · Exchange rate as of …"). The server's long history notice
   is no longer repeated in the card; it stays in "About these numbers".

"+ Add transaction" sits directly below the card. The My Portfolio / Practice selector, banners and every downstream section (Holdings, Allocation,
Cash and dividends, Transactions, Insights) are unchanged.

## 3. Shared components and rules

- `PortfolioOverviewDisplay.of(state, activeAccounts)` → `PortfolioOverview` (total, availability, today and its direction, all accounts,
  metrics with spoken text, freshness, notice, sample badge, spoken summary).
- `historyState(state, requested)` → `NOT_REQUESTED | LOADING | LOADED | FLAT | INSUFFICIENT | EMPTY | ERROR`. Also: `selectedPeriod`,
  `flat` (exact `Decimal` equality of ≥ 2 values), `hasGaps`, `flatSummary`, `insufficientSummary`, `pointDetail`, `xLabels`, `yLabels` (high/low
  from exact decimals), `chartDescription`, and the constants `SHOW_PERIOD = "1M"` / `SHOW_ACTION` and the state messages.
- `PortfolioDisplay.scenarioLabel` ("mixed-currencies" → "Mixed currencies"). `StockLabels` gained the acronym "FX" ("Missing FX").
- **New layout rule `StockLayout.metricRowStacked(longestLabelWord, valueWidth, availableWidth, gap)`** (`theme/Spacing.kt`, test in
  `DesignTokensTest`). Used by `StockMetricGrid(rowsWhenNarrow)` on both platforms: a value moves under its label only when the label's longest
  word and the value can't share the line. Found on device: "Unrealiz / ed P/L" at 2.0×. Default behaviour is unchanged whenever the line fits.
  Other screens using `rowsWhenNarrow` get the same fix.
- Chart spec, now the same on both platforms: one brand-blue line (colour never encodes gain/loss); missing values are gaps; first/last short dates;
  high/low value labels; the selected-day line above the chart (defaults to the latest); a rule on the selected day. iOS dropped the per-point
  marks and the ±1 domain for flat series (flat series are no longer drawn).
- Removed: Android `PortfolioHero`, `PortfolioPerformanceCard`; the iOS `hero` function and `PortfolioHistoryChart` struct (replaced by
  `PortfolioHistorySection` in the same file).

## 4. History states

| State | When | Shown |
|---|---|---|
| NOT_REQUESTED | nothing chosen on this screen, no history in the state | "History loads when you choose a period." + **Show 1M** (secondary button → existing `loadHistory("1M")`) |
| LOADING | `historyLoading` | progress + "Loading dated values…" (spoken) |
| LOADED | ≥ 2 dated values, not all equal | chart; "Some dates have no value and are left as gaps." when values are missing |
| FLAT | ≥ 2 dated values, all equal (exact decimals) | "Unchanged at CAD 1,850.00 · Sep 1, 2026 – Oct 8, 2026" — no line, no axis ticks suggesting movement |
| INSUFFICIENT | history present but < 2 dated values | "Only one dated value so far: …" or "No dated values in this period." |
| EMPTY | a period was requested, nothing came back, no error | "No dated values in this period." (pill stays selected) |
| ERROR | a period was requested, nothing came back, the presenter reports an error | "Account value history couldn't load." + **Retry** (existing `refresh()`, which reloads the report with the chosen range) |

History already held by the shared presenter (an earlier choice, or a sample scenario) counts as loaded. Nothing loads automatically (rule R2).

## 5. Financial accuracy guarantees

- Only existing `PortfolioUiState` fields, read as is:
  - `total` (reporting currency), `basis`, `unrealized`, `realized`;
  - `dailyGain` / `dailyPercent` (server);
  - `combinedTotal` (presenter conversion, already supported);
  - `history` (server account value).
- No new total-gain %, total-return %, sum or conversion.
- Exact-decimal formatting through `PortfolioFormat.amount` / `PortfolioPresentation.signedMoney` / `signedPercent`. The y-axis labels and flat
  detection use `Decimal`. Chart drawing coordinates use `Double` only for pixels, as before.
- Missing values: "CAD —", "Today's change unavailable", "All accounts · CAD —" (spoken "unavailable"), never 0.00. A daily amount without a
  percentage shows the amount alone.
- The chart is titled **Account value**, never performance, and its note says deposits and withdrawals move it.
- Currency codes come from `state.currency` (the account's reporting currency); nothing is hard-coded.

## 6. Android / iOS differences (intended)

| Topic | Android | iOS |
|---|---|---|
| Sample scenario menu | `DropdownMenu` section "Sample scenario" with a check on the active one | `Picker(.menu)` submenu inside the ⋯ `Menu` |
| Loading indicator | `LinearProgressIndicator` + caption | `ProgressView` with a spoken label |
| Chart selection | press or drag (Compose) | press-and-drag (`chartXSelection`) |
| Screen-reader grouping | `clearAndSetSemantics` on the total/today/all-accounts group when no Why? action | `.accessibilityElement(children: .ignore)` + the same spoken summary |

## 7. Tests and results

New `PortfolioOverviewTest` (12 tests, one per brief item):
1. not requested;
2. Show 1M requests 1M;
3. switching periods;
4. flat series;
5. insufficient, missing and gaps, and the error state;
6. missing total or daily change;
7. currency on every amount (CAD/USD);
8. negative and zero amounts;
9. all accounts;
10. sample scenario (badge, history shown, labels, "Missing FX");
11. spoken summaries and chart description;
12. Phase A account-by-id regression.

Plus `DesignTokensTest` (`metricRowStacked`). The existing Phase A tests (`PortfolioFormRulesTest` 18, `PortfolioPresentationTest` 11) are kept.

Final sequential run after all changes, with clean test outputs: §11.

## 8. Screenshots (`docs/design/portfolio_pb/`, local MOCK backend only)

| File | What it shows |
|---|---|
| `android-360dp-light-1x-empty-no-sample-field.jpg` | empty state; no sample selector in the content |
| `android-360dp-light-1x-overflow-sample-scenarios.jpg` | ⋯ menu with the "Sample scenario" group and the check |
| `android-360dp-light-1x-sample-flat-history-before-date-fix.jpg` | flat sample (mixed currencies) — **before** the fix: date broke "Oct / 8, 2026" (fixed with non-breaking spaces) |
| `android-360dp-light-1x-sample-metrics-currency.jpg` | metrics with currency codes, freshness line, Add transaction below the card |
| `android-360dp-light-1x-overview-top.jpg`, `…-loaded-chart.jpg` | "Cash flows" sample: total, today's change, loaded chart with high/low labels |
| `android-360dp-light-1x-history-not-requested-show-1m.jpg`, `…-history-requested-empty.jpg` | "Missing history" sample: Show 1M, then the empty 1M state |
| `android-360dp-light-1x-missing-data.jpg`, `…-missing-data-why-open.jpg` | "Missing FX" sample: CAD —, today's change unavailable · Why? (open) |
| `android-360dp-dark-1x-overview-top.jpg`, `…-loaded-chart.jpg`, `…-missing-data.jpg` | dark theme |
| `android-360dp-dark-2x-top.jpg`, `…-overview-all-accounts.jpg`, `…-metrics-stacked.jpg` | 2.0× font: all-accounts line, "Unrealized P/L" value stacked (after the `metricRowStacked` fix) |
| `android-360dp-light-1.3x-overview-top.jpg`, `…-chart-metrics.jpg` | 1.3× light |
| `android-360dp-dark-1x-phaseA-form-top.jpg`, `…-form-fields.jpg`, `…-decimal-keyboard.jpg` | Phase A regression: readable labels, `StockTextField`s, decimal keyboard on Shares, `primaryAction` button |
| `ios-iphone16-light-large-guest-empty.jpg` | iOS guest: shared empty copy |
| `ios-iphone16-dark-ax5-live-change-guest-empty.jpg` | iOS live change to AX5: header and segmented control update; the sign-in banner and empty state keep the old size (pre-existing, §10) |
| `ios-iphone16-light-large-phaseA-form-guest.jpg` | iOS Phase A regression: "Opening position" readable, "Create an account first" disabled for a guest |

Device state was restored afterwards:
- Android: emulator size reset, font scale 1.0, app Theme back to Light, `adb reverse` removed;
- iOS: iPhone 16 back to Large / light;
- the MOCK server is stopped.

The Settings screen (owner email visible) was not saved.

## 9. Accessibility findings

- Fixed on device:
  - mid-word label break in metric rows at 2.0× (`metricRowStacked`, both platforms);
  - mid-date wrap in the flat summary;
  - currency code split from its amount (all-accounts line, flat summary).
- Spoken:
  - one summary for total, today's change (direction in words) and all accounts;
  - metrics spoken as "gain of" / "loss of" / "no gain or loss";
  - the chart description gives the first and last dated values and mentions gaps.
- Touch targets: iOS header buttons and the account selector use `dims.touchTarget` (were hard-coded 44); Why? keeps the minimum target on both.
- iOS Dynamic Type: the overview text uses paired `relativeTo:` curves (`.footnote` for labels and captions, `.title1` for the total). The history
  section reads `dynamicTypeSize` for live updates.
- **NOT VERIFIED:** TalkBack navigation and VoiceOver (spoken text checked only through the tests); iOS overview live Dynamic Type with data (see §10).

## 10. Remaining risks / NOT RUN

- **iOS overview with data — NOT RUN on device.** MOCK sample scenarios need a signed-in session (`mockAvailable` depends on the environment, which
  a guest doesn't get). Signing in on the simulator is allowed only through the Firebase Auth emulator, and its CLI isn't installed here (installing
  it needs approval). iOS is verified by build, the shared tests and the guest screens only. **iPhone SE / AX-size overview captures: NOT RUN** for
  the same reason.
- Pre-existing (not Phase B): `StockBanner` and `StockStateMessage` don't refresh on a live Dynamic Type change on iOS (capture above).
- Pre-existing (Phase D): native iOS `Form` text fields lose their visible label once filled; Android edit still opens in an `AlertDialog`.
- Loading a new period clears the report while it loads (presenter behaviour, unchanged), so the total can briefly show the local valuation or "—".
- `metricRowStacked` changes `StockMetricGrid(rowsWhenNarrow)` everywhere it's used, but only where a word would otherwise break; other screens
  weren't re-captured.
- The chart's Retry calls the existing `refresh()` (ledger, quotes and report), not a history-only reload, because no history-only path exists.

## 11. Verification (final sequential run after all changes, clean test outputs, 2026-10-12)

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` | **BUILD SUCCESSFUL** (8 m 29 s) |
| core JVM / core iOS | **471 / 0** each (unchanged) |
| shared Android host | **150 / 0** (Phase A: 138; +12 `PortfolioOverviewTest`; `DesignTokensTest` gained assertions) |
| shared iOS simulator | **146 / 0** (Phase A: 134; +12) |
| `:app:androidApp:assembleDebug` | OK |
| default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED**, no warnings in the changed files (a first run failed on `relativeTo: .title`, fixed to `.title1`) |
| device build `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` | BUILD SUCCEEDED (Info.plist checked), installed on iPhone 16 (iOS 18.3) |
| Android emulator-5554, 360 dp, light/dark, 1.0× / 1.3× / 2.0×, local MOCK | **RUN** — §8 |
| iOS iPhone 16 guest screens (light Large, dark AX5 live), Phase A form | **RUN** — §8 |
| iOS overview with data, iPhone SE, AX overview | **NOT RUN** — §10 |
| TalkBack, VoiceOver, physical devices, `:server:test` (no server change) | **NOT RUN** |

## 12. Phase C readiness

Ready, after approval. Phase C (holdings rows, weight %, optional sort, donut + legend, chart spec details, holding details actions, Insights pass)
depends only on Phase A/B pieces now in place. Open decisions: D3 (sort), Q6 (by-currency allocation), plus Q1/Q3/Q4/Q7 from the plan. iOS
data-backed captures need either the Firebase Auth emulator (CLI install approval) or an owner-signed-in simulator.
