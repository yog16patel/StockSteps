# Phase 7E — Discover Stocks without the paid screener (curated universe)

Date: 2026-10-17. Base: `9b067e2` (Phase 7D, pushed). Phases 7A–7C are deployed to LAN REAL; the 7D client fix ships with app builds.
**This phase: "Make Discover work on REAL for Phase 7E with a versioned curated list of 155 US-listed companies enriched in the background from shared profile and closing-price caches, no FMP screener or per-row quote requests, dated close prices and a find-by-name-or-ticker search on Android and iOS", committed and pushed on the owner's request; not deployed.** Review package: `docs/reviews/discover_curated_phase7e/`.

**Verdict: READY FOR REVIEW**, with owner decisions (§7).

## 1. Audit (before)

| Layer | Finding |
|---|---|
| Apps | Compose `ScreenerScreen` / SwiftUI `DiscoverStocksViews` → shared core `ScreenerPresenter` → `StockStepsApi` `GET /api/v1/screener/catalog`, `POST /api/v1/screener/search` |
| Server | `ScreenerService` over a `ScreenerUniverseSource`:<br>• REAL: `FmpScreenerUniverse` (`/stable/company-screener` ×3 exchanges, cached 24 h with the `be3c15e` cooldown);<br>• MOCK: fixture universe (~150) |
| Engine | `ScreenerEngine` (core):<br>• choice filters (country, exchange, sector, industry, type) and range filters over 29 metrics;<br>• missing values are excluded and counted (`excludedForMissingData`) and sort last; ties by symbol;<br>• `offset.fingerprint` cursors; page cache 5 min |
| Restricted dependencies | `company-screener` (402) for the universe and its price/market cap/volume; then one **quote per page row** (mostly 402). Fundamentals need the warm-up (0 on LAN REAL) |
| REAL result | Discover unusable ("isn't available", no Retry, since `be3c15e`) |
| Search | none inside Discover (global Company Search is separate) |

## 2. What changed

| Area | Change |
|---|---|
| `server/src/main/resources/screener/curated-us-universe.json` (new) | 155 US-listed companies across 11 sectors (provider sector names). Version `2026-10-17.1`; metadata only; provenance and the "isn't the whole market" wording. See `CURATED_UNIVERSE.md` |
| `server/.../screener/CuratedScreenerUniverse.kt` (new) | The universe source. It returns the list at once with what's loaded, and enriches due companies in the background from the shared profile cache (name, sector, industry, exchange, country, market cap) and the shared session-aware close cache (latest close ≤ 5 sessions, dated, change vs the previous trading day). Budget `SCREENER_ENRICHMENT_PER_HOUR` (default 60), concurrency 4, LOW priority, 30-min retry after a failure, no repeat for unchanged data |
| `ScreenerService` | `UniverseEntry.priceDate` / `changePercent`; a "Latest close" price basis with the date; `pageQuotes = false` for curated (no quote per row); coverage and freshness warnings; `UniverseInfo.priced` |
| `Application.kt`, `StartupConfiguration.kt` | REAL uses the curated universe; `SCREENER_UNIVERSE=fmp` re-enables the FMP screener for a plan that includes it. `SCREENER_UNIVERSE` and `SCREENER_ENRICHMENT_PER_HOUR` are validated. MOCK is unchanged |
| Core `ScreenerModels` / `ScreenerEngine` | `ScreenerQuery.text` (optional, ≤ 100 characters): non-matches dropped, matches ordered by the shared `SearchRanking` tiers, the sort kept within a tier. `UniverseInfo.priced` (optional) |
| Core `CompanyProfile` / server `FmpCompanyProfile` | `marketCap` (optional, finite > 0) |
| Core `ScreenerPresenter`, `ScreenerDisplay` | `setText` (300 ms debounce, on top of the applied filters); `TEXT_SEARCH_LABEL` |
| Android `ScreenerScreen.kt` / `ScreenerScene.kt`; iOS `DiscoverStocksViews.swift` | one search field "Find a company by name or ticker" (existing design-system field); nothing else in the layout |

**Contracts:** additive only (new optional fields; old clients ignore them, and new clients read old servers). No success field was
removed or renamed.

## 3. Filters, sorts, search

`FILTER_SORT_CONTRACT.md`:
- market data (sector, exchange, market cap, price, change) is available once enriched;
- fundamentals depend on the warm-up and plan (coverage reported);
- volume and 52-week position are unavailable (no quotes);
- missing data is never 0, "small" or "no dividend".

## 4. Requests (MockEngine; `server/build/phase7e-discover-benchmark.txt`)

Upstream FMP requests, as screener / profile / EOD / quote / fundamentals / total:

| Scenario | FMP screener path, current plan (402) | FMP screener path, plan with screener | Curated |
|---|---|---|---|
| One cold open | 1 / 0 / 0 / 0 / 0 / 1 (Discover fails) | 3 / 0 / 0 / 0 / 0 / 3 | 0 / 155 / 155 / 0 / 0 / 310 (background, once) |
| Ten repeated opens | 1 / … / 1 | 3 / … / 3 | the same 310, no more |
| First page / three pages | 1 | 23 / 63 (a quote per row) | 310 (no extra) |
| Sector filter, market-cap sort, 10 concurrent users | 1 | 23 | 310 (no extra) |
| Provider 402 / 429 for profiles and closes (10 opens / 1 h) | 1 | 3 | 310 / 620 (429 cools down 10 min; REAL's guard would refuse locally after the first) |

- **Benchmark budget:** these runs use `perHour = 500`, so the whole list enriches in one pass. At the default of 60, the same 310
  requests spread over ≈ 2.6 h.
- **Live cost:** these are mock counts, not live measurements.

## 5. Tests and builds

`CHATGPT_TEST_RESULTS.md`. New:
- `CuratedDiscoverTest` (5): list quality, enrichment budget and reuse, failures and cooldown, filters/sorts/search/pages on the REAL
  adapters, cold and concurrent opens;
- `CuratedDiscoverBenchmarkTest` (1).

## 6. Device verification

NOT RUN / BLOCKED (`ANDROID_IOS_VERIFICATION.md`). Both builds pass.

## 7. Owner decisions

1. **The list:** approve the curated list and its wording.
2. **Search copy:** approve "Find a company by name or ticker".
3. **Enrichment budget:** set `SCREENER_ENRICHMENT_PER_HOUR` for the LAN key (quota Q1).
4. **Fundamentals:** whether to enable the fundamentals warm-up on REAL (cost; plan restrictions).
5. **Redeploy:** approve the LAN REAL redeploy.
6. **Licence:** the display licence (launch gate).
