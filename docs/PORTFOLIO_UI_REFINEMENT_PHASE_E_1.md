# Portfolio UI Refinement — Phase E.1: accessibility verification and QA closure (report)

Status: **executed 2026-10-13.**
- The first part (session-message fix, allocation proposal) was committed and pushed as "Fix Portfolio Phase E.1 session-refresh messaging so an
  ended session says to sign in again instead of blaming the password, and record the allocation-denominator proposal and blocked screen-reader
  checks".
- The **iOS signed-in verification (§3) was completed afterwards, the same day, with the owner's sign-in. That update was committed and pushed as "Record Portfolio Phase E.1 iOS signed-in verification with all add, edit, delete, rejection, sample, account-switch and navigation checks passing on the iPhone 16 simulator".**
- **Recommendation: NOT READY FOR SIGN-OFF**: TalkBack is deferred by the owner and not verified; VoiceOver and Accessibility Inspector are not
  verified.

Evidence types: **[A]** automated test; **[S]** static code inspection; **[V]** simulator/emulator visual check; **[R]** actual screen-reader output.
Nothing below counts a build or screenshot as screen-reader proof.

## 1. Changes made

### Issue 1 — misleading "Check your email and password" on a session failure (Q1, condition 1)

**Reproduced and confirmed before the fix:**
- The local Auth emulator answers the simulator's old cached session with `INVALID_REFRESH_TOKEN`: [V] a direct call to
  `127.0.0.1:9099/securetoken…/token` returned HTTP 400 `INVALID_REFRESH_TOKEN`.
- A temporary diagnostic log in the token path (since removed) showed the iOS SDK reports it as **`FIRAuthErrorDomain` 17999
  (`ERROR_INTERNAL_ERROR`)**, with the server reason `INVALID_REFRESH_TOKEN` inside the underlying deserialized response.
- The token-refresh path reused the sign-in mapper, whose default is "Could not sign in. Check your email and password."
- **Verdict:** a session-refresh failure. Not a wrong password, not a network outage. A network failure already maps to "Could not connect…".

**Fix** (platform auth adapters only; shared auth behaviour and every sign-in message unchanged):

| Path | Firebase result | Message |
|---|---|---|
| Token refresh (iOS `FirebaseAccountAdapters.tokenMessage`, Android `AndroidAuthGateway.tokenMessage`) | network | "Could not connect. Check your connection and try again." |
| | expired / invalid token, user not found / disabled, recent login required; **iOS also:** internal error whose server reason is `INVALID_REFRESH_TOKEN`, `TOKEN_EXPIRED`, `USER_NOT_FOUND` or `USER_DISABLED` | **"Your session has ended. Sign in again to continue."** |
| | anything else | "Sign in again to continue." |
| Email / Google sign-in | as before | unchanged (including "Could not sign in. Check your email and password." for wrong credentials) |

**Verified [V]:** iPhone 16, local emulator plus the stale session. The banner now reads "Offline · last saved portfolio / Your session has
ended. Sign in again to continue." (`docs/design/portfolio_phase_e_1/i16-large-session-ended-after.jpg`).

**Android [S] only:** the Android emulator build isn't pointed at the Auth emulator and has no stale session, so its token path wasn't
reproduced. The mapping follows Firebase Android's documented error codes. Build OK.

**Recovery path** (Q3 deferred, so the banner keeps Retry): the message tells the user to sign in again, using the existing account entry (Home →
account icon → Sign in). A "Sign in" banner action stays a separate shared-banner review.

### Issue 2 — allocation denominator (Q4, condition 4)

Investigated, with **no engine change**. Proposal: `docs/PORTFOLIO_ALLOCATION_DENOMINATOR_PROPOSAL.md`:
- dashboard = net account value (signed cash);
- Insights = gross long value (positive cash only, borrowed cash noted);
- both internally correct;
- recommendation: gross long value app-wide, as its own phase with the listed tests.

## 2. Android TalkBack verification matrix (E.1-A)

Environment prepared and verified:
- emulator-5554, 360 dp, local MOCK, account "PhaseE1" with 4 transactions (negative cash);
- TalkBack on with "Display speech output";
- a background recorder saving the speech display every 2 s.

Owner instructions were given. **No owner gestures were received:** 125 recorded frames, all showing only the initial focus
(`a-talkback-ready-no-owner-input.jpg`). Injected `adb` input still can't drive TalkBack (Phase E).

| # | Journey | Result | Evidence |
|---|---|---|---|
| 1 | Dashboard | **BLOCKED** — owner gestures required. Only the initial announcement "Portfolio. Heading." was observed [R] | `a-talkback-ready-no-owner-input.jpg` |
| 2 | Holdings | BLOCKED | — |
| 3 | Allocation | BLOCKED | — |
| 4 | Add transaction | BLOCKED | — |
| 5 | Validation errors | BLOCKED | — |
| 6 | Transaction history | BLOCKED | — |
| 7 | Transaction details | BLOCKED | — |
| 8 | Edit / delete confirmation | BLOCKED | — |
| 9 | Success / error feedback | BLOCKED | — |
| 10 | Account switching | BLOCKED | — |
| — | Portfolio tab selected on sub-screens | PASS [V] (Phase E captures) / not spoken-verified | `portfolio_phase_e/*` |

Static semantics for these journeys were reviewed in Phase E [S]; that is not a TalkBack pass. The exact owner steps are in the plan §E.1-A (and
repeated in the handoff). TalkBack and the speech display were turned off afterwards.

## 3. iOS signed-in workflow matrix (E.1-B) — executed 2026-10-13

Environment:
- iPhone 16 (iOS 18.3), Large, light;
- the E.1 MOCK build (ad-hoc signed, `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081`), launched with `--firebase-emulators`;
- local MOCK (port 8081) and the local Firebase Auth emulator (port 9099); no production Firebase.

**Owner step:** signed out of the stale session and created the local emulator account `e1-test@example.test`. The emulator's
`accounts:query` showed 1 account. Claude never saw or typed the password. Claude then created the portfolio account "E1 iOS · CAD" and drove every
scenario.

All results are **[V]** (simulator visual checks with screenshots), in `docs/design/portfolio_phase_e_1/`. **No screen-reader output was tested.**

| Scenario | Expected | Actual | Result | Evidence |
|---|---|---|---|---|
| Sign-in and account selection | Online portfolio for the new user; account can be created and selected | Empty state with no offline banner; "E1 iOS · CAD" created and selected | **PASS** | `i16-e1-01-signed-in-empty` |
| Add transaction | Sheet closes on a confirmed save; "Transaction added" once; holding appears | Opening TD.TO 5 @ 75 → "Transaction added"; holding TD.TO 5 shares; total CAD 2,166.48. A second add (Sell 2 @ 85) also confirmed | **PASS** | `i16-e1-03-add-success` |
| Rejected save (insufficient shares) | Form stays open; "Couldn't save transaction" + server text; no success | Sell 100 → sheet stayed open with "Couldn't save transaction. Transaction would sell or transfer more shares than owned on 2026-10-13."; no banner | **PASS** | `i16-e1-04-rejected-save-form` |
| Error feedback on the screen behind | Same title, server text, **Dismiss** (not Retry) | Dashboard: "Couldn't save transaction" + text + Dismiss; Dismiss cleared it | **PASS** | `i16-e1-05-dashboard-save-error-dismiss` |
| Rejected deletion (later sale depends) | Details stay; "Couldn't delete transaction" + server text + Dismiss; no success | Opening position delete → exactly that; the transaction is still listed | **PASS** | `i16-e1-07-rejected-delete` |
| Edit transaction | "Transaction updated"; details re-render from the server | Shares 5 → 6 → "Transaction updated"; details show 6 shares, USD 450.00 | **PASS** | `i16-e1-08-edit-updated` |
| Delete transaction | Confirmation names the transaction; "Transaction deleted"; history updates | Row ⋯ → Delete → dialog "Sell · TD.TO · 2 shares · Oct 13, 2026 · USD 170.00" → "Transaction deleted"; history 2 → 1 | **PASS** | `i16-e1-09-deleted-history` |
| History updates after mutations | Counts and rows follow each confirmed change; no repeated banner on Back | 2 transactions → edit shown (6 shares) → 1 after delete; no repeated "Transaction updated" when returning to history | **PASS** | `i16-e1-06-history`, `i16-e1-09-deleted-history` |
| Sample transactions read-only | SAMPLE banner; Add disabled; no ⋯; details "Sample transactions can't be changed."; no invented amount | All as expected (sample "Multiple accounts") | **PASS** | `i16-e1-10…12` |
| Account switching and scoping | History shows only the selected account; holding scope dropped on switch | Personal USD → TFSA CAD showed TD.TO (CAD) only. From TD.TO Holding details → "TD.TO transactions only" → switching to Personal USD dropped the scope and showed AAPL | **PASS** | `i16-e1-13…15` |
| Navigation back to Portfolio | Back returns to the dashboard at the same scroll position; Portfolio tab stays selected | As expected (every capture shows Portfolio selected) | **PASS** | captures above |
| Session-ended message (stale session, before sign-out) | No password wording | "Your session has ended. Sign in again to continue." | **PASS** | `i16-large-session-ended-after`, `i16-large-session-ended-rerun` |

**New finding (P3, pre-existing, not changed):** the "Create investment account" sheet title is truncated ("Create investment ac…") at Large text
(`i16-e1-02-account-form-title-truncated`). It's the existing account form (outside D2–E scope); candidate for a later polish phase.

## 4. iOS accessibility findings (E.1-C)

| Check | Result |
|---|---|
| Accessibility Inspector audit | **BLOCKED** — GUI tool, not run (condition 5) |
| VoiceOver | **BLOCKED** — physical device required |
| Dynamic Type Large / AX5, light/dark, keyboard, validation focus | PASS [V] in Phase E (`portfolio_phase_e/i16-*`) |
| Labels, traits, grouping (static) | Reviewed in Phase E [S]: headings, row labels, ⋯ labels, error announcements (E-01/E-02) |

## 5. Remaining blockers

1. TalkBack journeys 1–10: **deferred by the owner** (NOT VERIFIED).
2. iOS signed-in workflows: no longer blocked — done, all PASS (§3).
3. VoiceOver: physical iPhone.
4. Accessibility Inspector: owner run.
5. Android token-message path: not reproducible without an Auth-emulator Android build and a stale session (code inspection only).

## 6. Regression results [A] (sequential, `--rerun`, 2026-10-13)

| Suite | Result |
|---|---|
| core JVM / core iOS | **474 / 0** each |
| server (run alone) | **499 / 0** (3 skipped) |
| shared Android host / shared iOS | **219 / 0** / **215 / 0** |
| `:app:androidApp:assembleDebug` | OK |
| unsigned `xcodebuild` | **BUILD SUCCEEDED** |

No new automated test: the changed mappers are platform adapter code with no existing unit-test harness. Verified by runtime reproduction (iOS) and
build (Android).

## 7. Known financial issue

K-01 (dashboard vs Insights denominator): documented, not changed; separate proposal in §1 Issue 2.

## 8. Files changed

- `app/iosApp/iosApp/FirebaseAccountAdapters.swift`: `tokenMessage`, `serverReason`; the token path uses them.
- `app/androidApp/src/main/kotlin/org/example/stocksteps/account/AndroidAuthGateway.kt`: `tokenMessage`; the token path uses it.
- Docs:
  - `docs/PORTFOLIO_UI_REFINEMENT_PHASE_E_1_PLAN.md`, this report, `docs/PORTFOLIO_ALLOCATION_DENOMINATOR_PROPOSAL.md`;
  - `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`, `CLAUDE.md`.
- Captures: `docs/design/portfolio_phase_e_1/` (2).

## 9. Git status (at report time)

Before the commit: `main` = `origin/main` = `863d756`, with only the E.1 paths in §8 uncommitted. Committed and pushed as "Fix Portfolio Phase E.1 session-refresh messaging so an ended session says to sign in again instead of blaming the password, and record the allocation-denominator proposal and blocked screen-reader checks".

## 10. Environment at handoff

- **Android emulator-5554:** TalkBack off, speech display off, font 1.0, size reset, `adb reverse` removed. A second emulator `emulator-5556`
  (not started by Claude) was left untouched.
- **iPhone 16:** booted, Large/light, the E.1 MOCK build installed.
- **MOCK and Auth emulator:** stopped.

## 11. Recommendation

**NOT READY FOR SIGN-OFF.**
- Every iOS signed-in workflow and the session-ended message passed on the simulator, and the automated suites pass.
- But the screen-reader objectives remain **NOT VERIFIED**: TalkBack (deferred by the owner), VoiceOver (physical device required) and
  Accessibility Inspector.
- Sign-off is possible once those are run, or the owner explicitly accepts them as out of scope.

### Update log
- 2026-10-13, after the E.1 commit: iOS signed-in matrix executed (§3); new P3 finding (account-form title truncation).
  - No code changed, so no rebuild or test rerun was required (the §6 results stand for the committed code).
  - MOCK and the Auth emulator were stopped afterwards. The iPhone 16 stays signed in as the local emulator test account (it disappears when the
    emulator is restarted).
