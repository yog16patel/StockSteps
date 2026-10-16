# Review package: Phase 5D.5.6D (final navigation QA, accessibility & stabilization, KAN-15)

## 1. Git baseline and changed files
- **Base:** `4c5990f` "Add Portfolio nested tabs for Phase 5D.5.6C4 …" (pushed).
- **Under review:** "Stabilize navigation for Phase 5D.5.6D with once-per-screen loading on Android, a kept company model and reading position after reused-top Back on iOS, no duplicate reminders request, readable Practice tabs and title at large text and a guest sign-in card in My Alerts" (committed locally, not pushed).
- **Core:**
  - `earnings/EarningsReminders.kt` (refresh guard);
  - `EarningsRemindersPresenterTest.kt` (+1 test).
- **Shared/Android:**
  - `presentation/EntryOnce.kt` (new) + `EntryOnceTest.kt` (new);
  - `brief/DailyBriefScenes.kt`, `earnings/EarningsReminderUi.kt`, `screener/ScreenerScene.kt`, `practice/PracticeScenes.kt`;
  - `practice/PracticeScreens.kt`, `designsystem/components/StockSegmentedControl.kt`.
- **iOS:**
  - `TabRouter.swift`, `AppScene.swift`;
  - `CompanyDetailsModel.swift`, `CompanyDetailsScene.swift`, `CompanyDetailsScreen.swift`;
  - `PracticeScenes.swift`, `WatchListScene.swift`;
  - `DesignSystem/StockComponents.swift`, `DesignSystem/StockStatusViews.swift`.
- **Server, APIs, fixtures:** none.
- **Docs:** report, this package, focused diff, QA matrix, two contact sheets, plan, handoff, status.

## 2. Navigation coverage
All 43 policy screens are registered on both platforms (inventory: report §2). Device coverage is in `CHATGPT_QA_MATRIX.md`:
- stacks (A–E);
- cross-tab Back and swipe-back (S1–S5, rapid swipes);
- reselection on all five tabs, from a deep screen and at the root;
- C4 nested tabs;
- modals and search;
- notification links;
- background, rotation and process death;
- dark and large text.

## 3. Parity findings
- Behaviour matches on everything reached (canonical tabs, Back targets, reuse, reselect, sections).
- Accepted platform differences:
  - Android Settings and Notifications are full-screen global screens; iOS uses sheets;
  - iOS shows truthful native back labels;
  - Android's hidden Portfolio section is restored from saved state, while iOS keeps it alive.

## 4. Fixed defects (root causes: report §4)
1. **Reminders:** duplicate GET on first open (core guard + test).
2. **Android lifecycle:** effects re-ran on every tab return. The Daily Brief refetched and **cleared AI answers**; the history, reminders,
   research, AI usage and post-order refreshes also repeated. Fixed with `LaunchedOncePerEntry`.
3. **iOS reused-top Back:** the shared company model was released, causing 6 refetches and a lost position. It is kept now, and the page
   returns to its position.
4. **Android Practice tabs at 2.0×:** they broke inside words; now whole words in a scrolling row, with tab semantics.
5. **iOS Practice title:** didn't follow Dynamic Type.
6. **iOS live Dynamic Type:** kept-alive sections didn't follow live text-size changes. Fixed through the design-system components.
7. **iOS guest My Alerts:** placeholders never finished (since C2); a guest now gets the sign-in card.

## 5. Unresolved defects
- **P2, Android:** a short Learn term page loses a small scroll offset after a tab switch. Cause not confirmed; one fix attempt reverted.
- **P2, Android:** the Financials period resets after process death.
- **P2, iOS:**
  - native "Back" shortening next to long titles;
  - a search row can show initials before its logo loads.

## 6. Signed-in evidence
- **Android:** the cached signed-in test session covered the C4 flows and holdings via the sample scenario. Sign-out and account switch:
  **BLOCKED**, because signing out would need credentials to sign back in.
- **iOS:** all signed-in flows are **BLOCKED**: My Portfolio scroll, Holding Back, Practice Order Back, Practice scroll-to-top, account switch,
  sign-out. There is no approved disposable account, and the Firebase Auth emulator needs owner approval.
- **Code-level coverage of resets:**
  - core `TabNavigation.reset` (I11) tests;
  - `TabNavigator.reset` / `TabRouter.reset` restore My Portfolio;
  - presenters clear on a user change (`PracticePresenter`, `DailyBriefPresenter`, `EarningsRemindersPresenter` tests).

## 7. Accessibility
- **PASS:**
  - Android 2.0× Practice tabs (whole words, `Role.Tab` + selected);
  - iOS AX XL and AX5 Practice (cold and live);
  - nested tabs and landing bars in dark mode;
  - guest alerts card.
- **NOT RUN:** TalkBack and VoiceOver walkthroughs (manual, owner).

## 8. Request counts (report §6)
- Zero extra requests on tab switches, section switches, reselect, background → foreground and Back in every measured sequence after the
  fixes.
- Accepted refreshes:
  - Home's documented 60 s freshness refresh of alerts and watchlists;
  - cold-start loads after process death.

## 9. Tests and builds (sequential, final code)
| Suite | Result |
|---|---|
| `:core:jvmTest` | PASS 581/0 (+1) |
| `:core:iosSimulatorArm64Test` | PASS 581/0 |
| `:app:shared:testAndroidHostTest` | PASS 273/0 (+2) |
| `:app:shared:iosSimulatorArm64Test` | PASS 269/0 |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` (MOCK `localhost:8081`) | PASS |
| server tests | NOT RUN (no server change) |
| Swift router tests / Compose UI tests | NOT RUN (no iOS test target; no Robolectric/instrumented setup) |

## 10. Screenshots and matrix
- `CHATGPT_UI_REVIEW_ANDROID.png`: before/after Practice tabs at 2.0×, Markets, Financials, Learn term, Holding, Practice order, dark pinned
  tabs.
- `CHATGPT_UI_REVIEW_IOS.png`: before/after Practice title, before/after guest alerts, reused-top restore, Financials, P/E term, dark.
- `CHATGPT_QA_MATRIX.md`; raw captures in `captures/` (git-ignored). The Android Settings capture showing the account e-mail was excluded.

## 11. Remaining launch risks
1. Signed-in iOS navigation and the Portfolio children, unverified on device.
2. Sign-out and account-switch isolation verified only by code and unit tests.
3. iOS notification-link routing, unverified on device.
4. Screen-reader announcements of the tabs and nested tabs, unverified.
5. The offset-based restore of a rebuilt iOS company page can land slightly off if content above changed height meanwhile.

## 12. Recommendation
**APPROVE WITH FIXES for implementation; HOLD for final navigation sign-off.** The owner must first run the signed-in iOS and
sign-out/account-switch pass with an approved test account, plus TalkBack/VoiceOver.
