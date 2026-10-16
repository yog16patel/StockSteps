# Phase 5D.5.6B — Shared navigation policy & landing top bars

- **Status:** implemented and verified on the local MOCK server. Approved by the owner; committed and pushed as "Add the Phase 5D.5.6B shared navigation policy and landing top bars with a canonical parent tab per screen, a tested cross-tab trail model and Notifications and Settings on all five landing pages on Android and iOS".
- **Base:** `912ec7f` (5D.5.6A plan, pushed).
- **Plan:** `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6_PLAN.md` (approved D1–D12). This phase added §13 (Portfolio nested tabs) and §14–15
  (cross-tab trail state model, invariants and Phase C tests).
- **Reference:** the owner's "Five landing pages with shared top app bar" image. Title left; page actions, then bell and gear on the right, on
  all five pages, in light and dark.

## 1. What changed for the user
- **The same top bar on all five landing pages** (Home, Markets, Portfolio, Watchlist, Learn):
  - left: the page title (Home keeps the StockSteps brand mark);
  - right: the page's existing actions, then **Notifications** (bell) and **Settings** (gear), always last.
- **Page actions kept:**
  - Home: Search;
  - Markets: Search;
  - Portfolio: About (ⓘ) and the ⋯ account menu;
  - Watchlist: Search ("Add a company") and, when signed in, the list menu (⋯);
  - Learn: term search.
- **Home:** the person icon ("Profile and settings") is now the gear ("Settings", D6). The bell now says "Notifications" instead of "Your
  alerts".
- **Notifications** opens the existing alert history for all alerts (never filtered to a stock, D5). There's no unread badge, since there is no
  unread contract.
- **Settings** opens the existing Settings: a sheet on iOS (unchanged), the existing screen on Android.
- **Large text:** when the title and the icons can't share a line, the icons move to their own row under the title. Nothing is truncated or
  hidden, and no overflow menu was needed.
- **Navigation is unchanged:**
  - Notifications and Settings use today's presentation (Android push; iOS push for alerts and sheet for Settings), so Back and Done return to
    the exact landing page;
  - tab bar, Back, tab restoration and the My Portfolio / Practice control are unchanged (Phase C / C4).

## 2. Shared navigation policy (core, `org.example.stocksteps.navigation`)
| Type | Purpose |
|---|---|
| `AppTab` | The five primary tabs and their labels |
| `ScreenKind` | `TAB_ROOT`, `TAB_SCREEN`, `MODAL` (task over the current tab), `GLOBAL` (outside the tabs) |
| `ScreenId` | One stable id per audited screen (42), each with its kind and canonical tab. Tab screens have exactly one tab; modal and global screens have none |
| `NavigationPolicy` | `parentTab`, `root`, `isTabScreen`, `showsBottomNavigation` (approved target, §6), `topBar` (LANDING / BACK / MODAL / NONE), `crossesTabs` |
| `LandingTopBarDisplay` | Landing titles (brand on Home), spoken labels "Notifications", "Settings", "Search stocks" |
| `TabNavigationModel` (`TabNavigation`, `TabNavigationState`, `Hop`) | Pure reference model of the Phase C contract (plan §14): canonical push, cross-tab hops, Back, tab switch, reselect, reset. **Not wired into any host** |

**Android mapping:** `presentation/AppScreens.kt` `screenIdOf(route)` covers all 40 registered routes. `AlertsRoute(null)` is Notifications and
`AlertsRoute(symbol)` is Stock alerts.

**iOS:** it uses `AppTab` and `LandingTopBarDisplay` now. Its `AppDestination` → `ScreenId` mapping arrives with the typed destinations in C2,
where an exhaustive `switch` gives compile-time coverage.

**Canonical owners (D1):**
| Owner | Screens |
|---|---|
| Markets | Company Details, Financials, Valuation, News, News insight, Why did it move?, Discover, Compare (+ research and AI), all Earnings screens including the digest |
| Home | Daily Brief and Previous briefs |
| Portfolio | Holdings, Transactions, Insights, Practice and Practice order |
| Watchlist | Stock alerts, Earnings reminders |
| Learn | Learn topics, Learn terms, Guided research |
| Modal (no tab) | Company Search, the transaction form, the two pickers |
| Global (no tab) | Settings, Digest settings, Notifications, Auth, App lock |

## 3. Top app bar implementation
- **Compose:** `designsystem/components/StockLandingTopBar.kt`.
  - `tab`, `onNotifications`, `onSettings` and `actions` (a list of 48 dp icon buttons).
  - It measures the title with `rememberTextMeasurer` and decides stacking with the shared `StockLayout.metricRowStacked`.
  - New gear icon: `StockIcons.Settings`.
- **SwiftUI:** `DesignSystem/StockLandingTopBar.swift` (generic over the actions, plus `actionCount`) and `StockLandingIconButton`.
  - Fixed 20 pt symbols in 48 pt touch targets.
  - The same stacking rule, from `UIFont` text widths (never its own frame); it reads `dynamicTypeSize`, so live Dynamic Type changes re-layout.
- **Wiring:** each landing screen gets `onNotifications` / `onSettings`, passed from `AppNavigation` (`openNotifications` / `openSettings`,
  `launchSingleTop`) and `AppScene` (`openNotifications()` → `alertsTarget = ""`; `showingSettings = true`).
- **No duplicate bars:** the old in-page title rows were replaced, not stacked. Holding details (iOS) keeps its own title; Android Holding details
  keeps the app bar.

## 4. Files changed
- **Core:**
  - new `core/src/commonMain/.../navigation/NavigationPolicy.kt`, `LandingTopBarDisplay.kt`, `TabNavigationModel.kt`;
  - new tests `core/src/commonTest/.../navigation/NavigationPolicyTest.kt` (7) and `TabNavigationModelTest.kt` (12).
- **Shared (Compose):**
  - new `designsystem/components/StockLandingTopBar.kt` and `presentation/AppScreens.kt`;
  - `designsystem/icons/StockIcons.kt` (gear);
  - `presentation/AppNavigation.kt` (`openNotifications`, `openSettings`, wiring);
  - `home/HomeScreen.kt`, `markets/MarketsScene.kt` (+ `MarketsAction.Notifications/Settings`), `portfolio/PortfolioScene.kt`,
    `portfolio/PortfolioScreen.kt`, `watchlist/WatchListScene.kt`, `watchlist/WatchListScreen.kt` (+ `WatchListAction.Notifications/Settings`;
    the list menu became `ManageMenu`), `learn/LearnScene.kt`, `learn/LearnScreen.kt`;
  - tests: new `commonTest/.../presentation/AppScreensTest.kt` (3), plus a landing-bar case in `theme/ResponsiveLayoutRulesTest.kt`.
- **iOS:**
  - new `DesignSystem/StockLandingTopBar.swift`;
  - `AppScene.swift`, `HomeScreen.swift`, `MarketsScene.swift`, `PortfolioScene.swift`, `PortfolioScreen.swift`, `WatchListScene.swift`,
    `LearnScene.swift`, `LearnViews.swift`.
- **Docs:**
  - `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6_PLAN.md` (§13–15, C4);
  - this report;
  - `docs/reviews/phase5d_5_6b/` (package + contact sheets; raw captures in the git-ignored `captures/`);
  - `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`.
- **Not touched:** server, API, routes, `NavHost` structure, iOS `NavigationStack`, tab bar, Back handling, Settings and Alerts screens.

## 5. Tests and builds (run sequentially, final code)
| Command | Result |
|---|---|
| `./gradlew :core:jvmTest` | PASS 575/0 (556 + 19 navigation; `PortfolioSessionStateTest` passed) |
| `./gradlew :core:iosSimulatorArm64Test` | PASS 575/0 |
| `./gradlew :app:shared:testAndroidHostTest` | PASS 268/0 (+ `AppScreensTest` 3, landing-bar layout 1) |
| `./gradlew :app:shared:iosSimulatorArm64Test` | PASS 264/0 |
| `./gradlew :app:androidApp:assembleDebug -PstockstepsMockBackendUrl=http://127.0.0.1:8081` | PASS (rebuilt after the icon-tint change) |
| `xcodebuild` (iOS Simulator, `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081`) | PASS (final Swift) |
| `./gradlew :server:test` | NOT RUN (no server change) |

Compose UI tests: NOT RUN. `app:shared` has no Compose UI-test setup. On-device UI-tree dumps verified the Android labels and wiring (§6).

## 6. Device verification (local MOCK server)
Android: emulator-5554, 360 dp, **signed-in test session** (already on the emulator; not signed out). iOS: iPhone 16 simulator, **guest**.

| Check | Android | iOS |
|---|---|---|
| Landing bar on Home, Markets, Portfolio, Watchlist, Learn (light) | PASS (UI dump: "Notifications" and "Settings" last on all five) | PASS |
| Dark | PASS (app theme set to Dark, then back to Light) | PASS |
| Large text | 1.3× and 2.0× PASS (icons stay on the title line) | AX XL and AX5 PASS (icons move under the title; nothing truncated) |
| Notifications from each tab → "My Alerts" (all, no stock filter) → Back returns to the same landing page | PASS ×5 | PASS ×5 ("‹ Home/Markets/…" back label) |
| Settings from each tab → Back / Done returns to the same landing page | PASS ×5 (push) | PASS ×5 (sheet) |
| Existing Search | Home → Search stocks PASS | Home and Markets → search sheet PASS |
| Bottom tabs | PASS (all five switched repeatedly) | PASS |
| Guest mode | NOT RUN (the emulator holds a signed-in session; signing out was avoided to keep it) | PASS (Continue as guest; Portfolio shows its guest state) |
| No new requests / screen recreation | Code review: the bar only adds callbacks; no new presenters, ViewModels or loads. Request counts NOT RUN (the MOCK server doesn't log requests) | same |
| TalkBack / VoiceOver | NOT RUN (owner) | NOT RUN (owner) |

**Device state afterwards:**
- emulator: Light (app setting), 1.0×, `adb reverse` removed;
- simulator: light, Default size, guest;
- MOCK server stopped.

## 7. Known limitations
- **Deviation from the reference image:** the reference shows full-width search fields under the bar ("Search stocks, ETFs or topics", "Search
  your watchlist"). Those are new search scopes (ETFs, topics, filtering the watchlist) that don't exist. Per the brief ("avoid unnecessary
  secondary rows"), the existing search icons stay in the bar. A search-field row is a separate design decision.
- **Home title:** the reference shows a plain "StockSteps" wordmark. The app keeps the existing brand mark (tile + wordmark, D7).
- **Android 2.0×:** the screen-title style barely grows (existing typography), so the icons stay on the title line. iOS stacks them at
  AX XL / AX5.
- **Presentation:** Notifications and Settings are still pushed on Android; on iOS Notifications is pushed and Settings is a sheet. The approved
  global-modal presentation (plan §6) needs the Phase C hosts; doing it now would change Back behaviour.
- **Unread badge:** none; there is no unread contract (D5).

## 8. Deferred to Phase C (unchanged in B)
- Android nested tab graphs, iOS per-tab `NavigationStack`s, independent stacks.
- Canonical tab selection for pushed screens, the cross-tab trail (model ready, not wired), reselect behaviour.
- Global-modal Settings and Notifications, tab-bar visibility changes, state restoration.
- Portfolio nested tabs (C4).
- A notification inbox and unread badges (no data contract).

## 9. Regression risks
- **Removed controls:** Home's "Profile and settings" and "Your alerts" labels are now "Settings" and "Notifications". UI tests or scripts that
  look for the old labels need updating (none in the repo).
- **Watchlist list menu:** moved into `ManageMenu` (same items and actions). Shown only when signed in, as before.
- **Portfolio header:** the About and ⋯ menu are unchanged inside the bar. Holding details keeps its title (iOS) or app bar (Android).

## 10. Recommendation
**APPROVE.** Phase B scope is complete on both platforms with no navigation behaviour change.

Remaining items are owner checks or Phase C work:
- Android guest-mode check: NOT RUN (signed-in emulator);
- screen readers;
- the search-field row from the reference: a design decision.
