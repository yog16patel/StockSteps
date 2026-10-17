# Price history strategy — Phase 9B

## 1. Today (unchanged)
- Daily series: FMP `historical-price-eod/light` (`date`, `price`), one request per symbol, cached in `PriceChartService` until the next
  expected close (Phase 7B `EodSessions`, 60-min publication delay, 30-min late retry, ≤ 4 days), shared by charts, Markets sectors,
  Portfolio, Practice, Discover enrichment and valuation history.
- Intraday: FMP 5-min bars (1D chart, sparklines), empty on this plan.
- Quotes: FMP `quote` with Phase 7A outcome cooldowns; Markets indices only while active; Portfolio/Practice don't use quotes on REAL (7C).
- **Adjustment basis of the light series: UNVERIFIED** (Phase 6 Q6). Symbol changes and delistings: no handling (a renamed ticker is a new
  symbol; a delisted one has no new closes → shown with its last date, then unavailable after 5 sessions).

## 2. Boundary added in 9B (`marketdata/Prices.kt`)
- `DailyPriceSource` → `Sourced<PriceSeries>`: symbol, exchange, time zone, currency (from the caller's profile, never guessed), **basis**,
  valid closes only (finite, > 0, one row per date, oldest first), provenance (provider, dataset, retrieved, as-of = last close date).
- `FmpDailyPriceSource` wraps the existing provider (basis `UNVERIFIED`). A second vendor is another implementation; selection through
  `SourceSelector` with the required basis.
- Labels: `PriceSeriesRules.closeDelay` → END_OF_DAY for the expected session's close, STALE when older, UNKNOWN without a calendar; never
  REAL_TIME or DELAYED for a close. `quoteDelay` → DELAYED within 20 min during an active session, STALE older, END_OF_DAY for a post-close
  quote, UNKNOWN for missing/future timestamps. Nothing is labelled REAL_TIME (no licensed real-time feed, no streaming).
- `compatible(history, current)`: same symbol, same known currency, same basis — two UNVERIFIED series only from the same provider. Adjusted
  history is never combined with an unadjusted current price.
- Holidays/weekends: the US and TSX calendars (`UsMarketCalendar`, `TsxMarketCalendar`) decide the expected session; tests cover Saturday.

## 3. What Portfolio and Practice need
| Need | Requirement | Status |
|---|---|---|
| Valuation | latest official close per holding with its date, ≤ 5 sessions | done (7C) |
| Execution | expected session's close (≤ 1 behind) | done (7C) |
| History graph | daily closes on the **same share basis as the ledger quantities**: as-traded (unadjusted) closes with as-traded quantities, or split-adjusted closes with split-adjusted quantities | **open**: FMP basis unverified; no REAL split source |
| Performance / returns | total-return series needs dividend-adjusted closes or dividend cash flows | not offered (by design: "Account value", never performance) |
| Corporate actions | splits (ratio, date), symbol changes, delistings | **not in REAL** (D-9B-5) |

## 4. Alternatives evaluated (rights first)
| Option | Daily OHLCV / latest EOD | Adjusted vs unadjusted | History | Rights for this app |
|---|---|---|---|---|
| FMP (current) | yes (light: close only) | unverified | as returned | needs DDLA |
| Alpha Vantage free | `TIME_SERIES_DAILY`: raw as-traded, compact = 100 points | adjusted series premium | full history premium | personal non-commercial: **not usable** |
| Alpha Vantage premium | full + adjusted | both | 20+ years | commercial terms by written agreement (Q10) |
| Other licensed vendors | typical | typical | typical | UNVERIFIED (Q8) |
| SEC | no prices | — | — | — |

**Recommendation:** keep FMP EOD for development; for launch, one licensed EOD vendor whose contract covers display, shared caching and
derived values, with an explicitly documented basis (prefer unadjusted closes + a split/dividend feed, so ledgers and history share one
basis). Keep the boundary; don't add a second vendor until its rights are verified.
