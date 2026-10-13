# Portfolio UI Refinement — Phase D3: Transaction details and action feedback

Status: **implemented 2026-10-13; committed and pushed together with D2 as "Add Portfolio Phases D2 and D3 with transaction history filters, transaction details, confirmed-only add/edit/delete feedback, reusable transaction rows and sample read-only handling on Android and iOS".**
- Base: Phase D2 (in the same commit) on top of "Close Portfolio Phase D1.1 transaction form polish with all-at-once field validation,
  type-switch error clearing, selected-security line, scoped Android keyboard resize and stacked large-text actions on Android and iOS" (local
  commit, pushed).
- Plan: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D3_PLAN.md`. The owner approved all of its recommendations on 2026-10-13:

  | Decision | Choice |
  |---|---|
  | Q1 | Feedback is a nonblocking banner, not full-screen pages |
  | Q2 | Additive presenter field `actionResult` |
  | Q3 | Details show "Amount before fees" + "Fees" + "Net amount" |
  | Q4 | ⋯ menu kept; the whole row opens details; no chevron |
  | Q5 | "After" screenshots only |
  | Q6 | Android device checks approved; iOS runtime not approved |

> **D2 and D3 share files.** D3 refines files D2 created (`PortfolioTransactionViews.kt`, `PortfolioTransactionsScreen.kt` and its Swift
> counterpart), so D2 can no longer be committed on its own without its D3 edits. They were committed together.

Unchanged:
- the ledger, `PortfolioEngine`, `Decimal`, API, server, fixtures;
- entitlements, sample-data labelling;
- the five tabs, the manifest.

The only core change is the additive presenter result (§2).

## 1. What changed

| # | Area | Change | Evidence (`docs/design/portfolio_d3/`) |
|---|---|---|---|
| 1 | **Transaction details** (new destination) | **Android:** `PortfolioTransactionRoute(transactionId)` (identity only), title "Transaction details", Back. **iOS:** `navigationDestination(item:)` from `PortfolioScene` and `PortfolioTransactionsScene`. Both use the **same** presenter / `PortfolioViewModel` and look the transaction up by id on every update. Content: summary (type, avatar/icon, security, date, recorded amount with currency) → Details (only the fields the type uses and records; from `PortfolioFormRules.visibleFields`) → Notes → Edit / Delete. | `a-light1x-details-buy`, `a-light1x-details-cash-deposit` (no shares/price rows) |
| 2 | Edit / delete from details | Edit is the **existing** sheet (Android `PortfolioEditSheet`, iOS `PortfolioEntryScene`); Delete is the existing confirmation. After a confirmed edit, details re-render and show "Transaction updated". After a confirmed delete, details go back and the screen underneath shows "Transaction deleted". A transaction that disappears for any other reason shows "Transaction not available" with Back. | `a-light1x-details-updated-banner` (server-recalculated USD 1,050.00 / 1,051.50), `a-light1x-deleted-back-on-dashboard` |
| 3 | **Success feedback** | `StockBanner(SUCCESS)` pinned above the screen's content: "Transaction added / updated / deleted". It shows **only** after the presenter confirms (`actionResult`), hides after 4 s or on dismiss, and is announced (Android polite live region; iOS `AccessibilityNotification.Announcement`). A shared `PortfolioFeedbackTracker` (in `AccountDependencies`, next to the presenter) shows each result once across screens; it survives rotation, and a later screen never replays it. | `a-light1x-add-success-banner`, `a-light1x-details-updated-banner`, `a-light1x-deleted-back-on-dashboard` |
| 4 | Save/delete close conditions | The Add screen (both platforms) and the Android edit sheet now close on a confirmed result for **their** transaction. Before: Add used any ledger revision bump; edit used "busy → idle without error", which a cancelled request (account/environment changed) also produces. A rejection keeps the form open with the server's message. | `a-light1x-edit-rejected-no-banner` ("would sell or transfer more shares than owned…", no banner, ledger unchanged) |
| 5 | **Reusable row** (dashboard + history, both platforms) | Avatar (holding logo or ticker; type icon for cash rows) · type with the **recorded** amount right-aligned on its line · security · date · price per share (own lines; at 360 dp "USD 100.00 per share" broke mid-phrase when joined) · ⋯ (separate 48 dp / 44 pt target). The whole row opens details ("Open transaction details"). Large text: no avatar, the amount on its own line, nothing truncated. | `a-light1x-dashboard-rows`, `a-light1x-history-rows-cash-filter`, `a-light2x-dashboard-rows`, `a-dark1x-dashboard-rows` |
| 6 | Recorded amounts only | The row and headline amount is the server-normalized `grossAmount` (shares × price for trades, the entered amount otherwise). Details also show `fees` and the server's `netAmount`. A 0 / unparseable amount is **not recorded** → no amount and no row, never "0.00". Sample fixtures skip normalization, so their trades show only the price per share. | `a-light1x-sample-rows` (the sample deposit now reads USD 1,000.00; in D2 it showed "USD 0.00") |
| 7 | Delete confirmation | Same dialog. Copy follows the reference ("This will permanently remove this transaction from your portfolio." + the recalculation sentence) and names the transaction ("Sell · TD.TO · 2 shares · Oct 10, 2026 · USD 170.00"). | `a-light1x-delete-confirm-summary` |
| 8 | Sample mode | Details: SAMPLE banner, Edit only (read-only explanation), no Delete; rows keep the D2 rule (no Delete). | `a-light1x-sample-details-read-only`, `a-dark1x-details-cash-sample` |
| 9 | Large text | Details value rows stack (label, then value) at 2.0× / AX5. Edit and Delete stack. | `a-light2x-details-top`, `a-light2x-details-rows` |

## 2. Presenter change (approved, Q2)

Added to `core/.../portfolio/PortfolioPresenter.kt`:
- `PortfolioUiState.actionResult: PortfolioActionResult? = null`;
- `PortfolioActionResult(id, kind: ADDED | UPDATED | DELETED, transactionId)`.

The result is set only after `repository.saveTransaction` / `deleteTransaction` returned normally:
- the repository throws on a server rejection, on a validation failure and when the account or environment changes mid-request;
- ignored commands (busy, sample) never run.

Existing `failure` messages and every other field are unchanged. iOS reads the same state through `observePortfolio`.

New `IosAccountClient.takePortfolioFeedback()` / `skipPortfolioFeedback()` give iOS the shared tracker.

## 3. Verification

### Build and test (sequential, test tasks forced with `--rerun`)

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test` | **BUILD SUCCESSFUL** (8 m 16 s) |
| core JVM / iOS | **473 / 0** each (+1 `PortfolioPresenterSaveTest.actionResultsOnlyFollowConfirmedChanges`) |
| server | **492 / 0** (3 skipped, pre-existing; no server change) |
| shared Android host / iOS | **210 / 0** / **206 / 0** (+9 `PortfolioPhaseD3Test` each; D1, D1.1 and D2 tests green) |
| `:app:androidApp:assembleDebug` | OK |
| default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED** |

**One failure along the way, fixed.** The first full run failed the new presenter test **on iOS only** (expected 4 server writes, saw 3). The test
waited on "not busy and an error", and the mock's background valuation error could satisfy that before the rejected request ran. The test now waits
on the request counter. It passed 3× on iOS and 3× on JVM, then in the full run. The presenter code itself didn't change.

`PortfolioPhaseD3Test` covers:
- the Buy details rows and currencies;
- a cash deposit without share or price rows;
- opening-position labels;
- optional fields (settlement, cash currency, FX, notes, unknown account) only when recorded;
- sample fixtures never showing "0.00";
- row text and icons;
- no raw enum names;
- the tracker showing each result once (`take` / `skip`);
- baseline / `isNew`.

The presenter test covers:
- add → result 1;
- a rejected edit → no new result;
- a tap while busy → no request, no result;
- edit → 2, delete → 3;
- sample → ignored;
- leaving the sample → result preserved.

### Device: Android emulator 360 dp, local MOCK only (approved)

Test data: account "D3Test" with 4 transactions. MOCK keeps it in memory, so it was gone once the server stopped.

| Flow | Result |
|---|---|
| Add → back on the dashboard with "Transaction added" (only after the confirmed save) | PASS |
| Dashboard rows: avatar, recorded amount, separate ⋯; tap opens details | PASS |
| Details from the dashboard (Buy) and from history (Cash filter → Cash deposit: no shares/price) | PASS |
| Edit from details (price 100 → 105) → details show the server's USD 1,050.00 / 1,051.50 + "Transaction updated"; Back → **no repeat banner** on the dashboard, scroll kept | PASS |
| Rejected edit (sell 100 shares) → sheet stays open with the server message, **no** success banner, ledger unchanged | PASS |
| Delete from details → confirmation with the summary → back to the dashboard with "Transaction deleted", 3 transactions | PASS |
| Back from details keeps the history filter (Cash) | PASS |
| Holding scope: Holding details → See all ("AAPL transactions only") → details → Back keeps the scope and the account | PASS |
| Sample (Multiple buys / Multiple accounts): no "0.00" amounts; details Edit-only, no Delete | PASS |
| 2.0× font: rows and details stack, nothing clipped | PASS |
| Dark 1.0× (app theme set to Dark for the check, then restored to Light) | PASS |

### NOT VERIFIED

- **iOS runtime.** The iOS details screen, rows, banners and Large/AX5 layouts are build-verified only:
  - the owner hasn't approved iOS runtime checks;
  - the cached simulator session would also need the Firebase Auth emulator.
- **Android:**
  - the keyboard and safe area in the edit sheet weren't re-checked beyond normal use (the sheet and its D1.1 keyboard handling are unchanged);
  - 1.3×;
  - the "Transaction not available" state wasn't triggered on a device (it needs the transaction to vanish while details are open); it is
    covered by code review only.
- **TalkBack, VoiceOver.**
- **The cancelled-mutation path.** No test reproduces an account change mid-request.
- iPhone SE (excluded), physical devices.

## 4. Files

- **Core:**
  - `portfolio/PortfolioPresenter.kt`: `actionResult`, `PortfolioActionKind`, `PortfolioActionResult`, `confirm`;
  - `PortfolioPresenterSaveTest.kt`: +1 test.
- **Shared Kotlin:**
  - new:
    - `PortfolioTransactionDetailsDisplay.kt`: row text, details rows, icon keys, `PortfolioActionFeedback`, `PortfolioFeedbackTracker`;
    - `PortfolioTransactionScreen.kt`: details scene + screen;
  - edited:
    - `PortfolioTransactionViews.kt`: row, avatar, banner, dialog with summary, sheet closes on result, `logoFor`;
    - `PortfolioTransactionsScreen.kt`, `PortfolioScreen.kt`: rows open details, banner above the list;
    - `PortfolioScene.kt`: Add closes on a confirmed result, `onOpenTransaction`;
    - `PortfolioRoute.kt`, `presentation/AppNavigation.kt`: route, Back, title; Add from outside Portfolio skips the banner;
    - `di/AccountDependencies.kt`: `portfolioFeedback`;
    - `iosMain/.../IosAccountClient.kt`: take/skip.
- **iOS:**
  - new `PortfolioTransactionDetailsScreen.swift` (scene + screen);
  - edited:
    - `PortfolioTransactionsScreen.swift`: row, avatar, `portfolioFeedback` modifier, delete text, `logo(for:)`, details push;
    - `PortfolioScreen.swift`: rows, delete text;
    - `PortfolioScene.swift`: details push, feedback, the entry sheet dismisses on a confirmed result.
- **Tests:** new `PortfolioPhaseD3Test.kt` (9).
- **Docs:** this file, the plan, `PORTFOLIO_UI_REFINEMENT_PLAN.md`, `PROJECT_HANDOFF.md`, `project-status.md`, `CLAUDE.md`.
- **Captures:** `docs/design/portfolio_d3/` (16, "after" only).

## 5. Reusable components

| Component | Compose | SwiftUI |
|---|---|---|
| Transaction row | `PortfolioTransactionRow(transaction, logoUrl, onOpen, onEdit, onDelete?)` | `PortfolioTransactionRow(transaction:logoUrl:onOpen:onEdit:onDelete:)` |
| Avatar / type icon | `PortfolioTransactionAvatar` + `transactionIcon` | `PortfolioTransactionAvatar` + `transactionSymbol` |
| Success banner | `PortfolioActionBanner(tracker, result, leaving)` | `.portfolioFeedback(accounts:result:leaving:)` |
| Delete confirmation | `PortfolioDeleteTransactionDialog(transaction, …)` | `deleteConfirmation(_:)` text + `confirmationDialog` |
| Edit sheet | `PortfolioEditSheet` (unchanged API) | `PortfolioEntryScene` (unchanged API) |
| Shared rules | `PortfolioTransactionDetailsDisplay`, `PortfolioActionFeedback`, `PortfolioFeedbackTracker` | same objects |

## 6. Observations (not changed)

- **Banner title for any error.** After a rejected edit, details (like the dashboard) also show the presenter error under the existing "Couldn't
  refresh your portfolio" title (`state.error` shares that banner). This is pre-existing dashboard behaviour; a clearer title is a candidate
  follow-up.
- **Bottom tab highlight.** The Portfolio tab still isn't highlighted on Portfolio sub-destinations (known).
- **Moved code warning.** `rememberModalBottomSheetState` is deprecated (moved code, pre-existing).

**Status:** committed and pushed with D2 at the owner's request. The iOS runtime pass needs approval (simulator launch and,
for saved data, the Auth emulator).
