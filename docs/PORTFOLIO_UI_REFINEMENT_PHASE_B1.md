# Portfolio UI Refinement — Phase B.1: visual polish and QA closure

Status: **committed and pushed** as "Polish the Portfolio overview with a short sample banner, a grouped scenario sheet, account labels without repeated currency and AX5 fixes (Portfolio Phase B.1) on Android and iOS" (2026-10-12), on top of `6041e8f` (Phase B). The brief assumed Phase B was uncommitted, but it was
already committed and pushed; nothing in Phase B was reverted. **Phase C is not started.**

No presenter-contract, API, server, calculation, fixture, navigation-route or entitlement change. The only shared design-system changes are
live Dynamic Type refreshes on iOS (`StockBanner`, `StockStateMessage`, `StockPriceChange`), the FX acronym (Phase B), and the new
`StockChartLayout`.

## 1. Changes by priority

| # | Brief item | Change (both platforms unless noted) |
|---|---|---|
| 1 | Responsive layout | Shorter card: one-line sample banner and chart note; width-based chart height. iOS at AX5: "+ Add" moves under the account selector (it squeezed the name to "Pers / on…"); the scenario name moved out of the badge (a never-wrapping badge widened the whole screen at AX5); y-axis labels capped at xxxLarge with the dates drawn under the plot (they overlapped and clipped "Sep 1"); `StockPriceChange` refreshes live (today's change stayed small after a switch to AX5) |
| 2 | Sample warning | Visible line "Read-only sample portfolio. Changes can't be saved." + **Details / Hide details** (banner action) revealing "Your saved accounts and watchlists stay unchanged; switch to My saved accounts to make changes." (`PortfolioDisplay.SAMPLE_SHORT`, `SAMPLE_BANNER`) |
| 3 | Scenario selector | ⋯ → "Sample scenario…" opens a scrollable sheet (Android `ModalBottomSheet` + `LazyColumn`; iOS `.sheet` with `List` sections, medium/large detents, Done). Groups from shared `PortfolioScenarioGroups`: Your portfolio · Accounts · Trades and holdings · Income and cash · Currencies · Missing or delayed data (unknown ids → "Other"). The active scenario is checked and marked selected for screen readers (Android `selectable(role = RadioButton)`, iOS `.isSelected`); the sheet note restates that sample data is read-only |
| 4 | Account context | `PortfolioPresentation.accountLabel` no longer repeats the reporting currency when the account name contains it ("TFSA CAD", "Personal USD"); a different currency stays ("US savings USD · CAD"); a currency inside a word isn't matched ("Cadence · CAD"). With several accounts the card says **Selected account total** and **All accounts total · CAD 1,370.37** (`PortfolioOverview.totalLabel`, `ALL_ACCOUNTS_LABEL`) |
| 5 | Chart section | Visible "Includes deposits and withdrawals." + ⓘ ("About account value", expanded/collapsed state) revealing the full explanation (unchanged text: a rise isn't always a gain; deposit-adjusted returns in Portfolio insights, StockSteps+). Height from shared `StockChartLayout.height(width)` = 0.45 × width, 120–200 dp/pt (≈ 133 at 360 dp; was 150) |
| 6 | Add transaction | In sample mode the disabled button gets "Sample portfolios are read-only. Switch to My saved accounts to add a transaction." Real portfolios unchanged (`busy` still disables it without the note) |
| 7 | Bottom navigation | Verified on device: the last item (Portfolio insights) scrolls fully above the MOCK strip and tab bar on Android 360 dp and iPhone 16; nothing changed |
| 8 | iOS Dynamic Type | `StockBanner`, `StockStateMessage` (and `StockPriceChange`, found during QA) read `dynamicTypeSize` in `body` (the `StockButtonBody` pattern), so they re-render on a live text-size change — verified by switching Large → AX5 live |

Extra, found on device:
- the "Unavailable: FX:basis:USD:2026-09-01, FX:USD/CAD" notice used raw engine codes. `PortfolioOverviewDisplay.noticeText` now reads
  "Unavailable: USD to CAD exchange rate on Sep 1, 2026 (for cost), USD to CAD exchange rate"; tickers read "AAPL price"; unknown codes stay
  verbatim;
- expanding the sample banner repeated "Read-only sample"; the details text now only adds what the short line doesn't say.

## 2. Shared rules / components

- `PortfolioDisplay`: SAMPLE_SHORT, SAMPLE_DETAILS / HIDE, SCENARIO_MENU / NOTE / DONE, ADD_DISABLED_SAMPLE, CHART_NOTE_SHORT, CHART_INFO.
- `PortfolioScenarioGroups` + `PortfolioScenarioGroup`.
- `PortfolioOverview.totalLabel`, `sampleBadge` ("Sample"), `sampleScenario` ("Scenario: …"); `PortfolioOverviewDisplay.SELECTED_TOTAL_LABEL`,
  `ALL_ACCOUNTS_LABEL`, `noticeText`.
- `PortfolioPresentation.accountLabel` (currency de-duplication; display only, the ids and values are unchanged).
- `StockChartLayout.height` (`theme/StockSemanticStyles.kt`).
- Android `PortfolioScenarioSheet` (in `PortfolioScreen.kt`); iOS `PortfolioScenarioSheet` (in `PortfolioScreen.swift`).

## 3. Tests

- New `PortfolioPhaseB1Test` (6 tests):
  - account labels without the repeated currency;
  - selected vs all-accounts totals (and their spoken form);
  - short sample copy keeps the full explanation;
  - scenario groups list every fixture exactly once, saved accounts first, unknown ids under "Other";
  - readable unavailable notice;
  - chart height by width.
- Updated: `PortfolioOverviewTest` (badge/scenario split, all-accounts label), `PortfolioPresentationTest` (sample copy).
- Final sequential run, clean outputs: §6.

## 4. Screenshots (`docs/design/portfolio_pb1/`, local MOCK only)

- Android 360 dp:
  - `and-menu.jpg`: ⋯ with "Sample scenario…";
  - `and-sheet*.jpg`: the sheet at 1.0×, full height, groups, and dark 2.0×;
  - `and-multi.jpg`: light 1.0×, Multiple accounts — short banner, "Personal USD", selected / all-accounts totals;
  - `and-expanded.jpg`: Details and chart ⓘ open;
  - `and-cashflows.jpg`: loaded chart at the new height, plus the disabled-Add note;
  - `and-bottom.jpg`: last item above the MOCK strip;
  - `and-dark2x-k9/k10/k11.jpg`: dark 2.0×;
  - `and-missingfx-13.jpg`, `and-missingfx-notice-13.jpg`: light 1.3× Missing FX, "TFSA CAD", readable notice;
  - `and-cashflows-13.jpg`: light 1.3× loaded chart.
- iPhone 16:
  - `ios-offline-account.jpg`: data-backed account, offline banner;
  - `ios-scenario-sheet*.jpg`: the sheet;
  - `ios-multiple-accounts.jpg`, `ios-multi-large.jpg`: Large;
  - `ios-cashflows-large.jpg`: loaded chart at Large;
  - `ios-ax5-before-selector-fix.jpg`, `ios-ax5-before-axis-fix.jpg`, `ios-ax5-overflow-before-badge-fix.jpg`: the AX5 defects found;
  - `ios-ax5-banner.jpg`, `ios-ax5-overview.jpg`, `ios-ax5-metrics.jpg`, `ios-ax5-chart.jpg`, `ios-ax5-add-disabled.jpg`: after the fixes,
    dark AX5.

States rechecked:
- Missing FX (Android 1.3×);
- Multiple accounts (both platforms; Large, AX5, 2.0×);
- flat history (Multiple accounts);
- loaded history (Cash flows, both platforms, Large/AX5/1.0/1.3×);
- empty (Android saved accounts behind the menu; Phase B guest screens on iOS).

**iOS sign-in.** As approved, the Firebase Auth emulator was started from the repository's existing local `firebase/node_modules` (no install was
needed), and a throwaway `@example.test` account was created through the emulator's REST API (credentials only in the session scratchpad). It turned
out not to be needed: the simulator app restored an earlier cached test session ("Test account", offline), which is enough for the MOCK sample
scenarios. **No credentials were typed into the app.**

Restored afterwards:
- iPhone 16 back to Large/light;
- Android: emulator size reset, font scale 1.0, app Theme Light;
- `adb reverse` removed;
- MOCK and Auth emulator stopped.

The iPhone 16 keeps the ad-hoc signed MOCK build installed.

## 5. Findings and remaining risks

- **NOT RUN:**
  - iPhone SE (compact width);
  - TalkBack and VoiceOver navigation (spoken text checked only through the tests);
  - physical devices;
  - `:server:test` (no server change).
- iOS "Offline · last saved portfolio … Check your email and password" appears because the cached test session's token isn't valid against this
  emulator run. This is pre-existing session behaviour, not Phase B.1.
- The Android chart ⓘ row adds a 48 dp touch-target row above the note; acceptable, but a candidate for Phase E polish.
- `StockPriceChange` / `StockBanner` / `StockStateMessage` now re-render on live type changes everywhere they're used. This is the intended effect,
  but other screens weren't re-captured.
- System `Menu` items on iOS hyphenate long words at AX5 ("Delete ac-count…"); that's system rendering, not changed.

## 6. Verification (final sequential run after all changes, clean test outputs)

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` | **BUILD SUCCESSFUL** (8 m 39 s) |
| core JVM / core iOS | **471 / 0** each |
| shared Android host | **156 / 0** (Phase B 150 + 6) |
| shared iOS simulator | **152 / 0** (Phase B 146 + 6) |
| `:app:androidApp:assembleDebug` | OK |
| default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED**, no warnings in the changed files |
| ad-hoc signed simulator build with `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` | BUILD SUCCEEDED (used for the iOS checks) |
| Android emulator 360 dp: light/dark, 1.0× / 1.3× / 2.0× | **RUN** (§4) |
| iPhone 16: Large and AX5 (live switch), light/dark, data-backed | **RUN** (§4) |
| iPhone SE, TalkBack, VoiceOver, physical devices, `:server:test` | **NOT RUN** |

## 7. Phase C readiness

Ready after review. Phase C (holdings, allocation donut, sort, holding details, Insights pass) can build on the B/B.1 pieces. Decisions still open:
D3 (sort), Q6 (by-currency allocation), Q1, Q3, Q4.
