# Global UI Refinement Phase 5A — Watchlist

Status: **committed and pushed** as "Refine Watchlist with compact quote rows, full-name list selector, clear empty and unavailable states and grouped alert actions (Global UI Refinement Phase 5A) on Android and iOS" (2026-10-10). Base: `e08ceb2` (Phases 4C.2 + 4C.3). Reference: the Phase 5A Watchlist
image supplied by the owner (Android/iOS, light/dark, row menu, empty/loading/error/missing-data, long names, watchlist selector).
Device checks ran with approval: **local MOCK server on :8081 only** (no LAN host). Signed-in data lived only in the MOCK server's
memory (no Firebase writes); the iOS guest list is on-device only. Phase 5B (Screener) and 5C (Comparison) have not started.

## 1. Closed first: Phase 4C.3 range-selector fade
- Root cause: the fade started at any overflow, so on the iPhone 16 the 7 ranges (overflowing by a few points of padding) showed "ALL"
  dimmed although it was fully visible.
- Fix: shared `StockPillLayout.fadesEdge(hiddenBeyondEdge, touchTarget)` — fade only while more than half a touch target is hidden. Used
  by Compose (`StockChip.kt`) and SwiftUI (`StockDetailComponents.swift`), so every `StockPillSelector` decides the same way.
- Verified: iPhone 16 "ALL" no longer faded (`AFTER-ios-iphone16-range-all-not-faded-4c3-fix.jpg`); Android 360 dp keeps the cue because a
  whole pill is hidden. Test `ResponsiveLayoutRulesTest.selectorFadesOnlyWhenMoreThanHalfAPillIsHidden`.

## 2. Existing structure and constraints (unchanged)
- Android: `WatchListScene`/`WatchListViewModel`/`WatchListScreen` (Compose); iOS: `WatchListScene.swift` with `WatchlistsModel`. Both
  render the shared core `WatchlistPresenter` model; signed-in lists come from the backend, quotes from one batched `watch-data` request,
  guests keep an on-device list. No API, ViewModel action, navigation or entitlement change was made.

## 3. Before / after

| Area | Before | After |
|---|---|---|
| Header | "My Watchlists" + tagline, three text links that cut off mid-word at 360 dp ("Earnings dates for your wat"), bell and ⋮ | "Watchlist", "5 companies · Updated 5:27 PM ET" (caution colour when offline), search ("Add a company") and ⋮ (new / rename / delete) |
| Selector | "My Stocks 5" chips + "+ New list" | Full-name chips (count spoken), "+" chip; long names scroll, never truncate |
| Summary | Separate card (title, count, average, methodology, updated) | Removed. Count and update time go to the header; the average becomes a sentence in "Your watchlist today": "On average, these 5 stocks moved +0.18% today (equal-weighted; not a portfolio return)." It is shown only for 2 or more priced stocks and says "the 3 of 5 stocks with today's prices" when partial |
| Rows | Price "+ USD" code, percentage only, truncated names | Price (currency in the price for non-USD), "+$3.04 (+0.91%)" with arrow (direction in words for screen readers), full names; values move under the company when they'd crowd it (shared `StockQuoteRowLayout`) |
| Context | — | "Change since the previous close. Prices may be delayed." under the list |
| Missing / loading prices | "—" | "No data available" (never $0.00); placeholder while loading; "Prices couldn't be loaded… Try again" when the request failed (rows stay) |
| Actions | Header links | "Add a company" button + "Alerts and more" navigation rows (My alerts, Earnings dates, Earnings reminders, Compare these companies) |
| Row menu | Starts with Add to portfolio | "View details" first, then Add alert, Add to portfolio, note, move/copy, reorder, red "Remove from watchlist" (guest: View details + Remove) |
| Empty | Text + "Find stocks" | Star icon, "Your watchlist is empty" / "<List> is empty", one-line explanation, primary "Add a company" |
| List error | Text + Try again | "Couldn't load your watchlist" full state with Try again |

## 4. Shared rules and components
- New (shared KMP): `StockQuoteRowLayout.stacksValues` (values beside vs under the company), `StockPillLayout.fadesEdge`;
  core `HomePresentation.signedPrice`; `WatchlistRowModel.changeLine/quoteAvailable`; `WatchlistModel.emptyTitle`;
  `WatchlistPresenter.UNAVAILABLE/CHANGE_CONTEXT/EMPTY_MESSAGE/pricesUnavailableMessage`; spoken row label with direction in words.
- Reused: `StockRow`, `StockCard`, `StockPriceChange`, `StockChip`, `StockNavigationRow`, `StockEmptyState`/`StockStateMessage`,
  `StockErrorState`, `StockSkeleton`, `StockButton`, `StockInsightCard`. No new colours, fonts or spacing values in screens.
- Accessibility: one element per row (Android `clearAndSetSemantics` + "Open details" action; iOS `.accessibilityElement(children: .ignore)`
  + hint); bell and ⋮ are separate 48 dp / 44 pt targets with labels ("More actions for AAPL", "1 active alert for AAPL").

## 5. Files changed
Core: `home/HomePresentation.kt`, `watchlist/WatchlistPresentation.kt`; test `watchlist/WatchlistPresenterTest.kt`.
Shared: `presentation/watchlist/WatchListScreen.kt`, `theme/StockSemanticStyles.kt`, `designsystem/components/StockChip.kt`; test
`theme/ResponsiveLayoutRulesTest.kt`. iOS: `WatchListScene.swift`, `DesignSystem/StockDetailComponents.swift`.
Screenshots: `docs/design/phase5a/` (BEFORE-*, AFTER-*).

## 6. Tests and builds
Sequential run (`./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
:app:androidApp:assembleDebug --continue`, then iOS `xcodebuild … generic/platform=iOS Simulator … build`): core JVM **439/0** (+4), core iOS
**439/0**, shared Android **109/0** (+2), shared iOS **105/0**, `assembleDebug` OK, iOS **BUILD SUCCEEDED**. New/updated tests cover positive,
negative, unchanged and missing quotes, signed amounts and other currencies, long names, empty titles, the prices-unavailable rule (not while
loading, never as an empty list), the average sentence (none for one stock), row stacking and the selector fade.

## 7. Device verification (local MOCK only)

| Check | Android 360 dp | iOS iPhone 16 |
|---|---|---|
| Populated, light | ✅ `AFTER-android-360dp-1x-light-populated.jpg` | ✅ guest `AFTER-ios-iphone16-default-light-guest-populated.jpg` |
| Dark | ✅ `AFTER-android-360dp-1x-dark-populated.jpg` | ✅ XXXL dark `AFTER-ios-iphone16-xxxl-dark-stacked-values.jpg` |
| Large text | 1.3× ✅ values stacked `AFTER-android-360dp-1.3x-light-populated-stacked.jpg` | XXXL ✅ |
| Empty | ✅ default list and long-named list | ✅ guest |
| Offline / saved prices | ✅ `AFTER-android-360dp-offline-saved-prices.jpg` | NOT RUN |
| Row actions | ✅ sheet (signed in) | ✅ menu (guest) |
| Long watchlist name | ✅ "Long Term Growth and Dividends" chip and empty title | — (guest has one list) |
| Narrow iPhone (SE) | — | **NOT RUN** (the temporary SE needs a new device-permission prompt; the shared rule is the same one verified on Android 360 dp) |
| iOS signed-in (several lists, alerts) | — | **NOT RUN** (no sign-in on simulators) |
| "No data available" row | Presenter tests only (no MOCK fixture lacks a quote without changing fixtures) | same |

## 8. Accessibility status
Android accessibility tree checked: rows read e.g. "AAPL, Apple Inc., $336.67, up $3.04, 0.91% today"; chips "My Stocks, 5 companies";
"+" = "New watchlist"; header icons "Add a company", "Manage watchlist". **TalkBack: NOT RUN. VoiceOver: NOT RUN.**

## 9. Known limitations (ranked)
1. iOS narrow (SE) and iOS signed-in screens not checked on a device.
2. The "No data available" and "Prices couldn't be loaded" states are covered by tests only on devices (Android offline showed cached prices instead,
   which is correct behaviour).
3. iOS guest rows briefly showed ticker initials before logos loaded (normal image loading).
4. Screen readers not run.
5. The iPhone 16 simulator still has the localhost-configured build installed (device state only).

## 10. Verdict
**PASS WITH CONDITIONS** — Phase 4C.3 fade closed; the Watchlist matches the reference structure on both platforms with shared rules and
tests. Conditions: iOS SE and signed-in device pass, screen readers.

## 11. Recommended Phase 5B follow-ups
Screener "Discover" cards break words at 1.3× ("Compan/ies", seen in 4C.3); reuse `StockQuoteRowLayout` for screener result rows; apply the
grouped navigation-row pattern to Screener tools.
