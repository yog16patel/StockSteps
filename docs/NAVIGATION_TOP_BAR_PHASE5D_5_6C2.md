# Phase 5D.5.6C2 — iOS independent navigation stacks (KAN-10)

- **Status:** implemented and verified on the local MOCK server (iOS only). Approved by the owner; committed and pushed as "Add iOS independent tab stacks for Phase 5D.5.6C2 with a NavigationStack per tab, typed destinations owned by the shared navigation policy, a cross-tab Back trail and a visible tab bar on pushed screens".
- **Base:** `2341662` (5D.5.6C1, pushed).
- **Plan:** `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6_PLAN.md` §3–§8 and §14 (D1–D12). Android C1 is the reference behaviour. Android code is unchanged.

## 1. What changed for the user (iOS)
- **The tab bar stays visible on every tab screen**, pushed ones included: Company Details, Financials, Valuation, News, Earnings, Discover,
  Compare, Daily Brief, Learn topics and terms, Guided research and stock alerts. Before C2 every push covered it.
- **Five independent stacks.** Each tab keeps its own history, arguments, filters, scroll position and loaded data while you switch tabs.
  Markets → Apple → Financials (Quarterly) → Portfolio → Markets shows Financials again, pixel-identical.
- **The selected tab is the visible screen's canonical tab** (core `NavigationPolicy`). For example, Apple opened from Home, Watchlist or
  Search shows **Markets**; Daily Brief opened from Markets shows **Home**; stock alerts opened from Portfolio show **Watchlist**.
- **Back from a screen opened from another tab returns there**, with the back button or swipe-back: Home → Search → Apple (Markets) → Back →
  Home. The Markets stack is left as it was.
- **Reselecting the visible tab** pops it to its root (D3). At the root, it scrolls the landing page to the top (D12).
- **Notifications** (all alerts) is now an app-wide sheet with Done and no tab bar (was a push). Settings, Search, sign-in, the transaction form
  and the pickers keep their existing sheets.
- **Same screens and design:** no content or styling changes.

## 2. Architecture
```
 TabView(selection: router.selection)                         core (shared, tested)
 ├─ Home      NavigationStack(path: router.pathBinding(.home))      NavigationPolicy  ScreenId → tab
 ├─ Markets   NavigationStack(path: …(.markets))                    TabNavigation: open · selectTab · reselect ·
 ├─ Portfolio NavigationStack(path: …(.portfolio))   ◄── decides ──     sync · reset (I1–I11)
 ├─ Watchlist NavigationStack(path: …(.watchlist))
 └─ Learn     NavigationStack(path: …(.learn))
      each root: landing scene + .navigationDestination(for: AppDestination.self) { destination($0) }
 sheets on the TabView: Settings · Notifications/digest settings (router.global) · Search · Sign in
```
- **`AppDestination`** (`TabRouter.swift`): one `Hashable` case per pushed screen with its arguments (16 cases).
  - It replaces `AppScene`'s 17 presentation flags (`detailsSymbol`, `showingCompare`, `earningsEventId`…), which could each be on the stack only
    once.
  - `screen` is an exhaustive `switch` to core `ScreenId`, the only iOS ownership table (like Android's `AppRouteScreens`). The tab always comes
    from `NavigationPolicy.parentTab`.
- **`TabRouter`** (`@Observable`): the selected tab, five paths, the core `TabNavigationState`, the global sheet and the scroll-to-top signal.
  - `open` asks `TabNavigation.open` for the step: push on the canonical tab, switch tabs, record a hop.
  - Each tab's path binding setter treats a shorter path as Back (button or swipe). When it removes the latest hop's entry, the router selects
    the hop's origin tab.
  - Every change syncs the paths into the model (`TabNavigation.sync`), so pops can't leave stale hops.
  - `selection` routes a tap on another tab to `selectTab`, which clears the trail (D2), and a tap on the visible tab to `reselect` (D3/D12).
  - `reset()` runs on any account change (I11). `AppScene` is also rebuilt on sign-out (`ContentView`).
- **Native navigation only:** no custom view stack. Landing pages keep their own bars (`stockStepsTopBar(.screen(title, visible: false))`
  names the next page's back button). Pushed screens use their existing navigation bars.
- **MOCK banner:** `sampleDataInset()` now wraps each tab's `NavigationStack`, so the banner sits above the tab bar on every tab screen.
- **Scroll to top:** the `scrollsToTopOnReselect(_:)` modifier (iOS 18 `ScrollPosition`) on the five landing scroll views.
- **In-session only:** nothing is persisted (cold-start restoration stays deferred, D8).

## 3. Route coverage (iOS)
| `AppDestination` | ScreenId | Tab |
|---|---|---|
| `companyDetails(symbol)` | COMPANY_DETAILS | Markets |
| `discover`, `compare` | DISCOVER, COMPARE | Markets |
| `earningsCalendar`, `earningsEvent`, `earningsResults`, `earningsDigest`, `earningsDetails` | EARNINGS_* | Markets |
| `dailyBrief`, `briefHistory` | DAILY_BRIEF, DAILY_BRIEF_HISTORY | Home |
| `practice`, `practiceOrder` | PRACTICE, PRACTICE_ORDER | Portfolio |
| `stockAlerts(symbol)`, `earningsReminders` | STOCK_ALERTS, EARNINGS_REMINDERS | Watchlist |
| `learn(.topics/.term)`, `research` | LEARN_TOPICS, LEARN_TERM, GUIDED_RESEARCH | Learn |

**Global sheets:**
- `GlobalSheet.notifications` (NOTIFICATIONS) and `GlobalSheet.digestSettings` (EARNINGS_DIGEST_SETTINGS, with its reminders page);
- Settings (+ reminders, digest);
- Search, sign-in;
- the transaction form and pickers (feature sheets, unchanged).

**Nested destinations unchanged (C3):** 26 `navigationDestination(isPresented:/item:)` declarations inside feature scenes. They still push onto
the current tab's stack:
- Company Details → Financials, Valuation, News, Movement, Learn term, Earnings, Compare, Research;
- Portfolio → Insights, Transactions, Holding;
- Learn topic → term;
- Markets → Movement.

Each is correct whenever its screen's owner is the current tab (most of them). See §7 for the exceptions.

## 4. Files changed
- **iOS:**
  - `TabRouter.swift` (new: `AppDestination`, `GlobalSheet`, `TabRouter`, `scrollsToTopOnReselect`);
  - `AppScene.swift` (rewritten around the router);
  - `HomeScreen.swift`, `MarketsScene.swift`, `PortfolioScreen.swift`, `WatchListScene.swift`, `LearnViews.swift` (one modifier each).
- **Docs:** this report, `docs/reviews/phase5d_5_6c2/`, the plan, the handoff and the status.
- **Not touched:** Kotlin core and shared, Android, server.

## 5. Tests and builds
See §9. No Swift unit-test target exists in the iOS project, so the router is covered by the core model tests (the same `TabNavigation` contract, incl. `sync`) and the device matrix.

## 6. Device QA (iPhone 16 simulator, guest, local MOCK)
| # | Scenario | Result | Evidence |
|---|---|---|---|
| A | Markets → Search → Apple → Financials (Quarterly, scrolled) → Portfolio → Markets: Financials restored, pixel-identical | PASS | `ios-c2-a-restored` |
| B | Home → Search → Apple: Markets selected, tab bar visible | PASS | `ios-c2-b-home-apple` |
| B | Back button → Home (trail); swipe-back from MSFT → Home | PASS | `ios-c2-b-back`, `ios-c2-b-msft`, `ios-c2-b-swipe` |
| C | Reselect Markets on Apple → Markets root; scrolled root → reselect → top; Learn term → reselect → Learn root | PASS | `ios-c2-c-*` |
| D | Notifications (global sheet, Done) and Settings (sheet, Done): no tab bar; dismissal returns to Home | PASS | `ios-c2-d-*` |
| D | Search sheet (keyboard) and transaction form sheet from Apple: no tab bar; Cancel returns to Apple | PASS | `ios-c2-form` |
| E | Markets landing scrolled → Watchlist → Markets: pixel-identical (filter Losers + scroll) | PASS | `ios-c2-e-*` |
| F | Apple in dark and AX XL: Markets selected, tab bar and banner visible | PASS | `ios-c2-cd-dark`, `ios-c2-cd-axxl` |
| F | Learn topic → term: Learn selected | PASS | `ios-c2-learn-term` |
| G | Guest: Continue as guest; Practice from Learn → the sign-in sheet over Learn; closing it keeps Learn | PASS | `ios-c2-g-gate` |
| G | Sign-out / account change reset | NOT RUN (no sign-in on devices; code: `.onChange(of: user.id) → router.reset()`, plus `AppScene` rebuilt by `ContentView`) | — |
| — | Practice with Portfolio selected, Holding details, Watchlist rows | BLOCKED (guest; these need the owner's signed-in session) | — |
| — | VoiceOver | NOT RUN (Simulator; owner) | — |

**Device state afterwards:** simulator light, Default size, guest; MOCK server stopped.

## 7. Known limitations (C3 / C4)
- **Nested cross-tab links:** Company Details' nested Learn term and Research destinations open on the Markets stack with Markets selected
  (policy owner: Learn). Back works and returns to Company Details. C3 moves them to the router so they select Learn with a hop.
- **Back label on a cross-tab screen:** it names the stack's previous page (for example "‹ Markets" on Apple opened from Home), while Back
  returns to the origin tab, as the plan requires. C3 decides the label.
- **Reselect with only nested pushes:** e.g. Portfolio → Holding details (nested, not on the router path) scrolls to top instead of popping. C3
  (router) and C4 (Portfolio nested tabs).
- **Opening the identical top of another tab** (e.g. Apple already on top of Markets) switches tabs without a return hop, because iOS can't
  observe a Back that pops nothing.
- **`.task` re-runs:** pushed screens now stay alive in their tab and reappear on tab switches, so their `.task` can run again. Company Details
  guards its load. The full audit of repeated work (e.g. `recordViewedCompany` for signed-in users) is C3.
- **No cold-start restoration** (D8).

## 8. Deferred
- **C3:** nested cross-tab migration, the back-label decision, the `.task` audit, swipe-back parity for nested flows.
- **C4:** Portfolio nested tabs.
- **D:** final QA on both platforms (signed-in flows with the owner).

## 9. Test and build results (sequential, final code)
| Command | Result |
|---|---|
| `./gradlew :core:jvmTest` | PASS 576/0 (`PortfolioSessionStateTest` passed this run) |
| `./gradlew :core:iosSimulatorArm64Test` | PASS 576/0 |
| `./gradlew :app:shared:iosSimulatorArm64Test` | PASS 266/0 |
| `./gradlew :app:shared:compileAndroidMain` (Android smoke, no shared change) | PASS |
| `xcodebuild … -sdk iphonesimulator … STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081 build` | PASS |
| `./gradlew :app:shared:testAndroidHostTest`, `:server:test` | NOT RUN (no Kotlin or server change) |
| Swift unit tests for `TabRouter` | NOT RUN (no iOS test target; adding one is out of scope; covered by core model tests + device QA) |

## 10. Recommendation
**APPROVE.** C2 scope is met on iOS with no regressions found. Open items (§7) are C3/C4 work by plan; signed-in flows (Practice, Holding details, Watchlist rows, sign-out) need the owner.
