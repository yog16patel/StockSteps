# Financial API — Phase 4 observability (read-only)

Date: 2026-10-09, `main` at `6a70d0e`. Labels as in `FINANCIAL_API_PHASE4_SECURITY_AUDIT.md`.

## 1. Existing instrumentation (CONFIRMED)

| Source | What it records | Scope | Gap |
|---|---|---|---|
| `ProviderCalls.record` (`httpclient/NetworkUtils.kt`) | every FMP/Finnhub request: provider, endpoint (no query), feature, outcome (`ok`, `error`, `rateLimited`, `denied`, `timeout`, `invalid`, `cancelled`), latency | per instance, in memory | not exported; reset on restart |
| BoC FX, Gemini callers | `ProviderCalls.record` themselves; Gemini token counts via `generateWithUsage` → meter events | per instance | tokens not per feature in all callers (verify) |
| `ProviderUsageMeter` (`service/ProviderUsage.kt`) | counters `(provider, endpoint, feature, event)` + named events (`cache.<name>.<hit/miss/join/expired/evict>`, `screener.warm.*`, `statements.earningsSignal`, `fmp.statements.earningsRefresh`, `fmp.dataset.staleServed`, `research.*`) | per instance | no time series; one instance per scrape |
| `/internal/metrics/usage` | JSON report of the meter | whichever instance answers; shared secret | not aggregated across instances |
| Logs | logback root **TRACE** (`resources/logback.xml`) → full provider URLs including the FMP key; provider failures logged as `host/path/status`; unhandled errors log the exception class only | stdout → Cloud Logging (assumed) | **P0 secret exposure and noise** |
| Cloud Run metrics (instance count, request count, latency, CPU/memory) | built in | platform | not correlated with provider usage |

Missing: screener coverage gauge, quota denials by feature (exceptions only), budget exhaustion events (none exist yet), provider latency
percentiles, estimated spend.

## 2. Metric inventory to export

| Metric | Source | Type | Labels (bounded) |
|---|---|---|---|
| provider requests | `ProviderCalls` | counter | provider, endpoint, feature, outcome |
| provider latency | `ProviderCalls` | distribution | provider, endpoint |
| cache events | meter `cache.*` | counter | cache name, event |
| single-flight joins | meter `cache.*.join` | counter | cache name |
| AI calls / tokens in / tokens out | Gemini callers | counter | feature, model |
| AI quota denials | quota classes | counter | feature, code |
| rate-limit denials | route limiters | counter | route group, identity kind (uid/ip) |
| screener warm-up loads, budget reached, failures | meter `screener.warm.*` | counter | — |
| screener coverage | `ScreenerService.universeInfo` | gauge | — |
| earnings accelerated refreshes | `fmp.statements.earningsRefresh` | counter | — |
| stale served | `fmp.dataset.staleServed` | counter | — |
| budget exhaustion | Phase 4C | counter | provider, priority class |
| instance count, request latency | Cloud Run built-in | — | — |

Never as labels: symbols with user ids, uids, prompts, notes, portfolio contents, tokens, API keys, full URLs.

## 3. Export options

| Option | Effort | Cost (ingestion/storage) | Fit |
|---|---|---|---|
| **Structured usage log line per instance per minute** (JSON deltas of the meter) + **logs-based metrics** | low (one coroutine + logback JSON encoder or a single `log.info` with JSON) | ≈ 1–2 KB/min/instance (≈ 1.5–3 MB/day) | **recommended first** |
| Cloud Monitoring custom metrics (API writes) | medium (client library, service account) | per time series pricing; write quotas | later, if logs-based metrics are too coarse |
| `/internal/metrics/usage` polling | none | none | diagnostics only (per instance) |
| Cloud Billing budgets + alerts | console setup | none | **required**, but alerts are notifications, **not spending caps** |

Prices for Cloud Logging/Monitoring: confirm on the current Google Cloud pricing pages (not hard-coded).

## 4. Dashboard (minimal, Cloud Monitoring)

1. **Provider consumption**: requests/min and /day by provider and endpoint; outcome split; vs plan limit line (D5).
2. **AI usage**: calls and tokens by feature; quota denials; anonymous AI budget usage.
3. **Cache efficiency**: hit/miss/join/evict per cache; stale served.
4. **Backend scaling**: Cloud Run instance count, request latency p50/p95, concurrency.
5. **Rate-limit denials**: by route group and identity kind.
6. **Screener coverage**: evaluated/size per instance; warm-up loads; budget reached.
7. **Errors and outages**: provider `rateLimited`/`denied`/`error`/`timeout` rates; 5xx from the API.

## 5. Alerts (initial thresholds; tune after two weeks of data)

| Alert | Condition (initial) | Why |
|---|---|---|
| FMP spike | requests/hour > 2 × trailing 7-day same-hour median, or > 80 % of the plan's hourly/daily limit (D5) | abuse, cache regression |
| Gemini token spike | tokens/hour > 2 × median or > daily target ÷ 12 | prompt abuse, quota failure |
| Repeated 429 | `rateLimited` > 20 in 5 min for one provider | plan limit reached |
| Instance count | > max expected (D4) for 10 min | scale-out multiplies provider usage |
| Screener coverage | evaluated < 80 % of size for 2 h on any warm instance | warm-up failing |
| Quota enforcement failures | quota store errors > 0 for 5 min | fail-closed outage for premium AI |
| Provider outage | `error`/`timeout` > 50 % for 10 min | user-visible degradation |
| Log volume | log bytes/hour > 3 × baseline | TRACE logging re-enabled |
| Billing | budget alerts at 50/80/100 % of monthly target (D7) | spend (notification only) |

## 6. Logging and privacy controls

- Root log level INFO; `io.ktor.client` WARN (never log request URLs; FMP's key is a query parameter).
- Rotate the FMP key if TRACE logs ever reached Cloud Logging; restrict log viewer access; set retention deliberately.
- Never log: API keys, ID tokens, App Check tokens, prompts/questions, notes, portfolio data, push tokens, full provider payloads; uids only when
  required for support (hash otherwise).
- Keep the existing rule: exception messages aren't logged (they can contain URLs).

## 7. Runbooks

| Event | First steps |
|---|---|
| FMP 429 / spike | check top endpoints/features on the dashboard; if abuse: tighten per-IP limits or enable App Check enforcement; lower `SCREENER_FUNDAMENTALS_PER_HOUR`; confirm plan limit |
| Gemini spike | check feature split; lower anonymous AI budgets (insight/movement); verify quota store health |
| Coverage drop | check `screener.warm.failed` and provider errors; budget reached vs failures |
| Quota store errors | premium AI fails closed (expected); check Firestore status |
| Instance surge | inspect request sources; confirm max-instances; consider temporary concurrency increase |
| Key exposure | rotate key at provider, update secret, redeploy, purge/limit log access |

## 8. Production validation required

Verify on a deployment (explicitly authorised): the client-IP hop count (send a request with a forged `X-Forwarded-For` and confirm the extracted
IP), log level and absence of keys in Cloud Logging, logs-based metric extraction, alert delivery, App Check monitor-mode rates, and that
`/internal/*` routes reject requests without valid OIDC/secret.
