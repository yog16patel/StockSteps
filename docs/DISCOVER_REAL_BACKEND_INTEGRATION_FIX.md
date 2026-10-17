# Discover Stocks on the LAN REAL backend — diagnosis and fix (2026-10-16)

Base: `c6baf6a` ("Refine app-wide typography and spacing for Phase 5 …"), committed locally, not pushed. This work is the commit
"Fix Discover Stocks on the LAN REAL backend with distinct plan-restricted, rate-limited and temporary screener errors, a provider cooldown on the universe request and no Retry when the FMP plan excludes the screener on Android and iOS", committed and pushed on the owner's request; **not deployed**. Review package: `docs/reviews/discover_real_backend/`.

**Verdict: BLOCKED.** The REAL backend can't show genuine Discover results with the current FMP subscription. The plan doesn't
include FMP's `company-screener` endpoint (it returns 402 "Restricted Endpoint"). Without a subscription change or an owner-approved
alternative source, no code change can make Discover work on REAL. The fix makes the failure honest and cheap: a distinct
plan-restricted state with no Retry, a rate-limit state, and an hour-long cooldown so the provider isn't asked again on every open.
The server part of the fix only takes effect on the device after an approved redeploy (§9).

## 1. Reproduced failure

| Where | What happened |
|---|---|
| Pixel 7 (physical, USB + TLS adb), debug build, REAL → `http://<lan>:8082` (from `CONFIG` line: `stored=REAL effective=REAL`), guest | Markets → Discover Stocks shows "Couldn't load the screener" / "The screening universe couldn't be loaded. Try again shortly." / Try again |
| `StockSteps-API` log | `REQ GET /api/v1/screener/catalog env=REAL host=<lan> port=8082` → `RES status=503 result=HTTP_5XX durationMs=27 body={"code":"SCREENER_UNAVAILABLE",…}` |
| `curl` REAL `:8082` | catalog 503; search (default, `growing` preset, sector + name sort) 503 `SCREENER_UNAVAILABLE`; invalid range 400 `INVALID_SCREEN` |
| `curl` LAN MOCK `:8081` | catalog 200 (4 presets, 29 metrics, 160/160 evaluated); searches 200 with `sampleData=true`, cursor present |
| REAL container log (`stocksteps-real-api-1`) | `Provider HTTP failure: host=financialmodelingprep.com, path=/stable/company-screener, status=402` for every catalog attempt; the usage summary counts each one as `company-screener denied` |

Screenshot: `docs/reviews/discover_real_backend/captures/before_real_discover.png` (git-ignored).

## 2. Root cause (evidence)

1. REAL defines the screening universe with FMP `GET /stable/company-screener` (`FmpScreenerUniverse`, one request per exchange:
   NASDAQ, NYSE, TSX). MOCK uses fixtures (`FixtureScreenerUniverse`) and never calls FMP, which is why MOCK works.
2. One probe from inside the REAL container (`limit=1`; the key was expanded inside the container and never printed) returned
   **HTTP 402** with the body: *"Restricted Endpoint: This endpoint is not available under your current subscription…"*.
   So the endpoint itself is outside the plan; the problem isn't a parameter. `sp500-constituent` and `batch-quote` are also
   402. `profile?symbol=AAPL` is 200.
3. `apiCall` turns the 402 into `StockProviderException(UNAVAILABLE, 402)`. `ScreenerService.snapshot()` turned *every* universe
   failure into 503 `SCREENER_UNAVAILABLE` "Try again shortly". The message was misleading, because trying again can't help.
4. `CompanyFinancialCache` never stores failures, so **every** Discover open or Retry sent another refused request to FMP. The
   container log shows 6 `company-screener` upstream requests, all 402.
5. At the moment of the device repro, FMP was also answering 429 to quotes. The server's `ProviderGuard` circuit was open, so that
   catalog request was refused locally (`budgetDenied`, 27 ms) without reaching FMP. That's a second, temporary condition.

Ruled out:
- **URL/port:** the `CONFIG`/`ENV` lines show the right host and port.
- **Cleartext:** the request completed.
- **DI and route:** the response came from the screener route with a JSON `ApiError`.
- **Auth:** the screener is public; guest gets the same result.
- **Schema, mapping and UI state:** the client shows the server's message verbatim, as designed.
- **Cache:** nothing was cached.

## 3. Request flow

Compose `ScreenerScene` → core `ScreenerPresenter.start()` → `ScreenerDataSource.catalog()` → `StockStepsApi.getScreenerCatalog()`
(`GET /api/v1/screener/catalog`, Ktor client from `createBackendClient()`, `StockStepsApiLogging`) → server `screenerRoutes`
(admission `RouteGroup` screener + `RequestRateLimiter`) → `ScreenerService.catalog()` → `snapshot()` → `FmpScreenerUniverse.universe()`
(`CompanyFinancialCache` key `universe`, 24 h) → `apiCall` (`ProviderGuard` permit, `ProviderCalls.record`) → FMP `company-screener`.
Search: `POST /api/v1/screener/search` → the same `snapshot()` → `ScreenerEngine.evaluate` (page cache, 5 min) → one cached `quote` per
row on the page. Company Details opens with `CompanyDetailsRoute(symbol)` from a row; the screener has no sign-in boundary (saved screens
are `/api/v1/me/screens`).

REAL configuration (`deploy/local/docker-compose.real.yml`): `SCREENER_FUNDAMENTALS_PER_HOUR=0`, so there's no background warm-up.
Exchanges default to NASDAQ,NYSE,TSX, with a limit of 50 per exchange and a $2B minimum market cap. FMP budget: 250/min; Finnhub: 60/min.

## 4. Compatibility matrix

| Journey | MOCK (LAN :8081) | REAL backend (:8082) | Provider (FMP plan) | Result on REAL |
|---|---|---|---|---|
| Initial results (catalog + default search) | 200, 160 sample companies | 503 | `company-screener` 402 | BLOCKED (plan) |
| Presets (growing, valuation, dividend, …) | 200 (`growing` 89) | 503 | same | BLOCKED |
| Market-data filters (price, market cap, volume) | 200 | 503 | same | BLOCKED |
| Fundamental filters (growth, margins, P/E…) | 200 | 503 (and warm-up is 0 on REAL, so none would be evaluated) | statements/ratios endpoints not probed | BLOCKED |
| Choice filters (sector, exchange, country) | 200 | 503 | same | BLOCKED |
| Sorting (market cap, name, change, metric) | 200 (name sort verified) | 503 | same | BLOCKED |
| Pagination (cursor) | 200, `nextCursor` present | 503 | same | BLOCKED |
| Company Details from a row | works | unreachable (no rows) | `profile` 200 | NOT RUN |
| Empty state | "No companies match your filters" (engine) | unreachable | — | NOT RUN |
| Error state | — | after the fix: plan-restricted state, no Retry | — | PASS in tests; device BLOCKED (§8) |
| Validation error | 400 `INVALID_SCREEN` | 400 `INVALID_SCREEN` | none | PASS |

No data was invented: the app never falls back from REAL to MOCK or samples (the presenter test asserts no presets appear).

## 5. Provider usage and limits

- **Endpoint used:** FMP `/stable/company-screener` (universe). On REAL, search also calls `/stable/quote` once per row on the page.
  The fundamentals datasets are only needed for financial filters, and warm-up is off on REAL.
- **Plan:** `company-screener`, `sp500-constituent` and `batch-quote` are restricted (402). Single quotes are refused for most symbols:
  since the 21:57Z container start, 111 of 139 `quote` requests were 402 and 4 were 429. Overall, 125 of 218 FMP requests were refused
  (57 %), and `profile` works for AAPL. This pattern fits a basic subscription tier. The tier name wasn't checked, and no plan was
  bought or changed.
- **Lowest-cost compliant alternatives (owner decision; none implemented):**
  1. **An FMP tier that includes the company screener.** No code change: the existing universe, 24 h cache and cooldown work as
     designed. Cost: an FMP subscription change. Check what that tier allows for quotes and statements too, because quotes are
     already refused for most symbols.
  2. **A curated, repo-owned universe list** (as MOCK uses `fixtures/screener/universe.json`), labelled as a fixed list rather than a
     provider selection, with per-symbol FMP `profile` for price, market cap, volume, sector and industry (about 150 requests per
     day with the 24 h profile cache). Only basic filters would work unless statements are available on the plan. Medium effort.
     Needs the owner's approval of the universe wording. The current plan's symbol restrictions may still leave gaps, and those must
     show as unavailable.
  3. **Finnhub free tier** (`stock/profile2` + `stock/metric`): one request each per company per day, within 60/min. A new adapter
     would be needed, and its metric definitions differ from Company Details' FMP values. That breaks the rule "screener values match
     Company Details", so it needs an owner decision. Highest effort.
  - Not allowed and not used: scraping, unofficial APIs, raising rate limits, or retry loops.

## 6. The fix (smallest safe change)

- **Server, `ScreenerService.kt`:**
  - `FmpScreenerUniverse.universe()` caches an outcome with the existing `providerCooldown`: 24 h for data; 1 h for 402/403;
    10 min for 429; 30 s otherwise. A guard denial (no status) also gets 30 s. Concurrent callers still share one request
    (single flight). The cache is named `screener-universe` for the usage counters.
  - `snapshot()` maps the failure through `universeUnavailable` (all three keep status 503):
    - 402/403 → `SCREENER_PROVIDER_RESTRICTED`: "Discover Stocks isn't available right now because this server's market data plan
      doesn't include stock screening. Trying again won't help. You can still search for a company by name or ticker." The server
      logs one WARN line with the status only;
    - 429 → `SCREENER_RATE_LIMITED`: "Market data requests are limited right now. Try again in a few minutes.";
    - anything else → `SCREENER_UNAVAILABLE` with the unchanged "Try again shortly" message.
- **Core:**
  - `ScreenerDisplay` holds the codes, the messages, `catalogErrorTitle(code)` and `catalogRetryable(code)`.
  - `ScreenerUiState.catalogErrorCode` (new, optional) carries the code into the UI.
  - When the server sent no `ApiError`, the presenter now chooses between two messages: "couldn't reach the StockSteps server" for a
    connection failure, or "received a response it couldn't read" for a serialization failure. Before, both showed "Screening isn't
    available right now."
- **Android** (`ScreenerScreen.kt`) and **iOS** (`DiscoverStocksViews.swift`) take the title from `catalogErrorTitle`, and show
  Retry / "Try again" only when `catalogRetryable`. Nothing else in the UI changed.
- Unchanged:
  - navigation, Company Search ranking, the Company Details UI, Phase 5 typography and guest/auth boundaries;
  - the MOCK path;
  - successful REAL responses (same 24 h universe cache, page cache and quotes).

Loaded data during a refresh: the catalog loads once per presenter. Search keeps its existing behaviour (an error replaces rows).
Nothing changed there.

## 7. Request efficiency

MockEngine measurements (`ScreenerUniverseFailureTest`, REAL adapters with warm-up 0, as on the LAN):

| Journey | Before | After |
|---|---|---|
| Open Discover (catalog), healthy | 3 (one per exchange) | 3 |
| Default results, first page of 20 | 20 quotes | 20 |
| Apply a market-data filter | 0 (rows' quotes cached) | 0 |
| Repeat the identical filter | 0 (page cache) | 0 |
| Back from Company Details / tab switch (catalog asked again) | 0 (universe cache) | 0 |
| 20 concurrent identical catalog requests | 3 | 3 |
| **Plan-restricted: each open or Retry** | **1 refused FMP request each time** (log: 6 in this session) | **1 per hour** (tested at 10-min steps; 403 the same) |
| 20 concurrent opens while restricted | 1 (shared failure) | 1 |
| Rate limited (429) | 1 per open | 1 per 10 min |
| Outage (5xx) / unreadable JSON | 1 per open | 1 per 30 s |

App side: `ScreenerPresenter.start()` doesn't reload a loaded catalog. Searches are `distinctUntilChanged`, so an identical filter isn't
sent again. Android tab switches use `LaunchedOncePerEntry`. On the device these weren't counted on REAL, because Discover can't load
there: NOT MEASURED.

## 8. Device results (Pixel 7, physical)

- **Before (REAL, LAN :8082):** PASS for the reproduction. The 503 `SCREENER_UNAVAILABLE` "Try again shortly" state is shown, the
  log has the trace, and no MOCK data appears.
- **After, client part:** BLOCKED. A debug build of this tree pointed its debug REAL URL at a local stub (`127.0.0.1:8086` via
  `adb reverse`). The stub answered `/api/v1/screener/*` with the fixed server's 503 body and proxied every other route to LAN REAL.
  The build was installed and its `CONFIG` line confirmed `real=http://127.0.0.1:8086/`. Then the phone locked with a secure lock
  screen. I don't enter PINs, so Discover wasn't reached (the stub log shows no app requests). The client behaviour is covered by
  `ScreenerPresenterTest.catalogErrorsSayWhetherRetryingHelps` and by both platform builds. The phone was restored to the normal
  debug build (`CONFIG … real=http://<lan>:8082/`), and the reverse mapping and stub were removed.
- **After, server part on REAL:** BLOCKED. It needs the owner's approval for a redeploy of `stocksteps-real-api-1` (§9).
- **Genuine REAL results, filters, sorting, Company Details from a row:** BLOCKED by the FMP plan.
- **MOCK → REAL switch with no stale MOCK data:** BLOCKED (phone locked). The LAN MOCK and REAL answers differ (`sampleData=true` vs
  503), and the presenter is per environment graph. This wasn't re-checked on the device.
- **iOS:** NOT RUN on a device or simulator; `xcodebuild` PASS.

To finish the device check (after unlocking):
1. Run `./gradlew :app:androidApp:assembleDebug -PstockstepsDebugRealBackendUrl=http://127.0.0.1:8086`.
2. Start the stub and run `adb reverse tcp:8086 tcp:8086`.
3. Open Markets → Discover Stocks. Expect the title "Discover Stocks isn't available", the plan message, and no Try again button.
   Or redeploy REAL (§9) and use the normal build.

## 9. What needs the owner

1. **The FMP plan decision** (§5): upgrade, a curated universe, or Finnhub. Until then, Discover can't show real results on REAL.
2. **Approval to redeploy the LAN REAL container** with this server change, so the device shows the plan-restricted state and stops
   asking FMP on every open. It's the same procedure as §REAL-PORTFOLIO-PRACTICE: build the image from this tree, then run
   `STOCKSTEPS_STACK=real deploy/local/deploy.sh`. The overlay, env and keys are unchanged. That recreates `stocksteps-real-api-1`;
   MOCK isn't touched.
3. Copy approval for the three new messages (`ScreenerDisplay`).

## 10. Tests and builds (sequential, this tree)

| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS 613/0 (+1 `ScreenerPresenterTest.catalogErrorsSayWhetherRetryingHelps`) |
| `:core:iosSimulatorArm64Test` | PASS 611/0 |
| `:server:test` | PASS 515/0, 3 skipped (pre-existing), +7 `ScreenerUniverseFailureTest` |
| `:app:shared:testAndroidHostTest` | PASS 284/0 |
| `:app:shared:iosSimulatorArm64Test` | PASS 281/0 |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` (iOS simulator, generic) | PASS (BUILD SUCCEEDED) |

## 11. Follow-ups (not fixed here)

- `/stable/quote` is refused (402) for most symbols on this plan, and there's no cooldown for quote denials, so Markets, Home and
  Company Details quotes are affected and repeat refused requests. Separate task.
- FMP 429s while the plan's daily quota is shared with the Mac (D-R3).
- Search errors (`state.error`) still offer Retry for every code. They're unreachable while the catalog fails, but worth aligning.
