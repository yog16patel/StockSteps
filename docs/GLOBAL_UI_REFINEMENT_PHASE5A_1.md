# Global UI Refinement Phase 5A.1 — Watchlist final polish

Status: **committed and pushed** as "Polish Watchlist header wrapping, two-line names, fading list selector, short empty title and single offline label (Global UI Refinement Phase 5A.1) on Android and iOS" (2026-10-10). Base: `50580b5` (Phase 5A, pushed). A targeted polish pass, not a redesign.
Device checks ran with approval: **local MOCK server on :8081 only** (no LAN host). Signed-in data lived only in the MOCK server's memory;
iOS used guest mode (on-device list). A temporary iOS 18.3 iPhone SE was created and deleted afterwards. Phase 5B has not started.
"Before" evidence is the committed Phase 5A set in `docs/design/phase5a/`.

## 1. Issues — before / after

| # | Pri | Issue (before) | Defect or guest/signed-in difference? | After |
|---|---|---|---|---|
| A | P0 | Header at 360 dp, 1.3×: "5 companies · Updated 5:16 / PM ET" broke inside the time | Defect (both platforms) | Shared `WatchlistModel.headerLine` uses non-breaking spaces inside each part, so it wraps only between parts ("5 companies ·" / "Updated 5:16 PM ET"). Title, search and ⋮ unchanged; no font reduced |
| B | P1 | Names cut after one line ("Microsoft Corpor…", "Amazon.co…") | Defect | Optional `nameMaxLines` / `nameLineLimit` on the shared `StockRow` (default unchanged elsewhere); Watchlist passes 2. Values still follow `StockQuoteRowLayout` (verified per row on the SE: MSFT stacked, TSLA beside) |
| C | P1 | A long selected list name filled the selector with no cue that "+" or other lists follow | Defect (signed-in only; guests have one list) | The selector uses the new shared `StockFadingScrollRow` (the 4C.3 fade + keep-selected-visible, extracted from `StockPillSelector`): the edge fades while more lists are hidden, and the selected chip scrolls into view on switch. Names stay full; no dropdown |
| D | P1 | "Long Term Growth and Dividends is empty" became a two-line heading | Defect | Visible "This watchlist is empty" (default list keeps "Your watchlist is empty"); the spoken title keeps the name ("Long Term Growth and Dividends is empty") via a new optional `titleDescription` / `titleAccessibilityLabel` on the shared empty state. One primary "Add a company" |
| E | P0/P2 | Offline: the same orange "Saved prices · as of Oct 7" on every row | Defect (repetition) | Header shows it once in the caution colour: "5 companies · Offline · saved prices as of Oct 7"; rows keep their own label only when their date differs; each row's spoken label still says "Saved prices · as of …". Missing prices stay "No data available" |
| F | P0 | MOCK banner / safe areas | **No defect found** | Verified: the page end (disclaimer) sits above the banner on Android; iOS banner sits above the tab bar (Phase 4C.3) and above the home indicator on screens without tabs. Colours come from the shared semantic banner role, already covered by the AA contrast test in light and dark (`SemanticStylesTest`). No change |
| G | P2 | Alerts and more, insights, spacing | No defect found | Unchanged; destructive "Remove from watchlist" stays red and last |
| — | — | iOS shows no ⋮ manage menu or "Alerts and more" for guests | Guest/signed-in difference (by design) | Unchanged |

## 2. Files changed
- Core: `watchlist/WatchlistPresentation.kt` (`headerLine`, `headerCaution`, `emptySpokenTitle`, "This watchlist is empty", offline label moved
  to the header); test `watchlist/WatchlistPresenterTest.kt` (+2 tests, 2 updated).
- Shared: `designsystem/components/StockChip.kt` (`StockFadingScrollRow` extracted; pill selector uses it), `StockRow.kt` (`nameMaxLines`),
  `StockStates.kt` (`titleDescription`), `presentation/watchlist/WatchListScreen.kt`.
- iOS: `DesignSystem/StockDetailComponents.swift` (`StockFadingScrollRow`), `DesignSystem/StockComponents.swift` (`nameLineLimit`),
  `DesignSystem/StockControls.swift` (`titleAccessibilityLabel`), `WatchListScene.swift`.

## 3. Shared rule / component changes
`StockFadingScrollRow` (Compose + SwiftUI, same behaviour; built on the existing shared `StockPillLayout.fadesEdge`), the optional name-line
parameter on `StockRow`, the optional spoken title on the shared empty state, and the presenter fields above. All defaults are unchanged, so
other screens are unaffected. No new colours, fonts or spacing values.

## 4. Tests and builds
Sequential run, one command: `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
:app:androidApp:assembleDebug --continue`, then `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator
-destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`. Results (JUnit XML counts): core JVM **441/0**, core iOS **441/0**,
shared Android **109/0**, shared iOS **105/0**, `assembleDebug` OK, iOS **BUILD SUCCEEDED**. (A first attempt of this run was interrupted; these
counts are from the completed run.) Device builds for checks used the same command with `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081`.

## 5. Screenshots (`docs/design/phase5a_1/`)
Android 360 dp: `AFTER-android-360dp-1x-light-populated.jpg`, `AFTER-android-360dp-1.3x-light-populated-header-wraps-between-parts.jpg`,
`AFTER-android-360dp-1x-dark-populated.jpg`, `AFTER-android-360dp-empty-long-name-selector-scrolled.jpg`,
`AFTER-android-360dp-offline-saved-prices-once.jpg`, `AFTER-android-360dp-row-actions-sheet.jpg`, `AFTER-android-360dp-page-end-above-banner.jpg`.
iOS: `AFTER-ios-iphone16-default-light-guest-populated.jpg`, `AFTER-ios-iphone16-xxxl-dark-guest.jpg`, `AFTER-ios-se-375pt-guest-empty.jpg`,
`AFTER-ios-se-375pt-guest-populated-per-row-stacking.jpg`, `AFTER-ios-se-375pt-row-menu.jpg`.
All device settings were restored (emulator size/font/Light theme; iPhone 16 Large/Light); the temporary SE was deleted.

## 5a. Final verification (closure pass, 2026-10-10)

Devices: Android emulator-5554 (1080×2400 override = 360 dp, density 480, Android user build), iPhone 16 simulator (iOS 18.3), earlier pass:
temporary iPhone SE 3rd gen (iOS 18.3, deleted). Local MOCK server only.

| Scenario | Android | iOS |
|---|---|---|
| Header wraps between parts (360 dp, 1.3×) | PASS | PASS (XXXL, iPhone 16) |
| Two-line names / adaptive values | PASS | PASS (iPhone 16, SE) |
| Row actions, red destructive item | PASS (sheet) | PASS (menu, guest) |
| MOCK banner / safe areas, light + dark | PASS | PASS |
| Offline: header once, rows not repeated | PASS (signed in) | PASS (guest) `VERIFY-ios-iphone16-guest-offline-header-once.jpg` |
| Different saved-price dates across rows | Tests only (`offlineRowsKeepOnlyDatesThatDiffer`) | Tests only |
| Missing quote → "No data available", no $0.00, no % | Tests | PASS on device `VERIFY-ios-iphone16-missing-quote-no-data-available.jpg`; average says "3 of 4 stocks with today's prices" |
| Long list name, auto-scroll, empty custom list | PASS (signed in) | NOT RUN (signed-in only) |
| Guest vs signed-in boundaries | PASS (manage menu + Alerts and more only signed in) | PASS (guest: View details + Remove only) |
| Touch targets ≥ 48 dp | PASS (tree: chips 93×48, "+" 48×48, ⋮ 48×48) | Code review (44 pt frames) |
| Accessibility labels | PASS (tree; see §6) | NOT RUN at runtime (no CLI accessibility dump; Accessibility Inspector is GUI-only) |
| TalkBack spoken output | **NOT RUN** — TalkBack was enabled and navigated with keyboard shortcuts, but this user-build emulator has no root, so spoken text can't be captured; its notification-permission prompt was dismissed (not granted) and TalkBack disabled again | — |
| VoiceOver | — | **NOT RUN** (not available in the Simulator) |
| iPhone SE at XXXL | — | **NOT RUN** (the new temporary SE's access request wasn't answered; SE was deleted unused) |
| iOS signed-in (multiple lists, offline signed in) | — | **NOT RUN** (no sign-in on simulators) |

Defects found in this pass: **none** needing code changes. Observations: (1) the watch-data repository reuses quotes younger than 60 s, so a
pull-to-refresh right after going offline still shows "Updated …" until that window passes — existing, intended caching on both platforms;
(2) the Android chip's description node and its selectable node are separate in the tree; TalkBack should read the description, but this wasn't
confirmed by captured speech (risk, not a confirmed defect); (3) the default `xcodebuild` writes the LAN-host MOCK URL into the shared
DerivedData app, so device checks must rebuild with the localhost override before launching (done; nothing contacted the LAN host).

## 6. Accessibility checks actually performed
- Android accessibility tree (uiautomator): offline row reads "AAPL, Apple Inc., $336.67, up $3.04, 0.91% today, Saved prices · as of Oct 7";
  empty title node "This watchlist is empty" with description "Long Term Growth and Dividends is empty"; chips "My Stocks, 5 companies",
  "Long Term Growth and Dividends, 0 companies"; "More actions for AAPL"; 48 dp targets for ⋮.
- Visual large-text checks: Android 1.3×, iOS XXXL (iPhone 16).
- **TalkBack: NOT RUN. VoiceOver: NOT RUN.** (Spoken order not verified with a screen reader.)

## 7. Unverified scenarios
- iOS signed-in (several lists, offline header, long list name): no sign-in on simulators. Same shared presenter and fading row as Android.
- iOS offline state, iOS empty long-named list: signed-in only.
- iPhone SE at XXXL (checked in Phase 4C.3 for Company Details, not re-checked here).
- "No data available" row on a device: needs a fixture without a quote (tests only).

## 8. Remaining limitations
- Screen readers not run.
- The iPhone 16 simulator still has the localhost-configured build installed (device state only).

## 9. Verdict
**PASS WITH CONDITIONS** — A–E fixed on both platforms with shared rules and tests; F and G verified without change; the closure pass found no new
defects and confirmed offline and missing-data behaviour on device. Remaining conditions (environment, not code): spoken screen-reader output
(TalkBack/VoiceOver), iOS signed-in screens and iPhone SE at XXXL. Phase 5B not started.
