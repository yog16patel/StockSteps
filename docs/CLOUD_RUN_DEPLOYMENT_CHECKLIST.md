# StockSteps backend — Cloud Run staging deployment checklist (prepared in Phase 5A, executed in Phase 5B)

Phase 5A created no cloud resources. Every command below is for Phase 5B, after review, with placeholders replaced. Related:
`CLOUD_RUN_ENVIRONMENT.md` (settings), `CLOUD_RUN_SECRETS.md` (secrets, IAM, scheduler auth), `CLOUD_RUN_COST_AND_SCALING.md`,
`deploy/cloud-run-staging.yaml`, `FINANCIAL_API_PHASE5A_IMPLEMENTATION.md`.

## 1. Owner inputs (blocking)

- [ ] Staging GCP project id, region, Firebase project id (separate from production).
- [ ] FMP, Finnhub plan limits per minute (D5) and a staging daily FMP target; Gemini project quota; staging keys distinct from production.
- [ ] Monthly spending targets and billing budget thresholds (D7).
- [ ] Logging check: confirm no earlier deployment ever ran with TRACE/DEBUG Ktor logging; if one did, rotate `FMP_API_KEY` first (Phase 4 §8 #1).
- [ ] Decision: how operators reach a staging service with `ingress: internal` for smoke tests (§5).

## 2. Build the image (no deploy yet)

- [ ] Tests green on the commit: `./gradlew :server:test`.
- [ ] Build for Cloud Run's architecture (Apple silicon hosts must cross-build):
  `docker buildx build --platform linux/amd64 -t <REGION>-docker.pkg.dev/<STAGING_PROJECT_ID>/stocksteps/stocksteps-server:<GIT_SHA> .`
  (or Cloud Build with the same Dockerfile).
- [ ] Inspect: `docker image inspect` → `User` is `10001:10001`, no secrets in `docker history`, size close to the Phase 5A result (203 MB compressed / 616 MB unpacked); run a vulnerability scan (Artifact Registry scanning or Trivy).
- [ ] Local run with test-only values (§9) → `/health/live` and `/health/ready` 200, JSON logs, clean SIGTERM.
- [ ] Push and record the **digest**; deploy only by digest.

## 3. Project setup

- [ ] APIs: Cloud Run, Artifact Registry, Secret Manager, Firestore, Firebase Cloud Messaging (only if push is tested), Cloud Scheduler (later).
- [ ] Artifact Registry Docker repository `stocksteps` in `<REGION>` with a cleanup policy.
- [ ] Firestore database in the staging project; security rules from `firestore.rules`.
- [ ] Firestore composite indexes required by the server (equality + range on different fields; without them the earnings-reminder
  dispatch fails with `FAILED_PRECONDITION`):
  - `earningsDeliveries`: `status` ASC, `dueAt` ASC
  - `earningsDeliveries`: `status` ASC, `leaseUntil` ASC
  (other queries use single-field equality/order indexes that Firestore creates automatically).

## 4. Identities and secrets

- [ ] Runtime SA `stocksteps-api-staging@…` with exactly the grants in `CLOUD_RUN_SECRETS.md` §3; not the default compute SA.
- [ ] Secrets created (values via stdin), accessor granted per secret to the runtime SA, versions pinned in the YAML.
- [ ] Deployer roles (§4 of the secrets doc). Invoker: named operators only; **never `allUsers`**.

## 5. Deploy and smoke test

- [ ] Fill `deploy/cloud-run-staging.yaml` (all `<…>`); confirm `maxScale` = `CLOUD_RUN_MAX_INSTANCES` = 1.
- [ ] `gcloud run services replace deploy/cloud-run-staging.yaml --region=<REGION> --project=<STAGING_PROJECT_ID>`.
- [ ] Revision becomes ready (startup probe on `/health/ready`). Startup errors appear as `Configuration error: …` (severity ERROR) and the
  revision is not served.
- [ ] Logs: `severity` populated; no URL with `apikey`; warnings only for known items (Gemini/OIDC/`TRUSTED_PROXY_HOPS` as configured).
- [ ] Smoke tests. `ingress: internal` blocks requests from the internet even with IAM credentials. Options (owner decision): run them from a
  VM in the staging VPC with `curl -H "Authorization: Bearer $(gcloud auth print-identity-token)"`, or temporarily set ingress to `all` while
  keeping IAM-required invocation and use `gcloud run services proxy stocksteps-api-staging --region=<REGION>`; restore `internal` afterwards.
  Mobile apps can't call an IAM-protected service (they send Firebase ID tokens, which Cloud Run IAM does not accept) — app testing against
  staging needs a separate decision (public ingress + App Check enforcement, or a gateway).
- [ ] Checks: `/health/live`, `/health/ready`, `/api/v1/meta` (`real`), one quote, one Company Details, one screener request; then the usage
  summary shows the expected upstream counts; memory (RSS) under 1 GiB with headroom.

## 6. Verify client identity before setting `TRUSTED_PROXY_HOPS`

- [ ] Determine the path: direct `run.app` (Google Front End appends the client IP to `X-Forwarded-For`) or a load balancer (adds a hop).
- [ ] Test: set the candidate `TRUSTED_PROXY_HOPS` on a test revision; from one client send requests with a different forged
  `X-Forwarded-For` value each time to a cheap `MARKET_DATA` route. Correct value ⇒ they all share one identity (429 arrives at the same count
  as without forging). Wrong value ⇒ each forged value gets its own window (no 429) → revert to unset.
- [ ] Only then set it in the YAML. Until then guests share one pool per group (documented residual risk).

## 7. App Check (monitor mode)

- [ ] After the apps send App Check tokens: set `FIREBASE_PROJECT_NUMBER` (and `APP_CHECK_APP_IDS`), watch `appcheck.valid|missing|invalid`
  in the usage summaries. `APP_CHECK_ENFORCE` stays `false` in staging until old builds are gone.

## 8. Cloud Scheduler (design only; create in 5B)

| Job | Route | Schedule (proposal) | Idempotency | Retries |
|---|---|---|---|---|
| Alerts | `POST /internal/alerts/evaluate` | every 15 min, Mon–Fri, US market hours (`*/15 9-16 * * 1-5`, `America/New_York`). The code does not check market hours itself | events go through the notification outbox; items are claimed with a 60 s lease, so an overlapping or retried pass doesn't send twice | 0–1 retry; each pass costs one quote per alerted symbol |
| Earnings reminders | `POST /internal/earnings-reminders/dispatch` | every 5 min (`docs/EARNINGS.md`) | deliveries have idempotency keys (user, type, event, offset, date) and leases | 0–1 retry |
| Daily Brief | `POST /internal/daily-brief/dispatch` | every 15 min (users choose their delivery hour) | per user, `lastNotifiedBriefId` is claimed before sending | 0–1 retry |
| Usage metrics | `GET /internal/metrics/usage` | none — operator on demand; cross-instance totals come from the usage summary logs | read only | — |

- [ ] Scheduler SA with `roles/run.invoker` on this service only; OIDC audience = service URL; app-side auth per `CLOUD_RUN_SECRETS.md` §5
  (verify the stripped-signature question on one job first).
- [ ] Attempt deadline ≤ the service timeout (60 s). Alert on job failures (non-2xx) via scheduler logs.
- [ ] With `min instances = 0`, each job run may cold-start an instance (and its caches): budget for it in the cost review.

## 9. Local container test values (never real)

`STOCKSTEPS_DATA_MODE=real`, `K_SERVICE=local-test`, `LOG_FORMAT=json`, `FMP_API_KEY=test-not-real`, `FINNHUB_API_KEY=test-not-real`,
`FIREBASE_PROJECT_ID=example-staging`, `NEWS_FIRESTORE_PROJECT_ID=example-staging`, `CLOUD_RUN_MAX_INSTANCES=1`,
`PROVIDER_{FMP,FINNHUB,GEMINI,BOC}_PER_MINUTE` small numbers, and **no Google credentials** (`GOOGLE_APPLICATION_CREDENTIALS=/nonexistent`),
so Firestore and FCM are disabled and only `/health/*` and `/api/v1/meta` are called. Don't call market routes with fake keys: they would reach
the real provider hosts and fail.

## 10. Monitoring (advised, D7)

Logs-based metrics on `jsonPayload.kind="stocksteps.usage"` (upstream by provider/endpoint, budget denials, breaker opens, admission 429s,
`appcheck.*`, `screener.coverage.*`), a dashboard, alerts on budget denials and 5xx rate, billing budgets at 50/80/100 %.

## 11. Rollback

- Traffic: `gcloud run services update-traffic stocksteps-api-staging --to-revisions=<PREVIOUS_REVISION>=100 --region=<REGION>`.
- Configuration: revisions are immutable; re-apply the previous YAML (kept in git) or roll traffic back.
- Image: always by digest, so a rollback restores exactly the previous build.
- Secrets: re-point `secretKeyRef` to the previous version (new revision); disable the bad version.
- Full stop: remove all `roles/run.invoker` grants (requests are rejected before they reach the container), or delete the staging service
  (owner approval).
- Code: Phase 5A changes are additive (health routes, validation, JSON logging, server-only build flag); reverting the commit restores the
  previous behaviour (`/health` is unchanged).
