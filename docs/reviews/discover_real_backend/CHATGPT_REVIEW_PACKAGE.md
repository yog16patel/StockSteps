# Review package — Discover Stocks on the LAN REAL backend

## 1. Phase and Git baseline
- Phase: Discover REAL backend integration fix (diagnosis and smallest safe fix).
- Base: `c6baf6a` "Refine app-wide typography and spacing for Phase 5 …" (local, not pushed).
- Under review: "Fix Discover Stocks on the LAN REAL backend with distinct plan-restricted, rate-limited and temporary screener errors, a provider cooldown on the universe request and no Retry when the FMP plan excludes the screener on Android and iOS", committed and pushed on the owner's request. Not deployed.
- **Verdict: BLOCKED.** The FMP plan doesn't include `company-screener` (402), so REAL can't show genuine results.

## 2. Changes implemented
- Server:
  - the REAL universe failure now says why: plan restriction (402/403) `SCREENER_PROVIDER_RESTRICTED`, rate limit (429)
    `SCREENER_RATE_LIMITED`, or temporary `SCREENER_UNAVAILABLE` (unchanged copy);
  - a refused request is cached with the existing `providerCooldown` (1 h / 10 min / 30 s), not repeated on every open.
- App (Android + iOS):
  - plan restricted shows the title "Discover Stocks isn't available", the server message, and **no Retry**; rate limit and temporary
    keep "Try again";
  - with no server answer, the screen says the server couldn't be reached, or that the response couldn't be read.

## 3. Affected files
- **server:** `screener/ScreenerService.kt`; test `screener/ScreenerUniverseFailureTest.kt` (new).
- **core:**
  - `screener/ScreenerDisplay.kt` (codes, copy, `catalogErrorTitle`, `catalogRetryable`);
  - `screener/ScreenerPresentation.kt` (`catalogErrorCode`, fallback copy);
  - test `screener/ScreenerEngineTest.kt`.
- **shared (Android):** `presentation/screener/ScreenerScreen.kt`.
- **iOS:** `DiscoverStocksViews.swift`.
- **docs:**
  - `docs/DISCOVER_REAL_BACKEND_INTEGRATION_FIX.md`;
  - this package;
  - `docs/PROJECT_HANDOFF.md`;
  - `docs/project-status.md`.

## 4. Architectural decisions
- Reuse the existing `CompanyFinancialCache` outcome + `providerCooldown` pattern (as `SparklineService` / `StockService.getProfile`).
  There's no new cache, TTL, retry loop or infrastructure.
- The status stays 503 for every universe failure. Only the `ApiError.code` and the message differ, so deployed apps keep working.
- The code is a display decision in core `ScreenerDisplay`, and both platforms call it. The server message stays verbatim.
- Never fall back from REAL to MOCK or samples.

## 5. Test and build results
All PASS:
- core JVM 613/0;
- core iOS 611/0;
- server 515/0 with 3 pre-existing skips;
- shared Android 284/0;
- shared iOS 281/0;
- `assembleDebug`;
- `xcodebuild`.

Details: `CHATGPT_TEST_RESULTS.md`.

## 6. Android verification
- Pixel 7, REAL, guest:
  - the failure reproduced (503 "Try again shortly", 27 ms, `budgetDenied` while FMP was rate limiting; earlier attempts 402);
  - the after state on the device is BLOCKED: the phone locked with a secure lock screen;
  - REAL after the fix needs a redeploy (owner approval).
- Request counts: MockEngine (report §7). On the device: NOT MEASURED.

## 7. iOS verification
NOT RUN on a simulator or device; `xcodebuild` PASS. The change is the same two display calls as on Android.

## 8. Known issues
- FMP single quotes are refused (402) for most symbols on this plan: 111 of 139 since the container start. There's no cooldown for
  quote denials, which affects Markets, Home and Company Details. Separate follow-up.
- FMP answered 429 at about 218 requests in 2 h (shared key, D-R3).
- Search errors still always offer Retry. They're unreachable while the catalog fails.

## 9. Unverified scenarios
BLOCKED by the FMP plan:
- genuine REAL results;
- filters and presets;
- sorting and pagination;
- Company Details from a row.

BLOCKED by the locked phone:
- the device after state;
- the MOCK → REAL switch.

BLOCKED pending approval: the REAL redeploy.

NOT RUN: iOS on a device or simulator.

## 10. Deviations from the brief
- The device "after" check used a stub instead of the redeployed server, because a redeploy needs approval. It didn't complete
  (phone locked).
- Three low-volume FMP probes (one request each) classified the 402. Each was made from inside the container, and the key was never
  printed.

## 11. Decisions requiring owner approval
1. The FMP plan: upgrade to a tier with the company screener, a curated repo-owned universe with `profile`, or Finnhub
   `profile2`/`metric` (report §5).
2. Redeploy LAN REAL `:8082` with this server change.
3. The copy of the three messages and the "Discover Stocks isn't available" title.

## 12. Links
- Report: `docs/DISCOVER_REAL_BACKEND_INTEGRATION_FIX.md`.
- Focused diff: `CHATGPT_FOCUSED_DIFF.md`.
- Before screenshot: `captures/before_real_discover.png` (git-ignored).
- No contact sheets: the only visual change is an error state, which wasn't captured on the device.
