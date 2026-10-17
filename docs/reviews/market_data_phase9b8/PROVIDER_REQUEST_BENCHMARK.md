# Provider request benchmark — Phase 9B.8 (MockEngine; `captures/phase9b8-fundamentals-benchmark.txt`)

`FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage` over an FMP base (prices/quotes from FMP). FMP column = quote reads by the bundle (upper
bound: the real FMP adapter caches quotes too).

| Scenario | Alpha Vantage | SEC | FMP | Finnhub |
|---|---:|---:|---:|---:|
| Company Details fundamentals, cold | 5 | 0 | 1 | 0 |
| … warm (annual + quarter) | 0 | 0 | 0 | 0 |
| 100 simultaneous opens of one company | 5 | 0 | 1 | 0 |
| 1,000 users across 20 companies | 100 | 0 | 20 | 0 |
| Comparison history + Discover + AI input after Details | 0 more | 0 | 0 | 0 |
| Discover screener fundamentals, 155 companies (only if the warm-up is enabled) | 775 | 0 | 155 | 0 |

Before (FMP bundle, Phase 3 benchmark): ≈ 11–14 FMP dataset requests per cold company. After: 5 Alpha Vantage requests per company per day
(statements shared with Comparison history), 0 FMP datasets. Recommendation: keep `SCREENER_FUNDAMENTALS_PER_HOUR=0` on a personal plan
(the warm-up would spend ≈ 775 requests). Alpha Vantage-primary prices/quotes (9B.7 benchmark) are unchanged.
