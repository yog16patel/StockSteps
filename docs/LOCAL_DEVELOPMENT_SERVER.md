# StockSteps — local development server (LAN MOCK backend)

A persistent **MOCK** StockSteps backend in Docker on a LAN Ubuntu host, so Android and iOS debug builds (emulators, simulators and phones on
the same network) share one stable sample-data server instead of `./gradlew :server:runMock` on each laptop. Phase 5B Local; the Google Cloud
Run staging deployment is a separate, still-deferred step (`CLOUD_RUN_DEPLOYMENT_CHECKLIST.md`).

**What it is not:** not REAL data (no provider keys exist in it), not a shared database (MOCK state is in memory and resets on every restart),
not reachable from the internet (published on the host's LAN address only), not a Cloud Run rehearsal (no `K_SERVICE`, no Firebase/Google
credentials).

## 1. Architecture

```
Mac (repo, Android Studio, Xcode) ──ssh/scp──▶ Ubuntu host: ~/stocksteps-local/
      │                                         ├─ docker-compose.yml   (copy of deploy/local/docker-compose.yml)
      │                                         ├─ .env                 (image tag, build context, bind address, port — no secrets)
      │                                         └─ releases/<tag>/      (uploaded build context, newest 5 kept)
      │
      └─ HTTP ─▶ <server-lan-ip>:8081 ──▶ container stocksteps-local-api-1 :8080 (network stocksteps-local)
Android emulator / phone / iOS Simulator / iPhone (same LAN) ─▶ same address
```

- Compose project `stocksteps-local`, one service `api`, image `stocksteps-local:<git-sha>[-dirty-<hash>]` built from the repository
  `Dockerfile` (Phase 5A) for `linux/amd64` on the host.
- Own bridge network `stocksteps-local` (not shared with any other project). It is a normal bridge, not `internal: true`, because Docker
  doesn't publish ports from internal networks; MOCK mode makes no outbound calls (verified: zero upstream counters).
- Connection settings live in `deploy/local/server.env` (git-ignored; template `server.env.example`): `STOCKSTEPS_SSH`,
  `STOCKSTEPS_BIND_ADDR` (the host's LAN IP), `STOCKSTEPS_HOST_PORT` (default **8081**, the apps' mock-backend port). The LAN address is kept
  out of the repository, which is public.

## 2. Container configuration (`deploy/local/docker-compose.yml`)

| Setting | Value | Why |
|---|---|---|
| Mode | `STOCKSTEPS_DATA_MODE=mock`, `LOG_FORMAT=text`, `STOCKSTEPS_MOCK_ALERT_SECONDS=60` | fixtures only; readable logs; sample alerts/reminders evaluated in-process (no push sent) |
| Publish | `${STOCKSTEPS_BIND_ADDR}:${STOCKSTEPS_HOST_PORT}:8080` | LAN address only — never `0.0.0.0`; Compose refuses to start without an explicit address |
| User | `10001:10001` | non-root (image default, pinned again) |
| Filesystem | `read_only: true`, `tmpfs /tmp` 64 MB | nothing writes outside `/tmp` (verified) |
| Privileges | `cap_drop: [ALL]`, `no-new-privileges`, not privileged, `pids_limit: 256` | least privilege |
| Resources | `cpus: 1.0`, `mem_limit: 1g` | same as the Cloud Run staging template; observed ≤ 134 MiB |
| Health | `curl -fsS http://127.0.0.1:8080/health/ready` every 30 s, 3 retries, 40 s start period | `curl` is already in the runtime image; the probe never calls providers |
| Restart | `unless-stopped`, `stop_grace_period: 10s` | comes back after crashes and host/daemon restarts, stays down after `stop` |
| Logs | `json-file`, 10 MB × 3 | bounded (≤ 30 MB) |
| Volumes | none | MOCK has no durable state |

## 3. Prerequisites

- Key-based SSH from the Mac to the host, and the SSH user in the host's `docker` group (or equivalent Docker access).
- Docker Engine with the Compose v2 plugin and `docker-buildx` on the host.
- `deploy/local/server.env` filled in (copy `server.env.example`).

## 4. Commands (run from the repository root on the Mac)

| Task | Command |
|---|---|
| Initial deployment / rebuild after code changes | `deploy/local/deploy.sh deploy` (uploads the allow-listed build context, builds `stocksteps-local:<tag>`, starts it, waits for healthy, prints health) |
| Start / stop / restart | `deploy/local/deploy.sh start` · `stop` · `restart` |
| Logs | `deploy/local/deploy.sh logs 200` |
| Health from the Mac | `deploy/local/deploy.sh health` |
| State, health, CPU/memory, tag | `deploy/local/deploy.sh status` |
| Releases and images | `deploy/local/deploy.sh releases` |
| Roll back to an earlier image | `deploy/local/deploy.sh rollback <tag>` (no rebuild) |
| Smoke test | `deploy/local/deploy.sh smoke` |
| Reliability/security check | `deploy/local/deploy.sh reliability` (restarts the container once — MOCK state is lost) |

Tags: the commit SHA (12 characters); if `core/`, `server/` or the build files have uncommitted changes, `-dirty-<content hash>` is appended,
so a dirty tree never reuses a clean tag. Builds reuse the host's BuildKit cache (a rebuild with unchanged sources takes seconds; a cold build
≈ 10 minutes). Old images are **not** deleted automatically; remove one explicitly with `ssh <host> docker image rm stocksteps-local:<tag>`
(only `stocksteps-local:*` images; never prune globally — the host runs other services).

## 5. Android (debug builds)

The app already separates the backends: `app/androidApp/build.gradle.kts` reads `stockstepsMockBackendUrl` into the **debug** `BuildConfig`
only (release hard-codes an empty mock URL and hides the Development setting); cleartext HTTP is allowed by the **debug** manifest only.
No source change is needed:

1. Put `stockstepsMockBackendUrl=http://<server-lan-ip>:8081` in `~/.gradle/gradle.properties` (per machine, not in git), or pass
   `-PstockstepsMockBackendUrl=http://<server-lan-ip>:8081` to Gradle.
2. Build/install the debug app; in the app choose **Settings → Development → Backend Data Source → Mock Data** (debug builds only).
3. Emulator: reaches LAN addresses through the Mac (verified from the emulator shell). Phone: must be on the same Wi-Fi/LAN.
   `adb reverse` is not needed for the LAN server (it is only for a mock server on the Mac itself).

## 6. iOS (debug builds)

The mock URL comes from `STOCKSTEPS_MOCK_BACKEND_URL` and is honoured only in DEBUG builds (`BackendSettings.mockURL`). Override it per machine
without touching tracked files:

1. Copy `app/iosApp/Configuration/Local.xcconfig.example` to `Local.xcconfig` (git-ignored; included by `Config.xcconfig` with `#include?`)
   and set `STOCKSTEPS_MOCK_BACKEND_URL = http:/$()/<server-lan-ip>:8081`.
2. Build the Debug configuration; choose **Mock Data** under Settings → Development → Backend Data Source. The "Sample data · mock backend" banner appears only after
   `/api/v1/meta` on that server answered `mock`.
3. App Transport Security: plain HTTP to the raw LAN IP worked in the iOS 18.3 Simulator with the existing `NSAllowsLocalNetworking`
   (verified). On a physical iPhone, iOS asks for **Local Network** permission the first time; allow it. If a device still refuses
   cleartext to the IP, the fallback is a `.local` hostname (enable mDNS/avahi on the host and use `http://<host>.local:8081`), which
   `NSAllowsLocalNetworking` covers; no ATS exception is added to the shared Info.plist.

## 7. Signed-in features against MOCK

The MOCK backend accepts Firebase ID tokens without verifying them, and the test-only bearer form `mock-user:<uid>` (used by the smoke test;
REAL never accepts it). Data written by signed-in features (watchlists, practice ledger, research sessions, AI usage) lives in memory and is
lost when the container restarts or is redeployed. Debug plan records (StockSteps+ simulation) only work in MOCK.

## 8. Troubleshooting

- `unreachable` from the Mac: `deploy.sh status`; check the host firewall (UFW was active and published Docker ports were reachable without
  changes); check the Mac's macOS Local Network permission for the app making the request (Terminal, Android emulator, Xcode).
- `unhealthy`: `deploy.sh logs`; configuration errors appear at startup and stop the process.
- Port conflict on deploy: choose another `STOCKSTEPS_HOST_PORT` after checking `ss -ltn` on the host, and update the app URLs.

## 9. Removal

`deploy.sh stop`, then on the host: `cd ~/stocksteps-local && docker compose down` (removes the container and the `stocksteps-local`
network), `docker image rm stocksteps-local:<tag>` for each tag, and `rm -rf ~/stocksteps-local`. Nothing else on the host is involved.
