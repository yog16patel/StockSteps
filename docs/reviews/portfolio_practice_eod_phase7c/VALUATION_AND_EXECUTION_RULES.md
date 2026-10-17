# Phase 7C — valuation and execution rules (REAL)

## Shared close rule (`EodPrice.latest`)

- **Source:** FMP `historical-price-eod/light` through the shared `price-chart` cache (Phase 7B lifetimes: until the next expected close;
  30-min retry while late; 402/403 1 h; 429 10 min).
- **Valid observation:** a finite close > 0, on a trading day of the listing's calendar (NYSE for US listings, TSX for `.TO`), not after
  the **expected session** (the latest trading day whose close plus the 60-min publication delay has passed).
- **`sessionsBehind`:** trading sessions between the close and the expected session. 0 = the latest completed session.
- **Other suffixes:** listings without a supported calendar have no close (unavailable), never a guess.

| Use | Accepted | Otherwise |
|---|---|---|
| My Portfolio value | `sessionsBehind ≤ 5`; dated by `pricesAsOf` (the oldest close used) | the holding is missing → `totalValue` null, "Unavailable: SYM" (cost basis kept) |
| Practice valuation | `sessionsBehind ≤ 5`; `stale` when > 0 ("Some prices are from an earlier trading session.") | the holding's value is null → totals unavailable |
| Practice execution | `sessionsBehind ≤ 1` (the expected close, or the one before while publication is late) | `QUOTE_STALE` "The latest closing price for X is from Oct 13, 2026, which is too old for a practice trade…" |
| No valid close | — | `QUOTE_MISSING` "There's no closing price for X right now…" |
| Closes refused by the plan (402/403) | — | `QUOTE_RESTRICTED` "This server's market data plan doesn't include closing prices for X… Trying again won't help." |

## My Portfolio

- **Market value** = quantity × latest valid close (in the listing currency → the trade currency at the close date, then the reporting
  currency at the latest FX publication — the existing conversion rule, shown as `fxAsOf`).
- **Unrealized P/L, P/L % and allocations:** unchanged (`PortfolioEngine`). The basis stays at the dated FX of each trade.
- **Missing:** a missing holding or FX makes the total, the unrealized P/L and the allocations unavailable, never a partial sum or 0.
- **"Today's change":** needs live prices. On closing prices it shows `EOD_DAILY_NOTICE` ("Today's change needs live prices, which this
  server's market data plan doesn't include. Values use the latest closing prices.") instead of presenting a past session's move as
  today's. **Owner decision:** a "Last session" line could replace it in a later UI phase.
- **Freshness line:** "Closing prices as of Oct 16, 2026 · Exchange rate as of Oct 16, 2026".
- **No writes:** no market refresh writes to Firestore.

## Practice execution

- **Immediate fill:** the trade fills at the last available official close, labelled "Simulated trade using the last available closing
  price · Oct 16, 2026". This is an educational simulation, not a claim that an order could have filled there.
- **Server-set price:** the client's `reviewedPrice` must be within 1 % (`PRICE_CHANGED`), otherwise "review again".
- **FX:** CAD base, USD/CAD at the close's date (Bank of Canada publication within 7 days, else `FX_UNAVAILABLE`).
- **Unchanged rules:** no negative cash (`INSUFFICIENT_CASH`), no overselling (`INSUFFICIENT_SHARES`, `NO_HOLDING`),
  `INVALID_QUANTITY`, the holding limit, one fill per idempotency key, atomic Firestore update.
- **Not added:** queued orders and market-hours matching.

## MOCK

MOCK keeps its fixture-quote path. Its fixture closes end on 2026-10-07, while the MOCK clock advances. MOCK never feeds REAL.
