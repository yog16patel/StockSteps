# Phase 8C — API request regression

**Result:** no added requests.

**Flow** (Android emulator-5554, local MOCK `:8087`, DEBUG API log `StockSteps-API`, the same script `reqs8c.sh` for both builds):
cold start (Home) → Portfolio (My Portfolio) → Practice → My Portfolio → Watchlist → Portfolio → Home → Portfolio.

| Request | BEFORE (8B build) | AFTER (8C final) |
|---|---|---|
| `GET /api/v1/meta` | 1 | 1 |
| `GET /api/v1/daily-brief/latest` | 1 | 1 |
| `GET /api/v1/me/daily-brief/[REDACTED]/personalized` | 1 | 1 |
| `GET /api/v1/me/portfolio` | 1 | 1 |
| `GET /api/v1/me/practice` | 1 | 1 |
| `GET /api/v1/me/practice/performance` | 1 | 1 |
| `GET /api/v1/me/watchlists` | 1 | 1 |
| `GET /api/v1/stocks/watch-data` | 1 | 1 |
| `GET /api/v1/me/{alerts,entitlements,learning,screens}` | 4 | 4 |
| **Total** | **12** | **12** |

- Repeated tab switches, Back and the My Portfolio ↔ Practice switches sent nothing more, so once-per-entry loading (5D.5.6D) holds.
- The Portfolio account-value history still loads only on demand: no history request in this flow.
- No server code changed, so there are no upstream FMP/Finnhub changes and the Phase 7E budgets are untouched.

Logs: `captures/logs/req8c-before.log`, `req8c-after.log`. iOS runtime counts: NOT RUN (guest only; the views changed, not the
clients).
