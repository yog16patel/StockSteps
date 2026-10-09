# Phase 5B Local — Docker deployment and integration verification (implementation report)

Date: 2026-10-09. Base: `main` at `d138b19` "Add Phase 5A Cloud Run deployment preparation: container image, health probes, startup
validation, JSON logging and staging docs" (pushed). Phase 5B Local is committed as **"Add Phase 5B Local Docker deployment of the MOCK backend on a LAN host with smoke and reliability checks"** (pushed).

> **Phase 5B Local = local integration milestone. Cloud Run staging deployment remains deferred and unverified.** Nothing was deployed to
> Google Cloud, no cloud resource was created, no paid provider was called. Results below come from a LAN Ubuntu host, the development Mac,
> an Android emulator and an iOS Simulator.

Operator guide: `docs/LOCAL_DEVELOPMENT_SERVER.md`.

## 1. Summary

A persistent MOCK StockSteps backend now runs in Docker Compose on the LAN Ubuntu host (`stocksteps-local`, published on the host's LAN
address, port 8081), reachable from the Mac, the Android emulator and the iOS Simulator. Deployment, lifecycle, smoke and reliability checks
are scripted (`deploy/local/`). Android needed no source change (its debug-only mock URL property already existed); iOS gained an optional,
git-ignored `Local.xcconfig` override. Release configurations are unchanged.

## 2. Infrastructure audit (read-only, before any change)

| Item | Finding |
|---|---|
| Host | Ubuntu 24.04.5, x86_64, 4 CPUs, 15 GiB RAM (13 GiB available), 73 GB free, load ≈ 0.2 |
| Docker | Engine 29.1.3, Compose v2.40.3, buildx 0.30.1 (installed by the owner in Phase 5A), default builder |
| Existing services | Compose projects `immich` (`/opt/immich`, 4 containers) and `nextcloud` (`/opt/nextcloud`, 4); networks `bridge`, `immich_default`, `nextcloud_default` (172.17–19.0.0/16); 3 volumes |
| Ports in use | 22, 2283 (Immich, all interfaces), LAN-IP:8080 (Nextcloud), 53 (resolver, loopback), 127.0.0.1:37115 → **8081 free** |
| Firewall | UFW active (rules not readable without sudo; not changed). Docker-published ports were reachable from the LAN |
| mDNS | `avahi-daemon` not running (no `.local` name) — not needed, see §6 |
| StockSteps leftovers | Phase 5A image `stocksteps-phase5a-verify:local`, 2.59 GB BuildKit cache, `/tmp/stocksteps-phase5a-verify.*` — left untouched |

## 3. Files

Created:
- `deploy/local/docker-compose.yml` — Compose project `stocksteps-local` (§4).
- `deploy/local/deploy.sh` — deploy, start/stop/restart, status, logs, health, releases, rollback, smoke, reliability.
- `deploy/local/smoke-test.sh` — 23 HTTP checks (§7).
- `deploy/local/reliability-check.sh` — host-side reliability/security checks (§8).
- `deploy/local/server.env.example` — connection template (the real `server.env` is git-ignored).
- `app/iosApp/Configuration/Local.xcconfig.example` — per-developer iOS override template.
- `docs/LOCAL_DEVELOPMENT_SERVER.md`, this report.

Modified:
- `app/iosApp/Configuration/Config.xcconfig` — `#include? "Local.xcconfig"` at the end (optional include; no effect when the file is absent).
- `.gitignore` — `deploy/local/server.env`, `app/iosApp/Configuration/Local.xcconfig`.
- `PROJECT_HANDOFF.md`, `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`, `CLAUDE.md` (current task).

Local, git-ignored (not part of the change set): `deploy/local/server.env` (host address), `app/iosApp/Configuration/Local.xcconfig`
(LAN mock URL used for the Simulator test). Not changed: server/core/app source code, the Dockerfile, release configurations.

## 4. Docker Compose configuration (verified on the host)

Image `stocksteps-local:<git-sha>` built from the repository Dockerfile for `linux/amd64`; `STOCKSTEPS_DATA_MODE=mock`, `LOG_FORMAT=text`,
no `K_SERVICE`, no keys or credentials; dedicated bridge network `stocksteps-local`; published `<LAN-IP>:8081 → 8080` only; user 10001;
read-only root filesystem + 64 MB tmpfs `/tmp`; `cap_drop: ALL`; `no-new-privileges`; `pids_limit 256`; 1 CPU / 1 GiB; restart
`unless-stopped`; health check `curl -fsS …/health/ready` (curl is part of the runtime base image — nothing added); json-file logs 10 MB × 3;
no volumes. `docker compose config --quiet` → valid; without `STOCKSTEPS_BIND_ADDR` Compose refuses to interpolate (no accidental `0.0.0.0`).
The network is a normal bridge because Docker does not publish ports from `internal: true` networks; MOCK makes no outbound calls (§7).

## 5. Deployment (on the host, owner-approved)

Directory `~/stocksteps-local/` (mode 700): `docker-compose.yml`, `.env` (tag, context, bind address, port — no secrets), `releases/<tag>/`
(allow-listed build context, newest 5 kept), `reliability-check.sh`. First deploy of `d138b193ad44`: build fully cached from Phase 5A
(identical sources), container healthy, total 12 s. Lifecycle verified: `stop` → unreachable (`000`), `start` → healthy, `restart` → healthy,
`rollback d138b193ad44` → healthy, `releases`, `logs`, `status`.

## 6. Network and mobile integration

| Check | Result |
|---|---|
| Container listens on 8080; Docker publishes `<LAN-IP>:8081` only | ✅ `docker port` / `ss -ltn` show only the LAN address |
| Mac → `/health/live`, `/health/ready`, `/api/v1/meta` | ✅ 200, 200, `{"dataMode":"mock"}` |
| Android emulator (API "17" image, `emulator-5554`) → LAN server | ✅ raw HTTP from the emulator shell: `/api/v1/meta` 200 |
| Android debug build with `-PstockstepsMockBackendUrl=http://<LAN-IP>:8081` | ✅ `assembleDebug` OK; debug `BuildConfig.MOCK_BACKEND_URL` = LAN URL; release keeps `""` (code) |
| Android app end-to-end on emulator/phone | **Not run** — the installed app on the user's emulator was not replaced and the UI was not driven (project rule); manual steps in the guide §5 |
| iOS Debug build with `Local.xcconfig` | ✅ `xcodebuild … CODE_SIGNING_ALLOWED=NO build` BUILD SUCCEEDED; built Info.plist `StockStepsMockBackendURL` = LAN URL |
| iOS app in Simulator (spare iPhone 16, iOS 18.3; app installed, MOCK selected through `defaults`, launched — no UI taps) | ✅ "Sample data · mock backend" banner shown, which requires a successful `/api/v1/meta` = `mock` from the LAN URL → **ATS allows plain HTTP to the raw LAN IP with the existing `NSAllowsLocalNetworking`**; option b (avahi/`.local`) not needed. Test app uninstalled and the simulator shut down again |
| Physical Android phone / iPhone | **Not run** (no device attached). iPhones show a Local Network permission prompt on first use |

## 7. Smoke tests (`deploy/local/deploy.sh smoke`, from the Mac)

**23 passed, 0 failed.** Areas: health + meta (3), market overview labelled sample with "Not live" notice (1), company search + 400
`INVALID_QUERY` (2), company details + 400 `INVALID_SYMBOL` (2), screener catalog + preset search labelled sample (2), comparison + 400
`INVALID_COMPARISON` (2), earnings calendar labelled sample (1), Daily Market Brief labelled sample (1), Guided Research progress with a MOCK
bearer + 401 without (2), Practice Portfolio account, order preview, 400 `REVIEW_REQUIRED` without a reviewed price, execute with the reviewed
price, holding recorded, 401 without sign-in (6), **zero `upstream` provider requests** in the MOCK usage counters (1). A first run had 2
failures caused by the test (execution without the preview's `reviewedPrice` correctly returns `REVIEW_REQUIRED`); the script was fixed to send
it, as the apps do, and a negative check was added.
Load: 320 representative requests (overview, search, details, 3-company comparison, earnings, brief, 1Y chart, screener search) → 320 × 200 in 21 s.

Scope: the signed-in checks use the MOCK-only bearer `mock-user:<uid>` — **API route smoke tests, not Firebase sign-in or Firestore**, and not
mobile end-to-end flows. Comparison research, saved screens, AI routes and premium earnings were not exercised by the script.

## 8. Reliability and security (`deploy/local/deploy.sh reliability`)

| Check | Result |
|---|---|
| Memory after the 320-request load | 131.6 MiB, cgroup peak **134 MiB** of 1,024 MiB; CPU idle 0.2 %; 19 PIDs |
| Runtime user / filesystem | PID 1 uid 10001; writes to `/app` and `/etc` denied (read-only rootfs); `/tmp` tmpfs |
| Privileges | `Privileged=false`, `CapDrop=[ALL]`, no `CapAdd`, `no-new-privileges`, pids 256, 1 CPU, 1 GiB |
| Binding | `8080/tcp -> <LAN-IP>:8081` only; no `0.0.0.0` |
| Secrets | container env holds only mode/log/Java settings (0 secret-like names); logs: 0 key/bearer lines |
| Logs | json-file 10 MB × 3; readable with `deploy.sh logs` (MOCK writes one reminder-pass line per minute) |
| Crash → restart policy | SIGTERM to PID 1 from inside (process exit, not `docker stop`) → Docker restarted it (RestartCount 0 → 1), healthy again in ≈ 10 s |
| Health check detects an unhealthy backend | throwaway containers with the same probe: control `healthy`; app on another port (probe gets no answer) → `unhealthy` (failing streak 10); both removed |
| MOCK in-memory state | practice holding for a test uid: 1 before `restart`, 0 (cash 10,000) after — as documented |
| Existing services | all 8 Immich/Nextcloud containers still up with their original uptime; no other resource changed |
| Docker daemon restart / host reboot | **not tested** (forbidden); `unless-stopped` restarts the container after either by policy |

## 9. Automated tests

| Command | Result |
|---|---|
| `./gradlew :server:test :core:jvmTest --continue` | ✅ BUILD SUCCESSFUL — server **481 passed, 0 failed, 3 skipped** (Firestore emulator); core JVM **416 passed, 0 failed** (tasks up to date: no server/core source changed since those runs) |
| `./gradlew :app:androidApp:assembleDebug -PstockstepsMockBackendUrl=http://<LAN-IP>:8081` | ✅ |
| `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` | ✅ BUILD SUCCEEDED (with `Local.xcconfig`) |
| `docker compose config --quiet` (host) | ✅ valid; missing bind address rejected |
| Smoke (§7) / reliability (§8) | ✅ 23/23; all checks as listed |
| `:core:iosSimulatorArm64Test`, `:app:shared:testAndroidHostTest`, `:app:shared:iosSimulatorArm64Test` | **Not run** — no Kotlin source changed |
| Device end-to-end (Android app, physical phones) | **Not run** |

## 10. Security decisions

LAN-only publish on an explicit address; MOCK only, no credentials anywhere; non-root, read-only, no capabilities; no new host ports beyond 8081
on the LAN IP; no firewall/router/DNS/tunnel changes; host address kept out of the (public) repository via `server.env`; debug-only mock URLs
on both platforms; no new ATS exception. Accepted for a LAN MOCK server: `/internal/metrics/usage` and MOCK debug routes (plan simulation,
practice scenarios) are open in MOCK, so anyone on the LAN can read counters or change sample state; the MOCK authenticator trusts any
token's claims (never used in REAL). Company logos are image URLs on FMP's public image host, loaded by the apps, not API calls.

## 11. Known limitations and remaining manual tests

- Android app end-to-end (emulator and phone): set the property, choose Mock Data, open Markets/Details/Screener/Practice; sign-in uses Firebase
  (real project) — check the app's sign-in policy for MOCK before testing signed-in flows.
- Physical iPhone: Local Network permission, then the same flows.
- Firestore-backed and push flows are not covered by MOCK.
- The Ubuntu host has the 2.59 GB BuildKit cache and the Phase 5A verification image/directory (owner decision pending); `docker` group
  membership for the SSH user remains (root-equivalent).
- Cloud Run staging (Phase 5B proper) remains **deferred and unverified**; the Phase 5A conditions (owner inputs, image vulnerability scan,
  scheduler OIDC question) still apply.

## 12. Rollback

Server: `deploy/local/deploy.sh rollback <tag>`; full removal per the guide §9 (only `stocksteps-local` resources). Repository: the changes are
additive — delete `deploy/local/` and the two new docs, remove the `#include?` line from `Config.xcconfig` and the two `.gitignore` lines.

## 13. Verdict

**PASS WITH CONDITIONS.** All completion criteria hold except full mobile end-to-end on the Android app and on physical devices, which were not
run (the iOS app itself was verified against the LAN backend in the Simulator; Android connectivity was verified at network level from the
emulator and the debug build's configuration). Conditions: run the Android app and physical-device checks in `LOCAL_DEVELOPMENT_SERVER.md`
§5–§6.

Recommended next phase: complete the manual device checks, then resume Cloud Run staging (Phase 5B proper) once the Phase 5A owner inputs are
available — starting with an image vulnerability scan and the checklist §1 values.
