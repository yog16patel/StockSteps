# Alpha Vantage feature coverage — Phase 9B.7

States: **wired** (implemented and used by the REAL wiring when Alpha Vantage is primary) · **implemented, unused** · **fixture-tested** ·
**REAL-tested** (none: no key) · **licensing blocked** (every row for anyone but the key holder) · **deferred**.

| Feature | Dataset | Source with Alpha Vantage primary | State |
|---|---|---|---|
| Company Details — price chart 1W–ALL | daily closes | AV `TIME_SERIES_DAILY` (free: last 100 sessions only → 1Y/5Y/ALL truncated; premium: full) | wired · fixture-tested |
| Company Details — 1D chart, sparklines | intraday | none (premium/realtime; not requested) → unavailable | wired (empty) |
| Company Details — quote, day range | quote | AV `GLOBAL_QUOTE` (end of day) | wired · fixture-tested |
| Company Details — identity/overview | profile | AV `OVERVIEW` on a premium plan; FMP otherwise | wired · fixture-tested |
| Company Details — financials, ratios, valuation metrics | fundamentals bundle | FMP (dev) | deferred (needs AV→`CompanyFundamentals` mapping and real comparison) |
| Company Details — valuation history (P/E) | closes + quarterly EPS | AV raw closes + FMP EPS, raw-basis rule (no split factors; months spanning a split omitted) | wired · fixture-tested |
| Company Details — earnings, dividends | earnings, dividends | Finnhub / FMP | AV parsers implemented, unused |
| Comparison history / research statements | statement history | AV income/balance/cash flow | wired · fixture-tested |
| Search | symbol search | AV `SYMBOL_SEARCH` (US equities/ETFs) | wired · fixture-tested |
| Markets — movers | movers | AV `TOP_GAINERS_LOSERS` (end of day; tickers without names) | wired · fixture-tested |
| Markets — sectors, index fund closes | daily closes | AV daily | wired |
| Markets — index levels | index | none (AV `INDEX_DATA` premium 150+/min; index licensing Q9) → fund-close fallback (7B) | deferred |
| Discover — closes, profiles | daily, profile | AV daily; profile AV (premium) or FMP | wired (budgeted: 100/day cap + AV lane) |
| Watchlist, alerts | quote | AV quote (end of day) | wired |
| My Portfolio, Practice — valuation/execution | daily closes | AV raw closes (as traded = ledger basis) | wired; split events not applied (D-9B7-5) |
| Corporate actions | splits, dividends | AV `SPLITS`, `DIVIDENDS`, `DAILY_ADJUSTED` | implemented, unused (policy decision) |
| News, sentiment | news | Finnhub (AV `NEWS_SENTIMENT` premium) | deferred |
| Earnings calendar | calendar | Finnhub | deferred |
| FX | USD/CAD | Bank of Canada (official, cleared) | unchanged |
| Macro | — | none consumed | deferred |
| Every row above, for anyone but the key holder | — | — | **licensing blocked** (personal licence) |
