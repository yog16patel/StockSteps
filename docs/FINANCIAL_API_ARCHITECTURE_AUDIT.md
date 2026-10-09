# Financial API architecture audit (Phase 1, read-only)

Audit date: 2026-10-09. Repository state: `main` at **"Add Company Comparison Phase 5 AI comparison assistant (StockSteps+) and redesign
sign-in screens on Android and iOS"** (`e27991d`), clean working tree. Method: static reading of the server, core and app code
(the code is authoritative; docs were used only for hints). No provider calls, deployments, Firestore writes or code changes were made.

Evidence labels used throughout:
- **Verified (code)**: read directly in the cited function.
- **Doc evidence**: stated in project docs/fixtures, not re-checked live.
- **Inferred**: follows from the code path but depends on runtime data or configuration.
- **Unknown**: needs provider dashboards, contracts or deployment settings that are not in the repository.

---

## 1. Current architecture in one picture

```mermaid
flowchart LR
  subgraph Apps["Android (Compose) / iOS (SwiftUI)"]
    P[Shared KMP presenters] --> API[StockStepsApi public routes]
    P --> UAPI[UserApi /api/v1/me/* (Firebase token)]
  end
  API --> K[Ktor server (one Cloud Run service)]
  UAPI --> K
  subgraph K[Ktor server: one process = one set of in-memory caches]
    SS[StockService] --> FMPR[FmpStockProviderRepositoryImpl\n+ FmpFundamentalsLoader\n(CompanyFinancialCache)]
    PCS[PriceChartService] --> FPH[FmpPriceHistoryProvider]
    SPK[SparklineService] --> FPH
    MKT[MarketsService] --> FMD[FmpMarketDataProvider]
    MKT --> FMPR
    SNAP[MarketSnapshotService] --> FMD
    NEWS[NewsService] --> FHN[FinnhubNewsProviderRepositoryImpl]
    NEWS --> SIMP[NewsSimplificationService\n(Firestore store)]
    EARN[EarningsService] --> FHE[FinnhubEarningsDataSource]
    FX[BankOfCanadaPortfolioFx x3]
    AI[Gemini: GeminiJsonCall / GeminiNewsSimplifier]
  end
  FMPR --> FMP[(Financial Modeling Prep /stable)]
  FPH --> FMP
  FMD --> FMP
  FMD -. index fallback .-> FH[(Finnhub)]
  FHN --> FH
  FHE --> FH
  FX --> BOC[(Bank of Canada Valet)]
  AI --> GEM[(Gemini API)]
  SIMP --> FS[(Firestore)]
  K --> FS
```

All provider access is server-side (**verified**: no provider host appears in `core/` or `app/`; clients call only StockSteps routes).
There is **no shared/distributed cache**: every cache is a per-process `CompanyFinancialCache`, `ConcurrentHashMap` or similar, except
the news-simplification store (Firestore) and user data (Firestore). Deployment settings (Cloud Run min/max instances, concurrency,
Cloud Scheduler cadence) are **not in the repository → unknown**.

---

## 2. Provider inventory

`apiCall` (`server/.../httpclient/NetworkUtils.kt`) is the common HTTP helper: GET only, **no retries**, status classification (429 →
`RATE_LIMITED`, other non-2xx → `UNAVAILABLE`), logs host + path + status only (never the query string, so the FMP `apikey` query
parameter is not logged). Global client timeouts: request 10 s, connect 5 s, socket 10 s (`HttpClientProvider`).

| Provider | Endpoint | Calling code (file → function) | Feature(s) | Data | Cache (key / TTL) | Trigger | Auth |
|---|---|---|---|---|---|---|---|
| FMP | `/stable/search-symbol`, `/stable/search-name` | `FmpStockProviderRepositoryImpl.searchStocks` | Company Search, Earnings calendar name filter | search results | **none** (earnings name filter caches 1 h: `EarningsService.nameMatches`) | each search (client debounce 300 ms, `StockSearchViewModel`) → **2 calls** | `apikey` query param |
| FMP | `/stable/quote` | `FmpStockProviderRepositoryImpl.getQuote` | Quote, Details, Watch, Alerts, Portfolio, Markets, Movement, Comparison, Practice | quote | `quote:SYM` 30 s (shared repo cache) | any quote read (one symbol per call) | query |
| FMP | `/stable/quote` (SPY, QQQ, DIA) | `FmpMarketDataProvider.getMarketIndices` | legacy market snapshot | index proxies | only the 45 s snapshot cache (`InMemoryMarketSnapshotCache`); **bypasses the repo quote cache** | `/market/snapshot` | query |
| FMP | `/stable/profile` | `FmpStockProviderRepositoryImpl.getProfile` | Details, Watch, Comparison, Earnings logos, History names | profile | `profile:SYM` 24 h (repo) + `StockService` 24 h + `WatchMarketData` 24 h | profile reads | query |
| FMP | `/stable/biggest-gainers`, `/biggest-losers` | `FmpStockProviderRepositoryImpl.getMovers` | legacy `/api/v1/market/gainers|losers` | movers | **none** | each call | query |
| FMP | `/stable/biggest-gainers`, `/biggest-losers`, `/most-actives` | `FmpMarketDataProvider.movers` | Markets overview, legacy snapshot | movers | `MarketsService` `section:*` 60 s live / 15 min closed; snapshot 45 s | overview / snapshot | query |
| FMP | `/stable/exchange-market-hours?exchange=NYSE` | `FmpMarketDataProvider.getMarketStatus` | Company Details, legacy snapshot | market status | `CompanyDetailsService` `status` 60 s; snapshot 45 s | Details open | query |
| FMP | `/stable/income-statement` (annual 6, quarter 8, quarter 24), `balance-sheet-statement`, `cash-flow-statement`, `ratios-ttm`, `key-metrics-ttm`, `ratios`, `income-statement-ttm`, `cash-flow-statement-ttm`, `analyst-estimates`, `dividends`, `shares-float` | `FmpFundamentalsLoader.load`, `.quarterlyEarnings` | Details, Financials, Valuation, Screener, Comparison P1/P3/P4/P5 | statements, ratios, estimates, dividends | `endpoint:SYM:period:limit`; statements 24 h, TTM statements 6 h, ratios-ttm/key-metrics-ttm **5 min**, estimates 6 h; failures 30 s, 402/403 1 h (`cooldown:` layer); metered by `ProviderUsageMeter` | any fundamentals read | query |
| FMP | `/stable/historical-price-eod/light` | `FmpPriceHistoryProvider.getDailyCloses` | Charts 1W–ALL, Valuation P/E, Movement, Comparison performance, Portfolio history, Earnings reaction, Practice | daily closes (full history) | `PriceChartService` `daily:SYM` 6 h (one instance shared); `PortfolioMarketService` `portfolio-history:SYM` 6 h on top | chart/valuation/etc. | query |
| FMP | `/stable/historical-chart/5min` | `FmpPriceHistoryProvider.getIntradayPoints/Sparkline` | 1D chart, sparklines | 5-min bars | `intraday:SYM` 5 min (`PriceChartService`); sparklines 5 min (**separate** `SparklineService` cache) | 1D chart, Home sparklines | query |
| FMP | `/stable/company-screener` | `FmpScreenerUniverse.universe` | Screener | universe | `universe` 24 h | screener catalog/search | query |
| Finnhub | `/api/v1/quote` | `FinnhubStockProviderRepositoryImpl.getQuote` | all quotes when `QUOTE_PROVIDER=finnhub`; index fallback in `FmpMarketDataProvider` | quote | **none** in the class (callers' caches only) | quote reads / snapshot fallback | `X-Finnhub-Token` header |
| Finnhub | `/api/v1/company-news` (30 days) | `FinnhubNewsProviderRepositoryImpl.getCompanyNews` | Company News, Why Moved, Watch/Alerts news, Brief overlay | articles | `NewsService` `company:SYM` 10 min (empty 1 min) | news reads | header |
| Finnhub | `/api/v1/stock/profile2` | `FinnhubNewsProviderRepositoryImpl.companyName` | company-news relevance | name | in-class map 6 h (failure 5 min); **map unbounded** | first news read per symbol | header |
| Finnhub | `/api/v1/news` | `FinnhubNewsProviderRepositoryImpl.getNews` | Markets news, Brief | market news | `MarketsService` `section:news` 5 min; `/api/v1/news` route **uncached** | overview / route | header |
| Finnhub | `/api/v1/calendar/earnings` (window) | `FinnhubEarningsDataSource.calendar` | Earnings calendar | events | `calendar:FROM:TO` 1 h (past windows 12 h) + last-good copy | calendar page | header |
| Finnhub | `/api/v1/calendar/earnings?symbol=` (−3 y … +120 d) | `FinnhubEarningsDataSource.history` | Earnings details/results/reminders, Comparison latest-quarter growth, Watch "next earnings" | symbol history | `history:SYM` 6 h **and** `history:SYM:w` 6 h (two keys, see finding F2) | details / results / reminders / watch / compare | header |
| Bank of Canada | `/valet/observations/FXUSDCAD/json` | `BankOfCanadaPortfolioFx.rates` | Portfolio, Practice, Comparison FX | USD/CAD | `FROM:THROUGH` 6 h, **three separate instances** (`Application.kt`: portfolio, practice, comparison) | portfolio/practice/compare | none (public) |
| Gemini | `models/{model}:generateContent` | `GeminiJsonCall` (`news/GeminiInsights.kt`) used by `GeminiArticleInsightGenerator`, `GeminiMovementNarrator`, `GeminiBriefAi`, `GeminiEarningsAi`, `GeminiComparisonAi` | Article insight, Why Moved narration, Daily Brief AI, Earnings P5, Comparison P5 | generated JSON | see §6 | on demand | `x-goog-api-key` header |
| Gemini | same | `GeminiNewsSimplifier` (own client code) | News simplification | generated JSON | Firestore store (claim/save/fail), shared across instances | background queue from news reads (≤ 5 per response, queue 20, 1 worker) | header |
| Google | FCM `messages:send`, Firebase token certs | `FcmPushSender`, `FirebaseIdTokenAuthenticator` | push, auth | — | — | alerts/reminders/brief | ADC |

Internal StockSteps-only (no provider): research checklist CRUD (Phase 4), saved screens, watchlists, portfolio ledger, practice ledger,
learning progress, entitlements — Firestore only.

**MOCK** (`mockDataSources()`): `FixtureMarketDataSource`, `FixtureEarningsDataSource`, `FixtureScreenerUniverse`, `MockPortfolioFx`,
template AI providers, `InMemoryUserDataStore`, `MockUserAuthenticator`, simulated push — **no provider keys or network calls**
(verified: no `HttpClientProvider` use in the MOCK branch except Gemini/Firestore branches, which are REAL-only).

---

## 3. Feature → provider call graph (static, REAL, cold caches, one instance)

Counts are upstream requests, from the code paths above. "≈" marks data-dependent counts.

| Feature (route) | Initial entry | Refresh / return | Symbol change | Period change | Background | AI |
|---|---|---|---|---|---|---|
| Company Search (`/stocks/search`) | 2 FMP per debounced query | 2 again (no cache) | — | — | — | — |
| Company Details (`/stocks/{s}/details`, `CompanyDetailsService.getDetails`) | profile 1 + quote 1 + market hours 1 + fundamentals annual **11 datasets** + valuation: quarterly EPS (income q×24) 1 + daily closes 1 ≈ **16 FMP** | within TTLs: quote (30 s), market hours (60 s), ratios-ttm + key-metrics-ttm (5 min) → 1–4 | new symbol: ≈ 15 FMP (market hours shared) | — | — | — |
| Price chart | 1D: 5-min bars 1; others: daily closes 1 (shared) | 5 min / 6 h | per symbol | 1W…ALL: **0** (sliced locally); 1D separate | — | — |
| Why Moved (`movement`, Details preview) | quote (cached) + daily closes (shared) + SPY quote + SPY closes + Finnhub company-news 1 + profile2 1 | 1D result cached 5 min, 1W/1M 1 h | per symbol | per period: new build, data reused | — | Gemini narration (REAL, if key) per new fact digest |
| Financials (quarter) | +3 FMP (income/balance/cash quarter ×8); TTM/ratios reused | — | ≈ 14 FMP for an unseen symbol | annual ⇄ quarter: reuse | — | — |
| Valuation (`ValuationService.history`) | reuses fundamentals; +income q×24 + daily closes if not loaded | 6 h result cache | per symbol | — | — | — |
| Company News | Finnhub company-news 1 + profile2 1 | 10 min | per symbol | category/page: 0 (sliced) | simplification: ≤ 5 Gemini per response (Firestore-deduped) + Firestore reads per article | article insight: Gemini on demand (7-day cache) |
| Markets dashboard (`/markets/overview`) | ≈ 3 mover lists + most-active volume enrichment quotes + 11 sector-ETF quotes + index quotes/histories + Finnhub market news ≈ **25–35 calls** | overview 30 s live / 5 min closed; sections 60 s / 15 min | — | — | — | — |
| Watchlist (`/stocks/watch-data`, ≤ 100 symbols) | per symbol: quote 1 + profile 1 + Finnhub history 1 (next earnings) | quotes 60 s; profile 24 h; earnings 12 h | — | — | — | — |
| Smart alerts (`/internal/alerts/evaluate`) | — | — | — | — | per run: 1 quote per distinct alerted symbol (+ news feeds for news alerts) | — |
| Portfolio / Insights | quotes per holding (watch cache), BoC 1, daily closes per holding (history ranges) | 60 s quotes; 6 h closes | — | ranges: closes reused | — | — |
| Practice Portfolio | quotes/closes via `WatchPracticeMarket`; BoC (own instance) | as above | — | — | — | — |
| Guided Research (learning) | reuses `CompanyDetailsService.getDetails` | as Details | — | — | — | REAL research AI is `null` (unavailable) |
| Daily Market Brief | reuses Markets overview (0 extra when warm) + per-user watch overlay (watch cache) | — | — | — | dispatch job: overview + overlays | Plus Q&A: Gemini per question (no cache) |
| Earnings calendar | Finnhub window 1 + FMP profile per listed symbol for names/logos (≈ page size) | 1 h window; profiles 24 h | — | new window: 1 Finnhub | reminders pass: Finnhub history per distinct watched/reminded symbol (6 h) | — |
| Earnings details/results/reaction | Finnhub history ×2 (two keys) + daily closes 1 | 6 h | per symbol | reaction windows: 0 | weekly digest uses cached data | Phase 5 explain/ask/digest: Gemini (explanations shared per report version) |
| Screener | universe 1 (24 h); per search: records from caches | page cache 5 min | — | — | **warm-up: up to `SCREENER_FUNDAMENTALS_PER_HOUR` (25) symbols × 11–13 FMP ≈ 275–325 FMP/hour per instance** while the screener is used and the universe isn't warm | — |
| Comparison P1 (`/compare`) | per company: quote + profile + 11 fundamentals + Finnhub history (latest-quarter growth); BoC if CAD | fundamentals 6 h (screener map) / 24 h datasets | per new company | performance 1Y/3Y: daily closes 1 per company (shared) | — | — |
| Comparison P3 history | 1Y: `getFundamentals(quarter)` → **+3 FMP per company** (full quarterly bundle although only income is used); 3Y/5Y: annual bundle (reused) | 6 h statement cache | — | ranges reuse | — | — |
| Comparison P4 research | CRUD: **0 provider calls**; summary/export: reuse comparison + history caches | — | — | — | — | — |
| Comparison P5 AI | comparison 10-min cache + history cache: **0 FMP** when warm | — | new conversation | — | — | Gemini per non-cached request (+ ≤ 1 regeneration on invalid output) |

**Navigation example (AAPL: Details → Financials → Comparison → Guided Research), one instance:** Details ≈ 16 FMP; Financials
(quarter) +3; Comparison (AAPL vs MSFT): AAPL ≈ 0 FMP (quote/profile/fundamentals cached) + 1 Finnhub history (`:w` key, new), MSFT
≈ 13 FMP + 1 Finnhub + 1–2 closes; Guided Research → `getDetails` → ≈ 0–4 (quote/market-hours/ratios refresh if TTLs passed).
**The same AAPL statements are not refetched across these features on one instance** (shared `FmpFundamentalsLoader` cache).
They **are** refetched on every other Cloud Run instance that serves the same user (per-process caches).

---

## 4. Cache inventory

| Cache | Location | Key | TTL | Scope | Storage | Eviction | Concurrency | Consumers |
|---|---|---|---|---|---|---|---|---|
| `CompanyFinancialCache` (class) | `service/CompanyFinancialCache.kt` | caller-defined | caller TTL / `resultTtl` | per instance | memory, `LinkedHashMap` access-order | LRU by capacity (expired entries kept until evicted) | per-key `Mutex` (coalesces concurrent misses); **failures not stored** unless the caller wraps them | all below |
| FMP repo cache | `FmpStockProviderRepositoryImpl.financialCache` (capacity 512) | `quote:SYM`, `profile:SYM`, `endpoint:SYM:period:limit`, `success:…`, `cooldown:…` | 30 s / 24 h / 24 h / 6 h / **5 min** | public, per instance | memory | LRU 512 | single-flight; quote/profile **failures not cached** | all FMP fundamentals/quote/profile reads |
| `StockService.profileCache` (512) | `service/StockService.kt` | `SYM` | 24 h; failures cooldown | public | memory | LRU | single-flight | profile route, details, watch, comparison |
| `PriceChartService.cache` (256) | `service/PriceChartService.kt` | `daily:SYM`, `intraday:SYM` | 6 h / 5 min; 402/403 1 h; 429 10 min | public | memory | LRU 256 | single-flight + failure cooldown | charts, valuation, movement, comparison, portfolio, earnings |
| `SparklineService.cache` (256) | `service/SparklineService.kt` | `SYM` | 5 min | public | memory | LRU | single-flight + cooldown | Home sparklines (**separate from the 1D chart cache for the same 5-min bars**) |
| `MarketsService.cache` (128) | `service/MarketsService.kt` | `overview`, `section:*` | 30 s / 5 min; sections 60 s / 15 min; news 5 min | public | memory | LRU | single-flight + cooldown | Markets, Daily Brief |
| `InMemoryMarketSnapshotCache` | `service/MarketSnapshotCache.kt` | single value | 45 s | public | memory | — | one mutex | legacy `/market/snapshot` |
| `CompanyDetailsService.cache` (4) | `service/CompanyDetailsService.kt` | `status` | 60 s | public | memory | — | single-flight | Details |
| `ValuationService.cache` (512) | `service/ValuationService.kt` | `valuation:SYM` | 6 h | public | memory | LRU | single-flight | Valuation, Details |
| `MovementService.cache` (512) | `service/MovementService.kt` | `movement|SYM|P|version`, `quote|SYM`, `narration|version|digest` | 5 min (1D) / 1 h; quotes 60 s; narration 24 h | public | memory | LRU | single-flight | Why Moved |
| `NewsService.cache` (256) | `service/NewsService.kt` | `company:SYM` | 10 min (empty 1 min) | public | memory | LRU | single-flight | news, movement, watch, alerts, brief |
| Finnhub names map | `FinnhubNewsProviderRepositoryImpl.names` | `SYM` | 6 h / 5 min | public | memory | **none (unbounded)** | not synchronized against duplicate loads | company news |
| News simplification store | `FirestoreNewsSimplificationStore` | digest(article + version) | persistent | public, **cross-instance** | Firestore | none (retention unknown) | `claim()` prevents duplicate generation across instances | news |
| `ArticleInsightService.cache` (1024) | `news/ArticleInsights.kt` | article + content + version | 7 d / failure 15 min | public | memory | LRU | single-flight; 2 concurrent; 200/h per instance | article insight |
| `EarningsService.cache` (512) | `earnings/EarningsService.kt` | `calendar:F:T`, `history:SYM`, `history:SYM:w`, `names:q` | 1 h/12 h, 6 h, 6 h, 1 h | public | memory | LRU | single-flight; **failures not cached** (`lastGood` stale copy instead) | earnings, watch, compare, reminders |
| `EarningsPremiumService.explanations` | `earnings/EarningsPremiumService.kt` | reportId (version-checked) | until evicted (2,000 cap) | shared across Plus users (public data) | memory | bulk prune | in-flight coalescing | Earnings P5 |
| `ComparisonHistoryService.cache` (256) | `screener/ComparisonHistoryService.kt` | `SYM|period` | 6 h | public | memory | LRU | single-flight; budget `ProviderRequestBudget` | Comparison P3/P4/P5 |
| `ScreenerService` `records`/`fundamentals` maps, `pageCache` (128) | `screener/ScreenerService.kt` | `SYM`, query | 6 h, 5 min | public | memory | maps bounded by universe size | `Mutex` | screener, comparison |
| `ComparisonAiService.comparisons` (128), `shared`, `done`, `conversations` | `screener/ComparisonAiService.kt` | symbols; companies+type+range+context+prompt+model; uid+key; id | 10 min; until pruned; 15 min; 6 h | shared (public summaries only) / per user | memory | prune at 1,000 / 10,000 / 5,000 | coalescing; idempotency | Comparison P5 |
| `ComparisonAiQuota` | `users/{uid}/meta/aiUsage` | uid | rolling 30 d | per user, **cross-instance** | Firestore transaction | charges older than window dropped | transactional | Comparison P5 |
| `WatchMarketData.cache` (2,048) | `userdata/AlertEvaluator.kt` | `quote|SYM`, `profile|SYM`, `earnings|SYM`, `earnings-result|SYM` | 60 s, 24 h, 12 h, 30 min; failure 60 s | public | memory | LRU | single-flight; 6 permits | watch, alerts, portfolio, practice, brief |
| `PortfolioMarketService.cache` (256) | `userdata/PortfolioMarketService.kt` | `portfolio-history:SYM` | 6 h | public | memory | LRU | single-flight | portfolio (on top of `PriceChartService`) |
| `BankOfCanadaPortfolioFx.cache` (64) ×3 | `userdata/PortfolioMarketService.kt` | `FROM:THROUGH` | 6 h | public | memory | LRU | single-flight | portfolio, practice, comparison (**three independent instances**) |
| AI usage maps | `EarningsAiQuotaLedger`, `DailyBriefService.aiUsage`, `LearningService.aiUsage`, `EarningsService.aiUsage` | uid/day | daily | per user | memory | Earnings ledger resets daily; **Brief/Learning/Earnings-ask maps never pruned** | synchronized / `merge` | AI quotas |
| `ProviderUsageMeter.shared` | `service/ProviderUsage.kt` | provider/endpoint/feature/event | process life | — | memory | none (bounded by label set) | atomic | `/internal/metrics/usage` |
| `ProviderRequestBudget` | `service/ProviderUsage.kt` | feature | hourly window | per instance | memory | sliding | synchronized | comparison history/research only |
| Client caches | `core/.../ComparisonPresenter` (performance/history per selection), `EarningsResultsCache` (offline copies), presenters' `StateFlow` | — | session | per device | memory / SQL (`UserDataCache`) | — | — | apps |

### Cache-quality findings
1. **Failure stampede (verified)**: `CompanyFinancialCache.getOrLoad` stores nothing when the loader throws, so every queued waiter for
   the same key retries the upstream call one after another. Callers that wrap outcomes (`StockService.getProfile`, `PriceChartService`,
   `SparklineService`, `MarketsService.cached`, `FmpFundamentalsLoader` cooldown layer, `WatchMarketData`) are protected; the FMP repo
   `quote:`/`profile:` keys, `EarningsService` windows/histories (which also retry), `BankOfCanadaPortfolioFx` and
   `ComparisonHistoryService` are not.
2. **Missing dimensions**: none found for FMP datasets (`endpoint:symbol:period:limit` is complete). Finnhub history keys lack the
   window (fine: fixed −3 y/+120 d). Comparison AI context key is order-sensitive (`AAPL,MSFT` ≠ `MSFT,AAPL`).
3. **Overlapping keys for the same data (verified)**: income statements are fetched as quarter×8 (`load`) and quarter×24
   (`quarterlyEarnings`); 5-min bars twice (chart vs sparkline caches); Finnhub history twice (`history:SYM` vs `history:SYM:w`);
   BoC FX three times.
4. **Unbounded growth (verified)**: Finnhub `names` map; Daily Brief / Learning / Earnings-ask `aiUsage` maps keyed by uid+day.
5. **Stale presented as fresh**: quotes carry provider timestamps and Watch/Brief/Markets compare them with the session (`AlertRules.freshQuote`,
   `MarketsService.indexQuote`), so an old quote is not shown as today's — good. Nested TTLs can stack (profile 24 h × 3 layers ≈ up
   to ~72 h old, inferred); statements carry `retrievedAt`, not the cache-insertion time, which is the same moment — acceptable.
6. **User data in shared caches**: none found. Comparison AI keeps notes out of shared keys (tested); Brief overlay is per user;
   Earnings explanations contain report data only.
7. **Provider corrections**: no invalidation path; statements can stay up to 24 h after a restatement (by TTL only).
8. **Cross-instance**: every cache is per instance; N warm instances ≈ N× cold upstream traffic for the same symbols (inferred;
   instance count unknown).
9. **Licensing (requires verification)**: storing/reusing FMP and Finnhub data across users and serving derived values is assumed by the
   current design but is **not verified** against the provider terms or the account's plan (`docs/COMPANY_DETAIL.md` also flags this).
   Do not add persistent/shared storage before confirming retention and redistribution rights.

---

## 5. Freshness (existing behaviour)

| Data | Existing TTL(s) | Notes |
|---|---|---|
| Quotes | 30 s repo; 60 s watch/movement/markets live; markets closed 15 min | provider timestamp is compared with the session in watch/brief/markets |
| Intraday 5-min bars | 5 min (chart and sparkline, separately) | `fixtures/manifest.json` (doc evidence): the plan returned nothing for 1D intraday/sparklines → likely 402/403 → 1 h cooldown |
| Daily closes | 6 h | not session-aware (a close published after 16:00 ET may wait up to 6 h) |
| Profiles / market cap | 24 h (×3 layers) | market cap comes from the quote, so it's fresh |
| Statements | 24 h; TTM 6 h | not filing-aware |
| Ratios TTM / key metrics TTM | **5 min** | price-dependent ratios; refetched every 5 min even when markets are closed |
| Analyst estimates | 6 h | |
| Earnings calendar | 1 h (past 12 h) | last-good stale fallback, labelled |
| Earnings history/results | 6 h | results on report day can be up to 6 h late (watch "earnings-result" 30 min on top) |
| News | 10 min company; 5 min market | |
| FX (BoC) | 6 h | daily series; fine |
| Market status | 60 s (Details), 45 s (snapshot) | **a provider call for data `UsMarketCalendar` can compute locally** |
| AI | article insight 7 d; narration 24 h per fact digest; earnings explanation per data version; comparison summaries per context version | |

Timestamps: provider timestamp (quotes, filings, Finnhub publish time), retrieval (`retrievedAt`, `fetchedAt`), cache insertion (implicit
= retrieval) and expiry (implicit) are distinguished for quotes, statements and earnings; not for profiles, movers or market status.

---

## 6. Gemini usage (REAL)

| Feature | Class | Reaches Gemini in REAL? | Entitlement before call | Quota | Cache (incl. model/prompt version) | Output validated | Tokens tracked |
|---|---|---|---|---|---|---|---|
| Article insight | `ArticleInsightService` + `GeminiArticleInsightGenerator` | yes, if `GEMINI_API_KEY` | **none — public, unauthenticated route** | 200 starts/h **per instance**, 2 concurrent | 7 d per article+content+version | `InsightValidator` | no |
| Why Moved narration | `MovementService` + `GeminiMovementNarrator` | yes, if key | **none — public route** | **none** (only 8 s timeout) | 24 h per facts digest + version | `MovementNarrativeValidator` | no |
| News simplification | `NewsSimplificationService` + `GeminiNewsSimplifier` | yes, if key and Firestore store | none (background) | ≤ 5 queued per response, queue 20, 1 worker | Firestore, version in key, cross-instance claim | `validated()` | no |
| Daily Brief Q&A | `DailyBriefService.ai` + `GeminiBriefAi` | yes | StockSteps+ (`access`) | 15/day, **in memory** | none | `BriefAiValidator` | no |
| Earnings P5 explain/ask/digest | `EarningsPremiumService` + `GeminiEarningsAi` | yes | StockSteps+ fail-closed | daily per category, **in memory** | explanations shared per report data version + prompt + model | `EarningsAiValidator` | no |
| Earnings Q&A (Phase 1–4 ask) | `EarningsService.ask` | **no** (`research = null` in REAL) | — | in memory | — | — | — |
| Guided Research Q&A | `LearningService` | **no** (`research = null` in REAL) | StockSteps+ | in memory | — | — | — |
| Comparison P5 | `ComparisonAiService` + `GeminiComparisonAi` | yes | StockSteps+ fail-closed | **durable** (Firestore transaction) 10/day, 50/30 d | public summaries shared per context+prompt+model; notes never shared | `ComparisonAiValidator` | **yes** (`usageMetadata`) |

Concurrency vs quotas: the in-memory ledgers are per instance, so N instances allow up to N× the daily allowance (inferred). The
Comparison ledger is transactional and was tested with 15 parallel requests.

---

## 7. Security and reliability findings

- **Keys**: read from environment (`AppConfig`); FMP key travels as a query parameter (provider requirement) and is not logged by
  `apiCall`; Finnhub/Gemini use headers. Secret Manager wiring is not in the repo (unknown). No key in client code (verified).
- **Public cost-bearing routes without auth or rate limiting (verified)**: `/api/v1/stocks/search`, `/{s}/quote`, `/{s}/profile`,
  `/{s}/details`, `/{s}/fundamentals`, `/{s}/chart`, `/{s}/valuation`, `/{s}/news`, `/{s}/news/{id}/insight` (**Gemini**),
  `/{s}/movement` (**Gemini**), `/{s}/sparkline`, `/api/v1/news`, `/api/v1/market/*`, `/market/snapshot`, `/markets/overview`,
  `/stocks/watch-data` (up to 100 symbols per call). Screener/compare/earnings/AI/user routes do use `RequestRateLimiter` (per instance).
  Arbitrary valid-looking symbols bypass caches, so a scripted client can drive FMP/Finnhub/Gemini usage.
- **Retries**: `apiCall` none (good); `EarningsService.retrying` retries **any** failure once, including 429; AI services retry once on
  `UNAVAILABLE`. No circuit breaker; cooldown caches act as a partial breaker for FMP datasets/charts/sparklines only.
- **Timeouts**: 10 s client; 8 s Markets/Watch wrappers; 20 s comparison history; 3–20 s AI.
- **Fallbacks**: index quotes fall back to Finnhub when FMP returns no price; per doc evidence QQQ/DIA return HTTP 402 on the configured
  FMP account, so every snapshot rebuild spends 3 FMP + 2 Finnhub calls (legacy route).
- **Distributed quotas**: only Comparison AI. `ProviderRequestBudget` covers only comparison history/research and is per instance.
- **Firestore amplification**: `EntitlementService.get` reads `users/{uid}/meta/entitlements` on every premium request (no cache);
  Comparison AI performs 2–3 transactions per request (reserve, usage, release on failure); news enrichment reads one store document per
  article per news response (within a 1.5 s budget).
- **Observability**: `ProviderUsageMeter` meters only FMP fundamentals datasets, comparison history statements and comparison AI;
  quotes, profiles, search, movers, charts, sparklines, Finnhub, BoC and the other Gemini paths are not metered. `MarketsUsage` counts
  Markets calls separately. Attempts vs successes vs retries are only distinguished for FMP datasets (`upstream`, `cacheHit`, `cacheMiss`,
  `error`, `rateLimited`).
- **Legacy routes**: `/market/snapshot` and `/api/v1/market/gainers|losers` have no current app consumer found by static search (the
  iOS `DiscoveryViewModel` that calls gainers is not instantiated; Android registers `GetMarketSnapshot` but no screen uses it) — still
  publicly exposed and uncached (gainers/losers).
