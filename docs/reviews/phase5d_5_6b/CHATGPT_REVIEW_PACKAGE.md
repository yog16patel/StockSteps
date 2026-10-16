# Review package: Phase 5D.5.6B (shared navigation policy & landing top bars)

## 1. Phase and Git baseline
- **Phase:** 5D.5.6B, the first implementation phase of the approved 5D.5.6A plan (D1–D12).
- **Base:** `912ec7f` "Add the Phase 5D.5.6A shared navigation and top app bar audit and plan …" (pushed).
- **Under review:** "Add the Phase 5D.5.6B shared navigation policy and landing top bars with a canonical parent tab per screen, a tested cross-tab trail model and Notifications and Settings on all five landing pages on Android and iOS" (committed and pushed on the owner's approval).

## 2. Changes implemented
1. **One shared landing top bar on all five tab landing pages** (Compose + SwiftUI):
   - title on the left (Home: the StockSteps brand mark);
   - the page's existing actions;
   - then Notifications (bell) and Settings (gear), always last.
2. **Notifications** opens the existing all-alerts history ("My Alerts", never stock-filtered); no unread badge.
   **Settings** opens the existing Settings.
   Both keep today's presentation, so Back and Done return to the exact landing page.
3. **Large text:** the icons move to their own row under the title when they can't share its line (shared `StockLayout.metricRowStacked`).
4. **Core navigation policy:** `AppTab`, `ScreenKind`, `ScreenId` (42 screens with canonical tab), `NavigationPolicy`, `LandingTopBarDisplay`,
   and a pure reference model of the Phase C cross-tab trail (`TabNavigation`; not wired).
5. **Plan updated:** Portfolio nested tabs (§13, scheduled as C4), trail state model and invariants I1–I11 (§14), Phase C test scenarios (§15).

## 3. Affected files
- **core:** `navigation/NavigationPolicy.kt`, `LandingTopBarDisplay.kt`, `TabNavigationModel.kt` + 2 test files.
- **shared:**
  - `StockLandingTopBar.kt` and `AppScreens.kt` (new); `StockIcons.kt`, `AppNavigation.kt`;
  - Home, Markets, Portfolio, Watchlist and Learn screens/scenes;
  - `AppScreensTest.kt` (new), `ResponsiveLayoutRulesTest.kt`.
- **iOS:** `DesignSystem/StockLandingTopBar.swift` (new), `AppScene`, `HomeScreen`, `MarketsScene`, `PortfolioScene`/`Screen`,
  `WatchListScene`, `LearnScene`/`Views`.
- **docs:** the plan, `NAVIGATION_TOP_BAR_PHASE5D_5_6B.md`, this package, handoff, status.
- **server:** none.

## 4. Architectural decisions
- **Ownership lives once in core** (`ScreenId(kind, tab)`). Android routes map to ids in `AppScreens.screenIdOf` (40/40 routes, tested). iOS gets
  its exhaustive mapping with typed destinations in C2.
- **Bottom-bar visibility and top-bar style are the approved Phase C targets**. They're tested in core but not applied, so Phase B changes no
  navigation behaviour.
- **The trail model is pure and immutable, with invariant tests.** These include a 4,000-step random walk checking that no operation changes
  another tab's stack (I1). Hosts adopt it in Phase C.
- **The bar is one component per platform** with copy from core. Page actions are passed in, so pages keep their own menus and state.

## 5. Test and build results
| Suite | Result |
|---|---|
| core JVM | PASS 575/0 |
| core iOS | PASS 575/0 |
| shared Android | PASS 268/0 |
| shared iOS | PASS 264/0 |
| assembleDebug (MOCK URL) | PASS |
| xcodebuild (MOCK URL) | PASS |
| server | NOT RUN (unchanged) |
| Compose UI tests | NOT RUN (no infrastructure) |

## 6. Android verification
emulator-5554, 360 dp, MOCK, signed-in test session.

PASS:
- the five landing bars at 1.0×/1.3×/2.0×, light and dark;
- Notifications → My Alerts and Back ×5;
- Settings and Back ×5;
- Home Search;
- tab switching.

The checks used UI-tree dumps with spoken labels "Search stocks", "Notifications" and "Settings".

## 7. iOS verification
iPhone 16, MOCK, guest.

PASS:
- the five landing bars in Default light/dark and AX XL/AX5 (icons stack under the title);
- Notifications → My Alerts and Back ×5;
- Settings sheet → Done ×5;
- Home and Markets search.

## 8. Known issues
- **Reference deviation:** no full-width search rows (they'd introduce new search scopes); existing search icons kept.
- **Home:** keeps the brand mark (D7) rather than a plain wordmark.
- **Android 2.0×:** the title grows little (existing typography), so the icons stay inline.

## 9. Unverified scenarios
- Android guest mode: NOT RUN (the emulator holds a signed-in session that wasn't signed out).
- TalkBack/VoiceOver: NOT RUN (owner).
- Request counting on tab switch: NOT RUN (the MOCK server has no request log); code review shows no new loads.

## 10. Deviations from the approved plan
- Notifications and Settings aren't yet global modals (plan §6). That requires the Phase C hosts and would change Back now, which the brief
  forbids.

## 11. Decisions requiring owner approval
1. Approve 5D.5.6B.
2. Should landing pages get the reference's full-width search row? That's a new search scope; recommendation: decide separately.

## 12. Links
- Report: `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6B.md`.
- Plan: `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6_PLAN.md`.
- Contact sheets: `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`.
- Raw captures: `captures/` (git-ignored).
