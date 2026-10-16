# Review package: Phase 5D.5.6C1 (Android independent stacks & canonical tab selection)

## 1. Phase and Git baseline
- **Phase:** 5D.5.6C1 (Android only) of the approved 5D.5.6 plan (D1–D12).
- **Base:** `11b1e5b` "Add the Phase 5D.5.6B shared navigation policy and landing top bars …" (pushed).
- **Under review:** "Add Android independent tab stacks for Phase 5D.5.6C1 with canonical tab selection, a cross-tab Back trail from the shared navigation model, tab reselection and global Settings and Notifications screens" (committed and pushed on the owner's approval).

## 2. Changes implemented
1. **Five independent tab stacks** (Navigation's saved back stacks; one nested graph per tab). Switching tabs restores each tab's screen,
   arguments, ViewModels and scroll, Home included.
2. **The selected tab is the visible screen's canonical tab** (core `NavigationPolicy`); routes are registered in their policy tab's graph.
3. **Cross-tab trail from core `TabNavigation`:**
   - Back from a screen opened from another tab returns there, and nested hops unwind in order;
   - a manual tab switch clears the trail;
   - reselect pops to root, then scrolls to top;
   - Back at a non-Home root goes to Home, and at the Home root exits.
4. **Modal and global screens have no tab bar and never touch the stacks:** Search, the form, the pickers, Settings (+ its reminders/digest
   pages), Notifications (new global route, all alerts) and Auth. Opening a tab screen from them closes them first.
5. **The core contract gains `TabNavigation.sync`.** The host's real stack syncs into the model after every change, so pops performed anywhere
   can't leave stale hops.

## 3. Affected files
- **core:** `navigation/NavigationPolicy.kt`, `navigation/TabNavigationModel.kt` + tests.
- **shared:**
  - `TabNavigator.kt` (new), `components/AppBackHandler*.kt` (new);
  - `AppNavigation.kt`, `AppScreens.kt`, `AlertsScene.kt`, `EarningsReminderUi.kt`, `PortfolioRoute.kt`;
  - the five landing screens (scroll-to-top);
  - `AppScreensTest.kt`.
- **iOS, server:** none.
- **docs:** the report, this package, `CHATGPT_FOCUSED_DIFF.md`, the plan, the handoff, the status.

## 4. Architectural decisions
- **Native multiple back stacks over a custom navigator:** symmetric `popUpTo(currentGraph){inclusive; saveState}` + `restoreState` for every tab.
- **The core model decides, and the NavController is the source of truth for the visible stack**, synced back after every change. There is one
  saved trail (`rememberSaveable`) beside the NavController's own saved state.
- **One Back path:** `AppBackHandler` → `TabNavigator.back()`, registered after the NavHost. In-app Back buttons use the same function.
- **Graph membership is computed from the policy at registration** (`RouteTable` + `routeTab`). There is no second ownership table.

## 5. Test and build results
| Suite | Result |
|---|---|
| core JVM | **FAIL 576/1** (known intermittent `PortfolioSessionStateTest`; rerun alone PASS 3/0) |
| core iOS | PASS 576/0 |
| shared Android | PASS 270/0 |
| shared iOS | PASS 266/0 |
| assembleDebug | PASS |
| xcodebuild | PASS |
| server | NOT RUN (unchanged) |
| NavController integration tests | NOT RUN (no harness; device-verified) |

## 6. Android verification
emulator-5554, 360 dp, MOCK, the existing signed-in test session.

PASS:
- A: Financials restored;
- B: Watchlist → Apple → P/E term → Back → Back;
- I1: Markets' Discover kept through a cross-tab round trip;
- D2: manual switch clears the trail;
- C: reselect to root, then to top;
- root Back → Home → exit;
- modals: Search with keyboard, Settings, Notifications, the form;
- E: filter + scroll pixel-identical;
- Learn → Practice; Markets → Brief; Holding → Company;
- rapid taps; double tap;
- rotation and process death with an active trail;
- dark 2.0×.

## 7. iOS verification
Not applicable. iOS is unchanged (C2); `xcodebuild` passes.

## 8. Known issues
- **Search:** Back from a search result returns to the tab, not to Search (approved rule 5; behaviour change).
- **Settings:** now full screen without tabs (approved §6).

## 9. Unverified scenarios
- Android guest mode: NOT RUN (owner session not signed out).
- Back after sign-in: NOT RUN (no sign-in on devices).
- Comparison picker: NOT RUN (unchanged dialog).
- TalkBack: NOT RUN (owner).
- Sign-out `reset()`: code review only.

## 10. Deviations from the approved plan
- Added `NotificationsRoute` and `SettingsRemindersRoute` (+ `ScreenId.SETTINGS_EARNINGS_REMINDERS`), so Notifications and Settings reminders
  are real global routes as the plan's §2 table describes.
- Added `TabNavigation.sync` to the shared contract (tested) as the host adapter.

## 11. Decisions requiring owner approval
1. Approve 5D.5.6C1.
2. Accept the Search Back change (rule 5).

## 12. Links
- Report: `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6C1.md`.
- Focused diff: `CHATGPT_FOCUSED_DIFF.md`.
- Contact sheet: `CHATGPT_UI_REVIEW_ANDROID.png`.
- Raw captures: `captures/` (git-ignored).
