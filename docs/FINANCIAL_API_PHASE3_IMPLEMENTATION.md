# Financial API — Phase 3 implementation

Date: 2026-10-09. Builds on `FINANCIAL_API_PHASE3_AUDIT.md`, `…_COST_MODEL.md`, `…_DECISIONS.md` and `…_IMPLEMENTATION_PLAN.md`
(kept unchanged). Owner decision applied: **default screener universe = 50 NASDAQ + 50 NYSE + 50 TSX (150)**.
All numbers below are from MockEngine tests (REAL adapters, no network); no REAL provider calls were made.

## 1. Status

| Milestone | Status | Main change | Tests |
|---|---|---|---|
| 3B-0 | Done | `ScreenerService.warm()` reloads missing **or expired** fundamentals, never-loaded first then oldest, within `fundamentalsPerHour`; failed loads back off 30 min; all-datasets-failed loads aren't held as data; partial/stale loads refresh after 1 h; records expire with their fundamentals; maps bounded | `ScreenerWarmupTest` (7) |
| 3A | Done | `StockProviderRepository.statementHistory(symbol, period, statements)` (same FMP dataset keys); Comparison history loads income only; detailed research loads income + balance + cash flow; provider failures aren't cached as empty history | `SelectiveStatementsTest` (10) |
| 3B-1 | Done | Universe ≤ 50 per exchange (env `SCREENER_UNIVERSE_LIMIT`, default 50), honest description (no "largest" claim); screener warm-up uses `screenerFundamentals` (no historical ratios, no profile — listing currency from the universe; quote kept); FMP dataset cache 4,096 entries (env `FMP_DATASET_CACHE_ENTRIES`) | `ScreenerUniverseOptimizationTest` (7) |
| 3C | Done | `MarketFreshnessPolicy`: quotes / TTM ratios / intraday bars by the listing's session (US calendar; TSX calendar for `.TO`); rollback `MARKET_AWARE_TTL=false` | `MarketFreshnessTest` (9) |
| 3D | Done | `EarningsStatementSignals`: reported events already loaded by `EarningsService` shorten that symbol's statement lifetime to 2 h (10-day window) until the period appears; one invalidation per new report; disable `EARNINGS_AWARE_STATEMENTS=false` | `EarningsAwareStatementsTest` (7) |
| 3E | Done (statements/estimates) | Temporary failures (429/5xx/timeout/invalid) serve the last good statements (≤ 7 days) / estimates (≤ 2 days) labelled `STALE` with the original retrieval time; 402/403, TTM ratios and quotes never stale; additive `CompanyFundamentals.freshness` + `staleDatasets`; Financials notice on Android + iOS; history/comparison notes | `StaleFallbackTest` (6), core presenter test |
| Benchmarks (§10 of the brief) | **Not done** | `Phase3AuditBenchmarkTest` still measures the audit scenarios; before/after table not produced | — |

## 2. Measured request effects (tests)

| Path | Before | After | Source |
|---|---:|---:|---|
| Comparison 1Y history, 4 cold companies (statement requests) | 16 (full quarterly bundle in isolation; audit's 12 assumed annual income cached) | **4** | `SelectiveStatementsTest` |
| 3Y/5Y after a 3Y load | — | 0 extra | same |
| Cold screener company | 13 | **11** (10 datasets + quote) | `ScreenerUniverseOptimizationTest` |
| Screener coverage after expiry (15 companies, 25/h) | 15 → 0 | 15 → 15 (one reload per company per 6 h) | `ScreenerWarmupTest` |
| 150-company universe, 25/h, 24 h | — | coverage 25/50/…/150 in 6 h, then ≥ 125 at all times; ≤ 25 × 11 + 3 requests per hour | same |
| Fundamentals on a Saturday / US holiday, t, +10, +20 min | 4 repeats | **0** | `MarketFreshnessTest` |
| Same, open market | 4 repeats (5 min) | 2 (15 min) | same |
| Quote on a Saturday over 5 min | 10 | 1 | same |
| New quarter visible after publication (with earnings evidence) | ≤ 24 h | ≤ 2 h | `EarningsAwareStatementsTest` |

Modeled (not measured): 150 companies refreshed every 6 h ≈ 150 × 4 × k_r (≈ 6–7) + 150 × 11 daily ≈ 5,000–6,000 requests/day/instance;
multiply by the number of Cloud Run instances serving the screener (1×, 2×, 5×). Provider prices/plan limits are unknown — no dollar figures.

## 3. Cache capacity (§8)

Screener set per company ≈ 10 long-lived dataset entries (+ 30-second cooldown entries and a quote that expire quickly) → 150 × 10 ≈ 1,500.
Company Details/Financials ≈ 15 per viewed company. 4,096 leaves room for ≈ 170 other companies. Entry size ≈ 1–30 KB (dividends 100 rows,
24 quarters largest) → typically 10–40 MB worst case. Expired entries are trimmed first, then LRU; stale fallback only uses entries not yet trimmed.

## 4. Correctness notes

- Expired screener data is never used for filters (the company counts as "not evaluated"), never shown as missing values.
- Forward P/E in the screener set compares the quote with the listing currency (TSX → CAD, US → USD); a mismatch leaves it unavailable.
  Screener-set metrics equal full-bundle metrics in tests (US and TSX).
- Statement rows not requested keep balance/cash fields null (never zero). Annual/quarterly and statement sets have separate cache entries.
- StockSteps+ checks still precede provider work (tested: free 5Y → 403 with 0 requests).
- `retrievedAt` is now the **oldest provider retrieval** behind a result (cache hits don't change it).
- 3D evidence: Finnhub events have no period end, so "covered" = a row ending ≤ 105 days before the announcement (exact `periodEnd` when given).
  Corrections have no signal (24 h). Screener held fundamentals and the history service cache follow their own lifetimes (history cache also uses the signal).

## 5. Not done / limitations

- Phase 3 before/after benchmark (scenarios A–H table) not produced; `Phase3AuditBenchmarkTest` unchanged apart from call syntax.
- iOS `xcodebuild`, `:core:iosSimulatorArm64Test`, `:app:shared` tests not run after the shared-model/UI change (core JVM tests and Android compile pass).
- Stale fallback not implemented for profiles and daily closes (no freshness field on those responses; would need model + UI work).
- Watch-data / movement 60 s quote caches and closed-market screener record TTL unchanged.
- All caches, budgets and signals are per instance; no cross-instance coordination (D3/D4). Security track (D1/D2), durable AI quotas and
  global provider budgets remain Phase 4.

## 6. Rollback

`MARKET_AWARE_TTL=false`, `EARNINGS_AWARE_STATEMENTS=false`, `SCREENER_UNIVERSE_LIMIT=100`, `FMP_DATASET_CACHE_ENTRIES=512`; or revert the commit
(no data migrations; caches are in memory; the new JSON fields are optional).
