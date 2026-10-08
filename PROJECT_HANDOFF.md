# StockSteps project handoff

Last updated: 2026-10-07 (America/Toronto). Current commit: "Add normalized financial history and shared Financials presenter" on `main`.
Previous commit: `fc6d43f` (Complete Company Details reference design on Android and iOS).
This file describes the current state, not a request to implement every pending
item. Update this handoff in every commit, including completed work, validation,
limitations, and pending items. Read the actual code and check `git status` before continuing. Update this
file when a feature, architecture decision, or important limitation changes.

## Start here — Claude / replacement agent handoff (2026-10-06)

This section is the authoritative current snapshot. Later sections retain the
project's chronological history; older endpoint descriptions, test totals and
"current commit" headings may describe earlier milestones. Do not treat historical
pending items as instructions to implement them automatically.

### Repository state and immediate scope

- Workspace: `/Users/yogeshpatel/Documents/StockSteps`; branch: `main`.
- Current commit: **Add normalized financial history and shared Financials presenter**: Financials data layer only (statement history in `/fundamentals`, `FinancialMath`, `FinancialStatementsPresenter`, tests); the Financials UI rewrite is still pending (see "Financials screen" at the end).
- Previous commit `fc6d43f`, **Complete Company Details reference design on Android and iOS**: the full reference layout for Company Details on both platforms, iOS Financials/News destinations, mock gap filling and previews (see "Company Details reference-design pass" at the end).
- Previous commit `af299e9`, **Add Company Details page, mock sample data and Home visual refresh**. Included the canonical Company Details page
  (backend `/details`, `/chart`, `/why-moving`; Android + SwiftUI), mock tooling (`stopMock`,
  `runMock` from `installDist`, importer path fix, chart capture), fixtures recaptured from the
  REAL backend, MOCK `SampleMarketData` fallback for any ticker, the Home reference-matching
  pass (borderless white cards, index sparklines, pill chips, Learn banner, price over change)
  and the read-only market status switch. See the 2026-10-07 sections at the end of this file.
- Previous HEAD: `0d7a19d` — `Add mock/real backend data modes, fixtures and Development setting`.
  That commit added MOCK/REAL backend data modes (server
  `STOCKSTEPS_DATA_MODE`, fixture data source, `/api/v1/meta`, `runMock` on 8081, capture
  script, fixture importer, sample-fixture generator and mixed owner-supplied/synthetic
  fixtures), configurable app backend URLs, the Settings → Development "Backend Data Source"
  switch with per-request URL routing, the "Sample data" banner, and sub-dollar price
  formatting. See the "MOCK / REAL data modes" sections below.
- The user explicitly requested committing and pushing to `main` on 2026-10-07 (twice: the
  data-modes milestone and this one). Each request authorizes that milestone only, not future
  commits, pushes or deployments.
- Check `git status` for any changes made after this commit; do not reset/clean them.
- Read `AGENTS.md`, this snapshot, `docs/COMPANY_DETAIL.md`, and relevant existing
  code before changes. Recheck `git status`; state can change after this handoff.

### Current functionality

Android uses Compose/MVVM; iOS uses native SwiftUI/Observation. KMP shares domain,
networking, use cases, presentation calculations and theme tokens; Koin supplies
shared/Android dependencies. Route files contain identity/arguments only, Scenes
own ViewModels and navigation wiring, Screens render immutable state/callbacks.
Keep entry files light and components separate; preserve native Liquid Glass
availability guards and adaptive tablet/foldable layouts.

Firebase email/password and Google login, restored sessions, offline/cloud
watchlists, Home/market snapshot, AI-simplified news and configurable top bars
already exist. Watchlists preserve listing metadata and exact provider symbols;
legacy entries may lack metadata. Search now returns recognized **US exchanges
and USD only**, superseding the older USD/CAD description below. A `.TO` symbol
must never silently become a US ticker to obtain a quote.

Company Detail has independently loaded Overview, Financials, Valuation and News.
Company news uses Finnhub company news with relevance filtering, not the general
market feed. Financials/Valuation use:
`GET /api/v1/stocks/{symbol}/fundamentals?period=annual|quarter`.

The latest Financials screen is implemented on both platforms: primary value cards,
shared formatting, neutral availability, deterministic insights, expandable
secondary history, one/two-column layout, skeletons and compact section retry
states. Tabs scroll and News is no longer clipped at the tested Android phone
width. Annual/Quarterly selection clears old-period facts. Retrying the same
selection preserves valid sections; failed refreshes label previously loaded data.
No AI generates financial insights or invented qualitative investment ratings.

Backend public responses strip arbitrary diagnostic notes/warnings and use
`TEMPORARILY_UNAVAILABLE`; old availability names decode safely in new apps.
HTTP diagnostics remain internal without credentials/full URLs. Access-denied
financial datasets have a one-hour process-cache cooldown; transient failures
30 seconds. Successful caches retain their existing TTLs/coalescing. Changing
account access may require a backend restart to clear cached denials.

### Data availability and verification limits

Previous live AAPL annual and quarterly checks used the existing localhost server,
without reading credentials. Available: margins, ROIC/ROE/ROA, debt/equity,
current/quick ratios, dividend yield/DPS/payout and current outstanding shares.
Sources are FMP `ratios-ttm`, `key-metrics-ttm`, and `shares-float`.

Statements (income/balance/cash flow, including TTM), annual ratios/history and
historical dividends reported restricted access. Revenue/earnings/EPS history,
YoY/CAGR, balance totals, OCF/CapEx/FCF and buybacks therefore remain unavailable
for that observed account. Do not infer the exact subscription tier, fabricate
values, scrape sites, or reconstruct historical totals from current share counts.
Existing Finnhub integrations supply quotes/news, not financial statements.
Optional ambiguous zero interest coverage is suppressed. Full field mappings,
formulas and observed values are in `docs/COMPANY_DETAIL.md`.

Last completed verification (2026-10-05 session, not rerun for this documentation
update): 58 server cases (3 existing skips), 32 core JVM tests, 20 shared Android
host tests, zero failures. Android APK and shared iOS simulator framework builds
passed. Native SwiftUI compiled in an isolated Firebase compatibility project.
Android emulator visual check confirmed white two-column profitability cards,
friendly Growth empty state and complete tab labels; final APK was installed.

**Production iOS is not verified:** repository Firebase pin 12.19.2 requires newer
supported Xcode (26.2+); installed Xcode at last check was 16.2. The isolated project
uses Firebase 12.11.0 only to check native source compatibility. Do not downgrade
the production pin to imply a production build passed. Native iOS visual acceptance,
physical-device folding transitions and release readiness remain pending.

### Where to continue

1. If asked to validate the current feature, first check the running backend.
   A restart was requested but has not been confirmed; IDE-run servers do not
   automatically reload source. Verify sanitized fundamentals responses and actual
   Annual/Quarterly app behavior before declaring live completion.
2. Complete native iOS/manual acceptance using supported tooling when available.
3. Resolve account-specific statement entitlements only when requested; missing
   provider data is not an unfinished UI mapping. Existing available sections must
   remain useful without a subscription change.
4. Other pending product work: verified split-adjusted share history, peer/industry
   comparisons, documented qualitative thresholds/scoring, price history and
   FFO/AFFO. These are backlog items, not permission to implement all of them.
5. For subsequent work, commit/push only on a new explicit request, and update this handoff in that same
   commit. Review the full mixed working tree and never include local credentials.

Primary code entry points:

- `core/src/commonMain/kotlin/org/example/stocksteps/companydetail/FinancialsPresentation.kt`
- `core/src/commonMain/kotlin/org/example/stocksteps/model/CompanyFundamentals.kt`
- `app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/companydetail/`
- `app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/stocksearch/StockSearchViewModel.kt`
- `app/shared/src/commonMain/kotlin/org/example/stocksteps/theme/FinancialsTokens.kt`
- `app/iosApp/iosApp/CompanyFinancialsView.swift`, `CompanyDetailView.swift`, `StockSearchViewModel.swift`
- `server/src/main/kotlin/org/example/stocksteps/service/CompanyFinancialService.kt`
- `server/src/main/kotlin/org/example/stocksteps/repositoryImpl/FmpFundamentalsLoader.kt`
- `server/src/main/kotlin/org/example/stocksteps/repositoryImpl/FmpFundamentalsMapper.kt`
- Supporting docs: `README.md`, `docs/COMPANY_DETAIL.md`, `docs/AUTH_WATCHLIST.md`.

Useful verification command (run when code changes warrant it):

```sh
./gradlew :server:test :core:jvmTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:shared:linkDebugFrameworkIosSimulatorArm64
```

Keep API keys/tokens out of this document, logs and Git. IDE environment variables
belong to the IDE process; terminal Gradle/server runs do not inherit them. Do not
read IDE credential files simply to troubleshoot unavailable data. No cloud
or credentials access was performed during this handoff-only update.

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
   Watchlist storage is implemented in the latest local account increment below; no financial-data backend database exists.
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

## Previous milestone: discovery/Home

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

## Previous milestone: route/scene/screen separation

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

## Previous milestone: four-tab navigation shell

Owner explicitly limited this increment to navigation/screens, not complete
features. Bottom tabs are Home, WatchList, Learn, and Settings. Android uses the
owner's existing drawable icons: ic_home, ic_watchlist, ic_learn_more, ic_settings.
AndroidNavigationIcon maps public MainDestination values to native resource
painters; App accepts the icon composable from its platform entry point.
WatchList/Learn/Settings have separate typed route, scene, and placeholder screen
files without storage, lessons, or preferences logic. Home retains discovery;
Search opens from Home or mover selection as a secondary destination with Back.

iOS mirrors the four native tabs using SF Symbols and placeholder scenes/screens.
Search is presented from Home in a native sheet with Done; it retains its native
search/detail navigation. AppRoute defines only the four tab identities.

No watchlist persistence, learning content, or settings behavior is implemented
in this increment. Android and native iOS builds passed, and existing shared
Android host tests passed. Android was installed on the emulator; all four labels
were found and WatchList/Learn placeholder navigation was verified. The emulator
disconnected during the Settings check, so Settings is build-verified only.
iOS tab interaction is build-verified only. Next feature work requires a separate
user request.


## Current commit: Figma Welcome V2

Implemented the supplied Figma frame `1:2` from file `4onpJniqUV88MOW33J8ci6`
on Android Compose and native SwiftUI. Welcome is the launch destination, without
bottom tabs. Start exploring replaces it with Home; Android removes Welcome from
the back stack. iOS gates AppScene in ContentView. Completion is session-only,
not stored permanently; Android navigation state survives normal restoration.
No sign-in, analytics, or persistent onboarding preference was added.

Separate WelcomeRoute/WelcomeScene/WelcomeScreen and illustration components
preserve navigation boundaries. Swift has WelcomeScene/WelcomeScreen and
WelcomeInsightCard/WelcomeBenefits. Shared `theme/WelcomeTokens.kt` defines the
Figma palette, dimensions and typography; both platforms use native system fonts.
The Apple price/chart are a static onboarding example, not live financial data.
The reference contains text and shapes, no downloadable image/SVG assets.
Device chrome is supplied by the OS. Layout is scrollable for small windows and
large text, centered with a capped content width on tablets; Android uses the
existing hinge-safe region calculation.

Validation: Android assembleDebug and native iOS simulator build passed. Installed
Android build on emulator-5554, visually compared the unfolded layout with the
Figma screenshot and verified Start exploring opens Home and all four tabs.
Home data requires the existing local backend/adb reverse setup. No iOS simulator
was booted, so native welcome runtime/transition is still unverified. Phone,
large-font and physical fold-transition checks remain pending. Welcome code and
this handoff update are included together in the current commit.


## Latest local work: Firebase accounts and offline watchlist (included in current commit)

Owner approved the architecture plan and implementation, then separately approved
live Firestore rules deployment. Shared domain has User/AuthSession,
WatchlistItem/WatchlistSnapshot, AuthRepository/WatchlistRepository and use cases.
Platform callback gateways hide SDK types. Android uses official Firebase SDKs
(BoM 34.19.0); Swift adapters use official Firebase through SPM pinned to 12.19.2.
The actual iOS package requires Xcode 26.2+; installed Xcode remains 16.2.

SQLDelight 2.4.0 uses Android/native drivers and reactive local queries. Local
rows are keyed by owner and normalized ticker, plus a durable revisioned outbox.
AccountDependencies is app-owned Koin, separate from feature financial services.
ViewModels receive abstract repos/use cases. Android auth has Route/Scene/Screen;
Swift has separate scenes, screens, Observation model and AccountServing service.
WatchList is now real local storage, auth entry, sync status/retry, remove, and
open-stock navigation. Both stock detail UIs have add/remove actions. Settings
supports account state and logout. Passwords are transient; SDKs own sessions.

Guests merge atomically into the first signing-in UID, deduplicate, and consume
guest rows so another account cannot claim them later. Logout switches to guest;
old UID caches/outboxes stay private and are not copied. Tagged reactive snapshots
prevent previous-account rows appearing during state transitions. Authoritative
server snapshots reconcile clean rows; pending local edits win until acknowledged.
Server transactions avoid an independent Firebase offline-write queue. Last
server commit wins across devices; later offline adds can recreate removed stocks.
Retries run on edits, manual retry, reconnect, foreground and bounded 30-second
interval. No background sync guarantee while the app is terminated.

Both owner-provided Firebase config files are present and point to stocksteps,
registered as org.example.stocksteps. Native bundle ID was aligned to that
registration; SQLite linker flag added. Android config is ignored; the owner's
already-staged Apple plist is preserved. No keys are recorded in docs or logs.
Firestore API enabled/default Native database provisioned in nam5 (Standard,
free tier) by the approved rules deployment on 2026-10-04. UID-isolated three-field
rules deployed successfully. No live test accounts/documents created; automated
Firebase tests use emulators only. Ktor/provider implementation stays unchanged.

Validation passed: Android debug build, shared Android host tests, core JVM tests,
native shared framework link, five emulator rules tests, and one Android device
integration test covering guest signup merge, second client sync, offline removal,
reconnect, logout/isolation and cross-UID denial. New unit tests: three auth,
four coordinator race/retry tests, four real SQLite tests. Android UI verified
AAPL add from Ktor details, survival across full process restart, removal and
login/signup entry. The temporary UI stock was removed after verification.
Final device integration rerun passed after the ViewModel/DI refinement; the debug
APK was restored on emulator-5554 and temporary Firebase emulators were stopped.

Native adapter/service/view compilation passed in an isolated temporary project
with Firebase 12.11.0 on Xcode 16.2; this did not change the actual pinned package.
Actual iOS package resolution fails on installed Xcode (requires Swift tools 6.1;
Firebase officially needs Xcode 26.2+). Upgrade/select Xcode, then build/test actual
native SDK/runtime, including session restoration and offline transitions.
Live Android authentication with the owner's account is also pending; emulator
coverage establishes SDK/repository behavior, not production account setup.

See docs/AUTH_WATCHLIST.md for schema, conflict policy, emulator commands, rules,
configuration and exact validation limits. Next product work remains separate:
Google/Apple sign-in, learning/preferences/alerts/portfolio sync are not added.
Account implementation, deployed rules, login UI and navigation changes are
included in the current commit requested by the owner.

## Latest local work: Figma login screen (included in current commit)

Implemented design 40:142 in Android Compose and native SwiftUI. Shared AuthTokens
hold the exact palette, typography sizes and control geometry. Owner selected
existing platform fonts (no bundled Inter). Separate AuthComponents files contain
brand, field/button and divider UI; scenes retain model/navigation wiring. Email
sign-in and signup reuse the existing Firebase implementation. Password Show/Hide
is local UI state, password remains transient, and Continue as guest clears it and
closes auth. Android auth now relies on system back/guest instead of the extra
navigation header. Existing iOS sheet cancellation stays native.

Google and Forgot password controls match the design but show explicit availability
messages; Google credentials/provider setup and password-reset workflow remain
pending. No fake sign-in or reset success is shown. No static image/SVG assets were
provided for this node; its G badge and S brand are text/shapes as specified.
Scrollable capped-width forms support small screens, tablets and Dynamic Type;
Android retains the existing hinge-safe placement.

Validation: Android debug and shared iOS framework builds passed. Native SwiftUI
compile passed in the existing isolated Firebase 12.11.0 compatibility project;
actual project stays pinned to 12.19.2 and still requires newer Xcode. Android
foldable emulator screenshot compared with the Figma reference; Show/Hide,
Create account mode switch, scrolling and guest dismissal verified. Final debug
APK installed. Production auth
and actual iOS runtime are not newly tested by this visual change. Login UI and
account implementation are included in the current commit.

## Login replaces welcome (included in current commit)

Removed the Android welcome route/components, native welcome views and unused
WelcomeTokens. Login is the starting screen on Android and iOS. Sign-in/signup
success or Continue as guest opens Home; Android removes the initial auth route
from the back stack. Auth opened later from WatchList/Settings returns to its
caller. Entry choice is session-local, matching the previous welcome gate;
cold launches show login again. Missing Android account dependencies in previews
fall back to Home. Native root remains light and retains its app-owned account
model and foreground retry.

Validation: Android debug build and shared native framework link passed; native
Swift compile passed in isolated Firebase compatibility project (actual Xcode/SDK
limitation unchanged). Android fresh-process login and guest-to-Home/bottom-tabs
navigation verified. Included in the current commit.

## Google sign-in integration (included in current commit)

Continue with Google invokes Firebase-backed authentication on Android/iOS. Shared
repository has a SDK-free provider action, used through existing account models;
UI loading/errors/completion and UID-based watchlist sync are reused. Android
Credential Manager 1.5.0 + googleid 1.1.1 use GetSignInWithGoogleOption with the
configured web client ID. Provider adapter keeps only a weak Activity reference,
cancels presentation when detached, and clears credential state on logout. iOS
GoogleSignIn 9.2.0 SPM product, client configuration, foreground presentation,
reversed-client URL scheme and SwiftUI URL callback are wired. Tokens stay native
and are neither persisted nor logged. Google SDK logout accompanies Firebase logout.

Owner confirmed Google provider enabled. Owner registered the debug SHA-1 and refreshed google-services.json. Verified
stocksteps/package org.example.stocksteps, Android OAuth client type 1 with exact
debug fingerprint match, and web client type 3. Real Google login still needs
owner account testing. Provider UI opened on Android emulator;
cancellation returned to login with safe feedback. Core tests include provider
completion and cancellation/no false identity success. Android/shared framework
builds passed; iOS compile passed in isolated compatibility project. Actual iOS
Xcode requirement unchanged. Updated-config Android build passed and was installed
for owner testing. Google sign-in code, OAuth callback configuration and this
handoff update are included in the current commit.

## Figma Home screen (included in current commit)

Implemented frame 1:16 for Android and native iOS with shared HomeTokens and
separate HomeScene/HomeScreen/HomeComponents/HomeViewModel files. Existing Home
route identity and four-tab navigation stay in place. Replaced the discovery feed
UI on Home with greeting/search, three index cards, the real saved watchlist and
interactive P/E lesson. Prior discovery feed implementation remains in source
but is no longer the Home destination. Existing native tab chrome and Android
provided icons are reused; OS supplies device/status chrome. Figma contains only
text/shapes, no downloadable static image/SVG assets. Platform fonts preserved.

Watchlist quotes use existing backend/domain through GetStockQuote and a separate
IosHomeClient (independent reads avoid cancelling stock search/detail). Watchlist
symbols come from the UID-tagged account repository/native account state. Loading,
empty and unavailable rows are explicit; no Figma sample stocks/prices are seeded.
Android bounds quote concurrency to three, iOS loads rows sequentially. Quotes
remain transient and are not added to SQL/Firestore. Search/row taps open existing
stock search/detail. Lesson opens local educational content, not the Learn tab.
Scrollable width-capped layout uses hinge-safe Android region and scalable native
fonts. The Market Snapshot implementation below now supplies supported ETF proxy
data for the index cards and adds the three movers sections.

Validation: Android debug/shared native framework builds passed. Shared allTests
passed including Android host and iOS simulator unit runs. New Home test proves
account switch clears old rows and ignores late quote completion; index failure
is independent. Native SwiftUI compile passed in isolated Firebase compatibility
project (actual Xcode limitation unchanged). Foldable emulator Home screenshot
compared with Figma; guest empty state, lesson dialog and search navigation checked.
Populated Home rows/physical fold transitions and native Home runtime need owner
verification. Included in the current Home market snapshot commit.

## Market Snapshot (included in current commit)

Implemented GET /market/snapshot and /api/v1/market/snapshot using one shared
45-second in-memory cache, concurrent section loading, and a backend-only FMP
MarketDataProvider. Cache expiry uses a monotonic clock; concurrent misses are
coalesced. SPY, QQQ and DIA represent S&P 500, Nasdaq-100 and Dow Jones ETF proxies,
with USD prices and signed changes. Top five gainers, losers and most-active
stocks are included. MarketMover now permits missing numeric fields and volume.
No unavailable value is fabricated as zero. Index errors and section errors are
independent, sanitized and returned alongside successful data.

Market hours currently map the provider's regular-session flag to OPEN/CLOSED,
or UNKNOWN when unavailable. PRE_MARKET/AFTER_HOURS remain supported enum values
but are never inferred from local time. Apps use the shared snapshot repository
and use case; Android and native SwiftUI Home render status, prices, timestamp,
partial failure/retry and the three navigable movers sections. Existing watchlist
quote loading stays independent. See docs/MARKET_SNAPSHOT.md for the contract.

Validation: six server snapshot tests and two shared core repository tests passed,
including mapping/null fields, status, partial failures, expiry/coalescing, aliases
and client errors. Shared allTests, Android debug build and iOS framework build
passed. SwiftUI compiled in the isolated Firebase 12.11 compatibility project;
actual Firebase 12.19.2 still requires newer Xcode than the installed 16.2.
Live check on temporary port 8081 used local IDE credentials with explicit owner
approval: CLOSED, SPY price/change, five entries in all three movers sections;
QQQ/DIA returned HTTP 402 from FMP and correctly remained unavailable. Both aliases
returned an identical cached snapshot. No credentials were printed or committed.
Updated Android debug APK installed. Restart the regular backend on 8080 to load
these routes. Native runtime, populated watchlist and physical fold transitions
still need owner verification. Provider plan access for QQQ/DIA remains external.
Temporary preview source/process removed after verification. Included in the current Home market snapshot commit. Next work: owner review of snapshot Home and then
remaining Learn/Settings experiences; preserve route/scene/screen boundaries.

Home snapshot label clarification (included in current commit): DIA now displays “Dow 30” on
both platforms and in the backend response; QQQ already displays “Nasdaq-100”.
Both cards remain present during loading and provider failures. They are ETF
proxies, and the live FMP account's HTTP 402 restriction still prevents their
prices from loading. No fabricated prices or unrelated stock quotes substituted.
Validation: checked backend and both platform fallback labels; diff whitespace
check passed. This label-only adjustment does not change provider requests.

Finnhub ETF fallback (included in current commit): snapshot backend now injects the existing
FinnhubStockProviderRepositoryImpl as an optional quote fallback. Successful FMP
quotes remain unchanged; missing prices or FMP failures try Finnhub once per ETF.
Null/invalid fallback quotes produce sanitized per-card errors; cancellation
propagates. Movers/status and the 45-second cache remain unchanged. Both apps
consume the same response without client changes or client API keys. Live owner-
approved check on port 8081 returned SPY 769.64, QQQ 749.58 and DIA 511.10 with
signed changes and no index errors. This resolves the QQQ/DIA limitation above.
Server regression tests passed, including fallback recovery, preserving successful
FMP quotes, signed changes and null fallback failure. Temporary preview cleaned
up. Restart regular backend on 8080 and refresh Home to receive these values.

Commit validation: Home design, shared snapshot contract/repository, backend cache
and Finnhub ETF fallback, Android/native Home UI, tests and API documentation are
included in “Add Home market snapshot with Finnhub ETF fallback”. Server tests
passed after the final fallback change. Earlier shared allTests, core JVM tests,
Android debug and shared iOS framework builds passed. Actual pinned Firebase iOS
build/runtime still requires newer Xcode; isolated compatibility compile passed.
Xcode's project-file formatting changes preserve existing dependency versions.

## AI News Simplification (included in current commit)

Owner approved the plan including persistent backend SQLite and news on Android/
native iOS Home. Existing /api/v1/news array contract now has provider ID,
description and nullable explanation; original title/source/time/URL are retained.
Finnhub's positive ID and summary are preserved; no full article scraping or
company/ticker fabrication. Shared SimplifiedNews/NewsSentiment are provider-
neutral. Server news package contains AiNewsSimplifier, GeminiNewsSimplifier,
NewsSimplificationService, NewsSimplificationStore/SQLite implementation, and
composition in NewsDependencies; Application only wires the service.

Default model gemini-3.5-flash-lite was verified as stable and supporting structured
outputs in official Gemini docs on 2026-10-04. Config: GEMINI_API_KEY (optional),
GEMINI_NEWS_MODEL, NEWS_DB_PATH (default server/data/news.db relative to process
working directory). Keys stay backend-only. Missing key serves original news.
Storage initialization failure disables AI enrichment and logs only a safe warning.
docs/AI_NEWS.md describes setup and limitations.

Flow: provider feed -> deduplicate -> read stored result -> return immediately;
eligible misses queue in a bounded worker. SQLite persists shared results across
users/restarts. Cache identity uses provider ID or normalized URL/headline hash;
tracking parameters/fragments are removed for URL fallback. Content/publisher/
symbol/model/prompt-version changes yield new cache keys. One worker, capacity
20, up to five enqueues per response, database claim leases, eight-second HTTP and
ten-second job timeout, fifteen-minute failure cooldown. No automatic provider
retry. Pending queue is transient; later reads requeue interrupted jobs. Local
coalescing plus SQLite leases prevent duplicate work for processes sharing the
same file. Cloud Run production must replace this local store with durable shared
storage; per-instance queues are not an account-wide daily budget. No deployment,
Redis, user data sent to AI, or real Gemini calls in tests.

Relevance V1 requires description and deterministic finance/market keywords (or
requested ticker mention for ticker-tagged input). It can skip relevant stories
or admit ambiguous text. Prompt prohibits invented facts, advice, predictions and
unsupported causation; article fields are untrusted data. Structured parsing and
length/enum/confidence validation reject invalid output; thought parts are ignored.
This does not prove factual correctness; real summaries need source comparison
before release. Confidence is not displayed. AI/storage failures preserve original
news and null explanation. Failing news provider retains existing API errors.

Android and native Home load news independently of market/watchlist, with refresh,
loading/error/empty states. New separate NewsCard/NewsSection components show
publisher/time, simplified headline, summary, Why it matters, sentiment, AI-
simplified label and Read original. Older discovery feeds reuse the cards. Route/
Scene/Screen boundaries and existing four tabs remain. iOS Home client has an
independent news operation; no keys enter apps.

Validation: all 35 server tests passed, including nine AI tests for valid/malformed/
missing fields, unsupported sentiment, provider errors/incomplete response,
timeout/fallback, IDs/relevance/DTO mapping, duplicate/cached/uncached work,
SQLite concurrent claims and restart persistence. Core JVM tests, shared allTests,
Android debug assembly and shared iOS simulator framework builds passed. Native
SwiftUI compilation passed using the existing isolated Firebase 12.11 project;
actual Firebase 12.19.2 still requires newer Xcode than installed 16.2. Android APK
installed; unfolded emulator card visually checked with explicit local test fixture
(no real/generated news substituted in source). Preview server stopped and adb
reverse restored to 8080 -> 8080. Live Gemini remains unverified; owner was asked
whether backend key is configured. Do not read new credentials from IDE files
without authorization. Next: configure local Gemini key, restart backend, inspect
real source/summary examples, then owner review and eventual production durable
store/budgets. Included in “Add cached AI news simplification and beginner news cards”.

News description follow-up (included in current commit): Android and native iOS cards now show
the provider description when no AI explanation is available, trimmed and capped
at three lines. Blank/missing descriptions remain omitted; no text is invented.
AI summary and Why it matters still take precedence when present. Restart backend
to get the newly preserved Finnhub description field, and rebuild apps for this
UI adjustment.
Validation for description follow-up: Android build passed and updated APK
installed. Native SwiftUI compatibility build passed using Kotlin description_
export; production Xcode limitation remains unchanged.

Current commit includes AI news pipeline, shared persistent summaries, Android/iOS
news cards, provider-description fallback, tests and setup documentation. Live
Gemini verification and production shared durable storage remain pending.

## Restore account before choosing entry screen (included in current commit)

Firebase already persists and restores accounts, but Android previously hard-coded
AuthRoute and iOS reset hasEnteredApp to false on every launch. Both apps now show
a startup loading indicator until account initialization completes, then go
directly to Home for a restored user, or login for signed-out/new users. Android
chooses the initial route once to avoid resetting navigation on later auth events;
AuthScene also completes when a restored/authenticated user arrives and guards
against duplicate navigation from action and session updates. iOS retains the
in-session entered/guest flag after authentication. Explicit sign-out remains
available; no password, token or local boolean substitutes for Firebase session.
Home/watchlist continue using actual UID-tagged account state and backend values.
Validation: Android debug assembly and shared allTests passed; native SwiftUI
compatibility build passed (production Xcode constraint unchanged). Updated APK
installed. Owner should sign in once, force-close and reopen to verify restoration
with their account; no owner credentials used for testing. Included in the current commit.

## Cloud news summary storage (included in current commit)

Owner requested durable cloud storage. Added server-only Google Cloud Firestore
SDK 3.45.0 and FirestoreNewsSimplificationStore behind the existing interface.
Default NEWS_STORE is now firestore; sqlite remains explicit local-development
mode. Project config NEWS_FIRESTORE_PROJECT_ID falls back to GOOGLE_CLOUD_PROJECT
then stocksteps; database NEWS_FIRESTORE_DATABASE_ID defaults to (default).
Collection newsSimplifications/{cacheKey} stores result JSON, retryAt, updatedAt
and a temporary unique lease owner; full provider news feeds are not persisted.
Firestore transactions coordinate claims across backend instances. Owner fencing
prevents expired workers from overwriting newer results or failure cooldowns.
SDK futures are cancellable with five-second operation deadlines; news cache reads
have a total 1.5-second budget and preserve explanations already loaded while
returning original content for remaining articles. Original news survives cloud
configuration/errors; no silent switch to SQLite when Firestore fails. Cached
results remain readable when GEMINI_API_KEY is absent; new jobs require the key.

MigratingNewsSimplificationStore lazily copies matching existing SQLite summaries
to Firestore on lookup, avoiding another AI call. It is not a bulk migration of
older entries. File is used only if it already exists at NEWS_DB_PATH. Cloud Run
can use runtime Application Default Credentials/service account with Firestore
data permissions. Local setup needs ADC, not Firebase CLI login or mobile config
files. Existing deny-by-default Firestore rules protect this collection from all
mobile clients; server access uses IAM. No rules/IAM deployment or production
Firestore writes performed in this task. Per-instance AI queue limits remain;
account-wide budget enforcement and live Gemini verification remain pending.

Validation: 39 backend regression tests passed (42 total with three emulator tests
skipped in that run); the three Firestore emulator tests ran separately and passed
for concurrent claims/shared results, lease expiry/stale worker fencing and shared
failure cooldown. Six rules tests passed, including guest/authenticated denial of
summary read/list/write/delete. Added tests for cached reads without AI, migration,
slow cache fallback and preservation of partial cached explanations. SDK/emulator
verified only against demo-stocksteps; emulator shut down. docs/AI_NEWS.md updated
with credentials, config and production guidance. Local ADC is not configured and
gcloud CLI is not installed; live cloud activation needs owner setup. No mobile
changes required for storage; login restoration is included in this commit and
separately validated above. Current commit: “Persist AI news summaries in Firestore
and restore signed-in sessions”. Live cloud credential setup remains pending.

## Configurable common top app bar

Added shared core AppBarConfiguration/AppBarAction/AppBarBackButton models with
title, visibility, NONE/BACK/CLOSE, enabled state and trailing actions by ID.
Android's separate presentation/components/StockStepsTopBar renders Material
TopAppBar; AppNavigation wires destination titles and search Back, and keeps the
login header hidden to preserve that design. Native StockStepsTopBar.swift renders
the same configuration through a native toolbar ViewModifier and a Swift factory
helper. AppScene owns root NavigationStack and tab title; WatchList, Settings and
Learn placeholder content no longer contain their own nested navigation stacks.
Auth sheet Close respects busy state; search/sidebar/detail headers use Done, and
iOS compact details preserve automatic navigation back. Route identity files are
unchanged; callbacks stay in scenes/navigation composition. Docs TOP_APP_BAR.md
contains configuration examples. Native toolbar OS appearance is preserved.

Validation: Android debug and shared iOS simulator framework builds passed; native
SwiftUI compatibility build passed (actual Xcode/Firebase limitation unchanged).
APK installed. Emulator Home title and search title/Back inspected; Back returned
to Home and bottom navigation. Shared action callbacks and enabled states are
configurable for future screens. No backend changes.

Android back-icon follow-up: provided ic_back_button drawable now
renders in the common top bar via a platform-supplied composable slot, keeping
Android resources out of common code. AndroidBackIcon uses theme tint and a Back
accessibility label; IconButton preserves enabled state and touch target. Close
and native iOS navigation remain unchanged.

Validation: Android debug build and git diff --check passed; updated APK installed
on the existing Android emulator.


## Saved exchange-specific watchlist listings (included in this commit, 2026-10-05)

WatchlistItem now carries optional name, exchange, currency and exchangeFullName.
Search detail additions pass the complete StockSearchResult on Android and native
iOS. SQL schema migration 1.sqm (v1 to v2) preserves old rows/outbox and adds nullable
metadata; guest merge, snapshots and durable queued writes preserve these fields.
Both Firebase adapters read legacy documents and write optional listing metadata.
Provider symbol remains the identity/document key: exchange-qualified symbols such
as SHOP.TO stay distinct from SHOP. No company-name re-resolution or suffix removal.
Android route arguments carry listing metadata; WatchList/Home and native iOS
saved-item taps open details directly and request fresh quote/profile for the exact
provider symbol. If that provider cannot quote a listing, existing error/retry UI
applies rather than substituting another listing. Legacy items preserve ticker but
unknown metadata; re-add from search to capture exchange. See AUTH_WATCHLIST.md.

Validation: core tests, Android host tests (including metadata round-trip across
guest merge/outbox/cloud and v1 migration), Android build and shared simulator
framework passed. Seven Firestore emulator security tests passed; updated bounded
metadata rules deployed successfully to stocksteps. Native SwiftUI compilation
passed in existing isolated Firebase compatibility project; actual pinned Firebase
still requires newer Xcode as documented. Live cross-device sync/user taps remain
manual verification. Included in the current user-requested commit/push.

Final verification: Android debug APK installed on emulator-5554; git diff --check passed.


## US-only search (included in this commit, 2026-10-05)

StockService search now requires USD currency and a recognized US exchange. CAD,
foreign USD listings, and missing exchange/currency results are excluded. The
shared backend filter applies to Android and iOS, preserving exact-match ranking
and deduplication. Existing saved Canadian watchlist items are retained; their
provider access limitation remains. US search visibility does not guarantee all
symbols are available on the provider subscription. Pending: Canadian provider
coverage, live verification, and commit of this plus listing-metadata work.

Validation: backend regression tests and git diff --check passed. Restart the backend
to apply the filter; both mobile platforms consume the same search endpoint.


## Beginner company details (included in this commit, 2026-10-05)

Inspection completed before implementation: existing KMP use cases/networking,
Android lifecycle ViewModels/Koin, native Observation, shared theme, hinge-aware
panes, watchlist and NewsCard components reused. No detail-specific Figma frame or
existing chart library was present. Implementation/data mapping notes and candidate
provider endpoints are in docs/COMPANY_DETAIL.md (includes available/missing table).

New core/companydetail immutable models/glossary/presentation mapper keep education,
formatting, missing-input behavior and guarded historical comparisons out of views.
StockQuote now carries nullable real FMP marketCap; Finnhub remains nullable.
Android presentation/companydetail contains header, sticky four tabs, snapshot,
metrics, reusable education sheet, independent chart states/renderer and grouped
financial/valuation content. Compact selected details use the whole pane instead
of a 240dp nested card; existing fold/tablet sidebar geometry remains. Native
CompanyDetailView/Components mirror the structure with Observation actions,
shared mapper, pinned tabs, reading-width cap and native Glass watchlist buttons.
Logo rendering uses Coil 3.6.3 Compose/Ktor and native AsyncImage. Entry files stay
light; scenes wire callbacks. Preview-only fixtures explicitly identify sample data.

New GET /api/v1/stocks/{symbol}/news routes to Finnhub company-news for the latest
30 UTC days (max20, sorted/deduplicated), validates ticker/URLs/time, and reuses
NewsSimplificationService's existing backend cache/AI enrichment. Both platform
models cancel old company news on selection and handle failures independently of
quote/profile. No client provider keys, production fixtures or fake financial
scores/risks were added.

Data limitations: current integrations do not supply verified financial statement
series, historical prices/valuation/industry comparisons or a scoring/risk model.
These remain explicitly unavailable, with educational rows and disabled period
controls. Overview shows available metrics only; snapshot is unassessed. This is
the usable detail UI foundation, not a claim that historical/financial integrations
are finished. Remaining: connect validated provider financial/history sources,
period-aware trend/valuation charts, published scoring/risk methodology and provenance,
live company-news access checks, iOS runtime/accessibility and physical fold testing.

Validation: core presenter tests pass (5 cases), shared tests cover news cancellation,
partial failure and existing independent quote/profile retry; backend tests cover
company-news exact symbol/date parameters, safe upstream failure and invalid symbol.
Android build/shared simulator framework and native compatibility compile passed.
Eight Compose previews added for normal/profitable/loss/missing/loading/error/dark/
large layouts. Android emulator fixture verified compact header, tabs, Financials
and Valuation missing-data states; no AndroidRuntime crash reported. Education-sheet
state transitions are tested; manual sheet/news interaction checks remain. Fixture
server stopped, adb reverse restored to tcp8080→tcp8080, app restarted to clear sample
state, and no watchlist writes performed. Actual iOS Firebase pin still requires
newer Xcode; local compile used existing isolated compatibility project.

Final validation: final Android build/tests, shared iOS simulator framework and
native compatibility compile passed. Latest Android APK installed; git diff --check
passed. Backend restart needed for the new company-news route. Included in the current user-requested commit/push.


## Company-news relevance fix (included in this commit, 2026-10-05)

Live local AAPL response confirmed Finnhub feed includes broad/competitor items;
mapper previously assigned AAPL to every result without a relevance check. DTO
now keeps related-symbol metadata. CompanyNewsRelevance requires exact related
membership when supplied, plus bounded ticker evidence in headline/summary or a
verified company name in headline. Incidental summary-only names are excluded;
short word-like tickers require stock notation. Generic company identity comes
from Finnhub profile2 with exact ticker validation, process-local bounded cache
(256 entries, six-hour success/five-minute failure TTL), and cancellation-safe
failure fallback to ticker evidence. Filtering precedes truncation and AI cache
lookup, applies equally to both mobile platforms, and leaves general news alone.
Limitations: deterministic relevance, not primary-subject classification; relevant
multi-company comparisons can remain, aliases and name-only items can be missed
when profile access fails. Restart the IDE backend to apply this change.

Optional identity lookup has a two-second budget and runs alongside the news
request; parent request cancellation still propagates.

Validation: backend regression suite passed, including four pure relevance tests
and integration checks for filtering before the limit, identity caching and
profile failure fallback. git diff --check passed. Live updated endpoint awaits
backend restart; included in the current user-requested commit/push.

## Financials and Valuation mapping — implemented, included in this commit (2026-10-05)

User approved the mapping/implementation plan. Added provider-neutral
`CompanyFundamentals` contract, grouped financial facts and valuation/history,
source classification, nullable integer amounts/ratios, reporting metadata and
per-dataset availability. Server extends existing FMP repository/client; helper
DTO/loader/mapper files keep FMP fields off mobile. Added
`GET /api/v1/stocks/{symbol}/fundamentals?period=annual|quarter` and financial
service. Android shared MVVM/DI/use case and native SwiftUI/Observation bridge
load this independently of quote/profile/news, retry/cancel obsolete selections,
and switch annual/quarterly statements. Shared presenter populates Financials,
Valuation/key metrics and evidence-based Overview snapshot; no invented scores.

Implemented deterministic YoY/CAGR, fallback FCF with negative CapEx convention,
FCF margin, historical valuation windows/sample counts/ranges/dispersion flags,
forward FY P/E with matched currency and consensus EPS, dividend/buyback/current
share mappings, and measured growth/margin/debt/FCF context. Annual historical
ratios use stable property names (not legacy aliases). Quote/profile reuse caches;
fundamental dataset TTLs range from 5min to 24h with 30s failed-load cooldown,
bounded keys and coalesced misses. Flexible optional-number/year decoding prevents
one malformed metric from dropping a full statement. 402/403 restrictions stay
explicit; successful sections survive provider failures.

Validation: server/core/shared host tests and Android APK/shared iOS simulator
framework builds passed during implementation; native SwiftUI compiled in existing
isolated Firebase compatibility project. Additional final verification recorded
below. The production Firebase pin/Xcode constraint remains unchanged. Actual
account entitlements/live provider responses are not verified without approved
local credential access; do not equate fixture coverage with live access. Backend
restart is required for existing IDE-run server to expose the new endpoint.

Remaining: verified actual historical shares/split-adjustment basis (`sharesChange5`
stays unavailable), industry/peer averages, scoring methodology, FFO/AFFO, price
history, production iOS build with supported Xcode, and live/manual UI acceptance.
Dividend-growth coverage assumes complete provider events within the fetched
window and includes special dividends. Full mappings/formulas/cache policy and
limits are in `docs/COMPANY_DETAIL.md`. All earlier pending work is preserved and included in this commit;
this implementation is included in the current user-requested commit/push.

Final financials verification: 57 server tests (3 pre-existing skips), 24 core JVM
tests, and 20 shared Android host tests passed with zero failures. Android APK
and shared iOS simulator framework built successfully. Native SwiftUI passed
the existing isolated compatibility build; production Firebase/Xcode limitation
remains. The API distinguishes confirmed `NO_DIVIDEND` from `MISSING`.

## Financials UX redesign (2026-10-05, included in this commit)

Replaced repeated unavailable/diagnostic metric rows with beginner-focused shared
Financials presentation and separate Android/SwiftUI components. Numeric domain
facts remain numeric. `FinancialsPresentation.kt` owns display formatting, neutral
metric availability, partial/empty/loading/error section states, safe growth/net
margin explanations, expandable history and cash-flow reconciliation. ViewModels
map this state; screens render state and callbacks. Existing Route/Scene boundaries
and independently loaded quote/profile/news remain intact.

Both platforms have one/two-column cards with shared `FinancialsTokens`, neutral
text, theme surfaces, blue information links, directional movement colors and
large-text single-column layouts. Growth prioritizes revenue/net income/EPS;
profitability prioritizes margins/ROIC; health prioritizes cash/debt/ratios;
shareholders distinguish confirmed non-payers from missing/zero yield. Skeletons
and compact section retry cards replace rows of missing text. Android tab padding
was reduced after the emulator exposed a clipped News label; both tab rows remain
horizontally scrollable. Annual/Quarterly controls sit directly below tabs.

The backend service strips arbitrary fact/history notes and warnings at the public
boundary; provider access/network failures become `TEMPORARILY_UNAVAILABLE`.
Legacy availability strings decode into that neutral enum for older IDE servers.
Internal safe HTTP logs retain diagnostic statuses. Ambiguous interest-coverage
zero is suppressed unless supported by trailing interest expense. Typed cash/debt
YoY context uses matched balance dates/period/currency. Access-denied loads now
cool down for one hour, transient failures 30s, with existing bounded/coalescing
cache and successful TTLs. Account configuration changes can clear this process
cache by restarting the backend; app retry cannot unlock provider entitlements.

Live AAPL annual and quarterly responses were checked through the existing local
backend without reading credentials: profitability/ROIC/ROE/ROA, debt/equity,
current/quick ratios, dividend yield/DPS/payout and current shares are available.
Income/balance/cash-flow statements (including TTM), annual ratio history and
dividend history reported restrictions. Thus totals/growth/CAGR/cash flow/buybacks
remain absent. Correct stable endpoint paths were confirmed against FMP docs.
Exact account tier/HTTP status within the old grouped 402/403 classification remains
unknown. FMP plan docs distinguish annual versus full fundamentals; account-specific
entitlement needs the dashboard. Existing accessible ratios supply valid values,
but current shares/quote data cannot reconstruct historical totals. Finnhub's
existing integration only supplies quote/news; no unverified financial fallback,
scraping, AI interpretation or fabricated financial values was added.

Validation: 58 server cases (3 existing skips), 32 core JVM tests, 20 shared Android
host tests, zero failures; Android APK and shared iOS simulator framework built.
Native SwiftUI compatibility build succeeded with final responsive cards and
refresh handling; emulator acceptance is recorded below. Production
Firebase/Xcode constraint remains: use supported newer Xcode for the actual pinned
Firebase version; the isolated compatibility project does not change that pin.

Added eight shared presenter/formatter regressions and cache cooldown expiry test;
extended public-response sanitization, matched cash/debt YoY and coverage-zero
cases. New Android previews cover partial/empty/loading/loss/dark/tablet. Full live
values, sources, restrictions, calculation rules and changed file inventory are in
`docs/COMPANY_DETAIL.md`. All earlier pending changes are preserved and included in this commit. This redesign is included in the current user-requested commit/push.

Pending: backend restart to serve neutral public fields/new context; manual iOS
acceptance and production build with supported Xcode; verified statement access;
share-count history/split consistency; peer averages, documented qualitative
thresholds, scoring, FFO/AFFO and price history. Current UX supports available
metrics without depending on these pending integrations.

Financial retries now preserve previously loaded sections on both platforms;
missing sections can show skeletons independently. A failed refresh is labeled once
inside Financials as previously loaded data. Company/period changes still clear
old facts. Shared presenter and existing Android ViewModel regressions cover this.

Final acceptance: final native SwiftUI compatibility build passed; extended Android
ViewModel refresh/period regression passed. Emulator visual inspection confirmed
all four tab labels fit without clipping, Apple profitability renders a two-column
white-card grid (48.7% gross / 33.2% operating / 27.6% net / 51.9% ROIC), and missing
Growth is one compact friendly state. Main values are neutral, education is blue,
with no repeated provider diagnostics. Screenshot: `/tmp/stocksteps-financials-phone.png`.
Only optional interest-coverage zero is additionally omitted for compatibility with
older running servers. Other final totals: 58 server cases / 32 core JVM / 20 shared
host tests, zero failures (3 existing server skips). iOS simulator framework and
Android APK build successfully. Visual checks used the existing live localhost
server; no backend restart/secret access/cloud deployment occurred. Native iOS
manual visual acceptance is still pending; compilation was in the isolated
compatibility project, not a production build with the actual Firebase pin.


## Commit milestone — 2026-10-06

Commit title: **Add beginner company details and financials across Android and iOS**.
Includes the previously pending listing-specific watchlist/search/detail/news and
Financials work plus the Claude handoff. Existing validation remains 58 server
cases (3 skips), 32 core JVM and 20 shared host tests with zero failures, Android
APK/shared iOS framework success and native SwiftUI compatibility compilation.
No application code changed during commit preparation; whitespace and changed-file
credential-pattern checks passed. This does not replace production iOS validation.
Remaining limitations and next steps are recorded in the authoritative snapshot
above. Backend restart has not been confirmed; no deployment is part of this commit.

## Design system foundation + Home redesign (2026-10-06, included in current commit)

Owner decisions: iOS stays native SwiftUI and **mirrors** the shared design (no
Compose on iOS); keep the current four tabs (Home, WatchList, Learn, Settings) —
the reference's Markets/More tabs have no destinations yet. The canonical reference
image was not received; the implementation follows the written token/component spec.

Shared tokens (`app/shared/.../theme/`): new light/dark `ThemePalette` (spec values,
navy dark theme, legacy getters kept), plus accessible text-role colors
`primaryText`/`positiveText`/`negativeText` because the brand blue/green/red fills
are below 4.5:1 for small text on white. 11-step type scale with tabular-digit
number styles, 4pt spacing, `ThemeCorners`, `ThemeDimensions`. `HomeTokens` removed.
The palette switch changes the app-wide primary from green to blue on both platforms.

Compose design system (`designsystem/`, all `internal`): `StockStepsTheme(mode)` with
Light/Dark/System + CompositionLocals (`StockStepsTheme.colors/typography/spacing/
dimensions/shapes`) and MaterialTheme mapping for unmigrated screens; components
StockCard, StockSectionHeader, StockButton, StockChip, StockPriceChange, StockRow
(+avatar/skeleton), StockInsightCard, StockNewsCard (+skeleton), StockSearchEntry
(opens Search; editable bar deferred to Search migration), StockBottomNavigation,
StockDivider, StockLoading/Empty/ErrorState. Shared strings in Compose resources
(`composeResources/values/strings.xml`, `Res` in `org.example.stocksteps.resources`).
SwiftUI equivalents: `iosApp/DesignSystem/StockComponents.swift`, `StockNewsCard.swift`,
updated `Theme.swift` (`StockColors`, medium weight, monospaced digits).

Shared presentation in core: `home/HomePresentation.kt` (section status, mover
category, percent/price formatting, direction from rounded value, no currency claimed
for movers) and `news/NewsPresentation.kt` (`NewsUiModel`, relative time, drops
descriptions that echo the headline). Used by Compose and Swift.

Home (both platforms): brand header → search entry → Market Snapshot card (3 columns,
stacked on narrow/large text, status pill, ETF-proxy note) → Today's Movers with
local chip switching (no extra requests) → Your Watchlist (kept existing feature) →
Learn card (navigates to Learn tab; P/E dialog removed) → up to 3 news cards.
Sections load/fail independently with skeletons and calm retry messages; no
provider errors shown. No market-summary sentence, notification bell or "See All"
(no domain rule / destinations exist). Android shell now uses Scaffold (insets
applied once), shared bottom navigation, hidden title bar on Home; `AdaptiveSinglePane`
uses `safeDrawingPadding` (gesture insets no longer pad content on any screen using it).

Validation: 58 server (3 skips), 43 core JVM (11 new), 21 shared host (1 new) tests,
zero failures; Android APK + iOS simulator framework built; SwiftUI compiled in the
isolated Firebase 12.11 compatibility project. Android emulator: light, dark, scrolled
and 1.5× font Home checked with live backend data. Not verified: iOS runtime/visuals
(no simulator run), small-phone/tablet devices, TalkBack/VoiceOver passes, production
Firebase pin build (Xcode 26.2+ still required).

Next: owner review of Home; then migrate Watchlist, Stock Detail, Financials, News,
Learn, Search in order, deleting `AuthTokens`/legacy token aliases as screens move.

### Home visual refinement pass (2026-10-06, included in current commit)

Density pass on the same state/data/navigation. Tokens: section gap 20→16, chip 32→28
(48 touch kept), bottom bar 64→56 (+ system inset), new `bodySemiBold` ticker style.
Shared components: StockRow (semibold ticker, 8dp logo gap), StockChip (surface +
subtle border unselected; container, no border selected), StockSearchEntry (16dp glyph,
12dp radius). Home: tighter snapshot (2dp hierarchy gaps, 8dp divider, tertiary ETF note,
tiny status pill), 4 movers (`MAX_MOVERS`), watchlist preview of 3 with "See All" →
Watchlist tab, neutral "— / Price unavailable" rows, 8dp top inset. Tab label is now
"Watchlist" (shared Compose string resources for nav labels; iOS label/title too).
Markets, More and News destinations do not exist, so no "See All" on movers/news and
the four-tab structure is unchanged; no "why the market moved" data source exists.
Validation: same 58/43/21 test totals, zero failures; APK + iOS framework built;
SwiftUI compatibility build passed; Android emulator light/dark checked, See All
navigation verified. iOS runtime still unverified.

### Home reference-matching pass (2026-10-06, included in current commit)

Owner-specified structure (reference images were not received in the session; the
written target was followed): brand row ("S" tile from the login design + wordmark),
greeting + "Learn · Explore · Grow", two compact index cards (S&P 500 + Nasdaq-100 via
`HOME_MARKET_CARDS`; TSX is not provided by the backend) with one tiny footnote for
market status + ETF-proxy disclosure, Today's Movers with strong-blue chips (PrimaryDark
for 4.5:1 white text) and 3 borderless compact rows (`MAX_MOVERS = 3`), warm
`educationContainer`/`educationAccent` Learn card with the shared `ic_learn` vector
(copied into composeResources from the Android drawable), and 3 compact news rows.
Removed from Home only: search entry, Market Snapshot heading/card, status pill,
watchlist preview. StockRow gained compact density, padding control and an optional
sparkline slot (unused: movers carry no price history); StockNewsCard compact is now a
borderless row; StockInsightCard has INFO/EDUCATION tones and a leading icon slot.
No bell (no notifications), no "See All" (no Markets/News destinations), no Markets/More
tabs, no user name (User has no display name), no time-of-day greeting (no shared local
time support). HomeViewModel unchanged except market card limit; it still fetches
watchlist quotes Home no longer renders (cleanup candidate). Search is now reached from
the Watchlist screen, not Home. Validation: 58 server (3 skips) / 44 core / 21 host tests
pass; APK + iOS framework built; SwiftUI compatibility build passed; Android emulator
light/dark checked. News failed twice immediately after APK reinstall, not in 3 cold
starts; Try again recovered. iOS runtime unverified.

### Mover logos and sparklines (2026-10-06, included in current commit)

Backend: `MarketMover.logoUrl` from FMP's public image CDN (same path as the profile
`image` field; verified 200 for AAPL/OLB/MOBX, 404 for unknown tickers → client
fallback). New `GET /api/v1/stocks/{symbol}/sparkline` → `Sparkline(symbol, closes,
sessionDate)` from FMP `/stable/historical-chart/5min`, latest session only, oldest
first; cached 5 min, access denials 1 h, other failures 30 s; 404 when fewer than two
points. Mobile: `GetSparkline` use case/repository/API; Home requests sparklines only
for the 3 visible movers, once per symbol until refresh, failures leave no line.
`StockSparkline` (Compose Canvas / SwiftUI Path) draws real closes only; logos render
on a light `logoContainer` tile with ticker fallback. Tests: 63 server (3 skips),
45 core, 21 host pass; APK, iOS framework and SwiftUI compatibility build pass.
Pending: restart the IDE backend, then confirm live FMP intraday access for this
account (endpoint may be plan-restricted; Home then simply omits lines).
Follow-up: Home also fetches each visible mover's company profile (existing endpoint)
when the snapshot has no `logoUrl`, so logos work against older running backends;
backend logo URLs now use the profile's CDN (`images.financialmodelingprep.com/symbol/`).
Verified on the Android emulator: real logos render (OLB, FRGT, ARAI). Sparklines still
need the backend restart. Tests: 63 server / 45 core / 21 host pass; SwiftUI build passes.

### Home section surfaces (2026-10-06, included in current commit)

Movers and Recent News are each one `StockCard` section surface (flat rows + dividers
inside, no per-row cards); market index cards and the warm Learn card stay standalone;
gap between sections 12dp (`sectionGap`). Header action standardized to "View All"
(`action_view_all`). Owner chose to hide View All until destinations exist: HomeScene
exposes `onViewAllMovers`/`onViewAllNews` (null today → not rendered; Swift mirrors with
optional closures). Compact news rows always show a 48dp tile (cropped photo or neutral
placeholder). Removed the unused legacy SwiftUI `HomeNewsSection` from NewsCard.swift.
Validation: 63/45/21 tests pass; APK, iOS framework and SwiftUI compatibility build pass;
Android emulator light/dark checked. Sparkline endpoint still 404 on the running IDE
backend until restart.

### Market status indicator (2026-10-06, included in current commit)

Shared `MarketStatusIndicator` (Compose + SwiftUI): 8dp dot (`statusDot`) + 12sp label in
TextSecondary, in a compact "Market" header above the index cards. OPEN → positive dot,
CLOSED → negative dot, PRE_MARKET/AFTER_HOURS → primary (informational) dot, UNKNOWN →
nothing. Status comes only from the backend snapshot flag (never the device clock).
Screen readers hear one label ("Market closed"); the dot is decorative. The footnote
now carries only the ETF-proxy disclosure. Validation: 63/45/21 tests pass; APK, iOS
framework and SwiftUI compatibility build pass; Android emulator light/dark checked and
TalkBack description "Market closed" confirmed in the accessibility tree.

### FMP rate limit observed (2026-10-06, included in current commit)

After the owner restarted the IDE backend, the sparkline route existed but FMP returned
HTTP 429 for every FMP call (profile, search, sparkline, snapshot movers/status); quotes
still worked via Finnhub. Likely daily plan quota exhaustion from a day of development
traffic plus uncached profile lookups. Mitigations added (backend, needs another restart):
profiles cached 24 h (including not-found), and profile/sparkline rate-limit responses
cached 10 min (`providerCooldown`). Intraday sparkline entitlement is still unverified.
Server tests: 65 (3 skips) pass.

## Settings screen (2026-10-06, included in commit "Setting tab added.")

Structure: header, Account, Appearance, Notifications, About, Sign Out (Data & Display
intentionally omitted). Compose: `SettingsState.kt` (UiState, `SettingsAccount`
Loading/Guest/SignedIn, `SettingsLink`, `SettingsAction`), `SettingsScene.kt`,
`SettingsScreen.kt`, `SettingsPreviews.kt` (light/dark/guest/large font). New shared
primitives: `StockSettingsRow`, `StockSegmentedControl`, `StockAccountAvatar` (placeholder
only; `imageUrl` reserved), `designsystem/icons/StockIcons` (Material path vectors, no icon
library). `StockButton` gained an optional icon and a bordered subtle destructive variant.
SwiftUI mirror: `SettingsScene.swift`, `SettingsScreen.swift`,
`DesignSystem/StockSettingsComponents.swift` (SF Symbols).

Theme: `settings/ThemePreferenceStore` (`ThemeMode` LIGHT/DARK/SYSTEM, key
`stocksteps.themeMode`). Android `AndroidThemePreferenceStore` uses SharedPreferences;
`App(themePreferences, appVersion)` observes it at the root and `MainActivity` keeps
status/navigation bar icons in sync. iOS root `ContentView` uses `@AppStorage` with the same
key and `.preferredColorScheme` (nil for System). No new libraries.

Account: auth model has email only, so title "Account" + email; guests see "Sign in to sync"
(opens existing Auth) and no Sign Out. Sign out uses the existing AccountViewModel →
`AuthRepository.signOut()` after a confirmation dialog; existing behavior switches to guest
mode (no forced login screen). Version from Android `BuildConfig.VERSION_NAME` / iOS
`CFBundleShortVersionString`. No destinations exist for Account detail, Price Alerts, Market
News, About, Privacy, Terms or Help: rows show "Coming soon"; `SettingsScene(links = ...)`
enables them when added. No URLs, emails or notification permissions were introduced.

Validation: 65 server (3 skips), 45 core, 23 shared host tests pass (2 new theme-store
tests); APK, iOS framework and SwiftUI compatibility build pass. Android emulator verified:
light/dark rendering, Dark persists across a cold restart (new PID), System follows OS
night-mode toggle live, sign-out dialog opens and Cancel keeps the session, 1.5× font
stacks theme options without clipping. iOS runtime not verified.

## MOCK / REAL data modes (2026-10-07, included in current commit)

Owner approved: fixtures captured from the REAL backend's public API, a "Sample data"
banner, and empty/errors/slow scenarios deferred. Backend: `appconfig/DataMode`
(`STOCKSTEPS_DATA_MODE=real|mock`, default real, mock refused when `K_SERVICE` is set);
`Application.module` builds a `DataSources` set once (real: FMP/Finnhub/news service as
before; mock: `repositoryImpl/fixture/FixtureMarketDataSource` implementing the stock,
market-data, news and price-history provider interfaces, no keys/network/Gemini/Firestore).
Fixtures layout and age-shifted news times documented in the class. `GET /api/v1/meta`
→ `BackendInfo(dataMode)`. `PORT` env honoured. `:server:runMock [-Pport=]` Gradle task.
`scripts/capture-fixtures.sh` captures from a running REAL backend (refuses mock, writes
only HTTP 200s, stops on rate limit, merges search queries, writes `manifest.json`).

Apps: `GetBackendInfo` (core) → Android `BackendInfoViewModel` + `StockSampleDataBanner`
above the bottom bar; iOS `IosBackendInfoClient` + `SampleDataBanner` bottom inset.
Backend URL is configuration: Android `stockstepsBackendUrl` Gradle property →
`BuildConfig.BACKEND_URL` (debug default 127.0.0.1:8080, release empty); iOS
`STOCKSTEPS_BACKEND_URL` (Config.xcconfig) → Info.plist `StockStepsBackendURL`.

Validation: 69 server (3 skips; 4 new MockModeTest incl. fixture contract check), 45 core,
23 host tests pass; APK, iOS framework and SwiftUI compatibility build pass (built
Info.plist resolves the URL). Mock server ran on 8082/8083 without keys; capture script
refused it; Android emulator showed the banner and empty states against mock and no
banner against real (adb reverse restored to 8080). Pending: run the capture script once
FMP quota resets (no real fixtures committed yet); scenarios later; Cloud Run not started.

### Settings → Development → Backend Data Source (2026-10-07, included in current commit)

Debug-only segmented setting (Mock Data | Real Data) between Notifications and About, built
on the now-generic `StockSegmentedControl<T>` (also used by Light | Dark | System). Shared
`settings/BackendEnvironment.kt`: `BackendEnvironment` MOCK/REAL, `BackendEnvironmentStore`
(key `stocksteps.backendEnvironment`, unsaved → REAL), `BackendEndpoints(real, mock?)`,
`BackendRouter` (no mock URL ⇒ always REAL). `StockStepsApi` now takes a base-URL provider
resolved per request (String overload kept), so every existing client switches on its next
request; Android scene ViewModels are keyed by environment for fresh data; iOS clients take
a `() -> String` closure (`BackendSettings.currentURL`) and Home reloads on change. Status
line (blue dot) "Using local sample market data" / "Using live StockSteps backend", amber
quota warning for Real, Mock→Real confirmation ("Use real market data?"), Real→Mock immediate.
URLs: Android `BuildConfig.BACKEND_URL` / `MOCK_BACKEND_URL` (release mock empty); iOS
`STOCKSTEPS_BACKEND_URL` / `STOCKSTEPS_MOCK_BACKEND_URL` (mock DEBUG-only). `runMock` now
defaults to port 8081 (`adb reverse tcp:8081 tcp:8081`). Firebase auth/watchlist sync are
not StockSteps-backend calls and are unaffected.

Validation: server/core/host suites pass (new BackendRouterTest ×3, DynamicBaseUrlTest);
APK and SwiftUI compatibility build pass. Android emulator against a live mock server on 8081:
selecting Mock showed the sample-data banner; Real asked for confirmation, Cancel kept Mock;
Mock persisted across a cold restart; confirming Real showed the live status + quota warning
and removed the banner. App left on Real; mock server stopped. iOS runtime not verified.

### Fixture importer (2026-10-07, included in current commit)

`server/src/test/kotlin/.../tools/FixtureImporter.kt` + `:server:importFixture -Pkind=... -Pinput=...`
converts raw provider JSON into MOCK fixtures through the backend's own mappers/validation
(test source set only; not shipped). Supported kinds: `fmp-quote` (more added as owner
supplies responses). First fixture: `stocks/AAPL/quote.json` from an owner-supplied FMP quote
response; `manifest.json` records "Provider response examples imported with FixtureImporter".
Mock server verified: AAPL quote 200, symbols without fixtures 404. The owner's message
link exposed their FMP API key; owner advised to rotate it (key not stored anywhere).
Mock fixtures populated (2026-10-07): owner-supplied FMP responses imported through backend
mappers (AAPL quote; MOTS/SPEC/LUCY movers; market hours — a NASDAQ entry, accepted for
mocks though the live mapper reads NYSE; one news item in FMP news format via a mock-only
converter) plus synthetic sample values from `scripts/generate-sample-fixtures.py` (owner
asked to make up the rest): SPY/QQQ/DIA cards, 5-6 movers per tab, quotes, profiles (name,
sector, industry, country, logo URL only), smoothed intraday sparklines (none for sub-cent
stocks), search list, three neutral "Sample story" news items. Manifest labels the mix as
"Not market data". Home sub-dollar prices now show up to 4 decimals (fixes "$0.0002" →
"0.00" for live penny-stock movers too). Verified on emulator-5556 in Mock mode as guest:
full Home with logos, sparklines, Market Open, news and the sample-data banner.

## Company Details page (2026-10-07, in commit "Add Company Details page, mock sample data and Home visual refresh")

One canonical destination for every stock tap. Android: `CompanyDetailsRoute(symbol)` (plus
`CompanyFinancialsRoute(symbol)` reusing the existing Annual/Quarterly Financials + Valuation UI
and `CompanyNewsRoute(symbol)`); Home movers, Watchlist rows and Search results all navigate
there; the shell hides its title bar and the page renders `StockStepsTopBar` (new trailing slot)
with a watchlist star using the shared `StockWatchlistViewModel`. iOS: `CompanyDetailsScene`
pushed via `navigationDestination(item:)` from Home, Watchlist and Search (the search sheet
closes, then the page is pushed); watchlist star uses `AccountViewModel.toggle`. iOS has no
Financials/News deep destinations yet (links hidden there). The old tabbed in-search detail
remains in source but is no longer the entry point.

Backend (core models `CompanyDetails`, `ChartRange`, `PricePoint`, `PriceChart`, `WhyMoving`):
`/details` aggregation (`CompanyDetailsService`, sections fail independently with sanitized
errors), `/chart` (`PriceChartService`: 1D intraday, other ranges sliced from one daily history;
FMP `historical-price-eod/light`, live entitlement unverified), `/why-moving`
(`WhyMovingService`; REAL has no source yet → 404; MOCK fixtures). `PriceHistoryProvider`
gained intraday points + daily closes; fixture source implements them plus `WhyMovingSource`.

Shared presentation: `core/companydetail/CompanyOverview.kt` (`CompanyOverviewPresenter`):
At a glance (market cap size band, P/E "Nx earnings" or N/A, revenue growth, dividend with
NO_DIVIDEND vs missing), factual assessment rows (no ratings; valuation "Above/Below/Near its
history" from the backend difference, ±5%), deterministic beginner insight ending "This is
context, not a recommendation." with an evidence sheet, concise About, Financial highlights
with net-margin explanation, valuation summary. Education sheets for the four metrics.
`CompanyDetailsViewModel` (sealed `Section` states, chart cache per range). New generic
components: `StockMetric`, `StockInfoRow`, star icons, `chartHeight` token.

Mock fixtures (generator): MSFT complete/up, AAPL complete (real quote) without why-moving,
NVDA large move + P/E below history, TSLA down + no dividend + no P/E history, LONGN long
name + no logo + sparse data + no news/chart; daily (5y) and intraday charts, why-moving for
MSFT/NVDA/TSLA, company news. Initial page load = 4 backend requests (details, chart 1M,
why-moving, news).

Validation: server 75 (3 skips; new CompanyDetailsTest ×5), core 53 (new presenter ×7),
host 27 (new ViewModel test), APK + iOS framework + SwiftUI compatibility build pass. Mock
endpoints probed (details/chart ranges/why-moving/news). Android emulator in Mock mode showed
AAPL Company Details (header, price, Market Open, chart 1D/1Y, At a glance, assessment,
insight, About, highlights, valuation); the emulator was being used concurrently, so further
automated taps (search/watchlist entry, star toggle, news/financials links) were not run.
iOS runtime not verified. REAL mode not exercised (FMP quota). Pending: real why-moving
pipeline, iOS Financials/News destinations, live chart entitlement check.

### Mock tooling + real fixture capture (2026-10-07, in commit "Add Company Details page, mock sample data and Home visual refresh")

- `runMock` now runs from `installDist` (`build/install/server/lib`), so rebuilds no longer
  crash a running mock server (`NoClassDefFoundError`). New `:server:stopMock [-Pport=8081]`
  stops only a server whose `/api/v1/meta` reports `dataMode: mock` (uses `lsof`).
- `importFixture`: relative `-Pinput` resolves against the directory `./gradlew` ran from;
  missing input gives a clear message.
- `scripts/capture-fixtures.sh` also captures Company Details charts (`chart?range=1D` →
  `chart-intraday.json`, `range=ALL` → `chart-daily.json`). Fixtures recaptured from the REAL
  backend: snapshot, market news, quotes/profiles for 20 tickers, company news + 5y daily for
  AAPL/MSFT/NVDA/TSLA/AMZN/GOOGL/META, annual+quarter fundamentals for AAPL/MSFT/NVDA, search.
  The FMP plan returned errors for intraday (`historical-chart/5min`), so 1D/sparklines are
  not captured. Earlier designed scenarios (MSFT complete, NVDA P/E below history) now reflect
  real numbers; why-moving, LONGN and TSLA annual fundamentals remain sample data.
- MOCK sample fallback: `SampleMarketData` (server `repositoryImpl/fixture`) generates stable
  per-symbol quote, profile, 1D intraday (ends at quote, starts at previous close), ~5y daily,
  sparkline and fundamentals for any ticker without fixtures; stored series whose last close
  is >3% (intraday/sparkline) or >15% (daily) away from the quote are replaced. Enabled only
  via `FixtureMarketDataSource(sampleFallback = true)` in `mockDataSources()`; tests and REAL
  are unaffected. Why-moving and news remain fixture-only. Stale sample sparkline/intraday
  files for AMZN/META/MSFT/NVDA/TSLA were deleted.

Validation: server tests pass (new `MockModeTest.sampleFallbackFillsMissingTickersConsistently`);
mock server probed for SXTC/MSFT/NVDA/unknown ZZZQ: quote, sparkline, 1D and 1M all end at the
quote price, details include profile + fundamentals. Pending: mock empty/error/slow scenarios.

### Home reference-matching pass (2026-10-07, in commit "Add Company Details page, mock sample data and Home visual refresh")

- Home keeps the light grey `appBackground`. Its cards (index cards, Today's Movers, Recent
  News) are white with no border and no shadow, via `StockCard(bordered = false)` /
  `.stockCard(bordered: false)`; rows keep their dividers. Index cards now show name, price,
  change and a session sparkline (`MarketIndexUiModel.sparkline`,
  `HomePresentation.visibleIndexSymbols`; both ViewModels request index sparklines with the
  movers'). (A white-page variant was tried and reverted at the owner's request.)
- `StockChip` is a borderless pill (`shapes.pill` / `Capsule`), `chipHeight` 28 → 34.
- `StockRow` trailing column is now price (strong) over change, on both platforms (affects
  Watchlist/Search rows too).
- New Learn banner (`HomeLearnCard` / `HomeLearnBanner`): lavender-to-blue gradient tokens
  (`learnContainerStart/End`, `learnAccent`, `onLearnAccent`), decorative books-and-sprout
  illustration (`drawable/ill_learn_basics.xml`, iOS `LearnBasics.imageset` SVG), round arrow cue,
  copy "Learn the Basics" / "How the stock market works, step by step."
- New tokens: `learnIllustration`, `learnAction`; `StockIcons.ArrowForward`.
- Mock sample intraday noise scales with the day's move, so big movers get lifelike lines.
- Not adopted from the reference: notification bell (no notifications yet), personalised
  "Good morning, <name>" greeting, green brand chips (brand stays blue), five-tab bar.

Validation: core jvmTest, shared host tests (HomeViewModelTest updated for index sparkline
requests), server tests, Android assembleDebug pass; Android Home checked on emulator in Mock
mode. iOS NOT compiled: local Xcode 16.2 cannot resolve firebase-ios-sdk 12.19.2 (needs Swift
tools 6.1 / newer Xcode) — environment issue, predates this change.

### Market status switch (2026-10-07, in commit "Add Company Details page, mock sample data and Home visual refresh")

`MarketStatusIndicator` (Compose + SwiftUI; used on Home and Company Details) now shows the
label followed by a small read-only switch instead of the colored dot: green track/thumb right
for OPEN, red (`negative`) track/thumb left for CLOSED, grey (`textDisabled`) track/thumb left
for PRE_MARKET and AFTER_HOURS; white thumb. It is
drawn, not a real Switch/Toggle, so it is never focusable or tappable; the merged accessibility
label still announces the state ("Market closed"). UNKNOWN still renders nothing. New tokens
`statusSwitchWidth/Height/Thumb`; iOS `StockColors` gained `textDisabled`. Validation: Android
assembleDebug passes; not visually checked (emulator closed); iOS not compiled (Xcode 16.2 vs
firebase-ios-sdk 12.19.2).

### Company Details reference-design pass (2026-10-07, in commit "Complete Company Details reference design on Android and iOS")

Implemented the full reference layout (owner's 3-column Microsoft mock) on Android and iOS:
header with tags (sector, industry, size band) and "Updated <time> ET"; pill range selector,
area chart with y/x axis labels, range change ("+7.25% past 1M") and touch-to-inspect scrub;
quick stats (Open/High/Low/Volume/Market Cap/P/E, "—" when not reported); Why Did It Move card
(warm educational surface, "Why this matters" box, source links + sources sheet; hidden when the
backend has no explanation, retry state on failure); At a Glance 2×2 tiles with icons; How Does
<Company> Look? rows with icon tiles and rule-based badges; Beginner Insight; About with See
More/Less, country and tags; Financial Highlights with change column and basis footnote;
Valuation three-tile comparison + tinted headline (amber only when above history) + links;
52-week and day range bars; Key Ratios; Sector & Industry tiles. Skipped (no reliable data):
Analyst Outlook, Earnings, Comparable Companies, People Also Viewed.

- Contract: `StockQuote.open/yearHigh/yearLow` (FMP quote mapper; Finnhub maps `o`).
  `CompanyDetailsRepository.getWhyMoving` returns null for 404 `WHY_MOVING_UNAVAILABLE`.
- Shared: `CompanyOverview` extended (tags, updatedAt, quickStats, dayRange/yearRange,
  aboutFull, sector/industry/country/website, highlight changes + basis, keyRatios, valuation
  position/headline); `AssessmentRules` (documented thresholds); `ChartPresentation`.
- Compose components (generic): `StockTag`, `StockBadge`, `StockIconTile`, `toneColors`,
  `StockRangeBar`, `StockLineChart`, `StockAssessmentRow`, `StockPillSelector`; `StockInfoRow`
  gained change/direction/compact. Tokens: `cautionText`, `iconTile`, `rangeBar`, `rangeMarker`,
  `changeColumn`; icons TrendingUp, PieChart, Tag, Bank, Coin, Lightbulb, Globe.
  `formatQuoteTime` expect/actual (Android/JVM java.time, iOS NSDateFormatter).
- SwiftUI: `DesignSystem/StockDetailComponents.swift` mirrors the above; `CompanyDetailsScreen`
  rewritten; new `CompanyFinancialsScene.swift` (Financials + valuation, Annual/Quarterly via
  shared `FinancialsPresenter`) and `CompanyNewsScene`; both pushed from Company Details, so iOS
  "See Financials", "See Details", "See detailed valuation" and "View All" now work.
  `StockColors` gained `positiveContainer`, `cautionText`, `textDisabled` (iOS).
- Mock: sample gap filling — captured quotes get missing open/volume/market cap, captured
  fundamentals get missing facts and a P/E history around the real P/E (captured facts and
  NO_DIVIDEND always win); `manifest.json` `keepMissing` (LONGN, TSLA) keeps missing-data
  scenarios. 52-week range derived from stored daily closes; generated daily history passes
  through the previous close; generated intraday stays inside the day low/high.
- `runMock` ignores exit 143 so `stopMock` no longer reports BUILD FAILED.
- Previews: `CompanyDetailsPreviews.kt` (light, dark, negative day/no dividend/negative
  earnings, long name + partial failure, loading, large text).

Validation: core jvmTest (presenter: quick stats/ranges, valuation position, rules, chart
labels; repository: why-moving 404 → null, other errors stay failures), shared host tests
(why-moving hidden vs retry, route carries only the symbol), server tests (gap filling keeps
captured facts/NO_DIVIDEND, year range, previous-close continuity), Android assembleDebug,
iOS Kotlin framework link, and **Swift type-check of all iOS sources** against the Shared
framework (`swiftc -typecheck`, all files incl. Firebase-guarded ones) — passes. Android
emulator in Mock mode: SXTC (sample-filled) and MSFT (captured + gap-filled) checked top to
bottom in light and dark; 1D range and touch scrub verified; backend log confirms 4 requests
on open. Not verified: iOS runtime (Xcode 16.2 cannot resolve firebase-ios-sdk 12.19.2), the
watchlist star tap (it would change the signed-in account's real watchlist), REAL mode for
this pass. Pending: real why-moving pipeline; analyst/earnings/comparables need a data source.

### Financials screen — data layer done, UI pending (2026-10-07, in commit "Add normalized financial history and shared Financials presenter")

Done: `CompanyFundamentals.history` (`FinancialPeriodStatement`, newest first; FY or Q rows merged
from income/cash-flow/balance statements by fiscal year+period, capex normalized to positive
spending, FCF subtracted once, currency-mismatched rows not merged, nulls never zero) built by
`FmpFundamentalsMapper.history` from rows the loader already fetches (quarterly balance limit
raised to 8 to match). Shared `FinancialMath` + `FinancialStatementsPresenter` (core/companydetail):
revenue, profitability, cash flow, financial health, EPS (diluted only), dividends (NO_DIVIDEND vs
missing), factual summary, detailed table, advanced metrics, range slicing (3Y/5Y/10Y, 4 quarters
per year), same-quarter-last-year comparisons, no currency mixing. Tests: server
`FinancialHistoryTest`, core `FinancialStatementsPresenterTest` (pass).

Remaining: Compose screen rewrite of `CompanyFinancialsScene` (frequency + range pills, bar chart
component with tap-to-inspect, comparison bars, expandable table/advanced, per-section "what/means/
why"), ViewModel with per-frequency cache + profile header, SwiftUI mirror of
`CompanyFinancialsScene.swift`, mock history fixtures (MSFT/AAPL/NVDA/TSLA/LONGN/TD CAD Oct FY,
negative/missing scenarios) + `SampleMarketData` generated history for gap filling, previews,
emulator check, docs/commit.

### Mock connection diagnosis (2026-10-07, local runtime only)

User reported mock data unreachable after reversing ports 8080 and 8081. Both
reverse mappings were present on emulator-5554; the real backend listened on 8080,
but no process listened on 8081. `adb reverse` forwards traffic and does not start
the backend. Started `./gradlew :server:runMock` (log:
`/tmp/stocksteps-mock-server.log`). Mock metadata, AAPL quote and AAPL details all
returned HTTP 200; metadata confirmed `dataMode=mock`. Android must select
Settings → Development → Backend Data Source → Mock Data. If the local process
stops or the Mac restarts, start runMock again; repeat reverse mappings after
emulator/device reconnect. No application code changed; existing in-progress
Financials changes were preserved. No commit/push or cloud deployment performed.
