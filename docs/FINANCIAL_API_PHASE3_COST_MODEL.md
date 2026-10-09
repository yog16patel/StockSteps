# Financial API — Phase 3 cost model

Companion to `FINANCIAL_API_PHASE3_AUDIT.md`. **Measured** numbers come from `Phase3AuditBenchmarkTest` (REAL FMP/Finnhub adapters on a
Ktor MockEngine, one instance, cold caches, no network; output in `server/build/phase3-audit-benchmark.txt`). **Modeled** numbers are
estimates from the code paths and stated assumptions; none are production measurements, and provider prices/plan limits remain unknown.

## 1. Current request counts (measured, this repository at `75760ab`)

| Scenario | Upstream requests | Notes |
|---|---|---|
| A Company Details: overview, financials annual + quarterly, valuation | **17** | 11 annual datasets + quarterly income (×24, shared with valuation) + quarterly balance + quarterly cash flow + quote + profile + daily closes |
| B Comparison of 4 + performance 1Y/3Y + 1Y history + repeat | **72** | 1Y history alone **12** (quarterly income/balance/cash × 4); repeat comparison 0 |
| C Premium research after a 4-company comparison: 3Y + 5Y history, detailed summary, AI question | **56** (comparison) + **0** | 3Y/5Y, summary and AI reused cached data (Template AI, no Gemini) |
| D Screener: catalog (3 × 30 universe), warm-up 25/h, two searches, five Company Details | **403** | catalog + warm-up **328** (3 universe + 25 × 13); searches **0**; five details **75** (15 each, unwarmed symbols) |
| E 50 users, 10 overlapping symbols, Company Details | **150** | 15 per symbol; single flight removed all duplicates |
| F Market closed (Saturday): fundamentals at t, t + 10 min, t + 20 min | **15** | first 11, then **4 more** (ratios-TTM and key-metrics-TTM every 5 min) |
| G Statement published 2 h after a first load, then corrected | **28** | new quarter visible only after **24 h**; correction not visible within 1 h |
| Screener re-warm check (15-symbol universe, budget 25/h) | evaluated **15 → 0** after 7 h | expired fundamentals never reloaded (bug) |

Phase 2 reference (unchanged, reproduced earlier with `ProviderRequestBenchmarkTest`): 26 → 20, 74 → 72, 15 → 14, 30 → 1.

## 2. Screener cost model

Variables: `U` universe size (default `3 exchanges × limit 100` = up to 300); `b` warm-up budget per instance per hour (25); `k` requests
per cold symbol (13 today); `k_r` per re-warm when statements are still cached (≈ 7: ratios-TTM, key-metrics-TTM, income-TTM,
cash-flow-TTM, estimates, quote, profile*; *profile 24 h); `T` record TTL (6 h); `N` instances that receive screener traffic.

| Situation | Requests per instance per day | Coverage |
|---|---|---|
| **Today (bug)** | first ≈ 12 h: up to `b × k` = **325/h**; then **0** (all expired, never re-warmed) | peaks ≤ `6 × b` = 150, then **0** |
| Bug fixed, no other change, `U = 300` | budget-bound: `24 × b` symbol loads = 600/day; ≈ 600 × k_r … k ≈ **4,200–7,800** | ≤ 150 at a time (budget can't keep 300 within 6 h) |
| Bug fixed, budget sized to cover `U` every `T` (`b ≥ U/T` = 50/h) | `U × (24/T) × k_r + U × k_stmt(daily)` ≈ 300 × 4 × 7 + 300 × 6 ≈ **10,200** | full |
| + screener dataset set (drop historical `ratios`; take price/currency from the universe row instead of quote+profile) `k: 13 → 10`, `k_r: 7 → 5` | ≈ 300 × 4 × 5 + 300 × 5 ≈ **7,500** | full |
| + `T` = 24 h when markets are closed, 6 h open (weekday) | weekday ≈ 7,500; weekend ≈ 300 × 10 = **3,000/day** | full |
| + universe limit 50 per exchange (`U` = 150) | ≈ half of each row | full, smaller |
| FMP bulk endpoints (if on the plan) | a few bulk requests per refresh | full — **plan/licensing unknown** |
| Precomputed snapshot (scheduled job + storage) | one refresh per `T` for all instances (`N` → 1) | full — **needs licensing confirmation (D3)**; adds scheduler + Firestore/Storage cost |

Multiply every row by `N` for per-instance caches (no cross-instance sharing). Important cache sizing note (inferred): the FMP dataset
cache holds 512 entries ≈ 46 symbols × 11 datasets; a warmed 300-symbol universe needs ≈ 3,300 entries, so LRU eviction would force
full (`k`, not `k_r`) re-loads. Raising that capacity (memory ≈ 2–20 KB per entry → ≈ 7–70 MB) is part of making the fix affordable.

## 3. Selective-loading savings (modeled from measured baselines)

| Path | Today (measured) | After Design A (modeled) | Saving |
|---|---|---|---|
| Comparison 1Y history, 4 cold companies | 12 | 4 (quarterly income only) | −8 (−67 %) |
| Comparison 3Y/5Y after a comparison | 0 | 0 | — |
| Comparison 3Y/5Y without a prior comparison (e.g. AI or research opened directly), 4 companies | 4 × 13 = 52 | 4 × 1–2 (annual income + profile) | ≈ −44 |
| Screener per cold symbol | 13 | 10 (screener set) | −23 % |
| Comparison P1 per cold company | 13 + Finnhub | 12–13 | small (keep full set; most datasets are displayed) |
| Company Details / Financials / Valuation | 17 (A) | 17 | none — all datasets are displayed |

## 4. Market-aware TTL sensitivity (modeled)

Saved requests ≈ `2 datasets × (closed 5-minute windows in which a symbol is requested)`. US regular session = 32.5 h of 168 h per
week, so ≈ 80 % of hours are "closed" for ratio refreshes. Examples (per instance):
| Assumption | Saved per weekend (48 h) |
|---|---|
| 200 symbols each requested in 10 distinct 5-minute windows | 2 × 10 × 200 = **4,000** |
| 50 popular symbols requested continuously (every window) | 2 × 576 × 50 = **57,600** |
| Weeknights (≈ 17 h/night × 5 = 85 h, scaled from the first assumption: ≈ 18 windows × 200 symbols × 2) | ≈ +7,000/week |
Quotes when closed: Markets already uses 15 min; extending watch/details quote TTLs to "until next open" saves 1 request per symbol per
30–60 s window while closed (same formula with 1 dataset and 30–60 s windows). Freshness risk is low because closed-market prices don't
change; the provider timestamp stays visible.

## 5. Earnings-aware statements (modeled)

Per reporting company per quarter: today 1 refresh/day (24 h TTL) → visible up to 24 h late. Proposed: 1–2 h TTL for that symbol only
until the new period appears (bounded, e.g. 10 days): ≈ 12–24 extra statement requests per reporting company per quarter (4 datasets ×
3–6 polls) in exchange for ≤ 2 h staleness. With ~150 reporting companies/quarter in a 300-symbol universe: ≈ 1,800–3,600 extra
requests per quarter per instance — small relative to warm-up.

## 6. Instance-count sensitivity

| Instances receiving traffic (`N`) | Statement-class load | Screener warm-up | Notes |
|---|---|---|---|
| 1 | 1× | 1× | best case for process-local caches |
| 2 | ≈ 2× cold loads for the same symbols | 2× | |
| 5 | ≈ 5× | 5× | an L2 or snapshot starts paying off |
Cloud Run settings are not in the repository (unknown). `min-instances = 1` with higher concurrency reduces `N` for low traffic at the cost
of an always-on instance.

## 7. Assumptions and unknowns

Assumed: universe default 3 × 100; 13 requests per cold screener symbol (measured); re-warm `k_r` ≈ 7 (code-derived); cache capacities
as coded; Saturday/holiday ratio sources unchanged while closed (inferred). Unknown: FMP/Finnhub plans, per-endpoint costs, bulk/batch
endpoint availability, licensing for persistent/shared storage, Cloud Run instance counts and concurrency, real traffic and symbol mix,
real cache hit rates (production metrics now exist via `/internal/metrics/usage` but haven't been collected).
