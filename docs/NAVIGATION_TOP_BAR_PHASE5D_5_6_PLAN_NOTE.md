# Phase 5D.5.6 — Shared Navigation & Top App Bar Refinement (planning note)

- **Status:** planning only (2026-10-15). Nothing here is implemented. Implementation waits for an owner-approved plan (5D.5.6A audit + plan,
  then B/C/D phases).
- **Ground rules:** no extra API calls; the Route/Scene/Screen boundary (CLAUDE.md) stays; no sixth tab; Android and iOS behave the same.

## 1. Current state (from the code, `458e6a2`)
- **Android:**
  - one `NavHost` (`presentation/AppNavigation.kt`). `openTab` navigates with `popUpTo(DiscoveryRoute) { saveState = true }`, plus
    `restoreState` for every tab except Home;
  - Home has one back stack shared with the other tabs, so a pushed screen belongs to whichever tab was active, not to a canonical parent.
- **iOS:**
  - one `NavigationStack` wraps the `TabView` (`AppScene.swift`), and pushed destinations cover the tab bar;
  - about 20 `navigationDestination` flags plus sheets (Settings, Auth, Search).
  - So there are no per-tab stacks: a tab switch doesn't keep each tab's pushed screen, and the tab bar hides on push.
- **Top bars:**
  - Home has its own header (StockSteps, Search, Alerts, Profile and settings);
  - the other landing pages use per-screen bars, and iOS hides the shared bar on most tabs (`stockStepsTopBar(... visible: ...)`).

## 2. Screen inventory and canonical parent tab (draft, for 5D.5.6A to confirm)
| Canonical tab | Screens (Android route / iOS destination) |
|---|---|
| **Home** | Discovery (Home), Daily Brief + Brief history, News insight, Guided research entry from Home cards (TBD) |
| **Markets** | Markets, Screener/Discover, Comparison (+ Comparison research, Comparison AI), Earnings calendar, Earnings event/results/details, Earnings digest, Stock movement |
| **Portfolio** | Portfolio, Portfolio entry/transaction/transactions, Holding details, Portfolio insights, Portfolio search, Practice (+ order, search) — Practice placement TBD |
| **Watchlist** | Watchlist, Alerts, Earnings reminders |
| **Learn** | Learn, Learn topics, Learn term, Guided research |
| **Shared (opens in the current tab's stack)** | Company Details (+ Financials, Valuation, News), Company Search |
| **Outside tabs (modal)** | Settings (+ digest/reminder settings), Notifications/Alerts inbox (decision: modal vs Watchlist), Auth (sign-in/create account), app lock |

Decision needed: shared screens such as Company Details either keep the tab they were opened from highlighted (recommended), or switch to a
canonical tab. Items 3 and 6 below depend on it.

## 3. Requirements → approach
1. **Inventory:** finalise the table above in 5D.5.6A from `*Route` classes and `AppScene` flags.
2. **Canonical parent:** a single shared core table (e.g. `NavigationDisplay.parentTab(route)`) used by both platforms. Tests cover every route.
3. **Highlight:** the tab bar selection comes from the active stack's tab, never from the last tapped entry point.
   Cross-tab links (Home → Learn term) switch tabs and push onto that tab's stack.
4. **Independent stacks:**
   - Android: Navigation Compose nested graphs per tab, with `saveState`/`restoreState` for all five (including Home);
   - iOS: one `NavigationStack(path:)` per tab inside the `TabView`, replacing the single outer stack and the flag-based destinations.
5. **Restore on tab switch:** keep the screen, scroll position and UI state.
   - Android: `rememberSaveable`/`LazyListState` per back-stack entry;
   - iOS: the per-tab path plus `@SceneStorage`/state kept in the tab's models.
   - No reload on return: presenters keep their StateFlow, with no new requests.
6. **Back history:** Back pops the current tab's stack and never jumps to another tab's history.
   - Android: system Back at a tab root goes to Home, then exits.
   - Cross-tab jumps are recorded so Back can return across tabs; the exact rule is an owner decision.
7. **Reselect the active tab:** pop to its root, then a second tap scrolls to the top.
8. **Reusable top app bar:** one `StockTopAppBar` (Compose) / `StockStepsTopBar` (SwiftUI) on all five landing pages.
   - Title on the left; Notifications and Settings actions on the right (Search where the page has it).
   - Shared copy and spoken labels come from a core `*Display`.
   - 48 dp/44 pt targets; large-text behaviour follows `StockNavigationLayout`.
9. **Non-tab screens:**
   - Settings and Auth are modal, above the tabs. They don't change tab selection, and closing them returns to the exact previous screen.
   - Notifications: a modal list (recommended), or push it onto the current stack.
   - Sheets and dialogs never enter a tab stack.
   - Deep links and push notifications open the canonical tab, then push.
10. **Consistency and safety:**
    - parity tests for the parent table;
    - UI tests for tab switch, reselect and Back;
    - checks that a returned screen makes no new requests (MockEngine counters);
    - process-death and `@SceneStorage` restore;
    - sign-out resets every stack (as `AuthRoute` popUpTo does today).

## 4. Risks
- **Migration size:** the iOS flag-based destinations are the biggest change. Do it tab by tab behind the existing scenes, without changing the
  screens (Route/Scene/Screen boundary).
- **State corruption:** a shared Company Details opened in two stacks needs per-entry presenters, so stacks never share mutable scene state.
- **Existing rules:** the MOCK banner placement (`bannerPlacement.tabBarVisible`), the app lock and the session-expired "Sign in again" flow must
  keep working.

## 5. Proposed phases (for approval)
- **5D.5.6A:** audit, final inventory, owner decisions (shared-screen highlight, Notifications placement, cross-tab Back).
- **5D.5.6B:** core parent table and the reusable top app bar.
- **5D.5.6C:** per-tab stacks, restore and reselect on Android, then iOS.
- **5D.5.6D:** verification (large text, dark, screen readers, no extra requests).
