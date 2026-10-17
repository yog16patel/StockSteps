# Discover enrichment audit — Phase 9B

## 1. Universe
`server/src/main/resources/screener/curated-us-universe.json` v`2026-10-17.1`: 155 US-listed companies (NASDAQ/NYSE), 11 sectors, metadata
only (symbol, name, exchange, sector), described as "isn't the whole market" (tested in `CuratedDiscoverTest`). Not expanded in 9B.

## 2. Every automatic enrichment path
| Path | Trigger | Upstream | Budget | Status |
|---|---|---|---|---|
| Curated enrichment (`CuratedScreenerUniverse`) | any `/screener/catalog` or `/screener/search` call (never waits) | FMP `profile`, FMP `historical-price-eod/light` (through shared caches) | 60 companies/h (`SCREENER_ENRICHMENT_PER_HOUR`); **9B: 100 upstream requests/UTC day** (`SCREENER_ENRICHMENT_PER_DAY`, 0 = off) | changed in 9B |
| Fundamentals warm-up (`ScreenerService`) | page requests | FMP fundamentals bundle | `SCREENER_FUNDAMENTALS_PER_HOUR` (25 default, 0 on LAN REAL) | unchanged |
| Page quotes | only `SCREENER_UNIVERSE=fmp` or MOCK | FMP `quote` per row | none needed (off on REAL) | unchanged |
| FMP screener | only `SCREENER_UNIVERSE=fmp` | `company-screener` ×3/day | 24 h + 1 h cooldown | off on REAL |
| Search on Discover | typed text | none (local `SearchRanking` over the list) | — | unchanged |

Opening Markets, Discover or Search cannot burst: Discover returns immediately with what is enriched; background work is LOW priority (never
the interactive share of `ProviderGuard`), 4 at a time, inside both budgets; Search is 2 FMP requests per uncached query with a 6 h cache and
the 9A `search` bucket.

## 3. Phase 7E problem and the lost 7E.1 fix
Phase 7E costs ≈ 310 requests on a trading day (155 profiles + 155 closes) and 465 on the first day (closes re-checked after publication)
vs a key that answered 429 at ~250/day. A 7E.1 fix (100/day) was benchmarked in the `discover-quota-fix-5631dc` worktree, but **its code
exists on no branch** (only `captures/phase7e1-discover-quota-benchmark.txt`). 9B re-implements it with stricter accounting.

## 4. 9B change
- Daily cap in **real upstream requests**: each company reserves one unit per due part (profile, close) before it starts; the exact count of
  upstream calls it caused (`RequestCost`, the same counter admission uses) is settled afterwards. Cache hits and joined loads are free;
  concurrent passes can't overshoot; 402/429 answers count.
- Only due parts are fetched (a close refresh doesn't re-read the profile).
- Priority: never-loaded companies → missing closes → profile refreshes. While a close is still to be published in the budget day, profile
  refreshes may use only what the waiting companies won't need; profiles wait while any close couldn't be scheduled.
- Defaults: 60/h (unchanged) and 100/day (new). `SCREENER_ENRICHMENT_PER_DAY` validated at startup.

## 5. Measured (MockEngine, two weeks from Thu 2026-10-15, provider on time; profile / EOD / total · priced, latest close at 20:45 UTC)
See `server/build/phase9b-market-data-benchmark.txt` (copied to `captures/`). Highest day 465 → **100**; two weeks 3,875 → **1,400**; every
company priced by day 4; weekday latest-close coverage 100–155 of 155; 300 concurrent cold opens → 100 requests.

Trade-off: at 100/day not every close can be refreshed every trading day (155 needed). A full daily refresh needs ≈ 310/day
(`SCREENER_ENRICHMENT_PER_DAY=320`) once the plan allows it (D-9B-3). Rows always show the close's own date ("Latest close").

## 6. Not done
No universe expansion, no new background job, no precomputed fundamentals for Discover (proposed: SEC-derived ratios once D-9B-2 lands —
1 companyfacts per company per day, free, within SEC's 10 req/s).
