For project status, architecture, and pending work, read [PROJECT_HANDOFF.md](PROJECT_HANDOFF.md).

This is a Kotlin Multiplatform project targeting Android, iOS, Server.

* [/app/iosApp](./app/iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose
  Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/app/shared](./app/shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./app/shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./app/shared/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./app/shared/src/jvmMain/kotlin)
      folder is the appropriate location.

* [/core](./core/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./core/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Android app: `./gradlew :app:androidApp:assembleDebug`
- Server: `./gradlew :server:run`
- iOS app: open the [/app/iosApp](./app/iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :app:shared:testAndroidHostTest`
- Server tests: `./gradlew :server:test`
- iOS tests: `./gradlew :app:shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

### Stock quote API

Set `FMP_API_KEY` in the server environment before running `:server:run`.
`GET /api/v1/stocks/{symbol}/quote` returns a StockQuote on success.
Symbols are case-insensitive and accept 1–20 ASCII letters, digits, dots, or
hyphens, starting with a letter or digit (for example `AAPL`, `BRK.B`, `SHOP.TO`).
This checks syntax only; the provider determines whether the symbol exists.

Quote errors return JSON with `code` and `message`:

| HTTP status | Code | Meaning |
| --- | --- | --- |
| 400 | INVALID_SYMBOL | Invalid symbol syntax |
| 404 | STOCK_NOT_FOUND | Provider returned an empty quote list |
| 502 | PROVIDER_UNAVAILABLE | Provider HTTP or network failure |
| 502 | INVALID_PROVIDER_RESPONSE | Malformed or inconsistent quote data |
| 503 | PROVIDER_RATE_LIMITED | Upstream provider quota exhausted |
| 504 | PROVIDER_TIMEOUT | Provider request, connection, or socket timeout |
| 500 | INTERNAL_ERROR | Unexpected backend failure |

Provider bodies, URLs, credentials, and exception details are excluded from
public errors. Requests have a 10-second timeout, with a 5-second connection
timeout and a 10-second socket timeout. There are no automatic retries yet.
Server tests use fake repositories and a mock HTTP engine; no API key is needed.


### Stock search

`GET /api/v1/stocks/search?query=apple` queries FMP's `stable/search-symbol` and `stable/search-name`
endpoints concurrently (two provider requests per search) and returns an array of StockSearchResult objects (`symbol`, `name`,
`currency`, `exchange`, `exchangeFullName`). Only USD listings on recognized US exchanges are returned. Canadian/foreign listings and results with missing currency or exchange are excluded.
Duplicate symbols are removed case-insensitively, preferring ticker-search results.
Exact ticker matches appear first; remaining results retain ticker-search then
company-name ordering. Distinct listings such as AAPL and AAPL.TO are preserved. Optional fields may be null. Search includes the
instrument types returned by FMP; no stock-only filtering is applied yet.

The query is trimmed and must contain 1–100 characters without control
characters. Invalid queries return HTTP 400 with code `INVALID_QUERY`.
No matches returns HTTP 200 with `[]`. Provider failures use the same error
contract as quotes. Both company names and tickers are supported. If either provider request fails,
the search returns the existing provider error rather than partial results.


### Finnhub quotes

FMP remains the default quote provider. To select Finnhub, set
`QUOTE_PROVIDER=finnhub` and `FINNHUB_API_KEY` in the server environment, then
restart the server. `FMP_API_KEY` is still required for search. Use
`QUOTE_PROVIDER=fmp` (or leave it unset) to return to FMP quotes.

The public quote endpoint and JSON contract are unchanged. Finnhub quotes
are requested through the existing Ktor client with the `X-Finnhub-Token`
header; keys stay on the server. Company name and volume are null because
Finnhub's quote response does not supply them. The provider timestamp is
Unix seconds. The zero-price/zero-timestamp no-data response maps to 404;
malformed responses and provider errors use the existing JSON error contract.
There is no automatic fallback between providers.

Live quote access and Canadian symbol coverage must be checked with your
Finnhub account; mocked tests do not establish plan entitlements. For example,
try `/api/v1/stocks/AAPL/quote` and `/api/v1/stocks/SHOP.TO/quote` after enabling
Finnhub. Search access does not guarantee quote access for every listing.


### Company profiles

`GET /api/v1/stocks/{symbol}/profile` returns a provider-independent CompanyProfile
from FMP's `stable/profile` endpoint. Profiles always use FMP, including when
Finnhub is selected for quotes. Fields are `symbol`, `companyName`, `description`,
`sector`, `industry`, `website`, `country`, `currency`, `exchange`, and `logoUrl`.
All fields except symbol are nullable when unavailable. Symbols use the same
validation and uppercase normalization as quotes. Empty provider results return
404 with `PROFILE_NOT_FOUND`. Existing provider timeout/rate-limit/error handling
applies. Live access depends on your FMP plan and has not been verified by tests.


### Market movers

`GET /api/v1/market/gainers` and `GET /api/v1/market/losers` use FMP's
`stable/biggest-gainers` and `stable/biggest-losers` endpoints. Each returns
an array of MarketMover objects: `symbol`, `name`, `price`, `change`,
`changePercent`, and `exchange`. Name and exchange may be null. Gainers sort
by percentage change descending; losers sort ascending (largest decline first).
Empty lists return 200 with `[]`. Existing provider errors apply.

Movers reflect FMP's endpoint coverage, including OTC listings if returned.
These responses do not include currency; no USD/CAD filter or Canadian coverage
is promised for movers. No additional profile requests are made. Access depends
on your FMP plan; mocked tests do not establish live entitlements.


### Market news

`GET /api/v1/news?page=0&limit=20` uses Finnhub's `/api/v1/news?category=general`
feed and requires `FINNHUB_API_KEY`, independently of `QUOTE_PROVIDER`.
Authentication uses the `X-Finnhub-Token` header. FMP still handles search,
profiles and movers. Page defaults to 0 (allowed 0–100); limit defaults to 20
(allowed 1–100). Invalid pagination returns 400 with `INVALID_NEWS_QUERY`.

Finnhub returns a current feed rather than historical page-based results.
The backend sorts newest first and slices the feed using page and limit.
Pages beyond the available feed return 200 with `[]`; the feed can change
between requests. This is not stable historical pagination or a cached snapshot.

Each NewsArticle has `title`, `url`, and nullable `symbol`, `source`,
`publishedAt`, `imageUrl`. General news has no reliable single ticker, so symbol
is null. Publication times are UTC ISO 8601 strings converted from Unix seconds.
Only headlines, metadata and source links are returned; full article text is
not exposed. Existing provider errors apply; live access depends on your account.


### Mobile backend connection (search and quotes)

The Android Compose starter screen searches StockSteps and loads a quote
when a result is selected. Search waits 300 ms after input changes; obsolete
search/quote requests are cancelled. Loading, empty and error states are shown.
Provider API keys are never required by mobile code.

Public StockQuote, StockSearchResult, CompanyProfile and ApiError models live in `core` and are
used by both server and mobile. StockStepsApi accepts an injected Ktor client
and a backend base URL. Platform clients use OkHttp (Android) and Darwin (iOS).

The Android debug entry point uses `http://127.0.0.1:8080` with ADB reverse:
run `adb reverse tcp:8080 tcp:8080` before launching the app (repeat after the
emulator/device reconnects). This avoids host-alias connectivity issues and
works for USB-connected Android devices too. The shared Android fallback is
`http://10.0.2.2:8080`. iOS Simulator uses `http://localhost:8080`.
Start the backend first.
For a physical device, pass `App(baseUrl = "http://<Mac-LAN-IP>:8080")` from
the app entry point and use the same network. Android allows local cleartext
HTTP only in debug builds; iOS permits local networking. Use HTTPS for a deployed
backend; release Android builds do not allow this local HTTP setup.

These are integration screens, not the final Figma design. Persistent watchlists are not yet implemented. Home now connects movers and news.


### Native iOS UI and Liquid Glass

The iOS app now uses native SwiftUI in ContentView.swift, with search, quote
selection, loading/empty/error states and retry. IosStockStepsClient bridges the
shared Kotlin StockStepsApi and core models; Android continues using Compose.
Search is debounced and superseded search/quote jobs are cancelled.

Build with Xcode 26 or newer and run on iOS 26 or newer for native Liquid Glass
on navigation/search controls and the custom currency badge/retry controls.
Glass modifiers are guarded by compiler and runtime availability; older SDKs
build material/bordered fallbacks. List content keeps the standard readable
background instead of applying glass to every row. Xcode 16.2 can compile the
fallback but cannot verify or display native iOS 26 glass APIs.

The simulator backend defaults to http://localhost:8080. A physical iPhone
must use the Mac's LAN address via ContentView(baseURL: ...), or an HTTPS
backend. Provider keys remain on the server.


### Shared theme

`app/shared/src/commonMain/kotlin/org/example/stocksteps/theme` contains the
single source of values for colors (light/dark RGB palettes), spacing and
corners (logical dp/point units), and typography (size, line height and weight).
StockStepsTheme.kt adapts tokens to Compose MaterialTheme; Theme.swift adapts
them to SwiftUI Color, CGFloat and Dynamic Type-scaled native system fonts.
Change shared tokens to update both platforms; platform adapters retain native
controls, navigation, accessibility and Liquid Glass behavior. Native navigation
titles retain platform typography. Typography line height is applied in Compose;
SwiftUI uses native font metrics for baseline spacing. Tokens are initial app
defaults, not a completed Figma design-system import.

### Adaptive mobile layouts

Android uses the current app window size: below 600 dp, the quote appears with
search results; wider windows show search and quote panes side by side.
Jetpack WindowManager supplies separating folds and fully occluding hinges in
window coordinates. The layout leaves space around these features, including
horizontal tabletop folds. If one side is too small, content uses the larger
side. Search text and the selected stock survive Activity recreation.

iOS uses native `NavigationSplitView`: search and quote appear in separate
columns when space permits and collapse into navigation on compact windows.
This supports iPad multitasking and window resizing while preserving the native
Liquid Glass availability guards. No Apple-specific physical hinge integration
is assumed.

Before release, test a foldable Android emulator in folded, unfolded, and
half-open postures, plus rotation and split-screen. Verify that an active search
and selected quote remain usable after each transition. For iOS, test a compact
phone window and iPad split-screen at multiple widths, with larger text sizes.

### Mobile architecture

Android follows MVVM with clean layer boundaries. `App.kt` applies the theme
and opens `StockSearchRoute`. The scene acquires the destination-scoped lifecycle ViewModel and
collects its immutable `StateFlow` with lifecycle awareness. Search, quote,
and adaptive screen components live under `app/shared/.../presentation/stocksearch`;
UI events call ViewModel methods. The ViewModel owns request cancellation and
uses its lifecycle scope. Query and selection are saved by the scene for
recreation; fetched results are reloaded after process restoration.

The shared `core` domain layer defines `StockRepository`, `SearchStocks`, and
`GetStockQuote`. Its data layer implements the repository using `StockStepsApi`,
translates networking errors into domain errors, and preserves cancellation.
Existing serialized stock models are shared boundary models; separate domain
copies are unnecessary while their shapes are identical. Dependencies point
from presentation/data toward the domain contract. Dependency construction stays
in composition roots rather than in views or domain classes.

iOS uses native SwiftUI with an `@MainActor @Observable` view model, owned by
`ContentView` through `@State`. Search bindings use `@Bindable`; quote views read
observable state. `StockSearchServing` is injected into the view model, and its
native adapter calls the same shared domain use cases through the Kotlin bridge.
`ContentView` coordinates navigation, while separate search/quote views render
UI and `GlassModifiers` handles native glass availability. `iOSApp.swift` only
creates the scene. This is a native Observation-based MVVM structure, rather
than imposing Android lifecycle classes on SwiftUI.

### Dependency injection

Koin 4.2.2 is configured in `app/shared/.../di/StockStepsDependencies.kt`.
Each graph registers one HTTP client, API, and repository, plus factories for
use cases and the Android/Compose ViewModel. The Compose scene asks the graph
for its lifecycle-owned ViewModel with restored query/selection parameters.
The native iOS bridge resolves shared use cases from the same definitions;
SwiftUI continues to inject `StockSearchServing` through constructors.

Graphs are isolated Koin applications, so previews and multiple platform owners
can coexist without replacing global registrations. The ViewModel closes its
graph when cleared; the Swift service closes the bridge on release. Koin's
client definition closes its singleton HTTP client when the graph is closed.
Domain classes and UI components do not access the DI container.

Search input is debounced for 300 ms using Kotlin Flow on Android and Combine
on iOS. Changes cancel active requests immediately and blank input clears results
without waiting. Normalized duplicate queries do not issue another request.
Explicit iOS retries bypass typing debounce. The Android ViewModel test uses
virtual time to verify that typing bursts issue only the final query and clearing
input cancels an active request without repopulating the results.

Android windows with at least 600 dp of usable content width use a dedicated
search sidebar and quote detail surface. The sidebar keeps its search field
visible while results scroll and highlights the selected stock. Details show
price, change, and daily range. Compact windows retain the inline quote layout.
Separating folds use the same large-screen components in hinge-safe panes.

### Mobile company profiles

Selecting a stock loads its quote and profile independently on Android and iOS.
The company section displays name, sector, industry, country, and description.
Missing descriptions show an unavailable message; absent optional metadata is
omitted. Profile failures have their own retry and leave the quote visible.
Profiles use the existing StockSteps backend endpoint (FMP on the server), with
GetCompanyProfile injected through Koin. No provider credentials enter mobile.

### Mobile discovery/Home

Home loads gainers, losers, and 20 current market news headlines independently.
Each section handles loading, empty results, errors, and retry; Refresh reloads
all sections. Five movers per category are shown. Tapping a mover searches its
ticker using the existing US-only search flow, and Read article opens a source link.
Mover prices have no currency label because the backend does not supply one.
Native tabs on iOS and Compose navigation on Android retain the Search feature.
No watchlist storage or historical news pagination is included yet.

### Navigation boundaries

Routes contain typed destination identity and arguments only. Compose
AppNavigation uses Navigation Compose with destination-scoped ViewModel owners
and saved top-level state. Scenes acquire ViewModels and wire state/events;
screens receive UI state and callbacks and render adaptive content.

Native iOS follows the same separation using AppRoute, AppScene, feature scenes,
and value-state screens with callback/input bindings. Navigation remains native
TabView/NavigationSplitView. See PROJECT_HANDOFF.md for the current file map.

### Main navigation shell

Bottom tabs: Home, WatchList, Learn, Settings. Android uses the supplied drawable
icons; iOS uses native system icons. WatchList, Learn, and Settings are placeholder
screens only. Home contains discovery and opens Search through Search stocks or
a mover selection. Search is a secondary Android destination/native iOS sheet.


## Accounts and offline watchlist

Firebase email/password authentication and a SQLDelight-backed watchlist are
implemented on Android and native iOS. Firestore stores only per-user ticker
membership and timestamps; financial data stays on the Ktor backend. See
[authentication/watchlist setup and sync policy](docs/AUTH_WATCHLIST.md) for
configuration, tests, database ownership, security rules, and conflict behavior.
The actual iOS Firebase package requires Xcode 26.2 or newer.

Market Snapshot API and provider limitations: [docs/MARKET_SNAPSHOT.md](docs/MARKET_SNAPSHOT.md).

AI News backend setup and processing: [docs/AI_NEWS.md](docs/AI_NEWS.md).

Shared top app bar configuration: [docs/TOP_APP_BAR.md](docs/TOP_APP_BAR.md).

Company detail UI, data mapping and pending financial integrations: [COMPANY_DETAIL.md](docs/COMPANY_DETAIL.md).

Company fundamentals: `GET /api/v1/stocks/{symbol}/fundamentals?period=annual` (or `quarter`) supplies Financials and Valuation with reporting dates and partial availability. See [Company Detail](docs/COMPANY_DETAIL.md) for provider mappings and calculation limits.
