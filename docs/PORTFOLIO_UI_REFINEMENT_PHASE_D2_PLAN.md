# Portfolio UI Refinement — Phase D2 plan: Transaction history

Status: **plan approved by the owner, 2026-10-13, with changes, and implemented (committed and pushed with D3 as "Add Portfolio Phases D2 and D3 with transaction history filters, transaction details, confirmed-only add/edit/delete feedback, reusable transaction rows and sample read-only handling on Android and iOS"): see `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D2.md`.**
The owner's decisions replace §9's recommendations where they differ:
- Opening position → **Other**, not Buys;
- Delete hidden in sample mode;
- the link always shows when there is at least one transaction;
- D7 → Phase D3.

Original status: plan for owner review.
- Base: "Close Portfolio Phase D1.1 transaction form polish with all-at-once field validation, type-switch error clearing, selected-security line,
  scoped Android keyboard resize and stacked large-text actions on Android and iOS" (pushed).
- Covers plan items **D5** (Transactions destination, Q4) and **D6** (dashboard preview + link).
- **D7** (success confirmation after save) stays out of D2 unless the owner adds it (§9).

## 1. Goal and boundaries

Goal: a **Transaction history** screen inside Portfolio (not a tab). It lists the selected account's transactions, grouped by month, with
display-only filters. The dashboard and Holding details keep a short preview and link to it.

Not changed:
- `PortfolioPresenter` / `PortfolioUiState`;
- the ledger, `PortfolioEngine`, `Decimal`;
- API, server, fixtures;
- entitlements, sample-data labelling;
- the five tabs.

History rows need nothing new:
- `state.transactions` is already the selected account's full list, sorted newest first (`tradeDate`, then `createdAt`; `PortfolioPresenter.kt:213`);
- the dashboard already holds the whole list and only shows 5 of it.

**No totals.** The screen never sums, nets or computes amounts per month or per filter (D-R7: net amounts are server-only). It shows only the existing
row text (`PortfolioPresentation.transactionText`) and counts of rows.

## 2. Navigation (needs Q4 approval)

| Platform | Change |
|---|---|
| Android | New `@Serializable internal data class PortfolioTransactionsRoute(val symbol: String? = null)` in `PortfolioRoute.kt`: identity only. `symbol` scopes the list to one holding (from Holding details). Registered in `AppNavigation.kt` next to `PortfolioInsightsRoute`; added to `hasBack` and the top-bar title `when` ("Transactions"). |
| iOS | `PortfolioScene.swift`: `@State showTransactions` + `.navigationDestination(isPresented:)`, the same pattern as Insights. A holding-scoped variant passes the symbol. No change to `AppRoute` (the five tabs). |

Route → Scene → Screen boundary:
- **Scene** (`PortfolioTransactionsScene`, both platforms) acquires the existing portfolio presenter. On Android that's
  `viewModel(key = "portfolio") { PortfolioViewModel(accounts.portfolioPresenter) }`, the same shared presenter as the dashboard, so the destination
  makes no second load. On iOS it reuses the `PortfolioViewModel` passed from `PortfolioScene`. The scene wires edit, delete, refresh and account
  selection.
- **Screen** only renders.

## 3. Shared display and filter rules (`app/shared/.../presentation/portfolio/PortfolioTransactionsDisplay.kt`, new)

A pure Kotlin object used by Compose and the iOS bridge (no Compose types):

```kotlin
enum class PortfolioTransactionFilter { ALL, BUYS, SELLS, DIVIDENDS, CASH, OTHER }

object PortfolioTransactionsDisplay {
    val filters: List<PortfolioTransactionFilter>                       // fixed order: All · Buys · Sells · Dividends · Cash · Other
    fun label(filter): String                                           // "All", "Buys", …
    fun filterOf(type: TransactionType): PortfolioTransactionFilter     // exhaustive `when`: a new enum value fails to compile
    fun filter(transactions, filter, symbol: String? = null): List<PortfolioTransaction>   // keeps the presenter's order, never re-sorts
    fun groups(transactions): List<PortfolioTransactionMonth>           // ("October 2026", rows) newest first, from tradeDate "YYYY-MM"
    fun emptyMessage(filter, symbol): String                            // "No sells recorded in this account yet." / "…for AAPL yet."
    fun summary(count, filter): String                                  // "12 transactions" / "3 sells": a row count, not an amount
    fun spokenFilter(filter, count): String                             // "Sells, 3 transactions", for the live region on change
}
```

Proposed type → filter mapping (**owner decision D2-Q1**):

| Filter | Types |
|---|---|
| Buys | `BUY`, `OPENING_POSITION` |
| Sells | `SELL` |
| Dividends | `DIVIDEND`, `DIVIDEND_REINVESTMENT` |
| Cash | `CASH_DEPOSIT`, `CASH_WITHDRAWAL`, `CASH_ADJUSTMENT` |
| Other | `FEE`, `TRANSFER_IN`, `TRANSFER_OUT`, `STOCK_SPLIT` |

**Month headings:**
- built from the stored `YYYY-MM-DD` text;
- no time zone, the same as `PortfolioFormDates` and `PortfolioDates`;
- a date that can't be parsed goes to a final "Date not recorded" group and is never guessed ("missing stays missing").

**Copy** goes to `PortfolioDisplay`:
- `TRANSACTIONS_TITLE`, `SEE_ALL_TRANSACTIONS`, empty-filter strings and `SHOW_ALL_FILTER`;
- no raw enum names (labels come from `PortfolioPresentation.transactionLabel`).

## 4. Reusable components (extract and reuse; no new design-system component)

| Need | Reuse |
|---|---|
| Filter pills | `StockPillSelector` (Compose `StockChip.kt:80`, SwiftUI `StockDetailComponents.swift:239`). Equal width while every label fits (`StockPillLayout.fitsEqualWidth`), else a `StockFadingScrollRow`. Six pills will scroll at 360 dp / large text by design. |
| Account switch (more than one account) | The `StockChip` account row used on Insights (`PortfolioInsightsScreen.kt:56`). It calls the existing `presenter.selectAccount`. |
| Transaction row | Move the private `PortfolioTransactionRow` (`PortfolioScreen.kt:509`) to `internal` in a new `PortfolioTransactionViews.kt` with no behaviour change. iOS: extract `transactionRow` (`PortfolioScreen.swift:454`) into a `PortfolioTransactionRow` view. The dashboard and history then share one row (C.1 stacking at large text and the spoken text are kept). |
| Edit / delete | Android: make `PortfolioEditSheet` (`PortfolioScreen.kt:269`) internal and reuse it, plus the same delete `AlertDialog` copy. iOS: the existing `PortfolioEntryScene` sheet and `confirmationDialog`. |
| Status | `StockBanner` SAMPLE / ERROR / WARNING exactly as on the dashboard (`PortfolioScreen.kt:118–130`): sample banner, offline / refresh-failed with Retry, `LinearProgressIndicator` while loading. Signed out: the dashboard's existing sign-in prompt. |
| Sections | Borderless `StockCard(bordered = false)` per month, inset `StockDivider`s, `PortfolioSectionTitle`-style month headings marked as headings. |

## 5. Screen behaviour

1. **Header:** title "Transactions" (top bar), the account label (`PortfolioPresentation.accountLabel`) and the holding symbol when scoped.
2. **Filter pills:**
   - All is the default;
   - the selection survives rotation (`rememberSaveable` / `@State`) and isn't part of the route;
   - changing it announces `spokenFilter` politely.
3. **Rows:**
   - grouped by month, newest first;
   - each row has the existing ⋯ menu (Edit, Delete…);
   - Android uses `LazyColumn` items keyed by `transaction.id`; iOS uses a `LazyVStack` in a `ScrollView`.
4. **Empty states:**
   - no transactions at all: the existing `TRANSACTIONS_EMPTY` plus "Add transaction" (disabled in sample mode, as on the dashboard);
   - filter has no rows: `emptyMessage` plus a "Show all transactions" text button that resets to All.
5. **Sample mode:**
   - the SAMPLE banner stays visible;
   - Edit opens the D1 read-only explanation;
   - Delete: **owner decision D2-Q2** (see §9). The presenter already ignores commands for samples (`PortfolioPresenter.kt:154`).
6. **After edit or delete:** the list updates from the shared presenter state. The filter is kept. If the edited row no longer matches the filter,
   it leaves the list (expected; the summary count updates).

**Dashboard / Holding details (D6):**
- replace the inline "Show all N / Show recent only" toggle (`PortfolioScreen.kt:222`, `PortfolioScreen.swift:232`) with "See all transactions (N)";
- on Holding details the link opens the history scoped to that symbol;
- the preview stays the 5 most recent (`RECENT_TRANSACTIONS`);
- the link appears only when there are more than 5, or always (**D2-Q3**).

## 6. Accessibility and large text

- Pills: `Role.Tab` with selected state (Compose); `.isSelected` trait (SwiftUI); 48 dp / 44 pt targets.
- Month headings use `semantics { heading() }` / `.accessibilityAddTraits(.isHeader)`.
- Rows keep `clearAndSetSemantics(spoken)` and the "Actions for …" menu label.
- iOS: the views read `dynamicTypeSize` in `body` so live Dynamic Type changes refresh them. Captions use `relativeTo: .footnote`, the same curve as
  their values.
- Large text: the row's C.1 stacked groups; pills scroll and never ellipsize; nothing truncates.

## 7. Tests

New `PortfolioPhaseD2Test` (commonTest, runs on Android host and iOS):
1. Every `TransactionType` maps to exactly one filter; All contains every row.
2. `filter` keeps the presenter's order: same-date rows stay ordered by `createdAt`, not re-sorted.
3. The holding scope (symbol) combines with each filter; cash rows (no instrument) are excluded from a holding scope.
4. `groups`: month boundaries, a year boundary (Jan 2026 / Dec 2025), a single month, an unparseable date grouped last.
5. `emptyMessage` / `summary` / `spokenFilter` wording per filter, with and without a symbol; counts are row counts (no amount is produced anywhere in
   the object; asserted on the output strings).
6. No raw enum names in any label (as in `PortfolioPresentationTest`).
7. Inputs aren't mutated: the same list instance contents are unchanged after filtering and grouping.

Existing tests stay green: `PortfolioPhaseD1Test`, `PortfolioPhaseD11Test`, `PortfolioPresentationTest`, `PortfolioPresenterSaveTest`.

Verification run (sequential):
- `:core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test`;
- `:app:androidApp:assembleDebug`;
- default unsigned `xcodebuild`.

Device checks need **owner approval**, on local MOCK only:
- Android 360 dp at 1.0× and 2.0×, light and dark;
- iPhone 16 at Large and AX5.

Captures go in `docs/design/portfolio_d2/`. An iOS save/delete still needs the Auth emulator (separate approval). Without it, it's reported as NOT
VERIFIED.

## 8. Acceptance criteria

1. **Navigation:** "See all transactions" on the dashboard opens Transaction history; on Holding details it opens the history scoped to that holding.
   Back returns to the same scroll position. Five tabs unchanged; no new tab.
2. **Filters:** six pills in the fixed order. Each shows exactly the mapped types (§3) in presenter order, grouped by month. All shows every row.
3. **No new financial values:** no totals, net amounts, returns or computed figures. Row text is identical to the dashboard row for the same
   transaction.
4. **Empty states:** an empty filter shows its message and a one-tap reset to All; an empty account shows the existing empty text.
5. **Edit / delete:** they behave exactly as on the dashboard (same sheet, same confirmation, same presenter calls). The list and counts update; the
   filter is kept.
6. **Sample mode:** the SAMPLE banner is always visible and no sample data can be changed. Behaviour per D2-Q2.
7. **Status:** loading, offline and refresh-failed show the dashboard's banners with Retry. Missing data is never shown as `0.00`.
8. **Large text:** at Android 2.0× and iOS AX5, rows stack per C.1, pills scroll without ellipsis, headings and empty messages wrap between words,
   and nothing is clipped.
9. **Screen readers (structure):** pills expose the selected state, months are headings, rows read their spoken text, and a filter change is
   announced. (A full TalkBack/VoiceOver pass stays in Phase E.)
10. **Tests and builds:** all suites green; `PortfolioPhaseD2Test` covers §7; Android and iOS builds succeed.
11. **Scope:** no change to presenter, engine, API, server, entitlements or the manifest. Only the files in §10 change.

## 9. Owner decisions needed before implementation

| ID | Question | Recommendation |
|---|---|---|
| **Q4** | Approve the Transactions destination (Android route `PortfolioTransactionsRoute(symbol?)`, iOS `navigationDestination`) | Yes |
| D2-Q1 | Filter mapping in §3 (notably Opening position → Buys; Fee → Other; DRIP → Dividends) | As in §3 |
| D2-Q2 | Sample mode: hide Delete in the row menu (today the dialog confirms but the presenter silently ignores it) on dashboard and history alike | Hide Delete for samples; keep Edit (read-only explanation) |
| D2-Q3 | "See all transactions" link: only when there are more than 5 rows, or always | Always when there's at least 1 row (one consistent entry point) |
| D2-Q4 | Include D7 (success confirmation after save: a snackbar on Android, a banner on iOS, copy in `PortfolioDisplay`) in D2 | Separate small phase (D3) to keep D2 focused |

## 10. Files (expected)

- **Shared Kotlin:**
  - new: `PortfolioTransactionsDisplay.kt`, `PortfolioTransactionsScreen.kt`, `PortfolioTransactionViews.kt` (row + edit sheet moved, now internal);
  - edited: `PortfolioRoute.kt`, `PortfolioScene.kt` (new `PortfolioTransactionsScene`), `PortfolioScreen.kt` (preview link; row and sheet
    extracted), `PortfolioDisplay.kt` (copy), `presentation/AppNavigation.kt` (route, back, title).
- **iOS:**
  - new: `PortfolioTransactionsScreen.swift` (scene + screen);
  - edited: `PortfolioScene.swift` (destination), `PortfolioScreen.swift` (link; row extracted).
  - Swift reads `PortfolioTransactionsDisplay` directly from the shared framework (pure object); no new `Ios*Client` is needed.
- **Tests:** new `PortfolioPhaseD2Test.kt`.
- **Docs:**
  - new: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D2.md` (report), captures in `docs/design/portfolio_d2/`;
  - updated: plan, `PROJECT_HANDOFF.md`, `project-status.md`, `CLAUDE.md`.

## 11. Order of work

1. Shared rules (`PortfolioTransactionsDisplay`) and `PortfolioPhaseD2Test`.
2. Extract the row and edit sheet on both platforms, with no visual change (re-check the dashboard).
3. Android route, scene and screen; dashboard and Holding details links.
4. iOS destination and screen; links.
5. Full test and build run.
6. Device checks (after approval).
7. Report, docs and captures.
8. Owner review, then commit on request.
