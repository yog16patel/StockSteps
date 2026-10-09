# StockSteps backend — Cloud Run environment inventory (Phase 5A)

Source of truth: `server/src/main/kotlin` at the Phase 5A working tree (every name below is read by the code — `System.getenv`, `AppConfig`,
`*.fromEnvironment`, or logback). Startup validation lives in `appconfig/StartupConfiguration.kt` (tests: `CloudRunReadinessTest`).
No real values appear here. "Cloud Run" means the process sees `K_SERVICE` (Cloud Run sets it automatically).

**Validation behaviour (Phase 5A).** In REAL mode a setting that is *present but malformed* stops startup with an error that names the setting
(never its value); before Phase 5A most of them silently fell back to a default. On Cloud Run, the settings marked **CR-required** must be present.
Errors are logged as `severity: ERROR` (`Configuration error: …`) and the process exits with status 1, so a bad revision never receives traffic.
Columns: **Dev** = default when unset; **Staging/Prod** = recommendation; **S** = sensitive (Secret Manager, never logged).

## 1. Application and platform

| Name | Purpose | Required | Dev | Staging | Production | S | Validation | Failure behaviour |
|---|---|---|---|---|---|---|---|---|
| `PORT` | HTTP port; bound on `0.0.0.0` | set by Cloud Run | 8080 | Cloud Run sets it (don't override) | same | no | integer 1–65535 | malformed → startup error (was: silently 8080) |
| `K_SERVICE` | Cloud Run marker (service name) | set by Cloud Run | unset | automatic | automatic | no | — | enables Cloud Run-only checks; MOCK refused when set |
| `STOCKSTEPS_DATA_MODE` | `real` (providers) or `mock` (fixtures) | no | `real` | `real` (explicit) | `real` | no | `real`/`mock`, case-insensitive | other value → startup error; `mock` with `K_SERVICE` → startup error |
| `LOG_FORMAT` | `text` lines or `json` (Cloud Logging entries with `severity`) | no | `text` | `json` (image default) | `json` | no | exactly `text` or `json` | other → startup error; not `json` on Cloud Run → warning |
| `FIREBASE_PROJECT_ID` | Project whose Firebase ID tokens are accepted; FCM project | **CR-required** | `stocksteps` | staging Firebase project | prod project | no | non-blank | missing on Cloud Run → startup error (default would point at the project literally named `stocksteps`) |
| `NEWS_FIRESTORE_PROJECT_ID` | Firestore project for user data and news simplifications | **CR-required** (or `GOOGLE_CLOUD_PROJECT`) | `GOOGLE_CLOUD_PROJECT`, else `stocksteps` | staging project | prod project | no | non-blank | missing on Cloud Run → startup error; differs from `FIREBASE_PROJECT_ID` → warning |
| `GOOGLE_CLOUD_PROJECT` | Fallback Firestore project (not set by Cloud Run) | no | unset | prefer `NEWS_FIRESTORE_PROJECT_ID` | same | no | — | — |
| `NEWS_FIRESTORE_DATABASE_ID` | Firestore database id | no | `(default)` | `(default)` | `(default)` unless a named DB is created | no | — | wrong id → Firestore errors per feature (503 `USER_DATA_UNAVAILABLE`) |
| `NEWS_STORE` | Simplified-news store | no | `firestore` | `firestore` | `firestore` | no | `firestore`/`sqlite` | other → startup error (was: silently disabled); `sqlite` on Cloud Run → warning (memory-backed, lost on scale-in) |
| `NEWS_DB_PATH` | SQLite path (local only) | no | `server/data/news.db` | unset | unset | no | — | file absent → ignored |
| `GOOGLE_APPLICATION_CREDENTIALS` | Service-account key file for ADC | **must not be set on Cloud Run** | unset (local ADC) | unset (runtime SA via metadata server) | unset | yes (if a key file) | — | missing credentials → user data and push disabled (warning), market data still served |
| `JAVA_OPTS` | JVM flags (start script) | no | none | image default `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` | same | no | — | — |
| `USAGE_SUMMARY_SECONDS` | Phase 4D usage summary interval (REAL) | no | 60 | 60 | 60 | no | integer; ≤ 0 disables | malformed → default |

Not configured anywhere and **not needed**: a public base URL (the server builds no absolute links; the apps hold their own base URL).

## 2. Financial and AI providers

| Name | Purpose | Required | Dev | Staging | Production | S | Validation | Failure behaviour |
|---|---|---|---|---|---|---|---|---|
| `FMP_API_KEY` | Financial Modeling Prep | **yes (REAL)** | — | Secret Manager (staging key) | Secret Manager (prod key) | **yes** | non-blank | missing → startup error. Never logged; `io.ktor` stays WARN (the key is a URL query parameter) |
| `FINNHUB_API_KEY` | Finnhub (news, earnings calendar, optional quotes) | **yes (REAL)** | — | Secret Manager | Secret Manager | **yes** | non-blank | missing → startup error |
| `GEMINI_API_KEY` | Gemini (insights, narration, Brief/Earnings/Comparison AI) | no | unset | Secret Manager (separate staging key/project) | Secret Manager | **yes** | — | unset → warning; AI routes use deterministic fallbacks or answer AI-unavailable (never invented content) |
| `GEMINI_NEWS_MODEL` | Default Gemini model | no | `gemini-3.5-flash-lite` | default | owner choice | no | — | — |
| `GEMINI_EARNINGS_MODEL`, `GEMINI_COMPARISON_MODEL` | Per-feature model overrides | no | `GEMINI_NEWS_MODEL` | unset | owner choice | no | — | — |
| `GEMINI_PRICE_INPUT_PER_MTOK_USD`, `GEMINI_PRICE_OUTPUT_PER_MTOK_USD` | Cost estimates in Comparison AI metering | no | unset (no estimate) | unset until prices are confirmed | confirmed prices | no | number | malformed → no estimate |
| `QUOTE_PROVIDER` | Quote source | no | `fmp` | `fmp` | `fmp` | no | `fmp`/`finnhub` | other → startup error |
| `FMP_DATASET_CACHE_ENTRIES` | FMP dataset cache size | no | 4,096 | default | default (watch memory) | no | positive int | malformed → default |
| `MARKET_AWARE_TTL` | Phase 3C session-aware lifetimes | no | on | on | on | no | `false` disables | — |
| `EARNINGS_AWARE_STATEMENTS` | Phase 3D statement refresh after reports | no | on | on | on | no | `false` disables | — |
| `SCREENER_EXCHANGES` | Screener universe exchanges | no | `NASDAQ,NYSE,TSX` | default | default | no | comma list | — |
| `SCREENER_UNIVERSE_LIMIT` | Companies per exchange | no | 50 | default | default | no | int | malformed → default |
| `SCREENER_MIN_MARKET_CAP` | Universe floor | no | 2,000,000,000 | default | default | no | long | malformed → default |
| `SCREENER_FUNDAMENTALS_PER_HOUR` | Background warm-up loads per hour per instance | no | 25 | **10** (cost) | 25 after cost review | no | int; ≤ 0 disables warm-up | malformed → default |

Provider circuit breakers and caches have no separate switches: breakers are part of `ProviderGuard` (budgets below); cache lifetimes come from
Phase 3 policies.

## 3. Phase 4 security

| Name | Purpose | Required | Dev | Staging | Production | S | Validation | Failure behaviour |
|---|---|---|---|---|---|---|---|---|
| `TRUSTED_PROXY_HOPS` | Which `X-Forwarded-For` entry (from the right) is the client IP | no | unset (guests share one pool per group) | **unset until verified** with a forged XFF | verified value (1 direct Cloud Run, 2 behind an external LB — verify) | no | integer 1–5 | malformed → startup error (was: silently unset); unset → warning |
| `APP_CHECK_ENFORCE` | Reject requests without a valid App Check token | no | `false` (monitor) | `false` | `false` until App Check SDKs ship, then `true` | no | `true`/`false` | other → startup error; `true` without project number → startup error |
| `FIREBASE_PROJECT_NUMBER` | Enables App Check verification (JWKS, audience) | no | unset (`appcheck.unconfigured`) | staging number once apps send tokens | prod number | no | digits only | non-digits → startup error |
| `APP_CHECK_APP_IDS` | Allowed Firebase app ids | no | any app in the project | Android + iOS staging app ids | prod app ids | no | comma list | — |
| `INTERNAL_OIDC_AUDIENCE` | Expected audience of Cloud Scheduler OIDC tokens | no | unset | see `CLOUD_RUN_SECRETS.md` §5 | service URL | no | set together with the next row | only one of the two → startup error (was: OIDC silently off) |
| `INTERNAL_OIDC_SERVICE_ACCOUNT` | Scheduler service-account email allowed on `/internal/…` | no | unset | scheduler SA | scheduler SA | no | set with the audience | as above |
| `ALERTS_EVALUATOR_TOKEN`, `EARNINGS_REMINDERS_TOKEN`, `DAILY_BRIEF_DISPATCH_TOKEN`, `USAGE_METRICS_TOKEN` | Per-job secrets (header `X-StockSteps-Scheduler-Token`) | no | unset (routes absent) | Secret Manager, distinct values, only if used | same | **yes** | ≥ 32 characters; all distinct | shorter → startup error (was: silently ignored); duplicates → those jobs disabled + error log; none and no OIDC on Cloud Run → warning (routes absent) |
| `WATCH_DATA_ANONYMOUS_MAX_SYMBOLS` | Watch-data symbols per request without a verified uid | no | 30 | 30 | 30 (100 only while old app builds exist) | no | positive int (clamped to 100) | 0/malformed → startup error |
| `ADMISSION_<GROUP>_UNITS_PER_MINUTE`, `ADMISSION_<GROUP>_ANONYMOUS_POOL_PER_MINUTE`, `ADMISSION_<GROUP>_MAX_IN_FLIGHT` | Per-group admission overrides; `<GROUP>` ∈ `MARKET_DATA, SCREENER, HISTORY, RESEARCH, EARNINGS, BRIEF, PUBLIC_AI, PREMIUM_AI, USER_DATA` | no | `AdmissionPolicy.DEFAULTS` | defaults | tune from usage summaries | no | positive int | malformed → default (logged nowhere; keep the defaults unless measured) |
| Route limiters: `SCREENER_REQUESTS_PER_MINUTE` (60), `EARNINGS_REQUESTS_PER_MINUTE` (120), `EARNINGS_PREMIUM_REQUESTS_PER_MINUTE` (30), `BRIEF_REQUESTS_PER_MINUTE` (120), `RESEARCH_REQUESTS_PER_MINUTE` (120), `COMPARISON_AI_REQUESTS_PER_MINUTE` (20), `REMINDER_REQUESTS_PER_MINUTE` (60) | Older per-route limiters (keyed on `limiterKey()`) | no | in brackets | defaults | defaults | no | int | malformed → default |

## 4. Phase 4 provider budgets

`ProviderBudgetConfig.fromEnvironment`: per-instance rate = plan per-minute × `PROVIDER_SAFETY_MARGIN` ÷ `CLOUD_RUN_MAX_INSTANCES`.

| Name | Purpose | Required | Dev | Staging | Production | S | Validation | Failure behaviour |
|---|---|---|---|---|---|---|---|---|
| `CLOUD_RUN_MAX_INSTANCES` | Instance count budgets are divided by | **CR-required** | 1 (unverified) | `1` = `maxScale` | = deployed max instances | no | integer ≥ 1 | malformed → startup error; missing on Cloud Run → startup error. **Must equal the service's max instances** (not readable by the process) |
| `PROVIDER_FMP_PER_MINUTE`, `PROVIDER_FINNHUB_PER_MINUTE`, `PROVIDER_GEMINI_PER_MINUTE`, `PROVIDER_BOC_PER_MINUTE` | Plan/project request rate | **CR-required** | dev defaults 600/60/60/30 (warning "not production-ready") | verified plan values (BoC: 10) | verified plan values | no | integer ≥ 1 | malformed → startup error; missing on Cloud Run → startup error |
| `PROVIDER_<P>_BURST` | Token-bucket burst | no | per-minute value (FMP dev 600) | default | tune | no | integer ≥ 1 | malformed → startup error |
| `PROVIDER_<P>_CONCURRENCY` | Concurrent upstream calls | no | FMP 24, Finnhub 8, Gemini 4, BoC 2 | defaults | plan-dependent | no | integer ≥ 1 | malformed → startup error |
| `PROVIDER_<P>_DAILY_TARGET` | Daily request target (emits `provider.<p>.dailyTargetExceeded`; **not a cap**) | no | unset | set for FMP to see overruns | per spending target (D7) | no | integer ≥ 1 | malformed → startup error |
| `PROVIDER_SAFETY_MARGIN` | Share of the plan this deployment may use | no | 0.8 | 0.8 | 0.8 (lower if other systems share the key) | no | 0.1–1.0 | out of range → startup error |
| `PROVIDER_BUDGET_COMPARISON_HISTORY_PER_HOUR`, `PROVIDER_BUDGET_COMPARISON_RESEARCH_PER_HOUR` | Per-feature hourly request budgets | no | unlimited (global guard still applies) | unset | optional | no | positive int | malformed → unlimited |

## 5. AI quotas (all durable per user unless noted)

| Name | Default | Notes |
|---|---|---|
| `BRIEF_AI_DAILY_LIMIT` | 15 | `brief-ai`, durable in `users/{uid}/meta/aiUsage` |
| `EARNINGS_AI_EXPLANATIONS_PER_DAY` / `EARNINGS_AI_QUESTIONS_PER_DAY` / `EARNINGS_AI_DIGESTS_PER_DAY` | 10 / 20 / 3 | durable; `EARNINGS_AI_DAILY_LIMIT` (20) is the legacy questions fallback and the research limit of `EarningsService` |
| `EARNINGS_AI_GLOBAL_DAILY_BUDGET` | 5,000 | **per instance** (multiply by instances) |
| `COMPARISON_AI_DAILY_LIMIT` / `COMPARISON_AI_30_DAY_LIMIT` | 10 / 50 | durable |
| `COMPARISON_AI_GLOBAL_DAILY_BUDGET` | 2,000 | **per instance** |
| `RESEARCH_AI_DAILY_LIMIT` | 20 | Guided Research assistant (MOCK templates only today) |
| `RESEARCH_PLUS_SESSION_LIMIT` | 100 | research sessions per StockSteps+ user |
| `STOCKSTEPS_PLUS_AI_DAILY_CAP` | unset (disabled) | optional combined cap across Brief + Earnings AI (owner decision D6) |

All are non-sensitive integers; malformed values fall back to the default. Store outages fail closed (503 `AI_QUOTA_UNAVAILABLE`).

## 6. MOCK and test only (never set on Cloud Run)

`STOCKSTEPS_MOCK_CLOCK` (ISO instant), `STOCKSTEPS_MOCK_ALERT_SECONDS` (60; 0 disables), `FIRESTORE_EMULATOR_HOST` (emulator tests, see
`FINANCIAL_API_PHASE5A_IMPLEMENTATION.md` §7). Setting `STOCKSTEPS_DATA_MODE=mock` on Cloud Run is refused at startup.

## 7. Minimal staging set

Required to start on Cloud Run: `FMP_API_KEY`, `FINNHUB_API_KEY` (secrets), `FIREBASE_PROJECT_ID`, `NEWS_FIRESTORE_PROJECT_ID`,
`CLOUD_RUN_MAX_INSTANCES`, `PROVIDER_{FMP,FINNHUB,GEMINI,BOC}_PER_MINUTE`. Recommended: `LOG_FORMAT=json` (image default),
`STOCKSTEPS_DATA_MODE=real`, `GEMINI_API_KEY` (secret), `SCREENER_FUNDAMENTALS_PER_HOUR=10`, `PROVIDER_FMP_DAILY_TARGET`.
Template: `deploy/cloud-run-staging.yaml`.
