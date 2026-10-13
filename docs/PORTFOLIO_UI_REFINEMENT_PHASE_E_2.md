# Portfolio Phase E.2 — final UI polish, currency audit, session recovery (report)

Date: 2026-10-13. Plan and audit: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_E_2_PLAN.md` (owner-approved with decisions D-1…D-6).
Status: **IMPLEMENTED; VERIFICATION IN PROGRESS.** Committed at the owner's request before the full suite and the device checks finished. Every
result below is marked as it stands at commit time; anything not run is NOT RUN, never PASS.

## 1. What changed

| ID | Change | Files |
|---|---|---|
| A1 (D-1) | Account sheet title "Create investment account" → **"New investment account"** (shared copy); iOS sheet title **inline** like every other Portfolio sheet (a large title never wraps and truncated). Dynamic Type untouched. Android shows the shorter copy (its dialog title already wrapped). | `PortfolioDisplay.kt`, `PortfolioForms.swift` |
| D-4 | iOS **Account name** keeps a visible label above the input (the placeholder-only label vanished once typed), same pattern as the D1 transaction form; spoken label unchanged. | `PortfolioForms.swift` |
| B1 | **Trade currency** moved from collapsed "More options" to the top of **Amounts** on both platforms (`TRADE_CURRENCY` no longer advanced; `redesignOrder` updated). "More options" hint now "Cash currency, FX rate, …". | `PortfolioFormRules.kt`, `PortfolioDisplay.kt` |
| B2 | Note under Trade currency when it differs from the selected account's reporting currency: *"This account reports in CAD. These amounts are in USD; change Trade currency if you paid in CAD. They're converted to CAD with dated exchange rates."* Display only; nothing is converted, defaulted or rewritten. | `PortfolioFormRules.currencyNote`, `PortfolioDisplay.formCurrencyNote`, `PortfolioForms.kt`, `PortfolioForms.swift` |
| B3 | iOS: changing Trade currency moves Cash currency with it when they were equal (Android behaviour), so no unrequested FX-rate field. | `PortfolioForms.swift` |
| C1 (D-2) | Failure kind kept next to the message: `UserDataErrorKind` SESSION / NETWORK / SERVER (`userDataErrorKind`), in `ServerBackedRepository.State`, `PortfolioSnapshot` and `PortfolioUiState` (`errorKind`, `sessionExpired`). | `UserDataRepositories.kt`, `PortfolioRepository.kt`, `PortfolioPresenter.kt` |
| C1a | A token refresh that only **couldn't connect** is NETWORK, not an ended session: `AccountConnectionException` (subtype of `AccountException`) for the shared E.1 text `AccountMessages.TOKEN_CONNECTION`; both platform mappers return that constant. Messages unchanged. | `AuthRepository.kt`, `DefaultAuthRepository.kt`, `AndroidAuthGateway.kt`, `FirebaseAccountAdapters.swift` |
| C2 | Banner contract in `PortfolioDisplay` (`errorTitle`, `errorMessage`, `errorAction`, `errorActionLabel`), rendered by the shared Compose/SwiftUI banners. | `PortfolioDisplay.kt`, `PortfolioTransactionViews.kt`, `PortfolioTransactionsScreen.swift` |
| C3 | **"Sign in again"** = sign out (Firebase sign-out + this device's cached copy; push-token unlink is best effort; **no server data is deleted**), then Login: Android via the existing sign-out navigation, iOS via the existing sign-in sheet only after the user is gone. Never opens sign-in while the expired user is still signed in. | `PortfolioScene.kt` (`signInAgain`), `PortfolioScene.swift` (`portfolioSignInAgain`), dashboard/history/details screens |
| C4 (D-2b) | Add transaction, account "+ Add" and the empty-state "Create portfolio" are unavailable **only for an expired session**, with "Sign in again to add or change transactions."; ordinary outages keep Add. | `PortfolioScreen.kt`, `PortfolioTransactionsScreen.kt`, `PortfolioScreen.swift` |
| D-6 | Five clarifications added to the allocation proposal (§7): unfunded-cash sum, stale prices, per-account currency, rounding, old clients, labels. Documentation only. | `docs/PORTFOLIO_ALLOCATION_DENOMINATOR_PROPOSAL.md` |

Banner contract (refresh problems; rejected save/delete/account changes keep their D3.1 title and Dismiss):

| Kind | Title | Message | Action |
|---|---|---|---|
| SESSION | Session expired | session message (+ "Showing your last saved portfolio." when cached) | Sign in again |
| SERVER | Couldn't refresh your portfolio | server message (+ "Showing your last saved portfolio." when cached) | Retry |
| NETWORK (or unknown), cached | Offline · last saved portfolio | unchanged | Retry |

**Unchanged:** `PortfolioEngine`, `Decimal`, ledger, validation, FX, server code, API contracts, MOCK fixtures, existing transactions, default trade
currency, allocation, typography/spacing/colours, navigation structure.

## 2. Currency audit outcome (Scope B)

No calculation defect. TD.TO in "E1 iOS · CAD" was recorded in **USD** because the symbol was typed (no metadata lookup) and the USD default sat
under "More options". FX was applied correctly (CAD 506.25 = 5 × 75 USD × 1.35). The fix makes the trade currency visible and explains the mismatch.

**Documented limitations (D-3, D-5; not changed):**
- A **typed** symbol's currency is never looked up; the user must choose it (the note prompts them). The form also stores the trade currency as the
  security's currency for typed symbols; the server falls back to it only when the provider profile has no currency. Changing that contract is a
  separate phase.
- **MOCK fidelity (for the backend session):** the synthetic MOCK profile for any non-catalog symbol, TD.TO included, says `currency = USD`,
  `exchange = NASDAQ` (`server/.../fixture/SampleMarketData.kt` `profile()`), and its price is a random sample price. Not edited in E.2.

## 3. Verification at commit time

| Check | Result |
|---|---|
| Focused core JVM: `PortfolioSessionStateTest` (new, 3), `DefaultAuthRepositoryTest` (6 incl. 1 new), `PortfolioPresenterSaveTest` (3), `PortfolioRepositoryTest` (2) | **PASS** |
| `:core:jvmTest` full, `--rerun` | **PASS 478 / 0** |
| `:core:iosSimulatorArm64Test`, `:app:shared:iosSimulatorArm64Test`, `:server:test`, `:app:androidApp:assembleDebug` (sequential run) | **RUNNING at commit time, results not recorded yet** |
| `:app:shared:testAndroidHostTest` | **PASS 226 / 0** (incl. `PortfolioPhaseE2Test` 7 new, `PortfolioPhaseD1Test` updated); run before the C1a token change (core-only + platform mappers), so it is re-run in the sequential pass |
| Unsigned `xcodebuild` | **BUILD SUCCEEDED** before the C1a one-line Swift change; re-run queued after the suite |
| Device matrix (plan §6 rows 1–9) | **NOT RUN** |
| TalkBack, VoiceOver, Accessibility Inspector | **NOT VERIFIED** (deferred by the owner) |

Before captures (from E.1) are in `docs/design/portfolio_phase_e_2/before-*`; after captures are pending the device checks.

## 4. Remaining

1. Record the sequential suite results and the final `xcodebuild`.
2. Device checks on local MOCK (+ Auth emulator for the session-expired row): plan §6 rows 1–9, with after captures.
3. Known gaps: Edit / Delete on transaction details stay enabled while the session is expired (they fail with the session message and Dismiss);
   `UserApi`'s 401 text "Sign in to use watchlists and alerts." is shown unchanged as the message when the server rejects the token.
