# Ubuntu MOCK backend — end-to-end verification (2026-10-13)

Scope: the LAN MOCK backend on the Ubuntu host (Compose project `stocksteps-local`), the repository at `5c3f15f` "Add Portfolio Phases D2 and D3…"
(working tree also held uncommitted Portfolio edits from a parallel session, including `core/`), the Android emulator and the iOS build config.
All host access was read-only, except for two things: the smoke test and the route sweep wrote sample data for test users into the MOCK server's in-memory state.
No configuration, container, firewall or other service was changed, and nothing was exposed publicly. The host LAN address is written as `<lan-ip>`.

> **Headline: the backend has not been redeployed.** The host still runs image `stocksteps-local:a50a2e3cc833`
> ("Fix Phase 5C mobile integration bugs…", 2026-10-09). That is 35 commits behind `main`, and it does not include the Phase B sample company catalog (`231a0a1`).
> The results below describe **that running image**. §4 shows the same checks on current code run locally.

## 1. Results

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Container running and healthy | **PASS** | `stocksteps-local-api-1` Up 4 days (healthy), restarts 0, user 10001:10001, read-only rootfs, privileged=false, CapDrop=[ALL], 1 GiB / 1 CPU, restart `unless-stopped`, json-file 10m×3 |
| 1a | Deployed version is current | **FAIL** | `STOCKSTEPS_IMAGE_TAG=a50a2e3cc833`; `main` is `5c3f15f` |
| 2 | Health endpoints | **PASS** | `/health` 200 "StockSteps API is running"; `/health/live` 200; `/health/ready` 200; `/api/v1/meta` 200 `{"dataMode":"mock"}` |
| 3 | Routes with valid and invalid requests | **PASS (with 1 FAIL, see 3b)** | Smoke 20/23; sweep of 49 requests, all with the expected status (§2) |
| 3b | Practice trade on the long-running host | **FAIL** | `execute without review` 409 (want 400); `order execute` 409 `QUOTE_STALE` (want 200); `holding recorded` failed. Root cause in §3.1 |
| 4 | Market data comes from the MOCK fixture source | **PASS** | `dataMode: mock`; container env has only `STOCKSTEPS_DATA_MODE`, `LOG_FORMAT`, `STOCKSTEPS_MOCK_ALERT_SECONDS` + Java vars (no provider keys); overview labelled sample, "Not live"; usage counters show only `statements`/`stocksteps` events. (The server's mock provider is `FixtureMarketDataSource`; there is no class called `MockMarketDataProvider`.) |
| 5 | Zero FMP / Finnhub / Gemini calls | **PASS** | `/internal/metrics/usage`: upstream total **0**; no `fmp`/`finnhub`/`gemini`/`boc` counters; 0 provider URLs in 5,843 log lines |
| 6a | Quotes | **PASS** | AAPL 200 (336.67), lowercase `msft` 200, `@@` 400 `INVALID_SYMBOL`, 26-char symbol 400 |
| 6b | Historical charts | **PASS** | NVDA 1D 200 (78 pts), 1W/1M/3M/1Y/5Y 200; `range=7Q` 400 `INVALID_RANGE` |
| 6c | Screener filters, sort, paging | **PASS (old universe)** | catalog 200; sector, P/E ≤ 20 (6 matches), dividend ≥ 2 % (9), market-cap sort and paging 200; min>max, unknown metric and malformed JSON → 400 `INVALID_SCREEN`. Universe = **23** (catalog of 160 not deployed) |
| 6d | Company financials | **PASS** | MSFT fundamentals annual and quarter 200; JPM valuation 200; AMD sparkline 200 |
| 6e | Comparisons | **PASS** | `compare?symbols=AAPL,MSFT,NVDA` 200; one symbol 400 `INVALID_COMPARISON`; performance 1Y 200 |
| 6f | Comparison AI (mock) | **PASS** | no token 401; free plan 403 `PLUS_REQUIRED`; after the MOCK debug plan, StockSteps+ 200 "Sample analysis (no AI service in mock mode)…" |
| 6g | Catalog companies (COST, V…) | **FAIL (not deployed)** | Host returns generic "COST Sample Company" $91.12 and "V Sample Company". Current code locally: "Costco Wholesale Corporation" $902.32 |
| 7a | Signed-in routes reject missing or garbage tokens | **PASS** | `/me/watchlists` no token 401, `Bearer garbage` 401; with a MOCK token 200; create watchlist 200 |
| 7b | Real Firebase sign-in → backend → user data | **NOT TESTED** | It needs a real sign-in (credentials) or the Auth emulator, which requires owner approval. MOCK user data is in memory only |
| 7c | Firebase token *verification* in MOCK | **FAIL (by design, LAN-only)** | An unsigned token shaped like a Firebase token, with `user_id` set to any value, was accepted (200). MOCK uses `MockUserAuthenticator`; REAL uses `FirebaseIdTokenAuthenticator`. Acceptable only while the server is LAN-only |
| 8a | Android emulator can reach Ubuntu | **PASS** | From the `emulator-5554` shell: `GET /api/v1/meta` → `HTTP/1.0 200`, `"dataMode": "mock"` |
| 8b | Android build config points to Ubuntu | **FAIL** | This Mac has no `stockstepsMockBackendUrl` property. The last debug `BuildConfig` (Oct 9) has `MOCK_BACKEND_URL = http://127.0.0.1:8081` and `BACKEND_URL = http://127.0.0.1:8080` (local, via `adb reverse`; the reverse list shows `tcp:8080`) |
| 8c | iOS build config points to Ubuntu | **PASS (config only)** | git-ignored `Local.xcconfig`: `STOCKSTEPS_MOCK_BACKEND_URL = http://<lan-ip>:8081`; the last Debug build's Info.plist (Oct 13 00:11) has the LAN URL. App not launched (no booted simulator) |
| 8d | No private IP in tracked source | **PASS** | `git grep <lan-ip>` → 0 files |
| 9 | Automated tests (run fresh with `--rerun`) | **PASS** | core JVM 474/0; core iOS 473/0; server 492 run / 0 failed / 3 skipped (Firestore emulator); shared Android 210/0; shared iOS 206/0 |
| 9b | Integration: smoke against the host | **FAIL (3/23)** | 20 passed, 3 failed (3b) |
| 9c | Integration: smoke against current code (fresh local server :8082) | **PASS** | 23 passed, 0 failed; screener 152 US of 160; 0 WARN/ERROR in its log |
| 10 | CPU, memory, logs, Docker health | **PASS** | StockSteps: CPU 0.22 %, 179 MiB / 1 GiB, 19 PIDs; logs: 0 WARN/ERROR, 0 secret-like lines; host: load 0.16, 13 GiB RAM available, disk 23 % used (73 GB free) |
| 11 | Immich and Nextcloud running normally | **PASS** | All 8 containers running. Immich ×4 healthy. `immich /api/server/ping` 200. Nextcloud `status.php` 200 (installed, not in maintenance, no DB upgrade). Start times (Oct 9 00:42 / 01:23 UTC) are older than the StockSteps container. `immich_server` RestartCount 3 dates from before its current start, which is itself before StockSteps started |
| 12 | Native Android/iOS app end-to-end on Ubuntu | **NOT TESTED** | UI not driven (project rule); no physical devices |

## 2. Route sweep against the host (status · time)

`/health` 200 · `/markets/overview` 200 (US status `WEEKEND`) · `/market/snapshot` 200 · `/market/gainers` 200 · `/market/losers` 200 ·
`/stocks/AAPL/quote` 200 · `/stocks/COST/quote` 200 (generic sample) · `/stocks/V/profile` 200 (generic sample) · `/stocks/msft/quote` 200 ·
`/stocks/%40%40/quote` 400 · 26-char symbol 400 · `/stocks/ZZZZQ/details` **200** (sample company; see §3.3) · chart 1D–5Y 200 · `range=7Q` 400 ·
fundamentals annual/quarter 200 · valuation 200 · sparkline 200 · company news 200 · market news 200 · movement 200 · watch-data 200 ·
screener catalog/US/sector/P/E/dividend/paging 200 · screener bad range/metric/JSON 400 · compare (3) 200 · compare (1) 400 ·
compare performance 200 · earnings calendar 200 · brief 200 · `/me/watchlists` no token 401 / bad token 401 / MOCK token 200 / create 200 ·
AI summary no token 401 / free 403 / StockSteps+ 200 · AI usage 200 · unknown route 404. All responses were 15–110 ms.

## 3. Failures — root causes and fixes

### 3.1 Practice trades blocked after ~16 h of uptime (`409 QUOTE_STALE`) — real bug in long-running MOCK

- **Cause.** The MOCK market clock starts at the fixture capture time (2026-10-07 21:15Z) and then **advances in real time**
  (`mockDataSources()`, `Clock.offset`). Captured quotes keep their capture timestamps.
  - `AlertRules.freshQuote` rejects a quote dated before the latest session.
  - Once the mock clock reaches the next session's open (≈16 h after the container starts), every captured ticker (AAPL, MSFT…) counts as stale.
  - Practice orders then return 409, and price alerts on those tickers stop evaluating.
  - The host's mock clock now reads ≈ 2026-10-11 22:34Z (AI usage `asOf`), and the overview shows `WEEKEND`.
- **Evidence that it is clock drift, not a code regression.** The same smoke test passes 23/23 on a freshly started server built from current code.
- **Proposed fixes** (need approval; the first changes the MOCK clock semantics in `CLAUDE.md`):
  1. Preferred: loop the MOCK clock inside the captured trading day. Use `pinned + ((now − start) mod 24 h)`, or reset it to the capture instant each mock day, so captured quotes never go stale.
  2. Or re-stamp captured quote timestamps by the same offset as the clock (the closer analogue to "fresh data").
  3. Stopgap with no code change: `deploy.sh restart`, which also clears MOCK in-memory user state. It only buys about 16 h.

### 3.2 Host not redeployed — catalog and 35 commits missing

- **Cause.** `deploy/local/deploy.sh deploy` has not been run since 2026-10-09 (owner approval pending in `CLAUDE.md` "Still open").
- **Fix (needs approval).**
  - Run `deploy/local/deploy.sh deploy`, then `deploy.sh smoke` and `deploy.sh reliability`.
  - Rollback: `deploy.sh rollback a50a2e3cc833`.
  - The tag will carry `-dirty-<hash>` while `core/` has uncommitted Portfolio edits.
  - A redeploy also resets the mock clock, which clears 3.1 for about 16 h.

### 3.3 Unknown symbols return a sample company (200), not 404

- **Cause.** MOCK `sampleFallback` makes up a deterministic "<SYMBOL> Sample Company" for any valid-looking ticker. This is longstanding behaviour, labelled sample.
- **Proposed fix (optional).** Limit made-up companies to known tickers (fixtures, search list, catalog, snapshot movers, earnings fixtures), so `ZZZZQ` → 404 as in REAL. A test sweep is needed first, because many server tests rely on arbitrary sample tickers.

### 3.4 Android debug build targets the local mock, not Ubuntu

- **Cause.** No `stockstepsMockBackendUrl` property is set on this Mac. Debug falls back to `127.0.0.1:8081` via `adb reverse`.
- **Fix (per machine, no source change).** Add `stockstepsMockBackendUrl=http://<lan-ip>:8081` to `~/.gradle/gradle.properties`, or pass `-P…` on the build. Then rebuild debug and choose Settings → Development → Mock Data.

### 3.5 MOCK trusts unsigned tokens

By design: `MockUserAuthenticator` reads the uid without checking the signature. That is acceptable only while the server stays LAN-only.
**Fix before any remote access:** use `FirebaseIdTokenAuthenticator` in MOCK as well (project ID only, no key; it fetches Google's public certs). Also gate the open MOCK debug routes: `/internal/metrics/usage`, the plan simulation and the practice scenarios.

## 4. Not tested, and why

- Real Firebase sign-in end-to-end. It needs credentials or the Auth emulator, which needs owner approval.
- Android/iOS app UI flows against Ubuntu. Driving the emulator UI isn't allowed without a request, no iOS simulator was booted, and there are no physical devices.
- Host reboot and Docker daemon restart. Both are disruptive to other services.

---

## 5. Stabilization Phase 1 — fixes and validation (2026-10-13, uncommitted, not deployed)

### 5.1 Fix: MOCK quotes follow the MOCK market clock (§3.1)

- **Why not loop or freeze the clock.** The same `marketClock` also stamps user data: watchlists, alerts and cooldowns, AI quotas, saved screens, reminders and briefs. It must keep moving forward. Looping it would send those timestamps backwards; freezing it would stop cooldowns and daily resets.
- **Change.** `FixtureMarketDataSource` takes an optional `marketNow`. `mockDataSources()` builds the market clock first and passes `marketClock::instant`:
  - While the simulated session is OPEN, a quote's timestamp is the current mock time.
  - Otherwise it is the close of the latest session that has started (13:00 ET on early-close days).
  - Captured quotes only move forward and never earlier than the capture. Generated quotes take the session time, so their charts end on the simulated session date.
  - Prices are unchanged and deterministic.
  - A quote with no timestamp keeps none, so missing data is not made fresh.
  - Without `marketNow` (every other test and REAL), behaviour is unchanged.
- **Unchanged:** the freshness rule (`AlertRules.freshQuote`), the practice rules, REAL mode, and the MOCK `stale-quote` scenario, which still returns `QUOTE_STALE`.
- **Files:** `server/.../repositoryImpl/fixture/FixtureMarketDataSource.kt`, `server/.../Application.kt` (`mockDataSources`), new `server/src/test/.../market/MockClockFreshnessTest.kt`.

### 5.2 Results

| Check | Result | Evidence |
|---|---|---|
| Regression reproduced (old behaviour) | **PASS** | Without the fix, AAPL at capture + 24 h → `freshQuote` = null (the host's 409) |
| Quote freshness at start, next open, 24 h, weekend, 7 d, 30 d, Thanksgiving, Black Friday early close | **PASS** | AAPL, MSFT, KO, COST: fresh, deterministic price, never stamped in the future; equal to "now" while open; Friday 16:00 ET on the weekend; 13:00 ET after the early close |
| Practice trades at all those moments | **PASS** | preview has no blocker; execute AAPL and COST; 2 holdings each time |
| `stale-quote` scenario still blocks | **PASS** | `QUOTE_STALE` at +7 d |
| Price alerts at all those moments | **PASS** | `AlertEvaluator.run()`: stale 0, triggered 1 each time |
| MOCK server wiring | **PASS** | `mockDataSources()` AAPL and COST quotes fresh against its own clock |
| `MockClockFreshnessTest` | **PASS** | 7/7 |
| Full server suite (`--rerun`) | **PASS** | 499 run, 0 failed, 3 skipped (Firestore emulator) |
| End-to-end: local MOCK server, smoke suite at simulated clock 10-07 21:15Z (start), 10-08 21:15Z (+24 h), 10-09 15:00Z (OPEN), 10-14 21:15Z (+7 d), 11-06 21:15Z (+30 d), 11-26 17:00Z (HOLIDAY) | **PASS** | 23/23 at each; earnings calendar 7 items at each; 0 WARN/ERROR in server logs |
| core / shared / iOS suites after this change | **NOT RE-RUN** | No core or app code changed in Phase 1; last fresh run today: core JVM 474/0, core iOS 473/0, shared Android 210/0, shared iOS 206/0 |

### 5.3 Other issues found in the review

| Issue | Blocking? | Recommendation |
|---|---|---|
| The working tree's `core/` has the parallel Portfolio session's uncommitted edits; `deploy.sh` packages the working tree | **Blocks a clean deploy** | Commit the Phase 1 fix, then deploy from a clean `git worktree` of that commit (copy the git-ignored `deploy/local/server.env` into it). Tag = commit SHA; the parallel work is untouched |
| Parallel Gradle runs share `build/` dirs (one `EOFException` during this session) | No (local only) | Don't run test suites at the same moment as the Portfolio session |
| Mac disk 96 % used (18 GiB free) | Risk | Image builds run on the Ubuntu host (73 GB free); keep local `DerivedData` in check |
| Unknown symbols return a sample company (200) (§3.3) | No | Optional follow-up |
| API contract changes since the deployed image | None | Only a computed property and wording in `core` (no serialized fields) |

## 6. Security plan before any remote access (not implemented; the server stays LAN-only)

1. **Verify Firebase ID tokens in MOCK.** Add `STOCKSTEPS_MOCK_AUTH=firebase|unsigned` (default `unsigned`, LAN only). With `firebase`, `mockDataSources()` uses the existing `FirebaseIdTokenAuthenticator(FIREBASE_PROJECT_ID)`:
   - signature, issuer, audience and expiry are checked;
   - no service-account key is needed, only an outbound fetch of Google's public certificates.
   - The test-only `mock-user:<uid>` form is accepted only when `STOCKSTEPS_MOCK_TEST_TOKENS=true`, so the smoke test keeps working on the LAN.
   - Startup refuses `unsigned` when `STOCKSTEPS_PUBLIC=true`.
2. **Gate the debug and internal routes.**
   - Routes: `/internal/metrics/usage`, the plan simulation (`/api/v1/me/entitlements/debug`), the practice/portfolio/home scenario routes and the `/internal/*` dispatch routes.
   - Today these are open in MOCK. Require the existing `InternalJob` secrets (header) for `/internal/*`.
   - Disable the debug/scenario routes unless `STOCKSTEPS_MOCK_DEBUG_ROUTES=true` (default true on the LAN, false when public).
3. **Transport.** HTTPS through a tunnel or reverse proxy bound to `127.0.0.1`. Keep the existing admission limits, with `TRUSTED_PROXY_HOPS` set for the proxy and App Check in monitor mode, then enforce it.
4. **Tests.** Unsigned token → 401; expired or wrong-audience token → 401; debug routes 404 when disabled; `/internal/*` 401 without the secret.
