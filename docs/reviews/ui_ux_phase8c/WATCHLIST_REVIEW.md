# Phase 8C — Watchlist review

| Brief item | Result |
|---|---|
| Name, ticker, latest dated price | PASS: rows (Phase 5A) show the logo, ticker, name, price and signed change. **Changed:** the header freshness now carries the date, "Updated Oct 7, 5:15 PM ET" (was "Updated 4:48 AM ET", which read as today's for Oct 7 closes) |
| Movement only when valid | PASS (unchanged): "Change since the previous close. Prices may be delayed."; missing quotes → "No data available" |
| Add / remove | Add a company → Search → Company Details → star (three steps, unchanged). Removal via the star or the row ⋮ (unchanged) |
| Empty state | PASS: "Your watchlist is empty", Add a company; guest: "They're saved on this device" + "Sign in for more" |
| Guest vs signed-in | unchanged (lists never merged) |
| No extra market-data calls | PASS: header text only (`API_REQUEST_REGRESSION.md`) |
