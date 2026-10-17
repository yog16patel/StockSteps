# Quota usage — Phase 9B.9A

| Item | Value |
|---|---|
| Published free allowance | 25 requests/day (support page, 2026-10-17) |
| StockSteps budget on FREE (one private dev instance) | **20/day**, exact (`ALPHA_VANTAGE_DAILY_BUDGET`, default 20, startup error above 25; not scaled by `PROVIDER_SAFETY_MARGIN`) |
| Free rate | 5/min, burst 5 (× safety margin per instance); `ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE` ignored on FREE (warning) |
| Premium functions on FREE | 0 requests (refused locally) |
| Smoke test (first run) | ≤ 10 requests: TIME_SERIES_DAILY, GLOBAL_QUOTE, SYMBOL_SEARCH, INCOME_STATEMENT, BALANCE_SHEET, CASH_FLOW (6); + EARNINGS, SPLITS with `ALPHA_VANTAGE_SMOKE_OPTIONAL=true` (8); 15 s apart; stops at the first refusal; leaves ≥ 12 of the 20 for debugging |
| Discover bulk enrichment / fundamentals warm-up on FREE | **off in code** (`DataSources.alphaVantageFree` forces both to 0) |
| Authenticated requests made in this phase | **0** |

What a day of 20 buys (fixture-measured, 9B.7/9B.8): one cold Company Details open with Alpha Vantage prices ≈ 5, with Alpha Vantage
fundamentals ≈ 5 more. So 2 cold companies a day on the server — only development checks, never app use by anyone else.
Budgets are per instance; run exactly one dev instance (the exact daily budget would multiply with more).
