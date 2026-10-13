# Portfolio UI Refinement — Phase D3 plan: Transaction details and action feedback

Status: **approved by the owner (all recommendations, 2026-10-13) and implemented (committed and pushed with D2 as "Add Portfolio Phases D2 and D3 with transaction history filters, transaction details, confirmed-only add/edit/delete feedback, reusable transaction rows and sample read-only handling on Android and iOS"): see `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D3.md`.**
Original status: plan for owner review.
- Base: Phase D2 (Transaction history), implemented in the same working tree, on top of the D1.1 commit. D3 builds on the D2 working tree and
  never stages or rewrites D2 files on its own.
- Visual reference: the owner's D3 sheet (chat, 2026-10-13).

## 0. What the inspection found

| Topic | Finding | Consequence |
|---|---|---|
| Success signal | `PortfolioPresenter.command { }` only toggles `busy` and sets `failure` (`PortfolioPresenter.kt:153`). Today: Add detects success by a higher ledger revision (`PortfolioEntryScene`); the edit sheet infers it from `busy` true→false with no error; **delete has no detection**. | Not reliable: `UserDataRepository.mutate` throws `CancellationException` when the account or environment changes mid-request. `command` rethrows it and ends with `busy = false` and no error, so busy/error inference would show a **false "success"**. A fast command can also go true→false between collections (StateFlow conflation), giving a missed success. → a **small presenter change is needed** (§2, approval requested). |
| Authoritative amounts | The server normalizes every saved transaction (`PortfolioService.normalize`): `grossAmount` = shares × price for priced trades, else the entered amount; `netAmount` = gross ± fees by type. Both are ledger fields. | Rows and details can show **recorded** gross/net amounts; nothing is computed in the UI. Sample fixtures skip normalization (gross/net stay "0"), so a 0 amount on a priced trade is treated as **not recorded** and hidden, never shown as "0.00". |
| Snackbar | Neither platform has a `SnackbarHost` or toast pattern. `StockBanner` has a `SUCCESS` kind with semantic light/dark colours on both platforms. | Reuse `StockBanner(SUCCESS)` as a transient, nonblocking banner (§3) instead of adding a new component. |
| Field applicability | `PortfolioFormRules.visibleFields(type, symbol, cashDiffers)` already decides which fields a type uses. | Details reuse it, so a cash deposit never shows shares or price. |
| Logos | Holding rows use `StockTickerAvatar(symbol, logoUrl)` with `logoUrl` from the holdings display. Transactions carry no logo. | Rows look up the holding's existing `logoUrl` by symbol (display lookup only); otherwise the avatar shows the ticker. |

## 1. Transaction details destination

Navigation, with identity only and no new data loading:
- **Android:** `@Serializable PortfolioTransactionRoute(val transactionId: String)` in `PortfolioRoute.kt`, registered in `AppNavigation` (Back,
  title "Transaction details"). `PortfolioTransactionScene` acquires the **same** `viewModel(key = "portfolio")` presenter and finds the
  transaction in `state.transactions` by id.
- **iOS:** `.navigationDestination(item:)` from `PortfolioScene` (dashboard / Holding details) and from `PortfolioTransactionsScene`. It uses the
  same `PortfolioViewModel` and the same lookup.
- **Back:** the previous screen's account, filter (`rememberSaveable` / `@State`), holding scope and scroll position all survive, as in D2. The
  five tabs are unchanged.

Content comes from a new shared `PortfolioTransactionDetailsDisplay.of(transaction, accounts)`:
- **Summary card:** type label + icon, security (symbol · company name when recorded), date, and a headline amount only when a recorded,
  non-zero gross/net amount exists, with its currency ("USD 1,000.00").
- **Details card:** rows only for fields the type uses (via `visibleFields`) and that are recorded:
  - type;
  - trade/effective date ("Effective date" for opening positions);
  - settlement date;
  - account (label + "Personal · reports in CAD");
  - symbol, company name;
  - shares;
  - price per share or "Average cost per share" (opening position);
  - amount before fees (recorded gross; label per §6 Q3);
  - fees;
  - net amount (§6 Q3);
  - transaction currency;
  - cash currency + FX rate when recorded;
  - notes when present.

  Missing stays missing: an absent optional field has no row, never "0.00".
- **Actions:**
  - Edit opens the **existing** edit sheet (Android `PortfolioEditSheet`, iOS `PortfolioEntryScene`);
  - Delete opens the **existing** confirmation;
  - samples: Edit shows the read-only explanation and Delete is hidden.
- **Disappears or changes:**
  - deleted here → pop back with "Transaction deleted";
  - edited here → stay, re-render from state, show "Transaction updated";
  - gone for any other reason (another device, account switch, sample toggled, edit moved it to another account) → "This transaction is no longer
    in this account." with a Back button. Nothing stale is shown.
- **Layout:** `StockCard(bordered = false)`, `spacing.screen` (16), `cardPadding`, section gap. The value rows use the existing stacked metric
  rule (`StockLayout.metricRowStacked` / AX stacking on iOS), so a label and its value never squeeze each other at 2.0× / AX5.

## 2. Success feedback — needs a small presenter change (approval requested)

Proposed additive contract (core, defaults keep every caller unchanged):

```kotlin
enum class PortfolioActionKind { ADDED, UPDATED, DELETED }
data class PortfolioActionResult(val id: Long, val kind: PortfolioActionKind, val transactionId: String)
// PortfolioUiState: + val actionResult: PortfolioActionResult? = null
// PortfolioPresenter: saveTransaction / deleteTransaction set actionResult (id = previous + 1)
//   only after repository.saveTransaction / deleteTransaction returned normally.
```

- **Never** set on validation errors, server rejections, cancellation (account/environment change) or ignored commands (busy, sample).
- No local ledger and no speculative updates: the ledger still comes only from the repository response.
- Existing `failure` messages are unchanged.

Consumption (shared `PortfolioActionFeedback`):
- `message(kind)` → "Transaction added" / "Transaction updated" / "Transaction deleted".
- `isNew(result, seenId)`: each screen keeps the last id it has handled. Android uses `rememberSaveable`, so nothing replays after rotation; iOS
  uses `@State`. A screen entering composition marks the current id as seen, so old results never replay on navigation.
- Where it shows:

  | Action | Shown on |
  |---|---|
  | Add | dashboard / history after the Add screen closes |
  | Edit from details | details |
  | Edit from a row | that screen |
  | Delete from details | the screen it returns to |
  | Delete from a row | that screen |

- The Add screen and the edit sheet switch their close condition to this result. That also removes the false-success / missed-success risks from
  §0, and it is the one behaviour change to existing flows.

Without this change, the fallback is ledger-revision detection for all three actions. It's acceptable but weaker: a revision bump from another
device during a save would read as success. **Recommended: the presenter change.**

## 3. Feedback presentation (both platforms)

- `StockBanner(kind = SUCCESS, message, onDismiss)` shown at the top of the screen's content.
- Nonblocking; it auto-hides after ~4 s and can be dismissed. It is announced: Android polite live region; iOS
  `AccessibilityNotification.Announcement`. It uses semantic colours (light/dark), and the text wraps at 2.0× / AX5.
- The reference's full-screen "Transaction updated / deleted" pages are not used: the brief asks for concise, nonblocking feedback. See §6 Q1.

## 4. Reusable transaction row (dashboard preview and history)

Shared `PortfolioTransactionRowDisplay.of(transaction, logoUrl)`:
- texts: the existing `transactionText`;
- `iconKey` by type: buy, sell, dividend, cash in, cash out, fee, transfer, split;
- avatar: `StockTickerAvatar` (holding logo or ticker) for security rows; a type icon tile (`StockIconTile`) for cash rows;
- `trailingAmount`: the recorded gross for priced trades, the recorded net for cash rows; null when not recorded or 0 on a priced trade.

Normal text:

```
[avatar] Buy                         USD 1,000.00   [⋯]
         AAPL · 10 shares
         Oct 13, 2026 · USD 100.00 per share
```

- The whole row opens details (`onClickLabel` "Open transaction details").
- ⋯ stays a separate 48 dp / 44 pt target with Edit and Delete (Delete hidden for samples).

Large text (Android `largeFontScale`, iOS accessibility sizes):
- the avatar is hidden (as on Insights);
- type / security / date / price / amount each take their own line and wrap;
- ⋯ stays separately reachable.

Nothing is truncated (no `maxLines` on financial text). One component per platform, used by the dashboard and history:
- Compose: `PortfolioTransactionRow` in `PortfolioTransactionViews.kt`;
- SwiftUI: `PortfolioTransactionRow` in `PortfolioTransactionsScreen.swift`.

## 5. Delete confirmation

Same dialog and flow. Its copy follows the reference: "This will permanently remove this transaction from your portfolio." plus the existing
recalculation sentence, and the transaction summary line (type · security · date) so the user can see what is being deleted. No full-screen page.

## 6. Owner decisions needed

| ID | Question | Recommendation |
|---|---|---|
| **Q1** | Feedback style: transient success banner (brief) or the reference's full-screen success pages? | Banner (§3) |
| **Q2** | Approve the additive presenter field `actionResult` (§2)? | Yes; it's the only reliable signal |
| Q3 | Amount labels in details: "Amount before fees" (gross) + "Fees" + "Net amount" (all recorded), instead of the reference's single "Total amount" | Yes: "Total amount" for a buy hides that fees are extra |
| Q4 | Row: keep ⋯ (Edit/Delete) and make the row tappable, with no chevron (the reference has a chevron and no menu) | Keep ⋯ (the brief requires an independent action menu); the row tap opens details |
| Q5 | Screenshots: "after" only, as for D2? | Yes, unless "before" is wanted |
| Q6 | Runtime checks: Android emulator on local MOCK (running). iOS needs the simulator launch allowed and, for saved data, the Firebase Auth emulator | Approve Android now; decide on iOS separately |

## 7. Tests

New `PortfolioPhaseD3Test` (commonTest, Android host + iOS):
- details rows per type: a cash deposit has no shares or price; an opening position uses "Effective date" / "Average cost per share";
- optional fields absent → no row;
- 0 gross on a priced trade → no headline amount;
- currency always shown;
- the account label;
- row `trailingAmount` / icon per type;
- sample (fixture) rows never show "0.00";
- `PortfolioActionFeedback`: messages, `isNew` dedupe (same id, older id, baseline);
- no raw enum names.

Core (if Q2 is approved), `PortfolioPresenterSaveTest` additions:
- the result is set once per successful save, edit and delete, with increasing ids;
- **not** set on a rejected save, a validation failure, a cancelled mutation, a busy command or a sample.

Existing D1, D1.1 and D2 tests stay green.

Run sequentially:
- `:core:jvmTest`, `:core:iosSimulatorArm64Test`;
- `:server:test` (part of the documented suite; no server change);
- `:app:shared:testAndroidHostTest`, `:app:shared:iosSimulatorArm64Test`;
- `assembleDebug`, default unsigned `xcodebuild`.

Device checks (after approval, local MOCK only):
- open details from the dashboard and from history;
- edit → updated details + banner;
- delete → back to history + banner;
- a rejected save keeps the server message with no banner;
- sample read-only;
- account switch / holding scope;
- missing optional values;
- Android 1.0× / 2.0×, light and dark;
- keyboard and safe area in the edit sheet.

iOS Large/AX5 only if Q6 allows; otherwise reported as NOT VERIFIED.

## 8. Files (expected)

- **Core:** `portfolio/PortfolioPresenter.kt` (+ `actionResult`; only with Q2); presenter tests.
- **Shared Kotlin:**
  - new: `PortfolioTransactionDetailsDisplay.kt`, `PortfolioActionFeedback.kt` (+ row display in `PortfolioTransactionsDisplay.kt`),
    `PortfolioTransactionScreen.kt` (details scene + screen);
  - edited: `PortfolioTransactionViews.kt` (row, dialog copy, sheet close on result), `PortfolioTransactionsScreen.kt`, `PortfolioScreen.kt`,
    `PortfolioScene.kt` (Add success), `PortfolioRoute.kt`, `presentation/AppNavigation.kt`, `PortfolioDisplay.kt` (copy).
- **iOS:**
  - new: `PortfolioTransactionDetailsScreen.swift`;
  - edited: `PortfolioTransactionsScreen.swift` (row), `PortfolioScreen.swift`, `PortfolioScene.swift`, `PortfolioForms.swift` (close on result).
- **Tests:** `PortfolioPhaseD3Test.kt`.
- **Docs:** `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D3.md`, the plan, `PROJECT_HANDOFF.md`, `project-status.md`, `CLAUDE.md`.
- **Captures:** `docs/design/portfolio_d3/`.

## 9. Order of work

1. Presenter result + core tests (if Q2).
2. Shared display rules + D3 tests.
3. Row (both platforms).
4. Details destination (Android, then iOS).
5. Feedback wiring on Add, Edit and Delete.
6. Full test and build run.
7. Device checks.
8. Report, handoff and captures.
9. Stop for review. No commit.
