# Portfolio UI Refinement — Phase E.1: accessibility verification and QA closure (report)

Status: **executed 2026-10-13; committed and pushed as "Fix Portfolio Phase E.1 session-refresh messaging so an ended session says to sign in again instead of blaming the password, and record the allocation-denominator proposal and blocked screen-reader checks". Recommendation: NOT READY FOR SIGN-OFF** (TalkBack and iOS signed-in verification still need owner
interaction; VoiceOver and Accessibility Inspector blocked). Plan: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_E_1_PLAN.md`, approved with conditions 1–7.
Base: `863d756` (backend session) on top of Phase E `3d91ef3`.

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

## 3. iOS signed-in workflow matrix (E.1-B)

The iPhone 16 runs the fixed MOCK build against the local Auth emulator. The cached session is invalid (that's Issue 1). **The owner sign-in step
wasn't performed**, so every signed-in workflow is blocked. Claude doesn't enter credentials on simulators.

| Scenario | Expected | Actual | Result |
|---|---|---|---|
| Sign-in and account selection | Sign in with an emulator test account; Portfolio loads online | Not performed (owner step) | **BLOCKED** |
| Add transaction | "Transaction added" once; row appears | — | BLOCKED |
| Edit transaction | "Transaction updated"; details re-render | — | BLOCKED |
| Delete transaction | "Transaction deleted"; back to history | — | BLOCKED |
| Rejected save (sell more than owned) | Form: "Couldn't save transaction" + server text, announced once | — | BLOCKED |
| Rejected delete (later sale depends) | "Couldn't delete transaction" + Dismiss | — | BLOCKED |
| History updates after mutations | Counts and months update | — | BLOCKED |
| Samples read-only | No ⋯; details "Sample transactions can't be changed." | — | BLOCKED |
| Account switching and scoping | Holding scope dropped on switch | — | BLOCKED |
| Back to Portfolio | Portfolio tab, scroll kept | — | BLOCKED |
| Session-ended message (stale session) | No password wording | "Your session has ended. Sign in again to continue." | **PASS [V]** |

The equivalent Android flows passed on device in D3 / D3.1 [V].

## 4. iOS accessibility findings (E.1-C)

| Check | Result |
|---|---|
| Accessibility Inspector audit | **BLOCKED** — GUI tool, not run (condition 5) |
| VoiceOver | **BLOCKED** — physical device required |
| Dynamic Type Large / AX5, light/dark, keyboard, validation focus | PASS [V] in Phase E (`portfolio_phase_e/i16-*`) |
| Labels, traits, grouping (static) | Reviewed in Phase E [S]: headings, row labels, ⋯ labels, error announcements (E-01/E-02) |

## 5. Remaining blockers

1. TalkBack journeys 1–10: owner gestures on emulator-5554 (about 15 min).
2. iOS signed-in workflows: one owner sign-in on the iPhone 16 with a local emulator account.
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

**NOT READY FOR SIGN-OFF.** The code change is verified for the reproducible case and every automated suite passes. But the core E.1 objectives
— TalkBack journeys and iOS signed-in workflows — are BLOCKED pending owner interaction, and VoiceOver / Inspector are BLOCKED by the
environment. To close:
1. tell me when you're at the emulator and simulator;
2. I restart MOCK and the Auth emulator, re-enable TalkBack and the recorder, and walk you through the steps;
3. then I finish the matrices.
