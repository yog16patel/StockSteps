# Phase 7C — API request benchmark

Source: `server/src/test/.../userdata/PortfolioPracticeEodBenchmarkTest.kt` (MockEngine, fake clocks, no network, no keys); output from `server/build/phase7c-portfolio-benchmark.txt`.

- **Before:** the quote path (Phase 7A).
- **After:** the EOD path.
- **Counts:** upstream FMP `quote`, `profile` and `historical-price-eod/light` requests, and Bank of Canada requests.
- **Mock setup:** quotes are served as current at Friday's close for the "before" side.

| Scenario | Before quote | Before profile | Before EOD | Before BoC | After quote | After profile | After EOD | After BoC |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Portfolio, 5 holdings, one summary | 5 | 5 | 0 | 1 | 0 | 5 | 5 | 1 |
| Portfolio, 20 holdings, one summary | 20 | 20 | 0 | 1 | 0 | 20 | 20 | 1 |
| Portfolio, 5 holdings, 10 refreshes 1 min apart | 50 | 5 | 0 | 1 | 0 | 5 | 5 | 1 |
| 10 users holding the same stock, concurrent | 1 | 1 | 0 | 1 | 0 | 1 | 1 | 1 |
| Weekend: 5 holdings, a refresh every hour for 24 h | 120 | 5 | 0 | 4 | 0 | 5 | 5 | 4 |
| 5 holdings, one without a close, 10 refreshes over 1 h | 50 | 5 | 0 | 1 | 0 | 5 | 6 | 1 |
| 5 holdings, one refused (402), 10 refreshes over 1 h | 41 | 5 | 0 | 1 | 0 | 5 | 5 | 1 |
| Practice, 4 positions, overview 10 times 1 min apart | 31 | 4 | 0 | 1 | 0 | 4 | 4 | 1 |

Notes:
- **Profile requests:** unchanged on both sides (24 h); they give each holding's listing currency.
- **BoC requests:** unchanged (one 6 h window cache).
- **Close requests:** one per symbol until the next expected close (Phase 7B lifetime), shared across users, refreshes, Markets and
  charts. With 10 users holding the same stock, that is still 1 request.
- **Refreshes:** 10 refreshes of 5 holdings cost 50 quotes before and 0 quotes + 5 cached closes after. Over a weekend: 120 → 5.
- **"One refused (402)" row:** the mock refuses every endpoint for that symbol, so its profile is refused too. The refused close is
  cached 1 h (one request in the hour).
- **Live savings** depend on traffic; these are mock counts, not live measurements.
