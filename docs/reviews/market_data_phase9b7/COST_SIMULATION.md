# Cost simulation, Alpha Vantage primary — Phase 9B.7

**MODELED, one instance, per day.** Verified quotas only (published: free 25/day; premium 75 / 150 / 300 / 600 / 1,200 requests per
minute, no daily cap; monthly list prices $49.99 / $99.99 / $149.99 / $199.99 / $249.99 on 2026-10-17 — personal plans, **not** a
commercial licence, whose price needs a quote). Per-action counts from `AlphaVantageBenchmarkTest` (MockEngine). Assumptions as 9B (DAU
behaviour; distinct companies viewed per day D = 150 / 600 / 2,000; search miss 70 / 50 / 30 %; ≈ 4 distinct prefixes per search).

## Formulas (Alpha Vantage requests per day)
- Per distinct company per day: daily series 1 + quote 1 (both until the next close) + statements 3 (24 h) + OVERVIEW 1 (premium) = **6**
  (free plan: 5, profile from FMP).
- Search = DAU × 1.5 × 4 × miss (normalized, 6 h cache; storms coalesce: 100 users typing one word = 8 requests).
- Movers ≤ 96 (15-min TTL) — fixed. Discover ≤ 100 (9B cap) — fixed. Markets sector/fund closes ≈ 20 — fixed.
- Rapid navigation / repeat opens = 0 extra (measured: 50 re-opens → the cold 6).

| | 100 DAU | 1,000 DAU | 10,000 DAU |
|---|---:|---:|---:|
| Company data (6 × D) | 900 | 3,600 | 12,000 |
| Search | 420 | 3,000 | 18,000 |
| Movers + Markets + Discover | ≈ 220 | ≈ 220 | ≈ 220 |
| **Alpha Vantage total (base)** | **≈ 1,500** | **≈ 6,800** | **≈ 30,000** |
| low / high (0.5× / 2× activity) | 900 / 2,700 | 4,000 / 13,000 | 17,000 / 60,000 |
| Busiest hour (≈ 10 % of the day) per minute | ≈ 3 | ≈ 11 | ≈ 50 (high: 100) |
| Remaining FMP (fundamentals bundle 11 × D, EPS) | ≈ 1,700 | ≈ 6,600 | ≈ 22,000 |
| Finnhub (news, earnings) | ≈ 500 | ≈ 2,500 | ≈ 8,000 |

## What breaks on which allowance
- **Free (25/day, 20 per instance after the margin):** a single Company Details open costs 5; one tester exhausts it in minutes; Discover
  prices 4 companies an hour. Not usable for the app beyond a smoke test.
- **75/min (60/min after the margin):** fits 100 and 1,000 DAU by volume; at 10,000 DAU the busiest hour (≈ 50/min base, 100 high)
  saturates it — interactive requests wait ≤ 2 s, then fail with the typed rate-limit error; background Discover enrichment is deferred
  first (LOW priority). Search keystroke bursts are the largest variable component.
- **150/min:** recommended minimum for 10,000 DAU on one instance; multiple instances divide the plan rate (`CLOUD_RUN_MAX_INSTANCES`).
- **Commercial capacity:** the volume is small; the cost driver is the **commercial licence** (price unknown, Q13). Shared caching (if
  permitted) is what keeps 10,000 DAU at ≈ 30,000 requests/day instead of ≈ 1,000,000.

## Expensive features
Search typing (per prefix), first views of many distinct companies (6 each), premium full history (one large response per company per
day). Cheap: repeat views, Watchlist/Portfolio (one close per symbol per session), movers, Markets.

Budgets are **per instance** (ProviderGuard lane, Discover cap, caches); there is no global enforcement.
