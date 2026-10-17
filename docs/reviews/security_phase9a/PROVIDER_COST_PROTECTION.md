# Provider cost protection — Phase 9A

No provider call, cache, budget, market-data calculation or MOCK/REAL behaviour changed in 9A. This phase adds per-caller limits in front of
the existing controls and logs when the providers' limits bite.

## 1. Existing controls (verified present, unchanged)
| Control | Where | Behaviour |
|---|---|---|
| Single upstream path | `httpclient/NetworkUtils.apiCall`, `ProviderCalls.record` | the only place that counts upstream requests |
| Provider budgets | `service/ProviderBudget.kt` `ProviderGuard` (REAL always installed) | per provider: token bucket per minute + burst, concurrency, circuit breaker, daily allowance (`PROVIDER_<NAME>_DAILY_LIMIT`), divided by `CLOUD_RUN_MAX_INSTANCES` with a safety margin. Development defaults (unverified plan values): FMP 600/min, Finnhub 60/min burst 30, Gemini 60/min burst 20, BoC 30/min. Startup warns "not production-ready" until the real plan limits are set. |
| Cooldowns | `providerCooldown` / `resultTtl` | 402/403/429 are never retried; outcomes cached briefly so a burst can't re-hit a refusing provider |
| Caches with single flight | `CompanyFinancialCache` (profiles 24 h, search, quotes by market session, statements, ratios, closes until next expected close) | concurrent identical requests share one load; failures shared, never stored |
| Unknown-symbol gate | `SymbolExistence` | random symbols can't trigger 13–17 request bundles |
| Admission units | `Admission.kt` | upstream calls cost extra units per caller (cache hits cost 1), Gemini 20 |
| Discover enrichment budget | `CuratedScreenerUniverse` `SCREENER_ENRICHMENT_PER_HOUR` (default 60) | background only; no per-row requests from users |
| AI | `EntitlementService` before any provider call; durable per-user and global daily quotas; MOCK never calls AI | |

## 2. What 9A adds
- Per-caller **class limits and concurrency** (`RATE_LIMIT_REVIEW.md`): a single uid or address can't push more than 90–180 market-data
  requests/min or 2 concurrent AI calls into an instance, so one user can't drain the shared provider budget; global provider budgets stay
  separate and unchanged.
- **Cache before quota:** limits count requests, but cache hits never reach `ProviderGuard`; measured 40 concurrent identical searches → 1
  provider call; 35 repeats → still 1 (`RateLimitAbuseTest.repeatedIdenticalSearches…`). Rejected requests cost nothing upstream.
- **Observability:** `security event=upstream_restricted reason=provider_daily_limit_reached|provider_rate_limited|provider_restricted` with the
  route template only (no symbol, key, URL or quota number); counted as `security.upstream_restricted.*` in the per-minute usage summary.
- Exhaustion answers stay the Phase 7A codes (503 `PROVIDER_DAILY_LIMIT_REACHED` / `PROVIDER_RATE_LIMITED`, 502 `PROVIDER_RESTRICTED`); bodies
  were checked for keys, provider hosts and numbers (`providerQuotaExhaustionIsA503WithoutQuotaDetailsAndIsLogged`). Never a MOCK fallback.

## 3. Evidence and limits of the evidence
- All provider tests use MockEngine/fakes; **no live quota claims** are made from MOCK runs (repo rule). Existing benchmarks re-run in
  `:server:test` (ProviderBudgetTest, QuoteQuotaProtectionTest, ProviderRequestBenchmarkTest, Phase3AuditBenchmarkTest, CuratedDiscoverBenchmarkTest,
  MarketsEodBenchmarkTest, PortfolioPracticeEodBenchmarkTest) — unchanged numbers, all PASS.
- Mobile → Ktor request counts per flow are in `RATE_LIMIT_REVIEW.md` §2 (unchanged by 9A: no client request was added or removed).
- Open (owner): the real FMP/Finnhub plan limits and `CLOUD_RUN_MAX_INSTANCES` must be set before production (Phase 4C/7A item, still open).
