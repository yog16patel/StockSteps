# Phase 8B — accessibility checklist

| Check | Result | Evidence |
|---|---|---|
| Touch targets ≥ 48 dp/pt (pills, Clear all, disclosure row, search field) | PASS (component minimums: `StockPillSelector`, `minHeight touchTarget`, `StockDisclosure` 48 row) | code |
| Row spoken label includes the sector | PASS (unit) | `ScreenerDisplayTest.rowSectorIsSpokenOnlyWhenKnown` |
| Clear all spoken | "Clear all filters" | code |
| Sort spoken | unchanged ("Sorted by market cap, high to low") | `ScreenerDisplayTest` |
| Disclosure state | "Expanded" / "Collapsed" (existing `StockDisclosure`) | code |
| Gain/loss without colour | unchanged (arrow + sign) | frames |
| Missing data in words | PASS: "No data available", "Change unavailable", "Price unavailable" | unit tests |
| Android 2.0× | PASS: header, scope, pills, filter bar and rows wrap; the first row is reachable; the search placeholder ends in "…" (P2) | `after/android/font2-*` |
| iOS AX5 | PASS for the Discover header and scope (wrap). The long MOCK scope text uses about 6 lines | `after/ios/ax5-results-top` |
| Dark mode | PASS: pills, disclosure and captions readable | `after/*/dark-*` |
| TalkBack / VoiceOver | NOT RUN (owner pass) | — |
| Focus order | NOT RUN (screen reader needed) | — |
