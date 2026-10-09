# Financial API audit — executive summary (Phase 1, 2026-10-09)

Read-only audit of commit `e27991d`. Details: `FINANCIAL_API_ARCHITECTURE_AUDIT.md` (inventory, call graph, caches, security),
`FINANCIAL_API_COST_ANALYSIS.md` (scenarios, cost model), `FINANCIAL_API_OPTIMIZATION_ROADMAP.md` (target architecture, backlog).

## Verdict
- **Architecture health: good foundation, single-instance scope.** All providers are behind the Ktor server; one shared FMP repository
  and fundamentals cache serve every feature, with single-flight, failure cooldowns and per-dataset TTLs. Weak spots: every cache is
  per process, a few duplicate fetch paths, failures not single-flighted in some paths, and incomplete metering.
- **Cost exposure: moderate, unbounded on public routes.** Normal browsing reuses data well on one instance; the open exposure is public
  routes (including two Gemini paths) with no auth or rate limit, per-instance AI quotas, and the screener warm-up.
- **Production readiness (cost/quotas): not ready** until public routes are guarded, AI quotas are durable for all AI features, and
  provider usage is measured.
- **Confidence: medium.** Call graphs are verified in code; request counts are static estimates; provider plans, prices, licensing,
  Cloud Run instance counts and scheduler cadences are unknown.

## Top ten findings (by severity × likely impact)
1. **Public provider- and AI-backed routes have no auth or rate limiting** — `/stocks/{s}/news/{id}/insight` (Gemini), `/stocks/{s}/movement`
   (Gemini narration, no budget), details, fundamentals, charts, search, watch-data (≤ 100 symbols), markets. *(verified)*
2. **AI quotas are per instance for Brief, Earnings P5 and article insights** (Comparison P5 is the only durable ledger); Brief/Learning
   usage maps are never pruned. *(verified)*
3. **Every cache is per Cloud Run instance** — upstream load scales with instance count for the same symbols. *(verified design; instance count unknown)*
4. **One fundamentals request = 11 FMP datasets (14 for quarterly)**, even for callers that use only income statements (comparison
   history 1Y) or a subset (screener). *(verified)*
5. **Screener warm-up can spend ≈ 300+ FMP calls/hour per instance** (25 symbols/h × 11–13 datasets) whenever the screener is used. *(code-derived estimate)*
6. **Duplicate fetches of identical data**: Finnhub earnings history under two cache keys; 5-min bars in two caches; quarterly income
   statements as ×8 and ×24; three BoC FX caches. *(verified)*
7. **Failures aren't single-flighted in some paths** (`CompanyFinancialCache` stores nothing on exceptions → waiters retry in turn) and
   `EarningsService.retrying` retries rate-limited calls. *(verified)*
8. **Market status is a provider call** (`exchange-market-hours`, every 60 s per instance on Details) although `UsMarketCalendar` can
   compute it. *(verified)*
9. **TTM ratios refresh every 5 minutes, including when markets are closed**; quotes/daily closes aren't session-aware except in
   Markets/Watch presentation. *(verified)*
10. **Metering covers only part of the traffic** (FMP fundamentals, comparison history, comparison AI); quotes, profiles, search, charts,
    Finnhub, BoC and most Gemini calls are unmetered and tokens are tracked only for Comparison AI. *(verified)*

## Most expensive or wasteful paths
- First open of an unseen company: ≈ 16 FMP calls (11-dataset bundle dominates); Comparison of 4 new companies ≈ 52 FMP + 4 Finnhub, +12 for 1Y history.
- Screener warm-up (background, hourly).
- Watchlist of 20 symbols cold ≈ 60 calls (quote + profile + Finnhub history each); quotes repeat every 60 s while viewed.
- Earnings calendar names/logos: one FMP profile per listed symbol on first view.
- Legacy `/market/snapshot` (if still called by old app versions): FMP QQQ/DIA quotes reportedly return 402 then fall back to Finnhub on every 45-s rebuild.

## Existing strengths
Backend-only provider access; keys never in clients or logs; exchange-qualified, collision-free dataset keys; single-flight on cache
misses; 402/403 and 429 cooldowns; daily closes shared across charts/valuation/comparison/portfolio; Markets/Brief reuse one overview;
news simplification deduped across instances via Firestore; AI entitlement checks before calls (fail closed); validated AI outputs;
version-keyed AI caches; Comparison AI durable quota with idempotency and token metering; strict MOCK/REAL separation.

## Recommended next implementation task (Phase 2a)
1. Extend `ProviderUsageMeter` to all provider calls via `apiCall` and all Gemini calls via `GeminiJsonCall` (no symbols/uids in labels).
2. Add `RequestRateLimiter` to all public provider-backed routes and an hourly budget to the movement narrator.
3. Then Phase 2b: remove the duplicate fetch paths (finding 6), failure-safe single-flight (finding 7), local market status (finding 8), search cache.
No new infrastructure, no licensing question, small diffs, measurable with the new metrics.

## Decisions requiring project-owner approval
- **D1** Licensing: may FMP/Finnhub data be cached persistently and reused across users (required before any Firestore/Redis L2)?
- **D2** Cloud Run settings (min/max instances, concurrency) and expected traffic — determines whether a shared cache is worth it.
- **D3** Should article insights and movement narration stay public, or require sign-in (and/or App Check)?
- **D4** AI fair-use policy: one shared StockSteps+ AI allowance across features, or separate per-feature allowances (all durable)?
- **D5** Screener coverage vs cost: warm-up budget and universe size.
- **D6** Retire legacy routes (`/market/snapshot`, `/api/v1/market/gainers|losers`) once no shipped app version uses them.
- **D7** Provider plans: confirm FMP/Finnhub tiers, limits and batch endpoints; provide prices for cost dashboards.
