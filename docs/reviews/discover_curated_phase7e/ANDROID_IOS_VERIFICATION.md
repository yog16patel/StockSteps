# Phase 7E — Android and iOS verification

| Item | Android | iOS |
|---|---|---|
| Search field "Find a company by name or ticker" (new; above the presets or filter bar; `ScreenerPresenter.setText`, 300 ms debounce) | Compose `OutlinedTextField` in `ScreenerScreen.kt`; build PASS | `StockTextField` in `DiscoverStocksViews.swift`; build PASS |
| Results, prices, coverage and freshness lines | unchanged components; the server's warnings and the coverage line show the new text | same |
| Company Details from a row | unchanged (`onOpen(symbol)`; curated symbols are the provider's, e.g. `BRK-B`) | unchanged |
| Device / simulator run (MOCK or REAL) | NOT RUN | NOT RUN |
| REAL device | BLOCKED: needs a redeploy (approval), and FMP still answered 429 at the last check | BLOCKED |
| Light / dark, large text | NOT RUN (the field uses the standard design-system text field on both) | NOT RUN |

MOCK Discover is unchanged: it keeps the fixture universe with full records and per-row quotes.
