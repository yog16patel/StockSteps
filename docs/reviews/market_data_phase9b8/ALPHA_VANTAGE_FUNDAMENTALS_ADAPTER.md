# Alpha Vantage fundamentals adapter — Phase 9B.8

Code: `marketdata/alphavantage/AlphaVantageFundamentals.kt` (`AlphaVantageFundamentalsMapper`, `AlphaVantageFundamentalsProvider`);
engine additions in `marketdata/metrics/FinancialMetricsEngine.kt` (TOTAL_DEBT in D/E, trailing dividends and yield); `Prices.kt`
(`quoteDelay` labels a quote stamped at the expected close as END_OF_DAY); wiring in `Application.kt`; validation in
`StartupConfiguration.kt`. Tests: `AlphaVantageFundamentalsTest` (9), `AlphaVantageFundamentalsBenchmarkTest`, updated
`PriceBoundaryTest`, `MarketDataAccessGateTest`, `AlphaVantageConfigurationTest`, `RawBasisValuationTest`, `FinancialMetricsEngineTest`.

- **Same client, cache and quota** as 9B.7 (`AlphaVantageClient`: single flight, cooldowns, `alphavantage` lane, HTTP-200 refusals). Five
  requests per company per day at most: INCOME_STATEMENT, BALANCE_SHEET, CASH_FLOW (shared with `statementHistory`), DIVIDENDS, SPLITS.
  Computed bundles are cached 15 min per symbol and period (`av-fundamentals`).
- **Required vs optional:** income is required (a refusal propagates, typed); balance, cash flow, dividends and splits degrade to
  `TEMPORARILY_UNAVAILABLE` in `datasets`, and the affected metrics are unavailable. Never answered from FMP.
- **Price:** the base provider's quote (FMP or Alpha Vantage), dated and labelled with `PriceSeriesRules.quoteDelay`; USD only for US
  listings; anything else → no price ratios.
- **Rules kept:** missing never zero; no USD/CAD mixing (currency mismatch → unavailable); annual and quarterly never mixed; quarterly
  values only when the statement's quarters are verified standalone (they add up to the fiscal year; a fourth quarter equal to the year =
  year-to-date → refused; unknown → refused); no EPS (basic ≠ diluted; adjusted ≠ GAAP unverified); no shares inferred from market cap;
  no estimates.
- **Rollout:** `FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage` (requires `ALPHA_VANTAGE_ENABLED=true` + key; works with FMP or Alpha
  Vantage prices); `fmp` (default) keeps the FMP bundle. Startup validation names settings only.
