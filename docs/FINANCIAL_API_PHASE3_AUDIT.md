# Financial API — Phase 3 audit (read-only)

Date: 2026-10-09. Repository: `main` at **"Add financial API cost audit and Phase 2 shared provider cache with request reuse"**
(`75760ab`), clean working tree before this audit. Only test code and documents were added (a MockEngine benchmark,
`server/src/test/.../service/Phase3AuditBenchmarkTest.kt`); no production code, providers, Firestore or deployments were touched.

Evidence labels: **measured** (MockEngine benchmark, REAL adapters, no network), **verified (code)**, **inferred**, **unknown**.

## 1. Executive summary

1. **Screener warm-up is the single largest background cost and it is confirmed**: 25 symbols per hour × 13 upstream requests
   (11 fundamentals datasets + quote + profile) + 3 universe requests = **328 requests in the first warm-up hour per instance**
   (measured, scenario D; matches the Phase 1 estimate of ≈ 325).
2. **Correctness bug found (measured)**: screener fundamentals are **never re-warmed after their 6-hour TTL**. `ScreenerService.warm()`
   only selects symbols with `fundamentals[symbol] == null`, but expired entries stay in the map, so after 6 h financial filters evaluate
   **0 of N** companies (15 → 0 in the test) until the process restarts. This silently empties financial screens; it is a **P0
   correctness blocker** (not a cost saving) and should be fixed first in Phase 3B.
3. **Over-fetching for income-only callers (measured)**: Comparison 1Y history needs only quarterly income statements but triggers the
   quarterly bundle: **12 requests for four companies where 4 would do**; 3Y/5Y need only annual income (already loaded in practice).
4. **Most consumers need almost the whole bundle**: Company Details/Financials need all 11 datasets; the screener and Comparison P1 need
   10 of 11 (all except historical annual `ratios`). Selective loading therefore saves little on the main screens; its benefit is
   targeted (history, earnings growth helpers, AI context), not broad.
5. **Closed-market refresh waste (measured)**: TTM ratios and key metrics refetch every 5 minutes regardless of session — 4 extra
   requests in 20 minutes per viewed symbol on a Saturday (scenario F).
6. **Earnings staleness (measured)**: a newly published quarterly statement was invisible for up to **24 hours** (statement TTL);
   a provider correction likewise waits for TTL expiry (scenario G). No earnings-aware invalidation exists.
7. **Single flight works within an instance (measured)**: 50 concurrent users over 10 symbols → exactly 15 requests per symbol (scenario E).
8. **Premium research is free of extra provider calls once a comparison is loaded (measured)**: 3Y + 5Y history, the detailed research
   summary and an AI question added **0** upstream requests after the comparison (scenario C).
9. **Unresolved security blockers**: no per-client limits on public routes (client identity behind Cloud Run unresolved); per-instance
   caches and budgets multiply with instance count; in-memory AI quotas outside Comparison AI.

## 2. Current architecture (Phase 2 as implemented — verified in code)

- `CompanyFinancialCache` (`service/CompanyFinancialCache.kt`): per-key single flight, shared failures (not stored), cancellation
  hand-over, expired-first/LRU trimming, stats. Present.
- Duplicate-path removals present: one Finnhub history key (`EarningsService.events → history`), 24-quarter income shared
  (`FmpFundamentalsLoader.load`), raw 5-min bars cached in `FmpPriceHistoryProvider`, single BoC instance, calendar market status
  (`CompanyDetailsService.marketStatus`), search cache (`StockService`), `ProviderCalls` metering in `apiCall`, narration budget. Present.
- All caches and single flight are **per process**. Budgets: `ProviderRequestBudget` (comparison history/research only),
  `SCREENER_FUNDAMENTALS_PER_HOUR` (screener warm-up), narration 300/h, article insights 200/h — all per instance.

## 3. Financial dataset inventory (`FmpFundamentalsLoader.load`, verified)

| Dataset | FMP endpoint | TTL | Rows | Used for (mapper outputs) | Required by | Independently loadable? |
|---|---|---|---|---|---|---|
| Annual income | `income-statement?period=annual&limit=6` | 24 h | 6 FY | annual growth/CAGR, `forwardPe` date anchor, P/E history validity, annual figures, statement history (FY) | Details, Financials, Screener, Comparison P1/P3 3Y/5Y, Research | yes (own key) |
| Quarterly income | `income-statement?period=quarter&limit=24` | 24 h | 24 Q (8 shown) | quarterly growth, valuation P/E series, statement history (Q) | Financials q, Valuation, Comparison P3 1Y | yes |
| Balance sheet | `balance-sheet-statement?period=annual|quarter&limit=6|8` | 24 h | | cash, debt, equity, D/E fallback, current ratio fallback, statement history, debt/equity trend | Details, Financials, Screener (cash, debt), Research detailed summary | yes |
| Cash flow | `cash-flow-statement?period=…` | 24 h | | OCF, capex, FCF, FCF growth, buybacks, statement history | Details, Financials, Screener (FCF, OCF, FCF growth), Research | yes |
| Ratios TTM | `ratios-ttm` | **5 min** | 1 | margins, D/E, current/quick ratio, interest coverage, P/E, P/S, P/B, P/FCF, PEG, dividend yield/DPS/payout | everything showing valuation/ratios | yes |
| Key metrics TTM | `key-metrics-ttm` | **5 min** | 1 | ROE, ROA, ROIC, EV, EV/EBITDA, net debt/EBITDA | Details, Screener (ROE, ROIC, EV/EBITDA) | yes |
| Historical ratios | `ratios?period=annual&limit=6` | 24 h | 6 | 5-year valuation comparisons (P/E, P/S, P/B, P/FCF, EV/EBITDA) | Details/Financials valuation history; Valuation fallback | yes |
| Income TTM | `income-statement-ttm` | 6 h | 1 | positive-earnings guards (P/E, PEG, payout), EBITDA guard, interest-expense guard, TTM basis labels | everything showing P/E/payout/EV-EBITDA/interest coverage | yes |
| Cash flow TTM | `cash-flow-statement-ttm` | 6 h | 1 | P/FCF guard, **"no trailing dividend" detection** (affects `dividendYield` NO_DIVIDEND vs MISSING) | Details, Screener/Comparison dividend yield | yes |
| Analyst estimates | `analyst-estimates?period=annual&limit=10` | 6 h | | `forwardPe` | Details, Financials, Screener/Comparison advanced (forward P/E) | yes |
| Dividends | `dividends?limit=100` | 24 h | ≤100 | dividend growth, no-dividend detection | Details, Screener/Comparison (dividend growth, yield semantics) | yes |
| Shares | `shares-float` | 24 h | 1 | shares outstanding; **market cap from shares × price** in `CompanyRecordBuilder.marketCap` | Details, Screener/Comparison market cap | yes |
| Quote / profile | `quote` (30 s), `profile` (24 h) | | | price context for forward P/E (currency match), market cap fallback | all | yes (already separate) |

Indirect dependencies that make "invisible" datasets necessary: `cash-flow-statement-ttm` changes the **missing-data semantics** of
dividend yield; `income-statement-ttm` gates P/E, PEG, payout and EV/EBITDA validity; `shares-float` feeds market cap (with quote
fallback); annual income anchors which analyst-estimate year is "next fiscal year". Removing them would change displayed availability,
not just hide numbers — so they cannot be dropped for screens that show those metrics.

## 4. Feature → dataset dependency matrix

R = required, O = optional (a displayed but secondary metric), F = fallback-only, — = not needed.
Columns: Inc-A annual income, Inc-Q quarterly income, Bal, CF, RT ratios-TTM, KM key-metrics-TTM, RH historical ratios, IT income-TTM,
CT cash-flow-TTM, Est estimates, Div dividends, Sh shares; P/Q profile/quote; D daily closes; FH Finnhub history.

| Feature / state | Inc-A | Inc-Q | Bal | CF | RT | KM | RH | IT | CT | Est | Div | Sh | P/Q | D | FH |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Details — Overview (annual) | R | — | R | R | R | R | R | R | R | O | R | R | R | — | — |
| Details — Financials quarter | O | R | R (q) | R (q) | R | R | R | R | R | O | R | R | R | — | — |
| Details — Valuation | F | R | — | — | R | — | F | R | — | — | — | — | R | R | — |
| Details — News / Why moved | — | — | — | — | — | — | — | — | — | — | — | — | Q | R | — (Finnhub news) |
| Comparison P1 (beginner + advanced rows) | R | — | R | R | R | R | — | R | R | O | R | R | R | R (perf) | R (latest-quarter growth) |
| Comparison P2 (guided interpretation) | same data as P1 (no extra) | | | | | | | | | | | | | | |
| Comparison P3 1Y (free) | — | R | — | — | — | — | — | — | — | — | — | — | P | — | — |
| Comparison P3 3Y/5Y (Plus, checked first) | R | — | — | — | — | — | — | — | — | — | — | — | P | — | — |
| Comparison P4 basic summary | P1 data + P3 1Y | | | | | | | | | | | | | | |
| Comparison P4 detailed summary/PDF (Plus) | R (5Y) | — | R | R | (P1) | | | | | | | | | | |
| Comparison P5 AI (Plus) | P1 data (+ P3 when history is asked) — no extra datasets | | | | | | | | | | | | | | |
| Screener (financial filters/sort) | R | — | R | R | R | R | — | R | R | O | R | R | P | — | — |
| Screener (market-only filters) | — | — | — | — | — | — | — | — | — | — | — | — | universe + page quotes | — | — |
| Guided Research | = Details Overview (`getDetails`) | | | | | | | | | | | | | | |
| Portfolio / Insights | — | — | — | — | — | — | — | — | — | — | — | — | R | R | — |
| Earnings Intelligence | — | — | — | — | — | — | — | — | — | — | — | — | P | R (reaction) | R |
| Daily Brief | — | — | — | — | — | — | — | — | — | — | — | — | Q (overview) | — | R (overlay) |
| Markets | — | — | — | — | — | — | — | — | — | — | — | — | Q | R (index trends) | — |
| Watchlist | — | — | — | — | — | — | — | — | — | — | — | — | R | — | R |
| Practice | — | — | — | — | — | — | — | — | — | — | — | — | Q | R | — |

Minimum sets for Comparison: initial comparison and beginner explanations = P1 set (10 datasets; `ratios` historical not needed);
latest-quarter comparison = Finnhub history (already); 1Y history = quarterly income only; 3Y/5Y = annual income only; detailed summaries =
annual income + balance + cash flow; AI = whatever the comparison/history already loaded. Premium checks already precede premium-only
provider work (`ComparisonHistoryService.userHistoryIn`, `ComparisonResearchService.requirePlus`, `ComparisonAiService.requirePlus`) —
verified, and covered by `FinancialCacheTest.premiumHistoryStaysPremiumAlthoughStatementsAreShared`.

## 5. Selective loading — answers

1. **Can `FmpFundamentalsLoader` support it without major refactoring?** Yes. Each dataset already has its own key and cooldown
   (`dataset<T>(endpoint, symbol, period, limit, ttl)`); `FmpFundamentalsMapper.history(period, income, balance, cash, today)` is a
   separate function that tolerates empty balance/cash lists. A `statementHistory(symbol, period, include: Set<Statement>)` method on the
   existing loader/repository is a small addition.
2. **Partial success?** Yes — `FinancialDataset.availability` per dataset is already returned and annotated; independent loads keep it.
3. **More requests during rapid navigation?** No: a later full load reuses the same dataset keys (income q×24, income annual×6), so the
   selective request is a strict subset of the full one.
4. **Overlapping concurrent requests?** Per-key single flight in `CompanyFinancialCache` merges them (measured in E).
5. **Can one dataset serve multiple screens?** Yes, by key (see compatibility matrix in `FINANCIAL_API_CACHE_IMPLEMENTATION.md`).
6. **Missing/denied datasets?** Keep today's semantics: success cached by TTL, `TEMPORARILY_UNAVAILABLE` 30 s, 402/403 1 h cooldown;
   never substitute another dataset.
7. **Provenance/periods?** Rows carry `date`, `period`, `fiscalYear`, `reportedCurrency`, `acceptedDate`; `retrievedAt` stays on the result.
8. **API contract unchanged?** Yes — the change is internal to `ComparisonHistoryService.fundamentalsOf` and the screener's
   `fundamentalsOf`; responses keep their shape.
9. **Apps with independently loading sections?** Not needed for the recommended scope (server-side composition stays one response
   per route). Splitting Details into per-section routes is **not** recommended: it adds round trips for little provider saving.
10. **Waterfalls?** Load required datasets concurrently (as `load` does with `async`); never chain dataset B after A.

Designs compared:
| Design | Change | Saving | Risk |
|---|---|---|---|
| **A. Targeted statement accessors (recommended)** | add `statementHistory(symbol, period, statements)` and let `ComparisonHistoryService` (and Research detailed summary) use it | 1Y history: 3 → 1 request per company; summaries unchanged | low |
| B. Typed dataset requirements for every consumer (`Set<Dataset>` → partial `CompanyFundamentals`) | rework `getFundamentals` and mapper annotations per requirement set | screener/Comparison P1: 11 → 10 (drop `ratios`); others ≈ same | medium: partial objects risk wrong availability labels |
| C. Per-section client loading | new routes + app loading states | small | high |

## 6. Screener warm-up (verified + measured)

Workflow (`ScreenerService`): `catalog()`/`search()` → `snapshot()` → `universe()` (FMP `company-screener` **once per exchange**,
default NASDAQ/NYSE/TSX, `limit` 100, cached 24 h) → `record(entry, loadFundamentals = fullRecords=false)` for every entry (no provider
call; uses cached fundamentals if fresh) → `warm(entries)`.
- Starts lazily on the first catalog/search of an instance (not at startup). Background (`scope.launch`), concurrency 4
  (`permits = Semaphore(4)`), budget `SCREENER_FUNDAMENTALS_PER_HOUR` (25) per rolling hour per instance.
- Per symbol: `getFundamentals(symbol, "annual")` = 11 datasets + quote + profile = 13 cold requests (measured).
- Universe up to 300 symbols → **≈ 12 hours** to warm fully at 25/h; but records/fundamentals expire after **6 h** and — due to the bug in
  §1.2 — are never re-warmed. Coverage therefore peaks at ≤ 150 symbols (6 h × 25) and falls to **0** once every universe symbol has
  been warmed once and expired (≈ 12–18 h after the first screener use on an instance); from then on warm-up spends nothing and
  financial filters match nothing. **Fixing the bug will raise steady-state screener cost above today's** (see the cost model) — the
  current low steady-state cost is a symptom of the bug, not an efficiency.
- Users cannot force extra warm-ups beyond the budget; every instance warms independently (×instances).
- Search pages fetch one quote per row (≈ 20, 30 s cache); filters/sorts reuse cached records (searches added 0 requests in D).
- Provider batching: FMP offers bulk/batch endpoints on some plans (e.g. batch quotes, bulk ratios/metrics); **availability on the account
  is unknown** — would replace 300 × 13 requests with a handful of bulk calls if licensed.

Options (estimates in the cost model): fix re-warm (correctness), lightweight screener dataset set (10 of 11), budget across instances,
cached screener snapshot (requires D1 licensing), on-demand enrichment of only the page shown, smaller default universe, bulk endpoints.

## 7. Freshness (verified TTLs) and market awareness

Actual values: quotes 30 s (`FinancialCachePolicy.QUOTE`; watch/movement 60 s; Markets 60 s live / 15 min closed via `UsMarketCalendar`);
TTM ratios/key metrics 5 min; statements 24 h; TTM statements and estimates 6 h; search 6 h; intraday 5 min; daily closes 6 h; profiles
24 h; earnings history 6 h, calendar 1 h (12 h past); news 10 min; FX 6 h. Only Markets is session-aware today. A TSX calendar already
exists (`brief/BriefSessions.kt` `TsxMarketCalendar`; per-symbol calendar choice in `earnings/PriceReactionEngine.kt`) — reuse it, never apply
NYSE hours to `.TO` symbols.

| Data type | Current TTL | Open market | Closed (weekend/holiday/overnight) | Invalidation event | Risk |
|---|---|---|---|---|---|
| Quote | 30 s | 30–60 s | until next pre-open of the listing's exchange (keep provider timestamp; label "as of close") | session open | low (provider timestamp shown) |
| Ratios/key metrics TTM | 5 min | 15 min | until next open | statement publication | low–medium (P/E moves with price; closed-market price is fixed) |
| Intraday bars | 5 min | 5 min | until next open | — | low |
| Daily closes | 6 h | 6 h | until next close + ~2 h publication lag | session close | low |
| Statements | 24 h | 24 h; 1–2 h near verified reports | 24 h | report evidence (§8) | medium |
| Estimates | 6 h | 6–12 h | 24 h | report date | low |
| Earnings history | 6 h | 30 min on report day ±1 | 12–24 h | report date | low |
| Profiles, search | 24 h, 6 h | keep | keep | — | low |

Pre-market/after-hours: treat as "open" for quotes (prices still move), "closed" for ratio refreshes (ratio sources update on regular
sessions — inferred, verify with provider). Observation time (quote timestamp, statement `acceptedDate`) must stay distinct from cache age.

## 8. Earnings-aware invalidation

Available verified metadata: Finnhub calendar events (`EarningsEvent.date`, `session`, `fiscalYear/Quarter`, `periodEnd`, `actual`
when reported, `updatedAt`); FMP statement rows (`date` = period end, `period`, `fiscalYear`, `acceptedDate` = filing acceptance).
Missing: an explicit "statement available" signal and revision markers. A calendar event or an `actual` value does **not** mean FMP
statements are published (filings lag the press release).

Proposed strategy: when a symbol's earnings `actual` appears (or its report date passes) and the cached statements' newest `date` is
older than that event's `periodEnd`, shorten that symbol's statement TTL to 1–2 h (bounded, e.g. for 10 days) until a row with that
`periodEnd` appears; then return to 24 h. Corrections: keep the 24 h TTL (no evidence source); optionally compare `acceptedDate`/values
on refresh and log changes. Late publication: the shortened TTL keeps polling at most every 1–2 h for that symbol only.

## 9. Stale-while-revalidate

| State | Statements/estimates/profiles/daily closes | Quotes/TTM ratios | Earnings | AI |
|---|---|---|---|---|
| Fresh | serve | serve | serve | serve |
| Stale but usable | serve labelled + background refresh (≤ 2× TTL) | serve only when closed (fixed price) | already does (`lastGood`, `DataFreshness.STALE`) | n/a |
| Hard-expired | refresh synchronously | refresh | refresh | — |
| Provider unavailable / 429 / 403 | serve last good, labelled STALE with retrieval time | last good with timestamp | `lastGood` | — |
| No cached value | error/unavailable as today | same | same | — |

Models: `CompanyFundamentals.retrievedAt`, quote `timestamp`, earnings `DataFreshness` already exist; clients use
`ignoreUnknownKeys = true` (`network/StockStepsApi.kt`), so an additive optional `freshness`/`cacheAgeSeconds` field is
backward-compatible; UI would need a label where shown. Not implemented here.

## 10. Budgets, rate limits and client identity

Implemented: per-instance `RequestRateLimiter` on screener/compare/earnings/research/AI/user routes (keyed on `remoteHost` or uid);
`ProviderRequestBudget` (comparison history/research), screener warm-up budget, narration and article-insight hourly budgets, durable
Comparison AI quota, provider cooldowns. Missing: per-provider/global budgets, cross-instance budgets, limits on public provider routes,
durable quotas for other AI features, retry-storm protection beyond cooldowns (largely handled by shared failures).

Client identity options (do not trust `X-Forwarded-For` blindly):
| Option | Spoofing | Privacy | Shared-IP fairness | Cost | Notes |
|---|---|---|---|---|---|
| Trusted proxy header (rightmost Google-appended XFF entry; configured hop count) | resists spoofing only if the hop count matches the real topology (direct `run.app` vs external LB) | IP processing (already logged by GFE) | NAT users share a bucket | none | needs deployment verification |
| Firebase App Check | strong vs scripts | none | per device | free tier; client SDK work on both apps | best for public app traffic |
| Require sign-in for AI explanation routes | strong | uid-based | fair | low | product decision (D2) |
| Server-issued anonymous session token | medium (can be re-minted) | low | per device | low | complements IP limits |
| Layered: global per-route budget + trusted-IP limit + App Check | strong | low | acceptable | low | **recommended target** |

## 11. Cross-instance behaviour

All caches, single flight, warm-up budgets and most AI quotas multiply with instance count (verified design; instance counts unknown — no
Cloud Run config in the repo). Options: process-local only (today); Cloud Run tuning (min-instances, higher concurrency, max-instances
cap) — cheapest; Firestore L2 for statements/profiles/earnings histories (per-read/write cost, lease for single flight); Memorystore
(fixed monthly cost + VPC connector); Cloud Storage for immutable historical statements (cheap, eventual). **All shared/persistent
storage of provider data requires licensing confirmation (D3 in `FINANCIAL_API_PHASE3_DECISIONS.md`).** Recommendation unchanged: tune Cloud Run first, measure
`cache.*` and `ProviderCalls` metrics, add an L2 only if the measured instance multiplier justifies it.

## 12. Correctness and licensing guardrails

Recommendations keep: exact periods and annual/quarter/TTM separation (distinct keys and mapper filters); currency on every row;
exchange-qualified symbols; provider timestamps; split-adjusted daily closes only; missing-data semantics (no reconstruction from
ratios or share counts); premium checks before premium provider work; MOCK/REAL separation (one source set per process). Licensing:
in-process reuse across users is today's behaviour; persistent storage, cross-instance sharing, precomputed screener snapshots and
bulk endpoints need FMP/Finnhub terms confirmed (no contract text in the repository).
