# ChatGPT review package — UI/UX Refinement Phase 8E (navigation and motion)

## 1. Phase and Git baseline
- **Phase:** 8E, the last scoped UI/UX task before backend production readiness.
- **Base:** `1c71207` "Finish UI/UX Refinement Phase 8D …" (committed). The tree was clean at the start.
- **Under review:** the commit "Polish Android navigation motion for UI/UX Refinement Phase 8E with instant tab switches, a mirrored 220 ms push and Back, 150 ms modal fades and the system animation scale respected, keeping iOS native" (on `945601b`, Phase 9A). Tests ran on the same 8E code over `1c71207`; the Phase 9A suite also ran with 8E in the working tree (core 625/0 and 623/0, shared 289/0 and 286/0, `assembleDebug` and `xcodebuild` PASS).

## 2. Root cause (measured)
1. **The library default applied to every destination:** Android `navigation-compose` 2.9.7 runs `fadeIn(700)` + `fadeOut(700)` at the
   same time, tab switches included. Two pages are drawn over each other for about 0.5 s (`captures/before-tab-switch-double-exposure.png`).
2. **The top bar is outside the NavHost:** the Scaffold bar switches before the new page appears, so the new title sits over the old page
   (`captures/before-push-topbar-over-old-page.png`).
3. **Forward and Back look the same.**

## 3. Changes (Android only; iOS stays native, no iOS code change)
- **Core policy:** `NavigationMotion` defines NONE / PUSH / POP / MODAL_IN / MODAL_OUT and the timings (220 ms, 24 dp, 70/150 ms fades).
- **Navigator:** `TabNavigator.motion` is recorded by `open`, `back`, `selectTab`, reselect, sections and reset.
- **NavHost:** one set of transitions in `NavMotionTransitions`. Tabs instant; push fade-through and slide; Back mirrored; modals fade. The
  system animator scale is respected.

## 4. Files
| Area | Files |
|---|---|
| Core | `navigation/NavigationMotion.kt` (new), `navigation/NavigationMotionTest.kt` (new) |
| Shared/Android | `presentation/NavMotionTransitions.kt` (new), `presentation/TabNavigator.kt`, `presentation/AppNavigation.kt` |
| iOS, server, data | none |
| Docs | this package and its 11 companion files; `docs/UI_UX_REFINEMENT_PHASE8.md`; handoff; status |

## 5. Verification
- **Android (emulator-5554, MOCK):**
  - tabs instant: PASS; push and Back: PASS;
  - modals, rapid taps, repeated Back, background: PASS;
  - animator scale 0.5× and Off: PASS;
  - requests identical before and after (11 = 11, tabs 0 = 0);
  - gfxinfo shows fewer frames per flow and a lower p99 on the forward flow (loaded emulator; see the Android review).
- **iOS (iPhone 16, guest):** native tabs, push, pop and swipe-back: PASS. Reduce Motion: NOT RUN (no app animations to adapt).
- **NOT RUN / BLOCKED:**
  - process recreation (8E);
  - TalkBack and VoiceOver;
  - signed-in iOS;
  - physical device;
  - dark-mode video.

## 6. Tests and builds
See `CHATGPT_TEST_RESULTS.md`.

## 7. Known limitations
See `KNOWN_LIMITATIONS.md`. Main item: the top bar still changes before the new page's first frame, which is composition latency (it shows
with animations Off too). The fix is moving top bars into routes, a larger change for later.

## 8. Owner decisions
1. Accept the motion policy: 220 ms push and Back, instant tabs, 150 ms modal fade.
2. Backlog: move pushed-screen top bars into routes (removes limitation 1).
3. Backlog: opt into seekable predictive Back.

## 9. Artifacts
- `NAVIGATION_ARCHITECTURE_AUDIT.md`, `MOTION_POLICY.md`, `ANDROID_NAVIGATION_REVIEW.md`, `IOS_NAVIGATION_REVIEW.md`;
- `MOTION_BEFORE_AFTER.md`, `NAVIGATION_REGRESSION.md`, `API_REQUEST_REGRESSION.md`, `ACCESSIBILITY_REVIEW.md`;
- `KNOWN_LIMITATIONS.md`, `CHATGPT_FOCUSED_DIFF.md`, `CHATGPT_TEST_RESULTS.md`;
- videos and frame strips in `captures/` (git-ignored).

**Status: READY FOR REVIEW.**
