# Feature → data inventory — Phase 9B (2026-10-17)

Base: `main` = `origin/main` = `e69626b` (8E, pushed by the parent session during 9B; 9A = `945601b`). Traced in code: routes (`Application.kt`, `*Routes.kt`), services (`service/*`, `screener/*`,
`userdata/*`, `practice/*`, `earnings/*`, `news/*`), adapters (`repositoryImpl/*`), caches (`CompanyFinancialCache` instances) and the
mobile clients (core `network/StockStepsApi.kt`, `data/userdata/UserApi.kt`). Phase 6 (`market_data_audit_phase6/PROVIDER_COMPATIBILITY_MATRIX.md`)
is the baseline; this table records what changed since (7A cooldowns, 7B EOD Markets, 7C EOD Portfolio/Practice, 7E curated Discover,
9A limits, 9B) and adds the proposed source and licensing class per dataset.

**Classes:** OFFICIAL_PUBLIC · LICENSED_COMMERCIAL · INTERNALLY_DERIVED · USER_OWNED · UNAVAILABLE_PENDING_LICENSE.
Every FMP and Finnhub row is **UNAVAILABLE_PENDING_LICENSE** for public display: it works technically, but the published terms restrict
display (see `LICENSING_RIGHTS_MATRIX.md`). In code: `marketdata/DataRights.kt` `LicenseRegistry.DEFAULT`.

**Freshness actually needed** (beginner education; no trading): intraday is never essential. Delayed is optional (only where licensed and
labelled), end-of-day is enough for every price screen, quarterly or annual for fundamentals.

## 1. Home and Markets

| Feature / dataset | Route | Exact fields used | Source today | Proposed source | Need | Cache TTL today | Attribution | Class / licence | Fallback | Requests (cold) | Criticality |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Index levels and change | `/markets/overview` (Home, Daily Brief, Markets) | close, previous close, close dates; quote only while active | FMP `quote` (^GSPC, ^IXIC, ^DJI, ^GSPTSE) while active; else own EOD → fund EOD proxy (7B) | licensed EOD vendor (index levels are often separately licensed by S&P/Nasdaq/Dow Jones — Q9) | EOD | overview 30 s live / 5 min closed; closes until next expected close | vendor + index owner | PENDING · fund proxy EOD same vendor | unavailable, never estimated | ≤ 8 EOD/day + quotes while active | high (Home) |
| Gainers / losers / most active | `/markets/overview` | symbol, name, price, change %, exchange (no volume since 7B) | FMP `biggest-gainers`/`biggest-losers`/`most-actives` | same vendor, display licence; or drop for launch | delayed/EOD | 60 s live / 15 min closed | vendor | PENDING | section unavailable | 3 per refresh | medium |
| Sector performance | `/markets/overview` | 11 SPDR fund closes, change between consecutive trading-day closes | FMP EOD (7B; no quotes) | licensed EOD | EOD | until next expected close | vendor; fund names are trademarks | PENDING | restricted / unavailable section | 11 EOD/day | medium |
| Market status | `/markets/overview`, Details | open/closed/holiday | **local calendars** (`UsMarketCalendar`, `TsxMarketCalendar`) | unchanged | — | 60 s | none | INTERNALLY_DERIVED | — | 0 | high |
| Market news | `/news`, Brief | headline, url, source, datetime | Finnhub `news` | Finnhub commercial / other licensed news (headline+link) | hourly | 5 min | publisher + Finnhub | PENDING | section unavailable | 1 per 5 min | medium |
| Featured / personalized brief | `/daily-brief/*` | server summary, indices, sessions | built from the data above; Gemini only for StockSteps+ | unchanged | daily | brief cache | — | INTERNALLY_DERIVED (inherits inputs) | deterministic text | 0 extra | medium |

## 2. Markets: Discover, screener, search

| Feature / dataset | Route | Fields | Source today | Proposed | Need | TTL | Class / licence | Fallback | Requests | Criticality |
|---|---|---|---|---|---|---|---|---|---|---|
| Curated universe (155) | `/screener/catalog`, `/screener/search` | symbol, name, exchange, sector (repo file) | `screener/curated-us-universe.json` (7E) | unchanged | static | — | INTERNALLY_DERIVED (StockSteps' list) | — | 0 | high |
| Enrichment: profile | same | provider name, sector, industry, exchange, country, market cap | FMP `profile` via shared 24 h cache | licensed vendor; name/exchange could come from SEC tickers (OFFICIAL_PUBLIC) | weekly | 24 h; refreshed on spare budget (9B) | PENDING (SEC part allowed) | values null, row excluded from value filters | ≤ 100/day total (9B cap) | medium |
| Enrichment: latest close and change | same | dated close, change % | FMP EOD (shared close cache) | licensed EOD | EOD | until next expected close | PENDING | older dated close or none | inside the 100/day | medium |
| Fundamentals filters (screener warm-up) | same | ratios, growth | FMP bundle; `SCREENER_FUNDAMENTALS_PER_HOUR` (0 on LAN REAL) | SEC facts + metrics engine (OFFICIAL_PUBLIC + derived) | quarterly | 24 h | SEC allowed; price ratios inherit price licence | excluded for missing data | 0 today | low |
| FMP screener path | `SCREENER_UNIVERSE=fmp` only | universe rows | FMP `company-screener` (402 on this plan) | not used | — | 24 h + 1 h cooldown | VERIFIED_RESTRICTED (plan) | curated list | 3/day | none (off) |
| Search | `/stocks/search` | symbol, name, exchange, currency, logo URL | FMP `search-symbol` + `search-name`; ranking `SearchRanking` | SEC `company_tickers_exchange.json` (US listings, OFFICIAL_PUBLIC) + licensed vendor for currency/non-US | static | 6 h per query | FMP PENDING; SEC allowed | empty result, typed error | 2 per uncached query | high |
| Logos | search rows, Details | FMP image URL (no API call) | FMP image host | licensed logo source or initials only | — | — | PENDING (logo display licence UNVERIFIED since 5D.5.5D) | ticker initials | 0 | low |

## 3. Company Details

| Dataset | Route | Fields | Source today | Proposed | Need | TTL | Class / licence | Fallback | Requests (cold) | Criticality |
|---|---|---|---|---|---|---|---|---|---|---|
| Identity | `/stocks/{s}/profile`, `/details` | name, exchange, currency, sector, industry, description, isEtf | FMP `profile` | name/CIK/exchange from SEC submissions; sector/description licensed | weekly | 24 h ×3 layers | FMP PENDING; SEC allowed | missing fields null | 1 | high |
| Quote, previous close, day range | `/stocks/{s}/quote`, `/details` | price, change, previous close, day high/low, 52-w range, volume, market cap, timestamp | FMP `quote` (402 for many symbols; 7A cooldowns) | last official close (EOD) labelled with its date; delayed quote only if licensed | EOD (delayed optional) | 30 s live, ≤ 15 min closed; refusal 1 h; 429 10 min | PENDING | dated close or unavailable, never $0 | 1 | high |
| Price chart 1W–ALL | `/stocks/{s}/chart` | date, close | FMP `historical-price-eod/light` (basis UNVERIFIED) | licensed EOD with stated basis (`DailyPriceSource`) | EOD | until next expected close | PENDING | unavailable | 1 | high |
| 1D chart / sparklines | `/stocks/{s}/chart?range=1D`, `/sparkline` | 5-min closes | FMP `historical-chart/5min` (empty on this plan) | drop for launch, or licensed delayed intraday | optional | 5 min | PENDING | hidden | 1 | low |
| Income / balance / cash-flow statements | `/fundamentals`, `/details` | revenue, gross profit, operating income, net income, diluted EPS, OCF, capex, FCF, cash, debt, assets, liabilities, equity, current assets/liabilities, fiscal period, period end, currency | FMP statements (+TTM), 402 for many symbols | **SEC EDGAR companyfacts** (9B adapter, not wired) → existing `FinancialPeriodStatement` contract | quarterly | 24 h earnings-aware; stale ≤ 7 d labelled | FMP PENDING; **SEC OFFICIAL_PUBLIC, allowed** | missing values null | ≈ 11 FMP → 1 SEC per company/day | high |
| Ratios and growth | `/fundamentals`, `/valuation` | P/E, P/S, margins, ROE, ROA, D/E, current ratio, growth, FCF | FMP `ratios-ttm`, `key-metrics-ttm`, `ratios`; server `FinancialCalculations`, `ValuationCalculations` | **9B metrics engine** on SEC facts + licensed price | quarterly; price ratios EOD | 15 min active / 6 h | non-price ratios INTERNALLY_DERIVED from SEC (allowed); price ratios inherit the price licence | null with reason | 0 extra | high |
| Valuation history (P/E vs history) | `/valuation` | monthly P/E from closes and quarterly diluted EPS | FMP EOD + 24-quarter income | SEC quarterly EPS + licensed EOD | monthly | 6 h | inherits price licence | unreliable/unavailable | 0 extra | medium |
| Analyst estimates | Valuation | EPS/revenue estimates | FMP `analyst-estimates` | licensed only (no official source) | daily | 6 h | PENDING | hidden | 1 | low |
| Dividends | fundamentals | ex-date, amount, yield | FMP `dividends` (402 for symbol tried) | SEC `CommonStockDividendsPerShareDeclared` (declared, per filing) + licensed corporate actions for ex-dates | quarterly | 24 h | SEC allowed; ex-dates PENDING | "not reported" (≠ no dividend) | 1 | medium |
| Earnings (calendar, results, reactions, reminders) | `/earnings/*`, `/me/earnings/*` | date, EPS est/actual, revenue est/actual, hour; reaction from EOD | Finnhub `calendar/earnings`, FMP EOD | actuals: SEC 10-Q/10-K (filing date, EPS); estimates/dates: licensed only | daily | 1 h / 12 h / 6 h | Finnhub PENDING; SEC actuals allowed | unavailable | 1–2 | medium |
| Company news | `/stocks/{s}/news` | headline, url, source, time | Finnhub `company-news`, `stock/profile2` | licensed news; SEC filings list (submissions) as an official "filings" feed | hourly | 10 min | PENDING; SEC allowed | section unavailable | 2 | medium |
| Why did it move? | `/stocks/{s}/movement` | observed move (closes), SPY comparison, news, Gemini narration | FMP quote+EOD, Finnhub news, Gemini | EOD + licensed news | daily | 5 min / 1 h / 24 h | inherits inputs | deterministic text | 3–5 | low |

## 4. Portfolio, Practice, Watchlist (signed in)

| Dataset | Route | Fields | Source today | Proposed | Need | TTL | Class | Notes |
|---|---|---|---|---|---|---|---|---|
| Ledger (transactions, accounts) | `/me/portfolio/*` | user-entered trades, cash, currency, dates | Firestore (`users/{uid}/…`) | unchanged | — | none (per user) | **USER_OWNED** | 9A central gate; never in shared caches |
| Valuation prices | same | latest valid official close ≤ 5 sessions old, dated | FMP EOD via shared close cache (7C) | licensed EOD | EOD | until next close | PENDING | total null if any holding lacks a close |
| History (account value) | same | daily closes × holdings, dated FX | FMP EOD + BoC | licensed EOD with stated basis | EOD | as above | PENDING (prices), BoC allowed | split basis risk: `PORTFOLIO_PRACTICE_DATA_CORRECTNESS.md` |
| FX | Portfolio, Practice, Comparison | USD/CAD by date | Bank of Canada Valet `FXUSDCAD` | unchanged | daily | 6 h | **OFFICIAL_PUBLIC, allowed** (attribution; indicative) | one cache per consumer (dup, Phase 6 #6) |
| Practice execution price | `/me/practice/*` | last close of the expected session (≤ 1 behind) | FMP EOD (7C) | licensed EOD | EOD | as above | PENDING | `QUOTE_RESTRICTED` / `QUOTE_STALE` / `QUOTE_MISSING` blockers |
| Practice ledger, idempotency | same | orders, request ids | Firestore transactions | unchanged | — | — | USER_OWNED | 7D verified no double fills |
| Watchlist | `/me/watchlists`, `/stocks/watch-data` | symbols (user); price, change, next earnings | Firestore; FMP quote + profile; Finnhub history | dated closes (as Portfolio) | EOD | 60 s quotes; 24 h; 12 h | USER_OWNED + PENDING | still quote-based (Phase 7C didn't change it) |
| Alerts | `/me/alerts`, `/internal/alerts/evaluate` | rules (user); quote per alerted symbol | Firestore; FMP quote | EOD-based alerts ("closed above") | EOD | watch cache | USER_OWNED + PENDING | 15-min job |

## 5. Learn, AI Comparison, future website

| Item | Source | Class | Notes |
|---|---|---|---|
| Learn terms and lessons | core catalogue (`LearnDisplay`) | INTERNALLY_DERIVED (StockSteps text) | no market data; examples must not quote live values |
| AI Comparison inputs | server-built context from profiles, fundamentals, closes, FX; Gemini (StockSteps+) | inherits inputs | provider terms must allow **derived/AI use** (FMP Q4, Finnhub "derived results"); freshness and missing fields are passed as explicit nulls/notes (existing validators) |
| Future website | same `/api/v1` contracts | — | **web rights are separate** (`DisplayChannel.WEBSITE`): a mobile display licence doesn't cover the web; the registry records channels per dataset |

## 6. What truly needs intraday

Nothing in the current product. The only intraday items are the 1D chart/sparklines (optional, empty on this plan), index quotes while the
market is active (7B: falls back to dated closes), Watchlist/Alerts quotes (can move to closes) and the Details quote (can show the last close
with its date). **Recommendation:** EOD everywhere for launch; delayed quotes later only where a licence covers them and labelled DELAYED
(`PriceSeriesRules.quoteDelay`). No real-time, no streaming (none in 9B).
