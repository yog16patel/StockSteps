# Market Snapshot

Both `GET /market/snapshot` and `GET /api/v1/market/snapshot` return the same
cached snapshot. Clients call StockSteps only; FMP_API_KEY stays on the backend.

```sh
curl http://localhost:8080/market/snapshot
```

The response contains `marketStatus`, `indices`, `gainers`, `losers`, `mostActive`,
`lastUpdated` (UTC ISO timestamp of snapshot assembly), and `errors`.

- Indices: SPY (S&P 500), QQQ (Nasdaq-100), DIA (Dow 30). These are ETF proxies,
  identified by `isProxy: true`; prices are USD ETF prices, not index levels.
- Movers: at most five per section, including symbol, name, nullable price/change/
  changePercent, optional exchange and volume. Missing/invalid numbers stay null.
- Status: the FMP NYSE regular-session flag gives OPEN/CLOSED; unavailable status
  gives UNKNOWN. PRE_MARKET/AFTER_HOURS are supported but not guessed from clocks.
- Failures: an index carries its own optional `error`; failed sections appear in
  `errors` with a section name and safe code/message. Successful sections survive.
  An empty section without an error means the provider returned no entries.

The service caches the entire result for 45 seconds from completion, including
partial failures, and coalesces concurrent cache misses. Retry may return the
same cached result until expiry. There is no Redis or database cache. Cancellation
propagates; provider bodies and credentials are not exposed to clients.

Android and native SwiftUI Home use the shared repository/use case, with independent
watchlist loading, prices/status, loading/retry states, and movers opening stock
details. Restart your backend after updating code.

Live verification on October 4, 2026 returned SPY and all three movers sections.
FMP rejected QQQ/DIA quotes with HTTP 402 for the configured account; their cards
correctly display unavailable data. The backend now falls back to Finnhub for failed or missing ETF quotes.
A subsequent live check returned valid SPY, QQQ and DIA prices and signed changes.
FMP still supplies movers and market status; both providers remain backend-only.

Server tests cover mapping, status, partial failures, cache expiry/coalescing and
route aliases. Core tests cover backend-only request routing and safe client errors.
The native code compiled using an isolated compatibility project; the actual
Firebase pin requires a newer Xcode, so native runtime verification remains pending.
