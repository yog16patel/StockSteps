# Personalized Home implementation report

Implementation date: 2026-10-08. Commit: **Add personalized Home dashboard on Android and iOS**.
Previous base: `Market and watchlist data updated` (`e559ffe`).

## 1. Existing architecture discovered

StockSteps uses KMP core domain/network/presentation rules, Koin account dependencies,
Compose Android scenes/ViewModels, and native SwiftUI with Observation adapters.
The backend already provides authenticated watchlists and alerts, batch watch data
(quotes plus earnings), company news, and public market data. REAL user data uses
Firestore through the backend; MOCK uses process memory and fixtures.

The previous Home fetched indices, movers, general news, sparklines, and mover
logos, duplicating Markets. Portfolio holdings/trades/returns are not implemented.
Learn currently opens a placeholder destination; there is no lesson-progress repository.
These capability limits are preserved rather than inventing data or another engine.

## 2. Files created

- `core/src/commonMain/kotlin/org/example/stocksteps/home/PersonalDashboard.kt`
- `core/src/commonMain/kotlin/org/example/stocksteps/home/PersonalDashboardStore.kt`
- `core/src/commonTest/kotlin/org/example/stocksteps/home/PersonalDashboardRulesTest.kt`
- `core/src/commonTest/kotlin/org/example/stocksteps/home/PersonalDashboardStoreTest.kt`
- `app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/home/HomeClock.kt`
- Android and iOS `HomeClock` actual implementations under their platform source sets.
- `server/src/main/kotlin/org/example/stocksteps/HomePersonaRoutes.kt`
- Ten JSON fixtures in `server/src/main/resources/fixtures/home/`.
- `server/src/test/kotlin/org/example/stocksteps/home/HomeDashboardTest.kt`
- This report.

## 3. Files modified or retired

Replaced HomeViewModel, HomeScene, HomeScreen, previews and actions in shared Compose;
replaced native HomeViewModel/HomeScene/HomeScreen; retained only the established
Learn banner in both HomeComponents files. Updated AccountDependencies and
IosAccountClient for the shared dashboard; both App navigation owners and Company
Details scenes for routes/history. Updated StockStepsApi, backend Application and
NewsService for explicit non-enriched news. Updated fixture contract tests and
stabilized existing watchlist/alert tests by waiting for repository initialization
and completed UI state, with atomic request recording. InMemoryUserDataCache now
serializes access with a Mutex after a native test crash exposed concurrent mutable
collection access.
Retired IosHomeClient and obsolete market-centric HomeViewModel tests; their Home
responsibilities are tested at the shared store and rules boundary. Updated
PROJECT_HANDOFF.md and README.md.

## 4. Home sections implemented

Compact brand header with Search, Alerts and Settings; local-time greeting;
watchlist highlights; deterministic Daily Brief; upcoming earnings/recent triggered
alerts; compact Learn Basics banner; News for You; recently viewed with Clear.
Empty users get a working Find stocks action, not fake investments. Empty brief,
events and history sections are omitted. Markets retains general market discovery.

## 5. Existing components reused

StockBrandMark, StockCard, StockSectionHeader, StockRow/StockTickerAvatar,
StockPriceChange, loading skeletons, StockErrorState/StockSectionMessage,
StockNewsCard, Learn banner, AdaptiveSinglePane and existing semantic theme tokens.
One lazy vertical scroll per platform; no new animation, chart, nested vertical
scroll or design system. Compose uses keyed sections; native rows have stable IDs.

## 6. Data sources

| Section | Source |
| --- | --- |
| Greeting | Device-local hour, no name inferred from email |
| Watchlist | Existing account watchlists, or existing local guest list |
| Quote rows and earnings | Existing WatchDataRepository batch endpoint/offline cache |
| Daily Brief | VerifiedDailyBrief policy over available personal quote/event facts |
| Alerts | Existing authenticated AlertsRepository.history |
| News | Existing company feed, requested with `enrich=false` |
| Learn | Existing Learn destination, no invented progress |
| Recently viewed | Existing SQLDelight UserDataCache, account/environment namespace |

## 7. Backend/API changes

Home uses existing `/api/v1/me/watchlists`, `/api/v1/me/alerts`,
`/api/v1/stocks/watch-data?symbols=...`, and company news endpoints.
`GET /api/v1/stocks/{symbol}/news?enrich=false&page=0&limit=10` bypasses
NewsSimplificationService entirely, including AI-cache reads/queueing. Defaults on
existing consumers remain unchanged. There is no redundant authenticated dashboard
endpoint or extra database. A MOCK-only read-only route serves development scenarios:
`GET /api/v1/home/personas/{id}`. It is not registered in REAL.

## 8. Mock personas and fixtures

Select Development → Mock in Settings, then use Home's Sample scenario menu.
The menu is unavailable for effective REAL backends; native release builds do not
expose it. Backend fixtures support:

- new-user
- watchlist-only
- portfolio-only
- watchlist-and-portfolio
- learning-only
- upcoming-earnings
- triggered-alerts
- no-news
- stale-quotes
- partial-failures

Portfolio/learning scenarios explicitly explain missing capabilities and never add
holdings or progress. Persona selection is read-only: it does not seed, overwrite,
import or delete saved lists, register push devices, call providers or AI, or write
viewing history. “My saved companies” exits the preview. Changing account or backend
clears the preview. Fixtures use fictional values with the existing sample-data banner.
Normal MOCK account requests still use the existing auth identity and isolated mock
user-data backend, not production Firestore. Firebase Auth itself is not replaced
with an offline mock identity provider.

## 9. Personalization rules

Flatten all lists in saved list/entry order and deduplicate by exchange-qualified
provider symbol. Display at most three rows. Stable-partition fresh moves of at
least 3% absolute ahead of other rows, preserving saved order within each group.
The threshold is configurable in rules/brief policy; it is not a recommendation.
Missing values stay unavailable, never zero. Currency accompanies USD prices.
Offline/stale rows are labeled and cannot generate a current-price Daily Brief fact.

## 10. Portfolio integration

No functional portfolio engine exists. No portfolio card, creation button, fake
balances, holdings, investment returns or performance chart was added. Future
integration must use actual holdings and cash-flow-aware performance calculations.
A watchlist is never represented as a portfolio.

## 11. Watchlist integration

Reuses the existing repositories and exact listing symbol/exchange/currency metadata.
Home does not create a second watchlist system. “View all” opens the Watchlist tab.
New users can open Search and use the existing save flow. Names/notes changes do not
trigger another quote/news fetch when the watched symbol sequence is unchanged.

## 12. Learning integration

Learn Basics opens the existing Learn tab. No fabricated percentages, lesson history,
streaks or “Continue” card. A real lesson/progress engine remains pending; the tab
currently displays its pre-existing placeholder.

## 13. Alerts and earnings

Up to three facts from recent real user alert history and upcoming earnings. Recent
triggered alerts precede future earnings; each group is chronological. Earnings use
exchange-local dates and preserve CONFIRMED versus ESTIMATED and the provider's
session timing/source. Alert items open the stock-filtered Alerts route; earnings
open Company Details. There is no unread/read contract, so the bell has no fabricated
badge. Real push delivery remains outside this implementation's verification.

## 14. Personalized news

Only watched-company feeds are fetched; never substitute general news when empty.
Combine feeds, sort newest first and deduplicate by article identity and normalized
URL (fragment and tracking parameters removed), maximum three. Cards reuse publisher,
time, thumbnail, title and original link. Taps open the original article; existing
company Article Insight remains available through Company Details/Company News.
Home never automatically requests article insights or AI summaries.

## 15. Recently viewed

Record when Company Details actually opens on either platform, not on list impressions.
Retain up to four unique companies, newest first; preserve known listing metadata.
Persist in UserDataCache under `environment|user:uid` (guest separately), key
`home.recent`. Account/backend changes immediately replace visible state. Existing
SignOut.clearAccount removes persisted history and company-news caches for every
environment. Clear affects only the current namespace. Preview personas do not
modify the saved history.

## 16. Navigation changes

Header Search → existing search; bell → all Alerts; profile → Settings;
watchlist View all → Watchlist; company rows/brief/earnings/history → Company Details;
triggered alert → stock-filtered Alerts; Learn Basics → Learn; news → original URL.
Existing five bottom destinations remain Home, Markets, Watchlist, Learn, Settings.
Route identity/arguments, scene ViewModel/state/navigation, and stateless screen
callbacks remain separate on both platforms.

## 17. Authentication and privacy

Existing root Firebase-restoration guard remains; shared state also models initialization.
Watchlist restoration displays loading rather than flashing an empty-list CTA.
Account/environment observations cancel outstanding section jobs, reset all personal
state, and guard responses against a changed owner. Home UI is removed while the
biometric lock is active, in addition to the existing opaque privacy surface and
accessibility protection. No new credentials or Firebase configuration were read.

## 18. Caching and performance

Account-owned shared dashboard store survives scene recreation. Quotes/earnings use
the existing batch endpoint (up to 100 symbols per request) and offline cache.
Quotes, watchlists and alerts refresh on Home return when at least 60 seconds old;
company feeds use a 10-minute TTL. Manual refresh/retry bypasses the relevant TTL.
News requests have maximum concurrency three and reuse the server's cached company
feeds. Final company stories are cached locally per owner and symbol set, labeled
when a saved copy is shown. Partial news failure keeps other available content;
section failures never blank unrelated sections. No polling or automatic AI spending.

## 19. Android build and visual checks

`:app:androidApp:assembleDebug` passes. Installed the debug APK on emulator-5554.
Inspected populated watchlist-only Home in dark theme and a narrow 960px display with
system font scale 1.3; signed changes and company text remained readable. Restored
original 1280×2856 dimensions and font scale 1.0 afterward. No claim of exhaustive
TalkBack, all fold postures or light-theme visual verification.

## 20. iOS build

Full `xcodebuild`, scheme `app.iosApp`, Debug, generic iOS Simulator, Xcode 27.0,
code signing disabled, succeeds. Kotlin's shared framework builds via the existing
Xcode phase. No native iOS screenshot, physical device, VoiceOver or Dynamic Type
interaction was verified.

## 21. Automated tests

Final validation targets:

```
./gradlew :core:jvmTest :server:test :app:shared:testAndroidHostTest \
  :app:androidApp:assembleDebug :core:iosSimulatorArm64Test \
  :app:shared:iosSimulatorArm64Test
```

Core JVM: 125 tests; core iOS: 125; shared Android: 55; shared iOS: 49;
backend: 140 tests, three existing skipped. All executed tests pass. Dashboard tests
cover ordering/thresholds, missing/stale values, fact grounding, currency, earnings
status/order, alert targets, news relevance/deduplication, auth restoration,
account/environment cancellation, isolation/cleanup/history, TTL/retry, batching,
metadata-only changes, section failure, and mock-only previews. Backend tests load
all ten fixtures and prove `enrich=false` does not access AI caches or generators.
Existing alert test now waits for account repository restoration before submitting,
removing a native-dispatch initialization race. Its request log is now atomically
recorded, and the in-memory user-data cache is synchronized for concurrent native
repository access; a native crash was investigated during final regression checks.

## 22. Remaining limitations / next items

- Implement an actual Learn lesson/progress feature before a Continue Learning card.
- Portfolio remains absent until an existing, working holdings engine is available.
- REAL signed-in end-to-end, Firestore, APNs/FCM, paid providers and on-demand AI were
  not exercised. Existing ADC/configuration requirements still apply.
- Complete native iOS visual/accessibility and Android TalkBack/light/fold checks.
- Company feeds are one request per unique watched symbol, concurrency bounded at
  three; large lists may take longer. A server-side news batch can be added if measured
  usage justifies it. No unnecessary new dashboard service was added now.
- Alerts have no persisted read/unread model; no bell badge is shown.
- News cards open original sources; no new standalone personalized-news destination.
- This milestone includes the updated handoff in its commit. Continue updating the
  handoff with validation, limitations and pending work in every subsequent commit.

Local UI verification used a separate MOCK server on port 8094, leaving the existing
8081 process intact, with `adb reverse tcp:8081 tcp:8094`. To return the emulator to
its original server, run `adb reverse tcp:8081 tcp:8081`. Restart/rebuild the normal
mock backend before testing the newly added persona routes there. No production
provider or Firebase user-data service was used for these tests.
