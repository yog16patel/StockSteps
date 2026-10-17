# Provider fallback matrix — Phase 9B.7 (dataset level, chosen by configuration)

Rule: a dataset's source is chosen **up front** by configuration and entitlement; an Alpha Vantage refusal (rate limit, premium-only,
invalid key, daily budget) is returned as the typed failure — **never** answered from another provider (that would dodge a plan, rate or
licence limit). Never REAL → MOCK. In commercial mode every source must be licensed for the channel (fails closed today).

| Dataset | 1st | 2nd | 3rd | Notes |
|---|---|---|---|---|
| Daily prices (EOD, history) | Alpha Vantage `TIME_SERIES_DAILY` (raw) | — | FMP light (only when AV isn't primary) | basis must match the caller's (`PriceSeriesRules.compatible`) |
| Quotes | Alpha Vantage `GLOBAL_QUOTE` (EOD) | — | FMP (AV not primary) | no realtime anywhere |
| Search / symbol reference | Alpha Vantage `SYMBOL_SEARCH` | SEC tickers (9B adapter, not wired) | FMP (AV not primary) | US scope |
| Profile | AV `OVERVIEW` (premium plan) | FMP profile (no premium plan, by configuration) | — | SEC submissions for name/CIK (future) |
| Reported statements | Alpha Vantage statements | SEC EDGAR companyfacts (validated, User-Agent approved — D-9B7-3) | FMP (AV not primary) | SEC needs a real side-by-side comparison first |
| Fundamentals bundle (ratios, valuation metrics, estimates, dividends in fundamentals) | FMP (dev) | — | — | AV mapping deferred; metrics engine on SEC/AV facts after comparison |
| Quarterly EPS for P/E history | FMP (diluted, with share counts) | AV `EARNINGS` (basis undocumented) | — | raw-basis rule applies with AV closes |
| Splits, dividends | AV `SPLITS`/`DIVIDENDS` (not wired) | SEC dividends declared | — | policy decision D-9B7-5 |
| Movers | AV `TOP_GAINERS_LOSERS` | — | FMP (AV not primary) | — |
| Index levels | fund closes (7B) | — | — | AV `INDEX_DATA` premium + index licensing |
| News, earnings calendar | Finnhub (dev) | — | — | licensing pending (Q5) |
| FX | Bank of Canada | — | — | official, cleared |
| Macro | Treasury / BLS / BEA (none wired) | AV economic indicators | — | no feature yet |
