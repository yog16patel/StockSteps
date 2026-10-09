# Financial API — Phase 4 implementation plan (proposed; nothing implemented)

Date: 2026-10-09, `main` at `6a70d0e`. Order reflects risk: secret exposure first, then unbounded anonymous cost, then durable quotas and budgets.
Phase 3 correctness (expiry re-warm, selective loading, market-/earnings-aware freshness, labelled stale data) must be preserved by every milestone.

| Milestone | Priority | Description |
|---|---|---|
| 4A-0 | **P0, immediate** | Logging hotfix + FMP key rotation |
| 4A | P0 | Public route security and trustworthy client identity |
| 4B | P0 | Durable AI quotas |
| 4C | P0/P1 | Global provider budgets |
| 4D | P1 | Cost monitoring and alerts |
| 4E | Conditional | Shared caching (only if licensed and justified) |
| 4F | P1 | Security, concurrency and failure-mode verification |

### 4A-0 Logging hotfix (no decision needed)
- **Files**: `server/src/main/resources/logback.xml` (root INFO; `io.ktor.client` WARN; keep `StockSteps.*` INFO).
- **Ops**: if any deployment used the TRACE config, rotate `FMP_API_KEY` at FMP and in the secret store; review Cloud Logging access/retention.
- **Tests**: a test that loads the production logback config and asserts the effective level of `io.ktor.client` ≥ WARN; grep test output for
  `apikey=` (must be 0).
- **Rollback**: none needed. **Complexity**: trivial.

### 4A Public route security
- **Design**: (1) `ClientIdentity` helper: uid (verified) else trusted client IP from `X-Forwarded-For` using `TRUSTED_PROXY_HOPS`; (2) one
  route-group interceptor applying weighted per-identity limits to every provider-backed public route (reuse `RequestRateLimiter`, add weights
  and `Retry-After`); (3) existence gate: before a fundamentals/details bundle for a symbol not in the screener universe or recent search
  results, require a cached profile (1 request, negative-cached 24 h); (4) watch-data: anonymous ≤ 30 symbols, bounded concurrency (e.g. 8);
  (5) internal routes: Cloud Scheduler OIDC verification (audience + service-account email) or one secret per job with constant-time compare;
  (6) request-size guard on POST/PUT; (7) App Check verification in monitor mode, enforcement after app releases.
- **Files**: `Application.kt` (route groups), `screener/ScreenerService.kt` (`RequestRateLimiter`), new `security/ClientIdentity.kt`,
  `userdata/UserRoutes.kt` (watch-data, alerts internal), `earnings/EarningsReminderService.kt`, `brief/DailyBriefRoutes.kt`,
  `service/ProviderUsage.kt`, `CompanyFinancialRoutes.kt`; apps: App Check SDK (Android `app/androidApp`, iOS `app/iosApp`), shared network client
  header (`core/.../network/StockStepsApi.kt`, `data/userdata/UserApi.kt`).
- **Dependencies**: D1, D2, D4 (hop count from the topology). **Risks**: wrong hop count (spoofable or shared buckets); throttling legitimate users
  behind NAT. **Migration**: App Check monitor mode first. **Rollback**: env flags to disable each layer.
- **Tests**: header-spoofing (forged leading XFF ignored), per-IP and per-uid buckets, weights, existence gate (random symbols cost ≤ 1
  request), watch-data cap, OIDC/secret rejection, constant-time compare, body-size rejection. **Acceptance**: no public provider route without a
  limit; 1,000 random symbols ≤ 1,000 requests; spoofed headers don't change identity. **Complexity**: medium. **Approvals**: D1, D2.

### 4B Durable AI quotas
- **Design**: reuse `ComparisonAiQuota` (feature-tagged charges in `users/{uid}/meta/aiUsage`) for Daily Brief AI and Earnings premium AI
  (explanation/question/digest), later Learning/Earnings Q&A when they get REAL providers; optional combined StockSteps+ cap; idempotency keys on
  Brief/Earnings AI requests; release on any failure. Global AI budget: per-instance `target ÷ maxInstances` plus Gemini project quota.
  Anonymous AI: App Check + per-IP limits; article insights stored in the shared Firestore result store.
- **Files**: `screener/ComparisonAiService.kt` (extract `ComparisonAiQuota` to a shared `AiQuota`), `brief/DailyBriefService.kt`,
  `earnings/EarningsAi.kt`, `earnings/EarningsPremiumService.kt`, `earnings/EarningsDigest.kt`, `news/ArticleInsights.kt`, `Application.kt`;
  core models if usage responses change (additive).
- **Dependencies**: D6 for numbers (defaults usable). **Risks**: transaction latency (+1 round trip per AI call); contention negligible per user.
  **Migration**: none (new charges in the existing doc). **Rollback**: env flag back to in-memory ledgers.
- **Tests**: in-memory store + Firestore emulator: same user on two service instances shares the limit; concurrent reservations; failure after
  reservation releases; cancellation; idempotent retry; UTC day and 30-day boundaries; exhaustion messages. **Acceptance**: restarting or adding
  instances never increases a user's allowance. **Complexity**: low–medium.

### 4C Global provider budgets
- **Design**: in `apiCall` (and the Gemini/BoC paths): per-provider token bucket (per minute) + concurrency semaphore + daily soft counter, sized
  `plan limit × safety factor ÷ maxInstances`; priority classes (interactive > background warm-up/accelerated refresh); circuit breaker on
  sustained 429/5xx; exhaustion → the D8 fallbacks; counters exported for 4D. Firestore counters only for daily soft totals if needed.
- **Files**: `httpclient/NetworkUtils.kt`, `service/ProviderUsage.kt` (`ProviderRequestBudget` → provider-level), `screener/ScreenerService.kt`
  (yield when background budget is low), `news/GeminiInsights.kt`, `userdata/BankOfCanadaPortfolioFx` path, `Application.kt` (env sizing).
- **Dependencies**: D4, D5. **Risks**: limits too low degrade UX; must not starve interactive traffic. **Rollback**: env flag (unlimited).
- **Tests**: MockEngine: near-limit admission, priority ordering, 429 bursts trigger the breaker, budget exhaustion fallbacks per feature,
  multiple routes share one provider budget, simulated multi-instance sizing. **Acceptance**: provider requests per instance never exceed the
  configured ceiling; interactive requests served before warm-up. **Complexity**: medium.

### 4D Cost monitoring and alerts
- **Design**: per-minute structured usage log (meter deltas, no user data), logs-based metrics, dashboard and alerts (`FINANCIAL_API_PHASE4_OBSERVABILITY.md`),
  billing budgets; optional `AiPricing` env for estimates.
- **Files**: `service/ProviderUsage.kt`, `logback.xml` (JSON encoder optional), `Application.kt`; GCP console/IaC (owner).
- **Dependencies**: D5, D7 for thresholds. **Tests**: log line schema test; no sensitive fields. **Complexity**: low.

### 4E Shared caching (conditional)
- Only after D3 (licensing) and metrics show ≥ 2 warm instances most of the day or plan limits within ~2× of usage. Candidates: statements/profiles in
  Firestore with TTLs matching Phase 3 policies; not quotes or TTM ratios. **Complexity**: medium–high.

### 4F Verification
- **Deterministic (MockEngine, fake clocks, in-memory stores, Firestore emulator)**: everything in `FINANCIAL_API_PHASE4_SECURITY_AUDIT.md` §5 and
  quota/budget tests above; financial-correctness regressions (missing stays missing, stale labelled, timestamps preserved, no fabricated REAL data,
  Practice fills need fresh prices, server-side StockSteps+, MOCK/REAL isolation) — the existing suites cover most.
- **Authorised integration only**: Cloud Run hop count, App Check tokens from real builds, OIDC from Cloud Scheduler, log level in Cloud Logging,
  Gemini project quota behaviour, alert delivery.

## Smallest safe launch set

4A-0 → D4 (max instances) → 4A (identity + limits + existence gate + watch-data cap + scheduler auth) → 4C (per-instance provider ceilings) →
4B (durable Brief/Earnings AI quotas, or keep those AI features off until done) → 4D (usage log + billing alerts). App Check and 4E follow.
