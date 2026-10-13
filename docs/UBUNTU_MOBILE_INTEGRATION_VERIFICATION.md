# StockSteps — Android and iOS against the Ubuntu MOCK backend (Phase 3B)

Date: 2026-10-13, 21:56–22:40 UTC.
- **Apps:** built from a clean `git worktree` of `fa7c563` "Keep MOCK quotes current with the advancing MOCK market clock so practice trades and price
  alerts keep working on a long-running Ubuntu backend (Ubuntu backend stabilization Phase 1)". That was `main` HEAD. No Portfolio Phase E
  working-tree changes are included.
- **Backend:** the LAN Ubuntu host, image `stocksteps-local:fa7c563c94a9`, `STOCKSTEPS_DATA_MODE=mock`, LAN IP port 8081 only. It was not restarted or
  redeployed in this phase.
- **Excluded:** the host LAN address. It lives in git-ignored `deploy/local/server.env` and `app/iosApp/Configuration/Local.xcconfig`.
- **Screenshots:** `docs/design/ubuntu_mobile/` (`android-*.jpg`, `ios-*.jpg`).

**Verdict: PASS WITH CONDITIONS for continued LAN-based mobile testing.**
- Both apps reach the Ubuntu backend and render its MOCK data on every signed-out journey tested.
- **Not tested:**
  - signed-in journeys (watchlist sync, Portfolio, Practice trades, AI comparison). They need a real Firebase sign-in, and I may not enter credentials;
  - physical devices (none connected);
  - the long-uptime freshness check on the host (not yet due).

## 1. Isolation (what was and wasn't touched)

| Item | Action |
|---|---|
| Git | Read-only; no commit, push or stash. Portfolio Phase E files untouched. Apps built in the scratch worktree `mobile-fa7c563` (0 local changes) |
| Machine-wide Gradle setting | `~/.gradle/gradle.properties` (created in the previous phase, contained only `stockstepsMockBackendUrl`) **removed**, restoring the original state (no file). The Android URL was passed per build with `-PstockstepsMockBackendUrl=http://<lan-ip>:8081` |
| Android | Phase E's emulator `emulator-5554` (Pixel_9_Pro) was **not used and nothing was installed on it**. Testing used a second AVD, `Pixel_3a_API_34`, as `emulator-5556` (Android 14) |
| iOS | Phase E's iPhone 16 simulator (cached session) was **not used**. Testing used iPhone 16 Pro (`A863FEC2…`), a fresh install, with a separate DerivedData (`scratchpad/dd-mobile`) |
| Gradle | Worktree builds use their own `build/` folders; no test tasks were run, so there was no clash with the Phase E checkout |
| Backend | Not restarted or redeployed; no configuration, firewall or networking change |

## 2. Part A: Ubuntu connectivity (from the Mac)

| Check | Result | Evidence |
|---|---|---|
| Container | **PASS** | `stocksteps-local-api-1` healthy, image tag `fa7c563c94a9`, restarts 1 (the Phase 2 reliability crash test), started 21:30:36Z |
| `/health/live`, `/health/ready`, `/api/v1/meta` | **PASS** | 200 `{"status":"alive"}`, 200 `{"status":"ready"}`, 200 `{"dataMode":"mock"}` |
| Quotes | **PASS** | `/stocks/AAPL/quote` 200 (336.67), `/stocks/COST/quote` 200 (Costco Wholesale Corporation) |
| Screener | **PASS** | US filter: 152 of 160, `sampleData: true` |
| Container uptime | — | ≈ 26 min at the start of this phase; ≈ 70 min at the end |
| Long-uptime freshness smoke | **NOT TESTED (not due)** | Needs ≥ 16 h 15 min of uptime (mock clock past the next simulated open): run `deploy/local/deploy.sh smoke` after ≈ 13:50 UTC 2026-10-14, without a restart |

## 3. Android

### 3.1 Build and configuration

| Check | Result | Evidence |
|---|---|---|
| Debug build (`:app:androidApp:assembleDebug`, worktree, `-PstockstepsMockBackendUrl=…`) | **PASS** | `BuildConfig.MOCK_BACKEND_URL` = Ubuntu LAN :8081; `BACKEND_URL` = `http://127.0.0.1:8080` (unchanged default); release config untouched |
| Install on `emulator-5556` (fresh) | **PASS** | `adb install` Success |
| Default environment on a fresh install | **PASS (expected)** | REAL (`127.0.0.1:8080`, nothing listening): Home shows "The Daily Market Brief couldn't be loaded…", with no local fallback data (`android-01`) |
| Settings → Development → Mock Data | **PASS** | Stored `stocksteps.backendEnvironment = MOCK`; the "Sample data · mock backend" banner appears at once, which requires `/api/v1/meta` = mock from the server (`android-02`) |
| Traffic reaches Ubuntu | **PASS** | The emulator's socket table shows the StockSteps app (uid 10192) with an ESTABLISHED TCP connection to `<ubuntu-lan-ip>:8081` and none to loopback. The container's TCP table shows an ESTABLISHED connection from this Mac at the same time. No mock server runs on this Mac (ports 8080 and 8081 free) |

### 3.2 Emulator journeys (signed out, "Continue as guest")

| # | Journey | Result | Evidence |
|---|---|---|---|
| 1 | App launch | **PASS** | Sign-in screen, then guest Home; 0 crashes (`logcat -b crash` empty), no ANR |
| 2 | Mock Data selected | **PASS** | See 3.1 |
| 3 | Home shows Ubuntu data | **PASS** | "Markets today" Sample badge, US after-hours, S&P 500 7,772.36 / Nasdaq 26,418.52 / TSX 30,544.18 "Close · Oct 7", Daily Brief "Oct 7" (`android-03`) |
| 4 | Markets | **PASS** | US Market "After-hours · Next open Thu, Oct 8 · 9:30 AM ET · Sample quotes · Not live", S&P/Nasdaq/Dow "As of 4:00 PM ET · Oct 7", brief, Top Movers (Gainers / Most Active), 11 sector rows, research tools (`android-04`, `-05`) |
| 5 | Stock search | **PASS** | "costco" → Costco Wholesale Corporation (COST · NASDAQ · USD) (`android-11`); "PEP" → PepsiCo; nonsense "qqxyzz" → "No matches found." (`android-13`) |
| 6 | Company details and charts | **PASS** | META: profile chips, price, actions; charts 1D (from 738.88), 1W, 1M, 3M, 1Y, 5Y and ALL (from 330.05) all render; Valuation (current P/E), Financial Highlights (`android-08`–`-10`). COST (catalog company): Consumer Defensive · Discount Stores · Mega Cap, $902.32 (`android-12`) |
| 7 | Screener filters and paging | **PASS (with a data issue)** | Presets load; Growing Companies: "89 companies match · Checked all 160 companies · 3 left out"; Dividend Stocks: "7 companies match · Checked all 160 · **76 left out for missing data**" (see §6 issue 2) (`android-06`, `-07`). Paging through the list was not exercised by hand on the device (server paging verified in Phase 2: 160 rows, no duplicates) |
| 8 | Company Comparison | **PASS** | Example "Apple and Microsoft" → 2 of 4, Sample badge, "What can we learn…" insights, metric groups; added Visa (catalog) → 3 of 4 (`android-14`, `-15`) |
| 9 | Watchlist | **PASS (guest, device-local)** | Added COST from details → "1 company · Updated 6:05 PM ET", row $902.32 +1.41% (prices from Ubuntu watch-data); removed → "0 companies" (`android-21`, `-19`). Server watchlists need sign-in: **NOT TESTED** |
| 9b | Portfolio | **NOT TESTED (needs sign-in)** | Guest Portfolio shows "Sign in to save your portfolio securely" (`android-20`) |
| 10 | Practice trades | **NOT TESTED (needs sign-in)** | Server practice flow verified via API in Phase 2 (COST trade 200) |
| 10b | AI comparison (mock) | **NOT TESTED (needs sign-in)** | Gate is correct: "Sign in to use StockSteps AI" (`android-16`, `-17`) |
| 10c | Learn | **PASS** | Learn hub: "Understand a stock in five steps", Research Apple / Coca-Cola / JPMorgan / Microsoft (`android-18`) |
| 11 | Offline and reconnect | **PASS** | Wi-Fi and data off (emulator 5556 only): search → "Could not reach StockSteps. Check the server connection and try again." (`android-22`); back online (≈ 4 s), the same search returns PepsiCo (`android-23`) |
| 12 | Background → foreground | **PASS** | HOME, 8 s, relaunch → same screen and state, same process |
| 13 | Tab navigation, repeated searches | **PASS** | All five tabs; several searches; no crash |
| — | Physical Android device | **NOT TESTED** | `adb devices` lists only emulators |

## 4. iOS

### 4.1 Build and configuration

| Check | Result | Evidence |
|---|---|---|
| `Local.xcconfig` (git-ignored) | **PASS** | `STOCKSTEPS_MOCK_BACKEND_URL` = Ubuntu LAN :8081 (still valid: `/api/v1/meta` 200) |
| Debug build (worktree, scheme `iosApp`, `-derivedDataPath scratchpad/dd-mobile`, ad-hoc signed `CODE_SIGN_IDENTITY=-`) | **PASS** | BUILD SUCCEEDED; built `Info.plist` `StockStepsMockBackendURL` = Ubuntu LAN :8081. The main tree's `app.iosApp` scheme is per-user (git-ignored), so the clean worktree uses the auto-generated `iosApp` scheme |
| Install and launch on iPhone 16 Pro (iOS simulator) | **PASS** | Fresh install; sign-in screen, then "Continue as guest" |
| Default environment | **PASS (expected)** | REAL `localhost:8080`: "Daily Market Brief couldn't be loaded"; no fallback data |
| Settings → Development → Mock Data | **PASS** | Selected, then Home shows the "Sample data · mock backend" banner (`ios-01`) |
| Traffic reaches Ubuntu | **PASS** | The simulator's network log shows the app connecting to `<ubuntu-lan-ip>:8081` for 16 distinct paths (`/meta`, `/markets/overview`, `/daily-brief/latest`, `/stocks/search`, `/stocks/{s}/details`, `/chart`, `/news`, `/why-moving`, `/stocks/watch-data`, `/compare`, `/compare/history`, `/compare/performance`, `/screener/catalog`, `/earnings/calendar`, `/earnings/company/COST/latest`, `/next`), with CFNetwork `response_status=200`. Only 3 requests went to `localhost:8080`, before Mock was selected |

### 4.2 Simulator journeys (signed out)

| # | Journey | Result | Evidence |
|---|---|---|---|
| 1 | Launch | **PASS** | No crash reports; app process kept across background/foreground |
| 3 | Home | **PASS** | Sample badge, indices "Close · Oct 7", brief, "Sample data · mock backend" (`ios-01`) |
| 4 | Markets | **PASS** | US Market After-hours, next open Thu Oct 8 9:30 AM ET, indices, brief, Top Movers, sectors, research tools, market news (`ios-02`) |
| 5 | Search | **PASS** | "costco" → Costco Wholesale Corporation (COST · NASDAQ · USD) (`ios-03`) |
| 6 | Company details and charts | **PASS** | COST: logo, chips, $902.32 +1.41% Oct 7, After-hours, 1M chart Sep 7–Oct 7, 5Y chart 2021–2026, Open/High/Low/Volume, Why Did It Move? (`ios-04`, `-05`) |
| 7 | Screener | **PASS after retry (FAIL on first open)** | First open: "Couldn't load the screener · Screening isn't available right now." Root cause in §6 issue 1. "Try again" → presets loaded (`ios-10`). Filter results not exercised by hand on iOS |
| 8 | Company Comparison | **PASS** | From COST → Compare; added Walmart (search "wa" → Walmart Inc.) → 2 of 4, Sample, "Both companies are in the same industry (Discount Stores)…", COST $902.32 / WMT $106.16 (`ios-06`, `-07`) |
| 9 | Watchlist | **PASS (guest, device-local)** | Star on COST → Watchlist "1 company · Updated 6:17 PM ET" $902.32; ⋯ → Remove → empty (`ios-08`, `-09`) |
| 9b–10b | Portfolio, Practice, AI comparison | **NOT TESTED (needs sign-in)** | Portfolio: "Sign in to save your portfolio securely" (`ios-12`); Practice opens the sign-in sheet (`ios-13`) |
| 10c | Learn | **PASS** | Learn hub with research lessons (`ios-11`) |
| 11 | Offline and reconnect | **NOT TESTED** | The simulator shares the Mac's network; turning it off would affect the Phase E session and other apps |
| 12 | Background → foreground | **PASS** | HOME, 10 s, `simctl launch` → same PID (66943), state kept (`ios-14`) |
| — | Physical iPhone | **NOT TESTED** | `xcrun devicectl list devices` shows simulators only |

## 5. Part D: Authentication

- **How the apps authenticate:**
  - Both use Firebase Auth (real project) on the device and send the Firebase ID token as a bearer token.
  - The MOCK backend's `MockUserAuthenticator` reads the uid **without verifying** the signature (any LAN client can act as any uid; `mock-user:<uid>` is also accepted).
  - In REAL, `FirebaseIdTokenAuthenticator` verifies the token.
- **Tested:** the signed-out ("guest") path on both platforms. The sign-in screens render, and signed-in features show sign-in prompts.
- **Not tested:** real Firebase sign-in and every signed-in journey (server watchlists, Portfolio, Practice trades, AI comparison, StockSteps+ plan simulation).
  - **Blocker:** I must not enter account credentials. The Firebase Auth emulator needs owner approval.
  - **To unblock:** the owner signs in on `emulator-5556` and on the iPhone 16 Pro simulator, both still running. The iOS build is ad-hoc signed, so Firebase Auth's keychain works.
  - Signed-in data on the MOCK backend lives only in the container's memory. It is not Firestore and is lost on restart.
- No unsigned-token workaround was introduced; authentication was not changed.

## 6. Issues found

| # | Severity | Platform | Issue | Evidence and root cause | Recommended fix |
|---|---|---|---|---|---|
| 1 | Low | iOS | Discover Stocks shows "Couldn't load the screener" on first open after switching REAL → Mock | The shared `IosScreenerClient`'s `ScreenerPresenter` loaded the catalog at app launch while REAL was selected (`localhost:8080` → "Could not connect to the server", 18:29:41) and keeps that error; it doesn't reload when the environment changes or the screen reappears. "Try again" works. Android loads the catalog when the screen opens | On Discover appear, call `start()` again when `catalogError != null` (or reset the presenter on environment change). App change: needs approval |
| 2 | Medium (MOCK data) | Both | Dividend Stocks preset leaves out 76 of 160 companies "for missing data"; high-yield catalog companies (VZ, PFE…) never match | The Phase B catalog generates `dividendYield` but no `payoutRatio`; the preset also filters payout ratio ≤ 100 % | Generate a payout ratio for catalog companies (dividends paid = yield × market cap, ratio = dividends / net income; none for loss makers) in `SampleMarketData`. Server change, then redeploy: needs approval |
| 3 | Low (copy) | Both | Settings → Development: "Using local sample market data" while the data comes from the Ubuntu host | Static string in `SettingsScreen` (Android/iOS) | "Using the mock backend (sample data)". Needs approval |
| 4 | Low | iOS | Scrolled Markets content shows through the status-bar area | Screenshot during the Markets scroll | Same status-bar backdrop as Portfolio (Phase C.1) |
| 5 | Info | Android | After backing out of the app and relaunching, the guest choice is forgotten (sign-in screen again) | Observed once | Product decision (may be intended) |
| 6 | Info | Both | Market News (MOCK fixtures) includes general political headlines | Known limitation (`project-status.md` §5) | — |
| 7 | Security (known) | Backend | MOCK accepts unsigned tokens; debug and `/internal/*` routes open | By design for the LAN | Plan in `UBUNTU_BACKEND_VERIFICATION_2026-10-13.md` §6 before any remote access |

No backend API errors were seen: every Ubuntu response in the iOS log was 200, and the container log has 0 WARN/ERROR lines.

## 7. Part E: Backend monitoring (end of session)

| Check | Result |
|---|---|
| Requests reach Ubuntu | **PASS**: Android socket evidence (§3.1) and the iOS network log (§4.1) |
| Responses are MOCK | **PASS**: `dataMode: mock`; Sample badges and banner; fixture values (Oct 7 closes, catalog companies) |
| Container logs | **PASS**: 75 lines since start, 0 WARN/ERROR, 0 provider URLs, 0 secret-like lines |
| FMP / Finnhub / Gemini | **PASS**: upstream 0; no fmp/finnhub/gemini/boc counters (only the internal `statements` counter) |
| Resources | StockSteps CPU 0.25 %, 141.5 MiB / 1 GiB |
| Immich and Nextcloud | **PASS**: all 8 containers running with the same start times and restart counts as before; Immich ping 200; Nextcloud `status.php` installed, not in maintenance |

Note: the apps load company logos and news images straight from public CDNs (FMP image host, Finnhub static, CNBC). These are image downloads by the
apps, not provider API calls, and are not counted (documented in Phase 5B Local).

## 8. Remaining work

1. **Owner sign-in session:** sign in on `emulator-5556` and the iPhone 16 Pro simulator, then test watchlist sync, Portfolio, Practice preview/execute, AI
   comparison (mock "Sample analysis") and the StockSteps+ simulation on both.
2. **Long-uptime host smoke** after ≈ 13:50 UTC 2026-10-14 (no restart).
3. **Physical devices** when one is connected:
   - Android: `adb install` of the same APK (cleartext is allowed in debug only).
   - iPhone: a development-signed build needs the owner's Apple team; Local Network permission is asked on first use.
4. **Fixes 1–4** above (approval needed; issue 2 needs a server change and a redeploy).
5. **Cleanup:** the scratch worktree `mobile-fa7c563` and `scratchpad/dd-mobile`, and shutting down `emulator-5556` and the iPhone 16 Pro simulator, once the
   sign-in pass is done.
