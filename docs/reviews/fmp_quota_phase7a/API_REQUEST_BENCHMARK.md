# Phase 7A — API request benchmark

Source: `server/src/test/.../service/QuoteQuotaBenchmarkTest.kt` (MockEngine, fake clocks, no network, no keys). Output copied from `server/build/phase7a-quote-benchmark.txt`. "Before" replays the pre-7A repository rule (`quote:SYM` cached only on success, 30 s; no daily allowance) against the same mock; "After" is this working tree. Upstream FMP `quote` requests only. No request was sent to the real FMP service.

| Scenario | Before | After |
|---|---:|---:|
| 10 repeated reads, one restricted symbol (402), within 1 min | 10 | 1 |
| 20 concurrent reads, one restricted symbol (402) | 1 | 1 |
| 10 different restricted symbols, 3 reads each | 30 | 10 |
| Markets closed: 3 overview builds in 58 min (29-min steps), all 11 sector funds refused | 33 | 11 |
| Markets live: 29 overview builds in 58 min (every 2 min), all 11 sector funds refused | 319 | 11 |
| 429: 5 reads within 10 min, then 1 after | 6 | 2 |
| Daily allowance 10, 50 concurrent callers on 50 symbols | 50 | 10 |

Notes:
- **20 concurrent reads, before = 1:** single flight already shared one in-flight request. The waste was in *repeated* reads after a
  failure, which the 10-read and Markets rows show.
- **Markets rows: where "before" comes from.** The Markets before values (33 / 319) were measured with the pre-7A `MarketsService`
  and the pre-7A repository rule, before the sector-section change (§5 of the focused diff). With that change in place, the replay
  harness only reverts the repository rule. It now shows 11 / 11 for both columns, because the section's own restricted cooldown also
  stops the repeats. Either layer alone removes them.
- **Markets rows** count only the 11 sector funds:
  - before: the whole sector section failed, was cooled down for 30 s and rebuilt on every overview build;
  - after: one request per fund per hour, and the section reports `PROVIDER_RESTRICTED`.
- **Comparison with Phase 6** (LAN REAL, 2026-10-16 21:57Z–00:05Z): about 21 refused quotes per Markets build and 111 of 139 quote
  attempts refused. With this change, a refused symbol costs at most one request per hour per instance. At the live sector refresh rate
  (every 2 min) that is about 30× fewer sector requests.
- **Daily allowance row:** before has no allowance (all 50 sent). After, with a configured allowance of 10: 10 sent, 40 refused locally
  with `PROVIDER_DAILY_LIMIT_REACHED`.
