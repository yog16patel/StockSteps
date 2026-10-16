# Review package: Phase 5D.5.6C4 (Portfolio My Portfolio / Practice nested tabs, KAN-13)

## 1. Phase and Git baseline
- **Phase:** 5D.5.6C4 (Android and iOS) of the approved 5D.5.6 plan (§13).
- **Base:** `48f5a3a` (HEAD). The C3 navigation code is `d85eb3e`, pushed.
- **Under review:** "Add Portfolio nested tabs for Phase 5D.5.6C4 with My Portfolio and Practice switching in place on the Portfolio landing, state kept per section, children returning to their section and Practice loading once on Android and iOS" (committed and pushed on the owner's approval; the C4 files only, see §3).

## 2. Changes implemented
1. **Nested tabs.** My Portfolio | Practice are genuine nested tabs of the Portfolio landing (a tab group, selected state). Content switches in
   place, with no push and no Back. The tabs are pinned while the landing bar scrolls.
2. **Practice is a landing section**, no longer a pushed page. "Open Practice" from anywhere shows Portfolio with Practice selected.
3. **State per section** survives nested-tab and primary-tab switches: scroll, Practice's inner picker and the account selection.
4. **Children return to their section** (core `PortfolioSections.sectionFor`): Holding Details, Transactions and Insights → My Portfolio;
   Practice order → Practice.
5. **Reselect:**
   - from a child: the landing, section kept;
   - at the landing: the visible section scrolls to the top.
6. **Reset rules.** Sign-out and account switches reset to My Portfolio. Guests see the existing in-place Practice sign-in prompt.
7. **No duplicate loading.** Practice loads once; the resume and top-of-stack refreshes are removed.

## 3. Affected files
- **Core:** `navigation/PortfolioSection.kt` (new) and `PortfolioSectionTest.kt` (new, 3 tests).
- **Shared/Android:**
  - `StockSegmentedControl.kt` (`asTabs`, optional);
  - `PortfolioSectionTabs.kt` (new);
  - `PortfolioScreen.kt`, `PortfolioScene.kt`;
  - `PracticeScreens.kt`, `PracticeScenes.kt`;
  - `TabNavigator.kt`, `AppNavigation.kt`, `AppScreens.kt`;
  - `AppScreensTest.kt`.
- **iOS:**
  - `AppScene.swift`, `TabRouter.swift`;
  - `PortfolioScreen.swift`, `PortfolioScene.swift`, `PracticeScenes.swift`;
  - `StockSettingsComponents.swift`.
- **Server, APIs:** none.
- **Docs:** the report, this package, the focused diff, two contact sheets, the plan, the handoff, the status.
- **Not part of C4:** the other session's LAN REAL backend work is committed (`48f5a3a`) and untouched.

## 4. Architectural decisions
- **One shared rule** in core (`PortfolioSections`). Both navigators hold the selection and set it from `sectionFor` on every `open`.
- **Android:** `SaveableStateProvider` per section inside the Portfolio root route; `PracticeRoute` is intercepted, not registered.
- **iOS:** both sections stay alive in a `ZStack`, hidden with `sectionVisible` (opacity, hit testing, accessibility). Scroll-to-top is
  section-aware.
- **Shared header:** `StockLandingTopBar` plus pinned tabs, with the same top inset in both sections (no jump on switch).

## 5. Test and build results
| Suite | Result |
|---|---|
| core JVM | PASS 580/0 |
| core iOS | PASS 580/0 |
| shared Android host | PASS 271/0 |
| shared iOS | PASS 267/0 |
| `assembleDebug` | PASS |
| `xcodebuild` | PASS |
| server | NOT RUN (no change) |
| Swift router tests | NOT RUN (no iOS test target) |

## 6. Android verification
Emulator, cached signed-in session, MOCK, requests counted.
- **PASS:**
  - A, B, C, D (pixel-identical scroll);
  - E (pixel-identical);
  - F (order on Portfolio; Back → Practice);
  - G (from a child and at the root);
  - J (dark, 2.0×);
  - K (Practice 2 requests once; 0 on switches, returns and reselect).
- **NOT RUN:** H (signed in).
- **BLOCKED:** I (no sign-out of the owner's session).

## 7. iOS verification
iPhone 16, guest, MOCK, requests counted.
- **PASS:**
  - A, B (0 requests), C, G (section kept), H (prompt → modal → back to Practice);
  - Home card → Practice;
  - J (dark, AX XL; a live size change follows);
  - K (0).
- **BLOCKED** (guest): D, E, F, I, and scroll-to-top on Practice.

## 8. Known issues
- **Text-size layout.** At very large text the tabs sit at different heights in the two sections, because My Portfolio has four header
  actions and Practice two.
- **Pre-existing:** the iOS Practice title doesn't scale with Dynamic Type; the Android Practice inner picker breaks words at 2.0×.

## 9. Unverified scenarios
- Signed-in iOS: D, E, F, scroll-to-top.
- Sign-out reset (both platforms).
- TalkBack and VoiceOver (owner).

## 10. Deviations from the approved plan
- **Guests on Android.** "Open Practice" shows the in-place Practice prompt instead of jumping to sign-in (iOS parity; decision 2).
- **iOS guest sign-in.** A guest who signs in no longer resets navigation (Android parity; decision 3).
- **QA ports.** QA used a counting proxy on :8082 → MOCK :8083. Another session's server (pid 492) holds :8081 and was left untouched.

## 11. Decisions requiring owner approval
1. Approve C4.
2. Keep the guest in-place Practice prompt (recommended), or jump to sign-in.
3. Keep the place when a guest signs in on iOS (recommended).

## 12. Links
- Report: `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6C4.md`.
- Focused diff: `CHATGPT_FOCUSED_DIFF.md`.
- Contact sheets: `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`.
- Raw captures: `captures/` (git-ignored).

**Recommendation: APPROVE.**
