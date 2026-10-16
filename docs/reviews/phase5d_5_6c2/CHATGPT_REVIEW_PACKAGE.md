# Review package: Phase 5D.5.6C2 (iOS independent navigation stacks, KAN-10)

## 1. Phase and Git baseline
- **Phase:** 5D.5.6C2 (iOS only) of the approved 5D.5.6 plan (D1–D12). Android C1 is the reference.
- **Base:** `2341662` "Add Android independent tab stacks for Phase 5D.5.6C1 …" (pushed).
- **Under review:** "Add iOS independent tab stacks for Phase 5D.5.6C2 with a NavigationStack per tab, typed destinations owned by the shared navigation policy, a cross-tab Back trail and a visible tab bar on pushed screens" (committed and pushed on the owner's approval).

## 2. Changes implemented
1. **Five independent native stacks** (`NavigationStack(path:)` per tab inside the `TabView`; the outer stack is removed). The tab bar stays
   visible on pushed screens.
2. **Typed `AppDestination`** (16 cases → core `ScreenId`) replaces 17 presentation flags. The tab comes only from `NavigationPolicy`.
3. **`TabRouter`** drives every step from core `TabNavigation` (+ `sync`):
   - canonical selection;
   - cross-tab Back to the origin tab (back button and swipe);
   - a manual switch clears the trail;
   - reselect → root → scroll to top;
   - reset on account change.
4. **Notifications** (all alerts) is an app-wide sheet with Done. The other sheets are unchanged.

## 3. Affected files
- **iOS:**
  - `TabRouter.swift` (new), `AppScene.swift` (rewritten);
  - one modifier each in `HomeScreen.swift`, `MarketsScene.swift`, `PortfolioScreen.swift`, `WatchListScene.swift`, `LearnViews.swift`.
- **Kotlin core/shared, Android, server:** none.
- **Docs:** the report, this package, the focused diff, the contact sheet, the plan, the handoff, the status.

## 4. Architectural decisions
- **Native SwiftUI navigation** (path-based stacks), no custom view stack.
- **One ownership source:** core `NavigationPolicy` through an exhaustive Swift `switch`.
- **Same contract as Android:** the native paths are the truth, synced into the shared model; Back is detected from the path shrinking.
- **Nested `isPresented` destinations inside feature scenes stay for C3** (no behaviour change where the owner matches the tab).

## 5. Test and build results
| Suite | Result |
|---|---|
| core JVM | PASS 576/0 |
| core iOS | PASS 576/0 |
| shared iOS | PASS 266/0 |
| Android compile smoke | PASS |
| xcodebuild (MOCK URL) | PASS |
| shared Android host, server | NOT RUN (no change) |
| Swift router unit tests | NOT RUN (no iOS test target) |

## 6. Android verification
Not applicable (Android unchanged; compile smoke PASS).

## 7. iOS verification
iPhone 16, guest, MOCK.

PASS:
- A: Financials restored, pixel-identical;
- B: Apple from Home → Markets selected; back button and swipe-back → Home;
- C: reselect to root and to top (Markets, Learn);
- D: Notifications, Settings, Search and form sheets without tabs, dismissal restores;
- E: Markets filter + scroll pixel-identical;
- F: dark and AX XL;
- Learn term on Learn;
- G: guest gate.

## 8. Known issues
- **Nested cross-tab links:** Company Details → Learn term/Research select Markets instead of Learn (C3).
- **Back label:** names the stack's previous page on cross-tab screens (C3).
- **Reselect with only nested pushes:** scrolls to top instead of popping (C3/C4).
- **Reused-top opens:** no return hop.
- **`.task` audit:** C3.

## 9. Unverified scenarios
- BLOCKED (guest; need the owner's session): Practice on Portfolio, Holding details, Watchlist rows.
- NOT RUN (no sign-in on devices; code review only): sign-out reset.
- NOT RUN: VoiceOver.

## 10. Deviations from the approved plan
- The C2 plan item "tab bar visible on pushes, trail via path observation" is implemented for router destinations. Nested destinations follow in
  C3 as planned.

## 11. Decisions requiring owner approval
1. Approve C2.
2. C3: should the back button on a cross-tab screen name the origin tab (custom back button) or keep the native label?
   Recommendation: native label + origin return (as now), to keep swipe-back.

## 12. Links
- Report: `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6C2.md`.
- Focused diff: `CHATGPT_FOCUSED_DIFF.md`.
- Contact sheet: `CHATGPT_UI_REVIEW_IOS.png`.
- Raw captures: `captures/` (git-ignored).

**Recommendation: APPROVE.**
