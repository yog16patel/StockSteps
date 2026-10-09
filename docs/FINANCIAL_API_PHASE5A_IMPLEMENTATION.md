# Phase 5A — Google Cloud Run deployment preparation (implementation report)

Date: 2026-10-09. Base: `main` at `4586889` "Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage
monitoring" (verified with `git log`). Phase 5A is committed as **"Add Phase 5A Cloud Run deployment preparation: container image, health probes, startup validation, JSON logging and staging docs"** and pushed to `origin/main` together with Phase 4. Nothing was deployed, no cloud
resource, secret, service account or scheduler job was created, no production Firestore was touched, and no paid provider was called.

## 1. Architecture assessment (verified in code)

| Area | Finding |
|---|---|
| Modules | `:server` (Ktor 3.6, Kotlin/JVM, `application` plugin) depends on `:core` (KMP: JVM, Android, iOS targets). Settings also include `:app:androidApp`, `:app:shared` |
| Packaging | `installDist` (start script + `lib/` of 148 jars, 116 MB), already used by `runMock`. No fat JAR configured; no Dockerfile existed |
| Entry point | `ApplicationKt.main` → `embeddedServer(Netty)` on `0.0.0.0`, `PORT` (default 8080). Ktor 3.6 registers a JVM shutdown hook in `EmbeddedServer.start` (grace 1 s / timeout 5 s by default) |
| Data mode | `DataMode.fromEnvironment`: default REAL; MOCK refused when `K_SERVICE` is set. REAL never falls back to fixtures |
| Startup work | No provider call at startup: screener warm-up is request-driven; usage summaries only log. Firestore clients are constructed (no reads); Firebase token verifiers fetch public certificates lazily |
| Firebase/Firestore | `FirestoreOptions.getDefaultInstance()` with ADC → works on Cloud Run through the metadata server, no JSON key. Failure to obtain credentials → `UnavailableUserDataStore` (user-data routes 503), market data still served. FCM uses `GoogleCredentials.getApplicationDefault()` |
| Project selection | `FIREBASE_PROJECT_ID` and the Firestore project defaulted to the literal `stocksteps` (Cloud Run does not set `GOOGLE_CLOUD_PROJECT`) — a risk on Cloud Run, now guarded (§5) |
| Health | Only `GET /health` (text). No readiness concept |
| Logging | Plain text pattern on stdout (Cloud Logging would not recognise severities); `StockSteps.Usage` JSON lines; `io.ktor` WARN |
| Configuration | ~110 environment variables; most malformed values silently fell back to defaults (`toIntOrNull() ?: default`) |
| Background jobs | none in REAL except request-triggered warm-up and the usage reporter; alerts/reminders/brief dispatch are `/internal/…` routes for Cloud Scheduler |

## 2. Changes

Created:
- `Dockerfile` — multi-stage: Azul Zulu 21 JDK build stage (matches `gradle/gradle-daemon-jvm.properties`, so Gradle doesn't download a JDK),
  `-Pstocksteps.serverOnly=true :server:installDist`; Temurin 21 JRE (Ubuntu noble) runtime; both pinned by version **and digest**; user
  `10001:10001`; `LOG_FORMAT=json`; `JAVA_OPTS=-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`; entrypoint = the start script, which `exec`s
  java (PID 1 receives SIGTERM). No secrets, `.env` or credentials copied.
- `.dockerignore` — allow-list (`gradlew`, `gradle/`, root build files, `core/`, `server/`) plus exclusions for build outputs, `server/data/`,
  `local.properties`, `.env*`, service-account/credential JSON, Firebase app configs, keystores, logs.
- `deploy/cloud-run-staging.yaml` — Knative service template (non-deployable until placeholders are filled; provider limits are non-numeric
  placeholders that startup validation rejects).
- `server/.../appconfig/StartupConfiguration.kt` — startup validation (§5).
- `server/.../HealthRoutes.kt` — `GET /health/live`, `GET /health/ready`.
- `server/.../logging/CloudLoggingJsonLayout.kt` — JSON log lines with `severity`, `message`, `time`, `logger`, `thread`.
- `server/src/main/resources/logback-text.xml`, `logback-json.xml` — the two root appenders, selected by `LOG_FORMAT`.
- `server/src/test/.../CloudRunReadinessTest.kt` — 8 tests.
- Docs: `CLOUD_RUN_ENVIRONMENT.md`, `CLOUD_RUN_SECRETS.md`, `CLOUD_RUN_COST_AND_SCALING.md`, `CLOUD_RUN_DEPLOYMENT_CHECKLIST.md`, this report.

Modified:
- `settings.gradle.kts` — `-Pstocksteps.serverOnly=true` leaves out `:app:androidApp` and `:app:shared` (no Android SDK/Xcode in the container).
  Default builds are unchanged.
- `server/.../Application.kt` — `main()`: `PORT` via `StartupConfiguration.port()` (malformed → fail), explicit `shutdownGracePeriod = 2 s`,
  `shutdownTimeout = 8 s` (inside Cloud Run's 10 s); `module()`: runs validation right after the data mode is read (before any data source),
  installs the health routes. `/health` is unchanged.
- `server/src/main/resources/logback.xml` — includes `logback-${LOG_FORMAT:-text}.xml`; levels unchanged (root INFO, `io.ktor` WARN).
- `docs/project-status.md`, `PROJECT_HANDOFF.md`, `docs/PROJECT_HANDOFF.md`.

Not changed: Android/iOS code, `:core`, Phase 3/4 logic (admission, budgets, quotas, existence gate, provider paths).

## 3. Health endpoints

`/health/live` → 200 `{"status":"alive"}`; `/health/ready` → 200 `{"status":"ready"}` after `ApplicationStarted`, 503 `starting` before it and
503 `stopping` from `ApplicationStopPreparing` (SIGTERM) on. Both: `Cache-Control: no-store`, no configuration/version/mode in the body, no
provider/Firestore/Firebase access, outside every admission group (`RouteGroup.of` returns null — never rate limited, charged or App Check-gated).
Readiness means "wired and not stopping", not "providers and Firestore are up": downstream failures surface per feature (labelled stale /
unavailable, 503 `USER_DATA_UNAVAILABLE`, `AI_QUOTA_UNAVAILABLE`) and in the usage summaries.

## 4. MOCK/REAL isolation (verified)

1. MOCK on Cloud Run: refused at startup (`DataMode`, test `cloudRunNeedsExplicitProjectsBudgetsAndInstanceCount` and existing `MockModeTest`).
2. REAL never uses fixtures (data sources chosen once; unchanged); missing FMP/Finnhub keys now stop startup with a named error.
3. Missing required Cloud Run settings stop startup (§5); missing optional features (Gemini, internal auth, trusted hops) log warnings and those
   features answer unavailable / routes stay absent.
4. No startup provider calls: verified by running REAL with placeholder keys — zero outbound TCP connections (`lsof`) during startup, probes and
   `/api/v1/meta`.
5. Labelling unchanged (sample vs provider provenance, stale notices).
6. Practice Portfolio still refuses fills without a fresh quote (Phase 4 behaviour; `PracticeServiceTest` in the suite).
7. Phase 4 admission, provider budgets (`ProviderGuard.installed` in REAL) and durable quotas remain in the path; on Cloud Run the budgets can no
   longer start on development defaults.

## 5. Configuration validation (`StartupConfiguration`)

Errors stop startup (names only, never values; exit status 1). REAL mode:
- `FMP_API_KEY`, `FINNHUB_API_KEY` required.
- Present but malformed → error (previously silent fallback): `PORT`, `LOG_FORMAT` (`text`/`json`), `TRUSTED_PROXY_HOPS` (1–5),
  `APP_CHECK_ENFORCE` (`true`/`false`), `FIREBASE_PROJECT_NUMBER` (digits), `QUOTE_PROVIDER`, `NEWS_STORE`, `WATCH_DATA_ANONYMOUS_MAX_SYMBOLS`,
  `CLOUD_RUN_MAX_INSTANCES`, `PROVIDER_{FMP,FINNHUB,GEMINI,BOC}_{PER_MINUTE,BURST,CONCURRENCY,DAILY_TARGET}`, `PROVIDER_SAFETY_MARGIN` (0.1–1.0).
- `INTERNAL_OIDC_AUDIENCE` and `INTERNAL_OIDC_SERVICE_ACCOUNT` must be set together (half-configured OIDC used to be silently off).
- Per-job tokens shorter than 32 characters → error (they used to be silently ignored, leaving the job absent).
- `APP_CHECK_ENFORCE=true` without `FIREBASE_PROJECT_NUMBER` → error (also enforced by `AppCheckGuard`).
On Cloud Run additionally required: `FIREBASE_PROJECT_ID`, `NEWS_FIRESTORE_PROJECT_ID` (or `GOOGLE_CLOUD_PROJECT`), `CLOUD_RUN_MAX_INSTANCES`,
`PROVIDER_{FMP,FINNHUB,GEMINI,BOC}_PER_MINUTE`.
Warnings: no `GEMINI_API_KEY`; no internal job auth on Cloud Run; `TRUSTED_PROXY_HOPS` unset; Firestore project ≠ Firebase project;
`LOG_FORMAT` ≠ json on Cloud Run; `NEWS_STORE=sqlite` on Cloud Run.
MOCK validates only `PORT` and `LOG_FORMAT`. Off Cloud Run the existing development defaults and their "not production-ready" warning remain.

## 6. Verification

### 6a. Docker build and container test — **PASSED on a remote Ubuntu host** (2026-10-09)

The development Mac has no container runtime, so the image was built and tested on the owner's LAN server (Ubuntu 24.04.5 on the owner's LAN, x86_64, 4 CPUs,
15 GiB, Docker 29.1.3 / buildx 0.30.1, default builder). Owner-approved server changes: `docker-buildx` installed and the SSH user added to the
`docker` group (persistent; root-equivalent — remove with `sudo gpasswd -d <user> docker`). Everything else ran inside a new
`/tmp/stocksteps-phase5a-verify.*` directory (mode 700). The server's existing containers (8), images (7), networks (5) and volumes (3) were
compared before and after: unchanged. Added: the image `stocksteps-phase5a-verify:local` and BuildKit build cache (2.59 GB).

Transfer: only the `.dockerignore` allow-list (551 files, 6.9 MB; tarball without macOS metadata). Build:
`docker buildx build --builder default --platform linux/amd64 -t stocksteps-phase5a-verify:local .` → **success**, cold build 623 s (Gradle
`BUILD SUCCESSFUL in 9m 2s` incl. the Gradle 9.5.1 distribution download; no JDK provisioning — the Zulu build image satisfies the daemon JVM
criteria; no Android SDK; iOS targets skipped silently on Linux; only existing Kotlin warnings).

All runtime tests used `--network none` (no outbound access, no published ports), `--memory 1g --cpus 1` (the staging limits), test-only key
placeholders and `GOOGLE_APPLICATION_CREDENTIALS=/nonexistent` (Firestore/FCM disabled). Probes ran from inside the container over loopback.

| Check | Result |
|---|---|
| Image | `linux/amd64`, `User 10001:10001`, entrypoint `/app/bin/server`, `LOG_FORMAT=json`, `JAVA_OPTS` as designed; **203 MB compressed, 616 MB unpacked**, 8 layers |
| Secrets in image | `docker history` layer commands: none; no `.env`, service-account/credential JSON, `local.properties`, Firebase app configs or keystores anywhere in the filesystem; no Gradle/sources in the runtime image |
| Non-root | PID 1 is `java` (the start script `exec`s), uid 10001; `/app` owned by root (755) and not writable by the runtime user; `/tmp` writable |
| Bad configuration (REAL, `K_SERVICE`, missing Finnhub key/budgets/projects, `TRUSTED_PROXY_HOPS=two`) | exit 1, five `Configuration error` lines naming settings only; placeholder key value absent |
| MOCK on Cloud Run | exit 1, "Mock data mode is not allowed on Cloud Run" |
| `PORT=abc` | exit 1, "PORT must be a port number (1–65535)" |
| REAL, complete test-only config | ready in **5.6 s including container start** (app start 2.7 s at 1 vCPU); listening on `0.0.0.0:8080`; `/health/live`, `/health/ready`, `/health`, `/api/v1/meta` → 200; memory 114 MiB of 1 GiB at idle |
| Logs | 10 lines, **all JSON with `severity`** (INFO 5, WARNING 5); placeholder key values in logs: 0. One Netty warning about a missing hardware address is an artefact of `--network none` |
| SIGTERM (`docker stop -t 10`) | stopped in 0.13 s (idle), exit 143, not OOM-killed, one `Shutdown requested` line |
| `PORT=9090` with `--read-only` root filesystem + tmpfs `/tmp` | ready on 9090 (the app writes nothing outside `/tmp`) |
| MOCK, `LOG_FORMAT=text`, off Cloud Run | text logs, `/api/v1/meta` 200 |
| Cleanup | no verification containers left |

Image security observations (not blocking): the Temurin/Ubuntu runtime base contains `curl`, `wget` and 12 setuid/setgid binaries (standard
Ubuntu); the service runs as uid 10001, so they matter only through an unpatched base-image flaw. **No vulnerability scan was run** (no
scanner installed; installing one was not requested). Recommended before production: scan the pushed image (Artifact Registry scanning or
Trivy) and consider a smaller runtime base (e.g. distroless Java 21 with an argument-file classpath).

### 6b. Host-equivalent checks (what could be verified without Docker)
- **Build context**: recreated in the scratchpad with the `.dockerignore` allow-list (6.9 MB; no `local.properties`, `.env`, credentials, build
  outputs or `server/data`). Ran the Dockerfile's exact Gradle command there with `ANDROID_HOME`/`ANDROID_SDK_ROOT` unset and no
  `local.properties` → `BUILD SUCCESSFUL`, `:server:installDist` produced 148 jars (116 MB). (Compilation came from the local build cache;
  inside Docker it compiles from scratch.)
- **REAL on simulated Cloud Run with bad configuration** (`K_SERVICE` set, Finnhub key missing, `TRUSTED_PROXY_HOPS=two`, budgets/projects
  missing): five `Configuration error: …` JSON lines (severity ERROR) naming settings only, exit status 1, the placeholder key value absent
  from the output.
- **REAL on simulated Cloud Run with complete test-only configuration** (placeholder keys, `GOOGLE_APPLICATION_CREDENTIALS=/nonexistent` so
  Firestore/FCM are disabled): started in 0.47 s, listening on `*:18080`; `/health/live` 200, `/health/ready` 200, `/api/v1/meta` `real`;
  **no established outbound TCP connection**; logs JSON with severities; no key material in logs (`grep` count 0).
- **SIGTERM**: graceful stop in 2.1 s (grace period 2 s), exit status 143 (normal for SIGTERM); one `Shutdown requested` log line.
- **MOCK, `LOG_FORMAT` unset**: text log lines as before, no logback status output, `/health/ready` 200.
- Re-run on the final code: same results (start 0.36 s, zero outbound connections, `TESTKEY` occurrences in the log: 0).
- **YAML**: parses; `maxScale` = `CLOUD_RUN_MAX_INSTANCES` = 1; probes on `/health/ready` (startup) and `/health/live` (liveness).
- Base image digests resolved from Docker Hub (read-only registry queries): `azul/zulu-openjdk:21.0.11-21.50` (amd64 + arm64),
  `eclipse-temurin:21.0.12_8-jre-noble` (amd64, arm64, …). Estimated image ≈ 0.2 GB compressed (JRE layers 99.8 MB + app 103 MB gzipped).

### 6c. Automated tests
See §9 for the final results.

## 7. Firebase and Firestore readiness

- Cloud Run: ADC via the runtime service account; no JSON key needed or allowed. Verified locally only the "no credentials" path (graceful
  degradation); the real ADC path needs Phase 5B.
- Database: `NEWS_FIRESTORE_PROJECT_ID` + `NEWS_FIRESTORE_DATABASE_ID` (`(default)`); now explicit on Cloud Run.
- Transactions: durable AI quotas (`users/{uid}/meta/aiUsage`), entitlements, practice ledger, outbox claims and reminder leases use Firestore
  transactions; store outages fail closed for premium AI (503) and entitlement checks.
- Indexes: two composite indexes are required on `earningsDeliveries` (`status`+`dueAt`, `status`+`leaseUntil`); they are not defined in the
  repository (checklist §3).
- Emulator tests: the 3 skipped server tests are `FirestoreNewsSimplificationStoreTest` (they need `FIRESTORE_EMULATOR_HOST`). Run with the
  repo's firebase-tools: `cd firebase && npx firebase emulators:exec --project demo-stocksteps --only firestore --config ../firebase.json
  "cd .. && ./gradlew :server:test --tests '*FirestoreNewsSimplificationStoreTest'"` (emulator port 8085 from `firebase.json`; downloads the
  emulator on first use; needs Java). `FirestoreUserDataStore` (AI quota transactions, entitlements, outbox) has **no** emulator test — a gap to
  close before production. Firestore rules tests: `cd firebase && npm test`.

## 8. Security findings

Fixed in Phase 5A:
- **P1** Silent fallbacks for malformed security settings (`TRUSTED_PROXY_HOPS`, half-configured OIDC, short per-job tokens, invalid
  `NEWS_STORE`/`PORT`) → startup errors.
- **P1** On Cloud Run the Firebase/Firestore project defaulted to `stocksteps` (possibly production) → must be explicit.
- **P1** On Cloud Run the provider budgets could start on development defaults (FMP 600/min per instance) and `CLOUD_RUN_MAX_INSTANCES` could be
  missing → must be explicit.
- **P2** Unstructured logs on Cloud Run → `LOG_FORMAT=json` with severities (same content as before; no MDC/request data).

Open (no P0 found in code):
- **P1 (verify in 5B)** Scheduler OIDC: with IAM-required invocation, confirm that the in-app `GoogleOidcVerifier` receives a verifiable token;
  Cloud Run documents removing the signature for `X-Serverless-Authorization`. Fallback: per-job tokens (`CLOUD_RUN_SECRETS.md` §5).
- **P1 (owner, from Phase 4)** Confirm no deployment ever logged at TRACE (else rotate the FMP key); `TRUSTED_PROXY_HOPS` unverified until a
  deployed topology exists (checklist §6); the old FMP key in git history must stay revoked.
- **P2** Mobile apps cannot call an IAM-protected staging service; public exposure needs a separate decision (App Check enforcement, gateway).
- **P2** Firestore composite indexes not in the repository; no emulator test for `FirestoreUserDataStore`.
- **P2** No vulnerability scan of the image yet; the runtime base ships `curl`, `wget` and setuid binaries (§6a). Container build and run
  verified on Linux `amd64` (§6a).
- Info: the runtime classpath carries unused native libraries (e.g. Netty QUIC for macOS/Windows) — size only.

## 9. Test results

| Command | Result |
|---|---|
| `./gradlew :server:test --tests 'org.example.stocksteps.CloudRunReadinessTest'` | 8 passed, 0 failed |
| `./gradlew :server:test --continue` (final code) | **BUILD SUCCESSFUL: 481 tests, 0 failed, 3 skipped** (the Firestore-emulator tests, §7). Includes `LoggingSecurityTest`, `PublicApiProtectionTest`, `ProviderBudgetTest`, `DurableAiQuotaTest`, `UsageSummaryTest`, `PracticeServiceTest`, `MockModeTest` |

`:core`, Android and iOS code did not change, so their suites and the mobile builds were not re-run (`settings.gradle.kts` changed only behind
an opt-in property; `./gradlew projects` still lists `:app:androidApp` and `:app:shared` by default, and only `:core`/`:server` with
`-Pstocksteps.serverOnly=true`).

## 10. Remaining cloud prerequisites (Phase 5B)

Staging project/region/Firebase project; Artifact Registry repo; runtime SA + grants; Secret Manager secrets; verified FMP/Finnhub/Gemini limits;
Firestore database, rules and the two composite indexes; an operator access path for smoke tests under `ingress: internal`; scheduler SA and
design (after the OIDC verification); billing budget and alerts; a machine with Docker (or Cloud Build) to build `linux/amd64`.

## 11. Known risks

Cold starts at min 0 (JVM + image pull, unmeasured); screener caches and warm-up lost on scale-to-zero; per-instance AI global budgets and
admission are not global; usage summaries pause with CPU throttling between requests; unverified Linux build; 1 GiB memory unvalidated.

## 12. Rollback

Phase 5A code is additive. Revert the working-tree changes (or the future commit) to restore the previous behaviour; `/health` is unchanged and
no client contract changed. Cloud rollback procedures for 5B: `CLOUD_RUN_DEPLOYMENT_CHECKLIST.md` §11.

## 13. Phase 5B readiness verdict

**READY WITH CONDITIONS.** The server is container-ready in code (dynamic `PORT`, `0.0.0.0`, graceful SIGTERM, JSON logs, provider-free probes,
fail-fast configuration, MOCK refused on Cloud Run, no secrets in the image or context), and the staging template, environment inventory, secrets
plan, cost model and checklist exist; the image builds for `linux/amd64` and passed the container checks in §6a (non-root, no secrets, fail-fast
configuration, JSON logs, probes, SIGTERM, read-only root filesystem, no network). Conditions: provide the owner inputs in checklist §1; scan
the image for vulnerabilities; resolve the scheduler OIDC question before enabling jobs. Not production-ready: these are local/LAN results only.
