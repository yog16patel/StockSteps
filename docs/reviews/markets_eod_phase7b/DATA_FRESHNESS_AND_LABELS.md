# Phase 7B — data freshness and labels

## Sources per Markets section

| Section | REAL source (after 7B) | Value | Shown as |
|---|---|---|---|
| Index (market active) | FMP `quote` of the index, if the plan serves it | delayed quote | "As of 3:42 PM ET · Oct 19" (unchanged) |
| Index (market inactive, or quote refused) | FMP `historical-price-eod/light` of the index | the latest completed session's close, in points | "Last close · Oct 16" |
| Index without its own close | EOD of its fund (SPY, ONEQ, DIA, XIC.TO) | the fund's close in its currency; never the index level; no trend | "Last close · Oct 16" + "Proxy: XIC.TO fund price" (caution colour, unchanged) |
| Index with neither | — | `INDEX_UNAVAILABLE` "This index isn't available right now." | unchanged unavailable card |
| Sectors | EOD of each Select Sector SPDR fund | close vs the previous trading day's close | "Change from the previous close · Last close Oct 16. Measured with sector ETFs." (+ " · Not available: Real Estate" when a fund has no usable close) |
| Gainers / losers / most active | FMP lists (unchanged) | latest session as listed | unchanged; most active without volume shows no "Vol" |
| News | Finnhub (unchanged) | — | unchanged |
| Daily Brief markets block | the same `IndexQuote`s | — | the existing `asOf` rules: "Close · Oct 16", or during the session "Previous close · Oct 16 (today's values aren't available yet)" |

## Rules

- **Change** = latest close − previous trading day's close; **percent** = change ÷ previous × 100.
  - The two closes must be on consecutive trading days of the listing's calendar (NYSE or TSX holidays, US early closes).
  - Otherwise the change is unavailable, never computed across a gap.
- **Closes we don't use:** missing, zero, negative or non-finite values; a second row for the same date (counted once); a row dated
  after the expected session (a same-day partial row).
- **Equal closes on two days** are a real 0.00 % ("unchanged" in words). One day counted twice never is.
- **Expected session:** the latest trading day whose close plus the publication delay (60 min, `EOD_PUBLICATION_DELAY_MINUTES`) has
  passed. The real FMP delay is UNVERIFIED.
- **A late close:** if the newest close is older than expected, it is shown with its own date (`stale` internally). The series is asked
  again every 30 min until the close appears.
- **Sectors** are compared only on the newest close date. Funds on another date, or without data, are listed as not available.
- **Proxies:** a fund's close is never shown as an index level ("points"). Its trend isn't drawn.
- **Currency:** each index keeps its market currency; a proxy keeps its fund currency (XIC.TO in CAD). Nothing is compared across
  currencies.
- **REAL never falls back to MOCK.** MOCK uses fixture closes, labelled sample.

## Cache lifetimes (per instance, the existing `price-chart` cache)

| State | Lifetime |
|---|---|
| Series contains the expected session | until the next expected close (e.g. Fri 17:00 ET → Mon 17:00 ET), at most 4 days |
| Expected close missing | 30 min (late retry), never beyond the next expected close |
| 402/403 | 1 h; 429 10 min; other failures 30 s; a local daily-allowance denial 30 s (7A) |
| Listing without a calendar (other suffixes) | fixed 6 h (unchanged) |

## Copy needing approval

- "Last close · Oct 16"
- "Change from the previous close"
- "Last close Oct 16" (inside the sector caption)
- "Not available: Real Estate, Utilities"
- Sector methodology addition: "Each fund's latest closing price is compared with its close on the previous trading day."
