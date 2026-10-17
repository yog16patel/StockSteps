# Company Details data flow — Phase 9B.8

```
app → /api/v1/stocks/{s}/details ─┬─ profile ─────── StockService (24 h) → stockProvider.getProfile   (AV OVERVIEW premium | FMP)
                                  ├─ quote ───────── StockService → quoteProvider.getQuote            (AV GLOBAL_QUOTE | FMP)
                                  ├─ chart ───────── PriceChartService (until next close) → priceHistory (AV daily raw | FMP light)
                                  ├─ fundamentals ── CompanyFinancialService → stockProvider.getFundamentals
                                  │                    FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage:
                                  │                    AlphaVantageFundamentalsProvider (bundle cache 15 min)
                                  │                      → AlphaVantageClient: INCOME/BALANCE/CASH_FLOW/DIVIDENDS/SPLITS (24 h, single flight)
                                  │                      → base.getQuote (dated price) → AlphaVantageFundamentalsMapper → FinancialMetricsEngine
                                  │                    fmp (default): FmpStockProviderRepositoryImpl bundle
                                  ├─ valuation ────── ValuationService: P/E history = charts (closes) + FMP quarterly EPS (raw-basis rule with AV closes)
                                  └─ news, earnings ─ Finnhub
/api/v1/compare*, comparison research, AI comparison input, Discover screener fundamentals → the same stockProvider (same bundle, same cache)
```
Licence gate (`MarketDataAccessGate`) runs before all of these whenever Alpha Vantage serves any dataset (personal allowlists) or in
commercial mode (fail closed). Gemini receives no market data while Alpha Vantage is in use (AI-processing right unverified): comparison,
brief, earnings and movement AI use their deterministic fallbacks.
