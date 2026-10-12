# Portfolio UI Audit — current implementation vs. the Portfolio UI Reference Design

Status: **audit only** (2026-10-12). No application code was changed. Written against `main` at "Finish Company Comparison QA with a two-column
chart selection summary, scannable chart limitations, heading fit at large text and live Dynamic Type for buttons on Android and iOS"
(`f55fd90`, clean tree). It extends the earlier code audit in `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md` (issues #1–#16, 2026-10-11) and the P-A
baseline in `docs/PORTFOLIO_REFINEMENT_PHASE_PA.md`; this pass adds the **forms, transactions, holding details and Portfolio Insights**, which the
earlier audit only listed, and compares everything with the owner's new reference sheet.

## Evidence used

| Source | What it covers | Inspected |
|---|---|---|
| Code, Android/shared (Compose) | `app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/portfolio/` — `PortfolioScreen.kt` (462 lines), `PortfolioHistoryChart.kt`, `PortfolioForms.kt`, `PortfolioScene.kt`, `PortfolioInsightsScreen.kt`, `PortfolioPresentation.kt`, `PortfolioRoute.kt`; `presentation/AppNavigation.kt` (routes, titles) | read in full / relevant parts |
| Code, iOS (SwiftUI) | `app/iosApp/iosApp/PortfolioScreen.swift` (395), `PortfolioHistoryChart.swift`, `PortfolioForms.swift`, `PortfolioScene.swift`, `PortfolioViewModel.swift`, `PortfolioInsightsScreen.swift` (299); `app/shared/src/iosMain/.../IosAccountClient.kt` (`savePortfolioTransaction`) | read in full / relevant parts |
| Code, core | `core/.../portfolio/PortfolioModels.kt`, `PortfolioPresenter.kt`, `PortfolioEngine.kt` (`validate`), `Decimal.kt` (`PortfolioFormat`), `analytics/PortfolioAnalyticsEngine.kt` (free vs StockSteps+ split), `analytics/PortfolioInsightsEngine.kt` (insight wording), `analytics/AnalyticsModels.kt` | relevant parts |
| Theme | `app/shared/.../theme/Colors.kt` (hex tokens), `designsystem/theme/StockStepsTheme.kt` (Material mapping), `designsystem/components/` (StockTextField, StockSelectField, StockButton…), iOS `DesignSystem/` | relevant parts |
| Screenshots (already in the repo) | `docs/design/phase3_1/android.jpg` (Android light 427 dp: empty, sample hero, chart, holdings, allocation, cash, transactions), `phase3_1/ios-final.jpg` (iPhone light offline account + dark XXXL), `phase3/after-android-dark.jpg` (pre-3.1 dark) | **viewed** this session |
| Reference A | The sheet attached in chat, "StockSteps Portfolio – UI Reference Design (Beginner Friendly)": Overview / Holdings / Insights / Add-Edit Holding / Transactions, dark + light, palette + principles | viewed (chat attachment). **Not in the repository** — see Open questions Q1 |
| Reference B | `docs/design/stocksteps-portfolio-reference.png` ("Refined UI (Phase 2/3 Reference)", 2026-10-10) | viewed |

No simulator or emulator was started, no backend (local or LAN) was contacted and no new screenshots were taken. Any statement marked
**(code)** is from source reading only; **(capture)** means it was seen in a repository screenshot.

---

## 1. Executive summary

- The Portfolio **dashboard** (overview, chart, holdings, allocation, cash and dividends, transactions, insights entry) was refined in Global UI
  Refinement Phase 3/3.1. It's honest about missing data and the two platforms match. Its weak points are hierarchy and density (P-A findings):
  the value and its chart are separated by a full-width button, the chart is empty until a period is tapped, and iOS text scales unevenly.
- The **forms (Add/Edit transaction, Create account) have never been through the refinement**, and they hold the most serious problems:
  raw enum labels ("OPENING POSITION", "NON REGISTERED"), Android number fields with a text keyboard, dates typed as `YYYY-MM-DD`, Material
  buttons that fill with brand blue (white text fails AA), an Android edit form inside an `AlertDialog` with no Cancel, and **one data-integrity
  bug: on Android the account in the transaction form is chosen by name, so two accounts with the same name can get the wrong account's
  transaction (P0)**.
- **Holding details** shows Realized and Dividends as raw ledger strings, without currency or formatting (P1). **Portfolio Insights**
  ("Portfolio Intelligence") skipped the refinement too: iOS uses system fonts outside the theme, rows are 40 dp tall, ISO timestamps are shown,
  and the screen has three different names.
- **The chart titled "Portfolio performance" plots account value, including cash deposits.** A rising line can come from deposits rather than
  investment gains. The data is correct, but the title suggests returns (P1, meaning risk).
- **Reference fit:** the colour tokens already match the new reference almost exactly (dark background `#06111D` vs reference `#07111C`, light
  background `#F7FAFD` identical, brand `#1683FF`). The work is hierarchy, components and forms, not a new theme. Several reference elements
  need data the app doesn't have, belong to StockSteps+ only, or read as advice. Those are kept out or moved to the backlog (§9, §13):
  - total-gain %;
  - cash % in the holdings list;
  - Stocks/ETFs/Cash and sector allocation on the free dashboard;
  - "Consider adding more ETFs";
  - "under the 30% guideline".
- **Recommended order:** fix the P0/P1 form and labelling defects first (Phase A), then the overview card (B), holdings and allocation (C), the
  transactions screen and forms layout (D), and QA (E). Details in `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`.

---

## 2. Current architecture and feature inventory

### 2.1 Navigation (both platforms)

| Destination | Android route (`PortfolioRoute.kt`) / title (`AppNavigation.kt`) | iOS | Notes |
|---|---|---|---|
| Portfolio dashboard | `PortfolioRoute` → `PortfolioScene` → `PortfolioScreen(holdingSymbol = null)` | `PortfolioScene` → `PortfolioScreen` (tab root) | Bottom-tab root. Five tabs unchanged |
| Holding details | `HoldingDetailsRoute(accountId, symbol)` → same `PortfolioScreen` with `holdingSymbol`; top-bar title "Holding details" | `.navigationDestination(item: $holdingSymbol)` → `PortfolioScene(initialSymbol:)` | Reuses the dashboard composable filtered to one symbol |
| Add transaction | `PortfolioEntryRoute(symbol, name, exchange, currency)` → `PortfolioEntryScene` (full screen, top bar "Add transaction") | `.sheet` → `PortfolioEntryScene` (NavigationStack, title "Add investment") | Also opened from Company details / Watchlist via `addPortfolio` |
| Security search for entry | `PortfolioSearchRoute` | `.sheet` → `StockSearchScene` | |
| Edit transaction | `AlertDialog { PortfolioTransactionForm(existing) }` on the dashboard | the same `.sheet` as Add, with `existing` | Platforms differ |
| Account create/settings | `PortfolioAccountDialog` (`AlertDialog`) | `.sheet` → `PortfolioAccountForm` | |
| Portfolio Insights | `PortfolioInsightsRoute` → `PortfolioInsightsScene` (top bar "Insights", screen title "Portfolio Intelligence") | `.navigationDestination(isPresented:)` → `PortfolioInsightsScene` | Entry card is called "Portfolio insights" |
| Practice | `onPractice` from the My Portfolio / Practice segmented control | same | Separate simulated portfolio — **out of scope** |
| Transactions list | **no separate screen** — inline card on the dashboard (5 most recent + "Show all n") | same | Reference shows a dedicated screen with filters |

### 2.2 Feature inventory

| Feature | Android (Compose) | iOS (SwiftUI) | Shared logic | Current status |
|---|---|---|---|---|
| Portfolio overview | `PortfolioScreen.kt:306` `PortfolioHero` + `:268` `PortfolioAccountSelector` + `:253` `PortfolioOverflowMenu` | `PortfolioScreen.swift:244` `hero`, `:215` `accountSelector`, `:81` `header` | core `PortfolioPresenter.state` (`PortfolioUiState`), `PortfolioPresentation.todayChange/signedAmount/direction/freshnessLines` | Implemented, refined in Phase 3/3.1; hierarchy issues (§3) |
| Holdings | `PortfolioScreen.kt:348` `PortfolioHoldingRowView` | `PortfolioScreen.swift:281` `holdingRow` | `PortfolioPresenter.present` (row value, gain, gain % via `Decimal`), `PortfolioPresentation.holdingDescription/holdingChangeLines`, `PortfolioLayout.stackedRowWidth`; `PortfolioDisplayRules.sortHoldings` (P-A, **unused**) | Implemented; no sort/filter UI |
| Holding details | `PortfolioScreen.kt:151` (metric grid + three text buttons) | `PortfolioScreen.swift:146` | — | Implemented; formatting defect (A-F3) |
| Performance | `PortfolioHistoryChart.kt:37` `PortfolioPerformanceCard` → `StockTrendChart` | `PortfolioHistoryChart.swift` (Swift Charts, line + point marks, `chartXSelection`) | `PortfolioPresenter.loadHistory(period)` (server `PortfolioReport.history`, on demand), `PortfolioChartRules.drawable` (≥ 2 dated values) | Implemented; empty until a period is tapped; title meaning risk (A-F6) |
| Allocation | `PortfolioScreen.kt:413` `PortfolioAllocationCard` (bars by holding and by currency) | `PortfolioScreen.swift:342` | server `PortfolioReport.allocations/currencyAllocations`, `PortfolioPresentation.allocationFraction`; `PortfolioDisplayRules.allocationDonutAllowed` (P-A, **unused**) | Implemented as bars; no donut |
| Cash and dividends | `PortfolioScreen.kt:166` `PortfolioValueLines` | `PortfolioScreen.swift:161` `valueLines` | `PortfolioUiState.cash/dividends` (strings "CAD 1,200.00") | Implemented |
| Transactions | `PortfolioScreen.kt:390` `PortfolioTransactionRow` (overflow Edit / Delete…) | `PortfolioScreen.swift:322` | `PortfolioPresentation.transactionLabel/Detail/Amount`, `dateLabel` | Inline list only; no filter; no separate screen |
| Add/Edit | `PortfolioForms.kt:51` `PortfolioTransactionForm`, `:15` `PortfolioAccountDialog`, `:40` `PortfolioChoice`; `PortfolioScene.kt:115` `PortfolioEntryScene` | `PortfolioForms.swift:32` `PortfolioTransactionForm`, `:4` `PortfolioAccountForm`; `PortfolioScene.swift:65` `PortfolioEntryScene` | `PortfolioEngine.validate` (Android client + server `PortfolioService.kt:49`; iOS server only), `PortfolioPresenter.saveTransaction/saveAccount` | Implemented, **not refined**; several P0/P1 defects (§3.3) |
| Empty states | `StockEmptyState` "Start tracking what you own" (`:121`); plain-text holdings/transactions empties | `StockStateMessage` (`:59`) | — | Implemented; copy differs between platforms |
| Loading | `LinearProgressIndicator` (`:107`) | `ProgressView("Loading portfolio")` / "Restoring portfolio" (`:114`, `:66`) | `PortfolioUiState.loading/historyLoading` | Implemented; different on each platform; layout shifts |
| Error / offline | one `StockBanner` with Retry (`:109`) | same (`:116`) | `error`, `offline` | Implemented, consistent |
| Sample (MOCK) | "Sample scenario" `StockSelectField` + SAMPLE banner + badge | same | `PortfolioFixtureCatalog`, `showScenario` | Implemented; takes the top of the screen |
| Education | ⓘ "About these numbers" (`AlertDialog` / sheet), "Why?" toggle for today's change | same (sheet, medium detent) | `dailyNotice`, `historyNotice`, `notice` | Implemented; no per-metric explanations |
| Portfolio Insights | `PortfolioInsightsScreen.kt` (health, performance + benchmark chart, allocation tabs, concentration, contributors, dividends, currency, insights, learn) | `PortfolioInsightsScreen.swift` | `PortfolioAnalyticsPresenter`, server analytics; **StockSteps+ split** in `PortfolioAnalyticsEngine` (performance, benchmark, sectors, asset class, concentration detail, contributors, dividend detail, currency impact are Plus-only) | Implemented; **not refined** (§3.4) |
| Portfolio selection/switching | account selector menu + "+ Add" | `Menu` + "+ Add" | `selectAccount`, `combinedTotal` ("All accounts · …") | Implemented |

### 2.3 Data behaviour to preserve (no change proposed)

| Topic | How it works today (source) | UI implication |
|---|---|---|
| Total value | Server `PortfolioReport.summary.totalValue` when the report matches the account and ledger revision; otherwise the local `PortfolioEngine.value` with live quotes and **no FX** (`PortfolioPresenter.kt`, `present`) — mixed-currency totals stay `null` until the report arrives | Show "—" while unknown; never 0 |
| Cost basis | `investedCost` (moving-average cost, per the account-form note "Moving-average cost is not a legal tax-basis calculation") | Label "Invested cost"; explain in ⓘ |
| Unrealized / realized | `unrealizedGain`, `realizedGain` (reporting currency); per holding `gain` = value − basis, `gainPercent` = gain / basis × 100 (exact `Decimal`, core presenter, `null` when basis is 0) | Amounts and holding % are supported; **portfolio-level total return % is not** (R3, decision D2) |
| Today's change | Server `daily.gain` / `daily.percent` + `daily.notice`; `null` → "Today's change unavailable · Why?" | Keep |
| Percentage returns | Holding gain %; time-weighted and money-weighted returns exist only in Insights (StockSteps+) | A dashboard "total return %" would need an approved presenter field or the Plus TWR (different meaning) |
| Cash | `PortfolioPositionState.cash` per currency; negative = unfunded/borrowed (About text) | No cash **%** field; the reference's "Cash 3.8%" row is not supported |
| Multiple currencies | `PortfolioCurrency` CAD, USD; account reporting currency; trade vs cash currency with a trade-date FX rate; `currencyAllocations`; `fxAsOf` | Always show the currency code next to amounts (see A-F11) |
| Transaction types | 12 `TransactionType`s, human labels in `PortfolioPresentation.transactionLabel` (the dashboard uses them; **the forms don't**) | Reuse the label function in the forms |
| Missing prices | Row `price`/`value`/`gain` `null` → "USD —", "Gain unavailable"; `summary.missing` → "Unavailable: …" caution line | Keep |
| Pending/loading | `loading`, `historyLoading`, `busy` (form disables, "Saving…") | Keep; add skeletons (Phase C/E) |
| Persistence | Signed-in ledger through `UserApi` (`portfolio/transactions` POST/PUT/DELETE) with revision; offline = last saved ledger | Keep; destructive actions confirmed |
| History | `PortfolioReport.history` (dated end-of-day account values, cash included, missing = gaps), loaded only when a period is chosen (`range` is `null` until `loadHistory`) | On-demand stays (R2); retitle (A-F6) |

---

## 3. Android screen-by-screen audit (Compose)

### 3.1 Dashboard
- **Order (capture):** header (title, ⓘ, ⋯) → My Portfolio | Practice → (MOCK) Sample scenario field + banner → status banners → account selector
  + "+ Add" → hero → full-width "+ Add transaction" → Portfolio performance → Holdings (n) → Allocation → Cash and dividends → Transactions (n) →
  Portfolio insights card. All issues #1–#16 of the earlier plan still apply (none implemented).
- **Hero (capture `phase3_1/android.jpg`):** the total ("CAD 1,850.00", `largeNumber`) is immediately visible. "Invested cost 1,455.00",
  "Unrealized P/L ↑ +395.00" and "Realized P/L 0.00" show **no currency code**, unlike the total above them (A-F11). Up to four small lines
  stack under the grid: all-accounts line, notice, two freshness lines and the SAMPLE badge.
- **Account label (capture):** "TFSA CAD · CAD" — the fixture account name already contains the currency, so `accountLabel` repeats it. Real users
  can name accounts the same way. P3 (A-F20).
- **"+ Add transaction" while a sample is shown (capture):** a disabled full-width grey button takes a whole row (`busy = true` in samples).
- **Chart (capture):** the MOCK `mixed-currencies` ledger draws a flat two-point line across the card. Nothing is selected on open (code).
- **Allocation (capture):** "By holding" and "By currency" can be identical (USD 72.97 % = AAPL, CAD 27.03 % = TD.TO) — two blocks of the same
  bars.
- **Transactions (capture):** "Opening position · TD.TO · 5 shares / Sep 1, 2026 · CAD 75.00 per share" — readable. Overflow 48 dp.

### 3.2 Holding details (`PortfolioScreen.kt:151-163`)
- Metric grid: "Price USD 4.00", "Cost USD 8.00", **"Realized" = `row.realized`, "Dividends" = `row.dividends` — raw ledger decimal strings**
  (for example `0` or `12.50000000`), without `PortfolioFormat.amount` and without a currency (A-F3).
- Three stacked `StockButtonVariant.TEXT` buttons (Company details, Price alerts, Add to watchlist) → earlier plan #13.
- The same screen also renders "Cash and dividends" and the account's **total** cash, which isn't specific to the holding (code) — confusing on
  a single-holding screen (A-F15).

### 3.3 Add/Edit transaction and account forms (`PortfolioForms.kt`, `PortfolioScene.kt:115`)

| # | Observed (code) | Expected |
|---|---|---|
| a | `PortfolioChoice("Account", …) { selected -> accountId = available.first { it.name == selected }.id }` (`PortfolioForms.kt:104`) — the account is chosen **by display name**. With two accounts named "TFSA" the second one can't be selected, and choosing it records the transaction in the first. | Choose by id (as iOS does with `.tag($0.id)`) — **P0** (A-F1) |
| b | `PortfolioChoice("Transaction", type.name, TransactionType.entries.map { it.name })` → button "Transaction: OPENING POSITION", menu "CASH DEPOSIT", "DIVIDEND REINVESTMENT"…; account form "Account category: NON REGISTERED" (`replace('_', ' ')`) | `PortfolioPresentation.transactionLabel` / `categoryLabel` ("Opening position", "Non-registered") — P1 (A-F2) |
| c | Shares, price, amount, fees and FX use `OutlinedTextField` with **no `KeyboardOptions`** → full text keyboard | Decimal keyboard (`StockTextField(keyboardType = KeyboardType.Decimal)`) — P1 (A-F4) |
| d | Dates typed as text: "Effective / trade date · YYYY-MM-DD", "Settlement date (optional) · YYYY-MM-DD" | Native date picker writing the same `YYYY-MM-DD` string; text stays possible for accessibility — P1 (A-F5) |
| e | Save is a Material `Button` (and "Create an account first") → `MaterialTheme.colorScheme.primary` = brand `#1683FF` with white text, **3.67:1, below AA** (`StockButton.kt:39` comment); the error uses `MaterialTheme.colorScheme.error` | `StockButton` (PRIMARY → `primaryAction`), `negativeText` token — P1 (A-F7) |
| f | Edit opens the full form inside an `AlertDialog(confirmButton = {})` (`PortfolioScreen.kt:220`): no Cancel, no title bar, long scrolling content in a dialog, keyboard overlap likely (no `imePadding` there; the entry scene has it) | Same full-screen route as Add (or a full-height sheet) with Cancel — P1 (A-F8) |
| g | One error line above Save (`(error ?: state.error)`); `state.error` is the **global** presenter error, also shown in the dashboard banner. Messages come from `PortfolioEngine.validate` and are reasonable ("Choose a security and a positive share quantity.") but aren't attached to a field. | Error shown next to the field it concerns where the message maps to one field; summary at the top; a local error only — P2 (A-F9) |
| h | Field order: Account → Transaction → Trade/cash currency → symbol, name, exchange → shares/price or amount → fees → date → cash currency → FX → settlement → notes. Currency appears twice; date sits after fees. "Exact security symbol" is free text even though "Search and select a security" exists above the form as a plain `TextButton`. | Security (search first) → type → date → shares/price or amount → fees → (Advanced: currencies, FX, settlement, notes) — P2 (A-F10) |
| i | Entry scene: raw `Button("Create an account first")` and `TextButton("Search and select a security")` above the form, with no heading or explanation; title inside the form is "Add investment or transaction" while the top bar says "Add transaction" | One heading, actions as `StockButton` / `StockNavigationRow` — P2 |
| j | `PortfolioChoice` uses `OutlinedButton` + `DropdownMenu` and doesn't say which value is selected in the menu | `StockSelectField` (already used for "Sample scenario") — P2 |
| k | Account dialog note: "Account categories organize investments; calculations are not tax advice." (iOS: "Categories organize investments. Moving-average cost is not a legal tax-basis calculation.") | One shared copy — P3 |
| l | Success: the entry scene closes when the ledger revision grows (`PortfolioEntryScene`); no confirmation message | Short confirmation on the dashboard (snackbar / banner) — P3 |

### 3.4 Portfolio Insights (`PortfolioInsightsScreen.kt`)
- Never through Global UI Refinement: `FilterChip` (Material) for accounts and periods instead of `StockChip`/`StockPillSelector`;
  `PortfolioChoice` for the sample scenario; hard-coded `40.dp` metric rows (< 48 dp), `6.dp` bars, `48.dp` (fine) — 5 hard-coded `dp` (A-F13).
- "As of 2026-10-08 21:15 UTC" from `asOf.take(16).replace('T', ' ')` instead of `PortfolioDates.dateTime` (A-F14).
- Naming: entry card "Portfolio insights", top bar "Insights", screen title "Portfolio Intelligence" (A-F12).
- Content is well behaved: "There's no single score; each number is explained"; locked sections say "StockSteps+" instead of showing 0;
  insight sentences are factual ("All your invested money is in one security", "Past differences don't indicate future results").

### 3.5 States
- Empty (capture): good — icon tile, "Start tracking what you own", one line, "Create portfolio".
- Loading: a top `LinearProgressIndicator`; content jumps when data arrives (earlier #11).
- Offline/error: one banner with Retry (consistent).

---

## 4. iOS screen-by-screen audit (SwiftUI)

### 4.1 Dashboard (`PortfolioScreen.swift`)
- Same order and content as Android (by design). Differences are in §5.
- **Dynamic Type:** 34 `StockStepsTheme.font(type.x)` calls without `relativeTo:` (counted this pass). At XXXL, captions grow faster than values —
  in `phase3_1/ios-final.jpg` (dark XXXL) the freshness lines are almost as large as the metric values (A-F16, earlier #4).
- **Hero total** `.minimumScaleFactor(0.7).lineLimit(2)` — shrinks a financial value instead of wrapping (earlier #4).
- **Touch targets:** 12 hard-coded `44` frames (`frame(width: 44, height: 44)`, `minHeight: 44`) instead of `dims.touchTarget`.
- **Live Dynamic Type:** `typeSize` is read only for the holdings stacking decision; the hero, chart and allocation won't refresh on a live
  text-size change until the screen is reopened (the known limitation from the Comparison QA closure).
- **Chart (capture):** a flat series gets a ±1 domain, so the y axis shows invented-looking ticks "1,851.0 / 1,850.5 / 1,850.0 / 1,849.5 / 1,849.0"
  for a single value (`PortfolioHistoryChart.swift:31`). The data isn't changed, but the precision suggests movement that isn't there (A-F17).
  Point marks, plus a selection line above the chart that defaults to the last value; Android draws no point marks.
- **Copy drift:** "Read-only sample; saved accounts and watchlists stay unchanged." (iOS) vs "Read-only sample. Switch to My saved accounts to make
  changes." (Android); empty state "Your watchlist stays separate." vs "Saving a stock to a watchlist does not buy it."; holdings empty "Add an
  investment with its effective date." vs "Add an existing investment with its effective date, or record a buy." (A-F18).

### 4.2 Holding details
- Same raw `row.realized` / `row.dividends` values (A-F3). Three plain `Button`s with `minHeight: 44` (system tint, not `stockSecondary`).

### 4.3 Add/Edit transaction and account forms (`PortfolioForms.swift`)
- Native `Form` + `Picker`s with **raw enum strings**: `ForEach(["OPENING_POSITION", "BUY", …]) { Text($0.replacingOccurrences(of: "_", with: " ")) }`
  → "OPENING POSITION"; categories "NON REGISTERED" (A-F2).
- Good: `.keyboardType(.decimalPad)` on number fields, characters autocapitalization on the symbol, Cancel in the toolbar, account picked by id.
- Dates are text fields ("Effective / trade date · YYYY-MM-DD") — no `DatePicker` (A-F5).
- Save is a plain `Button` inside the form, not `.stockPrimary`; error `Text(error).foregroundStyle(.red)` (system red, not `negativeText`) (A-F7).
- **No client validation:** iOS sends the transaction straight to the server (`IosAccountClient.savePortfolioTransaction`); errors come back as the
  global `state.error` after a round trip, so the dashboard banner shows them too. Android runs `PortfolioEngine.validate` before sending (A-F9).
- Fields are shown or hidden by `shareType || !symbol.isEmpty` (Android: `sharesRequired || transfer && symbol`) — a slightly different set of fields
  on each platform for the same type (A-F10).
- Form fonts are system defaults (not `StockStepsTheme`), which is acceptable for a native `Form`, but the caption uses `.font(.caption)`.

### 4.4 Portfolio Insights (`PortfolioInsightsScreen.swift`)
- 17 system font calls (`.font(.caption)`, `.subheadline.weight(.semibold)`, `.caption2`) — `.caption2` grows faster than values at large sizes (the
  CLAUDE.md rule); 5 theme fonts without `relativeTo:`; 3 hard-coded `44`s (A-F13).
- Chart legend as text "— Portfolio" / "- - S&P 500" instead of the shared `StockChartSeriesStyle` swatches built in Comparison 5C (A-F19).
- ISO "As of" timestamp (A-F14); three names (A-F12).

---

## 5. Cross-platform differences

| Topic | Android | iOS | Target |
|---|---|---|---|
| Loading | top `LinearProgressIndicator` | centred `ProgressView` with text | shared skeleton (earlier #11) |
| Total value overflow | wraps | shrinks to 70 % | wrap rule, no scale factor |
| Text scaling | uniform `sp` × font scale | per-token curves (uneven) | paired curves on iOS |
| Chart | `StockTrendChart`, no points, touch details | Swift Charts, point marks, selection text, ±1 flat domain | one spec (earlier #12) + flat-series rule |
| Edit transaction | `AlertDialog` with the form, no Cancel | full sheet with Cancel | full screen/sheet with Cancel on both |
| Number keyboard | text keyboard | decimal pad | decimal on both |
| Account choice in form | **by name** | by id | by id |
| Client validation | `PortfolioEngine.validate` before sending | none (server only) | same validation before sending on both |
| Field visibility per type | `sharesRequired || transfer && symbol` | `shareType || !symbol.isEmpty` | one shared rule (`PortfolioFormRules`) |
| Form titles | "Add investment or transaction" / top bar "Add transaction" | "Add investment" / "Edit transaction" | one shared copy |
| Sample/empty/holding copy | see A-F18 | see A-F18 | `PortfolioDisplay` |
| Delete transaction confirm | explains recalculation and the "later sale" rule | title only | same text |
| About | `AlertDialog` | medium-detent sheet | keep native (documented) |
| Refresh | ⋯ menu | ⋯ menu + pull-to-refresh | keep (iOS idiom) |

---

## 6. Design system inconsistencies

| Where | Hard-coded / bypass | Existing token or component to use |
|---|---|---|
| Android forms (`PortfolioForms.kt`, `PortfolioScene.kt:133`) | `OutlinedTextField`, `OutlinedButton`, Material `Button`, `MaterialTheme.colorScheme.error` | `StockTextField` (label, supporting, `error`, `keyboardType`, `prefix`), `StockSelectField`, `StockButton`, `colors.negativeText` |
| iOS forms | `.foregroundStyle(.red)`, plain `Button`, `.font(.caption)` | `colors.negativeText`, `.buttonStyle(.stockPrimary)`, theme caption on a paired curve |
| iOS dashboard | 12 × `44`, 34 fonts without `relativeTo:`, `minimumScaleFactor(0.7)` | `dims.touchTarget`, paired `relativeTo:`, wrap rule |
| Insights, Android | `FilterChip`, `40.dp`, `6.dp`, `textSecondary`/`textTertiary` (legacy names; aliases of `textSupporting`/`textMeta`) | `StockChip`/`StockPillSelector`, `rowMinHeight`, `rangeBar`, semantic names |
| Insights, iOS | 17 system fonts, text legend | theme fonts, `StockChartSeriesStyle` legend swatches |
| Allocation bars | `colors.primary` fill for every bar | fine (labels carry meaning); donut slices later need a series palette (`StockChartSeriesStyle`) — never gain/loss colours |
| Hero metric values | no currency code | currency label rule (A-F11) |

The reference palette (Success `#22C55E`, Negative `#EF4444`, Warning `#F59E0B`, Learn `#7C3AED`) differs slightly from our tokens (positive `#16A86B`/
`#20D98B`, negative `#E5484D`/`#FF5B57`). **Keep our tokens**: they're contrast-tested (`DesignTokensTest`) and shared by every refined screen.
Don't add a Portfolio palette.

---

## 7. Accessibility findings

| # | Platform | Finding | Severity |
|---|---|---|---|
| X1 | Android | Material `Button` fills with brand `#1683FF` behind white text in the forms (3.67:1 < 4.5:1) | P1 |
| X2 | both | Raw enum text is also what TalkBack/VoiceOver read ("OPENING POSITION" may be spelled out or read with odd emphasis) | P1 |
| X3 | iOS | Dashboard captions on faster Dynamic Type curves than values; total shrinks at large sizes | P1 |
| X4 | Android | Edit form in an `AlertDialog`: no Cancel button (only tapping outside or Back), long content | P1 |
| X5 | both | Date entry by typing `YYYY-MM-DD` — hard for screen-reader and motor-impaired users | P1 |
| X6 | both (Insights) | 40 dp rows (Android), `.caption2` text (iOS) | P2 |
| X7 | both | Error text not associated with its field (no `isError`/`supportingText` on Android; no accessibility hint on iOS) | P2 |
| X8 | both | Holding rows: one spoken description including direction in words ✔; chart descriptions present ✔; headings ✔ (`heading()` / `.isHeader`) | keep |
| X9 | both | Gain/loss in words + sign + arrow ✔ (`StockPriceChange`, `holdingDescription`) | keep |
| X10 | both | TalkBack navigation and VoiceOver **not verified** for any Portfolio screen (environment limits, see §14) | open |

---

## 8. Data integrity and financial display risks

| # | Risk | Where | Severity | Proposal |
|---|---|---|---|---|
| D-R1 | Transaction recorded in the wrong account when two accounts share a name | `PortfolioForms.kt:104` | **P0** | Select by id; label shows `accountLabel` + category detail |
| D-R2 | "Portfolio performance" title over an account-value line that includes cash deposits; a deposit looks like a gain | `PortfolioHistoryChart.kt:43`, `PortfolioHistoryChart.swift:40` | P1 | Title "Account value", subtitle "Includes cash, deposits and withdrawals. Returns that exclude deposits are in Insights." No data change |
| D-R3 | Holding details show raw `realized` / `dividends` decimals with no currency | `PortfolioScreen.kt:156-157`, `.swift:151-152` | P1 | `PortfolioFormat.amount` + `row.currency` (or `signedAmount` for realized) |
| D-R4 | Hero metric values without currency next to a total with a currency | hero on both | P2 | "CAD 1,455.00" or a single "Amounts in CAD" line; decide one rule |
| D-R5 | iOS flat-series ±1 domain shows ticks that look like movement | `PortfolioHistoryChart.swift:31` | P2 | For a flat series, show one axis label (the value) or hide the y axis; shared rule |
| D-R6 | Holdings use live quotes (`WatchDataRepository`) while the total, allocation and chart use the server report (`quoteAsOf`) — timestamps can differ, so holding values may not add up to the total at a glance | `PortfolioPresenter.present` vs `report` | P2 (verify) | Show the holdings freshness ("Quotes delayed"/stale line exists per row); don't recompute; document in ⓘ |
| D-R7 | Reference items that would need a UI calculation: total gain % ("+14.76 %"), cash % in holdings, "Total value $2,305.00" in the form (= shares × price, before fees) | reference | rule | Not adopted without a presenter/core field (D2) or approval; the form preview, if wanted, comes from a shared exact-`Decimal` helper and is labelled "Before fees" |
| D-R8 | Reference "Stocks/ETFs/Cash" and "Top sectors" on the free dashboard: asset-class and sector allocations are **StockSteps+ only** (`PortfolioAnalyticsEngine`: `freeAllocation = allocation.copy(bySector = emptyList(), byAssetClass = emptyList())`) and come from the analytics endpoint, not the dashboard report | reference | rule | Keep them in Insights (Plus); the dashboard shows by holding and by currency only |
| D-R9 | Reference insight "Consider adding more ETFs … reduce risk" is a recommendation; "under the 30% guideline" invents a threshold | reference | rule | Not adopted. Existing engine insights are factual and stay |
| D-R10 | Allocation % is of **account value incl. cash**, and can exceed 100 % with borrowed cash | About text | keep | Donut only via `allocationDonutAllowed` (R4) |

---

## 9. Reference design comparison

### 9.1 Dimension table (dashboard / overview)

| Dimension | Current UI | Reference direction | Recommended change | Priority |
|---|---|---|---|---|
| Information hierarchy | Title → segmented control → (MOCK field) → account row → hero → full-width button → chart card → long sections | Title → Overview/Holdings/Insights tabs → one overview block (value, change, ranges, chart) → Invested / Total gain tiles → allocation donut | One overview card (value, today's change, chart, ranges, metric tiles); actions out of the hero path; sample selector into ⋯ | P1 |
| Typography | Correct token ladder; iOS curves uneven; section titles outside cards | Large value, small labels, compact in-card titles | Keep tokens; pair iOS curves; in-card `cardTitle` for secondary sections | P1 (iOS) / P2 |
| Colours | Tokens already match the reference background/surface/brand | Navy dark, green chart line, coloured tiles | Keep tokens. **Chart line stays brand blue** (R8: colour never encodes direction); gain/loss colours only for change text with words/arrows | P2 |
| Card styling | `StockCard` everywhere, equal weight | One dominant card, smaller tiles (Invested / Total gain) | Overview card + inner tiles on `surfaceSecondary` | P2 |
| Spacing | `md` between every block, `md` top padding on titles → long page | Tight, grouped | Group related blocks; fewer section titles | P2 |
| Financial data | Total, today's change, invested, unrealized, realized, all-accounts, freshness | Total, total gain + %, today, invested, total gain + % | Show what exists; **no total-gain %** (D2); "Total gain" = unrealized + realized would also be a UI calculation → no | rule |
| Navigation | Segmented My Portfolio/Practice; holdings inline; Insights as a row | Second tab row Overview/Holdings/Insights | Don't add tabs now (Q3); "View all" for holdings and a Transactions screen are lighter options | P3 / decision |
| Accessibility | Good semantics; iOS scaling + 44s; forms poor | Large text, clear contrast (claimed) | §7 fixes | P1 |

### 9.2 Per-screen comparison

| Reference screen | Current equivalent | Keep | Improve | Fix | Backlog / not adopted |
|---|---|---|---|---|---|
| 1 Overview | Dashboard hero + performance card | Honest missing states, "Why?", freshness, ⓘ | One card; ranges inside; tiles for Invested / Unrealized / Realized; "Show 1M" action instead of the empty block (R2) | iOS scaling; currency codes on metrics (D-R4); chart title (D-R2) | Total gain %; asset-class donut (Plus); auto-load history (D1) |
| 1b Asset allocation | Allocation bars (holding, currency) | Labels + % + amount | Donut + legend **only** when `allocationDonutAllowed`; hide "By currency" when it equals "By holding"? (Q6) | — | Stocks/ETFs/Cash (Plus, analytics) |
| 2 Holdings | Holdings card | One spoken label per row, stacking at large text | Two right-hand lines (value, signed %); weight % per row from `allocations[].percent` (server value, display only); "Name / Value / Gain/Loss" column header only in the wide layout; optional sort (D3); "+ Add holding" at the end (tonal) | — | Cash row with % (no field); logos in MOCK |
| 3 Insights | Portfolio Intelligence (separate screen) | Factual insights, no score, Plus locks | Rename consistently; card list with icons like the Comparison insights card (`ObservationIcon` pattern); theme fonts; shared legend | A-F12…A-F14, A-F19 | "Consider adding…" (advice); "30% guideline" (invented threshold); "Good growth exposure" (judgement) |
| 4 Add/Edit holding | Add/Edit transaction form | Engine validation, opening position as default, honest effective-date note | Search-first security selection; Buy / Sell / More types as a segmented control + "More types" select; date picker; Advanced section for currencies/FX/settlement/notes | A-F1, A-F2, A-F4, A-F5, A-F7, A-F8 | "Select from recent" chips (could reuse Home recent companies — needs approval); computed "Total value" (D-R7) |
| 5 Transactions | Inline card (5 + Show all) | Readable rows, overflow Edit/Delete, confirmations | Separate "Transactions" destination inside Portfolio (not a tab) with All / Buy / Sell / Dividend filter chips — filtering an existing list is display only; amount column right-aligned | — | Per-row logos when available |
| Empty state | `StockEmptyState` | Copy and single action | v2 with secondary action to Learn / Practice | — | "Popular ideas" list (advice risk) |

---

## 10. Prioritised findings

Severity: **P0** financial correctness / broken / severe a11y · **P1** significant usability or layout · **P2** consistency and polish · **P3** optional.
Earlier plan items keep their numbers (#1–#16); new ones are **A-F1…A-F20**.

| ID | Sev | Platform | Screen | Location | Observed | Expected | Proposed solution |
|---|---|---|---|---|---|---|---|
| A-F1 | **P0** | Android | Add/Edit transaction | `PortfolioForms.kt:104` | Account chosen by name; duplicate names pick the first account | Choice by id | `StockSelectField<PortfolioAccount>` keyed by id, label `accountLabel` + detail |
| A-F2 | P1 | both | Forms | `PortfolioForms.kt:26,105`; `PortfolioForms.swift:17,65` | "OPENING POSITION", "NON REGISTERED" | "Opening position", "Non-registered" | `PortfolioPresentation.transactionLabel` / `categoryLabel` for option text; enum values unchanged |
| A-F3 | P1 | both | Holding details | `PortfolioScreen.kt:156-157`; `.swift:151-152` | Raw `realized` / `dividends` strings, no currency | "USD 12.50" / "+USD 3.00" | `PortfolioFormat.amount` / `signedAmount` with `row.currency` |
| A-F4 | P1 | Android | Forms | `PortfolioForms.kt:113-121` | Text keyboard for numbers | Decimal keyboard | `StockTextField(keyboardType = KeyboardType.Decimal)` |
| A-F5 | P1 | both | Forms | Android `:117,120`; iOS `:77,80` | Typed `YYYY-MM-DD` | Date picker | Material `DatePickerDialog` / SwiftUI `DatePicker`, writing the same string (UTC date, no time) |
| A-F6 | P1 | both | Overview chart | `PortfolioHistoryChart.kt:43`; `.swift:40` | "Portfolio performance" over account value incl. deposits | Name says what's plotted | "Account value" + one-line note pointing to Insights for returns |
| A-F7 | P1 | both | Forms | Android `PortfolioForms.kt:123`, `PortfolioScene.kt:133`; iOS `:84`, `:83` | Brand-blue Material button (fails AA); `.red` / Material error colour | `primaryAction` fill; `negativeText` | `StockButton` / `.stockPrimary`; theme tokens |
| A-F8 | P1 | Android | Edit transaction | `PortfolioScreen.kt:220-225` | Form inside an `AlertDialog`, no Cancel | Full screen or full-height sheet with Cancel | Reuse `PortfolioEntryRoute` with a transaction id, or a `ModalBottomSheet`; needs a Route arg (`transactionId`) — navigation identity only |
| #1 | P1 | both | Overview | dashboard | Value, button, chart split | One overview card | `StockOverviewCard` (earlier C1) |
| #2 | P1 | both | Overview chart | `PortfolioPresenter.range = null` | Empty chart until a pill is tapped | Compact "Show 1M" action (R2) | UI only |
| #4 / A-F16 | P1 | iOS | Dashboard | `PortfolioScreen.swift` (34 fonts, `:249`, 12 × 44) | Uneven scaling; shrinking total | Paired curves, wrap, `dims.touchTarget` | As earlier plan #4 |
| A-F9 | P2 | both | Forms | Android `:122`; iOS `:83`, `IosAccountClient.kt:157` | One error line, global error; iOS validates only on the server | Local, field-level errors; same validation before sending | Call `PortfolioEngine.validate` from the iOS bridge (or a shared `PortfolioFormRules.validate` wrapper) — **same rules, no new rules**; map known messages to fields |
| A-F10 | P2 | both | Forms | both form files | Order puts currencies before the security and the date after fees; fields per type differ by platform | Security → type → date → amounts → fees → Advanced | Shared `PortfolioFormRules` (visible fields per type, labels, order) + tests |
| A-F11 | P2 | both | Overview | hero grids | Metrics without currency | Currency visible once for the card or per value | Decide rule (Q5) |
| A-F12 | P2 | both | Insights | entry card, `AppNavigation.kt:170`, screen titles | Three names | One name | "Portfolio insights" everywhere |
| A-F13 | P2 | both | Insights | `PortfolioInsightsScreen.kt` (5 dp, `FilterChip`); `.swift` (17 system fonts, 3 × 44) | Outside the design system | Tokens/components | Insights refinement phase (Plan Phase C/E) |
| A-F14 | P2 | both | Insights | Android `:188`; iOS `:170` | "As of 2026-10-08 21:15 UTC" | "As of Oct 8, 2026, 9:15 PM UTC" | `PortfolioDates.dateTime` |
| A-F17 | P2 | iOS | Overview chart | `PortfolioHistoryChart.swift:28-34` | ±1 domain ticks on a flat series | No suggested movement | Shared flat-series rule (`PortfolioChartRules.flat`) → single label |
| A-F18 | P2 | both | Dashboard | sample banner, empty state, holdings empty | Different copy per platform | Same copy | `PortfolioDisplay` (earlier C4) |
| A-F19 | P2 | iOS | Insights chart | `PortfolioInsightsScreen.swift:291-292` | Text legend "— / - -" | Drawn swatches | `StockChartSeriesStyle` (exists) |
| #5–#7, #9, #11, #12 | P2 | both | Allocation, holdings, density, actions, loading, chart spec | see earlier plan | — | — | Unchanged from the earlier plan |
| #8 | P2 | both | MOCK only | sample field | Takes the top of the screen | In ⋯ | Unchanged |
| A-F15 | P3 | both | Holding details | `PortfolioScreen.kt:166`; `.swift:161` | Account-wide cash and dividends shown on a holding screen | Holding-specific values only | Hide "Cash" on holding details; keep the holding's dividends (already in the grid) |
| A-F20 | P3 | both | Account selector | `PortfolioPresentation.accountLabel` | "TFSA CAD · CAD" | No duplicate currency | Omit " · CUR" when the name already ends with it (display only) — or leave (Q7) |
| #10, #13–#15 | P3 | both | Empty state, details actions, transactions collapse, account chip | see earlier plan | — | — | Unchanged |

---

## 11. Features to preserve

- Missing data stays missing: "—", "Gain unavailable", "Today's change unavailable · Why?", "None recorded", "Unavailable: …" caution line.
- One spoken description per holding row, with direction in words; headings on section titles; chart descriptions.
- Exact-decimal formatting through `PortfolioFormat` / `PortfolioPresentation`; nothing recomputed in the UI.
- On-demand history (R2) and the ≥ 2-values chart rule (`PortfolioChartRules`).
- About these numbers (ⓘ) and the Why? toggle; the sample disclosures (banner, badge, global strip).
- Confirmations before deleting accounts and transactions; offline/error banner with Retry.
- Practice stays a separate segmented destination — never mixed into totals.
- Insights: no score, Plus locks explained, factual insight sentences, "How these numbers work".
- Native idioms: iOS pull-to-refresh, Menus and sheets; Android dropdowns and dialogs where they fit.
- P-A shared rules: `allocationDonutAllowed`, `sortHoldings` (+ tests), `PortfolioLayout.stackedRowWidth`.

---

## 12. Proposed refined screen structure (existing functionality only)

### Portfolio (dashboard)
1. Header: "Portfolio" · ⓘ · ⋯ (Add transaction, Refresh, Account settings, (MOCK) Sample scenario…, Delete account…).
2. My Portfolio | Practice (unchanged).
3. Status banners (offline/error, sign-in) — unchanged.
4. **Overview card**: account chip ("Personal · CAD ▾", + Add account in its menu) → "Total value" + SAMPLE badge → total (wraps) → today's change
   or "unavailable · Why?" → range pills + chart ("Account value"; "Show 1M" action when nothing is loaded) → tiles: Invested cost · Unrealized
   P/L · Realized P/L (currency per rule Q5) → one freshness line → caution notice.
5. **Holdings (n)** card: rows (logo/monogram · name · ticker · shares — value · signed % · weight %), optional sort (D3), "+ Add transaction"
   tonal row at the end, "View all" when n > 5 (Q4).
6. **Allocation** card: donut + legend when allowed, otherwise bars; "By currency" as a second legend group.
7. **Cash and dividends** (unchanged content).
8. **Transactions** preview (3–5 rows) + "See all transactions" → Transactions screen (Q4).
9. **Portfolio insights** row (renamed consistently).

### Holdings
- Stays inside the dashboard card (no sub-tab). If the owner wants a full list: "View all" → a Holdings destination reusing the same rows and
  `sortHoldings` (Q3/Q4).

### Holding details
- Holding row header → metric grid (Price, Cost, Market value, Unrealized, Realized, Dividends — all formatted with currency) → actions as a
  `StockNavigationRow` group → this holding's transactions. No account-wide cash.

### Portfolio insights
- Existing sections, refined: header with account chips (`StockChip`) and period pills; the insight cards with category icons (Comparison
  pattern); sections collapsible after the first two; legend swatches; readable "As of".

### Add/Edit transaction
- Security: search field (opens the existing search) showing the chosen "AAPL · Apple Inc. · NASDAQ"; type: Buy | Sell | Opening position +
  "More types" (the other 9, readable labels); date picker; shares + price per share (decimal keyboards) or amount; fees; **Advanced** (collapsed):
  trade/cash currency, FX, settlement date, notes, name/exchange overrides; field errors; Save (`primaryAction`) + Cancel; account select by id at
  the top when > 1 account.

### Transactions (new destination inside Portfolio, not a tab — needs approval)
- Title "Transactions (n)"; filter chips All · Buys · Sells · Dividends · Cash · Other (display filter over `state.transactions`); rows:
  type · symbol · shares / date · amount (right) / overflow Edit · Delete…; empty "No transactions match this filter"; error/offline banner.

---

## 13. Backlog recommendations (need new data, logic or approval)

| Item | Why it's backlog |
|---|---|
| Total return % / "Total gain (+x %)" on the overview | Needs an exact-decimal presenter field (D2); TWR exists only in Plus Insights and means something else |
| Cash % row in holdings | No cash-allocation field in `PortfolioReport` |
| Asset-class (Stocks/ETFs/Cash) donut and Top sectors on the dashboard | StockSteps+ analytics data, separate endpoint |
| Overview / Holdings / Insights sub-tabs | New navigation inside Portfolio; reference only (Q3) |
| Recent-securities chips in the form | Could reuse Home's recent companies; new dependency in the form scene |
| "Total value" preview in the form | A calculation (shares × price); only with a shared exact helper and approval (D-R7) |
| Company logos for MOCK holdings | Fixture change (not allowed in UI phases) |
| Automatic 1M history on open | More requests per open (D1) |
| Advice-like insights ("Consider adding…", "guideline") | Not adopted (no-advice rule) |
| Home `PortfolioSummaryCard` on the overview card | Home phase |

---

## 14. Open questions

- **Q1 — Reference file:** the new reference sheet was attached in chat but isn't in the repository. Save it as
  `docs/design/stocksteps-portfolio-reference-v2.png` (path to confirm) so the plan and later closures can cite it?
- **Q2 — P0 timing:** fix A-F1 (account chosen by name) immediately as a stand-alone change, before the UI phases?
- **Q3 — Sub-tabs:** keep the single-scroll dashboard (recommended) or add Overview | Holdings | Insights inside Portfolio (a second segmented row
  under My Portfolio | Practice)?
- **Q4 — Transactions screen:** approve a "Transactions" destination inside Portfolio (Route with no new data) with display-only filters?
- **Q5 — Currency on metrics:** "CAD 1,455.00" on every value, or one "Amounts in CAD" line for the card?
- **Q6 — Allocation by currency:** hide it when it repeats the by-holding split (one currency per holding), or always show it?
- **Q7 — Account label:** drop the duplicated currency ("TFSA CAD · CAD")?
- **D1/D2/D3** (earlier plan): history on open, total return %, holdings sort — still open.

### Screenshots still needed (none taken in this audit)

All on the local MOCK backend (`./gradlew :server:runMock`, `adb reverse tcp:8081 tcp:8081`; iOS built with
`STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081`), only after approval:

| Platform | Theme | Text size | States |
|---|---|---|---|
| Android 360 dp | Light | 1.0× | dashboard (empty, sample `mixed-currencies`, loading), holding details, **Add transaction form** (top, Advanced fields, error), edit dialog, account dialog, Insights (free + Plus) |
| Android 360 dp | Dark | 1.0× | dashboard, form, Insights |
| Android 360 dp | Light | 1.3× | dashboard, holdings, form |
| Android 360 dp | Dark | 2.0× | dashboard, holdings, form, Insights |
| iPhone 16 | Light / Dark | Large | dashboard, holding details, form sheet, account sheet, Insights |
| iPhone SE (iOS 27, `4BFB2D70-…`) | Light | Accessibility (AX1–AX3) | dashboard, form |
| iPhone SE | Dark | AX5 | dashboard, holdings, form, Insights |
| either | — | — | a history-loaded chart with ≥ 3 distinct values (the `mixed-currencies` sample draws a flat two-point line) |

No forms, holding details, edit dialog or Insights captures exist in the repository today; the form and Insights findings above are **code-based**.
TalkBack (the owner must answer the Accessibility Suite notification prompt once) and VoiceOver (physical device) remain unverified.
