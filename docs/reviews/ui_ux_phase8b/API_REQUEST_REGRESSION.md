# Phase 8B — API request regression

**Result: no added requests.**

## Scripted flow (Android, emulator-5554, local MOCK `:8087`, DEBUG API log `StockSteps-API`)

Cold start (Home) → Markets → Discover open → "Growing Companies" preset → scroll → Back → Home → Search → type "apple" → open Apple Inc.
→ scroll Company Details. Same script for both builds (`acap8b.sh`).

| Requests | BEFORE (8A build `852afb9`) | AFTER (8B) |
|---|---|---|
| `GET /api/v1/screener/catalog` | 1 | 1 |
| `POST /api/v1/screener/search` | 1 | 1 |
| `GET /api/v1/stocks/search` (typing "apple", debounced) | 1 | 1 |
| `GET /api/v1/stocks/AAPL/{details,chart,news,why-moving}` | 4 | 4 |
| `GET /api/v1/earnings/company/AAPL/{latest,next}` | 2 | 2 |
| Home / Markets / account (`meta`, `daily-brief`, `markets/overview`, `earnings/calendar`, `me/*`) | 16 | 16 |
| **Total** | **25** | **25** |

Logs: `captures/logs/req8b-before.log`, `req8b-after.log`.

## New interactions (each is a user action, not visual polish)

- Sector pill tap → 1 `POST /api/v1/screener/search` (the same query "Show results" sends for that filter).
- "Clear all" → 1 `POST /api/v1/screener/search` (the existing `resetAll`).
- Verified in the log: Basic Materials then Clear all = 2 requests.

No request on scrolling, recomposition or opening the "About these results" disclosure. Upstream (FMP/Finnhub) calls are only made by the
server; no server code changed, and the 7E enrichment budget and quota controls are untouched. iOS runtime counts: NOT RUN (views only;
the presenter is shared).
