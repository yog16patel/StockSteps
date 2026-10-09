# Phase 5C — Android & iOS end-to-end integration test report

Date: 2026-10-09. Base: `1c1b073` "Add Phase 5B Local Docker deployment of the MOCK backend on a LAN host with smoke and reliability checks"
(pushed). Phase 5C changes are committed as "Fix Phase 5C mobile integration bugs and add end-to-end verification report". No Google Cloud, no paid provider calls (LAN backend `upstream` counter stayed 0 throughout),
no production Firestore, no real push, no real credentials typed.

## 1. Environment

| Item | Value |
|---|---|
| Backend | LAN Ubuntu host, Compose `stocksteps-local`, MOCK (`/api/v1/meta` = mock); redeployed during 5C with the learning fix (`stocksteps-local:1c1b073…-dirty-…`), healthy |
| Auth | Firebase **Auth emulator** on the Mac (`127.0.0.1:9099`, `firebase-tools` 15.32.1 from `firebase/`); throwaway accounts A, B, C (`@example.test`, credentials only in the session scratchpad); Android via `adb reverse tcp:9099` |
| Android | `emulator-5554`, `sdk_gphone16k_arm64`, Android 17 (API 37), 1280×2856 @480 dpi; debug build with `-PstockstepsMockBackendUrl=http://<LAN>:8081 -PfirebaseEmulators=true`, installed with `adb install -r` (user-approved) |
| iOS | Spare iPhone 16 Simulator, iOS 18.3 (Xcode 27.0); Debug build, ad-hoc signed for the simulator (`CODE_SIGN_IDENTITY=-`; unsigned builds can't use the keychain → Firebase Auth error 17995), git-ignored `Local.xcconfig` → LAN mock URL, launch argument `--firebase-emulators` |
| Driving | Android: `adb` input + `uiautomator dump` + screenshots; iOS: simulator taps/typing + `simctl` screenshots (the panel's screenshots lag one action) |

## 2. Feature matrix

| Feature | Android | iOS | Automated tests | Issues |
|---|---|---|---|---|
| A. Home | PASS (launch, brief card, portfolio/watchlist empty states, Practice card, sample banner) | PASS (guest + signed in; tab labels fixed) | existing suites | #3 iOS banner covered tab labels (fixed) |
| B. Markets | PASS (session, indices with a11y text, movers, brief, earnings entry, sample labels; light + 1.3× font) | PASS (render) | existing | Brief card meta text cramped at 1.3× (minor) |
| C. Company Details | PASS (price, a11y change in words, chart ranges, stats, Why It Moved, At a Glance, health cards, P/E sheet + back; rotation keeps state, 0 refetch) | PARTIAL (search → results verified; details screen not walked) | existing | three identical "Yahoo" source links (a11y, minor); MOCK filler market cap implausible (fixture) |
| D. Screener | PASS (presets, preset run = 1 request, filter chips, 12/23 matches, select for compare) | NOT RUN | existing | — |
| E. Comparison | PASS (2 companies, replace/remove, guided interpretation, Explain entry) | NOT RUN | existing | quarter growth = annual growth in MOCK fixtures (fixture artefact, labels correct) |
| F. Portfolio | PASS (empty state, create account, opening position MSFT ×3 → value/cost/unrealized exact; daily change honestly unavailable) | NOT RUN | existing | "CAD 2145.53" lacks grouping vs "$9,090.99" elsewhere (minor) |
| G. Practice | PASS (preview, buy 2 AAPL, sell 1, server-side totals/FX, free-tier 1 of 3, allocation lock) | NOT RUN | PracticeFormatTest (new) | #4 "1 shares" (fixed); #5 "Apple Inc.." a11y and iOS missing stale note (fixed) |
| H. Watchlists & Alerts | PASS (create list, add MSFT, row, price-above alert, My Alerts with MOCK note) | PASS (server watchlist shown; account isolation A↔B) | existing | — |
| I. Guided Research & Learn | PASS after fix (journeys, step 1 content; sync notice gone; progress synced iOS→server→Android) | PASS after fix (no sync notice; progress synced per account) | LearningRoutesTest (+1), ResearchStepLabelTest (new) | #1 MOCK rejected all progress syncs (fixed); #6 "?." a11y (fixed) |
| J. Daily Brief | PASS (dates, "not from today", indices, sample label; Plus: personalised insight, Ask → "Sample explanation (mock)", 1 premium-AI request) | PARTIAL (card on Home only) | existing | — |
| K. Earnings | PASS (calendar week with a11y day cells, reported filter, event, results EPS met/revenue miss exact) | NOT RUN | existing | — |
| L. Auth & Settings | PARTIAL (Settings, MOCK plan Free↔Plus, theme; sign-in with emulator account; real-account session was dropped by the emulator build) | PASS (signed-out, guest, sign-up mode, sign-in, sign-out confirm, A→B isolation, Settings → Sign in) | existing | #7 Settings "Sign in to sync" did nothing (fixed); #2 search; stale auth error stays after editing (minor); keychain error shows generic message (dev builds only) |
| Search (cross-cutting) | PASS (debounced: 1 request per query; back-stack keeps query) | PASS after fix | IosStockStepsClientTest (new) | #2 every iOS search failed (fixed) |

## 3. Bugs found and fixed

| # | Bug (repro) | Root cause | Fix | Regression |
|---|---|---|---|---|
| 1 | Learn shows "will sync when the connection is back" forever in MOCK; `PUT /api/v1/me/learning` with a visit stamped now → 400 `INVALID_PROGRESS` | `LearningService` rejected timestamps > `clock + 1 day`, with `clock` = MOCK market clock pinned to the Oct 7 fixture capture | separate `wallClock` (real time) for the future check; AI day/snapshot keep the market clock (`learning/LearningService.kt`) | `LearningRoutesTest.futureCheckUsesRealTimeNotThePinnedMarketClock`; LAN: 400 → 200 after redeploy |
| 2 | iOS search always "Could not load stocks"; Retry same | `IosStockStepsClient`: `scope.async { searchStocks(query) }` resolved to the member function (recursion) instead of the same-named use-case property → endless self-cancelling jobs on the main dispatcher; same for `getCompanyNews` | renamed properties `searchStocksUseCase` / `companyNewsUseCase`; internal test constructor | `IosStockStepsClientTest` (2, MockEngine); verified in Simulator |
| 3 | iOS: "Sample data · mock backend" banner hides the tab bar titles | root `.safeAreaInset` is ignored by the UIKit tab bar layout | banner stacked under the app in `ContentView` (VStack); background fills only the container safe area | Simulator screenshots (light/dark) |
| 4 | Practice shows "1 shares" (quantity rows, confirmations, a11y, sell limit) on both platforms | hard-coded plural | `PracticeFormat.shareCount` used by Compose, SwiftUI and the engine message | `PracticeFormatTest` |
| 5 | Holding a11y "AAPL, Apple Inc.. 2 shares"; iOS omitted the stale-price note | string concatenation; iOS ignores children | shared `PracticeFormat.holdingDescription` | `PracticeFormatTest` |
| 6 | Research step a11y "What does Microsoft do?. Not started" | ". " after a question | shared `ResearchStep.accessibilityLabel` | `ResearchStepLabelTest` |
| 7 | iOS Settings (guest) → "Sign in to sync" does nothing | second sheet presented while Settings sheet is up | close Settings first (pattern already used by nested rows) | Simulator |
| 8 | iOS launch repeats screener (6 per launch) and other requests | `AppScene.init` re-runs on parent updates; models created there started loading in `init` and were discarded | `start: false` + `activate()` from `.task` on the kept instances (Screener, Earnings reminders, Learning, Brief); other call sites unchanged | measured: screener 6 → 2 per launch |

Diagnostics added during investigation were removed (`grep PHASE5C-DIAG` = 0).

## 4. Performance and reliability observations

- No polling: 0 requests in 60 s idle. Search debounced (Android 1 request for a typed query). Screener preset = 1 request. Rotation: 0 refetch.
- iOS launch (signed in) after fix: research 1, brief 2, earnings 5–7, screener 2, user data 6–8 — earnings worth an itemised audit.
- Backend restart (redeploy): both apps recovered; MOCK state reset as documented.
- Android Logcat: no StockSteps errors/crashes, one process for the whole session; the `F` lines are the emulator's UWB HAL.
- Fault injection (timeouts, 400/401/429/500, empty bodies) is covered by existing MockEngine suites; not re-injected live.

## 5. Accessibility

Fixed: #4–#6. Verified: gain/loss in words (`up +3.04`), index/day-cell/holding descriptions, 48 dp targets on checked rows, 1.3× font and
light/dark on Android, dark mode on iOS. Open (minor): identical "Yahoo" link labels; cramped Brief meta at large font; VoiceOver/TalkBack
not run with the screen readers themselves; Dynamic Type not exercised on iOS.

## 6. Test results

`./gradlew :server:test :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`
→ **BUILD SUCCESSFUL in 9m 19s**: server 482 passed / 0 failed / 3 skipped (Firestore emulator); core JVM 421/0; core iOS 421/0; shared Android host
55/0; shared iOS 51/0; `assembleDebug` OK. iOS `xcodebuild` (Debug, simulator, ad-hoc signed) BUILD SUCCEEDED. New/changed tests: `PracticeFormatTest` (3), `ResearchStepLabelTest` (2), `LearningRoutesTest` (+1),
`IosStockStepsClientTest` (2).

## 7. Not run / remaining manual checks

iOS: Screener, Comparison, Portfolio, Practice, Earnings, Brief detail walkthroughs. Both: physical devices, real Firebase sign-in/Google,
push notifications, VoiceOver/TalkBack sessions, process death restoration, airplane-mode UI. Android: the debug build installed on
`emulator-5554` points to the LAN backend and the auth emulator; the user's real-account session there was signed out by it — reinstall the
normal debug build and sign in again.

## 8. Verdict

**PASS WITH CONDITIONS** — critical flows exercised through real UI on both platforms against the LAN MOCK backend; 8 integration bugs fixed
with regression tests or simulator verification; zero provider calls. Conditions: the iOS feature walkthroughs marked NOT RUN, physical-device
and real-auth checks, and the minor findings above.
