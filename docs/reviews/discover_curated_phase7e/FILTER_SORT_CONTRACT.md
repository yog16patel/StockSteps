# Phase 7E — filter, sort, search and pagination contract (curated universe, REAL)

All of it runs on the server over the **whole list** (never only the current page), through the existing `ScreenerEngine`. Results are
cached per query for 5 minutes and paged with a cursor tied to the query's fingerprint.

| Feature | Data | Missing-data behaviour | Status |
|---|---|---|---|
| Sector | curated, then the provider profile | a company without a sector doesn't match a sector filter | AVAILABLE |
| Industry, exchange, country | provider profile (curated exchange until it loads; WMT none) | no match while unknown | AVAILABLE once profiles load |
| Market cap range / size | FMP profile `marketCap` (listing currency → USD for comparison) | **excluded and counted** in `excludedForMissingData`; never classed as small | AVAILABLE where the profile reports it (field UNVERIFIED on this plan) |
| Price range | latest official close (≤ 5 trading sessions old) | excluded and counted; never 0 | AVAILABLE |
| P/E, dividend yield and other fundamentals | the existing fundamentals warm-up (`SCREENER_FUNDAMENTALS_PER_HOUR`; **0 on LAN REAL**; statements partly refused on this plan) | the response says "Financial filters were applied to X of N companies…"; a missing P/E is never 0, a missing yield is never "no dividend" (the existing `NO_DIVIDEND` availability) | RESTRICTED by plan and budget; not hidden |
| Volume, 52-week position | not loaded for curated (no quote) | excluded and counted | NOT AVAILABLE (needs quotes) |
| Daily change | latest close vs the previous trading day's close (Phase 7B rule) | null when the pair isn't consecutive | AVAILABLE |
| Sort: name A–Z, market cap ↓/↑, change, any metric | numeric values (`MetricValue.value`), never strings | missing values last (by symbol); ties by symbol; descending flips only the present values | AVAILABLE |
| Text search (`ScreenerQuery.text`, new, optional) | ticker and name | non-matches are dropped; matches ordered by the shared `SearchRanking` tiers (exact ticker, exact name, ticker prefix, name word start, name prefix, later word, substring); the chosen sort order is kept within a tier (stable) | AVAILABLE; ≤ 100 characters |
| Pagination | cursor `offset.fingerprint` | a cursor from another query → 400 `INVALID_CURSOR` | stable: 155 rows over 8 pages, no duplicates (test) |

Coverage is always reported:
- `UniverseInfo.priced` (new, optional);
- the warning "Prices and market caps are loaded for X of N companies so far…" while enrichment runs;
- "Prices are official closing prices (latest close Oct 16, 2026), not live quotes."
