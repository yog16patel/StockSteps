# StockSteps — session handoff (2026-10-13: Portfolio Phases D1.1, D2 (Transaction history) and D3 (Transaction details, action feedback) committed and pushed; next = Phase E)

Read order for a new session: `CLAUDE.md` → **§D3-2026-10-13 below** → §D2-2026-10-13 → §D1-1-CLOSURE-2026-10-13 → §HANDOFF-2026-10-13-END → §D1-1-2026-10-13 → §D1-2026-10-12 → §C1-CLEANUP-2026-10-12 → §C1-CLOSURE-2026-10-12 → §HANDOFF-2026-10-12-END → §PORTFOLIO-PC1-2026-10-12 → §PORTFOLIO-PC-2026-10-12 → §PORTFOLIO-PB1-2026-10-12 → §PORTFOLIO-PB-2026-10-12 → §PORTFOLIO-PA-2026-10-12 → §HANDOFF-2026-10-12 → §A11Y-CLOSURE-5C → §FINAL-POLISH-5C → §CLOSURE-5C1-PA → §HANDOFF-2026-10-11 → §PHASE5C1 → §PHASE5C → §PHASE5B-CLOSURE → §PHASE5B → §NEXT → §PHASE5A1 → §PHASE5A → §PHASE4C3 → §PHASE4C2 → §PHASE4C1 → §PHASE4C → §PHASE4B1 → §PHASE4B → §PHASE4A → §SESSION → §000000000…§00000 (Global UI Refinement phases, newest first) →
`docs/GLOBAL_UI_REFINEMENT_PLAN.md` → `docs/GLOBAL_UI_REFINEMENT_PHASE3_PORTFOLIO.md` + `docs/GLOBAL_UI_REFINEMENT_PHASE3_1_POLISH.md` →
§0000 (Phase 5C.1) → `docs/project-status.md` §0 → root `PROJECT_HANDOFF.md` → the code.
Always start with `git status` and `git log -5 --oneline`; the repository is authoritative when docs disagree.

## D3-2026-10-13. Portfolio Phase D3 (Transaction details and action feedback) — START HERE (committed and pushed with D2)

### Repository state
- Branch `main`, in sync with `origin/main` after the push. HEAD: **"Add Portfolio Phases D2 and D3 with transaction history filters, transaction details, confirmed-only add/edit/delete feedback, reusable transaction rows and sample read-only handling on Android and iOS"**, on top of "Close Portfolio Phase D1.1 transaction form polish with all-at-once field validation, type-switch error clearing, selected-security line, scoped Android keyboard resize and stacked large-text actions on Android and iOS". Both were pushed at the owner's request on
  2026-10-13.
- That commit holds D2 and D3 together (they share files), staged by explicit path, with no backend-migration files:
  - D2 paths: see §D2-2026-10-13;
  - core: `portfolio/PortfolioPresenter.kt`, `PortfolioPresenterSaveTest.kt`;
  - shared Kotlin: `PortfolioTransactionDetailsDisplay.kt`, `PortfolioTransactionScreen.kt` and the edited row, history, dashboard, scene, route,
    navigation, `AccountDependencies` and `IosAccountClient` files;
  - iOS: `PortfolioTransactionDetailsScreen.swift` plus the edited Swift files;
  - tests: `PortfolioPhaseD3Test.kt`;
  - docs: D3 report + plan, captures `docs/design/portfolio_d3/` (16).

### What D3 does
The owner approved the plan's recommendations Q1–Q6 on 2026-10-13.
- **Transaction details destination.** Android `PortfolioTransactionRoute(transactionId)`; iOS `navigationDestination`. Same presenter, looked up by
  id. Shows recorded fields only (per type, `visibleFields`). Edit / Delete use the existing sheet and confirmation; samples get Edit only; a
  vanished transaction shows "Transaction not available".
- **Success banners** "Transaction added / updated / deleted", shown only after confirmation:
  - additive presenter `PortfolioUiState.actionResult`, set only after the repository returned;
  - a shared `PortfolioFeedbackTracker` in `AccountDependencies`, so each result shows once, across screens and rotation;
  - the Add screen and edit sheet close on that confirmed result (no false success on a cancelled request).
- **Rows** (dashboard + history):
  - avatar (logo/ticker; icon for cash rows), and the recorded `grossAmount` right-aligned (none when not recorded, never "0.00");
  - date and price per share on their own lines; ⋯ separate;
  - large text stacks.
- **Delete confirmation** names the transaction.

### Validation
- **Sequential run, test tasks re-run:**
  - core 473/0 (JVM) and 473/0 (iOS);
  - server 492/0 (3 skipped);
  - shared 210/0 (Android) and 206/0 (iOS), including `PortfolioPhaseD3Test` 9/0;
  - `assembleDebug` OK; unsigned `xcodebuild` BUILD SUCCEEDED.
  - The new presenter test first failed on iOS only, because of test sequencing (it waited on error text that the mock's valuation error also
    produced). It now waits on the request counter and passes 3× on each platform.
- **Android device** (360 dp, local MOCK, approved), all PASS:
  - add/edit/delete banners (no repeat after Back);
  - a rejected edit keeps the server message with no banner;
  - details from the dashboard and from history;
  - the filter and holding scope survive Back;
  - sample read-only;
  - 2.0×; dark.
- **NOT VERIFIED:**
  - **iOS runtime** (not approved; the Auth emulator would also be needed);
  - TalkBack, VoiceOver;
  - Android 1.3×;
  - the "not available" state on a device;
  - the cancelled-mutation path;
  - physical devices.

### Environment at handoff
- **Android:** size reset, font 1.0, night mode off, app theme Light, `adb reverse` removed.
- **MOCK:** stopped. Its in-memory "D3Test" account is gone.
- **Auth emulator:** never started. iPhone 16 untouched.

### Next
1. Phase E: QA, screen readers, the iOS runtime pass (needs approval).
2. Phase 5D.

Candidates:
- a clearer title for presenter errors (today they show under "Couldn't refresh your portfolio");
- the Portfolio tab highlight on sub-screens.

## D2-2026-10-13. Portfolio Phase D2 (Transaction history) — committed and pushed with D3

### Repository state
- Branch `main`, HEAD "Close Portfolio Phase D1.1 transaction form polish with all-at-once field validation, type-switch error clearing, selected-security line, scoped Android keyboard resize and stacked large-text actions on Android and iOS" (local, **not pushed**; one commit ahead of `origin/main`).
- **Phase D2 paths** (uncommitted when written; now in the D2 + D3 commit):
  - shared Kotlin:
    - new `presentation/portfolio/PortfolioTransactionsDisplay.kt`, `PortfolioTransactionsScreen.kt`, `PortfolioTransactionViews.kt`;
    - edited `PortfolioScreen.kt`, `PortfolioScene.kt`, `PortfolioRoute.kt`, `presentation/AppNavigation.kt`;
  - iOS: new `app/iosApp/iosApp/PortfolioTransactionsScreen.swift`; edited `PortfolioScreen.swift`, `PortfolioScene.swift`;
  - tests: new `app/shared/src/commonTest/.../portfolio/PortfolioPhaseD2Test.kt`;
  - docs:
    - new `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D2.md` (report) and `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D2_PLAN.md` (plan, now marked approved);
    - edited `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`, this file, `docs/project-status.md`, `CLAUDE.md`;
  - captures: `docs/design/portfolio_d2/` (19, "after" only).

### What D2 does
Transaction history inside Portfolio (no new tab). The owner approved Q4 and these decisions on 2026-10-13:
- Opening position → **Other** (not Buys);
- Delete hidden in sample mode, Edit kept read-only;
- "See all transactions (N)" always shown when N ≥ 1;
- save confirmation → Phase D3.

Rules:
- Filters, scope and month grouping come from `PortfolioTransactionsDisplay`: display only, presenter order, no sums.
- Dashboard / Holding details show a 5-row preview plus the link.
- The holding scope applies only while its account is selected; an account switch drops it permanently.
- Row, edit sheet, delete dialog and account selector are shared by both screens.
- **Pre-existing D1 bug fixed:** sample states are `busy = true`, so dismissing the Android edit sheet on a sample row left an invisible scrim that
  blocked the screen. A read-only sheet now always closes.

### Validation
- Sequential run, test tasks re-run:
  - core 472/0 (JVM) and 472/0 (iOS);
  - server 492/0 (3 skipped);
  - shared 201/0 (Android) and 197/0 (iOS), including `PortfolioPhaseD2Test` 11/0;
  - `assembleDebug` OK; unsigned `xcodebuild` BUILD SUCCEEDED.
- **Android device** (emulator 360 dp, local MOCK, approved), all PASS:
  - preview + link;
  - filters (Buys excludes the opening position);
  - month groups;
  - edit and delete from history;
  - empty filter + reset;
  - Back restores the dashboard scroll;
  - sample Delete hidden, Edit read-only, banner;
  - holding scope + account switch;
  - 2.0× and dark 1.0×;
  - semantics via uiautomator.
- **NOT VERIFIED:**
  - **iOS runtime** (the simulator launch was blocked by the session permission classifier, and the cached session needs the Auth emulator, which
    wasn't approved): iOS history, iOS edit/delete from history and iOS Large/AX5 are build-only;
  - TalkBack, VoiceOver;
  - Android 1.3×; iPhone SE (excluded); physical devices.

### Environment at handoff
- **Android:** size reset, font 1.0, night mode off, app theme Light, `adb reverse` removed.
- **MOCK:** stopped. Its in-memory "D2Test" account is gone.
- **Auth emulator:** never started. iPhone 16 untouched.

### Not built from the reference sheet (owner to decide; report §6)
- a Transaction details screen (new route);
- a full-screen delete confirmation;
- icon / right-aligned-amount rows;
- the header "See all";
- outlined chips.

### Next
1. Owner review of D2 → commit on request.
2. Phase D3: save-success confirmation; transaction details if approved.
3. Phase E: QA, screen readers, the iOS runtime pass.
4. Phase 5D.

## D1-1-CLOSURE-2026-10-13. Portfolio Phase D1.1 closure (committed locally)

### Repository state
- Branch `main`. HEAD is **"Close Portfolio Phase D1.1 transaction form polish with all-at-once field validation, type-switch error clearing, selected-security line, scoped Android keyboard resize and stacked large-text actions on Android and iOS"**: a local commit, **not pushed** (owner instruction: no push, no deploy). It sits on "Add Portfolio Phase D1 transaction entry with sectioned forms, native date pickers, field-level validation, keyboard navigation and read-only sample handling on Android and iOS" (`d86c0e5`, pushed),
  so `main` is one commit ahead of `origin/main`.
- This commit contains only D1.1:
  - shared Kotlin: `PortfolioFormRules.kt`, `PortfolioDisplay.kt`, `PortfolioForms.kt`, `PortfolioScene.kt`, plus `KeyboardResize.kt` and its
    `androidMain`/`iosMain`/`jvmMain` actuals;
  - iOS: `PortfolioForms.swift`;
  - tests: `PortfolioPhaseD11Test.kt`, `PortfolioPhaseD1Test.kt`;
  - docs: the D1.1 report, plan, this file, `project-status.md`, `CLAUDE.md`;
  - captures: `docs/design/portfolio_d1_1/`.

  Every path was staged explicitly. No backend-migration file was included.

### What was done
- **Owner approval.** The owner reviewed the D1.1 captures and report and approved the UI changes, subject to a final repository check.
- **Diff audit.** Every change belongs to D1.1. Unchanged: ledger, `PortfolioEngine`, decimals, API, entitlements, sample labelling, routes, five tabs.
- **Final sequential verification** (test tasks forced with `--rerun`):
  - core **472/0** (JVM) and **472/0** (iOS);
  - server **492/0** (3 skipped, pre-existing);
  - shared **190/0** (Android host) and **186/0** (iOS), with `PortfolioPhaseD11Test` 7/0 and `PortfolioPhaseD1Test` 8/0 on both;
  - `assembleDebug` OK;
  - default unsigned `xcodebuild` **BUILD SUCCEEDED**.
- **Behaviour re-confirmed** from tests and code (devices **not** re-driven): field-specific validation, type-switch error clearing
  (`retainedErrors` on both platforms), scoped keyboard resize (only `PortfolioEntryScene`), stacked large-text actions. Device evidence is the D1.1
  capture set. The 2.0× and iPhone 16 captures post-date the last source edit. The 1.0× keyboard captures pre-date the final reference-counting edit.
  See D1.1 report §8.
- **NOT VERIFIED** (unchanged):
  - a successful iOS save (the Auth emulator isn't running; the owner declined);
  - TalkBack, VoiceOver;
  - Android dark mode for the form;
  - iPhone SE (excluded), physical devices.

### Next
1. **Phase D2 — Transaction history.** The plan was submitted to the owner in this session and is **not implemented**. It needs Q4 (a new
   navigation destination inside Portfolio, not a tab) and the plan's open decisions approved first.
2. Phase E (QA, screen readers).
3. Phase 5D.

Push `main` only when the owner asks.

## HANDOFF-2026-10-13-END. End-of-session handoff — START HERE

### Repository state (verified with `git status` / `git log` / `git fetch` at handoff)
- Branch `main`, in sync with `origin/main`. HEAD: **"Add Portfolio Phase D1 transaction entry with sectioned forms, native date pickers, field-level
  validation, keyboard navigation and read-only sample handling on Android and iOS"** (`d86c0e5`, pushed).
- Commits this session (oldest first, all pushed), on top of `6a787a3` (Portfolio Phase C.1):

  | Commit | What | Doc |
  |---|---|---|
  | `0fecddb` | C.1 final verification: 11 UI fixes (F1–F11) | `docs/PORTFOLIO_UI_REFINEMENT_PHASE_C1.md` §8 |
  | `9a1b884` | C.1 cleanup: insight cards with Show details, currency on insight amounts, stacked metric spacing | same doc §9 |
  | `231a0a1` | **Not this session:** Ubuntu backend Phase B (MOCK sample company catalog). Made by the parallel "StockSteps Ubuntu backend migration" session | — |
  | `d86c0e5` | Portfolio Phase D1: transaction entry and forms | `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D1.md` |

- **Phase D1.1** (uncommitted at this handoff; since approved and committed locally, see §D1-1-CLOSURE-2026-10-13):
  - app code (verified in §D1-1-2026-10-13):
    - `app/shared/src/commonMain/.../presentation/portfolio/PortfolioFormRules.kt`, `PortfolioDisplay.kt`, `PortfolioForms.kt`, `PortfolioScene.kt`;
    - new `KeyboardResize.kt` (commonMain `expect`), with actuals in `androidMain/.../portfolio/KeyboardResize.android.kt`, `iosMain/.../KeyboardResize.ios.kt`
      and `jvmMain/.../KeyboardResize.jvm.kt`;
    - `app/iosApp/iosApp/PortfolioForms.swift`;
  - tests: new `app/shared/src/commonTest/.../portfolio/PortfolioPhaseD11Test.kt`; modified `PortfolioPhaseD1Test.kt`;
  - docs:
    - new `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D1_1.md`;
    - modified `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`, this file, `docs/project-status.md`, `CLAUDE.md` ("Current task" and two Portfolio form conventions);
  - captures: `docs/design/portfolio_d1_1/` (20 JPGs).
- A parallel local session works on the Ubuntu backend in this same checkout. Never stage its files with Portfolio work. Check `git status`
  and stage by explicit path.

### Current development objective
Global UI Refinement of the **Portfolio module** on Android (Compose) and iOS (SwiftUI), display layer only. Plan: `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`
(Phases A–E).
- **Done and committed:** A, B, B.1, C, C.1 (with closure and cleanup), D1.
- **D1.1:** done (committed locally later, see §D1-1-CLOSURE-2026-10-13).
- **Remaining:** D2 (Transactions destination) and E (QA, screen readers). Phase 5D (Learn, Daily Brief, Earnings) follows after Portfolio.

### Exact task at the end of this session
Phase D1.1 ("Transaction Form Polish & Regression Closure") is implemented, device-verified, documented and tested, and is **waiting for the owner's
review**. Nothing is half-done in code. D2 has not started.

### Work completed this session
1. **C.1 final verification (`0fecddb`).** Re-checked all 22 C.1 issues on Android 360 dp (1.0×/1.3×/2.0×) and iPhone 16 (Large/AX5), and fixed 11
   defects:
   - pill selectors for the Insights allocation and index selectors;
   - inset transaction dividers;
   - `StockTrendChart` takes horizontal drags only, so the page scrolls;
   - iOS Insights chart legend and axis at AX sizes;
   - iOS `StockSegmentedControl` stacks the segments when a label word can't fit;
   - a fixed-size "⋯" glyph, and others.
2. **C.1 cleanup (`9a1b884`):**
   - insight cards show the finding first, one sentence, and an accessible Show details (`PortfolioInsightCardDisplay`);
   - insight amounts carry the reporting currency ("CAD 4,128.79"; the engine is untouched);
   - wider gaps between stacked metric groups.
3. **D1 (`d86c0e5`):**
   - sectioned form (Transaction information / Amounts / More options);
   - Material and SwiftUI date pickers storing `YYYY-MM-DD` with no time-zone shift;
   - field-level errors from the existing rules, with iOS validating before sending;
   - hidden fields never submitted; keyboard Next/Done;
   - full-height Android edit sheet with Cancel (no route);
   - read-only sample explanation with "Go to My saved accounts";
   - iOS large-text pickers.
4. **D1.1** (committed locally after approval):
   - all validation problems at once (the unchanged `PortfolioEngine.validate` re-run with check-only stand-ins);
   - two-field messages split per field;
   - a type switch drops stale rule errors and the save banner (`retainedErrors`);
   - selected-security line under the symbol; outlined search action;
   - the "earlier returns" note only for opening positions;
   - Android keyboard resize scoped to the Add screen (`KeyboardResizesContent`, reference-counted, restores pan);
   - stacked Save/Cancel at large text.

### Important decisions (and why)
- **Validation never adds rules or changes values.** `PortfolioFormRules.check` only re-runs `PortfolioEngine.validate`. The stand-ins exist only inside
  the check, and the transaction submitted is exactly what was typed. Unknown engine messages stay form-level and verbatim.
- **Hidden fields are blanked before submit** (`visibleTransaction`), so a type switch can't send stale shares, price or FX.
- **No new routes:** Android edit is a `ModalBottomSheet`.
  - Not built because they need data or routes (owner approval): a review screen with a computed total (D-R7: the net amount is server-only), a
    success destination, and the Transactions list (D2/Q4).
- **Keyboard resize is scoped, not app-wide.** Other screens (Auth, Screener filters) still rely on the pan default. A manifest change would affect
  them all.
  - `Window.setSoftInputMode(0)` is ignored, so an "unspecified" original mode is restored as `ADJUST_PAN` (what the system resolved before; checked
    with `dumpsys window`).
- **Insight-card currency** comes from presenter metrics plus `InsightsView.currency`; the engine runs on the server and is unchanged.
- **The iPhone SE is excluded** by the owner (2026-10-12).

### Known bugs, blockers and risks
- **iOS saves can't be verified.** The cached iPhone 16 session launches with `--firebase-emulators`, so Auth points at `127.0.0.1:9099`. Nothing runs
  there, so every authenticated call fails ("Could not connect" / "Insights couldn't be loaded"). The owner declined to start the Auth emulator for
  D1.1.
- **The MOCK server keeps user data in memory** (`InMemoryUserDataStore`). Restarting it loses test accounts ("D1Test", "D11Test" are gone).
- The parallel backend session once changed server code under a running MOCK, causing a `NoClassDefFoundError` mid-test. Restart the MOCK if a request
  500s after server files change.
- Comma decimal separators aren't converted (the ledger accepts "." only); the form explains this next to the field.
- **Shared-component changes this session** reach other screens. Only Portfolio was re-checked:
  - `StockTrendChart` horizontal-only drag;
  - iOS `StockSegmentedControl` stacking;
  - `StockInsightCard(details)`;
  - `StockMetricGrid` stacked gap;
  - `StockTextField(imeAction, keyboardActions)`;
  - new `StockDateField`.
- **Out of Portfolio scope, seen and not fixed:**
  - the sign-in screen breaks "StockSteps?" and "Create account" mid-word on the iPhone SE at AX5;
  - the bottom tab isn't highlighted on Portfolio sub-destinations;
  - engine card text could still show raw amounts where a metric isn't one of the known money keys.
- **Driving the device for tests:** on Android, `KEYCODE_ESCAPE` (111) and Back (4) leave the form once the keyboard is already closed. Use the IME
  action or tap outside to close the keyboard.

### Build and test results
- **Verified (final sequential run after D1.1, clean test outputs, 2026-10-13):**
  - Gradle suite: BUILD SUCCESSFUL;
  - core: **472/0** (JVM) and **472/0** (iOS);
  - shared: **190/0** (Android host) and **186/0** (iOS);
  - `assembleDebug` OK;
  - default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` **BUILD SUCCEEDED**.
- **Device-verified (local MOCK only, approved):**
  - Android emulator-5554 at 360 dp, light, 1.0× and 2.0×: form layout, all-errors, type switch, server rejection, hidden fields on a real save, edit
    sheet, keyboard resize, selected security via search;
  - iPhone 16 (iOS 18.3), Large and AX5 dark: layout, all-errors, type switch, selected security, AX5 labels.
- **NOT VERIFIED:**
  - a successful iOS save (Auth emulator);
  - TalkBack, VoiceOver;
  - Android dark mode for the form;
  - iPhone SE (excluded);
  - physical devices;
  - `:server:test` (no server change this session).

### Device / environment state at handoff
- **Android:** size reset, font 1.0, night mode off, app Theme Light, `adb reverse` removed.
- **iPhone 16 (`A04A9C8B-…`):** Large/light; the ad-hoc signed MOCK build is installed.
- **iPhone SE iOS 18.3 (`4BFB2D70-…`):** shut down; it has the MOCK build installed, with no session.
- **MOCK server:** stopped. **Auth emulator:** never started.
- **Device-check recipe:**
  - `./gradlew :server:runMock`; `adb reverse tcp:8081 tcp:8081`; `adb shell wm size 1080x2400`;
  - iOS: `xcodebuild … CODE_SIGN_IDENTITY=- CODE_SIGNING_ALLOWED=YES STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081 build`, then
    `simctl install` and `simctl launch … --firebase-emulators`;
  - an Android test account is created in the app ("Create portfolio"). It lives only until the MOCK stops.
- **Approvals:** device checks need owner approval each phase. The Firebase Auth emulator needs separate approval.

### Requirements discussed but not implemented
- **Phase D2 (needs approval of the Transactions destination, Q4):**
  - "See all transactions" from the dashboard;
  - filter chips All · Buys · Sells · Dividends · Cash · Other (display filter of `state.transactions`, grouping in `PortfolioDisplay`);
  - empty-filter state;
  - dashboard preview of 3–5 rows plus a link;
  - success confirmation after save (plan item D7).
- **Not approved / not supported:** a review screen with an estimated total (new client calculation, D-R7); a success screen (new destination).
- **Phase E:**
  - TalkBack and VoiceOver passes; Android dark-mode form check;
  - re-capture other screens that use the changed shared components;
  - a measured iOS legend placement; the iPhone SE once un-excluded.
- **Open owner decisions:**
  - D1 (auto 1M history);
  - D2 (total-return % presenter field);
  - Q1 (save the reference sheet in the repo);
  - Q3 (no Portfolio sub-tabs);
  - Q4 (Transactions destination).

### Exact next steps (as written at this handoff; steps 1–2 are now done, see §D1-1-CLOSURE-2026-10-13)
1. `git status`, `git log -5 --oneline`. Expect `d86c0e5` at HEAD plus the uncommitted D1.1 files listed above, and possibly new commits from the
   backend session.
2. Ask the owner to review D1.1 (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_D1_1.md`, captures in `docs/design/portfolio_d1_1/`).
   - On approval, commit **only the D1.1 paths** (never the backend session's files), with a title like "Polish Portfolio transaction form with
     all-at-once field guidance, type-switch error cleanup, selected security line and scoped keyboard resize (Phase D1.1) on Android and iOS".
   - Update this file in the same commit, naming the commit and changing "uncommitted" to "committed".
3. Ask for D2 approval, including the Transactions destination route (Q4).
4. When approved, implement D2 per plan §Phase D (items D5/D6/D7):
   - Route = identity only; Scene acquires the presenter; Screen renders;
   - filter on existing `state.transactions` only;
   - copy in `PortfolioDisplay`; tests;
   - full sequential suite plus both builds; captures on the local MOCK with approval;
   - `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D2.md`.

## D1-1-2026-10-13. Portfolio Phase D1.1 (form polish, regression closure) — committed locally after owner approval (see §D1-1-CLOSURE-2026-10-13)

- Repository: `main`. HEAD: "Add Portfolio Phase D1 transaction entry with sectioned forms, native date pickers, field-level validation, keyboard navigation and read-only sample handling on Android and iOS" (`d86c0e5`, pushed).
- **D1.1 paths** (uncommitted when written; now in the D1.1 commit):
  - shared Kotlin: `PortfolioFormRules.kt`, `PortfolioDisplay.kt`, `PortfolioForms.kt`, `PortfolioScene.kt`, new `KeyboardResize.kt` with actuals in
    `androidMain`/`iosMain`/`jvmMain`;
  - iOS: `PortfolioForms.swift`;
  - tests: new `PortfolioPhaseD11Test.kt`, updated `PortfolioPhaseD1Test.kt`;
  - docs: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D1_1.md`, the plan, this file, `project-status.md`;
  - captures: `docs/design/portfolio_d1_1/` (20).
- Done:
  - all validation problems shown at once (unchanged `PortfolioEngine.validate`, re-run with check-only stand-ins);
  - two-field messages split per field;
  - type switch drops old rule errors and the rejection banner (device-verified);
  - hidden fields excluded (unit + device);
  - selected security named under the symbol; outlined search action;
  - the "earlier returns" note only for opening positions;
  - Android keyboard resize scoped to the Add screen (fixes the pan that slid the top bar under the status bar);
  - stacked Save/Cancel at large text.
- Validation:
  - core 472/0 (JVM, iOS), shared 190/0 (Android) and 186/0 (iOS);
  - `assembleDebug` OK; default unsigned `xcodebuild` BUILD SUCCEEDED;
  - devices: Android 360 dp 1.0×/2.0×, iPhone 16 Large/AX5.
- Unverified:
  - a successful iOS save (the Auth emulator on 127.0.0.1:9099 isn't running; the owner declined to start it);
  - TalkBack/VoiceOver; Android dark mode for the form; SE excluded.
- Next: owner review → commit on request → Phase D2 after approval of the Transactions destination (Q4).

## D1-2026-10-12. Portfolio Phase D1 (transaction entry and forms) — START HERE (committed and pushed)

- Repository: `main`. Current commit: **"Add Portfolio Phase D1 transaction entry with sectioned forms, native date pickers, field-level validation, keyboard navigation and read-only sample handling on Android and iOS"** (pushed), on top of `231a0a1` (Ubuntu backend Phase B, another session) and `9a1b884` (C.1 cleanup).
- **In that commit (D1):**
  - shared Kotlin: `PortfolioFormRules.kt`, `PortfolioDisplay.kt`, `PortfolioForms.kt`, `PortfolioScene.kt`, `PortfolioScreen.kt`, `StockTextField.kt`
    (+ `StockDateField`), `StockIcons.kt`, `IosAccountClient.kt`;
  - iOS: `PortfolioForms.swift`, `PortfolioScene.swift`, `PortfolioViewModel.swift`;
  - tests: `PortfolioPhaseD1Test.kt` (new), `PortfolioPresenterSaveTest.kt` (new, core), `PortfolioFormRulesTest.kt`;
  - docs: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D1.md` (new), the plan, this file, `docs/project-status.md`;
  - captures: `docs/design/portfolio_d1/` (16).
- **Not D1, from another local session ("StockSteps Ubuntu backend migration"), never modified here:**
  - `server/.../Application.kt`, `FixtureMarketDataSource.kt`, `SampleMarketData.kt`, `ScreenerService.kt`, `MockModeTest.kt`;
  - new `SampleCompanyCatalog.kt`, `fixtures/stocks/catalog.json`, `MockCatalogTest.kt`;
  - `docs/LOCAL_DEVELOPMENT_SERVER.md`.
  That session committed them itself as `231a0a1` ("Add a sample company catalog to the MOCK backend…"); they are not in the D1 commit.
- Done:
  - sectioned form (More options collapsed); currency in labels;
  - native date pickers (YYYY-MM-DD, no time-zone shift);
  - shared field-level validation (iOS now validates before sending);
  - hidden fields never submitted; keyboard Next/Done;
  - save failures kept on the form; Android edit as a full-height sheet;
  - read-only samples with "Go to My saved accounts";
  - iOS AX pickers wrap.
- Validation:
  - core 472/0 (JVM, iOS), shared 183/0 (Android) and 179/0 (iOS);
  - `assembleDebug` OK; default unsigned `xcodebuild` BUILD SUCCEEDED;
  - devices: Android 360 dp at 1.0×/2.0× with saves on the local MOCK; iPhone 16 Large/AX5 (the cached session is offline, so no successful iOS save).
- Not built (unsupported, see D1 §8): review/total, success screen, Transactions list (Phase D2), comma decimals.
- Devices restored (Android size/font reset, `adb reverse` removed; iPhone 16 Large/light); MOCK stopped (its in-memory test account "D1Test" is gone).
- Next: Phase D2 after approval of the Transactions destination (Q4).

## C1-CLEANUP-2026-10-12. Portfolio Phase C.1 final cleanup — START HERE (committed and pushed)

- Repository: `main`. Current commit: **"Finish Portfolio Phase C.1 cleanup with concise insight cards, expandable details, currency on insight amounts and clearer stacked metric spacing on Android and iOS"** (pushed), on top of `0fecddb` (C.1 closure). Working tree clean after it.
- Contains: this cleanup (5 owner review items, `docs/PORTFOLIO_UI_REFINEMENT_PHASE_C1.md` §9) and the pruned/new captures in `docs/design/portfolio_pc1v/` (30 files).
- Done:
  - insight cards: finding first, one summary sentence, accessible "Show details" (shared `PortfolioInsightCardDisplay`), icon on the category line at large text, iOS category line;
  - insight amounts carry the reporting currency from the presenter ("CAD 4,128.79"); the engine is untouched;
  - stacked metric groups `spacing.md` apart;
  - allocation selector and MOCK strip verified, no change needed.
- Validation:
  - core 471/0 (JVM, iOS), shared 175/0 (Android) and 171/0 (iOS);
  - `assembleDebug` OK;
  - default unsigned `xcodebuild` BUILD SUCCEEDED;
  - devices: Android 360 dp at 1.0×/2.0× dark, iPhone 16 Large/AX5;
  - SE excluded; screen readers NOT VERIFIED.
- Devices restored (Android size/font/night/theme Light, `adb reverse` removed; iPhone 16 Large/light); MOCK stopped.
- Next: Phase D after approval of its routes (`PortfolioEntryRoute(transactionId)`, the Transactions destination / Q4).

## C1-CLOSURE-2026-10-12. Portfolio Phase C.1 final verification and closure — START HERE (committed and pushed)

- Repository: `main`. Current commit: **"Close Portfolio Phase C.1 verification with pill selectors, inset transaction dividers, vertically scrollable trend charts and large-text chart and selector fixes on Android and iOS"** (pushed), on top of `6a787a3` (Phase C.1). It contains:
  - this closure pass: 6 code files, the Portfolio docs and 30 captures in `docs/design/portfolio_pc1v/`;
  - the earlier end-of-session handoff edits (`CLAUDE.md`, `docs/project-status.md`, §HANDOFF-2026-10-12-END).
  Working tree clean after it.
- Result: **READY TO CLOSE** (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_C1.md` §8).
  - All reported C.1 issues PASS on Android 360 dp (light 1.0×, light 1.3×, dark 2.0×) and on iPhone 16 (light Large, dark AX5 with live switching).
  - The iPhone SE was **excluded by the owner**.
  - TalkBack and VoiceOver are **NOT VERIFIED**.
- Fixed in this pass, UI only:
  - F1: Android learn rows get a chevron;
  - F2/F6: transaction dividers are inset on both platforms;
  - F3/F4: Insights allocation and index selectors use `StockPillSelector` on both platforms (Android broke words mid-word, iOS cut "NASDAQ Com…");
  - F5: `StockTrendChart` only takes horizontal drags, so vertical swipes on a chart scroll the page;
  - F7: spacing under the Insights meta line;
  - F8: fixed-size iOS "⋯" glyph;
  - F9: leading-aligned iOS scenario menu;
  - F10: iOS Insights chart legend and axis at AX sizes;
  - F11: iOS `StockSegmentedControl` stacks the segments when a label word can't fit (SE AX5 showed "My Portfol…").
- Validation (sequential, clean outputs):
  - core 471/0 (JVM and iOS);
  - shared 169/0 (Android host) and 165/0 (iOS);
  - `assembleDebug` OK;
  - default unsigned `xcodebuild` BUILD SUCCEEDED.
- Known issues and limits (§8.5):
  - the cached iOS session still shows "Insights couldn't be loaded";
  - the bottom tab isn't highlighted on Portfolio sub-destinations (app-wide);
  - engine card text shows raw amounts ("4128.79");
  - the holding row's spoken gain has no currency;
  - the sign-in screen breaks words mid-word on SE AX5 (out of scope).
- Devices restored; MOCK stopped; no Auth emulator, no sign-in.
- Next:
  - **Phase D** after approval of its routes (`PortfolioEntryRoute(transactionId)`, the Transactions destination / Q4);
  - Phase E keeps the screen-reader passes and the SE.

## HANDOFF-2026-10-12-END. End-of-session handoff — START HERE

### Repository state (verified with `git status` / `git log` at handoff)
- Branch `main`, clean working tree, in sync with `origin/main`.
- HEAD: **"Close Portfolio visual and accessibility pass with group stacking, grouped transaction rows, single headings and large-text insights fixes (Portfolio Phase C.1) on Android and iOS"** (`6a787a3`, pushed).
- This session's commits (all pushed, oldest first, on top of `f55fd90` Company Comparison QA):

  | Commit | Phase | Doc |
  |---|---|---|
  | `37d74cd` | P0 account-by-id fix + Phase A | `docs/PORTFOLIO_UI_REFINEMENT_PHASE_A.md` (+ `docs/PORTFOLIO_UI_AUDIT.md`, rewritten plan) |
  | `6041e8f` | Phase B overview card | `docs/PORTFOLIO_UI_REFINEMENT_PHASE_B.md` |
  | `6c1d76b` | Phase B.1 polish | `docs/PORTFOLIO_UI_REFINEMENT_PHASE_B1.md` |
  | `8a86e4e` | Phase C holdings/allocation/details/insights | `docs/PORTFOLIO_UI_REFINEMENT_PHASE_C.md` |
  | `6a787a3` | Phase C.1 visual/accessibility closure | `docs/PORTFOLIO_UI_REFINEMENT_PHASE_C1.md` |

- The handoff edits made at the very end of the session (this section, `docs/project-status.md` §0, `CLAUDE.md` "Current task") were committed later with the C.1 closure (§C1-CLOSURE-2026-10-12).

### Current development objective
Global UI Refinement of the **Portfolio module** on Android (Compose) and iOS (SwiftUI). This is display-layer work only: shared KMP display/layout
rules, reused design-system components, accessible large-text layouts, and light/dark parity. It changes **no** presenter contract, API, server,
ledger, calculation, entitlement, fixture or navigation route. Plan: `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md` (Phases A–E).

### Exact task at the end of this session
Phase C.1 ("Visual and Accessibility Closure") was implemented, device-checked, documented, committed and pushed on request. Nothing is half-done in
code. The next phase (**Phase D**) has not started and needs owner approval.

### Work completed this session (details in each phase doc)
1. **Audit + plan:** `docs/PORTFOLIO_UI_AUDIT.md` (A-F1…A-F20, D-R1…D-R10, Q1–Q7, reference-sheet comparison); `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`
   rewritten as Phases A–E.
2. **P0 A-F1:** the Android transaction form chose the account **by name**, so a same-named second account saved into the first. It now chooses by
   **id** (`PortfolioFormRules.accountOptions` / `resolveAccountId`).
3. **Phase A:**
   - readable type and category labels; holding-detail amounts with currency;
   - `StockButton` / `.stockPrimary` and `negativeText` in the forms; Android decimal keyboards;
   - shared `PortfolioDisplay` (copy) and `PortfolioFormRules` (fields, order, labels);
   - readable Insights "As of"; "Portfolio insights" naming.
4. **Phase B:**
   - one overview card from the shared `PortfolioOverviewDisplay`: total → today's change / Why? → all accounts → **Account value** history →
     metrics with currency codes (owner Q5) → notice + freshness;
   - history states NOT_REQUESTED (Show 1M) / LOADING / LOADED / FLAT / INSUFFICIENT / EMPTY / ERROR, still on demand;
   - sample scenarios in ⋯; `StockLayout.metricRowStacked`.
5. **Phase B.1:**
   - short sample banner + Details; grouped scenario sheet (`PortfolioScenarioGroups`);
   - account labels without a repeated currency; "Selected account total" vs "All accounts total";
   - chart note + ⓘ; `StockChartLayout.height(width)`;
   - disabled Add explained in samples; readable "Unavailable: …" codes;
   - iOS AX5 fixes; live Dynamic Type for `StockBanner` / `StockStateMessage` / `StockPriceChange`.
6. **Phase C:**
   - borderless Portfolio sections (per call site); Android briefcase / graduation-cap selector icons (`StockSegmentedControl(compactIcons)`);
   - holdings via `PortfolioHoldingsDisplay`: server weight "of account value", display-only sort menu (owner D3: optional, default order);
   - `StockAllocationDonut` + legend (`StockAllocationLayout`), only when `allocationDonutAllowed`, bars otherwise; By currency collapsed (owner Q6);
   - Holding details grid + `StockNavigationRow` actions, no account-wide cash;
   - opt-in `StockTrendChart(areaFill)`;
   - Insights design-system pass (`StockChip(enabled)`, `PortfolioInsightIcons`).
7. **Phase C.1:**
   - one stacking decision per group (metric grid, legends); structured vertical holding rows at large text;
   - grouped transaction rows (`PortfolioPresentation.transactionText`); one heading on Holding details and Portfolio insights;
   - Insights rows, contributors and allocation stack at large text; `StockInsightCard` icon above the text at large text, arrow only if tappable;
   - iOS status-bar backdrop on the Portfolio tab.

### Files created or modified this session (all committed)
- **Shared Kotlin (new):** `presentation/portfolio/` `PortfolioFormRules.kt`, `PortfolioDisplay.kt`, `PortfolioOverview.kt`,
  `PortfolioHoldingsDisplay.kt`, `PortfolioAllocationDisplay.kt`; `designsystem/components/StockAllocationDonut.kt`.
- **Shared Kotlin (modified):**
  - Portfolio: `PortfolioForms.kt`, `PortfolioScene.kt`, `PortfolioScreen.kt`, `PortfolioHistoryChart.kt`, `PortfolioInsightsScreen.kt`,
    `PortfolioPresentation.kt`;
  - navigation and labels: `presentation/AppNavigation.kt` (title strings only), `designsystem/StockLabels.kt` (FX acronym);
  - theme: `theme/Spacing.kt` (`StockLayout.metricRowStacked`), `theme/StockSemanticStyles.kt` (`StockChartLayout`, `StockAllocationLayout`);
  - components: `StockMetric.kt`, `StockChip.kt`, `StockSegmentedControl.kt`, `StockTrendChart.kt`, `StockInsightCard.kt`;
  - icons: `icons/StockIcons.kt` (Briefcase, School).
- **Tests (new):** `PortfolioFormRulesTest`, `PortfolioOverviewTest`, `PortfolioPhaseB1Test`, `PortfolioPhaseCTest`, `PortfolioPhaseC1Test`.
- **Tests (modified):** `PortfolioPresentationTest`, `StockIconsTest`, `DesignTokensTest`.
- **iOS (new):** `DesignSystem/StockAllocationDonut.swift`.
- **iOS (modified):** `PortfolioForms.swift`, `PortfolioScene.swift`, `PortfolioScreen.swift`, `PortfolioHistoryChart.swift`,
  `PortfolioInsightsScreen.swift`, `PortfolioInsightsScene.swift`, `DesignSystem/StockComponents.swift`, `StockControls.swift`,
  `StockStatusViews.swift`, `StockSettingsComponents.swift`.
- **Docs:** the six Portfolio docs above, this file, `docs/project-status.md`, root `PROJECT_HANDOFF.md`, `CLAUDE.md`.
- **Captures:** `docs/design/portfolio_pb/`, `portfolio_pb1/`, `portfolio_pc/`, `portfolio_pc1/`.

### Important decisions (and why)
- **Ids, never names**, for account selection (names aren't unique; P0).
- **Display only:**
  - every number is an existing exact-decimal presenter value; missing stays "CAD —";
  - no total-return % (D2 stays open; it would need an approved presenter field);
  - holding weight only from a single matching server allocation.
- **The chart is "Account value"**: it includes deposits, so it's never called performance. History stays **on demand** (D1), to avoid extra
  requests.
- **Donut only when `allocationDonutAllowed`**: negative or borrowed cash can exceed 100 %, which a ring can't show honestly; bars are the fallback.
- **Owner decisions applied:** Q5 currency on every metric; D3 optional sort, default order; Q6 By currency collapsed.
- **Borderless per call site** (`bordered = false`), not a global `StockCard` change, so other screens can't regress.
- **Large text:**
  - layouts stack, wrap or move icons instead of shrinking text;
  - one stacking decision per group;
  - non-breaking spaces keep "10 shares" / "CAD 75.00" together;
  - iOS badges never wrap, so badge text stays short.
- **Sample scenarios:** live in a grouped sheet behind ⋯; the read-only banner stays visible; new fixture ids land in "Other".

### Known bugs, blockers and risks
- **NOT VERIFIED anywhere:** TalkBack navigation, VoiceOver, physical devices, iPhone SE (not approved in C / C.1).
- **iOS Learn-row hyphenation fix (C.1 #15):** in code, not re-captured.
- **iOS cached test session:**
  - the iPhone 16 simulator restores an old "Test account" session that is offline against the local MOCK;
  - its own Insights shows "Insights couldn't be loaded" (token state, not UI);
  - sample scenarios work. Reported, not fixed.
- **Shared-component changes reach other screens; only Portfolio was re-captured:**
  - `StockMetricGrid(rowsWhenNarrow)` group stacking;
  - `StockInsightCard` large-text layout;
  - live Dynamic Type in `StockBanner` / `StockStateMessage` / `StockPriceChange`.
- **iOS legend placement** uses a text-size threshold (Android measures).
- **Insights Retry** uses the full `refresh()`; no history-only reload exists.
- **Period change** briefly clears the report (presenter behaviour, unchanged).
- **Unchanged from earlier:**
  - LAN backend redeploy pending approval; Cloud Run deferred;
  - the default `xcodebuild` bakes the LAN MOCK URL.

### Build and test results
- **Verified (final sequential run after C.1, clean outputs):**
  - core JVM **471/0**, core iOS **471/0**;
  - shared Android host **169/0**, shared iOS **165/0**;
  - `:app:androidApp:assembleDebug` OK;
  - default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` **BUILD SUCCEEDED**.
- **Device-verified, local MOCK only:**
  - Android emulator-5554 at 360 dp: light/dark, 1.0× / 1.3× / 2.0×;
  - iPhone 16 (iOS 18.3): light Large and dark AX5 with live switching, data-backed through the cached session's sample scenarios.
- **Not run:**
  - `:server:test` (no server change);
  - TalkBack, VoiceOver, iPhone SE, physical devices.

### Device/environment notes (state at handoff)
- **Android:** emulator restored (size reset, font scale 1.0, app Theme Light, `adb reverse` removed).
- **iPhone 16 (`A04A9C8B-…`):** Large/light; it has the ad-hoc signed MOCK build installed (launch with `--firebase-emulators`).
- **MOCK and Auth emulator:** both stopped.
- **Device-check recipe:**
  - `./gradlew :server:runMock`; `adb reverse tcp:8081 tcp:8081`; `adb shell wm size 1080x2400` for 360 dp;
  - iOS: `xcodebuild … CODE_SIGN_IDENTITY=- CODE_SIGNING_ALLOWED=YES STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081 build`;
  - Portfolio ⋯ → "Sample scenario…" picks fixtures.
- **Firebase Auth emulator:** available from `firebase/node_modules/.bin/firebase` (installed earlier, no install needed), but only with owner
  approval.

### Requirements discussed but not implemented
- **Phase D:** date pickers; search-first form with Advanced; iOS client validation (`PortfolioEngine.validate`) with field-level errors; Android edit
  as a full-screen form with Cancel (needs a Route argument — approval); a **Transactions destination** inside Portfolio with display filters (owner
  Q4 — approval); success confirmation after save.
- **Phase E:** iPhone SE and screen-reader passes; Android chart ⓘ row height; a measured iOS legend placement; re-capture of other screens using the
  changed shared components.
- **Open owner decisions:** D1 (auto 1M history), D2 (total-return % presenter field), Q1 (save the new reference sheet in the repo), Q3 (no Portfolio
  sub-tabs — recommended), Q4 (Transactions destination), Q7 (done in B.1 as display-only dedupe).
- **Not adopted** (advice or unsupported data): "Consider adding more ETFs", "30% guideline", total-gain %, cash % row, asset-class/sector donut on the
  free dashboard (StockSteps+ analytics).
- **Queued after Portfolio:** Phase 5D (Learn, Daily Brief, Earnings — Earnings read-only audit first); the global investment disclaimer stays in
  the backlog.

### Exact next steps
1. `git status`, `git log -5 --oneline` (superseded: see §C1-CLOSURE-2026-10-12 for the current HEAD).
2. Ask the owner to approve **Portfolio Phase D** (scope in `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md` §Phase D), including the new routes
   (`PortfolioEntryRoute(transactionId)` for Android edit, a Transactions destination) and Q4.
3. Once approved, in order:
   - capture the forms "before" on local MOCK (approval needed);
   - implement on both platforms: the form layout from `PortfolioFormRules.redesignOrder` with Advanced collapsed, then date pickers writing
     `YYYY-MM-DD`, then iOS client validation, then field-level errors, then Android edit with Cancel, then the Transactions screen with display
     filters;
   - tests;
   - the full sequential suite + both builds;
   - captures;
   - `docs/PORTFOLIO_UI_REFINEMENT_PHASE_D.md`.
4. Keep the rules: ids for accounts, `PortfolioDisplay` for copy, no new validation rules, no calculation or API change.

## PORTFOLIO-PC1-2026-10-12. Portfolio Phase C.1 closure — START HERE (committed and pushed)

- Repository: `main`. Current commit: **"Close Portfolio visual and accessibility pass with group stacking, grouped transaction rows, single headings and large-text insights fixes (Portfolio Phase C.1) on Android and iOS"** (pushed), on top of `8a86e4e` (Phase C). Working tree clean after it.
- Done (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_C1.md`, 22 issues FIXED/ACCEPTED/DEFERRED):
  - group stacking for metric grids and allocation legends;
  - structured vertical holding rows; grouped transaction rows (`PortfolioPresentation.transactionText`);
  - one heading on Holding details and on Portfolio insights;
  - insights rows, contributors and allocation stack at large text; `StockInsightCard` icon above the text at large text;
  - iOS status-bar backdrop; scenario caption under the total.
- Validation:
  - core 471/0 (JVM, iOS), shared 169/0 (Android) and 165/0 (iOS);
  - `assembleDebug` OK;
  - iOS `xcodebuild` OK;
  - Android 360 dp (light 1.0×, dark 1.0×, dark 2.0×) and iPhone 16 (light Large, dark AX5): 36 captures in `docs/design/portfolio_pc1/`.
- **NOT RUN:** iPhone SE, TalkBack, VoiceOver. The iOS Learn-row hyphenation fix is in code but not re-captured.
- Next: Phase D after approval; commit only on request.

## PORTFOLIO-PC-2026-10-12. Portfolio Phase C — START HERE (committed and pushed; VERIFICATION PARTIAL)

- Repository: `main`. Current commit: **"Refine Portfolio holdings, allocation donut, holding details and insights with borderless sections and matching selector icons (Portfolio Phase C) on Android and iOS"** (pushed), on top of `6c1d76b` (B.1). Working tree clean after it.
- Done (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_C.md`):
  - borderless Portfolio sections (per call site);
  - Android briefcase/graduation-cap selector icons (`StockSegmentedControl(compactIcons)` on both platforms);
  - holdings via shared `PortfolioHoldingsDisplay`: server weight "of account value", display-only sort menu;
  - `StockAllocationDonut` + legend (shared `StockAllocationLayout`), only when `allocationDonutAllowed`, bars otherwise; By currency collapsed;
  - Holding details: metric grid + `StockNavigationRow` actions, no account-wide cash;
  - chart `areaFill` (opt-in);
  - Insights pass: `StockChip(enabled)`, icons, colours, readable dates/scenarios;
  - NBSP wrapping fixes.
- Validation:
  - core 471/0 (JVM, iOS), shared 166/0 (Android) and 162/0 (iOS);
  - `assembleDebug` OK;
  - iOS `xcodebuild` OK;
  - Android 360 dp (light/dark, 1.0/1.3/2.0×) and iPhone 16 (light Large, dark AX5): 36 captures in `docs/design/portfolio_pc/`.
- **NOT RUN:** iPhone SE, TalkBack, VoiceOver, physical devices.
- Known: the cached iOS test account's Insights fails to load (offline session token; pre-existing, not Phase C).
- Next: Phase D (forms and Transactions) after approval; commit only on request.

## PORTFOLIO-PB1-2026-10-12. Portfolio Phase B.1 polish — START HERE (committed and pushed)

- Repository: `main`. Current commit: **"Polish the Portfolio overview with a short sample banner, a grouped scenario sheet, account labels without repeated currency and AX5 fixes (Portfolio Phase B.1) on Android and iOS"** (pushed), on top of `6041e8f` (Phase B). Working tree clean after it.
- Done (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_B1.md`):
  - short sample banner with Details;
  - grouped, scrollable scenario sheet from ⋯ (`PortfolioScenarioGroups`);
  - account labels without the repeated currency;
  - "Selected account total" vs "All accounts total";
  - one-line chart note with ⓘ;
  - width-based chart height (`StockChartLayout`);
  - an explanation under the disabled Add transaction in samples;
  - readable "Unavailable: …" notice;
  - iOS AX5 fixes: account selector stacking, short badge + scenario caption, capped axis labels with dates under the plot;
  - live Dynamic Type for iOS `StockBanner` / `StockStateMessage` / `StockPriceChange`.
- Validation:
  - core 471/0 (JVM, iOS), shared 156/0 (Android) and 152/0 (iOS);
  - `assembleDebug` OK;
  - iOS `xcodebuild` OK;
  - device checks on Android 360 dp (light/dark, 1.0/1.3/2.0×) and iPhone 16 (Large/AX5 live, light/dark, data-backed via the cached test
    session): 29 captures in `docs/design/portfolio_pb1/`.
- **NOT RUN:** iPhone SE, TalkBack, VoiceOver, physical devices.
- Auth emulator: started from `firebase/node_modules` (already present); a throwaway `@example.test` account was created but not needed. No
  credentials were typed into the app.
- Devices restored; MOCK and the emulator are stopped. The iPhone 16 keeps the ad-hoc MOCK build.
- Next: Phase C after approval (D3, Q6; Q1/Q3/Q4 open); commit only on request.

## PORTFOLIO-PB-2026-10-12. Portfolio Phase B overview card — START HERE (committed and pushed)

- Repository: `main`. Current commit: **"Add the Portfolio overview card with on-demand account value history, currency on every metric and the sample selector in the menu (Portfolio Phase B) on Android and iOS"** (pushed), on top of `37d74cd` (Phase A). Working tree clean after it.
- Done (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_B.md`):
  - one overview card on both platforms: account → total → today's change / Why? → all accounts → Account value history → metrics with
    currency codes (owner Q5) → notice + freshness;
  - "+ Add transaction" below the card;
  - MOCK sample scenarios moved into the ⋯ menu (banner and badge stay visible);
  - shared `PortfolioOverviewDisplay` (states NOT_REQUESTED / LOADING / LOADED / FLAT / INSUFFICIENT / EMPTY / ERROR, Show 1M, flat summary,
    high/low labels, spoken text);
  - new shared rule `StockLayout.metricRowStacked` (no mid-word metric labels at large text, both platforms);
  - `StockLabels` acronym FX;
  - the iOS chart without point marks.
- No presenter, API, server, calculation, route or entitlement change.
- Validation (final sequential run):
  - core 471/0 (JVM, iOS), shared 150/0 (Android) and 146/0 (iOS);
  - `assembleDebug` OK;
  - iOS `xcodebuild` BUILD SUCCEEDED;
  - Android emulator 360 dp light/dark 1.0/1.3/2.0× on local MOCK and iOS guest screens: 24 captures in `docs/design/portfolio_pb/`.
- **NOT RUN:**
  - iOS overview with data: sample scenarios need sign-in, which is only allowed via the Firebase Auth emulator, and its CLI isn't installed;
  - iPhone SE captures, TalkBack, VoiceOver.
- Devices restored:
  - Android: emulator size reset, font scale 1.0, app Theme Light, `adb reverse` removed;
  - iOS: iPhone 16 back to Large/light;
  - the MOCK server is stopped.
- Next: Phase C after approval (decisions D3, Q6; Q1/Q3/Q4/Q7 open); commit only on request.

## PORTFOLIO-PA-2026-10-12. Portfolio audit, P0 account fix and Phase A — START HERE (committed and pushed)

- Repository: `main`. Current commit: **"Fix Portfolio account selection by id and add Phase A shared form rules, readable labels, formatted holding amounts and consistent copy on Android and iOS"** (pushed), on top of `f55fd90` (Company Comparison QA closure). It contains everything below;
  the working tree is clean after it.
- Audit pass (docs only): `docs/PORTFOLIO_UI_AUDIT.md` (new: inventory, A-F1…A-F20, D-R1…D-R10, Q1–Q7, comparison with the owner's new reference
  sheet, which isn't in the repo yet, see Q1) and `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md` (rewritten as Phases A–E; the old version is in `92a6d6a`).
- **Stage 1, P0 A-F1 fixed**: the Android transaction form chose the account by display name, so the second of two same-named accounts saved into the first.
  It now uses id-keyed `StockSelectField` options from `PortfolioFormRules.accountOptions` / `resolveAccountId` (a deleted choice is never replaced
  silently), and the submitted transaction is built by `PortfolioFormRules.transaction`. 11 tests.
- **Phase A A1–A9 done** (`docs/PORTFOLIO_UI_REFINEMENT_PHASE_A.md`):
  - readable type and category labels on both platforms;
  - holding-detail Realized and Dividends formatted with currency;
  - `StockButton` / `.stockPrimary` and `negativeText` in the forms;
  - Android `StockTextField` with decimal keyboards;
  - shared `PortfolioDisplay` copy and shared `PortfolioFormRules` field visibility, order and labels;
  - readable Insights "As of";
  - "Portfolio insights" naming everywhere;
  - also: the chart is titled "Account value", with a deposits note (moved forward from B2; deviation §8.1 of the phase doc).
- No API, presenter-contract, calculation, ledger, persistence, route, entitlement or fixture change.
- Validation (sequential, clean outputs):
  - core JVM **471/0**, core iOS **471/0**, shared Android **138/0**, shared iOS **134/0**;
  - `assembleDebug` OK;
  - default iOS `xcodebuild … CODE_SIGNING_ALLOWED=NO build` **BUILD SUCCEEDED**.
  - **Not run**: `:server:test` (no server change), and all device, emulator and simulator checks, captures, TalkBack and VoiceOver (no approval).
- Known limits:
  - the Android edit form is still an `AlertDialog` without Cancel, and dates are still typed;
  - iOS still validates only on the server (all Phase D);
  - the new field rows inside the Android edit dialog and the `.stockPrimary` rows inside iOS `Form`s are unverified on device.
- Next, each only after owner approval:
  - before/after captures on local MOCK;
  - **Phase B** (overview card), with decisions Q1–Q7 and D1–D3 from the plan;
  - commit only on request (update root `PROJECT_HANDOFF.md` in the same commit).

## HANDOFF-2026-10-12. End-of-session handoff — START HERE

### Repository state (verified with `git status` / `git log` at handoff)
- Branch `main`. Current commit: **"Finish Company Comparison QA with a two-column chart selection summary, scannable chart limitations, heading fit at large text and live Dynamic Type for buttons on Android and iOS"** (pushed), on top of `85e24a0` "Close Company Comparison accessibility pass with an icon-led insights card,
  collapsible chart help and sources, readable tab labels at large text and live Dynamic Type fixes on Android and iOS". Working tree clean after it.
- **That commit contains the "final polish & QA closure" pass:**
  - modified: `core/.../screener/ComparisonDisplay.kt`, `core/.../screener/ComparisonDisplayTest.kt`,
    `app/shared/.../designsystem/components/StockTrendChart.kt`, `app/shared/.../presentation/screener/ComparisonScreen.kt`,
    `app/shared/.../theme/StockSemanticStyles.kt`, `app/shared/.../theme/ResponsiveLayoutRulesTest.kt`,
    `app/iosApp/iosApp/ScreenerScenes.swift`, `app/iosApp/iosApp/DesignSystem/StockComponents.swift`, `app/iosApp/iosApp/DesignSystem/StockControls.swift`;
  - new: `docs/GLOBAL_UI_REFINEMENT_PHASE5C_QA_CLOSURE.md`, `docs/design/phase5c_qa/` (18 captures);
  - plus this handoff, root `PROJECT_HANDOFF.md`, `docs/project-status.md` and `CLAUDE.md`.
- Commits made this session (all pushed, oldest first): `42c749d` 5C.1 handoff docs · `92a6d6a` 5C.1 closure + Portfolio audit + P-A · `53c0044`
  Comparison final polish · `85e24a0` accessibility closure + insights card · "Finish Company Comparison QA with a two-column chart selection summary, scannable chart limitations, heading fit at large text and live Dynamic Type for buttons on Android and iOS" (QA closure).

### Current development objective
Global UI Refinement (`docs/GLOBAL_UI_REFINEMENT_PLAN.md`): screen-by-screen visual/UX/accessibility polish on Android (Compose) and iOS (SwiftUI)
with shared KMP display/layout rules and **no** data, API, presenter-contract, calculation, entitlement or navigation changes. This session finished
the **Company Comparison** screen (plan step 5) through several owner briefs; it stands at **PASS WITH CONDITIONS** (screen readers on physical
devices outstanding). Portfolio UI refinement has an approved-for-review audit + Phase P-A; P-B is not started.

### Exact task at the end of this session
"Company Comparison — Final Polish & QA Closure" brief: implemented, tested on devices, documented in `docs/GLOBAL_UI_REFINEMENT_PHASE5C_QA_CLOSURE.md`
(verdict PASS WITH CONDITIONS), committed and pushed on the owner's request. Nothing is half-done in the code.

### Work completed this session (details in each phase doc)
1. `92a6d6a` — 5C.1 closure (`docs/GLOBAL_UI_REFINEMENT_PHASE5C_1_CLOSURE.md`): pinned company header only from the first open section with
   unlabelled columns (`ComparisonDisplay.companyHeaderSection`), compact header, tighter spacing, iOS ⓘ centred. Portfolio audit
   (`docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`: 16 issues, components C1–C7, phases P-A…P-E, decisions D1–D3) and **P-A**
   (`docs/PORTFOLIO_REFINEMENT_PHASE_PA.md`: baseline from Phase 3.1 captures, rules R1–R8, `PortfolioDisplayRules.allocationDonutAllowed` /
   `sortHoldings` + `PortfolioHoldingSort` — not yet used by any screen).
2. `53c0044` — final polish (`docs/GLOBAL_UI_REFINEMENT_PHASE5C_FINAL_POLISH.md`): `StockChartSeriesStyle` (shared colour + dash per series, drawn
   legend swatches), quieter MOCK strip (`StockSemanticStyles.sampleStrip`; iOS strip resolved from `dynamicTypeSize`, capped at AX1), section period
   inside the header, More-metrics note, first "What the numbers show" condensation, AX5 fixes (chart dates, selection title).
3. `85e24a0` — accessibility closure + insights card (`docs/GLOBAL_UI_REFINEMENT_PHASE5C_ACCESSIBILITY_CLOSURE.md`): icon-led insights card
   (`ObservationIcon`, `StockSemanticStyles.insightAccent`, three primary insights + compact "Additional observations" panel + footer), collapsible
   "How to read this chart" and "Sources & methodology" (`ComparisonNotesDisplay`), Android tab labels never ellipsized
   (`StockNavigationLayout.labelScale`), iOS y-axis capped at AX sizes, `StockStepsTheme.font` resolving the current content-size category, history
   chart on shared styles, AX5 header fixes; 14 captures `docs/design/phase5c_a11y/`.
4. "Finish Company Comparison QA with a two-column chart selection summary, scannable chart limitations, heading fit at large text and live Dynamic Type for buttons on Android and iOS" — final polish & QA closure (`docs/GLOBAL_UI_REFINEMENT_PHASE5C_QA_CLOSURE.md`):
   - chart selected-date summary: date line + two-column grid of swatch · ticker · indexed value (one column at accessibility text; "No data" when
     missing) — core `ComparisonChartDisplay` (`selection`, `defaultIndex`, `selectionColumns`), Compose `StockTrendChart(selectionContent:)` +
     `ChartSelectionView`, SwiftUI `chartSelectionView`; iOS gained `chartXSelection` (press-and-drag), keeps the last day after release
     (`chartTouch` → `chartSelection`) and marks it with a rule line;
   - "Important limitations" split into two points (exact-sentence match only, else verbatim);
   - `ObservationItem.spoken` no longer doubles full stops (found via the Android accessibility tree);
   - iOS shared `StockButtonBody` depends on `dynamicTypeSize` (button labels refresh on live Dynamic Type changes);
   - new shared rule `StockHeadingFit.scale` (+ test): a heading shrinks only when one word can't fit its line (8 % measuring margin, floor 0.6) —
     applied to iOS section headers (widths from the header row and chevron, never from the title itself) and the "What can we learn…" heading,
     via `StockStepsTheme.fittedHeadingFont`.

### Files created or modified this session
- Core: `screener/ComparisonDisplay.kt` (companyHeaderSection, ObservationIcon/Item/View, ComparisonObservationsDisplay, ComparisonNotesDisplay,
  ComparisonChartDisplay, More-metrics/section labels), `screener/ComparisonDisplayTest.kt`.
- Shared app: `theme/StockSemanticStyles.kt` (sampleStrip, insightAccent, StockChartSeriesStyle, StockNavigationLayout, StockHeadingFit),
  `theme/ResponsiveLayoutRulesTest.kt`, `designsystem/components/StockTrendChart.kt`, `StockSampleDataBanner.kt`, `StockBottomNavigation.kt`,
  `designsystem/icons/StockIcons.kt` (BarChart, Speed, Layers, Percent), `presentation/AppNavigation.kt` (tab labels),
  `presentation/screener/ComparisonScreen.kt`, `ComparisonHistoryUi.kt`, `presentation/portfolio/PortfolioPresentation.kt` (P-A rules),
  tests `presentation/portfolio/PortfolioPresentationTest.kt`, `designsystem/StockIconsTest.kt` (new).
- iOS: `ScreenerScenes.swift`, `ComparisonHistoryViews.swift`, `ContentView.swift` (MOCK strip), `Theme.swift` (font traits),
  `DesignSystem/StockComponents.swift` (chart swatch/styles, fittedHeadingFont, live-type deps), `DesignSystem/StockControls.swift`.
- Docs: `docs/GLOBAL_UI_REFINEMENT_PHASE5C_1_CLOSURE.md`, `…_PHASE5C_FINAL_POLISH.md`, `…_PHASE5C_ACCESSIBILITY_CLOSURE.md`, `…_PHASE5C_QA_CLOSURE.md`,
  `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md`, `docs/PORTFOLIO_REFINEMENT_PHASE_PA.md`; captures `docs/design/phase5c_1/closure/`, `phase5c_final/`,
  `phase5c_a11y/`, `phase5c_qa/`; handoff/status docs and `CLAUDE.md`.

### Important decisions (and why)
- **Presentation-only transforms over server text**: insights, chart help and sources are reorganised client-side from the server's fixed sentences
  (known openings / exact matches; unknown text kept verbatim). No API or `ComparisonEngine` change was allowed; nothing is computed or invented.
- **No "highest/lowest" winner wording** in insights (engine states ranges by design); valuation icon is a price tag (no balance-scale vector).
- **Tab labels**: grow with font scale until the widest no longer fits, then stop (never below 1.0, never ellipsized; icons-only with spoken names
  as last resort) — same trade-off as the iOS system tab bar.
- **Heading fit, not heading caps**: capping section titles at AX3 inverted the hierarchy; shrinking only the heading whose word can't fit keeps it.
  Measuring the title's own column caused a feedback loop (titles collapsed to the floor) — measure font-independent widths instead.
- **History loads on demand** (Portfolio R2), **no UI-computed overall-return %** (R3), donut only when `allocationDonutAllowed` (R4).
- iOS live Dynamic Type: `StockStepsTheme.font` resolves against `UIApplication.shared.preferredContentSizeCategory`; a view must **depend on
  `dynamicTypeSize`** to re-render (done for Compare, the shared section header, navigation row and buttons; open `ForEach` groups use `.id(typeSize)`).

### Known bugs, blockers and risks
- **NOT VERIFIED**: TalkBack live navigation (TalkBack runs on the emulator but injected gestures / Alt+→ don't move its focus; speech isn't logged
  after TTS init) — only the accessibility tree (labels, order) was checked; **VoiceOver** (not in Simulator); **physical devices**.
- Android has no heading-fit rule (no mid-word break seen at 360 dp × 2.0×); iOS chart selection is press-and-drag (system), Android also tap.
- The "What can we learn…" heading measures its own width (no feedback observed; the section-header approach is safer if issues appear).
- Other screens' custom iOS views built with `StockStepsTheme.font` may not refresh on a live Dynamic Type change until reopened.
- MOCK fixture artifact (quarterly = annual revenue growth) unchanged by request. LAN backend redeploy still pending approval; Cloud Run deferred.
- The default `xcodebuild` bakes the LAN MOCK URL; device checks need `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` (check Info.plist). A fresh
  simulator install defaults to REAL (`localhost:8080`): `xcrun simctl spawn <udid> defaults write org.example.stocksteps stocksteps.backendEnvironment MOCK`.
  The app needs iOS ≥ 18.2 — use the iPhone SE **iOS 27** simulator `4BFB2D70-86EC-4B87-8BB9-CA49C20465B8` (the iOS 17.5 SE can't install it).

### Build and test results
- **Verified this session (latest run, outputs cleaned, after all uncommitted changes)**: core JVM **471/0**, core iOS **471/0**, shared Android host
  **117/0**, shared iOS **113/0**, `:app:androidApp:assembleDebug` OK, default iOS `xcodebuild` **BUILD SUCCEEDED** (no warnings in changed files).
- **Device-verified (local MOCK only)**: Android emulator-5554 at 360 dp light 1.0×/1.3×, dark 2.0× (2 and 4 companies); iPhone 16 (iOS 18.3) light/dark
  Large and AX5 with live switching; iPhone SE (iOS 27) light/dark Large and AX5 live, 2 and 3 companies. Captures in the four `phase5c*` folders.
- **Not verified**: TalkBack navigation, VoiceOver, physical devices, `:server:test` (no server change), network-level request counting for
  expand/collapse (code review only — MOCK server doesn't log per-request).

### Device/environment notes (state at handoff)
- Emulator restored: size reset (1280×2856), font scale 1.0, night mode yes, TalkBack off, app Theme **Light**, `adb reverse` removed. The owner granted
  Android Accessibility Suite's notification permission (TalkBack starts without a prompt now).
- iPhone 16 `A04A9C8B-…` Light/Large (booted); iPhone SE iOS 27 shut down (Light/Large; MOCK backend preference set in its app copy); MOCK stopped.
- Helper flow used for devices: `./gradlew :server:runMock`, `adb reverse tcp:8081 tcp:8081`, `wm size 1080x2400` for 360 dp; iOS guest flow: sign-in
  → scroll → "Continue as guest" → Markets → Research tools → Compare Companies → "Apple and Microsoft" → "Add a company".

### Requirements discussed but not implemented
- Portfolio **P-B** (Overview Card; exact scope in `docs/PORTFOLIO_REFINEMENT_PHASE_PA.md` §5) and later P-C…P-E; decisions D2 (overall return %
  needs an approved presenter field) and D3 (holdings sort control) are the owner's.
- Phase 5D (Learn, Daily Brief, Earnings; Earnings read-only audit first) — queued.
- The separate global educational/investment disclaimer is a **deferred backlog item** (owner said not to implement it yet).
- Screen-reader passes on physical devices for every refined screen.

### Exact next steps
1. `git status`, `git log -5 --oneline` (expect "Finish Company Comparison QA with a two-column chart selection summary, scannable chart limitations, heading fit at large text and live Dynamic Type for buttons on Android and iOS" at HEAD, clean tree).
2. Company Comparison is PASS WITH CONDITIONS; remaining: TalkBack navigation and VoiceOver on physical devices (owner) and owner visual sign-off.
3. Then the owner's choice (approval required): **Portfolio P-B — Overview Card** (start with approved before-captures, `PortfolioDisplay` copy +
   tests, `StockOverviewCard` on both platforms per P-A §5) or **Phase 5D** (Earnings read-only audit).
4. Run sequentially before any commit: clean outputs → `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest
   :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` → default `xcodebuild … CODE_SIGNING_ALLOWED=NO build`.

## A11Y-CLOSURE-5C. Company Comparison accessibility closure + insights card (2026-10-12, committed and pushed)

- Repository: `main`. Commit: "Close Company Comparison accessibility pass with an icon-led insights card, collapsible chart help and sources, readable tab labels at large text and live Dynamic Type fixes on Android and iOS" (pushed), on top of `53c0044` (Comparison final polish).
- Done (`docs/GLOBAL_UI_REFINEMENT_PHASE5C_ACCESSIBILITY_CLOSURE.md`): Android tab labels via shared `StockNavigationLayout.labelScale` (no ellipsis;
  icons-only fallback with spoken names); insights card with category icons (`ObservationIcon`, `StockSemanticStyles.insightAccent`), three primary
  insights + compact "Additional observations"; `ComparisonNotesDisplay` (chart help and "Sources & methodology", collapsed); iOS y-axis capped at AX
  sizes, live Dynamic Type (`StockStepsTheme.font` traits + `dynamicTypeSize` dependencies), history chart on shared series styles; AX5 header fixes.
- Validation: core JVM 470/0, core iOS 470/0, shared Android 116/0, shared iOS 112/0, `assembleDebug` OK, iOS `xcodebuild` BUILD SUCCEEDED; 14 device
  captures `docs/design/phase5c_a11y/` (Android 360 dp light + 2.0×, iPhone 16 dark + AX5 live change).
- Verdict: PASS WITH CONDITIONS (provisional) — remaining: Android dark/1.3×/two companies, iPhone SE, iOS light captures; TalkBack/VoiceOver/physical
  devices NOT TESTED; owner sign-off against the insights reference.
- Next: owner's choice — finish the remaining Comparison checks, Portfolio P-B, or Phase 5D (each only after approval).

## FINAL-POLISH-5C. Company Comparison final polish (2026-10-12, committed and pushed)

- Repository: `main`. Commit: "Polish Company Comparison with shared chart legend styles, concise What-the-numbers-show insights, a quieter sample strip and large-text fixes on Android and iOS" (pushed), on top of `92a6d6a` "Close Company Comparison Phase 5C.1 with section-based sticky header and
  spacing fixes, and add the Portfolio UI audit and Phase P-A display rules".
- Done (`docs/GLOBAL_UI_REFINEMENT_PHASE5C_FINAL_POLISH.md`): shared `StockChartSeriesStyle` (colour + dash per series, reference line) for Compose
  `StockTrendChart` and Swift Charts with drawn legend swatches; quieter sample strip (`StockSemanticStyles.sampleStrip`, iOS strip capped at the
  first accessibility size and following live text-size changes); section period inside the header and count stacked at large text; tighter metric
  spacing; More-metrics note; "What the numbers show" = 3 distinct insights + "View all N insights" via `ComparisonObservationsDisplay` (presentation
  only); AX5 fixes for chart dates and the selection title (also Android 2.0×).
- Validation: core JVM 468/0, core iOS 468/0, shared Android 114/0, shared iOS 110/0, `assembleDebug` OK, iOS `xcodebuild` BUILD SUCCEEDED; device
  checks on local MOCK (Android 360 dp dark 1.0×/1.3×/2.0×, light 2 companies; iPhone 16 light/dark/AX5; iPhone SE) — captures
  `docs/design/phase5c_final/` (25).
- Limits: TalkBack/VoiceOver/physical devices NOT TESTED; Swift Charts y-axis labels very large at AX5; live Dynamic Type changes need the screen
  rebuilt; Android tab labels truncate at 2.0×; iOS history chart keeps automatic colours.
- Next: owner's choice — Portfolio P-B (Overview Card, scope in `docs/PORTFOLIO_REFINEMENT_PHASE_PA.md` §5) or Phase 5D, each only after approval.

## CLOSURE-5C1-PA. Phase 5C.1 closure + Portfolio audit and P-A (2026-10-11/12, committed and pushed)

- Repository: `main`. Current commit: "Close Company Comparison Phase 5C.1 with section-based sticky header and spacing fixes, and add the Portfolio UI audit and Phase P-A display rules" (pushed), on top of `42c749d` "Add end-of-session handoff after Global UI Refinement Phase 5C.1 and
  point to the Phase 5D Earnings audit" (pushed with it). Everything below is in that commit.
- **5C.1 closure** (`docs/GLOBAL_UI_REFINEMENT_PHASE5C_1_CLOSURE.md`): shared `ComparisonDisplay.companyHeaderSection` (pinned company header
  only from the first open section with unlabelled columns; none over bar rows), compact header, tighter notes → first-metric spacing, iOS ⓘ
  centred; device checks on local MOCK (Android 360 dp light/dark 1.0×, dark 1.3×; iPhone 16 light/dark Large + AX5; iPhone SE iOS 27);
  captures `docs/design/phase5c_1/closure/`. Verdict **PASS WITH CONDITIONS** (TalkBack/VoiceOver/physical devices NOT TESTED).
- **Portfolio audit** `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md` (16 prioritised issues, components C1–C7, phases P-A…P-E, decisions D1–D3) and
  **P-A** `docs/PORTFOLIO_REFINEMENT_PHASE_PA.md` (baseline from Phase 3.1 captures — no new Portfolio captures; shared rules R1–R8;
  `PortfolioDisplayRules.allocationDonutAllowed` / `sortHoldings` + `PortfolioHoldingSort`, unused by screens yet; +2 tests). P-B not started.
- Tests (actual): core JVM 466/0, core iOS 466/0, shared Android 113/0, shared iOS 109/0, `assembleDebug` OK, default iOS `xcodebuild` BUILD SUCCEEDED.
- Device state to fix by the owner: emulator-5554 shows the system dialog "Allow Android Accessibility Suite to send you notifications?" (raised
  when TalkBack was briefly enabled; left unanswered per the rules; TalkBack is off) and the app's in-app Theme is on **Dark** (was Light).
- Next: owner approval for P-B (Portfolio Overview Card, scope in the P-A doc §5) and for device captures; decisions D2 (overall return %) and D3
  (sort control).

## HANDOFF-2026-10-11. End-of-session handoff — start here

### Repository state (verified with `git status` / `git log` at handoff)
- Branch `main`, HEAD **`19c3105`** "Polish Company Comparison with collapsible sections, short periods, comparison bars, concise notes and one info
  action (Global UI Refinement Phase 5C.1) on Android and iOS". This handoff is committed on top of it as
  "Add end-of-session handoff after Global UI Refinement Phase 5C.1 and point to the Phase 5D Earnings audit" (docs only).
- Commits made this session (all pushed, oldest first): `f08350c` 5A.1 closure (docs) · `b1aa29c` 5B Screener · `9e764fe` 5B closure + 5C brief ·
  `ab004df` 5C Comparison · `19c3105` 5C.1 Comparison polish.

### Current development objective
Global UI Refinement (`docs/GLOBAL_UI_REFINEMENT_PLAN.md` §3): screen-by-screen visual/UX polish on Android (Compose) and iOS (SwiftUI) with shared KMP
display/layout rules and **no** data, API, presenter-contract, entitlement, calculation or navigation changes. Steps 1–5 are done (tokens, components,
Portfolio, Home, Markets, Company Details, Watchlist, Screener, Comparison). Next is plan step 6 (Learn, Daily Market Brief, Earnings) — "Phase 5D".

### Exact task at the end of this session
Phase 5C.1 (Company Comparison polish + the owner's "final UI polish" follow-up) was implemented, verified, committed and pushed. Nothing is in progress.
The owner has **not** yet approved Phase 5D.

### Work completed this session (details in each phase doc)
1. **5A.1 closure** (`f08350c`, docs only): re-verified Watchlist fixes, re-ran tests without the Gradle cache, wrote the manual screen-reader
   procedure (`docs/GLOBAL_UI_REFINEMENT_PHASE5A_1.md` §5b).
2. **5B Screener / Discover Stocks** (`b1aa29c`, `docs/GLOBAL_UI_REFINEMENT_PHASE5B.md`): new core `screener/ScreenerDisplay.kt` (all copy, filter
   categories, inclusive-range operators, chips, sort labels, spoken text); shared `StockCardGridLayout`; Compose `ScreenerScreen.kt` rewritten + new
   `ScreenerFilters.kt`; new SwiftUI `DiscoverStocksViews.swift` (old Discover views removed from `ScreenerScenes.swift`); catalog retry.
3. **5B closure** (`9e764fe`, `docs/GLOBAL_UI_REFINEMENT_PHASE5B_CLOSURE.md`): thousands separators in filter inputs, inverted ranges blocked before search,
   iOS Show results on every editor page, iOS title measurement, Android chip click semantics; Phase 5C brief `docs/GLOBAL_UI_REFINEMENT_PHASE5C_BRIEF.md`.
4. **5C Comparison** (`ab004df`, `docs/GLOBAL_UI_REFINEMENT_PHASE5C.md`): shared `StockComparisonLayout` (columns only while every value/word fits, else one
   line per company); core `screener/ComparisonDisplay.kt`; selection ⋮ (Replace/Remove), section cards, sticky header only around metrics, chart-only
   error, "More tools" rows; optional `badge` on `StockNavigationRow` (both platforms); 29 before/after captures in `docs/design/phase5c/`.
5. **5C.1 polish** (`19c3105`, `docs/GLOBAL_UI_REFINEMENT_PHASE5C_1.md`): collapsible sections (Growth/Profitability open), short periods, percentage-only
   comparison bars (`ComparisonDisplay.bars`, new `MetricCell.value`), notes behind "What to keep in mind", one ⓘ per metric, insights preview, compact
   selection/collapsed cards, iOS XXXL value-vs-period size fix; captures in `docs/design/phase5c_1/` and `…/final/`.

### Files created or modified this session (all committed)
- Core: `screener/ScreenerDisplay.kt` (new), `screener/ComparisonDisplay.kt` (new), `screener/ScreenerPresentation.kt` (`MISSING_PRICE`,
  `ScreenerRowView.priceAvailable`/spoken label, `MetricCell.value`); tests `ScreenerDisplayTest.kt` (new, 15), `ComparisonDisplayTest.kt` (new, 9).
- Shared app: `theme/StockSemanticStyles.kt` (`StockCardGridLayout`, `StockComparisonLayout`), test `theme/ResponsiveLayoutRulesTest.kt`;
  `designsystem/components/StockNavigationRow.kt` (optional badge); `presentation/screener/` `ScreenerScreen.kt`, `ScreenerFilters.kt` (new),
  `ScreenerScene.kt`, `ComparisonScreen.kt`, `ComparisonHistoryUi.kt`, `GuidedMetricExplanation.kt`, `ComparisonResearchUi.kt` / `ComparisonAiUi.kt`
  (old entry cards removed); `iosMain/IosScreenerClient.kt`.
- iOS: `DiscoverStocksViews.swift` (new), `ScreenerScenes.swift`, `ComparisonHistoryViews.swift`, `ComparisonAiViews.swift`, `ComparisonResearchViews.swift`,
  `DesignSystem/StockComponents.swift`.
- Docs: phase docs above, `docs/design/phase5b/REFERENCE-screener-discover-stocks.webp`, `docs/design/phase5c/`, `docs/design/phase5c_1/`, handoff/status
  docs, `CLAUDE.md` (current task only).

### Important decisions (and why)
- **No sixth tab / no Screener tab**: the owner's reference images showed one; CLAUDE.md and the owner's briefs fix five tabs (Home | Markets | Portfolio |
  Watchlist | Learn). Screener and Compare stay pushed from Markets.
- **Display text lives in core** (`ScreenerDisplay`, `ComparisonDisplay`) so Compose and SwiftUI show identical copy and spoken labels; **layout decisions**
  live in `StockSemanticStyles.kt` objects with tests in `ResponsiveLayoutRulesTest`; platforms only measure text and call them.
- **Screener operators "At least / At most / Between"**, not "Less/Greater than": the engine's ranges are inclusive.
- **No dollar daily change on Screener rows**: records carry `changePercent` only; nothing is derived.
- **Comparison bars only for percentage metrics** (formatter sets `MetricCell.value` for `PERCENT` only) — money is never drawn across currencies; missing =
  no bar + "No data"; a note says bar length isn't "better".
- **Missing values**: Watchlist/Screener "No data available"; narrow Compare cells "No data" + a labelled reason control (never "N/A ⓘ", never 0).
- **iOS Dynamic Type**: small token styles scale faster (`.caption2` 11→18.7 pt at XXXL vs `.footnote` 13→17.3 pt, measured) — value and period labels
  both use the `.footnote` curve so a value is never smaller than its period.
- **Android system Back** closes the whole Screener filter sheet (no shared `BackHandler`); the draft is kept — documented, not fixed.

### Known bugs, blockers and risks
- **MOCK fixture artifact**: in `server/src/main/resources/fixtures/earnings/events.json` quarterly revenues were generated from the annual growth rate, so
  "Revenue growth — latest quarter" equals "— fiscal year" for every MOCK company. Code reads independent fields (verified); values left unchanged.
  Possible follow-up: regenerate those fixtures (needs owner approval; server fixtures).
- Android open Compare section leaves ~60 dp before the first metric (48 dp targets); sticky company header remains in column mode even where bar rows name
  companies. Minor.
- iOS: tapping while the Markets list is still scrolling can hit a sample news headline and open Safari (happened once during device checks) — be careful
  with coordinate taps; take two screenshots before tapping.
- The default `xcodebuild` bakes the **LAN** MOCK URL into the shared DerivedData app; device checks must rebuild with
  `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` and confirm `StockStepsMockBackendURL` in the built Info.plist before launching.
- Still open from earlier: approved LAN backend redeploy (5C.1 brief wording, `deploy/local/deploy.sh deploy`), Cloud Run staging deferred.

### Build and test results
- **Verified this session (latest run, after 5C.1, clean outputs)**: core JVM 465/0, core iOS 465/0, shared Android host 111/0, shared iOS 107/0,
  `assembleDebug` OK, iOS `xcodebuild` BUILD SUCCEEDED (no warnings in changed files). Device checks on local MOCK: Android emulator-5554 at a 360 dp
  override (1.0×, 1.3×, light), iPhone 16 iOS 18.3 (Large, XXXL; light and dark).
- **Not verified**: TalkBack, VoiceOver, iPhone SE for 5B/5C/5C.1, Android dark (app theme setting), 5B Screener device screenshots, chart-error /
  partial-company / unlocked-premium states on device, iOS signed-in screens, `:server:test` (not needed — no server change).

### Device/environment notes
- Android emulator default: 1280×2856 (no size override), night mode **on**, font scale 1.0 — restore after checks (`adb shell wm size reset`, etc.).
- iPhone 16 simulator `A04A9C8B-D9D1-4910-B0C0-BEC042629EDA` default Light / Large. iOS guest flow: sign-in screen → scroll → "Continue as guest";
  Compare is under Markets → Research tools. No iOS deep links exist (only Google sign-in URL handling).
- MOCK server: `./gradlew :server:runMock` (health `GET /health`), `./gradlew :server:stopMock`; `adb reverse tcp:8081 tcp:8081` (remove afterwards).
- Ask the owner before any emulator/simulator use; never use the LAN host; never sign in.

### Requirements discussed but not implemented
- Phase 5D: Learn, Daily Market Brief, Earnings (plan step 6) and later step 7 (Settings, auth, Practice, Guided Research, search, onboarding).
- Screen-reader passes on physical devices for every refined screen (manual procedure template in `docs/GLOBAL_UI_REFINEMENT_PHASE5A_1.md` §5b).
- Optional: regenerate MOCK earnings fixtures with independent quarterly growth; tighten Compare section spacing on Android; Android `BackHandler` for the
  Screener filter sheet.

### Exact next steps
1. `git status`, `git log -5 --oneline` (expect "Add end-of-session handoff after Global UI Refinement Phase 5C.1 and point to the Phase 5D Earnings audit" on top of `19c3105`).
2. Read `CLAUDE.md`, this section, `docs/GLOBAL_UI_REFINEMENT_PLAN.md` (step 6), `docs/EARNINGS.md`, `docs/DAILY_MARKET_BRIEF.md`.
3. **Wait for the owner's Phase 5D brief/approval.** Then do a read-only audit (recommended first: Earnings — iOS `EarningsScenes.swift`,
   `EarningsPremiumViews.swift`, `EarningsReminderViews.swift` use system fonts and `.bordered` buttons heavily, and Earnings tables use equal-width
   columns like Compare did; Android `presentation/earnings/*`), report findings before editing.
4. Implement with shared rules (`StockComparisonLayout` can be reused for multi-company tables), tests in `ResponsiveLayoutRulesTest` / core display tests.
5. Run sequentially: clean test outputs → `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest
   :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` → `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk
   iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`.
6. Device checks only with approval, local MOCK only; write `docs/GLOBAL_UI_REFINEMENT_PHASE5D.md`; commit/push only when asked (update root
   `PROJECT_HANDOFF.md`, this file, `docs/project-status.md`, `CLAUDE.md` "Current task" in the same commit).

## PHASE5C1. Phase 5C.1 — Company Comparison polish (committed and pushed)

- Repository: `main`. Current commit: "Polish Company Comparison with collapsible sections, short periods, comparison bars, concise notes and one info action (Global UI Refinement Phase 5C.1) on Android and iOS" (pushed), on top of `ab004df` (5C).
- Done (`docs/GLOBAL_UI_REFINEMENT_PHASE5C_1.md`): collapsible sections, short periods, percentage-only bars, notes behind one control, one ⓘ per metric,
  insights preview, compact selection, iOS XXXL value/period fix. Results: core 465/0 (JVM, iOS), shared 111/0 and 107/0, both builds OK.
- NOT RUN: TalkBack, VoiceOver, iPhone SE, Android dark. Quarterly = annual revenue growth in MOCK is a fixture artifact (values unchanged).
- Next: Phase 5D (Earnings audit recommended) only after approval.

## PHASE5C. Global UI Refinement Phase 5C — Company Comparison (committed and pushed)

- Repository: `main`. Current commit: "Refine Company Comparison with word-safe stacked values, honest missing data, compact selection and grouped tools (Global UI Refinement Phase 5C) on Android and iOS" (pushed), on top of `9e764fe` (5B closure).
- Done (`docs/GLOBAL_UI_REFINEMENT_PHASE5C.md`): shared `StockComparisonLayout` (columns only while every value/word fits; else one line per company),
  core `ComparisonDisplay` ("No data" + reason control, spoken labels naming company/metric/period), `StockNavigationRow` optional badge; Compose
  `ComparisonScreen.kt` and SwiftUI `CompareStocksScreen` reworked (selection ⋮, section cards, sticky header only around metrics, chart-only error,
  More tools with PREMIUM badge), history table uses the same rule; iOS `.bordered` → `.stockSecondary` in Compare/research/AI.
- Results: core 461/0 (JVM, iOS), shared Android 111/0, shared iOS 107/0, `assembleDebug` OK, iOS `xcodebuild` OK. Device before/after in
  `docs/design/phase5c/` (29 captures, local MOCK only). NOT RUN: TalkBack, VoiceOver, iPhone SE, Android dark, chart-error/partial/unlocked states.
  Verdict PASS WITH CONDITIONS. Next: Phase 5D (Earnings audit recommended) only after approval.

## PHASE5B-CLOSURE. Phase 5B closure verification (2026-10-11, committed and pushed)

- Repository: `main`. Current commit: "Close Global UI Refinement Phase 5B with filter-input, range and accessibility fixes and add the Phase 5C Comparison brief" (pushed), on top of `b1aa29c` (5B).
- Report `docs/GLOBAL_UI_REFINEMENT_PHASE5B_CLOSURE.md`: audit PASS on both platforms after five fixes — D1 `ScreenerDisplay.parseInput` thousands
  separators ("100,000" was 100), D2 `ScreenerDisplay.invalidRange` disables Show results for inverted ranges, D3 iOS `ShowResultsBar` on every editor
  page, D4 iOS preset title drawn with the measured Dynamic Type style, D5 Android chip semantics click action. +2 core tests.
- Results: core JVM 456/0, core iOS 456/0, shared Android 110/0, shared iOS 106/0, `assembleDebug` OK, iOS `xcodebuild` BUILD SUCCEEDED.
- NOT RUN: device screenshots, large-text device checks, TalkBack, VoiceOver (approval pending). Verdict **PASS WITH CONDITIONS**.
- Known limitation kept: Android system Back closes the filter sheet (draft kept). Phase 5A.1 conditions still open.
- Phase 5C brief (read-only review, nothing changed): `docs/GLOBAL_UI_REFINEMENT_PHASE5C_BRIEF.md`. Readiness READY WITH CONDITIONS.
- Next: device checks on the local MOCK server if approved → Phase 5C only after approval.

## PHASE5B. Global UI Refinement Phase 5B — Screener / Discover Stocks (committed and pushed)

- Repository: `main`. Current commit: "Refine Discover Stocks with word-safe preset cards, a categorised filter editor, grouped result rows and clear empty and error states (Global UI Refinement Phase 5B) on Android and iOS" (pushed), on top of
  `f08350c` "Close Global UI Refinement Phase 5A.1 with a re-verification pass and Phase 5B handoff" (docs only, pushed).
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE5B.md`): shared core `screener/ScreenerDisplay.kt` (copy, categories, inclusive-range operators, chips, sort
  labels, spoken text, "No data available"); `ScreenerRowView.priceAvailable` + spoken direction in words; shared `StockCardGridLayout` (preset cards 2 → 1
  column instead of breaking "Compan/ies"); Compose `ScreenerScreen.kt` rewritten + new `ScreenerFilters.kt`; SwiftUI new `DiscoverStocksViews.swift`
  (old Discover views removed from `ScreenerScenes.swift`). Catalog errors now have Try again. Tabs unchanged (Watchlist stays; no Screener tab).
- Results: core JVM 454/0, core iOS 454/0, shared Android 110/0, shared iOS 106/0, `assembleDebug` OK, iOS `xcodebuild` BUILD SUCCEEDED.
  NOT RUN: device screenshots, Android 360 dp 1.3× / iOS XXXL / compact iOS on device, TalkBack, VoiceOver (approval pending). Verdict PASS WITH CONDITIONS.
- Phase 5A.1 conditions remain open (screen readers, iOS signed-in Watchlist, iPhone SE XXXL, mixed saved dates on device).
- Next: device checks on the local MOCK server if approved (screenshots into `docs/design/phase5b/`) → **Phase 5C — Company Comparison only after approval** (not started).

## NEXT. Session handoff (2026-10-10, end of session; superseded by §PHASE5B for current state)

### Repository state (verified with `git status` / `git log` at handoff)
- Branch `main`, HEAD `bb7105e` "Polish Watchlist header wrapping, two-line names, fading list selector, short empty title and single offline label
  (Global UI Refinement Phase 5A.1) on Android and iOS", in sync with `origin/main`. Then the docs-only closure commit "Close Global UI Refinement Phase 5A.1 with a re-verification pass and Phase 5B handoff" on top.
- Commits made this session (all pushed, newest first): `bb7105e` 5A.1 Watchlist polish · `50580b5` 5A Watchlist · `e08ceb2` 4C.2 + 4C.3 Company
  Details · (earlier, already in history) `7adc89c` 4C + 4C.1.

### Phase 5A.1 closure review (2026-10-11) — committed as "Close Global UI Refinement Phase 5A.1 with a re-verification pass and Phase 5B handoff" (docs only)
- Code review confirmed every 5A.1 fix on both platforms; **no code changes**. Tests re-run with cleaned outputs (no cache): core JVM 441/0, core iOS
  441/0, shared Android 109/0, shared iOS 105/0, `assembleDebug` OK; iOS `xcodebuild` BUILD SUCCEEDED.
- Screen readers, iOS signed-in, SE XXXL and mixed saved dates on device: **NOT RUN** (no device use approved; manual procedure in §5b of the phase
  doc). Verdict unchanged: PASS WITH CONDITIONS, no blocking defects — ready to close. Phase 5B starts after the owner approves the closure.

### Current development objective
Global UI Refinement (plan `docs/GLOBAL_UI_REFINEMENT_PLAN.md` §3): screen-by-screen visual/UX polish on Android (Compose) and iOS (SwiftUI) with shared
KMP rules, **no** data/presenter-contract/navigation/business-logic changes. Done: steps 1–4 (tokens, components, Portfolio, Home, Markets, Company
Details) and step 5 part A (Watchlist = Phase 5A + 5A.1). **Next task: Phase 5B — Screener ("Discover Stocks")**, then 5C — Comparison. Only start 5B
after the owner approves; the owner usually sends a detailed phase brief (often with a reference image) — follow it.

### Work completed this session (details in each phase doc)
- **4C.2** (`docs/GLOBAL_UI_REFINEMENT_PHASE4C_2.md`): core `MarginWording` ("lost about $80 for every $100 in revenue"); `StockDataException.status/code`
  + `ChartPresentation.issue/notice` (no history w/o retry; connection / temporary with retry); date-aware change label
  `CompanyDetailsPresentation.changeLabel` ("Today" only when the quote's NY date is today **and** the server status is known, non-pre-market);
  growth row interprets; repeated net-margin helper removed; 48 dp sources target.
- **4C.3** (`docs/GLOBAL_UI_REFINEMENT_PHASE4C_3.md`): shared `StockActionLayout` (header actions pair only when both measured labels fit),
  `StockBadgeLayout` (long badges below titles), compact centered chart notices (`ChartNotice.title`), duplicate valuation headline removed,
  Android sources-button single label, iOS MOCK banner above the tab bar (`sampleDataInset` + `SampleBannerPlacement` in `ContentView.swift`).
- **5A** (`docs/GLOBAL_UI_REFINEMENT_PHASE5A.md`): `StockPillLayout.fadesEdge` (fade only when > half a touch target is hidden — closed the 4C.3
  "ALL faded on iPhone 16" issue); Watchlist reorder (header → selector → grouped compact rows → context line + Add a company → "Alerts and more" →
  insights); `HomePresentation.signedPrice`; `WatchlistRowModel.changeLine/quoteAvailable`; shared `StockQuoteRowLayout`; "No data available";
  empty state with one primary action; average as an insight sentence (2+ priced stocks only).
- **5A.1** (`docs/GLOBAL_UI_REFINEMENT_PHASE5A_1.md`): `WatchlistModel.headerLine/headerCaution` (non-breaking spaces inside parts), two-line names
  (`StockRow` `nameMaxLines` / `nameLineLimit`), shared `StockFadingScrollRow` (Compose `StockChip.kt`, SwiftUI `StockDetailComponents.swift`) used by
  the pill selector and the watchlist selector, "This watchlist is empty" + spoken name (`titleDescription` / `titleAccessibilityLabel`), offline
  saved-price label once in the header.
- New server test `server/src/test/.../market/CompanyDetailsFixtureWordingTest.kt` (MOCK fixtures through the shared presenter).

### Files most often touched (for orientation)
Core: `companydetail/{ChartPresentation,CompanyOverview}.kt`, `watchlist/WatchlistPresentation.kt`, `home/HomePresentation.kt`, `domain/StockRepository.kt`,
`data/StockDataRequest.kt`. Shared: `theme/StockSemanticStyles.kt` (all shared layout rules), `designsystem/components/{StockChip,StockRow,StockStates,
StockAssessmentRow}.kt`, `presentation/companydetails/*`, `presentation/watchlist/WatchListScreen.kt`. iOS: `CompanyDetailsScreen.swift`,
`CompanyDetailsModel.swift`, `WatchListScene.swift`, `ContentView.swift`, `AppScene.swift`, `DesignSystem/{StockDetailComponents,StockComponents,
StockControls}.swift`. Tests: `core/.../watchlist/WatchlistPresenterTest.kt`, `core/.../companydetail/CompanyOverviewPresenterTest.kt`,
`app/shared/.../theme/ResponsiveLayoutRulesTest.kt`, `app/shared/.../companydetails/*Test.kt`.

### Important decisions (and why)
- Responsive rules live in shared KMP objects in `theme/StockSemanticStyles.kt` (`StockPillLayout`, `StockActionLayout`, `StockBadgeLayout`,
  `StockQuoteRowLayout`); each platform only measures text and calls the rule → identical decisions on Compose and SwiftUI, unit-testable.
- New component parameters are optional with unchanged defaults (e.g. `nameMaxLines`, `titleDescription`) so other screens don't change.
- Missing data is never zero: "No data available" / "—"; spoken labels say "price unavailable"; the average is shown only with ≥ 2 priced stocks.
- "Today" is never inferred from the device clock alone (quote date + server market status).
- iOS banner: tab content insets it above the tab bar; root shows it only when no tab bar is visible (UIKit tab bar ignores SwiftUI root insets).

### Build and test results
- **Verified** (final runs, sequential): core JVM **441/0**, core iOS **441/0**, shared Android **109/0**, shared iOS **105/0**,
  `:app:androidApp:assembleDebug` OK, iOS `xcodebuild … generic/platform=iOS Simulator … build` **BUILD SUCCEEDED**; server
  `CompanyDetailsFixtureWordingTest` 3/0. The full `:server:test` suite was **not** run this session (no server main code changed).
- **Verified on devices** (local MOCK server only): Android emulator-5554 at 360 dp (1.0×/1.3×, light/dark); iPhone 16 (default/XXXL, light/dark,
  guest); temporary iOS 18.3 iPhone SE (created/deleted). Offline header and missing-quote "No data available" confirmed on device.
- **Not verified**: TalkBack/VoiceOver spoken output (TalkBack runs but the user-build emulator can't log speech; VoiceOver absent in Simulator);
  iOS signed-in Watchlist (no sign-in on simulators); iPhone SE at XXXL for 5A.1; iOS runtime accessibility labels (code review only).

### Known bugs, risks, blockers
- Android watchlist chip: description and selected state are on separate accessibility nodes — expected to read correctly, unconfirmed (no speech capture).
- Watch-data repository reuses quotes < 60 s old, so an immediate refresh after going offline still says "Updated …" (existing, intentional caching).
- **Device-check trap:** the default `xcodebuild` writes the LAN MOCK URL (git-ignored `app/iosApp/Configuration/Local.xcconfig`) into the shared
  DerivedData app. Before launching on a simulator rebuild with `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` (verify with
  `PlistBuddy -c "Print :StockStepsMockBackendURL" …/StockSteps.app/Info.plist`). Never contact the LAN host without approval.
- iPhone 16 simulator currently has the localhost build installed; its guest list holds AAPL, MSFT, TSLA, NVDA (device state only).
- MOCK search only matches captured queries (works: Apple, Microsoft, Nvidia, Tesla, Amazon; not Rivian/RIVN). RIVN and LONGN are reachable via
  Markets → Earnings Calendar → company filter → View Company Details. MOCK server data (signed-in lists) is in memory and lost on restart.
- Simulator input can lag a few seconds; tap with point coordinates (iPhone 16 = 393×852 pt: nav-bar back/star at y≈76, not screenshot pixels).
- Open owner decisions: R1 server "Why Did It Move?" repeats its caution; R2 "+135.60% past ALL" wording; R3 iOS hides tab bar on Company Details
  (Android keeps it); R5 failed `/info` hides the MOCK banner for the session; LAN backend still on the Phase 5C image (redeploy needs approval).

### Requirements discussed but not yet implemented
- Phase 5B Screener and 5C Comparison redesign (plan step 5).
- Known 5B input: Screener "Discover" cards break words mid-word at 1.3× ("Compan/ies", seen in 4C.3 at 360 dp). Suggested in 5A doc: reuse
  `StockQuoteRowLayout` for result rows and grouped `StockNavigationRow` patterns for tools.
- Plan steps 6 (Learn, Daily Brief, Earnings) and 7 (Settings, auth, Practice, Guided Research, search, onboarding).
- Screen-reader passes on physical devices; iOS signed-in verification with the Firebase Auth emulator (approved test account only).

### Exact next steps
1. `git status`, `git log -5 --oneline`; read `CLAUDE.md`, this section, `docs/GLOBAL_UI_REFINEMENT_PLAN.md`, `docs/GLOBAL_UI_REFINEMENT_PHASE5A.md`
   and `…PHASE5A_1.md`.
2. Wait for the owner's Phase 5B brief/approval. Then audit read-only: Android `presentation/screener/{ScreenerScene,ScreenerScreen}.kt`,
   iOS `ScreenerScenes.swift` (943 lines, also hosts Comparison entry views), core `screener/{ScreenerPresentation,ScreenerModels,ScreenerEngine}.kt`;
   report structure/constraints before editing (owner asks for this).
3. Implement with shared rules in `theme/StockSemanticStyles.kt` + tests in `ResponsiveLayoutRulesTest.kt`; reuse `StockRow`, `StockCard`,
   `StockPriceChange`, `StockNavigationRow`, `StockStateMessage`/`StockEmptyState`, `StockFadingScrollRow`, `StockPillSelector`.
4. Ask before device use; use local MOCK only (`./gradlew :server:runMock`, `adb reverse tcp:8081 tcp:8081`, localhost iOS build); restore settings.
5. Run sequentially: `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
   :app:androidApp:assembleDebug --continue`, then the iOS `xcodebuild` (takes ~15–20 min in total; run in background and wait).
6. Write `docs/GLOBAL_UI_REFINEMENT_PHASE5B.md` (issues table, files, tests, screenshots in `docs/design/phase5b/`, accessibility status,
   verdict); commit/push only when asked, updating root `PROJECT_HANDOFF.md`, this file, `docs/project-status.md` and `CLAUDE.md` "Current task".

## PHASE5A1. Global UI Refinement Phase 5A.1 — Watchlist final polish (committed and pushed)

- Repository: `main`. Current commit: "Polish Watchlist header wrapping, two-line names, fading list selector, short empty title and single offline label (Global UI Refinement Phase 5A.1) on Android and iOS" (pushed), on top of `50580b5` (Phase 5A).
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE5A_1.md`, evidence `docs/design/phase5a_1/`): header line wraps only between parts; two-line company
  names; shared `StockFadingScrollRow` for the list selector; "This watchlist is empty" with a spoken name; offline label once in the header.
- Results: core JVM/iOS 441/0, shared Android 109/0, shared iOS 105/0, `assembleDebug` OK, iOS `xcodebuild` OK. Closure pass: offline and missing data
  verified on device; no new defects. NOT RUN: TalkBack/VoiceOver speech, iOS signed-in, iPhone SE XXXL. Verdict PASS WITH CONDITIONS.
- Next: user review → Phase 5B only after approval.

## PHASE5A. Global UI Refinement Phase 5A — Watchlist (committed and pushed)

- Repository: `main`. Current commit: "Refine Watchlist with compact quote rows, full-name list selector, clear empty and unavailable states and grouped alert actions (Global UI Refinement Phase 5A) on Android and iOS" (pushed), on top of `e08ceb2` (4C.2 + 4C.3).
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE5A.md`, evidence `docs/design/phase5a/`): 4C.3 selector fade closed with shared
  `StockPillLayout.fadesEdge`; Watchlist on both platforms reordered (header with count/updated → full-name selector → compact grouped rows with
  "+$3.04 (+0.91%)" → context line + Add a company → "Alerts and more" rows → insights); shared `StockQuoteRowLayout`; "No data available" /
  loading / prices-unavailable states; empty state with one primary action; row menu starts with View details.
- Results: core JVM/iOS 439/0, shared Android 109/0, shared iOS 105/0, `assembleDebug` OK, iOS `xcodebuild` OK. Not run: iOS SE, iOS signed-in,
  TalkBack, VoiceOver. Verdict PASS WITH CONDITIONS.
- Next: user review → Phase 5B (Screener) only after approval.

## PHASE4C3. Global UI Refinement Phase 4C.3 — Company Details responsive UX and accessibility polish (committed and pushed)

- Repository: `main`. Current commit: "Polish Company Details: natural loss wording, chart error states, dated change label, responsive actions and range selector (Global UI Refinement Phases 4C.2 and 4C.3)" (pushed; 4C.2 and 4C.3 together), on top of `7adc89c` (4C + 4C.1).
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE4C_3.md`, evidence `docs/design/phase4c3/`): Android sources-button semantics (one "3 sources"
  label); shared `StockActionLayout` for side-by-side header actions on both platforms (iPhone SE wrap fixed); `StockPillSelector` scroll
  affordance (edge fade, keep selected visible, all call sites); compact centered chart error/empty states (`ChartNotice.title`); iOS MOCK banner
  above the tab bar (`sampleDataInset`, `SampleBannerPlacement`); long badges below titles (`StockBadgeLayout`); duplicate valuation headline removed.
- Results: core JVM/iOS 435/0, shared Android 107/0, shared iOS 103/0, server fixture wording test 3/0, `assembleDebug` OK, iOS `xcodebuild` OK.
  Devices (local MOCK only): Android 360 dp 1.0×/1.3×, iPhone 16, temporary iPhone SE default/XXXL. TalkBack and VoiceOver: NOT RUN.
- Open: iPhone 16 default shows "ALL" fully visible but faded (fade-slack fix planned, §7 of the phase doc); R1–R5 decisions. Next: user review →
  Phase 5 only after approval.

## PHASE4C2. Global UI Refinement Phase 4C.2 — final Company Details UX polish (committed with 4C.3)

- Committed and pushed together with 4C.3 as "Polish Company Details: natural loss wording, chart error states, dated change label, responsive actions and range selector (Global UI Refinement Phases 4C.2 and 4C.3)".
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE4C_2.md`): natural negative-margin wording via core `MarginWording` ("lost about $80 for every
  $100"); chart failures classified (`StockDataException.status/code` → `ChartPresentation.issue/notice`: no history without retry, connection /
  temporary with retry); date-aware change label (`CompanyDetailsPresentation.changeLabel`, "Today" only for today's session confirmed by the
  server status); repeated net-margin helper removed, growth row interprets; 48 dp/pt sources target.
- Results: core JVM/iOS 435/0, shared Android 104/0, shared iOS 100/0, `assembleDebug` OK, iOS `xcodebuild` OK. Not run: any device check,
  screenshots, TalkBack, VoiceOver. Verdict PASS WITH CONDITIONS.
- Next: user review (and device access for the visual pass) → Phase 5 only after approval.

## PHASE4C1. Global UI Refinement Phase 4C.1 — Company Details polish (committed and pushed) — read first

- Repository: `main`. Current commit: "Refine Company Details for beginners with chart scroll fix and accessible price summary (Global UI Refinement Phases 4C and 4C.1)" (pushed; Phases 4C and 4C.1 together), on top of `4eb9e5b` (Phase 4B.1). Commit/push only
  when asked; every commit updates the root `PROJECT_HANDOFF.md`.
- **Correction:** the iOS app's `StockStepsMockBackendURL` is a private-LAN address, so all iOS device checks in Phases 4A–4C.1 used the LAN MOCK
  backend (read-only app calls), not the local server earlier notes named. Android used the local server via `adb reverse`. Details
  `docs/GLOBAL_UI_REFINEMENT_PHASE4C_1.md` §0.
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE4C_1.md`, evidence `docs/design/phase4c1/`): chart scroll conflict fixed on both platforms
  (horizontal-only scrub; vertical drags scroll); quote time includes the date; price block = price + status chip, change, update line, one
  spoken sentence; header actions primary/secondary layout; duplicate inline source links removed.
- Results: core JVM/iOS 430/0, shared Android 101/0, shared iOS 97/0, `assembleDebug` OK, iOS `xcodebuild` OK. Android edge cases (NVDA, INTC,
  RIVN) and offline errors checked; iOS header/scrub/XXXL checked. Not run: TalkBack (injected input not routed; enabled then restored),
  VoiceOver, iOS error state, iOS SE re-check.
- Next: user review → Phase 5 only after approval.

## PHASE4C. Global UI Refinement Phase 4C — Company Details (committed with 4C.1)

- Repository: `main`, HEAD "Refine Markets to the final reference: compact US market status, US-only indices and sector methodology sheet
  (Global UI Refinement Phase 4B.1)" (pushed). **Phase 4C is in the working tree, uncommitted** (commit/push only when asked; update the
  root `PROJECT_HANDOFF.md` in the commit).
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE4C.md`, screenshots/reference `docs/design/phase4c/`): Company Details reordered for beginners
  (identity & price → chart → why it moved → At a Glance + ranges → Understand <company> → Valuation & financial health → Earnings → News);
  wrapping header actions; market status chip with neutral closed (switch removed); At a Glance in one `StockMetricGrid` with the "(YoY)"
  qualifier moved to the supporting line (truncation fixed); duplicates removed (chart stats, key ratios, sector section); valuation/earnings
  links as `StockNavigationRow`s; assessment badges wrap below titles; watchlist star blue; new shared `CompanyDetailsPresentation` + 4 tests.
- Results: core JVM/iOS 430/0, shared Android 100/0, shared iOS 96/0, `assembleDebug` OK, iOS `xcodebuild` OK. Devices: Android light/dark and
  1.3× at 360 dp, iOS light and dark XXXL, temporary iPhone SE 375 pt (deleted); settings restored, MOCK server stopped (iOS read from the LAN MOCK backend — see §PHASE4C1). Not run: screen
  readers, loading/offline/error states on device, negative/missing-value companies on device.
- Next: user review → Phase 5 only after approval.

## PHASE4B1. Global UI Refinement Phase 4B.1 — Markets final-reference refinement (committed and pushed) — read first

- Repository: `main`. Current commit: "Refine Markets to the final reference: compact US market status, US-only indices and sector methodology sheet (Global UI Refinement Phase 4B.1)" (pushed), on top of Phase 4B. Commit/push only when the user asks; every commit
  updates the root `PROJECT_HANDOFF.md`.
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE4.md` §Phase 4B.1; reference `docs/design/phase4b/stocksteps-markets-ui-reference-final.webp`):
  compact "US Market" status card (short status, calendar-based "Next open · Fri, Oct 9 · 9:30 AM ET" via new core
  `MarketsPresenter.sessionLine`, one-line "Sample quotes · Not live" footer with the full notice spoken); US-only indices (TSX removed
  from Markets by API region); brief card labels on their own line; sector caption shortened with the methodology in an (i) sheet; compact
  sector rows; non-breaking "9:30 AM ET".
- Results: core JVM/iOS 430/0, shared Android 96/0, shared iOS 92/0, `assembleDebug` OK, iOS `xcodebuild` OK. Devices: iOS light/dark/XXXL,
  Android light and 1.3× at 360 dp, sector sheet; settings restored, MOCK server stopped. Not run: screen readers, iPhone SE, error states.
- Next: user review → Phase 4C (Company Details) only after approval.

## PHASE4B. Global UI Refinement Phase 4B — Markets (committed and pushed) — read first

- Repository: `main`. Current commit: "Redesign Markets data-first with status, index rows and research tools (Global UI Refinement Phase 4B) on Android and iOS" (pushed), on top of Phase 4A
  ("Redesign Home with a market overview and portfolio summary (Global UI Refinement Phase 4A) on Android and iOS"). Commit/push only when the user asks; every commit updates the root `PROJECT_HANDOFF.md`.
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE4.md` §Phase 4B, screenshots `docs/design/phase4b/`, reference
  `docs/design/phase4b/stocksteps-markets-ui-reference.png`): Markets reordered data first (status → indices → brief entry → movers →
  sectors → research tools → news → lesson); Market status card (closed = neutral dot, US session only — user decision); indices as one
  grouped card of rows (sparkline only when the card is ≥ 380 dp/pt); brief as one entry row + Previous briefs (summary sentence dropped);
  research tools as grouped `StockNavigationRow`s (new design-system component; Home's `HomeLinkRow` delegates); sector names wrap;
  unused `DailyBriefPreviewCard` removed on both platforms; title "Markets".
- Results: core JVM/iOS 429/0, shared Android 92/0, shared iOS 88/0, `assembleDebug` OK, iOS `xcodebuild` BUILD SUCCEEDED. Device pass
  (approved): light/dark, iOS XXXL, Android 1.3× at 360 dp, temporary iPhone SE 375 pt (deleted afterwards), Android error state; settings
  restored, MOCK server stopped. NOT RUN: VoiceOver/TalkBack, iOS error state.
- Next: user review of the 4B screenshots → **Phase 4C (Company Details) only after approval**.

## PHASE4A. Global UI Refinement Phase 4A — Home (committed and pushed) — read first

- Repository: `main`. Current commit: "Redesign Home with a market overview and portfolio summary (Global UI Refinement Phase 4A) on Android and iOS" (pushed), on top of `6ec2267` (Phase 3.1). Commit/push only when the user asks; every
  commit updates the root `PROJECT_HANDOFF.md`. The SESSION section below predates Phase 4A.
- Done (details and evidence: `docs/GLOBAL_UI_REFINEMENT_PHASE4.md`, screenshots `docs/design/phase4a/`): Home reordered (header + greeting →
  "Markets today" → portfolio → watchlist → facts → Learn and practice → news → recent → MOCK picker); "Markets today" card built from the
  Daily Brief already loaded on Home (sessions, three indices, brief entry row; **no new requests**); compact `HomePortfolioSummary` in the
  Phase 3 language replaces the old `PortfolioSummaryCard` (removed on both platforms); `HomeLinkRow` fact rows; watchlist rows edge-to-edge;
  alerts error inside its card; `ReadableDates` moved to core (`PortfolioDates` delegates) so Home's core strings show readable UTC dates;
  `StockRow` names may wrap to two lines at large text.
- Files: `app/shared/.../presentation/home/{HomeScreen,HomeComponents,HomeDashboardPresentation}.kt`, `portfolio/{PortfolioScreen,
  PortfolioPresentation}.kt`, `designsystem/components/StockRow.kt`; iOS `HomeScreen.swift`, `HomeComponents.swift`, `PortfolioScreen.swift`,
  `DesignSystem/StockComponents.swift`; core `format/ReadableDates.kt`, `home/PersonalDashboard.kt`, `home/PersonalDashboardStore.kt`; test
  `HomeDashboardPresentationTest` (6); docs.
- Results: core JVM 429/0, core iOS 429/0, shared Android 87/0, shared iOS 83/0, `assembleDebug` OK, server 482/0 (3 skipped), iOS
  `xcodebuild` BUILD SUCCEEDED. Device pass (approved): iPhone 16 Simulator + emulator-5554, light/dark, iOS XXXL, Android 1.3× at 360 dp;
  settings restored, MOCK server stopped. NOT RUN: VoiceOver/TalkBack, iOS narrow width, visual check of the two-line `StockRow` names,
  populated iOS watchlist rows.
- Next: user review of 4A → **Phase 4B (Markets) only after approval**, then 4C (Company Details).

## SESSION. End-of-session handoff (2026-10-10) — read first

### Repository state (verified at handoff)
- Branch `main`, **in sync with `origin/main`, working tree clean** before this documentation update (only `docs/PROJECT_HANDOFF.md`,
  `docs/project-status.md` and `CLAUDE.md` are edited by the handoff itself; not committed).
- HEAD `6ec2267` "Polish the Portfolio reference screen (Global UI Refinement Phase 3.1) on Android and iOS". Commits made and pushed in this
  session (newest first):
  1. `6ec2267` Polish the Portfolio reference screen (Global UI Refinement Phase 3.1) on Android and iOS
  2. `5d3b3cd` Redesign Portfolio as the Global UI Refinement Phase 3 reference screen on Android and iOS
  3. `314894e` Add Global UI Refinement Phase 2: badges, pill selectors, banners, search field and component baseline
  4. `f098846` Add Global UI Refinement Phase 1A: accessible buttons, wrapping metrics, states and form controls
  5. `d9912bf` Add Global UI Refinement Phase 1: shared design-system tokens for Android and iOS
  6. `2171975` Close Phase 5C.1 verification: final test matrix, backend check and Global UI Refinement plan (doc-only)
  Below them: `fc3961e` Phase 5C.1 code, `a50a2e3` Phase 5C, `1c1b073` Phase 5B Local.
- Commit/push only when the user explicitly asks (each time); every commit updates the root `PROJECT_HANDOFF.md` and names the current commit.

### Current development objective
**Global UI Refinement** (`docs/GLOBAL_UI_REFINEMENT_PLAN.md`): make every screen calm, readable and consistent on Android (Compose) and iOS
(SwiftUI) using shared KMP tokens and components, following the approved visual direction `docs/design/stocksteps-ui-reference.png` (and
`docs/design/stocksteps-portfolio-reference.png` for Portfolio). Images are illustrative — never a source of data or navigation. Plan order:
1 tokens ✅ → 1A intermediate components ✅ → 2 remaining components ✅ → 3 Portfolio reference screen ✅ (+ 3.1 polish ✅) →
**4 Home, Markets, Company Details (next, needs approval)** → 5 Watchlist, Screener, Comparison → 6 Learn, Daily Brief, Earnings →
7 Settings, authentication and remaining screens.

### Exact task at the end of the session
Phase 3.1 (Portfolio polish) was finished, committed and pushed; the user then asked for this handoff. **No implementation is in progress.**
Phase 4 has **not** been started and must not start without the user's approval.

### Work completed in this session
- **Phase 5C.1 closure** (`2171975`): final matrix (server 482/0/3 skipped, core JVM 429/0, core iOS 429/0, shared Android 55/0, shared iOS 51/0,
  `assembleDebug`, iOS `xcodebuild`), hang regression strategy (report §13a), LAN backend inspected read-only, `docs/GLOBAL_UI_REFINEMENT_PLAN.md`.
- **Phase 1 tokens** (`d9912bf`, `docs/GLOBAL_UI_REFINEMENT_PHASE1.md`): dark navy palette, AA `textTertiary`, text-role aliases
  (`textTitle/textValue/textSupporting/textMeta`), type scale (body 15 … tiny 11; `largeNumber` 32, `numberEmphasis` 18), semantic spacing,
  corners 8/12/16/20, auth gradient tokens, iOS Dynamic Type mapping + `stockFont`, `DesignTokensTest`.
- **Phase 1A** (`f098846`, `docs/GLOBAL_UI_REFINEMENT_PHASE1A.md`): `primaryAction` `0F6FDB` (AA buttons; iOS `.stockPrimary`/`.stockSecondary`
  replaced all `.borderedProminent`), `cardPadding` 16, wrapping `StockMetric` + `StockMetricGrid` (`StockLayout.metricColumns`), full
  empty/error states, `StockSelectField`, iOS `StockTextField` (keyboard Done), `StockLabels.humanize`.
- **Phase 2** (`314894e`, `docs/GLOBAL_UI_REFINEMENT_PHASE2.md`): `StockSemanticStyles` (badge kinds NEUTRAL…SAMPLE, banner kinds, selection
  fill, education text, pill fit rule), `StockStatusBadge`, `StockTitleWithBadge`, `StockBanner`, editable `StockSearchField` (not wired),
  pill selector that scrolls instead of truncating, chips/pills in the action blue, light `negativeText` `CA3035`, row/news text roles;
  iOS before/after screenshots `docs/design/phase2/`.
- **Phase 3 Portfolio** (`5d3b3cd`, `docs/GLOBAL_UI_REFINEMENT_PHASE3_PORTFOLIO.md`): Portfolio redesigned on both platforms (header (i) + ⋯,
  My Portfolio | Practice segmented control, account selector + Add, value hero, + Add transaction, performance card, compact holding rows,
  allocation bars, transactions with menus, insights row, one sync/offline banner); shared `PortfolioPresentation`, `PortfolioChartRules`;
  content-aware metric grids, non-breaking price arrows, centred flat charts; iOS allocation blank block fixed; screenshots `docs/design/phase3/`.
- **Phase 3.1 polish** (`6ec2267`, `docs/GLOBAL_UI_REFINEMENT_PHASE3_1_POLISH.md`): `PortfolioDates` (readable "as of" dates, UTC labelled),
  hero metric rows (`StockMetricGrid(rowsWhenNarrow)`), "Today's change unavailable · Why?", "Cash and dividends" card, consistent allocation
  rows, per-share trade prices and readable dates, width-aware holding-row stacking (`PortfolioLayout.stackedRowWidth` 320), short chart axis
  dates and iOS data-based y-domain; screenshots `docs/design/phase3_1/`.
- Reference images moved into `docs/design/` (`stocksteps-ui-reference.png`, `stocksteps-portfolio-reference.png`).

### Files created or modified this session (all committed)
- Shared tokens `app/shared/src/commonMain/kotlin/org/example/stocksteps/theme/`: `Colors.kt`, `Typography.kt`, `Spacing.kt` (incl. `StockLayout`),
  `FinancialsTokens.kt`, new `StockSemanticStyles.kt`.
- Compose design system `designsystem/`: `theme/StockStepsTheme.kt`, new `StockLabels.kt`, components `StockButton`, `StockCard`, `StockMetric`,
  `StockPriceChange`, `StockStates`, `StockTag` (+ `StockStatusBadge`, `StockTitleWithBadge`), `StockTextField`, new `StockSelectField`,
  `StockChip` (+ `StockPillSelector`), `StockSegmentedControl`, `StockSearchBar` (+ `StockSearchField`), new `StockBanner`, `StockInsightCard`,
  `StockRow`, `StockNewsCard`, `StockSampleDataBanner`, `StockTrendChart`, `icons/StockIcons` (Check, Close, ChevronDown).
- Compose screens: `presentation/portfolio/PortfolioScreen.kt`, `PortfolioHistoryChart.kt`, new `PortfolioPresentation.kt`;
  `presentation/account/AuthComponents.kt` (token colours); `presentation/practice/PracticeScreens.kt`, `presentation/screener/ComparisonAiUi.kt`
  (badge swaps).
- iOS: `Theme.swift`, `DesignSystem/StockComponents.swift`, `StockDetailComponents.swift`, `StockSettingsComponents.swift`, `StockNewsCard.swift`,
  new `DesignSystem/StockControls.swift`, new `DesignSystem/StockStatusViews.swift`, `PortfolioScreen.swift`, `PortfolioHistoryChart.swift`,
  `AuthComponents.swift`, `PracticeScenes.swift`, `ComparisonAiViews.swift`, and the `.stockPrimary` swap in `ComparisonResearchViews`,
  `DailyBriefScenes`, `EarningsPremiumViews`, `EarningsScenes`, `GuidedResearchScenes`, `PortfolioInsightsScreen`, `ScreenerScenes`.
- Tests (`app/shared/src/commonTest/`): `theme/DesignTokensTest.kt`, `theme/SemanticStylesTest.kt`, `designsystem/StockLabelsTest.kt`,
  `presentation/portfolio/PortfolioPresentationTest.kt`.
- Docs: `GLOBAL_UI_REFINEMENT_PLAN.md`, `_PHASE1.md`, `_PHASE1A.md`, `_PHASE2.md`, `_PHASE3_PORTFOLIO.md`, `_PHASE3_1_POLISH.md`,
  `PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md`, `docs/design/**`, handoffs, status, `CLAUDE.md` (button/badge rules).

### Important decisions and reasons
- KMP tokens are the single source; Compose and SwiftUI only bridge them. Badge/banner colours only via `StockSemanticStyles`; layout rules
  shared (`StockLayout.metricColumns`, `StockPillLayout`, `PortfolioChartRules`, `PortfolioLayout`) so both platforms reflow identically.
- Reference-image colours that fail WCAG AA were **not** adopted (light tertiary `94A3B8`, light positive `16A34A`, warning `D97706`, dark
  negative `EF4444`); the education colour family stays amber/indigo (purple needs user approval).
- `primaryAction` `0F6FDB` for white-text buttons/selection; brand `1683FF` stays for accents, links, charts.
- `largeNumber` 32 (target 34) and `numberEmphasis` 18 (target 22) held back until screens using single-line metric cells adopt
  `StockMetricGrid` (Phase 4/5). `sectionGap` keeps 12 (it is the card gap on Home/Portfolio Insights/Practice/Brief) until screens move to
  `contentGap`; then it becomes 24.
- Portfolio: display only — every value from `PortfolioUiState` via `PortfolioFormat`; missing values stay missing; daily change unavailable is
  stated; history loads only after a period is chosen (presenter behaviour kept), so no pill is shown selected before data; charts need ≥ 2
  dated values; dates are UTC-labelled because there is no time-zone database (no new dependency).
- `PortfolioSummaryCard` (Home) intentionally unchanged until Phase 4.

### Known bugs, blockers and risks
- **LAN MOCK backend** (Ubuntu host) still runs `stocksteps-local:a50a2e3cc833` (Phase 5C) — the 5C.1 Daily Brief wording is not deployed.
  Redeploy needs approval: `deploy/local/deploy.sh deploy` (10–30 s downtime, MOCK in-memory data reset), `deploy/local/deploy.sh smoke`
  (23/23), check `upstream` 0; rollback `deploy/local/deploy.sh rollback a50a2e3cc833`.
- Test data: the spare simulator's throwaway account has MSFT opening position at unit price **4 USD** → 13,144 % unrealized; correct maths,
  bad input. Optional follow-up (approval): non-blocking "price far from market" hint in the transaction form.
- No UI-test targets (Compose UI tests / XCUITest): layout/navigation regressions are covered only by device screenshots.
- Not run: VoiceOver/TalkBack walkthroughs, physical devices, high-contrast mode, real Firebase/Google sign-in, push, Firestore persistence
  tests (3 skipped, Phase 5D).
- Freshness times are shown in UTC (no local time zone); Home still shows raw ISO timestamps in `PortfolioSummaryCard`.
- `StockSearchField` and `StockBanner` exist but most screens do not use them yet; Screener metric labels truncate at large text; Company
  Details stats table under-scales (Phase 4/5 items).
- Environment: spare **iPhone 16 Simulator** `A04A9C8B-…` is booted (light, Large text) with the latest Debug build (localhost:8081 MOCK URL)
  and the throwaway account cached. **emulator-5554** has this session's debug build installed and the **user's own Google account signed in**
  (only viewed; font scale 1.0, size reset, app theme Light). The local MOCK server is stopped (start: `STOCKSTEPS_DATA_MODE=mock PORT=8081
  server/build/install/server/bin/server` after `./gradlew :server:installDist`, or `./gradlew :server:runMock`). Do not drive devices without
  the user's approval.

### Build and test results
Verified (final code, `6ec2267`, 2026-10-10):
- `./gradlew :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` → BUILD SUCCESSFUL:
  shared Android host **81 / 0 failed / 0 skipped**, shared iOS **77 / 0 / 0**, `assembleDebug` OK.
- iOS `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator'
  CODE_SIGN_IDENTITY=- CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=YES build` → BUILD SUCCEEDED (no warnings in changed files).
- Manual device checks of Portfolio (iOS light/dark, default/XXXL; Android 427 dp and 360 dp at 1.0×/1.3×, light/dark) — screenshots in
  `docs/design/phase3/` and `phase3_1/`.
Verified earlier this session, not re-run since (server/core untouched by the UI phases): server 482/0 (3 skipped), core JVM 429/0, core iOS 429/0.
Unverified: Android visuals of the Phase 2 components outside Portfolio; screen-reader walkthroughs; physical devices.

### Exact next steps
1. `git status` / `git log -3 --oneline` (expect HEAD `6ec2267`, clean except any handoff doc edits).
2. Ask the user to approve **Phase 4 (Home, Markets, Company Details)** and device use for before/after screenshots.
3. Phase 4 plan (from the Phase 3/3.1 docs): Home portfolio card → reuse the Portfolio hero pattern (`PortfolioPresentation`, readable dates,
   no raw "Today — (—%)"); move `PortfolioDates` into a general date helper; promote the holding-row stacking into `StockRow`; Markets index
   cards and Company Details "At a Glance"/valuation onto `StockMetricGrid` (then raise `numberEmphasis` to 22); fix Company Details stats
   table scaling and the "Sample scenario" hanging indent; screen card gaps → `contentGap` (then `sectionGap` → 24).
4. Run the shared tests + `assembleDebug` + iOS `xcodebuild`; one Gradle/xcodebuild run at a time.
5. Optional with approval: LAN backend redeploy + smoke; VoiceOver/TalkBack pass.
6. Commit only when asked.

### Requirements discussed but not implemented
Phases 4–7 of the plan; local-time "as of" dates; separate holdings/transactions screens ("View all"); 2×2 quick tiles from the portfolio
reference; education colour family change (needs approval); `largeNumber` 34 / `numberEmphasis` 22; transaction-form price sanity hint;
UI-test targets; Phase 5D Firebase persistence; physical-device, screen-reader and real-auth testing; Cloud Run staging (deferred).

## 000000000. Global UI Refinement Phase 3.1 — Portfolio polish

- Repository: `main`. Current commit: "Polish the Portfolio reference screen (Global UI Refinement Phase 3.1) on Android and iOS" (pushed), on top of "Redesign Portfolio as the Global UI Refinement Phase 3 reference screen on Android and iOS" (pushed).
  Commit/push only when asked; every commit updates the root `PROJECT_HANDOFF.md`.
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE3_1_POLISH.md`): issue inventory; readable freshness dates (`PortfolioDates`, UTC labelled);
  hero metric rows when three do not fit (`StockMetricGrid(rowsWhenNarrow)`); "Why?" toggle for the daily-change reason; "Cash and
  dividends" card; consistent allocation rows; trade transactions show the price per share and readable dates; width-aware holding-row
  stacking; chart short axis dates and iOS data-based y-domain.
- Files: `presentation/portfolio/PortfolioPresentation.kt`, `PortfolioScreen.kt`, `PortfolioHistoryChart.kt`, `designsystem/components/StockMetric.kt`;
  iOS `PortfolioScreen.swift`, `PortfolioHistoryChart.swift`, `DesignSystem/StockControls.swift`; test `PortfolioPresentationTest`; screenshots
  `docs/design/phase3_1/`; docs.
- Results: shared Android 81/0/0, shared iOS 77/0/0, `assembleDebug` OK, iOS `xcodebuild` BUILD SUCCEEDED. Device pass (approved) on iOS and
  Android; all settings restored, simulator shut down, MOCK server stopped.
- Next: Phase 4 (Home, Markets, Company Details) only after the user approves it. Other open items: LAN backend redeploy for the
  Phase 5C.1 brief wording (approval needed), Android visual checks of Phase 2 components, VoiceOver/TalkBack walkthroughs, physical devices.

## 00000000. Global UI Refinement Phase 3 — Portfolio reference screen

- Repository: `main`. Current commit: "Redesign Portfolio as the Global UI Refinement Phase 3 reference screen on Android and iOS" (pushed), on top of "Add Global UI Refinement Phase 2: badges, pill selectors, banners, search field and component baseline" (pushed).
  Commit/push only when asked; every commit updates the root `PROJECT_HANDOFF.md`.
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE3_PORTFOLIO.md`): Portfolio redesigned on both platforms (mode selector, account selector,
  hero, + Add transaction, performance card, compact holding rows, cash/allocation/dividends/transactions cards, insights row, overflow menu,
  (i) explanations, one sync banner); shared `PortfolioPresentation` + `PortfolioChartRules`; design-system fixes (content-aware
  `StockMetricGrid`, non-breaking arrow in `StockPriceChange`, flat series centred in `StockTrendChart`); iOS allocation blank block fixed.
- §14 finding: MSFT 13,144 % matches the ledger (opening position at unit price 4 → cost 12 USD) — a test-data artefact, calculations unchanged.
- Files: `presentation/portfolio/PortfolioScreen.kt`, `PortfolioHistoryChart.kt`, new `PortfolioPresentation.kt`; design system `StockMetric.kt`,
  `StockPriceChange.kt`, `StockTrendChart.kt`, theme `Spacing.kt`; iOS `PortfolioScreen.swift`, `PortfolioHistoryChart.swift`,
  `DesignSystem/StockControls.swift`, `StockComponents.swift`; tests `PortfolioPresentationTest`, `DesignTokensTest`; docs; screenshots
  `docs/design/phase3/`; reference `docs/design/stocksteps-portfolio-reference.png`.
- Results: shared Android 80/0/0, shared iOS 76/0/0, `assembleDebug` OK, iOS `xcodebuild` BUILD SUCCEEDED.
- Devices (approved): spare iPhone 16 Simulator (restored, shut down) and emulator-5554 (debug build installed, font scale / size / app theme
  restored; the user's own account is signed in there and was only viewed; samples are read-only). MOCK server stopped.
- Next: Phase 4 (Home, Markets, Company Details) only after approval.

## 0000000. Global UI Refinement Phase 2 — remaining reusable components

- Repository: `main`. Current commit: "Add Global UI Refinement Phase 2: badges, pill selectors, banners, search field and component baseline" (pushed), on top of "Add Global UI Refinement Phase 1A: accessible buttons, wrapping metrics, states and form controls" (pushed).
  Commit/push only when asked; every commit updates the root `PROJECT_HANDOFF.md`.
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE2.md`): iOS before/after screenshots (approved: spare iPhone 16 Simulator, local MOCK server;
  Android NOT RUN) → `docs/design/phase2/*.jpg`; shared `StockSemanticStyles` (badge/banner kinds, selection fill, education text, pill
  fit rule); light `negativeText` → `CA3035` (AA on its container); pill selector scrolls instead of "…"; chips/pills selected =
  action blue; segmented labels wrap; `StockStatusBadge` + `StockTitleWithBadge` (SIMULATED no longer "SIMU-LATED"); `StockBanner`;
  editable `StockSearchField` (both, not wired yet); insight-card education text AA; row/news `textSupporting`/`textValue`/`textMeta`, iOS
  row values never truncate, Compose plain rows merged for TalkBack.
- Files: theme `Colors.kt`, new `StockSemanticStyles.kt`; Compose `designsystem/theme/StockStepsTheme.kt`, components `StockChip`, `StockTag`,
  `StockSegmentedControl`, `StockSearchBar`, new `StockBanner`, `StockInsightCard`, `StockRow`, `StockNewsCard`, `StockSampleDataBanner`,
  `icons/StockIcons`; screens `practice/PracticeScreens.kt`, `screener/ComparisonAiUi.kt` (badge swaps); iOS `Theme.swift`,
  `DesignSystem/StockComponents.swift`, `StockDetailComponents.swift`, `StockSettingsComponents.swift`, `StockNewsCard.swift`, new
  `StockStatusViews.swift`, `PracticeScenes.swift`, `ComparisonAiViews.swift`; test `SemanticStylesTest`; docs and `docs/design/phase2/`.
- Results: shared Android 75/0/0, shared iOS 71/0/0, `assembleDebug` OK, iOS `xcodebuild` BUILD SUCCEEDED.
- Environment: spare iPhone 16 Simulator restored (light, Large text) and shut down; local MOCK server stopped; Android emulator untouched.
- Next: Phase 3 Portfolio reference screen (on approval) (guidance in the Phase 2 doc §9; approval needed for
  screenshots).

## 000000. Global UI Refinement Phase 1A — intermediate component foundation

- Repository: `main`. Current commit: "Add Global UI Refinement Phase 1A: accessible buttons, wrapping metrics, states and form controls" (pushed), on top of "Add Global UI Refinement Phase 1: shared design-system tokens for Android and iOS" (pushed). Commit/push only when asked; every commit
  updates the root `PROJECT_HANDOFF.md`. Reference image (committed): `docs/design/stocksteps-ui-reference.png` (moved from
  `docs/StockSteps Financial UI Design System.png`).
- Phase 1A is an intermediate step between plan step 1 (tokens) and step 2 (reusable components), not Phase 2.
- Done (details `docs/GLOBAL_UI_REFINEMENT_PHASE1A.md`): reference-vs-token comparison (most differences kept for AA reasons); new
  `primaryAction` `0F6FDB` for primary buttons (Compose `StockButton`; iOS `.stockPrimary`/`.stockSecondary` replacing all 30
  `.borderedProminent`); `cardPadding` 12 → 16; wrapping `StockMetric` + `StockMetricGrid` on both platforms with shared
  `StockLayout.metricColumns`; full empty/error states (Compose `title`/`icon` params, iOS `StockStateMessage`); form controls
  (Compose `StockSelectField`; iOS `StockTextField` with keyboard Done, `StockSelectField`); `StockLabels.humanize`.
- Files: theme `Colors.kt`, `Spacing.kt`; Compose `designsystem/theme/StockStepsTheme.kt`, components `StockButton`, `StockCard`, `StockMetric`,
  `StockPriceChange`, `StockStates`, `StockTag`, `StockTextField`, new `StockSelectField`, `icons/StockIcons` (ChevronDown), new
  `designsystem/StockLabels.kt`; iOS `Theme.swift`, `DesignSystem/StockComponents.swift`, new `DesignSystem/StockControls.swift`, and the
  `.stockPrimary` swap in 10 screen files; tests `DesignTokensTest` (+2), new `StockLabelsTest`; docs.
- Results: shared Android 69/0, shared iOS 65/0, `assembleDebug` OK, iOS `xcodebuild` BUILD SUCCEEDED. Screens not visually reviewed.
- Next: Phase 2 (remaining reusable components), starting with the screenshot baseline (approval
  needed); then Phase 3 Portfolio reference screen.

## 00000. Global UI Refinement Phase 1 — design-system foundation

### Repository state
- Branch `main`. Current commit: "Add Global UI Refinement Phase 1: shared design-system tokens for Android and iOS" (pushed), on top of "Close Phase 5C.1 verification: final test matrix, backend check and Global UI Refinement plan" (pushed).
  Commit/push only when the user asks; every commit updates the root `PROJECT_HANDOFF.md`.

### What Phase 1 did (details, before/after tables: `docs/GLOBAL_UI_REFINEMENT_PHASE1.md`)
- Shared tokens (`app/shared/src/commonMain/kotlin/org/example/stocksteps/theme/`): dark palette moved to the refined navy targets; `textTertiary`
  fixed for WCAG AA (light `5F6F86`, dark `94A3B8`); text-role aliases `textTitle/textValue/textSupporting/textMeta`; new `brandGlow`,
  `primaryGradientEnd`; type scale raised (body 15, caption 12, tiny 11, titles/heroes larger; `largeNumber` 32 and `numberEmphasis` 18 held
  below targets on purpose); new spacing roles (`cardPaddingStandard/Spacious`, `contentGap`, `itemGap`, `labelValueGap`, `formFieldGap`) while
  `cardPadding`/`sectionGap` keep 12; `space6/10/14/30/40` deprecated (unused); corners button 12, card 16, cardLarge 20; `FinancialsTokens`
  reuses `ThemeDimensions`.
- Bridges: Compose `designsystem/theme/StockStepsTheme.kt` (new colours, aliases, spacing); iOS `Theme.swift` (default Dynamic Type style by
  token size, `lineSpacing`, `View.stockFont`, new colours/aliases).
- Sign-in: `presentation/account/AuthComponents.kt` and `AuthComponents.swift` use `primaryBright`/`brandGlow`/`primaryGradientEnd`/`onPrimary`
  (same RGB, no visual change); Google-button colours kept.
- Tests: new `app/shared/src/commonTest/.../theme/DesignTokensTest.kt` (10).

### Results (2026-10-10)
`./gradlew :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` BUILD SUCCESSFUL — shared
Android host 65/0, shared iOS 61/0, `assembleDebug` OK; iOS `xcodebuild` BUILD SUCCEEDED. Server/core not re-run (not touched). Screens were
not visually reviewed (no simulator/emulator use without approval).

### Risks / next
- Inherited changes: larger text everywhere → more truncation where screens force one line (Screener, Markets, At a Glance, Earnings);
  rounder cards; lighter dark cards, brighter metadata. White on `primary` is 3.67:1 (primary button label) — resolved in Phase 1A (`primaryAction`).
- Next: Phase 2 reusable components, starting with a light/dark × default/largest-text screenshot
  baseline (needs approval for simulator/emulator use). LAN redeploy (5C.1 brief wording) still awaiting approval.

## 0000. Phase 5C.1 — where the latest session stopped

### Repository state (verified at handoff, 2026-10-09 ~17:15 EDT)
- Branch `main`. Current commit: "Close Phase 5C.1 verification: final test matrix, backend check and Global UI Refinement plan" (doc-only, pushed) on top of
  `fc3961e` "Close Phase 5C.1: fix iOS navigation hang, search and duplicate requests, finish iOS walkthroughs" (pushed), `a50a2e3` Phase 5C (pushed),
  `1c1b073` Phase 5B Local (pushed).
- Commit/push only when the user explicitly asks; every commit updates the root `PROJECT_HANDOFF.md`.

### Current development objective
Close Phase 5C/5C.1 (mobile end-to-end integration against the LAN MOCK backend) cleanly, then start the **global UI refinement** phase
(design tokens → components → screens). Cloud Run staging stays deferred; Phase 5D (Firebase persistence) has not started.

### Exact task at the end of the session ("Finalize Phase 5C.1 and prepare for Global UI Refinement")
The user's last task list (Tasks 1–5) — status:
1. **Git review** — done: 5C.1 is already committed and pushed; `git diff --check` clean; no debug instrumentation (`P5C1-DIAG`, `_printChanges`,
   `NSLog`), credentials or LAN address in the commit (the only key-like string in the tree is the public Firebase client config in
   `GoogleService-Info.plist`, which predates 5C.1).
2. **Final verification** — done: full matrix + iOS `xcodebuild` green on the final code (results below; report §15). Hang regression strategy and
   missing UI-test coverage documented (report §13a).
3. **Backend alignment** — inspected read-only (user-requested): container healthy, image `stocksteps-local:a50a2e3cc833` (Phase 5C), `dataMode` mock,
   health/meta 200, `upstream` 0, only the MOCK AI template counted. The 5C.1 server change (`BriefWording.recentNews` used by
   `server/.../brief/DailyBriefService.kt`; server/core diff to HEAD 5 files) is **not deployed** — redeploy + smoke **awaiting the user's approval**.
4. **Close the report** — done: report §13 (closure note), §13a (regression strategy), §15 (final results), §16 (closure verdict PASS WITH CONDITIONS).
5. **`docs/GLOBAL_UI_REFINEMENT_PLAN.md`** — written (read-only proposal: token/component inventory, findings by issue with code evidence,
   the 7-step order); **awaiting the user's approval**, no UI implemented.

### Work completed in this session (all committed and pushed)
- Phase 5C commit (`a50a2e3`) after a diff review; LAN backend redeployed from it (`a50a2e3cc833`, cached build, ~13 s swap), smoke 23/23,
  learning sync regression ("now" → 200, "+2 days" → 400), `upstream` = 0.
- Phase 5C.1 (`fc3961e`), details in `docs/PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md` §9–§16:
  - iOS walkthroughs A–G on the spare iPhone 16 Simulator (iOS 18.3, ad-hoc signed, `--firebase-emulators`, Auth emulator accounts
    `p5c1-a/b@example.test` created via the emulator REST API) through a counting reverse proxy on the Mac (`127.0.0.1:8091` → LAN).
  - Fixed (#9–#22 in the report): **critical iOS hang** (Company Details → Earnings history / See full breakdown froze at 100 % CPU:
    `@Environment(\.openURL)` in scenes that own `navigationDestination`s → `ExternalURLOpener`); stuck search after fast typing (query `didSet`);
    search auto-focus; launch 19–21 → 13 requests; duplicate requests from presenters created in SwiftUI `init`s (earnings details/calendar/
    event/results/premium/digest, comparison `IosScreenerClient`, practice order with per-order scope + `release`); shared
    `ServerBackedRepository` loading race (watchlists/alerts 2×, Android too); portfolio digit grouping; source-link a11y labels; Android brief card
    at ≥1.3× font; stale iOS auth error; brief "1 of the 1 companies" grammar; compact comparison axis.

### Files created or modified this session
- `a50a2e3`: see §000 below.
- `fc3961e`: core `data/userdata/UserDataRepositories.kt`, `portfolio/Decimal.kt` (`PortfolioFormat`), `model/CompanyDetails.kt`
  (`WhyMovingSource.accessibilityLabel`), `brief/DailyBriefModels.kt` (`BriefWording.recentNews`); new tests `core/src/commonTest/.../portfolio/PortfolioFormatTest.kt`,
  `.../model/WhyMovingSourceTest.kt`, `.../brief/BriefWordingTest.kt`, +1 in `.../data/UserDataRepositoriesTest.kt`; server `brief/DailyBriefService.kt`;
  shared `presentation/brief/DailyBriefScreens.kt`, `presentation/companydetails/CompanyDetailsScreen.kt`, `iosMain/IosEarningsClient.kt`,
  `iosMain/IosPracticeClient.kt`; iOS new `ExternalURLOpener.swift` and `AccountViewModel`, `AuthScene`, `CompanyDetailsScene`, `CompanyDetailsScreen`,
  `CompanyNewsViews`, `ComparisonHistoryViews`, `EarningsPremiumViews`, `EarningsScenes`, `MarketsScene`, `PracticeScenes`, `ScreenerScenes`,
  `StockSearchScene`, `StockSearchScreen`, `StockSearchViewModel`; docs (report, handoffs, status, `CLAUDE.md`).
- Closure commit "Close Phase 5C.1 verification: final test matrix, backend check and Global UI Refinement plan": report §13/§13a/§15/§16, new `docs/GLOBAL_UI_REFINEMENT_PLAN.md`, `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`,
  root `PROJECT_HANDOFF.md`.

### Build and test results
Verified:
- Phase 5C code (before 5C.1): full matrix BUILD SUCCESSFUL — server 482/0 (3 skipped), core JVM 421/0, core iOS 421/0, shared Android 55/0, shared iOS 51/0,
  `assembleDebug` OK; iOS `xcodebuild` OK.
- Final 5C.1 code (HEAD `fc3961e`), closure re-run: server **482/0, 3 skipped** (Firestore emulator — skipped is not passed); core JVM **429/0**;
  **core iOS 429/0** (clean re-run 8 min 13 s after deleting the leftover `core/build/test-results/iosSimulatorArm64Test`; the earlier killed run,
  an `EOFException` run and a `NoSuchFileException` run were two sessions' Gradle builds colliding, not test failures); shared Android host **55/0**;
  shared iOS **51/0**; `:app:androidApp:assembleDebug` **OK**; iOS `xcodebuild` (generic simulator, ad-hoc signed) **BUILD SUCCEEDED**.
- Simulator: fixes #9–#16 exercised through the real UI with request counts from the proxy; LAN `upstream` counter 0 throughout.
Unverified:
- In the apps: #17 portfolio grouping, #18 a11y labels (no VoiceOver), #19 Android brief card at 1.3× (not seen on an emulator), #20 iOS auth error
  clearing, #22 comparison axis.
- Phase 5C.1 regression coverage for the hang/search/duplicate-request fixes is **simulator evidence only — there is no iOS UI test target**.
  Kotlin tests cover the shared repository race and the formatters only.

### Important decisions and reasons
- Lazy presenter creation + `.task` activation instead of restructuring `AppScene`/scenes (smallest change; SwiftUI keeps the first `@State`
  value but re-runs `init` and evaluates `navigationDestination` builders on every parent update).
- `ExternalURLOpener` (UIApplication) only in scenes that own destinations; other views keep `@Environment(\.openURL)`.
- Practice's two `me/practice` loads on first open kept (start + intentional `onAppear` refresh for freshness on re-entry).
- #7 (MOCK filler market cap from random shares in `SampleMarketData.fillQuote`) and #8 (MOCK quarter growth = annual from generated earnings
  history) deferred: MOCK-only, many fixture expectations depend on them; REAL unaffected.
- Accounts created via the Auth emulator REST API because simulator text injection drops characters in secure/numeric fields.

### Known bugs, blockers and risks
- LAN backend one commit behind for the server (5C.1 brief wording) — needs approved redeploy + smoke.
- No iOS UI-test target: the navigation-hang regression is guarded by the `CLAUDE.md` rule, a static check and manual walkthroughs only (report §13a).
- Minor UI issues (report §14): misleading chart error copy when offline; raw enum labels ("PERSONAL", "OPENING POSITION"); "SIMULAT-ED" badge wrap;
  truncated "+14.9 % (Y…"; P/E 39.0× (screener) vs 39.2 (details); no Done on iOS decimal keypads; Back from an event opened from the calendar
  returns to Markets; $0.01 practice "loss" from half-cent cost basis; MOCK brief stories stamped Oct 9; fixed-size captions/pills/banner under
  Dynamic Type; a Portfolio allocation row possibly not rendered in one screenshot (unconfirmed).
- Not run: physical Android/iPhone, VoiceOver/TalkBack, real Firebase/Google sign-in, push delivery, process-death restoration; Firestore
  persistence tests (3 skipped) / Phase 5D pending; Cloud Run staging deferred.
- Environment: spare iPhone 16 Simulator (`A04A9C8B-…`) left booted with throwaway account A signed in (text size restored); the user's booted
  iPhone 15 Pro / 16 Pro simulators untouched. Android `emulator-5554` still has the Phase 5C LAN/auth-emulator debug build and the user's real
  account signed out — reinstall the normal debug build and sign in (user). Firebase Auth emulator and counting proxy stopped;
  `app/iosApp/Configuration/Local.xcconfig` restored to the LAN URL. The 16:34 matrix + xcodebuild run finished at 16:44 (results above).

### Exact next steps
1. `git status` / `git log -3 --oneline` (expect HEAD "Close Phase 5C.1 verification: final test matrix, backend check and Global UI Refinement plan", pushed, clean tree).
2. Ask before any LAN host access. With approval: `deploy/local/deploy.sh deploy` (builds `stocksteps-local:<HEAD short SHA>`; downtime ~10–30 s for
   the container swap; all MOCK in-memory data reset; rollback `deploy/local/deploy.sh rollback a50a2e3cc833`), then `deploy/local/deploy.sh smoke`
   (23/23) and `curl …/internal/metrics/usage` (`upstream` 0). Record the result in report §16 and both handoffs.
3. Optionally verify #17–#20/#22 in the apps (simulator/emulator actions need approval).
4. Get the user's review/approval of `docs/GLOBAL_UI_REFINEMENT_PLAN.md`; then UI refinement step 1 (global design tokens). **No UI implementation**
   until the plan is approved.
5. Run only one Gradle/xcodebuild matrix at a time (two concurrent sessions collided on `:core:iosSimulatorArm64Test` results this session).

### Requirements discussed but not implemented
- Global UI refinement (plan doc first, then implementation); Phase 5D Firebase persistence; physical-device, screen-reader and real-auth testing;
  iOS UI-test target for navigation-loop regressions; #7/#8 fixture refresh; Cloud Run staging.

## 000. Phase 5C — where the latest session stopped (read first)

### Repository state (verified at handoff)
- Branch `main`, in sync with `origin/main`. HEAD `1c1b073` "Add Phase 5B Local Docker deployment of the MOCK backend on a LAN host with smoke and
  reliability checks" (pushed). Earlier this session and pushed: `d138b19` (Phase 5A Cloud Run preparation), `4586889` (Phase 4).
- **Phase 5C is committed** as "Fix Phase 5C mobile integration bugs and add end-to-end verification report" (on top of HEAD "Add Phase 5B Local Docker deployment of the MOCK backend on a LAN host with smoke and reliability checks"): 23 modified + 4 new files (listed below; docs included). Push only when the user asks.

### Current objective
Prove the Android and iOS apps work end to end against the LAN MOCK backend (Phase 5C), fix confirmed integration bugs, document results. The
broader program: financial API cost/safety (Phases 1–4 done) → deployment preparation (5A done) → local Docker backend (5B Local done) → mobile E2E
(5C, this session) → Cloud Run staging (deferred, only on explicit request).

### Exact task at the end of the session
Phase 5C testing and fixes are finished and documented; the session ended with the user asking for this handoff. No code was being edited.

### Work completed in Phase 5C (details: `docs/PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md`)
Bugs found, root-caused and fixed (all verified):
1. **MOCK Guided Research sync always failed** → `server/.../learning/LearningService.kt`: the "progress in the future" check used the MOCK market clock
   (pinned to the Oct 7 fixture capture); new `wallClock` parameter (real time) used only for that check. Test `LearningRoutesTest.futureCheckUsesRealTimeNotThePinnedMarketClock`.
   Redeployed to the LAN host (LAN `PUT /api/v1/me/learning` with "now": 400 → 200).
2. **Every iOS search failed** ("Could not load stocks") → `app/shared/src/iosMain/.../IosStockStepsClient.kt`: `scope.async { searchStocks(query) }`
   resolved to the member function (infinite self-cancelling recursion), same for `getCompanyNews`. Properties renamed `searchStocksUseCase`,
   `companyNewsUseCase`; internal test constructor. Test `app/shared/src/iosTest/.../IosStockStepsClientTest.kt` (2, MockEngine).
3. **iOS MOCK banner covered tab bar titles** → `app/iosApp/iosApp/ContentView.swift`: banner stacked under the app (VStack) instead of `.safeAreaInset`;
   background `ignoresSafeArea(.container, edges: .bottom)`.
4. **"1 shares"** (both platforms) → `PracticeFormat.shareCount` (core) used by `PracticeOrderScreen.kt`, `PracticeScreens.kt`, `PracticeScenes.swift`,
   `PracticeEngine` sell-limit message; `IosPracticeClient.shareCount`.
5. **Holding a11y "Apple Inc.."; iOS dropped the stale-price note** → shared `PracticeFormat.holdingDescription` (both platforms). Test `PracticeFormatTest` (3).
6. **Research step a11y "?. Not started"** → `ResearchStep.accessibilityLabel` in `core/.../learning/GuidedResearch.kt`, used by
   `GuidedResearchScreens.kt` and `GuidedResearchScenes.swift` (via `IosLearningClient.stepAccessibilityLabel`). Test `ResearchStepLabelTest` (2).
7. **iOS Settings (guest) "Sign in to sync" did nothing** → `AppScene.swift` closes the Settings sheet before presenting sign-in.
8. **iOS duplicate launch requests** (screener 6 per launch) → models built in `AppScene.init` take `start: false` and are activated once in
   `.task` (`ScreenerScenes.swift`, `EarningsScenes.swift`, `EarningsReminderViews.swift`, `GuidedResearchScenes.swift`, `DailyBriefScenes.swift`);
   other call sites keep the default `start: true`. Measured screener 6 → 2 per launch.

UI verification performed (approved by the user): Android `emulator-5554` (Android 17) — Home, Markets, Details (+P/E sheet, rotation), Search,
Screener, Comparison, Portfolio (account + opening position), Practice buy/sell, Watchlist + alert, Learn/Research, Brief (+ Plus AI), Earnings,
Settings, light/1.3× font. iOS spare iPhone 16 Simulator (iOS 18.3) — signed-out/guest, sign-up mode, sign-in (Firebase Auth emulator), sign-out,
account isolation A↔B, Learn sync, Watchlist, Search, Settings sign-in. Zero StockSteps crashes; LAN `upstream` provider counter stayed 0.

### Files created or modified (in commit "Fix Phase 5C mobile integration bugs and add end-to-end verification report")
- Server: `server/src/main/kotlin/org/example/stocksteps/learning/LearningService.kt`; test `server/src/test/.../learning/LearningRoutesTest.kt`.
- Core: `core/src/commonMain/.../practice/{PracticePresentation,PracticeEngine}.kt`, `core/src/commonMain/.../learning/GuidedResearch.kt`;
  new tests `core/src/commonTest/.../practice/PracticeFormatTest.kt`, `core/src/commonTest/.../learning/ResearchStepLabelTest.kt`.
- Shared app: `app/shared/src/commonMain/.../presentation/practice/{PracticeOrderScreen,PracticeScreens}.kt`,
  `.../presentation/research/GuidedResearchScreens.kt`; `app/shared/src/iosMain/.../{IosStockStepsClient,IosPracticeClient,IosLearningClient}.kt`;
  new test `app/shared/src/iosTest/.../IosStockStepsClientTest.kt`.
- iOS: `app/iosApp/iosApp/{ContentView,AppScene,PracticeScenes,GuidedResearchScenes,ScreenerScenes,EarningsScenes,EarningsReminderViews,DailyBriefScenes}.swift`.
- Docs: new `docs/PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md`; updated `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`, root `PROJECT_HANDOFF.md`, `CLAUDE.md`.

### Build and test results
Verified (final code, 2026-10-09):
- `./gradlew :server:test :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`
  → BUILD SUCCESSFUL in 9m 19s: server 482 passed / 0 failed / 3 skipped (Firestore emulator); core JVM 421/0; core iOS 421/0; shared Android host 55/0;
  shared iOS 51/0; `assembleDebug` OK.
- `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGN_IDENTITY=- CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=YES build` → BUILD SUCCEEDED.
- LAN smoke suite last run 23/23 in Phase 5B (not re-run after the 5C redeploy; health/meta and the learning PUT were checked).
Unverified / not run: iOS walkthroughs of Screener, Comparison, Portfolio, Practice, Earnings, Brief detail, Company Details; physical devices; real
Firebase/Google sign-in; push; VoiceOver/TalkBack sessions; Dynamic Type; process-death restoration; Firestore-emulator tests (3 skipped).

### Important decisions and reasons
- Fix accessibility/copy strings in **shared core helpers** (`PracticeFormat`, `ResearchStep`) so Android and iOS read identical sentences.
- iOS model side effects deferred with `start: false` + `.task { activate() }` instead of restructuring `AppScene` (smallest change; SwiftUI keeps only the
  first `@State` value but re-runs `init`).
- Banner placement via VStack (UIKit tab bar ignores SwiftUI `safeAreaInset`).
- Auth testing only with the **Firebase Auth emulator** (`127.0.0.1:9099`, `firebase/node_modules/.bin/firebase emulators:start --only auth --project stocksteps --config ../firebase.json`);
  throwaway `@example.test` accounts; credentials never committed. Android: `-PfirebaseEmulators=true` + `adb reverse tcp:9099 tcp:9099`; iOS: launch
  argument `--firebase-emulators` and an **ad-hoc signed** simulator build (unsigned builds fail Firebase Auth with keychain error 17995).
- The LAN address stays out of the public repo (`deploy/local/server.env`, `app/iosApp/Configuration/Local.xcconfig` are git-ignored).

### Known bugs, blockers and risks
- Minor, open: three identical "Yahoo" source-link labels on Details; Brief card meta cramped at 1.3× font (Android); Portfolio totals "CAD 2145.53"
  without grouping; iOS auth error stays visible after editing the password; keychain errors map to the generic sign-in message; iOS search field
  needs a second tap to focus after the sheet opens; "1 of the 1 companies … have" grammar in Brief Plus insights; MOCK fixtures: implausible filler
  market cap, latest-quarter growth equal to annual growth.
- Earnings requests per iOS launch 5–7 — audit which endpoints.
- **User environment side effects**: the Android emulator has the LAN/auth-emulator debug build installed and the user's real-account session there
  was signed out (reinstall the normal debug build and sign in). The Mac's own MOCK server (pid started by the user) was left running on :8081.
- **LAN host**: running image `stocksteps-local:1c1b0731adb0-dirty-f8eecdbc` — verified in Phase 5C.1: its content hash over `core/` + `server/` equals the
  uncommitted 5C tree, so it already ran the tested code; a redeploy after the commit only changes the image tag to the clean commit. Still there from Phase 5A: `/tmp/stocksteps-phase5a-verify.*`, image
  `stocksteps-phase5a-verify:local`, build cache; SSH user in the `docker` group (owner decisions pending).
- Phase 4/5A conditions unchanged: Cloud Run staging deferred; owner inputs (`docs/CLOUD_RUN_DEPLOYMENT_CHECKLIST.md` §1), image vulnerability scan,
  scheduler OIDC verification, `TRUSTED_PROXY_HOPS` verification.

### Exact next steps
1. `git status` / `git diff --stat` — confirm the Phase 5C file list above; run the matrix only if code changed since.
2. When the user asks: commit Phase 5C (suggested title "Fix Phase 5C mobile integration bugs found against the LAN MOCK backend and add end-to-end
   test report"), updating the root `PROJECT_HANDOFF.md` to "committed" in the same commit; push only if asked.
3. Redeploy the LAN backend from the committed tree: `deploy/local/deploy.sh deploy`, then `deploy/local/deploy.sh smoke` (expect 23/23).
4. Finish the iOS walkthroughs marked NOT RUN (report §2/§7) on a spare simulator with ad-hoc signed builds; then physical-device checks.
5. Optional small fixes from the minor list; audit iOS launch earnings requests.
6. Cloud Run staging (Phase 5B proper) only on explicit request, after the Phase 5A owner inputs.

### Requirements discussed but not implemented
- Physical Android/iPhone end-to-end, real Firebase/Google sign-in, push delivery, VoiceOver/TalkBack and Dynamic Type sessions, process-death tests.
- Live fault injection (timeouts/429/500) on devices — covered only by existing MockEngine tests.
- Cloud Run staging deployment, App Check SDKs in the apps, image vulnerability scanning.

## 00. Phase 5B Local — where the latest session stopped

- Phase 5B Local = local integration milestone. Cloud Run staging deployment remains deferred and unverified.
- HEAD: **"Add Phase 5B Local Docker deployment of the MOCK backend on a LAN host with smoke and reliability checks"** (pushed), on top of `d138b19` "Add Phase 5A Cloud Run deployment preparation: container image, health probes, startup validation, JSON logging and staging docs". Phase 5B Local contents: `deploy/local/` (Compose, deploy/smoke/reliability scripts,
  `server.env.example`), iOS `Config.xcconfig` optional include + `Local.xcconfig.example`, `.gitignore`, two new docs, handoff/status updates.
  Git-ignored local files: `deploy/local/server.env` (LAN host), `app/iosApp/Configuration/Local.xcconfig` (LAN mock URL).
- Running: MOCK backend `stocksteps-local:d138b193ad44` on the LAN Ubuntu host (`~/stocksteps-local`, port 8081 on the LAN IP), healthy.
- Verdict **PASS WITH CONDITIONS**: remaining manual checks = Android app end-to-end (emulator/phone) and a physical iPhone
  (`LOCAL_DEVELOPMENT_SERVER.md` §5–§6). Next: the manual device checks; Cloud Run staging only on explicit request.

## 0. Phase 5A — where the latest session stopped

- HEAD: **"Add Phase 5A Cloud Run deployment preparation: container image, health probes, startup validation, JSON logging and staging docs"** on top of `4586889` (Phase 4); both pushed to `origin/main`. The commit also contains the earlier doc-only handoff edits. Nothing was deployed or created in Google Cloud; no real provider calls.
- Done: `Dockerfile` + `.dockerignore` (allow-list), `settings.gradle.kts` `-Pstocksteps.serverOnly=true`, `HealthRoutes.kt` (`/health/live`,
  `/health/ready`), `appconfig/StartupConfiguration.kt` (fail-fast validation, Cloud Run requirements), explicit shutdown grace/timeout in `main()`,
  `logging/CloudLoggingJsonLayout.kt` + `logback-{text,json}.xml` (`LOG_FORMAT`), `CloudRunReadinessTest` (8), `deploy/cloud-run-staging.yaml`,
  `docs/CLOUD_RUN_{ENVIRONMENT,SECRETS,COST_AND_SCALING,DEPLOYMENT_CHECKLIST}.md`, `docs/FINANCIAL_API_PHASE5A_IMPLEMENTATION.md`.
- Verified: `./gradlew :server:test --continue` → 481 passed, 0 failed, 3 skipped (Firestore emulator). Image built (`linux/amd64`) and
  container-verified on the owner's LAN Ubuntu server (report §6a; Docker is not installed on the Mac). Server changes made with the
  owner's approval: `docker-buildx` installed, the SSH user added to the `docker` group. Left there: a `/tmp/stocksteps-phase5a-verify.*` directory, image
  `stocksteps-phase5a-verify:local`, 2.59 GB build cache (removal pending the owner's decision).
- Verdict: **READY WITH CONDITIONS** for Phase 5B. Next:
  scan the image for vulnerabilities; owner inputs (checklist §1); verify scheduler OIDC behind
  IAM-required invocation before enabling jobs. **Do not deploy (Phase 5B) without an explicit request.**

## 1. Repository state at handoff (verified)

- (Phase 3/4 session state below is historical: Phase 4 `4586889` has since been pushed together with the Phase 5A commit.)
- Branch `main`, then one commit ahead of `origin/main` (Phase 4 committed, not pushed).
- HEAD: **`4586889` "Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage monitoring"** (Phase 4), on top of `6a70d0e` "Verify financial API Phase 3 with before/after benchmarks and fix earnings-aware statement coverage".
- Commits made in this session: `517a46b` "Implement financial API Phase 3: screener re-warm fix, 150-company universe, selective
  statements, market- and earnings-aware freshness, stale fallback" (pushed); `6a70d0e` (above, pushed); **`4586889` Phase 4 (NOT pushed)**.
- Phase 4 = `4586889`: 51 files (31 modified, 20 added; +3,053/−138) — see §5. Working tree clean apart from this handoff's doc edits.
  Nothing was deployed; no cloud infrastructure or production Firestore was touched; no REAL provider calls. No MOCK server running.
- Shareable results document (Claude Docs, private until shared from its Share menu):
  https://claude.ai/code/artifact/c4ca8cbb-855c-4aa2-a43a-feecd79b4da9 ("StockSteps Financial API Phase 4 Results").

## 2. Current development objective

Financial API cost and safety program: reduce paid provider usage (FMP, Finnhub, Gemini, Bank of Canada) without losing financial correctness,
then make the backend safe for a controlled public launch. Phases 1–3 are done, committed and verified. **Phase 4 (production API security,
durable AI quotas, provider-wide budgets, cost monitoring) is implemented, fully tested and committed (not pushed).** Verdict: READY WITH CONDITIONS —
not launchable until the deployment-side settings in §8 are made and verified.

## 3. Exact task at the end of the session

Phase 4F verification was completed (full matrix green, provider-path audit, anonymous-pool audit and fix, older-client compatibility, §9 of the
implementation report), a shareable results doc was produced, the Phase 4 diff was reviewed (no secrets, debug output or unrelated changes; only
docs changed after the final test run) and Phase 4 was committed as `4586889` (not pushed). This handoff was then refreshed (docs only,
uncommitted). The next task is to **push `4586889` (and these doc edits, after committing them) only when the user asks**, then work through the
production configuration blockers (§8). Phase 5 has not been started and must not be started without an explicit request.

### Start-of-session checklist for the next session
1. `git status -sb` → expect `main...origin/main [ahead 1]` (or `[ahead 2]` if these doc edits were committed) and only doc changes.
2. `git log --oneline -3` → `4586889` on top of `6a70d0e`.
3. Read `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md` §4 (configuration), §8–§9 (blockers, validation) before touching deployment settings.
4. Re-run tests only if code changes: `./gradlew :server:test` (fast), full matrix in §9 below.

## 4. Work completed in this session

### 4.1 Phase 3 implementation — commit `517a46b`
3B-0 screener re-warm fix (expired fundamentals reloaded oldest-first within `SCREENER_FUNDAMENTALS_PER_HOUR`, failure backoff, bounded maps);
3A `statementHistory` selective statements (1Y history income only); 3B-1 default universe 50 NASDAQ + 50 NYSE + 50 TSX = 150, screener dataset set
(11 requests per cold company), FMP dataset cache 4,096 entries; 3C `MarketFreshnessPolicy` (US/TSX calendars) for quotes, TTM ratios, intraday bars;
3D `EarningsStatementSignals` (2 h statement lifetime after a reported quarter until it appears); 3E labelled stale fallback (statements ≤ 7 d,
estimates ≤ 2 d), additive `CompanyFundamentals.freshness`/`staleDatasets`, Financials stale notice on Android and iOS.
Details: `docs/FINANCIAL_API_PHASE3_IMPLEMENTATION.md`.

### 4.2 Phase 3 verification — commit `6a70d0e`
Benchmark A–H rewritten (`Phase3AuditBenchmarkTest`, output `server/build/phase3-benchmark.txt`); pre-Phase 3 baseline re-measured on `e4ba2be`.
Fix: earnings "covered" rule (the 105-day fallback could mark a fast reporter's previous quarter as covered → now exact `periodEnd` or "newer than
the baseline cached when the report arrived"). Injectable wall clock for FMP retrieval times. Results: A 17→17, B 72→64, cold 1Y history 56→8,
screener first hour 328→278, 24 h 1,953 (bug) → 4,353 with full coverage, closed market 39→16, new quarter visible 19 h → 30 min.

### 4.3 Phase 4 audit (read-only) — in commit "Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage monitoring"
`docs/FINANCIAL_API_PHASE4_SECURITY_AUDIT.md` (route inventory, findings S1–S14), `…_QUOTA_ARCHITECTURE.md`, `…_COST_MODEL.md`,
`…_OBSERVABILITY.md`, `…_DECISIONS.md` (D1–D8 register), `…_IMPLEMENTATION_PLAN.md`. Key P0s found: TRACE logging wrote FMP URLs with the
`apikey` query parameter (114,826 occurrences in one test run's output); random-symbol cost amplification (13–17 requests per unknown symbol);
watch-data fan-out (100 symbols ≈ 300 requests); no limits on public provider routes.

### 4.4 Phase 4 implementation — in commit "Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage monitoring" (report: `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md`)
- **4A-0 logging**: `server/src/main/resources/logback.xml` root INFO, `io.ktor` + `io.ktor.client` WARN, `io.ktor.server.Application` INFO, Google/gRPC
  WARN; separate plain-JSON appender for `StockSteps.Usage`.
- **4A public API protection** (package `security/`):
  - `ClientIdentity.kt`: verified Firebase uid → `User`; else the client IP at position `TRUSTED_PROXY_HOPS` from the right of `X-Forwarded-For`
    (IP literals only, IPv6 grouped by /64) → `Address`; else `Unverified`. Never `remoteHost`, never the first XFF value. `limiterKey()` for route
    limiters (null for unverified callers; legacy `remoteHost` only when the plugin isn't installed, i.e. route tests).
  - `Admission.kt`: `installAdmission` intercepts before routing: query ≤ 2,048 chars (414), body size per group (413; chunked without length 411),
    App Check guard, then per-`RouteGroup` admission (market data, screener, history, research, earnings, brief, public AI, premium AI, user data).
    Units = 1 per request + 1 per FMP/Finnhub/BoC call + 20 per Gemini call **actually made** (`RequestCost`, counted in `ProviderCalls.record`).
    Per-identity windows, per-identity and per-group in-flight caps, bounded state (10,000 identities/group), 429 `RATE_LIMITED` + `Retry-After`.
    **Unverified callers share one pool per group per instance that is charged only for upstream work** (cache hits free) — changed during
    verification so cheap floods can't lock guests out. Defaults in `AdmissionPolicy.DEFAULTS`; env overrides `ADMISSION_<GROUP>_…`.
  - `AppCheck.kt`: `FirebaseAppCheckVerifier` (JWKS, issuer/audience, optional app ids) + `AppCheckGuard` (monitor mode by default; counts
    `appcheck.valid|missing|invalid|unconfigured`; `APP_CHECK_ENFORCE=true` rejects; enforcement without a verifier fails startup). Not verified
    against real tokens.
  - `InternalAuth.kt`: `InternalJob` per-job secrets (`ALERTS_EVALUATOR_TOKEN`, `EARNINGS_REMINDERS_TOKEN`, `DAILY_BRIEF_DISPATCH_TOKEN`,
    `USAGE_METRICS_TOKEN`; ≥ 32 chars; a value shared by jobs disables those jobs), constant-time compare, optional Cloud Scheduler OIDC
    (`INTERNAL_OIDC_AUDIENCE`, `INTERNAL_OIDC_SERVICE_ACCOUNT`). Used by the four `/internal/…` routes (signatures unchanged).
  - `service/SymbolExistence.kt`: before fundamentals/details/valuation/movement and comparison bundles: cached profile, else quote; only a
    successful "neither exists" is negative-cached 24 h → 404 `SYMBOL_NOT_FOUND`; provider failure → `UNKNOWN` (request continues, never cached).
  - Watch-data: ≤ `WATCH_DATA_ANONYMOUS_MAX_SYMBOLS` (30) without a verified uid (`TOO_MANY_SYMBOLS`), signed-in 100, ≤ 8 symbols looked up at once
    per request; shared client `StockStepsApi.getWatchData` splits into chunks of `WATCH_DATA_CHUNK` = 30 and merges in order.
  - Existing route limiters (screener, history, brief, earnings) now key on `limiterKey()`.
- **4B durable AI quotas**: `service/AiQuota.kt` `DurableAiQuota` (feature-tagged charges in `users/{uid}/meta/aiUsage`, per-feature retention so
  features don't prune each other, idempotency keys, `settleFailure`: refunds failures that produced nothing, keeps timeouts/cancellations, store
  outage → `AiQuotaUnavailable` → 503 fail closed, optional `CombinedAiCap` disabled unless `STOCKSTEPS_PLUS_AI_DAILY_CAP`). Daily Brief AI
  (`brief-ai`, 15/day) and `EarningsAiQuotaLedger` (explanation 10, question 20, digest 3 per day; now suspend + durable; global per-day budget still
  per instance) use it. Optional `idempotencyKey` added to `BriefAiRequest` and `EarningsAiQuestion` (additive). `ComparisonAiQuota` unchanged.
- **4C provider budgets**: `service/ProviderBudget.kt` `ProviderGuard` (token bucket, burst, concurrency, priorities HIGH/NORMAL/LOW with a 30 %
  interactive reserve, slot waiting HIGH 2 s / NORMAL 5 s / LOW 10 s, circuit breaker: 429 pauses the provider for Retry-After else 30 s doubling to
  5 min; 5 consecutive 5xx/timeouts pause that endpoint; half-open single trial; 402/403 never trip). Enforced per attempt in `apiCall`
  (`httpclient/NetworkUtils.kt`), `GeminiJsonCall` (`news/GeminiInsights.kt`), `GeminiNewsSimplifier` (LOW) and `BankOfCanadaPortfolioFx`
  (`userdata/PortfolioMarketService.kt`). Denials = `StockProviderException(UNAVAILABLE)` with no upstream count → existing fallbacks (stale
  labelled data, unavailable, AI fallbacks; Practice refuses fills without a fresh quote). Screener warm-up runs LOW; alert/reminder/brief
  dispatch NORMAL. Installed globally only in REAL (`ProviderGuard.installed`); tests put a guard in the coroutine context. Config
  `ProviderBudgetConfig.fromEnvironment`: `PROVIDER_{FMP,FINNHUB,GEMINI,BOC}_{PER_MINUTE,BURST,CONCURRENCY,DAILY_TARGET}`, `PROVIDER_SAFETY_MARGIN`
  (0.8), `CLOUD_RUN_MAX_INSTANCES`; without them **development defaults** (FMP 600/min burst 600 conc. 24; Finnhub 60; Gemini 60, conc. 4; BoC 30)
  and a "not production-ready" warning.
- **4D monitoring**: `service/UsageSummary.kt` `UsageSummaryReporter` (REAL; `USAGE_SUMMARY_SECONDS`, default 60) logs one JSON line per instance
  per interval (`kind: stocksteps.usage`, counter deltas by provider/endpoint/feature/event, event deltas, gauges incl. `screener.coverage.*`) for
  logs-based metrics. `ProviderUsageMeter.gauge` + `snapshot`.

## 5. Files created or modified (Phase 4 commit)

- New main: `security/{ClientIdentity,Admission,AppCheck,InternalAuth}.kt`, `service/{SymbolExistence,AiQuota,ProviderBudget,UsageSummary}.kt`.
- Modified main: `resources/logback.xml`, `Application.kt`, `CompanyFinancialRoutes.kt`, `brief/DailyBriefRoutes.kt`, `brief/DailyBriefService.kt`,
  `earnings/{EarningsAi,EarningsPremiumService,EarningsReminderService,EarningsService}.kt`, `httpclient/NetworkUtils.kt`,
  `news/{GeminiInsights,GeminiNewsSimplifier}.kt`, `screener/{ComparisonHistoryService,ScreenerRoutes,ScreenerService}.kt`,
  `service/ProviderUsage.kt`, `userdata/{PortfolioMarketService,UserRoutes}.kt`.
- Core (KMP): `network/StockStepsApi.kt` (watch-data chunking), `earnings/EarningsPremium.kt`, `brief/DailyBriefModels.kt` (optional
  `idempotencyKey`); test `network/StockStepsApiTest.kt` (+2).
- New tests: `service/LoggingSecurityTest.kt` (2), `security/PublicApiProtectionTest.kt` (13), `service/DurableAiQuotaTest.kt` (6),
  `service/ProviderBudgetTest.kt` (10), `service/UsageSummaryTest.kt` (3). Modified tests: `brief/DailyBriefServiceTest.kt` (+1 cross-instance),
  `earnings/EarningsPremiumTest.kt` (timeouts now charged), `market/MarketSnapshotTest.kt` (thread-safe list), `service/FmpMock.kt`
  (unknown symbols, failing symbols, Finnhub events, concurrency peak), `service/Phase3AuditBenchmarkTest.kt` (24 h run under the dev budget).
- Docs: 7 new `docs/FINANCIAL_API_PHASE4_*.md`; updated `CLAUDE.md`, root `PROJECT_HANDOFF.md`, `docs/project-status.md`, this file.
- Android/iOS UI code: not changed in Phase 4.

## 6. Important decisions and reasons

- **Never trust forwarded headers without a verified hop count**: the deployed ingress is unknown; trusting the wrong XFF entry is spoofable.
  Unverified guests share a large pool charged only for upstream work, never a small proxy-keyed bucket (that throttled every guest together).
- **Admission charges actual upstream calls**, so cache hits are cheap and cold or AI-heavy requests cost what they cost.
- **Per-instance limits are not global**; the provider budget (`plan × margin ÷ maxInstances`) is the deployment-wide backstop once
  `CLOUD_RUN_MAX_INSTANCES` matches the real maximum. No Redis/Memorystore/Firestore counters for request admission (cost, contention).
- **Budget denial = provider outage semantics** so every existing fallback applies and no route can bypass a provider's budget.
- **Durable AI quotas reuse the existing `aiUsage` document** (no new collection/migration); per-feature retention; **timeouts and cancellations
  keep the charge** (the provider may have billed) — this deliberately changed the earlier "timeouts aren't charged" behaviour.
- **Combined StockSteps+ AI cap implemented but disabled** (owner decision D6). **App Check monitor mode only**, enforcement flag off.
- **Per-job scheduler secrets, fail closed** (a shared or short secret disables the route) — operator reconfiguration required.
- **Watch-data cap 30 for unverified callers** with client chunking; signed-in callers keep 100 (watchlists hold up to 100).
- Dev-default provider budget raised to FMP burst 600 and background slot waiting after the benchmark showed the screener warm-up being deferred.
- **Phase 4E shared caching deferred**: provider licensing (D3) unconfirmed.

## 7. Known bugs, blockers and risks

1. **Production configuration not done** (§8) — launch-blocking.
2. Without a trusted client IP, one abuser can still use up the shared provider budget (others get cached/stale/unavailable data, never invented
   values). Fix: `TRUSTED_PROXY_HOPS` (D1) + App Check.
3. **Older app builds** with watchlists > 30 stocks get `TOO_MANY_SYMBOLS` (mitigation `WATCH_DATA_ANONYMOUS_MAX_SYMBOLS=100`); unknown symbols now
   return 404 `SYMBOL_NOT_FOUND` on fundamentals/details/valuation/movement instead of 200 with empty data.
4. Existing deployments using one `ALERTS_EVALUATOR_TOKEN` for every job lose earnings reminders, brief dispatch and usage metrics until the new
   per-job secrets (or OIDC) are configured.
5. App Check verifier untested against real tokens; App Check SDKs not in the apps.
6. Global per-day AI budgets (Comparison 2,000; Earnings 5,000) and anonymous AI hourly budgets are still per instance.
7. Article insight results cache per instance (no shared persistence added).
8. Known flaky test (pre-existing): `PracticeServiceTest.concurrentOrdersCannotOverspendOrBypassTheLimit` (not seen this session).
9. Disk space on the dev machine is tight (~30 GB free); use default DerivedData for iOS.

## 8. Production-only configuration blockers (complete list)

| # | Item | Decision | Blocks launch |
|---|---|---|---|
| 1 | Confirm the deployed logging config; if TRACE was ever deployed, rotate `FMP_API_KEY` and review log access | — | yes |
| 2 | Verify ingress (direct Cloud Run vs load balancer) with a forged `X-Forwarded-For`, then set `TRUSTED_PROXY_HOPS` (1 or 2) | D1 | yes |
| 3 | Cloud Run max instances = `CLOUD_RUN_MAX_INSTANCES` | D4 | yes |
| 4 | `PROVIDER_*` plan limits (per minute, burst, concurrency, daily target) | D5 | yes |
| 5 | Cloud Scheduler OIDC or the four per-job secrets | — | yes |
| 6 | `WATCH_DATA_ANONYMOUS_MAX_SYMBOLS=100` if older builds are installed | — | yes, if old builds exist |
| 7 | App Check: Firebase registration, Play Integrity / App Attest-DeviceCheck, SDKs, `FIREBASE_PROJECT_NUMBER`, `APP_CHECK_APP_IDS`; enforce later | D1 | no (monitor) |
| 8 | Logs-based metrics, dashboard, alerts, billing budgets 50/80/100 % | D7 | no (advised) |
| 9 | AI policy: combined cap, per-instance AI budgets, Gemini project quota | D6 | no |

Unknown (owner input): provider plan limits and prices, Cloud Run settings, traffic, caching rights (D3).

## 9. Build and test results

Verified this session on the final Phase 4 code:
- `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
  :app:androidApp:assembleDebug --continue` → **BUILD SUCCESSFUL in 9 m 1 s**: server **473/0 (3 skipped — Firestore emulator)**, core JVM **416/0**,
  core iOS simulator **416/0**, shared Android host **55/0**, shared iOS simulator **49/0**, `assembleDebug` up to date (built earlier from the same
  Android sources, including the Phase 4 client change). 1,409 tests, 0 failures.
- `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator'
  CODE_SIGNING_ALLOWED=NO build` → **BUILD SUCCEEDED**.
- Phase 3 benchmark numbers unchanged under Phase 4 (A 17, B 64, B′ 8, D 278, D24 4,353, F 16, G 253, H 15→15); 24 h screener under the dev
  provider budget 4,353 requests, full coverage, 0 denials. 1,000 unknown symbols ≤ 2,000 requests (was ≈ 13,000 modeled); 429 storm 100 → 1
  upstream; five instances never exceed the plan.
Not verified: REAL FMP/Finnhub/Gemini/BoC calls, production Firestore (`aiUsage` transactions), Firestore emulator tests, Cloud Run topology/XFF,
real App Check tokens, Cloud Scheduler OIDC, device/simulator walkthroughs, logs-based metrics in Cloud Logging.

## 10. Exact next steps

1. Push the Phase 4 commit when the user asks (never push or deploy otherwise).
2. Production configuration (§8 items 1–6), each verified on the deployment (needs owner access/decisions D1, D4, D5).
3. App Check client integration (Android Play Integrity, iOS App Attest/DeviceCheck) in monitor mode; enforcement later.
4. Monitoring setup from `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md` §10.
5. Optional follow-ups: shared Firestore store for article insights; durable global AI budgets; stale fallback for profiles/daily closes;
   market-aware watch-data quote caches; set the anonymous pool below the provider budget if guest abuse appears.

## 11. Requirements discussed but not implemented

- Phase 4E shared/persistent caching (blocked on licensing D3 and production evidence).
- App Check enforcement and client SDKs; Cloud Armor/API Gateway (not justified yet).
- Global (cross-instance) AI budgets and anonymous-AI result sharing; combined StockSteps+ AI cap activation (D6).
- Cloud Monitoring dashboards/alerts/billing budgets (documented only; no cloud resources created).
- Password reset flow (no backend support; unchanged).

## Earlier handoff (2026-10-09, morning: Comparison Phase 5 / sign-in redesign / financial API Phases 1–3 audit) — kept for history

Read order for a new session: `CLAUDE.md` → **this section** → `docs/project-status.md` §0 → the root `PROJECT_HANDOFF.md` top sections
(canonical per-commit milestone log) → the feature docs named below → the code. Always start with `git status` and `git log -5 --oneline`;
the repository is authoritative when docs disagree.

## 1. Repository state at handoff (verified)

- Branch `main`, in sync with `origin/main`, working tree clean except this documentation update (uncommitted).
- HEAD: **`e4ba2be` "Add financial API Phase 3 audit (selective loading, screener warm-up, freshness) with request benchmark"**.
- Commits made in this session (oldest → newest; all pushed):

  | Commit | Title | Size |
  |---|---|---|
  | `e27991d` | Add Company Comparison Phase 5 AI comparison assistant (StockSteps+) and redesign sign-in screens on Android and iOS | 34 files, +3,804/−312 |
  | `75760ab` | Add financial API cost audit and Phase 2 shared provider cache with request reuse | 33 files, +1,555/−71 |
  | `e4ba2be` | Add financial API Phase 3 audit (selective loading, screener warm-up, freshness) with request benchmark | 8 files, +680/−3 |

  (The session started at `423f44a`, "Add Company Comparison Phase 4 guided research checklist …".)
- A local MOCK server is **not** running (a temporary one on port 8091 was started for a smoke test and stopped).

## 2. Current development objective

**Reduce paid financial-provider usage (FMP, Finnhub, Gemini, Bank of Canada) without losing financial correctness or freshness.**
This is a phased program: Phase 1 audit (done) → Phase 2 shared cache & request reuse (done) → Phase 3 audit (done) → **Phase 3
implementation (next; nothing started)** → Phase 4 budgets/observability/durable quotas → Phase 5 verification.

**Exact task to pick up**: Phase 3 milestone **3B-0 — fix the screener re-warm bug and size the FMP dataset cache**
(`docs/FINANCIAL_API_PHASE3_IMPLEMENTATION_PLAN.md` §2). It is first because it is a correctness defect, not an optimisation.

## 3. Work completed in this session

### 3.1 Company Comparison Phase 5 — AI Comparison Assistant (StockSteps+) — in `e27991d`
Spec: `docs/SCREENER_AND_COMPARISON.md` → "Company Comparison — Phase 5".
- Core `screener/ComparisonAi.kt`: `FinancialEvidence` registry (stable ids like `AAPL.pe`, `MSFT.h.revenue.FY2025`, `I.netMargin`,
  `L.pe`), `ComparisonGrounding.build` (compact context from the cached Phase 1 comparison, Phase 2 interpretation and Phase 3 history),
  `ComparisonAiFocus` (keyword routing so a P/E question only ships P/E/P/S evidence), `ComparisonAiValidator` (evidence ids must exist,
  numbers must come from cited evidence, rejects advice/rankings/predictions/unhedged causes/links/leaks; removes unverifiable
  statements, regenerates once, then falls back to deterministic readings), `ComparisonQuestionScreen` (advice/injection answered without
  AI), `ComparisonAiSuggestions`, request/response/usage models. `ComparisonAiPresentation.kt`: `ComparisonAiPresenter` (one conversation
  per account + company selection, idempotency key reused on retry, double-tap guard, explicit note consent), `RemoteComparisonAi`;
  `UserApi.compareAiSummary/Ask/Usage`.
- Server `screener/ComparisonAiService.kt`: `ComparisonAiExplainer` (`TemplateComparisonAi` in MOCK, `GeminiComparisonAi` in REAL),
  durable `ComparisonAiQuota` (10/day, 50/rolling 30 days; env `COMPARISON_AI_DAILY_LIMIT`, `COMPARISON_AI_30_DAY_LIMIT`,
  `COMPARISON_AI_GLOBAL_DAILY_BUDGET`) via new `UserDataStore.updateAiUsage` (Firestore transaction at `users/{uid}/meta/aiUsage`),
  routes `POST /api/v1/me/compare/ai/summary`, `POST /api/v1/me/compare/ai/ask`, `GET /api/v1/me/compare/ai/usage`; shared cache for
  public summaries only; notes read server-side only with consent and never shared; token metering (`GeminiJsonCall.generateWithUsage`).
- Android `presentation/screener/ComparisonAiUi.kt`, `ComparisonAiScene` + `ComparisonAiRoute` (reuses the Compare ViewModel), Compare
  entry card, research-checklist "Ask StockSteps AI about this"; iOS `ComparisonAiViews.swift`, `IosScreenerClient` AI bridge.
- Removed the unused client-side `ComparisonExplainer`/`NoComparisonExplainer` no-op.

### 3.2 Sign-in / create-account UI redesign (Android + iOS) — in `e27991d`
- Root cause of "changes not visible": the auth screens were styled by a separate hard-coded `AuthTokens` palette, not
  `StockStepsTheme`, so theme/design-system edits never reached them and they ignored dark mode.
- Rebuilt `presentation/account/AuthScreen.kt` + `AuthComponents.kt` (Compose) and `AuthScreen.swift` + `AuthComponents.swift`
  (SwiftUI) on `StockStepsTheme` (dark `#07111C/#0D1B2A`, primary `#1683FF`, light `#F7FAFD`): header (blue "S" tile, wordmark,
  tagline), hero "Build your **investing skills**" with a decorative Canvas/Path chart, 24-dp auth card, white Google button with Google's
  "G", icon fields with visibility toggle, blue Sign in button with spinner, outlined guest action, "Create account" strip, inline error.
  `theme/AuthTokens.kt` now holds sizes only; new `StockIcons` Mail, Lock, Visibility, VisibilityOff, GoogleLogo.
- Auth behaviour unchanged (`AuthScene` untouched). There is **no password-reset feature**; "Forgot password?" keeps its existing
  "not available in the app yet" notice (no fake flow was built).
- Verified visually in the running apps: Android emulator (dark, light, 720×1280 compact, create-account mode) and iOS Simulator iPhone 16
  Pro (dark, scrolled, light). Screenshots were reviewed and then deleted at the user's request (not in the repo).

### 3.3 Financial API Phase 1 audit (read-only) — docs in `75760ab`
`docs/FINANCIAL_API_AUDIT_SUMMARY.md`, `…_ARCHITECTURE_AUDIT.md` (provider inventory, call graphs, cache inventory, security),
`…_COST_ANALYSIS.md` (scenario counts, variable cost model), `…_OPTIMIZATION_ROADMAP.md` (target architecture, P0–P2 backlog,
decisions D1–D7). One later correction: the Finnhub company-name map was already bounded.

### 3.4 Financial API Phase 2 — shared cache & request reuse (server only) — in `75760ab`
Spec/results: `docs/FINANCIAL_API_CACHE_IMPLEMENTATION.md`.
- `service/CompanyFinancialCache.kt` rewritten in place (same API): per-key single flight via an in-flight `CompletableDeferred`;
  **failures shared with joined callers and never stored**; cancelled loader hands over to waiters; expired-first then LRU trimming
  (trim runs after the new entry is marked loading — a bug the tests caught); `invalidate`, `purgeExpired`, `stats`, optional
  `cache.<name>.*` metrics. `FinancialCachePolicy` gained `SEARCH` (6 h) and `INTRADAY` (5 min).
- Duplicates removed: one Finnhub earnings history per symbol (`EarningsService.events` reads `history:SYM:w`); one 24-quarter income
  request for Financials (newest 8) and Valuation (`FmpFundamentalsLoader.load`); raw 5-min bars cached once in `FmpPriceHistoryProvider`
  for chart + sparkline; one `BankOfCanadaPortfolioFx` instance in `Application`.
- Company Details market status from `UsMarketCalendar.marketStatusAt` (no FMP `exchange-market-hours`); search cache in `StockService`;
  `EarningsService.retrying` never retries 429/402/403.
- Metering: `ProviderCalls.record` in `httpclient/NetworkUtils.kt` (`apiCall`) is the **only** place counting `upstream` requests
  (provider, endpoint without query, feature, outcome, latency); BoC and both Gemini paths record too; Gemini tokens for every caller;
  `FmpFundamentalsLoader` no longer double-counts.
- `MovementService(narrationsPerHour = 300)` cost cap on the public narration; Brief/Learning/Earnings-ask AI usage maps pruned.
- Not done in Phase 2 (deliberately): per-client rate limiting of public routes — on Cloud Run `remoteHost` is most likely the proxy
  (no forwarded-headers handling), so an IP limiter would throttle everyone together.

### 3.5 Financial API Phase 3 audit (read-only) — in `e4ba2be`
`docs/FINANCIAL_API_PHASE3_AUDIT.md`, `…_PHASE3_COST_MODEL.md`, `…_PHASE3_IMPLEMENTATION_PLAN.md`, `…_PHASE3_DECISIONS.md` and
`server/src/test/.../service/Phase3AuditBenchmarkTest.kt` (MockEngine, measures only, writes `server/build/phase3-audit-benchmark.txt`).
Measured findings (REAL adapters on a MockEngine, one instance):
- Screener warm-up: **328** upstream requests in the first hour per instance (3 universe + 25 symbols × 13).
- **Bug**: expired screener fundamentals are never re-warmed → financial filters evaluate **15 → 0** companies after 7 h.
- Comparison 1Y history: 12 requests for 4 companies (4 would do). 3Y/5Y, detailed research summary and an AI question add 0 after a comparison.
- TTM ratios/key metrics refetch every 5 min even on a Saturday; a new quarterly statement stays invisible up to 24 h.
- 50 users × 10 overlapping symbols → exactly 15 requests per symbol (single flight works per instance).
- Details/Financials need all 11 fundamentals datasets; screener and Comparison P1 need 10 of 11 (not historical annual `ratios`).

## 4. Files created or modified this session (by commit)

- `e27991d`: core `screener/ComparisonAi.kt` (new), `ComparisonAiPresentation.kt` (new), `ScreenerPresentation.kt`,
  `data/userdata/UserApi.kt`; core test `screener/ComparisonAiTest.kt` (new); server `screener/ComparisonAiService.kt` (new),
  `Application.kt`, `news/GeminiInsights.kt`, `service/ProviderUsage.kt`, `userdata/UserDataStore.kt`, `userdata/FirestoreUserDataStore.kt`;
  server test `screener/ComparisonAiRoutesTest.kt` (new); shared `presentation/screener/ComparisonAiUi.kt` (new), `ComparisonScreen.kt`,
  `ComparisonResearchUi.kt`, `ScreenerRoute.kt`, `ScreenerScene.kt`, `presentation/AppNavigation.kt`, `di/AccountDependencies.kt`,
  `presentation/account/AuthScreen.kt`, `AuthComponents.kt`, `theme/AuthTokens.kt`, `designsystem/icons/StockIcons.kt`, iosMain
  `IosScreenerClient.kt`; iOS `ComparisonAiViews.swift` (new), `ScreenerScenes.swift`, `ComparisonResearchViews.swift`, `AuthScreen.swift`,
  `AuthComponents.swift`; docs.
- `75760ab`: server `service/CompanyFinancialCache.kt`, `httpclient/NetworkUtils.kt`, `repositoryImpl/FmpFundamentalsLoader.kt`,
  `FmpPriceHistoryProvider.kt`, `FmpStockProviderRepositoryImpl.kt`, `earnings/EarningsService.kt`, `service/StockService.kt`,
  `CompanyDetailsService.kt`, `MovementService.kt`, `PriceChartService.kt`, `SparklineService.kt`, `MarketsService.kt`, `NewsService.kt`,
  `ValuationService.kt`, `screener/ComparisonHistoryService.kt`, `userdata/AlertEvaluator.kt`, `userdata/PortfolioMarketService.kt`,
  `news/GeminiInsights.kt`, `news/GeminiNewsSimplifier.kt`, `brief/DailyBriefService.kt`, `learning/LearningService.kt`, `Application.kt`;
  server tests `service/FinancialCacheTest.kt` (new, 17), `service/ProviderRequestBenchmarkTest.kt` (new); docs (5 new `FINANCIAL_API_*`,
  `COMPANY_DETAIL.md`, status/handoff/CLAUDE).
- `e4ba2be`: 4 new `docs/FINANCIAL_API_PHASE3_*.md`, `server/src/test/.../service/Phase3AuditBenchmarkTest.kt` (new), status/handoff/CLAUDE.
- This handoff (uncommitted): `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`, `CLAUDE.md`.

## 5. Important decisions and reasons

- **Phase 5 AI runs only on the server**, grounded in already-validated data with typed evidence; numbers in an answer must come from the
  evidence it cites. Reason: no fabricated figures, no client keys, auditable citations.
- **Durable AI quota for Comparison AI only** (Firestore transaction); 10/day + 50/30 days are proposals, configurable by env. Earnings/Brief
  quotas remain in memory (Phase 4 / D8).
- **Cache hits, declined questions, fallbacks and provider failures are not charged** to the user's AI allowance.
- **Auth screens must use `StockStepsTheme`**, never a separate palette (otherwise theme changes don't reach them).
- **No fake password-reset flow**: none exists in the backend.
- **Extend `CompanyFinancialCache` instead of adding a new cache layer**; failures are shared, never cached as data; cooldowns stay explicit via `resultTtl`.
- **One upstream counter** (`ProviderCalls` in `apiCall`) to avoid double counting.
- **No public-route IP limiting** until client identity behind Cloud Run is decided (proxy IP risk).
- **No new infrastructure** (no Redis/Firestore L2) until licensing (D3) and instance-count evidence (D4) exist.
- **Phase 3 order changed**: 3B-0 (re-warm bug) first because fixing it is correctness-critical — and it will *raise* screener cost relative
  to today, whose low steady state is a symptom of the bug.
- **Benchmarks use the REAL adapters on a Ktor MockEngine**; the Phase 2 "before" numbers came from running the same benchmark on an
  untouched worktree of the prior commit.

## 6. Known bugs, blockers and risks

1. **Screener re-warm bug (open, P0 correctness)**: `ScreenerService.warm()` (`server/.../screener/ScreenerService.kt` ≈ line 186) selects
   `fundamentals[it.symbol] == null`; expired entries stay in the `fundamentals` map (line 140) so they're never reloaded;
   `cachedFundamentals()` (line 173) treats them as absent → financial filters match nothing ≈ 12–18 h after first use per instance.
2. **FMP dataset cache too small for a warmed universe**: `FmpStockProviderRepositoryImpl.financialCache` (line 29) has 512 entries
   (≈ 46 symbols × 11 datasets) vs ≈ 3,300 needed for 300 symbols → LRU churn would force full re-loads (inferred).
3. **Public provider/AI routes have no per-client limits** (`/api/v1/stocks/*`, `/news`, `/news/{id}/insight` (Gemini), `/movement`
   (Gemini, now budgeted 300/h/instance), `/markets/overview`, `/stocks/watch-data`). Existing route limiters key on `remoteHost`, likely
   the Cloud Run proxy (unverified on a deployment).
4. **All caches, single flight and most budgets are per instance**; Cloud Run instance counts are unknown (no deploy config in repo).
5. **In-memory AI quotas** for Daily Brief, Earnings Phase 5 and article insights (multiply with instances).
6. **Flaky test**: `PracticeServiceTest.concurrentOrdersCannotOverspendOrBypassTheLimit` failed once under the full parallel suite and
   passed 3/3 alone; not touched this session.
7. **Licensing** of shared/persistent reuse of FMP/Finnhub data is unconfirmed (D3).
8. **REAL Gemini (Comparison P5) is not live-verified**; Firestore `aiUsage` path not run against a real project.
9. Disk space on the dev machine is tight (~30 GB free during the session); use default DerivedData for iOS.

## 7. Build and test results

Verified (run this session):
- Full suite after Phase 5 (`./gradlew :core:jvmTest :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
  :app:androidApp:assembleDebug :core:iosSimulatorArm64Test --continue`): core JVM 413/0, **core iOS simulator 413/0** (this run also
  covered the Phase 3/4 core tests that earlier sessions couldn't run on iOS), shared Android host 55/0, shared iOS 49/0, `assembleDebug`
  OK; server 366 with 1 failure (the flaky Practice test above).
- iOS `xcodebuild … CODE_SIGNING_ALLOWED=NO build`: BUILD SUCCEEDED (after Phase 5 and after the auth redesign).
- Auth redesign: `:app:shared:compileAndroidMain`, `:app:shared:testAndroidHostTest`, `:app:androidApp:installDebug` OK; real-app screenshots on emulator and simulator.
- Phase 2: `./gradlew :server:test` → 384 tests, 0 failures, 3 skipped; `FinancialCacheTest` 3/3 reruns green; MOCK server smoke test
  (details/search/sparkline/compare 200, market status `AFTER_HOURS`, zero upstream counters).
- Phase 3 audit: `./gradlew :server:test` → **385 tests, 0 failures, 3 skipped** (latest).
Not verified:
- Core/Android/iOS suites were not re-run after Phase 2 or the Phase 3 audit (no changes there).
- No REAL FMP/Finnhub/Gemini calls; no device walkthrough of the Phase 5 AI screens; no TalkBack/VoiceOver/large-font checks; no live
  MOCK curl pass of the AI endpoints (covered by route tests only); Firestore paths untested against a real project.

## 8. Exact next implementation steps

1. **3B-0 (start here)** — in `ScreenerService.warm()`: choose symbols whose cached fundamentals are missing **or expired**
   (`now() - at >= recordTtl`), oldest first, respecting `fundamentalsPerHour`; remove/replace expired map entries; make sure `records`
   built without fundamentals are refreshed once fundamentals return. Raise `FmpStockProviderRepositoryImpl.financialCache` capacity
   (configurable, e.g. env `FMP_DATASET_CACHE_ENTRIES`, default ≈ 3,500) and document memory.
   Tests: turn the "re-warm" scenario in `Phase3AuditBenchmarkTest` into an assertion test (fake `MutableClock`, universe 3 × 5,
   budget 25: coverage after 7 h must return to 15/15; budget never exceeded; oldest first). Run `./gradlew :server:test`.
2. **3A** — add `statementHistory(symbol, period, statements)` (proposed interface in the plan) on `StockProviderRepository` /
   `FmpFundamentalsLoader` (same dataset keys) and use it in `ComparisonHistoryService` (1Y → quarterly income only; 3Y/5Y → annual
   income) and the Phase 4 detailed summary. Assert 1Y = 1 request per cold company; outputs identical in MOCK.
3. **3C** — central `FreshnessPolicy` using `UsMarketCalendar` and the existing `TsxMarketCalendar` (`brief/BriefSessions.kt`; per-symbol
   choice like `earnings/PriceReactionEngine.kt`) for TTM ratios, quotes, intraday and daily closes.
4. **3B-1** (screener dataset set 13 → 10, budget sizing), **3D** (earnings-aware statement TTL), **3E** (labelled stale fallback).
5. Owner decisions needed before the security track: `docs/FINANCIAL_API_PHASE3_DECISIONS.md` D1–D8.
Each commit must update the root `PROJECT_HANDOFF.md` (per `CLAUDE.md`).

## 9. Requirements discussed but not implemented

- Per-client rate limiting of public routes; Firebase App Check; sign-in for AI explanation routes (D1/D2).
- Durable AI quotas for Brief/Earnings/article insights; one shared StockSteps+ AI allowance (D8).
- Global/per-provider request budgets; dashboards and alerts with configured prices (Phase 4).
- Shared cross-instance cache / screener snapshot / FMP bulk endpoints (gated by D3, D4, D7).
- Market-aware and earnings-aware freshness; stale-while-revalidate with UI labels (Phase 3C–3E).
- Phase 5 AI: authorized REAL Gemini acceptance run, device walkthrough, Firestore `aiUsage` check, streaming (not planned).
- Password reset / forgot-password flow (no backend support exists).
- Billing (Play Billing / StoreKit 2 + server verification) — still not implemented; upgrade buttons open Settings.
- Earlier-phase follow-ups listed in `docs/project-status.md` §4 (deploy, Cloud Scheduler, push verification, device passes).

---

## Earlier handoff (2026-10-08) — kept for history

#### StockSteps — session handoff (2026-10-08, end of the Earnings Phase 5 + Company Comparison session)

> **Update (2026-10-09, Phase 5 session):** Company Comparison Phase 5 — AI Comparison Assistant (StockSteps+) and the authentication UI
> redesign are committed as **"Add Company Comparison Phase 5 AI comparison assistant (StockSteps+) and redesign sign-in screens on Android and iOS"** (on top of the Phase 4 commit). See the root `PROJECT_HANDOFF.md` (top section),
> `docs/project-status.md` §0 and `docs/SCREENER_AND_COMPARISON.md` → "Company Comparison — Phase 5".
>
> **Update (2026-10-09, later):** Company Comparison Phase 4 — Guided Research Checklist is committed as **"Add Company Comparison Phase 4 guided research checklist (free + StockSteps+) on Android and iOS"**; see the root
> `PROJECT_HANDOFF.md` (top section).
>
> **Update (2026-10-09):** Company Comparison Phase 3 — Historical Financial Comparison is committed as **"Add Company Comparison Phase 3 historical financial comparison (1Y free, 3Y/5Y StockSteps+) on Android and iOS"**; see the root
> `PROJECT_HANDOFF.md` (top section) and `docs/SCREENER_AND_COMPARISON.md` → "Company Comparison — Phase 3".
>
> **Update (Company Comparison Phase 2 session, 2026-10-08):** Phase 2 — Guided Metric Interpretation (free) is committed as
> **"Add Guided Company Comparison Phase 2 (guided metric interpretation) on Android and iOS"**. See the root `PROJECT_HANDOFF.md` (top section), `docs/project-status.md` §0 and
> `docs/SCREENER_AND_COMPARISON.md` → "Company Comparison — Phase 2". The full-suite verification that §3/§9 below list as pending was
> run in that session (results in the root handoff). Sections below describe the state before Phase 2.

End-of-session handoff for continuing in a fresh Claude Code session. Read order: `CLAUDE.md` → this file →
`docs/project-status.md` → the feature spec (`docs/SCREENER_AND_COMPARISON.md` "Company Comparison — Phase 1",
`docs/EARNINGS.md` "Phase 5") → the code. Always re-check `git status` / `git log` first; the root `PROJECT_HANDOFF.md`
is the canonical milestone log (every commit must update it, per `CLAUDE.md`).

---

## 1. Repository state (verified at handoff)

- Branch `main`, in sync with `origin/main`, **working tree clean** (before this doc-only update).
- HEAD **`9bbf586` "Improve Company Comparison Phase 1 for beginners on Android and iOS"** (pushed).
- Commits made this session (oldest → newest), both pushed:

  | Commit | Title | Size |
  |---|---|---|
  | `b558ba9` | Add StockSteps+ premium earnings intelligence (Earnings Intelligence Lite Phase 5) on Android and iOS | 45 files, +5260/−245 |
  | `9bbf586` | Improve Company Comparison Phase 1 for beginners on Android and iOS | 26 files, +1103/−302 |

  Before this session: `87acb1d` (Earnings Phase 4) → `dc9843d` (P3) → `5219e13` (P2) → `b5e2b9e` (P1) → `c92ec89` (Daily Brief).
- Uncommitted after this handoff: **documentation only** (`docs/PROJECT_HANDOFF.md`, `docs/project-status.md`).
- `docs/audits/` (an audit made by another session at ~22:46: feature audit, API audit, backlog, tracker CSV) was never
  committed by this session and **no longer exists in the working tree** (removed outside this session). Its findings
  that matter are folded into this file; its "Phase 5 uncommitted" statements are obsolete.
- **The local MOCK server is NOT running** (it was stopped before the last test run). Start it with `./gradlew :server:runMock`.
- Disk: ~32 GB free. Build iOS with the default DerivedData (no `-derivedDataPath`).

## 2. Current development objective

StockSteps is a beginner investing-education app (Android Compose Multiplatform, native SwiftUI iOS, shared Kotlin
Multiplatform core, Ktor backend). Philosophy NUMBER → CONTEXT → EXPLANATION → EDUCATION; never advice, rankings,
predictions; MOCK-first; REAL never fabricates. The user drives work with large "implement Phase N end-to-end" prompts,
then says "commit and push".

This session: (1) **Earnings Intelligence Lite Phase 5** (StockSteps+ premium AI earnings) — done and committed;
(2) **Company Comparison Phase 1 review & completion** (free) — implemented and committed, but **its final full-suite
verification was interrupted** (see §8).

## 3. Exact task at the end of the session

The last implementation task was the Company Comparison Phase 1 prompt ("audit + improve the existing comparison; keep it
free; MOCK-first; tests; docs; final report with READY/PARTIAL/BLOCKED verdicts"). Status:
- Implementation, targeted tests, Android compile and iOS build: **done**.
- The full test command + `:app:androidApp:assembleDebug` was started, then **stopped by the user** ("commit and push now")
  before finishing. It was committed with that limitation documented (root `PROJECT_HANDOFF.md`, `project-status.md` §0).
- **Not yet done**: the full suite, the Android APK build, a live MOCK check of `/api/v1/compare` with the new fields, a
  device/simulator walkthrough, and the formal final report (sections A–I) the prompt asked for. Verdict at handoff:
  **Phase 1 MOCK = PARTIAL** (code complete; verification incomplete); **REAL = not production-ready** (not live-verified).
- The last user request is this handoff (documentation only; no commit).

## 4. Work completed this session

### 4.1 Earnings Intelligence Lite Phase 5 — StockSteps+ Premium Earnings Intelligence (`b558ba9`)
Full spec: `docs/EARNINGS.md` → "Phase 5". Highlights:
- **Server** `earnings/EarningsAi.kt`: `EarningsAiQuotaLedger` (per-user daily quotas by category: explanations 10, questions 20,
  digest summaries 3; global budget 5000; env-configurable; failed calls released, never charged), `EarningsGrounding`
  (bounded verified context with source ids `S1-results`, `S2-estimates`, `S3-prices`, `S4-history`, `S5-alternate`, `L-*`
  lessons; missing data listed as unavailable, conflicts withheld), `TemplateEarningsAi` (MOCK, scenario hooks via
  `forScenario`), `GeminiEarningsAi` (REAL, only with `GEMINI_API_KEY`), `EarningsAiValidator` (citations, exact verified numbers,
  advice/prediction, causal claims, guidance/commentary, links/HTML, leakage), `EarningsQuestionScreen` (declines injection and
  advice before any provider call).
- `earnings/EarningsPremiumService.kt`: overview, history, explain (shared cache per report+sourceVersion+prompt+model,
  coalescing, STALE detection), ask (private, report- and version-scoped conversations), usage; routes under
  `/api/v1/me/earnings/{reports/{id}/premium[,/history], reports/{id}/ai/{explain,ask}, ai/usage, digest/*}`.
- `earnings/EarningsDigest.kt`: personalized watchlist digest (+ optional AI summary), preferences, history, weekly delivery
  (`WEEKLY_DIGEST` in the Phase 4 reminder pipeline; opt-in; once per ISO week; plan/opt-in/content re-checked at send time;
  counts only in the push).
- `EntitlementService`: `CANCELED`, `GRACE_PERIOD` (still Plus), `BILLING_ISSUE` (free), restored; unreadable record → 503
  `ENTITLEMENT_UNAVAILABLE` (fail closed). Debug API states `grace|canceled|payment-failed|restored|unavailable` (MOCK only).
- **Core** `earnings/EarningsPremium.kt` (models, `HistoricalEarningsEngine`, `EarningsQuestionChips`, `EarningsDigestRules`),
  `EarningsPremiumPresentation.kt` (`EarningsPremiumPresenter`, `EarningsDigestPresenter`, `RemoteEarningsPremium`), `UserApi` calls.
- **Android** `presentation/earnings/EarningsPremiumUi.kt`, `EarningsPremiumScenes.kt`; **iOS** `EarningsPremiumViews.swift`,
  `IosEarningsClient` bridge. Entry points: Earnings Results (3 premium cards after free sections), Earnings Center digest card,
  Company Details AI preview, Daily Brief digest link, Settings → Earnings Digest & AI, `earnings-digest` push deep link.
- Fixture: fictional `SSHX` (7 events: history gap + CAD quarter) via `scripts/generate_earnings_fixtures.py`.
- Verified at that commit: core JVM 341 / core iOS 341 / server 316 / shared Android host 55 / shared iOS 49, 0 failures;
  `assembleDebug` OK; iOS BUILD SUCCEEDED; live MOCK curl checks passed.

### 4.2 Company Comparison Phase 1 review & completion (`9bbf586`)
Full spec: `docs/SCREENER_AND_COMPARISON.md` → "Company Comparison — Phase 1" (scope, entry points, UI, metric formulas,
architecture, API, MOCK scenarios, tests, limitations, Phases 1–5 table).
- **Audit findings fixed**: horizontal scrolling for 3 companies (fixed 132+116 dp columns); ticker-only chips; no Replace;
  no Watchlist entry; ~30 rows up front; row periods taken from the catalog ("TTM") while values were annual; silent USD
  conversion of market cap; `$` vs `C$` ambiguity; no latest-quarter growth; generic N/A reasons ignoring the source note;
  P/E with losses and D/E with equity ≤ 0 labelled "missing"; chart rebased per series (not a common date) and full-period
  change claimed when history ended early; 1Y history fetched twice; no provenance/freshness.
- **Core**: `ComparisonSelection.replace/set`; `ComparisonPresenter` rebuilt (core groups Overview/Growth/Profitability/
  Financial Health/Valuation/Shareholder Returns + `advanced` "More metrics" sections; per-cell `MetricCell.detail` period;
  explicit currency markers; local market cap with ≈USD detail; `EXAMPLES`; `replace`, `useExample`; one shared request per
  chart period); `MetricFormatter.explanation(metricId, value)` and `money(…, explicit)`; `CompanyColumn.listing`;
  `PerformanceNormalizer.compute` (common base date, `lastDate`/`note` for early-ending history; `normalize` kept as wrapper);
  new comparison-only metric `quarterRevenueGrowth`; observation rule for it; MetricEducation formulas clarified.
- **Server**: `ScreenerService` gains `quarterlyRevenueGrowth` (wired in `Application.kt` to `EarningsService.latestResults` via
  `quarterRevenueGrowth(results)`) and `provenance`; compare notes (currencies, fiscal-year months, retrieval dates, sources);
  performance returns `baseDate` and clearer notes. `FmpFundamentalsMapper`: P/E with losses and D/E with equity ≤ 0 →
  `NON_POSITIVE_DENOMINATOR` + note.
- **Android** `ComparisonScreen.kt` rewritten (equal-width columns, companies card with Replace/Remove, examples, More metrics,
  "Share price change" card, sources); `ScreenerScene.kt` wiring; Watchlist "Compare these companies"; Markets tile "Compare
  Companies". **iOS** `ScreenerScenes.swift` compare section rewritten to match; `IosScreenerClient` (`replaceInComparison`,
  `useExample`, `compareCompanies`, `maxCompanies`); Watchlist and Markets wiring.
- **Tests**: core `ScreenerEngineTest.kt` (+ new `ComparisonPhase1Test` class), server `ScreenerRoutesTest.kt` (Phase 1 MOCK
  scenarios, quarterly provider failure, chart base date), new `repositoryImpl/ComparisonMetricsAdapterTest.kt` (FMP adapter
  with stubbed JSON).

## 5. Files created / modified this session
Created (Phase 5): core `earnings/EarningsPremium.kt`, `earnings/EarningsPremiumPresentation.kt`, `commonTest/.../earnings/EarningsPremiumTest.kt`;
server `earnings/EarningsAi.kt`, `EarningsPremiumService.kt`, `EarningsDigest.kt`, `test/.../earnings/EarningsPremiumTest.kt`;
Android `presentation/earnings/EarningsPremiumUi.kt`, `EarningsPremiumScenes.kt`; iOS `EarningsPremiumViews.swift`.
Created (Comparison): server `test/.../repositoryImpl/ComparisonMetricsAdapterTest.kt`.
Modified: see `git show --stat b558ba9 9bbf586` (notably `Application.kt`, `EarningsReminderService.kt`, `EarningsService.kt`,
`UserDataStore.kt`, `PortfolioAnalyticsService.kt` (EntitlementService), `AnalyticsModels.kt`, `ScreenerService.kt`,
`FmpFundamentalsMapper.kt`, `ScreenerEngine.kt`, `ScreenerModels.kt`, `ScreenerPresentation.kt`, `ScreenerCatalogDefinitions.kt`,
`MetricEducation.kt`, `ComparisonScreen.kt`, `ScreenerScenes.swift`, Watchlist/Markets/Settings/Brief/CompanyDetails screens on
both platforms, `events.json`, docs).

## 6. Important implementation decisions and reasons
| Decision | Reason |
|---|---|
| Phase 5 premium content is shared per report (not per user) and access follows the current plan | Public report data; cost control; matches the StockSteps+ model (expired → no premium content, free results stay). |
| One quota ledger for all earnings AI (incl. the older Earnings Details "Ask") | Spec forbids inconsistent independent counters. |
| Validator requires output numbers to equal verified numbers (rounding variants only on the context side) | First version accepted invented numbers via rounding; fixed and tested. |
| Weekly digest reuses `EarningsReminderDocument` + the Phase 4 delivery pipeline | One push system; Firestore persistence for free; opt-in preserved on downgrade. |
| Comparison latest-quarter growth reuses Earnings Results | Same comparability rules as the earnings screens; no extra statement requests. |
| Comparison table: label row above equal-width columns | Removes horizontal scrolling for 3 companies on 360 dp; 4 still supported (hint recommends 2–3). |
| Kept 4-company support | Works without horizontal scroll; values wrap to 2 lines; documented trade-off. |
| Advanced metrics kept under "More metrics" instead of removed | Phase 1 beginner focus without losing existing capability. |
| Market cap shown in listing currency + ≈USD detail; explicit US$/C$ when mixed | Spec: never silently compare different currencies. |
| Chart: common base date; carry-forward ≤ 5 days disclosed; early-ending history measured to last close | Correct alignment across exchange calendars without inventing data. |

## 7. Known bugs, blockers and risks
- **Comparison commit not fully verified** (see §8): run the full suite and `assembleDebug` first thing.
- MOCK fixture source has `sampleFallback = true`: some gaps (e.g. TD debt/equity) are filled with labelled sample values, so
  MOCK "missing metric" scenarios rely on CSU.TO (dividend history), LONGN (price to sales), TSLA (market cap).
- Comparison REAL: not live-verified (TSX coverage, Finnhub quarterly coverage for Canadian names); each compare loads full annual
  fundamentals per company (~13 FMP calls, cached 6 h).
- Phase 5: no store billing/receipt verification/checkout (upgrade → Settings; MOCK plans only); quotas, explanation cache,
  conversations and digest AI cache are in process memory (single instance only); REAL Gemini output unverified; no secondary
  results source, filing links, guidance or split data.
- Push: real FCM/APNs delivery unverified; per the audit, **iOS has no FirebaseMessaging product and no `aps-environment`
  entitlement**, so iOS remote push is effectively not wired. Cloud Scheduler jobs and Firestore indexes not set up.
- No UI-automation framework (Compose UI tests / XCUITest): rendering, dark/light, large text, TalkBack/VoiceOver unchecked on
  devices for Phase 5 and Comparison.
- The Settings plan simulator only offers Free/Plus/Expired; grace/canceled/payment-failed/unavailable are debug-API only.
- Leftover cleanup candidate: unused `EarningsDetailsState.reminder`/`reminderBusy` and the alerts collection in `EarningsDetailsPresenter`.

## 8. Build and test results

**Verified**
| When | Command | Result |
|---|---|---|
| Phase 5 (`b558ba9`) | full: `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug` | 341 / 341 / 316 / 55 / 49, 0 failures; APK built |
| Phase 5 | `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` | BUILD SUCCEEDED |
| Phase 5 | live MOCK curl script (plans, 401/403 gates, cache/STALE, AI failure scenarios, conflicts, ask, history, digest, weekly delivery, entitlement states, downgrade) | passed |
| Comparison (`9bbf586`) | `./gradlew :core:jvmTest --tests 'org.example.stocksteps.screener.*'` | 30 passed |
| Comparison | `./gradlew :server:test --tests 'org.example.stocksteps.screener.*' --tests 'org.example.stocksteps.repositoryImpl.*' --tests 'org.example.stocksteps.market.MockModeTest'` | all passed (screener 16 incl. 3 new; adapter 2 new) |
| Comparison | `./gradlew :app:shared:compileAndroidMain`, `:app:shared:compileKotlinIosSimulatorArm64` | succeeded |
| Comparison | iOS `xcodebuild` (as above) | BUILD SUCCEEDED |

**Not verified**
- Comparison commit: full suite (core iOS tests, all other server tests, shared Android/iOS host tests) and
  `:app:androidApp:assembleDebug` — started, **stopped before completion**.
- No live MOCK HTTP check of the new comparison fields (`quarterRevenueGrowth`, `baseDate`, notes).
- No device/simulator UI walkthrough of either feature; no REAL provider/AI calls (none authorized).

## 9. Exact next steps
1. `git status`, `git log -1` (expect `9bbf586` + these doc edits). Commit the docs only if the user asks.
2. Run the full verification for `9bbf586` and record results:
   `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug`
   then the iOS `xcodebuild` command. Fix anything that fails (likely candidates: tests asserting old comparison wording, the
   renamed "Revenue growth (fiscal year)" label, or the new `quarterRevenueGrowth` catalog entry in screener/catalog tests).
3. `./gradlew :server:runMock`, then live-check:
   `curl -s 'localhost:8081/api/v1/compare?symbols=AAPL,RY.TO'` (expect `metrics.quarterRevenueGrowth` with a note like
   "Q3 FY2026 vs Q3 FY2025", provenance/retrieval notes) and
   `curl -s 'localhost:8081/api/v1/compare/performance?symbols=AAPL,BB.TO&period=1Y'` (expect `baseDate`, BB.TO excluded with reason).
4. Produce the Comparison Phase 1 final report the prompt requested (A initial assessment … I verdicts) using §4.2/§7/§8; give
   a MOCK verdict (READY only after steps 2–3 pass and a UI pass) and a separate REAL verdict (not ready until an authorized
   live acceptance run).
5. Optional, only if asked: device walkthrough (emulator/simulator; drive UI only on request); Comparison Phase 2 (guided
   interpretation, free) — **not started**; Comparison Phases 3–5 not started.

## 10. Requirements discussed but not implemented
- Comparison: Phase 2 (guided interpretation, free), Phase 3 (historical financial comparison: 1Y free, 3Y/5Y StockSteps+),
  Phase 4 (guided research checklist, free), Phase 5 (AI comparison assistant, StockSteps+); total-return chart; persisted
  selection; lighter REAL metrics path; Compose/XCUITest UI tests.
- Earnings Phase 5: store billing (Play Billing / StoreKit + server verification), a real paywall/checkout, shared
  (Firestore/Redis) quota and AI caches, persisted conversations, secondary results provider, filing/press-release links,
  guidance data, corporate-action/split data, Settings segments for all billing states.
- Platform: iOS FirebaseMessaging + APNs entitlement; Cloud Scheduler jobs; Firestore indexes (`earningsDeliveries`);
  backend deployment (Practice/Learning/Brief/Earnings routes 404 in REAL until deployed); accessibility audits.

---

# Earlier handoff (after Earnings Phase 4, start of this session) — kept for history

End-of-session handoff for continuing in a fresh Claude Code session. It complements, and does not
replace:

- `PROJECT_HANDOFF.md` (repo root): the **canonical milestone log**. `AGENTS.md`/`CLAUDE.md` require
  every commit to update it.
- `CLAUDE.md`: permanent rules, conventions, build/test commands, interop pitfalls.
- `docs/project-status.md`: feature status table, architecture, business rules, pending tasks.
- `docs/EARNINGS.md`: the full specification of Earnings Intelligence Lite **Phases 1–4** (newest phase
  first), including MOCK scenario tables, API tables, policies and production gaps.

Read order for a new session: `CLAUDE.md` → this file → `docs/project-status.md` → `docs/EARNINGS.md`
(or the relevant `docs/<FEATURE>.md`) → the code. Always re-check `git status` and `git log` first.

---

## 1. Repository state (verified at handoff)

- Branch `main`, in sync with `origin/main`, **working tree clean** before this doc update.
- HEAD **`87acb1d` "Add earnings reminders and smart notifications (Earnings Intelligence Lite Phase 4)
  on Android and iOS"** (pushed).
- Commits made this session (oldest → newest), all pushed:

  | Commit | Title | Size |
  |---|---|---|
  | `b5e2b9e` | Add Earnings Calendar (Earnings Intelligence Lite Phase 1) on Android and iOS | 39 files |
  | `5219e13` | Add Earnings Results and beginner explanations (Earnings Intelligence Lite Phase 2) on Android and iOS | 37 files |
  | `dc9843d` | Add post-earnings price reaction (Earnings Intelligence Lite Phase 3) on Android and iOS | 26 files |
  | `87acb1d` | Add earnings reminders and smart notifications (Earnings Intelligence Lite Phase 4) on Android and iOS | 50 files |

  Earlier commits: `c92ec89` Daily Market Brief → `a4a4aeb` Practice Portfolio → `defaade` Guided
  Research → `54b5559` → `af39fb4` original Earnings Intelligence → `4c65521` → `bf44bd7` → `187d130`.
- Uncommitted after this handoff: **documentation only**: this file, `docs/project-status.md`,
  `CLAUDE.md` (new permanent earnings rules), and a one-line pointer in the root `PROJECT_HANDOFF.md`.
- The local **MOCK server is running on :8081** with the Phase 4 code (`GET /health` → 200 at handoff).
  Restart it after any server change (`./gradlew :server:stopMock`, then `./gradlew :server:runMock`).
- **Disk space is tight** (≈9.7 GB free at handoff; it fell to ≈0.4 GB during the session). See §7.

## 2. Current development objective

StockSteps is a beginner investing-education app: Android (Compose Multiplatform), native SwiftUI iOS,
a shared Kotlin Multiplatform core, and a Ktor backend. Philosophy NUMBER → CONTEXT → EXPLANATION →
EDUCATION; never advice, scores, predictions or trading signals; PortIQX is the separate advanced product.

This session delivered **Earnings Intelligence Lite Phases 1–4** from large user prompts ("implement
Phase N end-to-end, MOCK-first, tests, builds, final report"; the user then says "commit and push").
**Phase 5 (premium AI earnings intelligence) has not been requested or started**, and each phase prompt
said not to implement Phase 5.

## 3. Exact task at the end of this session

**No implementation was in progress.** The last request was this handoff (documentation only, no
commit). Phase 4 was finished, verified, committed and pushed as `87acb1d`. The user also re-sent the
Phase 1 prompt once by mistake; I verified Phase 1 existed and did not re-implement it.

The next session should ask the user what to do next. Likely candidates (§9): Phase 5 prompt, a device
walkthrough of Phases 1–4, or real push/scheduler verification.

## 4. Work completed this session (chronological, verified in the repo)

### Phase 1: Earnings Calendar (`b5e2b9e`)
Extended the existing Earnings Center (from `af39fb4`) instead of rebuilding it.
- Markets "Earnings Center" card: real 7-day scheduled count, next date, watchlist count, or a fallback.
- **Earnings Calendar** screen replaces the old Upcoming/Results/Following list:
  - week strip with day counts, previous/next week, Today, date picker;
  - Day/Week view, Upcoming/Reported tabs, All Companies/My Watchlist filter;
  - debounced server-side search (ticker or name-word prefix);
  - compact cards (date, timing, status, watchlist marker);
  - freshness and stale labels, MOCK scenario chips.
- **Earnings Event Details** screen.
- Company Details "Earnings" section (next date or "Next earnings date not available.").
- Daily Brief earnings rows open the event; Watchlist links to the calendar filtered to My Watchlist.
- Deep links: `earnings:<eventId>`, `earnings-calendar`.
- Statuses SCHEDULED/REPORTED/POSTPONED/CANCELED/UNKNOWN come from source data only.
- Server additions:
  - calendar response gains `dayCounts`, `q`, `day`, `scope=watchlist` (signed in), `view=reported|scheduled`;
  - new routes `/earnings/events/{id}`, `/earnings/company/{symbol}/next`, `/calendar/search`;
  - FRESH/CACHED/STALE with a last-good fallback; profiles only for the returned page.
- Files: core `earnings/EarningsCalendar.kt`; Android `presentation/earnings/{EarningsRoute,EarningsScenes,EarningsScreens}.kt`;
  iOS `EarningsScenes.swift`, `IosEarningsClient.kt`; fixtures `CALENDAR_DEMO` in `scripts/generate_earnings_fixtures.py`.

### Phase 2: Earnings Results (`5219e13`)
- **Earnings Results** screen per fiscal period (`EarningsResultsRoute(symbol, fiscalYear, fiscalQuarter)`,
  report id `SYMBOL:YYYY-Qn`, deep link `earnings-results:<id>`). Sections in order:
  1. header;
  2. EPS card and revenue card (one shared `FinancialComparisonCard`, expandable "What is…?");
  3. year-over-year growth;
  4. previous-quarter growth;
  5. deterministic Beginner Takeaway;
  6. Learn More (existing `BeginnerEducation` lessons);
  7. sources.
- Exact decimal maths (`EarningsMath`); **classification changed to exact: MET only when equal (no
  tolerance), `IN_LINE` renamed `MET`**, shared with the old Earnings Details screen.
- Endpoints: `/earnings/reports/{id}`, `/reports/{id}/insights`, `/company/{symbol}/latest`,
  `/company/{symbol}/reports`.
- Entry points: calendar "View Results", event "View Results", Company Details latest-results preview,
  Daily Brief result highlights (`WatchlistHighlight.reportId`).
- Offline saved copy on the device (`UserDataResultsCache`).
- Files: core `earnings/EarningsResults.kt`; fictional `SS*` demo companies in `DEMO_RESULTS`.

### Phase 3: Post-earnings price reaction (`dc9843d`)
- "How Did the Stock React?" section inside Earnings Results:
  - First Session / 3 Sessions / 5 Sessions windows;
  - before/after regular-session closes, change and %;
  - Phase 2 context reused;
  - deterministic non-causal explanations (cases A–H);
  - daily-close chart with dashed earnings marker, baseline/endpoint rings and gaps.
  - Android extends `StockTrendChart` with `markers`/`highlights`/`height`; iOS adds `ReactionChart`.
- Server `earnings/PriceReactionEngine.kt`:
  - `ExchangeCalendar` wraps the existing `UsMarketCalendar`/`TsxMarketCalendar`;
  - a timing-aware baseline/endpoint policy;
  - statuses `ReactionStatus` (AVAILABLE, EVENT_TIME_UNKNOWN, …), plus `ChartReactionPriceSource` and `FixtureReactionPriceSource`.
- Endpoints `/reports/{id}/price-reaction?window=…` and `/price-history`.
- **Earnings Details' reaction now uses the same engine** (old `EarningsReactionCalculator.compute` removed).
- Fixtures: `fixtures/earnings/price-scenarios.json` (generated), demo reports SSHU, SSCA.TO, SSHD.

### Phase 4: Earnings reminders and smart notifications (`87acb1d`)
- Server `earnings/EarningsReminderService.kt`:
  - `ReminderPlanner` (pure): offsets 1/3/7 calendar days, user IANA zone and delivery time, DST policy,
    quiet hours, 2 h late grace, WAITING_FOR_DATE, CANCELED, results once, optional date-change notice;
  - service: CRUD/preferences, legacy migration, batched pass (one event fetch per company), reconciliation, dispatch;
  - routes and `MockScenarioPushSender`.
- Persistence in `userdata/UserDataStore.kt` (+ Firestore + Unavailable):
  - `EarningsReminderDocument` per user;
  - `EarningsNotificationDelivery` with a unique idempotency key, atomic claim with lease, accepted devices,
    backoff 30 s×2ⁿ capped at 30 min, max 5 attempts, invalid-token cleanup.
- Core `earnings/EarningsReminders.kt` (models + `EarningsRemindersPresenter`, account-wide in
  `AccountDependencies.earningsReminders`).
- Android `presentation/earnings/EarningsReminderUi.kt`:
  - reminder button, "Remind Me About Earnings" sheet, Earnings Reminders settings screen;
  - `EarningsRemindersRoute`; `LocalNotificationAccess` provided in `AppNavigation`;
  - `DeviceZone` expect/actual;
  - `earnings_reminders` channel + deep links in `MainActivity`/`AndroidPush.kt`.
- iOS `EarningsReminderViews.swift` (sheet, control, settings scene), wired in `AppScene`,
  `CompanyDetailsScene/Screen`, `SettingsScene/Screen`, `WatchListScene`, `PushNotifications.swift`.
- **Legacy per-company EARNINGS alert rules are migrated once into company reminders and no longer
  evaluated by `AlertEvaluator`**. Creating EARNINGS alerts returns 400 `EARNINGS_REMINDERS`; the alert
  editors no longer offer Earnings; the old Earnings Details reminder dialog was replaced.
  Lead days and results notifications are now free.

### Other changes
- Test-only fix: `DailyBriefTest.offlineShowsOnlyBriefsThisDeviceDownloaded` had a race (it awaited any error;
  now it waits for the specific "Previously opened" message). It had failed intermittently on the iOS simulator.

## 5. Files created this session (all committed)

```
core/src/commonMain/kotlin/org/example/stocksteps/earnings/EarningsCalendar.kt        (P1)
core/src/commonMain/kotlin/org/example/stocksteps/earnings/EarningsResults.kt         (P2)
core/src/commonMain/kotlin/org/example/stocksteps/earnings/EarningsPriceReaction.kt   (P3)
core/src/commonMain/kotlin/org/example/stocksteps/earnings/EarningsReminders.kt       (P4)
core/src/commonTest/kotlin/org/example/stocksteps/earnings/{EarningsCalendarTest,EarningsResultsTest,EarningsPriceReactionTest,EarningsRemindersPresenterTest}.kt
server/src/main/kotlin/org/example/stocksteps/earnings/PriceReactionEngine.kt         (P3)
server/src/main/kotlin/org/example/stocksteps/earnings/EarningsReminderService.kt     (P4)
server/src/main/resources/fixtures/earnings/price-scenarios.json                     (P3, generated)
server/src/test/kotlin/org/example/stocksteps/earnings/{EarningsCalendarServiceTest,EarningsResultsServiceTest,EarningsPriceReactionServiceTest,EarningsRemindersTest}.kt
app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/earnings/{EarningsReminderUi,DeviceZone}.kt
app/shared/src/{androidMain,iosMain}/kotlin/org/example/stocksteps/presentation/earnings/DeviceZone.{android,ios}.kt
app/iosApp/iosApp/EarningsReminderViews.swift
docs/PROJECT_HANDOFF.md (this file)
```
Main files modified: `earnings/{EarningsModels,EarningsCalculations,EarningsPresentation}.kt`,
`network/StockStepsApi.kt`, `data/userdata/UserApi.kt`, `brief/DailyBriefModels.kt`; server
`earnings/{EarningsService,EarningsReaction}.kt`, `brief/DailyBriefService.kt`,
`userdata/{UserDataStore,FirestoreUserDataStore,Push,AlertEvaluator,AlertRules,AlertsService}.kt`,
`Application.kt`; Android `presentation/{AppNavigation,earnings/*,markets/MarketsScene,companydetails/*,
brief/*,watchlist/*,settings/*}.kt`, `designsystem/components/StockTrendChart.kt`,
`di/AccountDependencies.kt`, `composeResources/values/strings.xml`, `androidApp/.../{MainActivity,
account/AndroidPush}.kt`, `androidApp/.../res/values/strings.xml`; iOS `AppScene, EarningsScenes,
MarketsScene, CompanyDetailsScene/Screen, DailyBriefScenes, WatchListScene, SettingsScene/Screen,
PushNotifications`.swift, `IosEarningsClient.kt`; `scripts/generate_earnings_fixtures.py`,
`fixtures/earnings/events.json` (164 events, 40 companies), `fixtures/earnings/price-scenarios.json`; `docs/EARNINGS.md`, `docs/project-status.md`, `CLAUDE.md`, root `PROJECT_HANDOFF.md`; server tests `market/MockModeTest.kt`, `userdata/AlertsTest.kt`, `earnings/EarningsServiceTest.kt`.
Full per-commit lists: `git show --stat b5e2b9e 5219e13 dc9843d 87acb1d`.

## 6. Important implementation decisions and reasons

| Decision | Reason |
|---|---|
| Extend the existing Earnings Center and Earnings Details instead of rebuilding them. | The prompts said not to duplicate infrastructure; `af39fb4` already had sources, caching and history. |
| Event and report identity is the fiscal period `SYMBOL:YYYY-Qn`, with an exchange-qualified symbol (`TD` ≠ `TD.TO`). | Stable across reschedules; the canonical instrument id. Providers give no event ids. |
| Calendar status comes only from source data; "Reported" needs figures or an explicit flag; postponed/canceled only from an explicit flag. | The spec forbids inferring status from the date. |
| **Exact decimal classification (MET only when equal)**, replacing the ±0.5%/half-cent tolerance everywhere. | The spec forbids tolerances that hide differences; one policy across both screens. |
| Calculations live on the server (results, insights, reactions, schedules); apps only format. | "Backend authoritative"; iOS and Android can't diverge. |
| One price-reaction engine on the exchange calendar, also used by Earnings Details. | The two screens previously used different rules; the calendar handles holidays and half days honestly. |
| Reactions are recomputed from the cached series per request; incomplete windows are never stored. | Revisions, moved dates and newly completed windows show immediately. |
| Earnings Results and Price Reaction are free for every reported period. | "Basic results free"; StockSteps+ keeps the older Earnings Details history limit, AI and advanced insights. |
| One backend reminder system; legacy earnings alerts migrated and no longer evaluated. | Avoids duplicate notifications from two systems; the spec says to reuse the existing infrastructure. |
| Dedup key = user \| type \| event \| offset \| event date; claim with lease; per-device acceptance. | Exactly-once per logical notification across retries, workers, manual and auto reminders, and several watchlists. |
| Device zone sent on load and with every preference save. | Travel and time-zone changes reschedule pending reminders without asking. |
| Fictional "StockSteps Demo" (`SS*`) companies for edge-case fixtures. | Real tickers can't plausibly carry canceled, revised or fiscal-change scenarios; fictional ones are labelled demo data. |

## 7. Known bugs, blockers and risks

- **Not verified in production:**
  - real FCM/APNs delivery (no signed builds, devices or entitlements);
  - Cloud Scheduler jobs (`/internal/earnings-reminders/dispatch` every 5 min, plus the brief and alerts jobs);
  - Firestore composite indexes on `earningsDeliveries` (status+dueAt, status+leaseUntil);
  - the deployed backend (Practice, Learning, Brief and Earnings routes return 404 in REAL until deployed);
  - REAL Finnhub coverage of TSX and plan limits (no paid calls were made).
- **REAL data gaps:**
  - Finnhub gives no publication time, revision flag, estimate period, exact announcement time or
    postponed/canceled flags;
  - FMP daily closes only (no OHLC, intraday or extended hours);
  - no corporate-action feed (a warning is shown);
  - rule-based exchange calendars (no TSX early closes; unscheduled closures only appear as missing prices).
- **Disk space:** the machine ran out of space mid-session. The previous session's scratch derived data
  (`/private/tmp/claude-501/-Users-yogeshpatel-Documents-StockSteps/d9b12d26-…/scratchpad/dd`, ≈2 GB)
  is now **broken** (its package artifacts were partly purged). Build iOS with the **default DerivedData**
  (`~/Library/Developer/Xcode/DerivedData/iosApp-dafomoo…`): omit `-derivedDataPath`. That scratch folder
  can be deleted, but only with the user's OK.
- The core iOS test `DailyBriefTest.offlineShowsOnlyBriefsThisDeviceDownloaded` was racy (fixed in tests).
  Watch for other timing-sensitive tests on the iOS simulator.
- **The MOCK server's Gradle daemon can die** when other Gradle runs happen (seen once): if
  `curl localhost:8081/health` fails, restart `runMock`.
- MOCK reminder and delivery state is in-memory and cleared on server restart; the MOCK clock is pinned to
  `2026-10-07T21:15Z` at start and then advances.
- No UI-automation tests exist (no Compose UI or XCUITest setup). TalkBack/VoiceOver, large text and
  dark mode for Phases 1–4 were **not** checked on a device.
- Leftover, harmless: `EarningsDetailsState.reminder`/`reminderBusy` and the alerts collection in
  `EarningsDetailsPresenter` are unused since Phase 4 (cleanup candidate).
- No open functional bugs are known.

## 8. Build and test results

**Verified this session (after the final code change in each phase)**

| Suite | After P1 | After P2 | After P3 | After P4 (final) |
|---|---|---|---|---|
| core JVM | 309 | 322 | 325 | **329** |
| core iOS simulator | 309 | 322 | 325 | **329** |
| server | 257 | 266 | 278 | **297** |
| shared Android host | 55 | 55 | 55 | **55** |
| shared iOS | 49 | 49 | 49 | **49** |

All with 0 failures.
- `:app:androidApp:assembleDebug`: succeeded after each phase.
- iOS `xcodebuild` (scheme `app.iosApp`, generic simulator, `CODE_SIGNING_ALLOWED=NO`): **BUILD SUCCEEDED**
  after each phase. Phases 2–4 used the default DerivedData.
- Live MOCK checks (curl) after each phase:
  - calendar day counts, search, scenarios;
  - results spec example SSRV ($1.45 vs $1.20; $8.5B vs $8.2B; YoY +7.6%; QoQ +4.9%);
  - price reaction spec example ($150.00 → $157.50, +5.0%);
  - reminder flow: delivery time 17:00 Toronto, CRBU and JPM submitted, an `invalid-` token removed,
    reported event refused, 401 without auth.

**Not verified**
- On-device or simulator UI walkthroughs, accessibility and theme checks.
- Real push delivery on Android and iOS.
- REAL-mode end-to-end against a deployed backend.
- Gemini or other REAL AI.

## 9. Exact next steps for a new session

1. Run `git status` and `git log -1`. Expect `87acb1d` plus the uncommitted doc edits from this handoff.
   Commit them only if the user asks (e.g. "Update session handoff docs"), updating the root
   `PROJECT_HANDOFF.md` in the same commit and using the trailer from `CLAUDE.md`.
2. If needed:
   - `curl localhost:8081/health`, otherwise `./gradlew :server:runMock`;
   - for Android: `adb reverse tcp:8081 tcp:8081`;
   - test a MOCK user with `Authorization: Bearer mock-user:<uid>`;
   - run the reminder scheduler manually with `POST /internal/earnings-reminders/dispatch`.
3. Ask the user what's next. The default order:
   1. **Phase 5: premium AI earnings intelligence** (the user's series; not started). Expect a
      StockSteps+-only, server-side, validated, source-grounded design reusing the existing
      `EarningsResearchProvider` boundary (`TemplateEarningsResearch` in MOCK; REAL has no provider → 503)
      and the Gemini client with validators. Also reuse the Phase 2 insights and Phase 3 reaction as grounding.
   2. Device walkthrough of Phases 1–4: calendar → event → results → reaction, the reminder sheet and settings,
      and notification deep links. Drive the emulator or simulator only if the user asks.
   3. Production: deploy the backend; add Cloud Scheduler for reminders, brief and alerts; create the
      Firestore indexes; configure the APNs key and capabilities; then verify real push on one Android and
      one iOS device.
   4. Add a corporate-action source for REAL price reactions and Practice.
4. New features follow the established recipe:
   1. core models, policy and presenter, with `commonTest`;
   2. server service, routes, `UserDataStore` methods (InMemory, Firestore, Unavailable), MOCK fixtures and scenarios, tests;
   3. Android Route, Scene and Screen;
   4. iOS `Ios*Client` and SwiftUI;
   5. docs;
   6. the full test command, `assembleDebug` and `xcodebuild`.

## 10. Requirements discussed but not implemented

- Phase 5 (premium AI earnings analysis): not requested yet.
- UI automation (Compose UI tests, XCUITest), TalkBack/VoiceOver audits, dark/light and large-text checks:
  requested in every phase prompt, never done (no test infrastructure).
- iOS process-death restoration of the calendar selection (Android has it).
- Locale-aware date and number formatting (English only).
- Extended-hours, intraday and OHLC price data; corporate-action feed; versioned exchange calendars; TSX
  early closes.
- More delivery-time options and custom quiet hours in reminder settings (fixed choices and a 10 PM–7 AM preset).
- StockSteps+ digests or multiple schedules per event (architecture only reserved, per the Phase 4 spec).
- StockSteps+ purchase, restore and receipt validation; REAL AI providers for earnings and learning.
- From earlier sessions: scheduled brief generation and push retry queue; system Back between Guided Research
  steps; MOCK ETF profile fixtures.

## 11. Previous session (before `c92ec89`), for reference

Guided Research (`defaade`), Practice Portfolio (`a4a4aeb`) and the Daily Market Brief (`c92ec89`) were
built in the previous session; details are in the root `PROJECT_HANDOFF.md` milestones and
`docs/{GUIDED_RESEARCH,PRACTICE_PORTFOLIO,DAILY_MARKET_BRIEF}.md`.
