# StockSteps backend — Cloud Run cost drivers and scaling (Phase 5A)

No Google Cloud or provider prices are stated here: they are not in the repository and change over time. Costs are written as formulas with
named unit prices (`p_…`) to be filled from the current price lists and the owner's provider plans. Labels: **MEASURED** (this repo, local),
**MODELED** (formula + stated assumptions), **UNKNOWN**.

## 1. Cost drivers

| Driver | Formula | Inputs known now |
|---|---|---|
| Cloud Run CPU + memory (request-based billing, `cpu-throttling: true`) | `Σ billable instance-seconds × (vCPU × p_cpu + GiB × p_mem)`; an instance is billable while ≥ 1 request is in flight (plus startup) | 1 vCPU, 1 GiB (template); MEASURED app start 0.47 s after JVM launch on a Mac (container cold start incl. image pull and JVM: UNKNOWN, measure in 5B) |
| Cloud Run requests | `requests × p_req` | traffic UNKNOWN |
| Minimum instances | `min × 86,400 s/day × (vCPU × p_idle_cpu + GiB × p_idle_mem)` — billed whether or not requests arrive | 0 in the staging template |
| Always-allocated CPU (not used) | `instances × 86,400 × (vCPU × p_cpu_always + GiB × p_mem_always)` | off |
| Artifact Registry | `stored GB × p_storage` (+ egress outside the region) | MODELED image ≈ **0.2 GB compressed** (base JRE layers 99.8 MB for linux/amd64 + app distribution 103 MB gzipped); keep a cleanup policy (e.g. last 10 digests) |
| Cloud Logging | `ingested GB × p_log_ingest` (+ retention beyond default) | usage summaries: 1 line/min/instance ≈ 1,440 lines/day/instance; request logs: 1 entry per request (automatic); app logs mostly startup/warnings. Probes don't log in the app |
| Firestore | `reads × p_read + writes × p_write + deletes × p_delete + storage` | health probes: **0** operations. Per-feature estimates: `FINANCIAL_API_PHASE4_COST_MODEL.md` §5 |
| Secret Manager | `active secret versions × p_version + accesses × p_access` | 2–3 secrets (+4 optional tokens); accesses ≈ secrets × instance starts |
| FMP / Finnhub | subscription (fixed) + overage if the plan has it | plan limits UNKNOWN (D5); `ProviderGuard` caps requests per minute at `plan × 0.8 ÷ max instances` per instance |
| Gemini | `Σ calls × (input tokens × p_in + output tokens × p_out)` | output capped at 300–700 tokens per call; durable per-user quotas; per-instance global budgets (×instances) |
| Cloud Scheduler (5B) | `jobs × p_job` | 4 jobs (§6 of the checklist) |

## 2. The Phase 3 screener and instance multiplication

Facts from the code (Phase 3/4):

- Every instance has its **own in-memory caches** (provider dataset cache, screener fundamentals). Nothing is shared between instances or
  survives an instance stopping (persistent/shared caching is blocked on provider licensing, D3).
- Screener warm-up is **lazy and request-driven**: it is scheduled when a screener request arrives, at most `SCREENER_FUNDAMENTALS_PER_HOUR` (F)
  company loads per hour per instance, ≈ 11 FMP requests per cold company, LOW priority (deferred when the provider budget is short).
- Upper bound per instance that keeps receiving screener traffic (MODELED): `FMP_screener/day ≤ 24 × (11 × F + u)`, `u` = universe
  refreshes per hour (3 requests each). F = 25 → ≤ ~6,700/day; **F = 10 → ≤ ~2,700/day**. MEASURED (Phase 3 benchmark, F = 25, 150 companies):
  278 in the first hour, 4,353 in the first 24 h.
- With `cpu-throttling: true`, background coroutines get CPU only while the instance is serving a request, so warm-up progresses with traffic and
  is not continuous. With **min instances = 0** the instance is reclaimed after idling and the next instance starts cold: the screener's first
  hour cost is paid again (`≈ 3 + 11 × F` requests) and partial coverage is shown (labelled) until it warms.
- Background refresh cannot be assumed to happen while no instance is active. If periodic work is needed it must be triggered externally
  (Cloud Scheduler → an internal route); **no such job or route is created in Phase 5A**.

## 3. Scenarios (MODELED)

Common assumptions: staging traffic is a few operators (≪ 1 request/s), 1 vCPU / 1 GiB, request-based billing, concurrency 20.
`A` = active (billable) seconds per day for one instance; `S` = cold starts per day.

| | **A: min 0, max 1** | **B: min 0, max 3** | **C: min 1, max 3** |
|---|---|---|---|
| Idle cost | none | none | `1 × 86,400 × p_idle(1 vCPU, 1 GiB)` per day, every day |
| Active compute | `A × p_active` | `≤ 3 × A` in bursts; ≈ A at staging traffic | `A × p_active` (the min instance serves first) |
| Cold starts | every idle gap > the platform's idle timeout (UNKNOWN, observe) | same + extra instances during bursts | rare (only when scaling above 1) |
| Screener FMP/day (F = 10) | ≤ ~2,700, usually far less (warm-up only while serving); repeated first-hour cost per cold start | up to 3 × that if all three instances receive screener traffic | ≤ ~2,700 on the warm instance, continuous coverage; + bursts |
| Provider budget per instance | full plan × 0.8 | plan × 0.8 ÷ 3 (`CLOUD_RUN_MAX_INSTANCES=3`) — each instance gets a third, even when only one runs | same as B |
| Per-instance AI global budgets (Comparison 2,000/day, Earnings 5,000/day, anonymous AI hourly) | × 1 | up to × 3 | up to × 3 |
| Caches | lost on every scale-to-zero | lost per instance; duplicated across instances | kept on the min instance |
| Availability | cold start on first request after idle; one instance = no redundancy | absorbs bursts | best latency |

**Interpretation.** A is the cheapest and keeps provider usage bounded by one instance's budget and warm-up; its cost is cold starts and
repeated warm-ups — acceptable for staging. B adds burst capacity but multiplies screener/AI ceilings and splits the provider budget three ways
without lowering cost. C buys warm caches and latency for a fixed daily idle charge; it is a production candidate (Phase 4 D4 suggested min 1,
max 3, concurrency 80) once real traffic and plan limits are known.

## 4. Recommendation for staging

**Scenario A** (min 0, max 1, concurrency 20, 1 vCPU, 1 GiB, request-based CPU, startup CPU boost), `CLOUD_RUN_MAX_INSTANCES=1`,
`SCREENER_FUNDAMENTALS_PER_HOUR=10`, `PROVIDER_FMP_DAILY_TARGET` set to a staging target so overruns show as
`provider.fmp.dailyTargetExceeded` in the usage summaries, and a billing budget with alerts at 50/80/100 % (alerts are not caps).
Revisit memory after measuring heap/RSS in 5B; revisit min instances only for production.

## 5. Guard rails that already bound cost (Phase 4)

Admission per route group (cost-weighted, cache hits cheap), unknown-symbol existence gate, watch-data caps, provider token buckets and circuit
breakers (429/5xx), durable AI quotas checked before any Gemini call, usage summaries per minute. None of them is global across instances —
the deployment-wide bound is `max instances × per-instance limits`, which is why `CLOUD_RUN_MAX_INSTANCES` must equal the configured maximum.

## 6. Unknowns to fill before production

Current unit prices (`p_*`) and free tiers; FMP/Finnhub plan limits and overage terms (D5); Gemini project quota and prices; real DAU and
request mix; container cold-start time on Cloud Run; the platform idle-instance timeout; spending targets (D7).
