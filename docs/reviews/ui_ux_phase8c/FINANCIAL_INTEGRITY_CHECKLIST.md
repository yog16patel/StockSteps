# Phase 8C — financial integrity checklist (mandatory gate)

8C changed no accounting, valuation, execution, persistence, auth or Firestore code. The checks below were made by code inspection,
unit tests and MOCK frames.

| Check | Result | Evidence |
|---|---|---|
| Null price / FX stays unavailable | PASS | Missing FX scenario (Android): "CAD —", "Unavailable: USD to CAD exchange rate on Sep 1, 2026 (for cost), USD to CAD exchange rate"; allocation "AAPL · USD —". The overview reorder moved these lines, it didn't change them |
| No zero substitution | PASS | `PortfolioFormat.amount` / `signedMoney` → "—"; donut rule refuses missing or negative slices (tests unchanged) |
| Correct signs | PASS | Unrealized "↑ +CAD 200.00" (arrow, sign and colour), Practice "Gain +$224.43 (+10.96%)" / "Loss −$2.23"; unchanged code |
| Explicit currencies | PASS | "CAD 1,000.00", "USD 1,000.00", "US$303.42"; Watchlist "$" only for USD listings (unchanged) |
| No CAD/USD mixing without conversion | PASS | Totals come from the engine in the reporting currency; holdings show native currency; nothing summed in the UI |
| Trade date ≠ valuation date | PASS | Transactions "Sep 1, 2026"; valuation "Prices as of Oct 8, 2026, 3:00 PM UTC · Exchange rate as of Oct 8, 2026" |
| EOD not presented as live | PASS | Practice "Price basis … · date" in the order flow (7C) unchanged; Watchlist "Change since the previous close. Prices may be delayed." and now a dated "Updated Oct 7, 5:15 PM ET" |
| Portfolio and Practice separate | PASS | Separate nested tabs and presenters; SIMULATED badge; no shared totals |
| Cost basis unchanged | PASS | No engine change; `PortfolioEngine` tests pass |
| Decimal arithmetic | PASS | No new arithmetic (the donut-rule change was reverted) |
| Corporate-action limits visible | PASS (unchanged) | 7C split-like warnings untouched |
| No advice | PASS | Existing disclaimers kept ("These are observations about your simulated portfolio, not advice.") |
| Practice idempotency (7D) | PASS (unchanged) | `PracticeOrderPresenter` untouched; 7D tests run in `:core` / `:server` suites |
| Calculation defects found | none | — |
