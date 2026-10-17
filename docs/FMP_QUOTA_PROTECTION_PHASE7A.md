# Phase 7A — FMP quota protection and restricted-quote error handling

Date: 2026-10-16/17. Base: `29c6cae` ("Add the Phase 6 REAL market-data provider audit …", pushed; `main == origin/main`, clean tree at
start). Commit: "Protect the FMP quota for Phase 7A with cached restricted and rate-limited quote outcomes, an optional configured daily allowance, distinct restricted and daily-limit errors, a Practice QUOTE_RESTRICTED blocker and no $0.00 price on blocked Practice previews" (committed and pushed on the owner's request). Not deployed. The LAN REAL container still runs image `ff5662e…-dirty`: neither `be3c15e` (Discover
cooldown) nor this phase is live there. Review package: `docs/reviews/fmp_quota_phase7a/`.

## 1. Root causes addressed (from the Phase 6 audit, re-checked in code)

| # | Cause (before) | Where | Fix |
|---|---|---|---|
| 1 | A refused quote was never cached (`quote:SYM` stored only successes), so every read re-asked FMP | `FmpStockProviderRepositoryImpl.getQuote` | cache the **outcome** with the shared `providerCooldown`: 402/403 1 h, 429 10 min, a local daily-allowance denial and other failures 30 s; the failure is rethrown with its type |
| 2 | Markets rebuilt sectors every 2 min (live) and re-asked all 11 refused ETFs; the section reported "temporarily unavailable" and retried after 30 s | `MarketsService.loadSectors` | fixed by #1 per fund; when *every* fund is plan-refused the section error is `PROVIDER_RESTRICTED` (1 h section cooldown) |
| 3 | No daily allowance: only a per-minute bucket; the LAN `PROVIDER_FMP_PER_MINUTE=250` can't protect a 250/day plan | `ProviderGuard` | optional hard `PROVIDER_<NAME>_DAILY_LIMIT`, separate from the minute rate, check-and-count under the lane lock, UTC budget day |
| 4 | A 402 became 502 `PROVIDER_UNAVAILABLE` "temporarily unavailable" | `ApiErrorHandling`, `SnapshotErrors` | one mapping, `ProviderErrors.of`: `PROVIDER_RESTRICTED` / `PROVIDER_DAILY_LIMIT_REACHED` / unchanged codes; HTTP statuses unchanged |
| 5 | Practice said `QUOTE_MISSING` "no price right now" (with "Check the price again") for a plan-refused symbol | `PracticeService.priceForTrade`, `WatchMarketData` | `QUOTE_RESTRICTED` (409, "Trying again won't help"); the apps show no retry button for it (their retry list is unchanged) |
| 6 | **Found during testing:** a blocked Practice preview carries the contract placeholder `price = "0"`, and both apps showed "Latest usable price **$0.00**" | `PracticeOrderScreen.kt`, `PracticeScenes.swift` | core `PracticeFormat.previewPrice` → "Unavailable" for a non-positive price; both apps use it; the JSON contract is unchanged |

Not changed (later phases): EOD-backed Markets (7B), EOD Practice/Portfolio (7C), curated Discover, provider or plan changes.

## 2. Request flows (after this phase)

| Case | Path and owner |
|---|---|
| A. Successful quote | route / service → `StockService.getStock` → `FmpStockProviderRepositoryImpl.getQuote` (`fmp` cache, key `quote:SYM`, single flight) → `apiCall` (`ProviderGuard.acquire`: breaker → daily allowance + token → concurrency; `ProviderCalls.record`) → 200 → outcome cached 30 s (session-aware, ≤ 15 min while closed) |
| B. Restricted (402/403) | `apiCall` → `StockProviderException(UNAVAILABLE, 402/403)` (`restricted`) → outcome cached **1 h** → rethrown: route 502 `PROVIDER_RESTRICTED`; `WatchMarketData` null + `quoteRestricted`; Practice `QUOTE_RESTRICTED`; Markets sectors `PROVIDER_RESTRICTED` when all refused. Guard: 402/403 don't trip breakers (unchanged) |
| C. Provider 429 | `apiCall` → `RATE_LIMITED(429)`; `ProviderGuard.complete` opens the provider circuit (Retry-After, else 30 s doubling to 5 min, unchanged) → outcome cached **10 min** → 503 `PROVIDER_RATE_LIMITED`. **Never treated as a daily cap** (a 429 doesn't say which limit; FMP's "Limit Reach" body isn't documented as daily-only) |
| D. Confirmed daily cap | only with `PROVIDER_FMP_DAILY_LIMIT` set: once this instance's share (`limit × PROVIDER_SAFETY_MARGIN ÷ CLOUD_RUN_MAX_INSTANCES`) is admitted, `acquire` refuses **before any request** (`dailyLimitReached`, no upstream, no token used) → outcome cached 30 s per symbol (cheap local re-check) → 503 `PROVIDER_DAILY_LIMIT_REACHED`; one `provider.fmp.dailyLimitReached` event per day; new budget day at 00:00 UTC |
| E. 5xx / network / timeout | `UNAVAILABLE` / `TIMEOUT` → 30 s; five consecutive 5xx/timeouts open that endpoint's breaker (unchanged) → 502/504 as before |
| F. Valid but missing symbol | FMP 200 `[]` → outcome `quote = null` cached like a success → route 404 `STOCK_NOT_FOUND` (unchanged); provider failures never become "not found" (`SymbolExistence` already returns UNKNOWN for them) |
| G. Concurrent identical requests | `CompanyFinancialCache` single flight: one upstream request, every joined caller gets the same outcome (success or typed failure) |

No new cache layer: the outcome replaces the success-only value under the same `quote:SYM` key in the same `fmp` cache. Profiles, charts,
statements and the screener universe already had outcome cooldowns.

## 3. API contracts

- **New error codes** (bodies are the existing `ApiError {code, message}`; statuses unchanged):
  - `PROVIDER_RESTRICTED` (502), "This market data isn't included in this server's market data plan. Trying again won't help.";
  - `PROVIDER_DAILY_LIMIT_REACHED` (503), "This server has used today's market data allowance. Market data will be available again
    after the daily reset.";
  - Practice blocker `QUOTE_RESTRICTED` (409, like `QUOTE_MISSING`).
- **Unchanged:** every success response; `PROVIDER_RATE_LIMITED`, `PROVIDER_UNAVAILABLE`, `PROVIDER_TIMEOUT`,
  `INVALID_PROVIDER_RESPONSE`, `STOCK_NOT_FOUND`; Discover's `SCREENER_*` codes; `PracticeOrderPreview.price` stays a non-null string
  (`"0"` when unpriced).
- Clients show server messages verbatim and branch only on known codes. The new codes need no client change to be shown, and none of them
  is in a client retry list.

## 4. Before/after requests (MockEngine, `QuoteQuotaBenchmarkTest`, `server/build/phase7a-quote-benchmark.txt`)

| Scenario | Before | After |
|---|---:|---:|
| 10 repeated reads, one restricted symbol (402), within 1 min | 10 | 1 |
| 20 concurrent reads, one restricted symbol | 1 | 1 |
| 10 different restricted symbols, 3 reads each | 30 | 10 |
| Markets closed: 3 overview builds in 58 min, all 11 sector funds refused | 33 | 11 |
| Markets live: 29 overview builds in 58 min (every 2 min), all 11 refused | 319 | 11 |
| 429: 5 reads within 10 min, then 1 after | 6 | 2 |
| Daily allowance 10, 50 concurrent callers on 50 symbols | 50 | 10 |

"Before" replays the pre-7A rule against the same mock. Compared with the Phase 6 observation of 21 refused quotes per Markets build, the
sector funds now cost 11 requests per hour per instance instead of up to ~330.

## 5. Tests and builds

See `docs/reviews/fmp_quota_phase7a/CHATGPT_TEST_RESULTS.md` for counts. New:
- `QuoteQuotaProtectionTest` (12 tests);
- `QuoteQuotaBenchmarkTest` (1);
- `PracticeServiceTest.aPlanRestrictedQuoteBlocksTradingWithItsOwnReasonAndIsNeverValued`;
- `PracticeFormatTest.aPreviewWithoutAUsablePriceSaysUnavailable`.

`FmpMock` gained `symbolStatus` (per-endpoint, per-symbol statuses).

## 6. Device verification

NOT RUN.
- The Android/iOS change is one formatter call on the Practice order screen.
- Both builds are covered in the test results.
- A REAL device check needs an approved redeploy, and the FMP key's daily cap was exhausted at the end of Phase 6.

## 7. Configuration and operations

Configuration and operations are covered in `docs/reviews/fmp_quota_phase7a/CONFIGURATION_DECISIONS.md`:
- no environment value was changed;
- `PROVIDER_FMP_DAILY_LIMIT` stays unset until the owner confirms the account's daily limit;
- deploying needs an image rebuild and a container restart, which need approval.

## 8. Remaining restrictions and open questions

- **Plan restrictions remain.** The current plan still refuses most quotes, the screener and some statements. This phase only makes them
  cheap and honest.
- **Q1:** the FMP tier, its daily limit and its reset time (UTC midnight or rolling).
- **Q3:** whether 402/429 answers count against FMP's limit.
- **Q4:** the display licence (Phase 6 §11).
- **Owner decisions:**
  - the copy of the three new messages;
  - the daily-limit value and whether to set `PROVIDER_FMP_DAILY_LIMIT` on LAN REAL;
  - a redeploy of LAN REAL with `be3c15e` and this phase.

**Status: READY FOR REVIEW.** All suites and both builds pass (core iOS on rerun of one flaky logging test); devices NOT RUN.
