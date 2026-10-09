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

---

# Phase 5C.1 — verification closure (2026-10-09, same day)

Base: commit "Fix Phase 5C mobile integration bugs and add end-to-end verification report" (`a50a2e3`). Phase 5C.1 is committed as
"Close Phase 5C.1: fix iOS navigation hang, search and duplicate requests, finish iOS walkthroughs" (both pushed on the user's request). MOCK only: LAN `upstream` counter 0 before, during and after; no Google Cloud, no paid
provider calls, no production Firebase, no real push, credentials only for throwaway `@example.test` accounts in the Firebase **Auth emulator**.

## 9. Environment and method

- Backend: LAN host, Compose `stocksteps-local`. Before redeploy the running image's content hash over `core/` + `server/` equalled the 5C tree
  (tag `1c1b0731adb0-dirty-f8eecdbc`); after the 5C commit it was redeployed as `stocksteps-local:a50a2e3cc833` (cached build, ~13 s, container swap only).
  Smoke 23/23 before and after; learning sync "now" → 200, "+2 days" → 400; `upstream` = 0.
- iOS: spare **iPhone 16 Simulator (iOS 18.3)**, ad-hoc signed Debug builds, `--firebase-emulators`; the user's two booted simulators were not touched.
  Accounts were created through the Auth emulator's REST API (the simulator's text injection only delivers one character into secure/numeric fields).
- Request measurement: a counting reverse proxy on the Mac (`127.0.0.1:8091` → LAN backend, session scratchpad) with the git-ignored
  `Local.xcconfig` pointed at it for the session (restored afterwards). Screenshots via `simctl io screenshot` (session scratchpad).
- Fault injection: stopping that proxy (not shared infrastructure) = backend unreachable.

## 10. iOS walkthroughs (previously NOT RUN / PARTIAL)

| Flow | Result | Evidence (Simulator, LAN MOCK) |
|---|---|---|
| A. Company Details | **PASS after fixes** | search → MSFT/TSLA details; price + change in words; 1M/1Y chart (1 request each); stats; P/E sheet; valuation, ratios, sector, news, "not a recommendation"; missing values "—" (TSLA open/volume/market cap); backend down → per-section errors + Try again → recovered; Back keeps scroll. **Found: app hang (#9) and stuck search (#10).** |
| B. Screener | PASS | Discover (catalog prefetched, 0 requests), "Growing" preset 1 request → 12/23 (same as Android), custom filter max price 1 → "0 companies match" empty state + Reset all, select for compare |
| C. Comparison | PASS after fix | 3 companies, Remove (→2), Replace/Add present, metrics, guided interpretation, historical comparison (axis fixed, #22), research checklist, Ask AI (Plus) → sample answer with "not investment advice"; **duplicate requests fixed (#15)** |
| D. My Portfolio | PASS (partial input) | empty state, create account, opening position MSFT ×3 via security search; value **CAD 2,145.53 = Android** (3 × 529.76 × 1.35), holding/unrealized/percent exact, cash "No cash recorded", currency allocation, dividends none, transactions Edit/Delete, history ALL (no points before the opening date — nothing invented). Average price typed as $4 (simulator injection dropped digits), so cost basis differs from the Android run; edit/other transaction types NOT RUN |
| E. Practice | PASS | $10,000 start; buy 2 AAPL preview $909.01 / $9,090.99 (= Android); review; confirm; holdings + allocation 9.09/90.91 %; sell **1 share** (5C fix verified) $454.50 → $9,545.49; reset confirmation → $10,000; server-side totals. Free-tier limits NOT RUN on iOS (account on MOCK Plus; Android covered in 5C) |
| F. Daily Brief | PASS | reader ("isn't from today", indices Up/Down in words, stories, watchlist, upcoming earnings), Plus "Understand More" → "Sample explanation (mock)" + quota, Previous briefs (1 in MOCK) |
| G. Earnings | PASS after fixes | calendar (status from source data), event `INTC:2026-Q3`, reminder → server note "time has passed / notifications off" (no permission prompt), results EPS/revenue exact, price reaction $150 → $157.50 = +5.0 %, Earnings history, free → upgrade alert, MOCK Plus → history chart (gaps not zero) + "Sample explanation" AI |
| Settings / Auth | PASS after fix | sign-in (emulator), MOCK plan Free → Plus, Earnings Digest & AI screen (1 request), stale auth error **#4 reproduced then fixed** |

## 11. Bugs fixed in Phase 5C.1

| # | Bug | Root cause | Fix | Regression test |
|---|---|---|---|---|
| 9 | **Critical (iOS):** Company Details → "Earnings history" or "See full breakdown" froze the app (100 % CPU, no further input) | `CompanyDetailsScene` declared `@Environment(\.openURL)` and owns `navigationDestination`s; a pushed destination re-created that environment value → the scene re-rendered → rebuilt the destination → … (seen with `_printChanges`, `sample`) | `ExternalURLOpener` (no environment dependency) in the three scenes that own destinations: `CompanyDetailsScene`, `CompanyNewsScene`, `MarketsScene` | Simulator: Earnings history, breakdown, Markets "Why did … move?" open, Back works, 0 % CPU (no iOS UI-test target exists) |
| 10 | iOS search stuck on "Searching…" after fast typing ("microsoft") | `.onChange(of: state.query)` missed the last keystroke; the debounce delivered the stale "microsof" and dropped it | `StockSearchViewModel.query` `didSet` schedules the search; view-level `onChange` removed | Simulator: 3 sequences, one request per settled query |
| 11 | (#5) Search field needed a second tap | no focus on open; first tap lost to the sheet animation | `searchable(isPresented:)` activated on open when the query is empty | Simulator: typed without tapping |
| 12 | Launch: `digest/preferences` 4–7× | `EarningsDigestSettingsScene`/`EarningsDigestScene` started presenters in `init`; SwiftUI builds `navigationDestination` content on every `AppScene` update | `IosEarningsClient.digest(loadDigest:start:)` + `EarningsDigestModel.activate()` from `.task` | measured 4–7 → 0 at launch, 1 on open |
| 13 | Launch: `me/watchlists` and `me/alerts` 2× (shared code — Android too) | `ServerBackedRepository` set `loading` only after the cache read, so Home's `onVisible` saw an idle repository and refreshed again | state is `loading` from the moment the account is known | `UserDataRepositoriesTest.repositoryIsLoadingFromTheMomentTheAccountIsKnown` (fails without the fix) |
| 14 | Details 2× `me/earnings/{symbol}` (screen never opened); Calendar 5×; Results/price reaction/premium 3× each | presenters created in model `init` (re-run by SwiftUI) | Earnings Details/Calendar/Event/Results/Premium models create presenters on first use and subscribe in `.task` | measured 2 → 0, 5 → 1, 3 → 1 |
| 15 | Compare: each selection change 2× compare/performance/history | every discarded `ScreenerModel` (AppScene `init`) built an `IosScreenerClient` whose `ComparisonPresenter` observes the shared selection from construction | `ScreenerModel.client` created lazily | measured 9 → 5 requests per remove |
| 16 | Practice order: 3× preview per open; order presenters never released | presenter started in `init` on the client's long-lived scope | per-order child scope + `IosPracticeClient.release(order:)`; lazy creation | measured 3 → 1 |
| 17 | (#1) Portfolio "CAD 2145.53" | `PortfolioFormat.amount` had no grouping | grouped display ("2,145.53"), both platforms | `PortfolioFormatTest` (3) |
| 18 | (#2) identical source links ("Yahoo, Yahoo, Yahoo"; iOS "StockSteps Sample" ×2) | label = publisher only | `WhyMovingSource.accessibilityLabel` ("Source: Yahoo, <title>") used by Compose and SwiftUI | `WhyMovingSourceTest` (2) |
| 19 | (#3) Brief card meta cramped at 1.3× (Android) | one `Row` with a weighted text and two buttons | at `fontScale >= largeFontScale` the meta line sits above a `FlowRow` of the actions | see §13 |
| 20 | (#4) iOS sign-in error stayed after editing / switching mode | error cleared only on the next submit | `AccountViewModel.clearError()` on email/password/mode change | see §13 |
| 21 | (#6) "1 of the 1 companies you follow have news …" | fixed template | `BriefWording.recentNews` (server uses it) | `BriefWordingTest` (2) |
| 22 | Comparison history y-axis "1.5E11" (iOS) | default Swift Charts axis format | compact axis labels | see §13 |

## 12. Startup and screen request audit (iOS, signed in, counting proxy; cache hits are not upstream calls — LAN `upstream` stayed 0)

| Endpoint / action | Before | After | Explanation |
|---|---|---|---|
| Cold launch total | 19–21 | **13** (3 runs) | every endpoint once |
| `me/earnings/digest/preferences` | 4–7 | 0 | digest screens only load when shown (#12) |
| `me/watchlists`, `me/alerts` | 2 each | 1 each | repository race (#13) |
| `meta`, `daily-brief/latest`, `me/daily-brief/{id}/personalized`, `screener/catalog`, `me/entitlements`, `me/portfolio`, `me/learning`, `me/screens`, `me/comparison-research`, `me/compare/ai/usage`, `me/earnings/reminders` | 1 each | 1 each | necessary: Home/Markets content, plan, and signed-in repositories that back offline copies; catalog is a prefetch for Discover (Discover then opens with 0 requests) |
| Company Details open | 8 | 6 | `me/earnings/{symbol}` only when Earnings Details opens (#14) |
| Earnings Calendar open | 5 | 1 | #14 |
| Earnings Results open | 9 (3×3) | 3 | #14 |
| Compare: remove a company | 9 | 5 | compare, performance 1Y + 3Y (different periods), history, AI usage (#15) |
| Practice order open | 3 previews | 1 | #16 |
| Practice open | 2 × `me/practice` | 2 (kept) | first start + the intentional `onAppear` refresh that keeps the ledger fresh on re-entry |
| Search | 1 per settled query | 1 | debounced |

## 13. Verification of the 5C.1 fixes

- Verified in the Simulator with the final iOS code for #9–#16 (hang, search, focus, launch/screen request counts).
- **Not verified in UI** (the user stopped the session's final build/verification and asked to commit): #17 grouping (unit-tested; both platforms
  use the shared formatter), #18 labels (unit-tested; no VoiceOver run), #19 Android brief card at 1.3× (compiles; not seen on the emulator),
  #20 iOS auth error clearing, #22 comparison axis. The final iOS `xcodebuild` with these last Swift changes was **not run** in this session
  (the previous build, which included everything up to #16, succeeded).
- Closure session (same day, after commit `fc3961e`): the final iOS `xcodebuild` **succeeded** (§15), so #17–#22 compile on both platforms; they were
  still **not exercised in the running apps** (no simulator/emulator actions in the closure session).

### 13a. Regression strategy for the critical iOS navigation hang (#9)
- **No automated UI coverage**: the project has no iOS UI-test (XCUITest) target, and no Kotlin test can observe a SwiftUI render loop. The fix is
  protected by (1) the documented rule in `CLAUDE.md` (scenes owning `navigationDestination`s must not declare `@Environment(\.openURL)`; use
  `ExternalURLOpener`) and the KDoc on `ExternalURLOpener.swift`, (2) a static check re-run at closure: every scene that owns a destination
  (`AppScene`, `CompanyDetailsScene`, `CompanyNewsScene`, `ComparisonResearchViews`, `EarningsPremiumViews`, `EarningsScenes`, `MarketsScene`,
  `PortfolioScene`, `ScreenerScenes`) has no `@Environment(\.openURL)`; the five remaining users (`NewsInsightScene`, `StockMovementScene`,
  `DailyBriefScreen`, `HomeScene`, `AlertsScene`) own no destination, and (3) the manual Simulator walkthrough (§10 A, Company Details → Earnings history /
  See full breakdown → Back, CPU back to 0 %).
- **Recommended follow-up** (not implemented): an XCUITest target with one smoke test per destination-owning scene (push, assert the pushed title
  appears within a timeout, go Back) run against the MOCK backend, plus a CI script that fails when a file containing `navigationDestination`
  also declares `@Environment(\.openURL)`.

## 15. Automated tests (final matrix)

**Closure re-run (2026-10-09, evening, HEAD `fc3961e`, working tree = docs only)**: `./gradlew :server:test :core:jvmTest :core:iosSimulatorArm64Test
:app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`:
- server **482 passed / 0 failed / 3 skipped** (Firestore-emulator persistence tests; skipped ≠ passed — Phase 5D); core JVM **429 / 0**; shared
  Android host **55 / 0**; shared iOS **51 / 0**; `:app:androidApp:assembleDebug` **OK**. These tasks were Gradle UP-TO-DATE, i.e. their inputs are
  identical to the final code and the recorded results (from the interrupted run) apply to it.
- core iOS: the first attempt failed with a Gradle infrastructure error (`NoSuchFileException … in-progress-results-generic.bin`, left over from
  the killed run — not a test failure); after deleting `core/build/test-results/iosSimulatorArm64Test` the re-run passed **429 / 0 / 0 skipped**
  (8 min 13 s), including `PortfolioAnalyticsEngineTest.everyFixtureIsInternallyConsistent`.
- iOS `xcodebuild … CODE_SIGN_IDENTITY=- CODE_SIGNING_ALLOWED=YES build`: **BUILD SUCCEEDED**.

Earlier, interrupted run (kept for history):
`./gradlew :server:test :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`
was started on the final code and **stopped by the user** before it finished:

`./gradlew :server:test :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`
was started on the final code and **stopped by the user** before it finished:
- server **482 passed / 0 failed / 3 skipped** (Firestore emulator; skipped ≠ passed); core JVM **429 / 0** (421 + 8 new); shared Android host **55 / 0**;
  shared iOS **51 / 0**.
- core iOS: **interrupted** — 294 tests had run, 0 assertion failures; `PortfolioAnalyticsEngineTest.everyFixtureIsInternallyConsistent` is recorded
  as failed with type "Unknown" after 308 s because the run was killed while it executed (it passes on the JVM). Re-run before relying on it.
- `:app:androidApp:assembleDebug` and the iOS `xcodebuild`: **not completed** for the final code. `:app:shared:compileAndroidMain` passed earlier.
- New regression tests: `PortfolioFormatTest` (3), `WhyMovingSourceTest` (2), `BriefWordingTest` (2), `UserDataRepositoriesTest` +1 (verified to fail without its fix).

## 16. Verdict

**PASS WITH CONDITIONS.** All previously untested iOS flows (A–G) were exercised through the real UI against the LAN MOCK backend; a critical iOS
navigation hang and a stuck-search bug were found and fixed; startup requests dropped 19–21 → 13 and duplicated screen requests were removed; zero
provider calls. Conditions: re-run the interrupted matrix (core iOS, `assembleDebug`) and the iOS `xcodebuild`; verify #17–#20/#22 in the apps;
redeploy the LAN backend (5C.1 brief wording) and re-run smoke; physical devices, VoiceOver/TalkBack, real Firebase; #7/#8 fixture refresh.

**Closure update (same day): PASS WITH CONDITIONS — automated conditions met.** The full matrix and the iOS `xcodebuild` pass on the final code
(§15). Read-only LAN check: container `stocksteps-local-api-1` healthy, image `stocksteps-local:a50a2e3cc833` (Phase 5C), `dataMode` mock,
`/health/live` / `/health/ready` / `/api/v1/meta` 200, `upstream` counter absent (= 0); the only AI counter is the MOCK template
(`ai.comparison.model.template`), no live model. Remaining conditions:
- LAN backend is **one commit behind** for the server (5C.1 `BriefWording.recentNews`; image built from `a50a2e3`, server/core diff to HEAD: 5 files) —
  redeploy + 23-test smoke **awaiting approval**;
- #17–#20/#22 not seen in the running apps; no iOS UI-test target (§13a);
- not run: physical Android/iPhone, VoiceOver/TalkBack, Dynamic Type/font-scale sweep, real Firebase/Google sign-in, push delivery,
  process-death restoration; Firestore persistence tests (3 skipped) pending Phase 5D;
- simulator limits: text injection drops characters in secure/numeric fields (accounts made through the Auth-emulator REST API), no real push,
  Simulator performance ≠ device;
- minor UI issues in §14 (to be handled by `docs/GLOBAL_UI_REFINEMENT_PLAN.md`) and MOCK fixture artefacts #7/#8.

Environment left behind: Firebase Auth emulator and counting proxy stopped; `Local.xcconfig` restored to the LAN URL; spare iPhone 16 Simulator
booted with the throwaway account signed in (text size restored to default); Android emulator untouched in 5C.1 (still has the 5C LAN/auth-emulator build).

## 14. Deferred / observations (not fixed in this milestone)

- **#7 MOCK market cap** (MSFT $934.6B): `SampleMarketData.fillQuote` fills a captured quote's missing market cap from deterministic random
  shares (50M–5B) × the captured price. MOCK-only; changing it moves many fixture-based expectations — defer to a fixture refresh.
- **#8 MOCK quarter growth = annual** (MSFT "+14.9 % YoY" every quarter; comparison "latest quarter" equals fiscal year): the quarter metric comes
  from the MOCK earnings history's year-over-year insight, whose generated quarters carry the annual growth. Labels and periods are correct;
  REAL is unaffected. Defer to a fixture refresh.
- Minor UI/copy: chart error says "Price history isn't available for this range" when the backend is unreachable; raw enum labels
  ("PERSONAL", "OPENING POSITION") in Portfolio forms; "SIMULAT-ED" badge wraps; "+14.9 % (Y…" truncated in At a Glance; MSFT P/E 39.0× in the
  screener vs 39.2 on Details (different snapshots); iOS decimal keypads have no Done key (Review reachable by scrolling); Back from an earnings
  event opened from the calendar returns to Markets; Practice shows a $0.01 "loss" after buying and selling at one price (half-cent rounding of
  the cost basis — honest exact-decimal behaviour); MOCK brief for Oct 7 lists stories stamped Oct 9 (fixture news times follow the wall clock);
  first "Show results" tap in Screener filters while the keyboard was open did not apply (not reproduced); Dynamic Type: body text scales but
  some captions/pills and the sample banner use fixed sizes.
