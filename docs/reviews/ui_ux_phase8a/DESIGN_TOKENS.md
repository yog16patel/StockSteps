# Phase 8A — design tokens and components

## Tokens

| Token | Change | Value | Users |
|---|---|---|---|
| `ThemeSpacing.pageSectionGap` (+ Compose `StockStepsTheme.spacing.pageSectionGap`; iOS reads `ThemeSpacing.shared`) | **new** semantic role | 20 (= `xl`) | Home (was raw `xl`, same value: no visual change), Markets (was `sectionGap` 12) |

No existing token value changed: `sectionGap` stays 12, `cardPadding` 16, brand colours (primary `#1683FF`, dark background `#07111C`,
dark surface `#0D1B2A`, light background `#F7FAFD`) untouched. `DesignTokensTest` asserts `pageSectionGap == 20` and `> sectionGap`.

## Components

| Component | Change | Default behaviour | Other screens |
|---|---|---|---|
| `StockCard` / `.stockCard` | none | border **on** by default (unchanged) | — |
| `HomeSection`, `HomeRowGroup` / `.homeRowGroup()` (KAN-14) | none; now **also used by Markets** | borderless surface group | Home unchanged |
| iOS `StockRow` | new optional `stackTrailing: Bool = false` (values under the name) | off | Watchlist, Home, search, Discover rows unchanged (they don't pass it) |
| Compose `StockRow` | none | — | — |
| `StockInsightCard` (Markets lesson) | none | tinted education card kept | — |

## Shared display objects (no new data)

- `MarketsScreenPresentation.statusHeadline(header, session)` — "US Market · After-hours"; status words are the existing `statusLabel` /
  core header text.
- `MarketsScreenPresentation.sectorBar(row)` + `SectorBar`, `FLAT_TICK` — track geometry for diverging sector bars from the core
  `SectorRowModel.fraction` and `direction`.
- `HomeDashboardPresentation.portfolioOverview(state)` — `PortfolioOverviewDisplay.of(state, active accounts)` for the Home summary.

## Surfaces on Markets (before → after)

| Section | Before | After |
|---|---|---|
| US Market status | bordered card + "US Market" heading | open block on the page |
| Major indices | bordered card | borderless group |
| Daily Market Brief entry | own bordered card | closes the indices group (own borderless group when the overview failed) |
| Top Movers | bordered card | borderless group |
| Sector Performance | bordered card | borderless group, diverging bars |
| Research tools | bordered card | borderless group |
| Market News | bordered card (12 inset) | borderless group (16 inset) |
| Today's lesson | tinted card | unchanged |
| Overview error | bordered card | borderless |

Borders on a loaded Markets page: 7 → 0 (the lesson card has never had one).
