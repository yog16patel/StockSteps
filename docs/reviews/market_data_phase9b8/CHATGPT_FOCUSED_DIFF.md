# Focused diff — Phase 9B.8 (commit on `a3aa026`)

## 1. Quarterly basis check — `AlphaVantageFundamentalsMapper.quarterBasis`
```kotlin
close(sum(Q1..Q4), FY) -> standalone++      // quarters add up to the year (±1 %)
close(Q4, FY)          -> return YEAR_TO_DATE
else                   -> return UNVERIFIED  // one disagreeing year refuses the statement's quarters
```
`history()` drops quarterly income/cash-flow values unless STANDALONE. Risk: a restated quarter can make a year disagree → quarters
refused (safe direction). Tests: `quarterlyCashFlowIsUsedOnlyWhenVerifiedStandalone`.

## 2. Metrics only through the engine — `AlphaVantageFundamentalsMapper.map`
```kotlin
"revenueGrowth" to fromMetric(E.growth(Metric.REVENUE_GROWTH, REVENUE, latest, prior), latest)
"roe"           to fromMetric(E.returnOn(Metric.RETURN_ON_EQUITY, fy, fyPrior), fy, basisLabel = "annual")
"debtEquity"    to fromMetric(E.debtToEquity(sheet), sheet)                 // TOTAL_DEBT preferred when reported
"eps"/"pe"/…    to unavailable(NO_EPS) / unavailable(NO_SHARES)            // never estimated
```
`availability(MetricStatus)` maps engine statuses onto the existing enum (no new values for older apps). Tests: hand-calculated values,
unavailable-with-reason, missing ≠ zero, negative equity/losses, contract key parity.

## 3. Trailing dividends — `FinancialMetricsEngine.trailingDividendPerShare/Yield`
```kotlin
records.isEmpty() -> MISSING_INPUT; no record before the window -> INSUFFICIENT_HISTORY
!splits.verified || split in window -> SPLIT_UNVERIFIED
yield: usable price, same currency, price date within 7 days of the window end
```
Tests: `dividendsNeedCoverageASplitFreeWindowAndAUsablePrice`.

## 4. Provider: shared statements, no fallback, bundle cache — `AlphaVantageFundamentalsProvider`
```kotlin
override suspend fun getFundamentals(symbol, period) = bundles.getOrLoad("$SYMBOL:$period", 15 min) { build(symbol, period) }
val income = av.statement("INCOME_STATEMENT", symbol)          // required; failure propagates (typed)
optional(...) { av.statement("BALANCE_SHEET", symbol) } …       // refused → datasets[...] = TEMPORARILY_UNAVAILABLE
```
Never calls `base.getFundamentals`. Test: `theProviderSharesStatementsAcrossFeaturesAndNeverFallsBack` (100 concurrent → 5 requests).

## 5. Wiring, gate and AI — `Application.kt`, `MarketDataAccessGate.kt`
```kotlin
val stocks = if (fundamentalsAv) AlphaVantageFundamentalsProvider(avClient!!, baseStocks, avSessions) else baseStocks
personalAlphaVantage = alphaVantage != null && "alphavantage" in activeProviders && mode == PERSONAL
aiOnMarketData = !avInUse || registry.rights("alphavantage", …).aiProcessing == VERIFIED_ALLOWED   // → Gemini off for market data
```
One Alpha Vantage client (one cache and lane) for prices and fundamentals. Tests: gate fundamentals-only case, startup validation.

## 6. Quote label fix — `PriceSeriesRules.quoteDelay`
A quote stamped at the expected session's close is END_OF_DAY at any hour (was STALE during after-hours/the next session). Affects only
code that calls `quoteDelay` (9B/9B.8 marketdata package). Tests: `PriceBoundaryTest`.
