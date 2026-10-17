# Phase 8B — Android / iOS parity

| Element | Android (Compose) | iOS (SwiftUI) | Parity |
|---|---|---|---|
| Scope line | `ScreenerDisplay.scopeLine` caption under the subtitle | same | same text |
| Search field | `StockSearchField(bordered = false)` | `StockSearchField` | same component family as Company Search on each platform |
| Sector pills | `StockPillSelector(sectorOptions, selectedSector)` → `ScreenerActions.onSector` → `selectSector` | `StockPillSelector` → `client.screener.selectSector` | same presenter call |
| Clear all | `TextButton` in the filter `FlowRow`, spoken "Clear all filters" | `Button` in the `ViewThatFits` filter bar, spoken "Clear all filters" | same |
| Sort label | `ScreenerDisplay.sortLabel` | same | same |
| About these results | `StockDisclosure` | `StockDisclosure` | same content and order |
| Row sector | caption under the logo/name block, indented | same | same |
| Legacy movers | `MarketMoverDisplay.line` | `MarketMoverDisplay.shared.line` | same text |
| Search / Company Details | unchanged | unchanged | — |

Known per-platform differences (unchanged): Android Discover sits under a "Discover" top bar with Back, and iOS uses a "‹ Markets" Back
label. The pill row fades at the edge on both.
