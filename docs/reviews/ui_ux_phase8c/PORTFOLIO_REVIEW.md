# Phase 8C — My Portfolio review

| Brief item | Before | After | Status |
|---|---|---|---|
| Total value prominent | yes (largest number) | unchanged | PASS |
| Gain/loss with amount and % when valid | today's change "CAD 0.00 (0.00%)" or "Today's change unavailable · Why?"; unrealized P/L in the metric grid | unchanged values; the grid now sits directly under the total | PASS |
| Cost basis distinct from value | "Invested cost" in the grid | unchanged, now above the history | PASS |
| Valuation basis and date | "Prices as of Oct 8, 2026, 3:00 PM UTC · Exchange rate as of …" under the grid | moved with the grid (above the history) | PASS |
| Account value history | between today's change and the grid | after the grid and notices | **D8C-1** |
| Incomplete valuation | "CAD —", notice in words | unchanged (moved up) | PASS |
| Holdings rows | name, ticker · exchange · shares, value, gain % | unchanged | PASS |
| Allocation | donut also for one holding at 100 % | unchanged: the Phase C rule (`PortfolioPhaseCTest`) keeps the ring; D8C-3 asks the owner | not changed |
| Transactions | `PortfolioFormRules` forms, delete only on own data | unchanged | not changed |
| Guest | sign-in banner + empty state | unchanged | PASS |

iOS: the same reorder is in `PortfolioScreen.swift` (`overviewCard`). It's verified by build only, because signed-in iOS is BLOCKED.
