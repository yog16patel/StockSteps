# StockSteps project handoff

Last updated: 2026-10-04. Current update: "Add mobile discovery and route-scene-screen navigation" on `main`.
Previous implementation baseline: `34616b9`.
This file describes the current state, not a request to implement every pending
item. Update this handoff in every commit, including completed work, validation,
limitations, and pending items. Read the actual code and check `git status` before continuing. Update this
file when a feature, architecture decision, or important limitation changes.

## Product and working preferences

StockSteps is a beginner-friendly Android/iOS stock market app. Help users find
stocks, understand price movement and companies, build a watchlist, discover
market movers, and read news. Keep the UI approachable rather than turning it
into a professional trading terminal. Home should remain useful with an empty
or small watchlist through discovery content.

The owner is experienced with Android, Kotlin, KMP, Compose, Flow, and DI but
newer to backend development. Explain backend decisions when useful. Work in
small logical increments; continue the existing architecture rather than
redesigning it. Figma design work exists separately, but the current mobile UI
is an integration UI, not a completed implementation of a supplied Figma design.

Explicit preferences established during development:

- Android: Compose, MVVM, clean layer boundaries, immutable UI state.
- iOS: native SwiftUI, native Liquid Glass where supported, Observation state.
- Reuse domain/networking/theme foundations across platforms.
- Support tablet, split-screen, and foldable window sizes.
- Use separate component files; keep `App.kt` and `iOSApp.swift` light.
- Use named layout constants and theme tokens rather than magic numbers.
- Format Kotlin calls with multiline named arguments and modifier chains.
- Use debounce for search; preserve cancellation of obsolete requests.
- Keep DI out of domain classes and UI components.
- Do not add databases, authentication, Redis, cloud infrastructure, or other
  substantial infrastructure without a concrete requirement.
- Commit/push when requested; do not treat an old request as permission to push
  all future changes.

## Implemented backend

Ktor backend runs locally on port 8080. Flow: route → service → provider
repository → external API → normalized StockSteps model. Provider DTOs stay
inside server integrations. Mobile calls StockSteps only.

| Endpoint | Current provider/behavior |
| --- | --- |
| `GET /health` | Server health text |
| `GET /api/v1/stocks/search?query=apple` | FMP symbol + name search concurrently; USD/CAD only; deduplicated ticker; exact ticker first |
| `GET /api/v1/stocks/{symbol}/quote` | FMP by default; Finnhub when `QUOTE_PROVIDER=finnhub` |
| `GET /api/v1/stocks/{symbol}/profile` | FMP company profile |
| `GET /api/v1/market/gainers` | FMP gainers, descending percentage change |
| `GET /api/v1/market/losers` | FMP losers, ascending percentage change |
| `GET /api/v1/news?page=0&limit=20` | Finnhub current general feed, sorted newest first and sliced locally |

Key details:

- Public quote price is nullable, not fabricated as zero. FMP decimal volume
  is mapped defensively to the public nullable integer volume.
- Quotes/profiles validate and normalize symbols. Search validates queries.
- Public errors contain `code` and `message`; StatusPages handles invalid input,
  missing data, provider failures, timeouts, rate limits, and unexpected errors.
- Provider diagnostics omit bodies and API keys; do not log full provider URLs.
- FMP news returned HTTP 402 for this account, so news was moved to Finnhub.
- News pagination is a slice of a changing current feed, not stable historical
  pagination. General news does not have a reliable single ticker.
- Search makes two FMP calls; either failing causes a public error rather than
  partial results. Movers have no currency field/filter and may include OTC.
- No provider fallback, caching, or automatic retries are implemented.
- Live provider entitlements and Canadian symbol coverage vary by account;
  passing mocked tests does not establish live access for every endpoint.

Server code: `server/src/main/kotlin/org/example/stocksteps/`:
`Application.kt`, `ApiErrorHandling.kt`, `service/`, `repository/`,
`repositoryImpl/`, `repository/models/`, `httpclient/`, and `appconfig/`.
See README for endpoint contracts and validation ranges.

## Implemented mobile architecture

Shared core (`core/src/commonMain/kotlin/org/example/stocksteps/`):

- `model/`: public `StockQuote`, `StockSearchResult`, `CompanyProfile`, `ApiError` used by server/mobile.
- `network/StockStepsApi.kt`: backend-only Ktor API client with injected client/base URL.
- `domain/`: `StockRepository`, `SearchStocks`, `GetStockQuote`, `GetCompanyProfile`, domain error.
- `data/RemoteStockRepository.kt`: implements domain contract, translates network
  errors to safe domain errors, preserves coroutine cancellation.
- Serialized public stock models currently double as domain boundary models;
  no redundant copies exist while their shapes are identical.

Shared app (`app/shared/src/commonMain/kotlin/org/example/stocksteps/`):

- `App.kt`: theme + Home/Search navigation composition only.
- `presentation/stocksearch/StockSearchScene.kt`: composition root, lifecycle
  ViewModel acquisition, lifecycle-aware state collection, saved query/selection.
- `StockSearchViewModel.kt`: immutable StateFlow, query debounce, cancellable
  requests in viewModelScope, quote retry. Results reload after process restoration.
- `StockSearchState.kt`: immutable UI state. Views receive action callbacks.
- `StockSearchScreen.kt`: adaptive pane arrangement.
- `StockSearchPane.kt` / `StockQuotePane.kt`: compact search/inline quote UI.
- `StockSearchSidebar.kt` / `StockQuoteDetail.kt`: dedicated large-screen UI.
- `AdaptiveLayout.kt`: pure pane geometry, window-pixel hinge translation.
- `theme/`: colors, spacing, corners, typography, Compose adapter, layout constants.
- `di/StockStepsDependencies.kt`: Koin 4.2.2 isolated graph. Singleton HTTP client,
  API/repository, use-case and ViewModel factories. Closing graph closes client.
- Platform `BackendClient.kt` implementations use OkHttp/Darwin.

Android entry: `app/androidApp/.../MainActivity.kt` observes WindowManager fold
features while started. Separating or fully occluding hinges avoid content.
At 600 dp usable content width, show sidebar and quote details; sidebar width
is constrained to 280–360 dp. Fold geometry takes priority. Very small fold
panes fall back to the larger side. All policy dimensions live in
`theme/AdaptiveLayoutConstants.kt`. Tablet/foldable sidebar has pinned search,
scrolling results, selected-row styling, and an independent detail surface.

Native iOS (`app/iosApp/iosApp/`):

- `iOSApp.swift`: scene entry only.
- `ContentView.swift`: light entry wrapper; AppScene owns native tabs/models.
- `StockSearchViewModel.swift`: MainActor Observable model, cancellable Swift tasks.
- `StockSearchService.swift`: injected `StockSearchServing` protocol + native
  adapter owning the Kotlin bridge; domain dependencies resolve through Koin.
- `StockSearchScreen.swift`: state-driven search UI, query binding, and selection callbacks.
- `StockQuoteScreen.swift`: state-driven native quote detail UI.
- `Theme.swift`: shared tokens → native colors and Dynamic Type fonts.
- `GlassModifiers.swift`: compiler/runtime guards for iOS 26 glass; material and
  bordered fallbacks on older SDKs/OS versions.
- Native NavigationSplitView adapts between sidebar/detail and compact navigation.
  No Apple-specific physical hinge API/integration is implemented or claimed.
- Kotlin bridge: `app/shared/src/iosMain/.../IosStockStepsClient.kt`; owns Koin
  graph and coroutine scope, exposes search/quote and cancellation to Swift.

Search uses 300 ms Flow debounce on Android and Combine debounce on iOS.
Normalized duplicate queries are skipped. Editing cancels active requests;
clearing input clears results immediately. iOS explicit retry bypasses debounce.

## Local setup and common failures

Server-only environment variables: `FMP_API_KEY`, `FINNHUB_API_KEY`, optional
`QUOTE_PROVIDER` (`fmp` default or `finnhub`). Finnhub key is needed for news
regardless of quote provider. Never store keys in mobile code or this document.
IDE run environments are separate from terminal environments. Check exact
variable names (no leading whitespace) and nonblank values, without printing
secrets. Keys shared in earlier conversation should be rotated; rotation has
not been verified. No .env loader is implemented by AppConfig.

Android debug uses `http://127.0.0.1:8080` with:

```sh
adb reverse tcp:8080 tcp:8080
```

Repeat after emulator/device restart or reconnect. An emulator change lost this
mapping and caused "Could not reach StockSteps"; restoring it fixed the issue.
Check `adb devices`, `adb reverse --list`, and `/health` before changing API code.
Shared Android fallback is `http://10.0.2.2:8080`. Cleartext is debug-only.
Release base URL/HTTPS configuration is still pending.

iOS Simulator uses `http://localhost:8080`; physical devices need a LAN or HTTPS
backend URL. Info.plist allows local networking. Open
`app/iosApp/iosApp.xcodeproj`, scheme `app.iosApp`. Build phase runs
`:app:shared:embedAndSignAppleFrameworkForXcode`. `Configuration/Config.xcconfig`
contains the SDK/configuration-specific Shared framework search path and linker
flag; this fixed `No such module 'Shared'`. Framework exports `core`.

## Verification completed and limits

Previously passed (rerun after relevant changes; these are not perpetual guarantees):

```sh
./gradlew :server:test
./gradlew :core:jvmTest
./gradlew :app:androidApp:assembleDebug :app:shared:testAndroidHostTest
./gradlew :app:shared:compileAndroidMain
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp \
  -sdk iphonesimulator -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/stocksteps-ios-build CODE_SIGNING_ALLOWED=NO build
```

Tests cover backend contracts/provider mapping, backend-only mobile requests,
domain normalization/error/cancellation, adaptive pane geometry, DI graph
resolution/client reuse, and virtual-time debounce/cancellation. Android tablet
UI was installed and visually checked on an unfolded emulator. Backend health
and Apple search returned 200 during connection troubleshooting.

Native iOS builds passed with Xcode 16.2 / SDK 18.2. iOS 26 Liquid Glass branches
were not compiled/visually tested with Xcode 26. Physical folding transitions,
iPad resizing, large-text accessibility, and actual iOS networking/UI flows
still need broader runtime verification. The newest tablet formatting/constants
changes were Android-compiled; a new native build after those changes was not run.

## Pending work and suggested next steps

These are proposals based on the original product direction, not completed
features or authorization to implement everything immediately:

1. Validate adaptive UI on folded/unfolded/tabletop Android, rotation, split-screen,
   and large fonts; verify selection/query survives transitions. Test iPhone/iPad
   native navigation, resize, network failures and quote retry.
2. Build/test native glass with an iOS 26 SDK and supported device/simulator.
3. Company profiles are now integrated into mobile stock details on both
   platforms (included in the current commit). Validate live profile access for the
   current provider plan and unavailable/missing fields on real devices.
4. Discovery/Home is implemented in the current commit. Validate provider
   entitlements, article opening, and mover-to-search navigation on both platforms.
5. Implement a watchlist, then choose persistence according to requirements.
   No watchlist storage or backend database exists.
6. Integrate actual approved Figma designs while retaining native iOS behavior.
7. Configure deployed HTTPS backend URLs, distribution/signing, and CI when needed.
   Backend deployment and release readiness are not completed.
8. Add targeted reliability improvements such as caching/rate-budget management
   when justified; do not hide provider payment/entitlement failures with retries.

Potential improvements to review, not established defects: localized price
formatting on Android, richer typed errors, additional ViewModel race/retry tests,
native Swift service tests, and Android search retry UX. Avoid adding unrelated
financial features until the owner selects the next product increment.

## Recent Git milestones

- `46d56b7`: Finnhub news and safe provider diagnostics.
- `20bae25`: iOS Shared framework search/link fix.
- `b48d1cc`: shared mobile API/domain, MVVM, Koin, native SwiftUI, themes, debounce,
  initial adaptive layouts.
- `34616b9`: dedicated Android tablet/foldable layout, named dimensions, readable
  Compose formatting.

Company profiles were committed as `ad27d2f`; the current commit adds discovery
and navigation separation.
Use Git history/status to verify publication and any newer local work.

## Previous milestone: Add company profiles to Android and iOS stock details

Company profile mobile integration is implemented after `34616b9`. CompanyProfile
was moved into core without changing the backend JSON contract. GetCompanyProfile
is injected through Koin and the native bridge. Android and SwiftUI show separate
company sections with loading/error/retry states. Quote and profile requests run
independently; retrying one does not refetch the other. Profile content currently
includes name, sector, industry, country, and description; logos and website links
are not rendered. These changes and their documentation are included together
in the current commit.

Profile verification: Android build, shared host tests, core JVM tests, and server
tests passed. New tests cover nullable profile decoding and independent profile
retry without discarding/refetching the quote. Live AAPL profile returned HTTP
200. In Swift, Kotlin's profile description property is exported as `description_`
because `description` conflicts with the base object API.

Native iOS simulator build passed after the export naming fix. Android profile
build was installed on the connected emulator for manual testing.

The next suggested product increment after discovery is a persistent watchlist. Runtime adaptive/accessibility validation and
iOS 26 glass verification remain pending. Every future commit must update this
file, as recorded in AGENTS.md.

## Current commit: discovery/Home

Home/Search navigation is added on Android and iOS. Discovery independently loads
gainers, losers, and the first 20 current news headlines. Shows the first five
movers per category with ticker, name, price, and percentage change. Prices do
not claim a currency because the movers contract has none. Tapping a mover
opens a ticker search through the existing USD/CAD search flow (it may have no
match). News opens HTTP(S) source links in the native browser. No full articles
or watchlist functionality have been added. Refresh all and per-section retries
are supported; successful/stale content stays visible during refresh/failure.

MarketMover and NewsArticle moved to core. MarketRepository, RemoteMarketRepository,
and GetMarketGainers/GetMarketLosers/GetMarketNews are injected with Koin. Shared
safe request error mapping is extracted into StockDataRequest.kt. Android uses
DiscoveryViewModel/DiscoveryRoute and separate section components. Swift uses
DiscoveryViewModel (Observation), DiscoveryServing/DiscoveryService, native
DiscoveryView/MarketMoversView, and IosMarketClient. StockSearchScene holds the existing native search navigation; AppScene
coordinates native tabs.
Discovery on Android uses a single hinge-safe region when the window separates;
it is not yet a full tablet dashboard. Existing search retains its dual panes.

Validation: Android build, server tests, core JVM tests and shared host tests
passed, including backend endpoint/nullable-field contracts and independent
feed retry. Live gainers, losers and first news page each returned HTTP 200.
Native iOS simulator build passed. Android build was installed and the Home
screen visually checked on the unfolded emulator with live gainers loaded.
Native iOS runtime navigation/article-opening remains unverified. News remains a current-feed slice, not
stable history; no mobile pagination is implemented.

## Current commit: route/scene/screen separation

The owner requested explicit navigation boundaries. Compose uses typed
Navigation Compose 2.9.2 destinations rather than a Boolean tab switch.
DiscoveryRoute is identity-only; StockSearchRoute carries an optional initial
ticker. AppNavigation registers NavHost destinations, tracks the active
destination, and saves/restores top-level navigation state. Navigation entries
provide ViewModel owners, so scenes have destination-scoped ViewModels.

DiscoveryScene and StockSearchScene acquire ViewModels, collect StateFlow with
lifecycle awareness, restore transient state, and connect event/navigation
callbacks. Route classes contain no composables or ViewModel construction.
DiscoveryScreen and StockSearchScreen own rendering/adaptive geometry and receive
UI state plus callbacks. Initial ticker arguments apply once per restored entry
so recomposition/back navigation does not overwrite later typing.

Native SwiftUI mirrors the boundary: AppRoute identifies tabs; AppScene owns
models and tab navigation; DiscoveryScene/StockSearchScene observe models and
wire callbacks. DiscoveryScreen, StockSearchScreen, StockQuoteScreen, and profile
components receive value UI state and callbacks rather than ViewModels. The
search screen receives a query Binding created by its scene for native input.
NavigationSplitView and TabView remain native to preserve platform behavior.

Validation: Android build and existing shared host tests passed. Native iOS
simulator build passed. Installed on the Android emulator and verified Home →
Search → system Back → Home via the UI hierarchy. Native iOS runtime back/state
restoration and Android process-death navigation restoration remain unverified.
Discovery and the navigation refactor are included together in the current
commit, with this handoff update. Check git status for subsequent local changes.

Next product increment: a watchlist with persistence chosen for the actual
requirements. Remaining verification includes iOS runtime navigation/article
links, process restoration, fold transitions/accessibility, and iOS 26 glass.
