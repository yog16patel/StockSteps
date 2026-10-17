# Phase 7C — corporate-action gaps

| Question | Finding |
|---|---|
| Is FMP `historical-price-eod/light` split-adjusted? | **UNVERIFIED** (Phase 6 Q6). The light series has one price field per day; FMP's documentation wasn't fetchable and no probe was made |
| Are quantities and cost basis adjusted? | My Portfolio: only by user-entered `STOCK_SPLIT` transactions (existing; the chart notice says "Record stock splits in your transactions"). Practice REAL: **no** (`corporateActions = null`; MOCK uses `MockCorporateActions`) |
| Does an existing provider supply split events? | Not in the code. FMP has split endpoints on some plans, but they are not used, not probed, and their plan availability is unknown |
| Can REAL Practice safely hold a position across a split? | **No.** After a split, the close drops (e.g. ÷2) while the share count stays, so the value is understated. If the provider's history is back-adjusted, past closes change too |
| Is historical valuation on a consistent basis? | Only if the series is unadjusted and splits are recorded, or adjusted and quantities adjusted. Neither is guaranteed today |

## Guard added (no adjustment)

- When a held symbol's last two consecutive closes differ by ≥ 1.8× or ≤ 0.55× (`isSplitLike`), the screen says so:
  - **Practice overview** notice: "AAPL: the closing price changed by more than 45% in one session. If the company split its shares, this
    holding hasn't been adjusted.";
  - **My Portfolio chart notice:** "… if it was a stock split, record it.".
- The existing REAL Practice notice stays: "Dividends and stock splits aren't applied yet, so returns may differ from a real account."
- Nothing is adjusted, and no split is inferred as fact. A real 50 % crash also triggers the warning, which is worded conditionally.

## Not solved (needs a separate approved phase)

- A licensed split and dividend source.
- Applying splits to Practice positions and to cost basis, idempotently, with an audit trail.
- Knowing the adjustment basis of the close series.
- Dividends for REAL Practice.
