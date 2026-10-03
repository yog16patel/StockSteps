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
`currency`, `exchange`, `exchangeFullName`). Only USD and CAD results are returned; other or missing currencies are excluded.
Duplicate symbols are removed case-insensitively, preferring ticker-search results.
Exact ticker matches appear first; remaining results retain ticker-search then
company-name ordering. Distinct listings such as AAPL and AAPL.TO are preserved. Optional fields may be null. Search includes the
instrument types returned by FMP; no stock-only filtering is applied yet.

The query is trimmed and must contain 1–100 characters without control
characters. Invalid queries return HTTP 400 with code `INVALID_QUERY`.
No matches returns HTTP 200 with `[]`. Provider failures use the same error
contract as quotes. Both company names and tickers are supported. If either provider request fails,
the search returns the existing provider error rather than partial results.
