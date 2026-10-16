# Phase 5D.5.6D — Final navigation QA, accessibility & cross-platform stabilization (KAN-15)

Status: QA and fixes done 2026-10-16; committed locally on the owner's request as "Stabilize navigation for Phase 5D.5.6D with once-per-screen loading on Android, a kept company model and reading position after reused-top Back on iOS, no duplicate reminders request, readable Practice tabs and title at large text and a guest sign-in card in My Alerts", **not pushed**. Base: `4c5990f` (5D.5.6C4, pushed).
Plan: `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6_PLAN.md` (5D.5.6D). Review package: `docs/reviews/phase5d_5_6d/` (QA matrix in
`CHATGPT_QA_MATRIX.md`).

## 1. Audit summary (before changes)
- **Architecture:**
  - core `NavigationPolicy` (43 `ScreenId`s with kind and canonical tab), `TabNavigation` (trail model with hops and reuse) and
    `PortfolioSections`;
  - Android `TabNavigator` over one nested graph per tab (`RouteTable`; 41 registered routes, Practice is a landing section);
  - iOS `TabRouter` with five `NavigationStack(path:)`, 28 `AppDestination`s and app-wide sheets.
- **Risks going in:**
  1. iOS reused-top Back (C3 known limitation);
  2. delayed path restore on iOS;
  3. Android lifecycle effects on restored entries (C1 had no lifecycle audit, unlike iOS C3);
  4. signed-in and account-switch flows unverified on iOS;
  5. the two large-text defects carried from C4.
- **Working tree:** clean at the start (C4 committed and pushed). No concurrent changes in this checkout. Another emulator (emulator-5556)
  and another session's MOCK server on :8081 (pid 492) were left untouched.

## 2. Navigation inventory
Every destination is registered on both platforms with the policy's canonical tab:
- Android: `AppScreensTest` is exhaustive over `ScreenId` minus APP_LOCK and PRACTICE;
- iOS: an exhaustive `switch` in `AppDestination.screen`, plus the debug coverage check in `TabRouter`.

| Destination (`ScreenId`) | Tab | Android route | iOS | Presentation | Access | Verified |
|---|---|---|---|---|---|---|
| HOME / DAILY_BRIEF / DAILY_BRIEF_HISTORY | Home | `DiscoveryRoute` / `DailyBriefRoute` / `DailyBriefHistoryRoute` | root / `.dailyBrief` / `.briefHistory` | push | all | E, L1 |
| MARKETS, COMPANY_DETAILS, COMPANY_FINANCIALS / _VALUATION / _NEWS, NEWS_INSIGHT, STOCK_MOVEMENT | Markets | `MarketsRoute`, `CompanyDetailsRoute`, `Company*Route`, `NewsInsightRoute`, `StockMovementRoute` | root, `.companyDetails`, `.company*`, `.newsInsight`, `.stockMovement` | push | all | A, S1–S4 |
| DISCOVER, COMPARE, COMPARISON_RESEARCH, COMPARISON_AI | Markets | `ScreenerRoute`, `ComparisonRoute`, `ComparisonResearchRoute`, `ComparisonAiRoute` | `.discover`, `.compare`, `.comparisonResearch`, `.comparisonAi` | push | AI and research need sign-in | C1–C3 (lifecycle fix F2 here) |
| EARNINGS_CALENDAR / _EVENT / _RESULTS / _DETAILS / _DIGEST | Markets | `Earnings*Route` | `.earnings*` | push | digest StockSteps+ | L2 |
| PORTFOLIO, HOLDING_DETAILS, PORTFOLIO_TRANSACTIONS / _TRANSACTION / _INSIGHTS, PRACTICE (section), PRACTICE_ORDER | Portfolio | `PortfolioRoute` (+ section), `HoldingDetailsRoute`, `PortfolioTransaction*Route`, `PortfolioInsightsRoute`, `PracticeOrderRoute` | root (+ section), `.holdingDetails`, `.portfolioTransaction*`, `.portfolioInsights`, `.practiceOrder` | push; Practice in place | orders need sign-in | C, P1–P11 |
| WATCHLIST, STOCK_ALERTS, EARNINGS_REMINDERS | Watchlist | `WatchListRoute`, `AlertsRoute`, `EarningsRemindersRoute` | root, `.stockAlerts`, `.earningsReminders` | push | alerts and reminders need sign-in | D, L3, M2 |
| LEARN, LEARN_TOPICS, LEARN_TERM, GUIDED_RESEARCH | Learn | `LearnRoute`, `LearnTopicsRoute`, `LearnTermRoute`, `GuidedResearchRoute` | root, `.learn(.topics/.term)`, `.research` | push | all | B, S1, S2 |
| COMPANY_SEARCH, TRANSACTION_FORM, PORTFOLIO/PRACTICE_COMPANY_PICKER | — | `StockSearchRoute`, `PortfolioEntryRoute`, `PortfolioSearchRoute`, `PracticeSearchRoute` | sheets | modal (no tab bar) | — | M3, M4 |
| SETTINGS, EARNINGS_DIGEST_SETTINGS, SETTINGS_EARNINGS_REMINDERS, NOTIFICATIONS, AUTH, APP_LOCK | — | `SettingsRoute`, …, `NotificationsRoute`, `AuthRoute`; app lock overlay | sheets / `router.global` | global (no tab bar) | — | M1, M2 |

**Entry points:**
- Android: notification extras (`daily-brief`, `earnings-*`, `alert`);
- iOS: the same payloads through `UNUserNotificationCenter`;
- no app links or universal links exist (only the Google sign-in callback URL). None were added.

## 3. Results
The full matrix is `CHATGPT_QA_MATRIX.md`.
- **Android:**
  - stacks: A, C, D, E PASS (D and E after fixes); B FAIL (P2, open);
  - S1–S5 PASS; reselection from a deep screen and at the root on all five tabs PASS;
  - C4 items 1–7 PASS; global screens and search PASS;
  - notification links PASS; background, rotation and process death PASS.
- **iOS (guest):**
  - A, B, E PASS; S1, S2, S4 (after a fix) and rapid swipe-back PASS;
  - reselection PASS; C4 guest items and the sign-in modal PASS; sheets PASS; background PASS;
  - large text: after fixes, the title and messages follow live changes.
- **BLOCKED:**
  - signed-in iOS (My Portfolio scroll, Holding Back, Practice Order Back, Practice scroll-to-top, account switch, sign-out);
  - sign-out and account switch on Android: there is no approved disposable test account. Signing out the emulator's cached session would
    require typing credentials to sign back in, which isn't allowed.
- **NOT RUN:**
  - iOS notification links (needs a delivered notification tapped while notifications are off);
  - TalkBack and VoiceOver walkthroughs (owner).

## 4. Fixed defects
| # | Sev | Platform | Defect | Root cause | Fix | Regression evidence |
|---|---|---|---|---|---|---|
| F1 | P1 | both (core) | Earnings Reminders sent two GETs on first open | the lazily started presenter loads in `start()`, and the screen's `refresh()` ran while that load was in flight | `EarningsRemindersPresenter` drops a refresh while a load is running (start, account change or refresh), using `try/finally` | new `aRefreshDuringALoadDoesNotRepeatTheRequest` (fails without the fix); device: 1 GET |
| F2 | P1 | Android | returning to a tab re-ran fetching effects: Daily Brief re-opened (refetched the personal section and **cleared AI answers**), plus history, reminders, research list, AI usage, and Practice refresh after an order | Compose disposes a tab's screens on a switch and restores them; `LaunchedEffect` runs again. C1 had no lifecycle rule (iOS has `taskOnce` since C3) | new `LaunchedOncePerEntry` / `rememberEntryOnce` (flags in a ViewModel of the back-stack entry: kept across tab switches, cleared with the entry and on process death). The brief re-opens only when the shared presenter shows another brief | device: brief and reminders return with 0 req |
| F3 | P1 | iOS | after Back from a reused company page, Markets' copy was rebuilt: **6 refetches**, scroll lost (the C3 limitation) | the pop bumped `revision`, so `retain(only:)` ran while the path briefly lacked the symbol and released the shared model; the rebuilt view also had a new `@State` earnings model | `TabRouter.restoring` keeps the pending path's symbols in use. The next-earnings model and viewed flag moved to the per-symbol `CompanyDetailsModel`. The page records its offset, and the router marks only a restored reused page to return to it, once its content is laid out (no timer) | device: 0 page requests; position identical; rapid swipe-back clean |
| F4 | P1 | Android | Practice tabs broke inside words at 2.0× ("Overvi-ew", "Challen-ges") | four equal-width segments | `StockSegmentedControl(scrollWhenTight = true)` (optional, default unchanged) applies the shared `StockPillLayout.fitsEqualWidth` and falls back to natural widths in `StockFadingScrollRow`. Practice tabs also use `asTabs` | `and-a11y-practice-*-2x` |
| F5 | P1 | iOS | the Practice title didn't grow with Dynamic Type | `.largeTitle` metrics | the token's own curve (`StockStepsTheme.font(type.screenTitle)`) | `p-ios-ax-practice-cold` |
| F6 | P1 | iOS | in the kept-alive Practice section the title and guest message stayed at the old size after a **live** text-size change | design-system children whose inputs don't change never redrew | `StockTitleWithBadge` and `StockSectionMessage` read `dynamicTypeSize` in `body` (repo rule). A first `.id(typeSize)` attempt dropped the message from the `LazyVStack`, so it was reverted | `p-ios-ax-practice`, live round trip pixel-identical |
| F7 | P1 | iOS | guest My Alerts (bell) showed placeholders forever (since C2) | guests have no user snapshot, so `alertsLoaded` never becomes true | a guest gets the Android sign-in card (same copy); Sign in closes the sheet and opens sign-in | `p-ios-notifications`, `ios-notif-signin` |

## 5. Open issues
- **P2, Android:** a Learn term page that is only a little taller than the screen comes back at the top after a tab switch. Long pages
  (Company Details, Financials, Topics, Markets, Daily Brief) keep their position.
  - Hoisting the list state with `rememberSaveable(id)` didn't change it, so it was reverted.
  - Likely cause (not confirmed): the first layout after a restore has a slightly taller viewport, which clamps the small saved offset to 0.
  - Backlog: measure with a layout log.
- **P2, Android:** after process death, the Financials period resets to Annual (in-memory state). No new persistence was added.
- **P2, iOS:**
  - "Back" instead of "‹ AAPL" next to the long "Apple Financials" title (native shortening);
  - a search row can show ticker initials before its logo loads.
- **Process note:** emulator-5554 exited during the dark and 2.0× capture batch. It was relaunched from its AVD (`Pixel_9_Pro`), with app
  data kept.

## 6. Request-count summary (local MOCK; only unexpected or notable results)
| Sequence | Initial | Extra after the step | Expected | Note |
|---|---|---|---|---|
| Markets → Apple → Portfolio → Markets | Apple 7 | 0 | 0 | |
| Portfolio → Practice → My Portfolio → Practice | Practice 2 | 0 | 0 | |
| Learn → term → Markets → Learn | 0 | 0 | 0 | |
| Home → Brief → Portfolio/Markets → Home | 1 | 1 → **0 after F2** | 0 | |
| Company Details → Financials → Back | Financials 2 | 0 | 0 | |
| Practice Order → Back → Practice | order 2 (search, preview) | 0 | 0 | |
| Reselect at the root | — | 0 | 0 | |
| Background → foreground | — | 0 (both) | 0 | |
| Reminders open / return | 2 → **1** / 1 → **0** | | 1 / 0 | F1, F2 |
| iOS reused Apple → Back → Markets | — | 6 → **0** | 0 | F3 |
| Home re-entry after > 60 s | — | `/me/alerts` + `/me/watchlists` | allowed | Home's 60 s TTL (`PersonalDashboardStore.onVisible`) |
| Guest → sign-in, account switch | — | — | — | BLOCKED |

## 7. Tests and builds (sequential, final code)
| Suite | Result |
|---|---|
| `:core:jvmTest` | PASS 581/0 (+1 reminders test) |
| `:core:iosSimulatorArm64Test` | PASS 581/0 |
| `:app:shared:testAndroidHostTest` | PASS 273/0 (+2 `EntryOnceTest`) |
| `:app:shared:iosSimulatorArm64Test` | PASS 269/0 |
| `:app:androidApp:assembleDebug` (`127.0.0.1:8081`) | PASS |
| `xcodebuild` (MOCK `localhost:8081`) | PASS |
| server tests | NOT RUN (no server change) |
| Swift router tests / Compose UI tests | NOT RUN (no iOS test target; no instrumented test setup) |

The reminders test was checked to fail without the guard.

**Device restore:**
- the emulator has the final APK (`127.0.0.1:8081`, `adb reverse tcp:8081`), "My saved accounts", Light, 1.0×;
- the simulator has the standard MOCK build, guest, Light, default text size;
- the QA proxy (:8082) and MOCK (:8083) are stopped.

## 8. Recommendation
- **APPROVE WITH FIXES for implementation:** the fixes above are in and verified on the reachable flows.
- **HOLD for final navigation sign-off:** signed-in iOS, sign-out and account switch on both platforms, iOS notification links, and the
  TalkBack/VoiceOver walkthrough are still unverified. Navigation is not complete until the owner runs them with an approved test account.
