# Phase 7B — Markets API request benchmark

Source: `server/src/test/.../service/MarketsEodBenchmarkTest.kt` (MockEngine, fake clocks, no network, no keys); output copied from `server/build/phase7b-markets-benchmark.txt`.

- **Before:** the Phase 7A quote path (sectors, index proxies and most-active volumes from quotes; refused quotes cooled down 1 h).
- **After:** the EOD path.
- **Mock setup:** every index, proxy and sector-fund quote is refused (402), as observed on the current plan. Daily closes are served for every symbol through Fri 2026-10-16. The most-active list has no volumes.
- **Counts:** upstream FMP requests by endpoint group. Movers = `biggest-gainers` + `biggest-losers` + `most-actives`.

| Scenario | Before quote | Before EOD | Before movers | Before total | After quote | After EOD | After movers | After total |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| One cold overview (Fri 18:00 ET, after-hours) | 29 | 0 | 3 | 32 | 3 | 15 | 3 | 21 |
| Cold + one warm overview 1 min later | 39 | 0 | 6 | 45 | 3 | 15 | 6 | 24 |
| 10 opens, 6 min apart (Fri after-hours, 1 h) | 119 | 0 | 30 | 149 | 3 | 15 | 30 | 48 |
| Monday 04:00–20:00 ET, an open every minute (960 builds) | 9904 | 0 | 2880 | 12784 | 58 | 105 | 2880 | 3043 |
| Weekend: 48 opens, one every 30 min | 936 | 0 | 144 | 1080 | 0 | 15 | 144 | 159 |
| 20 concurrent cold overviews | 29 | 0 | 3 | 32 | 3 | 15 | 3 | 21 |
| Restricted EOD (402 for every close), 10 opens over 1 h | 119 | 0 | 30 | 149 | 3 | 19 | 30 | 52 |
| Missing data for 3 sector funds, 10 opens over 1 h | 119 | 0 | 30 | 149 | 3 | 18 | 30 | 51 |

Reading the rows:
- **Monday row** (one open per minute through the 16-hour active window = saturation):
  - quotes 9,904 → 58: the 3 US index quotes, refused and asked once an hour, plus one TSX index quote while TSX is active;
  - EOD 105 = 15 cold + the late close retries after Monday 17:00 ET, because the mock never publishes a Monday close (6 retries × 15);
  - movers are unchanged (2,880 = 3 lists × 960) and are now the largest cost.
- **Weekend row:** no quote at all. The closes are loaded once and kept until Monday's expected close.
- **Restricted EOD / missing data rows:** the refused or empty series are asked again only after their cooldown or late retry. The rest
  of Markets stays available (`MarketsEodTest` checks the sections).
- **Cache hits and misses** aren't in the table. `cache.price-chart.*` and `cache.fmp.*` events are emitted in the usage summaries when
  deployed.
- **Phase 6 comparison:** the 17,700/day saturation figure was *modeled*. Here one saturated weekday on the mock goes from 12,784 to
  3,043 Markets requests (−76 %). That's a mock measurement, not a live one. Live savings depend on traffic and on which quotes the plan
  actually serves.
