# Phase 6 — REAL market-data provider audit and cost optimization (audit and recommendation only)

Date: 2026-10-16 (evening ET; LAN REAL container clock 2026-10-17 00:05Z). Base: `main` at `be3c15e` ("Fix Discover Stocks on the LAN REAL
backend with distinct plan-restricted, rate-limited and temporary screener errors, …"), **committed and pushed** (`main == origin/main`,
clean tree). The REAL container runs image `stocksteps-local:ff5662ecba3e-dirty-…`, so the Discover fix is **not deployed** there.

No code, configuration, subscription, deployment or Ubuntu service was changed. Review package: `docs/reviews/market_data_audit_phase6/`
(`CHATGPT_REVIEW_PACKAGE.md`, `PROVIDER_COMPATIBILITY_MATRIX.md`, `API_USAGE_AND_COST_MODEL.md`, `CACHE_INVENTORY.md`,
`RECOMMENDED_MVP_ARCHITECTURE.md`).

**Verdict: NEEDS MORE INFORMATION.** The technical picture is clear. The provider decision depends on two answers only FMP (and Finnhub) can
give: which plan tier the key is on, and what a Data Display and Licensing Agreement costs for a consumer app. Under FMP's published terms, an
individual plan does **not** allow showing data to app users. That is a launch blocker for REAL whatever the tier.

## 1. Evidence and method

| Source | What it gave |
|---|---|
| Code (server `httpclient/NetworkUtils.kt`, `repositoryImpl/*`, `service/*`, `screener/*`, `userdata/*`, `practice/*`, `security/Admission.kt`, `service/ProviderBudget.kt`) | call graph, caches, TTLs, error mapping |
| REAL container log, 21:57Z–00:05Z (`docker logs stocksteps-real-api-1`, read-only; copy in the git-ignored scratchpad) | 21 `stocksteps.usage` JSON summaries (bounded labels, no symbols) and 130 sanitized `Provider HTTP failure` lines (path + status only); 0 lines containing `apikey`/`token=` |
| Fixture manifest (`server/src/main/resources/fixtures/manifest.json`, captured 2026-10-07 from the same REAL backend) | which datasets the plan returned per symbol at capture time (doc evidence) |
| `docs/DISCOVER_REAL_BACKEND_INTEGRATION_FIX.md` | earlier probes: `company-screener`, `sp500-constituent`, `batch-quote` → 402 "Restricted Endpoint"; `profile?symbol=AAPL` → 200 |
| 8 in-container probes this session (key expanded in the container, redacted from output) | **every one returned 429 "Limit Reach"** — see §4.2. No further probes were made. |
| Published pages (web search; FMP's own pages refuse automated fetches) | plan structure and display-licence wording; all prices **UNVERIFIED** |

Earlier audits (`FINANCIAL_API_*` Phases 1–5B) remain valid for the call graph; this audit re-checked the code and records what changed
since (local market status, shared failure single flight, shared 5-min bars, shared 24-quarter income request).

## 2. Current architecture (REAL)

Apps → Ktor server only (no provider host or key in `core/` or `app/`). Server providers:

| Provider | Configured by | Used for |
|---|---|---|
| FMP `/stable` | `FMP_API_KEY` (required in REAL) | search, quotes, profiles, movers, statements/ratios/estimates/dividends, EOD and 5-min history, screener universe, legacy index snapshot |
| Finnhub `/api/v1` | `FINNHUB_API_KEY` (required) | company news, market news, `stock/profile2` (news relevance names), earnings calendar and per-symbol history; quote fallback for the legacy snapshot; all quotes only if `QUOTE_PROVIDER=finnhub` |
| Bank of Canada Valet | none | USD/CAD for Portfolio, Practice, Comparison |
| Gemini | `GEMINI_API_KEY` (absent on LAN REAL, D-R2) | AI features (fallbacks without a key) |
| Firestore / Firebase | ADC / mounted service account (LAN REAL: opt-in read-only mount, `0696f7e`) | user data, entitlements, auth |

Flags and limits: `STOCKSTEPS_DATA_MODE`, `QUOTE_PROVIDER` (fmp), `MARKET_AWARE_TTL` (on), `EARNINGS_AWARE_STATEMENTS` (on),
`FMP_DATASET_CACHE_ENTRIES` (4,096), `SCREENER_EXCHANGES` (NASDAQ,NYSE,TSX), `SCREENER_UNIVERSE_LIMIT` (50), `SCREENER_MIN_MARKET_CAP` ($2B),
`SCREENER_FUNDAMENTALS_PER_HOUR` (25; **0 on LAN REAL**), `PROVIDER_<FMP|FINNHUB|GEMINI|BOC>_PER_MINUTE|BURST|CONCURRENCY|DAILY_TARGET`,
`PROVIDER_SAFETY_MARGIN` (0.5 on LAN REAL), `CLOUD_RUN_MAX_INSTANCES` (1), per-route `*_REQUESTS_PER_MINUTE`, `WATCH_DATA_ANONYMOUS_MAX_SYMBOLS`
(30), AI quota variables. LAN REAL sets `PROVIDER_FMP_PER_MINUTE=250`, `PROVIDER_FINNHUB_PER_MINUTE=60`, no `PROVIDER_FMP_DAILY_TARGET`.

Guards in the path of every upstream call: `apiCall` (GET only, no retries, logs host/path/status only) → `ProviderGuard.acquire`
(token bucket, concurrency, circuit breaker; 429 trips the provider, 402/403 don't) → `ProviderCalls.record` (the only `upstream` counter) →
admission `RouteGroup` charges the request one unit per upstream call. Usage summaries go to `StockSteps.Usage` every 60 s.

## 3. Feature-to-data inventory (implemented features)

Full table with fields and freshness: `PROVIDER_COMPATIBILITY_MATRIX.md` §1. Summary:

| Feature | Route | Provider endpoints | Cache (shared, per instance) | Freshness need (MVP) | REAL status (LAN, 2026-10-16) |
|---|---|---|---|---|---|
| Home / Daily Brief markets block, Markets tab | `/markets/overview`, `/daily-brief/latest` | FMP gainers/losers/most-actives, ~19–26 quotes (4 index, up to 4 ETF proxies, 11 sector ETFs, most-active enrichment), EOD for index trends; Finnhub `news` | overview 30 s live / 5 min closed; sections 60 s / 15 min (sectors ×2) | delayed or EOD is enough | movers OK; **sector ETF and most index-proxy quotes refused (402)** |
| Search | `/stocks/search` | FMP `search-symbol` + `search-name` | 6 h by normalized query | reference data | OK |
| Discover + filters | `/screener/catalog`, `/screener/search` | FMP `company-screener` ×3; quote per page row; fundamentals bundle per symbol (warm-up) | universe 24 h (+ cooldown since `be3c15e`, not deployed); page 5 min | daily | **BLOCKED (402 Restricted Endpoint)** |
| Profiles, logos | `/stocks/{s}/profile` | FMP `profile`; logos = FMP image URL (no API call) | 24 h ×3 layers | weekly is enough | OK |
| Company Details (+ Financials, Valuation) | `/stocks/{s}/details`, `/fundamentals`, `/valuation` | profile, quote, 11 annual datasets (+3 quarterly), 24-quarter income, EOD | quote 30 s (15 min max closed); TTM ratios 15 min active; statements 24 h (earnings-aware); estimates 6 h | quote delayed; statements daily | profile OK; quote mostly 402; statements **402 for the symbol tried**, TTM ratios/key metrics/shares/estimates OK |
| Quote / previous close | `/stocks/{s}/quote` | FMP `quote` | 30 s, no failure cooldown | delayed or last close | **mostly 402** (§4) |
| Charts 1W–ALL / 1D | `/stocks/{s}/chart` | FMP `historical-price-eod/light` / `historical-chart/5min` | 6 h / 5 min; 402 1 h | EOD / intraday optional | EOD OK (3/3); 5-min NOT TESTED (fixtures: plan returned nothing) |
| Why Did It Move? | `/stocks/{s}/movement` | quote, EOD, SPY quote + EOD, Finnhub company news; Gemini narration | 5 min / 1 h; narration 24 h | daily | depends on quote; Gemini not configured |
| News | `/stocks/{s}/news`, `/news` | Finnhub `company-news`, `stock/profile2`, `news` | 10 min / 5 min | hourly | market news OK; company news NOT TESTED in this window |
| Earnings calendar, history, results, reactions, reminders | `/earnings/*`, `/me/earnings/*` | Finnhub `calendar/earnings` (window and per symbol), FMP EOD, FMP profile for names | 1 h / 12 h; history 6 h (two keys) | daily | calendar OK (4/4) |
| Dividends | inside fundamentals | FMP `dividends` | 24 h | daily | 402 for the symbol tried |
| Watchlist | `/stocks/watch-data`, `/me/watchlists` | per symbol quote + profile + Finnhub history | quote 60 s, profile 24 h, earnings 12 h, failures 60 s | delayed / last close | quotes mostly 402 |
| My Portfolio valuation | `/me/portfolio/*` | quotes (watch cache), EOD per holding, BoC | 60 s; 6 h ×2 layers | last close is enough | works where quotes work; Firestore OK since `0696f7e` |
| Practice valuation and execution | `/me/practice/*` | quote + profile (watch cache), EOD, BoC | as above | **last close is enough** (§7) | **trading blocked (`QUOTE_MISSING`) for every symbol whose quote is refused** |
| Comparison (P1–P5) | `/compare*`, `/me/compare/*` | quote, profile, fundamentals bundle, Finnhub history, EOD, BoC; Gemini | as Details; comparison 10 min | daily | depends on statements/quotes |
| Gemini inputs | AI routes | server-built context from the data above (never provider calls from mobile) | version-keyed | — | not configured (D-R2) |
| Alerts evaluator | `/internal/alerts/evaluate` | 1 quote per alerted symbol per pass | watch cache | 15 min | depends on quote |
| Legacy | `/market/snapshot`, `/api/v1/market/gainers|losers` | FMP SPY/QQQ/DIA quotes → Finnhub fallback, movers | 45 s / none | — | no current app consumer (Phase 1 audit) |

Planned, not implemented (not costed as live): web client; Cloud Run staging; real-time streaming (not needed); bulk/EOD snapshot jobs.

## 4. Quote restrictions (high priority)

### 4.1 What the evidence shows

- Since the 21:57Z start: **139 FMP `quote` attempts → 24 ok, 111 denied (402), 4 rate limited (429)**; a further 14 were refused
  locally by `ProviderGuard` (circuit open after the 429s) and never reached FMP.
- The refusals arrive in a **fixed pattern per Markets build**: the minute summaries at 21:58, 22:05, 22:27 and 22:33 each show 26 quotes
  (21 refused, 5 ok), and 22:06 and 22:34 show 11 refused quotes. Eleven is exactly the Select Sector SPDR list (`MarketsService.SECTORS`,
  whose section lives twice as long as the others). So the same symbols are re-requested and re-refused on every rebuild.
- The fixture capture of 2026-10-07 (same backend) records that the plan returned **no quotes for the 11 sector ETFs and no index levels,
  but it did return their daily charts** (`stocks/XLK/chart-daily.json` exists; the manifest lists them as quote gaps). The same plan
  returned quotes and profiles for mega-caps and the day's small-cap movers.
- `profile`: 21/21 ok in this window (Home mover logos, Details). `profile?symbol=AAPL` → 200 in the earlier probe.
- Statements: for one Details open, `income-statement`, `balance-sheet-statement`, `cash-flow-statement`, `ratios`, `income-statement-ttm`,
  `cash-flow-statement-ttm` and `dividends` → 402, while `ratios-ttm`, `key-metrics-ttm`, `shares-float` and `analyst-estimates` → 200 for
  the same symbol. The fixtures have quarterly fundamentals only for AAPL, MSFT, NVDA, TSLA, TD and one TSX name, and keep KO, JPM, XOM,
  JNJ, RIVN and the TSX large caps without fundamentals.
- The server logs no symbols (by design), so the exact failing list can't be read back. The DEBUG `StockSteps-API` logs record app
  routes and statuses, but none of the saved captures cover a Markets or quote session on REAL.

### 4.2 Root cause (classification)

1. **Plan restriction by symbol and by endpoint (VERIFIED RESTRICTED, symbol list INFERRED).** The 402s aren't random or temporary: the
   same funds fail on every rebuild and in last week's capture, while EOD for those same funds works. That matches FMP's published plan
   matrix for its lower tiers: many datasets are "restricted by symbol, date range or result count". `company-screener`,
   `batch-quote` and `sp500-constituent` are restricted as endpoints (body "Restricted Endpoint").
2. **Daily request cap (RATE LIMITED, strongly indicated).** Every probe this session got 429 "Limit Reach … upgrade your plan". The
   server had made about 219 FMP attempts since 21:57Z, and the same key is used from the Mac (D-R3). FMP's published free tier
   (Basic) is **250 calls per day**. The LAN config sets `PROVIDER_FMP_PER_MINUTE=250`, which looks like the daily figure entered as a
   per-minute rate, so `ProviderGuard` (125/min after the 0.5 margin) can never protect the daily cap, and no daily target is set.
   **The tier wasn't confirmed** (owner question Q1).
3. `/stable/quote` vs `/stable/profile`: they **do** differ. Profile succeeded for every symbol requested; quote failed for most. Whether
   they differ for the *same* symbol wasn't proven: the planned probe pair (quote and profile for XLK and DDOG) hit the daily cap.

### 4.3 Budget, features and error handling

- **Denied calls consume budget.** Each 402 is a full upstream request: it uses a `ProviderGuard` token, is counted as `upstream`
  (`denied`) and charges the caller's admission units. Whether FMP counts 402 answers against the daily cap is **UNVERIFIED** (Q3),
  but the cap was reached after a session in which 125 of ~219 requests were refusals.
- **No cooldown for quote denials.** `FmpStockProviderRepositoryImpl.getQuote` caches only successes (`quote:SYM`, 30 s); the callers'
  layers are short: `WatchMarketData` 60 s for any failure, Markets sections only cool down when a whole section fails, Details nothing.
  The fundamentals loader (1 h), charts (1 h) and, since `be3c15e`, the screener universe (1 h) do cool down on 402/403.
- **Errors misstate the cause.** A 402 becomes `Failure.UNAVAILABLE` → route 502 `PROVIDER_UNAVAILABLE` "Market data is temporarily
  unavailable." (`ApiErrorHandling.kt`, `SnapshotErrors.kt`). That's the same misstatement fixed for Discover only. In Practice it shows as
  `QUOTE_MISSING` "There's no price … right now". The 429 daily cap is shown as "Try again later" and the breaker retries every 30 s to 5 min
  although the cap won't reset for hours.
- **Affected features:** Markets sectors and index proxies, Home/Brief market block, Company Details quote and Day Range, Why Did It
  Move?, Watchlist prices and alerts, My Portfolio market value, **Practice execution (blocked) and valuation**, Comparison quotes, Discover
  rows (if the universe worked). Charts, profiles, search, movers and earnings are unaffected.
- **Would cached EOD avoid quote calls?** Yes, for most features. The previous close and last close come from
  `historical-price-eod/light`, which this plan serves, and is already fetched and cached 6 h for charts, valuation and portfolio history.
  Markets sectors, Practice, Portfolio and Watchlist could be valued at the last close labelled "Close, <date>" and need no quote. Only an
  intraday "today" number needs a quote.
- **Should cooldowns extend to quotes?** Yes (recommendation only): cache a quote *outcome* per symbol with `providerCooldown` (402/403 1 h;
  429 10 min), and treat a 429 "Limit Reach" as a daily-cap state until the next reset instead of a 30 s breaker. Map 402 to a
  "not included in this server's market data plan" code like `SCREENER_PROVIDER_RESTRICTED`, not "temporarily unavailable".

## 5. Cheapest compliant sources

Key distinction kept throughout: **technical access** (what the key returns), **published pricing** (vendor pages, may change) and
**contractual rights** (display, caching, redistribution; only a signed agreement settles them). No vendor contract was available to this
audit.

| Option | Coverage / data | Quotas (published) | Display and redistribution rights | Cost | Effort | Risk |
|---|---|---|---|---|---|---|
| A. Current FMP key, permitted endpoints only | search, profile, movers, EOD, some ratios; quotes and statements for some symbols only; no screener | 250/day if Basic (UNVERIFIED tier) — below even 100 DAU | **Individual plans: no display to third parties** (FMP pricing page and ToS wording; UNVERIFIED for this account) | $0 now | low | **not launchable**: quota and licence |
| B. FMP paid individual tier (Starter/Premium/Ultimate) | Starter/Premium widen symbols, history and statements; the screener and bulk tier is UNVERIFIED (Q2) | published 300 / 750 / 3,000 calls per minute | still an individual licence: **display needs the Data Display and Licensing Agreement** | published list prices (third-party and FMP pages, e.g. Starter ≈ $19–29/mo, Premium ≈ $49–69/mo): **UNVERIFIED** | none (config) | licence gap unchanged |
| C. FMP + Data Display and Licensing Agreement | as the tier it's attached to | as the tier | display covered as negotiated; caching and derived-value terms to confirm | **UNVERIFIED (sales quote needed)** | none | single vendor; cost unknown |
| D. Finnhub (existing key) | news, earnings calendar, profile2, quote; `stock/metric` basic financials; US focus on lower tiers | 60/min published for free | free tier described as personal/non-commercial by third-party summaries; redistribution = enterprise | free now; paid tiers **UNVERIFIED** | medium (metric definitions differ from FMP's) | licence; mixed definitions |
| E. Hybrid FMP (fundamentals, EOD) + Finnhub (news, earnings, quotes) | best coverage per dataset of the two existing vendors | sum of both | both need commercial display terms | two contracts, **UNVERIFIED** | medium | two licences; "screener values match Company Details" holds only if one vendor feeds both |
| F. Other official commercial APIs with published display tiers (examples seen: Twelve Data external-display tier, Massive (ex-Polygon) business plans, Tiingo commercial with written redistribution approval, EODHD, Intrinio, Alpha Vantage commercial) | varies; EOD + fundamentals are common | varies | some publish an "external display" tier; exchange fees may apply for real-time, rarely for EOD through a vendor | third-party listings only — **UNVERIFIED** | high (new adapters, mapping, tests) | migration effort, different definitions |
| G. Curated universe on licensed EOD (any vendor above) | 100–150 named companies + ETFs/indices used by Markets | ~1 EOD + 1 profile per symbol per day, statements daily | as the vendor's licence | vendor licence | medium | must be labelled as a fixed list |

**No scraping** (Yahoo/Google Finance, Simply Wall St, exchange sites) and no unofficial wrappers were considered.

**Recommendation:** ask FMP for a Data Display and Licensing quote first (option C on the smallest tier that covers US symbols, EOD,
statements and, ideally, the screener and bulk EOD). FMP already powers every dataset, so no integration work is needed and the current
adapters, caches and tests stay. In parallel, get one competing written quote for an EOD + fundamentals vendor that publishes an external
display tier (option F), so the FMP answer can be judged. Keep Finnhub only for news and earnings if its commercial terms are acceptable;
otherwise move those to the same vendor.

## 6. Discover options

| | A. FMP plan with the screener | B. Curated real-company universe | C. Hybrid screener |
|---|---|---|---|
| What | the existing `FmpScreenerUniverse` works once `company-screener` is in the plan | a repo-owned list of 100–150 US large caps (+ ETFs), enriched per symbol with permitted `profile` (price, market cap, volume, sector, industry) and the existing fundamentals bundle | curated list for the universe + provider screener (FMP or Finnhub) for fields the list doesn't have |
| Effort | none (config) | medium: list file, `CuratedScreenerUniverse`, wording, tests | high |
| Ongoing requests | 3/day universe + quotes per page + warm-up (25/h × ~10 datasets ≈ 5,250/day/instance if enabled) | ~150 profiles/day + statements 150 × ~10 datasets/day ≈ 1,650/day; page quotes replaced by the EOD close | sum of both |
| Cache | universe 24 h + cooldown (already in `be3c15e`) | profile 24 h; statements 24 h; EOD 6 h; universe definition static | both |
| Filters supported | all current ones (market data, fundamentals once warm, choices) | sector, industry, market cap, price, volume, exchange; fundamentals only where statements are licensed | all |
| Unsupported | none | anything outside the list ("whole market"); small caps | — |
| Freshness | daily | daily (EOD) | daily |
| Subscription dependency | screener-capable FMP tier + display licence | any tier with profiles + statements for the listed symbols + display licence | two |
| Licensing risk | as FMP | as the vendor; must not reuse MOCK fixtures as REAL data | highest |
| Maintenance | none | review the list quarterly (listings, mergers, delistings) | highest |

**Is 100–150 enough for an educational MVP?** Yes, recommended. Beginners filter well-known companies; the S&P 100 plus a dozen ETFs
covers every sector and the Learn examples. It must be labelled "a fixed list of about N large US companies chosen by StockSteps, not the
whole market", never presented as a provider selection. MOCK fixtures may not be reused as REAL data. **Recommendation: B**, unless the FMP
display licence turns out to include the screener at no extra cost, then A (zero effort).

## 7. Practice and EOD

Current rule (`PracticeService.priceForTrade`): a trade executes immediately at the **fresh delayed quote** (open) or the **latest
closing price** (closed), only when `AlertRules.freshQuote` accepts it (today's session; ≤ `MAX_QUOTE_AGE_SECONDS` while open); USD/CAD only,
BoC FX at the quote date; Firestore ledger with idempotency. Valuation uses the same quote path. **On LAN REAL every refused quote blocks
the trade (`QUOTE_MISSING`)**, although EOD for the same symbol is available.

Requirements for licensed daily closes: the official close per exchange session with its date; history for performance; the listing
currency and exchange from the profile (already checked: only USD/CAD); split-adjusted vs unadjusted series stated (FMP `light` EOD is
used today; adjustment basis **UNVERIFIED**, Q6); corporate actions (splits, dividends) — REAL `CorporateActionSource` is not wired
(MOCK only), so a split would currently mis-value a holding; record it as a known gap.

| Model | How | Pros | Cons |
|---|---|---|---|
| **Immediate at the last close** | fill at the most recent official close (labelled "Close on <date>"), any time | simplest; one EOD request per symbol per day, shared by all users; deterministic; no scheduler | the price can be a day old during the session (labelled) |
| Queued at the next close | store a pending order, fill after the next close with that close | realistic "you don't know the price" lesson | pending-order state, scheduler job, notifications, cancellation, holiday handling, Firestore writes |
| Current (immediate at a fresh delayed quote) | quote during the session | intraday feel | needs licensed quotes for every symbol; blocked on this plan |

**Recommendation (no behaviour change now): immediate execution at the last official close**, shown with its date, valuations at the
same close, one cached EOD series per symbol shared across users and features. It is the most transparent model for beginners, needs no
quote licence, and removes Practice's dependence on the quote endpoint. Queued next-close fills can be a later teaching feature.

## 8. Cache inventory (summary)

Full table: `CACHE_INVENTORY.md`. All caches are per-process `CompanyFinancialCache` instances (bounded LRU, per-key single flight,
failures shared with joined callers but never stored) except Firestore-backed user data, news simplification, and durable AI quotas. Every
public provider cache is shared across users on one instance; no user data is in a shared key. A restart empties everything.

Observed in this window (cache events): `fmp` 3 hits / 198 misses / 13 joins (the refused quotes are never cached, so every request
misses); `profiles` 109/21 (84 % hits); `search` 14/11; `markets` 12/39; `price-chart` 12/3; `earnings` 16/4.

Duplicates and gaps:
1. quote denials have no cooldown (§4) — the largest avoidable waste;
2. Markets rebuilds re-request 11 sector ETF quotes every 2 min (live) though the plan never serves them;
3. profiles stack three 24 h layers (repo, `StockService`, `WatchMarketData`), up to ~72 h old (fine for profiles);
4. daily closes stack `PriceChartService` 6 h + `PortfolioMarketService` 6 h (up to 12 h; a new close can appear late);
5. Finnhub per-symbol earnings history is still cached under two keys (`history:SYM`, `history:SYM:w`);
6. BoC FX: one `BankOfCanadaPortfolioFx` per consumer plus Practice's own 1 h `fx:` layer.
Fixed since Phase 1: failure stampede, 5-min bars shared, 24-quarter income shared, local market status.

Justified additions only: quote outcome cooldowns; an EOD-session-aware quote TTL ("until the next session" when closed, which already
applies up to 15 min); a daily-cap state. Stale-while-revalidate already exists for statements (Phase 3E, 7 days labelled). No Redis or
persistent cache: cross-instance persistence needs the licence to allow it (Q4).

## 9. Cost model (summary)

Full model, assumptions and formulas: `API_USAGE_AND_COST_MODEL.md`. Requests/day, order of magnitude, one instance:

| DAU | FMP today, shared caches | FMP without shared caching | FMP, recommended EOD-first design | Finnhub (shared) |
|---:|---:|---:|---:|---:|
| 100 | ≈ 6,000 | ≈ 10,000 | ≈ 2,500 | ≈ 500 |
| 1,000 | ≈ 36,000 | ≈ 100,000 | ≈ 12,000 | ≈ 2,500 |
| 10,000 | ≈ 145,000 | ≈ 1,000,000 | ≈ 45,000 | ≈ 8,000 |

Even 100 DAU exceed a 250-per-day key by more than 20×. Paid FMP tiers are rate-limited per minute, not per call, so with shared caching
the request volume fits a Starter-class rate at every size. The cost question is the **licence**, not request volume. Subscription fees are
**UNVERIFIED**; cloud costs (Cloud Run, Firestore, logging) are separate and covered by `docs/CLOUD_RUN_COST_AND_SCALING.md`.

## 10. Recommended MVP architecture (summary)

Details: `RECOMMENDED_MVP_ARCHITECTURE.md`. One primary vendor per dataset, licensed for display:

- **EOD-first prices.** Every price a beginner sees in Markets, Watchlist, Portfolio and Practice is the last official close with its
  date. Delayed intraday quotes are optional, only where licensed, and always labelled.
- **Statements, ratios and profiles** from the same vendor (FMP today), daily, earnings-aware (existing).
- **News and earnings calendar** from Finnhub (existing) or the primary vendor, depending on the licence answers.
- **Discover** on a curated, labelled universe (§6 B).
- **The existing shared caches,** plus quote outcome cooldowns and a daily-cap state.
- **Guards with verified values:** `ProviderGuard` set to the verified per-minute and daily values (`PROVIDER_FMP_DAILY_TARGET`), and
  errors that say "not included in the plan" vs "temporarily unavailable".
- **Unchanged:** per-user isolation (Firestore, no user data in shared keys) and usage summaries for quota monitoring.
- **No new infrastructure.**

## 11. Open licensing and pricing questions (exact)

- **Q1 (FMP account):** Which plan is this API key on, and is its limit 250 requests per day or per minute? When does the daily counter
  reset (UTC midnight or rolling)?
- **Q2 (FMP):** Which tier includes `/stable/company-screener`, `/stable/batch-quote`, bulk EOD and quotes for every US-listed stock and ETF
  (including the Select Sector SPDR funds and index levels)?
- **Q3 (FMP):** Do HTTP 402 and 429 answers count against the request limit?
- **Q4 (FMP sales):** For a free consumer mobile app (later web) showing delayed quotes, end-of-day prices, financial statements, ratios
  and company profiles to the public:
  - what does the Data Display and Licensing Agreement cost per month at 1,000 and 10,000 monthly active users;
  - does it permit server-side caching shared across users, and for how long;
  - does it permit derived values (P/E history, growth rates, valuation comparisons) and AI-generated explanations built from the data;
  - does it permit showing company logos from the FMP image host;
  - does it require attribution?
- **Q5 (Finnhub sales):** Which plan permits commercial display of company news headlines/links, the earnings calendar and
  `stock/profile2` in a public app, with shared server caching? Price at 1k and 10k MAU?
- **Q6 (FMP docs/support):** Is `historical-price-eod/light` split-adjusted and/or dividend-adjusted, and which field is the official
  exchange close?
- **Q7 (exchanges/vendor):** Do delayed (15-min) US quotes shown in-app require exchange display fees or per-user reporting under the
  vendor's licence; are end-of-day prices exempt?
- **Q8 (alternative vendor):** For one EOD + fundamentals vendor with a published external-display tier, a written quote covering US
  equities and ETFs, five years of daily history, statements, display, shared caching and derived values.

## 12. Prioritized backlog (none implemented; each needs owner approval)

| # | Item | Why | Size |
|---|---|---|---|
| 1 | Owner answers Q1–Q4 (FMP tier and display licence) | launch blocker; decides everything below | owner |
| 2 | Fix the LAN config: set `PROVIDER_FMP_PER_MINUTE` / `PROVIDER_FMP_DAILY_TARGET` to the verified values | the guard can't protect a daily cap today | config |
| 3 | Quote outcome cooldown (402/403 1 h, 429 10 min) + daily-cap state for "Limit Reach" | stops repeated refused calls (≈ 21 per Markets build) | S, server |
| 4 | Plan-restricted error code for quote/statement 402s (as `SCREENER_PROVIDER_RESTRICTED`); Practice/Watchlist copy "not included in this server's market data plan" | errors say "temporarily" today | S, server + core display |
| 5 | Markets: drop or EOD-back sectors and index proxies that the plan refuses; use index EOD closes | 11–15 refused quotes per rebuild | S |
| 6 | Practice: execute and value at the last official close (§7) | removes the quote dependency; owner decision on the model | M |
| 7 | Discover option B (curated, labelled list) | Discover blocked on REAL | M |
| 8 | Merge Finnhub history keys; one BoC FX cache | small duplicate calls | S |
| 9 | Wire a REAL corporate-actions source for Practice (splits) | correctness gap once REAL Practice works | M, needs licensed data |
| 10 | Redeploy LAN REAL with `be3c15e` (approval) | Discover cooldown not live yet | ops |

## 13. Limits of this audit

- The failing quote symbols are inferred from timing and the fixture capture, not read from a log (symbols are never logged).
- The 8 probes this session reached FMP and got 429; they bypass the server's counters and may have counted against the daily cap.
  No more probes were made.
- One REAL window (~2 h, one evening, markets closed after 20:00 ET for most of it) was observed; no weekday-open traffic.
- All prices and licence statements come from public pages and third-party summaries: **UNVERIFIED** until a vendor confirms in writing.

Sources (published pages, retrieved 2026-10-16): FMP pricing (site.financialmodelingprep.com/developer/docs/pricing), FMP article "Can You
Use FMP Data in a Public App or Website?", FMP Terms of Service, FMP plan-selection article; Finnhub pricing (finnhub.io/pricing) and
third-party summaries (apicostcalc.com); vendor comparisons (marketdata.app licensing guide, moneyflock.com, apiscout.dev).
