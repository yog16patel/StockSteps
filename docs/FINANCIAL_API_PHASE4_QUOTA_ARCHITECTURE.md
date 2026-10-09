# Financial API — Phase 4 quota architecture (read-only)

Date: 2026-10-09, `main` at `6a70d0e`. Labels as in `FINANCIAL_API_PHASE4_SECURITY_AUDIT.md`. Nothing here is implemented.

## 1. Existing AI features and quotas (CONFIRMED by source)

| Feature | Gemini calls per request | Auth | Premium | Current quota | Storage | Reset | Bypass risk |
|---|---|---|---|---|---|---|---|
| Comparison AI summary/ask (`screener/ComparisonAiService.kt`) | 1, +1 regeneration on validation failure | user | Plus | 10/day + 50/rolling 30 days per uid; global 2,000/day | **Firestore** `users/{uid}/meta/aiUsage` (transaction) for per-user; global **in memory** | per-user: UTC day + rolling window; global: UTC day per instance | global multiplies by instances |
| Daily Brief AI explain/ask (`brief/DailyBriefService.kt:360–390`) | 1 (12 s timeout) | user | Plus | 15/day per uid | **memory** `ConcurrentHashMap` | UTC day; lost on restart | N instances → N × 15; restart resets |
| Earnings premium explain/ask/digest (`earnings/EarningsPremiumService.kt`, `earnings/EarningsAi.kt:28`) | 1 per category call | user | Plus | 10 explanations, 20 questions, 3 digests per day; global 5,000/day | **memory** ledger | UTC day; restart resets | as above |
| Earnings Q&A (`earnings/EarningsService.kt:515`) | none in REAL (`research = null` → 503) | user | Plus | shares the earnings ledger when set | memory | — | not live |
| Guided Research ask (`learning/LearningService.kt:79`) | none in REAL (`research = null` → 503) | user | Plus | 20/day | memory | — | not live |
| Article insight (`news/ArticleInsights.kt`) | 1 per article+content+version | **none** | no | 200 starts/h, 2 concurrent | memory (+ result cache 7 days per instance) | hourly window | per instance; anonymous |
| Movement narration (`service/MovementService.kt:181`) | 1 per symbol/period/version | **none** | no | 300 starts/h | memory | rolling hour | per instance; anonymous |
| News simplification (`news/NewsSimplificationService.kt`) | background, one worker, ≤ 5 queued per response, queue 20 | **none** (triggered by public news) | no | throughput bound (1 worker × 10 s timeout ≈ ≤ 360/h/instance) | Firestore claim + result store (shared across instances) | 15 min failure cooldown | per instance worker |

Per-call output caps exist (`maxOutputTokens`: insight 600, movement 300, brief 700, news 512, earnings per call, comparison per call —
`news/GeminiInsights.kt:29`, `brief/BriefAi.kt:76`, `news/GeminiNewsSimplifier.kt:57`). Input size is bounded by the context builders (validated
questions 3–300/500 chars, bounded evidence), not by an explicit token limit. Token usage is metered (`GeminiJsonCall.generateWithUsage`), but no
quota counts tokens. In-flight concurrency per user is not limited except by the per-minute route limiters.

## 2. The reference implementation: `ComparisonAiQuota` (`screener/ComparisonAiService.kt:265–345`)

- One document per user (`AiUsageDocument(charges)`), each charge `{id, feature, at, key}`; already **feature-tagged** and filters by `feature`.
- `reserve` runs a Firestore transaction (`userdata/FirestoreUserDataStore.kt:170`): drops charges older than the window, returns the existing
  charge for a repeated idempotency key (retry is free), denies at the daily or window limit, else appends a charge — **before** the Gemini call.
- `release` removes the charge when the request fails (validation fallback, provider error, cache hit not charged).
- Crash/timeout between reserve and release: the charge stays (user loses one request; never over-allows). Conservative and acceptable.
- Concurrency: two simultaneous requests from one user are serialised by the transaction (Firestore retries on contention); per-user write rate is
  tiny, so contention is negligible. Across instances the document is the single source of truth.
- Clock authority: the instance clock (`Clock`) at reservation; UTC calendar day for daily limits; rolling window by `at`. Instance clocks on
  Cloud Run are NTP-synchronised (UNKNOWN precision; seconds are irrelevant here).
- Gaps: the global daily budget is in memory (`:278`); `usage()` runs a transaction to read (no write when unchanged); charges array grows with use
  (bounded by the window: 50 × ~80 bytes, far below Firestore's 1 MiB document limit).

**Verdict**: suitable for reuse by every signed-in AI feature with a per-feature limit table. It is generic already (constructor `feature`,
`dailyLimit`, `windowLimit`).

## 3. Durable quota options

| | A. Extend `aiUsage` (one doc per user, feature-tagged charges) | B. One doc per user per feature | C. Reservation ledger collection (`aiReservations/{id}` with state + TTL) | D. Redis/Memorystore counters |
|---|---|---|---|---|
| Reads/writes per AI request | 1 transaction (1 read + 1 write); +1 on failure | same | 1 create + 1 update (commit) + reads for counting (or a counter doc) | 1–2 commands |
| Contention | per user only | lower (per feature) | per user counter doc | none practical |
| Atomicity | transaction | transaction | needs counter doc + transaction anyway | atomic INCR |
| Combined allowance across features | **easy** (same doc) | needs multi-doc transaction | possible | possible |
| Complexity | **lowest** (code exists) | low | medium–high | medium + VPC connector |
| Failure recovery | release on failure; crash keeps charge | same | explicit expiry of pending reservations | TTL keys |
| Cross-instance | yes | yes | yes | yes |
| Fixed cost | none | none | none | **monthly instance + connector** |

**Recommendation: A.** Reuse `ComparisonAiQuota` per feature (`brief-ai`, `earnings-ai-explanation`, `earnings-ai-question`, `earnings-ai-digest`,
later `learning-ai`) with an optional combined StockSteps+ cap computed from the same document (decision D6). Firestore schema stays
`users/{uid}/meta/aiUsage` → `{data: AiUsageDocument}`; no new collection. Keep `Reservation`/`release` semantics; add a `released`-on-cancellation
path (already `release` is called from `catch (Throwable)` in callers — verify each new caller does so).

## 4. Global AI budgets and anonymous AI

| Option | Correctness | Cost | Notes |
|---|---|---|---|
| In-memory per instance (today) | multiplies by instances | none | acceptable only with a hard max-instances bound (D4) |
| Firestore sharded daily counter (`system/aiBudget-{day}-{shard}`, K shards) | global; increments are transactions on a random shard; reads sum K docs | 1 write per AI call + K reads per check (or a cached sum refreshed every few seconds) | soft-ish (stale sum by a few seconds) |
| Gemini project quota (Google Cloud quota on the Generative Language API, if adjustable for the key's project) | **hard provider-side ceiling** | none | verify availability for the project (UNKNOWN); the app must then handle 429 gracefully (it does: fallbacks) |

Recommendation: **per-instance budgets sized as (global target ÷ max instances)** plus the provider-side quota as the hard backstop; a Firestore
counter only if D4 can't bound instances. For anonymous AI (insight, movement, news): App Check + per-IP limits, and move article insights to the
shared Firestore result store used by news simplification so instances don't regenerate the same article.

## 5. Global provider request budgets

| | A. Firestore transaction counters | B. Max instances + concurrency + local budgets | C. Redis/Memorystore limiter | D. Hybrid (recommended) |
|---|---|---|---|---|
| Cross-instance correctness | exact (per window) | exact by construction if every instance enforces `limit / maxInstances` | exact | B for hard limits, A-style counters only for daily soft totals |
| Burst control | poor (latency per request) | good (local token bucket) | good | good |
| Contention | **high**: one hot document per provider — Firestore sustained writes to one document are limited (on the order of one per second; sharding needed) | none | none | none on the request path |
| Latency added | a transaction per provider call (tens of ms) | none | ~1 ms in-region | none |
| Cost | 1 write + reads per provider call → doubles cost of every request | none | fixed monthly | none + small periodic writes |
| Failure mode | Firestore down → fail open or closed for all data | none | Redis down → fail open/closed | local only |
| Ops | low | low | VPC connector, sizing | low |
| Low budget fit | poor | **best** | poor | **best** |

Definitions kept distinct:
- **Hard ceiling**: requests beyond it are refused locally (B's per-instance bucket; provider plan limits).
- **Rate limit**: per-minute shaping (token bucket per provider, plus outbound concurrency semaphore).
- **Admission**: per-route/per-identity limits before work starts (security audit §6).
- **Soft cost target**: daily totals compared with a target; alerts, not refusals.
- **Monitoring alert**: notification only (billing budgets are not caps).

Enforcement point: `httpclient/NetworkUtils.kt` `apiCall` — the single choke point every FMP/Finnhub request already passes (BoC and Gemini call
`ProviderCalls.record` themselves and need the same guard). A budget enforced there cannot be bypassed by another route. Priority classes: interactive
requests first; screener warm-up and earnings-accelerated refreshes get a reserved fraction and yield when the bucket runs low.

## 6. Budget exhaustion behaviour (fail-open vs fail-closed)

| Feature | On exhausted provider budget | On quota store unavailable |
|---|---|---|
| Screener warm-up | pause; coverage shown as "X of 150" (already) | n/a |
| Company Details / Financials | cached or labelled stale data (Phase 3E); else `PROVIDER_RATE_LIMITED` 503 | n/a |
| News | cached feed; no new simplification | n/a |
| Gemini features | 503 `AI_BUSY`/429 quota with the non-AI content | **fail closed** for premium AI (no reservation → no call) |
| Practice Portfolio | **refuse the order**; never fill on a stale or fabricated price | n/a |
| Premium research | entitlement still enforced; history marks unavailable companies | entitlement read failure → 503 (already fail closed) |
| Alerts/reminders | skip the run, retry next schedule | n/a |

## 7. Failure recovery

- Quota reservation before the provider call; release on any failure (including validation fallback); keep the charge on crash.
- Idempotency keys (already for Comparison AI) for client retries; add to Brief/Earnings AI requests.
- Provider budget buckets reset per window; no persistent state to repair.
- After a restart, in-memory soft counters restart at zero — acceptable only because hard limits are per instance by construction.
