# Phase 4 — Company Search Relevance & Performance Audit

- **Status:** audit + one targeted server fix + regression tests. **Approved by the owner (2026-10-16)**; committed locally as "Normalize inner whitespace in Company Search queries for Phase 4 so the provider query and search cache key match, with ranking, stale-response and cache regression tests and the relevance and performance audit" (not pushed, not deployed).
- **Baseline:** `6d6b68e` "Note that the Company Details UI Phase 3 approval record 17b14a7 is pushed in the handoff and project status"
  (clean tree; on top of `17b14a7`, `337274e`).
- **Scope:** audit the existing Company Search (5D.5.5, closed in `ebadf96`); change code only where a defect is confirmed. No UI redesign,
  no provider/FMP changes, no new APIs, no navigation, Portfolio/Practice, Firebase or infrastructure changes.
- **Environment:** local MOCK on `:8083` (fixtures; zero provider calls), emulator-5554 (Pixel 9 Pro, debug build on MOCK `127.0.0.1:8083`),
  iPhone 16 simulator (guest, MOCK `localhost:8083`). REAL was **not** called (no approved quota for this phase).
- **Jira KAN-33:** NOT AVAILABLE (no Jira access in this session); the brief was used as the requirement.

## 1. Where things happen (request flow)

```
Android StockSearchScene → StockSearchViewModel (300 ms debounce, collectLatest, latest-query check)
iOS StockSearchView      → StockSearchViewModel.swift (Combine removeDuplicates + 300 ms debounce, task + bridge cancel)
        ↓ core SearchStocks (blank → no request; trim) → RemoteStockRepository → StockStepsApi GET /api/v1/stocks/search?query=
Ktor stockRoutes: trim, 1–100 chars, no control chars (400 INVALID_QUERY) — admission group MARKET_DATA
        ↓ StockService.searchStocks: cache key = normalized lower-case query; CompanyFinancialCache("search", 512, 6 h, coalesced)
        ↓ miss → StockProviderRepository.searchStocks
REAL: FmpStockProviderRepositoryImpl → 2 concurrent apiCall()s: stable/search-symbol + stable/search-name (no limit param, D8)
MOCK: FixtureMarketDataSource → fixtures/stocks/search.json + catalog.json (symbol prefix or name substring), no provider
        ↓ USD + US exchange filter (D1) → distinctBy(symbol) → SearchRanking.rank (core, shared) → logoUrl from ticker (no call)
```

- **Ranking happens once, on the server**, over the *whole* merged provider list from both endpoints (never a client-side re-rank of a
  truncated set). Clients render the server order; `SearchDisplay` only groups ("Best match") and never reorders or drops.
- **Candidate depth:** whatever FMP's default page size returns per endpoint (D8: no `limit` change until an approved REAL check). A company
  absent from both provider pages can't be ranked — documented limitation, NOT MEASURED in REAL.

## 2. Inventory

| Capability | Status | Evidence |
|---|---|---|
| Company-name search | PASS | MOCK `apple`, `Microsoft`, `Tesla` → company first (`EXACT_NAME`) |
| Exact ticker | PASS | MOCK `AAPL`, `MSFT`, `GOOG`, `GOOGL`, `NVDA` first; `SearchRankingTest` |
| Partial ticker | PASS | `goo` → GOOG, GOOGL, GOOGN, GOOGM, GOOX (`TICKER_PREFIX`) |
| Partial name | PASS | `micro` → MSFT, MU (name prefix), then ETFs naming Microsoft later |
| Case-insensitivity | PASS | `ApPlE` = `apple` (same order, server cache hit) |
| Whitespace normalization | **BROKEN → fixed** | leading/trailing trimmed everywhere, but inner runs reached the provider verbatim: `apple  inc` → **0 results** (MOCK) and a separate cache entry. Fixed in `StockService` (§4) |
| ETF search | PASS (logic) / PARTIAL (MOCK data) | ETFs rank by the same tiers (AAPX, AAPY, NVDX, MSFX in MOCK); no SPY/QQQ/VOO fixture in MOCK, so broad-index ETFs are covered by unit test only |
| Logo fallback | PASS | server `fmpLogoUrl(symbol)`, no call; ticker initials when the image fails (5D.5.5D) |
| Exchange identification | PASS | "AAPL · NASDAQ" identity line; results without a US exchange are out of scope (D1) |
| Security-type identification | N/A (owner decision D3: no type labels) | ETFs are recognisable only by their provider name; no type is fabricated |
| Ordering | PASS | deterministic tiers + OTC after major exchange (D2) + provider order + symbol |
| Duplicates | PASS | `distinctBy(symbol)` after the scope filter; share classes stay separate |
| Recent searches | PASS | `RecentSearchStore`: selected results only, newest first, max 10, one per listing |
| History persistence | PASS | device-local key `stocksteps.recentSearches` (SharedPreferences / UserDefaults), survives restart; no cloud history (none proposed without approval) |
| Debouncing | PASS | 300 ms both platforms; blank query = 0 ms, no request |
| Request cancellation | PASS | Android `collectLatest` + `searchJob.cancel()`; iOS `searchTask.cancel()` + `client.cancelSearch()` |
| Out-of-order responses | PASS | Android latest-query check before applying; iOS cancelled-task guard. New race test (§5) |
| Empty / loading / error states | PASS | `SearchDisplay.phase` (5D.5.5C/D, re-run in 5D.5.5D §8) |
| Retry | PASS | runs the current query once; double tap = one request (unit test) |
| Navigation to Company Details | PASS | both platforms open AAPL on Markets; 6 detail requests, 0 search requests |
| Search-state restoration | PASS (by design) | search is a modal (plan 5D.5.6 rule 5): it dismisses before Company Details; Back returns to the origin screen, search reopens empty with recents (D4) |
| Android/iOS parity | PASS | identical request counts (§6), same server order |
| Accessibility | PARTIAL | spoken row labels, headings, clear labels exist (5D.5.5D §8.3); TalkBack/VoiceOver walkthrough still with the owner |

## 3. Ranking rules (unchanged, `core/search/SearchRanking`)
`EXACT_TICKER → EXACT_NAME → TICKER_PREFIX → NAME_WORD_START → NAME_PREFIX → LATER_WORD → LATER_WORD_PREFIX → SUBSTRING → PROVIDER`;
within a tier major exchange before OTC, then provider order, then symbol. Tickers compare case-insensitively with `.` = `-`
(BRK.B = BRK-B, GOOG ≠ GOOGL); names case-fold, punctuation → space, inner whitespace collapsed, one legal suffix ignored for the exact-name
check. No popularity, market cap, AI or hard-coded names/tickers. **No ranking change was needed.**

### Ranking examples (before = after; ranking code unchanged)
| Query | Result order | Source |
|---|---|---|
| `APP` | APP (AppLovin, exact ticker) → APPN (ticker prefix) → AAPL (name prefix) → APLD → AAPX | unit test (MOCK has no APP fixture) |
| `APP` (MOCK) | APPLX (ticker prefix) → AAPL, APLE, AMAT … (name prefix) | MOCK :8083 |
| `AAPL` | AAPL → AAPY (ETF naming "(AAPL)") | MOCK + unit |
| `apple` | AAPL → APLE → AAPI → APRU → APPLX → AAPX → AAPY → GAPJ → Pineapple… | MOCK |
| `MSFT` / `Microsoft` | MSFT first; leveraged ETFs after | MOCK |
| `micro` | MSFT, MU (name prefix, provider order) → MSFX, MSFY | MOCK + unit |
| `GOOG` / `GOOGL` | each exact ticker first; both listed | MOCK + unit |
| `NVDA` / `Tesla` | NVDA / TSLA first | MOCK |
| `spy` | SPY → SPYG | unit |
| `BRK.B` | BRK-B → BRK-A | unit (no MOCK fixture) |
| `ZZZZQ` | empty (MOCK); provider order kept if anything returns | MOCK + unit |
| `apple  inc` | **before: no results** → after: AAPL | MOCK :8083 (old code) vs :8084 (fixed build, run briefly and stopped); server test |

## 4. Confirmed defect and fix
**D1 — inner whitespace not normalized before the provider and the cache key.** The approved plan (5D.5.5 §7) says "trim, case-fold, collapse
inner whitespace". `SearchRanking` did, but `StockService.searchStocks` only trimmed: `apple  inc` was sent to the provider verbatim (FMP and
the MOCK fixture both substring-match, so the doubled space found nothing) and cached under its own key (another pair of FMP calls in REAL).
- **Fix** (`server/.../service/StockService.kt`): collapse `\s+` to one space after trimming; the same normalized text is the provider query
  and (lower-cased) the cache key. Clients and the API contract are unchanged.
- **Test:** `StockSearchTest.innerWhitespaceAndCaseShareOneNormalizedProviderQueryAndCacheEntry` — `apple  inc`, `Apple Inc`,
  `  APPLE   INC ` all return AAPL with **one** pair of provider calls, query `apple inc`.

## 5. Race condition (`app` → `apple`, B answers first, A afterwards)
- Android: already safe (latest-query check + cancellation, `outOfOrderResponsesErrorsAndRetryKeepOnlyTheCurrentQuery`). Added
  `olderSlowerQueryNeverReplacesNewerResultsEvenWithoutCancellation`: A ignores cancellation (`NonCancellable`) and answers 2.5 s after B;
  results stay AAPL, `searching` ends with B, exactly 2 requests, and re-typing `" apple "` sends none.
- iOS: `prepareSearch` cancels the task and the Kotlin deferred on every change; results apply only when the task wasn't cancelled; the
  debounce sink drops values that no longer equal the field. Device: `app` (settled) then `le` → 2 requests, final list = `apple`. No iOS
  unit-test target exists, so this is code review + device evidence.

## 6. Request efficiency (measured with DEBUG API logging, MOCK)
| Scenario | Android (`StockSteps-API`) | iOS (`[StockSteps-API]`) |
|---|---|---|
| Fast typing `apple` | 1 request | 1 request |
| `app`, pause 1 s, `le` | 2 requests (app, apple) | 2 requests (app, apple) |
| Delete 5 chars then retype | 0 for intermediate states | — |
| Retype a query seen before | 1 backend request (server cache hit, 0 provider calls) | same |
| Select result → Company Details | 0 search; 6 detail requests | 0 search; 6 detail requests |
| Back from Company Details | 0 | 0 |
| Tab switches (Markets ↔ Home ×3) | 0 requests of any kind | not re-run (5D.5.6C3/D: no repeated requests) |
| Duplicate in-flight requests | none observed; Retry double tap = 1 (unit) | none observed |
| Backend response time (MOCK, 17 queries, curl) | median ≈ 3.6 ms; app round trip 33–71 ms | 7–48 ms |
| Provider calls per uncached query | REAL: 2 (search-symbol + search-name), 0 on a hit; MOCK: 0 | same |

## 7. Server search cache inventory (audit only)
| Aspect | Finding |
|---|---|
| Cached? where | Yes: `StockService.searchCache` = `CompanyFinancialCache("search")`, one per server process |
| Key | normalized query, lower-case (`Locale.ROOT`); after this phase inner whitespace is collapsed too |
| TTL / size / eviction | `FinancialCachePolicy.SEARCH` = 6 h; 512 entries; expired first, then LRU (`trim`) |
| Concurrent coalescing | Yes: one loader, joined callers wait; a cancelled owner hands over (`FinancialCacheTest`) |
| Failures | shared with concurrent callers, never stored; 429/402/403 not retried (`apiCall`) |
| Negative caching | empty results are cached for 6 h like any list (a ticker listed within that window appears after expiry) |
| Upstream reduction | measured on :8083: 3 hits / 17 lookups in a mixed run (`ApPlE`, `  apple  ` reused `apple`); every hit saves 2 FMP calls in REAL |
| Cross-user sharing | Yes, public reference data only: no tokens, no user id, no per-user fields in the key or value |
| Restart | in-memory: lost on restart; per instance (no sharing across Cloud Run instances) |

**Proposal (not built):** no cache change is needed for correctness. Optional, owner decision: (a) a shorter `resultTtl` (e.g. 1 h) for empty
results, so a new listing becomes searchable sooner — no cost benefit, measurable only with REAL; (b) a tiny client-side memo of the last
few result lists to skip the backend round trip when a user backspaces to a query seen seconds ago — saves ~1 backend request per such
edit and 0 provider calls (the server already hits). Neither is worth doing before REAL usage data.

## 8. Correctness notes
- Share classes (GOOG/GOOGL, BRK-A/BRK-B) are never merged; `.`/`-` spellings of one ticker are one listing.
- ETF vs stock: no type field is shown or invented (D3).
- Delisted/inactive: FMP search has no status field in our model; nothing is filtered or labelled (no data to support it).
- Missing exchange: out of scope by the US-exchange filter (D1); missing logo → initials.
- Long names: rows wrap (5D.5.5D large-text checks).
- Provider inconsistencies: a blank symbol/name fails the request as `INVALID_PROVIDER_RESPONSE` (existing). If one FMP endpoint fails, the
  whole search fails — a partial-result fallback would be a provider-behaviour change and is left to the backlog.

## 9. Before/after
| Metric | Before | After |
|---|---|---|
| Exact ticker ranking | PASS | PASS (unchanged) |
| Partial-name ranking | PASS | PASS (unchanged) |
| Inner-whitespace query (`apple  inc`) | 0 results; own cache entry (MOCK :8083) | AAPL; shares `apple inc` entry (MOCK :8084) |
| Duplicates | 0 | 0 |
| API requests per settled search | 1 | 1 |
| External provider requests per uncached query | 2 (REAL); `apple inc` + `apple  inc` = 4 | 2; the two spellings = 2 (server test) |
| Duplicate in-flight requests | 0 | 0 |
| Cache hit rate | 3/17 in the mixed MOCK run on :8083 | 1/4 on the fixed server (:8084, 4 queries): `apple inc` reused `apple  inc` |
| Median response time | ≈ 3.6 ms backend (MOCK curl) | unchanged code path (+ one regex split) |
| Stale-response failures | 0 | 0 (new regression test) |
| REAL provider measurements | NOT MEASURED | NOT MEASURED |

## 10. Files changed
- `server/src/main/kotlin/org/example/stocksteps/service/StockService.kt` — inner-whitespace normalization (D1).
- `server/src/test/kotlin/org/example/stocksteps/StockSearchTest.kt` — +1 test.
- `core/src/commonTest/kotlin/org/example/stocksteps/search/SearchRankingTest.kt` — +1 test (APP/AAPL/MSFT/micro/GOOG/SPY/BRK.B/whitespace).
- `app/shared/src/commonTest/.../stocksearch/StockSearchViewModelTest.kt` — +1 race test.
- Docs: this report, `docs/reviews/company_search_phase4/`, `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`.
- No UI, Android/iOS source, provider, navigation or fixture changes.

## 11. Tests and builds
See `docs/reviews/company_search_phase4/CHATGPT_TEST_RESULTS.md`.

## 12. Device verification
UI unchanged, so no screenshots or contact sheets (AI_REVIEW_WORKFLOW §3.2 "skipping sheets"). Checked on both platforms: exact name,
rapid typing, clear and retype, selection, Back, recents shown on reopen, request counts (§6). Large text (Android 2.0×, iOS AX5), dark,
empty, error/Retry and network failure: **NOT RE-RUN** — the client code is unchanged since the 5D.5.5D §8 PASS run. Screen readers: NOT RUN (owner).
Device state was not changed (font scale 1.0, light, Default Dynamic Type).

## 13. Backlog (separate; not implemented)
1. Approved REAL check of FMP's default page size for both endpoints (D8) and of `APP`, `BRK.B`, `SPY` in REAL.
2. Partial results when one FMP endpoint fails (provider behaviour change).
3. Optional cache tweaks in §7 (empty-result TTL, client memo) after REAL usage data.
4. Shared cross-instance search cache only if Cloud Run scales beyond one instance (no new infrastructure now).
5. MOCK fixtures for a broad ETF (SPY/QQQ) and a dotted class symbol, from a real capture, so device checks can cover them.
6. Owner TalkBack/VoiceOver pass; FMP logo licence (UNVERIFIED, launch dependency).

## 14. Recommendation
**APPROVE WITH FIXES** — Company Search already met the relevance, debounce, cancellation and caching requirements; the one confirmed defect
(inner whitespace) is fixed server-side with a test. Open items are verification-only (REAL page size, screen readers).
