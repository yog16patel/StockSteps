# Rate-limit review — Phase 9A (incl. the back-to-back request addendum)

## 1. Layers (outermost first; each keeps its own job)
1. **Phase 4A admission** (`security/Admission.kt`, unchanged): per route group, cost-weighted units per identity (1 per request + 1 per
   upstream call + 20 per Gemini call), anonymous upstream pool, in-flight caps, body/query size guards, App Check monitor.
2. **Phase 9A class limits** (`security/RateLimits.kt`, new): request counts per rate-limit class and identity, plus route buckets (`search`),
   per-identity concurrency caps. Classes come from `RouteAccess` (every route has one; unmatched paths fall back by prefix).
3. **Existing route limiters** (`RequestRateLimiter`: screener, earnings, brief, comparison, reminders, research; unchanged).
4. **Provider budgets** (Phase 4C/7A `ProviderGuard`, unchanged): global per-provider minute/burst/concurrency/daily limits and 402/403/429
   cooldowns, shared by all users — **separate from per-user limits**. One user can't monopolize them because layers 1–2 cap each caller first.

## 2. Measured app request patterns (emulator-5554, MOCK on :8091, debug API log, 2026-10-17)
| Flow | Requests (mobile → Ktor) | 429s |
|---|---|---|
| Cold start, signed in (Home) | 9: meta, brief latest, entitlements, learning, portfolio, watchlists, alerts, screens, personalized brief | 0 |
| First visit to Markets, Portfolio, Watchlist, Learn, Home | 5: markets overview, earnings calendar, earnings following, watchlists, alerts | 0 |
| **25 rapid tab switches** (5 cycles, 0.4 s apart) | **0** (landings load once) | 0 |
| Typing "microsoft" one letter per 0.5 s (slow typist; 300 ms debounce) | 9 searches | 0 |
| Open Company Details (MSFT) from search, then scroll the whole page | 7: details, chart, news, why-moving, earnings next, earnings latest, reminders | 0 |

Consequences for the suggested values: **search 10/min would block a single slowly typed word plus one correction** → search 45/min per guest
(≈ 5 slow searches). Guest market data 30/min would block the 5th Company Details open → 90/min (≈ 15 opens). Signed-in 120/min matches.
Expensive 5/min → 6 guest / 12 signed-in (AI explanations are tapped one at a time; Gemini calls also carry daily quotas).

## 3. Limits (defaults, per instance; env overrides `RATE_LIMIT_<NAME>_{GUEST,USER,POOL}_PER_MINUTE`, `_MAX_IN_FLIGHT`, validated at startup)
| Class / bucket | Guest (per trusted address) | Signed in (per uid) | Unverified pool (all guests, no trusted proxy) | In flight per identity | Routes |
|---|---:|---:|---:|---:|---|
| PUBLIC_LOW_COST | 120/min | 240/min | 12,000/min | 16 | meta, movers, snapshot, Markets overview, brief, Discover catalog |
| PUBLIC_MARKET_DATA | 90/min | 180/min | 9,000/min | 12 | quotes, profiles, details, charts, news, earnings, compare, Discover search |
| + `search` bucket | 45/min | 90/min | 4,500/min | 4 | `/api/v1/stocks/search` (counts in both) |
| AUTHENTICATED_STANDARD | 30/min* | 120/min | 3,000/min | 12 | signed-in reads, Practice preview |
| USER_WRITE | 10/min* | 40/min | 600/min | 4 | signed-in writes, Practice execute/reset |
| EXPENSIVE_OPERATION | 6/min | 12/min | 300/min | **2** | AI explain/ask (public and StockSteps+), PDF export |
| ADMIN_INTERNAL | 30/min | 30/min | 120/min | 4 | `/internal/*` |
| HEALTH_INTERNAL | — | — | — | — | probes, never limited |

\* guest numbers on signed-in classes only bound unauthenticated floods (those requests are 401 anyway, counted before the gate).

## 4. Identity
- Signed in: the verified uid (anonymous Firebase accounts excluded). Never body/query/headers, never a device id.
- Guest: the trusted client address only when `TRUSTED_PROXY_HOPS` is configured (entry N from the right of `X-Forwarded-For`; leading
  entries are client-controlled; IPv6 per /64) — repo rule `ClientIdentity` / `limiterKey()`, never `remoteHost` or the first XFF value.
- Otherwise the shared pool (see `GUEST_MODE_REVIEW.md` §3).

## 5. Concurrency, duplicates, idempotency
- **Concurrency:** excess requests above the in-flight cap are **rejected immediately** (429, `Retry-After: 1`), never queued; EXPENSIVE = 2.
- **Duplicate GETs:** identical concurrent public GETs share one upstream load in the single-flight caches (`CompanyFinancialCache`: search,
  profiles, quotes, statements, closes…; failures shared, never stored). Measured: 40 concurrent identical searches from 40 users → **1** provider
  call. These caches hold **public data only**, keyed by symbol/query — user-scoped data (Portfolio, Practice, Watchlists…) never enters a shared
  cache, so nothing is deduplicated across authorization scopes. No HTTP-response-level dedup was added (it would duplicate the cache layer).
- **Financial writes** are never deduplicated by GET-style logic. Practice keeps its idempotency key + atomic transaction: 8 concurrent
  executes with one key + a retry → **one** fill, every success returns the same transaction, a different order with the same key → 409.

## 6. Process-local state and production architecture
- All limits are **per instance** (memory). With N Cloud Run instances a caller can reach N × the limits (admission had the same property).
- State is bounded: windows expire after 60 s, idle entries are swept every 512 decisions, LRU eviction above 20,000 tracked keys
  (in-flight entries are never evicted); security-log throttling is bounded at 10,000 keys. Tested.
- Plan for multiple instances (no Redis or paid infrastructure added — owner approval needed):
  1. keep `CLOUD_RUN_MAX_INSTANCES` small and divide per-instance limits by it (as the provider budgets already do);
  2. add a gateway policy in front (Cloud Armor rate-based rules per client IP on the external HTTPS load balancer, or API Gateway quotas
     per Firebase uid) — enforced before any instance;
  3. if exact per-user limits across instances are required, a shared counter store (Memorystore/Redis or Firestore counters with sharding)
     behind the same `RateLimiter.enter/exit` interface. Cost and latency need owner approval.

## 7. Failure behaviour
429 `RATE_LIMITED` ("Too many requests. Wait a moment and try again.") + `Retry-After` (seconds until the oldest counted request leaves the
window, 1–60). No limits, pools, provider names, keys or quota numbers in the body. Provider exhaustion stays 503 `PROVIDER_RATE_LIMITED` /
`PROVIDER_DAILY_LIMIT_REACHED` or 502 `PROVIDER_RESTRICTED` (Phase 7A codes), never a MOCK fallback. Mobile: `StockStepsApiException.retryAfterSeconds`
exposes the header; neither client retries 429/503 automatically (one 401 retry with a fresh token only). See `ANDROID_IOS_INTEGRATION.md`.
