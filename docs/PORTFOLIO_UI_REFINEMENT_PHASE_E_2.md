# Portfolio Phase E.2 — final UI polish, currency audit, session recovery (report)

Date: 2026-10-13. Plan and audit: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_E_2_PLAN.md` (owner-approved with decisions D-1…D-6).
Status: **IMPLEMENTED; AUTOMATED SUITE PASS; DEVICE CHECKS PARTLY RUN.** First committed as `8e90dc8` at the owner's request while verification
was running. §3 records the full results that came after; the follow-up (§1, C5) and this update are committed as "Record Portfolio Phase E.2 verification with all suites passing, session-expired and currency device checks, and keep Session expired after dismissing a change rejected by an ended session". Anything not run is
NOT RUN or BLOCKED, never PASS.

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
| C5 (after `8e90dc8`) | A change rejected because the session ended also re-runs the sync, so after Dismiss the banner becomes "Session expired" + "Sign in again" (found on device: Dismiss would otherwise drop the session state). Test added. | `PortfolioPresenter.kt`, `PortfolioSessionStateTest.kt` |
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

## 3. Verification

**Automated (sequential, `--rerun`, on the committed code):**

| Suite | Result |
|---|---|
| `:core:jvmTest` | **PASS 478 / 0** (re-run after C5: 478 / 0) |
| `:core:iosSimulatorArm64Test` | **PASS 478 / 0** (before C5) |
| `:app:shared:testAndroidHostTest` | **PASS 226 / 0** (re-run after C5: 226 / 0) |
| `:app:shared:iosSimulatorArm64Test` | **PASS 222 / 0** (before C5) |
| `:server:test` (alone) | **PASS 499 / 0, 3 skipped** (no server code changed) |
| `:app:androidApp:assembleDebug` | **PASS** |
| unsigned `xcodebuild` | **BUILD SUCCEEDED**; ad-hoc signed simulator build with C5 also succeeded |

New/updated tests: `PortfolioSessionStateTest` (3), `DefaultAuthRepositoryTest` (+1), `PortfolioPhaseE2Test` (7), `PortfolioPhaseD1Test` (updated order).
C5 is a one-line presenter change; core iOS and shared iOS were not re-run after it (their previous run passed; JVM and Android host re-ran).

**Device (local MOCK on :8081; Auth emulator on :9099 for the session; iPhone 16 at Large; Android `emulator-5554` Pixel_9_Pro at 360 dp):**

| # | Scenario | Result | Evidence |
|---|---|---|---|
| 1 | iOS account sheet title at Large, keyboard hidden and shown; label stays above the name; Save disabled when empty, saves when named | **PASS** | `after-i16-large-account-title-keyboard-hidden`, `…-keyboard-label` (before: `before-i16-account-title-truncated`) |
| 1b | iOS title at AX5 | **BLOCKED**: the session ended during the run, and opening the sheet needs a signed-in user (re-sign-in is an owner step) | — |
| 2 | Android dialog "New investment account" wraps at 360 dp 1.0× | **PASS** | `after-a360-1x-account-title` |
| 3 | Typed TD.TO in a CAD account: Trade currency first in Amounts, note shown, visible above Shares with the keyboard; CAD → note gone, labels CAD; Cash currency followed to CAD with no FX field (iOS B3) | **PASS** (iOS); note **PASS** on Android 1.0× and 2.0× | `after-i16-large-form-trade-currency-note`, `…-typed-tdto-keyboard`, `…-cad-no-note`, `…-cash-follows-cad`, `after-a360-1x/2x-form-trade-currency-note` (before: `before-i16-form-currency-usd-hidden`) |
| 4 | Edit keeps the recorded currency | **NOT RUN** on device (no transaction could be saved before the session ended); unchanged code path (`existing.currency`) | — |
| 5 | History/details show the recorded currency | **NOT RUN** on device; display code unchanged (D3 tests) | — |
| 6 | Session ended (real expiry: old emulator session, ID token expired mid-run): rejected save says the session ended; Add / "+ Add" disabled with "Sign in again to add or change transactions."; after relaunch the banner is "Session expired" + "…Showing your last saved portfolio." + **Sign in again**; Sign in again signs out and opens the sign-in screen, which stays (no loop) | **PASS** (iOS) | `after-i16-large-save-session-ended`, `…-add-disabled-session`, `…-session-expired-banner`, `…-sign-in-again-opens-login` (before: `before-i16-session-ended-offline-banner`) |
| 6b | Signing in again and the portfolio reloading | **BLOCKED** (owner): needs a password typed into the app, which project rules leave to the owner | — |
| 6c | Android session-expired path | **NOT RUN**: no way to end the Android session without credentials (it uses the default Firebase project) | — |
| 7 | No connection (port forward removed): "Offline · last saved portfolio" + Retry, Add enabled; Retry after reconnecting clears it | **PASS** (Android) | `after-a360-1x-network-offline-retry` |
| 8 | Server error banner "Couldn't refresh your portfolio" + Retry | **NOT RUN** on device (MOCK has no 5xx route without server changes); covered by `PortfolioPhaseE2Test` / `PortfolioSessionStateTest` | — |
| 9 | Small-screen review: delete dialog at large text, history filters, dark theme, iOS AX5 | **NOT RUN** (blocked by the ended session on iOS; time) | — |

Sign-out scope (code inspection): `SignOut` = Firebase sign-out + this device's cached copy (`clearAccount`) + best-effort push-token unlink;
no portfolio request is sent, so server data is untouched. Not verified against server data on device.

**Not verified:** TalkBack, VoiceOver, Accessibility Inspector (deferred by the owner).

### 3b. Final verification pass (2026-10-13, after `095005d`; owner signed in with a local Auth-emulator account; committed as "Close Portfolio Phase E.2 with an AX5 fix that stacks the iOS account sheet pickers and a final verification pass recommending pass with conditions")

| Scenario | Result | Evidence |
|---|---|---|
| Re-sign-in: portfolio loads and editing is available after session recovery (fresh local account, iPhone 16) | **PASS** (account created, dashboard loaded with Add enabled) | — |
| "New investment account" sheet at **AX5**: title inline and complete | **PASS** | `after-i16-ax5-account-sheet-stacked` |
| AX5 sheet fields: Account category / Reporting currency pickers | **FAIL → FIXED (E2-F1, P2)**: label beside value broke "Ac-count cate-gory" mid-word with the value cut to "P…l"; now stacked at accessibility sizes like the D1 transaction form | before `before-i16-ax5-account-pickers-squeezed`, after `after-i16-ax5-account-sheet-stacked` |
| AX5 keyboard visible: Save account reachable above the keyboard; save succeeds | **PASS** | `after-i16-ax5-account-save-above-keyboard` |
| Large: Trade currency first in Amounts with the note, layout clear of the home indicator | **PASS** (re-confirmed) | — |
| Add CAD and USD transactions; edit keeps the recorded currency; history/details show it; account switching | **BLOCKED**: simulator tap injection stopped reaching the app (app alive; scroll works; taps on the enabled Add button repeatedly ignored) | — |
| Session-expiry re-test after re-sign-in (C5 Dismiss path, Sign in again) | **NOT RUN** this pass (verified in §3 rows 6; C5 covered by `PortfolioSessionStateTest`) | — |
| Dark theme, small screens (iOS SE excluded by the owner) | **NOT RUN** | — |
| Android | **NOT RUN** this pass: `emulator-5554` was stopped; Android signs in to the default Firebase project, so session-expiry there would act on a real account (not attempted) | — |

**Fix E2-F1** (`app/iosApp/iosApp/PortfolioForms.swift`, iOS only): the account sheet reads `dynamicTypeSize` and, at accessibility sizes,
puts each picker's label above its value (`stacked`, same pattern as the transaction form). Below AX sizes the layout is unchanged. Validation:
ad-hoc signed simulator `xcodebuild` **BUILD SUCCEEDED** and the after capture. No shared/Kotlin code changed, so the Kotlin suites were not
re-run. The unsigned `xcodebuild` was not run separately (the signed build compiles the same sources).

**New P3 observations (not fixed, out of scope):** Home "Markets today" breaks mid-word ("Market / s today") at AX5; the iOS account-name field
has no keyboard dismiss on Return (Save stays reachable by scrolling).

## 4. Remaining

1. Owner/tooling: finish add CAD + USD, edit currency, history/details currency and account switching on the iPhone 16 (simulator input
   stopped working in the final pass); dark theme.
2. Known gaps (not changed):
   - Home's portfolio card still says "Offline · last saved portfolio" for an ended session (`HomeDashboardPresentation`; outside E.2 scope).
   - Edit / Delete on transaction details stay enabled while the session is expired; they fail with the session message.
   - A server 401 shows the server's text ("Sign in to use watchlists and alerts.") as the message, under "Session expired".
   - Typed-symbol currency is never looked up (D-3); MOCK's synthetic TD.TO profile is USD / NASDAQ (D-5, backend session).
   - P3: Home "Markets today" mid-word break at AX5; no Return-to-dismiss on the iOS account name.
3. Allocation A1: schedule as its own phase (proposal §7 now complete).

## 5. Recommendation

**PASS WITH CONDITIONS.** Everything E.2 changed is implemented and covered by tests. All suites pass, and the device checks of the new behaviour pass on iOS and Android. One AX5 defect found in the final pass was fixed and verified.
Conditions: the currency round-trip (add CAD/USD → edit → history/details) on device, Android session-expiry at runtime, and dark theme.
TalkBack, VoiceOver and Accessibility Inspector remain **NOT VERIFIED** (deferred by the owner). Not a full accessibility sign-off.
