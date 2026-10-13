# Portfolio UI Refinement — Phase E.1 plan: accessibility verification and QA closure

Status: **approved with conditions 1–7 and executed; committed as "Fix Portfolio Phase E.1 session-refresh messaging so an ended session says to sign in again instead of blaming the password, and record the allocation-denominator proposal and blocked screen-reader checks". See `docs/PORTFOLIO_UI_REFINEMENT_PHASE_E_1.md`.**
- Base: "Add Portfolio Phase E accessibility fixes with iOS error and rejection announcements, unbroken P/L labels, filter-only history announcements
  and single-announcer save errors, plus Android and iOS large-text QA" (`3d91ef3`, pushed).
- Working tree: only the backend session's uncommitted `PROJECT_HANDOFF.md` (root), `docs/project-status.md` and
  `docs/UBUNTU_BACKEND_VERIFICATION_2026-10-13.md`. E.1 will not touch or stage them.

## 1. Environment facts that decide what can run

| Need | Available to this session? | Consequence |
|---|---|---|
| Real TalkBack gestures | **No.** TalkBack ignores injected `adb` input (Phase E: swipe, tap, `motionevent`, Alt+Arrow). No desktop-control tool is connected to this session. | TalkBack journeys need the **owner** at the emulator window, or a desktop-control session if you connect one |
| iOS sign-in | Partly. The local Auth emulator and MOCK can run. The cached simulator session is unknown to a fresh emulator. Claude doesn't type credentials into simulators (project rule). | The **owner** signs in once on the simulator with a local emulator test account (Claude can create the account through the emulator's local REST API and tell you the email; you choose and type the password). After that, Claude drives every signed-in flow. |
| Accessibility Inspector | **No** (GUI app; no headless interface, no desktop control) | Owner-run (checklist below) or BLOCKED |
| VoiceOver | Needs a physical iPhone | BLOCKED unless you provide one |
| Android/iOS visual checks, tests, builds | Yes | Claude runs them |

## 2. Stage plan

### E.1-A — Android TalkBack (owner-driven, Claude records)

Claude prepares:
- local MOCK with a seeded "PhaseE1" account: opening, deposit, buy (cash below zero), sell;
- TalkBack + "Display speech output" on emulator-5554.

The owner performs the gestures. Claude takes a screenshot of the speech display after each step (`adb screencap` needs no gestures) and fills in
the matrix.

| # | Journey | Owner gestures | Expected (from code) |
|---|---|---|---|
| 1 | Dashboard | Swipe right from "Portfolio" | "Portfolio, Heading" → info, ⋯ → "My Portfolio" tab → account "Account: PhaseE1 · CAD" → "+ Add" → total value → today's change → … |
| 2 | Holdings | Swipe to "Holdings (2)" | Heading; each row one sentence (name, symbol, shares, value, change in words) |
| 3 | Allocation | Swipe through allocation | Heading; each legend entry "TD.TO, USD, 21.65 percent of account value, CAD …"; cash note read once |
| 4 | Add transaction | Double-tap "+ Add transaction"; swipe through the form | Fields read label + value; date field read as a button with its value |
| 5 | Validation | Double-tap Save with empty fields | Focus moves to the first invalid field; its error is read once |
| 6 | History | Double-tap "See all transactions" | Filter pills read as tabs with selected state; month headings; rows one sentence; ⋯ "Actions for …" |
| 7 | Details | Double-tap a row | Summary sentence; "Details" heading; each row label + value |
| 8 | Edit / delete | Double-tap Delete → Cancel; Edit → Cancel | Dialog title read first; focus returns to the screen on dismiss |
| 9 | Feedback | Edit and save; reject a sell of 100 shares | "Transaction updated" read once; "Couldn't save transaction …" read once (form only, E-05) |
| 10 | Account switching | Account selector → choose another (sample "Multiple accounts") | "Account: …" updates; history count announced only on a filter change (E-04) |

On every sub-screen: Portfolio stays the selected tab ("Portfolio, selected"). PASS only for steps whose spoken output was captured.

### E.1-B — iOS signed-in functional verification

One owner step: sign in on the iPhone 16 with the emulator test account. Then Claude runs and captures:
- add;
- edit;
- delete;
- a rejected save (sell more than owned);
- a rejected delete (opening position with a later sell);
- success / error banners;
- history updates;
- samples read-only;
- account switching and scoping;
- Back to Portfolio.

Each gets expected / actual / PASS-FAIL-BLOCKED and a screenshot.

### E.1-C — iOS accessibility

Owner checklist in Accessibility Inspector (simulator target → Audit → Run audit) on: dashboard, Add form, history, details, delete confirmation,
banners, at Large and AX5. Claude records the results. VoiceOver: **BLOCKED — physical device required**, unless you provide a device.

### E.1-D — UX investigations

**Issue 1 — "Offline · last saved portfolio" + "Could not sign in. Check your email and password."**

Root cause (code):
- Backend requests get an ID token from `getIDTokenForcingRefresh` (iOS `FirebaseAccountAdapters.swift`) or `getIdToken` (Android
  `AndroidAuthGateway.kt`).
- When that fails, the error goes through `safeAuthMessage`, the **same mapper used by sign-in**. Every unlisted Firebase code (for example
  "user token expired", "invalid user token", "user not found", which a fresh emulator returns for an old session) falls to the default "Could not
  sign in. Check your email and password."
- The repository then shows the cached portfolio as offline under the offline title.

So the screenshot is **a session-refresh failure, not a wrong password and not a network outage**. A real network failure already maps to "Could
not connect. Check your connection and try again." The exact Firebase code will be confirmed at runtime from the simulator log before any change.

Proposed fix (approval needed; auth-adapter code only, no shared auth behaviour change):

| Path | Firebase result | Message |
|---|---|---|
| Token refresh | expired / invalid token, user not found, user disabled, requires recent login | **"Your session has ended. Sign in again to continue."** |
| Token refresh | network error | "Could not connect. Check your connection and try again." (unchanged) |
| Token refresh | anything else | "Sign in again to continue." (the existing generic text) |
| Sign-in (email/password) | as today | unchanged, including "Could not sign in. Check your email and password." for wrong credentials |

Implemented as a separate `tokenMessage` mapper next to `safeAuthMessage`, in `FirebaseAccountAdapters.swift` and `AndroidAuthGateway.kt` (which
are platform adapters).

The banner title stays "Offline · last saved portfolio": the data shown *is* the last saved copy. A "Sign in" action instead of Retry would be a
shared-banner change: proposed only, not in E.1 unless approved (Q3).

Tests: the mappers are platform code with no unit-test harness. Verification is a runtime reproduction on both platforms (fresh emulator + cached
session) with screenshots. The shared `userDataError` path is unchanged.

**Issue 2 — allocation denominator (K-01)**

| Screen | Denominator | Code | Holdings add up to |
|---|---|---|---|
| Dashboard "By holding" | **Net account value** = holdings + signed cash (negative cash lowers it) | `PortfolioEngine.allocations` | >100% when cash < 0, <100% when cash > 0 (explained by the D3.1 note) |
| Insights allocation | **Gross long value** = holdings + *positive* cash only; borrowed cash excluded and noted separately | `PortfolioAnalyticsEngine.allocation` | 100% with cash as its own slice |

Worked example (D3.1 data, USD→CAD 1.35):
- holdings CAD 6,711.53; cash −CAD 69.53;
- dashboard: TD.TO 2,166.48 / 6,642.00 = **32.62%**, AAPL **68.43%**;
- Insights: 2,166.48 / 6,711.53 = **32.28%**, AAPL **67.72%**.

Both are internally correct and each discloses its rule ("…of account value, which includes cash" / "Borrowed or unfunded cash … not included in
percentages"). They answer different questions: "share of my net worth here" versus "share of what I own".

Proposed separate follow-up (**not in E.1**; owner decision Q4): use one definition app-wide. Recommendation for beginners is **gross long value**
(Insights' rule):
- it always adds up to 100%;
- cash is its own slice;
- unfunded cash is shown separately.

That means changing `PortfolioEngine.allocations` (server report) and the dashboard note, with tests:
- the D3.1 ledger → 32.28 / 67.72;
- positive cash → a cash slice, sum 100.00;
- negative cash → no cash slice plus the separate unfunded line;
- missing price → percentages hidden.

The engine output changes, so it needs its own phase, server tests and a MOCK restart.

### E.1-E — Regression

After the approved code (Issue 1 only), run sequentially:
- `:core:jvmTest`, `:core:iosSimulatorArm64Test`;
- `:app:shared:testAndroidHostTest`, `:app:shared:iosSimulatorArm64Test`;
- `:server:test` (alone; it collided with the backend session in D3.1);
- `assembleDebug`, unsigned `xcodebuild`.

Then repeat the auth-message reproduction (light/dark, standard/large).

## 3. Summary for approval

1. **Automatic (Claude):**
   - MOCK + emulator setup and seeding;
   - TalkBack speech capture (with you gesturing);
   - all iOS signed-in flows after your sign-in;
   - Issue 1 reproduction and fix;
   - tests and builds;
   - visual checks;
   - report.
2. **Needs you:**
   - TalkBack gestures (about 15 minutes, following §E.1-A);
   - one iOS sign-in;
   - the Accessibility Inspector audit (optional);
   - a physical iPhone for VoiceOver (optional).
3. **Blocked by the environment:**
   - VoiceOver without a device;
   - Inspector without you;
   - TalkBack without you.
4. **Code changes:** Issue 1 token-refresh wording only (two platform auth adapters). No financial, ledger, API, entitlement or Firebase-config change.
5. **Order:** E.1-D Issue 1 (reproduce → fix) → E.1-B (needs your sign-in) → E.1-A (needs your gestures) → E.1-C → E.1-E → report.
6. **Files expected to change:**
   - `app/iosApp/iosApp/FirebaseAccountAdapters.swift`, `app/androidApp/src/main/kotlin/.../account/AndroidAuthGateway.kt`;
   - docs `PORTFOLIO_UI_REFINEMENT_PHASE_E_1.md`, this plan, `docs/PROJECT_HANDOFF.md`, `CLAUDE.md`;
   - captures `docs/design/portfolio_phase_e_1/`.

## 4. Decisions

| ID | Question | Recommendation |
|---|---|---|
| **Q1** | Approve the token-refresh message fix (Issue 1) on both platforms? | Yes |
| **Q2** | Will you do the iOS sign-in step and the TalkBack gestures (I set everything up and record)? | Yes; otherwise both stay BLOCKED |
| Q3 | Replace Retry with "Sign in" on a session-ended banner (shared banner change)? | Not in E.1 |
| Q4 | Allocation denominator: open a separate follow-up adopting gross long value app-wide? | Yes, as its own phase after E.1 |
| Q5 | Accessibility Inspector: will you run the checklist, or mark it BLOCKED? | Mark BLOCKED unless you have time |
