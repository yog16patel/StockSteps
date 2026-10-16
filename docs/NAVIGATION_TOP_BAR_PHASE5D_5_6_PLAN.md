# Phase 5D.5.6 — Shared Navigation & Top App Bar: audit and implementation plan (5D.5.6A)

- **Status:** audit and plan only (2026-10-15). No application code changed. **Approved by the owner (2026-10-15): decisions D1–D12 as recommended (§12).** Implementation starts with 5D.5.6B; each phase still needs the owner's go-ahead.
- **Baseline:** `ebadf96` (5D.5.5D closure, pushed).
- **Supersedes:** the planning note `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6_PLAN_NOTE.md`. In particular, its recommendation that shared screens keep
  the tab they were opened from is **withdrawn**: R1 (a canonical parent tab per screen) is the approved product rule.
- **Sources audited (the code is authoritative):**
  - Android: `app/shared/.../presentation/AppNavigation.kt` (single `NavHost`, 40 `composable<…>` routes), `App.kt`,
    `portfolio/PortfolioRoute.kt` (`PortfolioTabRoutes`), `MainActivity.kt` (notification links);
  - iOS: `ContentView.swift`, `AppScene.swift` (17 pushed destinations + 2 inside Settings + 3 sheets) and 26 nested `navigationDestination`s
    in feature scenes (48 push destinations in total, many of them repeats of the same screen, e.g. `LearnTermView` in 6 places).

---

## 1. Current state (findings)

### 1.1 Android
- **One `NavHost`, one `NavController`, flat graph.** All 40 routes are siblings; there are no nested graphs.
- **Tab switching** (`openTab`): `navigate(tabRoot) { popUpTo(DiscoveryRoute) { saveState = true }; launchSingleTop = true; restoreState = tab != HOME }`.
  - This already uses Navigation's multiple-back-stack support for Markets, Portfolio, Watchlist and Learn.
  - Home is **never restored**: Home → Daily Brief → Markets → Home shows the Home root.
- **Pushed screens join the stack of the tab they were opened from.** Watchlist → Company Details is saved with Watchlist's stack.
  The only exception is the start destination (Home): its children go wherever the current stack is.
- **Tab highlight** is computed per route, and child screens are mostly not covered:
  - Home: `DiscoveryRoute` only. Markets: `MarketsRoute` only. Watchlist: `WatchListRoute` only.
  - Portfolio: `PortfolioTabRoutes` (5 routes, D3.1). Learn: Learn, Topics and Term.
  - **Consequence:** on Company Details, Screener, Comparison, Earnings, Daily Brief, Alerts, Guided Research, Practice and Settings,
    **no tab is highlighted.**
  - On a Learn term opened from Company Details, Learn is highlighted, but the screen sits in the Markets/Watchlist/Home stack, so the
    highlight and the stack disagree.
- **Tab bar:** hidden only on Search (three search routes) and Auth. Visible everywhere else, including Settings (no tab selected).
- **Top bar:** one app-level `StockStepsTopBar` with a title and back button per route (a long `when`). Hidden on the five landing pages and on
  Company Details, Financials, Valuation, News insight, Stock movement and Alerts, which draw their own headers.
- **Back:** system Back pops the single stack. There is no cross-tab logic. Back at a non-Home tab root goes to Home (`popUpTo(DiscoveryRoute)`).
- **Push notifications** (`notificationLinks`) `navigate(...)` on top of whatever stack is current (no tab change, no highlight).
- **Auth:**
  - `AuthRoute` is the start destination when signed out (with an account graph);
  - sign-out navigates to `AuthRoute` popping the whole graph (all stacks cleared);
  - a guest opening Practice is pushed to `AuthRoute` and returned to the previous screen after sign-in (Practice is not resumed).
- **App lock:** an overlay in `App.kt` above the `NavHost`; navigation state survives.
- **ViewModels** are scoped to their back-stack entry (`viewModel(key = …)` inside each `composable`). Saved back stacks keep their ViewModels,
  so restoring a tab doesn't refetch.
  - Company Details from two entry points gets two separate ViewModels (key `company-details:<symbol>:<env>`, per entry).
- **Process death:** the NavController state (including saved tab stacks) is saved by Navigation. The screens' `rememberSaveable` state
  (112 uses) and `rememberLazyListState` (59 uses) restore with their entries.

### 1.2 iOS
- **One `NavigationStack` wraps the `TabView`** (`AppScene.swift:72-73`). Every push covers the tab bar, so:
  - the tab bar is hidden on every child screen;
  - there are no per-tab stacks. While a screen is pushed you can't switch tabs, and after Back you return to the same tab;
  - tab selection is `@State selectedTab`, changed only by the tab bar and a few closures (`onLearn: { …; selectedTab = .learn }`).
- **Destinations are flags or optional items** (`detailsSymbol`, `showingCompare`, `earningsEventId`…), 17 on `AppScene`. The consequences:
  - Each destination type can be on the stack **once**: Company Details → Earnings event → company sets `detailsSymbol` while Company Details is
    already shown, so it replaces the item instead of pushing a second page.
  - Order isn't a path. SwiftUI stacks flag destinations in activation order, and there's no programmatic "pop to root" except resetting every
    flag.
  - There is no `NavigationPath` and no `@SceneStorage`, so nothing restores after the app is terminated. The app always restarts on Home, or on
    sign-in.
- **Nested destinations** live inside feature scenes:
  - Company Details: 8 (Financials, Valuation, News, Movement, Learn term, Earnings, Compare, Research);
  - Portfolio: 4;
  - Learn: 4;
  - Company News: 2;
  - Screener: 2;
  - Financials, Valuation, Comparison research, Discover categories and Markets movement: 1 each.

  They stay valid inside a per-tab stack.
- **Sheets (modal):**
  - Settings, with its own `NavigationStack` (Reminders, Digest settings);
  - Auth;
  - Company Search;
  - in-feature sheets: add transaction, account, watchlist pickers, the Comparison picker, explanations.
- **Auth:** `ContentView` shows `AuthScene` until the user enters the app (guest or signed in). Sign-out sets `hasEnteredApp = false`, which
  destroys `AppScene` and every stack.
- **App lock:** an overlay over `AppScene` (state preserved, hidden from VoiceOver).
- **Re-appearance work:**
  - `.task` runs again whenever a view reappears. Company Details guards its load (`if model.overview.value == nil`), but calls
    `accounts.client?.recordViewedCompany(symbol:)` on every appearance.
  - Today a pushed screen only reappears after Back. With per-tab stacks it reappears on **every tab switch**, so unguarded `.task` work becomes
    extra requests (risk R-3).

### 1.3 Headers today (landing pages)
| Landing page | Android | iOS | Notifications | Settings |
|---|---|---|---|---|
| Home | `PersonalHomeHeader`: "StockSteps" brand + Search, "Your alerts" (bell), "Profile and settings" (person) | same (`HomeScreen.header`) | ✓ (bell → Alerts, all) | ✓ (person → Settings) |
| Markets | in-page title "Markets" + Search icon | in-page title + search | — | — |
| Portfolio | in-page title (account menu) | in-page large title | — | — |
| Watchlist | in-page "Watchlist" header + count + Search + list menu | same | — | — |
| Learn | in-page title + topic search | in-page large title | — | — |

There is no separate notification inbox. "Your alerts" opens the Alerts screen (all alerts + history; `AlertsRoute(null)` / `alertsTarget = ""`).
The alert history has no unread contract, so no badge is possible (`HomeScreen.kt` comment).

---

## 2. Complete screen inventory and canonical parent tab
Legend:
- **Push** = pushed onto a tab stack;
- **Modal** = presented above the tabs (Android: a route without the tab bar; iOS: sheet);
- **Global** = outside the five tabs.
- "Today" columns are the audited behaviour; **Proposed** columns are the plan.

### 2.1 Tab destinations
| # | Screen | Android route | iOS destination | Today: highlight / tab bar | **Canonical tab** | **Proposed tab bar** | Presentation | Back (proposed) |
|---|---|---|---|---|---|---|---|---|
| 1 | Home | `DiscoveryRoute` | `AppRoute.home` (TabView root) | Home / visible | **Home** | visible | root | Android: exit app. iOS: none |
| 2 | Daily Brief | `DailyBriefRoute(id)` | `briefTarget` | none / visible · iOS hidden | **Home** ⚑ | visible | push | previous entry (§4) |
| 3 | Previous briefs | `DailyBriefHistoryRoute` | `showingBriefHistory` | none / visible · iOS hidden | **Home** | visible | push | previous entry |
| 4 | Markets | `MarketsRoute` | `AppRoute.markets` | Markets | **Markets** | visible | root | Android → Home tab; iOS none |
| 5 | Company Search | `StockSearchRoute` | `showingSearch` (sheet) | hidden (Android), sheet (iOS) | **Markets (modal)** ⚑ | hidden | **modal** | close → exact previous screen |
| 6 | Company Details | `CompanyDetailsRoute(symbol)` | `detailsSymbol` | none / visible · iOS hidden | **Markets** | visible | push (cross-tab) | previous entry, across tabs (§4) |
| 7 | Financials | `CompanyFinancialsRoute` | CD → `showFinancials` | none | **Markets** | visible | push | Company Details |
| 8 | Valuation | `CompanyValuationRoute` | CD → `showValuation` | none | **Markets** | visible | push | Company Details |
| 9 | Company News | `CompanyNewsRoute` | CD → `showNews` | none | **Markets** | visible | push | Company Details |
| 10 | News insight | `NewsInsightRoute` | News → `insightArticle` | none | **Markets** | visible | push | News |
| 11 | Why did it move? | `StockMovementRoute` | Markets/CD/News → `movementSymbol`/`showMovement` | none | **Markets** | visible | push | previous entry |
| 12 | Discover (Screener) | `ScreenerRoute` | `showingDiscover` (+ category `NavigationLink`) | none | **Markets** | visible | push | previous entry |
| 13 | Compare | `ComparisonRoute` | `showingCompare`, CD → `showCompare` | none | **Markets** | visible | push | previous entry |
| 14 | Comparison research | `ComparisonResearchRoute` | Compare → `showingResearch` | none | **Markets** | visible | push | Compare |
| 15 | Comparison AI | `ComparisonAiRoute` | Compare/Research → `showingAi`/`asking` | none | **Markets** | visible | push | Compare / Research |
| 16 | Earnings calendar | `EarningsCalendarRoute` | `earningsCalendar` | none | **Markets** | visible | push | previous entry |
| 17 | Earnings event | `EarningsEventRoute(id)` | `earningsEventId` | none | **Markets** | visible | push | previous entry |
| 18 | Earnings results | `EarningsResultsRoute(id)` | `earningsResultsId` | none | **Markets** | visible | push | previous entry |
| 19 | Earnings details (company) | `EarningsDetailsRoute(symbol)` | `earningsSymbol`, CD → `showEarnings` | none | **Markets** | visible | push | previous entry |
| 20 | Earnings digest (StockSteps+) | `EarningsDigestRoute` | `showingEarningsDigest` | none | **Markets** ⚑ | visible | push | previous entry |
| 21 | Portfolio | `PortfolioRoute` | `AppRoute.portfolio` | Portfolio | **Portfolio** | visible | root | Android → Home tab |
| 22 | Holding details | `HoldingDetailsRoute(account, symbol)` | Portfolio → `holdingSymbol` | Portfolio | **Portfolio** | visible | push | Portfolio |
| 23 | Transactions | `PortfolioTransactionsRoute` | Portfolio → `showTransactions` | Portfolio | **Portfolio** | visible | push | previous entry |
| 24 | Transaction details | `PortfolioTransactionRoute(id)` | `openTransactionId` (2 places) | Portfolio | **Portfolio** | visible | push | previous entry |
| 25 | Portfolio insights | `PortfolioInsightsRoute` | Portfolio → `showInsights` | Portfolio | **Portfolio** | visible | push | Portfolio |
| 26 | Practice portfolio | `PracticeRoute` | `showingPractice` | none | **Portfolio** ⚑ | visible | push (sign-in gate) | previous entry |
| 27 | Practice order | `PracticeOrderRoute(symbol, side)` | `practiceTarget` | none | **Portfolio** | visible | push | previous entry |
| 28 | Watchlist | `WatchListRoute` | `AppRoute.watchlist` | Watchlist | **Watchlist** | visible | root | Android → Home tab |
| 29 | Stock alerts | `AlertsRoute(symbol)` | `alertsTarget = symbol` | none | **Watchlist** | visible | push | previous entry |
| 30 | Earnings reminders | `EarningsRemindersRoute` | `showingEarningsReminders` | none | **Watchlist** (from tabs) / Settings scope (from Settings) ⚑ | visible / hidden | push | previous entry |
| 31 | Learn | `LearnRoute` | `AppRoute.learn` | Learn | **Learn** | visible | root | Android → Home tab |
| 32 | Learn topics | `LearnTopicsRoute` | `learnTarget .topics`, Learn → `openTopic`/`showTopics` | Learn | **Learn** | visible | push | previous entry |
| 33 | Learn term | `LearnTermRoute(id)` | `learnTarget .term`, `openTerm`, `nextTerm`, CD/Financials/Valuation → `learnTerm` | Learn (stack disagrees) | **Learn** | visible | push (cross-tab from CD) | previous entry, across tabs |
| 34 | Guided research | `GuidedResearchRoute(symbol)` | `researchTarget`, CD → `research` | none | **Learn** ⚑ | visible | push (cross-tab) | previous entry, across tabs |

### 2.2 Global and modal destinations (outside the five tabs)
| # | Screen | Android route | iOS | Highlight | Tab bar | Presentation | Dismiss / Back |
|---|---|---|---|---|---|---|---|
| 35 | Settings | `SettingsRoute` | `showingSettings` sheet | unchanged underneath | hidden | **modal** (Android full screen, iOS sheet) | Close/Back → exact previous screen |
| 36 | Earnings digest settings | `EarningsDigestSettingsRoute` | Settings → `showingSettingsDigest`, Digest → `showingDigestSettings` | — | hidden | push **inside the Settings modal** | Back → Settings / Digest |
| 37 | Notifications (all alerts + history) | `AlertsRoute(null)` today | `alertsTarget = ""` today | unchanged underneath | hidden | **modal** ⚑ (new entry point; reuses the Alerts screen) | Close → exact previous screen |
| 38 | Sign in / create account | `AuthRoute` | root gate (`ContentView`) / `showingAuth` sheet | — | hidden | **modal** (root gate when signed out) | Close → previous screen; after sign-in → previous screen |
| 39 | App lock | overlay (`App.kt`) | overlay (`ContentView`) | — | hidden (covered) | overlay | unlock → unchanged state |
| 40 | Add / edit transaction | `PortfolioEntryRoute` (push today) | sheet (`showTransaction`, CD `showPortfolio`) | — | hidden | **modal form** ⚑ (Android changes from push) | Save/Cancel → opener |
| 41 | Portfolio company picker | `PortfolioSearchRoute` | sheet inside the form | — | hidden | modal picker | returns the selection |
| 42 | Practice company picker | `PracticeSearchRoute` | `showingSearch` + `searchForPractice` | — | hidden | modal picker | selection → Practice order (Portfolio) |
| 43 | In-screen sheets | Compose dialogs/sheets | SwiftUI sheets | — | unchanged | modal | unchanged (out of scope) |

The in-screen sheets in row 43 are: explanations, watchlist and account pickers, the Comparison picker (`AddCompanyDialog` / `AddCompanySheet`),
reminder sheets, filters and saved screens.

**Totals audited:** 40 Android routes (+ the app-lock overlay); 22 `AppScene` destinations (17 push + 2 in Settings + 3 sheets); 26 nested iOS
push destinations; ≈20 in-screen sheets.

⚑ = ambiguous ownership; the recommendation is in §12 (decision D1).

---

## 3. Architecture alternatives

The requirement that makes this non-standard is R3: a screen shows its **canonical** tab (Company Details → Markets), while Back follows the
**actual** history (back to Watchlist), and returning must not damage the Markets stack.

### A. Canonical stacks + a cross-tab trail (**recommended**)
A destination is always pushed onto **its canonical tab's stack**. If that isn't the current tab, the app switches to the canonical tab first and
records a **hop**: the origin tab, the target tab and the target stack depth at which the cross-tab entry starts. The hops form a LIFO **trail**.
- **Highlight = the stack owner, always.** The selected tab is simply the tab whose stack is visible. No override and no contradiction: tapping
  the highlighted tab behaves like reselecting it.
- **Back:** pop the current stack. If the popped entry is the start of the latest hop, switch to the hop's origin tab (whose stack was never
  touched) and drop the hop. Watchlist → Company Details → Back → Watchlist, exactly as before.
- **Markets stack safety:** the cross-tab entry is pushed *on top of* Markets' existing stack (e.g. Screener Results). Back removes it, so
  Markets returns to exactly what it was.
- **Chains:** Watchlist → Company Details (Markets) → Learn term (Learn): two hops. Back → Company Details, Back → Watchlist.
- **Platform fit:**
  - Android: Navigation's multiple back stacks (nested graph per tab, `saveState`/`restoreState`). Route membership in a tab graph *is* the
    canonical mapping;
  - iOS: `TabView` with one `NavigationStack(path:)` per tab, the standard SwiftUI pattern.
- **Cost:** a small trail model (core, pure, unit-tested) plus a Back hook on each platform.

### B. Origin stacks + highlight override
Destinations are pushed onto the **current** tab's stack (as Android does today), and the bar *highlights* `parentTab(top)` instead of the
owning tab.
- Back is trivially right.
- But the highlighted tab is not the stack owner:
  - tapping the highlighted Markets shows a *different* screen (the Markets stack), and the visible Company Details disappears;
  - tapping the un-highlighted Watchlist pops Company Details ("reselect");
  - switching away and back to Watchlist reopens Company Details with Markets highlighted.
- The same company can live in five stacks, and highlight and behaviour disagree permanently. That makes it confusing, hard to explain to
  screen-reader users and hard to test.
- **Rejected.**

### C. One global history + per-tab views (browser model)
A single linear history; tabs are filters that show "the last screen of tab X".
- Exact Back history for free.
- But tab stacks aren't independent (R2): popping across a tab boundary rewrites what another tab shows. It fights both native frameworks:
  Navigation Compose's saved stacks, and SwiftUI's per-tab `NavigationStack`. It needs a custom navigator on both platforms.
- **Rejected** (highest migration risk).

### D. A shared Kotlin navigator driving both platforms
One KMP state machine owns every stack; Compose and SwiftUI render it.
- Maximum parity, but SwiftUI would have to mirror Kotlin state into `NavigationPath` (two sources of truth: swipe-back, system gestures).
- It also means rewriting every Android `composable` call site.
- **Rejected** as a whole. Its good part is kept: the **policy** (mapping, hop and trail rules, visibility, reselect) is shared Kotlin.

### Comparison
| Criterion | A canonical + trail | B origin + override | C global history | D shared navigator |
|---|---|---|---|---|
| R1 highlight correct | ✓ | ✓ (cosmetic) | ✓ | ✓ |
| R2 independent stacks | ✓ native | ✓ native | ✗ | ✓ |
| R3 Back to origin, Markets intact | ✓ | ✓ | ✓ | ✓ |
| Highlight = stack owner | ✓ | ✗ | n/a | ✓ |
| Native fit (Navigation / NavigationStack) | high | high | low | low on iOS |
| Shared, testable rules | core policy | core policy | custom both sides | all in core |
| Migration risk | medium | low | high | high |
| **Verdict** | **Recommended** | rejected | rejected | rejected |

### Recommended structure
- **Core** (`core/.../navigation/`, new, pure Kotlin, tested on JVM and iOS):
  - `AppTab` (HOME, MARKETS, PORTFOLIO, WATCHLIST, LEARN) and `ScreenId` (one entry per row of §2);
  - `NavigationPolicy.owner(screen): ScreenOwner` (`Tab(tab)` | `Modal` | `Global`);
  - `NavigationPolicy.tabBarVisible(screen)`;
  - `NavigationPolicy.onReselect(depth, atTop)` → `PopToRoot` | `ScrollToTop` | `None`;
  - `CrossTabTrail`:
    - `push(currentTab, target, targetDepth)` → `NavStep(switchTo, recordHop)`;
    - `popped(tab, newDepth)` → origin tab or null;
    - `tabSelectedByUser()` → clears the trail (D2);
    - `reset()` on sign-out;
  - `TopBarDisplay`: landing titles, action labels and spoken labels (Notifications, Settings, Search).
- **Android:** routes map to `ScreenId` in `app:shared` (one list, like `PortfolioTabRoutes`, with a test that every registered route has an
  id). There's one `NavHost` with five nested tab graphs plus a global section; the bottom bar reads the current graph.
- **iOS:**
  - an `AppDestination: Hashable` enum (one case per pushed screen with its arguments) and a `TabRouter` (`@Observable`: `selected`,
    `paths: [AppTab: [AppDestination]]`, the trail via the Kotlin `CrossTabTrail`);
  - one `navigationDestination(for: AppDestination.self)` per tab stack;
  - an exhaustive `switch` from `AppDestination` to `ScreenId`.

---

## 4. Cross-tab navigation rules
1. **Open screen S from tab T:** let `C = owner(S)`.
   - If `C == T`: push onto T's stack (no hop).
   - If `C ≠ T`: switch to C, restoring its stack untouched. Push S, and record the hop `(origin = T, tab = C, depth = depth(C) after push)`.
2. **Same screen on top:** if C's top entry is the same `ScreenId` with the same arguments, don't push a duplicate. Record the hop (so Back still
   returns to T) and show that entry.
3. **Back:**
   - Pop C's stack.
   - If the popped entry is the latest hop's start (`newDepth < hop.depth`), switch to `hop.origin` and drop the hop.
   - Back at a tab root follows the platform: Android goes to the Home tab, then exits; iOS has no Back at a root.
4. **User taps another tab:** restore that tab's stack; **clear the trail** (D2). From then on Back stays inside each tab's own history.
   Re-entering a tab never replays an old hop.
5. **Modal/global (Settings, Notifications, Auth, forms, pickers):** never change the selection or touch a stack. Dismiss returns to the exact
   entry underneath. A modal that opens a tab destination (a search result, an alert's stock) **dismisses first**, then applies rule 1 from the
   tab underneath.
6. **Programmatic tab jumps** (Home "See all" → Learn, Brief → Watchlist; today's `openTab` / `selectedTab = …`) are treated as user tab
   switches: restore that tab and clear the trail. They're not hops.
7. **Duplicates across tabs are allowed and independent.** Company Details(AAPL) may exist in the Markets stack twice only through real
   navigation; each entry has its own presenter (Android per-entry ViewModel; iOS per-destination model).

### State transitions (example: Watchlist → Company Details → Learn term)
| Step | Action | Visible | Highlight | Stacks (H / M / P / W / L) | Trail |
|---|---|---|---|---|---|
| 0 | start | Watchlist | W | H[Home] M[Markets, Screener, Results] P[Portfolio] W[Watchlist] L[Learn] | — |
| 1 | tap AAPL row | Company Details | M | M[…, Results, **CD AAPL**] others unchanged | (W→M @4) |
| 2 | tap "P/E ⓘ → Learn" | Learn term P/E | L | L[Learn, **Term P/E**] | (W→M @4), (M→L @2) |
| 3 | Back | Company Details | M | L[Learn] | (W→M @4) |
| 4 | Back | Watchlist | W | M[Markets, Screener, Results] | — |
| 4′ | (instead of 4) tap Markets tab = reselect | Markets root | M | M[Markets] | — (D3) |

---

## 5. Back-stack behaviour by platform
- **Android:**
  - one `NavController`; a nested graph per tab (`HomeGraph`, `MarketsGraph`, …; each route class registered in exactly **one** graph, its
    canonical owner) plus global routes at the top level;
  - tab switch: `navigate(tabGraph) { popUpTo(startGraph) { saveState = true }; launchSingleTop = true; restoreState = true }`, **including
    Home** (fixes the lost Home stack);
  - a cross-tab open is that tab switch followed by `navigate(S)`;
  - Back: one `BackHandler` at the `AppNavigation` level consults `CrossTabTrail.popped`. Predictive Back keeps working for in-stack pops.
- **iOS:**
  - `TabView(selection: router.selectedBinding)` with five `NavigationStack(path: router.binding(tab))`;
  - Back (button or swipe) changes the path. `onChange(of: paths[tab])` calls `CrossTabTrail.popped` and switches the tab when a hop ends, so
    swipe-back is covered too;
  - the outer `NavigationStack` is removed.

---

## 6. Bottom navigation visibility policy
- **Visible** on every tab root and every pushed tab destination (rows 1–34), on both platforms.
  - On iOS this is a **change**: today every push hides the tab bar.
  - It shows the canonical selection and allows switching (R7).
- **Hidden** on: Auth, app lock, Settings (+ its pushes), Notifications, Company Search and the two pickers, the add/edit transaction form, and
  in-screen sheets.
  - Android Settings changes from "tab bar visible, nothing selected" to modal.
  - Android Add transaction changes from push (tab bar visible) to modal.
- **No duplicate bars:** pushed screens keep their own top bar (back + title). Only landing pages get the new shared landing bar (§7).
  - Company Details and the other screens that draw their own header keep it.
- **The MOCK "Sample data" strip** stays directly above the tab bar where the tab bar is visible, and at the bottom elsewhere.
  - Android: `StockSampleDataBanner` logic is unchanged.
  - iOS: `bannerPlacement.tabBarVisible` must become "tab bar visible", **not** "TabView on screen" — with per-tab stacks the `TabView` is always
    on screen.

---

## 7. Shared top app bar (landing pages)
- **Components:**
  - Compose: `StockLandingTopBar` (in the design system, next to `StockStepsTopBar`);
  - SwiftUI: `StockLandingTopBar` view;
  - copy and spoken labels: core `TopBarDisplay`.
- **Layout:**
  - left: title (Home: "StockSteps" brand mark, D7; other tabs: the tab name as a heading);
  - right: optional Search, then **Notifications** (bell), then **Settings** (gear);
  - 48 dp / 44 pt targets, theme tokens only (light/dark), spoken labels "Search stocks", "Notifications" and "Settings".
- **Large text:**
  - the title wraps (never truncated, `StockHeadingFit`);
  - the icons keep fixed sizes;
  - at ≥ 1.75× / AX3 the title moves above the icon row if they can't share a line (one `StockLayout` decision, tested in
    `ResponsiveLayoutRulesTest`).
- **Per page** (audit §1.3 → no duplicate titles or icons):
  - Home: brand + Search + Notifications + Settings. This replaces `PersonalHomeHeader` / `HomeScreen.header`; the person icon becomes a gear
    (D6).
  - Markets: "Markets" + Search + Notifications + Settings. The in-page title and search icon are removed.
  - Portfolio: "Portfolio" + Notifications + Settings. The account menu stays in the page.
  - Watchlist: "Watchlist" + Search + Notifications + Settings. The count and list menu stay in the page header, without a second title.
  - Learn: "Learn" + Notifications + Settings. Topic search stays in the page (it searches terms, not companies).
- **Actions:**
  - Notifications opens the Alerts screen (all alerts + history) as a **modal** (D5). No unread badge (no contract).
  - Settings opens Settings as a modal.
  - Both return to the exact screen on dismiss.
  - Guests see Notifications with the existing signed-out Alerts state.

---

## 8. Platform migration strategy (incremental, screens unchanged)
- **Android (feature Scenes and Screens are not touched; only `AppNavigation.kt` and route lists change):**
  1. Add the core policy and a `ScreenId` per route, with a coverage test.
  2. Wrap the routes into five `navigation<HomeGraph>(startDestination = DiscoveryRoute) { … }` blocks plus top-level global routes. Route
     classes stay the same, so deep links and argument types are unchanged.
  3. Replace `openTab` with a `TabNavigator` helper (tab switch / cross-tab open / back / reselect), and replace the scattered
     `navController.navigate(CompanyDetailsRoute…)` lambdas with `nav.open(route)`.
  4. Bottom bar selection from the current entry's graph. The hand-made `selected` rules and `PortfolioTabRoutes` are retired.
  5. Settings, Auth, Search, pickers and the transaction form become global routes without the tab bar.
  6. Restore Home with `restoreState`.
  7. Save the trail with `rememberSaveable` (serializable list) so it survives process death with the stacks.
- **iOS:**
  1. Add `AppDestination` + `TabRouter`. Move `AppScene`'s 17 flag destinations into the enum (one builder per tab stack).
     - Feature scenes keep their callbacks; `AppScene` maps them to `router.open(.companyDetails(symbol))`.
     - The `navigationDestination` builder rules in `CLAUDE.md` still apply (no work in `init`).
  2. Move `TabView` outside, with one `NavigationStack` per tab. Remove the outer stack.
  3. Nested destinations stay where they are, **except** where they cross tabs:
     - Company Details → Learn term / Guided research (→ Learn);
     - Financials/Valuation → Learn term (→ Learn);
     - Company Details → Compare / Earnings stay Markets, but move to the router so `.companyDetails` can be pushed twice.
     These become `router.open(...)`.
  4. Audit every pushed scene's `.task`/`onAppear` for work that would repeat on tab switch:
     - e.g. `recordViewedCompany` → once per model instance;
     - presenters already guard their loads (`activate()` / `start: false` pattern).
  5. Optional (D8): `@SceneStorage` of each tab's `[AppDestination]` (Codable) plus the selected tab for restoration. Recommended deferred.
- **Order:** Android first (smaller change, same NavController), then iOS. The core policy ships first, so both platforms obey the same tested
  rules.

---

## 9. Edge-case matrix (proposed behaviour)
Stacks shown only where they change. "Trail" lists hops (origin→tab).

| # | Scenario | Visible screen | Highlight | Stacks after | Back goes to | Restoration |
|---|---|---|---|---|---|---|
| 1 | Watchlist → Company Details → Back | CD, then Watchlist | M, then W | M[…, CD] then M[…] restored; W untouched | Watchlist | Watchlist scroll/filters intact |
| 2 | Home → CD → tap Markets tab → Back | CD → Markets root → (Back) Home tab | M → M → H | M[Markets] (reselect pops to root, D3); trail cleared | Android: Home tab; iOS: none | Home stack restored |
| 3 | Markets → Screener → Portfolio → Markets | Screener results | M | M[Markets, Screener]; P untouched | Markets root | Screener filters, results and scroll kept; no request |
| 4 | Learn → CD → Back | CD → Learn | M → L | M[…, CD] → M[…] | Learn (where it was) | Learn scroll kept |
| 5 | Portfolio → Search → CD | Search (modal) → CD | P (under modal) → M | M[…, CD], trail (P→M) | Portfolio (Search not reopened) | — |
| 6 | Same CD from two entry points (Watchlist, then Home) | CD | M | 1st: M[…, CD]; switching to Home clears trail; Home → CD: top is the same CD → no duplicate (rule 2), hop (H→M) | Home | one presenter for that entry |
| 7 | Tap selected tab repeatedly | 1st: tab root; 2nd: scroll to top; further: no-op | same | tab stack = [root] | — | — |
| 8 | Deep link to CD (cold start) | CD | M | M[Markets, CD]; trail (H→M) | Home (Android); iOS: Back to Markets root | — |
| 9 | Push opens Earnings event (warm) | dismiss any modal, then Event | M | M[…, Event], trail (current→M) | where the user was | Markets content below intact |
| 10 | Guest opens Practice | Auth modal | unchanged | none | Close → previous screen | after sign-in: back to the opener (resuming Practice: optional, D10) |
| 11 | Sign-in / sign-out / session expired | sign-out: Auth (Android) / root gate (iOS) | — | all five stacks + trail **reset**; "Sign in again" signs out first (E.2) | — | no data of the previous user reachable |
| 12 | Process death (Android) / app termination (iOS) | Android: same tab + stacks + scroll; iOS: Home (D8) | same / Home | Android saved; iOS fresh | as before | presenters reload once (cache rules unchanged) |
| 13 | Rotation / AX5 | unchanged screen | unchanged | unchanged | unchanged | Android `rememberSaveable`; landing bar wraps (§7) |
| 14 | Settings from each tab | Settings modal | unchanged (hidden) | unchanged | exact screen on that tab | no reload |
| 15 | Notifications from each tab | Alerts modal → tap a stock → dismiss, then CD | → M | M[…, CD], trail (T→M) | tab T's screen | — |

---

## 10. Risks and mitigations
| # | Risk | Where | Mitigation |
|---|---|---|---|
| R-1 | Restoring Home now keeps its stack; Home presenters assume a fresh root | Android `restoreState = tab != HOME` | Device check of Home after restore; the Home scene uses app-level account presenters (no refetch) |
| R-2 | Route registered in the wrong graph → wrong highlight, or `navigate` resolves the wrong destination | Android nested graphs | Each route in exactly one graph; a test that every route maps to one `ScreenId` and graph; settings-scope copies get their own route types |
| R-3 | `.task` reruns on tab switch → extra requests (e.g. `recordViewedCompany`) | iOS per-tab stacks | Audit pushed scenes; once-per-model guards; count requests on MOCK (server request counter or `URLProtocol` stub in UI tests) |
| R-4 | Cross-tab Back via `onChange(path)` animates pop, then tab switch | iOS | Accept (it reads as "back"); owner visual check in 5D.5.6D |
| R-5 | Flag destinations can't push the same screen twice | iOS today | Typed `AppDestination` path |
| R-6 | Shared `ComparisonResearch`/`Ai` reuse the Compare ViewModel via `getBackStackEntry<ComparisonRoute>()` | Android | All three in the Markets graph; behaviour unchanged |
| R-7 | `PortfolioEntryRoute.onDone` decides feedback from `previousBackStackEntry` (D3) | Android | Keep the opener check when it becomes modal (opener = the entry underneath) |
| R-8 | MOCK banner placement relies on the TabView appearing/disappearing | iOS | Drive it from `TabRouter.tabBarVisible` |
| R-9 | Session expiry / sign-out leaving old stacks | both | `router.reset()` / graph reset, and a test |
| R-10 | Comparison picker, Company Search and Company Details regressions | both | Regression matrix in 5D.5.6D; screens untouched |
| R-11 | Portfolio feedback shown once (`portfolioFeedback`) and form close rules | both | Unchanged code paths; verify with the D3 checks |
| R-12 | Push link arriving while a modal is open | both | Policy rule 5: dismiss first, then open |

No provider-call change: tab switches only restore entries. The server and API are untouched.

---

## 11. Implementation phases
- **5D.5.6B — Shared policy and landing top bar (both platforms, no stack migration)** — implemented 2026-10-15, report `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6B.md`. Notifications and Settings keep their current presentation until the Phase C hosts make them global modals:
  - core `navigation/`: `AppTab`, `ScreenId`, `NavigationPolicy`, `CrossTabTrail`, `TopBarDisplay`, with tests (every transition in §4 and
    §9);
  - Android route→`ScreenId` list + coverage test; iOS `ScreenId` mapping;
  - `StockLandingTopBar` (Compose + SwiftUI) on the five landing pages; Notifications (Alerts modal) and Settings (modal) entry points;
  - Android Settings as a modal route.
  - Verify: device screenshots of all five landing pages (light, dark, 1.3×, 2.0×, AX XL, AX5).
- **5D.5.6C1 — Android stacks** — implemented 2026-10-15, report `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6C1.md`. Added: `TabNavigation.sync` (host adapter), `NotificationsRoute` and `SettingsRemindersRoute` (`ScreenId.SETTINGS_EARNINGS_REMINDERS`):
  - nested tab graphs, `TabNavigator`, canonical cross-tab open, trail Back, reselect (root, then scroll to top);
  - global modals (Search, pickers, transaction form, Auth) without the tab bar;
  - Home restore; process-death check.
  - Focused diff (navigation) per `AI_REVIEW_WORKFLOW.md` §3.4.
- **5D.5.6C2 — iOS stacks** — implemented 2026-10-15, report `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6C2.md` (router-level trail included; nested cross-tab links stay for C3):
  - `AppDestination` + `TabRouter`, per-tab `NavigationStack`s, tab bar visible on pushes, trail Back via path observation, reselect;
  - migrate the 17 `AppScene` destinations; banner placement.
- **5D.5.6C3 — iOS cross-tab nested links and state** — implemented 2026-10-16, report `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6C3.md` (all nested pushes on the router; honest back labels; reused-top return; lifecycle audit):
  - Company Details/Financials/Valuation → Learn term and Guided research via the router;
  - `.task` audit;
  - optional `@SceneStorage` (if D8 = yes).
- **5D.5.6C4 — Portfolio nested tabs (§13), both platforms** — implemented 2026-10-16, report `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6C4.md` (Practice is a landing section, not a pushed page; core `PortfolioSections`; pinned tabs; state per section; children return to their section):
  - My Portfolio / Practice as genuine inner tabs of the Portfolio tab;
  - the selection, scroll and filters are preserved across tab switches;
  - pushed Portfolio and Practice screens return to their inner tab;
  - guests keep separate experiences; no reloads.
- **5D.5.6D — Final QA:**
  - the §9 matrix on both platforms;
  - TalkBack/VoiceOver checklist (owner): tab announcements and the selected state;
  - request counts on MOCK for tab switches (expect zero new requests);
  - large text, dark, rotation;
  - Company Search, Company Details, the Comparison picker, guest mode and the MOCK banner;
  - contact sheets + review package.

Each phase is independently shippable: B changes no Back behaviour; C1 and C2 can ship on one platform while the other keeps today's behaviour
(the policy tests keep the rules aligned).

---

## 12. Decisions requiring owner approval
| ID | Decision | Recommendation |
|---|---|---|
| D1 | Canonical owners of ambiguous screens | Daily Brief + history → **Home**; Earnings digest → **Markets**; Practice + order → **Portfolio**; Guided research → **Learn**; Stock alerts → **Watchlist**; Earnings reminders → **Watchlist** (from tabs) and inside Settings when opened from Settings |
| D1a | Company Search | **Modal** above the current tab (as today on both platforms); its result opens CD in Markets. Alternative: push onto Markets (switches tab while typing; not recommended) |
| D2 | Trail after a manual tab switch | **Cleared**: Back then stays within each tab's own history |
| D3 | Reselecting the highlighted tab on a cross-tab screen | **Pop to the tab root** (standard reselect) and clear the trail |
| D4 | Tab bar on pushed iOS screens | **Visible** (change from today); hidden on modals only (§6) |
| D5 | Notifications destination | The existing **Alerts screen (all alerts + history)** as a modal labelled "Notifications"; no unread badge |
| D6 | Settings icon on landing bars | **Gear** "Settings" on all five (Home's person icon replaced) |
| D7 | Home landing title | **"StockSteps" brand mark** on Home; tab name on the other four |
| D8 | iOS restoration after termination | **Deferred** (restart on Home as today); Android keeps process-death restoration |
| D9 | Contextual learning (5D.5.3 rule "term pages open on the same stack") | **Superseded**: Learn terms open on the Learn stack with a hop, so Back still returns to the stock |
| D10 | Resume a gated feature after sign-in (guest → Practice) | Out of scope; keep returning to the opener |
| D11 | Android Add transaction | **Modal form** (matches iOS) instead of a push with the tab bar |
| D12 | Reselect at root → scroll to top | **Yes** on both platforms |

---

## 13. Portfolio nested tabs (owner requirement, added 2026-10-15 with 5D.5.6B)
The My Portfolio / Practice control on the Portfolio landing page becomes a pair of **genuine nested tabs** inside the canonical Portfolio tab.
Today on Android it is a `StockSegmentedControl` whose Practice segment *pushes* `PracticeRoute`; on iOS it opens Practice through `openPractice`.

- **Ownership:** both inner tabs belong to the Portfolio bottom tab (`ScreenId.PORTFOLIO` with an inner tab `MY_PORTFOLIO` / `PRACTICE`).
  - Practice is no longer a pushed screen from the landing page. `ScreenId.PRACTICE` stays for links from other tabs (Learn, Home, Company
    Details), which open the Portfolio tab with the Practice inner tab selected (canonical rule 1).
- **State:** the selected inner tab is part of the Portfolio tab state.
  - Android: `rememberSaveable` on the Portfolio graph's root entry.
  - iOS: `TabRouter.portfolioInner`.

  It survives primary-tab switches, and each inner tab keeps its own scroll position, filters and loaded data (two lazy-list states; the
  presenters are app-level and already loaded, so no reload).
- **Returning:** Holding Details, Transactions and Transaction details return to My Portfolio. Practice Order and Practice holdings return to
  Practice. Each pushed entry records its inner tab, and Back restores it.
- **Guests:**
  - My Portfolio keeps its signed-out state (sign-in prompt, samples in MOCK);
  - Practice keeps its own gate (it needs an account: the server keeps the simulated ledger);
  - neither experience leaks into the other.
- **Loads:** switching inner tabs never refetches. Practice loads once, when it is first shown, as today.
- **Schedule:** a focused follow-up during the Portfolio part of the stack migration: **5D.5.6C4** (after C1/C2). It is not part of Phase B.

## 14. Cross-tab trail: state model and invariants (contract for Phase C)
**Model (pure; `core/.../navigation/TabNavigationModel.kt`, added in 5D.5.6B as a tested reference, not wired into any host):**
- `stacks: Map<AppTab, List<Entry>>` — every tab always has its root entry at index 0.
- `selected: AppTab` — the visible tab.
- `hops: List<Hop>` — LIFO. A `Hop(origin, tab, depth)` means "the entry at `stacks[tab][depth - 1]` was opened from `origin`".
- An `Entry(screen, key)` holds a `ScreenId` plus an argument key (e.g. a symbol). Platforms keep their real stacks and follow the same
  transitions.

**Transitions:**
| Event | Rule |
|---|---|
| `open(screen, key)` (tab destination) | owner `C`. If `C == selected`: push (no duplicate of an identical top). Else: select `C`, push (or reuse an identical top), and add `Hop(previous selected, C, depth)` |
| `open` of a modal/global | no stack or selection change (the host presents it) |
| `back()` | if the selected stack has > 1 entry: pop. If the popped depth ends the latest hop on this tab, select `hop.origin` and drop the hop. At a root: no change (the platform decides: Android goes to Home, then exits) |
| `selectTab(tab)` (user or programmatic jump) | select `tab`; **clear all hops** (D2) |
| `reselect()` (tap the selected tab) | stack deeper than its root → pop to root; at the root → scroll-to-top signal. **Clear all hops** (D3) |
| `reset()` (sign-out, session ended) | every stack back to its root, `selected = HOME`, no hops |
| `deepLink(screen, key)` | `open` from the current tab; on cold start the current tab is Home, so Back returns to Home (Android) |

**Invariants (each is a unit test):**
1. **I1 — No foreign damage:** `open`, `back`, `selectTab` and `reselect` change at most the stack of the tab they act on. Every other tab's stack
   is identical before and after. A cross-tab `open` appends to the canonical tab only, and the origin tab is untouched.
2. **I2 — Roots:** every tab's stack is non-empty and starts with that tab's root screen.
3. **I3 — Ownership:** every non-root entry in `stacks[t]` has `parentTab(screen) == t`.
4. **I4 — Hop validity:** for every hop, `depth <= stacks[hop.tab].size`. Hops are removed when their entry is popped (by `back` or
   `reselect`), so no hop outlives its entry.
5. **I5 — Back round trip:** for any state S, `open(x)` on a tab destination followed by `back()` returns S, except that an identical top
   (reuse) yields S with the selection restored.
6. **I6 — Repeated navigation:** opening the same `(screen, key)` twice in a row never creates two identical adjacent entries.
7. **I7 — Nested hops:** W → CD (Markets) → Term (Learn): `back` twice returns to Watchlist; Markets and Learn are back to their previous
   stacks.
8. **I8 — Manual switch:** `selectTab` clears hops. `back` afterwards stays in the current tab.
9. **I9 — Reselect:** pop to root, and the hops for that tab are gone; other tabs are unchanged (I1).
10. **I10 — Modal neutrality:** modal/global opens don't change `stacks`, `selected` or `hops`.
11. **I11 — Reset:** after `reset()`, all stacks are roots and there are no hops.

**Platform notes:**
- **Android system Back** maps to `back()`. Predictive Back animates in-stack pops natively, and a hop end switches tabs after the pop.
- **iOS Back button and swipe-back** both change the tab's path. The router derives `back()` from the path shrinking, and calls it once per
  removed entry.
- **Process restoration:**
  - Android: the hops are saved with the NavController state (`rememberSaveable`); a hop whose entry no longer exists after restoration is
    dropped (I4).
  - iOS restoration is deferred (D8).

## 15. Phase C test scenarios (defined before implementation)
Pure model (core): I1–I11 plus the §9 matrix rows 1–8, 11, 14 and 15 as reducer sequences.

Android (`app:shared` host tests with a `TestNavHostController`):
- tab switch keeps saved stacks;
- cross-tab open selects the canonical tab;
- Back across a hop;
- reselect;
- process recreation of the hops.

iOS (unit tests of `TabRouter`):
- path shrink → hop end;
- swipe-back equivalence;
- reselect.

Devices: the §9 matrix.
