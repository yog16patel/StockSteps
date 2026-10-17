# Phase 7B — EOD-first Markets optimization

Date: 2026-10-17. Base: `1404b4e` (Phase 7A deployment note; Phase 7A itself is `c57135a`, committed, pushed and deployed to LAN REAL
on the owner's request, ChatGPT review pending). Commit: "Make Markets EOD-first for Phase 7B with sector and index fall-back changes from consecutive trading-day closes, quotes only while an index market is active, no proxy or most-active enrichment quotes, closes cached until the next expected close and Last close date labels on Android and iOS" (committed and pushed on the owner's request); **not deployed**. Review package:
`docs/reviews/markets_eod_phase7b/`.

**Verdict: READY FOR REVIEW**, with two owner decisions: the redeploy, needed for any REAL device check, and the copy and publication
delay in `DATA_FRESHNESS_AND_LABELS.md`.

## 1. Markets data flow before this phase (Phase 7A code)

| Section | Source | Symbols | Cadence / TTL | Upstream per refresh | Freshness | Error behaviour |
|---|---|---|---|---|---|---|
| Overview | `MarketsService.overview` | — | 30 s while the US market is active / 5 min otherwise | sum of the sections below | — | sections fail independently |
| Indices | FMP `quote` (index), else proxy `quote`, trend from EOD | ^GSPC, ^IXIC, ^DJI, ^GSPTSE; SPY, ONEQ, DIA, XIC.TO | section 60 s / 15 min | up to 8 quotes (+1 EOD per index, 6 h) | delayed quote | proxy labelled; else `INDEX_UNAVAILABLE` |
| Sectors | FMP `quote` × 11 | XLK … XLB | 120 s / 30 min | 11 quotes | delayed quote | only funds on the newest session; refused → `PROVIDER_RESTRICTED` (7A) |
| Gainers / losers | FMP `biggest-gainers` / `biggest-losers` | list | 60 s / 15 min | 1 each | latest session | section error |
| Most active | FMP `most-actives` + **one `quote` per row without volume** | up to 10 | 60 s / 15 min | 1 + up to 10 quotes | latest session | missing volume → quote |
| News | Finnhub `news` | — | 5 min | 1 | — | section error |
| Session | `UsMarketCalendar` (local) | — | — | 0 | — | — |

**Necessary calls:** the movers lists and news. **Unnecessary on this plan:**
- sector and proxy quotes (refused, and replaceable by daily closes);
- most-active enrichment quotes. They succeed for the listed small caps, but cost up to 10 quotes a minute. In the benchmark they are
  ~9,600 of ~9,900 quote requests over a trading day.

## 2. What changed

| Area | Change |
|---|---|
| Server `service/EodCloses.kt` (new) | `EodSessions`: exchange calendars (US incl. `^GSPC`/`^IXIC`/`^DJI`; TSX incl. `.TO` and `^GSPTSE`), expected session = latest trading day whose close + publication delay has passed (default 60 min, env `EOD_PUBLICATION_DELAY_MINUTES`, UNVERIFIED for FMP), close instants (US early closes at 13:00), session-aware TTL, and `active()` hours. `EodChange`: latest completed-session close vs the **previous trading day's** close; invalid, zero and non-finite closes ignored; duplicates counted once; same-day partial rows ignored; non-consecutive closes → null; `stale` when the latest close is older than expected |
| Server `PriceChartService` | optional `eod`: a loaded daily series lives until the next expected close (weekends and holidays, at most 4 days); a missing expected close is retried every 30 min; failures keep the 7A cooldowns. The single cache is shared by charts, valuation, portfolio, earnings reactions and Markets |
| Server `MarketsService` | with `dailyCloses` (REAL and MOCK wiring): sectors from EOD closes (`closeDate`, `previousCloseDate`, section `closeDate`, `unavailable`); indices from the quote only while the index's market is active, else, or when the quote fails, its own EOD close, then its fund's EOD close (labelled proxy, fund currency, no trend), then unavailable; no proxy quotes; no most-active quote enrichment; an all-failed sector section keeps the provider reason (restricted / rate limited / daily allowance) |
| Server `Application.kt`, `StartupConfiguration.kt` | wiring; `EOD_PUBLICATION_DELAY_MINUTES` validated (0–720) |
| Core `model/Markets.kt` | optional fields only: `IndexQuote.closeDate/previousCloseDate`, `SectorPerformance.closeDate/previousCloseDate`, `SectorPerformanceSection.closeDate/unavailable` |
| Core `markets/MarketsPresentation.kt` | index cards: "Last close · Oct 16" for EOD values, quotes unchanged ("As of 4:00 PM ET · Oct 7"); sector period: "Change from the previous close · Last close Oct 16 · Not available: Real Estate" |
| Android / iOS | **no code change**: both render the shared `IndexCardModel.updated` and `MarketsScreenPresentation.sectorsCaption` |

Unchanged:
- movers and news endpoints;
- Practice, My Portfolio valuation, Discover;
- the Markets layout (no borderless work);
- the Daily Brief code. Its `asOf`-based labels read an EOD index as "Close · Oct 16", or "Previous close · …" during the session.

## 3. Contracts

- **Additive only.** New optional JSON fields with null or empty defaults; clients decode with `ignoreUnknownKeys = true`. Old clients
  ignore the new fields; new clients read old servers.
- **Meaning changes on the EOD path:**
  - an EOD `IndexQuote.asOf` is the session's closing instant, not a quote time;
  - `SectorPerformanceSection.period` reads "Change from the previous close" instead of "Change since the previous close";
  - most-active rows have no volume when the provider's list has none.

## 4. Request benchmark (MockEngine; `server/build/phase7b-markets-benchmark.txt`)

| Scenario | Before quote / EOD / movers = total | After quote / EOD / movers = total |
|---|---|---|
| One cold overview (Fri 18:00 ET, after-hours) | 29 / 0 / 3 = 32 | 3 / 15 / 3 = 21 |
| Cold + warm 1 min later | 39 / 0 / 6 = 45 | 3 / 15 / 6 = 24 |
| 10 opens over 1 h (after-hours) | 119 / 0 / 30 = 149 | 3 / 15 / 30 = 48 |
| Monday 04:00–20:00 ET, an open every minute (960 builds) | 9,904 / 0 / 2,880 = 12,784 | 58 / 105 / 2,880 = 3,043 |
| Weekend, 48 opens over 24 h | 936 / 0 / 144 = 1,080 | 0 / 15 / 144 = 159 |
| 20 concurrent cold overviews | 29 / 0 / 3 = 32 | 3 / 15 / 3 = 21 |
| Restricted EOD (402 for every close), 10 opens over 1 h | 119 / 0 / 30 = 149 | 3 / 19 / 30 = 52 |
| 3 sector funds without data, 10 opens over 1 h | 119 / 0 / 30 = 149 | 3 / 18 / 30 = 51 |

- **Before** is the Phase 7A quote path with every index, proxy and sector quote refused, as observed.
- **Remaining costs:** after the change, the movers are the dominant cost (3 lists every 60 s while the market is active), plus the late
  close retries after 17:00 ET.
- **Phase 6 comparison:** Phase 6 *modeled* ≈ 17,700 FMP/day at saturation. This benchmark *measures* 12,784 → 3,043 for one
  saturated trading day on the mock. Those are mock counts, not live measurements.

## 5. Tests and builds

`docs/reviews/markets_eod_phase7b/CHATGPT_TEST_RESULTS.md`. New tests:
- `MarketsEodTest` (14);
- `MarketsEodBenchmarkTest` (1);
- `MarketsPresenterTest.endOfDayValuesAreLabelledWithTheirCloseDate`.

`FmpMock` gained `bodies` (per-path response overrides).

MOCK end to end (throwaway local server on :8090, stopped after): 11 sectors from fixture closes, labelled `closeDate` 2026-10-07; the
TSX index by its labelled proxy close; `sampleData = true`; no section errors.

## 6. Device verification

**BLOCKED.**
- REAL needs an approved redeploy of `stocksteps-real-api-1`.
- Android and iOS app code is unchanged, and both builds pass.
- A MOCK device check wasn't run (no device driving was requested).

## 7. Remaining restrictions and licensing

- FMP quotes are still refused for most symbols; the screener is restricted.
- The EOD publication delay and adjustment basis (Q6) are UNVERIFIED.
- Display and caching rights for FMP data are UNVERIFIED (Phase 6 Q4). The longer close lifetimes keep data in memory for up to 4 days
  per instance, which the licence must allow.
- FMP's mover lists remain plan-dependent, and their volume field is missing for some rows.

## 8. Owner decisions

1. Approve the copy:
   - "Last close · Oct 16";
   - "Change from the previous close · Last close Oct 16 · Not available: …".
2. Confirm or replace the 60-minute publication delay (Q6).
3. Approve a LAN REAL redeploy for the device pass.
4. Accept that most-active rows show no volume when FMP's list lacks it, instead of spending a quote per row.
