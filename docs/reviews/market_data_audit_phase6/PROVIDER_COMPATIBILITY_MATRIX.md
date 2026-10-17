# Provider compatibility matrix — Phase 6 audit (2026-10-16)

Status words: **VERIFIED WORKING**, **VERIFIED RESTRICTED**, **RATE LIMITED**, **CONFIGURATION ISSUE**, **NOT TESTED**, **NOT REQUIRED**.
"Observed" counts are from the LAN REAL container's usage summaries, 21:57Z–00:05Z (`upstream` attempts → outcome), unless marked
otherwise. Rights: every row is **UNVERIFIED** for display/redistribution. FMP's published terms say individual plans don't permit display
to third parties without a Data Display and Licensing Agreement. Finnhub's free tier is described as personal/non-commercial in
third-party summaries. HTTP 200 proves technical access only.

## 1. Feature → data (implemented)

| Feature | Internal route | Provider · endpoint | Required fields | Cache / TTL | Freshness need | REAL status |
|---|---|---|---|---|---|---|
| Markets: indices | `/markets/overview` | FMP `quote` (^GSPC, ^IXIC, ^DJI, ^GSPTSE), else proxy quote (SPY, ONEQ, DIA, XIC.TO); EOD for trends | price, previousClose, change, timestamp; close series | section 60 s live / 15 min closed; EOD 6 h | EOD enough | mixed (≈ 3 index quotes + EOD ok; proxies mostly refused) |
| Markets: gainers / losers / most active | same | FMP `biggest-gainers`, `biggest-losers`, `most-actives` (+ quote for missing volume) | symbol, name, price, change %, volume, exchange | 60 s / 15 min | delayed | lists OK; enrichment quotes mostly refused |
| Markets: sectors | same | FMP `quote` × 11 SPDR ETFs | price, previousClose, timestamp | 120 s / 30 min | EOD enough | **all 11 refused (402)** |
| Markets / Brief news | same, `/news` | Finnhub `news` | headline, url, source, time | 5 min | hourly | OK |
| Home | `/daily-brief/latest`, `/stocks/watch-data`, `/stocks/{s}/sparkline` | Markets overview; watch data; FMP 5-min bars | as above | as above | delayed | partial |
| Search | `/stocks/search` | FMP `search-symbol`, `search-name` | symbol, name, exchange, currency | 6 h | static | **OK** |
| Discover | `/screener/catalog`, `/screener/search` | FMP `company-screener` ×3; quote per row; fundamentals bundle | universe rows; ratios/growth | 24 h + cooldown (`be3c15e`, not deployed); page 5 min | daily | **BLOCKED** |
| Profile / logo | `/stocks/{s}/profile` | FMP `profile`; logo URL (no call) | name, exchange, currency, sector, industry, description, isEtf | 24 h ×3 | weekly | **OK** |
| Company Details | `/stocks/{s}/details` | profile, quote, 11 annual datasets, 24-quarter income, EOD; market status local | see Financials | see `CACHE_INVENTORY.md` | quote delayed, statements daily | partial |
| Quote / previous close / day range | `/stocks/{s}/quote` | FMP `quote` | price, previousClose, dayLow/High, yearLow/High, volume, marketCap, timestamp | 30 s (≤ 15 min closed) | delayed or last close | **mostly refused** |
| Charts | `/stocks/{s}/chart` | FMP `historical-price-eod/light`; `historical-chart/5min` (1D) | date, close; 5-min bars | 6 h; 5 min; 402 → 1 h | EOD; intraday optional | EOD OK; 1D NOT TESTED |
| Financial statements | `/stocks/{s}/fundamentals` | FMP `income-statement` (annual 6, quarter 8/24), `balance-sheet-statement`, `cash-flow-statement`, `*-ttm` | reported values, fiscal period, acceptedDate, currency | 24 h (earnings-aware), TTM 6 h; 402 → 1 h; stale ≤ 7 d labelled | daily | **refused for the symbol tried** |
| Ratios / Valuation | `/fundamentals`, `/valuation` | FMP `ratios-ttm`, `key-metrics-ttm`, `ratios`, `analyst-estimates`, `shares-float`; EOD + quarterly EPS | P/E, margins, EV, estimates | 15 min active; 6 h; valuation 6 h | daily | TTM/estimates OK; `ratios` refused |
| Dividends | inside fundamentals | FMP `dividends` | ex-date, amount | 24 h | daily | refused for the symbol tried |
| Earnings | `/earnings/*` | Finnhub `calendar/earnings` (window, per symbol), FMP EOD (reactions), FMP profile (names) | date, EPS est/actual, revenue est/actual, hour | 1 h / 12 h; 6 h | daily | **OK** |
| Company news | `/stocks/{s}/news` | Finnhub `company-news`, `stock/profile2` | headline, url, source, time, name | 10 min; names 6 h | hourly | NOT TESTED (no call in window) |
| Why Did It Move? | `/stocks/{s}/movement` | quote, EOD, SPY quote + EOD, Finnhub company news; Gemini | as above | 5 min / 1 h | daily | depends on quote |
| Watchlist | `/stocks/watch-data` | quote, profile, Finnhub history | price, change, next earnings | 60 s / 24 h / 12 h | delayed or close | quotes mostly refused |
| Alerts | `/internal/alerts/evaluate` | quote per alerted symbol | price, previousClose, timestamp | watch cache | 15 min | depends on quote |
| My Portfolio | `/me/portfolio/*` | quote, EOD per holding, BoC | price, close series, USD/CAD | 60 s; 6 h ×2 | last close | depends on quote |
| Practice | `/me/practice/*` | quote + profile (watch cache), EOD, BoC | price + timestamp, currency, close series | as above | **last close** | **trades blocked for refused symbols** |
| Comparison | `/compare*` | quote, profile, fundamentals, Finnhub history, EOD, BoC | as Details | as Details; 10 min | daily | depends on statements |
| AI (Gemini) | `/me/*/ai/*`, public insight/movement | server context only | — | version-keyed | — | CONFIGURATION ISSUE (no key on LAN REAL, D-R2) |

## 2. Endpoint classification

| Provider · endpoint | Calling service | Calls per user action | Observed (window) | Classification | TTL | Alternatives |
|---|---|---|---|---|---|---|
| FMP `search-symbol` | `FmpStockProviderRepositoryImpl.searchStocks` | 1 per uncached query | 11 → 11 ok | VERIFIED WORKING | 6 h | Finnhub `search` |
| FMP `search-name` | same | 1 per uncached query | 11 → 11 ok | VERIFIED WORKING | 6 h | — |
| FMP `profile` | `getProfile` (repo, `StockService`, watch) | 1 per new symbol/day | 21 → 21 ok | VERIFIED WORKING | 24 h | Finnhub `stock/profile2` |
| FMP `quote` | `getQuote` (all quote readers) | Markets build ≈ 19–26; Details 1; Watchlist 1/symbol; Practice 1/position | 139 → 24 ok, **111 × 402**, 4 × 429 (+14 refused locally) | **VERIFIED RESTRICTED (by symbol)** + RATE LIMITED | 30 s; no failure cooldown | EOD close (`historical-price-eod/light`); Finnhub `quote` (licence) |
| FMP `batch-quote` | not used | — | 402 (earlier probe) | VERIFIED RESTRICTED · NOT REQUIRED | — | — |
| FMP `sp500-constituent` | not used | — | 402 (earlier probe) | VERIFIED RESTRICTED · NOT REQUIRED | — | curated list |
| FMP `company-screener` | `FmpScreenerUniverse` | 3 per day (cached) | 7 → 6 × 402, 1 × 429 (+4 refused locally) | **VERIFIED RESTRICTED ("Restricted Endpoint")** | 24 h; 402 → 1 h (`be3c15e`) | curated universe |
| FMP `biggest-gainers` / `biggest-losers` / `most-actives` | `FmpMarketDataProvider.movers` | 1 each per Markets section refresh | 5 / 5 / 5 ok | VERIFIED WORKING | 60 s / 15 min | — |
| FMP `historical-price-eod/light` | `FmpPriceHistoryProvider.getDailyCloses` | 1 per symbol per 6 h | 3 → 3 ok (+ fixture capture: charts for sector ETFs and many stocks) | VERIFIED WORKING | 6 h | bulk EOD (tier UNVERIFIED) |
| FMP `historical-chart/5min` | `intradayBars` | 1 per symbol per 5 min (1D chart, sparklines) | 0 calls | NOT TESTED (fixtures: plan returned nothing on 2026-10-07) | 5 min; 402 → 1 h | drop 1D for MVP |
| FMP `income-statement` | `FmpFundamentalsLoader` | 1–2 per new symbol/day | 2 → 2 × 402 | VERIFIED RESTRICTED (symbol tried) | 24 h; 402 → 1 h | — |
| FMP `balance-sheet-statement`, `cash-flow-statement`, `income-statement-ttm`, `cash-flow-statement-ttm`, `ratios`, `dividends` | same | 1 each per new symbol/day | 1 each → 402 | VERIFIED RESTRICTED (symbol tried) | 24 h / 6 h; 402 → 1 h | — |
| FMP `ratios-ttm`, `key-metrics-ttm` | same | 1 each per 15 min active | 1 each → ok | VERIFIED WORKING | 15 min active, ≤ 6 h closed | — |
| FMP `shares-float`, `analyst-estimates` | same | 1 each per day / 6 h | 1 each → ok | VERIFIED WORKING | 24 h / 6 h | — |
| FMP `exchange-market-hours` | legacy snapshot only (Details uses `UsMarketCalendar`) | 0 | 0 | NOT REQUIRED | — | local calendar |
| Finnhub `news` | `FinnhubNewsProviderRepositoryImpl.getNews` | 1 per 5 min | 5 → 5 ok | VERIFIED WORKING | 5 min | — |
| Finnhub `calendar/earnings` | `FinnhubEarningsDataSource` | window 1/h; history 1 per symbol/6 h | 4 → 4 ok | VERIFIED WORKING | 1 h / 6 h | FMP earnings calendar (tier UNVERIFIED) |
| Finnhub `company-news` | `getCompanyNews` | 1 per symbol/10 min | 0 | NOT TESTED | 10 min | FMP news (tier UNVERIFIED) |
| Finnhub `stock/profile2` | news relevance names | 1 per symbol/6 h | 0 | NOT TESTED | 6 h | FMP profile (already cached) |
| Finnhub `quote` | legacy snapshot fallback; `QUOTE_PROVIDER=finnhub` | 0 | 0 | NOT TESTED | callers' | — |
| BoC `observations/FXUSDCAD` | `BankOfCanadaPortfolioFx` | 1 per window/6 h | 1 → 1 ok | VERIFIED WORKING | 6 h | — (public, terms of use apply) |
| Gemini `generateContent` | AI services | per AI request | 0 | CONFIGURATION ISSUE (no key, D-R2) | version-keyed | deterministic fallbacks |

Quota context: FMP's published Basic tier is 250 calls per day; paid tiers 300 / 750 / 3,000 calls per minute (published; UNVERIFIED for
this account). The 8 probes this session all returned 429 "Limit Reach". The LAN config `PROVIDER_FMP_PER_MINUTE=250` with no daily
target is a **CONFIGURATION ISSUE** if the key is on Basic. Finnhub: 60/min published for free.
