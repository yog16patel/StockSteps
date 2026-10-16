# StockSteps — REAL backend My Portfolio & Practice (LAN Ubuntu host, physical Android)

Status: **committed and pushed as "Fix REAL My Portfolio and Practice on the LAN backend with Firestore user data, unwrapped transaction rejections, 503 for storage failures and an opt-in read-only service-account mount"; DEPLOYED to the LAN REAL stack 2026-10-16 with Firestore user data enabled**
(owner-created key and IAM grant, owner-approved redeploy; §10). My Portfolio and Practice load on REAL on the Pixel 7. Date: 2026-10-16. Base: `ff5662e` (UI/UX Refinement Phase 2, KAN-16). Review package:
`docs/reviews/real_portfolio_practice/`. Follows the diagnosis in `docs/DEBUG_API_LOGGING.md` §9.

## 1. Summary

| | |
|---|---|
| Confirmed root cause | The LAN REAL container (`stocksteps-real-api-1`, `<lan>:8082`) has **no Google credentials**: no `GOOGLE_APPLICATION_CREDENTIALS`, no mounts, no ADC. `FirestoreOptions…service` fails at startup, so `Application.kt` falls back to `UnavailableUserDataStore`, which answers every `/api/v1/me/*` call with 503 `USER_DATA_UNAVAILABLE`. This is decision D-R1 of `docs/LOCAL_REAL_BACKEND_PLAN.md`, not a code regression. |
| Not the cause | Firebase sign-in: the ID token is accepted (no 401, no refresh retry); the apps and server agree on the Firebase project id. Networking: `/api/v1/meta` 200 `real` from the phone. Client parsing and error mapping: correct (`StockStepsApiException(503)` → SERVER, not SESSION). |
| Code fixed (server) | 1. Firestore transaction rejections were wrapped in `ExecutionException` and would have turned into **500** as soon as credentials existed (Portfolio `400 INVALID_PORTFOLIO`, Practice `400 INSUFFICIENT_CASH`, `409 PLUS_ACTIVE`…). 2. Firestore failures (PERMISSION_DENIED, NOT_FOUND, UNAUTHENTICATED, timeouts) were unmapped 500s; now 503 `USER_DATA_UNAVAILABLE` with a type/status-only log line. 3. Daily Brief personalized answered 500 instead of 503. 4. The 503 copy said "Watchlists and alerts…" on Portfolio and Practice. 5. The startup warning now names the exception type. |
| Config prepared (inert) | `deploy/local/docker-compose.real.userdata.yml` + `deploy.sh` opt-in (`STOCKSTEPS_REAL_USER_DATA=1`): mounts an owner-placed key read-only, sets the project ids, checks owner/mode by `stat` only. Not enabled, not deployed. |
| Remaining | Owner: create a least-privilege key, place it on the host, enable the opt-in, approve a redeploy; then the signed-in device pass (§7). |

## 2. Environment inspected (2026-10-16, read-only)

- `git status`: another session's uncommitted Company Details work (core `companydetail/`, shared `companydetails/`, iOS; still changing during
  this session); untouched. HEAD `ff5662e`.
- Host `<lan>` over SSH (key login works). Containers: `stocksteps-local-api-1` (**MOCK**, `:8081`, image `fa7c563c94a9`) and
  `stocksteps-real-api-1` (**REAL**, `:8082`, image `d85eb3e8fb04`, healthy, up 8 h), plus unrelated Nextcloud/Immich (not touched).
  **Correction to the task brief:** `http://<lan>:8081` is the MOCK backend; REAL is `:8082` (`docs/LOCAL_REAL_BACKEND_PLAN.md` §2).
- Inside the REAL container (`docker exec … env | cut -d= -f1`, names only): `STOCKSTEPS_DATA_MODE`, provider keys and rates, no
  `GOOGLE_APPLICATION_CREDENTIALS`, no `FIREBASE_PROJECT_ID` / `GOOGLE_CLOUD_PROJECT` (server defaults to `stocksteps`). `docker inspect`
  mounts: none. Host directory `~/stocksteps-real`: `docker-compose.yml`, `.env`, `releases/`, `secrets.env` (600); no key file.
- Container log: `User data storage unavailable (Google credentials not found); watchlists and alerts are disabled.` at each start, and
  `Unhandled request failure: UserDataException` (the Daily Brief 500s) through the afternoon.
- Firebase project: `app/androidApp/google-services.json` `project_id` = `stocksteps`; iOS `GoogleService-Info.plist` the same project;
  server `AppConfig.firebaseProjectId` default `stocksteps`. No mismatch. **Only one Firebase project exists in the configuration**: a key
  for it reaches the same Firestore any later deployment of that project would use (owner decision, §6).
- Devices: Pixel 7 `28121FDH2001YN` (USB, also listed over adb-TLS), emulator-5554, emulator-5556. The Pixel 7 runs a debug build installed
  16:15 (after the logging commit), stored environment REAL (`real=<lan>:8082`, `mock=<lan>:8081`), signed in (the owner's session; Claude
  did not sign in or read the account).

## 3. Before: physical Pixel 7 on REAL

Read-only GETs only (force-stop + launch, open Portfolio, open Practice). Evidence: `captures/pixel7-real-before.log`,
`captures/pixel7-before-create-portfolio.png` (the owner's earlier Create portfolio attempt: "Couldn't update account · Watchlists and
alerts are temporarily unavailable"), `captures/pixel7-before-practice.png`.

| Correlation id | Request | Status | Body (redacted log) |
|---|---|---|---|
| `71fj-0002` | `GET /api/v1/meta` | 200 | `{"dataMode":"real"}` |
| `71fj-0003` | `GET /api/v1/daily-brief/latest` | 200 | live brief |
| `71fj-0001` | `GET /api/v1/me/entitlements` (Authorization `[REDACTED]`) | 503 | `USER_DATA_UNAVAILABLE` "Watchlists and alerts are temporarily unavailable." |
| `71fj-0005` | `GET /api/v1/me/portfolio` | **503** | same |
| `71fj-0010` | `GET /api/v1/me/practice` | **503** | same |
| `71fj-0004/6/7/8` | `GET /me/alerts`, `/me/learning`, `/me/watchlists`, `/me/screens` | 503 | same |
| `71fj-0009` | `GET /me/daily-brief/[REDACTED]/personalized` | **500** | `INTERNAL_ERROR` |

No 401 anywhere: the token is attached and verified. No Portfolio create (`PUT /me/portfolio/accounts`) was sent by Claude.

## 4. Code changes (server only; no app, UI or navigation change)

| File | Change |
|---|---|
| `server/.../userdata/FirestoreUserDataStore.kt` | `await()` goes through new `awaitFirestore`: unwraps `ExecutionException` and rethrows a transaction function's own exception unchanged (same behaviour as `InMemoryUserDataStore`); gRPC/Firestore/gax/IO failures and timeouts become `UserDataException(503, USER_DATA_UNAVAILABLE)`; logs `type=… status=…` only (messages can name documents/projects). |
| `server/.../userdata/UserDataStore.kt` | `USER_DATA_UNAVAILABLE_MESSAGE` = "Your saved data is temporarily unavailable. Try again later." (used by `UnavailableUserDataStore` and the Firestore mapping; it names no single feature because the apps show it verbatim on every signed-in screen). |
| `server/.../ApiErrorHandling.kt` | StatusPages maps `UserDataException` to its own status and code (safety net; fixes Daily Brief personalized 500 → 503). |
| `server/.../Application.kt` | Startup warning names the exception type and that signed-in data answers 503. |
| `server/src/test/.../userdata/FirestoreFailureMappingTest.kt` (new) | 5 tests, §8. |
| `deploy/local/docker-compose.real.userdata.yml` (new), `deploy/local/deploy.sh`, `deploy/local/server.env.example` | Opt-in user-data overlay (§6). Default behaviour unchanged: without `STOCKSTEPS_REAL_USER_DATA=1` nothing new is uploaded and the host `.env` is identical. |
| `.gitignore` | `*service-account*.json`, `*serviceAccount*.json`, `*credentials*.json` never tracked. |

Unchanged by design: API contracts and DTOs, token verification, MOCK (in-memory store), `UnavailableUserDataStore` as the REAL fallback
(no silent in-memory fallback), the apps' error handling.

## 5. Why the transaction fix matters for "Create portfolio"

`PortfolioService.mutate` turns `IllegalArgumentException` from `PortfolioEngine` into `400 INVALID_PORTFOLIO`, and
`PracticeService.execute` throws `UserDataException` (cash, holding limit, plan) **inside** the store transaction. The in-memory store
(MOCK, all existing tests) runs the block directly, so they worked. Firestore's `runTransaction` reports the block's exception through
`ApiFuture.get()` as `ExecutionException(cause)`, which neither catch matched → StatusPages → **500 INTERNAL_ERROR**. Mounting
credentials alone would therefore have made REAL Portfolio and Practice load, but every rejected entry or order would have shown "An
unexpected error occurred." The controlled before/after run (§8) shows exactly that.

## 6. Owner instructions to enable REAL user data (BLOCKED here: needs credentials and IAM)

Decision first (owner): the only configured Firebase project is `stocksteps` (both apps and the server). A key for it gives the LAN
server read/write access to that project's Firestore. Recommended: use it only while `stocksteps` holds development data, or create a
separate dev Firebase project (then new `google-services.json` / `GoogleService-Info.plist` too).

1. **Service account** (Google Cloud console → IAM → Service accounts, project `stocksteps`): create `stocksteps-lan-dev` and grant only
   **`roles/datastore.user`** (Firestore read/write). Not `roles/editor`, `roles/owner`, `roles/firebase.admin`, and not FCM unless push
   is tested (`roles/firebasecloudmessaging.admin`, `docs/CLOUD_RUN_SECRETS.md` §3). Token verification needs no credentials.
2. Make sure Firestore exists in Native mode with the `(default)` database (the server's default `NEWS_FIRESTORE_DATABASE_ID`).
3. Create one JSON key and copy it **directly** to the host (never into the repository or this Mac's project folder):
   `scp key.json <user>@<lan>:~/stocksteps-real/service-account.json`, then on the host
   `sudo chown 10001:10001 ~/stocksteps-real/service-account.json && sudo chmod 400 ~/stocksteps-real/service-account.json`
   (10001 is the container user; the directory stays 700). Delete the local download.
4. In `deploy/local/server.env` (git-ignored, no secrets): `STOCKSTEPS_REAL_USER_DATA=1` and
   `STOCKSTEPS_REAL_FIREBASE_PROJECT_ID=stocksteps`.
5. After approving a redeploy: `STOCKSTEPS_STACK=real deploy/local/deploy.sh deploy` (builds the current tree, which includes §4; MOCK
   untouched). `deploy.sh` refuses unless the key file is `10001 400`.
6. Check: `STOCKSTEPS_STACK=real deploy/local/deploy.sh logs 50` no longer shows "User data storage unavailable"; a warning
   `User data storage request failed: type=… status=PERMISSION_DENIED|NOT_FOUND` means the IAM role or database is missing.
7. Rollback: remove the two lines from `server.env` and redeploy (the overlay file is removed from the host), then delete the key on the
   host and disable it in IAM.

Note: with credentials the news-simplification store also uses Firestore (`NEWS_STORE` default); without a Gemini key it only reads. REAL
has no background loops, so no pushes or alert evaluations start on their own.

## 7. Verification

| Check | Result | Evidence |
|---|---|---|
| Root cause on the running container (env names, mounts, log) | PASS | §2 (SSH, names only) |
| Pixel 7 REAL before: meta 200; portfolio/practice/all `/me/*` 503; personalized 500; no 401 | PASS (reproduced) | `captures/pixel7-real-before.log` |
| Server fix: transaction rejections keep 400/409; storage failures 503; no 500 | PASS | `FirestoreFailureMappingTest` 5/5; before-fix run 4/5 FAIL (§8) |
| Overlay merges and fails closed without a project id | PASS | `docker compose config` on the host in a temp dir (dummy empty files, removed) |
| `deploy.sh` syntax | PASS | `bash -n` |
| Fixed server deployed to `:8082` | NOT RUN | redeploy needs owner approval |
| REAL Portfolio create, summary, empty holdings, reload after restart (Pixel 7) | BLOCKED | no credentials on the host (§6); also needs an approved test account |
| REAL Practice initialize, balance, holdings, reload (Pixel 7) | BLOCKED | same |
| Guest rejection | PASS (test) | `401 SIGN_IN_REQUIRED` from both `:8081` and `:8082` (curl) and in tests |
| MOCK → REAL isolation | PASS (unchanged) | separate containers and stores; the Pixel's REAL requests all went to `:8082` (log); no MOCK data shown |
| Android cleartext / network config | PASS | requests reach `<lan>:8082` from the phone (log) |
| iOS REAL signed in | BLOCKED | iPhone 16 simulator is in guest mode; same server cause applies; no iOS code changed |
| Android/iOS builds | NOT RUN | no app code changed |

## 8. Tests

| Suite | Result |
|---|---|
| `:server:test` (full) | PASS — 507 tests, 0 failures, 0 errors, 3 skipped (pre-existing skips) |
| `FirestoreFailureMappingTest` after the fix | PASS 5/5 |
| Same tests with `awaitFirestore` temporarily reverted to plain `get()` | 4/5 FAIL as expected (Portfolio oversell 500, Practice insufficient cash 500, two unwrap/mapping unit tests); the unavailable-store test passes either way. Original code restored and re-run. |
| `:core:jvmTest` | PASS — 608 tests, 0 failures (includes the other session's uncommitted core files) |
| `:core:iosSimulatorArm64Test`, `:app:shared:*`, `assembleDebug`, `xcodebuild` | NOT RUN — no core, shared or app change |

## 9. Remaining blockers and follow-ups

1. Owner: §6 (project decision, key, IAM, opt-in) and approval to redeploy REAL with this tree.
2. Owner: an approved test account for the signed-in pass; then on the Pixel 7: create a portfolio, add nothing (empty holdings),
   force-stop and reopen, open Practice (initialization), `adb -s 28121FDH2001YN logcat -d -s StockSteps-API | grep -E "me/(portfolio|practice)"`
   → expect 200 `[METADATA]` bodies. No Practice orders without explicit permission.
3. iOS signed-in REAL pass needs the owner to sign in on the simulator.
4. Not changed (separate): the `SIGN_IN_REQUIRED` copy still says "Sign in to use watchlists and alerts." on every route; REAL
   `screener/catalog` 503 (`SCREENER_UNAVAILABLE`, warm-up off).

## 10. Deployment record (2026-10-16, owner-approved)

- **Owner did:** confirmed Firestore `(default)` Native in project `stocksteps`; created `stocksteps-lan-dev@stocksteps.iam.gserviceaccount.com`;
  placed its key on the host.
- **Claude checked, without reading the key:**
  - `server.env`: `STOCKSTEPS_REAL_USER_DATA=1`, `STOCKSTEPS_REAL_FIREBASE_PROJECT_ID=stocksteps` added once; other values kept; git-ignored.
  - Key file: `stat` → uid 10001, gid 10001, mode 400, regular file, not readable by the SSH user.
  - IAM (`gcloud projects get-iam-policy`, read-only): the account's only project role is `roles/datastore.user`.
  - `docker compose config` on the host (temp dir, the host's non-secret `.env` values, dummy empty secret/key files, removed):
    - valid; REAL publishes `<lan>:8082`, MOCK `.env` keeps 8081;
    - read-only bind at `/run/stocksteps/service-account.json`, `FIREBASE_PROJECT_ID`/`GOOGLE_CLOUD_PROJECT` = `stocksteps`;
    - FMP 250 / Finnhub 60 kept.
- **Deployed from a clean worktree of `ff5662e`**, with only this change's server and deploy files copied in. Another session's
  uncommitted core Company Details work stayed out of the image.
  - Command: `STOCKSTEPS_STACK=real deploy/local/deploy.sh deploy`.
  - Image `stocksteps-local:ff5662ecba3e-dirty-b20aa97d`; Gradle `BUILD SUCCESSFUL in 5m 46s`.
  - Only `stocksteps-real-api-1` was recreated.
  - The previous image `d85eb3e8fb04` stays on the host for `rollback`. The temporary worktree was removed.

| Check | Result | Evidence |
|---|---|---|
| REAL health live/ready/meta 200, `{"dataMode":"real"}`; healthy, 0 restarts, user 10001, read-only rootfs | PASS | `captures/deploy-real.log`, `docker inspect` |
| Key mounted read-only (`rw=false`; a write in the mount fails); readable by the container user | PASS | `docker inspect` mounts, `docker exec test -r` |
| Env names present: `GOOGLE_APPLICATION_CREDENTIALS`, `FIREBASE_PROJECT_ID=stocksteps`, `GOOGLE_CLOUD_PROJECT`, `FMP_API_KEY`, `FINNHUB_API_KEY`, provider rates | PASS | `docker exec env \| cut -d= -f1` |
| Firestore initialization: no "User data storage unavailable" at start; only the expected Gemini/proxy-hops warnings; 0 Firestore/`UserDataException`/PERMISSION_DENIED lines | PASS | `docker logs` |
| Pixel 7 REAL (owner's existing signed-in session; GETs only): meta, entitlements, learning, screens, **portfolio**, alerts, watchlists, **practice**, **practice/performance**, personalized brief → all **200**; 0 × 503 | PASS | `captures/pixel7-real-after.log` |
| My Portfolio: empty state, no error banner ("Start tracking what you own") | PASS | `captures/pixel7-after-my-portfolio.png` |
| Practice: initialized ($10,000.00 virtual cash, 0 of 3 holdings, "Your practice portfolio is ready") | PASS | `captures/pixel7-after-practice.png` |
| Reload after force-stop: `GET /me/portfolio` 200, no 503 | PASS | `captures/pixel7-real-reload.log` |
| MOCK `:8081` meta `mock`, ready 200, container untouched (up 24 h); Nextcloud and Immich untouched | PASS | `docker ps`, curl |
| Portfolio **create** on REAL (`PUT /me/portfolio/accounts`) and Practice orders | NOT RUN | writes to the account need explicit permission |
| Practice reload after restart | NOT RUN | the app reopened on My Portfolio; Practice loads only when opened |
| iOS REAL signed in | BLOCKED | simulator in guest mode |

Data written by these GETs, all server defaults:
- the Practice starter document (`users/{uid}/practice/account`, simulated $10,000);
- the default watchlist (`users/{uid}/watchlists`, created on first read);
- for that signed-in account in project `stocksteps`.

No personal Portfolio data existed on REAL before (the store was unavailable), and none was written: the portfolio ledger is only saved
when it changes.
