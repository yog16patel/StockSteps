# Phase 8D — financial presentation checklist (regression gate)

8D changed text placement only:
- Discover header;
- Practice header and sign-in block;
- Learn intro;
- research step text.

No values, formatting, presenters for figures, engines, persistence or server code changed.

| Check | Result | Evidence |
|---|---|---|
| EOD not labelled live | PASS | Markets "As of 4:00 PM ET · Oct 7" / "Last close" rules untouched; Company Details "Prices may be delayed" (1.5× frame) |
| ETF proxies identified | PASS | Home/Markets proxy lines unchanged (8A) |
| Missing price ≠ 0 | PASS | Discover "No data available" (REAL Pixel 7), Portfolio "CAD —" (1.5× missing-FX frame) |
| Missing FX ≠ 0 | PASS | "Unavailable: USD to CAD exchange rate …" (1.5× frame) |
| Partial valuation not shown as complete | PASS | total "CAD —" when any holding lacks a value |
| Practice clearly simulated | PASS | SIMULATED badge and "Learn investing without real money." remain visible at AX5 (iOS AFTER) |
| Buy/sell execution, idempotency, cost basis | unchanged | no change to `PracticeOrderPresenter`, `PortfolioEngine`, server |
| No new calculations in platform UI | PASS | diff has no arithmetic |
| No REAL → MOCK fallback | PASS | no client or networking change |
| Dates and freshness visible | PASS | Discover scope label always visible; the server description is one tap away; Watchlist dated header (8C) |
| Curated-list disclosure kept | PASS | "not the whole market" in the always-visible label; the full server description under "About this list" |
