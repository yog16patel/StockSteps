# Phase 5D.5.6C4 — Portfolio My Portfolio / Practice nested tabs (KAN-13)

Status: implemented 2026-10-16; committed and pushed on the owner's approval as "Add Portfolio nested tabs for Phase 5D.5.6C4 with My Portfolio and Practice switching in place on the Portfolio landing, state kept per section, children returning to their section and Practice loading once on Android and iOS". Base: `48f5a3a` (HEAD; the C3 code is `d85eb3e`, pushed).
Plan: `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6_PLAN.md` §13 and the C4 entry. Review package: `docs/reviews/phase5d_5_6c4/`.

## 1. What changed for the user (Android and iOS)
- **Two nested tabs.** The Portfolio landing page has two nested tabs, **My Portfolio | Practice**, directly under the landing bar.
  - They are not primary tabs: the bottom bar keeps five tabs.
  - A tap switches the content in place. There is no push and no Back entry.
  - Spoken as a tab group "Portfolio sections": Compose `Role.Tab` and selected state; iOS `.isTabBar` container with selected traits.
- **Pinned tabs.** The landing bar scrolls away. The nested tabs stay pinned at the top, so sections can be switched at any scroll position.
- **Practice is no longer a pushed page.** "Open Practice" from Home or elsewhere selects Portfolio with Practice shown:
  - any Portfolio pushes are popped;
  - there is no hop and no duplicate Practice page.
- **Each section keeps its state** across nested-tab switches and primary-tab switches:
  - scroll position;
  - Practice's Overview/Holdings/Activity/Challenges picker;
  - the My Portfolio account selection.
- **Children return to their section.**
  - Holding Details, Transactions, a transaction and Insights → My Portfolio.
  - Practice order → Practice. "View holdings" after an order shows Practice, refreshed.
- **Reselecting Portfolio:**
  - from a child: pops to the landing and keeps the section;
  - at the landing: scrolls the visible section to the top and keeps the section.
- **Guests:**
  - My Portfolio shows its existing sign-in banner and empty state;
  - Practice shows its existing "Sign in to use your Practice Portfolio" prompt in place, which opens the sign-in modal;
  - Practice orders still need sign-in.
- **Sign-out and account switches** reset the section to My Portfolio. A guest who signs in keeps their place (Android already did; iOS now
  matches).

## 2. Architecture decisions
1. **Shared rule in core.** `navigation/PortfolioSection` (`PortfolioSection` + `PortfolioSections`):
   - the default section (My Portfolio);
   - the spoken group label;
   - `sectionFor(screen)` (which section a Portfolio child belongs to);
   - `isLandingSection` (Practice is a section, not a pushed screen).

   `ScreenId.PRACTICE` stays in the policy as the section's identity. Tests: `PortfolioSectionTest`.
2. **The selection lives in the navigator/router:**
   - Android `TabNavigator.portfolioSection`, `rememberSaveable` beside the trail;
   - iOS `TabRouter.portfolioSection`.

   `open()` sets the section from `sectionFor`, so a child always unwinds to its own section. `reset()` restores the default.
3. **Android:**
   - `route<PortfolioRoute>` hosts both sections through `rememberSaveableStateHolder` / `SaveableStateProvider(section.name)`, which keeps each
     section's saved state (the `LazyListState` and `rememberSaveable` values) while the other is shown;
   - `PracticeRoute` remains a type but is no longer registered: `nav.open(PracticeRoute)` → `showPortfolioSection(PRACTICE)`;
   - the shared header is `LazyListScope.portfolioLandingHeader`: `StockLandingTopBar` item + `stickyHeader` with `PortfolioSectionTabs`
     (`StockSegmentedControl(asTabs = true)`).
4. **iOS:**
   - the Portfolio tab root is a `ZStack` of `PortfolioScene` and `PracticeScene`. Practice is built the first time it is shown;
   - after that, both stay alive and are hidden with `sectionVisible` (opacity, hit testing, `accessibilityHidden`). This keeps the scroll and
     `@State` of each section;
   - the `.practice` `AppDestination` is removed;
   - the tabs are a pinned `Section` header in each section's `LazyVStack(pinnedViews:)`;
   - scroll-to-top on reselect only acts in the selected section (`scrollsToTopOnReselect(_:section:)`), so the hidden section keeps its
     position.
5. **No extra loading.**
   - Practice's presenter loads when it starts and on each account change. The old Android `LifecycleResumeEffect` refresh and the iOS
     "refresh when Practice is the top" hook are removed: with Practice in place, they would have refetched on every section or tab switch.
   - Orders refresh explicitly; pull-to-refresh and Retry stay.
6. **Same header geometry in both sections.** Practice takes My Portfolio's top inset (`space.screen`) when it is a section, so the bar and tabs
   don't move on a switch. On Android the tab bounds are identical: `[48,396][1232,540]` in both sections.

## 3. Files changed
- **Core (new):**
  - `navigation/PortfolioSection.kt`;
  - `navigation/PortfolioSectionTest.kt` (3 tests).
- **Android/shared:**
  - `designsystem/components/StockSegmentedControl.kt` (`asTabs`, optional, default unchanged);
  - `presentation/portfolio/PortfolioSectionTabs.kt` (new);
  - `PortfolioScreen.kt` and `PortfolioScene.kt` (`section`/`onSection` replace `onPractice`; the old segmented item is removed);
  - `practice/PracticeScreens.kt` and `PracticeScenes.kt` (`header`, reselect scroll, no resume refresh);
  - `TabNavigator.kt`, `AppNavigation.kt`, `AppScreens.kt` (Practice unregistered);
  - `AppScreensTest.kt` (registry 41, Practice not a route, new `portfolioChildrenReturnToTheirSection`).
- **iOS:**
  - `AppScene.swift` (`portfolioLanding`, Practice model for guests, reset rule);
  - `TabRouter.swift` (`portfolioSection`, `showPortfolioSection`, `sectionVisible`, section-aware scroll-to-top; `.practice` removed);
  - `PortfolioScreen.swift` (pinned `PortfolioSectionTabs`);
  - `PortfolioScene.swift`;
  - `PracticeScenes.swift` (landing bar/tabs, top inset, live Dynamic Type);
  - `DesignSystem/StockSettingsComponents.swift` (`asTabs`).
- **Server, backend APIs, fixtures:** none.

## 4. Device QA (local MOCK; requests counted through a proxy on :8082 → MOCK :8083, except where noted)
Android: emulator-5554 with the cached signed-in test session (no credentials typed); sample scenario "Multiple accounts" for A–G, then restored
to "My saved accounts". iOS: iPhone 16 simulator as a guest.

| # | Scenario | Android | iOS |
|---|---|---|---|
| A | Portfolio landing shows My Portfolio \| Practice, My Portfolio selected | PASS | PASS (guest) |
| B | Practice switches in place (no push, no Back) | PASS: first show 1× `/me/practice` + 1× `/performance`; re-show 0 | PASS: guest prompt, 0 requests |
| C | Practice kept after another tab and back | PASS: only Markets' own first-load requests | PASS: 0 requests on return |
| D | My Portfolio scroll kept after Practice | PASS (pixel-identical) | BLOCKED: guest content fits one screen |
| E | Holding Details → Back → My Portfolio | PASS (pixel-identical landing) | BLOCKED: guest has no holdings |
| F | Practice → Buy Stock → order → Back → Practice | PASS (Portfolio tab during the order; Practice selected after Back) | BLOCKED: orders need sign-in |
| G | Reselect from a child → landing, section kept; at the landing → top, section kept | PASS (both) | PASS (section kept, 0 requests); scroll-to-top not measurable as a guest |
| H | Guest rules: Practice prompt → sign-in modal → close → Practice | NOT RUN (signed-in emulator) | PASS |
| I | Sign-out resets to My Portfolio | BLOCKED (no sign-out of the owner's session) | BLOCKED (guest; no sign-in) |
| J | Light, dark, large text | PASS (dark; 2.0×) | PASS (dark; AX XL) |
| K | No duplicate requests | PASS: cold start → Portfolio 0, Practice 2, switches 0, Markets 3 (its own), return 0, reselect 0 | PASS: 0 for Portfolio, Practice, return and reselect |
| — | Home "Practice Portfolio" card → Portfolio with Practice selected | covered by `open(PracticeRoute)` tests | PASS |
| — | TalkBack / VoiceOver | NOT RUN (owner) | NOT RUN (owner) |

**Found and fixed during QA:**
1. **Duplicate Practice request.** The first Practice show made 2× `/me/practice`: a first-show refresh duplicated the presenter's `start()`
   load. The extra refresh is removed on both platforms, and a re-measure shows 1×.
2. **Tabs scrolled away**, which made D impossible. They are now pinned on both platforms.
3. **Header jump.** The bar and tabs moved ~4 pt between sections (12 vs 16 pt top inset). Fixed and re-measured.
4. **iOS live Dynamic Type.** The kept-alive Practice section didn't follow a live text-size change. It now reads `dynamicTypeSize`
   (repo rule); re-checked live.
5. **iOS scroll-to-top.** It would also have scrolled the hidden section. It is now section-aware. This was found in code review; it compiles
   but could not be measured as a guest.

Captures: `docs/reviews/phase5d_5_6c4/captures/` (git-ignored); contact sheets `CHATGPT_UI_REVIEW_ANDROID.png` / `CHATGPT_UI_REVIEW_IOS.png`.

## 5. Tests and builds (sequential, final code)
| Suite | Result |
|---|---|
| `:core:jvmTest` | PASS 580/0 |
| `:core:iosSimulatorArm64Test` | PASS 580/0 |
| `:app:shared:testAndroidHostTest` | PASS 271/0 |
| `:app:shared:iosSimulatorArm64Test` | PASS 267/0 |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` (MOCK `localhost:8081`) | PASS |
| `:server:test` | NOT RUN (no server change) |
| Swift router tests | NOT RUN (no iOS test target) |

The final iOS build (with the section-aware scroll-to-top) was smoke-tested on the simulator: Portfolio → Practice → reselect.

## 6. Known limitations
- **Text-size layout.** At very large text, My Portfolio's four header actions stack under the title while Practice's two fit beside it, so
  the tabs sit at different heights there. This is inherent to the different actions.
- **Practice title (pre-existing).** Practice's "Practice Portfolio" title doesn't scale with Dynamic Type on iOS. Its inner picker labels break
  inside words at Android 2.0×.
- **Android hidden section.** It is disposed and restored from saved state (scroll and saveable values). iOS keeps it alive and hidden. Both
  keep the user-visible state.
- **Guests on Android.** "Open Practice" now shows Practice's in-place sign-in prompt instead of jumping to the sign-in screen (same as iOS).
  See decision 2.

## 7. Decisions for the owner
1. Approve C4.
2. **Guest "Open Practice":** keep the in-place Practice sign-in prompt on both platforms (recommended; it is the existing Practice guest state
   and needs no extra screen), or jump straight to sign-in as Android did before.
3. **iOS guest sign-in:** keep the user's place (no reset) when a guest signs in (recommended; Android parity). Sign-out and account switches
   still reset.

## 8. Recommendation
**APPROVE.** No known defects remain. Still unverified (owner, before 5D.5.6D closes the navigation work):
- the signed-in iOS checks: D, E, F and scroll-to-top on Practice;
- the sign-out reset (I);
- TalkBack and VoiceOver.
