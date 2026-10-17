# Phase 8B — Discover, Search and Company Details UI audit (before changes)

Base: `852afb9` (Phase 8A, committed and pushed). Working tree was clean.

Evidence: BEFORE frames on local MOCK `:8087`.
- Android: emulator-5554 (Pixel 9 Pro, 412 dp; the stored session is signed in).
- iOS: iPhone 16 guest.
- Originals: `captures/before/`.

The MOCK Discover universe has 160 companies, described as "captured US large caps plus generated sample US and Canadian companies (not
real data)". The REAL curated list of 155 companies is unchanged and wasn't reachable: FMP is answering 429.

## Baseline checks

- **Phase 8A:** committed and pushed (`852afb9`).
- **Phase 7E enrichment quota:** still open. Phase 7E's `KNOWN_LIMITATIONS.md` §6 estimates ≈ 310 requests a day per instance at the
  default `SCREENER_ENRICHMENT_PER_HOUR=60`, too much for a 250-a-day key. This is recorded here and not changed: no backend work in 8B.
- **Symbol normalization:** BRK.B and BRK-B match the same listing through `SearchRanking.ticker`, which maps `-` to `.`. Both
  `SearchRankingTest` and `RecentSearchesTest` cover it. MOCK has no Berkshire listing, so a device search for "BRK.B" shows
  "No companies found". That is a gap in the fixture, not in normalization.

## Issue inventory

**Priority scale:**
- **P0:** broken navigation, an inaccessible control, or misleading financial data.
- **P1:** usability, hierarchy or readability.
- **P2:** cosmetic.

| # | Pri | Area | Platform | Issue | Evidence |
|---|---|---|---|---|---|
| D1 | P1 | Discover landing | both | The page never says which companies it covers. The scope ("N companies", not the whole market) appears only after a search, and only as "Checked all 160 companies". | `before/*/light-discover1` |
| D2 | P1 | Discover filters | both | Sector, the most common beginner filter, can only be reached through Edit filters → sheet → category → Sector. There is no one-tap sector choice. | code, frames |
| D3 | P1 | Discover filters | both | "Clear all filters" appears only in the no-results state. With results showing, filters come off one chip at a time. | code |
| D4 | P1 | Discover sort | both | The sort shows as "Sort: Market cap ↓". The arrow isn't plain English for beginners (the spoken label already says "high to low"). | `light-discover-results1` |
| D5 | P1 | Discover rows | both | Rows show no sector, so a list mixing industries can't be scanned by business type. The server already sends `sector`. | code (`CompanyRecord.sector`) |
| D6 | P2 | Discover search field | iOS | The field label "Find a company by name or ticker" is repeated as the placeholder text right under it. | iOS `light-discover1` |
| D7 | P2 | Discover search field | Android | It uses a plain Material `OutlinedTextField`, while Company Search uses the design-system `StockSearchField` (search icon, clear button). | code |
| D8 | P2 | Discover results | both | Four or five caption lines sit above the first row (coverage, explanation, a link, warnings). Only one row is above the fold on iPhone. | `light-discover-results1` |
| L1 | P1 | Legacy Discovery movers | both | Shows "Unavailable% · Price Unavailable" and raw doubles ("1.2345%"). Neither screen is reachable in the current app: iOS `DiscoveryScene` has no caller, and Android `DiscoveryRoute` renders Home. | code |
| S1 | — | Company Search | both | Already refined (5D.5.5, Phase 4/5): Best match section, readable rows, a useful empty state, debounce and stale-response guard, recent searches. No issue found on device. | `light-search-*` |
| C1 | — | Company Details | both | Already refined (5D.5.4A–D, Phase 3). Header, price and dated freshness line ("Closed · Updated 4:00 PM EDT · Oct 7 · Prices may be delayed"), chart with period change, spoken summary and touch inspection, At a Glance grid with ⓘ explanations, Understand rows, Valuation with P/E vs history, Financial Highlights. Missing values read "—" / "No data available", never 0. | `light-details1..8` |
| C2 | P2 | Company Details chart | both | Y-axis labels ("341.07") have no currency symbol. The headline price and the scrub label carry it. | `light-details1` |

No P0 found.

## Scoped plan (8B)

1. **Shared, display only (core `ScreenerDisplay` / `ScreenerRowView`):**
   - `scopeLine(universe)`;
   - sector options from the catalogue's own `choices[SECTOR]` and the selected sector from the applied query;
   - `CLEAR_ALL`;
   - a plain-words sort label;
   - `ScreenerRowView.sector` from `CompanyRecord.sector`.
2. **Discover, both platforms:**
   - the scope line under the subtitle;
   - one row of sector pills (All sectors + catalogue sectors) on the landing page and on results;
   - "Clear all" next to the filter bar;
   - a sector caption on rows;
   - the design-system search field (D6 / D7).

   Choosing a sector applies the same query the filter sheet builds, so it is one search request per tap, as before.
3. **Legacy movers (L1):** a shared `MarketMoverDisplay` with separate change and price text, used by both legacy views, plus tests.
4. **Search and Company Details:** no layout change (S1, C1). C2 is left as a P2 follow-up, because axis labels are shared with other
   charts.
5. **No server, provider, ranking, cursor or 7E budget change.**

**Owner decisions:**
- D8B-1: sector pills on Discover;
- D8B-2: the plain-words sort label.
