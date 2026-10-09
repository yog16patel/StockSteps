# Financial API — Phase 3 implementation plan (proposal; nothing here is implemented)

Based on `FINANCIAL_API_PHASE3_AUDIT.md` and `FINANCIAL_API_PHASE3_COST_MODEL.md`. All new components below are **proposed**.

## 1. Target architecture

```mermaid
flowchart TB
  subgraph Apps["Android (Compose) / iOS (SwiftUI) — unchanged API contracts"]
    UI[Screens] --> KMP[KMP presenters / StockStepsApi]
  end
  KMP --> R[Ktor routes]
  subgraph Server
    R --> D[CompanyDetailsService / FinancialsService\nfull bundle — unchanged]
    R --> C[ScreenerService.compare / ComparisonHistoryService\nComparisonResearchService / ComparisonAiService]
    R --> SC[ScreenerService catalog/search]
    R --> GR[LearningService → CompanyDetailsService]
    D --> FL[FmpFundamentalsLoader\n• load(symbol, period) — existing\n• statementHistory(symbol, period, statements) — PROPOSED\n• screenerSet(symbol) — PROPOSED]
    C --> FL
    SC --> W[Warm-up — PROPOSED fix: re-warm expired,\nbudget-aware, closed-market TTL]
    W --> FL
    FL --> CFC[(CompanyFinancialCache — existing\nper-key single flight, cooldowns)]
    FP[FreshnessPolicy — PROPOSED\nmarket session (UsMarketCalendar / TsxMarketCalendar)\n+ earnings evidence (EarningsService)] -.TTL.-> CFC
    CFC -->|miss| A[apiCall — existing ProviderCalls metering]
    B[ProviderRequestBudget — existing, extended per provider — PROPOSED] -.gate.-> A
  end
  A --> FMP[(FMP)]
  A --> FH[(Finnhub)]
  A --> BOC[(Bank of Canada)]
```

Reuse happens at `FmpFundamentalsLoader` dataset keys: Company Details, Comparison, Screener and Guided Research all read the same
`endpoint:SYMBOL:period:limit` entries; the proposed accessors only request subsets of those same keys, so they never create new
variants. No new service, repository layer or infrastructure is introduced; route responses keep their shapes.

## 2. Milestones (recommended order)

### 3B-0 Screener re-warm fix + cache capacity (first: correctness)
- **Problem**: expired screener fundamentals are never reloaded; financial filters evaluate 0 companies after ≈ 12–18 h.
- **Evidence**: `ScreenerService.warm()` filter `fundamentals[it.symbol] == null && it.symbol !in loading` (`server/.../screener/ScreenerService.kt`);
  `cachedFundamentals()` treats entries older than `recordTtl` as absent; measured 15 → 0 (`Phase3AuditBenchmarkTest`).
- **Solution**: select symbols whose cached fundamentals are missing **or expired** (`now() - at >= recordTtl`), oldest first; drop expired
  map entries; raise the FMP dataset cache capacity (`FmpStockProviderRepositoryImpl.financialCache`) to fit the universe (≈ 3,500
  entries; configurable `FMP_DATASET_CACHE_ENTRIES`), with the memory estimate documented.
- **Modules**: `ScreenerService`, `FmpStockProviderRepositoryImpl`. **API reduction**: negative (restores intended re-warm cost);
  paired with 3B-1 to keep it affordable. **Complexity**: low. **Risks**: higher steady-state cost; memory.
- **Tests**: fake-clock test (re-warm after expiry restores coverage; budget respected; oldest first). **Acceptance**: coverage stays at
  `min(U, b × T)` indefinitely; requests per hour ≤ `b × k`.

### 3A Targeted selective loading (statement accessors)
- **Problem**: income-only callers trigger the full bundle (1Y history 12 requests for 4 companies instead of 4).
- **Evidence**: `ComparisonHistoryService.fundamentalsOf` → `CompanyFinancialService.getFundamentals(symbol, period)` → `FmpFundamentalsLoader.load`.
- **Proposed interface** (on the existing repository; MOCK fixture implements it from its stored fundamentals):
  ```kotlin
  enum class Statement { INCOME, BALANCE, CASH_FLOW }
  // StockProviderRepository (default = full load, so other implementations keep working)
  suspend fun statementHistory(symbol: String, period: String, statements: Set<Statement>): StatementHistory
  data class StatementHistory(val rows: List<FinancialPeriodStatement>, val availability: Map<Statement, FinancialAvailability>, val retrievedAt: String)
  ```
  FMP implementation: the same `dataset()` keys (`income-statement:SYM:quarter:24`, `…:annual:6`, `balance-sheet-statement:…`,
  `cash-flow-statement:…`), mapped with `FmpFundamentalsMapper.history(...)` (empty lists for statements not requested).
- **Consumers**: `ComparisonHistoryService` (1Y → INCOME quarterly; 3Y/5Y → INCOME annual), `ComparisonResearchService` detailed summary
  (INCOME + BALANCE + CASH_FLOW annual). Not Details/Financials (they display everything).
- **Reduction**: 1Y history −67 %; 3Y/5Y opened cold −85 %. **Complexity**: low–medium. **Risks**: missing-field regressions in history
  rows (balance/cash fields null when not requested — only income fields are read by `HistoricalComparisonEngine`).
- **Tests**: request-count tests (1Y = 1 request per company); equality of `HistoricalComparison` output vs today for MOCK fixtures;
  premium checks still before provider calls. **Acceptance**: same API output; fewer requests; no change for Details.

### 3B-1 Screener request optimization
- Screener dataset set: a `screenerSet(symbol)` path that skips historical `ratios` and takes price/currency from the universe row
  instead of a quote + profile (forward P/E currency check uses the universe currency) — **13 → 10** per cold symbol; keep every dataset
  that changes metric availability (income-TTM, cash-flow-TTM, dividends, shares).
- Budget scaled to the universe (`b ≥ U / T`) or a smaller default universe (decision D6); closed-market `T` = 24 h.
- Count warm-up against `ProviderRequestBudget` (`screener-warmup` feature) so operators can cap it.
- Bulk endpoints only after plan and licensing confirmation (D7, D3).
- **Tests**: per-symbol request count; filters produce the same matches as the full bundle for MOCK fixtures; budget cap.

### 3C Market-aware freshness
- **Proposed** `FreshnessPolicy` (one object, server): `ttl(dataType, symbol, now)` using `UsMarketCalendar` for US listings and the
  existing `TsxMarketCalendar` (`brief/BriefSessions.kt`) for `.TO`/TSX (reuse `PriceReactionEngine`'s calendar selection).
- Apply via the existing `resultTtl`/`ttl` arguments: `FmpFundamentalsLoader.dataset` (ratios-TTM/key-metrics-TTM: 15 min open, until next
  open when closed), `FmpStockProviderRepositoryImpl.getQuote` and `WatchMarketData` (until next pre-open when closed), intraday bars
  (until next open), daily closes (until next close + lag).
- **Reduction**: F scenario 4 → 0 repeats; modeled 4,000–57,600 per weekend per instance. **Risks**: wrong calendar for dual listings
  (always choose by the exchange-qualified symbol); holidays (calendars already encode them). **Tests**: clock-driven tests for open,
  pre-market, after-hours, weekend, US holiday, TSX holiday, early close. **Acceptance**: no ratio/quote refresh while the listing's
  exchange is closed; provider timestamps unchanged.

### 3D Earnings-aware statement invalidation
- **Proposed**: `FreshnessPolicy.statementTtl(symbol)` shortens to 1–2 h (max 10 days) when `EarningsService` history shows a reported
  event (`actual != null` or report date passed) whose `periodEnd` is newer than the cached statements' newest `date`; reverts when a row
  with that `periodEnd` arrives. Uses `CompanyFinancialCache.invalidate` for immediate refresh on first evidence.
- **Reduction**: none (adds ≈ 12–24 requests per reporting company per quarter); **benefit**: staleness ≤ 2 h instead of ≤ 24 h.
- **Tests**: scenario G with fake clock (publication delay, late filing, correction). **Acceptance**: new quarter visible within 2 h of
  provider availability; no polling for symbols without a recent report.

### 3E Stale data and failure resilience
- Serve last good statements/profiles/daily closes when the provider fails (429/5xx/403), labelled with `retrievedAt` and an additive
  `freshness` field (`FRESH | CACHED | STALE`), mirroring `EarningsService.lastGood`; bounded age (e.g. 2× TTL, statements 7 days).
- Quotes/TTM ratios: stale only when the exchange is closed.
- Apps: show the existing "as of" text; add a small "Not updated recently" label where `freshness == STALE` (Compose + SwiftUI).
- **Tests**: failure scenarios with fake providers; JSON compatibility (old clients ignore the new field).

## 3. Production-security blockers (separate track)
1. **Client identity for public routes** (D1 in `FINANCIAL_API_PHASE3_DECISIONS.md`): trusted-proxy hop configuration verified on the
   deployed topology, then per-client + per-route global budgets; App Check for app traffic; sign-in for AI explanation routes.
2. **Durable AI quotas** for Brief/Earnings (reuse `ComparisonAiQuota` / `updateAiUsage`).
3. **Global provider budgets** per provider (per minute/day) in `ProviderRequestBudget`, fed by plan limits.

## 4. Test strategy
- MOCK/MockEngine only; fake clocks (`CompanyFinancialCache(now)`, `ScreenerService(clock)`, `FmpFundamentalsLoader(cache)`).
- Extend `Phase3AuditBenchmarkTest` scenarios into assertions per milestone (before/after counts).
- Keep existing suites green: `./gradlew :server:test` (and `:core:jvmTest` if shared models change in 3E).

## 5. Rollback
Each milestone is a small, independent change behind existing constructor parameters: revert the commit, or set the TTL policy to the
current constants (`FreshnessPolicy` returning today's values), or route `ComparisonHistoryService` back to `getFundamentals`. No data
migrations; caches are in memory.

## 6. Acceptance criteria (Phase 3 overall)
Screener coverage stable over 24 h with bounded requests; 1Y history 1 request per cold company; zero ratio/quote refreshes while the
listing's exchange is closed; new statements visible ≤ 2 h after provider availability for reporting companies; stale data always
labelled; all existing tests pass; no API contract breaks; MOCK makes no network calls.
