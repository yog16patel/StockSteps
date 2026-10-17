# Phase 8A — accessibility checklist

| Check | Result | Evidence |
|---|---|---|
| Touch targets ≥ 48 dp/pt (index rows 52, sector rows 48, nav rows, "Previous briefs", "Show all", "?" and ⓘ buttons) | PASS (unchanged sizes) | code; frames |
| Direction never by colour alone | PASS — sector bars now also show direction by side of the centre line; text keeps arrow + sign; index/mover changes unchanged (`StockPriceChange`) | frames `*-markets3` |
| Sector chart spoken | PASS (unchanged) — group description "Sector performance, … Best: … Weakest: …" and per-row "Healthcare, +0.57%, measured by XLV" | code (`chartDescription`, `accessibilityLabel`) |
| Status block spoken | dot hidden; "US Market · After-hours" merged; footer reads the full data notice (`statusFooterDescription`) | code |
| Headings | Section titles stay headings (`StockSectionHeader`). "US Market" is no longer a separate heading (now part of the status line) | code — owner decision D8A-1b in the package |
| Home portfolio summary spoken | PASS (unit) — one summary from `PortfolioOverviewDisplay.spoken` ("Total value unavailable" when missing, gain/loss in words) | `HomeDashboardPresentationTest` |
| Large text Android 2.0× | PASS — Markets indices stack, sector rows stack, movers readable; Home unchanged | `after/android/font2-*` |
| Large text iOS AX5 | PASS — movers fixed (were one letter per line); sectors, tools, news wrap; status block rebuilds on a live change | `after/ios/ax5-*` |
| Dark mode | PASS — borderless groups keep the surface contrast against the page (`#0D1B2A` on `#07111C`) on both | `after/*/dark-*` |
| TalkBack / VoiceOver | NOT RUN (owner pass; screen readers can't be driven by injected input here) | — |
| Reduced motion / contrast settings | NOT RUN | — |
