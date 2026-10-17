# ChatGPT review package — Phase 9B: market-data cost, licensing and provider architecture

## 1. Phase and Git baseline
- Phase 9B (9B.1 inventory/licensing/architecture audit → 9B.2 SEC EDGAR adapter → 9B.3 metrics engine → 9B.4 price boundary, caching,
  quotas → 9B.5 Discover optimisation and cost simulation → 9B.6 regression and package).
- Base: `main` = `origin/main` = `e69626b` ("Polish Android navigation motion for UI/UX Refinement Phase 8E …", pushed by the parent
  session while 9B ran; 9A = `945601b`). Under review: the commit "Add the Phase 9B market-data architecture with a fail-closed licensing registry, …" (server + docs only; pushed).
- Present and committed: 9A security (central auth, route table, per-class limits, Retry-After) and 8E navigation motion; 7A–7E market-data
  work. The Phase 7E.1 Discover quota fix existed only as benchmark captures in worktree `discover-quota-fix-5631dc` (no code on any
  branch) — re-implemented here.

## 2. Changes implemented (server only; no screen, route or mobile-model change)
- **Licensing as code, fail closed** (`marketdata/DataRights.kt`): classes, statuses, channels (mobile ≠ web), provenance, registry from the
  rights matrix, `STOCKSTEPS_LICENSE_MODE` (development default; commercial = only verified datasets), startup report of uncleared datasets.
- **Deterministic source selection and fallback** (`SourceSelection.kt`): never REAL→MOCK, never mixed adjustment basis, stale only as a
  labelled last resort, no fabricated values, one attempt per candidate (402/403/429 never looped), provenance can't be relabelled.
- **SEC EDGAR adapter** (`marketdata/sec/`): ticker ↔ CIK, companyfacts, submissions; fiscal periods by dates, restatements (latest vs
  original), amended flags, YTD never used as quarters, Q4 derived for flows only, share-class ambiguity, unit/currency normalisation;
  fair access (identifying User-Agent required, `sec` lane 5/s ≤ SEC's 10/s, bounded payloads, cached with single flight, cooldowns).
  **Not wired to routes; no live SEC request made.**
- **Pure metrics engine** (`marketdata/metrics/`): growth, margins, EPS/TTM, P/E, P/S, D/E, current ratio, ROE, ROA, FCF, dividend yield,
  exact decimals, null-with-reason for every unsupported case. Not wired.
- **Price boundary** (`marketdata/Prices.kt`): `DailyPriceSource`, `PriceSeries` with exchange/zone/currency/basis/provenance, FMP adapter
  (basis UNVERIFIED), EOD vs stale labelling, quote delay classes (never real-time), basis compatibility.
- **Discover daily cap** (behaviour change on REAL): ≤ `SCREENER_ENRICHMENT_PER_DAY` (default 100) real upstream requests per UTC day;
  closes before profiles; only due parts fetched.
- Startup validation for the new settings.

## 3. Affected files
- server main: `marketdata/{DataRights,SourceSelection,Fundamentals,Prices}.kt`, `marketdata/sec/{SecCompanyFacts,SecEdgarClient}.kt`,
  `marketdata/metrics/FinancialMetricsEngine.kt` (new); `httpclient/NetworkUtils.kt`, `service/ProviderBudget.kt`,
  `screener/CuratedScreenerUniverse.kt`, `appconfig/StartupConfiguration.kt`, `Application.kt` (modified).
- server tests: `marketdata/{SecFixtures,SecEdgarParsingTest,SecEdgarClientTest,FinancialMetricsEngineTest,SourceSelectionAndLicensingTest,
  PriceBoundaryTest,MarketDataConfigurationTest}.kt`, `screener/DiscoverDailyBudgetTest.kt`, `service/MarketDataPhase9bBenchmarkTest.kt`
  (new); `screener/CuratedDiscoverTest.kt`, `CuratedDiscoverBenchmarkTest.kt` (pin `perDay = null`).
- docs: this directory (17 files), `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`.
- core / shared / Android / iOS: **none**.

## 4. Architectural decisions
- New package beside the existing repositories instead of a rewrite; existing interfaces stay the per-dataset interfaces.
- `apiCallText` duplicates `apiCall`'s classification rather than refactoring the hot path used by every FMP/Finnhub call.
- SEC limits are published, so they don't block `ProviderBudgetConfig.verified` and can't be configured above the ceiling.
- Discover cap counts real upstream requests through `RequestCost` (reserve, then settle) — exact and concurrency-safe per instance.
- Licence gate is report-only in 9B; enforcement is an owner decision (D-9B-7).

## 5. Test and build results
See `CHATGPT_TEST_RESULTS.md` (sequential, `--rerun`).

## 6–7. Android / iOS verification
No UI or contract change (backend only): no contact sheets (`AI_REVIEW_WORKFLOW.md` §3.2). Builds and shared suites re-run; device checks
NOT RUN (nothing to see).

## 8. Known issues — `KNOWN_LIMITATIONS.md`
Licences unresolved (launch BLOCKED); SEC adapter/metrics not wired and not run live; no REAL corporate actions; per-instance budgets;
Discover can't refresh all closes daily at 100/day.

## 9. Unverified scenarios
Live SEC requests (BLOCKED: needs the owner's `SEC_USER_AGENT` contact); REAL FMP behaviour (NOT RUN: key at its limit, no REAL calls by
rule); Firebase emulator suites (NOT RUN: no user-owned flow changed); devices (NOT RUN: no UI change).

## 10. Deviations
- No Alpha Vantage adapter: its free terms are personal/non-commercial and `TIME_SERIES_DAILY` free is 100 points — evaluated, not built.
- Metrics engine and SEC adapter delivered unwired (wiring changes screen values → owner decision).
- Discover enrichment now fetches only due parts and prioritises closes (needed to make the cap usable; benchmarked).

## 11. Owner decisions — `OWNER_DECISIONS.md` (D-9B-1 … D-9B-13)
Most important: FMP display licence + competing quote; enable SEC statements; Discover cap value; corporate actions; licence enforcement.

## 12. Links
`FEATURE_DATA_INVENTORY.md`, `PROVIDER_COMPARISON.md`, `LICENSING_RIGHTS_MATRIX.md`, `PROVIDER_ARCHITECTURE.md`, `SEC_EDGAR_INTEGRATION.md`,
`FINANCIAL_METRICS_ENGINE.md`, `PRICE_HISTORY_STRATEGY.md`, `CACHE_AND_QUOTA_POLICY.md`, `DISCOVER_ENRICHMENT_AUDIT.md`,
`PORTFOLIO_PRACTICE_DATA_CORRECTNESS.md`, `COST_SIMULATION_100_1000_10000.md`, `ANDROID_IOS_CONTRACT_REVIEW.md`, `CHATGPT_FOCUSED_DIFF.md`;
Phase 6 audit `docs/REAL_MARKET_DATA_PROVIDER_AUDIT_PHASE6.md`; benchmark copy `captures/phase9b-market-data-benchmark.txt` (git-ignored).
