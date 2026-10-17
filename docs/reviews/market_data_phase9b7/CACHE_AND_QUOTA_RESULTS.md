# Cache and quota results — Phase 9B.7 (MockEngine; `captures/phase9b7-alpha-vantage-benchmark.txt`)

| Scenario | Alpha Vantage requests | Other providers |
|---|---:|---|
| Company Details cold, free plan | 5 (daily, quote, 3 statements) | 1 FMP (profile, by configuration) |
| Company Details cold, premium plan | 6 (+ OVERVIEW) | 0 |
| 1,000 concurrent Details readers × 20 symbols | 120 cold, **0 warm** | — |
| 100 users typing "microsoft" (8 prefixes each) | **8** | — |
| 50 rapid re-opens of one company | 6 (cold only) | — |
| 25 / 50 concurrent identical requests | 1 | — |
| Rate-limit answer in HTTP 200, 5 reads | 1 (10-min cooldown; breaker opens) | — |
| Premium-only answer, 5 reads | 1 (1 h cooldown) | — |
| Premium function without a plan / index ticker | **0** (refused locally) | — |
| Free daily allowance, 40 reads spread over the day | 20 (then `PROVIDER_DAILY_LIMIT_REACHED` locally) | — |
| Fast burst on the free rate | 4 (5/min × 0.8) | — |
| End-of-day quote over a weekend | 1 (lives until the next expected close) | — |
| Discover enrichment 1 h, free | 4 (4 of 155 priced) | profiles via FMP |
| Discover enrichment 1 h, 75/min plan | 84 (42 of 155 priced; LOW priority, never the interactive reserve) | 0 |

FMP/Finnhub before → after when Alpha Vantage is primary: FMP stops receiving price, quote, search, movers and statement-history requests;
it keeps the fundamentals bundle, quarterly EPS and (free AV plan) profiles. Finnhub unchanged. With Alpha Vantage disabled (default)
nothing changes: every existing benchmark (Phase 3, 7A–7E, 9A, 9B) re-ran unchanged in `:server:test`.

Controls kept: 9A per-class limits and concurrency caps; 9B Discover 100/day cap; single flight per function + symbol; refusals and
negatives cached; bounded caches (2,048 Alpha Vantage entries); no retries. **Per instance only** — no global quota enforcement.
