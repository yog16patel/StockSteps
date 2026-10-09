# StockSteps backend — secrets and identities on Cloud Run (Phase 5A)

Preparation only: nothing here has been created. All names are **placeholders** (`<STAGING_PROJECT_ID>`, `stocksteps-staging-…`); replace them in
Phase 5B. No real key, token or account appears in this repository.

## 1. Principles

- Provider keys and scheduler secrets live only in **Secret Manager**, one secret per value per environment, and reach the container as
  environment variables through `secretKeyRef` (never `--set-env-vars`, never baked into the image, never in `deploy/*.yaml` as values).
- Firebase Admin, Firestore and FCM use **Application Default Credentials from the Cloud Run runtime service account** (metadata server).
  No service-account JSON key exists for Cloud Run; `GOOGLE_APPLICATION_CREDENTIALS` must not be set there. The image cannot contain one:
  `.dockerignore` is an allow-list and excludes `*service-account*.json`, `*credentials*.json`, `*-key.json`, `.env*`, `local.properties`.
- Development, staging and production use **separate keys and separate projects** (staging keys can't spend production quota; a leaked staging key
  is rotated without touching production).
- The server never logs secret values: startup validation names settings only; `io.ktor` logs stay at WARN (FMP's key is a URL query parameter);
  health and metrics responses contain no configuration (`/health/*` returns only a status word; `/internal/metrics/usage` returns counters and
  requires OIDC or its own token).

## 2. Secret inventory (staging placeholders)

| Secret (placeholder name) | Env variable | Needed for | Required |
|---|---|---|---|
| `stocksteps-staging-fmp-api-key` | `FMP_API_KEY` | quotes, profiles, statements, prices, screener | yes |
| `stocksteps-staging-finnhub-api-key` | `FINNHUB_API_KEY` | news, earnings calendar | yes |
| `stocksteps-staging-gemini-api-key` | `GEMINI_API_KEY` | AI explanations (StockSteps+ and budgeted anonymous AI) | no (AI falls back / unavailable) |
| `stocksteps-staging-alerts-token` … `-reminders-token`, `-brief-token`, `-usage-token` | `ALERTS_EVALUATOR_TOKEN`, `EARNINGS_REMINDERS_TOKEN`, `DAILY_BRIEF_DISPATCH_TOKEN`, `USAGE_METRICS_TOKEN` | only if scheduler jobs use per-job secrets (§5) | no |

Per-job tokens: ≥ 32 random characters each, all different (startup fails on short ones; a value shared by jobs disables those jobs).
`FIREBASE_PROJECT_NUMBER`, project ids and `APP_CHECK_APP_IDS` are identifiers, not secrets (plain env values).

Pin each `secretKeyRef` to an explicit version number in the service YAML so a revision is reproducible; rotate by adding a version, updating the
version in the YAML and deploying a new revision, then disabling the old version after traffic moved. Environment-variable secrets are read when
an instance starts — a new version is not picked up by running instances.

Example (Phase 5B, **do not run in 5A**; the value is typed on stdin, never on the command line or in shell history):

```bash
# printf '%s' "$VALUE" | gcloud secrets create stocksteps-staging-fmp-api-key --project=<STAGING_PROJECT_ID> --replication-policy=automatic --data-file=-
# gcloud secrets add-iam-policy-binding stocksteps-staging-fmp-api-key --project=<STAGING_PROJECT_ID> \
#   --member=serviceAccount:stocksteps-api-staging@<STAGING_PROJECT_ID>.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
```

## 3. Runtime service account (`stocksteps-api-staging@<STAGING_PROJECT_ID>.iam.gserviceaccount.com`)

Dedicated, used by nothing else; never the default Compute Engine service account (it has broad Editor rights in many projects).

| Grant | Scope | Why |
|---|---|---|
| `roles/secretmanager.secretAccessor` | **each secret above** (resource level, not project) | read its own secrets at instance start |
| `roles/datastore.user` | staging project | Firestore reads/writes: watchlists, alerts, outbox, devices, AI quota (`users/{uid}/meta/aiUsage`), entitlements, briefs, reminders, research, news simplifications |
| `roles/firebasecloudmessaging.admin` (or a custom role with `cloudmessaging.messages.create`) | staging Firebase project | FCM HTTP v1 push for alerts, reminders, brief notifications; only needed when push is tested |
| none | — | Firebase ID-token and App Check verification use public certificates/JWKS; Cloud Run collects stdout logs without a role |

To verify in Phase 5B (not provable locally): whether the Firestore client's built-in client metrics try to write to Cloud Monitoring
(the client libraries are on the classpath). If the logs show permission warnings for `monitoring.timeSeries.create`, either grant
`roles/monitoring.metricWriter` or leave them off — the server does not depend on them.

Not granted: `roles/editor`, `roles/owner`, `roles/firebase.admin`, `roles/iam.serviceAccountTokenCreator`, Secret Manager admin roles,
Artifact Registry write (the deployer pushes images, the runtime only runs them — Cloud Run's service agent pulls the image).

## 4. Deployer and operators

- Deployer (a person or CI identity): `roles/run.developer` on the service (or project), `roles/iam.serviceAccountUser` **on the runtime SA only**,
  `roles/artifactregistry.writer` on the `stocksteps` repository. No Secret Manager *accessor* role is needed to deploy a `secretKeyRef`.
- Invokers (staging): `roles/run.invoker` on the service for named operators and the scheduler SA only. **Never `allUsers`.**

## 5. Scheduler authentication (Phase 4 internal routes)

The four routes — `POST /internal/alerts/evaluate`, `POST /internal/earnings-reminders/dispatch`, `POST /internal/daily-brief/dispatch`,
`GET /internal/metrics/usage` — are registered in REAL only when `INTERNAL_OIDC_AUDIENCE` + `INTERNAL_OIDC_SERVICE_ACCOUNT` are set, or the job's
own token is set. The pre-Phase 4 single shared secret is **not compatible**: one value for several jobs disables them.

Two layers exist on staging and must not be confused:

1. **Cloud Run IAM** (because invocation requires authentication): Cloud Scheduler sends a Google-signed OIDC token for the scheduler SA
   (`roles/run.invoker`). Cloud Run checks it before the request reaches the container.
2. **The application check** (`InternalCallers`): either the same OIDC token (audience + service-account email) or the job's
   `X-StockSteps-Scheduler-Token`.

Finding (to verify in Phase 5B before relying on in-app OIDC): Cloud Run documents that for `X-Serverless-Authorization` it **removes the
token's signature** before passing it to the container; it does not document the same for a token in `Authorization`. The in-app
`GoogleOidcVerifier` needs the signature. Recommended staging design until verified:

- Scheduler job: `--oidc-service-account-email=stocksteps-scheduler-staging@…` and `--oidc-token-audience=<SERVICE_URL>` (satisfies Cloud Run IAM),
  **and** set `INTERNAL_OIDC_AUDIENCE=<SERVICE_URL>`, `INTERNAL_OIDC_SERVICE_ACCOUNT=stocksteps-scheduler-staging@…`.
- Test one job manually in 5B; if the in-app verification fails because the signature was stripped, switch that job to a per-job token
  header (§2) — the token is then stored in the Cloud Scheduler job configuration, so restrict `cloudscheduler.jobs.get` to operators.
- Scheduler service account: `roles/run.invoker` on this service only; no other roles.

Required schedules, idempotency and retries are in `CLOUD_RUN_DEPLOYMENT_CHECKLIST.md` §8. No scheduler job is created in Phase 5A.

## 6. Never

Commit `.env` files or keys; paste keys into chat, tickets or YAML; print `gcloud secrets versions access` output in shared terminals; deploy with
`GOOGLE_APPLICATION_CREDENTIALS`; reuse the FMP key that appears in old git history (it must stay revoked); read production secrets for staging work.
