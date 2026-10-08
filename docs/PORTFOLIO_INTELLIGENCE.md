# Portfolio Intelligence & Advanced Performance Analytics

Portfolio → **Insights** (a pushed screen, not a new tab) on Android (Compose) and iOS
(SwiftUI). Both render the same shared Kotlin `PortfolioAnalyticsPresenter` state.
Every number is computed from the existing portfolio ledger by the existing
`PortfolioEngine` (`replay`/`value`); there is no second ledger or valuation engine.

## Architecture

| Layer | Files |
| --- | --- |
| Models (serializable) | `core/.../portfolio/analytics/AnalyticsModels.kt` |
| Maths | `PerformanceMath.kt` (XIRR, dates), `PortfolioAnalyticsEngine.kt` |
| Insights + education | `PortfolioInsightsEngine.kt` (`PortfolioInsightsEngine`, `AnalyticsEducation`) |
| Display model | `InsightsPresentation.kt` (`InsightsFormatter` → `InsightsView`: all text, signs, accessibility labels) |
| Presenter | `PortfolioAnalyticsPresenter.kt`; entitlements in `EntitlementsRepository.kt` |
| Fixtures | `AnalyticsFixtures.kt` (20 scenarios, `BenchmarkCatalog`, `EntitlementFeatures`) |
| Optional AI | `AiExplanations.kt` (interface + consent model only; no provider, no calls) |
| Server | `server/.../userdata/PortfolioAnalyticsService.kt` (service, entitlements, benchmarks, routes); `PortfolioMarketService` shares its context |
| Android | `presentation/portfolio/PortfolioInsightsScene.kt`, `PortfolioInsightsScreen.kt`, route `PortfolioInsightsRoute` |
| iOS | `PortfolioInsightsViewModel.swift`, `PortfolioInsightsScene.swift`, `PortfolioInsightsScreen.swift`; bridge in `IosAccountClient` |

Route/Scene/Screen boundaries are preserved: the route is identity only, the Scene
acquires the presenter and wires navigation, the Screen receives state + callbacks.

## API

All under `/api/v1/me`, Firebase ID token required (MOCK accepts `mock-user:<uid>`).

- `GET /portfolio/accounts/{id}/analytics?period=1D|1W|1M|3M|1Y|3Y|5Y|ALL&benchmark=SP500|TSX|NASDAQ`
  → `PortfolioAnalytics`. Unknown period/benchmark → 400; an account the caller doesn't
  own → 404 (the uid comes only from the token). Default period 1Y; default benchmark is
  the TSX for CAD accounts, S&P 500 for USD accounts.
- `GET /entitlements` → `Entitlements` (tier, status, expiry, source, feature ids).
- `PUT /entitlements/debug` `{"tier":"PLUS","expired":false}` — **registered only in MOCK**.
  `EntitlementService` also refuses it outside MOCK and ignores any stored `debug` record
  in REAL.

Caching: results are cached server-side for 10 minutes per uid, account, **ledger revision**,
period, benchmark, tier and UTC day; any ledger edit changes the revision, so the key. Price
history, profiles and FX reuse the existing shared caches (no duplicate market requests
across Portfolio, Watchlist, Home and Insights). The client memory-caches for one minute
and falls back to the last saved result for the same revision and tier (labelled).

## Methodology

**Valuation inputs.** Daily closes in each holding's trade currency (last close on or
before the date, at most 5 days old to cover different exchange holidays). FX: Bank of
Canada daily USD/CAD (MOCK: fixed 1.35), last publication at most 7 days old. Today's
value uses today's quote only — a failed quote is missing, never yesterday's close.
A past date never uses today's FX.

**External flows** (never gains or losses): cash deposits, withdrawals, cash adjustments,
cash transfers, and in-kind transfers / opening positions at that day's close.
Deposits/withdrawals in another currency use that date's rate.

**Time-weighted return (TWR).** Daily-boundary chain linking with end-of-day flows:
`rᵢ = (Vᵢ − Fᵢ) / Vᵢ₋₁ − 1`, TWR = Π(1 + rᵢ) − 1. The first funding of an empty account
uses `Vᵢ / Fᵢ − 1`. Every flow date is a valuation boundary; between flows, long periods
sample up to 200 dates. Annualized only for periods ≥ 1 year.

**Money-weighted return (XIRR).** Flows (−start value, −external flows, +end value),
actual/365. The NPV is scanned from −99.9% to +10,000%; the rate is shown only if there is
exactly one sign change, then bisected. No rate, multiple rates, non-convergence, fewer than
two flows, or a period under a month → not shown, with the reason.

**Investment gain** = end − start − net external flows (includes dividends, fees, FX).

**Benchmark.** Index levels (`^GSPC`, `^GSPTSE`, `^IXIC`; price-return, labelled so),
converted to the reporting currency at each date's FX, normalized to 100 on the portfolio
index's first date, on the same dates. Missing data → gaps or UNAVAILABLE, never 0%.
REAL: provider daily closes via the shared chart cache. MOCK: the fixture's month of
recorded history extended backwards by a fixed formula, labelled as sample data.

**Allocation.** Denominator = holdings + positive cash ("long assets"); negative cash is
shown separately. Missing quotes/FX hide percentages (never renormalized over part of the
account). Asset class: `isEtf == true` → ETF; `isEtf == false` or a sector → stock;
otherwise Unclassified. ETFs are never assigned sectors (no look-through data); missing
sectors stay "Unclassified". FMP `isEtf`/`isFund` were added to `CompanyProfile`; MOCK fixture
ETFs (SPY, QQQ, XL*) are profiled as ETFs without sectors.

**Concentration.** Largest holding, top 3 (only if > 3 holdings), top 5 (only if > 5),
largest sector (classified holdings only) as shares of long assets; HHI = Σ wᵢ² and
effective holdings = 1/HHI over securities only. No risk score.

**Contribution** (per holding, reporting currency, period only — lifetime gains are never
used): `MVₑ·fₑ − MVₛ·fₛ − Σ invested·f_k + Σ income·f_k`. Split exactly into a local part
(`(ΔMV − invested + income)` in trade currency × end FX) and an FX part (the remainder).
Percent uses Modified Dietz average capital. "Cash, fees and other" = gain − Σ contributions.

**Dividends.** Recorded cash dividends + DRIP amounts, each at its payment date's FX;
reinvested dividends count once. By company / last 12 months / year. No forecasts.

**Currency exposure** is denomination exposure (holdings and cash by trading currency),
plus the period's FX effect (sum of contributors' FX parts).

**Health overview**: holdings count, largest holding, largest sector (Plus), foreign-currency
share, recorded history span, benchmark status. No single score.

**Insights.** Deterministic rules over the computed metrics, fixed priorities, at most one
per category, max 5, each with id, category, title, explanation, metrics, period, timestamp,
completeness, methodology key and destination. No advice or predictions (tested by regex).

## StockSteps+ tiers

| Free | Plus |
| --- | --- |
| Health overview, holding and currency allocation, largest holding, recorded dividend total, education | + performance (TWR/XIRR, periods), benchmark, sector/asset-class allocation, top-3/5/HHI/effective holdings, contributors, dividends by company/month/year, currency impact, advanced insights |

The server computes only what the tier includes and returns `locked` section ids; premium
numbers never reach a free client. Expiry downgrades analytics only — ledgers, holdings and
transactions are never gated. Storage: REAL `users/{uid}/meta/entitlements` (server-only,
already denied to clients by `firestore.rules`); MOCK in memory. No billing integration
exists yet: REAL users are FREE until a billing service writes that document.

Debug simulation: Settings → Development → "Simulated StockSteps+ plan" (Free / Plus /
Expired), shown only for a signed-in user with the MOCK backend in debug builds.

## MOCK scenarios (20)

`new-portfolio, single-holding, multiple-holdings, concentrated, diversified,
mixed-currency, dividends, deposits, withdrawals, partial-sales, outperforming,
underperforming, insufficient-history, missing-sectors, missing-benchmark, missing-fx,
partial-failure, free, plus, expired` — read-only, selected from the Insights screen in
MOCK. Ledgers trade at that day's generated close so ledger and prices agree; all numbers
come from the engine (tests verify gain = end − start − flows and contributions + other =
gain for each).

## Tests

- `core/.../analytics/PortfolioAnalyticsEngineTest.kt`: returns, deposits/withdrawals, sales,
  dividends/DRIP, fees, fractional shares, dated FX and FX split, missing/stale FX, insufficient
  history, periods, XIRR convergence (incl. a reference irregular example) and refusal
  (no root / multiple roots), benchmark normalization/holidays/currency/labelling/missing data/
  flow neutrality, allocation sums, ETF/sector/missing metadata, borrowed cash, partial quotes,
  concentration/HHI/effective N, empty portfolio, attribution (ranking, reconciliation, period
  vs lifetime, mid-period trades, missing data), all 20 fixtures, tier shaping, insights
  (determinism, priority, dedupe, no advice wording, metrics provenance), education, AI consent.
- `PortfolioAnalyticsPresenterTest.kt`: loading, period/benchmark reloads, per-user benchmark
  memory, locked sections, MOCK-only plan simulation, scenarios without network, failure and
  offline fallback, sign-out clearing (including no stale view/accounts after an owner change).
- `server/.../PortfolioAnalyticsRoutesTest.kt`: auth, ownership, free shaping (no premium
  numbers, no benchmark fetch), Plus, expiry, request-time expiry, debug isolation, parameter
  validation, cache by revision.
- `AccountDependenciesTest` (iOS) resolves the new bindings.

## Known limits / next

- No billing/subscription purchase flow; REAL entitlements need a billing service writing
  `users/{uid}/meta/entitlements`.
- Benchmarks are price-return indices (no total-return series available); the UI labels it.
- REAL index history depends on the provider returning `^GSPC`/`^GSPTSE`/`^IXIC` daily closes;
  if it doesn't, the comparison shows UNAVAILABLE. Not verified live in this pass.
- Historical closes are typically split-adjusted while the ledger records splits as
  transactions; periods spanning a split can misstate a holding's start value.
- Contribution percent (Modified Dietz) can differ slightly from TWR.
- MOCK FX is constant, so MOCK currency effects are zero; the fixtures exercise FX.
- AI explanations: interface and consent model only; no provider or UI entry point.
- Not done: live REAL acceptance test, device TalkBack/VoiceOver/Dynamic Type pass,
  visual check of the new screens on emulator/simulator.
