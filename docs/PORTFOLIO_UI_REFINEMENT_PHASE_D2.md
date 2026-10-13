# Portfolio UI Refinement — Phase D2: Transaction history

Status: **implemented 2026-10-13; committed and pushed together with D3 as "Add Portfolio Phases D2 and D3 with transaction history filters, transaction details, confirmed-only add/edit/delete feedback, reusable transaction rows and sample read-only handling on Android and iOS".**
- Base: "Close Portfolio Phase D1.1 transaction form polish with all-at-once field validation, type-switch error clearing, selected-security line,
  scoped Android keyboard resize and stacked large-text actions on Android and iOS" (pushed).
- Specification: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D2_PLAN.md`, with the owner decisions below.
- Visual reference: the owner's D2 reference sheet (shared in chat, 2026-10-13). Differences from it are listed in §6.

Owner decisions (2026-10-13):
1. **Q4 approved:** a Transaction history destination inside Portfolio, with no new bottom tab.
2. **Six filters approved.** Opening position must not look like a normal purchase, so it goes under **Other**. Other explains what it contains.
3. **Sample mode hides Delete** on the dashboard and in history. Edit keeps the read-only explanation.
4. **"See all transactions" always shows** when at least one transaction exists.
5. **Save-success confirmation is out of scope** (Phase D3).

Unchanged:
- `PortfolioPresenter` / `PortfolioUiState`;
- the ledger, `PortfolioEngine`, `Decimal`, API, server, fixtures;
- entitlements, sample-data labelling;
- the five tabs, the manifest.

## 1. What changed

| # | Area | Before | After | Evidence (`docs/design/portfolio_d2/`) |
|---|---|---|---|---|
| 1 | Navigation | No history screen. The dashboard toggled "Show all N / Show recent only" inline, growing the page without limit. | Android: `PortfolioTransactionsRoute(accountId?, symbol?)` (identity only), registered in `AppNavigation` with Back and the "Transactions" title. iOS: `navigationDestination` from `PortfolioScene`, the same pattern as Insights. No tab added. | `a-light1x-dashboard-see-all-after`, `a-light1x-history-all` |
| 2 | Dashboard / Holding details preview | Inline toggle | The 5 most recent rows, then **"See all transactions (N)"** whenever N ≥ 1. On Holding details the link opens the history scoped to that holding. | `a-light1x-dashboard-see-all-after`, `a-light2x-dashboard-see-all` |
| 3 | Shared rules | — | New `PortfolioTransactionsDisplay` (commonMain, used directly by Compose and SwiftUI): filters, exhaustive type → filter map, notes, holding scope (`activeSymbol`), month groups, copy. **Display only:** presenter order kept, nothing summed, counts are row counts. | `PortfolioPhaseD2Test` |
| 4 | Filters | — | All · Buys · Sells · Dividends · Cash · Other on the shared `StockPillSelector` (equal width when the labels fit, otherwise a fading scroll row). **Buys = Buy only.** Opening position, Fee, Transfer in/out and Stock split go under Other, which shows "Opening positions, fees, transfers and stock splits." Dividends (including reinvestments) and Cash have notes too. | `a-light1x-history-buys` (2 buys, no opening position), `a-light1x-history-other-opening-position` |
| 5 | Grouping | — | Month headings ("October 2026") come from the stored `YYYY-MM-DD` text (no time zone), newest first. An invalid date goes to a final "Date not recorded" group and is never guessed. Headings are accessibility headings. | `a-light1x-history-all` |
| 6 | Count and empty states | — | "5 transactions" / "2 buys" (announced on filter change). An empty filter shows "No dividends in this account yet." with "Show all transactions". An empty account shows the existing empty text, plus Add when the account isn't read-only. | `a-light1x-history-empty-filter-after-delete` |
| 7 | Reuse | Row, edit sheet and delete dialog were private to `PortfolioScreen` | Moved unchanged to `PortfolioTransactionViews.kt` (Compose) and `PortfolioTransactionsScreen.swift` (SwiftUI `PortfolioTransactionRow`). Both screens use the same row, edit sheet, delete confirmation and presenter calls. The account selector is shared too: Compose `PortfolioAccountSelector` (now internal, "+ Add" optional); SwiftUI `PortfolioAccountMenu`. | `a-light1x-history-row-menu`, `a-light1x-history-edit-sheet`, `a-light1x-history-delete-confirm` |
| 8 | Sample mode | Delete offered on sample rows; the dialog confirmed, but the presenter silently ignored it | `onDelete` is null when `PortfolioFormRules.readOnly(state)`: no Delete on the dashboard or in history. Edit still opens the read-only explanation. The SAMPLE banner shows on the history screen. | `a-light1x-sample-delete-hidden-after`, `a-light1x-sample-edit-read-only`, `a-light1x-history-holding-scope-sample` |
| 9 | Holding scope and account switching | — | The scope ("AAPL transactions only" + "Show all holdings") applies only while the account it was opened for is selected. Switching account drops it for good; switching back doesn't revive it. iOS captures the scope's account on tap, because destination builders re-run on parent updates. | `a-light1x-history-holding-scope-sample` → `a-light1x-history-account-switch-drops-scope` |
| 10 | **Bug fix (pre-existing since D1)** | Android: dismissing the edit sheet on a **sample** row left an invisible full-screen scrim ("Close sheet"), so the screen stopped responding. Sample states report `busy = true` (`PortfolioPresenter.kt:129`), and the sheet closed only when `!busy`. | A read-only sheet always closes (`readOnly \|\| !busy`). Normal sheets still stay open while saving. Verified: after dismissing, no "Close sheet" node remains and the page scrolls. | uiautomator dump before/after (in §2) |

## 2. Verification

### Device: Android emulator 360 dp, local MOCK only (approved)

| Check | Result |
|---|---|
| Dashboard preview (5) + "See all transactions (6)" → history; Back | PASS. Back restores the dashboard's exact scroll position (the `LazyColumn` state is saved by the navigation back stack): `a-light1x-dashboard-scroll-kept-after-back` |
| All / Buys / Other filters; month groups in presenter order | PASS |
| Edit from history (opening position 5 → 6 shares), list updates, filter kept | PASS (`a-light1x-history-after-edit`) |
| Delete from history (dividend) → empty-filter message → "Show all transactions" resets | PASS; the dashboard afterwards shows 5 rows and "See all transactions (5)" |
| Sample mode: Delete hidden, Edit read-only, banner shown | PASS |
| Holding scope from Holding details; account switch drops it; switching back keeps it dropped | PASS (sample "Multiple accounts") |
| 2.0× font: rows stack per C.1, pills scroll without ellipsis, the Other note wraps between words, nothing clipped | PASS (`a-light2x-*`) |
| Dark theme 1.0× | PASS (`a-dark1x-*`; the app theme was set to Dark for the check and restored to Light) |
| Semantics (uiautomator, not TalkBack) | Rows read their spoken text; ⋯ reads "Actions for Buy, AAPL · 10 shares"; the account selector reads "Account: D2Test · CAD"; the count reads "All: 5 transactions" (polite live region); the pill row is labelled "Transaction type" |

Test data: a MOCK account "D2Test" with 6 transactions was created in the app. MOCK keeps user data in memory, so it was gone once the server stopped.

### Build and test (sequential, test tasks forced with `--rerun`)

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test` | **BUILD SUCCESSFUL** (8 m 28 s) |
| core JVM / iOS | **472 / 0** each |
| server | **492 / 0** (3 skipped, pre-existing) |
| shared Android host / iOS | **201 / 0** / **197 / 0** (+11 `PortfolioPhaseD2Test` each) |
| `:app:androidApp:assembleDebug` | OK |
| default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED** |

`PortfolioPhaseD2Test` covers:
- every type → exactly one filter;
- Opening position is not a buy;
- presenter order kept, including same-day rows;
- holding scope with every filter, and cash rows excluded from it;
- scope dropped on account switch;
- months across a year boundary, a single month, empty lists;
- invalid dates never guessed;
- copy: counts only, no amounts, no raw enum names;
- inputs never mutated, row text identical to the dashboard;
- sample read-only.

### NOT VERIFIED

- **iOS on a device or simulator.** iOS is build-only:
  - launching the MOCK build on the iPhone 16 simulator was blocked by this session's permission classifier;
  - the cached simulator session also needs the Firebase Auth emulator to reach MOCK data, and that wasn't approved;
  - so the iOS history screen, iOS edit and delete from history, and the iOS Large/AX5 layouts weren't seen running.
- **TalkBack and VoiceOver** (screen-reader passes stay in Phase E).
- Android 1.3×; iPhone SE (excluded by the owner); physical devices.

## 3. Accessibility

- **Pills:** the shared selector (`Role.Tab`, selectable, 48 dp targets; iOS 44 pt). The pill row is labelled "Transaction type".
- **Month headings:** `heading()` / `.isHeader`.
- **Count:** announced on change: Android live region; iOS `AccessibilityNotification.Announcement`.
- **Rows:** unchanged C.1 spoken text and action labels.
- **Holding scope and account selector:** the scope line and "Show all holdings" are stacked, so neither squeezes the other at large text. The
  account selector keeps its spoken label.
- **iOS:** the month groups use `.id(typeSize)` for live Dynamic Type changes. Captions use `relativeTo: .footnote`.

## 4. Files

- **Shared Kotlin:**
  - new: `PortfolioTransactionsDisplay.kt`, `PortfolioTransactionsScreen.kt` (scene + screen), `PortfolioTransactionViews.kt` (row, delete dialog,
    edit sheet moved from `PortfolioScreen.kt`; the sheet fix);
  - edited: `PortfolioScreen.kt` (preview + link, Delete hidden for samples, shared dialog, `PortfolioAccountSelector` internal with optional
    "+ Add"), `PortfolioScene.kt` (`onTransactions`), `PortfolioRoute.kt` (`PortfolioTransactionsRoute`), `presentation/AppNavigation.kt` (route,
    Back, title).
- **iOS:**
  - new: `PortfolioTransactionsScreen.swift` (`PortfolioTransactionRow`, `PortfolioAccountMenu`, `PortfolioTransactionsScene`,
    `PortfolioTransactionsScreen`);
  - edited: `PortfolioScreen.swift` (shared row and menu, link, samples), `PortfolioScene.swift` (destination; scope account captured on tap).
- **Tests:** new `PortfolioPhaseD2Test.kt` (11).
- **Docs:** this file, the D2 plan (status), `PORTFOLIO_UI_REFINEMENT_PLAN.md`, `PROJECT_HANDOFF.md`, `project-status.md`, `CLAUDE.md`.
- **Captures:** `docs/design/portfolio_d2/` (19 "after" captures; the "before" captures were removed at the owner's request).

## 5. Observations outside D2 (not changed)

- **Sample fixture cash deposit.** In the "Multiple buys" sample (`PortfolioFixtures`), the cash deposit row reads "USD 0.00": the fixture leaves
  `netAmount` at 0 (seen on device; no capture kept). Real ledger rows show their amount ("USD 1,000.00"). This is fixture data and was not
  changed.
- **Bottom tab highlight.** The Portfolio tab still isn't highlighted on Portfolio sub-destinations (known since C.1); now that includes Transactions.
- **Moved code warnings.** `rememberModalBottomSheetState` (moved code) and an iOS Insights warning are pre-existing deprecation warnings.

## 6. Differences from the reference sheet (for owner review)

The reference shows more than the approved plan. These parts were **not built**, because each needs a new destination or a change to the row design
that the plan didn't cover:

1. **A Transaction details screen** (tap a row → full details, with Edit/Delete buttons). This would need a new route; candidate for Phase D3.
2. **A full-screen delete confirmation with a row preview.** The existing dialog is reused, as the plan specified.
3. **Rows with a logo/type icon, right-aligned amount and chevron.** The C.1 row is kept so the dashboard and history stay identical and keep the
   verified large-text stacking.
4. **"See all" in the section header.** The link sits under the preview, where it has a full 48 dp row and wraps at 2.0×.
5. **Outlined, unselected filter chips.** The shared pill selector is used (filled when selected, plain label otherwise), the same as Insights.

Adopted from the reference:
- the account selector at the top of history;
- the filter row;
- the row count;
- month headings;
- borderless month cards;
- Edit in a full-height sheet.

**Recommendation:** ready for owner review. Commit only on request. iOS runtime checks need the simulator launch allowed and, for saved data, the
Auth emulator. D3 (save confirmation; optionally transaction details) follows on approval.
