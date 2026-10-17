# Provider architecture — Phase 9B

Goal: different providers per dataset, chosen deterministically by licence, freshness, completeness, cost and availability, with explicit
provenance — **without a rewrite and without changing a mobile contract.** All new code is server-side in
`server/src/main/kotlin/org/example/stocksteps/marketdata/`; existing services and routes are untouched (they keep their interfaces:
`StockProviderRepository`, `StockQuoteProviderRepository`, `PriceHistoryProvider`, `MarketDataProvider`, `NewsProviderRepository`,
`EarningsCalendarSource`, `PortfolioFxSource`).

## 1. Building blocks (implemented)

| File | Purpose |
|---|---|
| `marketdata/DataRights.kt` | `DataClass`, `LicenseStatus`, `DisplayChannel`, `Dataset`, `PriceDelay`, `AdjustmentBasis`, `Provenance`, `Sourced<T>`, `DataRights`, `LicenseRegistry` (fail-closed matrix), `LicenseMode` (env `STOCKSTEPS_LICENSE_MODE`), `LicenseGate` |
| `marketdata/SourceSelection.kt` | `SourceCandidate`, `SourceRequirement`, `SourceSelector` (deterministic order + fallback rules), `Selection` with every attempt |
| `marketdata/Fundamentals.kt` | provider-independent `FinancialConcept`, `ReportedValue` (tag, filing, original value, amended, derived), `FinancialPeriod`, `CompanyFinancialHistory` |
| `marketdata/Prices.kt` | price boundary: `DailyPriceSource` (fun interface), `PriceSeries` (exchange, zone, currency, basis, provenance), `PriceObservation`, `FmpDailyPriceSource` (wraps the existing `PriceHistoryProvider`, basis UNVERIFIED), `PriceSeriesRules` (clean, EOD labelling, quote delay, basis compatibility) |
| `marketdata/sec/SecCompanyFacts.kt` | companyfacts parser + `SecPeriodBuilder` (fiscal periods, restatements, Q4 derivation) |
| `marketdata/sec/SecEdgarClient.kt` | SEC client: tickers, submissions, companyfacts; caching, cooldowns, User-Agent; mapping to the existing `FinancialPeriodStatement` |
| `marketdata/metrics/FinancialMetricsEngine.kt` | pure metrics engine |
| `httpclient/NetworkUtils.kt` | `apiCallText` (bounded body; same guard/record path as `apiCall`); `ProviderCalls` knows `sec` with bounded endpoint labels |
| `service/ProviderBudget.kt` | `sec` lane (300/min, burst 10, 2 concurrent; never above 600/min) |

Focused interfaces per dataset (not one generic interface): the existing repositories stay the interfaces for profile, quotes, statements,
news, earnings, FX; 9B adds `DailyPriceSource` (prices) and `SecEdgarClient.history` (reported fundamentals as `Sourced<CompanyFinancialHistory>`).
Symbol resolution: `SecTickerMap` (US listings, `BRK.B` = `BRK-B`). Economic indicators: no feature uses them; none added.

## 2. Selection and fallback rules (`SourceSelector`)

Order: licence status (VERIFIED_ALLOWED first) → freshness rank → completeness (desc) → cost rank → provider name. Same inputs, same order
(`orderIsDeterministic…` test). In COMMERCIAL mode uncleared candidates are removed.

Fallback never (each has a test in `SourceSelectionAndLicensingTest`):

| Rule | How |
|---|---|
| REAL → MOCK | a candidate whose `mode` differs from the requirement is skipped (`WRONG_MODE`) |
| unlicensed in commercial mode | `NOT_LICENSED` |
| mixed adjustment bases | `requirement.adjustment` must equal the candidate's basis (`ADJUSTMENT_MISMATCH`); `PriceSeriesRules.compatible` also refuses two UNVERIFIED series from different vendors |
| stale shown as current | a stale result is kept only if no candidate returns current data, and its provenance stays `stale = true` |
| fabricated zeros | a candidate without data is skipped (`NO_DATA`); nothing is synthesised |
| retry loops on 402/403/429 | one attempt per candidate per call (`RESTRICTED`, `RATE_LIMITED`); cooldowns stay in the source's own cache (`providerCooldown`) |
| hidden provenance | `check(result.provenance.provider == candidate.provider)`; `Selection.attempts` lists every skip |

## 3. How it plugs in (proposed, not wired in 9B)

1. **Fundamentals:** `CompanyFinancialService` gains an optional `SecEdgarClient`; with `FUNDAMENTALS_SOURCE=sec` (owner decision D-9B-2) the
   reported statements come from `SecEdgarClient.history` mapped by `toStatement` into the existing `StatementHistory`/`FinancialPeriodStatement`
   (same fields; `source = "sec"`). FMP stays the fallback in DEVELOPMENT only; in COMMERCIAL mode FMP is skipped until licensed.
2. **Ratios:** non-price ratios from `FinancialMetricsEngine` on SEC facts; price ratios only when the price's dataset is cleared
   (`derivedDisplayAllowed`).
3. **Prices:** `PriceChartService` and `PortfolioMarketService` keep `PriceHistoryProvider`; a licensed vendor is added as a second
   `DailyPriceSource` and chosen through `SourceSelector` with `adjustment` set by the caller (charts: the vendor's stated basis; Portfolio
   history: the same basis as its quantities — see `PRICE_HISTORY_STRATEGY.md`).
4. **Licence gate:** route-level enforcement (`LicenseGate.permits`) per section, returning the existing "unavailable" shapes (null values,
   typed errors) — no new error contract; copy decision D-9B-7.

Nothing in 1–4 changes a mobile model: they fill the same nullable fields.

## 4. What 9B deliberately didn't do
- No route, screen, styling or mobile model change; no new public endpoint.
- No Alpha Vantage adapter (free terms forbid this use; a written quote first).
- No Redis/Memorystore or shared cache; budgets stay per instance.
- No WebSockets/streaming.
- No removal of FMP or Finnhub (replacement coverage and rights not yet verified).
