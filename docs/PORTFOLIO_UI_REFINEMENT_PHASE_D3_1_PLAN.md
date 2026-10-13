# Portfolio UI Refinement — Phase D3.1 plan: stabilization and final polish

Status: **approved by the owner (all recommendations, 2026-10-13) and implemented (committed and pushed as "Stabilize Portfolio Phase D3.1 with cash-aware allocation notes, formatted cash lines, Portfolio tab selection on sub-screens, action-specific error titles and action-free samples on Android and iOS"): see `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D3_1.md`.**
Original status: plan for owner review.
- Correction to the brief: D2 and D3 are **not** uncommitted. They were committed and pushed as "Add Portfolio Phases D2 and D3 with transaction
  history filters, transaction details, confirmed-only add/edit/delete feedback, reusable transaction rows and sample read-only handling on Android
  and iOS" (`5c3f15f`). The working tree is clean, and D3.1 starts from that commit.

## 1. Root-cause analysis

### Task 1 — Allocation 32.62% + 68.43% = 101.05%: not a bug; the cash slice isn't shown

The values come from the server: `PortfolioEngine.allocations` → market report → `PortfolioPresenter.allocations` → `PortfolioAllocationDisplay` →
UI. The UI and presenter don't compute anything.

`PortfolioEngine.allocations` (`core/.../PortfolioEngine.kt:184`) divides by **holdings + every currency's cash**. This is documented: "Allocation
includes cash … Borrowed cash can yield >100% exposure" (`docs/PORTFOLIO.md`; About text `ABOUT_ALLOCATION`). The "By holding" list shows **only the
holdings**, so the cash share is missing from the visible list.

Reconciliation of the screenshot (D3Test account):
- Rate: USD→CAD is 1.35, from TD.TO's USD 1,604.80 = CAD 2,166.48 on the same screen.
- Values:

  | Item | USD | CAD |
  |---|---|---|
  | TD.TO | 1,604.80 | 2,166.48 |
  | AAPL | 3,366.70 | 4,545.045 |
  | Cash | −51.50 | −69.525 |
  | **Denominator** | | **6,642.00** (the "Total value" CAD 6,642.0… shown on the same dashboard) |

- Percentages:
  - TD.TO 2,166.48 / 6,642.00 = 32.618% → **32.62%**;
  - AAPL 4,545.045 / 6,642.00 = 68.428% → **68.43%**;
  - cash −69.525 / 6,642.00 = **−1.05%**, not listed.
- Sum: 32.62 + 68.43 − 1.05 = 100.00.

**Ruled out:**
- *Stale state:* the presenter accepts a report only when its account id and ledger revision match (`PortfolioPresenter.kt:101`).
- *Inconsistent denominators:* one total is used for every slice.
- *Currency conversion:* the rate is applied consistently to holdings and cash.
- *Arithmetic or rounding:* the exact decimal and half-up rounding reproduce the screenshot.

**Real problem (presentation):**
- A beginner sees holdings adding up to over 100% with no explanation on screen.
- With **positive** cash the same gap exists the other way (holdings add up to less than 100%).

**Related inconsistency, for owner awareness (not changed):** Portfolio *Insights* (`PortfolioAnalyticsEngine.kt:345–376`) **excludes** borrowed
cash from its denominator and notes it separately. The dashboard **includes** it. The same holding can therefore show different percentages on the
two screens. Aligning them is a financial-convention change → owner decision (§5 Q4).

### Task 2 — Negative cash `USD -51.5`: intentional, supported ledger state

- **Documented** in `docs/PORTFOLIO.md`: "Negative cash is allowed and explicitly labelled as unfunded/borrowed cash; no margin lending or brokerage
  execution is implied." `PortfolioEngine.validate` and the server's `PortfolioService.normalize` have no cash-balance rule. Only Practice
  (`PracticeEngine.kt:130`) requires non-negative cash, and that's a separate simulated ledger.
- **The D3Test arithmetic checks out:**
  - deposit +1,000.00;
  - the opening position doesn't use cash (it records holdings that existed before tracking);
  - Buy AAPL 10 × 105 + 1.50 fees = −1,051.50;
  - → cash **−51.50**. (Before the edit to 105 it was 1,000 − 1,001.50 + 170.00 from the sell = 168.50, the "USD 168.5" in the screenshot.)
- **Real problems (presentation):**
  - the value isn't formatted: the presenter builds `"USD -51.5"` from the raw decimal (`PortfolioPresenter.kt:231`);
  - nothing next to the number says what a negative cash balance means. Only the ⓘ About dialog does.
- No restriction is added and nothing is clamped (as instructed).

### Task 3 — Bottom tab not highlighted on Portfolio sub-screens

- **Android:** `AppNavigation.kt:218` selects Portfolio only for `PortfolioRoute`. Transactions, Transaction details, Holding details and Insights
  match no tab, so none is highlighted.
- **iOS:** these screens are pushed inside the Portfolio tab (`AppScene.swift:106–113`), so the `TabView` selection stays on Portfolio by
  construction. Not runtime-verified.

### Task 4 — Wrong error title

`PortfolioUiState.error` is one string. The presenter's `command { }` writes the save, delete, account and refresh failures into the same
`failure`, and every screen shows it under "Couldn't refresh your portfolio" with a **Retry** that runs a refresh. So a rejected save is mislabelled,
and its Retry doesn't retry the save. Success is already never shown after a rejection: `actionResult` is set only after the repository returned.

**Edge found while tracing:** if the signed-in user changes *during* a request, `UserDataRepository.mutate` returns normally without applying the
response (`apply` silently returns). `confirm()` would then report success to the new session. This is a low-probability false positive; the fix is
part of the presenter change in §2.4.

### Task 5 — Money formatting

| Item | Before | Note |
|---|---|---|
| Dashboard "Cash and dividends" (the only place these strings appear, both platforms) | "USD 168.5", "USD -51.5" | Raw decimal from the presenter |
| Dividends | "USD 12.50" | `display()`: two decimals but no thousands separator ("USD 1250.00") |
| Transaction rows / details | already correct | `PortfolioFormat.amount` via `money()` |

The existing signed-money convention (`PortfolioPresentation.signedMoney`) puts the sign before the currency with an ASCII hyphen-minus: "-USD 51.50".

### Task 6 — Sample "Edit"

On a sample, Edit opens a sheet with only the read-only explanation (lock + "Transactions are read-only…"). **No fields are shown**, so a label like
"View transaction fields" would promise something the sheet doesn't do. Transaction details already show every recorded field and the SAMPLE
banner, so the Edit action adds a tap without adding information.

## 2. Proposed fixes

### 2.1 Allocation explanation (display only; no calculation change)

New shared rule `PortfolioAllocationDisplay.cashNote(state)`:
- **cash below zero:** "Cash is below zero (unfunded), so holdings add up to more than 100%.";
- **cash above zero:** "Percentages are of account value, which includes cash.";
- **no cash:** no note (unknown/missing values: none).

It is decided from the sign of the presenter's existing cash values only, shown under "By holding" on both platforms, and nothing is computed.

Optional, **needs approval (Q3)**: a real "Cash" row with its percentage. The engine doesn't return a cash percentage, so this would mean adding
`cash` entries to `PortfolioAllocations` and the market-report model: an additive API/model change in core and server. Not done unless approved.

### 2.2 Cash and dividend formatting (display only)

New shared `PortfolioPresentation.moneyLine(text)` reformats the presenter's `"CODE value"` strings with `PortfolioFormat.amount` and the existing
sign convention:
- "USD 168.5" → "USD 168.50";
- "USD -51.5" → **"-USD 51.50"**, spoken "minus USD 51.50";
- unparseable text stays verbatim (never invented).

A negative cash line gets a short caption, **"Unfunded"** (wording per Q2). No presenter change: the ledger values stay exact decimals, and only
the display string changes.

### 2.3 Centralized tab selection (Android)

`PortfolioRoute.kt` gains one shared rule: `val PortfolioTabRoutes = setOf(PortfolioRoute, PortfolioTransactionsRoute, PortfolioTransactionRoute,
HoldingDetailsRoute, PortfolioInsightsRoute)` (route classes) and `NavDestination.inPortfolioTab()`. `AppNavigation` uses it for the Portfolio tab.

The Add-transaction screen is **excluded**: it is also opened from Company details on other tabs (Q5). iOS needs no change.

### 2.4 Contextual error titles (additive presenter field — needs approval, Q1)

Core:
- `enum class PortfolioErrorSource { REFRESH, SAVE_TRANSACTION, DELETE_TRANSACTION, ACCOUNT }` and `PortfolioUiState.errorSource: PortfolioErrorSource? = null`;
- set together with `failure` in `command { }`;
- sync and background errors (`snapshot.error`) count as REFRESH;
- `clearError()` clears both;
- while in the presenter: `confirm()` runs only if the repository's uid/environment are unchanged since the command started (closes the edge in
  Task 4).

Shared `PortfolioDisplay.errorTitle(state)`:

| Source | Title |
|---|---|
| Save | "Couldn't save transaction" |
| Delete | "Couldn't delete transaction" |
| Account | "Couldn't update account" |
| Refresh / unknown | "Couldn't refresh your portfolio" |
| Offline | existing offline title |

- The server's message text is shown unchanged.
- Save/delete failures get **Dismiss** (`clearError`) instead of Retry (Retry would run a refresh). Refresh keeps Retry.
- The form's own title `FORM_SAVE_FAILED_TITLE` becomes "Couldn't save transaction" for the same wording.

Used on the dashboard, history and details (Android + iOS).

### 2.5 Sample actions (changes the D2 decision — needs approval, Q6)

Recommended:
- for samples, **no Edit or Delete anywhere**: the row's ⋯ is hidden (it would be empty); tapping the row still opens details;
- details replace the buttons with one line: "Sample transactions can't be changed."

Permissions are unchanged (the presenter already ignores sample commands).

Alternative if the owner prefers to keep Edit: label it **"Why can't I edit?"**, which matches what the sheet shows. Not "View transaction fields".

## 3. Risk assessment (financial calculations)

| Change | Calculation risk |
|---|---|
| 2.1, 2.2, 2.3, 2.5 | **None.** Display or navigation only; no ledger, engine, presenter or API change. Formatting reuses `PortfolioFormat.amount` (exact `Decimal`, half-up; no floating point). |
| 2.4 | **Low, additive.** A new optional field and a stricter success check. No calculation, ledger or API change. Old clients/defaults behave as before. The success check can only *suppress* a banner (on a session change), never add one. |
| Q3 (cash row) | Medium: touches the engine's allocation output and the server report model. It needs server tests and a MOCK restart. Not part of the default plan. |
| Q4 (align Insights and dashboard denominators) | High: changes a documented financial convention. Not proposed for D3.1. |

## 4. Test plan

New `PortfolioPhaseD31Test` (commonTest, Android host + iOS):
- **allocation regression on the real engine:**
  - `PortfolioEngine.allocations` with holdings + negative cash → holding percentages add up to >100%, and holdings + currency groups reconcile to
    100% within rounding;
  - the screenshot case reproduced (1.35 rate → 32.62 / 68.43);
  - positive cash → holdings add up to <100%;
- `cashNote`: negative / positive / none / missing;
- `moneyLine`: "168.5" → "168.50"; "-51.5" → "-USD 51.50" plus spoken "minus"; thousands separators; unparseable text kept verbatim; no Double
  anywhere (string/Decimal only);
- tab rule: every Portfolio sub-route maps to the Portfolio tab; the Add screen and other tabs' routes don't;
- `errorTitle`: per source, offline, null;
- sample actions: no Edit/Delete for read-only states (if Q6 is approved).

Core `PortfolioPresenterSaveTest` additions (if Q1 is approved):
- a rejected save → `errorSource = SAVE_TRANSACTION`, no result;
- a rejected delete → DELETE_TRANSACTION;
- a refresh failure → REFRESH;
- `clearError` resets it;
- a session change during a save → no `actionResult`.

Full run (sequential, `--rerun`):
- `:core:jvmTest`, `:core:iosSimulatorArm64Test`, `:server:test`;
- `:app:shared:testAndroidHostTest`, `:app:shared:iosSimulatorArm64Test`;
- `assembleDebug`, unsigned `xcodebuild`.

Device regression (Android emulator 360 dp, local MOCK; **needs approval**, Q7):
- add / edit / delete / rejected save (title "Couldn't save transaction", Dismiss);
- rejected delete;
- history filters; account switch; holding history;
- samples;
- banner shown once;
- tab highlight on the four screens;
- the cash line and allocation note with negative and positive cash;
- 1.0× / 2.0×; light / dark; Back scroll restoration.

iOS runtime, TalkBack and VoiceOver will be reported as NOT VERIFIED unless approved and performed.

## 5. Decisions needed

| ID | Question | Recommendation |
|---|---|---|
| **Q1** | Approve the additive presenter field `errorSource` + the session-unchanged success check (§2.4)? | Yes: it's the only reliable way to title errors by action |
| **Q2** | Negative cash display: "-USD 51.50" (the existing hyphen-minus convention) with caption "Unfunded"? Or U+2212 "−USD 51.50"? | Existing "-" convention; caption "Unfunded" |
| Q3 | Add a real "Cash" allocation row (engine + server report, additive API change)? | Not now: use the explanatory note (§2.1) |
| Q4 | Align Insights (borrowed cash excluded) and dashboard (included) denominators? | Not in D3.1; separate financial-convention decision |
| Q5 | Highlight Portfolio on the Add-transaction screen too? | No (it's also opened from other tabs) |
| **Q6** | Samples: remove Edit (hide ⋯; details say "Sample transactions can't be changed.")? This revises the D2 decision "keep Edit's read-only explanation" | Yes |
| Q7 | Run Android device regression on the local MOCK (the emulator is already running)? iOS runtime separately? | Android yes; iOS when you choose |

## 6. Files (expected)

- **Core** (Q1 only): `portfolio/PortfolioPresenter.kt`, `PortfolioPresenterSaveTest.kt`.
- **Shared Kotlin:** `PortfolioAllocationDisplay.kt` (note), `PortfolioPresentation.kt` (`moneyLine`), `PortfolioDisplay.kt` (titles, copy),
  `PortfolioRoute.kt` (tab routes), `presentation/AppNavigation.kt` (tab selection), `PortfolioScreen.kt` (cash lines, note, error banner),
  `PortfolioTransactionsScreen.kt`, `PortfolioTransactionScreen.kt`, `PortfolioTransactionViews.kt` (error banner, sample actions).
- **iOS:** `PortfolioScreen.swift`, `PortfolioTransactionsScreen.swift`, `PortfolioTransactionDetailsScreen.swift` (same).
- **Tests:** `PortfolioPhaseD31Test.kt`.
- **Docs:** `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D3_1.md`, `PROJECT_HANDOFF.md`, `project-status.md`, `CLAUDE.md`, plan.
