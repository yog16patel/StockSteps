# Phase 8C — Android / iOS parity

| Element | Android (Compose) | iOS (SwiftUI) | Verified |
|---|---|---|---|
| My Portfolio overview order | metrics, notice and freshness before the history | same (`overviewCard`) | Android frames; iOS build only (signed-in iOS BLOCKED) |
| Practice borderless sections and rows | `bordered = false` per call site | `card {}` and row modifiers `bordered: false` | Android frames; iOS build only (Practice content needs sign-in) |
| Practice empty-state copy | without the cash amount | same text | Android code; iOS code |
| Practice buttons | Material `StockButton` (unchanged) | `.stockSecondary` (was `.bordered`) | iOS build |
| Watchlist dated header | core `WatchlistPresentation` | same (shared) | Android frames; iOS guest list has no server header (device-only list) |
| Guest Portfolio / Practice / Watchlist | unchanged | unchanged | iOS guest frames BEFORE/AFTER |
