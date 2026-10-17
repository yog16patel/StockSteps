# Cache and quota policy — Phase 9B

## 1. Audit of existing caches and single flight (verified in code)
All provider caches are per-process `CompanyFinancialCache` instances: bounded LRU, per-key single flight, failures shared with joined callers
and never stored as data, cooldowns only as cached *outcomes* via `providerCooldown` (402/403 1 h, 429 10 min, local daily-limit denial 30 s,
other 30 s). Phase 6 `CACHE_INVENTORY.md` lists every instance; changes since:

| Cache | Key | TTL | Since |
|---|---|---|---|
| quote outcome | `quote:SYM` | 30 s live, ≤ 15 min closed; refusals cooled down | 7A |
| daily closes (shared) | `daily:SYM` (`PriceChartService`) | until next expected close; 30-min late retry | 7B |
| Portfolio/Practice closes | reuse the shared close cache | — | 7C |
| Discover enrichment | in-memory map per company + 60/h + **100 upstream/day** | profile 24 h (spare budget), close per session | 7E, **9B** |
| SEC tickers / companyfacts / submissions | `tickers`, `facts:CIK`, `submissions:CIK` (`cache.sec.*`) | 24 h / 24 h / 6 h; 404 cached; 403 1 h; 429 10 min | **9B** |

Keys include provider (cache instance per adapter), dataset/endpoint, exchange-qualified symbol, period/frequency and row limit where they
apply (existing rule); the price boundary adds currency and basis to the *value* (`PriceSeries`), so a cache of `PriceSeries` keyed by
`provider:dataset:symbol:basis` can't mix bases. No public cache key contains a uid; user data lives only in Firestore documents per uid (9A).

## 2. Dataset policies (categories, not permission — each cache must also respect its source licence)

| Dataset | Policy | Licence condition |
|---|---|---|
| Reported fundamentals (SEC) | 24 h; earnings-aware invalidation possible from `submissions` (new 10-Q/10-K accession) | public: caching allowed |
| Fundamentals (FMP) | 24 h earnings-aware; stale ≤ 7 days labelled (3E) | shared caching **PENDING** (Q4) |
| Reference (tickers, profiles) | 24 h; search 6 h | SEC allowed; FMP pending |
| Daily prices | trading-calendar-aware (until next expected close) | pending |
| Quotes | short (30 s) only while a session is active; closed → until next session ≤ 15 min | pending; delayed-display fees (Q7) |
| Macro (if added) | until the next release (agency release calendars) | Treasury allowed; BLS/BEA pending; FRED per series |
| FX (BoC) | 6 h per window, dated observations (no future FX) | allowed with attribution |
| News | 5–10 min | licence-dependent (Finnhub pending) |

Stale-while-revalidate: only the existing labelled statement fallback (3E). The original as-of is kept (`retrievedAt`, `Provenance.asOf`).

## 3. Budgets and metrics
- Per provider (per instance): `ProviderGuard` token bucket + burst + concurrency + circuit breaker + optional daily limit; FMP, Finnhub,
  Gemini, BoC as before; **`sec` new** (300/min, burst 10, 2 concurrent; published ceiling enforced). No thundering-herd retries: 429 opens
  the provider breaker (Retry-After or 30 s doubling ≤ 5 min), one half-open trial.
- Metrics (existing usage summary every 60 s): per provider/endpoint/feature `upstream`, `ok`, `rateLimited`, `denied` (402/403), `error`,
  `timeout`, `invalid`, latency, cache hit/miss/join/expired/evict, budget denials, `dailyLimitReached`; **9B** adds `sec` and
  `discover.enrich.dailyBudgetReached`. Estimated quota = `ProviderGuard.dailyUsage(provider)` (admitted attempts today vs local allowance).
- **Per-instance only.** With N instances totals are bounded by N × local limits = the plan value only when `CLOUD_RUN_MAX_INSTANCES`
  matches the deployment; the Discover daily cap is per instance too. Multi-instance safety needs a shared counter (Firestore transaction
  counter or Memorystore) — **not added; needs approval** (D-9B-10).
- 9A request limits are unchanged (per-class buckets, Retry-After, concurrency caps); cache hits cost no provider budget.

## 4. Before / after (MockEngine benchmarks; `server/build/phase9b-market-data-benchmark.txt`)
| Scenario | Before | After |
|---|---:|---:|
| Discover enrichment, highest UTC day (two weeks, provider on time) | 465 | **100** |
| Discover enrichment, two weeks total | 3,875 | **1,400** |
| Latest-close coverage on weekdays at 20:45 UTC (of 155) | 155 | 100–155 (closes first; profiles refresh on spare budget, mostly weekends) |
| 300 concurrent Discover opens, cold instance | 310 | **100** |
| SEC: 1,000 concurrent readers × 20 companies, cold / warm | — | 21 / 0 |
| SEC 403 / 429 repeated 5× | — | 1 upstream each (cooldown) |
| Existing benchmarks (7A quotes, 7B Markets, 7C Portfolio/Practice, Phase 3, 9A storms) | unchanged | unchanged (re-run in `:server:test`) |
