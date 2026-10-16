# Phase 5D.5.6C1 — Android independent navigation stacks & canonical tab selection

- **Status:** implemented and verified on the local MOCK server (Android only). Approved by the owner; committed and pushed as "Add Android independent tab stacks for Phase 5D.5.6C1 with canonical tab selection, a cross-tab Back trail from the shared navigation model, tab reselection and global Settings and Notifications screens".
- **Base:** `11b1e5b` (5D.5.6B, pushed).
- **Plan:** `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6_PLAN.md` §3–§6, §14–§15 (D1–D12). iOS is untouched (C2).

## 1. What changed for the user (Android)
- **Five independent stacks.** Each tab keeps its own history, screen arguments, ViewModels and scroll position. Switching tabs restores the
  tab exactly as it was (Markets → Apple → Financials → Portfolio → Markets shows Financials again).
- **The selected tab is the visible screen's canonical tab** (core `NavigationPolicy`). For example:
  - Company Details and its pages, Compare and Earnings select **Markets** from wherever they were opened;
  - Daily Brief selects **Home**;
  - Holding details and Practice select **Portfolio**;
  - stock alerts select **Watchlist**;
  - Learn topics, terms and Guided research select **Learn**.
- **Cross-tab Back:** a screen opened from another tab goes onto its own tab's stack, and Back returns to where you came from. For example,
  Watchlist → Apple (Markets) → P/E term (Learn) → Back → Apple → Back → Watchlist. The Markets and Learn stacks are left exactly as they were.
- **Manual tab switch** clears the trail (D2), so Back then stays inside that tab.
- **Reselecting the current tab** pops it to its root (D3). At the root, it scrolls the landing page to the top (D12).
- **Back at a tab root** goes to Home; Back at the Home root closes the app (plan §5).
- **Modal and global screens** have no tab bar and don't change the stacks:
  - modal: Company Search, the transaction form, the company pickers;
  - global: Settings (and its Earnings reminders/digest pages), **Notifications** (all alerts), sign-in.

  Opening a tab screen from a modal closes the modal first (rule 5). For example, Search → Apple shows Apple in Markets, and Back returns to the
  tab you searched from.

## 2. Architecture
```
                       core (shared, tested)                                    Android host (app:shared)
   ┌──────────────────────────────────────────────┐          ┌──────────────────────────────────────────────────────────────┐
   │ NavigationPolicy  ScreenId(kind, tab)        │◄─────────│ AppRouteScreens: route class → ScreenId (the only table)    │
   │ TabNavigation (pure model):                  │          │ AppNavigation: NavHost                                       │
   │   open · back · selectTab · reselect ·       │          │   HomeGraph{Discovery, Daily Brief…}   MarketsGraph{…}       │
   │   reset · sync  (invariants I1–I11)          │◄────────►│   PortfolioGraph{…}  WatchlistGraph{…}  LearnGraph{…}        │
   └──────────────────────────────────────────────┘ decides  │   top level: Search, form, pickers, Settings, Notifications, │
                                                    + sync   │              Auth (modal / global)                           │
                                                             │ TabNavigator: drives the NavController from the model;      │
                                                             │   AppBackHandler → back(); bottom bar = destination's graph │
                                                             └──────────────────────────────────────────────────────────────┘
```
- **Stacks:** Jetpack Navigation's own multiple back stacks, using the repository's Navigation Compose 2.9.2.
  - Each tab is a nested graph (`navigation(startDestination = tab root, route = tab graph)`).
  - A tab switch pops the current tab graph with `saveState` and navigates to the target graph with `restoreState`. This is symmetric for all
    five tabs, including Home, which previously was never restored.
  - Saved stacks keep their entries' ViewModel stores and saveable UI state, so returning makes no new requests.
- **Ownership is the policy:** every route registration goes into a `RouteTable`. `AppNavigation` puts each one into the graph of
  `NavigationPolicy.parentTab(screenIdOf(route))`, or at the top level when it is modal or global. Graph membership can't drift from the policy,
  and the bottom bar selects the graph the visible destination lives in.
- **Trail:** `TabNavigator` asks core `TabNavigation` for every step (`open`, `back`, `selectTab`, `reselect`, `reset`) and performs it on the
  NavController.
  - After every back-stack change, the NavController's real stack for the visible tab is synced into the model with the new core
    `TabNavigation.sync` (contract extended with tests). A pop done anywhere (in-screen Back, pop-to flows) therefore can't leave a stale hop.
  - Entry identity is the typed route's value (`CompanyDetailsRoute(symbol=AAPL)`), recorded per back-stack entry id.
  - The model plus those keys are saved with `rememberSaveable` beside the NavController state, so rotation and process death restore both.
- **One Back:** `AppBackHandler` (an expect/actual over the activity's back dispatcher, registered after the NavHost) sends system Back to
  `TabNavigator.back()`. In-app Back buttons call the same function, so there are no competing pops. It's disabled only at the Home root with no
  trail, where the system closes the app.
- **Routes added:**
  - `NotificationsRoute` (global, all alerts; `AlertsRoute(symbol)` is now always a stock's alerts, Watchlist);
  - `SettingsRemindersRoute` (Earnings reminders inside Settings; new `ScreenId.SETTINGS_EARNINGS_REMINDERS`, GLOBAL).
- **Portfolio nested tabs (C4) prepared:** the Portfolio graph owns Portfolio, Holdings, Transactions, Insights, Practice and Practice order. The
  inner My Portfolio / Practice control is unchanged.

## 3. Route coverage
- **42 route types**, all in `AppRouteScreens`. `AppScreensTest` checks:
  - the table equals the registered set;
  - no type is listed twice;
  - every `ScreenId` except the app-lock overlay has a route;
  - each tab graph's start equals `NavigationPolicy.root`.
- **By tab:**
  - Home: 3;
  - Markets: 16;
  - Portfolio: 7;
  - Watchlist: 3;
  - Learn: 4;
  - top level: 9 (Search, transaction form, Portfolio and Practice pickers, Settings, Settings reminders, Digest settings, Notifications, Auth).

## 4. Files changed
- **Core:**
  - `navigation/NavigationPolicy.kt` (+ `SETTINGS_EARNINGS_REMINDERS`);
  - `navigation/TabNavigationModel.kt` (+ `sync`);
  - tests: `NavigationPolicyTest.kt`, `TabNavigationModelTest.kt` (+ sync invariants).
- **Shared:**
  - new `presentation/TabNavigator.kt` (graphs, navigator, trail saver, scroll-to-top on reselect);
  - new `presentation/components/AppBackHandler.kt` (+ `.android/.ios/.jvm`);
  - `presentation/AppNavigation.kt` (nested graphs, navigator wiring, bottom bar, Back);
  - `presentation/AppScreens.kt` (class table);
  - `watchlist/AlertsScene.kt` (`NotificationsRoute`), `earnings/EarningsReminderUi.kt` (`SettingsRemindersRoute`);
  - `portfolio/PortfolioRoute.kt` (dead `inPortfolioTab` removed);
  - landing screens for scroll-to-top: `home/HomeScreen.kt`, `markets/MarketsScene.kt`, `portfolio/PortfolioScreen.kt`,
    `watchlist/WatchListScreen.kt`, `learn/LearnScreen.kt`;
  - tests: `presentation/AppScreensTest.kt`.
- **Docs:** this report, `docs/reviews/phase5d_5_6c1/`, the plan, the handoff and the status.
- **Not touched:** iOS, server, APIs, feature screens' content.

## 5. Automated tests
| Command (sequential, final code) | Result |
|---|---|
| `./gradlew :core:jvmTest` | **FAIL 576/1** — `PortfolioSessionStateTest` timed out (known intermittent, tracked separately). Rerun alone: PASS 3/0. The full run is not counted as a pass |
| `./gradlew :core:iosSimulatorArm64Test` | PASS 576/0 |
| `./gradlew :app:shared:testAndroidHostTest` | PASS 270/0 |
| `./gradlew :app:shared:iosSimulatorArm64Test` | PASS 266/0 |
| `./gradlew :app:androidApp:assembleDebug -PstockstepsMockBackendUrl=http://127.0.0.1:8081` | PASS |
| `xcodebuild` (iOS, shared framework unchanged in behaviour) | PASS |
| `./gradlew :server:test` | NOT RUN (no server change) |
| Navigator + real NavController integration tests | NOT RUN (no Robolectric/instrumented harness; covered on device, §6) |

**New or changed tests:**
- core `TabNavigationModelTest` +1 (`sync`: unchanged stack, host pop drops the hop, replaced entry drops the hop, deeper pushes keep it,
  foreign stacks ignored, restoration);
- core `NavigationPolicyTest` (6 global screens);
- shared `AppScreensTest` +2: 42 route types equal to `AppRouteScreens` with no duplicates; tab graph roots equal `NavigationPolicy.root`; the
  trail survives save and restore; route keys carry arguments.

**Requirement → test:**

| # | Requirement | Covered by |
|---|---|---|
| 1 | Route → one screen id | `AppScreensTest` |
| 2 | Each tab has its own stack | `everyTabGraphStartsAtThePolicyRoot` |
| 3 | Switching restores the stack | `tabSwitchRestoresStacksUntouched` |
| 4 | Selection follows the visible screen | `approvedCanonicalOwners`, `canonicalOwnersOfKeyRoutes` |
| 5 | Cross-tab Back | `watchlistToCompanyDetailsAndBack` |
| 6 | Nested cross-tab Back | `nestedHopsUnwindInOrder` |
| 7 | Manual switch clears the trail | `manualTabSwitchClearsTheTrail` |
| 8, 9 | Reselect pops to root, then scrolls to top | `reselectPopsToRootThenScrollsToTop` |
| 10 | Modals don't touch the stacks | `modalAndGlobalScreensAreNeutral` |
| 11 | Global screens have no tab | `everyScreenHasExactlyOneClassification…` |
| 12 | Repeated destinations | `repeatedNavigationNeverDuplicates…` |
| 13 | Deep links | `deepLinkOnColdStartReturnsHome` |
| 14 | Saved state | `theTrailSurvivesSavingAndRestoring`, `syncFollowsTheHostAndDropsStaleHops` |
| 15 | Rapid taps | model I6 + device F |
| 16 | Arguments | route keys in `theTrailSurvivesSavingAndRestoring` + device A/B |
| I1–I4 | Invariants | `randomWalksKeepInvariants` (4,000 random steps) |

## 6. Device QA (Android emulator-5554, 360 dp, local MOCK, the emulator's existing signed-in test session)
| # | Scenario | Result | Evidence |
|---|---|---|---|
| A | Markets → Apple → Financials → Portfolio → Markets: Financials restored at its scroll position | PASS | `c1-a-financials-restored` |
| B | Watchlist → Apple (Markets selected) → P/E term (Learn selected) → Back → Apple (Markets) → Back → Watchlist; Markets and Learn stacks intact | PASS | `c1-b-*` |
| I1 | Markets at Discover; Watchlist → MSFT → Back → Watchlist; Markets tab still shows Discover | PASS | UI dumps |
| D2 | Watchlist → MSFT → Portfolio → Markets → Back stays in Markets (MSFT → Discover → root) | PASS | UI dumps |
| C | Reselect Markets on Apple → Markets root; reselect at root → scrolled to top (also Portfolio) | PASS | UI dumps |
| Root | Back at Markets root → Home; Back at Home root → app closes | PASS | UI dumps (launcher) |
| D | Search (keyboard: first Back hides it, second closes Search), Settings (+ Earnings reminders inside Settings), Notifications, transaction form: no tab bar; Back returns to the exact screen | PASS | `c1-d-*` |
| D | Comparison picker | NOT RUN (in-screen dialog; code unchanged) | — |
| E | Markets Losers filter + scroll kept across Learn and Portfolio (pixel-identical) | PASS | `c1-e-losers-*` |
| E | Company Details scroll position kept on Back from a Learn term | PASS | `c1-b-back-to-cd` |
| F | Home → Search → Apple: Markets selected, Back → Home | PASS | `c1-e-home-search-apple-markets` |
| F | Learn → Practice (Portfolio) → Back → Learn; Markets → Daily Brief (Home) → Back → Markets | PASS | `c1-f-practice-portfolio` |
| F | Portfolio sample → Holding details (Portfolio) → Company (Markets) → Back → Holding → Back → Portfolio (scroll kept) | PASS | `c1-h-*` |
| F | Rapid tab taps (10) settle on the last tab; double tap on a Learn row pushes once | PASS | UI dumps |
| F | Rotation (activity recreation) with an active hop: Back still returns to Home | PASS | UI dumps |
| F | Background → process killed → relaunch: Apple restored in Markets, Back → Home (trail restored) | PASS | UI dumps |
| F | Dark at 2.0× (Apple, Markets selected) | PASS | `c1-g-dark-20-cd-markets` |
| F | Guest mode | NOT RUN (the emulator holds the owner's signed-in session; not signed out) | — |
| F | Back after sign-in | NOT RUN (no sign-in on devices) | — |
| — | TalkBack | NOT RUN (owner) | — |

**Device state afterwards:**
- emulator: Light (app setting), 1.0×, portrait, `adb reverse` removed;
- the Portfolio sample scenario is back to "My saved accounts";
- MOCK server stopped.

## 7. Known limitations
- **Navigator integration tests are on-device only.** `app:shared` host tests run on Android stubs without Robolectric (no new dependency added),
  so `TabNavigator` with a real NavController is covered by the device matrix. The model, sync, route table and save/restore have unit tests.
- **Watchlist rows:** the test account's watchlist is empty, so "Watchlist → Company Details" was exercised through Watchlist's search. Both
  call the same `openStock`.
- **Search from a tab** opens results in Markets. Back returns to the tab you searched from, not to Search (approved rule 5). This changes the
  previous behaviour, where Back reopened the search screen.
- **Settings is a full-screen global screen** without the tab bar. Before C1 it showed the tab bar with no tab selected.
- **Practice "View holdings" flows** pop back to the existing Practice entry instead of re-creating it, so a cross-tab trail into Practice stays
  valid.

## 8. Recommendation
**APPROVE.** No defects remain from QA. Still open:
- the known intermittent core JVM test, tracked separately;
- Android guest mode and TalkBack (owner);
- iOS follows in C2.

## 9. Deferred
- **C2:** iOS per-tab `NavigationStack`s, typed destinations, canonical selection and trail.
- **C3:** iOS nested cross-tab links and the `.task` audit.
- **C4:** Portfolio nested tabs.
- **D:** final QA on both platforms.
- **Not needed:** Android deep links by URL (none exist); notification links go through the same `open()`.

## 10. Regression risks
- **Rerouting:** every navigation call now goes through `TabNavigator`. A future `navController.navigate` to a tab route from outside would
  bypass the policy. Mitigation: KDoc on `TabNavigator`; the code review rule in `CLAUDE.md` (Navigation, C1).
- **Back registration order:** `AppBackHandler` must stay after the NavHost. If Navigation's handler ever won, a cross-tab Back would pop inside
  Markets instead of returning. The device matrix (B, D2) covers this.
- **Saved states and sign-out:** `reset()` clears all five saved tab stacks on sign-out (session-ended included). It was verified by code review
  only (no sign-out on the owner's session).
