# Phase 5D.5.6C3 — iOS cross-tab navigation, Back behaviour & lifecycle audit (KAN-10)

- **Status:** implemented and verified on the local MOCK server (iOS only). Approved by the owner; committed and pushed as "Finish iOS cross-tab navigation for Phase 5D.5.6C3 with every tab screen on the router, truthful back labels, reused-top Back, reselection from any depth and no repeated requests on tab switches".
- **Base:** `8fce9d1` (5D.5.6C2, pushed).
- **Plan:** `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6_PLAN.md` §4–§9, §14. Android C1 is the reference. Android code is unchanged.

## 1. What changed for the user (iOS)
- **Every tab screen opens on its canonical tab**, including the pages Company Details, Portfolio, Learn, Compare and News used to push
  themselves. For example:
  - Apple → P/E term and Apple → Guided research select **Learn**;
  - Financials and Valuation → a term select **Learn**;
  - Company News → News insight stays on **Markets**;
  - Portfolio → Holding, Transactions and Insights stay on **Portfolio**.
- **Back follows the real history across tabs**, with the button and swipe-back alike:
  - Watchlist → Apple (Markets) → P/E term (Learn) → Back → Apple → Back → Watchlist;
  - the Markets and Learn stacks are left as they were.
- **The back button names where Back goes:**
  - "‹ Watchlist" on Apple opened from Watchlist;
  - "‹ Home" on Apple opened from Home;
  - "‹ AAPL" on a term or Guided research opened from Apple.

  It's the native button, so swipe-back, VoiceOver and Dynamic Type are unchanged.
- **Reselecting a tab pops to its root from any depth**, including screens that used to be pushed outside the router (Financials, Holding
  details, Learn topic → term).
- **Reused top:** opening the company already on top of Markets from another tab shows that same page (no duplicate). Back returns to the tab
  you came from, and Markets keeps the page.
- **No repeated network work on tab switches:** a tab round trip with Apple or Financials open made **0 requests** (measured, §6).

## 2. Nested destination inventory (C2 left 26 nested `navigationDestination`s in feature scenes)
| From | To | Class | C3 |
|---|---|---|---|
| Company Details | Financials, Valuation, News, Why it moved | tab push (Markets) | → router (`.companyFinancials/.companyValuation/.companyNews/.stockMovement`) |
| Company Details | Earnings details | tab push (Markets) | → router `.earningsDetails` (the app's EarningsModel, one shared client) |
| Company Details | Compare | tab push (Markets) | → router `.compare` (the shared selection) |
| Company Details | Learn term | **cross-tab (Learn)** | → router `.learn(.term)` |
| Company Details | Guided research | **cross-tab (Learn)** | → router `.research` |
| Company Financials, Valuation | Learn term | **cross-tab (Learn)** | → router (from the explanation sheet after it closes) |
| Company News | News insight, Why it moved | tab push (Markets) | → router `.newsInsight/.stockMovement` |
| Markets | Why it moved | tab push (Markets) | → router `.stockMovement` |
| Compare | Comparison research, Ask AI | tab push (Markets) | → router `.comparisonResearch/.comparisonAi` |
| Comparison research | Ask AI (question) | tab push (Markets) | → router `.comparisonAi(target)` |
| Portfolio | Insights, Transactions, Transaction, Holding | tab push (Portfolio) | → router `.portfolioInsights/.portfolioTransactions/.portfolioTransaction/.holdingDetails` |
| Transactions | Transaction details | tab push (Portfolio) | → router `.portfolioTransaction` |
| Learn topics | topic, term | tab push (Learn) | → router `.learn(.topics/.term)` |
| Learn term | next term, all topics | tab push (Learn) | → router `.learn(...)` |
| Settings sheet | Reminders, Digest settings | modal-local | **kept** (inside the Settings sheet's own stack) |
| Global digest-settings sheet | Reminders | modal-local | **kept** |
| Discover filters sheet | category (`NavigationLink(value:)`), metric editor | modal-local | **kept** (the sheet has its own `NavigationStack`) |

**Result:**
- no nested push is left in any tab stack;
- `AppDestination` has 28 typed cases;
- a debug-build launch check asserts that every `ScreenKind.TAB_SCREEN` id has a destination;
- the exhaustive `screen` switch keeps the mapping complete at compile time.

## 3. Architecture decisions
- **One navigation system per tab stack.** Feature scenes push only through `TabRouter` (read from the environment). The old `@State` flags and
  their `navigationDestination`s are deleted, so tab selection, stack depth, Back and reselection can't disagree.
- **Shared state kept:**
  - `CompanyModelStore`: one company model and details client per symbol, shared by Company Details and its sub-pages. It's released when its
    symbol is on no stack (`router.symbolsInUse` on each `revision`);
  - the app-level `PortfolioViewModel`: one subscription for Portfolio, Holding, Transactions and Transaction details;
  - the app's `EarningsModel`: Company Details' next-earnings request and Earnings details share one client;
  - the app's `ScreenerModel`: Compare, research and AI.
- **Back (button or swipe) → core `TabNavigation.back`** when one pop removes the latest hop's entry. It returns to the hop's origin.
  - For a reused hop, the pre-existing entry is restored on the next run-loop turn, after the tab switch. SwiftUI ignores a path restored
    inside its own pop; first device run: FAIL, fixed, re-run PASS.
  - The model already supported reused hops (`Hop.reused`), so no shared-contract change was needed. A core test was added for the iOS chain
    (reused top + nested hop).
- **Back labels:** a tab root's (hidden) navigation title names its first pushed screen's back button. While that first screen is a hop's
  entry, the root's title is the origin's visible screen (`rootTitle`), giving the native "‹ Watchlist", "‹ AAPL" etc. A deeper hop entry
  shows the chevron only (`toolbarRole(.editor)`), so no misleading name appears. Native buttons keep swipe-back.
- **Lifecycle rule:** work that fetches runs once per screen (`taskOnce`) and again only when its id changes, never because a view came back on
  screen. Pull-to-refresh and Retry stay explicit.

## 4. Lifecycle audit (tab roots and pushed screens)
| Screen | Hook | Finding | Change |
|---|---|---|---|
| Company Details | `.task` | load guarded; next earnings guarded; **viewed-company record ran on every reappearance** | recorded once per visit (`recordedView`) |
| Daily Brief | `.task(id:) open` | re-fetched the brief / personal brief on each reappearance | `taskOnce(id:)` |
| Previous briefs | `.task loadHistory` | refetched | `taskOnce` |
| Earnings reminders | `.task refreshReminders` | refetched (permission check is local) | permission check per appearance; reminders `taskOnce` |
| Comparison research | `.task reload` | refetched sessions | `taskOnce` |
| Ask AI | `.task refreshUsage` | refetched usage | `taskOnce` |
| Practice | `.onAppear refresh` | refreshed on every tab switch | refresh when shown and when it becomes the top again (back from an order), not on tab switches |
| Markets, News, Financials, Valuation, Movement, Insight, Screener, Earnings, Digest, Learn | `start()/activate()` | already once-only guards | none |
| Home, Portfolio, Watchlist, Learn roots | `onAppear` connect / visible | unchanged roots (they already reappeared on tab switches before C2) | none |
| Error banners (`PortfolioStatusBanner`) | `onAppear` announce | announces again when the screen reappears | none (accessibility only; no request) |

## 5. Files changed
- **iOS:**
  - `TabRouter.swift`: 12 new destination cases, `backTitle`, `rootTitle`, `hidesBackTitle`, reused-hop Back, `revision`/`symbolsInUse`,
    `open(_:afterSheet:)`, `CompanyModelStore`, `taskOnce`, debug coverage check;
  - `AppScene.swift`: destinations, shared models, root titles, model release;
  - `CompanyDetailsScene.swift`, `CompanyFinancialsScene.swift`, `CompanyValuationScene.swift`, `CompanyNewsViews.swift`, `MarketsScene.swift`,
    `LearnViews.swift`, `PortfolioScene.swift`, `PortfolioTransactionsScreen.swift`, `ScreenerScenes.swift`, `ComparisonResearchViews.swift`
    (nested pushes → router);
  - `DailyBriefScenes.swift`, `EarningsReminderViews.swift`, `ComparisonAiViews.swift`, `PracticeScenes.swift` (lifecycle).
- **Core:** `TabNavigationModelTest.kt` (+1 test). No production Kotlin change.
- **Docs:** this report, `docs/reviews/phase5d_5_6c3/`, the plan, the handoff and the status.

## 6. Device QA (iPhone 16 simulator, guest, local MOCK; requests counted)
**Request counting:**
- a throwaway counting proxy (scratchpad only, not in the repo) on 127.0.0.1:8082 in front of a MOCK server on :8083;
- the app was built with `STOCKSTEPS_MOCK_BACKEND_URL=http://127.0.0.1:8082` for this run.

**Port 8081 is busy:** another process from this checkout (pid 492, started 07:33 today) was serving :8081, so it was left untouched and not
used.

| # | Scenario | Result | Evidence |
|---|---|---|---|
| A | Watchlist → Apple (Markets, "‹ Watchlist") → P/E term (Learn, "‹ AAPL") → Back → Apple (Markets, same scroll) → swipe-back → Watchlist | PASS | `ios-c3-a-*` |
| B | Apple → P/E term: Learn selected, tab bar visible, Back → Apple | PASS | `ios-c3-a-term` |
| C | Apple → Guided research: Learn selected, "‹ AAPL"; Back → Apple | PASS | `ios-c3-c-*` |
| D | Markets → Apple → Financials (formerly nested) → tap Markets → Markets root | PASS | `ios-c3-d-reselect` |
| D | Learn → topic → term → tap Learn → Learn root | PASS | `ios-c3-learn-*` |
| D | Portfolio → Holding details → tap Portfolio; Watchlist child → tap Watchlist | BLOCKED (guest: no holdings, no Watchlist child pages) | — |
| E | Markets → Apple; Home → Apple again (reused, "‹ Home", Markets) → Back → Home; Markets still shows one Apple (one Back → root) | **FAIL, then PASS after the fix** (§3) | `ios-c3-e-*` |
| F | Settings sheet from Learn → Done → Learn root unchanged; Search sheets (Watchlist, Markets, Home) → result opens on Markets | PASS | `ios-c3-f-*` |
| G | Apple loaded → Portfolio → Markets: **0 requests**. Apple → Financials: **2 requests** (fundamentals, profile; first open). Financials → Portfolio → Markets: **0**. Reselect → root: **0** | PASS | proxy log counts |
| H | Light, dark, AX XL (Apple from Watchlist, native "‹ Watchlist"); bottom bar and MOCK banner on child screens | PASS | `ios-c3-watch-apple-*` |
| I | Signed-in Portfolio holdings, Watchlist rows, Practice, sign-out reset | BLOCKED (no approved signed-in test session) | — |
| — | VoiceOver | NOT RUN (Simulator) | — |

**Device state afterwards:**
- simulator light, Default size, guest;
- the app is reinstalled with the standard MOCK build (`localhost:8081`);
- the proxy and the :8083 server are stopped;
- pid 492 on :8081 is untouched.

## 7. Known limitations
- **No Swift unit-test target.** The router's adapter logic is verified on the simulator; the shared model (`TabNavigation`, incl. reused hops and
  `sync`) has unit tests.
- **Reused-top Back restores the existing page:** that page's view state (scroll) is rebuilt, but its data comes from the shared company model,
  so there is no reload.
- **Multi-level pops** (long-press back menu, popping past a hop in one step) end the hop by `sync` and stay in the tab. Only a single Back
  returns across tabs.
- **Back label at deeper hops** (the target stack already had pages): chevron only, no name.
- **Signed-in flows** (Holding details reselect, Watchlist children, Practice, sign-out) need the owner's session.

## 8. Tests and builds (sequential, final code)
| Command | Result |
|---|---|
| `./gradlew :core:jvmTest` | PASS 577/0 (+1 navigation test; `PortfolioSessionStateTest` passed) |
| `./gradlew :core:iosSimulatorArm64Test` | PASS 577/0 |
| `./gradlew :app:shared:iosSimulatorArm64Test` | PASS 266/0 |
| `./gradlew :app:shared:compileAndroidMain` (Android smoke) | PASS |
| `xcodebuild … STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081 build` | PASS (final code) |
| Swift router unit tests | NOT RUN (no iOS test target; simulator matrix instead) |
| `:app:shared:testAndroidHostTest`, `:server:test` | NOT RUN (no Kotlin production or server change) |

**Requirement → coverage:**
| Requirement | Covered by |
|---|---|
| Nested cross-tab | core `nestedHopsUnwindInOrder` + device A |
| Back chains | `watchlistToCompanyDetailsAndBack` + device A, C |
| Reused top | `repeatedNavigation…` and the new `reusedTopThenANestedHopUnwindsWithoutDamage` + device E |
| Manual switch | `manualTabSwitchClearsTheTrail` |
| Reselect from nested | device D |
| Duplicates | I6 tests + device E |
| Reset | `resetClearsEverything` (+ `onChange(user.id)` code) |
| Sync | `syncFollowsTheHostAndDropsStaleHops` |
| Modal neutrality | `modalAndGlobalScreensAreNeutral` + device F |

## 9. Recommendation
**APPROVE.** The C3 completion criteria are met on iOS:
- canonical ownership for every migrated route;
- cross-tab Back and swipe-back;
- reselect from formerly nested screens;
- reused-top return without duplicates (a defect found during QA was fixed and re-verified);
- stable modals;
- 0 requests on tab switches;
- all required suites green.

Signed-in checks (I, D for Holding/Watchlist) need the owner's session.
