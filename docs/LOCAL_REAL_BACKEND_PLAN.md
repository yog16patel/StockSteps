# StockSteps — LAN REAL backend on the Ubuntu host (plan, awaiting approval)

Status: **APPROVED 2026-10-16 (D-R1 to D-R6 as recommended). DEPLOYED 2026-10-16 (image `d85eb3e8fb04`, port 8082); results in §10.** Date: 2026-10-16. Base: `8fce9d1` (+ uncommitted 5D.5.6C3 work in progress and the
debug-only backend diagnostics below).

## 1. Why (finding from the Android Real-mode investigation)

On the physical Pixel 7 (debug build, `adb logcat -s StockStepsBackend`):

| Mode | Resolved request | Result |
|---|---|---|
| MOCK | `http://<server-lan-ip>:8081/api/v1/stocks/search?query=app` | 200 (also after an app restart) |
| REAL | `http://127.0.0.1:8080/api/v1/stocks/search?query=app` | `ConnectException` → "Couldn't load search results" (also after restart) |

- The toggle is not reversed. The setting picks the backend for each request (`BackendRouter.currentUrl`, read on every request), and the
  search view model is keyed by mode, so the query runs again after a switch. No guard blocks the request: no auth, no key, no minimum length
  beyond a blank query. The error reaches the UI (`stockDataRequest` → `StockDataException`). It isn't swallowed into an empty list.
- **Root cause:** the debug REAL URL defaults to `http://127.0.0.1:8080` (`app/androidApp/build.gradle.kts`, `stockstepsBackendUrl` is unset),
  which on a phone is the phone itself (no `adb reverse tcp:8080`). The Ubuntu host has **no REAL StockSteps server** to point at: the LAN
  deployment is MOCK-only by design (`docs/LOCAL_DEVELOPMENT_SERVER.md`), and port 8080 on the host belongs to an unrelated service.
- (2026-10-16 later: replaced by the shared DEBUG-only API log, tag `StockSteps-API`, `docs/DEBUG_API_LOGGING.md`.)
- Added (uncommitted, debug only): `app/shared/src/androidMain/.../BackendDiagnostics.kt`, an OkHttp interceptor that logs mode, base URL,
  redacted request URL, status or exception, enabled from `MainActivity` only when `BuildConfig.DEBUG`. It never logs headers or bodies.

## 2. Target

A second, independent container on the same host: **REAL data, LAN only, port 8082**, next to the untouched MOCK container on 8081.

```
phone / emulator / simulator ──▶ <server-lan-ip>:8081 ─▶ stocksteps-local-api-1  (MOCK, unchanged)
                              └▶ <server-lan-ip>:8082 ─▶ stocksteps-real-api-1   (REAL, new) ──▶ FMP / Finnhub (paid quota)
```

Port check on the host (2026-10-16, `ss -ltn`): 8080 and 8081 are in use, **8082 is free**. Host: 72 GB disk free, about 13 GB RAM available.

## 3. Container (`deploy/local/docker-compose.real.yml`, new)

Same hardening as MOCK (`read_only`, tmpfs `/tmp`, user 10001, `cap_drop: ALL`, `no-new-privileges`, 1 CPU / 1 GB, health check, bounded
logs, `restart: unless-stopped`). Differences:

| Setting | Value | Why |
|---|---|---|
| Compose project / network | `stocksteps-real` / `stocksteps-real` | MOCK project, network and images untouched; `deploy.sh` never touches the other project |
| Image | the same `stocksteps-local:<tag>` image built by `deploy.sh` | one Dockerfile; mode is runtime configuration only |
| Publish | `${STOCKSTEPS_BIND_ADDR}:${STOCKSTEPS_REAL_HOST_PORT:-8082}:8080` | LAN address only, never `0.0.0.0` |
| `STOCKSTEPS_DATA_MODE` | `real` | explicit |
| `LOG_FORMAT` | `text` | readable `deploy.sh logs` |
| Secrets | `env_file: ./secrets.env` on the host (`chmod 600`, owner-created) | see §4 |
| `CLOUD_RUN_MAX_INSTANCES` | `1` | single instance; budgets aren't divided |
| `PROVIDER_FMP_PER_MINUTE` / `PROVIDER_FINNHUB_PER_MINUTE` / `PROVIDER_BOC_PER_MINUTE` | owner's verified plan values (BoC 10) | avoid dev defaults (600/60/30, "not production-ready") |
| `PROVIDER_SAFETY_MARGIN` | `0.5` | the same keys may be used from the Mac; leave headroom (D-R3) |
| `PROVIDER_FMP_DAILY_TARGET` | owner's daily target | logs `provider.fmp.dailyTargetExceeded` (an alert, not a cap) |
| `SCREENER_FUNDAMENTALS_PER_HOUR` | `0` (warm-up off) at first | the background warm-up spends quota with no user present (D-R3) |
| `GEMINI_API_KEY` | **unset** at first | AI routes fall back to deterministic text or "AI unavailable" (D-R2) |
| Firebase / Firestore | no `GOOGLE_APPLICATION_CREDENTIALS` | signed-in user data and push are disabled (warning at startup); public market data and search work (D-R1) |
| `NEWS_STORE` | `sqlite`, `NEWS_DB_PATH=/tmp/news.db` | no Firestore; simplified news cached on tmpfs, lost on restart |
| `TRUSTED_PROXY_HOPS`, `APP_CHECK_*`, scheduler tokens, `K_SERVICE` | unset | direct LAN, no proxy, no scheduler, not Cloud Run |

## 4. Provider keys

- The **owner** creates `~/stocksteps-real/secrets.env` on the host over SSH (`FMP_API_KEY=…`, `FINNHUB_API_KEY=…`, later `GEMINI_API_KEY=…`),
  `chmod 600`, directory `chmod 700`. Claude never types, reads, prints or copies the values. `deploy.sh` checks that the file exists and is
  mode 600 (`stat`), never its contents.
- The file stays out of the repository and out of the uploaded build context (already excluded: `*.env`, `.env.*`).
- Use **separate dev keys** if the plans allow it (D-R3), so LAN usage can be told apart from future staging and production usage.
- Known exposure: anyone in the host's `docker` group can read container environment variables (`docker inspect`). This is acceptable on a
  single-owner host; otherwise use a dedicated host user.
- Logging is already safe: `io.ktor` at WARN, logs never contain provider URLs or keys (Phase 4 guard). Verify with `deploy.sh logs` after the
  first REAL request (`grep -i apikey` returns nothing).

## 5. `deploy.sh` changes (small)

- `STOCKSTEPS_STACK=real deploy/local/deploy.sh <command>` (default `mock`, unchanged) selects `docker-compose.real.yml`, remote directory
  `~/stocksteps-real`, project `stocksteps-real`, port `STOCKSTEPS_REAL_HOST_PORT` (new in `server.env.example`, default 8082).
- `deploy` for `real` refuses to start without `secrets.env` (mode 600) and never uploads it.
- `health` and `status` work for both stacks. `smoke` for `real` calls only `/health/*`, `/api/v1/meta` (expects `real`) and **one** search
  (`query=app`), so a smoke run costs at most one FMP request pair. The MOCK smoke test (many routes) must not run against REAL.
- `reliability` stays MOCK-only.
- Update `docs/LOCAL_DEVELOPMENT_SERVER.md` with a REAL section (§8 of this plan).

## 6. Apps

**Android.** `stockstepsBackendUrl` also feeds the **release** `BACKEND_URL`, so putting the LAN URL in `~/.gradle/gradle.properties` would
bake cleartext LAN HTTP into release builds. Recommended (D-R4): add a debug-only property `stockstepsDebugRealBackendUrl` (debug
`BACKEND_URL` = it, else `stockstepsBackendUrl`, else `127.0.0.1:8080`; release unchanged). Then set it per machine:

```properties
# ~/.gradle/gradle.properties (not in git)
stockstepsDebugRealBackendUrl=http://<server-lan-ip>:8082
```

The debug manifest already allows cleartext. Rebuild and reinstall; the stored REAL/MOCK choice survives `install -r`.

**iOS.** `Local.xcconfig` (git-ignored): `STOCKSTEPS_BACKEND_URL = http:/$()/<server-lan-ip>:8082`. Check that the REAL URL is debug-only
the same way the mock URL is (`BackendSettings`), and fix it if not (part of D-R4).

## 7. Verification (after approval and deployment)

1. `STOCKSTEPS_STACK=real deploy.sh deploy` → healthy; `/api/v1/meta` reports `real`; startup log shows only the expected warnings (no
   Firebase credentials, no Gemini, single instance).
2. Real smoke test: one search, 200, non-empty, no fabricated data. MOCK container unaffected (`deploy.sh health`).
3. Pixel 7: REAL search "app" → `StockStepsBackend` log shows `mode=REAL http://<server-lan-ip>:8082 … -> 200`. Switch to MOCK → 8081 → 200.
   Force-stop and relaunch in each mode → same.
4. iOS simulator: the same search in both modes.
5. Logs: no key or provider URL; usage summary (60 s) shows the expected `upstream` counts for the session.
6. Container restart keeps working (in-memory caches cold, no data loss, since there's no durable state).
7. Unverified by design: signed-in features (Portfolio, Watchlist sync, AI quotas) against REAL. They answer 503 `USER_DATA_UNAVAILABLE`
   without Firestore credentials (D-R1).

## 8. Rollback and removal

`STOCKSTEPS_STACK=real deploy.sh stop`; full removal `cd ~/stocksteps-real && docker compose down && rm -rf ~/stocksteps-real` (this also deletes
`secrets.env`). Apps: remove `stockstepsDebugRealBackendUrl` / the xcconfig line. MOCK is untouched throughout.

## 9. Decisions for the owner

- **D-R1 Firebase.** Recommended: no service-account key on the host at first (market data only). Alternative: mount a read-only
  service-account JSON for the dev Firebase project so signed-in features work against REAL (another secret on the host).
- **D-R2 Gemini.** Recommended: unset at first; add a dev key later with the existing AI quotas.
- **D-R3 Quota guard.** Recommended: separate dev FMP/Finnhub keys if available; `PROVIDER_SAFETY_MARGIN=0.5`; screener warm-up off;
  provider per-minute values and an FMP daily target taken from the owner's actual plans (needed before deployment).
- **D-R4 App URL.** Recommended: the debug-only `stockstepsDebugRealBackendUrl` (one Gradle line plus the iOS debug-only check) instead of
  setting `stockstepsBackendUrl`.
- **D-R5 Port.** Recommended: 8082 (free on 2026-10-16).
- **D-R6 Exposure.** The REAL server (paid keys) is reachable from the whole LAN. Recommended: accept on the home LAN with the admission limits.
  Alternative: bind to the host's LAN IP and add a UFW rule allowing only the Mac and phone addresses.

Implementation order once approved: D-R4 app change → compose file and `deploy.sh` stack switch → owner creates `secrets.env` → deploy →
verification §7 → docs and review package (`docs/reviews/local_real_backend/`) → commit on request.

## 10. Deployment record (2026-10-16)

- Released from a clean checkout of `d85eb3e` (deploy files copied in), so another session's uncommitted core work stayed out of the image.
  Image `stocksteps-local:d85eb3e8fb04`, Compose project `stocksteps-real`, `<server-lan-ip>:8082`. The owner created `secrets.env`
  (mode 600, keys `FMP_API_KEY`, `FINNHUB_API_KEY`). Claude only checked its mode and key names. Plan rates: FMP 250/min, Finnhub 60/min.
- Changes found during deployment (in `docker-compose.real.yml`):
  - `PROVIDER_GEMINI_PER_MINUTE=60` added. Without it, startup warned "development defaults" even though FMP and Finnhub budgets applied.
  - The SQLite news store was removed. Its native library can't load from the noexec `/tmp` (`UnsatisfiedLinkError`), and simplification needs
    Gemini anyway. The server serves original news.

| Check | Result | Evidence |
|---|---|---|
| Deploy, health, `/api/v1/meta` = `real` | PASS | `deploy.sh deploy` output |
| Container env (rates 250/60, margin 0.5, 1 instance, warm-up 0, mode real) | PASS | `docker exec printenv` per name (no key values) |
| Startup warnings only the expected ones (no Gemini, no proxy hops, original news, no user data); no errors | PASS | `deploy.sh logs` after redeploy |
| REAL smoke (live, ready, meta, search "app" → AAPL) | PASS 4/4 | `smoke-real.sh` |
| Logs contain no keys or provider URLs | PASS | grep for `apikey=`, `token=`, provider hosts: 0 |
| Restart: healthy, smoke again 4/4 | PASS | `deploy.sh restart` + smoke |
| MOCK container unaffected (running since 2026-10-15, 0 restarts, meta `mock`) | PASS | `deploy.sh status` / `health` |
| Usage counting (`stocksteps.usage`: FMP `search-name` ok) | PASS | container log |
| Android debug BuildConfig REAL = `<server-lan-ip>:8082`, MOCK = `:8081` | PASS | generated `BuildConfig.java` |
| Pixel 7: REAL search "app" → `<server-lan-ip>:8082` 200, live provider results (AppLovin best match, logos, no Sample strip); again after a cold restart | PASS | `StockStepsBackend` logcat + screenshot |
| Pixel 7: switch to MOCK → `:8081` 200 in the same session (no restart), fixture results; again after a cold restart | PASS | logcat + screenshot |
| Pixel 7: switch back to REAL (confirmation dialog) → `:8082` 200 in the same session; left on REAL | PASS | logcat, preference file |
| iOS simulator search in both modes | NOT RUN | needs `Local.xcconfig` `[config=Debug]` line and a rebuild |
| Signed-in features against REAL | NOT RUN | by design (D-R1, no Firestore credentials) |

Note: the shared `app/androidApp/build` output was overwritten once by another session's build with `-PstockstepsMockBackendUrl=http://127.0.0.1:8082`
(the startup diagnostics line showed it at once). The phone build was rebuilt with explicit `-PstockstepsMockBackendUrl` /
`-PstockstepsDebugRealBackendUrl` and installed from a private copy. The installed debug build also contains that session's uncommitted
Portfolio C4 work (no effect on search).
