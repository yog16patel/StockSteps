# Phase 7E — Discover API request benchmark

Source: `server/src/test/.../screener/CuratedDiscoverBenchmarkTest.kt` (MockEngine, fake clocks, no network, no keys); output from `server/build/phase7e-discover-benchmark.txt`. The curated column includes its background enrichment pass (`perHour = 500` here, so one pass covers all 155 companies; the default 60 spreads the same requests over ≈ 2.6 h).

| Scenario | FMP screener path, current plan (402) | FMP screener path, plan with screener | Curated universe |
|---|---|---|---|
| One cold open (catalog) | 1 / 0 / 0 / 0 / 0 / 1 | 3 / 0 / 0 / 0 / 0 / 3 | 0 / 155 / 155 / 0 / 0 / 310 |
| Cold open, then a warm open | 1 / 0 / 0 / 0 / 0 / 1 | 3 / 0 / 0 / 0 / 0 / 3 | 0 / 155 / 155 / 0 / 0 / 310 |
| Ten repeated opens | 1 / 0 / 0 / 0 / 0 / 1 | 3 / 0 / 0 / 0 / 0 / 3 | 0 / 155 / 155 / 0 / 0 / 310 |
| First page of 20 | 1 / 0 / 0 / 0 / 0 / 1 | 3 / 0 / 0 / 20 / 0 / 23 | 0 / 155 / 155 / 0 / 0 / 310 |
| Three pages | 1 / 0 / 0 / 0 / 0 / 1 | 3 / 0 / 0 / 60 / 0 / 63 | 0 / 155 / 155 / 0 / 0 / 310 |
| Filter by sector | 1 / 0 / 0 / 0 / 0 / 1 | 3 / 0 / 0 / 20 / 0 / 23 | 0 / 155 / 155 / 0 / 0 / 310 |
| Sort by market cap | 1 / 0 / 0 / 0 / 0 / 1 | 3 / 0 / 0 / 20 / 0 / 23 | 0 / 155 / 155 / 0 / 0 / 310 |
| Ten concurrent users (open + first page) | 1 / 0 / 0 / 0 / 0 / 1 | 3 / 0 / 0 / 20 / 0 / 23 | 0 / 155 / 155 / 0 / 0 / 310 |
| Provider 402 for profiles and closes, 10 opens over 1 h | 1 / 0 / 0 / 0 / 0 / 1 | 3 / 0 / 0 / 0 / 0 / 3 | 0 / 155 / 155 / 0 / 0 / 310 |
| Provider 429 for profiles and closes, 10 opens over 1 h | 1 / 0 / 0 / 0 / 0 / 1 | 3 / 0 / 0 / 0 / 0 / 3 | 0 / 310 / 310 / 0 / 0 / 620 |

Reading the table:
- **Targets met:**
  - the curated path sends **0 screener and 0 quote requests**;
  - enrichment happens once per company (one profile + one close series);
  - opens, pages, filters, sorts and concurrent users add nothing after it.
- **Current plan, FMP screener path:** 1 refused request, and Discover can't work at all.
- **A plan that includes the screener:** 3 universe requests plus 20 quote requests per page.
- **429 row (620):** the 10-minute rate-limit cooldown expires within the hour, so each company is tried twice. On REAL the
  `ProviderGuard` circuit opens after the first 429 and refuses the rest locally.
- **Fundamentals:** 0 everywhere (warm-up off, as on LAN REAL).
- **Live savings:** these are mock counts, not live measurements.
