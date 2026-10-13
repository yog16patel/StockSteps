# Portfolio UI Refinement — Phase D3.1: stabilization and final polish

Status: **implemented 2026-10-13; committed and pushed as "Stabilize Portfolio Phase D3.1 with cash-aware allocation notes, formatted cash lines, Portfolio tab selection on sub-screens, action-specific error titles and action-free samples on Android and iOS".**
- Base: "Add Portfolio Phases D2 and D3 with transaction history filters, transaction details, confirmed-only add/edit/delete feedback, reusable
  transaction rows and sample read-only handling on Android and iOS" (`5c3f15f`, pushed).
- Plan and root-cause analysis: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D3_1_PLAN.md`. The owner approved all of its recommendations on 2026-10-13:

  | Decision | Choice |
  |---|---|
  | Q1 | Additive `errorSource` + session-checked success |
  | Q2 | "-USD 51.50" + "Unfunded" |
  | Q3 | Explanatory note; no cash row |
  | Q4 | Insights and dashboard denominators not aligned in D3.1 |
  | Q5 | Add screen not highlighted |
  | Q6 | Samples: no Edit/Delete |
  | Q7 | Android device checks approved; iOS runtime not approved |

> **The working tree also holds another session's uncommitted backend work.** It is **not** part of D3.1 and must not be staged with it:
> - `server/src/main/kotlin/.../Application.kt`, `server/.../fixture/FixtureMarketDataSource.kt`;
> - `server/src/test/.../market/MockClockFreshnessTest.kt`;
> - `docs/UBUNTU_BACKEND_VERIFICATION_2026-10-13.md`;
> - a stray file named `2`.

## 1. Findings (from the plan, confirmed in code and on device)

- **Allocation 32.62% + 68.43% = 101.05% is not a calculation error.**
  - `PortfolioEngine.allocations` divides by holdings **+ cash**, as documented in `docs/PORTFOLIO.md`; the list shows holdings only.
  - With cash at −USD 51.50 (−CAD 69.53 at 1.35), the hidden cash share is −1.05%.
  - Reproduced exactly:
    - on the real engine in `PortfolioPhaseD31Test` (32.62 / 68.43, currency groups = 100.00%);
    - on the device (`a-light1x-allocation-cash-note`).
  - No stale state, mixed denominators, conversion or rounding problem.
- **Negative cash is a supported ledger state** (documented as unfunded/borrowed). Deposit 1,000.00 − buy (10 × 105 + 1.50) = −51.50; an opening
  position uses no cash. Nothing was clamped or restricted.
- **Not changed (Q4):** Portfolio Insights still excludes borrowed cash from its denominator, so the same holding can show a different % there.

## 2. Changes

| # | Task | Change | Evidence (`docs/design/portfolio_d3_1/`) |
|---|---|---|---|
| 1 | Allocation | Under "By holding", `PortfolioAllocationDisplay.cashNote`: **below zero** → "Cash is below zero (unfunded), so holdings add up to more than 100%."; **above zero** → "Percentages are of account value, which includes cash."; none otherwise. It is decided from the sign of the presenter's cash only and computes nothing. Both platforms. | `a-light1x-allocation-cash-note`, `a-light1x-allocation-positive-cash-note`, `a-light2x-allocation-note-cash` |
| 2 | Cash formatting | `PortfolioPresentation.moneyLine` reformats the presenter's exact `"USD 168.5"` / `"USD -51.5"` with `PortfolioFormat.amount` (exact `Decimal`, half-up): "USD 168.50", "**-USD 51.50**" (spoken "minus USD 51.50"); thousands separators ("USD 1,170.00"). Negative cash shows the caption **"Unfunded"**. Unparseable text stays verbatim. Dashboard "Cash and dividends" on both platforms (the only place these strings appear). | `a-light1x-cash-unfunded`, `a-light2x-allocation-note-cash` |
| 3 | Tab selection | One rule, `PortfolioTabRoutes` / `NavDestination.inPortfolioTab()` in `PortfolioRoute.kt`: Portfolio, Transactions, Transaction details, Holding details, Insights. `AppNavigation` uses it for the Portfolio tab. Add-transaction isn't listed (also opened from other tabs). iOS needed no change: these screens are pushed inside the Portfolio tab. | `a-light1x-history-tab-highlighted`, `a-light1x-holding-details-tab-highlighted`, `a-light1x-insights-tab-highlighted` |
| 4 | Error titles | Core: `PortfolioUiState.errorSource` (`REFRESH`, `SAVE_TRANSACTION`, `DELETE_TRANSACTION`, `ACCOUNT`), set with the failure; background and sync problems = REFRESH; `clearError` resets it. Shared `PortfolioDisplay.errorTitle` / `errorRetries` and one banner per platform (Compose `PortfolioErrorBanner`, SwiftUI `portfolioErrorBanner`) on the dashboard, history and details: "Couldn't save transaction" / "Couldn't delete transaction" / "Couldn't update account" / "Couldn't refresh your portfolio" (offline keeps its title). The server's message is shown unchanged. Save/delete/account errors offer **Dismiss** (Retry only refreshes); refresh keeps Retry. The form uses the same "Couldn't save transaction". | `a-light1x-add-rejected-form`, `a-light1x-dashboard-save-error-dismiss`, `a-light1x-details-delete-rejected` |
| 5 | No success after a cancelled request | `confirm()` reports success only if the repository's signed-in user and environment are unchanged since the command started. A response that lands after a session change returns normally but isn't applied, so it never shows "Transaction added". | core test |
| 6 | Samples | No Edit or Delete: rows have **no ⋯** (tapping still opens details); details show "Sample transactions can't be changed." instead of buttons. Sample permissions are unchanged (the presenter already ignores sample commands). Both platforms. | `a-light1x-sample-rows-no-actions`, `a-light1x-sample-details-cannot-change` |

## 3. Verification

### Build and test

Sequential, test tasks forced with `--rerun`:

| Suite | Result |
|---|---|
| core JVM / core iOS | **474 / 0** each (+1 `PortfolioPresenterSaveTest.errorsNameTheirActionAndSessionChangesNeverReportSuccess`) |
| shared Android host / shared iOS | **217 / 0** / **213 / 0** (+7 `PortfolioPhaseD31Test`; D1–D3 tests green) |
| server | **499 / 0** (3 skipped) |
| `:app:androidApp:assembleDebug` | OK |
| default unsigned `xcodebuild` | **BUILD SUCCEEDED** |

The server suite in the combined run **failed twice for an infrastructure reason**: `EOFException`, then `NoSuchFileException` on
`server/build/test-results/.../in-progress-results-generic.bin`. The parallel backend session was writing the same `server/build` test-results
directory. Run alone, it passed: 499/0. Of the 499, 7 are that session's new uncommitted `MockClockFreshnessTest`. D3.1 changes no server code.

`PortfolioPhaseD31Test` covers:
- negative cash from the ledger;
- the screenshot's allocation on the real engine (holdings > 100% with negative cash, < 100% with positive cash, currency groups = 100%);
- `cashNote` (negative / positive / zero / none / no holdings / mixed currencies);
- `moneyLine` (2 decimals, sign before the currency, thousands separators, half-up 0.125 → 0.13, verbatim for unparseable text);
- `errorTitle` / `errorRetries` per source and offline;
- the Portfolio tab routes.

The presenter test covers:
- the valuation error = REFRESH;
- `clearError` → null;
- a rejected save = SAVE_TRANSACTION;
- a rejected delete = DELETE_TRANSACTION with the server text unchanged and no result;
- the signed-in user changing mid-save → no `actionResult`.

### Device: Android emulator 360 dp, local MOCK only (approved)

Test data: account "D31Test". MOCK keeps it in memory, so it was gone once the server stopped.

| Check | Result |
|---|---|
| Add (opening, deposit, buy, sell) → "Transaction added" | PASS |
| Negative cash: "-USD 51.50 / Unfunded"; allocation 32.62% / 68.43% + the below-zero note | PASS |
| Positive cash: "USD 118.50", later "USD 1,170.00"; the account-value note | PASS |
| Rejected add (sell 100): the form says "Couldn't save transaction" + server text; the dashboard banner has the same title with **Dismiss** | PASS (Dismiss was tapped; its effect, `clearError`, is unit-tested, but the banner's disappearance wasn't captured) |
| Rejected delete (opening position before a later sell): details stay, "Couldn't delete transaction" + server text, Dismiss, no success | PASS |
| Edit from details → "Transaction updated"; back to history → **no repeat** | PASS |
| Delete from a history row → "Transaction deleted" | PASS |
| Tab highlighted on Transactions, Transaction details, Holding details, Insights | PASS |
| Samples: rows without ⋯; details "Sample transactions can't be changed." | PASS |
| Account switch in history (sample Personal USD → TFSA CAD) | PASS |
| 2.0× (note and cash wrap, no clipping); dark 1.0× (app theme set to Dark for the check, then restored to Light) | PASS |

### NOT VERIFIED

- **iOS runtime.** The banners, cash lines, notes and sample actions on iOS are build-only. The iOS tab highlight relies on the existing
  structure.
- **TalkBack, VoiceOver.**
- Android 1.3×; physical devices; iPhone SE (excluded).

## 4. Files

- **Core:**
  - `portfolio/PortfolioPresenter.kt`: `errorSource`, `PortfolioErrorSource`, `command(source)`, session-checked `confirm`;
  - `PortfolioPresenterSaveTest.kt`: +1 test.
- **Shared Kotlin:**
  - `PortfolioDisplay.kt`: titles, `errorTitle`, `errorRetries`, `CASH_UNFUNDED`, `SAMPLE_CANNOT_CHANGE`; `FORM_SAVE_FAILED_TITLE` aligned;
  - `PortfolioPresentation.kt`: `moneyLine`;
  - `PortfolioAllocationDisplay.kt`: `cashNote`;
  - `PortfolioRoute.kt`: `PortfolioTabRoutes`, `inPortfolioTab`;
  - `presentation/AppNavigation.kt`: tab rule;
  - `PortfolioTransactionViews.kt`: `PortfolioErrorBanner`; row ⋯ only with actions;
  - `PortfolioScreen.kt`: cash lines, note, error banner, samples;
  - `PortfolioTransactionsScreen.kt`, `PortfolioTransactionScreen.kt`: error banner, samples;
  - `PortfolioScene.kt`: Dismiss wiring.
- **iOS:**
  - `PortfolioScreen.swift`: cash lines, note, banner, samples;
  - `PortfolioTransactionsScreen.swift`: `portfolioErrorBanner`, row ⋯ only with actions, Dismiss;
  - `PortfolioTransactionDetailsScreen.swift`: banner, sample line;
  - `PortfolioScene.swift`: Dismiss wiring.
- **Tests:** new `PortfolioPhaseD31Test.kt` (7).
- **Docs:** this file, the plan, `PROJECT_HANDOFF.md`, `project-status.md`, `CLAUDE.md`.
- **Captures:** `docs/design/portfolio_d3_1/` (14, "after" only).

## 5. Risk

- **Calculations:** none changed. The engine, ledger, API and server are untouched. Formatting reuses the exact `Decimal` formatter; no floating
  point.
- **Presenter:** the change is additive (a new optional field). The new success check can only suppress a banner, never add one.
- **Sample permissions:** unchanged.

**Status:** committed and pushed at the owner's request, staging only the D3.1 paths above (the backend session's files were left out).
