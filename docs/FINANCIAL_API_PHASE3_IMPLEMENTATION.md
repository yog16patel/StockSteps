# Financial API — Phase 3 implementation and verification

Date: 2026-10-09. Builds on `FINANCIAL_API_PHASE3_AUDIT.md`, `…_COST_MODEL.md`, `…_DECISIONS.md` and `…_IMPLEMENTATION_PLAN.md`
(kept unchanged). Owner decision applied: **default screener universe = 50 NASDAQ + 50 NYSE + 50 TSX (150)**.
Implementation commit: "Implement financial API Phase 3: screener re-warm fix, 150-company universe, selective statements, market- and
earnings-aware freshness, stale fallback". Verification (benchmark, edge tests and one fix): commit "Verify financial API Phase 3 with before/after benchmarks and fix earnings-aware statement coverage".
Every number here comes from MockEngine tests (REAL adapters, no network, fake clocks); no REAL provider calls were made.

## 1. Status

| Milestone | Status | Main change | Tests |
|---|---|---|---|
| 3B-0 | Done, verified | `ScreenerService.warm()` reloads missing **or expired** fundamentals (never-loaded first, then oldest) within `fundamentalsPerHour`; 30-min failure backoff; all-datasets-failed loads aren't held; partial/stale loads refresh after 1 h; records expire with their fundamentals; bounded maps | `ScreenerWarmupTest` (7), benchmark D24/H |
| 3A | Done, verified | `StockProviderRepository.statementHistory(symbol, period, statements)` (same FMP dataset keys); history = income only; detailed research = income + balance + cash flow; failures not cached as empty history | `SelectiveStatementsTest` (10), benchmark B |
| 3B-1 | Done, verified | ≤ 50 per exchange (`SCREENER_UNIVERSE_LIMIT`), honest description; `screenerFundamentals` (10 datasets + quote); FMP dataset cache 4,096 (`FMP_DATASET_CACHE_ENTRIES`) | `ScreenerUniverseOptimizationTest` (7), benchmark D/D24 |
| 3C | Done, verified | `MarketFreshnessPolicy` (US / TSX calendars) for quotes, TTM ratios, intraday bars; rollback `MARKET_AWARE_TTL=false` | `MarketFreshnessTest` (9), benchmark F |
| 3D | Done, verified, **fixed** | `EarningsStatementSignals`: 2 h statement lifetime after a reported quarter until the period appears (10-day window). Verification found the 105-day fallback could mark a fast reporter's *previous* quarter as covered; replaced by a baseline rule (§6) | `EarningsAwareStatementsTest` (12), benchmark G |
| 3E | Done for statements/estimates, verified | Labelled stale fallback (statements ≤ 7 d, estimates ≤ 2 d; never 402/403, TTM ratios, quotes); additive `freshness`/`staleDatasets`; Financials notice (Android + iOS); comparison/history notes | `StaleFallbackTest` (7), core presenter test, `SelectiveStatementsTest` |

## 2. Benchmark method

`server/src/test/.../service/Phase3AuditBenchmarkTest.kt` (output `server/build/phase3-benchmark.txt`, per provider and endpoint):
REAL `FmpStockProviderRepositoryImpl`, `FmpPriceHistoryProvider`, `FinnhubEarningsDataSource`, services and screener on `FmpMock`
(Ktor MockEngine); one instance; one `MutableClock` drives cache expiry, market sessions, warm-up budget, earnings windows and retrieval times.
- **Before (pre-Phase 3, measured)**: the audit benchmark and a 24 h screener run on the old code (`e4ba2be`, temporary worktree; the extra
  test wasn't committed). Reproduced the audit: A 17, B 72 (1Y alone 12), C 0 extra, D 402–403, E 150, F 4 repeats, G 24 h, re-warm 15 → 0.
- **Legacy config (measured)**: today's code with the Phase 3 switches off (fixed lifetimes, no signals, full bundles, 100 per exchange,
  512 entries) on the same mock — like-for-like with "after" for A, B, F, G (the screener re-warm can't be switched back on).
- **After (measured)**: the configuration `Application.realDataSources` wires.
- **Modeled**: §4 only, labelled.

## 3. Results (measured upstream requests)

| Scenario | Before | After | Reduction | Data correctness | Notes |
|---|---:|---:|---:|---|---|
| A Company Details (overview, annual + quarterly financials, valuation) | 17 | 17 | 0 | unchanged (every dataset displayed) | expected; same breakdown |
| B Comparison of 4 + performance 1Y/3Y + 1Y history + 3Y/5Y + repeat | 72 | 64 | −11 % | history output identical (`selectiveHistoryMatchesTheFullBundle`) | 1Y history after the comparison 12 → **4**; 3Y + 5Y 0 → 0 |
| B′ 1Y history, 4 cold companies | 56 (16 statement requests) | 8 (4 statement requests) | −86 % | identical | 4 profile lookups for names remain |
| C Premium research after a comparison (3Y + 5Y, detailed summary, AI question) | 0 extra | 0 extra | — | balance/cash fields present in detailed summary | preserved |
| D Screener first hour (catalog + warm-up 25/h) | 328 (3 + 25 × 13) | 278 (3 + 25 × 11) | −15 % | screener-set metrics = full-bundle metrics (US + TSX) | searches 0; coverage reported "25 of 150" |
| D24 Screener 150, 24 h, 10 users/h | 1,953; coverage peaks **75**, **0 from hour 16** | 4,353; coverage 25…150 by hour 6, **150 every hour after** | +123 % (restores intended behaviour) | expired data never screened | re-warm 6 requests/company (statements cached 24 h) |
| D24 with the pre-Phase 3 512-entry cache | — | 6,603; ≈ 5,260 evictions (varies ±10 with concurrent timing) | (+52 % vs 4,096) | same coverage | justifies the larger cache |
| D24 with 20 companies failing in hours 12–13 | — | 4,353; 120 failed responses; coverage 150 | — | those 20 stay evaluated with statements + stale TTM statements, P/E unavailable; retry ≤ 1 h | no retry storm |
| D24 with budget 10/h | — | 2,043; coverage **60 of 150** | — | search warning states partial coverage | never claims full coverage |
| E 50 users, 10 overlapping symbols | 150 | 148 | — | — | 1 request per dataset per symbol (single flight); the 2 TSX symbols skip daily closes because the mock's profile (CAD) and statements (USD) differ — correct behaviour |
| F Saturday: fundamentals t/+10/+20 min + quote every minute for 20 min | 39 (TTM repeats 4, quotes 23) | 16 (TTM repeats **0**, quotes 4) | −59 % | provider timestamps unchanged | |
| F US holiday (Thanksgiving) | 39 | 16 | −59 % | same | |
| F open market (Wednesday) | 39 | 37 (TTM repeats 2) | −5 % | ratios refresh every 15 min (was 5) | quotes unchanged (30 s) |
| G Earnings: report after close, filing 4 h later, 1 h outage, correction; viewer every 30 min ~2 days | 355; new quarter visible **19 h** after publication | 253; visible **30 min** after publication | −29 % total; statements +14 (30 → 44) | no premature claim; outage shown as STALE with original date; correction waits for the normal 24 h | 12 loads got the 2 h lifetime; 1 Finnhub request either way; total falls mostly from market-aware TTM ratios |
| H Screener expiration regression (15 companies, 25/h) | 15 → **0** after 7 h | 15 → **15** | — | — | first warm-up 168, re-warm 90 (6/company) |

## 4. Screener cost and coverage (150 companies)

- Universe: 150 (50 NASDAQ, 50 NYSE, 50 TSX; exchange-qualified `.TO`), as selected by FMP's company screener (not claimed to be the largest).
- Cold requests per company: 11 (10 datasets + quote); re-warm while statements are cached: 6.
- Warm-up per hour: ≤ 25 companies (`SCREENER_FUNDAMENTALS_PER_HOUR`) → ≤ 278 requests in the first hour, 275 while filling, 150 at steady state.
- Coverage: full after 6 h; stays 150 at hourly sampling with 25/h (= U / T exactly; any missed hour or failure leaves companies "not evaluated"
  briefly, and searches say so). With 10/h coverage stays at 60.
- Measured 24 h per instance: 4,353 requests (first day). **Modeled** steady state: 150 × 4 re-warms × 6 + 150 × 11 daily statement reloads ≈
  5,250 requests/day/instance; 2 instances ≈ 10,500; 5 instances ≈ 26,000 (caches and budgets are per instance). Weekends: TTM ratios live up to
  6 h, so re-warms cost less (not measured over a weekend). Provider prices/plan limits unknown — no dollar figures.
- Cache: 150 × ~10 long-lived entries ≈ 1,500 + other screens; 4,096 entries, 0 evictions in D24; memory ≈ 10–40 MB worst case (estimate).
- Budget exhausted: no further upstream loads that hour (`screener.warm.budgetReached`), companies remain "not evaluated", resumes next hour.

## 5. Correctness verification

- **Periods/alignment**: selective history output equals the full bundle for 1Y/3Y/5Y; quarterly and annual entries separate; history rows of
  unrequested statements have null balance/cash fields (never zero).
- **Currencies**: forward P/E compares the quote with the listing currency; TSX + USD estimates stay unavailable (tested).
- **Missing values**: expired screener data counts as "not evaluated", never as missing metrics; all-failed loads aren't held; failures not cached
  as empty history; stale only within limits and never after 402/403.
- **Timestamps**: `retrievedAt` = oldest provider retrieval; unchanged by cache hits (tested with a fake wall clock); stale data keeps the original
  date (Company Financials and Comparison tested exactly).
- **Premium**: free 5Y → 403 with 0 provider requests; public 3Y → 401; existing route suites for history, research, AI and entitlements pass.
- **MOCK/REAL**: signals and market-aware lifetimes exist only in `realDataSources`; new accessors fall back to fixtures (`Phase3MockSeparationTest`:
  zero upstream requests, no stale labels); MOCK route tests still assert zero upstream.
- **Apps**: one shared presenter (`FinancialStatementsPresenter.staleNotice`, also used by the older `FinancialsPresenter.refreshMessage`) feeds the
  Compose and SwiftUI Financials screens; comparison/history notes are server text both apps already render. Not checked visually on a device:
  MOCK never produces STALE.

## 6. Earnings-aware refresh: fix made during verification

The original rule treated a row ending ≤ 105 days before the announcement as the reported period. For a fast reporter (quarter ends Sep 30,
announced Oct 9) the *previous* quarter (Jun 30, 101 days) passed, so the new quarter could stay invisible for 24 h. New rule
(`EarningsStatementSignals.covers`): exact `periodEnd` when the source gives one; otherwise the newest period end cached when the report arrived
(or the first load after it) is a baseline and only a strictly newer period counts. Worst case (filing already present at the first load) is extra
2 h polling for that one company for ≤ 10 days, never a false "covered". Tests: fast reporter, 52/53-week period end, nothing cached at report
time, filing after the window (falls back to 24 h, ≈ 12 refreshes/day inside the window), corrections (24 h).

## 7. Validation (exact commands and outcomes, 2026-10-09)

| Command | Outcome |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` | BUILD SUCCESSFUL: core JVM **414/0**, core iOS simulator **414/0**, server 437/0 (3 skipped), shared Android host **55/0**, shared iOS simulator **49/0**, `assembleDebug` OK |
| `./gradlew :server:test` (after adding `Phase3MockSeparationTest`) | **438 tests, 0 failures, 3 skipped** (includes `Phase3AuditBenchmarkTest`) |
| `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED** |
| Pre-Phase 3 baseline: audit benchmark + 24 h screener test on `e4ba2be` (temporary worktree, removed) | reproduced the audit numbers (§2) |

Not run: device/simulator walkthrough of the stale notice (MOCK can't produce STALE), TalkBack/VoiceOver, REAL providers.
The known flaky `PracticeServiceTest.concurrentOrdersCannotOverspendOrBypassTheLimit` passed in both server runs.

## 7a. Readiness

**Phase 3: ready for Phase 4.** All six milestones are implemented and verified by measured benchmarks and tests on every platform suite;
one defect found during verification (3D coverage rule) is fixed and covered. Remaining items (§8) are documented limitations, not blockers;
the production-security track (D1/D2) and per-instance multiplication are Phase 4 scope.

## 8. Remaining limitations

- Caches, budgets, single flight and earnings signals are per instance; no cross-instance coordination (D3/D4).
- Stale fallback not implemented for company profiles and daily closes (no freshness field on those responses).
- Watch-data/movement 60 s quote caches and the screener's 6 h record lifetime aren't market-aware; Comparison/screener held fundamentals don't
  use earnings signals (Company Details/Financials and history do).
- Corrections have no reliable signal (normal 24 h lifetime).
- Weekend and multi-day screener costs are modeled, not measured; production traffic, hit rates and provider plan limits unknown.
- Security track open: public-route client identity (D1/D2), durable AI quotas, global provider budgets.

## 9. Rollback

`MARKET_AWARE_TTL=false`, `EARNINGS_AWARE_STATEMENTS=false`, `SCREENER_UNIVERSE_LIMIT=100`, `FMP_DATASET_CACHE_ENTRIES=512`; or revert the commit
(no data migrations; caches are in memory; the new JSON fields are optional).
