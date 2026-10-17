# Phase 8B — Discover and Company Search review

## Discover Stocks (changed, Android + iOS)

| Requirement (brief §3) | Result | How |
|---|---|---|
| Search prominent near the top | PASS | Directly under the title, now the design-system `StockSearchField` (icon, clear button); iOS no longer repeats the label as the placeholder |
| Purpose clear | PASS (unchanged copy) | "Find companies using filters that matter to you." |
| Curated scope stated honestly | PASS | New scope line on the landing page and with results: "160 companies · <server's universe description>" (`ScreenerDisplay.scopeLine`). REAL shows the 7E description ("…isn't the whole market"); MOCK shows "(not real data)" |
| Rows easy to scan | PASS | Sector caption under the name (`ScreenerRowView.sector`, the provider's value; absent when missing). Price, change and metrics unchanged |
| Sector filters easy to find | PASS | One row of sector pills ("All sectors" + catalogue sectors) on the landing page and with results. A tap applies the same `ChoiceFilter(SECTOR, [x])` the sheet builds on top of the **applied** query (`ScreenerPresenter.selectSector`) — one search request |
| Sorting understandable | PASS | "Sort: Market cap (high to low)" (was "↓"); same wording as the menu options |
| Active filters clear | PASS (unchanged) | "N filters" pill + removable chips; a sector tap adds "Sector: Basic Materials" |
| Clear-filters action | PASS | "Clear all" beside the filter count whenever a filter is active (spoken "Clear all filters"); was only in the no-results state |
| Empty / partially enriched states | unchanged | Empty state with Edit filters + Clear all filters; coverage line ("Checked 120 of 155 …") and server warnings stay visible |
| Pagination and cursor | unchanged | Same `loadMore` trigger, keys and cursor; no request change |
| Price and date | unchanged | The row shows the server's price; the 7E "Latest close" basis is the server's warning line. `CompanyRecord` has no per-row price date, so none is shown (never invented) |
| Nullable price / market cap / P/E / yield | PASS (unchanged) | "No data available", never 0 |
| Density | improved | The results explanation, preset notes and "What these numbers mean" moved into one "About these results" disclosure; server warnings stay visible. At 2.0× the first result row now starts on the first results frame (`after/android/font2-results1`); before, none was visible (`before/android/font2-results1`) |

Not changed: the curated universe, ranking/filter semantics, server, the 7E enrichment budget.

## Company Search (verified, no change)

| Requirement (brief §4) | Result | Evidence |
|---|---|---|
| Ticker or name | PASS | "apple" → Best match Apple Inc. (AAPL · NASDAQ), Other results (`light-search-apple`) |
| Exact ticker prominent | PASS | "Best match" section from `SearchDisplay` (5D.5.5) |
| Exchange only when given | PASS | Rows show the provider exchange; none invented |
| Loading / no results | PASS (code + frames) | `SearchDisplay.phase`; "No companies found" with "Try a different search". Keyboard focus/dismissal: unchanged, not separately re-tested |
| Debounce / stale responses | PASS | One `GET /api/v1/stocks/search` for typing "apple" (request log) |
| Selection → Company Details | PASS | Apple Inc. opens AAPL details on both platforms |
| Independent of the curated list | PASS | Global search hits `/api/v1/stocks/search` (FMP search on REAL), not the screener |
| BRK.B vs BRK-B | PASS (tests) | `SearchRanking.ticker` maps `-` to `.`; `SearchRankingTest`, `RecentSearchesTest`. MOCK has no Berkshire listing (device search returns none) |
| No invented confidence scores | PASS | Best match is a section, no score |
